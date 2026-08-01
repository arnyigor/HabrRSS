package com.arny.habrrss

import com.arny.habrrss.domain.util.toEpochMillis
import com.arny.habrrss.ui.components.formatRelative
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DateFormattingTest {

    @Test
    fun parsesRfc822AndIso8601ToSameEpoch() {
        val rfc = "Sat, 02 May 2026 10:00:00 GMT"
        val iso = "2026-05-02T10:00:00Z"
        assertEquals(rfc.toEpochMillis(), iso.toEpochMillis())
        assertNotNull(rfc.toEpochMillis())
    }

    @Test
    fun parsesIso8601WithOffset() {
        val withOffset = "2026-08-02T14:30:00+03:00"
        val utc = "2026-08-02T11:30:00Z"
        assertEquals(withOffset.toEpochMillis(), utc.toEpochMillis())
    }

    @Test
    fun parsesIso8601WithSpaceAndCompactOffset() {
        val withSpace = "2026-08-02 14:30:00+0300"
        val utc = "2026-08-02T11:30:00Z"
        assertEquals(withSpace.toEpochMillis(), utc.toEpochMillis())
    }

    @Test
    fun returnsNullForGarbage() {
        assertNull("not a date".toEpochMillis())
        assertNull("".toEpochMillis())
        assertNull("   ".toEpochMillis())
    }

    @Test
    fun relativeTimeBuckets() {
        val now = 1_000_000_000_000L
        val minute = 60_000L
        val hour = 3_600_000L
        val day = 24 * hour

        assertEquals("только что", formatRelative(now - 30_000, now))
        assertEquals("5 минут назад", formatRelative(now - 5 * minute, now))
        assertEquals("2 часа назад", formatRelative(now - 2 * hour, now))
        assertEquals("вчера", formatRelative(now - 30 * hour, now))
        assertEquals("3 дня назад", formatRelative(now - 3 * day, now))
        assertEquals("2 недели назад", formatRelative(now - 15 * day, now))
    }

    @Test
    fun singularAndTeenPlurals() {
        val now = 1_000_000_000_000L
        val minute = 60_000L
        val hour = 3_600_000L
        val day = 24 * hour

        assertEquals("1 минуту назад", formatRelative(now - minute, now))
        assertEquals("21 час назад", formatRelative(now - 21 * hour, now))
        assertEquals("11 часов назад", formatRelative(now - 11 * hour, now))
        assertEquals("вчера", formatRelative(now - day, now))
        assertEquals("2 недели назад", formatRelative(now - 14 * day, now))
    }

    @Test
    fun oldDatesFallBackToAbsolute() {
        val now = 1_000_000_000_000L
        val day = 24 * 3_600_000L
        val result = formatRelative(now - 100 * day, now)
        assertTrue("назад" !in result)
        assertTrue(result.contains("2001"), "Expected year in result, got: $result")
    }

    @Test
    fun futureDatesFallBackToAbsolute() {
        val now = 1_000_000_000_000L
        val result = formatRelative(now + 3_600_000L, now)
        assertTrue("назад" !in result)
        assertTrue(result.contains("2001"), "Expected year in result, got: $result")
    }
}
