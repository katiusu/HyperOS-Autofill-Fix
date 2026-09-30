package com.katiusu.hyperautofillfix.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.remember
import com.katiusu.hyperautofillfix.data.AppPrefs
import com.katiusu.hyperautofillfix.data.LogStore

/** 只负责建立主题与状态持有者，界面结构全部在 [HafApp]。 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val prefs = AppPrefs.get(this)
        LogStore.init(this)

        setContent {
            val uiState = remember { UiPrefsState(prefs) }
            HafTheme(themeMode = uiState.themeMode) {
                HafApp(state = uiState)
            }
        }
    }
}
