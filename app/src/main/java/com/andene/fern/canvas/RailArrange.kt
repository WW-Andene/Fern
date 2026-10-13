package com.andene.fern.canvas

import android.content.ClipData
import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.draganddrop.dragAndDropSource
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.DragAndDropTransferData
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color

/*
 * Arranging the live rail by dragging (design roadmap D22), while Settings > Arrange the layout is
 * open: a tool's chip in that panel, long-pressed and dragged onto the rail, pins it there; a
 * pinned tool long-pressed on the rail and dropped anywhere off it goes. Taps and the panel's own
 * list do the same without dragging, for screen readers and switch access.
 */

/** Whether the layout editor is open, so the rail's pinned tools can be dragged off it. */
object RailArrange {
    var active by mutableStateOf(false)

    // The pinned tool being dragged off the rail, if any: removed unless it lands back on the rail.
    internal var draggedPinned: Tool? = null
}

private const val RAIL_TOOL_LABEL = "fern rail tool"

private fun railTransfer(tool: Tool) = DragAndDropTransferData(ClipData.newPlainText(RAIL_TOOL_LABEL, tool.name))

/** A drag source for [tool]: a long press then a drag carries it, drawn as a [color] tile while it moves. */
@OptIn(ExperimentalFoundationApi::class)
internal fun Modifier.railToolSource(tool: Tool, color: Color, onStart: () -> Unit = {}): Modifier =
    dragAndDropSource(drawDragDecoration = { drawRoundRect(color, cornerRadius = CornerRadius(size.minDimension / 4)) }) {
        detectDragGesturesAfterLongPress(onDragStart = {
            onStart()
            startTransfer(railTransfer(tool))
        }) { _, _ -> }
    }

/** The rail as a drop target: a tool dropped on it is pinned; a pinned one dragged off and dropped elsewhere is unpinned. */
@OptIn(ExperimentalFoundationApi::class)
internal fun Modifier.railDropTarget(context: Context): Modifier = dragAndDropTarget(
    shouldStartDragAndDrop = { event -> event.toAndroidDragEvent().clipDescription?.label == RAIL_TOOL_LABEL },
    target = object : DragAndDropTarget {
        override fun onDrop(event: DragAndDropEvent): Boolean {
            val name = event.toAndroidDragEvent().clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()
            val tool = RAIL_EXTRA_TOOLS.firstOrNull { it.name == name } ?: return false
            val pinned = RailSettings.pinned(context)
            if (tool !in pinned) RailSettings.setPinned(context, pinned + tool)
            return true
        }

        override fun onEnded(event: DragAndDropEvent) {
            val dragged = RailArrange.draggedPinned ?: return
            RailArrange.draggedPinned = null
            // Dropped somewhere other than the rail: it goes.
            if (!event.toAndroidDragEvent().result) RailSettings.setPinned(context, RailSettings.pinned(context) - dragged)
        }
    },
)
