package com.andene.fern.canvas

import android.content.Context
import android.graphics.Bitmap
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import kotlin.math.acos

/*
 * The hand pose scanner (roadmap item 146), as in Clip Studio Paint: a photo of a hand poses the
 * 3D hand. MediaPipe's hand landmarker (on the device, no network) finds the hand's 21 joints in
 * 3D; [landmarksToPose] turns them into the curls, spreads and thumb reach a [HandPose] holds.
 */

/** MediaPipe's 21 hand landmarks: 0 the wrist, then four per finger from its base joint to its tip, thumb first. */
private const val WRIST = 0
private fun joint(finger: Finger, k: Int) = 1 + finger.ordinal * 4 + k

/**
 * The [HandPose] of a hand whose 21 landmarks (MediaPipe's order, metric 3D) are [points]:
 * each finger's curl from how far its joints bend (as a share of the 3D hand's fullest bend),
 * its spread from its angle to the middle finger across the palm, the thumb's reach across the
 * palm from how close its tip comes to the little finger's knuckle. [left] says which hand it is.
 */
fun landmarksToPose(points: List<Vec3>, left: Boolean): HandPose {
    require(points.size == 21) { "21 hand landmarks expected" }
    fun bend(a: Vec3, b: Vec3, c: Vec3): Double {
        val u = (b - a).normalized()
        val v = (c - b).normalized()
        return Math.toDegrees(acos((u dot v).coerceIn(-1.0, 1.0)))
    }
    val wrist = points[WRIST]
    // The palm's plane: across (index knuckle to little knuckle) and along (wrist to middle knuckle).
    val across = (points[joint(Finger.LITTLE, 0)] - points[joint(Finger.INDEX, 0)]).normalized()
    val along = (points[joint(Finger.MIDDLE, 0)] - wrist).normalized()
    val normal = across.cross(along).normalized()
    val curls = Finger.entries.map { finger ->
        // The wrist, then the finger's four landmarks; bends at the three joints between them.
        val chain = listOf(wrist) + (0..3).map { points[joint(finger, it)] }
        val bends = (0..2).map { k -> bend(chain[k], chain[k + 1], chain[k + 2]) }
        if (finger == Finger.THUMB) {
            // The thumb's base (CMC) sits angled out from the wrist even when straight: only its
            // MCP and IP joints say how far it's bent.
            ((bends[1] / 60.0).coerceIn(0.0, 1.0) + (bends[2] / 80.0).coerceIn(0.0, 1.0)) / 2
        } else {
            // At the knuckle, only the bend out of the palm's plane is a curl: the fingers' fan
            // across the palm (their spread) would otherwise read as one.
            val proximal = chain[2] - chain[1]
            val knuckle = Math.toDegrees(kotlin.math.atan2(kotlin.math.abs(proximal dot normal), proximal dot along))
            val maxima = doubleArrayOf(85.0, 100.0, 75.0)
            (listOf(knuckle) + bends.drop(1)).mapIndexed { k, b -> (b / maxima[k]).coerceIn(0.0, 1.0) }.average()
        }
    }
    val middle = (points[joint(Finger.MIDDLE, 1)] - points[joint(Finger.MIDDLE, 0)])
    fun planar(v: Vec3) = Math.toDegrees(kotlin.math.atan2(v dot across, v dot along))
    val spreads = Finger.entries.map { finger ->
        if (finger == Finger.THUMB || finger == Finger.MIDDLE) return@map 0.0
        val direction = points[joint(finger, 1)] - points[joint(finger, 0)]
        val angle = planar(direction) - planar(middle)
        // Positive spread moves a finger away from the middle one: toward the index side for the index finger.
        val away = if (finger == Finger.INDEX) -angle else angle
        (away / SCAN_FULL_SPREAD_DEGREES).coerceIn(-1.0, 1.0)
    }
    val palmWidth = (points[joint(Finger.LITTLE, 0)] - points[joint(Finger.INDEX, 0)]).length.coerceAtLeast(1e-9)
    val reach = (points[joint(Finger.THUMB, 3)] - points[joint(Finger.LITTLE, 0)]).length / palmWidth
    // A tip at the little knuckle is fully across (reach ~0.4); out to the side, ~1.8 palm widths away.
    val thumbAcross = (1 - (reach - 0.4) / 1.4).coerceIn(0.0, 1.0)
    return HandPose(curls, spreads, thumbAcross, left)
}

/**
 * Finds a hand in [photo] and returns its pose, or null when none is found or the detector
 * can't run. MediaPipe labels handedness as if the picture were mirrored (a selfie); a photo
 * from the back camera or the gallery isn't, so the label is read the other way round.
 * Blocking: call off the main thread.
 */
fun scanHandPose(context: Context, photo: Bitmap): HandPose? = runCatching {
    val options = HandLandmarker.HandLandmarkerOptions.builder()
        .setBaseOptions(BaseOptions.builder().setModelAssetPath(HAND_MODEL_ASSET).build())
        .setRunningMode(RunningMode.IMAGE)
        .setNumHands(1)
        .build()
    HandLandmarker.createFromOptions(context, options).use { landmarker ->
        val bitmap = if (photo.config == Bitmap.Config.ARGB_8888) photo else photo.copy(Bitmap.Config.ARGB_8888, false)
        val result = landmarker.detect(BitmapImageBuilder(bitmap).build())
        val hand = result.worldLandmarks().firstOrNull() ?: return@use null
        val label = result.handedness().firstOrNull()?.firstOrNull()?.categoryName()
        landmarksToPose(hand.map { Vec3(it.x().toDouble(), it.y().toDouble(), it.z().toDouble()) }, left = label == "Right")
    }
}.getOrNull()

private const val HAND_MODEL_ASSET = "hand_landmarker.task"
// A finger fanned this far from the middle one counts as fully spread.
private const val SCAN_FULL_SPREAD_DEGREES = 20.0
