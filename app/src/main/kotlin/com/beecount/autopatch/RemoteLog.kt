package com.beecount.autopatch

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import de.robv.android.xposed.XposedBridge

/**
 * 在**目标进程（自动记账）**里记录日志。
 *
 * 目标进程与模块进程不是同一 UID，写不了模块的私有目录，所以这里把日志行通过
 * 显式广播发回模块进程的 [LogReceiver]，由它落地到 [LogStore]。
 * 同时镜像一份到 LSPosed 日志，便于在 Xposed 日志里直接查看。
 *
 * 注意：本类只在目标进程调用，模块自身进程不要调用（那里没有 XposedBridge）。
 */
object RemoteLog {

    const val ACTION = "com.beecount.autopatch.LOG"
    const val EXTRA_TOKEN = "token"
    const val EXTRA_LINE = "line"

    /** 用于挡掉无关/伪造广播，两端共享。 */
    const val TOKEN = "bcap-7f3a9c1e5d"

    private const val MODULE_PKG = "com.beecount.autopatch"

    fun log(context: Context?, line: String) {
        XposedBridge.log("[BeeCountAutoPatch] $line")
        val ctx = context?.applicationContext ?: return
        try {
            ctx.sendBroadcast(
                Intent(ACTION).apply {
                    setComponent(ComponentName(MODULE_PKG, "$MODULE_PKG.LogReceiver"))
                    putExtra(EXTRA_TOKEN, TOKEN)
                    putExtra(EXTRA_LINE, line)
                    // 模块 App 平时没有进程，系统会把它视为 stopped；不带这两个 flag 时
                    // AMS 会直接丢弃广播（Logcat 里报 "Failed to broadcast to stopped app"）。
                    addFlags(
                        Intent.FLAG_INCLUDE_STOPPED_PACKAGES or Intent.FLAG_RECEIVER_FOREGROUND,
                    )
                },
            )
        } catch (_: Throwable) {
            // 广播失败不影响记账主流程
        }
    }
}
