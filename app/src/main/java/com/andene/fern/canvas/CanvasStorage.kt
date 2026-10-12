package com.andene.fern.canvas

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.AtomicFile
import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.util.UUID

/** Metadata for one saved document, without loading its (potentially large) stroke data. */
data class DocumentMeta(
    val id: String,
    val name: String,
    val lastModified: Long,
    val favorite: Boolean = false,
    /** When the document was moved to the trash, or null if it isn't trashed. */
    val trashedAt: Long? = null,
    /** Library folder name, or null for the top level. Folders are just this label: one level, no separate folder records. */
    val folder: String? = null,
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
private const val VIDEOS_DIR_NAME = "videos"
private const val MODELS_DIR_NAME = "models"

/**
 * Persists canvas documents to local app storage: an index file listing every document's
 * metadata, plus per-document files for each concern.
 *
 * Strokes, undo history and saved versions - the files that grow with the drawing - are binary
 * ([StrokeCodec], `strokes_<id>.bin`), carrying their own format version. Everything else is
 * small and uses `org.json` (bundled in the Android SDK), wrapped in a versioned envelope; see
 * [FORMAT_VERSION]. JSON stroke files from before the binary format are still read, and
 * replaced by the next save.
 */
object CanvasStorage {
    private const val INDEX_FILE_NAME = "documents.json"
    private const val VERSION_SUFFIX = ".bin"
    private const val LEGACY_VERSION_SUFFIX = ".json"
    internal const val BUNDLE_STROKES_ENTRY = "strokes.bin"
    internal const val LEGACY_BUNDLE_STROKES_ENTRY = "canvas.json"
    private const val PALETTE_FILE_NAME = "palette.json"
    private const val LEGACY_FILE_NAME = "canvas.json" // pre-multi-document single canvas file
    private const val DEFAULT_DOCUMENT_NAME = "My Canvas"

    /**
     * Version of the on-disk format, written into every file's envelope
     * (`{"formatVersion": N, "data": <payload>}`). Bump it when a payload's shape changes in a way
     * old readers can't handle, and add the upgrade step to [migrate]. Files from before
     * versioning are bare payloads and read as version 0.
     */
    private const val FORMAT_VERSION = 1
    private const val KEY_FORMAT_VERSION = "formatVersion"
    private const val KEY_DATA = "data"

    // Matches the canvas backdrop's original hardcoded color, so an existing document with no
    // background file yet (created before this feature existed) looks exactly as it always has.
    // Same 30-day window as Android's own Files and Photos trash, so it matches user expectations.
    // 16 is on the project size scale (CLAUDE.md §7); see saveHistory for why it's below the in-memory cap.
    private const val PERSISTED_HISTORY_STEPS = 16

    // Strokes across all persisted undo (and, separately, redo) steps; see withinBudget.
    private const val PERSISTED_HISTORY_STROKES = 262_144L

    private const val VERSION_INTERVAL_MILLIS = 10L * 60 * 1000
    // 32 versions at most 10 minutes apart covers 5+ hours of continuous drawing, and longer
    // across sessions since versions are only written while drawing. 32 is on the §7 scale.
    private const val MAX_VERSIONS = 32

    private const val TRASH_RETENTION_MILLIS = 30L * 24 * 60 * 60 * 1000

    private val DEFAULT_BACKGROUND_COLOR = Color(0xFFF7F5F0)

    /** Every document, trashed ones included; callers filter on [DocumentMeta.trashedAt]. */
    fun listDocuments(context: Context): List<DocumentMeta> {
        migrateLegacyIfNeeded(context)
        purgeExpiredTrash(context)
        val index = readIndex(context)
        val metas = mutableListOf<DocumentMeta>()
        for (i in 0 until index.length()) {
            metas.add(metaFromJson(index.getJSONObject(i)))
        }
        return metas.sortedByDescending { it.lastModified }
    }

    fun setFolder(context: Context, id: String, folder: String?) {
        updateIndexEntry(context, id) { entry ->
            if (folder.isNullOrBlank()) entry.remove("folder") else entry.put("folder", folder.trim())
        }
    }

    fun setFavorite(context: Context, id: String, favorite: Boolean) {
        updateIndexEntry(context, id) { it.put("favorite", favorite) }
    }

    /** Soft delete: the document stays restorable until [TRASH_RETENTION_MILLIS] has passed. */
    fun moveToTrash(context: Context, id: String) {
        updateIndexEntry(context, id) { it.put("trashedAt", System.currentTimeMillis()) }
    }

    fun restoreFromTrash(context: Context, id: String) {
        updateIndexEntry(context, id) { it.remove("trashedAt") }
    }

    private fun purgeExpiredTrash(context: Context) {
        val cutoff = System.currentTimeMillis() - TRASH_RETENTION_MILLIS
        val index = readIndex(context)
        for (i in 0 until index.length()) {
            val meta = metaFromJson(index.getJSONObject(i))
            val trashedAt = meta.trashedAt ?: continue
            if (trashedAt < cutoff) deleteDocument(context, meta.id)
        }
    }

    private fun updateIndexEntry(context: Context, id: String, change: (JSONObject) -> Unit) {
        val index = readIndex(context)
        for (i in 0 until index.length()) {
            val entry = index.getJSONObject(i)
            if (entry.getString("id") == id) change(entry)
        }
        writeIndex(context, index)
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
        journalFile(context, id).delete()
        legacyStrokesFile(context, id).delete()
        synchronized(this) { lastSaved.remove(id) }
        pinsFile(context, id).delete()
        viewFile(context, id).delete()
        rasterDir(context, id).deleteRecursively()
        textItemsFile(context, id).delete()
        connectorsFile(context, id).delete()
        bonesFile(context, id).delete()
        loadAnimationSettings(context, id).soundtrack?.let { deleteSoundtrack(context, it) }
        animationFile(context, id).delete()
        imageItemsFile(context, id).delete()
        backgroundFile(context, id).delete()
        thumbnailFile(context, id).delete()
        historyFile(context, id).delete()
        legacyHistoryFile(context, id).delete()
        layersFile(context, id).delete()
        layerCompsFile(context, id).delete()
        statsFile(context, id).delete()
        guidesFile(context, id).delete()
        symbolInstancesFile(context, id).delete()
        framesFile(context, id).delete()
        drawingScaleFile(context, id).delete()
        branchesFile(context, id).delete()
        gameObjectsFile(context, id).delete()
        AudioNotes.deleteAll(context, id)
        versionsDir(context, id).deleteRecursively()
        // Deliberately not deleting files under images/: they're shared/immutable and may
        // still be referenced by a duplicate of this document. Accepted tradeoff - orphaned
        // image files from a deleted, non-duplicated document are left behind rather than
        // adding reference counting.
    }

    /** Creates a new document with a copy of [id]'s strokes, text items, and image references, named [newName]. */
    fun duplicateDocument(context: Context, id: String, newName: String): DocumentMeta {
        val strokes = load(context, id)
        val textItems = loadTextItems(context, id)
        val connectors = loadConnectors(context, id)
        val bones = loadBones(context, id)
        val animation = loadAnimationSettings(context, id)
        val imageItems = loadImageItems(context, id)
        val (backgroundColor, backgroundStyle) = loadBackground(context, id)
        val layers = loadLayers(context, id)
        val layerComps = loadLayerComps(context, id)
        val guides = loadGuides(context, id)
        val symbolInstances = loadSymbolInstances(context, id)
        val frames = loadFrames(context, id)
        val drawingScale = loadDrawingScale(context, id)
        val branches = branchesFile(context, id).takeIf { it.exists() }?.readBytes()
        val gameObjects = loadGameObjects(context, id)
        val meta = createDocument(context, newName)
        save(context, meta.id, strokes)
        saveTextItems(context, meta.id, textItems)
        saveConnectors(context, meta.id, connectors)
        saveBones(context, meta.id, bones)
        // The copy gets its own soundtrack file, so deleting either document leaves the other's.
        val soundtrack = animation.soundtrack?.let { name ->
            soundtrackFile(context, name).inputStream().use { importSoundtrack(context, it, name.substringAfterLast('.', ""), replacing = null) }
        }
        saveAnimationSettings(context, meta.id, animation.copy(soundtrack = soundtrack))
        saveImageItems(context, meta.id, imageItems)
        saveBackground(context, meta.id, backgroundColor, backgroundStyle)
        if (layers.isNotEmpty()) saveLayers(context, meta.id, layers)
        if (layerComps.isNotEmpty()) saveLayerComps(context, meta.id, layerComps)
        if (guides.isNotEmpty()) saveGuides(context, meta.id, guides)
        if (symbolInstances.isNotEmpty()) saveSymbolInstances(context, meta.id, symbolInstances)
        if (frames.isNotEmpty()) saveFrames(context, meta.id, frames)
        if (drawingScale != null) saveDrawingScale(context, meta.id, drawingScale)
        if (branches != null) writeAtomically(branchesFile(context, meta.id), branches)
        if (gameObjects.isNotEmpty()) saveGameObjects(context, meta.id, gameObjects)
        return meta
    }

    /**
     * Saves [strokes]. When the only change since the last save is strokes added at the end (the
     * usual case: something was just drawn), just those are appended to a journal instead of
     * rewriting the whole file; see [saveStrokesFile]. [withThumbnail] re-renders the library
     * thumbnail too; autosave (after every action) passes false, since rendering every stroke again each time adds up on big
     * documents - the thumbnail is refreshed when leaving the document or opening the library.
     */
    fun save(context: Context, documentId: String, strokes: List<Stroke>, withThumbnail: Boolean = true) {
        saveStrokesFile(context, documentId, strokes)
        if (withThumbnail) saveThumbnail(context, documentId, strokes)
        saveVersionIfDue(context, documentId, strokes)
        val index = readIndex(context)
        for (i in 0 until index.length()) {
            val entry = index.getJSONObject(i)
            if (entry.getString("id") == documentId) {
                entry.put("lastModified", System.currentTimeMillis())
            }
        }
        writeIndex(context, index)
    }

    /**
     * Reads a document's strokes. With [first] and [onFirst], the strokes in that area are read
     * first and passed to [onFirst] (on the calling thread) to show while the rest loads; see
     * [StrokeChunkFile].
     */
    fun load(
        context: Context,
        documentId: String,
        first: StrokeChunkFile.Area? = null,
        onFirst: ((List<Stroke>) -> Unit)? = null,
    ): List<Stroke> = synchronized(this) {
        // Whatever was remembered from an earlier load or save no longer describes these objects.
        lastSaved.remove(documentId)
        val file = strokesFile(context, documentId)
        val base = readBinary(file) { input ->
            StrokeChunkFile.read(input, first, onFirst) { DataInputStream(BufferedInputStream(AtomicFile(file).openRead())) }
        }
            // Documents saved before the binary format; converted by the next save.
            ?: readStored(legacyStrokesFile(context, documentId)) { parseStrokes(it as JSONArray) }
            ?: emptyList()
        val journal = readJournal(journalFile(context, documentId))
        val strokes = base + journal.strokes
        if (journal.damagedTail) {
            // A crash mid-append left a partial block; rewrite cleanly so later appends aren't
            // stranded behind it.
            saveStrokesFile(context, documentId, strokes)
        }
        lastSaved[documentId] = SavedStrokes(strokes, journal.blocks)
        strokes
    }

    fun listPins(context: Context, documentId: String): List<PinMeta> {
        val file = pinsFile(context, documentId)
        val root = readStored(file) { it as JSONArray } ?: return emptyList()
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
        writeStored(pinsFile(context, documentId), root)
    }

    fun saveConnectors(context: Context, documentId: String, connectors: List<Connector>) {
        val root = JSONArray()
        for (c in connectors) root.put(JSONObject().put("id", c.id).put("from", c.fromId).put("to", c.toId))
        writeStored(connectorsFile(context, documentId), root)
    }

    fun loadConnectors(context: Context, documentId: String): List<Connector> {
        val root = readStored(connectorsFile(context, documentId)) { it as JSONArray } ?: return emptyList()
        return (0 until root.length()).map { i ->
            val entry = root.getJSONObject(i)
            Connector(entry.getString("id"), entry.getString("from"), entry.getString("to"))
        }
    }

    /** Saves every frame's skeleton bones (roadmap item 83). */
    fun saveBones(context: Context, documentId: String, bones: List<Bone>) {
        val root = JSONArray()
        for (b in bones) {
            root.put(
                JSONObject().put("id", b.id).put("parent", b.parentId ?: JSONObject.NULL).put("frame", b.animFrame)
                    .put("hx", b.head.x).put("hy", b.head.y).put("tx", b.tail.x).put("ty", b.tail.y)
                    .apply {
                        if (b.minTurn != null && b.maxTurn != null) put("minTurn", b.minTurn).put("maxTurn", b.maxTurn)
                        if (b.dynamic) put("dynamic", true)
                    }
            )
        }
        writeStored(bonesFile(context, documentId), root)
    }

    fun loadBones(context: Context, documentId: String): List<Bone> {
        val root = readStored(bonesFile(context, documentId)) { it as JSONArray } ?: return emptyList()
        return (0 until root.length()).mapNotNull { i ->
            val e = root.optJSONObject(i) ?: return@mapNotNull null
            runCatching {
                Bone(
                    e.getString("id"), if (e.isNull("parent")) null else e.getString("parent"),
                    WorldPoint(e.getDouble("hx"), e.getDouble("hy")), WorldPoint(e.getDouble("tx"), e.getDouble("ty")), e.optInt("frame", 0),
                    minTurn = if (e.has("minTurn")) e.getDouble("minTurn") else null,
                    maxTurn = if (e.has("maxTurn")) e.getDouble("maxTurn") else null,
                    dynamic = e.optBoolean("dynamic", false),
                )
            }.getOrNull()
        }
    }

    /**
     * Copies an audio file into app storage as a soundtrack (roadmap item 88) and returns the
     * stored file's name, or null if it couldn't be read. The previous soundtrack [replacing], if
     * any, is deleted.
     */
    fun importSoundtrack(context: Context, input: java.io.InputStream, extension: String, replacing: String?): String? {
        val name = "soundtrack_${java.util.UUID.randomUUID()}.${extension.filter { it.isLetterOrDigit() }.take(8).ifEmpty { "audio" }}"
        val file = soundtrackFile(context, name)
        return runCatching {
            file.outputStream().use { input.copyTo(it) }
            replacing?.let { deleteSoundtrack(context, it) }
            name
        }.getOrElse {
            file.delete()
            null
        }
    }

    fun deleteSoundtrack(context: Context, name: String) {
        soundtrackFile(context, name).delete()
    }

    /** Where soundtrack [name] is stored; names are only ever ones [importSoundtrack] made. */
    fun soundtrackFile(context: Context, name: String): File = File(File(context.filesDir, "soundtracks").apply { mkdirs() }, File(name).name)

    /** Saves the playback speed, X-sheet holds, mouth chart and soundtrack (roadmap item 88). */
    fun saveAnimationSettings(context: Context, documentId: String, settings: AnimationSettings) {
        val holds = JSONObject()
        for ((frame, hold) in settings.holds) holds.put(frame.toString(), hold)
        val mouths = JSONObject()
        for ((frame, mouth) in settings.mouths) mouths.put(frame.toString(), mouth.name)
        writeStored(
            animationFile(context, documentId),
            JSONObject().put("fps", settings.fps).put("holds", holds).put("mouths", mouths).apply {
                settings.soundtrack?.let { put("soundtrack", it) }
                if (settings.tags.isNotEmpty()) {
                    put("tags", JSONArray().apply {
                        for (tag in settings.tags) put(JSONObject().put("name", tag.name).put("from", tag.from).put("to", tag.to).put("color", tag.color).put("direction", tag.direction.name))
                    })
                }
            },
        )
    }

    /** The saved timing, or the defaults (12 fps, no holds) for a document from before it was saved. */
    fun loadAnimationSettings(context: Context, documentId: String): AnimationSettings {
        val root = readStored(animationFile(context, documentId)) { it as JSONObject } ?: return AnimationSettings()
        val holds = root.optJSONObject("holds")
        val mouths = root.optJSONObject("mouths")
        return AnimationSettings(
            root.optInt("fps", 12).coerceIn(1, 60),
            holds?.keys()?.asSequence()?.mapNotNull { key -> key.toIntOrNull()?.let { it to holds.optInt(key, 1).coerceIn(1, CanvasState.MAX_FRAME_HOLD) } }?.toMap().orEmpty(),
            mouths?.keys()?.asSequence()?.mapNotNull { key ->
                val frame = key.toIntOrNull() ?: return@mapNotNull null
                MouthShape.entries.firstOrNull { it.name == mouths.optString(key) }?.let { frame to it }
            }?.toMap().orEmpty(),
            root.optString("soundtrack").ifEmpty { null }?.takeIf { soundtrackFile(context, it).exists() },
            root.optJSONArray("tags")?.let { tags ->
                (0 until tags.length()).mapNotNull { i ->
                    val t = tags.optJSONObject(i) ?: return@mapNotNull null
                    val from = t.optInt("from", -1)
                    val to = t.optInt("to", -1)
                    if (from < 0 || to < from) return@mapNotNull null
                    FrameTag(t.optString("name"), from, to, t.optInt("color", TAG_COLORS[0]), enumOrDefault(t.optString("direction"), TagDirection.FORWARD))
                }
            }.orEmpty(),
        )
    }

    fun loadTextItems(context: Context, documentId: String): List<TextItem> {
        val file = textItemsFile(context, documentId)
        val root = readStored(file) { it as JSONArray } ?: return emptyList()
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
                    // Labels saved before text styling are plain.
                    bold = entry.optBoolean("bold", false),
                    italic = entry.optBoolean("italic", false),
                    sticky = entry.optBoolean("sticky", false),
                    // Labels saved before fonts, alignment and text boxes: sans, left, no box.
                    font = enumOrDefault(entry.optString("font"), TextFont.SANS),
                    alignment = enumOrDefault(entry.optString("align"), TextAlignment.START),
                    boxWidthWorld = if (entry.has("boxWidth")) entry.getDouble("boxWidth") else null,
                    outlined = entry.optBoolean("outlined", false),
                    features = entry.optJSONArray("features")?.let { a -> (0 until a.length()).mapNotNull { i -> OpenTypeFeature.entries.firstOrNull { it.name == a.optString(i) } }.toSet() }.orEmpty(),
                    path = entry.optJSONArray("path")?.let { a -> (0 until a.length() / 2).map { WorldPoint(a.getDouble(2 * it), a.getDouble(2 * it + 1)) } },
                    link = when {
                        entry.has("linkPin") -> LinkTarget.Pin(entry.getString("linkPin"))
                        entry.has("linkDoc") -> LinkTarget.Document(entry.getString("linkDoc"))
                        else -> null
                    },
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
                    put("bold", item.bold)
                    put("italic", item.italic)
                    put("sticky", item.sticky)
                    put("font", item.font.name)
                    put("align", item.alignment.name)
                    item.boxWidthWorld?.let { put("boxWidth", it) }
                    if (item.outlined) put("outlined", true)
                    if (item.features.isNotEmpty()) put("features", JSONArray(item.features.map { it.name }))
                    item.path?.let { path -> put("path", JSONArray().apply { path.forEach { put(it.x); put(it.y) } }) }
                    when (val link = item.link) {
                        is LinkTarget.Pin -> put("linkPin", link.pinId)
                        is LinkTarget.Document -> put("linkDoc", link.documentId)
                        null -> {}
                    }
                }
            )
        }
        writeStored(textItemsFile(context, documentId), root)
    }

    fun loadImageItems(context: Context, documentId: String): List<ImageItem> {
        val file = imageItemsFile(context, documentId)
        val root = readStored(file) { it as JSONArray } ?: return emptyList()
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
                    videoFileName = entry.optString("video").takeIf { it.isNotEmpty() },
                    modelFileName = entry.optString("model").takeIf { it.isNotEmpty() },
                )
            )
        }
        return items
    }

    /** A document's page background, or the app-wide default if it never set one. */
    fun loadBackground(context: Context, documentId: String): Pair<Color, BackgroundStyle> {
        val file = backgroundFile(context, documentId)
        val root = readStored(file) { it as JSONObject } ?: return DEFAULT_BACKGROUND_COLOR to BackgroundStyle.PLAIN
        val color = Color(root.getInt("color"))
        val style = enumOrDefault(root.optString("style"), BackgroundStyle.PLAIN)
        return color to style
    }

    /** Where a document was last viewed: centre and zoom, or null if never saved. */
    fun loadView(context: Context, documentId: String): Pair<WorldPoint, Double>? {
        val root = readStored(viewFile(context, documentId)) { it as JSONObject } ?: return null
        val scale = root.optDouble("scale", Double.NaN)
        if (!(scale > 0.0)) return null
        return WorldPoint(root.getDouble("x"), root.getDouble("y")) to scale
    }

    fun saveView(context: Context, documentId: String, center: WorldPoint, scale: Double) {
        writeStored(viewFile(context, documentId), JSONObject().put("x", center.x).put("y", center.y).put("scale", scale))
    }

    fun saveBackground(context: Context, documentId: String, color: Color, style: BackgroundStyle) {
        val root = JSONObject().apply {
            put("color", color.toArgb())
            put("style", style.name)
        }
        writeStored(backgroundFile(context, documentId), root)
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
                    item.videoFileName?.let { put("video", it) }
                    item.modelFileName?.let { put("model", it) }
                }
            )
        }
        writeStored(imageItemsFile(context, documentId), root)
    }

    /**
     * Persists undo/redo snapshots (oldest first) so history survives an app restart. Only the
     * newest [PERSISTED_HISTORY_STEPS] of each are kept on disk: every step is a full copy of
     * the document, so persisting all 64 in-memory steps would make this file up to 64x the
     * size of the document itself.
     */
    fun saveHistory(context: Context, documentId: String, undo: List<List<Stroke>>, redo: List<List<Stroke>>) {
        // Layout: int undo-step count, that many stroke blocks, then the same for redo.
        writeBinary(historyFile(context, documentId)) { out ->
            for (steps in listOf(withinBudget(undo.takeLast(PERSISTED_HISTORY_STEPS)), withinBudget(redo.takeLast(PERSISTED_HISTORY_STEPS)))) {
                out.writeInt(steps.size)
                steps.forEach { StrokeCodec.write(out, it) }
            }
        }
        legacyHistoryFile(context, documentId).delete()
    }

    /**
     * The newest [steps] whose strokes add up to at most [PERSISTED_HISTORY_STROKES]. Each step is
     * a whole stroke list, so 16 steps of a million-stroke document would write gigabytes on
     * every save; a big document keeps fewer steps across restarts (none past the budget)
     * while its in-memory undo is unaffected. Sizes are read without materializing the steps.
     */
    private fun withinBudget(steps: List<List<Stroke>>): List<List<Stroke>> {
        var total = 0L
        var first = steps.size
        while (first > 0 && total + steps[first - 1].size <= PERSISTED_HISTORY_STROKES) {
            total += steps[first - 1].size
            first--
        }
        return steps.subList(first, steps.size)
    }

    /** Undo and redo snapshots saved by [saveHistory], or empty lists if there are none. */
    fun loadHistory(context: Context, documentId: String): Pair<List<List<Stroke>>, List<List<Stroke>>> {
        readBinary(historyFile(context, documentId)) { input ->
            fun steps() = List(input.readInt().coerceAtLeast(0)) { StrokeCodec.read(input) }
            val undo = steps()
            undo to steps()
        }?.let { return it }
        val root = readStored(legacyHistoryFile(context, documentId)) { it as JSONObject } ?: return emptyList<List<Stroke>>() to emptyList()
        fun steps(key: String): List<List<Stroke>> {
            val array = root.optJSONArray(key) ?: return emptyList()
            return (0 until array.length()).map { parseStrokes(array.getJSONArray(it)) }
        }
        return steps("undo") to steps("redo")
    }

    /** Saved version timestamps for [documentId], newest first. */
    fun listVersions(context: Context, documentId: String): List<Long> =
        versionsDir(context, documentId).listFiles().orEmpty()
            .mapNotNull { it.name.removeSuffix(VERSION_SUFFIX).removeSuffix(LEGACY_VERSION_SUFFIX).toLongOrNull() }
            .distinct()
            .sortedDescending()

    fun loadVersion(context: Context, documentId: String, timestamp: Long): List<Stroke> {
        val dir = versionsDir(context, documentId)
        readBinary(File(dir, "$timestamp$VERSION_SUFFIX")) { StrokeCodec.read(it) }?.let { return it }
        // Versions saved before the binary format stay readable as they age out.
        return readStored(File(dir, "$timestamp$LEGACY_VERSION_SUFFIX")) { parseStrokes(it as JSONArray) } ?: emptyList()
    }

    /**
     * Keeps a timeline of past states: on save, a version is written if the newest one is at
     * least [VERSION_INTERVAL_MILLIS] old, and only the newest [MAX_VERSIONS] are kept. Unlike
     * undo history this is time-based, so it reaches back hours or days rather than a fixed
     * number of edits. Versions cover strokes only; text and images aren't versioned yet.
     */
    private fun saveVersionIfDue(context: Context, documentId: String, strokes: List<Stroke>) {
        if (strokes.isEmpty()) return
        val now = System.currentTimeMillis()
        val existing = listVersions(context, documentId)
        if (existing.isNotEmpty() && now - existing.first() < VERSION_INTERVAL_MILLIS) return
        val dir = versionsDir(context, documentId).apply { mkdirs() }
        writeBinary(File(dir, "$now$VERSION_SUFFIX")) { StrokeCodec.write(it, strokes) }
        for (old in existing.drop(MAX_VERSIONS - 1)) {
            File(dir, "$old$VERSION_SUFFIX").delete()
            File(dir, "$old$LEGACY_VERSION_SUFFIX").delete()
        }
    }

    /** The user's saved colour palette, shared by all documents. */
    fun loadSavedColors(context: Context): List<Color> {
        val root = readStored(File(context.filesDir, PALETTE_FILE_NAME)) { it as JSONArray } ?: return emptyList()
        return (0 until root.length()).map { Color(root.getInt(it)) }
    }

    fun saveSavedColors(context: Context, colors: List<Color>) {
        writeStored(File(context.filesDir, PALETTE_FILE_NAME), JSONArray().apply { colors.forEach { put(it.toArgb()) } })
    }

    fun saveFrames(context: Context, documentId: String, frames: List<Frame>) {
        val root = JSONArray()
        for (frame in frames) {
            root.put(JSONObject().apply {
                put("id", frame.id)
                put("name", frame.name)
                put("minX", frame.min.x)
                put("minY", frame.min.y)
                put("maxX", frame.max.x)
                put("maxY", frame.max.y)
                frame.layoutGrid?.let { grid ->
                    put("grid", JSONObject().put("columns", grid.columns).put("rows", grid.rows).put("gutter", grid.gutter).put("margin", grid.margin))
                }
            })
        }
        writeStored(framesFile(context, documentId), root)
    }

    fun loadFrames(context: Context, documentId: String): List<Frame> {
        val root = readStored(framesFile(context, documentId)) { it as JSONArray } ?: return emptyList()
        return (0 until root.length()).map { i ->
            val entry = root.getJSONObject(i)
            Frame(
                id = entry.getString("id"),
                name = entry.getString("name"),
                min = WorldPoint(entry.getDouble("minX"), entry.getDouble("minY")),
                max = WorldPoint(entry.getDouble("maxX"), entry.getDouble("maxY")),
                layoutGrid = entry.optJSONObject("grid")?.let { grid ->
                    LayoutGrid(grid.getInt("columns"), grid.getInt("rows"), grid.getDouble("gutter"), grid.getDouble("margin"))
                },
            )
        }
    }

    fun saveLayers(context: Context, documentId: String, layers: List<Layer>) {
        val root = JSONArray()
        for (layer in layers) {
            root.put(JSONObject().apply {
                put("id", layer.id)
                put("name", layer.name)
                put("visible", layer.visible)
                put("locked", layer.locked)
                put("opacity", layer.opacity.toDouble())
                put("reference", layer.reference)
                layer.skin?.let { put("skin", it) }
                if (layer.raster) put("raster", true).put("depth", layer.depth)
                if (layer.masked) put("masked", true)
                if (layer.clipped) put("clipped", true)
                if (layer.alphaLocked) put("alphaLocked", true)
                if (layer.blend != LayerBlend.NORMAL) put("blend", layer.blend.name)
                layer.adjustment?.let { put("adjustment", adjustmentToJson(it)) }
                layer.fileSource?.let { put("file", it) }
                if (layer.hdr) put("hdr", true)
            })
        }
        writeStored(layersFile(context, documentId), root)
    }

    /** Saves the document's linked symbol instances (roadmap item 100). */
    fun saveSymbolInstances(context: Context, documentId: String, instances: List<SymbolInstance>) {
        val root = JSONArray()
        for (i in instances) {
            root.put(JSONObject().put("group", i.groupId).put("symbol", i.symbolId).put("revision", i.revision).put("masterWidth", i.masterWidth))
        }
        writeStored(symbolInstancesFile(context, documentId), root)
    }

    fun loadSymbolInstances(context: Context, documentId: String): List<SymbolInstance> {
        val root = readStored(symbolInstancesFile(context, documentId)) { it as JSONArray } ?: return emptyList()
        return (0 until root.length()).map { i ->
            val e = root.getJSONObject(i)
            SymbolInstance(e.getString("group"), e.getString("symbol"), e.optLong("revision"), e.getDouble("masterWidth"))
        }
    }

    /** Saves the document's game objects (roadmap item 155). */
    fun saveGameObjects(context: Context, documentId: String, objects: List<GameObject>) {
        if (objects.isEmpty()) gameObjectsFile(context, documentId).delete() else writeStored(gameObjectsFile(context, documentId), GameObjectsJson.write(objects))
    }

    fun loadGameObjects(context: Context, documentId: String): List<GameObject> =
        readStored(gameObjectsFile(context, documentId)) { it as JSONArray }?.let(GameObjectsJson::read).orEmpty()

    /**
     * Saves the document's animation branches (roadmap item 153) and which one is current. The
     * current branch's own latest content is the document itself, so its stored copy may lag.
     */
    fun saveBranches(context: Context, documentId: String, branches: List<AnimationBranch>, currentId: String) {
        writeAtomically(branchesFile(context, documentId), AnimationBranchFile.write(branches, currentId))
    }

    /** The saved branches and the current one's id, or nothing for a document without branches (or a damaged file). */
    fun loadBranches(context: Context, documentId: String): Pair<List<AnimationBranch>, String?> {
        val file = branchesFile(context, documentId)
        if (!file.exists()) return emptyList<AnimationBranch>() to null
        return runCatching { AnimationBranchFile.read(file.readBytes()) }.getOrNull()?.let { (b, c) -> b to c } ?: (emptyList<AnimationBranch>() to null)
    }

    /** Saves the document's drawing scale (roadmap item 143); null removes it (world units). */
    fun saveDrawingScale(context: Context, documentId: String, scale: DrawingScale?) {
        val file = drawingScaleFile(context, documentId)
        if (scale == null) {
            file.delete()
            return
        }
        writeStored(file, JSONObject().put("unit", scale.unit.name).put("unitsPerWorld", scale.unitsPerWorld))
    }

    fun loadDrawingScale(context: Context, documentId: String): DrawingScale? {
        val root = readStored(drawingScaleFile(context, documentId)) { it as JSONObject } ?: return null
        val unit = MeasureUnit.entries.firstOrNull { it.name == root.optString("unit") } ?: return null
        val factor = root.optDouble("unitsPerWorld", Double.NaN)
        return if (factor > 0 && factor.isFinite()) DrawingScale(unit, factor) else null
    }

    /** Saves the document's ruler guides (roadmap item 99). */
    fun saveGuides(context: Context, documentId: String, guides: List<Guide>) {
        val root = JSONArray()
        for (g in guides) root.put(JSONObject().put("id", g.id).put("vertical", g.vertical).put("position", g.position))
        writeStored(guidesFile(context, documentId), root)
    }

    fun loadGuides(context: Context, documentId: String): List<Guide> {
        val root = readStored(guidesFile(context, documentId)) { it as JSONArray } ?: return emptyList()
        return (0 until root.length()).map { i ->
            val e = root.getJSONObject(i)
            Guide(e.getString("id"), e.getBoolean("vertical"), e.getDouble("position"))
        }
    }

    /** Saves the document's usage statistics (roadmap item 132). */
    fun saveStats(context: Context, documentId: String, stats: DocumentStats) {
        writeStored(
            statsFile(context, documentId),
            JSONObject()
                .put("activeMillis", stats.activeMillis)
                .put("strokesDrawn", stats.strokesDrawn)
                .put("erases", stats.erases)
                .put("firstAction", stats.firstAction)
                .put("lastAction", stats.lastAction),
        )
    }

    fun loadStats(context: Context, documentId: String): DocumentStats {
        val root = readStored(statsFile(context, documentId)) { it as JSONObject } ?: return DocumentStats()
        return DocumentStats(
            activeMillis = root.optLong("activeMillis"),
            strokesDrawn = root.optLong("strokesDrawn"),
            erases = root.optLong("erases"),
            firstAction = root.optLong("firstAction"),
            lastAction = root.optLong("lastAction"),
        )
    }

    /** Saves the document's layer comps (roadmap item 123). */
    fun saveLayerComps(context: Context, documentId: String, comps: List<LayerComp>) {
        val root = JSONArray()
        for (comp in comps) {
            val states = JSONObject()
            for ((layerId, state) in comp.states) {
                states.put(layerId, JSONObject().apply {
                    put("visible", state.visible)
                    put("opacity", state.opacity.toDouble())
                    if (state.blend != LayerBlend.NORMAL) put("blend", state.blend.name)
                })
            }
            root.put(JSONObject().put("id", comp.id).put("name", comp.name).put("layers", states))
        }
        writeStored(layerCompsFile(context, documentId), root)
    }

    fun loadLayerComps(context: Context, documentId: String): List<LayerComp> {
        val root = readStored(layerCompsFile(context, documentId)) { it as JSONArray } ?: return emptyList()
        return (0 until root.length()).map { i ->
            val entry = root.getJSONObject(i)
            val states = entry.getJSONObject("layers")
            LayerComp(
                entry.getString("id"),
                entry.getString("name"),
                states.keys().asSequence().associateWith { layerId ->
                    val state = states.getJSONObject(layerId)
                    LayerCompState(
                        state.optBoolean("visible", true),
                        state.optDouble("opacity", 1.0).toFloat(),
                        LayerBlend.entries.firstOrNull { it.name == state.optString("blend") } ?: LayerBlend.NORMAL,
                    )
                },
            )
        }
    }

    /** The document's layers, bottom first; empty for a document from before layers (it gets the default layer). */
    fun loadLayers(context: Context, documentId: String): List<Layer> {
        val root = readStored(layersFile(context, documentId)) { it as JSONArray } ?: return emptyList()
        return (0 until root.length()).map { i ->
            val entry = root.getJSONObject(i)
            Layer(
                id = entry.getString("id"),
                name = entry.getString("name"),
                visible = entry.optBoolean("visible", true),
                locked = entry.optBoolean("locked", false),
                opacity = entry.optDouble("opacity", 1.0).toFloat(),
                reference = entry.optBoolean("reference", false),
                skin = entry.optString("skin").ifEmpty { null },
                raster = entry.optBoolean("raster", false),
                depth = if (entry.optInt("depth", 8) == 16) 16 else 8,
                masked = entry.optBoolean("masked", false),
                clipped = entry.optBoolean("clipped", false),
                alphaLocked = entry.optBoolean("alphaLocked", false),
                blend = LayerBlend.entries.firstOrNull { it.name == entry.optString("blend") } ?: LayerBlend.NORMAL,
                adjustment = entry.optJSONObject("adjustment")?.let(::adjustmentFromJson),
                fileSource = entry.optString("file").ifEmpty { null },
                hdr = entry.optBoolean("hdr", false),
            )
        }
    }

    /**
     * Raster layers' pixels (roadmap item 89) live in `raster_<doc>/<layer>/`: `layer.meta`
     * (depth, pixel size, origin) and one compressed file per painted tile, `<x>_<y>.tile`. Saves
     * are incremental: only the tiles [tiles] lists are written (or deleted, for null).
     */
    fun saveRasterTiles(context: Context, documentId: String, layerId: String, surface: RasterSurface, tiles: Map<Long, RasterTile?>) {
        val dir = rasterLayerDir(context, documentId, layerId).apply { mkdirs() }
        writeBinary(File(dir, RASTER_META)) { out ->
            out.writeInt(RASTER_META_VERSION)
            out.writeInt(surface.depth)
            out.writeDouble(surface.pixelWorld)
            out.writeDouble(surface.originX)
            out.writeDouble(surface.originY)
        }
        for ((key, tile) in tiles) {
            val file = File(dir, "${RasterSurface.tileX(key)}_${RasterSurface.tileY(key)}$RASTER_TILE_SUFFIX")
            if (tile == null) file.delete() else writeBinary(file) { RasterSurface.writeTile(it, tile, surface.depth) }
        }
    }

    fun deleteRasterLayer(context: Context, documentId: String, layerId: String) {
        rasterLayerDir(context, documentId, layerId).deleteRecursively()
    }

    /** Every saved raster layer of the document whose id is in [layerIds], by layer id. */
    fun loadRasters(context: Context, documentId: String, layerIds: Collection<String>): Map<String, RasterSurface> =
        layerIds.mapNotNull { id ->
            val dir = rasterLayerDir(context, documentId, id)
            val surface = readBinary(File(dir, RASTER_META)) { input ->
                if (input.readInt() > RASTER_META_VERSION) throw IOException("Newer raster layer format")
                RasterSurface(input.readInt(), input.readDouble(), input.readDouble(), input.readDouble())
            } ?: return@mapNotNull null
            for (file in dir.listFiles().orEmpty()) {
                if (!file.name.endsWith(RASTER_TILE_SUFFIX)) continue
                val (x, y) = file.name.removeSuffix(RASTER_TILE_SUFFIX).split('_').mapNotNull { it.toIntOrNull() }.takeIf { it.size == 2 } ?: continue
                readBinary(file) { RasterSurface.readTile(it, surface.depth) }?.let { surface.tiles[RasterSurface.tileKey(x, y)] = it }
            }
            id to surface
        }.toMap()

    /** Every stored raster file of the document, by its path under the raster folder ("<layer>/<file>"), for bundles. */
    internal fun rasterFiles(context: Context, documentId: String): Map<String, File> =
        rasterDir(context, documentId).listFiles().orEmpty().filter { it.isDirectory }.flatMap { layer ->
            layer.listFiles().orEmpty().filter { it.isFile }.map { "${layer.name}/${it.name}" to it }
        }.toMap()

    /**
     * Where a bundled raster file "<layer>/<file>" goes for document [documentId], or null for a
     * path that isn't one Fern writes (so a crafted bundle can't write elsewhere).
     */
    internal fun rasterTarget(context: Context, documentId: String, path: String): File? {
        val parts = path.split('/')
        if (parts.size != 2 || !parts[0].matches(Regex("[A-Za-z0-9-]+"))) return null
        if (parts[1] != RASTER_META && !parts[1].matches(Regex("-?[0-9]+_-?[0-9]+\\$RASTER_TILE_SUFFIX"))) return null
        return File(rasterLayerDir(context, documentId, parts[0]).apply { mkdirs() }, parts[1])
    }

    private fun rasterDir(context: Context, documentId: String) = File(context.filesDir, "raster_$documentId")

    private fun rasterLayerDir(context: Context, documentId: String, layerId: String) = File(rasterDir(context, documentId), File(layerId).name)

    private const val RASTER_META = "layer.meta"
    private const val RASTER_META_VERSION = 1
    private const val RASTER_TILE_SUFFIX = ".tile"

    /** The document library preview, regenerated on every save; missing for a never-drawn-on document. */
    fun loadThumbnail(context: Context, documentId: String): Bitmap? {
        val file = thumbnailFile(context, documentId)
        return if (file.exists()) BitmapFactory.decodeFile(file.path) else null
    }

    fun saveThumbnail(context: Context, documentId: String, strokes: List<Stroke>) {
        val png = renderThumbnailPng(strokes)
        if (png == null) thumbnailFile(context, documentId).delete()
        else writeAtomically(thumbnailFile(context, documentId), png)
    }

    /** Directory holding decoded copies of every inserted image, shared across all documents and keyed by id-derived file name. */
    fun imagesDir(context: Context): File {
        val dir = File(context.filesDir, IMAGES_DIR_NAME)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun imageFile(context: Context, fileName: String): File = File(imagesDir(context), fileName)

    /** Directory holding embedded videos (roadmap item 107), shared and immutable like [imagesDir]. */
    fun videosDir(context: Context): File = File(context.filesDir, VIDEOS_DIR_NAME).also { if (!it.exists()) it.mkdirs() }

    fun videoFile(context: Context, fileName: String): File = File(videosDir(context), fileName)

    /** Directory holding 3D models (roadmap item 108), shared and immutable like [imagesDir]. */
    fun modelsDir(context: Context): File = File(context.filesDir, MODELS_DIR_NAME).also { if (!it.exists()) it.mkdirs() }

    fun modelFile(context: Context, fileName: String): File = File(modelsDir(context), fileName)

    /**
     * One-time upgrade from the pre-multi-document single `canvas.json` file: wraps its
     * content as a document named "My Canvas" so existing saved work isn't lost, then removes
     * the legacy file so this only runs once.
     */
    private fun migrateLegacyIfNeeded(context: Context) {
        val indexFile = File(context.filesDir, INDEX_FILE_NAME)
        val legacyFile = File(context.filesDir, LEGACY_FILE_NAME)
        if (indexFile.exists() || !legacyFile.exists()) return
        val strokes = readStored(legacyFile) { parseStrokes(it as JSONArray) } ?: emptyList()
        val meta = DocumentMeta(UUID.randomUUID().toString(), DEFAULT_DOCUMENT_NAME, System.currentTimeMillis())
        saveStrokesFile(context, meta.id, strokes)
        writeIndex(context, JSONArray().put(metaToJson(meta)))
        legacyFile.delete()
    }

    private fun readIndex(context: Context): JSONArray {
        val file = File(context.filesDir, INDEX_FILE_NAME)
        return readStored(file) { it as JSONArray } ?: JSONArray()
    }

    private fun writeIndex(context: Context, index: JSONArray) {
        writeStored(File(context.filesDir, INDEX_FILE_NAME), index)
    }

    /**
     * Writes [bytes] to [file] crash-safely. The plain `writeText` this replaces truncated the
     * file first, so a process kill mid-write left half a JSON document that then failed to
     * parse on every launch. [AtomicFile] writes to a side file, fsyncs it, and only then
     * renames it over the original, so a reader sees either the old or the new content.
     */
    private fun writeStored(file: File, payload: Any) {
        val envelope = JSONObject().apply {
            put(KEY_FORMAT_VERSION, FORMAT_VERSION)
            put(KEY_DATA, payload)
        }
        writeAtomically(file, envelope.toString().toByteArray())
    }

    /**
     * Unwraps a file's envelope and upgrades its payload to [FORMAT_VERSION]. A file written by a
     * newer app version throws, so [readStored] quarantines it instead of letting this older
     * build silently overwrite data it doesn't understand.
     */
    private fun migrate(raw: Any): Any {
        val version = if (raw is JSONObject && raw.has(KEY_FORMAT_VERSION)) raw.getInt(KEY_FORMAT_VERSION) else 0
        require(version <= FORMAT_VERSION) { "File format $version is newer than supported $FORMAT_VERSION" }
        // Version 0 -> 1 only added the envelope; the payload shape is unchanged.
        return if (version == 0) raw else (raw as JSONObject).get(KEY_DATA)
    }

    private fun writeAtomically(file: File, bytes: ByteArray) {
        val atomic = AtomicFile(file)
        val out = atomic.startWrite()
        try {
            out.write(bytes)
            atomic.finishWrite(out)
        } catch (e: Exception) {
            atomic.failWrite(out)
            throw e
        }
    }

    /**
     * Reads [file], unwraps and migrates its envelope via [migrate], and parses the payload, or returns null if it doesn't exist. A file that can't be parsed
     * (e.g. damaged by a crash before atomic writes existed) is renamed to `<name>.corrupt-<time>`
     * rather than deleted, so the next autosave can't overwrite the only copy of that data, and
     * the caller falls back to its empty default instead of crashing on every launch.
     */
    private fun <T> readStored(file: File, parse: (Any) -> T): T? {
        val text = try {
            AtomicFile(file).readFully().decodeToString()
        } catch (e: FileNotFoundException) {
            return null
        }
        return try {
            parse(migrate(JSONTokener(text).nextValue()))
        } catch (e: Exception) {
            val quarantined = File(file.parentFile, "${file.name}.corrupt-${System.currentTimeMillis()}")
            Log.e("CanvasStorage", "Unreadable ${file.name}; moved to ${quarantined.name}", e)
            file.renameTo(quarantined)
            null
        }
    }

    private fun metaToJson(meta: DocumentMeta): JSONObject = JSONObject().apply {
        put("id", meta.id)
        put("name", meta.name)
        put("lastModified", meta.lastModified)
        put("favorite", meta.favorite)
        meta.trashedAt?.let { put("trashedAt", it) }
        meta.folder?.let { put("folder", it) }
    }

    // favorite/trashedAt/folder are optional: index entries written before they existed lack them.
    private fun metaFromJson(entry: JSONObject) = DocumentMeta(
        id = entry.getString("id"),
        name = entry.getString("name"),
        lastModified = entry.getLong("lastModified"),
        favorite = entry.optBoolean("favorite", false),
        trashedAt = if (entry.has("trashedAt")) entry.getLong("trashedAt") else null,
        folder = if (entry.has("folder")) entry.getString("folder") else null,
    )

    /**
     * A document's portable files by kind (the key is the file name used inside a `.fern`
     * bundle; see DocumentBundle). Excludes what can be regenerated or is device-local:
     * thumbnail, undo history and version snapshots.
     */
    /** Folds the append journal into the strokes file, so [portableFiles]' strokes entry is complete. */
    internal fun compactStrokes(context: Context, documentId: String): Unit = synchronized(this) {
        if (!journalFile(context, documentId).exists()) return@synchronized
        val strokes = load(context, documentId)
        lastSaved.remove(documentId)
        saveStrokesFile(context, documentId, strokes)
    }

    internal fun portableFiles(context: Context, documentId: String): Map<String, File> = mapOf(
        BUNDLE_STROKES_ENTRY to strokesFile(context, documentId),
        // Bundles exported before the binary format carry this instead; load() still reads it.
        LEGACY_BUNDLE_STROKES_ENTRY to legacyStrokesFile(context, documentId),
        "pins.json" to pinsFile(context, documentId),
        "text.json" to textItemsFile(context, documentId),
        "connectors.json" to connectorsFile(context, documentId),
        "bones.json" to bonesFile(context, documentId),
        "animation.json" to animationFile(context, documentId),
        "images.json" to imageItemsFile(context, documentId),
        "background.json" to backgroundFile(context, documentId),
        "layers.json" to layersFile(context, documentId),
        "layercomps.json" to layerCompsFile(context, documentId),
        "stats.json" to statsFile(context, documentId),
        "guides.json" to guidesFile(context, documentId),
        "symbols.json" to symbolInstancesFile(context, documentId),
        "frames.json" to framesFile(context, documentId),
        "scale.json" to drawingScaleFile(context, documentId),
        "branches.bin" to branchesFile(context, documentId),
        "objects.json" to gameObjectsFile(context, documentId),
    )

    /** Adds an index entry for files already written under [id] (used by bundle import). */
    internal fun registerDocument(context: Context, id: String, name: String): DocumentMeta {
        val meta = DocumentMeta(id, name, System.currentTimeMillis())
        val index = readIndex(context)
        index.put(metaToJson(meta))
        writeIndex(context, index)
        return meta
    }

    private fun strokesFile(context: Context, documentId: String) = File(context.filesDir, "strokes_$documentId.bin")

    /** Strokes appended since [strokesFile] was last written in full; see [saveStrokesFile]. */
    private fun journalFile(context: Context, documentId: String) = File(context.filesDir, "strokes_$documentId.journal")

    /** The JSON strokes file documents used before [StrokeCodec]; read as a fallback, deleted on the next save. */
    private fun legacyStrokesFile(context: Context, documentId: String) = File(context.filesDir, "canvas_$documentId.json")

    private fun pinsFile(context: Context, documentId: String) = File(context.filesDir, "pins_$documentId.json")

    private fun viewFile(context: Context, documentId: String) = File(context.filesDir, "view_$documentId.json")

    private fun textItemsFile(context: Context, documentId: String) = File(context.filesDir, "text_$documentId.json")

    private fun connectorsFile(context: Context, documentId: String) = File(context.filesDir, "connectors_$documentId.json")
    private fun bonesFile(context: Context, documentId: String) = File(context.filesDir, "bones_$documentId.json")
    private fun animationFile(context: Context, documentId: String) = File(context.filesDir, "animation_$documentId.json")

    private fun imageItemsFile(context: Context, documentId: String) = File(context.filesDir, "images_$documentId.json")

    private fun versionsDir(context: Context, documentId: String) = File(context.filesDir, "versions_$documentId")

    private fun framesFile(context: Context, documentId: String) = File(context.filesDir, "frames_$documentId.json")
    private fun drawingScaleFile(context: Context, documentId: String) = File(context.filesDir, "scale_$documentId.json")
    private fun branchesFile(context: Context, documentId: String) = File(context.filesDir, "branches_$documentId.bin")
    private fun gameObjectsFile(context: Context, documentId: String) = File(context.filesDir, "objects_$documentId.json")

    private fun layersFile(context: Context, documentId: String) = File(context.filesDir, "layers_$documentId.json")

    private fun layerCompsFile(context: Context, documentId: String) = File(context.filesDir, "layercomps_$documentId.json")

    private fun statsFile(context: Context, documentId: String) = File(context.filesDir, "stats_$documentId.json")

    private fun guidesFile(context: Context, documentId: String) = File(context.filesDir, "guides_$documentId.json")

    private fun symbolInstancesFile(context: Context, documentId: String) = File(context.filesDir, "symbolinstances_$documentId.json")

    private fun historyFile(context: Context, documentId: String) = File(context.filesDir, "history_$documentId.bin")

    private fun legacyHistoryFile(context: Context, documentId: String) = File(context.filesDir, "history_$documentId.json")

    private fun thumbnailFile(context: Context, documentId: String) = File(context.filesDir, "thumb_$documentId.png")

    private fun backgroundFile(context: Context, documentId: String) = File(context.filesDir, "background_$documentId.json")

    private fun saveStrokesFile(context: Context, documentId: String, strokes: List<Stroke>): Unit = synchronized(this) {
        val saved = lastSaved[documentId]
        val added = saved?.appendedTo(strokes)
        if (saved != null && added != null && saved.journalBlocks < MAX_JOURNAL_BLOCKS && strokesFile(context, documentId).exists()) {
            if (added.isNotEmpty()) appendJournal(journalFile(context, documentId), added)
            lastSaved[documentId] = saved.withAppended(added, saved.journalBlocks + if (added.isEmpty()) 0 else 1)
            return@synchronized
        }
        writeBinary(strokesFile(context, documentId)) { StrokeChunkFile.write(it, strokes) }
        // The full file now holds everything the journal did.
        journalFile(context, documentId).delete()
        legacyStrokesFile(context, documentId).delete()
        lastSaved[documentId] = SavedStrokes(strokes, 0)
    }

    /**
     * What was last written for a document, for incremental saves: the strokes (by identity) and
     * each one's [Stroke.revision] at that moment. Strokes are mutable - a floating-origin rebase
     * shifts every point in place, a transform rewrites them - so identity alone can't tell an
     * unchanged stroke from an edited one; the revision can.
     */
    private class SavedStrokes private constructor(
        private val strokes: List<Stroke>,
        private val revisions: IntArray,
        val journalBlocks: Int,
    ) {
        constructor(strokes: List<Stroke>, journalBlocks: Int) :
            this(strokes.toList(), IntArray(strokes.size) { strokes[it].plainRevision }, journalBlocks)

        /** This record plus [added] at the end, without re-reading what was already recorded. */
        fun withAppended(added: List<Stroke>, journalBlocks: Int): SavedStrokes {
            if (added.isEmpty()) return SavedStrokes(strokes, revisions, journalBlocks)
            val revs = revisions.copyOf(revisions.size + added.size)
            for (i in added.indices) revs[revisions.size + i] = added[i].plainRevision
            return SavedStrokes(strokes + added, revs, journalBlocks)
        }

        /** The strokes [current] adds after an untouched copy of what was saved, or null if anything saved changed. */
        fun appendedTo(current: List<Stroke>): List<Stroke>? {
            if (current.size < strokes.size) return null
            for (i in strokes.indices) {
                if (current[i] !== strokes[i] || current[i].plainRevision != revisions[i]) return null
            }
            return current.subList(strokes.size, current.size).toList()
        }
    }

    // In-memory only: after a restart the first save is a full one (load() re-seeds this too).
    private val lastSaved = HashMap<String, SavedStrokes>()

    /**
     * Appended blocks before the journal is folded into a full rewrite: 64 keeps load-time block
     * parsing trivial and the full rewrite rare (once per 64 drawing actions at most).
     */
    private const val MAX_JOURNAL_BLOCKS = 64

    /**
     * Appends one [StrokeCodec] block of newly drawn strokes, then syncs it to disk. A crash
     * mid-append leaves a partial final block, which [readJournal] drops (losing only that last
     * action) while every earlier block stays readable.
     */
    private fun appendJournal(file: File, strokes: List<Stroke>) {
        val bytes = java.io.ByteArrayOutputStream().also { StrokeCodec.write(DataOutputStream(it), strokes) }.toByteArray()
        java.io.FileOutputStream(file, true).use { stream ->
            stream.write(bytes)
            stream.fd.sync()
        }
    }

    private class Journal(val strokes: List<Stroke>, val blocks: Int, val damagedTail: Boolean)

    private fun readJournal(file: File): Journal {
        if (!file.exists()) return Journal(emptyList(), 0, false)
        val strokes = mutableListOf<Stroke>()
        var blocks = 0
        var damaged = false
        DataInputStream(BufferedInputStream(file.inputStream())).use { input ->
            while (true) {
                input.mark(1)
                if (input.read() == -1) break
                input.reset()
                try {
                    strokes += StrokeCodec.read(input)
                    blocks++
                } catch (e: Exception) {
                    Log.e("CanvasStorage", "Dropping damaged tail of ${file.name} after $blocks blocks", e)
                    damaged = true
                    break
                }
            }
        }
        return Journal(strokes, blocks, damaged)
    }

    /** [writeAtomically] for a binary payload, streamed rather than built in memory first. */
    private fun writeBinary(file: File, write: (DataOutputStream) -> Unit) {
        val atomic = AtomicFile(file)
        val stream = atomic.startWrite()
        try {
            val out = DataOutputStream(BufferedOutputStream(stream))
            write(out)
            out.flush()
            atomic.finishWrite(stream)
        } catch (e: Exception) {
            atomic.failWrite(stream)
            throw e
        }
    }

    /**
     * The binary counterpart of [readStored]: null if [file] doesn't exist, and an unreadable or
     * newer-version file is quarantined (renamed aside, never deleted) rather than crashing.
     */
    private fun <T> readBinary(file: File, read: (DataInputStream) -> T): T? {
        val stream = try {
            AtomicFile(file).openRead()
        } catch (e: FileNotFoundException) {
            return null
        }
        return try {
            DataInputStream(BufferedInputStream(stream)).use(read)
        } catch (e: Exception) {
            stream.close()
            val quarantined = File(file.parentFile, "${file.name}.corrupt-${System.currentTimeMillis()}")
            Log.e("CanvasStorage", "Unreadable ${file.name}; moved to ${quarantined.name}", e)
            file.renameTo(quarantined)
            null
        }
    }

    private inline fun <reified T : Enum<T>> enumOrDefault(name: String, default: T): T =
        enumValues<T>().firstOrNull { it.name == name } ?: default

    private fun parseStrokes(root: JSONArray): List<Stroke> {
        val strokes = mutableListOf<Stroke>()
        for (i in 0 until root.length()) {
            val strokeJson = root.getJSONObject(i)
            val color = Color(strokeJson.getInt("color"))
            val width = strokeJson.getDouble("width")
            // Older saved documents predate PenType and have no "penType" key - default to
            // MARKER (the type those strokes were always rendered as before this existed).
            // An unknown name (written by a newer Fern) falls back too, rather than failing the
            // whole file and quarantining every stroke in it.
            val penType = enumOrDefault(strokeJson.optString("penType"), PenType.MARKER)
            val filled = strokeJson.optBoolean("filled", false)
            // Older saved documents predate blend modes - default to NORMAL (how every
            // stroke was always composited before this existed).
            val blendMode = enumOrDefault(strokeJson.optString("blendMode"), StrokeBlendMode.NORMAL)
            // Strokes saved before layers existed belong to the default layer.
            val layerId = strokeJson.optString("layer", DEFAULT_LAYER_ID)
            // Strokes saved before animation belong to frame 0.
            val stroke = Stroke(color, width, penType, filled, blendMode, layerId, strokeJson.optInt("frame", 0), strokeJson.optBoolean("dashed", false), strokeJson.optBoolean("fade", false), strokeJson.optBoolean("tone", false))
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
