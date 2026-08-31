package com.liana.health.widget

import androidx.datastore.preferences.core.intPreferencesKey
import com.liana.widgets.core.design.AccentPalette

/**
 * Per-widget state, stored in each instance's own slice of Glance's DataStore.
 *
 * Countdown keys its widgets to an occasion because that is what differs between them. Here every
 * widget shows the same weight, so the only thing worth varying is the colour — which is not
 * nothing: a widget that clashes with the wallpaper gets removed.
 *
 * Stored as a plain ARGB Int for the same reason [AccentPalette] holds Ints rather than Compose
 * colours: DataStore has no idea what a Color is.
 */
object WidgetPrefs {
    val AccentColor = intPreferencesKey("accent_color")

    /** What a widget placed before this setting existed — or bound without one — renders as. */
    val DefaultAccent: Int = AccentPalette.Cyan
}
