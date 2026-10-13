package com.andene.fern

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Design roadmap D10: panels show in the host, stack, and close back to the one beneath. */
@RunWith(RobolectricTestRunner::class)
class FernPanelTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun panelsStackAndClose() {
        var outer by mutableStateOf(true)
        var inner by mutableStateOf(false)
        compose.setContent {
            Box(Modifier.fillMaxSize()) {
                if (outer) FernPanel(onDismissRequest = { outer = false }, title = { Text("Layers") }, text = { Text("Layer list") })
                if (inner) FernPanel(onDismissRequest = { inner = false }, title = { Text("Rules") }, text = { Text("Rule list") })
                FernPanelHost(railOnLeft = true, railInset = FernScale.s64, railSide = FernScale.none)
            }
        }
        compose.onNodeWithText("Layer list").assertExists()
        inner = true
        compose.onNodeWithText("Rule list").assertExists()
        compose.onNodeWithText("Layer list").assertDoesNotExist()
        compose.onNodeWithContentDescription("Close").performClick()
        compose.onNodeWithText("Layer list").assertExists()
        compose.onNodeWithContentDescription("Collapse panel").performClick()
        compose.onNodeWithText("Layer list").assertDoesNotExist()
        compose.onNodeWithContentDescription("Close").performClick()
        compose.onNodeWithText("Layers").assertDoesNotExist()
    }
}
