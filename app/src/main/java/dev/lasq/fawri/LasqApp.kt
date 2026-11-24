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
    val integrity by lazy { Integrity.inspect(this) }
    val recorder by lazy { Recorder(this) }

    override fun onCreate() {
        super.onCreate()
        // v1 could have a persisted expiry job. Upgrades have no such job or service.
        system<android.app.job.JobScheduler>().cancel(4760)
    }
}

val Context.app
    get() = applicationContext as LasqApp
