package com.arny.habrrss.ui.components

import com.arny.habrrss.domain.util.toEpochMillis
import kotlin.math.abs
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Formats a date string (RFC 822 or ISO 8601) as a human-readable relative time:
 * "только что", "5 мин назад", "2 ч назад", "вчера", "3 дн назад"
 * and falls back to an absolute date ("5 авг 2026") for older entries.
 */
internal fun humanReadableDate(value: String?, epochMillis: Long? = null): String {
    val epoch = epochMillis ?: value?.toEpochMillis() ?: return ""
    return formatRelative(epoch, Clock.System.now().toEpochMilliseconds())
}

internal fun formatRelative(epochMillis: Long, nowMillis: Long): String {
    val diffMillis = nowMillis - epochMillis
    if (diffMillis < 0) return absoluteDate(epochMillis)

    val diffMinutes = diffMillis / 60_000L
    return when {
        diffMinutes < 1 -> "только что"
        diffMinutes < 60 -> pluralRu(diffMinutes, "минуту", "минуты", "минут") + " назад"
        diffMinutes < 24 * 60 -> pluralRu(diffMinutes / 60, "час", "часа", "часов") + " назад"
        diffMinutes < 48 * 60 -> "вчера"
        diffMinutes < 7 * 24 * 60 -> pluralRu(diffMinutes / (24 * 60), "день", "дня", "дней") + " назад"
        diffMillis / (7 * 24 * 60 * 60_000L) < 4 -> {
            val weeks = diffMillis / (7 * 24 * 60 * 60_000L)
            pluralRu(weeks, "неделю", "недели", "недель") + " назад"
        }
        else -> absoluteDate(epochMillis)
    }
}

private fun absoluteDate(epochMillis: Long): String {
    val local = Instant.fromEpochMilliseconds(epochMillis)
        .toLocalDateTime(TimeZone.currentSystemDefault())
    return "${local.day} ${local.month.ruGenitive()} ${local.year}"
}

private fun Month.ruGenitive(): String = when (this) {
    Month.JANUARY -> "января"
    Month.FEBRUARY -> "февраля"
    Month.MARCH -> "марта"
    Month.APRIL -> "апреля"
    Month.MAY -> "мая"
    Month.JUNE -> "июня"
    Month.JULY -> "июля"
    Month.AUGUST -> "августа"
    Month.SEPTEMBER -> "сентября"
    Month.OCTOBER -> "октября"
    Month.NOVEMBER -> "ноября"
    Month.DECEMBER -> "декабря"
}

private fun pluralRu(value: Long, one: String, few: String, many: String): String {
    val v = abs(value)
    val n10 = v % 10
    val n100 = v % 100
    val word = when {
        n10 == 1L && n100 != 11L -> one
        n10 in 2L..4L && (n100 < 12 || n100 > 14) -> few
        else -> many
    }
    return "$value $word"
}
