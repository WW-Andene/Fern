package com.andene.fern.canvas

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.util.UUID

/** A saved symbol's name and id; its strokes are read on demand ([SymbolLibrary.load]). */
data class SymbolMeta(val id: String, val name: String)

/**
 * Reusable symbols (roadmap item 100): drawings saved once and placed in any document, like a
 * vector editor's symbol library. Kept app-wide, outside any one document, under
 * files/symbols/: each symbol's strokes in the binary stroke format ([StrokeCodec]) and an
 * index of names. Placing a symbol pastes a copy, so editing one copy leaves the others and
 * the saved symbol as they are; strokes only (labels and images aren't kept).
 */
object SymbolLibrary {
    private const val DIR = "symbols"
    private const val INDEX = "index.json"

    private fun dir(context: Context) = File(context.filesDir, DIR).apply { mkdirs() }

    fun list(context: Context): List<SymbolMeta> {
        val file = File(dir(context), INDEX)
        if (!file.exists()) return emptyList()
        return runCatching {
            val root = JSONArray(file.readText())
            (0 until root.length()).map { root.getJSONObject(it) }.map { SymbolMeta(it.getString("id"), it.getString("name")) }
        }.getOrDefault(emptyList())
    }

    /** Saves [strokes] as a new symbol called [name], listed last. */
    fun save(context: Context, name: String, strokes: List<Stroke>): SymbolMeta {
        val meta = SymbolMeta(UUID.randomUUID().toString(), name.ifBlank { "Symbol" })
        val bytes = ByteArrayOutputStream().also { StrokeCodec.write(DataOutputStream(it), strokes) }.toByteArray()
        writeAtomically(File(dir(context), "${meta.id}.bin"), bytes)
        writeIndex(context, list(context) + meta)
        return meta
    }

    /** The symbol's strokes, or empty if it's missing or unreadable. */
    fun load(context: Context, id: String): List<Stroke> {
        val file = File(dir(context), "$id.bin")
        if (!file.exists()) return emptyList()
        return try {
            StrokeCodec.read(DataInputStream(ByteArrayInputStream(file.readBytes())))
        } catch (e: IOException) {
            emptyList()
        }
    }

    fun delete(context: Context, id: String) {
        writeIndex(context, list(context).filter { it.id != id })
        File(dir(context), "$id.bin").delete()
    }

    private fun writeIndex(context: Context, symbols: List<SymbolMeta>) {
        val root = JSONArray()
        for (s in symbols) root.put(JSONObject().put("id", s.id).put("name", s.name))
        writeAtomically(File(dir(context), INDEX), root.toString().toByteArray())
    }

    // Written beside the target, then renamed over it, so a crash mid-write never leaves a torn file.
    private fun writeAtomically(file: File, bytes: ByteArray) {
        val temp = File(file.parentFile, file.name + ".tmp")
        temp.writeBytes(bytes)
        if (!temp.renameTo(file)) {
            file.delete()
            temp.renameTo(file)
        }
    }
}
