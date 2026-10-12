package com.andene.fern.canvas

import android.graphics.Bitmap
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/*
 * Exporting a painted model (roadmap item 147): its texture maps as pictures, and the model for
 * augmented reality and other 3D apps - glTF binary (.glb, which Android's Scene Viewer, Blender
 * and the web read) and USDZ (Apple's AR Quick Look). Both carry the mesh with the colour,
 * roughness and metallic maps as a physically based material, scaled so the model's 2-unit size
 * is [AR_MODEL_METRES] across in the real world.
 */

/** How big a model is in augmented reality: its unit-sphere fit is this many metres across. */
const val AR_MODEL_METRES = 0.3

/** A map as a PNG: the colour map in colour, roughness and metallic as greys. */
fun mapPng(maps: SurfaceMaps, channel: SurfaceChannel): ByteArray {
    val size = maps.size
    val pixels = IntArray(size * size) { k ->
        when (channel) {
            SurfaceChannel.COLOR -> maps.color[k] or (0xFF shl 24)
            SurfaceChannel.ROUGHNESS -> grey(maps.roughness[k].toInt() and 0xFF)
            SurfaceChannel.METALLIC -> grey(maps.metallic[k].toInt() and 0xFF)
        }
    }
    return png(pixels, size)
}

/** The maps as glTF's metallic-roughness texture: roughness in green, metallic in blue. */
private fun metallicRoughnessPng(maps: SurfaceMaps): ByteArray =
    png(IntArray(maps.size * maps.size) { k -> (0xFF shl 24) or ((maps.roughness[k].toInt() and 0xFF) shl 8) or (maps.metallic[k].toInt() and 0xFF) }, maps.size)

private fun grey(v: Int) = (0xFF shl 24) or (v shl 16) or (v shl 8) or v

private fun png(pixels: IntArray, size: Int): ByteArray {
    val bitmap = Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
    return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
}

/** The three maps as PNGs in a ZIP: color.png, roughness.png, metallic.png. */
fun exportMapsZip(model: Model3d): ByteArray {
    val out = ByteArrayOutputStream()
    ZipOutputStream(out).use { zip ->
        for ((name, channel) in listOf("color.png" to SurfaceChannel.COLOR, "roughness.png" to SurfaceChannel.ROUGHNESS, "metallic.png" to SurfaceChannel.METALLIC)) {
            zip.putNextEntry(ZipEntry(name))
            zip.write(mapPng(model.maps, channel))
            zip.closeEntry()
        }
    }
    return out.toByteArray()
}

/**
 * [model] as glTF 2.0 binary (.glb): one mesh with positions, normals, texture coordinates and
 * 32-bit indices, and a metallic-roughness material with the colour map and the combined
 * roughness/metallic map embedded as PNGs; the root node scaled to [AR_MODEL_METRES].
 */
fun exportGlb(model: Model3d): ByteArray {
    val mesh = model.mesh
    val bin = ByteArrayOutputStream()
    val views = JSONArray()
    fun view(bytes: ByteArray, target: Int?): Int {
        while (bin.size() % 4 != 0) bin.write(0)
        val offset = bin.size()
        bin.write(bytes)
        views.put(JSONObject().put("buffer", 0).put("byteOffset", offset).put("byteLength", bytes.size).apply { target?.let { put("target", it) } })
        return views.length() - 1
    }
    fun floats(values: FloatArray) = ByteBuffer.allocate(values.size * 4).order(ByteOrder.LITTLE_ENDIAN).apply { asFloatBuffer().put(values) }.array()
    fun ints(values: IntArray) = ByteBuffer.allocate(values.size * 4).order(ByteOrder.LITTLE_ENDIAN).apply { asIntBuffer().put(values) }.array()
    val accessors = JSONArray()
    fun accessor(view: Int, componentType: Int, count: Int, type: String, min: List<Float>? = null, max: List<Float>? = null): Int {
        accessors.put(JSONObject().put("bufferView", view).put("componentType", componentType).put("count", count).put("type", type).apply {
            min?.let { put("min", JSONArray(it.map(Float::toDouble))) }
            max?.let { put("max", JSONArray(it.map(Float::toDouble))) }
        })
        return accessors.length() - 1
    }
    val count = mesh.vertexCount
    val minP = (0..2).map { k -> (0 until count).minOf { mesh.positions[3 * it + k] } }
    val maxP = (0..2).map { k -> (0 until count).maxOf { mesh.positions[3 * it + k] } }
    val position = accessor(view(floats(mesh.positions), ARRAY_BUFFER), FLOAT, count, "VEC3", minP, maxP)
    val normal = accessor(view(floats(mesh.normals), ARRAY_BUFFER), FLOAT, count, "VEC3")
    // glTF's texture coordinates start at the image's top-left, as Fern's do.
    val texcoord = accessor(view(floats(mesh.uvs), ARRAY_BUFFER), FLOAT, count, "VEC2")
    val indices = accessor(view(ints(mesh.indices), ELEMENT_ARRAY_BUFFER), UNSIGNED_INT, mesh.indices.size, "SCALAR")
    val colorImage = view(mapPng(model.maps, SurfaceChannel.COLOR), null)
    val mrImage = view(metallicRoughnessPng(model.maps), null)
    val json = JSONObject()
        .put("asset", JSONObject().put("version", "2.0").put("generator", "Fern"))
        .put("scene", 0)
        .put("scenes", JSONArray().put(JSONObject().put("nodes", JSONArray().put(0))))
        .put("nodes", JSONArray().put(JSONObject().put("mesh", 0).put("name", "Model").put("scale", JSONArray(List(3) { AR_MODEL_METRES / 2 }))))
        .put("meshes", JSONArray().put(JSONObject().put("primitives", JSONArray().put(
            JSONObject().put("attributes", JSONObject().put("POSITION", position).put("NORMAL", normal).put("TEXCOORD_0", texcoord))
                .put("indices", indices).put("material", 0),
        ))))
        .put("materials", JSONArray().put(JSONObject().put("name", "Painted").put("doubleSided", true).put("pbrMetallicRoughness", JSONObject()
            .put("baseColorTexture", JSONObject().put("index", 0))
            .put("metallicRoughnessTexture", JSONObject().put("index", 1))
            .put("metallicFactor", 1.0).put("roughnessFactor", 1.0))))
        .put("samplers", JSONArray().put(JSONObject().put("magFilter", LINEAR).put("minFilter", LINEAR_MIPMAP_LINEAR).put("wrapS", CLAMP_TO_EDGE).put("wrapT", CLAMP_TO_EDGE)))
        .put("images", JSONArray()
            .put(JSONObject().put("bufferView", colorImage).put("mimeType", "image/png"))
            .put(JSONObject().put("bufferView", mrImage).put("mimeType", "image/png")))
        .put("textures", JSONArray().put(JSONObject().put("source", 0).put("sampler", 0)).put(JSONObject().put("source", 1).put("sampler", 0)))
        .put("accessors", accessors)
        .put("bufferViews", views)
    while (bin.size() % 4 != 0) bin.write(0)
    json.put("buffers", JSONArray().put(JSONObject().put("byteLength", bin.size())))
    // The JSON chunk is padded with spaces, the binary one with zeros, to 4 bytes.
    var jsonBytes = json.toString().toByteArray(Charsets.UTF_8)
    if (jsonBytes.size % 4 != 0) jsonBytes += ByteArray(4 - jsonBytes.size % 4) { ' '.code.toByte() }
    val binBytes = bin.toByteArray()
    val total = 12 + 8 + jsonBytes.size + 8 + binBytes.size
    return ByteBuffer.allocate(total).order(ByteOrder.LITTLE_ENDIAN).apply {
        putInt(0x46546C67) // "glTF"
        putInt(2)
        putInt(total)
        putInt(jsonBytes.size); putInt(0x4E4F534A) // "JSON"
        put(jsonBytes)
        putInt(binBytes.size); putInt(0x004E4942) // "BIN\0"
        put(binBytes)
    }.array()
}

/**
 * [model] as USDZ: an uncompressed ZIP whose first file is the scene in text USD (.usda) - the
 * mesh, and a UsdPreviewSurface material reading the colour, roughness and metallic maps - with
 * the maps beside it, each file's data aligned to 64 bytes as the format requires.
 */
fun exportUsdz(model: Model3d): ByteArray {
    val files = listOf(
        "model.usda" to usda(model).toByteArray(Charsets.UTF_8),
        "color.png" to mapPng(model.maps, SurfaceChannel.COLOR),
        "roughness.png" to mapPng(model.maps, SurfaceChannel.ROUGHNESS),
        "metallic.png" to mapPng(model.maps, SurfaceChannel.METALLIC),
    )
    val out = CountingStream(ByteArrayOutputStream())
    ZipOutputStream(out).use { zip ->
        for ((name, bytes) in files) {
            val entry = ZipEntry(name)
            entry.method = ZipEntry.STORED
            entry.size = bytes.size.toLong()
            entry.compressedSize = bytes.size.toLong()
            entry.crc = CRC32().apply { update(bytes) }.value
            // The local header is 30 bytes, then the name, then this padding field, then the data.
            zip.flush()
            val headerEnd = out.count + 30 + name.length
            val padding = ((64 - (headerEnd + 4) % 64) % 64).toInt()
            entry.extra = ByteBuffer.allocate(4 + padding).order(ByteOrder.LITTLE_ENDIAN).putShort(USDZ_PADDING_ID).putShort(padding.toShort()).array()
            zip.putNextEntry(entry)
            zip.write(bytes)
            zip.closeEntry()
        }
    }
    return (out.target as ByteArrayOutputStream).toByteArray()
}

private fun usda(model: Model3d): String {
    val mesh = model.mesh
    fun n(v: Float) = String.format(Locale.ROOT, "%.6g", v)
    val points = (0 until mesh.vertexCount).joinToString(", ") { "(${n(mesh.positions[3 * it])}, ${n(mesh.positions[3 * it + 1])}, ${n(mesh.positions[3 * it + 2])})" }
    val normals = (0 until mesh.vertexCount).joinToString(", ") { "(${n(mesh.normals[3 * it])}, ${n(mesh.normals[3 * it + 1])}, ${n(mesh.normals[3 * it + 2])})" }
    // USD's texture coordinates run up the image; Fern's run down.
    val st = (0 until mesh.vertexCount).joinToString(", ") { "(${n(mesh.uvs[2 * it])}, ${n(1 - mesh.uvs[2 * it + 1])})" }
    val counts = List(mesh.triangleCount) { 3 }.joinToString(", ")
    val indices = mesh.indices.joinToString(", ")
    val scale = AR_MODEL_METRES / 2
    return """
        |#usda 1.0
        |(
        |    defaultPrim = "Model"
        |    metersPerUnit = 1
        |    upAxis = "Y"
        |)
        |
        |def Xform "Model" (
        |    kind = "component"
        |)
        |{
        |    double3 xformOp:scale = ($scale, $scale, $scale)
        |    uniform token[] xformOpOrder = ["xformOp:scale"]
        |
        |    def Mesh "Mesh"
        |    {
        |        int[] faceVertexCounts = [$counts]
        |        int[] faceVertexIndices = [$indices]
        |        point3f[] points = [$points]
        |        normal3f[] normals = [$normals] (
        |            interpolation = "vertex"
        |        )
        |        texCoord2f[] primvars:st = [$st] (
        |            interpolation = "vertex"
        |        )
        |        uniform token subdivisionScheme = "none"
        |        uniform bool doubleSided = 1
        |        rel material:binding = </Model/Painted>
        |    }
        |
        |    def Material "Painted"
        |    {
        |        token outputs:surface.connect = </Model/Painted/Surface.outputs:surface>
        |
        |        def Shader "Surface"
        |        {
        |            uniform token info:id = "UsdPreviewSurface"
        |            color3f inputs:diffuseColor.connect = </Model/Painted/Color.outputs:rgb>
        |            float inputs:roughness.connect = </Model/Painted/Roughness.outputs:r>
        |            float inputs:metallic.connect = </Model/Painted/Metallic.outputs:r>
        |            token outputs:surface
        |        }
        |
        |        def Shader "Coordinates"
        |        {
        |            uniform token info:id = "UsdPrimvarReader_float2"
        |            token inputs:varname = "st"
        |            float2 outputs:result
        |        }
        |
        |        def Shader "Color"
        |        {
        |            uniform token info:id = "UsdUVTexture"
        |            asset inputs:file = @color.png@
        |            float2 inputs:st.connect = </Model/Painted/Coordinates.outputs:result>
        |            float3 outputs:rgb
        |        }
        |
        |        def Shader "Roughness"
        |        {
        |            uniform token info:id = "UsdUVTexture"
        |            asset inputs:file = @roughness.png@
        |            float2 inputs:st.connect = </Model/Painted/Coordinates.outputs:result>
        |            float outputs:r
        |        }
        |
        |        def Shader "Metallic"
        |        {
        |            uniform token info:id = "UsdUVTexture"
        |            asset inputs:file = @metallic.png@
        |            float2 inputs:st.connect = </Model/Painted/Coordinates.outputs:result>
        |            float outputs:r
        |        }
        |    }
        |}
        |""".trimMargin()
}

/** Counts the bytes written through it, to align USDZ entries. */
private class CountingStream(val target: OutputStream) : OutputStream() {
    var count = 0L
        private set

    override fun write(b: Int) {
        target.write(b)
        count++
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        target.write(b, off, len)
        count += len
    }
}

/**
 * Replaces [maps]' colour map with [argb] ([width] x [height], ARGB), stretched over the whole
 * texture (nearest texel) - a picture painted elsewhere, or exported and edited, brought back.
 */
fun importColorMap(maps: SurfaceMaps, argb: IntArray, width: Int, height: Int) {
    val size = maps.size
    for (y in 0 until size) for (x in 0 until size) {
        val sx = (x * width / size).coerceIn(0, width - 1)
        val sy = (y * height / size).coerceIn(0, height - 1)
        maps.color[y * size + x] = argb[sy * width + sx] or (0xFF shl 24)
    }
}

private const val FLOAT = 5126
private const val UNSIGNED_INT = 5125
private const val ARRAY_BUFFER = 34962
private const val ELEMENT_ARRAY_BUFFER = 34963
private const val LINEAR = 9729
private const val LINEAR_MIPMAP_LINEAR = 9987
private const val CLAMP_TO_EDGE = 33071
// The extra-field ID USD's own packager uses for alignment padding.
private const val USDZ_PADDING_ID: Short = 0x1986
