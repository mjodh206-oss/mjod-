package app.majwuda.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat

/** Runtime permissions مجودة asks for, grouped by feature. */
val CORE_PERMISSIONS = buildList {
    add(Manifest.permission.RECORD_AUDIO)
    if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
}

val COMMUNICATION_PERMISSIONS = listOf(
    Manifest.permission.READ_CONTACTS,
    Manifest.permission.CALL_PHONE,
    Manifest.permission.SEND_SMS,
    Manifest.permission.READ_SMS,
    Manifest.permission.READ_CALL_LOG,
)

val CONTEXT_PERMISSIONS = listOf(
    Manifest.permission.READ_CALENDAR,
    Manifest.permission.WRITE_CALENDAR,
    Manifest.permission.ACCESS_COARSE_LOCATION,
    Manifest.permission.ACCESS_FINE_LOCATION,
)

val ALL_RUNTIME_PERMISSIONS: List<String> = CORE_PERMISSIONS + COMMUNICATION_PERMISSIONS + CONTEXT_PERMISSIONS

fun hasPermission(context: Context, permission: String): Boolean =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

fun missingPermissions(context: Context, permissions: List<String> = ALL_RUNTIME_PERMISSIONS): List<String> =
    permissions.filterNot { hasPermission(context, it) }

/** Asks for everything مجودة can use, once per install. */
@Composable
fun RequestPermissionsOnFirstLaunch(onDone: () -> Unit = {}) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var requested by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        onDone()
    }
    LaunchedEffect(Unit) {
        if (requested) return@LaunchedEffect
        requested = true
        val missing = missingPermissions(context)
        if (missing.isNotEmpty()) launcher.launch(missing.toTypedArray()) else onDone()
    }
}

/** Decodes and downsamples an image Uri to reasonable size; returns (base64, mediaType). */
fun decodeImageAsBase64(context: Context, uri: Uri, maxDimension: Int = 1280, quality: Int = 85): Pair<String, String>? {
    return try {
        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxDimension && bounds.outHeight / (sample * 2) >= maxDimension) {
            sample *= 2
        }
        val options = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap = context.contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, options) } ?: return null
        // Scale down further if still larger than maxDimension
        val scaled = if (bitmap.width > maxDimension || bitmap.height > maxDimension) {
            val ratio = maxDimension.toFloat() / maxOf(bitmap.width, bitmap.height)
            android.graphics.Bitmap.createScaledBitmap(bitmap, (bitmap.width * ratio).toInt().coerceAtLeast(1), (bitmap.height * ratio).toInt().coerceAtLeast(1), true)
        } else {
            bitmap
        }
        val out = java.io.ByteArrayOutputStream()
        scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, quality, out)
        val base64 = android.util.Base64.encodeToString(out.toByteArray(), android.util.Base64.NO_WRAP)
        base64 to "image/jpeg"
    } catch (_: Exception) {
        null
    }
}
