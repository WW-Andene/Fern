package com.andene.fern.canvas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Roadmap items 156 and 164: rules built from blocks, and objects' game scripts. */
@RunWith(RobolectricTestRunner::class)
class GameScriptTest {
    private val scene = WorldBox(WorldPoint(-100.0, -100.0), WorldPoint(100.0, 100.0))
    private val here = WorldBox(WorldPoint(0.0, 0.0), WorldPoint(10.0, 10.0))
    private val far = WorldBox(WorldPoint(50.0, 50.0), WorldPoint(60.0, 60.0))

    private fun world(vararg objects: Pair<GameObject, WorldBox>) =
        GameWorld(objects.map { it.first }, objects.associate { it.first.id to it.second }, scene, GameSettings(gravityY = 0.0, walls = false))

    @Test
    fun aRuleCollectsWhatItTouches() {
        val rule = Rule(listOf(Condition(ConditionKind.TOUCHING, "Coin")), listOf(RuleAction(ActionKind.REMOVE_TOUCHED, "Coin"), RuleAction(ActionKind.ADD_TO_PROPERTY, "score", 1.0)))
        val w = world(GameObject("p", "Player", rules = listOf(rule)) to here, GameObject("c", "Coin") to here, GameObject("d", "Coin") to far)
        w.step(0.1)
        w.step(0.1)
        assertEquals(1.0, w.instanceOf("p")!!.properties["score"])
        assertTrue(w.instanceOf("c")!!.removed)
        assertFalse(w.instanceOf("d")!!.removed)
    }

    @Test
    fun aScriptMovesKeepsVariablesAndReadsTheWorld() {
        val script = "if count(\"Coin\") == 1 [ setprop \"seen\" 1 ]\nif time < 0.15 [ set ticks 0 ]\nset ticks ticks + 1\nsetprop \"ticks\" ticks\ngoto 0 0\nspeed 10 0"
        val w = world(GameObject("p", "Player", script = script) to here, GameObject("c", "Coin") to far)
        w.step(0.1)
        w.step(0.1)
        val p = w.instanceOf("p")!!
        assertNull(p.scriptError)
        assertEquals(1.0, p.properties["seen"])
        assertEquals(2.0, p.properties["ticks"])
        assertEquals(10.0, p.vx, 0.0)
        assertEquals(0.0, p.centre.x, 1e-9)
    }

    @Test
    fun aBrokenScriptStopsAndSaysWhy() {
        assertNotNull(GameScript.check("speed 1"))
        assertNull(GameScript.check("speed 1 2"))
        val w = world(GameObject("p", "Player", script = "setprop \"a\" prop(\"missing\") + nothing") to here)
        w.step(0.1)
        assertEquals(1, w.instanceOf("p")!!.scriptError!!.line)
    }

    @Test
    fun rulesAndScriptsRoundTripThroughJson() {
        val objects = listOf(GameObject("g", "Ball", rules = listOf(Rule(listOf(Condition(ConditionKind.GROUNDED)), listOf(RuleAction(ActionKind.SET_SPEED_Y, value = -300.0)))), script = "hide"))
        assertEquals(objects, GameObjectsJson.read(GameObjectsJson.write(objects)))
    }
}
