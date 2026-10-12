package com.andene.fern.canvas

import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.andene.fern.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 3D painting (roadmap items 108 and 147), full screen: the model rendered under its light,
 * painted with one finger - colour, roughness or metallic, the brush keeping its on-screen size -
 * and turned (drag) and zoomed (pinch) with two. Below it: what to paint and with which colour or
 * value, the brush's size, softness and opacity, the light's direction and strength, and undo.
 * Done hands the painted model to [onSave]; Cancel drops the changes.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Model3dEditor(model: Model3d, initialColor: Color, onSave: (Model3d) -> Unit, onDismiss: () -> Unit) {
    val maps = remember { model.maps.copy() }
    // A posable hand's pose and the mesh built from it (roadmap item 146); other models keep theirs.
    var handPose by remember { mutableStateOf(model.handPose) }
    val mesh = remember(handPose) { handPose?.let(::handMesh) ?: model.mesh }
    var camera by remember { mutableStateOf(model.camera) }
    var light by remember { mutableStateOf(model.light) }
    var revision by remember { mutableIntStateOf(0) }
    var channel by remember { mutableStateOf(SurfaceChannel.COLOR) }
    var color by remember { mutableStateOf(initialColor) }
    var level by remember { mutableFloatStateOf(0.5f) }
    var brush by remember { mutableFloatStateOf(24f) }
    var softness by remember { mutableFloatStateOf(0.5f) }
    var opacity by remember { mutableFloatStateOf(1f) }
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    var showColorPicker by remember { mutableStateOf(false) }
    val undo = remember { ArrayDeque<SurfaceMaps>() }

    if (showColorPicker) {
        ColorPickerDialog(initialColor = color, onDismiss = { showColorPicker = false }, onColorSelected = {
            color = it
            showColorPicker = false
        })
    }

    // Rendered off the main thread at most RENDER_MAX_EDGE_PX on the long edge, then scaled up.
    val renderScale = if (viewSize.width <= 0) 1f else minOf(1f, RENDER_MAX_EDGE_PX / max(viewSize.width, viewSize.height).toFloat())
    val image by produceState<ImageBitmap?>(null, mesh, camera, light, revision, viewSize) {
        if (viewSize.width <= 0 || viewSize.height <= 0) return@produceState
        val w = max(1, (viewSize.width * renderScale).roundToInt())
        val h = max(1, (viewSize.height * renderScale).roundToInt())
        value = withContext(Dispatchers.Default) {
            val rendered = renderMesh(mesh, maps, camera, light, w, h)
            Bitmap.createBitmap(rendered.pixels, w, h, Bitmap.Config.ARGB_8888).asImageBitmap()
        }
    }

    fun paintAt(view: Offset) {
        val w = max(1, (viewSize.width * renderScale).roundToInt())
        val h = max(1, (viewSize.height * renderScale).roundToInt())
        val x = view.x * renderScale
        val y = view.y * renderScale
        val hit = pickMesh(mesh, camera, x.toDouble(), y.toDouble(), w, h) ?: return
        val radius = brush / 2 * renderScale * texelsPerPixel(mesh, hit.triangle, camera, w, h, maps.size)
        val argb = color.copy(alpha = opacity).toArgb()
        val target = (level * 255).roundToInt()
        paintDab(maps, hit.u, hit.v, radius, softness.toDouble(), channel, argb, target)
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column {
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Canvas(
                        Modifier
                            .fillMaxSize()
                            .pointerInput(mesh) { // re-keyed so a re-posed hand is painted, not the old mesh
                                awaitEachGesture {
                                    val down = awaitFirstDown()
                                    var last = down.position
                                    var painting = true
                                    var started = false
                                    var previousCentroid: Offset? = null
                                    var previousSpan = 0f
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val pressed = event.changes.filter { it.pressed }
                                        if (pressed.isEmpty()) break
                                        if (pressed.size >= 2) {
                                            // Two fingers turn and zoom; a stroke that had begun ends.
                                            painting = false
                                            val centroid = pressed.fold(Offset.Zero) { acc, c -> acc + c.position } / pressed.size.toFloat()
                                            val span = pressed.fold(0f) { acc, c -> acc + (c.position - centroid).getDistance() } / pressed.size
                                            previousCentroid?.let { before ->
                                                val delta = centroid - before
                                                camera = camera.orbit(-delta.x * ORBIT_RADIANS_PER_PX, delta.y * ORBIT_RADIANS_PER_PX)
                                                if (previousSpan > 1f && span > 1f) camera = camera.zoom((span / previousSpan).toDouble())
                                            }
                                            previousCentroid = centroid
                                            previousSpan = span
                                        } else if (painting) {
                                            val change = pressed[0]
                                            if (!started) {
                                                if (undo.size >= MAX_UNDO) undo.removeFirst()
                                                undo.addLast(maps.copy())
                                                paintAt(down.position)
                                                started = true
                                            }
                                            if (change.positionChanged()) {
                                                // Dabs a third of the brush apart along the drag.
                                                val step = max(1f, brush / 3)
                                                val distance = (change.position - last).getDistance()
                                                var travelled = step
                                                while (travelled <= distance) {
                                                    paintAt(last + (change.position - last) * (travelled / distance))
                                                    travelled += step
                                                }
                                                if (distance >= step) last = change.position
                                            }
                                            revision++
                                        }
                                        event.changes.forEach { it.consume() }
                                    }
                                }
                            },
                    ) {
                        viewSize = IntSize(size.width.toInt(), size.height.toInt())
                        image?.let { drawImage(it, dstOffset = IntOffset.Zero, dstSize = IntSize(size.width.toInt(), size.height.toInt())) }
                    }
                }
                Column(
                    Modifier
                        .heightIn(max = 256.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                ) {
                    FlowRow(verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
                        for ((option, label) in listOf(
                            SurfaceChannel.COLOR to R.string.model_paint_colour,
                            SurfaceChannel.ROUGHNESS to R.string.model_paint_roughness,
                            SurfaceChannel.METALLIC to R.string.model_paint_metallic,
                        )) {
                            FilterChip(selected = channel == option, onClick = { channel = option }, label = { Text(stringResource(label)) }, modifier = Modifier.padding(end = 8.dp))
                        }
                        if (channel == SurfaceChannel.COLOR) {
                            Surface(
                                onClick = { showColorPicker = true },
                                shape = CircleShape,
                                color = color,
                                modifier = Modifier.size(32.dp).align(Alignment.CenterVertically),
                            ) {}
                        }
                        IconButton(onClick = {
                            undo.removeLastOrNull()?.let { previous ->
                                previous.color.copyInto(maps.color)
                                previous.roughness.copyInto(maps.roughness)
                                previous.metallic.copyInto(maps.metallic)
                                revision++
                            }
                        }) { Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = stringResource(R.string.action_undo)) }
                    }
                    handPose?.let { pose -> HandPoseControls(pose) { handPose = it } }
                    if (channel != SurfaceChannel.COLOR) {
                        Text(stringResource(if (channel == SurfaceChannel.ROUGHNESS) R.string.model_roughness_value else R.string.model_metallic_value, (level * 100).roundToInt()))
                        Slider(value = level, onValueChange = { level = it })
                    }
                    Text(stringResource(R.string.model_brush_size, brush.roundToInt()))
                    Slider(value = brush, onValueChange = { brush = it.roundToInt().toFloat() }, valueRange = 2f..128f)
                    Text(stringResource(R.string.model_brush_softness, (softness * 100).roundToInt()))
                    Slider(value = softness, onValueChange = { softness = it })
                    Text(stringResource(R.string.model_brush_opacity, (opacity * 100).roundToInt()))
                    Slider(value = opacity, onValueChange = { opacity = it }, valueRange = 0.05f..1f)
                    Text(stringResource(R.string.model_light_direction, Math.toDegrees(light.yaw).roundToInt()))
                    Slider(value = light.yaw.toFloat(), onValueChange = { light = light.copy(yaw = it.toDouble()) }, valueRange = -Math.PI.toFloat()..Math.PI.toFloat())
                    Text(stringResource(R.string.model_light_height, Math.toDegrees(light.pitch).roundToInt()))
                    Slider(value = light.pitch.toFloat(), onValueChange = { light = light.copy(pitch = it.toDouble()) }, valueRange = -1.5f..1.5f)
                    Text(stringResource(R.string.model_light_strength, (light.intensity * 100).roundToInt()))
                    Slider(value = light.intensity.toFloat(), onValueChange = { light = light.copy(intensity = it.toDouble()) }, valueRange = 0f..1.5f)
                    Text(stringResource(R.string.model_light_ambient, (light.ambient * 100).roundToInt()))
                    Slider(value = light.ambient.toFloat(), onValueChange = { light = light.copy(ambient = it.toDouble()) }, valueRange = 0f..1f)
                    // The light's colour and the exposure (roadmap item 147).
                    FlowRow {
                        for ((tint, label) in LIGHT_TINTS) {
                            FilterChip(selected = light.color == tint, onClick = { light = light.copy(color = tint) }, label = { Text(stringResource(label)) }, modifier = Modifier.padding(end = 8.dp))
                        }
                    }
                    Text(stringResource(R.string.model_exposure, (light.exposure * 100).roundToInt()))
                    Slider(value = light.exposure.toFloat(), onValueChange = { light = light.copy(exposure = it.toDouble()) }, valueRange = 0.25f..2.5f)
                    ModelFileActions(
                        current = { Model3d(mesh, maps, camera, light, handPose) },
                        onTextureImported = { argb, w, h ->
                            if (undo.size >= MAX_UNDO) undo.removeFirst()
                            undo.addLast(maps.copy())
                            importColorMap(maps, argb, w, h)
                            revision++
                        },
                    )
                    Row(Modifier.fillMaxWidth()) {
                        TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
                        TextButton(onClick = { onSave(Model3d(mesh, maps, camera, light, handPose)) }) { Text(stringResource(R.string.model_done)) }
                    }
                }
            }
        }
    }
}

/** A model's picture for the canvas: rendered [POSTER_EDGE_PX] on its long edge from its saved camera, transparent around it. */
fun renderModelPoster(model: Model3d): Bitmap {
    val rendered = renderMesh(model.mesh, model.maps, model.camera, model.light, POSTER_EDGE_PX, POSTER_EDGE_PX)
    return Bitmap.createBitmap(rendered.pixels, POSTER_EDGE_PX, POSTER_EDGE_PX, Bitmap.Config.ARGB_8888)
}

private const val RENDER_MAX_EDGE_PX = 512f
// Photos are scanned no larger than this on their long edge: plenty for finding a hand.
private const val SCAN_MAX_EDGE_PX = 1024
private const val POSTER_EDGE_PX = 512
private const val ORBIT_RADIANS_PER_PX = 0.008
// Each step keeps a copy of the maps (6 MB at 1024 texels), so only a few are kept.
private const val MAX_UNDO = 6

/** Posing a hand (roadmap item 146): ready-made poses, which hand, each finger's curl and spread, and the thumb's reach across the palm. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HandPoseControls(pose: HandPose, onChange: (HandPose) -> Unit) {
    Text(stringResource(R.string.model_pose), style = MaterialTheme.typography.titleSmall)
    FlowRow {
        for (preset in HandPreset.entries) {
            FilterChip(selected = false, onClick = { onChange(preset.pose.copy(left = pose.left)) }, label = { Text(stringResource(handPresetLabel(preset))) }, modifier = Modifier.padding(end = 8.dp))
        }
        FilterChip(selected = pose.left, onClick = { onChange(pose.copy(left = !pose.left)) }, label = { Text(stringResource(R.string.model_left_hand)) })
    }
    // The hand pose scanner (roadmap item 146): a photo of a hand poses this one.
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var scanning by remember { mutableStateOf(false) }
    fun scan(bitmap: Bitmap?) {
        if (bitmap == null) return
        scanning = true
        scope.launch {
            val scanned = withContext(Dispatchers.Default) { scanHandPose(context, bitmap) }
            scanning = false
            if (scanned == null) Toast.makeText(context, context.getString(R.string.toast_no_hand), Toast.LENGTH_SHORT).show()
            else onChange(scanned)
        }
    }
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { scan(it) }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch { scan(withContext(Dispatchers.IO) { decodeDownsampled(context, uri, SCAN_MAX_EDGE_PX) }) }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = { takePhoto.launch(null) }, enabled = !scanning) { Text(stringResource(R.string.model_scan_camera)) }
        TextButton(onClick = { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, enabled = !scanning) {
            Text(stringResource(R.string.model_scan_photo))
        }
    }
    for (finger in Finger.entries) {
        Text(stringResource(R.string.model_finger_curl, stringResource(fingerLabel(finger)), (pose.curl(finger) * 100).roundToInt()))
        Slider(value = pose.curl(finger).toFloat(), onValueChange = { onChange(pose.withCurl(finger, it.toDouble())) })
        Text(stringResource(R.string.model_finger_spread, stringResource(fingerLabel(finger)), (pose.spread(finger) * 100).roundToInt()))
        Slider(value = pose.spread(finger).toFloat(), onValueChange = { onChange(pose.withSpread(finger, it.toDouble())) }, valueRange = -1f..1f)
    }
    Text(stringResource(R.string.model_thumb_across, (pose.thumbAcross * 100).roundToInt()))
    Slider(value = pose.thumbAcross.toFloat(), onValueChange = { onChange(pose.copy(thumbAcross = it.toDouble())) })
}

private fun fingerLabel(finger: Finger) = when (finger) {
    Finger.THUMB -> R.string.finger_thumb
    Finger.INDEX -> R.string.finger_index
    Finger.MIDDLE -> R.string.finger_middle
    Finger.RING -> R.string.finger_ring
    Finger.LITTLE -> R.string.finger_little
}

private fun handPresetLabel(preset: HandPreset) = when (preset) {
    HandPreset.RELAXED -> R.string.hand_relaxed
    HandPreset.OPEN -> R.string.hand_open
    HandPreset.FIST -> R.string.hand_fist
    HandPreset.POINT -> R.string.hand_point
    HandPreset.PEACE -> R.string.hand_peace
    HandPreset.THUMBS_UP -> R.string.hand_thumbs_up
    HandPreset.GRIP -> R.string.hand_grip
}

/**
 * Model files (roadmap item 147): export for augmented reality and 3D apps as .glb or .usdz,
 * export the texture maps, or bring a colour texture in from a picture.
 */
@Composable
private fun ModelFileActions(current: () -> Model3d, onTextureImported: (IntArray, Int, Int) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    fun writer(make: (Model3d) -> ByteArray) = { uri: android.net.Uri? ->
        if (uri != null) {
            val model = current()
            scope.launch {
                val ok = withContext(Dispatchers.IO) {
                    runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(make(model)) } != null }.getOrDefault(false)
                }
                Toast.makeText(context, context.getString(if (ok) R.string.toast_exported else R.string.toast_export_failed), Toast.LENGTH_SHORT).show()
            }
        }
    }
    val glb = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("model/gltf-binary"), writer(::exportGlb))
    val usdz = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("model/vnd.usdz+zip"), writer(::exportUsdz))
    val mapsZip = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip"), writer(::exportMapsZip))
    val texture = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val bitmap = withContext(Dispatchers.IO) { decodeDownsampled(context, uri, MODEL_MAP_SIZE) } ?: return@launch
            val pixels = IntArray(bitmap.width * bitmap.height).also { bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height) }
            onTextureImported(pixels, bitmap.width, bitmap.height)
        }
    }
    @OptIn(ExperimentalLayoutApi::class)
    FlowRow {
        TextButton(onClick = { glb.launch("model.glb") }) { Text(stringResource(R.string.model_export_glb)) }
        TextButton(onClick = { usdz.launch("model.usdz") }) { Text(stringResource(R.string.model_export_usdz)) }
        TextButton(onClick = { mapsZip.launch("model maps.zip") }) { Text(stringResource(R.string.model_export_maps)) }
        TextButton(onClick = { texture.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Text(stringResource(R.string.model_import_texture)) }
    }
}

// Light colours offered (roadmap item 147): neutral, warm, cool and a low evening sun.
private val LIGHT_TINTS = listOf(
    0xFFFFFFFF.toInt() to R.string.light_white,
    0xFFFFE0B2.toInt() to R.string.light_warm,
    0xFFBBDEFB.toInt() to R.string.light_cool,
    0xFFFFAB91.toInt() to R.string.light_sunset,
)
