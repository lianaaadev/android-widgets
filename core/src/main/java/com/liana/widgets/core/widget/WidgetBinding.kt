package com.liana.widgets.core.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.datastore.preferences.core.Preferences
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Points one widget instance at one thing.
 *
 * This is the bridge that trips people up: the framework deals in `appWidgetId`, but Glance keys
 * its per-instance state by [androidx.glance.GlanceId]. Everything that creates a widget goes
 * through here — the configuration activity, and the pin-to-home-screen callback below.
 */
suspend fun <T : Any> bindWidget(
    context: Context,
    appWidgetId: Int,
    widget: GlanceAppWidget,
    key: Preferences.Key<T>,
    value: T,
) {
    val glanceId = GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId)
    updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs: Preferences ->
        prefs.toMutablePreferences().apply { this[key] = value }
    }
    widget.update(context, glanceId)
}

/**
 * Asks the launcher to place a widget already bound to [entityId], skipping the trip through the
 * widget picker. Returns false when the launcher does not support pinning — several do not, and
 * there is no way to force it, so callers must fall back to telling the user to long-press their
 * home screen rather than leaving a dead button.
 *
 * [callback] is the app's own [WidgetPinnedReceiver] subclass, which is what knows the widget
 * and the preference key to bind once the launcher reports back.
 */
fun requestPinWidget(
    context: Context,
    provider: Class<out GlanceAppWidgetReceiver>,
    callback: Class<out WidgetPinnedReceiver>,
    entityId: Long,
): Boolean {
    val manager = AppWidgetManager.getInstance(context) ?: return false
    if (!manager.isRequestPinAppWidgetSupported) return false

    val pendingIntent = PendingIntent.getBroadcast(
        context,
        entityId.toInt(),
        Intent(context, callback).putExtra(WidgetPinnedReceiver.EXTRA_ENTITY_ID, entityId),
        // Mutable so the launcher can add EXTRA_APPWIDGET_ID to the callback it fires.
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
    )

    return manager.requestPinAppWidget(ComponentName(context, provider), null, pendingIntent)
}

/**
 * Fires once the launcher has actually placed a pinned widget, carrying the id it assigned.
 * Without this the widget would land on the home screen bound to nothing.
 *
 * Abstract because the binding it performs is app-specific: a subclass supplies the widget to
 * update and the key to write. That subclass is what goes in the app's manifest — an abstract
 * receiver cannot be declared there.
 */
abstract class WidgetPinnedReceiver : BroadcastReceiver() {

    protected abstract val widget: GlanceAppWidget

    protected abstract val key: Preferences.Key<Long>

    override fun onReceive(context: Context, intent: Intent) {
        val entityId = intent.getLongExtra(EXTRA_ENTITY_ID, -1L)
        val appWidgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        )
        if (entityId <= 0L || appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return

        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.Default).launch {
            try {
                bindWidget(appContext, appWidgetId, widget, key, entityId)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val EXTRA_ENTITY_ID = "com.liana.widgets.core.PIN_ENTITY_ID"
    }
}
