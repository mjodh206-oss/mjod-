package dev.lasq.fawri

import android.app.NotificationManager
import android.app.UiAutomation
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import dev.lasq.fawri.core.*
import dev.lasq.fawri.platform.*
import dev.lasq.fawri.ui.MainActivity
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PastePathTest {
    private val inst = InstrumentationRegistry.getInstrumentation()
    private val ctx
        get() = inst.targetContext

    private val source = "dev.lasq.fixture.source"
    private val destination = "dev.lasq.fixture.destination"
    private val flags = UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES

    private fun shell(cmd: String): String =
        ParcelFileDescriptor.AutoCloseInputStream(inst.getUiAutomation(flags).executeShellCommand(cmd))
            .bufferedReader()
            .use { it.readText().trim() }

    private fun await(message: String, condition: () -> Boolean) {
        val end = SystemClock.elapsedRealtime() + 15_000
        do {
            if (condition()) return
            SystemClock.sleep(100)
        } while (SystemClock.elapsedRealtime() < end)
        fail(message)
    }

    private fun present(s: String) = runBlocking {
        ctx.app.vault.read { it.search(0, "LASQ_FIXTURE_$s", "", false, 5).isNotEmpty() }
    }

    @Test
    fun pasteAcrossTwoRealPackagesOnceAndOnlyWithConsent() {
        // Shell provisioning is test-only, explicitly opted into, and restricted to disposable
        // emulators.
        Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("ciSmoke") == "true")
        check(ctx.packageName.endsWith(".debug") && Build.HARDWARE in setOf("ranchu", "goldfish"))
        Configurator.getInstance().uiAutomationFlags = flags
        val device = UiDevice.getInstance(inst)
        val pkg = ctx.packageName
        val previousA11y = shell("settings get secure enabled_accessibility_services")
        val previousEnabled = shell("settings get secure accessibility_enabled")
        val previousIme = shell("settings get secure default_input_method")
        val ime = "$pkg/${PasteBoard::class.java.name}"
        val access = "$pkg/${PasteAccess::class.java.name}"
        val old = ctx.app.prefs.value
        fun open(target: String) {
            shell("am start -W -f 0x10008000 -n $target/dev.lasq.fixture.Editor")
            assertTrue(device.wait(Until.hasObject(By.res(target, "editor")), 8_000))
        }
        fun click(name: String) {
            requireNotNull(device.wait(Until.findObject(By.desc("copy-$name")), 5_000)).click()
        }
        fun fieldIs(text: String) = device.hasObject(By.res(destination, "editor").text(text))
        ActivityScenario.launch(MainActivity::class.java).use { host ->
            try {
                if (Build.VERSION.SDK_INT >= 33)
                    shell("pm grant $pkg android.permission.POST_NOTIFICATIONS")
                if (Build.VERSION.SDK_INT >= 29) {
                    shell("ime enable $ime")
                    shell("ime set $ime")
                }
                val enabled =
                    (previousA11y.split(':').filter { it.isNotBlank() && it != "null" } + access)
                        .distinct()
                        .joinToString(":")
                shell("settings put secure enabled_accessibility_services $enabled")
                shell("settings put secure accessibility_enabled 1")
                await("Accessibility binding was not established") {
                    PasteAccess.instance != null && Checks.access(ctx)
                }
                host.onActivity { a ->
                    a.app.prefs.change {
                        Options(intro = true, consent = true, allowed = setOf(source, destination))
                    }
                    assertNull(Checks.reason(a))
                    assertTrue(Monitor.start(a))
                }
                await("Foreground service did not start") { ctx.app.active.value }
                open(source)
                click("sensitive")
                click("instant")
                await("Copy not archived from the allowed source") { present("instant") }
                assertFalse("Sensitive clip reached the vault", present("sensitive"))
                assertTrue(device.hasObject(By.res(source, "editor").text("Original fixture field")))
                open(destination)
                await("Accessibility paste did not reach the destination") {
                    fieldIs("LASQ_FIXTURE_instant")
                }
                inst.runOnMainSync { ctx.app.prefs.change { it.copy(mode = Mode.CONFIRM) } }
                open(source)
                click("confirm")
                await("Confirmation copy not captured") { present("confirm") }
                open(destination)
                fun prompt() =
                    ctx.system<NotificationManager>()
                        .activeNotifications
                        .firstOrNull { it.id == Alerts.PROMPT }
                        ?.notification
                await("No per-field confirmation action") { prompt() != null }
                assertFalse(fieldIs("LASQ_FIXTURE_confirm"))
                requireNotNull(prompt()).actions.single().actionIntent.send()
                await("Confirmed paste not delivered") { fieldIs("LASQ_FIXTURE_confirm") }
                inst.runOnMainSync { ctx.app.prefs.change { it.copy(mode = Mode.ARCHIVE) } }
                open(source)
                click("archive")
                await("History-only copy not saved") { present("archive") }
                open(destination)
                SystemClock.sleep(500)
                assertFalse(fieldIs("LASQ_FIXTURE_archive"))
                assertNull(prompt())
            } finally {
                inst.runOnMainSync {
                    Monitor.instance?.halt(false)
                    ctx.app.prefs.change { old }
                }
                if (Build.VERSION.SDK_INT >= 29) {
                    if (previousIme != "null") shell("ime set $previousIme")
                    shell("ime disable $ime")
                }
                if (previousA11y == "null")
                    shell("settings delete secure enabled_accessibility_services")
                else shell("settings put secure enabled_accessibility_services $previousA11y")
                if (previousEnabled == "null") shell("settings delete secure accessibility_enabled")
                else shell("settings put secure accessibility_enabled $previousEnabled")
                runBlocking {
                    ctx.app.vault.write { dao ->
                        dao.search(0, "LASQ_FIXTURE_", "", false, 100).forEach { dao.delete(it.id) }
                    }
                }
            }
        }
    }
}
