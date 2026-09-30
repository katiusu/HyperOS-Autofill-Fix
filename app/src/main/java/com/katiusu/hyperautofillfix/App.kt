package com.katiusu.hyperautofillfix

import android.app.Application
import com.katiusu.hyperautofillfix.data.LogStore

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        LogStore.init(this)
    }
}
