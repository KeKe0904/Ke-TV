package com.tvtoolbox.screensaver

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * 更新检查器。
 *
 * 从 GitHub Releases API 获取最新 Release（包括 prerelease 测试版本），
 * 与当前 App 的 versionCode 比较，返回是否有新版本以及下载地址。
 */
object UpdateChecker {

    private const val TAG = "UpdateChecker"
    private const val RELEASES_API =
        "https://api.github.com/repos/KeKe0904/Ke-TV/releases"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val gson = Gson()

    data class Result(
        val hasUpdate: Boolean,
        val latestVersion: String,
        val currentVersion: String,
        val downloadUrl: String,
        val releaseNotes: String,
        val isPrerelease: Boolean
    )

    /**
     * 检查更新。失败抛异常，调用方自行处理。
     *
     * 网络策略：依次尝试主代理 → 备代理 → 直连，任意一个成功即返回。
     * 一个代理失败（超时 / 非 2xx / 非 GitHub JSON）就换下一个。
     */
    fun check(context: Context): Result {
        val currentVersionName = getVersionName(context)
        // 关键修复：用同一套 versionName 解析规则比较，避免与 versionCode 编号不一致导致永远提示更新
        // 之前 bug：parseVersionCode("v1.6.1")=10601 与 versionCode=11 比较，永远大于，永远提示有更新
        val currentVersionParsed = parseVersionCode(currentVersionName)

        // 依次尝试主代理 → 备代理 → 直连
        val apiUrls = GithubProxy.wrapAll(RELEASES_API)
        var lastError: Throwable? = null
        for (apiUrl in apiUrls) {
            try {
                val request = Request.Builder()
                    .url(apiUrl)
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "Ke-TV-UpdateChecker/$currentVersionName")
                    .build()

                client.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        throw RuntimeException("HTTP ${resp.code}")
                    }
                    val body = resp.body?.string() ?: throw RuntimeException("响应为空")
                    val releases = gson.fromJson(body, Array<JsonObject>::class.java)
                        ?: throw RuntimeException("解析失败")

                    if (releases.isEmpty()) {
                        throw RuntimeException("没有发布版本")
                    }

                    // GitHub 默认按创建时间倒序，取第一个即最新
                    val latest = releases[0]
                    val tagName = latest.get("tag_name")?.asString ?: ""
                    val latestVersionCode = parseVersionCode(tagName)
                    val isPrerelease = latest.get("prerelease")?.asBoolean ?: false
                    val releaseNotes = latest.get("body")?.asString ?: ""
                    val latestVersionName = tagName.trimStart('v', 'V')

                    // 查找第一个 APK asset 作为下载链接
                    val assets = latest.getAsJsonArray("assets") ?: emptyList()
                    var downloadUrl = "https://github.com/KeKe0904/Ke-TV/releases/tag/$tagName"
                    for (asset in assets) {
                        val obj = asset.asJsonObject
                        val name = obj.get("name")?.asString ?: ""
                        if (name.endsWith(".apk", ignoreCase = true)) {
                            downloadUrl = obj.get("browser_download_url")?.asString ?: downloadUrl
                            break
                        }
                    }

                    Log.d(TAG, "current=$currentVersionParsed latest=$latestVersionCode tag=$tagName apiUrl=$apiUrl")

                    return Result(
                        hasUpdate = latestVersionCode > currentVersionParsed,
                        latestVersion = latestVersionName,
                        currentVersion = currentVersionName,
                        downloadUrl = downloadUrl,
                        releaseNotes = releaseNotes,
                        isPrerelease = isPrerelease
                    )
                }
            } catch (t: Throwable) {
                Log.w(TAG, "API 请求失败：$apiUrl — ${t.message}")
                lastError = t
                // 继续尝试下一个候选 URL
            }
        }
        throw lastError ?: RuntimeException("所有代理均失败")
    }

    private fun getVersionName(context: Context): String {
        return try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
        } catch (e: PackageManager.NameNotFoundException) {
            "?"
        }
    }

    /**
     * 从 tag 解析 versionCode。支持 v1.4、v1.4.1、v1.5 等。
     * 规则：主版本 * 10000 + 次版本 * 100 + 修订版本。
     * 例如 1.4.1 → 10401，1.4 → 10400。
     */
    private fun parseVersionCode(tag: String): Int {
        val cleaned = tag.trimStart('v', 'V')
        val parts = cleaned.split(".").mapNotNull { it.toIntOrNull() }
        val major = parts.getOrNull(0) ?: 0
        val minor = parts.getOrNull(1) ?: 0
        val patch = parts.getOrNull(2) ?: 0
        return major * 10000 + minor * 100 + patch
    }
}
