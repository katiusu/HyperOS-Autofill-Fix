package com.katiusu.hyperautofillfix.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

/** 主题模式常量，与 [com.katiusu.hyperautofillfix.data.AppPrefs.themeMode] 对应。 */
object ThemeMode {
    const val SYSTEM = 0
    const val LIGHT = 1
    const val DARK = 2

    /** 取系统壁纸主色（Android 12+ 的动态取色 / Monet）。 */
    const val MONET = 3
}

/** 主题模式的可读名称。 */
fun themeModeLabel(mode: Int): String = when (mode) {
    ThemeMode.LIGHT -> "浅色"
    ThemeMode.DARK -> "深色"
    ThemeMode.MONET -> "动态取色"
    else -> "跟随系统"
}

@Composable
fun HafTheme(themeMode: Int, content: @Composable () -> Unit) {
    val controller = remember(themeMode) {
        ThemeController(
            colorSchemeMode = when (themeMode) {
                ThemeMode.LIGHT -> ColorSchemeMode.Light
                ThemeMode.DARK -> ColorSchemeMode.Dark
                ThemeMode.MONET -> ColorSchemeMode.MonetSystem
                else -> ColorSchemeMode.System
            },
        )
    }
    MiuixTheme(controller = controller, content = content)
}
