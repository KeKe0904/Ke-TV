package com.tvtoolbox.screensaver

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.apply(this)
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContentView(R.layout.activity_main)

        // 处理状态栏 insets，给 toolbar 加顶部 padding
        val toolbar = findViewById<View>(R.id.toolbar)
        ViewCompat.setOnApplyWindowInsetsListener(toolbar) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, bars.top, v.paddingRight, v.paddingBottom)
            insets
        }

        // ===== 卡片点击 =====
        // 天气卡片：点击刷新天气
        findViewById<View>(R.id.cardWeather).setOnClickListener { loadWeather() }

        // 1. 屏保设置
        findViewById<View>(R.id.cardSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        // 2. 立即进入屏保
        findViewById<View>(R.id.cardStartScreensaver).setOnClickListener {
            val ok = DreamSettingsHelper.triggerScreensaverNow(this)
            if (!ok) {
                Toast.makeText(this, "无法启动屏保", Toast.LENGTH_SHORT).show()
            }
        }

        // 3. 系统屏保设置（带 fallback）
        findViewById<View>(R.id.cardDreamSettings).setOnClickListener { openDreamSettings() }

        // 4. 浏览器（占位，敬请期待）
        findViewById<View>(R.id.cardBrowser).setOnClickListener {
            Toast.makeText(
                this,
                getString(R.string.feature_coming_soon, getString(R.string.card_browser_title)),
                Toast.LENGTH_SHORT
            ).show()
        }

        // 5. 软件设置（主题 + 软件更新）
        findViewById<View>(R.id.cardUpdate).setOnClickListener {
            startActivity(Intent(this, AppSettingsActivity::class.java))
        }

        // 6. 关于：打开 GitHub 项目主页
        findViewById<View>(R.id.cardAbout).setOnClickListener {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.about_github_url))))
            } catch (_: Throwable) {
                Toast.makeText(this, "无法打开浏览器", Toast.LENGTH_SHORT).show()
            }
        }

        // ===== TV 焦点导航 =====
        val cardWeather = findViewById<View>(R.id.cardWeather)
        val cardSettings = findViewById<View>(R.id.cardSettings)
        val cardStart = findViewById<View>(R.id.cardStartScreensaver)
        val cardDream = findViewById<View>(R.id.cardDreamSettings)
        val cardBrowser = findViewById<View>(R.id.cardBrowser)
        val cardUpdate = findViewById<View>(R.id.cardUpdate)
        val cardAbout = findViewById<View>(R.id.cardAbout)
        FocusHelper.setupFocusAll(
            cardWeather, cardSettings, cardStart, cardDream,
            cardBrowser, cardUpdate, cardAbout
        )
        FocusHelper.requestInitialFocus(cardSettings)

        // 首次加载天气
        loadWeather()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    /** 加载天气数据（异步）。 */
    private fun loadWeather() {
        val tvCity = findViewById<TextView>(R.id.tvCity)
        val tvCity2 = findViewById<TextView>(R.id.tvCity2)
        val tvTemp = findViewById<TextView>(R.id.tvTemp)
        val tvDesc = findViewById<TextView>(R.id.tvWeatherDesc)

        tvDesc.text = getString(R.string.card_weather_loading)

        scope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    WeatherFetcher().fetch()
                } catch (t: Throwable) {
                    null
                }
            }

            if (result == null) {
                tvCity.text = "—"
                tvCity2.text = "—"
                tvTemp.text = "--°"
                tvDesc.text = getString(R.string.card_weather_error)
                return@launch
            }

            tvCity.text = result.city
            tvCity2.text = result.city
            tvTemp.text = "${result.tempC}°"
            tvDesc.text = getString(
                R.string.card_weather_format,
                result.desc,
                result.humidity,
                result.wind
            )
        }
    }

    /** 打开系统屏保设置，TV 上多级 fallback。 */
    private fun openDreamSettings() {
        val ok = DreamSettingsHelper.openDreamSettings(this)
        if (!ok) {
            // 所有 Intent 都失败，提示用户用「立即进入屏保」
            AlertDialog.Builder(this)
                .setTitle(R.string.card_dream_settings_title)
                .setMessage(R.string.dream_settings_fallback)
                .setPositiveButton(R.string.card_start_screensaver_title) { _, _ ->
                    DreamSettingsHelper.triggerScreensaverNow(this)
                }
                .setNegativeButton(R.string.dialog_cancel, null)
                .show()
        }
    }
}
