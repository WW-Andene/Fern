package com.andene.fern.canvas

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FormatPaint
import androidx.compose.material.icons.filled.Toys
import androidx.compose.material.icons.filled.Transform
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.andene.fern.FernMessages
import com.andene.fern.FernSize
import com.andene.fern.FernSpace
import com.andene.fern.R
import kotlin.math.roundToInt

/** Actions the strip asks the toolbar to open, since their dialogs live there. */
object SelectionRequests {
    var transform by mutableStateOf(false)
    var makeObject by mutableStateOf(false)
}

/**
 * The selection's own actions, floating just above it (design roadmap D39): how many items are
 * selected, then duplicate, apply the current colour, transform, make an object and delete -
 * where the eye already is, instead of in a bar at the screen edge. Kept on screen when the
 * selection runs off it.
 */
@Composable
fun SelectionActionStrip(state: CanvasState) {
    if (state.activeTool != Tool.SELECT || state.selection.isEmpty() || state.isAdjustingShape) return
    val (min, _) = state.selectionBounds() ?: return
    val viewport = state.viewportSize
    val anchor = state.worldToScreen(min, Offset(viewport.width / 2f, viewport.height / 2f))
    var stripSize by androidx.compose.runtime.remember { mutableStateOf(IntSize.Zero) }
    val gap = with(LocalDensity.current) { FernSize.touchTarget.toPx() }
    val x = anchor.x.coerceIn(0f, (viewport.width - stripSize.width).coerceAtLeast(0f))
    val y = (anchor.y - stripSize.height - gap / 2).coerceIn(0f, (viewport.height - stripSize.height).coerceAtLeast(0f))
    val deleted = stringResource(R.string.msg_deleted)
    val tipMade = stringResource(R.string.selection_tip_made)
    val attachmentName = stringResource(R.string.rig_attachment_name)
    val undo = stringResource(R.string.action_undo)
    Surface(
        modifier = Modifier.offset { IntOffset(x.roundToInt(), y.roundToInt()) }.onSizeChanged { stripSize = it },
        shape = MaterialTheme.shapes.large,
        tonalElevation = FernSpace.xs,
        shadowElevation = FernSpace.sm,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(pluralStringResource(R.plurals.selection_count, state.selection.size, state.selection.size), modifier = Modifier.padding(horizontal = FernSpace.md))
            IconButton(onClick = { state.duplicateSelection() }) { Icon(Icons.Filled.ContentCopy, contentDescription = stringResource(R.string.tb_duplicate)) }
            IconButton(onClick = { state.recolorSelection() }) { Icon(Icons.Filled.FormatPaint, contentDescription = stringResource(R.string.tb_apply_colour)) }
            IconButton(onClick = { SelectionRequests.transform = true }) { Icon(Icons.Filled.Transform, contentDescription = stringResource(R.string.transform_title)) }
            IconButton(onClick = { SelectionRequests.makeObject = true }) { Icon(Icons.Filled.Toys, contentDescription = stringResource(R.string.object_title)) }
            // Your own drawing on the rig (roadmap item 353): it follows the bones from now on.
            if (state.rigEditor.rig.bones.isNotEmpty()) {
                IconButton(onClick = { state.attachSelectionToRig(attachmentName) }) { Icon(Icons.Filled.Animation, contentDescription = stringResource(R.string.rig_attach_selection)) }
            }
            // Your own drawing as a brush tip (roadmap item 347).
            IconButton(onClick = { if (state.brushTipFromSelection()) FernMessages.show(tipMade) }) { Icon(Icons.Filled.Brush, contentDescription = stringResource(R.string.selection_make_tip)) }
            IconButton(onClick = {
                state.deleteSelection()
                FernMessages.show(deleted, undo) { state.undo() }
            }) { Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.tb_delete)) }
        }
    }
}
