package app.majwuda.services

import app.majwuda.MajwudaApp
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import app.majwuda.R
import app.majwuda.core.NLog
import app.majwuda.ui.AssistActivity

/**
 * Opt-in hands-free mode: keeps مجودة listening for «يا مجودة» while the screen is off or in use.
 * Relies on Google's on-device speech recognizer, so battery cost is real — it is strictly opt-in
 * and stops itself whenever the user says stop or disables the toggle.
 */
class WakeWordService : Service() {

    private var recognizer: android.speech.SpeechRecognizer? = null
    private var backoffMillis = INITIAL_BACKOFF_MS
    private var running = false
    private val restartHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val restartRunnable = Runnable { if (running) listenOnce() }

    override fun onCreate() {
        super.onCreate()
        running = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(NOTIFICATION_ID, buildNotification())
        listenOnce()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun listenOnce() {
        destroyRecognizer()
        if (!running) return
        if (!android.speech.SpeechRecognizer.isRecognitionAvailable(this)) {
            NLog.w("Wake", "speech recognition unavailable on this device")
            stopSelf()
            return
        }
        val instance = try {
            android.speech.SpeechRecognizer.createSpeechRecognizer(this)
        } catch (t: Throwable) {
            NLog.e("Wake", "cannot create recognizer", t)
            scheduleRestart()
            return
        }
        recognizer = instance
        instance.setRecognitionListener(object : android.speech.RecognitionListener {
            override fun onReadyForSpeech(params: android.os.Bundle?) {
                backoffMillis = INITIAL_BACKOFF_MS
            }

            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit

            override fun onError(error: Int) {
                if (!running) return
                // No match / timeouts are normal while idling; restart quickly.
                val fatal = error == android.speech.SpeechRecognizer.ERROR_RECOGNIZER_BUSY ||
                    error == android.speech.SpeechRecognizer.ERROR_CLIENT ||
                    error == android.speech.SpeechRecognizer.ERROR_NETWORK ||
                    error == android.speech.SpeechRecognizer.ERROR_NETWORK_TIMEOUT
                backoffMillis = if (fatal) (backoffMillis * 2).coerceAtMost(MAX_BACKOFF_MS) else INITIAL_BACKOFF_MS
                scheduleRestart()
            }

            override fun onResults(results: android.os.Bundle?) {
                if (!running) return
                val texts = results?.getStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                val transcript = texts.firstOrNull()?.trim().orEmpty()
                if (transcript.isNotEmpty()) {
                    val match = app.majwuda.core.ArabicText.findWakePhrase(transcript, WAKE_NAMES)
                    if (match != null) {
                        val command = match.remainder
                        launchAssistant(command)
                        // Pause listening while the assistant is in use.
                        backoffMillis = 4_000L
                    }
                }
                scheduleRestart()
            }

            override fun onPartialResults(partialResults: android.os.Bundle?) = Unit
            override fun onEvent(eventType: Int, params: android.os.Bundle?) = Unit
        })
        try {
            instance.startListening(wakeIntent())
        } catch (t: Throwable) {
            NLog.e("Wake", "startListening failed", t)
            scheduleRestart()
        }
    }

    private fun wakeIntent(): Intent =
        Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, "ar")
            putExtra(android.speech.RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(android.speech.RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(android.speech.RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1200L)
        }

    private fun launchAssistant(command: String) {
        try {
            val intent = Intent(this, AssistActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(AssistActivity.EXTRA_LISTEN, true)
                if (command.isNotBlank()) putExtra(AssistActivity.EXTRA_INITIAL_COMMAND, command)
            }
            startActivity(intent)
        } catch (t: Throwable) {
            NLog.e("Wake", "cannot launch assistant", t)
        }
    }

    private fun scheduleRestart() {
        restartHandler.removeCallbacks(restartRunnable)
        restartHandler.postDelayed(restartRunnable, backoffMillis)
    }

    private fun destroyRecognizer() {
        val active = recognizer
        recognizer = null
        if (active != null) {
            try {
                active.cancel()
            } catch (_: Exception) {
            }
            try {
                active.destroy()
            } catch (_: Exception) {
            }
        }
    }

    private fun buildNotification(): Notification {
        val tapIntent = Intent(this, AssistActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(AssistActivity.EXTRA_LISTEN, true)
        }
        val contentIntent = PendingIntent.getActivity(this, 41, tapIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stopIntent = Intent(this, WakeWordService::class.java).apply { action = ACTION_STOP }
        val stopPending = PendingIntent.getService(this, 42, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, MajwudaApp.CHANNEL_WAKE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.notif_wake_title))
            .setContentText(getString(R.string.notif_wake_text))
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, getString(R.string.notif_wake_stop), stopPending)
            .build()
    }

    override fun onDestroy() {
        running = false
        restartHandler.removeCallbacks(restartRunnable)
        destroyRecognizer()
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "app.majwuda.action.WAKE_STOP"
        private const val NOTIFICATION_ID = 7001
        private const val INITIAL_BACKOFF_MS = 600L
        private const val MAX_BACKOFF_MS = 30_000L

        val WAKE_NAMES = listOf("مجودة", "مجود", "majwuda", "majwoda", "majouda", "mojooda")
    }
}
