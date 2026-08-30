package com.liana.health.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant

private val Context.healthDataStore: DataStore<Preferences> by preferencesDataStore("health_cache")

/**
 * The last thing we successfully read, plus the settings that decide how it is displayed.
 *
 * This is the whole persistence story for the app. Countdown needs Room because the user authors
 * its data; here Health Connect owns every value, so duplicating it into a database would buy a
 * second source of truth to keep in sync and nothing else. What the widget actually needs is
 * something to draw when a background read fails or permission is revoked — which is a handful
 * of values, not a schema.
 *
 * The trend being a delta rather than a chart is what keeps it that way: two readings, not a
 * history. See `health/plan.md`, "No Room, and why".
 */
class ReadingCache(private val context: Context) {

    val state: Flow<CachedState> = context.healthDataStore.data.map { it.toCachedState() }

    suspend fun putSnapshot(snapshot: Snapshot?, readAt: Instant) {
        context.healthDataStore.edit { prefs ->
            prefs[KeyLastReadAt] = readAt.toEpochMilli()

            if (snapshot == null) {
                // A successful read that found nothing is different from a failed read: it means
                // Health Connect genuinely holds no weight, so the stale number must go rather
                // than linger as a value we can no longer source.
                prefs.remove(KeyLatestValue)
                prefs.remove(KeyLatestAt)
                prefs.remove(KeyPreviousValue)
                prefs.remove(KeyPreviousAt)
            } else {
                prefs[KeyLatestValue] = snapshot.latest.value
                prefs[KeyLatestAt] = snapshot.latest.at.toEpochMilli()
                snapshot.previous?.let {
                    prefs[KeyPreviousValue] = it.value
                    prefs[KeyPreviousAt] = it.at.toEpochMilli()
                } ?: run {
                    prefs.remove(KeyPreviousValue)
                    prefs.remove(KeyPreviousAt)
                }
            }
        }
    }

    /**
     * Recorded separately from the readings so the widget can tell "permission was taken away"
     * from "there is nothing to show", which are different states with different copy and
     * different taps.
     */
    suspend fun putPermissionGranted(granted: Boolean) {
        context.healthDataStore.edit { it[KeyPermissionGranted] = granted }
    }

    suspend fun putUnits(units: UnitPreference) {
        context.healthDataStore.edit { it[KeyUnits] = units.name }
    }

    private fun Preferences.toCachedState(): CachedState {
        val latest = readingFrom(KeyLatestValue, KeyLatestAt)
        return CachedState(
            snapshot = latest?.let {
                Snapshot(latest = it, previous = readingFrom(KeyPreviousValue, KeyPreviousAt))
            },
            lastReadAt = this[KeyLastReadAt]?.let(Instant::ofEpochMilli),
            permissionGranted = this[KeyPermissionGranted] ?: false,
            units = this[KeyUnits]
                ?.let { name -> UnitPreference.entries.firstOrNull { it.name == name } }
                ?: UnitPreference.Default,
        )
    }

    private fun Preferences.readingFrom(
        value: Preferences.Key<Double>,
        at: Preferences.Key<Long>,
    ): Reading? {
        val v = this[value] ?: return null
        val t = this[at] ?: return null
        return Reading(value = v, at = Instant.ofEpochMilli(t))
    }

    private companion object {
        val KeyLatestValue = doublePreferencesKey("latest_value")
        val KeyLatestAt = longPreferencesKey("latest_at")
        val KeyPreviousValue = doublePreferencesKey("previous_value")
        val KeyPreviousAt = longPreferencesKey("previous_at")
        val KeyLastReadAt = longPreferencesKey("last_read_at")
        val KeyPermissionGranted = booleanPreferencesKey("permission_granted")
        val KeyUnits = stringPreferencesKey("units")
    }
}

/** Everything the widget renders from. It never calls Health Connect itself. */
data class CachedState(
    val snapshot: Snapshot? = null,
    val lastReadAt: Instant? = null,
    val permissionGranted: Boolean = false,
    val units: UnitPreference = UnitPreference.Default,
)
