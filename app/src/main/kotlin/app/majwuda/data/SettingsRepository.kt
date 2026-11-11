package app.majwuda.data

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private val Context.majwudaDataStore by preferencesDataStore(name = "majwuda.settings")

/**
 * Settings repository: DataStore for ordinary preferences, AndroidKeyStore-backed [SecureKeys]
 * for the API key. Exposes a hot [state] flow that tools read cheaply, and the full
 * [settingsFlow] for the UI.
 */
class SettingsRepository private constructor(private val appContext: Context) {

    private val store = appContext.majwudaDataStore
    private val secureKeys = SecureKeys(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow(AssistantSettings(apiKey = secureKeys.get(KEY_API_KEY).orEmpty()))
    val state: StateFlow<AssistantSettings> = _state.asStateFlow()

    val settingsFlow = store.data.map { prefs -> load(prefs) }

    init {
        scope.launch {
            settingsFlow.collect { _state.value = it }
        }
    }

    /** Cheap synchronous snapshot for tool execution; may be one emission behind DataStore. */
    fun snapshot(): AssistantSettings = _state.value

    /** Waits until DataStore has been read at least once. */
    suspend fun awaitLoaded() {
        settingsFlow.first()
    }

    suspend fun setApiKey(value: String?) {
        secureKeys.put(KEY_API_KEY, value?.trim()?.ifEmpty { null })
        refresh()
    }

    suspend fun setGatewayUrl(value: String) = put(KEY_GATEWAY, value.trim())
    suspend fun setModel(value: String) = put(KEY_MODEL, value.trim())
    suspend fun setMaxTokens(value: Int) = put(KEY_MAX_TOKENS, value.coerceIn(256, 8192))
    suspend fun setAssistantName(value: String) = put(KEY_ASSISTANT_NAME, value.trim().ifEmpty { "مجودة" })
    suspend fun setUserName(value: String) = put(KEY_USER_NAME, value.trim())
    suspend fun setCustomInstructions(value: String) = put(KEY_CUSTOM_INSTRUCTIONS, value)
    suspend fun setLanguage(value: String) = put(KEY_LANGUAGE, value)
    suspend fun setTheme(value: ThemeMode) = put(KEY_THEME, value.name)
    suspend fun setDynamicColor(value: Boolean) = put(KEY_DYNAMIC_COLOR, value)
    suspend fun setVoiceEnabled(value: Boolean) = put(KEY_VOICE_ENABLED, value)
    suspend fun setVoiceName(value: String) = put(KEY_VOICE_NAME, value)
    suspend fun setSpeechRate(value: Float) = put(KEY_SPEECH_RATE, value.coerceIn(0.5f, 2.0f))
    suspend fun setHapticsEnabled(value: Boolean) = put(KEY_HAPTICS, value)
    suspend fun setWakePhraseEnabled(value: Boolean) = put(KEY_WAKE, value)
    suspend fun setScreenControlEnabled(value: Boolean) = put(KEY_SCREEN_CONTROL, value)
    suspend fun setNotificationReadEnabled(value: Boolean) = put(KEY_NOTIFICATION_READ, value)
    suspend fun setWebAccessEnabled(value: Boolean) = put(KEY_WEB_ACCESS, value)
    suspend fun setAutoCall(value: Boolean) = put(KEY_AUTO_CALL, value)
    suspend fun setAutoSendSms(value: Boolean) = put(KEY_AUTO_SMS, value)
    suspend fun setTelemetryEnabled(value: Boolean) = put(KEY_TELEMETRY, value)
    suspend fun setKeepScreenOn(value: Boolean) = put(KEY_KEEP_SCREEN_ON, value)

    suspend fun resetAll() {
        store.edit { it.clear() }
        secureKeys.clear()
        refresh()
    }

    private fun load(prefs: androidx.datastore.preferences.core.Preferences) = AssistantSettings(
        apiKey = secureKeys.get(KEY_API_KEY).orEmpty(),
        gatewayUrl = prefs[KEY_GATEWAY].orEmpty(),
        model = prefs[KEY_MODEL] ?: "claude-sonnet-4-5",
        maxTokens = prefs[KEY_MAX_TOKENS] ?: 1800,
        assistantName = prefs[KEY_ASSISTANT_NAME] ?: "مجودة",
        userName = prefs[KEY_USER_NAME].orEmpty(),
        customInstructions = prefs[KEY_CUSTOM_INSTRUCTIONS].orEmpty(),
        language = prefs[KEY_LANGUAGE] ?: "auto",
        theme = ThemeMode.entries.firstOrNull { it.name == (prefs[KEY_THEME] ?: "SYSTEM") } ?: ThemeMode.SYSTEM,
        dynamicColor = prefs[KEY_DYNAMIC_COLOR] ?: false,
        voiceEnabled = prefs[KEY_VOICE_ENABLED] ?: true,
        voiceName = prefs[KEY_VOICE_NAME].orEmpty(),
        speechRate = prefs[KEY_SPEECH_RATE] ?: 1.0f,
        hapticsEnabled = prefs[KEY_HAPTICS] ?: true,
        wakePhraseEnabled = prefs[KEY_WAKE] ?: false,
        screenControlEnabled = prefs[KEY_SCREEN_CONTROL] ?: false,
        notificationReadEnabled = prefs[KEY_NOTIFICATION_READ] ?: false,
        webAccessEnabled = prefs[KEY_WEB_ACCESS] ?: true,
        autoCall = prefs[KEY_AUTO_CALL] ?: false,
        autoSendSms = prefs[KEY_AUTO_SMS] ?: false,
        telemetryEnabled = prefs[KEY_TELEMETRY] ?: false,
        keepScreenOn = prefs[KEY_KEEP_SCREEN_ON] ?: false,
    )

    private suspend fun <T> put(key: androidx.datastore.preferences.core.Preferences.Key<T>, value: T) {
        store.edit { it[key] = value }
        refresh()
    }

    private suspend fun refresh() {
        _state.value = settingsFlow.first()
    }

    companion object {
        const val KEY_API_KEY = "api_key"
        val KEY_GATEWAY = stringPreferencesKey("gateway_url")
        val KEY_MODEL = stringPreferencesKey("model")
        val KEY_MAX_TOKENS = intPreferencesKey("max_tokens")
        val KEY_ASSISTANT_NAME = stringPreferencesKey("assistant_name")
        val KEY_USER_NAME = stringPreferencesKey("user_name")
        val KEY_CUSTOM_INSTRUCTIONS = stringPreferencesKey("custom_instructions")
        val KEY_LANGUAGE = stringPreferencesKey("language")
        val KEY_THEME = stringPreferencesKey("theme")
        val KEY_DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val KEY_VOICE_ENABLED = booleanPreferencesKey("voice_enabled")
        val KEY_VOICE_NAME = stringPreferencesKey("voice_name")
        val KEY_SPEECH_RATE = floatPreferencesKey("speech_rate")
        val KEY_HAPTICS = booleanPreferencesKey("haptics")
        val KEY_WAKE = booleanPreferencesKey("wake_phrase")
        val KEY_SCREEN_CONTROL = booleanPreferencesKey("screen_control")
        val KEY_NOTIFICATION_READ = booleanPreferencesKey("notification_read")
        val KEY_WEB_ACCESS = booleanPreferencesKey("web_access")
        val KEY_AUTO_CALL = booleanPreferencesKey("auto_call")
        val KEY_AUTO_SMS = booleanPreferencesKey("auto_send_sms")
        val KEY_TELEMETRY = booleanPreferencesKey("telemetry")
        val KEY_KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")

        @Volatile
        private var INSTANCE: SettingsRepository? = null

        fun get(context: Context): SettingsRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: SettingsRepository(context.applicationContext).also { INSTANCE = it }
            }
    }
}
