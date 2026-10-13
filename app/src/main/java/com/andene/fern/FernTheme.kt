package com.andene.fern

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.graphics.Color

/**
 * High-contrast colour schemes (roadmap item 69): pure black on white, or white on black, with
 * one strong accent - every text/background pair well beyond WCAG AA's 4.5:1 (black on white is
 * 21:1; the dark accent #FFD600 on black is about 15:1, the light one #002A80 on white about 12.8:1).
 * Outlines and secondary text are full-strength too, since the usual tinted greys are what
 * low-vision users lose first.
 */
val HighContrastLight: ColorScheme = lightColorScheme(
    primary = Color(0xFF002A80), onPrimary = Color.White,
    primaryContainer = Color(0xFF002A80), onPrimaryContainer = Color.White,
    secondaryContainer = Color.Black, onSecondaryContainer = Color.White,
    background = Color.White, onBackground = Color.Black,
    surface = Color.White, onSurface = Color.Black,
    surfaceVariant = Color.White, onSurfaceVariant = Color.Black,
    outline = Color.Black, outlineVariant = Color.Black,
)

val HighContrastDark: ColorScheme = darkColorScheme(
    primary = Color(0xFFFFD600), onPrimary = Color.Black,
    primaryContainer = Color(0xFFFFD600), onPrimaryContainer = Color.Black,
    secondaryContainer = Color.White, onSecondaryContainer = Color.Black,
    background = Color.Black, onBackground = Color.White,
    surface = Color.Black, onSurface = Color.White,
    surfaceVariant = Color.Black, onSurfaceVariant = Color.White,
    outline = Color.White, outlineVariant = Color.White,
)

/**
 * The app theme: the system's light or dark, in standard or high contrast; and the canvas's
 * marks ([FernColors]) in standard, high-contrast or colour-blind-safe colours.
 */
@Composable
fun FernTheme(highContrast: Boolean, colorBlindSafe: Boolean = false, uiSize: UiSize = UiSize.STANDARD, content: @Composable () -> Unit) {
    FernColors.current = FernColors.roles(
        when {
            colorBlindSafe -> FernColorMode.COLOR_BLIND_SAFE
            highContrast -> FernColorMode.HIGH_CONTRAST
            else -> FernColorMode.STANDARD
        },
    )
    val dark = isSystemInDarkTheme()
    val scheme = when {
        highContrast && dark -> HighContrastDark
        highContrast -> HighContrastLight
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    // The UI size (design roadmap D25) scales every dp; text keeps following the system's own font size on top.
    val density = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(density.density * uiSize.factor, density.fontScale)) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

/**
 * How big the whole UI is drawn (design roadmap D25), separate from the system font size. The
 * factors keep the §7 steps: at 1.5, 16 dp becomes 24 and 32 becomes 48. There is no size under
 * standard, so a control is never smaller than its 48 dp touch target.
 */
enum class UiSize(val factor: Float, @androidx.annotation.StringRes val label: Int) {
    STANDARD(1f, R.string.ui_size_standard),
    LARGE(1.25f, R.string.ui_size_large),
    LARGEST(1.5f, R.string.ui_size_largest),
}
