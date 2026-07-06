package com.tvtoolbox.screensaver

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.service.dreams.DreamService
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import coil.ImageLoader
import coil.request.ImageRequest
import coil.size.Scale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.random.Random

class PhotoDreamService : DreamService() {

    companion object {
        private const val TAG = "PhotoDream"
    }

    private lateinit var root: FrameLayout
    private lateinit var imageView: ImageView
    private lateinit var hintView: TextView

    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var urls: List<String> = emptyList()
    private var index: Int = 0
    private var nextRunnable: Runnable? = null
    private var currentAnim: ObjectAnimator? = null
    private var imageLoader: ImageLoader? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true

        root = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        imageView = ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            scaleType = ImageView.ScaleType.FIT_CENTER
            visibility = View.INVISIBLE
        }

        hintView = TextView(this).apply {
            text = getString(R.string.loading)
            setTextColor(Color.WHITE)
            textSize = 18f
            gravity = android.view.Gravity.CENTER
            setPadding(32, 32, 32, 32)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                android.view.Gravity.CENTER
            )
        }

        root.addView(imageView)
        root.addView(hintView)
        setContentView(root)
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        Log.d(TAG, "dream started")

        imageLoader = ImageLoader.Builder(this)
            .crossfade(true)
            .build()

        val url = Prefs.imageUrl(this)
        if (url.isBlank()) {
            showHint(getString(R.string.no_url))
            return
        }

        showHint(getString(R.string.loading))
        scope.launch {
            try {
                val fetched = ImageFetcher().fetch(url)
                urls = if (Prefs.randomOrder(this@PhotoDreamService)) {
                    fetched.shuffled(Random(System.currentTimeMillis()))
                } else {
                    fetched
                }
                if (urls.isEmpty()) {
                    showHint(getString(R.string.empty))
                    return@launch
                }
                hideHint()
                index = 0
                showCurrent()
                scheduleNext()
            } catch (t: Throwable) {
                Log.e(TAG, "fetch failed", t)
                showHint(getString(R.string.test_fail, t.message ?: "未知错误"))
            }
        }
    }

    override fun onDreamingStopped() {
        super.onDreamingStopped()
        Log.d(TAG, "dream stopped")
        nextRunnable?.let { handler.removeCallbacks(it) }
        nextRunnable = null
        currentAnim?.cancel()
        imageLoader?.shutdown()
        imageLoader = null
        scope.cancel()
    }

    private fun showCurrent() {
        if (urls.isEmpty()) return
        val safeIndex = ((index % urls.size) + urls.size) % urls.size
        val u = urls[safeIndex]
        Log.d(TAG, "show $u")

        val req = ImageRequest.Builder(this)
            .data(u)
            .scale(Scale.FIT)
            .target(
                onStart = {
                    // loading; keep current image visible if any
                },
                onSuccess = { result ->
                    imageView.setImageDrawable(result)
                    imageView.visibility = View.VISIBLE
                    if (Prefs.kenBurns(this)) {
                        startKenBurns()
                    }
                },
                onError = { _ ->
                    // 跳过失败的图
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
        val seconds = Prefs.intervalSeconds(this)
        handler.postDelayed(r, seconds * 1000L)
    }

    private fun startKenBurns() {
        currentAnim?.cancel()
        imageView.translationX = 0f
        imageView.translationY = 0f
        imageView.scaleX = 1f
        imageView.scaleY = 1f

        val random = Random(System.currentTimeMillis())
        val targetScale = 1.05f + random.nextFloat() * 0.15f   // 1.05 - 1.20
        val dx = (random.nextFloat() - 0.5f) * 80f             // -40 .. 40
        val dy = (random.nextFloat() - 0.5f) * 80f

        val duration = (Prefs.intervalSeconds(this) * 1000L).coerceAtLeast(3000L)

        val sx = ObjectAnimator.ofFloat(imageView, View.SCALE_X, 1f, targetScale)
        val sy = ObjectAnimator.ofFloat(imageView, View.SCALE_Y, 1f, targetScale)
        val tx = ObjectAnimator.ofFloat(imageView, View.TRANSLATION_X, 0f, dx)
        val ty = ObjectAnimator.ofFloat(imageView, View.TRANSLATION_Y, 0f, dy)

        listOf(sx, sy, tx, ty).forEach {
            it.duration = duration
            it.start()
        }
        currentAnim = sx // 用一个引用便于 cancel（其他几个会自然结束）
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
