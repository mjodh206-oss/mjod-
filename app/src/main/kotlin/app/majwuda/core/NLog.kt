package app.majwuda.core

import android.util.Log

/**
 * Thin logging facade. Never throws, so pure logic stays unit-testable on a plain JVM
 * (where android.util.Log is only a stub).
 */
object NLog {
    private const val PREFIX = "Majwuda/"

    fun d(tag: String, message: String) {
        try {
            Log.d(PREFIX + tag, message)
        } catch (_: Throwable) {
        }
    }

    fun i(tag: String, message: String) {
        try {
            Log.i(PREFIX + tag, message)
        } catch (_: Throwable) {
        }
    }

    fun w(tag: String, message: String, error: Throwable? = null) {
        try {
            if (error != null) Log.w(PREFIX + tag, message, error) else Log.w(PREFIX + tag, message)
        } catch (_: Throwable) {
        }
    }

    fun e(tag: String, message: String, error: Throwable? = null) {
        try {
            if (error != null) Log.e(PREFIX + tag, message, error) else Log.e(PREFIX + tag, message)
        } catch (_: Throwable) {
        }
    }
}
