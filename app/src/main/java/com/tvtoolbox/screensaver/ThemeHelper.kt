package com.tvtoolbox.screensaver

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

/**
 * 主题模式工具：把 Prefs.themeMode 映射到 AppCompatDelegate.setDefaultNightMode。
 *
 * 必须在 Activity 的 super.onCreate 之前调用，否则当前 Activity 不会按新主题重建。
 * AppCompatDelegate.setDefaultNightMode 是全局的，配置一次后所有后续 Activity 都会跟随。
 */
object ThemeHelper {

    fun apply(context: Context) {
        val mode = when (Prefs.themeMode(context)) {
            "light" -> AppCompatDelegate.MODE_NIGHT_NO
            "dark" -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(mode)
    }
}
