package com.andene.fern.canvas

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Roadmap item 127: DXF export for CAD programs. */
@RunWith(RobolectricTestRunner::class)
class DxfExportTest {
    /** The file's (group code, value) pairs. */
    private fun pairs(dxf: String): List<Pair<Int, String>> =
        dxf.trimEnd().lines().chunked(2).map { (code, value) -> code.trim().toInt() to value }

    @Test
    fun strokesBecomePolylinesOnTheirLayersWithYUp() {
        val state = CanvasState()
        state.loadLayers(listOf(Layer("a", "Ink: final"), Layer("b", "Colour", visible = false)))
        state.loadStrokes(listOf(
            Stroke(Color.Red, 4.0, layerId = "a", dashed = true).apply { addPoint(WorldPoint(0.0, 0.0)); addPoint(WorldPoint(100.0, 0.0)) },
            Stroke(Color.Blue, 0.0, filled = true, layerId = "b").apply { addPoint(WorldPoint(0.0, 50.0)); addPoint(WorldPoint(50.0, 50.0)); addPoint(WorldPoint(25.0, 90.0)) },
        ))
        val groups = pairs(exportDocumentDxf(state)!!)
        assertEquals(0 to "SECTION", groups.first())
        assertEquals(0 to "EOF", groups.last())
        assertTrue(1 to "AC1009" in groups)
        // Layers: the colon replaced; the hidden one switched off (negative colour).
        val layerEntries = groups.indices.filter { groups[it] == (0 to "LAYER") }.map { groups[it + 1].second }
        assertEquals(listOf("Ink_ final", "Colour"), layerEntries)
        assertTrue(62 to "-7" in groups)

        val polylines = groups.indices.filter { groups[it] == (0 to "POLYLINE") }
        assertEquals(2, polylines.size)
        fun entity(start: Int) = groups.drop(start + 1).takeWhile { it.first != 0 }
        val line = entity(polylines[0])
        assertTrue(8 to "Ink_ final" in line)
        assertTrue(62 to "1" in line) // red
        assertTrue(420 to "${0xFF0000}" in line)
        assertTrue(6 to "DASHED" in line)
        assertTrue(40 to "4" in line)
        assertTrue(70 to "0" in line)
        val fill = entity(polylines[1])
        assertTrue(62 to "5" in fill) // blue
        assertTrue(70 to "1" in fill) // closed

        // y points up: the line (world y 0) sits above the fill (world y 50 to 90).
        val ys = groups.indices.filter { groups[it] == (0 to "VERTEX") }.map { i -> groups.drop(i).first { it.first == 20 }.second.toDouble() }
        assertEquals(5, ys.size)
        assertTrue(ys[0] > ys[2] && ys[2] > ys[4])
    }

    @Test
    fun layerNamesStayUniqueAndColoursMapToTheNearestBasicOne() {
        val names = dxfLayerNames(listOf(Layer("a", "Ink"), Layer("b", "ink"), Layer("c", "")))
        assertEquals(listOf("Ink", "ink 2", "Layer"), names.values.toList())
        assertEquals(7, nearestAci(Color(0.1f, 0.1f, 0.1f)))
        assertEquals(3, nearestAci(Color(0.2f, 0.8f, 0.3f)))
    }

    @Test
    fun anEmptyDocumentHasNoExport() {
        assertNull(exportDocumentDxf(CanvasState()))
    }
}
