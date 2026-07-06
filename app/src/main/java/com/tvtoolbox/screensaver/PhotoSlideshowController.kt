package com.tvtoolbox.screensaver

import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import coil.ImageLoader
import coil.request.ImageRequest
import coil.size.Scale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * 图片轮播控制器。共用逻辑，供 DreamService（TV 屏保）和 PhonePreviewActivity（手机预览）复用。
 *
 * 用法：
 *   val controller = PhotoSlideshowController(context, container)
 *   controller.start()       // 开始（拉取 + 轮播）
 *   controller.stop()        // 停止
 *   controller.release()     // 释放资源
 */
class PhotoSlideshowController(
    private val context: Context,
    private val container: FrameLayout
) {
    companion object {
        private const val TAG = "PhotoSlideshow"
    }

    private lateinit var imageView: ImageView
    private lateinit var hintView: TextView

    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var urls: List<String> = emptyList()
    private var index: Int = 0
    private var nextRunnable: Runnable? = null
    private var currentAnim: ObjectAnimator? = null
    private var imageLoader: ImageLoader? = null
    private var running: Boolean = false

    /** 把 ImageView 和提示 TextView 加到 container。调用一次即可。 */
    fun attachViews() {
        imageView = ImageView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            scaleType = ImageView.ScaleType.FIT_CENTER
            visibility = View.INVISIBLE
            setBackgroundColor(Color.BLACK)
        }

        hintView = TextView(context).apply {
            text = context.getString(R.string.loading)
            setTextColor(Color.WHITE)
            textSize = 18f
            gravity = android.view.Gravity.CENTER
            setPadding(48, 48, 48, 48)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                android.view.Gravity.CENTER
            )
        }

        container.removeAllViews()
        container.addView(imageView)
        container.addView(hintView)
    }

    /** 开始：拉取图床并启动轮播。 */
    fun start() {
        if (running) return
        running = true

        imageLoader = ImageLoader.Builder(context)
            .crossfade(true)
            .build()

        val url = Prefs.imageUrl(context)
        if (url.isBlank()) {
            showHint(context.getString(R.string.no_url))
            return
        }

        showHint(context.getString(R.string.loading))
        scope.launch {
            try {
                val fetched = ImageFetcher().fetch(url)
                urls = if (Prefs.randomOrder(context)) {
                    fetched.shuffled(Random(System.currentTimeMillis()))
                } else {
                    fetched
                }
                if (urls.isEmpty()) {
                    showHint(context.getString(R.string.empty))
                    return@launch
                }
                hideHint()
                index = 0
                showCurrent()
                scheduleNext()
            } catch (t: Throwable) {
                Log.e(TAG, "fetch failed", t)
                showHint(context.getString(R.string.test_fail, t.message ?: "未知错误"))
            }
        }
    }

    /** 停止轮播（可再 start 恢复）。 */
    fun stop() {
        running = false
        nextRunnable?.let { handler.removeCallbacks(it) }
        nextRunnable = null
        currentAnim?.cancel()
    }

    /** 彻底释放资源。 */
    fun release() {
        stop()
        imageLoader?.shutdown()
        imageLoader = null
        scope.cancel()
    }

    private fun showCurrent() {
        if (urls.isEmpty()) return
        val safeIndex = ((index % urls.size) + urls.size) % urls.size
        val u = urls[safeIndex]
        Log.d(TAG, "show $u")

        val req = ImageRequest.Builder(context)
            .data(u)
            .scale(Scale.FIT)
            .target(
                onStart = { /* loading */ },
                onSuccess = { result ->
                    imageView.setImageDrawable(result)
                    imageView.visibility = View.VISIBLE
                    if (Prefs.kenBurns(context)) {
                        startKenBurns()
                    }
                },
                onError = { _ ->
                    index++
                    if (urls.isNotEmpty()) showCurrent()
                }
            )
            .build()
        imageLoader?.enqueue(req)
    }

    private fun scheduleNext() {
        nextRunnable?.let { handler.removeCallbacks(it) }
        val r = Runnable {
            index++
            showCurrent()
            scheduleNext()
        }
        nextRunnable = r
        val seconds = Prefs.intervalSeconds(context)
        handler.postDelayed(r, seconds * 1000L)
    }

    private fun startKenBurns() {
        currentAnim?.cancel()
        imageView.translationX = 0f
        imageView.translationY = 0f
        imageView.scaleX = 1f
        imageView.scaleY = 1f

        val random = Random(System.currentTimeMillis())
        val targetScale = 1.05f + random.nextFloat() * 0.15f
        val dx = (random.nextFloat() - 0.5f) * 80f
        val dy = (random.nextFloat() - 0.5f) * 80f
        val duration = (Prefs.intervalSeconds(context) * 1000L).coerceAtLeast(3000L)

        listOf(
            ObjectAnimator.ofFloat(imageView, View.SCALE_X, 1f, targetScale),
            ObjectAnimator.ofFloat(imageView, View.SCALE_Y, 1f, targetScale),
            ObjectAnimator.ofFloat(imageView, View.TRANSLATION_X, 0f, dx),
            ObjectAnimator.ofFloat(imageView, View.TRANSLATION_Y, 0f, dy)
        ).forEach {
            it.duration = duration
            it.start()
        }
    }

    private fun showHint(text: String) {
        hintView.text = text
        hintView.visibility = View.VISIBLE
        imageView.visibility = View.INVISIBLE
    }

    private fun hideHint() {
        hintView.visibility = View.GONE
    }
}
