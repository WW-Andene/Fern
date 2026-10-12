package com.andene.fern.canvas

import androidx.compose.runtime.mutableStateMapOf
import com.andene.fern.R
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import java.util.UUID

/** How [CanvasState.alignSelection] lines selected strokes up, by their bounding boxes. */
enum class StrokeAlignment(@StringRes val label: Int) {
    LEFT(R.string.align_left),
    CENTER_X(R.string.align_center_x),
    RIGHT(R.string.align_right),
    TOP(R.string.align_top),
    CENTER_Y(R.string.align_center_y),
    BOTTOM(R.string.align_bottom),
    DISTRIBUTE_X(R.string.align_distribute_x),
    DISTRIBUTE_Y(R.string.align_distribute_y),
}

enum class Tool { PEN, ERASER, SELECT, SHAPE, FILL, TEXT, RULER, EYEDROPPER, CONNECT, BONE, BEZIER }

/**
 * Camera + document model for the infinite canvas.
 *
 * ## Why this actually gets you unbounded pan/zoom
 *
 * Compose's [Offset] is Float, which only has ~7 significant decimal digits — nowhere near
 * enough for "infinite". This state instead keeps [panWorld] and [scale] as Double
 * (~15-17 significant digits), and — critically — uses a **floating origin**: whenever the
 * camera has drifted far from world-space (0,0), [rebaseIfNeeded] re-anchors the origin to
 * the camera by subtracting [panWorld] (rounded to whole paper-pattern cells, so the pattern
 * stays aligned with the content) from every stored point, leaving [panWorld] within one cell
 * of zero.
 *
 * That matters because Double precision is *relative to magnitude*: a Double near
 * 10,000,000 has much coarser precision than one near 0. Without rebasing, panning far
 * enough would eventually make `world - panWorld` catastrophically lose precision, same
 * as with Float, just later. With rebasing, all stored coordinates and the pan offset are
 * always kept close to zero, so that subtraction never loses precision no matter how far
 * you've panned — pan range is unbounded for any realistic drawing session.
 *
 * Zoom depth is carried entirely by [scale], which never needs rebasing: Double covers
 * roughly 300 orders of magnitude (~4.9e-324 to ~1.8e308) with full relative precision at
 * every level, since it's a pure multiplier applied after the (already-small,
 * already-precise) rebased coordinates. [MIN_SCALE]/[MAX_SCALE] leave enormous headroom
 * below that ceiling purely to avoid float overflow/underflow at the very last
 * Double->Float conversion when actually drawing to screen — for any human drawing task
 * this is indistinguishable from literally infinite zoom.
 *
 * @param onChanged invoked with a snapshot of [strokes] after every completed mutation
 *   (a finished stroke, an undo, a clear) — not on every point mid-stroke. Intended for
 *   driving autosave; the caller decides how/when to actually persist the snapshot.
 * @param onTextChanged invoked with a snapshot of [textItems] whenever a label is added,
 *   edited, or removed - the text-item equivalent of [onChanged], since text edits are their
 *   own independent unit of persisted state.
 * @param onImageChanged the [imageItems] equivalent of [onTextChanged].
 * @param onBackgroundChanged invoked with the new (color, style) whenever [setBackground] is
 *   called - the background's equivalent of [onChanged], since it's also per-document
 *   persisted state.
 */
class CanvasState(
    private val onChanged: (List<Stroke>) -> Unit = {},
    private val onTextChanged: (List<TextItem>) -> Unit = {},
    private val onImageChanged: (List<ImageItem>) -> Unit = {},
    private val onBackgroundChanged: (Color, BackgroundStyle) -> Unit = { _, _ -> },
    /** World-space content stored outside this class (pins) must shift by -offset too; see [rebaseIfNeeded]. */
    private val onRebased: (offset: WorldPoint) -> Unit = {},
    private val onLayersChanged: (List<Layer>) -> Unit = {},
    private val onFramesChanged: (List<Frame>) -> Unit = {},
    private val onSavedColorsChanged: (List<Color>) -> Unit = {},
    private val onBrushChanged: (StampBrush) -> Unit = {},
    private val onToolSlotsChanged: (List<ToolPreset?>) -> Unit = {},
    private val onBrushPresetsChanged: (List<BrushPreset>) -> Unit = {},
    private val onConnectorsChanged: (List<Connector>) -> Unit = {},
    private val onBonesChanged: (List<Bone>) -> Unit = {},
    /** Playback speed and frame holds changed (see [AnimationSettings]). */
    private val onAnimationSettingsChanged: (AnimationSettings) -> Unit = {},
    /**
     * False for an off-screen camera that renders the document (the tile cache's): it never
     * needs the floating origin, and rebasing would shift and re-index its whole copy of the
     * document on every jump to a distant tile.
     */
    private val floatingOrigin: Boolean = true,
) {
    companion object {
        // Effectively unbounded: leaves ~250 orders of magnitude of headroom on both sides
        // of Double's range purely as a safety margin, not a meaningful practical limit.
        const val MIN_SCALE = 1e-250
        const val MAX_SCALE = 1e250

        // Passes per Smooth tap: visibly smoother without collapsing small details in one tap.
        private const val SMOOTH_PASSES = 3

        // A full row of small swatches on a phone; 16 is on the §7 scale.
        private const val MAX_SAVED_COLORS = 16

        // 8 swatches fit beside the fixed palette without crowding the toolbar; on the §7 scale.
        private const val MAX_RECENT_COLORS = 8

        // Finger-sized hit slop so thin strokes can still be picked; on the §7 scale.
        private const val EYEDROPPER_TOLERANCE_SCREEN = 12.0

        // Holding the pen still this long before lifting turns a rough stroke into a clean shape.
        private const val SHAPE_HOLD_MILLIS = 500L

        // One second of motion at the default 12 fps.
        private const val MOTION_PATH_STEPS = 12
        private const val MOTION_PATH_SAMPLES = 256

        // Comic panel borders, in screen px at the zoom they're inserted at; on the §7 scale.
        private const val PANEL_BORDER_SCREEN = 3.0
        // Bone tool reach and the shortest bone an add drag makes, in screen px (§7 scale).
        private const val BONE_HIT_SCREEN = 24.0
        private const val BONE_MIN_SCREEN = 16.0
        // Smart guides: snap distance (screen px) and how many nearby objects offer guide lines.
        private const val GUIDE_SNAP_SCREEN = 8.0
        private const val MAX_GUIDE_OBJECTS = 2000
        // Bézier pen: reach of the first node to close a path, and curve sampling (screen px).
        private const val BEZIER_CLOSE_SCREEN = 24.0
        private const val BEZIER_SAMPLE_SCREEN = 4.0
        // IK: chain length, solver passes per move, and close enough (screen px).
        private const val IK_CHAIN_BONES = 4
        /** Longest hold the X-sheet offers, in playback ticks. */
        const val MAX_FRAME_HOLD = 24
        private const val IK_ITERATIONS = 8
        private const val IK_TOLERANCE_SCREEN = 1.0
        // Template text sizes and spacing in screen px at the zoom the template is inserted at (§7 scale).
        private const val PLANNER_LABEL_SCREEN = 16.0
        private const val MIND_MAP_CENTRAL_SCREEN = 24.0
        private const val MIND_MAP_BRANCH_SCREEN = 16.0
        private const val MIND_MAP_CENTRAL_WIDTH_SCREEN = 192.0
        private const val MIND_MAP_BRANCH_WIDTH_SCREEN = 128.0
        private const val MIND_MAP_SPREAD_X_SCREEN = 256.0
        private const val MIND_MAP_SPREAD_Y_SCREEN = 192.0

        // Movement under this many screen px still counts as a tap; on the §7 scale.
        private const val TAP_SLOP_SCREEN = 8.0

        // Touch radius of a vanishing point's handle, and how far a stroke must travel before its
        // perspective direction is chosen; both on the §7 scale.
        private const val VANISHING_POINT_HANDLE_SCREEN = 24.0

        // How close (screen px) a shape point must come to an object anchor to snap; on the §7 scale.
        private const val OBJECT_SNAP_RADIUS_SCREEN = 12.0

        // Touch radius of a node handle in node editing; on the §7 scale.
        private const val NODE_HANDLE_SCREEN = 16.0

        // How close (screen px) a press must be to a connector to remove it; on the §7 scale.
        private const val CONNECTOR_HIT_SCREEN = 12.0

        // Spacing between laid-out diagram boxes, in screen px; on the §7 scale.
        private const val DIAGRAM_GAP_SCREEN = 48.0
        private const val PERSPECTIVE_DECIDE_SCREEN = 16.0

        // Assumed view width before the first layout pass reports the real one.
        private const val DEFAULT_VIEW_WIDTH_SCREEN = 1024f

        // Velocity dynamics tuning: at 2 screen px/ms (a brisk stroke) the line is at its
        // thinnest; 30% blending per point keeps width changes smooth.
        private const val VELOCITY_THINNING = 0.3
        private const val MIN_VELOCITY_PRESSURE = 0.4
        private const val VELOCITY_SMOOTHING = 0.3f

        // Procreate/Concepts-style dead zone around upright.
        private const val ROTATION_SNAP_DEGREES = 8.0
        private const val HOLD_JITTER_SCREEN = 2.0

        // Above ~0.9 the stroke lags so far behind the pen that it stops feeling like drawing.
        const val MAX_STABILIZATION = 0.9

        // World-space distance the camera can drift from the current origin before we
        // re-anchor. Large enough that rebasing is rare (not a per-frame cost), small
        // enough that precision near the origin never meaningfully degrades.
        private const val REBASE_THRESHOLD = 65536.0

        // Snapshot undo keeps a full copy of the strokes per step, so the depth is capped to
        // bound memory on big documents. 64 is on the project size scale (CLAUDE.md §7).
        private const val MAX_UNDO_STEPS = 64

        // Eraser radius in screen pixels (on the §7 scale - Secondary tier), converted to
        // world units by the same scale-dependent logic as pen width, so the eraser covers
        // a consistent on-screen area regardless of zoom level.
        private const val ERASER_RADIUS_SCREEN = 24.0

        // How close (in screen pixels) a stroke's start/end points must be to count as a
        // "closed" shape for the FILL tool - same scale-dependent conversion as the eraser.
        private const val FILL_CLOSE_TOLERANCE_SCREEN = 24.0
        // Gaps in line art up to about twice this (screen px) are closed by the fill.
        private const val FILL_GAP_SCREEN = 4.0

        // How close (in screen pixels) a touch must land on the ruler's endpoint to grab it
        // for resizing, rather than translating the whole guide - same scale-dependent
        // conversion as the eraser/fill tolerances above.
        private const val RULER_HANDLE_RADIUS_SCREEN = 24.0

        // Default ruler length when first placed: half a screen-width on each side of the tap,
        // in screen pixels (Secondary tier on the §7 scale), converted to world units.
        private const val RULER_DEFAULT_HALF_LENGTH_SCREEN = 300.0
    }

    // World-space point currently at the center of the screen.
    var panWorld by mutableStateOf(WorldPoint.Zero)
        private set

    var scale by mutableDoubleStateOf(1.0)
        private set

    /**
     * The drawing surface's current on-screen size in pixels, kept up to date by
     * [DrawingCanvas] every frame. Lets [zoomToFit] and the minimap compute what the
     * viewport covers without every caller having to thread the Canvas size through
     * themselves.
     */
    var viewportSize by mutableStateOf(Size.Zero)

    val strokes = mutableStateListOf<Stroke>()

    var activeColor by mutableStateOf(Color(0xFF1B1B1B))
    var activeWidthWorld by mutableDoubleStateOf(4.0)
    private var activeToolState by mutableStateOf(Tool.PEN)

    var activeTool: Tool
        get() = activeToolState
        set(value) {
            // Leaving the Bézier pen keeps the path drawn so far, as an open path.
            if (activeToolState == Tool.BEZIER && value != Tool.BEZIER && bezierNodes.isNotEmpty()) finishBezierPath(closed = false)
            activeToolState = value
        }
    var activePenType by mutableStateOf(PenType.MARKER)

    private var toolBeforeEraserToggle = Tool.PEN

    /**
     * Swaps to the eraser and back to the previous tool - the action of a stylus button press
     * (Android's counterpart of Apple Pencil's double-tap/squeeze, roadmap item 27).
     */
    fun toggleEraser() {
        if (activeTool == Tool.ERASER) {
            activeTool = toolBeforeEraserToggle
        } else {
            toolBeforeEraserToggle = activeTool
            activeTool = Tool.ERASER
        }
    }

    /** Brush settings new [PenType.STAMP] strokes are drawn with; edited in the brush editor. */
    var activeBrush by mutableStateOf(StampBrush())
        private set

    /** Changes the stamp brush and reports it for persistence (it applies to every document). */
    fun setBrush(brush: StampBrush) {
        activeBrush = brush.clamped()
        onBrushChanged(activeBrush)
    }

    /** Restores the persisted stamp brush without reporting it back. */
    fun loadBrush(brush: StampBrush) {
        activeBrush = brush.clamped()
    }

    /** Stamp brushes saved by name in the brush editor; shared by every document. */
    val brushPresets = mutableStateListOf<BrushPreset>()

    /** Saves [brush] as [name], replacing a preset already called that (names are trimmed; blank is ignored). */
    fun saveBrushPreset(name: String, brush: StampBrush) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        val preset = BrushPreset(trimmed, brush.clamped())
        val existing = brushPresets.indexOfFirst { it.name == trimmed }
        if (existing == -1) brushPresets.add(preset) else brushPresets[existing] = preset
        onBrushPresetsChanged(brushPresets.toList())
    }

    fun deleteBrushPreset(name: String) {
        if (brushPresets.removeWhere { it.name == name }) onBrushPresetsChanged(brushPresets.toList())
    }

    /** Restores persisted presets without reporting them back. */
    fun loadBrushPresets(loaded: List<BrushPreset>) {
        brushPresets.clear()
        brushPresets.addAll(loaded)
    }

    /** Favourite pen setups, one per toolbar slot (null = empty); shared by every document. */
    val toolSlots = mutableStateListOf<ToolPreset?>().apply { repeat(TOOL_SLOT_COUNT) { add(null) } }

    /** Stores the current pen setup in slot [index]. */
    fun saveToolSlot(index: Int) {
        toolSlots[index] = ToolPreset(activePenType, activeColor, activeWidthWorld, activeOpacity, activeBlendMode, activeDashed, activeBrush)
        onToolSlotsChanged(toolSlots.toList())
    }

    /** Switches to the pen setup in slot [index] (and to the pen tool); no-op for an empty slot. */
    fun applyToolSlot(index: Int) {
        val preset = toolSlots[index] ?: return
        activePenType = preset.penType
        activeColor = preset.color
        activeWidthWorld = preset.width
        activeOpacity = preset.opacity
        activeBlendMode = preset.blendMode
        activeDashed = preset.dashed
        activeBrush = preset.brush.clamped()
        activeTool = Tool.PEN
    }

    fun clearToolSlot(index: Int) {
        toolSlots[index] = null
        onToolSlotsChanged(toolSlots.toList())
    }

    /** Restores persisted slots without reporting them back; extra entries are ignored, missing ones stay empty. */
    fun loadToolSlots(loaded: List<ToolPreset?>) {
        for (i in 0 until TOOL_SLOT_COUNT) toolSlots[i] = loaded.getOrNull(i)
    }
    var activeShapeKind by mutableStateOf(ShapeKind.LINE)
    var activeTextSizeWorld by mutableDoubleStateOf(24.0)

    /** Opacity (0..1) applied to new strokes' color at creation time - baked into the stored color's alpha, not a separate live-editable property, matching how width/pen type are also fixed once a stroke is drawn. */
    var activeOpacity by mutableDoubleStateOf(1.0)

    /**
     * Stroke stabilisation strength, 0 (off) to [MAX_STABILIZATION]: how strongly each new
     * point is pulled back toward the previous one, smoothing hand wobble (the same idea as
     * Procreate's StreamLine or Krita's stabiliser). Higher values feel like dragging the pen
     * on a string - smoother but lagging behind the finger.
     */
    var activeStabilization by mutableDoubleStateOf(0.0)

    /** Flipbook animation: number of frames (at least 1) and the one being shown and edited. */
    var animFrameCount by mutableIntStateOf(1)
        private set
    var currentAnimFrame by mutableIntStateOf(0)
        private set

    /** Shows the animation bar; with it off, documents behave exactly as before (one frame). */
    var animationMode by mutableStateOf(false)

    /** Previous frame shown faintly under the current one while editing, the animator's onion skin. */
    var onionSkin by mutableStateOf(true)

    var playing by mutableStateOf(false)

    private var animFpsState by mutableIntStateOf(12)

    /** Playback speed in ticks per second; 12 is the classic hand-drawn animation rate ("on twos"). Saved per document. */
    var animFps: Int
        get() = animFpsState
        set(value) {
            animFpsState = value
            onAnimationSettingsChanged(animationSettings())
        }

    /**
     * The exposure sheet (X-sheet, roadmap item 88): how many playback ticks each frame stays on
     * screen, for frames held longer than one; a frame not listed is shown for one tick.
     * Playback and the animated exports follow it. Shifts with frames as they're inserted or
     * deleted, and is part of undo.
     */
    val frameHolds = mutableStateMapOf<Int, Int>()

    fun holdOf(frame: Int): Int = frameHolds[frame] ?: 1

    /** Sets frame [frame]'s hold, from 1 to [MAX_FRAME_HOLD] ticks. */
    fun setHold(frame: Int, ticks: Int) {
        val hold = ticks.coerceIn(1, MAX_FRAME_HOLD)
        if (hold == holdOf(frame)) return
        recordHistory()
        if (hold == 1) frameHolds.remove(frame) else frameHolds[frame] = hold
        onAnimationSettingsChanged(animationSettings())
    }

    fun animationSettings() = AnimationSettings(animFps, frameHolds.toMap())

    /** Applies settings read from storage, without reporting them back. */
    fun loadAnimationSettings(settings: AnimationSettings) {
        animFpsState = settings.fps
        frameHolds.clear()
        frameHolds.putAll(settings.holds.filterValues { it > 1 })
    }

    private fun shiftHolds(from: Int, by: Int) {
        if (frameHolds.isEmpty()) return
        val moved = frameHolds.entries.associate { (frame, hold) -> (if (frame >= from) frame + by else frame) to hold }
        frameHolds.clear()
        frameHolds.putAll(moved)
        onAnimationSettingsChanged(animationSettings())
    }

    // Ticks the current frame has been on screen during playback.
    private var heldTicks = 0

    fun goToAnimFrame(frame: Int) {
        clearSelectionState()
        currentAnimFrame = frame.coerceIn(0, animFrameCount - 1)
    }

    /** Playback bounces back and forth (1-2-3-2-1...) instead of looping (1-2-3-1-2-3...). */
    var pingPong by mutableStateOf(false)

    /** Cap, join and width profile for new strokes (roadmap item 97); the default draws as before. */
    var activeLineStyle by mutableStateOf(LineStyle())

    /** Timing of generated in-betweens and motion-path steps. */
    var motionEasing by mutableStateOf(Easing.LINEAR)

    private var playingBackwards = false

    /** Next frame for playback: wraps to the first when looping, reverses at either end in ping-pong. */
    fun advanceAnimFrame() {
        if (animFrameCount <= 1) return
        if (++heldTicks < holdOf(currentAnimFrame)) return
        heldTicks = 0
        if (!pingPong) {
            playingBackwards = false
            currentAnimFrame = (currentAnimFrame + 1) % animFrameCount
            return
        }
        if (currentAnimFrame == animFrameCount - 1) playingBackwards = true
        if (currentAnimFrame == 0) playingBackwards = false
        currentAnimFrame += if (playingBackwards) -1 else 1
    }

    /**
     * Inserts a frame right after the current one and moves to it. With [copyCurrent], it
     * starts as a copy of the current frame (the usual way to make the next drawing);
     * otherwise blank. Later frames shift up by one.
     */
    fun insertAnimFrame(copyCurrent: Boolean) {
        recordHistory()
        clearSelectionState()
        val insertAt = currentAnimFrame + 1
        for (i in strokes.indices) {
            if (strokes[i].animFrame >= insertAt) strokes[i] = strokes[i].onAnimFrame(strokes[i].animFrame + 1)
        }
        if (copyCurrent) {
            strokes.addAll(strokes.filter { it.animFrame == currentAnimFrame }.map { it.onAnimFrame(insertAt) })
        }
        shiftBoneFrames(from = insertAt, by = 1)
        shiftHolds(from = insertAt, by = 1)
        if (copyCurrent) bones.addAll(bones.filter { it.animFrame == currentAnimFrame }.map { it.copy(animFrame = insertAt) })
        if (bones.isNotEmpty()) onBonesChanged(bones.toList())
        animFrameCount++
        currentAnimFrame = insertAt
        strokesChanged()
    }

    /**
     * Automatic in-betweens: inserts [count] frames between the current frame and the next,
     * morphing each stroke of the current frame into its counterpart in the next ([tweenPairs]:
     * nearest centres). Both strokes are resampled to the same number of points by arc
     * length and interpolated, along with width and colour, at the progress [motionEasing] gives
     * each in-between. Strokes without a partner
     * (the frames have different stroke counts) are left out of the in-betweens. Undoable.
     */
    fun generateInBetweens(count: Int) {
        val from = currentAnimFrame
        val to = from + 1
        if (count < 1 || to >= animFrameCount) return
        val start = strokes.filter { it.animFrame == from && !it.filled }
        val end = strokes.filter { it.animFrame == to && !it.filled }
        val pairs = tweenPairs(start, end)
        if (pairs.isEmpty()) return
        recordHistory()
        clearSelectionState()
        // Make room: everything from the next frame on moves up by [count].
        for (i in strokes.indices) {
            if (strokes[i].animFrame >= to) strokes[i] = strokes[i].onAnimFrame(strokes[i].animFrame + count)
        }
        // The skeleton's pose is tweened too, so in-betweens can be posed further.
        val poseFrom = bones.filter { it.animFrame == from }
        val poseTo = bones.filter { it.animFrame == to }
        shiftBoneFrames(from = to, by = count)
        shiftHolds(from = to, by = count)
        if (poseFrom.isNotEmpty() && poseTo.isNotEmpty()) {
            for (step in 1..count) bones.addAll(interpolatePose(poseFrom, poseTo, motionEasing.at(step.toDouble() / (count + 1)), from + step))
        }
        if (bones.isNotEmpty()) onBonesChanged(bones.toList())
        // Matched once per pair, shared by every in-between.
        val matched = pairs.map { (a, b) -> tweenSamples(a, b, maxOf(a.points.size, b.points.size, 2)) }
        for (step in 1..count) {
            val t = motionEasing.at(step.toDouble() / (count + 1))
            for ((index, pair) in pairs.withIndex()) {
                val (a, b) = pair
                val (pa, pb) = matched[index]
                val samples = pa.size
                val color = androidx.compose.ui.graphics.lerp(a.color, b.color, t.toFloat())
                val tween = Stroke(color, a.widthWorld + (b.widthWorld - a.widthWorld) * t, a.penType, blendMode = a.blendMode, layerId = a.layerId, animFrame = from + step, dashed = a.dashed, brush = a.brush, boneId = a.boneId.takeIf { it == b.boneId }, lineStyle = a.lineStyle)
                for (i in 0 until samples) tween.addPoint(WorldPoint(pa[i].x + (pb[i].x - pa[i].x) * t, pa[i].y + (pb[i].y - pa[i].y) * t))
                strokes.add(tween)
            }
        }
        animFrameCount += count
        strokesChanged()
    }

    /**
     * Motion path: with two or more strokes selected, the most recently drawn one is a path
     * and the rest move along it. Creates [steps] frames from the current one onward (adding
     * frames as needed); in each, a copy of the moving strokes is offset by how far along the
     * path that frame is, by arc length at the progress [motionEasing] gives it (evenly when
     * linear). The path itself is a guide and is removed.
     * Moving strokes already on later frames are left as they are. One undo step.
     */
    fun animateAlongPath(steps: Int = MOTION_PATH_STEPS) {
        if (selection.size < 2 || steps < 2) return
        val path = selection.maxBy { strokes.indexOf(it) }
        val movers = selection.filter { it !== path }
        if (path.points.size < 2) return
        recordHistory()
        // Densely resampled, so an eased fraction of the length lands between samples smoothly.
        val route = resamplePolyline(path.points, MOTION_PATH_SAMPLES)
        val start = route.first()
        strokes.remove(path)
        for (step in 1 until steps) {
            val position = motionEasing.at(step.toDouble() / (steps - 1)) * (route.size - 1)
            val i = position.toInt().coerceAtMost(route.size - 2)
            val f = position - i
            val at = WorldPoint(route[i].x + (route[i + 1].x - route[i].x) * f, route[i].y + (route[i + 1].y - route[i].y) * f)
            val offset = at - start
            val frame = currentAnimFrame + step
            for (mover in movers) {
                val copy = mover.onAnimFrame(frame)
                copy.shiftBy(WorldPoint(-offset.x, -offset.y)) // shiftBy subtracts
                strokes.add(copy)
            }
        }
        animFrameCount = maxOf(animFrameCount, currentAnimFrame + steps)
        animationMode = true
        clearSelectionState()
        strokesChanged()
    }

    /** Deletes the current frame and its strokes; later frames shift down. The last frame can't be deleted. */
    fun deleteAnimFrame() {
        if (animFrameCount <= 1) return
        recordHistory()
        clearSelectionState()
        val removed = currentAnimFrame
        strokes.removeWhere { it.animFrame == removed }
        for (i in strokes.indices) {
            if (strokes[i].animFrame > removed) strokes[i] = strokes[i].onAnimFrame(strokes[i].animFrame - 1)
        }
        if (bones.isNotEmpty()) {
            bones.removeWhere { it.animFrame == removed }
            shiftBoneFrames(from = removed + 1, by = -1)
            onBonesChanged(bones.toList())
        }
        if (frameHolds.remove(removed) != null) onAnimationSettingsChanged(animationSettings())
        shiftHolds(from = removed + 1, by = -1)
        animFrameCount--
        currentAnimFrame = currentAnimFrame.coerceAtMost(animFrameCount - 1)
        strokesChanged()
    }

    /**
     * Time-lapse replay: while non-null, only the first [replayCount] strokes (in drawing
     * order, which is creation order) are drawn, so the drawing rebuilds itself stroke by
     * stroke. Purely a view: nothing is changed or saved.
     */
    var replayCount by mutableStateOf<Int?>(null)

    /** Named regions of the canvas, in creation order. */
    val frames = mutableStateListOf<Frame>()

    /** Adds a frame covering exactly what's on screen now (unrotated view). */
    fun addFrameFromView(name: String) {
        if (viewportSize.width <= 0f || viewportSize.height <= 0f) return
        val center = Offset(viewportSize.width / 2f, viewportSize.height / 2f)
        val min = screenToWorld(Offset.Zero, center)
        val max = screenToWorld(Offset(viewportSize.width, viewportSize.height), center)
        frames.add(Frame(UUID.randomUUID().toString(), name, min, max))
        onFramesChanged(frames.toList())
    }

    /**
     * Comic page template: a 2:3 page centred in the view, ruled into five panels (two, one
     * wide, two) as ordinary rectangle strokes on the active layer, plus a frame named
     * "Page N" so it can be presented and exported as a page. Undoable for the panels.
     */
    fun insertComicPage() {
        if (!activeLayerEditable || viewportSize.width <= 0f) return
        recordHistory()
        val s = scale.coerceAtLeast(1e-300)
        // The page fills most of the screen's height at the current zoom.
        val pageHeight = viewportSize.height * 0.8 / s
        val pageWidth = pageHeight * 2.0 / 3.0
        val left = panWorld.x - pageWidth / 2.0
        val top = panWorld.y - pageHeight / 2.0
        val margin = pageWidth * 0.06
        val gutter = pageWidth * 0.03
        val innerWidth = pageWidth - 2 * margin
        val rowHeight = (pageHeight - 2 * margin - 2 * gutter) / 3.0
        val halfWidth = (innerWidth - gutter) / 2.0
        val panels = listOf(
            Triple(0, 0.0, halfWidth), Triple(0, halfWidth + gutter, halfWidth),
            Triple(1, 0.0, innerWidth),
            Triple(2, 0.0, halfWidth), Triple(2, halfWidth + gutter, halfWidth),
        )
        for ((row, x, width) in panels) {
            val panelLeft = left + margin + x
            val panelTop = top + margin + row * (rowHeight + gutter)
            val border = Stroke(Color.Black, PANEL_BORDER_SCREEN / s, PenType.MARKER, layerId = activeLayerId, animFrame = currentAnimFrame)
            for (point in generateShapePoints(ShapeKind.RECTANGLE, WorldPoint(panelLeft, panelTop), WorldPoint(panelLeft + width, panelTop + rowHeight))) {
                border.addPoint(point)
            }
            strokes.add(border)
        }
        frames.add(Frame(UUID.randomUUID().toString(), "Page ${frames.size + 1}", WorldPoint(left, top), WorldPoint(left + pageWidth, top + pageHeight)))
        onFramesChanged(frames.toList())
        strokesChanged()
    }

    /**
     * Storyboard template: six 16:9 shot panels in two columns centred in the view, as
     * rectangle strokes, with a "Storyboard" frame around them. Undoable for the panels.
     */
    fun insertStoryboard() {
        if (!activeLayerEditable || viewportSize.width <= 0f) return
        recordHistory()
        val s = scale.coerceAtLeast(1e-300)
        val sheetHeight = viewportSize.height * 0.8 / s
        val gap = sheetHeight * 0.04
        val panelHeight = (sheetHeight - 4 * gap) / 3.0
        val panelWidth = panelHeight * 16.0 / 9.0
        val sheetWidth = 2 * panelWidth + 3 * gap
        val left = panWorld.x - sheetWidth / 2.0
        val top = panWorld.y - sheetHeight / 2.0
        for (row in 0 until 3) for (column in 0 until 2) {
            val panelLeft = left + gap + column * (panelWidth + gap)
            val panelTop = top + gap + row * (panelHeight + gap)
            val border = Stroke(Color.Black, PANEL_BORDER_SCREEN / s, PenType.MARKER, layerId = activeLayerId, animFrame = currentAnimFrame)
            for (point in generateShapePoints(ShapeKind.RECTANGLE, WorldPoint(panelLeft, panelTop), WorldPoint(panelLeft + panelWidth, panelTop + panelHeight))) {
                border.addPoint(point)
            }
            strokes.add(border)
        }
        frames.add(Frame(UUID.randomUUID().toString(), "Storyboard", WorldPoint(left, top), WorldPoint(left + sheetWidth, top + sheetHeight)))
        onFramesChanged(frames.toList())
        strokesChanged()
    }

    /**
     * Weekly planner template (roadmap item 72): a 2:3 page of eight boxes in two columns, one per
     * day in [dayNames] order and the last for notes, each named by a bold label in its top-left
     * corner, with a frame named [title] around the page. Undoable. The names are passed in so
     * they come in the user's language and week order.
     */
    fun insertWeeklyPlanner(title: String, dayNames: List<String>, notesLabel: String) {
        if (!activeLayerEditable || viewportSize.width <= 0f) return
        recordHistory()
        val s = scale.coerceAtLeast(1e-300)
        val pageHeight = viewportSize.height * 0.8 / s
        val pageWidth = pageHeight * 2.0 / 3.0
        val left = panWorld.x - pageWidth / 2.0
        val top = panWorld.y - pageHeight / 2.0
        val margin = pageWidth * 0.06
        val gutter = pageWidth * 0.03
        val boxWidth = (pageWidth - 2 * margin - gutter) / 2.0
        val boxHeight = (pageHeight - 2 * margin - 3 * gutter) / 4.0
        val labelSize = PLANNER_LABEL_SCREEN / s
        for ((index, name) in (dayNames + notesLabel).take(8).withIndex()) {
            val boxLeft = left + margin + (index % 2) * (boxWidth + gutter)
            val boxTop = top + margin + (index / 2) * (boxHeight + gutter)
            val border = Stroke(Color.Black, PANEL_BORDER_SCREEN / s, PenType.MARKER, layerId = activeLayerId, animFrame = currentAnimFrame)
            for (point in generateShapePoints(ShapeKind.RECTANGLE, WorldPoint(boxLeft, boxTop), WorldPoint(boxLeft + boxWidth, boxTop + boxHeight))) {
                border.addPoint(point)
            }
            strokes.add(border)
            textItems.add(TextItem(UUID.randomUUID().toString(), name, WorldPoint(boxLeft + labelSize / 2, boxTop + labelSize / 2), labelSize, Color.Black, bold = true))
        }
        frames.add(Frame(UUID.randomUUID().toString(), title, WorldPoint(left, top), WorldPoint(left + pageWidth, top + pageHeight)))
        onFramesChanged(frames.toList())
        onTextChanged(textItems.toList())
        strokesChanged()
    }

    /**
     * Mind-map template (roadmap item 72): a central sticky note ([centralLabel]) in the middle of
     * the view, joined by connectors to four branch notes ([branchLabel]) above, right, below and
     * left of it. They're ordinary notes and connectors: edit, move, add more or "Tidy diagram".
     * Undoable.
     */
    fun insertMindMap(centralLabel: String, branchLabel: String) {
        if (viewportSize.width <= 0f) return
        recordHistory()
        val s = scale.coerceAtLeast(1e-300)
        fun note(text: String, center: WorldPoint, sizePx: Double, widthPx: Double): TextItem {
            val width = widthPx / s
            val size = sizePx / s
            return TextItem(
                UUID.randomUUID().toString(), text, WorldPoint(center.x - width / 2, center.y - size / 2), size, Color.Black,
                bold = sizePx > MIND_MAP_BRANCH_SCREEN, sticky = true, alignment = TextAlignment.CENTER, boxWidthWorld = width,
            )
        }
        val center = panWorld
        val central = note(centralLabel, center, MIND_MAP_CENTRAL_SCREEN, MIND_MAP_CENTRAL_WIDTH_SCREEN)
        val dx = MIND_MAP_SPREAD_X_SCREEN / s
        val dy = MIND_MAP_SPREAD_Y_SCREEN / s
        val branches = listOf(WorldPoint(center.x, center.y - dy), WorldPoint(center.x + dx, center.y), WorldPoint(center.x, center.y + dy), WorldPoint(center.x - dx, center.y))
            .map { note(branchLabel, it, MIND_MAP_BRANCH_SCREEN, MIND_MAP_BRANCH_WIDTH_SCREEN) }
        textItems.add(central)
        textItems.addAll(branches)
        for (branch in branches) connectors.add(Connector(UUID.randomUUID().toString(), central.id, branch.id))
        onTextChanged(textItems.toList())
        onConnectorsChanged(connectors.toList())
    }

    // ---- Bézier pen tool (roadmap item 96) ----

    /** Nodes of the path being built with [Tool.BEZIER]; empty when none is in progress. */
    val bezierNodes = mutableStateListOf<BezierNode>()

    /**
     * Bézier pen press: on the first node of a path of three or more nodes, closes and
     * finishes it; otherwise adds a node here (a corner until dragged).
     */
    fun beginBezierNode(world: WorldPoint) {
        val reach = BEZIER_CLOSE_SCREEN / scale.coerceAtLeast(1e-300)
        val first = bezierNodes.firstOrNull()
        if (first != null && bezierNodes.size >= 3 && hypot(first.point.x - world.x, first.point.y - world.y) <= reach) {
            finishBezierPath(closed = true)
            return
        }
        bezierNodes.add(BezierNode(world))
    }

    /** Dragging while placing a node pulls its handle out, making it smooth. */
    fun dragBezierHandle(world: WorldPoint) {
        val last = bezierNodes.lastOrNull() ?: return
        // Within the tap slop it stays a corner, so a tap with a little wobble isn't curved.
        val moved = hypot(world.x - last.point.x, world.y - last.point.y) * scale
        bezierNodes[bezierNodes.lastIndex] = last.copy(handleOut = if (moved <= TAP_SLOP_SCREEN) last.point else world)
    }

    fun removeLastBezierNode() {
        if (bezierNodes.isNotEmpty()) bezierNodes.removeAt(bezierNodes.lastIndex)
    }

    fun cancelBezierPath() {
        bezierNodes.clear()
    }

    /**
     * Commits the path as a stroke with the current pen settings (closed back to its first
     * node when [closed], so the fill tool can fill it), sampled finely enough to look smooth
     * at the current zoom. Undoable. A path of fewer than two nodes is discarded.
     */
    fun finishBezierPath(closed: Boolean) {
        val nodes = bezierNodes.toList()
        bezierNodes.clear()
        if (nodes.size < 2 || !activeLayerEditable) return
        recordHistory()
        val s = scale.coerceAtLeast(1e-300)
        val stroke = Stroke(
            activeColor.copy(alpha = activeColor.alpha * activeOpacity.toFloat()),
            activeWidthWorld / s,
            activePenType,
            blendMode = activeBlendMode,
            layerId = activeLayerId,
            animFrame = currentAnimFrame,
            dashed = activeDashed,
            lineStyle = activeLineStyle.takeIf { it != LineStyle() },
            brush = if (activePenType == PenType.STAMP) activeBrush else null,
        )
        for (point in flattenBezier(nodes, closed && nodes.size >= 3, BEZIER_SAMPLE_SCREEN / s)) stroke.addPoint(point)
        strokes.add(stroke)
        rememberActiveColor()
        strokesChanged()
    }

    // ---- Skeleton (roadmap item 83) ----

    /** Every frame's bones; each frame's are its pose (see [Bone]). */
    val bones = mutableStateListOf<Bone>()

    /** The bone tapped last with the bone tool, for deleting; null when none. */
    var selectedBoneId by mutableStateOf<String?>(null)

    /** With [Tool.BONE]: true to pose (rotate, move) the skeleton, false to add bones. */
    var posingBones by mutableStateOf(false)

    /**
     * Smooth joints (roadmap item 84): turning a bone bends the ink around its joint instead of
     * moving it rigidly. Each point of a stroke bound to the turned bone or to its parent follows
     * the turn by a weight from its distances to the two bones (inverse-distance: fully far out
     * along the turned bone, half at the joint, not at all far up the parent): it turns about
     * the joint by the bone's angle times its weight. Blending the angle, rather than the two
     * bones' positions as linear blend skinning does, keeps a bent joint from pinching. Weights are taken from the pose when the drag starts, and the
     * drag is applied from that starting geometry, so posing never drifts.
     */
    var smoothJoints by mutableStateOf(true)

    /**
     * Inverse kinematics (roadmap item 85): dragging a bone's tail pulls its chain - the bone
     * and up to [IK_CHAIN_BONES] - 1 ancestors - so the tail follows the finger, each joint
     * turning as needed (cyclic coordinate descent). Bound ink moves rigidly with its bone.
     */
    var inverseKinematics by mutableStateOf(false)

    /** The bones of the frame on screen. */
    val currentBones: List<Bone> get() = bones.filter { it.animFrame == currentAnimFrame }

    private enum class BoneGestureKind { ADD, ROTATE, MOVE, REACH, NONE }

    private data class BoneGesture(val kind: BoneGestureKind, val boneId: String?, val last: WorldPoint, val parentId: String? = null, val head: WorldPoint = last)

    private var boneGesture: BoneGesture? = null

    // A turn in progress, applied from its start each move: the turned bone's joint and starting
    // pointer angle, its subtree, the frame's bones then, and each moved stroke's starting points
    // with per-point weights (null = rigid).
    private class BoneTurn(
        val center: WorldPoint,
        val startAngle: Double,
        val moved: Set<String>,
        val startBones: List<Bone>,
        val strokes: List<Triple<Stroke, List<WorldPoint>, DoubleArray?>>,
    )

    private var boneTurn: BoneTurn? = null

    /** The bone being drawn by an add drag, for the overlay; null otherwise. */
    var boneDraft by mutableStateOf<Pair<WorldPoint, WorldPoint>?>(null)
        private set

    /**
     * A posable mannequin (roadmap item 108, in 2D): a rigged figure standing in the middle of
     * the view, about 0.6 of its height, on the current frame. Bones from the hips: spine, neck
     * and head; upper and lower arms from the shoulders; thighs and shins. Each bone carries a
     * limb drawn as a thick round-ended line (the head a circle) in the active colour, bound to
     * it, so the bone tool poses it straight away. One undo step.
     */
    fun insertMannequin() {
        if (!activeLayerEditable || viewportSize.height <= 0f) return
        recordHistory()
        val s = scale.coerceAtLeast(1e-300)
        val unit = viewportSize.height * 0.6 / s / 8 // the figure is 8 head-heights tall
        val hips = WorldPoint(panWorld.x, panWorld.y + unit * 0.5)
        fun at(dx: Double, dy: Double) = WorldPoint(hips.x + dx * unit, hips.y + dy * unit)
        val frame = currentAnimFrame
        val added = ArrayList<Bone>()
        val joints = ArrayList<Bone>()
        fun bone(parent: Bone?, head: WorldPoint, tail: WorldPoint) =
            Bone(UUID.randomUUID().toString(), parent?.id, head, tail, frame).also { added.add(it) }
        val spine = bone(null, hips, at(0.0, -2.5))
        val neck = bone(spine, spine.tail, at(0.0, -3.0))
        val head = bone(neck, neck.tail, at(0.0, -4.0))
        for (side in listOf(-1.0, 1.0)) {
            val shoulder = bone(spine, spine.tail, at(side * 1.0, -2.5)).also { joints.add(it) }
            val upperArm = bone(shoulder, shoulder.tail, at(side * 1.2, -1.0))
            bone(upperArm, upperArm.tail, at(side * 1.3, 0.3))
            val hip = bone(spine, hips, at(side * 0.5, 0.0)).also { joints.add(it) }
            val thigh = bone(hip, hip.tail, at(side * 0.6, 1.8))
            bone(thigh, thigh.tail, at(side * 0.6, 3.5))
        }
        bones.addAll(added)
        val ink = activeColor.copy(alpha = activeColor.alpha * activeOpacity.toFloat())
        for (b in added) {
            // The head is a circle round its bone; shoulder and hip bones are joints, not limbs.
            if (b in joints) continue
            val limb = if (b === head) {
                Stroke(ink, unit * 0.12, PenType.MARKER, layerId = activeLayerId, animFrame = frame, boneId = b.id).apply {
                    val c = WorldPoint((b.head.x + b.tail.x) / 2, (b.head.y + b.tail.y) / 2)
                    for (p in generateShapePoints(ShapeKind.ELLIPSE, WorldPoint(c.x - unit * 0.4, c.y - unit * 0.5), WorldPoint(c.x + unit * 0.4, c.y + unit * 0.5))) addPoint(p)
                }
            } else {
                val width = if (b === spine) unit * 0.9 else if (b === neck) unit * 0.3 else unit * 0.4
                Stroke(ink, width, PenType.MARKER, layerId = activeLayerId, animFrame = frame, boneId = b.id).apply {
                    addPoint(b.head)
                    addPoint(b.tail)
                }
            }
            strokes.add(limb)
        }
        activeTool = Tool.BONE
        posingBones = true
        onBonesChanged(bones.toList())
        strokesChanged()
    }

    fun loadBones(loaded: List<Bone>) {
        bones.clear()
        bones.addAll(loaded)
        selectedBoneId = null
    }

    private fun shiftBoneFrames(from: Int, by: Int) {
        for (i in bones.indices) if (bones[i].animFrame >= from) bones[i] = bones[i].copy(animFrame = bones[i].animFrame + by)
    }

    /**
     * Bone tool press. Adding: starts a new bone here, chained to a bone whose tail is within
     * reach (its joint snaps onto that tail). Posing: a press near a bone's tail turns that bone
     * about its joint; a press elsewhere on a root bone moves its whole skeleton. A press on a
     * bone also selects it.
     */
    fun beginBone(world: WorldPoint) {
        val reach = BONE_HIT_SCREEN / scale.coerceAtLeast(1e-300)
        val frameBones = currentBones
        val hit = boneAt(frameBones, world, reach)
        selectedBoneId = hit?.id
        if (!posingBones) {
            val parent = frameBones.filter { hypot(it.tail.x - world.x, it.tail.y - world.y) <= reach }.minByOrNull { hypot(it.tail.x - world.x, it.tail.y - world.y) }
            val head = parent?.tail ?: world
            boneGesture = BoneGesture(BoneGestureKind.ADD, null, world, parent?.id, head)
            boneDraft = head to world
            return
        }
        val tipBone = frameBones.filter { hypot(it.tail.x - world.x, it.tail.y - world.y) <= reach }.minByOrNull { hypot(it.tail.x - world.x, it.tail.y - world.y) }
        val gesture = when {
            tipBone != null && inverseKinematics && tipBone.parentId != null -> BoneGesture(BoneGestureKind.REACH, tipBone.id, world)
            tipBone != null -> BoneGesture(BoneGestureKind.ROTATE, tipBone.id, world)
            hit != null && hit.parentId == null -> BoneGesture(BoneGestureKind.MOVE, hit.id, world)
            hit != null -> BoneGesture(BoneGestureKind.ROTATE, hit.id, world)
            else -> BoneGesture(BoneGestureKind.NONE, null, world)
        }
        if (gesture.kind != BoneGestureKind.NONE) recordHistory()
        boneGesture = gesture
        boneTurn = if (gesture.kind == BoneGestureKind.ROTATE) startTurn(frameBones.first { it.id == gesture.boneId }, world) else null
    }

    private fun startTurn(bone: Bone, pointer: WorldPoint): BoneTurn {
        val frame = currentAnimFrame
        val frameBones = bones.filter { it.animFrame == frame }
        val moved = boneSubtree(frameBones, bone.id)
        val parent = if (smoothJoints) bone.parentId?.let { id -> frameBones.firstOrNull { it.id == id } } else null
        val affected = strokes.filter { it.animFrame == frame && (it.boneId in moved || (parent != null && it.boneId == parent.id)) }
        return BoneTurn(
            bone.head,
            atan2(pointer.y - bone.head.y, pointer.x - bone.head.x),
            moved,
            frameBones,
            affected.map { stroke ->
                val points = stroke.points.toList()
                // Only ink on the turned bone itself or its parent blends; deeper bones move rigidly.
                val weights = if (parent != null && (stroke.boneId == bone.id || stroke.boneId == parent.id)) {
                    DoubleArray(points.size) { i ->
                        val toBone = distanceToBone(bone, points[i])
                        val toParent = distanceToBone(parent, points[i])
                        if (toBone + toParent == 0.0) 0.5 else toParent / (toBone + toParent)
                    }
                } else null
                Triple(stroke, points, weights)
            },
        )
    }

    fun continueBone(world: WorldPoint) {
        val gesture = boneGesture ?: return
        when (gesture.kind) {
            BoneGestureKind.ADD -> {
                boneDraft = gesture.head to world
                boneGesture = gesture.copy(last = world)
            }
            BoneGestureKind.ROTATE -> {
                val turn = boneTurn ?: return
                val angle = atan2(world.y - turn.center.y, world.x - turn.center.x) - turn.startAngle
                val start = turn.startBones.associateBy { it.id }
                for (i in bones.indices) {
                    val b = bones[i]
                    if (b.animFrame == currentAnimFrame && b.id in turn.moved) {
                        val s0 = start[b.id] ?: continue
                        bones[i] = b.copy(head = rotateAbout(s0.head, turn.center, angle), tail = rotateAbout(s0.tail, turn.center, angle))
                    }
                }
                for ((stroke, points, weights) in turn.strokes) {
                    if (stroke.points.size != points.size) continue
                    stroke.setPoints(
                        if (weights == null) points.map { rotateAbout(it, turn.center, angle) }
                        else points.mapIndexed { i, p -> rotateAbout(p, turn.center, angle * weights[i]) }
                    )
                }
                geometryEpoch++
                boneGesture = gesture.copy(last = world)
            }
            BoneGestureKind.REACH -> {
                reachToward(gesture.boneId ?: return, world)
                boneGesture = gesture.copy(last = world)
            }
            BoneGestureKind.MOVE -> {
                val delta = world - gesture.last
                transformRig(gesture.boneId ?: return) { it + delta }
                boneGesture = gesture.copy(last = world)
            }
            BoneGestureKind.NONE -> {}
        }
    }

    fun endBone() {
        val gesture = boneGesture ?: return
        boneGesture = null
        boneTurn = null
        boneDraft = null
        when (gesture.kind) {
            BoneGestureKind.ADD -> {
                val tail = gesture.last
                // Too short to be a deliberate bone (a tap): nothing is added.
                if (hypot(tail.x - gesture.head.x, tail.y - gesture.head.y) < BONE_MIN_SCREEN / scale.coerceAtLeast(1e-300)) return
                recordHistory()
                val bone = Bone(UUID.randomUUID().toString(), gesture.parentId, gesture.head, tail, currentAnimFrame)
                bones.add(bone)
                selectedBoneId = bone.id
                onBonesChanged(bones.toList())
            }
            BoneGestureKind.ROTATE, BoneGestureKind.MOVE, BoneGestureKind.REACH -> {
                onBonesChanged(bones.toList())
                strokesChanged()
            }
            BoneGestureKind.NONE -> {}
        }
    }

    /** One move of an IK drag: turns the chain ending at [endId] so its tail nears [target]. */
    private fun reachToward(endId: String, target: WorldPoint) {
        val frame = currentAnimFrame
        fun bone(id: String) = bones.firstOrNull { it.animFrame == frame && it.id == id }
        val chain = generateSequence(bone(endId)) { b -> b.parentId?.let { bone(it) } }.take(IK_CHAIN_BONES).map { it.id }.toList()
        repeat(IK_ITERATIONS) {
            for (id in chain) {
                val joint = bone(id) ?: return
                val tip = bone(endId)?.tail ?: return
                val turn = atan2(target.y - joint.head.y, target.x - joint.head.x) - atan2(tip.y - joint.head.y, tip.x - joint.head.x)
                if (turn != 0.0) transformRig(id) { rotateAbout(it, joint.head, turn) }
            }
            val tip = bone(endId)?.tail ?: return
            if (hypot(tip.x - target.x, tip.y - target.y) < IK_TOLERANCE_SCREEN / scale.coerceAtLeast(1e-300)) return
        }
    }

    // Applies [transform] to bone [rootId], the bones hanging from it, and every stroke bound to
    // one of them, on the current frame.
    private fun transformRig(rootId: String, transform: (WorldPoint) -> WorldPoint) {
        val frame = currentAnimFrame
        val moved = boneSubtree(bones.filter { it.animFrame == frame }, rootId)
        for (i in bones.indices) {
            val b = bones[i]
            if (b.animFrame == frame && b.id in moved) bones[i] = b.copy(head = transform(b.head), tail = transform(b.tail))
        }
        for (stroke in strokes) {
            if (stroke.animFrame == frame && stroke.boneId in moved) stroke.setPoints(stroke.points.map(transform))
        }
        geometryEpoch++
    }

    /**
     * Binds every stroke on the current frame to its nearest bone (by distance from the
     * stroke's centre to the bone), the usual automatic binding. Strokes on frames without
     * bones are left as they are. Undoable.
     */
    fun bindStrokesToNearestBones() {
        val frameBones = currentBones
        if (frameBones.isEmpty()) return
        recordHistory()
        for (i in strokes.indices) {
            val stroke = strokes[i]
            if (stroke.animFrame != currentAnimFrame || stroke.points.isEmpty()) continue
            val center = WorldPoint((stroke.minX + stroke.maxX) / 2, (stroke.minY + stroke.maxY) / 2)
            val nearest = frameBones.minBy { distanceToBone(it, center) }
            if (stroke.boneId != nearest.id) strokes[i] = stroke.onBone(nearest.id)
        }
        strokesChanged()
    }

    /**
     * Deletes the selected bone and the bones hanging from it, on every frame (a bone is one
     * limb throughout the animation). Strokes bound to them are unbound, not deleted. Undoable.
     */
    fun deleteSelectedBone() {
        val id = selectedBoneId ?: return
        recordHistory()
        val removed = bones.map { it.animFrame }.distinct().flatMap { frame -> boneSubtree(bones.filter { it.animFrame == frame }, id) }.toSet()
        bones.removeWhere { it.id in removed }
        for (i in strokes.indices) if (strokes[i].boneId in removed) strokes[i] = strokes[i].onBone(null)
        selectedBoneId = null
        onBonesChanged(bones.toList())
        strokesChanged()
    }

    fun deleteFrame(id: String) {
        frames.removeWhere { it.id == id }
        onFramesChanged(frames.toList())
    }

    fun loadFrames(loaded: List<Frame>) {
        frames.clear()
        frames.addAll(loaded)
    }

    /** Fits [frame] to the screen, upright. */
    fun jumpToFrame(frame: Frame) {
        val viewport = viewportSize
        if (viewport.width <= 0f || viewport.height <= 0f) return
        val width = (frame.max.x - frame.min.x).coerceAtLeast(1e-9)
        val height = (frame.max.y - frame.min.y).coerceAtLeast(1e-9)
        rotation = 0.0
        jumpTo(
            WorldPoint((frame.min.x + frame.max.x) / 2.0, (frame.min.y + frame.max.y) / 2.0),
            minOf(viewport.width / width, viewport.height / height),
        )
    }

    /** Drawing layers, bottom first. Never empty. */
    val layers = mutableStateListOf(Layer(DEFAULT_LAYER_ID, "Layer 1"))

    /** Layer new strokes, shapes and fills go on. */
    var activeLayerId by mutableStateOf(DEFAULT_LAYER_ID)

    private fun layerOf(stroke: Stroke): Layer? = layers.firstOrNull { it.id == stroke.layerId }

    /** Shown and unlocked: tools may change it. Strokes on a layer that no longer exists count as editable. */
    // Only the current animation frame can be edited or picked from; other frames are hidden
    // (or shown as a faint onion skin) and must not be erased or selected by accident.
    private fun isEditable(stroke: Stroke): Boolean =
        stroke.animFrame == currentAnimFrame && (layerOf(stroke)?.let { it.visible && !it.locked } ?: true)

    private fun isVisible(stroke: Stroke): Boolean =
        stroke.animFrame == currentAnimFrame && (layerOf(stroke)?.visible ?: true)

    /** Drawing tools do nothing on a hidden or locked active layer, rather than adding invisible or "locked" content. */
    private val activeLayerEditable: Boolean
        get() = layers.firstOrNull { it.id == activeLayerId }?.let { it.visible && !it.locked } ?: false

    fun addLayer() {
        val layer = Layer(UUID.randomUUID().toString(), "Layer ${layers.size + 1}")
        layers.add(layer)
        activeLayerId = layer.id
        onLayersChanged(layers.toList())
    }

    fun updateLayer(id: String, change: (Layer) -> Layer) {
        val index = layers.indexOfFirst { it.id == id }
        if (index == -1) return
        layers[index] = change(layers[index])
        onLayersChanged(layers.toList())
    }

    /** Moves a layer one step up (towards the top) or down in the stack. */
    fun moveLayer(id: String, up: Boolean) {
        val index = layers.indexOfFirst { it.id == id }
        val target = if (up) index + 1 else index - 1
        if (index == -1 || target !in layers.indices) return
        layers.add(target, layers.removeAt(index))
        onLayersChanged(layers.toList())
    }

    /** Deletes a layer and its strokes (undoable for the strokes). The last layer can't be deleted. */
    fun deleteLayer(id: String) {
        if (layers.size <= 1) return
        recordHistory()
        clearSelectionState()
        strokes.removeWhere { it.layerId == id }
        layers.removeWhere { it.id == id }
        if (activeLayerId == id) activeLayerId = layers.last().id
        strokesChanged()
        onLayersChanged(layers.toList())
    }

    /** Removes layers with no strokes in any animation frame, keeping at least one layer. */
    fun deleteEmptyLayers() {
        val used = strokes.map { it.layerId }.toSet()
        val keep = layers.filter { it.id in used }.ifEmpty { listOf(layers.first()) }
        if (keep.size == layers.size) return
        layers.retainAll(keep.toSet())
        if (layers.none { it.id == activeLayerId }) activeLayerId = layers.last().id
        onLayersChanged(layers.toList())
    }

    /** Deletes every hidden layer and its strokes (undoable for the strokes), keeping at least one layer. */
    fun deleteHiddenLayers() {
        val hidden = layers.filter { !it.visible }.map { it.id }.toSet()
        if (hidden.isEmpty() || hidden.size == layers.size) return
        recordHistory()
        clearSelectionState()
        strokes.removeWhere { it.layerId in hidden }
        layers.removeWhere { it.id in hidden }
        if (activeLayerId in hidden) activeLayerId = layers.last().id
        strokesChanged()
        onLayersChanged(layers.toList())
    }

    /** Moves every stroke of a layer onto the layer below it, then removes the emptied layer. */
    fun mergeLayerDown(id: String) {
        val index = layers.indexOfFirst { it.id == id }
        if (index <= 0) return
        val below = layers[index - 1]
        recordHistory()
        clearSelectionState()
        for (i in strokes.indices) {
            if (strokes[i].layerId == id) strokes[i] = strokes[i].onLayer(below.id)
        }
        layers.removeAt(index)
        if (activeLayerId == id) activeLayerId = below.id
        strokesChanged()
        onLayersChanged(layers.toList())
    }

    /** Replaces the layer stack (e.g. from [CanvasStorage.loadLayers]); an empty list means the single default layer. */
    fun loadLayers(loaded: List<Layer>) {
        layers.clear()
        layers.addAll(loaded.ifEmpty { listOf(Layer(DEFAULT_LAYER_ID, "Layer 1")) })
        activeLayerId = layers.last().id
    }

    /** The user's saved palette (app-wide, not per document), oldest first. */
    val savedColors = mutableStateListOf<Color>()

    fun addSavedColor(color: Color) {
        if (color in savedColors) return
        savedColors.add(color)
        while (savedColors.size > MAX_SAVED_COLORS) savedColors.removeAt(0)
        onSavedColorsChanged(savedColors.toList())
    }

    /**
     * Shared colour styles (roadmap item 100): changes saved colour [old] to [new] everywhere -
     * in the palette, the pen, and every stroke, fill gradient and label drawn in it, on every
     * layer and frame. Matching is by colour, ignoring alpha, so each stroke keeps its own
     * opacity; anything else drawn in exactly the same colour changes too. One undo step; does
     * nothing if no stroke or label uses it.
     */
    fun replaceColorEverywhere(old: Color, new: Color) {
        fun same(c: Color) = c.copy(alpha = 1f) == old.copy(alpha = 1f)
        val strokeHits = strokes.indices.filter { same(strokes[it].color) || strokes[it].gradient?.let { g -> same(g.endColor) } == true }
        val textHits = textItems.indices.filter { same(textItems[it].color) }
        if (strokeHits.isNotEmpty() || textHits.isNotEmpty()) {
            recordHistory()
            for (i in strokeHits) {
                val stroke = strokes[i]
                var changed = if (same(stroke.color)) stroke.withColor(new.copy(alpha = stroke.color.alpha)) else stroke
                val gradient = changed.gradient
                if (gradient != null && same(gradient.endColor)) changed = changed.withGradient(gradient.copy(endColor = new.copy(alpha = gradient.endColor.alpha)))
                strokes[i] = changed
            }
            for (i in textHits) textItems[i] = textItems[i].copy(color = new.copy(alpha = textItems[i].color.alpha))
            clearSelectionState()
            strokesChanged()
            if (textHits.isNotEmpty()) onTextChanged(textItems.toList())
        }
        val index = savedColors.indexOf(old)
        if (index != -1) {
            savedColors[index] = new
            onSavedColorsChanged(savedColors.toList())
        }
        if (same(activeColor)) activeColor = new.copy(alpha = activeColor.alpha)
    }

    /**
     * Indexed palette for pixel art (roadmap item 104): while on, pixel-pen strokes are drawn in
     * the saved colour nearest the chosen one, so a sprite keeps to its palette.
     */
    var indexedPalette by mutableStateOf(false)

    /** The saved colour nearest [color] (RGB distance, alpha kept), or [color] with no saved colours. */
    fun nearestPaletteColor(color: Color): Color {
        val nearest = savedColors.minByOrNull {
            val dr = it.red - color.red
            val dg = it.green - color.green
            val db = it.blue - color.blue
            dr * dr + dg * dg + db * db
        } ?: return color
        return nearest.copy(alpha = color.alpha)
    }

    /** Recolours the selection to the nearest saved colours (roadmap item 104), keeping opacity. One undo step. */
    fun reduceSelectionToPalette() {
        if (selection.isEmpty() || savedColors.isEmpty()) return
        recordHistory()
        val reduced = selection.associateWith { it.withColor(nearestPaletteColor(it.color)) }
        for (i in strokes.indices) reduced[strokes[i]]?.let { strokes[i] = it }
        selection.clear()
        selection.addAll(reduced.values)
        strokesChanged()
    }

    fun removeSavedColor(color: Color) {
        if (savedColors.remove(color)) onSavedColorsChanged(savedColors.toList())
    }

    fun loadSavedColors(loaded: List<Color>) {
        savedColors.clear()
        savedColors.addAll(loaded)
    }

    /** Colours most recently drawn with, newest first, for quick re-picking from the toolbar. */
    val recentColors = mutableStateListOf<Color>()

    /** Records [activeColor] as used; called whenever a new stroke, shape or fill is created. */
    private fun rememberActiveColor() {
        recentColors.remove(activeColor)
        recentColors.add(0, activeColor)
        while (recentColors.size > MAX_RECENT_COLORS) recentColors.removeAt(recentColors.size - 1)
    }

    /**
     * EYEDROPPER tool: takes the colour of the topmost stroke under [worldPoint] (fills by
     * containment, lines by distance within their width), then returns to the pen so the
     * picked colour can be drawn with immediately. The stroke's alpha is dropped because it
     * already includes the opacity slider's value; opacity stays a separate setting.
     */
    fun pickColorAt(worldPoint: WorldPoint) {
        val tolerance = EYEDROPPER_TOLERANCE_SCREEN / scale.coerceAtLeast(1e-300)
        val hit = strokes.lastOrNull { stroke ->
            if (!isVisible(stroke)) false
            else if (stroke.filled) polygonContains(stroke.points, worldPoint)
            else distanceToPolyline(stroke.points, worldPoint) <= stroke.widthWorld / 2 + tolerance
        } ?: return
        activeColor = hit.color.copy(alpha = 1f)
        activeTool = Tool.PEN
    }

    /** Fills are drawn as a comic screentone (dots) instead of solid. */
    var activeScreentone by mutableStateOf(false)

    /** Fills fade from the colour to transparent (top to bottom) instead of being solid. */
    var activeFadeFill by mutableStateOf(false)

    /** Two-colour gradient for new fills (from the active colour), or null for none; exclusive with [activeFadeFill]. */
    var activeGradient: FillGradient? by mutableStateOf(null)

    /** New strokes and shapes are drawn dashed. */
    var activeDashed by mutableStateOf(false)

    /** Blend mode applied to new strokes - see [StrokeBlendMode]. */
    var activeBlendMode by mutableStateOf(StrokeBlendMode.NORMAL)

    /** The shape currently being dragged out (SHAPE tool), (start, snappedEnd) in world space. Null when none is in progress. */
    var shapePreview: Pair<WorldPoint, WorldPoint>? by mutableStateOf(null)
        private set

    private var shapeStart = WorldPoint.Zero

    /** Every text label placed on the canvas. */
    val textItems = mutableStateListOf<TextItem>()

    /** A tap with the TEXT tool that's waiting on the entry dialog: which item (if any, by id) is being edited, and where a new one would go. */
    data class PendingTextEdit(
        val id: String?,
        val worldPoint: WorldPoint,
        val initialText: String,
        val initialBold: Boolean = false,
        val initialItalic: Boolean = false,
        val initialSticky: Boolean = false,
        /** On-screen text size in px at the current zoom. */
        val initialSizePx: Double = 24.0,
        val initialFont: TextFont = TextFont.SANS,
        val initialAlignment: TextAlignment = TextAlignment.START,
        /** The text box width (world units) the label will get, or null for no box; see [TextItem.boxWidthWorld]. */
        val boxWidthWorld: Double? = null,
        val initialLink: LinkTarget? = null,
        val initialOutlined: Boolean = false,
        /** The path a new label will follow, relative to [worldPoint]; see [TextItem.path]. */
        val path: List<WorldPoint>? = null,
    )

    /** While dragging out a text box with the TEXT tool: its opposite corners, for the preview outline. */
    var textBoxDraft: Pair<WorldPoint, WorldPoint>? by mutableStateOf(null)
        private set

    private var textGestureStart: WorldPoint? = null

    var pendingTextEdit: PendingTextEdit? by mutableStateOf(null)
        private set

    /** Every placed image on the canvas. */
    val imageItems = mutableStateListOf<ImageItem>()

    /** This document's page background color, matching the default canvas backdrop until changed. */
    var backgroundColor by mutableStateOf(PaperPreset.LIGHT.background)
        private set

    /** This document's page pattern - see [BackgroundStyle]. */
    var backgroundStyle by mutableStateOf(BackgroundStyle.PLAIN)
        private set

    /** Sets the document's background color/style and persists the change via [onBackgroundChanged]. */
    fun setBackground(color: Color, style: BackgroundStyle) {
        backgroundColor = color
        backgroundStyle = style
        onBackgroundChanged(color, style)
    }

    /**
     * Switches to [paper] (a [PaperPreset]), keeping the pattern. If the pen's colour would
     * nearly vanish on the new paper (contrast under 3:1, WCAG's floor for graphics), the pen
     * switches to the preset's ink, so the next stroke can be seen; existing strokes keep their colours.
     */
    fun applyPaperPreset(paper: PaperPreset) {
        setBackground(paper.background, backgroundStyle)
        if (contrastRatio(activeColor, paper.background) < 3.0) activeColor = paper.ink
    }

    /** Replaces the background with [color]/[style] without persisting (e.g. loading a document). */
    fun loadBackground(color: Color, style: BackgroundStyle) {
        backgroundColor = color
        backgroundStyle = style
    }

    /**
     * The ruler guide's two endpoints in world space, or null if never placed. Visible and
     * draggable only while [rulerActive]; a real drafting ruler stays on the page and can be
     * put away, so toggling it off hides it without discarding its position.
     */
    var rulerLine: Pair<WorldPoint, WorldPoint>? by mutableStateOf(null)
        private set

    /** Whether the ruler guide is currently placed/visible and snapping pen strokes to it. */
    var rulerActive by mutableStateOf(false)
        private set

    private enum class RulerDragMode { NONE, START, END, TRANSLATE }
    private var rulerDragMode = RulerDragMode.NONE
    private var rulerDragLast = WorldPoint.Zero

    /** Currently selected strokes (SELECT tool). Empty when nothing is selected. */
    val selection = mutableStateListOf<Stroke>()

    // The marquee rectangle currently being dragged out (SELECT tool), in world space, as
    // (dragStart, currentPoint) - not yet normalized into a left/top/right/bottom rect,
    // since the drag can go in any direction. Null when no marquee is in progress.
    var marqueeRect: Pair<WorldPoint, WorldPoint>? by mutableStateOf(null)

    /** SELECT tool mode: false = rectangle marquee, true = freehand lasso. */
    var lassoSelect by mutableStateOf(false)

    /** The lasso outline being drawn, in world space; empty when no lasso is in progress. */
    val lassoPath = mutableStateListOf<WorldPoint>()

    private var isMovingSelection = false
    private var lastDragPoint: WorldPoint? = null

    // Smart guides (roadmap item 99) for a selection move: where the drag started, the selection's
    // bounds then, how far it has been moved so far, and the other objects' edge and centre lines.
    private var moveStart = WorldPoint.Zero
    private var moveStartBounds: Pair<WorldPoint, WorldPoint>? = null
    private var moveApplied = WorldPoint.Zero
    private var guideTargetsX = DoubleArray(0)
    private var guideTargetsY = DoubleArray(0)

    /** Guide lines the moving selection is snapped to: x of vertical lines, y of horizontal ones. */
    var smartGuides by mutableStateOf<Pair<Double?, Double?>>(null to null)
        private set

    // Scale/rotate gesture state. Both apply their transform fresh each frame from a
    // snapshot of each selected stroke's points taken at gesture start (gestureSnapshot),
    // rather than accumulating incremental deltas - which would drift over a long drag.
    private var gestureAnchor = WorldPoint.Zero // scale: opposite corner; rotate: pivot (centroid)
    private var gestureStartDistance = 0.0 // scale only
    private var gestureStartAngle = 0.0 // rotate only
    private var gestureSnapshot: Map<Stroke, List<WorldPoint>> = emptyMap()
    private var gestureSnapshotWidths: Map<Stroke, Double> = emptyMap()

    private var currentStroke: Stroke? = null

    // Snapshot history: each entry is a deep copy of the whole stroke list before (undo) or
    // after (redo) an action. The previous design popped the last stroke, which was only
    // correct for drawing - undoing an erase, move, fill or clear removed an unrelated stroke
    // instead of reverting the action. Snapshots make every stroke action undoable the same way.
    // Images and text labels are recorded too, so removing or moving one is undoable. They're
    // immutable values, so the list itself is the copy. Null means "not recorded" (history
    // reloaded from disk, which stores strokes only) and leaves the current ones as they are.
    //
    // An entry holds each stroke by reference plus its immutable geometry ([Stroke.State],
    // captured copy-on-write in O(1) and shared between entries while a stroke is untouched),
    // not a deep copy: a deep copy per action cost O(document) time and up to 64 x the
    // document in memory, which froze a 100k-stroke document for over a minute per erase
    // (ScaleBenchmark). [shift] accumulates floating-origin rebases since capture, applied
    // when the entry is restored instead of rewriting every snapshot on each rebase.
    private class HistoryEntry(
        val strokes: Array<Stroke>,
        val states: Array<Stroke.State>,
        var images: List<ImageItem>?,
        var texts: List<TextItem>? = null,
        var shift: WorldPoint = WorldPoint.Zero,
        val connectors: List<Connector>? = null,
        var bones: List<Bone>? = null,
        val holds: Map<Int, Int>? = null,
    ) {
        /** Independent strokes holding this entry's geometry (shift applied), e.g. for saving. */
        fun materialize(): List<Stroke> = strokes.indices.map { i ->
            strokes[i].withState(states[i]).also { if (shift != WorldPoint.Zero) it.shiftBy(shift) }
        }

        companion object {
            fun of(strokes: List<Stroke>, images: List<ImageItem>?, texts: List<TextItem>? = null, connectors: List<Connector>? = null, bones: List<Bone>? = null, holds: Map<Int, Int>? = null) =
                HistoryEntry(strokes.toTypedArray(), Array(strokes.size) { strokes[it].captureState() }, images, texts, connectors = connectors, bones = bones, holds = holds)
        }
    }

    /**
     * Every completed stroke mutation ends here: reports it for autosave ([onChanged]) and marks
     * the spatial index stale, since an in-place edit (a move, a transform) changes geometry
     * without changing the list.
     */
    private fun strokesChanged() {
        geometryEpoch++
        onChanged(strokes.toList())
    }

    // Bumped by strokesChanged() and whenever strokes move in place; part of [sceneVersion].
    private var geometryEpoch = 0
    private var spatialIndex: StrokeSpatialIndex? = null
    private var indexedStrokes: List<Stroke>? = null
    // The list and epoch [patchPrefix]/[patchSuffix]/[patchDirty] were worked out for.
    private var validatedList: List<Stroke>? = null
    private var validatedEpoch = -1
    // Common prefix/suffix of the current list with the indexed one, and the positions (in the
    // current list, ascending) of strokes the index can't vouch for: new, or edited in place.
    private var patchPrefix = 0
    private var patchSuffix = 0
    private var patchDirty = IntArray(0)

    /**
     * The spatial index, reused across edits where possible. Rebuilding costs ~0.35 s at a
     * million strokes (roadmap item 18), so after a change only the strokes that differ from
     * what was indexed - by identity or by [Stroke.plainRevision] - are found (an O(n) scan of
     * plain fields) and tested directly; the index is rebuilt once more than [MAX_INDEX_PATCH]
     * of them have piled up.
     */
    private fun validIndexFor(list: List<Stroke>): StrokeSpatialIndex {
        val index = spatialIndex
        val base = indexedStrokes
        if (index == null || base == null) return rebuildIndex(list)
        if (list === validatedList && geometryEpoch == validatedEpoch) return index
        val shorter = minOf(base.size, list.size)
        var prefix = 0
        while (prefix < shorter && base[prefix] === list[prefix]) prefix++
        var suffix = 0
        while (suffix < shorter - prefix && base[base.size - 1 - suffix] === list[list.size - 1 - suffix]) suffix++
        if (list.size - prefix - suffix > MAX_INDEX_PATCH) return rebuildIndex(list)
        val dirty = IntArrayList()
        for (i in 0 until prefix) {
            if (list[i].plainRevision != index.revisionAt(i)) dirty.add(i)
            if (dirty.size > MAX_INDEX_PATCH) return rebuildIndex(list)
        }
        for (j in prefix until list.size - suffix) dirty.add(j)
        for (k in 0 until suffix) {
            if (list[list.size - suffix + k].plainRevision != index.revisionAt(base.size - suffix + k)) dirty.add(list.size - suffix + k)
            if (dirty.size > MAX_INDEX_PATCH) return rebuildIndex(list)
        }
        validatedList = list
        validatedEpoch = geometryEpoch
        patchPrefix = prefix
        patchSuffix = suffix
        patchDirty = dirty.toSortedArray()
        return index
    }

    private fun rebuildIndex(list: List<Stroke>): StrokeSpatialIndex = StrokeSpatialIndex(list).also {
        spatialIndex = it
        indexedStrokes = list
        validatedList = list
        validatedEpoch = geometryEpoch
        patchPrefix = list.size
        patchSuffix = 0
        patchDirty = IntArray(0)
    }

    /**
     * Strokes that may intersect the world rectangle, in drawing order - what rendering needs,
     * in time proportional to what's nearby instead of the whole document (roadmap item 49).
     * The index is reused across edits (see [validIndexFor]); strokes being edited
     * right now (the stroke being drawn, its symmetry copies, the selection) can change shape
     * before their action completes, so they're always included. Candidates only: callers
     * still test [Stroke.intersects].
     */
    fun strokesNear(left: Double, top: Double, right: Double, bottom: Double): List<Stroke> {
        val list = strokes.toList() // the list's immutable snapshot: same instance until it changes
        val index = validIndexFor(list)
        val found = if (patchDirty.isEmpty() && indexedStrokes === list) index.query(left, top, right, bottom) else {
            val baseSize = indexedStrokes!!.size
            val shift = list.size - baseSize
            val positions = IntArrayList()
            for (i in index.query(left, top, right, bottom)) {
                val at = when {
                    i < patchPrefix -> i
                    i >= baseSize - patchSuffix -> i + shift
                    else -> continue // in the replaced run, gone from the list
                }
                if (java.util.Arrays.binarySearch(patchDirty, at) < 0) positions.add(at)
            }
            // Changed strokes aren't where the index thinks: tested directly.
            for (j in patchDirty) if (list[j].intersects(left, top, right, bottom)) positions.add(j)
            positions.toSortedArray()
        }
        val live = buildList {
            currentStroke?.let(::add)
            symmetryCopies.forEach { add(it.first) }
            addAll(selection)
        }
        if (live.isEmpty()) return found.map { list[it] }
        val positions = java.util.TreeSet<Int>()
        found.forEach(positions::add)
        // Searched from the end: the stroke being drawn and fresh pastes/duplicates are last.
        for (stroke in live) {
            var at = list.size - 1
            while (at >= 0 && list[at] !== stroke) at--
            if (at >= 0) positions.add(at)
        }
        return positions.map { list[it] }
    }

    /** What the rendered scene depends on, for [SceneTileCache]; comparing two is O(1). */
    fun sceneVersion() = SceneVersion(
        listOf(strokes.toList(), geometryEpoch, layers.toList(), textItems.toList(), imageItems.toList(), connectors.toList(), backgroundColor, backgroundStyle, currentAnimFrame),
    )

    /** Arrows between text labels and sticky notes; see [Connector]. */
    val connectors = mutableStateListOf<Connector>()

    /** Replaces the connectors, keeping only those whose two labels exist (call after [loadTextItems]). */
    fun loadConnectors(loaded: List<Connector>) {
        val ids = textItems.mapTo(HashSet()) { it.id }
        connectors.clear()
        connectors.addAll(loaded.filter { it.fromId in ids && it.toId in ids })
    }

    /** While dragging out a connector with the CONNECT tool: the label it starts from and the finger's point. */
    var connectorDraft: Pair<String, WorldPoint>? by mutableStateOf(null)
        private set

    /**
     * CONNECT tool: press on a label and drag to another to connect them (a connector between
     * the same two labels in the same direction isn't added twice). Pressing on an existing
     * connector, away from any label, removes it.
     */
    fun beginConnect(worldPoint: WorldPoint) {
        val from = findTextItemAt(worldPoint)
        if (from != null) {
            connectorDraft = from.id to worldPoint
            return
        }
        val hit = connectorAt(worldPoint) ?: return
        recordHistory()
        connectors.remove(hit)
        onConnectorsChanged(connectors.toList())
    }

    fun continueConnect(worldPoint: WorldPoint) {
        val draft = connectorDraft ?: return
        connectorDraft = draft.first to worldPoint
    }

    fun endConnect() {
        val (fromId, point) = connectorDraft ?: return
        connectorDraft = null
        val to = findTextItemAt(point) ?: return
        if (to.id == fromId || connectors.any { it.fromId == fromId && it.toId == to.id }) return
        recordHistory()
        connectors.add(Connector(UUID.randomUUID().toString(), fromId, to.id))
        onConnectorsChanged(connectors.toList())
    }

    /** Lays out the connected labels as a layered flowchart ([tidyDiagram]); one undo step. */
    fun tidyDiagram() {
        if (connectors.isEmpty()) return
        val boxes = textItems.associate { it.id to drawnBounds(it) }
        val positions = boxes.mapValues { it.value.first }
        val sizes = boxes.mapValues { (_, b) -> (b.second.x - b.first.x) to (b.second.y - b.first.y) }
        val gap = DIAGRAM_GAP_SCREEN / scale.coerceAtLeast(1e-300)
        val laid = tidyDiagram(positions, sizes, connectors.toList(), gap, gap)
        if (laid.isEmpty()) return
        recordHistory()
        for (i in textItems.indices) {
            val item = textItems[i]
            // Layout is of drawn boxes; keep each label's offset from its box.
            laid[item.id]?.let { textItems[i] = item.copy(position = it + (item.position - boxes.getValue(item.id).first)) }
        }
        onTextChanged(textItems.toList())
    }

    /**
     * The two labels' boxes for [connector] as drawn, or null if either label is gone. A sticky
     * note's paper extends half its text height around the text (see drawScene), so its box
     * does too - otherwise the arrowhead ends under the note and is hidden by it.
     */
    fun connectorEnds(connector: Connector): Pair<Pair<WorldPoint, WorldPoint>, Pair<WorldPoint, WorldPoint>>? {
        val from = textItems.firstOrNull { it.id == connector.fromId } ?: return null
        val to = textItems.firstOrNull { it.id == connector.toId } ?: return null
        return drawnBounds(from) to drawnBounds(to)
    }

    private fun drawnBounds(item: TextItem): Pair<WorldPoint, WorldPoint> {
        val (min, max) = textBounds(item)
        if (!item.sticky) return min to max
        val pad = (max.y - min.y) / 2
        return WorldPoint(min.x - pad, min.y - pad) to WorldPoint(max.x + pad, max.y + pad)
    }

    private fun connectorAt(point: WorldPoint): Connector? {
        val tolerance = CONNECTOR_HIT_SCREEN / scale.coerceAtLeast(1e-300)
        return connectors.lastOrNull { connector ->
            val (from, to) = connectorEnds(connector) ?: return@lastOrNull false
            val (a, b) = connectorSegment(from.first, from.second, to.first, to.second) ?: return@lastOrNull false
            distanceToPolyline(listOf(a, b), point) <= tolerance
        }
    }

    /**
     * Whether the scene may be drawn from [SceneTileCache]: a large document, and nothing that
     * changes every frame - a stroke being drawn or erased, a selection, an image or label being
     * dragged, animation or replay - since those must be drawn live, in their stacking order.
     */
    val tileCacheEligible: Boolean
        get() = strokes.size >= TILE_CACHE_MIN_STROKES && currentStroke == null && selection.isEmpty() &&
            !isErasing && !isMovingImage && !isMovingText && !animationMode && replayCount == null

    private var isErasing = false

    private val undoStack = ArrayDeque<HistoryEntry>()
    private val redoStack = ArrayDeque<HistoryEntry>()

    private fun currentEntry() = HistoryEntry.of(strokes, imageItems.toList(), textItems.toList(), connectors.toList(), bones.toList(), frameHolds.toMap())

    /** Called at the start of every stroke-changing action; also invalidates redo, standard editor semantics. */
    private fun recordHistory() {
        undoStack.addLast(currentEntry())
        while (undoStack.size > MAX_UNDO_STEPS) undoStack.removeFirst()
        redoStack.clear()
    }

    /** Undo and redo stroke snapshots, oldest first, for [CanvasStorage.saveHistory]. */
    fun historySnapshot(): Pair<List<List<Stroke>>, List<List<Stroke>>> {
        // Materialized per step on access, so a caller keeping only the newest few steps (as
        // CanvasStorage.saveHistory does) doesn't build strokes for the rest. Read it right away:
        // it reflects the stacks as they are when each step is read.
        fun lazySteps(entries: ArrayDeque<HistoryEntry>) = object : AbstractList<List<Stroke>>() {
            override val size get() = entries.size
            override fun get(index: Int) = entries[index].materialize()
        }
        return lazySteps(undoStack) to lazySteps(redoStack)
    }

    /** Restores history saved by [CanvasStorage.saveHistory]; call after [loadStrokes], which clears it. */
    fun loadHistory(undo: List<List<Stroke>>, redo: List<List<Stroke>>) {
        undoStack.clear()
        undoStack.addAll(undo.takeLast(MAX_UNDO_STEPS).map { HistoryEntry.of(it, null) })
        redoStack.clear()
        redoStack.addAll(redo.map { HistoryEntry.of(it, null) })
    }

    private fun restore(snapshot: List<Stroke>) = restore(HistoryEntry.of(snapshot, null))

    private fun restore(entry: HistoryEntry) {
        val texts = entry.texts
        if (texts != null && texts != textItems.toList()) {
            textItems.clear()
            textItems.addAll(texts)
            onTextChanged(textItems.toList())
        }
        val images = entry.images
        if (images != null && images != imageItems.toList()) {
            imageItems.clear()
            imageItems.addAll(images)
            onImageChanged(imageItems.toList())
        }
        val savedConnectors = entry.connectors
        if (savedConnectors != null && savedConnectors != connectors.toList()) {
            connectors.clear()
            connectors.addAll(savedConnectors)
            onConnectorsChanged(connectors.toList())
        }
        val savedHolds = entry.holds
        if (savedHolds != null && savedHolds != frameHolds.toMap()) {
            frameHolds.clear()
            frameHolds.putAll(savedHolds)
            onAnimationSettingsChanged(animationSettings())
        }
        val savedBones = entry.bones
        if (savedBones != null && savedBones != bones.toList()) {
            bones.clear()
            bones.addAll(savedBones)
            selectedBoneId = selectedBoneId?.takeIf { id -> bones.any { it.id == id } }
            onBonesChanged(bones.toList())
        }
        // The entry's own stroke objects come back with its geometry: O(1) each unless a rebase
        // happened since (then each is shifted, copying its arrays once).
        for (i in entry.strokes.indices) {
            entry.strokes[i].restoreState(entry.states[i])
            if (entry.shift != WorldPoint.Zero) entry.strokes[i].shiftBy(entry.shift)
        }
        strokes.clear()
        strokes.addAll(entry.strokes)
        // Undoing a frame insert/delete changes how many frames have strokes; keep the count in step.
        animFrameCount = (strokes.maxOfOrNull { it.animFrame } ?: 0) + 1
        currentAnimFrame = currentAnimFrame.coerceAtMost(animFrameCount - 1)
    }

    fun worldToScreen(world: WorldPoint, screenCenter: Offset): Offset {
        val relative = world - panWorld
        return Offset(
            x = screenCenter.x + (relative.x * scale).toFloat(),
            y = screenCenter.y + (relative.y * scale).toFloat(),
        )
    }

    fun screenToWorld(screen: Offset, screenCenter: Offset): WorldPoint {
        return WorldPoint(
            x = (screen.x - screenCenter.x).toDouble() / scale + panWorld.x,
            y = (screen.y - screenCenter.y).toDouble() / scale + panWorld.y,
        )
    }

    fun beginStroke(worldPoint: WorldPoint, pressure: Float = 1f, tilt: Float = 0f, orientation: Float = 0f) {
        // Touching a vanishing point's handle moves it instead of drawing (works on locked layers too: it's a guide).
        if (perspectiveMode != PerspectiveMode.OFF) {
            val hit = vanishingPoints.indexOfFirst { hypot(it.x - worldPoint.x, it.y - worldPoint.y) * scale <= VANISHING_POINT_HANDLE_SCREEN }
            if (hit != -1) {
                draggingVanishingPoint = hit
                return
            }
        }
        perspectiveDirection = null
        if (!activeLayerEditable) return
        strokeStartDepth = undoStack.size
        strokeStartMillis = System.currentTimeMillis()
        strokeMovedAway = false
        recordHistory()
        clearSelectionState() // avoid a stale selection referencing strokes another tool is about to change
        // Indexed palette (roadmap item 104): pixel art stays within the saved colours.
        val inkColor = if (activePenType == PenType.PIXEL && indexedPalette) nearestPaletteColor(activeColor) else activeColor
        val stroke = Stroke(
            inkColor.copy(alpha = inkColor.alpha * activeOpacity.toFloat()),
            activeWidthWorld / scale.coerceAtLeast(1e-300),
            activePenType,
            blendMode = activeBlendMode,
            layerId = activeLayerId,
            animFrame = currentAnimFrame,
            dashed = activeDashed,
            lineStyle = activeLineStyle.takeIf { it != LineStyle() },
            brush = if (activePenType == PenType.STAMP) activeBrush else null,
        )
        stroke.addPoint(snappedToActiveGuide(worldPoint), pressure, tilt, orientation)
        rememberActiveColor()
        lastStrokeMoveMillis = System.currentTimeMillis()
        lastPointMillis = lastStrokeMoveMillis
        currentStroke = stroke
        strokes.add(stroke)
        symmetryCopies = symmetryTransforms(symmetryMode, symmetryCenter).map { transform ->
            val copy = Stroke(stroke.color, stroke.widthWorld, stroke.penType, blendMode = stroke.blendMode, layerId = stroke.layerId, animFrame = stroke.animFrame, dashed = stroke.dashed, brush = stroke.brush)
            copy.addPoint(transform(stroke.points.first()), pressure, tilt, orientation)
            strokes.add(copy)
            copy to transform
        }
    }

    /** Active symmetry for new pen strokes; see [SymmetryMode]. */
    var symmetryMode by mutableStateOf(SymmetryMode.OFF)
        private set

    /** World point the symmetry axes pass through; set to the view centre when symmetry is turned on. */
    var symmetryCenter by mutableStateOf(WorldPoint.Zero)
        private set

    fun setSymmetry(mode: SymmetryMode) {
        if (mode != SymmetryMode.OFF && symmetryMode == SymmetryMode.OFF) symmetryCenter = panWorld
        symmetryMode = mode
    }

    // Mirrored/rotated copies of the stroke being drawn, each with the transform that produces it.
    private var symmetryCopies: List<Pair<Stroke, (WorldPoint) -> WorldPoint>> = emptyList()

    /** Adds a point to the current stroke and the same point, transformed, to each symmetry copy. */
    private fun addStrokePoint(stroke: Stroke, point: WorldPoint, pressure: Float, tilt: Float, orientation: Float) {
        stroke.addPoint(point, pressure, tilt, orientation)
        for ((copy, transform) in symmetryCopies) copy.addPoint(transform(point), pressure, tilt, orientation)
    }

    fun extendStroke(worldPoint: WorldPoint, pressure: Float = 1f, tilt: Float = 0f, orientation: Float = 0f) {
        draggingVanishingPoint?.let { index ->
            vanishingPoints[index] = worldPoint
            return
        }
        val stroke = currentStroke ?: return
        // Touchscreens report sub-pixel jitter even from a still finger; only real movement
        // (more than HOLD_JITTER_SCREEN px) restarts the hold timer for shape recognition.
        // Measured from where the timer last restarted, not the previous event, so slow
        // continuous drawing (under 2 px per event) still counts as moving.
        val anchor = holdAnchor
        if (anchor == null || hypot(worldPoint.x - anchor.x, worldPoint.y - anchor.y) * scale > HOLD_JITTER_SCREEN) {
            lastStrokeMoveMillis = System.currentTimeMillis()
            holdAnchor = worldPoint
        }
        val now = System.currentTimeMillis()
        val effectivePressure = speedPressure(stroke, worldPoint, pressure, now)
        lastPointMillis = now
        lastRawPoint = worldPoint
        val first = stroke.points.first()
        if (hypot(worldPoint.x - first.x, worldPoint.y - first.y) * scale > HOLD_JITTER_SCREEN * 2) strokeMovedAway = true
        val previous = stroke.points.lastOrNull()
        // Exponential smoothing: move only part of the way from the last point toward the pen.
        val smoothed = if (previous == null || activeStabilization <= 0.0) worldPoint
        else previous + (worldPoint - previous) * (1.0 - activeStabilization)
        decidePerspectiveDirection(stroke, smoothed)
        addStrokePoint(stroke, guided(smoothed), effectivePressure, tilt, orientation)
    }

    /** Active perspective guide; see [PerspectiveMode]. */
    var perspectiveMode by mutableStateOf(PerspectiveMode.OFF)
        private set

    /** The guide's vanishing points in world space, [PerspectiveMode.pointCount] of them while it's on. */
    val vanishingPoints = mutableStateListOf<WorldPoint>()

    /**
     * Turns the perspective guide on, off or to another mode. Vanishing points are placed fresh
     * around the view: one at its centre, or two on a horizon through the centre, 40% of the
     * view's width to each side - then dragged by their handles to fit the drawing.
     */
    fun setPerspective(mode: PerspectiveMode) {
        if (mode.pointCount != perspectiveMode.pointCount) {
            vanishingPoints.clear()
            val halfSpread = (viewportSize.width.takeIf { it > 0f } ?: DEFAULT_VIEW_WIDTH_SCREEN) * 0.4 / scale.coerceAtLeast(1e-300)
            when (mode.pointCount) {
                1 -> vanishingPoints.add(panWorld)
                2 -> {
                    vanishingPoints.add(WorldPoint(panWorld.x - halfSpread, panWorld.y))
                    vanishingPoints.add(WorldPoint(panWorld.x + halfSpread, panWorld.y))
                }
            }
        }
        perspectiveMode = mode
    }

    // Index into vanishingPoints being dragged by its handle, or null while drawing normally.
    private var draggingVanishingPoint: Int? = null

    // The perspective direction the current stroke was straightened onto, once decided.
    private var perspectiveDirection: WorldPoint? = null

    /**
     * Assisted perspective drawing: once a stroke has moved [PERSPECTIVE_DECIDE_SCREEN] px from
     * where it started, the perspective direction closest to that movement is chosen for the
     * rest of the stroke, and the points drawn so far are straightened onto it too. Off while
     * the ruler is active (the ruler wins: it's the more specific guide).
     */
    private fun decidePerspectiveDirection(stroke: Stroke, point: WorldPoint) {
        if (perspectiveMode == PerspectiveMode.OFF || rulerActive || perspectiveDirection != null) return
        val start = stroke.points.first()
        if (hypot(point.x - start.x, point.y - start.y) * scale < PERSPECTIVE_DECIDE_SCREEN) return
        val direction = closestDirection(perspectiveDirections(perspectiveMode, vanishingPoints, start), start, point) ?: return
        perspectiveDirection = direction
        val straightened = stroke.points.map { projectOntoLine(start, direction, it) }
        stroke.setPoints(straightened)
        for ((copy, transform) in symmetryCopies) copy.setPoints(straightened.map(transform))
    }

    /** [point] on the current stroke's perspective line when one was chosen, else on the ruler (if active). */
    private fun guided(point: WorldPoint): WorldPoint {
        val direction = perspectiveDirection
        val stroke = currentStroke
        if (direction != null && stroke != null) return projectOntoLine(stroke.points.first(), direction, point)
        return snappedToActiveGuide(point)
    }

    /**
     * Velocity dynamics: when on and the input has no real pressure (a finger reports 1.0),
     * fast movement thins the line and slow movement thickens it, like ink from a nib. The
     * result is stored as the point's pressure, so rendering, export and persistence need no
     * changes. Smoothed against the previous point so width changes gradually.
     */
    var velocityDynamics by mutableStateOf(false)

    private var lastPointMillis = 0L

    private fun speedPressure(stroke: Stroke, worldPoint: WorldPoint, pressure: Float, now: Long): Float {
        if (!velocityDynamics || pressure < 0.999f) return pressure
        val previous = stroke.points.lastOrNull() ?: return pressure
        val elapsed = (now - lastPointMillis).coerceAtLeast(1L)
        val speedScreenPerMs = hypot(worldPoint.x - previous.x, worldPoint.y - previous.y) * scale / elapsed
        val target = (1.0 - speedScreenPerMs * VELOCITY_THINNING).coerceIn(MIN_VELOCITY_PRESSURE, 1.0).toFloat()
        val last = stroke.pressures.lastOrNull() ?: 1f
        return last + (target - last) * VELOCITY_SMOOTHING
    }

    /** The last point of the pen stroke in progress, or null when not drawing (for the motion-prediction preview). */
    val strokeTip: WorldPoint? get() = currentStroke?.points?.lastOrNull()

    /** Style of the stroke in progress, for drawing its predicted extension the same way. */
    val strokeInProgress: Stroke? get() = currentStroke

    // For touch-and-hold eyedropper: history depth and time when the current stroke began.
    private var strokeStartDepth = 0
    private var strokeStartMillis = 0L
    private var strokeMovedAway = false

    // When the pen last moved; a pause before lifting triggers shape recognition in endStroke.
    private var lastStrokeMoveMillis = 0L
    private var holdAnchor: WorldPoint? = null

    // The unsmoothed pen position, so endStroke can finish the line where the pen actually lifted.
    private var lastRawPoint: WorldPoint? = null

    /** Swaps [stroke] for a clean shape with the same style; pressure is dropped, as with SHAPE-tool strokes. */
    private fun replaceWithShape(stroke: Stroke, cleanPoints: List<WorldPoint>) {
        val index = strokes.indexOf(stroke)
        if (index == -1) return
        val shape = Stroke(stroke.color, stroke.widthWorld, stroke.penType, blendMode = stroke.blendMode, layerId = stroke.layerId, animFrame = stroke.animFrame, dashed = stroke.dashed, brush = stroke.brush)
        for (point in cleanPoints) shape.addPoint(point)
        strokes[index] = shape
    }

    /**
     * Projects [point] onto the ruler guide's infinite line when it's active and placed,
     * otherwise returns [point] unchanged. Lets pen strokes drawn against the ruler come out
     * perfectly straight along it, the way tracing along a physical ruler's edge works.
     */
    private fun snappedToActiveGuide(point: WorldPoint): WorldPoint {
        if (!rulerActive) return point
        val (start, end) = rulerLine ?: return point
        val dx = end.x - start.x
        val dy = end.y - start.y
        val lengthSq = dx * dx + dy * dy
        if (lengthSq < 1e-12) return point
        val t = ((point.x - start.x) * dx + (point.y - start.y) * dy) / lengthSq
        return WorldPoint(start.x + t * dx, start.y + t * dy)
    }

    fun endStroke() {
        if (draggingVanishingPoint != null) {
            draggingVanishingPoint = null
            return
        }
        val stroke = currentStroke
        // Touch and hold without moving = eyedropper (the Procreate gesture): the stroke never
        // left the jitter radius around where it started, so it was a press, not a mark.
        // Its dot is discarded (without becoming redoable) and the colour under it picked.
        if (stroke != null && !strokeMovedAway &&
            System.currentTimeMillis() - strokeStartMillis >= SHAPE_HOLD_MILLIS
        ) {
            val pressedAt = stroke.points.first()
            lastRawPoint = null
            currentStroke = null
            discardActionsSince(strokeStartDepth)
            pickColorAt(pressedAt)
            return
        }
        val raw = lastRawPoint
        if (stroke != null && raw != null && activeStabilization > 0.0) {
            // Smoothing trails behind the pen; close the gap so the stroke ends at the lift point.
            addStrokePoint(stroke, guided(raw), stroke.pressures.last(), stroke.tilts.last(), stroke.orientations.last())
        }
        if (stroke != null && System.currentTimeMillis() - lastStrokeMoveMillis >= SHAPE_HOLD_MILLIS) {
            recognizeShape(stroke.points)?.let { clean ->
                replaceWithShape(stroke, clean)
                // Copies get the same clean shape, transformed, so symmetry still holds.
                for ((copy, transform) in symmetryCopies) replaceWithShape(copy, clean.map(transform))
            }
        }
        symmetryCopies = emptyList()
        lastRawPoint = null
        holdAnchor = null
        currentStroke = null
        rebaseIfNeeded()
        strokesChanged()
    }

    /** World-space radius of the eraser at the current zoom level. */
    private val eraserRadiusWorld: Double
        get() = ERASER_RADIUS_SCREEN / scale.coerceAtLeast(1e-300)

    fun beginErase(worldPoint: WorldPoint) {
        isErasing = true
        eraseStartDepth = undoStack.size
        recordHistory()
        clearSelectionState() // avoid a stale selection referencing strokes this erase is about to split/remove
        if (lassoErase) lassoPath.add(worldPoint) else eraseAt(worldPoint)
    }

    fun continueErase(worldPoint: WorldPoint) {
        if (lassoErase) {
            if (lassoPath.isNotEmpty()) lassoPath.add(worldPoint)
        } else {
            eraseAt(worldPoint)
        }
    }

    fun endErase() {
        isErasing = false
        if (lassoErase) {
            val lasso = lassoPath.toList()
            lassoPath.clear()
            // A tap or a line encloses nothing: drop the history entry instead of leaving an empty undo step.
            if (lasso.size < 3 || !eraseInsideLasso(lasso)) {
                discardActionsSince(eraseStartDepth)
                return
            }
        }
        strokesChanged()
        onImageChanged(imageItems.toList())
    }

    /**
     * Lasso eraser: instead of rubbing out where the eraser passes, the eraser gesture draws a
     * loop and everything inside it is erased when the finger lifts - ink cut at the loop's edge
     * in ink mode, whole strokes and images in object mode (see [eraseWholeObjects]).
     */
    var lassoErase by mutableStateOf(false)

    private var eraseStartDepth = 0

    /** Erases inside the closed loop [lasso]; returns whether anything was erased. */
    private fun eraseInsideLasso(lasso: List<WorldPoint>): Boolean {
        val minX = lasso.minOf { it.x }
        val minY = lasso.minOf { it.y }
        val maxX = lasso.maxOf { it.x }
        val maxY = lasso.maxOf { it.y }
        var changed = false
        if (eraseWholeObjects) {
            // An image goes when its centre is inside, so a loop drawn across a page's corner doesn't delete it.
            changed = imageItems.removeWhere { image ->
                polygonContains(lasso, WorldPoint(image.position.x + image.widthWorld / 2, image.position.y + image.heightWorld / 2))
            }
            changed = strokes.removeWhere { stroke ->
                isEditable(stroke) && stroke.intersects(minX, minY, maxX, maxY) &&
                    stroke.points.any { polygonContains(lasso, it) }
            } || changed
            return changed
        }
        for (stroke in strokes.toList()) {
            if (!isEditable(stroke) || !stroke.intersects(minX, minY, maxX, maxY)) continue
            val pieces = stroke.eraseInside(lasso) ?: continue
            val index = strokes.indexOf(stroke)
            if (index == -1) continue
            strokes.removeAt(index)
            strokes.addAll(index, pieces)
            changed = true
        }
        return changed
    }

    /**
     * Eraser mode: false (default) erases ink only, cutting strokes where the eraser passes
     * (see [Stroke.eraseNear]); true is an object eraser that removes each stroke or image it
     * touches whole, the note-taking-app convention.
     *
     * Images are only ever removed by the object eraser. The ink eraser used to delete any
     * image it passed over, so erasing a mistake written on an imported PDF page deleted the
     * page itself.
     */
    var eraseWholeObjects by mutableStateOf(false)

    private fun eraseAt(point: WorldPoint) {
        val radius = eraserRadiusWorld
        if (eraseWholeObjects) {
            imageItems.removeWhere { image ->
                point.x >= image.position.x && point.x <= image.position.x + image.widthWorld &&
                    point.y >= image.position.y && point.y <= image.position.y + image.heightWorld
            }
            // Only strokes near the eraser are tested (spatial index); the list is rewritten only
            // when something was actually hit.
            val hit = strokesNear(point.x - radius, point.y - radius, point.x + radius, point.y + radius).filterTo(
                java.util.Collections.newSetFromMap(java.util.IdentityHashMap()),
            ) { stroke ->
                isEditable(stroke) && stroke.intersects(point.x - radius, point.y - radius, point.x + radius, point.y + radius) && (
                    if (stroke.filled) polygonContains(stroke.points, point)
                    else distanceToPolyline(stroke.points, point) <= radius + stroke.widthWorld / 2
                )
            }
            if (hit.isNotEmpty()) strokes.removeWhere { it in hit }
            return
        }
        for (stroke in strokesNear(point.x - radius, point.y - radius, point.x + radius, point.y + radius)) {
            if (!isEditable(stroke)) continue
            if (!stroke.intersects(point.x - radius, point.y - radius, point.x + radius, point.y + radius)) continue
            val pieces = stroke.eraseNear(point, radius) ?: continue
            val index = strokes.indexOf(stroke)
            if (index == -1) continue
            strokes.removeAt(index)
            strokes.addAll(index, pieces)
        }
    }

    fun beginShape(worldPoint: WorldPoint) {
        if (!activeLayerEditable) return
        recordHistory()
        rememberActiveColor()
        clearSelectionState()
        shapeStart = snappedToObject(worldPoint) ?: snappedToGrid(worldPoint)
        shapePreview = shapeStart to shapeStart
    }

    /** Snap shape points to existing strokes' ends and corners (see [nearestSnapAnchor]); a user setting. */
    var snapToObjects by mutableStateOf(true)

    /** The anchor the shape being drawn is snapped to right now, shown as a ring; null when none. */
    var snapIndicator: WorldPoint? by mutableStateOf(null)
        private set

    private fun snappedToObject(point: WorldPoint): WorldPoint? {
        if (!snapToObjects) return null
        val radius = OBJECT_SNAP_RADIUS_SCREEN / scale.coerceAtLeast(1e-300)
        val near = strokesNear(point.x - radius, point.y - radius, point.x + radius, point.y + radius)
        val anchor = nearestSnapAnchor(near, point, radius, ::isVisible)
        snapIndicator = anchor
        return anchor
    }

    /**
     * On grid or dot-grid paper, shape corners and endpoints land on the nearest pattern point,
     * the way drawing on graph paper invites. Other paper styles leave points untouched.
     */
    private fun snappedToGrid(point: WorldPoint): WorldPoint {
        if (backgroundStyle != BackgroundStyle.GRID && backgroundStyle != BackgroundStyle.DOT) return point
        val spacing = BACKGROUND_PATTERN_SPACING_WORLD
        return WorldPoint(kotlin.math.round(point.x / spacing) * spacing, kotlin.math.round(point.y / spacing) * spacing)
    }

    fun continueShape(worldPoint: WorldPoint) {
        if (shapePreview == null) return
        // An object anchor wins (an exact join was asked for); then the grid, which already gives
        // clean geometry; angle/aspect snapping applies only when neither did.
        snappedToObject(worldPoint)?.let {
            shapePreview = shapeStart to it
            return
        }
        val end = snappedToGrid(worldPoint)
        shapePreview = shapeStart to if (end != worldPoint) end else snappedShapeEnd(activeShapeKind, shapeStart, worldPoint)
    }

    /** Commits the current shape preview as a new stroke. No-op if there was no actual drag (a zero-size shape). */
    fun endShape() {
        snapIndicator = null
        val preview = shapePreview ?: return
        shapePreview = null
        val (start, end) = preview
        if (start == end) return
        val stroke = Stroke(
            activeColor.copy(alpha = activeColor.alpha * activeOpacity.toFloat()),
            activeWidthWorld / scale.coerceAtLeast(1e-300),
            activePenType,
            blendMode = activeBlendMode,
            layerId = activeLayerId,
            animFrame = currentAnimFrame,
            dashed = activeDashed,
            lineStyle = activeLineStyle.takeIf { it != LineStyle() },
            brush = if (activePenType == PenType.STAMP) activeBrush else null,
        )
        for (point in generateShapePoints(activeShapeKind, start, end)) stroke.addPoint(point)
        strokes.add(stroke)
        strokesChanged()
    }

    /**
     * FILL tool: tapping inside a closed stroke fills its interior with [activeColor]. Since
     * this is a vector canvas (no pixel grid), "fill" means inserting a new filled-polygon
     * stroke using that boundary's exact points, positioned just beneath it in draw order so
     * the boundary's own outline still shows on top.
     *
     * Strokes whose first and last point are close together (a "closed enough" loop) are tried
     * first; among all that actually contain [worldPoint], the smallest by area wins, so
     * tapping inside a small shape drawn inside a larger one fills the small one, matching
     * real fill-tool expectations. Failing that, the area enclosed by several strokes is
     * found with [gapClosingFill] at screen resolution within the view, gaps up to about
     * twice [FILL_GAP_SCREEN] px closed, and filled with its traced outline. No-op (and no undo
     * step) if neither finds an enclosed area. With reference layers, only their strokes bound.
     */
    fun fillAt(worldPoint: WorldPoint) {
        if (!activeLayerEditable) return
        rememberActiveColor()
        clearSelectionState()
        // Boundaries come from reference layers when there are any (roadmap item 94).
        val referenceLayers = layers.filter { it.reference }.mapTo(HashSet()) { it.id }
        fun isWall(stroke: Stroke) = !stroke.filled && isVisible(stroke) && (referenceLayers.isEmpty() || stroke.layerId in referenceLayers)
        val closeTolerance = FILL_CLOSE_TOLERANCE_SCREEN / scale.coerceAtLeast(1e-300)
        var bestIndex = -1
        var bestArea = Double.POSITIVE_INFINITY
        for (index in strokes.indices) {
            val stroke = strokes[index]
            if (!isWall(stroke)) continue
            val points = stroke.points
            if (!isClosedLoop(points, closeTolerance)) continue
            if (!polygonContains(points, worldPoint)) continue
            val area = polygonArea(points)
            if (area < bestArea) {
                bestArea = area
                bestIndex = index
            }
        }
        val outline: List<WorldPoint>
        val insertAt: Int
        if (bestIndex != -1) {
            // Inside one closed stroke: fill exactly its outline.
            outline = strokes[bestIndex].points
            insertAt = bestIndex
        } else {
            // Otherwise an area closed by several strokes, small gaps included, within the view.
            val s = scale.coerceAtLeast(1e-300)
            val center = Offset(viewportSize.width / 2f, viewportSize.height / 2f)
            val corners = listOf(Offset.Zero, Offset(viewportSize.width, 0f), Offset(0f, viewportSize.height), Offset(viewportSize.width, viewportSize.height))
                .map { screenToWorld(it, center) }
            val left = corners.minOf { it.x }
            val top = corners.minOf { it.y }
            val right = corners.maxOf { it.x }
            val bottom = corners.maxOf { it.y }
            val walls = strokesNear(left, top, right, bottom).filter(::isWall)
            outline = gapClosingFill(walls, worldPoint, left, top, right, bottom, cell = 1 / s, gapWorld = FILL_GAP_SCREEN / s) ?: return
            // Beneath the ink around it.
            val wallSet = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<Stroke, Boolean>()).apply { addAll(walls) }
            insertAt = strokes.indexOfFirst { it in wallSet }.let { if (it == -1) strokes.size else it }
        }
        // Recorded only now, so a tap that fills nothing leaves no empty undo step.
        recordHistory()
        val fillStroke = Stroke(
            activeColor, 0.0, PenType.MARKER, filled = true, layerId = activeLayerId, animFrame = currentAnimFrame,
            fadeFill = activeFadeFill, screentone = activeScreentone, gradient = activeGradient,
        )
        for (point in outline) fillStroke.addPoint(point)
        strokes.add(insertAt, fillStroke)
        strokesChanged()
    }

    /**
     * TEXT tool, pointer-down: opens [pendingTextEdit] for the entry dialog to act on -
     * editing whatever label already sits at [worldPoint] (per [findTextItemAt]), or a fresh
     * one to be placed there.
     */
    /**
     * TEXT tool gesture: a tap places (or edits) a label where it lands; a drag past the tap slop
     * draws out a text box instead, whose width the new label's text wraps to - the text-box
     * convention of drawing and whiteboard apps. The dialog opens when the finger lifts.
     */
    fun beginTextGesture(worldPoint: WorldPoint) {
        textGestureStart = worldPoint
        textBoxDraft = null
    }

    fun continueTextGesture(worldPoint: WorldPoint) {
        val start = textGestureStart ?: return
        val movedScreen = hypot(worldPoint.x - start.x, worldPoint.y - start.y) * scale
        if (textBoxDraft != null || movedScreen > TAP_SLOP_SCREEN) textBoxDraft = start to worldPoint
    }

    fun endTextGesture() {
        val start = textGestureStart ?: return
        textGestureStart = null
        val box = textBoxDraft
        textBoxDraft = null
        if (box == null) {
            beginTextEdit(start)
            return
        }
        val (a, b) = box
        val topLeft = WorldPoint(minOf(a.x, b.x), minOf(a.y, b.y))
        pendingTextEdit = PendingTextEdit(null, topLeft, "", initialSizePx = activeTextSizeWorld, boxWidthWorld = abs(b.x - a.x))
    }

    /** Abandons a TEXT gesture that turned into something else (e.g. a second finger landed to pinch). */
    fun cancelTextGesture() {
        textGestureStart = null
        textBoxDraft = null
    }

    fun beginTextEdit(worldPoint: WorldPoint) {
        val existing = findTextItemAt(worldPoint)
        pendingTextEdit = if (existing != null) {
            PendingTextEdit(
                existing.id, existing.position, existing.text, existing.bold, existing.italic, existing.sticky,
                existing.fontSizeWorld * scale, existing.font, existing.alignment, existing.boxWidthWorld, existing.link,
                existing.outlined, existing.path,
            )
        } else {
            PendingTextEdit(null, worldPoint, "", initialSizePx = activeTextSizeWorld)
        }
    }

    /**
     * Applies the entry dialog's result: blank text deletes the label being edited (a no-op
     * if this was a fresh placement), otherwise creates a new label or updates the edited
     * one's text in place.
     */
    fun confirmTextEdit(
        text: String,
        bold: Boolean = false,
        italic: Boolean = false,
        sticky: Boolean = false,
        sizePx: Double = activeTextSizeWorld,
        font: TextFont = TextFont.SANS,
        alignment: TextAlignment = TextAlignment.START,
        /** False drops the pending box, so the label becomes a plain one without wrapping. */
        keepBox: Boolean = true,
        link: LinkTarget? = null,
        outlined: Boolean = false,
    ) {
        // The chosen size also becomes the default for the next new label.
        activeTextSizeWorld = sizePx
        val fontSizeWorld = sizePx / scale.coerceAtLeast(1e-300)
        val pending = pendingTextEdit ?: return
        pendingTextEdit = null
        recordHistory()
        if (text.isBlank()) {
            if (pending.id != null && textItems.removeWhere { it.id == pending.id }) {
                onTextChanged(textItems.toList())
            }
            return
        }
        val boxWidthWorld = if (keepBox) pending.boxWidthWorld else null
        val existingIndex = pending.id?.let { id -> textItems.indexOfFirst { it.id == id } } ?: -1
        if (existingIndex != -1) {
            textItems[existingIndex] = textItems[existingIndex].copy(
                text = text, bold = bold, italic = italic, sticky = sticky, fontSizeWorld = fontSizeWorld,
                font = font, alignment = alignment, boxWidthWorld = boxWidthWorld, link = link, outlined = outlined,
            )
        } else {
            textItems.add(
                TextItem(
                    id = UUID.randomUUID().toString(),
                    text = text,
                    position = pending.worldPoint,
                    fontSizeWorld = fontSizeWorld,
                    color = activeColor,
                    bold = bold,
                    italic = italic,
                    sticky = sticky,
                    font = font,
                    alignment = alignment,
                    boxWidthWorld = boxWidthWorld,
                    link = link,
                    outlined = outlined,
                    path = pending.path,
                )
            )
        }
        onTextChanged(textItems.toList())
    }

    /**
     * Text on a path (roadmap item 98): with one stroke selected, opens the text dialog for a
     * new label that follows that stroke's shape from its start. The stroke stays as drawn (a
     * guide to keep or delete).
     */
    fun beginTextOnPath() {
        val stroke = selection.singleOrNull() ?: return
        val points = stroke.points
        if (points.size < 2) return
        val start = points.first()
        clearSelectionState()
        pendingTextEdit = PendingTextEdit(null, start, "", initialSizePx = activeTextSizeWorld, path = points.map { it - start })
    }

    fun cancelTextEdit() {
        pendingTextEdit = null
    }

    /** Finds the topmost text label whose approximate bounding box contains [worldPoint], or null. */
    private fun findTextItemAt(worldPoint: WorldPoint): TextItem? {
        for (item in textItems.asReversed()) {
            val (min, max) = textBounds(item)
            if (worldPoint.x in min.x..max.x && worldPoint.y in min.y..max.y) return item
        }
        return null
    }

    /**
     * Approximate world-space box of a text label (average glyph width 0.55 em, line height
     * 1.3 em), for hit-testing and the selection highlight. Counts newlines, and for a boxed
     * label, the extra lines its wrapping adds.
     */
    fun textBounds(item: TextItem): Pair<WorldPoint, WorldPoint> {
        item.path?.takeIf { it.isNotEmpty() }?.let { path ->
            // Text on a path: around the path, a letter's height either side.
            val pad = item.fontSizeWorld
            return WorldPoint(item.position.x + path.minOf { it.x } - pad, item.position.y + path.minOf { it.y } - pad) to
                WorldPoint(item.position.x + path.maxOf { it.x } + pad, item.position.y + path.maxOf { it.y } + pad)
        }
        val glyph = item.fontSizeWorld * 0.55
        val lineWidths = item.text.split('\n').map { it.length * glyph }
        val box = item.boxWidthWorld
        val width = box ?: (lineWidths.maxOrNull() ?: 0.0)
        val lines = if (box == null || box <= 0.0) lineWidths.size
        else lineWidths.sumOf { maxOf(1, kotlin.math.ceil(it / box).toInt()) }
        return item.position to WorldPoint(item.position.x + width, item.position.y + item.fontSizeWorld * 1.3 * lines)
    }

    /** Replaces every text item with [loaded] (e.g. from [CanvasStorage.loadTextItems] on document load/switch). */
    fun loadTextItems(loaded: List<TextItem>) {
        textItems.clear()
        textItems.addAll(loaded)
    }

    /**
     * Places a newly-inserted image (already decoded and saved to disk by the caller - see
     * `MainActivity`'s photo-picker flow) at [position], sized to [widthWorld] x [heightWorld].
     */
    /** Places an image; undoable. Pass [recordUndo] = false for all but the first of a batch (e.g. PDF pages) so the batch undoes in one step. */
    fun addImage(fileName: String, position: WorldPoint, widthWorld: Double, heightWorld: Double, recordUndo: Boolean = true) {
        if (recordUndo) recordHistory()
        imageItems.add(ImageItem(UUID.randomUUID().toString(), fileName, position, widthWorld, heightWorld))
        onImageChanged(imageItems.toList())
    }

    /** Replaces every image item with [loaded] (e.g. from [CanvasStorage.loadImageItems] on document load/switch). */
    fun loadImageItems(loaded: List<ImageItem>) {
        imageItems.clear()
        imageItems.addAll(loaded)
    }

    /**
     * Toggles the ruler guide on/off. The first time it's turned on with no prior position, it
     * appears as a horizontal line through the current viewport center - after that, toggling
     * off and back on restores wherever it was left, matching a physical ruler you set aside
     * rather than one that resets each time you pick it up.
     */
    fun toggleRuler() {
        rulerActive = !rulerActive
        if (rulerActive && rulerLine == null) {
            val halfLength = RULER_DEFAULT_HALF_LENGTH_SCREEN / scale.coerceAtLeast(1e-300)
            rulerLine = WorldPoint(panWorld.x - halfLength, panWorld.y) to WorldPoint(panWorld.x + halfLength, panWorld.y)
        }
    }

    private val rulerHandleRadiusWorld: Double
        get() = RULER_HANDLE_RADIUS_SCREEN / scale.coerceAtLeast(1e-300)

    /**
     * RULER tool, pointer-down: grabs whichever endpoint [worldPoint] landed near (to resize/
     * re-angle the guide), or starts translating the whole line otherwise. No-op if the ruler
     * isn't placed.
     */
    fun beginRulerGesture(worldPoint: WorldPoint) {
        val (start, end) = rulerLine ?: return
        val handleRadius = rulerHandleRadiusWorld
        rulerDragMode = when {
            hypot(worldPoint.x - start.x, worldPoint.y - start.y) <= handleRadius -> RulerDragMode.START
            hypot(worldPoint.x - end.x, worldPoint.y - end.y) <= handleRadius -> RulerDragMode.END
            else -> RulerDragMode.TRANSLATE
        }
        rulerDragLast = worldPoint
    }

    fun continueRulerGesture(worldPoint: WorldPoint) {
        val (start, end) = rulerLine ?: return
        when (rulerDragMode) {
            RulerDragMode.START -> rulerLine = worldPoint to end
            RulerDragMode.END -> rulerLine = start to worldPoint
            RulerDragMode.TRANSLATE -> {
                val delta = worldPoint - rulerDragLast
                rulerLine = (start + delta) to (end + delta)
                rulerDragLast = worldPoint
            }
            RulerDragMode.NONE -> {}
        }
    }

    fun endRulerGesture() {
        rulerDragMode = RulerDragMode.NONE
    }

    /**
     * SELECT tool, pointer-down: starts moving the current selection if [worldPoint] falls
     * inside its combined bounds, otherwise starts a new marquee drag (replacing any
     * existing selection once the marquee is released).
     */
    /**
     * The image picked with the SELECT tool, or null. Images are picked by a tap (not a
     * marquee, so a marquee can still start on top of an imported PDF page to select the ink
     * on it); dragging inside the picked image moves it, and Delete removes it.
     */
    var selectedImageId by mutableStateOf<String?>(null)
        private set

    private var isMovingImage = false

    /** The text label picked with the SELECT tool (same tap/drag/delete model as images), or null. */
    var selectedTextId by mutableStateOf<String?>(null)
        private set

    private var isMovingText = false

    private fun imageContains(image: ImageItem, point: WorldPoint) =
        point.x >= image.position.x && point.x <= image.position.x + image.widthWorld &&
            point.y >= image.position.y && point.y <= image.position.y + image.heightWorld

    /**
     * Node editing: with exactly one stroke selected, its nodes ([editNodeIndices]) are shown as
     * handles and dragging one reshapes the stroke ([dragNode]); each drag is one undo step.
     * Ends when the selection changes.
     */
    var nodeEditing by mutableStateOf(false)
        private set

    /** The stroke whose nodes are being edited, or null when node editing isn't active. */
    val nodeEditStroke: Stroke? get() = if (nodeEditing) selection.singleOrNull() else null

    fun toggleNodeEditing() {
        nodeEditing = !nodeEditing && selection.size == 1
    }

    private var nodeDragIndex: Int? = null
    private var nodeDragOriginal: List<WorldPoint> = emptyList()
    private var nodeDragStart = WorldPoint.Zero

    fun beginSelectGesture(worldPoint: WorldPoint) {
        nodeEditStroke?.let { stroke ->
            val nodes = editNodeIndices(stroke.points.size)
            val hit = nodes.indices.minByOrNull { k ->
                val p = stroke.points[nodes[k]]
                hypot(p.x - worldPoint.x, p.y - worldPoint.y)
            }?.takeIf { k ->
                val p = stroke.points[nodes[k]]
                hypot(p.x - worldPoint.x, p.y - worldPoint.y) * scale <= NODE_HANDLE_SCREEN
            }
            if (hit != null) {
                recordHistory()
                nodeDragIndex = hit
                nodeDragOriginal = stroke.points.toList()
                nodeDragStart = worldPoint
                return
            }
        }
        val pickedText = textItems.firstOrNull { it.id == selectedTextId }
        if (pickedText != null && findTextItemAt(worldPoint)?.id == pickedText.id) {
            recordHistory()
            isMovingText = true
            lastDragPoint = worldPoint
            return
        }
        selectedTextId = null
        val pickedImage = imageItems.firstOrNull { it.id == selectedImageId }
        if (pickedImage != null && imageContains(pickedImage, worldPoint)) {
            recordHistory()
            isMovingImage = true
            lastDragPoint = worldPoint
            return
        }
        selectedImageId = null
        if (selection.isNotEmpty() && pointInsideSelectionBounds(worldPoint)) {
            // Recorded only for a move: selecting changes no strokes, so recording it too left
            // undo steps that appeared to do nothing.
            recordHistory()
            isMovingSelection = true
            lastDragPoint = worldPoint
            startSmartGuides(worldPoint)
        } else {
            isMovingSelection = false
            selection.clear()
            nodeEditing = false
            if (lassoSelect) lassoPath.add(worldPoint) else marqueeRect = worldPoint to worldPoint
        }
    }

    fun continueSelectGesture(worldPoint: WorldPoint) {
        val dragged = nodeDragIndex
        val editing = nodeEditStroke
        if (dragged != null && editing != null) {
            val nodes = editNodeIndices(nodeDragOriginal.size)
            editing.setPoints(dragNode(nodeDragOriginal, nodes, dragged, worldPoint - nodeDragStart))
            return
        }
        if (isMovingText) {
            val last = lastDragPoint ?: return
            val index = textItems.indexOfFirst { it.id == selectedTextId }
            if (index != -1) textItems[index] = textItems[index].copy(position = textItems[index].position + (worldPoint - last))
            lastDragPoint = worldPoint
            return
        }
        if (isMovingImage) {
            val last = lastDragPoint ?: return
            val index = imageItems.indexOfFirst { it.id == selectedImageId }
            if (index != -1) imageItems[index] = imageItems[index].copy(position = imageItems[index].position + (worldPoint - last))
            lastDragPoint = worldPoint
            return
        }
        if (isMovingSelection) {
            val wanted = snappedMove(worldPoint - moveStart)
            val delta = wanted - moveApplied
            for (stroke in selection) stroke.shiftBy(WorldPoint(-delta.x, -delta.y))
            moveApplied = wanted
            lastDragPoint = worldPoint
        } else if (lassoSelect) {
            if (lassoPath.isNotEmpty()) lassoPath.add(worldPoint)
        } else {
            val start = marqueeRect?.first ?: return
            marqueeRect = start to worldPoint
        }
    }

    fun endSelectGesture() {
        if (nodeDragIndex != null) {
            nodeDragIndex = null
            nodeDragOriginal = emptyList()
            strokesChanged()
            return
        }
        if (isMovingText) {
            isMovingText = false
            lastDragPoint = null
            onTextChanged(textItems.toList())
            return
        }
        if (isMovingImage) {
            isMovingImage = false
            lastDragPoint = null
            onImageChanged(imageItems.toList())
            return
        }
        if (isMovingSelection) {
            isMovingSelection = false
            lastDragPoint = null
            smartGuides = null to null
            moveStartBounds = null
            strokesChanged()
            return
        }
        if (lassoPath.isNotEmpty()) {
            val lasso = lassoPath.toList()
            lassoPath.clear()
            // A stroke is lassoed when at least half its points fall inside the (implicitly
            // closed) outline - tolerant of a stroke that pokes slightly out of a loose loop.
            if (lasso.size >= 3) {
                selection.addAll(strokes.filter { stroke ->
                    isEditable(stroke) && stroke.points.isNotEmpty() &&
                        stroke.points.count { polygonContains(lasso, it) } * 2 >= stroke.points.size
                })
                expandSelectionToGroups()
            }
            return
        }
        val rect = marqueeRect
        marqueeRect = null
        if (rect == null) return
        val (start, end) = rect
        val left = minOf(start.x, end.x)
        val right = maxOf(start.x, end.x)
        val top = minOf(start.y, end.y)
        val bottom = maxOf(start.y, end.y)
        selection.addAll(strokes.filter { isEditable(it) && it.intersects(left, top, right, bottom) })
        expandSelectionToGroups()
        // A tap (a marquee that never grew past a few pixels) that caught no strokes picks the
        // topmost image under it instead.
        val tapSlopWorld = TAP_SLOP_SCREEN / scale.coerceAtLeast(1e-300)
        if (selection.isEmpty() && right - left <= tapSlopWorld && bottom - top <= tapSlopWorld) {
            // Text is drawn above images, so it wins when both are under the tap.
            selectedTextId = findTextItemAt(start)?.id
            if (selectedTextId == null) selectedImageId = imageItems.lastOrNull { imageContains(it, start) }?.id
        }
    }

    /** The selection's area, padded by the widest selected stroke, as an export region; null if nothing is selected. */
    fun selectionExportRegion(): Frame? {
        val (min, max) = selectionBounds() ?: return null
        val pad = selection.maxOf { it.widthWorld }.coerceAtLeast(1e-9)
        return Frame("selection", "Selection", WorldPoint(min.x - pad, min.y - pad), WorldPoint(max.x + pad, max.y + pad))
    }

    /** The current selection's combined bounding box, or null if nothing is selected. */
    fun selectionBounds(): Pair<WorldPoint, WorldPoint>? {
        if (selection.isEmpty()) return null
        val left = selection.minOf { it.minX }
        val right = selection.maxOf { it.maxX }
        val top = selection.minOf { it.minY }
        val bottom = selection.maxOf { it.maxY }
        return WorldPoint(left, top) to WorldPoint(right, bottom)
    }

    /**
     * Starts a uniform scale of the selection, anchored at the bounding box's top-left
     * corner (the corner opposite the bottom-right handle the caller hit-tested against) -
     * so dragging that handle resizes the selection the way dragging an image's corner
     * handle does, rather than scaling in place and drifting the selection's position.
     * No-op if nothing is selected.
     */
    fun beginScaleSelection(worldPoint: WorldPoint) {
        val bounds = selectionBounds() ?: return
        recordHistory()
        gestureAnchor = bounds.first
        gestureStartDistance = hypot(worldPoint.x - gestureAnchor.x, worldPoint.y - gestureAnchor.y).coerceAtLeast(1e-9)
        gestureSnapshot = selection.associateWith { it.points.toList() }
        gestureSnapshotWidths = selection.associateWith { it.widthWorld }
    }

    fun continueScaleSelection(worldPoint: WorldPoint) {
        if (gestureSnapshot.isEmpty()) return
        val distance = hypot(worldPoint.x - gestureAnchor.x, worldPoint.y - gestureAnchor.y).coerceAtLeast(1e-9)
        val factor = distance / gestureStartDistance
        for ((stroke, originalPoints) in gestureSnapshot) {
            stroke.setPoints(
                originalPoints.map { point ->
                    WorldPoint(
                        gestureAnchor.x + (point.x - gestureAnchor.x) * factor,
                        gestureAnchor.y + (point.y - gestureAnchor.y) * factor,
                    )
                }
            )
            gestureSnapshotWidths[stroke]?.let { stroke.setWidth(it * factor) }
        }
    }

    fun endScaleSelection() {
        gestureSnapshot = emptyMap()
        gestureSnapshotWidths = emptyMap()
        strokesChanged()
    }

    /**
     * Starts rotating the selection around its bounding box's center. No-op if nothing is
     * selected.
     */
    private fun startSmartGuides(worldPoint: WorldPoint) {
        moveStart = worldPoint
        moveApplied = WorldPoint.Zero
        moveStartBounds = selectionBounds()
        if (!snapToObjects || viewportSize.width <= 0f) {
            guideTargetsX = DoubleArray(0)
            guideTargetsY = DoubleArray(0)
            return
        }
        // Edges and centres of the other visible strokes in view.
        val center = Offset(viewportSize.width / 2f, viewportSize.height / 2f)
        val corners = listOf(Offset.Zero, Offset(viewportSize.width, viewportSize.height), Offset(viewportSize.width, 0f), Offset(0f, viewportSize.height))
            .map { screenToWorld(it, center) }
        val chosen = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<Stroke, Boolean>()).apply { addAll(selection) }
        val others = strokesNear(corners.minOf { it.x }, corners.minOf { it.y }, corners.maxOf { it.x }, corners.maxOf { it.y })
            .filter { it !in chosen && isVisible(it) && it.points.isNotEmpty() }
            .take(MAX_GUIDE_OBJECTS)
        guideTargetsX = others.flatMap { listOf(it.minX, (it.minX + it.maxX) / 2, it.maxX) }.toDoubleArray()
        guideTargetsY = others.flatMap { listOf(it.minY, (it.minY + it.maxY) / 2, it.maxY) }.toDoubleArray()
    }

    /**
     * [delta] (the drag so far) nudged so the selection's left, centre or right lines up with
     * another object's, and likewise top, middle or bottom, when within [GUIDE_SNAP_SCREEN] px;
     * sets [smartGuides] to the lines it snapped to.
     */
    private fun snappedMove(delta: WorldPoint): WorldPoint {
        val (min, max) = moveStartBounds ?: return delta
        val reach = GUIDE_SNAP_SCREEN / scale.coerceAtLeast(1e-300)
        fun snap(edges: DoubleArray, targets: DoubleArray): Pair<Double, Double?> {
            var best = 0.0
            var bestLine: Double? = null
            var bestDistance = reach
            for (edge in edges) for (target in targets) {
                val d = abs(target - edge)
                if (d <= bestDistance) {
                    bestDistance = d
                    best = target - edge
                    bestLine = target
                }
            }
            return best to bestLine
        }
        val (dx, lineX) = snap(doubleArrayOf(min.x + delta.x, (min.x + max.x) / 2 + delta.x, max.x + delta.x), guideTargetsX)
        val (dy, lineY) = snap(doubleArrayOf(min.y + delta.y, (min.y + max.y) / 2 + delta.y, max.y + delta.y), guideTargetsY)
        smartGuides = lineX to lineY
        return WorldPoint(delta.x + dx, delta.y + dy)
    }

    /**
     * Numeric transform (roadmap item 99): resizes the selection's bounds to [width] x [height]
     * (world units; widths of strokes scale by the mean of the two factors) with its top-left at
     * ([left], [top]), then turns it by [rotationDegrees] about its new centre. One undo step.
     */
    fun transformSelection(left: Double, top: Double, width: Double, height: Double, rotationDegrees: Double) {
        val (min, max) = selectionBounds() ?: return
        if (!(width > 0 && height > 0)) return
        recordHistory()
        val sx = width / (max.x - min.x).coerceAtLeast(1e-9)
        val sy = height / (max.y - min.y).coerceAtLeast(1e-9)
        val center = WorldPoint(left + width / 2, top + height / 2)
        val angle = Math.toRadians(rotationDegrees)
        for (stroke in selection) {
            stroke.setPoints(stroke.points.map { p -> rotateAbout(WorldPoint(left + (p.x - min.x) * sx, top + (p.y - min.y) * sy), center, angle) })
            stroke.setWidth(stroke.widthWorld * (sx + sy) / 2)
        }
        strokesChanged()
    }

    fun beginRotateSelection(worldPoint: WorldPoint) {
        val bounds = selectionBounds() ?: return
        recordHistory()
        val (min, max) = bounds
        gestureAnchor = WorldPoint((min.x + max.x) / 2.0, (min.y + max.y) / 2.0)
        gestureStartAngle = atan2(worldPoint.y - gestureAnchor.y, worldPoint.x - gestureAnchor.x)
        gestureSnapshot = selection.associateWith { it.points.toList() }
    }

    fun continueRotateSelection(worldPoint: WorldPoint) {
        if (gestureSnapshot.isEmpty()) return
        val currentAngle = atan2(worldPoint.y - gestureAnchor.y, worldPoint.x - gestureAnchor.x)
        val delta = currentAngle - gestureStartAngle
        val cosDelta = cos(delta)
        val sinDelta = sin(delta)
        for ((stroke, originalPoints) in gestureSnapshot) {
            stroke.setPoints(
                originalPoints.map { point ->
                    val dx = point.x - gestureAnchor.x
                    val dy = point.y - gestureAnchor.y
                    WorldPoint(
                        gestureAnchor.x + dx * cosDelta - dy * sinDelta,
                        gestureAnchor.y + dx * sinDelta + dy * cosDelta,
                    )
                }
            )
        }
    }

    fun endRotateSelection() {
        gestureSnapshot = emptyMap()
        strokesChanged()
    }

    /** Deletes every currently selected stroke. */
    /** Redraws every selected stroke in the current colour and opacity; the selection stays on the recoloured strokes. */
    fun recolorSelection() {
        if (selection.isEmpty()) return
        recordHistory()
        val newColor = activeColor.copy(alpha = activeColor.alpha * activeOpacity.toFloat())
        val replaced = selection.toList().map { old ->
            val recolored = old.withColor(newColor)
            val index = strokes.indexOf(old)
            if (index != -1) strokes[index] = recolored
            recolored
        }
        clearSelectionState()
        selection.addAll(replaced)
        rememberActiveColor()
        strokesChanged()
    }

    /** Moves the selected strokes to the top ([toFront]) or bottom of the drawing order, keeping their order relative to each other. */
    fun reorderSelection(toFront: Boolean) {
        if (selection.isEmpty()) return
        recordHistory()
        val selected = selection.toSet()
        val moving = strokes.filter { it in selected }
        strokes.removeAll(selected)
        if (toFront) strokes.addAll(moving) else strokes.addAll(0, moving)
        strokesChanged()
    }

    /** Adds every other editable member of each group a selected stroke belongs to: a group is picked whole. */
    private fun expandSelectionToGroups() {
        val groups = selection.mapNotNullTo(HashSet()) { it.groupId }
        if (groups.isEmpty()) return
        val already = selection.toHashSet()
        selection.addAll(strokes.filter { it.groupId in groups && it !in already && isEditable(it) })
    }

    /** True if any selected stroke is in a group, so the toolbar offers Ungroup instead of Group. */
    val selectionHasGroup: Boolean get() = selection.any { it.groupId != null }

    /** Puts the selected strokes (two or more) into one new group, replacing any groups they were in. */
    fun groupSelection() {
        if (selection.size < 2) return
        regroupSelection(UUID.randomUUID().toString())
    }

    /** Takes the selected strokes out of their groups. */
    fun ungroupSelection() {
        if (!selectionHasGroup) return
        regroupSelection(null)
    }

    private fun regroupSelection(groupId: String?) {
        recordHistory()
        val replaced = selection.map { stroke ->
            val copy = stroke.inGroup(groupId)
            val index = strokes.indexOf(stroke)
            if (index != -1) strokes[index] = copy
            copy
        }
        selection.clear()
        selection.addAll(replaced)
        strokesChanged()
    }

    /**
     * Copies with their group ids replaced by fresh ones (consistently, so a copied group is still
     * one group): a duplicate or a paste is a new group, not more members of the original.
     */
    private fun withFreshGroups(copies: List<Stroke>): List<Stroke> {
        val fresh = HashMap<String, String>()
        return copies.map { stroke ->
            val old = stroke.groupId ?: return@map stroke
            stroke.inGroup(fresh.getOrPut(old) { UUID.randomUUID().toString() })
        }
    }

    /**
     * Independent copies of the selected strokes, for the system clipboard ([SystemClipboard]),
     * so later edits don't change what gets pasted. Empty when nothing is selected.
     */
    fun selectionForClipboard(): List<Stroke> = selection.map { it.deepCopy() }

    /** Pastes [text] as a new label centred on [at] (the view centre by default), in the active colour and text size. */
    fun pasteText(text: String, at: WorldPoint = panWorld) {
        if (text.isBlank()) return
        recordHistory()
        val fontSizeWorld = activeTextSizeWorld / scale.coerceAtLeast(1e-300)
        val item = TextItem(UUID.randomUUID().toString(), text, at, fontSizeWorld, activeColor)
        val (min, max) = textBounds(item)
        // Centre the label's approximate box on the point.
        textItems.add(item.copy(position = WorldPoint(at.x - (max.x - min.x) / 2, at.y - (max.y - min.y) / 2)))
        onTextChanged(textItems.toList())
    }

    /** Pastes [copied] strokes centred on [at] (the view centre by default), on the active layer and frame, and selects them. */
    fun pasteStrokes(copied: List<Stroke>, at: WorldPoint = panWorld) {
        if (copied.isEmpty() || !activeLayerEditable) return
        recordHistory()
        // Strokes from another document may name a layer or frame this one doesn't have.
        val pasted = withFreshGroups(copied.map { it.onLayer(activeLayerId).onAnimFrame(currentAnimFrame) })
        val centerX = (pasted.minOf { it.minX } + pasted.maxOf { it.maxX }) / 2.0
        val centerY = (pasted.minOf { it.minY } + pasted.maxOf { it.maxY }) / 2.0
        // shiftBy subtracts, so shifting by (old centre - target) lands it on the target.
        val offset = WorldPoint(centerX - at.x, centerY - at.y)
        pasted.forEach { it.shiftBy(offset) }
        strokes.addAll(pasted)
        clearSelectionState()
        selection.addAll(pasted)
        strokesChanged()
    }

    /**
     * Aligns each selected stroke's bounding box to the selection's combined bounds, or spaces
     * their centres evenly between the outermost two (distribute; needs at least 3 strokes).
     */
    fun alignSelection(alignment: StrokeAlignment) {
        if (selection.size < 2) return
        if ((alignment == StrokeAlignment.DISTRIBUTE_X || alignment == StrokeAlignment.DISTRIBUTE_Y) && selection.size < 3) return
        val (min, max) = selectionBounds() ?: return
        recordHistory()
        fun centerX(s: Stroke) = (s.minX + s.maxX) / 2.0
        fun centerY(s: Stroke) = (s.minY + s.maxY) / 2.0
        when (alignment) {
            StrokeAlignment.DISTRIBUTE_X, StrokeAlignment.DISTRIBUTE_Y -> {
                val horizontal = alignment == StrokeAlignment.DISTRIBUTE_X
                val ordered = selection.sortedBy { if (horizontal) centerX(it) else centerY(it) }
                val first = if (horizontal) centerX(ordered.first()) else centerY(ordered.first())
                val last = if (horizontal) centerX(ordered.last()) else centerY(ordered.last())
                val step = (last - first) / (ordered.size - 1)
                ordered.forEachIndexed { i, s ->
                    val delta = first + step * i - (if (horizontal) centerX(s) else centerY(s))
                    // shiftBy subtracts its offset.
                    s.shiftBy(if (horizontal) WorldPoint(-delta, 0.0) else WorldPoint(0.0, -delta))
                }
            }
            else -> for (s in selection) {
                val dx = when (alignment) {
                    StrokeAlignment.LEFT -> min.x - s.minX
                    StrokeAlignment.CENTER_X -> (min.x + max.x) / 2.0 - centerX(s)
                    StrokeAlignment.RIGHT -> max.x - s.maxX
                    else -> 0.0
                }
                val dy = when (alignment) {
                    StrokeAlignment.TOP -> min.y - s.minY
                    StrokeAlignment.CENTER_Y -> (min.y + max.y) / 2.0 - centerY(s)
                    StrokeAlignment.BOTTOM -> max.y - s.maxY
                    else -> 0.0
                }
                s.shiftBy(WorldPoint(-dx, -dy))
            }
        }
        strokesChanged()
    }

    /** Sets every selected stroke's width to the current brush width (as it would draw at this zoom). Fills have no width. */
    fun applyWidthToSelection() {
        if (selection.isEmpty()) return
        recordHistory()
        val width = activeWidthWorld / scale.coerceAtLeast(1e-300)
        for (stroke in selection) if (!stroke.filled) stroke.setWidth(width)
        strokesChanged()
    }

    /** Selects every editable stroke on the active layer (in the current animation frame). */
    fun selectAllOnActiveLayer() {
        clearSelectionState()
        selection.addAll(strokes.filter { it.layerId == activeLayerId && isEditable(it) })
    }

    /**
     * Selects every editable stroke with the same colour as the first selected stroke, or as
     * the current drawing colour when nothing is selected. Compared without alpha, so the
     * opacity slider's effect doesn't split one colour into many.
     */
    fun selectSameColor() {
        val target = (selection.firstOrNull()?.color ?: activeColor).copy(alpha = 1f)
        clearSelectionState()
        selection.addAll(strokes.filter { isEditable(it) && it.color.copy(alpha = 1f) == target })
    }

    /**
     * Straightens each selected stroke into a line between its endpoints, keeping its point
     * count (points are spread evenly along the line) so pressure/tilt stay paired. Fills and
     * closed loops (endpoints together) are left alone, since a line between them is nothing.
     */
    fun straightenSelection() {
        if (selection.isEmpty()) return
        recordHistory()
        for (stroke in selection) {
            val points = stroke.points
            if (stroke.filled || points.size < 2) continue
            val first = points.first()
            val last = points.last()
            if (hypot(last.x - first.x, last.y - first.y) < stroke.widthWorld) continue
            stroke.setPoints(points.indices.map { i ->
                val t = i.toDouble() / (points.size - 1)
                WorldPoint(first.x + (last.x - first.x) * t, first.y + (last.y - first.y) * t)
            })
        }
        strokesChanged()
    }

    /** Smooths the selected strokes' wobble after the fact; repeatable for a stronger effect. Filled shapes are left alone. */
    fun smoothSelection() {
        if (selection.isEmpty()) return
        recordHistory()
        for (stroke in selection) {
            if (!stroke.filled) stroke.setPoints(smoothPoints(stroke.points, SMOOTH_PASSES))
        }
        strokesChanged()
    }

    /**
     * Boolean operation on the selected closed shapes (roadmap item 96): union, intersect,
     * subtract (the bottom shape minus the others) or exclude, with [combineShapes]. The result
     * takes the bottom shape's style and place in the drawing order, replacing the selection;
     * a filled result keeps its holes empty, an outlined one draws them as outlines. Needs two or
     * more strokes, each closed; does nothing (and records no undo step) otherwise, or if
     * nothing would be left.
     */
    fun combineSelection(op: ShapeOp) {
        val closeTolerance = FILL_CLOSE_TOLERANCE_SCREEN / scale.coerceAtLeast(1e-300)
        val chosen = selection.toList()
        if (chosen.size < 2 || chosen.any { !isClosedLoop(it.points, closeTolerance) }) return
        val ordered = chosen.sortedBy { strokes.indexOf(it) }
        val bottom = ordered.first()
        val loops = combineShapes(ordered.map { it.points }, op, bridgeHoles = bottom.filled)
        if (loops.isEmpty()) return
        recordHistory()
        val at = strokes.indexOf(bottom)
        val results = loops.map { loop ->
            Stroke(
                bottom.color, bottom.widthWorld, bottom.penType, bottom.filled, bottom.blendMode, bottom.layerId, bottom.animFrame,
                bottom.dashed, bottom.fadeFill, bottom.screentone, bottom.brush, bottom.gradient, bottom.groupId, bottom.boneId, bottom.lineStyle,
            ).apply {
                for (point in loop) addPoint(point)
                addPoint(loop.first()) // closed, like the shapes it came from
            }
        }
        val removed = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<Stroke, Boolean>()).apply { addAll(chosen) }
        strokes.removeWhere { it in removed }
        strokes.addAll(at.coerceAtMost(strokes.size), results)
        clearSelectionState()
        selection.addAll(results)
        strokesChanged()
    }

    /**
     * Adds an image's traced shapes (roadmap item 101) as filled strokes in the active colour,
     * laid over the image they came from: [outlines] are in the traced pixel grid of
     * [pixelWidth] x [pixelHeight], stretched onto [image]'s place. They're selected, ready to
     * move or recolour; the image stays (delete it to keep only the vector). One undo step.
     */
    fun addTracedImage(image: ImageItem, outlines: List<List<WorldPoint>>, pixelWidth: Int, pixelHeight: Int) {
        if (outlines.isEmpty() || !activeLayerEditable || pixelWidth <= 0 || pixelHeight <= 0) return
        recordHistory()
        val sx = image.widthWorld / pixelWidth
        val sy = image.heightWorld / pixelHeight
        val traced = outlines.map { loop ->
            Stroke(activeColor, 0.0, PenType.MARKER, filled = true, layerId = activeLayerId, animFrame = currentAnimFrame).apply {
                for (p in loop) addPoint(WorldPoint(image.position.x + p.x * sx, image.position.y + p.y * sy))
            }
        }
        strokes.addAll(traced)
        clearSelectionState()
        selection.addAll(traced)
        strokesChanged()
    }

    /**
     * Places a drawing script's lines (roadmap items 111 and 164): one script unit is one screen
     * pixel at the current zoom, so a script draws at the size it reads; centred in the view,
     * on the active layer and frame, selected. One undo step.
     */
    fun insertScriptLines(lines: List<ScriptLine>) {
        if (lines.isEmpty()) return
        val s = scale.coerceAtLeast(1e-300)
        val drawn = lines.map { line ->
            Stroke(line.color, line.width / s, PenType.MARKER, layerId = activeLayerId, animFrame = currentAnimFrame).apply {
                for (p in line.points) addPoint(WorldPoint(p.x / s, p.y / s))
            }
        }
        activeTool = Tool.SELECT
        pasteStrokes(drawn)
    }

    fun deleteSelection() {
        val textId = selectedTextId
        if (textId != null) {
            recordHistory()
            textItems.removeWhere { it.id == textId }
            selectedTextId = null
            onTextChanged(textItems.toList())
            return
        }
        val imageId = selectedImageId
        if (imageId != null) {
            recordHistory()
            imageItems.removeWhere { it.id == imageId }
            selectedImageId = null
            onImageChanged(imageItems.toList())
            return
        }
        if (selection.isEmpty()) return
        recordHistory()
        strokes.removeAll(selection.toSet())
        selection.clear()
        strokesChanged()
    }

    /** Duplicates every currently selected stroke, offset slightly so the copy is visible, and selects the copies. */
    fun duplicateSelection() {
        if (selection.isEmpty()) return
        recordHistory()
        val nudge = WorldPoint(20.0 / scale.coerceAtLeast(1e-300), 20.0 / scale.coerceAtLeast(1e-300))
        // deepCopy keeps every attribute (stylus data, stamp brush, fill gradient...); shiftBy
        // subtracts, so shifting by -nudge moves the copy down-right.
        val duplicates = withFreshGroups(selection.map { original -> original.deepCopy().also { it.shiftBy(WorldPoint(-nudge.x, -nudge.y)) } })
        strokes.addAll(duplicates)
        selection.clear()
        selection.addAll(duplicates)
        strokesChanged()
    }

    /** Clears the selection and any in-progress scale/rotate snapshot together, so neither can outlive the strokes it references. */
    private fun clearSelectionState() {
        selection.clear()
        nodeEditing = false
        selectedImageId = null
        selectedTextId = null
        gestureSnapshot = emptyMap()
        gestureSnapshotWidths = emptyMap()
    }

    private fun pointInsideSelectionBounds(point: WorldPoint): Boolean {
        val (min, max) = selectionBounds() ?: return false
        return point.x in min.x..max.x && point.y in min.y..max.y
    }

    /** Number of undoable steps; with [discardActionsSince], lets a gesture take back what it started. */
    val historyDepth: Int get() = undoStack.size

    /**
     * Reverts every action recorded since [depth] without making it redoable - for input that
     * turned out not to be drawing (e.g. the first finger of a two-finger tap briefly began a
     * stroke). Unlike [undo], nothing is pushed onto the redo stack.
     */
    fun discardActionsSince(depth: Int) {
        if (undoStack.size <= depth) return
        var snapshot: HistoryEntry? = null
        while (undoStack.size > depth) snapshot = undoStack.removeLast()
        clearSelectionState()
        currentStroke = null
        symmetryCopies = emptyList()
        snapshot?.let { restore(it) }
        strokesChanged()
    }

    fun undo() {
        val previous = undoStack.removeLastOrNull() ?: return
        clearSelectionState()
        redoStack.addLast(currentEntry())
        restore(previous)
        strokesChanged()
    }

    fun redo() {
        val next = redoStack.removeLastOrNull() ?: return
        clearSelectionState()
        undoStack.addLast(currentEntry())
        restore(next)
        strokesChanged()
    }

    /** Replaces the strokes with a saved version; undoable like any other action. */
    fun restoreVersion(version: List<Stroke>) {
        recordHistory()
        clearSelectionState()
        restore(version)
        strokesChanged()
    }

    /** Replaces all strokes with [loaded] (e.g. from [CanvasStorage.load] on app start). */
    fun loadStrokes(loaded: List<Stroke>) {
        // Frame count isn't stored separately: it's one past the highest frame any stroke is on
        // (so trailing empty frames aren't kept across sessions).
        animFrameCount = (loaded.maxOfOrNull { it.animFrame } ?: 0) + 1
        currentAnimFrame = 0
        playing = false
        strokes.clear()
        undoStack.clear()
        redoStack.clear()
        clearSelectionState()
        strokes.addAll(loaded)
    }

    fun clear() {
        recordHistory() // records strokes, images and text, so Clear is fully undoable
        strokes.clear()
        clearSelectionState()
        strokesChanged()
        textItems.clear()
        onTextChanged(textItems.toList())
        imageItems.clear()
        onImageChanged(imageItems.toList())
        if (bones.isNotEmpty()) {
            bones.clear()
            selectedBoneId = null
            onBonesChanged(bones.toList())
        }
    }

    /** Pan by a screen-space delta (drag). Only call while no stroke is in progress. */
    fun panByScreenDelta(delta: Offset) {
        panWorld -= WorldPoint(delta.x.toDouble(), delta.y.toDouble()) / scale
        rebaseIfNeeded()
    }

    /**
     * Zoom by [factor] around the world point currently under [screenFocus].
     * Only call while no stroke is in progress.
     */
    fun zoomAround(screenFocus: Offset, screenCenter: Offset, factor: Double) {
        val worldFocusBefore = screenToWorld(screenFocus, screenCenter)
        scale = (scale * factor).coerceIn(MIN_SCALE, MAX_SCALE)
        val worldFocusAfter = screenToWorld(screenFocus, screenCenter)
        panWorld += (worldFocusBefore - worldFocusAfter)
        rebaseIfNeeded()
    }

    fun resetView() {
        panWorld = WorldPoint.Zero
        scale = 1.0
        rotation = 0.0
    }

    /**
     * Canvas rotation in radians, applied as a whole-scene rotation about the screen centre at
     * draw time. [worldToScreen]/[screenToWorld] deliberately stay unrotated ("view space"), so
     * every renderer and hit-test keeps working unchanged; only raw touch positions need
     * [unrotateScreen] before use.
     */
    var rotation by mutableDoubleStateOf(0.0)
        private set

    /** Maps a raw on-screen position into unrotated view space by turning it back about [screenCenter]. */
    fun unrotateScreen(screen: Offset, screenCenter: Offset): Offset {
        if (rotation == 0.0) return screen
        val dx = (screen.x - screenCenter.x).toDouble()
        val dy = (screen.y - screenCenter.y).toDouble()
        val c = cos(-rotation)
        val s = sin(-rotation)
        return Offset(
            x = screenCenter.x + (dx * c - dy * s).toFloat(),
            y = screenCenter.y + (dx * s + dy * c).toFloat(),
        )
    }

    /** Rotates by [deltaRadians] keeping the content under the raw screen point [screenFocus] in place. */
    fun rotateAround(screenFocus: Offset, screenCenter: Offset, deltaRadians: Double) {
        if (deltaRadians == 0.0) return
        val worldFocusBefore = screenToWorld(unrotateScreen(screenFocus, screenCenter), screenCenter)
        rotation = normalizedAngle(rotation + deltaRadians)
        val worldFocusAfter = screenToWorld(unrotateScreen(screenFocus, screenCenter), screenCenter)
        panWorld += (worldFocusBefore - worldFocusAfter)
    }

    /** At gesture end, snaps to upright when within [ROTATION_SNAP_DEGREES], so pinch-zooming doesn't leave a slight accidental tilt. */
    fun snapRotation() {
        if (abs(Math.toDegrees(rotation)) < ROTATION_SNAP_DEGREES) rotation = 0.0
    }

    private fun normalizedAngle(angle: Double): Double {
        var a = angle
        while (a > PI) a -= 2 * PI
        while (a < -PI) a += 2 * PI
        return a
    }

    /** The combined bounding box of every stroke, or null if the canvas is empty. */
    fun contentBounds(): Pair<WorldPoint, WorldPoint>? {
        // Images and text count too: a document holding only an imported PDF or only typed
        // notes used to have no bounds, so zoom-to-fit and the minimap did nothing.
        val boxes = strokes.filter { it.points.isNotEmpty() }.map { WorldPoint(it.minX, it.minY) to WorldPoint(it.maxX, it.maxY) } +
            imageItems.map { it.position to WorldPoint(it.position.x + it.widthWorld, it.position.y + it.heightWorld) } +
            textItems.map { textBounds(it) }
        if (boxes.isEmpty()) return null
        return WorldPoint(boxes.minOf { it.first.x }, boxes.minOf { it.first.y }) to
            WorldPoint(boxes.maxOf { it.second.x }, boxes.maxOf { it.second.y })
    }

    /** Recenters the camera on [world] without changing zoom. */
    fun panTo(world: WorldPoint) {
        panWorld = world
        rebaseIfNeeded()
    }

    /** Jumps the camera to [world] at [targetScale] (e.g. restoring a saved pin). */
    fun jumpTo(world: WorldPoint, targetScale: Double) {
        scale = targetScale.coerceIn(MIN_SCALE, MAX_SCALE)
        panWorld = world
        rebaseIfNeeded()
    }

    /** Frames all content in the current viewport, with a margin. No-op on an empty canvas. */
    fun zoomToFit() {
        val bounds = contentBounds() ?: return
        val viewport = viewportSize
        if (viewport.width <= 0f || viewport.height <= 0f) return
        val (min, max) = bounds
        val contentWidth = (max.x - min.x).coerceAtLeast(1e-6)
        val contentHeight = (max.y - min.y).coerceAtLeast(1e-6)
        // 0.8 margin: content fills 80% of the viewport, leaving breathing room at the edges.
        val fitScaleX = viewport.width.toDouble() * 0.8 / contentWidth
        val fitScaleY = viewport.height.toDouble() * 0.8 / contentHeight
        scale = minOf(fitScaleX, fitScaleY).coerceIn(MIN_SCALE, MAX_SCALE)
        panWorld = WorldPoint((min.x + max.x) / 2.0, (min.y + max.y) / 2.0)
        rebaseIfNeeded()
    }

    /**
     * Re-anchors world-space (0,0) to wherever the camera currently is, so [panWorld]
     * (and every stored stroke coordinate) stays close to zero. Safe to call any time no
     * stroke is actively being drawn — call it after gestures settle, not mid-stroke,
     * since it mutates the same point list a live stroke may still be appending to.
     */
    private fun rebaseIfNeeded() {
        if (!floatingOrigin) return
        if (abs(panWorld.x) < REBASE_THRESHOLD && abs(panWorld.y) < REBASE_THRESHOLD) return
        // Rounded to whole pattern cells: the paper pattern is drawn at world multiples of its
        // spacing, so an arbitrary offset made it jump relative to the content on every rebase.
        // The leftover (under one cell) stays in panWorld, which is still near zero.
        val spacing = BACKGROUND_PATTERN_SPACING_WORLD
        val offset = WorldPoint(
            kotlin.math.round(panWorld.x / spacing) * spacing,
            kotlin.math.round(panWorld.y / spacing) * spacing,
        )
        for (stroke in strokes) {
            stroke.shiftBy(offset)
        }
        // History snapshots are in the same world space, so they must move with it, or an
        // undo after a rebase would restore strokes at their pre-rebase coordinates.
        geometryEpoch++ // every stroke moved
        for (entry in undoStack + redoStack) {
            entry.shift += offset
            entry.images = entry.images?.map { it.copy(position = it.position - offset) }
            entry.texts = entry.texts?.map { it.copy(position = it.position - offset) }
            entry.bones = entry.bones?.map { it.shiftedBy(offset) }
        }
        // Everything else positioned in world space moves too. Previously only strokes did,
        // so after panning past the threshold, text, images, the ruler and pins jumped away
        // from the drawing they were placed on.
        if (textItems.isNotEmpty()) {
            for (i in textItems.indices) textItems[i] = textItems[i].copy(position = textItems[i].position - offset)
            onTextChanged(textItems.toList())
        }
        if (imageItems.isNotEmpty()) {
            for (i in imageItems.indices) imageItems[i] = imageItems[i].copy(position = imageItems[i].position - offset)
            onImageChanged(imageItems.toList())
        }
        if (bezierNodes.isNotEmpty()) for (i in bezierNodes.indices) bezierNodes[i] = bezierNodes[i].shiftedBy(offset)
        if (bones.isNotEmpty()) {
            for (i in bones.indices) bones[i] = bones[i].shiftedBy(offset)
            onBonesChanged(bones.toList())
        }
        boneGesture = boneGesture?.let { it.copy(last = it.last - offset) }
        // A turn holds starting geometry in the old coordinates; one-finger drags don't pan, so
        // this can't normally happen mid-turn, but if it does the turn simply stops.
        boneTurn = null
        if (frames.isNotEmpty()) {
            for (i in frames.indices) frames[i] = frames[i].copy(min = frames[i].min - offset, max = frames[i].max - offset)
            onFramesChanged(frames.toList())
        }
        rulerLine = rulerLine?.let { (start, end) -> (start - offset) to (end - offset) }
        symmetryCenter -= offset
        pendingTextEdit = pendingTextEdit?.let { it.copy(worldPoint = it.worldPoint - offset) }
        onRebased(offset)
        panWorld -= offset
    }
}

/** A document's animation timing (saved per document): playback speed and the X-sheet's holds. */
data class AnimationSettings(val fps: Int = 12, val holds: Map<Int, Int> = emptyMap())

// Changed strokes tested directly before [CanvasState.strokesNear] rebuilds its index instead.
private const val MAX_INDEX_PATCH = 4096

/** A growable int list, so collecting candidate positions doesn't box each one. */
private class IntArrayList {
    private var data = IntArray(16)
    var size = 0
        private set

    fun add(value: Int) {
        if (size == data.size) data = data.copyOf(size * 2)
        data[size++] = value
    }

    fun toSortedArray(): IntArray = data.copyOf(size).also { it.sort() }
}

