package com.andene.fern.canvas

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
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

/**
 * The rig as a DragonBones project (roadmap item 87): a zip of `fern_ske.json` (DragonBones
 * 5.5 data), `fern_tex.json` and `fern_tex.png` (a texture atlas: each bone's ink, side by side).
 *
 * Same content as [exportSpine]: the first frame with bones is the setup pose under an added
 * root bone, each bone's ink one image in its own slot, and an animation with one key per frame
 * with a skeleton, durations in frames from the X-sheet. DragonBones shares Fern's axes (y down,
 * clockwise angles), so nothing is flipped. Null when there's no skeleton.
 */
fun exportDragonBones(source: CanvasState, textMeasurer: TextMeasurer, density: Density, imageFor: (String) -> ImageBitmap?): ByteArray? {
    val frames = (0 until source.animFrameCount).filter { f -> source.bones.any { it.animFrame == f } }
    val setupFrame = frames.firstOrNull() ?: return null
    val setup = parentsFirst(source.bones.filter { it.animFrame == setupFrame })
    val names = setup.withIndex().associate { (i, b) -> b.id to "bone${i + 1}" }
    fun local(bone: Bone, pose: List<Bone>): Triple<Double, Double, Double> {
        val parent = bone.parentId?.let { id -> pose.firstOrNull { it.id == id } } ?: return Triple(bone.head.x, bone.head.y, Math.toDegrees(bone.angle))
        val dx = bone.head.x - parent.head.x
        val dy = bone.head.y - parent.head.y
        val c = cos(parent.angle)
        val s = sin(parent.angle)
        return Triple(dx * c + dy * s, -dx * s + dy * c, Math.toDegrees(normalizeAngle(bone.angle - parent.angle)))
    }
    val setupLocal = setup.associate { it.id to local(it, setup) }

    val bones = JSONArray().put(JSONObject().put("name", SPINE_ROOT))
    for (b in setup) {
        val (x, y, r) = setupLocal.getValue(b.id)
        bones.put(
            JSONObject().put("name", names.getValue(b.id)).put("parent", b.parentId?.let { names[it] } ?: SPINE_ROOT).put("length", r2(b.length))
                .put("transform", JSONObject().put("x", r2(x)).put("y", r2(y)).put("skX", r2(r)).put("skY", r2(r))),
        )
    }

    // Each bone's ink, in stacking order, packed left to right into one atlas.
    val strokeOrder = source.strokes.withIndex().filter { it.value.animFrame == setupFrame && it.value.boneId != null && it.value.points.isNotEmpty() }
    val slotBones = strokeOrder.groupBy { it.value.boneId!! }.filterKeys { id -> setup.any { it.id == id } }
        .entries.sortedBy { (_, list) -> list.minOf { it.index } }.map { it.key }
    val rendered = slotBones.mapNotNull { id -> renderBoneInk(source, strokeOrder.filter { it.value.boneId == id }.map { it.value }, textMeasurer, density, imageFor)?.let { id to it } }
    val atlasWidth = rendered.sumOf { it.second.image.width }.coerceAtLeast(1)
    val atlasHeight = (rendered.maxOfOrNull { it.second.image.height } ?: 1).coerceAtLeast(1)
    val atlas = ImageBitmap(atlasWidth, atlasHeight)
    val atlasCanvas = Canvas(atlas)
    val subTextures = JSONArray()
    val slots = JSONArray()
    val skinSlots = JSONArray()
    var left = 0
    for ((id, ink) in rendered) {
        val name = names.getValue(id)
        atlasCanvas.drawImage(ink.image, Offset(left.toFloat(), 0f), Paint())
        subTextures.put(JSONObject().put("name", name).put("x", left).put("y", 0).put("width", ink.image.width).put("height", ink.image.height))
        left += ink.image.width
        slots.put(JSONObject().put("name", name).put("parent", name))
        val bone = setup.first { it.id == id }
        // The image's centre in the bone's frame, scaled from atlas pixels to world units, kept upright.
        val dx = ink.centerX - bone.head.x
        val dy = ink.centerY - bone.head.y
        val c = cos(bone.angle)
        val s = sin(bone.angle)
        val rotation = -Math.toDegrees(bone.angle)
        skinSlots.put(
            JSONObject().put("name", name).put(
                "display",
                JSONArray().put(
                    JSONObject().put("name", name).put(
                        "transform",
                        JSONObject().put("x", r2(dx * c + dy * s)).put("y", r2(-dx * s + dy * c)).put("skX", r2(rotation)).put("skY", r2(rotation))
                            .put("scX", r2(ink.worldWidth / ink.image.width)).put("scY", r2(ink.worldHeight / ink.image.height)),
                    ),
                ),
            ),
        )
    }

    // One key per frame with a skeleton, lasting until the next one.
    val starts = IntArray(source.animFrameCount + 1).also { for (f in 0 until source.animFrameCount) it[f + 1] = it[f] + source.holdOf(f) }
    val boneTimelines = JSONArray()
    for (b in setup) {
        val translate = JSONArray()
        val rotate = JSONArray()
        frames.forEachIndexed { k, f ->
            val pose = source.bones.filter { it.animFrame == f }
            val bone = pose.firstOrNull { it.id == b.id } ?: return@forEachIndexed
            val (x, y, r) = local(bone, pose)
            val (bx, by, br) = setupLocal.getValue(b.id)
            val duration = (if (k + 1 < frames.size) starts[frames[k + 1]] else starts[source.animFrameCount]) - starts[f]
            translate.put(JSONObject().put("duration", duration).put("x", r2(x - bx)).put("y", r2(y - by)))
            rotate.put(JSONObject().put("duration", duration).put("rotate", r2(Math.toDegrees(normalizeAngle(Math.toRadians(r - br))))))
        }
        boneTimelines.put(JSONObject().put("name", names.getValue(b.id)).put("translateFrame", translate).put("rotateFrame", rotate))
    }

    val armature = JSONObject()
        .put("type", "Armature").put("frameRate", source.animFps).put("name", "armature")
        .put("bone", bones).put("slot", slots)
        .put("skin", JSONArray().put(JSONObject().put("slot", skinSlots)))
        .put("animation", JSONArray().put(JSONObject().put("name", "animation").put("playTimes", 0).put("duration", starts[source.animFrameCount]).put("bone", boneTimelines)))
        .put("defaultActions", JSONArray().put(JSONObject().put("gotoAndPlay", "animation")))
    val skeleton = JSONObject().put("frameRate", source.animFps).put("name", "fern").put("version", "5.5").put("compatibleVersion", "5.5").put("armature", JSONArray().put(armature))
    val texture = JSONObject().put("name", "fern").put("imagePath", "fern_tex.png").put("width", atlasWidth).put("height", atlasHeight).put("SubTexture", subTextures)

    val png = ByteArrayOutputStream().also { atlas.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
    val out = ByteArrayOutputStream()
    ZipOutputStream(out).use { zip ->
        for ((name, bytes) in listOf("fern_ske.json" to skeleton.toString(2).toByteArray(), "fern_tex.json" to texture.toString(2).toByteArray(), "fern_tex.png" to png)) {
            zip.putNextEntry(ZipEntry(name))
            zip.write(bytes)
            zip.closeEntry()
        }
    }
    return out.toByteArray()
}

private fun r2(value: Double): Double = Math.round(value * 100.0) / 100.0
