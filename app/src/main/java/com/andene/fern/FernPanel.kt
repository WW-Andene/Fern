package com.andene.fern

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.semantics.customActions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/*
 * Docked panels (design roadmap D10): the panels that used to be modal dialogs - layers,
 * frames, symbols, pins, versions, objects and their logic - open in a panel that leaves the
 * canvas usable. On a phone held upright it's a bottom sheet taking at most half the screen,
 * that collapses to its header; otherwise it docks beside the rail. Panels stack: one opened
 * from another (an object's rules) shows on top, and closing it brings the other back.
 */

/** One open panel. */
class PanelEntry internal constructor(
    val key: Any,
    title: @Composable () -> Unit,
    onDismissRequest: () -> Unit,
    content: @Composable () -> Unit,
    actions: @Composable () -> Unit,
    headerAction: (@Composable () -> Unit)? = null,
    onFilter: ((String) -> Unit)? = null,
    scrollContent: Boolean = false,
    peek: (@Composable () -> Unit)? = null,
) {
    var scrollContent by mutableStateOf(scrollContent)
    var peek by mutableStateOf(peek)
    var headerAction by mutableStateOf(headerAction)
    var onFilter by mutableStateOf(onFilter)
    var title by mutableStateOf(title)
    var onDismissRequest by mutableStateOf(onDismissRequest)
    var content by mutableStateOf(content)
    var actions by mutableStateOf(actions)
}

/** The open panels, last on top. */
object FernPanels {
    val open = mutableStateListOf<PanelEntry>()
}

/**
 * A panel, called where a dialog would be: shown by the [FernPanelHost] for as long as this is
 * in the composition. Same parameters as an `AlertDialog`; [confirmButton] and [dismissButton]
 * sit at the panel's foot.
 */
@Composable
fun FernPanel(
    onDismissRequest: () -> Unit,
    title: @Composable () -> Unit,
    text: @Composable () -> Unit,
    confirmButton: @Composable () -> Unit = {},
    dismissButton: @Composable () -> Unit = {},
    /** The panel's main action, in its header (design roadmap D42): e.g. "New layer". */
    headerAction: (@Composable () -> Unit)? = null,
    /** When set, a filter field under the header; called as the text changes. */
    onFilter: ((String) -> Unit)? = null,
    /**
     * Scroll [text] inside the panel. On by default only where the content doesn't scroll itself:
     * a phone's sheet is half the screen, so longer content would be cut off. Off for content
     * holding its own vertical scroll or lazy list, which can't sit inside another scroll.
     */
    scrollContent: Boolean = false,
    /** What the panel still shows in its header while collapsed (design roadmap D92): the active layer, the brush. */
    peek: (@Composable () -> Unit)? = null,
) {
    val key = remember { Any() }
    val actions: @Composable () -> Unit = {
        dismissButton()
        confirmButton()
    }
    DisposableEffect(key) {
        FernPanels.open.add(PanelEntry(key, title, onDismissRequest, text, actions, headerAction, onFilter, scrollContent, peek))
        onDispose { FernPanels.open.removeAll { it.key === key } }
    }
    SideEffect {
        FernPanels.open.firstOrNull { it.key === key }?.let {
            it.title = title
            it.onDismissRequest = onDismissRequest
            it.content = text
            it.actions = actions
            it.headerAction = headerAction
            it.onFilter = onFilter
            it.scrollContent = scrollContent
            it.peek = peek
        }
    }
}

/**
 * Shows the open panels; place once, in the screen's root box. With the bottom rail (a phone held
 * upright) the top panel is a sheet; otherwise panels dock beside the rail, as many side by side
 * as the window has room for ([FernLayout]), the top one next to the rail. [oneHand] keeps a
 * sheet within the bottom third.
 */
@Composable
fun androidx.compose.foundation.layout.BoxScope.FernPanelHost(railOnLeft: Boolean, railInset: androidx.compose.ui.unit.Dp, railSide: androidx.compose.ui.unit.Dp, oneHand: Boolean = false) {
    val current = FernPanels.open.lastOrNull()
    // The last panel shown stays composed while it slides away (design roadmap D57).
    val lastShown = remember { arrayOfNulls<PanelEntry>(1) }
    if (current != null) lastShown[0] = current
    val entry = lastShown[0] ?: return
    // How much of the screen the sheet may take; dragged at its handle (design roadmap D42).
    var share by remember { mutableFloatStateOf(DEFAULT_SHEET_SHARE) }
    BackHandler(enabled = current != null) { entry.onDismissRequest() }
    val configuration = LocalConfiguration.current
    val layout = fernLayout()
    val sheet = layout.railAtBottom
    // Panels open beneath the top one that still have room beside it (wide windows only).
    val beneath = if (sheet || current == null) emptyList() else FernPanels.open.dropLast(1).takeLast(layout.dockedPanels - 1).reversed()
    val modifier = if (sheet) {
        Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .padding(bottom = railInset)
            // In one-hand mode (design roadmap D55) the sheet's header stays in the bottom third.
            .heightIn(max = (configuration.screenHeightDp * if (oneHand) minOf(share, ONE_HAND_SHEET_SHARE) else share).dp)
    } else {
        Modifier
            .align(if (railOnLeft) androidx.compose.ui.AbsoluteAlignment.CenterLeft else androidx.compose.ui.AbsoluteAlignment.CenterRight)
            .padding(horizontal = railSide)
            .heightIn(max = configuration.screenHeightDp.dp - FernScale.s32)
    }
    // Panels slide in from where they dock and back out (design roadmap D57); Compose follows
    // the system's animation scale, so "remove animations" turns this off (D5).
    val slide = tween<IntOffset>(FernMotion.MEDIUM)
    // Docked panels line up outward from the rail: the top one first. A Row runs from the
    // physical left in a left-to-right language and from the right otherwise.
    val ltr = androidx.compose.ui.platform.LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Ltr
    val fromStart = ltr == railOnLeft
    Row(modifier) {
        if (!fromStart) for (other in beneath.asReversed()) key(other.key) { PanelCard(other, sheet = false, share, { share = it }, Modifier.width(FernScale.s384)) }
        androidx.compose.animation.AnimatedVisibility(
            visible = current != null,
            enter = if (sheet) slideInVertically(slide) { it } else slideInHorizontally(slide) { if (railOnLeft) -it else it },
            exit = if (sheet) slideOutVertically(slide) { it } else slideOutHorizontally(slide) { if (railOnLeft) -it else it },
        ) {
            key(entry.key) { PanelCard(entry, sheet, share, { share = it }, if (sheet) Modifier.fillMaxWidth() else Modifier.width(FernScale.s384)) }
        }
        if (fromStart) for (other in beneath) key(other.key) { PanelCard(other, sheet = false, share, { share = it }, Modifier.width(FernScale.s384)) }
    }
}

/** One panel: its header (title, main action, collapse, close), filter, body and actions. */
@Composable
private fun PanelCard(entry: PanelEntry, sheet: Boolean, share: Float, onShareChange: (Float) -> Unit, modifier: Modifier) {
    var collapsed by remember(entry.key) { mutableStateOf(false) }
    var filter by remember(entry.key) { mutableStateOf("") }
    val configuration = LocalConfiguration.current
    Surface(modifier.padding(FernSpace.sm), shape = MaterialTheme.shapes.large, tonalElevation = FernSpace.xs, shadowElevation = FernSpace.sm) {
        Column {
            if (sheet) {
                // The handle: drag up or down to give the sheet more or less of the screen.
                val screenPx = with(LocalDensity.current) { configuration.screenHeightDp.dp.toPx() }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(FernScale.s16)
                        .pointerInput(Unit) {
                            detectVerticalDragGestures { change, dy ->
                                change.consume()
                                onShareChange((share - dy / screenPx).coerceIn(MIN_SHEET_SHARE, MAX_SHEET_SHARE))
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.width(FernScale.s32).height(FernScale.s4).background(MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small))
                }
            }
            // Swiping the header sideways brings the next open panel to the front, keeping them all
            // open (design roadmap D91): layers, brushes and timeline without closing and reopening.
            val swipePx = with(LocalDensity.current) { FernScale.s64.toPx() }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = FernSpace.lg)
                    .pointerInput(Unit) {
                        var dragged = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { dragged = 0f },
                            onHorizontalDrag = { change, dx ->
                                change.consume()
                                dragged += dx
                            },
                            onDragEnd = {
                                val open = FernPanels.open
                                if (open.size > 1 && kotlin.math.abs(dragged) >= swipePx) {
                                    if (dragged < 0) open.add(0, open.removeAt(open.lastIndex)) else open.add(open.removeAt(0))
                                }
                            },
                        )
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f).semantics { heading() }) {
                    Column {
                        ProvideTextStyle(MaterialTheme.typography.titleMedium) { entry.title() }
                        if (collapsed) entry.peek?.let { peek -> ProvideTextStyle(MaterialTheme.typography.bodySmall) { peek() } }
                    }
                }
                entry.headerAction?.invoke()
                IconButton(onClick = { collapsed = !collapsed }) {
                    Icon(
                        if (collapsed) FernIcons.ExpandLess else FernIcons.ExpandMore,
                        contentDescription = stringResource(if (collapsed) R.string.panel_expand else R.string.panel_collapse),
                    )
                }
                IconButton(onClick = { entry.onDismissRequest() }) {
                    Icon(FernIcons.Close, contentDescription = stringResource(R.string.action_close))
                }
            }
            if (!collapsed) {
                entry.onFilter?.let { onFilter ->
                    FernTextField(
                        filter,
                        {
                            filter = it
                            onFilter(it)
                        },
                        placeholder = { Text(stringResource(R.string.panel_filter)) },
                        leadingIcon = { Icon(FernIcons.Search, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = FernSpace.lg, vertical = FernSpace.xs),
                    )
                }
                HorizontalDivider()
                Box(Modifier.weight(1f, fill = false).padding(horizontal = FernSpace.lg, vertical = FernSpace.sm)) {
                    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                        ProvideTextStyle(MaterialTheme.typography.bodyMedium) {
                            if (entry.scrollContent) Box(Modifier.verticalScroll(rememberScrollState())) { entry.content() } else entry.content()
                        }
                    }
                }
                // Actions scroll sideways rather than squeeze on a phone (the layers panel has several).
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = FernSpace.sm), horizontalArrangement = Arrangement.End) { entry.actions() }
            }
        }
    }
}

// The sheet's share of the screen: half by default, between a quarter and three quarters.
private const val DEFAULT_SHEET_SHARE = 0.5f
private const val MIN_SHEET_SHARE = 0.25f
private const val MAX_SHEET_SHARE = 0.75f
private const val ONE_HAND_SHEET_SHARE = 1f / 3

/**
 * One row in a panel, the same everywhere (design roadmap D42): a tap runs [onClick], a long
 * press [onLongClick], and swiping it left deletes it through [onSwipeDelete], which should
 * offer Undo. Screen readers get delete as an action instead of the swipe.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun PanelRow(
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    onSwipeDelete: (() -> Unit)? = null,
    deleteLabel: String = "",
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    val row: @Composable () -> Unit = {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = FernSize.touchTarget)
                .background(MaterialTheme.colorScheme.surface)
                .then(
                    if (onClick != null || onLongClick != null) {
                        Modifier.combinedClickable(onClick = { onClick?.invoke() }, onLongClick = onLongClick)
                    } else {
                        Modifier
                    },
                )
                .then(
                    if (onSwipeDelete != null) {
                        Modifier.semantics {
                            customActions = listOf(androidx.compose.ui.semantics.CustomAccessibilityAction(deleteLabel) {
                                onSwipeDelete()
                                true
                            })
                        }
                    } else {
                        Modifier
                    },
                ),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
    if (onSwipeDelete == null) return row()
    val dismiss = androidx.compose.material3.rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == androidx.compose.material3.SwipeToDismissBoxValue.EndToStart) onSwipeDelete()
            false // the row goes when its item does; the swipe springs back
        },
    )
    androidx.compose.material3.SwipeToDismissBox(
        state = dismiss,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.errorContainer).padding(horizontal = FernSpace.lg), contentAlignment = Alignment.CenterEnd) {
                Icon(FernIcons.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
            }
        },
    ) { row() }
}
