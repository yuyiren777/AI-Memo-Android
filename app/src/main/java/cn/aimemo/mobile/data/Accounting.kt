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
