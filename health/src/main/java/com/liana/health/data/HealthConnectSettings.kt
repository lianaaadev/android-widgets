package com.liana.health.data

import android.content.Context
import android.content.Intent

/**
 * The escape hatch for a permission dialog that will not appear again.
 *
 * Health Connect stops offering the request dialog after the user dismisses it twice. From then
 * on the contract returns immediately with nothing granted, and an app that keeps calling it
 * just renders a button that visibly does nothing. There is no API to ask whether we have been
 * locked out, so the only option is to count our own attempts and switch routes.
 */
object HealthConnectSettings {

    /** Health Connect's own limit. Past this the dialog is gone and only its settings work. */
    private const val MaxPrompts = 2

    private const val Prefs = "health_connect_prompts"
    private const val KeyPrompts = "prompt_count"

    fun promptsExhausted(context: Context): Boolean = prefs(context).getInt(KeyPrompts, 0) >= MaxPrompts

    fun recordPrompt(context: Context) {
        val prefs = prefs(context)
        prefs.edit().putInt(KeyPrompts, prefs.getInt(KeyPrompts, 0) + 1).apply()
    }

    /** Granting elsewhere clears the count, so a later revoke gets a fresh pair of dialogs. */
    fun clearPrompts(context: Context) {
        prefs(context).edit().remove(KeyPrompts).apply()
    }

    /**
     * Health Connect's settings screen. The platform action exists from Android 14, where Health
     * Connect moved into the system; before that it is an activity inside the provider APK under
     * the AndroidX action. Try the newer one first and fall back.
     */
    fun settingsIntents(): List<Intent> = listOf(
        Intent("android.health.connect.action.HEALTH_HOME_SETTINGS"),
        Intent("androidx.health.ACTION_HEALTH_CONNECT_SETTINGS"),
    ).map { it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }

    private fun prefs(context: Context) =
        context.getSharedPreferences(Prefs, Context.MODE_PRIVATE)
}
