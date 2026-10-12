package com.andene.fern.canvas

import androidx.compose.material.icons.filled.TextRotationAngleup
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material.icons.filled.JoinFull
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pentagon
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.annotation.StringRes
import com.andene.fern.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AlignHorizontalLeft
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Approval
import androidx.compose.material.icons.filled.AutoFixNormal
import androidx.compose.material.icons.filled.BlurLinear
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Colorize
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.CopyAll
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flare
import androidx.compose.material.icons.filled.FlipToBack
import androidx.compose.material.icons.filled.FlipToFront
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material.icons.filled.FormatPaint
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.Gradient
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.GroupWork
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.HighlightAlt
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LineStyle
import androidx.compose.material.icons.filled.LineWeight
import androidx.compose.material.icons.filled.LinearScale
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Polyline
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Schema
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Tonality
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

private val palette = listOf(
    Color(0xFF1B1B1B),
    Color(0xFFE53935),
    Color(0xFF1E88E5),
    Color(0xFF43A047),
    Color(0xFFFB8C00),
)

// Screen-pixel pen width range: matches CanvasState's default of 4.0 and stays reasonable
// across the full slider (a 1px pen is barely visible; 32px is a thick marker).
private const val MIN_WIDTH_SCREEN = 1f
private const val MAX_WIDTH_SCREEN = 32f

// A fully transparent stroke would be pointless and indistinguishable from "nothing drawn" -
// this floor keeps the lightest setting still faintly visible.
private const val MIN_OPACITY = 0.1f

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Toolbar(
    state: CanvasState,
    modifier: Modifier = Modifier,
    /** Button groups the user turned off in Settings (roadmap item 71); see [ToolbarGroup]. */
    hiddenGroups: Set<ToolbarGroup> = emptySet(),
    /** Wrap the tools onto several rows instead of scrolling them sideways: for wide windows (roadmap item 74). */
    wrap: Boolean = false,
    onOpenDocuments: () -> Unit = {},
    onOpenPins: () -> Unit = {},
    onOpenVersions: () -> Unit = {},
    onInsertImage: () -> Unit = {},
    onExportPng: () -> Unit = {},
    onExportPdf: () -> Unit = {},
    onExportTransparentPng: () -> Unit = {},
    onExportSelection: () -> Unit = {},
    onExportSvg: () -> Unit = {},
    onShare: () -> Unit = {},
    onExportBundle: () -> Unit = {},
    onExportSpriteSheet: () -> Unit = {},
    onExportGif: () -> Unit = {},
    onExportApng: () -> Unit = {},
    onExportLottie: () -> Unit = {},
    onExportReplayGif: () -> Unit = {},
    onImportPdf: () -> Unit = {},
    onOpenFrames: () -> Unit = {},
    onImportPalette: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenAudioNotes: () -> Unit = {},
    /** Copy the selection to the system clipboard; see [SystemClipboard]. */
    onCopySelection: () -> Unit = {},
    /** Paste whatever the system clipboard holds: Fern strokes, text or an image. */
    onPaste: () -> Unit = {},
    /** Start dragging the selection to another window; called as a drag begins on its handle. */
    onDragSelectionOut: () -> Unit = {},
) {
    val dragOutLabel = stringResource(R.string.tb_drag_out)
    var showExportMenu by remember { mutableStateOf(false) }
    var showWidthSlider by remember { mutableStateOf(false) }
    var showColorPicker by remember { mutableStateOf(false) }
    var showPenTypeMenu by remember { mutableStateOf(false) }
    var showShapeKindMenu by remember { mutableStateOf(false) }
    var showOpacitySlider by remember { mutableStateOf(false) }
    var showStabilizationSlider by remember { mutableStateOf(false) }
    var showLayersDialog by remember { mutableStateOf(false) }
    var showSearchDialog by remember { mutableStateOf(false) }
    var showSymmetryMenu by remember { mutableStateOf(false) }
    var showAlignMenu by remember { mutableStateOf(false) }
    var showLineStyleMenu by remember { mutableStateOf(false) }
    var showCombineMenu by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var showBlendModeMenu by remember { mutableStateOf(false) }
    var showBackgroundStyleMenu by remember { mutableStateOf(false) }
    var showBackgroundColorPicker by remember { mutableStateOf(false) }
    var showBrushEditor by remember { mutableStateOf(false) }
    var showGradientDialog by remember { mutableStateOf(false) }
    var showPerspectiveMenu by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        tonalElevation = 4.dp,
        shadowElevation = 4.dp,
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            // Saved palette colours (squares; long-press removes one), then recently used colours
            // (circles) not already shown - both smaller than the fixed palette's swatches.
            val recents = state.recentColors.filter { it !in palette && it !in state.savedColors }
            if (state.savedColors.isNotEmpty() || recents.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(bottom = 8.dp),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                ) {
                    for (color in state.savedColors) {
                        Surface(
                            modifier = Modifier
                                .size(24.dp)
                                .pointerInput(color) {
                                    detectTapGestures(
                                        onTap = {
                                            state.activeColor = color
                                            state.activeTool = Tool.PEN
                                        },
                                        onLongPress = { state.removeSavedColor(color) },
                                    )
                                },
                            shape = RoundedCornerShape(6.dp), // §7.3: 0.24 × 24 = 5.8, nearest scale value 6
                            color = color,
                        ) {}
                    }
                    for (color in recents) {
                        Surface(
                            modifier = Modifier
                                .size(24.dp)
                                .clickable {
                                    state.activeColor = color
                                    state.activeTool = Tool.PEN
                                },
                            shape = CircleShape,
                            color = color,
                        ) {}
                    }
                }
            }
            // Every tool, in toolbar order. On a phone they scroll sideways: they add up to far
            // more than its width, and without scrolling everything past the edge was unreachable.
            // On a wide window (roadmap item 74) they wrap onto further rows instead, all in view.
            val tools: @Composable () -> Unit = {
                if (ToolbarGroup.FAVOURITES !in hiddenGroups) {
                    for (index in 0 until TOOL_SLOT_COUNT) ToolSlotButton(state, index)
                }
                if (ToolbarGroup.PALETTE !in hiddenGroups) {
                    for (color in palette) {
                        val selected = state.activeTool == Tool.PEN && state.activeColor == color
                        Surface(
                            modifier = Modifier
                                // 48dp tall like the buttons beside it, so wrapped rows line up.
                                .padding(vertical = 8.dp)
                                .size(32.dp)
                                .clickable {
                                    state.activeColor = color
                                    state.activeTool = Tool.PEN // picking a color implies "draw", per §6 baseline expectations
                                }
                                .then(
                                    if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                    else Modifier
                                ),
                            shape = CircleShape,
                            color = color,
                        ) {}
                    }
                }
                IconButton(onClick = { showColorPicker = true }) {
                    Icon(Icons.Filled.Palette, contentDescription = stringResource(R.string.tb_custom_color))
                }
                IconButton(onClick = {
                    state.activeTool = if (state.activeTool == Tool.EYEDROPPER) Tool.PEN else Tool.EYEDROPPER
                }) {
                    Icon(
                        Icons.Filled.Colorize,
                        contentDescription = stringResource(R.string.tb_eyedropper),
                        tint = if (state.activeTool == Tool.EYEDROPPER) MaterialTheme.colorScheme.primary
                        else LocalContentColor.current,
                    )
                }
                Box {
                    IconButton(onClick = { showPenTypeMenu = true }) {
                        Icon(penTypeIcon(state.activePenType), contentDescription = stringResource(R.string.tb_pen_type, stringResource(penTypeLabel(state.activePenType))))
                    }
                    DropdownMenu(expanded = showPenTypeMenu, onDismissRequest = { showPenTypeMenu = false }) {
                        for (type in PenType.entries) {
                            DropdownMenuItem(
                                text = { Text(stringResource(penTypeLabel(type))) },
                                leadingIcon = { Icon(penTypeIcon(type), contentDescription = null) },
                                onClick = {
                                    state.activePenType = type
                                    state.activeTool = Tool.PEN
                                    showPenTypeMenu = false
                                },
                            )
                        }
                    }
                }
                if (state.activePenType == PenType.STAMP) {
                    IconButton(onClick = { showBrushEditor = true }) {
                        Icon(Icons.Filled.Tune, contentDescription = stringResource(R.string.brush_settings))
                    }
                }

                IconButton(onClick = {
                    state.activeTool = if (state.activeTool == Tool.ERASER) Tool.PEN else Tool.ERASER
                }) {
                    Icon(
                        Icons.AutoMirrored.Filled.Backspace,
                        contentDescription = stringResource(R.string.tb_eraser),
                        tint = if (state.activeTool == Tool.ERASER) MaterialTheme.colorScheme.primary
                        else LocalContentColor.current,
                    )
                }
                if (state.activeTool == Tool.ERASER) {
                    IconButton(onClick = { state.eraseWholeObjects = !state.eraseWholeObjects }) {
                        Icon(
                            if (state.eraseWholeObjects) Icons.Filled.CleaningServices else Icons.Filled.AutoFixNormal,
                            contentDescription = if (state.eraseWholeObjects) stringResource(R.string.tb_object_eraser) else stringResource(R.string.tb_ink_eraser),
                        )
                    }
                    // Same lasso icon as lasso selection: one icon per meaning.
                    IconButton(onClick = { state.lassoErase = !state.lassoErase }) {
                        Icon(
                            Icons.Filled.Gesture,
                            contentDescription = if (state.lassoErase) stringResource(R.string.tb_lasso_erase_on) else stringResource(R.string.tb_lasso_erase_off),
                            tint = if (state.lassoErase) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                        )
                    }
                }
                IconButton(onClick = {
                    state.activeTool = if (state.activeTool == Tool.SELECT) Tool.PEN else Tool.SELECT
                }) {
                    Icon(
                        Icons.Filled.SelectAll,
                        contentDescription = stringResource(R.string.action_select),
                        tint = if (state.activeTool == Tool.SELECT) MaterialTheme.colorScheme.primary
                        else LocalContentColor.current,
                    )
                }
                Box {
                    IconButton(onClick = {
                        if (state.activeTool == Tool.SHAPE) showShapeKindMenu = true
                        else state.activeTool = Tool.SHAPE
                    }) {
                        Icon(
                            shapeKindIcon(state.activeShapeKind),
                            contentDescription = stringResource(R.string.tb_shape, stringResource(shapeKindLabel(state.activeShapeKind))),
                            tint = if (state.activeTool == Tool.SHAPE) MaterialTheme.colorScheme.primary
                            else LocalContentColor.current,
                        )
                    }
                    DropdownMenu(expanded = showShapeKindMenu, onDismissRequest = { showShapeKindMenu = false }) {
                        for (kind in ShapeKind.entries) {
                            DropdownMenuItem(
                                text = { Text(stringResource(shapeKindLabel(kind))) },
                                leadingIcon = { Icon(shapeKindIcon(kind), contentDescription = null) },
                                onClick = {
                                    state.activeShapeKind = kind
                                    state.activeTool = Tool.SHAPE
                                    showShapeKindMenu = false
                                },
                            )
                        }
                    }
                }
                // Bézier pen (roadmap item 96): tap for corners, drag for curves, tap the first node to close.
                IconButton(onClick = { state.activeTool = if (state.activeTool == Tool.BEZIER) Tool.PEN else Tool.BEZIER }) {
                    Icon(
                        Icons.Filled.Polyline,
                        contentDescription = stringResource(R.string.tb_bezier),
                        tint = if (state.activeTool == Tool.BEZIER) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                    )
                }
                if (state.activeTool == Tool.BEZIER && state.bezierNodes.isNotEmpty()) {
                    IconButton(onClick = { state.finishBezierPath(closed = false) }, enabled = state.bezierNodes.size >= 2) {
                        Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.tb_bezier_finish))
                    }
                    IconButton(onClick = { state.finishBezierPath(closed = true) }, enabled = state.bezierNodes.size >= 3) {
                        Icon(Icons.Filled.Pentagon, contentDescription = stringResource(R.string.tb_bezier_close))
                    }
                    IconButton(onClick = { state.removeLastBezierNode() }) {
                        Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = stringResource(R.string.tb_bezier_remove_node))
                    }
                    IconButton(onClick = { state.cancelBezierPath() }) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.tb_bezier_cancel))
                    }
                }
                IconButton(onClick = { showWidthSlider = !showWidthSlider }) {
                    Icon(
                        Icons.Filled.FormatSize,
                        contentDescription = stringResource(R.string.tb_brush_size),
                        tint = if (showWidthSlider) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                    )
                }
                IconButton(onClick = { showOpacitySlider = !showOpacitySlider }) {
                    Icon(
                        Icons.Filled.Opacity,
                        contentDescription = stringResource(R.string.tb_opacity),
                        tint = if (showOpacitySlider) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                    )
                }
                if (ToolbarGroup.STROKE_OPTIONS !in hiddenGroups) {
                    IconButton(onClick = { state.activeDashed = !state.activeDashed }) {
                        Icon(
                            Icons.Filled.LineStyle,
                            contentDescription = if (state.activeDashed) stringResource(R.string.tb_dashed_on) else stringResource(R.string.tb_dashed_off),
                            tint = if (state.activeDashed) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                        )
                    }
                    // Line style (roadmap item 97): caps, joins and width profile for new strokes.
                    Box {
                        IconButton(onClick = { showLineStyleMenu = true }) {
                            Icon(
                                Icons.Filled.LineStyle,
                                contentDescription = stringResource(R.string.tb_line_style),
                                tint = if (state.activeLineStyle != LineStyle()) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                            )
                        }
                        DropdownMenu(expanded = showLineStyleMenu, onDismissRequest = { showLineStyleMenu = false }) {
                            val current = state.activeLineStyle
                            for (profile in WidthProfile.entries) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(profile.label)) },
                                    leadingIcon = { if (current.profile == profile) Icon(Icons.Filled.Check, contentDescription = null) },
                                    onClick = { state.activeLineStyle = current.copy(profile = profile) },
                                )
                            }
                            HorizontalDivider()
                            for (cap in LineCap.entries) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(cap.label)) },
                                    leadingIcon = { if (current.cap == cap) Icon(Icons.Filled.Check, contentDescription = null) },
                                    onClick = { state.activeLineStyle = current.copy(cap = cap) },
                                )
                            }
                            HorizontalDivider()
                            for (join in LineJoin.entries) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(join.label)) },
                                    leadingIcon = { if (current.join == join) Icon(Icons.Filled.Check, contentDescription = null) },
                                    onClick = { state.activeLineStyle = current.copy(join = join) },
                                )
                            }
                        }
                    }
                    IconButton(onClick = { state.velocityDynamics = !state.velocityDynamics }) {
                        Icon(
                            Icons.Filled.Speed,
                            contentDescription = if (state.velocityDynamics) stringResource(R.string.tb_speed_on) else stringResource(R.string.tb_speed_off),
                            tint = if (state.velocityDynamics) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                        )
                    }
                    IconButton(onClick = { showStabilizationSlider = !showStabilizationSlider }) {
                        Icon(
                            Icons.Filled.Timeline,
                            contentDescription = stringResource(R.string.tb_stabilisation),
                            tint = if (showStabilizationSlider) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                        )
                    }
                }
                if (ToolbarGroup.GUIDES !in hiddenGroups) {
                    Box {
                        IconButton(onClick = { showSymmetryMenu = true }) {
                            Icon(
                                Icons.Filled.Flare,
                                contentDescription = stringResource(R.string.tb_symmetry, stringResource(state.symmetryMode.label)),
                                tint = if (state.symmetryMode != SymmetryMode.OFF) MaterialTheme.colorScheme.primary
                                else LocalContentColor.current,
                            )
                        }
                        DropdownMenu(expanded = showSymmetryMenu, onDismissRequest = { showSymmetryMenu = false }) {
                            for (mode in SymmetryMode.entries) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(mode.label)) },
                                    onClick = {
                                        state.setSymmetry(mode)
                                        showSymmetryMenu = false
                                    },
                                )
                            }
                        }
                    }
                    Box {
                        IconButton(onClick = { showPerspectiveMenu = true }) {
                            Icon(
                                Icons.Filled.ViewInAr,
                                contentDescription = stringResource(R.string.tb_perspective, stringResource(state.perspectiveMode.label)),
                                tint = if (state.perspectiveMode != PerspectiveMode.OFF) MaterialTheme.colorScheme.primary
                                else LocalContentColor.current,
                            )
                        }
                        DropdownMenu(expanded = showPerspectiveMenu, onDismissRequest = { showPerspectiveMenu = false }) {
                            for (mode in PerspectiveMode.entries) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(mode.label)) },
                                    onClick = {
                                        state.setPerspective(mode)
                                        showPerspectiveMenu = false
                                    },
                                )
                            }
                        }
                    }
                }
                if (ToolbarGroup.ANIMATION !in hiddenGroups) {
                    IconButton(onClick = {
                        state.animationMode = !state.animationMode
                        state.playing = false
                    }) {
                        Icon(
                            Icons.Filled.Movie,
                            contentDescription = if (state.animationMode) stringResource(R.string.tb_anim_on) else stringResource(R.string.tb_anim_off),
                            tint = if (state.animationMode) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                        )
                    }
                    // Skeleton (roadmap item 83): draw bones, then pose them frame by frame.
                    IconButton(onClick = { state.activeTool = if (state.activeTool == Tool.BONE) Tool.PEN else Tool.BONE }) {
                        Icon(
                            Icons.Filled.AccessibilityNew,
                            contentDescription = stringResource(R.string.tb_bones),
                            tint = if (state.activeTool == Tool.BONE) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                        )
                    }
                    if (state.activeTool == Tool.BONE) {
                        IconButton(onClick = { state.posingBones = !state.posingBones }) {
                            Icon(
                                if (state.posingBones) Icons.Filled.PanTool else Icons.Filled.AddCircleOutline,
                                contentDescription = if (state.posingBones) stringResource(R.string.tb_bones_pose) else stringResource(R.string.tb_bones_add),
                            )
                        }
                        IconButton(onClick = { state.smoothJoints = !state.smoothJoints }) {
                            Icon(
                                Icons.Filled.Gesture,
                                contentDescription = if (state.smoothJoints) stringResource(R.string.tb_joints_smooth) else stringResource(R.string.tb_joints_rigid),
                                tint = if (state.smoothJoints) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                            )
                        }
                        IconButton(onClick = { state.inverseKinematics = !state.inverseKinematics }) {
                            Icon(
                                Icons.Filled.Timeline,
                                contentDescription = if (state.inverseKinematics) stringResource(R.string.tb_ik_on) else stringResource(R.string.tb_ik_off),
                                tint = if (state.inverseKinematics) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                            )
                        }
                        IconButton(onClick = { state.bindStrokesToNearestBones() }, enabled = state.currentBones.isNotEmpty()) {
                            Icon(Icons.Filled.Link, contentDescription = stringResource(R.string.tb_bones_bind))
                        }
                        IconButton(onClick = { state.deleteSelectedBone() }, enabled = state.selectedBoneId != null) {
                            Icon(Icons.Filled.DeleteOutline, contentDescription = stringResource(R.string.tb_bones_delete))
                        }
                    }
                }
                if (ToolbarGroup.LAYERS !in hiddenGroups) {
                    IconButton(onClick = { showLayersDialog = true }) {
                        Icon(Icons.Filled.Layers, contentDescription = stringResource(R.string.layers_title))
                    }
                    Box {
                        IconButton(onClick = { showBlendModeMenu = true }) {
                            Icon(
                                // Was the Layers icon before layers existed; that icon now opens the layer panel.
                                Icons.Filled.Tonality,
                                contentDescription = stringResource(R.string.tb_blend, stringResource(blendModeLabel(state.activeBlendMode))),
                                tint = if (state.activeBlendMode != StrokeBlendMode.NORMAL) MaterialTheme.colorScheme.primary
                                else LocalContentColor.current,
                            )
                        }
                        DropdownMenu(expanded = showBlendModeMenu, onDismissRequest = { showBlendModeMenu = false }) {
                            for (mode in StrokeBlendMode.entries) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(blendModeLabel(mode))) },
                                    onClick = {
                                        state.activeBlendMode = mode
                                        showBlendModeMenu = false
                                    },
                                )
                            }
                        }
                    }
                }
                if (ToolbarGroup.FILL !in hiddenGroups) {
                    IconButton(onClick = {
                        state.activeTool = if (state.activeTool == Tool.FILL) Tool.PEN else Tool.FILL
                    }) {
                        Icon(
                            Icons.Filled.FormatColorFill,
                            contentDescription = stringResource(R.string.tb_fill),
                            tint = if (state.activeTool == Tool.FILL) MaterialTheme.colorScheme.primary
                            else LocalContentColor.current,
                        )
                    }
                    if (state.activeTool == Tool.FILL) {
                        IconButton(onClick = { state.activeScreentone = !state.activeScreentone }) {
                            Icon(
                                Icons.Filled.BlurLinear,
                                contentDescription = if (state.activeScreentone) stringResource(R.string.tb_screentone_on) else stringResource(R.string.tb_screentone_off),
                                tint = if (state.activeScreentone) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                            )
                        }
                        val gradientOn = state.activeFadeFill || state.activeGradient != null
                        IconButton(onClick = { showGradientDialog = true }) {
                            Icon(
                                Icons.Filled.Gradient,
                                contentDescription = when {
                                    state.activeGradient != null -> stringResource(R.string.tb_gradient_two)
                                    state.activeFadeFill -> stringResource(R.string.tb_gradient_fade)
                                    else -> stringResource(R.string.tb_gradient_off)
                                },
                                tint = if (gradientOn) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                            )
                        }
                    }
                }
                if (ToolbarGroup.TEXT !in hiddenGroups) {
                    IconButton(onClick = {
                        state.activeTool = if (state.activeTool == Tool.TEXT) Tool.PEN else Tool.TEXT
                    }) {
                        Icon(
                            Icons.Filled.TextFields,
                            contentDescription = stringResource(R.string.text_label),
                            tint = if (state.activeTool == Tool.TEXT) MaterialTheme.colorScheme.primary
                            else LocalContentColor.current,
                        )
                    }
                    IconButton(onClick = {
                        state.activeTool = if (state.activeTool == Tool.CONNECT) Tool.PEN else Tool.CONNECT
                    }) {
                        Icon(
                            Icons.Filled.AccountTree,
                            contentDescription = stringResource(R.string.tb_connect),
                            tint = if (state.activeTool == Tool.CONNECT) MaterialTheme.colorScheme.primary
                            else LocalContentColor.current,
                        )
                    }
                    if (state.activeTool == Tool.CONNECT) {
                        IconButton(onClick = { state.tidyDiagram() }, enabled = state.connectors.isNotEmpty()) {
                            Icon(Icons.Filled.Schema, contentDescription = stringResource(R.string.tb_tidy))
                        }
                    }
                }
                if (state.activeTool == Tool.SELECT) {
                    IconButton(onClick = { state.selectAllOnActiveLayer() }) {
                        Icon(Icons.Filled.ViewModule, contentDescription = stringResource(R.string.tb_select_layer))
                    }
                    IconButton(onClick = { state.selectSameColor() }) {
                        Icon(Icons.Filled.ColorLens, contentDescription = stringResource(R.string.tb_select_colour))
                    }
                    IconButton(onClick = { state.lassoSelect = !state.lassoSelect }) {
                        Icon(
                            if (state.lassoSelect) Icons.Filled.Gesture else Icons.Filled.HighlightAlt,
                            contentDescription = if (state.lassoSelect) stringResource(R.string.tb_lasso_select) else stringResource(R.string.tb_rect_select),
                        )
                    }
                    IconButton(onClick = { state.reorderSelection(toFront = true) }) {
                        Icon(Icons.Filled.FlipToFront, contentDescription = stringResource(R.string.tb_front))
                    }
                    IconButton(onClick = { state.reorderSelection(toFront = false) }) {
                        Icon(Icons.Filled.FlipToBack, contentDescription = stringResource(R.string.tb_back))
                    }
                    Box {
                        IconButton(onClick = { showAlignMenu = true }) {
                            Icon(Icons.Filled.AlignHorizontalLeft, contentDescription = stringResource(R.string.tb_align))
                        }
                        DropdownMenu(expanded = showAlignMenu, onDismissRequest = { showAlignMenu = false }) {
                            for (alignment in StrokeAlignment.entries) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(alignment.label)) },
                                    onClick = {
                                        state.alignSelection(alignment)
                                        showAlignMenu = false
                                    },
                                )
                            }
                        }
                    }
                    Box {
                        IconButton(onClick = { showCombineMenu = true }, enabled = state.selection.size >= 2) {
                            Icon(Icons.Filled.JoinFull, contentDescription = stringResource(R.string.tb_combine))
                        }
                        DropdownMenu(expanded = showCombineMenu, onDismissRequest = { showCombineMenu = false }) {
                            for ((op, label) in listOf(
                                ShapeOp.UNION to R.string.combine_union, ShapeOp.INTERSECT to R.string.combine_intersect,
                                ShapeOp.SUBTRACT to R.string.combine_subtract, ShapeOp.EXCLUDE to R.string.combine_exclude,
                            )) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(label)) },
                                    onClick = {
                                        state.combineSelection(op)
                                        showCombineMenu = false
                                    },
                                )
                            }
                        }
                    }
                    IconButton(onClick = { state.beginTextOnPath() }, enabled = state.selection.size == 1) {
                        Icon(Icons.Filled.TextRotationAngleup, contentDescription = stringResource(R.string.tb_text_on_path))
                    }
                    IconButton(onClick = { state.animateAlongPath() }) {
                        Icon(Icons.Filled.Route, contentDescription = stringResource(R.string.tb_motion_path))
                    }
                    IconButton(onClick = { state.straightenSelection() }) {
                        // LinearScale, not Straighten: that icon (a ruler) already means the ruler guide.
                        Icon(Icons.Filled.LinearScale, contentDescription = stringResource(R.string.tb_straighten))
                    }
                    IconButton(onClick = { state.smoothSelection() }) {
                        Icon(Icons.Filled.Waves, contentDescription = stringResource(R.string.tb_smooth))
                    }
                    IconButton(onClick = { state.applyWidthToSelection() }) {
                        Icon(Icons.Filled.LineWeight, contentDescription = stringResource(R.string.tb_apply_width))
                    }
                    IconButton(onClick = { state.recolorSelection() }) {
                        Icon(Icons.Filled.FormatPaint, contentDescription = stringResource(R.string.tb_apply_colour))
                    }
                    IconButton(onClick = onCopySelection) {
                        Icon(Icons.Filled.CopyAll, contentDescription = stringResource(R.string.tb_copy))
                    }
                    // Drag-only: dragging from this handle carries the selection to another window
                    // (Copy and Paste do the same without a gesture).
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .semantics { contentDescription = dragOutLabel }
                            .pointerInput(Unit) { detectDragGestures(onDragStart = { onDragSelectionOut() }) { change, _ -> change.consume() } },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.OpenWith, contentDescription = null)
                    }
                    IconButton(onClick = onPaste) {
                        Icon(Icons.Filled.ContentPaste, contentDescription = stringResource(R.string.action_paste))
                    }
                    IconButton(onClick = { state.duplicateSelection() }) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = stringResource(R.string.tb_duplicate))
                    }
                    IconButton(onClick = { state.toggleNodeEditing() }, enabled = state.selection.size == 1) {
                        Icon(
                            Icons.Filled.Polyline,
                            contentDescription = if (state.nodeEditing) stringResource(R.string.tb_nodes_stop) else stringResource(R.string.tb_nodes),
                            tint = if (state.nodeEditing) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                        )
                    }
                    val grouped = state.selectionHasGroup
                    IconButton(
                        onClick = { if (grouped) state.ungroupSelection() else state.groupSelection() },
                        enabled = grouped || state.selection.size >= 2,
                    ) {
                        Icon(
                            Icons.Filled.GroupWork,
                            contentDescription = if (grouped) stringResource(R.string.tb_ungroup) else stringResource(R.string.tb_group),
                            tint = if (grouped) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                        )
                    }
                    IconButton(onClick = { state.deleteSelection() }) {
                        Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.tb_delete))
                    }
                }

                IconButton(onClick = { state.undo() }) {
                    Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = stringResource(R.string.action_undo))
                }
                IconButton(onClick = { state.redo() }) {
                    Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = stringResource(R.string.action_redo))
                }
                if (ToolbarGroup.VIEW !in hiddenGroups) {
                    IconButton(onClick = { state.clear() }) {
                        Icon(Icons.Filled.DeleteSweep, contentDescription = stringResource(R.string.tb_clear_all))
                    }
                    IconButton(onClick = { state.resetView() }) {
                        Icon(Icons.Filled.ZoomOutMap, contentDescription = stringResource(R.string.tb_reset_view))
                    }
                    IconButton(onClick = { state.zoomToFit() }) {
                        Icon(Icons.Filled.CenterFocusStrong, contentDescription = stringResource(R.string.tb_fit))
                    }
                }
                IconButton(onClick = onOpenDocuments) {
                    Icon(Icons.Filled.Description, contentDescription = stringResource(R.string.documents_title))
                }
                // Occasional, dialog-opening actions live in an overflow menu so the toolbar row
                // keeps the tools used while drawing within reach.
                Box {
                    IconButton(onClick = { showMoreMenu = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.tb_more))
                    }
                    DropdownMenu(expanded = showMoreMenu, onDismissRequest = { showMoreMenu = false }) {
                        val moreItems = listOf(
                            Triple(R.string.menu_insert_image, Icons.Filled.Image, onInsertImage),
                            Triple(R.string.menu_import_pdf, Icons.Filled.PictureAsPdf, onImportPdf),
                            Triple(R.string.search_title, Icons.Filled.Search) { showSearchDialog = true },
                            Triple(R.string.pins_title, Icons.Filled.PushPin, onOpenPins),
                            Triple(R.string.frames_title, Icons.Filled.CropFree, onOpenFrames),
                            Triple(R.string.audio_title, Icons.Filled.Mic, onOpenAudioNotes),
                            Triple(R.string.versions_title, Icons.Filled.History, onOpenVersions),
                            Triple(R.string.settings_title, Icons.Filled.Settings, onOpenSettings),
                        )
                        for ((label, icon, action) in moreItems) {
                            DropdownMenuItem(
                                text = { Text(stringResource(label)) },
                                leadingIcon = { Icon(icon, contentDescription = null) },
                                onClick = {
                                    showMoreMenu = false
                                    action()
                                },
                            )
                        }
                    }
                }
                if (ToolbarGroup.REPLAY !in hiddenGroups) {
                    IconButton(onClick = { state.replayCount = if (state.replayCount == null) 0 else null }) {
                        Icon(
                            Icons.Filled.Replay,
                            contentDescription = if (state.replayCount == null) stringResource(R.string.tb_replay) else stringResource(R.string.tb_stop_replay),
                            tint = if (state.replayCount != null) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                        )
                    }
                }
                Box {
                    IconButton(onClick = { showExportMenu = true }) {
                        Icon(Icons.Filled.IosShare, contentDescription = stringResource(R.string.tb_export))
                    }
                    DropdownMenu(expanded = showExportMenu, onDismissRequest = { showExportMenu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.tb_share)) },
                            onClick = {
                                showExportMenu = false
                                onShare()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.tb_export_png)) },
                            onClick = {
                                showExportMenu = false
                                onExportPng()
                            },
                        )
                        if (state.selection.isNotEmpty()) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.tb_export_selection)) },
                                onClick = {
                                    showExportMenu = false
                                    onExportSelection()
                                },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.tb_export_transparent)) },
                            onClick = {
                                showExportMenu = false
                                onExportTransparentPng()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.tb_export_pdf)) },
                            onClick = {
                                showExportMenu = false
                                onExportPdf()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.tb_export_svg)) },
                            onClick = {
                                showExportMenu = false
                                onExportSvg()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.tb_export_replay)) },
                            onClick = {
                                showExportMenu = false
                                onExportReplayGif()
                            },
                        )
                        if (state.animFrameCount > 1) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.tb_export_gif)) },
                                onClick = {
                                    showExportMenu = false
                                    onExportGif()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.tb_export_apng)) },
                                onClick = {
                                    showExportMenu = false
                                    onExportApng()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.tb_export_lottie)) },
                                onClick = {
                                    showExportMenu = false
                                    onExportLottie()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.tb_export_sprites)) },
                                onClick = {
                                    showExportMenu = false
                                    onExportSpriteSheet()
                                },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.tb_export_fern)) },
                            onClick = {
                                showExportMenu = false
                                onExportBundle()
                            },
                        )
                    }
                }
                if (ToolbarGroup.BACKGROUND !in hiddenGroups) {
                    Box {
                        IconButton(onClick = { showBackgroundStyleMenu = true }) {
                            Icon(Icons.Filled.GridOn, contentDescription = stringResource(R.string.tb_background, stringResource(backgroundStyleLabel(state.backgroundStyle))))
                        }
                        DropdownMenu(expanded = showBackgroundStyleMenu, onDismissRequest = { showBackgroundStyleMenu = false }) {
                            for (style in BackgroundStyle.entries) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(backgroundStyleLabel(style))) },
                                    onClick = {
                                        state.setBackground(state.backgroundColor, style)
                                        showBackgroundStyleMenu = false
                                    },
                                )
                            }
                            for (paper in PaperPreset.entries) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(paper.label)) },
                                    onClick = {
                                        state.applyPaperPreset(paper)
                                        showBackgroundStyleMenu = false
                                    },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.tb_background_color)) },
                                onClick = {
                                    showBackgroundStyleMenu = false
                                    showBackgroundColorPicker = true
                                },
                            )
                        }
                    }
                }
                if (ToolbarGroup.GUIDES !in hiddenGroups) {
                    IconButton(onClick = {
                        state.toggleRuler()
                        state.activeTool = if (state.rulerActive) Tool.RULER
                        else if (state.activeTool == Tool.RULER) Tool.PEN else state.activeTool
                    }) {
                        Icon(
                            Icons.Filled.Straighten,
                            contentDescription = stringResource(R.string.tb_ruler),
                            tint = if (state.rulerActive) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                        )
                    }
                }
                        }
            if (wrap) {
                FlowRow(
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                ) { tools() }
            } else {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                ) { tools() }
            }
            if (showWidthSlider) {
                Slider(
                    value = state.activeWidthWorld.toFloat(),
                    onValueChange = { state.activeWidthWorld = it.toDouble() },
                    valueRange = MIN_WIDTH_SCREEN..MAX_WIDTH_SCREEN,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .width(192.dp),
                )
            }
            if (showOpacitySlider) {
                Slider(
                    value = state.activeOpacity.toFloat(),
                    onValueChange = { state.activeOpacity = it.toDouble() },
                    valueRange = MIN_OPACITY..1f,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .width(192.dp),
                )
            }
            if (showStabilizationSlider) {
                Slider(
                    value = state.activeStabilization.toFloat(),
                    onValueChange = { state.activeStabilization = it.toDouble() },
                    valueRange = 0f..CanvasState.MAX_STABILIZATION.toFloat(),
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .width(192.dp),
                )
            }
        }
    }

    if (showSearchDialog) {
        SearchDialog(
            textItems = state.textItems,
            onJump = { item ->
                state.panTo(item.position)
                showSearchDialog = false
            },
            onDismiss = { showSearchDialog = false },
        )
    }

    if (showLayersDialog) {
        LayersDialog(state = state, onDismiss = { showLayersDialog = false })
    }

    if (showColorPicker) {
        ColorPickerDialog(
            initialColor = state.activeColor,
            onDismiss = { showColorPicker = false },
            onColorSelected = { color ->
                state.activeColor = color
                state.activeTool = Tool.PEN
                showColorPicker = false
            },
            onSaveColor = { color -> state.addSavedColor(color) },
            onImportPalette = {
                showColorPicker = false
                onImportPalette()
            },
        )
    }

    if (showGradientDialog) {
        GradientFillDialog(
            startColor = state.activeColor,
            initialMode = when {
                state.activeGradient != null -> GradientMode.TWO_COLOURS
                state.activeFadeFill -> GradientMode.FADE
                else -> GradientMode.NONE
            },
            initialGradient = state.activeGradient,
            onApply = { mode, gradient ->
                state.activeFadeFill = mode == GradientMode.FADE
                state.activeGradient = gradient
                showGradientDialog = false
            },
            onDismiss = { showGradientDialog = false },
        )
    }

    if (showBrushEditor) {
        BrushEditorDialog(
            initialBrush = state.activeBrush,
            color = state.activeColor,
            onApply = { brush ->
                state.setBrush(brush)
                state.activeTool = Tool.PEN
                showBrushEditor = false
            },
            onDismiss = { showBrushEditor = false },
            presets = state.brushPresets,
            onSavePreset = { name, brush -> state.saveBrushPreset(name, brush) },
            onDeletePreset = { name -> state.deleteBrushPreset(name) },
        )
    }

    if (showBackgroundColorPicker) {
        ColorPickerDialog(
            initialColor = state.backgroundColor,
            onDismiss = { showBackgroundColorPicker = false },
            onColorSelected = { color ->
                state.setBackground(color, state.backgroundStyle)
                showBackgroundColorPicker = false
            },
        )
    }
}

/**
 * One favourite tool slot. Tap: recall the saved pen, or save the current one into an empty
 * slot. Touch and hold: a menu to replace the slot with the current pen or clear it.
 */
@Composable
private fun ToolSlotButton(state: CanvasState, index: Int) {
    val preset = state.toolSlots[index]
    var showMenu by remember { mutableStateOf(false) }
    val description = if (preset == null) stringResource(R.string.tb_slot_empty, index + 1)
    else stringResource(R.string.tb_slot, index + 1, stringResource(penTypeLabel(preset.penType)))
    Box {
        Box(
            modifier = Modifier
                // 48dp tall like the buttons beside it, so wrapped rows line up.
                .padding(vertical = 8.dp)
                .size(32.dp)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)) // §7.3: 0.24 × 32 = 7.7 → 8
                .semantics { contentDescription = description }
                .pointerInput(preset) {
                    detectTapGestures(
                        onTap = { if (preset == null) state.saveToolSlot(index) else state.applyToolSlot(index) },
                        onLongPress = { showMenu = true },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            if (preset == null) {
                Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(16.dp))
            } else {
                Icon(penTypeIcon(preset.penType), contentDescription = null, tint = preset.color, modifier = Modifier.size(16.dp))
            }
        }
        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.tb_slot_save)) },
                onClick = {
                    state.saveToolSlot(index)
                    showMenu = false
                },
            )
            if (preset != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_clear)) },
                    onClick = {
                        state.clearToolSlot(index)
                        showMenu = false
                    },
                )
            }
        }
    }
}

internal fun penTypeIcon(penType: PenType) = when (penType) {
    PenType.MARKER -> Icons.Filled.Brush
    PenType.PENCIL -> Icons.Filled.Edit
    PenType.HIGHLIGHTER -> Icons.Filled.Highlight
    PenType.CALLIGRAPHY -> Icons.Filled.Draw
    PenType.AIRBRUSH -> Icons.Filled.BlurOn
    PenType.CRAYON -> Icons.Filled.Create
    PenType.PIXEL -> Icons.Filled.Apps
    PenType.STAMP -> Icons.Filled.Approval
}

@StringRes
private fun penTypeLabel(penType: PenType) = when (penType) {
    PenType.MARKER -> R.string.pen_marker
    PenType.PENCIL -> R.string.pen_pencil
    PenType.HIGHLIGHTER -> R.string.pen_highlighter
    PenType.CALLIGRAPHY -> R.string.pen_calligraphy
    PenType.AIRBRUSH -> R.string.pen_airbrush
    PenType.CRAYON -> R.string.pen_crayon
    PenType.PIXEL -> R.string.pen_pixel
    PenType.STAMP -> R.string.brush_title
}

internal fun shapeKindIcon(kind: ShapeKind) = when (kind) {
    ShapeKind.LINE -> Icons.Filled.Remove
    ShapeKind.RECTANGLE -> Icons.Filled.CropSquare
    ShapeKind.ELLIPSE -> Icons.Filled.Circle
    ShapeKind.ARROW -> Icons.AutoMirrored.Filled.ArrowForward
    ShapeKind.SPEECH_BUBBLE -> Icons.Filled.ChatBubbleOutline
}

@StringRes
private fun shapeKindLabel(kind: ShapeKind) = when (kind) {
    ShapeKind.LINE -> R.string.shape_line
    ShapeKind.RECTANGLE -> R.string.shape_rectangle
    ShapeKind.ELLIPSE -> R.string.shape_ellipse
    ShapeKind.ARROW -> R.string.shape_arrow
    ShapeKind.SPEECH_BUBBLE -> R.string.shape_bubble
}

@StringRes
private fun blendModeLabel(mode: StrokeBlendMode) = when (mode) {
    StrokeBlendMode.NORMAL -> R.string.blend_normal
    StrokeBlendMode.MULTIPLY -> R.string.blend_multiply
    StrokeBlendMode.SCREEN -> R.string.blend_screen
}

@StringRes
private fun backgroundStyleLabel(style: BackgroundStyle) = when (style) {
    BackgroundStyle.PLAIN -> R.string.paper_plain
    BackgroundStyle.GRID -> R.string.paper_grid
    BackgroundStyle.DOT -> R.string.template_dot
    BackgroundStyle.RULED -> R.string.paper_ruled
}
