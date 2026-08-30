package com.liana.health.data

import java.util.Locale

/**
 * kg or lb, one setting for the whole app. Not per-widget: two widgets showing the same weight in
 * two units is not something anyone wants, and making it per-widget would push the preference
 * into per-widget DataStore and grow the config screen a row for it.
 *
 * Values are stored in kilograms everywhere and converted at the point of display, so switching
 * units never rewrites anything.
 */
enum class UnitPreference(val label: String, val suffix: String) {
    Kilograms("Kilograms", "kg"),
    Pounds("Pounds", "lb"),
    ;

    fun fromKilograms(kilograms: Double): Double = when (this) {
        Kilograms -> kilograms
        // The international avoirdupois pound is defined as exactly 0.45359237 kg. Spelled out
        // rather than pulled from the Health Connect library so this stays a pure function that
        // unit tests can call without an Android runtime.
        Pounds -> kilograms / 0.45359237
    }

    /** One decimal place in both units: bathroom scales do not meaningfully resolve finer. */
    fun format(kilograms: Double): String =
        String.format(Locale.US, "%.1f", fromKilograms(kilograms))

    companion object {
        val Default = Kilograms
    }
}
