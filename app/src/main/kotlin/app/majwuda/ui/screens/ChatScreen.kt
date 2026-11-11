package app.majwuda.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.majwuda.assistant.EngineState
import app.majwuda.ui.ChatItem
import app.majwuda.ui.ChatViewModel
import app.majwuda.ui.components.AssistantBubble
import app.majwuda.ui.components.ChatRow
import app.majwuda.ui.components.ConfirmationBar
import app.majwuda.ui.components.Orb
import app.majwuda.ui.components.ThinkingRow
import app.majwuda.ui.components.Waveform
import app.majwuda.ui.theme.MajwudaPalette
import kotlinx.coroutines.launch
import java.time.LocalTime

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    vm: ChatViewModel,
    onOpenSettings: () -> Unit,
    onOpenConversations: () -> Unit,
    onOpenNotes: () -> Unit,
    onOpenReminders: () -> Unit,
    onOpenMemory: () -> Unit,
) {
    val items by vm.items.collectAsState()
    val streaming by vm.streamingText.collectAsState()
    val engineState by vm.engineState.collectAsState()
    val busy by vm.busy.collectAsState()
    val pending by vm.pendingConfirmation.collectAsState()
    val isListening by vm.isListening.collectAsState()
    val voiceLevel by vm.voiceLevel.collectAsState()
    val partial by vm.partialTranscript.collectAsState()

    var input by remember { mutableStateOf("") }
    var menuOpen by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val settings = app.majwuda.MajwudaApp.from(LocalContext.current).settings.state.collectAsState().value

    // Snackbar events from the view model
    LaunchedEffect(Unit) {
        vm.snackbar.collect { message -> snackbarHostState.showSnackbar(message) }
    }

    // Auto-scroll to the latest row
    val rowCount = items.size + if (streaming.isNotEmpty() || busy) 1 else 0
    LaunchedEffect(rowCount, streaming.length) {
        if (rowCount > 0) {
            listState.animateScrollToItem(rowCount - 1)
        }
    }

    val visualState = if (isListening) EngineState.LISTENING else engineState
    val isEmpty = items.isEmpty() && streaming.isEmpty() && !busy

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            settings.assistantName.ifBlank { "مجودة" },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            if (busy) "تعمل الآن…" else "جاهزة لمساعدتك",
                            style = MaterialTheme.typography.labelSmall,
                            color = MajwudaPalette.TextSecondary,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { vm.newConversation() }) {
                        Icon(Icons.Filled.Add, contentDescription = "محادثة جديدة")
                    }
                    IconButton(onClick = onOpenConversations) {
                        Icon(Icons.Filled.History, contentDescription = "المحادثات")
                    }
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "المزيد")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(text = { Text("الملاحظات") }, onClick = { menuOpen = false; onOpenNotes() }, leadingIcon = { Icon(Icons.Filled.Notes, null) })
                        DropdownMenuItem(text = { Text("التذكيرات") }, onClick = { menuOpen = false; onOpenReminders() }, leadingIcon = { Icon(Icons.Filled.NotificationsActive, null) })
                        DropdownMenuItem(text = { Text("الذاكرة") }, onClick = { menuOpen = false; onOpenMemory() }, leadingIcon = { Icon(Icons.Filled.Psychology, null) })
                        DropdownMenuItem(text = { Text("الإعدادات") }, onClick = { menuOpen = false; onOpenSettings() }, leadingIcon = { Icon(Icons.Filled.Settings, null) })
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (isEmpty) {
                EmptyHero(
                    assistantName = settings.assistantName.ifBlank { "مجودة" },
                    userName = settings.userName,
                    state = visualState,
                    level = voiceLevel,
                    onSuggestion = { suggestion -> vm.send(suggestion) },
                    modifier = Modifier.weight(1f),
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 14.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(items.size, key = { items[it].key }) { index ->
                        ChatRow(items[index])
                    }
                    if (streaming.isNotEmpty()) {
                        item(key = "streaming") {
                            AssistantBubble(text = streaming, interrupted = false, streaming = true)
                        }
                    } else if (busy && items.none { it is ChatItem.Tool && it.running }) {
                        item(key = "thinking") { ThinkingRow() }
                    }
                }
            }

            // Confirmation zone above the composer
            pending?.let { request ->
                ConfirmationBar(
                    prompt = request.prompt,
                    onApprove = { vm.confirmTool(true) },
                    onDeny = { vm.confirmTool(false) },
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                )
            }

            // Voice / confirm zone above the composer
            if (isListening) {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp)) {
                    Text(
                        text = partial.ifBlank { "أسمعك…" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MajwudaPalette.Teal,
                        maxLines = 2,
                    )
                    Waveform(level = voiceLevel, active = true, modifier = Modifier.fillMaxWidth())
                }
            }

            // Composer
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = CircleShape,
                    color = if (isListening) MajwudaPalette.Amber else MajwudaPalette.SurfaceHigh,
                    modifier = Modifier.size(48.dp).clickable { vm.toggleListening() },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            if (isListening) Icons.Filled.Stop else Icons.Filled.Mic,
                            contentDescription = if (isListening) "إيقاف الاستماع" else "تحدث مع مجودة",
                            tint = if (isListening) MajwudaPalette.SpaceDeep else MajwudaPalette.Teal,
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("اسأل مجودة… أو اضغط الميكروفون") },
                    shape = RoundedCornerShape(24.dp),
                    maxLines = 4,
                )
                Spacer(Modifier.width(8.dp))
                val canSend = input.isNotBlank() && !busy
                Surface(
                    shape = CircleShape,
                    color = if (canSend) MajwudaPalette.Teal else MajwudaPalette.SurfaceHigh,
                    modifier = Modifier.size(48.dp).clickable(enabled = canSend) {
                        val text = input.trim()
                        input = ""
                        vm.send(text)
                    },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (busy) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MajwudaPalette.Teal)
                        } else {
                            Icon(Icons.Filled.Send, contentDescription = "أرسل", tint = if (canSend) MajwudaPalette.SpaceDeep else MajwudaPalette.TextTertiary)
                        }
                    }
                }
            }
        }
    }
}

private fun greeting(): String {
    val hour = LocalTime.now().hour
    return when {
        hour < 5 -> "سهرة طيبة"
        hour < 12 -> "صباح الخير"
        hour < 17 -> "مساء الخير"
        else -> "مساء الخير"
    }
}

@Composable
private fun EmptyHero(
    assistantName: String,
    userName: String,
    state: EngineState,
    level: Float,
    onSuggestion: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val suggestions = listOf(
        "ما الطقس اليوم؟",
        "ذكرني أشرب مي كل ساعتين",
        "احسب ١٥ بالمية من ٢٤٠",
        "افتح يوتيوب",
        "شو مواعيدي اليوم؟",
        "اكتب ملاحظة: اجتماع مع الفريق بكرة",
    )
    Column(
        modifier = modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Orb(state = state, level = level, size = 148.dp)
        Spacer(Modifier.height(18.dp))
        Text(
            text = buildString {
                append(greeting())
                if (userName.isNotBlank()) append("، $userName")
            },
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = "أنا $assistantName — مساعدتك الذكية بقلب كلود",
            style = MaterialTheme.typography.bodyMedium,
            color = MajwudaPalette.TextSecondary,
        )
        Spacer(Modifier.height(20.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            suggestions.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { suggestion ->
                        Text(
                            text = suggestion,
                            style = MaterialTheme.typography.labelMedium,
                            color = MajwudaPalette.Teal,
                            modifier = Modifier
                                .clip(RoundedCornerShape(18.dp))
                                .background(MajwudaPalette.SurfaceHigh)
                                .border(1.dp, MajwudaPalette.OutlineSoft, RoundedCornerShape(18.dp))
                                .clickable { onSuggestion(suggestion) }
                                .padding(horizontal = 14.dp, vertical = 9.dp),
                        )
                    }
                }
            }
        }
    }
}
