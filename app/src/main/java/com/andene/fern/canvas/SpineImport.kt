package com.andene.fern.canvas

import org.json.JSONObject
import java.io.IOException
import java.util.UUID
import java.util.zip.ZipInputStream
import kotlin.math.cos
import kotlin.math.sin

/**
 * Reads a Spine skeleton (roadmap item 87) - JSON 3.8 or 4.x, alone or inside a zip as
 * [exportSpine] writes - into Fern bones posed frame by frame, for [CanvasState.importSkeleton].
 *
 * Bones keep their hierarchy, positions and lengths (y flipped back to Fern's downward axis;
 * Spine's own [SPINE_ROOT] bone, when it sits at the origin unrotated, is dropped). The first
 * animation's rotate and translate keys become frames: one per key time, at [fps], each bone's
 * value linearly interpolated between its keys. Scale, shear, constraints and attachments
 * aren't brought across (attachments are images, which Fern can't bind to bones).
 */
object SpineImport {
    fun read(bytes: ByteArray, fps: Int): List<List<Bone>> {
        val json = if (bytes.size >= 2 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()) skeletonFromZip(bytes) else String(bytes)
        return parse(JSONObject(json), fps)
    }

    private fun skeletonFromZip(bytes: ByteArray): String {
        ZipInputStream(bytes.inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.name.endsWith(".json")) return String(zip.readBytes())
            }
        }
        throw IOException("No skeleton JSON in the zip")
    }

    private class SetupBone(val name: String, val parent: String?, val length: Double, val x: Double, val y: Double, val rotation: Double)

    /** Linear keys of one value: (time, value) pairs, by time. */
    private class Track(val keys: List<Pair<Double, Double>>) {
        fun at(time: Double): Double {
            if (keys.isEmpty()) return 0.0
            if (time <= keys.first().first) return keys.first().second
            if (time >= keys.last().first) return keys.last().second
            val next = keys.indexOfFirst { it.first > time }
            val (t0, v0) = keys[next - 1]
            val (t1, v1) = keys[next]
            return v0 + (v1 - v0) * (time - t0) / (t1 - t0)
        }
    }

    internal fun parse(root: JSONObject, fps: Int): List<List<Bone>> {
        val bonesJson = root.getJSONArray("bones")
        val setup = (0 until bonesJson.length()).map { i ->
            val b = bonesJson.getJSONObject(i)
            SetupBone(
                b.getString("name"), if (b.has("parent")) b.getString("parent") else null,
                b.optDouble("length", 0.0), b.optDouble("x", 0.0), b.optDouble("y", 0.0), b.optDouble("rotation", 0.0),
            )
        }
        if (setup.isEmpty()) return emptyList()
        val byName = setup.associateBy { it.name }

        // The first animation's rotate/translate tracks; 3.8 names the rotate value "angle".
        val rotate = HashMap<String, Track>()
        val translateX = HashMap<String, Track>()
        val translateY = HashMap<String, Track>()
        val times = sortedSetOf(0.0)
        root.optJSONObject("animations")?.let { animations ->
            val first = animations.keys().asSequence().firstOrNull() ?: return@let
            val bones = animations.getJSONObject(first).optJSONObject("bones") ?: return@let
            for (name in bones.keys()) {
                val timelines = bones.getJSONObject(name)
                timelines.optJSONArray("rotate")?.let { keys ->
                    rotate[name] = Track((0 until keys.length()).map { k ->
                        val key = keys.getJSONObject(k)
                        key.optDouble("time", 0.0) to (if (key.has("value")) key.getDouble("value") else key.optDouble("angle", 0.0))
                    }.sortedBy { it.first }.also { list -> list.forEach { times.add(it.first) } })
                }
                timelines.optJSONArray("translate")?.let { keys ->
                    val list = (0 until keys.length()).map { k -> keys.getJSONObject(k) }.sortedBy { it.optDouble("time", 0.0) }
                    list.forEach { times.add(it.optDouble("time", 0.0)) }
                    translateX[name] = Track(list.map { it.optDouble("time", 0.0) to it.optDouble("x", 0.0) })
                    translateY[name] = Track(list.map { it.optDouble("time", 0.0) to it.optDouble("y", 0.0) })
                }
            }
        }

        // The Spine root at the origin, unturned, is only a container: its children become roots.
        val dropRoot = setup.first().let { it.parent == null && it.x == 0.0 && it.y == 0.0 && it.rotation == 0.0 && it.length == 0.0 }
        val ids = setup.associate { it.name to UUID.randomUUID().toString() }
        // One frame per key time, quantized to the playback rate (keys closer than a frame merge).
        val frameTimes = times.map { Math.round(it * fps) / fps.toDouble() }.distinct()
        return frameTimes.map { time ->
            val world = HashMap<String, Triple<Double, Double, Double>>() // name -> (x, y, angle) in Spine space
            fun place(b: SetupBone, depth: Int): Triple<Double, Double, Double> {
                world[b.name]?.let { return it }
                val x = b.x + (translateX[b.name]?.at(time) ?: 0.0)
                val y = b.y + (translateY[b.name]?.at(time) ?: 0.0)
                val rotation = Math.toRadians(b.rotation + (rotate[b.name]?.at(time) ?: 0.0))
                val parent = b.parent?.let { byName[it] }?.takeIf { depth < setup.size }?.let { place(it, depth + 1) }
                val placed = if (parent == null) Triple(x, y, rotation) else {
                    val (px, py, pa) = parent
                    Triple(px + x * cos(pa) - y * sin(pa), py + x * sin(pa) + y * cos(pa), pa + rotation)
                }
                world[b.name] = placed
                return placed
            }
            setup.mapNotNull { b ->
                if (dropRoot && b === setup.first()) return@mapNotNull null
                val (x, y, angle) = place(b, 0)
                // Fern bones need a length to be seen and grabbed; a zero-length Spine bone gets a short one.
                val length = b.length.takeIf { it > 0.0 } ?: 8.0
                val parentId = b.parent?.takeUnless { dropRoot && it == setup.first().name }?.let { ids[it] }
                // Back to Fern's space: y down, angles clockwise.
                Bone(ids.getValue(b.name), parentId, WorldPoint(x, -y), WorldPoint(x + cos(angle) * length, -(y + sin(angle) * length)))
            }
        }
    }
}
