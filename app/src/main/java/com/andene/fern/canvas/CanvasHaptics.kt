package com.andene.fern.canvas

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalView
import com.andene.fern.FernHaptics
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/**
 * Haptic ticks for what happens on the canvas (design roadmap D7): switching tool, zoom passing
 * 100 %, and a moved selection snapping to another object's edge or centre.
 */
@Composable
fun CanvasHaptics(state: CanvasState) {
    val view = LocalView.current
    LaunchedEffect(state, view) {
        launch {
            snapshotFlow { state.activeTool }.distinctUntilChanged().drop(1).collect { FernHaptics.tick(view) }
        }
        launch {
            // Which side of 100 % the zoom is on; a change means it just passed through.
            snapshotFlow { state.scale.compareTo(1.0) }.distinctUntilChanged().drop(1).collect { side -> if (side != 0) FernHaptics.tick(view) }
        }
        launch {
            snapshotFlow { state.smartGuides }.distinctUntilChanged().collect { (x, y) -> if (x != null || y != null) FernHaptics.tick(view) }
        }
    }
}
