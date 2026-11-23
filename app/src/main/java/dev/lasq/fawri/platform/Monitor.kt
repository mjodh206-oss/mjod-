package dev.lasq.fawri.platform

import android.app.*
import android.content.*
import android.os.*
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import dev.lasq.fawri.*
import dev.lasq.fawri.core.*
import dev.lasq.fawri.data.Clip
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.drop

class Monitor : Service(), ClipboardManager.OnPrimaryClipChangedListener {
    companion object {
        var instance: Monitor? = null; private set
        fun start(c: Context): Boolean {
            Checks.reason(c)?.let { Toast.makeText(c,it,Toast.LENGTH_LONG).show(); return false }
            return try { c.startForegroundService(Intent(c,Monitor::class.java)); true } catch (_: Exception) {
                Toast.makeText(c,"تعذّر بدء الخدمة من الخلفية. افتح التطبيق وحاول مجدداً.",Toast.LENGTH_LONG).show(); false
            }
        }
    }
    private val scope = CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper()); private val gate=DeliveryGate()
    private var observing=false; private var lastStamp=0L; private var since=0L; private var bound: Field? = null
    private var nonce=0L; private var approved=false; private var approvalUntil=0L
    private val expire=Runnable { cancelTicket() }; private val probe=Runnable { evaluate() }
    private val deliver=Runnable { deliverNow() }; private val clear=Runnable { runCatching { ClipboardPort.expire(this) } }
    private val stateChange=object: BroadcastReceiver() { override fun onReceive(c: Context,i: Intent) {
        cancelTicket(); if (i.action==Intent.ACTION_INPUT_METHOD_CHANGED && !Checks.canObserve(c)) halt(false)
    } }
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onCreate() {
        super.onCreate(); instance=this; Alerts.setup(this)
        registerReceiver(stateChange,IntentFilter(Intent.ACTION_SCREEN_OFF).apply { addAction(Intent.ACTION_INPUT_METHOD_CHANGED) })
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(Alerts.SERVICE,Alerts.running(this)); Alerts.hide(this,Alerts.PAUSED)
        if (Checks.reason(this)!=null || PasteAccess.instance==null) { halt(false); return START_NOT_STICKY }
        if (!observing) {
            observing=true; since=System.currentTimeMillis(); app.active.value=true
            getSystemService(ClipboardManager::class.java).addPrimaryClipChangedListener(this); Surfaces.refresh(this)
            scope.launch { app.prefs.flow.drop(1).collect { cancelTicket(); if (Checks.reason(this@Monitor)!=null) halt(false) else scheduleClear() } }
            scheduleClear()
        }; return START_NOT_STICKY
    }
    override fun onPrimaryClipChanged() {
        cancelTicket() // even rejected/sensitive changes invalidate a previous copy
        if (!observing || !Checks.unlocked(this)) return
        if (Checks.reason(this)!=null) { halt(false); return }
        try {
            val source = PasteAccess.instance?.focused { f,_ -> f.field } ?: return
            val sample=ClipboardPort.read(this,ignoreOwn=true) ?: return
            val now=System.currentTimeMillis()
            if (sample.stamp<=since || sample.stamp==lastStamp || now-sample.stamp !in 0L..3_000L) return
            lastStamp=sample.stamp; val o=app.prefs.value; val text=Rules.prepare(sample.text,o) ?: return
            val label=runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(source.pkg,0)).toString().take(255) }.getOrDefault(source.pkg.take(255))
            scope.launch {
                try { app.vault.write { it.add(Clip(body=text,time=now,source=label,kind=Rules.kind(text).name)) }; app.cleanLater() }
                catch (e: CancellationException) { throw e } catch (_: Exception) { cancelTicket(); halt(false); app.report("تعذّر حفظ العنصر بأمان؛ أوقفنا الخدمة.") }
            }
            ClipboardPort.remember(this,sample.stamp); scheduleClear()
            if (o.mode!=Mode.ARCHIVE) { gate.offer(sample.stamp,text,source,SystemClock.elapsedRealtime()); handler.postDelayed(expire,45_000); focusChanged() }
        } catch (_: SecurityException) { cancelTicket(); app.report("لم يسمح النظام بقراءة الحافظة.") } catch (_: IllegalStateException) { cancelTicket() }
    }
    fun scheduleClear() { handler.removeCallbacks(clear); ClipboardPort.due(this)?.let { handler.postDelayed(clear,(it-System.currentTimeMillis()).coerceAtLeast(0)) } }
    fun cancelTicket() {
        gate.clear(); bound=null; approved=false; nonce++
        handler.removeCallbacks(probe); handler.removeCallbacks(deliver); handler.removeCallbacks(expire); Alerts.hide(this,Alerts.PROMPT)
    }
    fun focusChanged() { if(gate.current!=null) { handler.removeCallbacks(probe); handler.postDelayed(probe,80) } }
    private fun evaluate() {
        val p=gate.current ?: return
        if (!observing || !Checks.unlocked(this)) { cancelTicket(); return }
        val dest=PasteAccess.instance?.focused { f,_ -> f.takeIf { it.editor }?.field } ?: return
        if (!gate.eligible(p.id,dest,p.stamp,SystemClock.elapsedRealtime())) return
        if (approved && dest!=bound) { cancelTicket(); return }
        if (bound==dest && !approved) return
        val changed=bound!=dest; bound=dest
        if (app.prefs.value.mode==Mode.CONFIRM && !approved) { if(changed) Alerts.ask(this,++nonce); return }
        handler.removeCallbacks(deliver); handler.postDelayed(deliver,app.prefs.value.delayMs)
    }
    fun confirm(token: Long) {
        if (token!=nonce || bound==null || gate.current==null || !Checks.unlocked(this)) return
        approved=true; approvalUntil=SystemClock.elapsedRealtime()+5_000; Alerts.hide(this,Alerts.PROMPT)
        // The notification shade may still be closing. Only the very same field may receive this approval.
        handler.removeCallbacks(deliver); handler.postDelayed(deliver,250+app.prefs.value.delayMs)
    }
    private fun deliverNow() {
        val p=gate.current ?: return; val o=app.prefs.value
        if (!observing || Checks.reason(this)!=null || !Checks.unlocked(this) || o.mode==Mode.ARCHIVE || (o.mode==Mode.CONFIRM && (!approved || SystemClock.elapsedRealtime()>approvalUntil))) { cancelTicket(); return }
        try {
            PasteAccess.instance?.focused { f,node ->
                if (!f.editor || f.field!=bound || node==null) { if (!approved) bound=null; return@focused }
                val sample=ClipboardPort.read(this,ignoreOwn=true)
                if (sample==null || Rules.prepare(sample.text,o)!=p.text || gate.take(p.id,f.field,sample.stamp,SystemClock.elapsedRealtime())==null) { cancelTicket(); return@focused }
                cancelTicket() // consume before performing the action (including rejection by the editor)
                if (sample.text!=p.text) { ClipboardPort.put(this,p.text); scheduleClear(); app.cleanLater() }
                if (!node.performAction(AccessibilityNodeInfo.ACTION_PASTE)) app.report("هذا الحقل لا يدعم إجراء اللصق.")
            } ?: run { if (!approved) bound=null }
        } catch (_: Exception) { cancelTicket(); app.report("لم يكتمل اللصق؛ ربما تغيّر الحقل أو الحافظة.") }
    }
    fun halt(paused: Boolean) {
        cancelTicket(); if(observing) getSystemService(ClipboardManager::class.java).removePrimaryClipChangedListener(this)
        observing=false; app.active.value=false; handler.removeCallbacks(clear); Surfaces.refresh(this)
        stopForeground(STOP_FOREGROUND_REMOVE); if(paused && Checks.notifications(this)) Alerts.paused(this); stopSelf()
    }
    override fun onDestroy() {
        if (observing) getSystemService(ClipboardManager::class.java).removePrimaryClipChangedListener(this)
        observing=false; app.active.value=false; cancelTicket(); handler.removeCallbacksAndMessages(null); scope.cancel(); unregisterReceiver(stateChange); instance=null; Surfaces.refresh(this); super.onDestroy()
    }
}
