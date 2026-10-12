package com.andene.fern.canvas

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

/*
 * A 3D head (roadmap item 146), as in Clip Studio Paint's head model: a sphere shaped into a
 * head - cranium, narrowing jaw and chin, brow, eye sockets, nose and ears - facing +z, turned to
 * any angle with the 3D view's camera. Its texture starts as a skin tone with the Loomis
 * construction lines drawn on (centre line, brow, eye and nose lines), the guides artists use to
 * draw a head in perspective; it paints like any model.
 */

/** The head as a mesh, about 2 units tall, centred on the origin, the face toward +z. */
fun headMesh(): Mesh {
    val rings = HEAD_RINGS
    val segments = HEAD_SEGMENTS
    val positions = ArrayList<Float>()
    val uvs = ArrayList<Float>()
    val indices = ArrayList<Int>()
    for (r in 0..rings) {
        val phi = PI * r / rings
        for (s in 0..segments) {
            val theta = 2 * PI * s / segments
            val p = shapeHead(sin(phi) * cos(theta), cos(phi), sin(phi) * sin(theta))
            positions += listOf(p.x.toFloat(), p.y.toFloat(), p.z.toFloat())
            uvs += listOf(s.toFloat() / segments, r.toFloat() / rings)
        }
    }
    val row = segments + 1
    for (r in 0 until rings) for (s in 0 until segments) {
        val a = r * row + s
        val b = a + row
        indices += listOf(a, a + 1, b, a + 1, b + 1, b)
    }
    val p = positions.toFloatArray()
    val idx = indices.toIntArray()
    return Mesh(p, Mesh.computeNormals(p, idx), uvs.toFloatArray(), idx)
}

/** Moves the unit-sphere point ([x], [y], [z]) onto the head's surface. */
private fun shapeHead(x: Double, y: Double, z: Double): Vec3 {
    var px = x * 0.72
    var py = y * 0.95
    var pz = z * 0.84
    if (y < 0) {
        // The jaw narrows toward the chin, which reaches a little lower.
        val down = (-y).pow(1.5)
        px *= 1 - 0.38 * down
        pz *= 1 - 0.2 * down * (if (z < 0) 1.6 else 1.0)
        py *= 1.08
    }
    if (z < 0) pz *= 1.12 // the back of the skull
    val front = z.coerceAtLeast(0.0)
    // Nose: a ridge down the middle of the face, strongest at its tip.
    pz += 0.17 * front * exp(-((x / 0.085).pow(2) + ((y + 0.12) / 0.17).pow(2)))
    // Eye sockets, either side of the nose.
    pz -= 0.07 * front * exp(-(((abs(x) - 0.3) / 0.13).pow(2) + ((y - 0.06) / 0.08).pow(2)))
    // Brow ridge above them.
    pz += 0.04 * front * exp(-(((y - 0.2) / 0.06).pow(2))) * (if (abs(x) < 0.5) 1.0 else 0.0)
    // Chin.
    pz += 0.05 * front * exp(-((x / 0.15).pow(2) + ((y + 0.82) / 0.1).pow(2)))
    // Ears at the sides, at eye level.
    px += sign(x) * 0.09 * exp(-((y + 0.02) / 0.16).pow(2) - (z / 0.13).pow(2))
    return Vec3(px, py, pz)
}

/** The head's starting texture (roadmap item 146): skin, with the Loomis construction lines. */
fun headMaps(size: Int): SurfaceMaps {
    val maps = SurfaceMaps.blank(size, HEAD_SKIN)
    val line = maxOf(1, size / 256)
    fun hLine(v: Double) {
        val y0 = (v * size).toInt()
        for (y in (y0 - line).coerceAtLeast(0)..(y0 + line).coerceAtMost(size - 1)) for (x in 0 until size) maps.color[y * size + x] = HEAD_GUIDE
    }
    fun vLine(u: Double) {
        val x0 = (u * size).toInt()
        for (x in (x0 - line).coerceAtLeast(0)..(x0 + line).coerceAtMost(size - 1)) for (y in 0 until size) maps.color[y * size + x] = HEAD_GUIDE
    }
    // The face looks along +z, a quarter of the way around the texture; its back at three quarters.
    vLine(0.25)
    vLine(0.75)
    // Eye line at half the head's height; the brow above, the nose base below.
    hLine(0.5)
    hLine(0.43)
    hLine(0.6)
    return maps
}

/** A new head model ready to turn and paint (roadmap item 146). */
fun newHeadModel() = Model3d(headMesh(), headMaps(MODEL_MAP_SIZE), OrbitCamera(yaw = 0.5, pitch = 0.15, distance = 3.4))

private const val HEAD_RINGS = 48
private const val HEAD_SEGMENTS = 64
private val HEAD_SKIN = 0xFFE8C4A8.toInt()
private val HEAD_GUIDE = 0xFF8D6E63.toInt()
