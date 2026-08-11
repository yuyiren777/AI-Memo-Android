package cn.aimemo.mobile.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import cn.aimemo.mobile.ai.ZhipuAiClient
import cn.aimemo.mobile.BuildConfig
import cn.aimemo.mobile.data.ReminderLog
import cn.aimemo.mobile.data.Schedule
import cn.aimemo.mobile.data.SecureBackupContents
import cn.aimemo.mobile.data.SecureScheduleBackup
import cn.aimemo.mobile.data.Urgency
import cn.aimemo.mobile.reminder.NotificationHelper
import cn.aimemo.mobile.reminder.formatRemainingTime
import cn.aimemo.mobile.ui.theme.ImportantColor
import cn.aimemo.mobile.ui.theme.NormalColor
import cn.aimemo.mobile.ui.theme.UrgentColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date

private enum class BatchScheduleAction {
    COMPLETE,
    DELETE,
}

private data class CompletionChange(
    val schedule: Schedule,
    val completed: Boolean,
)

@Composable
fun ScheduleListScreen(
    contentPadding: PaddingValues,
    state: AppUiState,
    onTextRecognition: () -> Unit,
    onImageRecognition: () -> Unit,
    onManualAdd: () -> Unit,
    onEdit: (Schedule) -> Unit,
    onCompleted: (Schedule, Boolean) -> Unit,
    onDelete: (Schedule) -> Unit,
    onBatchCompleted: (List<Schedule>) -> Unit,
    onBatchDelete: (List<Schedule>) -> Unit,
    onImport: (SecureBackupContents) -> Unit,
    onDeleteReminderLog: (Long) -> Unit,
    showReminderLogs: Boolean,
    onShowReminderLogsChange: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingEncryptedExport by remember { mutableStateOf<String?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        val encryptedBackup = pendingEncryptedExport
        pendingEncryptedExport = null
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                require(!encryptedBackup.isNullOrBlank()) { "没有可导出的加密备份" }
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri, "wt")?.bufferedWriter(Charsets.UTF_8)?.use {
                        it.write(encryptedBackup)
                    } ?: error("无法创建备份文件")
                }
            }.onSuccess {
                Toast.makeText(context, "自动加密备份已导出", Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(context, it.message ?: "导出失败", Toast.LENGTH_LONG).show()
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    readBackupContent(context, uri)
                }
            }.onSuccess { content ->
                runCatching {
                    withContext(Dispatchers.Default) {
                        SecureScheduleBackup().decode(content)
                    }
                }.onSuccess(onImport).onFailure { error ->
                    Toast.makeText(context, error.message ?: "导入失败", Toast.LENGTH_LONG).show()
                }
            }.onFailure {
                Toast.makeText(context, it.message ?: "导入失败", Toast.LENGTH_LONG).show()
            }
        }
    }
    var showCompleted by remember { mutableStateOf(false) }
    var newMenuExpanded by remember { mutableStateOf(false) }
    var backupMenuExpanded by remember { mutableStateOf(false) }
    var filterMenuExpanded by remember { mutableStateOf(false) }
    var selectionMode by remember { mutableStateOf(false) }
    var selectedScheduleIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var pendingCompletion by remember { mutableStateOf<CompletionChange?>(null) }
    var pendingDeletion by remember { mutableStateOf<Schedule?>(null) }
    var pendingBatchAction by remember { mutableStateOf<BatchScheduleAction?>(null) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000 - System.currentTimeMillis() % 60_000)
            now = System.currentTimeMillis()
        }
    }
    val visible by remember(state.schedules, showCompleted) {
        derivedStateOf { state.schedules.filter { showCompleted || !it.completed } }
    }
    val selectedSchedules = state.schedules.filter { it.id in selectedScheduleIds }
    val selectedPendingSchedules = selectedSchedules.filterNot(Schedule::completed)
    LaunchedEffect(state.schedules) {
        val existingIds = state.schedules.mapTo(mutableSetOf(), Schedule::id)
        selectedScheduleIds = selectedScheduleIds.intersect(existingIds)
    }
    val today = remember(now / 60_000) {
        Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate()
    }
    val groups = remember(visible, today) { scheduleGroups(visible, today) }

    pendingCompletion?.let { change ->
        val completing = change.completed
        AlertDialog(
            onDismissRequest = { pendingCompletion = null },
            title = { Text(if (completing) "确认完成日程" else "确认恢复待办") },
            text = {
                Text(
                    if (completing) {
                        "确定将“${change.schedule.title}”标记为已完成吗？"
                    } else {
                        "确定将“${change.schedule.title}”恢复为待办吗？"
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingCompletion = null
                        onCompleted(change.schedule, completing)
                    },
                ) { Text(if (completing) "确认完成" else "确认恢复") }
            },
            dismissButton = { TextButton(onClick = { pendingCompletion = null }) { Text("取消") } },
        )
    }
    pendingDeletion?.let { schedule ->
        AlertDialog(
            onDismissRequest = { pendingDeletion = null },
            title = { Text("确认删除日程") },
            text = { Text("确定删除“${schedule.title}”吗？删除后无法恢复。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDeletion = null
                        onDelete(schedule)
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("确认删除") }
            },
            dismissButton = { TextButton(onClick = { pendingDeletion = null }) { Text("取消") } },
        )
    }
    pendingBatchAction?.let { action ->
        val targets = if (action == BatchScheduleAction.COMPLETE) selectedPendingSchedules else selectedSchedules
        AlertDialog(
            onDismissRequest = { pendingBatchAction = null },
            title = { Text(if (action == BatchScheduleAction.COMPLETE) "确认完成所选日程" else "确认删除所选日程") },
            text = {
                Text(
                    if (action == BatchScheduleAction.COMPLETE) {
                        "确定将选中的 ${targets.size} 条日程标记为已完成吗？"
                    } else {
                        "确定删除选中的 ${targets.size} 条日程吗？删除后无法恢复。"
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingBatchAction = null
                        selectionMode = false
                        selectedScheduleIds = emptySet()
                        if (action == BatchScheduleAction.COMPLETE) {
                            onBatchCompleted(targets)
                        } else {
                            onBatchDelete(targets)
                        }
                    },
                    colors = if (action == BatchScheduleAction.DELETE) {
                        ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    } else {
                        ButtonDefaults.textButtonColors()
                    },
                ) { Text(if (action == BatchScheduleAction.COMPLETE) "确认完成" else "确认删除") }
            },
            dismissButton = { TextButton(onClick = { pendingBatchAction = null }) { Text("取消") } },
        )
    }

    Column(Modifier.fillMaxSize().padding(contentPadding).padding(horizontal = 16.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("日程概览", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("${state.schedules.count { !it.completed }} 项待处理", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box {
                Button(onClick = { newMenuExpanded = true }) {
                    Icon(Icons.Outlined.Add, null)
                    Spacer(Modifier.width(5.dp))
                    Text("新建")
                    Icon(Icons.Outlined.ArrowDropDown, null)
                }
                DropdownMenu(
                    expanded = newMenuExpanded,
                    onDismissRequest = { newMenuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("文字识别") },
                        leadingIcon = { Icon(Icons.Outlined.TextFields, null) },
                        onClick = {
                            newMenuExpanded = false
                            onTextRecognition()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("图片识别") },
                        leadingIcon = { Icon(Icons.Outlined.Image, null) },
                        onClick = {
                            newMenuExpanded = false
                            onImageRecognition()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("手动添加") },
                        leadingIcon = { Icon(Icons.Outlined.Edit, null) },
                        onClick = {
                            newMenuExpanded = false
                            onManualAdd()
                        },
                    )
                }
            }
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = {
                    selectionMode = !selectionMode
                    selectedScheduleIds = emptySet()
                },
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 12.dp),
            ) { Text(if (selectionMode) "取消多选" else "多选") }
            Box(Modifier.weight(1f)) {
                OutlinedButton(
                    onClick = { backupMenuExpanded = true },
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                ) {
                    Text("备份")
                    Icon(Icons.Outlined.ArrowDropDown, null)
                }
                DropdownMenu(
                    expanded = backupMenuExpanded,
                    onDismissRequest = { backupMenuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("导入本机备份") },
                        leadingIcon = { Icon(Icons.Outlined.FileDownload, null) },
                        onClick = {
                            backupMenuExpanded = false
                            importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("导出自动加密备份") },
                        leadingIcon = { Icon(Icons.Outlined.FileUpload, null) },
                        onClick = {
                            backupMenuExpanded = false
                            val contents = SecureBackupContents(
                                schedules = state.schedules.toList(),
                                accountEntries = state.accountEntries.toList(),
                                budgetSettings = state.budgetSettings,
                            )
                            scope.launch {
                                runCatching {
                                    withContext(Dispatchers.Default) {
                                        SecureScheduleBackup().encode(contents)
                                    }
                                }.onSuccess { encrypted ->
                                    pendingEncryptedExport = encrypted
                                    exportLauncher.launch("AI备忘录自动加密备份_${LocalDate.now()}.aimemo")
                                }.onFailure { error ->
                                    Toast.makeText(context, error.message ?: "创建加密备份失败", Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                    )
                }
            }
            Box(Modifier.weight(1f)) {
                OutlinedButton(
                    onClick = { filterMenuExpanded = true },
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                ) {
                    Text("筛选")
                    Icon(Icons.Outlined.ArrowDropDown, null)
                }
                DropdownMenu(
                    expanded = filterMenuExpanded,
                    onDismissRequest = { filterMenuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("显示已完成日程") },
                        leadingIcon = { Checkbox(showCompleted, onCheckedChange = null) },
                        onClick = { showCompleted = !showCompleted },
                    )
                    DropdownMenuItem(
                        text = { Text("显示已提醒日程") },
                        leadingIcon = { Checkbox(showReminderLogs, onCheckedChange = null) },
                        onClick = { onShowReminderLogsChange(!showReminderLogs) },
                    )
                }
            }
        }
        if (selectionMode) {
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("已选择 ${selectedSchedules.size} 条", modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                TextButton(
                    onClick = { selectedScheduleIds = visible.mapTo(mutableSetOf(), Schedule::id) },
                    enabled = visible.isNotEmpty(),
                ) { Text("全选当前列表") }
                TextButton(onClick = { selectedScheduleIds = emptySet() }) { Text("清空") }
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = { pendingBatchAction = BatchScheduleAction.COMPLETE },
                    enabled = selectedPendingSchedules.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                ) { Text("确认完成") }
                Button(
                    onClick = { pendingBatchAction = BatchScheduleAction.DELETE },
                    enabled = selectedSchedules.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text("删除所选") }
            }
        }
        Spacer(Modifier.height(6.dp))
        when {
            state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            visible.isEmpty() && !showReminderLogs -> EmptyScheduleState()
            else -> LazyColumn(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 18.dp),
            ) {
                if (visible.isEmpty()) {
                    item(key = "empty_schedule_filter") {
                        Text(
                            "当前没有符合筛选条件的日程",
                            modifier = Modifier.fillMaxWidth().padding(vertical = 22.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                groups.forEach { group ->
                    item(key = "group_${group.title}") {
                        Row(
                            Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                group.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("${group.schedules.size} 项", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    items(group.schedules, key = Schedule::id) { schedule ->
                        ScheduleCard(
                            schedule = schedule,
                            nowMillis = now,
                            selectionMode = selectionMode,
                            selected = schedule.id in selectedScheduleIds,
                            onSelectionChanged = { selected ->
                                selectedScheduleIds = if (selected) {
                                    selectedScheduleIds + schedule.id
                                } else {
                                    selectedScheduleIds - schedule.id
                                }
                            },
                            onEdit = { onEdit(schedule) },
                            onCompletedRequested = { completed ->
                                pendingCompletion = CompletionChange(schedule, completed)
                            },
                            onDeleteRequested = { pendingDeletion = schedule },
                        )
                    }
                }
                if (showReminderLogs) {
                    item(key = "reminder_log_header") {
                        Row(
                            Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "已提醒日程",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("${state.reminderLogs.size} 条", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (state.reminderLogs.isEmpty()) {
                        item(key = "empty_reminder_logs") {
                            Text(
                                "还没有提醒记录",
                                modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        items(state.reminderLogs, key = { "reminder_${it.id}" }) { log ->
                            ReminderLogCard(log, onDelete = { onDeleteReminderLog(log.id) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyScheduleState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Outlined.EventAvailable, null, Modifier.size(58.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(12.dp))
            Text("还没有待办日程", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun ReminderLogCard(log: ReminderLog, onDelete: () -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${stageLabel(log.stage)}：${log.scheduleTitle}", fontWeight = FontWeight.Bold)
                Text(
                    EXPORT_TIME_FORMATTER.format(
                        LocalDateTime.ofInstant(Instant.ofEpochMilli(log.createdAt), ZoneId.systemDefault())
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onDelete) {
                Icon(Icons.Outlined.DeleteOutline, "删除提醒记录", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun ScheduleCard(
    schedule: Schedule,
    nowMillis: Long,
    selectionMode: Boolean,
    selected: Boolean,
    onSelectionChanged: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onCompletedRequested: (Boolean) -> Unit,
    onDeleteRequested: () -> Unit,
) {
    var showDetails by remember(schedule.id) { mutableStateOf(false) }
    val accent = when (schedule.urgency) {
        Urgency.NORMAL -> NormalColor
        Urgency.IMPORTANT -> ImportantColor
        Urgency.URGENT -> UrgentColor
    }
    if (showDetails) {
        AlertDialog(
            onDismissRequest = { showDetails = false },
            title = { Text("日程详情") },
            text = {
                Column(
                    Modifier.fillMaxWidth().heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(schedule.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    HorizontalDivider()
                    Text(scheduleDateText(schedule, nowMillis))
                    if (schedule.location.isNotBlank()) Text("地点：${schedule.location}")
                    Text("紧急度：${schedule.urgency.label}")
                    if (schedule.repeatRule != "none") Text("重复：${repeatLabel(schedule.repeatRule)}")
                    if (schedule.notes.isNotBlank()) {
                        HorizontalDivider()
                        Text("备注", fontWeight = FontWeight.Bold)
                        Text(schedule.notes)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDetails = false; onEdit() }) { Text("编辑") }
            },
            dismissButton = {
                TextButton(onClick = { showDetails = false }) { Text("关闭") }
            },
        )
    }
    Surface(
        Modifier.fillMaxWidth().clickable {
            if (selectionMode) {
                onSelectionChanged(!selected)
            } else {
                showDetails = true
            }
        },
        shape = RoundedCornerShape(6.dp),
        tonalElevation = 1.dp,
    ) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(6.dp).fillMaxHeight().background(accent))
            Column(Modifier.weight(1f).padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = if (selectionMode) selected else schedule.completed,
                        onCheckedChange = { checked ->
                            if (selectionMode) onSelectionChanged(checked) else onCompletedRequested(checked)
                        },
                    )
                    Text(
                        schedule.title,
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        textDecoration = if (schedule.completed) TextDecoration.LineThrough else null,
                    )
                    if (!selectionMode) {
                        IconButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, "编辑日程") }
                        IconButton(onClick = onDeleteRequested) {
                            Icon(Icons.Outlined.DeleteOutline, "删除日程", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                Text(scheduleDateText(schedule, nowMillis), color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
                if (schedule.location.isNotBlank()) Text("地点：${schedule.location}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (schedule.notes.isNotBlank()) Text("备注：${schedule.notes}", maxLines = 3, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (schedule.repeatRule != "none") Text("重复：${repeatLabel(schedule.repeatRule)}", color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

private fun scheduleDateText(schedule: Schedule, nowMillis: Long): String {
    val date = schedule.date ?: return "未设置日期 · 每次打开应用时提醒"
    val time = schedule.startTime ?: LocalTime.NOON
    val dateText = date.format(SCHEDULE_DATE_FORMATTER)
    val timeText = buildString {
        append(time.format(TIME_FORMATTER))
        schedule.endTime?.let { append("～${it.format(TIME_FORMATTER)}") }
        if (schedule.startTime == null) append("（默认中午）")
    }
    val target = date.atTime(time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val remaining = formatRemainingTime(target, nowMillis)
    return "$dateText  $timeText · $remaining"
}

@Composable
fun ScheduleEditorScreen(
    modifier: Modifier,
    contentPadding: PaddingValues,
    initial: Schedule?,
    onCancel: () -> Unit,
    onSave: (Schedule) -> Unit,
) {
    val context = LocalContext.current
    var title by remember(initial) { mutableStateOf(initial?.title.orEmpty()) }
    var location by remember(initial) { mutableStateOf(initial?.location.orEmpty()) }
    var notes by remember(initial) { mutableStateOf(initial?.notes.orEmpty()) }
    var dateEnabled by remember(initial) { mutableStateOf(initial?.date != null) }
    var selectedDate by remember(initial) { mutableStateOf(initial?.date ?: LocalDate.now()) }
    var timeEnabled by remember(initial) { mutableStateOf(initial?.startTime != null) }
    var selectedTime by remember(initial) { mutableStateOf(initial?.startTime ?: LocalTime.NOON) }
    var endEnabled by remember(initial) { mutableStateOf(initial?.endTime != null) }
    var selectedEnd by remember(initial) { mutableStateOf(initial?.endTime ?: LocalTime.NOON.plusHours(1)) }
    var urgency by remember(initial) { mutableStateOf(initial?.urgency ?: Urgency.NORMAL) }
    var repeatRule by remember(initial) { mutableStateOf(initial?.repeatRule ?: "none") }
    val dateDialog = remember(selectedDate) { DatePickerDialog(context, { _, y, m, d -> selectedDate = LocalDate.of(y, m + 1, d) }, selectedDate.year, selectedDate.monthValue - 1, selectedDate.dayOfMonth) }
    val timeDialog = remember(selectedTime) { TimePickerDialog(context, { _, h, m -> selectedTime = LocalTime.of(h, m) }, selectedTime.hour, selectedTime.minute, true) }
    val endDialog = remember(selectedEnd) { TimePickerDialog(context, { _, h, m -> selectedEnd = LocalTime.of(h, m) }, selectedEnd.hour, selectedEnd.minute, true) }

    Column(
        modifier.fillMaxSize().padding(contentPadding).verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        Text(if (initial?.id == null || initial.id == 0L) "添加日程" else "编辑日程", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("日期、时间和地点均可不填；只有日期时按当天中午 12:00 提醒。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), label = { Text("标题（必填）") }, singleLine = true)
        OutlinedTextField(location, { location = it }, Modifier.fillMaxWidth(), label = { Text("地点（选填）") }, singleLine = true)
        SettingSwitchRow("设置日期", dateEnabled) { dateEnabled = it; if (!it) { timeEnabled = false; endEnabled = false } }
        if (dateEnabled) {
            OutlinedButton(dateDialog::show, Modifier.fillMaxWidth()) { Text(selectedDate.format(DATE_PICKER_FORMATTER)) }
            SettingSwitchRow("设置具体时间", timeEnabled) { timeEnabled = it; if (!it) endEnabled = false }
            if (timeEnabled) {
                OutlinedButton(timeDialog::show, Modifier.fillMaxWidth()) { Text("开始 ${selectedTime.format(TIME_FORMATTER)}") }
                SettingSwitchRow("设置结束时间", endEnabled) { endEnabled = it }
                if (endEnabled) OutlinedButton(endDialog::show, Modifier.fillMaxWidth()) { Text("结束 ${selectedEnd.format(TIME_FORMATTER)}") }
            }
        }
        Text("紧急程度", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Urgency.entries.forEach { option -> FilterChip(urgency == option, { urgency = option }, label = { Text(option.label) }) }
        }
        Text("重复安排", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            listOf("none" to "不重复", "daily" to "每天", "weekly" to "每周", "monthly" to "每月").forEach { (value, label) ->
                FilterChip(repeatRule.startsWith(value), { repeatRule = value }, label = { Text(label) })
            }
        }
        OutlinedTextField(notes, { notes = it }, Modifier.fillMaxWidth().height(130.dp), label = { Text("备注（选填）") })
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)) {
            TextButton(onClick = onCancel) { Text("取消") }
            Button(
                enabled = title.isNotBlank(),
                onClick = {
                    onSave((initial ?: Schedule(title = title)).copy(
                        title = title.trim(), location = location.trim(), notes = notes.trim(),
                        date = selectedDate.takeIf { dateEnabled },
                        startTime = selectedTime.withSecond(0).withNano(0).takeIf { dateEnabled && timeEnabled },
                        endTime = selectedEnd.withSecond(0).withNano(0).takeIf { dateEnabled && timeEnabled && endEnabled },
                        urgency = urgency, repeatRule = repeatRule,
                    ))
                },
            ) { Text("保存") }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
fun SmartAddScreen(
    contentPadding: PaddingValues,
    state: AppUiState,
    initialMode: String,
    onBack: () -> Unit,
    onRecognizeText: (String) -> Unit,
    onRecognizeImages: (List<Pair<ByteArray, String>>) -> Unit,
    onManualAdd: () -> Unit,
    onEditResult: (Int) -> Unit,
    onRemoveResult: (Int) -> Unit,
    onSaveAll: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var mode by remember(initialMode) { mutableStateOf(initialMode) }
    var input by remember { mutableStateOf("") }
    var imageName by remember { mutableStateOf("") }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    val processUris: (List<Uri>) -> Unit = { uris ->
        val selected = uris.take(MAX_AI_IMAGE_COUNT)
        imageName = "正在读取 1/${selected.size} 张图片…"
        scope.launch {
            val images = mutableListOf<Pair<ByteArray, String>>()
            val failures = mutableListOf<String>()
            selected.forEachIndexed { index, uri ->
                imageName = "正在读取第 ${index + 1}/${selected.size} 张图片…"
                runCatching {
                    withContext(Dispatchers.IO) {
                        prepareAiImage(context, uri, SCHEDULE_IMAGE_PROFILE)
                    }
                }
                    .onSuccess { images.add(it) }
                    .onFailure { failures += it.message ?: "无法读取第 ${index + 1} 张图片" }
            }
            when {
                images.isNotEmpty() -> {
                    imageName = if (failures.isEmpty()) {
                        "已选择 ${images.size} 张图片"
                    } else {
                        "已读取 ${images.size} 张，${failures.size} 张无法读取"
                    }
                    onRecognizeImages(images)
                }
                else -> imageName = failures.firstOrNull() ?: "没有读取到可用图片"
            }
        }
    }
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(MAX_AI_IMAGE_COUNT)
    ) { uris -> if (uris.isNotEmpty()) processUris(uris) }
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { captured ->
        val uri = pendingCameraUri
        pendingCameraUri = null
        if (captured && uri != null) {
            processUris(listOf(uri))
        } else if (uri != null) {
            runCatching { context.contentResolver.delete(uri, null, null) }
            imageName = "已取消拍照"
        }
    }

    LazyColumn(
        Modifier.fillMaxSize().padding(contentPadding),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回日程") }
                Column {
                    Text("智能添加", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text("把聊天记录、通知或截图交给 AI，识别成日程后可逐条修改。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(mode == "text", { mode = "text" }, label = { Text("文字识别") }, modifier = Modifier.weight(1f))
                FilterChip(mode == "image", { mode = "image" }, label = { Text("图片识别") }, modifier = Modifier.weight(1f))
                FilterChip(false, onManualAdd, label = { Text("手动添加") }, modifier = Modifier.weight(1f))
            }
        }
        if (mode == "text") item {
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        input,
                        { input = it },
                        Modifier.fillMaxWidth().height(180.dp),
                        label = { Text("粘贴或输入要记录的内容") },
                        placeholder = { Text("可以一次识别多条日程") },
                    )
                    Button({ onRecognizeText(input) }, enabled = input.isNotBlank() && !state.recognizing, modifier = Modifier.align(Alignment.End)) { Text("识别文字") }
                }
            }
        }
        if (mode == "image") item {
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Outlined.Image, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(imageName.ifBlank { "选择通知、聊天或考试时间截图" })
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                            enabled = !state.recognizing,
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 8.dp),
                        ) {
                            Icon(Icons.Outlined.Image, null)
                            Spacer(Modifier.width(4.dp))
                            Text("相册（最多4张）")
                        }
                        OutlinedButton(
                            onClick = {
                                runCatching { createCameraImageUri(context) }
                                    .onSuccess { uri ->
                                        pendingCameraUri = uri
                                        cameraLauncher.launch(uri)
                                    }
                                    .onFailure { imageName = it.message ?: "无法打开相机" }
                            },
                            enabled = !state.recognizing,
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 8.dp),
                        ) {
                            Icon(Icons.Outlined.PhotoCamera, null)
                            Spacer(Modifier.width(4.dp))
                            Text("拍照")
                        }
                        OutlinedButton(
                            onClick = {
                                clipboardImageUri(context)?.let { processUris(listOf(it)) }
                                    ?: run { imageName = "剪贴板中没有可读取的图片" }
                            },
                            enabled = !state.recognizing,
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 8.dp),
                        ) { Icon(Icons.Outlined.ContentPaste, null); Spacer(Modifier.width(4.dp)); Text("粘贴") }
                    }
                }
            }
        }
        if (state.recognizing) item {
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(28.dp))
                Spacer(Modifier.width(12.dp))
                Text(state.recognitionStatus)
            }
        }
        if (state.recognized.isNotEmpty()) {
            item {
                Text("识别结果（${state.recognized.size} 条）", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("请确认日期和时间，点击编辑可修正。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            itemsIndexed(state.recognized) { index, schedule ->
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(schedule.title, fontWeight = FontWeight.Bold)
                            Text(scheduleDateText(schedule, System.currentTimeMillis()), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (schedule.location.isNotBlank()) Text("地点：${schedule.location}")
                        }
                        IconButton({ onEditResult(index) }) { Icon(Icons.Outlined.Edit, "修改识别结果") }
                        IconButton({ onRemoveResult(index) }) { Icon(Icons.Outlined.DeleteOutline, "移除", tint = MaterialTheme.colorScheme.error) }
                    }
                }
            }
            item { Button(onSaveAll, Modifier.fillMaxWidth()) { Text("确认并添加全部日程") } }
        }
    }
}

@Composable
fun SettingsScreen(
    contentPadding: PaddingValues,
    state: AppUiState,
    onThemeModeChanged: (String) -> Unit,
    onReminderSettingsChanged: (Int, Int?, Int?) -> Unit,
    onEnableCaptureProtection: () -> Unit,
    onDisableCaptureProtection: suspend (CharArray, CharArray?) -> String?,
    onModelSettingsChanged: (String, String, String, String, String) -> Unit,
    onClearApiKey: () -> Unit,
    onTestConnection: () -> Unit,
) {
    var mode by remember(state.modelMode) { mutableStateOf(state.modelMode) }
    var apiKey by remember { mutableStateOf("") }
    var unified by remember(state.unifiedModel) { mutableStateOf(state.unifiedModel) }
    var textModel by remember(state.textModel) { mutableStateOf(state.textModel) }
    var imageModel by remember(state.imageModel) { mutableStateOf(state.imageModel) }
    var modelModeMenuExpanded by remember { mutableStateOf(false) }
    var showCaptureProtectionWarning by remember { mutableStateOf(false) }
    var showCapturePasswordDialog by remember { mutableStateOf(false) }
    var capturePassword by remember { mutableStateOf("") }
    var capturePasswordConfirmation by remember { mutableStateOf("") }
    var capturePasswordError by remember { mutableStateOf<String?>(null) }
    var verifyingCapturePassword by remember { mutableStateOf(false) }
    var finalParts by remember(state.finalReminderMinutes) { mutableStateOf(minutesToParts(state.finalReminderMinutes)) }
    var firstEnabled by remember(state.firstReminderMinutes) { mutableStateOf(state.firstReminderMinutes != null) }
    var firstParts by remember(state.firstReminderMinutes) { mutableStateOf(minutesToParts(state.firstReminderMinutes ?: 0)) }
    var secondEnabled by remember(state.secondReminderMinutes) { mutableStateOf(state.secondReminderMinutes != null) }
    var secondParts by remember(state.secondReminderMinutes) { mutableStateOf(minutesToParts(state.secondReminderMinutes ?: 0)) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    if (showCaptureProtectionWarning) {
        AlertDialog(
            onDismissRequest = { showCaptureProtectionWarning = false },
            title = { Text("关闭截图与录屏保护？") },
            text = {
                Text("关闭后，应用中的日程、账单、API 配置等画面可以被截图或录屏，可能造成隐私泄露。")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showCaptureProtectionWarning = false
                        capturePassword = ""
                        capturePasswordConfirmation = ""
                        capturePasswordError = null
                        showCapturePasswordDialog = true
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("继续关闭") }
            },
            dismissButton = { TextButton(onClick = { showCaptureProtectionWarning = false }) { Text("保持开启") } },
        )
    }

    if (showCapturePasswordDialog) {
        val creatingPassword = !state.hasCaptureProtectionPassword
        AlertDialog(
            onDismissRequest = {
                if (!verifyingCapturePassword) {
                    showCapturePasswordDialog = false
                    capturePassword = ""
                    capturePasswordConfirmation = ""
                    capturePasswordError = null
                }
            },
            title = { Text(if (creatingPassword) "设置安全密码" else "验证安全密码") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        if (creatingPassword) {
                            "密码无法找回，请务必记住。以后每次关闭截图与录屏保护时都需要输入该密码。"
                        } else {
                            "请输入之前设置的安全密码，验证后才能关闭截图与录屏保护。"
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = capturePassword,
                        onValueChange = {
                            capturePassword = it.take(128)
                            capturePasswordError = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("安全密码") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                    )
                    if (creatingPassword) {
                        OutlinedTextField(
                            value = capturePasswordConfirmation,
                            onValueChange = {
                                capturePasswordConfirmation = it.take(128)
                                capturePasswordError = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("再次输入安全密码") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                        )
                    }
                    capturePasswordError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        when {
                            capturePassword.length < 8 -> capturePasswordError = "安全密码至少需要 8 位"
                            creatingPassword && capturePassword != capturePasswordConfirmation -> {
                                capturePasswordError = "两次输入的密码不一致"
                            }
                            else -> scope.launch {
                                verifyingCapturePassword = true
                                val error = onDisableCaptureProtection(
                                    capturePassword.toCharArray(),
                                    if (creatingPassword) capturePasswordConfirmation.toCharArray() else null,
                                )
                                verifyingCapturePassword = false
                                if (error == null) {
                                    showCapturePasswordDialog = false
                                    capturePassword = ""
                                    capturePasswordConfirmation = ""
                                    capturePasswordError = null
                                } else {
                                    capturePasswordError = error
                                }
                            }
                        }
                    },
                    enabled = !verifyingCapturePassword,
                ) {
                    if (verifyingCapturePassword) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(if (verifyingCapturePassword) "验证中…" else "确认关闭")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showCapturePasswordDialog = false
                        capturePassword = ""
                        capturePasswordConfirmation = ""
                        capturePasswordError = null
                    },
                    enabled = !verifyingCapturePassword,
                ) { Text("取消") }
            },
        )
    }

    Column(Modifier.fillMaxSize().padding(contentPadding).verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("设置", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("AI 模型", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text("API Key 已加密保存在本机。默认模型服务为智谱。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://open.bigmodel.cn/"))) }) { Text("点我申请智谱 API Key") }
                Box(Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { modelModeMenuExpanded = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            if (mode == "separate") "文本 / 视觉理解分开（推荐）" else "只用视觉理解模型同时处理文字图片",
                            modifier = Modifier.weight(1f),
                        )
                        Icon(Icons.Outlined.ArrowDropDown, contentDescription = "选择模型模式")
                    }
                    DropdownMenu(
                        expanded = modelModeMenuExpanded,
                        onDismissRequest = { modelModeMenuExpanded = false },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        DropdownMenuItem(
                            text = { Text("文本 / 视觉理解分开（推荐）") },
                            onClick = {
                                mode = "separate"
                                modelModeMenuExpanded = false
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("只用视觉理解模型同时处理文字图片") },
                            onClick = {
                                mode = "unified"
                                modelModeMenuExpanded = false
                            },
                        )
                    }
                }
                OutlinedTextField(
                    apiKey,
                    { apiKey = it },
                    Modifier.fillMaxWidth(),
                    label = { Text(if (state.hasApiKey) "API Key（已保存）" else "API Key（必填）") },
                    supportingText = if (state.hasApiKey) {
                        { Text("留空表示继续使用已保存的 Key；输入新值可替换。") }
                    } else {
                        null
                    },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                )
                if (state.hasApiKey) {
                    TextButton(
                        onClick = {
                            apiKey = ""
                            onClearApiKey()
                        },
                        modifier = Modifier.align(Alignment.End),
                    ) { Text("清除已保存 Key") }
                }
                if (mode == "unified") OutlinedTextField(unified, { unified = it }, Modifier.fillMaxWidth(), label = { Text("视觉理解模型（留空用 ${ZhipuAiClient.DEFAULT_IMAGE_MODEL}）") }, singleLine = true)
                else {
                    OutlinedTextField(textModel, { textModel = it }, Modifier.fillMaxWidth(), label = { Text("文本模型（留空用 ${ZhipuAiClient.DEFAULT_TEXT_MODEL}）") }, singleLine = true)
                    OutlinedTextField(imageModel, { imageModel = it }, Modifier.fillMaxWidth(), label = { Text("视觉理解模型（留空用 ${ZhipuAiClient.DEFAULT_IMAGE_MODEL}）") }, singleLine = true)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)) {
                    OutlinedButton(
                        onClick = {
                            onModelSettingsChanged(mode, apiKey, unified, textModel, imageModel)
                            apiKey = ""
                            onTestConnection()
                        },
                        enabled = (apiKey.isNotBlank() || state.hasApiKey) && !state.recognizing,
                    ) { Text("测试连接") }
                    Button(
                        onClick = {
                            onModelSettingsChanged(mode, apiKey, unified, textModel, imageModel)
                            apiKey = ""
                        },
                        enabled = apiKey.isNotBlank() || state.hasApiKey,
                    ) { Text("保存模型") }
                }
                if (state.recognizing) {
                    Row(
                        Modifier.fillMaxWidth().padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.5.dp)
                        Text(
                            state.recognitionStatus.ifBlank { "正在测试模型连接，请稍候…" },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("三阶段提醒", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text("最后一次默认提前 30 分钟；第一次必须最早，第二次居中。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                ReminderPartsRow("最后一次提醒", finalParts) { finalParts = it }
                SettingSwitchRow("启用第一次提醒", firstEnabled) { firstEnabled = it }
                if (firstEnabled) ReminderPartsRow("第一次提醒", firstParts) { firstParts = it }
                SettingSwitchRow("启用第二次提醒", secondEnabled) { secondEnabled = it }
                if (secondEnabled) ReminderPartsRow("第二次提醒", secondParts) { secondParts = it }
                Button({
                    onReminderSettingsChanged(
                        partsToMinutes(finalParts),
                        partsToMinutes(firstParts).takeIf { firstEnabled },
                        partsToMinutes(secondParts).takeIf { secondEnabled },
                    )
                }, Modifier.align(Alignment.End)) { Text("应用提醒设置") }
            }
        }
        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("外观", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text("选择日间、柔和或夜间主题；首次打开默认使用柔和模式。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = state.themeMode == "light",
                        onClick = { onThemeModeChanged("light") },
                        label = { Text("日间") },
                        modifier = Modifier.weight(1f),
                    )
                    FilterChip(
                        selected = state.themeMode == "soft",
                        onClick = { onThemeModeChanged("soft") },
                        label = { Text("柔和") },
                        modifier = Modifier.weight(1f),
                    )
                    FilterChip(
                        selected = state.themeMode == "dark",
                        onClick = { onThemeModeChanged("dark") },
                        label = { Text("夜间") },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("通知显示", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    "“提醒后台服务运行中”通知表示退出界面后仍可提醒。清理后台应用时，可以下滑或者长按锁住本应用（不同手机厂商锁住方式不同），防止提醒失灵（我已经尽力了，还是没法防后台清理提醒功能）。所以重要日程还是自己定闹钟比较合适，后面我也会考虑用服务器推送实现24小时提醒。",
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold,
                )
                Button(
                    onClick = {
                        NotificationHelper.showTest(context)
                        Toast.makeText(context, NotificationHelper.readinessMessage(context), Toast.LENGTH_LONG).show()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("测试声音和顶部提醒") }
                OutlinedButton({ NotificationHelper.openChannelSettings(context) }, Modifier.fillMaxWidth()) { Text("1. 通知权限与横幅设置") }
                OutlinedButton({ NotificationHelper.openExactAlarmSettings(context) }, Modifier.fillMaxWidth()) { Text("2. 精确提醒权限") }
                OutlinedButton({ NotificationHelper.openBatterySettings(context) }, Modifier.fillMaxWidth()) { Text("3. 后台与电池优化设置") }
            }
        }
        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("隐私与数据", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text("API Key、日程、账单、提醒历史、自定义分类和预算设置均已加密保存在本机；仅在进行 AI 识别或用户主动分析收支时，发送本次选择的文字、图片或当前周期汇总给智谱模型服务。")
                HorizontalDivider()
                SettingSwitchRow("截图与录屏保护", state.captureProtectionEnabled) { enabled ->
                    if (enabled) {
                        onEnableCaptureProtection()
                    } else {
                        showCaptureProtectionWarning = true
                    }
                }
                Text(
                    if (state.captureProtectionEnabled) "当前禁止应用内截图和录屏" else "当前允许应用内截图和录屏",
                    color = if (state.captureProtectionEnabled) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
                HorizontalDivider()
                Text("AI备忘录 Android · ${BuildConfig.VERSION_NAME}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun ReminderPartsRow(label: String, parts: Triple<String, String, String>, onChange: (Triple<String, String, String>) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, fontWeight = FontWeight.Medium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField(parts.first, "天", Modifier.weight(1f)) { onChange(Triple(it, parts.second, parts.third)) }
            NumberField(parts.second, "时", Modifier.weight(1f)) { onChange(Triple(parts.first, it, parts.third)) }
            NumberField(parts.third, "分", Modifier.weight(1f)) { onChange(Triple(parts.first, parts.second, it)) }
        }
    }
}

@Composable
private fun NumberField(value: String, label: String, modifier: Modifier, onChange: (String) -> Unit) {
    OutlinedTextField(value, { onChange(it.filter(Char::isDigit).take(5)) }, modifier, label = { Text(label) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
}

@Composable
private fun SettingSwitchRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) }.padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        Switch(checked, onCheckedChange)
    }
}

private fun minutesToParts(total: Int) = Triple((total / 1440).toString(), ((total % 1440) / 60).toString(), (total % 60).toString())
private fun partsToMinutes(parts: Triple<String, String, String>) = (parts.first.toIntOrNull() ?: 0) * 1440 + (parts.second.toIntOrNull() ?: 0) * 60 + (parts.third.toIntOrNull() ?: 0)

private fun readBackupContent(context: Context, uri: Uri): String {
    return context.contentResolver.openInputStream(uri)?.use { input ->
        ByteArrayOutputStream().use { output ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var total = 0
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                require(total <= MAX_BACKUP_BYTES) { "备份文件过大，无法导入" }
                output.write(buffer, 0, read)
            }
            output.toString(Charsets.UTF_8.name())
        }
    } ?: error("无法读取所选文件")
}

private fun stageLabel(key: String) = when (key) { "first" -> "第一次提醒"; "second" -> "第二次提醒"; else -> "最后一次提醒" }
private fun repeatLabel(rule: String) = when { rule == "daily" -> "每天"; rule.startsWith("weekly") -> "每周"; rule.startsWith("monthly") -> "每月"; else -> "不重复" }

private data class ScheduleGroup(val title: String, val schedules: List<Schedule>)

private fun scheduleGroups(schedules: List<Schedule>, today: LocalDate): List<ScheduleGroup> {
    val tomorrow = today.plusDays(1)
    val dayAfterTomorrow = today.plusDays(2)
    val definitions = listOf(
        "已过期" to schedules.filter { it.date != null && it.date.isBefore(today) },
        "今天" to schedules.filter { it.date == today },
        "明天" to schedules.filter { it.date == tomorrow },
        "后天" to schedules.filter { it.date == dayAfterTomorrow },
        "未来" to schedules.filter { it.date != null && it.date.isAfter(dayAfterTomorrow) },
        "未设日期" to schedules.filter { it.date == null },
    )
    return definitions.mapNotNull { (title, items) ->
        if (items.isEmpty()) null else ScheduleGroup(title, items)
    }
}

private fun clipboardImageUri(context: Context): Uri? {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    val clip = clipboard.primaryClip ?: return null
    if (!clip.description.hasMimeType("image/*") && !clip.description.hasMimeType(ClipDescription.MIMETYPE_TEXT_URILIST)) return null
    return clip.getItemAt(0).uri
}

private val SCHEDULE_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy年M月d日 EEEE")
private val DATE_PICKER_FORMATTER = DateTimeFormatter.ofPattern("yyyy年M月d日")
private val TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm")
private val EXPORT_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
private const val MAX_BACKUP_BYTES = 20 * 1024 * 1024
