package com.liana.health.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.liana.health.HealthApp
import com.liana.health.data.CachedState
import com.liana.health.data.Reading
import com.liana.health.data.Snapshot
import com.liana.health.data.TrendDirection
import com.liana.health.data.UnitPreference
import com.liana.health.data.WeightMetric
import com.liana.widgets.core.design.AccentPalette
import com.liana.widgets.core.design.BorderSubtle
import com.liana.widgets.core.design.PrimaryButton
import com.liana.widgets.core.design.SecondaryButton
import com.liana.widgets.core.design.SurfaceCard
import com.liana.widgets.core.design.TextPrimary
import com.liana.widgets.core.design.TextSecondary
import com.liana.widgets.core.design.TextTertiary
import com.liana.widgets.core.design.WidgetTheme
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * Launched by the launcher when a weight widget is dropped, and again whenever the launcher
 * offers to reconfigure one.
 *
 * There is only one metric, so unlike countdown's picker this screen exists solely to choose a
 * colour. That is enough to justify it: several weight widgets showing the same number in
 * different accents is a real thing people do to match a wallpaper, and the colour is the only
 * property that can differ between two instances.
 */
class HealthWidgetConfigActivity : ComponentActivity() {

    private var appWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        // Backing out must leave no widget behind, so the cancelled result is set up front.
        setResult(RESULT_CANCELED, resultIntent())

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val repository = (application as HealthApp).repository

        setContent {
            WidgetTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    val cached by repository.cached.collectAsState(initial = CachedState())
                    AccentConfigScreen(
                        cached = cached,
                        onConfirm = ::bindAndFinish,
                        onCancel = { finish() },
                    )
                }
            }
        }
    }

    private fun bindAndFinish(accentColor: Int) {
        lifecycleScope.launch {
            bindWeightWidget(this@HealthWidgetConfigActivity, appWidgetId, accentColor)
            setResult(RESULT_OK, resultIntent())
            finish()
        }
    }

    private fun resultIntent() = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
}

@Composable
private fun AccentConfigScreen(
    cached: CachedState,
    onConfirm: (Int) -> Unit,
    onCancel: () -> Unit,
) {
    var accent by remember { mutableIntStateOf(WidgetPrefs.DefaultAccent) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .padding(top = 62.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(26.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Pick a colour",
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextPrimary,
                )
                Text(
                    text = "Each widget keeps its own. The weight is the same in all of them.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextTertiary,
                )
            }

            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                WidgetPreview(cached = cached, accent = Color(accent))
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "ACCENT",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextTertiary,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AccentPalette.all.forEach { swatch ->
                        Swatch(
                            color = Color(swatch),
                            selected = swatch == accent,
                            onClick = { accent = swatch },
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, bottom = 34.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SecondaryButton(label = "Cancel", onClick = onCancel, modifier = Modifier.weight(1f))
            PrimaryButton(
                label = "Add widget",
                color = Color(accent),
                onClick = { onConfirm(accent) },
                modifier = Modifier.weight(1.6f),
            )
        }
    }
}

@Composable
private fun Swatch(color: Color, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(46.dp)
            .clip(RoundedCornerShape(23.dp))
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) TextPrimary else BorderSubtle,
                shape = RoundedCornerShape(23.dp),
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(color),
        )
    }
}

/**
 * A still of the 2x2 widget in the chosen colour, drawn in Compose rather than Glance.
 *
 * It has to be a separate implementation — Glance composables cannot render inside an ordinary
 * activity — so it is deliberately kept to the few things that carry the colour: label, number,
 * trend, recency. Anything more would be a second layout to keep in sync with the real one.
 */
@Composable
private fun WidgetPreview(cached: CachedState, accent: Color) {
    val units = cached.units
    val snapshot = cached.snapshot
        // A placeholder only when there is genuinely nothing cached, so the preview shows the
        // user's own weight whenever we have it.
        ?: Snapshot(latest = Reading(72.4, Instant.now()), previous = null)
    val trend = WeightMetric.trend(snapshot, units)

    Column(
        modifier = Modifier
            .size(168.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceCard)
            .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp))
            .padding(16.dp),
    ) {
        Text(
            text = "WEIGHT",
            style = MaterialTheme.typography.labelMedium,
            color = TextSecondary,
        )
        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = units.format(snapshot.latest.value),
                style = MaterialTheme.typography.displayMedium,
                color = accent,
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = units.suffix.uppercase(),
                style = MaterialTheme.typography.bodySmall,
                color = TextTertiary,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        Spacer(Modifier.weight(1f))
        trend?.let {
            Text(
                text = when (it.direction) {
                    TrendDirection.Up -> "+${it.text}"
                    TrendDirection.Down -> "−${it.text}"
                    TrendDirection.Level -> it.text
                },
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
            Spacer(Modifier.height(3.dp))
        }
        Text(
            text = com.liana.health.data.Recency.describe(snapshot.latest.at, Instant.now()),
            style = MaterialTheme.typography.bodySmall,
            color = TextTertiary,
        )
    }
}
