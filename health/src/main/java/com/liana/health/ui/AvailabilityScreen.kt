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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.liana.health.data.HealthConnectAvailability
import com.liana.widgets.core.design.AccentPalette
import com.liana.widgets.core.design.BorderSubtle
import com.liana.widgets.core.design.PrimaryButton
import com.liana.widgets.core.design.SurfaceCard
import com.liana.widgets.core.design.TextPrimary
import com.liana.widgets.core.design.TextSecondary
import com.liana.widgets.core.design.TextTertiary

/** The app's accent. Countdown carries one colour per occasion; here there is one metric. */
private val Accent = Color(AccentPalette.Cyan)

/**
 * Phase 0's screen: the answer to "is there a Health Connect on this phone", and nothing else.
 * Phase 1 replaces the [HealthConnectAvailability.Available] branch with the real read path;
 * the other two branches survive as-is, because they are terminal states rather than steps.
 */
@Composable
fun AvailabilityScreen(
    availability: HealthConnectAvailability?,
    onUpdateProvider: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .padding(top = 62.dp, bottom = 34.dp),
    ) {
        Text(
            text = "HEALTH",
            style = MaterialTheme.typography.labelLarge,
            color = TextTertiary,
        )

        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            // Null means onResume has not run yet. It is a single synchronous call, so this is
            // one frame at most — showing a spinner for it would flash more than it informed.
            when (availability) {
                null -> Unit
                HealthConnectAvailability.Available -> AvailableState()
                HealthConnectAvailability.ProviderUpdateRequired -> ProviderUpdateState()
                HealthConnectAvailability.NotSupported -> NotSupportedState()
            }
        }

        if (availability == HealthConnectAvailability.ProviderUpdateRequired) {
            PrimaryButton(
                label = "Update Health Connect",
                onClick = onUpdateProvider,
                color = Accent,
            )
        }
    }
}

@Composable
private fun AvailableState() {
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Statement(
            headline = "Health Connect is ready.",
            body = "The provider is installed and current. Nothing is being read yet — the " +
                "permission flow and the weight read land in Phase 1.",
        )
        StatusRow(label = "Provider", value = "Installed", ok = true)
    }
}

@Composable
private fun ProviderUpdateState() {
    Statement(
        headline = "Health Connect needs updating.",
        body = "It is installed, but older than this app can talk to. The Play Store will " +
            "offer the update.",
    )
}

@Composable
private fun NotSupportedState() {
    Statement(
        headline = "This phone has no Health Connect.",
        body = "Health Connect needs Android 9 or later, and is not available on every device. " +
            "Without it there is no weight to read and nothing this app can usefully show.",
    )
}

@Composable
private fun Statement(headline: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            text = headline,
            style = MaterialTheme.typography.headlineMedium,
            color = TextPrimary,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            textAlign = TextAlign.Start,
        )
    }
}

@Composable
private fun StatusRow(label: String, value: String, ok: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(SurfaceCard)
            .border(1.dp, BorderSubtle, RoundedCornerShape(6.dp))
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (ok) Color(AccentPalette.Lime) else TextTertiary),
            )
            Text(text = value, style = MaterialTheme.typography.bodySmall, color = TextPrimary)
        }
    }
}
