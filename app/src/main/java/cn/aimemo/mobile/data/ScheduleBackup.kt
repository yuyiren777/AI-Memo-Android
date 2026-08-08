package cn.aimemo.mobile.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime

object ScheduleBackup {
    private const val FORMAT = "ai-memo-schedule-backup"
    private const val VERSION = 1
    private const val MAX_SCHEDULES = 5_000

    fun encode(schedules: List<Schedule>): String = JSONObject().apply {
        put("format", FORMAT)
        put("version", VERSION)
        put("exported_at", System.currentTimeMillis())
        put("schedules", JSONArray().apply {
            schedules.forEach { schedule ->
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
    }.toString(2)

    fun decode(content: String): List<Schedule> {
        val root = runCatching { JSONObject(content) }
            .getOrElse { error("这不是有效的 AI备忘录日程备份文件") }
        require(root.optString("format") == FORMAT) { "请选择由 AI备忘录导出的日程备份文件" }
        require(root.optInt("version", 0) in 1..VERSION) { "该备份版本暂不受支持，请升级应用后重试" }
        val items = root.optJSONArray("schedules") ?: error("备份文件中没有日程数据")
        require(items.length() <= MAX_SCHEDULES) { "单次最多导入 $MAX_SCHEDULES 条日程" }
        return buildList(items.length()) {
            for (index in 0 until items.length()) {
                val item = items.optJSONObject(index) ?: error("第 ${index + 1} 条日程格式不正确")
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
    }

    private fun JSONObject.putNullable(key: String, value: String?) {
        put(key, value ?: JSONObject.NULL)
    }

    private fun JSONObject.optionalString(key: String): String? =
        if (!has(key) || isNull(key)) null else optString(key).trim().takeIf(String::isNotEmpty)
}
