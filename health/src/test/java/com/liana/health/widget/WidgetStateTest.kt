package com.liana.health.widget

import com.liana.health.data.CachedState
import com.liana.health.data.HealthConnectAvailability
import com.liana.health.data.Reading
import com.liana.health.data.Snapshot
import com.liana.health.data.UnitPreference
import com.liana.health.data.WeightMetric
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

class WidgetStateTest {

    private val now: Instant = Instant.parse("2026-08-30T09:00:00Z")
    private val utc: ZoneId = ZoneId.of("UTC")

    private fun cache(
        daysAgo: Long? = 2,
        previousDaysAgo: Long? = 9,
        granted: Boolean = true,
    ) = CachedState(
        snapshot = daysAgo?.let {
            Snapshot(
                latest = Reading(72.4, now.minus(Duration.ofDays(it))),
                previous = previousDaysAgo?.let { d -> Reading(72.7, now.minus(Duration.ofDays(d))) },
            )
        },
        permissionGranted = granted,
        units = UnitPreference.Kilograms,
    )

    private fun stateOf(
        cached: CachedState,
        availability: HealthConnectAvailability = HealthConnectAvailability.Available,
    ) = widgetStateOf(
        availability = availability,
        cached = cached,
        now = now,
        trend = { WeightMetric.trend(it, UnitPreference.Kilograms) },
        zone = utc,
    )

    @Test
    fun `a recent reading with a comparison point is ready, with its trend`() {
        val state = stateOf(cache()) as WidgetState.Ready
        assertEquals(72.4, state.reading.value, 0.0)
        assertEquals("0.3 kg this week", state.trend!!.text)
        assertEquals("2 days ago", state.recency)
    }

    @Test
    fun `ready without a comparison point still renders, just without a trend`() {
        val state = stateOf(cache(previousDaysAgo = null)) as WidgetState.Ready
        assertNull(state.trend)
    }

    @Test
    fun `a reading past the stale threshold goes stale`() {
        val state = stateOf(cache(daysAgo = 20, previousDaysAgo = null))
        assertTrue(state is WidgetState.Stale)
    }

    @Test
    fun `stale drops the trend even when a comparison point exists`() {
        // The Stale type has no trend field at all, so this is enforced by construction rather
        // than by remembering not to render one.
        val state = stateOf(cache(daysAgo = 20, previousDaysAgo = 40)) as WidgetState.Stale
        assertEquals("20 days ago", state.recency)
    }

    @Test
    fun `the stale boundary is exclusive, so exactly a fortnight is still ready`() {
        assertTrue(stateOf(cache(daysAgo = 14, previousDaysAgo = null)) is WidgetState.Ready)
        assertTrue(stateOf(cache(daysAgo = 15, previousDaysAgo = null)) is WidgetState.Stale)
    }

    @Test
    fun `no permission beats having a cached reading`() {
        // The number is still in the cache from before the revoke. Showing it as current would
        // imply we can still see it.
        assertEquals(WidgetState.NeedsPermission, stateOf(cache(granted = false)))
    }

    @Test
    fun `no permission and no reading is a permission problem, not an empty one`() {
        // Without permission we cannot know whether there is data, so "no weight yet" would be a
        // guess — and would point the user at Samsung Health instead of at one tap.
        assertEquals(WidgetState.NeedsPermission, stateOf(cache(daysAgo = null, granted = false)))
    }

    @Test
    fun `granted with nothing cached names the likely cause`() {
        val state = stateOf(cache(daysAgo = null)) as WidgetState.Unavailable
        assertEquals(WidgetState.Unavailable.Reason.NoData, state.reason)
    }

    @Test
    fun `a missing provider outranks everything, even a good cached reading`() {
        val state = stateOf(cache(), HealthConnectAvailability.NotSupported) as WidgetState.Unavailable
        assertEquals(WidgetState.Unavailable.Reason.NoProvider, state.reason)
    }

    @Test
    fun `a provider needing an update is also unavailable, not stale`() {
        val state = stateOf(cache(), HealthConnectAvailability.ProviderUpdateRequired)
        assertTrue(state is WidgetState.Unavailable)
    }
}

class NumeralSizeTest {

    @Test
    fun `a four glyph reading gets the full size`() {
        assertEquals(116f, numeralSp("72.4", base = 116f), 0.01f)
    }

    @Test
    fun `pounds shrink to fit`() {
        // The bug this exists to prevent: 159.6 at the kilogram size runs off the cover screen.
        assertEquals(92.8f, numeralSp("159.6", base = 116f), 0.01f)
    }

    @Test
    fun `three digits and a decimal still fit at three figures`() {
        assertEquals(77.33f, numeralSp("159.60", base = 116f), 0.01f)
    }

    @Test
    fun `short readings do not grow`() {
        // Otherwise the number would visibly change size the day you crossed below 100.
        assertEquals(116f, numeralSp("9.4", base = 116f), 0.01f)
        assertEquals(116f, numeralSp("99.9", base = 116f), 0.01f)
    }
}
