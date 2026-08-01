package com.arny.habrrss.domain.util

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

/**
 * Parses RFC 822 ("Sat, 02 May 2026 10:00:00 GMT") and ISO 8601
 * ("2026-08-02T14:30:00+03:00" / "…Z") to epoch milliseconds.
 */
internal fun String.toEpochMillis(): Long? {
    val trimmed = trim()
    return trimmed.parseIso8601() ?: trimmed.parseRfc822()
}

private fun String.parseIso8601(): Long? = try {
    val withSeconds = Regex("""(\d{4})-(\d{2})-(\d{2})[T ](\d{2}):(\d{2}):(\d{2})([+-]\d{2}:?\d{2}|Z)?""")
        .find(this) ?: return null
    val g = withSeconds.groupValues
    val dateTime = LocalDateTime(
        year = g[1].toInt(),
        month = Month(g[2].toInt()),
        day = g[3].toInt(),
        hour = g[4].toInt(),
        minute = g[5].toInt(),
        second = g[6].toInt(),
    )
    val tz = g.getOrNull(7)?.takeIf { it.isNotBlank() } ?: "Z"
    dateTime.toInstant(tz.toIsoTimeZone()).toEpochMilliseconds()
} catch (_: Exception) {
    null
}

private fun String.toIsoTimeZone(): TimeZone = when {
    this == "Z" -> TimeZone.UTC
    length == 6 && get(3) == ':' -> TimeZone.of(this) // +03:00
    length == 5 -> TimeZone.of("${substring(0, 3)}:${substring(3)}") // +0300
    else -> TimeZone.UTC
}

private fun String.parseRfc822(): Long? = try {
    val patterns = listOf(
        Regex("""(\d{1,2})\s+(\w{3})\s+(\d{4})\s+(\d{1,2}):(\d{2}):(\d{2})\s*(\w+)?"""),
        Regex("""(\d{1,2})\s+(\w{3})\s+(\d{4})\s+(\d{1,2}):(\d{2}):(\d{2})"""),
    )
    patterns.firstNotNullOfOrNull { pattern ->
        val match = pattern.find(this) ?: return@firstNotNullOfOrNull null
        val g = match.groupValues
        val day = g[1].toIntOrNull() ?: return@firstNotNullOfOrNull null
        val month = g[2].toMonthNumber() ?: return@firstNotNullOfOrNull null
        val year = g[3].toIntOrNull() ?: return@firstNotNullOfOrNull null
        val hour = g[4].toIntOrNull() ?: 0
        val minute = g[5].toIntOrNull() ?: 0
        val second = g[6].toIntOrNull() ?: 0
        val tz = g.getOrNull(7) ?: "GMT"
        val localDateTime = LocalDateTime(year, month, day, hour, minute, second)
        localDateTime.toInstant(tz.toRfc822TimeZone()).toEpochMilliseconds()
    }
} catch (_: Exception) {
    null
}

private fun String.toMonthNumber(): Month? = when (uppercase()) {
    "JAN" -> Month.JANUARY
    "FEB" -> Month.FEBRUARY
    "MAR" -> Month.MARCH
    "APR" -> Month.APRIL
    "MAY" -> Month.MAY
    "JUN" -> Month.JUNE
    "JUL" -> Month.JULY
    "AUG" -> Month.AUGUST
    "SEP" -> Month.SEPTEMBER
    "OCT" -> Month.OCTOBER
    "NOV" -> Month.NOVEMBER
    "DEC" -> Month.DECEMBER
    else -> null
}

private fun String.toRfc822TimeZone(): TimeZone = when (uppercase()) {
    "GMT", "UTC", "Z" -> TimeZone.UTC
    "EST" -> TimeZone.of("UTC-05:00")
    "EDT" -> TimeZone.of("UTC-04:00")
    "CST" -> TimeZone.of("UTC-06:00")
    "CDT" -> TimeZone.of("UTC-05:00")
    "MST" -> TimeZone.of("UTC-07:00")
    "MDT" -> TimeZone.of("UTC-06:00")
    "PST" -> TimeZone.of("UTC-08:00")
    "PDT" -> TimeZone.of("UTC-07:00")
    else -> when {
        length == 5 -> TimeZone.of("$this:00") // +0300
        length == 6 && get(3) == ':' -> TimeZone.of(this) // +03:00
        length == 6 -> TimeZone.of("${substring(0, 3)}:${substring(3)}")
        else -> TimeZone.UTC
    }
}
