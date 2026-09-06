package com.andene.fern.canvas

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
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

                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        val pointers = event.changes.filter { it.pressed }
                        val count = pointers.size
                        if (count == 0) break

                        if (count == 1) {
                            if (mode != Mode.DRAW) {
                                if (mode == Mode.NAVIGATE) state.endStroke() // safety, no-op
                                val world = state.screenToWorld(pointers[0].position, screenCenter)
                                state.beginStroke(world)
                                mode = Mode.DRAW
                            } else {
                                val change = pointers[0]
                                if (change.positionChanged()) {
                                    val world = state.screenToWorld(change.position, screenCenter)
                                    state.extendStroke(world)
                                }
                            }
                            pointers[0].consume()
                        } else {
                            if (mode == Mode.DRAW) {
                                state.endStroke()
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
                                    val factor = span / prevSpan
                                    state.zoomAround(centroid, screenCenter, factor)
                                }
                                prevCentroid = centroid
                                prevSpan = span
                            }
                            pointers.forEach { it.consume() }
                        }
                    }
                    if (mode == Mode.DRAW) state.endStroke()
                }
            }
    ) {
        val screenCenter = Offset(size.width / 2f, size.height / 2f)
        val topLeftWorld = state.screenToWorld(Offset.Zero, screenCenter)
        val bottomRightWorld = state.screenToWorld(Offset(size.width, size.height), screenCenter)

        for (stroke in state.strokes) {
            if (!stroke.intersects(topLeftWorld.x, topLeftWorld.y, bottomRightWorld.x, bottomRightWorld.y)) continue
            val points = stroke.points
            if (points.isEmpty()) continue
            val widthScreen = (stroke.widthWorld * state.scale).coerceAtLeast(1f)
            if (points.size == 1) {
                val p = state.worldToScreen(points[0], screenCenter)
                drawCircle(stroke.color, radius = widthScreen / 2f, center = p)
            } else {
                for (i in 0 until points.size - 1) {
                    val a = state.worldToScreen(points[i], screenCenter)
                    val b = state.worldToScreen(points[i + 1], screenCenter)
                    drawLine(
                        color = stroke.color,
                        start = a,
                        end = b,
                        strokeWidth = widthScreen,
                        cap = StrokeCap.Round,
                    )
                }
            }
        }
    }
}

private enum class Mode { NONE, DRAW, NAVIGATE }
