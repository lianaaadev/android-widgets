# android-widgets

Home-screen widgets for Android, built on Jetpack Glance. Two apps, one shared core.

The widgets are the product in both cases. The apps exist to feed them.

## Modules

| Module | What it is |
|---|---|
| [`:countdown`](countdown/README.md) | Count down to a birthday, a flight, a lease running out. One widget per occasion, plus the Galaxy Z Flip cover screen. **Shipping.** |
| [`:health`](health/README.md) | Your latest weight, read from Health Connect — whichever app writes it. Named for the door it goes through, not the one thing behind it. **Shipping: weight only.** More metrics to come; everything else is behind the same door. |
| `:core` | The design language and the widget plumbing both apps share. Android library, no persistence. |

Each app is its own APK with its own `applicationId`; they share code, not a process.

## What lives in `:core`

```
core/src/main/java/com/liana/widgets/core/
  design/
    Palette.kt          Neutral tokens, Dimmed (the greys a widget falls back to when its
                        value no longer applies), and AccentPalette (plain ARGB, so Room and
                        DataStore can carry them and Glance can read them)
    Theme.kt            WidgetTheme — dark colour scheme and the type scale
    Components.kt       PrimaryButton, SecondaryButton
  widget/
    WidgetBinding.kt    bindWidget (the appWidgetId → GlanceId bridge), requestPinWidget,
                        and the abstract pin-callback receiver
    WidgetRefreshScheduler.kt   Inexact alarms, and why they are inexact
    WidgetSizes.kt      The Medium / Wide / Cover size buckets, Flex Window included
```

The rule for what belongs here: mechanics and design tokens, never domain. Date arithmetic stays
in `:countdown`; Health Connect stays in `:health`. `:core` holds no Room database and no
entities — only the app modules apply KSP.

`:core` exposes Compose and Glance as `api` dependencies, since both appear in its public
signatures.

## Building

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
./gradlew :countdown:assembleDebug
./gradlew :countdown:testDebugUnitTest
./gradlew :countdown:installDebug      # with a device or emulator attached

./gradlew :health:assembleDebug
./gradlew :health:testDebugUnitTest
./gradlew :health:installDebug
```

Or open the project root in Android Studio.

`compileSdk` is 36 across every module, because `connect-client` 1.1.0 sets `minCompileSdk=36`
in its AAR metadata and pulls AGP 8.9.1 up with it. `targetSdk` stays at 34.

## Repo layout

```
core/          shared library module
countdown/     the countdown app + its README
health/        the health app + its README
docs/          exported design stills, per app
mockup/        design canvas artboards — gitignored, local only
```
