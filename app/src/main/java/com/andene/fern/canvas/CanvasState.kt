package com.andene.fern.canvas

import androidx.compose.runtime.mutableStateMapOf
import com.andene.fern.R
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import kotlin.math.PI
import kotlin.random.Random
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

enum class Tool { PEN, ERASER, SELECT, SHAPE, FILL, TEXT, RULER, EYEDROPPER, CONNECT, BONE, BEZIER, MEASURE }

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
    /** Raster pixels changed; [takeDirtyRasterTiles] has what to save. */
    private val onRasterChanged: () -> Unit = {},
    private val onFramesChanged: (List<Frame>) -> Unit = {},
    private val onSavedColorsChanged: (List<Color>) -> Unit = {},
    private val onBrushChanged: (StampBrush) -> Unit = {},
    private val onToolSlotsChanged: (List<ToolPreset?>) -> Unit = {},
    private val onBrushPresetsChanged: (List<BrushPreset>) -> Unit = {},
    private val onConnectorsChanged: (List<Connector>) -> Unit = {},
    private val onBonesChanged: (List<Bone>) -> Unit = {},
    private val onLayerCompsChanged: (List<LayerComp>) -> Unit = {},
    private val onStatsChanged: (DocumentStats) -> Unit = {},
    private val onGuidesChanged: (List<Guide>) -> Unit = {},
    /** The document's drawing scale changed (roadmap item 143). */
    private val onDrawingScaleChanged: (DrawingScale?) -> Unit = {},
    private val onSymbolInstancesChanged: (List<SymbolInstance>) -> Unit = {},
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
        /** Where [selectionMask] lives among [rasters]; not a layer id, so it's never drawn or saved as one. */
        const val SELECTION_MASK_ID = "selection-mask"
        private const val WRAP_NEW_TILE_FILL = 0.8 // a new tile fills 80% of the view's short side
        private const val SIGNATURE_FALLBACK_VIEW_PX = 1024.0 // before the view has a size

        // One second of motion at the default 12 fps.
        private const val MOTION_PATH_STEPS = 12
        private const val MOTION_PATH_SAMPLES = 256

        // Comic panel borders, in screen px at the zoom they're inserted at; on the §7 scale.
        private const val PANEL_BORDER_SCREEN = 3.0
        // Bone tool reach and the shortest bone an add drag makes, in screen px (§7 scale).
        private const val BONE_HIT_SCREEN = 24.0
        // Raster brush dabs this fraction of their radius apart: close enough to read as a line.
        private const val RASTER_DAB_SPACING = 0.25
        // Spring of a dynamic bone, per second: stiff enough to keep up within a few frames,
        // damped to settle after about one overshoot.
        private const val DYNAMICS_STIFFNESS = 120.0
        private const val DYNAMICS_DAMPING = 8.0
        private const val DYNAMICS_SUBSTEPS = 8
        // Weight-painting brush radius in screen pixels (on the §7 scale).
        private const val WEIGHT_BRUSH_SCREEN = 24.0
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
        // How wide (screen px) a newly placed 3D model's picture is (roadmap item 108).
        private const val MODEL_PLACED_SCREEN = 256.0
        // World units (points) between pages of an imported vector PDF (roadmap item 103).
        private const val IMPORTED_PAGE_GAP = 32.0
        // How wide (screen px) a placed QR code is, quiet zone included (roadmap item 144).
        private const val QR_SIZE_SCREEN = 256.0
        // Gutter (screen px) the comic panel cutter leaves between the two panels (roadmap item 105).
        private const val PANEL_GUTTER_SCREEN = 16.0
        // Width (screen px) of the cut a knife stroke takes out of a closed shape (roadmap item 141).
        private const val KNIFE_GAP_SCREEN = 2.0

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
            // The shape builder and knife belong to the SELECT tool; the eraser is spared, since a
            // flipped stylus switches to it for one gesture and back.
            if (value != Tool.SELECT && value != Tool.ERASER) endShapeEdit()
            if (value != Tool.MEASURE) measurement = null
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
        val existing = brushPresets.indexOfFirst { it.name == trimmed }
        // Replacing a preset keeps its tags.
        val preset = BrushPreset(trimmed, brush.clamped(), brushPresets.getOrNull(existing)?.tags.orEmpty())
        if (existing == -1) brushPresets.add(preset) else brushPresets[existing] = preset
        onBrushPresetsChanged(brushPresets.toList())
    }

    /** Sets preset [name]'s tags (trimmed, blank and repeated ones dropped). */
    fun setBrushPresetTags(name: String, tags: List<String>) {
        val index = brushPresets.indexOfFirst { it.name == name }
        if (index == -1) return
        brushPresets[index] = brushPresets[index].copy(tags = tags.map { it.trim() }.filter { it.isNotEmpty() }.distinct())
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

    /**
     * The X-sheet's mouth chart (roadmap item 88): the mouth shape each frame shows for lip-sync,
     * filled by hand or from the soundtrack ([LipSync]). Frames without one aren't listed.
     */
    val frameMouths = mutableStateMapOf<Int, MouthShape>()

    /** Sets (or with null clears) frame [frame]'s mouth shape. */
    fun setMouth(frame: Int, mouth: MouthShape?) {
        if (mouth == null) frameMouths.remove(frame) else frameMouths[frame] = mouth
        onAnimationSettingsChanged(animationSettings())
    }

    /** Replaces the whole mouth chart, e.g. with one worked out from the soundtrack. */
    fun setMouths(mouths: Map<Int, MouthShape>) {
        frameMouths.clear()
        frameMouths.putAll(mouths)
        onAnimationSettingsChanged(animationSettings())
    }

    /**
     * The animation's soundtrack: an audio file in app storage (see [CanvasStorage.importSoundtrack])
     * played in step with playback from the frame it starts on; null when there's none.
     */
    var soundtrack by mutableStateOf<String?>(null)
        private set

    fun setSoundtrackFile(fileName: String?) {
        soundtrack = fileName
        onAnimationSettingsChanged(animationSettings())
    }

    /** When frame [frame] starts, in milliseconds from the first frame, following the X-sheet holds. */
    fun frameStartMillis(frame: Int): Long = (0 until frame).sumOf { holdOf(it).toLong() } * 1000L / animFps

    fun animationSettings() = AnimationSettings(animFps, frameHolds.toMap(), frameMouths.toMap(), soundtrack, frameTags.toList())

    /** Named frame ranges (roadmap item 145); see [FrameTag]. Saved with the animation settings; not part of undo. */
    val frameTags = mutableStateListOf<FrameTag>()

    /** Adds [tag], or replaces the tag of the same name. */
    fun putFrameTag(tag: FrameTag) {
        val clamped = tag.copy(from = tag.from.coerceIn(0, animFrameCount - 1), to = tag.to.coerceIn(tag.from.coerceIn(0, animFrameCount - 1), animFrameCount - 1))
        val index = frameTags.indexOfFirst { it.name == tag.name }
        if (index == -1) frameTags.add(clamped) else frameTags[index] = clamped
        onAnimationSettingsChanged(animationSettings())
    }

    fun removeFrameTag(name: String) {
        if (frameTags.removeWhere { it.name == name }) onAnimationSettingsChanged(animationSettings())
        if (playingTag == name) playingTag = null
    }

    /**
     * The tag playback is confined to (roadmap item 145): playback loops over its frames in its
     * direction instead of the whole animation. Null plays everything.
     */
    var playingTag by mutableStateOf<String?>(null)

    /** Plays only tag [name]'s frames, from its start. */
    fun playTag(name: String) {
        val tag = frameTags.firstOrNull { it.name == name } ?: return
        playingTag = name
        currentAnimFrame = if (tag.direction == TagDirection.REVERSE) tag.to else tag.from
        playingBackwards = tag.direction == TagDirection.REVERSE
        heldTicks = 0
        playing = true
    }

    /**
     * Onion skin settings (roadmap item 145): how many frames before and after the current one
     * show, and whether they're tinted red (before) and blue (after), as in Aseprite, instead of
     * shown in their own colours. View settings, not saved.
     */
    var onionBefore by mutableIntStateOf(1)
    var onionAfter by mutableIntStateOf(0)
    var onionTinted by mutableStateOf(false)

    /** Applies settings read from storage, without reporting them back. */
    fun loadAnimationSettings(settings: AnimationSettings) {
        animFpsState = settings.fps
        frameHolds.clear()
        frameHolds.putAll(settings.holds.filterValues { it > 1 })
        frameMouths.clear()
        frameMouths.putAll(settings.mouths)
        soundtrack = settings.soundtrack
        frameTags.clear()
        frameTags.addAll(settings.tags)
        playingTag = null
    }

    private fun shiftHolds(from: Int, by: Int) {
        if (frameHolds.isEmpty() && frameMouths.isEmpty()) return
        val moved = frameHolds.entries.associate { (frame, hold) -> (if (frame >= from) frame + by else frame) to hold }
        frameHolds.clear()
        frameHolds.putAll(moved)
        // The mouth chart moves with its frames too.
        val mouths = frameMouths.entries.associate { (frame, mouth) -> (if (frame >= from) frame + by else frame) to mouth }
        frameMouths.clear()
        frameMouths.putAll(mouths)
        onAnimationSettingsChanged(animationSettings())
    }

    private fun shiftTags(moved: List<FrameTag>) {
        if (moved == frameTags.toList()) return
        frameTags.clear()
        frameTags.addAll(moved)
        if (frameTags.none { it.name == playingTag }) playingTag = null
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

    /** The curve [Easing.CUSTOM] follows, shaped in the curve editor. */
    var motionCurve by mutableStateOf(CubicEasing.DEFAULT)

    /** Progress at time [t] (0..1) under the chosen easing. */
    fun easedProgress(t: Double): Double = if (motionEasing == Easing.CUSTOM) motionCurve.at(t) else motionEasing.at(t)

    private var playingBackwards = false

    /** Next frame for playback: wraps to the first when looping, reverses at either end in ping-pong. */
    fun advanceAnimFrame() {
        if (animFrameCount <= 1) return
        if (++heldTicks < holdOf(currentAnimFrame)) return
        heldTicks = 0
        // A played tag (roadmap item 145) loops its own frames in its own direction.
        frameTags.firstOrNull { it.name == playingTag }?.let { tag ->
            val from = tag.from.coerceAtMost(animFrameCount - 1)
            val to = tag.to.coerceAtMost(animFrameCount - 1)
            if (currentAnimFrame !in from..to) {
                currentAnimFrame = if (tag.direction == TagDirection.REVERSE) to else from
                return
            }
            when (tag.direction) {
                TagDirection.FORWARD -> currentAnimFrame = if (currentAnimFrame >= to) from else currentAnimFrame + 1
                TagDirection.REVERSE -> currentAnimFrame = if (currentAnimFrame <= from) to else currentAnimFrame - 1
                TagDirection.PING_PONG -> {
                    if (from == to) return
                    if (currentAnimFrame >= to) playingBackwards = true
                    if (currentAnimFrame <= from) playingBackwards = false
                    currentAnimFrame += if (playingBackwards) -1 else 1
                }
            }
            return
        }
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
        shiftTags(tagsAfterInsert(frameTags, insertAt))
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
            for (step in 1..count) bones.addAll(interpolatePose(poseFrom, poseTo, easedProgress(step.toDouble() / (count + 1)), from + step))
        }
        if (bones.isNotEmpty()) onBonesChanged(bones.toList())
        // Matched once per pair, shared by every in-between.
        val matched = pairs.map { (a, b) -> tweenSamples(a, b, maxOf(a.points.size, b.points.size, 2)) }
        for (step in 1..count) {
            val t = easedProgress(step.toDouble() / (count + 1))
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
            val position = easedProgress(step.toDouble() / (steps - 1)) * (route.size - 1)
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
        val hadHold = frameHolds.remove(removed) != null
        if (frameMouths.remove(removed) != null || hadHold) onAnimationSettingsChanged(animationSettings())
        shiftHolds(from = removed + 1, by = -1)
        shiftTags(tagsAfterDelete(frameTags, removed))
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

    /** This document's usage statistics (roadmap item 132); see [DocumentStats]. */
    var stats by mutableStateOf(DocumentStats())
        private set

    // When the last counted action ended, this session; null until one has.
    private var lastStatsAction: Long? = null

    /** Replaces the statistics (from [CanvasStorage.loadStats]) when a document is opened. */
    fun loadStats(loaded: DocumentStats) {
        stats = loaded
        lastStatsAction = null
    }

    private fun countAction(action: StatsAction, startedAt: Long) {
        val now = System.currentTimeMillis()
        stats = stats.after(action, startedAt, now, lastStatsAction)
        lastStatsAction = now
        onStatsChanged(stats)
    }

    /** A voice note playing in step with its ink (roadmap item 130); null otherwise. Purely a view. */
    var audioReplay by mutableStateOf<AudioReplay?>(null)

    /** The visible stroke drawn during [audioReplay]'s recording nearest [world] (within a finger's reach), for jumping playback to it. */
    fun syncedStrokeAt(world: WorldPoint): Stroke? {
        val replay = audioReplay ?: return null
        val reach = ERASER_RADIUS_SCREEN / scale.coerceAtLeast(1e-300)
        return strokesNear(world.x - reach, world.y - reach, world.x + reach, world.y + reach)
            .filter { replay.covers(it) && isVisible(it) }
            .map { it to distanceToPolyline(it.points, world) - it.widthWorld / 2 }
            .filter { (_, distance) -> distance <= reach }
            .minByOrNull { (_, distance) -> distance }?.first
    }

    /** Named regions of the canvas, in creation order. */
    val frames = mutableStateListOf<Frame>()

    /** Adds a frame covering exactly what's on screen now (unrotated view). */
    /** The frame wrap-around mode repeats (roadmap item 137), or null when the mode is off. View state; not saved. */
    var wrapFrameId by mutableStateOf<String?>(null)
        private set

    val wrapTile: Frame? get() = wrapFrameId?.let { id -> frames.firstOrNull { it.id == id } }

    /**
     * Turns wrap-around mode on - tiling the frame named [WRAP_TILE_FRAME_NAME], made as the
     * largest square in the middle of the view if there's none - or off.
     */
    fun toggleWrapAround() {
        if (wrapFrameId != null) {
            wrapFrameId = null
            return
        }
        val existing = frames.firstOrNull { it.name == WRAP_TILE_FRAME_NAME }
        if (existing != null) {
            wrapFrameId = existing.id
            return
        }
        if (viewportSize.width <= 0f || viewportSize.height <= 0f) return
        val half = minOf(viewportSize.width, viewportSize.height) / 2.0 / scale.coerceAtLeast(1e-300) * WRAP_NEW_TILE_FILL
        val tile = Frame(UUID.randomUUID().toString(), WRAP_TILE_FRAME_NAME, WorldPoint(panWorld.x - half, panWorld.y - half), WorldPoint(panWorld.x + half, panWorld.y + half))
        frames.add(tile)
        onFramesChanged(frames.toList())
        wrapFrameId = tile.id
    }

    fun addFrameFromView(name: String) {
        if (viewportSize.width <= 0f || viewportSize.height <= 0f) return
        val center = Offset(viewportSize.width / 2f, viewportSize.height / 2f)
        val min = screenToWorld(Offset.Zero, center)
        val max = screenToWorld(Offset(viewportSize.width, viewportSize.height), center)
        frames.add(Frame(UUID.randomUUID().toString(), name, min, max))
        onFramesChanged(frames.toList())
    }

    /**
     * Comic page template (roadmap item 105): a page of [size]'s proportions centred in the view,
     * filling most of its height, ruled into [layout]'s panels as ordinary rectangle strokes on
     * the active layer, plus a frame named "Page N" so it can be presented and exported as a
     * page. The defaults are the classic 2:3 page of five panels. Undoable for the panels.
     */
    fun insertComicPage(size: ComicPageSize = ComicPageSize.CLASSIC, layout: ComicLayout = ComicLayout.CLASSIC_FIVE) {
        if (!activeLayerEditable || viewportSize.width <= 0f) return
        recordHistory()
        val s = scale.coerceAtLeast(1e-300)
        // The page fills most of the screen's height (or width, for a wide page) at the current zoom.
        val fit = minOf(viewportSize.height * 0.8 / size.height, viewportSize.width * 0.8 / size.width) / s
        val pageWidth = size.width * fit
        val pageHeight = size.height * fit
        val left = panWorld.x - pageWidth / 2.0
        val top = panWorld.y - pageHeight / 2.0
        val panels = comicPanels(layout, left, top, pageWidth, pageHeight, pageWidth * COMIC_MARGIN_FRACTION, pageWidth * COMIC_GUTTER_FRACTION)
        for ((panelLeft, panelTop, panelRight, panelBottom) in panels.map { it.toList() }) {
            val border = Stroke(Color.Black, PANEL_BORDER_SCREEN / s, PenType.MARKER, layerId = activeLayerId, animFrame = currentAnimFrame)
            for (point in generateShapePoints(ShapeKind.RECTANGLE, WorldPoint(panelLeft, panelTop), WorldPoint(panelRight, panelBottom))) {
                border.addPoint(point)
            }
            strokes.add(border)
        }
        frames.add(Frame(UUID.randomUUID().toString(), "Page ${frames.size + 1}", WorldPoint(left, top), WorldPoint(left + pageWidth, top + pageHeight)))
        onFramesChanged(frames.toList())
        strokesChanged()
    }

    /**
     * Speed or focus lines (roadmap item 105) over the selection's bounds, or the view when
     * nothing is selected: [count] lines in the active colour at the active width, tapering
     * toward the inner end ([comicLines] describes [inner] and [angleDegrees]), on the active
     * layer as one selected group, the selection's strokes left as they are. One undo step.
     */
    fun addComicLines(kind: ComicLineKind, count: Int, inner: Double, angleDegrees: Double, seed: Int = Random.nextInt()) {
        if (!activeLayerEditable) return
        val (min, max) = selectionBounds() ?: run {
            if (viewportSize.width <= 0f) return
            val center = Offset(viewportSize.width / 2f, viewportSize.height / 2f)
            val corners = listOf(Offset.Zero, Offset(viewportSize.width, 0f), Offset(0f, viewportSize.height), Offset(viewportSize.width, viewportSize.height))
                .map { screenToWorld(it, center) }
            WorldPoint(corners.minOf { it.x }, corners.minOf { it.y }) to WorldPoint(corners.maxOf { it.x }, corners.maxOf { it.y })
        }
        val lines = comicLines(kind, min, max, count, inner, angleDegrees, seed)
        if (lines.isEmpty()) return
        recordHistory()
        val group = UUID.randomUUID().toString()
        val added = lines.map { line ->
            Stroke(
                activeColor.copy(alpha = activeColor.alpha * activeOpacity.toFloat()),
                activeWidthWorld / scale.coerceAtLeast(1e-300),
                PenType.MARKER,
                layerId = activeLayerId,
                animFrame = currentAnimFrame,
                groupId = group,
                lineStyle = LineStyle(LineCap.ROUND, LineJoin.ROUND, WidthProfile.TAPER_END),
            ).apply { for (p in line) addPoint(p) }
        }
        strokes.addAll(added)
        clearSelectionState()
        selection.addAll(added)
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

    /**
     * Weight painting (roadmap item 84): with the bone tool, dragging over ink bound to the
     * selected bone sets how much each point under the brush follows that bone rather than its
     * parent ([Stroke.jointWeights]) to [weightPaintValue], overriding the automatic weights the
     * next time a joint is turned. Each drag is one undo step.
     */
    var paintingWeights by mutableStateOf(false)

    /** The weight painting applies, 0 (follow the parent) to 1 (follow the bone itself). */
    var weightPaintValue by mutableDoubleStateOf(1.0)

    private enum class BoneGestureKind { ADD, ROTATE, MOVE, REACH, PAINT, NONE }

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
        // A frame holding only a pose (no ink yet, e.g. an imported animation) still counts.
        animFrameCount = maxOf(animFrameCount, (loaded.maxOfOrNull { it.animFrame } ?: 0) + 1)
    }

    /**
     * Adds a skeleton posed frame by frame (from [SpineImport]): [poses] holds frame 0's bones,
     * then frame 1's, and so on, each bone keeping its id across frames. Frames are added as
     * needed. One undo step.
     */
    fun importSkeleton(poses: List<List<Bone>>) {
        if (poses.isEmpty() || poses.all { it.isEmpty() }) return
        recordHistory()
        poses.forEachIndexed { frame, pose -> bones.addAll(pose.map { it.copy(animFrame = frame) }) }
        animFrameCount = maxOf(animFrameCount, poses.size)
        onBonesChanged(bones.toList())
        strokesChanged()
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
        if (paintingWeights) {
            if (selectedBoneId == null) selectedBoneId = boneAt(frameBones, world, reach)?.id
            if (selectedBoneId == null) return
            recordHistory()
            boneGesture = BoneGesture(BoneGestureKind.PAINT, selectedBoneId, world)
            paintWeightsAt(world)
            return
        }
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

    /** Sets [weightPaintValue] on the points of the selected bone's ink within the brush of [world]. */
    private fun paintWeightsAt(world: WorldPoint) {
        val boneId = selectedBoneId ?: return
        val radius = WEIGHT_BRUSH_SCREEN / scale.coerceAtLeast(1e-300)
        val value = weightPaintValue.toFloat().coerceIn(0f, 1f)
        for (stroke in strokesNear(world.x - radius, world.y - radius, world.x + radius, world.y + radius)) {
            if (stroke.boneId != boneId || stroke.animFrame != currentAnimFrame) continue
            val points = stroke.points
            var weights: FloatArray? = null
            for (i in points.indices) {
                if (hypot(points[i].x - world.x, points[i].y - world.y) > radius) continue
                if (weights == null) weights = stroke.jointWeights?.copyOf() ?: FloatArray(points.size) { Float.NaN }
                weights[i] = value
            }
            if (weights != null) stroke.setJointWeights(weights)
        }
    }

    private fun startTurn(bone: Bone, pointer: WorldPoint, frame: Int = currentAnimFrame): BoneTurn {
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
                    // A painted weight is the share of the stroke's own bone: for ink on the
                    // turned bone that's how far it follows; for ink on the parent, the rest.
                    val painted = stroke.jointWeights?.takeIf { it.size == points.size }
                    DoubleArray(points.size) { i ->
                        val w = painted?.get(i)
                        if (w != null && !w.isNaN()) {
                            (if (stroke.boneId == bone.id) w.toDouble() else 1.0 - w).coerceIn(0.0, 1.0)
                        } else {
                            val toBone = distanceToBone(bone, points[i])
                            val toParent = distanceToBone(parent, points[i])
                            if (toBone + toParent == 0.0) 0.5 else toParent / (toBone + toParent)
                        }
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
                val start = turn.startBones.associateBy { it.id }
                // Joint limits hold for a hand-turned bone as for inverse kinematics.
                val turned = gesture.boneId?.let { start[it] }
                val angle = limitedTurn(
                    turned ?: return,
                    turned.parentId?.let { start[it] },
                    normalizeAngle(atan2(world.y - turn.center.y, world.x - turn.center.x) - turn.startAngle),
                )
                applyTurn(turn, angle, currentAnimFrame)
                boneGesture = gesture.copy(last = world)
            }
            BoneGestureKind.PAINT -> paintWeightsAt(world)
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

    /** Puts [turn]'s bones and ink at [angle] radians from where the turn started, in [frame]. */
    private fun applyTurn(turn: BoneTurn, angle: Double, frame: Int) {
        val start = turn.startBones.associateBy { it.id }
        for (i in bones.indices) {
            val b = bones[i]
            if (b.animFrame == frame && b.id in turn.moved) {
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
    }

    /** Every bone's timeline across the animation, for the dopesheet (roadmap item 134). */
    fun boneTimeline(label: (Int) -> String): List<BoneTrack> = boneTracks(bones.toList(), animFrameCount, label)

    /**
     * Turns bone [boneId] in [frame] so its angle (relative to its parent's, or its own for a
     * root - see [BoneTrack]) is [target] radians, carrying its children and bound ink the way
     * turning it by hand does, within its joint limits. The curve editor's edit (roadmap item
     * 134). [recordUndo] false continues the previous call's undo step (later moves of one drag).
     */
    fun setBoneAngle(boneId: String, frame: Int, target: Double, recordUndo: Boolean = true) {
        val frameBones = bones.filter { it.animFrame == frame }
        val bone = frameBones.firstOrNull { it.id == boneId } ?: return
        val parent = bone.parentId?.let { id -> frameBones.firstOrNull { it.id == id } }
        val current = if (parent != null) relativeAngle(bone, parent) else bone.angle
        val turn = limitedTurn(bone, parent, normalizeAngle(target - current))
        if (abs(turn) < 1e-12) return
        if (recordUndo) recordHistory()
        applyTurn(startTurn(bone, bone.tail, frame), turn, frame)
        onBonesChanged(bones.toList())
        strokesChanged()
    }

    /**
     * Procedural motion (roadmap item 135): turns bone [boneId] in every frame it's in by
     * [offset] (frame -> radians, see [proceduralSway]), on top of the pose already there,
     * carrying its children and ink, within its joint limits. One undo step.
     */
    fun swayBone(boneId: String, offset: (Int) -> Double) {
        val frames = bones.filter { it.id == boneId }.map { it.animFrame }.distinct()
        if (frames.isEmpty()) return
        recordHistory()
        for (frame in frames) {
            val frameBones = bones.filter { it.animFrame == frame }
            val bone = frameBones.firstOrNull { it.id == boneId } ?: continue
            val parent = bone.parentId?.let { id -> frameBones.firstOrNull { it.id == id } }
            val turn = limitedTurn(bone, parent, offset(frame))
            if (abs(turn) > 1e-12) applyTurn(startTurn(bone, bone.tail, frame), turn, frame)
        }
        onBonesChanged(bones.toList())
        strokesChanged()
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
            BoneGestureKind.PAINT -> strokesChanged()
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
                val wanted = normalizeAngle(atan2(target.y - joint.head.y, target.x - joint.head.x) - atan2(tip.y - joint.head.y, tip.x - joint.head.x))
                val turn = limitedTurn(joint, joint.parentId?.let { bone(it) }, wanted)
                if (turn != 0.0) transformRig(id) { rotateAbout(it, joint.head, turn) }
            }
            val tip = bone(endId)?.tail ?: return
            if (hypot(tip.x - target.x, tip.y - target.y) < IK_TOLERANCE_SCREEN / scale.coerceAtLeast(1e-300)) return
        }
    }

    // Applies [transform] to bone [rootId], the bones hanging from it, and every stroke bound to
    // one of them, on the current frame.
    private fun transformRig(rootId: String, frame: Int = currentAnimFrame, transform: (WorldPoint) -> WorldPoint) {
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
     * Animation blending (roadmap item 86): mixes the current frame's pose with frame
     * [otherFrame]'s - [weight] 0 keeps this pose, 1 takes the other - as in-betweens do
     * ([interpolatePose]: joints turn, chains stay attached). Each bone's ink moves rigidly with
     * its bone from the old pose to the blended one. Bones present in only one of the two poses
     * stay as they are. One undo step.
     */
    fun blendPoseWith(otherFrame: Int, weight: Double) {
        val frame = currentAnimFrame
        val mine = bones.filter { it.animFrame == frame }
        val other = bones.filter { it.animFrame == otherFrame }
        if (otherFrame == frame || mine.isEmpty() || other.isEmpty()) return
        val blended = interpolatePose(mine, other, weight.coerceIn(0.0, 1.0), frame).associateBy { it.id }
        if (blended.isEmpty()) return
        recordHistory()
        val before = mine.associateBy { it.id }
        for (stroke in strokes) {
            if (stroke.animFrame != frame) continue
            val old = before[stroke.boneId] ?: continue
            val new = blended[old.id] ?: continue
            val turn = new.angle - old.angle
            val shift = new.head - old.head
            stroke.setPoints(stroke.points.map { rotateAbout(it, old.head, turn) + shift })
        }
        for (i in bones.indices) {
            val b = bones[i]
            if (b.animFrame == frame) blended[b.id]?.let { bones[i] = it }
        }
        geometryEpoch++
        onBonesChanged(bones.toList())
        strokesChanged()
    }

    /**
     * Sets bone [boneId]'s joint limits on every frame (relative angles in radians; null and null
     * for a free joint). Undoable.
     */
    fun setJointLimits(boneId: String, min: Double?, max: Double?) {
        recordHistory()
        for (i in bones.indices) if (bones[i].id == boneId) bones[i] = bones[i].copy(minTurn = min, maxTurn = max)
        onBonesChanged(bones.toList())
    }

    /** Marks bone [boneId] dynamic or not, on every frame. Undoable. */
    fun setBoneDynamic(boneId: String, dynamic: Boolean) {
        recordHistory()
        for (i in bones.indices) if (bones[i].id == boneId) bones[i] = bones[i].copy(dynamic = dynamic)
        onBonesChanged(bones.toList())
    }

    /**
     * Secondary motion (roadmap item 85): every dynamic bone follows its parent through the
     * animation like a damped spring instead of rigidly - it trails a moving parent, overshoots
     * when the parent stops, and settles - the way a tail, hair or a loose sleeve moves. Each
     * frame's keyed pose is the spring's rest position; the simulated angle is written into the
     * frame, turning the bone with its children and bound ink. Frames are visited in order at the
     * playback speed, in [DYNAMICS_SUBSTEPS] steps each. One undo step.
     */
    fun simulateDynamics() {
        val frames = (0 until animFrameCount).filter { f -> bones.any { it.animFrame == f } }
        val dynamicIds = bones.filter { it.dynamic && it.parentId != null }.map { it.id }.distinct()
        if (frames.size < 2 || dynamicIds.isEmpty()) return
        recordHistory()
        val dt = 1.0 / animFps / DYNAMICS_SUBSTEPS
        // Parents first, so a dynamic chain uses its already-simulated parent.
        val order = dynamicIds.sortedBy { id -> generateSequence(bones.firstOrNull { it.id == id }) { b -> b.parentId?.let { p -> bones.firstOrNull { it.id == p } } }.count() }
        for (id in order) {
            var angle = Double.NaN
            var velocity = 0.0
            for (frame in frames) {
                fun bone(boneId: String) = bones.firstOrNull { it.animFrame == frame && it.id == boneId }
                val b = bone(id) ?: continue
                val parent = b.parentId?.let { bone(it) } ?: continue
                val rest = parent.angle + relativeAngle(b, parent)
                if (angle.isNaN()) {
                    angle = rest
                } else {
                    repeat(DYNAMICS_SUBSTEPS) {
                        val pull = normalizeAngle(rest - angle)
                        velocity += (DYNAMICS_STIFFNESS * pull - DYNAMICS_DAMPING * velocity) * dt
                        angle += velocity * dt
                    }
                }
                val turn = normalizeAngle(angle - b.angle)
                if (turn != 0.0) transformRig(id, frame) { rotateAbout(it, b.head, turn) }
            }
        }
        onBonesChanged(bones.toList())
        strokesChanged()
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

    /**
     * Resizes frame [id] to [width] x [height] world units, its top-left corner staying put
     * (roadmap item 144). The strokes on it - visible ones whose bounds' centre lies in the old
     * frame - follow their [Stroke.constraints] (unconstrained ones stay pinned top-left, so they
     * don't move); a group's members follow within the whole group's bounds. Their change is one undo step; the frame's size, like every frame edit, isn't
     * part of the undo history, so resizing back is how to restore it.
     */
    fun resizeFrame(id: String, width: Double, height: Double) {
        val index = frames.indexOfFirst { it.id == id }
        if (index == -1 || width <= 0 || height <= 0 || !width.isFinite() || !height.isFinite()) return
        val old = frames[index]
        val resized = old.copy(max = WorldPoint(old.min.x + width, old.min.y + height))
        val inside = strokes.filter { stroke ->
            val cx = (stroke.minX + stroke.maxX) / 2
            val cy = (stroke.minY + stroke.maxY) / 2
            stroke.points.isNotEmpty() && isVisible(stroke) && cx in old.min.x..old.max.x && cy in old.min.y..old.max.y &&
                stroke.constraints.let { it != null && it != LayoutConstraints() }
        }
        if (inside.isNotEmpty()) {
            recordHistory()
            // A group follows as one object: its members stretch or scale within the group's bounds.
            val groupBounds = inside.filter { it.groupId != null }.groupBy { it.groupId }.mapValues { (_, members) ->
                listOf(members.minOf { it.minX }, members.minOf { it.minY }, members.maxOf { it.maxX }, members.maxOf { it.maxY })
            }
            for (stroke in inside) {
                val pins = stroke.constraints ?: continue
                val (minX, minY, maxX, maxY) = groupBounds[stroke.groupId] ?: listOf(stroke.minX, stroke.minY, stroke.maxX, stroke.maxY)
                stroke.setPoints(stroke.points.map { p ->
                    WorldPoint(
                        constrainedCoordinate(p.x, minX, maxX, old.min.x, old.max.x, resized.min.x, resized.max.x, pins.horizontal),
                        constrainedCoordinate(p.y, minY, maxY, old.min.y, old.max.y, resized.min.y, resized.max.y, pins.vertical),
                    )
                })
            }
            strokesChanged()
        }
        frames[index] = resized
        onFramesChanged(frames.toList())
    }

    /** The selected strokes' shared constraints (roadmap item 144): the default when they differ or have none. */
    val selectionConstraints: LayoutConstraints
        get() = selection.map { it.constraints ?: LayoutConstraints() }.distinct().singleOrNull() ?: LayoutConstraints()

    /** Gives every selected stroke [constraints] (roadmap item 144). One undo step. */
    fun setSelectionConstraints(constraints: LayoutConstraints) {
        if (selection.isEmpty()) return
        recordHistory()
        val stored = constraints.takeIf { it != LayoutConstraints() }
        for (stroke in selection) stroke.setConstraints(stored)
        strokesChanged()
    }

    /** Lays [grid] over frame [id] (roadmap item 142), or removes its grid when null. Saved with the frames. */
    fun setFrameLayoutGrid(id: String, grid: LayoutGrid?) {
        val index = frames.indexOfFirst { it.id == id }
        if (index == -1) return
        frames[index] = frames[index].copy(layoutGrid = grid)
        onFramesChanged(frames.toList())
    }

    /**
     * The isometric plane (roadmap item 142) shapes are drawn on: a rectangle drag makes that
     * face of a cube, an ellipse an isometric circle ([shapeOnPlane]). Null draws flat.
     */
    var isometricPlane: IsometricPlane? by mutableStateOf(null)

    /**
     * Lays the selected strokes onto [plane] (roadmap item 142): their shape as drawn flat is
     * mapped onto that isometric face around the selection's centre, the way Affinity's
     * isometric panel places an object on a plane. One undo step.
     */
    fun placeSelectionOnPlane(plane: IsometricPlane) {
        val (min, max) = selectionBounds() ?: return
        if (selection.isEmpty()) return
        recordHistory()
        val centre = WorldPoint((min.x + max.x) / 2, (min.y + max.y) / 2)
        for (stroke in selection) stroke.setPoints(stroke.points.map { plane.toScreenPlane(it, centre) })
        strokesChanged()
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
        get() = layers.firstOrNull { it.id == activeLayerId }?.let { it.visible && !it.locked && it.adjustment == null } ?: false

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

    /**
     * Raster layers' pixels by layer id (roadmap item 89). Not snapshot state: [rasterRevision]
     * is bumped whenever any of them changes, which is what views observe.
     */
    val rasters = HashMap<String, RasterSurface>()

    var rasterRevision by mutableIntStateOf(0)
        private set

    // The history entry whose log changes are recorded into now (see openLog).
    private var openEntry: HistoryEntry? = null

    // Tiles changed since the last save, by layer, for incremental raster saving.
    private val dirtyRasterTiles = HashMap<String, MutableSet<Long>>()

    /** Adds a raster layer of [depth] bits above the others, its pixels the size of a screen pixel at the current zoom. */
    fun addRasterLayer(depth: Int, hdr: Boolean = false) {
        // HDR needs the 16-bit range for its headroom.
        val bits = if (hdr) 16 else depth
        val layer = Layer(UUID.randomUUID().toString(), (if (hdr) "HDR " else "Raster ") + "${layers.size + 1}", raster = true, depth = bits, hdr = hdr)
        attachRaster(layer.id, RasterSurface(bits, 1.0 / scale.coerceAtLeast(1e-300)).also { it.hdr = hdr; it.hdrView = hdrView })
        layers.add(layer)
        activeLayerId = layer.id
        onLayersChanged(layers.toList())
        markRasterDirty(layer.id, emptySet())
    }

    /** Replaces the raster layers' pixels (from [CanvasStorage.loadRasters]); call after [loadLayers]. */
    fun loadRasters(loaded: Map<String, RasterSurface>) {
        rasters.clear()
        dirtyRasterTiles.clear()
        for ((id, surface) in loaded) attachRaster(id, surface)
        // A raster layer saved before its first tile has no file yet; it starts empty.
        for (layer in layers) if (layer.raster && layer.id !in rasters) attachRaster(layer.id, RasterSurface(layer.depth, 1.0 / scale.coerceAtLeast(1e-300)))
        for (layer in layers) if (layer.masked && maskIdOf(layer.id) !in rasters) attachRaster(maskIdOf(layer.id), RasterSurface(8, 1.0 / scale.coerceAtLeast(1e-300)))
        for (layer in layers) rasters[layer.id]?.let { it.hdr = layer.hdr; it.hdrView = hdrView }
        rasterSelection = null
        selectionMask = null
        paintingSelection = false
        editingMask = false
        rasterRevision++
    }

    private fun attachRaster(layerId: String, surface: RasterSurface) {
        surface.beforeTileChange = { key -> recordRasterTile(layerId, surface, key) }
        surface.afterTileChange = { key -> dirtyRasterTiles.getOrPut(layerId) { HashSet() }.add(key) }
        rasters[layerId] = surface
    }

    /** Saves [key]'s tile as it is now into the open history entry, once per entry. */
    private fun recordRasterTile(layerId: String, surface: RasterSurface, key: Long) {
        val entry = openEntry ?: return
        val ref = layerId to key
        if (ref !in entry.rasterLog) entry.rasterLog[ref] = surface.tileAt(key)?.copy()
    }

    private fun markRasterDirty(layerId: String, keys: Set<Long>) {
        dirtyRasterTiles.getOrPut(layerId) { HashSet() }.addAll(keys)
        rasterRevision++
        onRasterChanged()
    }

    /**
     * The raster tiles changed since the last call, by layer, as copies safe to write from
     * another thread (null for a tile that's gone), with each layer's surface for its settings.
     */
    fun takeDirtyRasterTiles(): Map<String, Pair<RasterSurface, Map<Long, RasterTile?>>> {
        val result = dirtyRasterTiles.mapNotNull { (layerId, keys) ->
            val surface = rasters[layerId] ?: return@mapNotNull null
            layerId to (surface to keys.associateWith { key -> surface.tileAt(key)?.takeUnless { it.isEmpty() }?.copy() })
        }.toMap()
        dirtyRasterTiles.clear()
        return result
    }

    private val activeRaster: RasterSurface?
        get() {
            val layer = layers.firstOrNull { it.id == activeLayerId } ?: return null
            if (editingMask && layer.masked) return rasters[maskIdOf(layer.id)]
            if (!layer.raster) return null
            if (paintingSelection) return selectionMask
            return rasters[layer.id]
        }

    /**
     * While on, the pen and eraser paint the active layer's mask (roadmap item 91) instead of the
     * layer: the pen hides, the eraser reveals.
     */
    var editingMask by mutableStateOf(false)

    /** Gives layer [layerId] a mask, hiding nothing until painted, and starts editing it. */
    fun addLayerMask(layerId: String) {
        val layer = layers.firstOrNull { it.id == layerId } ?: return
        if (layer.masked) return
        attachRaster(maskIdOf(layerId), RasterSurface(8, 1.0 / scale.coerceAtLeast(1e-300)))
        updateLayer(layerId) { it.copy(masked = true) }
        activeLayerId = layerId
        editingMask = true
        markRasterDirty(maskIdOf(layerId), emptySet())
    }

    fun removeLayerMask(layerId: String) {
        updateLayer(layerId) { it.copy(masked = false) }
        dropRaster(maskIdOf(layerId))
        editingMask = false
    }

    /**
     * The raster selection (roadmap item 91): a closed outline in world space limiting raster
     * painting and the selection actions; null when nothing is selected. Made with the select
     * tool's lasso or marquee while the active layer is a raster layer.
     */
    var rasterSelection by mutableStateOf<List<WorldPoint>?>(null)
        private set

    fun clearRasterSelection() {
        rasterSelection = null
        dropSelectionMask()
    }

    /**
     * The global selection mask (roadmap item 138, Krita's): the raster selection as coverage
     * painted with the pen (adding) and eraser (taking away) while [paintingSelection] is on, in
     * place of an outline. Pixels count as selected where it's at least half covered. Kept in
     * [rasters] under [SELECTION_MASK_ID] so its painting undoes like any raster edit, but never
     * saved: a selection is a working state, not part of the drawing.
     */
    var selectionMask: RasterSurface? by mutableStateOf(null)
        private set

    /** While on (with a raster layer active), the pen and eraser paint [selectionMask] instead of the layer. */
    var paintingSelection by mutableStateOf(false)
        private set

    /** Some pixels are selected, by outline or mask. */
    val hasRasterSelection: Boolean get() = rasterSelection != null || selectionMask != null

    /**
     * Starts or stops painting the selection. Starting turns an outline selection into the mask
     * (so it can be painted on); stopping keeps the mask as the selection.
     */
    fun togglePaintingSelection() {
        if (paintingSelection) {
            paintingSelection = false
            return
        }
        val layer = layers.firstOrNull { it.id == activeLayerId }?.takeIf { it.raster } ?: return
        val surface = rasters[layer.id] ?: return
        if (selectionMask == null) {
            val mask = RasterSurface(8, surface.pixelWorld, surface.originX, surface.originY)
            attachRaster(SELECTION_MASK_ID, mask)
            // Not saved: only undo needs to know about its tiles.
            mask.afterTileChange = {}
            rasterSelection?.let { outline ->
                selectionPixelBox(mask)?.let { box ->
                    mask.forEachPixelIn(box[0].toDouble(), box[1].toDouble(), box[2].toDouble(), box[3].toDouble()) { tile, index, px, py ->
                        if (polygonContains(outline, mask.worldOf(px + 0.5, py + 0.5))) tile.data[index + 3] = 65535.toShort()
                    }
                    mask.tiles.entries.removeAll { it.value.isEmpty() }
                }
            }
            rasterSelection = null
            selectionMask = mask
        }
        paintingSelection = true
        rasterRevision++
    }

    private fun dropSelectionMask() {
        if (selectionMask == null) return
        rasters.remove(SELECTION_MASK_ID)
        selectionMask = null
        paintingSelection = false
        rasterRevision++
    }

    /** Whether pixel ([px], [py])'s centre is inside the raster selection on [surface]. */
    private fun inRasterSelection(surface: RasterSurface, px: Int, py: Int): Boolean {
        val world = surface.worldOf(px + 0.5, py + 0.5)
        selectionMask?.let { mask ->
            val mx = kotlin.math.floor((world.x - mask.originX) / mask.pixelWorld).toInt()
            val my = kotlin.math.floor((world.y - mask.originY) / mask.pixelWorld).toInt()
            return mask.pixel(mx, my)[3] >= 0.5f
        }
        val outline = rasterSelection ?: return true
        return polygonContains(outline, world)
    }

    private fun selectionPixelBox(surface: RasterSurface): IntArray? {
        selectionMask?.let { mask ->
            if (mask.tiles.isEmpty()) return null
            val xs = mask.tiles.keys.map { RasterSurface.tileX(it) }
            val ys = mask.tiles.keys.map { RasterSurface.tileY(it) }
            val min = mask.worldOf((xs.min() * RASTER_TILE).toDouble(), (ys.min() * RASTER_TILE).toDouble())
            val max = mask.worldOf(((xs.max() + 1) * RASTER_TILE).toDouble(), ((ys.max() + 1) * RASTER_TILE).toDouble())
            return intArrayOf(
                kotlin.math.floor((min.x - surface.originX) / surface.pixelWorld).toInt(), kotlin.math.floor((min.y - surface.originY) / surface.pixelWorld).toInt(),
                kotlin.math.ceil((max.x - surface.originX) / surface.pixelWorld).toInt(), kotlin.math.ceil((max.y - surface.originY) / surface.pixelWorld).toInt(),
            )
        }
        val outline = rasterSelection ?: return null
        fun px(x: Double) = (x - surface.originX) / surface.pixelWorld
        fun py(y: Double) = (y - surface.originY) / surface.pixelWorld
        return intArrayOf(
            kotlin.math.floor(px(outline.minOf { it.x })).toInt(), kotlin.math.floor(py(outline.minOf { it.y })).toInt(),
            kotlin.math.ceil(px(outline.maxOf { it.x })).toInt(), kotlin.math.ceil(py(outline.maxOf { it.y })).toInt(),
        )
    }

    /** Clears the selected pixels on the active raster layer. Undoable. */
    fun deleteRasterSelectionPixels() = editSelectedPixels { tile, index -> for (c in 0 until 4) tile.data[index + c] = 0 }

    /** Fills the selection with the active colour (at the active opacity) on the active raster layer. Undoable. */
    fun fillRasterSelection() {
        val color = floatArrayOf(activeColor.red, activeColor.green, activeColor.blue, activeColor.alpha * activeOpacity.toFloat())
        editSelectedPixels { tile, index ->
            val a = color[3]
            for (c in 0 until 3) tile.data[index + c] = (color[c] * a * 65535 + RasterSurface.u16(tile.data[index + c]) * (1 - a)).toInt().coerceIn(0, 65535).toShort()
            tile.data[index + 3] = (a * 65535 + RasterSurface.u16(tile.data[index + 3]) * (1 - a)).toInt().coerceIn(0, 65535).toShort()
        }
    }

    private fun editSelectedPixels(edit: (RasterTile, Int) -> Unit) {
        val surface = activeRaster ?: return
        val box = selectionPixelBox(surface) ?: return
        recordHistory()
        surface.clip = { x, y -> inRasterSelection(surface, x, y) }
        surface.forEachPixelIn(box[0].toDouble(), box[1].toDouble(), box[2].toDouble(), box[3].toDouble()) { tile, index, _, _ -> edit(tile, index) }
        surface.clip = null
        markRasterDirty(activeLayerId, emptySet())
    }

    /**
     * Turns the raster selection's pixels - or, with no selection, all the active raster layer's
     * paint - [degrees] clockwise about their centre (roadmap item 145), with RotSprite for pixel
     * art ([rotSprite]) or plain nearest-neighbour sampling. The selection outline turns with
     * them. Works at 8 bits per channel (as the raster warp does). One undo step.
     */
    fun rotateRasterPixels(degrees: Double, rotSprite: Boolean) {
        val surface = activeRaster ?: return
        val box = selectionPixelBox(surface) ?: paintedPixelBox(surface) ?: return
        val width = box[2] - box[0] + 1
        val height = box[3] - box[1] + 1
        if (width <= 0 || height <= 0) return
        recordHistory()
        // Lift: the picture (inside the selection, if any) as straight 8-bit ARGB, cleared from the layer.
        val lifted = IntArray(width * height)
        surface.clip = if (hasRasterSelection) { x, y -> inRasterSelection(surface, x, y) } else null
        surface.forEachPixelIn(box[0].toDouble(), box[1].toDouble(), box[2].toDouble(), box[3].toDouble()) { tile, index, px, py ->
            val a = RasterSurface.u16(tile.data[index + 3])
            if (a > 0) {
                fun channel(c: Int) = (RasterSurface.u16(tile.data[index + c]).toLong() * 255 / a).toInt().coerceIn(0, 255)
                lifted[(py - box[1]) * width + (px - box[0])] = ((a * 255 / 65535) shl 24) or (channel(0) shl 16) or (channel(1) shl 8) or channel(2)
            }
            for (c in 0 until 4) tile.data[index + c] = 0
        }
        surface.clip = null
        val turned = rotatePixels(PixelImage(width, height, lifted), degrees, rotSprite)
        // Centred where the source was centred.
        val left = box[0] + (width - turned.width) / 2
        val top = box[1] + (height - turned.height) / 2
        surface.forEachPixelIn(left.toDouble(), top.toDouble(), (left + turned.width - 1).toDouble(), (top + turned.height - 1).toDouble()) { tile, index, px, py ->
            val x = px - left
            val y = py - top
            if (x !in 0 until turned.width || y !in 0 until turned.height) return@forEachPixelIn
            val argb = turned.at(x, y)
            val a = (argb ushr 24) / 255.0
            if (a == 0.0) return@forEachPixelIn
            val src = doubleArrayOf(((argb shr 16) and 0xFF) / 255.0 * a, ((argb shr 8) and 0xFF) / 255.0 * a, (argb and 0xFF) / 255.0 * a, a)
            for (c in 0 until 4) tile.data[index + c] = (src[c] * 65535 + RasterSurface.u16(tile.data[index + c]) * (1 - a)).toInt().coerceIn(0, 65535).toShort()
        }
        // The selection outline follows the pixels.
        rasterSelection?.let { outline ->
            val centre = surface.worldOf(box[0] + width / 2.0, box[1] + height / 2.0)
            val radians = Math.toRadians(degrees)
            rasterSelection = outline.map { p ->
                val dx = p.x - centre.x
                val dy = p.y - centre.y
                WorldPoint(centre.x + dx * cos(radians) - dy * sin(radians), centre.y + dx * sin(radians) + dy * cos(radians))
            }
        }
        markRasterDirty(activeLayerId, emptySet())
        rasterRevision++
    }

    /** Copies the selected pixels of the active raster layer onto a new raster layer above it. */
    fun copyRasterSelectionToNewLayer() {
        val source = activeRaster ?: return
        val box = selectionPixelBox(source) ?: return
        val sourceLayer = layers.firstOrNull { it.id == activeLayerId } ?: return
        val copy = RasterSurface(source.depth, source.pixelWorld, source.originX, source.originY)
        val layer = Layer(UUID.randomUUID().toString(), "${sourceLayer.name} (copy)", raster = true, depth = source.depth)
        attachRaster(layer.id, copy)
        copy.clip = { x, y -> inRasterSelection(copy, x, y) }
        copy.forEachPixelIn(box[0].toDouble(), box[1].toDouble(), box[2].toDouble(), box[3].toDouble()) { tile, index, px, py ->
            val p = source.pixel(px, py)
            for (c in 0 until 4) tile.data[index + c] = (p[c] * 65535).toInt().toShort()
        }
        copy.clip = null
        copy.tiles.entries.removeAll { it.value.isEmpty() }
        layers.add(layers.indexOfFirst { it.id == sourceLayer.id } + 1, layer)
        activeLayerId = layer.id
        onLayersChanged(layers.toList())
        markRasterDirty(layer.id, copy.tiles.keys.toSet())
    }

    // An adjustment being previewed: the history depth before it, and its open entry, whose
    // raster log holds each tile as it was before the adjustment.
    private var adjustStartDepth = -1
    private var adjustingEntry: HistoryEntry? = null

    /** Starts previewing adjustments on the active raster layer (within the raster selection, if any). */
    fun beginAdjusting(): Boolean {
        if (activeRaster == null) return false
        adjustStartDepth = undoStack.size
        recordHistory()
        adjustingEntry = openEntry
        return true
    }

    /** Shows [adjustment] applied to the layer as it was when adjusting began. */
    fun previewAdjustment(adjustment: Adjustment) {
        val surface = activeRaster ?: return
        val entry = adjustingEntry ?: return
        for ((ref, tile) in entry.rasterLog) if (rasters[ref.first] === surface) surface.setTile(ref.second, tile?.copy())
        surface.clip = if (hasRasterSelection) { x, y -> inRasterSelection(surface, x, y) } else null
        surface.adjust(adjustment)
        surface.clip = null
        rasterRevision++
    }

    /** Keeps the previewed adjustment as one undo step. */
    fun commitAdjustment() {
        adjustingEntry = null
        markRasterDirty(activeLayerId, emptySet())
    }

    /** Drops the previewed adjustment, leaving the layer as it was. */
    fun cancelAdjustment() {
        if (adjustingEntry == null) return
        adjustingEntry = null
        discardActionsSince(adjustStartDepth)
    }

    // Moving selected pixels: what was lifted (premultiplied, by pixel), and each tile as it was
    // after the lift, so every move step repaints from the same start.
    private var floatingPixels: Map<Pair<Int, Int>, FloatArray>? = null
    private var floatingBase = HashMap<Long, RasterTile?>()
    private var floatingOutline: List<WorldPoint> = emptyList()
    private var floatingStart = WorldPoint.Zero
    private var floatingMaskOrigin: WorldPoint? = null

    private fun beginMovingSelectedPixels(at: WorldPoint): Boolean {
        val surface = activeRaster ?: return false
        if (!hasRasterSelection || paintingSelection) return false
        val outline = rasterSelection
        val atPixel = surface.pixelOf(at)
        if (if (outline != null) !polygonContains(outline, at) else !inRasterSelection(surface, atPixel.first, atPixel.second)) return false
        val box = selectionPixelBox(surface) ?: return false
        recordHistory()
        val lifted = HashMap<Pair<Int, Int>, FloatArray>()
        surface.clip = { x, y -> inRasterSelection(surface, x, y) }
        surface.forEachPixelIn(box[0].toDouble(), box[1].toDouble(), box[2].toDouble(), box[3].toDouble()) { tile, index, px, py ->
            val p = FloatArray(4) { RasterSurface.u16(tile.data[index + it]) / 65535f }
            if (p[3] > 0f) lifted[px to py] = p
            for (c in 0 until 4) tile.data[index + c] = 0
        }
        surface.clip = null
        floatingPixels = lifted
        floatingBase = HashMap()
        floatingOutline = outline ?: emptyList()
        floatingMaskOrigin = selectionMask?.let { WorldPoint(it.originX, it.originY) }
        floatingStart = at
        return true
    }

    private fun moveSelectedPixels(to: WorldPoint) {
        val surface = activeRaster ?: return
        val lifted = floatingPixels ?: return
        // Whole pixels only, so moved paint stays sharp.
        val dx = Math.round((to.x - floatingStart.x) / surface.pixelWorld).toInt()
        val dy = Math.round((to.y - floatingStart.y) / surface.pixelWorld).toInt()
        for ((key, tile) in floatingBase) surface.setTile(key, tile?.copy())
        val before = surface.beforeTileChange
        surface.beforeTileChange = { key ->
            before(key)
            if (key !in floatingBase) floatingBase[key] = surface.tileAt(key)?.copy()
        }
        for ((pixel, p) in lifted) {
            val (px, py) = pixel
            surface.forEachPixelIn((px + dx).toDouble(), (py + dy).toDouble(), (px + dx).toDouble(), (py + dy).toDouble()) { tile, index, x, y ->
                if (x != px + dx || y != py + dy) return@forEachPixelIn
                val a = p[3]
                for (c in 0 until 4) tile.data[index + c] = (p[c] * 65535 + RasterSurface.u16(tile.data[index + c]) * (1 - a)).toInt().coerceIn(0, 65535).toShort()
            }
        }
        surface.beforeTileChange = before
        val mask = selectionMask
        val maskOrigin = floatingMaskOrigin
        if (mask != null && maskOrigin != null) {
            // The painted selection moves with its pixels.
            mask.originX = maskOrigin.x + dx * surface.pixelWorld
            mask.originY = maskOrigin.y + dy * surface.pixelWorld
        } else {
            rasterSelection = floatingOutline.map { WorldPoint(it.x + dx * surface.pixelWorld, it.y + dy * surface.pixelWorld) }
        }
        rasterRevision++
    }

    private fun endMovingSelectedPixels() {
        floatingPixels = null
        floatingBase = HashMap()
        markRasterDirty(activeLayerId, emptySet())
    }

    /** True when the active layer holds pixels, so the pen and eraser work on them. */
    val activeLayerIsRaster: Boolean get() = activeRaster != null

    /** How the pen acts on a raster layer: paint, wet mixing, smudge or blur, and its tip (roadmap item 90). */
    var rasterBrush by mutableStateOf(RasterBrush())

    // The colour a wet brush carries, picked up from the canvas as it goes; null when not painting wet.
    private var wetColor: FloatArray? = null

    // The raster brush stroke in progress: its last point, and how far along the segment the
    // next dab falls (dabs are evenly spaced across points).
    private var rasterLast: WorldPoint? = null
    private var rasterLastDab: WorldPoint? = null
    private var rasterCarry = 0.0
    // Brush engines' place along the stroke (roadmap item 140): distance so far, dabs so far, and the stroke's hair pattern.
    private var rasterTravelled = 0.0
    private var rasterDabIndex = 0
    private var rasterStrokeSeed = 0
    private var rasterErasing = false

    /** Radius, opacity and hardness of the raster brush for the current pen at [pressure]. */
    private fun rasterBrush(pressure: Float): Triple<Double, Float, Float> {
        val radius = activeWidthWorld / inputScale / 2 * pressure.coerceIn(0.1f, 1f)
        return when (activePenType) {
            PenType.AIRBRUSH -> Triple(radius * 3, 0.08f, 0f)
            PenType.PENCIL -> Triple(radius * 0.6, 0.85f, 1f)
            PenType.HIGHLIGHTER -> Triple(radius * 2, 0.35f, 1f)
            else -> Triple(radius, 1f, 0.8f)
        }
    }

    private fun rasterDabs(surface: RasterSurface, to: WorldPoint, pressure: Float) {
        val (radius, opacity, hardness) = if (rasterErasing) Triple(eraserRadiusWorld, 1f, 0.9f) else rasterBrush(pressure)
        // A mask is painted in coverage alone: the pen hides fully, whatever the colour.
        val color = when {
            editingMask || paintingSelection -> floatArrayOf(0f, 0f, 0f, 1f)
            // HDR: the colour's light times the intensity, in the layer's scaled-down storage.
            surface.hdr -> activeColor.let { c ->
                fun light(v: Float) = (linearToSrgb(srgbToLinear(v) * hdrIntensity) / HDR_ENCODED_MAX).coerceIn(0f, 1f)
                floatArrayOf(light(c.red), light(c.green), light(c.blue), c.alpha * activeOpacity.toFloat())
            }
            else -> activeColor.let { floatArrayOf(it.red, it.green, it.blue, it.alpha * activeOpacity.toFloat()) }
        }
        val spacing = (radius * RASTER_DAB_SPACING).coerceAtLeast(surface.pixelWorld * 0.5)
        val from = rasterLast ?: to
        val dabs = dabsAlong(from, to, spacing, rasterCarry)
        val brush = if (editingMask || paintingSelection) RasterBrush(tip = rasterBrush.tip) else rasterBrush
        var previous = rasterLastDab ?: from
        for (p in dabs) {
            when {
                rasterErasing -> surface.dab(p, radius, color, opacity, hardness, erase = true, tip = brush.tip)
                brush.mode == RasterBrushMode.SMUDGE -> surface.smudge(previous, p, radius, brush.strength, brush.tip)
                brush.mode == RasterBrushMode.BLUR -> surface.blur(p, radius, brush.strength, brush.tip)
                brush.mode == RasterBrushMode.LIQUIFY -> surface.liquify(previous, p, radius, brush.strength, brush.tip)
                brush.mode == RasterBrushMode.SPRAY -> {
                    val grain = sprayParticleRadius(radius, surface.pixelWorld)
                    for (particle in sprayParticles(p, radius, rasterStrokeSeed * 31 + rasterDabIndex)) surface.dab(particle, grain, color, opacity, 0.5f)
                }
                brush.mode == RasterBrushMode.BRISTLE -> {
                    val hair = bristleRadius(radius, surface.pixelWorld)
                    for ((at, paint) in bristleHairs(previous, p, radius, rasterTravelled, rasterStrokeSeed)) {
                        if (paint > 0f) surface.dab(at, hair, color, opacity * paint, 0.9f)
                    }
                }
                brush.mode == RasterBrushMode.WET -> {
                    // Pick up some of what's underneath, then lay the mixture down.
                    val carried = wetColor ?: color.copyOf().also { wetColor = it }
                    val under = surface.sampleUnder(p, radius)
                    val pickup = brush.wetness * under[3]
                    for (c in 0 until 3) carried[c] = carried[c] * (1 - pickup) + under[c] * pickup
                    surface.dab(p, radius, carried, opacity, hardness, tip = brush.tip)
                }
                else -> surface.dab(p, radius, color, opacity, hardness, tip = brush.tip)
            }
            rasterTravelled += hypot(p.x - previous.x, p.y - previous.y)
            rasterDabIndex++
            previous = p
        }
        if (dabs.isNotEmpty()) rasterLastDab = dabs.last()
        // Distance past [to] where the next dab falls, so spacing stays even across segments.
        rasterCarry += spacing * dabs.size - hypot(to.x - from.x, to.y - from.y)
        rasterLast = to
        rasterRevision++
    }

    private fun beginRasterStroke(surface: RasterSurface, at: WorldPoint, pressure: Float, erasing: Boolean) {
        rasterErasing = erasing
        // The selection limits painting; alpha lock keeps coverage (not on a mask, which is all coverage).
        // Painting the selection itself isn't limited by it.
        surface.clip = if (hasRasterSelection && !paintingSelection) { x, y -> inRasterSelection(surface, x, y) } else null
        surface.alphaLock = !editingMask && !paintingSelection && layers.firstOrNull { it.id == activeLayerId }?.alphaLocked == true
        rasterLast = null
        rasterLastDab = null
        wetColor = null
        rasterCarry = 0.0
        rasterTravelled = 0.0
        rasterDabIndex = 0
        rasterStrokeSeed = (System.nanoTime() and 0x7FFFFFFF).toInt() // each bristle stroke's hairs differ
        rasterDabs(surface, at, pressure)
    }

    private fun endRasterStroke() {
        activeRaster?.let { it.clip = null; it.alphaLock = false }
        rasterLast = null
        markRasterDirty(activeLayerId, emptySet())
    }

    /**
     * Adds [document]'s layers (from a PSD, Krita or Procreate file, roadmap item 95) as raster
     * layers above the others, the picture centred on the view at one pixel per screen pixel.
     * Their names, visibility, opacity and blend modes come along.
     */
    fun importLayers(document: ImportedDocument) {
        if (document.layers.isEmpty()) return
        val pixelWorld = 1.0 / scale.coerceAtLeast(1e-300)
        val originX = panWorld.x - document.width / 2.0 * pixelWorld
        val originY = panWorld.y - document.height / 2.0 * pixelWorld
        for (imported in document.layers) {
            val layer = Layer(
                UUID.randomUUID().toString(), imported.name.ifBlank { "Layer ${layers.size + 1}" },
                visible = imported.visible, opacity = imported.opacity, blend = imported.blend, raster = true, depth = document.depth,
                hdr = document.hdr,
            )
            val surface = RasterSurface(document.depth, pixelWorld, originX, originY).also { it.hdr = document.hdr; it.hdrView = hdrView }
            attachRaster(layer.id, surface)
            surface.forEachPixelIn(imported.left.toDouble(), imported.top.toDouble(), (imported.left + imported.width - 1).toDouble(), (imported.top + imported.height - 1).toDouble()) { tile, index, px, py ->
                val x = px - imported.left
                val y = py - imported.top
                if (x !in 0 until imported.width || y !in 0 until imported.height) return@forEachPixelIn
                val i = (y * imported.width + x) * 4
                val a = RasterSurface.u16(imported.rgba16[i + 3])
                if (a == 0) return@forEachPixelIn
                for (c in 0 until 3) tile.data[index + c] = (RasterSurface.u16(imported.rgba16[i + c]).toLong() * a / 65535).toInt().toShort()
                tile.data[index + 3] = a.toShort()
            }
            surface.tiles.entries.removeAll { it.value.isEmpty() }
            layers.add(layer)
            activeLayerId = layer.id
            markRasterDirty(layer.id, surface.tiles.keys.toSet())
        }
        onLayersChanged(layers.toList())
    }

    /**
     * Adds a file layer (roadmap item 138) showing [argb] (non-premultiplied ARGB, [width] x
     * [height], read from [source]) centred in the view, one image pixel per screen pixel at the
     * current zoom, locked against painting. Its surface's origin is the image's top-left, so a
     * reload repaints from pixel (0, 0).
     */
    fun addFileLayer(name: String, source: String, argb: IntArray, width: Int, height: Int) {
        val pixelWorld = 1.0 / scale.coerceAtLeast(1e-300)
        val surface = RasterSurface(8, pixelWorld, panWorld.x - width / 2.0 * pixelWorld, panWorld.y - height / 2.0 * pixelWorld)
        val layer = Layer(UUID.randomUUID().toString(), name.ifBlank { "File ${layers.size + 1}" }, locked = true, raster = true, fileSource = source)
        attachRaster(layer.id, surface)
        paintImage(surface, argb, width, height)
        layers.add(layer)
        activeLayerId = layer.id
        markRasterDirty(layer.id, surface.tiles.keys.toSet())
        onLayersChanged(layers.toList())
    }

    /**
     * Replaces file layer [id]'s pixels with the file as it is now. [recordUndo] false for the
     * automatic reload when a document opens, which isn't an edit to undo.
     */
    fun reloadFileLayer(id: String, argb: IntArray, width: Int, height: Int, recordUndo: Boolean = true) {
        val surface = rasters[id] ?: return
        if (layers.none { it.id == id && it.fileSource != null }) return
        if (recordUndo) recordHistory()
        val old = surface.tiles.keys.toSet()
        for (key in old) {
            surface.beforeTileChange(key)
            surface.setTile(key, null)
        }
        paintImage(surface, argb, width, height)
        markRasterDirty(id, old + surface.tiles.keys)
    }

    /** Paints [argb] (non-premultiplied) onto [surface] with its top-left at pixel (0, 0). */
    private fun paintImage(surface: RasterSurface, argb: IntArray, width: Int, height: Int) {
        surface.forEachPixelIn(0.0, 0.0, (width - 1).toDouble(), (height - 1).toDouble()) { tile, index, px, py ->
            if (px !in 0 until width || py !in 0 until height) return@forEachPixelIn
            val c = argb[py * width + px]
            val a = c ushr 24
            if (a == 0) return@forEachPixelIn
            fun premultiplied(shift: Int) = (((c shr shift) and 0xFF) * a * 65535L / (255 * 255)).toInt().toShort()
            tile.data[index] = premultiplied(16)
            tile.data[index + 1] = premultiplied(8)
            tile.data[index + 2] = premultiplied(0)
            tile.data[index + 3] = (a * 257).toShort()
        }
        surface.tiles.entries.removeAll { it.value.isEmpty() }
    }

    /**
     * How HDR layers (roadmap item 139) are shown: exposure and tone mapping, standing in for an
     * OpenColorIO display transform. A view setting: it changes what's on screen, never pixels.
     */
    var hdrView by mutableStateOf(HdrView())
        private set

    fun changeHdrView(view: HdrView) {
        hdrView = view
        for (layer in layers) if (layer.hdr) rasters[layer.id]?.hdrView = view
        rasterRevision++
    }

    /** How many times brighter than its colour the pen paints on an HDR layer: 1 is the colour as picked. */
    var hdrIntensity by mutableFloatStateOf(1f)

    /** Adds an adjustment layer applying [adjustment] to everything beneath it (roadmap item 93). */
    fun addAdjustmentLayer(adjustment: Adjustment) {
        val layer = Layer(UUID.randomUUID().toString(), "Adjustment ${layers.size + 1}", adjustment = adjustment)
        layers.add(layer)
        activeLayerId = layer.id
        onLayersChanged(layers.toList())
    }

    /** The document's skins (roadmap item 86), in layer order. */
    val skins: List<String> get() = layers.mapNotNull { it.skin }.distinct()

    /** The skin on show: the one whose layers are visible while every other skin's are hidden; null if none or mixed. */
    val activeSkin: String?
        get() = skins.singleOrNull { name -> layers.all { it.skin == null || it.visible == (it.skin == name) } }

    /** Shows skin [name]'s layers and hides every other skin's; layers outside skins are left alone. */
    fun applySkin(name: String) {
        var changed = false
        for (i in layers.indices) {
            val layer = layers[i]
            if (layer.skin == null || layer.visible == (layer.skin == name)) continue
            layers[i] = layer.copy(visible = layer.skin == name)
            changed = true
        }
        if (changed) onLayersChanged(layers.toList())
    }

    /** The document's layer comps (roadmap item 123), in the order they were saved. */
    val layerComps = mutableStateListOf<LayerComp>()

    /** Replaces the layer comps (from [CanvasStorage.loadLayerComps]). */
    fun loadLayerComps(loaded: List<LayerComp>) {
        layerComps.clear()
        layerComps.addAll(loaded)
    }

    /** Saves the layers as they're shown now as a new comp named [name]; returns its id. */
    fun saveLayerComp(name: String): String {
        val comp = captureLayerComp(UUID.randomUUID().toString(), name, layers)
        layerComps.add(comp)
        onLayerCompsChanged(layerComps.toList())
        return comp.id
    }

    /** Shows the layers the way comp [id] records. */
    fun applyLayerComp(id: String) {
        val comp = layerComps.firstOrNull { it.id == id } ?: return
        val applied = layersShownAs(comp, layers)
        if (applied == layers.toList()) return
        for (i in layers.indices) if (layers[i] != applied[i]) layers[i] = applied[i]
        onLayersChanged(layers.toList())
    }

    /** Overwrites comp [id] with the layers as they're shown now, keeping its name. */
    fun updateLayerComp(id: String) = changeLayerComp(id) { captureLayerComp(it.id, it.name, layers) }

    fun renameLayerComp(id: String, name: String) = changeLayerComp(id) { it.copy(name = name) }

    fun deleteLayerComp(id: String) {
        if (layerComps.removeAll { it.id == id }) onLayerCompsChanged(layerComps.toList())
    }

    private fun changeLayerComp(id: String, change: (LayerComp) -> LayerComp) {
        val index = layerComps.indexOfFirst { it.id == id }
        if (index == -1) return
        layerComps[index] = change(layerComps[index])
        onLayerCompsChanged(layerComps.toList())
    }

    /** The comp the layers are currently shown as, if any - the first that matches. */
    val currentLayerCompId: String?
        get() = layerComps.firstOrNull { layerCompMatches(it, layers) }?.id

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
        dropRaster(id)
        dropRaster(maskIdOf(id))
        if (activeLayerId == id) activeLayerId = layers.last().id
        strokesChanged()
        onLayersChanged(layers.toList())
    }

    /**
     * Merges raster layer [upper] onto raster layer [lower] (source-over, at the upper layer's
     * opacity), when both share one pixel grid; a vector layer and a raster layer don't merge
     * (that would need the strokes rasterized). Undoable for the pixels.
     */
    private fun mergeRasterDown(upper: Layer, lower: Layer) {
        val top = rasters[upper.id] ?: return
        val bottom = rasters[lower.id] ?: return
        if (!upper.raster || !lower.raster || top.pixelWorld != bottom.pixelWorld) return
        val dx = (top.originX - bottom.originX) / top.pixelWorld
        val dy = (top.originY - bottom.originY) / top.pixelWorld
        if (dx != Math.rint(dx) || dy != Math.rint(dy)) return
        recordHistory()
        for ((key, tile) in top.tiles.toList()) {
            val left = RasterSurface.tileX(key) * RASTER_TILE + dx
            val topEdge = RasterSurface.tileY(key) * RASTER_TILE + dy
            bottom.forEachPixelIn(left, topEdge, left + RASTER_TILE - 1, topEdge + RASTER_TILE - 1) { target, index, px, py ->
                val sx = px - left.toInt()
                val sy = py - topEdge.toInt()
                if (sx !in 0 until RASTER_TILE || sy !in 0 until RASTER_TILE) return@forEachPixelIn
                val source = (sy * RASTER_TILE + sx) * 4
                val a = RasterSurface.u16(tile.data[source + 3]) / 65535.0 * upper.opacity
                if (a == 0.0) return@forEachPixelIn
                for (c in 0 until 4) {
                    val src = RasterSurface.u16(tile.data[source + c]) * upper.opacity
                    target.data[index + c] = (src + RasterSurface.u16(target.data[index + c]) * (1 - a)).toInt().coerceIn(0, 65535).toShort()
                }
            }
        }
        layers.removeWhere { it.id == upper.id }
        dropRaster(upper.id)
        if (activeLayerId == upper.id) activeLayerId = lower.id
        onLayersChanged(layers.toList())
    }

    // Raster layers deleted since the last save, whose stored pixels go too.
    private val deletedRasterLayers = HashSet<String>()

    private fun dropRaster(layerId: String) {
        if (rasters.remove(layerId) == null) return
        dirtyRasterTiles.remove(layerId)
        deletedRasterLayers.add(layerId)
        markRasterDirty(activeLayerId, emptySet())
    }

    /** Raster layers deleted since the last call, whose stored pixels should be removed. */
    fun takeDeletedRasterLayers(): Set<String> = deletedRasterLayers.toSet().also { deletedRasterLayers.clear() }

    /** Removes layers with no strokes in any animation frame, keeping at least one layer. */
    fun deleteEmptyLayers() {
        // A raster layer with paint isn't empty, though it has no strokes.
        val used = strokes.map { it.layerId }.toSet() + rasters.filterValues { it.tiles.isNotEmpty() }.keys + layers.filter { it.adjustment != null }.map { it.id }
        val keep = layers.filter { it.id in used }.ifEmpty { listOf(layers.first()) }
        if (keep.size == layers.size) return
        for (layer in layers) if (layer !in keep) dropRaster(layer.id)
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
        hidden.forEach(::dropRaster)
        if (activeLayerId in hidden) activeLayerId = layers.last().id
        strokesChanged()
        onLayersChanged(layers.toList())
    }

    /** Moves every stroke of a layer onto the layer below it, then removes the emptied layer. */
    fun mergeLayerDown(id: String) {
        val index = layers.indexOfFirst { it.id == id }
        if (index <= 0) return
        val below = layers[index - 1]
        val layer = layers[index]
        if (layer.raster || below.raster) {
            mergeRasterDown(layer, below)
            return
        }
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
        val initialFeatures: Set<OpenTypeFeature> = emptySet(),
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
    // An entry holds the stroke list as it was (the snapshot list's immutable instance, O(1))
    // plus a [Stroke.MutationLog] of the geometry strokes had before their first in-place change
    // after the entry was made. Restoring an entry puts the list back and replays its log. This
    // costs O(strokes changed) per action; capturing every stroke's geometry per entry cost
    // ~0.3 s an action at a million strokes (ScaleBenchmark, roadmap item 18).
    //
    // Every entry opens a new log and makes it the active one ([openLog]), so the logs of the
    // surviving entries, ordered by [seq], cover the whole time since the oldest; a stroke's
    // geometry at an entry is its earliest record in that entry's or any later entry's log, or its
    // current geometry if none has one.
    private class HistoryEntry(
        val strokes: List<Stroke>,
        var images: List<ImageItem>?,
        var texts: List<TextItem>? = null,
        val connectors: List<Connector>? = null,
        var bones: List<Bone>? = null,
        val holds: Map<Int, Int>? = null,
    ) {
        val log = Stroke.MutationLog()

        // Raster tiles as they were before their first change after this entry (null: the
        // tile didn't exist), by layer and tile key; the raster counterpart of [log].
        val rasterLog = HashMap<Pair<String, Long>, RasterTile?>()
        val seq = nextSeq++

        companion object {
            private var nextSeq = 0L

            fun of(strokes: List<Stroke>, images: List<ImageItem>?, texts: List<TextItem>? = null, connectors: List<Connector>? = null, bones: List<Bone>? = null, holds: Map<Int, Int>? = null) =
                HistoryEntry(strokes.toList(), images, texts, connectors = connectors, bones = bones, holds = holds)
        }
    }

    /**
     * Moves strokes that undo or redo would bring back but that aren't on the canvas now (erased
     * or cleared ones), so they come back in the rebased world space too. Found per entry as
     * the run between its common prefix and suffix with the live list, so a typical history
     * costs a reference scan, not a set of every stroke.
     */
    private fun shiftStrokesOnlyInHistory(offset: WorldPoint) {
        val live = strokes.toList()
        var liveSet: MutableSet<Stroke>? = null
        val shifted = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<Stroke, Boolean>())
        for (entry in undoStack + redoStack) {
            val list = entry.strokes
            if (list === live) continue
            val shorter = minOf(list.size, live.size)
            var prefix = 0
            while (prefix < shorter && list[prefix] === live[prefix]) prefix++
            var suffix = 0
            while (suffix < shorter - prefix && list[list.size - 1 - suffix] === live[live.size - 1 - suffix]) suffix++
            if (prefix + suffix == list.size) continue
            // A stroke in the entry's run that is live is in the live list's run too (a stroke
            // appears once per list), so only that run needs checking.
            val liveRun = if (live.size - prefix - suffix <= MAX_INDEX_PATCH) {
                java.util.Collections.newSetFromMap(java.util.IdentityHashMap<Stroke, Boolean>()).also { set -> for (j in prefix until live.size - suffix) set.add(live[j]) }
            } else {
                liveSet ?: java.util.Collections.newSetFromMap(java.util.IdentityHashMap<Stroke, Boolean>()).also { set -> set.addAll(live); liveSet = set }
            }
            for (j in prefix until list.size - suffix) {
                val stroke = list[j]
                if (stroke !in liveRun && shifted.add(stroke)) stroke.shiftBy(offset)
            }
        }
    }

    /** Makes [entry]'s log the one in-place stroke changes are recorded in from now on. */
    private fun openLog(entry: HistoryEntry?) {
        openEntry = entry
        Stroke.activeLog = entry?.log
    }

    /** Independent strokes holding [entry]'s geometry, e.g. for saving history. */
    private fun materialize(entry: HistoryEntry): List<Stroke> = Stroke.withoutLogging {
        val earliest = java.util.IdentityHashMap<Stroke, Stroke>()
        // Latest log first, so each stroke ends with its earliest record at or after [entry].
        for (later in (undoStack + redoStack).filter { it.seq >= entry.seq }.sortedByDescending { it.seq }) later.log.earliestInto(earliest)
        entry.strokes.map { earliest[it] ?: it.deepCopy() }
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
        val list = strokes.toList()
        return positionsNear(list, left, top, right, bottom).map { list[it] }
    }

    /** [strokesNear] as ascending positions in [list], which must be the current `strokes.toList()`. */
    private fun positionsNear(list: List<Stroke>, left: Double, top: Double, right: Double, bottom: Double): IntArray {
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
        if (live.isEmpty()) return found
        val positions = java.util.TreeSet<Int>()
        found.forEach(positions::add)
        // Searched from the end: the stroke being drawn and fresh pastes/duplicates are last.
        for (stroke in live) {
            var at = list.size - 1
            while (at >= 0 && list[at] !== stroke) at--
            if (at >= 0) positions.add(at)
        }
        return positions.toIntArray()
    }

    /** What the rendered scene depends on, for [SceneTileCache]; comparing two is O(1). */
    fun sceneVersion() = SceneVersion(
        listOf(strokes.toList(), geometryEpoch, rasterRevision, layers.toList(), textItems.toList(), imageItems.toList(), connectors.toList(), backgroundColor, backgroundStyle, currentAnimFrame),
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
            !isErasing && !isMovingImage && !isMovingText && !animationMode && replayCount == null && audioReplay == null

    private var isErasing = false

    private val undoStack = ArrayDeque<HistoryEntry>()
    private val redoStack = ArrayDeque<HistoryEntry>()

    private fun currentEntry() = HistoryEntry.of(strokes.toList(), imageItems.toList(), textItems.toList(), connectors.toList(), bones.toList(), frameHolds.toMap())

    /** Called at the start of every stroke-changing action; also invalidates redo, standard editor semantics. */
    private fun recordHistory() {
        val entry = currentEntry()
        undoStack.addLast(entry)
        while (undoStack.size > MAX_UNDO_STEPS) undoStack.removeFirst()
        redoStack.clear()
        openLog(entry)
    }

    /** Undo and redo stroke snapshots, oldest first, for [CanvasStorage.saveHistory]. */
    fun historySnapshot(): Pair<List<List<Stroke>>, List<List<Stroke>>> {
        // Materialized per step on access, so a caller keeping only the newest few steps (as
        // CanvasStorage.saveHistory does) doesn't build strokes for the rest. Read it right away:
        // it reflects the stacks as they are when each step is read.
        fun lazySteps(entries: ArrayDeque<HistoryEntry>) = object : AbstractList<List<Stroke>>() {
            override val size get() = entries.size
            // Each step reports its size without building strokes; they're built on first read.
            override fun get(index: Int): List<Stroke> {
                val entry = entries[index]
                return object : AbstractList<Stroke>() {
                    private val built by lazy { materialize(entry) }
                    override val size get() = entry.strokes.size
                    override fun get(index: Int) = built[index]
                }
            }
        }
        return lazySteps(undoStack) to lazySteps(redoStack)
    }

    /** Restores history saved by [CanvasStorage.saveHistory]; call after [loadStrokes], which clears it. */
    fun loadHistory(undo: List<List<Stroke>>, redo: List<List<Stroke>>) {
        undoStack.clear()
        undoStack.addAll(undo.takeLast(MAX_UNDO_STEPS).map { HistoryEntry.of(it, null) })
        redoStack.clear()
        redoStack.addAll(redo.map { HistoryEntry.of(it, null) })
        // Changes from here on belong after the newest step.
        openLog(listOf(undoStack.lastOrNull(), redoStack.lastOrNull()).filterNotNull().maxByOrNull { it.seq })
    }

    private fun restore(snapshot: List<Stroke>) = restore(HistoryEntry.of(snapshot, null))

    private fun restore(entry: HistoryEntry) {
        // Raster tiles back as they were; restoring logs the current ones into the open entry,
        // so redo can put them back.
        if (entry.rasterLog.isNotEmpty()) {
            for ((ref, tile) in entry.rasterLog) {
                val surface = rasters[ref.first] ?: continue
                recordRasterTile(ref.first, surface, ref.second)
                surface.setTile(ref.second, tile?.copy())
            }
            rasterRevision++
            onRasterChanged()
        }
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
        // Strokes changed in place since the entry get their geometry back; the list itself is
        // only rewritten when it differs (a SnapshotStateList rewrite is ~15 ms per million).
        entry.log.restore()
        if (strokes.toList() !== entry.strokes) {
            strokes.clear()
            strokes.addAll(entry.strokes)
        }
        // Undoing a frame insert/delete changes how many frames have strokes; keep the count in step.
        animFrameCount = maxOf(strokes.maxOfOrNull { it.animFrame } ?: 0, bones.maxOfOrNull { it.animFrame } ?: 0) + 1
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
        activeRaster?.let { surface ->
            beginRasterStroke(surface, worldPoint, pressure, erasing = false)
            return
        }
        // Indexed palette (roadmap item 104): pixel art stays within the saved colours.
        val inkColor = if (activePenType == PenType.PIXEL && indexedPalette) nearestPaletteColor(activeColor) else activeColor
        val stroke = Stroke(
            inkColor.copy(alpha = inkColor.alpha * activeOpacity.toFloat()),
            activeWidthWorld / inputScale,
            activePenType,
            blendMode = activeBlendMode,
            layerId = activeLayerId,
            animFrame = currentAnimFrame,
            dashed = activeDashed,
            lineStyle = activeLineStyle.takeIf { it != LineStyle() },
            brush = if (activePenType == PenType.STAMP) activeBrush else null,
        )
        stroke.addPoint(snappedToActiveGuide(worldPoint), pressure, tilt, orientation)
        stroke.setDrawnAt(strokeStartMillis)
        rememberActiveColor()
        lastStrokeMoveMillis = System.currentTimeMillis()
        lastPointMillis = lastStrokeMoveMillis
        currentStroke = stroke
        strokes.add(stroke)
        symmetryCopies = symmetryTransforms(symmetryMode, symmetryCenter).map { transform ->
            val copy = Stroke(stroke.color, stroke.widthWorld, stroke.penType, blendMode = stroke.blendMode, layerId = stroke.layerId, animFrame = stroke.animFrame, dashed = stroke.dashed, brush = stroke.brush)
            copy.addPoint(transform(stroke.points.first()), pressure, tilt, orientation)
            copy.setDrawnAt(strokeStartMillis)
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
        if (rasterLast != null) {
            activeRaster?.let { rasterDabs(it, worldPoint, pressure) }
            return
        }
        draggingVanishingPoint?.let { index ->
            vanishingPoints[index] = worldPoint
            return
        }
        val stroke = currentStroke ?: return
        liveShape?.let { live ->
            val adjusted = adjustRecognizedShape(live.clean, live.snappedAt, worldPoint)
            replaceLiveShape(adjusted)
            return
        }
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
    private fun replaceWithShape(stroke: Stroke, cleanPoints: List<WorldPoint>): Stroke? {
        val index = strokes.indexOf(stroke)
        if (index == -1) return null
        val shape = Stroke(stroke.color, stroke.widthWorld, stroke.penType, blendMode = stroke.blendMode, layerId = stroke.layerId, animFrame = stroke.animFrame, dashed = stroke.dashed, brush = stroke.brush)
        for (point in cleanPoints) shape.addPoint(point)
        shape.setDrawnAt(stroke.drawnAt)
        strokes[index] = shape
        return shape
    }

    /** A shape recognised while the pen is still down: its clean points and the pen position at the snap. */
    private class LiveShape(val clean: List<WorldPoint>, val snappedAt: WorldPoint)

    private var liveShape: LiveShape? = null

    /**
     * Called periodically while a pen stroke is in progress (roadmap item 118, QuickShape's live
     * snap): once the pen has held still for [SHAPE_HOLD_MILLIS] the stroke snaps to the shape
     * it resembles right away, before the pen lifts, and from then on moving the pen adjusts
     * that shape (see [adjustRecognizedShape]) instead of drawing. Lifting commits it.
     * Returns true when the stroke snapped on this call.
     */
    fun holdTick(now: Long = System.currentTimeMillis()): Boolean {
        val stroke = currentStroke ?: return false
        if (liveShape != null || !strokeMovedAway || now - lastStrokeMoveMillis < SHAPE_HOLD_MILLIS) return false
        val clean = recognizeShape(stroke.points) ?: return false
        liveShape = LiveShape(clean, lastRawPoint ?: stroke.points.last())
        replaceLiveShape(clean)
        return true
    }

    /** True while the stroke in progress has snapped to a shape the pen is adjusting (see [holdTick]). */
    val isAdjustingShape: Boolean get() = liveShape != null

    private fun replaceLiveShape(clean: List<WorldPoint>) {
        val stroke = currentStroke ?: return
        currentStroke = replaceWithShape(stroke, clean) ?: return
        // Copies get the same clean shape, transformed, so symmetry still holds.
        symmetryCopies = symmetryCopies.map { (copy, transform) -> (replaceWithShape(copy, clean.map(transform)) ?: copy) to transform }
        geometryEpoch++ // redraw; autosave waits for the lift (endStroke)
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
        if (rasterLast != null) {
            endRasterStroke()
            countAction(StatsAction.DRAW, strokeStartMillis)
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
        if (stroke != null && raw != null && activeStabilization > 0.0 && liveShape == null) {
            // Smoothing trails behind the pen; close the gap so the stroke ends at the lift point.
            addStrokePoint(stroke, guided(raw), stroke.pressures.last(), stroke.tilts.last(), stroke.orientations.last())
        }
        if (stroke != null && liveShape == null && System.currentTimeMillis() - lastStrokeMoveMillis >= SHAPE_HOLD_MILLIS) {
            recognizeShape(stroke.points)?.let { clean ->
                currentStroke = replaceWithShape(stroke, clean) ?: stroke
                // Copies get the same clean shape, transformed, so symmetry still holds.
                symmetryCopies = symmetryCopies.map { (copy, transform) -> (replaceWithShape(copy, clean.map(transform)) ?: copy) to transform }
            }
        }
        // Wrap-around: ink drawn on a repeat belongs to the tile itself.
        wrapTile?.let { tile ->
            for (s in listOfNotNull(currentStroke) + symmetryCopies.map { it.first }) {
                val shift = wrappedIntoTile(tile, s.points.firstOrNull() ?: continue)
                if (shift != WorldPoint.Zero) s.shiftBy(shift)
            }
        }
        symmetryCopies = emptyList()
        liveShape = null
        lastRawPoint = null
        holdAnchor = null
        currentStroke = null
        if (stroke != null) countAction(StatsAction.DRAW, strokeStartMillis)
        rebaseIfNeeded()
        strokesChanged()
    }

    /** World-space radius of the eraser at the current zoom level. */
    private val eraserRadiusWorld: Double
        get() = ERASER_RADIUS_SCREEN / inputScale

    // The zoom of the view the pen is drawing in: the main view's, or the second view's while
    // it forwards a stroke (see [withInputScale]). Pen width and eraser size are on-screen
    // sizes, so they follow the view drawn in.
    private var inputScaleOverride: Double? = null
    private val inputScale: Double get() = (inputScaleOverride ?: scale).coerceAtLeast(1e-300)

    /** Runs [block] (a pen or eraser call from the second view) as if drawn at zoom [viewScale]. */
    fun <T> withInputScale(viewScale: Double, block: () -> T): T {
        inputScaleOverride = viewScale
        try {
            return block()
        } finally {
            inputScaleOverride = null
        }
    }

    /**
     * The second view (roadmap item 124): the document in a separate pane with its own pan and
     * zoom - see [SecondView]. View state only; not saved.
     */
    var secondViewOpen by mutableStateOf(false)

    /**
     * Everything the floating origin has shifted the world by so far ([rebaseIfNeeded]). Views
     * with their own camera, like the second view, subtract changes in it from their centre to
     * stay on the same content.
     */
    var originShift by mutableStateOf(WorldPoint.Zero)
        private set

    fun beginErase(worldPoint: WorldPoint) {
        isErasing = true
        eraseStartMillis = System.currentTimeMillis()
        eraseStartDepth = undoStack.size
        recordHistory()
        clearSelectionState() // avoid a stale selection referencing strokes this erase is about to split/remove
        val surface = activeRaster
        if (surface != null && activeLayerEditable) {
            beginRasterStroke(surface, worldPoint, 1f, erasing = true)
            return
        }
        if (lassoErase) lassoPath.add(worldPoint) else eraseAt(worldPoint)
    }

    fun continueErase(worldPoint: WorldPoint) {
        if (rasterLast != null) {
            activeRaster?.let { rasterDabs(it, worldPoint, 1f) }
            return
        }
        if (lassoErase) {
            if (lassoPath.isNotEmpty()) lassoPath.add(worldPoint)
        } else {
            eraseAt(worldPoint)
        }
    }

    fun endErase() {
        isErasing = false
        if (rasterLast != null) {
            endRasterStroke()
            countAction(StatsAction.ERASE, eraseStartMillis)
            return
        }
        if (lassoErase) {
            val lasso = lassoPath.toList()
            lassoPath.clear()
            // A tap or a line encloses nothing: drop the history entry instead of leaving an empty undo step.
            if (lasso.size < 3 || !eraseInsideLasso(lasso)) {
                discardActionsSince(eraseStartDepth)
                return
            }
        }
        countAction(StatsAction.ERASE, eraseStartMillis)
        strokesChanged()
        onImageChanged(imageItems.toList())
    }

    private var eraseStartMillis = 0L

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
            val list = strokes.toList()
            // Removed by position, last first so earlier positions stay valid: a filter over the
            // whole list cost ~0.13 s per touch at a million strokes.
            for (at in positionsNear(list, point.x - radius, point.y - radius, point.x + radius, point.y + radius).reversed()) {
                val stroke = list[at]
                val hit = isEditable(stroke) && stroke.intersects(point.x - radius, point.y - radius, point.x + radius, point.y + radius) && (
                    if (stroke.filled) polygonContains(stroke.points, point)
                    else distanceToPolyline(stroke.points, point) <= radius + stroke.widthWorld / 2
                )
                if (hit) strokes.removeAt(at)
            }
            return
        }
        val list = strokes.toList()
        for (at in positionsNear(list, point.x - radius, point.y - radius, point.x + radius, point.y + radius).reversed()) {
            val stroke = list[at]
            if (!isEditable(stroke)) continue
            if (!stroke.intersects(point.x - radius, point.y - radius, point.x + radius, point.y + radius)) continue
            val pieces = stroke.eraseNear(point, radius) ?: continue
            strokes.removeAt(at)
            strokes.addAll(at, pieces)
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
     * the way drawing on graph paper invites; on isometric paper, on the isometric lattice
     * (roadmap item 142). On other paper, a frame's layout grid edges within
     * [OBJECT_SNAP_RADIUS_SCREEN] attract each coordinate; otherwise points stay untouched.
     */
    private fun snappedToGrid(point: WorldPoint): WorldPoint {
        val spacing = BACKGROUND_PATTERN_SPACING_WORLD
        when (backgroundStyle) {
            BackgroundStyle.GRID, BackgroundStyle.DOT ->
                return WorldPoint(kotlin.math.round(point.x / spacing) * spacing, kotlin.math.round(point.y / spacing) * spacing)
            BackgroundStyle.ISOMETRIC -> return snappedToIsometric(point, spacing)
            else -> {}
        }
        val radius = OBJECT_SNAP_RADIUS_SCREEN / scale.coerceAtLeast(1e-300)
        fun nearest(value: Double, lines: List<Double>) = lines.minByOrNull { abs(it - value) }?.takeIf { abs(it - value) <= radius } ?: value
        return WorldPoint(nearest(point.x, layoutGridLines(frames, vertical = true)), nearest(point.y, layoutGridLines(frames, vertical = false)))
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
        // A shape drawn in several outlines (a thought balloon's trail) is one group.
        val extras = extrasOnPlane(activeShapeKind, start, end, isometricPlane)
        val group = if (extras.isEmpty()) null else UUID.randomUUID().toString()
        fun styled(points: List<WorldPoint>) = Stroke(
            activeColor.copy(alpha = activeColor.alpha * activeOpacity.toFloat()),
            activeWidthWorld / scale.coerceAtLeast(1e-300),
            activePenType,
            blendMode = activeBlendMode,
            layerId = activeLayerId,
            animFrame = currentAnimFrame,
            dashed = activeDashed,
            lineStyle = activeLineStyle.takeIf { it != LineStyle() },
            brush = if (activePenType == PenType.STAMP) activeBrush else null,
            groupId = group,
        ).apply { for (point in points) addPoint(point) }
        strokes.add(styled(shapeOnPlane(activeShapeKind, start, end, isometricPlane)))
        for (extra in extras) strokes.add(styled(extra))
        countAction(StatsAction.DRAW, System.currentTimeMillis())
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
                existing.outlined, existing.path, existing.features,
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
        features: Set<OpenTypeFeature> = emptySet(),
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
                font = font, alignment = alignment, boxWidthWorld = boxWidthWorld, link = link, outlined = outlined, features = features,
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
                    features = features,
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
    fun addImage(fileName: String, position: WorldPoint, widthWorld: Double, heightWorld: Double, recordUndo: Boolean = true, videoFileName: String? = null, modelFileName: String? = null) {
        if (recordUndo) recordHistory()
        imageItems.add(ImageItem(UUID.randomUUID().toString(), fileName, position, widthWorld, heightWorld, videoFileName, modelFileName))
        onImageChanged(imageItems.toList())
    }

    /**
     * Places a saved 3D model (roadmap item 108) in the middle of the view as an image item,
     * [MODEL_PLACED_SCREEN] screen pixels wide, selected so "Paint in 3D" can open it. Undoable.
     */
    fun placeModel(stored: StoredModel) {
        val width = MODEL_PLACED_SCREEN / scale.coerceAtLeast(1e-300)
        val height = width * stored.aspect
        addImage(stored.posterFileName, WorldPoint(panWorld.x - width / 2, panWorld.y - height / 2), width, height, modelFileName = stored.modelFileName)
        clearSelectionState()
        selectedImageId = imageItems.last().id
    }

    /**
     * Swaps image [id]'s picture for [fileName] and its 3D model for [modelFileName] (roadmap
     * item 108: a painted model saved as new files), keeping its place and width; its height
     * follows the new picture's [aspect] (height / width). One undo step.
     */
    fun replaceImageModel(id: String, fileName: String, modelFileName: String, aspect: Double) {
        val index = imageItems.indexOfFirst { it.id == id }
        if (index == -1) return
        recordHistory()
        val old = imageItems[index]
        imageItems[index] = old.copy(fileName = fileName, modelFileName = modelFileName, heightWorld = old.widthWorld * aspect)
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

    /**
     * Warping the selection or the raster layer (roadmap items 84, 92, 122), in one of the
     * [WarpKind]s: handles over the selection's box; dragging one bends the selected ink
     * smoothly. Each drag is one undo step. Ends when the selection is cleared or changed, or
     * toggled off.
     */
    var meshWarping by mutableStateOf(false)
        private set

    /** Which warp [meshWarping] is doing. */
    var warpKind by mutableStateOf(WarpKind.MESH)
        private set

    /**
     * The warp's handles while [meshWarping]; empty otherwise. The mesh lattice's control points
     * row by row, the distort's four corners (see [perspectiveDistort]), or the puppet pins
     * where they've been dragged to.
     */
    val meshNodes = mutableStateListOf<WorldPoint>()

    // Puppet warp: where each pin in [meshNodes] was stuck in, in the picture as it was then.
    private val puppetOrigins = mutableListOf<WorldPoint>()
    // A pin pressed and released without moving is pulled out.
    private var pinPressedAt: WorldPoint? = null
    private var pinMoved = false

    private var meshMin = WorldPoint.Zero
    private var meshMax = WorldPoint.Zero
    // Each selected stroke's points when the lattice was laid, which every warp is computed from.
    private var meshOriginals: List<Pair<Stroke, List<WorldPoint>>> = emptyList()
    private var meshDragIndex: Int? = null

    // Raster warp (roadmap item 92): the picture being bent, lifted on the first drag (pixels,
    // non-premultiplied ARGB, and their box), and each tile as it was after the lift, so every
    // step repaints from the same start.
    private var warpSurface: RasterSurface? = null
    private var warpSource: IntArray? = null
    private var warpBox = IntArray(4)
    private var warpBase = HashMap<Long, RasterTile?>()

    fun toggleMeshWarp() = toggleWarp(WarpKind.MESH)

    /** Starts warping in [kind], or stops when that warp is already on (another kind switches to this one). */
    fun toggleWarp(kind: WarpKind) {
        if (meshWarping) {
            val same = warpKind == kind
            endMeshWarp()
            if (same) return
        }
        warpKind = kind
        if (activeLayerIsRaster) {
            startRasterWarp()
            return
        }
        if (selection.isEmpty()) return
        val (min, max) = selectionBounds() ?: return
        // A flat selection (a horizontal line) still gets a box to bend.
        val pad = maxOf(max.x - min.x, max.y - min.y) * 0.05 + 1.0 / scale.coerceAtLeast(1e-300)
        meshMin = WorldPoint(min.x - pad, min.y - pad)
        meshMax = WorldPoint(max.x + pad, max.y + pad)
        meshNodes.clear()
        meshNodes.addAll(initialWarpHandles())
        meshOriginals = selection.map { it to it.points.toList() }
        meshWarping = true
    }

    private fun initialWarpHandles(): List<WorldPoint> = when (warpKind) {
        WarpKind.MESH -> meshGrid(meshMin, meshMax)
        WarpKind.DISTORT -> boxCorners(meshMin, meshMax)
        WarpKind.PUPPET -> emptyList() // pins are stuck in by tapping
    }

    /** Where the warp as it stands takes each point of the picture it started from. */
    private fun warpMapping(): (WorldPoint) -> WorldPoint {
        val nodes = meshNodes.toList()
        val min = meshMin
        val max = meshMax
        return when (warpKind) {
            WarpKind.MESH -> { p -> meshWarp(p, min, max, nodes) }
            WarpKind.DISTORT -> { p -> perspectiveDistort(p, min, max, nodes) }
            WarpKind.PUPPET -> {
                val origins = puppetOrigins.toList()
                ({ p -> puppetWarp(p, origins, nodes) })
            }
        }
    }

    /**
     * Makes the picture as it is now the warp's new starting point, with every pin where it is:
     * a pin stuck into an already-bent picture holds that spot, since its origin can only be
     * known in the picture as it is.
     */
    private fun rebaseWarp() {
        puppetOrigins.clear()
        puppetOrigins.addAll(meshNodes)
        meshOriginals = meshOriginals.map { (stroke, _) -> stroke to stroke.points.toList() }
        lastWarped?.let { warped ->
            // Raster: the warped picture becomes the lifted one, still off the layer underneath.
            warpSource = warped.pixels
            warpBox = intArrayOf(warped.left, warped.top, warped.left + warped.width - 1, warped.top + warped.height - 1)
        }
    }

    /** Lays the lattice over the raster selection, or over all the active raster layer's paint. */
    private fun startRasterWarp() {
        val surface = activeRaster ?: return
        val box = selectionPixelBox(surface) ?: paintedPixelBox(surface) ?: return
        meshMin = surface.worldOf(box[0].toDouble(), box[1].toDouble())
        meshMax = surface.worldOf(box[2] + 1.0, box[3] + 1.0)
        meshNodes.clear()
        meshNodes.addAll(initialWarpHandles())
        meshOriginals = emptyList()
        warpSurface = surface
        warpSource = null
        warpBox = box
        warpBase = HashMap()
        meshWarping = true
    }

    private fun paintedPixelBox(surface: RasterSurface): IntArray? {
        if (surface.tiles.isEmpty()) return null
        val xs = surface.tiles.keys.map { RasterSurface.tileX(it) }
        val ys = surface.tiles.keys.map { RasterSurface.tileY(it) }
        return intArrayOf(xs.min() * RASTER_TILE, ys.min() * RASTER_TILE, (xs.max() + 1) * RASTER_TILE - 1, (ys.max() + 1) * RASTER_TILE - 1)
    }

    /** Redraws the lifted picture bent by the lattice as it is now. */
    private fun applyRasterWarp() {
        val surface = warpSurface ?: return
        val box = warpBox
        val width = box[2] - box[0] + 1
        val height = box[3] - box[1] + 1
        val before = surface.beforeTileChange
        surface.beforeTileChange = { key ->
            before(key)
            if (key !in warpBase) warpBase[key] = surface.tileAt(key)?.copy()
        }
        // Back to the lifted state (logging each tile for undo first).
        for ((key, tile) in warpBase) {
            before(key)
            surface.setTile(key, tile?.copy())
        }
        val source = warpSource ?: IntArray(width * height).also { pixels ->
            // Lift: copy the picture (inside the selection, if any) and clear it from the layer.
            surface.clip = if (hasRasterSelection) { x, y -> inRasterSelection(surface, x, y) } else null
            surface.forEachPixelIn(box[0].toDouble(), box[1].toDouble(), box[2].toDouble(), box[3].toDouble()) { tile, index, px, py ->
                val a = RasterSurface.u16(tile.data[index + 3])
                if (a > 0) {
                    fun channel(c: Int) = (RasterSurface.u16(tile.data[index + c]).toLong() * 255 / a).toInt().coerceIn(0, 255)
                    pixels[(py - box[1]) * width + (px - box[0])] = ((a * 255 / 65535) shl 24) or (channel(0) shl 16) or (channel(1) shl 8) or channel(2)
                }
                for (c in 0 until 4) tile.data[index + c] = 0
            }
            surface.clip = null
            warpSource = pixels
            // The hook kept each tile as it was before the lift (that's what undo needs); later
            // steps must restore the lifted state instead, or the original picture comes back
            // under the warped copy from the second drag event on.
            for (key in warpBase.keys) warpBase[key] = surface.tileAt(key)?.copy()
        }
        val warped = warpPixels(surface, source, width, height, box[0], box[1], warpMapping())
        lastWarped = warped
        if (warped != null) {
            surface.forEachPixelIn(warped.left.toDouble(), warped.top.toDouble(), (warped.left + warped.width - 1).toDouble(), (warped.top + warped.height - 1).toDouble()) { tile, index, px, py ->
                val x = px - warped.left
                val y = py - warped.top
                if (x !in 0 until warped.width || y !in 0 until warped.height) return@forEachPixelIn
                val argb = warped.pixels[y * warped.width + x]
                val a = (argb ushr 24) / 255.0
                if (a == 0.0) return@forEachPixelIn
                val src = doubleArrayOf(((argb shr 16) and 0xFF) / 255.0 * a, ((argb shr 8) and 0xFF) / 255.0 * a, (argb and 0xFF) / 255.0 * a, a)
                for (c in 0 until 4) tile.data[index + c] = (src[c] * 65535 + RasterSurface.u16(tile.data[index + c]) * (1 - a)).toInt().coerceIn(0, 65535).toShort()
            }
        }
        surface.beforeTileChange = before
        rasterRevision++
    }

    // The raster warp's last result, which a puppet rebase takes as its new lifted picture.
    private var lastWarped: WarpedPixels? = null

    private fun endMeshWarp() {
        if (warpSurface != null) markRasterDirty(activeLayerId, emptySet())
        warpSurface = null
        warpSource = null
        lastWarped = null
        puppetOrigins.clear()
        pinPressedAt = null
        warpBase = HashMap()
        meshWarping = false
        meshNodes.clear()
        meshOriginals = emptyList()
        meshDragIndex = null
    }

    fun beginSelectGesture(worldPoint: WorldPoint) {
        if (shapeEditMode != null) {
            beginShapeEditGesture(worldPoint)
            return
        }
        // On a raster layer, a drag inside the raster selection moves its pixels.
        if (activeLayerIsRaster && beginMovingSelectedPixels(worldPoint)) return
        // The lattice belongs to the selection it was laid over; a changed selection ends it.
        if (meshWarping && warpSurface == null && (meshOriginals.size != selection.size || meshOriginals.any { (stroke, _) -> selection.none { it === stroke } })) endMeshWarp()
        if (meshWarping) {
            val hit = meshNodes.indices.minByOrNull { k -> hypot(meshNodes[k].x - worldPoint.x, meshNodes[k].y - worldPoint.y) }
                ?.takeIf { k -> hypot(meshNodes[k].x - worldPoint.x, meshNodes[k].y - worldPoint.y) * scale <= NODE_HANDLE_SCREEN }
            if (hit != null) {
                meshDragIndex = hit
                if (warpKind == WarpKind.PUPPET) {
                    // Maybe a tap to pull the pin out: history waits for the pin to move.
                    pinPressedAt = worldPoint
                    pinMoved = false
                } else {
                    recordHistory()
                }
                return
            }
            if (warpKind == WarpKind.PUPPET) {
                // A tap off the pins sticks a new one in there, ready to drag.
                rebaseWarp()
                recordHistory()
                meshNodes.add(worldPoint)
                puppetOrigins.add(worldPoint)
                meshDragIndex = meshNodes.lastIndex
                pinPressedAt = null
                return
            }
        }
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
        if (shapeEditMode != null) {
            continueShapeEditGesture(worldPoint)
            return
        }
        if (floatingPixels != null) {
            moveSelectedPixels(worldPoint)
            return
        }
        meshDragIndex?.let { k ->
            pinPressedAt?.let { pressed ->
                if (!pinMoved) {
                    if (hypot(worldPoint.x - pressed.x, worldPoint.y - pressed.y) * scale <= TAP_SLOP_SCREEN) return
                    pinMoved = true
                    recordHistory()
                }
            }
            meshNodes[k] = worldPoint
            if (warpSurface != null) {
                applyRasterWarp()
                return
            }
            val warp = warpMapping()
            for ((stroke, original) in meshOriginals) {
                if (stroke.points.size == original.size) stroke.setPoints(original.map(warp))
            }
            return
        }
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
        if (shapeEditMode != null) {
            endShapeEditGesture()
            return
        }
        if (floatingPixels != null) {
            endMovingSelectedPixels()
            return
        }
        // On a raster layer the lasso and marquee select pixels, not strokes.
        if (activeLayerIsRaster) {
            val lasso = lassoPath.toList()
            lassoPath.clear()
            val rect = marqueeRect
            marqueeRect = null
            if (paintingSelection) return // the pen and eraser make this selection
            dropSelectionMask() // a new outline replaces a painted selection
            rasterSelection = when {
                lasso.size >= 3 -> lasso
                rect != null && hypot(rect.second.x - rect.first.x, rect.second.y - rect.first.y) * scale > TAP_SLOP_SCREEN ->
                    listOf(rect.first, WorldPoint(rect.second.x, rect.first.y), rect.second, WorldPoint(rect.first.x, rect.second.y))
                else -> null // a tap deselects
            }
            return
        }
        meshDragIndex?.let { k ->
            meshDragIndex = null
            if (pinPressedAt != null && !pinMoved) {
                // A pin tapped without dragging is pulled out; the picture keeps its shape.
                rebaseWarp()
                meshNodes.removeAt(k)
                puppetOrigins.removeAt(k)
            }
            pinPressedAt = null
            strokesChanged()
            return
        }
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
        // Ruler guides always attract a moved selection; other objects only with snapping on.
        // Frames' layout grid edges (roadmap item 142) attract it the same way.
        val rulerX = guides.filter { it.vertical }.map { it.position } + layoutGridLines(frames, vertical = true)
        val rulerY = guides.filter { !it.vertical }.map { it.position } + layoutGridLines(frames, vertical = false)
        if (!snapToObjects || viewportSize.width <= 0f) {
            guideTargetsX = rulerX.toDoubleArray()
            guideTargetsY = rulerY.toDoubleArray()
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
        guideTargetsX = (others.flatMap { listOf(it.minX, (it.minX + it.maxX) / 2, it.maxX) } + rulerX).toDoubleArray()
        guideTargetsY = (others.flatMap { listOf(it.minY, (it.minY + it.maxY) / 2, it.maxY) } + rulerY).toDoubleArray()
    }

    /** The document's real-world scale (roadmap item 143); null measures in world units. */
    var drawingScale: DrawingScale? by mutableStateOf(null)
        private set

    fun changeDrawingScale(scale: DrawingScale?) {
        drawingScale = scale
        onDrawingScaleChanged(scale)
    }

    fun loadDrawingScale(scale: DrawingScale?) {
        drawingScale = scale
    }

    /**
     * What the MEASURE tool (roadmap item 143) is showing: a dragged line (two points, open), a
     * tapped stroke's outline (closed when it's a closed shape: perimeter and area), or null.
     * Not part of the document; it stays until the next measurement.
     */
    var measurement: Measurement? by mutableStateOf(null)
        private set

    /** Starts a measuring drag at [worldPoint], snapped to a nearby stroke end or corner when object snapping is on. */
    fun beginMeasure(worldPoint: WorldPoint) {
        val start = snappedToObject(worldPoint) ?: worldPoint
        measurement = Measurement(listOf(start, start), closed = false)
    }

    fun continueMeasure(worldPoint: WorldPoint) {
        val start = measurement?.points?.firstOrNull() ?: return
        measurement = Measurement(listOf(start, snappedToObject(worldPoint) ?: worldPoint), closed = false)
    }

    /**
     * Ends a measuring drag. A drag shorter than a tap measures what was tapped instead: the
     * smallest visible closed shape around the point (perimeter and area), else the nearest
     * visible line within reach (its length), else nothing.
     */
    fun endMeasure() {
        snapIndicator = null
        val line = measurement ?: return
        val (a, b) = line.points
        if (hypot(b.x - a.x, b.y - a.y) * scale > TAP_SLOP_SCREEN) return
        val reach = OBJECT_SNAP_RADIUS_SCREEN / scale.coerceAtLeast(1e-300)
        val closeTolerance = FILL_CLOSE_TOLERANCE_SCREEN / scale.coerceAtLeast(1e-300)
        val near = strokesNear(a.x - reach, a.y - reach, a.x + reach, a.y + reach).filter { isVisible(it) && it.points.size >= 2 }
        val shape = strokes.filter { isVisible(it) && (it.filled || isClosedLoop(it.points, closeTolerance)) && polygonContains(it.points, a) }
            .minByOrNull { polygonArea(it.points) }
        measurement = when {
            shape != null -> Measurement(shape.points, closed = true)
            else -> near.minByOrNull { distanceToPolyline(it.points, a) }
                ?.takeIf { distanceToPolyline(it.points, a) <= reach }
                ?.let { Measurement(it.points, closed = false) }
        }
    }

    /** Screen rulers along the canvas's top and left edges (roadmap item 99). A view setting. */
    var showRulers by mutableStateOf(false)

    /** Guides pulled out of the rulers; see [Guide]. */
    val guides = mutableStateListOf<Guide>()

    fun loadGuides(loaded: List<Guide>) {
        guides.clear()
        guides.addAll(loaded)
    }

    /** Adds a [vertical] (or horizontal) guide at world [position]; returns its id. */
    fun addGuide(vertical: Boolean, position: Double): String {
        val guide = Guide(UUID.randomUUID().toString(), vertical, position)
        guides.add(guide)
        onGuidesChanged(guides.toList())
        return guide.id
    }

    fun moveGuide(id: String, position: Double) {
        val index = guides.indexOfFirst { it.id == id }
        if (index == -1) return
        guides[index] = guides[index].copy(position = position)
    }

    /** Reports guides moved with [moveGuide] (called once a drag ends, not every step). */
    fun guidesSettled() = onGuidesChanged(guides.toList())

    fun removeGuide(id: String) {
        if (guides.removeAll { it.id == id }) onGuidesChanged(guides.toList())
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

    /** The style the style picker last copied (roadmap item 121); null until something's copied. Session-only, like the system clipboard's history. */
    var copiedStyle by mutableStateOf<StrokeStyle?>(null)
        private set

    /** Copies the style of the first selected stroke for [pasteStyleToSelection]. */
    fun copySelectionStyle() {
        copiedStyle = StrokeStyle.of(selection.firstOrNull() ?: return)
    }

    /** Redraws every selected stroke in [copiedStyle] (see [Stroke.withStyle]); undoable, and the selection stays on them. */
    fun pasteStyleToSelection() {
        val style = copiedStyle ?: return
        if (selection.isEmpty()) return
        recordHistory()
        val replaced = selection.toList().map { old ->
            val restyled = old.withStyle(style)
            val index = strokes.indexOf(old)
            if (index != -1) strokes[index] = restyled
            restyled
        }
        clearSelectionState()
        selection.addAll(replaced)
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
    /**
     * Places [signature] (pad coordinates, see [SignatureStore]) at the centre of the view, a
     * third of the view wide, selected so it can be moved into place (roadmap item 129).
     */
    fun placeSignature(signature: List<Stroke>) {
        val viewWidth = if (viewportSize.width > 0f) viewportSize.width.toDouble() else SIGNATURE_FALLBACK_VIEW_PX
        pasteStrokes(placedSignature(signature, viewWidth / 3 / scale.coerceAtLeast(1e-300)))
    }

    /**
     * What a plugin (roadmap item 111) is given: the selected strokes in drawing order, the
     * world area on screen, and the active pen (its width in world units at the current zoom).
     */
    fun pluginInput(): PluginInput {
        val closeTolerance = FILL_CLOSE_TOLERANCE_SCREEN / scale.coerceAtLeast(1e-300)
        val chosen = selection.sortedBy { strokes.indexOf(it) }
        val view = if (viewportSize.width > 0f) {
            val center = Offset(viewportSize.width / 2f, viewportSize.height / 2f)
            val corners = listOf(Offset.Zero, Offset(viewportSize.width, 0f), Offset(0f, viewportSize.height), Offset(viewportSize.width, viewportSize.height)).map { screenToWorld(it, center) }
            WorldPoint(corners.minOf { it.x }, corners.minOf { it.y }) to WorldPoint(corners.maxOf { it.x }, corners.maxOf { it.y })
        } else {
            panWorld to panWorld
        }
        return PluginInput(
            chosen.map { PluginStroke(it.points.toList(), it.color, it.widthWorld, it.filled, isClosedLoop(it.points, closeTolerance)) },
            view,
            activeColor,
            activeWidthWorld / scale.coerceAtLeast(1e-300),
        )
    }

    /**
     * Applies a plugin's [changes] (roadmap item 111) to the selection [pluginInput] described:
     * updates and removals by selection index, additions on the active layer and frame. One
     * undo step; the strokes added or changed end up selected.
     */
    fun applyPluginChanges(changes: PluginResult.Changes) {
        if (changes.add.isEmpty() && changes.update.isEmpty() && changes.remove.isEmpty()) return
        if (changes.add.isNotEmpty() && !activeLayerEditable) return
        recordHistory()
        val chosen = selection.sortedBy { strokes.indexOf(it) }
        val touched = ArrayList<Stroke>()
        for (update in changes.update) {
            val stroke = chosen.getOrNull(update.index) ?: continue
            val at = strokes.indexOfFirst { it === stroke }
            if (at == -1) continue
            var edited = if (update.color != null) stroke.withColor(update.color) else stroke
            update.points?.let { edited.setPoints(it) }
            update.width?.let { edited.setWidth(it) }
            if (edited !== stroke) strokes[at] = edited
            touched.add(edited)
        }
        val removed = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<Stroke, Boolean>()).apply { changes.remove.mapNotNullTo(this) { chosen.getOrNull(it) } }
        strokes.removeWhere { it in removed }
        touched.removeAll { it in removed }
        val added = changes.add.map { s ->
            Stroke(s.color, s.width, PenType.MARKER, filled = s.filled, layerId = activeLayerId, animFrame = currentAnimFrame).apply {
                for (p in s.points) addPoint(p)
                if (s.closed && s.points.size > 2 && s.points.first() != s.points.last()) addPoint(s.points.first())
            }
        }
        strokes.addAll(added)
        clearSelectionState()
        selection.addAll(touched + added)
        strokesChanged()
    }

    /**
     * Places a QR code (roadmap item 144) at the centre of the view as one selected group:
     * [qr]'s dark modules as filled outlines in the active colour, over a white square that
     * includes the quiet zone when [lightBackground] (so it reads on any paper). The code is
     * [QR_SIZE_SCREEN] screen pixels across at the current zoom. One undo step.
     */
    fun insertQrCode(qr: QrOutlines, lightBackground: Boolean) {
        if (qr.outlines.isEmpty() || !activeLayerEditable) return
        val side = qr.size + 2 * QR_QUIET_ZONE_MODULES
        val module = QR_SIZE_SCREEN / side / scale.coerceAtLeast(1e-300)
        val group = UUID.randomUUID().toString()
        fun place(points: List<WorldPoint>, color: Color) = Stroke(color, 0.0, filled = true, groupId = group).apply {
            for (p in points) addPoint(WorldPoint((p.x + QR_QUIET_ZONE_MODULES) * module, (p.y + QR_QUIET_ZONE_MODULES) * module))
        }
        val backing = if (lightBackground) {
            val q = -QR_QUIET_ZONE_MODULES.toDouble()
            val far = (qr.size + QR_QUIET_ZONE_MODULES).toDouble()
            listOf(place(listOf(WorldPoint(q, q), WorldPoint(far, q), WorldPoint(far, far), WorldPoint(q, far), WorldPoint(q, q)), Color.White))
        } else {
            emptyList()
        }
        pasteStrokes(backing + qr.outlines.map { place(it, activeColor) })
    }

    /**
     * Places PDF or Illustrator pages read as vectors (roadmap item 103), one point per world
     * unit, stacked top to bottom with a gap, centred on the view, each page a group, selected.
     * Paths marked with a PDF layer go onto the Fern layer of that name (made when missing);
     * the rest onto the active layer. Fills become filled outlines (holes kept, by the path's
     * own fill rule), strokes outlines with their colour, width, cap, join and dashes. One
     * undo step for the strokes; layers it makes stay on undo, as layers always do. Returns
     * how many strokes were added.
     */
    fun importVectorPages(pages: List<ImportedVectorPage>): Int {
        if (!activeLayerEditable) return 0
        val created = ArrayList<Stroke>()
        var top = 0.0
        val layerFor = HashMap<String, String>()
        for (page in pages) {
            val group = UUID.randomUUID().toString()
            val pageTop = top
            // PDF y points up; the page's top edge lands at this page's place in the stack.
            fun place(p: WorldPoint) = WorldPoint(p.x, pageTop + page.height - p.y)
            for (path in page.paths) {
                val layerId = path.layer?.let { name ->
                    layerFor.getOrPut(name) {
                        layers.firstOrNull { it.name == name && !it.raster }?.id ?: Layer(UUID.randomUUID().toString(), name).also { layers.add(it) }.id
                    }
                } ?: activeLayerId
                val subpaths = path.subpaths.map { (points, closed) -> points.map(::place) to closed }
                path.fill?.let { fill ->
                    for (outline in importedFillOutlines(subpaths.map { it.first }, path.evenOdd)) {
                        created.add(Stroke(fill, 0.0, filled = true, layerId = layerId, animFrame = currentAnimFrame, groupId = group).apply {
                            for (p in outline) addPoint(p)
                        })
                    }
                }
                path.stroke?.let { color ->
                    val style = LineStyle(path.cap, path.join).takeIf { it != LineStyle() }
                    for ((points, closed) in subpaths) {
                        created.add(Stroke(color, path.width, filled = false, layerId = layerId, animFrame = currentAnimFrame, dashed = path.dashed, groupId = group, lineStyle = style).apply {
                            for (p in points) addPoint(p)
                            if (closed && points.size > 1) addPoint(points.first())
                        })
                    }
                }
            }
            top += page.height + IMPORTED_PAGE_GAP
        }
        if (layerFor.isNotEmpty()) onLayersChanged(layers.toList())
        if (created.isEmpty()) return 0
        recordHistory()
        val centreX = (created.minOf { it.minX } + created.maxOf { it.maxX }) / 2
        val centreY = (created.minOf { it.minY } + created.maxOf { it.maxY }) / 2
        val offset = WorldPoint(centreX - panWorld.x, centreY - panWorld.y)
        for (stroke in created) stroke.shiftBy(offset)
        strokes.addAll(created)
        clearSelectionState()
        selection.addAll(created)
        strokesChanged()
        return created.size
    }

    /** Placed symbols that stay linked to the library (roadmap item 100); see [SymbolInstance]. */
    val symbolInstances = mutableStateListOf<SymbolInstance>()

    fun loadSymbolInstances(loaded: List<SymbolInstance>) {
        symbolInstances.clear()
        symbolInstances.addAll(loaded)
    }

    /** Places [strokes], symbol [symbol]'s master, at the centre of the view as one linked, selected group. */
    fun placeSymbol(symbol: SymbolMeta, strokes: List<Stroke>) {
        if (strokes.isEmpty() || !activeLayerEditable) return
        val group = UUID.randomUUID().toString()
        pasteStrokes(strokes.map { it.inGroup(group) })
        // pasteStrokes gives groups fresh ids; the instance is the group it ended up in.
        val placed = selection.firstOrNull()?.groupId ?: return
        symbolInstances.add(SymbolInstance(placed, symbol.id, symbol.revision, strokesWidth(strokes)))
        onSymbolInstancesChanged(symbolInstances.toList())
    }

    /** The linked instance the selection belongs to, if it includes one (the first found). */
    val selectedSymbolInstance: SymbolInstance?
        get() = selection.mapNotNull { it.groupId }.distinct().firstNotNullOfOrNull { g -> symbolInstances.firstOrNull { it.groupId == g } }

    /**
     * Redraws every instance of [symbol] in this document from its new master [strokes], each at
     * its own place and size, on its own layer and frame. [recordUndo] false for the catch-up when
     * a document opens. Instances whose strokes are all gone are dropped.
     */
    fun syncSymbol(symbol: SymbolMeta, master: List<Stroke>, recordUndo: Boolean = true) {
        val affected = symbolInstances.filter { it.symbolId == symbol.id }
        if (affected.isEmpty()) return
        if (recordUndo) recordHistory()
        val newWidth = strokesWidth(master)
        for (instance in affected) {
            val members = strokes.filter { it.groupId == instance.groupId }
            val index = symbolInstances.indexOf(instance)
            if (members.isEmpty()) {
                symbolInstances.removeAt(index)
                continue
            }
            val min = WorldPoint(members.minOf { it.minX }, members.minOf { it.minY })
            val max = WorldPoint(members.maxOf { it.maxX }, members.maxOf { it.maxY })
            val first = members.first()
            val fitted = fittedSymbol(master, instance.masterWidth, min, max)
                .map { it.onLayer(first.layerId).onAnimFrame(first.animFrame).inGroup(instance.groupId) }
            val at = strokes.indexOf(first)
            strokes.removeAll { it.groupId == instance.groupId }
            strokes.addAll(at.coerceIn(0, strokes.size), fitted)
            // Its size now relates to the new master as it did to the old.
            symbolInstances[index] = instance.copy(revision = symbol.revision, masterWidth = newWidth)
        }
        clearSelectionState()
        onSymbolInstancesChanged(symbolInstances.toList())
        strokesChanged()
    }

    /**
     * Readies a redefinition of the selected instance's symbol from the selection: every
     * selected stroke joins the instance's group (strokes drawn onto it become part of it), and
     * the new master is the selection scaled back by the instance's size relative to the old
     * master, so other instances keep their sizes. Null when no instance is selected.
     */
    fun symbolRedefinitionFromSelection(): Pair<SymbolInstance, List<Stroke>>? {
        val instance = selectedSymbolInstance ?: return null
        val members = strokes.filter { it.groupId == instance.groupId }
        val factor = strokesWidth(members) / instance.masterWidth.coerceAtLeast(1e-9)
        regroupSelection(instance.groupId)
        val copies = selectionForClipboard()
        if (copies.isEmpty() || factor <= 0.0) return null
        val drawn = copies.filter { it.points.isNotEmpty() }
        val cx = (drawn.minOf { it.minX } + drawn.maxOf { it.maxX }) / 2
        val cy = (drawn.minOf { it.minY } + drawn.maxOf { it.maxY }) / 2
        for (stroke in drawn) {
            stroke.setPoints(stroke.points.map { WorldPoint(cx + (it.x - cx) / factor, cy + (it.y - cy) / factor) })
            stroke.setWidth(stroke.widthWorld / factor)
        }
        return instance to drawn
    }

    /** Unlinks the selected instance: its strokes stay, as an ordinary group. */
    fun detachSymbolInstance() {
        val instance = selectedSymbolInstance ?: return
        symbolInstances.remove(instance)
        onSymbolInstancesChanged(symbolInstances.toList())
    }

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

    /** Which of the shape builder's and knife's gestures (roadmap item 141) the SELECT tool is doing; null for neither. */
    var shapeEditMode: ShapeEditMode? by mutableStateOf(null)
        private set

    /** The drag of the shape builder or knife in progress, in world space; empty when none is. */
    val shapeEditPath = mutableStateListOf<WorldPoint>()

    /**
     * Turns [mode] on, or off when it's already on. The shape builder (either of its modes)
     * starts by dividing the selected closed shapes into every region they make between them
     * ([divideShapes]), one undo step; each region takes the style of the topmost shape over
     * it. Drags across regions then merge them ([ShapeEditMode.BUILD_ADD]) or delete them
     * ([ShapeEditMode.BUILD_REMOVE]). The knife needs a selection; each drag cuts it.
     */
    fun toggleShapeEdit(mode: ShapeEditMode) {
        val current = shapeEditMode
        if (current == mode) {
            endShapeEdit()
            return
        }
        val building = current == ShapeEditMode.BUILD_ADD || current == ShapeEditMode.BUILD_REMOVE
        // The panel cutter works on whatever it crosses; it needs no selection.
        if (mode != ShapeEditMode.PANEL_CUT && (mode == ShapeEditMode.KNIFE || !building)) {
            if (selection.isEmpty()) return
            if (mode != ShapeEditMode.KNIFE && !divideSelection()) return
        }
        shapeEditMode = mode
        shapeEditPath.clear()
    }

    fun endShapeEdit() {
        shapeEditMode = null
        shapeEditPath.clear()
    }

    /**
     * Replaces the selected closed shapes by their regions ([divideShapes]), selected. False,
     * changing nothing, unless two or more strokes are selected and all of them are closed.
     */
    private fun divideSelection(): Boolean {
        val closeTolerance = FILL_CLOSE_TOLERANCE_SCREEN / scale.coerceAtLeast(1e-300)
        val chosen = selection.toList()
        if (chosen.size < 2 || chosen.any { !isClosedLoop(it.points, closeTolerance) }) return false
        val ordered = chosen.sortedBy { strokes.indexOf(it) }
        val regions = divideShapes(ordered.map { it.points })
        if (regions.isEmpty()) return false
        recordHistory()
        val pieces = regions.map { region ->
            val inside = interiorPoint(region)
            val owner = ordered.lastOrNull { polygonContains(it.points, inside) } ?: ordered.last()
            owner.withLoop(region)
        }
        replaceStrokes(chosen, pieces, at = strokes.indexOf(ordered.first()))
        return true
    }

    fun beginShapeEditGesture(worldPoint: WorldPoint) {
        shapeEditPath.clear()
        shapeEditPath.add(worldPoint)
    }

    fun continueShapeEditGesture(worldPoint: WorldPoint) {
        if (shapeEditPath.isNotEmpty()) shapeEditPath.add(worldPoint)
    }

    /**
     * Finishes a shape builder or knife drag. The builder merges (union) or deletes the selected
     * regions the drag passed through; the knife cuts every selected stroke it crosses - closed
     * shapes into closed pieces ([cutShape]), lines at the crossings ([splitPolyline]). The
     * pieces stay selected for the next drag. One undo step, none when nothing changed.
     */
    fun endShapeEditGesture() {
        val path = shapeEditPath.toList()
        shapeEditPath.clear()
        val mode = shapeEditMode ?: return
        if (path.isEmpty()) return
        val chosen = selection.toList()
        when (mode) {
            ShapeEditMode.BUILD_ADD, ShapeEditMode.BUILD_REMOVE -> {
                val touched = chosen.filter { stroke -> path.any { polygonContains(stroke.points, it) } }.sortedBy { strokes.indexOf(it) }
                if (mode == ShapeEditMode.BUILD_REMOVE) {
                    if (touched.isEmpty()) return
                    recordHistory()
                    replaceStrokes(touched, emptyList(), at = 0)
                    selection.addAll(chosen.filter { it !in touched })
                    return
                }
                if (touched.size < 2) return
                val bottom = touched.first()
                val merged = combineShapes(touched.map { it.points }, ShapeOp.UNION, bridgeHoles = bottom.filled)
                if (merged.isEmpty()) return
                recordHistory()
                replaceStrokes(touched, merged.map { bottom.withLoop(it) }, at = strokes.indexOf(bottom))
                selection.addAll(chosen.filter { it !in touched })
            }
            ShapeEditMode.PANEL_CUT -> {
                // Comic panels (roadmap item 105): every closed outline on the active layer the
                // drag crosses is split in two with a gutter between, selection or not.
                if (path.size < 2) return
                val closeTolerance = FILL_CLOSE_TOLERANCE_SCREEN / scale.coerceAtLeast(1e-300)
                val gutter = PANEL_GUTTER_SCREEN / scale.coerceAtLeast(1e-300)
                val left = path.minOf { it.x }
                val top = path.minOf { it.y }
                val right = path.maxOf { it.x }
                val bottom = path.maxOf { it.y }
                val cuts = strokesNear(left, top, right, bottom)
                    .filter { it.layerId == activeLayerId && isVisible(it) && isEditable(it) && isClosedLoop(it.points, closeTolerance) }
                    .mapNotNull { stroke -> cutShape(stroke.points, path, gutter).takeIf { it.isNotEmpty() }?.let { stroke to it.map(stroke::withLoop) } }
                if (cuts.isEmpty()) return
                recordHistory()
                for ((stroke, pieces) in cuts) replaceStrokes(listOf(stroke), pieces, at = strokes.indexOf(stroke))
                clearSelectionState()
            }
            ShapeEditMode.KNIFE -> {
                if (path.size < 2) return
                val closeTolerance = FILL_CLOSE_TOLERANCE_SCREEN / scale.coerceAtLeast(1e-300)
                val gap = KNIFE_GAP_SCREEN / scale.coerceAtLeast(1e-300)
                val cuts = chosen.mapNotNull { stroke ->
                    val pieces = if (isClosedLoop(stroke.points, closeTolerance)) cutShape(stroke.points, path, gap).map(stroke::withLoop)
                    else splitPolyline(stroke.points, path).takeIf { it.size >= 2 }?.map(stroke::withPoints) ?: emptyList()
                    if (pieces.isEmpty()) null else stroke to pieces
                }
                if (cuts.isEmpty()) return
                recordHistory()
                val kept = chosen.filter { stroke -> cuts.none { it.first === stroke } }
                for ((stroke, pieces) in cuts) replaceStrokes(listOf(stroke), pieces, at = strokes.indexOf(stroke))
                selection.clear()
                selection.addAll(kept)
                selection.addAll(cuts.flatMap { it.second })
            }
        }
        strokesChanged()
    }

    /**
     * Contour (roadmap item 141): new outlines [distance] (world units; negative to shrink
     * closed shapes) around the selected strokes, merged, in the active colour at the
     * active width, laid above the selection and selected instead. One undo step; nothing
     * when nothing would be drawn.
     */
    fun contourSelection(distance: Double, join: ContourJoin) {
        if (selection.isEmpty() || !activeLayerEditable) return
        val closeTolerance = FILL_CLOSE_TOLERANCE_SCREEN / scale.coerceAtLeast(1e-300)
        val ordered = selection.sortedBy { strokes.indexOf(it) }
        val sources = ordered.map { ContourSource(it.points, if (it.filled) 0.0 else it.widthWorld, it.filled || isClosedLoop(it.points, closeTolerance)) }
        val loops = contourOutlines(sources, distance, join)
        if (loops.isEmpty()) return
        recordHistory()
        val top = ordered.last()
        val outlines = loops.map { loop ->
            Stroke(activeColor, activeWidthWorld, PenType.MARKER, layerId = top.layerId, animFrame = top.animFrame).apply {
                for (point in loop) addPoint(point)
                addPoint(loop.first())
            }
        }
        strokes.addAll((strokes.indexOf(top) + 1).coerceAtMost(strokes.size), outlines)
        clearSelectionState()
        selection.addAll(outlines)
        strokesChanged()
    }

    /** Swaps [old] for [new] (placed at [at] in the drawing order), and selects only [new]. Doesn't call [strokesChanged]. */
    private fun replaceStrokes(old: List<Stroke>, new: List<Stroke>, at: Int) {
        val removed = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<Stroke, Boolean>()).apply { addAll(old) }
        strokes.removeWhere { it in removed }
        strokes.addAll(at.coerceIn(0, strokes.size), new)
        clearSelectionState()
        selection.addAll(new)
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
        endMeshWarp()
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
        clearSelectionState()
        currentStroke = null
        symmetryCopies = emptyList()
        // Newest first: each entry's log takes strokes back to that entry, the oldest one last.
        Stroke.withoutLogging {
            openEntry = null
            while (undoStack.size > depth) restore(undoStack.removeLast())
        }
        openLog(listOf(undoStack.lastOrNull(), redoStack.lastOrNull()).filterNotNull().maxByOrNull { it.seq })
        strokesChanged()
    }

    fun undo() {
        val previous = undoStack.removeLastOrNull() ?: return
        clearSelectionState()
        // The redo step's log records what restoring changes, so redo can put it back.
        redoStack.addLast(currentEntry().also(::openLog))
        restore(previous)
        strokesChanged()
    }

    fun redo() {
        val next = redoStack.removeLastOrNull() ?: return
        clearSelectionState()
        undoStack.addLast(currentEntry().also(::openLog))
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
        openLog(null)
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
        // Isometric paper repeats less often across (roadmap item 142).
        val spacingX = if (backgroundStyle == BackgroundStyle.ISOMETRIC) isometricPeriodX(spacing) else spacing
        val offset = WorldPoint(
            kotlin.math.round(panWorld.x / spacingX) * spacingX,
            kotlin.math.round(panWorld.y / spacing) * spacing,
        )
        endMeshWarp() // its box and starting points are in the old coordinates
        for (surface in rasters.values) surface.shiftOrigin(offset)
        if (rasters.isNotEmpty()) markRasterDirty(activeLayerId, emptySet()) // origins are saved with the tiles
        // Not logged: the logs and entries are moved below instead.
        Stroke.withoutLogging {
            for (stroke in strokes) stroke.shiftBy(offset)
            shiftStrokesOnlyInHistory(offset)
        }
        // History snapshots are in the same world space, so they must move with it, or an
        // undo after a rebase would restore strokes at their pre-rebase coordinates.
        geometryEpoch++ // every stroke moved
        for (entry in undoStack + redoStack) {
            entry.log.shiftAll(offset)
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
        if (guides.isNotEmpty()) {
            for (i in guides.indices) guides[i] = guides[i].let { it.copy(position = it.position - if (it.vertical) offset.x else offset.y) }
            onGuidesChanged(guides.toList())
        }
        symmetryCenter -= offset
        pendingTextEdit = pendingTextEdit?.let { it.copy(worldPoint = it.worldPoint - offset) }
        onRebased(offset)
        originShift += offset
        panWorld -= offset
    }
}

/** A document's animation timing (saved per document): playback speed and the X-sheet's holds. */
data class AnimationSettings(
    val fps: Int = 12,
    val holds: Map<Int, Int> = emptyMap(),
    val mouths: Map<Int, MouthShape> = emptyMap(),
    val soundtrack: String? = null,
    /** Named frame ranges (roadmap item 145). */
    val tags: List<FrameTag> = emptyList(),
)

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

