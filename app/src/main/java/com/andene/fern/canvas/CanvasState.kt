package com.andene.fern.canvas

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
enum class StrokeAlignment(val label: String) {
    LEFT("Align left"),
    CENTER_X("Align centres horizontally"),
    RIGHT("Align right"),
    TOP("Align top"),
    CENTER_Y("Align centres vertically"),
    BOTTOM("Align bottom"),
    DISTRIBUTE_X("Distribute horizontally"),
    DISTRIBUTE_Y("Distribute vertically"),
}

enum class Tool { PEN, ERASER, SELECT, SHAPE, FILL, TEXT, RULER, EYEDROPPER }

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

        // Comic panel borders, in screen px at the zoom they're inserted at; on the §7 scale.
        private const val PANEL_BORDER_SCREEN = 3.0

        // Movement under this many screen px still counts as a tap; on the §7 scale.
        private const val TAP_SLOP_SCREEN = 8.0

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
    var activeTool by mutableStateOf(Tool.PEN)
    var activePenType by mutableStateOf(PenType.MARKER)
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

    /** Playback speed in frames per second; 12 is the classic hand-drawn animation rate ("on twos"). */
    var animFps by mutableIntStateOf(12)

    fun goToAnimFrame(frame: Int) {
        clearSelectionState()
        currentAnimFrame = frame.coerceIn(0, animFrameCount - 1)
    }

    /** Playback bounces back and forth (1-2-3-2-1...) instead of looping (1-2-3-1-2-3...). */
    var pingPong by mutableStateOf(false)

    private var playingBackwards = false

    /** Next frame for playback: wraps to the first when looping, reverses at either end in ping-pong. */
    fun advanceAnimFrame() {
        if (animFrameCount <= 1) return
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
        animFrameCount++
        currentAnimFrame = insertAt
        onChanged(strokes.toList())
    }

    /**
     * Automatic in-betweens: inserts [count] frames between the current frame and the next,
     * morphing each stroke of the current frame into the stroke at the same position (drawing
     * order) in the next frame. Both strokes are resampled to the same number of points by arc
     * length and interpolated linearly, along with width and colour. Strokes without a partner
     * (the frames have different stroke counts) are left out of the in-betweens. Undoable.
     */
    fun generateInBetweens(count: Int) {
        val from = currentAnimFrame
        val to = from + 1
        if (count < 1 || to >= animFrameCount) return
        val start = strokes.filter { it.animFrame == from && !it.filled }
        val end = strokes.filter { it.animFrame == to && !it.filled }
        val pairs = start.zip(end)
        if (pairs.isEmpty()) return
        recordHistory()
        clearSelectionState()
        // Make room: everything from the next frame on moves up by [count].
        for (i in strokes.indices) {
            if (strokes[i].animFrame >= to) strokes[i] = strokes[i].onAnimFrame(strokes[i].animFrame + count)
        }
        for (step in 1..count) {
            val t = step.toDouble() / (count + 1)
            for ((a, b) in pairs) {
                val samples = maxOf(a.points.size, b.points.size, 2)
                val pa = resamplePolyline(a.points, samples)
                val pb = resamplePolyline(b.points, samples)
                val color = androidx.compose.ui.graphics.lerp(a.color, b.color, t.toFloat())
                val tween = Stroke(color, a.widthWorld + (b.widthWorld - a.widthWorld) * t, a.penType, blendMode = a.blendMode, layerId = a.layerId, animFrame = from + step, dashed = a.dashed)
                for (i in 0 until samples) tween.addPoint(WorldPoint(pa[i].x + (pb[i].x - pa[i].x) * t, pa[i].y + (pb[i].y - pa[i].y) * t))
                strokes.add(tween)
            }
        }
        animFrameCount += count
        onChanged(strokes.toList())
    }

    /**
     * Motion path: with two or more strokes selected, the most recently drawn one is a path
     * and the rest move along it. Creates [steps] frames from the current one onward (adding
     * frames as needed); in each, a copy of the moving strokes is offset by how far along the
     * path that frame is, evenly by arc length. The path itself is a guide and is removed.
     * Moving strokes already on later frames are left as they are. One undo step.
     */
    fun animateAlongPath(steps: Int = MOTION_PATH_STEPS) {
        if (selection.size < 2 || steps < 2) return
        val path = selection.maxBy { strokes.indexOf(it) }
        val movers = selection.filter { it !== path }
        if (path.points.size < 2) return
        recordHistory()
        val route = resamplePolyline(path.points, steps)
        val start = route.first()
        strokes.remove(path)
        for (step in 1 until steps) {
            val offset = route[step] - start
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
        onChanged(strokes.toList())
    }

    /** Deletes the current frame and its strokes; later frames shift down. The last frame can't be deleted. */
    fun deleteAnimFrame() {
        if (animFrameCount <= 1) return
        recordHistory()
        clearSelectionState()
        val removed = currentAnimFrame
        strokes.removeAll { it.animFrame == removed }
        for (i in strokes.indices) {
            if (strokes[i].animFrame > removed) strokes[i] = strokes[i].onAnimFrame(strokes[i].animFrame - 1)
        }
        animFrameCount--
        currentAnimFrame = currentAnimFrame.coerceAtMost(animFrameCount - 1)
        onChanged(strokes.toList())
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
        onChanged(strokes.toList())
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
        onChanged(strokes.toList())
    }

    fun deleteFrame(id: String) {
        frames.removeAll { it.id == id }
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
        strokes.removeAll { it.layerId == id }
        layers.removeAll { it.id == id }
        if (activeLayerId == id) activeLayerId = layers.last().id
        onChanged(strokes.toList())
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
        strokes.removeAll { it.layerId in hidden }
        layers.removeAll { it.id in hidden }
        if (activeLayerId in hidden) activeLayerId = layers.last().id
        onChanged(strokes.toList())
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
        onChanged(strokes.toList())
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
    )

    var pendingTextEdit: PendingTextEdit? by mutableStateOf(null)
        private set

    /** Every placed image on the canvas. */
    val imageItems = mutableStateListOf<ImageItem>()

    /** This document's page background color, matching the default canvas backdrop until changed. */
    var backgroundColor by mutableStateOf(Color(0xFFF7F5F0))
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
        private set

    private var isMovingSelection = false
    private var lastDragPoint: WorldPoint? = null

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
    private class HistoryEntry(val strokes: List<Stroke>, var images: List<ImageItem>?, var texts: List<TextItem>? = null)

    private val undoStack = ArrayDeque<HistoryEntry>()
    private val redoStack = ArrayDeque<HistoryEntry>()

    private fun currentEntry() = HistoryEntry(strokes.map { it.deepCopy() }, imageItems.toList(), textItems.toList())

    /** Called at the start of every stroke-changing action; also invalidates redo, standard editor semantics. */
    private fun recordHistory() {
        undoStack.addLast(currentEntry())
        while (undoStack.size > MAX_UNDO_STEPS) undoStack.removeFirst()
        redoStack.clear()
    }

    /** Undo and redo stroke snapshots, oldest first, for [CanvasStorage.saveHistory]. */
    fun historySnapshot(): Pair<List<List<Stroke>>, List<List<Stroke>>> =
        undoStack.map { it.strokes } to redoStack.map { it.strokes }

    /** Restores history saved by [CanvasStorage.saveHistory]; call after [loadStrokes], which clears it. */
    fun loadHistory(undo: List<List<Stroke>>, redo: List<List<Stroke>>) {
        undoStack.clear()
        undoStack.addAll(undo.takeLast(MAX_UNDO_STEPS).map { HistoryEntry(it, null) })
        redoStack.clear()
        redoStack.addAll(redo.map { HistoryEntry(it, null) })
    }

    private fun restore(snapshot: List<Stroke>) = restore(HistoryEntry(snapshot, null))

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
        strokes.clear()
        strokes.addAll(entry.strokes.map { it.deepCopy() })
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
        if (!activeLayerEditable) return
        strokeStartDepth = undoStack.size
        strokeStartMillis = System.currentTimeMillis()
        strokeMovedAway = false
        recordHistory()
        clearSelectionState() // avoid a stale selection referencing strokes another tool is about to change
        val stroke = Stroke(
            activeColor.copy(alpha = activeColor.alpha * activeOpacity.toFloat()),
            activeWidthWorld / scale.coerceAtLeast(1e-300),
            activePenType,
            blendMode = activeBlendMode,
            layerId = activeLayerId,
            animFrame = currentAnimFrame,
            dashed = activeDashed,
        )
        stroke.addPoint(snappedToActiveGuide(worldPoint), pressure, tilt, orientation)
        rememberActiveColor()
        lastStrokeMoveMillis = System.currentTimeMillis()
        lastPointMillis = lastStrokeMoveMillis
        currentStroke = stroke
        strokes.add(stroke)
        symmetryCopies = symmetryTransforms(symmetryMode, symmetryCenter).map { transform ->
            val copy = Stroke(stroke.color, stroke.widthWorld, stroke.penType, blendMode = stroke.blendMode, layerId = stroke.layerId, animFrame = stroke.animFrame, dashed = stroke.dashed)
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
        addStrokePoint(stroke, snappedToActiveGuide(smoothed), effectivePressure, tilt, orientation)
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
        val shape = Stroke(stroke.color, stroke.widthWorld, stroke.penType, blendMode = stroke.blendMode, layerId = stroke.layerId, animFrame = stroke.animFrame, dashed = stroke.dashed)
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
            addStrokePoint(stroke, snappedToActiveGuide(raw), stroke.pressures.last(), stroke.tilts.last(), stroke.orientations.last())
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
        onChanged(strokes.toList())
    }

    /** World-space radius of the eraser at the current zoom level. */
    private val eraserRadiusWorld: Double
        get() = ERASER_RADIUS_SCREEN / scale.coerceAtLeast(1e-300)

    fun beginErase(worldPoint: WorldPoint) {
        recordHistory()
        clearSelectionState() // avoid a stale selection referencing strokes this erase is about to split/remove
        eraseAt(worldPoint)
    }

    fun continueErase(worldPoint: WorldPoint) {
        eraseAt(worldPoint)
    }

    fun endErase() {
        onChanged(strokes.toList())
        onImageChanged(imageItems.toList())
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
            imageItems.removeAll { image ->
                point.x >= image.position.x && point.x <= image.position.x + image.widthWorld &&
                    point.y >= image.position.y && point.y <= image.position.y + image.heightWorld
            }
            strokes.removeAll { stroke ->
                isEditable(stroke) && (
                    if (stroke.filled) polygonContains(stroke.points, point)
                    else distanceToPolyline(stroke.points, point) <= radius + stroke.widthWorld / 2
                )
            }
            return
        }
        for (stroke in strokes.toList()) {
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
        shapeStart = snappedToGrid(worldPoint)
        shapePreview = shapeStart to shapeStart
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
        // Grid snapping already gives clean geometry; angle/aspect snapping applies only off the grid.
        val end = snappedToGrid(worldPoint)
        shapePreview = shapeStart to if (end != worldPoint) end else snappedShapeEnd(activeShapeKind, shapeStart, worldPoint)
    }

    /** Commits the current shape preview as a new stroke. No-op if there was no actual drag (a zero-size shape). */
    fun endShape() {
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
        )
        for (point in generateShapePoints(activeShapeKind, start, end)) stroke.addPoint(point)
        strokes.add(stroke)
        onChanged(strokes.toList())
    }

    /**
     * FILL tool: tapping inside a closed stroke fills its interior with [activeColor]. Since
     * this is a vector canvas (no pixel grid), "fill" means inserting a new filled-polygon
     * stroke using that boundary's exact points, positioned just beneath it in draw order so
     * the boundary's own outline still shows on top - not a raster flood fill, which
     * wouldn't make sense on an infinite canvas with no fixed resolution.
     *
     * Only strokes whose first and last point are close together (a "closed enough" loop) are
     * eligible; among all that actually contain [worldPoint], the smallest by area wins, so
     * tapping inside a small shape drawn inside a larger one fills the small one, matching
     * real fill-tool expectations. No-op if nothing qualifies.
     */
    fun fillAt(worldPoint: WorldPoint) {
        if (!activeLayerEditable) return
        recordHistory()
        rememberActiveColor()
        clearSelectionState()
        val closeTolerance = FILL_CLOSE_TOLERANCE_SCREEN / scale.coerceAtLeast(1e-300)
        var bestIndex = -1
        var bestArea = Double.POSITIVE_INFINITY
        for (index in strokes.indices) {
            val stroke = strokes[index]
            if (stroke.filled || !isVisible(stroke)) continue
            val points = stroke.points
            if (!isClosedLoop(points, closeTolerance)) continue
            if (!polygonContains(points, worldPoint)) continue
            val area = polygonArea(points)
            if (area < bestArea) {
                bestArea = area
                bestIndex = index
            }
        }
        if (bestIndex == -1) return
        val boundary = strokes[bestIndex]
        val fillStroke = Stroke(activeColor, 0.0, PenType.MARKER, filled = true, layerId = activeLayerId, animFrame = currentAnimFrame, fadeFill = activeFadeFill, screentone = activeScreentone)
        for (point in boundary.points) fillStroke.addPoint(point)
        strokes.add(bestIndex, fillStroke)
        onChanged(strokes.toList())
    }

    /**
     * TEXT tool, pointer-down: opens [pendingTextEdit] for the entry dialog to act on -
     * editing whatever label already sits at [worldPoint] (per [findTextItemAt]), or a fresh
     * one to be placed there.
     */
    fun beginTextEdit(worldPoint: WorldPoint) {
        val existing = findTextItemAt(worldPoint)
        pendingTextEdit = if (existing != null) {
            PendingTextEdit(existing.id, existing.position, existing.text, existing.bold, existing.italic, existing.sticky, existing.fontSizeWorld * scale)
        } else {
            PendingTextEdit(null, worldPoint, "", initialSizePx = activeTextSizeWorld)
        }
    }

    /**
     * Applies the entry dialog's result: blank text deletes the label being edited (a no-op
     * if this was a fresh placement), otherwise creates a new label or updates the edited
     * one's text in place.
     */
    fun confirmTextEdit(text: String, bold: Boolean = false, italic: Boolean = false, sticky: Boolean = false, sizePx: Double = activeTextSizeWorld) {
        // The chosen size also becomes the default for the next new label.
        activeTextSizeWorld = sizePx
        val fontSizeWorld = sizePx / scale.coerceAtLeast(1e-300)
        val pending = pendingTextEdit ?: return
        pendingTextEdit = null
        recordHistory()
        if (text.isBlank()) {
            if (pending.id != null && textItems.removeAll { it.id == pending.id }) {
                onTextChanged(textItems.toList())
            }
            return
        }
        val existingIndex = pending.id?.let { id -> textItems.indexOfFirst { it.id == id } } ?: -1
        if (existingIndex != -1) {
            textItems[existingIndex] = textItems[existingIndex].copy(text = text, bold = bold, italic = italic, sticky = sticky, fontSizeWorld = fontSizeWorld)
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
                )
            )
        }
        onTextChanged(textItems.toList())
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

    /** Approximate world-space box of a text label (average glyph width), for hit-testing and the selection highlight. */
    fun textBounds(item: TextItem): Pair<WorldPoint, WorldPoint> = item.position to WorldPoint(
        item.position.x + item.text.length * item.fontSizeWorld * 0.55,
        item.position.y + item.fontSizeWorld * 1.3,
    )

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

    fun beginSelectGesture(worldPoint: WorldPoint) {
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
        } else {
            isMovingSelection = false
            selection.clear()
            if (lassoSelect) lassoPath.add(worldPoint) else marqueeRect = worldPoint to worldPoint
        }
    }

    fun continueSelectGesture(worldPoint: WorldPoint) {
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
            val last = lastDragPoint ?: return
            val delta = worldPoint - last
            for (stroke in selection) stroke.shiftBy(WorldPoint(-delta.x, -delta.y))
            lastDragPoint = worldPoint
        } else if (lassoSelect) {
            if (lassoPath.isNotEmpty()) lassoPath.add(worldPoint)
        } else {
            val start = marqueeRect?.first ?: return
            marqueeRect = start to worldPoint
        }
    }

    fun endSelectGesture() {
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
            onChanged(strokes.toList())
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
        onChanged(strokes.toList())
    }

    /**
     * Starts rotating the selection around its bounding box's center. No-op if nothing is
     * selected.
     */
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
        onChanged(strokes.toList())
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
        onChanged(strokes.toList())
    }

    /** Moves the selected strokes to the top ([toFront]) or bottom of the drawing order, keeping their order relative to each other. */
    fun reorderSelection(toFront: Boolean) {
        if (selection.isEmpty()) return
        recordHistory()
        val selected = selection.toSet()
        val moving = strokes.filter { it in selected }
        strokes.removeAll(selected)
        if (toFront) strokes.addAll(moving) else strokes.addAll(0, moving)
        onChanged(strokes.toList())
    }

    // App-level clipboard. CanvasState outlives document switches, so this also carries strokes
    // between documents. Held as independent copies so later edits don't change what's pasted.
    private var clipboard: List<Stroke> = emptyList()

    var hasClipboard by mutableStateOf(false)
        private set

    fun copySelection() {
        if (selection.isEmpty()) return
        clipboard = selection.map { it.deepCopy() }
        hasClipboard = true
    }

    /** Pastes the clipboard centred in the current view and selects the pasted strokes. */
    fun paste() {
        if (clipboard.isEmpty()) return
        recordHistory()
        val pasted = clipboard.map { it.deepCopy() }
        val centerX = (pasted.minOf { it.minX } + pasted.maxOf { it.maxX }) / 2.0
        val centerY = (pasted.minOf { it.minY } + pasted.maxOf { it.maxY }) / 2.0
        // shiftBy subtracts, so shifting by (old centre - view centre) lands it at the view centre.
        val offset = WorldPoint(centerX - panWorld.x, centerY - panWorld.y)
        pasted.forEach { it.shiftBy(offset) }
        strokes.addAll(pasted)
        clearSelectionState()
        selection.addAll(pasted)
        onChanged(strokes.toList())
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
        onChanged(strokes.toList())
    }

    /** Sets every selected stroke's width to the current brush width (as it would draw at this zoom). Fills have no width. */
    fun applyWidthToSelection() {
        if (selection.isEmpty()) return
        recordHistory()
        val width = activeWidthWorld / scale.coerceAtLeast(1e-300)
        for (stroke in selection) if (!stroke.filled) stroke.setWidth(width)
        onChanged(strokes.toList())
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
        onChanged(strokes.toList())
    }

    /** Smooths the selected strokes' wobble after the fact; repeatable for a stronger effect. Filled shapes are left alone. */
    fun smoothSelection() {
        if (selection.isEmpty()) return
        recordHistory()
        for (stroke in selection) {
            if (!stroke.filled) stroke.setPoints(smoothPoints(stroke.points, SMOOTH_PASSES))
        }
        onChanged(strokes.toList())
    }

    fun deleteSelection() {
        val textId = selectedTextId
        if (textId != null) {
            recordHistory()
            textItems.removeAll { it.id == textId }
            selectedTextId = null
            onTextChanged(textItems.toList())
            return
        }
        val imageId = selectedImageId
        if (imageId != null) {
            recordHistory()
            imageItems.removeAll { it.id == imageId }
            selectedImageId = null
            onImageChanged(imageItems.toList())
            return
        }
        if (selection.isEmpty()) return
        recordHistory()
        strokes.removeAll(selection.toSet())
        selection.clear()
        onChanged(strokes.toList())
    }

    /** Duplicates every currently selected stroke, offset slightly so the copy is visible, and selects the copies. */
    fun duplicateSelection() {
        if (selection.isEmpty()) return
        recordHistory()
        val nudge = WorldPoint(20.0 / scale.coerceAtLeast(1e-300), 20.0 / scale.coerceAtLeast(1e-300))
        val duplicates = selection.map { original ->
            val copy = Stroke(original.color, original.widthWorld, original.penType, original.filled, original.blendMode, original.layerId, original.animFrame, original.dashed, original.fadeFill, original.screentone)
            for (point in original.points) copy.addPoint(point + nudge)
            copy
        }
        strokes.addAll(duplicates)
        selection.clear()
        selection.addAll(duplicates)
        onChanged(strokes.toList())
    }

    /** Clears the selection and any in-progress scale/rotate snapshot together, so neither can outlive the strokes it references. */
    private fun clearSelectionState() {
        selection.clear()
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
        onChanged(strokes.toList())
    }

    fun undo() {
        val previous = undoStack.removeLastOrNull() ?: return
        clearSelectionState()
        redoStack.addLast(currentEntry())
        restore(previous)
        onChanged(strokes.toList())
    }

    fun redo() {
        val next = redoStack.removeLastOrNull() ?: return
        clearSelectionState()
        undoStack.addLast(currentEntry())
        restore(next)
        onChanged(strokes.toList())
    }

    /** Replaces the strokes with a saved version; undoable like any other action. */
    fun restoreVersion(version: List<Stroke>) {
        recordHistory()
        clearSelectionState()
        restore(version)
        onChanged(strokes.toList())
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
        onChanged(strokes.toList())
        textItems.clear()
        onTextChanged(textItems.toList())
        imageItems.clear()
        onImageChanged(imageItems.toList())
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
        for (entry in undoStack + redoStack) {
            for (stroke in entry.strokes) stroke.shiftBy(offset)
            entry.images = entry.images?.map { it.copy(position = it.position - offset) }
            entry.texts = entry.texts?.map { it.copy(position = it.position - offset) }
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
