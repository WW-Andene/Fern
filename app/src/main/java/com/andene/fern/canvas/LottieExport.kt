package com.andene.fern.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import org.json.JSONArray
import org.json.JSONObject

// Longest edge of the Lottie composition in px, like the GIF and sprite-sheet exports.
private const val LOTTIE_MAX_EDGE_PX = 512

/**
 * The animation as a Lottie file (roadmap item 87): JSON that apps and web pages play with the
 * Lottie runtimes, vector and scalable. Frame by frame, like the flipbook: each animation frame
 * is one shape layer shown for that frame only, holding its strokes as paths (outlined, or
 * filled for fills) in layer order, over a paper-coloured background layer. Null for an empty
 * document.
 *
 * Simplified like the SVG export: uniform-width strokes, no pen textures, blend modes, text or
 * images; layer opacity is folded into each stroke's.
 */
fun exportLottie(source: CanvasState): String? {
    val frame = frameForExport(source, LOTTIE_MAX_EDGE_PX) ?: return null
    val camera = frame.camera
    val center = Offset(frame.widthPx / 2f, frame.heightPx / 2f)
    fun px(p: WorldPoint): JSONArray {
        val s = camera.worldToScreen(p, center)
        return JSONArray().put(round(s.x.toDouble())).put(round(s.y.toDouble()))
    }
    val layerIndex = camera.layers.withIndex().associate { (index, layer) -> layer.id to index }
    val frames = source.animFrameCount
    // Each frame starts where the previous one's hold ends (X-sheet, roadmap item 88).
    val starts = IntArray(frames + 1).also { for (f in 0 until frames) it[f + 1] = it[f] + source.holdOf(f) }
    val layers = JSONArray()
    // Lottie draws its first layer on top, so frames go first and the paper last.
    for (f in 0 until frames) {
        val shapes = JSONArray()
        val ordered = camera.strokes.withIndex()
            .filter { it.value.animFrame == f && it.value.points.isNotEmpty() }
            .sortedWith(compareBy({ layerIndex[it.value.layerId] ?: 0 }, { it.index }))
            .map { it.value }
        // Also top-first within the layer: reverse drawing order.
        for (stroke in ordered.asReversed()) {
            val layer = camera.layers.getOrNull(layerIndex[stroke.layerId] ?: 0)
            if (layer != null && !layer.visible) continue
            val opacity = stroke.color.alpha * (layer?.opacity ?: 1f) * 100.0
            val vertices = JSONArray().apply { stroke.points.forEach { put(px(it)) } }
            val zeros = JSONArray().apply { repeat(stroke.points.size) { put(JSONArray().put(0).put(0)) } }
            val path = JSONObject().put("ty", "sh").put(
                "ks",
                static(JSONObject().put("i", zeros).put("o", zeros).put("v", vertices).put("c", stroke.filled)),
            )
            val paint = if (stroke.filled) {
                JSONObject().put("ty", "fl").put("c", static(rgba(stroke.color))).put("o", static(round(opacity))).put("r", 1)
            } else {
                JSONObject().put("ty", "st").put("c", static(rgba(stroke.color))).put("o", static(round(opacity)))
                    .put("w", static(round(stroke.widthWorld * camera.scale)))
                    // Lottie: caps 1 butt, 2 round, 3 square; joins 1 miter, 2 round, 3 bevel.
                    .put("lc", when (stroke.lineStyle?.cap) { LineCap.BUTT -> 1; LineCap.SQUARE -> 3; else -> 2 })
                    .put("lj", when (stroke.lineStyle?.join) { LineJoin.MITER -> 1; LineJoin.BEVEL -> 3; else -> 2 })
            }
            shapes.put(JSONObject().put("ty", "gr").put("it", JSONArray().put(path).put(paint).put(groupTransform())))
        }
        layers.put(shapeLayer("Frame ${f + 1}", starts[f], starts[f + 1], shapes))
    }
    val paper = JSONArray().put(
        JSONObject().put("ty", "gr").put(
            "it",
            JSONArray()
                .put(
                    JSONObject().put("ty", "rc").put("p", static(JSONArray().put(frame.widthPx / 2.0).put(frame.heightPx / 2.0)))
                        .put("s", static(JSONArray().put(frame.widthPx).put(frame.heightPx))).put("r", static(0)),
                )
                .put(JSONObject().put("ty", "fl").put("c", static(rgba(camera.backgroundColor))).put("o", static(100)).put("r", 1))
                .put(groupTransform()),
        ),
    )
    layers.put(shapeLayer("Paper", 0, starts[frames], paper))
    return JSONObject()
        .put("v", "5.7.4")
        .put("fr", source.animFps)
        .put("ip", 0)
        .put("op", starts[frames])
        .put("w", frame.widthPx)
        .put("h", frame.heightPx)
        .put("nm", "Fern animation")
        .put("ddd", 0)
        .put("assets", JSONArray())
        .put("layers", layers)
        .toString()
}

private fun shapeLayer(name: String, inPoint: Int, outPoint: Int, shapes: JSONArray): JSONObject = JSONObject()
    .put("ddd", 0)
    .put("ty", 4)
    .put("nm", name)
    .put("sr", 1)
    .put("ks", layerTransform())
    .put("ao", 0)
    .put("shapes", shapes)
    .put("ip", inPoint)
    .put("op", outPoint)
    .put("st", 0)
    .put("bm", 0)

private fun layerTransform(): JSONObject = JSONObject()
    .put("o", static(100))
    .put("r", static(0))
    .put("p", static(JSONArray().put(0).put(0).put(0)))
    .put("a", static(JSONArray().put(0).put(0).put(0)))
    .put("s", static(JSONArray().put(100).put(100).put(100)))

private fun groupTransform(): JSONObject = JSONObject()
    .put("ty", "tr")
    .put("p", static(JSONArray().put(0).put(0)))
    .put("a", static(JSONArray().put(0).put(0)))
    .put("s", static(JSONArray().put(100).put(100)))
    .put("r", static(0))
    .put("o", static(100))

private fun static(value: Any): JSONObject = JSONObject().put("a", 0).put("k", value)

private fun rgba(color: Color): JSONArray =
    JSONArray().put(round(color.red.toDouble())).put(round(color.green.toDouble())).put(round(color.blue.toDouble())).put(1)

// Three decimals: plenty at these pixel sizes, and keeps the file small.
private fun round(value: Double): Double = Math.round(value * 1000) / 1000.0
