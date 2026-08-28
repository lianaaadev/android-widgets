package com.liana.widgets.core.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.getSystemService
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Wakes a widget on a schedule the system will honour without special permissions.
 *
 * Deliberately an *inexact* alarm. `SCHEDULE_EXACT_ALARM` is denied by default from Android 14
 * and Google Play restricts exact-alarm permissions to alarm-clock and calendar class apps;
 * nothing in this repo qualifies, and none of it needs the precision. Callers should cover the
 * case where Doze defers the alarm by also refreshing when their app resumes.
 *
 * Alarms do not survive a reboot, so the receiver on the other end has to reschedule itself —
 * on `BOOT_COMPLETED` as well as on each firing.
 */
object WidgetRefreshScheduler {

    fun schedule(
        context: Context,
        receiver: Class<out BroadcastReceiver>,
        action: String,
        requestCode: Int,
        at: Instant,
    ) {
        val alarmManager = context.getSystemService<AlarmManager>() ?: return
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC,
            at.toEpochMilli(),
            PendingIntent.getBroadcast(
                context,
                requestCode,
                Intent(context, receiver).setAction(action),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        )
    }

    /**
     * Just after the start of the next local day. A minute of slack keeps the alarm clear of the
     * midnight boundary itself, so a widget that recomputes "today" never does it a moment early.
     */
    fun nextMidnight(zone: ZoneId = ZoneId.systemDefault()): Instant =
        LocalDate.now(zone).plusDays(1).atStartOfDay(zone).plusMinutes(1).toInstant()
}
