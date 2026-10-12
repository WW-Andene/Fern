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
)
