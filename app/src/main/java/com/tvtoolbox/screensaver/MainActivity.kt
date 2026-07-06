package com.tvtoolbox.screensaver

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.apply(this)
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        if (isTv()) {
            // TV: 直接进设置页
            startActivity(Intent(this, SettingsActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_main)

        // 处理状态栏 insets，给 toolbar 加顶部 padding
        val toolbar = findViewById<View>(R.id.toolbar)
        ViewCompat.setOnApplyWindowInsetsListener(toolbar) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, bars.top, v.paddingRight, v.paddingBottom)
            insets
        }

        // 卡片点击
        findViewById<View>(R.id.cardStatus).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        findViewById<View>(R.id.cardPreview).setOnClickListener {
            startActivity(Intent(this, PhonePreviewActivity::class.java))
        }
        findViewById<View>(R.id.cardSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        findViewById<View>(R.id.cardDreamSettings).setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_DREAM_SETTINGS))
            } catch (_: Throwable) {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (!isTv()) refreshStatus()
    }

    private fun refreshStatus() {
        val url = Prefs.imageUrl(this)
        val badge = findViewById<TextView>(R.id.tvStatusBadge)
        val desc = findViewById<TextView>(R.id.tvStatusDesc)
        if (url.isBlank()) {
            badge.text = getString(R.string.card_status_empty)
            badge.setTextColor(getColor(R.color.accent_orange))
            desc.text = getString(R.string.card_status_empty)
        } else {
            badge.text = getString(R.string.card_status_configured)
            badge.setTextColor(getColor(R.color.accent_green))
            desc.text = url
        }
    }

    private fun isTv(): Boolean =
        packageManager.hasSystemFeature("android.software.leanback")
}
