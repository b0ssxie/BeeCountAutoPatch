package com.beecount.autopatch

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** 接收目标进程经 [RemoteLog] 发来的日志行并落地。 */
class LogReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != RemoteLog.ACTION) return
        if (intent.getStringExtra(RemoteLog.EXTRA_TOKEN) != RemoteLog.TOKEN) return
        val line = intent.getStringExtra(RemoteLog.EXTRA_LINE) ?: return
        LogStore.append(context.applicationContext, line)
    }
}
