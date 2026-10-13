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

@RunWith(RobolectricTestRunner::class)
class ConnectorTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun segmentRunsEdgeToEdge() {
        val (a, b) = connectorSegment(WorldPoint(0.0, 0.0), WorldPoint(10.0, 10.0), WorldPoint(30.0, 0.0), WorldPoint(40.0, 10.0))!!
        assertEquals(WorldPoint(10.0, 5.0), a)
        assertEquals(WorldPoint(30.0, 5.0), b)
        assertNull(connectorSegment(WorldPoint(0.0, 0.0), WorldPoint(10.0, 10.0), WorldPoint(2.0, 2.0), WorldPoint(12.0, 12.0)))
    }

    private fun twoNotes(): CanvasState = CanvasState().apply {
        loadTextItems(listOf(
            TextItem("a", "Start", WorldPoint(0.0, 0.0), 20.0, Color.Black, sticky = true),
            TextItem("b", "End", WorldPoint(300.0, 0.0), 20.0, Color.Black, sticky = true),
        ))
    }

    private fun connect(state: CanvasState, from: WorldPoint, to: WorldPoint) {
        state.connectTool.begin(from); state.connectTool.move(to); state.connectTool.end()
    }

    @Test
    fun dragFromOneLabelToAnotherConnectsThemOnce() {
        val state = twoNotes()
        connect(state, WorldPoint(5.0, 5.0), WorldPoint(305.0, 5.0))
        assertEquals(listOf("a" to "b"), state.connectors.map { it.fromId to it.toId })
        connect(state, WorldPoint(5.0, 5.0), WorldPoint(305.0, 5.0)) // same again: no duplicate
        connect(state, WorldPoint(5.0, 5.0), WorldPoint(8.0, 5.0)) // onto itself: nothing
        connect(state, WorldPoint(5.0, 5.0), WorldPoint(150.0, 200.0)) // onto empty canvas: nothing
        assertEquals(1, state.connectors.size)
        assertNull(state.connectTool.draft)
        state.undo()
        assertTrue(state.connectors.isEmpty())
        state.redo()
        assertEquals(1, state.connectors.size)
    }

    @Test
    fun pressingAConnectorRemovesIt() {
        val state = twoNotes()
        connect(state, WorldPoint(5.0, 5.0), WorldPoint(305.0, 5.0))
        val (from, to) = state.connectorEnds(state.connectors.single())!!
        val (a, b) = connectorSegment(from.first, from.second, to.first, to.second)!!
        state.connectTool.begin(WorldPoint((a.x + b.x) / 2, (a.y + b.y) / 2 + 3))
        state.connectTool.end()
        assertTrue(state.connectors.isEmpty())
    }

    @Test
    fun aConnectorFollowsAMovedLabel() {
        val state = twoNotes()
        connect(state, WorldPoint(5.0, 5.0), WorldPoint(305.0, 5.0))
        val before = state.connectorEnds(state.connectors.single())!!.second.first
        state.loadTextItems(state.textItems.map { if (it.id == "b") it.copy(position = WorldPoint(300.0, 400.0)) else it })
        val after = state.connectorEnds(state.connectors.single())!!.second.first
        assertEquals(400.0, after.y - before.y, 1e-9)
    }

    @Test
    fun loadingDropsConnectorsWithAMissingLabel() {
        val state = twoNotes()
        state.loadConnectors(listOf(Connector("1", "a", "b"), Connector("2", "a", "gone")))
        assertEquals(listOf("1"), state.connectors.map { it.id })
    }

    @Test
    fun connectorsPersistTravelInBundlesAndExportToSvg() {
        val doc = CanvasStorage.createDocument(context, "Flow")
        val state = twoNotes()
        connect(state, WorldPoint(5.0, 5.0), WorldPoint(305.0, 5.0))
        CanvasStorage.saveTextItems(context, doc.id, state.textItems.toList())
        CanvasStorage.saveConnectors(context, doc.id, state.connectors.toList())
        assertEquals(state.connectors.toList(), CanvasStorage.loadConnectors(context, doc.id))
        val imported = importDocumentBundle(context, exportDocumentBundle(context, doc.id, "Flow").inputStream())!!
        assertEquals(1, CanvasStorage.loadConnectors(context, imported.id).size)
        val svg = exportDocumentSvg(state) { null }!!
        assertTrue(svg.contains("<line") && svg.contains("<polygon"))
    }

    @Test
    fun tidyLaysAChainOutInLayersAndHandlesCycles() {
        val positions = mapOf("a" to WorldPoint(500.0, 300.0), "b" to WorldPoint(0.0, 0.0), "c" to WorldPoint(90.0, 900.0), "lone" to WorldPoint(7.0, 7.0))
        val sizes = positions.mapValues { 40.0 to 20.0 }
        val laid = tidyDiagram(positions, sizes, listOf(Connector("1", "a", "b"), Connector("2", "b", "c"), Connector("3", "a", "c")), 10.0, 30.0)
        assertTrue("lone" !in laid)
        // a is the root; c is two steps down (longest path), b between.
        assertTrue(laid.getValue("a").y < laid.getValue("b").y && laid.getValue("b").y < laid.getValue("c").y)
        assertEquals(50.0, laid.getValue("b").y - laid.getValue("a").y, 1e-9) // height 20 + gap 30
        val cycle = tidyDiagram(positions, sizes, listOf(Connector("1", "a", "b"), Connector("2", "b", "a")), 10.0, 30.0)
        assertEquals(2, cycle.size) // terminates and places both
    }

    @Test
    fun tidyIsOneUndoStep() {
        val state = twoNotes()
        state.loadTextItems(state.textItems.map { if (it.id == "b") it.copy(position = WorldPoint(-400.0, -300.0)) else it })
        connect(state, WorldPoint(5.0, 5.0), WorldPoint(-395.0, -295.0))
        val before = state.textItems.toList()
        state.tidyDiagram()
        assertTrue(state.textItems.single { it.id == "b" }.position.y > state.textItems.single { it.id == "a" }.position.y)
        state.undo()
        assertEquals(before, state.textItems.toList())
    }
}
