package com.andene.fern.canvas

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.andene.fern.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The raster brush menu (roadmap item 90), shown while the active layer is a raster layer:
 * paint, wet mixing, smudge or blur, and the tip - round, or one imported from a Photoshop
 * (.abr) or Procreate (.brush) file. An .abr with several brushes uses the first.
 */
@Composable
fun RasterBrushControls(state: CanvasState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var open by remember { mutableStateOf(false) }
    var adjusting by remember { mutableStateOf(false) }
    if (adjusting) AdjustmentsDialog(state, onDismiss = { adjusting = false })
    val importTip = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val tips = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openInputStream(uri)?.use { BrushImport.read(it.readBytes()) } }.getOrNull().orEmpty()
            }
            val tip = tips.firstOrNull()
            if (tip == null) Toast.makeText(context, context.getString(R.string.raster_brush_bad_file), Toast.LENGTH_SHORT).show()
            else state.rasterBrush = state.rasterBrush.copy(tip = tip)
        }
    }
    val labels = mapOf(
        RasterBrushMode.PAINT to R.string.raster_brush_paint,
        RasterBrushMode.WET to R.string.raster_brush_wet,
        RasterBrushMode.SMUDGE to R.string.raster_brush_smudge,
        RasterBrushMode.BLUR to R.string.raster_brush_blur,
        RasterBrushMode.LIQUIFY to R.string.raster_brush_liquify,
        RasterBrushMode.SPRAY to R.string.raster_brush_spray,
        RasterBrushMode.BRISTLE to R.string.raster_brush_bristle,
    )
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Filled.Brush, contentDescription = stringResource(R.string.raster_brush_title))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            for ((mode, label) in labels) {
                DropdownMenuItem(
                    text = { Text(stringResource(label)) },
                    leadingIcon = { RadioButton(selected = state.rasterBrush.mode == mode, onClick = null) },
                    onClick = {
                        state.rasterBrush = state.rasterBrush.copy(mode = mode)
                        open = false
                    },
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(stringResource(R.string.adjust_title)) },
                onClick = {
                    open = false
                    adjusting = state.beginAdjusting()
                },
            )
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(stringResource(R.string.raster_brush_round)) },
                leadingIcon = { RadioButton(selected = state.rasterBrush.tip == null, onClick = null) },
                onClick = {
                    state.rasterBrush = state.rasterBrush.copy(tip = null)
                    open = false
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.raster_brush_import)) },
                leadingIcon = { RadioButton(selected = state.rasterBrush.tip != null, onClick = null) },
                onClick = {
                    open = false
                    importTip.launch(arrayOf("*/*"))
                },
            )
        }
    }
}
