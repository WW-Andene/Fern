package com.andene.fern.canvas

/**
 * Drags the canvas handle nearest [from] (roadmap item 171) through [to], as the canvas does for a
 * finger: press, a drag to each point, release. With no point to go to it's a tap on the handle.
 * Fails when no handle is within [reach] world units of [from].
 */
internal fun CanvasState.dragHandle(from: WorldPoint, vararg to: WorldPoint, reach: Double = 24.0) {
    val handle = canvasHandles().minByOrNull { kotlin.math.hypot(it.position.x - from.x, it.position.y - from.y) }
        ?.takeIf { kotlin.math.hypot(it.position.x - from.x, it.position.y - from.y) <= reach }
        ?: throw AssertionError("No canvas handle within $reach of $from")
    handle.press(from)
    for (point in to) handle.drag(point)
    handle.release()
}
