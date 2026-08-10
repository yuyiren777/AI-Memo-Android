package cn.aimemo.mobile.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId

class AccountingTest {
    private val zone = ZoneId.of("Asia/Shanghai")

    @Test
    fun summarizesIncomeExpenseBalanceAndCategories() {
        val entries = listOf(
            entry(AccountEntryType.INCOME, 500_000, "工资"),
            entry(AccountEntryType.INCOME, 20_000, "兼职"),
            entry(AccountEntryType.EXPENSE, 3_500, "餐饮"),
            entry(AccountEntryType.EXPENSE, 1_200, "交通"),
            entry(AccountEntryType.EXPENSE, 800, "餐饮"),
        )

        val summary = summarizeAccounts(entries)

        assertEquals(520_000, summary.incomeCents)
        assertEquals(5_500, summary.expenseCents)
        assertEquals(514_500, summary.balanceCents)
        assertEquals(
            listOf(AccountCategoryTotal("餐饮", 4_300), AccountCategoryTotal("交通", 1_200)),
            summary.expenseCategories,
        )
    }

    @Test
    fun monthFilteringUsesTheExactLocalOccurrenceTime() {
        val aprilEnd = entry(
            AccountEntryType.EXPENSE,
            100,
            "其他",
            LocalDateTime.of(2026, 4, 30, 23, 59).atZone(zone).toInstant().toEpochMilli(),
        )
        val mayStart = entry(
            AccountEntryType.INCOME,
            200,
            "其他",
            LocalDateTime.of(2026, 5, 1, 0, 0).atZone(zone).toInstant().toEpochMilli(),
        )

        assertEquals(listOf(aprilEnd), accountEntriesInMonth(listOf(aprilEnd, mayStart), YearMonth.of(2026, 4), zone))
        assertEquals(listOf(mayStart), accountEntriesInMonth(listOf(aprilEnd, mayStart), YearMonth.of(2026, 5), zone))
    }

    @Test
    fun annualTotalsContainEveryMonth() {
        val mayIncome = entry(
            AccountEntryType.INCOME,
            12_345,
            "兼职",
            LocalDateTime.of(2026, 5, 8, 12, 30).atZone(zone).toInstant().toEpochMilli(),
        )

        val totals = accountMonthTotals(listOf(mayIncome), 2026, zone)

        assertEquals(12, totals.size)
        assertEquals(12_345, totals[4].summary.incomeCents)
        assertEquals(0, totals[3].summary.incomeCents)
    }

    @Test
    fun parsesAndFormatsMoneyAtCentPrecision() {
        assertEquals(1_235L, parseAmountToCents("12.345"))
        assertEquals(123_456L, parseAmountToCents("1,234.56"))
        assertEquals(null, parseAmountToCents("0"))
        assertEquals("-¥1.25", formatMoney(-125))
    }

    private fun entry(
        type: AccountEntryType,
        amountCents: Long,
        category: String,
        occurredAt: Long = 0L,
    ) = AccountEntry(
        type = type,
        amountCents = amountCents,
        category = category,
        occurredAt = occurredAt,
    )
}
