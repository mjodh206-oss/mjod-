package dev.lasq.fawri.platform

import android.content.ClipboardManager
import android.os.Build
import dev.lasq.fawri.LasqApp
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow

/** One main-thread listener shared by the focused activity, selected IME and legacy service. */
class Recorder(private val app: LasqApp) : ClipboardManager.OnPrimaryClipChangedListener {
    enum class State {
        OFF,
        ON_OPEN,
        INSTANT,
    }

    val state = MutableStateFlow(State.OFF)
    val problem = MutableStateFlow<String?>(null)
    private var window = false
    private var legacy = false
    private var privateEditor = false
    private var listening = false
    private val cm by lazy { app.system<ClipboardManager>() }

    fun focus(hasFocus: Boolean) {
        val gained = hasFocus && !window
        window = hasFocus
        refresh()
        // Modern Android may withhold all previous background events. Read only the current clip.
        if (gained) capture()
    }

    fun legacyRunning(value: Boolean) {
        legacy = value
        refresh()
    }

    fun editor(privateInput: Boolean) {
        privateEditor = privateInput
        refresh()
    }

    fun setEnabled(enabled: Boolean) {
        app.prefs.enable(enabled)
        problem.value = null
        refresh()
        if (!enabled) LegacyMonitor.stop(app) else if (window) capture()
    }

    private fun instant() = if (Build.VERSION.SDK_INT < 29) legacy else Checks.keyboard(app)

    fun refresh() {
        val allowed = app.prefs.enabled.value && app.integrity.first
        val background = allowed && instant()
        state.value =
            when {
                !allowed -> State.OFF
                background -> State.INSTANT
                else -> State.ON_OPEN
            }
        val needsListener = allowed && (window || background)
        if (needsListener != listening) {
            if (needsListener) cm.addPrimaryClipChangedListener(this)
            else cm.removePrimaryClipChangedListener(this)
            listening = needsListener
        }
    }

    override fun onPrimaryClipChanged() = capture()

    private fun capture() {
        if (!app.prefs.enabled.value || !app.integrity.first || !Checks.unlocked(app)) return
        if (!window && !instant()) {
            refresh()
            return
        }
        try {
            val e = ClipboardPort.read(app, privateEditor && Checks.keyboard(app)) ?: return
            app.scope.launch {
                try {
                    app.vault.capture(e)
                    problem.value = null
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    setEnabled(false)
                    problem.value =
                        "تعذّر الحفظ الآمن. لم نحذف سجلك؛ تحقّق من مساحة الجهاز ثم أعد التشغيل."
                }
            }
        } catch (_: ClipboardPort.TextTooLarge) {
            problem.value = "هذا النص أكبر من الحد المتاح للحافظة. لم نحفظ نسخة مبتورة."
        } catch (_: SecurityException) {
            refresh()
        }
    }
}
