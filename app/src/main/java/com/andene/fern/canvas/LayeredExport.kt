package com.andene.fern.canvas

import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.unit.Density

/** Longest edge of a layered export (PSD, Krita), in px. */
const val LAYERED_EXPORT_MAX_EDGE_PX = 4096

/** One layer of a layered export: its pixels (non-premultiplied ARGB) over the whole canvas. */
class LayerPixels(
    val name: String,
    val pixels: IntArray,
    val visible: Boolean,
    val opacity: Float,
    val blend: LayerBlend,
)

/** A document as separate layer images plus the flattened picture, all [width] x [height]. */
class LayeredImage(val width: Int, val height: Int, val layers: List<LayerPixels>, val merged: IntArray)

/**
 * Renders [source] layer by layer for the layered formats (roadmap item 95), framed like the
 * other exports. Each layer is drawn alone at full opacity with normal blending (those travel as
 * layer settings), its mask applied. Placed images and text labels, which aren't on any layer,
 * become an "Images" layer at the bottom and a "Text" layer at the top. Adjustment layers can't
 * be expressed as pixels and are left out. Null for an empty document.
 */
fun renderLayers(source: CanvasState, textMeasurer: TextMeasurer, density: Density, imageFor: (String) -> ImageBitmap?): LayeredImage? {
    val frame = frameForExport(source, LAYERED_EXPORT_MAX_EDGE_PX) ?: return null
    val camera = frame.camera
    val allLayers = camera.layers.toList()
    val texts = camera.textItems.toList()
    val images = camera.imageItems.toList()
    fun render(transparent: Boolean): IntArray {
        val image = ImageBitmap(frame.widthPx, frame.heightPx)
        frame.drawOn(Canvas(image), density, textMeasurer, imageFor, transparent)
        return IntArray(frame.widthPx * frame.heightPx).also { image.readPixels(it) }
    }
    val merged = render(transparent = false)
    val result = ArrayList<LayerPixels>()
    fun pass(name: String, layers: List<Layer>, withImages: Boolean, withText: Boolean, visible: Boolean, opacity: Float, blend: LayerBlend) {
        camera.loadLayers(layers)
        camera.loadImageItems(if (withImages) images else emptyList())
        camera.loadTextItems(if (withText) texts else emptyList())
        result.add(LayerPixels(name, render(transparent = true), visible, opacity, blend))
    }
    val hidden = allLayers.map { it.copy(visible = false) }
    if (images.isNotEmpty()) pass("Images", hidden, withImages = true, withText = false, visible = true, opacity = 1f, blend = LayerBlend.NORMAL)
    for ((i, layer) in allLayers.withIndex()) {
        if (layer.adjustment != null) continue
        val alone = hidden.toMutableList().also { it[i] = layer.copy(visible = true, opacity = 1f, blend = LayerBlend.NORMAL, clipped = false) }
        pass(layer.name, alone, withImages = false, withText = false, visible = layer.visible, opacity = layer.opacity, blend = layer.blend)
    }
    if (texts.isNotEmpty()) pass("Text", hidden, withImages = false, withText = true, visible = true, opacity = 1f, blend = LayerBlend.NORMAL)
    return LayeredImage(frame.widthPx, frame.heightPx, result, merged)
}
