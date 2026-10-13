package com.andene.fern.canvas

import androidx.compose.foundation.clickable
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import com.andene.fern.FernPanel
import com.andene.fern.FernScale
import com.andene.fern.FernSlider
import com.andene.fern.FernSwitch
import com.andene.fern.FernTextButton
import com.andene.fern.FernTextField
import com.andene.fern.R

/**
 * The rig panel (roadmap items 274, 280): the bones as a tree (tap to select, rename the
 * selected one), and the slots in draw order, last on top, each movable up or down or taken off
 * the rig back onto the canvas.
 */
@Composable
fun RigPanel(state: CanvasState, onDismiss: () -> Unit) {
    val editor = state.rigEditor
    val rig = editor.rig
    FernPanel(
        scrollContent = true,
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rig_panel_title)) },
        peek = { rig.bones.firstOrNull { it.id == editor.selectedBoneId }?.let { Text(it.name) } },
        text = {
            Column {
                Text(stringResource(R.string.rig_bones), style = MaterialTheme.typography.labelLarge)
                if (rig.bones.isEmpty()) Text(stringResource(R.string.rig_no_bones))
                // Parents before children, each indented by its depth.
                fun depth(id: String?): Int {
                    var d = 0
                    var p = rig.bones.firstOrNull { it.id == id }?.parentId
                    while (p != null) {
                        d++
                        p = rig.bones.firstOrNull { it.id == p }?.parentId
                    }
                    return d
                }
                for (bone in rig.bones) {
                    val selected = bone.id == editor.selectedBoneId
                    Text(
                        bone.name,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { editor.selectedBoneId = bone.id }
                            .semantics { this.selected = selected }
                            .padding(start = FernScale.s16 * depth(bone.id), top = FernScale.s8, bottom = FernScale.s8),
                    )
                }
                rig.bones.firstOrNull { it.id == editor.selectedBoneId }?.let { bone ->
                    var name by remember(bone.id) { mutableStateOf(bone.name) }
                    FernTextField(
                        name,
                        {
                            name = it
                            editor.renameBone(bone.id, it)
                        },
                        label = { Text(stringResource(R.string.rig_bone_name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    // Follow-through (roadmap item 292): the bone lags and swings behind its animation.
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.rig_physics), modifier = Modifier.weight(1f))
                        FernSwitch(checked = bone.physics != null, onCheckedChange = { on -> editor.setPhysics(bone.id, if (on) BonePhysics() else null) })
                    }
                    bone.physics?.let { physics ->
                        Text(stringResource(R.string.rig_physics_strength), style = MaterialTheme.typography.bodySmall)
                        FernSlider(value = physics.strength.toFloat(), onValueChange = { editor.setPhysics(bone.id, physics.copy(strength = it.toDouble())) })
                        Text(stringResource(R.string.rig_physics_damping), style = MaterialTheme.typography.bodySmall)
                        FernSlider(value = physics.damping.toFloat(), onValueChange = { editor.setPhysics(bone.id, physics.copy(damping = it.toDouble())) })
                    }
                }
                // The active animation's keys (roadmap items 256-257): one row per bone and property.
                editor.activeAnimation?.let { anim ->
                    Text(stringResource(R.string.rig_keys, anim.name), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = FernScale.s8))
                    if (anim.timelines.isEmpty()) Text(stringResource(R.string.rig_no_keys))
                    for (timeline in anim.timelines) {
                        val boneName = rig.bones.firstOrNull { it.id == timeline.boneId }?.name.orEmpty()
                        Text(stringResource(R.string.rig_timeline, boneName, stringResource(propertyLabel(timeline.property))), style = MaterialTheme.typography.bodySmall)
                        Row(Modifier.horizontalScroll(rememberScrollState())) {
                            for (key in timeline.keys) {
                                var open by remember(key) { mutableStateOf(false) }
                                Box {
                                    FilterChip(
                                        selected = kotlin.math.abs(key.time - state.currentAnimFrame) < 1e-9,
                                        onClick = {
                                            state.goToAnimFrame(key.time.toInt())
                                            open = true
                                        },
                                        label = { Text(stringResource(R.string.rig_key_frame, key.time.toInt() + 1, stringResource(curveLabel(key.curve)))) },
                                        modifier = Modifier.padding(end = FernScale.s4),
                                    )
                                    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                                        for ((curve, label) in listOf(KeyCurve.Linear to R.string.rig_curve_linear, KeyCurve.Stepped to R.string.rig_curve_stepped, KeyCurve.Bezier(CubicEasing.DEFAULT) to R.string.rig_curve_eased)) {
                                            DropdownMenuItem(text = { Text(stringResource(label)) }, onClick = {
                                                editor.setKeyCurve(timeline.boneId, timeline.property, key.time, curve)
                                                open = false
                                            })
                                        }
                                        DropdownMenuItem(text = { Text(stringResource(R.string.rig_key_delete)) }, onClick = {
                                            editor.deleteKey(timeline.boneId, timeline.property, key.time)
                                            open = false
                                        })
                                    }
                                }
                            }
                        }
                    }
                }
                Text(stringResource(R.string.rig_slots), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = FernScale.s8))
                if (rig.bones.isNotEmpty()) {
                    FernTextButton(onClick = { state.attachLayersToRig() }) { Text(stringResource(R.string.rig_attach_layers)) }
                }
                if (rig.slots.isEmpty()) Text(stringResource(R.string.rig_no_slots))
                // Last drawn on top: listed top first, as a layers list is.
                for ((index, slot) in rig.slots.withIndex().reversed()) {
                    val boneName = rig.bones.firstOrNull { it.id == slot.boneId }?.name.orEmpty()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.rig_slot_on_bone, slot.name, boneName), modifier = Modifier.weight(1f))
                        IconButton(onClick = { editor.moveSlot(slot.id, 1) }, enabled = index < rig.slots.lastIndex) {
                            Icon(Icons.Filled.KeyboardArrowUp, contentDescription = stringResource(R.string.rig_slot_up))
                        }
                        IconButton(onClick = { editor.moveSlot(slot.id, -1) }, enabled = index > 0) {
                            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = stringResource(R.string.rig_slot_down))
                        }
                        IconButton(onClick = { state.detachRigSlot(slot.id) }) {
                            Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = stringResource(R.string.rig_slot_detach))
                        }
                    }
                }
            }
        },
    )
}

private fun propertyLabel(property: BoneProperty): Int = when (property) {
    BoneProperty.ROTATE -> R.string.rig_property_rotate
    BoneProperty.X -> R.string.rig_property_x
    BoneProperty.Y -> R.string.rig_property_y
    BoneProperty.SCALE_X -> R.string.rig_property_scale_x
    BoneProperty.SCALE_Y -> R.string.rig_property_scale_y
    BoneProperty.SHEAR_X -> R.string.rig_property_shear_x
    BoneProperty.SHEAR_Y -> R.string.rig_property_shear_y
}

private fun curveLabel(curve: KeyCurve): Int = when (curve) {
    KeyCurve.Linear -> R.string.rig_curve_linear
    KeyCurve.Stepped -> R.string.rig_curve_stepped
    is KeyCurve.Bezier -> R.string.rig_curve_eased
}
