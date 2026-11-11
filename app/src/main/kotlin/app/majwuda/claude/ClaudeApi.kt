package app.majwuda.claude

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Hand-written, lenient model of the subset of the Anthropic Messages API used by مجودة.
 * Content blocks and streaming events are kept as JsonObjects, so nothing breaks when the API
 * grows new fields.
 */
data class UsageInfo(
    val inputTokens: Int = 0,
    val outputTokens: Int = 0,
    val cacheCreationInputTokens: Int = 0,
    val cacheReadInputTokens: Int = 0,
)

enum class StopReason(val id: String) {
    END_TURN("end_turn"),
    TOOL_USE("tool_use"),
    MAX_TOKENS("max_tokens"),
    STOP_SEQUENCE("stop_sequence"),
    PAUSE_TURN("pause_turn"),
    REFUSAL("refusal"),
    MODEL_CONTEXT_WINDOW_EXCEEDED("model_context_window_exceeded"),
    UNKNOWN("unknown");

    companion object {
        fun from(id: String?): StopReason = values().firstOrNull { it.id == id } ?: UNKNOWN
    }
}

data class ClaudeResponse(
    val id: String,
    val model: String,
    val content: List<JsonObject>,
    val stopReason: StopReason,
    val usage: UsageInfo,
) {
    val textBlocks: List<String> get() = content.mapNotNull { block ->
        if (block["type"]?.toString()?.trim('"') == "text") (block["text"] as? JsonPrimitive)?.content else null
    }

    val toolUses: List<ToolUse> get() = content.mapNotNull { block ->
        if (block["type"]?.toString()?.trim('"') != "tool_use") return@mapNotNull null
        val name = (block["name"] as? JsonPrimitive)?.content ?: return@mapNotNull null
        ToolUse(
            id = (block["id"] as? JsonPrimitive)?.content ?: "",
            name = name,
            input = block["input"] as? JsonObject ?: JsonObject(emptyMap()),
        )
    }

    val hasText: Boolean get() = textBlocks.any { it.isNotBlank() }
}

data class ToolUse(val id: String, val name: String, val input: JsonObject)

/** [role] is "user" or "assistant". [content] is either a String or a list of content blocks. */
data class ApiMessage(val role: String, val content: List<JsonObject>) {
    fun toJson(): JsonObject = buildJsonObject {
        put("role", role)
        putJsonArray("content") { content.forEach { add(it) } }
    }

    companion object {
        fun userText(text: String): ApiMessage = ApiMessage("user", listOf(textBlock(text)))
        fun userBlocks(blocks: List<JsonObject>): ApiMessage = ApiMessage("user", blocks)
        fun assistantBlocks(blocks: List<JsonObject>): ApiMessage = ApiMessage("assistant", blocks)
        fun textBlock(text: String): JsonObject = buildJsonObject {
            put("type", "text")
            put("text", text)
        }
        fun toolResultBlock(toolUseId: String, content: String, isError: Boolean = false): JsonObject = buildJsonObject {
            put("type", "tool_result")
            put("tool_use_id", toolUseId)
            put("content", content)
            if (isError) put("is_error", true)
        }
        fun imageBlock(base64: String, mediaType: String): JsonObject = buildJsonObject {
            put("type", "image")
            putJsonObject("source") {
                put("type", "base64")
                put("media_type", mediaType)
                put("data", base64)
            }
        }
        fun toolUseBlock(id: String, name: String, input: JsonObject): JsonObject = buildJsonObject {
            put("type", "tool_use")
            put("id", id)
            put("name", name)
            put("input", input)
        }
    }
}

/**
 * A tool definition as sent to the API. [schema] must be a JSON object schema whose
 * "properties" cover every argument the model may pass.
 */
data class ToolDefinition(
    val name: String,
    val description: String,
    val schema: JsonObject,
    val destructive: Boolean = false,
    val communication: Boolean = false,
) {
    fun toJson(): JsonObject = buildJsonObject {
        put("name", name)
        put("description", description)
        put("input_schema", schema)
    }
}

/** Streaming callback, invoked from a background thread. */
interface ClaudeStreamListener {
    fun onTextDelta(text: String) {}
    fun onThinkingDelta(text: String) {}
    fun onToolUseStart(id: String, name: String) {}
    fun onToolUseInputDelta(id: String, partialJson: String) {}
    fun onMessageStop(response: ClaudeResponse) {}
    fun onError(message: String) {}
}
