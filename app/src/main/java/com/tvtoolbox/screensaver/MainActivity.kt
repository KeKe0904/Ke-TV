package com.tvtoolbox.screensaver

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.card.MaterialCardView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)

        if (isTv()) {
            // TV: 直接进设置页（保留原有行为）
            startActivity(Intent(this, SettingsActivity::class.java))
            finish()
            return
        }

        // 手机: 显示卡片式主页
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.toolbar)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, bars.top, v.paddingRight, v.paddingBottom)
            insets
        }

        findViewById<MaterialCardView>(R.id.cardStatus).setOnClickListener {
            refreshStatus()
        }
        findViewById<MaterialCardView>(R.id.cardPreview).setOnClickListener {
            startActivity(Intent(this, PhonePreviewActivity::class.java))
        }
        findViewById<MaterialCardView>(R.id.cardSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        findViewById<MaterialCardView>(R.id.cardDreamSettings).setOnClickListener {
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
        val desc = if (url.isBlank()) {
            getString(R.string.card_status_empty)
        } else {
            getString(R.string.card_status_configured) + "\n" + url
        }
        findViewById<android.widget.TextView>(R.id.tvStatusDesc).text = desc
    }

    /** 判断是否是 TV 设备（leanback feature available）。 */
    private fun isTv(): Boolean {
        return packageManager.hasSystemFeature("android.software.leanback")
    }
}
