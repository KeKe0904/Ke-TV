package com.tvtoolbox.screensaver

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat

/**
 * TV 焦点导航工具。
 *
 * 用法：
 * 1. 卡片 View 调用 [setupFocus]：获得焦点时显示明显的发光描边，并放大。
 * 2. 列表容器调用 [setupVerticalFocus]：自动让 D-pad 上下移动焦点。
 * 3. 进入页面时调用 [requestInitialFocus]：默认选中第一个可聚焦项。
 */
object FocusHelper {

    /**
     * 给单个 View 配置焦点视觉：
     * - 获得焦点时切到 glass_card_focus_bg + 轻微放大 1.04x
     * - 失去焦点时恢复 glass_card_ripple 原样
     *
     * View 必须 focusable=true, clickable=true。
     */
    fun setupFocus(view: View) {
        view.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                v.background = ContextCompat.getDrawable(v.context, R.drawable.glass_card_focus_bg)
                v.animate().scaleX(1.04f).scaleY(1.04f).setDuration(150).start()
                v.elevation = 12f
            } else {
                v.background = ContextCompat.getDrawable(v.context, R.drawable.glass_card_ripple)
                v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start()
                v.elevation = 0f
            }
        }
        // 让 View 自身可聚焦
        view.isFocusable = true
        view.isFocusableInTouchMode = true
    }

    /**
     * 给一组 View（如设置页的行）批量配置焦点。
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
