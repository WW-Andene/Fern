package com.andene.fern.canvas

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.andene.fern.R
import java.util.Locale

/**
 * Exact position, size and rotation for the selection (roadmap item 99), in world units: the
 * selection's bounds as they are, editable, with width and height kept in proportion while the
 * lock is on. Apply calls [onApply] with the left, top, width, height and the turn in degrees;
 * a field that doesn't read as a number keeps its current value.
 */
@Composable
fun TransformDialog(
    bounds: Pair<WorldPoint, WorldPoint>,
    onApply: (left: Double, top: Double, width: Double, height: Double, rotationDegrees: Double) -> Unit,
    onDismiss: () -> Unit,
) {
    val (min, max) = bounds
    val startWidth = max.x - min.x
    val startHeight = max.y - min.y
    fun format(value: Double) = String.format(Locale.ROOT, "%.1f", value)
    // Accepts either decimal separator, whatever the keyboard offers.
    fun parse(text: String) = text.trim().replace(',', '.').toDoubleOrNull()
    var x by remember { mutableStateOf(format(min.x)) }
    var y by remember { mutableStateOf(format(min.y)) }
    var w by remember { mutableStateOf(format(startWidth)) }
    var h by remember { mutableStateOf(format(startHeight)) }
    var angle by remember { mutableStateOf("0") }
    var locked by remember { mutableStateOf(true) }
    val numeric = KeyboardOptions(keyboardType = KeyboardType.Decimal)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.transform_title)) },
        text = {
            Column {
                Row {
                    OutlinedTextField(x, { x = it }, label = { Text("X") }, singleLine = true, keyboardOptions = numeric, modifier = Modifier.width(128.dp).padding(end = 8.dp))
                    OutlinedTextField(y, { y = it }, label = { Text("Y") }, singleLine = true, keyboardOptions = numeric, modifier = Modifier.width(128.dp))
                }
                Row {
                    OutlinedTextField(
                        w,
                        { text ->
                            w = text
                            if (locked && startWidth > 0) parse(text)?.let { h = format(it * startHeight / startWidth) }
                        },
                        label = { Text(stringResource(R.string.transform_width)) }, singleLine = true, keyboardOptions = numeric,
                        modifier = Modifier.width(128.dp).padding(end = 8.dp),
                    )
                    OutlinedTextField(
                        h,
                        { text ->
                            h = text
                            if (locked && startHeight > 0) parse(text)?.let { w = format(it * startWidth / startHeight) }
                        },
                        label = { Text(stringResource(R.string.transform_height)) }, singleLine = true, keyboardOptions = numeric,
                        modifier = Modifier.width(128.dp),
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = locked, onCheckedChange = { locked = it })
                    Text(stringResource(R.string.transform_keep_proportions))
                }
                OutlinedTextField(angle, { angle = it }, label = { Text(stringResource(R.string.transform_rotate)) }, singleLine = true, keyboardOptions = numeric, modifier = Modifier.width(128.dp))
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onApply(parse(x) ?: min.x, parse(y) ?: min.y, parse(w) ?: startWidth, parse(h) ?: startHeight, parse(angle) ?: 0.0)
            }) { Text(stringResource(R.string.action_apply)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
