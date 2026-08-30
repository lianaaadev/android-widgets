package com.liana.health.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration
import java.time.Instant

/**
 * The trend is two numbers picked out of a list, which sounds too simple to test until you
 * consider what "seven days back" means when readings are irregular — which, for weight, they
 * always are.
 */
class WeightMetricTest {

    private val now: Instant = Instant.parse("2026-08-30T09:00:00Z")

    private fun reading(daysAgo: Long, kilograms: Double) =
        SourcedReading(
            reading = Reading(value = kilograms, at = now.minus(Duration.ofDays(daysAgo))),
            sourcePackage = "com.sec.android.app.shealth",
        )

    @Test
    fun `no readings means no snapshot`() {
        assertNull(WeightMetric.snapshotFrom(emptyList(), now))
    }

    @Test
    fun `latest is the newest reading regardless of list order`() {
        val snapshot = WeightMetric.snapshotFrom(
            listOf(reading(9, 73.1), reading(2, 72.4), reading(5, 72.6)),
            now,
        )
        assertEquals(72.4, snapshot!!.latest.value, 0.0)
    }

    @Test
    fun `comparison is the newest reading at or beyond the seven day mark`() {
        // 9 and 12 days back both qualify; 9 is the newer, so it is the one to compare against.
        val snapshot = WeightMetric.snapshotFrom(
            listOf(reading(2, 72.4), reading(5, 72.6), reading(9, 72.7), reading(12, 73.1)),
            now,
        )
        assertEquals(72.7, snapshot!!.previous!!.value, 0.0)
    }

    @Test
    fun `a reading inside the window is not a comparison point`() {
        // Only 3 days of history. A delta over 3 days labelled "this week" would be a lie, so
        // there is no trend rather than a short one.
        val snapshot = WeightMetric.snapshotFrom(listOf(reading(1, 72.4), reading(3, 73.0)), now)
        assertNull(snapshot!!.previous)
    }

    @Test
    fun `a reading exactly seven days old counts`() {
        val snapshot = WeightMetric.snapshotFrom(listOf(reading(0, 72.4), reading(7, 72.7)), now)
        assertEquals(72.7, snapshot!!.previous!!.value, 0.0)
    }

    @Test
    fun `the only reading is never its own comparison point`() {
        // A single record 30 days old is both the newest and older than the target. Comparing it
        // with itself would render "level this week" off one reading.
        val snapshot = WeightMetric.snapshotFrom(listOf(reading(30, 72.4)), now)
        assertEquals(72.4, snapshot!!.latest.value, 0.0)
        assertNull(snapshot.previous)
    }

    @Test
    fun `losing weight reads as down`() {
        val trend = trendOf(latest = 72.4, previous = 72.7)
        assertEquals(TrendDirection.Down, trend!!.direction)
        assertEquals("0.3 kg this week", trend.text)
    }

    @Test
    fun `gaining weight reads as up, in the same neutral wording`() {
        val trend = trendOf(latest = 73.0, previous = 72.7)
        assertEquals(TrendDirection.Up, trend!!.direction)
        assertEquals("0.3 kg this week", trend.text)
    }

    @Test
    fun `a difference that rounds away is level, not a tiny arrow`() {
        val trend = trendOf(latest = 72.42, previous = 72.40)
        assertEquals(TrendDirection.Level, trend!!.direction)
        assertEquals("level this week", trend.text)
    }

    @Test
    fun `no comparison point means no trend`() {
        val snapshot = Snapshot(latest = Reading(72.4, now), previous = null)
        assertNull(WeightMetric.trend(snapshot, UnitPreference.Kilograms))
    }

    @Test
    fun `the delta is computed in the display unit, not converted after rounding`() {
        // 0.3 kg is 0.66 lb. Rounding the kg delta first would show "0.3 lb", which is wrong by
        // more than a factor of two.
        val trend = trendOf(latest = 72.4, previous = 72.7, units = UnitPreference.Pounds)
        assertEquals("0.7 lb this week", trend!!.text)
    }

    private fun trendOf(
        latest: Double,
        previous: Double,
        units: UnitPreference = UnitPreference.Kilograms,
    ) = WeightMetric.trend(
        Snapshot(
            latest = Reading(latest, now),
            previous = Reading(previous, now.minus(Duration.ofDays(8))),
        ),
        units,
    )
}
