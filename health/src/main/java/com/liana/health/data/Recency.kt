package com.liana.health.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * How a reading's age is described. Always shown next to the number: a Galaxy Watch syncs on
 * Samsung's schedule rather than ours, so the widget never implies a value is current.
 *
 * Counted in calendar days rather than elapsed hours. A reading from 11pm last night is
 * "yesterday" at 7am, not "8 hours ago" — the day boundary is what people actually mean, and it
 * is also what the daily midnight tick can cheaply roll over without a Health Connect call.
 */
object Recency {

    private val DayMonth = DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())

    fun describe(at: Instant, now: Instant, zone: ZoneId = ZoneId.systemDefault()): String =
        when (val days = daysBetween(at, now, zone)) {
            0L -> "today"
            1L -> "yesterday"
            else -> "$days days ago"
        }

    fun formatDate(at: Instant, zone: ZoneId = ZoneId.systemDefault()): String =
        DayMonth.format(at.atZone(zone))

    fun daysBetween(at: Instant, now: Instant, zone: ZoneId = ZoneId.systemDefault()): Long =
        ChronoUnit.DAYS.between(
            LocalDate.ofInstant(at, zone),
            LocalDate.ofInstant(now, zone),
        )
}
