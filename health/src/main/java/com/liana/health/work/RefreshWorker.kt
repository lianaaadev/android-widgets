package com.liana.health.work

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.liana.health.HealthApp
import com.liana.health.data.HealthConnectAvailability
import com.liana.health.widget.WeightWidget

/**
 * The daily background read.
 *
 * Weight changes once a day at most, and usually less, so anything faster spends quota to
 * display the same number. That matters because Google publishes no numeric rate limits — only
 * that background quotas are stricter than foreground ones, that there are both periodic and
 * daily ceilings, and that apps should back off rather than retry tightly.
 *
 * Even this run is usually free of record reads: see
 * [com.liana.health.data.HealthRepository.refreshIfChanged].
 */
class RefreshWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as HealthApp

        // Nothing to read and nothing to retry: the provider is gone or too old. Succeed so the
        // periodic work stays scheduled — it becomes useful again the moment one is installed.
        if (HealthConnectAvailability.of(applicationContext) != HealthConnectAvailability.Available) {
            return Result.success()
        }

        val outcome = app.repository.refreshIfChanged()

        outcome.exceptionOrNull()?.let { error ->
            // IllegalStateException is how Health Connect reports quota exhaustion. Retry, so
            // WorkManager's exponential backoff applies — never failure, which would drop the
            // run, and never a tight loop, which is what earned the quota block.
            return if (error is IllegalStateException) Result.retry() else Result.failure()
        }

        // Redraw regardless of whether the value moved: the recency line ages even when the
        // number does not.
        WeightWidget().updateAll(applicationContext)
        return Result.success()
    }
}
