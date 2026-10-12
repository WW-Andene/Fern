package com.andene.fern.canvas

import com.andene.fern.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/** Everything read for one document, before any of it is applied to the canvas. */
class LoadedDocument(
    val strokes: List<Stroke>,
    val layers: List<Layer>,
    val frames: List<Frame>,
    val history: Pair<List<List<Stroke>>, List<List<Stroke>>>,
    val texts: List<TextItem>,
    val connectors: List<Connector>,
    val bones: List<Bone>,
    val animation: AnimationSettings,
    val images: List<ImageItem>,
    val background: Pair<Color, BackgroundStyle>,
    val pins: List<PinMeta>,
)

/**
 * Covers the canvas while a document loads: a progress indicator, and a scrim that takes every
 * touch, so nothing is drawn onto a document that isn't there yet.
 */
@Composable
fun DocumentLoadingOverlay() {
    // Resolved here: the semantics block isn't a composable context.
    val loadingLabel = stringResource(R.string.loading_document)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.25f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .semantics { contentDescription = loadingLabel },
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}
