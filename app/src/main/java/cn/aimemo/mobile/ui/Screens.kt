package cn.aimemo.mobile.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import cn.aimemo.mobile.data.Schedule
import cn.aimemo.mobile.data.Urgency
import cn.aimemo.mobile.ui.theme.ImportantColor
import cn.aimemo.mobile.ui.theme.NormalColor
import cn.aimemo.mobile.ui.theme.UrgentColor
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Composable
fun ScheduleListScreen(
    contentPadding: PaddingValues,
    state: AppUiState,
    onAdd: () -> Unit,
    onEdit: (Schedule) -> Unit,
    onCompleted: (Schedule, Boolean) -> Unit,
    onDelete: (Schedule) -> Unit,
) {
    var showCompleted by remember { mutableStateOf(false) }
    val visible = state.schedules.filter { showCompleted || !it.completed }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = 18.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("日程概览", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(
                    "${state.schedules.count { !it.completed }} 项待处理",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(onClick = onAdd) {
                Icon(Icons.Outlined.Add, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("新建")
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showCompleted = !showCompleted }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = showCompleted, onCheckedChange = { showCompleted = it })
            Text("显示已完成日程")
        }
        Spacer(Modifier.height(6.dp))

        when {
            state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            visible.isEmpty() -> EmptyScheduleState(onAdd)
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 18.dp),
            ) {
                items(visible, key = Schedule::id) { schedule ->
                    ScheduleCard(
                        schedule = schedule,
                        onEdit = { onEdit(schedule) },
                        onCompleted = { onCompleted(schedule, it) },
                        onDelete = { onDelete(schedule) },
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyScheduleState(onAdd: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Outlined.EventAvailable,
                contentDescription = null,
                modifier = Modifier.size(58.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(12.dp))
            Text("还没有待办日程", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = onAdd) { Text("添加第一条日程") }
        }
    }
}

@Composable
private fun ScheduleCard(
    schedule: Schedule,
    onEdit: () -> Unit,
    onCompleted: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    val accent = when (schedule.urgency) {
        Urgency.NORMAL -> NormalColor
        Urgency.IMPORTANT -> ImportantColor
        Urgency.URGENT -> UrgentColor
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(6.dp),
        tonalElevation = 1.dp,
        shadowElevation = 0.dp,
    ) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(6.dp).fillMaxHeight().background(accent))
            Column(Modifier.weight(1f).padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = schedule.completed, onCheckedChange = onCompleted)
                    Text(
                        schedule.title,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        textDecoration = if (schedule.completed) TextDecoration.LineThrough else null,
                    )
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Outlined.Edit, contentDescription = "编辑日程")
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Outlined.DeleteOutline, contentDescription = "删除日程", tint = MaterialTheme.colorScheme.error)
                    }
                }
                Text(
                    scheduleDateText(schedule),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium,
                )
                if (schedule.location.isNotBlank()) {
                    Spacer(Modifier.height(5.dp))
                    Text("地点：${schedule.location}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (schedule.notes.isNotBlank()) {
                    Spacer(Modifier.height(5.dp))
                    Text(schedule.notes, maxLines = 2, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

private fun scheduleDateText(schedule: Schedule): String {
    val date = schedule.date ?: return "未设置日期 · 打开应用时提醒"
    val dateText = date.format(DateTimeFormatter.ofPattern("yyyy年M月d日 EEEE"))
    val timeText = schedule.startTime?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: "12:00（默认）"
    return "$dateText  $timeText"
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
    var title by remember(initial?.id) { mutableStateOf(initial?.title.orEmpty()) }
    var location by remember(initial?.id) { mutableStateOf(initial?.location.orEmpty()) }
    var notes by remember(initial?.id) { mutableStateOf(initial?.notes.orEmpty()) }
    var dateEnabled by remember(initial?.id) { mutableStateOf(initial?.date != null) }
    var selectedDate by remember(initial?.id) { mutableStateOf(initial?.date ?: LocalDate.now()) }
    var timeEnabled by remember(initial?.id) { mutableStateOf(initial?.startTime != null) }
    var selectedTime by remember(initial?.id) { mutableStateOf(initial?.startTime ?: LocalTime.NOON) }
    var urgency by remember(initial?.id) { mutableStateOf(initial?.urgency ?: Urgency.NORMAL) }

    val dateDialog = remember(selectedDate) {
        DatePickerDialog(
            context,
            { _, year, month, day -> selectedDate = LocalDate.of(year, month + 1, day) },
            selectedDate.year,
            selectedDate.monthValue - 1,
            selectedDate.dayOfMonth,
        )
    }
    val timeDialog = remember(selectedTime) {
        TimePickerDialog(
            context,
            { _, hour, minute -> selectedTime = LocalTime.of(hour, minute) },
            selectedTime.hour,
            selectedTime.minute,
            true,
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(contentPadding)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            if (initial == null) "手动添加日程" else "编辑日程",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "日期、时间和地点均可不填。只有日期时，日程按当天中午 12:00 提醒。",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("标题（必填）") },
            singleLine = true,
        )
        OutlinedTextField(
            value = location,
            onValueChange = { location = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("地点（选填）") },
            singleLine = true,
        )

        SettingSwitchRow(
            title = "设置日期",
            checked = dateEnabled,
            onCheckedChange = {
                dateEnabled = it
                if (!it) timeEnabled = false
            },
        )
        if (dateEnabled) {
            OutlinedButton(onClick = dateDialog::show, modifier = Modifier.fillMaxWidth()) {
                Text(selectedDate.format(DateTimeFormatter.ofPattern("yyyy年M月d日")))
            }
            SettingSwitchRow("设置具体时间", timeEnabled) { timeEnabled = it }
            if (timeEnabled) {
                OutlinedButton(onClick = timeDialog::show, modifier = Modifier.fillMaxWidth()) {
                    Text(selectedTime.format(DateTimeFormatter.ofPattern("HH:mm")))
                }
            }
        }

        Text("紧急程度", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Urgency.entries.forEach { option ->
                FilterChip(
                    selected = urgency == option,
                    onClick = { urgency = option },
                    label = { Text(option.label) },
                )
            }
        }
        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            modifier = Modifier.fillMaxWidth().height(130.dp),
            label = { Text("备注（选填）") },
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
        ) {
            TextButton(onClick = onCancel) { Text("取消") }
            Button(
                enabled = title.isNotBlank(),
                onClick = {
                    onSave(
                        (initial ?: Schedule(title = title)).copy(
                            title = title.trim(),
                            location = location.trim(),
                            notes = notes.trim(),
                            date = selectedDate.takeIf { dateEnabled },
                            startTime = selectedTime.withSecond(0).withNano(0).takeIf { dateEnabled && timeEnabled },
                            urgency = urgency,
                        )
                    )
                },
            ) { Text("保存日程") }
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun SettingSwitchRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun SettingsScreen(
    contentPadding: PaddingValues,
    state: AppUiState,
    onDarkModeChanged: (Boolean) -> Unit,
    onReminderMinutesChanged: (Int) -> Unit,
) {
    var minutesText by remember(state.reminderLeadMinutes) {
        mutableStateOf(state.reminderLeadMinutes.toString())
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("设置", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("外观", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                SettingSwitchRow("夜间主题", state.darkMode, onDarkModeChanged)
            }
        }
        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("提醒时间", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    "日程开始前多少分钟提醒。新日程默认提前 30 分钟。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = minutesText,
                    onValueChange = { value -> minutesText = value.filter(Char::isDigit).take(6) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("提前分钟数") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0 to "准时", 30 to "30 分钟", 60 to "1 小时", 1440 to "1 天").forEach { (value, label) ->
                        FilterChip(
                            selected = minutesText.toIntOrNull() == value,
                            onClick = { minutesText = value.toString() },
                            label = { Text(label) },
                        )
                    }
                }
                Button(
                    onClick = { minutesText.toIntOrNull()?.let(onReminderMinutesChanged) },
                    enabled = minutesText.toIntOrNull() != null,
                    modifier = Modifier.align(Alignment.End),
                ) { Text("应用提醒设置") }
            }
        }
        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("隐私与数据", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text("日程保存在本机，不上传云端。卸载应用前请确认不再需要本地日程。")
                HorizontalDivider()
                Text("AI备忘录 Android · 预览版 0.1.0", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
