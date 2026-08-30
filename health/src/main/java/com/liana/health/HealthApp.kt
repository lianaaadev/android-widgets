package com.liana.health

import android.app.Application

/**
 * Mirrors `CountdownApp`, minus the database. Countdown owns its data and needs Room; here
 * Health Connect owns every value and the app owns a cache — see `health/plan.md`, "No Room".
 *
 * The `HealthRepository` this will hold lands in Phase 1, alongside the read path it wraps.
 */
class HealthApp : Application()
