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

    /** 图床类型：single = 单图 URL（随机图），json = JSON 列表。默认 single。 */
    fun sourceMode(context: Context): String =
        get(context).getString("source_mode", "single") ?: "single"

    fun setSourceMode(context: Context, value: String) =
        get(context).edit().putString("source_mode", value).apply()

    /** 主题模式：system 跟随系统，light 强制白天，dark 强制夜间。默认 system。 */
    fun themeMode(context: Context): String =
        get(context).getString("theme_mode", "system") ?: "system"

    fun setThemeMode(context: Context, value: String) =
        get(context).edit().putString("theme_mode", value).apply()

    // ===== 更新安装结果检测 =====
    // 用户点"下载并安装"后，启动系统安装器，但 APP 不知道用户最终有没有点"安装"。
    // 这里记录安装前的 versionCode，Activity onResume 时比对，识别"假安装成功"。

    fun lastInstallCheckVersionCode(context: Context): Long =
        get(context).getLong("last_install_check_vc", -1L)

    fun setLastInstallCheckVersionCode(context: Context, value: Long) =
        get(context).edit().putLong("last_install_check_vc", value).apply()

    fun lastInstallApkPath(context: Context): String =
        get(context).getString("last_install_apk_path", "") ?: ""

    fun setLastInstallApkPath(context: Context, value: String) =
        get(context).edit().putString("last_install_apk_path", value).apply()
}
