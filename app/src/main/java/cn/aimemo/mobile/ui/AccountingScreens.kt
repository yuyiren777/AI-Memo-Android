package cn.aimemo.mobile.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.graphics.Paint
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.BusinessCenter
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.Checkroom
import androidx.compose.material.icons.outlined.ChildCare
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.HomeWork
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.LocalHospital
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Percent
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.material.icons.outlined.VolunteerActivism
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material.icons.outlined.WorkOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.aimemo.mobile.data.AccountCategoryTotal
import cn.aimemo.mobile.data.AccountEntry
import cn.aimemo.mobile.data.AccountEntryType
import cn.aimemo.mobile.data.AccountingSummary
import cn.aimemo.mobile.data.BudgetSettings
import cn.aimemo.mobile.ai.AccountClassificationResult
import cn.aimemo.mobile.data.AccountTrendPoint
import cn.aimemo.mobile.data.accountDailyTotals
import cn.aimemo.mobile.data.accountEntriesInMonth
import cn.aimemo.mobile.data.accountEntriesInYear
import cn.aimemo.mobile.data.accountMonthTotals
import cn.aimemo.mobile.data.accountYearTrend
import cn.aimemo.mobile.data.formatMoney
import cn.aimemo.mobile.data.parseAmountToCents
import cn.aimemo.mobile.data.summarizeAccounts
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val ExpenseColor = Color(0xFFC74646)
private val IncomeColor = Color(0xFF218653)
private val ChartIncomeColor = Color(0xFFD3A52C)
private val ChartBalanceColor = Color(0xFF2E9D64)
private val PieColors = listOf(
    Color(0xFFC74646), Color(0xFFDB7B35), Color(0xFFD3A52C), Color(0xFF7E9F3B),
    Color(0xFF2E9D64), Color(0xFF2F8FA3), Color(0xFF4D76B8), Color(0xFF7655A8),
    Color(0xFFA65388), Color(0xFF8B6F47),
)

@Composable
fun AccountingScreen(
    contentPadding: PaddingValues,
    entries: List<AccountEntry>,
    budgetSettings: BudgetSettings,
    analyzingFinancial: Boolean,
    financialAnalysisPeriodKey: String?,
    financialAnalysis: String,
    onAdd: () -> Unit,
    onEdit: (AccountEntry) -> Unit,
    onDelete: (AccountEntry) -> Unit,
    onDeleteMany: (Collection<AccountEntry>) -> Unit,
    onAnalyzeFinancial: (String, String, AccountingSummary) -> Unit,
    onSaveMonthlyBudget: (YearMonth, Long) -> Unit,
    onSaveYearlyBudget: (Int, Long) -> Unit,
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var monthOffset by rememberSaveable { mutableIntStateOf(0) }
    var selectedYear by rememberSaveable { mutableIntStateOf(LocalDate.now().year) }
    var selectionMode by rememberSaveable { mutableStateOf(false) }
    var selectedEntryIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var pendingDelete by remember { mutableStateOf<AccountEntry?>(null) }
    var pendingBatchDelete by remember { mutableStateOf(false) }
    val month = remember(monthOffset) { YearMonth.now().plusMonths(monthOffset.toLong()) }
    val monthEntries = remember(entries, month) { accountEntriesInMonth(entries, month) }
    val monthSummary = remember(monthEntries) { summarizeAccounts(monthEntries) }
    val yearEntries = remember(entries, selectedYear) { accountEntriesInYear(entries, selectedYear) }
    val yearSummary = remember(yearEntries) { summarizeAccounts(yearEntries) }
    val selectedEntries = monthEntries.filter { it.id in selectedEntryIds }

    LaunchedEffect(monthEntries, selectedTab, selectionMode) {
        selectedEntryIds = selectedEntryIds.intersect(monthEntries.mapTo(mutableSetOf(), AccountEntry::id))
        if (selectedTab != 0 && selectionMode) selectionMode = false
    }

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
    if (pendingBatchDelete) {
        AlertDialog(
            onDismissRequest = { pendingBatchDelete = false },
            title = { Text("确认删除所选流水") },
            text = { Text("确定删除选中的 ${selectedEntries.size} 笔流水吗？删除后无法恢复。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingBatchDelete = false
                        selectionMode = false
                        selectedEntryIds = emptySet()
                        onDeleteMany(selectedEntries)
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("确认删除") }
            },
            dismissButton = { TextButton(onClick = { pendingBatchDelete = false }) { Text("取消") } },
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (selectedTab == 0) {
                    OutlinedButton(
                        onClick = {
                            selectionMode = !selectionMode
                            selectedEntryIds = emptySet()
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp),
                    ) { Text(if (selectionMode) "取消多选" else "多选") }
                }
                Button(onClick = onAdd) {
                    Icon(Icons.Outlined.Add, null)
                    Spacer(Modifier.width(5.dp))
                    Text("记一笔")
                }
            }
        }
        if (selectionMode) {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("已选择 ${selectedEntries.size} 笔", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                TextButton(
                    onClick = { selectedEntryIds = monthEntries.mapTo(mutableSetOf(), AccountEntry::id) },
                    enabled = monthEntries.isNotEmpty(),
                ) { Text("全选") }
                TextButton(onClick = { selectedEntryIds = emptySet() }, enabled = selectedEntries.isNotEmpty()) { Text("清空") }
                TextButton(
                    onClick = { pendingBatchDelete = true },
                    enabled = selectedEntries.isNotEmpty(),
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Icon(Icons.Outlined.DeleteOutline, null, Modifier.size(17.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("删除")
                }
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
                budgetCents = budgetSettings.budgetForMonth(month),
                onPrevious = { monthOffset-- },
                onNext = { monthOffset++ },
                onEdit = onEdit,
                onDelete = { pendingDelete = it },
                selectionMode = selectionMode,
                selectedEntryIds = selectedEntryIds,
                onSelectionChanged = { id, selected ->
                    selectedEntryIds = if (selected) selectedEntryIds + id else selectedEntryIds - id
                },
            )
            1 -> AccountMonthSummary(
                month = month,
                entries = monthEntries,
                entryCount = monthEntries.size,
                summary = monthSummary,
                budgetCents = budgetSettings.budgetForMonth(month),
                analyzingFinancial = analyzingFinancial,
                financialAnalysisPeriodKey = financialAnalysisPeriodKey,
                financialAnalysis = financialAnalysis,
                onPrevious = { monthOffset-- },
                onNext = { monthOffset++ },
                onAnalyzeFinancial = onAnalyzeFinancial,
                onSaveBudget = { onSaveMonthlyBudget(month, it) },
            )
            else -> AccountYearSummary(
                year = selectedYear,
                entries = entries,
                entryCount = yearEntries.size,
                summary = yearSummary,
                budgetCents = budgetSettings.budgetForYear(selectedYear),
                analyzingFinancial = analyzingFinancial,
                financialAnalysisPeriodKey = financialAnalysisPeriodKey,
                financialAnalysis = financialAnalysis,
                onPrevious = { selectedYear-- },
                onNext = { selectedYear++ },
                onAnalyzeFinancial = onAnalyzeFinancial,
                onSaveBudget = { onSaveYearlyBudget(selectedYear, it) },
            )
        }
    }
}

@Composable
private fun AccountLedger(
    month: YearMonth,
    entries: List<AccountEntry>,
    summary: AccountingSummary,
    budgetCents: Long,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onEdit: (AccountEntry) -> Unit,
    onDelete: (AccountEntry) -> Unit,
    selectionMode: Boolean,
    selectedEntryIds: Set<Long>,
    onSelectionChanged: (Long, Boolean) -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        item { MonthSelector(month, onPrevious, onNext) }
        item { AccountSummaryStrip(summary) }
        item { AccountBudgetProgress("本月预算", summary.expenseCents, budgetCents) }
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
                    AccountEntryRow(
                        entry = entry,
                        selectionMode = selectionMode,
                        selected = entry.id in selectedEntryIds,
                        onSelectionChanged = { onSelectionChanged(entry.id, it) },
                        onEdit = { onEdit(entry) },
                        onDelete = { onDelete(entry) },
                    )
                }
            }
        }
    }
}

@Composable
private fun AccountMonthSummary(
    month: YearMonth,
    entries: List<AccountEntry>,
    entryCount: Int,
    summary: AccountingSummary,
    budgetCents: Long,
    analyzingFinancial: Boolean,
    financialAnalysisPeriodKey: String?,
    financialAnalysis: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onAnalyzeFinancial: (String, String, AccountingSummary) -> Unit,
    onSaveBudget: (Long) -> Unit,
) {
    var editingBudget by rememberSaveable(month) { mutableStateOf(false) }
    val trend = remember(entries, month) { accountDailyTotals(entries, month) }
    val analysisKey = "month:$month"
    val periodLabel = month.format(ACCOUNT_MONTH_FORMATTER)
    if (editingBudget) {
        BudgetEditorDialog(
            periodLabel = periodLabel,
            budgetCents = budgetCents,
            onDismiss = { editingBudget = false },
            onSave = {
                onSaveBudget(it)
                editingBudget = false
            },
        )
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { MonthSelector(month, onPrevious, onNext) }
        item { AccountSummaryStrip(summary) }
        item {
            AccountBudgetProgress(
                label = "本月预算",
                spentCents = summary.expenseCents,
                budgetCents = budgetCents,
                onEdit = { editingBudget = true },
            )
        }
        item {
            FinancialAdviceSection(
                periodKey = analysisKey,
                periodLabel = periodLabel,
                summary = summary,
                analysis = financialAnalysis.takeIf { financialAnalysisPeriodKey == analysisKey }.orEmpty(),
                isAnalyzing = analyzingFinancial && financialAnalysisPeriodKey == analysisKey,
                analysisInProgress = analyzingFinancial,
                onAnalyze = onAnalyzeFinancial,
            )
        }
        item { AccountTrendChart("月度收支趋势", trend, "日") }
        item { AccountPieChart("月度支出分类", summary.expenseCategories) }
        item { AccountPieChart("月度收入分类", summary.incomeCategories) }
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
    budgetCents: Long,
    analyzingFinancial: Boolean,
    financialAnalysisPeriodKey: String?,
    financialAnalysis: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onAnalyzeFinancial: (String, String, AccountingSummary) -> Unit,
    onSaveBudget: (Long) -> Unit,
) {
    var editingBudget by rememberSaveable(year) { mutableStateOf(false) }
    val totals = remember(entries, year) { accountMonthTotals(entries, year) }
    val trend = remember(entries, year) { accountYearTrend(entries, year) }
    val analysisKey = "year:$year"
    val periodLabel = "${year}年"
    if (editingBudget) {
        BudgetEditorDialog(
            periodLabel = periodLabel,
            budgetCents = budgetCents,
            onDismiss = { editingBudget = false },
            onSave = {
                onSaveBudget(it)
                editingBudget = false
            },
        )
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        item { YearSelector(year, onPrevious, onNext) }
        item { AccountSummaryStrip(summary) }
        item {
            AccountBudgetProgress(
                label = "年度预算",
                spentCents = summary.expenseCents,
                budgetCents = budgetCents,
                onEdit = { editingBudget = true },
            )
        }
        item {
            FinancialAdviceSection(
                periodKey = analysisKey,
                periodLabel = periodLabel,
                summary = summary,
                analysis = financialAnalysis.takeIf { financialAnalysisPeriodKey == analysisKey }.orEmpty(),
                isAnalyzing = analyzingFinancial && financialAnalysisPeriodKey == analysisKey,
                analysisInProgress = analyzingFinancial,
                onAnalyze = onAnalyzeFinancial,
            )
        }
        item { AccountTrendChart("年度收支趋势", trend, "月") }
        item { AccountPieChart("年度支出分类", summary.expenseCategories) }
        item { AccountPieChart("年度收入分类", summary.incomeCategories) }
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
private fun FinancialAdviceSection(
    periodKey: String,
    periodLabel: String,
    summary: AccountingSummary,
    analysis: String,
    isAnalyzing: Boolean,
    analysisInProgress: Boolean,
    onAnalyze: (String, String, AccountingSummary) -> Unit,
) {
    val hasData = summary.incomeCents != 0L || summary.expenseCents != 0L
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "AI 消费建议",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "分析收入、支出分类与结余",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(
                onClick = { onAnalyze(periodKey, periodLabel, summary) },
                enabled = hasData && !analysisInProgress,
            ) {
                if (isAnalyzing) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Outlined.AutoAwesome, contentDescription = null)
                }
                Spacer(Modifier.width(6.dp))
                Text(
                    when {
                        isAnalyzing -> "分析中…"
                        analysis.isNotBlank() -> "重新分析"
                        else -> "开始分析"
                    },
                )
            }
        }
        if (analysis.isNotBlank() || isAnalyzing) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Text(
                    text = analysis.ifBlank { "正在生成建议…" },
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun AccountTrendChart(
    title: String,
    points: List<AccountTrendPoint>,
    xAxisSuffix: String,
) {
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val axisColor = MaterialTheme.colorScheme.outline
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val chartWidth = maxOf(360.dp, (points.size * 46).dp)
    val chartScrollState = rememberScrollState()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            ChartLegend(ExpenseColor, "支出")
            ChartLegend(ChartIncomeColor, "收入")
            ChartLegend(ChartBalanceColor, "结余")
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Box(Modifier.fillMaxWidth().horizontalScroll(chartScrollState)) {
                Canvas(Modifier.width(chartWidth).height(220.dp).padding(8.dp)) {
                    if (points.isEmpty()) return@Canvas

                    val values = points.flatMap { point ->
                        listOf(point.incomeCents.toDouble(), point.expenseCents.toDouble(), point.balanceCents.toDouble())
                    }
                    val minValue = minOf(0.0, values.minOrNull() ?: 0.0)
                    val maxValue = maxOf(0.0, values.maxOrNull() ?: 0.0)
                    val valueSpan = (maxValue - minValue).coerceAtLeast(1.0)
                    val left = 42.dp.toPx()
                    val top = 10.dp.toPx()
                    val right = size.width - 8.dp.toPx()
                    val bottom = size.height - 30.dp.toPx()
                    val plotWidth = (right - left).coerceAtLeast(1f)
                    val plotHeight = (bottom - top).coerceAtLeast(1f)
                    val yFor: (Double) -> Float = { value ->
                        (bottom - ((value - minValue) / valueSpan * plotHeight).toFloat()).coerceIn(top, bottom)
                    }
                    val xFor: (Int) -> Float = { index ->
                        if (points.size == 1) left else left + plotWidth * index / (points.lastIndex.toFloat())
                    }
                    val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = labelColor.toArgb()
                        textSize = 10.dp.toPx()
                    }
                    (0..4).forEach { index ->
                        val value = minValue + valueSpan * index / 4.0
                        val y = yFor(value)
                        drawLine(gridColor, Offset(left, y), Offset(right, y), strokeWidth = 1f)
                        drawIntoCanvas { canvas ->
                            canvas.nativeCanvas.drawText(compactChartAmount(value), 0f, y + 4.dp.toPx(), labelPaint)
                        }
                    }
                    drawLine(axisColor, Offset(left, top), Offset(left, bottom), strokeWidth = 1.5f)
                    drawLine(axisColor, Offset(left, bottom), Offset(right, bottom), strokeWidth = 1.5f)

                    fun pathFor(value: (AccountTrendPoint) -> Long): Path {
                        return Path().apply {
                            points.forEachIndexed { index, point ->
                                val position = Offset(xFor(index), yFor(value(point).toDouble()))
                                if (index == 0) moveTo(position.x, position.y) else lineTo(position.x, position.y)
                            }
                        }
                    }
                    drawPath(pathFor(AccountTrendPoint::expenseCents), ExpenseColor, style = Stroke(width = 2.dp.toPx()))
                    drawPath(pathFor(AccountTrendPoint::incomeCents), ChartIncomeColor, style = Stroke(width = 2.dp.toPx()))
                    drawPath(pathFor(AccountTrendPoint::balanceCents), ChartBalanceColor, style = Stroke(width = 2.dp.toPx()))

                    points.forEachIndexed { index, point ->
                        val x = xFor(index)
                        val paint = Paint(labelPaint).apply { textAlign = Paint.Align.CENTER }
                        drawLine(gridColor, Offset(x, bottom), Offset(x, bottom + 4.dp.toPx()), strokeWidth = 1f)
                        drawIntoCanvas { canvas ->
                            canvas.nativeCanvas.drawText("${point.label}$xAxisSuffix", x, size.height - 7.dp.toPx(), paint)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChartLegend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(Modifier.size(9.dp).background(color, CircleShape))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun AccountPieChart(title: String, categories: List<AccountCategoryTotal>) {
    val slices = categories.take(10)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        if (slices.isEmpty()) {
            Text("暂无数据", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            val totalCents = slices.sumOf(AccountCategoryTotal::amountCents).coerceAtLeast(1L)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Canvas(Modifier.fillMaxWidth().height(180.dp).padding(10.dp)) {
                    val diameter = minOf(size.width, size.height) * 0.78f
                    val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
                    var startAngle = -90f
                    slices.forEachIndexed { index, category ->
                        val sweep = category.amountCents.toFloat() / totalCents.toFloat() * 360f
                        drawArc(
                            color = PieColors[index % PieColors.size],
                            startAngle = startAngle,
                            sweepAngle = sweep,
                            useCenter = true,
                            topLeft = topLeft,
                            size = Size(diameter, diameter),
                        )
                        startAngle += sweep
                    }
                }
            }
            slices.forEachIndexed { index, category ->
                val percentage = (category.amountCents.toDouble() * 1000 / totalCents).roundToLong() / 10.0
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).background(PieColors[index % PieColors.size], CircleShape))
                    Spacer(Modifier.width(8.dp))
                    Text(category.category, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("$percentage%", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(12.dp))
                    Text(formatMoney(category.amountCents), fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

private fun compactChartAmount(cents: Double): String {
    val amount = cents / 100.0
    return when {
        abs(amount) >= 10_000 -> "${(amount / 10_000).roundToLong()}万"
        abs(amount) >= 1_000 -> "${(amount / 1_000).roundToLong()}千"
        else -> amount.roundToLong().toString()
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
private fun BudgetEditorDialog(
    periodLabel: String,
    budgetCents: Long,
    onDismiss: () -> Unit,
    onSave: (Long) -> Unit,
) {
    var amount by remember(periodLabel, budgetCents) { mutableStateOf(budgetAmountInput(budgetCents)) }
    val parsedAmount = remember(amount) { parseBudgetAmountCents(amount) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("设置${periodLabel}预算") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "预算只统计支出。该金额仅用于 $periodLabel，切换到其他月份或年份后可以单独设置。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = sanitizeBudgetAmountInput(it) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("预算金额（元）") },
                    placeholder = { Text("例如：3000") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    isError = parsedAmount == null,
                    supportingText = if (parsedAmount == null) ({ Text("请输入有效金额") }) else null,
                )
                Text("留空并保存可清除当前周期预算。", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(requireNotNull(parsedAmount)) },
                enabled = parsedAmount != null,
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun AccountBudgetProgress(
    label: String,
    spentCents: Long,
    budgetCents: Long,
    onEdit: (() -> Unit)? = null,
) {
    val hasBudget = budgetCents > 0L
    val rawProgress = if (hasBudget) spentCents.toDouble() / budgetCents.toDouble() else 0.0
    val overBudget = hasBudget && spentCents > budgetCents
    val progressColor = if (overBudget) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(label, Modifier.weight(1f), fontWeight = FontWeight.Bold)
                if (onEdit != null) {
                    TextButton(onClick = onEdit) {
                        Icon(Icons.Outlined.Edit, null, Modifier.size(17.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (hasBudget) "修改" else "设置")
                    }
                }
                Text(
                    if (hasBudget) "${(rawProgress * 100).roundToLong()}%" else "未设置",
                    color = progressColor,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            LinearProgressIndicator(
                progress = { rawProgress.toFloat().coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = progressColor,
                trackColor = MaterialTheme.colorScheme.surface,
            )
            Text(
                when {
                    !hasBudget -> if (onEdit == null) "可在月总结中设置本月预算" else "尚未设置当前周期预算"
                    overBudget -> "已支出 ${formatMoney(spentCents)}，超出 ${formatMoney(spentCents - budgetCents)}"
                    else -> "已支出 ${formatMoney(spentCents)}，剩余 ${formatMoney(budgetCents - spentCents)}"
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (overBudget) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun budgetAmountInput(cents: Long): String = if (cents <= 0L) "" else
    BigDecimal.valueOf(cents).movePointLeft(2).stripTrailingZeros().toPlainString()

private fun parseBudgetAmountCents(value: String): Long? {
    if (value.isBlank()) return 0L
    val amount = value.toBigDecimalOrNull() ?: return null
    if (amount < BigDecimal.ZERO) return null
    return runCatching {
        amount.setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact()
    }.getOrNull()
}

private fun sanitizeBudgetAmountInput(value: String): String {
    val filtered = value.filter { it.isDigit() || it == '.' }
    val dot = filtered.indexOf('.')
    if (dot < 0) return filtered.take(12)
    return filtered.take(dot + 1) + filtered.drop(dot + 1).filter(Char::isDigit).take(2)
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
private fun AccountEntryRow(
    entry: AccountEntry,
    selectionMode: Boolean,
    selected: Boolean,
    onSelectionChanged: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val color = if (entry.type == AccountEntryType.INCOME) IncomeColor else ExpenseColor
    val prefix = if (entry.type == AccountEntryType.INCOME) "+" else "-"
    Surface(
        Modifier.fillMaxWidth().clickable {
            if (selectionMode) onSelectionChanged(!selected) else onEdit()
        },
        shape = RoundedCornerShape(6.dp),
        tonalElevation = 1.dp,
    ) {
        Row(Modifier.padding(start = 7.dp, top = 9.dp, bottom = 9.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (selectionMode) {
                Checkbox(checked = selected, onCheckedChange = onSelectionChanged)
            }
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
            if (!selectionMode) {
                IconButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, "编辑流水") }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Outlined.DeleteOutline, "删除流水", tint = MaterialTheme.colorScheme.error)
                }
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
@OptIn(ExperimentalLayoutApi::class)
fun AccountEntryEditorScreen(
    contentPadding: PaddingValues,
    initial: AccountEntry,
    customExpenseCategories: Set<String>,
    customIncomeCategories: Set<String>,
    classifyingAccount: Boolean,
    accountClassificationStatus: String,
    savingAccountEntries: Boolean,
    onCancel: () -> Unit,
    onAddCustomCategory: (AccountEntryType, String) -> Unit,
    onDeleteCustomCategory: (AccountEntryType, String) -> Unit,
    onClassifyAccount: (
        String,
        List<String>,
        List<String>,
        Long,
        () -> Unit,
        (List<AccountClassificationResult>) -> Unit,
    ) -> Unit,
    onClassifyAccountImages: (
        List<Pair<ByteArray, String>>,
        List<String>,
        List<String>,
        Long,
        () -> Unit,
        (List<AccountClassificationResult>) -> Unit,
        (String) -> Unit,
    ) -> Unit,
    onSaveEntries: (List<AccountEntry>, () -> Unit) -> Unit,
    onFinished: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val initialDateTime = remember(initial) {
        Instant.ofEpochMilli(initial.occurredAt).atZone(ZoneId.systemDefault()).toLocalDateTime()
    }
    var type by remember(initial) { mutableStateOf(initial.type) }
    var amount by remember(initial) { mutableStateOf(amountInput(initial.amountCents)) }
    var category by remember(initial) { mutableStateOf(initial.category) }
    var note by remember(initial) { mutableStateOf(initial.note) }
    var date by remember(initial) { mutableStateOf(initialDateTime.toLocalDate()) }
    var time by remember(initial) { mutableStateOf(initialDateTime.toLocalTime().withSecond(0).withNano(0)) }
    var showCustomCategoryDialog by remember { mutableStateOf(false) }
    var customCategoryName by remember { mutableStateOf("") }
    var accountDescription by remember(initial) { mutableStateOf("") }
    var recognitionMode by remember(initial) { mutableStateOf("text") }
    var accountImageStatus by remember(initial) { mutableStateOf("") }
    var preparingAccountImages by remember { mutableStateOf(false) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    var pendingDeleteCustomCategory by remember { mutableStateOf<String?>(null) }
    var reviewItems by remember(initial) { mutableStateOf<List<AccountClassificationResult>>(emptyList()) }
    var reviewedEntries by remember(initial) { mutableStateOf<List<AccountEntry>>(emptyList()) }
    var reviewIndex by remember(initial) { mutableIntStateOf(0) }
    var showReviewHint by remember { mutableStateOf(false) }
    val builtInCategories = if (type == AccountEntryType.EXPENSE) EXPENSE_CATEGORY_OPTIONS else INCOME_CATEGORY_OPTIONS
    val customCategories = if (type == AccountEntryType.EXPENSE) customExpenseCategories else customIncomeCategories
    val expenseCategoryNames = (EXPENSE_CATEGORY_OPTIONS.map(AccountCategoryOption::name) + customExpenseCategories.sorted()).distinct()
    val incomeCategoryNames = (INCOME_CATEGORY_OPTIONS.map(AccountCategoryOption::name) + customIncomeCategories.sorted()).distinct()
    val categories = remember(type, customCategories, category) {
        buildList {
            addAll(builtInCategories)
            customCategories.sorted().forEach { name ->
                if (none { it.name == name }) add(AccountCategoryOption(name, Icons.AutoMirrored.Outlined.Label, isCustom = true))
            }
            if (category.isNotBlank() && none { it.name == category }) {
                add(AccountCategoryOption(category, Icons.AutoMirrored.Outlined.Label))
            }
        }
    }
    val normalizedCustomCategory = customCategoryName.trim()
    val customCategoryExists = categories.any { it.name.equals(normalizedCustomCategory, ignoreCase = true) }
    val amountCents = remember(amount) { parseAmountToCents(amount) }
    val currentReviewItem = reviewItems.getOrNull(reviewIndex)
    val isReviewing = reviewItems.isNotEmpty()
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

    fun loadReviewItem(result: AccountClassificationResult) {
        type = result.type ?: AccountEntryType.EXPENSE
        amount = result.amountCents?.let(::amountInput).orEmpty()
        category = result.category.orEmpty()
        note = result.note.orEmpty()
    }

    fun beginReview(items: List<AccountClassificationResult>) {
        if (items.isEmpty()) return
        reviewItems = items
        reviewedEntries = emptyList()
        reviewIndex = 0
        loadReviewItem(items.first())
        accountDescription = ""
        accountImageStatus = ""
        showReviewHint = true
    }

    val processAccountImageUris: (List<Uri>) -> Unit = { uris ->
        val selected = uris.take(MAX_AI_IMAGE_COUNT)
        if (selected.isEmpty()) {
            accountImageStatus = "尚未选择图片"
        } else {
            preparingAccountImages = true
            accountImageStatus = "正在读取 1/${selected.size} 张图片…"
            scope.launch {
                val images = mutableListOf<Pair<ByteArray, String>>()
                val failures = mutableListOf<String>()
                selected.forEachIndexed { index, uri ->
                    accountImageStatus = "正在读取第 ${index + 1}/${selected.size} 张图片…"
                    runCatching {
                        withContext(Dispatchers.IO) {
                            prepareAiImage(context, uri, ACCOUNT_DOCUMENT_IMAGE_PROFILE)
                        }
                    }.onSuccess { images.add(it) }.onFailure { error ->
                        failures += error.message ?: "第 ${index + 1} 张图片无法读取"
                    }
                }
                preparingAccountImages = false
                if (failures.isNotEmpty()) {
                    accountImageStatus =
                        "有 ${failures.size} 张图片无法读取，尚未开始识别，请重新选择"
                } else {
                    accountImageStatus = "已读取 ${images.size} 张图片，正在提交识别…"
                    val occurredAt = date.atTime(time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    onClassifyAccountImages(
                        images,
                        expenseCategoryNames,
                        incomeCategoryNames,
                        occurredAt,
                        onFinished,
                        ::beginReview,
                        { message -> accountImageStatus = "识别失败：$message" },
                    )
                }
            }
        }
    }
    val accountImagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(MAX_AI_IMAGE_COUNT)
    ) { uris -> processAccountImageUris(uris) }
    val accountCameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { captured ->
        val uri = pendingCameraUri
        pendingCameraUri = null
        if (captured && uri != null) {
            processAccountImageUris(listOf(uri))
        } else if (uri != null) {
            runCatching { context.contentResolver.delete(uri, null, null) }
            accountImageStatus = "已取消拍照"
        }
    }

    pendingDeleteCustomCategory?.let { categoryName ->
        AlertDialog(
            onDismissRequest = { pendingDeleteCustomCategory = null },
            title = { Text("删除自定义分类") },
            text = { Text("确定删除“$categoryName”吗？已有账单不会被删除。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteCustomCategory(type, categoryName)
                        if (category == categoryName) category = builtInCategories.first().name
                        pendingDeleteCustomCategory = null
                    },
                ) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { pendingDeleteCustomCategory = null }) { Text("取消") } },
        )
    }

    if (showReviewHint) {
        AlertDialog(
            onDismissRequest = { showReviewHint = false },
            title = { Text("需要逐条检查") },
            text = {
                Text("识别到 ${reviewItems.size} 笔账目，但至少一笔存在无法确定的字段，因此尚未写入。请逐条核对并补全，最后统一保存。")
            },
            confirmButton = { TextButton(onClick = { showReviewHint = false }) { Text("开始检查") } },
        )
    }

    if (showCustomCategoryDialog) {
        AlertDialog(
            onDismissRequest = {
                showCustomCategoryDialog = false
                customCategoryName = ""
            },
            title = { Text("添加自定义分类") },
            text = {
                OutlinedTextField(
                    value = customCategoryName,
                    onValueChange = { customCategoryName = it.take(20) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("分类名称") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Outlined.Label, null) },
                    supportingText = if (customCategoryExists && normalizedCustomCategory.isNotBlank()) {
                        { Text("这个分类已经存在") }
                    } else null,
                    isError = customCategoryExists && normalizedCustomCategory.isNotBlank(),
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onAddCustomCategory(type, normalizedCustomCategory)
                        category = normalizedCustomCategory
                        customCategoryName = ""
                        showCustomCategoryDialog = false
                    },
                    enabled = normalizedCustomCategory.isNotBlank() && !customCategoryExists,
                ) { Text("添加") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showCustomCategoryDialog = false
                        customCategoryName = ""
                    },
                ) { Text("取消") }
            },
        )
    }

    Column(
        Modifier.fillMaxSize().padding(contentPadding).verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        Text(
            when {
                isReviewing -> "检查第 ${reviewIndex + 1}/${reviewItems.size} 笔"
                initial.id == 0L -> "记一笔"
                else -> "编辑流水"
            },
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        currentReviewItem?.let { reviewItem ->
            val missingFields = reviewItem.missingFields()
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        if (missingFields.isEmpty()) "该笔字段完整，请核对后继续" else "模型未能确定：${missingFields.joinToString("、")}",
                        fontWeight = FontWeight.SemiBold,
                    )
                    reviewItem.suggestedCategory?.takeIf(String::isNotBlank)?.let {
                        Text("建议分类：$it", color = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AccountEntryType.entries.forEach { option ->
                FilterChip(
                    selected = type == option,
                    onClick = {
                        type = option
                        val newBuiltIns = if (option == AccountEntryType.EXPENSE) EXPENSE_CATEGORY_OPTIONS else INCOME_CATEGORY_OPTIONS
                        val newCustomCategories = if (option == AccountEntryType.EXPENSE) customExpenseCategories else customIncomeCategories
                        if (category !in newBuiltIns.map { it.name } && category !in newCustomCategories) {
                            category = newBuiltIns.first().name
                        }
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
        if (initial.id == 0L && !isReviewing) {
            Text("智能记账", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = recognitionMode == "text",
                    onClick = { recognitionMode = "text" },
                    label = { Text("一句话识别") },
                    enabled = !classifyingAccount && !preparingAccountImages,
                    modifier = Modifier.weight(1f),
                )
                FilterChip(
                    selected = recognitionMode == "image",
                    onClick = { recognitionMode = "image" },
                    label = { Text("图片识别") },
                    enabled = !classifyingAccount && !preparingAccountImages,
                    modifier = Modifier.weight(1f),
                )
            }
            if (recognitionMode == "text") {
                OutlinedTextField(
                    value = accountDescription,
                    onValueChange = { accountDescription = it.take(1000) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("描述一条或多条收入、支出") },
                    placeholder = { Text("例如：买菜 32 元，打车 18 元，工资入账 2 万") },
                    minLines = 3,
                    maxLines = 6,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    OutlinedButton(
                        onClick = {
                            val occurredAt = date.atTime(time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                            onClassifyAccount(
                                accountDescription,
                                expenseCategoryNames,
                                incomeCategoryNames,
                                occurredAt,
                                onFinished,
                                ::beginReview,
                            )
                        },
                        enabled = accountDescription.isNotBlank() && !classifyingAccount,
                    ) {
                        if (classifyingAccount) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Outlined.AutoAwesome, null)
                        }
                        Spacer(Modifier.width(6.dp))
                        Text(if (classifyingAccount) "识别中…" else "识别并分类")
                    }
                }
            } else {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Column(
                        Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            Icons.Outlined.Image,
                            contentDescription = null,
                            modifier = Modifier.size(38.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            accountImageStatus.ifBlank { "尚未选择购物清单或票据图片" },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    accountImagePicker.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                enabled = !preparingAccountImages && !classifyingAccount,
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 8.dp),
                            ) {
                                if (preparingAccountImages) {
                                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Outlined.Image, null)
                                }
                                Spacer(Modifier.width(6.dp))
                                Text(if (preparingAccountImages) "读取中…" else "相册（最多4张）")
                            }
                            OutlinedButton(
                                onClick = {
                                    runCatching { createCameraImageUri(context) }
                                        .onSuccess { uri ->
                                            pendingCameraUri = uri
                                            accountCameraLauncher.launch(uri)
                                        }
                                        .onFailure { accountImageStatus = it.message ?: "无法打开相机" }
                                },
                                enabled = !preparingAccountImages && !classifyingAccount,
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 8.dp),
                            ) {
                                Icon(Icons.Outlined.PhotoCamera, null)
                                Spacer(Modifier.width(6.dp))
                                Text("拍照")
                            }
                        }
                    }
                }
            }
            if (classifyingAccount) {
                Text(
                    accountClassificationStatus.ifBlank { "正在识别并匹配分类…" },
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        Text("分类", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val itemWidth = (maxWidth - 24.dp) / 4
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                maxItemsInEachRow = 4,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
            categories.forEach { option ->
                    AccountCategoryTile(
                        option = option,
                        selected = category == option.name,
                        onClick = { category = option.name },
                        isCustom = option.isCustom,
                        onDelete = { pendingDeleteCustomCategory = option.name },
                        modifier = Modifier.width(itemWidth),
                    )
                }
                AccountCategoryTile(
                    option = AccountCategoryOption("自定义", Icons.Outlined.Add),
                    selected = false,
                    onClick = { showCustomCategoryDialog = true },
                    isCustom = false,
                    onDelete = null,
                    modifier = Modifier.width(itemWidth),
                )
            }
        }
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
            label = { Text(if (isReviewing) "备注（必填）" else "备注（选填）") },
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)) {
            TextButton(onClick = onCancel) { Text(if (isReviewing) "取消整批" else "取消") }
            Button(
                onClick = {
                    val occurredAt = date.atTime(time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    val entry = initial.copy(
                        id = if (isReviewing) 0L else initial.id,
                        type = type,
                        amountCents = requireNotNull(amountCents),
                        category = category.trim(),
                        note = note.trim(),
                        occurredAt = occurredAt,
                        createdAt = if (isReviewing) System.currentTimeMillis() + reviewIndex else initial.createdAt,
                    )
                    if (isReviewing && reviewIndex < reviewItems.lastIndex) {
                        reviewedEntries = reviewedEntries + entry
                        reviewIndex++
                        loadReviewItem(reviewItems[reviewIndex])
                    } else {
                        val entriesToSave = if (isReviewing) reviewedEntries + entry else listOf(entry)
                        onSaveEntries(entriesToSave, onFinished)
                    }
                },
                enabled = !savingAccountEntries && amountCents != null && category.isNotBlank() && (!isReviewing || note.isNotBlank()),
            ) {
                if (savingAccountEntries) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(6.dp))
                    Text("保存中…")
                } else {
                    Text(
                        when {
                            !isReviewing -> "保存"
                            reviewIndex < reviewItems.lastIndex -> "确认并检查下一笔"
                            else -> "保存全部"
                        }
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

private fun AccountClassificationResult.missingFields(): List<String> = buildList {
    if (type == null) add("收入/支出")
    if (amountCents == null || amountCents <= 0) add("金额")
    if (category.isNullOrBlank()) add("分类")
    if (note.isNullOrBlank()) add("备注")
}

@Composable
private fun AccountCategoryTile(
    option: AccountCategoryOption,
    selected: Boolean,
    onClick: () -> Unit,
    isCustom: Boolean,
    onDelete: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    val containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
    val contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    Box(modifier.height(66.dp)) {
        Surface(
            modifier = Modifier.fillMaxSize().clickable(onClick = onClick),
            shape = RoundedCornerShape(8.dp),
            color = containerColor,
            contentColor = contentColor,
            border = BorderStroke(if (selected) 2.dp else 1.dp, borderColor),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 3.dp, vertical = 7.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(option.icon, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.height(3.dp))
                Text(
                    text = option.name,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (isCustom && onDelete != null) {
            IconButton(
                onClick = onDelete,
                modifier = Modifier.align(Alignment.TopEnd).size(25.dp),
            ) {
                Icon(Icons.Outlined.DeleteOutline, "删除自定义分类", modifier = Modifier.size(15.dp))
            }
        }
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

private data class AccountCategoryOption(
    val name: String,
    val icon: ImageVector,
    val isCustom: Boolean = false,
)

private val EXPENSE_CATEGORY_OPTIONS = listOf(
    AccountCategoryOption("餐饮", Icons.Outlined.Restaurant),
    AccountCategoryOption("交通", Icons.Outlined.DirectionsCar),
    AccountCategoryOption("购物", Icons.Outlined.ShoppingBag),
    AccountCategoryOption("住房", Icons.Outlined.Home),
    AccountCategoryOption("水电燃气", Icons.Outlined.Bolt),
    AccountCategoryOption("通讯网络", Icons.Outlined.Wifi),
    AccountCategoryOption("医疗健康", Icons.Outlined.LocalHospital),
    AccountCategoryOption("教育培训", Icons.Outlined.School),
    AccountCategoryOption("娱乐", Icons.Outlined.Movie),
    AccountCategoryOption("旅行", Icons.Outlined.Flight),
    AccountCategoryOption("人情礼金", Icons.Outlined.CardGiftcard),
    AccountCategoryOption("家庭育儿", Icons.Outlined.ChildCare),
    AccountCategoryOption("宠物", Icons.Outlined.Pets),
    AccountCategoryOption("美容服饰", Icons.Outlined.Checkroom),
    AccountCategoryOption("运动健身", Icons.Outlined.FitnessCenter),
    AccountCategoryOption("保险", Icons.Outlined.Security),
    AccountCategoryOption("税费", Icons.AutoMirrored.Outlined.ReceiptLong),
    AccountCategoryOption("订阅服务", Icons.Outlined.Subscriptions),
    AccountCategoryOption("办公", Icons.Outlined.BusinessCenter),
    AccountCategoryOption("维修", Icons.Outlined.Build),
    AccountCategoryOption("公益捐赠", Icons.Outlined.VolunteerActivism),
)

private val INCOME_CATEGORY_OPTIONS = listOf(
    AccountCategoryOption("工资", Icons.Outlined.Payments),
    AccountCategoryOption("奖金", Icons.Outlined.EmojiEvents),
    AccountCategoryOption("兼职副业", Icons.Outlined.WorkOutline),
    AccountCategoryOption("经营收入", Icons.Outlined.Storefront),
    AccountCategoryOption("理财收益", Icons.AutoMirrored.Outlined.TrendingUp),
    AccountCategoryOption("利息", Icons.Outlined.Percent),
    AccountCategoryOption("报销", Icons.AutoMirrored.Outlined.ReceiptLong),
    AccountCategoryOption("退款", Icons.Outlined.Replay),
    AccountCategoryOption("租金", Icons.Outlined.HomeWork),
    AccountCategoryOption("礼金红包", Icons.Outlined.CardGiftcard),
    AccountCategoryOption("补贴", Icons.Outlined.Savings),
    AccountCategoryOption("出售闲置", Icons.Outlined.Sell),
    AccountCategoryOption("借款入账", Icons.Outlined.AccountBalance),
)
private val ACCOUNT_MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy年M月")
private val ACCOUNT_DAY_FORMATTER = DateTimeFormatter.ofPattern("M月d日 EEEE")
private val ACCOUNT_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy年M月d日")
private val ACCOUNT_TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm")
