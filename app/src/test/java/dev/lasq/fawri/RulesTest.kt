package dev.lasq.fawri

import dev.lasq.fawri.core.*
import dev.lasq.fawri.data.*
import org.junit.Assert.*
import org.junit.Test

class RulesTest {
    @Test
    fun repeatCallbacksHaveStableIdentityButNewCopiesAreDifferent() {
        val a = CopyEvent(1, listOf("  نص\n", "١٢٣"))
        val b = a.copy(stamp = 2)
        assertEquals(a.fingerprint(), b.fingerprint())
        assertNotEquals(
            Cursor(stamp = a.stamp, fingerprint = a.fingerprint()),
            Cursor(stamp = b.stamp, fingerprint = b.fingerprint()),
        )
        assertNotEquals(
            CopyEvent(1, listOf("ab", "c")).fingerprint(),
            CopyEvent(1, listOf("a", "bc")).fingerprint(),
        )
    }

    @Test
    fun textIsNotFilteredOrCleaned() {
        val raw = "  https://example.org/?utm_source=x\n  ١٢٣  "
        assertEquals(raw, CopyEvent(7, listOf(raw)).parts.single())
        assertEquals("   ", CopyEvent(7, listOf("   ")).parts.single())
        assertThrows(IllegalArgumentException::class.java) {
            CopyEvent(5, listOf("x".repeat(MAX_TEXT + 1)))
        }
    }

    @Test
    fun oldAndPinnedItemsComeBackWithoutAnyExpiry() {
        val rows =
            listOf(
                Clip(body = "نص قديم", time = 1, pinned = true, bucket = "WORK"),
                Clip(body = "  ", time = 2),
            )
        val pass = "عبارة مرور محلية".toCharArray()
        val blob = Backup.seal(rows, pass)
        assertFalse(blob.toString(Charsets.UTF_8).contains("نص قديم"))
        assertEquals(rows, Backup.open(blob, pass))
        assertThrows(Exception::class.java) { Backup.open(blob, "not my password".toCharArray()) }
        blob[blob.lastIndex] = (blob.last().toInt() xor 1).toByte()
        assertThrows(Exception::class.java) { Backup.open(blob, pass) }
    }

    @Test
    fun noUnboundedArchives() {
        assertThrows(IllegalArgumentException::class.java) {
            Backup.readBounded(ByteArray(Backup.LIMIT + 1).inputStream())
        }
    }
}
