package com.liana.health.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/** The ordinary home-screen provider. */
class WeightWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WeightWidget()
}

/**
 * A second provider declared at the Flex Window's size and opted in to Samsung's cover screen.
 * It renders the same [WeightWidget]; only the manifest declaration differs.
 *
 * Kept separate from the home-screen provider for the same reason countdown keeps its own: the
 * two need different `widgetCategory` and size declarations, and if the Samsung opt-in stops
 * working on some future One UI, the home-screen widget is unaffected.
 */
class WeightCoverWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WeightWidget()
}
