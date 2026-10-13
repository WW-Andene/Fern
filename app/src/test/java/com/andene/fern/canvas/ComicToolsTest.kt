package com.andene.fern.canvas

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs
import kotlin.math.hypot

/** Roadmap item 105: balloons, page layouts, the panel cutter, speed and focus lines. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ComicToolsTest {
    @Test
    fun thoughtBalloonsAreACloudWithATrailAsOneGroup() {
        val cloud = thoughtCloudPoints(WorldPoint(0.0, 0.0), WorldPoint(200.0, 100.0))
        assertTrue(hypot(cloud.first().x - cloud.last().x, cloud.first().y - cloud.last().y) < 1e-6) // closed
        // Scallops reach beyond the ellipse.
        assertTrue(cloud.maxOf { it.x } > 200.0)
        assertEquals(3, thoughtTrail(WorldPoint(0.0, 0.0), WorldPoint(200.0, 100.0)).size)
        val state = CanvasState()
        state.activeTool = Tool.SHAPE
        state.activeShapeKind = ShapeKind.THOUGHT_BUBBLE
        state.shapeTool.begin(WorldPoint(0.0, 0.0))
        state.shapeTool.move(WorldPoint(200.0, 100.0))
        state.shapeTool.end()
        assertEquals(4, state.strokes.size)
        assertEquals(1, state.strokes.map { it.groupId }.distinct().size)
        assertTrue(state.strokes.first().groupId != null)
    }

    @Test
    fun shoutBalloonsAreSpiky() {
        val burst = shoutBurstPoints(WorldPoint(0.0, 0.0), WorldPoint(100.0, 100.0))
        val radii = burst.map { hypot(it.x - 50, it.y - 50) }
        assertEquals(50.0, radii.max(), 1e-6)
        assertEquals(39.0, radii.min(), 1e-6) // the inner corners
    }

    @Test
    fun layoutsFillThePageInsideTheMarginWithGutters() {
        val panels = comicPanels(ComicLayout.GRID_SIX, 0.0, 0.0, 200.0, 300.0, margin = 10.0, gutter = 4.0)
        assertEquals(6, panels.size)
        assertEquals(10.0, panels.first()[0], 1e-9)
        assertEquals(190.0, panels[1][2], 1e-9)
        assertEquals(290.0, panels.last()[3], 1e-9)
        assertEquals(4.0, panels[1][0] - panels[0][2], 1e-9)
        assertEquals(4, comicPanels(ComicLayout.FOUR_KOMA, 0.0, 0.0, 100.0, 400.0, 0.0, 0.0).size)
    }

    @Test
    fun insertingAPageMakesItsPanelsAndAFrame() {
        val state = CanvasState()
        state.viewportSize = Size(1000f, 1000f)
        state.insertComicPage(ComicPageSize.SQUARE, ComicLayout.GRID_NINE)
        assertEquals(9, state.strokes.size)
        val page = state.frames.single()
        assertEquals(page.max.x - page.min.x, page.max.y - page.min.y, 1e-6)
    }

    @Test
    fun thePanelCutterDividesCrossedPanelsWithAGutter() {
        val state = CanvasState()
        val panel = Stroke(Color.Black, 3.0).apply {
            for (p in listOf(WorldPoint(0.0, 0.0), WorldPoint(200.0, 0.0), WorldPoint(200.0, 100.0), WorldPoint(0.0, 100.0), WorldPoint(0.0, 0.0))) addPoint(p)
        }
        state.loadStrokes(listOf(panel))
        state.activeTool = Tool.SELECT
        state.toggleShapeEdit(ShapeEditMode.PANEL_CUT) // no selection needed
        assertEquals(ShapeEditMode.PANEL_CUT, state.shapeEditMode)
        state.beginSelectGesture(WorldPoint(100.0, -20.0))
        state.continueSelectGesture(WorldPoint(100.0, 120.0))
        state.endSelectGesture()
        assertEquals(2, state.strokes.size)
        val (left, right) = state.strokes.sortedBy { it.minX }
        assertEquals(16.0, right.minX - left.maxX, 1e-3) // the gutter, at zoom 1
        // Outlined panels stay closed outlines.
        assertTrue(state.strokes.all { !it.filled && it.points.first() == it.points.last() })
        state.undo()
        assertEquals(1, state.strokes.size)
    }

    @Test
    fun focusLinesConvergeAndSpeedLinesRunParallel() {
        val min = WorldPoint(0.0, 0.0)
        val max = WorldPoint(200.0, 100.0)
        val focus = comicLines(ComicLineKind.FOCUS, min, max, 40, 0.5, 0.0, seed = 1)
        assertEquals(40, focus.size)
        for ((outer, innerEnd) in focus) {
            // The outer end is on the box's edge, the inner end nearer the centre.
            assertTrue(abs(outer.x) < 1e-6 || abs(outer.x - 200) < 1e-6 || abs(outer.y) < 1e-6 || abs(outer.y - 100) < 1e-6)
            assertTrue(hypot(innerEnd.x - 100, innerEnd.y - 50) < hypot(outer.x - 100, outer.y - 50))
        }
        val speed = comicLines(ComicLineKind.SPEED, min, max, 30, 0.3, 0.0, seed = 2)
        assertTrue(speed.isNotEmpty())
        for ((a, b) in speed) assertEquals(a.y, b.y, 1e-9) // horizontal at 0°
        val state = CanvasState()
        state.viewportSize = Size(400f, 300f)
        state.addComicLines(ComicLineKind.FOCUS, 24, 0.4, 0.0, seed = 3)
        assertEquals(24, state.strokes.size)
        assertEquals(WidthProfile.TAPER_END, state.strokes.first().lineStyle?.profile)
        assertEquals(24, state.selection.size)
    }
}
