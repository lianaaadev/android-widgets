package com.liana.health.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class RecencyTest {

    private val london = ZoneId.of("Europe/London")

    @Test
    fun `age is counted in calendar days, not elapsed hours`() {
        // Recorded at 11pm, read at 7am the next morning: eight hours apart, but "yesterday" is
        // what anyone reading the widget means.
        val at = Instant.parse("2026-08-29T22:00:00Z")
        val now = Instant.parse("2026-08-30T06:00:00Z")
        assertEquals("yesterday", Recency.describe(at, now, london))
    }

    @Test
    fun `same day is today even across many hours`() {
        val at = Instant.parse("2026-08-30T06:00:00Z")
        val now = Instant.parse("2026-08-30T21:00:00Z")
        assertEquals("today", Recency.describe(at, now, london))
    }

    @Test
    fun `older readings count days`() {
        val at = Instant.parse("2026-08-28T08:00:00Z")
        val now = Instant.parse("2026-08-30T08:00:00Z")
        assertEquals("2 days ago", Recency.describe(at, now, london))
    }
}
