package com.andene.fern.canvas

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
        compose.setContent { Column { Toolbar(CanvasState(), onOpenVersions = { versions = true }) } }
        compose.onNodeWithText("Document").performScrollTo().performClick()
        compose.onNodeWithText("Version history").performClick()
        assertTrue(versions)
        compose.onNodeWithText("Insert").performScrollTo().performClick()
        compose.onNodeWithText("Insert image").assertExists()
    }
}
