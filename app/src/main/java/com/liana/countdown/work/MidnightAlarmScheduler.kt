package com.liana.countdown.work

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.getSystemService
import androidx.glance.appwidget.updateAll
import com.liana.countdown.widget.CountdownWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

/**
 * Wakes the widgets once a day so the number ticks down.
 *
 * Deliberately an *inexact* alarm. `SCHEDULE_EXACT_ALARM` is denied by default from Android 14
 * and Google Play restricts exact-alarm permissions to alarm-clock and calendar class apps; a
 * countdown does not qualify and does not need the precision. A few minutes of drift after
 * midnight is invisible, and [com.liana.countdown.ui.MainActivity.onResume] covers the case
 * where Doze defers the alarm for longer.
 */
object MidnightAlarmScheduler {

    private const val RequestCode = 1001

    fun schedule(context: Context) {
        val alarmManager = context.getSystemService<AlarmManager>() ?: return
        val nextMidnight = LocalDate.now()
            .plusDays(1)
            .atStartOfDay(ZoneId.systemDefault())
            .plusMinutes(1)
            .toInstant()
            .toEpochMilli()

        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC,
            nextMidnight,
            pendingIntent(context),
        )
    }

    private fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        RequestCode,
        Intent(context, MidnightReceiver::class.java).setAction(MidnightReceiver.ACTION_TICK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
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
