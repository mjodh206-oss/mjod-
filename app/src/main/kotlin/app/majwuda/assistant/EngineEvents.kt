package app.majwuda.assistant

/** Events emitted by the agent loop. [conversationId] is always set. */
sealed interface EngineEvent {
    val conversationId: String

    /** A turn began ([isNew] true when a new conversation was created). */
    data class TurnStarted(override val conversationId: String, val isNew: Boolean) : EngineEvent

    /** Persisted user message id. */
    data class UserPersisted(override val conversationId: String, val messageId: Long) : EngineEvent

    /** Streaming text chunk of the assistant's reply. */
    data class TextDelta(override val conversationId: String, val delta: String) : EngineEvent

    /** A tool invocation started. */
    data class ToolStarted(override val conversationId: String, val toolUseId: String, val name: String, val label: String) : EngineEvent

    /** A tool finished; [payload] carries what the model sees (compact JSON). */
    data class ToolFinished(
        override val conversationId: String,
        val toolUseId: String,
        val name: String,
        val success: Boolean,
        val message: String,
        val card: app.majwuda.tools.ToolCard?,
    ) : EngineEvent

    /** A tool needs the user's confirmation before running. */
    data class ConfirmationNeeded(
        override val conversationId: String,
        val toolUseId: String,
        val toolName: String,
        val prompt: String,
    ) : EngineEvent

    /** The assistant turn finished ([finalText] may be empty). */
    data class TurnFinished(
        override val conversationId: String,
        val messageId: Long,
        val finalText: String,
        val interrupted: Boolean,
        val stopReason: String,
    ) : EngineEvent

    /** Ask the UI to speak [text] aloud (through SpeechManager). */
    data class Speak(override val conversationId: String, val text: String) : EngineEvent

    /** Non-fatal failure during the turn (error already shown to the user). */
    data class TurnFailed(override val conversationId: String, val message: String) : EngineEvent

    /** Machine-readable state for the orb / status line. */
    data class StateChanged(override val conversationId: String, val state: EngineState) : EngineEvent
}

enum class EngineState { IDLE, THINKING, TOOL, LISTENING }

/** One assistant action attached to an image (used for camera-assist questions). */
data class ImageAttachment(val base64: String, val mediaType: String)
