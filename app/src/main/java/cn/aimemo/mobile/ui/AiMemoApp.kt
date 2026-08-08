package cn.aimemo.mobile.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.EventNote
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
    SCHEDULES("日程"), ADD("添加"), ACCOUNTING("记账"), HISTORY("记录"), SETTINGS("设置")
}

@Composable
fun AiMemoApp(viewModel: AppViewModel, state: AppUiState) {
    var section by rememberSaveable { mutableStateOf(AppSection.SCHEDULES) }
    var editingSchedule by remember { mutableStateOf<Schedule?>(null) }
    var editingAccountEntry by remember { mutableStateOf<AccountEntry?>(null) }
    var editingRecognizedIndex by remember { mutableIntStateOf(-1) }

    val apiKeyRequired = state.error?.contains("API Key", ignoreCase = true) == true

    if (apiKeyRequired) {
        AlertDialog(
            onDismissRequest = viewModel::consumeNotice,
            title = { Text("使用 AI 前还差一步（也可以手动添加）") },
            text = {
                Text("AI 识别需要先填写智谱 API Key。如果暂时不想接入 AI，可以直接手动记录日程，其他功能不受影响。")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.consumeNotice()
                        editingSchedule = null
                        editingRecognizedIndex = -1
                        section = AppSection.SETTINGS
                    },
                ) { Text("去填写 API Key") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        viewModel.consumeNotice()
                        editingSchedule = Schedule(title = "")
                        editingRecognizedIndex = -1
                    },
                ) { Text("手动添加日程") }
            },
        )
    } else if (state.suggestModelSwitch) {
        AlertDialog(
            onDismissRequest = viewModel::consumeNotice,
            title = { Text("模型连接超时") },
            text = { Text("当前模型暂时没有响应。是否前往模型配置页切换模型？也可以稍后再试。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.consumeNotice()
                        editingSchedule = null
                        editingRecognizedIndex = -1
                        section = AppSection.SETTINGS
                    },
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
        bottomBar = {
            BottomAppBar {
                listOf(
                    Triple(AppSection.SCHEDULES, Icons.AutoMirrored.Outlined.EventNote, "日程"),
                    Triple(AppSection.ADD, Icons.Outlined.AddCircleOutline, "添加"),
                    Triple(AppSection.ACCOUNTING, Icons.Outlined.AccountBalanceWallet, "记账"),
                    Triple(AppSection.HISTORY, Icons.Outlined.History, "提醒记录"),
                    Triple(AppSection.SETTINGS, Icons.Outlined.Settings, "设置"),
                ).forEach { (target, icon, description) ->
                    NavigationBarItem(
                        selected = section == target,
                        onClick = {
                            section = target
                            editingSchedule = null
                            editingAccountEntry = null
                            editingRecognizedIndex = -1
                        },
                        icon = { Icon(icon, description) },
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
                onCancel = { editingSchedule = null },
                onSave = { schedule ->
                    viewModel.save(schedule) {
                        editingSchedule = null
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
                onCancel = { editingAccountEntry = null },
                onSave = { entry ->
                    viewModel.saveAccountEntry(entry) {
                        editingAccountEntry = null
                        section = AppSection.ACCOUNTING
                    }
                },
            )
            section == AppSection.SCHEDULES -> ScheduleListScreen(
                contentPadding = padding,
                state = state,
                onAdd = { section = AppSection.ADD },
                onEdit = { editingSchedule = it },
                onCompleted = viewModel::setCompleted,
                onDelete = viewModel::delete,
                onBatchCompleted = viewModel::setSchedulesCompleted,
                onBatchDelete = viewModel::deleteSchedules,
                onImport = viewModel::importSchedules,
            )
            section == AppSection.ADD -> SmartAddScreen(
                contentPadding = padding,
                state = state,
                onRecognizeText = viewModel::recognizeText,
                onRecognizeImages = viewModel::recognizeImages,
                onManualAdd = { editingSchedule = Schedule(title = "") },
                onEditResult = { editingRecognizedIndex = it },
                onRemoveResult = viewModel::removeRecognized,
                onSaveAll = { viewModel.saveRecognized { section = AppSection.SCHEDULES } },
            )
            section == AppSection.ACCOUNTING -> AccountingScreen(
                contentPadding = padding,
                entries = state.accountEntries,
                onAdd = {
                    editingAccountEntry = AccountEntry(
                        type = AccountEntryType.EXPENSE,
                        amountCents = 0,
                        category = "餐饮",
                    )
                },
                onEdit = { editingAccountEntry = it },
                onDelete = viewModel::deleteAccountEntry,
            )
            section == AppSection.HISTORY -> ReminderHistoryScreen(
                contentPadding = padding,
                logs = state.reminderLogs,
                onDelete = viewModel::deleteReminderLog,
            )
            else -> SettingsScreen(
                contentPadding = padding,
                state = state,
                onDarkModeChanged = viewModel::setDarkMode,
                onReminderSettingsChanged = viewModel::saveReminderSettings,
                onModelSettingsChanged = viewModel::saveModelSettings,
                onTestConnection = viewModel::testConnection,
            )
        }
    }
}
