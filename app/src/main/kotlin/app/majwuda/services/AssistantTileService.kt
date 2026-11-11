package app.majwuda.services

import android.content.Intent
import android.service.quicksettings.TileService
import app.majwuda.ui.AssistActivity

/** Quick Settings tile: one tap to talk to مجودة. */
class AssistantTileService : TileService() {

    @Suppress("DEPRECATION")
    override fun onClick() {
        super.onClick()
        val intent = Intent(this, AssistActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(AssistActivity.EXTRA_LISTEN, true)
        }
        try {
            startActivityAndCollapse(intent)
        } catch (_: Exception) {
            // Fallback for API levels where startActivityAndCollapse(PendingIntent) is required.
            try {
                val pending = android.app.PendingIntent.getActivity(this, 7, intent, android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE)
                if (android.os.Build.VERSION.SDK_INT >= 24) {
                    startActivityAndCollapse(pending)
                }
            } catch (_: Exception) {
            }
        }
    }
}
