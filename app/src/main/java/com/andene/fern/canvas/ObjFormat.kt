package com.andene.fern.canvas

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Reads a Wavefront OBJ model (roadmap item 108) as a [Mesh]: vertices, texture coordinates and
 * normals, polygons split into triangle fans, negative (relative) indices, every group and object
 * merged into one mesh. Materials (MTL) aren't read: the model is painted in Fern. Texture
 * coordinates outside 0..1 (a repeating texture) are clamped to its edges. A model
 * without texture coordinates gets a spherical projection around its centre (paintable, with a
 * seam at the back); one without normals gets smooth ones. Null when the text has no faces or
 * more than [MAX_OBJ_TRIANGLES] triangles.
 */
fun readObj(text: String): Mesh? {
    val v = ArrayList<Float>(); val vt = ArrayList<Float>(); val vn = ArrayList<Float>()
    val positions = ArrayList<Float>(); val uvs = ArrayList<Float>(); val normals = ArrayList<Float>(); val indices = ArrayList<Int>()
    var hasUvs = true
    var hasNormals = true
    // One output vertex per distinct (position, uv, normal) corner.
    val corners = HashMap<Triple<Int, Int, Int>, Int>()
    fun resolve(index: String, count: Int): Int? {
        val i = index.toIntOrNull() ?: return null
        val resolved = if (i < 0) count + i else i - 1
        return resolved.takeIf { it in 0 until count }
    }
    for (raw in text.lineSequence()) {
        val line = raw.substringBefore('#').trim()
        if (line.isEmpty()) continue
        val parts = line.split(Regex("\\s+"))
        when (parts[0]) {
            "v" -> for (k in 1..3) v += parts.getOrNull(k)?.toFloatOrNull() ?: 0f
            "vt" -> { vt += parts.getOrNull(1)?.toFloatOrNull() ?: 0f; vt += parts.getOrNull(2)?.toFloatOrNull() ?: 0f }
            "vn" -> for (k in 1..3) vn += parts.getOrNull(k)?.toFloatOrNull() ?: 0f
            "f" -> {
                val face = parts.drop(1).mapNotNull { corner ->
                    val fields = corner.split('/')
                    val p = resolve(fields[0], v.size / 3) ?: return@mapNotNull null
                    val t = fields.getOrNull(1)?.takeIf { it.isNotEmpty() }?.let { resolve(it, vt.size / 2) } ?: -1
                    val n = fields.getOrNull(2)?.takeIf { it.isNotEmpty() }?.let { resolve(it, vn.size / 3) } ?: -1
                    if (t < 0) hasUvs = false
                    if (n < 0) hasNormals = false
                    corners.getOrPut(Triple(p, t, n)) {
                        positions += listOf(v[3 * p], v[3 * p + 1], v[3 * p + 2])
                        // OBJ's v runs up the texture; Fern's down.
                        uvs += if (t >= 0) listOf(vt[2 * t], 1f - vt[2 * t + 1]) else listOf(0f, 0f)
                        normals += if (n >= 0) listOf(vn[3 * n], vn[3 * n + 1], vn[3 * n + 2]) else listOf(0f, 0f, 0f)
                        positions.size / 3 - 1
                    }
                }
                for (k in 1 until face.size - 1) indices += listOf(face[0], face[k], face[k + 1])
                if (indices.size / 3 > MAX_OBJ_TRIANGLES) return null
            }
        }
    }
    if (indices.isEmpty()) return null
    val p = positions.toFloatArray()
    val idx = indices.toIntArray()
    val n = if (hasNormals) normalizedNormals(normals.toFloatArray()) else Mesh.computeNormals(p, idx)
    val t = if (hasUvs) uvs.toFloatArray().map { it.coerceIn(0f, 1f) }.toFloatArray() else sphericalUvs(p)
    return Mesh(p, n, t, idx).normalized()
}

private fun normalizedNormals(normals: FloatArray): FloatArray {
    for (i in 0 until normals.size / 3) {
        val l = sqrt(normals[3 * i] * normals[3 * i] + normals[3 * i + 1] * normals[3 * i + 1] + normals[3 * i + 2] * normals[3 * i + 2])
        if (l > 0f) for (k in 0..2) normals[3 * i + k] /= l
    }
    return normals
}

/** Texture coordinates from the direction of each vertex from the centre (longitude, latitude). */
private fun sphericalUvs(positions: FloatArray): FloatArray {
    val count = positions.size / 3
    val cx = (0 until count).map { positions[3 * it] }.average().toFloat()
    val cy = (0 until count).map { positions[3 * it + 1] }.average().toFloat()
    val cz = (0 until count).map { positions[3 * it + 2] }.average().toFloat()
    return FloatArray(count * 2) { k ->
        val i = k / 2
        val x = positions[3 * i] - cx; val y = positions[3 * i + 1] - cy; val z = positions[3 * i + 2] - cz
        if (k % 2 == 0) ((atan2(z, x) / (2 * PI)) + 0.5).toFloat()
        else (0.5 - atan2(y, sqrt(x * x + z * z)) / PI).toFloat()
    }
}

/** Larger models are refused rather than slowing the software renderer to a crawl. */
const val MAX_OBJ_TRIANGLES = 200_000
