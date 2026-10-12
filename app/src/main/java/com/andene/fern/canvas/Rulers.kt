package com.andene.fern.canvas

import com.andene.fern.FernColors

import com.andene.fern.FernType

import com.andene.fern.FernScale

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.platform.LocalDensity
import com.andene.fern.R
import kotlin.math.abs

/**
 * Screen rulers (roadmap item 99) along the top and left edges of the canvas, in world units
 * or the document's drawing scale unit (roadmap item 143), with ticks that rescale as the view zooms. As in Illustrator and Affinity: dragging out of
 * the top ruler pulls a horizontal guide, out of the left ruler a vertical one. Each guide has
 * a marker on the ruler along its length - drag the marker to move the guide, hold it to
 * remove the guide. Shown for an unrotated view (a turned view's axes don't run along the
 * screen's edges).
 */
@Composable
fun Rulers(state: CanvasState, modifier: Modifier = Modifier, topInset: Dp = FernScale.none) {
    if (state.rotation != 0.0) return
    val textMeasurer = rememberTextMeasurer()
    // Where each ruler's top-left sits on the canvas, so its local positions map to the canvas's.
    val density = LocalDensity.current
    val topPx = with(density) { topInset.toPx() }
    val rulerPx = with(density) { RULER_DP.dp.toPx() }
    val background = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
    val ink = MaterialTheme.colorScheme.onSurface
    val label = stringResource(R.string.rulers_label)
    Box(modifier.fillMaxSize()) {
        for (top in listOf(true, false)) {
            val origin = if (top) Offset(0f, topPx) else Offset(0f, topPx + rulerPx)
            Canvas(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = if (top) topInset else topInset + RULER_DP.dp)
                    .then(if (top) Modifier.fillMaxWidth().height(RULER_DP.dp) else Modifier.fillMaxHeight().width(RULER_DP.dp))
                    .semantics { contentDescription = label }
                    .pointerInput(state, top, origin) { rulerGestures(state, top, origin) },
            ) {
                drawRect(background)
                val view = state.viewportSize
                if (view.width <= 0f) return@Canvas
                val center = Offset(view.width / 2f, view.height / 2f)
                val minWorld = state.screenToWorld(Offset.Zero, center)
                val maxWorld = state.screenToWorld(Offset(view.width, view.height), center)
                // Ticks in the drawing scale's unit (roadmap item 143): the same tick rules, on
                // positions measured in that unit, then mapped back to world positions.
                val k = state.drawingScale.factor
                val step = rulerStep(state.scale / k)
                val ticks = if (top) rulerTicks(minWorld.x * k, maxWorld.x * k, state.scale / k) else rulerTicks(minWorld.y * k, maxWorld.y * k, state.scale / k)
                val thickness = if (top) size.height else size.width
                for (tick in ticks) {
                    val world = tick.position / k
                    val at = if (top) state.worldToScreen(WorldPoint(world, 0.0), center).x else state.worldToScreen(WorldPoint(0.0, world), center).y - origin.y
                    val length = if (tick.major) thickness * 0.5f else thickness * 0.25f
                    if (top) drawLine(ink, Offset(at, thickness - length), Offset(at, thickness)) else drawLine(ink, Offset(thickness - length, at), Offset(thickness, at))
                    if (tick.major) {
                        val text = textMeasurer.measure(rulerLabel(tick.position, step), TextStyle(fontSize = FernType.caption, color = ink))
                        drawText(text, topLeft = if (top) Offset(at + 2f, 1f) else Offset(1f, at + 2f))
                    }
                }
                // Markers for the guides that cross this ruler: vertical guides on the top one.
                for (guide in state.guides.filter { it.vertical == top }) {
                    val at = if (top) state.worldToScreen(WorldPoint(guide.position, 0.0), center).x else state.worldToScreen(WorldPoint(0.0, guide.position), center).y - origin.y
                    val marker = Path().apply {
                        if (top) { moveTo(at - MARKER_PX, 0f); lineTo(at + MARKER_PX, 0f); lineTo(at, MARKER_PX * 1.5f) }
                        else { moveTo(0f, at - MARKER_PX); lineTo(0f, at + MARKER_PX); lineTo(MARKER_PX * 1.5f, at) }
                        close()
                    }
                    drawPath(marker, RULER_GUIDE_COLOR)
                }
            }
        }
    }
}

/**
 * A press on a ruler: on a guide's marker, drag to move it or hold still to remove it; anywhere
 * else, dragging pulls out a new guide square to this ruler (horizontal from the top one).
 */
private suspend fun PointerInputScope.rulerGestures(state: CanvasState, top: Boolean, origin: Offset) {
    awaitEachGesture {
        val down = awaitFirstDown()
        val view = state.viewportSize
        if (view.width <= 0f) return@awaitEachGesture
        val center = Offset(view.width / 2f, view.height / 2f)
        // Positions here are the ruler's own; the canvas's are offset by where the ruler sits.
        fun world(p: Offset) = state.screenToWorld(p + origin, center)
        val along = if (top) down.position.x else down.position.y + origin.y
        val marker = state.guides.filter { it.vertical == top }.minByOrNull { g ->
            abs((if (top) state.worldToScreen(WorldPoint(g.position, 0.0), center).x else state.worldToScreen(WorldPoint(0.0, g.position), center).y) - along)
        }?.takeIf { g ->
            abs((if (top) state.worldToScreen(WorldPoint(g.position, 0.0), center).x else state.worldToScreen(WorldPoint(0.0, g.position), center).y) - along) <= MARKER_HIT_PX
        }
        down.consume()
        var guideId = marker?.id
        var moved = false
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (!change.pressed) break
            if (!moved && (change.position - down.position).getDistance() > MOVE_SLOP_PX) moved = true
            if (moved) {
                val w = world(change.position)
                if (guideId == null) guideId = state.addGuide(vertical = !top, position = if (top) w.y else w.x)
                val g = state.guides.firstOrNull { it.id == guideId } ?: break
                state.moveGuide(g.id, if (g.vertical) w.x else w.y)
            } else if (marker != null && change.uptimeMillis - down.uptimeMillis >= HOLD_TO_REMOVE_MILLIS) {
                state.removeGuide(marker.id)
                change.consume()
                // Wait out the rest of the press.
                while (awaitPointerEvent().changes.any { it.pressed }) Unit
                return@awaitEachGesture
            }
            change.consume()
        }
        if (moved) state.guidesSettled()
    }
}

private const val RULER_DP = 24
private const val MARKER_PX = 6f
private const val MARKER_HIT_PX = 24f
private const val MOVE_SLOP_PX = 8f
private const val HOLD_TO_REMOVE_MILLIS = 500L
internal val RULER_GUIDE_COLOR: androidx.compose.ui.graphics.Color get() = FernColors.current.measure
