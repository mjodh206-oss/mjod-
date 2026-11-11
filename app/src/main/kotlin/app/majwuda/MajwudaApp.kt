package app.majwuda

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import app.majwuda.data.SettingsRepository
import app.majwuda.data.db.MajwudaDatabase
import app.majwuda.tools.ToolContext
import app.majwuda.tools.Tools
import app.majwuda.core.NLog

/** Process-wide container: settings, database, tooling. */
class MajwudaApp : Application() {

    lateinit var settings: SettingsRepository
        private set

    lateinit var database: MajwudaDatabase
        private set

    lateinit var toolContext: ToolContext
        private set

    lateinit var engine: app.majwuda.assistant.AssistantEngine
        private set

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        settings = SettingsRepository.get(this)
        database = MajwudaDatabase.get(this)
        toolContext = ToolContext(applicationContext, database, settings)
        engine = app.majwuda.assistant.AssistantEngine(this, toolContext, Tools.registry(toolContext))
        NLog.i("App", "majwuda started")
    }

    private fun createNotificationChannels() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        val reminders = NotificationChannel(CHANNEL_REMINDERS, getString(R.string.channel_reminders_name), NotificationManager.IMPORTANCE_HIGH).apply {
            description = getString(R.string.channel_reminders_desc)
        }
        val wake = NotificationChannel(CHANNEL_WAKE, getString(R.string.channel_wake_name), NotificationManager.IMPORTANCE_LOW).apply {
            description = getString(R.string.channel_wake_desc)
        }
        val status = NotificationChannel(CHANNEL_STATUS, getString(R.string.channel_status_name), NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = getString(R.string.channel_status_desc)
        }
        manager.createNotificationChannels(listOf(reminders, wake, status))
    }

    companion object {
        const val CHANNEL_REMINDERS = "reminders"
        const val CHANNEL_WAKE = "wake"
        const val CHANNEL_STATUS = "status"

        fun from(context: Context): MajwudaApp =
            context.applicationContext as MajwudaApp
    }
}
