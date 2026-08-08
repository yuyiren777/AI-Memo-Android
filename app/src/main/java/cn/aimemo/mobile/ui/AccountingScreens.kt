package cn.aimemo.mobile.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.aimemo.mobile.data.AccountCategoryTotal
import cn.aimemo.mobile.data.AccountEntry
import cn.aimemo.mobile.data.AccountEntryType
import cn.aimemo.mobile.data.AccountingSummary
import cn.aimemo.mobile.data.accountEntriesInMonth
import cn.aimemo.mobile.data.accountEntriesInYear
import cn.aimemo.mobile.data.accountMonthTotals
import cn.aimemo.mobile.data.formatMoney
import cn.aimemo.mobile.data.parseAmountToCents
import cn.aimemo.mobile.data.summarizeAccounts
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val ExpenseColor = Color(0xFFC74646)
private val IncomeColor = Color(0xFF218653)

@Composable
fun AccountingScreen(
    contentPadding: PaddingValues,
    entries: List<AccountEntry>,
    onAdd: () -> Unit,
    onEdit: (AccountEntry) -> Unit,
    onDelete: (AccountEntry) -> Unit,
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var monthOffset by rememberSaveable { mutableIntStateOf(0) }
    var selectedYear by rememberSaveable { mutableIntStateOf(LocalDate.now().year) }
    var pendingDelete by remember { mutableStateOf<AccountEntry?>(null) }
    val month = remember(monthOffset) { YearMonth.now().plusMonths(monthOffset.toLong()) }
    val monthEntries = remember(entries, month) { accountEntriesInMonth(entries, month) }
    val monthSummary = remember(monthEntries) { summarizeAccounts(monthEntries) }
    val yearEntries = remember(entries, selectedYear) { accountEntriesInYear(entries, selectedYear) }
    val yearSummary = remember(yearEntries) { summarizeAccounts(yearEntries) }

    pendingDelete?.let { entry ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除这笔流水？") },
            text = { Text("${entry.type.label} ${formatMoney(entry.amountCents)} · ${entry.category}\n删除后无法恢复。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDelete = null
                        onDelete(entry)
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("取消") } },
        )
    }

    Column(Modifier.fillMaxSize().padding(contentPadding)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("记账", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("${entries.size} 笔本地流水", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Button(onClick = onAdd) {
                Icon(Icons.Outlined.Add, null)
                Spacer(Modifier.width(5.dp))
                Text("记一笔")
            }
        }
        TabRow(selectedTabIndex = selectedTab) {
            listOf("流水", "月总结", "年总结").forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title) },
                )
            }
        }
        when (selectedTab) {
            0 -> AccountLedger(
                month = month,
                entries = monthEntries,
                summary = monthSummary,
                onPrevious = { monthOffset-- },
                onNext = { monthOffset++ },
                onEdit = onEdit,
                onDelete = { pendingDelete = it },
            )
            1 -> AccountMonthSummary(
                month = month,
                entryCount = monthEntries.size,
                summary = monthSummary,
                onPrevious = { monthOffset-- },
                onNext = { monthOffset++ },
            )
            else -> AccountYearSummary(
                year = selectedYear,
                entries = entries,
                entryCount = yearEntries.size,
                summary = yearSummary,
                onPrevious = { selectedYear-- },
                onNext = { selectedYear++ },
            )
        }
    }
}

@Composable
private fun AccountLedger(
    month: YearMonth,
    entries: List<AccountEntry>,
    summary: AccountingSummary,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onEdit: (AccountEntry) -> Unit,
    onDelete: (AccountEntry) -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        item { MonthSelector(month, onPrevious, onNext) }
        item { AccountSummaryStrip(summary) }
        if (entries.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(vertical = 54.dp), contentAlignment = Alignment.Center) {
                    Text("这个月还没有流水", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            val grouped = entries.groupBy(::entryDate)
            grouped.forEach { (date, dayEntries) ->
                item(key = "day_$date") {
                    Text(
                        date.format(ACCOUNT_DAY_FORMATTER),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                items(dayEntries, key = AccountEntry::id) { entry ->
                    AccountEntryRow(entry, onEdit = { onEdit(entry) }, onDelete = { onDelete(entry) })
                }
            }
        }
    }
}

@Composable
private fun AccountMonthSummary(
    month: YearMonth,
    entryCount: Int,
    summary: AccountingSummary,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { MonthSelector(month, onPrevious, onNext) }
        item { AccountSummaryStrip(summary) }
        item { Text("共 $entryCount 笔", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item { CategorySummarySection("支出分类", summary.expenseCategories, summary.expenseCents, ExpenseColor) }
        item { CategorySummarySection("收入分类", summary.incomeCategories, summary.incomeCents, IncomeColor) }
    }
}

@Composable
private fun AccountYearSummary(
    year: Int,
    entries: List<AccountEntry>,
    entryCount: Int,
    summary: AccountingSummary,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    val totals = remember(entries, year) { accountMonthTotals(entries, year) }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        item { YearSelector(year, onPrevious, onNext) }
        item { AccountSummaryStrip(summary) }
        item { Text("全年共 $entryCount 笔", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item {
            Text("每月收支", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        items(totals, key = { it.month.monthValue }) { total ->
            Row(
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp)).padding(11.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("${total.month.monthValue}月", Modifier.width(38.dp), fontWeight = FontWeight.Bold)
                AnnualMoneyColumn("收入", total.summary.incomeCents, IncomeColor, Modifier.weight(1f))
                AnnualMoneyColumn("支出", total.summary.expenseCents, ExpenseColor, Modifier.weight(1f))
                AnnualMoneyColumn("结余", total.summary.balanceCents, MaterialTheme.colorScheme.primary, Modifier.weight(1f))
            }
        }
        item { CategorySummarySection("年度支出分类", summary.expenseCategories, summary.expenseCents, ExpenseColor) }
        item { CategorySummarySection("年度收入分类", summary.incomeCategories, summary.incomeCents, IncomeColor) }
    }
}

@Composable
private fun AccountSummaryStrip(summary: AccountingSummary) {
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp)).padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MoneyMetric("收入", summary.incomeCents, IncomeColor, Modifier.weight(1f))
        MoneyMetric("支出", summary.expenseCents, ExpenseColor, Modifier.weight(1f))
        MoneyMetric("结余", summary.balanceCents, MaterialTheme.colorScheme.primary, Modifier.weight(1f))
    }
}

@Composable
private fun MoneyMetric(label: String, amount: Long, color: Color, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            formatMoney(amount),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun AnnualMoneyColumn(label: String, amount: Long, color: Color, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            formatMoney(amount),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun CategorySummarySection(
    title: String,
    categories: List<AccountCategoryTotal>,
    totalCents: Long,
    amountColor: Color,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        if (categories.isEmpty()) {
            Text("暂无数据", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            categories.forEach { item ->
                val percentage = if (totalCents == 0L) 0 else (item.amountCents * 100 / totalCents).toInt()
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(item.category, Modifier.weight(1f), fontWeight = FontWeight.Medium)
                    Text("$percentage%", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(14.dp))
                    Text(formatMoney(item.amountCents), color = amountColor, fontWeight = FontWeight.SemiBold)
                }
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun AccountEntryRow(entry: AccountEntry, onEdit: () -> Unit, onDelete: () -> Unit) {
    val color = if (entry.type == AccountEntryType.INCOME) IncomeColor else ExpenseColor
    val prefix = if (entry.type == AccountEntryType.INCOME) "+" else "-"
    Surface(
        Modifier.fillMaxWidth().clickable(onClick = onEdit),
        shape = RoundedCornerShape(6.dp),
        tonalElevation = 1.dp,
    ) {
        Row(Modifier.padding(start = 13.dp, top = 9.dp, bottom = 9.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(entry.category, fontWeight = FontWeight.Bold)
                Text(
                    buildString {
                        append(entryTime(entry).format(ACCOUNT_TIME_FORMATTER))
                        entry.note.takeIf(String::isNotBlank)?.let { append(" · ").append(it) }
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text("$prefix${formatMoney(entry.amountCents)}", color = color, fontWeight = FontWeight.Bold)
            IconButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, "编辑流水") }
            IconButton(onClick = onDelete) {
                Icon(Icons.Outlined.DeleteOutline, "删除流水", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun MonthSelector(month: YearMonth, onPrevious: () -> Unit, onNext: () -> Unit) {
    PeriodSelector(month.format(ACCOUNT_MONTH_FORMATTER), onPrevious, onNext)
}

@Composable
private fun YearSelector(year: Int, onPrevious: () -> Unit, onNext: () -> Unit) {
    PeriodSelector("${year}年", onPrevious, onNext)
}

@Composable
private fun PeriodSelector(label: String, onPrevious: () -> Unit, onNext: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrevious) { Icon(Icons.Outlined.ChevronLeft, "上一周期") }
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        IconButton(onClick = onNext) { Icon(Icons.Outlined.ChevronRight, "下一周期") }
    }
}

@Composable
fun AccountEntryEditorScreen(
    contentPadding: PaddingValues,
    initial: AccountEntry,
    onCancel: () -> Unit,
    onSave: (AccountEntry) -> Unit,
) {
    val context = LocalContext.current
    val initialDateTime = remember(initial) {
        Instant.ofEpochMilli(initial.occurredAt).atZone(ZoneId.systemDefault()).toLocalDateTime()
    }
    var type by remember(initial) { mutableStateOf(initial.type) }
    var amount by remember(initial) { mutableStateOf(amountInput(initial.amountCents)) }
    var category by remember(initial) { mutableStateOf(initial.category) }
    var note by remember(initial) { mutableStateOf(initial.note) }
    var date by remember(initial) { mutableStateOf(initialDateTime.toLocalDate()) }
    var time by remember(initial) { mutableStateOf(initialDateTime.toLocalTime().withSecond(0).withNano(0)) }
    val categories = if (type == AccountEntryType.EXPENSE) EXPENSE_CATEGORIES else INCOME_CATEGORIES
    val amountCents = remember(amount) { parseAmountToCents(amount) }
    val dateDialog = remember(date) {
        DatePickerDialog(
            context,
            { _, year, month, day -> date = LocalDate.of(year, month + 1, day) },
            date.year,
            date.monthValue - 1,
            date.dayOfMonth,
        )
    }
    val timeDialog = remember(time) {
        TimePickerDialog(
            context,
            { _, hour, minute -> time = LocalTime.of(hour, minute) },
            time.hour,
            time.minute,
            true,
        )
    }

    Column(
        Modifier.fillMaxSize().padding(contentPadding).verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        Text(
            if (initial.id == 0L) "记一笔" else "编辑流水",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AccountEntryType.entries.forEach { option ->
                FilterChip(
                    selected = type == option,
                    onClick = {
                        type = option
                        val newCategories = if (option == AccountEntryType.EXPENSE) EXPENSE_CATEGORIES else INCOME_CATEGORIES
                        if (category !in newCategories) category = newCategories.first()
                    },
                    label = { Text(option.label) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        OutlinedTextField(
            value = amount,
            onValueChange = { amount = sanitizeAmountInput(it) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("金额（元）") },
            placeholder = { Text("0.00") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            isError = amount.isNotBlank() && amountCents == null,
            supportingText = if (amount.isNotBlank() && amountCents == null) {
                { Text("请输入大于 0 的有效金额") }
            } else null,
        )
        Text("分类", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            categories.forEach { option ->
                FilterChip(category == option, { category = option }, label = { Text(option) })
            }
        }
        OutlinedTextField(
            value = category,
            onValueChange = { category = it.take(20) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("分类名称") },
            singleLine = true,
        )
        Text("发生时间", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = dateDialog::show, modifier = Modifier.weight(1f)) {
                Icon(Icons.Outlined.CalendarMonth, null)
                Spacer(Modifier.width(5.dp))
                Text(date.format(ACCOUNT_DATE_FORMATTER))
            }
            OutlinedButton(onClick = timeDialog::show, modifier = Modifier.weight(1f)) {
                Icon(Icons.Outlined.Schedule, null)
                Spacer(Modifier.width(5.dp))
                Text(time.format(ACCOUNT_TIME_FORMATTER))
            }
        }
        OutlinedTextField(
            value = note,
            onValueChange = { note = it.take(200) },
            modifier = Modifier.fillMaxWidth().height(120.dp),
            label = { Text("备注（选填）") },
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)) {
            TextButton(onClick = onCancel) { Text("取消") }
            Button(
                onClick = {
                    val occurredAt = date.atTime(time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    onSave(
                        initial.copy(
                            type = type,
                            amountCents = requireNotNull(amountCents),
                            category = category.trim(),
                            note = note.trim(),
                            occurredAt = occurredAt,
                        )
                    )
                },
                enabled = amountCents != null && category.isNotBlank(),
            ) { Text("保存") }
        }
        Spacer(Modifier.height(16.dp))
    }
}

private fun entryDate(entry: AccountEntry): LocalDate =
    Instant.ofEpochMilli(entry.occurredAt).atZone(ZoneId.systemDefault()).toLocalDate()

private fun entryTime(entry: AccountEntry): LocalTime =
    Instant.ofEpochMilli(entry.occurredAt).atZone(ZoneId.systemDefault()).toLocalTime()

private fun amountInput(cents: Long): String = if (cents <= 0L) "" else
    BigDecimal.valueOf(cents).movePointLeft(2).stripTrailingZeros().toPlainString()

private fun sanitizeAmountInput(value: String): String {
    val filtered = value.filter { it.isDigit() || it == '.' }
    val dot = filtered.indexOf('.')
    if (dot < 0) return filtered.take(10)
    return filtered.take(dot + 1) + filtered.drop(dot + 1).filter(Char::isDigit).take(2)
}

private val EXPENSE_CATEGORIES = listOf("餐饮", "交通", "购物", "住房", "医疗", "娱乐", "教育", "其他")
private val INCOME_CATEGORIES = listOf("工资", "奖金", "兼职", "理财", "红包", "其他")
private val ACCOUNT_MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy年M月")
private val ACCOUNT_DAY_FORMATTER = DateTimeFormatter.ofPattern("M月d日 EEEE")
private val ACCOUNT_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy年M月d日")
private val ACCOUNT_TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm")
