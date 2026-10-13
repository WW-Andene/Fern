package com.andene.fern.canvas

import com.andene.fern.FernIcons

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStyle
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import com.andene.fern.FernMessages
import com.andene.fern.FernPanel
import com.andene.fern.FernScale
import com.andene.fern.FernSize
import com.andene.fern.R
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

/**
 * The brush library (design roadmap D44): every pen kind and saved stamp brush as a tile with a
 * sample stroke in the current colour, filtered by name and by tag; a tap picks it, the pin
 * puts it in a free favourite slot, and Edit opens the brush editor beside the canvas.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BrushLibrary(state: CanvasState, onEditStamp: () -> Unit, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var tag by remember { mutableStateOf<String?>(null) }
    val pinnedLabel = stringResource(R.string.brush_pinned)
    val slotsFull = stringResource(R.string.brush_slots_full)
    fun pin() {
        val free = state.toolSlots.indexOfFirst { it == null }
        if (free == -1) FernMessages.show(slotsFull) else {
            state.saveToolSlot(free)
            FernMessages.show(pinnedLabel)
        }
    }
    FernPanel(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.brush_library)) },
        peek = { Text(stringResource(penTypeLabel(state.activePenType))) },
        onFilter = { query = it },
        text = {
            Column(Modifier.heightIn(max = FernSize.dialogMaxHeight).verticalScroll(rememberScrollState())) {
                FlowRow {
                    for (type in PenType.entries - PenType.STAMP) {
                        val name = stringResource(penTypeLabel(type))
                        if (query.isNotBlank() && !name.contains(query.trim(), ignoreCase = true)) continue
                        BrushTile(name, state.activePenType == type && state.activeTool == Tool.PEN, onPick = {
                            state.activePenType = type
                            state.activeTool = Tool.PEN
                        }, onPin = {
                            state.activePenType = type
                            pin()
                        }) { PenSample(type, state) }
                    }
                }
                val tags = state.brushPresets.flatMap { it.tags }.distinct().sorted()
                if (tags.isNotEmpty()) FlowRow(Modifier.padding(top = FernScale.s8)) {
                    for (t in tags) FilterChip(selected = tag == t, onClick = { tag = if (tag == t) null else t }, label = { Text(t) }, modifier = Modifier.padding(end = FernScale.s8))
                }
                FlowRow {
                    for (preset in state.brushPresets) {
                        if (tag != null && tag !in preset.tags) continue
                        if (query.isNotBlank() && !preset.name.contains(query.trim(), ignoreCase = true) && preset.tags.none { it.contains(query.trim(), ignoreCase = true) }) continue
                        BrushTile(preset.name, state.activePenType == PenType.STAMP && state.activeBrush == preset.brush, onPick = {
                            state.setBrush(preset.brush)
                            state.activePenType = PenType.STAMP
                            state.activeTool = Tool.PEN
                        }, onPin = {
                            state.setBrush(preset.brush)
                            state.activePenType = PenType.STAMP
                            pin()
                        }) { BrushPreview(preset.brush, state.activeColor, height = FernScale.s48) }
                    }
                }
                IconButton(onClick = onEditStamp) { Icon(FernIcons.Edit, contentDescription = stringResource(R.string.brush_settings)) }
            }
        },
    )
}

@Composable
private fun BrushTile(name: String, selected: Boolean, onPick: () -> Unit, onPin: () -> Unit, preview: @Composable () -> Unit) {
    val border = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Column(
        Modifier
            .padding(end = FernScale.s8, bottom = FernScale.s8)
            .width(FernScale.s128)
            .border(if (selected) FernScale.s2 else FernScale.s1, border, MaterialTheme.shapes.small)
            .semantics(mergeDescendants = true) {
                contentDescription = name
                this.selected = selected
            }
            .clickable(onClick = onPick)
            .padding(FernScale.s4),
    ) {
        preview()
        androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text(name, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = onPin) { Icon(FernIcons.PushPin, contentDescription = stringResource(R.string.brush_pin, name)) }
        }
    }
}

/** A sample S-curve suggesting each pen kind's look: width, transparency and ends. */
@Composable
private fun PenSample(type: PenType, state: CanvasState) {
    val color = state.activeColor
    Canvas(Modifier.fillMaxWidth().height(FernScale.s48)) {
        val margin = FernScale.s8.toPx()
        val path = Path()
        for (k in 0..32) {
            val t = k / 32f
            val p = Offset(margin + t * (size.width - 2 * margin), size.height / 2f + sin(t * 2f * PI.toFloat()) * (size.height / 2f - margin))
            if (k == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
        }
        val (width, alpha, cap) = when (type) {
            PenType.MARKER -> Triple(4f, 1f, StrokeCap.Round)
            PenType.PENCIL -> Triple(2f, 0.7f, StrokeCap.Round)
            PenType.HIGHLIGHTER -> Triple(12f, 0.35f, StrokeCap.Square)
            PenType.CALLIGRAPHY -> Triple(6f, 1f, StrokeCap.Butt)
            PenType.AIRBRUSH -> Triple(12f, 0.25f, StrokeCap.Round)
            PenType.CRAYON -> Triple(6f, 0.8f, StrokeCap.Round)
            PenType.PIXEL -> Triple(4f, 1f, StrokeCap.Square)
            PenType.STAMP -> Triple(4f, 1f, StrokeCap.Round)
        }
        drawPath(path, color.copy(alpha = alpha), style = DrawStyle(width = width.dp.toPx(), cap = cap))
    }
}
