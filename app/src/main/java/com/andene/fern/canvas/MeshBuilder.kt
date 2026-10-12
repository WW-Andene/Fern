package com.andene.fern.canvas

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A rigid transform (rotation and translation) in 3D, as a 3 x 3 rotation [r] (row-major) and a
 * translation [t] - what posing a jointed figure chains bone to bone (roadmap item 146).
 */
class Transform3d(val r: DoubleArray = doubleArrayOf(1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0), val t: Vec3 = Vec3(0.0, 0.0, 0.0)) {
    fun apply(p: Vec3) = Vec3(r[0] * p.x + r[1] * p.y + r[2] * p.z, r[3] * p.x + r[4] * p.y + r[5] * p.z, r[6] * p.x + r[7] * p.y + r[8] * p.z) + t
    fun rotate(v: Vec3) = Vec3(r[0] * v.x + r[1] * v.y + r[2] * v.z, r[3] * v.x + r[4] * v.y + r[5] * v.z, r[6] * v.x + r[7] * v.y + r[8] * v.z)

    /** The composition this x [child]: [child]'s frame placed within this one, as a bone hangs from its parent. */
    operator fun times(child: Transform3d): Transform3d {
        val m = DoubleArray(9)
        for (i in 0..2) for (j in 0..2) m[3 * i + j] = (0..2).sumOf { k -> r[3 * i + k] * child.r[3 * k + j] }
        return Transform3d(m, apply(child.t))
    }

    companion object {
        fun translation(x: Double, y: Double, z: Double) = Transform3d(t = Vec3(x, y, z))

        /** A turn of [radians] about the unit [axis] (right-handed). */
        fun rotation(axis: Vec3, radians: Double): Transform3d {
            val a = axis.normalized()
            val c = cos(radians)
            val s = sin(radians)
            val k = 1 - c
            return Transform3d(
                doubleArrayOf(
                    c + a.x * a.x * k, a.x * a.y * k - a.z * s, a.x * a.z * k + a.y * s,
                    a.y * a.x * k + a.z * s, c + a.y * a.y * k, a.y * a.z * k - a.x * s,
                    a.z * a.x * k - a.y * s, a.z * a.y * k + a.x * s, c + a.z * a.z * k,
                ),
            )
        }
    }
}

/**
 * Builds a mesh out of parts, each with its own square cell of the texture (a [cells] x [cells]
 * grid), so a jointed figure stays paintable as a whole (roadmap item 146).
 */
class MeshBuilder(private val cells: Int) {
    private val positions = ArrayList<Float>()
    private val normals = ArrayList<Float>()
    private val uvs = ArrayList<Float>()
    private val indices = ArrayList<Int>()

    /**
     * A capsule from the origin along +y, [length] long between its end centres, [radius]
     * thick, placed by [placement], painted in texture cell [cell].
     */
    fun capsule(radius: Double, length: Double, placement: Transform3d, cell: Int) {
        val rings = CAPSULE_RINGS
        val segments = CAPSULE_SEGMENTS
        // Rings run from the bottom pole (a hemisphere), up the side, to the top pole.
        val profile = ArrayList<Pair<Double, Double>>() // (y, ring radius)
        for (i in 0..rings) {
            val phi = PI / 2 * i / rings
            profile.add(-radius * cos(phi) to radius * sin(phi))
        }
        for (i in 0..rings) {
            val phi = PI / 2 * i / rings
            profile.add(length + radius * sin(phi) to radius * cos(phi))
        }
        val base = positions.size / 3
        for ((row, ring) in profile.withIndex()) {
            val (y, ringRadius) = ring
            for (s in 0..segments) {
                val theta = 2 * PI * s / segments
                val local = Vec3(ringRadius * cos(theta), y, ringRadius * sin(theta))
                // Normal: from the nearer end centre (hemispheres) or the axis (the side).
                val centre = Vec3(0.0, y.coerceIn(0.0, length), 0.0)
                val normal = (local - centre).normalized().let { if (it.length == 0.0) Vec3(0.0, if (y < 0) -1.0 else 1.0, 0.0) else it }
                add(placement.apply(local), placement.rotate(normal), cell, s.toDouble() / segments, row.toDouble() / (profile.size - 1))
            }
        }
        val row = segments + 1
        for (i in 0 until profile.size - 1) for (s in 0 until segments) {
            val a = base + i * row + s
            val b = a + row
            indices += listOf(a, b, a + 1, a + 1, b, b + 1)
        }
    }

    /** A box [w] x [h] x [d] centred on the origin, placed by [placement], painted in texture cell [cell] (each face a sixth of it). */
    fun box(w: Double, h: Double, d: Double, placement: Transform3d, cell: Int) {
        val faces = listOf(
            Triple(Vec3(0.0, 0.0, 1.0), Vec3(1.0, 0.0, 0.0), Vec3(0.0, 1.0, 0.0)),
            Triple(Vec3(0.0, 0.0, -1.0), Vec3(-1.0, 0.0, 0.0), Vec3(0.0, 1.0, 0.0)),
            Triple(Vec3(1.0, 0.0, 0.0), Vec3(0.0, 0.0, -1.0), Vec3(0.0, 1.0, 0.0)),
            Triple(Vec3(-1.0, 0.0, 0.0), Vec3(0.0, 0.0, 1.0), Vec3(0.0, 1.0, 0.0)),
            Triple(Vec3(0.0, 1.0, 0.0), Vec3(1.0, 0.0, 0.0), Vec3(0.0, 0.0, -1.0)),
            Triple(Vec3(0.0, -1.0, 0.0), Vec3(1.0, 0.0, 0.0), Vec3(0.0, 0.0, 1.0)),
        )
        val half = Vec3(w / 2, h / 2, d / 2)
        fun scaled(v: Vec3) = Vec3(v.x * half.x, v.y * half.y, v.z * half.z)
        for ((f, face) in faces.withIndex()) {
            val (n, u, v) = face
            val base = positions.size / 3
            for ((du, dv) in listOf(-1.0 to -1.0, 1.0 to -1.0, 1.0 to 1.0, -1.0 to 1.0)) {
                val local = scaled(n) + scaled(u) * du + scaled(v) * dv
                add(placement.apply(local), placement.rotate(n), cell, (f % 3 + (du + 1) / 2 * 0.9 + 0.05) / 3, (f / 3 + (1 - dv) / 2 * 0.9 + 0.05) / 2)
            }
            indices += listOf(base, base + 1, base + 2, base, base + 2, base + 3)
        }
    }

    private fun add(p: Vec3, n: Vec3, cell: Int, u: Double, v: Double) {
        positions += listOf(p.x.toFloat(), p.y.toFloat(), p.z.toFloat())
        normals += listOf(n.x.toFloat(), n.y.toFloat(), n.z.toFloat())
        // Inset a little inside the cell so neighbouring parts' paint doesn't bleed across.
        val cu = (cell % cells + CELL_INSET + u * (1 - 2 * CELL_INSET)) / cells
        val cv = (cell / cells + CELL_INSET + v * (1 - 2 * CELL_INSET)) / cells
        uvs += listOf(cu.toFloat(), cv.toFloat())
    }

    fun build(): Mesh {
        val n = normals.toFloatArray()
        // Normals of degenerate spots (a pole) may come out zero; give them a direction.
        for (i in 0 until n.size / 3) {
            val l = sqrt(n[3 * i] * n[3 * i] + n[3 * i + 1] * n[3 * i + 1] + n[3 * i + 2] * n[3 * i + 2])
            if (l == 0f) n[3 * i + 1] = 1f else for (k in 0..2) n[3 * i + k] /= l
        }
        return Mesh(positions.toFloatArray(), n, uvs.toFloatArray(), indices.toIntArray())
    }

    private companion object {
        const val CAPSULE_RINGS = 6
        const val CAPSULE_SEGMENTS = 16
        const val CELL_INSET = 0.04
    }
}
