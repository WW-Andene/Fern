package com.andene.fern.canvas

import com.andene.fern.FernIcons
import com.andene.fern.FernPanel
import com.andene.fern.FernScale
import com.andene.fern.FernTextButton
import com.andene.fern.FernTextField

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import com.andene.fern.R
import java.util.Locale

/**
 * The inspector for the selection (design roadmap D47): everything about what's selected in one
 * panel, always the same one - its measurements, then its position, size and rotation (roadmap
 * item 99), its style, and its constraints (roadmap item 144). It replaces the separate
 * transform and constraints dialogs. Sizes are in the drawing scale's unit (roadmap item 143).
 * It closes itself when the selection empties.
 */
@Composable
fun SelectionInspector(state: CanvasState, onDismiss: () -> Unit) {
    val bounds = state.selectionBounds()
    LaunchedEffect(bounds == null) { if (bounds == null) onDismiss() }
    if (bounds == null) return
    FernPanel(
        scrollContent = true,
        onDismissRequest = {
            state.transformPreview = null
            onDismiss()
        },
        title = { Text(stringResource(R.string.inspector_title)) },
        peek = { Text(pluralStringResource(R.plurals.selection_count, state.selection.size, state.selection.size)) },
        text = {
            Column {
                MeasureSection(state, bounds)
                InspectorHeading(R.string.transform_title)
                // Keyed by the bounds: once applied, the fields start again from the new ones.
                androidx.compose.runtime.key(bounds) {
                    TransformFields(
                        bounds,
                        unitsPerWorld = state.drawingScale.factor,
                        unitSymbol = state.drawingScale.symbol,
                        onApply = { left, top, width, height, rotation ->
                            state.transformPreview = null
                            state.transformSelection(left, top, width, height, rotation)
                        },
                        onPreview = { left, top, width, height, rotation -> state.transformPreview = TransformPreview(left, top, width, height, rotation) },
                    )
                }
                InspectorHeading(R.string.inspector_style)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { state.recolorSelection() }) { Icon(FernIcons.FormatPaint, contentDescription = stringResource(R.string.tb_apply_colour)) }
                    IconButton(onClick = { state.applyWidthToSelection() }) { Icon(FernIcons.LineWeight, contentDescription = stringResource(R.string.tb_apply_width)) }
                    IconButton(onClick = { state.copySelectionStyle() }) { Icon(FernIcons.Style, contentDescription = stringResource(R.string.tb_copy_style)) }
                    IconButton(onClick = { state.pasteStyleToSelection() }, enabled = state.copiedStyle != null) {
                        Icon(FernIcons.ContentPasteGo, contentDescription = stringResource(R.string.tb_paste_style))
                    }
                }
                InspectorHeading(R.string.constraints_title)
                ConstraintsFields(state)
            }
        },
    )
}

@Composable
private fun InspectorHeading(@StringRes label: Int) {
    Text(
        stringResource(label),
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(top = FernScale.s12, bottom = FernScale.s4).semantics { heading() },
    )
}

/** How many items, how big, and how long their outlines run, in the drawing's unit. */
@Composable
private fun MeasureSection(state: CanvasState, bounds: Pair<WorldPoint, WorldPoint>) {
    val (min, max) = bounds
    val scale = state.drawingScale
    Text(pluralStringResource(R.plurals.selection_count, state.selection.size, state.selection.size))
    Text(stringResource(R.string.inspector_size, scale.formatLength(max.x - min.x), scale.formatLength(max.y - min.y)))
    Text(stringResource(R.string.inspector_length, scale.formatLength(outlineLength(state.selection))))
}

/** The total length of [strokes]' paths, in world units: what a measuring tape laid along them would read. */
internal fun outlineLength(strokes: List<Stroke>): Double = strokes.sumOf { stroke ->
    stroke.points.zipWithNext().sumOf { (a, b) -> kotlin.math.hypot(b.x - a.x, b.y - a.y) }
}

/**
 * Exact position, size and rotation (roadmap item 99): the selection's bounds as they are,
 * editable, width and height kept in proportion while the lock is on, previewed on the canvas as
 * typed (design roadmap D27). Apply calls [onApply] with the left, top, width, height (world units)
 * and the turn in degrees; a field that doesn't read as a number keeps its current value.
 */
@Composable
private fun TransformFields(
    bounds: Pair<WorldPoint, WorldPoint>,
    unitsPerWorld: Double,
    unitSymbol: String,
    onApply: (left: Double, top: Double, width: Double, height: Double, rotationDegrees: Double) -> Unit,
    onPreview: (left: Double, top: Double, width: Double, height: Double, rotationDegrees: Double) -> Unit,
) {
    val (min, max) = bounds
    val startWidth = max.x - min.x
    val startHeight = max.y - min.y
    fun format(value: Double) = String.format(Locale.ROOT, "%.1f", value)
    // Accepts either decimal separator, whatever the keyboard offers.
    fun parse(text: String) = text.trim().replace(',', '.').toDoubleOrNull()
    // Fields show and take the scale's unit; [onApply] gets world units.
    val k = unitsPerWorld
    fun parseWorld(text: String) = parse(text)?.let { it / k }
    var x by remember { mutableStateOf(format(min.x * k)) }
    var y by remember { mutableStateOf(format(min.y * k)) }
    var w by remember { mutableStateOf(format(startWidth * k)) }
    var h by remember { mutableStateOf(format(startHeight * k)) }
    var angle by remember { mutableStateOf("0") }
    var locked by remember { mutableStateOf(true) }
    val numeric = KeyboardOptions(keyboardType = KeyboardType.Decimal)
    fun values() = listOf(parseWorld(x) ?: min.x, parseWorld(y) ?: min.y, parseWorld(w) ?: startWidth, parseWorld(h) ?: startHeight, parse(angle) ?: 0.0)
    LaunchedEffect(x, y, w, h, angle) {
        val (l, t, wd, ht, r) = values()
        onPreview(l, t, wd, ht, r)
    }
    Column {
        Row {
            FernTextField(x, { x = it }, label = { Text("X ($unitSymbol)") }, singleLine = true, keyboardOptions = numeric, modifier = Modifier.width(FernScale.s128).padding(end = FernScale.s8))
            FernTextField(y, { y = it }, label = { Text("Y ($unitSymbol)") }, singleLine = true, keyboardOptions = numeric, modifier = Modifier.width(FernScale.s128))
        }
        Row {
            FernTextField(
                w,
                { text ->
                    w = text
                    if (locked && startWidth > 0) parse(text)?.let { h = format(it * startHeight / startWidth) }
                },
                label = { Text("${stringResource(R.string.transform_width)} ($unitSymbol)") }, singleLine = true, keyboardOptions = numeric,
                modifier = Modifier.width(FernScale.s128).padding(end = FernScale.s8),
            )
            FernTextField(
                h,
                { text ->
                    h = text
                    if (locked && startHeight > 0) parse(text)?.let { w = format(it * startWidth / startHeight) }
                },
                label = { Text("${stringResource(R.string.transform_height)} ($unitSymbol)") }, singleLine = true, keyboardOptions = numeric,
                modifier = Modifier.width(FernScale.s128),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = locked, onCheckedChange = { locked = it })
            Text(stringResource(R.string.transform_keep_proportions))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            FernTextField(angle, { angle = it }, label = { Text(stringResource(R.string.transform_rotate)) }, singleLine = true, keyboardOptions = numeric, modifier = Modifier.width(FernScale.s128))
            FernTextButton(onClick = {
                val (l, t, wd, ht, r) = values()
                onApply(l, t, wd, ht, r)
            }) { Text(stringResource(R.string.action_apply)) }
        }
    }
}

/**
 * Constraints (roadmap item 144): how the selection follows its frame when the frame is resized,
 * across and down - pinned to an edge, to both (stretching), to the centre, or scaled. A tap sets
 * them on every selected stroke at once (one undo step).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConstraintsFields(state: CanvasState) {
    val current = state.selectionConstraints
    Text(stringResource(R.string.constraints_horizontal))
    FlowRow {
        for (pin in ConstraintPin.entries) {
            FilterChip(
                selected = current.horizontal == pin,
                onClick = { state.setSelectionConstraints(current.copy(horizontal = pin)) },
                label = { Text(stringResource(pinLabel(pin, across = true))) },
                modifier = Modifier.padding(end = FernScale.s8),
            )
        }
    }
    Text(stringResource(R.string.constraints_vertical))
    FlowRow {
        for (pin in ConstraintPin.entries) {
            FilterChip(
                selected = current.vertical == pin,
                onClick = { state.setSelectionConstraints(current.copy(vertical = pin)) },
                label = { Text(stringResource(pinLabel(pin, across = false))) },
                modifier = Modifier.padding(end = FernScale.s8),
            )
        }
    }
}

@StringRes
private fun pinLabel(pin: ConstraintPin, across: Boolean) = when (pin) {
    ConstraintPin.START -> if (across) R.string.constraint_left else R.string.constraint_top
    ConstraintPin.END -> if (across) R.string.constraint_right else R.string.constraint_bottom
    ConstraintPin.BOTH -> if (across) R.string.constraint_left_right else R.string.constraint_top_bottom
    ConstraintPin.CENTER -> R.string.constraint_centre
    ConstraintPin.SCALE -> R.string.constraint_scale
}
