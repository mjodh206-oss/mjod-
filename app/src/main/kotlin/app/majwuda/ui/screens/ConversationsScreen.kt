package app.majwuda.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.majwuda.ui.ChatViewModel
import app.majwuda.ui.formatDay
import app.majwuda.ui.theme.MajwudaPalette

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ConversationsScreen(vm: ChatViewModel, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val conversations by vm.conversations.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("المحادثات", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "رجوع") }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    vm.newConversation()
                    onBack()
                },
                containerColor = MajwudaPalette.Teal,
                contentColor = MajwudaPalette.SpaceDeep,
            ) {
                Icon(Icons.Filled.Add, contentDescription = "محادثة جديدة")
            }
        },
    ) { padding ->
        if (conversations.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.ChatBubbleOutline, null, tint = MajwudaPalette.TextTertiary, modifier = Modifier.size(56.dp))
                    Spacer(Modifier.size(12.dp))
                    Text("لا توجد محادثات بعد", style = MaterialTheme.typography.titleMedium, color = MajwudaPalette.TextSecondary)
                    Text("اسأل مجودة أي شيء وستظهر هنا", style = MaterialTheme.typography.bodySmall, color = MajwudaPalette.TextTertiary)
                }
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp),
        ) {
            items(items = conversations, key = { it.id }) { conversation ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            vm.openConversation(conversation.id)
                            onOpen(conversation.id)
                        }
                        .padding(vertical = 10.dp, horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (conversation.pinned) {
                        Icon(Icons.Filled.PushPin, null, tint = MajwudaPalette.Amber, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            conversation.title,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            formatDay(conversation.updatedAt),
                            style = MaterialTheme.typography.labelSmall,
                            color = MajwudaPalette.TextTertiary,
                        )
                    }
                    IconButton(onClick = { vm.deleteConversation(conversation.id) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "حذف", tint = MajwudaPalette.TextSecondary)
                    }
                }
            }
        }
    }
}
