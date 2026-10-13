package com.andene.fern.canvas

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.andene.fern.FernPanel
import com.andene.fern.FernScale
import com.andene.fern.R

/** What an export is for (design roadmap D13), the first choice when exporting. */
enum class ExportPurpose(@StringRes val label: Int) {
    SHARE(R.string.export_purpose_share),
    IMAGE(R.string.export_purpose_image),
    PRINT(R.string.export_purpose_print),
    VECTOR(R.string.export_purpose_vector),
    LAYERED(R.string.export_purpose_layered),
    ANIMATION(R.string.export_purpose_animation),
    GAME(R.string.export_purpose_game),
}

/**
 * One export format: its [purpose], its [label], whether it's that purpose's [recommended]
 * format, and when it applies ([available]: e.g. animation formats need frames).
 */
enum class ExportFormat(
    val purpose: ExportPurpose,
    @StringRes val label: Int,
    val recommended: Boolean = false,
    val available: (CanvasState) -> Boolean = { true },
) {
    SHARE(ExportPurpose.SHARE, R.string.tb_share, recommended = true),
    PNG(ExportPurpose.IMAGE, R.string.tb_export_png, recommended = true),
    TRANSPARENT_PNG(ExportPurpose.IMAGE, R.string.tb_export_transparent),
    SELECTION_PNG(ExportPurpose.IMAGE, R.string.tb_export_selection, available = { it.selection.isNotEmpty() }),
    EXR(ExportPurpose.IMAGE, R.string.tb_export_exr),
    PDF(ExportPurpose.PRINT, R.string.tb_export_pdf, recommended = true),
    CMYK_PDF(ExportPurpose.PRINT, R.string.tb_export_cmyk_pdf),
    SVG(ExportPurpose.VECTOR, R.string.tb_export_svg, recommended = true),
    EPS(ExportPurpose.VECTOR, R.string.tb_export_eps),
    AI(ExportPurpose.VECTOR, R.string.tb_export_ai),
    DXF(ExportPurpose.VECTOR, R.string.tb_export_dxf),
    PSD(ExportPurpose.LAYERED, R.string.tb_export_psd, recommended = true),
    KRA(ExportPurpose.LAYERED, R.string.tb_export_kra),
    FERN(ExportPurpose.LAYERED, R.string.tb_export_fern),
    GIF(ExportPurpose.ANIMATION, R.string.tb_export_gif, recommended = true, available = { it.animFrameCount > 1 }),
    MP4(ExportPurpose.ANIMATION, R.string.tb_export_mp4, available = { it.animFrameCount > 1 }),
    WEBM(ExportPurpose.ANIMATION, R.string.tb_export_webm, available = { it.animFrameCount > 1 }),
    APNG(ExportPurpose.ANIMATION, R.string.tb_export_apng, available = { it.animFrameCount > 1 }),
    LOTTIE(ExportPurpose.ANIMATION, R.string.tb_export_lottie, available = { it.animFrameCount > 1 }),
    REPLAY_GIF(ExportPurpose.ANIMATION, R.string.tb_export_replay),
    REPLAY_MP4(ExportPurpose.ANIMATION, R.string.tb_export_replay_mp4),
    SPRITE_SHEET(ExportPurpose.GAME, R.string.tb_export_sprites, recommended = true, available = { it.animFrameCount > 1 }),
    SPRITE_ATLAS(ExportPurpose.GAME, R.string.tb_export_sprite_atlas, available = { it.animFrameCount > 1 }),
    SPINE(ExportPurpose.GAME, R.string.tb_export_spine, available = { it.animFrameCount > 1 && it.bones.isNotEmpty() }),
    DRAGON_BONES(ExportPurpose.GAME, R.string.tb_export_dragonbones, available = { it.animFrameCount > 1 && it.bones.isNotEmpty() }),
}

private const val KEY_LAST_EXPORT = "last_export"

/** The last export used, for "Again" (design roadmap D13). */
object ExportSettings {
    fun last(context: Context): ExportFormat? = AppSettings.prefs(context).getString(KEY_LAST_EXPORT, null)?.let { name -> ExportFormat.entries.firstOrNull { it.name == name } }

    fun setLast(context: Context, format: ExportFormat) {
        AppSettings.prefs(context).edit().putString(KEY_LAST_EXPORT, format.name).apply()
    }
}

/**
 * Export by purpose (design roadmap D13): the last export again in one tap, then what the export
 * is for, then its formats with the recommended one first. [run] performs a format's export.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExportPanel(state: CanvasState, run: (ExportFormat) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val last = remember { ExportSettings.last(context) }?.takeIf { it.available(state) }
    var purpose by remember { mutableStateOf(last?.purpose ?: ExportPurpose.SHARE) }
    fun export(format: ExportFormat) {
        ExportSettings.setLast(context, format)
        onDismiss()
        run(format)
    }
    FernPanel(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tb_export)) },
        text = {
            Column {
                if (last != null) {
                    TextButton(onClick = { export(last) }) {
                        Icon(Icons.Filled.Replay, contentDescription = null, modifier = Modifier.padding(end = FernScale.s8))
                        Text(stringResource(R.string.export_again, stringResource(last.label)))
                    }
                }
                FlowRow {
                    for (p in ExportPurpose.entries) {
                        FilterChip(selected = purpose == p, onClick = { purpose = p }, label = { Text(stringResource(p.label)) }, modifier = Modifier.padding(end = FernScale.s8))
                    }
                }
                val formats = ExportFormat.entries.filter { it.purpose == purpose }
                for (format in formats.sortedByDescending { it.recommended }) {
                    val enabled = format.available(state)
                    ListItem(
                        headlineContent = { Text(stringResource(format.label)) },
                        supportingContent = when {
                            !enabled -> { { Text(stringResource(R.string.export_unavailable)) } }
                            format.recommended -> { { Text(stringResource(R.string.export_recommended)) } }
                            else -> null
                        },
                        leadingContent = if (format.recommended) { { Icon(Icons.Filled.Star, contentDescription = null, tint = MaterialTheme.colorScheme.primary) } } else null,
                        modifier = if (enabled) Modifier.clickable { export(format) } else Modifier,
                    )
                }
            }
        },
    )
}
