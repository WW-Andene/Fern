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
    }

    /** Creates a new document with a copy of [id]'s strokes, named [newName]. */
    fun duplicateDocument(context: Context, id: String, newName: String): DocumentMeta {
        val strokes = load(context, id)
        val meta = createDocument(context, newName)
        save(context, meta.id, strokes)
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

    private fun saveStrokesFile(context: Context, documentId: String, strokes: List<Stroke>) {
        val root = JSONArray()
        for (stroke in strokes) {
            val strokeJson = JSONObject()
            strokeJson.put("color", stroke.color.toArgb())
            strokeJson.put("width", stroke.widthWorld)
            val pointsJson = JSONArray()
            for (point in stroke.points) {
                val pointJson = JSONObject()
                pointJson.put("x", point.x)
                pointJson.put("y", point.y)
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
            val stroke = Stroke(color, width)
            val pointsJson = strokeJson.getJSONArray("points")
            for (j in 0 until pointsJson.length()) {
                val pointJson = pointsJson.getJSONObject(j)
                stroke.addPoint(WorldPoint(pointJson.getDouble("x"), pointJson.getDouble("y")))
            }
            strokes.add(stroke)
        }
        return strokes
    }
}
