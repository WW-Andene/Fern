package com.andene.fern.canvas

import android.graphics.BitmapFactory
import android.view.MotionEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStyle
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

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
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun DrawingCanvas(state: CanvasState, modifier: Modifier = Modifier) {
    // Side-channel for stylus pressure/tilt/orientation: Compose's own pointer-input APIs
    // (used below, unchanged) don't expose tilt at all, and pressure only patchily, so this
    // observes the raw MotionEvent directly. It never consumes the event (always returns
    // false), so the existing gesture handling below still sees and processes every touch
    // exactly as before - this only adds a way to read pressure/tilt for pointer 0 at the
    // moment each point is captured. Untested against real stylus hardware.
    val stylusSample = remember { StylusSample() }
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val context = LocalContext.current
    // Decoded lazily and cached by file name: re-decoding from disk every frame would be
    // wasteful, and images never change once placed (a new image is a new ImageItem/file).
    val imageBitmapCache = remember { mutableMapOf<String, ImageBitmap>() }

    Canvas(
        modifier = modifier
            .pointerInteropFilter { event ->
                if (event.pointerCount > 0) {
                    val isStylus = event.getToolType(0) == MotionEvent.TOOL_TYPE_STYLUS
                    stylusSample.pressure = event.getPressure(0)
                    stylusSample.tilt = if (isStylus) event.getAxisValue(MotionEvent.AXIS_TILT, 0) else 0f
                    stylusSample.orientation = if (isStylus) event.getOrientation(0) else 0f
                }
                false
            }
            .pointerInput(state) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    val screenCenter = Offset(size.width / 2f, size.height / 2f)

                    var mode = Mode.NONE
                    var prevCentroid = Offset.Zero
                    var prevSpan = 0f
                    var selectionGestureKind = SelectionGestureKind.OTHER
                    var wasStylusActive = false

                    fun endActiveDrawAction() {
                        when (state.activeTool) {
                            Tool.PEN -> state.endStroke()
                            Tool.ERASER -> state.endErase()
                            Tool.SHAPE -> state.endShape()
                            Tool.FILL -> {} // one-shot on pointer-down, nothing to end
                            Tool.TEXT -> {} // one-shot on pointer-down, nothing to end
                            Tool.RULER -> state.endRulerGesture()
                            Tool.SELECT -> when (selectionGestureKind) {
                                SelectionGestureKind.SCALE -> state.endScaleSelection()
                                SelectionGestureKind.ROTATE -> state.endRotateSelection()
                                SelectionGestureKind.OTHER -> state.endSelectGesture()
                            }
                        }
                    }

                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        val allPressed = event.changes.filter { it.pressed }
                        // Palm rejection: once a stylus is down, ignore finger pointers
                        // entirely (a resting palm reads as ordinary touch input) rather
                        // than letting them register as a second drawing/navigation
                        // pointer. Consumed so nothing else treats them as unhandled.
                        val stylusActive = allPressed.any { it.type == PointerType.Stylus }
                        val pointers = if (stylusActive) {
                            val (kept, rejected) = allPressed.partition { it.type != PointerType.Touch }
                            rejected.forEach { it.consume() }
                            kept
                        } else {
                            allPressed
                        }

                        if (wasStylusActive && !stylusActive) {
                            // Stylus just lifted; any remaining fingers (previously
                            // rejected) must start a fresh gesture, not silently continue
                            // whatever the stylus was doing.
                            if (mode == Mode.DRAW) endActiveDrawAction()
                            mode = Mode.NONE
                        }
                        wasStylusActive = stylusActive

                        val count = pointers.size
                        if (count == 0) break

                        if (count == 1) {
                            if (mode != Mode.DRAW) {
                                val downPos = pointers[0].position
                                val world = state.screenToWorld(downPos, screenCenter)
                                when (state.activeTool) {
                                    Tool.PEN -> state.beginStroke(world, stylusSample.pressure, stylusSample.tilt, stylusSample.orientation)
                                    Tool.ERASER -> state.beginErase(world)
                                    Tool.SHAPE -> state.beginShape(world)
                                    Tool.FILL -> state.fillAt(world)
                                    Tool.TEXT -> state.beginTextEdit(world)
                                    Tool.RULER -> state.beginRulerGesture(world)
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
                                        Tool.PEN -> state.extendStroke(world, stylusSample.pressure, stylusSample.tilt, stylusSample.orientation)
                                        Tool.ERASER -> state.continueErase(world)
                                        Tool.SHAPE -> state.continueShape(world)
                                        Tool.FILL -> {} // one-shot; ignore drag
                                        Tool.TEXT -> {} // one-shot; ignore drag
                                        Tool.RULER -> state.continueRulerGesture(world)
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
                            if (mode == Mode.DRAW) endActiveDrawAction()
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
                    if (mode == Mode.DRAW) endActiveDrawAction()
                }
            }
    ) {
        state.viewportSize = size
        val screenCenter = Offset(size.width / 2f, size.height / 2f)
        val topLeftWorld = state.screenToWorld(Offset.Zero, screenCenter)
        val bottomRightWorld = state.screenToWorld(Offset(size.width, size.height), screenCenter)

        // Drawn first (behind strokes/text), matching the common "insert a reference image,
        // draw over it" workflow.
        for (item in state.imageItems) {
            var bitmap = imageBitmapCache[item.fileName]
            if (bitmap == null) {
                val file = CanvasStorage.imageFile(context, item.fileName)
                val decoded = BitmapFactory.decodeFile(file.absolutePath)
                if (decoded == null) continue
                bitmap = decoded.asImageBitmap()
                imageBitmapCache[item.fileName] = bitmap
            }
            val topLeft = state.worldToScreen(item.position, screenCenter)
            val bottomRight = state.worldToScreen(
                WorldPoint(item.position.x + item.widthWorld, item.position.y + item.heightWorld),
                screenCenter,
            )
            val widthPx = (bottomRight.x - topLeft.x).roundToInt()
            val heightPx = (bottomRight.y - topLeft.y).roundToInt()
            if (widthPx <= 0 || heightPx <= 0) continue
            drawImage(
                image = bitmap,
                dstOffset = IntOffset(topLeft.x.roundToInt(), topLeft.y.roundToInt()),
                dstSize = IntSize(widthPx, heightPx),
            )
        }

        for (stroke in state.strokes) {
            // Reading revision here (and only here) is what subscribes this draw phase to
            // the stroke's mutations, so a redraw is triggered as each new point is added
            // mid-stroke rather than waiting for an unrelated pan/zoom to happen to redraw.
            stroke.revision
            if (!stroke.intersects(topLeftWorld.x, topLeftWorld.y, bottomRightWorld.x, bottomRightWorld.y)) continue
            val points = stroke.points
            if (points.isEmpty()) continue
            val screenPoints = points.map { state.worldToScreen(it, screenCenter) }
            if (stroke.filled) {
                val path = Path()
                path.moveTo(screenPoints[0].x, screenPoints[0].y)
                for (i in 1 until screenPoints.size) path.lineTo(screenPoints[i].x, screenPoints[i].y)
                path.close()
                drawPath(path = path, color = stroke.color)
                continue
            }
            val rawWidthScreen = stroke.widthWorld * state.scale
            if (!rawWidthScreen.isFinite()) continue
            val widthScreen = rawWidthScreen.toFloat().coerceIn(1f, 1_000_000f)
            drawStroke(stroke, screenPoints, widthScreen, stroke.blendMode.toComposeBlendMode())
        }

        for (item in state.textItems) {
            val screenPos = state.worldToScreen(item.position, screenCenter)
            val fontSizePx = (item.fontSizeWorld * state.scale)
            if (!fontSizePx.isFinite() || fontSizePx <= 0.0) continue
            val fontSizeSp = with(density) { fontSizePx.toFloat().toSp() }
            val layout = textMeasurer.measure(text = item.text, style = TextStyle(color = item.color, fontSize = fontSizeSp))
            drawText(textLayoutResult = layout, topLeft = screenPos)
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
            val size = Size(abs(b.x - a.x), abs(b.y - a.y))
            drawRect(color = MARQUEE_FILL_COLOR, topLeft = topLeft, size = size)
            drawRect(color = MARQUEE_BORDER_COLOR, topLeft = topLeft, size = size, style = DrawStyle(width = 2f))
        }

        state.shapePreview?.let { (start, end) ->
            val screenPoints = generateShapePoints(state.activeShapeKind, start, end).map { state.worldToScreen(it, screenCenter) }
            val rawWidthScreen = state.activeWidthWorld
            val widthScreen = rawWidthScreen.toFloat().coerceIn(1f, 1_000_000f)
            val path = Path()
            path.moveTo(screenPoints[0].x, screenPoints[0].y)
            for (i in 1 until screenPoints.size) path.lineTo(screenPoints[i].x, screenPoints[i].y)
            drawPath(path = path, color = state.activeColor, style = DrawStyle(width = widthScreen, cap = StrokeCap.Round, join = StrokeJoin.Round))
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

        if (state.rulerActive) {
            state.rulerLine?.let { (start, end) ->
                val startScreen = state.worldToScreen(start, screenCenter)
                val endScreen = state.worldToScreen(end, screenCenter)
                drawLine(color = RULER_COLOR, start = startScreen, end = endScreen, strokeWidth = 4f)
                if (state.activeTool == Tool.RULER) {
                    drawCircle(color = RULER_COLOR, radius = 10f, center = startScreen)
                    drawCircle(color = Color.White, radius = 5f, center = startScreen)
                    drawCircle(color = RULER_COLOR, radius = 10f, center = endScreen)
                    drawCircle(color = Color.White, radius = 5f, center = endScreen)
                }
            }
        }
    }

    state.pendingTextEdit?.let { pending ->
        TextEditDialog(
            initialText = pending.initialText,
            onConfirm = { text -> state.confirmTextEdit(text) },
            onDismiss = { state.cancelTextEdit() },
        )
    }
}

/** Mutable holder for the most recent raw-MotionEvent pressure/tilt/orientation of pointer 0. */
private class StylusSample {
    var pressure = 1f
    var tilt = 0f
    var orientation = 0f
}

/**
 * True if this stroke actually has stylus data worth rendering: finger input (or a saved
 * stroke from before pressure/tilt existed) reports a uniform pressure of 1.0 and zero tilt
 * at every point, which is indistinguishable from "no data" - in that case, rendering must
 * fall back to the plain smoothed path so ordinary finger-drawn strokes look exactly as they
 * always have.
 */
private fun hasStylusVariation(stroke: Stroke): Boolean {
    val minPressure = stroke.pressures.minOrNull() ?: 1f
    val maxPressure = stroke.pressures.maxOrNull() ?: 1f
    val maxTilt = stroke.tilts.maxOrNull() ?: 0f
    return (maxPressure - minPressure) > 0.05f || maxTilt > 0.05f
}

/** Pressure/tilt combined into a single multiplier on the base stroke width at one point. */
private fun widthFactorAt(stroke: Stroke, index: Int): Float {
    val pressure = stroke.pressures.getOrElse(index) { 1f }.coerceIn(0.1f, 1f)
    val tilt = stroke.tilts.getOrElse(index) { 0f }
    // More tilt lays down more ink, like a real pen/marker tipped over - up to 80% wider at
    // maximum tilt (~90°, a stylus flat against the screen).
    val tiltFactor = 1f + (tilt / (Math.PI.toFloat() / 2f)).coerceIn(0f, 1f) * 0.8f
    return pressure * tiltFactor
}

/**
 * Renders one stroke's already screen-space points, styled per [Stroke.penType]. Marker,
 * pencil, and highlighter share the same smoothed-path construction (quadratic Beziers
 * through successive midpoints - see the class doc) with a different color/width/cap, unless
 * the stroke actually carries stylus pressure/tilt data, in which case it's rendered
 * per-segment instead (like calligraphy) so the width can vary along its length. Calligraphy
 * always needs per-segment width, so it's built entirely differently regardless.
 */
private fun DrawScope.drawStroke(stroke: Stroke, screenPoints: List<Offset>, baseWidthScreen: Float, blendMode: BlendMode) {
    if (stroke.penType == PenType.CALLIGRAPHY) {
        drawCalligraphyStroke(stroke, screenPoints, baseWidthScreen, blendMode)
        return
    }
    val style = penStyle(stroke.penType, stroke.color, baseWidthScreen)
    if (screenPoints.size >= 2 && hasStylusVariation(stroke)) {
        for (i in 0 until screenPoints.size - 1) {
            val factor = (widthFactorAt(stroke, i) + widthFactorAt(stroke, i + 1)) / 2f
            val width = (style.widthScreen * factor).coerceAtLeast(1f)
            drawLine(color = style.color, start = screenPoints[i], end = screenPoints[i + 1], strokeWidth = width, cap = style.cap, blendMode = blendMode)
        }
        return
    }
    when (screenPoints.size) {
        1 -> drawCircle(style.color, radius = style.widthScreen / 2f, center = screenPoints[0], blendMode = blendMode)
        2 -> drawLine(
            color = style.color,
            start = screenPoints[0],
            end = screenPoints[1],
            strokeWidth = style.widthScreen,
            cap = style.cap,
            blendMode = blendMode,
        )
        else -> {
            val path = Path()
            path.moveTo(screenPoints[0].x, screenPoints[0].y)
            for (i in 1 until screenPoints.size - 1) {
                val current = screenPoints[i]
                val next = screenPoints[i + 1]
                path.quadraticBezierTo(current.x, current.y, (current.x + next.x) / 2f, (current.y + next.y) / 2f)
            }
            path.lineTo(screenPoints.last().x, screenPoints.last().y)
            drawPath(
                path = path,
                color = style.color,
                style = DrawStyle(width = style.widthScreen, cap = style.cap, join = StrokeJoin.Round),
                blendMode = blendMode,
            )
        }
    }
}

/** Maps this app's persisted [StrokeBlendMode] to the Compose [BlendMode] actually used for drawing. */
private fun StrokeBlendMode.toComposeBlendMode(): BlendMode = when (this) {
    StrokeBlendMode.NORMAL -> BlendMode.SrcOver
    StrokeBlendMode.MULTIPLY -> BlendMode.Multiply
    StrokeBlendMode.SCREEN -> BlendMode.Screen
}

private data class PenStyle(val color: Color, val widthScreen: Float, val cap: StrokeCap)

private fun penStyle(penType: PenType, baseColor: Color, baseWidthScreen: Float): PenStyle = when (penType) {
    PenType.MARKER -> PenStyle(baseColor, baseWidthScreen, StrokeCap.Round)
    PenType.PENCIL -> PenStyle(
        color = baseColor.copy(alpha = baseColor.alpha * 0.85f),
        widthScreen = (baseWidthScreen * 0.6f).coerceAtLeast(1f),
        cap = StrokeCap.Round,
    )
    PenType.HIGHLIGHTER -> PenStyle(
        color = baseColor.copy(alpha = baseColor.alpha * 0.35f),
        widthScreen = baseWidthScreen * 3f,
        cap = StrokeCap.Square,
    )
    PenType.CALLIGRAPHY -> PenStyle(baseColor, baseWidthScreen, StrokeCap.Round) // unused: drawCalligraphyStroke handles it
}

/**
 * Simulates a flat calligraphy nib: each segment's width depends on how that segment's
 * direction relates to the nib's angle - widest when drawing across the nib's edge
 * (perpendicular to it), thinnest when drawing along it. Drawn as separate line segments
 * rather than one smoothed path, since a single Path/Stroke style can't vary width along its
 * length.
 *
 * Uses the stylus's actual tilt orientation as the nib angle when real tilt data is present
 * (a genuinely tilt-responsive nib), falling back to a fixed 45° for finger input or a
 * stylus held upright. `MotionEvent.getOrientation()`'s convention (0 = tilted toward the
 * top of the device, increasing clockwise) is converted to this file's angle convention (0 =
 * pointing along +X, matching `atan2`) with a quarter-turn offset - approximate, and unverified
 * against real stylus hardware.
 */
private fun DrawScope.drawCalligraphyStroke(stroke: Stroke, screenPoints: List<Offset>, baseWidthScreen: Float, blendMode: BlendMode) {
    if (screenPoints.size < 2) {
        if (screenPoints.size == 1) drawCircle(stroke.color, radius = baseWidthScreen / 2f, center = screenPoints[0], blendMode = blendMode)
        return
    }
    val avgTilt = if (stroke.tilts.isEmpty()) 0f else stroke.tilts.average().toFloat()
    val useRealNibAngle = avgTilt > 0.1f
    for (i in 0 until screenPoints.size - 1) {
        val a = screenPoints[i]
        val b = screenPoints[i + 1]
        val angle = atan2(b.y - a.y, b.x - a.x)
        val nibAngle = if (useRealNibAngle) {
            val orientation = (stroke.orientations.getOrNull(i) ?: stroke.orientations.getOrElse(0) { CALLIGRAPHY_NIB_ANGLE })
            orientation + (Math.PI.toFloat() / 2f)
        } else {
            CALLIGRAPHY_NIB_ANGLE
        }
        val pressureFactor = ((stroke.pressures.getOrElse(i) { 1f } + stroke.pressures.getOrElse(i + 1) { 1f }) / 2f).coerceIn(0.1f, 1f)
        val widthFactor = (0.25f + 0.75f * abs(sin(angle - nibAngle))) * pressureFactor
        val width = (baseWidthScreen * widthFactor).coerceAtLeast(1f)
        drawLine(color = stroke.color, start = a, end = b, strokeWidth = width, cap = StrokeCap.Round, blendMode = blendMode)
    }
}

private const val CALLIGRAPHY_NIB_ANGLE = (Math.PI / 4).toFloat() // 45°, a standard calligraphy nib angle

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
private val RULER_COLOR = Color(0xFFFB8C00)

private enum class Mode { NONE, DRAW, NAVIGATE }
private enum class SelectionGestureKind { SCALE, ROTATE, OTHER }
