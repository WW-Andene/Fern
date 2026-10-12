package com.andene.fern.canvas

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStyle
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.andene.fern.R

/**
 * The second view (roadmap item 124; Affinity's and Clip Studio's "new view" of the same
 * document): the drawing in its own pane with its own pan and zoom, live - a zoomed-out view
 * to judge the whole while working close up, or a close-up of a detail while drawing the
 * whole. Two fingers pan and zoom it; one finger draws or erases with the pen or eraser tool
 * as it would on the main canvas (at this view's zoom, so the pen is the same size on screen),
 * and pans with any other tool. Pressure and tilt aren't read here (strokes are drawn at full
 * pressure); the main canvas does that. Strokes go to the one document, so they show in both views and
 * undo as usual. The main view's frame is outlined in it.
 */
@Composable
fun SecondView(state: CanvasState, modifier: Modifier = Modifier) {
    var center by remember { mutableStateOf(state.panWorld) }
    var viewScale by remember { mutableDoubleStateOf(state.scale / 4) }
    // Follow the floating origin: when the world shifts, so does this view's centre.
    var seenShift by remember { mutableStateOf(state.originShift) }
    LaunchedEffect(state.originShift) {
        center -= state.originShift - seenShift
        seenShift = state.originShift
    }
    // A camera onto the document, rebuilt when its content changes. It shares the stroke
    // objects rather than copying them, so the stroke being drawn shows as it grows.
    val version = state.sceneVersion()
    val camera = remember(version) {
        CanvasState(floatingOrigin = false).apply {
            loadStrokes(state.strokes.toList())
            loadLayers(state.layers.toList())
            loadRasters(state.rasters.mapValues { it.value.view() })
            loadTextItems(state.textItems.toList())
            loadConnectors(state.connectors.toList())
            loadImageItems(state.imageItems.toList())
            loadBackground(state.backgroundColor, state.backgroundStyle)
            goToAnimFrame(state.currentAnimFrame)
        }
    }
    var paneSize by remember { mutableStateOf(IntSize.Zero) }
    val textMeasurer = rememberTextMeasurer()
    val context = LocalContext.current
    val images = remember { HashMap<String, ImageBitmap>() }
    val imageFor = { fileName: String ->
        images[fileName] ?: BitmapFactory.decodeFile(CanvasStorage.imageFile(context, fileName).absolutePath)
            ?.asImageBitmap()?.also { images[fileName] = it }
    }

    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 4.dp,
        shadowElevation = 4.dp,
    ) {
        Box(modifier = Modifier.onSizeChanged { paneSize = it }) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(state) {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            val screenCenter = Offset(size.width / 2f, size.height / 2f)
                            fun world(at: Offset) = center + WorldPoint((at.x - screenCenter.x).toDouble(), (at.y - screenCenter.y).toDouble()) / viewScale
                            val tool = state.activeTool
                            var drawing = tool == Tool.PEN || tool == Tool.ERASER
                            fun draw(action: CanvasState.() -> Unit) = state.withInputScale(viewScale) { state.action() }
                            if (drawing) {
                                val at = world(down.position)
                                draw { if (tool == Tool.PEN) beginStroke(at) else beginErase(at) }
                            }
                            down.consume()
                            var previous: List<Offset> = listOf(down.position)
                            while (true) {
                                val event = awaitPointerEvent()
                                val pressed = event.changes.filter { it.pressed }
                                if (pressed.isEmpty()) break
                                val points = pressed.map { it.position }
                                if (points.size > 1 && drawing) {
                                    // A second finger turns drawing into navigating.
                                    draw { if (tool == Tool.PEN) endStroke() else endErase() }
                                    drawing = false
                                }
                                if (drawing) {
                                    val at = world(points[0])
                                    draw { if (tool == Tool.PEN) extendStroke(at) else continueErase(at) }
                                } else if (points.size == previous.size) {
                                    val centroid = points.reduce { a, b -> a + b } / points.size.toFloat()
                                    val before = previous.reduce { a, b -> a + b } / previous.size.toFloat()
                                    if (points.size > 1) {
                                        val spanNow = points.maxOf { (it - centroid).getDistance() }
                                        val spanBefore = previous.maxOf { (it - before).getDistance() }
                                        if (spanBefore > 0f && spanNow > 0f) {
                                            // Zoom about the fingers: the world point under them stays put.
                                            val anchor = world(before)
                                            viewScale = (viewScale * spanNow / spanBefore).coerceIn(1e-6, 1e6)
                                            center = anchor - WorldPoint((before.x - screenCenter.x).toDouble(), (before.y - screenCenter.y).toDouble()) / viewScale
                                        }
                                    }
                                    val moved = centroid - before
                                    center -= WorldPoint(moved.x.toDouble(), moved.y.toDouble()) / viewScale
                                }
                                previous = points
                                event.changes.forEach { it.consume() }
                            }
                            if (drawing) draw { if (tool == Tool.PEN) endStroke() else endErase() }
                        }
                    },
            ) {
                // Positioned in the composition's draw phase only: the camera is never shown elsewhere.
                camera.jumpTo(center, viewScale)
                val screenCenter = Offset(size.width / 2f, size.height / 2f)
                drawRect(camera.backgroundColor)
                val topLeft = camera.screenToWorld(Offset.Zero, screenCenter)
                val bottomRight = camera.screenToWorld(Offset(size.width, size.height), screenCenter)
                drawScene(camera, screenCenter, topLeft, bottomRight, textMeasurer, imageFor)
                // Where the main view is looking.
                val viewport = state.viewportSize
                if (viewport.width > 0f) {
                    val mainCenter = Offset(viewport.width / 2f, viewport.height / 2f)
                    val a = camera.worldToScreen(state.screenToWorld(Offset.Zero, mainCenter), screenCenter)
                    val b = camera.worldToScreen(state.screenToWorld(Offset(viewport.width, viewport.height), mainCenter), screenCenter)
                    drawRect(MAIN_VIEW_FRAME_COLOR, topLeft = a, size = androidx.compose.ui.geometry.Size(b.x - a.x, b.y - a.y), style = DrawStyle(width = 2f))
                }
            }
            Box(modifier = Modifier.align(Alignment.TopEnd)) {
                androidx.compose.foundation.layout.Row {
                    IconButton(onClick = {
                        // Fit the whole drawing, with a margin.
                        val bounds = state.contentBounds()
                        if (bounds != null && paneSize.width > 0 && paneSize.height > 0) {
                            val (min, max) = bounds
                            center = WorldPoint((min.x + max.x) / 2, (min.y + max.y) / 2)
                            viewScale = FIT_FILL * minOf(paneSize.width / (max.x - min.x).coerceAtLeast(1e-6), paneSize.height / (max.y - min.y).coerceAtLeast(1e-6))
                        }
                    }) {
                        Icon(Icons.Filled.FitScreen, contentDescription = stringResource(R.string.second_view_fit))
                    }
                    IconButton(onClick = { state.secondViewOpen = false }) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.second_view_close))
                    }
                }
            }
        }
    }
}

private const val FIT_FILL = 0.9 // the fitted drawing fills 90% of the pane

private val MAIN_VIEW_FRAME_COLOR = androidx.compose.ui.graphics.Color(0xFF4A7A52)
