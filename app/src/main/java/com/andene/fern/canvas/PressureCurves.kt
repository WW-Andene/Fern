package com.andene.fern.canvas

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject

private const val KEY_PRESSURE_CURVES = "pressure_curves"

/**
 * How each pen answers stylus pressure (roadmap item 189): a curve from the pressure the stylus
 * reports to the pressure the stroke records, edited as a graph (the easing curve editor). A
 * light hand raises the curve so soft touches still make a full line; a heavy hand lowers it.
 * Each pen type keeps its own, so a pencil can be soft while an inking pen stays firm. A finger
 * reports full pressure and is left alone, so velocity dynamics keep working for fingers.
 */
object PressureCurves {
    /** The straight curve: pressure unchanged. */
    val LINEAR = CubicEasing(1.0 / 3, 1.0 / 3, 2.0 / 3, 2.0 / 3)

    private var curves by mutableStateOf<Map<PenType, CubicEasing>>(emptyMap())
    private var loaded = false

    /** Reads the saved curves once; later calls do nothing. */
    fun load(context: Context) {
        if (loaded) return
        loaded = true
        val json = AppSettings.prefs(context).getString(KEY_PRESSURE_CURVES, null)?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return
        curves = PenType.entries.mapNotNull { type ->
            json.optJSONArray(type.name)?.takeIf { it.length() == 4 }?.let { a ->
                runCatching { type to CubicEasing(a.getDouble(0), a.getDouble(1), a.getDouble(2), a.getDouble(3)) }.getOrNull()
            }
        }.toMap()
    }

    fun curveFor(type: PenType): CubicEasing = curves[type] ?: LINEAR

    fun set(context: Context, type: PenType, curve: CubicEasing) {
        curves = curves + (type to curve)
        val json = JSONObject()
        for ((t, c) in curves) json.put(t.name, org.json.JSONArray(listOf(c.x1, c.y1, c.x2, c.y2)))
        AppSettings.prefs(context).edit().putString(KEY_PRESSURE_CURVES, json.toString()).apply()
    }

    /** [pressure] from the stylus shaped by [type]'s curve; a finger's full pressure passes unchanged. */
    fun shape(type: PenType, pressure: Float): Float {
        if (pressure >= FULL_PRESSURE) return pressure
        val curve = curves[type] ?: return pressure
        return curve.at(pressure.toDouble()).coerceIn(0.0, 1.0).toFloat()
    }

    private const val FULL_PRESSURE = 0.999f
}
