package com.andene.fern.canvas

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Roadmap item 129: a signature drawn once, saved, and placed on any document. */
@RunWith(RobolectricTestRunner::class)
class SignatureTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun signature() = listOf(
        Stroke(Color.Blue, 3.0).apply { addPoint(WorldPoint(10.0, 10.0)); addPoint(WorldPoint(110.0, 40.0)) },
        Stroke(Color.Blue, 3.0).apply { addPoint(WorldPoint(60.0, 50.0)) },
    )

    @Test
    fun theSignatureIsSavedAndReadBack() {
        SignatureStore.save(context, signature())
        val loaded = SignatureStore.load(context)
        assertEquals(signature().map { it.points }, loaded.map { it.points })
    }

    @Test
    fun placingScalesItToAThirdOfTheViewAndSelectsIt() {
        val placed = placedSignature(signature(), 50.0)
        assertEquals(50.0, placed.maxOf { it.maxX } - placed.minOf { it.minX }, 1e-9)
        assertEquals(1.5, placed[0].widthWorld, 1e-9)

        val state = CanvasState()
        state.placeSignature(signature()) // no view size yet: placed as if 1024 px wide
        assertEquals(2, state.strokes.size)
        assertEquals(state.strokes.toList(), state.selection.toList())
        val width = state.strokes.maxOf { it.maxX } - state.strokes.minOf { it.minX }
        assertEquals(1024.0 / 3, width, 1e-6)
        assertTrue(state.strokes.all { it.color == Color.Blue })
    }
}
