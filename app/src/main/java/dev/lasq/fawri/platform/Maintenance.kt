package dev.lasq.fawri.platform

import android.app.job.*
import android.content.*
import dev.lasq.fawri.app
import kotlinx.coroutines.*

class Maintenance : JobService() {
    private var work: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        work =
            app.scope.launch {
                var retry = false
                var next: Long? = null
                try {
                    ClipboardPort.expire(this@Maintenance)
                    next = app.vault.prune(app.prefs.value.days)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    retry = true
                }
                jobFinished(params, retry)
                if (!retry) plan(this@Maintenance, next)
            }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        work?.cancel()
        return true
    }

    companion object {
        private const val ID = 4760

        fun cancel(c: Context) {
            c.system<JobScheduler>().cancel(ID)
        }

        fun plan(c: Context, history: Long?) {
            val now = System.currentTimeMillis()
            val next =
                listOfNotNull(history, ClipboardPort.due(c)?.takeIf { it > now }).minOrNull()
                    ?: run {
                        cancel(c)
                        return
                    }
            val delay = (next - now).coerceAtLeast(1_000)
            val js = c.system<JobScheduler>()
            val info =
                JobInfo.Builder(ID, ComponentName(c, Maintenance::class.java))
                    .setMinimumLatency(delay)
                    .setOverrideDeadline(delay + 60_000L)
                    .setPersisted(true)
                    .build()
            js.schedule(info)
        }
    }
}
