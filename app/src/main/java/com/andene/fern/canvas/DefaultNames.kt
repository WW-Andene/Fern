package com.andene.fern.canvas

import android.content.Context
import com.andene.fern.R

/**
 * The names things get when they're made (design roadmap D87), in the user's language: "Layer 3"
 * or "Calque 3". [CanvasState] has no [Context], so the activity gives it these through
 * [fromResources]; the English ones stand in where there are no resources (tests).
 */
open class DefaultNames {
    open fun layer(number: Int) = "Layer $number"
    open fun rasterLayer(number: Int) = "Raster $number"
    open fun hdrLayer(number: Int) = "HDR $number"
    open fun fileLayer(number: Int) = "File $number"
    open fun adjustmentLayer(number: Int) = "Adjustment $number"
    open fun copyOf(name: String) = "$name copy"

    companion object {
        /** The names from [context]'s resources, in its language. */
        fun fromResources(context: Context): DefaultNames = object : DefaultNames() {
            override fun layer(number: Int) = context.getString(R.string.layer_default_name, number)
            override fun rasterLayer(number: Int) = context.getString(R.string.layer_default_raster, number)
            override fun hdrLayer(number: Int) = context.getString(R.string.layer_default_hdr, number)
            override fun fileLayer(number: Int) = context.getString(R.string.layer_default_file, number)
            override fun adjustmentLayer(number: Int) = context.getString(R.string.layer_default_adjustment, number)
            override fun copyOf(name: String) = context.getString(R.string.name_copy, name)
        }
    }
}

/** [base], or "[base] 2", "[base] 3"… - the first not in [taken], so new things don't share a name. */
fun uniqueName(base: String, taken: Collection<String>): String {
    if (base !in taken) return base
    var n = 2
    while ("$base $n" in taken) n++
    return "$base $n"
}
