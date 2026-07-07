package com.tvtoolbox.screensaver

import android.content.Context
import android.view.View
import android.view.animation.OvershootInterpolator
import androidx.core.content.ContextCompat

/**
 * TV 焦点导航 + 手机端按压反馈工具。
 *
 * 设计区分：
 * - **TV**：获得焦点时切到 glass_card_focus_bg（蓝色发光描边），并放大 1.05x + 抬升阴影，
 *   让用户在 3 米外也能看清当前焦点。焦点切换用 OvershootInterpolator 增加弹性。
 * - **手机**：不放大（避免卡片超出屏幕边缘），只在按下时轻微缩小 0.97x 模拟按压感，
 *   抬起时回弹。背景保持 ripple 不变。
 *
 * View 必须 focusable=true, clickable=true。
 */
object FocusHelper {

    /**
     * 给单个 View 配置焦点/按压视觉。
     * 根据设备类型自动选择不同效果。
     */
    fun setupFocus(view: View) {
        val isTv = isTv(view.context)

        if (isTv) {
            // TV：焦点放大 + 发光描边
            view.setOnFocusChangeListener { v, hasFocus ->
                if (hasFocus) {
                    v.background = ContextCompat.getDrawable(v.context, R.drawable.glass_card_focus_bg)
                    v.animate()
                        .scaleX(1.05f).scaleY(1.05f)
                        .setDuration(220)
                        .setInterpolator(OvershootInterpolator(1.2f))
                        .start()
                    v.elevation = 14f
                } else {
                    v.background = ContextCompat.getDrawable(v.context, R.drawable.glass_card_ripple)
                    v.animate()
                        .scaleX(1.0f).scaleY(1.0f)
                        .setDuration(180)
                        .start()
                    v.elevation = 0f
                }
            }
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
}
