package com.andene.fern.canvas

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Roadmap item 158: state machines playing frame tags, moved by conditions. */
@RunWith(RobolectricTestRunner::class)
class StateMachineTest {
    private val machine = StateMachine(
        listOf(AnimState("idle", "idle"), AnimState("walk", "walk"), AnimState("wave", "wave", loop = false)),
        "idle",
        listOf(
            Transition("idle", "walk", listOf(Condition(ConditionKind.ACTION_DOWN, "right"))),
            Transition("walk", "idle", listOf(Condition(ConditionKind.TIME_IN_STATE, value = 0.5))),
            Transition(null, "wave", listOf(Condition(ConditionKind.PROPERTY_ABOVE, "joy", 1.0))),
        ),
    )
    private val tags = listOf(FrameTag("idle", 0, 0, 0), FrameTag("walk", 1, 3, 0), FrameTag("wave", 4, 5, 0))

    private fun world() = GameWorld(
        listOf(GameObject("o", "O", stateMachine = machine)),
        mapOf("o" to WorldBox(WorldPoint(0.0, 0.0), WorldPoint(10.0, 10.0))),
        WorldBox(WorldPoint(-100.0, -100.0), WorldPoint(100.0, 100.0)),
        GameSettings(gravityY = 0.0),
        AnimationInfo(fps = 10, holds = emptyMap(), tags = tags, frameCount = 6),
    )

    @Test
    fun statesPlayTheirTagsAndTransitionsFollowConditions() {
        val w = world()
        val o = w.instanceOf("o")!!
        w.step(0.01)
        assertEquals("idle", o.state)
        assertEquals(0, o.frame)
        w.input.key(KeyEvent.KEYCODE_DPAD_RIGHT, true)
        w.step(0.01)
        assertEquals("walk", o.state)
        assertEquals(1, o.frame)
        w.input.key(KeyEvent.KEYCODE_DPAD_RIGHT, false)
        w.step(0.1)
        assertEquals(2, o.frame)
        repeat(6) { w.step(0.1) }
        assertEquals("idle", o.state)
        o.properties["joy"] = 2.0
        repeat(5) { w.step(0.1) }
        assertEquals("wave", o.state)
        assertEquals(5, o.frame)
        assertTrue(o.animationDone)
    }

    @Test
    fun machinesSaveWithTheObject() {
        val objects = listOf(GameObject("o", "O", stateMachine = machine))
        assertEquals(objects, GameObjectsJson.read(GameObjectsJson.write(objects)))
    }
}
