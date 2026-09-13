package com.beecount.autopatch

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 目标进程（自动记账）里的日志。
 *
 * 日志直接写进自动记账自己的私有目录，于是**只要 hook 跑得到就一定落盘**，
 * 不依赖模块 App 是否在运行。模块 App 侧由 [RemoteLogReader] 读取同一路径。
 *
 * 新版 libxposed API（LSPosed API 102）没有全局的 `XposedBridge.log`，
 * 所以框架日志出口由 [BeeCountHook] 在 `onModuleLoaded` 时注入到 [frameworkLog]。
 *
 * 注意：本类只在目标进程调用，模块自身进程不要调用。
 */
object RemoteLog {

    private const val MAX_BYTES = 256 * 1024
    private const val KEEP_BYTES = 192 * 1024

    /**
     * 框架日志出口：(消息, 异常或 null) -> Unit。
     * 由模块入口注入；模块 App 进程里一直是 null（那边不会写日志）。
     */
    @Volatile
    var frameworkLog: ((String, Throwable?) -> Unit)? = null

    /** 普通日志。 */
    fun log(context: Context?, line: String) {
        logToFramework(line, null)
        append(context, line)
    }

    /** 带异常的日志（框架侧按 ERROR 记）。 */
    fun log(context: Context?, line: String, tr: Throwable) {
        logToFramework(line, tr)
        append(context, line)
    }

    private fun logToFramework(line: String, tr: Throwable?) {
        try {
            frameworkLog?.invoke(line, tr)
        } catch (_: Throwable) {
            // 框架日志失败不能影响记账主流程
        }
    }

    private fun append(context: Context?, line: String) {
        synchronized(lock) {
            try {
                val f = file(context)
                f.parentFile?.let { dir ->
                    dir.mkdirs()
                    // 放宽目录/文件权限，让模块 App 能直接读到；失败也不影响写入。
                    dir.setExecutable(true, false)
                    dir.parentFile?.setExecutable(true, false)
                }
                f.appendText("${stamp.format(Date())}  $line\n")
                f.setReadable(true, false)
                f.setWritable(true, false)
                if (f.length() > MAX_BYTES) trim(f)
            } catch (_: Throwable) {
                // 日志失败不能影响记账主流程
            }
        }
    }

    /** 优先用目标进程自己的 filesDir（多用户/分区都可靠），取不到时退回包名推出来的路径。 */
    private fun file(context: Context?): File =
        File(context?.filesDir ?: File("/data/data/${LogSpec.TARGET_PKG}/files"), LogSpec.FILE_NAME)

    private fun trim(f: File) {
        val keep = f.readText().takeLast(KEEP_BYTES)
        f.writeText("…（日志过长，仅保留最近部分）\n$keep")
    }

    private val lock = Any()
    private val stamp = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)
}