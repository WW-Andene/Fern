package com.andene.fern.canvas

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Persists the canvas's strokes to local app storage as JSON, so work survives process death
 * and app restarts. Single-document scope for now — multi-document storage is a separate,
 * later addition, not something to speculatively build in here.
 *
 * Uses `org.json` (bundled in the Android SDK) rather than a serialization library, since the
 * schema is small and fixed; revisit if/when a real migration story (versioned schema) is
 * needed.
 */
object CanvasStorage {
    private const val FILE_NAME = "canvas.json"

    fun save(context: Context, strokes: List<Stroke>) {
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
        file(context).writeText(root.toString())
    }

    fun load(context: Context): List<Stroke> {
        val target = file(context)
        if (!target.exists()) return emptyList()
        val root = JSONArray(target.readText())
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

    private fun file(context: Context) = File(context.filesDir, FILE_NAME)
}
