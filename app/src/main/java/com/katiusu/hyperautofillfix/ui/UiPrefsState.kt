package com.katiusu.hyperautofillfix.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.katiusu.hyperautofillfix.data.AppPrefs

/**
 * 界面设置的唯一持有者：内存里是 Compose state，改动立刻写回 SharedPreferences。
 * 由 `AppRoot` 用 `remember` 创建，向下传给各个页面（不重复创建）。
 */
class UiPrefsState(private val prefs: AppPrefs) {

    private var showAllowedState by mutableStateOf(prefs.showAllowed)
    private var rawViewState by mutableStateOf(prefs.rawView)
    private var themeModeState by mutableStateOf(prefs.themeMode)

    var showAllowed: Boolean
        get() = showAllowedState
        set(value) {
            showAllowedState = value
            prefs.showAllowed = value
        }

    /** 日志页的视图模式：true = 原始 logcat 行，false = 直观排版。 */
    var rawView: Boolean
        get() = rawViewState
        set(value) {
            rawViewState = value
            prefs.rawView = value
        }

    var themeMode: Int
        get() = themeModeState
        set(value) {
            themeModeState = value
            prefs.themeMode = value
        }
}
