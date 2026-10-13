package com.andene.fern.canvas

import android.content.Context
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.core.app.ApplicationProvider
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.cos
import kotlin.math.sin

/**
 * Scale benchmark suite (roadmap item 48): how storage, rendering and hit-testing cost grows at
 * 10k, 100k and 1M strokes. Skipped in normal test runs; run with
 * `./gradlew testDebugUnitTest -Pbenchmark --tests '*ScaleBenchmark'`. Results are printed and
 * written to app/build/reports/benchmarks/scale.txt.
 *
 * These are JVM numbers (Robolectric's software Skia for rendering), so they compare
 * configurations and catch regressions; they are not frame times on a phone, which needs an
 * on-device benchmark. Each measurement is the median of [RUNS] after one warm-up run.
 *
 * Strokes are random-walk scribbles of [POINTS_PER_STROKE] points laid out in a square grid,
 * one per 64 world units, so the canvas grows with the count like a real document does.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScaleBenchmark {
    private val context: Context = ApplicationProvider.getApplicationContext()
    // Written line by line, so a long run's results so far survive an interrupted run.
    private val report = File("build/reports/benchmarks").apply { mkdirs() }.resolve("scale.txt").apply { writeText("") }

    @Before
    fun onlyWhenAsked() {
        assumeTrue("Benchmarks run only with -Pbenchmark", System.getProperty("fern.benchmark") == "true")
    }

    @Test
    fun scale() {
        for (count in listOf(10_000, 100_000, 1_000_000)) {
            val strokes = scribbles(count)
            line("== $count strokes x $POINTS_PER_STROKE points ==")
            benchmarkStorage(strokes)
            benchmarkRendering(strokes)
            benchmarkHitTesting(strokes)
        }
    }

    private fun benchmarkStorage(strokes: List<Stroke>) {
        val doc = CanvasStorage.createDocument(context, "bench")
        CanvasStorage.load(context, doc.id)
        // A full save: the journal only takes appends, so pass a list that isn't one.
        measure("full save") { CanvasStorage.compactStrokes(context, doc.id); CanvasStorage.save(context, doc.id, strokes.reversed(), withThumbnail = false); CanvasStorage.save(context, doc.id, strokes, withThumbnail = false) }
        var loaded: List<Stroke> = emptyList()
        // The previous copy is dropped first: at a million strokes two loaded copies (~0.7 GB each) plus the original overflow the heap.
        measure("load") { loaded = emptyList(); loaded = CanvasStorage.load(context, doc.id) }
        val extra = scribbles(1, offset = 1e7)
        var current = loaded
        measure("autosave after one new stroke") { current = current + extra.single().deepCopy(); CanvasStorage.save(context, doc.id, current, withThumbnail = false) }
        CanvasStorage.deleteDocument(context, doc.id)
    }

    private fun benchmarkRendering(strokes: List<Stroke>) {
        val state = CanvasState()
        state.loadStrokes(strokes)
        val side = gridSide(strokes.size) * CELL
        val textMeasurer = TextMeasurer(createFontFamilyResolver(context), Density(1f), LayoutDirection.Ltr)
        val image = ImageBitmap(VIEW_PX, VIEW_PX)
        fun frame(center: WorldPoint, scale: Double) {
            state.jumpTo(center, scale)
            val c = state.panWorld // after any floating-origin rebase
            val half = VIEW_PX / 2.0 / scale
            CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, androidx.compose.ui.graphics.Canvas(image), Size(VIEW_PX.toFloat(), VIEW_PX.toFloat())) {
                drawScene(state, Offset(VIEW_PX / 2f, VIEW_PX / 2f), WorldPoint(c.x - half, c.y - half), WorldPoint(c.x + half, c.y + half), textMeasurer, { null })
            }
        }
        val middle = WorldPoint(side / 2, side / 2)
        measure("spatial index build") { StrokeSpatialIndex(state.strokes.toList()) }
        measure("render: whole document in view") { frame(middle, VIEW_PX / side) }
        // The same view from the tile cache: a pan frame once the tiles exist.
        val tiles = SceneTileCache()
        fun tiledFrame(center: WorldPoint, scale: Double) {
            state.jumpTo(center, scale)
            val c = state.panWorld
            val half = VIEW_PX / 2.0 / scale
            CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, androidx.compose.ui.graphics.Canvas(image), Size(VIEW_PX.toFloat(), VIEW_PX.toFloat())) {
                tiles.draw(this, state, Offset(VIEW_PX / 2f, VIEW_PX / 2f), WorldPoint(c.x - half, c.y - half), WorldPoint(c.x + half, c.y + half), textMeasurer) { null }
            }
        }
        val firstStart = System.nanoTime()
        tiledFrame(middle, VIEW_PX / side)
        line("%-48s %10.1f ms".format("render: whole document, filling the tile cache", (System.nanoTime() - firstStart) / 1e6))
        measure("render: whole document from cached tiles") { tiledFrame(middle, VIEW_PX / side) }
        measure("render: mid zoom (about 1000 strokes in view)") { frame(middle, VIEW_PX / (32.0 * CELL)) }
        measure("render: zoomed in (about 16 strokes in view)") { frame(middle, VIEW_PX / (4.0 * CELL)) }
    }

    private fun benchmarkHitTesting(strokes: List<Stroke>) {
        val point = WorldPoint(CELL * 1.5, CELL * 1.5)
        measure("snap-anchor search") { nearestSnapAnchor(strokes, point, 12.0) }
        val state = CanvasState()
        state.loadStrokes(strokes)
        state.eraseWholeObjects = true
        measure("object-eraser touch") { state.eraserTool.begin(point); state.eraserTool.end(); state.undo() }
        // A finger moving over the canvas: the per-event cost, without the per-gesture undo snapshot.
        val empty = WorldPoint(-CELL * 10, -CELL * 10)
        state.eraserTool.begin(empty)
        measure("eraser move, nothing under it") { state.eraserTool.move(empty) }
        state.eraserTool.end()
    }

    private fun measure(label: String, block: () -> Unit) {
        block() // warm-up
        val times = List(RUNS) {
            val start = System.nanoTime()
            block()
            (System.nanoTime() - start) / 1e6
        }.sorted()
        line("%-48s %10.1f ms".format(label, times[RUNS / 2]))
    }

    private fun line(text: String) {
        println(text)
        report.appendText(text + "\n")
    }

    private fun gridSide(count: Int): Int = kotlin.math.ceil(kotlin.math.sqrt(count.toDouble())).toInt()

    private fun scribbles(count: Int, offset: Double = 0.0): List<Stroke> {
        val side = gridSide(count)
        val random = java.util.Random(42)
        return List(count) { i ->
            Stroke(Color.Black, 2.0).apply {
                var x = offset + (i % side) * CELL + CELL / 2
                var y = offset + (i / side) * CELL + CELL / 2
                var angle = random.nextDouble() * 2 * Math.PI
                repeat(POINTS_PER_STROKE) {
                    addPoint(WorldPoint(x, y))
                    angle += (random.nextDouble() - 0.5)
                    x += cos(angle) * 3
                    y += sin(angle) * 3
                }
            }
        }
    }

    private companion object {
        const val POINTS_PER_STROKE = 16
        const val CELL = 64.0
        const val VIEW_PX = 1024
        const val RUNS = 5
    }
}
