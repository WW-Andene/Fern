package com.andene.fern.canvas

import org.junit.Assert.assertEquals
import org.junit.Test

class SymmetryTest {
    private val center = WorldPoint(10.0, 10.0)

    @Test
    fun copyCountPerMode() {
        assertEquals(0, symmetryTransforms(SymmetryMode.OFF, center).size)
        assertEquals(1, symmetryTransforms(SymmetryMode.VERTICAL, center).size)
        assertEquals(3, symmetryTransforms(SymmetryMode.QUADRANT, center).size)
        assertEquals(RADIAL_SEGMENTS - 1, symmetryTransforms(SymmetryMode.RADIAL, center).size)
    }

    @Test
    fun verticalMirrorsAcrossCenter() {
        val mirrored = symmetryTransforms(SymmetryMode.VERTICAL, center).single()(WorldPoint(4.0, 7.0))
        assertEquals(WorldPoint(16.0, 7.0), mirrored)
    }

    @Test
    fun radialKeepsDistanceFromCenter() {
        val p = WorldPoint(20.0, 10.0)
        for (transform in symmetryTransforms(SymmetryMode.RADIAL, center)) {
            val q = transform(p)
            assertEquals(10.0, Math.hypot(q.x - center.x, q.y - center.y), 1e-9)
        }
    }
}
