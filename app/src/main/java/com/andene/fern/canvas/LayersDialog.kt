package com.andene.fern.canvas

import com.andene.fern.FernIcons

import com.andene.fern.FernTextButton
import com.andene.fern.FernSlider
import com.andene.fern.FernTextField
import com.andene.fern.ConfirmIrreversible
import com.andene.fern.FernPanel
import com.andene.fern.PanelRow
import kotlin.math.roundToInt
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import com.andene.fern.confirmationSkipped

import com.andene.fern.FernMessages

import com.andene.fern.FernScale

import androidx.compose.material3.MaterialTheme
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import android.provider.OpenableColumns
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.rememberCoroutineScope
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import android.net.Uri
import android.content.Intent
import androidx.compose.material3.LocalContentColor
import com.andene.fern.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import com.andene.fern.FernMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight

/**
 * The layer stack, listed top layer first like every paint app. Tap a name to make it the
 * active layer (new strokes go there); per layer: show/hide, lock, move up/down, rename,
 * delete. The active layer also gets an opacity slider and merge-down.
 */
@Composable
fun LayersDialog(state: CanvasState, onDismiss: () -> Unit) {
    var renamingId by remember { mutableStateOf<String?>(null) }
    // The adjustment layer being edited, and its adjustment before editing (restored on cancel).
    var editingAdjustment by remember { mutableStateOf<Pair<String, Adjustment>?>(null) }
    editingAdjustment?.let { (id, before) ->
        AdjustmentsDialog(
            initial = before,
            onPreview = { a -> state.updateLayer(id) { it.copy(adjustment = a) } },
            onApply = { a -> state.updateLayer(id) { it.copy(adjustment = a) }; editingAdjustment = null },
            onCancel = { state.updateLayer(id) { it.copy(adjustment = before) }; editingAdjustment = null },
        )
    }
    var renameText by remember { mutableStateOf("") }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // File layers (roadmap item 138): the picked image's permission is kept, so it reloads later.
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        scope.launch {
            val image = withContext(Dispatchers.IO) { readFileLayerImage(context, uri) }
            if (image == null) {
                FernMessages.show(context.getString(R.string.layers_file_unreadable))
                return@launch
            }
            val name = runCatching {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
            }.getOrNull()?.substringBeforeLast('.') ?: ""
            state.addFileLayer(name, uri.toString(), image.argb, image.width, image.height)
        }
    }
    fun reload(layer: Layer) {
        val source = layer.fileSource ?: return
        scope.launch {
            val image = withContext(Dispatchers.IO) { readFileLayerImage(context, Uri.parse(source)) }
            if (image == null) FernMessages.show(context.getString(R.string.layers_file_unreadable))
            else state.reloadFileLayer(layer.id, image.argb, image.width, image.height)
        }
    }

    var confirmingDelete by remember { mutableStateOf<String?>(null) }
    confirmingDelete?.let { id ->
        ConfirmIrreversible(
            CONFIRM_RASTER_DELETE, stringResource(R.string.confirm_raster_delete), stringResource(R.string.tb_delete),
            onConfirm = {
                state.deleteLayer(id)
                confirmingDelete = null
            },
            onDismiss = { confirmingDelete = null },
        )
    }
    var layerFilter by remember { mutableStateOf("") }
    val deletedLabel = stringResource(R.string.layers_deleted)
    val undoLabel = stringResource(R.string.action_undo)
    fun delete(layer: Layer) {
        // A raster layer's pixels don't come back with undo: ask first (design roadmap D26); other layers offer Undo.
        if (layer.raster && !confirmationSkipped(context, CONFIRM_RASTER_DELETE)) {
            confirmingDelete = layer.id
        } else {
            state.deleteLayer(layer.id)
            if (!layer.raster) FernMessages.show(deletedLabel, undoLabel) { state.undo() }
        }
    }
    FernPanel(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.layers_title)) },
        peek = { state.layers.firstOrNull { it.id == state.activeLayerId }?.let { Text(it.name) } },
        // The panel's main action in its header, and a filter by name (design roadmap D42, D43).
        headerAction = {
            IconButton(onClick = { state.addLayer() }) { Icon(FernIcons.Add, contentDescription = stringResource(R.string.layers_new)) }
        },
        onFilter = { layerFilter = it },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = FernScale.s384)
                    .verticalScroll(rememberScrollState()),
            ) {
                val skins = state.skins
                if (skins.isNotEmpty()) {
                    Text(stringResource(R.string.layers_skins), style = MaterialTheme.typography.labelLarge)
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        for (skin in skins) {
                            FilterChip(
                                selected = state.activeSkin == skin,
                                onClick = { state.applySkin(skin) },
                                label = { Text(skin) },
                                modifier = Modifier.padding(end = FernScale.s4),
                            )
                        }
                    }
                }
                FernTextButton(onClick = { pickFile.launch(arrayOf("image/*")) }) { Text(stringResource(R.string.layers_add_file)) }
                LayerCompsSection(state)
                if (state.layers.any { it.hdr }) HdrSection(state)
                for (layer in state.layers.reversed()) {
                    if (layerFilter.isNotBlank() && !layer.name.contains(layerFilter.trim(), ignoreCase = true)) continue
                    val active = layer.id == state.activeLayerId
                    // Swipe left to delete, tap to make active (design roadmap D43).
                    PanelRow(
                        onClick = { state.activeLayerId = layer.id },
                        onSwipeDelete = if (state.layers.size > 1) { { delete(layer) } } else null,
                        deleteLabel = stringResource(R.string.layers_delete),
                    ) {
                        LayerThumbnail(state, layer.id)
                        IconButton(onClick = { state.updateLayer(layer.id) { it.copy(visible = !it.visible) } }) {
                            Icon(
                                if (layer.visible) FernIcons.Visibility else FernIcons.VisibilityOff,
                                contentDescription = if (layer.visible) stringResource(R.string.layers_hide) else stringResource(R.string.layers_show),
                            )
                        }
                        IconButton(onClick = { state.updateLayer(layer.id) { it.copy(reference = !it.reference) } }) {
                            Icon(
                                FernIcons.FormatColorFill,
                                contentDescription = if (layer.reference) stringResource(R.string.layers_reference_on) else stringResource(R.string.layers_reference_off),
                                tint = if (layer.reference) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                            )
                        }
                        IconButton(onClick = { state.updateLayer(layer.id) { it.copy(locked = !it.locked) } }) {
                            Icon(
                                if (layer.locked) FernIcons.Lock else FernIcons.LockOpen,
                                contentDescription = if (layer.locked) stringResource(R.string.layers_unlock) else stringResource(R.string.layers_lock),
                            )
                        }
                        if (renamingId == layer.id) {
                            FernTextField(
                                value = renameText,
                                onValueChange = { renameText = it },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = {
                                if (renameText.isNotBlank()) state.updateLayer(layer.id) { it.copy(name = renameText.trim()) }
                                renamingId = null
                            }) {
                                Icon(FernIcons.Check, contentDescription = stringResource(R.string.action_confirm_rename))
                            }
                        } else {
                            // Drag sideways on the name to set the layer's opacity, shown beside it (design roadmap D43).
                            val opacityLabel = stringResource(R.string.layers_opacity_drag)
                            Text(
                                text = if (layer.opacity < 1f) "${layer.name} · ${(layer.opacity * 100).roundToInt()} %" else layer.name,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier
                                    .weight(1f)
                                    .semantics { stateDescription = opacityLabel }
                                    .pointerInput(layer.id) {
                                        detectHorizontalDragGestures { change, dx ->
                                            change.consume()
                                            state.updateLayer(layer.id) { it.copy(opacity = (it.opacity + dx / OPACITY_DRAG_PX).coerceIn(0f, 1f)) }
                                        }
                                    }
                                    .padding(vertical = FernScale.s12),
                            )
                            IconButton(onClick = {
                                renamingId = layer.id
                                renameText = layer.name
                            }) {
                                Icon(FernIcons.Edit, contentDescription = stringResource(R.string.layers_rename))
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (layer.fileSource != null) {
                            IconButton(onClick = { reload(layer) }) {
                                Icon(FernIcons.Refresh, contentDescription = stringResource(R.string.layers_reload_file))
                            }
                        }
                        IconButton(onClick = { state.moveLayer(layer.id, up = true) }, enabled = layer != state.layers.last()) {
                            Icon(FernIcons.ArrowUpward, contentDescription = stringResource(R.string.layers_up))
                        }
                        IconButton(onClick = { state.moveLayer(layer.id, up = false) }, enabled = layer != state.layers.first()) {
                            Icon(FernIcons.ArrowDownward, contentDescription = stringResource(R.string.layers_down))
                        }
                        IconButton(onClick = { state.mergeLayerDown(layer.id) }, enabled = layer != state.layers.first()) {
                            Icon(FernIcons.MergeType, contentDescription = stringResource(R.string.layers_merge))
                        }
                        IconButton(onClick = { delete(layer) }, enabled = state.layers.size > 1) {
                            Icon(FernIcons.Delete, contentDescription = stringResource(R.string.layers_delete))
                        }
                    }
                    if (active) {
                        // Blend mode (roadmap item 93), and an adjustment layer's settings.
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            var blendMenu by remember { mutableStateOf(false) }
                            Box {
                                FernTextButton(onClick = { blendMenu = true }) {
                                    Text(stringResource(R.string.layers_blend) + ": " + stringResource(layer.blend.label))
                                }
                                DropdownMenu(expanded = blendMenu, onDismissRequest = { blendMenu = false }) {
                                    for (mode in LayerBlend.entries) {
                                        FernMenuItem(
                                            text = { Text(stringResource(mode.label)) },
                                            onClick = {
                                                state.updateLayer(layer.id) { it.copy(blend = mode) }
                                                blendMenu = false
                                            },
                                        )
                                    }
                                }
                            }
                            val adjustment = layer.adjustment
                            if (adjustment != null) {
                                FernTextButton(onClick = { editingAdjustment = layer.id to adjustment }) { Text(stringResource(R.string.layers_edit_adjustment)) }
                            }
                        }
                        // Masks, clipping and alpha lock (roadmap item 91).
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { if (layer.masked) state.removeLayerMask(layer.id) else state.addLayerMask(layer.id) }) {
                                Icon(
                                    FernIcons.Gradient,
                                    contentDescription = stringResource(if (layer.masked) R.string.layers_mask_remove else R.string.layers_mask_add),
                                    tint = if (layer.masked) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                                )
                            }
                            if (layer.masked) {
                                IconButton(onClick = { state.editingMask = !state.editingMask }) {
                                    Icon(
                                        FernIcons.Brush,
                                        contentDescription = stringResource(if (state.editingMask) R.string.layers_mask_edit_stop else R.string.layers_mask_edit),
                                        tint = if (state.editingMask) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                                    )
                                }
                            }
                            IconButton(onClick = { state.updateLayer(layer.id) { it.copy(clipped = !it.clipped) } }, enabled = layer != state.layers.first()) {
                                Icon(
                                    FernIcons.SubdirectoryArrowRight,
                                    contentDescription = stringResource(if (layer.clipped) R.string.layers_clip_off else R.string.layers_clip_on),
                                    tint = if (layer.clipped) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                                )
                            }
                            if (layer.raster) {
                                IconButton(onClick = { state.updateLayer(layer.id) { it.copy(alphaLocked = !it.alphaLocked) } }) {
                                    Icon(
                                        FernIcons.Opacity,
                                        contentDescription = stringResource(if (layer.alphaLocked) R.string.layers_alpha_unlock else R.string.layers_alpha_lock),
                                        tint = if (layer.alphaLocked) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                                    )
                                }
                            }
                        }
                    }
                    if (active) {
                        FernSlider(
                            value = layer.opacity,
                            onValueChange = { value -> state.updateLayer(layer.id) { it.copy(opacity = value) } },
                            valueRange = 0f..1f,
                        )
                        // Blank for a layer that's always shown; a name puts it in that skin.
                        FernTextField(
                            value = layer.skin.orEmpty(),
                            onValueChange = { name -> state.updateLayer(layer.id) { it.copy(skin = name.trim().ifEmpty { null }) } },
                            label = { Text(stringResource(R.string.layers_skin)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        },
        confirmButton = {
            Row {
                FernTextButton(onClick = { state.deleteEmptyLayers() }) { Text(stringResource(R.string.layers_remove_empty)) }
                FernTextButton(onClick = { state.deleteHiddenLayers() }) { Text(stringResource(R.string.layers_delete_hidden)) }
                // Raster layers hold pixels; 16 bits per channel for smooth gradients.
                FernTextButton(onClick = { state.addRasterLayer(8) }) { Text(stringResource(R.string.layers_new_raster)) }
                FernTextButton(onClick = { state.addRasterLayer(16) }) { Text(stringResource(R.string.layers_new_raster_16)) }
                FernTextButton(onClick = { state.addRasterLayer(16, hdr = true) }) { Text(stringResource(R.string.layers_new_hdr)) }
                FernTextButton(onClick = {
                    state.addAdjustmentLayer(Levels())
                    editingAdjustment = state.activeLayerId to Levels()
                }) { Text(stringResource(R.string.layers_new_adjustment)) }
            }
        },
    )
}

/**
 * Layer comps (roadmap item 123): a chip per saved comp, ticked when the layers are shown that
 * way; tapping one applies it and opens its name, update and delete controls. "Save comp"
 * records the layers as they're shown now.
 */
@Composable
private fun LayerCompsSection(state: CanvasState) {
    var chosenId by remember { mutableStateOf<String?>(null) }
    val current = state.currentLayerCompId
    Text(stringResource(R.string.layers_comps), style = MaterialTheme.typography.labelLarge)
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (comp in state.layerComps) {
            FilterChip(
                selected = comp.id == current,
                onClick = { state.applyLayerComp(comp.id); chosenId = comp.id },
                label = { Text(comp.name) },
                modifier = Modifier.padding(end = FernScale.s4),
            )
        }
        val newName = stringResource(R.string.layers_comp_default_name, state.layerComps.size + 1)
        FernTextButton(onClick = { chosenId = state.saveLayerComp(newName) }) {
            Text(stringResource(R.string.layers_comp_save))
        }
    }
    val chosen = state.layerComps.firstOrNull { it.id == chosenId } ?: return
    Row(verticalAlignment = Alignment.CenterVertically) {
        FernTextField(
            value = chosen.name,
            onValueChange = { name -> state.renameLayerComp(chosen.id, name) },
            label = { Text(stringResource(R.string.layers_comp_name)) },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = { state.updateLayerComp(chosen.id) }, enabled = chosen.id != current) {
            Icon(FernIcons.Save, contentDescription = stringResource(R.string.layers_comp_update))
        }
        IconButton(onClick = { state.deleteLayerComp(chosen.id); chosenId = null }) {
            Icon(FernIcons.Delete, contentDescription = stringResource(R.string.layers_comp_delete))
        }
    }
}

/**
 * HDR layers (roadmap item 139): the view transform they're shown through - exposure in stops
 * and how light past white is brought into the screen's range - and how bright the pen paints
 * on them.
 */
@Composable
private fun HdrSection(state: CanvasState) {
    val view = state.hdrView
    Text(stringResource(R.string.hdr_title), style = MaterialTheme.typography.labelLarge)
    Text(stringResource(R.string.hdr_exposure, "%+.1f".format(view.exposureStops)))
    FernSlider(value = view.exposureStops, onValueChange = { state.changeHdrView(view.copy(exposureStops = it)) }, valueRange = -6f..6f)
    Row {
        for ((mode, label) in listOf(ToneMap.CLIP to R.string.hdr_clip, ToneMap.FILMIC to R.string.hdr_filmic)) {
            FilterChip(selected = view.toneMap == mode, onClick = { state.changeHdrView(view.copy(toneMap = mode)) }, label = { Text(stringResource(label)) }, modifier = Modifier.padding(end = FernScale.s4))
        }
    }
    Text(stringResource(R.string.hdr_intensity, "%.1f".format(state.hdrIntensity)))
    FernSlider(value = state.hdrIntensity, onValueChange = { state.hdrIntensity = it }, valueRange = 1f..16f)
}

private const val CONFIRM_RASTER_DELETE = "delete_raster_layer"

// Dragging this far sideways on a layer's name takes its opacity from clear to solid.
private const val OPACITY_DRAG_PX = 384f
