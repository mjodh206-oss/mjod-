package app.majwuda.services

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import app.majwuda.R
import app.majwuda.ui.AssistActivity

/** Home screen widget: tap to talk to مجودة. */
class AssistantWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { id ->
            val views = RemoteViews(context.packageName, R.layout.widget_assistant)
            val intent = Intent(context, AssistActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(AssistActivity.EXTRA_LISTEN, true)
            }
            val pending = PendingIntent.getActivity(
                context,
                100 + id,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_root, pending)
            views.setOnClickPendingIntent(R.id.widget_mic, pending)
            views.setOnClickPendingIntent(R.id.widget_orb, pending)
            views.setOnClickPendingIntent(R.id.widget_text, pending)
            appWidgetManager.updateAppWidget(id, views)
        }
    }
}
