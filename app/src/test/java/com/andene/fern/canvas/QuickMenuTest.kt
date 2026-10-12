package com.andene.fern.canvas

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.unit.DpOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** UI test: the quick menu opens from its button, runs the tapped action and closes. */
@RunWith(RobolectricTestRunner::class)
class QuickMenuTest {
    @get:Rule
    val compose = createComposeRule()

    private val state = CanvasState()
    private var open by mutableStateOf(false)

    private fun show() = compose.setContent {
        QuickMenuButton(onOpen = { open = true })
        if (open) QuickMenuOverlay(state = state, onDismiss = { open = false })
    }

    @Test
    fun tappingAToolSelectsItAndCloses() {
        show()
        compose.onNodeWithContentDescription("Quick menu").performClick()
        compose.onNodeWithContentDescription("Eraser").performClick()
        assertEquals(Tool.ERASER, state.activeTool)
        compose.onNodeWithContentDescription("Eraser").assertDoesNotExist()
    }

    @Test
    fun undoRunsFromTheRing() {
        state.beginStroke(WorldPoint(0.0, 0.0)); state.extendStroke(WorldPoint(50.0, 20.0)); state.endStroke()
        show()
        compose.onNodeWithContentDescription("Quick menu").performClick()
        compose.onNodeWithContentDescription("Undo").performClick()
        assertEquals(0, state.strokes.size)
    }

    @Test
    fun closeButtonDismissesWithoutChangingTheTool() {
        show()
        compose.onNodeWithContentDescription("Quick menu").performClick()
        compose.onNodeWithContentDescription("Close quick menu").performClick()
        assertEquals(Tool.PEN, state.activeTool)
        compose.onNodeWithContentDescription("Close quick menu").assertDoesNotExist()
    }

    @Test
    fun actionsSitOnARingAroundTheCloseButtonWithoutOverlapping() {
        compose.setContent { QuickMenuOverlay(state = state, onDismiss = {}) }
        fun center(label: String) = compose.onNodeWithContentDescription(label).getUnclippedBoundsInRoot()
            .let { DpOffset((it.left + it.right) / 2, (it.top + it.bottom) / 2) }
        val middle = center("Close quick menu")
        val labels = listOf("Pen", "Eraser", "Select", "Shapes", "Redo", "Undo", "Text", "Fill")
        val centers = labels.map(::center)
        for (c in centers) {
            val distance = kotlin.math.hypot((c.x - middle.x).value, (c.y - middle.y).value)
            assertEquals(96f, distance, 0.5f)
        }
        // Neighbours are further apart than an item is wide (48 dp), so items don't overlap.
        for (i in centers.indices) {
            val a = centers[i]
            val b = centers[(i + 1) % centers.size]
            assertTrue(kotlin.math.hypot((a.x - b.x).value, (a.y - b.y).value) > 48f)
        }
        // First action straight above the centre.
        assertEquals(middle.x.value, centers[0].x.value, 0.5f)
    }

    @Test
    fun aCustomRingShowsTheChosenActions() {
        // Roadmap item 133: the user picks what the ring holds.
        compose.setContent { QuickMenuOverlay(state = state, onDismiss = {}, actions = listOf(QuickMenuAction.EYEDROPPER, QuickMenuAction.UNDO)) }
        compose.onNodeWithContentDescription("Eyedropper").performClick()
        assertEquals(Tool.EYEDROPPER, state.activeTool)
        compose.onNodeWithContentDescription("Eraser").assertDoesNotExist()
    }

    @Test
    fun toggledActionsKeepOrderAndLimits() {
        val one = listOf(QuickMenuAction.UNDO)
        assertEquals(one, toggledQuickMenu(one, QuickMenuAction.UNDO)) // the last can't go
        assertEquals(listOf(QuickMenuAction.PEN, QuickMenuAction.UNDO), toggledQuickMenu(one, QuickMenuAction.PEN))
        val full = QuickMenuAction.entries.take(QUICK_MENU_MAX_ACTIONS)
        assertEquals(full, toggledQuickMenu(full, QuickMenuAction.entries.last()))
    }
}
