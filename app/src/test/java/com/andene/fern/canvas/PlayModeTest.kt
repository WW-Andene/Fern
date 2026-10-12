package com.andene.fern.canvas

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Roadmap item 161: play mode - scene, camera, pause, HUD, and the view coming back. */
@RunWith(RobolectricTestRunner::class)
class PlayModeTest {
    @get:Rule
    val compose = createComposeRule()

    private fun game(): Pair<CanvasState, GameObject> {
        val state = CanvasState()
        state.viewportSize = Size(400f, 400f)
        state.loadStrokes(listOf(Stroke(Color.Black, 2.0).apply { addPoint(WorldPoint(0.0, 0.0)); addPoint(WorldPoint(10.0, 10.0)) }))
        state.selection.add(state.strokes[0])
        val hero = state.objectForEditing("Hero")!!.first
        state.updateObject(hero.copy(properties = mapOf("score" to 7.0), behaviours = listOf(Behaviour(BehaviourKind.MOVE, mapOf("vx" to 1000.0, "vy" to 0.0))), showInHud = true))
        return state to state.gameObjects.single()
    }

    @Test
    fun theSceneFrameFitsTheScreenAndTheCameraFollowsWithinIt() {
        val (state, hero) = game()
        state.loadFrames(listOf(Frame("level", "Level", WorldPoint(-100.0, -100.0), WorldPoint(1900.0, 100.0))))
        state.changeGameSettings(state.gameSettings.copy(sceneFrameId = "level", cameraTarget = hero.id))
        val before = state.panWorld to state.scale
        state.startPlay()
        assertEquals(WorldBox(WorldPoint(-100.0, -100.0), WorldPoint(1900.0, 100.0)), state.play!!.scene)
        assertEquals(0.2, state.scale, 1e-9) // 2000 wide into 400 px
        repeat(60) { state.stepPlay(1.0 / 60) }
        // The camera went along with the hero (a second at 1000/s), never past the scene's end.
        assertTrue(state.panWorld.x > 500)
        state.playPaused = true
        val frozen = state.play!!.instanceOf(hero.id)!!.x
        state.stepPlay(0.5)
        assertEquals(frozen, state.play!!.instanceOf(hero.id)!!.x, 1e-9)
        state.restartPlay()
        state.stopPlay()
        assertEquals(before, state.panWorld to state.scale) // the view from before play, even after a restart
        assertNull(state.play)
    }

    @Test
    fun theSettingsAndHudFlagAreSaved() {
        val (state, hero) = game()
        val settings = state.gameSettings.copy(sceneFrameId = "f", cameraTarget = hero.id)
        val (objects, read) = GameObjectsJson.readGame(GameObjectsJson.writeGame(state.gameObjects.toList(), settings))
        assertEquals(settings, read)
        assertTrue(objects.single().showInHud)
    }

    @Test
    fun thePlayScreenShowsTheHudAndItsControlsWork() {
        val (state, _) = game()
        state.startPlay()
        // The game steps every frame, so the test clock is driven by hand.
        compose.mainClock.autoAdvance = false
        compose.setContent { PlayOverlay(state) }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("Hero score: 7").assertExists()
        compose.onNodeWithContentDescription("Pause").performClick()
        assertTrue(state.playPaused)
        compose.onNodeWithContentDescription("Stop").performClick()
        assertNull(state.play)
    }
}
