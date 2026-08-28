# Countdown Widget App — Implementation Plan

Two parts. **Part 1** is the actual app: countdown widgets on the home screen, and — via Samsung's official Flex Window opt-in — on the Z Flip cover screen. **Part 2** is a separate, much larger optional project: a general-purpose widget-bridging app in the mould of CoverWidgets. Read the note at the top of Part 2 before committing to it; for the countdown app's own cover-screen goal it is probably unnecessary.

---

# Part 1 — The countdown app

## Core concept
User creates named "occasions" (e.g. "Anniversary — Oct 12"). Each occasion can be pinned as its own widget showing days remaining + the occasion name. Multiple independent widget instances, each bound to a different occasion.

## Tech stack
- **Kotlin**, Jetpack **Compose** for the main app UI
- **Jetpack Glance** for the widgets (current recommended widget framework — Compose-style API over RemoteViews)
- **Room** for local persistence of occasions
- **DataStore** via Glance's `GlanceStateDefinition` to map each widget instance → occasion
- **AlarmManager (inexact)** for the daily rollover tick
- Min SDK 26, target latest

### Known Glance constraints (decide around these, don't discover them late)
- No `Canvas`, no arbitrary drawing, no animations. A circular progress ring is not a composable — it must be pre-rendered to a `Bitmap` and shown via `Image`.
- Layout primitives are limited to Box/Row/Column/Text/Image/Button/LazyColumn.

## Data model
```
Occasion(
  id: Long,
  title: String,            // "Mom's Birthday"
  date: LocalDate,          // target date
  recurringYearly: Boolean, // if true, countdown rolls over after the date passes
  emoji: String?,           // optional icon shown on widget
  isDeleted: Boolean,       // tombstone — see "Orphaned widgets" below
  accentColor: Int?         // reserved for per-widget theming, see Phase 6
)
```

### Date math — the one genuinely bug-prone piece
`daysUntil(occasion, now)` must be a **pure function with unit tests written in Phase 1**, covering:
- Timezone change mid-countdown (user travels; "days remaining" is timezone-sensitive)
- DST transitions — use `LocalDate.until` / `ChronoUnit.DAYS.between` on `LocalDate`, never millisecond subtraction
- **Feb 29** on a `recurringYearly` occasion — pick and test a rule (Feb 28 in common years is the usual choice)
- The date being today (show "Today!"), and in the past for non-recurring occasions
- Year rollover for recurring occasions (after the date passes, target next year's instance)

Decide **granularity** here, not later: days-only, or hours/minutes when the event is near? A minute-resolution countdown is a fundamentally different refresh architecture and cannot be driven by a daily alarm. Recommendation: days-only for v1.

## Build phases

### Phase 1 — Data layer
Room entity + DAO + repository for CRUD on `Occasion`. The pure `daysUntil` function plus its test suite. Set `android:allowBackup="true"` and confirm the Room DB is included in Auto Backup / D2D transfer — silently losing every occasion on a phone migration is a one-star-review bug.

### Phase 2 — Main app
Compose screens: list of occasions with live countdowns, add/edit/delete form, date picker. Include an "Add to home screen" affordance using `AppWidgetManager.requestPinAppWidget` — guard it with `isRequestPinAppWidgetSupported()` and fall back to instructing the user to long-press the home screen, since not every launcher supports pinning.

### Phase 3 — Widget rendering
One `GlanceAppWidget` class reused for every instance; Android gives each instance its own `appWidgetId`/`GlanceId` for free. Layout: emoji + big day count + occasion label. Use `SizeMode.Responsive` with 2–3 size buckets.

Set in the widget XML:
- `updatePeriodMillis="0"` — updates are driven by our own alarm; leaving this non-zero has the system redundantly waking the app.
- `minResizeWidth`/`minResizeHeight` down to roughly a 1×1 cell, so the widget stays usable when small.
- A `previewImage` (or `previewLayout` on API 31+) so it looks right in the picker.

Tapping the widget should deep-link to that occasion's edit screen via `actionStartActivity` carrying the occasion id.

### Phase 4 — Per-widget configuration
A **plain `Activity`** with an `android.appwidget.action.APPWIDGET_CONFIGURE` intent filter, referenced from `android:configure` in the `appwidget-provider` XML, returning `RESULT_OK` with the `EXTRA_APPWIDGET_ID` extra. (There is no `GlanceAppWidgetConfigurationActivity` class — that was wrong in an earlier draft of this plan.)

The friction point, and the main thing to get right in this phase: the config activity receives an **`appWidgetId`**, but Glance state is keyed by **`GlanceId`**. Bridge them with:
```kotlin
val glanceId = GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId)
updateAppWidgetState(context, glanceId) { it[OCCASION_ID_KEY] = occasionId }
```
Let the user pick an existing occasion or create one inline.

**Orphaned widgets.** Deleting an occasion that has live widgets pointing at it must have defined behavior — this affects the schema, so settle it here. Recommended: soft-delete (`isDeleted`) so the widget can render "Occasion removed — tap to reconfigure" and deep-link back into the config activity, rather than crashing or rendering blank.

### Phase 5 — Refresh scheduling
The countdown changes once per local day.

**Do not use exact alarms.** `SCHEDULE_EXACT_ALARM` is denied by default on Android 14+, and Google Play restricts exact-alarm permissions to alarm-clock/calendar-class apps; a countdown tick would not qualify and does not need the precision. Instead:
- A daily `AlarmManager.setAndAllowWhileIdle` (or `setInexactRepeating`) one-shot targeting just after local midnight, rescheduled each time it fires. No permission required.
- Refresh on app resume, and after any occasion edit, via `GlanceAppWidgetManager` / `updateAll()`.
- Manifest receiver for `ACTION_TIME_CHANGED`, `ACTION_TIMEZONE_CHANGED`, and `ACTION_BOOT_COMPLETED` to reschedule and re-render. (These are exempt from the implicit-broadcast restrictions; `ACTION_DATE_CHANGED` is not reliably delivered to manifest receivers on O+, so don't depend on it.)

A few minutes of lag after midnight is acceptable; a wrong number when the user actually looks is not — hence the resume-time refresh.

### Phase 6 — Polish
Empty and error states, light/dark theming, accessibility content descriptions on the widget (screen readers get "42 days until Mom's Birthday", not "42"), locale-aware date formatting, optional "today's the day" notification.

**Per-widget appearance** (accent color, background opacity) is the first thing users ask for, and it lives in the same per-widget state blob as the occasion id — design the slot now even if the UI ships later.

### Phase 7 — Cover screen (Samsung Flex Window)
Samsung does **not** surface arbitrary widgets on the Z Flip cover screen just because they resize small — the cover screen list is effectively limited to widgets that have explicitly opted in, which is why it's mostly Samsung's own plus Outlook and a few Google widgets.

But there *is* an official developer opt-in, documented on Samsung's Flex Window developer page. Declare a second, Samsung-specific provider meta-data on the same receiver:

```xml
<receiver android:name=".CountdownWidgetReceiver" android:exported="true">
    <meta-data
        android:name="android.appwidget.provider"
        android:resource="@xml/countdown_widget_info" />
    <meta-data
        android:name="com.samsung.android.appwidget.provider"
        android:resource="@xml/samsung_countdown_widget_info" />
</receiver>
```

`res/xml/samsung_countdown_widget_info.xml`:
```xml
<samsung-appwidget-provider display="sub_screen" />
```

The paired standard `appwidget-provider` for the cover-screen variant uses `android:widgetCategory="keyguard"`, with Samsung's documented Flex Window size of `minWidth="352dp"` / `minHeight="339dp"` and `resizeMode="horizontal|vertical"`.

**Caveats to verify on the actual device before relying on this:**
- Samsung's documentation is Flip 5 / One UI 5.1.1-era. Confirm the same opt-in still works on the **Z Flip 8 / One UI 9**, whose cover screen was reworked into customizable panels.
- Confirm whether one receiver can serve both home screen and cover screen, or whether the cover-screen variant needs its own receiver with its own `widgetCategory` — if the latter, factor the Glance composable so both receivers share it.
- The Flex Window box (352×339dp) is a very different aspect ratio from a home-screen 2×2. Give it its own `SizeMode.Responsive` bucket rather than letting the home-screen layout stretch.
- There is no emulator cover screen. This phase can only be validated on physical hardware.

If the opt-in turns out not to work on the Flip 8, that is the point at which Part 2 becomes worth considering — and even then, installing the existing CoverWidgets app is the cheaper answer.

## Suggested order of work
Data layer + date-math tests → main app CRUD → single hardcoded-occasion widget → multi-instance + config activity → daily refresh → polish → Flex Window opt-in, validated on device.

---

# Part 2 — "CoverBridge": our own widget-bridging app

> **Read this first.** Part 2 is *not* needed to get our countdown onto the cover screen — Phase 7's Flex Window opt-in does that directly, and it is perhaps a day of work against several weeks here. Part 2 only makes sense if the goal is a **general-purpose product** that puts *other* apps' widgets (which have not opted in, and never will) onto the Flip cover screen. Build it as its own app, on its own timeline, after Part 1 ships.

## How this works, in one paragraph
CoverWidgets and anything like it are a **widget host proxy**. The bridge app opts *itself* into the cover screen using exactly the Flex Window mechanism from Phase 7. Inside that cover-screen surface it doesn't render its own content — it acts as an `AppWidgetHost` (the same API launchers use), binds a widget belonging to some other app, and re-presents that widget's content inside its own. The user picks "Spotify widget" in our app; the cover screen sees only *our* widget; our widget's job is to look like Spotify's.

**This inference is not verified against CoverWidgets' implementation** — it's the only mechanism the platform APIs plausibly allow. Phase B below is the spike that confirms it before any real investment.

## The central technical risk
An `AppWidgetHostView` is a live `View` in *our* process. A cover-screen widget is a `RemoteViews` object serialized to the *system's* widget host process. You cannot put a live View into RemoteViews. So the proxying has to happen one of two ways, and which one works determines the whole product:

**Approach A — RemoteViews nesting.** Capture the `RemoteViews` the hosted provider sends to our `AppWidgetHost`, and nest it into ours via `RemoteViews.addView(viewId, childRemoteViews)`. RemoteViews carries its originating package and can inflate against that package's resources, so cross-package nesting is plausible. If it works, the hosted widget stays *live* — real layout, working `PendingIntent` taps, correct updates. This is the good outcome.

**Approach B — Bitmap snapshot.** Inflate the hosted widget into an offscreen `AppWidgetHostView` in our process, measure/layout it at cover-screen dimensions, draw it to a `Bitmap`, and ship that bitmap as an `ImageView` inside our RemoteViews. Guaranteed to render *something*, but the result is a static picture: no working buttons, no live scrolling, and every update costs a re-render. Taps degrade to "whole widget opens the host app."

Assume B and be delighted by A.

## Phases

### Phase A — Foundations
New app module. Implement the Flex Window opt-in from Part 1 Phase 7 (this is a hard prerequisite: if our own widget can't reach the cover screen, nothing else matters). Build the widget-picker UI: enumerate installed providers with `AppWidgetManager.getInstalledProviders()`, grouped by app, with labels and preview images.

### Phase B — Binding spike *(go/no-go gate)*
Bind one hard-coded third-party widget and get it onto the cover screen. Specifically:
- Allocate an id with `AppWidgetHost.allocateAppWidgetId()`.
- Bind it. Normal apps are **not** granted `BIND_APPWIDGET` — that's a system/launcher privilege. Use `bindAppWidgetIdIfAllowed()` and, when it returns false, fall back to the `AppWidgetManager.ACTION_APPWIDGET_BIND` intent, which prompts the user. Expect a per-widget consent dialog; design the onboarding around it rather than fighting it.
- Try Approach A. Time-box it. Fall back to B.

**Do not proceed past this phase until something renders on the physical cover screen.** Everything after assumes this works.

### Phase C — Sizing and layout
The hosted widget must be told it lives in a 352×339dp box, or it will lay out for a phone home screen and look wrong. Call `AppWidgetManager.updateAppWidgetOptions()` with `OPTION_APPWIDGET_MIN_WIDTH`/`MAX_WIDTH`/`MIN_HEIGHT`/`MAX_HEIGHT` set to the Flex Window dimensions before rendering. Handle widgets that ignore the hint (clip, letterbox, or scale — pick one and make it consistent).

### Phase D — Update pipeline
An `AppWidgetHost` only receives updates while `startListening()` is active, which means while our process is alive — and a background app's process is killed routinely. This is the second-hardest problem in Part 2:
- Re-render on cover-screen wake rather than continuously.
- Re-establish `startListening()` and force a refresh from a `BroadcastReceiver`/`WorkManager` job on screen-on.
- Accept staleness for widgets that only push updates on their own schedule.
- Resist the urge to solve this with a persistent foreground service — it's a battery and Play-policy liability, and it's the reason bridging apps feel janky.

### Phase E — Interactivity
In Approach A, `PendingIntent`s inside the nested RemoteViews should fire on their own. In Approach B, the best available behavior is a single tap target that launches the hosted app — use `ActivityOptions.setLaunchDisplayId()` (main = 0, cover = 1) to choose which screen it opens on, and let the user configure that per widget.

### Phase F — Configuration passthrough
Many widgets have their own configuration activity (`appwidget-provider android:configure`). Ours must launch it after binding and honor its result, on the **main** screen. Widgets whose config activity is unreachable or crashes need a graceful "this widget can't be added" path.

### Phase G — Productization
Multi-slot management (which bridged widget in which cover panel), per-widget settings, an explicit "not affiliated with Samsung" disclaimer, and a compatibility list. Expect a long tail of widgets that render wrong; ship a way for users to report them and a known-broken list in-app.

## Standing risks
- **Undocumented surface.** `display="sub_screen"` is Samsung-specific and can change between One UI releases. A Flip 9 could break the whole app.
- **Cross-package RemoteViews nesting is not a guaranteed API contract.** If Approach A works today it may not survive a platform update; keep Approach B as a live fallback path, not a deleted branch.
- **Play policy.** Hosting other apps' widgets is legitimate (launchers do it), but the consent flow, background behavior, and Samsung trademark usage all need care.
- **Effort asymmetry.** Part 1 is a well-understood app. Part 2 is platform archaeology against an undocumented vendor surface, and the honest estimate is weeks with a real chance the spike in Phase B kills it.

## Sources
- [Samsung Developer — Flex Window](https://developer.samsung.com/galaxy-z/flex_window.html) (the `com.samsung.android.appwidget.provider` / `display="sub_screen"` opt-in)
- [Android Authority — Z Flip 7 cover screen widgets, with retraction](https://www.androidauthority.com/samsung-galaxy-z-flip-7-flip-7-fe-cover-screen-upgrade-3574841/) (confirms the allowlist behavior)
- [9to5Google — Z Flip 8 cover screen app support](https://9to5google.com/2026/07/22/samsung-galaxy-z-flip-8-cover-screen-app-improvements/)
- [Android Developers — exact alarms denied by default](https://developer.android.com/about/versions/14/changes/schedule-exact-alarms)
- [CoverWidgets on Google Play](https://play.google.com/store/apps/details?id=apps.ijp.coverwidgets) (prior art for Part 2)
