package app.majwuda.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.StickyNote2
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.majwuda.MajwudaApp
import app.majwuda.ui.theme.MajwudaPalette
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dayFormatter = DateTimeFormatter.ofPattern("EEEE d MMMM، h:mm a", Locale("ar")).withZone(ZoneId.systemDefault())

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun NotesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = MajwudaApp.from(context)
    val scope = rememberCoroutineScope()
    val notes by app.database.noteDao().observeAll().collectAsState(initial = emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("الملاحظات", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "رجوع") } },
            )
        },
    ) { padding ->
        if (notes.isEmpty()) {
            EmptyHint(
                icon = Icons.Filled.StickyNote2,
                title = "لا توجد ملاحظات",
                subtitle = "قل «اكتب ملاحظة: …» أو «دوّن فكرة…» وستجدها هنا",
                modifier = Modifier.padding(padding),
            )
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp),
        ) {
            items(items = notes, key = { it.id }) { note ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MajwudaPalette.SurfaceHigh),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                note.title.ifBlank { "ملاحظة" },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = { scope.launch { app.database.noteDao().deleteById(note.id) } }) {
                                Icon(Icons.Filled.Delete, contentDescription = "حذف", tint = MajwudaPalette.TextSecondary)
                            }
                        }
                        Text(note.body, style = MaterialTheme.typography.bodyMedium, maxLines = 6, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.size(6.dp))
                        Text(
                            dayFormatter.format(Instant.ofEpochMilli(note.updatedAt)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MajwudaPalette.TextTertiary,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun RemindersScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = MajwudaApp.from(context)
    val scope = rememberCoroutineScope()
    val reminders by app.database.reminderDao().observeAll().collectAsState(initial = emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("التذكيرات", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "رجوع") } },
            )
        },
    ) { padding ->
        if (reminders.isEmpty()) {
            EmptyHint(
                icon = Icons.Filled.Alarm,
                title = "لا توجد تذكيرات",
                subtitle = "قل «ذكرني بعد نص ساعة أطلع القهوة» أو «نبّهني بكرة الساعة 9 بالاجتماع»",
                modifier = Modifier.padding(padding),
            )
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp),
        ) {
            items(items = reminders, key = { it.id }) { reminder ->
                val effectiveAt = reminder.snoozedUntil ?: reminder.dueAt
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = if (reminder.done) MajwudaPalette.Surface else MajwudaPalette.SurfaceHigh),
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { scope.launch { app.database.reminderDao().markDone(reminder.id, System.currentTimeMillis()) } }) {
                            Icon(
                                Icons.Filled.CheckCircle,
                                contentDescription = "إنجاز",
                                tint = if (reminder.done) MajwudaPalette.Green else MajwudaPalette.TextTertiary,
                            )
                        }
                        Column(Modifier.weight(1f)) {
                            Text(
                                reminder.title,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                color = if (reminder.done) MajwudaPalette.TextTertiary else MaterialTheme.colorScheme.onSurface,
                            )
                            reminder.body?.takeIf { it.isNotBlank() }?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, color = MajwudaPalette.TextSecondary, maxLines = 2)
                            }
                            Text(
                                (if (reminder.done) "أُنجزت · " else "") + dayFormatter.format(Instant.ofEpochMilli(effectiveAt)) +
                                    if (reminder.recurrence != "NONE") " · متكررة" else "",
                                style = MaterialTheme.typography.labelSmall,
                                color = MajwudaPalette.Teal,
                            )
                        }
                        IconButton(onClick = { scope.launch { app.database.reminderDao().deleteById(reminder.id) } }) {
                            Icon(Icons.Filled.Delete, contentDescription = "حذف", tint = MajwudaPalette.TextSecondary)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun MemoryScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = MajwudaApp.from(context)
    val scope = rememberCoroutineScope()
    val memories by app.database.memoryDao().observeAll().collectAsState(initial = emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("الذاكرة", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "رجوع") } },
            )
        },
    ) { padding ->
        if (memories.isEmpty()) {
            EmptyHint(
                icon = Icons.Filled.Psychology,
                title = "لا توجد ذكريات محفوظة",
                subtitle = "تستطيع مجودة أن تتذكر عنك اسمك وتفضيلاتك — قل «احفظ أنني…»",
                modifier = Modifier.padding(padding),
            )
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp),
        ) {
            items(items = memories, key = { it.id }) { memory ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MajwudaPalette.SurfaceHigh),
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(memory.text, style = MaterialTheme.typography.bodyMedium)
                            if (memory.tags.isNotBlank()) {
                                Text(memory.tags, style = MaterialTheme.typography.labelSmall, color = MajwudaPalette.VioletSoft)
                            }
                        }
                        IconButton(onClick = { scope.launch { app.database.memoryDao().deleteById(memory.id) } }) {
                            Icon(Icons.Filled.Delete, contentDescription = "حذف", tint = MajwudaPalette.TextSecondary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyHint(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, tint = MajwudaPalette.TextTertiary, modifier = Modifier.size(56.dp))
        Spacer(Modifier.size(12.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = MajwudaPalette.TextSecondary)
        Spacer(Modifier.size(6.dp))
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MajwudaPalette.TextTertiary, modifier = Modifier.padding(horizontal = 40.dp))
    }
}
