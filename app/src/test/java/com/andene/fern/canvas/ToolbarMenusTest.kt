package com.andene.fern.canvas

import androidx.compose.ui.test.onNodeWithContentDescription
import com.andene.fern.FernScale
import com.andene.fern.FernPanelHost
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Design roadmap D12: occasional actions sit behind labelled menus by purpose, one level deep. */
@RunWith(RobolectricTestRunner::class)
class ToolbarMenusTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun menusGroupActionsByPurpose() {
        var versions = false
        // The labelled menus live in the More panel since the toolbar strip was retired (roadmap item 339).
        compose.setContent {
            Box(Modifier.fillMaxSize()) {
                Column { Toolbar(CanvasState(), onOpenVersions = { versions = true }) }
                FernPanelHost(railOnLeft = true, railInset = FernScale.none, railSide = FernScale.none)
            }
        }
        compose.onNodeWithContentDescription("More").performClick()
        compose.onNodeWithText("Document").performScrollTo().performClick()
        compose.onNodeWithText("Version history").performClick()
        assertTrue(versions)
        compose.onNodeWithText("Insert").performScrollTo().performClick()
        compose.onNodeWithText("Insert image").assertExists()
    }
}
