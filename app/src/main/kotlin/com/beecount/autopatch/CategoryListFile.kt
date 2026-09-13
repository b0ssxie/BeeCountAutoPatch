package com.beecount.autopatch

import java.io.File

/**
 * 模块 App 侧维护「蜜蜂记账分类名单」。
 *
 * 名单文件由 hook 在自动记账进程里读取（见 [CategoryStore]），模块 App 负责读写。
 * 文件在自动记账的私有目录，直读/直写不通时退回 root。
 */
object CategoryListFile {

    /** 名单规模上限，防止手滑粘贴出个巨型文件。 */
    private const val MAX_NAME_LENGTH = 64
    private const val MAX_ENTRIES = 1000

    data class Result(val text: String, val source: String, val ok: Boolean)

    fun path(): String = CategoryStore.path(null)

    fun read(): Result {
        val p = path()
        directRead(p)?.let { return Result(it, "直接读取", true) }
        rootRead(p)?.let { return Result(it, "root 读取", true) }
        return Result("", "读不到：尚未设置，或需要 root", false)
    }

    /** 保存：先规范化（去空行、去重、限长），再直写，失败退 root。 */
    fun save(text: String): Boolean {
        val normalized = normalize(text)
        val p = path()
        val direct = runCatching {
            val f = File(p)
            if (f.exists() && f.canWrite()) {
                f.writeText(normalized)
                true
            } else {
                false
            }
        }.getOrDefault(false)
        if (direct) return true
        return RootShell.writeFile(p, normalized)
    }

    /**
     * 规范化名单文本：逐行去首尾空格、丢空行、忽略大小写去重、限制条数与单条长度。
     * 复用 [CategoryStore.parse]，保证 App 侧与 hook 侧的解析规则一致。
     */
    fun normalize(text: String): String {
        val out = ArrayList<String>()
        for (name in CategoryStore.parse(text)) {
            if (out.size >= MAX_ENTRIES) break
            out.add(name.take(MAX_NAME_LENGTH))
        }
        return out.joinToString("\n")
    }

    private fun directRead(path: String): String? = runCatching {
        File(path).takeIf { it.canRead() }?.readText()
    }.getOrNull()

    private fun rootRead(path: String): String? =
        RootShell.exec("cat ${shellQuote(path)}")?.takeIf { it.isNotEmpty() }

    private fun shellQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"
}