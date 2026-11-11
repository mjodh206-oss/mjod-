package app.majwuda.assistant

import android.os.Build
import app.majwuda.MajwudaApp
import app.majwuda.assistant.AssistantPrompt
import app.majwuda.claude.ApiMessage
import app.majwuda.claude.ClaudeClient
import app.majwuda.claude.ClaudeConfig
import app.majwuda.claude.ClaudeResponse
import app.majwuda.claude.ClaudeStreamListener
import app.majwuda.claude.StopReason
import app.majwuda.core.NLog
import app.majwuda.core.parseJsonArray
import app.majwuda.data.AssistantSettings
import app.majwuda.data.db.ConversationEntity
import app.majwuda.data.db.MessageEntity
import app.majwuda.tools.ToolContext
import app.majwuda.tools.ToolOutput
import app.majwuda.tools.ToolRegistry
import app.majwuda.tools.accessibilityServiceEnabled
import app.majwuda.tools.environmentOf
import app.majwuda.tools.notificationListenerEnabled
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** The confirmation request shown in the UI. */
data class PendingConfirmation(
    val conversationId: String,
    val toolUseId: String,
    val toolName: String,
    val prompt: String,
)

/** A pending quick-read of an image handed to the assistant (camera questions). */
data class PendingAttachment(val base64: String, val mediaType: String)

/**
 * The مجودة agent loop. One instance per process (owned by [MajwudaApp]).
 *
 * Responsibilities:
 *  - run the Claude Messages API agent loop (text deltas + tool calls),
 *  - execute tools through [ToolRegistry] (with confirmation for sensitive ones),
 *  - persist conversations/messages to Room,
 *  - expose state/events to any UI.
 *
 * All UI surfaces depend only on the public flows below.
 */
class AssistantEngine internal constructor(
    private val app: MajwudaApp,
    private val toolContext: ToolContext,
    val registry: ToolRegistry,
) {

    private val db = app.database

    // ------------------------------------------------------------------ public state

    private val _state = MutableStateFlow(EngineState.IDLE)
    val state: StateFlow<EngineState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<EngineEvent>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<EngineEvent> = _events.asSharedFlow()

    private val _pendingConfirmation = MutableStateFlow<PendingConfirmation?>(null)
    val pendingConfirmation: StateFlow<PendingConfirmation?> = _pendingConfirmation.asStateFlow()

    private val _activeConversation = MutableStateFlow<String?>(null)
    val activeConversation: StateFlow<String?> = _activeConversation.asStateFlow()

    private val interruptions = ConcurrentHashMap<String, Boolean>()
    private val pending = ConcurrentHashMap<String, kotlinx.coroutines.CompletableDeferred<Boolean>>()
    private val turnMutex = Mutex()

    // ------------------------------------------------------------------ api

    /** Start a new conversation thread; returns its id. */
    fun createConversation(): String = UUID.randomUUID().toString()

    /**
     * Send one user message into [conversationId] (created when null/blank) and run the agent
     * loop until the model stops asking for tools (or the iteration cap / interrupt is hit).
     */
    suspend fun send(
        conversationId: String?,
        userText: String,
        attachment: ImageAttachment? = null,
        forceSpeak: Boolean = false,
    ): String? {
        val cid = conversationId?.takeIf { it.isNotBlank() } ?: createConversation()
        turnMutex.withLock {
            if (interruptions[cid] == true) interruptions.remove(cid)
            _activeConversation.value = cid
            val settings = app.settings.state.value
            try {
                val isNew = ensureConversation(cid, userText, settings)
                _events.emit(EngineEvent.TurnStarted(cid, isNew))
                _state.value = EngineState.THINKING

                val userMessageId = persistUserMessage(cid, userText, attachment)
                _events.emit(EngineEvent.UserPersisted(cid, userMessageId))

                if (settings.apiKey.isBlank() && settings.gatewayUrl.isBlank()) {
                    val message = "لسا ما حطيت مفتاح Claude. افتح الإعدادات وألصق مفتاح API، وبعدها أنا جاهز."
                    persistAssistantText(cid, message)
                    _events.emit(EngineEvent.TurnFailed(cid, message))
                    _state.value = EngineState.IDLE
                    return cid
                }

                val transcript = buildTranscript(cid, userText, attachment)
                val result = runLoop(cid, transcript, settings, forceSpeak)
                if (result == null) {
                    _state.value = EngineState.IDLE
                    return cid
                }
                _state.value = EngineState.IDLE
            } catch (t: Throwable) {
                NLog.e("Engine", "turn failed", t)
                val message = when (t) {
                    is app.majwuda.claude.ApiException -> t.message ?: "تعذر الاتصال بـ Claude."
                    is app.majwuda.claude.GatewayConnectException -> t.message ?: "تعذر الوصول إلى البوابة."
                    is java.io.IOException -> "انقطع الاتصال بالإنترنت أثناء الرد."
                    else -> "صار خطأ غير متوقع: ${t.message ?: t.javaClass.simpleName}"
                }
                persistAssistantText(cid, message)
                _events.emit(EngineEvent.TurnFailed(cid, message))
                _state.value = EngineState.IDLE
            }
            return cid
        }
    }

    /** Ask the pending confirmation question and return immediately; UI calls [confirmTool]. */
    private fun requestConfirmation(cid: String, toolUseId: String, toolName: String, prompt: String): kotlinx.coroutines.CompletableDeferred<Boolean> {
        val deferred = kotlinx.coroutines.CompletableDeferred<Boolean>()
        pending[toolUseId] = deferred
        _pendingConfirmation.value = PendingConfirmation(cid, toolUseId, toolName, prompt)
        _events.tryEmit(EngineEvent.ConfirmationNeeded(cid, toolUseId, toolName, prompt))
        return deferred
    }

    fun confirmTool(toolUseId: String, approved: Boolean) {
        pending.remove(toolUseId)?.complete(approved)
        if (_pendingConfirmation.value?.toolUseId == toolUseId) {
            _pendingConfirmation.value = null
        }
    }

    /** Interrupt the current turn for [conversationId] (if any). */
    fun interrupt(conversationId: String?) {
        if (conversationId == null) return
        interruptions[conversationId] = true
        // Unblock any awaiting confirmation so the loop can exit.
        val awaiting = pending.keys.toList()
        awaiting.forEach { confirmTool(it, false) }
    }

    fun isInterrupted(conversationId: String): Boolean = interruptions[conversationId] == true

    fun consumeInterruption(conversationId: String): Boolean = interruptions.remove(conversationId) == true

    // ------------------------------------------------------------------ loop

    /**
     * Runs the tool loop. Returns the final assistant text (empty when interrupted). Emits all
     * [EngineEvent]s; final assistant message persisted before returning.
     */
    private suspend fun runLoop(
        cid: String,
        transcript: List<ApiMessage>,
        settings: AssistantSettings,
        forceSpeak: Boolean,
    ): String {
        val ctx = toolContext
        val env = environmentOf(
            ctx.context,
            settings,
            notificationAccess = notificationListenerEnabled(ctx.context),
            accessibilityOn = accessibilityServiceEnabled(ctx.context),
        )
        val offered = registry.offeredDefinitions(env)
        val memoryItems = runCatching { db.memoryDao().all().take(20).map { it.text } }.getOrDefault(emptyList())
        val deviceLine = buildString {
            append(Build.MANUFACTURER.replaceFirstChar { it.uppercase() })
            append(' ')
            append(Build.MODEL)
            append(" · أندرويد ")
            append(Build.VERSION.RELEASE ?: "?")
        }
        val system = AssistantPrompt.build(
            settings = settings,
            deviceLine = deviceLine,
            memoryItems = memoryItems,
            now = java.time.ZonedDateTime.now(ZoneId.systemDefault()),
        )
        val client = ClaudeClient(
            ClaudeConfig(
                endpoint = settings.messagesEndpoint(),
                apiKey = settings.apiKey.ifBlank { "gateway" },
                model = settings.displayModel(),
                maxTokens = settings.maxTokens,
            ),
        )

        val messages = transcript.toMutableList()
        var finalText = ""
        var interrupted = false
        var iterations = 0
        var lastStop = StopReason.END_TURN
        val speakQueue = ArrayDeque<String>()

        while (iterations < MAX_TOOL_ITERATIONS) {
            iterations++
            if (isInterrupted(cid)) { interrupted = true; break }
            _state.value = EngineState.THINKING
            val textBuilder = StringBuilder()
            val listener = object : ClaudeStreamListener {
                override fun onTextDelta(text: String) {
                    textBuilder.append(text)
                    _events.tryEmit(EngineEvent.TextDelta(cid, text))
                }
            }
            val response: ClaudeResponse = client.streamMessage(
                messages = messages,
                system = system,
                tools = offered,
                listener = listener,
            )
            lastStop = response.stopReason
            finalText = textBuilder.toString().ifBlank { response.textBlocks.joinToString("\n").trim() }

            val toolUses = response.toolUses
            if (toolUses.isEmpty() || response.stopReason != StopReason.TOOL_USE) {
                persistAssistantMessage(cid, response, interrupted = false)
                val speakText = lastSpokenOrFinal(finalText, speakQueue)
                if (speakText.isNotBlank()) {
                    _events.tryEmit(EngineEvent.Speak(cid, speakText))
                }
                _events.emit(
                    EngineEvent.TurnFinished(
                        conversationId = cid,
                        messageId = -1L,
                        finalText = finalText,
                        interrupted = false,
                        stopReason = lastStop.id,
                    ),
                )
                return finalText
            }

            // Append the assistant's tool-use turn to the transcript.
            messages += ApiMessage.assistantBlocks(response.content)

            // Execute tools; confirmation-gated ones wait for the user.
            val toolResultBlocks = ArrayList<JsonObject>()
            for (use in toolUses) {
                if (isInterrupted(cid)) { interrupted = true; break }
                val spec = registry.find(use.name)
                if (spec == null) {
                    toolResultBlocks += ApiMessage.toolResultBlock(use.id, "أداة غير معروفة: ${use.name}", isError = true)
                    continue
                }
                val confirmation = registry.needsConfirmation(
                    toolId = use.name,
                    args = use.input,
                    env = env,
                    autoCall = settings.autoCall,
                    autoSendSms = settings.autoSendSms,
                )
                if (confirmation != null) {
                    val prompt = spec.confirmationPrompt?.invoke(use.input) ?: confirmation
                    _state.value = EngineState.IDLE
                    val approved = requestConfirmation(cid, use.id, use.name, prompt).await()
                    _state.value = EngineState.TOOL
                    if (!approved) {
                        toolResultBlocks += ApiMessage.toolResultBlock(use.id, "رفض المستخدم هذه العملية.", isError = true)
                        _events.tryEmit(
                            EngineEvent.ToolFinished(cid, use.id, use.name, success = false, message = "أُلغيت بطلب المستخدم", card = null),
                        )
                        continue
                    }
                } else {
                    _state.value = EngineState.TOOL
                }

                _events.tryEmit(EngineEvent.ToolStarted(cid, use.id, use.name, toolLabel(use.name)))
                val output: ToolOutput = try {
                    spec.handler.execute(ctx, use.input)
                } catch (t: Throwable) {
                    NLog.e("Engine", "tool ${use.name} crashed", t)
                    ToolOutput.fail("حدث خطأ أثناء التنفيذ: ${t.message ?: "خطأ غير معروف"}")
                }
                output.speak?.takeIf { it.isNotBlank() }?.let { speakQueue.addLast(it) }
                toolResultBlocks += ApiMessage.toolResultBlock(use.id, output.toolResultForModel, isError = !output.success)
                _events.tryEmit(
                    EngineEvent.ToolFinished(
                        conversationId = cid,
                        toolUseId = use.id,
                        name = use.name,
                        success = output.success,
                        message = output.message,
                        card = output.card,
                    ),
                )
            }
            if (interrupted) break
            messages += ApiMessage.userBlocks(toolResultBlocks)
        }

        if (interrupted) {
            persistAssistantMessage(cid, finalText, interrupted = true)
            consumeInterruption(cid)
            _events.emit(
                EngineEvent.TurnFinished(cid, -1L, finalText, interrupted = true, stopReason = "interrupted"),
            )
            return finalText
        }

        val fallback = "وصلت للحد الأقصى من الخطوات في هذا الطلب. جرّب تختصر المطلوب أو توزّعه على رسائل."
        val text = finalText.ifBlank { fallback }
        persistAssistantText(cid, text)
        _events.emit(EngineEvent.TurnFinished(cid, -1L, text, interrupted = false, stopReason = lastStop.id))
        return text
    }

    private fun lastSpokenOrFinal(finalText: String, speakQueue: ArrayDeque<String>): String {
        val last = speakQueue.lastOrNull()
        return if (last != null && last.isNotBlank()) last else finalText
    }

    private fun toolLabel(name: String): String = when (name) {
        "make_call" -> "اتصال"
        "send_sms" -> "رسالة"
        "read_screen" -> "قراءة الشاشة"
        "web_search" -> "بحث"
        "get_weather" -> "الطقس"
        else -> name.replace('_', ' ')
    }

    // ------------------------------------------------------------------ persistence

    private suspend fun ensureConversation(cid: String, firstUserText: String, settings: AssistantSettings): Boolean {
        val existing = db.conversationDao().get(cid)
        if (existing != null) {
            db.conversationDao().upsert(existing.copy(updatedAt = System.currentTimeMillis()))
            return false
        }
        val now = System.currentTimeMillis()
        val title = firstUserText.trim().replace(Regex("\\s+"), " ").take(48).ifBlank { "محادثة جديدة" }
        db.conversationDao().upsert(
            ConversationEntity(
                id = cid,
                title = title,
                createdAt = now,
                updatedAt = now,
                assistantName = settings.assistantName.ifBlank { "مجودة" },
            ),
        )
        return true
    }

    private suspend fun persistUserMessage(cid: String, text: String, attachment: ImageAttachment?): Long {
        val blocks = buildList {
            if (attachment != null) {
                add(ApiMessage.imageBlock(attachment.base64, attachment.mediaType))
            }
            add(ApiMessage.textBlock(text.ifBlank { "(صورة)" }))
        }
        val entity = MessageEntity(
            conversationId = cid,
            role = "user",
            contentJson = JsonArray(blocks).toString(),
            text = text.ifBlank { "(صورة)" },
            toolUses = "[]",
            toolResults = "[]",
            ts = System.currentTimeMillis(),
        )
        val id = db.messageDao().insert(entity)
        touchConversation(cid)
        return id
    }

    private suspend fun persistAssistantMessage(cid: String, response: ClaudeResponse, interrupted: Boolean) {
        val text = response.textBlocks.joinToString("\n").trim()
        val toolUses = response.toolUses
        val entity = MessageEntity(
            conversationId = cid,
            role = "assistant",
            contentJson = JsonArray(response.content).toString(),
            text = text.ifBlank { "(أدوات)" },
            toolUses = JsonArray(toolUses.map { JsonPrimitive(it.name) }).toString(),
            toolResults = "[]",
            ts = System.currentTimeMillis(),
            interrupted = interrupted,
        )
        db.messageDao().insert(entity)
        touchConversation(cid)
    }

    private suspend fun persistAssistantText(cid: String, text: String) {
        val entity = MessageEntity(
            conversationId = cid,
            role = "assistant",
            contentJson = JsonArray(listOf(ApiMessage.textBlock(text))).toString(),
            text = text,
            toolUses = "[]",
            toolResults = "[]",
            ts = System.currentTimeMillis(),
        )
        db.messageDao().insert(entity)
        touchConversation(cid)
    }

    private suspend fun persistAssistantMessage(cid: String, text: String, interrupted: Boolean) {
        val entity = MessageEntity(
            conversationId = cid,
            role = "assistant",
            contentJson = JsonArray(listOf(ApiMessage.textBlock(text))).toString(),
            text = text,
            toolUses = "[]",
            toolResults = "[]",
            ts = System.currentTimeMillis(),
            interrupted = interrupted,
        )
        db.messageDao().insert(entity)
        touchConversation(cid)
    }

    private suspend fun touchConversation(cid: String) {
        db.conversationDao().get(cid)?.let { db.conversationDao().upsert(it.copy(updatedAt = System.currentTimeMillis())) }
    }

    /** Rebuild the API transcript from persisted messages (text-only assistant side). */
    private suspend fun buildTranscript(cid: String, userText: String, attachment: ImageAttachment?): List<ApiMessage> {
        val history = db.messageDao().all(cid)
        val limit = 40
        val trimmed = if (history.size > limit) history.takeLast(limit) else history
        val messages = ArrayList<ApiMessage>(trimmed.size + 2)
        for (message in trimmed) {
            when (message.role) {
                "user" -> {
                    val blocks = parseJsonArray(message.contentJson)?.mapNotNull { it as? JsonObject }
                    if (!blocks.isNullOrEmpty()) {
                        messages += ApiMessage("user", blocks)
                    } else {
                        messages += ApiMessage.userText(message.text)
                    }
                }
                "assistant" -> {
                    if (message.text.isNotBlank()) {
                        messages += ApiMessage.assistantBlocks(listOf(ApiMessage.textBlock(message.text)))
                    }
                }
            }
        }
        // The user message we just persisted is already in `history`; drop nothing.
        return messages
    }

    companion object {
        private const val MAX_TOOL_ITERATIONS = 14
    }
}
