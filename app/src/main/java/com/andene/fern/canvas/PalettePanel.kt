package com.andene.fern.canvas

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import com.andene.fern.FernPanel
import com.andene.fern.FernScale
import com.andene.fern.FernTextButton
import com.andene.fern.FernTextField
import com.andene.fern.R

/**
 * The palette panel (roadmap item 350): every palette as a chip to switch to, the active one's
 * colours as a grid of swatches, and the current colour's harmonies. Tap a swatch to draw with
 * it; select one to move it earlier or later, remove it or change it everywhere. New, rename,
 * delete and import work on whole palettes.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PalettePanel(
    state: CanvasState,
    onImport: () -> Unit,
    onChangeEverywhere: (Color) -> Unit,
    onDismiss: () -> Unit,
) {
    // The swatch whose actions show, by index in the active palette; -1 for none.
    var selected by remember { mutableIntStateOf(-1) }
    var naming by remember { mutableStateOf(false) }
    val newName = stringResource(R.string.palette_new_name)
    val active = state.palettes.firstOrNull { it.id == state.activePaletteId }
    FernPanel(
        scrollContent = true,
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.palette_panel_title)) },
        peek = { active?.let { Text(it.name) } },
        headerAction = {
            IconButton(onClick = {
                state.addPalette(newName)
                selected = -1
                naming = true
            }) { Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.palette_new)) }
        },
        text = {
            Column {
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    for (palette in state.palettes) {
                        FilterChip(
                            selected = palette.id == state.activePaletteId,
                            onClick = {
                                state.selectPalette(palette.id)
                                selected = -1
                                naming = false
                            },
                            label = { Text(palette.name) },
                            modifier = Modifier.padding(end = FernScale.s4),
                        )
                    }
                }
                if (active != null) {
                    if (naming) {
                        var name by remember(active.id) { mutableStateOf(active.name) }
                        FernTextField(
                            name,
                            {
                                name = it
                                state.renamePalette(active.id, it)
                            },
                            label = { Text(stringResource(R.string.palette_name)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FernTextButton(onClick = { naming = !naming }) { Text(stringResource(R.string.palette_rename)) }
                        if (state.palettes.size > 1) {
                            IconButton(onClick = {
                                state.deletePalette(active.id)
                                selected = -1
                            }) { Icon(Icons.Filled.DeleteOutline, contentDescription = stringResource(R.string.palette_delete, active.name)) }
                        }
                    }
                    FlowRow {
                        state.savedColors.forEachIndexed { index, color ->
                            val label = stringResource(R.string.color_saved)
                            Surface(
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .size(FernScale.s32)
                                    .semantics {
                                        contentDescription = label
                                        this.selected = index == selected
                                    }
                                    .clickable {
                                        state.activeColor = color
                                        if (state.activeTool !in COLOUR_TOOLS) state.activeTool = Tool.PEN
                                        selected = if (selected == index) -1 else index
                                    },
                                shape = MaterialTheme.shapes.small,
                                color = color,
                                border = if (index == selected) BorderStroke(FernScale.s2, MaterialTheme.colorScheme.primary) else null,
                            ) {}
                        }
                    }
                    if (selected in state.savedColors.indices) {
                        val color = state.savedColors[selected]
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = {
                                state.moveSavedColor(selected, selected - 1)
                                selected = (selected - 1).coerceAtLeast(0)
                            }, enabled = selected > 0) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.palette_move_earlier)) }
                            IconButton(onClick = {
                                state.moveSavedColor(selected, selected + 1)
                                selected = (selected + 1).coerceAtMost(state.savedColors.lastIndex)
                            }, enabled = selected < state.savedColors.lastIndex) { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = stringResource(R.string.palette_move_later)) }
                            IconButton(onClick = {
                                state.removeSavedColor(color)
                                selected = -1
                            }) { Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.color_remove_saved)) }
                            FernTextButton(onClick = { onChangeEverywhere(color) }) { Text(stringResource(R.string.color_change_everywhere)) }
                        }
                    }
                    FernTextButton(onClick = { state.addSavedColor(state.activeColor) }, enabled = state.activeColor !in state.savedColors) {
                        Text(stringResource(R.string.color_save_palette))
                    }
                }
                // Harmonies of the current colour: complementary, analogous either side, triadic.
                Text(stringResource(R.string.color_harmonies), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = FernScale.s8))
                Row {
                    val harmonyLabel = stringResource(R.string.color_harmonies)
                    for (turn in HARMONY_TURNS) {
                        val color = turnHue(state.activeColor, turn)
                        Swatch(color, CircleShape, harmonyLabel) {
                            state.activeColor = color
                            if (state.activeTool !in COLOUR_TOOLS) state.activeTool = Tool.PEN
                        }
                    }
                }
                FernTextButton(onClick = onImport) { Text(stringResource(R.string.color_import_palette)) }
            }
        },
    )
}
