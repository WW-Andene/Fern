package com.andene.fern.canvas

import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.PixelFormat
import android.os.Build
import android.view.MotionEvent
import android.view.SurfaceView
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.toArgb
import androidx.graphics.lowlatency.CanvasFrontBufferedRenderer

/**
 * Low-latency ink (roadmap item 7): the stroke in progress is also drawn straight into a
 * front buffer, a surface the display scans out without waiting for the next composed frame,
 * so ink appears under the pen a frame or two sooner than Compose alone can show it.
 *
 * The Compose canvas still draws the real stroke underneath; this layer only paints the same
 * segments on top as they arrive and is wiped when the stroke ends. That's why it only runs
 * for strokes whose overdraw is invisible - an opaque, normal-blend, undashed marker with no
 * width profile ([isEligible]); a translucent or textured pen would visibly double up.
 * Width follows pressure and tilt the same way the canvas does (see `widthFactorAt`).
 *
 * Requires API 29 (front-buffered SurfaceControl); older devices keep the Compose-only path.
 * The latency gain can only be measured on a real device and hasn't been here.
 */
@RequiresApi(Build.VERSION_CODES.Q)
class FrontBufferedInk(surfaceView: SurfaceView) {
    /** One straight piece of ink in view pixels. */
    class Segment(val x0: Float, val y0: Float, val x1: Float, val y1: Float, val width: Float, val argb: Int)

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val renderer = CanvasFrontBufferedRenderer(surfaceView, object : CanvasFrontBufferedRenderer.Callback<Segment> {
        override fun onDrawFrontBufferedLayer(canvas: Canvas, bufferWidth: Int, bufferHeight: Int, param: Segment) {
            paint.color = param.argb
            paint.strokeWidth = param.width
            canvas.drawLine(param.x0, param.y0, param.x1, param.y1, paint)
        }

        override fun onDrawMultiBufferedLayer(canvas: Canvas, bufferWidth: Int, bufferHeight: Int, params: Collection<Segment>) {
            // The Compose canvas owns the finished stroke; this layer stays transparent.
            canvas.drawColor(AndroidColor.TRANSPARENT, BlendMode.CLEAR)
        }
    })

    private var lastX = Float.NaN
    private var lastY = Float.NaN
    private var active = false

    /**
     * Feeds one raw touch event (view coordinates of the canvas). [stroke] is the stroke the
     * canvas is currently drawing, or null when the pen isn't drawing; [scale] is the zoom.
     */
    fun onTouchEvent(event: MotionEvent, stroke: Stroke?, scale: Double) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.getX(0)
                lastY = event.getY(0)
            }
            MotionEvent.ACTION_MOVE -> {
                if (stroke == null || !isEligible(stroke) || event.pointerCount != 1 || lastX.isNaN()) return
                val argb = stroke.color.toArgb()
                val baseWidth = (stroke.widthWorld * scale).toFloat().coerceIn(1f, 4096f)
                // Finger pressure is noise the canvas ignores (hasStylusVariation), so only a pen varies width.
                val isStylus = event.getToolType(0) == MotionEvent.TOOL_TYPE_STYLUS
                for (h in 0..event.historySize) {
                    val x = if (h < event.historySize) event.getHistoricalX(0, h) else event.getX(0)
                    val y = if (h < event.historySize) event.getHistoricalY(0, h) else event.getY(0)
                    val pressure = if (h < event.historySize) event.getHistoricalPressure(0, h) else event.getPressure(0)
                    val factor = if (isStylus) inkWidthFactor(pressure, event.getAxisValue(MotionEvent.AXIS_TILT, 0)) else 1f
                    renderer.renderFrontBufferedLayer(Segment(lastX, lastY, x, y, baseWidth * factor, argb))
                    active = true
                    lastX = x
                    lastY = y
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN -> clear()
        }
    }

    /** Wipes the front buffer, leaving the Compose canvas's copy of the stroke. */
    fun clear() {
        lastX = Float.NaN
        lastY = Float.NaN
        if (active) {
            renderer.commit()
            active = false
        }
    }

    fun release() = renderer.release(true)

    companion object {
        /** Configures a SurfaceView to sit transparently above the window, as the front buffer needs. */
        fun prepare(surfaceView: SurfaceView) {
            surfaceView.setZOrderOnTop(true)
            surfaceView.holder.setFormat(PixelFormat.TRANSLUCENT)
        }

        val isSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
    }
}

/** Strokes whose front-buffered copy is indistinguishable from the canvas's own rendering. */
internal fun isEligible(stroke: Stroke): Boolean =
    stroke.penType == PenType.MARKER && !stroke.filled && !stroke.dashed &&
        stroke.blendMode == StrokeBlendMode.NORMAL && stroke.lineStyle == null && stroke.color.alpha >= 1f

/** Mirrors DrawingCanvas's pressure/tilt width multiplier so both layers draw the same width. */
internal fun inkWidthFactor(pressure: Float, tilt: Float): Float {
    val tiltFactor = 1f + (tilt / (Math.PI.toFloat() / 2f)).coerceIn(0f, 1f) * 0.8f
    return pressure.coerceIn(0.1f, 1f) * tiltFactor
}
