package com.liana.countdown.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.getAppWidgetState
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
import com.liana.countdown.CountdownApp
import com.liana.countdown.data.Occasion
import com.liana.countdown.domain.Countdown
import com.liana.countdown.domain.CountdownState
import com.liana.countdown.ui.MainActivity
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * One widget class, reused for every instance and for both the home screen and the Flex Window.
 * What an instance shows comes from its own [WidgetPrefs.OccasionId].
 *
 * The layouts deliberately stay inside what Glance can render: solid backgrounds, rounded
 * corners, text and rows. No shadows, gradients, arcs or animation — Glance has none of them,
 * so the design gets its contrast from colour alone.
 */
class CountdownWidget : GlanceAppWidget() {

    override val stateDefinition = PreferencesGlanceStateDefinition

    override val sizeMode = SizeMode.Responsive(setOf(Small, Medium, Wide, Cover))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = (context.applicationContext as CountdownApp).repository
        val prefs = getAppWidgetState(context, PreferencesGlanceStateDefinition, id)
        val occasionId = prefs[WidgetPrefs.OccasionId]
        val occasion = occasionId?.let { repository.getById(it) }?.takeUnless { it.isDeleted }
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)

        // Resolved once per render, against the reader's own time zone.
        val state = occasion?.let {
            Countdown.stateFor(it.date, it.recurringYearly, LocalDate.now())
        }

        provideContent {
            if (occasion == null || state == null) {
                OrphanedWidget(appWidgetId)
            } else {
                CountdownWidgetContent(occasion, state)
            }
        }
    }

    companion object {
        val Small = DpSize(72.dp, 72.dp)
        val Medium = DpSize(168.dp, 168.dp)
        val Wide = DpSize(344.dp, 168.dp)

        /** Samsung's documented Flex Window size for the Galaxy Z Flip cover screen. */
        val Cover = DpSize(352.dp, 339.dp)
    }
}

/**
 * A widget is one tap target showing one number; without this a screen reader reads out "46".
 */
private fun GlanceModifier.semanticsDescription(text: String): GlanceModifier =
    semantics { contentDescription = text }

// --- palette ---------------------------------------------------------------------------------

private val SurfaceNight = Color(0xFF15151C)
private val Ink = Color(0xFF14141A)
private val TextNight = Color(0xFFF5F5F7)
private val MutedNight = Color(0xFF9C9CAB)
private val FaintNight = Color(0xFF63636F)

private val PastSurfaceDay = Color(0xFFE7E7EB)
private val PastSurfaceNight = Color(0xFF131319)
private val PastNumberDay = Color(0xFF9A9AA6)
private val PastNumberNight = Color(0xFF3A3A45)
private val PastLabelDay = Color(0xFF7C7C88)
private val PastLabelNight = Color(0xFF44444E)

/**
 * The block inverts. On a light home screen the widget is a solid slab of the occasion's colour
 * with dark type; on a dark one it is a dark card with the number in that colour. On the day
 * itself it inverts in both, which is what makes "today" unmissable at a glance.
 */
private fun surfaceFor(state: CountdownState, accent: Color): ColorProvider = when (state) {
    is CountdownState.Today -> DayNight(day = accent, night = accent)
    is CountdownState.Past -> DayNight(day = PastSurfaceDay, night = PastSurfaceNight)
    is CountdownState.Upcoming -> DayNight(day = accent, night = SurfaceNight)
}

private fun numberFor(state: CountdownState, accent: Color): ColorProvider = when (state) {
    is CountdownState.Today -> DayNight(day = Ink, night = Ink)
    is CountdownState.Past -> DayNight(day = PastNumberDay, night = PastNumberNight)
    is CountdownState.Upcoming -> DayNight(day = Ink, night = accent)
}

private fun titleFor(state: CountdownState): ColorProvider = when (state) {
    is CountdownState.Today -> DayNight(day = Ink, night = Ink)
    is CountdownState.Past -> DayNight(day = PastLabelDay, night = PastLabelNight)
    is CountdownState.Upcoming -> DayNight(day = Ink, night = MutedNight)
}

private fun footerFor(state: CountdownState): ColorProvider = when (state) {
    is CountdownState.Today -> DayNight(day = Ink, night = Ink)
    is CountdownState.Past -> DayNight(day = PastLabelDay, night = PastLabelNight)
    is CountdownState.Upcoming -> DayNight(day = Ink, night = FaintNight)
}

// --- content ---------------------------------------------------------------------------------

private fun numberText(state: CountdownState): String = when (state) {
    is CountdownState.Upcoming -> state.days.toString()
    is CountdownState.Today -> "TODAY"
    is CountdownState.Past -> state.daysAgo.toString()
}

private fun footerText(state: CountdownState): String {
    val short = DateTimeFormatter.ofPattern("d MMM")
    return when (state) {
        is CountdownState.Upcoming -> "days · ${state.target.format(short)}"
        is CountdownState.Today -> state.target.format(DateTimeFormatter.ofPattern("d MMMM"))
        is CountdownState.Past -> "days ago · ${state.target.format(short)}"
    }
}

/** What a screen reader announces, in place of a bare number. */
private fun spokenDescription(occasion: Occasion, state: CountdownState): String = when (state) {
    is CountdownState.Upcoming -> "${state.days} days until ${occasion.title}"
    is CountdownState.Today -> "${occasion.title} is today"
    is CountdownState.Past -> "${occasion.title} was ${state.daysAgo} days ago"
}

@Composable
private fun CountdownWidgetContent(occasion: Occasion, state: CountdownState) {
    val context = LocalContext.current
    val accent = Color(occasion.accentColor)
    val openApp = actionStartActivity(
        Intent(context, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_OCCASION_ID, occasion.id)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        },
    )

    val surface = GlanceModifier
        .fillMaxSize()
        .background(surfaceFor(state, accent))
        .cornerRadius(16.dp)
        .clickable(openApp)
        .semanticsDescription(spokenDescription(occasion, state))

    when (LocalSize.current) {
        CountdownWidget.Small -> SmallLayout(occasion, state, surface)
        CountdownWidget.Wide -> WideLayout(occasion, state, surface)
        CountdownWidget.Cover -> CoverLayout(occasion, state, surface)
        else -> MediumLayout(occasion, state, surface)
    }
}

@Composable
private fun SmallLayout(occasion: Occasion, state: CountdownState, surface: GlanceModifier) {
    Column(modifier = surface.padding(9.dp)) {
        Text(
            text = numberText(state),
            maxLines = 1,
            style = TextStyle(
                color = numberFor(state, Color(occasion.accentColor)),
                fontSize = if (state is CountdownState.Today) 15.sp else 28.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
        Spacer(GlanceModifier.defaultWeight())
        Text(
            text = occasion.title.take(6).uppercase(),
            maxLines = 1,
            style = TextStyle(
                color = titleFor(state),
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium,
            ),
        )
    }
}

@Composable
private fun MediumLayout(occasion: Occasion, state: CountdownState, surface: GlanceModifier) {
    Column(modifier = surface.padding(16.dp)) {
        Text(
            text = occasion.title.uppercase(),
            maxLines = 2,
            style = TextStyle(
                color = titleFor(state),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
            ),
        )
        Spacer(GlanceModifier.defaultWeight())
        Text(
            text = numberText(state),
            maxLines = 1,
            style = TextStyle(
                color = numberFor(state, Color(occasion.accentColor)),
                fontSize = if (state is CountdownState.Today) 34.sp else 56.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
        Spacer(GlanceModifier.defaultWeight())
        Text(
            text = footerText(state),
            maxLines = 1,
            style = TextStyle(color = footerFor(state), fontSize = 11.sp),
        )
    }
}

@Composable
private fun WideLayout(occasion: Occasion, state: CountdownState, surface: GlanceModifier) {
    Row(
        modifier = surface.padding(horizontal = 20.dp, vertical = 18.dp),
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        Text(
            text = numberText(state),
            maxLines = 1,
            style = TextStyle(
                color = numberFor(state, Color(occasion.accentColor)),
                fontSize = if (state is CountdownState.Today) 40.sp else 72.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
        Spacer(GlanceModifier.width(20.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = occasion.title,
                maxLines = 2,
                style = TextStyle(
                    color = titleFor(state),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
            Spacer(GlanceModifier.height(8.dp))
            Text(
                text = footerText(state),
                maxLines = 2,
                style = TextStyle(color = footerFor(state), fontSize = 12.sp),
            )
        }
    }
}

@Composable
private fun CoverLayout(occasion: Occasion, state: CountdownState, surface: GlanceModifier) {
    Column(modifier = surface.padding(28.dp)) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Vertical.CenterVertically) {
            Text(
                text = occasion.title.uppercase(),
                maxLines = 1,
                style = TextStyle(
                    color = titleFor(state),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                ),
                modifier = GlanceModifier.defaultWeight(),
            )
            occasion.emoji?.let {
                Text(text = it, style = TextStyle(fontSize = 20.sp))
            }
        }
        Spacer(GlanceModifier.defaultWeight())
        Text(
            text = numberText(state),
            maxLines = 1,
            style = TextStyle(
                color = numberFor(state, Color(occasion.accentColor)),
                fontSize = if (state is CountdownState.Today) 68.sp else 128.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
        Spacer(GlanceModifier.defaultWeight())
        Text(
            text = when (state) {
                is CountdownState.Upcoming -> "days until ${state.target.format(DateTimeFormatter.ofPattern("EEEE d MMMM"))}"
                is CountdownState.Today -> state.target.format(DateTimeFormatter.ofPattern("EEEE d MMMM"))
                is CountdownState.Past -> "days ago · ${state.target.format(DateTimeFormatter.ofPattern("d MMMM"))}"
            },
            maxLines = 1,
            style = TextStyle(color = footerFor(state), fontSize = 14.sp),
        )
    }
}

/**
 * The occasion behind this widget was deleted. The widget stays put and offers to be pointed at
 * something else, rather than going blank or disappearing.
 */
@Composable
private fun OrphanedWidget(appWidgetId: Int) {
    val context = LocalContext.current
    val reconfigure = actionStartActivity(
        Intent(context, CountdownWidgetConfigActivity::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        },
    )

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(DayNight(day = PastSurfaceDay, night = PastSurfaceNight))
            .cornerRadius(16.dp)
            .clickable(reconfigure)
            .padding(16.dp)
            .semanticsDescription("Occasion removed. Tap to choose another."),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Occasion removed\nTap to choose another",
            style = TextStyle(
                color = DayNight(day = PastLabelDay, night = MutedNight),
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
            ),
        )
    }
}
