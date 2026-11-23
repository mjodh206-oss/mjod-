package dev.lasq.fawri.platform

import android.appwidget.*
import android.content.*
import android.os.Build
import android.service.quicksettings.*
import android.widget.*
import dev.lasq.fawri.*

object Surfaces {
    fun refresh(c: Context) {
        val m = AppWidgetManager.getInstance(c)
        val ids = m.getAppWidgetIds(ComponentName(c, LasqWidget::class.java))
        if (ids.isNotEmpty()) LasqWidget().onUpdate(c, m, ids)
        TileService.requestListeningState(c, ComponentName(c, LasqTile::class.java))
    }
}

class Control : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        when (i.action) {
            "stop" -> Monitor.instance?.halt(true) ?: Surfaces.refresh(c)
            "confirm" -> Monitor.instance?.confirm(i.getLongExtra("token", -1))
            "toggle" ->
                if (c.app.active.value) Monitor.instance?.halt(true)
                else if (!c.app.prefs.value.lock && Checks.unlocked(c)) Monitor.start(c)
                else Toast.makeText(c, "افتح التطبيق لتأكيد هويتك.", Toast.LENGTH_SHORT).show()
        }
    }
}

class LasqWidget : AppWidgetProvider() {
    override fun onUpdate(c: Context, m: AppWidgetManager, ids: IntArray) {
        val on = c.app.active.value
        val v = RemoteViews(c.packageName, R.layout.widget)
        v.setTextViewText(R.id.widget_status, if (on) "قيد التشغيل  •  إيقاف" else "متوقف  •  تشغيل")
        v.setOnClickPendingIntent(
            R.id.widget_root,
            if (on) Alerts.action(c, "stop")
            else if (c.app.prefs.value.lock || Checks.reason(c) != null) Alerts.open(c)
            else Alerts.action(c, "toggle"),
        )
        m.updateAppWidget(ids, v)
    }
}

class LasqTile : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            state = if (app.active.value) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            label = "لصق فوري"
            updateTile()
        }
    }

    @Suppress("DEPRECATION")
    override fun onClick() {
        super.onClick()
        if (app.active.value) Monitor.instance?.halt(true)
        else if (app.prefs.value.lock || Checks.reason(this) != null) {
            if (Build.VERSION.SDK_INT >= 34) startActivityAndCollapse(Alerts.open(this))
            else
                startActivityAndCollapse(
                    Intent(this, dev.lasq.fawri.ui.MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
        } else unlockAndRun { Monitor.start(this) }
        onStartListening()
    }
}
