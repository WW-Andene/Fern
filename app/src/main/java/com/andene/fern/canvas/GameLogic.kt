package com.andene.fern.canvas

import androidx.annotation.StringRes
import com.andene.fern.R
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

/*
 * Game logic (roadmap items 156 and 158): the conditions a game tests - shared by state
 * machines' transitions, rules ([Rule]) and game scripts - and state machines that play an object's
 * animations: each state shows a frame tag's frames (drawn frames, or a rig posed frame by
 * frame, whose bound ink follows), and transitions move between states when their conditions
 * hold.
 */

/** What a condition tests; [needsText] and [needsValue] say which of its fields it reads. */
enum class ConditionKind(@StringRes val label: Int, val needsText: Boolean = false, val needsValue: Boolean = false) {
    /** An input action is held (text: the action). */
    ACTION_DOWN(R.string.condition_action_down, needsText = true),
    /** An input action went down this step (text: the action). */
    ACTION_PRESSED(R.string.condition_action_pressed, needsText = true),
    /** Standing on something. */
    GROUNDED(R.string.condition_grounded),
    /** In the air. */
    AIRBORNE(R.string.condition_airborne),
    /** Moving faster than a crawl. */
    MOVING(R.string.condition_moving),
    /** Still. */
    STILL(R.string.condition_still),
    /** Touching an object this step (text: its name; empty: anything, walls included). */
    TOUCHING(R.string.condition_touching, needsText = true),
    /** A property is above a value (text: property, value). */
    PROPERTY_ABOVE(R.string.condition_property_above, needsText = true, needsValue = true),
    /** A property is below a value. */
    PROPERTY_BELOW(R.string.condition_property_below, needsText = true, needsValue = true),
    /** A property equals a value. */
    PROPERTY_EQUALS(R.string.condition_property_equals, needsText = true, needsValue = true),
    /** At least this many seconds in the current state (value). */
    TIME_IN_STATE(R.string.condition_time_in_state, needsValue = true),
    /** The current state's animation has played to its end (never, while it loops). */
    ANIMATION_DONE(R.string.condition_animation_done),
}

/** One condition: its [kind], and the name ([text]) and number ([value]) the kind reads. */
data class Condition(val kind: ConditionKind, val text: String = "", val value: Double = 0.0)

/** A state: its [name], the frame tag it plays ([tag], null: stays on the frame it's drawn on) and whether that [loop]s. */
data class AnimState(val name: String, val tag: String? = null, val loop: Boolean = true)

/** From state [from] (null: any state) to state [to] when every condition holds. */
data class Transition(val from: String?, val to: String, val conditions: List<Condition>)

/** An object's state machine (roadmap item 158): its states, the one it starts in, and the transitions between them. */
data class StateMachine(val states: List<AnimState>, val initial: String, val transitions: List<Transition>)

/** The animation an object's states draw on: the frame rate, the X-sheet holds, the tags and the frame count. */
class AnimationInfo(val fps: Int, val holds: Map<Int, Int>, val tags: List<FrameTag>, val frameCount: Int)

// Speeds under this (world units a second) count as still.
private const val STILL_SPEED = 5.0

/** Whether [condition] holds for [o] in [world] this step. */
fun conditionHolds(condition: Condition, o: GameInstance, world: GameWorld): Boolean = when (condition.kind) {
    ConditionKind.ACTION_DOWN -> world.input.isDown(condition.text)
    ConditionKind.ACTION_PRESSED -> world.input.justPressed(condition.text)
    ConditionKind.GROUNDED -> o.grounded
    ConditionKind.AIRBORNE -> !o.grounded
    ConditionKind.MOVING -> abs(o.vx) + abs(o.vy) >= STILL_SPEED
    ConditionKind.STILL -> abs(o.vx) + abs(o.vy) < STILL_SPEED
    // Empty text: anything, walls included.
    ConditionKind.TOUCHING -> if (condition.text.isEmpty()) world.contacts.any { it.a === o || it.b === o } else world.touching(o, condition.text).isNotEmpty()
    ConditionKind.PROPERTY_ABOVE -> (o.properties[condition.text] ?: 0.0) > condition.value
    ConditionKind.PROPERTY_BELOW -> (o.properties[condition.text] ?: 0.0) < condition.value
    ConditionKind.PROPERTY_EQUALS -> abs((o.properties[condition.text] ?: 0.0) - condition.value) < 1e-9
    ConditionKind.TIME_IN_STATE -> o.stateTime >= condition.value
    ConditionKind.ANIMATION_DONE -> o.animationDone
}

/** Reading and writing logic in the game's JSON. */
object GameLogicJson {
    fun condition(c: Condition) = JSONObject().put("kind", c.kind.name).put("text", c.text).put("value", c.value)

    fun condition(o: JSONObject): Condition? =
        ConditionKind.entries.firstOrNull { it.name == o.optString("kind") }?.let { Condition(it, o.optString("text"), o.optDouble("value", 0.0)) }

    fun conditions(array: JSONArray?): List<Condition> = if (array == null) emptyList() else (0 until array.length()).mapNotNull { array.optJSONObject(it)?.let(::condition) }

    fun write(machine: StateMachine) = JSONObject()
        .put("initial", machine.initial)
        .put("states", JSONArray().apply { for (s in machine.states) put(JSONObject().put("name", s.name).put("loop", s.loop).apply { s.tag?.let { put("tag", it) } }) })
        .put("transitions", JSONArray().apply {
            for (t in machine.transitions) put(JSONObject().put("to", t.to).put("conditions", JSONArray(t.conditions.map(::condition))).apply { t.from?.let { put("from", it) } })
        })

    fun read(o: JSONObject): StateMachine {
        val states = o.optJSONArray("states")?.let { a ->
            (0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.let { s -> AnimState(s.optString("name"), s.optString("tag").takeIf { it.isNotEmpty() }, s.optBoolean("loop", true)) } }
        }.orEmpty()
        val transitions = o.optJSONArray("transitions")?.let { a ->
            (0 until a.length()).mapNotNull { i ->
                a.optJSONObject(i)?.let { t -> Transition(t.optString("from").takeIf { it.isNotEmpty() }, t.optString("to"), conditions(t.optJSONArray("conditions"))) }
            }
        }.orEmpty()
        return StateMachine(states, o.optString("initial", states.firstOrNull()?.name.orEmpty()), transitions)
    }
}
