package com.liana.health.ui

import com.liana.health.data.HealthConnectAvailability
import com.liana.health.data.HealthPermissionState
import com.liana.health.data.Snapshot
import com.liana.health.data.SourcedReading

/**
 * Everything the one screen needs, as one value. Phase 1's screen is a debug view, but the shape
 * is the shape the real screen wants too: an availability answer, a permission answer, and the
 * data — each of which can be absent for its own reason.
 */
data class MainState(
    val availability: HealthConnectAvailability? = null,
    val permission: HealthPermissionState? = null,
    val snapshot: Snapshot? = null,
    val records: List<SourcedReading> = emptyList(),
    val promptsExhausted: Boolean = false,
    /** Remembered across empty reads, so the empty state still has an app to name. */
    val sourcePackage: String? = null,
    /** Non-null when a read threw. Shown verbatim: this screen exists to diagnose. */
    val error: String? = null,
    val loading: Boolean = false,
)
