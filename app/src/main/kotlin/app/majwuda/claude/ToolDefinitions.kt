package app.majwuda.claude

import app.majwuda.tools.ToolSpec
import kotlinx.serialization.json.JsonArray

/** Serializes the offered tool set into the request `tools` array. */
fun toolDefinitionsJson(active: List<ToolSpec>): JsonArray =
    JsonArray(active.map { it.definition.toJson() })
