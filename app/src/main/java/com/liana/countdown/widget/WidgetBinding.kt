package com.liana.countdown.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.datastore.preferences.core.Preferences
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Points one widget instance at one occasion.
 *
 * This is the bridge that trips people up: the framework deals in `appWidgetId`, but Glance keys
 * its per-instance state by [androidx.glance.GlanceId]. Everything that creates a widget goes
 * through here — the configuration activity, and the pin-to-home-screen callback below.
 */
suspend fun bindWidget(context: Context, appWidgetId: Int, occasionId: Long) {
    val glanceId = GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId)
    updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs: Preferences ->
        prefs.toMutablePreferences().apply { this[WidgetPrefs.OccasionId] = occasionId }
    }
    CountdownWidget().update(context, glanceId)
}

/**
 * Asks the launcher to place a widget for [occasionId], skipping the trip through the widget
 * picker. Returns false when the launcher does not support pinning — several do not, and there
 * is no way to force it, so callers must fall back to telling the user to long-press their home
 * screen rather than leaving a dead button.
 */
fun requestPinWidget(context: Context, occasionId: Long): Boolean {
    val manager = AppWidgetManager.getInstance(context) ?: return false
    if (!manager.isRequestPinAppWidgetSupported) return false

    val callback = PendingIntent.getBroadcast(
        context,
        occasionId.toInt(),
        Intent(context, WidgetPinnedReceiver::class.java)
            .putExtra(WidgetPinnedReceiver.EXTRA_OCCASION_ID, occasionId),
        // Mutable so the launcher can add EXTRA_APPWIDGET_ID to the callback it fires.
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
    )

    return manager.requestPinAppWidget(
        ComponentName(context, CountdownWidgetReceiver::class.java),
        null,
        callback,
    )
}

/**
 * Fires once the launcher has actually placed a pinned widget, carrying the id it assigned.
 * Without this the widget would land on the home screen bound to nothing.
 */
class WidgetPinnedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val occasionId = intent.getLongExtra(EXTRA_OCCASION_ID, -1L)
        val appWidgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        )
        if (occasionId <= 0L || appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return

        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.Default).launch {
            try {
                bindWidget(appContext, appWidgetId, occasionId)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val EXTRA_OCCASION_ID = "com.liana.countdown.PIN_OCCASION_ID"
    }
}
