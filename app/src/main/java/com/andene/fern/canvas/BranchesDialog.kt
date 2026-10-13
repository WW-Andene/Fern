package com.andene.fern.canvas

import com.andene.fern.FernPanel
import com.andene.fern.FernTextButton
import com.andene.fern.FernTextField
import com.andene.fern.FernScale

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMerge
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import com.andene.fern.R

/**
 * Animation branches (roadmap item 153): the branches, the current one ticked; tap one to work
 * on it, merge one into the current branch (frames both changed go to the side chosen below),
 * delete one, or start a new branch from the animation as it is.
 */
@Composable
fun BranchesDialog(state: CanvasState, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var preference by remember { mutableStateOf(MergePreference.OURS) }
    var report by remember { mutableStateOf<String?>(null) }
    val mergedText = stringResource(R.string.branches_merged)
    val conflictsText = stringResource(R.string.branches_conflicts)
    val nothingText = stringResource(R.string.branches_nothing)
    FernPanel(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.branches_title)) },
        text = {
            Column(Modifier.heightIn(max = FernScale.s512).verticalScroll(rememberScrollState())) {
                if (state.branches.isEmpty()) Text(stringResource(R.string.branches_empty))
                for (branch in state.branches) {
                    val current = branch.id == state.currentBranchId
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (current) Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.branches_current))
                        Text(
                            branch.name,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f).clickable(enabled = !current) { state.switchBranch(branch.id) }.padding(FernScale.s8),
                        )
                        if (!current) {
                            IconButton(onClick = {
                                val result = state.mergeBranch(branch.id, preference)
                                report = when {
                                    result.taken.isEmpty() && result.conflicts.isEmpty() -> nothingText
                                    else -> mergedText.format(result.taken.size) + if (result.conflicts.isEmpty()) "" else " " + conflictsText.format(result.conflicts.joinToString { (it + 1).toString() })
                                }
                            }) { Icon(Icons.AutoMirrored.Filled.CallMerge, contentDescription = stringResource(R.string.branches_merge)) }
                            IconButton(onClick = { state.deleteBranch(branch.id) }) { Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.branches_delete)) }
                        }
                    }
                }
                report?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                HorizontalDivider(Modifier.padding(vertical = FernScale.s8))
                Text(stringResource(R.string.branches_conflict_rule))
                Row {
                    FilterChip(selected = preference == MergePreference.OURS, onClick = { preference = MergePreference.OURS }, label = { Text(stringResource(R.string.branches_keep_ours)) }, modifier = Modifier.padding(end = FernScale.s8))
                    FilterChip(selected = preference == MergePreference.THEIRS, onClick = { preference = MergePreference.THEIRS }, label = { Text(stringResource(R.string.branches_take_theirs)) })
                }
                FernTextField(name, { name = it }, label = { Text(stringResource(R.string.branches_new_name)) }, singleLine = true)
                FernTextButton(onClick = {
                    state.createBranch(name.trim())
                    name = ""
                }, enabled = name.isNotBlank() && state.branches.none { it.name == name.trim() }) { Text(stringResource(R.string.branches_create)) }
            }
        },
        confirmButton = { FernTextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
    )
}
