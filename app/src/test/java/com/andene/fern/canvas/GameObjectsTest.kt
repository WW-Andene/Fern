package com.andene.fern.canvas

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Roadmap item 155: drawings as game objects with properties and behaviours. */
@RunWith(RobolectricTestRunner::class)
class GameObjectsTest {
    private val box = WorldBox(WorldPoint(0.0, 0.0), WorldPoint(10.0, 10.0))
    private val scene = WorldBox(WorldPoint(-100.0, -100.0), WorldPoint(100.0, 100.0))

    private fun world(vararg behaviours: Behaviour) = GameWorld(listOf(GameObject("o", "O", behaviours = behaviours.toList())), mapOf("o" to box), scene)

    @Test
    fun objectsRoundTripThroughJson() {
        val objects = listOf(GameObject("g", "Ball", mapOf("score" to 3.0), listOf(Behaviour(BehaviourKind.FOLLOW_PATH, path = listOf(WorldPoint(0.0, 0.0), WorldPoint(5.0, 5.0))), Behaviour(BehaviourKind.ROTATE))))
        assertEquals(objects, GameObjectsJson.read(GameObjectsJson.write(objects)))
    }

    @Test
    fun moveRotateSwingBlinkAndPulse() {
        val moving = world(Behaviour(BehaviourKind.MOVE, mapOf("vx" to 10.0, "vy" to -5.0)), Behaviour(BehaviourKind.ROTATE, mapOf("speed" to 90.0)))
        repeat(10) { moving.step(0.1) }
        val o = moving.instances.single()
        assertEquals(10.0, o.x, 1e-9)
        assertEquals(-5.0, o.y, 1e-9)
        assertEquals(90.0, o.angle, 1e-9)
        // The centre stays the turning point: it's only moved, not swung round.
        assertEquals(WorldPoint(15.0, 0.0), o.place(o.pivot))
        val swinging = world(Behaviour(BehaviourKind.OSCILLATE, mapOf("dx" to 20.0, "dy" to 0.0, "period" to 4.0)))
        swinging.step(1.0)
        assertEquals(20.0, swinging.instances.single().swingX, 1e-9) // a quarter period: furthest out
        val blinking = world(Behaviour(BehaviourKind.BLINK, mapOf("period" to 1.0)))
        blinking.step(0.75)
        assertFalse(blinking.instances.single().visible)
        val pulsing = world(Behaviour(BehaviourKind.PULSE, mapOf("amount" to 0.5, "period" to 4.0)))
        pulsing.step(1.0)
        assertEquals(1.5, pulsing.instances.single().scale, 1e-9)
    }

    @Test
    fun bounceAndWrapKeepObjectsInTheScene() {
        val bouncing = world(Behaviour(BehaviourKind.BOUNCE, mapOf("vx" to 100.0, "vy" to 0.0)))
        repeat(200) { bouncing.step(0.02) }
        val b = bouncing.instances.single()
        assertTrue(b.box.max.x <= scene.max.x + 3 && b.box.min.x >= scene.min.x - 3)
        val wrapping = world(Behaviour(BehaviourKind.MOVE, mapOf("vx" to 100.0, "vy" to 0.0)), Behaviour(BehaviourKind.WRAP))
        repeat(300) { wrapping.step(0.02) }
        val w = wrapping.instances.single()
        assertTrue(w.box.min.x <= scene.max.x && w.box.max.x >= scene.min.x)
    }

    @Test
    fun followingAPathLoops() {
        val path = listOf(WorldPoint(0.0, 0.0), WorldPoint(100.0, 0.0))
        val following = world(Behaviour(BehaviourKind.FOLLOW_PATH, mapOf("speed" to 50.0, "loop" to 1.0), path))
        following.step(1.0)
        val o = following.instances.single()
        assertEquals(WorldPoint(50.0, 0.0), o.place(o.pivot))
        following.step(1.5) // 125 along a 100 path: looped round to 25
        assertEquals(25.0, o.place(o.pivot).x, 1e-9)
    }

    @Test
    fun theSelectionBecomesAnObjectThatPlays() {
        val state = CanvasState()
        state.viewportSize = Size(400f, 400f)
        val a = Stroke(Color.Black, 2.0).apply { addPoint(WorldPoint(0.0, 0.0)); addPoint(WorldPoint(10.0, 0.0)) }
        val b = Stroke(Color.Black, 2.0).apply { addPoint(WorldPoint(0.0, 5.0)); addPoint(WorldPoint(10.0, 5.0)) }
        val path = Stroke(Color.Black, 2.0).apply { addPoint(WorldPoint(0.0, 50.0)); addPoint(WorldPoint(90.0, 50.0)) }
        state.loadStrokes(listOf(a, b, path))
        state.selection.addAll(listOf(state.strokes[0], state.strokes[1]))
        val (made, noPath) = state.objectForEditing("Ship")!!
        assertNull(noPath)
        assertEquals(2, state.strokes.count { it.groupId == made.id }) // grouped into one object
        // Selecting it with the line offers the line as a path.
        state.selection.clear()
        state.selection.addAll(state.strokes)
        val (same, offered) = state.objectForEditing("Other")!!
        assertEquals(made.id, same.id)
        assertEquals(2, offered!!.size)
        state.updateObject(same.copy(behaviours = listOf(Behaviour(BehaviourKind.MOVE, mapOf("vx" to 10.0, "vy" to 0.0)))))
        state.startPlay()
        assertNotNull(state.play)
        state.stepPlay(1.0)
        assertEquals(10.0, state.play!!.instanceOf(made.id)!!.x, 1e-9)
        state.stopPlay()
        assertNull(state.play)
        // The drawing itself never moved.
        assertEquals(0.0, state.strokes[0].minX, 1e-9)
    }
}
