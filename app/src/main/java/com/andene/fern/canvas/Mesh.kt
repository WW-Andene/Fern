package com.andene.fern.canvas

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A triangle mesh for 3D painting (roadmap item 108): per-vertex [positions] (x, y, z),
 * [normals] (x, y, z, unit length) and texture coordinates [uvs] (u, v in 0..1, v down the
 * texture), and [indices], three per triangle. Vertices on a texture seam are duplicated, as in
 * every real-time format, so each vertex has exactly one UV.
 */
class Mesh(val positions: FloatArray, val normals: FloatArray, val uvs: FloatArray, val indices: IntArray) {
    init {
        require(positions.size % 3 == 0 && normals.size == positions.size && uvs.size == positions.size / 3 * 2) { "Mismatched vertex arrays" }
        require(indices.size % 3 == 0 && indices.all { it in 0 until vertexCount }) { "Bad triangle indices" }
    }

    val vertexCount: Int get() = positions.size / 3
    val triangleCount: Int get() = indices.size / 3

    /**
     * This mesh moved and scaled to fit a sphere of radius 1 at the origin, so the camera's
     * framing suits any imported model whatever its units.
     */
    fun normalized(): Mesh {
        if (vertexCount == 0) return this
        var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE; var minZ = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE; var maxZ = -Float.MAX_VALUE
        for (i in 0 until vertexCount) {
            minX = minOf(minX, positions[3 * i]); maxX = maxOf(maxX, positions[3 * i])
            minY = minOf(minY, positions[3 * i + 1]); maxY = maxOf(maxY, positions[3 * i + 1])
            minZ = minOf(minZ, positions[3 * i + 2]); maxZ = maxOf(maxZ, positions[3 * i + 2])
        }
        val cx = (minX + maxX) / 2; val cy = (minY + maxY) / 2; val cz = (minZ + maxZ) / 2
        var radius = 0f
        for (i in 0 until vertexCount) {
            val dx = positions[3 * i] - cx; val dy = positions[3 * i + 1] - cy; val dz = positions[3 * i + 2] - cz
            radius = max(radius, sqrt(dx * dx + dy * dy + dz * dz))
        }
        if (radius == 0f) radius = 1f
        val moved = FloatArray(positions.size) { k ->
            val c = when (k % 3) { 0 -> cx; 1 -> cy; else -> cz }
            (positions[k] - c) / radius
        }
        return Mesh(moved, normals, uvs, indices)
    }

    companion object {
        /** Smooth per-vertex normals from the triangles' faces, area-weighted, for meshes that come without any. */
        fun computeNormals(positions: FloatArray, indices: IntArray): FloatArray {
            val normals = FloatArray(positions.size)
            for (t in 0 until indices.size / 3) {
                val a = indices[3 * t]; val b = indices[3 * t + 1]; val c = indices[3 * t + 2]
                val ux = positions[3 * b] - positions[3 * a]; val uy = positions[3 * b + 1] - positions[3 * a + 1]; val uz = positions[3 * b + 2] - positions[3 * a + 2]
                val vx = positions[3 * c] - positions[3 * a]; val vy = positions[3 * c + 1] - positions[3 * a + 1]; val vz = positions[3 * c + 2] - positions[3 * a + 2]
                // The cross product's length is twice the area: larger faces weigh more.
                val nx = uy * vz - uz * vy; val ny = uz * vx - ux * vz; val nz = ux * vy - uy * vx
                for (v in intArrayOf(a, b, c)) { normals[3 * v] += nx; normals[3 * v + 1] += ny; normals[3 * v + 2] += nz }
            }
            for (v in 0 until normals.size / 3) {
                val length = sqrt(normals[3 * v] * normals[3 * v] + normals[3 * v + 1] * normals[3 * v + 1] + normals[3 * v + 2] * normals[3 * v + 2])
                if (length > 0f) for (k in 0..2) normals[3 * v + k] /= length else normals[3 * v + 1] = 1f
            }
            return normals
        }
    }
}

/** Built-in shapes to paint on (roadmap item 108), each UV-mapped so its whole surface has its own texture area. */
enum class MeshPrimitive { SPHERE, CUBE, CYLINDER, TORUS, PLANE }

/** [primitive] as a mesh of about unit size, centred on the origin. */
fun primitiveMesh(primitive: MeshPrimitive): Mesh = when (primitive) {
    MeshPrimitive.SPHERE -> uvSphere(SPHERE_RINGS, SPHERE_SEGMENTS)
    MeshPrimitive.CUBE -> cube()
    MeshPrimitive.CYLINDER -> cylinder(CYLINDER_SEGMENTS)
    MeshPrimitive.TORUS -> torus(TORUS_RINGS, TORUS_SEGMENTS, 0.35f)
    MeshPrimitive.PLANE -> grid(1, 1)
}

/** A latitude-longitude sphere: [rings] bands from pole to pole, [segments] around; the texture wraps once around (equirectangular). */
private fun uvSphere(rings: Int, segments: Int): Mesh {
    val positions = ArrayList<Float>(); val uvs = ArrayList<Float>(); val indices = ArrayList<Int>()
    for (r in 0..rings) {
        val phi = PI * r / rings
        for (s in 0..segments) {
            val theta = 2 * PI * s / segments
            positions += listOf((sin(phi) * cos(theta)).toFloat(), cos(phi).toFloat(), (sin(phi) * sin(theta)).toFloat())
            uvs += listOf(s.toFloat() / segments, r.toFloat() / rings)
        }
    }
    val row = segments + 1
    for (r in 0 until rings) for (s in 0 until segments) {
        val a = r * row + s; val b = a + row
        // Wound so the outside faces outward (counter-clockwise seen from outside).
        indices += listOf(a, a + 1, b, a + 1, b + 1, b)
    }
    val p = positions.toFloatArray()
    return Mesh(p, p.copyOf(), uvs.toFloatArray(), indices.toIntArray())
}

/** A cube with each face its own square of a 3 x 2 texture layout. */
private fun cube(): Mesh {
    // Each face: its normal and two in-plane axes (u right, v down as seen from outside).
    val faces = listOf(
        floatArrayOf(0f, 0f, 1f, 1f, 0f, 0f, 0f, -1f, 0f), // front
        floatArrayOf(1f, 0f, 0f, 0f, 0f, -1f, 0f, -1f, 0f), // right
        floatArrayOf(0f, 0f, -1f, -1f, 0f, 0f, 0f, -1f, 0f), // back
        floatArrayOf(-1f, 0f, 0f, 0f, 0f, 1f, 0f, -1f, 0f), // left
        floatArrayOf(0f, 1f, 0f, 1f, 0f, 0f, 0f, 0f, 1f), // top
        floatArrayOf(0f, -1f, 0f, 1f, 0f, 0f, 0f, 0f, -1f), // bottom
    )
    val positions = ArrayList<Float>(); val normals = ArrayList<Float>(); val uvs = ArrayList<Float>(); val indices = ArrayList<Int>()
    val s = 1f / sqrt(3f) // corners on the unit sphere
    for ((f, axes) in faces.withIndex()) {
        val base = positions.size / 3
        val cellU = (f % 3) / 3f; val cellV = (f / 3) / 2f
        for ((du, dv) in listOf(-1f to -1f, 1f to -1f, 1f to 1f, -1f to 1f)) {
            for (k in 0..2) positions += s * (axes[k] + du * axes[3 + k] + dv * axes[6 + k])
            for (k in 0..2) normals += axes[k]
            // A small inset keeps neighbouring faces' paint apart at the texture's cell edges.
            uvs += listOf(cellU + (0.02f + 0.96f * (du + 1) / 2) / 3, cellV + (0.02f + 0.96f * (dv + 1) / 2) / 2)
        }
        indices += listOf(base, base + 2, base + 1, base, base + 3, base + 2)
    }
    return Mesh(positions.toFloatArray(), normals.toFloatArray(), uvs.toFloatArray(), indices.toIntArray())
}

/** A capped cylinder: the side around the top 3/4 of the texture, the two caps side by side below. */
private fun cylinder(segments: Int): Mesh {
    val positions = ArrayList<Float>(); val normals = ArrayList<Float>(); val uvs = ArrayList<Float>(); val indices = ArrayList<Int>()
    val radius = 0.6f; val half = 0.8f
    for (s in 0..segments) {
        val theta = 2 * PI * s / segments
        val x = (cos(theta) * radius).toFloat(); val z = (sin(theta) * radius).toFloat()
        for ((y, v) in listOf(half to 0f, -half to 0.75f)) {
            positions += listOf(x, y, z); normals += listOf(x / radius, 0f, z / radius); uvs += listOf(s.toFloat() / segments, v)
        }
    }
    for (s in 0 until segments) {
        val a = 2 * s
        indices += listOf(a, a + 2, a + 1, a + 2, a + 3, a + 1)
    }
    for ((y, centreU) in listOf(half to 0.25f, -half to 0.75f)) {
        val centre = positions.size / 3
        positions += listOf(0f, y, 0f); normals += listOf(0f, if (y > 0) 1f else -1f, 0f); uvs += listOf(centreU, 0.875f)
        for (s in 0..segments) {
            val theta = 2 * PI * s / segments
            positions += listOf((cos(theta) * radius).toFloat(), y, (sin(theta) * radius).toFloat())
            normals += listOf(0f, if (y > 0) 1f else -1f, 0f)
            uvs += listOf(centreU + (cos(theta) * 0.24).toFloat(), 0.875f + (sin(theta) * 0.12).toFloat())
        }
        for (s in 0 until segments) {
            if (y > 0) indices += listOf(centre, centre + 2 + s, centre + 1 + s) else indices += listOf(centre, centre + 1 + s, centre + 2 + s)
        }
    }
    return Mesh(positions.toFloatArray(), normals.toFloatArray(), uvs.toFloatArray(), indices.toIntArray())
}

/** A torus around the y axis, tube radius [tube] (the ring's radius is 1 - [tube]). */
private fun torus(rings: Int, segments: Int, tube: Float): Mesh {
    val positions = ArrayList<Float>(); val normals = ArrayList<Float>(); val uvs = ArrayList<Float>(); val indices = ArrayList<Int>()
    val ring = 1f - tube
    for (r in 0..rings) {
        val theta = 2 * PI * r / rings
        for (s in 0..segments) {
            val phi = 2 * PI * s / segments
            val nx = (cos(phi) * cos(theta)).toFloat(); val ny = sin(phi).toFloat(); val nz = (cos(phi) * sin(theta)).toFloat()
            positions += listOf((ring * cos(theta)).toFloat() + tube * nx, tube * ny, (ring * sin(theta)).toFloat() + tube * nz)
            normals += listOf(nx, ny, nz)
            uvs += listOf(r.toFloat() / rings, s.toFloat() / segments)
        }
    }
    val row = segments + 1
    for (r in 0 until rings) for (s in 0 until segments) {
        val a = r * row + s; val b = a + row
        indices += listOf(a, b, a + 1, a + 1, b, b + 1)
    }
    return Mesh(positions.toFloatArray(), normals.toFloatArray(), uvs.toFloatArray(), indices.toIntArray())
}

/** A flat square facing the viewer (+z), [columns] x [rows] quads, the whole texture across it. */
private fun grid(columns: Int, rows: Int): Mesh {
    val positions = ArrayList<Float>(); val normals = ArrayList<Float>(); val uvs = ArrayList<Float>(); val indices = ArrayList<Int>()
    for (r in 0..rows) for (c in 0..columns) {
        val u = c.toFloat() / columns; val v = r.toFloat() / rows
        positions += listOf(u * 1.4f - 0.7f, 0.7f - v * 1.4f, 0f); normals += listOf(0f, 0f, 1f); uvs += listOf(u, v)
    }
    val row = columns + 1
    for (r in 0 until rows) for (c in 0 until columns) {
        val a = r * row + c; val b = a + row
        indices += listOf(a, b, a + 1, a + 1, b, b + 1)
    }
    return Mesh(positions.toFloatArray(), normals.toFloatArray(), uvs.toFloatArray(), indices.toIntArray())
}

private const val SPHERE_RINGS = 24
private const val SPHERE_SEGMENTS = 48
private const val CYLINDER_SEGMENTS = 48
private const val TORUS_RINGS = 48
private const val TORUS_SEGMENTS = 24
