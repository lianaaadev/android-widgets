# Health Widget App — Implementation Plan

Home-screen widgets over Health Connect. Ships as `:health`, a second app in this repo, sharing
`:core` with `:countdown`.

**Weight is the first and only feature of v1.** The app is named for the door it goes through, not
for the one thing behind it, because the interesting property of Health Connect is that everything
else is behind the same door.

This plan commits to the one abstraction that makes a second metric cheap, and deliberately
refuses every other kind of generality until there is a second caller. There is no plan here for
any metric but weight — the catalogue below is a map, not a roadmap.

## Decided

Four questions were open before Phase 1. All four are now answered, and the answers are load-bearing
enough that they belong at the top rather than in a footnote.

| Question | Answer | What it changes |
|---|---|---|
| **Units** | kg default, lb the alternative. App-wide. | No stone — it is a compound format (`11 st 5 lb`) that would need its own layout branch. One `UnitPreference` in app-level DataStore, not per-widget state |
| **Trend** | Yes — a delta only. Latest reading vs. the nearest one **7 days** back | **Room stays out.** A delta needs two values, not a history. See "No Room, and why" |
| **Cover screen** | v1 | Moves from "after Phase 5" into **Phase 2**, alongside the other two sizes |
| **Phone** | Android 14+ | `READ_HEALTH_DATA_IN_BACKGROUND` is available, so state 4 below is the target state and background refresh is real |

The design canvas for all of this is `mockup/health/` (gitignored, local only), published as
**Weight Widget**.

---

## Core concept

One widget shows one metric. Per-widget configuration picks which — exactly the shape
`:countdown` already has, where per-widget state picks which occasion. v1 offers one choice
(weight), so the picker has one row. That is fine; the plumbing is what we are building.

The app itself is small on purpose: a permission flow, a settings screen, and a "here is what
Health Connect currently has" panel for when the widget looks wrong. Health Connect is the
database. We are not building another one.

## The extensibility question — read this before writing the abstraction

The trap is modelling a metric as "a number and a timestamp", shipping weight, and then finding
that steps do not fit. Health Connect records come in four shapes, and they need different reads:

| Shape | Read | Examples |
|---|---|---|
| **Instantaneous** | `readRecords`, `ascendingOrder = false`, `pageSize = 1` | Weight, body fat, resting heart rate, blood pressure |
| **Interval, cumulative** | `aggregate(AggregateRequest(...))` | Steps today, active calories, distance, hydration |
| **Interval, session** | `readRecords` for the session, then derive a duration | Last night's sleep, last workout, mindfulness |
| **Series** | Read the most recent record, then take its last *sample* | Heart rate, speed, power, skin temperature |

Weight is the easiest of the four. Series records are the awkward one — a single record holds a
list of timestamped samples, so "the latest value" is two levels down, not one.

Using `readRecords` for shape 2 double-counts when two apps write overlapping data — Samsung
Health and a watch both reporting steps is the normal case, not an edge case. So the abstraction
is over the **read strategy**, not over the value:

```kotlin
/** One thing a widget can display. Register an object per metric; the widget never branches. */
interface HealthMetric {
    val id: String                       // stable, persisted in widget state — never rename
    val label: String                    // "Weight"
    val permission: String               // android.permission.health.READ_WEIGHT
    suspend fun read(client: HealthConnectClient): Snapshot?
    fun format(reading: Reading, units: UnitPreference): String
    /** Null when this metric has no meaningful delta, or there is nothing to compare against. */
    fun formatTrend(snapshot: Snapshot, units: UnitPreference): String?
}

/** [at] is when the value was recorded, not when we read it — the widget shows the difference. */
data class Reading(val value: Double, val at: Instant)

/** [previous] is the trend's comparison point: for weight, the nearest reading 7 days back. */
data class Snapshot(val latest: Reading, val previous: Reading?)
```

`read` returns both readings in one call rather than exposing a second method, because the shapes
in the table above differ in how they'd fetch the comparison: weight takes one `readRecords` call
over the window and picks two records out of it, while a cumulative metric would aggregate two
windows. One call keeps that choice inside the metric, where it belongs.

`WeightMetric` is the only implementation in v1. The gate that proves the abstraction is Phase 6,
where a second metric of a *different shape* gets added. Until that runs, assume the interface is
wrong somewhere.

Three rules that keep this from sprawling:

- **`id` is a stable string, not an enum ordinal.** It is persisted in per-widget DataStore and
  will outlive several versions of the metric list.
- **One permission per metric, requested only when a widget asks for it.** Do not request the
  union of every permission at install; Health Connect shows the user exactly what is asked for,
  and an app displaying a weight widget that asks to read sleep looks like spyware. This is the
  main reason the app is named for the door rather than the feature: a broad name must not
  become an excuse for a broad permission request.
- **No metric-specific code in the widget layout.** The widget renders a label, a big number, a
  unit and a recency line. If a future metric cannot fit that, it gets its own widget rather than
  a conditional in this one.

## What else is in Health Connect

**Nothing below is planned.** This is the catalogue, recorded so the `HealthMetric` interface is
designed against the real range rather than against weight alone, and so future-you can see what
the door opens onto without re-researching it.

Grouped by the shape that decides how it is read. Every type needs its own
`android.permission.health.READ_*`, requested only when a widget actually asks for it.

### Instantaneous — same read path as weight, cheapest to add

| Category | Types |
|---|---|
| Body | Body fat, body water mass, bone mass, lean body mass, height, basal metabolic rate |
| Vitals | Blood pressure, blood glucose, body temperature, oxygen saturation, respiratory rate, resting heart rate, heart rate variability (RMSSD) |
| Activity | VO2 max |

Anything here is a near-copy of `WeightMetric`: swap the record class, the permission and the
formatter. Body fat is the obvious second feature if the goal is to prove the abstraction cheaply
rather than to stress it.

### Interval, cumulative — must use `aggregate()`, never `readRecords`

Steps, distance, active calories, total calories, floors climbed, elevation gained, wheelchair
pushes, hydration, nutrition (40+ nutrient aggregates on one record type).

These are the ones that punish a naive implementation: two apps writing overlapping step data —
Samsung Health and a Galaxy Watch, the normal case — will double-count under `readRecords`. They
also need a window ("today", "this week"), which instantaneous metrics do not, so the widget gains
a notion of period the moment the first one lands.

### Interval, session — a duration, not a number

Sleep sessions (with stages), exercise sessions, planned exercise, mindfulness sessions,
menstruation periods.

"Last night's sleep" is the appealing one and the least like weight: the value is a duration, the
recency line means something different, and sleep stages are a nested structure. It would want its
own widget layout, not a parameter on the existing one.

### Series — a record containing many samples

Heart rate, cycling pedaling cadence, power, speed, steps cadence, skin temperature.

Two levels of nesting to reach a current value. Worth adding one of these eventually purely as a
test of the interface.

### Sensitive categories

Cycle tracking (menstruation, ovulation tests, cervical mucus, sexual activity, basal body
temperature) is technically identical to the rest and socially not. Health Connect treats these as
restricted, and Google Play asks for specific justification. Not a fit for a home-screen widget —
the whole point of a widget is that it is visible to whoever glances at the phone.

### What Samsung Health actually writes

Samsung's FAQ commits only to "activity data, such as steps and exercise, heart rate, and sleep".
Secondary sources report a much wider set — weight, body fat, body water, lean body mass, height,
blood pressure, blood glucose, hydration, nutrition, SpO2, respiratory rate, HRV, resting heart
rate, skin temperature, floors, distance, calories, menstruation — and Samsung's own developer blog
covers reading Galaxy Watch body composition through Health Connect, which is why weight is a
credible v1.

Treat all of that as unverified. The only reliable check is the permission list inside Samsung
Health → Settings → Health Connect on your own phone, and it is worth screenshotting that list
during Phase 1 while you are already there — it answers "what could this app ever show?" in one
step, permanently.

## Tech stack

| Layer | Choice | Notes |
|---|---|---|
| Shared design + widget plumbing | `:core` | Theme, buttons, palette, `bindWidget`, pin flow, `WidgetRefreshScheduler`, size buckets |
| Health data | `androidx.health.connect:connect-client` 1.1.0 | 1.2.0-alpha06 is the canary; stay on stable |
| Background work | `androidx.work:work-runtime-ktx` | New to this repo — pin a version in the catalog |
| Per-widget state | DataStore, via Glance's `PreferencesGlanceStateDefinition` | Same as countdown |
| Reading cache | App-level DataStore | **No Room**, trend included. See below |
| Units | App-level DataStore | kg / lb, one preference for the whole app |
| Daily recency tick | `:core`'s `WidgetRefreshScheduler` | So "2 days ago" rolls over at midnight |

### No Room, and why

Countdown needs a database because the user authors the data. Here the user authors nothing —
Health Connect owns every value, and duplicating it into Room buys a second source of truth to
keep in sync. What we actually need is a **cache of the last successful reading per metric**, so
the widget can render when a background read fails or permission is revoked. That is a handful of
values: app-level DataStore, no KSP, no schema, no migrations.

**The trend does not change this**, which is the whole reason it was worth pinning down the shape
of "trend" before Phase 1. A *sparkline* would need a stored history, and Glance cannot draw a line
anyway without rendering to a `Bitmap`. A *delta* needs exactly two numbers — the latest reading and
the nearest one seven days back — and both come out of a single `readRecords` call over a window we
already have permission for. So the cache grows by one `Reading` per metric and nothing else.

If a sparkline is ever genuinely wanted, note that Glance *can* draw bars: a row of `Box`es with
computed heights is buildable natively, and only a true line needs the `Bitmap` path. That would
still need a history, so it would still bring Room (or a serialized list) back. Not now.

### Architecture, mirroring countdown

`WeightApp` (Application) owns a `HealthRepository`, exactly as `CountdownApp` owns
`OccasionRepository`. The repository is the only thing that touches `HealthConnectClient`; it
exposes a `Flow<Snapshot?>` per metric backed by the cache, and a `suspend fun refresh()`. The
widget collects the flow and never calls Health Connect itself.

This matters more here than it did in countdown: a Health Connect read is suspend, throws, and
**fails silently when the app is not in the foreground and the background permission is not
granted**. Nothing on the render path may depend on it.

## Availability and permissions — a four-state machine

This is the bulk of the real work, and most of it is states rather than code.

1. **SDK unavailable.** `HealthConnectClient.getSdkStatus()` returns `SDK_UNAVAILABLE`
   (below Android 9, or no provider) or `SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED` (Play Store
   deep link to update). The widget says so; the app explains.
2. **Permission not granted.** Requested through
   `PermissionController.createRequestPermissionResultContract()` — not the normal permission
   APIs. Health Connect stops offering the dialog after two dismissals, and from then on the only
   route is Health Connect's own settings screen. The app must detect that and link there rather
   than looping a dialog that no longer appears.
3. **Granted, foreground only.** `android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND` is a
   separate grant, and it lives in the system module — **unavailable on Android 13 and below even
   with the Health Connect APK installed.** Check with
   `client.features.getFeatureStatus(FEATURE_READ_HEALTH_DATA_IN_BACKGROUND)`. Without it the
   widget only refreshes while the app is open, which is a materially worse product; the app
   should say that plainly instead of appearing broken.
4. **Granted, background.** The good state. Everything below assumes it.

Manifest, all four states aside:

```xml
<uses-permission android:name="android.permission.health.READ_WEIGHT" />
<uses-permission android:name="android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND" />
<queries><package android:name="com.google.android.apps.healthdata" /></queries>
```

plus a rationale activity handling `androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE` (Android 13
and below) and an `activity-alias` for `android.intent.action.VIEW_PERMISSION_USAGE` with category
`android.intent.category.HEALTH_PERMISSIONS` (Android 14+). Both point at a privacy policy screen.
Health Connect will not grant permissions to an app that does not declare these.

**Not requested:** `READ_HEALTH_DATA_HISTORY`. Without it we see the last 30 days, and the widget
shows one current number plus a 7-day delta. That window comfortably contains the trend — which is
the second reason to keep the trend a delta rather than a chart: a sparkline worth looking at would
want more history than we are willing to ask for. Asking for a user's entire weight history to
display today's figure is not a trade worth making.

## Refresh

Google publishes no numeric rate limits — only that background limits are stricter than
foreground, that there are both periodic and daily quotas, and that apps should prefer changelogs
over repeated raw reads and back off on `IllegalStateException`. That shapes this directly:

- **`PeriodicWorkRequest`, 1 day**, enqueued as unique work with `ExistingPeriodicWorkPolicy.KEEP`.
  Weight changes once a day at most; anything faster spends quota to display the same number.
  Skip enqueuing entirely when the background feature is unavailable.

  *This started at one hour and was cut to a day.* Hourly bought nothing: opening the app is
  itself a full read, and the midnight tick ages the recency line without any read at all, so
  between background runs the widget is neither wrong nor idle. The only thing an hourly job
  caught sooner was a weight recorded while the app was closed and never opened since — and that
  is a widget nobody is looking at.
- **Changes token, not repeated full reads.** `getChangesToken(ChangesTokenRequest(setOf(WeightRecord::class)))`
  once, persisted; then `getChanges(token)` each run, re-priming when the token expires. This is
  the documented way to stay under quota.
- **Back off on `IllegalStateException`** — `Result.retry()`, not `Result.failure()`, and never a
  tight retry.
- **Refresh on app resume**, as `MainActivity.onResume` already does in countdown. Cheap insurance
  when a background read has been quietly failing.
- **Daily tick via `:core`'s `WidgetRefreshScheduler`**, so the "measured 2 days ago" line rolls
  over at midnight without a Health Connect call at all. The value did not change; its description
  did.

There is no push callback when Samsung Health writes new data. Polling is the only option.

## Widget

Sizes reuse `:core`'s `WidgetSizes` — Medium (2x2), Wide (4x2), and the Flip cover screen via the
same second-receiver plus `samsung-appwidget-provider` opt-in that countdown uses. **All three ship
in v1.** A weight number is arguably a better cover-screen widget than a countdown: weighing
yourself and reading the number back without opening the phone is the whole case for it.

Four states, mirroring countdown's:

| State | Shows |
|---|---|
| **Ready** | The number, its unit, the 7-day delta, and how long ago it was recorded |
| **Stale** | The cached number, greyed, with the recording date — never a blank widget. **Drops the delta**, because a trend computed against a value we have not been able to refresh is a claim we cannot stand behind |
| **Needs permission** | A short line, tapping opens the app at the permission step |
| **Unavailable** | Health Connect missing or too old, or the metric has no data at all |

### Two things the design surfaced

- **The trend arrow takes the accent colour in both directions.** No green for down, no red for up.
  The app does not know whether you are trying to lose, gain or hold, and a widget that congratulates
  you for one direction is making a guess about your health it has no business making. This is a
  design decision with a code consequence: one colour, no conditional.
- **Glance has no autosizing text, and the numeral length varies.** `72.4` is four glyphs and
  `159.6` is five, so a size that fills the cover screen in kg overflows it in lb — on the Flex
  Window the difference is roughly 138sp vs 110sp. The size has to be picked from the formatted
  string's length in Kotlin before the `Text` is emitted. Cheap to do, invisible until it bites,
  and it bites hardest on exactly the size that shipped for the first time in v1.

"No data at all" deserves its own copy, because the overwhelmingly likely cause is that whichever
app writes the weight has stopped sharing it with Health Connect — invisible from our side, and
indistinguishable from a user who has never weighed themselves.

**The empty state must not guess which app that is.** This plan assumed Samsung Health and hard
coded it into the copy; on the first real phone the writer was a smart-scale app
(`com.qingniu.fitindex`), so the instructions pointed at an app with nothing to do with it. The
writer is in the data — `record.metadata.dataOrigin.packageName` — so the copy names whatever last
wrote a reading, remembered across empty reads precisely because an empty read is when the name is
needed. With no writer ever seen, the copy stays deliberately general.

## Build phases

**Phase 0 — Module.** `:health` scaffold, `applicationId com.liana.health`, depends on `:core`.
Manifest with permissions, rationale activity and the `VIEW_PERMISSION_USAGE` alias. Availability
check on screen. *Verify early:* whether `connect-client` 1.1.0 forces `compileSdk` past 34 — if it
does, that bump lands on `:core` and `:countdown` too and should happen as its own commit.

**Phase 1 — The read path. Go/no-go gate.** `HealthRepository`, `WeightMetric` (both readings —
latest and the 7-day comparison), the permission flow, and a plain debug screen listing whatever
weight records exist. Stop here and answer one
question on your actual phone: **does your Samsung Health weight appear?** Everything downstream
assumes it does, and the answer depends on a toggle inside an app we do not control. If it does
not appear, the fallback is the Samsung Health Data SDK, which is a different project.

**Phase 2 — Widget.** Glance widget, all four states, all three sizes including the cover-screen
receiver, and the 7-day delta. Rendering from cache only. Pick the numeral size from the formatted
string's length here rather than discovering it later in lb.

**Phase 3 — Refresh. Done.** Daily `PeriodicWorkRequest` as unique work with `KEEP`, gated on the
background grant and cancelled without it — a backgrounded read without that grant returns nothing
*silently*, so scheduling anyway would spend quota to learn nothing and could convince the cache
there is no weight. Changes token persisted in the same DataStore, re-primed on expiry; a quiet
day costs one `getChanges` and no record read. `IllegalStateException` — how quota exhaustion
arrives — maps to `Result.retry()` and WorkManager's exponential backoff, never `failure()` and
never a tight loop. Daily tick via `:core`'s `WidgetRefreshScheduler`, which redraws the recency
line without a Health Connect call at all.

*Found while building it:* the app was reading twice per open — once for the record list, once for
the snapshot — to answer the same question. Now one `readRecords` serves both.

**Phase 4 — Configuration.** Config activity: metric and accent colour, plus pin-to-home-screen via
`:core`'s `requestPinWidget`. Units are **not** here — kg / lb is one app-wide preference on the
settings screen, since two widgets in two units is not a thing you want.

**Phase 5 — Polish. Done.** Accessibility: the widget already carried a description; the app's
latest-reading card now merges its four fragments into one spoken sentence, and the accent swatches
are named — a bare coloured circle announces nothing. Empty-state copy names the real writer (above).

*Staleness turned out to be two thresholds, not one.* A reading goes stale after 14 days, but a
reading can also be from this morning while every refresh since has quietly failed. `UnconfirmedAfter`
(2 days) covers that second case: without it the widget would keep presenting this morning's number
as confirmed when nothing had confirmed it. Generous on purpose — with no background grant the only
refreshes are app opens, and someone who opens the app twice a week should not face a permanently
greyed widget.

**Phase 6 — Second metric.** Add one metric of a *different shape* — steps (aggregate) is the
sharpest test, body fat (latest sample) the easiest. This is what proves or breaks `HealthMetric`.
Better to find out at 400 lines than at 4,000.

## What gets promoted to `:core`, and when

**On the second use, not the first.** Two candidates are already visible:

- The **config-activity picker** — a list of choices with a Cancel/Confirm bar — is nearly
  identical between "which occasion" and "which metric". Promote after Phase 4, when there are two.
- The **stale/unavailable widget states** may share a shape. Wait and see; if the copy differs
  enough, they are not the same thing.

Health Connect stays in `:health` regardless. `:core` holds mechanics and design tokens, never
domain — the same rule that kept date arithmetic in `:countdown`.

## Risks

| Risk | Mitigation |
|---|---|
| Samsung Health sync toggle off — no data, no error | Empty state names the toggle explicitly |
| Background read unavailable below Android 14 | Detect and say so; widget still works, refreshes on app open |
| Galaxy Watch → phone sync lags on Samsung's own schedule | Always show *when* a value was recorded, never imply "now" |
| Permission revoked at any time, silently | Cache means the widget degrades to stale rather than blank |
| Reports of Health Connect revoking read permissions from unused apps | **Unverified** — not in the docs, only community threads. A widget-only app that is rarely opened is exactly the profile at risk. Test by leaving it untouched for a month |
| Quota exhaustion from over-polling | Changes token + daily ceiling + backoff |
| Play Store health-data declaration + privacy policy | Only if published; sideloading sidesteps it |

## Open questions for you

Only one of the original five is left. The other four are settled under "Decided" at the top.

1. **Which second metric** in Phase 6 — steps tests the abstraction hardest, body fat proves it
   most cheaply. This does not block anything before Phase 6, so it can stay open.

And one the design raised rather than answered:

2. **What counts as stale?** Phase 5 shipped two thresholds — 14 days for the reading, 2 days for
   the last successful read — but both are still guesses. The second is the one to watch: if the
   background grant is off, it decides how often a working widget looks greyed. Worth a week of
   living with it before trusting the numbers.

## Sources

- [Get started with Health Connect](https://developer.android.com/health-and-fitness/health-connect/get-started)
- [Read raw data](https://developer.android.com/health-and-fitness/health-connect/read-data)
- [Plan to avoid rate limiting](https://developer.android.com/health-and-fitness/health-connect/rate-limiting)
- [Permissions and data access](https://developer.android.com/health-and-fitness/health-connect/ui/permissions)
- [Health Connect data types](https://developer.android.com/health-and-fitness/health-connect/data-types)
- [Health Connect releases](https://developer.android.com/jetpack/androidx/releases/health-connect)
- [Accessing Samsung Health Data through Health Connect](https://developer.samsung.com/health/blog/en/accessing-samsung-health-data-through-health-connect)
