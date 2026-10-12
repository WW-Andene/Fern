package com.andene.fern.canvas

/**
 * Free-form deformation (roadmap items 84 and 122): a lattice of [MESH_SIZE] x [MESH_SIZE]
 * control points over a box; moving control points bends everything inside smoothly, the
 * classic Sederberg-Parry FFD. With 4 x 4 points the lattice is a cubic Bezier patch: a point
 * at (u, v) in the box goes to the sum of control points weighted by Bernstein polynomials.
 * Control points left on the even grid reproduce the box exactly, so an untouched lattice
 * changes nothing.
 */
const val MESH_SIZE = 4

/** The undeformed lattice over the box [min]..[max], row by row (top row first). */
fun meshGrid(min: WorldPoint, max: WorldPoint): List<WorldPoint> = List(MESH_SIZE * MESH_SIZE) { k ->
    val i = k % MESH_SIZE
    val j = k / MESH_SIZE
    WorldPoint(min.x + (max.x - min.x) * i / (MESH_SIZE - 1), min.y + (max.y - min.y) * j / (MESH_SIZE - 1))
}

/** Where [point] goes when the lattice over [min]..[max] is moved to [nodes]. Points outside the box use the nearest edge's parameters extended linearly. */
fun meshWarp(point: WorldPoint, min: WorldPoint, max: WorldPoint, nodes: List<WorldPoint>): WorldPoint {
    val width = max.x - min.x
    val height = max.y - min.y
    if (width <= 0.0 || height <= 0.0) return point
    val u = (point.x - min.x) / width
    val v = (point.y - min.y) / height
    val bu = bernstein(u)
    val bv = bernstein(v)
    var x = 0.0
    var y = 0.0
    for (j in 0 until MESH_SIZE) for (i in 0 until MESH_SIZE) {
        val w = bu[i] * bv[j]
        val node = nodes[j * MESH_SIZE + i]
        x += w * node.x
        y += w * node.y
    }
    return WorldPoint(x, y)
}

/** The four cubic Bernstein polynomials at [t]. */
private fun bernstein(t: Double): DoubleArray {
    val s = 1 - t
    return doubleArrayOf(s * s * s, 3 * s * s * t, 3 * s * t * t, t * t * t)
}
