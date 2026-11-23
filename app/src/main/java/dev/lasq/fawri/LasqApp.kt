package dev.lasq.fawri

import android.app.Application
import android.content.Context
import dev.lasq.fawri.data.*
import dev.lasq.fawri.platform.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class LasqApp : Application() {
    val prefs by lazy { Prefs(this) }
    val vault by lazy { Vault(this) }
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val active = MutableStateFlow(false)
    val messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val integrity by lazy { Integrity.inspect(this) }

    fun report(s: String) {
        messages.tryEmit(s)
    }

    fun cleanLater() {
        scope.launch {
            try {
                val due = vault.prune(prefs.value.days)
                Maintenance.plan(this@LasqApp, due)
            } catch (_: Exception) {
                report("تعذّر فتح السجل المشفّر. لم نحذف بياناتك.")
            }
        }
    }
}

val Context.app
    get() = applicationContext as LasqApp
