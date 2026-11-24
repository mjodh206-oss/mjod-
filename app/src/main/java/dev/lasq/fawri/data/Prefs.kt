package dev.lasq.fawri.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** No copied text in preferences. Opt in anew: v1 consent was for a different feature. */
class Prefs(context: Context) {
    private val file = context.getSharedPreferences("options", Context.MODE_PRIVATE)
    private val enabledState = MutableStateFlow(file.getBoolean("archiveEnabled", false))
    private val lockState = MutableStateFlow(file.getBoolean("lock", false))
    val enabled = enabledState.asStateFlow()
    val locked = lockState.asStateFlow()

    fun enable(value: Boolean) {
        file.edit().putBoolean("archiveEnabled", value).apply()
        enabledState.value = value
    }

    fun lock(value: Boolean) {
        file.edit().putBoolean("lock", value).apply()
        lockState.value = value
    }

    // Remember a suppressed copy across focus changes. No content or source identity is retained.
    var privateStamp: Long
        get() = file.getLong("privateStamp", -1)
        set(value) {
            file.edit().putLong("privateStamp", value).apply()
        }
}
