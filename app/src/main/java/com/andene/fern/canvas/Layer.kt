package com.andene.fern.canvas

/** Layer every stroke belongs to unless placed on another; also where strokes from before layers existed live. */
const val DEFAULT_LAYER_ID = "base"

/**
 * One drawing layer. Layers are drawn bottom to top in [CanvasState.layers] order; each
 * stroke names its layer by [Stroke.layerId]. Immutable/copy-on-edit like [TextItem], so
 * replacing an element of the state list is what Compose observes.
 *
 * Only strokes are layered: text and images keep their fixed places (images beneath all
 * layers, text above), so existing documents look the same.
 */
data class Layer(
    val id: String,
    val name: String,
    val visible: Boolean = true,
    /** A locked layer is still shown but can't be drawn on, erased, filled or selected. */
    val locked: Boolean = false,
    /** Applied to the layer as a whole (composited once), not to each stroke separately. */
    val opacity: Float = 1f,
    /**
     * A reference layer (roadmap item 94): when any layer is one, the fill tool finds its
     * boundaries in reference layers only, so colours can go on their own layer under line art.
     */
    val reference: Boolean = false,
    /**
     * The skin this layer belongs to (roadmap item 86), or null when it's always part of the
     * drawing. A skin is an alternative look for the same rig - a costume, a character variant -
     * drawn on its own layers over the same bones; choosing a skin shows its layers and hides
     * every other skin's ([CanvasState.applySkin]).
     */
    val skin: String? = null,
    /**
     * A raster layer (roadmap item 89) holds pixels ([RasterSurface], kept by
     * [CanvasState.rasters]) instead of strokes; painting and erasing on it change pixels.
     */
    val raster: Boolean = false,
    /** A raster layer's bits per channel: 8, or 16 for smooth gradients and repeated editing. */
    val depth: Int = 8,
    /**
     * Has a layer mask (roadmap item 91): a grayscale raster ([CanvasState.rasters] under
     * [maskIdOf]) whose paint hides the layer where it covers, raster or vector alike.
     */
    val masked: Boolean = false,
    /** A clipping mask: drawn only where the nearest unclipped layer below has paint. */
    val clipped: Boolean = false,
    /** Alpha lock: painting on this raster layer changes colour only where there's already paint. */
    val alphaLocked: Boolean = false,
    /** How the layer composites onto what's beneath it (roadmap item 93). */
    val blend: LayerBlend = LayerBlend.NORMAL,
    /**
     * An adjustment layer (roadmap item 93) holds no content: it applies this adjustment to
     * everything beneath it, at the layer's opacity, and can be changed or removed at any time.
     */
    val adjustment: Adjustment? = null,
)

/** Where layer [layerId]'s mask lives among [CanvasState.rasters] and on disk. */
fun maskIdOf(layerId: String): String = "$layerId-mask"
