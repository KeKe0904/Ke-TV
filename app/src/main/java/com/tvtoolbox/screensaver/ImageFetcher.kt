package com.tvtoolbox.screensaver

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * 图床 JSON 解析器。
 *
 * 支持以下 JSON 格式（自动识别）：
 *  1) ["url1", "url2", ...]                          字符串数组
 *  2) [{"url": "..."}, {"url": "..."}]               对象数组，含 url 字段
 *  3) {"images": [...], ...}                         对象，含 images 字段
 *  4) {"data": [...], ...}                           对象，含 data 字段（兼容常见图床 API）
 *
 * 其中数组元素如果是对象，会尝试 url / link / src / file / path / image_url 等字段。
 */
class ImageFetcher {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    /** 拉取并解析图片 URL 列表。失败抛异常。 */
    fun fetch(url: String): List<String> {
        require(url.isNotBlank()) { "URL 为空" }

        val request = Request.Builder()
            .url(url.trim())
            .header("User-Agent", "TVToolbox/1.0 (Android TV Screensaver)")
            .header("Accept", "application/json, */*")
            .build()

        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw RuntimeException("HTTP ${resp.code}")
            }
            val body = resp.body?.string()
                ?: throw RuntimeException("响应为空")
            return parse(body)
        }
    }

    fun parse(text: String): List<String> {
        val root = JsonParser.parseString(text)
        val arr = when {
            root.isJsonArray -> root.asJsonArray
            root.isJsonObject -> {
                val obj = root.asJsonObject
                val candidate = obj.get("images")
                    ?: obj.get("data")
                    ?: obj.get("list")
                    ?: obj.get("photos")
                    ?: obj.get("results")
                    ?: obj.get("items")
                    ?: throw RuntimeException("JSON 中未找到图片数组字段")
                if (!candidate.isJsonArray) {
                    throw RuntimeException("图片字段不是数组")
                }
                candidate.asJsonArray
            }
            else -> throw RuntimeException("JSON 既不是对象也不是数组")
        }

        val result = mutableListOf<String>()
        for (el: JsonElement in arr) {
            val u = extractUrl(el) ?: continue
            if (u.isNotBlank()) result.add(normalize(u))
        }
        return result
    }

    private fun extractUrl(el: JsonElement): String? {
        if (el.isJsonPrimitive) {
            return el.asString
        }
        if (el.isJsonObject) {
            val o = el.asJsonObject
            for (key in arrayOf(
                "url", "link", "src", "file", "path",
                "image_url", "img", "img_url", "raw", "download_url",
                "original", "full", "large", "medium", "preview"
            )) {
                val v = o.get(key) ?: continue
                if (v.isJsonPrimitive) return v.asString
            }
            // 兼容 nested: { url: { raw: "..." } }
            val nested = o.get("url")
            if (nested != null && nested.isJsonObject) {
                for (key in arrayOf("raw", "full", "regular", "small")) {
                    val v = nested.asJsonObject.get(key) ?: continue
                    if (v.isJsonPrimitive) return v.asString
                }
            }
        }
        return null
    }

    private fun normalize(u: String): String {
        val t = u.trim()
        // 处理 // 开头
        return if (t.startsWith("//")) "https:$t" else t
    }

    companion object {
        val gson: Gson = Gson()
    }
}
