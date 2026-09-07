package com.andene.fern.canvas

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStyle
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import kotlin.math.hypot

/**
 * The infinite drawing surface.
 *
 * Gesture model (matches the "one finger draws, two fingers navigate" convention used by
 * Endless Paper / most infinite-canvas sketch apps):
 *  - 1 active pointer: freehand drawing.
 *  - 2+ active pointers: pan (drag) and zoom (pinch), with the zoom centered on the
 *    pointers' midpoint so the content under your fingers stays put.
 *
 * Only strokes whose bounding box intersects the visible world-space rect are drawn each
 * frame, so the number of on-screen strokes stays bounded no matter how much content has
 * accumulated elsewhere on the infinite sheet.
 */
@Composable
fun DrawingCanvas(state: CanvasState, modifier: Modifier = Modifier) {
    Canvas(
        modifier = modifier
            .pointerInput(state) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    val screenCenter = Offset(size.width / 2f, size.height / 2f)

                    var mode = Mode.NONE
                    var prevCentroid = Offset.Zero
                    var prevSpan = 0f
                    var selectionGestureKind = SelectionGestureKind.OTHER

                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        val pointers = event.changes.filter { it.pressed }
                        val count = pointers.size
                        if (count == 0) break

                        if (count == 1) {
                            if (mode != Mode.DRAW) {
                                val downPos = pointers[0].position
                                val world = state.screenToWorld(downPos, screenCenter)
                                when (state.activeTool) {
                                    Tool.PEN -> state.beginStroke(world)
                                    Tool.ERASER -> state.beginErase(world)
                                    Tool.SELECT -> {
                                        val handles = selectionHandles(state, screenCenter)
                                        selectionGestureKind = when {
                                            handles != null && (downPos - handles.scaleHandle).getDistance() <= HANDLE_HIT_RADIUS_SCREEN -> {
                                                state.beginScaleSelection(world)
                                                SelectionGestureKind.SCALE
                                            }
                                            handles != null && (downPos - handles.rotateHandle).getDistance() <= HANDLE_HIT_RADIUS_SCREEN -> {
                                                state.beginRotateSelection(world)
                                                SelectionGestureKind.ROTATE
                                            }
                                            else -> {
                                                state.beginSelectGesture(world)
                                                SelectionGestureKind.OTHER
                                            }
                                        }
                                    }
                                }
                                mode = Mode.DRAW
                            } else {
                                val change = pointers[0]
                                if (change.positionChanged()) {
                                    val world = state.screenToWorld(change.position, screenCenter)
                                    when (state.activeTool) {
                                        Tool.PEN -> state.extendStroke(world)
                                        Tool.ERASER -> state.continueErase(world)
                                        Tool.SELECT -> when (selectionGestureKind) {
                                            SelectionGestureKind.SCALE -> state.continueScaleSelection(world)
                                            SelectionGestureKind.ROTATE -> state.continueRotateSelection(world)
                                            SelectionGestureKind.OTHER -> state.continueSelectGesture(world)
                                        }
                                    }
                                }
                            }
                            pointers[0].consume()
                        } else {
                            if (mode == Mode.DRAW) {
                                when (state.activeTool) {
                                    Tool.PEN -> state.endStroke()
                                    Tool.ERASER -> state.endErase()
                                    Tool.SELECT -> when (selectionGestureKind) {
                                        SelectionGestureKind.SCALE -> state.endScaleSelection()
                                        SelectionGestureKind.ROTATE -> state.endRotateSelection()
                                        SelectionGestureKind.OTHER -> state.endSelectGesture()
                                    }
                                }
                            }
                            val centroid = pointers.fold(Offset.Zero) { acc, c -> acc + c.position } / count.toFloat()
                            val span = pointers.fold(0f) { acc, c -> acc + hypot((c.position.x - centroid.x), (c.position.y - centroid.y)) } / count.toFloat()

                            if (mode != Mode.NAVIGATE) {
                                // Just transitioned into navigation: establish baseline, no jump this frame.
                                prevCentroid = centroid
                                prevSpan = span
                                mode = Mode.NAVIGATE
                            } else {
                                val panDelta = centroid - prevCentroid
                                if (panDelta != Offset.Zero) {
                                    state.panByScreenDelta(panDelta)
                                }
                                if (prevSpan > 0.001f && span > 0.001f) {
                                    val factor = (span / prevSpan).toDouble()
                                    state.zoomAround(centroid, screenCenter, factor)
                                }
                                prevCentroid = centroid
                                prevSpan = span
                            }
                            pointers.forEach { it.consume() }
                        }
                    }
                    if (mode == Mode.DRAW) {
                        when (state.activeTool) {
                            Tool.PEN -> state.endStroke()
                            Tool.ERASER -> state.endErase()
                            Tool.SELECT -> when (selectionGestureKind) {
                                SelectionGestureKind.SCALE -> state.endScaleSelection()
                                SelectionGestureKind.ROTATE -> state.endRotateSelection()
                                SelectionGestureKind.OTHER -> state.endSelectGesture()
                            }
                        }
                    }
                }
            }
    ) {
        state.viewportSize = size
        val screenCenter = Offset(size.width / 2f, size.height / 2f)
        val topLeftWorld = state.screenToWorld(Offset.Zero, screenCenter)
        val bottomRightWorld = state.screenToWorld(Offset(size.width, size.height), screenCenter)

        for (stroke in state.strokes) {
            // Reading revision here (and only here) is what subscribes this draw phase to
            // the stroke's mutations, so a redraw is triggered as each new point is added
            // mid-stroke rather than waiting for an unrelated pan/zoom to happen to redraw.
            stroke.revision
            if (!stroke.intersects(topLeftWorld.x, topLeftWorld.y, bottomRightWorld.x, bottomRightWorld.y)) continue
            val points = stroke.points
            if (points.isEmpty()) continue
            val rawWidthScreen = stroke.widthWorld * state.scale
            if (!rawWidthScreen.isFinite()) continue
            val widthScreen = rawWidthScreen.toFloat().coerceIn(1f, 1_000_000f)
            if (points.size == 1) {
                val p = state.worldToScreen(points[0], screenCenter)
                drawCircle(stroke.color, radius = widthScreen / 2f, center = p)
            } else if (points.size == 2) {
                // Not enough points for the midpoint construction below to add anything.
                val a = state.worldToScreen(points[0], screenCenter)
                val b = state.worldToScreen(points[1], screenCenter)
                drawLine(color = stroke.color, start = a, end = b, strokeWidth = widthScreen, cap = StrokeCap.Round)
            } else {
                // Smooths the raw point-to-point path via quadratic Bezier segments through
                // successive midpoints, using each raw point as the curve's control point -
                // the standard technique for turning faceted freehand input into a smooth
                // line without resampling or spline math.
                val screenPoints = points.map { state.worldToScreen(it, screenCenter) }
                val path = Path()
                path.moveTo(screenPoints[0].x, screenPoints[0].y)
                for (i in 1 until screenPoints.size - 1) {
                    val current = screenPoints[i]
                    val next = screenPoints[i + 1]
                    val midX = (current.x + next.x) / 2f
                    val midY = (current.y + next.y) / 2f
                    path.quadraticBezierTo(current.x, current.y, midX, midY)
                }
                path.lineTo(screenPoints.last().x, screenPoints.last().y)
                drawPath(
                    path = path,
                    color = stroke.color,
                    style = DrawStyle(width = widthScreen, cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }
        }

        for (stroke in state.selection) {
            stroke.revision // subscribe so a moved selection redraws its highlight live
            val topLeft = state.worldToScreen(WorldPoint(stroke.minX, stroke.minY), screenCenter)
            val bottomRight = state.worldToScreen(WorldPoint(stroke.maxX, stroke.maxY), screenCenter)
            drawRect(
                color = SELECTION_HIGHLIGHT_COLOR,
                topLeft = topLeft,
                size = Size(bottomRight.x - topLeft.x, bottomRight.y - topLeft.y),
                style = DrawStyle(width = 2f),
            )
        }

        state.marqueeRect?.let { (start, end) ->
            val a = state.worldToScreen(start, screenCenter)
            val b = state.worldToScreen(end, screenCenter)
            val topLeft = Offset(minOf(a.x, b.x), minOf(a.y, b.y))
            val size = Size(kotlin.math.abs(b.x - a.x), kotlin.math.abs(b.y - a.y))
            drawRect(color = MARQUEE_FILL_COLOR, topLeft = topLeft, size = size)
            drawRect(color = MARQUEE_BORDER_COLOR, topLeft = topLeft, size = size, style = DrawStyle(width = 2f))
        }

        if (state.activeTool == Tool.SELECT) {
            selectionHandles(state, screenCenter)?.let { handles ->
                drawLine(color = HANDLE_COLOR, start = handles.topCenter, end = handles.rotateHandle, strokeWidth = 2f)
                drawCircle(color = HANDLE_COLOR, radius = 8f, center = handles.scaleHandle)
                drawCircle(color = Color.White, radius = 4f, center = handles.scaleHandle)
                drawCircle(color = HANDLE_COLOR, radius = 8f, center = handles.rotateHandle)
                drawCircle(color = Color.White, radius = 4f, center = handles.rotateHandle)
            }
        }
    }
}

/** The selection's scale handle (bottom-right corner) and rotate handle (above top-center), in screen space. */
private data class SelectionHandles(val scaleHandle: Offset, val rotateHandle: Offset, val topCenter: Offset)

private fun selectionHandles(state: CanvasState, screenCenter: Offset): SelectionHandles? {
    val bounds = state.selectionBounds() ?: return null
    val (min, max) = bounds
    val scaleHandle = state.worldToScreen(max, screenCenter)
    val topCenter = state.worldToScreen(WorldPoint((min.x + max.x) / 2.0, min.y), screenCenter)
    val rotateHandle = Offset(topCenter.x, topCenter.y - ROTATE_HANDLE_OFFSET_SCREEN)
    return SelectionHandles(scaleHandle, rotateHandle, topCenter)
}

// Both on the §7 scale (Primary tier).
private const val HANDLE_HIT_RADIUS_SCREEN = 16f
private const val ROTATE_HANDLE_OFFSET_SCREEN = 32f

private val SELECTION_HIGHLIGHT_COLOR = Color(0xFF1E88E5)
private val MARQUEE_BORDER_COLOR = Color(0xFF1E88E5)
private val MARQUEE_FILL_COLOR = Color(0x1A1E88E5)
private val HANDLE_COLOR = Color(0xFF1E88E5)

private enum class Mode { NONE, DRAW, NAVIGATE }
private enum class SelectionGestureKind { SCALE, ROTATE, OTHER }
