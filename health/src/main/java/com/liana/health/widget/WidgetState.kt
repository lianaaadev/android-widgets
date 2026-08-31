package com.liana.health.widget

import com.liana.health.data.CachedState
import com.liana.health.data.HealthConnectAvailability
import com.liana.health.data.Reading
import com.liana.health.data.SourceApp
import com.liana.health.data.Recency
import com.liana.health.data.Snapshot
import com.liana.health.data.Trend
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/**
 * The four states from `health/plan.md`, decided once, in one pure function, so the three
 * layouts do not each re-derive them and drift apart.
 */
sealed interface WidgetState {

    /** A number worth trusting, with its age and — when there is one — its 7-day delta. */
    data class Ready(
        val reading: Reading,
        val trend: Trend?,
        val recency: String,
    ) : WidgetState

    /**
     * A cached number that has stopped being refreshed, or one nobody has replaced in a long
     * time. Rendered greyed with its recording date, and deliberately without the delta: a trend
     * computed against a value we have not been able to refresh is a claim we cannot stand
     * behind. Never a blank widget.
     */
    data class Stale(
        val reading: Reading,
        val recency: String,
        val date: String,
    ) : WidgetState

    /** Read permission is gone. Tapping opens the app at the permission step. */
    data object NeedsPermission : WidgetState

    /**
     * No provider, or a provider holding no weight at all. The two get different copy, and the
     * empty one names the app that last wrote a reading rather than guessing at one.
     */
    data class Unavailable(val reason: Reason, val source: String? = null) : WidgetState {
        enum class Reason { NoProvider, NoData }
    }
}

/**
 * How old a reading may get before the widget stops presenting it as current.
 *
 * A fortnight is a guess, and the plan records it as an open question. Weight is a metric people
 * genuinely stop recording for two weeks without anything being wrong, so too eager a threshold
 * makes a working widget look broken, and too patient a one quietly shows a month-old number as
 * today's. Worth revisiting in Phase 5 against real data rather than by reasoning.
 */
val StaleAfter: Duration = Duration.ofDays(14)

/**
 * How long a *successful read* may be stale before the number stops being presented as current,
 * regardless of how recent the reading itself is.
 *
 * These are different failures. A reading can be from this morning while every refresh since has
 * quietly failed — a revoked background grant, an exhausted quota, a provider mid-update — and
 * without this the widget would keep showing this morning's number as though it were confirmed.
 *
 * Generous on purpose: with no background grant the only refreshes are app opens, and someone who
 * opens the app twice a week should not see a permanently greyed widget. Two days is long enough
 * to survive normal neglect and short enough that a genuinely broken refresh shows.
 */
val UnconfirmedAfter: Duration = Duration.ofDays(2)

/**
 * Everything the widget shows, derived from the cache plus one synchronous availability check.
 *
 * Deliberately takes plain values rather than a Context: this is the logic worth testing, and it
 * should be testable without an Android runtime.
 */
fun widgetStateOf(
    availability: HealthConnectAvailability,
    cached: CachedState,
    now: Instant,
    trend: (Snapshot) -> Trend?,
    zone: ZoneId = ZoneId.systemDefault(),
): WidgetState {
    if (availability != HealthConnectAvailability.Available) {
        return WidgetState.Unavailable(WidgetState.Unavailable.Reason.NoProvider)
    }

    // Permission is checked before emptiness on purpose. Without permission we cannot know
    // whether there is data, so "no weight yet" would be a guess — and the wrong one, since the
    // fix is a tap here rather than a trip into Samsung Health's settings.
    if (!cached.permissionGranted) return WidgetState.NeedsPermission

    val snapshot = cached.snapshot
        ?: return WidgetState.Unavailable(
            reason = WidgetState.Unavailable.Reason.NoData,
            source = SourceApp.label(cached.sourcePackage),
        )

    val age = Duration.between(snapshot.latest.at, now)
    // A read that has never happened counts as unconfirmed: there is a cached number, but
    // nothing has verified it. Expressed as a boolean rather than a fallback duration, which
    // put the never-read case exactly *on* the threshold instead of past it.
    val unconfirmed = cached.lastReadAt?.let { Duration.between(it, now) > UnconfirmedAfter } ?: true
    return if (age > StaleAfter || unconfirmed) {
        WidgetState.Stale(
            reading = snapshot.latest,
            recency = Recency.describe(snapshot.latest.at, now, zone),
            date = Recency.formatDate(snapshot.latest.at, zone),
        )
    } else {
        WidgetState.Ready(
            reading = snapshot.latest,
            trend = trend(snapshot),
            recency = Recency.describe(snapshot.latest.at, now, zone),
        )
    }
}

/**
 * Glance has no autosizing text, so the size is chosen from the string before it is drawn.
 *
 * This is not a nicety: `72.4` is four glyphs and `159.6` is five, so a size that fills the cover
 * screen in kilograms overflows it in pounds. Width scales roughly with glyph count, so the base
 * size is defined for a four-glyph reading and scaled down from there.
 *
 * Shorter strings do not scale *up*. A widget whose number changes size when you cross below
 * 100 kg would be worse than one that occasionally leaves a little room.
 */
internal fun numeralSp(text: String, base: Float, baseGlyphs: Int = 4): Float =
    if (text.length <= baseGlyphs) base else base * baseGlyphs / text.length
