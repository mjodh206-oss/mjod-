package dev.lasq.fawri

import android.app.UiAutomation
import android.os.*
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import dev.lasq.fawri.platform.*
import dev.lasq.fawri.ui.MainActivity
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CapturePathTest {
    private val inst = InstrumentationRegistry.getInstrumentation()
    private val ctx
        get() = inst.targetContext

    private val flags = UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES

    private fun shell(cmd: String) =
        ParcelFileDescriptor.AutoCloseInputStream(inst.getUiAutomation(flags).executeShellCommand(cmd))
            .bufferedReader()
            .use { it.readText().trim() }

    private fun await(message: String, check: () -> Boolean) {
        val end = SystemClock.elapsedRealtime() + 15_000
        while (!check()) {
            if (SystemClock.elapsedRealtime() > end) fail(message)
            SystemClock.sleep(80)
        }
    }

    private fun count(name: String) = runBlocking {
        ctx.app.vault.read { it.page("LASQ_FIXTURE_$name", 8).size }
    }

    @Test
    fun externalCopiesAreArchivedWithoutWritingIntoAnyEditor() {
        Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("ciSmoke") == "true")
        check(ctx.packageName.endsWith(".debug") && Build.HARDWARE in setOf("ranchu", "goldfish"))
        Configurator.getInstance().uiAutomationFlags = flags
        val device = UiDevice.getInstance(inst)
        val source = "dev.lasq.fixture.source"
        val destination = "dev.lasq.fixture.destination"
        val pkg = ctx.packageName
        val ime = "$pkg/${PasteBoard::class.java.name}"
        val original = shell("settings get secure default_input_method")
        fun open(p: String) {
            shell("am start -W -f 0x10008000 -n $p/dev.lasq.fixture.Editor")
            assertTrue(device.wait(Until.hasObject(By.res(p, "editor")), 8_000))
        }
        fun copy(name: String) {
            requireNotNull(device.wait(Until.findObject(By.desc("copy-$name")), 5_000)).click()
        }
        ActivityScenario.launch(MainActivity::class.java).use { host ->
            try {
                host.onActivity { it.changeRecording(true) }
                if (Build.VERSION.SDK_INT >= 29) {
                    open(source)
                    copy("first")
                    copy("last")
                    SystemClock.sleep(300)
                    assertEquals(
                        "Stock keyboard must not grant background clipboard access",
                        0,
                        count("last"),
                    )
                    shell("am start -W -n $pkg/${MainActivity::class.java.name}")
                    await("Latest text was not saved automatically on return") { count("last") == 1 }
                    assertEquals(0, count("first"))
                    shell("ime enable $ime")
                    shell("ime set $ime")
                    await("Keyboard selection not observed") { Checks.keyboard(ctx) }
                }
                open(source)
                await("Archive not ready in background") {
                    ctx.app.recorder.state.value == Recorder.State.INSTANT
                }
                copy("sensitive")
                SystemClock.sleep(250)
                copy("instant")
                await("Text not saved while still in the source app") { count("instant") == 1 }
                copy("instant")
                await("Second intentional copy of same text lost") { count("instant") == 2 }
                assertEquals(0, count("sensitive"))
                assertTrue(device.hasObject(By.res(source, "editor").text("Original fixture field")))
                open(destination)
                SystemClock.sleep(300)
                assertFalse(device.hasObject(By.text("LASQ_FIXTURE_instant")))
                inst.runOnMainSync { ctx.app.recorder.setEnabled(false) }
                open(source)
                copy("paused")
                SystemClock.sleep(250)
                assertEquals(0, count("paused"))
            } finally {
                inst.runOnMainSync { ctx.app.recorder.setEnabled(false) }
                if (Build.VERSION.SDK_INT >= 29 && original != "null") {
                    shell("ime set $original")
                    shell("ime disable $ime")
                }
                runBlocking { ctx.app.vault.write { it.clear() } }
            }
        }
    }
}
