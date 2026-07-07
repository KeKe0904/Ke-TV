package com.tvtoolbox.screensaver

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings

/**
 * 系统级屏保强制设置工具（v1.7.5 新增）。
 *
 * 核心思路：
 * Android 系统屏保通过 Settings.Secure 存储配置：
 * - screensaver_enabled：1 启用，0 关闭
 * - screensaver_components：屏保组件名（ComponentName.flattenToString()）
 * - screensaver_timeout：屏保触发等待时间（毫秒）
 *
 * 普通 APP 没有写这些值的权限（需要 WRITE_SECURE_SETTINGS），
 * 但通过 adb 主动授予权限后，APP 可以直接修改系统屏保配置：
 *
 *   adb shell pm grant com.tvtoolbox.screensaver android.permission.WRITE_SECURE_SETTINGS
 *
 * 这是**无需 root 的最强方案**，适用于：
 * - 小米 TV / 红米 TV（MIUI for TV）
 * - 海信 / 创维 / TCL 等所有 Android TV
 * - 安卓手机 / 平板
 *
 * 用户只需：
 * 1. 在 TV 设置里启用「开发者选项」+「USB 调试」
 * 2. 用电脑 adb 连接 TV（同一 WiFi 下可用 adb connect）
 * 3. 执行 grant 命令授予权限
 * 4. 回到 APP 点击「一键设为系统屏保」
 *
 * 优势：
 * - 永久生效（即使重启 TV）
 * - 不需要每次手动启动
 * - 完全替代系统默认屏保
 */
object SystemDreamHelper {

    private const val APP_PACKAGE = "com.tvtoolbox.screensaver"
    private const val DREAM_COMPONENT = "$APP_PACKAGE/$APP_PACKAGE.PhotoDreamService"

    /**
     * 检查是否已获得 WRITE_SECURE_SETTINGS 权限。
     *
     * 此权限为系统级权限，普通安装不授予，必须用户通过 adb 主动授予：
     *   adb shell pm grant com.tvtoolbox.screensaver android.permission.WRITE_SECURE_SETTINGS
     */
    fun hasSecureSettingsPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            context.checkCallingOrSelfPermission("android.permission.WRITE_SECURE_SETTINGS") ==
                PackageManager.PERMISSION_GRANTED
        } else {
            false
        }
    }

    /**
     * 一键把本应用设为系统屏保（强制覆盖）。
     *
     * 调用前提：[hasSecureSettingsPermission] 返回 true
     *
     * 设置项：
     * - screensaver_enabled = 1
     * - screensaver_components = 本应用 PhotoDreamService
     * - screensaver_timeout = 用户设置的等待时间（默认 5 分钟）
     *
     * @param timeoutMillis 屏保触发等待时间，默认 5 分钟
     * @return true 设置成功
     */
    fun setAsSystemDream(context: Context, timeoutMillis: Int = 5 * 60 * 1000): Boolean {
        if (!hasSecureSettingsPermission(context)) return false

        return try {
            val resolver = context.contentResolver

            // 1. 启用屏保
            Settings.Secure.putInt(resolver, "screensaver_enabled", 1)

            // 2. 设置本应用为屏保组件
            //    格式：包名/完整组件名（与 ComponentName.flattenToString 一致）
            Settings.Secure.putString(resolver, "screensaver_components", DREAM_COMPONENT)

            // 3. 设置屏保触发等待时间（毫秒）
            Settings.Secure.putInt(resolver, "screensaver_timeout", timeoutMillis)

            // 4. 启用插电源时触发屏保（部分设备默认关闭）
            Settings.Secure.putInt(resolver, "screensaver_activate_on_sleep", 1)

            true
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * 检查系统当前是否已设置本应用为屏保。
     */
    fun isCurrentSystemDream(context: Context): Boolean {
        return try {
            val components = Settings.Secure.getString(
                context.contentResolver, "screensaver_components"
            ) ?: ""
            components.contains(DREAM_COMPONENT)
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * 获取系统当前屏保组件名（用于诊断）。
     */
    fun getCurrentSystemDream(context: Context): String {
        return try {
            Settings.Secure.getString(
                context.contentResolver, "screensaver_components"
            ) ?: "(未设置)"
        } catch (_: Throwable) {
            "(读取失败)"
        }
    }

    /**
     * 获取系统屏保等待时间（毫秒）。
     */
    fun getScreenSaverTimeout(context: Context): Int {
        return try {
            Settings.Secure.getInt(context.contentResolver, "screensaver_timeout", -1)
        } catch (_: Throwable) {
            -1
        }
    }

    /**
     * 获取系统屏保是否启用。
     */
    fun isScreenSaverEnabled(context: Context): Boolean {
        return try {
            Settings.Secure.getInt(context.contentResolver, "screensaver_enabled", 0) == 1
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * 生成给用户的 adb 授权命令（用户复制到电脑执行）。
     */
    fun getAdbGrantCommand(): String {
        return "adb shell pm grant $APP_PACKAGE android.permission.WRITE_SECURE_SETTINGS"
    }

    /**
     * 生成给用户的 adb 撤销权限命令。
     */
    fun getAdbRevokeCommand(): String {
        return "adb shell pm revoke $APP_PACKAGE android.permission.WRITE_SECURE_SETTINGS"
    }
}
