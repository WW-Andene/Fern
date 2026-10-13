package com.andene.fern.canvas

import android.content.Context
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File

/**
 * Saving a document's rig (roadmap items 250-253, 272): bones, slots, attachments with their
 * weights, and animations with their keyed timelines, as one JSON file per document. An
 * attachment's ink goes through [StrokeCodec], the same encoding as the canvas's own strokes.
 */
object RigStorage {
    fun file(context: Context, documentId: String) = File(context.filesDir, "rig_$documentId.json")

    fun delete(context: Context, documentId: String) {
        file(context, documentId).delete()
    }

    fun save(context: Context, documentId: String, rig: Rig) {
        val target = file(context, documentId)
        if (rig.bones.isEmpty() && rig.slots.isEmpty()) {
            target.delete()
            return
        }
        val temp = File(target.parentFile, "${target.name}.tmp")
        temp.writeText(toJson(rig).toString())
        temp.renameTo(target)
    }

    fun load(context: Context, documentId: String): Rig {
        val f = file(context, documentId)
        if (!f.exists()) return Rig(emptyList())
        return runCatching { fromJson(JSONObject(f.readText())) }.getOrElse { Rig(emptyList()) }
    }

    fun toJson(rig: Rig): JSONObject {
        val bones = JSONArray()
        for (b in rig.bones) {
            bones.put(
                JSONObject().put("id", b.id).put("name", b.name).put("parent", b.parentId ?: JSONObject.NULL)
                    .put("x", b.x).put("y", b.y).put("rotation", b.rotation).put("length", b.length)
                    .put("scaleX", b.scaleX).put("scaleY", b.scaleY).put("shearX", b.shearX).put("shearY", b.shearY)
                    .put("inherit", b.inherit.name)
                    .apply { b.physics?.let { put("physics", JSONObject().put("strength", it.strength).put("damping", it.damping)) } },
            )
        }
        val slots = JSONArray()
        for (s in rig.slots) slots.put(JSONObject().put("id", s.id).put("name", s.name).put("bone", s.boneId).put("attachment", s.attachmentId ?: JSONObject.NULL))
        val attachments = JSONArray()
        for (a in rig.attachments) {
            val bytes = ByteArrayOutputStream()
            DataOutputStream(bytes).use { StrokeCodec.write(it, a.strokes) }
            val weights = JSONArray()
            for (stroke in a.weights) {
                val perPoint = JSONArray()
                for (point in stroke) {
                    val pairs = JSONArray()
                    for ((bone, w) in point) pairs.put(bone).put(w)
                    perPoint.put(pairs)
                }
                weights.put(perPoint)
            }
            attachments.put(
                JSONObject().put("id", a.id).put("name", a.name)
                    .put("strokes", Base64.encodeToString(bytes.toByteArray(), Base64.NO_WRAP)).put("weights", weights),
            )
        }
        val animations = JSONArray()
        for (anim in rig.animations) {
            val timelines = JSONArray()
            for (t in anim.timelines) {
                val keys = JSONArray()
                for (k in t.keys) {
                    val key = JSONObject().put("time", k.time).put("value", k.value)
                    when (val c = k.curve) {
                        KeyCurve.Linear -> {}
                        KeyCurve.Stepped -> key.put("curve", "stepped")
                        is KeyCurve.Bezier -> key.put("curve", JSONArray(listOf(c.curve.x1, c.curve.y1, c.curve.x2, c.curve.y2)))
                    }
                    keys.put(key)
                }
                timelines.put(JSONObject().put("bone", t.boneId).put("property", t.property.name).put("keys", keys))
            }
            animations.put(JSONObject().put("id", anim.id).put("name", anim.name).put("length", anim.length).put("timelines", timelines))
        }
        return JSONObject().put("version", 1).put("bones", bones).put("slots", slots).put("attachments", attachments).put("animations", animations)
    }

    fun fromJson(json: JSONObject): Rig {
        fun objects(name: String) = json.optJSONArray(name)?.let { a -> (0 until a.length()).map { a.getJSONObject(it) } }.orEmpty()
        val bones = objects("bones").map { b ->
            RigBone(
                b.getString("id"), b.optString("name"), if (b.isNull("parent")) null else b.getString("parent"),
                b.getDouble("x"), b.getDouble("y"), b.getDouble("rotation"), b.getDouble("length"),
                b.optDouble("scaleX", 1.0), b.optDouble("scaleY", 1.0), b.optDouble("shearX", 0.0), b.optDouble("shearY", 0.0),
                BoneInherit.entries.firstOrNull { it.name == b.optString("inherit") } ?: BoneInherit.NORMAL,
                b.optJSONObject("physics")?.let { BonePhysics(it.optDouble("strength", 0.3), it.optDouble("damping", 0.2)) },
            )
        }
        val slots = objects("slots").map { s ->
            RigSlot(s.getString("id"), s.optString("name"), s.getString("bone"), if (s.isNull("attachment")) null else s.getString("attachment"))
        }
        val attachments = objects("attachments").map { a ->
            val strokes = DataInputStream(ByteArrayInputStream(Base64.decode(a.getString("strokes"), Base64.NO_WRAP))).use { StrokeCodec.read(it) }
            val w = a.getJSONArray("weights")
            val weights = (0 until w.length()).map { si ->
                val perPoint = w.getJSONArray(si)
                (0 until perPoint.length()).map { pi ->
                    val pairs = perPoint.getJSONArray(pi)
                    (0 until pairs.length() / 2).map { k -> pairs.getString(2 * k) to pairs.getDouble(2 * k + 1) }
                }
            }
            RigAttachment(a.getString("id"), a.optString("name"), strokes, weights)
        }
        val animations = objects("animations").map { anim ->
            val t = anim.getJSONArray("timelines")
            val timelines = (0 until t.length()).mapNotNull { i ->
                val tl = t.getJSONObject(i)
                val property = BoneProperty.entries.firstOrNull { it.name == tl.getString("property") } ?: return@mapNotNull null
                val k = tl.getJSONArray("keys")
                val keys = (0 until k.length()).map { ki ->
                    val key = k.getJSONObject(ki)
                    val curve = when (val c = key.opt("curve")) {
                        "stepped" -> KeyCurve.Stepped
                        is JSONArray -> KeyCurve.Bezier(CubicEasing(c.getDouble(0), c.getDouble(1), c.getDouble(2), c.getDouble(3)))
                        else -> KeyCurve.Linear
                    }
                    Key(key.getDouble("time"), key.getDouble("value"), curve)
                }
                BoneTimeline(tl.getString("bone"), property, keys)
            }
            RigAnimation(anim.getString("id"), anim.optString("name"), anim.optInt("length", 1), timelines)
        }
        return Rig(bones, slots, attachments, animations)
    }
}
