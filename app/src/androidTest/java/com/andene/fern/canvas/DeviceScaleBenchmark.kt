package com.andene.fern.canvas

import android.content.Context
import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.cos
import kotlin.math.sin

/**
 * On-device counterpart of the JVM ScaleBenchmark (roadmap item 48): the same scribble
 * documents measured on the phone's own CPU and Skia, so frame costs are a device's, not a
 * desktop JVM's. Run on a connected device with
 * `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.andene.fern.canvas.DeviceScaleBenchmark`;
 * results land in the instrumentation output and `app/build/outputs/connected_android_test_additional_output`.
 *
 * Sizes are 10k and 100k strokes: a million 16-point strokes needs ~0.7 GB of heap, more than
 * most phones give an app. Pass `-Pandroid.testInstrumentationRunnerArguments.fern.strokes=1000000`
 * to try it on a device that has the memory.
 */
@RunWith(AndroidJUnit4::class)
class DeviceScaleBenchmark {
    @get:Rule
    val benchmark = BenchmarkRule()

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val count = InstrumentationRegistry.getArguments().getString("fern.strokes")?.toIntOrNull()

    private fun state(strokes: Int): CanvasState = CanvasState().apply { loadStrokes(scribbles(strokes)) }

    private fun frame(state: CanvasState, strokes: Int, viewWorld: Double) {
        val textMeasurer = TextMeasurer(createFontFamilyResolver(context), Density(1f), LayoutDirection.Ltr)
        val image = ImageBitmap(VIEW_PX, VIEW_PX)
        val side = gridSide(strokes) * CELL
        state.jumpTo(WorldPoint(side / 2, side / 2), VIEW_PX / viewWorld)
        benchmark.measureRepeated {
            val c = state.panWorld
            val half = viewWorld / 2
            CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, androidx.compose.ui.graphics.Canvas(image), Size(VIEW_PX.toFloat(), VIEW_PX.toFloat())) {
                drawScene(state, Offset(VIEW_PX / 2f, VIEW_PX / 2f), WorldPoint(c.x - half, c.y - half), WorldPoint(c.x + half, c.y + half), textMeasurer, { null })
            }
        }
    }

    @Test
    fun frameZoomedIn10k() = frame(state(10_000), 10_000, 4 * CELL)

    @Test
    fun frameMidZoom10k() = frame(state(10_000), 10_000, 32 * CELL)

    @Test
    fun frameZoomedIn100k() = (count ?: 100_000).let { frame(state(it), it, 4 * CELL) }

    @Test
    fun frameMidZoom100k() = (count ?: 100_000).let { frame(state(it), it, 32 * CELL) }

    @Test
    fun eraserTouchWithUndo100k() {
        val strokes = count ?: 100_000
        val state = state(strokes)
        state.eraseWholeObjects = true
        val point = WorldPoint(CELL * 1.5, CELL * 1.5)
        benchmark.measureRepeated {
            state.beginErase(point)
            state.endErase()
            state.undo()
        }
    }

    @Test
    fun autosaveAfterOneStroke100k() {
        val strokes = scribbles(count ?: 100_000)
        val doc = CanvasStorage.createDocument(context, "device benchmark")
        CanvasStorage.save(context, doc.id, strokes, withThumbnail = false)
        var current = strokes
        benchmark.measureRepeated {
            current = current + scribbles(1, offset = 1e7).single()
            CanvasStorage.save(context, doc.id, current, withThumbnail = false)
        }
        CanvasStorage.deleteDocument(context, doc.id)
    }

    private fun gridSide(count: Int): Int = kotlin.math.ceil(kotlin.math.sqrt(count.toDouble())).toInt()

    // Same documents as the JVM ScaleBenchmark: random-walk scribbles on a 64-unit grid.
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
    }
}
