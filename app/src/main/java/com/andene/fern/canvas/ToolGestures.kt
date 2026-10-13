package com.andene.fern.canvas

import com.andene.fern.FernSounds
import com.andene.fern.PaperSound

/*
 * Each tool's handling of a one-pointer gesture on the canvas (roadmap item 167): what a press
 * begins, what a move continues, what a release or a cancel ends. The canvas keeps the gesture
 * plumbing - pointers, palm rejection, long press, navigation - and hands the one-pointer part to
 * the gesture [startToolGesture] returns, instead of switching on the tool at press, move and
 * release. A new tool adds one entry here; the canvas doesn't change.
 */

/** One point of a gesture: where it is in the world, the stylus reading there, and how fast the pointer moves on screen. */
internal class GesturePoint(
    val world: WorldPoint,
    val pressure: Float = 1f,
    val tilt: Float = 0f,
    val orientation: Float = 0f,
    /** Screen pixels per millisecond since the last point; 0 at the press. */
    val screenSpeed: Float = 0f,
)

/** A tool's gesture in progress, begun by [startToolGesture]. */
internal interface ToolGesture {
    /** The pointer moved. */
    fun move(point: GesturePoint) {}

    /** The pointer lifted, or the gesture ended another way (a long press, the stylus lifting). */
    fun release() {}

    /** A second finger landed, turning the gesture into navigation. Ends it like a release unless the tool undoes it. */
    fun cancel() = release()
}

/**
 * Begins [tool]'s gesture at [point] and returns it. A press on a canvas handle (the selection's
 * scale and rotate handles among them) never gets here: the canvas gives it to the handle
 * ([CanvasState.canvasHandles], roadmap item 171).
 */
internal fun startToolGesture(state: CanvasState, tool: Tool, point: GesturePoint): ToolGesture {
    val at = point.world
    return when (tool) {
        Tool.PEN -> {
            state.beginStroke(at, point.pressure, point.tilt, point.orientation)
            PenGesture(state)
        }
        Tool.ERASER -> {
            state.eraserTool.begin(at)
            gesture(move = { state.eraserTool.move(it.world) }, release = state.eraserTool::end)
        }
        Tool.SHAPE -> {
            state.shapeTool.begin(at)
            gesture(move = { state.shapeTool.move(it.world) }, release = state.shapeTool::end)
        }
        // One-shot on press: nothing to continue or end.
        Tool.FILL -> {
            state.fillAt(at)
            NO_GESTURE
        }
        Tool.EYEDROPPER -> {
            state.pickColorAt(at)
            NO_GESTURE
        }
        Tool.TEXT -> {
            state.textTool.begin(at)
            // A second finger means a pinch, not a tap to type: no text box for it.
            gesture(move = { state.textTool.move(it.world) }, release = state.textTool::end, cancel = state.textTool::cancel)
        }
        Tool.CONNECT -> {
            state.connectTool.begin(at)
            gesture(move = { state.connectTool.move(it.world) }, release = state.connectTool::end)
        }
        Tool.BONE -> {
            state.boneTool.begin(at)
            gesture(move = { state.boneTool.move(it.world) }, release = state.boneTool::end)
        }
        // A node is placed on press; dragging pulls its handles out.
        Tool.BEZIER -> {
            state.bezierTool.press(at)
            gesture(move = { state.bezierTool.dragHandle(it.world) }, release = {})
        }
        Tool.MEASURE -> {
            state.measureTool.begin(at)
            gesture(move = { state.measureTool.move(it.world) }, release = state.measureTool::end)
        }
        Tool.RIG -> {
            state.rigEditor.begin(at)
            gesture(move = { state.rigEditor.move(it.world) }, release = { state.rigEditor.end() })
        }
        Tool.SELECT -> {
            state.beginSelectGesture(at)
            gesture(move = { state.continueSelectGesture(it.world) }, release = state::endSelectGesture)
        }
    }
}

/** The pen: a stroke, and the paper's sound following the pen's speed (design roadmap D59). */
private class PenGesture(private val state: CanvasState) : ToolGesture {
    override fun move(point: GesturePoint) {
        state.extendStroke(point.world, point.pressure, point.tilt, point.orientation)
        state.activePenType.paperSound()?.let { paper -> FernSounds.stroke(paper, point.screenSpeed) }
    }

    override fun release() {
        state.endStroke()
        FernSounds.strokeEnd()
    }
}

private fun gesture(move: (GesturePoint) -> Unit, release: () -> Unit, cancel: (() -> Unit)? = null): ToolGesture = LambdaGesture(move, release, cancel ?: release)

private class LambdaGesture(private val onMove: (GesturePoint) -> Unit, private val onRelease: () -> Unit, private val onCancel: () -> Unit) : ToolGesture {
    override fun move(point: GesturePoint) = onMove(point)
    override fun release() = onRelease()
    override fun cancel() = onCancel()
}

private val NO_GESTURE = object : ToolGesture {}

/** The paper sound a pen makes (design roadmap D59): graphite-like pens sound like a pencil, inks like a felt tip; digital ones are silent. */
internal fun PenType.paperSound(): PaperSound? = when (this) {
    PenType.PENCIL, PenType.CRAYON -> PaperSound.PENCIL
    PenType.MARKER, PenType.HIGHLIGHTER, PenType.CALLIGRAPHY -> PaperSound.MARKER
    PenType.AIRBRUSH, PenType.PIXEL, PenType.STAMP -> null
}
