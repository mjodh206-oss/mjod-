package app.majwuda.ui.components

import android.content.Intent
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.StickyNote2
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import app.majwuda.tools.CardRow
import app.majwuda.tools.ToolCard
import app.majwuda.ui.theme.MajwudaPalette
import java.io.File

/** Renders any [ToolCard] produced by the tools into a rich, tappable card. */
@Composable
fun ToolCardView(card: ToolCard, modifier: Modifier = Modifier) {
    when (card) {
        is ToolCard.Plain -> PlainCard(card.title, card.body, modifier)
        is ToolCard.KeyValues -> KeyValuesCard(card.title, card.rows, modifier)
        is ToolCard.ListItems -> ListCard(card.title, card.items, card.footer, modifier)
        is ToolCard.Table -> TableCard(card.title, card.header, card.rows, modifier)
        is ToolCard.Weather -> WeatherCard(card, modifier)
        is ToolCard.Image -> ImageCard(card.title, card.path, modifier)
        is ToolCard.Note -> NoteCard(card.title, card.body, card.tags, modifier)
        is ToolCard.Reminder -> ReminderCard(card.title, card.body, card.at, card.done, modifier)
        is ToolCard.WebLinks -> WebLinksCard(card.query, card.links, modifier)
    }
}

@Composable
private fun cardColors() = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))

@Composable
private fun CardTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun PlainCard(title: String, body: String, modifier: Modifier) {
    Card(modifier = modifier.fillMaxWidth(), colors = cardColors()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            CardTitle(title)
            Text(body, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun KeyValuesCard(title: String, rows: List<CardRow>, modifier: Modifier) {
    Card(modifier = modifier.fillMaxWidth(), colors = cardColors()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CardTitle(title)
            rows.forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(row.label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(row.value.orEmpty(), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun ListCard(title: String, items: List<CardRow>, footer: String?, modifier: Modifier) {
    Card(modifier = modifier.fillMaxWidth(), colors = cardColors()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CardTitle(title)
            items.forEachIndexed { index, row ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${index + 1}.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(row.label, style = MaterialTheme.typography.bodyMedium)
                        row.sub?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        row.value?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
                if (index != items.lastIndex) HorizontalDivider(color = MajwudaPalette.OutlineSoft)
            }
            footer?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun TableCard(title: String, header: List<String>, rows: List<List<String>>, modifier: Modifier) {
    Card(modifier = modifier.fillMaxWidth(), colors = cardColors()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CardTitle(title)
            if (header.isNotEmpty()) {
                Row(Modifier.fillMaxWidth()) {
                    header.forEach { cell ->
                        Text(
                            cell,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                HorizontalDivider(color = MajwudaPalette.OutlineSoft)
            }
            rows.forEach { row ->
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    row.forEach { cell -> Text(cell, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun WeatherCard(card: ToolCard.Weather, modifier: Modifier) {
    Card(modifier = modifier.fillMaxWidth(), colors = cardColors()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Cloud, contentDescription = null, tint = MajwudaPalette.Teal)
                Spacer(Modifier.width(8.dp))
                Text(card.place, style = MaterialTheme.typography.titleMedium)
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(card.temperature, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(card.description, style = MaterialTheme.typography.bodyMedium)
                    card.feelsLike?.let { Text("الإحساس $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                card.humidity?.let { MiniStat("الرطوبة", it) }
                card.wind?.let { MiniStat("الرياح", it) }
                card.precipitation?.let { MiniStat("الأمطار", it) }
            }
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ImageCard(title: String, path: String, modifier: Modifier) {
    val context = LocalContext.current
    val bitmap = androidx.compose.runtime.remember(path) {
        runCatching { BitmapFactory.decodeFile(path) }.getOrNull()
    }
    var expanded by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    Card(modifier = modifier.fillMaxWidth(), colors = cardColors()) {
        Column {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Image, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.labelLarge)
            }
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clickable { expanded = true },
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                )
            } else {
                Text(
                    "تعذر عرض الصورة (${File(path).name})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }
    }
    if (expanded && bitmap != null) {
        Dialog(onDismissRequest = { expanded = false }) {
            androidx.compose.foundation.Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = title,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.large)
                    .pointerInput(Unit) {},
            )
        }
    }
}

@Composable
private fun NoteCard(title: String, body: String, tags: List<String>, modifier: Modifier) {
    Card(modifier = modifier.fillMaxWidth(), colors = cardColors()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.StickyNote2, null, tint = MajwudaPalette.Amber)
                Spacer(Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            Text(body, style = MaterialTheme.typography.bodyMedium)
            if (tags.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(tags) { tag ->
                        Text(
                            "#$tag",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .background(MajwudaPalette.Teal.copy(alpha = 0.12f), MaterialTheme.shapes.small)
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReminderCard(title: String, body: String?, at: Long, done: Boolean, modifier: Modifier) {
    Card(modifier = modifier.fillMaxWidth(), colors = cardColors()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Alarm, null, tint = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MajwudaPalette.Amber)
            Spacer(Modifier.width(10.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium, color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                body?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Text(formatReminderTime(at), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

private fun formatReminderTime(at: Long): String {
    val fmt = java.time.format.DateTimeFormatter.ofPattern("EEEE d MMMM، h:mm a", java.util.Locale("ar"))
        .withZone(java.time.ZoneId.systemDefault())
    return fmt.format(java.time.Instant.ofEpochMilli(at))
}

@Composable
private fun WebLinksCard(query: String, links: List<CardRow>, modifier: Modifier) {
    val context = LocalContext.current
    Card(modifier = modifier.fillMaxWidth(), colors = cardColors()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CardTitle("نتائج «$query»")
            links.forEach { link ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            link.value?.let { url ->
                                runCatching {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                }
                            }
                        }
                        .padding(vertical = 2.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Link, null, tint = MajwudaPalette.VioletSoft, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(link.label, style = MaterialTheme.typography.bodyMedium, color = MajwudaPalette.VioletSoft)
                    }
                    link.sub?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2) }
                }
            }
        }
    }
}
