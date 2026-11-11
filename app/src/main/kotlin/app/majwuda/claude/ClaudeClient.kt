package app.majwuda.claude

import app.majwuda.core.NLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.putJsonObject
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import okio.BufferedSource
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

/** Transport-level failures with a message that is safe to show to the user. */
class GatewayConnectException(message: String, cause: Throwable? = null) : IOException(message, cause)

class ApiException(val status: Int, val code: String?, message: String) : IOException(message)

/** Credentials and endpoint for one Claude request. */
data class ClaudeConfig(
    /** Full Messages endpoint, e.g. https://gateway.example.com/v1/messages or https://api.anthropic.com/v1/messages */
    val endpoint: String,
    val apiKey: String,
    val model: String,
    val maxTokens: Int = 1500,
    val timeoutSeconds: Int = 120,
)

/**
 * Minimal, dependency-free client for the Anthropic Messages API (or a compatible gateway).
 * Streaming uses server-sent events; both paths return the same [ClaudeResponse] shape.
 */
class ClaudeClient(private val config: ClaudeConfig) {

    private val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .callTimeout(config.timeoutSeconds.toLong() + 30, TimeUnit.SECONDS)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(config.timeoutSeconds.toLong(), TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    /** Non-streaming request. [system] is the array of system content blocks. */
    suspend fun createMessage(
        messages: List<ApiMessage>,
        system: kotlinx.serialization.json.JsonArray? = null,
        tools: List<ToolDefinition>,
        enableThinking: Boolean = false,
    ): ClaudeResponse = withContext(Dispatchers.IO) {
        val request = buildRequest(messages, system, tools, stream = false, enableThinking = enableThinking)
        http.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw apiError(response.code, body)
            parseResponse(body)
        }
    }

    /**
     * Streaming request used when the caller wants progressive text. [listener] runs on the
     * OkHttp reader thread; the returned value is the final assembled response.
     */
    suspend fun streamMessage(
        messages: List<ApiMessage>,
        system: kotlinx.serialization.json.JsonArray? = null,
        tools: List<ToolDefinition>,
        enableThinking: Boolean = false,
        listener: ClaudeStreamListener,
    ): ClaudeResponse = withContext(Dispatchers.IO) {
        val request = buildRequest(messages, system, tools, stream = true, enableThinking = enableThinking)
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val body = response.body?.string().orEmpty()
                listener.onError(errorMessage(response.code, body))
                throw apiError(response.code, body)
            }
            readEventStream(response.body, listener)
        }
    }

    // -----------------------------------------------------------------------------------------

    private fun buildRequest(
        messages: List<ApiMessage>,
        system: kotlinx.serialization.json.JsonArray?,
        tools: List<ToolDefinition>,
        stream: Boolean,
        enableThinking: Boolean,
    ): Request {
        val payload = buildJsonObject {
            put("model", JsonPrimitive(config.model))
            put("max_tokens", JsonPrimitive(config.maxTokens))
            if (system != null) putJsonArray("system") { system.forEach { add(it) } }
            putJsonArray("messages") { messages.forEach { add(it.toJson()) } }
            if (tools.isNotEmpty()) putJsonArray("tools") { tools.forEach { add(it.toJson()) } }
            put("stream", JsonPrimitive(stream))
            if (enableThinking) {
                putJsonObject("thinking") {
                    put("type", "enabled")
                    put("budget_tokens", 2048)
                }
            }
        }.toString()

        val builder = Request.Builder()
            .url(config.endpoint)
            .post(payload.toRequestBody(JSON_MEDIA_TYPE))
            .header("content-type", "application/json")
            .header("anthropic-version", "2023-06-01")
            .header("anthropic-beta", "prompt-caching-2024-07-31")
            .header("user-agent", "majwuda-android/1.0")
        if (config.apiKey.isNotBlank()) builder.header("x-api-key", config.apiKey)
        return builder.build()
    }

    private fun apiError(status: Int, body: String): ApiException {
        val (code, message) = parseError(body)
        return ApiException(status, code, errorMessage(status, message))
    }

    private fun parseError(body: String): Pair<String?, String> {
        return try {
            val root = json.parseToJsonElement(body).jsonObject
            val error = root["error"]?.jsonObject
            val code = (error?.get("type") as? JsonPrimitive)?.content
            val message = (error?.get("message") as? JsonPrimitive)?.content ?: body.take(400)
            code to message
        } catch (_: Exception) {
            null to body.take(400)
        }
    }

    private fun errorMessage(status: Int, body: String): String {
        val (code, message) = parseError(body)
        val codeHint = code?.let { " ($it)" }.orEmpty()
        return when (status) {
            401, 403 -> "مفتاح Claude غير صالح أو مرفوض ($status$codeHint)."
            429 -> "وصلت الطلبات إلى الحد الأقصى، حاول بعد قليل.$codeHint"
            529 -> "خوادم Claude مشغولة مؤقتًا، حاول بعد قليل."
            in 500..599 -> "مشكلة في خوادم Claude ($status$codeHint)."
            else -> message.ifBlank { "تعذّر الاتصال بـ Claude ($status$codeHint)." }
        }
    }

    private fun parseResponse(body: String): ClaudeResponse {
        val root = json.parseToJsonElement(body).jsonObject
        val usage = root["usage"]?.jsonObject
        return ClaudeResponse(
            id = (root["id"] as? JsonPrimitive)?.content ?: "",
            model = (root["model"] as? JsonPrimitive)?.content ?: config.model,
            content = (root["content"] as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty(),
            stopReason = StopReason.from((root["stop_reason"] as? JsonPrimitive)?.content),
            usage = UsageInfo(
                inputTokens = usage?.get("input_tokens")?.let { (it as? JsonPrimitive)?.intOrNull } ?: 0,
                outputTokens = usage?.get("output_tokens")?.let { (it as? JsonPrimitive)?.intOrNull } ?: 0,
                cacheCreationInputTokens = usage?.get("cache_creation_input_tokens")?.let { (it as? JsonPrimitive)?.intOrNull } ?: 0,
                cacheReadInputTokens = usage?.get("cache_read_input_tokens")?.let { (it as? JsonPrimitive)?.intOrNull } ?: 0,
            ),
        )
    }

    /**
     * Parses the SSE stream. Content blocks are assembled incrementally:
     * text/thinking deltas are forwarded, tool_use input JSON is accumulated and re-parsed
     * when the block closes.
     */
    private fun readEventStream(body: ResponseBody?, listener: ClaudeStreamListener): ClaudeResponse {
        if (body == null) throw IOException("استجابة فارغة من الخادم")
        val source: BufferedSource = body.source()
        val reader = BufferedReader(InputStreamReader(source.inputStream(), Charsets.UTF_8))

        var messageId = ""
        var model = config.model
        var stopReason = StopReason.END_TURN
        var inputTokens = 0
        var outputTokens = 0
        var cacheCreate = 0
        var cacheRead = 0

        val blocks = ArrayList<JsonObject>()
        val current = HashMap<Int, BlockBuilder>()
        var event = ""
        var data = StringBuilder()

        fun flushEvent() {
            val raw = data.toString().trim()
            data = StringBuilder()
            if (raw.isEmpty()) return
            val element = try {
                json.parseToJsonElement(raw).jsonObject
            } catch (_: Exception) {
                return
            }
            when (event) {
                "message_start" -> {
                    val message = element["message"]?.jsonObject ?: element
                    messageId = (message["id"] as? JsonPrimitive)?.content ?: messageId
                    model = (message["model"] as? JsonPrimitive)?.content ?: model
                    val usage = message["usage"]?.jsonObject
                    inputTokens = usage?.intField("input_tokens") ?: inputTokens
                    cacheCreate = usage?.intField("cache_creation_input_tokens") ?: cacheCreate
                    cacheRead = usage?.intField("cache_read_input_tokens") ?: cacheRead
                }
                "content_block_start" -> {
                    val index = element.intField("index") ?: 0
                    val block = element["content_block"]?.jsonObject
                    val type = (block?.get("type") as? JsonPrimitive)?.content ?: "text"
                    val builder = BlockBuilder().apply {
                        this.type = type
                        id = (block?.get("id") as? JsonPrimitive)?.content ?: ""
                        name = (block?.get("name") as? JsonPrimitive)?.content ?: ""
                        val textValue = (block?.get("text") as? JsonPrimitive)?.content
                        if (textValue != null) text.append(textValue)
                        val thinkingValue = (block?.get("thinking") as? JsonPrimitive)?.content
                        if (thinkingValue != null) thinking.append(thinkingValue)
                    }
                    current[index] = builder
                    if (builder.type == "thinking" || builder.type == "redacted_thinking") {
                        // redacted thinking has no text delta
                    } else if (builder.type == "tool_use") {
                        listener.onToolUseStart(builder.id, builder.name)
                    } else if (builder.text.isNotEmpty()) {
                        listener.onTextDelta(builder.text.toString())
                    }
                }
                "content_block_delta" -> {
                    val index = element.intField("index") ?: 0
                    val delta = element["delta"]?.jsonObject ?: return
                    val builder = current.getOrPut(index) { BlockBuilder() }
                    when ((delta["type"] as? JsonPrimitive)?.content) {
                        "text_delta" -> {
                            val piece = (delta["text"] as? JsonPrimitive)?.content.orEmpty()
                            builder.text.append(piece)
                            if (piece.isNotEmpty()) listener.onTextDelta(piece)
                        }
                        "thinking_delta" -> {
                            val piece = (delta["thinking"] as? JsonPrimitive)?.content.orEmpty()
                            builder.thinking.append(piece)
                            if (piece.isNotEmpty()) listener.onThinkingDelta(piece)
                        }
                        "signature_delta" -> Unit
                        "input_json_delta" -> {
                            val piece = (delta["partial_json"] as? JsonPrimitive)?.content.orEmpty()
                            builder.jsonParts.append(piece)
                            if (piece.isNotEmpty()) listener.onToolUseInputDelta(builder.id, piece)
                        }
                        else -> Unit
                    }
                }
                "content_block_stop" -> {
                    val index = element.intField("index") ?: 0
                    val builder = current.remove(index)
                    if (builder != null) blocks.add(finishBlock(builder))
                }
                "message_delta" -> {
                    val delta = element["delta"]?.jsonObject
                    (delta?.get("stop_reason") as? JsonPrimitive)?.content?.let { stopReason = StopReason.from(it) }
                    val usage = element["usage"]?.jsonObject
                    outputTokens = usage?.intField("output_tokens") ?: outputTokens
                    inputTokens = usage?.intField("input_tokens") ?: inputTokens
                    cacheCreate = usage?.intField("cache_creation_input_tokens") ?: cacheCreate
                    cacheRead = usage?.intField("cache_read_input_tokens") ?: cacheRead
                }
                "message_stop" -> Unit
                "ping" -> Unit
                "error" -> {
                    val error = element["error"]?.jsonObject
                    val message = (error?.get("message") as? JsonPrimitive)?.content ?: "خطأ أثناء الاستجابة"
                    listener.onError(message)
                }
                else -> NLog.d("SSE", "ignored event: $event")
            }
        }

        try {
            reader.use { buffered ->
                var line = buffered.readLine()
                while (line != null) {
                    when {
                        line.isEmpty() -> {
                            flushEvent()
                            event = ""
                        }
                        line.startsWith("event:") -> event = line.removePrefix("event:").trim()
                        line.startsWith("data:") -> data.append(line.removePrefix("data:").trim())
                        line.startsWith(":") -> Unit // comment/heartbeat
                    }
                    line = buffered.readLine()
                }
                flushEvent()
            }
        } catch (io: IOException) {
            listener.onError("انقطع الاتصال أثناء الإجابة: ${io.message}")
            throw io
        }
        // Close blocks that never received an explicit stop.
        current.entries.sortedBy { it.key }.forEach { (_, builder) -> blocks.add(finishBlock(builder)) }

        val response = ClaudeResponse(
            id = messageId,
            model = model,
            content = blocks,
            stopReason = stopReason,
            usage = UsageInfo(inputTokens, outputTokens, cacheCreate, cacheRead),
        )
        listener.onMessageStop(response)
        return response
    }

    private fun finishBlock(builder: BlockBuilder): JsonObject = buildJsonObject {
        when (builder.type) {
            "tool_use" -> {
                put("type", JsonPrimitive("tool_use"))
                put("id", JsonPrimitive(builder.id))
                put("name", JsonPrimitive(builder.name))
                val parsed = parseJsonObjectLoose(builder.jsonParts.toString()) ?: JsonObject(emptyMap())
                put("input", parsed)
            }
            "thinking", "redacted_thinking" -> {
                put("type", JsonPrimitive(builder.type))
                put("thinking", JsonPrimitive(builder.thinking.toString()))
            }
            else -> {
                put("type", JsonPrimitive("text"))
                put("text", JsonPrimitive(builder.text.toString()))
            }
        }
    }

    private fun parseJsonObjectLoose(raw: String): JsonObject? {
        val cleaned = raw.trim()
        if (cleaned.isEmpty()) return JsonObject(emptyMap())
        return try {
            json.parseToJsonElement(cleaned).jsonObject
        } catch (_: Exception) {
            // The model emitted invalid JSON: expose it verbatim so the caller can report it.
            buildJsonObject { put("_raw", JsonPrimitive(cleaned)) }
        }
    }

    private fun JsonObject.intField(name: String): Int? = (this[name] as? JsonPrimitive)?.intOrNull

    companion object {
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}


/** Streaming state for one content block. */
private class BlockBuilder {
    var type: String = "text"
    var id: String = ""
    var name: String = ""
    val text = StringBuilder()
    val thinking = StringBuilder()
    val jsonParts = StringBuilder()
}

/** Builds the cached system-prompt payload: one large stable block + one small dynamic block. */
fun systemPrompt(stable: String, dynamic: String? = null): kotlinx.serialization.json.JsonArray =
    kotlinx.serialization.json.buildJsonArray {
        add(buildJsonObject {
            put("type", "text")
            put("text", stable)
            putJsonObject("cache_control") { put("type", "ephemeral") }
        })
        if (!dynamic.isNullOrBlank()) {
            add(buildJsonObject {
                put("type", "text")
                put("text", dynamic)
            })
        }
    }
