package dev.lasq.fawri.data

import android.content.Context
import dev.lasq.fawri.core.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class Prefs(context: Context) {
    private val p = context.getSharedPreferences("options", Context.MODE_PRIVATE)

    private inline fun <reified E : Enum<E>> enum(name: String, default: E): E =
        enumValues<E>().firstOrNull { it.name == p.getString(name, null) } ?: default

    private val state =
        MutableStateFlow(
            Options(
                intro = p.getBoolean("intro", false),
                consent = p.getBoolean("consent", false),
                mode = enum("mode", Mode.AUTO),
                allowList = p.getBoolean("allow", true),
                allowed = p.getStringSet("yes", null).orEmpty().toSet(),
                denied = p.getStringSet("no", null).orEmpty().toSet(),
                types =
                    Kind.entries
                        .filter {
                            it.name in
                                p.getStringSet("types", Kind.entries.map { k -> k.name }.toSet())
                                    .orEmpty()
                        }
                        .toSet(),
                trim = p.getBoolean("trim", false),
                tracking = p.getBoolean("tracking", false),
                delayMs = p.getLong("delay", 0),
                days = p.getInt("days", 7),
                clearSeconds = p.getInt("clear", 0),
                lock = p.getBoolean("lock", false),
                theme = enum("theme", Theme.SYSTEM),
                locale = p.getString("locale", "ar") ?: "ar",
            ),
        )
    val flow = state.asStateFlow()
    val value
        get() = state.value

    fun change(transform: (Options) -> Options) {
        val s = transform(value)
        p.edit()
            .putBoolean("intro", s.intro)
            .putBoolean("consent", s.consent)
            .putString("mode", s.mode.name)
            .putBoolean("allow", s.allowList)
            .putStringSet("yes", s.allowed)
            .putStringSet("no", s.denied)
            .putStringSet("types", s.types.map { it.name }.toSet())
            .putBoolean("trim", s.trim)
            .putBoolean("tracking", s.tracking)
            .putLong("delay", s.delayMs)
            .putInt("days", s.days)
            .putInt("clear", s.clearSeconds)
            .putBoolean("lock", s.lock)
            .putString("theme", s.theme.name)
            .putString("locale", s.locale)
            .apply()
        state.value = s
    }

    fun reset() {
        p.edit().clear().commit()
        state.value = Options()
    }
}
