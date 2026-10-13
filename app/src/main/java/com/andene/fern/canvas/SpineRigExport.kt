package com.andene.fern.canvas

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.unit.Density
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * The Spine-model rig (roadmap items 250, 305) as a Spine project: a zip of `skeleton.json`
 * (Spine JSON 4.1) and `images/<attachment>.png`, read by Spine's "Import Data".
 *
 * - Bones keep their setup transforms (position, rotation, length, scale, shear, inherit) under
 *   an added [SPINE_ROOT]. Spine's y axis points up, so every y and angle is mirrored.
 * - Each attachment is rendered to an image and exported as a weighted **mesh**: a grid over the
 *   image whose vertices follow the bones by the same automatic weights as the canvas, so the
 *   drawing bends in Spine as it does in Fern instead of moving rigidly.
 * - Slots keep their draw order. Each animation keeps its keys per property, with linear,
 *   stepped or Bézier curves, at [fps].
 *
 * Null when the rig has no bones.
 */
fun exportSpineRig(
    source: CanvasState,
    rig: Rig,
    fps: Int,
    textMeasurer: TextMeasurer,
    density: Density,
    imageFor: (String) -> ImageBitmap?,
): ByteArray? {
    if (rig.bones.isEmpty()) return null
    val names = HashMap<String, String>()
    val used = HashSet<String>().apply { add(SPINE_ROOT) }
    fun unique(base: String): String {
        val clean = base.trim().ifEmpty { "item" }
        var name = clean
        var n = 2
        while (!used.add(name)) name = "$clean ${n++}"
        return name
    }
    for (b in rig.bones) names[b.id] = unique(b.name)
    val boneIndex = HashMap<String, Int>().apply { rig.bones.forEachIndexed { i, b -> put(b.id, i + 1) } } // root is 0

    val bonesJson = JSONArray().put(JSONObject().put("name", SPINE_ROOT))
    for (b in rig.bones) {
        bonesJson.put(
            JSONObject()
                .put("name", names.getValue(b.id))
                .put("parent", b.parentId?.let { names[it] } ?: SPINE_ROOT)
                .put("length", r2(b.length))
                .put("x", r2(b.x)).put("y", r2(-b.y))
                .put("rotation", r2(-Math.toDegrees(b.rotation)))
                .apply {
                    if (b.scaleX != 1.0) put("scaleX", r2(b.scaleX))
                    if (b.scaleY != 1.0) put("scaleY", r2(b.scaleY))
                    if (b.shearX != 0.0) put("shearX", r2(-Math.toDegrees(b.shearX)))
                    if (b.shearY != 0.0) put("shearY", r2(-Math.toDegrees(b.shearY)))
                    when (b.inherit) {
                        BoneInherit.NORMAL -> {}
                        BoneInherit.ONLY_TRANSLATION -> put("inherit", "onlyTranslation")
                        BoneInherit.NO_ROTATION_OR_REFLECTION -> put("inherit", "noRotationOrReflection")
                        BoneInherit.NO_SCALE -> put("inherit", "noScale")
                    }
                },
        )
    }

    val setup = rig.setupPose()
    val slots = JSONArray()
    val skin = JSONObject()
    val images = LinkedHashMap<String, ByteArray>()
    val attachmentsById = rig.attachments.associateBy { it.id }
    for (slot in rig.slots) {
        val attachment = attachmentsById[slot.attachmentId] ?: continue
        val ink = attachment.strokes.map { it.deepCopy().onAnimFrame(0) }
        val rendered = renderBoneInk(source, ink, textMeasurer, density, imageFor) ?: continue
        val slotName = unique(slot.name)
        val imageName = slotName
        images[imageName] = rendered.png
        slots.put(JSONObject().put("name", slotName).put("bone", names[slot.boneId] ?: SPINE_ROOT).put("attachment", imageName))
        skin.put(slotName, JSONObject().put(imageName, meshJson(rendered, rig, setup, boneIndex)))
    }

    val animations = JSONObject()
    for (anim in rig.animations) {
        val bones = JSONObject()
        for ((boneId, timelines) in anim.timelines.groupBy { it.boneId }) {
            val name = names[boneId] ?: continue
            val json = JSONObject()
            fun timeline(p: BoneProperty) = timelines.firstOrNull { it.property == p }
            timeline(BoneProperty.ROTATE)?.let { json.put("rotate", singleKeys(it, fps) { v -> -Math.toDegrees(v) }) }
            pairKeys(timeline(BoneProperty.X), timeline(BoneProperty.Y), fps, { it }, { -it }, 0.0)?.let { json.put("translate", it) }
            pairKeys(timeline(BoneProperty.SCALE_X), timeline(BoneProperty.SCALE_Y), fps, { it }, { it }, 1.0)?.let { json.put("scale", it) }
            pairKeys(timeline(BoneProperty.SHEAR_X), timeline(BoneProperty.SHEAR_Y), fps, { -Math.toDegrees(it) }, { -Math.toDegrees(it) }, 0.0)?.let { json.put("shear", it) }
            if (json.length() > 0) bones.put(name, json)
        }
        animations.put(unique(anim.name), JSONObject().put("bones", bones))
    }

    val skeleton = JSONObject()
        .put("skeleton", JSONObject().put("spine", "4.1.00").put("images", "./images/"))
        .put("bones", bonesJson)
        .put("slots", slots)
        .put("skins", JSONArray().put(JSONObject().put("name", "default").put("attachments", skin)))
        .put("animations", animations)

    val out = ByteArrayOutputStream()
    ZipOutputStream(out).use { zip ->
        zip.putNextEntry(ZipEntry("skeleton.json"))
        zip.write(skeleton.toString(2).toByteArray())
        zip.closeEntry()
        for ((name, png) in images) {
            zip.putNextEntry(ZipEntry("images/$name.png"))
            zip.write(png)
            zip.closeEntry()
        }
    }
    return out.toByteArray()
}

/**
 * A weighted mesh over [rendered]'s image: a [MESH_CELLS] x [MESH_CELLS] grid, its outline first
 * (Spine's hull), each vertex bound to its nearest bones with the canvas's automatic weights and
 * given in each bone's setup frame, mirrored to Spine's y-up.
 */
private fun meshJson(rendered: RenderedInk, rig: Rig, setup: Map<String, Affine>, boneIndex: Map<String, Int>): JSONObject {
    val n = MESH_CELLS
    val left = rendered.centerX - rendered.worldWidth / 2
    val top = rendered.centerY - rendered.worldHeight / 2
    // Grid points (i across, j down), outline first in order around the edge, then the inside.
    val outline = ArrayList<Pair<Int, Int>>()
    for (i in 0..n) outline += i to 0
    for (j in 1..n) outline += n to j
    for (i in n - 1 downTo 0) outline += i to n
    for (j in n - 1 downTo 1) outline += 0 to j
    val inside = (1 until n).flatMap { j -> (1 until n).map { i -> i to j } }
    val order = outline + inside
    val indexOf = order.withIndex().associate { (k, ij) -> ij to k }
    val uvs = JSONArray()
    val worldPoints = order.map { (i, j) ->
        uvs.put(r4(i.toDouble() / n)).put(r4(j.toDouble() / n))
        WorldPoint(left + rendered.worldWidth * i / n, top + rendered.worldHeight * j / n)
    }
    val triangles = JSONArray()
    for (j in 0 until n) for (i in 0 until n) {
        val a = indexOf.getValue(i to j)
        val b = indexOf.getValue(i + 1 to j)
        val c = indexOf.getValue(i + 1 to j + 1)
        val d = indexOf.getValue(i to j + 1)
        triangles.put(a).put(b).put(c).put(a).put(c).put(d)
    }
    val weights = autoWeights(worldPoints, rig.bones, setup)
    val vertices = JSONArray()
    worldPoints.forEachIndexed { k, p ->
        val w = weights[k].ifEmpty { listOf(rig.bones.first().id to 1.0) }
        vertices.put(w.size)
        for ((boneId, weight) in w) {
            val local = (setup[boneId] ?: Affine.IDENTITY).inverse().apply(p)
            vertices.put(boneIndex.getValue(boneId)).put(r2(local.x)).put(r2(-local.y)).put(r4(weight))
        }
    }
    return JSONObject()
        .put("type", "mesh")
        .put("uvs", uvs)
        .put("triangles", triangles)
        .put("vertices", vertices)
        .put("hull", outline.size)
        .put("width", rendered.image.width)
        .put("height", rendered.image.height)
}

/** A one-value timeline as Spine keys: time in seconds, value through [convert], curves in absolute time and value. */
private fun singleKeys(timeline: BoneTimeline, fps: Int, convert: (Double) -> Double): JSONArray {
    val keys = JSONArray()
    timeline.keys.forEachIndexed { i, key ->
        val json = JSONObject().put("time", r4(key.time / fps)).put("value", r2(convert(key.value)))
        val next = timeline.keys.getOrNull(i + 1)
        curveJson(key, next, fps, listOf(convert(key.value) to (next?.let { convert(it.value) } ?: 0.0)))?.let { json.put("curve", it) }
        keys.put(json)
    }
    return keys
}

/**
 * Two timelines (x and y of one transform) as Spine's paired keys: one key at every time either
 * has, each value read from its own timeline; a missing timeline gives [default]. Null when
 * neither exists.
 */
private fun pairKeys(x: BoneTimeline?, y: BoneTimeline?, fps: Int, cx: (Double) -> Double, cy: (Double) -> Double, default: Double): JSONArray? {
    if (x == null && y == null) return null
    val times = ((x?.keys.orEmpty() + y?.keys.orEmpty()).map { it.time }).distinct().sorted()
    val keys = JSONArray()
    times.forEachIndexed { i, t ->
        val vx = cx(x?.valueAt(t) ?: default)
        val vy = cy(y?.valueAt(t) ?: default)
        val json = JSONObject().put("time", r4(t / fps)).put("x", r2(vx)).put("y", r2(vy))
        val nextTime = times.getOrNull(i + 1)
        // A curve only where both channels have a key here and at the next time, so it describes them exactly.
        val kx = x?.keys?.firstOrNull { it.time == t }
        val ky = y?.keys?.firstOrNull { it.time == t }
        if (nextTime != null && (kx != null || x == null) && (ky != null || y == null)) {
            val nx = cx(x?.valueAt(nextTime) ?: default)
            val ny = cy(y?.valueAt(nextTime) ?: default)
            val curveX = kx?.let { curveJson(it, Key(nextTime, 0.0), fps, listOf(vx to nx)) }
            val curveY = ky?.let { curveJson(it, Key(nextTime, 0.0), fps, listOf(vy to ny)) }
            when {
                curveX == "stepped" || curveY == "stepped" -> json.put("curve", "stepped")
                curveX is JSONArray || curveY is JSONArray -> {
                    val arr = JSONArray()
                    fun linear(v0: Double, v1: Double) = listOf(t / fps + (nextTime - t) / fps / 3, v0 + (v1 - v0) / 3, t / fps + 2 * (nextTime - t) / fps / 3, v0 + 2 * (v1 - v0) / 3)
                    val px = (curveX as? JSONArray)?.let { a -> (0 until 4).map { a.getDouble(it) } } ?: linear(vx, nx)
                    val py = (curveY as? JSONArray)?.let { a -> (0 until 4).map { a.getDouble(it) } } ?: linear(vy, ny)
                    for (v in px + py) arr.put(r4(v))
                    json.put("curve", arr)
                }
            }
        }
        keys.put(json)
    }
    return keys
}

/**
 * The Spine curve from [key] to [next]: "stepped", four numbers (two handles in absolute time and
 * value, Spine 4's form) for a Bézier, or null for linear. [values] holds the converted value
 * pair (from, to) of the one channel.
 */
private fun curveJson(key: Key, next: Key?, fps: Int, values: List<Pair<Double, Double>>): Any? {
    if (next == null) return null
    return when (val curve = key.curve) {
        KeyCurve.Linear -> null
        KeyCurve.Stepped -> "stepped"
        is KeyCurve.Bezier -> {
            val (v0, v1) = values.first()
            val t0 = key.time / fps
            val t1 = next.time / fps
            val c = curve.curve
            JSONArray().put(r4(t0 + c.x1 * (t1 - t0))).put(r4(v0 + c.y1 * (v1 - v0))).put(r4(t0 + c.x2 * (t1 - t0))).put(r4(v0 + c.y2 * (v1 - v0)))
        }
    }
}

// Mesh grid cells per side: enough vertices to bend smoothly at a joint, few enough to stay light.
private const val MESH_CELLS = 8

private fun r2(value: Double): Double = Math.round(value * 100.0) / 100.0
private fun r4(value: Double): Double = Math.round(value * 10000.0) / 10000.0
