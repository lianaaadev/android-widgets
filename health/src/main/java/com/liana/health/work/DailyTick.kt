package com.liana.health.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.updateAll
import com.liana.health.widget.WeightWidget
import com.liana.widgets.core.widget.WidgetRefreshScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Rolls "2 days ago" over at midnight without touching Health Connect at all.
 *
 * The value has not changed; its description has. Spending a read on that would be spending
 * quota to learn something the clock already knows. The alarm mechanics — inexact, and why —
 * live in `:core`'s [WidgetRefreshScheduler].
 */
object DailyTickScheduler {

    private const val RequestCode = 2001

    fun schedule(context: Context) {
        WidgetRefreshScheduler.schedule(
            context = context,
            receiver = DailyTickReceiver::class.java,
            action = DailyTickReceiver.ACTION_TICK,
            requestCode = RequestCode,
            at = WidgetRefreshScheduler.nextMidnight(),
        )
    }
}

/**
 * The daily tick, plus everything else that invalidates a rendered recency line: a reboot
 * (alarms do not survive one, and neither does WorkManager's first run), the clock being set,
 * and the time zone changing under the user as they travel.
 */
class DailyTickReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.Default).launch {
            try {
                WeightWidget().updateAll(appContext)
                DailyTickScheduler.schedule(appContext)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_TICK = "com.liana.health.action.TICK"
    }
}
