package dev.lasq.fawri

import android.content.Context
import android.content.pm.PackageManager
import android.view.WindowManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.lasq.fawri.core.DAY
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
    fun localLibraryWithoutAnyServicePermission() {
        val a = ui.activity
        assertFalse(a.app.active.value)
        assertTrue(a.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
        ui.waitUntil(12_000) {
            ui.onAllNodesWithText("استكشف التطبيق").fetchSemanticsNodes().isNotEmpty()
        }
        ui.onNodeWithText("استكشف التطبيق").performClick()
        ui.onNodeWithText("لوحة التحكم").assertExists()
        ui.runOnIdle { a.vm.manual("اختبار محلي فريد", "إضافة يدوية") }
        ui.onNodeWithText("السجل", useUnmergedTree = true).performClick()
        ui.waitUntil(12_000) {
            ui.onAllNodesWithText("اختبار محلي فريد").fetchSemanticsNodes().isNotEmpty()
        }
        ui.onNodeWithContentDescription("خيارات العنصر").performClick()
        ui.onNodeWithText("تثبيت").performClick()
        ui.onNodeWithText("المثبّتة").performClick()
        ui.onNodeWithText("اختبار محلي فريد").assertExists()
        assertFalse(a.app.prefs.value.consent)
        assertNull(Monitor.instance)
    }
}

@RunWith(AndroidJUnit4::class)
class PrivateStorageTest {
    private val c: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun encryptedFileAndRetention() = runBlocking {
        val now = System.currentTimeMillis()
        val vault = c.app.vault
        val id = vault.write {
            it.add(Clip(body = "SENTINEL_PRIVATE_47", source = "test", time = now, kind = "TEXT"))
        }
        vault.write {
            it.add(
                Clip(
                    body = "expire-me",
                    source = "test",
                    time = now - 3 * DAY,
                    kind = "TEXT",
                    pinned = true,
                ),
            )
        }
        vault.prune(1)
        assertTrue(
            vault.read { it.search(now - DAY, "SENTINEL_PRIVATE", "", false, 4) }.any { it.id == id },
        )
        assertTrue(vault.read { it.search(0, "expire-me", "", false, 4) }.isEmpty())
        val bytes = c.getDatabasePath("lasq-vault.db").readBytes().toString(Charsets.ISO_8859_1)
        assertFalse(bytes.startsWith("SQLite format 3"))
        assertFalse(bytes.contains("SENTINEL_PRIVATE"))
        vault.write { it.delete(id) }
        assertEquals(
            PackageManager.PERMISSION_DENIED,
            c.checkSelfPermission(android.Manifest.permission.INTERNET),
        )
        assertTrue(
            Checks.password(
                android.text.InputType.TYPE_CLASS_NUMBER or
                    android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD,
            ),
        )
        assertFalse(Checks.password(android.text.InputType.TYPE_CLASS_TEXT))
    }
}
