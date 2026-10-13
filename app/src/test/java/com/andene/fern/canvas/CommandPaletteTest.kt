package com.andene.fern.canvas

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.andene.fern.FernPanelHost
import com.andene.fern.FernScale
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Design roadmap D14: the command palette finds actions by name in either language, without accents. */
@RunWith(RobolectricTestRunner::class)
class CommandPaletteTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun matchingIgnoresCaseAccentsAndLanguage() {
        val layers = PaletteCommand("Calques", "Layers", "Barre d’outils") {}
        val select = PaletteCommand("Sélection", "Select", "Rail d’outils") {}
        val all = listOf(layers, select)
        assertEquals(listOf(select), matchCommands(all, "selection"))
        assertEquals(listOf(layers), matchCommands(all, "LAYERS"))
        assertEquals(listOf(select), matchCommands(all, "rail sel"))
        assertEquals(all, matchCommands(all, " "))
    }

    @Test
    fun theToolbarsPaletteRunsAnAction() {
        val state = CanvasState()
        CommandPaletteState.open = false
        compose.setContent {
            Box(Modifier.fillMaxSize()) {
                Column { Toolbar(state) }
                FernPanelHost(railOnLeft = true, railInset = FernScale.none, railSide = FernScale.none)
            }
        }
        compose.onNodeWithContentDescription("Find a command").performScrollTo().performClick()
        compose.onNodeWithText("Tool, action, setting, document…").performTextInput("measure")
        compose.onNodeWithText("Measure").performClick()
        assertEquals(Tool.MEASURE, state.activeTool)
    }
}
