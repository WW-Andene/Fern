package com.andene.fern.canvas

/**
 * A layer comp (roadmap item 123; Photoshop's layer comps, Affinity's layer states): a named
 * record of how each layer was shown - visible, opacity, blend - so several versions of one
 * drawing (with and without colour, day and night, alternative backgrounds) can be switched
 * between without hiding and showing layers one by one. Layers added after the comp was saved
 * aren't in it and are left as they are when it's applied; deleted ones are ignored.
 */
data class LayerComp(val id: String, val name: String, val states: Map<String, LayerCompState>)

/** One layer's look in a [LayerComp]. */
data class LayerCompState(val visible: Boolean, val opacity: Float, val blend: LayerBlend)

/** A comp of [layers] as they are now. */
fun captureLayerComp(id: String, name: String, layers: List<Layer>): LayerComp =
    LayerComp(id, name, layers.associate { it.id to LayerCompState(it.visible, it.opacity, it.blend) })

/** [layers] shown the way [comp] records. */
fun layersShownAs(comp: LayerComp, layers: List<Layer>): List<Layer> = layers.map { layer ->
    comp.states[layer.id]?.let { layer.copy(visible = it.visible, opacity = it.opacity, blend = it.blend) } ?: layer
}

/** True when every layer [comp] knows is shown the way it records. */
fun layerCompMatches(comp: LayerComp, layers: List<Layer>): Boolean =
    layers.all { layer -> comp.states[layer.id]?.let { it == LayerCompState(layer.visible, layer.opacity, layer.blend) } ?: true }
