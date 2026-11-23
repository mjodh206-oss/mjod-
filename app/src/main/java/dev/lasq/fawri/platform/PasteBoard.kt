package dev.lasq.fawri.platform

import android.inputmethodservice.InputMethodService
import android.content.*
import android.graphics.Color
import android.os.Build
import android.view.*
import android.view.inputmethod.*
import android.widget.*
import dev.lasq.fawri.ui.MainActivity

/** Optional paste-only IME. Never calls getTextBeforeCursor/getExtractedText or observes keystrokes. */
class PasteBoard : InputMethodService() {
    companion object { var privateEditor: String? = null; private set }
    override fun onEvaluateFullscreenMode() = false
    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute,restarting)
        privateEditor = attribute?.takeIf { Checks.password(it.inputType) || it.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING != 0 }?.packageName
        if (privateEditor != null) Monitor.instance?.cancelTicket()
    }
    override fun onFinishInput() { privateEditor = null; super.onFinishInput() }
    @Suppress("DEPRECATION") override fun onCreateInputView(): View {
        fun dp(n: Int) = (resources.displayMetrics.density*n).toInt()
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; layoutDirection = View.LAYOUT_DIRECTION_RTL; setPadding(dp(16),dp(10),dp(16),dp(12)); setBackgroundColor(Color.rgb(32,35,57))
            addView(TextView(context).apply { text="لوحة لصق فوري   •   محلية وخاصة"; setTextColor(Color.WHITE); textSize=16f })
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(Button(context).apply { text="لوحة الكتابة السابقة"; setOnClickListener {
                    val imm = getSystemService(InputMethodManager::class.java)
                    val switched = if (Build.VERSION.SDK_INT>=28) switchToPreviousInputMethod() else imm.switchToLastInputMethod(window.window?.attributes?.token)
                    if (!switched) imm.showInputMethodPicker()
                } })
                addView(Button(context).apply { text="فتح التطبيق"; setOnClickListener { startActivity(Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } })
            })
        }
    }
}
