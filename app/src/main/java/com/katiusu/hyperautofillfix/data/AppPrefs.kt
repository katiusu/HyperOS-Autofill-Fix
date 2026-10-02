package com.katiusu.hyperautofillfix.data

import android.content.Context
import androidx.core.content.edit

/** 界面设置，全部存在单个 SharedPreferences 里。 */
class AppPrefs private constructor(context: Context) {

    private val sp = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** 日志页是否显示「放行」事件。 */
    var showAllowed: Boolean
        get() = sp.getBoolean(KEY_SHOW_ALLOWED, true)
        set(value) = sp.edit { putBoolean(KEY_SHOW_ALLOWED, value) }

    /** 日志页默认视图：true = 直观，false = 原始。 */
    var rawView: Boolean
        get() = sp.getBoolean(KEY_RAW_VIEW, false)
        set(value) = sp.edit { putBoolean(KEY_RAW_VIEW, value) }

    /** 主题：0 跟随系统，1 亮色，2 暗色。 */
    var themeMode: Int
        get() = sp.getInt(KEY_THEME, 0)
        set(value) = sp.edit { putInt(KEY_THEME, value) }

    /** 底栏形态：true = 悬浮毛玻璃胶囊，false = 贴底普通底栏（2.0.0 的形态）。 */
    var floatingNavBar: Boolean
        get() = sp.getBoolean(KEY_FLOATING_NAV_BAR, true)
        set(value) = sp.edit { putBoolean(KEY_FLOATING_NAV_BAR, value) }

    companion object {
        private const val KEY_SHOW_ALLOWED = "show_allowed"
        private const val KEY_RAW_VIEW = "raw_view"
        private const val KEY_THEME = "theme_mode"
        private const val KEY_FLOATING_NAV_BAR = "floating_nav_bar"

        @Volatile
        private var instance: AppPrefs? = null

        fun get(context: Context): AppPrefs = instance ?: synchronized(this) {
            instance ?: AppPrefs(context.applicationContext).also { instance = it }
        }
    }
}
