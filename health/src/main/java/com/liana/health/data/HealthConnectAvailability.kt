package com.liana.health.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.health.connect.client.HealthConnectClient

/**
 * State 1 of the four-state machine in `health/plan.md`: is there a Health Connect to talk to at
 * all? Everything else — permission, background permission, actual data — is downstream of this
 * and is not asked until this says [Available].
 *
 * This is deliberately a separate type from "do we have permission". They fail for unrelated
 * reasons, they are fixed in different places, and collapsing them into one nullable would lose
 * the distinction the user most needs explained.
 */
sealed interface HealthConnectAvailability {

    /** Health Connect is present and current. The only state from which anything else is tried. */
    data object Available : HealthConnectAvailability

    /**
     * Installed, but older than the client library needs. Recoverable by the user, and the only
     * state that carries an action — see [providerUpdateIntent].
     */
    data object ProviderUpdateRequired : HealthConnectAvailability

    /**
     * No provider, and no route to one: below Android 9, or a device that simply does not ship
     * Health Connect. Nothing the app or the user can do, so the app should say that plainly
     * rather than offer a button that cannot help.
     */
    data object NotSupported : HealthConnectAvailability

    companion object {
        fun of(context: Context): HealthConnectAvailability =
            when (HealthConnectClient.getSdkStatus(context, PROVIDER_PACKAGE)) {
                HealthConnectClient.SDK_AVAILABLE -> Available
                HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> ProviderUpdateRequired
                else -> NotSupported
            }

        /**
         * The Play Store listing for the provider, with the `onerror=...` parameter Health
         * Connect's own docs specify: it is what makes the Store show an update rather than an
         * install page for a package that is already present.
         */
        fun providerUpdateIntent(): Intent = Intent(Intent.ACTION_VIEW).apply {
            setPackage(PLAY_STORE_PACKAGE)
            data = Uri.parse("market://details")
                .buildUpon()
                .appendQueryParameter("id", PROVIDER_PACKAGE)
                .appendQueryParameter("url", "healthconnect://onboarding")
                .build()
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        const val PROVIDER_PACKAGE = "com.google.android.apps.healthdata"
        private const val PLAY_STORE_PACKAGE = "com.android.vending"
    }
}
