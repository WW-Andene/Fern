package com.andene.fern.canvas

import kotlin.math.hypot

/**
 * Puppet warp (roadmap item 122): pins stuck into the picture at [origins] and dragged to
 * [pins]; everything else follows as rigidly as it can - the moving-least-squares rigid
 * deformation of Schaefer, McPhail and Warren, "Image Deformation Using Moving Least Squares"
 * (SIGGRAPH 2006), with weights 1 / distance². Each point gets its own best rotation and
 * translation of the pins, weighted toward the pins nearest it, so a limb bends around a pin
 * instead of stretching. No pins changes nothing; one pin moves everything with it.
 */
fun puppetWarp(point: WorldPoint, origins: List<WorldPoint>, pins: List<WorldPoint>): WorldPoint {
    if (origins.isEmpty()) return point
    var weightSum = 0.0
    var px = 0.0; var py = 0.0; var qx = 0.0; var qy = 0.0
    val weights = DoubleArray(origins.size)
    for (i in origins.indices) {
        val dx = origins[i].x - point.x
        val dy = origins[i].y - point.y
        val d2 = dx * dx + dy * dy
        if (d2 < 1e-18) return pins[i] // on a pin: exactly where the pin is
        val w = 1.0 / d2
        weights[i] = w
        weightSum += w
        px += w * origins[i].x; py += w * origins[i].y
        qx += w * pins[i].x; qy += w * pins[i].y
    }
    px /= weightSum; py /= weightSum; qx /= weightSum; qy /= weightSum
    // As complex numbers: c = sum of w * q̂ * conj(p̂); its angle is the best rotation here.
    var cRe = 0.0
    var cIm = 0.0
    for (i in origins.indices) {
        val hx = origins[i].x - px; val hy = origins[i].y - py
        val kx = pins[i].x - qx; val ky = pins[i].y - qy
        cRe += weights[i] * (kx * hx + ky * hy)
        cIm += weights[i] * (ky * hx - kx * hy)
    }
    val vx = point.x - px
    val vy = point.y - py
    val c = hypot(cRe, cIm)
    if (c < 1e-18) return WorldPoint(qx + vx, qy + vy) // one pin (or all together): a plain move
    val cos = cRe / c
    val sin = cIm / c
    return WorldPoint(qx + vx * cos - vy * sin, qy + vx * sin + vy * cos)
}
