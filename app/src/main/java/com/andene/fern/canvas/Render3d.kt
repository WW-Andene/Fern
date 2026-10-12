package com.andene.fern.canvas

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/*
 * A small software renderer for 3D painting (roadmap items 108 and 147): a z-buffered,
 * perspective-correct triangle rasterizer with Blinn-Phong lighting driven by painted colour,
 * roughness and metallic maps, and ray picking for the brush. Software rather than OpenGL so it
 * renders identically everywhere, tests included; models are kept small enough
 * ([MAX_OBJ_TRIANGLES]) for it to stay interactive.
 */

/**
 * An orbit camera around the origin: [yaw] (radians, around the vertical axis), [pitch]
 * (radians, up from the horizon, kept short of the poles), [distance] from the centre, and a
 * vertical field of view of [fovDegrees].
 */
data class OrbitCamera(val yaw: Double = 0.6, val pitch: Double = 0.35, val distance: Double = 3.2, val fovDegrees: Double = 40.0) {
    val eye: Vec3
        get() = Vec3(distance * cos(pitch) * sin(yaw), distance * sin(pitch), distance * cos(pitch) * cos(yaw))

    /** Turned by [dYaw] and [dPitch] radians, the pitch kept within ±85°. */
    fun orbit(dYaw: Double, dPitch: Double) = copy(yaw = yaw + dYaw, pitch = (pitch + dPitch).coerceIn(-MAX_PITCH, MAX_PITCH))

    /** Moved [factor] times closer (more than 1 zooms in), within reason. */
    fun zoom(factor: Double) = copy(distance = (distance / factor).coerceIn(MIN_DISTANCE, MAX_DISTANCE))

    /** The camera's right, up and backward axes (an orthonormal basis looking at the origin). */
    internal fun basis(): Triple<Vec3, Vec3, Vec3> {
        val back = eye.normalized()
        val right = Vec3(0.0, 1.0, 0.0).cross(back).normalized()
        val up = back.cross(right)
        return Triple(right, up, back)
    }

    private companion object {
        const val MAX_PITCH = 85.0 * Math.PI / 180
        const val MIN_DISTANCE = 1.3
        const val MAX_DISTANCE = 12.0
    }
}

/** A light from the direction [yaw], [pitch] (as for the camera), at [intensity], over [ambient] light from everywhere. */
data class Light3d(val yaw: Double = -0.7, val pitch: Double = 0.8, val intensity: Double = 0.9, val ambient: Double = 0.3) {
    val direction: Vec3 get() = Vec3(cos(pitch) * sin(yaw), sin(pitch), cos(pitch) * cos(yaw)).normalized()
}

/** A 3D vector. */
data class Vec3(val x: Double, val y: Double, val z: Double) {
    operator fun plus(o: Vec3) = Vec3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: Vec3) = Vec3(x - o.x, y - o.y, z - o.z)
    operator fun times(s: Double) = Vec3(x * s, y * s, z * s)
    infix fun dot(o: Vec3) = x * o.x + y * o.y + z * o.z
    fun cross(o: Vec3) = Vec3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x)
    val length: Double get() = sqrt(x * x + y * y + z * z)
    fun normalized(): Vec3 = length.let { if (it == 0.0) this else Vec3(x / it, y / it, z / it) }
}

/**
 * A model's painted surface (roadmap items 108 and 147): square maps [size] texels across, by
 * texel row: [color] (ARGB, its alpha unused), [roughness] (0 = mirror-smooth, 255 = fully
 * matte) and [metallic] (0 = dielectric, 255 = metal).
 */
class SurfaceMaps(val size: Int, val color: IntArray, val roughness: ByteArray, val metallic: ByteArray) {
    init {
        require(size > 0 && color.size == size * size && roughness.size == size * size && metallic.size == size * size) { "Map sizes differ" }
    }

    fun copy() = SurfaceMaps(size, color.copyOf(), roughness.copyOf(), metallic.copyOf())

    companion object {
        /** Blank maps: [base] colour, fairly matte, not metal. */
        fun blank(size: Int, base: Int) = SurfaceMaps(size, IntArray(size * size) { base }, ByteArray(size * size) { DEFAULT_ROUGHNESS.toByte() }, ByteArray(size * size))

        const val DEFAULT_ROUGHNESS = 180
    }
}

/** The result of [renderMesh]: ARGB pixels, transparent where nothing was drawn. */
class Rendered3d(val width: Int, val height: Int, val pixels: IntArray)

/**
 * Renders [mesh] painted with [maps] from [camera] under [light] into [width] x [height]
 * pixels. Back faces are drawn too (their normals turned to the viewer), so open meshes show
 * their inside; the z-buffer sorts everything.
 */
fun renderMesh(mesh: Mesh, maps: SurfaceMaps, camera: OrbitCamera, light: Light3d, width: Int, height: Int): Rendered3d {
    val pixels = IntArray(width * height)
    val depth = FloatArray(width * height) { Float.POSITIVE_INFINITY }
    val (right, up, back) = camera.basis()
    val eye = camera.eye
    val focal = (height / 2.0) / tan(Math.toRadians(camera.fovDegrees) / 2)
    val count = mesh.vertexCount
    // Per vertex: screen x, y, view depth (distance along the view direction), and 1 / depth.
    val sx = DoubleArray(count); val sy = DoubleArray(count); val sz = DoubleArray(count)
    for (i in 0 until count) {
        val p = Vec3(mesh.positions[3 * i].toDouble(), mesh.positions[3 * i + 1].toDouble(), mesh.positions[3 * i + 2].toDouble()) - eye
        val z = -(p dot back)
        sz[i] = z
        sx[i] = width / 2.0 + (p dot right) * focal / z
        sy[i] = height / 2.0 - (p dot up) * focal / z
    }
    val lightDir = light.direction
    for (t in 0 until mesh.triangleCount) {
        val a = mesh.indices[3 * t]; val b = mesh.indices[3 * t + 1]; val c = mesh.indices[3 * t + 2]
        if (sz[a] < NEAR || sz[b] < NEAR || sz[c] < NEAR) continue // behind or too close: not clipped, skipped
        val minX = max(0, floor(min(sx[a], min(sx[b], sx[c]))).toInt())
        val maxX = min(width - 1, floor(max(sx[a], max(sx[b], sx[c]))).toInt())
        val minY = max(0, floor(min(sy[a], min(sy[b], sy[c]))).toInt())
        val maxY = min(height - 1, floor(max(sy[a], max(sy[b], sy[c]))).toInt())
        if (minX > maxX || minY > maxY) continue
        val area = (sx[b] - sx[a]) * (sy[c] - sy[a]) - (sx[c] - sx[a]) * (sy[b] - sy[a])
        if (abs(area) < 1e-12) continue
        val ia = 1 / sz[a]; val ib = 1 / sz[b]; val ic = 1 / sz[c]
        for (y in minY..maxY) {
            val py = y + 0.5
            for (x in minX..maxX) {
                val px = x + 0.5
                // Barycentric weights in screen space (positive inside, whichever the winding).
                var wa = ((sx[b] - px) * (sy[c] - py) - (sx[c] - px) * (sy[b] - py)) / area
                var wb = ((sx[c] - px) * (sy[a] - py) - (sx[a] - px) * (sy[c] - py)) / area
                var wc = 1 - wa - wb
                if (wa < 0 || wb < 0 || wc < 0) continue
                // Perspective-correct: interpolate attributes over 1/z.
                val inv = wa * ia + wb * ib + wc * ic
                val z = 1 / inv
                val k = y * width + x
                if (z >= depth[k]) continue
                depth[k] = z.toFloat()
                wa = wa * ia / inv; wb = wb * ib / inv; wc = wc * ic / inv
                val u = wa * mesh.uvs[2 * a] + wb * mesh.uvs[2 * b] + wc * mesh.uvs[2 * c]
                val v = wa * mesh.uvs[2 * a + 1] + wb * mesh.uvs[2 * b + 1] + wc * mesh.uvs[2 * c + 1]
                var normal = Vec3(
                    wa * mesh.normals[3 * a] + wb * mesh.normals[3 * b] + wc * mesh.normals[3 * c],
                    wa * mesh.normals[3 * a + 1] + wb * mesh.normals[3 * b + 1] + wc * mesh.normals[3 * c + 1],
                    wa * mesh.normals[3 * a + 2] + wb * mesh.normals[3 * b + 2] + wc * mesh.normals[3 * c + 2],
                ).normalized()
                val world = Vec3(
                    wa * mesh.positions[3 * a] + wb * mesh.positions[3 * b] + wc * mesh.positions[3 * c],
                    wa * mesh.positions[3 * a + 1] + wb * mesh.positions[3 * b + 1] + wc * mesh.positions[3 * c + 1],
                    wa * mesh.positions[3 * a + 2] + wb * mesh.positions[3 * b + 2] + wc * mesh.positions[3 * c + 2],
                )
                val toEye = (eye - world).normalized()
                if ((normal dot toEye) < 0) normal = normal * -1.0
                pixels[k] = shade(maps, u, v, normal, toEye, lightDir, light)
            }
        }
    }
    return Rendered3d(width, height, pixels)
}

/** Blinn-Phong shading of the surface at texture point [u], [v] (see the file comment). */
private fun shade(maps: SurfaceMaps, u: Double, v: Double, normal: Vec3, toEye: Vec3, lightDir: Vec3, light: Light3d): Int {
    val texel = texelIndex(maps.size, u, v)
    val albedo = maps.color[texel]
    val roughness = (maps.roughness[texel].toInt() and 0xFF) / 255.0
    val metal = (maps.metallic[texel].toInt() and 0xFF) / 255.0
    val diffuse = max(0.0, normal dot lightDir) * light.intensity
    val half = (lightDir + toEye).normalized()
    // Smoother surfaces have a tighter, brighter highlight.
    val shininess = 2.0 / max(0.0004, roughness.pow(4)) - 2.0
    val specular = if (diffuse > 0) max(0.0, normal dot half).pow(max(1.0, shininess)) * light.intensity * (1 - roughness * 0.9) else 0.0
    fun channel(shift: Int): Int {
        val base = ((albedo shr shift) and 0xFF) / 255.0
        // Metals have no diffuse colour and tint their highlight; dielectrics reflect ~4 % white.
        val lit = base * (light.ambient + (1 - metal) * diffuse) + specular * (0.04 * (1 - metal) + base * metal) * 4
        return (lit.coerceIn(0.0, 1.0) * 255 + 0.5).toInt()
    }
    return (0xFF shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
}

/** The texel of a [size] x [size] map at texture point [u], [v] (clamped to the map). */
internal fun texelIndex(size: Int, u: Double, v: Double): Int {
    val x = (u * size).toInt().coerceIn(0, size - 1)
    val y = (v * size).toInt().coerceIn(0, size - 1)
    return y * size + x
}

/** Where a ray hit a mesh: the triangle, the texture point, and the distance along the ray. */
class MeshHit(val triangle: Int, val u: Double, val v: Double, val distance: Double)

/**
 * The first triangle of [mesh] under screen point ([x], [y]) of a [width] x [height] view from
 * [camera] (Möller-Trumbore ray casting), or null where the ray misses the model.
 */
fun pickMesh(mesh: Mesh, camera: OrbitCamera, x: Double, y: Double, width: Int, height: Int): MeshHit? {
    val (right, up, back) = camera.basis()
    val focal = (height / 2.0) / tan(Math.toRadians(camera.fovDegrees) / 2)
    val origin = camera.eye
    val dir = (right * ((x - width / 2.0) / focal) + up * ((height / 2.0 - y) / focal) - back).normalized()
    var best: MeshHit? = null
    for (t in 0 until mesh.triangleCount) {
        val ia = mesh.indices[3 * t]; val ib = mesh.indices[3 * t + 1]; val ic = mesh.indices[3 * t + 2]
        val p0 = vertex(mesh, ia); val p1 = vertex(mesh, ib); val p2 = vertex(mesh, ic)
        val e1 = p1 - p0; val e2 = p2 - p0
        val h = dir.cross(e2)
        val det = e1 dot h
        if (abs(det) < 1e-12) continue
        val s = origin - p0
        val bu = (s dot h) / det
        if (bu < 0 || bu > 1) continue
        val q = s.cross(e1)
        val bv = (dir dot q) / det
        if (bv < 0 || bu + bv > 1) continue
        val distance = (e2 dot q) / det
        if (distance <= 1e-9 || (best != null && distance >= best.distance)) continue
        val bw = 1 - bu - bv
        val u = bw * mesh.uvs[2 * ia] + bu * mesh.uvs[2 * ib] + bv * mesh.uvs[2 * ic]
        val v = bw * mesh.uvs[2 * ia + 1] + bu * mesh.uvs[2 * ib + 1] + bv * mesh.uvs[2 * ic + 1]
        best = MeshHit(t, u, v, distance)
    }
    return best
}

private fun vertex(mesh: Mesh, i: Int) = Vec3(mesh.positions[3 * i].toDouble(), mesh.positions[3 * i + 1].toDouble(), mesh.positions[3 * i + 2].toDouble())

/**
 * How many texels one screen pixel covers on [triangle] of [mesh] seen from [camera] in a view
 * [height] pixels tall, for maps [size] texels across: the square root of the triangle's texture
 * area over its screen area. Lets a brush keep its on-screen size whatever the texture density.
 */
fun texelsPerPixel(mesh: Mesh, triangle: Int, camera: OrbitCamera, width: Int, height: Int, size: Int): Double {
    val (right, up, back) = camera.basis()
    val focal = (height / 2.0) / tan(Math.toRadians(camera.fovDegrees) / 2)
    val idx = IntArray(3) { mesh.indices[3 * triangle + it] }
    val screen = idx.map { i ->
        val p = vertex(mesh, i) - camera.eye
        val z = -(p dot back)
        ((p dot right) * focal / z) to ((p dot up) * focal / z)
    }
    val screenArea = abs((screen[1].first - screen[0].first) * (screen[2].second - screen[0].second) - (screen[2].first - screen[0].first) * (screen[1].second - screen[0].second)) / 2
    val uv = idx.map { i -> mesh.uvs[2 * i] * size.toDouble() to mesh.uvs[2 * i + 1] * size.toDouble() }
    val texelArea = abs((uv[1].first - uv[0].first) * (uv[2].second - uv[0].second) - (uv[2].first - uv[0].first) * (uv[1].second - uv[0].second)) / 2
    return if (screenArea < 1e-9) 1.0 else sqrt(texelArea / screenArea)
}

/** What a 3D brush paints into (roadmap items 108 and 147). */
enum class SurfaceChannel { COLOR, ROUGHNESS, METALLIC }

/**
 * Paints a round dab into [maps] at texture point [u], [v], [radius] texels across, its edge
 * softened by [softness] (0 hard - 1 soft): [channel] COLOR blends toward [color] (ARGB, its
 * alpha the dab's opacity), ROUGHNESS and METALLIC toward [value] (0-255). The dab stays in
 * texture space, so it doesn't continue across a UV seam.
 */
fun paintDab(maps: SurfaceMaps, u: Double, v: Double, radius: Double, softness: Double, channel: SurfaceChannel, color: Int, value: Int) {
    val size = maps.size
    val cx = u * size
    val cy = v * size
    val r = max(0.5, radius)
    val opacity = ((color ushr 24) and 0xFF) / 255.0
    val inner = r * (1 - softness.coerceIn(0.0, 1.0))
    for (y in max(0, (cy - r).toInt())..min(size - 1, (cy + r).toInt())) {
        for (x in max(0, (cx - r).toInt())..min(size - 1, (cx + r).toInt())) {
            val d = sqrt((x + 0.5 - cx) * (x + 0.5 - cx) + (y + 0.5 - cy) * (y + 0.5 - cy))
            if (d > r) continue
            val falloff = if (d <= inner) 1.0 else 1 - (d - inner) / (r - inner).coerceAtLeast(1e-9)
            val k = y * size + x
            when (channel) {
                SurfaceChannel.COLOR -> {
                    val w = falloff * opacity
                    val old = maps.color[k]
                    fun mix(shift: Int) = (((old shr shift) and 0xFF) * (1 - w) + ((color shr shift) and 0xFF) * w + 0.5).toInt().coerceIn(0, 255)
                    maps.color[k] = (0xFF shl 24) or (mix(16) shl 16) or (mix(8) shl 8) or mix(0)
                }
                SurfaceChannel.ROUGHNESS -> maps.roughness[k] = blendByte(maps.roughness[k], value, falloff * opacity)
                SurfaceChannel.METALLIC -> maps.metallic[k] = blendByte(maps.metallic[k], value, falloff * opacity)
            }
        }
    }
}

private fun blendByte(old: Byte, target: Int, w: Double): Byte = ((old.toInt() and 0xFF) * (1 - w) + target * w + 0.5).toInt().coerceIn(0, 255).toByte()

// Triangles closer to the camera than this (view units) are skipped rather than clipped.
private const val NEAR = 0.05
