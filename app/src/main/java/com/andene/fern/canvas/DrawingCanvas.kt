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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStyle
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
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
                    stylusSample.eraserEnd = event.getToolType(0) == MotionEvent.TOOL_TYPE_ERASER
                    stylusSample.buttonPressed = isStylus && (event.buttonState and MotionEvent.BUTTON_STYLUS_PRIMARY) != 0
                }
                false
            }
            .pointerInput(state) {
                awaitEachGesture {
                    val firstDown = awaitFirstDown(requireUnconsumed = false)
                    val screenCenter = Offset(size.width / 2f, size.height / 2f)
                    // Multi-finger tap detection (2 = undo, 3 = redo): few fingers' worth of
                    // movement, short, and never more than 3 fingers.
                    val gestureStartMillis = firstDown.uptimeMillis
                    val historyDepthAtStart = state.historyDepth
                    var maxPointers = 0
                    var navigationTravel = 0f

                    var mode = Mode.NONE
                    var prevCentroid = Offset.Zero
                    var prevSpan = 0f
                    var prevAngle = 0.0
                    var selectionGestureKind = SelectionGestureKind.OTHER
                    var wasStylusActive = false
                    // Tool to return to after a gesture that the stylus temporarily switched to the eraser.
                    var toolBeforeStylusEraser: Tool? = null

                    fun endActiveDrawAction() {
                        when (state.activeTool) {
                            Tool.PEN -> state.endStroke()
                            Tool.ERASER -> state.endErase()
                            Tool.SHAPE -> state.endShape()
                            Tool.FILL -> {} // one-shot on pointer-down, nothing to end
                            Tool.TEXT -> {} // one-shot on pointer-down, nothing to end
                            Tool.EYEDROPPER -> {} // one-shot on pointer-down, nothing to end
                            Tool.RULER -> state.endRulerGesture()
                            Tool.SELECT -> when (selectionGestureKind) {
                                SelectionGestureKind.SCALE -> state.endScaleSelection()
                                SelectionGestureKind.ROTATE -> state.endRotateSelection()
                                SelectionGestureKind.OTHER -> state.endSelectGesture()
                            }
                        }
                        toolBeforeStylusEraser?.let { state.activeTool = it }
                        toolBeforeStylusEraser = null
                    }

                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        val allPressed = event.changes.filter { it.pressed }
                        // Palm rejection: once a stylus is down, ignore finger pointers
                        // entirely (a resting palm reads as ordinary touch input) rather
                        // than letting them register as a second drawing/navigation
                        // pointer. Consumed so nothing else treats them as unhandled.
                        // The eraser end of a stylus reports its own pointer type; it's still a pen, not a palm.
                        val stylusActive = allPressed.any { it.type == PointerType.Stylus || it.type == PointerType.Eraser }
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
                        maxPointers = maxOf(maxPointers, count)

                        if (count == 1) {
                            if (mode != Mode.DRAW) {
                                // Flipped stylus (eraser end) or held side button erases for this
                                // gesture only, then the previous tool comes back - the convention
                                // of Wacom pens, S Pen apps and most desktop paint programs.
                                if ((stylusSample.eraserEnd || stylusSample.buttonPressed) && state.activeTool != Tool.ERASER) {
                                    toolBeforeStylusEraser = state.activeTool
                                    state.activeTool = Tool.ERASER
                                }
                                val downPos = state.unrotateScreen(pointers[0].position, screenCenter)
                                val world = state.screenToWorld(downPos, screenCenter)
                                when (state.activeTool) {
                                    Tool.PEN -> state.beginStroke(world, stylusSample.pressure, stylusSample.tilt, stylusSample.orientation)
                                    Tool.ERASER -> state.beginErase(world)
                                    Tool.SHAPE -> state.beginShape(world)
                                    Tool.FILL -> state.fillAt(world)
                                    Tool.TEXT -> state.beginTextEdit(world)
                                    Tool.EYEDROPPER -> state.pickColorAt(world)
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
                                    val world = state.screenToWorld(state.unrotateScreen(change.position, screenCenter), screenCenter)
                                    when (state.activeTool) {
                                        Tool.PEN -> state.extendStroke(world, stylusSample.pressure, stylusSample.tilt, stylusSample.orientation)
                                        Tool.ERASER -> state.continueErase(world)
                                        Tool.SHAPE -> state.continueShape(world)
                                        Tool.FILL -> {} // one-shot; ignore drag
                                        Tool.TEXT -> {} // one-shot; ignore drag
                                        Tool.EYEDROPPER -> {} // one-shot; ignore drag
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
                            // Angle of the first two fingers; its change frame to frame rotates the canvas.
                            val angle = atan2(
                                (pointers[1].position.y - pointers[0].position.y).toDouble(),
                                (pointers[1].position.x - pointers[0].position.x).toDouble(),
                            )

                            if (mode != Mode.NAVIGATE) {
                                // Just transitioned into navigation: establish baseline, no jump this frame.
                                prevCentroid = centroid
                                prevSpan = span
                                prevAngle = angle
                                mode = Mode.NAVIGATE
                            } else {
                                // Pan and zoom work in unrotated view space, so a drag moves the
                                // content along with the fingers whatever the canvas angle is.
                                navigationTravel += (centroid - prevCentroid).getDistance() + abs(span - prevSpan)
                                val panDelta = state.unrotateScreen(centroid, screenCenter) - state.unrotateScreen(prevCentroid, screenCenter)
                                if (panDelta != Offset.Zero) {
                                    state.panByScreenDelta(panDelta)
                                }
                                if (prevSpan > 0.001f && span > 0.001f) {
                                    val factor = (span / prevSpan).toDouble()
                                    state.zoomAround(state.unrotateScreen(centroid, screenCenter), screenCenter, factor)
                                }
                                state.rotateAround(centroid, screenCenter, angleDelta(prevAngle, angle))
                                prevCentroid = centroid
                                prevSpan = span
                                prevAngle = angle
                            }
                            pointers.forEach { it.consume() }
                        }
                    }
                    if (mode == Mode.DRAW) endActiveDrawAction()
                    if (mode == Mode.NAVIGATE) state.snapRotation()
                    val tapDuration = (currentEvent.changes.firstOrNull()?.uptimeMillis ?: gestureStartMillis) - gestureStartMillis
                    if (maxPointers in 2..3 && tapDuration <= MULTI_FINGER_TAP_MAX_MILLIS && navigationTravel <= MULTI_FINGER_TAP_SLOP_PX) {
                        // The first finger may have begun a stroke before the others landed; that wasn't drawing.
                        state.discardActionsSince(historyDepthAtStart)
                        if (maxPointers == 2) state.undo() else state.redo()
                    }
                }
            }
    ) {
        state.viewportSize = size
        val screenCenter = Offset(size.width / 2f, size.height / 2f)
        // When rotated, the visible area is the screen turned about its centre; the square
        // circumscribing it (side = screen diagonal) covers every angle, so cull against that.
        val halfExtent = if (state.rotation == 0.0) null else hypot(size.width, size.height) / 2f
        val topLeftWorld = state.screenToWorld(
            if (halfExtent == null) Offset.Zero else screenCenter - Offset(halfExtent, halfExtent),
            screenCenter,
        )
        val bottomRightWorld = state.screenToWorld(
            if (halfExtent == null) Offset(size.width, size.height) else screenCenter + Offset(halfExtent, halfExtent),
            screenCenter,
        )

        // Drawn before the rotation so the paper colour always fills the whole screen.
        drawRect(color = state.backgroundColor, topLeft = Offset.Zero, size = size)
        // Everything below is laid out in unrotated view space (worldToScreen) and turned as a
        // whole here, so text, images and patterns rotate with the strokes.
        rotate(degrees = Math.toDegrees(state.rotation).toFloat(), pivot = screenCenter) {
            drawScene(state, screenCenter, topLeftWorld, bottomRightWorld, textMeasurer) { fileName ->
                imageBitmapCache[fileName]
                    ?: BitmapFactory.decodeFile(CanvasStorage.imageFile(context, fileName).absolutePath)
                        ?.asImageBitmap()
                        ?.also { imageBitmapCache[fileName] = it }
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

            // Symmetry axes through the symmetry centre, long enough to cross the screen at any rotation.
            if (state.symmetryMode != SymmetryMode.OFF) {
                val c = state.worldToScreen(state.symmetryCenter, screenCenter)
                val reach = hypot(size.width, size.height) * 2f
                val axisAngles = when (state.symmetryMode) {
                    SymmetryMode.VERTICAL -> listOf(PI / 2)
                    SymmetryMode.HORIZONTAL -> listOf(0.0)
                    SymmetryMode.QUADRANT -> listOf(0.0, PI / 2)
                    SymmetryMode.RADIAL -> (0 until RADIAL_SEGMENTS).map { PI * it / RADIAL_SEGMENTS }
                    SymmetryMode.OFF -> emptyList()
                }
                for (angle in axisAngles) {
                    val d = Offset((cos(angle) * reach).toFloat(), (sin(angle) * reach).toFloat())
                    drawLine(color = SYMMETRY_COLOR, start = c - d, end = c + d, strokeWidth = 2f)
                }
            }

            // Frame outlines and names: editing guides only, so not part of drawScene and never exported.
            for (frame in state.frames) {
                val a = state.worldToScreen(frame.min, screenCenter)
                val b = state.worldToScreen(frame.max, screenCenter)
                drawRect(
                    color = FRAME_COLOR,
                    topLeft = a,
                    size = Size(b.x - a.x, b.y - a.y),
                    style = DrawStyle(width = 2f),
                )
                drawText(
                    textMeasurer = textMeasurer,
                    text = frame.name,
                    topLeft = Offset(a.x, a.y - 24f),
                    style = TextStyle(color = FRAME_COLOR, fontSize = 12.sp),
                )
            }

            if (state.lassoPath.size >= 2) {
                val lasso = Path()
                state.lassoPath.forEachIndexed { index, point ->
                    val screen = state.worldToScreen(point, screenCenter)
                    if (index == 0) lasso.moveTo(screen.x, screen.y) else lasso.lineTo(screen.x, screen.y)
                }
                lasso.close()
                drawPath(lasso, color = MARQUEE_FILL_COLOR)
                drawPath(lasso, color = MARQUEE_BORDER_COLOR, style = DrawStyle(width = 2f))
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
    }

    state.pendingTextEdit?.let { pending ->
        TextEditDialog(
            initialText = pending.initialText,
            onConfirm = { text -> state.confirmTextEdit(text) },
            onDismiss = { state.cancelTextEdit() },
        )
    }
}

/**
 * Draws the document's page pattern ([BackgroundStyle]) within the visible world-space rect
 * only, at a fixed world-unit spacing so the pattern is a genuine property of the infinite
 * page (it pans/zooms with the content) rather than a fixed screen-space overlay. A no-op for
 * [BackgroundStyle.PLAIN].
 */
private fun DrawScope.drawBackgroundPattern(state: CanvasState, screenCenter: Offset, topLeftWorld: WorldPoint, bottomRightWorld: WorldPoint) {
    if (state.backgroundStyle == BackgroundStyle.PLAIN) return
    val spacing = BACKGROUND_PATTERN_SPACING_WORLD
    // At extreme zoom-out the visible world rect can span an enormous range - drawing every
    // line/dot in it would be both invisible (sub-pixel spacing) and prohibitively slow, so
    // skip the pattern entirely past this line-count ceiling, same as how a real sheet of
    // grid paper's lines merge into a blur if you back away far enough.
    val columnCount = (bottomRightWorld.x - topLeftWorld.x) / spacing
    val rowCount = (bottomRightWorld.y - topLeftWorld.y) / spacing
    if (!columnCount.isFinite() || !rowCount.isFinite() || columnCount > MAX_BACKGROUND_PATTERN_LINES || rowCount > MAX_BACKGROUND_PATTERN_LINES) return
    val startX = kotlin.math.floor(topLeftWorld.x / spacing) * spacing
    val startY = kotlin.math.floor(topLeftWorld.y / spacing) * spacing
    val lineColor = BACKGROUND_PATTERN_COLOR
    when (state.backgroundStyle) {
        BackgroundStyle.GRID -> {
            var x = startX
            while (x <= bottomRightWorld.x) {
                val screenX = state.worldToScreen(WorldPoint(x, 0.0), screenCenter).x
                drawLine(color = lineColor, start = Offset(screenX, 0f), end = Offset(screenX, size.height), strokeWidth = 1f)
                x += spacing
            }
            var y = startY
            while (y <= bottomRightWorld.y) {
                val screenY = state.worldToScreen(WorldPoint(0.0, y), screenCenter).y
                drawLine(color = lineColor, start = Offset(0f, screenY), end = Offset(size.width, screenY), strokeWidth = 1f)
                y += spacing
            }
        }
        BackgroundStyle.DOT -> {
            var y = startY
            while (y <= bottomRightWorld.y) {
                var x = startX
                while (x <= bottomRightWorld.x) {
                    val screenPoint = state.worldToScreen(WorldPoint(x, y), screenCenter)
                    drawCircle(color = lineColor, radius = 2f, center = screenPoint)
                    x += spacing
                }
                y += spacing
            }
        }
        BackgroundStyle.RULED -> {
            var y = startY
            while (y <= bottomRightWorld.y) {
                val screenY = state.worldToScreen(WorldPoint(0.0, y), screenCenter).y
                drawLine(color = lineColor, start = Offset(0f, screenY), end = Offset(size.width, screenY), strokeWidth = 1f)
                y += spacing
            }
        }
        BackgroundStyle.PLAIN -> {} // handled by the early return above
    }
}

// World-unit spacing between pattern lines/dots - the page pattern's own fixed "grain",
// independent of stroke width or any other scale-dependent value in this app.
private const val BACKGROUND_PATTERN_SPACING_WORLD = 40.0
private const val MAX_BACKGROUND_PATTERN_LINES = 500.0
private val BACKGROUND_PATTERN_COLOR = Color(0xFFD8D3C8)

/** Mutable holder for the most recent raw-MotionEvent pressure/tilt/orientation of pointer 0. */
private class StylusSample {
    var pressure = 1f
    var tilt = 0f
    var orientation = 0f
    var eraserEnd = false
    var buttonPressed = false
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
// Two/three-finger tap to undo/redo, the convention of Procreate and most iPad/Android drawing apps.
private const val MULTI_FINGER_TAP_MAX_MILLIS = 250L
private const val MULTI_FINGER_TAP_SLOP_PX = 24f // on the §7 scale

// Translucent teal so the axes guide without competing with the drawing.
private val SYMMETRY_COLOR = Color(0x6600897B)
// Muted purple, distinct from the blue selection/marquee colours.
private val FRAME_COLOR = Color(0xFF7E57C2)
private val MARQUEE_FILL_COLOR = Color(0x1A1E88E5)
private val HANDLE_COLOR = Color(0xFF1E88E5)
private val RULER_COLOR = Color(0xFFFB8C00)

private enum class Mode { NONE, DRAW, NAVIGATE }
private enum class SelectionGestureKind { SCALE, ROTATE, OTHER }

/** The signed change from [from] to [to] in radians, wrapped to -pi..pi so crossing the +-180 degree seam doesn't jump. */
private fun angleDelta(from: Double, to: Double): Double {
    var delta = to - from
    while (delta > Math.PI) delta -= 2 * Math.PI
    while (delta < -Math.PI) delta += 2 * Math.PI
    return delta
}

/**
 * Draws the document itself - paper pattern, images, strokes, text - for [state]'s camera,
 * without any editing overlays (selection, marquee, previews, ruler). Shared by the live canvas
 * and image export, so an export looks exactly like the canvas. [imageFor] supplies decoded
 * images by file name (null skips one that can't be decoded).
 */
internal fun DrawScope.drawScene(
    state: CanvasState,
    screenCenter: Offset,
    topLeftWorld: WorldPoint,
    bottomRightWorld: WorldPoint,
    textMeasurer: TextMeasurer,
    imageFor: (String) -> ImageBitmap?,
) {
    drawBackgroundPattern(state, screenCenter, topLeftWorld, bottomRightWorld)

    // Drawn first (behind strokes/text), matching the common "insert a reference image,
    // draw over it" workflow.
    for (item in state.imageItems) {
        val bitmap = imageFor(item.fileName) ?: continue
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

    // Layers bottom to top; strokes keep their list order within a layer. A stroke whose layer
    // no longer exists is drawn with the bottom layer rather than disappearing.
    val layerIndex = state.layers.withIndex().associate { (index, layer) -> layer.id to index }
    val strokesByLayer = state.strokes.groupBy { layerIndex[it.layerId] ?: 0 }
    // Offscreen bounds for a translucent layer: the square around the screen centre that covers
    // the screen at any canvas rotation.
    val halfDiagonal = hypot(size.width, size.height) / 2f
    val layerBounds = Rect(screenCenter - Offset(halfDiagonal, halfDiagonal), screenCenter + Offset(halfDiagonal, halfDiagonal))
    for ((index, layer) in state.layers.withIndex()) {
        if (!layer.visible) continue
        val layerStrokes = strokesByLayer[index] ?: continue
        // Layer opacity applies to the layer as a whole: drawn offscreen, then composited once,
        // so overlapping strokes on one layer don't show through each other.
        val translucent = layer.opacity < 1f
        if (translucent) drawContext.canvas.saveLayer(layerBounds, Paint().apply { alpha = layer.opacity })
        for (stroke in layerStrokes) {
                // Reading revision here (and only here) is what subscribes this draw phase to
                // the stroke's mutations, so a redraw is triggered as each new point is added
                // mid-stroke rather than waiting for an unrelated pan/zoom to happen to redraw.
                stroke.revision
                if (!stroke.intersects(topLeftWorld.x, topLeftWorld.y, bottomRightWorld.x, bottomRightWorld.y)) continue
                val points = stroke.points
                if (points.isEmpty()) continue
                // Level of detail: a stroke smaller than a pixel on screen (far zoomed out) is
                // drawn as one dot, skipping the per-point transform and path building that
                // dominate the cost of drawing thousands of distant strokes.
                // Width counts too: a single-point dot has zero extent but can be large on screen.
                val extentScreen = (maxOf(stroke.maxX - stroke.minX, stroke.maxY - stroke.minY) + stroke.widthWorld) * state.scale
                if (extentScreen < 1.0) {
                    val center = state.worldToScreen(WorldPoint((stroke.minX + stroke.maxX) / 2.0, (stroke.minY + stroke.maxY) / 2.0), screenCenter)
                    drawCircle(color = stroke.color, radius = 0.5f, center = center)
                    continue
                }
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
        if (translucent) drawContext.canvas.restore()
    }

    for (item in state.textItems) {
        val screenPos = state.worldToScreen(item.position, screenCenter)
        val fontSizePx = (item.fontSizeWorld * state.scale)
        if (!fontSizePx.isFinite() || fontSizePx <= 0.0) continue
        val fontSizeSp = fontSizePx.toFloat().toSp() // DrawScope is a Density
        val layout = textMeasurer.measure(text = item.text, style = TextStyle(color = item.color, fontSize = fontSizeSp))
        drawText(textLayoutResult = layout, topLeft = screenPos)
    }
}
