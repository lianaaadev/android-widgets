package com.liana.health.data

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.permission.HealthPermission
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import kotlin.coroutines.cancellation.CancellationException

/**
 * States 2 to 4 of the four-state machine in `health/plan.md`. State 1 — whether there is a
 * provider at all — is [HealthConnectAvailability]'s job and is answered before this one is asked.
 */
sealed interface HealthPermissionState {

    /** No read permission. Nothing can be read; the widget deep-links here. */
    data object NotGranted : HealthPermissionState

    /**
     * Read permission granted. [background] is a separate grant that lives in the system module
     * and, per [backgroundSupported], does not exist at all below Android 14 — without it the
     * widget only updates while the app is open, which the app says plainly rather than
     * appearing broken.
     */
    data class Granted(
        val background: Boolean,
        val backgroundSupported: Boolean,
    ) : HealthPermissionState
}

/**
 * The only thing in the app that touches [HealthConnectClient], mirroring how `OccasionRepository`
 * is the only thing in countdown that touches Room.
 *
 * Phase 1 exposes suspend functions. The cached `Flow<Snapshot?>` the widget renders from lands
 * in Phase 2 with the DataStore cache behind it — the widget must never call Health Connect on
 * its render path, because a read here is suspend, throws, and fails silently when the app is
 * backgrounded without the background grant.
 */
class HealthRepository(private val context: Context) {

    val metric: HealthMetric = WeightMetric

    private val cache = ReadingCache(context)

    /** What the widget renders from. Never touches Health Connect. */
    val cached: Flow<CachedState> = cache.state

    /**
     * Read permission always; background only because the manifest declares it. Requesting the
     * background grant alongside the read grant means one dialog rather than two, and Health
     * Connect shows the user exactly what is being asked for either way.
     */
    val requiredPermissions: Set<String> = setOf(
        WeightMetric.permission,
        HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND,
    )

    private val client: HealthConnectClient by lazy { HealthConnectClient.getOrCreate(context) }

    suspend fun permissionState(): Result<HealthPermissionState> = guarded {
        val granted = client.permissionController.getGrantedPermissions()

        if (WeightMetric.permission !in granted) {
            HealthPermissionState.NotGranted
        } else {
            HealthPermissionState.Granted(
                background = HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND in granted,
                backgroundSupported = client.features.getFeatureStatus(
                    HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_IN_BACKGROUND
                ) == HealthConnectFeatures.FEATURE_STATUS_AVAILABLE,
            )
        }
    }

    /** The current value and its 7-day comparison. Null means no readings in the visible window. */
    suspend fun read(now: Instant = Instant.now()): Result<Snapshot?> =
        guarded { metric.read(client, now) }

    /**
     * Read Health Connect and write the result to the cache the widget renders from.
     *
     * A failed read deliberately leaves the cached reading alone: the number stays on screen and
     * simply ages into [com.liana.health.widget.WidgetState.Stale], which is the whole reason
     * the cache exists. Only permission is written on failure, because losing it is the one
     * failure whose cause the widget can name and offer to fix.
     */
    suspend fun refresh(now: Instant = Instant.now()): Result<Snapshot?> {
        val permission = permissionState().getOrNull()
        val granted = permission is HealthPermissionState.Granted
        cache.putPermissionGranted(granted)

        if (!granted) return Result.success(null)

        return read(now).onSuccess { cache.putSnapshot(it, now) }
    }

    suspend fun setUnits(units: UnitPreference) = cache.putUnits(units)

    /** Every reading in the visible window, with the app that wrote each. For the debug screen. */
    suspend fun readWindow(now: Instant = Instant.now()): Result<List<SourcedReading>> =
        guarded { WeightMetric.readWindow(client, now) }

    /**
     * Health Connect throws for a dozen unrelated reasons — permission revoked mid-call, the
     * provider updating underneath us, quota exhaustion as `IllegalStateException`. Every one of
     * them has to become a value rather than a crash, because the widget renders from whatever
     * this returns.
     *
     * [CancellationException] is deliberately rethrown rather than captured: swallowing it would
     * break structured concurrency, leaving a cancelled scope believing it is still running.
     * This is the bug `runCatching` would have introduced for free.
     */
    private inline fun <T> guarded(block: () -> T): Result<T> =
        try {
            Result.success(block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
}
