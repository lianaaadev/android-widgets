package com.liana.health.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.liana.health.data.HealthConnectAvailability
import com.liana.health.data.HealthPermissionState
import com.liana.health.data.Recency
import com.liana.health.data.Snapshot
import com.liana.health.data.SourceApp
import com.liana.health.data.SourcedReading
import com.liana.health.data.TrendDirection
import com.liana.health.data.UnitPreference
import com.liana.health.data.WeightMetric
import com.liana.widgets.core.design.AccentPalette
import com.liana.widgets.core.design.BorderSubtle
import com.liana.widgets.core.design.PrimaryButton
import com.liana.widgets.core.design.SecondaryButton
import com.liana.widgets.core.design.SurfaceCard
import com.liana.widgets.core.design.TextFaint
import com.liana.widgets.core.design.TextPrimary
import com.liana.widgets.core.design.TextSecondary
import com.liana.widgets.core.design.TextTertiary
import java.time.Instant

private val Accent = Color(AccentPalette.Cyan)

/**
 * Phase 1's screen, and deliberately a diagnostic one. Its whole job is to answer the plan's
 * go/no-go question — does Samsung Health's weight actually reach Health Connect on this phone —
 * so it shows every record it can see and who wrote each, rather than the one polished number
 * the real screen will show.
 */
@Composable
fun MainScreen(
    state: MainState,
    onGrant: () -> Unit,
    onOpenSettings: () -> Unit,
    onUpdateProvider: () -> Unit,
    onRefresh: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(top = 62.dp, bottom = 34.dp),
        verticalArrangement = Arrangement.spacedBy(26.dp),
    ) {
        Text("HEALTH", style = MaterialTheme.typography.labelLarge, color = TextTertiary)

        when (state.availability) {
            null -> Unit

            HealthConnectAvailability.NotSupported -> Statement(
                headline = "This phone has no Health Connect.",
                body = "It needs Android 9 or later, and is not on every device. Without it " +
                    "there is no weight to read.",
            )

            HealthConnectAvailability.ProviderUpdateRequired -> {
                Statement(
                    headline = "Health Connect needs updating.",
                    body = "It is installed, but older than this app can talk to.",
                )
                PrimaryButton("Update Health Connect", onUpdateProvider, color = Accent)
            }

            HealthConnectAvailability.Available -> when (val permission = state.permission) {
                null -> Unit
                HealthPermissionState.NotGranted -> NotGranted(
                    promptsExhausted = state.promptsExhausted,
                    onGrant = onGrant,
                    onOpenSettings = onOpenSettings,
                )
                is HealthPermissionState.Granted -> Granted(
                    permission = permission,
                    state = state,
                    onRefresh = onRefresh,
                    onOpenSettings = onOpenSettings,
                )
            }
        }

        state.error?.let { ErrorPanel(it) }
    }
}

@Composable
private fun NotGranted(
    promptsExhausted: Boolean,
    onGrant: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Statement(
        headline = "One number, one permission.",
        body = "This app reads your weight from Health Connect and puts it on your home screen. " +
            "It does not ask for steps, sleep, heart rate or anything else behind the same door.",
    )

    if (promptsExhausted) {
        // Health Connect will not show the dialog again, so offering the button that triggers it
        // would be offering a button that does nothing.
        Statement(
            headline = "Health Connect will not ask again.",
            body = "It stops offering the dialog after two dismissals. The permission can still " +
                "be granted from inside Health Connect itself.",
        )
        PrimaryButton("Open Health Connect", onOpenSettings, color = Accent)
    } else {
        PrimaryButton("Grant permission", onGrant, color = Accent)
    }
}

@Composable
private fun Granted(
    permission: HealthPermissionState.Granted,
    state: MainState,
    onRefresh: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val now = Instant.now()

    if (state.snapshot != null) {
        LatestCard(state.snapshot, now)
    } else if (!state.loading) {
        // Names the app that last wrote a reading rather than assuming one. The plan assumed
        // Samsung Health and hard coded it here; on a real phone the writer was a smart-scale
        // app, so the instructions pointed somewhere that could not have helped.
        val source = SourceApp.label(state.sourcePackage)
        Statement(
            headline = "Health Connect has no weight.",
            body = if (source != null) {
                "Permission is granted, so this is almost certainly a sync switch rather than " +
                    "anything you did. Open $source and check it is still sharing weight with " +
                    "Health Connect."
            } else {
                "Permission is granted, so Health Connect genuinely holds nothing. Whichever " +
                    "app records your weight — a scale app, Samsung Health, Google Fit — needs " +
                    "to be sharing it with Health Connect, with Weight ticked."
            },
        )
    }

    if (!permission.backgroundSupported) {
        Note(
            "Background reads are not available on this Android version. The widget will only " +
                "update while this app is open."
        )
    } else if (!permission.background) {
        Note(
            "Background access is not granted, so the widget will only update while this app is " +
                "open. It can be turned on in Health Connect."
        )
    }

    if (state.records.isNotEmpty()) {
        Text(
            "IN HEALTH CONNECT · LAST 30 DAYS",
            style = MaterialTheme.typography.labelMedium,
            color = TextFaint,
        )
        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            state.records.forEach { RecordRow(it, now) }
        }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        SecondaryButton("Refresh", onRefresh, modifier = Modifier.weight(1f))
        SecondaryButton("Health Connect", onOpenSettings, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun LatestCard(snapshot: Snapshot, now: Instant) {
    val units = UnitPreference.Default
    val trend = WeightMetric.trend(snapshot, units)

    // Merged and described, or a screen reader announces "LATEST", "72.4", "KG", "down 0.3 kg
    // this week" as four unrelated fragments — the same reason the widget carries a description.
    val spoken = buildString {
        append("Latest weight ${WeightMetric.format(snapshot.latest, units)} ${units.suffix}, ")
        append(Recency.describe(snapshot.latest.at, now))
        trend?.let {
            append(
                when (it.direction) {
                    TrendDirection.Up -> ", up ${it.text}"
                    TrendDirection.Down -> ", down ${it.text}"
                    TrendDirection.Level -> ", ${it.text}"
                }
            )
        } ?: append(", no reading a week back to compare with")
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(SurfaceCard)
            .border(1.dp, BorderSubtle, RoundedCornerShape(6.dp))
            .padding(22.dp)
            .semantics(mergeDescendants = true) { contentDescription = spoken },
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("LATEST", style = MaterialTheme.typography.labelMedium, color = Accent)

        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = WeightMetric.format(snapshot.latest, units),
                style = MaterialTheme.typography.displayMedium,
                color = Accent,
            )
            Text(
                text = units.suffix.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                // The direction is a word here rather than the widget's arrow, and it is the
                // same neutral wording either way — the app takes no view on which way is good.
                text = trend?.let {
                    when (it.direction) {
                        TrendDirection.Up -> "up ${it.text}"
                        TrendDirection.Down -> "down ${it.text}"
                        TrendDirection.Level -> it.text
                    }
                } ?: "no reading a week back",
                style = MaterialTheme.typography.bodySmall,
                color = if (trend == null) TextTertiary else TextSecondary,
            )
            Text(
                text = "${Recency.formatDate(snapshot.latest.at)} · " +
                    Recency.describe(snapshot.latest.at, now),
                style = MaterialTheme.typography.bodySmall,
                color = TextTertiary,
            )
        }
    }
}

@Composable
private fun RecordRow(record: SourcedReading, now: Instant) {
    val units = UnitPreference.Default
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = Recency.formatDate(record.reading.at),
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
            )
            Text(
                // The label when we know it, but always falling back to the raw package: this
                // screen exists to tell you exactly which app wrote a record, and the package is
                // the only answer that cannot be wrong.
                text = SourceApp.label(record.sourcePackage) ?: record.sourcePackage,
                style = MaterialTheme.typography.bodySmall,
                color = TextTertiary,
            )
        }
        Text(
            text = "${units.format(record.reading.value)} ${units.suffix}",
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary,
        )
    }
}

@Composable
private fun Statement(headline: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(headline, style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
    }
}

@Composable
private fun Note(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .border(1.dp, BorderSubtle, RoundedCornerShape(6.dp))
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Box(
            modifier = Modifier
                .padding(top = 5.dp)
                .size(7.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(AccentPalette.Amber)),
        )
        Text(text, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
    }
}

@Composable
private fun ErrorPanel(message: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .border(1.dp, Color(AccentPalette.Coral), RoundedCornerShape(6.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("READ FAILED", style = MaterialTheme.typography.labelMedium, color = Color(AccentPalette.Coral))
        // Verbatim, including the exception class. A Phase 1 diagnostic screen that paraphrases
        // the error is worse than useless.
        Text(message, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
    }
}
