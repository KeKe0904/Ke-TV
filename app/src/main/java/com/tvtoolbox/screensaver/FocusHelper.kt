package com.tvtoolbox.screensaver

import android.content.Context
import android.media.AudioManager
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.SoundEffectConstants
import android.view.View
import android.view.animation.OvershootInterpolator
import androidx.core.content.ContextCompat

/**
 * TV 焦点导航 + 手机端按压反馈工具。
 *
 * 设计区分：
 * - **TV**：
 *   1. 获得焦点时切到 glass_card_focus_bg（蓝色发光描边），并放大 1.04x + 抬升阴影，
 *      让用户在 3 米外也能看清当前焦点。
 *   2. 焦点切换瞬间触发触觉反馈（HapticFeedback）+ 系统按键音，给用户"切了"的确定感。
 *   3. 按 D-pad 中央键 / 回车键时，按下 → 缩小 0.95x，抬起 → 回弹 1.04x，
 *      给用户"我点中了"的清晰视觉反馈（TV 上没有 touch 事件，必须用按键监听）。
 *   4. 焦点切换用 OvershootInterpolator 增加弹性。
 * - **手机**：不放大（避免卡片超出屏幕边缘），只在按下时轻微缩小 0.97x 模拟按压感，
 *   抬起时回弹。背景保持 ripple 不变。
 *
 * View 必须 focusable=true, clickable=true。
 *
 * **OnKeyListener 冲突说明**：
 * [setupFocus] 内部会调用 [attachConfirmKeyFeedback] 设置一个 OnKeyListener 处理按压反馈。
 * 如果外部在 setupFocus 之后再次调用 `view.setOnKeyListener { ... }`，
 * 会覆盖 setupFocus 的 listener，导致按压反馈失效。
 * 此时外部 listener 内应主动调用 [handleConfirmKeyFeedback] 保留按压反馈：
 * ```
 * view.setOnKeyListener { v, keyCode, event ->
 *     FocusHelper.handleConfirmKeyFeedback(v, keyCode, event)
 *     // ... 自定义逻辑
 *     false
 * }
 * ```
 */
object FocusHelper {

    /**
     * 给单个 View 配置焦点/按压视觉。
     * 根据设备类型自动选择不同效果。
     */
    fun setupFocus(view: View) {
        val isTv = isTv(view.context)

        if (isTv) {
            // TV：焦点放大 + 发光描边 + 触觉/音效 + D-pad 按下回弹反馈
            view.setOnFocusChangeListener { v, hasFocus ->
                if (hasFocus) {
                    v.background = ContextCompat.getDrawable(v.context, R.drawable.glass_card_focus_bg)
                    v.animate()
                        .scaleX(1.04f).scaleY(1.04f)
                        .setDuration(220)
                        .setInterpolator(OvershootInterpolator(1.2f))
                        .start()
                    v.elevation = 16f
                    // 焦点切换瞬间反馈：让用户感觉到"焦点到了"
                    // 使用 TYPE_CLOCK_TICK 比较轻微，不会扰民
                    try {
                        v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    } catch (_: Throwable) {
                    }
                    // 播放系统焦点移动音效（受系统音量控制）
                    playFocusSound(v.context)
                } else {
                    v.background = ContextCompat.getDrawable(v.context, R.drawable.glass_card_ripple)
                    v.animate()
                        .scaleX(1.0f).scaleY(1.0f)
                        .setDuration(180)
                        .start()
                    v.elevation = 0f
                }
            }

            // TV D-pad 确认键的"按压→回弹"视觉反馈。
            // 这是用户第四轮要求"优化点击更多后的交互反馈"的核心：
            // TV 上点击只有 performClick，没有 touch 事件，所以视觉上看不出"我点中了"。
            // 通过监听 KEYCODE_DPAD_CENTER / KEYCODE_ENTER 的 DOWN / UP 来模拟按压动画。
            // 注意：外部如果再次 setOnKeyListener 会覆盖此 listener，
            // 应在外部 listener 内调用 handleConfirmKeyFeedback 保留按压反馈。
            attachConfirmKeyFeedback(view)
        } else {
            // 手机：保持背景不变，仅按下时缩小
            view.setOnFocusChangeListener(null)
            view.setOnTouchListener { v, event ->
                when (event.action) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        v.animate()
                            .scaleX(0.97f).scaleY(0.97f)
                            .setDuration(120)
                            .setInterpolator(OvershootInterpolator(0.6f))
                            .start()
                        v.elevation = 4f
                    }
                    android.view.MotionEvent.ACTION_UP,
                    android.view.MotionEvent.ACTION_CANCEL -> {
                        v.animate()
                            .scaleX(1.0f).scaleY(1.0f)
                            .setDuration(180)
                            .setInterpolator(OvershootInterpolator(1.4f))
                            .start()
                        v.elevation = 0f
                    }
                }
                // 返回 false 让 OnClickListener 仍然能收到点击事件
                false
            }
        }

        view.isFocusable = true
        // 关键修复：手机端关闭 touch-mode focusable
        // 之前 isFocusableInTouchMode=true 会导致首次点击只获取焦点而不触发 OnClickListener，
        // 用户必须点两次才能进入功能。TV 上保留 true（D-pad 导航需要），手机上设为 false。
        view.isFocusableInTouchMode = isTv
    }

    /**
     * 给一组 View（如设置页的行）批量配置焦点/按压。
     */
    fun setupFocusAll(vararg views: View) {
        views.forEach { setupFocus(it) }
    }

    /**
     * 默认聚焦到第一个可聚焦 View。延迟 100ms 等 View attached。
     */
    fun requestInitialFocus(vararg views: View) {
        views.firstOrNull { it.visibility == View.VISIBLE }?.let { target ->
            target.postDelayed({
                target.requestFocus()
            }, 100)
        }
    }

    /**
     * 判断当前设备是否为 TV（leanback 特性）。
     */
    fun isTv(context: Context): Boolean =
        context.packageManager.hasSystemFeature("android.software.leanback")

    /**
     * 给 View 自动绑上 D-pad 确认键的按压→回弹视觉反馈。
     * 适用于没有自定义 OnKeyListener 需求的场景（如 MainActivity 卡片）。
     *
     * 若外部还需要自定义 OnKeyListener（如设置页 row 的左右键切换值），
     * 应在自定义 listener 内调用 [handleConfirmKeyFeedback] 保留反馈。
     */
    fun attachConfirmKeyFeedback(view: View) {
        view.setOnKeyListener { v, keyCode, event ->
            handleConfirmKeyFeedback(v, keyCode, event)
            // 返回 false 让 OnClickListener 仍能收到点击事件
            false
        }
    }

    /**
     * D-pad 确认键的按压→回弹视觉反馈逻辑。
     *
     * 外部 OnKeyListener 应在第一行调用此方法并保留其副作用，
     * 然后再处理自己的逻辑（如返回 true 消费 LEFT/RIGHT 切换值）。
     *
     * 用法：
     * ```
     * view.setOnKeyListener { v, keyCode, event ->
     *     FocusHelper.handleConfirmKeyFeedback(v, keyCode, event)
     *     if (event.action == KeyEvent.ACTION_UP && keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
     *         // ... 切换值
     *         return@setOnKeyListener true
     *     }
     *     false
     * }
     * ```
     */
    fun handleConfirmKeyFeedback(v: View, keyCode: Int, event: KeyEvent) {
        if (event.action == KeyEvent.ACTION_DOWN &&
            (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER)
        ) {
            // 按下：缩小到 0.95x，模拟"按下去"
            v.animate()
                .scaleX(0.95f).scaleY(0.95f)
                .setDuration(80)
                .start()
            v.elevation = 8f
            // 按下时也来一次触觉反馈
            try {
                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            } catch (_: Throwable) {
            }
        } else if (event.action == KeyEvent.ACTION_UP &&
            (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER)
        ) {
            // 抬起：回弹到 1.04x（焦点态），带 OvershootInterpolator 让回弹有弹性
            v.animate()
                .scaleX(1.04f).scaleY(1.04f)
                .setDuration(220)
                .setInterpolator(OvershootInterpolator(1.4f))
                .start()
            v.elevation = 16f
        }
    }

    /**
     * 播放系统"焦点移动"音效，受系统媒体音量控制。
     * 失败时静默忽略，不影响功能。
     */
    private fun playFocusSound(context: Context) {
        try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                ?: return
            // 仅在系统音量不为 0 时播放，避免静音环境下突兀
            if (am.getStreamVolume(AudioManager.STREAM_MUSIC) == 0) return
            am.playSoundEffect(SoundEffectConstants.NAVIGATION_DOWN, -1f)
        } catch (_: Throwable) {
            // 某些设备 / ROM 可能没有这个音效，静默忽略
        }
    }
}
