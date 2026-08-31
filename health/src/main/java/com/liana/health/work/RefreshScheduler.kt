package com.liana.health.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Duration

/**
 * Owns the hourly [RefreshWorker], and the decision not to schedule it at all.
 */
object RefreshScheduler {

    private const val UniqueName = "weight-refresh"

    /**
     * One hour, as unique work with [ExistingPeriodicWorkPolicy.KEEP] so repeated calls — every
     * app resume, every reboot — do not restart the interval and quietly turn an hourly job into
     * a job that never fires.
     */
    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<RefreshWorker>(Duration.ofHours(1))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, Duration.ofMinutes(15))
            .build()

        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(UniqueName, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /**
     * Called when the background grant is missing or unavailable on this Android version.
     *
     * A Health Connect read from a backgrounded app without that grant does not throw — it
     * returns nothing, silently. Scheduling work that can only ever produce a clean-looking
     * empty result would spend quota to learn nothing and could convince the cache the user has
     * no weight, so the work is cancelled outright and the app says plainly that the widget only
     * updates while it is open.
     */
    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(UniqueName)
    }
}
