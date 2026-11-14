package com.amjadhabib.rattib

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

class RattibWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    companion object {
        const val PREFS_NAME = "rattib_widget_prefs"
        const val KEY_REMAINING = "remaining_text"
        const val KEY_DAYS_LEFT = "days_left_text"
        const val KEY_PROGRESS = "progress_percent"
        const val KEY_SUBTITLE = "subtitle_text"

        fun updateAllWidgets(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, RattibWidgetProvider::class.java)
            val ids = manager.getAppWidgetIds(componentName)
            for (id in ids) {
                updateAppWidget(context, manager, id)
            }
        }

        private fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val remaining = prefs.getString(KEY_REMAINING, "افتح رَتِّبْ لإعداد خطتك") ?: "افتح رَتِّبْ لإعداد خطتك"
            val daysLeft = prefs.getString(KEY_DAYS_LEFT, "فلوسك بتستاهل خطة") ?: "فلوسك بتستاهل خطة"
            val progress = prefs.getInt(KEY_PROGRESS, 100).coerceIn(0, 100)
            val subtitle = prefs.getString(KEY_SUBTITLE, "تنفيذ وتطوير: أمجد حبيب") ?: "تنفيذ وتطوير: أمجد حبيب"

            val views = RemoteViews(context.packageName, R.layout.rattib_widget)
            views.setTextViewText(R.id.widget_remaining, remaining)
            views.setTextViewText(R.id.widget_days_left, daysLeft)
            views.setProgressBar(R.id.widget_progress, 100, progress, false)
            views.setTextViewText(R.id.widget_subtitle, subtitle)

            val intent = Intent(context, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
