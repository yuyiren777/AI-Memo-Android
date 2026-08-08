package cn.aimemo.mobile.ai

import android.util.Base64
import cn.aimemo.mobile.AppPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL

class ZhipuAiClient(private val preferences: AppPreferences) {
    suspend fun extractText(text: String): String = request(
        model = selectedModel(isImage = false),
        content = text,
        image = null,
    )

    suspend fun extractImage(
        imageBytes: ByteArray,
        mimeType: String,
        recoveryAttempt: Boolean = false,
    ): String = request(
        model = selectedModel(isImage = true),
        content = if (recoveryAttempt) IMAGE_RECOVERY_PROMPT else IMAGE_PROMPT,
        image = "data:$mimeType;base64,${Base64.encodeToString(imageBytes, Base64.NO_WRAP)}",
    )

    suspend fun testConnection(): String {
        request(selectedModel(false), "只回复：连接成功", null, extractionPrompt = false)
        return "连接成功"
    }

    private fun selectedModel(isImage: Boolean): String = if (preferences.modelMode == "unified") {
        preferences.unifiedModel.ifBlank { DEFAULT_IMAGE_MODEL }
    } else if (isImage) {
        preferences.imageModel.ifBlank { DEFAULT_IMAGE_MODEL }
    } else {
        preferences.textModel.ifBlank { DEFAULT_TEXT_MODEL }
    }

    private suspend fun request(
        model: String,
        content: String,
        image: String?,
        extractionPrompt: Boolean = true,
    ): String = withContext(Dispatchers.IO) {
        require(preferences.apiKey.isNotBlank()) { "请先在设置中填写智谱 API Key" }
        var lastError: Throwable? = null
        repeat(MAX_ATTEMPTS) { attempt ->
            try {
                return@withContext execute(model, content, image, extractionPrompt)
            } catch (error: AiHttpException) {
                lastError = error
                if (error.status !in RETRYABLE_CODES || attempt == MAX_ATTEMPTS - 1) throw error
                delay((attempt + 1L) * 1500L)
            } catch (error: SocketTimeoutException) {
                throw AiTimeoutException(error)
            } catch (error: Exception) {
                lastError = error
                if (attempt == MAX_ATTEMPTS - 1) throw error
                delay((attempt + 1L) * 1000L)
            }
        }
        throw lastError ?: IllegalStateException("识别失败")
    }

    private fun execute(model: String, text: String, image: String?, extractionPrompt: Boolean): String {
        val userContent: Any = if (image == null) text else JSONArray()
            .put(JSONObject().put("type", "text").put("text", text))
            .put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", image)))
        val messages = JSONArray()
        if (extractionPrompt) messages.put(JSONObject().put("role", "system").put("content", ScheduleExtractor.prompt()))
        messages.put(JSONObject().put("role", "user").put("content", userContent))
        val body = JSONObject()
            .put("model", model)
            .put("messages", messages)
            .put("temperature", 0.1)
            .put("max_tokens", 2048)

        val connection = URL(ENDPOINT).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 15_000
            connection.readTimeout = 45_000
            connection.doOutput = true
            connection.setRequestProperty("Authorization", "Bearer ${preferences.apiKey}")
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val response = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                val message = runCatching {
                    JSONObject(response).optJSONObject("error")?.optString("message")
                }.getOrNull().orEmpty().ifBlank { "HTTP $status" }
                throw AiHttpException(status, message)
            }
            return JSONObject(response)
                .getJSONArray("choices").getJSONObject(0)
                .getJSONObject("message").getString("content")
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        const val DEFAULT_TEXT_MODEL = "glm-4.7-flash"
        const val DEFAULT_IMAGE_MODEL = "glm-4.6v-flash"
        private const val IMAGE_PROMPT = """请先逐区域完整阅读图片中的所有可见文字，再提取全部日程、待办、课程、会议、考试、报名、缴费和截止事项。即使没有标准的“日程”措辞，也要把用户可能需要记住的内容转换为日程。每项分别输出，除非图片确实空白或完全不可辨认，否则不要返回空数组。"""
        private const val IMAGE_RECOVERY_PROMPT = """上一次结果没有生成可解析的日程。请重新仔细查看整张图片，尤其检查小字、表格各行、日期时间、地点和通知正文。先在内部完成 OCR，再严格按照系统要求返回 JSON 数组；只要图片中存在任何需要记住的事项，就至少返回一项，不要解释。"""
        private const val ENDPOINT = "https://open.bigmodel.cn/api/paas/v4/chat/completions"
        private const val MAX_ATTEMPTS = 4
        private val RETRYABLE_CODES = setOf(409, 429, 500, 502, 503, 504)
    }
}

class AiHttpException(val status: Int, message: String) : Exception("识别服务返回 $status：$message")

class AiTimeoutException(cause: Throwable) : Exception("模型连接超时，请稍后重试或切换模型。", cause)
