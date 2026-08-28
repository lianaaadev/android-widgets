package com.liana.countdown.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class CountdownTest {

    private fun date(iso: String) = LocalDate.parse(iso)

    // --- one-off occasions -------------------------------------------------------------------

    @Test
    fun `counts whole days to a future date`() {
        val state = Countdown.stateFor(date("2026-09-03"), recurringYearly = false, today = date("2026-08-27"))
        assertEquals(CountdownState.Upcoming(7, date("2026-09-03")), state)
    }

    @Test
    fun `the day itself is Today, not zero days`() {
        val state = Countdown.stateFor(date("2026-10-12"), recurringYearly = false, today = date("2026-10-12"))
        assertEquals(CountdownState.Today(date("2026-10-12")), state)
    }

    @Test
    fun `a one-off occasion that has passed reports how long ago`() {
        val state = Countdown.stateFor(date("2026-08-15"), recurringYearly = false, today = date("2026-08-27"))
        assertEquals(CountdownState.Past(12, date("2026-08-15")), state)
    }

    @Test
    fun `counts across a year boundary`() {
        val state = Countdown.stateFor(date("2027-01-08"), recurringYearly = false, today = date("2026-12-01"))
        assertEquals(CountdownState.Upcoming(38, date("2027-01-08")), state)
    }

    @Test
    fun `a span containing 29 February counts the leap day`() {
        // 2028 is a leap year: 1 Feb to 1 Mar is 29 days, not 28.
        val state = Countdown.stateFor(date("2028-03-01"), recurringYearly = false, today = date("2028-02-01"))
        assertEquals(CountdownState.Upcoming(29, date("2028-03-01")), state)
    }

    // --- yearly occasions --------------------------------------------------------------------

    @Test
    fun `a yearly occasion still to come this year counts to this year`() {
        val state = Countdown.stateFor(date("1962-10-12"), recurringYearly = true, today = date("2026-08-27"))
        assertEquals(CountdownState.Upcoming(46, date("2026-10-12")), state)
    }

    @Test
    fun `a yearly occasion rolls to next year the day after it passes`() {
        val dayAfter = Countdown.stateFor(date("1962-10-12"), recurringYearly = true, today = date("2026-10-13"))
        assertEquals(CountdownState.Upcoming(364, date("2027-10-12")), dayAfter)
    }

    @Test
    fun `a yearly occasion is Today on the day, not rolled forward`() {
        val state = Countdown.stateFor(date("1962-10-12"), recurringYearly = true, today = date("2026-10-12"))
        assertEquals(CountdownState.Today(date("2026-10-12")), state)
    }

    @Test
    fun `a yearly occasion is never reported as past`() {
        val state = Countdown.stateFor(date("2020-01-01"), recurringYearly = true, today = date("2026-08-27"))
        assertEquals(CountdownState.Upcoming(127, date("2027-01-01")), state)
    }

    // --- 29 February -------------------------------------------------------------------------

    @Test
    fun `a 29 February occasion falls on 28 February in a common year`() {
        val state = Countdown.stateFor(date("2024-02-29"), recurringYearly = true, today = date("2027-01-01"))
        assertEquals(CountdownState.Upcoming(58, date("2027-02-28")), state)
    }

    @Test
    fun `a 29 February occasion falls on 29 February in a leap year`() {
        val state = Countdown.stateFor(date("2024-02-29"), recurringYearly = true, today = date("2028-01-01"))
        assertEquals(CountdownState.Upcoming(59, date("2028-02-29")), state)
    }

    @Test
    fun `clamping only applies to February — a 31st anchor keeps its day`() {
        // Every month but February has the same length in every year, so 31 January is always
        // 31 January. Guards against over-eager clamping.
        val state = Countdown.stateFor(date("2020-01-31"), recurringYearly = true, today = date("2026-04-01"))
        assertEquals(CountdownState.Upcoming(305, date("2027-01-31")), state)
    }

    // --- travel ------------------------------------------------------------------------------

    @Test
    fun `crossing the date line changes the answer by a day`() {
        // Same moment, two zones: 27 Aug in London is already 28 Aug in Auckland. The countdown
        // is computed from the reader's local date, so it legitimately differs by one.
        val target = date("2026-09-03")
        val london = Countdown.stateFor(target, recurringYearly = false, today = date("2026-08-27"))
        val auckland = Countdown.stateFor(target, recurringYearly = false, today = date("2026-08-28"))
        assertEquals(7L, (london as CountdownState.Upcoming).days)
        assertEquals(6L, (auckland as CountdownState.Upcoming).days)
    }

    // --- ordering ----------------------------------------------------------------------------

    @Test
    fun `today sorts first and past occasions sort last`() {
        val today = date("2026-08-27")
        val states = listOf(
            Countdown.stateFor(date("2026-09-03"), false, today),  // upcoming, 7
            Countdown.stateFor(date("2026-08-15"), false, today),  // past, 12
            Countdown.stateFor(date("2026-08-27"), false, today),  // today
            Countdown.stateFor(date("2026-08-30"), false, today),  // upcoming, 3
        ).sortedBy { Countdown.sortKey(it) }

        assertEquals(
            listOf(
                CountdownState.Today(date("2026-08-27")),
                CountdownState.Upcoming(3, date("2026-08-30")),
                CountdownState.Upcoming(7, date("2026-09-03")),
                CountdownState.Past(12, date("2026-08-15")),
            ),
            states,
        )
    }
}
