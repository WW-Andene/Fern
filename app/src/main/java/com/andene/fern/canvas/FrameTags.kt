package com.andene.fern.canvas

/** How a tagged range plays (roadmap item 145), as in Aseprite's tag directions. */
enum class TagDirection { FORWARD, REVERSE, PING_PONG }

/**
 * A named range of animation frames (roadmap item 145, Aseprite's frame tags): frames [from] to
 * [to] (0-based, inclusive), shown in [color] (ARGB) on the timeline, played [direction] when the
 * tag is played on its own. Tags name the animations of a sprite ("idle", "walk"); the sprite
 * atlas export lists them for game engines.
 */
data class FrameTag(val name: String, val from: Int, val to: Int, val color: Int, val direction: TagDirection = TagDirection.FORWARD) {
    init { require(from in 0..to) { "A tag's range runs forward from frame 0" } }

    operator fun contains(frame: Int) = frame in from..to
}

/**
 * [tags] after inserting a frame at [at]: tags at or after it move along; a tag running across
 * it grows by the new frame.
 */
fun tagsAfterInsert(tags: List<FrameTag>, at: Int): List<FrameTag> = tags.map { tag ->
    when {
        tag.from >= at -> tag.copy(from = tag.from + 1, to = tag.to + 1)
        tag.to >= at -> tag.copy(to = tag.to + 1)
        else -> tag
    }
}

/** [tags] after deleting frame [removed]: later tags move back, a tag over it shrinks, a tag of only that frame goes. */
fun tagsAfterDelete(tags: List<FrameTag>, removed: Int): List<FrameTag> = tags.mapNotNull { tag ->
    when {
        tag.from == removed && tag.to == removed -> null
        tag.from > removed -> tag.copy(from = tag.from - 1, to = tag.to - 1)
        tag.to >= removed -> tag.copy(to = tag.to - 1)
        else -> tag
    }
}

/** Timeline colours offered for tags: distinct at a glance on light and dark themes. */
val TAG_COLORS = intArrayOf(0xFFE53935.toInt(), 0xFFFB8C00.toInt(), 0xFFFDD835.toInt(), 0xFF43A047.toInt(), 0xFF1E88E5.toInt(), 0xFF8E24AA.toInt())
