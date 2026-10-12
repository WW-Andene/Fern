package com.andene.fern.canvas

import androidx.annotation.StringRes
import com.andene.fern.R
import org.json.JSONArray
import org.json.JSONObject

/*
 * Visual scripting (roadmap item 156): an object's rules, built from blocks - "when" blocks
 * (the conditions of item 158) and "do" blocks (the actions below). Each step of the game a rule
 * whose conditions all hold runs its actions, in order; a rule with no conditions runs every
 * step. Use "action pressed" or a property as a flag for things that should happen once.
 */

/** What an action does; [needsText] and [needsValue] say which of its fields it reads. */
enum class ActionKind(@StringRes val label: Int, val needsText: Boolean = false, val needsValue: Boolean = false) {
    /** Sets a property (text: property) to a value. */
    SET_PROPERTY(R.string.rule_set_property, needsText = true, needsValue = true),
    /** Adds a value to a property (negative takes away). */
    ADD_TO_PROPERTY(R.string.rule_add_to_property, needsText = true, needsValue = true),
    /** Sets the speed across (world units a second). */
    SET_SPEED_X(R.string.rule_set_speed_x, needsValue = true),
    /** Sets the speed down (negative: up). */
    SET_SPEED_Y(R.string.rule_set_speed_y, needsValue = true),
    /** Turns to an angle (degrees). */
    TURN_TO(R.string.rule_turn_to, needsValue = true),
    SHOW(R.string.rule_show),
    HIDE(R.string.rule_hide),
    /** Takes the object out of play until the game restarts. */
    REMOVE(R.string.rule_remove),
    /** Takes out what it touches that's called the text (empty: any object it touches). */
    REMOVE_TOUCHED(R.string.rule_remove_touched, needsText = true),
    /** Moves its state machine (roadmap item 158) to a state (text). */
    GO_TO_STATE(R.string.rule_go_to_state, needsText = true),
}

/** One action: its [kind], and the name ([text]) and number ([value]) the kind reads. */
data class RuleAction(val kind: ActionKind, val text: String = "", val value: Double = 0.0)

/** When every condition holds, do the actions. */
data class Rule(val conditions: List<Condition>, val actions: List<RuleAction>)

/**
 * What objects can be made to do while the game plays, shared by rules' actions and game
 * scripts' commands so the two always act alike.
 */
object GameActions {
    fun setProperty(o: GameInstance, name: String, value: Double) {
        o.properties[name] = value
    }

    fun addToProperty(o: GameInstance, name: String, amount: Double) {
        o.properties[name] = (o.properties[name] ?: 0.0) + amount
    }

    /** Takes [o] out of play. */
    fun remove(o: GameInstance) {
        o.removed = true
        o.vx = 0.0
        o.vy = 0.0
    }

    /** Takes out the objects [o] touches called [name] (empty: any); how many went. */
    fun removeTouched(world: GameWorld, o: GameInstance, name: String): Int {
        val touched = world.touching(o, name)
        touched.forEach { remove(it) }
        return touched.size
    }

    /** Runs [action] on [o]. */
    fun apply(world: GameWorld, o: GameInstance, action: RuleAction) {
        when (action.kind) {
            ActionKind.SET_PROPERTY -> setProperty(o, action.text, action.value)
            ActionKind.ADD_TO_PROPERTY -> addToProperty(o, action.text, action.value)
            ActionKind.SET_SPEED_X -> o.vx = action.value
            ActionKind.SET_SPEED_Y -> o.vy = action.value
            ActionKind.TURN_TO -> o.angle = action.value % 360
            ActionKind.SHOW -> o.visible = true
            ActionKind.HIDE -> o.visible = false
            ActionKind.REMOVE -> remove(o)
            ActionKind.REMOVE_TOUCHED -> removeTouched(world, o, action.text)
            ActionKind.GO_TO_STATE -> world.goToState(o, action.text)
        }
    }

    /** Runs [o]'s rules for one step: each rule whose conditions all hold, in order, until [o] is removed. */
    fun runRules(world: GameWorld, o: GameInstance) {
        for (rule in o.def.rules) {
            if (o.removed) return
            if (rule.conditions.all { conditionHolds(it, o, world) }) for (action in rule.actions) apply(world, o, action)
        }
    }
}

/** Reading and writing rules in the game's JSON. */
object GameRulesJson {
    fun write(rules: List<Rule>) = JSONArray().apply {
        for (r in rules) put(
            JSONObject()
                .put("when", JSONArray(r.conditions.map(GameLogicJson::condition)))
                .put("do", JSONArray(r.actions.map { JSONObject().put("kind", it.kind.name).put("text", it.text).put("value", it.value) })),
        )
    }

    fun read(array: JSONArray): List<Rule> = (0 until array.length()).mapNotNull { i ->
        val r = array.optJSONObject(i) ?: return@mapNotNull null
        val actions = r.optJSONArray("do")?.let { a ->
            (0 until a.length()).mapNotNull { k ->
                val o = a.optJSONObject(k) ?: return@mapNotNull null
                ActionKind.entries.firstOrNull { it.name == o.optString("kind") }?.let { RuleAction(it, o.optString("text"), o.optDouble("value", 0.0)) }
            }
        }.orEmpty()
        Rule(GameLogicJson.conditions(r.optJSONArray("when")), actions)
    }
}
