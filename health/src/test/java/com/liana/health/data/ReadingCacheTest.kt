package com.liana.health.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Instant

/**
 * The cache reached a phone with a bug a round trip would have caught, so it now gets one.
 */
class ReadingCacheTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun cache(scope: TestScope): ReadingCache {
        val store: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            scope = TestScope(UnconfinedTestDispatcher(scope.testScheduler)),
            produceFile = { folder.newFile("cache-${System.nanoTime()}.preferences_pb") },
        )
        return ReadingCache(store)
    }

    private val now: Instant = Instant.parse("2026-08-30T09:00:00Z")

    @Test
    fun `a snapshot with no comparison point round-trips`() = runTest {
        // The exact shape that crashed on a real phone: one reading, inside the trend window, so
        // previous is null and the previous-* keys have never been written. Clearing an absent
        // key used to unbox a null Long and take the app down.
        val cache = cache(this)
        val snapshot = Snapshot(latest = Reading(83.2, now.minusSeconds(4 * 86_400)), previous = null)

        cache.putSnapshot(snapshot, now)

        val state = cache.state.first()
        assertEquals(83.2, state.snapshot!!.latest.value, 0.0)
        assertNull(state.snapshot!!.previous)
        assertEquals(now, state.lastReadAt)
    }

    @Test
    fun `clearing an already empty cache is safe`() = runTest {
        // Same trap from the other direction: nothing has ever been written, and a successful
        // read that found nothing clears all four keys.
        val cache = cache(this)
        cache.putSnapshot(null, now)
        assertNull(cache.state.first().snapshot)
    }

    @Test
    fun `a previous reading survives and is then dropped when it goes away`() = runTest {
        val cache = cache(this)
        cache.putSnapshot(
            Snapshot(
                latest = Reading(72.4, now.minusSeconds(2 * 86_400)),
                previous = Reading(72.7, now.minusSeconds(9 * 86_400)),
            ),
            now,
        )
        assertEquals(72.7, cache.state.first().snapshot!!.previous!!.value, 0.0)

        // Now a read where the comparison point has aged out. The stored key must go, not linger.
        cache.putSnapshot(Snapshot(latest = Reading(72.4, now), previous = null), now)
        assertNull(cache.state.first().snapshot!!.previous)
    }

    @Test
    fun `a successful empty read clears a previously cached number`() = runTest {
        val cache = cache(this)
        cache.putSnapshot(Snapshot(Reading(72.4, now), null), now)
        assertEquals(72.4, cache.state.first().snapshot!!.latest.value, 0.0)

        cache.putSnapshot(null, now)
        assertNull(cache.state.first().snapshot)
    }

    @Test
    fun `permission and units persist independently of readings`() = runTest {
        val cache = cache(this)
        cache.putPermissionGranted(true)
        cache.putUnits(UnitPreference.Pounds)

        val state = cache.state.first()
        assertEquals(true, state.permissionGranted)
        assertEquals(UnitPreference.Pounds, state.units)
        assertNull(state.snapshot)
    }

    @Test
    fun `defaults are safe before anything has been written`() = runTest {
        val state = cache(this).state.first()
        assertNull(state.snapshot)
        assertNull(state.lastReadAt)
        assertEquals(false, state.permissionGranted)
        assertEquals(UnitPreference.Kilograms, state.units)
    }
}
