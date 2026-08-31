package cn.aimemo.mobile.ai

import android.util.Base64
import cn.aimemo.mobile.AppPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runInterruptible
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL

enum class AiProvider(
    val id: String,
    val label: String,
    val endpoint: String,
    val defaultTextModel: String,
    val defaultImageModel: String,
    val apiKeyUrl: String,
) {
    ZHIPU(
        id = "zhipu",
        label = "智谱",
        endpoint = "https://open.bigmodel.cn/api/paas/v4/chat/completions",
        defaultTextModel = "glm-4.7-flash",
        defaultImageModel = "glm-4.6v-flash",
        apiKeyUrl = "https://open.bigmodel.cn/",
    ),
    DEEPSEEK(
        id = "deepseek",
        label = "DeepSeek",
        endpoint = "https://api.deepseek.com/chat/completions",
        defaultTextModel = "deepseek-v4-flash",
        defaultImageModel = "deepseek-v4-flash-vision-exp",
        apiKeyUrl = "https://platform.deepseek.com/",
    ),
    ;

    companion object {
        fun fromId(id: String): AiProvider = entries.firstOrNull { it.id == id } ?: ZHIPU
    }
}

class ZhipuAiClient(private val preferences: AppPreferences) {
    suspend fun extractText(text: String): String = request(
        model = selectedModel(isImage = false),
        content = text,
        image = null,
    )

    suspend fun classifyAccount(
        description: String,
        expenseCategories: List<String>,
        incomeCategories: List<String>,
    ): String = request(
        model = selectedModel(isImage = false),
        content = AccountExtractor.prompt(description, expenseCategories, incomeCategories),
        image = null,
        extractionPrompt = false,
        maxTokens = 4096,
    )

    suspend fun classifyAccountImage(
        imageBytes: ByteArray,
        mimeType: String,
        expenseCategories: List<String>,
        incomeCategories: List<String>,
        recoveryAttempt: Boolean = false,
    ): String = request(
        model = selectedModel(isImage = true),
        content = AccountExtractor.imagePrompt(expenseCategories, incomeCategories, recoveryAttempt),
        image = encodedImage(imageBytes, mimeType),
        extractionPrompt = false,
        maxTokens = 8192,
    )

    suspend fun streamFinancialAdvice(prompt: String, onDelta: (String) -> Unit) = runInterruptible(Dispatchers.IO) {
        val provider = selectedProvider()
        val apiKey = preferences.activeApiKey
        require(apiKey.isNotBlank()) { "请先在设置中填写${provider.label} API Key" }
        try {
            executeStreaming(provider.endpoint, selectedModel(isImage = false), prompt, apiKey, onDelta)
        } catch (error: SocketTimeoutException) {
            throw AiTimeoutException(error)
        }
    }

    suspend fun extractImage(
        imageBytes: ByteArray,
        mimeType: String,
        recoveryAttempt: Boolean = false,
    ): String = request(
        model = selectedModel(isImage = true),
        content = if (recoveryAttempt) IMAGE_RECOVERY_PROMPT else IMAGE_PROMPT,
        image = encodedImage(imageBytes, mimeType),
    )

    suspend fun testConnection(): String {
        request(selectedModel(false), "只回复：连接成功", null, extractionPrompt = false)
        return "连接成功"
    }

    private fun selectedProvider(): AiProvider = AiProvider.fromId(preferences.aiProvider)

    private fun selectedModel(isImage: Boolean): String {
        val provider = selectedProvider()
        return if (preferences.modelMode == "unified") {
            preferences.unifiedModel.ifBlank { provider.defaultImageModel }
        } else if (isImage) {
            preferences.imageModel.ifBlank { provider.defaultImageModel }
        } else {
            preferences.textModel.ifBlank { provider.defaultTextModel }
        }
    }

    private suspend fun request(
        model: String,
        content: String,
        image: String?,
        extractionPrompt: Boolean = true,
        maxTokens: Int = 2048,
    ): String {
        val provider = selectedProvider()
        val apiKey = preferences.activeApiKey
        require(apiKey.isNotBlank()) { "请先在设置中填写${provider.label} API Key" }
        var lastError: Throwable? = null
        repeat(MAX_ATTEMPTS) { attempt ->
            try {
                return runInterruptible(Dispatchers.IO) {
                    execute(provider.endpoint, model, content, image, extractionPrompt, apiKey, maxTokens)
                }
            } catch (error: CancellationException) {
                throw error
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

    private fun execute(
        endpoint: String,
        model: String,
        text: String,
        image: String?,
        extractionPrompt: Boolean,
        apiKey: String,
        maxTokens: Int,
    ): String {
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
            .put("max_tokens", maxTokens)

        val connection = URL(endpoint).openConnection() as HttpURLConnection
        try {
            connection.instanceFollowRedirects = false
            connection.useCaches = false
            connection.requestMethod = "POST"
            connection.connectTimeout = 15_000
            // Vision requests can spend considerably longer in OCR/inference before
            // the first response bytes arrive. Keep text requests responsive while
            // allowing image recognition enough time to finish.
            connection.readTimeout = if (image != null) IMAGE_READ_TIMEOUT_MS else TEXT_READ_TIMEOUT_MS
            connection.doOutput = true
            connection.setRequestProperty("Authorization", "Bearer $apiKey")
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setRequestProperty("Cache-Control", "no-store")
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val response = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.use { it.readUtf8Limited(if (status in 200..299) MAX_RESPONSE_BYTES else MAX_ERROR_BYTES) }
                .orEmpty()
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

    private fun executeStreaming(
        endpoint: String,
        model: String,
        prompt: String,
        apiKey: String,
        onDelta: (String) -> Unit,
    ) {
        val messages = JSONArray()
            .put(JSONObject().put("role", "user").put("content", prompt))
        val body = JSONObject()
            .put("model", model)
            .put("messages", messages)
            .put("temperature", 0.35)
            .put("max_tokens", 2048)
            .put("stream", true)
        val connection = URL(endpoint).openConnection() as HttpURLConnection
        try {
            connection.instanceFollowRedirects = false
            connection.useCaches = false
            connection.requestMethod = "POST"
            connection.connectTimeout = 15_000
            connection.readTimeout = 90_000
            connection.doOutput = true
            connection.setRequestProperty("Authorization", "Bearer $apiKey")
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setRequestProperty("Accept", "text/event-stream")
            connection.setRequestProperty("Cache-Control", "no-store")
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            if (status !in 200..299) {
                val response = connection.errorStream?.use { it.readUtf8Limited(MAX_ERROR_BYTES) }.orEmpty()
                val message = runCatching {
                    JSONObject(response).optJSONObject("error")?.optString("message")
                }.getOrNull().orEmpty().ifBlank { "HTTP $status" }
                throw AiHttpException(status, message)
            }
            var receivedContent = false
            var receivedCharacters = 0
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                while (true) {
                    val line = reader.readLine() ?: break
                    if (!line.startsWith("data:")) continue
                    val payload = line.removePrefix("data:").trim()
                    if (payload == "[DONE]") break
                    val delta = runCatching {
                        JSONObject(payload)
                            .optJSONArray("choices")
                            ?.optJSONObject(0)
                            ?.optJSONObject("delta")
                            ?.optString("content")
                    }.getOrNull().orEmpty()
                    if (delta.isNotEmpty()) {
                        receivedCharacters += delta.length
                        require(receivedCharacters <= MAX_STREAM_CHARACTERS) { "模型返回内容过大，已停止接收" }
                        receivedContent = true
                        onDelta(delta)
                    }
                }
            }
            if (!receivedContent) throw IllegalStateException("模型没有返回消费建议")
        } finally {
            connection.disconnect()
        }
    }

    private fun encodedImage(imageBytes: ByteArray, mimeType: String): String =
        "data:$mimeType;base64,${Base64.encodeToString(imageBytes, Base64.NO_WRAP)}"

    companion object {
        private const val IMAGE_PROMPT = """请先逐区域完整阅读图片中的所有可见文字，再提取全部日程、待办、课程、会议、考试、报名、缴费和截止事项。即使没有标准的“日程”措辞，也要把用户可能需要记住的内容转换为日程。每项分别输出，除非图片确实空白或完全不可辨认，否则不要返回空数组。"""
        private const val IMAGE_RECOVERY_PROMPT = """上一次结果没有生成可解析的日程。请重新仔细查看整张图片，尤其检查小字、表格各行、日期时间、地点和通知正文。先在内部完成 OCR，再严格按照系统要求返回 JSON 数组；只要图片中存在任何需要记住的事项，就至少返回一项，不要解释。"""
        private const val MAX_ATTEMPTS = 4
        private const val TEXT_READ_TIMEOUT_MS = 45_000
        private const val IMAGE_READ_TIMEOUT_MS = 180_000
        private const val MAX_RESPONSE_BYTES = 1024 * 1024
        private const val MAX_ERROR_BYTES = 64 * 1024
        private const val MAX_STREAM_CHARACTERS = 256 * 1024
        private val RETRYABLE_CODES = setOf(409, 429, 500, 502, 503, 504)
    }
}

private fun InputStream.readUtf8Limited(maxBytes: Int): String {
    val output = ByteArrayOutputStream(minOf(maxBytes, 16 * 1024))
    val buffer = ByteArray(8 * 1024)
    var total = 0
    while (true) {
        val read = read(buffer)
        if (read < 0) break
        total += read
        require(total <= maxBytes) { "模型返回内容过大，已停止接收" }
        output.write(buffer, 0, read)
    }
    return output.toString(Charsets.UTF_8.name())
}

class AiHttpException(val status: Int, message: String) : Exception("识别服务返回 $status：$message")

class AiTimeoutException(cause: Throwable) : Exception("模型连接超时，请稍后重试或切换模型。", cause)
