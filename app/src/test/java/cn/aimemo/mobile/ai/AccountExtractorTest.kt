package cn.aimemo.mobile.ai

import cn.aimemo.mobile.data.AccountEntryType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountExtractorTest {
    @Test
    fun parsesMultipleReceiptItemsWithoutLosingCentPrecision() {
        val results = AccountExtractor.parse(
            """
                {"count":2,"entries":[
                  {"type":"expense","amount_yuan":12.50,"category":"餐饮","suggested_category":null,"note":"鲜牛奶 2盒"},
                  {"type":"expense","amount_yuan":3.20,"category":"日用百货","suggested_category":null,"note":"抽纸 1包"}
                ]}
            """.trimIndent()
        )

        assertEquals(2, results.size)
        assertEquals(AccountEntryType.EXPENSE, results[0].type)
        assertEquals(1_250L, results[0].amountCents)
        assertEquals("鲜牛奶 2盒", results[0].note)
        assertEquals(320L, results[1].amountCents)
        assertNull(results[1].suggestedCategory)
    }

    @Test
    fun keepsUnreadableReceiptFieldsEmptyForManualReview() {
        val result = AccountExtractor.parse(
            """{"count":1,"entries":[{"type":"expense","amount_yuan":null,"category":null,"suggested_category":"食品","note":"模糊商品"}]}"""
        ).single()

        assertEquals(AccountEntryType.EXPENSE, result.type)
        assertNull(result.amountCents)
        assertNull(result.category)
        assertEquals("食品", result.suggestedCategory)
    }

    @Test
    fun receiptPromptRequiresExactCategoriesAndPreventsTotalDuplication() {
        val prompt = AccountExtractor.imagePrompt(
            expenseCategories = listOf("餐饮", "日用百货"),
            incomeCategories = listOf("工资", "退款"),
            recoveryAttempt = false,
        )

        assertTrue(prompt.contains("餐饮、日用百货"))
        assertTrue(prompt.contains("工资、退款"))
        assertTrue(prompt.contains("绝不能在商品明细之外重复记账"))
        assertTrue(prompt.contains("如果各商品金额清晰，逐项输出且不要再输出合计"))
    }
}
