package com.andene.fern

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The app starts behind a loading cover that goes away once the document is in.
 *
 * Runs on API 29: from API 30 the motion-prediction library asks the view's context for its
 * display, which Robolectric's activity context doesn't provide (a device's does).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class MainActivityLoadingTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun loadingCoverGoesAwayOnceTheDocumentIsLoaded() {
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithDescription("Loading document").isEmpty()
        }
        compose.onNodeWithContentDescription("Loading document").assertDoesNotExist()
    }

    private fun androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>.onAllNodesWithDescription(text: String) =
        onAllNodes(androidx.compose.ui.test.hasContentDescription(text)).fetchSemanticsNodes()
}
