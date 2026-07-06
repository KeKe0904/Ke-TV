package com.tvtoolbox.screensaver

import android.animation.ValueAnimator
import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.sin

/**
 * 液态玻璃背景 View。
 *
 * 4 个彩色光晕在背景上缓慢漂浮，模拟 macOS Sonoma / iOS 流光效果。
 * 背景纯色：系统夜间模式 → 纯黑；白天模式 → 纯白（默认）。
 * 光晕在白底上更柔和、alpha 略低；黑底上更通透。
 */
class LiquidBackgroundView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private data class Blob(
        val baseX: Float,        // 0..1 归一化位置
        val baseY: Float,
        val radiusRatio: Float,  // 相对短边
        val color: Int,
        val phase: Float,
        val driftX: Float,       // 漂浮幅度
        val driftY: Float,
        val speed: Float
    )

    private val blobs = listOf(
        Blob(0.18f, 0.28f, 0.65f, 0xFF5B8DEF.toInt(), 0.0f, 0.12f, 0.10f, 0.00018f),
        Blob(0.82f, 0.22f, 0.55f, 0xFFBF5AF2.toInt(), 1.6f, 0.10f, 0.13f, 0.00022f),
        Blob(0.74f, 0.80f, 0.60f, 0xFFFF375F.toInt(), 3.1f, 0.14f, 0.09f, 0.00020f),
        Blob(0.26f, 0.78f, 0.52f, 0xFF30D158.toInt(), 4.7f, 0.11f, 0.12f, 0.00025f)
    )

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var time = 0f

    /** 强制夜间（黑色背景）。屏保（DreamService）通常设为 true，避免白光刺眼。 */
    var forceNight: Boolean = false

    /** 当前是否为夜间模式：forceNight 优先，否则跟随系统 uiMode。 */
    private val isNight: Boolean
        get() = forceNight ||
            (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    private val animator = ValueAnimator.ofFloat(0f, (Math.PI * 2.0 * 60.0).toFloat()).apply {
        duration = 120000L
        repeatCount = ValueAnimator.INFINITE
        interpolator = android.view.animation.LinearInterpolator()
        addUpdateListener {
            time = it.animatedValue as Float
            invalidate()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        animator.start()
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val night = isNight

        // 纯色基底：夜间纯黑，白天纯白
        canvas.drawColor(if (night) Color.BLACK else Color.WHITE)

        val w = width.toFloat()
        val h = height.toFloat()
        val shortEdge = minOf(w, h)

        // 白底上光晕更柔和（alpha 低），黑底上更通透（alpha 高）
        val blobAlpha = if (night) 130 else 110
        for (b in blobs) {
            val phase = time * b.speed + b.phase
            val cx = (b.baseX + b.driftX * sin(phase * 1.7f).toFloat()) * w
            val cy = (b.baseY + b.driftY * cos(phase * 1.3f).toFloat()) * h
            val r = b.radiusRatio * shortEdge

            paint.shader = RadialGradient(
                cx, cy, r,
                intArrayOf(b.color, Color.TRANSPARENT),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP
            )
            paint.alpha = blobAlpha
            canvas.drawRect(0f, 0f, w, h, paint)
        }

        // 顶部一层轻微暗化（夜间）/ 暗化（白天），保证文字对比度
        paint.shader = null
        paint.alpha = if (night) 40 else 18
        canvas.drawColor(if (night) 0x08000000 else 0x10000000)
    }
}
