package com.liana.countdown.domain

import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/**
 * How far away an occasion is, resolved against a particular day.
 *
 * [target] is the date actually being counted to. For a yearly occasion that is the next
 * occurrence, which is not necessarily the date the user originally entered.
 */
sealed interface CountdownState {
    val target: LocalDate

    data class Upcoming(val days: Long, override val target: LocalDate) : CountdownState

    data class Today(override val target: LocalDate) : CountdownState

    data class Past(val daysAgo: Long, override val target: LocalDate) : CountdownState
}

/**
 * The whole of the app's date arithmetic, kept pure so it can be tested without a device.
 *
 * Every entry point takes [today] rather than reading the clock, because "how many days away"
 * depends on the reader's time zone: the same instant is two different local dates either side
 * of midnight. Callers pass `LocalDate.now()` (system zone); tests pass a fixed date.
 */
object Countdown {

    fun stateFor(date: LocalDate, recurringYearly: Boolean, today: LocalDate): CountdownState {
        val target = if (recurringYearly) nextOccurrence(date, today) else date
        // Day arithmetic on LocalDate, never on milliseconds: a DST transition makes some local
        // days 23 or 25 hours long, and dividing a millisecond span by 86_400_000 drifts.
        val days = ChronoUnit.DAYS.between(today, target)
        return when {
            days > 0L -> CountdownState.Upcoming(days, target)
            days == 0L -> CountdownState.Today(target)
            else -> CountdownState.Past(-days, target)
        }
    }

    /**
     * The next time [anchor]'s month-and-day comes round, on or after [today]. A yearly occasion
     * is therefore never in the past — the day it passes, it rolls to next year.
     */
    fun nextOccurrence(anchor: LocalDate, today: LocalDate): LocalDate {
        val thisYear = occurrenceIn(today.year, anchor)
        return if (!thisYear.isBefore(today)) thisYear else occurrenceIn(today.year + 1, anchor)
    }

    /**
     * A 29 February anchor has no counterpart in a common year. We clamp to the last day of the
     * month — 28 February — so the occasion is marked three years in four rather than skipped.
     */
    private fun occurrenceIn(year: Int, anchor: LocalDate): LocalDate {
        val lastDayOfMonth = YearMonth.of(year, anchor.month).lengthOfMonth()
        return LocalDate.of(year, anchor.month, minOf(anchor.dayOfMonth, lastDayOfMonth))
    }

    /**
     * Sort key that puts today first, then the nearest upcoming occasion, with anything already
     * past sinking to the bottom in reverse order of how long ago it was.
     */
    fun sortKey(state: CountdownState): Long = when (state) {
        is CountdownState.Today -> -1L
        is CountdownState.Upcoming -> state.days
        is CountdownState.Past -> Long.MAX_VALUE - state.daysAgo
    }
}
