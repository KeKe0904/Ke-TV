package com.tvtoolbox.screensaver

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 更新管理器：在 APP 内部完成 APK 下载并推送到系统安装器。
 *
 * 流程：
 * 1. 调用 [UpdateChecker.check] 拿到最新版本与 APK 下载地址
 * 2. 显示确认对话框
 * 3. 用户确认后下载 APK 到 files 目录（应用私有，无需存储权限）
 * 4. 通过 FileProvider + ACTION_VIEW / ACTION_INSTALL_PACKAGE 触发系统安装器
 *
 * 不再跳浏览器，整个流程在 APP 内完成。
 */
object UpdateManager {

    private const val TAG = "UpdateManager"

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    /**
     * 完整流程：检查 + 提示 + 下载 + 安装。
     *
     * @param activity 调用方 Activity（用于弹对话框、启动安装器）
     * @param scope 协程 scope，调用方负责管理生命周期
     * @param onStatusChange 检查阶段的状态提示回调（如 Toast 文案）
     */
    fun checkAndInstall(
        activity: Activity,
        scope: CoroutineScope,
        onStatusChange: (CharSequence) -> Unit = {}
    ) {
        onStatusChange(activity.getString(R.string.update_checking))

        scope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    UpdateChecker.check(activity)
                } catch (t: Throwable) {
                    Log.e(TAG, "check failed", t)
                    null
                }
            }

            if (result == null) {
                onStatusChange(activity.getString(R.string.update_error, "网络异常"))
                return@launch
            }

            if (!result.hasUpdate) {
                onStatusChange(activity.getString(R.string.update_latest))
                return@launch
            }

            if (result.downloadUrl.isBlank() ||
                !result.downloadUrl.endsWith(".apk", ignoreCase = true)
            ) {
                // 没有 APK 资产，无法 in-app 下载
                onStatusChange(activity.getString(R.string.update_no_assets))
                return@launch
            }

            showConfirmDialog(activity, result, scope)
        }
    }

    /** 弹出"发现新版本"对话框，确认后开始下载。 */
    private fun showConfirmDialog(
        activity: Activity,
        result: UpdateChecker.Result,
        scope: CoroutineScope
    ) {
        val prereleaseTag = if (result.isPrerelease)
            " · ${activity.getString(R.string.update_prerelease)}" else ""
        val message = activity.getString(
            R.string.update_dialog_message,
            result.currentVersion,
            result.latestVersion,
            prereleaseTag
        )

        AppDialog.showMessage(
            context = activity,
            title = activity.getString(R.string.update_dialog_title),
            message = message,
            positiveText = activity.getString(R.string.update_dialog_download),
            onPositive = { startDownload(activity, result, scope) },
            negativeText = activity.getString(R.string.update_dialog_later),
            cancelable = true
        )
    }

    /**
     * 显示进度对话框并下载 APK，下载完成自动触发安装。
     */
    private fun startDownload(
        activity: Activity,
        result: UpdateChecker.Result,
        scope: CoroutineScope
    ) {
        val fileName = "Ke-TV-${result.latestVersion}.apk"
        val targetFile = File(activity.filesDir, fileName)
        // 删除旧版本残留
        if (targetFile.exists()) targetFile.delete()

        // 进度对话框容器：横向进度条 + 进度文案
        val density = activity.resources.displayMetrics.density
        val container = android.widget.LinearLayout(activity).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            val hPad = (8 * density).toInt()
            val vPad = (4 * density).toInt()
            setPadding(hPad, vPad, hPad, vPad)
        }
        val progressBar = android.widget.ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
            isIndeterminate = true
            val lp = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            )
            layoutParams = lp
        }
        val progressText = android.widget.TextView(activity).apply {
            text = activity.getString(R.string.update_downloading_indeterminate)
            setTextColor(ContextCompat.getColor(activity, R.color.text_tertiary))
            textSize = 13f
            val lp = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = (12 * density).toInt()
            layoutParams = lp
        }
        container.addView(progressBar)
        container.addView(progressText)

        // 用 AppDialog.showCustom 自定义视图，保留 dialog 引用以便取消
        // 用 holder 让取消按钮能引用到 downloadJob（job 在 dialog 之后才创建）
        val jobHolder = arrayOf<Job?>(null)
        val dialog = AppDialog.showCustom(
            context = activity,
            title = activity.getString(R.string.update_downloading_title),
            contentView = container,
            negativeText = activity.getString(R.string.dialog_cancel),
            onNegative = {
                jobHolder[0]?.cancel()
            },
            cancelable = false
        )

        val downloadJob = scope.launch {
            val outcome = withContext(Dispatchers.IO) {
                downloadApk(result.downloadUrl, targetFile) { read, total ->
                    if (total > 0) {
                        val percent = (read * 100 / total).toInt()
                        val readStr = formatSize(read)
                        val totalStr = formatSize(total)
                        progressBar.post {
                            // 首次拿到总大小，从 indeterminate 切到 determinate
                            if (progressBar.isIndeterminate) {
                                progressBar.isIndeterminate = false
                            }
                            progressBar.progress = percent
                            progressText.text = activity.getString(
                                R.string.update_downloading_message,
                                percent,
                                readStr,
                                totalStr
                            )
                        }
                    }
                }
            }

            // 关闭进度对话框
            progressBar.post { if (dialog.isShowing) dialog.dismiss() }

            when (outcome) {
                is DownloadResult.Success -> {
                    onStatusChange(activity, activity.getString(R.string.update_download_complete))
                    installApk(activity, outcome.file)
                }
                is DownloadResult.Canceled -> {
                    onStatusChange(activity, activity.getString(R.string.update_download_canceled))
                }
                is DownloadResult.Error -> {
                    onStatusChange(
                        activity,
                        activity.getString(R.string.update_download_failed, outcome.message)
                    )
                }
            }
        }
        jobHolder[0] = downloadJob
    }

    private fun onStatusChange(activity: Activity, message: CharSequence) {
        if (!activity.isFinishing && !activity.isDestroyed) {
            android.widget.Toast.makeText(activity, message, android.widget.Toast.LENGTH_LONG).show()
        }
    }

    /** 下载 APK 文件，支持进度回调与取消。 */
    private suspend fun downloadApk(
        url: String,
        targetFile: File,
        onProgress: (Long, Long) -> Unit
    ): DownloadResult {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Ke-TV-Updater/${android.os.Build.VERSION.SDK_INT}")
                .build()

            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return DownloadResult.Error("HTTP ${resp.code}")
                }
                val body = resp.body ?: return DownloadResult.Error("响应为空")
                val total = body.contentLength()
                val source = body.source()
                targetFile.outputStream().use { sink ->
                    val buffer = ByteArray(8 * 1024)
                    var read: Int
                    var totalRead = 0L
                    while (true) {
                        val n = source.read(buffer)
                        if (n == -1) break
                        sink.write(buffer, 0, n)
                        totalRead += n
                        onProgress(totalRead, total)
                    }
                }
                DownloadResult.Success(targetFile)
            }
        } catch (t: kotlinx.coroutines.CancellationException) {
            targetFile.delete()
            DownloadResult.Canceled
        } catch (t: Throwable) {
            Log.e(TAG, "download failed", t)
            targetFile.delete()
            DownloadResult.Error(t.message ?: t.javaClass.simpleName)
        }
    }

    /** 通过 FileProvider 推送到系统安装器。 */
    private fun installApk(activity: Activity, file: File) {
        try {
            val authority = "${activity.packageName}.fileprovider"
            val uri: Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                FileProvider.getUriForFile(activity, authority, file)
            } else {
                Uri.fromFile(file)
            }

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }
            activity.startActivity(intent)
        } catch (t: Throwable) {
            Log.e(TAG, "install failed", t)
            onStatusChange(activity, activity.getString(R.string.update_install_failed))
        }
    }

    private fun formatSize(bytes: Long): String {
        val kb = bytes / 1024.0
        return if (kb < 1024) String.format("%.1f KB", kb)
        else String.format("%.1f MB", kb / 1024.0)
    }

    private sealed class DownloadResult {
        data class Success(val file: File) : DownloadResult()
        object Canceled : DownloadResult()
        data class Error(val message: String) : DownloadResult()
    }
}
