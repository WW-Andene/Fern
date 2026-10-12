package com.andene.fern.canvas

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.util.zip.ZipInputStream

/** Roadmap item 87: Spine and DragonBones skeletons out, Spine back in. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SkeletalFormatsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val textMeasurer = TextMeasurer(createFontFamilyResolver(context), Density(1f), LayoutDirection.Ltr)

    /** An arm (shoulder at the origin, elbow at 100,0, hand at 200,0) with ink on the forearm, bent down on frame 1. */
    private fun riggedArm(): CanvasState {
        val upper = Bone("u", null, WorldPoint(0.0, 0.0), WorldPoint(100.0, 0.0))
        val fore = Bone("f", "u", WorldPoint(100.0, 0.0), WorldPoint(200.0, 0.0))
        val bent = fore.copy(tail = WorldPoint(100.0, 100.0), animFrame = 1)
        return CanvasState().apply {
            loadStrokes(listOf(Stroke(Color.Black, 4.0, boneId = "f").apply { addPoint(WorldPoint(120.0, 0.0)); addPoint(WorldPoint(190.0, 0.0)) }))
            loadBones(listOf(upper, fore, upper.copy(animFrame = 1), bent))
        }
    }

    private fun entries(zip: ByteArray): Map<String, ByteArray> = buildMap {
        ZipInputStream(zip.inputStream()).use { input ->
            while (true) {
                val entry = input.nextEntry ?: break
                put(entry.name, input.readBytes())
            }
        }
    }

    @Test
    fun spineExportReimportsAsTheSamePoses() {
        val state = riggedArm()
        val zip = exportSpine(state, textMeasurer, Density(1f)) { null }
        assertNotNull(zip)
        val files = entries(zip!!)
        assertTrue("images/bone2.png" in files) // the forearm's ink
        val skeleton = JSONObject(String(files.getValue("skeleton.json")))
        assertEquals(3, skeleton.getJSONArray("bones").length()) // root + two
        val poses = SpineImport.read(zip, state.animFps)
        assertEquals(2, poses.size)
        for ((frame, pose) in poses.withIndex()) {
            val original = state.bones.filter { it.animFrame == frame }.sortedBy { it.parentId != null }
            val read = pose.sortedBy { it.parentId != null }
            assertEquals(original.size, read.size)
            for ((a, b) in original.zip(read)) {
                assertEquals(a.head.x, b.head.x, 0.02)
                assertEquals(a.head.y, b.head.y, 0.02)
                assertEquals(a.tail.x, b.tail.x, 0.05)
                assertEquals(a.tail.y, b.tail.y, 0.05)
            }
            assertEquals(read[0].id, read[1].parentId) // hierarchy kept, Spine's root dropped
        }
        state.importSkeleton(poses)
        assertEquals(8, state.bones.size)
    }

    @Test
    fun dragonBonesExportHoldsTheArmatureAndAnAtlas() {
        val files = entries(exportDragonBones(riggedArm(), textMeasurer, Density(1f)) { null }!!)
        val armature = JSONObject(String(files.getValue("fern_ske.json"))).getJSONArray("armature").getJSONObject(0)
        assertEquals(3, armature.getJSONArray("bone").length())
        assertEquals(1, armature.getJSONArray("slot").length())
        val rotate = armature.getJSONArray("animation").getJSONObject(0).getJSONArray("bone").getJSONObject(1).getJSONArray("rotateFrame")
        assertEquals(90.0, rotate.getJSONObject(1).getDouble("rotate"), 0.01) // frame 1: forearm bent a quarter turn
        assertEquals(1, JSONObject(String(files.getValue("fern_tex.json"))).getJSONArray("SubTexture").length())
        assertTrue(files.getValue("fern_tex.png").isNotEmpty())
    }
}
