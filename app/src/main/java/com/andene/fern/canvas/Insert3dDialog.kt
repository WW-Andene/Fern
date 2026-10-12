package com.andene.fern.canvas

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.andene.fern.R

/**
 * Adds a 3D model to paint (roadmap item 108): a built-in shape, or an OBJ file through
 * [onImportObj]. The model lands in the middle of the view; "Paint in 3D" in the selection bar
 * opens it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Insert3dDialog(onPrimitive: (MeshPrimitive) -> Unit, onImportObj: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.model_insert_title)) },
        text = {
            FlowRow {
                for (primitive in MeshPrimitive.entries) {
                    AssistChip(onClick = { onPrimitive(primitive) }, label = { Text(stringResource(primitiveLabel(primitive))) }, modifier = Modifier.padding(end = 8.dp))
                }
            }
        },
        confirmButton = { TextButton(onClick = onImportObj) { Text(stringResource(R.string.model_import_obj)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@StringRes
private fun primitiveLabel(primitive: MeshPrimitive) = when (primitive) {
    MeshPrimitive.SPHERE -> R.string.model_sphere
    MeshPrimitive.CUBE -> R.string.model_cube
    MeshPrimitive.CYLINDER -> R.string.model_cylinder
    MeshPrimitive.TORUS -> R.string.model_torus
    MeshPrimitive.PLANE -> R.string.model_plane
}
