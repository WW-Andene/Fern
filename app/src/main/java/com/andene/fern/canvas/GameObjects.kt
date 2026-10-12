package com.andene.fern.canvas

import androidx.annotation.StringRes
import com.andene.fern.R
import org.json.JSONArray
import org.json.JSONObject

/*
 * From drawing to logic (roadmap items 155-161, 164): drawings become game objects. An object
 * is a group of strokes (its id is the group's id, so all its strokes, on every animation frame,
 * move together) with a name, named number properties, and behaviours that make it act when the
 * game plays.
 */

/** A behaviour's number setting: its key, default and label. */
class BehaviourParam(val key: String, val default: Double, @StringRes val label: Int)

/** What an object can do on its own (roadmap item 155), each with its settings. */
enum class BehaviourKind(@StringRes val label: Int, val params: List<BehaviourParam>) {
    /** Moves at a steady speed (world units a second). */
    MOVE(R.string.behaviour_move, listOf(BehaviourParam("vx", 60.0, R.string.param_vx), BehaviourParam("vy", 0.0, R.string.param_vy))),
    /** Turns steadily (degrees a second). */
    ROTATE(R.string.behaviour_rotate, listOf(BehaviourParam("speed", 90.0, R.string.param_degrees_per_second))),
    /** Swings back and forth by (dx, dy) and back, once per period (seconds). */
    OSCILLATE(R.string.behaviour_oscillate, listOf(BehaviourParam("dx", 60.0, R.string.param_dx), BehaviourParam("dy", 0.0, R.string.param_dy), BehaviourParam("period", 2.0, R.string.param_period))),
    /** Follows a drawn path at a speed, looping or stopping at the end (loop 1 or 0). */
    FOLLOW_PATH(R.string.behaviour_follow_path, listOf(BehaviourParam("speed", 80.0, R.string.param_speed), BehaviourParam("loop", 1.0, R.string.param_loop))),
    /** Moves and bounces off the edges of the scene. */
    BOUNCE(R.string.behaviour_bounce, listOf(BehaviourParam("vx", 80.0, R.string.param_vx), BehaviourParam("vy", 60.0, R.string.param_vy))),
    /** Comes back in at the opposite edge when it leaves the scene. */
    WRAP(R.string.behaviour_wrap, emptyList()),
    /** Shows and hides, once per period. */
    BLINK(R.string.behaviour_blink, listOf(BehaviourParam("period", 1.0, R.string.param_period))),
    /** Grows and shrinks by a share of its size, once per period. */
    PULSE(R.string.behaviour_pulse, listOf(BehaviourParam("amount", 0.2, R.string.param_amount), BehaviourParam("period", 1.0, R.string.param_period))),
}

/** One behaviour on an object: its [kind], its settings, and for [BehaviourKind.FOLLOW_PATH] the [path] (world points). */
data class Behaviour(val kind: BehaviourKind, val params: Map<String, Double> = kind.params.associate { it.key to it.default }, val path: List<WorldPoint>? = null) {
    fun param(key: String): Double = params[key] ?: kind.params.firstOrNull { it.key == key }?.default ?: 0.0
}

/**
 * A game object (roadmap item 155): the strokes of group [id], called [name], with named number
 * [properties] (a score, health, a speed - for rules and scripts) and [behaviours].
 */
data class GameObject(
    val id: String,
    val name: String,
    val properties: Map<String, Double> = emptyMap(),
    val behaviours: List<Behaviour> = emptyList(),
)

/** Reading and writing the objects (objects_<document>.json). */
object GameObjectsJson {
    fun write(objects: List<GameObject>): JSONArray = JSONArray().apply {
        for (o in objects) put(
            JSONObject().put("id", o.id).put("name", o.name)
                .put("properties", JSONObject().apply { for ((k, v) in o.properties) put(k, v) })
                .put("behaviours", JSONArray().apply {
                    for (b in o.behaviours) put(JSONObject().put("kind", b.kind.name).put("params", JSONObject().apply { for ((k, v) in b.params) put(k, v) }).apply {
                        b.path?.let { path -> put("path", JSONArray(path.map { JSONArray().put(it.x).put(it.y) })) }
                    })
                }),
        )
    }

    fun read(array: JSONArray): List<GameObject> = (0 until array.length()).mapNotNull { i ->
        val o = array.optJSONObject(i) ?: return@mapNotNull null
        val id = o.optString("id").takeIf { it.isNotEmpty() } ?: return@mapNotNull null
        GameObject(
            id,
            o.optString("name", "Object"),
            o.optJSONObject("properties")?.let { p -> p.keys().asSequence().associateWith { p.optDouble(it, 0.0) } }.orEmpty(),
            o.optJSONArray("behaviours")?.let { list ->
                (0 until list.length()).mapNotNull { k ->
                    val b = list.optJSONObject(k) ?: return@mapNotNull null
                    val kind = BehaviourKind.entries.firstOrNull { it.name == b.optString("kind") } ?: return@mapNotNull null
                    val params = b.optJSONObject("params")?.let { p -> p.keys().asSequence().associateWith { p.optDouble(it, 0.0) } }.orEmpty()
                    val path = b.optJSONArray("path")?.let { pts -> (0 until pts.length()).map { WorldPoint(pts.getJSONArray(it).getDouble(0), pts.getJSONArray(it).getDouble(1)) } }
                    Behaviour(kind, kind.params.associate { it.key to it.default } + params, path)
                }
            }.orEmpty(),
        )
    }
}
