package com.liana.health.data

import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Duration
import java.time.Instant
import kotlin.math.abs

/**
 * Weight: the easiest of Health Connect's four record shapes, and the only metric in v1.
 *
 * Instantaneous, so it reads with [HealthConnectClient.readRecords] rather than `aggregate()`.
 * Two apps writing overlapping weight is not the double-counting hazard it is for steps — a
 * duplicate weight reading is still that weight, where duplicate step counts add up wrongly.
 */
object WeightMetric : HealthMetric {

    override val id: String = "weight"
    override val label: String = "Weight"
    override val permission: String = HealthPermission.getReadPermission(WeightRecord::class)

    /** How far back the trend compares. See [snapshotFrom] for why it is anchored on now. */
    val TrendWindow: Duration = Duration.ofDays(7)

    /**
     * The whole window we are allowed to see without READ_HEALTH_DATA_HISTORY. Reading all of it
     * costs one call and gives the debug screen something to show, which is the point of Phase 1.
     */
    val ReadWindow: Duration = Duration.ofDays(30)

    override suspend fun read(client: HealthConnectClient, now: Instant): Snapshot? =
        snapshotFrom(readWindow(client, now), now)

    /**
     * Every reading in the visible window, newest first. Exposed separately from [read] because
     * the debug screen needs the raw list — including who wrote each record, which is the single
     * most useful thing to see when the widget looks wrong.
     */
    suspend fun readWindow(client: HealthConnectClient, now: Instant): List<SourcedReading> =
        client.readRecords(
            ReadRecordsRequest(
                recordType = WeightRecord::class,
                timeRangeFilter = TimeRangeFilter.between(now.minus(ReadWindow), now),
                ascendingOrder = false,
            )
        ).records.map { record ->
            SourcedReading(
                reading = Reading(value = record.weight.inKilograms, at = record.time),
                sourcePackage = record.metadata.dataOrigin.packageName,
            )
        }

    override fun format(reading: Reading, units: UnitPreference): String =
        units.format(reading.value)

    override fun trend(snapshot: Snapshot, units: UnitPreference): Trend? {
        val previous = snapshot.previous ?: return null

        val delta = units.fromKilograms(snapshot.latest.value) -
            units.fromKilograms(previous.value)

        // Rounded before it is classified, not after. A 0.04 kg difference formats as "0.0", and
        // an arrow next to "0.0 kg this week" is noise pretending to be information.
        val magnitude = String.format(java.util.Locale.US, "%.1f", abs(delta))
        val direction = when {
            magnitude == "0.0" -> TrendDirection.Level
            delta > 0 -> TrendDirection.Up
            else -> TrendDirection.Down
        }

        return Trend(
            direction = direction,
            text = if (direction == TrendDirection.Level) {
                "level this week"
            } else {
                "$magnitude ${units.suffix} this week"
            },
        )
    }

    /**
     * Picks the latest reading and the one the trend compares it against.
     *
     * The comparison point is the newest reading at or before `now - 7 days` — anchored on now
     * rather than on the latest reading's own timestamp, because the widget's label says "this
     * week" and that is the user's week, not the reading's. It also degrades the way the design
     * wants: stop weighing yourself for long enough and the target slides past every record, the
     * trend disappears, and the widget goes stale rather than quietly comparing two ancient
     * numbers and calling the result a week.
     *
     * Returns no trend rather than a short one when nothing is old enough. A delta over three
     * days labelled "this week" would be worse than no delta at all.
     */
    internal fun snapshotFrom(readings: List<SourcedReading>, now: Instant): Snapshot? {
        val ordered = readings.map { it.reading }.sortedByDescending { it.at }
        val latest = ordered.firstOrNull() ?: return null
        val target = now.minus(TrendWindow)

        val previous = ordered.firstOrNull { it.at <= target && it.at < latest.at }
        return Snapshot(latest = latest, previous = previous)
    }
}

/** A reading plus the app that wrote it. Samsung Health and a Galaxy Watch are different origins. */
data class SourcedReading(val reading: Reading, val sourcePackage: String)
