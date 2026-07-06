package com.tvtoolbox.screensaver

import android.os.Bundle
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity

/**
 * 手机端预览屏保效果。全屏显示，触屏单击退出，自动轮播图片。
 * 复用 PhotoSlideshowController，效果与 TV 屏保完全一致。
 */
class PhonePreviewActivity : AppCompatActivity() {

    private var controller: PhotoSlideshowController? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 全屏沉浸
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
                or android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        )

        val root = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        setContentView(root)

        controller = PhotoSlideshowController(this, root).also {
            it.attachViews()
            it.start()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        controller?.release()
        controller = null
    }

    /** 手机端：触屏单击即退出预览。 */
    override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
        if (event.action == android.view.MotionEvent.ACTION_UP) {
            finish()
            return true
        }
        return super.onTouchEvent(event)
    }

    /** 物理按键也退出（便于测试）。 */
    override fun onBackPressed() {
        super.onBackPressed()
    }
}
