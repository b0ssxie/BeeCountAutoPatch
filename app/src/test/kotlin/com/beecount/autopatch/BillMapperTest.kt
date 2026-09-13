package com.beecount.autopatch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URLDecoder

class BillMapperTest {

    private fun query(uri: String): Map<String, String> {
        assertEquals("beecount://add", uri.substringBefore("?"))
        return uri.substringAfter("?").split("&").associate {
            val i = it.indexOf('=')
            it.substring(0, i) to URLDecoder.decode(it.substring(i + 1), "UTF-8")
        }
    }

    @Test
    fun mapType_fallsBackByMajorCategory() {
        assertEquals("expense", BillMapper.mapType("Expend"))
        assertEquals("expense", BillMapper.mapType("ExpendReimbursement"))
        assertEquals("expense", BillMapper.mapType("ExpendLending"))
        assertEquals("income", BillMapper.mapType("Income"))
        assertEquals("income", BillMapper.mapType("IncomeRefund"))
        assertEquals("income", BillMapper.mapType("IncomeReimbursement"))
        assertEquals("transfer", BillMapper.mapType("Transfer"))
    }

    @Test
    fun categoryOf_prefersChildThenParent() {
        assertEquals("餐饮", BillMapper.categoryOf("餐饮"))
        assertEquals("早餐", BillMapper.categoryOf("餐饮-早餐"))
        assertEquals("餐饮", BillMapper.categoryOf("餐饮-"))
        assertNull(BillMapper.categoryOf(""))
        assertNull(BillMapper.categoryOf("   "))
    }

    // ---- 分类兜底：找不到就归为「其他」 ----

    @Test
    fun resolveCategory_emptyNameFallsBack() {
        val r = BillMapper.resolveCategory("", setOf("餐饮", "其他"))
        assertEquals("其他", r.category)
        assertTrue(r.fallbackUsed)
        assertNull(r.original)
    }

    @Test
    fun resolveCategory_unknownListKeepsOriginal() {
        // 名单未知（null 或空）时无法判断是否存在，保持原分类，避免误兜底。
        val nullList = BillMapper.resolveCategory("餐饮-早餐", null)
        assertEquals("早餐", nullList.category)
        assertFalse(nullList.fallbackUsed)

        val emptyList = BillMapper.resolveCategory("餐饮-早餐", emptySet())
        assertEquals("早餐", emptyList.category)
        assertFalse(emptyList.fallbackUsed)
    }

    @Test
    fun resolveCategory_hitUsesSpellingFromList() {
        // 命中时回写名单里的写法，避免大小写/空格不一致导致蜜蜂记账仍匹配不上。
        val r = BillMapper.resolveCategory("餐饮-早餐", listOf(" 早餐 ", "其他"))
        assertEquals("早餐", r.category)
        assertFalse(r.fallbackUsed)

        val cased = BillMapper.resolveCategory("Food", listOf("food"))
        assertEquals("food", cased.category)
        assertFalse(cased.fallbackUsed)
    }

    @Test
    fun resolveCategory_missFallsBackToOther() {
        // 子类、父类都不在名单里 → 归为「其他」。
        val r = BillMapper.resolveCategory("餐饮-夜宵", setOf("交通", "其他"))
        assertEquals("其他", r.category)
        assertTrue(r.fallbackUsed)
        assertEquals("夜宵", r.original)
    }

    @Test
    fun resolveCategory_prefersChildOverParent() {
        val r = BillMapper.resolveCategory("餐饮-夜宵", setOf("餐饮", "夜宵"))
        assertEquals("夜宵", r.category)
        assertFalse(r.fallbackUsed)
    }

    @Test
    fun resolveCategory_fallsBackToParentWhenChildMissing() {
        // 子类没建、父类建了 → 发父类，比直接归到「其他」更贴近原意。
        val r = BillMapper.resolveCategory("餐饮-夜宵", setOf("餐饮", "其他"))
        assertEquals("餐饮", r.category)
        assertFalse(r.fallbackUsed)
        assertEquals("夜宵", r.original)
    }

    @Test
    fun categoryCandidates_orderIsChildThenParent() {
        assertEquals(listOf("早餐", "餐饮"), BillMapper.categoryCandidates("餐饮-早餐"))
        assertEquals(listOf("餐饮"), BillMapper.categoryCandidates("餐饮"))
        assertEquals(listOf("餐饮"), BillMapper.categoryCandidates("餐饮-"))
        assertEquals(listOf("餐饮"), BillMapper.categoryCandidates(" 餐饮 "))
        assertEquals(emptyList<String>(), BillMapper.categoryCandidates("   "))
    }

    @Test
    fun resolveCategory_customFallbackName() {
        val r = BillMapper.resolveCategory("夜宵", setOf("餐饮"), fallback = "未分类")
        assertEquals("未分类", r.category)
        assertTrue(r.fallbackUsed)
    }

    @Test
    fun mergeNoteWithOriginalCategory_prependsAndKeepsNote() {
        assertEquals("原分类:夜宵", BillMapper.mergeNoteWithOriginalCategory(null, "夜宵"))
        assertEquals("原分类:夜宵", BillMapper.mergeNoteWithOriginalCategory("", "夜宵"))
        assertEquals("原分类:夜宵 | 小龙虾", BillMapper.mergeNoteWithOriginalCategory("小龙虾", "夜宵"))
    }

    @Test
    fun mergeNoteWithOriginalCategory_avoidsDuplicate() {
        assertEquals("夜宵 小龙虾", BillMapper.mergeNoteWithOriginalCategory("夜宵 小龙虾", "夜宵"))
        assertNull(BillMapper.mergeNoteWithOriginalCategory(null, null))
        assertEquals("小龙虾", BillMapper.mergeNoteWithOriginalCategory("小龙虾", ""))
    }

    @Test
    fun buildUri_fallbackCategoryKeepsOriginalInNote() {
        // 子类、父类都不在名单里 → 兜底为「其他」，原分类进备注。
        val r = BillMapper.resolveCategory("餐饮-夜宵", setOf("交通", "其他"))
        val uri = BillMapper.buildUri(
            BillMapper.Bill(
                amount = 30.0,
                type = "expense",
                category = r.category,
                note = BillMapper.mergeNoteWithOriginalCategory("小龙虾", r.original),
                account = null,
                toAccount = null,
                tags = emptyList(),
                timeMillis = 0L,
            ),
        )
        val q = query(uri)
        assertEquals("其他", q["category"])
        assertEquals("原分类:夜宵 | 小龙虾", q["note"])
    }

    // ---- 深链构建 ----

    @Test
    fun buildUri_expense() {
        val uri = BillMapper.buildUri(
            BillMapper.Bill(
                amount = 26.5,
                type = "expense",
                category = "餐饮/早餐",
                note = "豆浆 油条+鸡蛋",
                account = "支付宝",
                toAccount = null,
                tags = listOf("差旅", "报销"),
                timeMillis = 0L,
            ),
        )
        val q = query(uri)
        assertEquals("26.5", q["amount"])
        assertEquals("expense", q["type"])
        assertEquals("餐饮/早餐", q["category"])
        assertEquals("豆浆 油条+鸡蛋", q["note"])
        assertEquals("支付宝", q["account"])
        assertEquals("差旅,报销", q["tags"])
        assertEquals("1", q["silent"])
        assertFalse(q.containsKey("to_account"))
        assertFalse(q.containsKey("date"))
    }

    @Test
    fun buildUri_transferHasNoCategoryAndUsesToAccount() {
        val uri = BillMapper.buildUri(
            BillMapper.Bill(
                amount = 100.0,
                type = "transfer",
                category = "不该出现",
                note = null,
                account = "银行卡",
                toAccount = "现金",
                tags = emptyList(),
                timeMillis = 0L,
            ),
        )
        val q = query(uri)
        assertEquals("100", q["amount"])
        assertEquals("transfer", q["type"])
        assertEquals("银行卡", q["account"])
        assertEquals("现金", q["to_account"])
        assertFalse(q.containsKey("category"))
        assertFalse(q.containsKey("tags"))
    }

    @Test
    fun buildUri_dateIsLocalIso8601WithoutTimezone() {
        val millis = 1706745600000L // 2024-02-01T00:00:00Z 附近，仅校验格式
        val uri = BillMapper.buildUri(
            BillMapper.Bill(1.0, "expense", "餐饮", null, null, null, emptyList(), millis),
        )
        val date = query(uri)["date"]!!
        assertTrue("date=$date", Regex("""\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}""").matches(date))
    }

    @Test
    fun buildUri_silentFalseOmitsSilentParam() {
        val uri = BillMapper.buildUri(
            BillMapper.Bill(1.0, "expense", "餐饮", null, null, null, emptyList(), 0L, silent = false),
        )
        assertFalse(query(uri).containsKey("silent"))
    }
}