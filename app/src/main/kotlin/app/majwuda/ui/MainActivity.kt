package app.majwuda.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import app.majwuda.MajwudaApp
import app.majwuda.ui.screens.ChatScreen
import app.majwuda.ui.screens.ConversationsScreen
import app.majwuda.ui.screens.MemoryScreen
import app.majwuda.ui.screens.NotesScreen
import app.majwuda.ui.screens.RemindersScreen
import app.majwuda.ui.screens.SettingsScreen
import app.majwuda.ui.theme.MajwudaTheme

private sealed interface Route {
    data object Chat : Route
    data object Conversations : Route
    data object Settings : Route
    data object Notes : Route
    data object Reminders : Route
    data object Memory : Route
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val app = MajwudaApp.from(LocalContext.current)
            val themeMode by app.settings.state.collectAsState()
            val vm = androidx.lifecycle.viewmodel.compose.viewModel<ChatViewModel>()
            MajwudaTheme(themeMode = themeMode.theme) {
                RequestPermissionsOnFirstLaunch()
                AppRoot(vm = vm, startRoute = startRouteFor(intent))
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Re-resolve the start surface; Compose state picks it up via intent recreation.
        recreate()
    }

    private fun startRouteFor(intent: Intent?): Route = when (intent?.action) {
        ACTION_TALK -> Route.Chat
        ACTION_NEW_CHAT -> Route.Chat
        ACTION_CAMERA -> Route.Chat
        else -> Route.Chat
    }

    companion object {
        const val ACTION_TALK = "app.majwuda.action.TALK"
        const val ACTION_NEW_CHAT = "app.majwuda.action.NEW_CHAT"
        const val ACTION_CAMERA = "app.majwuda.action.CAMERA"
    }
}

@Composable
private fun AppRoot(vm: ChatViewModel, startRoute: Route) {
    var route by remember { mutableStateOf<Route>(startRoute) }

    // Shortcut side effects: brand-new conversation on "new chat"
    val context = LocalContext.current
    androidx.compose.runtime.LaunchedEffect(Unit) {
        if ((context as? android.app.Activity)?.intent?.action == MainActivity.ACTION_NEW_CHAT) {
            vm.newConversation()
        }
    }

    BackHandler(enabled = route != Route.Chat) { route = Route.Chat }

    when (route) {
        Route.Chat -> ChatScreen(
            vm = vm,
            onOpenSettings = { route = Route.Settings },
            onOpenConversations = { route = Route.Conversations },
            onOpenNotes = { route = Route.Notes },
            onOpenReminders = { route = Route.Reminders },
            onOpenMemory = { route = Route.Memory },
        )
        Route.Conversations -> ConversationsScreen(
            vm = vm,
            onBack = { route = Route.Chat },
            onOpen = { route = Route.Chat },
        )
        Route.Settings -> SettingsScreen(onBack = { route = Route.Chat })
        Route.Notes -> NotesScreen(onBack = { route = Route.Chat })
        Route.Reminders -> RemindersScreen(onBack = { route = Route.Chat })
        Route.Memory -> MemoryScreen(onBack = { route = Route.Chat })
    }
}
