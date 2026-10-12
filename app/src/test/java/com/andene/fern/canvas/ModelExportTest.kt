package com.andene.fern.canvas

import android.graphics.BitmapFactory
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipInputStream

/** Roadmap item 147: texture maps, light colour and exposure, and AR export (.glb, .usdz). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ModelExportTest {
    private val model = Model3d(primitiveMesh(MeshPrimitive.CUBE), SurfaceMaps.blank(16, 0xFF336699.toInt()).also { it.roughness[0] = 9 })

    @Test
    fun glbHasValidChunksAccessorsAndImages() {
        val bytes = exportGlb(model)
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(0x46546C67, buffer.getInt(0))
        assertEquals(2, buffer.getInt(4))
        assertEquals(bytes.size, buffer.getInt(8))
        val jsonLength = buffer.getInt(12)
        assertEquals(0x4E4F534A, buffer.getInt(16))
        assertEquals(0, jsonLength % 4)
        val json = JSONObject(String(bytes, 20, jsonLength, Charsets.UTF_8))
        val binLength = buffer.getInt(20 + jsonLength)
        assertEquals(0x004E4942, buffer.getInt(24 + jsonLength))
        assertEquals(json.getJSONArray("buffers").getJSONObject(0).getInt("byteLength"), binLength)
        val accessors = json.getJSONArray("accessors")
        assertEquals(model.mesh.vertexCount, accessors.getJSONObject(0).getInt("count"))
        assertEquals(model.mesh.indices.size, accessors.getJSONObject(3).getInt("count"))
        assertTrue(accessors.getJSONObject(0).has("min"))
        // The embedded colour image decodes to the map's size.
        val views = json.getJSONArray("bufferViews")
        val image = views.getJSONObject(json.getJSONArray("images").getJSONObject(0).getInt("bufferView"))
        val binStart = 28 + jsonLength
        val png = bytes.copyOfRange(binStart + image.getInt("byteOffset"), binStart + image.getInt("byteOffset") + image.getInt("byteLength"))
        assertEquals(16, BitmapFactory.decodeByteArray(png, 0, png.size).width)
        for (i in 0 until views.length()) assertEquals(0, views.getJSONObject(i).getInt("byteOffset") % 4)
    }

    @Test
    fun usdzIsStoredWithSixtyFourByteAlignedFilesAndTheSceneFirst() {
        val bytes = exportUsdz(model)
        val entries = ArrayList<java.util.zip.ZipEntry>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip -> while (true) entries.add(zip.nextEntry ?: break) }
        assertEquals(listOf("model.usda", "color.png", "roughness.png", "metallic.png"), entries.map { it.name })
        assertTrue(entries.all { it.method == java.util.zip.ZipEntry.STORED })
        // Walk the local headers: each file's data starts on a 64-byte boundary.
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        var offset = 0
        var files = 0
        while (buffer.getInt(offset) == 0x04034b50) {
            assertEquals(0, buffer.getShort(offset + 8).toInt()) // stored
            val size = buffer.getInt(offset + 18)
            val nameLength = buffer.getShort(offset + 26).toInt()
            val extraLength = buffer.getShort(offset + 28).toInt()
            val data = offset + 30 + nameLength + extraLength
            assertEquals(0, data % 64)
            if (files == 0) assertTrue(String(bytes, data, 10, Charsets.UTF_8).startsWith("#usda 1.0"))
            offset = data + size
            files++
        }
        assertEquals(4, files)
    }

    @Test
    fun lightColourTintsAndExposureBrightens() {
        val sphere = primitiveMesh(MeshPrimitive.SPHERE)
        val white = SurfaceMaps.blank(32, 0xFFFFFFFF.toInt())
        val camera = OrbitCamera(yaw = 0.0, pitch = 0.0)
        fun centre(light: Light3d) = renderMesh(sphere, white, camera, light, 48, 48).pixels[24 * 48 + 24]
        val red = centre(Light3d(yaw = 0.0, pitch = 0.0, ambient = 0.0, color = 0xFFFF0000.toInt()))
        assertTrue((red shr 16 and 0xFF) > 100 && (red and 0xFF) < 40)
        val dim = centre(Light3d(yaw = 0.0, pitch = 0.0, intensity = 0.3, ambient = 0.0))
        val bright = centre(Light3d(yaw = 0.0, pitch = 0.0, intensity = 0.3, ambient = 0.0, exposure = 2.0))
        assertTrue((bright and 0xFF) > (dim and 0xFF) + 40)
    }

    @Test
    fun theLightsColourAndExposureAreSaved() {
        val lit = Model3d(model.mesh, model.maps, light = Light3d(color = 0xFFFFE0B2.toInt(), exposure = 1.5))
        val read = Model3dFile.read(Model3dFile.write(lit))
        assertEquals(0xFFFFE0B2.toInt(), read.light.color)
        assertEquals(1.5, read.light.exposure, 1e-12)
        val bytes = Model3dFile.write(lit)
        ByteBuffer.wrap(bytes).putInt(4, 2) // as a version 2 file: no colour or exposure
        assertEquals(Light3d().color, Model3dFile.read(bytes).light.color)
    }

    @Test
    fun mapsExportAndAColourTextureImports() {
        val zip = exportMapsZip(model)
        val names = ArrayList<String>()
        ZipInputStream(ByteArrayInputStream(zip)).use { while (true) names.add((it.nextEntry ?: break).name) }
        assertEquals(listOf("color.png", "roughness.png", "metallic.png"), names)
        val roughness = mapPng(model.maps, SurfaceChannel.ROUGHNESS)
        assertNotNull(BitmapFactory.decodeByteArray(roughness, 0, roughness.size))
        val maps = SurfaceMaps.blank(4, 0)
        importColorMap(maps, intArrayOf(0xFFFF0000.toInt(), 0xFF00FF00.toInt(), 0xFF0000FF.toInt(), 0xFFFFFFFF.toInt()), 2, 2)
        assertEquals(0xFFFF0000.toInt(), maps.color[0])
        assertEquals(0xFFFFFFFF.toInt(), maps.color[15])
    }
}
