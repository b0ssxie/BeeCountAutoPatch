package com.beecount.autopatch

import java.util.concurrent.TimeUnit

/**
 * 模块 App 侧的 root 命令执行封装。
 *
 * 目标应用（自动记账）的私有目录只有它自己或 root 能碰，模块 App 想读日志、
 * 写分类名单都得借 root。这里把 `su` 的探测与超时集中处理，供
 * [RemoteLogReader] 和 [CategoryListFile] 共用。
 */
object RootShell {

    /** 单条命令超时；设备卡住时不能把界面线程拖死。 */
    private const val TIMEOUT_SECONDS = 20L

    private val SU_BINARIES = listOf("su", "/system/bin/su", "/system/xbin/su")

    /** 依次尝试各个 su 路径执行 [command]；未授权/不可用时返回 null。 */
    fun exec(command: String): String? {
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

    /**
     * 以 root 写入 [content] 到 [path]。
     *
     * 内容通过 stdin 传给 `cat > path`，不拼进命令行，避免分类名里的引号、空格、
     * 换行把 shell 命令拼坏（也避免注入）。
     */
    fun writeFile(path: String, content: String): Boolean {
        for (bin in SU_BINARIES) {
            val ok = runCatching {
                val process = ProcessBuilder(bin, "-c", "cat > ${shellQuote(path)}").start()
                process.outputStream.use { it.write(content.toByteArray(Charsets.UTF_8)) }
                val finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                val code = if (finished) process.exitValue() else -1
                process.destroy()
                finished && code == 0
            }.getOrDefault(false)
            if (ok) return true
        }
        return false
    }

    /** 只用于路径这种由模块自己拼出来的字符串；分类名等内容一律走 stdin。 */
    private fun shellQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"
}