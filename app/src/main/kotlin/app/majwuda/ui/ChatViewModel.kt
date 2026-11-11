package app.majwuda.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.majwuda.MajwudaApp
import app.majwuda.assistant.EngineEvent
import app.majwuda.assistant.EngineState
import app.majwuda.assistant.ImageAttachment
import app.majwuda.assistant.PendingConfirmation
import app.majwuda.data.db.ConversationEntity
import app.majwuda.data.db.MessageEntity
import app.majwuda.speech.SpeechManager
import app.majwuda.tools.ToolCard
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

/**
 * Single source of truth for the chat screens. UI-agnostic enough to back both the full
 * [MainActivity] chat and the compact [AssistActivity] overlay.
 */
class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val app = MajwudaApp.from(application)
    private val engine = app.engine
    private val db = app.database
    val speech: SpeechManager = SpeechManager.get(application)

    // ------------------------------------------------------------ conversations

    private val _conversationId = MutableStateFlow<String?>(null)
    val conversationId: StateFlow<String?> = _conversationId.asStateFlow()

    val conversations: StateFlow<List<ConversationEntity>> = db.conversationDao()
        .observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _dbMessages = MutableStateFlow<List<MessageEntity>>(emptyList())
    private var messagesCollectJob: Job? = null

    // ------------------------------------------------------------ transcript rows

    private val _toolRows = MutableStateFlow<List<ChatItem.Tool>>(emptyList())
    private val _streamingText = MutableStateFlow("")
    val streamingText: StateFlow<String> = _streamingText.asStateFlow()

    val engineState: StateFlow<EngineState> = engine.state

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    val pendingConfirmation: StateFlow<PendingConfirmation?> = engine.pendingConfirmation

    private val _snackbar = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val snackbar = _snackbar.asSharedFlow()

    /** Combined, ordered transcript: DB messages + in-memory tool rows. */
    val items: StateFlow<List<ChatItem>> = combine(_dbMessages, _toolRows) { messages, tools ->
        buildList<ChatItem> {
            messages.forEach { entity ->
                val timeKey = entity.id.toString()
                when (entity.role) {
                    "user" -> add(
                        ChatItem.User(
                            key = "u_$timeKey",
                            text = entity.text,
                            hasImage = entity.contentJson.contains("\"image\""),
                            id = entity.id,
                            ts = entity.ts,
                        ),
                    )
                    else -> add(
                        ChatItem.Assistant(
                            key = "a_$timeKey",
                            text = entity.text,
                            interrupted = entity.interrupted,
                            id = entity.id,
                            ts = entity.ts,
                        ),
                    )
                }
            }
            addAll(tools)
            sortBy { item ->
                when (item) {
                    is ChatItem.User -> item.ts
                    is ChatItem.Assistant -> item.ts
                    is ChatItem.Tool -> item.ts
                }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ------------------------------------------------------------ voice

    val isListening: StateFlow<Boolean> = speech.isListening
    val voiceLevel: StateFlow<Float> = speech.voiceLevel
    val partialTranscript: StateFlow<String> = speech.partialTranscript
    val isSpeaking: StateFlow<Boolean> = speech.isSpeaking

    init {
        viewModelScope.launch { collectEngineEvents() }
        speakCurrentSettings()
    }

    // ------------------------------------------------------------ actions

    fun openConversation(id: String?) {
        _conversationId.value = id
        _toolRows.value = emptyList()
        _streamingText.value = ""
        messagesCollectJob?.cancel()
        messagesCollectJob = viewModelScope.launch {
            if (id == null) {
                _dbMessages.value = emptyList()
                return@launch
            }
            db.messageDao().observeAll(id)
                .catch { _dbMessages.value = emptyList() }
                .collect { _dbMessages.value = it }
        }
    }

    fun newConversation() {
        openConversation(null)
    }

    fun deleteConversation(id: String) {
        viewModelScope.launch {
            db.messageDao().deleteForConversation(id)
            db.conversationDao().delete(id)
            if (_conversationId.value == id) openConversation(null)
        }
    }

    fun send(text: String, attachment: ImageAttachment? = null) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() && attachment == null) return
        if (attachment == null && trimmed.isEmpty()) return
        viewModelScope.launch {
            _busy.value = true
            _streamingText.value = ""
            _toolRows.value = emptyList()
            val cid = engine.send(_conversationId.value, trimmed, attachment)
            if (cid != null && cid != _conversationId.value) {
                openConversation(cid)
            }
            _busy.value = false
        }
    }

    fun stopTurn() {
        engine.interrupt(_conversationId.value)
    }

    fun confirmTool(approved: Boolean) {
        val pending = pendingConfirmation.value ?: return
        engine.confirmTool(pending.toolUseId, approved)
    }

    fun startListening() {
        speakCurrentSettings()
        speech.startListening(
            SpeechManager.ListenerCallbacks(
                onFinal = { text -> if (text.isNotBlank()) send(text) },
                onError = { message, code ->
                    // ERROR_NO_MATCH (7) is routine silence; keep it quiet.
                    if (code != 7) _snackbar.tryEmit(message)
                },
            ),
        )
    }

    fun stopListening() {
        speech.stopListening()
    }

    fun toggleListening() {
        if (isListening.value) stopListening() else startListening()
    }

    fun stopSpeaking() {
        speech.stopSpeaking()
    }

    // ------------------------------------------------------------ engine plumbing

    private suspend fun collectEngineEvents() {
        engine.events.collectLatest { event ->
            when (event) {
                is EngineEvent.TextDelta -> {
                    if (event.conversationId == currentEventCid()) {
                        _streamingText.value = _streamingText.value + event.delta
                    }
                }
                is EngineEvent.ToolStarted -> {
                    upsertToolRow(
                        ChatItem.Tool(
                            key = "t_${event.toolUseId}",
                            toolUseId = event.toolUseId,
                            name = event.name,
                            label = event.label,
                            running = true,
                            success = true,
                            message = "",
                            card = null,
                            ts = System.currentTimeMillis(),
                        ),
                    )
                }
                is EngineEvent.ToolFinished -> {
                    val existing = _toolRows.value.firstOrNull { it.toolUseId == event.toolUseId }
                    upsertToolRow(
                        ChatItem.Tool(
                            key = "t_${event.toolUseId}",
                            toolUseId = event.toolUseId,
                            name = event.name,
                            label = existing?.label ?: event.name.replace('_', ' '),
                            running = false,
                            success = event.success,
                            message = shortMessage(event.message),
                            card = event.card,
                            ts = existing?.ts ?: System.currentTimeMillis(),
                        ),
                    )
                }
                is EngineEvent.Speak -> {
                    if (app.settings.state.value.voiceEnabled) {
                        speech.speak(event.text)
                    }
                }
                is EngineEvent.TurnFinished -> {
                    if (event.conversationId == currentEventCid()) {
                        _streamingText.value = ""
                        if (event.interrupted) {
                            _snackbar.tryEmit("أوقفتُ الرد")
                        }
                    }
                }
                is EngineEvent.TurnFailed -> {
                    if (event.conversationId == currentEventCid()) {
                        _streamingText.value = ""
                        _snackbar.tryEmit(event.message)
                    }
                }
                is EngineEvent.ConfirmationNeeded -> Unit // surfaced via pendingConfirmation flow
                is EngineEvent.TurnStarted -> Unit
                is EngineEvent.UserPersisted -> Unit
                is EngineEvent.StateChanged -> Unit
            }
        }
    }

    private fun currentEventCid(): String? = _conversationId.value ?: engine.activeConversation.value

    private fun upsertToolRow(row: ChatItem.Tool) {
        val current = _toolRows.value.toMutableList()
        val index = current.indexOfFirst { it.toolUseId == row.toolUseId }
        if (index >= 0) current[index] = row else current += row
        _toolRows.value = current
    }

    private fun shortMessage(message: String): String =
        message.replace(Regex("\\s+"), " ").trim().take(160)

    private fun speakCurrentSettings() {
        val settings = app.settings.state.value
        speech.setSpeechLanguage(if (settings.language == "en") "en" else "ar")
        speech.setVoice(settings.voiceName, settings.speechRate)
    }

    override fun onCleared() {
        speech.stopListening(release = true)
        super.onCleared()
    }
}

/** Formats epoch millis as a short Arabic clock time (e.g. ٥:٣٢ م). */
fun formatClock(ts: Long): String {
    val fmt = java.time.format.DateTimeFormatter.ofPattern("h:mm a", java.util.Locale("ar"))
        .withZone(ZoneId.systemDefault())
    return fmt.format(Instant.ofEpochMilli(ts))
}

fun formatDay(ts: Long): String {
    val now = java.time.LocalDate.now(ZoneId.systemDefault())
    val date = Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault()).toLocalDate()
    return when (date) {
        now -> "اليوم"
        now.minusDays(1) -> "أمس"
        else -> java.time.format.DateTimeFormatter.ofPattern("d MMMM", java.util.Locale("ar")).format(date)
    }
}
