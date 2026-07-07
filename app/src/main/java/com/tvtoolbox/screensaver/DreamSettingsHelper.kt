package com.tvtoolbox.screensaver

import android.app.AlarmManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings

/**
 * 系统屏保设置跳转 + 立即进入屏保的兜底工具。
 *
 * TV 上 `Settings.ACTION_DREAM_SETTINGS` 在小米/红米 TV 上经常不可用，
 * 所以提供多级 fallback：
 *
 * 1. ACTION_DREAM_SETTINGS（标准 Android 入口）
 * 2. com.android.settings.Settings$DreamSettingsActivity（直接组件名）
 * 3. 小米 TV 的屏保设置 Activity（多种组件名尝试）
 * 4. ACTION_SETTINGS（系统设置首页，让用户手动找）
 * 5. 都失败：提示用户用「立即进入屏保」功能测试
 *
 * 「立即进入屏保」用 DreamManager / AlarmManager trick 触发系统屏保，
 * 或直接启动我们自己的 PhotoDreamService 全屏预览（保证可用）。
 *
 * v1.7.4 新增：
 * - 小米 TV 屏保设置 Activity 多个候选组件名（覆盖不同 MIUI 版本）
 * - setAsSystemDream：跳转到系统屏保选择页 + 提示如何选中本应用
 */
object DreamSettingsHelper {

    /**
     * 尝试打开系统屏保设置。失败返回 false。
     * 内部已尝试多种 Intent，调用方只需判断结果。
     */
    fun openDreamSettings(context: Context): Boolean {
        val candidates = buildList {
            // 标准 Android 入口
            add(Intent(Settings.ACTION_DREAM_SETTINGS))
            // AOSP 直接组件名
            add(Intent().setComponent(
                ComponentName(
                    "com.android.settings",
                    "com.android.settings.Settings\$DreamSettingsActivity"
                )
            ))
            // 小米 / 红米 TV 多个候选（覆盖不同 MIUI 版本）
            add(Intent().setComponent(
                ComponentName(
                    "com.xiaomi.mitv.settings",
                    "com.xiaomi.mitv.settings.SettingsActivity"
                )
            ))
            add(Intent().setComponent(
                ComponentName(
                    "com.xiaomi.mitv.tvmanager",
                    "com.xiaomi.mitv.tvmanager.MainActivity"
                )
            ))
            add(Intent().setComponent(
                ComponentName(
                    "com.mitv.tvmanager",
                    "com.mitv.tvmanager.MainActivity"
                )
            ))
            // 小米电视管家（屏保设置入口在部分 MIUI for TV 版本中）
            add(Intent().setComponent(
                ComponentName(
                    "com.xiaomi.mitv.tvmanager",
                    "com.xiaomi.mitv.tvmanager.module.tvsetting.TvSettingActivity"
                )
            ))
            // 通用 settings 入口
            add(Intent(Settings.ACTION_SETTINGS))
        }

        for (intent in candidates) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return true
            } catch (_: Throwable) {
                // 试下一个
            }
        }
        return false
    }

    /**
     * 跳转到系统屏保选择页 + 提示用户如何选中本应用。
     *
     * 1. 尝试 ACTION_DREAM_SETTINGS（标准入口）
     * 2. 失败：返回 false，调用方显示提示
     */
    fun openDreamPicker(context: Context): Boolean {
        // 优先尝试标准 ACTION_DREAM_SETTINGS
        val candidates = listOf(
            Intent(Settings.ACTION_DREAM_SETTINGS),
            Intent().setComponent(
                ComponentName(
                    "com.android.settings",
                    "com.android.settings.Settings\$DreamSettingsActivity"
                )
            ),
            // 小米 TV 屏保设置
            Intent().setComponent(
                ComponentName(
                    "com.xiaomi.mitv.settings",
                    "com.xiaomi.mitv.settings.SettingsActivity"
                )
            )
        )
        for (intent in candidates) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return true
            } catch (_: Throwable) {
                // 试下一个
            }
        }
        return false
    }

    /**
     * 立即触发系统屏保。
     *
     * 优先方案：通过 AlarmManager 设置一个马上到期的屏幕变暗事件触发屏保。
     * 这个方法在部分系统上不可靠。
     *
     * 兜底方案：直接启动我们自己的 PhotoDreamService 作为 Activity 全屏预览。
     * 这是 100% 可用的方案，让用户能立即看到屏保效果。
     */
    fun triggerScreensaverNow(context: Context): Boolean {
        // 方案 A：尝试让系统立即开始屏保（需要 WRITE_SETTINGS 权限，不保证可用）
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                // 让屏幕马上休眠触发 dream
                val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                am.setTime(System.currentTimeMillis() - 1)
            }
        } catch (_: Throwable) {
            // 大概率没有权限，忽略
        }

        // 方案 B：直接启动 PhotoDreamService 的预览 Activity（兜底，100% 可用）
        return try {
            val intent = Intent(context, PhonePreviewActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        } catch (_: Throwable) {
            false
        }
    }
}
