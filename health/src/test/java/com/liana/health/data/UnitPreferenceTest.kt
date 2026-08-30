package com.liana.health.data

import org.junit.Assert.assertEquals
import org.junit.Test

class UnitPreferenceTest {

    @Test
    fun `kilograms pass through untouched`() {
        assertEquals("72.4", UnitPreference.Kilograms.format(72.4))
    }

    @Test
    fun `pounds use the exact avoirdupois definition`() {
        // 72.4 / 0.45359237 = 159.614..., and this is the number the cover-screen mockup is
        // sized against — five glyphs where kilograms needs four.
        assertEquals("159.6", UnitPreference.Pounds.format(72.4))
    }

    @Test
    fun `formatting is locale independent`() {
        // A device set to a comma-decimal locale must not render "72,4" into a widget laid out
        // for a period, nor into a string the trend concatenates.
        val previous = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.GERMANY)
            assertEquals("72.4", UnitPreference.Kilograms.format(72.4))
        } finally {
            java.util.Locale.setDefault(previous)
        }
    }
}
