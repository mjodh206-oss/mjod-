package app.majwuda.claude

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Minimal JSON-Schema builder used for tool definitions sent to the Claude Messages API.
 * Produces plain serializable JsonObjects; no schema codegen.
 */
@Suppress("FunctionName")
object Schema {

    fun object_(vararg properties: Pair<String, JsonObject>, required: List<String> = properties.map { it.first }, description: String? = null): JsonObject =
        buildJsonObject {
            put("type", "object")
            if (description != null) put("description", description)
            putJsonObject("properties") {
                properties.forEach { (name, schema) -> put(name, schema) }
            }
            putJsonArray("required") { required.forEach { add(JsonPrimitive(it)) } }
        }

    fun emptyObject(): JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {}
    }

    fun string(description: String, enumValues: List<String>? = null, default: String? = null, format: String? = null): JsonObject =
        buildJsonObject {
            put("type", "string")
            put("description", description)
            if (enumValues != null) putJsonArray("enum") { enumValues.forEach { add(JsonPrimitive(it)) } }
            if (default != null) put("default", default)
            if (format != null) put("format", format)
        }

    fun integer(description: String, minimum: Int? = null, maximum: Int? = null, default: Int? = null): JsonObject =
        buildJsonObject {
            put("type", "integer")
            put("description", description)
            if (minimum != null) put("minimum", minimum)
            if (maximum != null) put("maximum", maximum)
            if (default != null) put("default", default)
        }

    fun number(description: String, minimum: Double? = null, maximum: Double? = null, default: Double? = null): JsonObject =
        buildJsonObject {
            put("type", "number")
            put("description", description)
            if (minimum != null) put("minimum", minimum)
            if (maximum != null) put("maximum", maximum)
            if (default != null) put("default", default)
        }

    fun boolean(description: String, default: Boolean? = null): JsonObject =
        buildJsonObject {
            put("type", "boolean")
            put("description", description)
            if (default != null) put("default", default)
        }

    fun array(itemSchema: JsonObject, description: String, minItems: Int? = null, maxItems: Int? = null): JsonObject =
        buildJsonObject {
            put("type", "array")
            put("description", description)
            put("items", itemSchema)
            if (minItems != null) put("minItems", minItems)
            if (maxItems != null) put("maxItems", maxItems)
        }
}
