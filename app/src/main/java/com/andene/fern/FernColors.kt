package com.andene.fern

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/**
 * Semantic colour roles for the app's own marks (design roadmap D2): what the canvas draws over
 * the artwork (selection, guides, frames, bones, measurements…) and the overlays of play mode.
 * The artwork's colours, paper colours and the default palette are content, not chrome, and
 * stay outside these roles.
 */
data class FernColorRoles(
    /** Selection outlines, handles and marquees; the minimap's view box. */
    val selection: Color,
    /** Drawn guides: ruler, guide lines, symmetry. */
    val guide: Color,
    /** Something being cut, removed or recorded: the knife, the selection mask, recording. */
    val danger: Color,
    /** Measuring and other readouts drawn on the canvas. */
    val measure: Color,
    /** Frames and layout grids. */
    val frame: Color,
    /** Bones, perspective lines and other construction marks. */
    val construction: Color,
    /** The active bone or the active element of a construction. */
    val active: Color,
    /** Game guides: hitboxes, links. */
    val game: Color,
    /** Onion skin tints: frames before and after. */
    val before: Color,
    val after: Color,
    /** Neutral marks: connectors, content extents. */
    val neutral: Color,
    /** Backdrops behind overlay text and buttons (play HUD, badges), with their text. */
    val scrim: Color,
    val onScrim: Color,
)

/** Which set of roles is in use. */
enum class FernColorMode { STANDARD, HIGH_CONTRAST, COLOR_BLIND_SAFE }

object FernColors {
    val standard = FernColorRoles(
        selection = Color(0xFF1E88E5), guide = Color(0xFFE91E63), danger = Color(0xFFE53935), measure = Color(0xFF00897B),
        frame = Color(0xFF7E57C2), construction = Color(0xFF5E35B1), active = Color(0xFFFF6F00), game = Color(0xFF43A047),
        before = Color(0xFFE53935), after = Color(0xFF1E88E5), neutral = Color(0xFF607D8B), scrim = Color(0x99000000), onScrim = Color.White,
    )

    /** Full-strength, mutually distinct colours with solid scrims, for low vision. */
    val highContrast = FernColorRoles(
        selection = Color(0xFF0050FF), guide = Color(0xFFFF0090), danger = Color(0xFFFF0000), measure = Color(0xFF008060),
        frame = Color(0xFF8000FF), construction = Color(0xFF4000C0), active = Color(0xFFFF8000), game = Color(0xFF00A000),
        before = Color(0xFFFF0000), after = Color(0xFF0050FF), neutral = Color(0xFF404040), scrim = Color(0xE6000000), onScrim = Color.White,
    )

    /**
     * The Okabe-Ito palette, told apart with protanopia, deuteranopia and tritanopia; onion
     * skin uses orange and blue instead of red and blue.
     */
    val colorBlindSafe = FernColorRoles(
        selection = Color(0xFF0072B2), guide = Color(0xFFCC79A7), danger = Color(0xFFD55E00), measure = Color(0xFF009E73),
        frame = Color(0xFF56B4E9), construction = Color(0xFF7F7F7F), active = Color(0xFFE69F00), game = Color(0xFF009E73),
        before = Color(0xFFE69F00), after = Color(0xFF0072B2), neutral = Color(0xFF7F7F7F), scrim = Color(0x99000000), onScrim = Color.White,
    )

    /** The roles in use; set by [FernTheme], read while drawing (it's state, so the canvas redraws when it changes). */
    var current by mutableStateOf(standard)
        internal set

    fun roles(mode: FernColorMode) = when (mode) {
        FernColorMode.STANDARD -> standard
        FernColorMode.HIGH_CONTRAST -> highContrast
        FernColorMode.COLOR_BLIND_SAFE -> colorBlindSafe
    }
}
