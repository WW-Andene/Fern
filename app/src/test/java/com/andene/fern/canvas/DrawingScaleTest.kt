package com.andene.fern.canvas

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Roadmap item 143: real-world scale, measurement and calibration. */
@RunWith(RobolectricTestRunner::class)
class DrawingScaleTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun formatsWithThreeSignificantDigits() {
        assertEquals("1234", formatMeasure(1234.4))
        assertEquals("12.3", formatMeasure(12.345))
        assertEquals("0.0123", formatMeasure(0.012345))
        assertEquals("100", formatMeasure(99.96))
        assertEquals("0", formatMeasure(0.0))
    }

    @Test
    fun lengthsAndAreasReadInTheScaleUnit() {
        val scale = DrawingScale(MeasureUnit.CENTIMETRE, 0.5)
        assertEquals("50.0 cm", scale.formatLength(100.0))
        assertEquals("25.0 cm²", scale.formatArea(100.0))
        val none: DrawingScale? = null
        assertEquals("100 px", none.formatLength(100.0))
    }

    @Test
    fun calibratingFromAMeasuredLine() {
        val scale = DrawingScale.calibrated(worldLength = 200.0, realLength = 5.0, unit = MeasureUnit.METRE)!!
        assertEquals(0.025, scale.unitsPerWorld, 1e-12)
        assertNull(DrawingScale.calibrated(0.0, 5.0, MeasureUnit.METRE))
        assertNull(DrawingScale.calibrated(10.0, -1.0, MeasureUnit.METRE))
    }

    @Test
    fun scaleIsSavedRemovedAndDuplicated() {
        CanvasStorage.saveDrawingScale(context, "doc", DrawingScale(MeasureUnit.FOOT, 0.1))
        assertEquals(DrawingScale(MeasureUnit.FOOT, 0.1), CanvasStorage.loadDrawingScale(context, "doc"))
        CanvasStorage.saveDrawingScale(context, "doc", null)
        assertNull(CanvasStorage.loadDrawingScale(context, "doc"))
        val original = CanvasStorage.createDocument(context, "A")
        CanvasStorage.saveDrawingScale(context, original.id, DrawingScale(MeasureUnit.INCH, 2.0))
        val copy = CanvasStorage.duplicateDocument(context, original.id, "B")
        assertEquals(DrawingScale(MeasureUnit.INCH, 2.0), CanvasStorage.loadDrawingScale(context, copy.id))
    }

    @Test
    fun measureToolDragsMeasureLinesAndTapsMeasureShapes() {
        val state = CanvasState()
        val square = Stroke(Color.Black, 2.0, filled = true).apply {
            for (p in listOf(WorldPoint(0.0, 0.0), WorldPoint(100.0, 0.0), WorldPoint(100.0, 100.0), WorldPoint(0.0, 100.0), WorldPoint(0.0, 0.0))) addPoint(p)
        }
        state.loadStrokes(listOf(square))
        state.snapToObjects = false
        state.activeTool = Tool.MEASURE
        state.measureTool.begin(WorldPoint(200.0, 0.0))
        state.measureTool.move(WorldPoint(230.0, 40.0))
        state.measureTool.end()
        assertEquals(50.0, state.measureTool.measurement!!.length, 1e-9)
        // A tap inside the square measures its perimeter and area.
        state.measureTool.begin(WorldPoint(50.0, 50.0))
        state.measureTool.end()
        val shape = state.measureTool.measurement!!
        assertTrue(shape.closed)
        assertEquals(400.0, shape.length, 1e-9)
        assertEquals(10000.0, shape.area, 1e-9)
        // A tap on nothing clears it; leaving the tool too.
        state.measureTool.begin(WorldPoint(500.0, 500.0))
        state.measureTool.end()
        assertNull(state.measureTool.measurement)
        state.measureTool.begin(WorldPoint(50.0, 50.0))
        state.measureTool.end()
        state.activeTool = Tool.PEN
        assertNull(state.measureTool.measurement)
    }

    @Test
    fun shapeReadoutUsesTheScale() {
        assertEquals("5.00 m · -53°", shapeDimensions(ShapeKind.LINE, WorldPoint(0.0, 0.0), WorldPoint(300.0, 400.0), DrawingScale(MeasureUnit.METRE, 0.01)))
        assertEquals("3.00 × 4.00 m", shapeDimensions(ShapeKind.RECTANGLE, WorldPoint(0.0, 0.0), WorldPoint(300.0, 400.0), DrawingScale(MeasureUnit.METRE, 0.01)))
        assertEquals("500 · -53°", shapeDimensions(ShapeKind.LINE, WorldPoint(0.0, 0.0), WorldPoint(300.0, 400.0)))
    }
}
