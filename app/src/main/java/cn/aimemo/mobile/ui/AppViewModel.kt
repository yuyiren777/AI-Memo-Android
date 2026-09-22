package cn.aimemo.mobile.ui

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cn.aimemo.mobile.AiMemoApplication
import cn.aimemo.mobile.ai.AccountClassificationResult
import cn.aimemo.mobile.ai.AccountExtractor
import cn.aimemo.mobile.ai.ScheduleExtractor
import cn.aimemo.mobile.ai.AiProvider
import cn.aimemo.mobile.ai.AiTimeoutException
import cn.aimemo.mobile.ai.ZhipuAiClient
import cn.aimemo.mobile.data.AccountEntry
import cn.aimemo.mobile.data.AccountEntryType
import cn.aimemo.mobile.data.AccountingSummary
import cn.aimemo.mobile.data.BudgetSettings
import cn.aimemo.mobile.data.ReminderLog
import cn.aimemo.mobile.data.Schedule
import cn.aimemo.mobile.data.SecureBackupContents
import cn.aimemo.mobile.data.formatMoney
import java.util.Collections
import java.time.YearMonth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AppUiState(
    val schedules: List<Schedule> = emptyList(),
    val accountEntries: List<AccountEntry> = emptyList(),
    val budgetSettings: BudgetSettings = BudgetSettings(),
    val captureProtectionEnabled: Boolean = true,
    val hasCaptureProtectionPassword: Boolean = false,
    val customExpenseCategories: Set<String> = emptySet(),
    val customIncomeCategories: Set<String> = emptySet(),
    val reminderLogs: List<ReminderLog> = emptyList(),
    val unreadReminderCount: Int = 0,
    val loading: Boolean = true,
    val recognizing: Boolean = false,
    val recognitionStatus: String = "",
    val classifyingAccount: Boolean = false,
    val accountClassificationStatus: String = "",
    val savingAccountEntries: Boolean = false,
    val analyzingFinancial: Boolean = false,
    val financialAnalysisPeriodKey: String? = null,
    val financialAnalysis: String = "",
    val recognized: List<Schedule> = emptyList(),
    val themeMode: String = "soft",
    val finalReminderMinutes: Int = 30,
    val firstReminderMinutes: Int? = null,
    val secondReminderMinutes: Int? = null,
    val aiProvider: String = "zhipu",
    val hasApiKey: Boolean = false,
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
    private val accountingRepository = app.accountingRepository
    private val aiClient = ZhipuAiClient(app.preferences)
    private val _uiState = MutableStateFlow(readPreferences())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()
    private var lastRefreshRequestedAt = Long.MIN_VALUE
    private val sensitiveJobs = Collections.synchronizedSet(mutableSetOf<Job>())

    init {
        viewModelScope.launch {
            repository.schedules.collect { schedules ->
                _uiState.value = _uiState.value.copy(schedules = schedules, loading = false)
            }
        }
        viewModelScope.launch {
            accountingRepository.entries.collect { entries ->
                _uiState.value = _uiState.value.copy(accountEntries = entries)
            }
        }
        viewModelScope.launch {
            app.reminderEvents.collect {
                runCatching { repository.reminderLogs() }
                    .onSuccess { updateReminderLogs(it) }
            }
        }
        refresh()
    }

    private fun readPreferences() = AppUiState(
        themeMode = app.preferences.themeMode,
        finalReminderMinutes = app.preferences.finalReminderMinutes,
        firstReminderMinutes = app.preferences.firstReminderMinutes,
        secondReminderMinutes = app.preferences.secondReminderMinutes,
        aiProvider = app.preferences.aiProvider,
        hasApiKey = app.preferences.hasApiKey,
        modelMode = app.preferences.modelMode,
        unifiedModel = app.preferences.unifiedModel,
        textModel = app.preferences.textModel,
        imageModel = app.preferences.imageModel,
        customExpenseCategories = app.preferences.customExpenseCategories,
        customIncomeCategories = app.preferences.customIncomeCategories,
        budgetSettings = app.preferences.budgetSettings,
        captureProtectionEnabled = app.preferences.captureProtectionEnabled,
        hasCaptureProtectionPassword = app.preferences.hasCaptureProtectionPassword,
    )

    fun refresh() {
        val now = SystemClock.elapsedRealtime()
        if (lastRefreshRequestedAt != Long.MIN_VALUE && now - lastRefreshRequestedAt < 2_000L) return
        lastRefreshRequestedAt = now
        viewModelScope.launch {
            runCatching {
                repository.refresh()
                accountingRepository.refresh()
                repository.reminderLogs()
            }.onSuccess { logs ->
                updateReminderLogs(logs, loading = false)
            }.onFailure(::showError)
        }
    }

    fun clearSensitiveMemory() {
        synchronized(sensitiveJobs) { sensitiveJobs.toList() }.forEach(Job::cancel)
        lastRefreshRequestedAt = Long.MIN_VALUE
        repository.clearMemory()
        accountingRepository.clearMemory()
        _uiState.value = _uiState.value.copy(
            schedules = emptyList(),
            accountEntries = emptyList(),
            reminderLogs = emptyList(),
            recognized = emptyList(),
            financialAnalysisPeriodKey = null,
            financialAnalysis = "",
            recognizing = false,
            recognitionStatus = "",
            classifyingAccount = false,
            accountClassificationStatus = "",
            analyzingFinancial = false,
            loading = true,
        )
    }

    private fun launchSensitive(block: suspend CoroutineScope.() -> Unit) {
        val job = viewModelScope.launch(start = CoroutineStart.LAZY, block = block)
        sensitiveJobs += job
        job.invokeOnCompletion { sensitiveJobs -= job }
        job.start()
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
        launchSensitive {
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
                        }.onFailure {
                            if (it is CancellationException) throw it
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
                    if (it is CancellationException) throw it
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
        launchSensitive {
            runCatching { block() }
                .onSuccess { schedules ->
                    _uiState.value = _uiState.value.copy(
                        recognizing = false,
                        recognitionStatus = "",
                        recognized = schedules,
                    )
                }.onFailure {
                    if (it is CancellationException) return@onFailure
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
                updateReminderLogs(logs)
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
                updateReminderLogs(
                    logs,
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

    fun importBackup(backup: SecureBackupContents) {
        viewModelScope.launch {
            runCatching {
                repository.refresh()
                accountingRepository.refresh()
                val existingScheduleKeys = repository.schedules.value.map(::scheduleImportKey).toMutableSet()
                val uniqueSchedules = backup.schedules.filter { existingScheduleKeys.add(scheduleImportKey(it)) }
                val existingAccountKeys = accountingRepository.entries.value.map(::accountEntryImportKey).toMutableSet()
                val uniqueEntries = backup.accountEntries.filter { existingAccountKeys.add(accountEntryImportKey(it)) }
                uniqueSchedules.forEach { repository.save(it.copy(id = 0L)) }
                if (uniqueEntries.isNotEmpty()) {
                    accountingRepository.saveAll(uniqueEntries.map { it.copy(id = 0L) })
                }
                app.preferences.budgetSettings = backup.budgetSettings
                uniqueSchedules.size to uniqueEntries.size
            }.onSuccess { (scheduleCount, entryCount) ->
                _uiState.value = _uiState.value.copy(
                    budgetSettings = backup.budgetSettings,
                    message = "备份恢复完成：$scheduleCount 条日程、$entryCount 笔账目，预算设置已恢复",
                )
            }.onFailure(::showError)
        }
    }

    fun deleteReminderLog(id: Long) {
        viewModelScope.launch {
            runCatching {
                repository.deleteReminderLog(id)
                repository.reminderLogs()
            }.onSuccess { updateReminderLogs(it) }.onFailure(::showError)
        }
    }

    fun markReminderLogsSeen() {
        if (_uiState.value.unreadReminderCount == 0) return
        viewModelScope.launch {
            runCatching {
                repository.markReminderLogsSeen()
                repository.reminderLogs()
            }.onSuccess { updateReminderLogs(it) }.onFailure(::showError)
        }
    }

    fun saveAccountEntry(entry: AccountEntry, onSaved: () -> Unit = {}) {
        viewModelScope.launch {
            runCatching { accountingRepository.save(entry) }
                .onSuccess { onSaved() }
                .onFailure(::showError)
        }
    }

    fun saveAccountEntries(entries: List<AccountEntry>, onSaved: () -> Unit = {}) {
        if (_uiState.value.savingAccountEntries) return
        _uiState.value = _uiState.value.copy(savingAccountEntries = true, error = null)
        viewModelScope.launch {
            runCatching { accountingRepository.saveAll(entries) }
                .onSuccess {
                    _uiState.value = _uiState.value.copy(savingAccountEntries = false)
                    onSaved()
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(savingAccountEntries = false)
                    showError(it)
                }
        }
    }

    fun classifyAccountText(
        description: String,
        expenseCategories: List<String>,
        incomeCategories: List<String>,
        occurredAt: Long,
        onAutoSaved: () -> Unit,
        onNeedsReview: (List<AccountClassificationResult>) -> Unit,
    ) {
        val normalized = description.trim()
        if (normalized.isBlank()) return showError(IllegalArgumentException("请先输入一段收入或支出描述"))
        classifyAccounts(
            initialStatus = "正在判断收入/支出并匹配分类…",
            expenseCategories = expenseCategories,
            incomeCategories = incomeCategories,
            occurredAt = occurredAt,
            onAutoSaved = onAutoSaved,
            onNeedsReview = onNeedsReview,
        ) {
            AccountExtractor.parse(
                aiClient.classifyAccount(normalized, expenseCategories, incomeCategories)
            )
        }
    }

    fun classifyAccountImages(
        images: List<Pair<ByteArray, String>>,
        expenseCategories: List<String>,
        incomeCategories: List<String>,
        occurredAt: Long,
        onAutoSaved: () -> Unit,
        onNeedsReview: (List<AccountClassificationResult>) -> Unit,
        onFailed: (String) -> Unit,
    ) {
        if (images.isEmpty()) {
            val error = IllegalArgumentException("请先选择购物清单或票据图片")
            onFailed(error.message.orEmpty())
            return showError(error)
        }
        if (images.size > MAX_ACCOUNT_IMAGES) {
            val error = IllegalArgumentException("一次最多识别 $MAX_ACCOUNT_IMAGES 张图片")
            onFailed(error.message.orEmpty())
            return showError(error)
        }
        classifyAccounts(
            initialStatus = "准备识别 ${images.size} 张记账图片…",
            expenseCategories = expenseCategories,
            incomeCategories = incomeCategories,
            occurredAt = occurredAt,
            onAutoSaved = onAutoSaved,
            onNeedsReview = onNeedsReview,
            onFailed = onFailed,
        ) {
            val merged = mutableListOf<AccountClassificationResult>()
            images.forEachIndexed { index, (bytes, mimeType) ->
                _uiState.value = _uiState.value.copy(
                    accountClassificationStatus =
                        "正在识别第 ${index + 1}/${images.size} 张图片中的商品与金额…",
                )
                var parsed = AccountExtractor.parse(
                    aiClient.classifyAccountImage(
                        bytes,
                        mimeType,
                        expenseCategories,
                        incomeCategories,
                    )
                )
                if (!parsed.hasUsefulAccountContent()) {
                    _uiState.value = _uiState.value.copy(
                        accountClassificationStatus =
                            "第 ${index + 1}/${images.size} 张首次未提取到账目，正在放大复查…",
                    )
                    parsed = AccountExtractor.parse(
                        aiClient.classifyAccountImage(
                            bytes,
                            mimeType,
                            expenseCategories,
                            incomeCategories,
                            recoveryAttempt = true,
                        )
                    )
                }
                if (!parsed.hasUsefulAccountContent()) {
                    throw IllegalStateException("第 ${index + 1} 张图片没有识别出有效账目，整批尚未写入")
                }
                merged += parsed
            }
            merged
        }
    }

    private fun classifyAccounts(
        initialStatus: String,
        expenseCategories: List<String>,
        incomeCategories: List<String>,
        occurredAt: Long,
        onAutoSaved: () -> Unit,
        onNeedsReview: (List<AccountClassificationResult>) -> Unit,
        onFailed: (String) -> Unit = {},
        recognize: suspend () -> List<AccountClassificationResult>,
    ) {
        if (_uiState.value.classifyingAccount) return
        _uiState.value = _uiState.value.copy(
            classifyingAccount = true,
            accountClassificationStatus = initialStatus,
            error = null,
            suggestModelSwitch = false,
        )
        launchSensitive {
            runCatching {
                val results = recognize().map { result ->
                    normalizeAccountClassification(result, expenseCategories, incomeCategories)
                }
                if (results.isEmpty()) throw IllegalStateException("模型没有识别出任何账目")
                if (results.all { it.isComplete() }) {
                    val createdAt = System.currentTimeMillis()
                    val entries = results.mapIndexed { index, result ->
                        AccountEntry(
                            type = requireNotNull(result.type),
                            amountCents = requireNotNull(result.amountCents),
                            category = requireNotNull(result.category),
                            note = requireNotNull(result.note).trim(),
                            occurredAt = occurredAt,
                            createdAt = createdAt + index,
                        )
                    }
                    accountingRepository.saveAll(entries)
                    AccountClassificationOutcome.AutoSaved(entries.size)
                } else {
                    AccountClassificationOutcome.NeedsReview(results)
                }
            }.onSuccess { outcome ->
                when (outcome) {
                    is AccountClassificationOutcome.AutoSaved -> {
                        _uiState.value = _uiState.value.copy(
                            classifyingAccount = false,
                            accountClassificationStatus = "",
                            message = "已自动写入 ${outcome.count} 笔账目，请在流水中逐笔检查。",
                        )
                        onAutoSaved()
                    }
                    is AccountClassificationOutcome.NeedsReview -> {
                        _uiState.value = _uiState.value.copy(
                            classifyingAccount = false,
                            accountClassificationStatus = "",
                        )
                        onNeedsReview(outcome.items)
                    }
                }
            }.onFailure {
                if (it is CancellationException) return@onFailure
                val message = it.message?.takeIf(String::isNotBlank) ?: "识别失败，请重试"
                _uiState.value = _uiState.value.copy(
                    classifyingAccount = false,
                    accountClassificationStatus = "识别失败：$message",
                )
                onFailed(message)
                showError(it)
            }
        }
    }

    fun addCustomAccountCategory(type: AccountEntryType, name: String) {
        val normalized = name.trim()
        if (normalized.isBlank()) return showError(IllegalArgumentException("分类名称不能为空"))
        if (normalized.length > 20) return showError(IllegalArgumentException("分类名称不能超过 20 个字"))

        when (type) {
            AccountEntryType.EXPENSE -> {
                val updated = app.preferences.customExpenseCategories + normalized
                app.preferences.customExpenseCategories = updated
                _uiState.value = _uiState.value.copy(customExpenseCategories = updated)
            }
            AccountEntryType.INCOME -> {
                val updated = app.preferences.customIncomeCategories + normalized
                app.preferences.customIncomeCategories = updated
                _uiState.value = _uiState.value.copy(customIncomeCategories = updated)
            }
        }
    }

    fun deleteCustomAccountCategory(type: AccountEntryType, name: String) {
        val normalized = name.trim()
        when (type) {
            AccountEntryType.EXPENSE -> {
                val updated = app.preferences.customExpenseCategories - normalized
                app.preferences.customExpenseCategories = updated
                _uiState.value = _uiState.value.copy(customExpenseCategories = updated)
            }
            AccountEntryType.INCOME -> {
                val updated = app.preferences.customIncomeCategories - normalized
                app.preferences.customIncomeCategories = updated
                _uiState.value = _uiState.value.copy(customIncomeCategories = updated)
            }
        }
    }

    fun deleteAccountEntry(entry: AccountEntry) {
        viewModelScope.launch {
            runCatching { accountingRepository.delete(entry) }
                .onFailure(::showError)
        }
    }

    fun deleteAccountEntries(entries: Collection<AccountEntry>) {
        if (entries.isEmpty()) return
        viewModelScope.launch {
            runCatching { accountingRepository.deleteAll(entries) }
                .onFailure(::showError)
        }
    }

    fun analyzeFinancialSummary(periodKey: String, periodLabel: String, summary: AccountingSummary) {
        if (_uiState.value.analyzingFinancial) return
        if (summary.incomeCents == 0L && summary.expenseCents == 0L) {
            return showError(IllegalArgumentException("当前周期没有可分析的收支数据"))
        }
        _uiState.value = _uiState.value.copy(
            analyzingFinancial = true,
            financialAnalysisPeriodKey = periodKey,
            financialAnalysis = "",
            error = null,
        )
        launchSensitive {
            val streamed = StringBuilder()
            runCatching {
                aiClient.streamFinancialAdvice(financialAdvicePrompt(periodLabel, summary)) { delta ->
                    streamed.append(delta)
                    _uiState.value = _uiState.value.copy(financialAnalysis = streamed.toString())
                }
            }.onSuccess {
                _uiState.value = _uiState.value.copy(analyzingFinancial = false)
            }.onFailure {
                if (it is CancellationException) return@onFailure
                _uiState.value = _uiState.value.copy(analyzingFinancial = false)
                showError(it)
            }
        }
    }

    fun setThemeMode(mode: String) {
        val normalized = mode.takeIf { it == "light" || it == "soft" || it == "dark" } ?: "soft"
        app.preferences.themeMode = normalized
        _uiState.value = _uiState.value.copy(themeMode = normalized)
    }

    fun saveMonthlyBudget(month: YearMonth, budgetCents: Long) {
        val budgets = _uiState.value.budgetSettings.withMonthlyBudget(month, budgetCents)
        app.preferences.budgetSettings = budgets
        _uiState.value = _uiState.value.copy(
            budgetSettings = budgets,
            message = if (budgetCents > 0L) {
                "${month.year}年${month.monthValue}月预算已保存"
            } else {
                "${month.year}年${month.monthValue}月预算已清除"
            },
        )
    }

    fun saveYearlyBudget(year: Int, budgetCents: Long) {
        val budgets = _uiState.value.budgetSettings.withYearlyBudget(year, budgetCents)
        app.preferences.budgetSettings = budgets
        _uiState.value = _uiState.value.copy(
            budgetSettings = budgets,
            message = if (budgetCents > 0L) "$year 年预算已保存" else "$year 年预算已清除",
        )
    }

    fun enableCaptureProtection() {
        app.preferences.enableCaptureProtection()
        _uiState.value = _uiState.value.copy(
            captureProtectionEnabled = true,
            message = "截图与录屏保护已开启",
        )
    }

    suspend fun disableCaptureProtection(password: CharArray, confirmation: CharArray?): String? {
        val error = withContext(Dispatchers.Default) {
            runCatching { app.preferences.disableCaptureProtection(password, confirmation) }
                .exceptionOrNull()
                ?.message
        }
        if (error == null) {
            _uiState.value = _uiState.value.copy(
                captureProtectionEnabled = false,
                hasCaptureProtectionPassword = true,
                message = "截图与录屏保护已关闭",
            )
        }
        return error
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

    fun saveModelSettings(provider: String, mode: String, apiKey: String, unified: String, text: String, image: String) {
        app.preferences.aiProvider = provider
        app.preferences.modelMode = mode
        if (apiKey.isNotBlank()) app.preferences.activeApiKey = apiKey
        app.preferences.unifiedModel = unified
        app.preferences.textModel = text
        app.preferences.imageModel = image
        val usesDefaultModel = if (mode == "unified") {
            unified.isBlank()
        } else {
            text.isBlank() || image.isBlank()
        }
        _uiState.value = _uiState.value.copy(
            aiProvider = app.preferences.aiProvider,
            modelMode = mode, hasApiKey = app.preferences.hasApiKey, unifiedModel = unified,
            textModel = text,
            imageModel = image,
            message = if (usesDefaultModel) {
                "当前使用${AiProvider.fromId(app.preferences.aiProvider).label}提供的默认模型。费用和限额请以服务商页面为准。"
            } else {
                "模型设置已保存"
            },
        )
    }

    fun switchAiProvider(provider: String) {
        app.preferences.aiProvider = provider
        _uiState.value = _uiState.value.copy(
            aiProvider = app.preferences.aiProvider,
            hasApiKey = app.preferences.hasApiKey,
            unifiedModel = app.preferences.unifiedModel,
            textModel = app.preferences.textModel,
            imageModel = app.preferences.imageModel,
            message = null,
            error = null,
        )
    }

    fun clearApiKey() {
        app.preferences.activeApiKey = ""
        _uiState.value = _uiState.value.copy(hasApiKey = false, message = "API Key 已从本机清除")
    }

    fun testConnection() {
        _uiState.value = _uiState.value.copy(
            recognizing = true,
            recognitionStatus = "正在测试连接…",
            message = null,
            error = null,
            suggestModelSwitch = false,
        )
        launchSensitive {
            runCatching { aiClient.testConnection() }
                .onSuccess { _uiState.value = _uiState.value.copy(recognizing = false, recognitionStatus = "", message = it) }
                .onFailure {
                    if (it is CancellationException) return@onFailure
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

    private fun updateReminderLogs(
        logs: List<ReminderLog>,
        loading: Boolean = _uiState.value.loading,
        message: String? = _uiState.value.message,
    ) {
        _uiState.value = _uiState.value.copy(
            reminderLogs = logs,
            unreadReminderCount = logs.count { !it.seen },
            loading = loading,
            message = message,
        )
    }
}

private sealed interface AccountClassificationOutcome {
    data class AutoSaved(val count: Int) : AccountClassificationOutcome
    data class NeedsReview(val items: List<AccountClassificationResult>) : AccountClassificationOutcome
}

private fun normalizeAccountClassification(
    result: AccountClassificationResult,
    expenseCategories: List<String>,
    incomeCategories: List<String>,
): AccountClassificationResult {
    val candidates = when (result.type) {
        AccountEntryType.EXPENSE -> expenseCategories
        AccountEntryType.INCOME -> incomeCategories
        null -> emptyList()
    }
    val matchedCategory = result.category?.let { modelCategory ->
        candidates.firstOrNull { it.equals(modelCategory.trim(), ignoreCase = true) }
    }
    return result.copy(
        category = matchedCategory,
        suggestedCategory = result.suggestedCategory ?: result.category.takeIf { matchedCategory == null },
        note = result.note?.trim()?.takeIf(String::isNotBlank),
    )
}

private fun AccountClassificationResult.isComplete(): Boolean =
    type != null && amountCents != null && amountCents > 0 && category != null && !note.isNullOrBlank()

private fun List<AccountClassificationResult>.hasUsefulAccountContent(): Boolean = any { result ->
    result.type != null ||
        result.amountCents != null ||
        !result.category.isNullOrBlank() ||
        !result.suggestedCategory.isNullOrBlank() ||
        !result.note.isNullOrBlank()
}

private fun financialAdvicePrompt(periodLabel: String, summary: AccountingSummary): String {
    fun categories(items: List<cn.aimemo.mobile.data.AccountCategoryTotal>): String =
        items.joinToString("；") { "${it.category} ${formatMoney(it.amountCents)}" }.ifBlank { "无" }

    return """
        你是谨慎、务实的个人消费分析助手。请只根据下面提供的本地记账汇总，分析收入结构、支出结构和结余，并给出可执行的消费改善建议。
        不要虚构用户的职业、家庭、债务、预算或资产情况；数据不足时明确说明。不要提供具体证券、基金或加密资产买卖建议。

        周期：$periodLabel
        总收入：${formatMoney(summary.incomeCents)}
        收入分类：${categories(summary.incomeCategories)}
        总支出：${formatMoney(summary.expenseCents)}
        支出分类：${categories(summary.expenseCategories)}
        结余：${formatMoney(summary.balanceCents)}

        请使用简洁中文纯文本输出，不要 Markdown 表格。依次给出：
        1. 收支与结余概况；
        2. 最值得关注的收入和支出分类；
        3. 三到五条具体、克制、可执行的消费建议；
        4. 一句风险提示，说明仅凭该周期汇总存在的局限。
    """.trimIndent()
}

private fun isEmptyModelResult(response: String): Boolean {
    val normalized = response.trim()
        .removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
    return normalized.isBlank() || normalized == "[]" || normalized == "{}"
}

private const val MAX_ACCOUNT_IMAGES = 4

private fun scheduleImportKey(schedule: Schedule) = listOf(
    schedule.title.trim(), schedule.notes.trim(), schedule.location.trim(), schedule.date,
    schedule.startTime, schedule.endTime, schedule.repeatRule, schedule.urgency, schedule.completed,
).joinToString("\u001F")

private fun accountEntryImportKey(entry: AccountEntry) = listOf(
    entry.type, entry.amountCents, entry.category.trim(), entry.note.trim(), entry.occurredAt,
).joinToString("\u001F")
