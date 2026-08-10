package cn.aimemo.mobile.ai

import cn.aimemo.mobile.data.AccountEntryType
import cn.aimemo.mobile.data.parseAmountToCents
import org.json.JSONArray
import org.json.JSONObject

data class AccountClassificationResult(
    val type: AccountEntryType?,
    val amountCents: Long?,
    val category: String?,
    val suggestedCategory: String?,
    val note: String?,
)

object AccountExtractor {
    fun prompt(description: String, expenseCategories: List<String>, incomeCategories: List<String>): String = """
        你是一个严谨的中文记账助手。先判断用户描述中共有几条独立账目，每一次独立的资金收入或支出都必须拆成一条，不能合并或遗漏。
        然后对每一条账目严格依次判断：
        1. type 是支出 expense 还是收入 income；
        2. amount_yuan 是人民币元金额；
        3. category 从对应收支分类列表中选择一个完全相同的分类；
        4. note 是只描述该条账目的具体事项，不能把多条账目的整段文字重复作为备注。
        任意字段无法可靠判断时，该字段必须为 null，禁止猜测。没有合适分类时 category 为 null，并在 suggested_category 给出简短建议分类名称。
        金额示例：“两毛”是 0.2，“2万”是 20000。
        只返回 JSON，不要 Markdown、解释或额外文字：
        {"count":2,"entries":[{"type":"expense 或 income 或 null","amount_yuan":数字或null,"category":"列表中的原文分类或null","suggested_category":"建议分类或null","note":"该条具体事项或null"}]}
        count 必须等于 entries 的实际条数。

        支出分类列表：${expenseCategories.joinToString("、")}
        收入分类列表：${incomeCategories.joinToString("、")}
        用户描述：$description
    """.trimIndent()

    fun parse(response: String): List<AccountClassificationResult> {
        val cleaned = response.trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        val objectStart = cleaned.indexOf('{')
        val arrayStart = cleaned.indexOf('[')
        if (arrayStart >= 0 && (objectStart < 0 || arrayStart < objectStart)) {
            val arrayEnd = cleaned.lastIndexOf(']')
            if (arrayEnd <= arrayStart) return emptyList()
            val array = runCatching { JSONArray(cleaned.substring(arrayStart, arrayEnd + 1)) }.getOrNull()
                ?: return emptyList()
            return (0 until array.length().coerceAtMost(100)).map { index -> parseItem(array.optJSONObject(index)) }
        }
        val objectEnd = cleaned.lastIndexOf('}')
        if (objectStart < 0 || objectEnd <= objectStart) return emptyList()
        val root = runCatching { JSONObject(cleaned.substring(objectStart, objectEnd + 1)) }.getOrNull()
            ?: return emptyList()
        val entries = root.optJSONArray("entries") ?: root.optJSONArray("accounts")
            ?: JSONArray().put(root)
        val declaredCount = root.optInt("count", entries.length()).coerceAtLeast(0)
        val itemCount = maxOf(declaredCount, entries.length()).coerceAtMost(100)
        return (0 until itemCount).map { index -> parseItem(entries.optJSONObject(index)) }
    }

    private fun parseItem(json: JSONObject?): AccountClassificationResult {
        val type = when (json?.optString("type")?.trim()?.lowercase()) {
            "income", "收入" -> AccountEntryType.INCOME
            "expense", "支出" -> AccountEntryType.EXPENSE
            else -> null
        }
        val amountCents = json?.opt("amount_yuan")
            .takeUnless { it == null || it == JSONObject.NULL }
            ?.toString()
            ?.let(::parseAmountToCents)
        return AccountClassificationResult(
            type = type,
            amountCents = amountCents,
            category = json?.optString("category")?.takeUnless { it.isBlank() || it.equals("null", true) },
            suggestedCategory = json?.optString("suggested_category")
                ?.takeUnless { it.isBlank() || it.equals("null", true) },
            note = json?.optString("note")?.takeUnless { it.isBlank() || it.equals("null", true) },
        )
    }
}
