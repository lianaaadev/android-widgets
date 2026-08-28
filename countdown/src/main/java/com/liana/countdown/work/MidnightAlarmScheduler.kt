package com.liana.countdown.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.updateAll
import com.liana.countdown.widget.CountdownWidget
import com.liana.widgets.core.widget.WidgetRefreshScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Wakes the widgets once a day so the number ticks down.
 *
 * The alarm mechanics — inexact, and why — live in [WidgetRefreshScheduler]. All that is
 * countdown-specific is the time of day. A few minutes of drift after midnight is invisible, and
 * [com.liana.countdown.ui.MainActivity.onResume] covers the case where Doze defers the alarm for
 * longer.
 */
object MidnightAlarmScheduler {

    private const val RequestCode = 1001

    fun schedule(context: Context) {
        WidgetRefreshScheduler.schedule(
            context = context,
            receiver = MidnightReceiver::class.java,
            action = MidnightReceiver.ACTION_TICK,
            requestCode = RequestCode,
            at = WidgetRefreshScheduler.nextMidnight(),
        )
    }
}

/**
 * Handles the daily tick and everything else that can invalidate a rendered countdown: a reboot
 * (alarms do not survive one), the clock being set, and the time zone changing under the user
 * as they travel.
 */
class MidnightReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.Default).launch {
            try {
                CountdownWidget().updateAll(appContext)
                MidnightAlarmScheduler.schedule(appContext)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_TICK = "com.liana.countdown.action.TICK"
    }
}
