package com.beecount.autopatch

import java.io.File

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
        return RootShell.exec("rm -f ${shellQuote(path)}") != null
    }

    private fun directRead(path: String): String? = runCatching {
        File(path).takeIf { it.canRead() }?.readText()
    }.getOrNull()

    private fun rootRead(path: String): String? =
        RootShell.exec("cat ${shellQuote(path)}")?.takeIf { it.isNotEmpty() }

    private fun shellQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"
}