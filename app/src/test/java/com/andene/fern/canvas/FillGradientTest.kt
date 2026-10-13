package com.andene.fern.canvas

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.andene.fern.FernPanelHost
import com.andene.fern.FernScale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FillGradientTest {
    @get:Rule
    val compose = createComposeRule()

    private val rect = listOf(Offset(0f, 0f), Offset(200f, 0f), Offset(200f, 100f), Offset(0f, 100f))

    @Test
    fun endpointsSpanTheShapeInTheDirection() {
        val (s0, e0) = gradientEndpoints(rect, 0)
        assertEquals(0f, s0.x, 1e-3f); assertEquals(200f, e0.x, 1e-3f)
        val (s90, e90) = gradientEndpoints(rect, 90)
        assertEquals(0f, s90.y, 1e-3f); assertEquals(100f, e90.y, 1e-3f)
        val (s180, e180) = gradientEndpoints(rect, 180)
        assertEquals(200f, s180.x, 1e-3f); assertEquals(0f, e180.x, 1e-3f)
    }

    @Test
    fun fillToolAppliesTheActiveGradient() {
        val state = CanvasState()
        state.beginStroke(WorldPoint(0.0, 0.0))
        for (p in listOf(WorldPoint(100.0, 0.0), WorldPoint(100.0, 100.0), WorldPoint(0.0, 100.0), WorldPoint(0.0, 0.5))) state.extendStroke(p)
        state.endStroke()
        state.activeGradient = FillGradient(Color.Yellow, 45)
        state.fillAt(WorldPoint(50.0, 50.0))
        val fill = state.strokes.first { it.filled }
        assertEquals(FillGradient(Color.Yellow, 45), fill.gradient)
        // Recolouring a gradient fill keeps its gradient.
        assertEquals(fill.gradient, fill.withColor(Color.Red).gradient)
        val svg = exportDocumentSvg(state) { null }!!
        assertTrue(svg.contains("<linearGradient") && svg.contains("fill=\"url(#g0)\""))
    }

    @Test
    fun dialogAppliesTwoColoursWithDirection() {
        var result: Pair<GradientMode, FillGradient?>? = null
        compose.setContent {
            Box(Modifier.fillMaxSize()) {
                GradientFillDialog(Color.Black, GradientMode.NONE, null, onApply = { m, g -> result = m to g }, onDismiss = {})
                // The dialog is a panel (design roadmap D42), shown only through its host.
                FernPanelHost(railOnLeft = true, railInset = FernScale.none, railSide = FernScale.none)
            }
        }
        compose.onNodeWithText("Two colours").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Direction 0°").performScrollTo().performClick()
        compose.onNodeWithText("Apply").performClick()
        assertEquals(GradientMode.TWO_COLOURS, result?.first)
        assertEquals(0, result?.second?.angleDegrees)
    }

    @Test
    fun dialogFadeHasNoGradient() {
        var result: Pair<GradientMode, FillGradient?>? = null
        compose.setContent {
            Box(Modifier.fillMaxSize()) {
                GradientFillDialog(Color.Black, GradientMode.TWO_COLOURS, FillGradient(Color.Red, 90), onApply = { m, g -> result = m to g }, onDismiss = {})
                // The dialog is a panel (design roadmap D42), shown only through its host.
                FernPanelHost(railOnLeft = true, railInset = FernScale.none, railSide = FernScale.none)
            }
        }
        compose.onNodeWithText("Fade out").performScrollTo().performClick()
        compose.onNodeWithText("Apply").performClick()
        assertEquals(GradientMode.FADE to null, result)
    }
}
