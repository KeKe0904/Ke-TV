package com.tvtoolbox.screensaver

import android.service.dreams.DreamService
import android.util.Log
import android.widget.FrameLayout

class PhotoDreamService : DreamService() {

    companion object {
        private const val TAG = "PhotoDream"
    }

    private var controller: PhotoSlideshowController? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true

        val root = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        setContentView(root)

        controller = PhotoSlideshowController(this, root).also {
            it.attachViews()
        }
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        Log.d(TAG, "dream started")
        controller?.start()
    }

    override fun onDreamingStopped() {
        super.onDreamingStopped()
        Log.d(TAG, "dream stopped")
        controller?.stop()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        controller?.release()
        controller = null
    }
}
