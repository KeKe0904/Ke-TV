package com.tvtoolbox.screensaver

import android.content.Context
import android.content.SharedPreferences

object Prefs {
    private const val FILE = "tvtoolbox_prefs"

    fun get(context: Context): SharedPreferences =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun imageUrl(context: Context): String =
        get(context).getString("image_url", "") ?: ""

    fun setImageUrl(context: Context, value: String) =
        get(context).edit().putString("image_url", value).apply()

    fun intervalSeconds(context: Context): Int =
        get(context).getString("interval_seconds", "15")?.toIntOrNull() ?: 15

    fun setIntervalSeconds(context: Context, value: Int) =
        get(context).edit().putString("interval_seconds", value.toString()).apply()

    fun randomOrder(context: Context): Boolean =
        get(context).getBoolean("random_order", true)

    fun setRandomOrder(context: Context, value: Boolean) =
        get(context).edit().putBoolean("random_order", value).apply()

    fun kenBurns(context: Context): Boolean =
        get(context).getBoolean("ken_burns", true)

    fun setKenBurns(context: Context, value: Boolean) =
        get(context).edit().putBoolean("ken_burns", value).apply()
}
