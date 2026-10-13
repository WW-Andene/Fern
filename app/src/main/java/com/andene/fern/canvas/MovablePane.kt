package com.andene.fern.canvas

import com.andene.fern.FernScale

import android.content.Context
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Where a floating pane (the second view, the minimap) has been dragged to: an offset from the
 * spot its caller aligns it to, saved under [key] so the pane comes back where it was left.
 *
 * The offset is stored in dp, not px, so it means the same place after a density change. It is
 * kept inside the parent: a drag can't push the pane off the canvas, and a saved offset that no
 * longer fits (rotation, a smaller window) is pulled back in on the next layout.
 */
@Stable
class MovablePaneState internal constructor(
    private val context: Context,
    private val key: String,
    private val density: Density,
) {
    private var offsetDp by mutableStateOf(MovablePaneSettings.offset(context, key))
    private var bounds = Rect.Zero // the pane in its parent, offset included
    private var parentWidth = 0f
    private var parentHeight = 0f

    /** The current offset, in px. */
    val offset: Offset get() = with(density) { Offset(offsetDp.x.dp.toPx(), offsetDp.y.dp.toPx()) }

    /** Moves the pane by [delta] px, as far as the parent allows. */
    fun moveBy(delta: Offset) {
        if (parentWidth <= 0f) return
        val dx = delta.x.coerceIn(-bounds.left, (parentWidth - bounds.right).coerceAtLeast(-bounds.left))
        val dy = delta.y.coerceIn(-bounds.top, (parentHeight - bounds.bottom).coerceAtLeast(-bounds.top))
        if (dx == 0f && dy == 0f) return
        // Moved here too, so successive drag events before the next layout clamp against where the pane now is.
        bounds = bounds.translate(dx, dy)
        val next = offset + Offset(dx, dy)
        offsetDp = with(density) { Offset(next.x.toDp().value, next.y.toDp().value) }
    }

    /** Saves the offset; called once a drag ends rather than on every move. */
    fun save() = MovablePaneSettings.setOffset(context, key, offsetDp)

    /** Back to where the caller aligns the pane. */
    fun reset() {
        offsetDp = Offset.Zero
        save()
    }

    internal fun placed(inParent: Rect, width: Float, height: Float) {
        bounds = inParent
        parentWidth = width
        parentHeight = height
        // Pull a pane that no longer fits back onto the canvas.
        val before = offsetDp
        moveBy(
            Offset(
                when { inParent.left < 0f -> -inParent.left; inParent.right > width -> width - inParent.right; else -> 0f },
                when { inParent.top < 0f -> -inParent.top; inParent.bottom > height -> height - inParent.bottom; else -> 0f },
            ),
        )
        if (offsetDp != before) save()
    }
}

@Composable
fun rememberMovablePaneState(key: String): MovablePaneState {
    val context = LocalContext.current
    val density = LocalDensity.current
    return remember(key, density) { MovablePaneState(context.applicationContext, key, density) }
}

/** Shifts the pane by [state]'s offset and reports where it lands; goes after the pane's alignment and padding. */
fun Modifier.movablePane(state: MovablePaneState): Modifier = this
    .offset { IntOffset(state.offset.x.roundToInt(), state.offset.y.roundToInt()) }
    .onGloballyPositioned { coordinates ->
        val parent = coordinates.parentLayoutCoordinates ?: return@onGloballyPositioned
        state.placed(coordinates.boundsInParent(), parent.size.width.toFloat(), parent.size.height.toFloat())
    }

/**
 * The grip a floating pane is moved by: drag it to move the pane, double-tap it to put the pane
 * back in its usual place. Only the grip moves the pane, so one finger inside the pane still
 * draws, pans or taps as before.
 */
@Composable
fun PaneDragHandle(state: MovablePaneState, contentDescription: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(width = FernScale.s48, height = FernScale.s24)
            .pointerInput(state) {
                detectDragGestures(onDragEnd = { state.save() }, onDragCancel = { state.save() }) { change, amount ->
                    change.consume()
                    state.moveBy(amount)
                }
            }
            .pointerInput(state) { detectTapGestures(onDoubleTap = { state.reset() }) },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Filled.DragHandle, contentDescription = contentDescription, modifier = Modifier.size(FernScale.s24))
    }
}

/** Saved pane offsets, in the app settings file beside the other view preferences. */
private object MovablePaneSettings {
    private fun keyX(key: String) = "pane_${key}_x"
    private fun keyY(key: String) = "pane_${key}_y"

    fun offset(context: Context, key: String): Offset {
        val prefs = AppSettings.prefs(context)
        return Offset(prefs.getFloat(keyX(key), 0f), prefs.getFloat(keyY(key), 0f))
    }

    fun setOffset(context: Context, key: String, value: Offset) {
        AppSettings.prefs(context).edit().putFloat(keyX(key), value.x).putFloat(keyY(key), value.y).apply()
    }
}
