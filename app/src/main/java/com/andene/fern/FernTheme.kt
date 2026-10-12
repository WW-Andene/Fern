package com.andene.fern

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
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

/** The app theme: the system's light or dark, in standard or high contrast. */
@Composable
fun FernTheme(highContrast: Boolean, content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val scheme = when {
        highContrast && dark -> HighContrastDark
        highContrast -> HighContrastLight
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
