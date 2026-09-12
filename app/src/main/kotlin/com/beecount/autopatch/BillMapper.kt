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

    /** BillType 枚举名 → BeeCount 的 type；报销/借贷/还款/退款等扩展类型按收支大类回退。 */
    fun mapType(billTypeName: String): String = when {
        billTypeName == "Transfer" -> "transfer"
        billTypeName.startsWith("Income") -> "income"
        else -> "expense"
    }

    /**
     * AutoAccounting 的 cateName 以 '-' 分隔父/子分类（见 BillInfoModel.categoryPair）。
     * BeeCount 只按单个分类名匹配，这里取子类优先、回退父类。
     */
    fun categoryOf(cateName: String): String? {
        val name = cateName.trim()
        if (name.isEmpty()) return null
        val i = name.indexOf('-')
        val parent = if (i <= 0) name else name.substring(0, i).trim()
        val child = if (i in 1 until name.lastIndex) name.substring(i + 1).trim() else ""
        return child.ifEmpty { parent }.ifEmpty { null }
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
