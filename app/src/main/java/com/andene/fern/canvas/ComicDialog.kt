package com.andene.fern.canvas

import com.andene.fern.FernScale

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.andene.fern.R
import kotlin.math.roundToInt

/**
 * Comic tools (roadmap item 105): a new page of a chosen size and panel layout (with a frame
 * for presenting and exporting it), and speed or focus lines over the selection or the view.
 * Balloons are shapes (speech, thought, shout) and panels are divided with the panel cutter in
 * the selection bar; screentones are a fill option.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ComicDialog(state: CanvasState, onDismiss: () -> Unit) {
    var size by remember { mutableStateOf(ComicPageSize.CLASSIC) }
    var layout by remember { mutableStateOf(ComicLayout.CLASSIC_FIVE) }
    var lineKind by remember { mutableStateOf(ComicLineKind.FOCUS) }
    var count by remember { mutableFloatStateOf(64f) }
    var inner by remember { mutableFloatStateOf(0.4f) }
    var angle by remember { mutableFloatStateOf(0f) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.comic_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.comic_page), style = MaterialTheme.typography.titleSmall)
                FlowRow {
                    for (option in ComicPageSize.entries) {
                        FilterChip(selected = size == option, onClick = { size = option }, label = { Text(stringResource(pageSizeLabel(option))) }, modifier = Modifier.padding(end = FernScale.s8))
                    }
                }
                FlowRow {
                    for (option in ComicLayout.entries) {
                        FilterChip(selected = layout == option, onClick = { layout = option }, label = { Text(stringResource(layoutLabel(option))) }, modifier = Modifier.padding(end = FernScale.s8))
                    }
                }
                TextButton(onClick = {
                    state.insertComicPage(size, layout)
                    onDismiss()
                }) { Text(stringResource(R.string.comic_add_page)) }
                HorizontalDivider()
                Text(stringResource(R.string.comic_lines), style = MaterialTheme.typography.titleSmall)
                FlowRow {
                    for ((option, label) in listOf(ComicLineKind.FOCUS to R.string.comic_focus_lines, ComicLineKind.SPEED to R.string.comic_speed_lines)) {
                        FilterChip(selected = lineKind == option, onClick = { lineKind = option }, label = { Text(stringResource(label)) }, modifier = Modifier.padding(end = FernScale.s8))
                    }
                }
                Text(stringResource(R.string.comic_line_count, count.roundToInt()))
                Slider(value = count, onValueChange = { count = it.roundToInt().toFloat() }, valueRange = 8f..256f)
                Text(stringResource(if (lineKind == ComicLineKind.FOCUS) R.string.comic_focus_inner else R.string.comic_speed_length, (inner * 100).roundToInt()))
                Slider(value = inner, onValueChange = { inner = it }, valueRange = 0.05f..0.95f)
                if (lineKind == ComicLineKind.SPEED) {
                    Text(stringResource(R.string.comic_speed_angle, angle.roundToInt()))
                    Slider(value = angle, onValueChange = { angle = it.roundToInt().toFloat() }, valueRange = -90f..90f)
                }
                TextButton(onClick = {
                    state.addComicLines(lineKind, count.roundToInt(), inner.toDouble(), angle.toDouble())
                    onDismiss()
                }) { Text(stringResource(R.string.comic_add_lines)) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
    )
}

@StringRes
private fun pageSizeLabel(size: ComicPageSize) = when (size) {
    ComicPageSize.CLASSIC -> R.string.comic_size_classic
    ComicPageSize.MANGA -> R.string.comic_size_manga
    ComicPageSize.A4 -> R.string.comic_size_a4
    ComicPageSize.US_COMIC -> R.string.comic_size_us
    ComicPageSize.SQUARE -> R.string.comic_size_square
    ComicPageSize.WEBTOON -> R.string.comic_size_webtoon
}

@StringRes
private fun layoutLabel(layout: ComicLayout) = when (layout) {
    ComicLayout.SINGLE -> R.string.comic_layout_single
    ComicLayout.TWO_ROWS -> R.string.comic_layout_two
    ComicLayout.THREE_ROWS -> R.string.comic_layout_three
    ComicLayout.FOUR_KOMA -> R.string.comic_layout_four_koma
    ComicLayout.CLASSIC_FIVE -> R.string.comic_layout_five
    ComicLayout.GRID_SIX -> R.string.comic_layout_six
    ComicLayout.GRID_NINE -> R.string.comic_layout_nine
}
