package com.andene.fern.canvas

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Roadmap item 159: arcade physics for game objects. */
@RunWith(RobolectricTestRunner::class)
class PhysicsTest {
    private val scene = WorldBox(WorldPoint(-500.0, -500.0), WorldPoint(500.0, 500.0))

    private fun box(x: Double, y: Double, w: Double, h: Double) = WorldBox(WorldPoint(x, y), WorldPoint(x + w, y + h))

    private fun obj(id: String, body: PhysicsBody, vararg behaviours: Behaviour) = GameObject(id, id, behaviours = behaviours.toList(), body = body)

    @Test
    fun aBodyFallsAndComesToRestOnTheFloor() {
        val world = GameWorld(
            listOf(obj("ball", PhysicsBody(BodyType.DYNAMIC, bounce = 0.0)), obj("floor", PhysicsBody(BodyType.STATIC))),
            mapOf("ball" to box(0.0, 0.0, 20.0, 20.0), "floor" to box(-100.0, 100.0, 200.0, 20.0)),
            scene, GameSettings(gravityY = 900.0, walls = false),
        )
        repeat(180) { world.step(1.0 / 60) }
        val ball = world.instanceOf("ball")!!
        // Resting on the floor's top (100) within the slop, not sinking through.
        assertEquals(100.0, ball.box.max.y, 0.5)
        assertTrue(ball.grounded)
        assertEquals(0.0, ball.vy, 20.0)
        assertTrue(world.contacts.any { it.b?.def?.id == "floor" || it.a.def.id == "floor" })
    }

    @Test
    fun bouncyBodiesBounce() {
        val world = GameWorld(
            listOf(obj("ball", PhysicsBody(BodyType.DYNAMIC, BodyShape.CIRCLE, bounce = 0.9))),
            mapOf("ball" to box(0.0, 0.0, 20.0, 20.0)),
            WorldBox(WorldPoint(-100.0, -100.0), WorldPoint(100.0, 100.0)), GameSettings(gravityY = 900.0, walls = true),
        )
        var rose = false
        var previousY = 0.0
        repeat(240) {
            world.step(1.0 / 60)
            val y = world.instanceOf("ball")!!.y
            if (y < previousY - 0.5 && world.instanceOf("ball")!!.box.max.y > 50) rose = true
            previousY = y
        }
        assertTrue("the ball came back up after hitting the bottom wall", rose)
        assertTrue(world.instanceOf("ball")!!.box.max.y <= 100.5)
    }

    @Test
    fun collidingCirclesTradeMomentum() {
        val world = GameWorld(
            listOf(
                obj("a", PhysicsBody(BodyType.DYNAMIC, BodyShape.CIRCLE, bounce = 1.0, friction = 0.0), Behaviour(BehaviourKind.MOVE, mapOf("vx" to 100.0, "vy" to 0.0))),
                obj("b", PhysicsBody(BodyType.DYNAMIC, BodyShape.CIRCLE, bounce = 1.0, friction = 0.0)),
            ),
            mapOf("a" to box(0.0, 0.0, 20.0, 20.0), "b" to box(40.0, 0.0, 20.0, 20.0)),
            scene, GameSettings(gravityY = 0.0, walls = false),
        )
        repeat(60) { world.step(1.0 / 60) }
        val a = world.instanceOf("a")!!
        val b = world.instanceOf("b")!!
        // Equal masses, a straight elastic hit: the moving ball stops, the other carries on.
        assertEquals(100.0, a.vx + b.vx, 1e-6)
        assertTrue(b.vx > 90 && a.vx < 10)
    }

    @Test
    fun settingsAreSavedAndOldObjectListsStillRead() {
        val objects = listOf(obj("x", PhysicsBody(BodyType.DYNAMIC, BodyShape.CIRCLE, 2.0, 0.5, 0.1, 0.5)))
        val settings = GameSettings(10.0, 300.0, walls = false)
        assertEquals(objects to settings, GameObjectsJson.readGame(GameObjectsJson.writeGame(objects, settings)))
        val old = JSONArray(GameObjectsJson.write(listOf(GameObject("y", "Y"))).toString())
        assertEquals(listOf(GameObject("y", "Y")) to GameSettings(), GameObjectsJson.readGame(old))
    }
}
