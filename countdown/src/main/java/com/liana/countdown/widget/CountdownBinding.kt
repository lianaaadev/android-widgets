package com.liana.countdown.widget

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.glance.appwidget.GlanceAppWidget
import com.liana.widgets.core.widget.WidgetPinnedReceiver
import com.liana.widgets.core.widget.bindWidget
import com.liana.widgets.core.widget.requestPinWidget

/**
 * Countdown's side of the shared widget plumbing: which widget to update, and which key holds
 * the binding. The mechanics — the `appWidgetId` to `GlanceId` bridge, the pin callback — live
 * in `:core`.
 *
 * Named apart from the `:core` functions they wrap on purpose. Kotlin resolves a same-package
 * declaration ahead of an import, so a wrapper sharing its callee's name would call itself.
 */
suspend fun bindCountdownWidget(context: Context, appWidgetId: Int, occasionId: Long) {
    bindWidget(context, appWidgetId, CountdownWidget(), WidgetPrefs.OccasionId, occasionId)
}

fun requestPinCountdownWidget(context: Context, occasionId: Long): Boolean = requestPinWidget(
    context = context,
    provider = CountdownWidgetReceiver::class.java,
    callback = CountdownPinnedReceiver::class.java,
    entityId = occasionId,
)

/** The manifest-declared half of the pin callback; the shared base does the work. */
class CountdownPinnedReceiver : WidgetPinnedReceiver() {
    override val widget: GlanceAppWidget = CountdownWidget()
    override val key: Preferences.Key<Long> = WidgetPrefs.OccasionId
}
