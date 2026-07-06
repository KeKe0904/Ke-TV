package com.tvtoolbox.screensaver

import android.animation.ValueAnimator
import android.content.Context
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
 * 4 个彩色光晕在深色背景上缓慢漂浮，模拟 macOS Sonoma / iOS 流光效果。
 * 作为 Activity 的背景层，毛玻璃卡片叠在上面形成"液态玻璃"视觉。
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
        // 深色基底
        canvas.drawColor(0xFF08080C.toInt())

        val w = width.toFloat()
        val h = height.toFloat()
        val shortEdge = minOf(w, h)

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
            paint.alpha = 130
            canvas.drawRect(0f, 0f, w, h, paint)
        }

        // 顶部一层轻微暗化，保证文字对比度
        paint.shader = null
        paint.alpha = 40
        canvas.drawColor(0x08000000)
    }
}
