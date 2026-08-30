package com.liana.health.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider as DayNight
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.liana.health.HealthApp
import com.liana.health.data.CachedState
import com.liana.health.data.HealthConnectAvailability
import com.liana.health.data.TrendDirection
import com.liana.health.data.UnitPreference
import com.liana.health.data.WeightMetric
import com.liana.health.ui.MainActivity
import com.liana.widgets.core.design.AccentPalette
import com.liana.widgets.core.design.Ink
import com.liana.widgets.core.design.SurfaceCard
import com.liana.widgets.core.design.TextSecondary
import com.liana.widgets.core.design.TextTertiary
import com.liana.widgets.core.widget.WidgetSizes
import java.time.Instant

/**
 * One widget class for the home screen and the Flex Window alike; only the manifest declaration
 * differs between them.
 *
 * Everything here renders from [com.liana.health.data.ReadingCache] and never calls Health
 * Connect. That is not an optimisation — a Health Connect read is suspend, throws, and fails
 * silently when the app is backgrounded without the background grant, so a render path that
 * depended on it would produce blank widgets at exactly the moments people look at them.
 *
 * The layouts stay inside what Glance can draw: solid fills, rounded corners, text and rows. No
 * shadows, gradients, arcs, lines or animation — which is also why the trend is a delta and an
 * arrow rather than a sparkline.
 */
class WeightWidget : GlanceAppWidget() {

    override val stateDefinition = PreferencesGlanceStateDefinition

    override val sizeMode =
        SizeMode.Responsive(setOf(WidgetSizes.Medium, WidgetSizes.Wide, WidgetSizes.Cover))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as HealthApp

        // Cheap and local: a package lookup, not a Health Connect call. Read once per session
        // rather than cached in DataStore, because the provider can be installed or updated
        // while a widget sits on the home screen.
        val availability = HealthConnectAvailability.of(context)

        provideContent {
            val cached by app.repository.cached.collectAsState(initial = null)

            // Null means the first DataStore emission has not landed. Distinguished from an
            // empty cache so the widget does not flash "no weight yet" for a frame on every
            // recomposition.
            when (val state = cached) {
                null -> Placeholder()
                else -> WeightWidgetContent(availability, state)
            }
        }
    }
}

@Composable
private fun WeightWidgetContent(availability: HealthConnectAvailability, cached: CachedState) {
    val context = LocalContext.current
    val units = cached.units

    val state = widgetStateOf(
        availability = availability,
        cached = cached,
        now = Instant.now(),
        trend = { WeightMetric.trend(it, units) },
    )

    val openApp = actionStartActivity(
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        },
    )

    val surface = GlanceModifier
        .fillMaxSize()
        .background(surfaceFor(state))
        .cornerRadius(16.dp)
        .clickable(openApp)
        .semantics { contentDescription = spokenDescription(state, units) }

    when (state) {
        is WidgetState.Ready, is WidgetState.Stale -> when (LocalSize.current) {
            WidgetSizes.Wide -> WideLayout(state, units, surface)
            WidgetSizes.Cover -> CoverLayout(state, units, surface)
            else -> MediumLayout(state, units, surface)
        }
        WidgetState.NeedsPermission -> MessageLayout(
            "Weight permission\nnot granted",
            "Tap to fix",
            surface,
        )
        is WidgetState.Unavailable -> when (state.reason) {
            WidgetState.Unavailable.Reason.NoProvider -> MessageLayout(
                "Health Connect\nunavailable",
                "Tap for detail",
                surface,
            )
            // Names the likely cause rather than shrugging. On a Samsung phone with permission
            // granted, an empty Health Connect is overwhelmingly Samsung Health's sync switch —
            // and that is invisible from here, indistinguishable from someone who has never
            // weighed themselves.
            WidgetState.Unavailable.Reason.NoData -> MessageLayout(
                "No weight yet",
                "Check Samsung\nHealth sync",
                surface,
            )
        }
    }
}

// --- palette ---------------------------------------------------------------------------------

private val Accent = Color(AccentPalette.Cyan)

private val StaleSurfaceDay = Color(0xFFE7E7EB)
private val StaleSurfaceNight = Color(0xFF131319)
private val StaleNumberDay = Color(0xFF9A9AA6)
private val StaleNumberNight = Color(0xFF3A3A45)
private val StaleLabelDay = Color(0xFF7C7C88)
private val StaleLabelNight = Color(0xFF44444E)

/**
 * The block inverts, as countdown's does: a solid slab of accent on a light home screen, a dark
 * card with an accent number on a dark one.
 */
private fun surfaceFor(state: WidgetState): ColorProvider = when (state) {
    is WidgetState.Ready -> DayNight(day = Accent, night = SurfaceCard)
    is WidgetState.Stale -> DayNight(day = StaleSurfaceDay, night = StaleSurfaceNight)
    else -> DayNight(day = StaleSurfaceDay, night = StaleSurfaceNight)
}

private fun numberFor(state: WidgetState): ColorProvider = when (state) {
    is WidgetState.Ready -> DayNight(day = Ink, night = Accent)
    else -> DayNight(day = StaleNumberDay, night = StaleNumberNight)
}

private fun labelFor(state: WidgetState): ColorProvider = when (state) {
    is WidgetState.Ready -> DayNight(day = Ink, night = TextSecondary)
    else -> DayNight(day = StaleLabelDay, night = StaleLabelNight)
}

private fun footerFor(state: WidgetState): ColorProvider = when (state) {
    is WidgetState.Ready -> DayNight(day = Ink, night = TextTertiary)
    else -> DayNight(day = StaleLabelDay, night = StaleLabelNight)
}

// --- content ---------------------------------------------------------------------------------

private fun numeralText(state: WidgetState, units: UnitPreference): String = when (state) {
    is WidgetState.Ready -> units.format(state.reading.value)
    is WidgetState.Stale -> units.format(state.reading.value)
    else -> ""
}

/**
 * The trend line, or the recording date when there is no trend to show. Stale never shows one:
 * see [WidgetState.Stale].
 */
private fun trendText(state: WidgetState): String? = when (state) {
    is WidgetState.Ready -> state.trend?.let {
        when (it.direction) {
            TrendDirection.Up -> "+${it.text}"
            TrendDirection.Down -> "−${it.text}"
            TrendDirection.Level -> it.text
        }
    }
    else -> null
}

private fun footerText(state: WidgetState): String = when (state) {
    is WidgetState.Ready -> state.recency
    is WidgetState.Stale -> "${state.date} · ${state.recency}"
    else -> ""
}

/** What a screen reader announces. A bare number would be read out as a bare number. */
private fun spokenDescription(state: WidgetState, units: UnitPreference): String = when (state) {
    is WidgetState.Ready -> buildString {
        append("Weight ${units.format(state.reading.value)} ${units.suffix}, ${state.recency}")
        state.trend?.let { trend ->
            append(
                when (trend.direction) {
                    TrendDirection.Up -> ", up ${trend.text}"
                    TrendDirection.Down -> ", down ${trend.text}"
                    TrendDirection.Level -> ", ${trend.text}"
                }
            )
        }
    }
    is WidgetState.Stale -> "Weight ${units.format(state.reading.value)} ${units.suffix}, " +
        "last recorded ${state.recency}, not refreshed since"
    WidgetState.NeedsPermission -> "Weight permission not granted. Tap to fix."
    is WidgetState.Unavailable -> when (state.reason) {
        WidgetState.Unavailable.Reason.NoProvider -> "Health Connect unavailable. Tap for detail."
        WidgetState.Unavailable.Reason.NoData ->
            "No weight in Health Connect. Check Samsung Health sync."
    }
}

@Composable
private fun MediumLayout(state: WidgetState, units: UnitPreference, surface: GlanceModifier) {
    val numeral = numeralText(state, units)
    Column(modifier = surface.padding(16.dp)) {
        Text(
            text = "WEIGHT",
            maxLines = 1,
            style = TextStyle(color = labelFor(state), fontSize = 13.sp, fontWeight = FontWeight.Bold),
        )
        Spacer(GlanceModifier.defaultWeight())
        Row(verticalAlignment = Alignment.Vertical.Bottom) {
            Text(
                text = numeral,
                maxLines = 1,
                style = TextStyle(
                    color = numberFor(state),
                    fontSize = numeralSp(numeral, base = 46f).sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
            Spacer(GlanceModifier.width(4.dp))
            Text(
                text = units.suffix.uppercase(),
                style = TextStyle(color = footerFor(state), fontSize = 10.sp, fontWeight = FontWeight.Bold),
                modifier = GlanceModifier.padding(bottom = 6.dp),
            )
        }
        Spacer(GlanceModifier.defaultWeight())
        trendText(state)?.let {
            Text(text = it, maxLines = 1, style = TextStyle(color = labelFor(state), fontSize = 11.sp))
            Spacer(GlanceModifier.height(3.dp))
        }
        Text(text = footerText(state), maxLines = 1, style = TextStyle(color = footerFor(state), fontSize = 10.sp))
    }
}

@Composable
private fun WideLayout(state: WidgetState, units: UnitPreference, surface: GlanceModifier) {
    val numeral = numeralText(state, units)
    Row(
        modifier = surface.padding(horizontal = 20.dp, vertical = 18.dp),
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.Vertical.Bottom) {
            Text(
                text = numeral,
                maxLines = 1,
                style = TextStyle(
                    color = numberFor(state),
                    fontSize = numeralSp(numeral, base = 62f).sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
            Spacer(GlanceModifier.width(5.dp))
            Text(
                text = units.suffix.uppercase(),
                style = TextStyle(color = footerFor(state), fontSize = 11.sp, fontWeight = FontWeight.Bold),
                modifier = GlanceModifier.padding(bottom = 8.dp),
            )
        }
        Spacer(GlanceModifier.width(20.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = "Weight",
                maxLines = 1,
                style = TextStyle(color = labelFor(state), fontSize = 20.sp, fontWeight = FontWeight.Bold),
            )
            Spacer(GlanceModifier.height(8.dp))
            trendText(state)?.let {
                Text(text = it, maxLines = 1, style = TextStyle(color = labelFor(state), fontSize = 12.sp))
                Spacer(GlanceModifier.height(4.dp))
            }
            Text(
                text = "measured ${footerText(state)}",
                maxLines = 2,
                style = TextStyle(color = footerFor(state), fontSize = 12.sp),
            )
        }
    }
}

@Composable
private fun CoverLayout(state: WidgetState, units: UnitPreference, surface: GlanceModifier) {
    val numeral = numeralText(state, units)
    Column(modifier = surface.padding(28.dp)) {
        Text(
            text = "WEIGHT",
            maxLines = 1,
            style = TextStyle(color = labelFor(state), fontSize = 24.sp, fontWeight = FontWeight.Bold),
        )
        Spacer(GlanceModifier.defaultWeight())
        Row(verticalAlignment = Alignment.Vertical.Bottom) {
            Text(
                text = numeral,
                maxLines = 1,
                style = TextStyle(
                    color = numberFor(state),
                    // The size that made this necessary: 138sp fits "72.4" and overflows "159.6".
                    fontSize = numeralSp(numeral, base = 116f).sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
            Spacer(GlanceModifier.width(10.dp))
            Text(
                text = units.suffix.uppercase(),
                style = TextStyle(color = footerFor(state), fontSize = 15.sp, fontWeight = FontWeight.Bold),
                modifier = GlanceModifier.padding(bottom = 14.dp),
            )
        }
        Spacer(GlanceModifier.defaultWeight())
        trendText(state)?.let {
            Text(text = it, maxLines = 1, style = TextStyle(color = labelFor(state), fontSize = 15.sp))
            Spacer(GlanceModifier.height(6.dp))
        }
        Text(
            text = "measured ${footerText(state)}",
            maxLines = 1,
            style = TextStyle(color = footerFor(state), fontSize = 14.sp),
        )
    }
}

@Composable
private fun MessageLayout(headline: String, detail: String, surface: GlanceModifier) {
    Column(
        modifier = surface.padding(16.dp),
        horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        Text(
            text = headline,
            style = TextStyle(
                color = DayNight(day = StaleLabelDay, night = TextSecondary),
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
            ),
        )
        Spacer(GlanceModifier.height(6.dp))
        Text(
            text = detail,
            style = TextStyle(
                color = DayNight(day = StaleLabelDay, night = StaleLabelNight),
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
            ),
        )
    }
}

/** Shown for the frame before the cache arrives; deliberately says nothing. */
@Composable
private fun Placeholder() {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(DayNight(day = StaleSurfaceDay, night = SurfaceCard))
            .cornerRadius(16.dp)
            .fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {}
}
