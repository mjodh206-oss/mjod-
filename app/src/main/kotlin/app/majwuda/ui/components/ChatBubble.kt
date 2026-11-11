package app.majwuda.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.majwuda.ui.ChatItem
import app.majwuda.ui.theme.MajwudaPalette

/** One row of the transcript. */
@Composable
fun ChatRow(item: ChatItem, modifier: Modifier = Modifier) {
    when (item) {
        is ChatItem.User -> UserBubble(item, modifier)
        is ChatItem.Assistant -> AssistantBubble(item.text, item.interrupted, modifier)
        is ChatItem.Tool -> ToolRow(item, modifier)
    }
}

@Composable
fun UserBubble(item: ChatItem.User, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.fillMaxWidth(0.86f)) {
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 4.dp, bottomStart = 18.dp, bottomEnd = 18.dp))
                    .background(MajwudaPalette.UserBubble)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                if (item.hasImage) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Image, null, tint = MajwudaPalette.Teal, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("صورة مرفقة", style = MaterialTheme.typography.labelSmall, color = MajwudaPalette.Teal)
                    }
                    Spacer(Modifier.size(4.dp))
                }
                Text(item.text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable
fun AssistantBubble(text: String, interrupted: Boolean, modifier: Modifier = Modifier, streaming: Boolean = false) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Column(modifier = Modifier.fillMaxWidth(0.92f)) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 18.dp))
                    .background(MajwudaPalette.AssistantBubble)
                    .border(BorderStroke(1.dp, MajwudaPalette.OutlineSoft), RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 18.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            }
            if (interrupted) {
                Text(
                    "توقفتُ هنا (أوقفتَ الرد)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MajwudaPalette.TextTertiary,
                    modifier = Modifier.padding(top = 2.dp, start = 4.dp),
                )
            }
        }
    }
}

@Composable
fun ToolRow(item: ChatItem.Tool, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(MajwudaPalette.SurfaceHigh)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (item.running) {
                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = MajwudaPalette.Teal)
            } else if (item.success) {
                Icon(Icons.Filled.CheckCircle, null, tint = MajwudaPalette.Green, modifier = Modifier.size(16.dp))
            } else {
                Icon(Icons.Filled.Close, null, tint = MajwudaPalette.Red, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.width(8.dp))
            Column {
                Text(item.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
                AnimatedVisibility(visible = item.message.isNotBlank(), enter = fadeIn(), exit = fadeOut()) {
                    Text(
                        item.message,
                        style = MaterialTheme.typography.labelSmall,
                        color = MajwudaPalette.TextSecondary,
                        maxLines = 2,
                    )
                }
            }
        }
    }
    item.card?.let { card ->
        ToolCardView(card = card, modifier = Modifier.padding(top = 4.dp))
    }
}

/** Inline "thinking" shimmer row shown while the model is working before any token arrived. */
@Composable
fun ThinkingRow(modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.Psychology, null, tint = MajwudaPalette.VioletSoft, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("مجودة تفكر…", style = MaterialTheme.typography.bodyMedium, color = MajwudaPalette.TextSecondary)
    }
}

/** Confirmation bar shown above the composer for destructive/sensitive actions. */
@Composable
fun ConfirmationBar(
    prompt: String,
    onApprove: () -> Unit,
    onDeny: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MajwudaPalette.SurfaceHighest)
            .border(BorderStroke(1.dp, MajwudaPalette.Amber.copy(alpha = 0.5f)), RoundedCornerShape(16.dp))
            .padding(12.dp),
    ) {
        Text("تأكيد مطلوب", style = MaterialTheme.typography.labelMedium, color = MajwudaPalette.Amber, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        Spacer(Modifier.size(4.dp))
        Text(prompt, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Start)
        Spacer(Modifier.size(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            androidx.compose.material3.Button(
                onClick = onApprove,
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = MajwudaPalette.Teal, contentColor = MajwudaPalette.SpaceDeep),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("نفّذ", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            }
            androidx.compose.material3.OutlinedButton(
                onClick = onDeny,
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("لا")
            }
        }
    }
}
