package com.andene.fern.canvas

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Still exports show the animation frame on screen, not every frame at once (nor always the first). */
@RunWith(RobolectricTestRunner::class)
class ExportFrameTest {
    private fun line(frame: Int, color: Color) = Stroke(color, 2.0, animFrame = frame).apply {
        addPoint(WorldPoint(0.0, frame * 10.0)); addPoint(WorldPoint(100.0, frame * 10.0))
    }

    @Test
    fun svgAndExportFramingFollowTheCurrentFrame() {
        val state = CanvasState()
        state.loadStrokes(listOf(line(0, Color.Red), line(1, Color.Blue)))
        state.goToAnimFrame(1)
        val svg = exportDocumentSvg(state) { null }!!
        assertEquals(1, Regex("<path").findAll(svg).count())
        assertTrue(svg.contains("#0000ff") || svg.contains("#0000FF"))
        assertEquals(1, frameForExport(state, 256)!!.camera.currentAnimFrame)
    }

    @Test
    fun aDrawingOfRasterPaintAloneIsFramedAroundThePaint() {
        val state = CanvasState()
        state.addRasterLayer(8)
        state.activeWidthWorld = 10.0
        state.beginStroke(WorldPoint(300.0, 40.0)); state.extendStroke(WorldPoint(340.0, 40.0)); state.endStroke()
        assertTrue(state.strokes.isEmpty()) // raster paint makes no strokes
        val frame = frameForExport(state, 256)!!
        val center = androidx.compose.ui.geometry.Offset(frame.widthPx / 2f, frame.heightPx / 2f)
        val middle = frame.camera.screenToWorld(center, center)
        assertEquals(320.0, middle.x, 2.0)
        assertEquals(40.0, middle.y, 2.0)
    }
}
