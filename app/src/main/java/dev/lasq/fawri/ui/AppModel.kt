package dev.lasq.fawri.ui

import android.app.*
import android.content.*
import android.net.Uri
import androidx.lifecycle.*
import dev.lasq.fawri.*
import dev.lasq.fawri.core.*
import dev.lasq.fawri.data.*
import dev.lasq.fawri.platform.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class Query(
    val text: String = "",
    val group: String = "",
    val pins: Boolean = false,
    val limit: Int = 40,
)

data class Library(
    val rows: List<Clip> = emptyList(),
    val recent: List<Clip> = emptyList(),
    val total: Int = 0,
    val templates: List<Snippet> = emptyList(),
    val more: Boolean = false,
    val loaded: Boolean = false,
    val error: Boolean = false,
)

data class Installed(val pkg: String, val title: String, val restricted: Boolean)

@OptIn(ExperimentalCoroutinesApi::class)
class AppModel(a: Application) : AndroidViewModel(a) {
    val app = a as LasqApp
    val prefs = app.prefs
    val query = MutableStateFlow(Query())
    val visible = MutableStateFlow(false)
    private val refresh = MutableStateFlow(0L)

    fun refresh() {
        refresh.update { it + 1 }
    }

    val library =
        combine(visible, query, app.vault.revision, prefs.flow, refresh) { show, q, _, o, _ ->
                Triple(show, q, o.days)
            }
            .mapLatest { (show, q, days) ->
                if (!show) return@mapLatest Library()
                try {
                    val time = System.currentTimeMillis() - days * DAY
                    app.vault.read { dao ->
                        val rows = dao.search(time, q.text, q.group, q.pins, q.limit + 1)
                        Library(
                            rows.take(q.limit),
                            dao.recent(time),
                            dao.count(time),
                            dao.templates(),
                            rows.size > q.limit,
                            true,
                        )
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    Library(error = true)
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(0, 0), Library())
    val apps = MutableStateFlow<List<Installed>?>(null)
    private var fetching = false

    fun loadApps() {
        if (fetching) return
        fetching = true
        viewModelScope.launch {
            apps.value =
                withContext(Dispatchers.IO) {
                    val pm = app.packageManager
                    pm.queryIntentActivities(
                            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
                            0,
                        )
                        .map {
                            val pkg = it.activityInfo.packageName
                            Installed(
                                pkg,
                                it.loadLabel(pm).toString(),
                                Rules.restricted(pkg, app.packageName),
                            )
                        }
                        .filterNot { it.pkg == app.packageName }
                        .distinctBy { it.pkg }
                        .sortedWith(compareBy({ it.restricted }, { it.title.lowercase() }))
                }
            fetching = false
        }
    }

    fun message(s: String) = app.report(s)

    fun settings(f: (Options) -> Options) {
        val old = prefs.value
        prefs.change(f)
        if (old.days != prefs.value.days || old.clearSeconds != prefs.value.clearSeconds)
            app.cleanLater()
        Surfaces.refresh(app)
    }

    private fun action(f: suspend () -> Unit) {
        if (!visible.value) return
        viewModelScope.launch {
            try {
                f()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                message("تعذّر إتمام العملية بأمان. تحقق من مساحة الجهاز وأعد المحاولة.")
            }
        }
    }

    fun manual(raw: String, source: String) = action {
        val clean = Rules.prepare(raw, prefs.value)
        if (clean == null) {
            message("النص فارغ أو طويل جداً أو مستبعد بنوع المحتوى المختار.")
            return@action
        }
        app.vault.write {
            it.add(
                Clip(
                    body = clean,
                    time = System.currentTimeMillis(),
                    source = source,
                    kind = Rules.kind(clean).name,
                ),
            )
        }
        app.cleanLater()
        message("أُضيف العنصر إلى سجلك المشفّر.")
    }

    fun importClipboard(c: Context) {
        if (!visible.value || !Checks.unlocked(c)) return
        val s = runCatching { ClipboardPort.read(c, false) }.getOrNull()
        if (s == null) {
            message("لا يوجد نص متاح. نتجاهل الصور والبيانات المعلّمة كحساسة.")
            return
        }
        manual(s.text, "إضافة يدوية")
        ClipboardPort.remember(c, s.stamp)
        Monitor.instance?.scheduleClear()
    }

    fun copy(c: Context, s: String) {
        if (visible.value && Checks.unlocked(c))
            runCatching {
                ClipboardPort.put(c, s)
                Monitor.instance?.scheduleClear()
                app.cleanLater()
                message("نُسخ إلى الحافظة.")
            }
                .onFailure { message("لم يسمح النظام بالنسخ الآن.") }
    }

    fun pin(c: Clip) = action { app.vault.write { it.pin(c.id) } }

    fun group(c: Clip, g: Group) = action { app.vault.write { it.group(c.id, g.name) } }

    fun delete(c: Clip) = action {
        app.vault.write { it.delete(c.id) }
        message("حُذف العنصر.")
    }

    fun addSnippet(title: String, body: String) = action {
        require(
            title.isNotBlank() && body.isNotBlank() && title.length <= 80 && body.length <= MAX_TEXT,
        )
        app.vault.write { it.addSnippet(Snippet(title = title.trim(), body = body)) }
        message("حُفظ القالب محلياً.")
    }

    fun removeSnippet(s: Snippet) = action { app.vault.write { it.deleteSnippet(s.id) } }

    val transferring = MutableStateFlow(false)

    fun transfer(uri: Uri, export: Boolean, pass: CharArray) = action {
        if (transferring.value) {
            pass.fill('\u0000')
            return@action
        }
        transferring.value = true
        try {
            val n =
                withContext(Dispatchers.IO) {
                    val now = System.currentTimeMillis()
                    val days = prefs.value.days
                    if (export) {
                        val after = now - days * DAY
                        val rows =
                            app.vault.read { dao ->
                                require(
                                    dao.count(after) <= Backup.ROWS &&
                                        dao.exportSize(after) <= Backup.LIMIT,
                                )
                                dao.export(after, Backup.ROWS)
                            }
                        val bytes = Backup.seal(rows, pass)
                        requireNotNull(app.contentResolver.openOutputStream(uri, "wt")).use {
                            it.write(bytes)
                        }
                        rows.size
                    } else {
                        val bytes =
                            requireNotNull(app.contentResolver.openInputStream(uri))
                                .use(Backup::readBounded)
                        val rows = Backup.open(bytes, pass, now, days)
                        app.vault.write { it.addAll(rows) }
                        rows.size
                    }
                }
            app.cleanLater()
            message(
                if (export) "اكتمل تصدير $n من العناصر في ملف مشفّر."
                else "استُورد $n من العناصر غير المنتهية.",
            )
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            message(
                if (export) "تعذّر التصدير. الحد ألف عنصر وأربعة ميغابايت؛ تحقق من مساحة الوجهة."
                else "تعذّر فتح النسخة. تحقق من عبارة المرور وصحة الملف وحجمه.",
            )
        } finally {
            pass.fill('\u0000')
            transferring.value = false
        }
    }

    fun erase(done: () -> Unit) = action {
        if (transferring.value) {
            message("انتظر اكتمال نقل الملف أولاً.")
            return@action
        }
        Monitor.instance?.halt(false)
        app.prefs.change { it.copy(consent = false) }
        visible.value = false
        Maintenance.cancel(app)
        runCatching { ClipboardPort.expire(app, foreground = true, force = true) }
        ClipboardPort.forget(app)
        app.system<NotificationManager>().cancelAll()
        app.vault.erase()
        prefs.reset()
        query.value = Query()
        Surfaces.refresh(app)
        done()
    }
}
