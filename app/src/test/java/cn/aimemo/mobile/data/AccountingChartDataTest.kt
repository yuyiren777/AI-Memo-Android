package cn.aimemo.mobile.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId

class AccountingChartDataTest {
    private val zone = ZoneId.of("Asia/Shanghai")

    @Test
    fun dailyTotalsUseTheMonthLengthAndGroupByLocalDay() {
        val entries = listOf(
            entry(AccountEntryType.INCOME, 100, LocalDateTime.of(2026, 2, 1, 0, 1)),
            entry(AccountEntryType.EXPENSE, 40, LocalDateTime.of(2026, 2, 28, 23, 59)),
        )

        val points = accountDailyTotals(entries, YearMonth.of(2026, 2), zone)

        assertEquals(28, points.size)
        assertEquals(100, points.first().incomeCents)
        assertEquals(40, points.last().expenseCents)
    }

    @Test
    fun annualTrendAlwaysContainsTwelveMonths() {
        val entries = listOf(entry(AccountEntryType.INCOME, 250, LocalDateTime.of(2026, 7, 3, 9, 0)))

        val points = accountYearTrend(entries, 2026, zone)

        assertEquals(12, points.size)
        assertEquals("7", points[6].label)
        assertEquals(250, points[6].incomeCents)
        assertEquals(0, points[5].incomeCents)
    }

    private fun entry(type: AccountEntryType, amountCents: Long, dateTime: LocalDateTime) = AccountEntry(
        type = type,
        amountCents = amountCents,
        category = "测试",
        occurredAt = dateTime.atZone(zone).toInstant().toEpochMilli(),
    )
}
