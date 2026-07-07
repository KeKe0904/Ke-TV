package com.tvtoolbox.screensaver

import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.materialswitch.MaterialSwitch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 屏保设置页（屏保功能本身的配置）。
 *
 * 仅保留与屏保直接相关的设置：
 * - 图床类型 / 图床 URL / 切换间隔 / 随机顺序 / Ken Burns
 * - 测试连通性 / 系统屏保设置
 *
 * 主题模式与软件更新已迁移到 [AppSettingsActivity]，因为它们属于软件本身而非屏保功能。
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
        ThemeHelper.apply(this)
        super.onCreate(savedInstanceState)
        // 全屏：让背景层延伸到屏幕边缘，不被 system bar inset 推进
        // （移除了布局里的 fitsSystemWindows=true，配合 setDecorFitsSystemWindows(false)）
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_settings)

        setSupportActionBar(findViewById(R.id.toolbar_settings))
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(false)
            setDisplayShowHomeEnabled(false)
            // 用户要求顶部状态栏不显示任何文字
            setDisplayShowTitleEnabled(false)
            title = ""
        }
        // toolbar 单独处理状态栏 inset（顶部 padding）
        val toolbar = findViewById<View>(R.id.toolbar_settings)
        ViewCompat.setOnApplyWindowInsetsListener(toolbar) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, bars.top, v.paddingRight, v.paddingBottom)
            insets
        }

        bindRows()
    }

    override fun onResume() {
        super.onResume()
        refreshUi()
    }

    private fun bindRows() {
        val isTv = FocusHelper.isTv(this)

        // 图床类型：TV 上按左右键直接在值之间循环切换，无需打开对话框
        findViewById<View>(R.id.rowSourceMode).also { row ->
            row.setOnClickListener { showSourceModeDialog() }
            FocusHelper.setupFocus(row)
            if (isTv) {
                row.setOnKeyListener { v, keyCode, event ->
                    // 保留 setupFocus 的 D-pad 确认键按压反馈
                    FocusHelper.handleConfirmKeyFeedback(v, keyCode, event)
                    if (event.action == KeyEvent.ACTION_UP &&
                        (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT)
                    ) {
                        val cur = Prefs.sourceMode(this)
                        val idx = sourceModeValues.indexOf(cur).coerceAtLeast(0)
                        val newIdx = if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                            (idx + 1) % sourceModeValues.size
                        } else {
                            (idx - 1 + sourceModeValues.size) % sourceModeValues.size
                        }
                        Prefs.setSourceMode(this, sourceModeValues[newIdx])
                        refreshUi()
                        return@setOnKeyListener true
                    }
                    false
                }
            }
        }

        // 图床 URL：TV 上也保留点击弹出对话框（URL 需要输入），但优化了 dialog 焦点
        findViewById<View>(R.id.rowImageUrl).also {
            it.setOnClickListener { showUrlDialog() }
            FocusHelper.setupFocus(it)
        }

        // 切换间隔：TV 上按左右键直接增减
        findViewById<View>(R.id.rowInterval).also { row ->
            row.setOnClickListener { showIntervalDialog() }
            FocusHelper.setupFocus(row)
            if (isTv) {
                row.setOnKeyListener { v, keyCode, event ->
                    FocusHelper.handleConfirmKeyFeedback(v, keyCode, event)
                    if (event.action == KeyEvent.ACTION_UP &&
                        (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT)
                    ) {
                        val cur = Prefs.intervalSeconds(this)
                        val idx = intervalValues.indexOfFirst { it.toIntOrNull() == cur }.coerceAtLeast(0)
                        val newIdx = if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                            (idx + 1).coerceAtMost(intervalValues.size - 1)
                        } else {
                            (idx - 1).coerceAtLeast(0)
                        }
                        val newSeconds = intervalValues[newIdx].toIntOrNull() ?: cur
                        Prefs.setIntervalSeconds(this, newSeconds)
                        refreshUi()
                        return@setOnKeyListener true
                    }
                    false
                }
            }
        }

        // 随机顺序：点击切换开关
        findViewById<View>(R.id.rowRandom).also { row ->
            row.setOnClickListener {
                val cur = Prefs.randomOrder(this)
                Prefs.setRandomOrder(this, !cur)
                refreshUi()
            }
            FocusHelper.setupFocus(row)
            if (isTv) {
                row.setOnKeyListener { v, keyCode, event ->
                    FocusHelper.handleConfirmKeyFeedback(v, keyCode, event)
                    if (event.action == KeyEvent.ACTION_UP &&
                        (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT)
                    ) {
                        val cur = Prefs.randomOrder(this)
                        Prefs.setRandomOrder(this, !cur)
                        refreshUi()
                        return@setOnKeyListener true
                    }
                    false
                }
            }
        }

        // Ken Burns
        findViewById<View>(R.id.rowKenBurns).also { row ->
            row.setOnClickListener {
                val cur = Prefs.kenBurns(this)
                Prefs.setKenBurns(this, !cur)
                refreshUi()
            }
            FocusHelper.setupFocus(row)
            if (isTv) {
                row.setOnKeyListener { v, keyCode, event ->
                    FocusHelper.handleConfirmKeyFeedback(v, keyCode, event)
                    if (event.action == KeyEvent.ACTION_UP &&
                        (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT)
                    ) {
                        val cur = Prefs.kenBurns(this)
                        Prefs.setKenBurns(this, !cur)
                        refreshUi()
                        return@setOnKeyListener true
                    }
                    false
                }
            }
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

        // TV 进入设置页时，默认聚焦到第一个 row（让遥控器有起点）
        FocusHelper.requestInitialFocus(findViewById(R.id.rowSourceMode))
    }

    /** 刷新所有行的显示状态。 */
    private fun refreshUi() {
        // 图床类型
        val mode = Prefs.sourceMode(this)
        val modeIdx = sourceModeValues.indexOf(mode).coerceAtLeast(0)
        val modeText = if (modeIdx in sourceModeEntries.indices) sourceModeEntries[modeIdx]
            else getString(R.string.pref_source_mode_single)
        findViewById<android.widget.TextView>(R.id.tvSourceModeValue).text = modeText

        // URL：用户未自定义时显示"默认图床"提示，但仍可点击查看/修改
        val url = Prefs.imageUrl(this)
        val urlText = if (Prefs.isUsingDefaultImageUrl(this)) {
            getString(R.string.pref_image_url_default)
        } else {
            url
        }
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
        showAppSingleChoice(
            title = getString(R.string.dialog_source_mode_title),
            entries = sourceModeEntries,
            checkedIndex = checked,
            onSelected = { which ->
                if (which in sourceModeValues.indices) {
                    Prefs.setSourceMode(this, sourceModeValues[which])
                    refreshUi()
                }
            }
        )
    }

    /** 图床 URL 输入对话框。 */
    private fun showUrlDialog() {
        showAppInput(
            title = getString(R.string.dialog_url_title),
            hint = getString(R.string.pref_image_url_hint),
            // 初始文本展示当前实际生效的 URL（包括默认值），方便用户在其基础上修改
            initialText = Prefs.imageUrl(this),
            onSave = { newUrl ->
                Prefs.setImageUrl(this, newUrl)
                refreshUi()
                Toast.makeText(this, R.string.dialog_save, Toast.LENGTH_SHORT).show()
            }
        )
    }

    /** 切换间隔单选对话框。 */
    private fun showIntervalDialog() {
        val cur = Prefs.intervalSeconds(this)
        val checked = intervalValues.indexOfFirst { it.toIntOrNull() == cur }
        showAppSingleChoice(
            title = getString(R.string.dialog_interval_title),
            entries = intervalEntries,
            checkedIndex = checked,
            onSelected = { which ->
                intervalValues[which].toIntOrNull()?.let { secs ->
                    Prefs.setIntervalSeconds(this, secs)
                    refreshUi()
                }
            }
        )
    }

    /** 测试图床连通性。 */
    private fun doTest() {
        val url = Prefs.imageUrl(this)
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
            try {
                Toast.makeText(this@SettingsActivity, msg, Toast.LENGTH_LONG).show()
                tv.text = original
            } catch (_: Throwable) {
                // Activity 已经销毁，忽略
            }
        }
    }

    private fun openDreamSettings() {
        val ok = try {
            DreamSettingsHelper.openDreamSettings(this)
        } catch (_: Throwable) {
            false
        }
        if (!ok) {
            // 用应用内置弹窗替代 Toast，让 TV 上看得更清楚
            showAppMessage(
                title = getString(R.string.card_dream_settings_title),
                message = getString(R.string.dream_settings_fallback),
                positiveText = getString(R.string.card_start_screensaver_title),
                onPositive = {
                    try { DreamSettingsHelper.triggerScreensaverNow(this) } catch (_: Throwable) {}
                },
                negativeText = getString(R.string.dialog_cancel)
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
