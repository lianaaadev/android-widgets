package com.liana.health.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SourceAppTest {

    @Test
    fun `a known writer gets its display name`() {
        assertEquals("Samsung Health", SourceApp.label("com.sec.android.app.shealth"))
        assertEquals("FitIndex", SourceApp.label("com.qingniu.fitindex"))
    }

    @Test
    fun `an unknown writer falls back to its package, never to a guess`() {
        // Naming the wrong app is worse than naming none: it sends someone to check a setting in
        // an app that has nothing to do with their weight.
        assertEquals("com.example.scale", SourceApp.label("com.example.scale"))
    }

    @Test
    fun `nothing to name yields null rather than a placeholder`() {
        assertNull(SourceApp.label(null))
        assertNull(SourceApp.label(""))
        assertNull(SourceApp.label("   "))
    }
}
