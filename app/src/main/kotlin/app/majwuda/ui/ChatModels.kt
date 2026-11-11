package app.majwuda.ui

import app.majwuda.tools.ToolCard

/** One row in the chat transcript. */
sealed interface ChatItem {
    val key: String

    data class User(
        override val key: String,
        val text: String,
        val hasImage: Boolean,
        val id: Long,
        val ts: Long,
    ) : ChatItem

    data class Assistant(
        override val key: String,
        val text: String,
        val interrupted: Boolean,
        val id: Long,
        val ts: Long,
    ) : ChatItem

    /** A tool invocation shown inline (spinner → result). */
    data class Tool(
        override val key: String,
        val toolUseId: String,
        val name: String,
        val label: String,
        val running: Boolean,
        val success: Boolean,
        val message: String,
        val card: ToolCard?,
        val ts: Long,
    ) : ChatItem
}
