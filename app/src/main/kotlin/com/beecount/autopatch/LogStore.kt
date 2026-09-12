package com.beecount.autopatch

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 模块自身的日志文件（落在本模块 filesDir，只有模块进程读写）。 */
object LogStore {

    private const val FILE_NAME = "autopatch.log"
    private const val MAX_BYTES = 256 * 1024
    private const val KEEP_BYTES = 192 * 1024

    private val lock = Any()
    private val stamp = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)

    fun path(context: Context): String = file(context).absolutePath

    fun append(context: Context, line: String) {
        synchronized(lock) {
            try {
                val f = file(context)
                f.appendText("${stamp.format(Date())}  $line\n")
                if (f.length() > MAX_BYTES) trim(f)
            } catch (_: Throwable) {
                // 日志失败不能影响其它流程
            }
        }
    }

    fun read(context: Context): String = synchronized(lock) {
        try {
            file(context).takeIf { it.exists() }?.readText().orEmpty()
        } catch (_: Throwable) {
            ""
        }
    }

    fun clear(context: Context) {
        synchronized(lock) {
            runCatching { file(context).writeText("") }
        }
    }

    private fun file(context: Context) = File(context.filesDir, FILE_NAME)

    private fun trim(f: File) {
        val keep = f.readText().takeLast(KEEP_BYTES)
        f.writeText("…（日志过长，仅保留最近部分）\n$keep")
    }
}
