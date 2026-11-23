package dev.lasq.fawri

import dev.lasq.fawri.core.*
import dev.lasq.fawri.data.*
import org.junit.Assert.*
import org.junit.Test

class RulesTest {
    @Test
    fun emptyAllowListDeniesByDefault() {
        assertFalse(Rules.permits("notes.example", "self", Options()))
    }

    @Test
    fun neverOverrideProtectedPackages() {
        val o = Options(allowList = false)
        for (id in
            listOf(
                "org.example.bank",
                "com.bitwarden.vault",
                "self",
                "com.android.systemui",
            )) assertFalse(Rules.permits(id, "self", o))
        assertFalse(
            Rules.permits("org.example.bank", "self", Options(allowed = setOf("org.example.bank"))),
        )
    }

    @Test
    fun sourceAndDestinationPolicy() {
        val s = Options(allowed = setOf("one"))
        assertTrue(Rules.permits("one", "self", s))
        assertFalse(Rules.permits("two", "self", s))
        assertFalse(Rules.permits("two", "self", s.copy(allowList = false, denied = setOf("two"))))
    }

    @Test
    fun contentFilter() {
        assertEquals(Kind.NUMBER, Rules.kind("١٢٣"))
        assertEquals(Kind.LINK, Rules.kind("https://example.org/"))
        assertEquals(Kind.TEXT, Rules.kind("see https://example.org/"))
        assertNull(Rules.prepare("a", Options(types = setOf(Kind.NUMBER))))
        assertNull(Rules.prepare("x".repeat(MAX_TEXT + 1), Options()))
        assertNull(Rules.prepare(" \n ", Options()))
    }

    @Test
    fun cleaningPreservesSemantics() {
        assertEquals("نص آخر\nثانٍ", Rules.prepare("  نص   آخر\nثانٍ  ", Options(trim = true)))
        val url = "https://x.org/a%2Fb?x=1&utm_source=one&gclid=q&x=2#top"
        assertEquals("https://x.org/a%2Fb?x=1&x=2#top", Rules.prepare(url, Options(tracking = true)))
        assertEquals(url, Rules.prepare(url, Options()))
        assertEquals(
            "https://x.org/#a",
            Rules.prepare("https://x.org/?fbclid=1#a", Options(tracking = true)),
        )
    }

    private val s = Field("source", 1, "a")
    private val d = Field("dest", 1, "a")

    @Test
    fun noLoopsOrAmbiguousSameApp() {
        val g = DeliveryGate()
        val p = g.offer(1, "hello", s, 0)
        assertNull(g.take(p.id, s, 1, 10))
        assertNull(g.take(p.id, Field("source", 2, null), 1, 10))
        assertNotNull(g.take(p.id, d, 1, 11))
        assertNull(g.take(p.id, d, 1, 12))
    }

    @Test
    fun differentKnownFieldSameApp() {
        assertTrue(Field("source", 1, "b").distinctFrom(s))
        assertFalse(Field("source", 2, "a").distinctFrom(s))
    }

    @Test
    fun onlyNewestAndUnexpiredTicket() {
        val g = DeliveryGate()
        val old = g.offer(1, "old", s, 0)
        val cur = g.offer(2, "new", s, 1)
        assertNull(g.take(old.id, d, 2, 2))
        assertNull(g.take(cur.id, d, 3, 2))
        assertNull(g.take(cur.id, d, 2, 45_001))
        g.clear()
        assertNull(g.current)
    }

    @Test
    fun archiveAuthenticatesAndAppliesRetention() {
        val now = DAY * 50
        val c =
            Clip(
                body = "أهلاً",
                source = "يدوي",
                time = now - 1,
                kind = "TEXT",
                pinned = true,
                bucket = "WORK",
            )
        val pass = "عبارة مرور محلية".toCharArray()
        val b = Backup.seal(listOf(c, c.copy(time = now - 8 * DAY)), pass)
        assertFalse(b.toString(Charsets.UTF_8).contains("أهلاً"))
        assertEquals(listOf(c), Backup.open(b, pass, now, 7))
        assertThrows(Exception::class.java) { Backup.open(b, "another password".toCharArray(), now, 7) }
        b[b.lastIndex] = (b.last().toInt() xor 1).toByte()
        assertThrows(Exception::class.java) { Backup.open(b, pass, now, 7) }
    }

    @Test
    fun archiveRejectsMalformedOrOversizedInput() {
        assertThrows(IllegalArgumentException::class.java) {
            Backup.readBounded(ByteArray(Backup.LIMIT + 1).inputStream())
        }
        assertThrows(IllegalArgumentException::class.java) {
            Backup.open(byteArrayOf(1, 2), "abcdefghij".toCharArray(), 100, 1)
        }
    }
}
