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
import kotlinx.coroutines.flow.first
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
class ReadingCache(private val dataStore: DataStore<Preferences>) {

    /** The app's instance. The DataStore is injected so the round-trip is unit-testable. */
    constructor(context: Context) : this(context.healthDataStore)

    val state: Flow<CachedState> = dataStore.data.map { it.toCachedState() }

    /**
     * Clearing uses `-=` rather than [androidx.datastore.preferences.core.MutablePreferences.remove].
     *
     * `remove` is declared to return a non-null `T` but returns null when the key is absent, so
     * Kotlin trusts the signature and unboxes it — and any expression that ends on a `remove`
     * call becomes a NullPointerException the moment the key was never written. That shipped
     * once: an elvis branch ending in `remove(KeyPreviousAt)` crashed for anyone whose only
     * reading was inside the trend window, because that is exactly when the key is absent.
     * `minusAssign` returns Unit and has no such trap.
     */
    suspend fun putSnapshot(snapshot: Snapshot?, readAt: Instant) {
        dataStore.edit { prefs ->
            prefs[KeyLastReadAt] = readAt.toEpochMilli()

            if (snapshot == null) {
                // A successful read that found nothing is different from a failed read: it means
                // Health Connect genuinely holds no weight, so the stale number must go rather
                // than linger as a value we can no longer source.
                prefs -= KeyLatestValue
                prefs -= KeyLatestAt
                prefs -= KeyPreviousValue
                prefs -= KeyPreviousAt
                return@edit
            }

            prefs[KeyLatestValue] = snapshot.latest.value
            prefs[KeyLatestAt] = snapshot.latest.at.toEpochMilli()

            val previous = snapshot.previous
            if (previous == null) {
                prefs -= KeyPreviousValue
                prefs -= KeyPreviousAt
            } else {
                prefs[KeyPreviousValue] = previous.value
                prefs[KeyPreviousAt] = previous.at.toEpochMilli()
            }
        }
    }

    /**
     * Recorded separately from the readings so the widget can tell "permission was taken away"
     * from "there is nothing to show", which are different states with different copy and
     * different taps.
     */
    suspend fun putPermissionGranted(granted: Boolean) {
        dataStore.edit { it[KeyPermissionGranted] = granted }
    }

    /**
     * The app that wrote the most recent reading, so the empty state can name it.
     *
     * Kept because the plan guessed wrong: it assumed Samsung Health writes the weight, and hard
     * coded that into the copy. On a real phone the writer turned out to be a smart-scale app.
     * Telling someone to check a sync toggle in an app that is not involved is worse than saying
     * nothing, so the name comes from the data rather than from an assumption.
     */
    suspend fun putSource(packageName: String?) {
        dataStore.edit { prefs ->
            if (packageName == null) prefs -= KeySource else prefs[KeySource] = packageName
        }
    }

    /**
     * Health Connect's changes token. Persisted so a background run can ask "has anything
     * changed" instead of re-reading records it already has — the documented way to stay inside
     * a quota Google does not publish numbers for.
     */
    suspend fun putChangesToken(token: String?) {
        dataStore.edit { prefs ->
            if (token == null) prefs -= KeyChangesToken else prefs[KeyChangesToken] = token
        }
    }

    suspend fun changesToken(): String? = dataStore.data.first()[KeyChangesToken]

    suspend fun putUnits(units: UnitPreference) {
        dataStore.edit { it[KeyUnits] = units.name }
    }

    private fun Preferences.toCachedState(): CachedState {
        val latest = readingFrom(KeyLatestValue, KeyLatestAt)
        return CachedState(
            snapshot = latest?.let {
                Snapshot(latest = it, previous = readingFrom(KeyPreviousValue, KeyPreviousAt))
            },
            lastReadAt = this[KeyLastReadAt]?.let(Instant::ofEpochMilli),
            permissionGranted = this[KeyPermissionGranted] ?: false,
            sourcePackage = this[KeySource],
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

    internal companion object {
        val KeyLatestValue = doublePreferencesKey("latest_value")
        val KeyLatestAt = longPreferencesKey("latest_at")
        val KeyPreviousValue = doublePreferencesKey("previous_value")
        val KeyPreviousAt = longPreferencesKey("previous_at")
        val KeyLastReadAt = longPreferencesKey("last_read_at")
        val KeyPermissionGranted = booleanPreferencesKey("permission_granted")
        val KeyUnits = stringPreferencesKey("units")
        val KeySource = stringPreferencesKey("source_package")
        val KeyChangesToken = stringPreferencesKey("changes_token")
    }
}

/** Everything the widget renders from. It never calls Health Connect itself. */
data class CachedState(
    val snapshot: Snapshot? = null,
    val lastReadAt: Instant? = null,
    val permissionGranted: Boolean = false,
    val units: UnitPreference = UnitPreference.Default,
    /** Package name of whatever wrote [snapshot]'s latest reading, when we have seen one. */
    val sourcePackage: String? = null,
)
