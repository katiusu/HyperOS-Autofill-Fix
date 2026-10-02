package com.katiusu.hyperautofillfix.ui

import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.LocalActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
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

    // edge-to-edge：系统栏图标必须跟随**应用内**的明暗选择，而不是系统设置。
    // ComponentActivity.enableEdgeToEdge() 默认用 SystemBarStyle.auto()，它只看系统 dark mode；
    // 而本应用允许在设置里强制浅色 / 深色，两者不一致时就会出现「浅底 + 白图标」看不见的情况。
    // 这里按当前主题重新下发显式样式：light 样式配深色图标，dark 样式配浅色图标。
    // 另一处收益：androidx 只在 auto 样式时打开 isNavigationBarContrastEnforced，
    // 用显式样式会把系统给导航栏叠的那层半透明底关掉 —— 悬浮底栏的玻璃才能一路铺到屏幕底部。
    val darkTheme = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        // 跟随系统 / 动态取色：明暗跟系统走
        else -> isSystemInDarkTheme()
    }
    val activity = LocalActivity.current as? ComponentActivity
    if (activity != null) {
        SideEffect {
            val style = if (darkTheme) {
                SystemBarStyle.dark(Color.TRANSPARENT)
            } else {
                SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
            }
            activity.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
        }
    }

    MiuixTheme(controller = controller, content = content)
}
