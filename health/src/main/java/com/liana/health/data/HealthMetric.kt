package com.liana.health.data

import androidx.health.connect.client.HealthConnectClient
import java.time.Instant

/**
 * One thing a widget can display. Register an object per metric; the widget never branches on
 * which one it has.
 *
 * v1 has exactly one implementation. The gate that proves this interface is Phase 6, where a
 * metric of a genuinely different shape gets added — until that runs, assume it is wrong
 * somewhere.
 */
interface HealthMetric {

    /** Stable, persisted in per-widget state. Never rename — it outlives the metric list. */
    val id: String

    val label: String

    /** e.g. android.permission.health.READ_WEIGHT. One permission, requested only when asked for. */
    val permission: String

    /**
     * Both readings in one call. Health Connect's four record shapes fetch their comparison point
     * differently — weight takes one windowed read and picks two records out of it, where a
     * cumulative metric would aggregate two separate windows — so the choice belongs in here
     * rather than in a second method the caller has to know when to use.
     */
    suspend fun read(client: HealthConnectClient, now: Instant): Snapshot?

    fun format(reading: Reading, units: UnitPreference): String

    /** Null when there is nothing to compare against, or the delta is not meaningful. */
    fun trend(snapshot: Snapshot, units: UnitPreference): Trend?
}

/**
 * [at] is when the value was recorded, not when we read it. The widget always shows that
 * difference: a Galaxy Watch can sync on Samsung's own schedule, so "now" is never implied.
 *
 * [value] is in the metric's own canonical unit — kilograms, for weight.
 */
data class Reading(val value: Double, val at: Instant)

/** [previous] is the trend's comparison point, and is null when there is no usable one. */
data class Snapshot(val latest: Reading, val previous: Reading?)

enum class TrendDirection { Up, Down, Level }

/**
 * Direction is kept separate from the text so the widget can pick an arrow for it. Both arrows
 * are drawn in the accent colour — never green for down and red for up. The app does not know
 * whether you are trying to lose, gain or hold, and colouring one direction as good news would
 * be a claim about your health it has no basis for making.
 */
data class Trend(val direction: TrendDirection, val text: String)
