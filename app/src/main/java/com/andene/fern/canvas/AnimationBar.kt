package com.andene.fern.canvas

import com.andene.fern.FernTextButton
import com.andene.fern.FernSlider
import com.andene.fern.FernScale

import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.material.icons.filled.TableRows
import androidx.compose.material.icons.filled.ViewTimeline
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.Merge
import com.andene.fern.R
import com.andene.fern.MoreInline
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AddBox
import androidx.compose.material.icons.filled.AutoAwesomeMotion
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LayersClear
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.SyncAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

// In-betweens generated per tap: 3 turns two keyframes into a smooth 5-frame move.
private const val IN_BETWEEN_COUNT = 3

// Playback speeds offered by tapping the fps label, the common hand-drawn animation rates.
private val FPS_CHOICES = listOf(6, 8, 12, 24)

/**
 * Flipbook controls shown in animation mode: step between frames, add a blank frame or a copy
 * of the current one, delete the frame, toggle onion skin, play/pause, and cycle playback speed.
 */
@Composable
fun AnimationBar(state: CanvasState, modifier: Modifier = Modifier) {
    var showXSheet by remember { mutableStateOf(false) }
    if (showXSheet) XSheetDialog(state, onDismiss = { showXSheet = false })
    var showSway by remember { mutableStateOf(false) }
    if (showSway) SwayDialog(state, onDismiss = { showSway = false })
    var showDopesheet by remember { mutableStateOf(false) }
    if (showDopesheet) DopesheetDialog(state, onDismiss = { showDopesheet = false })
    var showBranches by remember { mutableStateOf(false) }
    if (showBranches) BranchesDialog(state, onDismiss = { showBranches = false })
    var showTags by remember { mutableStateOf(false) }
    if (showTags) FrameTagsDialog(state, onDismiss = { showTags = false })
    var showOnionSettings by remember { mutableStateOf(false) }
    if (showOnionSettings) OnionSkinDialog(state, onDismiss = { showOnionSettings = false })
    var editingCurve by remember { mutableStateOf(false) }
    var blendingPose by remember { mutableStateOf(false) }
    if (blendingPose) BlendPoseDialog(state, onDismiss = { blendingPose = false })
    if (editingCurve) {
        EasingCurveDialog(
            initial = state.motionCurve,
            onApply = { state.motionCurve = it; editingCurve = false },
            onDismiss = { editingCurve = false },
        )
    }
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        tonalElevation = FernScale.s4,
        shadowElevation = FernScale.s4,
    ) {
        Column {
        // Scrub through the frames (design roadmap D45): drag along the strip to see each one.
        if (state.animFrameCount > 1) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = FernScale.s12)) {
                Text(stringResource(R.string.anim_frame_of, state.currentAnimFrame + 1, state.animFrameCount), style = MaterialTheme.typography.labelMedium)
                FernSlider(
                    value = state.currentAnimFrame.toFloat(),
                    onValueChange = { state.goToAnimFrame(it.roundToInt()) },
                    valueRange = 0f..(state.animFrameCount - 1).toFloat(),
                    steps = (state.animFrameCount - 2).coerceAtLeast(0),
                    enabled = !state.playing,
                    modifier = Modifier.weight(1f).padding(start = FernScale.s8),
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { state.goToAnimFrame(state.currentAnimFrame - 1) }, enabled = !state.playing && state.currentAnimFrame > 0) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.presentation_previous))
            }
            Text(stringResource(R.string.presentation_position, state.currentAnimFrame + 1, state.animFrameCount), modifier = Modifier.padding(horizontal = FernScale.s8))
            IconButton(onClick = { state.goToAnimFrame(state.currentAnimFrame + 1) }, enabled = !state.playing && state.currentAnimFrame < state.animFrameCount - 1) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = stringResource(R.string.presentation_next))
            }
            IconButton(onClick = { state.insertAnimFrame(copyCurrent = false) }, enabled = !state.playing) {
                Icon(Icons.Filled.AddBox, contentDescription = stringResource(R.string.anim_add_blank))
            }
            IconButton(onClick = { state.insertAnimFrame(copyCurrent = true) }, enabled = !state.playing) {
                Icon(Icons.Filled.ContentCopy, contentDescription = stringResource(R.string.anim_duplicate))
            }
            // Fills the gap to the next frame with 3 morphed in-betweens.
            IconButton(onClick = { state.generateInBetweens(IN_BETWEEN_COUNT) }, enabled = !state.playing && state.currentAnimFrame < state.animFrameCount - 1) {
                Icon(Icons.Filled.AutoAwesomeMotion, contentDescription = stringResource(R.string.anim_inbetween))
            }
            IconButton(onClick = { state.deleteAnimFrame() }, enabled = !state.playing && state.animFrameCount > 1) {
                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.anim_delete))
            }
            IconButton(onClick = { state.onionSkin = !state.onionSkin }) {
                Icon(
                    if (state.onionSkin) Icons.Filled.Layers else Icons.Filled.LayersClear,
                    contentDescription = if (state.onionSkin) stringResource(R.string.anim_onion_on) else stringResource(R.string.anim_onion_off),
                )
            }
            IconButton(onClick = { showOnionSettings = true }) {
                Icon(Icons.Filled.Tune, contentDescription = stringResource(R.string.onion_title))
            }
            // Frame tags (roadmap item 145): the current frame's tag, tap to manage them all.
            val currentTag = state.frameTags.lastOrNull { state.currentAnimFrame in it }
            FernTextButton(onClick = { showTags = true }) {
                Icon(Icons.AutoMirrored.Filled.Label, contentDescription = null, tint = currentTag?.let { Color(it.color) } ?: LocalContentColor.current)
                Text(currentTag?.name ?: stringResource(R.string.tags_title), modifier = Modifier.padding(start = FernScale.s4))
            }
            IconButton(onClick = {
                if (state.playing) state.playingTag = null
                state.playing = !state.playing
            }, enabled = state.animFrameCount > 1) {
                Icon(
                    if (state.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (state.playing) stringResource(R.string.action_pause) else stringResource(R.string.action_play),
                )
            }
            IconButton(onClick = { state.pingPong = !state.pingPong }) {
                Icon(
                    if (state.pingPong) Icons.Filled.SyncAlt else Icons.Filled.Repeat,
                    contentDescription = if (state.pingPong) stringResource(R.string.anim_pingpong) else stringResource(R.string.anim_loop),
                )
            }
            // Rarer tools behind More, so the strip stays short (design roadmap D32, D45).
            MoreInline("animation_more") {
            if (state.currentBones.isNotEmpty() && state.animFrameCount > 1) {
                IconButton(onClick = { blendingPose = true }) {
                    Icon(Icons.Filled.Merge, contentDescription = stringResource(R.string.blend_pose_title))
                }
            }
            if (state.bones.isNotEmpty()) {
                IconButton(onClick = { showDopesheet = true }) {
                    Icon(Icons.Filled.ViewTimeline, contentDescription = stringResource(R.string.dopesheet_title))
                }
                IconButton(onClick = { showSway = true }) {
                    Icon(Icons.Filled.Animation, contentDescription = stringResource(R.string.sway_title))
                }
            }
            // Branches (roadmap item 153): alternative takes of the animation.
            IconButton(onClick = { showBranches = true }, enabled = !state.playing) {
                Icon(Icons.Filled.AccountTree, contentDescription = stringResource(R.string.branches_title))
            }
            IconButton(onClick = { showXSheet = true }) {
                Icon(Icons.Filled.TableRows, contentDescription = stringResource(R.string.xsheet_title))
            }
            // Timing of the generated in-betweens and motion paths; tap to cycle.
            FernTextButton(onClick = { state.motionEasing = Easing.entries[(state.motionEasing.ordinal + 1) % Easing.entries.size] }) {
                Text(stringResource(state.motionEasing.label))
            }
            if (state.motionEasing == Easing.CUSTOM) {
                FernTextButton(onClick = { editingCurve = true }) { Text(stringResource(R.string.easing_edit_curve)) }
            }
            FernTextButton(onClick = {
                state.animFps = FPS_CHOICES[(FPS_CHOICES.indexOf(state.animFps) + 1) % FPS_CHOICES.size]
            }) {
                Text(stringResource(R.string.anim_fps, state.animFps))
            }
            }
        }
        }
    }
}
