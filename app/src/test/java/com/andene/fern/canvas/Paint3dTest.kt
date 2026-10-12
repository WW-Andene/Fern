package com.andene.fern.canvas

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs
import kotlin.math.sqrt

/** Roadmap item 108: meshes, OBJ, the software renderer, picking, painting and model files. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Paint3dTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun brightness(argb: Int) = ((argb shr 16) and 0xFF) + ((argb shr 8) and 0xFF) + (argb and 0xFF)

    @Test
    fun primitivesAreValidMeshesWithUnitNormals() {
        for (primitive in MeshPrimitive.entries) {
            val mesh = primitiveMesh(primitive)
            assertTrue(primitive.name, mesh.triangleCount > 0)
            for (i in 0 until mesh.vertexCount) {
                val n = sqrt(mesh.normals[3 * i] * mesh.normals[3 * i] + mesh.normals[3 * i + 1] * mesh.normals[3 * i + 1] + mesh.normals[3 * i + 2] * mesh.normals[3 * i + 2])
                assertEquals(primitive.name, 1f, n, 1e-3f)
            }
            assertTrue(mesh.uvs.all { it in 0f..1f })
        }
    }

    @Test
    fun objQuadsNegativeIndicesAndMissingCoordinates() {
        val quad = readObj(
            """
            # a unit square as one quad
            v 0 0 0
            v 2 0 0
            v 2 2 0
            v 0 2 0
            vt 0 0
            vt 1 0
            vt 1 1
            vt 0 1
            f -4/-4 -3/-3 -2/-2 -1/-1
            """.trimIndent(),
        )!!
        assertEquals(2, quad.triangleCount)
        // Normalized into the unit sphere: the corners at distance 1 from the centre.
        assertEquals(1f, sqrt(quad.positions[0] * quad.positions[0] + quad.positions[1] * quad.positions[1]), 1e-5f)
        // v flipped to run down the texture.
        assertEquals(1f, quad.uvs[1], 1e-6f)
        // Normals computed from the face, all facing +z.
        assertEquals(1f, abs(quad.normals[2]), 1e-5f)
        val bare = readObj("v 0 0 0\nv 1 0 0\nv 0 1 0\nf 1 2 3\n")!!
        assertTrue(bare.uvs.all { it in 0f..1f })
        assertNull(readObj("v 0 0 0\n"))
    }

    @Test
    fun aSphereRendersLitFromTheLightsSideAndTransparentAroundIt() {
        val mesh = primitiveMesh(MeshPrimitive.SPHERE)
        val maps = SurfaceMaps.blank(64, 0xFFFFFFFF.toInt())
        val camera = OrbitCamera(yaw = 0.0, pitch = 0.0, distance = 3.0)
        // Light from the viewer's right.
        val light = Light3d(yaw = Math.PI / 2, pitch = 0.0, intensity = 1.0, ambient = 0.1)
        val image = renderMesh(mesh, maps, camera, light, 64, 64)
        assertEquals(0, image.pixels[0] ushr 24) // corner: nothing
        assertEquals(0xFF, image.pixels[32 * 64 + 32] ushr 24) // centre: the sphere
        assertTrue(brightness(image.pixels[32 * 64 + 44]) > brightness(image.pixels[32 * 64 + 20]))
    }

    @Test
    fun pickingAndPaintingChangeTheTextureUnderTheBrush() {
        val mesh = primitiveMesh(MeshPrimitive.PLANE)
        val maps = SurfaceMaps.blank(128, 0xFFFFFFFF.toInt())
        val camera = OrbitCamera(yaw = 0.0, pitch = 0.0, distance = 3.0)
        assertNull(pickMesh(mesh, camera, 0.0, 0.0, 200, 200)) // the corner misses the plane
        val hit = pickMesh(mesh, camera, 100.0, 100.0, 200, 200)!!
        assertEquals(0.5, hit.u, 1e-6)
        assertEquals(0.5, hit.v, 1e-6)
        val density = texelsPerPixel(mesh, hit.triangle, camera, 200, 200, maps.size)
        assertTrue(density > 0)
        paintDab(maps, hit.u, hit.v, radius = 8.0, softness = 0.0, channel = SurfaceChannel.COLOR, color = 0xFFFF0000.toInt(), value = 0)
        assertEquals(0xFFFF0000.toInt(), maps.color[texelIndex(128, 0.5, 0.5)])
        assertEquals(0xFFFFFFFF.toInt(), maps.color[0])
        paintDab(maps, 0.5, 0.5, 4.0, 0.0, SurfaceChannel.METALLIC, 0xFF000000.toInt(), value = 255)
        assertEquals(255, maps.metallic[texelIndex(128, 0.5, 0.5)].toInt() and 0xFF)
        // The painted spot shows red in the middle of the render.
        val image = renderMesh(mesh, maps, camera, Light3d(yaw = 0.0, pitch = 0.0), 200, 200)
        val centre = image.pixels[100 * 200 + 100]
        assertTrue(((centre shr 16) and 0xFF) > ((centre shr 8) and 0xFF))
    }

    @Test
    fun modelFilesRoundTripAndRejectDamage() {
        val model = Model3d(primitiveMesh(MeshPrimitive.CUBE), SurfaceMaps.blank(16, 0xFF123456.toInt()), OrbitCamera(1.0, 0.2, 4.0, 35.0), Light3d(0.1, 0.2, 0.3, 0.4))
        model.maps.roughness[5] = 7
        val read = Model3dFile.read(Model3dFile.write(model))
        assertArrayEquals(model.mesh.positions, read.mesh.positions, 0f)
        assertArrayEquals(model.mesh.indices, read.mesh.indices)
        assertArrayEquals(model.maps.color, read.maps.color)
        assertEquals(7.toByte(), read.maps.roughness[5])
        assertEquals(model.camera, read.camera)
        assertEquals(model.light, read.light)
        val damaged = Model3dFile.write(model).copyOf(40)
        assertTrue(runCatching { Model3dFile.read(damaged) }.isFailure)
    }

    @Test
    fun aStoredModelIsPlacedAndRepaintedUndoably() {
        val stored = storeModel(context, newModel(primitiveMesh(MeshPrimitive.TORUS)))
        assertTrue(CanvasStorage.modelFile(context, stored.modelFileName).exists())
        assertTrue(CanvasStorage.imageFile(context, stored.posterFileName).exists())
        val state = CanvasState()
        state.placeModel(stored)
        val item = state.imageItems.single()
        assertEquals(stored.modelFileName, item.modelFileName)
        assertEquals(item.id, state.selectedImageId)
        val repainted = storeModel(context, Model3dFile.read(CanvasStorage.modelFile(context, stored.modelFileName).readBytes()))
        state.replaceImageModel(item.id, repainted.posterFileName, repainted.modelFileName, repainted.aspect)
        assertEquals(repainted.modelFileName, state.imageItems.single().modelFileName)
        state.undo()
        assertEquals(stored.modelFileName, state.imageItems.single().modelFileName)
    }
}
