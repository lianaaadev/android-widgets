package com.liana.health

import android.app.Application
import com.liana.health.data.HealthRepository
import com.liana.health.work.DailyTickScheduler

/**
 * Mirrors `CountdownApp`, minus the database. Countdown owns its data and needs Room; here
 * Health Connect owns every value and this app owns a cache — see `health/plan.md`, "No Room".
 *
 * The repository is created lazily because constructing the Health Connect client on a device
 * without a provider is pointless work on every cold start, and the availability check that
 * gates it runs on the first frame.
 */
class HealthApp : Application() {

    val repository: HealthRepository by lazy { HealthRepository(this) }

    override fun onCreate() {
        super.onCreate()
        // Costs nothing and touches no health data: it only makes the recency line roll over at
        // midnight. The hourly read is scheduled separately, and only once the background grant
        // is confirmed — see MainActivity.
        DailyTickScheduler.schedule(this)
    }
}
