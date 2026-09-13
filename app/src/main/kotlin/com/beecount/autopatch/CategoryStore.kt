package com.beecount.autopatch

/**
 * 蜜蜂记账分类名单的存取。
 *
 * 背景：补丁跑在自动记账进程里，拿不到蜜蜂记账的分类表，只能靠一份本地名单
 * 来判断“这个分类在蜜蜂记账里有没有”。名单文件放在自动记账的私有目录，
 * 与日志同目录，方便用 root / adb 维护：
 * `/data/data/net.ankio.auto/files/autopatch_categories.txt`（每行一个分类名）。
 *
 * 名单缺失或为空 = 未知，一律按“未知”处理（保持原分类发送，只有空分类才兜底），
 * 避免误把本来能记的分类也归为“其他”。
 */
object CategoryStore {

    const val FILE_NAME = "autopatch_categories.txt"

    /** 目标进程内的名单路径（与日志同目录）。 */
    fun path(filesDirPath: String?): String =
        if (filesDirPath.isNullOrEmpty()) "/data/data/${LogSpec.TARGET_PKG}/files/$FILE_NAME"
        else "$filesDirPath/$FILE_NAME"

    /**
     * 解析名单文本：按行切分，去首尾空格、去空行、去重（保留首次出现的写法，
     * 以便命中时回写蜜蜂记账里的原始大小写）。
     * 纯 JVM，可单测。
     */
    fun parse(text: String): LinkedHashSet<String> {
        val out = LinkedHashSet<String>()
        val seen = HashSet<String>()
        text.lines().forEach { line ->
            val name = line.trim().trim('\uFEFF')
            if (name.isEmpty()) return@forEach
            if (seen.add(name.lowercase())) out.add(name)
        }
        return out
    }
}
