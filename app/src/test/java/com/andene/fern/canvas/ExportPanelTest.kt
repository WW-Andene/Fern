package com.andene.fern.canvas

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.andene.fern.FernPanelHost
import com.andene.fern.FernScale
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Design roadmap D13: export by purpose, then format; the last export comes back as "Again". */
@RunWith(RobolectricTestRunner::class)
class ExportPanelTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun purposeThenFormatAndAgain() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val ran = mutableListOf<ExportFormat>()
        compose.setContent {
            Box(Modifier.fillMaxSize()) {
                ExportPanel(CanvasState(), run = { ran += it }, onDismiss = {})
                FernPanelHost(railOnLeft = true, railInset = FernScale.none, railSide = FernScale.none)
            }
        }
        compose.onNodeWithText("Vector").performClick()
        compose.onNodeWithText("Vector (SVG)").performClick()
        assertEquals(listOf(ExportFormat.SVG), ran)
        assertEquals(ExportFormat.SVG, ExportSettings.last(context))
    }

    @Test
    fun everyPurposeHasOneRecommendedFormat() {
        for (purpose in ExportPurpose.entries) assertEquals(purpose.name, 1, ExportFormat.entries.count { it.purpose == purpose && it.recommended })
    }
}
