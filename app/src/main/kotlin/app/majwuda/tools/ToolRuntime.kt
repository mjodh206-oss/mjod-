package app.majwuda.tools

import app.majwuda.claude.ToolDefinition
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/** A card rendered in the chat and (optionally) fed back to the model. */
@Serializable
sealed interface ToolCard {
    @Serializable
    @SerialName("plain")
    data class Plain(val title: String, val body: String) : ToolCard

    @Serializable
    @SerialName("kv")
    data class KeyValues(val title: String, val rows: List<CardRow>) : ToolCard

    @Serializable
    @SerialName("list")
    data class ListItems(val title: String, val items: List<CardRow>, val footer: String? = null) : ToolCard

    @Serializable
    @SerialName("table")
    data class Table(val title: String, val header: List<String>, val rows: List<List<String>>) : ToolCard

    @Serializable
    @SerialName("weather")
    data class Weather(
        val place: String,
        val temperature: String,
        val feelsLike: String?,
        val description: String,
        val humidity: String?,
        val wind: String?,
        val precipitation: String?,
    ) : ToolCard

    @Serializable
    @SerialName("image")
    data class Image(val title: String, val path: String, val mediaType: String) : ToolCard

    @Serializable
    @SerialName("note")
    data class Note(val title: String, val body: String, val tags: List<String> = emptyList()) : ToolCard

    @Serializable
    @SerialName("reminder")
    data class Reminder(val title: String, val body: String?, val at: Long, val done: Boolean) : ToolCard

    @Serializable
    @SerialName("search")
    data class WebLinks(val query: String, val links: List<CardRow>) : ToolCard
}

@Serializable
data class CardRow(val label: String, val value: String? = null, val sub: String? = null)

/** Result of executing a tool. [message] goes to the model; [speak] overrides the spoken reply for quick actions. */
data class ToolOutput(
    val success: Boolean,
    val message: String,
    val speak: String? = null,
    val card: ToolCard? = null,
    /** Include a short summary of the card in the tool_result sent to the model (defaults to [message]). */
    val toolResultForModel: String = message,
) {
    companion object {
        fun ok(message: String, speak: String? = null, card: ToolCard? = null, toolResultForModel: String = message) =
            ToolOutput(true, message, speak, card, toolResultForModel)

        fun fail(message: String) = ToolOutput(false, message, message, null, "error: $message")
    }
}

enum class ToolRisk {
    /** Safe: execute without asking. */
    SAFE,

    /** Needs an explicit user confirmation before it is executed. */
    CONFIRM,

    /** Destructive or provider-side: always confirm (used for deletes). */
    DESTRUCTIVE,
}

/** Fine-grained switches for tools that require device capabilities. */
enum class ToolGate {
    CONTACTS,
    CALL,
    SMS,
    CALENDAR,
    LOCATION,
    NOTIFICATION_ACCESS,
    ACCESSIBILITY,
    ALERT_POLICY,
    WRITE_SETTINGS,
}

/**
 * One registered capability. [availability] explains why a tool cannot be used right now; the
 * model is simply not told about the tool in that case.
 */
data class ToolSpec(
    val id: String,
    val gates: Set<ToolGate> = emptySet(),
    val risk: ToolRisk,
    val definition: ToolDefinition,
    /** Builds the confirmation message shown to the user. */
    val confirmationPrompt: ((args: JsonObject) -> String)? = null,
    val handler: ToolHandler,
) {
    val communication: Boolean get() = definition.communication
    val destructive: Boolean get() = definition.destructive

    fun toolDefinition(): ToolDefinition = definition
}

fun interface ToolHandler {
    suspend fun execute(ctx: ToolContext, args: JsonObject): ToolOutput
}

/** Live snapshot of what this device can do right now. Rebuilt per assistant turn. */
data class ToolEnvironment(
    val hasContacts: Boolean = true,
    val canCall: Boolean = true,
    val canSendSms: Boolean = true,
    val canReadSms: Boolean = true,
    val canReadCalendar: Boolean = true,
    val canWriteCalendar: Boolean = true,
    val hasLocation: Boolean = true,
    val hasNotificationAccess: Boolean = false,
    val hasAccessibility: Boolean = false,
    val canSetAlertPolicy: Boolean = true,
    val canWriteSettings: Boolean = false,
    val screenControlEnabled: Boolean = false,
    val notificationReadEnabled: Boolean = false,
    val wakePhraseEnabled: Boolean = false,
    val selectedVoice: String = "auto",
    val language: String = "ar",
) {
    fun allows(gates: Set<ToolGate>): Boolean = gates.all { gate ->
        when (gate) {
            ToolGate.CONTACTS -> hasContacts
            ToolGate.CALL -> canCall
            ToolGate.SMS -> canSendSms
            ToolGate.CALENDAR -> canReadCalendar && canWriteCalendar
            ToolGate.LOCATION -> hasLocation
            ToolGate.NOTIFICATION_ACCESS -> hasNotificationAccess && notificationReadEnabled
            ToolGate.ACCESSIBILITY -> hasAccessibility && screenControlEnabled
            ToolGate.ALERT_POLICY -> canSetAlertPolicy
            ToolGate.WRITE_SETTINGS -> canWriteSettings
        }
    }
}

/** Pure registry logic: which tools exist, which are offered to the model, which need confirmation. */
class ToolRegistry(private val specs: List<ToolSpec>) {

    val all: List<ToolSpec> = specs

    fun availableTools(env: ToolEnvironment): List<ToolSpec> =
        specs.filter { env.allows(it.gates) }

    fun offeredDefinitions(env: ToolEnvironment): List<ToolDefinition> =
        availableTools(env).map { it.definition }

    fun find(id: String): ToolSpec? = specs.firstOrNull { it.id == id }

    /** Decides if [toolId] needs user confirmation and builds the prompt. */
    fun needsConfirmation(
        toolId: String,
        args: JsonObject,
        env: ToolEnvironment,
        autoCall: Boolean,
        autoSendSms: Boolean,
    ): String? {
        val spec = find(toolId) ?: return null
        if (spec.gates.contains(ToolGate.SMS) && autoSendSms && toolId in SMS_TOOLS_WITH_AUTOSEND) return null
        if (toolId in AUTO_CALL_TOOLS && autoCall && spec.gates.contains(ToolGate.CALL)) return null
        return when (spec.risk) {
            ToolRisk.SAFE -> null
            else -> spec.confirmationPrompt?.invoke(args)?.takeIf { it.isNotBlank() }
                ?: "هل أتابع؟"
        }
    }

    private companion object {
        val SMS_TOOLS_WITH_AUTOSEND = setOf("send_sms")
        val AUTO_CALL_TOOLS = setOf("make_call")
    }
}
