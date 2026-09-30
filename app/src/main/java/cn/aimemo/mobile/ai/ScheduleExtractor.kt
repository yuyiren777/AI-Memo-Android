package cn.aimemo.mobile.ai

import cn.aimemo.mobile.data.Schedule
import cn.aimemo.mobile.data.Urgency
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeParseException

object ScheduleExtractor {
    private fun promptLegacy(today: LocalDate = LocalDate.now()): String = """
        你是日程提取助手。今天是 $today。请从输入中提取所有日程、待办、通知、约会、考试和截止事项。
        自动纠正有把握的同音字和错别字。只返回严格 JSON 数组，不要 Markdown：
        [{"title":"标题","description":"备注或空字符串","date":"YYYY-MM-DD或null","date_year_explicit":true或false,"start_time":"HH:mm或null","end_time":"HH:mm或null","location":"地点或null","repeat":"none/daily/weekly:1/monthly:15","urgency":"normal/important/urgent"}]
        日期规则：相对日期以今天为基准；原文只有月日而没有年份时 date_year_explicit 必须为 false，并选择今天起最近的未来日期，跨年时使用下一年；完全没有日期就填 null。只有日期没有时分时 start_time 填 null，应用将按中午12:00处理。不要因为措辞不像标准日程就返回空数组，凡是用户希望记住的事项都应提取。
        图片输入规则：先逐区域完整阅读图片中的所有可见文字，再提取日程。课程表、考试通知、群聊通知、海报、表格和手写截图都可能包含日程；只要存在可执行、需记住或有时间线索的事项，就至少返回一项。只有图片确实没有可辨认内容时才允许返回空数组。
    """.trimIndent()

    fun prompt(today: LocalDate = LocalDate.now()): String = """
        你是日程提取助手。今天是 $today。请从输入中提取所有日程、待办、通知、约会、考试和截止事项。
        自动纠正明显的同音字和错别字。只返回严格 JSON 数组，不要 Markdown：
        [{"title":"标题","description":"备注或空字符串","date":"YYYY-MM-DD或null","date_year_explicit":true或false,"start_time":"HH:mm或null","end_time":"HH:mm或null","location":"地点或null","repeat":"none/daily/weekly:1/monthly:15","urgency":"normal/important/urgent"}]
        日期规则：相对日期以今天为基准；原文只有月日而没有年份时 date_year_explicit 必须为 false，并选择今天起最近的未来日期，跨年时使用下一年；完全没有日期和时间线索才填 null。只有日期没有时分时 start_time 填 null，应用将按中午12:00处理。如果原文只提供早上、上午、中午、下午、傍晚、晚上或凌晨等时间段，没有日期、星期、相对日期或重复规则，则 date 必须填今天。不要因为措辞不像标准日程就返回空数组，凡是用户希望记住的事项都应提取。
        图片输入规则：先逐区域完整阅读图片中的所有可见文字，再提取日程。课程表、考试通知、群聊通知、海报、表格和手写截图都可能包含日程；只要存在可执行、需记住或有时间线索的事项，就至少返回一项。只有图片确实没有可辨认内容时才允许返回空数组。
    """.trimIndent()

    fun parse(
        response: String,
        fallbackText: String? = null,
        today: LocalDate = LocalDate.now(),
    ): List<Schedule> {
        val parsed = parseStructured(response, fallbackText, today)
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

    fun parseStructured(
        response: String,
        sourceText: String? = null,
        today: LocalDate = LocalDate.now(),
    ): List<Schedule> {
        val cleaned = response.trim()
            .removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        return jsonItems(cleaned).mapNotNull { parseItem(it, sourceText, today) }
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

    private fun parseItem(item: JSONObject, sourceText: String?, today: LocalDate): Schedule? {
        val title = listOf("title", "name", "event", "task").firstNotNullOfOrNull { key ->
            item.optString(key).cleanNull().takeIf(String::isNotBlank)
        } ?: return null
        val date = parseDate(
            item.optString("date").cleanNull(),
            item.optBoolean("date_year_explicit").takeIf { item.has("date_year_explicit") },
            today,
        )
        val repeatRule = item.optString("repeat", "none").cleanNull().ifBlank { "none" }
        val resolvedDate = date ?: today.takeIf {
            repeatRule.equals("none", ignoreCase = true) && sourceText.isTimeOnlyDateCue()
        }
        return Schedule(
            title = title,
            notes = item.optString("description").cleanNull().ifBlank {
                item.optString("notes").cleanNull()
            },
            location = item.optString("location").cleanNull(),
            date = resolvedDate,
            startTime = parseTime(item.optString("start_time").cleanNull()),
            endTime = parseTime(item.optString("end_time").cleanNull()),
            repeatRule = repeatRule,
            urgency = when (item.optString("urgency")) {
                "urgent" -> Urgency.URGENT
                "important" -> Urgency.IMPORTANT
                else -> Urgency.NORMAL
            },
        )
    }

    private fun parseDate(value: String, yearExplicit: Boolean?, today: LocalDate): LocalDate? {
        if (value.isBlank()) return null
        val parsed = try { LocalDate.parse(value) } catch (_: DateTimeParseException) { return null }
        if (yearExplicit == true || (yearExplicit == null && parsed.year >= today.year)) return parsed
        val thisYear = runCatching { LocalDate.of(today.year, parsed.month, parsed.dayOfMonth) }.getOrNull()
        if (thisYear != null && !thisYear.isBefore(today)) return thisYear
        return runCatching { LocalDate.of(today.year + 1, parsed.month, parsed.dayOfMonth) }.getOrNull()
    }

    private fun parseTime(value: String): LocalTime? = if (value.isBlank()) null else runCatching {
        LocalTime.parse(if (value.length == 5) value else value.take(5))
    }.getOrNull()

    private fun String?.isTimeOnlyDateCue(): Boolean {
        val text = this?.trim().orEmpty()
        if (text.isBlank() || hasExplicitDateCue(text)) return false
        return Regex("(?:早上|上午|中午|下午|傍晚|晚上|夜里|凌晨)").containsMatchIn(text)
    }

    private fun hasExplicitDateCue(text: String): Boolean = Regex(
        "(?:今天|明天|后天|大后天|昨天|前天|今晚|本周|这周|下周|上周|周[一二三四五六七日天]|星期[一二三四五六七日天]|\\d{1,4}[年/-]\\s*\\d{1,2}[月/-]|\\d{1,2}\\s*月\\s*\\d{1,2}\\s*[日号]?)"
    ).containsMatchIn(text)

    private fun String.cleanNull(): String = trim().takeUnless {
        it.equals("null", true) || it.equals("none", true)
    }.orEmpty()
}
