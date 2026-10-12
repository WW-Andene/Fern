package com.andene.fern.canvas

/*
 * A posable 3D hand (roadmap item 146), as in Clip Studio Paint's hand model: a palm and five
 * jointed fingers, posed by how much each finger curls and spreads, the thumb also by how far it
 * crosses the palm. Built from capsules and a box with [MeshBuilder], one texture cell per part,
 * so a posed hand can be painted like any model.
 */

/** The fingers, thumb first. */
enum class Finger { THUMB, INDEX, MIDDLE, RING, LITTLE }

/**
 * A hand pose: per finger, [curls] (0 straight - 1 fully bent) and [spreads] (-1 toward the
 * middle finger - 1 away from it), [thumbAcross] (0 out to the side - 1 across the palm), and
 * whether it's a [left] hand (the right one mirrored).
 */
data class HandPose(
    val curls: List<Double> = List(5) { 0.15 },
    val spreads: List<Double> = List(5) { 0.0 },
    val thumbAcross: Double = 0.2,
    val left: Boolean = false,
) {
    init { require(curls.size == 5 && spreads.size == 5) { "One value per finger" } }

    fun curl(finger: Finger) = curls[finger.ordinal]
    fun spread(finger: Finger) = spreads[finger.ordinal]

    fun withCurl(finger: Finger, value: Double) = copy(curls = curls.toMutableList().also { it[finger.ordinal] = value.coerceIn(0.0, 1.0) })
    fun withSpread(finger: Finger, value: Double) = copy(spreads = spreads.toMutableList().also { it[finger.ordinal] = value.coerceIn(-1.0, 1.0) })
}

/** Ready-made hand poses (roadmap item 146). */
enum class HandPreset(val pose: HandPose) {
    RELAXED(HandPose(curls = listOf(0.25, 0.2, 0.25, 0.3, 0.35))),
    OPEN(HandPose(curls = List(5) { 0.0 }, spreads = listOf(0.6, 0.5, 0.0, 0.5, 0.8), thumbAcross = 0.0)),
    FIST(HandPose(curls = listOf(0.7, 1.0, 1.0, 1.0, 1.0), thumbAcross = 0.9)),
    POINT(HandPose(curls = listOf(0.7, 0.0, 1.0, 1.0, 1.0), thumbAcross = 0.8)),
    PEACE(HandPose(curls = listOf(0.7, 0.0, 0.0, 1.0, 1.0), spreads = listOf(0.0, 0.6, 0.6, 0.0, 0.0), thumbAcross = 0.8)),
    THUMBS_UP(HandPose(curls = listOf(0.0, 1.0, 1.0, 1.0, 1.0), thumbAcross = 0.0)),
    GRIP(HandPose(curls = listOf(0.45, 0.55, 0.55, 0.55, 0.55), thumbAcross = 0.6)),
}

/** The hand in [pose] as a mesh about 1.8 units long, wrist at the bottom, palm facing +z (toward a default camera). */
fun handMesh(pose: HandPose): Mesh {
    val builder = MeshBuilder(HAND_CELLS)
    // Everything is built for a right hand and reflected across x for a left one: every point
    // and normal mirrored (x negated), which is what a left hand is.
    fun placed(t: Transform3d): Transform3d = if (!pose.left) t else Transform3d(
        doubleArrayOf(-t.r[0], -t.r[1], -t.r[2], t.r[3], t.r[4], t.r[5], t.r[6], t.r[7], t.r[8]),
        Vec3(-t.t.x, t.t.y, t.t.z),
    )
    val root = Transform3d.translation(0.0, -0.9, 0.0)
    builder.box(PALM_WIDTH, PALM_HEIGHT, PALM_DEPTH, placed(root * Transform3d.translation(0.0, PALM_HEIGHT / 2, 0.0)), cell = 0)
    var cell = 1
    for (finger in Finger.entries) {
        val (lengths, radius) = FINGER_SIZES.getValue(finger)
        var bone = if (finger == Finger.THUMB) {
            // From the base of the palm on the index side, angled outward and forward; "across"
            // swings it in front of the palm.
            root * Transform3d.translation(-PALM_WIDTH * 0.42, PALM_HEIGHT * 0.18, PALM_DEPTH * 0.3) *
                Transform3d.rotation(Vec3(0.0, 0.0, 1.0), Math.toRadians(42.0 - 18.0 * pose.spread(finger))) *
                Transform3d.rotation(Vec3(0.0, 1.0, 0.0), Math.toRadians(-20.0 - 60.0 * pose.thumbAcross))
        } else {
            val x = FINGER_BASES.getValue(finger)
            // Spread fans the fingers away from the middle one around the knuckle.
            val away = if (x < 0) 1.0 else -1.0
            root * Transform3d.translation(x * PALM_WIDTH, PALM_HEIGHT, 0.0) *
                Transform3d.rotation(Vec3(0.0, 0.0, 1.0), Math.toRadians(MAX_SPREAD_DEGREES * pose.spread(finger) * away))
        }
        for ((joint, length) in lengths.withIndex()) {
            // Curling bends each joint toward the palm (+z): a turn about x.
            val bend = pose.curl(finger) * MAX_BEND_DEGREES[joint] * (if (finger == Finger.THUMB) THUMB_BEND else 1.0)
            bone = bone * Transform3d.rotation(Vec3(1.0, 0.0, 0.0), Math.toRadians(bend))
            builder.capsule(radius * (1 - 0.08 * joint), length, placed(bone), cell++)
            bone = bone * Transform3d.translation(0.0, length, 0.0)
        }
    }
    return builder.build()
}

// A 4 x 4 grid of texture cells: the palm, then the fifteen finger segments.
private const val HAND_CELLS = 4
private const val PALM_WIDTH = 0.8
private const val PALM_HEIGHT = 0.85
private const val PALM_DEPTH = 0.24
private const val MAX_SPREAD_DEGREES = 14.0
// The base, middle and tip joints' fullest bend.
private val MAX_BEND_DEGREES = doubleArrayOf(85.0, 100.0, 75.0)
// The thumb bends less at each joint.
private const val THUMB_BEND = 0.6
private val FINGER_BASES = mapOf(Finger.INDEX to -0.36, Finger.MIDDLE to -0.12, Finger.RING to 0.12, Finger.LITTLE to 0.36)
// Segment lengths (base to tip) and radius per finger.
private val FINGER_SIZES = mapOf(
    Finger.THUMB to (listOf(0.32, 0.28, 0.24) to 0.1),
    Finger.INDEX to (listOf(0.42, 0.25, 0.2) to 0.08),
    Finger.MIDDLE to (listOf(0.46, 0.28, 0.21) to 0.085),
    Finger.RING to (listOf(0.43, 0.27, 0.2) to 0.08),
    Finger.LITTLE to (listOf(0.34, 0.2, 0.18) to 0.07),
)
