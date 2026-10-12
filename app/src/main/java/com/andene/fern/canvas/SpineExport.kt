package com.andene.fern.canvas

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.unit.Density
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.cos
import kotlin.math.sin

/** Longest edge of each bone's attachment image, in px. */
private const val SPINE_IMAGE_MAX_EDGE_PX = 512

/** Name of the skeleton's added root bone, which Spine expects and Fern's free roots hang from. */
const val SPINE_ROOT = "root"

/**
 * The rig as a Spine project (roadmap item 87): a zip of `skeleton.json` (Spine JSON 4.x) and
 * `images/<bone>.png`, the form Spine's "Import Data" reads with an images folder.
 *
 * - Bones: the first animation frame's skeleton is the setup pose, under an added [SPINE_ROOT]
 *   bone. Spine's y axis points up and its angles turn counter-clockwise, so both are flipped.
 * - Attachments: each bone's bound ink on that frame is rendered to an image, placed as a
 *   region attachment so it sits exactly where it was drawn; one slot per bone, in the order
 *   the ink is stacked.
 * - Animation "animation": each frame with a skeleton is a key (at its X-sheet start time and
 *   the playback speed) of every bone's rotation and translation relative to the setup pose.
 *
 * Ink is baked per bone, so smooth-joint bending (weights) becomes rigid in Spine. Null when
 * the first frame with bones has none.
 */
fun exportSpine(source: CanvasState, textMeasurer: TextMeasurer, density: Density, imageFor: (String) -> ImageBitmap?): ByteArray? {
    val frames = (0 until source.animFrameCount).filter { f -> source.bones.any { it.animFrame == f } }
    val setupFrame = frames.firstOrNull() ?: return null
    val setup = parentsFirst(source.bones.filter { it.animFrame == setupFrame })
    val names = uniqueNames(setup)
    val setupLocal = setup.associate { it.id to spineLocal(it, setup) }

    val bonesJson = JSONArray().put(JSONObject().put("name", SPINE_ROOT))
    for (b in setup) {
        val local = setupLocal.getValue(b.id)
        bonesJson.put(
            JSONObject()
                .put("name", names.getValue(b.id))
                .put("parent", b.parentId?.let { names[it] } ?: SPINE_ROOT)
                .put("length", round2(b.length))
                .put("x", round2(local.x)).put("y", round2(local.y))
                .put("rotation", round2(local.rotation)),
        )
    }

    // Slots in stacking order: a bone's slot goes where its lowest stroke is.
    val strokeOrder = source.strokes.withIndex().filter { it.value.animFrame == setupFrame && it.value.boneId != null && it.value.points.isNotEmpty() }
    val slotBones = strokeOrder.groupBy { it.value.boneId!! }.filterKeys { id -> setup.any { it.id == id } }
        .entries.sortedBy { (_, list) -> list.minOf { it.index } }.map { it.key }
    val slots = JSONArray()
    val attachments = JSONObject()
    val images = LinkedHashMap<String, ByteArray>()
    for (boneId in slotBones) {
        val bone = setup.first { it.id == boneId }
        val name = names.getValue(boneId)
        val rendered = renderBoneInk(source, strokeOrder.filter { it.value.boneId == boneId }.map { it.value }, textMeasurer, density, imageFor) ?: continue
        images[name] = rendered.png
        slots.put(JSONObject().put("name", name).put("bone", name).put("attachment", name))
        // The image's centre in the bone's own frame; the image stays upright in the world.
        val world = spineWorld(bone)
        val cx = rendered.centerX
        val cy = -rendered.centerY
        val dx = cx - world.headX
        val dy = cy - world.headY
        val c = cos(world.angle)
        val s = sin(world.angle)
        attachments.put(
            name,
            JSONObject().put(
                name,
                JSONObject()
                    .put("x", round2(dx * c + dy * s)).put("y", round2(-dx * s + dy * c))
                    .put("rotation", round2(-Math.toDegrees(world.angle)))
                    .put("width", round2(rendered.worldWidth)).put("height", round2(rendered.worldHeight)),
            ),
        )
    }

    // Animation keys: one per frame with a skeleton, at the frame's start on the X-sheet.
    val starts = IntArray(source.animFrameCount + 1).also { for (f in 0 until source.animFrameCount) it[f + 1] = it[f] + source.holdOf(f) }
    val boneTimelines = JSONObject()
    for (b in setup) {
        val rotate = JSONArray()
        val translate = JSONArray()
        for (f in frames) {
            val pose = parentsFirst(source.bones.filter { it.animFrame == f })
            val bone = pose.firstOrNull { it.id == b.id } ?: continue
            val local = spineLocal(bone, pose)
            val base = setupLocal.getValue(b.id)
            val time = round2(starts[f].toDouble() / source.animFps)
            rotate.put(JSONObject().put("time", time).put("value", round2(normalizeDegrees(local.rotation - base.rotation))))
            translate.put(JSONObject().put("time", time).put("x", round2(local.x - base.x)).put("y", round2(local.y - base.y)))
        }
        boneTimelines.put(names.getValue(b.id), JSONObject().put("rotate", rotate).put("translate", translate))
    }

    val skeleton = JSONObject()
        .put("skeleton", JSONObject().put("spine", "4.1.00").put("images", "./images/"))
        .put("bones", bonesJson)
        .put("slots", slots)
        .put("skins", JSONArray().put(JSONObject().put("name", "default").put("attachments", attachments)))
        .put("animations", JSONObject().put("animation", JSONObject().put("bones", boneTimelines)))

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

/** A bone's place in Spine's world: head with y up, angle counter-clockwise. */
internal class SpineWorld(val headX: Double, val headY: Double, val angle: Double)

internal fun spineWorld(bone: Bone) = SpineWorld(bone.head.x, -bone.head.y, -bone.angle)

/** A bone's Spine setup values: position in its parent's frame, rotation relative to it (degrees). */
internal class SpineLocal(val x: Double, val y: Double, val rotation: Double)

internal fun spineLocal(bone: Bone, pose: List<Bone>): SpineLocal {
    val world = spineWorld(bone)
    val parent = bone.parentId?.let { id -> pose.firstOrNull { it.id == id } } ?: return SpineLocal(world.headX, world.headY, Math.toDegrees(world.angle))
    val p = spineWorld(parent)
    val dx = world.headX - p.headX
    val dy = world.headY - p.headY
    val c = cos(p.angle)
    val s = sin(p.angle)
    return SpineLocal(dx * c + dy * s, -dx * s + dy * c, normalizeDegrees(Math.toDegrees(world.angle - p.angle)))
}

/** Bones ordered so every parent comes before its children (Spine requires it). */
internal fun parentsFirst(bones: List<Bone>): List<Bone> {
    val byId = bones.associateBy { it.id }
    val placed = LinkedHashMap<String, Bone>()
    fun place(b: Bone, depth: Int) {
        if (b.id in placed || depth > bones.size) return
        b.parentId?.let { byId[it] }?.let { place(it, depth + 1) }
        placed[b.id] = b
    }
    bones.forEach { place(it, 0) }
    return placed.values.toList()
}

/** Readable, unique Spine names for the bones: "bone1", "bone2"... in parent-first order. */
private fun uniqueNames(bones: List<Bone>): Map<String, String> = bones.withIndex().associate { (i, b) -> b.id to "bone${i + 1}" }

private fun normalizeDegrees(degrees: Double): Double = Math.toDegrees(normalizeAngle(Math.toRadians(degrees)))

private fun round2(value: Double): Double = Math.round(value * 100.0) / 100.0

internal class RenderedInk(val image: ImageBitmap, val png: ByteArray, val centerX: Double, val centerY: Double, val worldWidth: Double, val worldHeight: Double)

/** [strokes] rendered on a clear background, framed exactly to their padded bounds. */
internal fun renderBoneInk(source: CanvasState, strokes: List<Stroke>, textMeasurer: TextMeasurer, density: Density, imageFor: (String) -> ImageBitmap?): RenderedInk? {
    if (strokes.isEmpty()) return null
    val minX = strokes.minOf { it.minX - it.widthWorld }
    val minY = strokes.minOf { it.minY - it.widthWorld }
    val maxX = strokes.maxOf { it.maxX + it.widthWorld }
    val maxY = strokes.maxOf { it.maxY + it.widthWorld }
    val ink = CanvasState().apply {
        loadStrokes(strokes.map { it.deepCopy() })
        loadLayers(source.layers.toList())
        goToAnimFrame(strokes.first().animFrame)
    }
    val frame = frameForExport(ink, SPINE_IMAGE_MAX_EDGE_PX, Frame("bone", "bone", WorldPoint(minX, minY), WorldPoint(maxX, maxY))) ?: return null
    val image = ImageBitmap(frame.widthPx, frame.heightPx)
    frame.drawOn(Canvas(image), density, textMeasurer, imageFor, transparent = true)
    val out = ByteArrayOutputStream()
    image.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, out)
    return RenderedInk(image, out.toByteArray(), (minX + maxX) / 2, (minY + maxY) / 2, maxX - minX, maxY - minY)
}
