package com.beecount.autopatch

import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 模块 App 侧读取 [LogSpec.path] 指向的日志。
 *
 * Android 11+ 默认禁止跨应用访问私有目录，所以依次尝试：
 * 1. 直接读文件——hook 写入时放宽了目录/文件权限，多数机型够用，且不需要 root；
 * 2. root 读取（`su -c cat`）——直读不行时的兜底。
 */
object RemoteLogReader {

    /** [source] 说明日志来源或失败原因。 */
    data class Result(val text: String, val source: String, val ok: Boolean)

    private const val TIMEOUT_SECONDS = 20L

    private val SU_BINARIES = listOf("su", "/system/bin/su", "/system/xbin/su")

    fun read(): Result {
        val path = LogSpec.path()
        directRead(path)?.let { return Result(it, "直接读取", true) }
        rootRead(path)?.let { return Result(it, "root 读取", true) }
        return Result("", "读不到：需要 root 权限，或日志尚未生成", false)
    }

    /** 清空日志；直写不行就交给 root。 */
    fun clear(): Boolean {
        val path = LogSpec.path()
        runCatching {
            val f = File(path)
            if (f.canWrite()) {
                f.writeText("")
                return true
            }
        }
        return rootExec("rm -f $path") != null
    }

    private fun directRead(path: String): String? = runCatching {
        File(path).takeIf { it.canRead() }?.readText()
    }.getOrNull()

    private fun rootRead(path: String): String? =
        rootExec("cat $path")?.takeIf { it.isNotEmpty() }

    /** 依次尝试各个 su 路径执行 [command]；未授权/不可用时返回 null。 */
    private fun rootExec(command: String): String? {
        for (bin in SU_BINARIES) {
            val out = runCatching {
                val process = ProcessBuilder(bin, "-c", command).start()
                val text = process.inputStream.bufferedReader().use { it.readText() }
                val finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                process.destroy()
                if (finished && process.exitValue() == 0) text else null
            }.getOrNull()
            if (out != null) return out
        }
        return null
    }
}
