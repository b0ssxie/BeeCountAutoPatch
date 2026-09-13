package com.beecount.autopatch

import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 把一笔账单映射为蜜蜂记账(BeeCount)的深链参数。
 *
 * 协议参考 BeeCount `lib/services/platform/app_link_service.dart` 的
 * `_handleAddTransaction` 与 `AppLinkBuilder.add`：
 * `beecount://add?amount=..&type=..&category=..&note=..&account=..&to_account=..&tags=..&date=..&silent=1`
 *
 * 纯 JVM 实现（不依赖 Android），便于单元测试。
 */
object BillMapper {

    data class Bill(
        val amount: Double,
        /** expense | income | transfer */
        val type: String,
        /** 转账无分类，传 null */
        val category: String?,
        val note: String?,
        val account: String?,
        /** 仅转账使用 */
        val toAccount: String?,
        val tags: List<String>,
        val timeMillis: Long,
        val silent: Boolean = true,
    )

    /**
     * 分类在蜜蜂记账里不存在时的兜底分类名。
     *
     * 注意：这个分类本身也必须存在于蜜蜂记账（默认要求用户建一个名为「其他」的分类），
     * 否则深链仍会被对方拒绝。
     */
    const val FALLBACK_CATEGORY = "其他"

    /** 分类解析结果：最终发送的分类 + 是否走了兜底 + 原始分类（用于备注留痕/日志）。 */
    data class CategoryResolution(
        val category: String,
        val fallbackUsed: Boolean,
        val original: String?,
    )

    /** BillType 枚举名 → BeeCount 的 type；报销/借贷/还款/退款等扩展类型按收支大类回退。 */
    fun mapType(billTypeName: String): String = when {
        billTypeName == "Transfer" -> "transfer"
        billTypeName.startsWith("Income") -> "income"
        else -> "expense"
    }

    /**
     * AutoAccounting 的 cateName 以 '-' 分隔父/子分类（见 BillInfoModel.categoryPair），
     * 例如 `餐饮-早餐`。
     *
     * BeeCount 只按单个分类名匹配，所以这里给出备选顺序：**子类优先，父类兜底**，
     * 去重并丢掉空值。`餐饮-早餐` → `[早餐, 餐饮]`；`餐饮` → `[餐饮]`；`餐饮-` → `[餐饮]`。
     */
    fun categoryCandidates(cateName: String): List<String> {
        val name = cateName.trim()
        if (name.isEmpty()) return emptyList()
        val i = name.indexOf('-')
        if (i <= 0) return listOf(name)
        val parent = name.substring(0, i).trim()
        val child = if (i < name.lastIndex) name.substring(i + 1).trim() else ""
        return listOf(child, parent).filter { it.isNotEmpty() }.distinct()
    }

    /** 取首选分类名（子类优先）；空分类返回 null。 */
    fun categoryOf(cateName: String): String? = categoryCandidates(cateName).firstOrNull()

    /**
     * 解析最终要发送给蜜蜂记账的分类，实现「找不到就归为其他」。
     *
     * @param cateName 自动记账的原始分类名，可能含 '-' 父子结构。
     * @param knownCategories 蜜蜂记账里已存在的分类名集合（匹配时忽略大小写和首尾空格，
     *   命中后返回名单里的原始写法，避免大小写不一致又匹配不上）。
     *   传 null 或空集合表示名单未知：无法判断分类是否存在，此时保持原分类发送
     *   （只有空分类才兜底），避免把本来有效的分类误判成「其他」。
     * @param fallback 兜底分类名，默认 [FALLBACK_CATEGORY]。
     */
    fun resolveCategory(
        cateName: String,
        knownCategories: Collection<String>? = null,
        fallback: String = FALLBACK_CATEGORY,
    ): CategoryResolution {
        val candidates = categoryCandidates(cateName)
        val preferred = candidates.firstOrNull()
            ?: return CategoryResolution(fallback, true, cateName.trim().ifEmpty { null })
        // 名单未知 → 保持老行为：直接发子类优先的那个名字。
        if (knownCategories == null || knownCategories.isEmpty()) {
            return CategoryResolution(preferred, false, preferred)
        }
        // 忽略大小写/首尾空格建索引，保留名单里的原始写法（首次出现的那个）。
        val byName = HashMap<String, String>()
        for (raw in knownCategories) {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) continue
            val key = trimmed.lowercase()
            if (!byName.containsKey(key)) byName[key] = trimmed
        }
        // 子类 → 父类依次尝试，命中就用名单里的写法。
        for (candidate in candidates) {
            byName[candidate.lowercase()]?.let { return CategoryResolution(it, false, preferred) }
        }
        // 都没命中 → 归为兜底分类，原始分类留给备注。
        return CategoryResolution(fallback, true, preferred)
    }

    /**
     * 走了兜底分类时，把原始分类写进备注，避免信息丢失。
     * 备注里已包含原分类时不重复拼接。
     */
    fun mergeNoteWithOriginalCategory(note: String?, original: String?): String? {
        if (original.isNullOrEmpty()) return note
        if (!note.isNullOrEmpty() && note.contains(original)) return note
        val prefix = "原分类:$original"
        return if (note.isNullOrEmpty()) prefix else "$prefix | $note"
    }

    fun buildUri(bill: Bill): String {
        val params = LinkedHashMap<String, String>()
        params["amount"] = formatAmount(bill.amount)
        params["type"] = bill.type
        if (bill.type != "transfer") {
            bill.category?.takeIf { it.isNotEmpty() }?.let { params["category"] = it }
        }
        bill.note?.takeIf { it.isNotEmpty() }?.let { params["note"] = it }
        bill.account?.takeIf { it.isNotEmpty() }?.let { params["account"] = it }
        if (bill.type == "transfer") {
            bill.toAccount?.takeIf { it.isNotEmpty() }?.let { params["to_account"] = it }
        }
        if (bill.tags.isNotEmpty()) params["tags"] = bill.tags.joinToString(",")
        if (bill.timeMillis > 0) params["date"] = formatTime(bill.timeMillis)
        if (bill.silent) params["silent"] = "1"

        val query = params.entries.joinToString("&") { "${it.key}=${encode(it.value)}" }
        return "beecount://add?$query"
    }

    private fun formatAmount(value: Double): String =
        if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()

    private fun formatTime(millis: Long): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).format(Date(millis))

    private fun encode(value: String): String =
        URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}