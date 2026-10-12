package com.andene.fern.canvas

import android.content.Context
import org.json.JSONObject
import org.json.JSONTokener
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

private const val META_ENTRY = "meta.json"
private const val IMAGES_PREFIX = "images/"
private const val RASTER_PREFIX = "raster/"
private const val SOUNDTRACK_PREFIX = "soundtrack/"
// 2: strokes travel as the binary strokes.bin (StrokeCodec) instead of canvas.json.
private const val BUNDLE_VERSION = 2

/**
 * A `.fern` bundle: one document as a zip of its portable files (see
 * [CanvasStorage.portableFiles]), the images it places under `images/`, and `meta.json` with
 * its name. For backup and for moving a document between devices; importing always creates a
 * new document, so it never overwrites anything.
 */
fun exportDocumentBundle(context: Context, documentId: String, name: String): ByteArray {
    val out = ByteArrayOutputStream()
    ZipOutputStream(out).use { zip ->
        fun put(entry: String, bytes: ByteArray) {
            zip.putNextEntry(ZipEntry(entry))
            zip.write(bytes)
            zip.closeEntry()
        }
        put(META_ENTRY, JSONObject().put("name", name).put("bundleVersion", BUNDLE_VERSION).toString().toByteArray())
        CanvasStorage.compactStrokes(context, documentId)
        for ((entry, file) in CanvasStorage.portableFiles(context, documentId)) {
            if (file.exists()) put(entry, file.readBytes())
        }
        // Raster layers' pixels (layer settings and tiles) and the animation's soundtrack.
        for ((path, file) in CanvasStorage.rasterFiles(context, documentId)) put(RASTER_PREFIX + path, file.readBytes())
        CanvasStorage.loadAnimationSettings(context, documentId).soundtrack?.let { name ->
            val file = CanvasStorage.soundtrackFile(context, name)
            if (file.exists()) put(SOUNDTRACK_PREFIX + name, file.readBytes())
        }
        for (item in CanvasStorage.loadImageItems(context, documentId).distinctBy { it.fileName }) {
            val image = CanvasStorage.imageFile(context, item.fileName)
            if (image.exists()) put(IMAGES_PREFIX + item.fileName, image.readBytes())
        }
    }
    return out.toByteArray()
}

/** Imports a bundle as a new document and returns it, or null if [input] isn't a valid bundle. */
fun importDocumentBundle(context: Context, input: InputStream): DocumentMeta? {
    val newId = UUID.randomUUID().toString()
    val targets = CanvasStorage.portableFiles(context, newId)
    var name: String? = null
    var soundtrack: Pair<String, ByteArray>? = null
    ZipInputStream(input).use { zip ->
        while (true) {
            val entry = zip.nextEntry ?: break
            val entryName = entry.name
            when {
                entryName == META_ENTRY ->
                    name = (JSONTokener(zip.readBytes().decodeToString()).nextValue() as? JSONObject)?.optString("name")
                // Image file names are generated UUIDs; anything else (e.g. "../") is rejected so
                // a crafted bundle can't write outside the images folder.
                entryName.startsWith(IMAGES_PREFIX) -> {
                    val fileName = entryName.removePrefix(IMAGES_PREFIX)
                    if (fileName.matches(Regex("[A-Za-z0-9-]+\\.png"))) {
                        val target = CanvasStorage.imageFile(context, fileName)
                        val bytes = zip.readBytes()
                        if (!target.exists()) target.writeBytes(bytes)
                    }
                }
                entryName.startsWith(RASTER_PREFIX) ->
                    CanvasStorage.rasterTarget(context, newId, entryName.removePrefix(RASTER_PREFIX))?.writeBytes(zip.readBytes())
                // Stored under a new name below, so the imported copy never shares a file with the original.
                entryName.startsWith(SOUNDTRACK_PREFIX) ->
                    soundtrack = entryName.removePrefix(SOUNDTRACK_PREFIX).substringAfterLast('.', "") to zip.readBytes()
                entryName in targets -> targets.getValue(entryName).writeBytes(zip.readBytes())
            }
            zip.closeEntry()
        }
    }
    val bundleName = name ?: return cleanUp(targets.values)
    val hasStrokes = targets.getValue(CanvasStorage.BUNDLE_STROKES_ENTRY).exists() ||
        targets.getValue(CanvasStorage.LEGACY_BUNDLE_STROKES_ENTRY).exists()
    if (!hasStrokes) return cleanUp(targets.values)
    soundtrack?.let { (extension, bytes) ->
        val stored = CanvasStorage.importSoundtrack(context, bytes.inputStream(), extension, replacing = null)
        val settings = CanvasStorage.loadAnimationSettings(context, newId)
        CanvasStorage.saveAnimationSettings(context, newId, settings.copy(soundtrack = stored))
    }
    return CanvasStorage.registerDocument(context, newId, bundleName)
}

private fun cleanUp(files: Collection<java.io.File>): DocumentMeta? {
    files.forEach { it.delete() }
    return null
}
