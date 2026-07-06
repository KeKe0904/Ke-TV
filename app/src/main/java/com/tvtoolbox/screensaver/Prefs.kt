package com.tvtoolbox.screensaver

import android.content.Context
import android.content.SharedPreferences

object Prefs {
    private const val FILE = "tvtoolbox_prefs"

    fun get(context: Context): SharedPreferences =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun imageUrl(context: Context): String =
        get(context).getString("image_url", "") ?: ""

    fun intervalSeconds(context: Context): Int =
        get(context).getString("interval_seconds", "15")?.toIntOrNull() ?: 15

    fun randomOrder(context: Context): Boolean =
        get(context).getBoolean("random_order", true)

    fun kenBurns(context: Context): Boolean =
        get(context).getBoolean("ken_burns", true)
}
