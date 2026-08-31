package com.liana.health.widget

import android.content.Context
import com.liana.widgets.core.widget.bindWidget

/**
 * Writes an accent into one widget instance's state and redraws it, going through `:core`'s
 * [bindWidget] so the appWidgetId-to-GlanceId translation stays in one place.
 */
suspend fun bindWeightWidget(context: Context, appWidgetId: Int, accentColor: Int) {
    bindWidget(
        context = context,
        appWidgetId = appWidgetId,
        widget = WeightWidget(),
        key = WidgetPrefs.AccentColor,
        value = accentColor,
    )
}
