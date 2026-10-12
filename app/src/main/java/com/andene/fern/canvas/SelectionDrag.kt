package com.andene.fern.canvas

import android.content.ClipData
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Point
import android.graphics.Rect
import android.view.View

// Longest edge of the picture that follows the finger during a drag; on the §7 scale.
private const val DRAG_SHADOW_MAX_PX = 192

/**
 * Starts dragging [clip] (a [SystemClipboard.dragClip]) out of [view] to any window, showing
 * [image] - the selection's picture, scaled down - under the finger (roadmap item 75). Other
 * apps receive the PNG and may read it; another Fern window gets the strokes back. Must run
 * while the finger is still down. False if the system refused the drag.
 */
fun startSelectionDrag(view: View, clip: ClipData, image: Bitmap): Boolean {
    val scale = minOf(1f, DRAG_SHADOW_MAX_PX.toFloat() / maxOf(image.width, image.height))
    val width = maxOf(1, (image.width * scale).toInt())
    val height = maxOf(1, (image.height * scale).toInt())
    val shadow = object : View.DragShadowBuilder(view) {
        override fun onProvideShadowMetrics(outShadowSize: Point, outShadowTouchPoint: Point) {
            outShadowSize.set(width, height)
            outShadowTouchPoint.set(width / 2, height / 2)
        }

        override fun onDrawShadow(canvas: Canvas) {
            canvas.drawBitmap(image, null, Rect(0, 0, width, height), null)
        }
    }
    return view.startDragAndDrop(clip, shadow, null, View.DRAG_FLAG_GLOBAL or View.DRAG_FLAG_GLOBAL_URI_READ)
}
