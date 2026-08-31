package com.liana.health.data

/**
 * Turns the package that wrote a reading into something worth showing a person.
 *
 * The plan assumed Samsung Health writes the weight and hard coded that into the empty state.
 * On the first real phone the writer was a smart-scale app instead, which made the instructions
 * point at an app that had nothing to do with it. So the name comes from the data.
 *
 * Deliberately not resolved through PackageManager: reading another app's label needs it to be
 * visible to us, which on Android 11+ means either listing every candidate in `<queries>` or
 * asking for QUERY_ALL_PACKAGES. Neither is a trade worth making for a nicer string in one
 * sentence — an app that reads one number should not be able to enumerate what else is
 * installed. A short table of the usual writers, and the raw package name otherwise, costs
 * nothing and reveals nothing.
 */
object SourceApp {

    private val Known = mapOf(
        "com.sec.android.app.shealth" to "Samsung Health",
        "com.google.android.apps.fitness" to "Google Fit",
        "com.qingniu.fitindex" to "FitIndex",
        "com.fitbit.FitbitMobile" to "Fitbit",
        "com.withings.wiscale2" to "Withings",
        "com.myfitnesspal.android" to "MyFitnessPal",
        "com.garmin.android.apps.connectmobile" to "Garmin Connect",
        "com.renpho.health" to "Renpho",
        "cc.chenhe.weartools" to "Wear Tools",
    )

    /** A display name when we recognise it, the package name when we do not, null when unknown. */
    fun label(packageName: String?): String? {
        if (packageName.isNullOrBlank()) return null
        return Known[packageName] ?: packageName
    }
}
