package com.liana.widgets.core.widget

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp

/**
 * The size buckets every widget in this repo is built against, for `SizeMode.Responsive`.
 *
 * 2x2 is the floor. A 1x1 could only carry a number and a truncated word, which is not enough to
 * tell two widgets apart on the same home screen.
 */
object WidgetSizes {
    val Medium = DpSize(168.dp, 168.dp)
    val Wide = DpSize(344.dp, 168.dp)

    /**
     * Samsung's documented Flex Window size for the Galaxy Z Flip cover screen. Reaching the
     * cover screen also needs the `samsung-appwidget-provider` opt-in in the provider XML and a
     * second receiver declared at this size — see the countdown app's manifest for the shape.
     */
    val Cover = DpSize(352.dp, 339.dp)
}
