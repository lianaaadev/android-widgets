package com.liana.widgets.core.design

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val DarkColors = darkColorScheme(
    primary = Color(AccentPalette.Default),
    onPrimary = Ink,
    background = Ground,
    onBackground = TextPrimary,
    surface = SurfaceCard,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceHigh,
    onSurfaceVariant = TextSecondary,
    outline = BorderSubtle,
    error = Color(AccentPalette.Coral),
    onError = Ink,
)

/**
 * The design calls for Archivo at weight 800. Shipping that means bundling the face (or wiring
 * up downloadable fonts); until then this leans on the platform's heaviest weight, which keeps
 * the hierarchy even though the numerals are a little less distinctive.
 */
private val WidgetTypography = Typography(
    displayLarge = TextStyle(fontWeight = FontWeight.Black, fontSize = 96.sp, letterSpacing = (-3).sp),
    displayMedium = TextStyle(fontWeight = FontWeight.Black, fontSize = 56.sp, letterSpacing = (-2).sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.Black, fontSize = 26.sp, letterSpacing = (-0.5).sp),
    titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 2.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 2.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp),
    bodySmall = TextStyle(fontWeight = FontWeight.Normal, fontSize = 12.sp),
)

/** The shared look for every app in this repo. Dark only — the widgets are designed against it. */
@Composable
fun WidgetTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        typography = WidgetTypography,
        content = content,
    )
}
