package com.andene.fern.canvas

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val palette = listOf(
    Color(0xFF1B1B1B),
    Color(0xFFE53935),
    Color(0xFF1E88E5),
    Color(0xFF43A047),
    Color(0xFFFB8C00),
)

// Screen-pixel pen width range: matches CanvasState's default of 4.0 and stays reasonable
// across the full slider (a 1px pen is barely visible; 32px is a thick marker).
private const val MIN_WIDTH_SCREEN = 1f
private const val MAX_WIDTH_SCREEN = 32f

@Composable
fun Toolbar(
    state: CanvasState,
    modifier: Modifier = Modifier,
    onOpenDocuments: () -> Unit = {},
    onOpenPins: () -> Unit = {},
) {
    var showWidthSlider by remember { mutableStateOf(false) }
    var showColorPicker by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        tonalElevation = 4.dp,
        shadowElevation = 4.dp,
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
            ) {
                for (color in palette) {
                    val selected = state.activeTool == Tool.PEN && state.activeColor == color
                    Surface(
                        modifier = Modifier
                            .size(32.dp)
                            .clickable {
                                state.activeColor = color
                                state.activeTool = Tool.PEN // picking a color implies "draw", per §6 baseline expectations
                            }
                            .then(
                                if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                else Modifier
                            ),
                        shape = CircleShape,
                        color = color,
                    ) {}
                }
                IconButton(onClick = { showColorPicker = true }) {
                    Icon(Icons.Filled.Palette, contentDescription = "Custom color")
                }

                IconButton(onClick = {
                    state.activeTool = if (state.activeTool == Tool.ERASER) Tool.PEN else Tool.ERASER
                }) {
                    Icon(
                        Icons.AutoMirrored.Filled.Backspace,
                        contentDescription = "Eraser",
                        tint = if (state.activeTool == Tool.ERASER) MaterialTheme.colorScheme.primary
                        else LocalContentColor.current,
                    )
                }
                IconButton(onClick = {
                    state.activeTool = if (state.activeTool == Tool.SELECT) Tool.PEN else Tool.SELECT
                }) {
                    Icon(
                        Icons.Filled.SelectAll,
                        contentDescription = "Select",
                        tint = if (state.activeTool == Tool.SELECT) MaterialTheme.colorScheme.primary
                        else LocalContentColor.current,
                    )
                }
                IconButton(onClick = { showWidthSlider = !showWidthSlider }) {
                    Icon(
                        Icons.Filled.FormatSize,
                        contentDescription = "Brush size",
                        tint = if (showWidthSlider) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                    )
                }
                if (state.activeTool == Tool.SELECT) {
                    IconButton(onClick = { state.duplicateSelection() }) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = "Duplicate selection")
                    }
                    IconButton(onClick = { state.deleteSelection() }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete selection")
                    }
                }

                IconButton(onClick = { state.undo() }) {
                    Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
                }
                IconButton(onClick = { state.redo() }) {
                    Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo")
                }
                IconButton(onClick = { state.clear() }) {
                    Icon(Icons.Filled.DeleteSweep, contentDescription = "Clear all")
                }
                IconButton(onClick = { state.resetView() }) {
                    Icon(Icons.Filled.ZoomOutMap, contentDescription = "Reset view")
                }
                IconButton(onClick = { state.zoomToFit() }) {
                    Icon(Icons.Filled.CenterFocusStrong, contentDescription = "Zoom to fit content")
                }
                IconButton(onClick = onOpenDocuments) {
                    Icon(Icons.Filled.Description, contentDescription = "Documents")
                }
                IconButton(onClick = onOpenPins) {
                    Icon(Icons.Filled.PushPin, contentDescription = "Pins")
                }
            }
            if (showWidthSlider) {
                Slider(
                    value = state.activeWidthWorld.toFloat(),
                    onValueChange = { state.activeWidthWorld = it.toDouble() },
                    valueRange = MIN_WIDTH_SCREEN..MAX_WIDTH_SCREEN,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .width(192.dp),
                )
            }
        }
    }

    if (showColorPicker) {
        ColorPickerDialog(
            initialColor = state.activeColor,
            onDismiss = { showColorPicker = false },
            onColorSelected = { color ->
                state.activeColor = color
                state.activeTool = Tool.PEN
                showColorPicker = false
            },
        )
    }
}
