package com.andene.fern.canvas

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Previous / position / next / exit bar shown instead of the toolbar while presenting frames. */
@Composable
fun PresentationControls(
    position: Int,
    count: Int,
    onStep: (Int) -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        tonalElevation = 4.dp,
        shadowElevation = 4.dp,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onStep(-1) }, enabled = position > 0) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous frame")
            }
            Text("${position + 1} / $count", modifier = Modifier.padding(horizontal = 8.dp))
            IconButton(onClick = { onStep(1) }, enabled = position < count - 1) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next frame")
            }
            IconButton(onClick = onExit) {
                Icon(Icons.Filled.Close, contentDescription = "Exit presentation")
            }
        }
    }
}
