package com.andene.fern.canvas

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.math.hypot

/** Roadmap item 157: drawn hitboxes and links between objects. */
@RunWith(RobolectricTestRunner::class)
class HitboxesAndLinksTest {
    private val scene = WorldBox(WorldPoint(-1000.0, -1000.0), WorldPoint(1000.0, 1000.0))

    private fun box(x: Double, y: Double, w: Double, h: Double) = WorldBox(WorldPoint(x, y), WorldPoint(x + w, y + h))

    @Test
    fun aHitboxReplacesTheDrawingsBoxForCollisions() {
        // A drawing 40 wide whose hitbox is only its bottom 10 x 10.
        val hitbox = listOf(WorldPoint(15.0, 30.0), WorldPoint(25.0, 30.0), WorldPoint(25.0, 40.0), WorldPoint(15.0, 40.0))
        val world = GameWorld(
            listOf(GameObject("p", "P", body = PhysicsBody(BodyType.DYNAMIC, bounce = 0.0), hitbox = hitbox), GameObject("floor", "F", body = PhysicsBody(BodyType.STATIC))),
            mapOf("p" to box(0.0, 0.0, 40.0, 40.0), "floor" to box(-100.0, 100.0, 200.0, 20.0)),
            scene, GameSettings(gravityY = 900.0, walls = false),
        )
        repeat(180) { world.step(1.0 / 60) }
        val p = world.instanceOf("p")!!
        assertEquals(100.0, p.hitBox.max.y, 0.5)
        assertEquals(p.box.max.y, p.hitBox.max.y, 1e-9) // the hitbox is at the drawing's bottom here
        assertEquals(10.0, p.hitBox.width, 1e-9)
    }

    @Test
    fun rodsKeepDistanceRopesOnlyLimitItAndAttachmentsCarry() {
        fun world(kind: LinkKind) = GameWorld(
            listOf(GameObject("anchor", "A", body = PhysicsBody(BodyType.STATIC)), GameObject("bob", "B", body = PhysicsBody(BodyType.DYNAMIC))),
            mapOf("anchor" to box(0.0, 0.0, 10.0, 10.0), "bob" to box(100.0, 0.0, 10.0, 10.0)),
            scene, GameSettings(gravityY = 900.0, walls = false, links = listOf(ObjectLink("l", "anchor", "bob", kind, 100.0))),
        )
        val rod = world(LinkKind.ROD)
        repeat(120) { rod.step(1.0 / 60) }
        val a = rod.instanceOf("anchor")!!.centre
        val b = rod.instanceOf("bob")!!.centre
        assertEquals(100.0, hypot(b.x - a.x, b.y - a.y), 1.0) // swung down like a pendulum, same length
        assertTrue(b.y > a.y + 10)
        val rope = world(LinkKind.ROPE)
        rope.step(1.0 / 60)
        assertTrue(hypot(rope.instanceOf("bob")!!.centre.x - 5.0, rope.instanceOf("bob")!!.centre.y - 5.0) <= 100.5)
        val carried = GameWorld(
            listOf(GameObject("car", "C", behaviours = listOf(Behaviour(BehaviourKind.MOVE, mapOf("vx" to 50.0, "vy" to 0.0)))), GameObject("rider", "R")),
            mapOf("car" to box(0.0, 0.0, 10.0, 10.0), "rider" to box(0.0, -20.0, 10.0, 10.0)),
            scene, GameSettings(gravityY = 0.0, links = listOf(ObjectLink("l", "car", "rider", LinkKind.ATTACH, 20.0))),
        )
        carried.step(1.0)
        assertEquals(50.0, carried.instanceOf("rider")!!.x, 1e-9)
    }

    @Test
    fun drawnLinesBecomeHitboxesAndLinks() {
        val state = CanvasState()
        fun square(x: Double) = Stroke(Color.Black, 2.0).apply { for (p in listOf(WorldPoint(x, 0.0), WorldPoint(x + 10, 0.0), WorldPoint(x + 10, 10.0), WorldPoint(x, 10.0))) addPoint(p) }
        state.loadStrokes(listOf(square(0.0), square(100.0)))
        state.selection.add(state.strokes[0]); val left = state.objectForEditing("Left")!!.first
        state.selection.clear(); state.selection.add(state.strokes[1]); val right = state.objectForEditing("Right")!!.first
        // A line from the right square to the left one: the right is the link's first object.
        val line = Stroke(Color.Black, 2.0).apply { addPoint(WorldPoint(105.0, 5.0)); addPoint(WorldPoint(5.0, 5.0)) }
        state.strokes.add(line)
        state.selection.clear(); state.selection.addAll(state.strokes)
        assertTrue(state.canLink)
        state.linkSelection(LinkKind.ROPE)
        val link = state.gameSettings.links.single()
        assertEquals(right.id, link.from)
        assertEquals(left.id, link.to)
        assertEquals(100.0, link.length, 1e-9)
        assertEquals(2, state.strokes.size) // the line became the link
        // A closed line with one object becomes its hitbox.
        val ring = Stroke(Color.Black, 2.0).apply { for (p in listOf(WorldPoint(2.0, 2.0), WorldPoint(8.0, 2.0), WorldPoint(8.0, 8.0), WorldPoint(2.0, 8.0), WorldPoint(2.0, 2.0))) addPoint(p) }
        state.strokes.add(ring)
        state.selection.clear(); state.selection.add(state.strokes[0]); state.selection.add(ring)
        assertTrue(state.canUseHitbox)
        state.useSelectionAsHitbox()
        assertEquals(5, state.gameObjects.first { it.id == left.id }.hitbox!!.size)
        assertFalse(state.strokes.any { it === ring })
        // Removing an object takes its links with it.
        state.removeObject(left.id)
        assertTrue(state.gameSettings.links.isEmpty())
    }
}
