package com.tvtoolbox.screensaver

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

/**
 * 手机端预览屏保效果。全屏显示，触屏单击退出，自动轮播图片。
 * 复用 PhotoSlideshowController，效果与 TV 屏保完全一致。
 */
class PhonePreviewActivity : AppCompatActivity() {

    private var controller: PhotoSlideshowController? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.apply(this)
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        )

        val root = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        // 退出提示
        val hint = TextView(this).apply {
            text = getString(R.string.preview_hint)
            setTextColor(0xCCFFFFFF.toInt())
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(48, 24, 48, 24)
            background = getDrawable(R.drawable.glass_pill_bg)
            alpha = 0f
            translationY = 40f
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            ).apply { bottomMargin = 120 }
        }

        root.addView(hint)
        setContentView(root)

        // 退出提示入场 + 3 秒后淡出
        hint.animate().alpha(1f).translationY(0f).setDuration(500).setStartDelay(600)
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    Handler(Looper.getMainLooper()).postDelayed({
                        hint.animate().alpha(0f).translationY(40f).setDuration(500).start()
                    }, 3000)
                }
            }).start()

        controller = PhotoSlideshowController(this, root).also {
            it.attachViews()
            it.start()
        }

        // 处理底部导航栏 inset
        ViewCompat.setOnApplyWindowInsetsListener(hint) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.translationY = -bars.bottom.toFloat()
            insets
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        controller?.release()
        controller = null
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            finish()
            return true
        }
        return super.onTouchEvent(event)
    }
}
