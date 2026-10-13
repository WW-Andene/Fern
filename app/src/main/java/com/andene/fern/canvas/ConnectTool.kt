package com.andene.fern.canvas

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * The CONNECT tool, with its own state rather than in [CanvasState] (roadmap item 167): press on
 * a label and drag to another to connect them (a connector between the same two labels in the
 * same direction isn't added twice); pressing on an existing connector, away from any label,
 * removes it. The connectors themselves are the document's, reached through [host].
 */
class ConnectTool(private val host: Host) {
    interface Host {
        /** The label at [point], if any. */
        fun labelAt(point: WorldPoint): TextItem?

        /** The connector drawn at [point], if any. */
        fun connectorAt(point: WorldPoint): Connector?

        /** Whether a connector already runs from [fromId] to [toId]. */
        fun connected(fromId: String, toId: String): Boolean

        /** Adds a connector from [fromId] to [toId], as one undo step. */
        fun connect(fromId: String, toId: String)

        /** Removes [connector], as one undo step. */
        fun disconnect(connector: Connector)
    }

    /** While dragging out a connector: the label it starts from and the finger's point. */
    var draft: Pair<String, WorldPoint>? by mutableStateOf(null)
        private set

    fun begin(worldPoint: WorldPoint) {
        val from = host.labelAt(worldPoint)
        if (from != null) {
            draft = from.id to worldPoint
            return
        }
        host.connectorAt(worldPoint)?.let(host::disconnect)
    }

    fun move(worldPoint: WorldPoint) {
        val current = draft ?: return
        draft = current.first to worldPoint
    }

    fun end() {
        val (fromId, point) = draft ?: return
        draft = null
        val to = host.labelAt(point) ?: return
        if (to.id == fromId || host.connected(fromId, to.id)) return
        host.connect(fromId, to.id)
    }
}
