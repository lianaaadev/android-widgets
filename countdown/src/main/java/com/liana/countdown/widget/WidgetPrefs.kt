package com.liana.countdown.widget

import androidx.datastore.preferences.core.longPreferencesKey

/**
 * Per-widget state. Every instance of the widget stores its own copy, keyed by its [GlanceId],
 * which is what lets several countdown widgets sit on one home screen showing different things.
 */
object WidgetPrefs {
    val OccasionId = longPreferencesKey("occasion_id")
}
