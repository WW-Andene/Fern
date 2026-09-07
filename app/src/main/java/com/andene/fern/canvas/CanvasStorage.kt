package com.andene.fern.canvas

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Metadata for one saved document, without loading its (potentially large) stroke data. */
data class DocumentMeta(
    val id: String,
    val name: String,
    val lastModified: Long,
)

/** A named, jumpable camera position within one document. */
data class PinMeta(
    val id: String,
    val name: String,
    val x: Double,
    val y: Double,
    val scale: Double,
)

/** Where a document's placed images live and how their metadata is named. */
private const val IMAGES_DIR_NAME = "images"

/**
 * Persists canvas documents to local app storage as JSON: an index file listing every
 * document's metadata, plus one strokes file per document (`canvas_<id>.json`).
 *
 * Uses `org.json` (bundled in the Android SDK) rather than a serialization library, since the
 * schema is small and fixed; revisit if/when a real migration story (versioned schema) is
 * needed for the strokes format itself.
 */
object CanvasStorage {
    private const val INDEX_FILE_NAME = "documents.json"
    private const val LEGACY_FILE_NAME = "canvas.json" // pre-multi-document single canvas file
    private const val DEFAULT_DOCUMENT_NAME = "My Canvas"

    fun listDocuments(context: Context): List<DocumentMeta> {
        migrateLegacyIfNeeded(context)
        val index = readIndex(context)
        val metas = mutableListOf<DocumentMeta>()
        for (i in 0 until index.length()) {
            val entry = index.getJSONObject(i)
            metas.add(DocumentMeta(entry.getString("id"), entry.getString("name"), entry.getLong("lastModified")))
        }
        return metas.sortedByDescending { it.lastModified }
    }

    fun createDocument(context: Context, name: String): DocumentMeta {
        val meta = DocumentMeta(UUID.randomUUID().toString(), name, System.currentTimeMillis())
        saveStrokesFile(context, meta.id, emptyList())
        val index = readIndex(context)
        index.put(metaToJson(meta))
        writeIndex(context, index)
        return meta
    }

    fun renameDocument(context: Context, id: String, newName: String) {
        val index = readIndex(context)
        for (i in 0 until index.length()) {
            val entry = index.getJSONObject(i)
            if (entry.getString("id") == id) {
                entry.put("name", newName)
                entry.put("lastModified", System.currentTimeMillis())
            }
        }
        writeIndex(context, index)
    }

    fun deleteDocument(context: Context, id: String) {
        val index = readIndex(context)
        val kept = JSONArray()
        for (i in 0 until index.length()) {
            val entry = index.getJSONObject(i)
            if (entry.getString("id") != id) kept.put(entry)
        }
        writeIndex(context, kept)
        strokesFile(context, id).delete()
        pinsFile(context, id).delete()
        textItemsFile(context, id).delete()
        imageItemsFile(context, id).delete()
        // Deliberately not deleting files under images/: they're shared/immutable and may
        // still be referenced by a duplicate of this document. Accepted tradeoff - orphaned
        // image files from a deleted, non-duplicated document are left behind rather than
        // adding reference counting.
    }

    /** Creates a new document with a copy of [id]'s strokes, text items, and image references, named [newName]. */
    fun duplicateDocument(context: Context, id: String, newName: String): DocumentMeta {
        val strokes = load(context, id)
        val textItems = loadTextItems(context, id)
        val imageItems = loadImageItems(context, id)
        val meta = createDocument(context, newName)
        save(context, meta.id, strokes)
        saveTextItems(context, meta.id, textItems)
        saveImageItems(context, meta.id, imageItems)
        return meta
    }

    fun save(context: Context, documentId: String, strokes: List<Stroke>) {
        saveStrokesFile(context, documentId, strokes)
        val index = readIndex(context)
        for (i in 0 until index.length()) {
            val entry = index.getJSONObject(i)
            if (entry.getString("id") == documentId) {
                entry.put("lastModified", System.currentTimeMillis())
            }
        }
        writeIndex(context, index)
    }

    fun load(context: Context, documentId: String): List<Stroke> {
        val target = strokesFile(context, documentId)
        if (!target.exists()) return emptyList()
        return parseStrokes(target.readText())
    }

    fun listPins(context: Context, documentId: String): List<PinMeta> {
        val file = pinsFile(context, documentId)
        if (!file.exists()) return emptyList()
        val root = JSONArray(file.readText())
        val pins = mutableListOf<PinMeta>()
        for (i in 0 until root.length()) {
            val entry = root.getJSONObject(i)
            pins.add(
                PinMeta(
                    id = entry.getString("id"),
                    name = entry.getString("name"),
                    x = entry.getDouble("x"),
                    y = entry.getDouble("y"),
                    scale = entry.getDouble("scale"),
                )
            )
        }
        return pins
    }

    fun savePins(context: Context, documentId: String, pins: List<PinMeta>) {
        val root = JSONArray()
        for (pin in pins) {
            root.put(
                JSONObject().apply {
                    put("id", pin.id)
                    put("name", pin.name)
                    put("x", pin.x)
                    put("y", pin.y)
                    put("scale", pin.scale)
                }
            )
        }
        pinsFile(context, documentId).writeText(root.toString())
    }

    fun loadTextItems(context: Context, documentId: String): List<TextItem> {
        val file = textItemsFile(context, documentId)
        if (!file.exists()) return emptyList()
        val root = JSONArray(file.readText())
        val items = mutableListOf<TextItem>()
        for (i in 0 until root.length()) {
            val entry = root.getJSONObject(i)
            items.add(
                TextItem(
                    id = entry.getString("id"),
                    text = entry.getString("text"),
                    position = WorldPoint(entry.getDouble("x"), entry.getDouble("y")),
                    fontSizeWorld = entry.getDouble("fontSize"),
                    color = Color(entry.getInt("color")),
                )
            )
        }
        return items
    }

    fun saveTextItems(context: Context, documentId: String, items: List<TextItem>) {
        val root = JSONArray()
        for (item in items) {
            root.put(
                JSONObject().apply {
                    put("id", item.id)
                    put("text", item.text)
                    put("x", item.position.x)
                    put("y", item.position.y)
                    put("fontSize", item.fontSizeWorld)
                    put("color", item.color.toArgb())
                }
            )
        }
        textItemsFile(context, documentId).writeText(root.toString())
    }

    fun loadImageItems(context: Context, documentId: String): List<ImageItem> {
        val file = imageItemsFile(context, documentId)
        if (!file.exists()) return emptyList()
        val root = JSONArray(file.readText())
        val items = mutableListOf<ImageItem>()
        for (i in 0 until root.length()) {
            val entry = root.getJSONObject(i)
            items.add(
                ImageItem(
                    id = entry.getString("id"),
                    fileName = entry.getString("fileName"),
                    position = WorldPoint(entry.getDouble("x"), entry.getDouble("y")),
                    widthWorld = entry.getDouble("width"),
                    heightWorld = entry.getDouble("height"),
                )
            )
        }
        return items
    }

    fun saveImageItems(context: Context, documentId: String, items: List<ImageItem>) {
        val root = JSONArray()
        for (item in items) {
            root.put(
                JSONObject().apply {
                    put("id", item.id)
                    put("fileName", item.fileName)
                    put("x", item.position.x)
                    put("y", item.position.y)
                    put("width", item.widthWorld)
                    put("height", item.heightWorld)
                }
            )
        }
        imageItemsFile(context, documentId).writeText(root.toString())
    }

    /** Directory holding decoded copies of every inserted image, shared across all documents and keyed by id-derived file name. */
    fun imagesDir(context: Context): File {
        val dir = File(context.filesDir, IMAGES_DIR_NAME)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun imageFile(context: Context, fileName: String): File = File(imagesDir(context), fileName)

    /**
     * One-time upgrade from the pre-multi-document single `canvas.json` file: wraps its
     * content as a document named "My Canvas" so existing saved work isn't lost, then removes
     * the legacy file so this only runs once.
     */
    private fun migrateLegacyIfNeeded(context: Context) {
        val indexFile = File(context.filesDir, INDEX_FILE_NAME)
        val legacyFile = File(context.filesDir, LEGACY_FILE_NAME)
        if (indexFile.exists() || !legacyFile.exists()) return
        val strokes = parseStrokes(legacyFile.readText())
        val meta = DocumentMeta(UUID.randomUUID().toString(), DEFAULT_DOCUMENT_NAME, System.currentTimeMillis())
        saveStrokesFile(context, meta.id, strokes)
        writeIndex(context, JSONArray().put(metaToJson(meta)))
        legacyFile.delete()
    }

    private fun readIndex(context: Context): JSONArray {
        val file = File(context.filesDir, INDEX_FILE_NAME)
        if (!file.exists()) return JSONArray()
        return JSONArray(file.readText())
    }

    private fun writeIndex(context: Context, index: JSONArray) {
        File(context.filesDir, INDEX_FILE_NAME).writeText(index.toString())
    }

    private fun metaToJson(meta: DocumentMeta): JSONObject = JSONObject().apply {
        put("id", meta.id)
        put("name", meta.name)
        put("lastModified", meta.lastModified)
    }

    private fun strokesFile(context: Context, documentId: String) = File(context.filesDir, "canvas_$documentId.json")

    private fun pinsFile(context: Context, documentId: String) = File(context.filesDir, "pins_$documentId.json")

    private fun textItemsFile(context: Context, documentId: String) = File(context.filesDir, "text_$documentId.json")

    private fun imageItemsFile(context: Context, documentId: String) = File(context.filesDir, "images_$documentId.json")

    private fun saveStrokesFile(context: Context, documentId: String, strokes: List<Stroke>) {
        val root = JSONArray()
        for (stroke in strokes) {
            val strokeJson = JSONObject()
            strokeJson.put("color", stroke.color.toArgb())
            strokeJson.put("width", stroke.widthWorld)
            strokeJson.put("penType", stroke.penType.name)
            strokeJson.put("filled", stroke.filled)
            strokeJson.put("blendMode", stroke.blendMode.name)
            val pointsJson = JSONArray()
            for (index in stroke.points.indices) {
                val point = stroke.points[index]
                val pointJson = JSONObject()
                pointJson.put("x", point.x)
                pointJson.put("y", point.y)
                pointJson.put("pressure", stroke.pressures[index])
                pointJson.put("tilt", stroke.tilts[index])
                pointJson.put("orientation", stroke.orientations[index])
                pointsJson.put(pointJson)
            }
            strokeJson.put("points", pointsJson)
            root.put(strokeJson)
        }
        strokesFile(context, documentId).writeText(root.toString())
    }

    private fun parseStrokes(json: String): List<Stroke> {
        val root = JSONArray(json)
        val strokes = mutableListOf<Stroke>()
        for (i in 0 until root.length()) {
            val strokeJson = root.getJSONObject(i)
            val color = Color(strokeJson.getInt("color"))
            val width = strokeJson.getDouble("width")
            // Older saved documents predate PenType and have no "penType" key - default to
            // MARKER (the type those strokes were always rendered as before this existed).
            val penType = PenType.valueOf(strokeJson.optString("penType", PenType.MARKER.name))
            val filled = strokeJson.optBoolean("filled", false)
            // Older saved documents predate blend modes - default to NORMAL (how every
            // stroke was always composited before this existed).
            val blendMode = StrokeBlendMode.valueOf(strokeJson.optString("blendMode", StrokeBlendMode.NORMAL.name))
            val stroke = Stroke(color, width, penType, filled, blendMode)
            val pointsJson = strokeJson.getJSONArray("points")
            for (j in 0 until pointsJson.length()) {
                val pointJson = pointsJson.getJSONObject(j)
                stroke.addPoint(
                    point = WorldPoint(pointJson.getDouble("x"), pointJson.getDouble("y")),
                    // Older saved points predate pressure/tilt/orientation - default to "no data".
                    pressure = pointJson.optDouble("pressure", 1.0).toFloat(),
                    tilt = pointJson.optDouble("tilt", 0.0).toFloat(),
                    orientation = pointJson.optDouble("orientation", 0.0).toFloat(),
                )
            }
            strokes.add(stroke)
        }
        return strokes
    }
}
