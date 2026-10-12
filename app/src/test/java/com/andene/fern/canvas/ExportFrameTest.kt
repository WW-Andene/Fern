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
}
