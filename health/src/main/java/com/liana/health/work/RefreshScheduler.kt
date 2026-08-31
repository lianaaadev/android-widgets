package com.liana.health.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Duration

/**
 * Owns the daily [RefreshWorker], and the decision not to schedule it at all.
 */
object RefreshScheduler {

    /**
     * Versioned, because the policy below is KEEP.
     *
     * KEEP means an already-enqueued job wins, so simply changing the period would leave every
     * phone that already had the hourly job running it forever — the new spec would never be
     * applied. Renaming the work makes the daily job a different job, and [LegacyNames] retires
     * the old one. Any future change to the period needs the same treatment.
     */
    private const val UniqueName = "weight-refresh-daily"

    private val LegacyNames = listOf("weight-refresh")

    /**
     * Once a day.
     *
     * Weight changes once a day at most, and usually less: an hourly job spent quota to redraw
     * the same number. The two things that make a daily period sufficient are that opening the
     * app is itself a full read, and that the midnight tick ages the recency line without any
     * read at all — so between background runs the widget is neither wrong nor idle.
     *
     * Unique work with [ExistingPeriodicWorkPolicy.KEEP], because this is called on every resume
     * and every reboot; REPLACE would restart the period each time and turn a daily job into one
     * that never fires for anyone who opens the app regularly.
     */
    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<RefreshWorker>(Duration.ofDays(1))
            // Quota exhaustion is the failure this backs off from, and it clears on a
            // timescale of hours rather than minutes.
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, Duration.ofMinutes(30))
            .build()

        val workManager = WorkManager.getInstance(context)
        LegacyNames.forEach(workManager::cancelUniqueWork)
        workManager.enqueueUniquePeriodicWork(UniqueName, ExistingPeriodicWorkPolicy.KEEP, request)
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
        val workManager = WorkManager.getInstance(context)
        (LegacyNames + UniqueName).forEach(workManager::cancelUniqueWork)
    }
}
