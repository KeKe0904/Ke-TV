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
 * 设计原则：
 * 1. 背景必须首先是纯色：夜间纯黑，白天纯白。
 * 2. 彩色光晕只是极淡的氛围点缀，不能喧宾夺主。
 * 3. 默认状态下用户应明显看到纯黑/纯白，而非彩色渐变。
 */
class LiquidBackgroundView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private data class Blob(
        val baseX: Float,
        val baseY: Float,
        val radiusRatio: Float,
        val color: Int,
        val phase: Float,
        val driftX: Float,
        val driftY: Float,
        val speed: Float
    )

    /** 光晕位置更靠边、面积更大、移动更慢，存在感极低。 */
    private val blobs = listOf(
        Blob(0.10f, 0.15f, 0.90f, 0xFF5B8DEF.toInt(), 0.0f, 0.08f, 0.08f, 0.00012f),
        Blob(0.90f, 0.12f, 0.80f, 0xFFBF5AF2.toInt(), 2.2f, 0.07f, 0.09f, 0.00014f),
        Blob(0.85f, 0.88f, 0.85f, 0xFFFF375F.toInt(), 4.1f, 0.09f, 0.07f, 0.00013f),
        Blob(0.12f, 0.85f, 0.75f, 0xFF30D158.toInt(), 5.7f, 0.08f, 0.08f, 0.00015f)
    )

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var time = 0f

    /** 强制夜间（黑色背景）。屏保（DreamService）通常设为 true。 */
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

        // 第一步：铺满纯色基底，这是用户看到的主体背景
        canvas.drawColor(if (night) Color.BLACK else Color.WHITE)

        val w = width.toFloat()
        val h = height.toFloat()
        val shortEdge = minOf(w, h)

        // 第二步：在角落画极淡的彩色光晕，alpha 非常低，只是氛围
        // 夜间 alpha 22，白天 alpha 16，确保不会掩盖纯黑/纯白
        val blobAlpha = if (night) 22 else 16
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

        // 第三步：顶部叠加一层几乎看不见的暗化/亮化蒙版，保证文字对比度
        paint.shader = null
        paint.alpha = if (night) 12 else 6
        canvas.drawColor(if (night) 0x08000000 else 0x10000000)
    }
}
