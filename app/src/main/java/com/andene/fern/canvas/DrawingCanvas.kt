package com.andene.fern.canvas

import com.andene.fern.FernHaptics

import com.andene.fern.FernColors
import com.andene.fern.FernScale

import com.andene.fern.FernType

import com.andene.fern.R
import android.graphics.BitmapFactory
import android.view.MotionEvent
import android.view.SurfaceView
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStyle
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.input.motionprediction.MotionEventPredictor
import kotlinx.coroutines.delay
import android.os.SystemClock
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
fun DrawingCanvas(
    state: CanvasState,
    modifier: Modifier = Modifier,
    /** Pins and documents a text label can link to (see [LinkTarget]). */
    linkChoices: LinkChoices = LinkChoices(),
    onFollowLink: (LinkTarget) -> Unit = {},
    /** Gesture actions for the screen around the canvas: quick menu, palette, hiding the bars (design roadmap D15). */
    onGestureAction: (GestureAction) -> Unit = {},
) {
    // Side-channel for stylus pressure/tilt/orientation: Compose's own pointer-input APIs
    // (used below, unchanged) don't expose tilt at all, and pressure only patchily, so this
    // observes the raw MotionEvent directly. It never consumes the event (always returns
    // false), so the existing gesture handling below still sees and processes every touch
    // exactly as before - this only adds a way to read pressure/tilt for pointer 0 at the
    // moment each point is captured. Untested against real stylus hardware.
    val stylusSample = remember { StylusSample() }
    // Motion prediction: where the pen will probably be one frame from now. Drawn as a short,
    // temporary extension of the stroke in progress (never stored), so ink appears to keep up
    // with the pen instead of trailing it by the display pipeline's latency.
    val view = LocalView.current
    val predictor = remember(view) { MotionEventPredictor.newInstance(view) }
    // Where the canvas sits in the window, so the eyedropper reads the pixels under the finger (roadmap item 170).
    var canvasOrigin by remember { mutableStateOf(Offset.Zero) }
    val nothingToPick = stringResource(R.string.eyedropper_nothing)
    DisposableEffect(state, view) {
        state.screenSampler = { at, size, onColor -> sampleWindow(view, canvasOrigin + at, size, onColor) }
        state.onNothingPicked = { com.andene.fern.FernMessages.show(nothingToPick) }
        onDispose {
            state.screenSampler = null
            state.onNothingPicked = null
        }
    }
    var predictedScreen by remember { mutableStateOf<Offset?>(null) }
    val textMeasurer = rememberTextMeasurer()
    val tileCache = remember { SceneTileCache() }
    androidx.compose.runtime.DisposableEffect(tileCache) { onDispose { tileCache.release() } }
    val context = LocalContext.current
    // Decoded lazily and cached by file name: re-decoding from disk every frame would be
    // wasteful, and images never change once placed (a new image is a new ImageItem/file).
    val gestures by GestureSettings.state(context)
    val touchTiming by TouchTimingSettings.state(context)
    val palm by PalmSettings.state(context)
    // Where a hovering stylus is (design roadmap D16): the brush's footprint shows there before it touches.
    var hoverPoint by remember { mutableStateOf<Offset?>(null) }
    // Whether a selection is being moved, scaled or turned: its place and size show beside it (design roadmap D37).
    var selectionReadout by remember { mutableStateOf(false) }
    // Pinching or panning: the zoom and turn show at the top (design roadmap D38).
    var navigating by remember { mutableStateOf(false) }
    // The last two-finger tap's action and time, to spot a double tap.
    var lastTwoFingerTap by remember { mutableStateOf(GestureAction.NONE to 0L) }
    // What a tap gesture did, where and when, shown briefly (design roadmap D38).
    var gestureBadge by remember { mutableStateOf<Triple<String, Offset, Long>?>(null) }
    gestureBadge?.let { badge ->
        LaunchedEffect(badge) {
            delay(GESTURE_BADGE_MILLIS)
            if (gestureBadge === badge) gestureBadge = null
        }
    }
    val imageBitmapCache = remember { mutableMapOf<String, ImageBitmap>() }
    // Low-latency ink (see FrontBufferedInk): a transparent front-buffered surface above the
    // canvas. Placed first in the Box so the canvas, not the surface, receives touches; the
    // surface is still composited on top because it's ordered above the window.
    var frontInk by remember { mutableStateOf<FrontBufferedInk?>(null) }
    // Live QuickShape (roadmap item 118): a held pen stroke snaps to its shape while still down.
    // Touch events stop while the pen is still, so a clock has to notice the hold.
    LaunchedEffect(state) {
        while (true) {
            delay(SHAPE_HOLD_POLL_MILLIS)
            // The front buffer still shows the freehand ink; wipe it so the clean shape shows.
            if (state.holdTick()) {
                frontInk?.clear()
                FernHaptics.tick(view) // the held stroke just snapped to its shape (design roadmap D7)
            }
        }
    }

    Box(modifier) {
    if (FrontBufferedInk.isSupported) {
        AndroidView(
            factory = { ctx -> SurfaceView(ctx).also { FrontBufferedInk.prepare(it); frontInk = FrontBufferedInk(it) } },
            modifier = Modifier.matchParentSize(),
            onRelease = { frontInk?.release(); frontInk = null },
        )
    }
    Canvas(
        modifier = Modifier.matchParentSize()
            .onGloballyPositioned { canvasOrigin = it.positionInWindow() }
            // Screen readers otherwise announce nothing for the drawing surface.
            .semantics {
                contentDescription = context.resources.getQuantityString(R.plurals.canvas_description, state.strokes.size, state.strokes.size)
                // The tool, zoom and selection, announced as they change (design roadmap D4).
                stateDescription = context.resources.getQuantityString(
                    R.plurals.canvas_state, state.selection.size,
                    context.getString(toolName(state.activeTool)), (state.scale * 100).roundToInt(), state.selection.size,
                )
                liveRegion = LiveRegionMode.Polite
            }
            .pointerInput(state) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull() ?: continue
                        if (change.type == PointerType.Stylus || change.type == PointerType.Eraser) {
                            stylusSample.lastSeenMillis = SystemClock.uptimeMillis()
                            hoverPoint = if (!change.pressed && event.type != PointerEventType.Exit) change.position else null
                        } else if (event.type == PointerEventType.Exit) {
                            hoverPoint = null
                        }
                    }
                }
            }
            .pointerInteropFilter { event ->
                if (event.pointerCount > 0) {
                    val isStylus = event.getToolType(0) == MotionEvent.TOOL_TYPE_STYLUS
                    stylusSample.pressure = event.getPressure(0)
                    stylusSample.tilt = if (isStylus) event.getAxisValue(MotionEvent.AXIS_TILT, 0) else 0f
                    stylusSample.orientation = if (isStylus) event.getOrientation(0) else 0f
                    stylusSample.eraserEnd = event.getToolType(0) == MotionEvent.TOOL_TYPE_ERASER
                    stylusSample.buttonPressed = isStylus && (event.buttonState and MotionEvent.BUTTON_STYLUS_PRIMARY) != 0
                    predictor.record(event)
                    frontInk?.onTouchEvent(event, state.strokeInProgress?.takeIf { state.activeStabilization <= 0.0 && !state.isAdjustingShape }, state.scale)
                    predictedScreen = when (event.actionMasked) {
                        MotionEvent.ACTION_MOVE -> predictor.predict()?.let { predicted ->
                            Offset(predicted.x, predicted.y).also { predicted.recycle() }
                        }
                        else -> null
                    }
                }
                false
            }
            // Mouse wheel / trackpad scroll: pans, or zooms around the pointer with Ctrl held (the
            // convention of desktop canvas apps, and how trackpad pinch arrives on ChromeOS).
            .pointerInput(state) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type != PointerEventType.Scroll) continue
                        val change = event.changes.firstOrNull() ?: continue
                        val delta = change.scrollDelta
                        val screenCenter = Offset(size.width / 2f, size.height / 2f)
                        if (event.keyboardModifiers.isCtrlPressed) {
                            state.zoomAround(state.unrotateScreen(change.position, screenCenter), screenCenter, Math.pow(WHEEL_ZOOM_STEP, -delta.y.toDouble()))
                        } else {
                            state.panByScreenDelta(-delta * WHEEL_PAN_PX)
                        }
                        change.consume()
                    }
                }
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
                    // Configurable gestures (design roadmap D15): a finger held still, and a three-finger swipe.
                    val downClock = SystemClock.uptimeMillis()
                    var longPressArmed = firstDown.type == PointerType.Touch && gestures.getValue(CanvasGesture.LONG_PRESS) != GestureAction.NONE
                    var longPressed = false
                    var swipeStart: Offset? = null
                    var swipeLast = Offset.Zero
                    var swipeStartSpan = 0f
                    var swipeSpanChange = 0f
                    val viewAtStart = Triple(state.panWorld, state.scale, state.rotation)
                    fun runGesture(action: GestureAction, at: Offset) {
                        // A badge where the fingers tapped says what happened (design roadmap D38).
                        if (action == GestureAction.UNDO || action == GestureAction.REDO) gestureBadge = Triple(context.getString(action.label), at, SystemClock.uptimeMillis())
                        state.runGestureAction(action, state.screenToWorld(state.unrotateScreen(at, screenCenter), screenCenter), onGestureAction)
                    }

                    var mode = Mode.NONE
                    var prevCentroid = Offset.Zero
                    var prevSpan = 0f
                    var prevAngle = 0.0
                    // Fingers on the canvas at the last navigation frame.
                    var prevNavigationCount = 0
                    var selectionGestureKind = SelectionGestureKind.OTHER
                    var wasStylusActive = false
                    // Tool to return to after a gesture that the stylus temporarily switched to the eraser.
                    var toolBeforeStylusEraser: Tool? = null
                    // This gesture is moving a guide (guide edit mode, roadmap item 340), not using the tool.
                    var editingGuide = false

                    fun endActiveDrawAction() {
                        if (editingGuide) {
                            state.endGuideEdit()
                            editingGuide = false
                            return
                        }
                        when (state.activeTool) {
                            Tool.PEN -> state.endStroke()
                            Tool.ERASER -> state.endErase()
                            Tool.SHAPE -> state.endShape()
                            Tool.FILL -> {} // one-shot on pointer-down, nothing to end
                            Tool.TEXT -> state.endTextGesture()
                            Tool.EYEDROPPER -> {} // one-shot on pointer-down, nothing to end
                            Tool.CONNECT -> state.endConnect()
                            Tool.BONE -> state.endBone()
                            Tool.BEZIER -> {} // a node is placed on press; nothing to end
                            Tool.MEASURE -> state.endMeasure()
                            Tool.RIG -> state.rigEditor.end()
                            Tool.SELECT -> {
                                when (selectionGestureKind) {
                                    SelectionGestureKind.SCALE -> state.endScaleSelection()
                                    SelectionGestureKind.ROTATE -> state.endRotateSelection()
                                    SelectionGestureKind.OTHER -> state.endSelectGesture()
                                }
                                selectionReadout = false
                            }
                        }
                        toolBeforeStylusEraser?.let { state.activeTool = it }
                        toolBeforeStylusEraser = null
                    }

                    while (true) {
                        val holding = longPressArmed && maxPointers <= 1
                        val event = if (holding) {
                            withTimeoutOrNull((touchTiming.holdMillis - (SystemClock.uptimeMillis() - downClock)).coerceAtLeast(1L)) { awaitPointerEvent(PointerEventPass.Main) }
                        } else {
                            awaitPointerEvent(PointerEventPass.Main)
                        }
                        if (event == null) {
                            // Held still: whatever the first touch began (a dot) wasn't meant.
                            longPressArmed = false
                            longPressed = true
                            if (mode == Mode.DRAW) endActiveDrawAction()
                            state.discardActionsSince(historyDepthAtStart)
                            mode = Mode.NONE
                            runGesture(gestures.getValue(CanvasGesture.LONG_PRESS), firstDown.position)
                            continue
                        }
                        val allPressed = event.changes.filter { it.pressed }
                        // Palm rejection: once a stylus is down, ignore finger pointers
                        // entirely (a resting palm reads as ordinary touch input) rather
                        // than letting them register as a second drawing/navigation
                        // pointer. Consumed so nothing else treats them as unhandled.
                        // The eraser end of a stylus reports its own pointer type; it's still a pen, not a palm.
                        val stylusActive = allPressed.any { it.type == PointerType.Stylus || it.type == PointerType.Eraser }
                        // Palm rejection (design roadmap D16): off, while the stylus is down, or also while it's near.
                        val stylusNear = SystemClock.uptimeMillis() - stylusSample.lastSeenMillis < PALM_NEAR_MILLIS
                        val rejectTouch = when (palm) {
                            PalmRejection.OFF -> false
                            PalmRejection.NORMAL -> stylusActive
                            PalmRejection.STRICT -> stylusActive || stylusNear
                        }
                        val pointers = if (rejectTouch) {
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
                        if (longPressed) {
                            // The rest of a long press does nothing more.
                            pointers.forEach { it.consume() }
                            continue
                        }
                        if (count > 1 || (pointers[0].position - firstDown.position).getDistance() > LONG_PRESS_SLOP_PX) longPressArmed = false

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
                                // In guide edit mode a finger moves guides and nothing else; out of it guides never take a touch.
                                if (state.editingGuides) {
                                    editingGuide = true
                                    state.beginGuideEdit(world)
                                } else when (state.activeTool) {
                                    Tool.PEN -> state.beginStroke(world, stylusSample.pressure, stylusSample.tilt, stylusSample.orientation)
                                    Tool.ERASER -> state.beginErase(world)
                                    Tool.SHAPE -> state.beginShape(world)
                                    Tool.FILL -> state.fillAt(world)
                                    Tool.TEXT -> state.beginTextGesture(world)
                                    Tool.EYEDROPPER -> state.pickColorAt(world)
                                    Tool.CONNECT -> state.beginConnect(world)
                                    Tool.BONE -> state.beginBone(world)
                                    Tool.BEZIER -> state.beginBezierNode(world)
                                    Tool.MEASURE -> state.beginMeasure(world)
                                    Tool.RIG -> state.rigEditor.begin(world)
                                    Tool.SELECT -> {
                                        // The shape builder and knife (roadmap item 141) take every drag, handles included.
                                        val handles = if (state.shapeEditMode == null) selectionHandles(state, screenCenter) else null
                                        selectionReadout = true
                                        selectionGestureKind = when {
                                            handles != null && (downPos - handles.scaleHandle).getDistance() <= HANDLE_HIT.toPx() -> {
                                                state.beginScaleSelection(world)
                                                SelectionGestureKind.SCALE
                                            }
                                            handles != null && (downPos - handles.rotateHandle).getDistance() <= HANDLE_HIT.toPx() -> {
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
                                    if (editingGuide) state.continueGuideEdit(world) else when (state.activeTool) {
                                        Tool.PEN -> state.extendStroke(world, stylusSample.pressure, stylusSample.tilt, stylusSample.orientation)
                                        Tool.ERASER -> state.continueErase(world)
                                        Tool.SHAPE -> state.continueShape(world)
                                        Tool.FILL -> {} // one-shot; ignore drag
                                        Tool.TEXT -> state.continueTextGesture(world)
                                        Tool.EYEDROPPER -> {} // one-shot; ignore drag
                                        Tool.CONNECT -> state.continueConnect(world)
                                        Tool.BONE -> state.continueBone(world)
                                        Tool.BEZIER -> state.dragBezierHandle(world)
                                        Tool.MEASURE -> state.continueMeasure(world)
                                        Tool.RIG -> state.rigEditor.move(world)
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
                            // A second finger turns a text tap into a pinch: don't open the text dialog for it.
                            if (mode == Mode.DRAW && state.activeTool == Tool.TEXT) state.cancelTextGesture()
                            else if (mode == Mode.DRAW) endActiveDrawAction()
                            val centroid = pointers.fold(Offset.Zero) { acc, c -> acc + c.position } / count.toFloat()
                            val span = pointers.fold(0f) { acc, c -> acc + hypot((c.position.x - centroid.x), (c.position.y - centroid.y)) } / count.toFloat()
                            // Angle of the first two fingers; its change frame to frame rotates the canvas.
                            val angle = atan2(
                                (pointers[1].position.y - pointers[0].position.y).toDouble(),
                                (pointers[1].position.x - pointers[0].position.x).toDouble(),
                            )
                            if (count == 3) {
                                if (swipeStart == null) {
                                    swipeStart = centroid
                                    swipeStartSpan = span
                                }
                                swipeLast = centroid
                                swipeSpanChange = maxOf(swipeSpanChange, abs(span - swipeStartSpan))
                            }

                            if (mode != Mode.NAVIGATE || count != prevNavigationCount) {
                                // Just transitioned into navigation, or a finger landed or lifted: establish
                                // a baseline, no jump this frame. The centroid, span and angle all shift
                                // with the finger count without any finger moving, and counting that as
                                // travel panned the view and turned staggered multi-finger taps into drags.
                                prevNavigationCount = count
                                prevCentroid = centroid
                                prevSpan = span
                                prevAngle = angle
                                mode = Mode.NAVIGATE
                                navigating = true
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
                    if (mode == Mode.NAVIGATE) {
                        state.snapRotation()
                        // The turn snapped back level: a tick to feel it (design roadmap D38).
                        if (state.rotation == 0.0 && viewAtStart.third != 0.0) FernHaptics.tick(view)
                    }
                    navigating = false
                    val tapDuration = (currentEvent.changes.firstOrNull()?.uptimeMillis ?: gestureStartMillis) - gestureStartMillis
                    val swipeFrom = swipeStart
                    if (longPressed) {
                        // Handled when it fired.
                    } else if (maxPointers in 2..4 && tapDuration <= touchTiming.tapMaxMillis && navigationTravel <= touchTiming.tapSlopPx) {
                        // The first finger may have begun a stroke before the others landed; that wasn't drawing.
                        state.discardActionsSince(historyDepthAtStart)
                        val gesture = when (maxPointers) {
                            2 -> CanvasGesture.TWO_FINGER_TAP
                            3 -> CanvasGesture.THREE_FINGER_TAP
                            else -> CanvasGesture.FOUR_FINGER_TAP
                        }
                        val now = SystemClock.uptimeMillis()
                        if (maxPointers == 2 && now - lastTwoFingerTap.second <= DOUBLE_TAP_MILLIS) {
                            // A two-finger double tap fits the drawing (design roadmap D41); the first tap's undo or redo is taken back.
                            when (lastTwoFingerTap.first) {
                                GestureAction.UNDO -> state.redo()
                                GestureAction.REDO -> state.undo()
                                else -> {}
                            }
                            lastTwoFingerTap = GestureAction.NONE to 0L
                            state.fitWithReturn()
                        } else {
                            val action = gestures.getValue(gesture)
                            if (maxPointers == 2) lastTwoFingerTap = action to now
                            runGesture(action, firstDown.position)
                        }
                    } else if (maxPointers == 3 && swipeFrom != null && (swipeLast - swipeFrom).getDistance() >= SWIPE_MIN_PX &&
                        swipeSpanChange <= SWIPE_SPAN_SLOP_PX && tapDuration <= SWIPE_MAX_MILLIS &&
                        gestures.getValue(CanvasGesture.THREE_FINGER_SWIPE) != GestureAction.NONE
                    ) {
                        // A swipe, not a pan: the view goes back to where it was.
                        state.restoreView(viewAtStart.first, viewAtStart.second, viewAtStart.third)
                        runGesture(gestures.getValue(CanvasGesture.THREE_FINGER_SWIPE), swipeLast)
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
            val imageFor = { fileName: String ->
                imageBitmapCache[fileName]
                    ?: BitmapFactory.decodeFile(CanvasStorage.imageFile(context, fileName).absolutePath)
                        ?.asImageBitmap()
                        ?.also { imageBitmapCache[fileName] = it }
            }
            val wrapTile = state.wrapTile
            if (wrapTile != null) {
                // Wrap-around mode (roadmap item 137): the drawing again at every whole-tile
                // offset the view reaches, the paper drawn once, and the tile outlined.
                for (offset in wrapOffsets(wrapTile, topLeftWorld, bottomRightWorld)) {
                    withTransform({ translate((offset.x * state.scale).toFloat(), (offset.y * state.scale).toFloat()) }) {
                        drawScene(state, screenCenter, topLeftWorld - offset, bottomRightWorld - offset, textMeasurer, imageFor, drawPaper = offset == WorldPoint.Zero)
                    }
                }
                val a = state.worldToScreen(wrapTile.min, screenCenter)
                val b = state.worldToScreen(wrapTile.max, screenCenter)
                drawRect(HANDLE_COLOR, topLeft = a, size = Size(b.x - a.x, b.y - a.y), style = DrawStyle(width = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))))
            } else {
                // Large documents pan and zoom from cached tiles when nothing is being edited live.
                val fromTiles = state.tileCacheEligible &&
                    tileCache.draw(this, state, screenCenter, topLeftWorld, bottomRightWorld, textMeasurer, imageFor)
                if (!fromTiles) drawScene(state, screenCenter, topLeftWorld, bottomRightWorld, textMeasurer, imageFor)
            }

            state.textItems.firstOrNull { it.id == state.selectedTextId }?.let { item ->
                val (min, max) = state.textBounds(item)
                val topLeft = state.worldToScreen(min, screenCenter)
                val bottomRight = state.worldToScreen(max, screenCenter)
                drawRect(
                    color = SELECTION_HIGHLIGHT_COLOR,
                    topLeft = topLeft,
                    size = Size(bottomRight.x - topLeft.x, bottomRight.y - topLeft.y),
                    style = DrawStyle(width = 2f),
                )
            }

            // Embedded videos (roadmap item 107): a play badge on their first frame.
            for (video in state.imageItems) {
                if (video.videoFileName == null) continue
                val centre = state.worldToScreen(WorldPoint(video.position.x + video.widthWorld / 2, video.position.y + video.heightWorld / 2), screenCenter)
                drawCircle(VIDEO_BADGE_COLOR, radius = VIDEO_BADGE_RADIUS_PX, center = centre)
                val r = VIDEO_BADGE_RADIUS_PX / 2
                val triangle = Path().apply {
                    moveTo(centre.x - r * 0.6f, centre.y - r)
                    lineTo(centre.x + r, centre.y)
                    lineTo(centre.x - r * 0.6f, centre.y + r)
                    close()
                }
                drawPath(triangle, Color.White)
            }

            state.imageItems.firstOrNull { it.id == state.selectedImageId }?.let { image ->
                val topLeft = state.worldToScreen(image.position, screenCenter)
                val bottomRight = state.worldToScreen(
                    WorldPoint(image.position.x + image.widthWorld, image.position.y + image.heightWorld),
                    screenCenter,
                )
                drawRect(
                    color = SELECTION_HIGHLIGHT_COLOR,
                    topLeft = topLeft,
                    size = Size(bottomRight.x - topLeft.x, bottomRight.y - topLeft.y),
                    style = DrawStyle(width = 4f),
                )
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

            // Perspective guide: construction lines radiating from each vanishing point, the horizon
            // through them, and a handle on each to drag it. A guide only: never exported.
            if (state.perspectiveMode != PerspectiveMode.OFF && state.vanishingPoints.isNotEmpty()) {
                val reach = hypot(size.width, size.height) * 2f
                val points = state.vanishingPoints.map { state.worldToScreen(it, screenCenter) }
                val horizonY = points.first().y
                if (points.size >= 2) {
                    // The horizon is the line through the first two points, extended across the screen.
                    val d = points[1] - points[0]
                    val unit = d / d.getDistance().coerceAtLeast(1e-3f)
                    drawLine(PERSPECTIVE_HORIZON_COLOR, points[0] - unit * reach, points[0] + unit * reach, strokeWidth = 2f)
                } else {
                    drawLine(PERSPECTIVE_HORIZON_COLOR, Offset(points[0].x - reach, horizonY), Offset(points[0].x + reach, horizonY), strokeWidth = 2f)
                }
                for (vp in points) {
                    for (k in 0 until PERSPECTIVE_RAYS) {
                        val angle = 2 * PI * k / PERSPECTIVE_RAYS
                        drawLine(PERSPECTIVE_RAY_COLOR, vp, vp + Offset((cos(angle) * reach).toFloat(), (sin(angle) * reach).toFloat()), strokeWidth = 1f)
                    }
                    drawCircle(color = DRAWING_GUIDE_COLOR, radius = 12f, center = vp)
                    drawCircle(color = Color.White, radius = 6f, center = vp)
                }
            }

            // Frame outlines and names: editing guides only, so not part of drawScene and never exported.
            for (frame in state.frames) {
                val a = state.worldToScreen(frame.min, screenCenter)
                val b = state.worldToScreen(frame.max, screenCenter)
                // Layout grid (roadmap item 142): its columns and rows as faint bands.
                frame.layoutGrid?.let { grid ->
                    val columns = grid.columnEdges(frame).chunked(2)
                    val rows = grid.rowEdges(frame).chunked(2)
                    for ((left, right) in columns) {
                        val l = state.worldToScreen(WorldPoint(left, frame.min.y), screenCenter)
                        val r = state.worldToScreen(WorldPoint(right, frame.max.y), screenCenter)
                        drawRect(LAYOUT_GRID_COLOR, topLeft = l, size = Size(r.x - l.x, r.y - l.y))
                    }
                    for ((top, bottom) in rows) {
                        val t = state.worldToScreen(WorldPoint(frame.min.x, top), screenCenter)
                        val u = state.worldToScreen(WorldPoint(frame.max.x, bottom), screenCenter)
                        drawRect(LAYOUT_GRID_COLOR, topLeft = t, size = Size(u.x - t.x, u.y - t.y))
                    }
                }
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
                    style = TextStyle(color = FRAME_COLOR, fontSize = FernType.caption),
                )
            }

            // Predicted extension of the pen stroke in progress (see predictor above).
            val tip = state.strokeTip
            val drawing = state.strokeInProgress
            val predicted = predictedScreen
            if (tip != null && drawing != null && predicted != null && state.activeStabilization <= 0.0) {
                drawLine(
                    color = drawing.color,
                    start = state.worldToScreen(tip, screenCenter),
                    end = state.unrotateScreen(predicted, screenCenter),
                    strokeWidth = (drawing.widthWorld * state.scale).toFloat().coerceIn(1f, 1_000_000f),
                    cap = StrokeCap.Round,
                )
            }

            // Ruler guides (roadmap item 99): lines across the whole view.
            for (guide in state.guides) {
                val a = if (guide.vertical) WorldPoint(guide.position, topLeftWorld.y) else WorldPoint(topLeftWorld.x, guide.position)
                val b = if (guide.vertical) WorldPoint(guide.position, bottomRightWorld.y) else WorldPoint(bottomRightWorld.x, guide.position)
                drawLine(RULER_GUIDE_COLOR, state.worldToScreen(a, screenCenter), state.worldToScreen(b, screenCenter), strokeWidth = 1f)
            }

            // A painted selection (roadmap item 138): its coverage tinted, the way Krita shows a selection mask.
            state.selectionMask?.let { mask ->
                state.rasterRevision // repaint as it's painted
                val half = hypot(size.width, size.height) / 2f
                val bounds = Rect(screenCenter - Offset(half, half), screenCenter + Offset(half, half))
                drawContext.canvas.saveLayer(bounds, Paint().apply { alpha = SELECTION_MASK_ALPHA })
                drawRaster(mask, state, screenCenter, topLeftWorld, bottomRightWorld)
                drawRect(SELECTION_MASK_TINT, topLeft = bounds.topLeft, size = bounds.size, blendMode = BlendMode.SrcIn)
                drawContext.canvas.restore()
            }

            // Raster selection outline: dashed, so it reads over any paint.
            state.rasterSelection?.let { outline ->
                val path = Path()
                outline.forEachIndexed { i, p ->
                    val s = state.worldToScreen(p, screenCenter)
                    if (i == 0) path.moveTo(s.x, s.y) else path.lineTo(s.x, s.y)
                }
                path.close()
                drawPath(path, Color.White, style = DrawStyle(width = 2f))
                drawPath(path, Color.Black, style = DrawStyle(width = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))))
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

            // Game objects' drawn hitboxes and links (roadmap item 157), while editing with the selection tool.
            if (state.play == null && state.activeTool == Tool.SELECT && state.gameObjects.isNotEmpty()) {
                val dashes = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
                for (o in state.gameObjects) {
                    val hitbox = o.hitbox ?: continue
                    val path = Path()
                    hitbox.forEachIndexed { i, p -> val s = state.worldToScreen(p, screenCenter); if (i == 0) path.moveTo(s.x, s.y) else path.lineTo(s.x, s.y) }
                    path.close()
                    drawPath(path, GAME_GUIDE_COLOR, style = DrawStyle(width = 2f, pathEffect = dashes))
                }
                for (link in state.gameSettings.links) {
                    val a = state.objectCentre(link.from) ?: continue
                    val b = state.objectCentre(link.to) ?: continue
                    drawLine(GAME_GUIDE_COLOR, state.worldToScreen(a, screenCenter), state.worldToScreen(b, screenCenter), strokeWidth = 2f, pathEffect = dashes)
                }
            }

            // Shape builder / knife drag (roadmap item 141): an open line, red when it removes or cuts.
            if (state.shapeEditPath.size >= 2) {
                val trail = Path()
                state.shapeEditPath.forEachIndexed { index, point ->
                    val screen = state.worldToScreen(point, screenCenter)
                    if (index == 0) trail.moveTo(screen.x, screen.y) else trail.lineTo(screen.x, screen.y)
                }
                val adding = state.shapeEditMode == ShapeEditMode.BUILD_ADD
                drawPath(trail, color = Color.White, style = DrawStyle(width = 4f))
                drawPath(trail, color = if (adding) MARQUEE_BORDER_COLOR else SHAPE_CUT_COLOR, style = DrawStyle(width = 2f))
            }

            // Node editing: a square handle on each node of the stroke being edited.
            state.nodeEditStroke?.let { stroke ->
                stroke.revision // redraw as nodes are dragged
                for (index in editNodeIndices(stroke.points.size)) {
                    val p = state.worldToScreen(stroke.points[index], screenCenter)
                    drawRect(Color.White, topLeft = p - Offset(6f, 6f), size = Size(12f, 12f))
                    drawRect(HANDLE_COLOR, topLeft = p - Offset(6f, 6f), size = Size(12f, 12f), style = DrawStyle(width = 2f))
                }
            }

            // Weight painting: each point of the selected bone's ink as a dot, red where it fully
            // follows the bone, blue where it follows the parent, grey where it's automatic.
            if (state.paintingWeights && state.activeTool == Tool.BONE) {
                val boneId = state.selectedBoneId
                for (stroke in state.strokes) {
                    if (boneId == null || stroke.boneId != boneId || stroke.animFrame != state.currentAnimFrame) continue
                    stroke.revision // redraw as weights are painted
                    val weights = stroke.jointWeights
                    for (i in stroke.points.indices) {
                        val w = weights?.getOrNull(i)
                        val color = if (w == null || w.isNaN()) WEIGHT_AUTO_COLOR else lerp(WEIGHT_PARENT_COLOR, WEIGHT_BONE_COLOR, w)
                        drawCircle(color, radius = 4f, center = state.worldToScreen(stroke.points[i], screenCenter))
                    }
                }
            }

            // Warp handles: the mesh's grid lines, the distort's quad, or the puppet's bare pins.
            if (state.meshWarping) {
                val nodes = state.meshNodes.map { state.worldToScreen(it, screenCenter) }
                if (state.warpKind == WarpKind.MESH && nodes.size == MESH_SIZE * MESH_SIZE) {
                    for (j in 0 until MESH_SIZE) for (i in 0 until MESH_SIZE) {
                        val p = nodes[j * MESH_SIZE + i]
                        if (i + 1 < MESH_SIZE) drawLine(HANDLE_COLOR, p, nodes[j * MESH_SIZE + i + 1], strokeWidth = 1f)
                        if (j + 1 < MESH_SIZE) drawLine(HANDLE_COLOR, p, nodes[(j + 1) * MESH_SIZE + i], strokeWidth = 1f)
                    }
                }
                if (state.warpKind == WarpKind.DISTORT && nodes.size == 4) {
                    for (i in 0 until 4) drawLine(HANDLE_COLOR, nodes[i], nodes[(i + 1) % 4], strokeWidth = 1f)
                }
                for (p in nodes) {
                    drawCircle(Color.White, radius = 8f, center = p)
                    drawCircle(HANDLE_COLOR, radius = 8f, center = p, style = DrawStyle(width = 2f))
                }
            }

            // The Bézier path being built: the curve so far, its nodes, and the last node's handles.
            if (state.bezierNodes.isNotEmpty()) {
                val nodes = state.bezierNodes.toList()
                val preview = flattenBezier(nodes, closed = false, spacing = 4.0 / state.scale.coerceAtLeast(1e-300)).map { state.worldToScreen(it, screenCenter) }
                for (i in 1 until preview.size) drawLine(MARQUEE_BORDER_COLOR, preview[i - 1], preview[i], strokeWidth = 2f)
                val last = nodes.last()
                if (!last.isCorner) {
                    val p = state.worldToScreen(last.point, screenCenter)
                    for (handle in listOf(last.handleIn, last.handleOut)) {
                        val h = state.worldToScreen(handle, screenCenter)
                        drawLine(HANDLE_COLOR, p, h, strokeWidth = 1f)
                        drawCircle(HANDLE_COLOR, 4f, h)
                    }
                }
                for ((i, node) in nodes.withIndex()) {
                    val p = state.worldToScreen(node.point, screenCenter)
                    // The first node is drawn larger: tapping it closes the path.
                    drawRect(HANDLE_COLOR, topLeft = p - Offset(if (i == 0) 6f else 4f, if (i == 0) 6f else 4f), size = Size(if (i == 0) 12f else 8f, if (i == 0) 12f else 8f), style = DrawStyle(width = 2f))
                }
            }

            // The skeleton (roadmap item 83): a guide, never exported. Shown while the bone tool is
            // in use, or in animation mode, where it's what poses each frame.
            if (state.activeTool == Tool.BONE || (state.animationMode && state.bones.isNotEmpty())) {
                for (bone in state.currentBones) {
                    drawBone(state.worldToScreen(bone.head, screenCenter), state.worldToScreen(bone.tail, screenCenter), bone.id == state.selectedBoneId)
                }
                state.boneDraft?.let { (head, tail) -> drawBone(state.worldToScreen(head, screenCenter), state.worldToScreen(tail, screenCenter), true) }
            }

            // Connector being dragged out: from its label's box edge to the finger.
            state.connectorDraft?.let { (fromId, point) ->
                state.textItems.firstOrNull { it.id == fromId }?.let { from ->
                    val (min, max) = state.textBounds(from)
                    val start = state.worldToScreen(WorldPoint((min.x + max.x) / 2, (min.y + max.y) / 2), screenCenter)
                    drawConnector(start, state.worldToScreen(point, screenCenter), MARQUEE_BORDER_COLOR)
                }
            }

            // Ring on the object point the shape being drawn is snapped to.
            state.snapIndicator?.let { anchor ->
                drawCircle(color = MARQUEE_BORDER_COLOR, radius = 12f, center = state.worldToScreen(anchor, screenCenter), style = DrawStyle(width = 2f))
            }

            state.textBoxDraft?.let { (start, end) ->
                val a = state.worldToScreen(start, screenCenter)
                val b = state.worldToScreen(end, screenCenter)
                drawRect(
                    color = MARQUEE_BORDER_COLOR,
                    topLeft = Offset(minOf(a.x, b.x), minOf(a.y, b.y)),
                    size = Size(abs(b.x - a.x), abs(b.y - a.y)),
                    style = DrawStyle(width = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))),
                )
            }

            state.shapePreview?.let { (start, end) ->
                val outlines = listOf(shapeOnPlane(state.activeShapeKind, start, end, state.isometricPlane)) +
                    extrasOnPlane(state.activeShapeKind, start, end, state.isometricPlane)
                val rawWidthScreen = state.activeWidthWorld
                val widthScreen = rawWidthScreen.toFloat().coerceIn(1f, 1_000_000f)
                val path = Path()
                for (outline in outlines) {
                    val screenPoints = outline.map { state.worldToScreen(it, screenCenter) }
                    path.moveTo(screenPoints[0].x, screenPoints[0].y)
                    for (i in 1 until screenPoints.size) path.lineTo(screenPoints[i].x, screenPoints[i].y)
                }
                drawPath(path = path, color = state.activeColor, style = DrawStyle(width = widthScreen, cap = StrokeCap.Round, join = StrokeJoin.Round))
                // Dimensions (roadmap item 99): the line's length, or the box's width x height,
                // in world units, beside the finger.
                val label = shapeDimensions(state.activeShapeKind, start, end, state.drawingScale)
                val layout = textMeasurer.measure(label, TextStyle(color = FernColors.current.onScrim, fontSize = FernType.caption))
                val at = state.worldToScreen(end, screenCenter) + Offset(16f, 16f)
                drawRect(MARQUEE_BORDER_COLOR, topLeft = at - Offset(4f, 2f), size = Size(layout.size.width + 8f, layout.size.height + 4f))
                drawText(layout, topLeft = at)
            }

            // Measurement (roadmap item 143): the measured line or outline, and its readout.
            if (state.activeTool == Tool.MEASURE) state.measurement?.let { measured ->
                val screen = measured.points.map { state.worldToScreen(it, screenCenter) }
                val path = Path().apply {
                    moveTo(screen[0].x, screen[0].y)
                    for (i in 1 until screen.size) lineTo(screen[i].x, screen[i].y)
                    if (measured.closed) close()
                }
                drawPath(path, Color.White, style = DrawStyle(width = 4f))
                drawPath(path, MEASURE_COLOR, style = DrawStyle(width = 2f))
                val scaleOf = state.drawingScale
                val label: String
                val at: Offset
                if (measured.points.size == 2 && !measured.closed) {
                    for (end in screen) drawCircle(MEASURE_COLOR, radius = 4f, center = end)
                    val (a, b) = measured.points
                    // Compass angle as on screen: 0° to the right, counter-clockwise positive (y is down).
                    val degrees = Math.toDegrees(kotlin.math.atan2(a.y - b.y, b.x - a.x))
                    label = "${scaleOf.formatLength(measured.length)} · ${formatMeasure(degrees)}°"
                    at = (screen[0] + screen[1]) / 2f + Offset(8f, 8f)
                } else {
                    label = if (measured.closed) "${scaleOf.formatLength(measured.length)} · ${scaleOf.formatArea(measured.area)}"
                    else scaleOf.formatLength(measured.length)
                    at = screen.reduce { acc, p -> acc + p } / screen.size.toFloat()
                }
                val layout = textMeasurer.measure(label, TextStyle(color = FernColors.current.onScrim, fontSize = FernType.caption))
                drawRect(MEASURE_COLOR, topLeft = at - Offset(4f, 2f), size = Size(layout.size.width + 8f, layout.size.height + 4f))
                drawText(layout, topLeft = at)
            }

            // Smart guides (roadmap item 99): the lines a moving selection is snapped to.
            val (guideX, guideY) = state.smartGuides
            guideX?.let { x ->
                val sx = state.worldToScreen(WorldPoint(x, 0.0), screenCenter).x
                drawLine(GUIDE_COLOR, Offset(sx, 0f), Offset(sx, size.height), strokeWidth = 1f)
            }
            guideY?.let { y ->
                val sy = state.worldToScreen(WorldPoint(0.0, y), screenCenter).y
                drawLine(GUIDE_COLOR, Offset(0f, sy), Offset(size.width, sy), strokeWidth = 1f)
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

            drawGuides(state, screenCenter, state.editingGuides)
            if (state.activeTool == Tool.RIG) drawRig(state, screenCenter)
        }
    }
    if (navigating || gestureBadge != null) Canvas(Modifier.matchParentSize()) {
        fun badge(label: String, at: Offset) {
            val layout = textMeasurer.measure(label, TextStyle(color = FernColors.current.onScrim, fontSize = FernType.caption))
            val topLeft = at - Offset(layout.size.width / 2f, layout.size.height / 2f)
            drawRoundRect(FernColors.current.scrim, topLeft = topLeft - Offset(READOUT_GAP_PX * 2, READOUT_GAP_PX), size = Size(layout.size.width + READOUT_GAP_PX * 4, layout.size.height + READOUT_GAP_PX * 2), cornerRadius = androidx.compose.ui.geometry.CornerRadius(READOUT_GAP_PX * 2))
            drawText(layout, topLeft = topLeft)
        }
        if (navigating) badge("${(state.scale * 100).roundToInt()} % · ${Math.toDegrees(state.rotation).roundToInt()}°", Offset(size.width / 2f, NAV_BADGE_TOP_PX))
        gestureBadge?.let { (label, at, _) -> badge(label, at) }
    }
    // The selection's position and size while it's dragged (design roadmap D37), in the drawing's units.
    if (selectionReadout) state.selectionBounds()?.let { (min, max) ->
        Canvas(Modifier.matchParentSize()) {
            val screenCenter = Offset(size.width / 2f, size.height / 2f)
            val scaleOf = state.drawingScale
            val label = "${scaleOf.formatLength(min.x)}, ${scaleOf.formatLength(min.y)} · ${formatMeasure((max.x - min.x) * scaleOf.factor)} × ${scaleOf.formatLength(max.y - min.y)}"
            val layout = textMeasurer.measure(label, TextStyle(color = FernColors.current.onScrim, fontSize = FernType.caption))
            val anchor = state.worldToScreen(min, screenCenter) - Offset(0f, layout.size.height + READOUT_GAP_PX * 2)
            drawRoundRect(FernColors.current.scrim, topLeft = anchor - Offset(READOUT_GAP_PX, READOUT_GAP_PX), size = Size(layout.size.width + READOUT_GAP_PX * 2, layout.size.height + READOUT_GAP_PX * 2), cornerRadius = androidx.compose.ui.geometry.CornerRadius(READOUT_GAP_PX))
            drawText(layout, topLeft = anchor)
        }
    }
    // Where a typed transform would put the selection (design roadmap D27).
    state.transformPreview?.let { t ->
        Canvas(Modifier.matchParentSize()) {
            val screenCenter = Offset(size.width / 2f, size.height / 2f)
            val cx = t.left + t.width / 2
            val cy = t.top + t.height / 2
            val rad = Math.toRadians(t.rotationDegrees)
            val corners = listOf(t.left to t.top, t.left + t.width to t.top, t.left + t.width to t.top + t.height, t.left to t.top + t.height).map { (x, y) ->
                val dx = x - cx
                val dy = y - cy
                state.worldToScreen(WorldPoint(cx + dx * kotlin.math.cos(rad) - dy * kotlin.math.sin(rad), cy + dx * kotlin.math.sin(rad) + dy * kotlin.math.cos(rad)), screenCenter)
            }
            val outline = Path().apply {
                moveTo(corners[0].x, corners[0].y)
                for (c in corners.drop(1)) lineTo(c.x, c.y)
                close()
            }
            drawPath(outline, FernColors.current.selection, style = DrawStyle(width = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))))
        }
    }
    // The brush's size and opacity while a drag on the rail adjusts it (design roadmap D19).
    if (RailAdjust.active) Canvas(Modifier.matchParentSize()) {
        val radius = (state.activeWidthWorld * state.scale / 2).toFloat().coerceAtLeast(2f)
        drawCircle(state.activeColor.copy(alpha = state.activeOpacity.toFloat()), radius = radius, center = center)
        drawCircle(FernColors.current.selection, radius = radius + 2f, center = center, style = DrawStyle(width = 2f))
    }
    // The brush's footprint under a hovering stylus (design roadmap D16).
    hoverPoint?.let { point ->
        if (state.activeTool == Tool.PEN || state.activeTool == Tool.ERASER) Canvas(Modifier.matchParentSize()) {
            val radius = (state.activeWidthWorld * state.scale / 2).toFloat()
            if (radius < CROSSHAIR_BELOW_PX) {
                // Too small to see as a ring (design roadmap D36): a crosshair with a gap at the tip.
                val mark = FernColors.current.selection
                for ((dx, dy) in listOf(1f to 0f, -1f to 0f, 0f to 1f, 0f to -1f)) {
                    val from = point + Offset(dx, dy) * CROSSHAIR_GAP_PX
                    val to = point + Offset(dx, dy) * (CROSSHAIR_GAP_PX + CROSSHAIR_ARM_PX)
                    drawLine(Color.White, from, to, strokeWidth = 3f)
                    drawLine(mark, from, to, strokeWidth = 1f)
                }
            } else {
                drawCircle(Color.White, radius = radius + 1f, center = point, style = DrawStyle(width = 3f))
                drawCircle(FernColors.current.selection, radius = radius, center = point, style = DrawStyle(width = 1f))
            }
        }
    }
    }

    state.pendingTextEdit?.let { pending ->
        TextEditDialog(
            pending = pending,
            onConfirm = { r -> state.confirmTextEdit(r.text, r.bold, r.italic, r.sticky, r.sizePx, r.font, r.alignment, r.keepBox, r.link, r.outlined, r.features) },
            onDismiss = { state.cancelTextEdit() },
            linkChoices = linkChoices,
            onFollowLink = onFollowLink,
        )
    }
}

/**
 * A raster layer's painted tiles within the view, each scaled from its pixels to the screen.
 * Magnified pixels stay crisp (nearest neighbour) once a pixel covers two screen pixels, so
 * pixel work reads clearly; below that they're filtered.
 */
private fun DrawScope.drawRaster(surface: RasterSurface, state: CanvasState, screenCenter: Offset, topLeftWorld: WorldPoint, bottomRightWorld: WorldPoint, blendMode: BlendMode = BlendMode.SrcOver) {
    val tileWorld = RASTER_TILE * surface.pixelWorld
    val quality = if (surface.pixelWorld * state.scale >= 2.0) FilterQuality.None else FilterQuality.Low
    for ((key, tile) in surface.tiles) {
        val left = surface.originX + RasterSurface.tileX(key) * tileWorld
        val top = surface.originY + RasterSurface.tileY(key) * tileWorld
        if (left > bottomRightWorld.x || top > bottomRightWorld.y || left + tileWorld < topLeftWorld.x || top + tileWorld < topLeftWorld.y) continue
        val a = state.worldToScreen(WorldPoint(left, top), screenCenter)
        val b = state.worldToScreen(WorldPoint(left + tileWorld, top + tileWorld), screenCenter)
        val x = a.x.roundToInt()
        val y = a.y.roundToInt()
        val width = b.x.roundToInt() - x
        val height = b.y.roundToInt() - y
        if (width <= 0 || height <= 0) continue
        drawImage(surface.bitmapOf(tile).asImageBitmap(), dstOffset = IntOffset(x, y), dstSize = IntSize(width, height), filterQuality = quality, blendMode = blendMode)
    }
}

/** Blends [source] onto [target] (same size) in a CPU [mode], pixel by pixel (roadmap item 93). */
private fun blendPixels(target: ImageBitmap, source: ImageBitmap, mode: LayerBlend) {
    val width = target.width
    val height = target.height
    val below = IntArray(width * height).also { target.readPixels(it) }
    val above = IntArray(width * height).also { source.readPixels(it) }
    fun unpack(c: Int) = floatArrayOf(((c shr 16) and 0xFF) / 255f, ((c shr 8) and 0xFF) / 255f, (c and 0xFF) / 255f, (c ushr 24) / 255f)
    for (i in above.indices) {
        if (above[i] ushr 24 == 0) continue
        val r = blendPixel(mode, unpack(below[i]), unpack(above[i]), dissolveNoise(i % width, i / width))
        below[i] = ((r[3] * 255 + 0.5f).toInt().coerceIn(0, 255) shl 24) or ((r[0] * 255 + 0.5f).toInt().coerceIn(0, 255) shl 16) or
            ((r[1] * 255 + 0.5f).toInt().coerceIn(0, 255) shl 8) or (r[2] * 255 + 0.5f).toInt().coerceIn(0, 255)
    }
    target.asAndroidBitmap().setPixels(below, 0, width, 0, 0, width, height)
}

/** Applies an adjustment layer's [adjustment] to everything in [target], mixed in by [opacity]. */
private fun adjustPixels(target: ImageBitmap, adjustment: Adjustment, opacity: Float, scale: Double) {
    val width = target.width
    val height = target.height
    val pixels = IntArray(width * height).also { target.readPixels(it) }
    if (adjustment is SpatialFilter) {
        // A filter layer (roadmap item 138): sized in world units, so it's the same at any zoom.
        val sizePx = (adjustment.sizeWorld * scale).toInt().coerceIn(0, MAX_SCREEN_FILTER_PX)
        val premultiplied = IntArray(pixels.size) { premultiply(pixels[it]) }
        val filtered = premultiplied.copyOf().also { adjustment.filter(it, width, height, sizePx) }
        for (i in pixels.indices) {
            // The layer's opacity mixes the filtered picture over the original.
            val mixed = mixArgb(premultiplied[i], filtered[i], opacity)
            pixels[i] = unpremultiply(mixed)
        }
        target.asAndroidBitmap().setPixels(pixels, 0, width, 0, 0, width, height)
        return
    }
    adjustment as ColorAdjustment
    val rgb = FloatArray(3)
    for (i in pixels.indices) {
        val c = pixels[i]
        if (c ushr 24 == 0) continue
        val original = floatArrayOf(((c shr 16) and 0xFF) / 255f, ((c shr 8) and 0xFF) / 255f, (c and 0xFF) / 255f)
        original.copyInto(rgb)
        adjustment.apply(rgb)
        fun out(k: Int) = ((original[k] + (rgb[k] - original[k]) * opacity) * 255 + 0.5f).toInt().coerceIn(0, 255)
        pixels[i] = (c and 0xFF000000.toInt()) or (out(0) shl 16) or (out(1) shl 8) or out(2)
    }
    target.asAndroidBitmap().setPixels(pixels, 0, width, 0, 0, width, height)
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
    val lineColor = if (state.backgroundColor.luminance() < 0.5f) DARK_BACKGROUND_PATTERN_COLOR else BACKGROUND_PATTERN_COLOR
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
        BackgroundStyle.ISOMETRIC -> {
            // Vertical lines a lattice column apart, then the two 30° families, each a spacing
            // apart vertically ([snappedToIsometric]'s lattice).
            val column = isometricPeriodX(spacing) / 2
            var x = kotlin.math.floor(topLeftWorld.x / column) * column
            while (x <= bottomRightWorld.x) {
                val screenX = state.worldToScreen(WorldPoint(x, 0.0), screenCenter).x
                drawLine(color = lineColor, start = Offset(screenX, 0f), end = Offset(screenX, size.height), strokeWidth = 1f)
                x += column
            }
            val slope = 0.5 / (column / spacing) // tan 30°
            for (sign in listOf(1.0, -1.0)) {
                // Lines y = sign * slope * x + k * spacing crossing the view: k from the lowest
                // intercept at either view edge to the highest.
                val intercepts = listOf(topLeftWorld.x, bottomRightWorld.x).flatMap { edgeX ->
                    listOf(topLeftWorld.y - sign * slope * edgeX, bottomRightWorld.y - sign * slope * edgeX)
                }
                if ((intercepts.max() - intercepts.min()) / spacing > MAX_BACKGROUND_PATTERN_LINES) break
                var k = kotlin.math.floor(intercepts.min() / spacing)
                val last = kotlin.math.ceil(intercepts.max() / spacing)
                while (k <= last) {
                    val c = k * spacing
                    val a = state.worldToScreen(WorldPoint(topLeftWorld.x, sign * slope * topLeftWorld.x + c), screenCenter)
                    val b = state.worldToScreen(WorldPoint(bottomRightWorld.x, sign * slope * bottomRightWorld.x + c), screenCenter)
                    drawLine(color = lineColor, start = a, end = b, strokeWidth = 1f)
                    k += 1.0
                }
            }
        }
        BackgroundStyle.PLAIN -> {} // handled by the early return above
    }
}

private const val SHAPE_HOLD_POLL_MILLIS = 64L // well under the 500 ms hold, so the snap feels immediate

private const val MAX_BACKGROUND_PATTERN_LINES = 500.0
private val BACKGROUND_PATTERN_COLOR = Color(0xFFD8D3C8)
// The same faint step from the paper, the other way, for dark paper (roadmap item 73).
private val DARK_BACKGROUND_PATTERN_COLOR = Color(0xFF3C3D40)

/** Mutable holder for the most recent raw-MotionEvent pressure/tilt/orientation of pointer 0. */
private class StylusSample {
    var pressure = 1f
    var tilt = 0f
    var orientation = 0f
    var eraserEnd = false
    var buttonPressed = false
    /** When a stylus was last seen, hovering or touching (palm rejection, design roadmap D16). */
    var lastSeenMillis = 0L
}

/**
 * True if this stroke actually has stylus data worth rendering: finger input (or a saved
 * stroke from before pressure/tilt existed) reports a uniform pressure of 1.0 and zero tilt
 * at every point, which is indistinguishable from "no data" - in that case, rendering must
 * fall back to the plain smoothed path so ordinary finger-drawn strokes look exactly as they
 * always have.
 */
private fun hasStylusVariation(stroke: Stroke): Boolean {
    // Primitive accessors, not the boxed list views: this runs for every stroke every frame.
    var minPressure = 1f
    var maxPressure = 1f
    var maxTilt = 0f
    for (i in 0 until stroke.points.size) {
        val pressure = stroke.pressureAt(i)
        if (pressure < minPressure) minPressure = pressure
        if (pressure > maxPressure) maxPressure = pressure
        val tilt = stroke.tiltAt(i)
        if (tilt > maxTilt) maxTilt = tilt
    }
    return (maxPressure - minPressure) > 0.05f || maxTilt > 0.05f
}

/** Pressure/tilt combined into a single multiplier on the base stroke width at one point. */
private fun widthFactorAt(stroke: Stroke, index: Int): Float {
    val inRange = index in 0 until stroke.points.size
    val pressure = (if (inRange) stroke.pressureAt(index) else 1f).coerceIn(0.1f, 1f)
    val tilt = if (inRange) stroke.tiltAt(index) else 0f
    // More tilt lays down more ink, like a real pen/marker tipped over - up to 80% wider at
    // maximum tilt (~90°, a stylus flat against the screen).
    val tiltFactor = 1f + (tilt / (Math.PI.toFloat() / 2f)).coerceIn(0f, 1f) * 0.8f
    return pressure * tiltFactor
}

/**
 * Renders one stroke's already screen-space points, styled per [Stroke.penType]. Marker,
 * pencil, and highlighter share the same smoothed-path construction (quadratic Beziers
 * through successive midpoints - see the class doc) with a different color/width/cap, unless
 * the stroke actually carries stylus pressure/tilt data, in which case it's filled as a
 * variable-width outline instead (like calligraphy) so the width can vary along its length.
 * Calligraphy always needs per-point width, so it's built entirely differently regardless.
 */
private fun DrawScope.drawStroke(stroke: Stroke, screenPoints: List<Offset>, source: IntArray, baseWidthScreen: Float, blendMode: BlendMode) {
    if (stroke.penType == PenType.CALLIGRAPHY) {
        drawCalligraphyStroke(stroke, screenPoints, source, baseWidthScreen, blendMode)
        return
    }
    if (stroke.penType == PenType.AIRBRUSH || stroke.penType == PenType.CRAYON) {
        drawTexturedStroke(stroke, screenPoints, source, baseWidthScreen, blendMode)
        return
    }
    if (stroke.penType == PenType.STAMP) {
        drawStampStroke(stroke, screenPoints, source, baseWidthScreen, blendMode)
        return
    }
    val penDefaults = penStyle(stroke.penType, stroke.color, baseWidthScreen)
    // The stroke's own line style (roadmap item 97) overrides the pen's cap and the round join.
    val line = stroke.lineStyle
    val style = when (line?.cap) {
        LineCap.ROUND -> penDefaults.copy(cap = StrokeCap.Round)
        LineCap.SQUARE -> penDefaults.copy(cap = StrokeCap.Square)
        LineCap.BUTT -> penDefaults.copy(cap = StrokeCap.Butt)
        null -> penDefaults
    }
    val join = when (line?.join) {
        LineJoin.MITER -> StrokeJoin.Miter
        LineJoin.BEVEL -> StrokeJoin.Bevel
        else -> StrokeJoin.Round
    }
    val profile = line?.profile ?: WidthProfile.UNIFORM
    if (screenPoints.size >= 2 && (hasStylusVariation(stroke) || profile != WidthProfile.UNIFORM)) {
        // A width profile is applied by length along the stroke, on top of any pen pressure.
        val along = FloatArray(screenPoints.size)
        for (i in 1 until screenPoints.size) along[i] = along[i - 1] + (screenPoints[i] - screenPoints[i - 1]).getDistance()
        val total = along.last().coerceAtLeast(1e-3f)
        val halfWidths = FloatArray(screenPoints.size) { i ->
            (style.widthScreen * widthFactorAt(stroke, source[i]) * profile.at(along[i] / total)).coerceAtLeast(1f) / 2f
        }
        val cap = when (style.cap) {
            StrokeCap.Square -> OutlineCap.SQUARE
            StrokeCap.Butt -> OutlineCap.BUTT
            else -> OutlineCap.ROUND
        }
        drawOutline(screenPoints, halfWidths, cap, style.color, blendMode)
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
            pathEffect = if (stroke.dashed) dashEffect(style.widthScreen) else null,
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
                style = DrawStyle(
                    width = style.widthScreen,
                    cap = style.cap,
                    join = join,
                    pathEffect = if (stroke.dashed) dashEffect(style.widthScreen) else null,
                ),
                blendMode = blendMode,
            )
        }
    }
}

/** Dash 3x the line width, gap 2x (plus the round caps), so dashes stay proportioned at any width or zoom. */
private fun dashEffect(widthScreen: Float): PathEffect =
    PathEffect.dashPathEffect(floatArrayOf(widthScreen * 3f, widthScreen * 2f + widthScreen))

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
    // Unused: these pens have their own renderers (drawCalligraphyStroke, drawTexturedStroke, drawStampStroke).
    PenType.CALLIGRAPHY, PenType.AIRBRUSH, PenType.CRAYON, PenType.PIXEL, PenType.STAMP -> PenStyle(baseColor, baseWidthScreen, StrokeCap.Round)
}

/**
 * Procedural textured pens. Every "random" value comes from a hash of the point index and
 * sample number, never a live random generator, so the texture is identical every frame and
 * doesn't shimmer while panning; it's laid out in screen space, so it keeps a constant grain
 * size at any zoom.
 * - AIRBRUSH: [AIRBRUSH_DOTS_PER_SAMPLE] dots per sample, spread around the path within the
 *   stroke width with a centre-weighted falloff, each faint so overlaps build up density.
 * - CRAYON: the line drawn as short segments with grain gaps, plus a jittered second pass, so
 *   it reads as wax over textured paper.
 */
private fun DrawScope.drawTexturedStroke(stroke: Stroke, screenPoints: List<Offset>, source: IntArray, baseWidthScreen: Float, blendMode: BlendMode) {
    val radius = baseWidthScreen / 2f
    // Walk the path at a fixed screen spacing so texture density doesn't depend on how fast
    // (and so how sparsely) the points were captured.
    val step = (radius / 2f).coerceAtLeast(1f)
    var sample = 0
    for (i in 0 until maxOf(1, screenPoints.size - 1)) {
        val a = screenPoints[i]
        val b = screenPoints.getOrElse(i + 1) { a }
        val segment = b - a
        val length = segment.getDistance()
        val count = maxOf(1, (length / step).toInt())
        for (k in 0 until count) {
            val center = a + segment * (k.toFloat() / count)
            val width = baseWidthScreen * widthFactorAt(stroke, source[i])
            when (stroke.penType) {
                PenType.AIRBRUSH -> for (d in 0 until AIRBRUSH_DOTS_PER_SAMPLE) {
                    val angle = hash01(sample, d * 2) * 2f * Math.PI.toFloat()
                    // Squaring the radius fraction concentrates dots towards the centre line.
                    val distance = hash01(sample, d * 2 + 1).let { it * it } * width
                    drawCircle(
                        color = stroke.color.copy(alpha = stroke.color.alpha * 0.25f),
                        radius = (width / 24f).coerceAtLeast(0.75f),
                        center = center + Offset(cos(angle) * distance, sin(angle) * distance),
                        blendMode = blendMode,
                    )
                }
                else -> {
                    // About one sample in five is a grain gap where the paper shows through.
                    if (hash01(sample, 0) > 0.2f) {
                        val next = a + segment * ((k + 1).toFloat() / count)
                        val jitter = Offset(hash01(sample, 1) - 0.5f, hash01(sample, 2) - 0.5f) * (width * 0.3f)
                        drawLine(stroke.color.copy(alpha = stroke.color.alpha * 0.8f), center, next, strokeWidth = width * 0.8f, cap = StrokeCap.Round, blendMode = blendMode)
                        drawLine(stroke.color.copy(alpha = stroke.color.alpha * 0.4f), center + jitter, next + jitter, strokeWidth = width * 0.5f, cap = StrokeCap.Round, blendMode = blendMode)
                    }
                }
            }
            sample++
        }
    }
}

private const val AIRBRUSH_DOTS_PER_SAMPLE = 6

/**
 * A [PenType.STAMP] stroke: its brush's tip drawn at each of [stampPlacements]. Each stamp is
 * opaque in the stroke's colour, so a translucent colour builds up where stamps overlap - the
 * way dab-based brushes behave in paint apps.
 */
private fun DrawScope.drawStampStroke(stroke: Stroke, screenPoints: List<Offset>, source: IntArray, baseWidthScreen: Float, blendMode: BlendMode) {
    val brush = stroke.brush ?: StampBrush()
    val stamps = stampPlacements(screenPoints, baseWidthScreen, brush) { i -> widthFactorAt(stroke, source[i]) }
    for (stamp in stamps) drawStamp(brush.tip, stamp, stroke.color, blendMode, brush.mask)
}

// One tintable bitmap per image tip, shared by every stamp and frame; weak so unused tips go.
private val tipBitmaps = java.util.WeakHashMap<TipMask, ImageBitmap>()

private fun tipBitmap(mask: TipMask): ImageBitmap = tipBitmaps.getOrPut(mask) {
    val colors = IntArray(mask.size * mask.size) { ((mask.alpha[it].toInt() and 0xFF) shl 24) or 0xFFFFFF }
    android.graphics.Bitmap.createBitmap(colors, mask.size, mask.size, android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap()
}

/** Draws one stamp of [tip]; shared with the brush editor's preview. */
fun DrawScope.drawStamp(tip: StampTip, stamp: Stamp, color: Color, blendMode: BlendMode = BlendMode.SrcOver, mask: TipMask? = null) {
    val radius = (stamp.size / 2f).coerceAtLeast(0.5f)
    when (tip) {
        // The picture's coverage tinted with the stroke colour (SrcIn keeps its alpha, takes the colour).
        StampTip.IMAGE -> if (mask == null) drawCircle(color, radius = radius, center = stamp.center, blendMode = blendMode) else {
            val side = (radius * 2f).toInt().coerceAtLeast(1)
            rotate(Math.toDegrees(stamp.angle.toDouble()).toFloat(), pivot = stamp.center) {
                drawImage(
                    tipBitmap(mask),
                    dstOffset = IntOffset((stamp.center.x - side / 2f).toInt(), (stamp.center.y - side / 2f).toInt()),
                    dstSize = IntSize(side, side),
                    colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(color, BlendMode.SrcIn),
                    blendMode = blendMode,
                )
            }
        }
        StampTip.CIRCLE -> drawCircle(color, radius = radius, center = stamp.center, blendMode = blendMode)
        // Soft: a radial falloff from full colour at the centre to transparent at the rim.
        StampTip.SOFT -> drawCircle(
            brush = Brush.radialGradient(listOf(color, color.copy(alpha = 0f)), center = stamp.center, radius = radius),
            radius = radius,
            center = stamp.center,
            blendMode = blendMode,
        )
        StampTip.SQUARE -> rotate(Math.toDegrees(stamp.angle.toDouble()).toFloat(), pivot = stamp.center) {
            drawRect(color, topLeft = stamp.center - Offset(radius, radius), size = Size(radius * 2f, radius * 2f), blendMode = blendMode)
        }
        StampTip.STAR -> {
            val path = Path()
            starOutline(stamp.size, stamp.angle).forEachIndexed { k, p ->
                if (k == 0) path.moveTo(stamp.center.x + p.x, stamp.center.y + p.y) else path.lineTo(stamp.center.x + p.x, stamp.center.y + p.y)
            }
            path.close()
            drawPath(path, color, blendMode = blendMode)
        }
    }
}

// Drawn points closer than this on screen are skipped: below a pixel they add no visible detail.
private const val MIN_POINT_SPACING_SCREEN = 1.0

/**
 * Pixel-art rendering: the grid is in world space with cells [Stroke.widthWorld] wide,
 * anchored at the world origin, so cells from different strokes of the same width line up.
 * Every cell between consecutive points is filled (Bresenham's line), so fast movement leaves
 * no gaps, and each cell is drawn once as a square. The grid is anchored at the world origin,
 * which the floating-origin rebase moves in whole 40-unit steps: cells whose size divides 40
 * stay aligned across a rebase.
 */
private fun DrawScope.drawPixelStroke(stroke: Stroke, state: CanvasState, screenCenter: Offset) {
    val cell = stroke.widthWorld
    if (cell <= 0.0 || stroke.points.isEmpty()) return
    val points = stroke.points
    fun cellOf(p: WorldPoint) = kotlin.math.floor(p.x / cell).toLong() to kotlin.math.floor(p.y / cell).toLong()
    val cells = LinkedHashSet<Pair<Long, Long>>()
    var previous = cellOf(points[0])
    cells.add(previous)
    for (i in 1 until points.size) {
        val next = cellOf(points[i])
        // Bresenham between the two cells.
        var x = previous.first
        var y = previous.second
        val dx = kotlin.math.abs(next.first - x)
        val dy = -kotlin.math.abs(next.second - y)
        val sx = if (x < next.first) 1 else -1
        val sy = if (y < next.second) 1 else -1
        var error = dx + dy
        while (true) {
            cells.add(x to y)
            if (x == next.first && y == next.second) break
            val doubled = 2 * error
            if (doubled >= dy) { error += dy; x += sx }
            if (doubled <= dx) { error += dx; y += sy }
        }
        previous = next
    }
    for ((cx, cy) in cells) {
        val topLeft = state.worldToScreen(WorldPoint(cx * cell, cy * cell), screenCenter)
        val bottomRight = state.worldToScreen(WorldPoint((cx + 1) * cell, (cy + 1) * cell), screenCenter)
        drawRect(color = stroke.color, topLeft = topLeft, size = Size(bottomRight.x - topLeft.x, bottomRight.y - topLeft.y))
    }
}

/**
 * Comic screentone: a dot grid clipped to the fill's outline, offset every other row like
 * printed halftone. Laid out in screen space at a fixed 8 px pitch so the tone reads the same
 * at any zoom (and the dot count stays bounded however large the fill is on screen).
 */
private fun DrawScope.drawScreentone(outline: Path, color: Color, screenPoints: List<Offset>) {
    val pitch = 8f
    val left = screenPoints.minOf { it.x }.coerceAtLeast(0f)
    val right = screenPoints.maxOf { it.x }.coerceAtMost(size.width)
    val top = screenPoints.minOf { it.y }.coerceAtLeast(0f)
    val bottom = screenPoints.maxOf { it.y }.coerceAtMost(size.height)
    clipPath(outline) {
        var row = 0
        var y = top
        while (y <= bottom) {
            var x = left + if (row % 2 == 0) 0f else pitch / 2f
            while (x <= right) {
                drawCircle(color = color, radius = 2f, center = Offset(x, y))
                x += pitch
            }
            y += pitch
            row++
        }
    }
}

/** A stable pseudo-random value in [0, 1) from two integers (integer hash, no state). */
private fun hash01(a: Int, b: Int): Float {
    var h = a * 374761393 + b * 668265263
    h = (h xor (h ushr 13)) * 1274126177
    h = h xor (h ushr 16)
    return (h and 0x7FFFFFFF) / 2147483648f
}

/**
 * Simulates a flat calligraphy nib: each segment's width depends on how that segment's
 * direction relates to the nib's angle - widest when drawing across the nib's edge
 * (perpendicular to it), thinnest when drawing along it. Filled as one variable-width
 * outline ([variableWidthOutline]) rather than stroked, since a single Path/Stroke style can't
 * vary width along its length.
 *
 * Uses the stylus's actual tilt orientation as the nib angle when real tilt data is present
 * (a genuinely tilt-responsive nib), falling back to a fixed 45° for finger input or a
 * stylus held upright. `MotionEvent.getOrientation()`'s convention (0 = tilted toward the
 * top of the device, increasing clockwise) is converted to this file's angle convention (0 =
 * pointing along +X, matching `atan2`) with a quarter-turn offset - approximate, and unverified
 * against real stylus hardware.
 */
private fun DrawScope.drawCalligraphyStroke(stroke: Stroke, screenPoints: List<Offset>, source: IntArray, baseWidthScreen: Float, blendMode: BlendMode) {
    if (screenPoints.size < 2) {
        if (screenPoints.size == 1) drawCircle(stroke.color, radius = baseWidthScreen / 2f, center = screenPoints[0], blendMode = blendMode)
        return
    }
    val pointCount = stroke.points.size
    var tiltSum = 0.0
    for (i in 0 until pointCount) tiltSum += stroke.tiltAt(i)
    val avgTilt = if (pointCount == 0) 0f else (tiltSum / pointCount).toFloat()
    val useRealNibAngle = avgTilt > 0.1f
    // Width is a property of each segment's direction; a point takes the mean of the segments
    // on either side of it, so the outline changes width smoothly through each point.
    val segmentWidths = FloatArray(screenPoints.size - 1)
    for (i in 0 until screenPoints.size - 1) {
        val a = screenPoints[i]
        val b = screenPoints[i + 1]
        val angle = atan2(b.y - a.y, b.x - a.x)
        val nibAngle = if (useRealNibAngle) {
            val orientation = stroke.orientationAt(source[i])
            orientation + (Math.PI.toFloat() / 2f)
        } else {
            CALLIGRAPHY_NIB_ANGLE
        }
        val pressureFactor = ((stroke.pressureAt(source[i]) + stroke.pressureAt(source[i + 1])) / 2f).coerceIn(0.1f, 1f)
        val widthFactor = (0.25f + 0.75f * abs(sin(angle - nibAngle))) * pressureFactor
        segmentWidths[i] = (baseWidthScreen * widthFactor).coerceAtLeast(1f)
    }
    val halfWidths = FloatArray(screenPoints.size) { i ->
        val before = segmentWidths.getOrElse(i - 1) { segmentWidths[i] }
        val after = segmentWidths.getOrElse(i) { segmentWidths[i - 1] }
        (before + after) / 4f
    }
    drawOutline(screenPoints, halfWidths, OutlineCap.ROUND, stroke.color, blendMode)
}

/**
 * Fills a variable-width stroke as one shape (see [variableWidthOutline]), so translucent ink
 * isn't darkened where its segments would otherwise overlap. A stroke whose points all
 * coincide has no outline and is drawn as a dot.
 */
private fun DrawScope.drawOutline(screenPoints: List<Offset>, halfWidths: FloatArray, cap: OutlineCap, color: Color, blendMode: BlendMode) {
    val outline = variableWidthOutline(screenPoints, halfWidths, cap)
    if (outline.isEmpty()) {
        drawCircle(color, radius = halfWidths.max(), center = screenPoints[0], blendMode = blendMode)
        return
    }
    val path = Path()
    path.moveTo(outline[0].x, outline[0].y)
    for (i in 1 until outline.size) path.lineTo(outline[i].x, outline[i].y)
    path.close()
    drawPath(path = path, color = color, blendMode = blendMode)
}

private const val CALLIGRAPHY_NIB_ANGLE = (Math.PI / 4).toFloat() // 45°, a standard calligraphy nib angle

/** The selection's scale handle (bottom-right corner) and rotate handle (above top-center), in screen space. */
private data class SelectionHandles(val scaleHandle: Offset, val rotateHandle: Offset, val topCenter: Offset)

private fun selectionHandles(state: CanvasState, screenCenter: Offset): SelectionHandles? {
    // While editing nodes, a node on the bounds' corner must be draggable, not the scale handle.
    if (state.nodeEditStroke != null) return null
    val bounds = state.selectionBounds() ?: return null
    val (min, max) = bounds
    val scaleHandle = state.worldToScreen(max, screenCenter)
    val topCenter = state.worldToScreen(WorldPoint((min.x + max.x) / 2.0, min.y), screenCenter)
    val rotateHandle = Offset(topCenter.x, topCenter.y - ROTATE_HANDLE_OFFSET_SCREEN)
    return SelectionHandles(scaleHandle, rotateHandle, topCenter)
}

// Both on the §7 scale (Primary tier).
// Half a 48 dp touch target around each handle (design roadmap D39).
private val HANDLE_HIT = FernScale.s24
private const val ROTATE_HANDLE_OFFSET_SCREEN = 32f

private val SELECTION_HIGHLIGHT_COLOR: Color get() = FernColors.current.selection
private val MARQUEE_BORDER_COLOR: Color get() = FernColors.current.selection
// Aseprite's onion skin colours (roadmap item 145): earlier frames red, later ones blue.
private val ONION_BEFORE_TINT: Color get() = FernColors.current.before
private val ONION_AFTER_TINT: Color get() = FernColors.current.after
// The play badge on an embedded video (roadmap item 107).
private val VIDEO_BADGE_COLOR: Color get() = FernColors.current.scrim
private const val VIDEO_BADGE_RADIUS_PX = 24f
// The measure tool's line and readout (roadmap item 143).
private val MEASURE_COLOR: Color get() = FernColors.current.measure
// Game objects' hitboxes and links, while editing (roadmap item 157).
private val GAME_GUIDE_COLOR: Color get() = FernColors.current.game
// A frame's layout grid bands (roadmap item 142): the frame colour, faint, so overlaps read darker.
private val LAYOUT_GRID_COLOR: Color get() = FernColors.current.frame.copy(alpha = 0.1f)
// The shape builder's remove drag and the knife's cut line (roadmap item 141).
private val SHAPE_CUT_COLOR: Color get() = FernColors.current.danger
// Widest a text box is laid out on screen; beyond this (deep zoom) the box is cropped to it.
private const val MAX_TEXT_BOX_PX = 65536.0

private val CONNECTOR_COLOR: Color get() = FernColors.current.neutral

/** A connector arrow from [a] to [b] in screen space: a 2 px line and an arrowhead at [b]. */
// Bone overlay: a tapered wedge from joint to tail, with a ring on the joint (sizes in px on the §7 scale).
private val BONE_COLOR: Color get() = FernColors.current.construction.copy(alpha = 0.8f)
private val SELECTED_BONE_COLOR: Color get() = FernColors.current.active
private const val BONE_HALF_WIDTH_PX = 6f
private const val BONE_JOINT_RADIUS_PX = 6f

private val GUIDE_COLOR: Color get() = FernColors.current.guide

/**
 * The shape-tool readout: a line or arrow's length, otherwise the box's width x height. Whole
 * world units without a drawing scale; with one (roadmap item 143), three significant digits
 * and the unit.
 */
internal fun shapeDimensions(kind: ShapeKind, start: WorldPoint, end: WorldPoint, drawingScale: DrawingScale? = null): String {
    val dx = abs(end.x - start.x)
    val dy = abs(end.y - start.y)
    // A line's angle too, counter-clockwise from the right as on a protractor (design roadmap D37).
    val angle = "%.0f°".format(Math.toDegrees(kotlin.math.atan2(-(end.y - start.y), end.x - start.x)))
    if (drawingScale != null) return when (kind) {
        ShapeKind.LINE, ShapeKind.ARROW -> "${drawingScale.formatLength(kotlin.math.hypot(dx, dy))} · $angle"
        else -> "${formatMeasure(dx * drawingScale.unitsPerWorld)} × ${drawingScale.formatLength(dy)}"
    }
    return when (kind) {
        ShapeKind.LINE, ShapeKind.ARROW -> "%.0f · %s".format(kotlin.math.hypot(dx, dy), angle)
        else -> "%.0f × %.0f".format(dx, dy)
    }
}

private fun DrawScope.drawBone(head: Offset, tail: Offset, selected: Boolean) {
    val color = if (selected) SELECTED_BONE_COLOR else BONE_COLOR
    val direction = tail - head
    val length = direction.getDistance()
    if (length > 0f) {
        val normal = Offset(-direction.y / length, direction.x / length) * BONE_HALF_WIDTH_PX
        val shoulder = head + direction * 0.2f
        val path = Path().apply {
            moveTo(head.x, head.y)
            lineTo(shoulder.x + normal.x, shoulder.y + normal.y)
            lineTo(tail.x, tail.y)
            lineTo(shoulder.x - normal.x, shoulder.y - normal.y)
            close()
        }
        drawPath(path, color.copy(alpha = color.alpha * 0.5f))
        drawPath(path, color, style = DrawStyle(width = 2f))
    }
    drawCircle(color, BONE_JOINT_RADIUS_PX, head, style = DrawStyle(width = 2f))
}

internal fun DrawScope.drawConnector(a: Offset, b: Offset, color: Color = CONNECTOR_COLOR) {
    drawLine(color, a, b, strokeWidth = 2f, cap = StrokeCap.Round)
    val direction = b - a
    val length = direction.getDistance()
    if (length < 1f) return
    val unit = direction / length
    val normal = Offset(-unit.y, unit.x)
    // A 12 px long arrowhead, 6 px each side of the line (both on the §7 scale).
    val back = b - unit * 12f
    val head = Path().apply {
        moveTo(b.x, b.y)
        lineTo(back.x + normal.x * 6f, back.y + normal.y * 6f)
        lineTo(back.x - normal.x * 6f, back.y - normal.y * 6f)
        close()
    }
    drawPath(head, color)
}

// Classic sticky-note yellow.
private val STICKY_NOTE_COLOR = Color(0xFFFFF59D)

// Faint enough to read as "the previous drawing" without competing with the current one.
private const val ONION_SKIN_ALPHA = 0.25f
private const val AUDIO_AHEAD_ALPHA = 0.2f
private const val SELECTION_MASK_ALPHA = 0.35f
private val SELECTION_MASK_TINT: Color get() = FernColors.current.danger
private const val MAX_SCREEN_FILTER_PX = 256 // a filter layer's reach on screen, zoomed in far

private fun premultiply(c: Int): Int {
    val a = c ushr 24
    fun ch(shift: Int) = ((c shr shift) and 0xFF) * a / 255
    return (a shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
}

private fun unpremultiply(c: Int): Int {
    val a = c ushr 24
    if (a == 0) return 0
    fun ch(shift: Int) = (((c shr shift) and 0xFF) * 255 / a).coerceAtMost(255)
    return (a shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
}

/** [from] moved toward [to] by [t], channel by channel. */
private fun mixArgb(from: Int, to: Int, t: Float): Int {
    fun ch(shift: Int): Int {
        val a = (from shr shift) and 0xFF
        val b = (to shr shift) and 0xFF
        return (a + (b - a) * t).roundToInt().coerceIn(0, 255)
    }
    return (ch(24) shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
}

// One wheel notch pans 64 px, or zooms by 10% with Ctrl held.
private const val WHEEL_PAN_PX = 64f
private const val WHEEL_ZOOM_STEP = 1.1

// After a stylus was last seen, strict palm rejection ignores touches this long.
private const val PALM_NEAR_MILLIS = 1024L
// Padding around an on-canvas readout.
private const val READOUT_GAP_PX = 4f
// Two two-finger taps this close together are a double tap.
private const val DOUBLE_TAP_MILLIS = 384L
// How long a gesture's badge shows, and where the zoom readout sits below the top edge.
private const val GESTURE_BADGE_MILLIS = 768L
private const val NAV_BADGE_TOP_PX = 128f
// Under this footprint radius the hover cursor is a crosshair; its gap and arm lengths.
private const val CROSSHAIR_BELOW_PX = 4f
private const val CROSSHAIR_GAP_PX = 4f
private const val CROSSHAIR_ARM_PX = 8f
private const val LONG_PRESS_SLOP_PX = 24f
// A three-finger swipe: this far, quickly, without pinching.
private const val SWIPE_MIN_PX = 192f
private const val SWIPE_SPAN_SLOP_PX = 48f
private const val SWIPE_MAX_MILLIS = 768L

// Translucent teal so the axes guide without competing with the drawing.
private val SYMMETRY_COLOR: Color get() = FernColors.current.measure.copy(alpha = 0.4f)
private val PERSPECTIVE_RAY_COLOR: Color get() = FernColors.current.construction.copy(alpha = 0.2f)
private val PERSPECTIVE_HORIZON_COLOR: Color get() = FernColors.current.construction.copy(alpha = 0.6f)
// One ray every 15 degrees.
private const val PERSPECTIVE_RAYS = 24
// Muted purple, distinct from the blue selection/marquee colours.
private val FRAME_COLOR: Color get() = FernColors.current.frame
private val MARQUEE_FILL_COLOR: Color get() = FernColors.current.selection.copy(alpha = 0.1f)
private val HANDLE_COLOR: Color get() = FernColors.current.selection
private val DRAWING_GUIDE_COLOR: Color get() = FernColors.current.active

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
    drawPaper: Boolean = true,
    cpuTarget: ImageBitmap? = null,
    cpuOrigin: Offset = Offset.Zero,
) {
    // Blend modes the GPU doesn't have, and adjustment layers, need what's beneath them as pixels
    // (roadmap item 93): the whole scene is drawn into an offscreen bitmap covering the screen
    // at any rotation, those layers are composited into it per pixel, and the result is drawn.
    if (cpuTarget == null && state.layers.any { it.visible && (it.blend.gpu == null || it.adjustment != null) }) {
        val half = hypot(size.width, size.height) / 2f
        val origin = screenCenter - Offset(half, half)
        val side = kotlin.math.ceil(2 * half).toInt().coerceAtLeast(1)
        val target = ImageBitmap(side, side)
        CanvasDrawScope().draw(this, layoutDirection, androidx.compose.ui.graphics.Canvas(target), Size(side.toFloat(), side.toFloat())) {
            withTransform({ translate(-origin.x, -origin.y) }) {
                // The paper colour is part of the backdrop the layers blend with.
                if (drawPaper) drawRect(state.backgroundColor, topLeft = origin, size = Size(side.toFloat(), side.toFloat()))
                drawScene(state, screenCenter, topLeftWorld, bottomRightWorld, textMeasurer, imageFor, drawPaper, target, origin)
            }
        }
        drawImage(target, topLeft = origin)
        return
    }
    if (drawPaper) drawBackgroundPattern(state, screenCenter, topLeftWorld, bottomRightWorld)

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
    // Only the current animation frame is drawn (outside animation everything is frame 0), plus
    // neighbouring frames as a faint onion skin while editing an animation: [onionBefore] before
    // and [onionAfter] after, fainter the further they are, tinted red and blue when asked
    // (roadmap item 145).
    val onionFrames = if (state.animationMode && state.onionSkin && !state.playing) {
        (1..state.onionBefore).map { state.currentAnimFrame - it to it } + (1..state.onionAfter).map { state.currentAnimFrame + it to it }
    } else {
        emptyList()
    }.filter { (frame, _) -> frame in 0 until state.animFrameCount }
    // Only strokes near the view, from the spatial index - not a pass over the whole document -
    // plus, while a game plays (roadmap item 155), every object's strokes wherever they've gone.
    val world = state.play
    state.playRevision // redraw on each game step
    val nearby = state.strokesNear(topLeftWorld.x, topLeftWorld.y, bottomRightWorld.x, bottomRightWorld.y).let { near ->
        if (world == null) near else (near + state.strokes.filter { world.instanceOf(it.groupId) != null }).distinct()
    }
    // Replay draws the first replayCount strokes of the document, in drawing order.
    val replayedOrAll = state.replayCount?.let { count -> state.strokes.take(count).toHashSet().let { shown -> nearby.filter { it in shown } } } ?: nearby
    // Audio sync (roadmap item 130): ink the playing voice note hasn't reached yet is drawn
    // faded, underneath, until the playback gets to it.
    val audio = state.audioReplay
    val ahead = if (audio == null) emptyList() else replayedOrAll.filter { audio.isAhead(it) }
    val replayed = if (ahead.isEmpty()) replayedOrAll else ahead.toHashSet().let { later -> replayedOrAll.filter { it !in later } }
    // A playing object shows the frame its state's animation is on (roadmap item 158).
    val strokesByLayer = replayed.filter { s ->
        val shown = world?.instanceOf(s.groupId)?.frame ?: state.currentAnimFrame
        s.animFrame == shown
    }.let { shownStrokes ->
        // The rig's attached ink, posed (roadmap items 250, 353): drawn on its own layers like any ink.
        val rigInk = state.rigEditor.posedStrokes()
        if (rigInk.isEmpty()) shownStrokes else shownStrokes + rigInk
    }.groupBy { layerIndex[it.layerId] ?: 0 }
    // Offscreen bounds for a translucent layer: the square around the screen centre that covers
    // the screen at any canvas rotation.
    val halfDiagonal = hypot(size.width, size.height) / 2f
    val layerBounds = Rect(screenCenter - Offset(halfDiagonal, halfDiagonal), screenCenter + Offset(halfDiagonal, halfDiagonal))
    // Furthest first, so nearer frames show over them.
    for ((onionFrame, distance) in onionFrames.sortedByDescending { it.second }) {
        val tint = when {
            !state.onionTinted -> null
            onionFrame < state.currentAnimFrame -> ONION_BEFORE_TINT
            else -> ONION_AFTER_TINT
        }
        drawContext.canvas.saveLayer(layerBounds, Paint().apply {
            alpha = ONION_SKIN_ALPHA / distance
            if (tint != null) colorFilter = ColorFilter.tint(tint, BlendMode.SrcIn)
        })
        for (stroke in nearby) {
            if (stroke.animFrame != onionFrame || stroke.points.isEmpty()) continue
            if (state.layers.firstOrNull { it.id == stroke.layerId }?.visible == false) continue
            if (!stroke.intersects(topLeftWorld.x, topLeftWorld.y, bottomRightWorld.x, bottomRightWorld.y)) continue
            val source = stroke.decimatedIndices(MIN_POINT_SPACING_SCREEN / state.scale)
            val screenPoints = source.map { state.worldToScreen(stroke.points[it], screenCenter) }
            val widthScreen = (stroke.widthWorld * state.scale).toFloat().coerceIn(1f, 1_000_000f)
            drawStroke(stroke, screenPoints, source, widthScreen, BlendMode.SrcOver)
        }
        drawContext.canvas.restore()
    }

    if (ahead.isNotEmpty()) {
        drawContext.canvas.saveLayer(layerBounds, Paint().apply { alpha = AUDIO_AHEAD_ALPHA })
        for (stroke in ahead) {
            if (stroke.animFrame != state.currentAnimFrame || stroke.points.isEmpty()) continue
            if (state.layers.firstOrNull { it.id == stroke.layerId }?.visible == false) continue
            val source = stroke.decimatedIndices(MIN_POINT_SPACING_SCREEN / state.scale)
            val screenPoints = source.map { state.worldToScreen(stroke.points[it], screenCenter) }
            val widthScreen = (stroke.widthWorld * state.scale).toFloat().coerceIn(1f, 1_000_000f)
            drawStroke(stroke, screenPoints, source, widthScreen, BlendMode.SrcOver)
        }
        drawContext.canvas.restore()
    }

    state.rasterRevision // repaint as raster pixels change
    // One layer's own content: its pixels, or its strokes in list order.
    fun DrawScope.drawLayerContent(index: Int, layer: Layer) {
        if (layer.raster) {
            state.rasters[layer.id]?.let { drawRaster(it, state, screenCenter, topLeftWorld, bottomRightWorld) }
            return
        }
        val layerStrokes = strokesByLayer[index] ?: return
        // One stroke where it's drawn; [culled] skips it when it's off screen (not for a playing
        // object, which may have moved anywhere).
        fun DrawScope.drawOne(stroke: Stroke, culled: Boolean) {
            // Reading revision here (and only here) is what subscribes this draw phase to
            // the stroke's mutations, so a redraw is triggered as each new point is added
            // mid-stroke rather than waiting for an unrelated pan/zoom to happen to redraw.
            stroke.revision
            if (culled && !stroke.intersects(topLeftWorld.x, topLeftWorld.y, bottomRightWorld.x, bottomRightWorld.y)) return
            val points = stroke.points
            if (points.isEmpty()) return
            // Level of detail: a stroke smaller than a pixel on screen (far zoomed out) is
            // drawn as one dot, skipping the per-point transform and path building that
            // dominate the cost of drawing thousands of distant strokes.
            // Width counts too: a single-point dot has zero extent but can be large on screen.
            val extentScreen = (maxOf(stroke.maxX - stroke.minX, stroke.maxY - stroke.minY) + stroke.widthWorld) * state.scale
            if (extentScreen < 1.0) {
                val center = state.worldToScreen(WorldPoint((stroke.minX + stroke.maxX) / 2.0, (stroke.minY + stroke.maxY) / 2.0), screenCenter)
                drawCircle(color = stroke.color, radius = 0.5f, center = center)
                return
            }
            // Points closer together than a pixel on screen add path-building cost but no
            // visible detail, so only those at least MIN_POINT_SPACING_SCREEN apart are drawn;
            // [source] maps each drawn point back to its index for pressure/tilt lookups.
            val source = stroke.decimatedIndices(MIN_POINT_SPACING_SCREEN / state.scale)
            val screenPoints = source.map { state.worldToScreen(points[it], screenCenter) }
            if (stroke.filled) {
                val path = Path()
                path.moveTo(screenPoints[0].x, screenPoints[0].y)
                for (i in 1 until screenPoints.size) path.lineTo(screenPoints[i].x, screenPoints[i].y)
                path.close()
                if (stroke.screentone) {
                    drawScreentone(path, stroke.color, screenPoints)
                } else if (stroke.gradient != null) {
                    val (start, end) = gradientEndpoints(screenPoints, stroke.gradient.angleDegrees)
                    drawPath(path = path, brush = Brush.linearGradient(listOf(stroke.color, stroke.gradient.endColor), start = start, end = end))
                } else if (stroke.fadeFill) {
                    // Gradient spans the fill's own height on screen, so it zooms with the shape.
                    val top = screenPoints.minOf { it.y }
                    val bottom = screenPoints.maxOf { it.y }
                    drawPath(
                        path = path,
                        brush = Brush.verticalGradient(listOf(stroke.color, stroke.color.copy(alpha = 0f)), startY = top, endY = bottom),
                    )
                } else {
                    drawPath(path = path, color = stroke.color)
                }
                return
            }
            val rawWidthScreen = stroke.widthWorld * state.scale
            if (!rawWidthScreen.isFinite()) return
            val widthScreen = rawWidthScreen.toFloat().coerceIn(1f, 1_000_000f)
            if (stroke.penType == PenType.PIXEL) drawPixelStroke(stroke, state, screenCenter)
            else drawStroke(stroke, screenPoints, source, widthScreen, stroke.blendMode.toComposeBlendMode())
        }
        for (stroke in layerStrokes) {
            // While a game plays (roadmap item 155), an object's strokes are drawn where it is now.
            val playing = world?.instanceOf(stroke.groupId)
            if (playing == null) {
                drawOne(stroke, culled = true)
                continue
            }
            if (!playing.visible || playing.removed) continue
            val pivot = state.worldToScreen(playing.pivot, screenCenter)
            withTransform({
                translate(((playing.x + playing.swingX) * state.scale).toFloat(), ((playing.y + playing.swingY) * state.scale).toFloat())
                rotate(playing.angle.toFloat(), pivot)
                scale(playing.scale.toFloat(), playing.scale.toFloat(), pivot)
            }) { drawOne(stroke, culled = false) }
        }
    }

    // A layer as a whole: opacity applies to the layer once (drawn offscreen, then composited, so
    // overlapping strokes on one layer don't show through each other), and its mask (roadmap item
    // 91) hides what it covers.
    fun DrawScope.drawLayer(index: Int, layer: Layer) {
        val mask = if (layer.masked) state.rasters[maskIdOf(layer.id)] else null
        val offscreen = layer.opacity < 1f || mask != null
        if (offscreen) drawContext.canvas.saveLayer(layerBounds, Paint().apply { alpha = layer.opacity })
        drawLayerContent(index, layer)
        if (mask != null) drawRaster(mask, state, screenCenter, topLeftWorld, bottomRightWorld, BlendMode.DstOut)
        if (offscreen) drawContext.canvas.restore()
    }

    // Bottom to top. A layer followed by clipped layers (clipping masks, roadmap item 91) is drawn
    // with them in one group, each clipped layer only where the group already has paint.
    val layerList = state.layers.toList()
    // A layer and the layers clipped to it, composited as one by the base's blend mode.
    fun DrawScope.drawGroup(at: Int, next: Int, withBlend: Boolean) {
        val base = layerList[at]
        val blend = base.blend.gpu.takeIf { withBlend && base.blend != LayerBlend.NORMAL }
        if (blend != null) drawContext.canvas.saveLayer(layerBounds, Paint().apply { blendMode = blend })
        if (next == at + 1) {
            drawLayer(at, base)
        } else {
            drawContext.canvas.saveLayer(layerBounds, Paint())
            drawLayer(at, base)
            for (k in at + 1 until next) {
                if (!layerList[k].visible) continue
                drawContext.canvas.saveLayer(layerBounds, Paint().apply { blendMode = BlendMode.SrcAtop })
                drawLayer(k, layerList[k])
                drawContext.canvas.restore()
            }
            drawContext.canvas.restore()
        }
        if (blend != null) drawContext.canvas.restore()
    }
    var at = 0
    while (at < layerList.size) {
        val base = layerList[at]
        var next = at + 1
        while (next < layerList.size && layerList[next].clipped) next++
        val adjustment = base.adjustment
        when {
            // A hidden base hides the layers clipped to it, which have nothing to show through.
            !base.visible -> {}
            adjustment != null -> if (cpuTarget != null) adjustPixels(cpuTarget, adjustment, base.opacity, state.scale)
            cpuTarget != null && base.blend.gpu == null -> {
                // Drawn on its own, then blended into what's beneath pixel by pixel.
                val group = ImageBitmap(cpuTarget.width, cpuTarget.height)
                CanvasDrawScope().draw(this, layoutDirection, androidx.compose.ui.graphics.Canvas(group), Size(cpuTarget.width.toFloat(), cpuTarget.height.toFloat())) {
                    withTransform({ translate(-cpuOrigin.x, -cpuOrigin.y) }) { drawGroup(at, next, withBlend = false) }
                }
                blendPixels(cpuTarget, group, base.blend)
            }
            else -> drawGroup(at, next, withBlend = true)
        }
        at = next
    }

    // Labels are measured first: connectors run to the boxes actually drawn (measured text plus a
    // sticky note's paper), and are drawn under the labels so arrows never cover a note's text.
    class MeasuredLabel(val item: TextItem, val position: Offset, val layout: TextLayoutResult, val pad: Float)
    val labels = state.textItems.mapNotNull { item ->
        val fontSizePx = (item.fontSizeWorld * state.scale)
        if (!fontSizePx.isFinite() || fontSizePx <= 0.0) return@mapNotNull null
        val fontSizeSp = fontSizePx.toFloat().toSp() // DrawScope is a Density
        // A boxed label lays out at exactly its box width (so wrapping and alignment are within the
        // box); capped because layout constraints can't hold an arbitrarily large zoomed-in width.
        val boxPx = item.boxWidthWorld?.let { (it * state.scale).coerceIn(1.0, MAX_TEXT_BOX_PX).toInt() }
        val onPath = item.path != null && item.path.size >= 2
        val layout = textMeasurer.measure(
            // Text on a path is one line along it.
            text = if (onPath) item.text.replace('\n', ' ') else item.text,
            style = TextStyle(
                color = item.color,
                fontSize = fontSizeSp,
                fontWeight = if (item.bold) FontWeight.Bold else FontWeight.Normal,
                fontStyle = if (item.italic) FontStyle.Italic else FontStyle.Normal,
                fontFamily = item.font.fontFamily,
                fontFeatureSettings = featureSettings(item.features),
                textAlign = item.alignment.textAlign,
                // A label linked to a pin or document is underlined, like a link.
                textDecoration = if (item.link != null) TextDecoration.Underline else null,
            ),
            constraints = if (boxPx != null && !onPath) Constraints.fixedWidth(boxPx) else Constraints(),
        )
        // A sticky note extends half a line around the text, like paper around handwriting.
        MeasuredLabel(item, state.worldToScreen(item.position, screenCenter), layout, if (item.sticky) layout.size.height / 2f else 0f)
    }
    val labelBoxes = labels.associate { label ->
        val topLeft = label.position - Offset(label.pad, label.pad)
        val bottomRight = label.position + Offset(label.layout.size.width + label.pad, label.layout.size.height + label.pad)
        label.item.id to (WorldPoint(topLeft.x.toDouble(), topLeft.y.toDouble()) to WorldPoint(bottomRight.x.toDouble(), bottomRight.y.toDouble()))
    }
    for (connector in state.connectors) {
        // Screen-space boxes, so the same edge-to-edge geometry applies.
        val from = labelBoxes[connector.fromId] ?: continue
        val to = labelBoxes[connector.toId] ?: continue
        val (a, b) = connectorSegment(from.first, from.second, to.first, to.second) ?: continue
        drawConnector(Offset(a.x.toFloat(), a.y.toFloat()), Offset(b.x.toFloat(), b.y.toFloat()))
    }

    for (label in labels) {
        if (label.item.sticky) {
            drawRect(
                color = STICKY_NOTE_COLOR,
                topLeft = label.position - Offset(label.pad, label.pad),
                size = Size(label.layout.size.width + 2 * label.pad, label.layout.size.height + 2 * label.pad),
            )
        }
        // Outlined text (roadmap item 98): letter outlines a twentieth of the text size wide.
        val textStyle = if (label.item.outlined) DrawStyle(width = maxOf(1f, label.layout.layoutInput.style.fontSize.toPx() / 20f)) else Fill
        val path = label.item.path
        if (path != null && path.size >= 2) {
            drawTextOnPath(label.layout, path.map { state.worldToScreen(label.item.position + it, screenCenter) }, textStyle)
        } else {
            drawText(textLayoutResult = label.layout, topLeft = label.position, drawStyle = textStyle)
        }
    }
}

/**
 * Draws one line of laid-out text along [path] (screen points, roadmap item 98): each letter
 * centred at its distance along the path, sitting on it by its baseline and turned to the
 * path's direction there. Letters past the path's end aren't drawn. Each letter is the whole
 * line drawn moved and clipped to that letter, so shaping (kerning, ligatures) is kept.
 */
private fun DrawScope.drawTextOnPath(layout: TextLayoutResult, path: List<Offset>, style: androidx.compose.ui.graphics.drawscope.DrawStyle) {
    val along = FloatArray(path.size)
    for (i in 1 until path.size) along[i] = along[i - 1] + (path[i] - path[i - 1]).getDistance()
    val baseline = layout.firstBaseline
    var segment = 1
    for (i in 0 until layout.layoutInput.text.length) {
        val box = layout.getBoundingBox(i)
        if (box.width <= 0f) continue // spaces and zero-width marks have nothing to draw
        val middle = box.left + box.width / 2
        if (middle > along.last()) break
        while (segment < path.size - 1 && along[segment] < middle) segment++
        val a = path[segment - 1]
        val b = path[segment]
        val span = (along[segment] - along[segment - 1]).coerceAtLeast(1e-3f)
        val t = ((middle - along[segment - 1]) / span).coerceIn(0f, 1f)
        val at = a + (b - a) * t
        val degrees = Math.toDegrees(atan2((b - a).y.toDouble(), (b - a).x.toDouble())).toFloat()
        withTransform({
            translate(at.x, at.y)
            rotate(degrees, pivot = Offset.Zero)
        }) {
            clipRect(box.left - middle - 1f, box.top - baseline - 1f, box.right - middle + 1f, box.bottom - baseline + 1f) {
                drawText(textLayoutResult = layout, topLeft = Offset(-middle, -baseline), drawStyle = style)
            }
        }
    }
}

// Weight-painting overlay colours: automatic, following the parent (0), following the bone (1).
private val WEIGHT_AUTO_COLOR: Color get() = FernColors.current.neutral
private val WEIGHT_PARENT_COLOR: Color get() = FernColors.current.selection
private val WEIGHT_BONE_COLOR: Color get() = FernColors.current.danger

/** The spoken name of [tool] (design roadmap D4). */
internal fun toolName(tool: Tool): Int = when (tool) {
    Tool.PEN -> R.string.tool_name_pen
    Tool.ERASER -> R.string.tool_name_eraser
    Tool.SELECT -> R.string.tool_name_select
    Tool.SHAPE -> R.string.tool_name_shape
    Tool.FILL -> R.string.tool_name_fill
    Tool.TEXT -> R.string.tool_name_text
    Tool.EYEDROPPER -> R.string.tool_name_eyedropper
    Tool.CONNECT -> R.string.tool_name_connect
    Tool.BONE -> R.string.tool_name_bone
    Tool.BEZIER -> R.string.tool_name_bezier
    Tool.MEASURE -> R.string.tool_name_measure
    Tool.RIG -> R.string.tool_name_rig
}

/**
 * Reads the pixels shown in the window around [center] (window coordinates), a [size] px square,
 * and hands their average colour to [onColor] on the main thread; null when they can't be read
 * (off the window, no activity, or the copy failed). Uses [PixelCopy], so it sees exactly what
 * the user sees: every layer, raster paint, images, fills and the paper.
 */
private fun sampleWindow(view: android.view.View, center: Offset, size: Int, onColor: (Color?) -> Unit) {
    var context = view.context
    while (context !is android.app.Activity && context is android.content.ContextWrapper) context = context.baseContext
    val window = (context as? android.app.Activity)?.window ?: return onColor(null)
    val half = size / 2
    val left = center.x.roundToInt() - half
    val top = center.y.roundToInt() - half
    if (left < 0 || top < 0 || left + size > view.width || top + size > view.height) return onColor(null)
    val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
    try {
        android.view.PixelCopy.request(
            window,
            android.graphics.Rect(left, top, left + size, top + size),
            bitmap,
            { result ->
                onColor(if (result == android.view.PixelCopy.SUCCESS) averageColor(bitmap) else null)
                bitmap.recycle()
            },
            android.os.Handler(android.os.Looper.getMainLooper()),
        )
    } catch (_: IllegalArgumentException) {
        bitmap.recycle()
        onColor(null)
    }
}

/** The mean of every pixel of [bitmap], opaque. */
private fun averageColor(bitmap: android.graphics.Bitmap): Color {
    val pixels = IntArray(bitmap.width * bitmap.height)
    bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    var r = 0L
    var g = 0L
    var b = 0L
    for (p in pixels) {
        r += (p shr 16) and 0xFF
        g += (p shr 8) and 0xFF
        b += p and 0xFF
    }
    val n = pixels.size
    return Color((r / n).toInt(), (g / n).toInt(), (b / n).toInt())
}

/**
 * The guides (roadmap items 340-342), in view space: straight edges as lines across the view,
 * parallel guides as their direction line, radial and concentric guides as their centre with a
 * few lines or rings, ellipses as the curve. Handles show only in guide edit mode.
 */
private fun DrawScope.drawGuides(state: CanvasState, screenCenter: Offset, editing: Boolean) {
    val color = DRAWING_GUIDE_COLOR
    fun screen(p: WorldPoint) = state.worldToScreen(p, screenCenter)
    val reach = size.width + size.height
    for (guide in state.drawingGuides) {
        if (!guide.visible) continue
        val h = guide.handles.map(::screen)
        when (guide.kind) {
            GuideKind.STRAIGHT, GuideKind.PARALLEL -> {
                val d = h[1] - h[0]
                val len = d.getDistance()
                if (len > 0f) {
                    val u = d / len
                    drawLine(color, h[0] - u * reach, h[0] + u * reach, strokeWidth = GUIDE_LINE_PX)
                    if (guide.kind == GuideKind.PARALLEL) {
                        val n = Offset(-u.y, u.x) * GUIDE_PARALLEL_GAP_PX
                        for (k in listOf(-2, -1, 1, 2)) drawLine(color.copy(alpha = GUIDE_FAINT_ALPHA), h[0] + n * k.toFloat() - u * reach, h[0] + n * k.toFloat() + u * reach, strokeWidth = GUIDE_FAINT_PX)
                    }
                }
            }
            GuideKind.RADIAL -> for (k in 0 until GUIDE_RADIAL_LINES) {
                val a = k * 2 * Math.PI / GUIDE_RADIAL_LINES
                drawLine(color.copy(alpha = GUIDE_FAINT_ALPHA), h[0], h[0] + Offset(cos(a).toFloat(), sin(a).toFloat()) * reach, strokeWidth = GUIDE_FAINT_PX)
            }
            GuideKind.CONCENTRIC -> for (k in 1..GUIDE_RINGS) {
                drawCircle(color.copy(alpha = GUIDE_FAINT_ALPHA), radius = k * GUIDE_RING_GAP_PX, center = h[0], style = androidx.compose.ui.graphics.drawscope.Stroke(GUIDE_FAINT_PX))
            }
            GuideKind.GRID -> {
                val cw = kotlin.math.abs(h[1].x - h[0].x)
                val ch = kotlin.math.abs(h[1].y - h[0].y)
                // Only while its cells are big enough on screen to read; denser grids would be noise.
                if (cw >= GUIDE_GRID_MIN_CELL_PX && ch >= GUIDE_GRID_MIN_CELL_PX) {
                    val faint = color.copy(alpha = GUIDE_FAINT_ALPHA)
                    var x = h[0].x - kotlin.math.floor(h[0].x / cw) * cw
                    while (x <= size.width) { drawLine(faint, Offset(x, 0f), Offset(x, size.height), strokeWidth = GUIDE_FAINT_PX); x += cw }
                    var y = h[0].y - kotlin.math.floor(h[0].y / ch) * ch
                    while (y <= size.height) { drawLine(faint, Offset(0f, y), Offset(size.width, y), strokeWidth = GUIDE_FAINT_PX); y += ch }
                }
            }
            GuideKind.CURVE -> {
                val path = androidx.compose.ui.graphics.Path()
                curvePoints(guide.handles).map(::screen).forEachIndexed { k, p -> if (k == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y) }
                drawPath(path, color, style = androidx.compose.ui.graphics.drawscope.Stroke(GUIDE_LINE_PX))
                // The control arms, so the handles read as a Bézier's.
                if (editing) {
                    drawLine(color.copy(alpha = GUIDE_FAINT_ALPHA), h[0], h[1], strokeWidth = GUIDE_FAINT_PX)
                    drawLine(color.copy(alpha = GUIDE_FAINT_ALPHA), h[3], h[2], strokeWidth = GUIDE_FAINT_PX)
                }
            }
            GuideKind.ELLIPSE -> {
                val e = ellipseOf(guide.handles)
                val path = androidx.compose.ui.graphics.Path()
                for (k in 0..GUIDE_ELLIPSE_SEGMENTS) {
                    val p = screen(e.at(k * 2 * Math.PI / GUIDE_ELLIPSE_SEGMENTS))
                    if (k == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
                }
                drawPath(path, color, style = androidx.compose.ui.graphics.drawscope.Stroke(GUIDE_LINE_PX))
            }
        }
        if (editing) for (p in h) {
            drawCircle(color, radius = GUIDE_HANDLE_PX, center = p)
            drawCircle(Color.White, radius = GUIDE_HANDLE_PX / 2, center = p)
        }
    }
}

// Guide drawing sizes, screen px on the §7 scale.
private const val GUIDE_LINE_PX = 2f
private const val GUIDE_FAINT_PX = 1f
private const val GUIDE_FAINT_ALPHA = 0.4f
private const val GUIDE_HANDLE_PX = 12f
private const val GUIDE_PARALLEL_GAP_PX = 64f
private const val GUIDE_RING_GAP_PX = 64f
private const val GUIDE_RINGS = 8
private const val GUIDE_RADIAL_LINES = 24
private const val GUIDE_ELLIPSE_SEGMENTS = 96
private const val GUIDE_GRID_MIN_CELL_PX = 8f

/**
 * The rig's bones (roadmap item 250) while the rig tool is active, posed as shown: each a long
 * thin diamond from its origin to its tip, Spine's bone shape, the selected one in the accent
 * colour, with the bone being drawn as a line.
 */
private fun DrawScope.drawRig(state: CanvasState, screenCenter: Offset) {
    val editor = state.rigEditor
    val pose = editor.pose()
    for (bone in editor.rig.bones) {
        val world = pose[bone.id] ?: continue
        val a = state.worldToScreen(boneOrigin(world), screenCenter)
        val b = state.worldToScreen(boneTip(bone, world), screenCenter)
        val d = b - a
        val len = d.getDistance()
        if (len < 1f) continue
        val n = Offset(-d.y, d.x) / len * (len * RIG_BONE_WIDTH_RATIO).coerceAtMost(RIG_BONE_MAX_HALF_WIDTH_PX)
        val shoulder = a + d * RIG_BONE_SHOULDER
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(a.x, a.y)
            lineTo((shoulder + n).x, (shoulder + n).y)
            lineTo(b.x, b.y)
            lineTo((shoulder - n).x, (shoulder - n).y)
            close()
        }
        val color = if (bone.id == editor.selectedBoneId) DRAWING_GUIDE_COLOR else FernColors.current.neutral
        drawPath(path, color.copy(alpha = RIG_BONE_FILL_ALPHA))
        drawPath(path, color, style = androidx.compose.ui.graphics.drawscope.Stroke(GUIDE_LINE_PX))
        drawCircle(color, radius = RIG_JOINT_PX, center = a)
    }
    // While painting weights (roadmap item 285): each attached point, as solid as the selected bone's share of it.
    val selected = editor.selectedBoneId
    if (editor.setupMode && editor.setupAction == RigEditor.SetupAction.PAINT_WEIGHTS && selected != null) {
        for (attachment in editor.rig.attachments) {
            attachment.strokes.forEachIndexed { si, stroke ->
                val weights = attachment.weights.getOrNull(si) ?: return@forEachIndexed
                stroke.points.forEachIndexed { pi, p ->
                    val share = weights.getOrNull(pi)?.firstOrNull { it.first == selected }?.second ?: 0.0
                    drawCircle(DRAWING_GUIDE_COLOR.copy(alpha = (RIG_WEIGHT_MIN_ALPHA + share * (1 - RIG_WEIGHT_MIN_ALPHA)).toFloat()), radius = RIG_JOINT_PX, center = state.worldToScreen(p, screenCenter))
                }
            }
        }
    }
    editor.draft?.let { (head, tail) ->
        drawLine(DRAWING_GUIDE_COLOR, state.worldToScreen(head, screenCenter), state.worldToScreen(tail, screenCenter), strokeWidth = GUIDE_LINE_PX * 2)
    }
}

// Rig bone shape, screen px on the §7 scale: widest a tenth of the way along, at most 8 px each side.
private const val RIG_BONE_WIDTH_RATIO = 0.08f
private const val RIG_BONE_MAX_HALF_WIDTH_PX = 8f
private const val RIG_BONE_SHOULDER = 0.1f
private const val RIG_BONE_FILL_ALPHA = 0.3f
private const val RIG_JOINT_PX = 4f
private const val RIG_WEIGHT_MIN_ALPHA = 0.12
