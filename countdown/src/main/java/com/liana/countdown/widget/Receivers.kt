package com.liana.countdown.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/** The ordinary home-screen provider. */
class CountdownWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CountdownWidget()
}

/**
 * A second provider declared at the Flex Window's size and opted in to Samsung's cover screen.
 * It renders the same [CountdownWidget]; only the manifest declaration differs.
 *
 * Kept separate from the home-screen provider because the two need different `widgetCategory`
 * and size declarations, and because if the Samsung opt-in stops working on a future One UI the
 * home-screen widget is unaffected.
 */
class CountdownCoverWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CountdownWidget()
}
