package com.andene.fern.canvas

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.andene.fern.FernPanelHost
import com.andene.fern.FernScale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** UI test: the brush editor applies edits only on Apply, and Reset restores the default. */
// A tall screen: the editor is a panel taking half of it (design roadmap D42), and performScrollTo
// on a chip only scrolls its own horizontal row, so the tip chips must be in view without scrolling.
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h1600dp")
class BrushEditorDialogTest {
    @get:Rule
    val compose = createComposeRule()

    private var applied: StampBrush? = null
    private var dismissed = false

    private fun show(initial: StampBrush) = compose.setContent {
        Box(Modifier.fillMaxSize()) {
            BrushEditorDialog(initialBrush = initial, color = Color.Black, onApply = { applied = it }, onDismiss = { dismissed = true })
            // The editor is a panel (design roadmap D42), shown only through its host.
            FernPanelHost(railOnLeft = true, railInset = FernScale.none, railSide = FernScale.none)
        }
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

    @Test
    fun presetsSaveLoadAndDelete() {
        val state = CanvasState()
        compose.setContent {
            Box(Modifier.fillMaxSize()) {
                BrushEditorDialog(
                    initialBrush = StampBrush(StampTip.STAR, spacing = 1.25f),
                    color = Color.Black,
                    onApply = { applied = it },
                    onDismiss = {},
                    presets = state.brushPresets,
                    onSavePreset = { name, brush -> state.saveBrushPreset(name, brush) },
                    onDeletePreset = { state.deleteBrushPreset(it) },
                )
                FernPanelHost(railOnLeft = true, railInset = FernScale.none, railSide = FernScale.none)
            }
        }
        compose.onNodeWithText("Save as preset").performScrollTo().performClick()
        compose.onNodeWithText("Preset name").performTextInput("Stars")
        compose.onNodeWithText("Save").performClick()
        assertEquals(listOf(BrushPreset("Stars", StampBrush(StampTip.STAR, spacing = 1.25f))), state.brushPresets.toList())

        compose.onNodeWithText("Reset to default").performScrollTo().performClick()
        // performScrollTo only scrolls the chip's own (horizontal) row, so bring the dialog's
        // column back to the top through a node that sits in it directly.
        compose.onNodeWithContentDescription("Brush preview").performScrollTo()
        compose.onNodeWithText("Stars").performClick() // loads it back into the editor
        compose.onNodeWithText("Apply").performClick()
        assertEquals(StampTip.STAR, applied?.tip)

        compose.onNodeWithContentDescription("Delete preset Stars").performClick()
        assertTrue(state.brushPresets.isEmpty())
    }
}
