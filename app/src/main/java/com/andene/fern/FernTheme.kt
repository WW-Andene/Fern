package com.andene.fern

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
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
    // Dark or light chrome: the system's, or the paper's when the user asks (design roadmap D60).
    val systemDark = isSystemInDarkTheme()
    val dark = if (FernPaper.chromeFollowsPaper) FernPaper.dark ?: systemDark else systemDark
    val scheme = when {
        highContrast && dark -> HighContrastDark
        highContrast -> HighContrastLight
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    // The UI size (design roadmap D25) scales every dp; text keeps following the system's own font size on top.
    val density = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(density.density * uiSize.factor, density.fontScale)) {
        MaterialTheme(colorScheme = scheme, typography = FernTypography, shapes = FernShapes, content = content)
    }
}

/**
 * The open document's paper, for chrome that follows it (design roadmap D60): with the option on,
 * a dark paper gives dark bars and panels and a light paper light ones, so painting at night on
 * dark paper isn't framed by glaring white chrome. The canvas sets [dark]; Settings sets
 * [chromeFollowsPaper].
 */
object FernPaper {
    var dark: Boolean? by mutableStateOf(null)
    var chromeFollowsPaper: Boolean by mutableStateOf(false)
}

/**
 * Material's type scale moved onto the CLAUDE.md §7 scale (roadmap item 334). Material's own
 * defaults include 11, 22, 28, 36, 45 and 57 sp text and 20, 28, 36, 44, 52 and 64 sp line
 * heights; each size and line height here is rounded by §7.1 (a tie goes to the primary, so a
 * 20 sp line height becomes 16). Several styles collapse onto the same size; that is the
 * accepted cost of the scale. Nothing is under 12 sp (design roadmap D5). Letter spacing and
 * weights stay Material's.
 */
val FernTypography: Typography = Typography().run {
    fun TextStyle.on(size: Int, lineHeight: Int) = copy(fontSize = size.sp, lineHeight = lineHeight.sp)
    Typography(
        displayLarge = displayLarge.on(48, 64),
        displayMedium = displayMedium.on(48, 48),
        displaySmall = displaySmall.on(32, 48),
        headlineLarge = headlineLarge.on(32, 32),
        headlineMedium = headlineMedium.on(30, 32),
        headlineSmall = headlineSmall.on(24, 32),
        titleLarge = titleLarge.on(24, 32),
        titleMedium = titleMedium.on(16, 24),
        titleSmall = titleSmall.on(14, 16),
        bodyLarge = bodyLarge.on(16, 24),
        bodyMedium = bodyMedium.on(14, 16),
        bodySmall = bodySmall.on(12, 16),
        labelLarge = labelLarge.on(14, 16),
        labelMedium = labelMedium.on(12, 16),
        labelSmall = labelSmall.on(12, 16),
    )
}

/**
 * Corner radii by CLAUDE.md §7.3 (roadmap item 335): 0.24 × the height of the elements each
 * Material size class rounds - extra small for 24 dp elements (tooltips, small badges), small
 * for 32 dp (chips, menus' items), medium for 48 dp (fields, buttons, cards), large for 64 dp
 * (panels, sheets) and extra large for 96 dp and taller (dialogs). Material's defaults
 * (4, 8, 12, 16 and 28 dp) follow no height rule.
 */
val FernShapes: Shapes = Shapes(
    extraSmall = RoundedCornerShape(fernRadius(FernScale.s24)),
    small = RoundedCornerShape(fernRadius(FernScale.s32)),
    medium = RoundedCornerShape(fernRadius(FernScale.s48)),
    large = RoundedCornerShape(fernRadius(FernScale.s64)),
    extraLarge = RoundedCornerShape(fernRadius(FernScale.s96)),
)

/**
 * Expert density (design roadmap D90): for experienced users, more of the tools at once on a
 * phone - a second line of tools on the rail, and the "More" disclosures ([MoreOptions],
 * [MoreInline]) open from the start - without making anything smaller: every control keeps its
 * 48 dp touch target. Off by default (easy to come in). Settings sets [expert].
 */
object FernDensity {
    var expert: Boolean by mutableStateOf(false)
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
