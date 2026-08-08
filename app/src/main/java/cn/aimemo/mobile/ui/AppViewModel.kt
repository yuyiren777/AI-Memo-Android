package cn.aimemo.mobile.ui

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cn.aimemo.mobile.AiMemoApplication
import cn.aimemo.mobile.ai.ScheduleExtractor
import cn.aimemo.mobile.ai.AiTimeoutException
import cn.aimemo.mobile.ai.ZhipuAiClient
import cn.aimemo.mobile.data.ReminderLog
import cn.aimemo.mobile.data.Schedule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AppUiState(
    val schedules: List<Schedule> = emptyList(),
    val reminderLogs: List<ReminderLog> = emptyList(),
    val loading: Boolean = true,
    val recognizing: Boolean = false,
    val recognitionStatus: String = "",
    val recognized: List<Schedule> = emptyList(),
    val darkMode: Boolean = false,
    val finalReminderMinutes: Int = 30,
    val firstReminderMinutes: Int? = null,
    val secondReminderMinutes: Int? = null,
    val apiKey: String = "",
    val modelMode: String = "separate",
    val unifiedModel: String = "",
    val textModel: String = "",
    val imageModel: String = "",
    val message: String? = null,
    val error: String? = null,
    val suggestModelSwitch: Boolean = false,
)

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as AiMemoApplication
    private val repository = app.repository
    private val aiClient = ZhipuAiClient(app.preferences)
    private val _uiState = MutableStateFlow(readPreferences())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()
    private var lastRefreshRequestedAt = Long.MIN_VALUE

    init {
        viewModelScope.launch {
            repository.schedules.collect { schedules ->
                _uiState.value = _uiState.value.copy(schedules = schedules, loading = false)
            }
        }
        refresh()
    }

    private fun readPreferences() = AppUiState(
        darkMode = app.preferences.darkMode,
        finalReminderMinutes = app.preferences.finalReminderMinutes,
        firstReminderMinutes = app.preferences.firstReminderMinutes,
        secondReminderMinutes = app.preferences.secondReminderMinutes,
        apiKey = app.preferences.apiKey,
        modelMode = app.preferences.modelMode,
        unifiedModel = app.preferences.unifiedModel,
        textModel = app.preferences.textModel,
        imageModel = app.preferences.imageModel,
    )

    fun refresh() {
        val now = SystemClock.elapsedRealtime()
        if (lastRefreshRequestedAt != Long.MIN_VALUE && now - lastRefreshRequestedAt < 2_000L) return
        lastRefreshRequestedAt = now
        viewModelScope.launch {
            runCatching {
                repository.refresh()
                repository.reminderLogs()
            }.onSuccess { logs ->
                _uiState.value = _uiState.value.copy(reminderLogs = logs, loading = false)
            }.onFailure(::showError)
        }
    }

    fun save(schedule: Schedule, onSaved: () -> Unit = {}) {
        viewModelScope.launch {
            runCatching { repository.save(schedule) }
                .onSuccess { onSaved() }
                .onFailure(::showError)
        }
    }

    fun saveRecognized(onSaved: () -> Unit) {
        val items = _uiState.value.recognized
        viewModelScope.launch {
            runCatching { items.forEach { repository.save(it) } }
                .onSuccess {
                    _uiState.value = _uiState.value.copy(recognized = emptyList(), message = "已添加 ${items.size} 条日程")
                    onSaved()
                }.onFailure(::showError)
        }
    }

    fun updateRecognized(index: Int, schedule: Schedule) {
        _uiState.value = _uiState.value.copy(
            recognized = _uiState.value.recognized.mapIndexed { itemIndex, item ->
                if (itemIndex == index) schedule else item
            }
        )
    }

    fun removeRecognized(index: Int) {
        _uiState.value = _uiState.value.copy(
            recognized = _uiState.value.recognized.filterIndexed { itemIndex, _ -> itemIndex != index }
        )
    }

    fun recognizeText(text: String) = recognize("正在识别文字…") {
        ScheduleExtractor.parse(aiClient.extractText(text), fallbackText = text)
    }

    fun recognizeImages(images: List<Pair<ByteArray, String>>) {
        if (_uiState.value.recognizing || images.isEmpty()) return
        _uiState.value = _uiState.value.copy(
            recognizing = true,
            recognitionStatus = "准备识别 ${images.size} 张图片…",
            error = null,
            suggestModelSwitch = false,
        )
        viewModelScope.launch {
            val schedules = mutableListOf<Schedule>()
            val failures = mutableListOf<Throwable>()
            var timeout: AiTimeoutException? = null
            for ((index, image) in images.withIndex()) {
                val (bytes, mimeType) = image
                _uiState.value = _uiState.value.copy(
                    recognitionStatus = "正在识别第 ${index + 1}/${images.size} 张图片，限流时会自动重试…"
                )
                runCatching {
                    val firstResponse = aiClient.extractImage(bytes, mimeType)
                    var parsed = ScheduleExtractor.parseStructured(firstResponse)
                    var lastResponse = firstResponse
                    if (parsed.isEmpty()) {
                        _uiState.value = _uiState.value.copy(
                            recognitionStatus = "第 ${index + 1}/${images.size} 张首次未提取到日程，正在仔细复查…"
                        )
                        runCatching {
                            aiClient.extractImage(bytes, mimeType, recoveryAttempt = true)
                        }.getOrNull()?.let { recoveryResponse ->
                            lastResponse = recoveryResponse
                            parsed = ScheduleExtractor.parseStructured(recoveryResponse)
                        }
                    }
                    if (parsed.isNotEmpty()) parsed else ScheduleExtractor.parse(
                        lastResponse,
                        fallbackText = lastResponse.takeUnless(::isEmptyModelResult)
                            ?: "图片中的待办事项\nAI 未能完整识别，请点击编辑补充标题、日期和备注。",
                    )
                }.onSuccess { schedules.addAll(it) }.onFailure {
                    failures.add(it)
                    if (it is AiTimeoutException) timeout = it
                }
                if (timeout != null) break
            }
            _uiState.value = _uiState.value.copy(recognizing = false, recognitionStatus = "")
            when {
                timeout != null -> {
                    _uiState.value = _uiState.value.copy(recognized = schedules)
                    showError(timeout!!)
                }
                schedules.isNotEmpty() -> {
                    _uiState.value = _uiState.value.copy(
                        recognized = schedules,
                        message = failures.takeIf { it.isNotEmpty() }?.let {
                            "已完成 ${images.size - it.size} 张图片的识别，另有 ${it.size} 张未能识别。成功结果已保留，请检查后保存。"
                        },
                    )
                }
                failures.isNotEmpty() -> showError(failures.first())
                else -> showError(IllegalStateException("没有读取到可识别的图片"))
            }
        }
    }

    private fun recognize(status: String, block: suspend () -> List<Schedule>) {
        if (_uiState.value.recognizing) return
        _uiState.value = _uiState.value.copy(
            recognizing = true,
            recognitionStatus = status,
            error = null,
            suggestModelSwitch = false,
        )
        viewModelScope.launch {
            runCatching { block() }
                .onSuccess { schedules ->
                    _uiState.value = _uiState.value.copy(
                        recognizing = false,
                        recognitionStatus = "",
                        recognized = schedules,
                    )
                }.onFailure {
                    _uiState.value = _uiState.value.copy(recognizing = false, recognitionStatus = "")
                    showError(it)
                }
        }
    }

    fun delete(schedule: Schedule) {
        viewModelScope.launch {
            runCatching {
                repository.delete(schedule)
                repository.reminderLogs()
            }.onSuccess { logs ->
                _uiState.value = _uiState.value.copy(reminderLogs = logs)
            }.onFailure(::showError)
        }
    }

    fun setCompleted(schedule: Schedule, completed: Boolean) {
        viewModelScope.launch { runCatching { repository.setCompleted(schedule, completed) }.onFailure(::showError) }
    }

    fun deleteSchedules(schedules: List<Schedule>) {
        val targets = schedules.distinctBy(Schedule::id)
        if (targets.isEmpty()) return
        viewModelScope.launch {
            runCatching {
                repository.delete(targets)
                repository.reminderLogs()
            }.onSuccess { logs ->
                _uiState.value = _uiState.value.copy(
                    reminderLogs = logs,
                    message = "已删除 ${targets.size} 条日程",
                )
            }.onFailure(::showError)
        }
    }

    fun setSchedulesCompleted(schedules: List<Schedule>) {
        val targets = schedules.filterNot(Schedule::completed).distinctBy(Schedule::id)
        if (targets.isEmpty()) return
        viewModelScope.launch {
            runCatching { repository.setCompleted(targets, true) }
                .onSuccess {
                    _uiState.value = _uiState.value.copy(message = "已确认完成 ${targets.size} 条日程")
                }
                .onFailure(::showError)
        }
    }

    fun importSchedules(imported: List<Schedule>) {
        val existingKeys = _uiState.value.schedules.map(::scheduleImportKey).toMutableSet()
        val unique = imported.filter { existingKeys.add(scheduleImportKey(it)) }
        viewModelScope.launch {
            runCatching {
                unique.forEach { repository.save(it.copy(id = 0L)) }
            }.onSuccess {
                _uiState.value = _uiState.value.copy(
                    message = when {
                        unique.isEmpty() -> "没有导入新日程，备份中的内容已存在"
                        unique.size == imported.size -> "已导入 ${unique.size} 条日程，并重新设置提醒"
                        else -> "已导入 ${unique.size} 条日程，跳过 ${imported.size - unique.size} 条重复内容"
                    },
                )
            }.onFailure(::showError)
        }
    }

    fun deleteReminderLog(id: Long) {
        viewModelScope.launch {
            repository.deleteReminderLog(id)
            _uiState.value = _uiState.value.copy(reminderLogs = repository.reminderLogs())
        }
    }

    fun setDarkMode(enabled: Boolean) {
        app.preferences.darkMode = enabled
        _uiState.value = _uiState.value.copy(darkMode = enabled)
    }

    fun saveReminderSettings(final: Int, first: Int?, second: Int?) {
        if (first != null && first <= final) return showError(IllegalArgumentException("第一次提醒必须早于最后一次提醒"))
        if (second != null && second <= final) return showError(IllegalArgumentException("第二次提醒必须早于最后一次提醒"))
        if (first != null && second != null && first <= second) return showError(IllegalArgumentException("第一次提醒必须早于第二次提醒"))
        app.preferences.finalReminderMinutes = final
        app.preferences.firstReminderMinutes = first
        app.preferences.secondReminderMinutes = second
        _uiState.value = _uiState.value.copy(
            finalReminderMinutes = final,
            firstReminderMinutes = first,
            secondReminderMinutes = second,
            message = "提醒设置已应用，所有待办已重新计算",
        )
        viewModelScope.launch { repository.rescheduleAll() }
    }

    fun saveModelSettings(mode: String, apiKey: String, unified: String, text: String, image: String) {
        app.preferences.modelMode = mode
        app.preferences.apiKey = apiKey
        app.preferences.unifiedModel = unified
        app.preferences.textModel = text
        app.preferences.imageModel = image
        val usesDefaultModel = if (mode == "unified") {
            unified.isBlank()
        } else {
            text.isBlank() || image.isBlank()
        }
        _uiState.value = _uiState.value.copy(
            modelMode = mode, apiKey = apiKey, unifiedModel = unified,
            textModel = text,
            imageModel = image,
            message = if (usesDefaultModel) {
                "当前使用智谱提供的免费默认模型。免费模型的连接稳定性和识别能力可能有限，后续可以随时在这里填写其他兼容模型。"
            } else {
                "模型设置已保存"
            },
        )
    }

    fun testConnection() {
        _uiState.value = _uiState.value.copy(
            recognizing = true,
            recognitionStatus = "正在测试连接…",
            message = null,
            error = null,
            suggestModelSwitch = false,
        )
        viewModelScope.launch {
            runCatching { aiClient.testConnection() }
                .onSuccess { _uiState.value = _uiState.value.copy(recognizing = false, recognitionStatus = "", message = it) }
                .onFailure {
                    _uiState.value = _uiState.value.copy(recognizing = false, recognitionStatus = "")
                    showError(it)
                }
        }
    }

    private fun showError(error: Throwable) {
        val timedOut = error is AiTimeoutException || generateSequence(error) { it.cause }
            .any { cause ->
                cause is java.net.SocketTimeoutException ||
                    cause.message?.contains("timeout", ignoreCase = true) == true ||
                    cause.message?.contains("timed out", ignoreCase = true) == true ||
                    cause.message?.contains("连接超时") == true
            }
        _uiState.value = _uiState.value.copy(
            loading = false,
            error = error.message ?: "操作失败",
            suggestModelSwitch = timedOut,
        )
    }

    fun consumeNotice() {
        _uiState.value = _uiState.value.copy(error = null, message = null, suggestModelSwitch = false)
    }
}

private fun isEmptyModelResult(response: String): Boolean {
    val normalized = response.trim()
        .removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
    return normalized.isBlank() || normalized == "[]" || normalized == "{}"
}

private fun scheduleImportKey(schedule: Schedule) = listOf(
    schedule.title.trim(), schedule.notes.trim(), schedule.location.trim(), schedule.date,
    schedule.startTime, schedule.endTime, schedule.repeatRule, schedule.urgency, schedule.completed,
).joinToString("\u001F")
