package dev.lasq.fawri.platform

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.inputmethodservice.InputMethodService
import android.text.InputType
import android.view.*
import android.view.inputmethod.*
import android.widget.*
import dev.lasq.fawri.app
import dev.lasq.fawri.core.confidentialPackage
import dev.lasq.fawri.ui.MainActivity

/** Optional local keyboard on Android 10+. Key taps go directly to the editor, never to storage. */
class PasteBoard : InputMethodService() {
    private var english = false
    private var symbols = false
    private var upper = false
    private var panel: LinearLayout? = null
    private val dark = Color.rgb(35, 32, 48)

    override fun onCreate() {
        super.onCreate()
        app.recorder.refresh()
    }

    override fun onEvaluateFullscreenMode() = false

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        val privateInput =
            attribute != null &&
                (Checks.password(attribute.inputType) ||
                    confidentialPackage(attribute.packageName) ||
                    attribute.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING != 0)
        app.recorder.editor(privateInput)
        if (!restarting) {
            symbols =
                attribute != null &&
                    attribute.inputType and InputType.TYPE_MASK_CLASS in
                        setOf(InputType.TYPE_CLASS_NUMBER, InputType.TYPE_CLASS_PHONE)
            upper = false
        }
    }

    override fun onFinishInput() {
        app.recorder.editor(false)
        super.onFinishInput()
    }

    override fun onDestroy() {
        app.recorder.editor(false)
        super.onDestroy()
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        render()
    }

    override fun onCreateInputView(): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(dark)
            setPadding(dp(3), dp(3), dp(3), dp(5))
            panel = this
            render()
        }

    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()

    private fun render() {
        val root = panel ?: return
        root.removeAllViews()
        fun row() =
            LinearLayout(this).apply {
                layoutDirection = View.LAYOUT_DIRECTION_LTR
                root.addView(this)
            }
        fun key(
            r: LinearLayout,
            label: String,
            width: Float = 1f,
            small: Boolean = false,
            press: () -> Unit,
        ) {
            val v =
                TextView(this).apply {
                    text = label
                    textSize = if (small) 13f else 19f
                    gravity = Gravity.CENTER
                    setTextColor(Color.WHITE)
                    isFocusable = true
                    background =
                        GradientDrawable().apply {
                            setColor(if (small) 0xff51466d.toInt() else 0xff464151.toInt())
                            cornerRadius = dp(7).toFloat()
                        }
                    setOnClickListener { press() }
                }
            val lp =
                LinearLayout.LayoutParams(0, dp(if (small) 36 else 44), width).apply {
                    setMargins(dp(1), dp(2), dp(1), dp(2))
                }
            r.addView(v, lp)
        }
        val head = row()
        key(head, "تبديل لوحة المفاتيح", 1f, true) {
            system<InputMethodManager>().showInputMethodPicker()
        }
        val on = app.prefs.enabled.value
        key(head, if (on) "الحفظ يعمل  ·  إيقاف" else "الحفظ متوقف  ·  فتح السجل", 1.4f, true) {
            if (on) {
                app.recorder.setEnabled(false)
                render()
            } else
                startActivity(
                    Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
        }
        val rows =
            when {
                symbols -> listOf("1234567890", "@#%&*()-+", ".،؟!_/:=\"")
                english -> listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
                else -> listOf("ضصثقفغعهخحج", "شسيبلاتنمكط", "ئءؤرلىةوزظد", "ذأإآى،.؟")
            }
        for (letters in rows) {
            val r = row()
            for (letter in letters) {
                val s = if (!symbols && english && upper) letter.uppercase() else letter.toString()
                key(r, s) { currentInputConnection?.commitText(s, 1) }
            }
        }
        val bottom = row()
        key(bottom, if (english) "عربي" else "EN", small = true) {
            english = !english
            symbols = false
            render()
        }
        key(bottom, if (symbols) "أبج" else "123", small = true) {
            symbols = !symbols
            render()
        }
        if (english && !symbols)
            key(bottom, "⇧", small = true) {
                upper = !upper
                render()
            }
        key(bottom, "مسافة", 2f, true) { currentInputConnection?.commitText(" ", 1) }
        key(bottom, "⌫", small = true) { sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL) }
        key(bottom, "↵", small = true) {
            if (!sendDefaultEditorAction(true)) sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
        }
    }
}
