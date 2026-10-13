package com.andene.fern

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration

/**
 * How the screen is arranged for the window's size (design roadmap D52), phone first:
 *
 * - **Compact width** (under 600 dp, Material's window size class: a phone held upright, or a
 *   narrow split screen): the tool rail along the bottom, panels as bottom sheets. This is the
 *   reference layout.
 * - **Wider** (a phone on its side, a tablet, a foldable open, a desktop window): the rail down
 *   the side and panels docked beside it, so the canvas keeps its full height.
 * - **Wide enough for more** (tablets in landscape, desktops): several open panels docked side by
 *   side, as many [PANEL_WIDTH_DP] panels as fit beside the rail while the canvas keeps at least
 *   [MIN_CANVAS_DP], up to [MAX_DOCKED_PANELS].
 *
 * Width decides, not orientation: a tablet held upright is 800 dp wide and gets the side rail
 * and docked panel, where an orientation rule would have given it a phone's sheet.
 */
data class FernLayout(
    /** The rail runs along the bottom and panels are sheets; otherwise the rail is on the side. */
    val railAtBottom: Boolean,
    /** How many open panels show docked at once beside the side rail; 1 with the bottom rail (a sheet). */
    val dockedPanels: Int,
) {
    companion object {
        /** Material's compact/medium boundary. */
        const val COMPACT_WIDTH_DP = 600
        const val RAIL_DP = 64
        const val PANEL_WIDTH_DP = 384
        const val MIN_CANVAS_DP = 384
        const val MAX_DOCKED_PANELS = 3

        /** The layout for a window [widthDp] wide. */
        fun of(widthDp: Int): FernLayout {
            if (widthDp < COMPACT_WIDTH_DP) return FernLayout(railAtBottom = true, dockedPanels = 1)
            val fit = (widthDp - RAIL_DP - MIN_CANVAS_DP) / PANEL_WIDTH_DP
            return FernLayout(railAtBottom = false, dockedPanels = fit.coerceIn(1, MAX_DOCKED_PANELS))
        }
    }
}

/** The layout for the current window, following resizes, rotation and folding. */
@Composable
fun fernLayout(): FernLayout = FernLayout.of(LocalConfiguration.current.screenWidthDp)
