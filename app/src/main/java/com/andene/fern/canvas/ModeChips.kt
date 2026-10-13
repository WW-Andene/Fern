package com.andene.fern.canvas

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.andene.fern.FernScale
import com.andene.fern.R

/**
 * The drawing modes that are on (design roadmap D40) - symmetry, perspective, wrap-around,
 * isometric plane, stabiliser, ruler - as chips at the canvas edge, so none stays on unnoticed;
 * tapping one turns it off.
 */
@Composable
fun ModeChips(state: CanvasState, modifier: Modifier = Modifier) {
    val chips = buildList<Pair<String, () -> Unit>> {
        if (state.symmetryMode != SymmetryMode.OFF) add(stringResource(R.string.tb_symmetry, stringResource(state.symmetryMode.label)) to { state.setSymmetry(SymmetryMode.OFF) })
        if (state.perspectiveMode != PerspectiveMode.OFF) add(stringResource(R.string.tb_perspective, stringResource(state.perspectiveMode.label)) to { state.setPerspective(PerspectiveMode.OFF) })
        if (state.wrapFrameId != null) add(stringResource(R.string.wrap_off) to { state.toggleWrapAround() })
        state.isometricPlane?.let { add(stringResource(isometricPlaneLabel(it)) to { state.isometricPlane = null }) }
        if (state.activeStabilization > 0.0) add(stringResource(R.string.tb_stabilisation) to { state.activeStabilization = 0.0 })
        if (state.rulerActive) add(stringResource(R.string.tb_ruler) to { state.toggleRuler() })
    }
    if (chips.isEmpty()) return
    Column(modifier) {
        for ((label, turnOff) in chips) {
            InputChip(
                selected = true,
                onClick = turnOff,
                label = { Text(label) },
                trailingIcon = { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.mode_turn_off)) },
                modifier = Modifier.padding(bottom = FernScale.s4),
            )
        }
    }
}
