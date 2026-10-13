package com.andene.fern.canvas

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** A docked panel a workspace can keep open (design roadmap D83). Stored by name. */
enum class WorkspacePanel { LAYERS, BRUSHES, PALETTES, RIG }

/**
 * Which docked panels are open (design roadmap D10), as shared state rather than the toolbar's own,
 * so a workspace can remember them and open them again when it's chosen (design roadmap D83).
 */
object DockedPanels {
    var layers by mutableStateOf(false)
    var brushes by mutableStateOf(false)
    var palettes by mutableStateOf(false)
    var rig by mutableStateOf(false)

    /** The panels open now. */
    fun open(): Set<WorkspacePanel> = buildSet {
        if (layers) add(WorkspacePanel.LAYERS)
        if (brushes) add(WorkspacePanel.BRUSHES)
        if (palettes) add(WorkspacePanel.PALETTES)
        if (rig) add(WorkspacePanel.RIG)
    }

    /** Opens exactly [panels], closing the others. */
    fun show(panels: Set<WorkspacePanel>) {
        layers = WorkspacePanel.LAYERS in panels
        brushes = WorkspacePanel.BRUSHES in panels
        palettes = WorkspacePanel.PALETTES in panels
        rig = WorkspacePanel.RIG in panels
    }
}
