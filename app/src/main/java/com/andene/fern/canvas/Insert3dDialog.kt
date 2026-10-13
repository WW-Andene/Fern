package com.andene.fern.canvas

import com.andene.fern.FernPanel
import com.andene.fern.FernTextButton
import com.andene.fern.FernScale

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.andene.fern.R

/**
 * Adds a 3D model to paint (roadmap item 108): a posable hand or a head (roadmap item 146), a built-in shape, or an OBJ file through
 * [onImportObj]. The model lands in the middle of the view; "Paint in 3D" in the selection bar
 * opens it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Insert3dDialog(onPrimitive: (MeshPrimitive) -> Unit, onHand: () -> Unit, onHead: () -> Unit, onImportObj: () -> Unit, onDismiss: () -> Unit) {
    FernPanel(
        scrollContent = true,
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.model_insert_title)) },
        text = {
            FlowRow {
                // Posable references (roadmap item 146).
                AssistChip(onClick = onHand, label = { Text(stringResource(R.string.model_hand)) }, modifier = Modifier.padding(end = FernScale.s8))
                AssistChip(onClick = onHead, label = { Text(stringResource(R.string.model_head)) }, modifier = Modifier.padding(end = FernScale.s8))
                for (primitive in MeshPrimitive.entries) {
                    AssistChip(onClick = { onPrimitive(primitive) }, label = { Text(stringResource(primitiveLabel(primitive))) }, modifier = Modifier.padding(end = FernScale.s8))
                }
            }
        },
        confirmButton = { FernTextButton(onClick = onImportObj) { Text(stringResource(R.string.model_import_obj)) } },
        dismissButton = { FernTextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
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
