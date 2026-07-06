package com.tvtoolbox.screensaver

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 设置页（自定义布局，不再用 PreferenceFragment）。
 *
 * 这样做的理由：
 * 1. EditTextPreference 在某些主题下对话框不弹出（之前的 bug）
 * 2. 液态玻璃 UI 用 PreferenceFragment 难以定制
 * 3. 自定义逻辑更可控、更可调试
 */
class SettingsActivity : AppCompatActivity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // 间隔选项
    private val intervalEntries by lazy { resources.getStringArray(R.array.interval_entries) }
    private val intervalValues by lazy { resources.getStringArray(R.array.interval_values) }

    // 图床类型选项
    private val sourceModeEntries by lazy { resources.getStringArray(R.array.source_mode_entries) }
    private val sourceModeValues by lazy { resources.getStringArray(R.array.source_mode_values) }

    // 主题模式选项
    private val themeModeEntries by lazy { resources.getStringArray(R.array.theme_mode_entries) }
    private val themeModeValues by lazy { resources.getStringArray(R.array.theme_mode_values) }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.apply(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        setSupportActionBar(findViewById(R.id.toolbar_settings))
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = getString(R.string.settings_title)
        }
        // 用自定义返回图标，覆盖默认
        findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar_settings)
            .setNavigationOnClickListener { finish() }

        bindRows()
    }

    override fun onResume() {
        super.onResume()
        refreshUi()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun bindRows() {
        // 主题模式
        findViewById<View>(R.id.rowThemeMode).also {
            it.setOnClickListener { showThemeModeDialog() }
            FocusHelper.setupFocus(it)
        }
        // 图床类型
        findViewById<View>(R.id.rowSourceMode).also {
            it.setOnClickListener { showSourceModeDialog() }
            FocusHelper.setupFocus(it)
        }
        // 图床 URL
        findViewById<View>(R.id.rowImageUrl).also {
            it.setOnClickListener { showUrlDialog() }
            FocusHelper.setupFocus(it)
        }
        // 切换间隔
        findViewById<View>(R.id.rowInterval).also {
            it.setOnClickListener { showIntervalDialog() }
            FocusHelper.setupFocus(it)
        }
        // 随机顺序：点击卡片切换开关
        findViewById<View>(R.id.rowRandom).also {
            it.setOnClickListener {
                val cur = Prefs.randomOrder(this)
                Prefs.setRandomOrder(this, !cur)
                refreshUi()
            }
            FocusHelper.setupFocus(it)
        }
        // Ken Burns
        findViewById<View>(R.id.rowKenBurns).also {
            it.setOnClickListener {
                val cur = Prefs.kenBurns(this)
                Prefs.setKenBurns(this, !cur)
                refreshUi()
            }
            FocusHelper.setupFocus(it)
        }
        // 测试连通性
        findViewById<View>(R.id.rowTest).also {
            it.setOnClickListener { doTest() }
            FocusHelper.setupFocus(it)
        }
        // 系统屏保设置
        findViewById<View>(R.id.rowDreamSettings).also {
            it.setOnClickListener { openDreamSettings() }
            FocusHelper.setupFocus(it)
        }
        // 检查更新
        findViewById<View>(R.id.rowCheckUpdate).also {
            it.setOnClickListener { checkUpdate() }
            FocusHelper.setupFocus(it)
        }

        // TV 进入设置页时，默认聚焦到第一个 row（让遥控器有起点）
        FocusHelper.requestInitialFocus(findViewById(R.id.rowThemeMode))
    }

    /** 刷新所有行的显示状态。 */
    private fun refreshUi() {
        // 主题模式
        val tm = Prefs.themeMode(this)
        val tmIdx = themeModeValues.indexOf(tm).coerceAtLeast(0)
        val tmText = if (tmIdx in themeModeEntries.indices) themeModeEntries[tmIdx]
            else themeModeEntries[0]
        findViewById<android.widget.TextView>(R.id.tvThemeModeValue).text = tmText

        // 图床类型
        val mode = Prefs.sourceMode(this)
        val modeIdx = sourceModeValues.indexOf(mode).coerceAtLeast(0)
        val modeText = if (modeIdx in sourceModeEntries.indices) sourceModeEntries[modeIdx]
            else getString(R.string.pref_source_mode_single)
        findViewById<android.widget.TextView>(R.id.tvSourceModeValue).text = modeText

        // URL
        val url = Prefs.imageUrl(this)
        val urlText = if (url.isBlank()) getString(R.string.pref_image_url_summary) else url
        findViewById<android.widget.TextView>(R.id.tvImageUrlValue).text = urlText

        // 间隔
        val seconds = Prefs.intervalSeconds(this)
        val idx = intervalValues.indexOfFirst { it.toIntOrNull() == seconds }
        val intervalText = if (idx in intervalEntries.indices) intervalEntries[idx] else "${seconds}s"
        findViewById<android.widget.TextView>(R.id.tvIntervalValue).text = intervalText

        // 开关
        findViewById<MaterialSwitch>(R.id.switchRandom).isChecked = Prefs.randomOrder(this)
        findViewById<MaterialSwitch>(R.id.switchKenBurns).isChecked = Prefs.kenBurns(this)
    }

    /** 图床类型单选对话框。 */
    private fun showSourceModeDialog() {
        val cur = Prefs.sourceMode(this)
        val checked = sourceModeValues.indexOf(cur).coerceAtLeast(0)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_source_mode_title)
            .setSingleChoiceItems(sourceModeEntries, checked) { dialog, which ->
                if (which in sourceModeValues.indices) {
                    Prefs.setSourceMode(this, sourceModeValues[which])
                    refreshUi()
                }
                dialog.dismiss()
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    /** 主题模式单选对话框。切换后立即重建 Activity 让背景生效。 */
    private fun showThemeModeDialog() {
        val cur = Prefs.themeMode(this)
        val checked = themeModeValues.indexOf(cur).coerceAtLeast(0)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_theme_mode_title)
            .setSingleChoiceItems(themeModeEntries, checked) { dialog, which ->
                if (which in themeModeValues.indices) {
                    val newMode = themeModeValues[which]
                    Prefs.setThemeMode(this, newMode)
                    dialog.dismiss()
                    // 立即应用主题模式并重建 Activity，让背景纯黑/纯白立刻生效
                    ThemeHelper.apply(this)
                    recreate()
                }
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    /** 图床 URL 输入对话框（修复 v1.1 点击无反应的 bug）。 */
    private fun showUrlDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_url_input, null, false)
        val et = view.findViewById<TextInputEditText>(R.id.etUrl)
        et.setText(Prefs.imageUrl(this))

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_url_title)
            .setView(view)
            .setPositiveButton(R.string.dialog_save) { _, _ ->
                val newUrl = et.text?.toString()?.trim() ?: ""
                Prefs.setImageUrl(this, newUrl)
                refreshUi()
                Toast.makeText(this, R.string.dialog_save, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    /** 切换间隔单选对话框。 */
    private fun showIntervalDialog() {
        val cur = Prefs.intervalSeconds(this)
        val checked = intervalValues.indexOfFirst { it.toIntOrNull() == cur }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_interval_title)
            .setSingleChoiceItems(intervalEntries, checked) { dialog, which ->
                intervalValues[which].toIntOrNull()?.let { secs ->
                    Prefs.setIntervalSeconds(this, secs)
                    refreshUi()
                }
                dialog.dismiss()
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    /** 测试图床连通性。 */
    private fun doTest() {
        val url = Prefs.imageUrl(this)
        if (url.isBlank()) {
            Toast.makeText(this, R.string.no_url, Toast.LENGTH_SHORT).show()
            return
        }
        val tv = findViewById<android.widget.TextView>(R.id.tvTestTitle)
        val original = tv.text
        tv.text = getString(R.string.test_running)

        scope.launch {
            val msg = withContext(Dispatchers.IO) {
                try {
                    val list = ImageFetcher().fetch(url, Prefs.sourceMode(this@SettingsActivity))
                    getString(R.string.test_ok, list.size)
                } catch (t: Throwable) {
                    getString(R.string.test_fail, t.message ?: "?")
                }
            }
            Toast.makeText(this@SettingsActivity, msg, Toast.LENGTH_LONG).show()
            tv.text = original
        }
    }

    private fun openDreamSettings() {
        val ok = DreamSettingsHelper.openDreamSettings(this)
        if (!ok) {
            Toast.makeText(this, R.string.dream_settings_unavailable, Toast.LENGTH_LONG).show()
        }
    }

    /** 检查 GitHub 最新 Release（含测试版）。 */
    private fun checkUpdate() {
        val tvTitle = findViewById<android.widget.TextView>(R.id.tvCheckUpdateTitle)
        val tvSummary = findViewById<android.widget.TextView>(R.id.tvCheckUpdateSummary)
        val originalTitle = tvTitle.text
        val originalSummary = tvSummary.text

        tvTitle.text = getString(R.string.pref_check_update_title)
        tvSummary.text = getString(R.string.update_checking)

        scope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    UpdateChecker.check(this@SettingsActivity)
                } catch (t: Throwable) {
                    null
                }
            }

            tvTitle.text = originalTitle
            tvSummary.text = originalSummary

            if (result == null) {
                Toast.makeText(this@SettingsActivity, getString(R.string.update_error, "网络异常"), Toast.LENGTH_LONG).show()
                return@launch
            }

            if (!result.hasUpdate) {
                Toast.makeText(
                    this@SettingsActivity,
                    getString(R.string.update_latest),
                    Toast.LENGTH_SHORT
                ).show()
                return@launch
            }

            val prereleaseTag = if (result.isPrerelease) " · ${getString(R.string.update_prerelease)}" else ""
            val message = getString(
                R.string.update_dialog_message,
                result.currentVersion,
                result.latestVersion,
                prereleaseTag
            )
            MaterialAlertDialogBuilder(this@SettingsActivity)
                .setTitle(R.string.update_dialog_title)
                .setMessage(message)
                .setPositiveButton(R.string.update_dialog_download) { _, _ ->
                    try {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(result.downloadUrl)))
                    } catch (_: Throwable) {
                        Toast.makeText(this@SettingsActivity, "无法打开浏览器", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton(R.string.update_dialog_later, null)
                .show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
