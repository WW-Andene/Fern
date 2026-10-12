package com.andene.fern.canvas

/**
 * How an object follows its frame when the frame is resized (roadmap item 144), along one axis,
 * as in design tools' constraints: pinned to the [START] (left or top) edge, the [END] (right or
 * bottom) edge, [BOTH] edges (stretching with the frame), the [CENTER], or [SCALE]d in
 * proportion with the frame.
 */
enum class ConstraintPin { START, END, BOTH, CENTER, SCALE }

/** An object's [horizontal] and [vertical] constraints. The default pins it to the top-left corner, as unconstrained objects behave. */
data class LayoutConstraints(val horizontal: ConstraintPin = ConstraintPin.START, val vertical: ConstraintPin = ConstraintPin.START) {
    /** One byte for the stroke file: horizontal in the low nibble, vertical in the high one. */
    fun pack(): Int = horizontal.ordinal or (vertical.ordinal shl 4)

    companion object {
        fun unpack(packed: Int): LayoutConstraints {
            val pins = ConstraintPin.entries
            return LayoutConstraints(pins.getOrElse(packed and 0xF) { ConstraintPin.START }, pins.getOrElse((packed shr 4) and 0xF) { ConstraintPin.START })
        }
    }
}

/**
 * Where a coordinate [value] of an object spanning [objectMin]..[objectMax] goes when its frame
 * goes from [oldMin]..[oldMax] to [newMin]..[newMax] along one axis, under [pin].
 */
fun constrainedCoordinate(
    value: Double, objectMin: Double, objectMax: Double,
    oldMin: Double, oldMax: Double, newMin: Double, newMax: Double, pin: ConstraintPin,
): Double = when (pin) {
    ConstraintPin.START -> value + (newMin - oldMin)
    ConstraintPin.END -> value + (newMax - oldMax)
    ConstraintPin.CENTER -> value + ((newMin + newMax) - (oldMin + oldMax)) / 2
    ConstraintPin.SCALE -> {
        val span = oldMax - oldMin
        if (span <= 0) value + (newMin - oldMin) else newMin + (value - oldMin) * (newMax - newMin) / span
    }
    ConstraintPin.BOTH -> {
        // The object's edges keep their distances to the frame's edges; inside, it stretches.
        val left = objectMin + (newMin - oldMin)
        val right = (objectMax + (newMax - oldMax)).coerceAtLeast(left)
        val span = objectMax - objectMin
        if (span <= 0) left else left + (value - objectMin) * (right - left) / span
    }
}
