package com.katiusu.hyperautofillfix

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.katiusu.hyperautofillfix.data.LogStore

/**
 * 接收模块从被 Hook 进程（system_server / 设置存储 / 系统管家）广播过来的日志事件。
 * 广播是显式指定包名的，Android 8.0+ 的隐式广播限制不影响它。
 */
class LogReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ModuleLog.ACTION_LOG) return
        val json = intent.getStringExtra(ModuleLog.EXTRA_EVENT) ?: return
        LogStore.init(context.applicationContext)
        LogStore.append(json)
    }
}
