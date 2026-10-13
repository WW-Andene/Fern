package com.andene.fern.canvas

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.andene.fern.R

/**
 * Optional groups of toolbar buttons the user can hide (roadmap item 71). The core tools - pen,
 * eraser, select, shapes, size, opacity, undo/redo, documents, the ⋮ menu and export - can't be
 * hidden, so Settings (in the ⋮ menu) and drawing itself always stay reachable.
 */
enum class ToolbarGroup(@StringRes val label: Int) {
    FAVOURITES(R.string.group_favourites),
    PALETTE(R.string.group_palette),
    STROKE_OPTIONS(R.string.group_stroke_options),
    GUIDES(R.string.group_guides),
    ANIMATION(R.string.group_animation),
    LAYERS(R.string.group_layers),
    FILL(R.string.group_fill),
    TEXT(R.string.group_text),
    VIEW(R.string.group_view),
    REPLAY(R.string.group_replay),
    BACKGROUND(R.string.group_background),
}
