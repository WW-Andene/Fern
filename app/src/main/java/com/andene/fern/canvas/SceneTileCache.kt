package com.andene.fern.canvas

import android.os.Build
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** Tile edge in pixels at the tile's own zoom; on the §7 scale. */
const val TILE_PX = 256

/** Documents with fewer strokes than this render directly: they're fast enough, and nothing changes for them. */
const val TILE_CACHE_MIN_STROKES = 20_000

// Tiles kept in memory (each 256 x 256 x 4 bytes = 256 KB, so about 40 MB). A view needing more
// than this at once renders directly instead.
private const val MAX_TILES = 160

/**
 * Cache of the rendered scene in world-aligned tiles (roadmap item 51), so panning and zooming a
 * large document redraws a few hundred bitmaps instead of every stroke in view.
 *
 * Tiles are rendered per zoom octave: at scale s the octave is z = round(log2 s), whose tiles
 * are [TILE_PX] px at scale 2^z, drawn here scaled by s / 2^z (0.71x to 1.41x). They hold
 * everything [drawScene] draws - paper, strokes, images, text - rendered by a private camera:
 * a [CanvasState] with copy-on-write copies of the content and no floating origin, so tile
 * rendering never moves the live view or touches the live document. Any change to the scene ([CanvasState.sceneVersion])
 * drops every tile.
 *
 * Used only when [CanvasState.tileCacheEligible] holds: a large document with nothing being
 * edited in place (a stroke being drawn, a selection being moved) and no animation playback,
 * since those change every frame and must be drawn live in their correct stacking order.
 */
class SceneTileCache(private val useGpu: Boolean = true) {
    private data class Key(val octave: Int, val x: Long, val y: Long)

    // Access-ordered: the least recently drawn tile is evicted first.
    private val tiles = object : LinkedHashMap<Key, ImageBitmap>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, ImageBitmap>?) = size > MAX_TILES
    }
    private var version: SceneVersion? = null
    private var camera: CanvasState? = null

    // GPU tile rasterization (GpuTileRenderer) where available; dropped for the session after a failure.
    private var gpu: GpuTileRenderer? = null
    private var gpuFailed = !useGpu || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q

    /** Tiles the GPU path rendered, for tests and benchmarks. */
    var gpuTilesRendered = 0
        private set

    /** Tiles rendered so far (cache misses), for tests and benchmarks. */
    var tilesRendered = 0
        private set

    /**
     * Draws the scene from tiles, rendering any that are missing. Returns false (drawing nothing)
     * if the view needs more tiles than the cache holds; the caller then draws directly.
     */
    fun draw(
        scope: DrawScope,
        state: CanvasState,
        screenCenter: Offset,
        topLeftWorld: WorldPoint,
        bottomRightWorld: WorldPoint,
        textMeasurer: TextMeasurer,
        imageFor: (String) -> ImageBitmap?,
    ): Boolean {
        val octave = (ln(state.scale) / ln(2.0)).roundToInt()
        val tileWorld = TILE_PX / Math.scalb(1.0, octave)
        val x0 = floor(topLeftWorld.x / tileWorld).toLong()
        val x1 = floor(bottomRightWorld.x / tileWorld).toLong()
        val y0 = floor(topLeftWorld.y / tileWorld).toLong()
        val y1 = floor(bottomRightWorld.y / tileWorld).toLong()
        if ((x1 - x0 + 1) * (y1 - y0 + 1) > MAX_TILES) return false

        val current = state.sceneVersion()
        if (current != version) {
            tiles.clear()
            camera = null
            version = current
        }
        val tileCamera = camera ?: cameraFor(state).also { camera = it }

        for (tx in x0..x1) for (ty in y0..y1) {
            val key = Key(octave, tx, ty)
            val image = tiles[key] ?: render(tileCamera, octave, tx, ty, tileWorld, textMeasurer, imageFor, scope).also { tiles[key] = it }
            // Edges rounded from the shared world grid lines, so neighbouring tiles meet exactly.
            val topLeft = state.worldToScreen(WorldPoint(tx * tileWorld, ty * tileWorld), screenCenter)
            val bottomRight = state.worldToScreen(WorldPoint((tx + 1) * tileWorld, (ty + 1) * tileWorld), screenCenter)
            val left = topLeft.x.roundToLong().toInt()
            val top = topLeft.y.roundToLong().toInt()
            val width = bottomRight.x.roundToLong().toInt() - left
            val height = bottomRight.y.roundToLong().toInt() - top
            if (width <= 0 || height <= 0) continue
            scope.drawImage(image, dstOffset = IntOffset(left, top), dstSize = IntSize(width, height), filterQuality = FilterQuality.Low)
        }
        return true
    }

    // No floating origin: the camera shares the live document's coordinates, so tile positions
    // need no translation, and distant tiles don't shift and re-index its whole copy.
    private fun cameraFor(state: CanvasState): CanvasState =
        CanvasState(floatingOrigin = false).apply {
            loadStrokes(state.strokes.map { it.deepCopy() })
            loadLayers(state.layers.toList())
            loadTextItems(state.textItems.toList())
            loadConnectors(state.connectors.toList())
            loadImageItems(state.imageItems.toList())
            loadBackground(state.backgroundColor, state.backgroundStyle)
            goToAnimFrame(state.currentAnimFrame)
        }

    private fun render(
        camera: CanvasState,
        octave: Int,
        tx: Long,
        ty: Long,
        tileWorld: Double,
        textMeasurer: TextMeasurer,
        imageFor: (String) -> ImageBitmap?,
        density: Density,
    ): ImageBitmap {
        val c = WorldPoint((tx + 0.5) * tileWorld, (ty + 0.5) * tileWorld)
        camera.jumpTo(c, Math.scalb(1.0, octave))
        val half = tileWorld / 2
        tilesRendered++
        val drawTile: DrawScope.() -> Unit = {
            drawRect(color = camera.backgroundColor)
            drawScene(camera, Offset(TILE_PX / 2f, TILE_PX / 2f), WorldPoint(c.x - half, c.y - half), WorldPoint(c.x + half, c.y + half), textMeasurer, imageFor)
        }
        if (!gpuFailed && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val renderer = gpu ?: runCatching { GpuTileRenderer(TILE_PX) }.getOrNull()?.also { gpu = it }
            val image = renderer?.render(density, drawTile)
            if (image != null) {
                gpuTilesRendered++
                return image
            }
            gpuFailed = true
            gpu?.release()
            gpu = null
        }
        val image = ImageBitmap(TILE_PX, TILE_PX)
        CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(image), Size(TILE_PX.toFloat(), TILE_PX.toFloat()), drawTile)
        return image
    }

    /** Frees the GPU renderer; the cache keeps working (on the CPU) if drawn again. */
    fun release() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) gpu?.release()
        gpu = null
    }
}

/**
 * Everything the rendered scene depends on, compared by identity: the content lists are
 * immutable snapshots that change identity whenever they change ([CanvasState.sceneVersion]).
 */
class SceneVersion(private val parts: List<Any?>) {
    override fun equals(other: Any?): Boolean {
        if (other !is SceneVersion || other.parts.size != parts.size) return false
        for (i in parts.indices) {
            val a = parts[i]
            val b = other.parts[i]
            // Lists by identity (O(1)); small values by equality.
            if (a is List<*> || b is List<*>) { if (a !== b) return false } else if (a != b) return false
        }
        return true
    }

    override fun hashCode(): Int = parts.size
}
