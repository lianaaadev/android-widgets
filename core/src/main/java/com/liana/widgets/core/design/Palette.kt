package com.liana.widgets.core.design

import androidx.compose.ui.graphics.Color

/**
 * The one palette, shared by the app screens and by the widgets.
 *
 * These used to exist twice — once here for Compose and once privately inside the widget under
 * night-suffixed names — which is exactly the kind of pair that drifts. Glance cannot take a
 * `MaterialTheme`, so the tokens live as plain [Color]s that both worlds can read.
 */
val Ground = Color(0xFF0E0E12)
val SurfaceCard = Color(0xFF15151C)
val SurfaceHigh = Color(0xFF1E1E27)
val BorderSubtle = Color(0xFF24242F)
val TextPrimary = Color(0xFFF5F5F7)
val TextSecondary = Color(0xFF9C9CAB)
val TextTertiary = Color(0xFF63636F)
val TextFaint = Color(0xFF4E4E5A)
val Ink = Color(0xFF14141A)

/**
 * The accent a user can pick per widget. Plain ARGB ints rather than Compose or Glance colours,
 * because the same value has to survive a trip through Room and through DataStore, neither of
 * which knows what a [Color] is.
 */
object AccentPalette {
    val Amber = 0xFFFFB43C.toInt()
    val Coral = 0xFFFF7A6B.toInt()
    val Pink = 0xFFFF8FC4.toInt()
    val Violet = 0xFFB79BFF.toInt()
    val Cyan = 0xFF5BD1EA.toInt()
    val Lime = 0xFFA9E05A.toInt()

    val Default = Amber

    val all = listOf(Amber, Coral, Pink, Violet, Cyan, Lime)
}
