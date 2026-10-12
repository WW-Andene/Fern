package com.andene.fern.canvas

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FrontBufferedInkTest {
    @Test
    fun onlyOpaqueNormalMarkersUseTheFrontBuffer() {
        assertTrue(isEligible(Stroke(Color.Black, 4.0)))
        assertFalse(isEligible(Stroke(Color.Black.copy(alpha = 0.5f), 4.0)))
        assertFalse(isEligible(Stroke(Color.Black, 4.0, penType = PenType.PENCIL)))
        assertFalse(isEligible(Stroke(Color.Black, 4.0, dashed = true)))
        assertFalse(isEligible(Stroke(Color.Black, 4.0, blendMode = StrokeBlendMode.MULTIPLY)))
    }

    @Test
    fun widthFactorMatchesTheCanvas() {
        assertEquals(1f, inkWidthFactor(1f, 0f), 1e-6f)
        assertEquals(0.1f, inkWidthFactor(0f, 0f), 1e-6f)
        assertEquals(1.8f, inkWidthFactor(1f, (Math.PI / 2).toFloat()), 1e-5f)
    }
}
