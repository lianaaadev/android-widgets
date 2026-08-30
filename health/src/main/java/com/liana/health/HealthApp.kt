package com.liana.health

import android.app.Application
import com.liana.health.data.HealthRepository

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
}
