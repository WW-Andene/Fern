package com.andene.fern.canvas

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.math.hypot

/** Roadmap item 160: actions mapped to keys, gamepads, touch and the stylus. */
@RunWith(RobolectricTestRunner::class)
class GameInputTest {
    @Test
    fun actionsFollowTheirBindingsAndReportPressesOnce() {
        val input = GameInputState(DEFAULT_ACTIONS)
        input.key(KeyEvent.KEYCODE_A, true)
        input.beginStep()
        assertTrue(input.isDown("left"))
        assertTrue(input.justPressed("left"))
        input.beginStep()
        assertTrue(input.isDown("left"))
        assertFalse(input.justPressed("left")) // held, not pressed again
        input.key(KeyEvent.KEYCODE_A, false)
        input.axis(AXIS_X, 0.3f) // under half way: not pushed
        input.beginStep()
        assertFalse(input.isDown("left") || input.isDown("right"))
        input.axis(AXIS_X, 0.8f)
        input.touchButton(TouchButton.A, true)
        input.pointerAt(WorldPoint(3.0, 4.0), isStylus = true)
        input.beginStep()
        assertTrue(input.isDown("right") && input.isDown("jump") && input.isDown("touch"))
        assertEquals(WorldPoint(3.0, 4.0), input.pointer)
        assertEquals(TouchButton.entries.toSet(), input.touchButtons)
    }

    @Test
    fun actionsRoundTripThroughJson() {
        val custom = listOf(InputAction("dash", listOf(InputBinding.Key(KeyEvent.KEYCODE_SHIFT_LEFT), InputBinding.Axis(AXIS_Y, true), InputBinding.Touch(TouchButton.B), InputBinding.Screen, InputBinding.Stylus)))
        assertEquals(custom, InputActionsJson.read(InputActionsJson.write(custom)))
        val settings = GameSettings(actions = custom)
        assertEquals(custom, GameObjectsJson.readGame(GameObjectsJson.writeGame(emptyList(), settings)).second.actions)
    }

    @Test
    fun platformerJumpsOnlyWhenStandingAndTopDownMovesAtOneSpeed() {
        val box = WorldBox(WorldPoint(0.0, 0.0), WorldPoint(20.0, 20.0))
        val world = GameWorld(
            listOf(
                GameObject("hero", "Hero", behaviours = listOf(Behaviour(BehaviourKind.PLATFORMER, mapOf("speed" to 100.0, "jump" to 400.0))), body = PhysicsBody(BodyType.DYNAMIC, bounce = 0.0)),
                GameObject("floor", "Floor", body = PhysicsBody(BodyType.STATIC)),
            ),
            mapOf("hero" to box, "floor" to WorldBox(WorldPoint(-200.0, 20.0), WorldPoint(200.0, 40.0))),
            WorldBox(WorldPoint(-500.0, -500.0), WorldPoint(500.0, 500.0)), GameSettings(walls = false),
        )
        val hero = world.instanceOf("hero")!!
        world.input.key(KeyEvent.KEYCODE_SPACE, true) // jump held before landing: no jump in mid-air
        repeat(30) { world.step(1.0 / 60) }
        assertTrue(hero.grounded)
        world.input.key(KeyEvent.KEYCODE_SPACE, false)
        world.step(1.0 / 60)
        world.input.key(KeyEvent.KEYCODE_SPACE, true)
        world.input.key(KeyEvent.KEYCODE_DPAD_RIGHT, true)
        world.step(1.0 / 60)
        world.step(1.0 / 60)
        assertTrue(hero.vy < -300) // jumped
        assertEquals(100.0, hero.vx, 1e-9)

        val topDown = GameWorld(listOf(GameObject("p", "P", behaviours = listOf(Behaviour(BehaviourKind.TOP_DOWN, mapOf("speed" to 100.0))))), mapOf("p" to box), WorldBox(WorldPoint(-500.0, -500.0), WorldPoint(500.0, 500.0)), GameSettings(gravityY = 0.0))
        topDown.input.key(KeyEvent.KEYCODE_DPAD_RIGHT, true)
        topDown.input.key(KeyEvent.KEYCODE_DPAD_DOWN, true)
        topDown.step(1.0 / 60)
        val p = topDown.instanceOf("p")!!
        assertEquals(100.0, hypot(p.vx, p.vy), 1e-9) // diagonals aren't faster
    }
}
