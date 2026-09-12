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
}
