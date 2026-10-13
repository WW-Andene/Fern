package com.andene.fern.canvas

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * A named set of colours (roadmap item 350), like the swatch sets of Procreate, Krita and Clip
 * Studio. The user keeps several, switches between them, and the active one is what the colour
 * row, the indexed pixel palette and "change everywhere" use.
 */
data class Palette(val id: String, val name: String, val colors: List<Color>)

/** The most colours one palette holds; far beyond any real palette, a guard against runaway imports. */
const val MAX_PALETTE_COLORS = 256

/** Reading and saving the palettes, shared by all documents. */
object PaletteStore {
    private const val FILE_NAME = "palettes.json"

    /** The colours offered before the user has any of their own. */
    val BASIC = listOf(Color(0xFF1B1B1B), Color(0xFFE53935), Color(0xFF1E88E5), Color(0xFF43A047), Color(0xFFFB8C00))

    /**
     * The saved palettes and the active one's id. The first time, the palettes are made from
     * what the app had before: the basic colours, and the single saved palette as "My colours".
     */
    fun load(context: Context, basicName: String, mineName: String): Pair<List<Palette>, String?> {
        val file = File(context.filesDir, FILE_NAME)
        val json = if (file.exists()) runCatching { JSONObject(file.readText()) }.getOrNull() else null
        if (json == null) {
            val basic = Palette(UUID.randomUUID().toString(), basicName, BASIC)
            val saved = CanvasStorage.loadSavedColors(context)
            val mine = Palette(UUID.randomUUID().toString(), mineName, saved)
            return listOf(basic, mine) to (if (saved.isNotEmpty()) mine.id else basic.id)
        }
        val list = json.optJSONArray("palettes") ?: JSONArray()
        val palettes = (0 until list.length()).map { i ->
            val p = list.getJSONObject(i)
            val colors = p.getJSONArray("colors")
            Palette(p.getString("id"), p.getString("name"), (0 until colors.length()).map { Color(colors.getInt(it)) })
        }
        return palettes to json.optString("active").takeIf { it.isNotEmpty() }
    }

    fun save(context: Context, palettes: List<Palette>, activeId: String?) {
        val list = JSONArray()
        for (p in palettes) {
            list.put(JSONObject().put("id", p.id).put("name", p.name).put("colors", JSONArray().apply { p.colors.forEach { put(it.toArgb()) } }))
        }
        val json = JSONObject().put("palettes", list).put("active", activeId ?: "")
        val file = File(context.filesDir, FILE_NAME)
        val temp = File(context.filesDir, "$FILE_NAME.tmp")
        temp.writeText(json.toString())
        temp.renameTo(file)
    }
}
