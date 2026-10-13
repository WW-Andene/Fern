package com.andene.fern.canvas

import com.andene.fern.FernIcons

import com.andene.fern.FernTextButton
import com.andene.fern.FernTextField
import com.andene.fern.FernPanel

import com.andene.fern.FernScale

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
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * The document's frames (artboards). Tap a name to fit it on screen; export one as a PNG, or
 * all of them as slices at 1x, @2x and @3x;
 * set a layout grid of columns and rows on one (roadmap item 142); resize one, its objects
 * following their constraints (roadmap item 144);
 * the field at the bottom adds a frame covering the current view. With frames present, PDF
 * export produces one page per frame, and Present steps through them full screen like slides.
 */
@Composable
fun FramesDialog(
    frames: List<Frame>,
    onJump: (Frame) -> Unit,
    onExport: (Frame) -> Unit,
    /** Exports every frame at the chosen scales as one ZIP of slices (roadmap item 128). */
    onExportSlices: (scales: List<Int>) -> Unit,
    onDelete: (Frame) -> Unit,
    /** Sets or removes (null) a frame's layout grid (roadmap item 142). */
    onSetLayoutGrid: (Frame, LayoutGrid?) -> Unit,
    /** Resizes a frame to a width and height in world units (roadmap item 144). */
    onResize: (Frame, Double, Double) -> Unit,
    /** The document's drawing scale, for the resize fields' unit (roadmap item 143). */
    drawingScale: DrawingScale? = null,
    onCreate: (name: String) -> Unit,
    onPresent: () -> Unit,
    onAddComicPage: () -> Unit,
    onDismiss: () -> Unit,
) {
    var newFrameName by remember { mutableStateOf("") }
    var sliceScales by remember { mutableStateOf(setOf(1, 2, 3)) }
    var gridFrame by remember { mutableStateOf<Frame?>(null) }
    var resizeFrame by remember { mutableStateOf<Frame?>(null) }
    resizeFrame?.let { frame ->
        FrameResizeDialog(frame, drawingScale, onApply = { w, h ->
            onResize(frame, w, h)
            resizeFrame = null
        }, onDismiss = { resizeFrame = null })
    }
    gridFrame?.let { frame ->
        LayoutGridDialog(frame, onApply = { grid ->
            onSetLayoutGrid(frame, grid)
            gridFrame = null
        }, onDismiss = { gridFrame = null })
    }

    FernPanel(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.frames_title)) },
        text = {
            Column {
                Column(
                    modifier = Modifier
                        .heightIn(max = FernScale.s384)
                        .verticalScroll(rememberScrollState()),
                ) {
                    if (frames.isEmpty()) {
                        Text(stringResource(R.string.frames_empty))
                    }
                    for (frame in frames) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = frame.name,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { onJump(frame) }
                                    .padding(vertical = FernScale.s12),
                            )
                            IconButton(onClick = { resizeFrame = frame }) {
                                Icon(FernIcons.AspectRatio, contentDescription = stringResource(R.string.frame_resize))
                            }
                            IconButton(onClick = { gridFrame = frame }) {
                                Icon(
                                    FernIcons.ViewColumn,
                                    contentDescription = stringResource(R.string.layout_grid_edit),
                                    tint = if (frame.layoutGrid != null) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                                )
                            }
                            IconButton(onClick = { onExport(frame) }) {
                                Icon(FernIcons.IosShare, contentDescription = stringResource(R.string.frames_export))
                            }
                            IconButton(onClick = { onDelete(frame) }) {
                                Icon(FernIcons.Delete, contentDescription = stringResource(R.string.frames_delete))
                            }
                        }
                    }
                }
                if (frames.isNotEmpty()) {
                    // Slices: every frame at the ticked scales, in one go.
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        for (scale in SLICE_SCALES) {
                            FilterChip(
                                selected = scale in sliceScales,
                                onClick = { sliceScales = if (scale in sliceScales) sliceScales - scale else sliceScales + scale },
                                label = { Text(if (scale == 1) "1x" else "@${scale}x") },
                                modifier = Modifier.padding(end = FernScale.s4),
                            )
                        }
                        FernTextButton(onClick = { onExportSlices(sliceScales.sorted()) }, enabled = sliceScales.isNotEmpty()) {
                            Text(stringResource(R.string.frames_export_slices))
                        }
                    }
                }
                Row(
                    modifier = Modifier.padding(top = FernScale.s8),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FernTextField(
                        value = newFrameName,
                        onValueChange = { newFrameName = it },
                        label = { Text(stringResource(R.string.frames_new_name)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = {
                        if (newFrameName.isNotBlank()) {
                            onCreate(newFrameName.trim())
                            newFrameName = ""
                        }
                    }) {
                        Icon(FernIcons.Add, contentDescription = stringResource(R.string.frames_add))
                    }
                }
            }
        },
        confirmButton = {
            Row {
                FernTextButton(onClick = onAddComicPage) { Text(stringResource(R.string.frames_comic)) }
                FernTextButton(onClick = onPresent, enabled = frames.isNotEmpty()) { Text(stringResource(R.string.frames_present)) }
            }
        },
    )
}
