package com.andene.fern.canvas

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStyle
import com.andene.fern.FernScale
import com.andene.fern.FernSize

// A thumbnail draws at most this many strokes, and this many points of each: enough to recognise the layer.
private const val THUMB_STROKES = 256
private const val THUMB_POINTS = 64

/**
 * A layer's thumbnail (design roadmap D43): its strokes as thin lines in their colours, fitted
 * to a small square; raster layers show their outline only. Redrawn when the strokes change.
 */
@Composable
fun LayerThumbnail(state: CanvasState, layerId: String) {
    val strokes = state.strokes.filter { it.layerId == layerId && it.points.isNotEmpty() }.takeLast(THUMB_STROKES)
    val bounds = remember(strokes) {
        if (strokes.isEmpty()) null else listOf(strokes.minOf { it.minX }, strokes.minOf { it.minY }, strokes.maxOf { it.maxX }, strokes.maxOf { it.maxY })
    }
    Canvas(
        Modifier
            .size(FernSize.icon + FernScale.s8)
            .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.extraSmall)
            .border(FernScale.s1, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.extraSmall),
    ) {
        val (minX, minY, maxX, maxY) = bounds ?: return@Canvas
        val extent = maxOf(maxX - minX, maxY - minY).coerceAtLeast(1e-9)
        val k = (size.minDimension * 0.85f) / extent
        val ox = (size.width - (maxX - minX) * k) / 2
        val oy = (size.height - (maxY - minY) * k) / 2
        for (stroke in strokes) {
            val step = (stroke.points.size / THUMB_POINTS).coerceAtLeast(1)
            val path = Path()
            stroke.points.filterIndexed { i, _ -> i % step == 0 || i == stroke.points.lastIndex }.forEachIndexed { i, p ->
                val o = Offset((ox + (p.x - minX) * k).toFloat(), (oy + (p.y - minY) * k).toFloat())
                if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y)
            }
            drawPath(path, stroke.color, style = DrawStyle(width = 1f))
        }
    }
}
