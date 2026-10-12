package com.andene.fern.canvas

/**
 * A placed symbol that stays linked to it (roadmap item 100): the strokes of group [groupId] are
 * symbol [symbolId] as it was at [revision], drawn at the size where the symbol's master was
 * [masterWidth] wide. When the symbol is redefined, the instance is redrawn from the new master
 * at its own current place and size - moved or resized instances keep that. Detaching drops this
 * record and leaves plain strokes.
 */
data class SymbolInstance(val groupId: String, val symbolId: String, val revision: Long, val masterWidth: Double)

/**
 * The new master [master] fitted to an instance now spanning [instanceMin]..[instanceMax]: scaled
 * by how the instance's width compares to the master width it was made from ([oldMasterWidth]),
 * and centred where the instance is. Fresh copies; the caller sets their group and layer.
 */
fun fittedSymbol(master: List<Stroke>, oldMasterWidth: Double, instanceMin: WorldPoint, instanceMax: WorldPoint): List<Stroke> {
    val drawn = master.filter { it.points.isNotEmpty() }
    if (drawn.isEmpty()) return emptyList()
    val factor = (instanceMax.x - instanceMin.x) / oldMasterWidth.coerceAtLeast(1e-9)
    val cx = (drawn.minOf { it.minX } + drawn.maxOf { it.maxX }) / 2
    val cy = (drawn.minOf { it.minY } + drawn.maxOf { it.maxY }) / 2
    val tx = (instanceMin.x + instanceMax.x) / 2
    val ty = (instanceMin.y + instanceMax.y) / 2
    return drawn.map { stroke ->
        stroke.deepCopy().also { copy ->
            copy.setPoints(stroke.points.map { WorldPoint(tx + (it.x - cx) * factor, ty + (it.y - cy) * factor) })
            copy.setWidth(stroke.widthWorld * factor)
        }
    }
}

/** The width of [strokes]' combined bounds (0 when empty). */
fun strokesWidth(strokes: List<Stroke>): Double {
    val drawn = strokes.filter { it.points.isNotEmpty() }
    if (drawn.isEmpty()) return 0.0
    return drawn.maxOf { it.maxX } - drawn.minOf { it.minX }
}
