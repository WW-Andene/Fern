package com.andene.fern.canvas

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
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Tune
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

@Composable
fun Toolbar(
    state: CanvasState,
    modifier: Modifier = Modifier,
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
    onImportPdf: () -> Unit = {},
    onOpenFrames: () -> Unit = {},
    onImportPalette: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenAudioNotes: () -> Unit = {},
) {
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
    var showMoreMenu by remember { mutableStateOf(false) }
    var showBlendModeMenu by remember { mutableStateOf(false) }
    var showBackgroundStyleMenu by remember { mutableStateOf(false) }
    var showBackgroundColorPicker by remember { mutableStateOf(false) }
    var showBrushEditor by remember { mutableStateOf(false) }
    var showGradientDialog by remember { mutableStateOf(false) }

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
            // Scrolls sideways: the tools add up to far more than a phone's width, and without
            // this everything past the screen edge was simply unreachable.
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
            ) {
                for (index in 0 until TOOL_SLOT_COUNT) ToolSlotButton(state, index)
                for (color in palette) {
                    val selected = state.activeTool == Tool.PEN && state.activeColor == color
                    Surface(
                        modifier = Modifier
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
                IconButton(onClick = { showColorPicker = true }) {
                    Icon(Icons.Filled.Palette, contentDescription = "Custom color")
                }
                IconButton(onClick = {
                    state.activeTool = if (state.activeTool == Tool.EYEDROPPER) Tool.PEN else Tool.EYEDROPPER
                }) {
                    Icon(
                        Icons.Filled.Colorize,
                        contentDescription = "Eyedropper",
                        tint = if (state.activeTool == Tool.EYEDROPPER) MaterialTheme.colorScheme.primary
                        else LocalContentColor.current,
                    )
                }
                Box {
                    IconButton(onClick = { showPenTypeMenu = true }) {
                        Icon(penTypeIcon(state.activePenType), contentDescription = "Pen type: ${penTypeLabel(state.activePenType)}")
                    }
                    DropdownMenu(expanded = showPenTypeMenu, onDismissRequest = { showPenTypeMenu = false }) {
                        for (type in PenType.entries) {
                            DropdownMenuItem(
                                text = { Text(penTypeLabel(type)) },
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
                        Icon(Icons.Filled.Tune, contentDescription = "Brush settings")
                    }
                }

                IconButton(onClick = {
                    state.activeTool = if (state.activeTool == Tool.ERASER) Tool.PEN else Tool.ERASER
                }) {
                    Icon(
                        Icons.AutoMirrored.Filled.Backspace,
                        contentDescription = "Eraser",
                        tint = if (state.activeTool == Tool.ERASER) MaterialTheme.colorScheme.primary
                        else LocalContentColor.current,
                    )
                }
                if (state.activeTool == Tool.ERASER) {
                    IconButton(onClick = { state.eraseWholeObjects = !state.eraseWholeObjects }) {
                        Icon(
                            if (state.eraseWholeObjects) Icons.Filled.CleaningServices else Icons.Filled.AutoFixNormal,
                            contentDescription = if (state.eraseWholeObjects) "Object eraser (tap for ink eraser)" else "Ink eraser (tap for object eraser)",
                        )
                    }
                    // Same lasso icon as lasso selection: one icon per meaning.
                    IconButton(onClick = { state.lassoErase = !state.lassoErase }) {
                        Icon(
                            Icons.Filled.Gesture,
                            contentDescription = if (state.lassoErase) "Lasso erase on (tap to rub out instead)" else "Lasso erase off (tap to erase inside a loop)",
                            tint = if (state.lassoErase) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                        )
                    }
                }
                IconButton(onClick = {
                    state.activeTool = if (state.activeTool == Tool.SELECT) Tool.PEN else Tool.SELECT
                }) {
                    Icon(
                        Icons.Filled.SelectAll,
                        contentDescription = "Select",
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
                            contentDescription = "Shape: ${shapeKindLabel(state.activeShapeKind)}",
                            tint = if (state.activeTool == Tool.SHAPE) MaterialTheme.colorScheme.primary
                            else LocalContentColor.current,
                        )
                    }
                    DropdownMenu(expanded = showShapeKindMenu, onDismissRequest = { showShapeKindMenu = false }) {
                        for (kind in ShapeKind.entries) {
                            DropdownMenuItem(
                                text = { Text(shapeKindLabel(kind)) },
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
                IconButton(onClick = { showWidthSlider = !showWidthSlider }) {
                    Icon(
                        Icons.Filled.FormatSize,
                        contentDescription = "Brush size",
                        tint = if (showWidthSlider) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                    )
                }
                IconButton(onClick = { showOpacitySlider = !showOpacitySlider }) {
                    Icon(
                        Icons.Filled.Opacity,
                        contentDescription = "Stroke opacity",
                        tint = if (showOpacitySlider) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                    )
                }
                IconButton(onClick = { state.activeDashed = !state.activeDashed }) {
                    Icon(
                        Icons.Filled.LineStyle,
                        contentDescription = if (state.activeDashed) "Dashed lines: on" else "Dashed lines: off",
                        tint = if (state.activeDashed) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                    )
                }
                IconButton(onClick = { state.velocityDynamics = !state.velocityDynamics }) {
                    Icon(
                        Icons.Filled.Speed,
                        contentDescription = if (state.velocityDynamics) "Speed changes line width: on" else "Speed changes line width: off",
                        tint = if (state.velocityDynamics) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                    )
                }
                IconButton(onClick = { showStabilizationSlider = !showStabilizationSlider }) {
                    Icon(
                        Icons.Filled.Timeline,
                        contentDescription = "Stroke stabilisation",
                        tint = if (showStabilizationSlider) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                    )
                }
                Box {
                    IconButton(onClick = { showSymmetryMenu = true }) {
                        Icon(
                            Icons.Filled.Flare,
                            contentDescription = "Symmetry: ${state.symmetryMode.label}",
                            tint = if (state.symmetryMode != SymmetryMode.OFF) MaterialTheme.colorScheme.primary
                            else LocalContentColor.current,
                        )
                    }
                    DropdownMenu(expanded = showSymmetryMenu, onDismissRequest = { showSymmetryMenu = false }) {
                        for (mode in SymmetryMode.entries) {
                            DropdownMenuItem(
                                text = { Text(mode.label) },
                                onClick = {
                                    state.setSymmetry(mode)
                                    showSymmetryMenu = false
                                },
                            )
                        }
                    }
                }
                IconButton(onClick = {
                    state.animationMode = !state.animationMode
                    state.playing = false
                }) {
                    Icon(
                        Icons.Filled.Movie,
                        contentDescription = if (state.animationMode) "Animation: on" else "Animation: off",
                        tint = if (state.animationMode) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                    )
                }
                IconButton(onClick = { showLayersDialog = true }) {
                    Icon(Icons.Filled.Layers, contentDescription = "Layers")
                }
                Box {
                    IconButton(onClick = { showBlendModeMenu = true }) {
                        Icon(
                            // Was the Layers icon before layers existed; that icon now opens the layer panel.
                            Icons.Filled.Tonality,
                            contentDescription = "Blend mode: ${blendModeLabel(state.activeBlendMode)}",
                            tint = if (state.activeBlendMode != StrokeBlendMode.NORMAL) MaterialTheme.colorScheme.primary
                            else LocalContentColor.current,
                        )
                    }
                    DropdownMenu(expanded = showBlendModeMenu, onDismissRequest = { showBlendModeMenu = false }) {
                        for (mode in StrokeBlendMode.entries) {
                            DropdownMenuItem(
                                text = { Text(blendModeLabel(mode)) },
                                onClick = {
                                    state.activeBlendMode = mode
                                    showBlendModeMenu = false
                                },
                            )
                        }
                    }
                }
                IconButton(onClick = {
                    state.activeTool = if (state.activeTool == Tool.FILL) Tool.PEN else Tool.FILL
                }) {
                    Icon(
                        Icons.Filled.FormatColorFill,
                        contentDescription = "Fill",
                        tint = if (state.activeTool == Tool.FILL) MaterialTheme.colorScheme.primary
                        else LocalContentColor.current,
                    )
                }
                if (state.activeTool == Tool.FILL) {
                    IconButton(onClick = { state.activeScreentone = !state.activeScreentone }) {
                        Icon(
                            Icons.Filled.BlurLinear,
                            contentDescription = if (state.activeScreentone) "Screentone fill: on" else "Screentone fill: off",
                            tint = if (state.activeScreentone) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                        )
                    }
                    val gradientOn = state.activeFadeFill || state.activeGradient != null
                    IconButton(onClick = { showGradientDialog = true }) {
                        Icon(
                            Icons.Filled.Gradient,
                            contentDescription = when {
                                state.activeGradient != null -> "Gradient fill: two colours"
                                state.activeFadeFill -> "Gradient fill: fade out"
                                else -> "Gradient fill: off"
                            },
                            tint = if (gradientOn) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                        )
                    }
                }
                IconButton(onClick = {
                    state.activeTool = if (state.activeTool == Tool.TEXT) Tool.PEN else Tool.TEXT
                }) {
                    Icon(
                        Icons.Filled.TextFields,
                        contentDescription = "Text",
                        tint = if (state.activeTool == Tool.TEXT) MaterialTheme.colorScheme.primary
                        else LocalContentColor.current,
                    )
                }
                if (state.activeTool == Tool.SELECT) {
                    IconButton(onClick = { state.selectAllOnActiveLayer() }) {
                        Icon(Icons.Filled.ViewModule, contentDescription = "Select all on this layer")
                    }
                    IconButton(onClick = { state.selectSameColor() }) {
                        Icon(Icons.Filled.ColorLens, contentDescription = "Select strokes of the same colour")
                    }
                    IconButton(onClick = { state.lassoSelect = !state.lassoSelect }) {
                        Icon(
                            if (state.lassoSelect) Icons.Filled.Gesture else Icons.Filled.HighlightAlt,
                            contentDescription = if (state.lassoSelect) "Lasso select (tap for rectangle)" else "Rectangle select (tap for lasso)",
                        )
                    }
                    IconButton(onClick = { state.reorderSelection(toFront = true) }) {
                        Icon(Icons.Filled.FlipToFront, contentDescription = "Bring selection to front")
                    }
                    IconButton(onClick = { state.reorderSelection(toFront = false) }) {
                        Icon(Icons.Filled.FlipToBack, contentDescription = "Send selection to back")
                    }
                    Box {
                        IconButton(onClick = { showAlignMenu = true }) {
                            Icon(Icons.Filled.AlignHorizontalLeft, contentDescription = "Align and distribute")
                        }
                        DropdownMenu(expanded = showAlignMenu, onDismissRequest = { showAlignMenu = false }) {
                            for (alignment in StrokeAlignment.entries) {
                                DropdownMenuItem(
                                    text = { Text(alignment.label) },
                                    onClick = {
                                        state.alignSelection(alignment)
                                        showAlignMenu = false
                                    },
                                )
                            }
                        }
                    }
                    IconButton(onClick = { state.animateAlongPath() }) {
                        Icon(Icons.Filled.Route, contentDescription = "Animate selection along the last drawn path")
                    }
                    IconButton(onClick = { state.straightenSelection() }) {
                        // LinearScale, not Straighten: that icon (a ruler) already means the ruler guide.
                        Icon(Icons.Filled.LinearScale, contentDescription = "Straighten selected strokes")
                    }
                    IconButton(onClick = { state.smoothSelection() }) {
                        Icon(Icons.Filled.Waves, contentDescription = "Smooth selected strokes")
                    }
                    IconButton(onClick = { state.applyWidthToSelection() }) {
                        Icon(Icons.Filled.LineWeight, contentDescription = "Apply current brush width to selection")
                    }
                    IconButton(onClick = { state.recolorSelection() }) {
                        Icon(Icons.Filled.FormatPaint, contentDescription = "Apply current colour to selection")
                    }
                    IconButton(onClick = { state.copySelection() }) {
                        Icon(Icons.Filled.CopyAll, contentDescription = "Copy selection")
                    }
                    IconButton(onClick = { state.paste() }, enabled = state.hasClipboard) {
                        Icon(Icons.Filled.ContentPaste, contentDescription = "Paste")
                    }
                    IconButton(onClick = { state.duplicateSelection() }) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = "Duplicate selection")
                    }
                    IconButton(onClick = { state.deleteSelection() }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete selection")
                    }
                }

                IconButton(onClick = { state.undo() }) {
                    Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
                }
                IconButton(onClick = { state.redo() }) {
                    Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo")
                }
                IconButton(onClick = { state.clear() }) {
                    Icon(Icons.Filled.DeleteSweep, contentDescription = "Clear all")
                }
                IconButton(onClick = { state.resetView() }) {
                    Icon(Icons.Filled.ZoomOutMap, contentDescription = "Reset view")
                }
                IconButton(onClick = { state.zoomToFit() }) {
                    Icon(Icons.Filled.CenterFocusStrong, contentDescription = "Zoom to fit content")
                }
                IconButton(onClick = onOpenDocuments) {
                    Icon(Icons.Filled.Description, contentDescription = "Documents")
                }
                // Occasional, dialog-opening actions live in an overflow menu so the toolbar row
                // keeps the tools used while drawing within reach.
                Box {
                    IconButton(onClick = { showMoreMenu = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More")
                    }
                    DropdownMenu(expanded = showMoreMenu, onDismissRequest = { showMoreMenu = false }) {
                        val moreItems = listOf(
                            Triple("Insert image", Icons.Filled.Image, onInsertImage),
                            Triple("Import PDF to annotate", Icons.Filled.PictureAsPdf, onImportPdf),
                            Triple("Search text", Icons.Filled.Search) { showSearchDialog = true },
                            Triple("Pins", Icons.Filled.PushPin, onOpenPins),
                            Triple("Frames", Icons.Filled.CropFree, onOpenFrames),
                            Triple("Voice notes", Icons.Filled.Mic, onOpenAudioNotes),
                            Triple("Version history", Icons.Filled.History, onOpenVersions),
                            Triple("Settings", Icons.Filled.Settings, onOpenSettings),
                        )
                        for ((label, icon, action) in moreItems) {
                            DropdownMenuItem(
                                text = { Text(label) },
                                leadingIcon = { Icon(icon, contentDescription = null) },
                                onClick = {
                                    showMoreMenu = false
                                    action()
                                },
                            )
                        }
                    }
                }
                IconButton(onClick = { state.replayCount = if (state.replayCount == null) 0 else null }) {
                    Icon(
                        Icons.Filled.Replay,
                        contentDescription = if (state.replayCount == null) "Replay how this drawing was made" else "Stop replay",
                        tint = if (state.replayCount != null) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                    )
                }
                Box {
                    IconButton(onClick = { showExportMenu = true }) {
                        Icon(Icons.Filled.IosShare, contentDescription = "Export")
                    }
                    DropdownMenu(expanded = showExportMenu, onDismissRequest = { showExportMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Share…") },
                            onClick = {
                                showExportMenu = false
                                onShare()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Image (PNG)") },
                            onClick = {
                                showExportMenu = false
                                onExportPng()
                            },
                        )
                        if (state.selection.isNotEmpty()) {
                            DropdownMenuItem(
                                text = { Text("Selected area (PNG)") },
                                onClick = {
                                    showExportMenu = false
                                    onExportSelection()
                                },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Image, transparent (PNG)") },
                            onClick = {
                                showExportMenu = false
                                onExportTransparentPng()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("PDF") },
                            onClick = {
                                showExportMenu = false
                                onExportPdf()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Vector (SVG)") },
                            onClick = {
                                showExportMenu = false
                                onExportSvg()
                            },
                        )
                        if (state.animFrameCount > 1) {
                            DropdownMenuItem(
                                text = { Text("Animated GIF") },
                                onClick = {
                                    showExportMenu = false
                                    onExportGif()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Animation sprite sheet (PNG)") },
                                onClick = {
                                    showExportMenu = false
                                    onExportSpriteSheet()
                                },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Fern backup (.fern)") },
                            onClick = {
                                showExportMenu = false
                                onExportBundle()
                            },
                        )
                    }
                }
                Box {
                    IconButton(onClick = { showBackgroundStyleMenu = true }) {
                        Icon(Icons.Filled.GridOn, contentDescription = "Background: ${backgroundStyleLabel(state.backgroundStyle)}")
                    }
                    DropdownMenu(expanded = showBackgroundStyleMenu, onDismissRequest = { showBackgroundStyleMenu = false }) {
                        for (style in BackgroundStyle.entries) {
                            DropdownMenuItem(
                                text = { Text(backgroundStyleLabel(style)) },
                                onClick = {
                                    state.setBackground(state.backgroundColor, style)
                                    showBackgroundStyleMenu = false
                                },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Background color…") },
                            onClick = {
                                showBackgroundStyleMenu = false
                                showBackgroundColorPicker = true
                            },
                        )
                    }
                }
                IconButton(onClick = {
                    state.toggleRuler()
                    state.activeTool = if (state.rulerActive) Tool.RULER
                    else if (state.activeTool == Tool.RULER) Tool.PEN else state.activeTool
                }) {
                    Icon(
                        Icons.Filled.Straighten,
                        contentDescription = "Ruler guide",
                        tint = if (state.rulerActive) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                    )
                }
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
    val description = if (preset == null) "Empty favourite ${index + 1}, tap to save the current pen"
    else "Favourite ${index + 1}: ${penTypeLabel(preset.penType)}"
    Box {
        Box(
            modifier = Modifier
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
                text = { Text("Save current pen here") },
                onClick = {
                    state.saveToolSlot(index)
                    showMenu = false
                },
            )
            if (preset != null) {
                DropdownMenuItem(
                    text = { Text("Clear") },
                    onClick = {
                        state.clearToolSlot(index)
                        showMenu = false
                    },
                )
            }
        }
    }
}

private fun penTypeIcon(penType: PenType) = when (penType) {
    PenType.MARKER -> Icons.Filled.Brush
    PenType.PENCIL -> Icons.Filled.Edit
    PenType.HIGHLIGHTER -> Icons.Filled.Highlight
    PenType.CALLIGRAPHY -> Icons.Filled.Draw
    PenType.AIRBRUSH -> Icons.Filled.BlurOn
    PenType.CRAYON -> Icons.Filled.Create
    PenType.PIXEL -> Icons.Filled.Apps
    PenType.STAMP -> Icons.Filled.Approval
}

private fun penTypeLabel(penType: PenType) = when (penType) {
    PenType.MARKER -> "Marker"
    PenType.PENCIL -> "Pencil"
    PenType.HIGHLIGHTER -> "Highlighter"
    PenType.CALLIGRAPHY -> "Calligraphy"
    PenType.AIRBRUSH -> "Airbrush"
    PenType.CRAYON -> "Crayon"
    PenType.PIXEL -> "Pixel art"
    PenType.STAMP -> "Stamp brush"
}

private fun shapeKindIcon(kind: ShapeKind) = when (kind) {
    ShapeKind.LINE -> Icons.Filled.Remove
    ShapeKind.RECTANGLE -> Icons.Filled.CropSquare
    ShapeKind.ELLIPSE -> Icons.Filled.Circle
    ShapeKind.ARROW -> Icons.AutoMirrored.Filled.ArrowForward
    ShapeKind.SPEECH_BUBBLE -> Icons.Filled.ChatBubbleOutline
}

private fun shapeKindLabel(kind: ShapeKind) = when (kind) {
    ShapeKind.LINE -> "Line"
    ShapeKind.RECTANGLE -> "Rectangle"
    ShapeKind.ELLIPSE -> "Ellipse"
    ShapeKind.ARROW -> "Arrow"
    ShapeKind.SPEECH_BUBBLE -> "Speech bubble"
}

private fun blendModeLabel(mode: StrokeBlendMode) = when (mode) {
    StrokeBlendMode.NORMAL -> "Normal"
    StrokeBlendMode.MULTIPLY -> "Multiply"
    StrokeBlendMode.SCREEN -> "Screen"
}

private fun backgroundStyleLabel(style: BackgroundStyle) = when (style) {
    BackgroundStyle.PLAIN -> "Plain"
    BackgroundStyle.GRID -> "Grid"
    BackgroundStyle.DOT -> "Dot grid"
    BackgroundStyle.RULED -> "Ruled"
}
