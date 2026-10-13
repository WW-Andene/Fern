package com.andene.fern.canvas

import androidx.annotation.StringRes
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStyle
import com.andene.fern.R

/*
 * On-canvas handles (roadmap item 171): one way to show, hit-test and drag the points a finger
 * grabs on the canvas - the selection's scale and rotate handles, node editing's nodes, a warp's
 * nodes and pins, vanishing points, guide handles - instead of each feature drawing its own dots
 * at its own size and catching them at its own radius. A feature lists its handles in
 * [CanvasState.canvasHandles]; the canvas draws them all alike and, on a press, gives the nearest
 * one within reach the gesture before any tool sees it.
 */

/** How a handle looks: a round one moves a point; a square one is a node of a path. */
enum class HandleShape { ROUND, SQUARE }

/**
 * A point on the canvas a finger can grab and drag. [press] gets the world point pressed,
 * [drag] each new one, [release] the lift. [screenOffset] places it that many screen pixels from
 * [position] - for a handle that sits beside what it moves, like the selection's rotate handle -
 * whatever the zoom. [live] is where it is now (it moves while dragged; [position] is where it
 * was when listed). [value] is what it sets when that isn't simply its position (roadmap item
 * 172): shown beside it while dragged, and typed in when it's held.
 */
class CanvasHandle(
    val position: WorldPoint,
    val shape: HandleShape = HandleShape.ROUND,
    val screenOffset: Offset = Offset.Zero,
    val live: () -> WorldPoint = { position },
    val value: HandleValue? = null,
    val press: (WorldPoint) -> Unit = {},
    val drag: (WorldPoint) -> Unit,
    val release: () -> Unit = {},
)

/** One number a handle sets: its name, its current value (read live) and its unit. */
class HandleField(@StringRes val label: Int, val read: () -> Double, val unit: String)

/** The numbers a handle sets (roadmap item 172), and how typed ones are applied, in the fields' order. */
class HandleValue(val fields: List<HandleField>, val apply: (List<Double>) -> Unit)

/**
 * What [this] handle sets: its own [CanvasHandle.value], or else its position, X and Y in the
 * drawing's unit ([scale]), applied by dragging it there - press, one drag, release - so a typed
 * position does exactly what a drag to it would.
 */
internal fun CanvasHandle.typedValue(scale: DrawingScale?): HandleValue = value ?: HandleValue(
    listOf(
        HandleField(R.string.handle_x, { live().x * scale.factor }, scale.symbol),
        HandleField(R.string.handle_y, { live().y * scale.factor }, scale.symbol),
    ),
) { (x, y) ->
    val from = live()
    press(from)
    drag(WorldPoint(x / scale.factor, y / scale.factor))
    release()
}

/**
 * The handle a press at [screen] grabs: the nearest of [handles] within [radiusPx] (half a 48 dp
 * touch target, design roadmap D39), or null. [toScreen] places a world point on the screen.
 */
internal fun handleAt(handles: List<CanvasHandle>, screen: Offset, toScreen: (WorldPoint) -> Offset, radiusPx: Float): CanvasHandle? =
    handles.map { it to (toScreen(it.position) + it.screenOffset - screen).getDistance() }
        .filter { (_, distance) -> distance <= radiusPx }
        .minByOrNull { (_, distance) -> distance }
        ?.first

/** A handle being dragged, as the gesture the canvas runs instead of the tool's; [handle] shows its value meanwhile. */
internal class HandleGesture(val handle: CanvasHandle, pressedAt: WorldPoint) : ToolGesture {
    init {
        handle.press(pressedAt)
    }

    override fun move(point: GesturePoint) = handle.drag(point.world)
    override fun release() = handle.release()
}

/** Draws [handles] the one way: a ring with a white centre, or a white square with an outline, in [color]. */
internal fun DrawScope.drawHandles(handles: List<CanvasHandle>, toScreen: (WorldPoint) -> Offset, color: Color) {
    for (handle in handles) {
        val at = toScreen(handle.position) + handle.screenOffset
        when (handle.shape) {
            HandleShape.ROUND -> {
                drawCircle(color, radius = HANDLE_RADIUS_PX, center = at)
                drawCircle(Color.White, radius = HANDLE_RADIUS_PX / 2, center = at)
            }
            HandleShape.SQUARE -> {
                val corner = at - Offset(HANDLE_RADIUS_PX * 3 / 4, HANDLE_RADIUS_PX * 3 / 4)
                val side = Size(HANDLE_RADIUS_PX * 3 / 2, HANDLE_RADIUS_PX * 3 / 2)
                drawRect(Color.White, topLeft = corner, size = side)
                drawRect(color, topLeft = corner, size = side, style = DrawStyle(width = 2f))
            }
        }
    }
}

// Drawn size in screen px: a handle 16 px across (a §7 primary), its hit area still the 48 dp target.
private const val HANDLE_RADIUS_PX = 8f

/** How far above the selection's top edge its rotate handle sits, in screen px (a §7 primary). */
internal const val ROTATE_HANDLE_OFFSET_SCREEN = 32f
