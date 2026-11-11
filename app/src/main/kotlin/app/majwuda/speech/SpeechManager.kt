package app.majwuda.speech

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import app.majwuda.core.ArabicText
import app.majwuda.core.NLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID

data class VoiceInfo(
    val name: String,
    val locale: String,
    val languageLabel: String,
    val quality: Int,
    val requiresNetwork: Boolean,
    val isNetworkVoice: Boolean,
) {
    val qualityLabel: String get() = when (quality) {
        Voice.QUALITY_VERY_HIGH -> "ممتازة"
        Voice.QUALITY_HIGH -> "عالية"
        Voice.QUALITY_NORMAL -> "عادية"
        else -> "أساسية"
    }
}

/**
 * Single shared speech engine: Google-powered SpeechRecognizer for listening and TextToSpeech
 * for talking. All listening is push-to-talk (or the opt-in wake service); nothing is recorded
 * continuously without that explicit opt-in.
 */
class SpeechManager private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @Volatile
    private var recognizer: SpeechRecognizer? = null

    @Volatile
    private var tts: TextToSpeech? = null

    @Volatile
    private var ttsReady = false

    @Volatile
    private var speechLanguage: Locale = Locale("ar")

    @Volatile
    private var currentVoiceName: String = ""

    @Volatile
    private var speechRate: Float = 1.0f

    @Volatile
    private var speechPitch: Float = 1.05f

    private var silenceWatcher: Job? = null

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _partialTranscript = MutableStateFlow("")
    val partialTranscript: StateFlow<String> = _partialTranscript.asStateFlow()

    /** 0..1 smoothed level for waveform rendering (synthesised from timing, not from raw audio). */
    private val _voiceLevel = MutableStateFlow(0f)
    val voiceLevel: StateFlow<Float> = _voiceLevel.asStateFlow()

    /** Last final transcript, for debugging/UI echo. */
    private val _lastTranscript = MutableStateFlow("")
    val lastTranscript: StateFlow<String> = _lastTranscript.asStateFlow()

    fun setSpeechLanguage(tag: String) {
        speechLanguage = when (tag.lowercase(Locale.ROOT)) {
            "en" -> Locale.ENGLISH
            else -> Locale("ar") // ar-SA / ar-EG / ar select the same recognizer; Android uses its configured ar locale
        }
        tts?.let { engine ->
            try {
                engine.language = speechLanguage
            } catch (_: Exception) {
            }
        }
    }

    fun setVoice(name: String, rate: Float) {
        currentVoiceName = name
        speechRate = rate.coerceIn(0.5f, 2.0f)
        applyVoiceToEngine()
    }

    fun setPitch(pitch: Float) {
        speechPitch = pitch.coerceIn(0.5f, 2.0f)
    }

    private fun ensureTts(): TextToSpeech {
        tts?.let { return it }
        synchronized(this) {
            tts?.let { return it }
            lateinit var engine: TextToSpeech
            engine = TextToSpeech(appContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    try {
                        engine.language = speechLanguage
                        engine.setSpeechRate(speechRate)
                        engine.setPitch(speechPitch)
                        applyVoiceToEngine()
                        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                            override fun onStart(utteranceId: String?) {
                                _isSpeaking.value = true
                            }

                            override fun onDone(utteranceId: String?) {
                                _isSpeaking.value = false
                                stopLevelWatch()
                            }

                            override fun onError(utteranceId: String?) {
                                _isSpeaking.value = false
                                stopLevelWatch()
                            }

                            override fun onStop(utteranceId: String?, interrupted: Boolean) {
                                _isSpeaking.value = false
                                stopLevelWatch()
                            }
                        })
                        ttsReady = true
                        NLog.i("TTS", "ready, locale=${speechLanguage.language}")
                    } catch (t: Throwable) {
                        NLog.e("TTS", "init failed", t)
                    }
                } else {
                    NLog.w("TTS", "engine unavailable (status=$status)")
                }
            }
            tts = engine
            return engine
        }
    }

    private fun applyVoiceToEngine() {
        val engine = tts ?: return
        if (!ttsReady) return
        try {
            if (currentVoiceName.isNotBlank()) {
                engine.voices?.firstOrNull { it.name == currentVoiceName }?.let { voice ->
                    if (!voice.isNetworkConnectionRequired) {
                        engine.setVoice(voice)
                    } else {
                        engine.setVoice(voice) // allowed; falls back to network if online
                    }
                }
            }
            engine.setSpeechRate(speechRate)
            engine.setPitch(speechPitch)
        } catch (_: Exception) {
            // Voice selection is best-effort.
        }
    }

    /** Speaks [text]; interrupts any ongoing utterance. [onDone] fires on the main thread. */
    fun speak(text: String, onDone: (() -> Unit)? = null) {
        val clean = text.trim()
        if (clean.isEmpty()) {
            onDone?.invoke()
            return
        }
        val engine = ensureTts()
        if (!ttsReady) {
            // Give the engine a moment on first use, then try once.
            scope.launch {
                kotlinx.coroutines.delay(400)
                speak(clean, onDone)
            }
            return
        }
        try {
            stopLevelWatch()
            val utteranceId = UUID.randomUUID().toString()
            val params = Bundle()
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) {
                    _isSpeaking.value = true
                    startLevelWatch()
                }

                override fun onDone(id: String?) {
                    _isSpeaking.value = false
                    stopLevelWatch()
                    if (id == utteranceId) onDone?.invoke()
                }

                override fun onError(id: String?) {
                    _isSpeaking.value = false
                    stopLevelWatch()
                    if (id == utteranceId) onDone?.invoke()
                }

                override fun onStop(id: String?, interrupted: Boolean) {
                    _isSpeaking.value = false
                    stopLevelWatch()
                    if (id == utteranceId) onDone?.invoke()
                }
            })
            engine.speak(clean, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
        } catch (t: Throwable) {
            NLog.e("TTS", "speak failed", t)
            _isSpeaking.value = false
            onDone?.invoke()
        }
    }

    fun stopSpeaking() {
        try {
            tts?.stop()
        } catch (_: Exception) {
        }
        _isSpeaking.value = false
        stopLevelWatch()
    }

    private fun startLevelWatch() {
        stopLevelWatch()
        silenceWatcher = scope.launch {
            var t = 0f
            while (_isSpeaking.value) {
                t += 0.18f
                val base = 0.45f + 0.3f * kotlin.math.sin(t * 2.3f) + 0.18f * kotlin.math.sin(t * 5.1f + 1.2f)
                _voiceLevel.value = base.coerceIn(0.08f, 1f)
                kotlinx.coroutines.delay(40)
            }
            _voiceLevel.value = 0f
        }
    }

    private fun stopLevelWatch() {
        silenceWatcher?.cancel()
        silenceWatcher = null
        _voiceLevel.value = 0f
    }

    data class ListenerCallbacks(
        val onFinal: (text: String) -> Unit,
        val onPartial: (text: String) -> Unit = {},
        val onError: (message: String, code: Int) -> Unit = { _, _ -> },
        val onReadyForSpeech: () -> Unit = {},
        val onEnd: () -> Unit = {},
        /** When set, each matching wake phrase in the transcript fires [onPartial] with the remainder. */
    )

    /**
     * Starts one push-to-talk listening session. Call [stopListening] to cut it short.
     * The recognizer is recreated per session (most reliable on MIUI/OneUI).
     */
    fun startListening(callbacks: ListenerCallbacks) {
        if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
            callbacks.onError("التعرف على الكلام غير متاح في هذا الجهاز", -1)
            return
        }
        stopListening(release = true)
        unlockMedia()
        val created = createRecognizer(callbacks)
        recognizer = created
        if (created == null) {
            callbacks.onError("تعذر تشغيل الميكروفون", -1)
            return
        }
        _isListening.value = true
        _partialTranscript.value = ""
        try {
            created.startListening(listenIntent())
        } catch (t: Throwable) {
            _isListening.value = false
            callbacks.onError("تعذر بدء الاستماع: ${t.message}", -1)
            releaseRecognizer()
        }
    }

    private fun listenIntent(): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, speechLanguage.toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, speechLanguage.toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 6)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1600L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1200L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 2400L)
        }

    private fun createRecognizer(callbacks: ListenerCallbacks): SpeechRecognizer? {
        return try {
            val instance = SpeechRecognizer.createSpeechRecognizer(appContext)
            instance.setRecognitionListener(object : RecognitionListener {
                private var lastPartial = ""
                override fun onReadyForSpeech(params: Bundle?) {
                    _isListening.value = true
                    callbacks.onReadyForSpeech()
                }
                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) {
                    // Map -2..12 dB to 0..1
                    val norm = ((rmsdB + 2f) / 14f).coerceIn(0f, 1f)
                    _voiceLevel.value = norm
                }
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() {
                    _isListening.value = false
                }
                override fun onError(error: Int) {
                    _isListening.value = false
                    val (message, code) = mapRecognitionError(error)
                    if (code != ERROR_NO_MATCH) {
                        _lastTranscript.value = ""
                    }
                    callbacks.onError(message, code)
                    callbacks.onEnd()
                    releaseRecognizer()
                }
                override fun onResults(results: Bundle?) {
                    _isListening.value = false
                    val texts = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                    val best = texts.firstOrNull { it.isNotBlank() }?.trim().orEmpty()
                    _lastTranscript.value = best
                    if (best.isNotEmpty()) {
                        callbacks.onPartial(best)
                        callbacks.onFinal(best)
                    } else {
                        callbacks.onError("لم أسمع شيئًا، حاول مرة أخرى", ERROR_NO_MATCH)
                    }
                    _partialTranscript.value = ""
                    callbacks.onEnd()
                    releaseRecognizer()
                }
                override fun onPartialResults(partialResults: Bundle?) {
                    val texts = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                    val partial = texts.firstOrNull { it.isNotBlank() }?.trim().orEmpty()
                    if (partial.isNotEmpty() && partial != lastPartial) {
                        lastPartial = partial
                        _partialTranscript.value = partial
                        callbacks.onPartial(partial)
                    }
                }
                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })
            instance
        } catch (t: Throwable) {
            NLog.e("STT", "createSpeechRecognizer failed", t)
            null
        }
    }

    /** Stops the current session; the engine delivers its last result (or error) as usual. */
    fun stopListening(release: Boolean = false) {
        val active = recognizer
        if (active != null) {
            try {
                if (!release) active.stopListening() else active.cancel()
            } catch (_: Exception) {
            }
            if (release) releaseRecognizer()
        }
        _isListening.value = false
        _voiceLevel.value = 0f
    }

    private fun releaseRecognizer() {
        val active = recognizer ?: return
        recognizer = null
        try {
            active.destroy()
        } catch (_: Exception) {
        }
    }

    private fun unlockMedia() {
        try {
            val audio = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            @Suppress("DEPRECATION")
            audio.requestAudioFocus(
                null,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT,
            )
        } catch (_: Exception) {
        }
    }

    /** AR/EN voices installed on the device, Arabic ones first. */
    fun availableVoices(): List<VoiceInfo> {
        val engine = ensureTts()
        return try {
            engine.voices
                .orEmpty()
                .asSequence()
                .filter { it.locale.language == "ar" || it.locale.language == "en" }
                .sortedWith(compareByDescending<Voice> { it.locale.language == "ar" }
                    .thenByDescending { it.quality }
                    .thenBy { it.name })
                .map {
                    VoiceInfo(
                        name = it.name,
                        locale = it.locale.toLanguageTag(),
                        languageLabel = if (it.locale.language == "ar") "العربية" else "English",
                        quality = it.quality,
                        requiresNetwork = it.isNetworkConnectionRequired,
                        isNetworkVoice = it.isNetworkConnectionRequired,
                    )
                }
                .toList()
        } catch (t: Throwable) {
            NLog.e("TTS", "list voices failed", t)
            emptyList()
        }
    }

    fun beep(success: Boolean = true) {
        try {
            val audio = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            if (success) audio.playSoundEffect(android.view.SoundEffectConstants.CLICK)
        } catch (_: Exception) {
        }
    }

    fun shutdown() {
        stopListening(release = true)
        stopSpeaking()
        try {
            tts?.shutdown()
        } catch (_: Exception) {
        }
        tts = null
        ttsReady = false
    }

    companion object {
        const val ERROR_NO_MATCH = 7

        @Volatile
        private var INSTANCE: SpeechManager? = null

        fun get(context: Context): SpeechManager =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: SpeechManager(context.applicationContext).also { INSTANCE = it }
            }

        /** Maps recognizer error codes to user-facing Arabic messages. */
        fun mapRecognitionError(error: Int): Pair<String, Int> = when (error) {
            SpeechRecognizer.ERROR_AUDIO -> "مشكلة في الميكروفون" to error
            SpeechRecognizer.ERROR_CLIENT -> "خطأ داخلي في خدمة التعرف" to error
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "إذن الميكروفون غير ممنوح" to error
            SpeechRecognizer.ERROR_NETWORK -> "مشكلة في الاتصال بالإنترنت" to error
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "انتهت مهلة الاتصال" to error
            SpeechRecognizer.ERROR_NO_MATCH -> "لم أسمع كلامًا واضحًا" to ERROR_NO_MATCH
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "خدمة التعرف مشغولة، حاول مرة أخرى" to error
            SpeechRecognizer.ERROR_SERVER -> "مشكلة في خادم التعرف على الكلام" to error
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "لم أسمع أحدًا يتحدث" to ERROR_NO_MATCH
            else -> "حدث خطأ في التعرف على الكلام ($error)" to error
        }
    }
}
