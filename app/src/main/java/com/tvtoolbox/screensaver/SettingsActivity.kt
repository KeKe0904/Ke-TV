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

        // 图床类型：暂时固定为「单图 URL」，不允许切换
        // （用户要求："图床类型暂时选不了"，只保留 URL 选项）
        // 仍然显示该行作为信息展示，但不可点击、不响应 D-pad 切换
        findViewById<View>(R.id.rowSourceMode).also { row ->
            row.isClickable = false
            row.isFocusable = false
            // 强制写回 single 模式，保证一致性
            if (Prefs.sourceMode(this) != "single") {
                Prefs.setSourceMode(this, "single")
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
        // v1.7.4：改为引导式，告诉用户如何把本应用设为系统屏保
        // （小米/红米 TV 等通常需要用户在系统设置中手动选中本应用）
        findViewById<View>(R.id.rowDreamSettings).also {
            it.setOnClickListener { guideSetAsSystemDream() }
            FocusHelper.setupFocus(it)
        }

        // TV 进入设置页时，默认聚焦到第一个可聚焦 row（让遥控器有起点）
        // v1.7.4：rowSourceMode 已不可聚焦，改为聚焦到 rowImageUrl
        FocusHelper.requestInitialFocus(findViewById(R.id.rowImageUrl))
    }

    /** 刷新所有行的显示状态。 */
    private fun refreshUi() {
        // 图床类型：固定为「单图 URL（固定）」，提示用户暂时不可切换
        val modeText = getString(R.string.pref_source_mode_fixed)
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

    /**
     * 引导用户把本应用设为系统屏保（v1.7.4 新增，v1.7.5 强化）。
     *
     * 小米 / 红米 TV 等电视系统通常不允许 APP 直接修改系统屏保配置，
     * v1.7.5 新增「强制修改系统底层屏保」方案：
     *
     * 1. 检查是否有 WRITE_SECURE_SETTINGS 权限
     *    - 有 → 直接调用 setAsSystemDream 一键设为系统屏保
     *    - 无 → 显示 adb 授权引导对话框
     * 2. 用户在电脑用 adb 授予权限后，回到 APP 点击「一键设置」
     * 3. APP 直接修改 Settings.Secure.screensaver_* 配置项
     *
     * 这是无需 root 的最强方案，永久生效。
     */
    private fun guideSetAsSystemDream() {
        if (SystemDreamHelper.hasSecureSettingsPermission(this)) {
            // 已有权限：直接显示一键设置对话框
            showOneClickSetDreamDialog()
        } else {
            // 无权限：显示 adb 引导对话框
            showAdbGuideDialog()
        }
    }

    /**
     * 已获得 WRITE_SECURE_SETTINGS 权限时显示的对话框。
     * 用户点击「一键设为系统屏保」直接修改 Settings.Secure。
     */
    private fun showOneClickSetDreamDialog() {
        val isCurrent = SystemDreamHelper.isCurrentSystemDream(this)
        val isEnabled = SystemDreamHelper.isScreenSaverEnabled(this)
        val timeout = SystemDreamHelper.getScreenSaverTimeout(this)
        val timeoutText = if (timeout > 0) "${timeout / 1000 / 60} 分钟" else "未设置"
        val statusText = buildString {
            append("当前状态：\n")
            append("· 系统屏保：").append(if (isEnabled) "已启用" else "未启用").append("\n")
            append("· 屏保组件：").append(if (isCurrent) "✓ 已设为本应用" else "✗ 未设为本应用").append("\n")
            append("· 等待时间：").append(timeoutText)
        }

        showAppMessage(
            title = getString(R.string.dream_oneclick_title),
            message = statusText,
            positiveText = getString(R.string.dream_oneclick_set),
            onPositive = {
                val ok = try {
                    SystemDreamHelper.setAsSystemDream(this, 5 * 60 * 1000)
                } catch (_: Throwable) {
                    false
                }
                if (ok) {
                    showAppMessage(
                        title = getString(R.string.dream_oneclick_success_title),
                        message = getString(R.string.dream_oneclick_success_message)
                    )
                } else {
                    Toast.makeText(this, R.string.dream_oneclick_failed, Toast.LENGTH_LONG).show()
                }
            },
            negativeText = getString(R.string.dream_guide_preview_now),
            onNegative = {
                try { DreamSettingsHelper.triggerScreensaverNow(this) } catch (_: Throwable) {}
            }
        )
    }

    /**
     * 无 WRITE_SECURE_SETTINGS 权限时显示的对话框。
     * 引导用户用电脑 adb 授予权限，然后回到 APP 点击「已授权，重试」。
     */
    private fun showAdbGuideDialog() {
        showAppMessage(
            title = getString(R.string.dream_adb_guide_title),
            message = getString(R.string.dream_adb_guide_message),
            positiveText = getString(R.string.dream_adb_authorized),
            onPositive = {
                // 用户点击「已授权，重试」→ 重新检查权限
                if (SystemDreamHelper.hasSecureSettingsPermission(this)) {
                    showOneClickSetDreamDialog()
                } else {
                    Toast.makeText(this, R.string.dream_adb_still_no_permission, Toast.LENGTH_LONG).show()
                }
            },
            negativeText = getString(R.string.dream_guide_preview_now),
            onNegative = {
                try { DreamSettingsHelper.triggerScreensaverNow(this) } catch (_: Throwable) {}
            }
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
