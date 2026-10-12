package com.andene.fern.canvas

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.andene.fern.R
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * A workspace (design roadmap D11): which toolbar groups show and which tools are pinned to the
 * rail, for one kind of work. Built-in ones have a translated [label]; the user's own have a [name].
 */
data class Workspace(
    val id: String,
    val hiddenGroups: Set<ToolbarGroup>,
    val pinned: List<Tool>,
    @StringRes val label: Int? = null,
    val name: String = "",
)

private val ALL_GROUPS = ToolbarGroup.entries.toSet()

/** The built-in workspaces: each shows what its kind of work needs and hides the rest. */
val BUILT_IN_WORKSPACES = listOf(
    Workspace("draw", setOf(ToolbarGroup.ANIMATION, ToolbarGroup.REPLAY, ToolbarGroup.LAYERS), listOf(Tool.FILL, Tool.TEXT, Tool.EYEDROPPER), R.string.workspace_draw),
    Workspace("paint", setOf(ToolbarGroup.ANIMATION, ToolbarGroup.REPLAY, ToolbarGroup.TEXT), listOf(Tool.FILL, Tool.EYEDROPPER), R.string.workspace_paint),
    Workspace("design", setOf(ToolbarGroup.ANIMATION, ToolbarGroup.REPLAY), listOf(Tool.BEZIER, Tool.TEXT, Tool.MEASURE, Tool.CONNECT), R.string.workspace_design),
    Workspace("animate", setOf(ToolbarGroup.REPLAY, ToolbarGroup.TEXT), listOf(Tool.BONE, Tool.FILL, Tool.EYEDROPPER), R.string.workspace_animate),
    Workspace("game", setOf(ToolbarGroup.REPLAY, ToolbarGroup.TEXT), listOf(Tool.FILL, Tool.BONE, Tool.EYEDROPPER), R.string.workspace_game),
    Workspace("notes", ALL_GROUPS - setOf(ToolbarGroup.FAVOURITES, ToolbarGroup.PALETTE, ToolbarGroup.VIEW, ToolbarGroup.BACKGROUND), listOf(Tool.TEXT, Tool.EYEDROPPER), R.string.workspace_notes),
)

private const val KEY_WORKSPACES = "workspaces"
private const val KEY_ACTIVE_WORKSPACE = "active_workspace"

/** The user's workspaces and which is active. */
object WorkspaceStore {
    fun custom(context: Context): List<Workspace> {
        val raw = AppSettings.prefs(context).getString(KEY_WORKSPACES, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                Workspace(
                    o.getString("id"),
                    o.optJSONArray("hidden")?.let { a -> (0 until a.length()).mapNotNullTo(HashSet()) { k -> ToolbarGroup.entries.firstOrNull { it.name == a.getString(k) } } }.orEmpty(),
                    o.optJSONArray("pinned")?.let { a -> (0 until a.length()).mapNotNull { k -> Tool.entries.firstOrNull { it.name == a.getString(k) } } }.orEmpty(),
                    name = o.optString("name"),
                )
            }
        }.getOrDefault(emptyList())
    }

    fun setCustom(context: Context, workspaces: List<Workspace>) {
        val array = JSONArray()
        for (w in workspaces) array.put(
            JSONObject().put("id", w.id).put("name", w.name)
                .put("hidden", JSONArray(w.hiddenGroups.map { it.name }))
                .put("pinned", JSONArray(w.pinned.map { it.name })),
        )
        AppSettings.prefs(context).edit().putString(KEY_WORKSPACES, array.toString()).apply()
    }

    fun activeId(context: Context): String? = AppSettings.prefs(context).getString(KEY_ACTIVE_WORKSPACE, null)

    fun setActiveId(context: Context, id: String) {
        AppSettings.prefs(context).edit().putString(KEY_ACTIVE_WORKSPACE, id).apply()
    }

    /** Applies [workspace]: its toolbar groups through [onHiddenGroups], its pinned tools to the rail. */
    fun apply(context: Context, workspace: Workspace, onHiddenGroups: (Set<ToolbarGroup>) -> Unit) {
        setActiveId(context, workspace.id)
        AppSettings.setHiddenToolbarGroups(context, workspace.hiddenGroups)
        onHiddenGroups(workspace.hiddenGroups)
        RailSettings.setPinned(context, workspace.pinned)
    }
}

/**
 * The workspace switcher (design roadmap D11): one tap shows the workspaces; picking one changes
 * the toolbar and the rail at once. The current layout can be saved as a new workspace or into
 * one of the user's, and the user's own can be deleted.
 */
@Composable
fun WorkspaceSwitcher(hiddenGroups: Set<ToolbarGroup>, onHiddenGroups: (Set<ToolbarGroup>) -> Unit) {
    val context = LocalContext.current
    var custom by remember { mutableStateOf(WorkspaceStore.custom(context)) }
    var activeId by remember { mutableStateOf(WorkspaceStore.activeId(context)) }
    var open by remember { mutableStateOf(false) }
    var naming by remember { mutableStateOf(false) }
    val all = BUILT_IN_WORKSPACES + custom
    val active = all.firstOrNull { it.id == activeId }
    val label: @Composable (Workspace) -> String = { w -> w.label?.let { stringResource(it) } ?: w.name }
    val pinned by RailSettings.pinnedState(context)
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Filled.Dashboard, contentDescription = stringResource(R.string.workspace_switch, active?.let { label(it) } ?: stringResource(R.string.workspace_custom_layout)))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            for (w in all) {
                DropdownMenuItem(
                    text = { Text(label(w)) },
                    leadingIcon = { if (w.id == activeId) Icon(Icons.Filled.Check, contentDescription = null) },
                    trailingIcon = if (w.label == null) {
                        {
                            IconButton(onClick = {
                                custom = custom.filter { it.id != w.id }
                                WorkspaceStore.setCustom(context, custom)
                            }) { Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.workspace_delete, w.name)) }
                        }
                    } else {
                        null
                    },
                    onClick = {
                        WorkspaceStore.apply(context, w, onHiddenGroups)
                        activeId = w.id
                        open = false
                    },
                )
            }
            HorizontalDivider()
            if (active != null && active.label == null) {
                DropdownMenuItem(text = { Text(stringResource(R.string.workspace_update, active.name)) }, onClick = {
                    custom = custom.map { if (it.id == active.id) it.copy(hiddenGroups = hiddenGroups, pinned = pinned) else it }
                    WorkspaceStore.setCustom(context, custom)
                    open = false
                })
            }
            DropdownMenuItem(text = { Text(stringResource(R.string.workspace_save_new)) }, onClick = {
                open = false
                naming = true
            })
        }
    }
    if (naming) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { naming = false },
            title = { Text(stringResource(R.string.workspace_save_new)) },
            text = { OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.workspace_name)) }, singleLine = true) },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    val created = Workspace(UUID.randomUUID().toString(), hiddenGroups, pinned, name = name.trim())
                    custom = custom + created
                    WorkspaceStore.setCustom(context, custom)
                    WorkspaceStore.setActiveId(context, created.id)
                    activeId = created.id
                    naming = false
                }) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = { TextButton(onClick = { naming = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}
