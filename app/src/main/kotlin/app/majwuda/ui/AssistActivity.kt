package app.majwuda.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.majwuda.MajwudaApp
import app.majwuda.assistant.EngineEvent
import app.majwuda.assistant.EngineState
import app.majwuda.assistant.ImageAttachment
import app.majwuda.speech.SpeechManager
import app.majwuda.ui.components.Orb
import app.majwuda.ui.components.Waveform
import app.majwuda.ui.theme.MajwudaPalette
import app.majwuda.ui.theme.MajwudaTheme
import kotlinx.coroutines.launch

/**
 * The assistant overlay: launched via the ASSIST gesture/button, the assistant tile, the app
 * widget, the wake phrase, or share targets. It is intentionally lightweight — a dim scrim with
 * a compact panel — so it feels like a system surface rather than a second copy of the app.
 */
class AssistActivity : ComponentActivity() {

    private var pendingShareText: String? = null
    private var pendingImage: ImageAttachment? = null
    private var autoListen = false
    private var initialCommand: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }

        autoListen = intent?.getBooleanExtra(EXTRA_LISTEN, true) ?: true
        initialCommand = intent?.getStringExtra(EXTRA_INITIAL_COMMAND)
        parseIncomingIntent(intent)

        setContent {
            val app = MajwudaApp.from(this)
            MajwudaTheme(themeMode = app.settings.state.collectAsState().value.theme) {
                AssistPanel(
                    autoListen = autoListen,
                    initialCommand = initialCommand,
                    sharedText = pendingShareText,
                    sharedImage = pendingImage,
                    onOpenFullApp = { openFullApp() },
                    onClose = { finish() },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        autoListen = intent.getBooleanExtra(EXTRA_LISTEN, true)
        initialCommand = intent.getStringExtra(EXTRA_INITIAL_COMMAND)
        parseIncomingIntent(intent)
        // Relaunch panel content by recreating state holders: simplest is to re-set content.
        recreate()
    }

    private fun parseIncomingIntent(intent: Intent?) {
        intent ?: return
        when (intent.action) {
            Intent.ACTION_SEND -> {
                val type = intent.type ?: ""
                if (type.startsWith("image/")) {
                    val uri = getParcelableExtraCompat(intent, Intent.EXTRA_STREAM)
                    if (uri != null) {
                        decodeImageAsBase64(this, uri)?.let { (base64, mediaType) ->
                            pendingImage = ImageAttachment(base64, mediaType)
                        }
                    }
                }
                intent.getStringExtra(Intent.EXTRA_TEXT)?.let { pendingShareText = it }
            }
            Intent.ACTION_PROCESS_TEXT -> {
                @Suppress("DEPRECATION")
                val text = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
                if (!text.isNullOrBlank()) pendingShareText = text
            }
        }
    }

    private fun getParcelableExtraCompat(intent: Intent, key: String): Uri? =
        if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(key, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(key)
        }

    private fun openFullApp() {
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
        )
        finish()
    }

    companion object {
        const val EXTRA_LISTEN = "app.majwuda.extra.LISTEN"
        const val EXTRA_INITIAL_COMMAND = "app.majwuda.extra.INITIAL_COMMAND"
    }
}

@Composable
private fun AssistPanel(
    autoListen: Boolean,
    initialCommand: String?,
    sharedText: String?,
    sharedImage: ImageAttachment?,
    onOpenFullApp: () -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val app = MajwudaApp.from(context)
    val engine = app.engine
    val speech = remember { SpeechManager.get(context) }

    val engineState by engine.state.collectAsState()
    val isListening by speech.isListening.collectAsState()
    val voiceLevel by speech.voiceLevel.collectAsState()
    val partial by speech.partialTranscript.collectAsState()

    var transcript by remember { mutableStateOf("") }
    var reply by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf(sharedText.orEmpty()) }
    val scope = rememberCoroutineScope()

    fun send(text: String, attachment: ImageAttachment?) {
        if (text.isBlank() && attachment == null) return
        busy = true
        reply = ""
        scope.launch {
            engine.send(null, text.trim(), attachment)
            busy = false
        }
    }

    fun startListening() {
        speech.startListening(
            SpeechManager.ListenerCallbacks(
                onFinal = { text ->
                    transcript = text
                    send(text, null)
                },
                onError = { _, _ -> },
            ),
        )
    }

    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startListening()
    }

    fun requestMicThenListen() {
        if (hasPermission(context, android.Manifest.permission.RECORD_AUDIO)) {
            startListening()
        } else {
            micLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
        }
    }

    // Stream assistant deltas into the panel
    LaunchedEffect(Unit) {
        engine.events.collect { event ->
            when (event) {
                is EngineEvent.TextDelta -> reply += event.delta
                is EngineEvent.TurnFinished -> {
                    busy = false
                }
                is EngineEvent.TurnFailed -> {
                    busy = false
                    reply = event.message
                }
                is EngineEvent.Speak -> if (app.settings.state.value.voiceEnabled) speech.speak(event.text)
                else -> Unit
            }
        }
    }

    // Kick off the session once
    LaunchedEffect(initialCommand, sharedText, sharedImage) {
        when {
            initialCommand != null -> {
                transcript = initialCommand
                send(initialCommand, null)
            }
            sharedImage != null -> {
                transcript = sharedText ?: "شو رأيك؟"
                send(sharedText ?: "شو رأيك؟", sharedImage)
            }
            sharedText != null -> {
                transcript = sharedText
                send(sharedText, null)
            }
            autoListen -> requestMicThenListen()
        }
    }

    val visualState = if (isListening) EngineState.LISTENING else engineState

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(onClick = onClose),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(MajwudaPalette.SpaceMid)
                .border(1.dp, MajwudaPalette.OutlineSoft, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .pointerInput(Unit) { /* consume taps so the scrim doesn't dismiss */ }
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Handle + header
            Box(
                modifier = Modifier
                    .size(width = 44.dp, height = 5.dp)
                    .clip(CircleShape)
                    .background(MajwudaPalette.Outline),
            )
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "مجودة",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onOpenFullApp) {
                    Icon(Icons.Filled.OpenInNew, contentDescription = "افتح المحادثة الكاملة", tint = MajwudaPalette.TextSecondary)
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.Close, contentDescription = "إغلاق", tint = MajwudaPalette.TextSecondary)
                }
            }

            Spacer(Modifier.height(8.dp))
            Orb(state = visualState, level = voiceLevel, size = 120.dp)
            Spacer(Modifier.height(10.dp))

            val statusText = when {
                isListening -> partial.ifBlank { "أسمعك…" }
                busy -> reply.ifBlank { "أفكر…" }
                reply.isNotBlank() -> reply
                else -> "قل «يا مجودة» أو اكتب سؤالك"
            }
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isListening) MajwudaPalette.Teal else MaterialTheme.colorScheme.onBackground,
                minLines = 1,
                maxLines = 4,
            )
            Spacer(Modifier.height(6.dp))
            Waveform(level = voiceLevel, active = isListening, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))

            if (isListening) {
                Surface(
                    shape = CircleShape,
                    color = MajwudaPalette.Amber,
                    modifier = Modifier.size(52.dp).clickable { speech.stopListening() },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Stop, contentDescription = "إيقاف", tint = MajwudaPalette.SpaceDeep)
                    }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Surface(
                        shape = CircleShape,
                        color = MajwudaPalette.SurfaceHigh,
                        modifier = Modifier.size(48.dp).clickable { requestMicThenListen() },
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Mic, contentDescription = "تحدث", tint = MajwudaPalette.Teal)
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("اكتب…") },
                        maxLines = 2,
                        shape = RoundedCornerShape(22.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    val canSend = input.isNotBlank() && !busy
                    Surface(
                        shape = CircleShape,
                        color = if (canSend) MajwudaPalette.Teal else MajwudaPalette.SurfaceHigh,
                        modifier = Modifier.size(48.dp).clickable(enabled = canSend) {
                            val text = input.trim()
                            input = ""
                            transcript = text
                            send(text, null)
                        },
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("أرسل", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = if (canSend) MajwudaPalette.SpaceDeep else MajwudaPalette.TextTertiary)
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}
