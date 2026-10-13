package com.andene.fern.canvas

import com.andene.fern.FernPanel

import com.andene.fern.FernScale

import com.andene.fern.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlin.math.PI
import kotlin.math.sin

/**
 * Brush editor for the stamp pen: pick a tip and tune spacing, scatter, size and angle jitter
 * and direction-following, with a live preview stroke. Edits stay local until Apply, so Cancel
 * (or dismissing) leaves the brush as it was; Reset returns to the default brush.
 *
 * Presets: tapping a saved preset loads it into the editor (still applied only by Apply); the
 * loaded preset then shows a close icon that deletes it, and "Save as preset" stores the brush being edited under a name -
 * saving under an existing name replaces that preset.
 */
@Composable
fun BrushEditorDialog(
    initialBrush: StampBrush,
    color: Color,
    onApply: (StampBrush) -> Unit,
    onDismiss: () -> Unit,
    presets: List<BrushPreset> = emptyList(),
    onSavePreset: (name: String, brush: StampBrush) -> Unit = { _, _ -> },
    onDeletePreset: (name: String) -> Unit = {},
    onSetPresetTags: (name: String, tags: List<String>) -> Unit = { _, _ -> },
) {
    var brush by remember { mutableStateOf(initialBrush) }
    // Brush tags (roadmap item 140): which tag the preset row is filtered to, and a new tag being typed.
    var tagFilter by remember { mutableStateOf<String?>(null) }
    var newTag by remember { mutableStateOf("") }
    var namingPreset by remember { mutableStateOf(false) }
    var presetName by remember { mutableStateOf("") }
    val context = LocalContext.current
    val pickTipImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val mask = uri?.let { decodeTipMask(context, it) }
        if (mask != null) brush = brush.copy(tip = StampTip.IMAGE, mask = mask)
    }
    FernPanel(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.brush_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                BrushPreview(brush, color)
                if (presets.isNotEmpty()) {
                    Text(stringResource(R.string.brush_presets), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = FernScale.s8))
                    val tags = presets.flatMap { it.tags }.distinct().sorted()
                    if (tags.isNotEmpty()) {
                        Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                            FilterChip(selected = tagFilter == null, onClick = { tagFilter = null }, label = { Text(stringResource(R.string.brush_tag_all)) }, modifier = Modifier.padding(end = FernScale.s4))
                            for (tag in tags) {
                                FilterChip(selected = tagFilter == tag, onClick = { tagFilter = if (tagFilter == tag) null else tag }, label = { Text(tag) }, modifier = Modifier.padding(end = FernScale.s4))
                            }
                        }
                    }
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        for (preset in presets.filter { tagFilter == null || tagFilter in it.tags }) {
                            val loaded = preset.brush == brush
                            InputChip(
                                selected = loaded,
                                onClick = { brush = preset.brush },
                                label = { Text(preset.name) },
                                // Only the loaded preset shows its delete icon: a small icon's touch
                                // target is enlarged to 48 dp and would otherwise cover most of the
                                // chip, turning a tap meant to load a preset into deleting it.
                                trailingIcon = if (loaded) {
                                    {
                                        Icon(
                                            Icons.Filled.Close,
                                            contentDescription = stringResource(R.string.brush_delete_preset, preset.name),
                                            modifier = Modifier.minimumInteractiveComponentSize().size(FernScale.s16).clickable { onDeletePreset(preset.name) },
                                        )
                                    }
                                } else {
                                    null
                                },
                                modifier = Modifier.padding(end = FernScale.s4),
                            )
                        }
                    }
                }
                // The loaded preset's tags: tap one to remove it, or add another.
                presets.firstOrNull { it.brush == brush }?.let { loaded ->
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                        for (tag in loaded.tags) {
                            InputChip(
                                selected = false,
                                onClick = { onSetPresetTags(loaded.name, loaded.tags - tag) },
                                label = { Text(tag) },
                                trailingIcon = { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.brush_remove_tag, tag), modifier = Modifier.size(FernScale.s16)) },
                                modifier = Modifier.padding(end = FernScale.s4),
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = newTag,
                            onValueChange = { newTag = it },
                            label = { Text(stringResource(R.string.brush_add_tag)) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = {
                            if (newTag.isNotBlank()) onSetPresetTags(loaded.name, loaded.tags + newTag.trim())
                            newTag = ""
                        }) { Text(stringResource(R.string.brush_tag_add_button)) }
                    }
                }
                Row(modifier = Modifier.padding(vertical = FernScale.s8).horizontalScroll(rememberScrollState())) {
                    for (tip in StampTip.entries) {
                        FilterChip(
                            selected = brush.tip == tip,
                            // An image tip needs a picture first; picking one selects the tip.
                            onClick = { if (tip == StampTip.IMAGE && brush.mask == null) pickTipImage.launch("image/*") else brush = brush.copy(tip = tip) },
                            label = { Text(stringResource(tip.label)) },
                            modifier = Modifier.padding(end = FernScale.s4),
                        )
                    }
                }
                if (brush.tip == StampTip.IMAGE) {
                    TextButton(onClick = { pickTipImage.launch("image/*") }) { Text(stringResource(R.string.brush_pick_image)) }
                }
                BrushSlider(stringResource(R.string.brush_spacing), brush.spacing, StampBrush.MIN_SPACING..StampBrush.MAX_SPACING) { brush = brush.copy(spacing = it) }
                BrushSlider(stringResource(R.string.brush_scatter), brush.scatter, 0f..StampBrush.MAX_SCATTER) { brush = brush.copy(scatter = it) }
                BrushSlider(stringResource(R.string.brush_size_jitter), brush.sizeJitter, 0f..1f) { brush = brush.copy(sizeJitter = it) }
                BrushSlider(stringResource(R.string.brush_angle_jitter), brush.angleJitter, 0f..1f) { brush = brush.copy(angleJitter = it) }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = FernScale.s8),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.brush_follow), modifier = Modifier.weight(1f))
                    Switch(checked = brush.followDirection, onCheckedChange = { brush = brush.copy(followDirection = it) })
                }
                Row {
                    TextButton(onClick = { brush = StampBrush() }) { Text(stringResource(R.string.brush_reset)) }
                    TextButton(onClick = { namingPreset = true }) { Text(stringResource(R.string.brush_save_preset)) }
                }
                if (namingPreset) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = presetName,
                            onValueChange = { presetName = it },
                            label = { Text(stringResource(R.string.brush_preset_name)) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            onClick = {
                                onSavePreset(presetName, brush)
                                presetName = ""
                                namingPreset = false
                            },
                            enabled = presetName.isNotBlank(),
                        ) { Text(stringResource(R.string.action_save)) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onApply(brush) }) { Text(stringResource(R.string.action_apply)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun BrushSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Text(stringResource(R.string.brush_slider_value, label, value), style = MaterialTheme.typography.bodySmall)
    Slider(value = value, onValueChange = onChange, valueRange = range)
}

/** A sample S-curve drawn with [brush] in [color], redrawn on every change. */
@Composable
internal fun BrushPreview(brush: StampBrush, color: Color, height: androidx.compose.ui.unit.Dp = FernScale.s96) {
    val outline = MaterialTheme.colorScheme.outline
    val previewLabel = stringResource(R.string.brush_preview)
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .border(FernScale.s1, outline)
            .semantics { contentDescription = previewLabel },
    ) {
        val margin = FernScale.s24.toPx()
        val width = FernScale.s16.toPx()
        val path = (0..64).map { k ->
            val t = k / 64f
            Offset(margin + t * (size.width - 2 * margin), size.height / 2f + sin(t * 2f * PI.toFloat()) * (size.height / 2f - margin))
        }
        for (stamp in stampPlacements(path, width, brush)) drawStamp(brush.tip, stamp, color, mask = brush.mask)
    }
}

/** Reads a picked picture as an image tip, decoded at reduced size since the tip is only [TIP_MASK_SIZE] wide. */
private fun decodeTipMask(context: Context, uri: Uri): TipMask? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= TIP_MASK_SIZE * 2) sample *= 2
    val bitmap = context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
    } ?: return null
    val pixels = IntArray(bitmap.width * bitmap.height)
    bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    tipMaskFromArgb(pixels, bitmap.width, bitmap.height).also { bitmap.recycle() }
}.getOrNull()

