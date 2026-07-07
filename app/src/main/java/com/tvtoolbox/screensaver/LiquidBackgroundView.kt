package com.tvtoolbox.screensaver

import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.Choreographer
import kotlin.math.cos
import kotlin.math.sin

/**
 * 液态玻璃背景 View。
 *
 * 设计原则：
 * 1. 背景必须首先是纯色：夜间纯黑，白天纯白
 * 2. 彩色光晕只是极淡的氛围点缀，alpha 极低
 *
 * 性能优化（v1.6.3）：
 * - Shader 缓存：避免每帧 4 次新建 RadialGradient（这是最大的卡顿元凶）
 * - 硬件层：开启 LAYER_TYPE_HARDWARE，让 GPU 缓存绘制结果
 * - 帧率限制：30fps 上限（背景动画人眼几乎察觉不到 60fps 与 30fps 差异）
 * - 按需暂停：不可见时停止动画（onVisibilityAggregated / onWindowVisibilityChanged）
 * - 减少 blob 数量：4 → 3，且光晕半径稍小
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

    /** 光晕数量从 4 减到 3，进一步降低每帧绘制开销。 */
    private val blobs = listOf(
        Blob(0.12f, 0.18f, 0.78f, 0xFF5B8DEF.toInt(), 0.0f, 0.08f, 0.08f, 0.00010f),
        Blob(0.88f, 0.15f, 0.72f, 0xFFBF5AF2.toInt(), 2.2f, 0.07f, 0.09f, 0.00012f),
        Blob(0.50f, 0.88f, 0.70f, 0xFFFF375F.toInt(), 4.1f, 0.09f, 0.07f, 0.00011f)
    )

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        // 启用双线性过滤，让光晕边缘更柔和
        isFilterBitmap = true
    }

    /** 缓存的 Shader 数组：blob 数量固定，按 index 复用。 */
    private val shaderCache = arrayOfNulls<RadialGradient>(blobs.size)
    /** 上次构建 Shader 时的尺寸，尺寸变化才重建。 */
    private var cachedWidth = -1f
    private var cachedHeight = -1f

    private var time = 0f

    /** 强制夜间（黑色背景）。屏保（DreamService）通常设为 true。 */
    var forceNight: Boolean = false

    private val isNight: Boolean
        get() = forceNight ||
            (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    /** 帧率限制：约 30fps（33ms 一帧），背景动画不需要 60fps。 */
    private val frameIntervalMs = 33L
    private var lastFrameTime = 0L

    /**
     * 用 Choreographer 而不是 ValueAnimator：
     * - 可以节流帧率（ValueAnimator 默认每帧都回调，无法跳帧）
     * - 不可见时停止回调，零开销
     */
    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            val now = frameTimeNanos / 1_000_000
            if (now - lastFrameTime >= frameIntervalMs) {
                lastFrameTime = now
                time = (now / 1000f)
                invalidate()
            }
            if (isAttachedToWindow && isShown) {
                Choreographer.getInstance().postFrameCallback(this)
            }
        }
    }

    init {
        // 不开启 LAYER_TYPE_HARDWARE：
        // RadialGradient 每帧位置变化，硬件层反而需要重新上传纹理，得不偿失
        // 保留默认软件绘制，靠 Shader 缓存 + 帧率限制已足够
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    override fun onDetachedFromWindow() {
        Choreographer.getInstance().removeFrameCallback(frameCallback)
        super.onDetachedFromWindow()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == VISIBLE && isAttachedToWindow) {
            Choreographer.getInstance().removeFrameCallback(frameCallback)
            Choreographer.getInstance().postFrameCallback(frameCallback)
        } else {
            Choreographer.getInstance().removeFrameCallback(frameCallback)
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // 尺寸变化时让 Shader 缓存失效，下次 onDraw 重建
        cachedWidth = -1f
        cachedHeight = -1f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val night = isNight

        // 第一步：铺满纯色基底
        canvas.drawColor(if (night) Color.BLACK else Color.WHITE)

        val w = width.toFloat()
        val h = height.toFloat()
        val shortEdge = minOf(w, h)

        // 重建 Shader 缓存（仅在尺寸变化时）
        if (w != cachedWidth || h != cachedHeight) {
            rebuildShaders(w, h, shortEdge)
            cachedWidth = w
            cachedHeight = h
        }

        // 第二步：画极淡彩色光晕，复用 Shader
        val blobAlpha = if (night) 22 else 16
        for ((i, b) in blobs.withIndex()) {
            val phase = time * b.speed + b.phase
            val cx = (b.baseX + b.driftX * sin(phase * 1.7f).toFloat()) * w
            val cy = (b.baseY + b.driftY * cos(phase * 1.3f).toFloat()) * h

            val shader = shaderCache[i]
            if (shader != null) {
                // 平移 Shader 到当前位置（避免每帧重建）
                val matrix = shaderMatrix
                matrix.reset()
                matrix.postTranslate(cx - b.baseX * w, cy - b.baseY * h)
                shader.setLocalMatrix(matrix)
                paint.shader = shader
                paint.alpha = blobAlpha
                canvas.drawRect(0f, 0f, w, h, paint)
            }
        }

        // 第三步：顶部叠加一层几乎看不见的暗化/亮化蒙版
        paint.shader = null
        paint.alpha = if (night) 12 else 6
        canvas.drawColor(if (night) 0x08000000 else 0x10000000)
    }

    /** 复用的 Matrix，避免每帧 new。 */
    private val shaderMatrix = android.graphics.Matrix()

    /** 重建所有光晕的 Shader（仅在尺寸变化时调用一次）。 */
    private fun rebuildShaders(w: Float, h: Float, shortEdge: Float) {
        for ((i, b) in blobs.withIndex()) {
            val cx = b.baseX * w
            val cy = b.baseY * h
            val r = b.radiusRatio * shortEdge
            shaderCache[i] = RadialGradient(
                cx, cy, r,
                intArrayOf(b.color, Color.TRANSPARENT),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP
            )
        }
    }
}
