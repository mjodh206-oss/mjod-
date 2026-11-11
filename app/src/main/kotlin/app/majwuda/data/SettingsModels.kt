package app.majwuda.data

/**
 * All user-facing preferences, as one immutable snapshot. Persisted via DataStore (except the
 * API key, which goes through [SecureKeys]).
 */
data class AssistantSettings(
    val apiKey: String = "",
    /** Optional base URL for a shared gateway (e.g. https://majwuda-gateway.example.workers.dev). Blank = call Anthropic directly. */
    val gatewayUrl: String = "",
    val model: String = "claude-sonnet-4-5",
    val maxTokens: Int = 1800,
    val assistantName: String = "مجودة",
    val userName: String = "",
    val customInstructions: String = "",
    val language: String = "auto", // auto | ar | en
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val voiceEnabled: Boolean = true, // speak replies aloud
    val voiceName: String = "", // preferred TTS voice (empty = system default for ar)
    val speechRate: Float = 1.0f,
    val hapticsEnabled: Boolean = true,
    val wakePhraseEnabled: Boolean = false,
    val screenControlEnabled: Boolean = false,
    val notificationReadEnabled: Boolean = false,
    val webAccessEnabled: Boolean = true, // fetch web pages / weather / currency rates directly from the device
    val autoCall: Boolean = false,
    val autoSendSms: Boolean = false,
    val telemetryEnabled: Boolean = false,
    val keepScreenOn: Boolean = false,
) {
    val usesGateway: Boolean get() = gatewayUrl.isNotBlank()

    /** The actual Messages endpoint the client should call. */
    fun messagesEndpoint(): String {
        val base = if (usesGateway) gatewayUrl.trimEnd('/') else ANTHROPIC_BASE
        return if (base.endsWith("/v1/messages")) base else "$base/v1/messages"
    }

    fun displayModel(): String = model.ifBlank { "claude-sonnet-4-5" }

    companion object {
        const val ANTHROPIC_BASE = "https://api.anthropic.com"

        val MODEL_OPTIONS = listOf(
            ModelOption("claude-sonnet-4-5", "كلود Sonnet 4.5", "الأسرع والأقوى توازنًا — الافتراضي"),
            ModelOption("claude-opus-4-1", "كلود Opus 4.1", "الأعمق والأبطأ — للمسائل الصعبة"),
            ModelOption("claude-haiku-4-5", "كلود Haiku 4.5", "الأسرع والأخف — للأوامر السريعة"),
        )
    }
}

data class ModelOption(val id: String, val title: String, val subtitle: String)

enum class ThemeMode { SYSTEM, LIGHT, DARK }
