package com.andene.fern.canvas

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Symmetry drawing: every pen stroke is repeated live through these reflections/rotations
 * about [CanvasState.symmetryCenter], like the symmetry guides in Procreate or Krita.
 */
enum class SymmetryMode(val label: String) {
    OFF("Off"),
    VERTICAL("Mirror left/right"),
    HORIZONTAL("Mirror top/bottom"),
    QUADRANT("Four-way mirror"),
    RADIAL("Radial (6)"),
}

// Six segments: the common default for mandala-style radial symmetry.
const val RADIAL_SEGMENTS = 6

/** The extra copies' point transforms for [mode] about [center] (the original stroke isn't included). */
fun symmetryTransforms(mode: SymmetryMode, center: WorldPoint): List<(WorldPoint) -> WorldPoint> {
    val mirrorX = { p: WorldPoint -> WorldPoint(2 * center.x - p.x, p.y) }
    val mirrorY = { p: WorldPoint -> WorldPoint(p.x, 2 * center.y - p.y) }
    return when (mode) {
        SymmetryMode.OFF -> emptyList()
        SymmetryMode.VERTICAL -> listOf(mirrorX)
        SymmetryMode.HORIZONTAL -> listOf(mirrorY)
        SymmetryMode.QUADRANT -> listOf(mirrorX, mirrorY, { p -> mirrorY(mirrorX(p)) })
        SymmetryMode.RADIAL -> (1 until RADIAL_SEGMENTS).map { k ->
            val angle = 2 * PI * k / RADIAL_SEGMENTS
            val c = cos(angle)
            val s = sin(angle)
            ({ p: WorldPoint ->
                val dx = p.x - center.x
                val dy = p.y - center.y
                WorldPoint(center.x + dx * c - dy * s, center.y + dx * s + dy * c)
            })
        }
    }
}
