package com.andene.fern.canvas

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * A spot colour (roadmap item 102): a named ink - a brand colour, a varnish, a metallic - kept as
 * its own separation in print PDFs. [color] is how it shows on screen (and the colour strokes in
 * it are drawn with); [cmyk] the process mix a press without the ink would stand in with.
 */
data class SpotColor(val name: String, val color: Color, val cmyk: List<Float>)

/**
 * App-wide colour management for print (roadmap item 102): the CMYK ICC profile CMYK values are
 * converted through - entered in the picker, written to print PDFs - and the spot colours.
 * Without a profile, CMYK is plain uncalibrated device CMYK. Kept under files/: the profile as
 * `cmyk.icc`, the spots as `spots.json`.
 */
object ColorManagement {
    /** The loaded CMYK profile, or null for uncalibrated device CMYK. */
    var cmykProfile by mutableStateOf<IccProfile?>(null)
        private set

    val spotColors = mutableStateListOf<SpotColor>()

    private fun profileFile(context: Context) = File(context.filesDir, "cmyk.icc")
    private fun spotsFile(context: Context) = File(context.filesDir, "spots.json")

    /** Reads the saved profile and spots; a profile that no longer reads is set aside. */
    fun load(context: Context) {
        cmykProfile = profileFile(context).takeIf { it.exists() }?.let { runCatching { IccProfile.read(it.readBytes()) }.getOrNull() }
        spotColors.clear()
        spotsFile(context).takeIf { it.exists() }?.let { file ->
            runCatching {
                val root = JSONArray(file.readText())
                for (i in 0 until root.length()) {
                    val e = root.getJSONObject(i)
                    val cmyk = e.getJSONArray("cmyk")
                    spotColors.add(SpotColor(e.getString("name"), Color(e.getInt("color")), List(4) { cmyk.getDouble(it).toFloat() }))
                }
            }
        }
    }

    /** Uses the CMYK profile in [bytes] from now on (throws for a file that isn't one); returns it. */
    fun installProfile(context: Context, bytes: ByteArray): IccProfile {
        val profile = IccProfile.read(bytes)
        profileFile(context).writeBytes(bytes)
        cmykProfile = profile
        return profile
    }

    fun removeProfile(context: Context) {
        profileFile(context).delete()
        cmykProfile = null
    }

    /** Adds or replaces (by name) a spot colour. */
    fun saveSpot(context: Context, spot: SpotColor) {
        val index = spotColors.indexOfFirst { it.name == spot.name }
        if (index == -1) spotColors.add(spot) else spotColors[index] = spot
        writeSpots(context)
    }

    fun deleteSpot(context: Context, name: String) {
        if (spotColors.removeAll { it.name == name }) writeSpots(context)
    }

    private fun writeSpots(context: Context) {
        val root = JSONArray()
        for (s in spotColors) root.put(JSONObject().put("name", s.name).put("color", s.color.toArgb()).put("cmyk", JSONArray(s.cmyk.map { it.toDouble() })))
        spotsFile(context).writeText(root.toString())
    }
}
