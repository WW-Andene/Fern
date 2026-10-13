package com.andene.fern.canvas

import com.andene.fern.FernColors

import com.andene.fern.FernScale

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStyle
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import com.andene.fern.R

/**
 * A small always-visible overview of the whole canvas: the combined extent of every stroke,
 * plus a highlighted rectangle showing what the main viewport currently covers. Tapping
 * anywhere on it recenters the camera there (zoom unchanged) - a minimap you can't navigate
 * from is display-only and doesn't meet the archetype's baseline (CLAUDE.md §6). Its grip
 * moves it ([PaneDragHandle]).
 *
 * Renders nothing when the canvas is empty (there's no extent to show) or before the main
 * viewport has reported its size.
 *
 * Not updated point-by-point while a stroke is actively being drawn - it reacts to
 * [CanvasState.strokes] structurally (a stroke added/removed) and to camera changes, which is
 * enough for an overview; sub-pixel live accuracy during a single stroke isn't the point of
 * this feature.
 */
@Composable
fun Minimap(state: CanvasState, modifier: Modifier = Modifier) {
    val bounds = state.contentBounds()
    val viewport = state.viewportSize
    if (bounds == null || viewport.width <= 0f || viewport.height <= 0f) return

    val pane = rememberMovablePaneState("minimap")

    Surface(
        modifier = modifier.movablePane(pane).size(FernScale.s96),
        shape = MaterialTheme.shapes.medium,
        tonalElevation = FernScale.s4,
        shadowElevation = FernScale.s4,
        color = Color(0xFFF7F5F0),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(FernScale.s4)
                    .pointerInput(state) {
                        detectTapGestures { offset ->
                            val region = minimapRegion(state) ?: return@detectTapGestures
                            val world = mapMinimapPointToWorld(offset, size.width.toFloat(), size.height.toFloat(), region)
                            state.panTo(world)
                        }
                    }
            ) {
                val region = minimapRegion(state) ?: return@Canvas
                val (regionMin, regionSize) = region

                fun mapX(x: Double) = ((x - regionMin.x) / regionSize.x * this.size.width).toFloat()
                fun mapY(y: Double) = ((y - regionMin.y) / regionSize.y * this.size.height).toFloat()

                val (contentMin, contentMax) = bounds
                drawRect(
                    color = CONTENT_EXTENT_COLOR,
                    topLeft = Offset(mapX(contentMin.x), mapY(contentMin.y)),
                    size = Size(
                        (mapX(contentMax.x) - mapX(contentMin.x)).coerceAtLeast(1f),
                        (mapY(contentMax.y) - mapY(contentMin.y)).coerceAtLeast(1f),
                    ),
                )

                val screenCenter = Offset(viewport.width / 2f, viewport.height / 2f)
                val viewMin = state.screenToWorld(Offset.Zero, screenCenter)
                val viewMax = state.screenToWorld(Offset(viewport.width, viewport.height), screenCenter)
                drawRect(
                    color = VIEWPORT_COLOR,
                    topLeft = Offset(mapX(viewMin.x), mapY(viewMin.y)),
                    size = Size(
                        (mapX(viewMax.x) - mapX(viewMin.x)).coerceAtLeast(1f),
                        (mapY(viewMax.y) - mapY(viewMin.y)).coerceAtLeast(1f),
                    ),
                    style = DrawStyle(width = 2f),
                )
            }
            PaneDragHandle(pane, stringResource(R.string.minimap_move), Modifier.align(Alignment.TopCenter))
        }
    }
}

/**
 * The world-space region the minimap maps its pixels to: the union of the content's bounding
 * box and the current viewport, so the viewport indicator is always visible even when it's
 * larger than the content (e.g. zoomed far out) or outside it (panned away). Returns
 * (regionMin, regionSize) or null if there's nothing to show.
 */
private fun minimapRegion(state: CanvasState): Pair<WorldPoint, WorldPoint>? {
    val bounds = state.contentBounds() ?: return null
    val viewport = state.viewportSize
    if (viewport.width <= 0f || viewport.height <= 0f) return null
    val (contentMin, contentMax) = bounds
    val screenCenter = Offset(viewport.width / 2f, viewport.height / 2f)
    val viewMin = state.screenToWorld(Offset.Zero, screenCenter)
    val viewMax = state.screenToWorld(Offset(viewport.width, viewport.height), screenCenter)

    val regionMinX = minOf(contentMin.x, viewMin.x)
    val regionMinY = minOf(contentMin.y, viewMin.y)
    val regionMaxX = maxOf(contentMax.x, viewMax.x)
    val regionMaxY = maxOf(contentMax.y, viewMax.y)
    val regionWidth = (regionMaxX - regionMinX).coerceAtLeast(1e-6)
    val regionHeight = (regionMaxY - regionMinY).coerceAtLeast(1e-6)
    return WorldPoint(regionMinX, regionMinY) to WorldPoint(regionWidth, regionHeight)
}

private fun mapMinimapPointToWorld(
    offset: Offset,
    minimapWidth: Float,
    minimapHeight: Float,
    region: Pair<WorldPoint, WorldPoint>,
): WorldPoint {
    val (regionMin, regionSize) = region
    return WorldPoint(
        x = regionMin.x + (offset.x / minimapWidth) * regionSize.x,
        y = regionMin.y + (offset.y / minimapHeight) * regionSize.y,
    )
}

private val CONTENT_EXTENT_COLOR: Color get() = FernColors.current.neutral.copy(alpha = 0.5f)
private val VIEWPORT_COLOR: Color get() = FernColors.current.selection
