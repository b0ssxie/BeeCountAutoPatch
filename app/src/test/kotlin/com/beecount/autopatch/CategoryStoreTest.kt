package com.beecount.autopatch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryStoreTest {

    @Test
    fun parse_skipsBlankLinesAndTrims() {
        val set = CategoryStore.parse("\n  餐饮  \n\n早餐\n   \n")
        assertEquals(listOf("餐饮", "早餐"), set.toList())
    }

    @Test
    fun parse_handlesCrlf() {
        val set = CategoryStore.parse("餐饮\r\n早餐\r\n")
        assertEquals(listOf("餐饮", "早餐"), set.toList())
    }

    @Test
    fun parse_dedupesIgnoringCaseKeepingFirstSpelling() {
        val set = CategoryStore.parse("Food\nfood\nFOOD\n其他\n")
        assertEquals(listOf("Food", "其他"), set.toList())
        assertEquals(2, set.size)
    }

    @Test
    fun parse_stripsBomFromFirstLine() {
        // 用 Windows 记事本保存时首行可能带 BOM，不能把 BOM 当成分类名的一部分。
        val set = CategoryStore.parse("\uFEFF餐饮\n早餐\n")
        assertEquals(listOf("餐饮", "早餐"), set.toList())
    }

    @Test
    fun parse_keepsInnerSpaces() {
        val set = CategoryStore.parse("餐饮 外卖\n")
        assertEquals(listOf("餐饮 外卖"), set.toList())
    }

    @Test
    fun parse_emptyTextGivesEmptySet() {
        assertTrue(CategoryStore.parse("").isEmpty())
        assertTrue(CategoryStore.parse("  \n\n ").isEmpty())
    }

    @Test
    fun path_prefersGivenFilesDirAndFallsBackToTargetDataDir() {
        assertEquals(
            "/data/data/net.ankio.auto/files/autopatch_categories.txt",
            CategoryStore.path(null),
        )
        assertEquals(
            "/data/user/0/net.ankio.auto/files/autopatch_categories.txt",
            CategoryStore.path("/data/user/0/net.ankio.auto/files"),
        )
        // filesDir 为空的字符串同样走兜底路径，避免拼出 "/autopatch_categories.txt"。
        assertEquals(
            "/data/data/net.ankio.auto/files/autopatch_categories.txt",
            CategoryStore.path(""),
        )
    }

    @Test
    fun parse_thenResolve_matchesBeeCountSpelling() {
        // 端到端：名单文件里的分类能被 BillMapper 命中，未命中的归为「其他」。
        val known = CategoryStore.parse("餐饮\n其他\n娱乐\n")

        val hit = BillMapper.resolveCategory("餐饮-早餐", known)
        assertEquals("餐饮", hit.category)
        assertTrue(!hit.fallbackUsed)

        val miss = BillMapper.resolveCategory("交通-打车", known)
        assertEquals("其他", miss.category)
        assertTrue(miss.fallbackUsed)
        assertEquals("打车", miss.original)
    }
}