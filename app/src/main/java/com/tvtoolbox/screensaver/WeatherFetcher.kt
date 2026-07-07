package com.tvtoolbox.screensaver

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * 天气获取器。
 *
 * 使用 wttr.in 免费 API，根据请求来源 IP 自动定位城市，无需 API key。
 * 接口：https://wttr.in/?format=j1 返回 JSON。
 *
 * 性能优化（v1.6.3）：
 * - 内存缓存：10 分钟内重复请求不重复打网络
 * - 缓存让用户切换 Activity 回主页时立即看到上次的天气，避免每次都重新加载
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
        private const val CACHE_TTL_MS = 10L * 60 * 1000

        /** 仅供测试用：清掉缓存。 */
        fun clearCache() {
            cached = null
            cachedAt = 0L
        }
    }

    /** 拉取天气。失败抛异常。优先返回缓存。 */
    fun fetch(): Weather {
        val now = System.currentTimeMillis()
        val c = cached
        if (c != null && now - cachedAt < CACHE_TTL_MS) {
            return c
        }

        val request = Request.Builder()
            .url("https://wttr.in/?format=j1")
            .header("User-Agent", "Ke-TV/1.5 (Android)")
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

