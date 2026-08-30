# android-widgets

Home-screen widgets for Android, built on Jetpack Glance. Two apps, one shared core.

The widgets are the product in both cases. The apps exist to feed them.

## Modules

| Module | What it is |
|---|---|
| [`:countdown`](countdown/README.md) | Count down to a birthday, a flight, a lease running out. One widget per occasion, plus the Galaxy Z Flip cover screen. **Shipping.** |
| [`:health`](health/plan.md) | Your latest weight, read from Health Connect (which is where Samsung Health puts it). Named for the door it goes through, not the one thing behind it. **Not built yet.** |
| `:core` | The design language and the widget plumbing both apps share. Android library, no persistence. |

Each app is its own APK with its own `applicationId`; they share code, not a process.

## What lives in `:core`

```
core/src/main/java/com/liana/widgets/core/
  design/
    Palette.kt          Neutral tokens + AccentPalette (plain ARGB, so Room and DataStore
                        can carry them and Glance can read them)
    Theme.kt            WidgetTheme — dark colour scheme and the type scale
    Components.kt       PrimaryButton, SecondaryButton
  widget/
    WidgetBinding.kt    bindWidget (the appWidgetId → GlanceId bridge), requestPinWidget,
                        and the abstract pin-callback receiver
    WidgetRefreshScheduler.kt   Inexact alarms, and why they are inexact
    WidgetSizes.kt      The Medium / Wide / Cover size buckets, Flex Window included
```

The rule for what belongs here: mechanics and design tokens, never domain. Date arithmetic stays
in `:countdown`; Health Connect will stay in `:health`. `:core` holds no Room database and no
entities — only the app modules apply KSP.

`:core` exposes Compose and Glance as `api` dependencies, since both appear in its public
signatures.

## Building

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
./gradlew :countdown:assembleDebug
./gradlew :countdown:testDebugUnitTest
./gradlew :countdown:installDebug      # with a device or emulator attached
```

Or open the project root in Android Studio.

## Repo layout

```
core/          shared library module
countdown/     the countdown app + its README
docs/          exported design stills, per app
mockup/        design canvas artboards — gitignored, local only
```
