package com.katiusu.hyperautofillfix

import android.content.Context
import com.katiusu.hyperautofillfix.prefs.PrefsStore
import top.yukonga.miuix.kmp.theme.ColorSchemeMode

/**
 * 外壳级设置：主题模式、底栏形态、背景模糊。
 *
 * 存储就是 [PrefsStore]（与 Hook 侧共享的 `settings` 文件），外壳的设置项由配置管道
 * 写入并即时重组；[load] 只服务于非 Compose 场景（如二级页 Activity），
 * 因此这里没有反向的写入口，避免出现绕过 ConfigState 的落盘路径。
 */
data class AppSettings(
    val themeMode: String = THEME_SYSTEM,
    val isFloatingNavbar: Boolean = true,
    val isLiquidGlass: Boolean = false,
    val isBlurEnabled: Boolean = true,
) {

    companion object {

        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_FLOATING_NAVBAR = "floating_navbar"
        const val KEY_LIQUID_GLASS = "liquid_glass"
        const val KEY_BLUR_ENABLED = "blur_enabled"

        /** 与 Miuix `ColorSchemeMode` 的枚举名逐字对应，直接 `valueOf` 使用。 */
        const val THEME_SYSTEM = "System"
        const val THEME_LIGHT = "Light"
        const val THEME_DARK = "Dark"
        const val THEME_MONET_SYSTEM = "MonetSystem"
        const val THEME_MONET_LIGHT = "MonetLight"
        const val THEME_MONET_DARK = "MonetDark"

        /** 2.x 的旧底栏键名。 */
        private const val LEGACY_FLOATING_NAVBAR_KEY = "floating_nav_bar"

        /** 主题名 → [ColorSchemeMode]，未知值（含旧版残留）一律回退跟随系统。 */
        fun themeModeOf(name: String?): ColorSchemeMode = try {
            ColorSchemeMode.valueOf(name ?: THEME_SYSTEM)
        } catch (_: Exception) {
            ColorSchemeMode.System
        }

        fun load(context: Context): AppSettings {
            PrefsStore.init(context)
            return AppSettings(
                themeMode = PrefsStore.getString(KEY_THEME_MODE, THEME_SYSTEM) ?: THEME_SYSTEM,
                isFloatingNavbar = PrefsStore.getBoolean(KEY_FLOATING_NAVBAR, true),
                isLiquidGlass = PrefsStore.getBoolean(KEY_LIQUID_GLASS, false),
                isBlurEnabled = PrefsStore.getBoolean(KEY_BLUR_ENABLED, true),
            )
        }

        /**
         * 2.x → 3.0 的键迁移，必须早于 [com.katiusu.hyperautofillfix.prefs.ConfigState.init]。
         *
         * 旧版主题存成 Int（0 跟随系统 / 1 浅色 / 2 深色 / 3 动态取色），新版改存枚举名字符串，
         * 不做迁移的话升级后主题会悄悄回落到跟随系统。
         */
        fun migrateLegacy(context: Context) {
            PrefsStore.init(context)
            val prefs = context.getSharedPreferences(PrefsStore.PREFS_NAME, Context.MODE_PRIVATE)
            val stored = prefs.all

            (stored[KEY_THEME_MODE] as? Number)?.let { legacy ->
                val mapped = when (legacy.toInt()) {
                    1 -> "Light"
                    2 -> "Dark"
                    3 -> "MonetSystem"
                    else -> THEME_SYSTEM
                }
                PrefsStore.put(KEY_THEME_MODE, mapped)
            }

            (stored[LEGACY_FLOATING_NAVBAR_KEY] as? Boolean)?.let { legacy ->
                if (!prefs.contains(KEY_FLOATING_NAVBAR)) {
                    PrefsStore.put(KEY_FLOATING_NAVBAR, legacy)
                }
                // 迁移后清掉旧键，免得导出的备份里同时出现新旧两套键。
                prefs.edit().remove(LEGACY_FLOATING_NAVBAR_KEY).apply()
            }
        }
    }
}
