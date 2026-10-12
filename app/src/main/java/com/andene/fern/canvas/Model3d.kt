package com.andene.fern.canvas

import android.content.Context
import android.graphics.Bitmap
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.util.UUID
import java.util.zip.DeflaterOutputStream
import java.util.zip.InflaterInputStream

/**
 * A paintable 3D model (roadmap item 108): its [mesh], its painted surface [maps], and the
 * [camera] and [light] it was last viewed with. Placed on the canvas as an image item showing
 * its render ([ImageItem.modelFileName] names the saved model). A posable hand (roadmap item
 * 146) keeps its [handPose], from which its mesh is rebuilt when it's posed again.
 */
class Model3d(val mesh: Mesh, val maps: SurfaceMaps, val camera: OrbitCamera = OrbitCamera(), val light: Light3d = Light3d(), val handPose: HandPose? = null)

/**
 * The model file (".fmodel"): magic "FM3D", a version, then the mesh arrays, the maps, the
 * camera and the light, all Deflate-compressed after the header. Files are written once and
 * never changed (a saved edit is a new file), so duplicated documents can share them, as images do.
 */
object Model3dFile {
    private const val MAGIC = 0x464D3344 // "FM3D"
    // 2: an optional hand pose after the light (roadmap item 146).
    // 3: the light's colour and exposure after the pose (roadmap item 147).
    private const val VERSION = 3
    // Guards a damaged size from allocating gigabytes before failing.
    private const val MAX_MAP_SIZE = 4096
    private const val MAX_VERTICES = 3 * MAX_OBJ_TRIANGLES

    fun write(model: Model3d): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).apply {
            writeInt(MAGIC)
            writeInt(VERSION)
            flush()
        }
        DataOutputStream(DeflaterOutputStream(bytes)).use { out ->
            val mesh = model.mesh
            out.writeInt(mesh.vertexCount)
            for (f in mesh.positions) out.writeFloat(f)
            for (f in mesh.normals) out.writeFloat(f)
            for (f in mesh.uvs) out.writeFloat(f)
            out.writeInt(mesh.indices.size)
            for (i in mesh.indices) out.writeInt(i)
            val maps = model.maps
            out.writeInt(maps.size)
            for (c in maps.color) out.writeInt(c)
            out.write(maps.roughness)
            out.write(maps.metallic)
            with(model.camera) { out.writeDouble(yaw); out.writeDouble(pitch); out.writeDouble(distance); out.writeDouble(fovDegrees) }
            with(model.light) { out.writeDouble(yaw); out.writeDouble(pitch); out.writeDouble(intensity); out.writeDouble(ambient) }
            val pose = model.handPose
            out.writeBoolean(pose != null)
            if (pose != null) {
                for (c in pose.curls) out.writeDouble(c)
                for (s in pose.spreads) out.writeDouble(s)
                out.writeDouble(pose.thumbAcross)
                out.writeBoolean(pose.left)
            }
            out.writeInt(model.light.color)
            out.writeDouble(model.light.exposure)
        }
        return bytes.toByteArray()
    }

    /** Reads a model written by [write]; throws [IOException] on a damaged or newer file. */
    fun read(bytes: ByteArray): Model3d {
        val header = DataInputStream(ByteArrayInputStream(bytes))
        if (header.readInt() != MAGIC) throw IOException("Not a Fern model")
        val version = header.readInt()
        if (version > VERSION) throw IOException("Model format $version is newer than supported $VERSION")
        DataInputStream(InflaterInputStream(ByteArrayInputStream(bytes, 8, bytes.size - 8))).use { input ->
            val vertices = input.readInt()
            if (vertices !in 0..MAX_VERTICES) throw IOException("Bad vertex count $vertices")
            val positions = FloatArray(vertices * 3) { input.readFloat() }
            val normals = FloatArray(vertices * 3) { input.readFloat() }
            val uvs = FloatArray(vertices * 2) { input.readFloat() }
            val indexCount = input.readInt()
            if (indexCount !in 0..MAX_VERTICES * 3) throw IOException("Bad index count $indexCount")
            val indices = IntArray(indexCount) { input.readInt() }
            val size = input.readInt()
            if (size !in 1..MAX_MAP_SIZE) throw IOException("Bad map size $size")
            val color = IntArray(size * size) { input.readInt() }
            val roughness = ByteArray(size * size).also { input.readFully(it) }
            val metallic = ByteArray(size * size).also { input.readFully(it) }
            val camera = OrbitCamera(input.readDouble(), input.readDouble(), input.readDouble(), input.readDouble())
            val light = Light3d(input.readDouble(), input.readDouble(), input.readDouble(), input.readDouble())
            val handPose = if (version >= 2 && input.readBoolean()) {
                HandPose(List(5) { input.readDouble().coerceIn(0.0, 1.0) }, List(5) { input.readDouble().coerceIn(-1.0, 1.0) }, input.readDouble().coerceIn(0.0, 1.0), input.readBoolean())
            } else {
                null
            }
            val lit = if (version >= 3) light.copy(color = input.readInt(), exposure = input.readDouble().coerceIn(0.1, 4.0)) else light
            val mesh = try {
                Mesh(positions, normals, uvs, indices)
            } catch (e: IllegalArgumentException) {
                throw IOException("Damaged mesh", e)
            }
            return Model3d(mesh, SurfaceMaps(size, color, roughness, metallic), camera, lit, handPose)
        }
    }
}

/** The texture size new models are painted at: fine enough for a phone screen's view of the model. */
const val MODEL_MAP_SIZE = 1024

/** A model saved for the canvas: its picture and model file names, and the picture's height over width. */
class StoredModel(val posterFileName: String, val modelFileName: String, val aspect: Double)

/**
 * Saves [model] as a new model file and a new picture of it (see [Model3dFile] on why files are
 * never overwritten). Blocking: call off the main thread.
 */
fun storeModel(context: Context, model: Model3d): StoredModel {
    val modelName = "${UUID.randomUUID()}.fmodel"
    CanvasStorage.modelFile(context, modelName).writeBytes(Model3dFile.write(model))
    val poster = renderModelPoster(model)
    val posterName = "${UUID.randomUUID()}.png"
    CanvasStorage.imageFile(context, posterName).outputStream().use { poster.compress(Bitmap.CompressFormat.PNG, 100, it) }
    return StoredModel(posterName, modelName, poster.height.toDouble() / poster.width)
}

/** A new model of [mesh] with blank white maps, ready to paint. */
fun newModel(mesh: Mesh) = Model3d(mesh, SurfaceMaps.blank(MODEL_MAP_SIZE, 0xFFFFFFFF.toInt()))

/** A new posable hand (roadmap item 146) in [pose], skin-coloured, its palm toward the camera. */
fun newHandModel(pose: HandPose = HandPreset.RELAXED.pose) =
    Model3d(handMesh(pose), SurfaceMaps.blank(MODEL_MAP_SIZE, HAND_SKIN), OrbitCamera(yaw = 0.0, pitch = 0.1, distance = 3.4), Light3d(), pose)

private val HAND_SKIN = 0xFFE8C4A8.toInt()
