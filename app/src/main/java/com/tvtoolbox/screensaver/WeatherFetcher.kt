package com.tvtoolbox.screensaver

import com.google.gson.JsonParser
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * 天气获取器。
 *
 * 数据源：wttr.in 免费 API，无需 API key。
 *
 * 定位策略（v1.6.4 优化）：
 * - 优先用 [lat]/[lon] 经纬度查询：`https://wttr.in/{lat},{lon}?format=j1`
 * - 未传入经纬度时回退到基于 IP 的自动定位：`https://wttr.in/?format=j1`
 *
 * 性能优化（v1.6.3）：
 * - 内存缓存：10 分钟内的天气数据复用，避免短时间内重复请求
 * - 切换 Activity 回主页时立即看到上次的天气
 *
 * 注意：定位权限申请、获取经纬度的耗时操作应在调用方（Activity）完成，
 * 这里只负责"拿到经纬度（或没有）就查询"。
 */
class WeatherFetcher {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    data class Weather(
        val city: String,
        val tempC: String,
        val desc: String,
        val humidity: String,
        val wind: String,
        val code: String
    )

    companion object {
        /** 内存缓存：10 分钟内的天气数据复用，避免短时间内重复请求。 */
        @Volatile private var cached: Weather? = null
        @Volatile private var cachedAt: Long = 0L
        /** 缓存对应的查询坐标。null 表示基于 IP 查询。 */
        @Volatile private var cachedKey: String? = null
        private const val CACHE_TTL_MS = 10L * 60 * 1000

        /** 仅供测试用：清掉缓存。 */
        fun clearCache() {
            cached = null
            cachedAt = 0L
            cachedKey = null
        }
    }

    /**
     * 拉取天气。失败抛异常。优先返回缓存。
     *
     * @param lat 纬度（可选）。传入时与 [lon] 一起用于精确查询；为 null 时走 IP 自动定位。
     * @param lon 经度（可选）。必须与 [lat] 同时传入或同时为 null。
     */
    fun fetch(lat: Double? = null, lon: Double? = null): Weather {
        val now = System.currentTimeMillis()
        // 缓存键：经纬度都给就用 "lat,lon"，否则用 IP（key=null）
        val key = if (lat != null && lon != null) "${lat},${lon}" else null

        // 缓存命中条件：缓存存在 + TTL 内 + 查询坐标相同
        val c = cached
        if (c != null && now - cachedAt < CACHE_TTL_MS && cachedKey == key) {
            return c
        }

        val url = if (lat != null && lon != null) {
            // 经纬度查询：精确到当地天气
            // 例：https://wttr.in/31.23,121.47?format=j1
            "https://wttr.in/${lat},${lon}?format=j1"
        } else {
            // IP 自动定位：wttr.in 根据请求来源 IP 推断城市
            "https://wttr.in/?format=j1"
        }

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Ke-TV/1.6 (Android)")
            .header("Accept-Language", "zh-CN,zh;q=0.9")
            .build()

        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw RuntimeException("HTTP ${resp.code}")
            }
            val body = resp.body?.string() ?: throw RuntimeException("响应为空")
            val result = parse(body)
            cached = result
            cachedAt = now
            cachedKey = key
            return result
        }
    }

    private fun parse(text: String): Weather {
        val root = JsonParser.parseString(text).asJsonObject
        val current = root.getAsJsonArray("current_condition")?.get(0)?.asJsonObject
            ?: throw RuntimeException("无 current_condition")

        val tempC = current.get("temp_C")?.asString ?: "?"
        val humidity = current.get("humidity")?.asString ?: "?"
        val wind = current.get("windspeedKmph")?.asString ?: "?"
        val code = current.get("weatherCode")?.asString ?: "0"
        val desc = current.getAsJsonArray("lang_zh")
            ?.get(0)?.asJsonObject?.get("value")?.asString
            ?: current.getAsJsonArray("weatherDesc")
                ?.get(0)?.asJsonObject?.get("value")?.asString
            ?: "未知"

        val area = root.getAsJsonArray("nearest_area")
            ?.get(0)?.asJsonObject
        val city = area?.get("areaName")?.asJsonArray?.get(0)?.asJsonObject?.get("value")?.asString
            ?: "未知城市"

        return Weather(
            city = city,
            tempC = tempC,
            desc = desc,
            humidity = humidity,
            wind = wind,
            code = code
        )
    }
}
