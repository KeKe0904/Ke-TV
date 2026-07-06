package com.tvtoolbox.screensaver

import android.content.Intent
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

    override fun onCreate(savedInstanceState: Bundle?) {
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
        // 图床类型
        findViewById<View>(R.id.rowSourceMode).setOnClickListener { showSourceModeDialog() }
        // 图床 URL
        findViewById<View>(R.id.rowImageUrl).setOnClickListener { showUrlDialog() }
        // 切换间隔
        findViewById<View>(R.id.rowInterval).setOnClickListener { showIntervalDialog() }
        // 随机顺序：点击卡片切换开关
        findViewById<View>(R.id.rowRandom).setOnClickListener {
            val cur = Prefs.randomOrder(this)
            Prefs.setRandomOrder(this, !cur)
            refreshUi()
        }
        // Ken Burns
        findViewById<View>(R.id.rowKenBurns).setOnClickListener {
            val cur = Prefs.kenBurns(this)
            Prefs.setKenBurns(this, !cur)
            refreshUi()
        }
        // 测试连通性
        findViewById<View>(R.id.rowTest).setOnClickListener { doTest() }
        // 系统屏保设置
        findViewById<View>(R.id.rowDreamSettings).setOnClickListener { openDreamSettings() }
    }

    /** 刷新所有行的显示状态。 */
    private fun refreshUi() {
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
        try {
            startActivity(Intent(Settings.ACTION_DREAM_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (t: Throwable) {
            try {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            } catch (_: Throwable) {
                Toast.makeText(this, "无法打开系统设置", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
