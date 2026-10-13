package com.andene.fern.canvas

import com.andene.fern.FernIcons

import com.andene.fern.FernPanel
import com.andene.fern.FernTextButton
import com.andene.fern.FernTextField
import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import com.andene.fern.FernMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
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
 * rail, for one kind of work - and its quick menu and open panels (design roadmap D83), null where
 * it leaves them as they are. Built-in ones have a translated [label]; the user's own have a [name].
 */
data class Workspace(
    val id: String,
    val hiddenGroups: Set<ToolbarGroup>,
    val pinned: List<Tool>,
    @StringRes val label: Int? = null,
    val name: String = "",
    val quickMenu: List<QuickMenuAction>? = null,
    val panels: Set<WorkspacePanel>? = null,
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
// How each workspace was last left, by id (design roadmap D83); a built-in one's own definition until then.
private const val KEY_WORKSPACE_MEMORY = "workspace_memory"

private fun workspaceToJson(w: Workspace): JSONObject = JSONObject().put("id", w.id).put("name", w.name)
    .put("hidden", JSONArray(w.hiddenGroups.map { it.name }))
    .put("pinned", JSONArray(w.pinned.map { it.name }))
    .apply {
        w.quickMenu?.let { put("quickMenu", JSONArray(it.map { a -> a.name })) }
        w.panels?.let { put("panels", JSONArray(it.map { p -> p.name })) }
    }

private fun workspaceFromJson(o: JSONObject, label: Int? = null): Workspace {
    fun names(key: String): List<String>? = o.optJSONArray(key)?.let { a -> (0 until a.length()).map { a.getString(it) } }
    return Workspace(
        o.getString("id"),
        names("hidden").orEmpty().mapNotNullTo(HashSet()) { n -> ToolbarGroup.entries.firstOrNull { it.name == n } },
        names("pinned").orEmpty().mapNotNull { n -> Tool.entries.firstOrNull { it.name == n } },
        label = label,
        name = o.optString("name"),
        quickMenu = names("quickMenu")?.mapNotNull { n -> QuickMenuAction.entries.firstOrNull { it.name == n } }?.ifEmpty { null },
        panels = names("panels")?.mapNotNullTo(HashSet()) { n -> WorkspacePanel.entries.firstOrNull { it.name == n } },
    )
}

/** The user's workspaces and which is active. */
object WorkspaceStore {
    fun custom(context: Context): List<Workspace> {
        val raw = AppSettings.prefs(context).getString(KEY_WORKSPACES, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).map { i -> workspaceFromJson(array.getJSONObject(i)) }
        }.getOrDefault(emptyList())
    }

    fun setCustom(context: Context, workspaces: List<Workspace>) {
        AppSettings.prefs(context).edit().putString(KEY_WORKSPACES, JSONArray(workspaces.map(::workspaceToJson)).toString()).apply()
    }

    /** How [workspace] was last left, or the workspace itself when it hasn't been yet (design roadmap D83). */
    fun remembered(context: Context, workspace: Workspace): Workspace {
        val stored = memory(context).optJSONObject(workspace.id) ?: return workspace
        return runCatching { workspaceFromJson(stored, workspace.label).copy(name = workspace.name) }.getOrDefault(workspace)
    }

    /** Remembers [layout] for workspace [id], to come back to when it's chosen again. */
    fun remember(context: Context, id: String, layout: Workspace) {
        val all = memory(context).put(id, workspaceToJson(layout.copy(id = id)))
        AppSettings.prefs(context).edit().putString(KEY_WORKSPACE_MEMORY, all.toString()).apply()
    }

    /** Forgets how workspace [id] was left, so it opens as defined. */
    fun forget(context: Context, id: String) {
        val all = memory(context).apply { remove(id) }
        AppSettings.prefs(context).edit().putString(KEY_WORKSPACE_MEMORY, all.toString()).apply()
    }

    private fun memory(context: Context): JSONObject =
        AppSettings.prefs(context).getString(KEY_WORKSPACE_MEMORY, null)?.let { runCatching { JSONObject(it) }.getOrNull() } ?: JSONObject()

    /** The layout on screen now, as a workspace: toolbar groups [hiddenGroups], the rail, the quick menu and open panels. */
    fun current(context: Context, hiddenGroups: Set<ToolbarGroup>): Workspace = Workspace(
        "", hiddenGroups, RailSettings.pinned(context),
        quickMenu = AppSettings.quickMenuState(context).value,
        panels = DockedPanels.open(),
    )

    fun activeId(context: Context): String? = AppSettings.prefs(context).getString(KEY_ACTIVE_WORKSPACE, null)

    fun setActiveId(context: Context, id: String) {
        AppSettings.prefs(context).edit().putString(KEY_ACTIVE_WORKSPACE, id).apply()
    }

    /**
     * Switches to [workspace] (design roadmap D83): the one being left, if any, remembers the layout
     * as it is now; then [workspace] comes back as it was last left - toolbar groups (through
     * [onHiddenGroups]), rail, quick menu and open panels.
     */
    fun switchTo(context: Context, workspace: Workspace, hiddenGroups: Set<ToolbarGroup>, onHiddenGroups: (Set<ToolbarGroup>) -> Unit) {
        activeId(context)?.let { leaving -> if (leaving != workspace.id) remember(context, leaving, current(context, hiddenGroups)) }
        val layout = remembered(context, workspace)
        setActiveId(context, workspace.id)
        AppSettings.setHiddenToolbarGroups(context, layout.hiddenGroups)
        onHiddenGroups(layout.hiddenGroups)
        RailSettings.setPinned(context, layout.pinned)
        layout.quickMenu?.let { AppSettings.setQuickMenuActions(context, it) }
        layout.panels?.let(DockedPanels::show)
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
    Box {
        IconButton(onClick = { open = true }) {
            Icon(FernIcons.Dashboard, contentDescription = stringResource(R.string.workspace_switch, active?.let { label(it) } ?: stringResource(R.string.workspace_custom_layout)))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            for (w in all) {
                FernMenuItem(
                    text = { Text(label(w)) },
                    leadingIcon = { if (w.id == activeId) Icon(FernIcons.Check, contentDescription = null) },
                    trailingIcon = if (w.label == null) {
                        {
                            IconButton(onClick = {
                                custom = custom.filter { it.id != w.id }
                                WorkspaceStore.setCustom(context, custom)
                                WorkspaceStore.forget(context, w.id)
                            }) { Icon(FernIcons.Delete, contentDescription = stringResource(R.string.workspace_delete, w.name)) }
                        }
                    } else {
                        null
                    },
                    onClick = {
                        WorkspaceStore.switchTo(context, w, hiddenGroups, onHiddenGroups)
                        activeId = w.id
                        open = false
                    },
                )
            }
            HorizontalDivider()
            // Back to how the active workspace started, forgetting how it was left (design roadmap D83).
            if (active != null) {
                val activeName = label(active)
                FernMenuItem(text = { Text(stringResource(R.string.workspace_reset, activeName)) }, onClick = {
                    WorkspaceStore.forget(context, active.id)
                    WorkspaceStore.switchTo(context, active, hiddenGroups, onHiddenGroups)
                    open = false
                })
            }
            if (active != null && active.label == null) {
                FernMenuItem(text = { Text(stringResource(R.string.workspace_update, active.name)) }, onClick = {
                    val now = WorkspaceStore.current(context, hiddenGroups)
                    custom = custom.map { if (it.id == active.id) now.copy(id = it.id, name = it.name) else it }
                    WorkspaceStore.setCustom(context, custom)
                    WorkspaceStore.forget(context, active.id) // the saved definition is now how it was left
                    open = false
                })
            }
            FernMenuItem(text = { Text(stringResource(R.string.workspace_save_new)) }, onClick = {
                open = false
                naming = true
            })
        }
    }
    if (naming) {
        var name by remember { mutableStateOf("") }
        FernPanel(
            scrollContent = true,
            onDismissRequest = { naming = false },
            title = { Text(stringResource(R.string.workspace_save_new)) },
            text = { FernTextField(name, { name = it }, label = { Text(stringResource(R.string.workspace_name)) }, singleLine = true) },
            confirmButton = {
                FernTextButton(enabled = name.isNotBlank(), onClick = {
                    val created = WorkspaceStore.current(context, hiddenGroups).copy(id = UUID.randomUUID().toString(), name = name.trim())
                    custom = custom + created
                    WorkspaceStore.setCustom(context, custom)
                    WorkspaceStore.setActiveId(context, created.id)
                    activeId = created.id
                    naming = false
                }) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = { FernTextButton(onClick = { naming = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}
