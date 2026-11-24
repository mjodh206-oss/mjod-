package dev.lasq.fawri.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.*
import dev.lasq.fawri.*
import dev.lasq.fawri.core.MAX_TEXT
import dev.lasq.fawri.data.*
import dev.lasq.fawri.platform.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class Library(
    val items: List<Preview> = emptyList(),
    val total: Int = 0,
    val more: Boolean = false,
    val loaded: Boolean = false,
    val failed: Boolean = false,
)

data class Query(val term: String = "", val limit: Int = 40)

@OptIn(ExperimentalCoroutinesApi::class)
class AppModel(application: Application) : AndroidViewModel(application) {
    val app = application as LasqApp
    val visible = MutableStateFlow(false)
    val query = MutableStateFlow(Query())
    val messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val transferring = MutableStateFlow(false)
    val library =
        combine(visible, query, app.vault.revision) { show, q, _ -> show to q }
            .mapLatest { (show, q) ->
                if (!show) return@mapLatest Library()
                try {
                    app.vault.read { dao ->
                        val items = dao.page(q.term, q.limit + 1)
                        Library(items.take(q.limit), dao.count(), items.size > q.limit, loaded = true)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    Library(failed = true)
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(0, 0), Library())

    private fun action(f: suspend () -> Unit) {
        if (!visible.value) return
        viewModelScope.launch {
            try {
                f()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                messages.tryEmit("تعذّر إتمام العملية. لم نمسح بياناتك.")
            }
        }
    }

    fun text(id: Long, done: (String) -> Unit) = action {
        val body = app.vault.read { it.text(id) }
        if (body != null && visible.value && Checks.unlocked(app)) done(body)
    }

    fun copy(id: Long) =
        text(id) { body ->
            ClipboardPort.copy(app, body)
            messages.tryEmit("نُسخ النص.")
        }

    fun shared(body: String) = action {
        require(body.length in 1..MAX_TEXT)
        app.vault.write {
            it.add(Clip(body = body, time = System.currentTimeMillis(), source = "مشاركة"))
        }
    }

    fun remove(id: Long) = action {
        app.vault.write { it.delete(id) }
        messages.tryEmit("حُذف النص.")
    }

    fun clear() = action {
        if (transferring.value) {
            messages.tryEmit("انتظر اكتمال نقل الملف.")
            return@action
        }
        app.vault.write { it.clear() }
        messages.tryEmit("تم مسح السجل.")
    }

    fun transfer(uri: Uri, export: Boolean, pass: CharArray) {
        if (!visible.value || transferring.value) {
            pass.fill('\u0000')
            return
        }
        transferring.value = true
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    if (export) {
                        val rows =
                            app.vault.read { dao ->
                                require(dao.count() <= Backup.ROWS && dao.exportSize() <= Backup.LIMIT)
                                dao.export(Backup.ROWS)
                            }
                        val encrypted = Backup.seal(rows, pass)
                        requireNotNull(app.contentResolver.openOutputStream(uri, "wt")).use {
                            it.write(encrypted)
                        }
                    } else {
                        val bytes =
                            requireNotNull(app.contentResolver.openInputStream(uri))
                                .use(Backup::readBounded)
                        val rows = Backup.open(bytes, pass)
                        app.vault.write { it.addAll(rows) }
                    }
                }
                messages.tryEmit(
                    if (export) "حُفظت النسخة المشفّرة." else "أُضيفت نصوص النسخة إلى السجل.",
                )
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                messages.tryEmit(
                    if (export) "تعذّر التصدير. حد الملف ألف نص وأربعة ميغابايت؛ لم يتغيّر سجلك."
                    else "تعذّر فتح الملف. تأكّد من عبارة المرور والنسخة.",
                )
            } finally {
                pass.fill('\u0000')
                transferring.value = false
            }
        }
    }
}
