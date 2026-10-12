package com.andene.fern.canvas

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** UI test: the brush editor applies edits only on Apply, and Reset restores the default. */
@RunWith(RobolectricTestRunner::class)
class BrushEditorDialogTest {
    @get:Rule
    val compose = createComposeRule()

    private var applied: StampBrush? = null
    private var dismissed = false

    private fun show(initial: StampBrush) = compose.setContent {
        BrushEditorDialog(initialBrush = initial, color = Color.Black, onApply = { applied = it }, onDismiss = { dismissed = true })
    }

    @Test
    fun applyReturnsTheEditedBrush() {
        show(StampBrush())
        compose.onNodeWithText("Star").performClick()
        compose.onNodeWithText("Apply").performClick()
        assertEquals(StampTip.STAR, applied?.tip)
    }

    @Test
    fun cancelAppliesNothing() {
        show(StampBrush())
        compose.onNodeWithText("Square").performClick()
        compose.onNodeWithText("Cancel").performClick()
        assertTrue(dismissed)
        assertEquals(null, applied)
    }

    @Test
    fun resetRestoresTheDefault() {
        show(StampBrush(StampTip.SOFT, spacing = 1.5f, followDirection = true))
        compose.onNodeWithText("Reset to default").performScrollTo().performClick()
        compose.onNodeWithText("Apply").performClick()
        assertEquals(StampBrush(), applied)
    }
}
