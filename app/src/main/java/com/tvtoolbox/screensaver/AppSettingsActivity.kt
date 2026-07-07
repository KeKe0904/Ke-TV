package com.tvtoolbox.screensaver

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 软件设置页：与屏保设置独立的「软件级」设置入口。
 *
 * 当前包含：
 * 1. 主题模式（白天/夜间/跟随系统）
 * 2. 软件更新（in-app 下载 APK + 触发系统安装器，不再跳浏览器）
 */
class AppSettingsActivity : AppCompatActivity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val themeModeEntries by lazy { resources.getStringArray(R.array.theme_mode_entries) }
    private val themeModeValues by lazy { resources.getStringArray(R.array.theme_mode_values) }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.apply(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_settings)

        setSupportActionBar(findViewById(R.id.toolbar_settings))
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = getString(R.string.app_settings_title)
        }
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

        // 软件更新
        findViewById<View>(R.id.rowCheckUpdate).also {
            it.setOnClickListener { checkUpdate() }
            FocusHelper.setupFocus(it)
        }

        // TV 进入设置页时，默认聚焦到第一个 row
        FocusHelper.requestInitialFocus(findViewById(R.id.rowThemeMode))
    }

    private fun refreshUi() {
        // 主题模式
        val tm = Prefs.themeMode(this)
        val tmIdx = themeModeValues.indexOf(tm).coerceAtLeast(0)
        val tmText = if (tmIdx in themeModeEntries.indices) themeModeEntries[tmIdx]
            else themeModeEntries[0]
        findViewById<TextView>(R.id.tvThemeModeValue).text = tmText
    }

    /** 主题模式单选对话框。切换后立即重建 Activity 让背景生效。 */
    private fun showThemeModeDialog() {
        val cur = Prefs.themeMode(this)
        val checked = themeModeValues.indexOf(cur).coerceAtLeast(0)
        showAppSingleChoice(
            title = getString(R.string.dialog_theme_mode_title),
            entries = themeModeEntries,
            checkedIndex = checked,
            onSelected = { which ->
                if (which in themeModeValues.indices) {
                    Prefs.setThemeMode(this, themeModeValues[which])
                    // 立即应用主题模式并重建 Activity，让背景纯黑/纯白立刻生效
                    ThemeHelper.apply(this)
                    recreate()
                }
            }
        )
    }

    /** 检查并下载安装最新版本（in-app 流程）。 */
    private fun checkUpdate() {
        UpdateManager.checkAndInstall(
            activity = this,
            scope = scope,
            onStatusChange = { message ->
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            }
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
