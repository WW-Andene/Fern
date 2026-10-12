package com.andene.fern.canvas

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** UI test: the settings switches reflect and change their values, and Done dismisses. */
@RunWith(RobolectricTestRunner::class)
class SettingsDialogTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun togglesAndDismisses() {
        var leftHanded by mutableStateOf(false)
        var dismissed = false
        compose.setContent {
            SettingsDialog(
                leftHanded = leftHanded,
                onLeftHandedChange = { leftHanded = it },
                toolbarAtBottom = false,
                onToolbarAtBottomChange = {},
                snapToObjects = true,
                onSnapToObjectsChange = {},
                onShowGuide = {},
                onDismiss = { dismissed = true },
            )
        }
        val leftHandedSwitch = compose.onAllNodes(isToggleable())[0]
        leftHandedSwitch.assertIsOff()
        leftHandedSwitch.performClick()
        leftHandedSwitch.assertIsOn()
        assertTrue(leftHanded)
        compose.onNodeWithText("Done").performClick()
        assertTrue(dismissed)
    }
}
