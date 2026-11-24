package dev.lasq.fawri

import android.content.*
import android.content.pm.PackageManager
import android.view.WindowManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.lasq.fawri.core.CopyEvent
import dev.lasq.fawri.data.*
import dev.lasq.fawri.platform.*
import dev.lasq.fawri.ui.MainActivity
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeviceTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()

    @Test
    fun copyOnceDeleteAndDoNotResurrectOnReopen() {
        val a = ui.activity
        val text = "نص التجربة كما نُسخ"
        ui.runOnIdle { a.changeRecording(false) }
        runBlocking { a.app.vault.write { it.clear() } }
        assertTrue(a.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
        ui.onNodeWithText("سجل النسخ").assertExists()
        ui.onNodeWithText("بدء الحفظ").performClick()
        ui.runOnIdle {
            a.system<ClipboardManager>().setPrimaryClip(ClipData.newPlainText("local", text))
        }
        fun count() = runBlocking { a.app.vault.read { it.page(text, 10).size } }
        ui.waitUntil(15_000) { count() == 1 }
        ui.waitUntil(8_000) { ui.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
        // Search by body so an existing real system clip never changes which button we click.
        ui.onNodeWithContentDescription("بحث في النصوص").performClick()
        ui.onNode(hasSetTextAction()).performTextInput(text)
        ui.waitUntil(8_000) {
            ui.onAllNodesWithContentDescription("حذف العنصر").fetchSemanticsNodes().size == 1
        }
        ui.onNodeWithContentDescription("حذف العنصر").performClick()
        ui.onNodeWithText("حذف").performClick()
        ui.waitUntil(8_000) { count() == 0 }
        ui.activityRule.scenario.recreate()
        ui.waitForIdle()
        assertEquals(0, count())
        ui.runOnIdle {
            ui.activity.system<ClipboardManager>().setPrimaryClip(ClipData.newPlainText("again", text))
        }
        ui.waitUntil(8_000) { count() == 1 }
        ui.runOnIdle { ui.activity.changeRecording(false) }
        runBlocking { a.app.vault.write { it.clear() } }
    }
}

@RunWith(AndroidJUnit4::class)
class StorageTest {
    private val c
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Suppress("DEPRECATION")
    @Test
    fun allAgesPersistAcrossOpenAndManualClearKeepsOnlyTheCursor() = runBlocking {
        val vault = c.app.vault
        val e = CopyEvent(1, listOf("PRIVATE_SENTINEL_421", "  ٢٣\n"))
        vault.write {
            it.clear()
            it.remember(Cursor(stamp = 0, fingerprint = ""))
        }
        vault.capture(e)
        vault.capture(e)
        val fresh = Vault(c)
        assertEquals(2, fresh.read { it.count() })
        assertEquals("  ٢٣\n", fresh.read { it.text(it.page("٢٣", 1).single().id) })
        val raw = c.getDatabasePath("lasq-vault.db").readBytes().toString(Charsets.ISO_8859_1)
        assertFalse(raw.startsWith("SQLite format 3"))
        assertFalse(raw.contains("PRIVATE_SENTINEL"))
        vault.write { it.clear() }
        fresh.capture(e)
        assertEquals(0, vault.read { it.count() })
        fresh.capture(e.copy(stamp = 2))
        assertEquals(2, vault.read { it.count() })
        vault.write { it.clear() }
        assertEquals(
            PackageManager.PERMISSION_DENIED,
            c.checkSelfPermission(android.Manifest.permission.INTERNET),
        )
        assertFalse(
            c.packageManager
                .getPackageInfo(c.packageName, PackageManager.GET_SERVICES)
                .services
                .orEmpty()
                .any { it.permission == android.Manifest.permission.BIND_ACCESSIBILITY_SERVICE },
        )
    }
}
