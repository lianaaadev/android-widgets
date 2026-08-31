# Health

An Android app that reads your weight out of Health Connect and puts it on your home screen —
the current number, how it has moved this week, and when it was actually recorded.

The widget is the product. The app exists to feed it, and to explain itself when the widget looks
wrong.

One of two apps in this repo. The design language and the Glance/AppWidget plumbing they share
live in `:core` — see the [repo README](../README.md).

## Purpose

Health Connect is where the app that owns your scale puts your weight. This app reads it, caches
the last value, and renders it at three sizes including the Galaxy Z Flip
[cover screen](#cover-screen-samsung-flex-window). Each widget picks its own accent colour;
kilograms or pounds is one setting for the whole app.

**Weight is the only thing it shows.** The app is named for the door it goes through rather than
the one thing behind it, because the interesting property of Health Connect is that everything
else is behind the same door — see [Adding a second metric](#adding-a-second-metric). The name
must not become an excuse for a broad permission request: it asks to read weight, and nothing
else.

## Tech stack

| Layer | Choice | Notes |
|---|---|---|
| Language | Kotlin 2.0.21 | |
| App UI | Jetpack Compose, Material 3 | Compose BOM 2024.06.00 |
| Widgets | **Jetpack Glance 1.1.0** | Compose-style API over `RemoteViews` |
| Shared code | `:core` | Design tokens, theme, buttons, widget binding, refresh alarms |
| Health data | **`androidx.health.connect:connect-client` 1.1.0** | The only source of truth |
| Persistence | **DataStore 1.1.1 — no Room** | See [No database](#no-database-and-why) |
| Per-widget state | DataStore, via Glance's `PreferencesGlanceStateDefinition` | Accent colour |
| Background work | WorkManager 2.9.1 | Daily read; see [Refresh](#what-triggers-a-refresh) |
| Scheduling | `AlarmManager`, **inexact** | Midnight recency rollover |
| Build | AGP 8.9.1, Gradle 8.11.1, JDK 17 | `compileSdk` 36, `minSdk` 26, `targetSdk` 34 |

`compileSdk` is **36 and not negotiable**: `connect-client` 1.1.0's AAR metadata declares
`minCompileSdk=36` and `minAndroidGradlePluginVersion=8.9.1`, which is what pulled the whole repo
— `:core` and `:countdown` included — up with it.

No dependency injection framework, no navigation library, no `ViewModel`s, same as `:countdown`.
The dependency graph is one repository hung off `Application`.

### Constraints Glance imposes

`:countdown`'s README lists the general ones (no shadows, gradients, arcs, animation; no
letter-spacing; `Bold` is the heaviest weight). Two bite specifically here:

- **No autosizing text.** `72.4` is four glyphs and `159.6` is five, so a size that fills the
  cover screen in kilograms overflows it in pounds. `numeralSp()` picks the size from the glyph
  count before the `Text` is emitted. Short readings deliberately do *not* scale up — a number
  that changed size the day you crossed below 100 kg would be worse than one that leaves a
  little room.
- **No lines.** Which is why the trend is a delta and an arrow rather than a sparkline. Glance
  *can* draw bars — a row of `Box`es with computed heights — if a chart is ever wanted, but a
  true line needs rendering to a `Bitmap`.

## Data model

Nothing here is authored by the user, so nothing here is a database entity. `:countdown` starts
from a Room table; this app starts from someone else's records and keeps only what it needs to
draw when those records cannot be reached.

### What is actually pulled — `WeightRecord`

One call, in `WeightMetric.readWindow`:

```kotlin
client.readRecords(
    ReadRecordsRequest(
        recordType = WeightRecord::class,
        timeRangeFilter = TimeRangeFilter.between(now.minus(ReadWindow), now),
        ascendingOrder = false,          // newest first
    )
)
```

Three fields are taken off each record and the rest is dropped on the floor:

| Read from the record | Becomes | Notes |
|---|---|---|
| `record.weight.inKilograms` | `Reading.value` | **Always stored in kg.** Pounds is a display concern; converting on the way in would bake a preference into the cache |
| `record.time` | `Reading.at` | An `Instant` — when the scale recorded it, not when we read it |
| `record.metadata.dataOrigin.packageName` | `SourcedReading.sourcePackage` | Which app wrote it — see [Naming the source](#naming-the-source) |

| Window | Value | Why |
|---|---|---|
| `ReadWindow` | 30 days | Everything visible without `READ_HEALTH_DATA_HISTORY`. One call covers both the current value and the comparison |
| `TrendWindow` | 7 days | How far back the delta compares |

Weight is **instantaneous**, so it reads with `readRecords` rather than `aggregate()`. Two apps
writing overlapping weight is not the double-counting hazard it is for steps — a duplicate weight
reading is still that weight, where duplicate step counts add up wrongly. That distinction is the
whole reason the metric interface exists rather than a single hard-coded read.

### The objects

None of these are persisted as such; they are what the read produces and the widget consumes.

| Type | Declared in | What it is |
|---|---|---|
| `Reading` | `HealthMetric.kt` | One number and its timestamp — `value: Double` (kg), `at: Instant` |
| `Snapshot` | `HealthMetric.kt` | `latest: Reading` and `previous: Reading?` — the pair a trend needs, resolved in one read |
| `Trend` | `HealthMetric.kt` | `direction: TrendDirection` and `text: String` — already formatted, in the display unit |
| `SourcedReading` | `WeightMetric.kt` | A `Reading` plus the package that wrote it. Sits with the metric because only its raw read produces one |
| `CachedState` | `ReadingCache.kt` | What the widget actually renders from; the cache's seven keys, rehydrated |

`HealthMetric` is the one abstraction, and the thing a second metric has to fit:

```kotlin
interface HealthMetric {
    val id: String            // stable, persisted in per-widget state — never rename
    val label: String
    val permission: String    // android.permission.health.READ_WEIGHT
    suspend fun read(client: HealthConnectClient, now: Instant): Snapshot?
    fun format(reading: Reading, units: UnitPreference): String
    fun trend(snapshot: Snapshot, units: UnitPreference): Trend?
}
```

`read` returns **both** readings in one call rather than exposing a second method, because Health
Connect's four record shapes fetch their comparison point differently: weight takes one windowed
`readRecords` and picks two records out of it, where a cumulative metric would aggregate two
separate windows. Keeping that inside the metric is the whole point.

`WeightMetric` is the only implementation. Until a second one exists, assume this interface is
wrong somewhere.

### The trend

`Trend` carries a **direction** alongside the text so the widget can pick an arrow. Both
directions get the accent colour and the same neutral wording — never green for down and red for
up. The app does not know whether you are trying to lose, gain or hold, and colouring one
direction as good news would be a claim about your health it has no basis for making.

Selecting the comparison point is `WeightMetric.snapshotFrom`, and it is less obvious than it
looks:

- The comparison is the newest reading **at or before `now − 7 days`**, anchored on *now* rather
  than on the latest reading's own timestamp. The label says "this week" and that is the user's
  week. It also degrades correctly: stop weighing yourself and the target slides past every
  record, so the trend disappears rather than quietly comparing two ancient numbers.
- **No trend at all** when nothing is old enough. A delta over three days labelled "this week"
  would be worse than no delta.
- The latest reading is never its own comparison point, which a single old record would otherwise
  render as "level".
- The delta is computed **in the display unit before rounding**. Rounding in kg and converting
  after would show a 0.3 kg change as "0.3 lb", wrong by more than a factor of two.

### The cache — `data/ReadingCache.kt`

Seven values in one app-level DataStore. This is the entire persistence story.

| Key | Why |
|---|---|
| `latest_value`, `latest_at` | The number the widget draws |
| `previous_value`, `previous_at` | The trend's comparison point |
| `last_read_at` | Distinguishes "old reading" from "reading nobody has confirmed" |
| `permission_granted` | So the widget can tell a revoked permission from an empty provider |
| `source_package` | The app that wrote the last reading — see [Naming the source](#naming-the-source) |
| `changes_token` | Health Connect's changelog cursor |

Two rules that are easy to get backwards:

- A read that **succeeds and finds nothing** clears the cached number. An empty Health Connect
  means the value is genuinely gone, and leaving a stale one we can no longer source would be a
  lie.
- A read that **fails** leaves it alone. The number stays on screen and ages into `Stale`, which
  is the reason the cache exists at all.

> `MutablePreferences.remove(key)` is declared to return a non-null `T` but returns null when the
> key is absent, and Kotlin trusts the signature and unboxes it. Any expression ending on a
> `remove` call is a `NullPointerException` waiting for the first time that key is missing. This
> shipped once. Clear keys with `-=` (`minusAssign`), which returns `Unit`.

### No database, and why

`:countdown` needs Room because the user authors its data. Here Health Connect owns every value,
so duplicating it into a database would buy a second source of truth to keep in sync and nothing
else. What the widget needs is something to draw when a read fails or permission is revoked —
which is a handful of values, not a schema. No KSP, no migrations.

The trend does not change this, and that is why its shape was pinned down before any code was
written. A *sparkline* needs stored history; a *delta* needs two numbers, both of which come out
of a single `readRecords` call over a window we already have permission for.

Backup rules exclude everything, for the same reason: a restored cache is a stale copy of data
the device can simply re-read.

### Per-widget state

```kotlin
object WidgetPrefs { val AccentColor = intPreferencesKey("accent_color") }
```

A plain ARGB `Int`, because DataStore does not know what a `Color` is — the same reason
`:core`'s `AccentPalette` holds `Int`s. A widget placed before this key existed falls back to the
default rather than failing.

Note what is *not* here. `:countdown` stores an occasion id per widget, because two widgets show
different things; here every widget shows the same number, so the only per-widget property is its
colour.

## How the data flows

Health Connect is the database. The app owns a cache, and **the widget renders from the cache
only.**

```
   Health Connect (provider)              ReadingCache (app DataStore)
   ┌────────────────────────┐             ┌─────────────────────────────┐
   │  WeightRecord rows     │  refresh()  │ latest / previous / read at │
   │  written by some other │ ──────────► │ permission / source / token │
   │  app entirely          │             └─────────────────────────────┘
   └────────────────────────┘                          │
                                                       │ Flow
                                                       ▼
                                              WeightWidget (Glance)
```

That direction is load-bearing. A Health Connect read is `suspend`, throws, and — this is the
one that catches people — **fails silently when the app is backgrounded without the background
grant**, returning nothing rather than an error. A render path that depended on it would produce
blank widgets at exactly the moments people look at them.

Everything goes through one repository, which is the only thing in the app that holds a
`HealthConnectClient`:

```
                          HealthRepository
                                  │
        ┌─────────────────┬───────┴────────┬──────────────────┐
        │                 │                │                  │
    cached            refresh()     refreshIfChanged()   permissionState()
   (Flow, no          (full read,    (changes token,      (granted? background?)
    network)           app resume)    daily worker)
        │                 │                │                  │
        ▼                 ▼                ▼                  ▼
   WeightWidget      ReadingCache ────────────────────►  MainScreen
   (renders)         (written, then WeightWidget().updateAll())
```

Only the middle two ever touch Health Connect. `cached` is a `Flow` off DataStore and cannot
fail, which is what makes it safe on a render path.

### Reading — the app

`MainActivity.onResume` → `repository.refresh()`, which does **one** `readRecords` call and
returns both the snapshot and the record list. It used to do two, asking the same question twice
per open.

### Reading — a widget

```
launcher / alarm / worker
      │
      ▼
GlanceAppWidgetReceiver ──► WeightWidget.provideGlance(context, id)
                                    │
                                    ├─ HealthConnectAvailability.of(context)   ← local, cheap
                                    │
                                    └─ provideContent {
                                          currentState(WidgetPrefs.AccentColor)  ← reactive
                                          repository.cached                       ← reactive Flow
                                              │
                                              ▼
                                          widgetStateOf(availability, cached, now)
                                              │
                                              ▼
                                          Ready | Stale | NeedsPermission | Unavailable
                                              │
                                              ▼
                                          Medium | Wide | Cover layout
                                       }
```

**Both reads happen inside `provideContent`, and that is load-bearing** — for the same reason
`:countdown`'s README gives. `provideGlance`'s body runs once per Glance session; a later
`update()` recomposes without re-running it. A widget dropped from the picker starts its session
*before* the config activity has written the accent, so a colour captured above the composition
would be stuck at the default forever.

### The four widget states — `widget/WidgetState.kt`

Decided once, in one pure function, so three layouts cannot drift apart on what they think they
are showing.

| State | Shows |
|---|---|
| **Ready** | The number, its unit, the 7-day delta, and how long ago it was recorded |
| **Stale** | The cached number greyed, with its recording date — and **no delta** |
| **NeedsPermission** | A short line; tapping opens the app at the permission step |
| **Unavailable** | No provider, or a provider holding no weight — different copy for each |

`Stale` has no `trend` field at all, so dropping the delta is enforced by construction rather
than by remembering to hide it. A trend computed against a value we have not been able to refresh
is a claim we cannot stand behind.

Permission is checked **before** emptiness. Without permission we cannot know whether there is
data, so "no weight yet" would be a guess — and the wrong one, since it would send someone into
another app's settings when the fix is one tap.

Staleness is **two thresholds**, because there are two different failures:

| Threshold | Covers |
|---|---|
| `StaleAfter` — 14 days | The reading itself is old |
| `UnconfirmedAfter` — 2 days | The reading is recent but nothing has confirmed it since |

The second matters when refreshes fail silently. Generous on purpose: with no background grant
the only refreshes are app opens, and someone who opens the app twice a week should not face a
permanently greyed widget. **Both numbers are still guesses** and worth revisiting against real
use.

### Naming the source

The empty state names the app that last wrote a reading — remembered across empty reads, because
an empty read is exactly when the name is needed and has none to give.

This is not decoration. The design assumed Samsung Health writes the weight and hard coded that
into the copy; on the first real phone the writer was a smart-scale app, so the instructions
pointed at an app that had nothing to do with it. The writer is in the data
(`record.metadata.dataOrigin.packageName`), so it comes from there.

`SourceApp` is a short table of known packages plus a raw-package fallback, deliberately **not**
a `PackageManager` lookup: reading another app's label needs it visible to us, which on Android
11+ means `QUERY_ALL_PACKAGES` or a long `<queries>` list. An app that reads one number should not
be able to enumerate what else is installed.

### Writing — binding a widget to an accent

The only write path in the app, and the mirror of `:countdown`'s occasion binding. There is no
"add to home screen" route here, because there is nothing to pin *from* — one metric means the
picker is the only entry point:

```
   launcher allocates appWidgetId
   → HealthWidgetConfigActivity
   → user picks an accent
   → setResult(RESULT_OK)
              │
              ▼
   bindWeightWidget(context, appWidgetId, accentColor)
              │
              ├─ GlanceAppWidgetManager.getGlanceIdBy(appWidgetId)
              ├─ updateAppWidgetState { accent_color = accentColor }
              └─ WeightWidget().update(context, glanceId)
```

The `getGlanceIdBy` line is the same awkward seam `:countdown` describes — the framework deals in
`appWidgetId` (an `Int`), Glance keys state by `GlanceId`. Both apps cross it through `:core`'s
`bindWidget`, so it exists in exactly one place.

Nothing here writes to Health Connect. The app holds a read permission and no write permission,
and gaining one would change what the privacy screen has to say.

### What triggers a refresh

| Trigger | Path | Touches Health Connect? |
|---|---|---|
| App resumed | `MainActivity.onResume` → `repository.refresh()` | Yes — one `readRecords` |
| Once a day | `RefreshScheduler` → `RefreshWorker` → `refreshIfChanged()` | Usually only `getChanges` |
| Local midnight | `DailyTickScheduler` → `DailyTickReceiver` → `updateAll()` | **No** |
| Reboot | `BOOT_COMPLETED` (alarms don't survive one) | No |
| Clock or time zone changed | `TIME_SET`, `TIMEZONE_CHANGED` | No |
| App updated | `MY_PACKAGE_REPLACED` | No |
| Accent changed | `bindWeightWidget` → `update()` | No |

Five of the seven redraw the widget without asking Health Connect anything, which is the point:
the recency line ages on its own, and ageing it is not worth a read.

#### The daily worker

`PeriodicWorkRequest` at one day, as unique work with `KEEP`. This started hourly and was cut:
weight changes once a day at most, and the other two paths already cover the gaps — opening the
app is a full read, and the midnight tick ages the recency line without any read at all.

> **The unique work name is versioned** (`weight-refresh-daily`). `KEEP` means an already-enqueued
> job wins, so changing the period alone would leave every phone that already had the old job
> running it forever, with the new spec never applied. `RefreshScheduler.LegacyNames` retires the
> old one. Any future change to the period needs the same treatment.

**Gated on the background grant, and cancelled without it.** A backgrounded read without that
grant returns nothing silently, so scheduling anyway would spend quota to learn nothing — and
could convince the cache there is no weight, inventing an empty state out of a permission problem.

`refreshIfChanged` uses Health Connect's **changes token** rather than re-reading records: a quiet
day costs one `getChanges` call and no record read. Google publishes no numeric rate limits, only
that background quotas are stricter than foreground ones and that apps should prefer changelogs
to repeated raw reads. Expired tokens yield *no changes* rather than an error, so expiry
re-primes — without that branch the widget would sit unchanged forever on a token nobody noticed.

`IllegalStateException` is how quota exhaustion arrives, and maps to `Result.retry()` and
exponential backoff — never `failure()`, which drops the run, and never a tight retry, which is
what earns the block in the first place.

### Dates, not durations

`:countdown` has the same section under the name *Time zones*, and for the same reason: the unit
of this app is a day, not an hour.

`Recency` counts **calendar days**, not elapsed hours: a reading from 11pm last night is
"yesterday" at 7am, not "8 hours ago". The day boundary is what people mean, and it is also what
the midnight tick can roll over for free.

`LocalDate.ofInstant` is an **API 34** method and this module's `minSdk` is 26 — it compiles
clean against `compileSdk` 36 and throws `NoSuchMethodError` on older phones. Use
`instant.atZone(zone).toLocalDate()`.

## Availability and permissions

No equivalent in `:countdown`, which owns its data and needs no permission to read it. Here the
app can fail in five distinct ways before it has a number to show, and most of the real work is
in the states rather than the code.

| # | State | Detection | What the app does |
|---|---|---|---|
| 1 | SDK unavailable | `getSdkStatus()` | Says so plainly; no button that cannot help |
| 2 | Provider needs updating | `SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED` | Play Store deep link |
| 3 | Permission not granted | `getGrantedPermissions()` | Asks — see below |
| 4 | Granted, foreground only | `getFeatureStatus(FEATURE_READ_HEALTH_DATA_IN_BACKGROUND)` | Says the widget only updates while open, and cancels the worker |
| 5 | Granted, background | both | The good state |

`HealthConnectAvailability` (states 1–2) is deliberately a separate type from
`HealthPermissionState` (3–5). They fail for unrelated reasons and are fixed in different places;
collapsing them into one nullable would lose the distinction the user most needs explained.

**Health Connect stops offering its permission dialog after two dismissals**, and gives no API to
ask whether that has happened. `HealthConnectSettings` counts the app's own prompts and switches
to linking into Health Connect rather than rendering a button that silently does nothing.

The manifest carries three things Health Connect will refuse to grant permissions without:

```xml
<uses-permission android:name="android.permission.health.READ_WEIGHT" />
<uses-permission android:name="android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND" />
<queries><package android:name="com.google.android.apps.healthdata" /></queries>
```

plus both rationale routes — an `androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE` filter for
Android 13 and below, and a `VIEW_PERMISSION_USAGE` `activity-alias` for 14+ — which both point
at `PrivacyActivity`.

Without the `<queries>` entry, Android 11+ hides the provider and `getSdkStatus()` reports it
missing on a phone that has it.

**`READ_HEALTH_DATA_HISTORY` is deliberately not requested.** Without it we see the last 30 days,
which comfortably contains both the current weight and the 7-day comparison. Asking for someone's
entire weight history to display today's figure is not a trade worth making — and it is the
second reason the trend is a delta rather than a chart.

## Widget sizes

| Size | Dimensions | Notes |
|---|---|---|
| 2×2 | 168 × 168 dp | Label, number, trend, recency |
| 4×2 | 344 × 168 dp | Number left, label and trend right |
| Cover | 352 × 339 dp | Samsung Flex Window |

`SizeMode.Responsive` picks between them; `updatePeriodMillis` is `0` because refreshes are driven
by our own worker and alarm.

Placing a widget runs `HealthWidgetConfigActivity`, which picks the accent — the only property
that can differ between two weight widgets, since there is one metric and units are app-wide.

### Cover screen (Samsung Flex Window)

Same mechanism as `:countdown`: a second receiver at `widgetCategory="keyguard"` and the Flex
Window's size, carrying Samsung's opt-in alongside the standard meta-data.

```xml
<samsung-appwidget-provider display="sub_screen" />
```

`minWidth`/`minHeight` stay at the full window, which is what Samsung documents. **`minResize*`
are deliberately much smaller** (150 × 160 dp): from Flip 6 / One UI 6.1.1 the cover screen can
lay out several widgets in one view, and a widget declaring it cannot shrink below the whole
window will never be one of them. Samsung does not prescribe those values.

> **Unverified.** The full-size cover widget is confirmed working on a device. Whether the
> smaller `minResize*` actually lets two widgets share the cover screen is not — there is no
> cover screen in the emulator.

## Adding a second metric

The gate that proves or breaks `HealthMetric`. Health Connect's records come in four shapes, and
they need different reads — the trap is modelling a metric as "a number and a timestamp",
shipping weight, and then finding that steps do not fit.

| Shape | Read | Examples |
|---|---|---|
| **Instantaneous** | `readRecords`, `ascendingOrder = false` | Weight, body fat, resting heart rate, blood pressure |
| **Interval, cumulative** | `aggregate(AggregateRequest(...))` | Steps, active calories, distance, hydration |
| **Interval, session** | `readRecords`, then derive a duration | Sleep, workouts, mindfulness |
| **Series** | Read the latest record, then take its last *sample* | Heart rate, speed, power, skin temperature |

Weight is the easiest of the four. **Steps is the sharpest test** — `readRecords` double-counts
when two apps write overlapping data (a phone and a watch both reporting steps is the normal
case, not an edge case), so it must use `aggregate()`, and it needs a notion of period that
instantaneous metrics do not. **Body fat is the cheapest** — a near-copy of `WeightMetric` with a
different record class, permission and formatter.

Sleep would want its own widget layout rather than a parameter on this one: the value is a
duration, and the recency line means something different.

Three rules that keep this from sprawling:

- **One permission per metric, requested only when a widget asks for it.** Never the union at
  install. An app displaying a weight widget that asks to read sleep looks like spyware.
- **`id` is a stable string, not an enum ordinal.** It is persisted in per-widget state and will
  outlive several versions of the metric list.
- **No metric-specific code in the widget layout.** If a future metric cannot fit label / number /
  unit / recency, it gets its own widget rather than a conditional in this one.

Cycle tracking is technically identical to the rest and socially not — Health Connect treats it as
restricted, and the whole point of a widget is that it is visible to whoever glances at the phone.

## Project layout

```
health/src/main/java/com/liana/health/
  HealthApp.kt                    Application; owns the repository, schedules the midnight tick
  data/
    HealthMetric.kt               The metric interface, Reading, Snapshot, Trend
    WeightMetric.kt               The only implementation; trend selection lives here
    HealthRepository.kt           The only thing that touches HealthConnectClient
    ReadingCache.kt               The whole persistence story, in one DataStore
    HealthConnectAvailability.kt  Is there a provider at all
    HealthConnectSettings.kt      Prompt counting and the settings deep link
    UnitPreference.kt             kg / lb, app-wide
    Recency.kt                    Calendar-day arithmetic
    SourceApp.kt                  Package name → something worth showing a person
  ui/
    MainActivity.kt               Permission flow, resume refresh, worker gating
    MainScreen.kt                 Every app state, including the record list
    MainViewModel.kt              One state class; no ViewModel
    PrivacyActivity.kt            Both rationale routes point here
  widget/
    WeightWidget.kt               GlanceAppWidget; all three size layouts
    WidgetState.kt                The four states + numeral sizing, both pure
    Receivers.kt                  Home-screen and cover-screen providers
    HealthWidgetConfigActivity.kt Accent picker, runs when a widget is placed
    HealthWidgetBinding.kt        Which widget and which key, over :core's helpers
    WidgetPrefs.kt                Per-widget DataStore keys
  work/
    RefreshWorker.kt              The daily read
    RefreshScheduler.kt           Scheduling, and the decision not to
    DailyTick.kt                  Midnight rollover + boot/clock/timezone receiver

health/src/test/java/com/liana/health/    45 tests, no Android runtime needed
```

The palette, type scale, buttons and widget-binding helpers are not here — they are `:core`'s,
shared with `:countdown`, including the `Dimmed` greys a stale reading falls back to.

## Building

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
./gradlew :health:assembleDebug
./gradlew :health:testDebugUnitTest
./gradlew :health:installDebug      # with a device or emulator attached
```

Or open the project root in Android Studio.

### Tests

45 tests, all pure JVM — nothing here needs a device, because everything worth testing was kept
free of one.

| Suite | Covers |
|---|---|
| `WeightMetricTest` (11) | Trend selection: irregular readings, nothing old enough, a reading that would compare with itself, and deltas rounded in the display unit |
| `WidgetStateTest` (15) | The four states, both staleness thresholds, permission beating emptiness, and the empty state naming its source |
| `ReadingCacheTest` (6) | The DataStore round trip, including the shape that shipped a crash |
| `NumeralSizeTest` (4) | Pounds shrinking to fit; short readings not growing — nested in `WidgetStateTest.kt` |
| `RecencyTest` (3) | Calendar days rather than elapsed hours |
| `SourceAppTest` (3) | Known packages mapped to names, unknown ones passed through |
| `UnitPreferenceTest` (3) | Exact avoirdupois conversion, and a comma-decimal locale |

### What can only be tested on a device

- Whether your weight reaches Health Connect at all — it depends on a toggle inside an app we do
  not control.
- The cover screen. There is none in the emulator.
- Whether the daily worker actually fires. WorkManager's period is a floor, not a promise, and
  Samsung's battery management stretches it. `adb shell dumpsys jobscheduler | grep com.liana.health`
  shows whether the job is even scheduled.

## The look

> **Stills not exported yet.** `:countdown` has `docs/countdown/*.png` embedded here; the
> equivalent artboards for this app exist only in the local, gitignored `mockup/health/`
> (`WidgetSizes`, `WidgetStates`, `CoverScreen`, `Main`, `Permission`, `NoData`, `Settings`,
> `WidgetConfig`). Exporting them to `docs/health/` is what this section is waiting on.

What they would show, and what the design is accountable to:

- **Widget sizes** — 2×2, 4×2 and cover, in dark and light. On a light home screen the widget is
  a solid slab of its accent with dark type; on a dark one it is a dark card with the number in
  that accent.
- **Widget states** — Ready with a delta, Stale greyed with no delta, NeedsPermission, and the
  two Unavailable variants. The [four states](#the-four-widget-states--widgetwidgetstatekt) are
  the section this illustrates.
- **App** — the permission flow, the record list with its per-row source, and the unit toggle.

## Known unknowns

| Risk | Where it stands |
|---|---|
| The writing app stops syncing — no data, no error | Empty state names whichever app last wrote a reading |
| Background read unavailable below Android 14 | Detected; worker cancelled and the app says so |
| Watch → phone sync lags on the vendor's schedule | The widget always shows *when* a value was recorded, never implies "now" |
| Permission revoked silently | Cache means the widget degrades to stale rather than blank |
| Health Connect revoking permissions from unused apps | **Unverified** — community reports only. A widget-only app that is rarely opened is exactly the profile at risk |
| Quota exhaustion | Changes token + daily ceiling + backoff |
| Play Store health-data declaration + privacy policy | Only if published; sideloading sidesteps it |
