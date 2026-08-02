package cn.aimemo.mobile.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.EventNote
import androidx.compose.material.icons.outlined.AddCircleOutline
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import cn.aimemo.mobile.data.Schedule

private enum class AppSection(val label: String) {
    SCHEDULES("日程"),
    ADD("添加"),
    SETTINGS("设置"),
}

@Composable
fun AiMemoApp(viewModel: AppViewModel, state: AppUiState) {
    var section by rememberSaveable { mutableStateOf(AppSection.SCHEDULES) }
    var editingSchedule by remember { mutableStateOf<Schedule?>(null) }

    state.error?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::consumeError,
            title = { Text("操作未完成") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = viewModel::consumeError) { Text("知道了") } },
        )
    }

    Scaffold(
        bottomBar = {
            BottomAppBar {
                NavigationBarItem(
                    selected = section == AppSection.SCHEDULES,
                    onClick = { section = AppSection.SCHEDULES; editingSchedule = null },
                    icon = { Icon(Icons.AutoMirrored.Outlined.EventNote, null) },
                    label = { Text(AppSection.SCHEDULES.label) },
                )
                NavigationBarItem(
                    selected = section == AppSection.ADD,
                    onClick = { section = AppSection.ADD; editingSchedule = null },
                    icon = { Icon(Icons.Outlined.AddCircleOutline, null) },
                    label = { Text(AppSection.ADD.label) },
                )
                NavigationBarItem(
                    selected = section == AppSection.SETTINGS,
                    onClick = { section = AppSection.SETTINGS; editingSchedule = null },
                    icon = { Icon(Icons.Outlined.Settings, null) },
                    label = { Text(AppSection.SETTINGS.label) },
                )
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
            section == AppSection.SCHEDULES -> ScheduleListScreen(
                contentPadding = padding,
                state = state,
                onAdd = { section = AppSection.ADD },
                onEdit = { editingSchedule = it },
                onCompleted = viewModel::setCompleted,
                onDelete = viewModel::delete,
            )
            section == AppSection.ADD -> ScheduleEditorScreen(
                modifier = Modifier,
                contentPadding = padding,
                initial = null,
                onCancel = { section = AppSection.SCHEDULES },
                onSave = { schedule ->
                    viewModel.save(schedule) { section = AppSection.SCHEDULES }
                },
            )
            else -> SettingsScreen(
                contentPadding = padding,
                state = state,
                onDarkModeChanged = viewModel::setDarkMode,
                onReminderMinutesChanged = viewModel::setReminderLeadMinutes,
            )
        }
    }
}
