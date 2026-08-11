package cn.aimemo.mobile.ui

import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.EventNote
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import cn.aimemo.mobile.data.Schedule
import cn.aimemo.mobile.data.AccountEntry
import cn.aimemo.mobile.data.AccountEntryType

private enum class AppSection(val label: String) {
    SCHEDULES("日程"), ACCOUNTING("记账"), SETTINGS("设置")
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun AiMemoApp(viewModel: AppViewModel, state: AppUiState) {
    var section by rememberSaveable { mutableStateOf(AppSection.SCHEDULES) }
    var editingSchedule by remember { mutableStateOf<Schedule?>(null) }
    var editingAccountEntry by remember { mutableStateOf<AccountEntry?>(null) }
    var editingRecognizedIndex by remember { mutableIntStateOf(-1) }
    var showingSmartAdd by rememberSaveable { mutableStateOf(false) }
    var smartAddMode by rememberSaveable { mutableStateOf("text") }
    var showReminderLogs by rememberSaveable { mutableStateOf(false) }

    val openReminderLogs: () -> Unit = {
        section = AppSection.SCHEDULES
        editingSchedule = null
        editingAccountEntry = null
        editingRecognizedIndex = -1
        showingSmartAdd = false
        showReminderLogs = true
        viewModel.markReminderLogsSeen()
    }
    val openAiSettings: () -> Unit = {
        viewModel.consumeNotice()
        editingSchedule = null
        editingAccountEntry = null
        editingRecognizedIndex = -1
        showingSmartAdd = false
        showReminderLogs = false
        section = AppSection.SETTINGS
    }
    BackHandler(
        enabled = editingSchedule != null || editingAccountEntry != null ||
            editingRecognizedIndex >= 0 || showingSmartAdd,
    ) {
        when {
            editingSchedule != null -> {
                editingSchedule = null
                showingSmartAdd = false
                section = AppSection.SCHEDULES
            }
            editingAccountEntry != null -> {
                editingAccountEntry = null
                section = AppSection.ACCOUNTING
            }
            editingRecognizedIndex >= 0 -> editingRecognizedIndex = -1
            showingSmartAdd -> showingSmartAdd = false
        }
    }
    val reminderListVisible = section == AppSection.SCHEDULES &&
        showReminderLogs && !showingSmartAdd && editingSchedule == null && editingRecognizedIndex < 0
    LaunchedEffect(reminderListVisible, state.unreadReminderCount) {
        if (reminderListVisible && state.unreadReminderCount > 0) {
            viewModel.markReminderLogsSeen()
        }
    }

    val apiKeyRequired = state.error?.contains("API Key", ignoreCase = true) == true

    if (apiKeyRequired) {
        AlertDialog(
            onDismissRequest = viewModel::consumeNotice,
            title = { Text("使用 AI 前还差一步") },
            text = {
                Text("AI 功能需要先填写智谱 API Key。暂时不使用 AI 时，日程和记账的手动功能仍可正常使用。")
            },
            confirmButton = {
                TextButton(
                    onClick = openAiSettings,
                ) { Text("去填写 API Key") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::consumeNotice) { Text("暂不使用") }
            },
        )
    } else if (state.suggestModelSwitch) {
        AlertDialog(
            onDismissRequest = viewModel::consumeNotice,
            title = { Text("模型连接超时") },
            text = { Text("当前模型暂时没有响应。是否前往模型配置页切换模型？也可以稍后再试。") },
            confirmButton = {
                TextButton(
                    onClick = openAiSettings,
                ) { Text("去切换模型") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::consumeNotice) { Text("暂不切换") }
            },
        )
    } else (state.error ?: state.message)?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::consumeNotice,
            title = { Text(if (state.error != null) "暂时未能完成" else "提示") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = viewModel::consumeNotice) { Text("知道了") } },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI备忘录") },
                actions = {
                    IconButton(onClick = openReminderLogs) {
                        BadgedBox(
                            badge = { UnreadReminderBadge(state.unreadReminderCount) },
                        ) {
                            Icon(Icons.Outlined.NotificationsNone, "查看已提醒日程")
                        }
                    }
                },
            )
        },
        bottomBar = {
            BottomAppBar {
                listOf(
                    Triple(AppSection.SCHEDULES, Icons.AutoMirrored.Outlined.EventNote, "日程"),
                    Triple(AppSection.ACCOUNTING, Icons.Outlined.AccountBalanceWallet, "记账"),
                    Triple(AppSection.SETTINGS, Icons.Outlined.Settings, "设置"),
                ).forEach { (target, icon, description) ->
                    NavigationBarItem(
                        selected = section == target,
                        onClick = {
                            section = target
                            editingSchedule = null
                            editingAccountEntry = null
                            editingRecognizedIndex = -1
                            showingSmartAdd = false
                        },
                        icon = {
                            BadgedBox(
                                badge = {
                                    if (target == AppSection.SCHEDULES) {
                                        UnreadReminderBadge(state.unreadReminderCount)
                                    }
                                },
                            ) {
                                Icon(icon, description)
                            }
                        },
                        label = { Text(target.label) },
                    )
                }
            }
        }
    ) { padding ->
        when {
            editingSchedule != null -> ScheduleEditorScreen(
                modifier = Modifier,
                contentPadding = padding,
                initial = editingSchedule,
                onCancel = {
                    editingSchedule = null
                    showingSmartAdd = false
                },
                onSave = { schedule ->
                    viewModel.save(schedule) {
                        editingSchedule = null
                        showingSmartAdd = false
                        section = AppSection.SCHEDULES
                    }
                },
            )
            editingRecognizedIndex >= 0 -> ScheduleEditorScreen(
                modifier = Modifier,
                contentPadding = padding,
                initial = state.recognized.getOrNull(editingRecognizedIndex),
                onCancel = { editingRecognizedIndex = -1 },
                onSave = {
                    viewModel.updateRecognized(editingRecognizedIndex, it)
                    editingRecognizedIndex = -1
                },
            )
            editingAccountEntry != null -> AccountEntryEditorScreen(
                contentPadding = padding,
                initial = requireNotNull(editingAccountEntry),
                customExpenseCategories = state.customExpenseCategories,
                customIncomeCategories = state.customIncomeCategories,
                classifyingAccount = state.classifyingAccount,
                accountClassificationStatus = state.accountClassificationStatus,
                savingAccountEntries = state.savingAccountEntries,
                onCancel = { editingAccountEntry = null },
                onAddCustomCategory = viewModel::addCustomAccountCategory,
                onDeleteCustomCategory = viewModel::deleteCustomAccountCategory,
                onClassifyAccount = viewModel::classifyAccountText,
                onClassifyAccountImages = viewModel::classifyAccountImages,
                onSaveEntries = viewModel::saveAccountEntries,
                onFinished = {
                    editingAccountEntry = null
                    section = AppSection.ACCOUNTING
                },
            )
            showingSmartAdd -> SmartAddScreen(
                contentPadding = padding,
                state = state,
                initialMode = smartAddMode,
                onBack = { showingSmartAdd = false },
                onRecognizeText = viewModel::recognizeText,
                onRecognizeImages = viewModel::recognizeImages,
                onManualAdd = { editingSchedule = Schedule(title = "") },
                onEditResult = { editingRecognizedIndex = it },
                onRemoveResult = viewModel::removeRecognized,
                onSaveAll = {
                    viewModel.saveRecognized {
                        showingSmartAdd = false
                        section = AppSection.SCHEDULES
                    }
                },
            )
            section == AppSection.SCHEDULES -> ScheduleListScreen(
                contentPadding = padding,
                state = state,
                onTextRecognition = {
                    smartAddMode = "text"
                    showingSmartAdd = true
                },
                onImageRecognition = {
                    smartAddMode = "image"
                    showingSmartAdd = true
                },
                onManualAdd = { editingSchedule = Schedule(title = "") },
                onEdit = { editingSchedule = it },
                onCompleted = viewModel::setCompleted,
                onDelete = viewModel::delete,
                onBatchCompleted = viewModel::setSchedulesCompleted,
                onBatchDelete = viewModel::deleteSchedules,
                onImport = viewModel::importBackup,
                onDeleteReminderLog = viewModel::deleteReminderLog,
                showReminderLogs = showReminderLogs,
                onShowReminderLogsChange = { visible ->
                    showReminderLogs = visible
                    if (visible) viewModel.markReminderLogsSeen()
                },
            )
            section == AppSection.ACCOUNTING -> AccountingScreen(
                contentPadding = padding,
                entries = state.accountEntries,
                budgetSettings = state.budgetSettings,
                analyzingFinancial = state.analyzingFinancial,
                financialAnalysisPeriodKey = state.financialAnalysisPeriodKey,
                financialAnalysis = state.financialAnalysis,
                onAdd = {
                    editingAccountEntry = AccountEntry(
                        type = AccountEntryType.EXPENSE,
                        amountCents = 0,
                        category = "餐饮",
                    )
                },
                onEdit = { editingAccountEntry = it },
                onDelete = viewModel::deleteAccountEntry,
                onDeleteMany = viewModel::deleteAccountEntries,
                onAnalyzeFinancial = viewModel::analyzeFinancialSummary,
                onSaveMonthlyBudget = viewModel::saveMonthlyBudget,
                onSaveYearlyBudget = viewModel::saveYearlyBudget,
            )
            else -> SettingsScreen(
                contentPadding = padding,
                state = state,
                onThemeModeChanged = viewModel::setThemeMode,
                onReminderSettingsChanged = viewModel::saveReminderSettings,
                onEnableCaptureProtection = viewModel::enableCaptureProtection,
                onDisableCaptureProtection = viewModel::disableCaptureProtection,
                onModelSettingsChanged = viewModel::saveModelSettings,
                onClearApiKey = viewModel::clearApiKey,
                onTestConnection = viewModel::testConnection,
            )
        }
    }
}

@Composable
private fun UnreadReminderBadge(count: Int) {
    when {
        count <= 0 -> Unit
        count == 1 -> Badge()
        else -> Badge { Text(if (count > 99) "99+" else count.toString()) }
    }
}
