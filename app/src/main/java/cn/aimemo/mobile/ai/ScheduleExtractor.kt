package cn.aimemo.mobile.ai

import cn.aimemo.mobile.data.Schedule
import cn.aimemo.mobile.data.Urgency
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeParseException

object ScheduleExtractor {
    fun prompt(today: LocalDate = LocalDate.now()): String = """
        你是日程提取助手。今天是 $today。请从输入中提取所有日程、待办、通知、约会、考试和截止事项。
        自动纠正有把握的同音字和错别字。只返回严格 JSON 数组，不要 Markdown：
        [{"title":"标题","description":"备注或空字符串","date":"YYYY-MM-DD或null","date_year_explicit":true或false,"start_time":"HH:mm或null","end_time":"HH:mm或null","location":"地点或null","repeat":"none/daily/weekly:1/monthly:15","urgency":"normal/important/urgent"}]
        日期规则：相对日期以今天为基准；原文只有月日而没有年份时 date_year_explicit 必须为 false，并选择今天起最近的未来日期，跨年时使用下一年；完全没有日期就填 null。只有日期没有时分时 start_time 填 null，应用将按中午12:00处理。不要因为措辞不像标准日程就返回空数组，凡是用户希望记住的事项都应提取。
        图片输入规则：先逐区域完整阅读图片中的所有可见文字，再提取日程。课程表、考试通知、群聊通知、海报、表格和手写截图都可能包含日程；只要存在可执行、需记住或有时间线索的事项，就至少返回一项。只有图片确实没有可辨认内容时才允许返回空数组。
    """.trimIndent()

    fun parse(response: String, fallbackText: String? = null): List<Schedule> {
        val parsed = parseStructured(response)
        if (parsed.isNotEmpty()) return parsed
        val fallback = fallbackText?.trim().orEmpty()
        if (fallback.isBlank()) return listOf(Schedule(title = "图片中的待办事项", notes = "AI 未能完整提取，请点击编辑补充内容。"))
        return listOf(
            Schedule(
                title = fallback.lineSequence().firstOrNull { it.isNotBlank() }?.take(80) ?: "待办事项",
                notes = fallback,
            )
        )
    }

    fun parseStructured(response: String): List<Schedule> {
        val cleaned = response.trim()
            .removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        return jsonItems(cleaned).mapNotNull(::parseItem)
    }

    private fun jsonItems(cleaned: String): List<JSONObject> {
        runCatching {
            val start = cleaned.indexOf('[')
            val end = cleaned.lastIndexOf(']')
            if (start >= 0 && end > start) JSONArray(cleaned.substring(start, end + 1)) else null
        }.getOrNull()?.let { array ->
            return (0 until array.length()).mapNotNull(array::optJSONObject)
        }
        runCatching { JSONObject(cleaned) }.getOrNull()?.let { root ->
            root.optJSONArray("schedules")?.let { array ->
                return (0 until array.length()).mapNotNull(array::optJSONObject)
            }
        }

        // Recover complete objects when a long JSON response was truncated
        // before the closing array bracket.
        val objects = mutableListOf<JSONObject>()
        var depth = 0
        var start = -1
        var inString = false
        var escaped = false
        cleaned.forEachIndexed { index, char ->
            if (inString) {
                when {
                    escaped -> escaped = false
                    char == '\\' -> escaped = true
                    char == '"' -> inString = false
                }
            } else {
                when (char) {
                    '"' -> inString = true
                    '{' -> {
                        if (depth == 0) start = index
                        depth++
                    }
                    '}' -> if (depth > 0) {
                        depth--
                        if (depth == 0 && start >= 0) {
                            runCatching { JSONObject(cleaned.substring(start, index + 1)) }
                                .getOrNull()?.let(objects::add)
                            start = -1
                        }
                    }
                }
            }
        }
        return objects
    }

    private fun parseItem(item: JSONObject): Schedule? {
        val title = listOf("title", "name", "event", "task").firstNotNullOfOrNull { key ->
            item.optString(key).cleanNull().takeIf(String::isNotBlank)
        } ?: return null
        val date = parseDate(
            item.optString("date").cleanNull(),
            item.optBoolean("date_year_explicit").takeIf { item.has("date_year_explicit") },
        )
        return Schedule(
            title = title,
            notes = item.optString("description").cleanNull().ifBlank {
                item.optString("notes").cleanNull()
            },
            location = item.optString("location").cleanNull(),
            date = date,
            startTime = parseTime(item.optString("start_time").cleanNull()),
            endTime = parseTime(item.optString("end_time").cleanNull()),
            repeatRule = item.optString("repeat", "none").cleanNull().ifBlank { "none" },
            urgency = when (item.optString("urgency")) {
                "urgent" -> Urgency.URGENT
                "important" -> Urgency.IMPORTANT
                else -> Urgency.NORMAL
            },
        )
    }

    private fun parseDate(value: String, yearExplicit: Boolean?): LocalDate? {
        if (value.isBlank()) return null
        val parsed = try { LocalDate.parse(value) } catch (_: DateTimeParseException) { return null }
        val today = LocalDate.now()
        if (yearExplicit == true || (yearExplicit == null && parsed.year >= today.year)) return parsed
        val thisYear = runCatching { LocalDate.of(today.year, parsed.month, parsed.dayOfMonth) }.getOrNull()
        if (thisYear != null && !thisYear.isBefore(today)) return thisYear
        return runCatching { LocalDate.of(today.year + 1, parsed.month, parsed.dayOfMonth) }.getOrNull()
    }

    private fun parseTime(value: String): LocalTime? = if (value.isBlank()) null else runCatching {
        LocalTime.parse(if (value.length == 5) value else value.take(5))
    }.getOrNull()

    private fun String.cleanNull(): String = trim().takeUnless {
        it.equals("null", true) || it.equals("none", true)
    }.orEmpty()
}
