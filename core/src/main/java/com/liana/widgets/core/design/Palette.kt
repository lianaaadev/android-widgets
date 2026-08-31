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
 * The greys a widget falls back to when the value it shows no longer applies — a countdown that
 * has already passed, a reading too old to trust.
 *
 * Both apps arrived at these same six values independently, under their own domain names
 * (`Past*` in countdown, `Stale*` in health). They are one design decision wearing two names, so
 * they live here instead. The names are deliberately about the *role* — surface, number, label —
 * rather than about what made the value stale, because that part differs per app.
 *
 * Day and night are hand-picked rather than derived from the neutrals above: a widget sits on
 * the user's wallpaper, not on [Ground], so the day variants are tuned against a light home
 * screen rather than against this palette's own surfaces.
 */
object Dimmed {
    val SurfaceDay = Color(0xFFE7E7EB)
    val SurfaceNight = Color(0xFF131319)
    val NumberDay = Color(0xFF9A9AA6)
    val NumberNight = Color(0xFF3A3A45)
    val LabelDay = Color(0xFF7C7C88)
    val LabelNight = Color(0xFF44444E)
}

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
