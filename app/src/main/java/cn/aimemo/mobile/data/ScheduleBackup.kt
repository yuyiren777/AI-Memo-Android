package cn.aimemo.mobile.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

data class SecureBackupContents(
    val schedules: List<Schedule>,
    val accountEntries: List<AccountEntry>,
    val budgetSettings: BudgetSettings,
)

internal object ScheduleBackup {
    private const val FORMAT = "ai-memo-schedule-backup-payload"
    private const val VERSION = 3
    private const val MAX_SCHEDULES = 5_000
    private const val MAX_ACCOUNT_ENTRIES = 20_000
    private const val MAX_MONTHLY_BUDGETS = 1_200
    private const val MAX_YEARLY_BUDGETS = 100

    fun encodePayload(contents: SecureBackupContents): String {
        require(contents.schedules.size <= MAX_SCHEDULES) { "单次最多导出 $MAX_SCHEDULES 条日程" }
        require(contents.accountEntries.size <= MAX_ACCOUNT_ENTRIES) { "单次最多导出 $MAX_ACCOUNT_ENTRIES 条账目" }
        require(contents.budgetSettings.monthlyBudgetsCents.size <= MAX_MONTHLY_BUDGETS) { "月预算记录过多" }
        require(contents.budgetSettings.yearlyBudgetsCents.size <= MAX_YEARLY_BUDGETS) { "年预算记录过多" }
        return JSONObject().apply {
            put("format", FORMAT)
            put("version", VERSION)
            put("exported_at", System.currentTimeMillis())
            put("schedules", JSONArray().apply {
                contents.schedules.forEach { schedule ->
                    put(JSONObject().apply {
                        put("title", schedule.title)
                        put("notes", schedule.notes)
                        put("location", schedule.location)
                        putNullable("date", schedule.date?.toString())
                        putNullable("start_time", schedule.startTime?.toString())
                        putNullable("end_time", schedule.endTime?.toString())
                        put("repeat_rule", schedule.repeatRule)
                        put("urgency", schedule.urgency.name)
                        put("completed", schedule.completed)
                        put("created_at", schedule.createdAt)
                    })
                }
            })
            put("account_entries", JSONArray().apply {
                contents.accountEntries.forEach { entry ->
                    put(JSONObject().apply {
                        put("type", entry.type.value)
                        put("amount_cents", entry.amountCents)
                        put("category", entry.category)
                        put("note", entry.note)
                        put("occurred_at", entry.occurredAt)
                        put("created_at", entry.createdAt)
                    })
                }
            })
            put("budgets", JSONObject().apply {
                put("months", JSONObject().apply {
                    contents.budgetSettings.monthlyBudgetsCents.toSortedMap().forEach { (month, amount) ->
                        if (amount > 0L) put(month.toString(), amount)
                    }
                })
                put("years", JSONObject().apply {
                    contents.budgetSettings.yearlyBudgetsCents.toSortedMap().forEach { (year, amount) ->
                        if (year in 1..9999 && amount > 0L) put(year.toString(), amount)
                    }
                })
            })
        }.toString()
    }

    fun decodePayload(content: String): SecureBackupContents {
        val root = runCatching { JSONObject(content) }
            .getOrElse { error("这不是有效的 AI备忘录备份文件") }
        require(root.optString("format") == FORMAT) { "请选择由当前 AI备忘录导出的备份文件" }
        val version = root.optInt("version", 0)
        require(version in 2..VERSION) { "该备份版本暂不受支持，请升级应用后重试" }

        val schedulesJson = root.optJSONArray("schedules") ?: error("备份文件中没有日程数据")
        require(schedulesJson.length() <= MAX_SCHEDULES) { "单次最多导入 $MAX_SCHEDULES 条日程" }
        val schedules = buildList(schedulesJson.length()) {
            for (index in 0 until schedulesJson.length()) {
                val item = schedulesJson.optJSONObject(index) ?: error("第 ${index + 1} 条日程格式不正确")
                val title = item.optString("title").trim()
                require(title.isNotBlank()) { "第 ${index + 1} 条日程缺少标题" }
                add(
                    Schedule(
                        title = title,
                        notes = item.optString("notes").trim(),
                        location = item.optString("location").trim(),
                        date = item.optionalString("date")?.let(LocalDate::parse),
                        startTime = item.optionalString("start_time")?.let(LocalTime::parse),
                        endTime = item.optionalString("end_time")?.let(LocalTime::parse),
                        repeatRule = item.optString("repeat_rule", "none"),
                        urgency = runCatching { Urgency.valueOf(item.optString("urgency")) }
                            .getOrDefault(Urgency.NORMAL),
                        completed = item.optBoolean("completed", false),
                        createdAt = item.optLong("created_at", System.currentTimeMillis()),
                    ),
                )
            }
        }

        val entriesJson = root.optJSONArray("account_entries") ?: JSONArray()
        require(entriesJson.length() <= MAX_ACCOUNT_ENTRIES) { "单次最多导入 $MAX_ACCOUNT_ENTRIES 条账目" }
        val entries = buildList(entriesJson.length()) {
            for (index in 0 until entriesJson.length()) {
                val item = entriesJson.optJSONObject(index) ?: error("第 ${index + 1} 条账目格式不正确")
                val category = item.optString("category").trim()
                val amount = item.optLong("amount_cents", 0L)
                require(category.isNotBlank() && amount > 0L) { "第 ${index + 1} 条账目内容不完整" }
                add(
                    AccountEntry(
                        type = AccountEntryType.fromValue(item.optInt("type", AccountEntryType.EXPENSE.value)),
                        amountCents = amount,
                        category = category,
                        note = item.optString("note").trim(),
                        occurredAt = item.optLong("occurred_at", System.currentTimeMillis()),
                        createdAt = item.optLong("created_at", System.currentTimeMillis()),
                    ),
                )
            }
        }

        val budgets = root.optJSONObject("budgets")
        return SecureBackupContents(
            schedules = schedules,
            accountEntries = entries,
            budgetSettings = decodeBudgetSettings(budgets, version),
        )
    }

    private fun decodeBudgetSettings(budgets: JSONObject?, version: Int): BudgetSettings {
        if (budgets == null) return BudgetSettings()
        if (version == 2) {
            val currentMonth = YearMonth.now()
            val monthly = budgets.optLong("monthly_cents", 0L).coerceAtLeast(0L)
            val yearly = budgets.optLong("yearly_cents", 0L).coerceAtLeast(0L)
            return BudgetSettings(
                monthlyBudgetsCents = if (monthly > 0L) mapOf(currentMonth to monthly) else emptyMap(),
                yearlyBudgetsCents = if (yearly > 0L) mapOf(currentMonth.year to yearly) else emptyMap(),
            )
        }

        val months = budgets.optJSONObject("months") ?: JSONObject()
        val years = budgets.optJSONObject("years") ?: JSONObject()
        require(months.length() <= MAX_MONTHLY_BUDGETS) { "备份中的月预算记录过多" }
        require(years.length() <= MAX_YEARLY_BUDGETS) { "备份中的年预算记录过多" }
        return BudgetSettings(
            monthlyBudgetsCents = buildMap {
                months.keys().forEach { key ->
                    val month = runCatching { YearMonth.parse(key) }
                        .getOrElse { error("备份中的月预算周期无效") }
                    val amount = months.optLong(key, -1L)
                    require(amount >= 0L) { "备份中的月预算金额无效" }
                    if (amount > 0L) put(month, amount)
                }
            },
            yearlyBudgetsCents = buildMap {
                years.keys().forEach { key ->
                    val year = key.toIntOrNull()
                    require(year != null && year in 1..9999) { "备份中的年预算周期无效" }
                    val amount = years.optLong(key, -1L)
                    require(amount >= 0L) { "备份中的年预算金额无效" }
                    if (amount > 0L) put(year, amount)
                }
            },
        )
    }

    private fun JSONObject.putNullable(key: String, value: String?) {
        put(key, value ?: JSONObject.NULL)
    }

    private fun JSONObject.optionalString(key: String): String? =
        if (!has(key) || isNull(key)) null else optString(key).trim().takeIf(String::isNotEmpty)
}
