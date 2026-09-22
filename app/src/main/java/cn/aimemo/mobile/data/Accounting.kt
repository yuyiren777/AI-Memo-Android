package cn.aimemo.mobile.data

import androidx.compose.runtime.Immutable
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

enum class AccountEntryType(val value: Int, val label: String) {
    EXPENSE(0, "支出"),
    INCOME(1, "收入");

    companion object {
        fun fromValue(value: Int) = entries.firstOrNull { it.value == value } ?: EXPENSE
    }
}

@Immutable
data class AccountEntry(
    val id: Long = 0,
    val type: AccountEntryType,
    val amountCents: Long,
    val category: String,
    val note: String = "",
    val occurredAt: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis(),
)

@Immutable
data class BudgetSettings(
    val monthlyBudgetsCents: Map<YearMonth, Long> = emptyMap(),
    val yearlyBudgetsCents: Map<Int, Long> = emptyMap(),
) {
    fun budgetForMonth(month: YearMonth): Long = monthlyBudgetsCents[month] ?: 0L

    fun budgetForYear(year: Int): Long = yearlyBudgetsCents[year] ?: 0L

    fun withMonthlyBudget(month: YearMonth, budgetCents: Long): BudgetSettings {
        require(budgetCents >= 0L) { "预算金额不能小于 0" }
        val updated = monthlyBudgetsCents.toMutableMap().apply {
            if (budgetCents == 0L) remove(month) else put(month, budgetCents)
        }
        return copy(monthlyBudgetsCents = updated.toMap())
    }

    fun withYearlyBudget(year: Int, budgetCents: Long): BudgetSettings {
        require(year in 1..9999) { "年份无效" }
        require(budgetCents >= 0L) { "预算金额不能小于 0" }
        val updated = yearlyBudgetsCents.toMutableMap().apply {
            if (budgetCents == 0L) remove(year) else put(year, budgetCents)
        }
        return copy(yearlyBudgetsCents = updated.toMap())
    }
}

@Immutable
data class AccountCategoryTotal(
    val category: String,
    val amountCents: Long,
)

@Immutable
data class AccountingSummary(
    val incomeCents: Long,
    val expenseCents: Long,
    val incomeCategories: List<AccountCategoryTotal>,
    val expenseCategories: List<AccountCategoryTotal>,
) {
    val balanceCents: Long get() = incomeCents - expenseCents
}

@Immutable
data class AccountMonthTotal(
    val month: YearMonth,
    val summary: AccountingSummary,
)

@Immutable
data class AccountTrendPoint(
    val label: String,
    val incomeCents: Long,
    val expenseCents: Long,
    val balanceCents: Long,
)

fun summarizeAccounts(entries: List<AccountEntry>): AccountingSummary {
    val income = entries.filter { it.type == AccountEntryType.INCOME }
    val expenses = entries.filter { it.type == AccountEntryType.EXPENSE }
    return AccountingSummary(
        incomeCents = income.sumOf(AccountEntry::amountCents),
        expenseCents = expenses.sumOf(AccountEntry::amountCents),
        incomeCategories = income.categoryTotals(),
        expenseCategories = expenses.categoryTotals(),
    )
}

fun accountEntriesInMonth(
    entries: List<AccountEntry>,
    month: YearMonth,
    zoneId: ZoneId = ZoneId.systemDefault(),
): List<AccountEntry> = entries.filter { entry ->
    YearMonth.from(Instant.ofEpochMilli(entry.occurredAt).atZone(zoneId)) == month
}

fun accountEntriesInYear(
    entries: List<AccountEntry>,
    year: Int,
    zoneId: ZoneId = ZoneId.systemDefault(),
): List<AccountEntry> = entries.filter { entry ->
    Instant.ofEpochMilli(entry.occurredAt).atZone(zoneId).year == year
}

fun accountMonthTotals(
    entries: List<AccountEntry>,
    year: Int,
    zoneId: ZoneId = ZoneId.systemDefault(),
): List<AccountMonthTotal> = (1..12).map { month ->
    val yearMonth = YearMonth.of(year, month)
    AccountMonthTotal(yearMonth, summarizeAccounts(accountEntriesInMonth(entries, yearMonth, zoneId)))
}

fun accountDailyTotals(
    entries: List<AccountEntry>,
    month: YearMonth,
    zoneId: ZoneId = ZoneId.systemDefault(),
): List<AccountTrendPoint> {
    val monthEntries = accountEntriesInMonth(entries, month, zoneId)
    return (1..month.lengthOfMonth()).map { day ->
        val summary = summarizeAccounts(monthEntries.filter { entry ->
            Instant.ofEpochMilli(entry.occurredAt).atZone(zoneId).dayOfMonth == day
        })
        AccountTrendPoint(
            label = day.toString(),
            incomeCents = summary.incomeCents,
            expenseCents = summary.expenseCents,
            balanceCents = summary.balanceCents,
        )
    }
}

fun accountYearTrend(
    entries: List<AccountEntry>,
    year: Int,
    zoneId: ZoneId = ZoneId.systemDefault(),
): List<AccountTrendPoint> = accountMonthTotals(entries, year, zoneId).map { total ->
    AccountTrendPoint(
        label = total.month.monthValue.toString(),
        incomeCents = total.summary.incomeCents,
        expenseCents = total.summary.expenseCents,
        balanceCents = total.summary.balanceCents,
    )
}

fun parseAmountToCents(value: String): Long? {
    val normalized = value.trim().replace(",", "").removePrefix("¥").trim()
    val amount = normalized.toBigDecimalOrNull() ?: return null
    if (amount <= BigDecimal.ZERO) return null
    return runCatching {
        amount.setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact()
    }.getOrNull()
}

fun formatMoney(cents: Long): String {
    val value = BigDecimal.valueOf(cents).movePointLeft(2)
    val sign = if (value.signum() < 0) "-" else ""
    return "$sign¥${value.abs().setScale(2).toPlainString()}"
}

private fun List<AccountEntry>.categoryTotals(): List<AccountCategoryTotal> =
    groupBy { it.category.ifBlank { "其他" } }
        .map { (category, entries) -> AccountCategoryTotal(category, entries.sumOf(AccountEntry::amountCents)) }
        .sortedByDescending(AccountCategoryTotal::amountCents)
