package com.tvtoolbox.screensaver

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
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

    /**
     * 定位权限请求：先尝试获取精准定位，失败时（系统降级）尝试粗略定位。
     * 申请结果回调里：授予权限 → 加载天气（用定位）；拒绝 → 加载天气（用 IP）。
     */
    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result.values.any { it }
        if (granted) {
            loadWeather()
        } else {
            // 用户拒绝定位：仍然加载天气（走 IP 自动定位），并提示一次
            Toast.makeText(
                this,
                getString(R.string.browser_location_denied),
                Toast.LENGTH_LONG
            ).show()
            loadWeather()
        }
    }

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
        // 天气卡片：点击刷新天气（会重新触发权限检查）
        findViewById<View>(R.id.cardWeather).setOnClickListener { loadWeatherWithPermission() }

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

        // 4. 浏览器：启动 BrowserActivity（基于 Chrome 内核的系统 WebView）
        findViewById<View>(R.id.cardBrowser).setOnClickListener {
            startActivity(Intent(this, BrowserActivity::class.java))
        }

        // 5. 软件设置（主题 + 软件更新）
        findViewById<View>(R.id.cardUpdate).setOnClickListener {
            startActivity(Intent(this, AppSettingsActivity::class.java))
        }

        // 6. 关于：打开本地 about.html（瞬间加载，避免直接打开 GitHub 白屏）
        // 用户在 about.html 里点击 GitHub 链接，由 BrowserActivity 内 WebView 加载
        findViewById<View>(R.id.cardAbout).setOnClickListener {
            val intent = Intent(this, BrowserActivity::class.java).apply {
                putExtra(BrowserActivity.EXTRA_URL, BrowserActivity.ABOUT_PAGE_URL)
            }
            try {
                startActivity(intent)
            } catch (_: Throwable) {
                Toast.makeText(this, "无法打开关于页面", Toast.LENGTH_SHORT).show()
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

        // 首次加载天气：推迟到 ViewCreated 后，避免阻塞 onCreate
        findViewById<View>(R.id.cardWeather).post {
            loadWeatherWithPermission()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    /**
     * 检查定位权限：已授予 → 直接加载天气；未授予 → 申请权限后加载。
     *
     * 天气获取流程：
     * - 默认尝试用 GPS 最后位置 → 走 wttr.in 经纬度查询
     * - 拿不到定位（无权限 / 系统未提供最后位置）→ 走 wttr.in IP 自动定位
     */
    private fun loadWeatherWithPermission() {
        if (hasLocationPermission()) {
            loadWeather()
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    /** 是否已获得定位权限（FINE 或 COARSE 任一）。 */
    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /** 加载天气数据（异步）。优先用最后已知位置；无定位则走 IP。 */
    private fun loadWeather() {
        val tvCity = findViewById<TextView>(R.id.tvCity)
        val tvCity2 = findViewById<TextView>(R.id.tvCity2)
        val tvTemp = findViewById<TextView>(R.id.tvTemp)
        val tvDesc = findViewById<TextView>(R.id.tvWeatherDesc)

        tvDesc.text = getString(R.string.card_weather_loading)

        scope.launch {
            // 先在 IO 线程拿到最后位置（如果有权限且系统有数据）
            val location = withContext(Dispatchers.IO) {
                tryGetLastKnownLocation()
            }

            val result = withContext(Dispatchers.IO) {
                try {
                    if (location != null) {
                        WeatherFetcher().fetch(location.latitude, location.longitude)
                    } else {
                        // 拿不到定位：走 IP 自动定位
                        WeatherFetcher().fetch()
                    }
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

    /**
     * 同步获取最后已知位置（必须在 IO 线程调用）。
     *
     * 优先级：
     * 1. GPS_PROVIDER（最精确，但可能为 null）
     * 2. NETWORK_PROVIDER（基站/Wi-Fi 定位，无 GPS 时仍可用）
     * 3. PASSIVE_PROVIDER（被动接收其他 APP 的定位更新）
     *
     * 返回 null 的情况：无权限 / 系统从未拿到过位置 / 设备无定位硬件。
     */
    private fun tryGetLastKnownLocation(): Location? {
        if (!hasLocationPermission()) return null
        return try {
            val lm = getSystemService(LOCATION_SERVICE) as? LocationManager ?: return null
            // 优先 GPS，其次 NETWORK，最后 PASSIVE
            val candidates = listOf(
                LocationManager.GPS_PROVIDER,
                LocationManager.NETWORK_PROVIDER,
                LocationManager.PASSIVE_PROVIDER
            )
            for (provider in candidates) {
                // getLastKnownLocation 在 API 30+ 上需要权限检查
                @Suppress("MissingPermission")
                val loc = lm.getLastKnownLocation(provider)
                if (loc != null) return loc
            }
            null
        } catch (_: SecurityException) {
            null
        } catch (_: Throwable) {
            null
        }
    }

    /** 打开系统屏保设置，TV 上多级 fallback。 */
    private fun openDreamSettings() {
        val ok = DreamSettingsHelper.openDreamSettings(this)
        if (!ok) {
            // 所有 Intent 都失败，提示用户用「立即进入屏保」
            showAppMessage(
                title = getString(R.string.card_dream_settings_title),
                message = getString(R.string.dream_settings_fallback),
                positiveText = getString(R.string.card_start_screensaver_title),
                onPositive = { DreamSettingsHelper.triggerScreensaverNow(this) },
                negativeText = getString(R.string.dialog_cancel)
            )
        }
    }
}
