package com.andene.fern.canvas

import com.andene.fern.FernPanel
import com.andene.fern.FernTextField
import com.andene.fern.FernTextButton
import com.andene.fern.FernSlider
import androidx.compose.ui.platform.LocalView

import com.andene.fern.FernHaptics

import com.andene.fern.FernMessages
import com.andene.fern.MoreInline
import com.andene.fern.FernScale

import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.Fence
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Toys
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled._3dRotation
import android.content.Context

import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.ViewQuilt
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Adjust
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Interests
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.InvertColors
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.AutoFixHigh
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Transform
import androidx.compose.material.icons.filled.TextRotationAngleup
import androidx.compose.material3.minimumInteractiveComponentSize
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
import androidx.compose.material.icons.filled.SquareFoot
import androidx.compose.material.icons.filled.BorderColor
import androidx.compose.material.icons.filled.Texture
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.VerticalSplit
import androidx.compose.material.icons.filled.SportsGymnastics
import androidx.compose.material.icons.filled.FormatShapes
import androidx.compose.material.icons.filled.ContentPasteGo
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.Gradient
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Deselect
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.GroupWork
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.HighlightAlt
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.AddBox
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LineStyle
import androidx.compose.material.icons.filled.LineWeight
import androidx.compose.material.icons.filled.LinearScale
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PanoramaFishEye
import androidx.compose.material.icons.filled.TripOrigin
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Reorder
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
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics

private val palette = listOf(
    Color(0xFF1B1B1B),
    Color(0xFFE53935),
    Color(0xFF1E88E5),
    Color(0xFF43A047),
    Color(0xFFFB8C00),
)

// Screen-pixel pen width range: matches CanvasState's default of 4.0 and stays reasonable
// across the full slider (a 1px pen is barely visible; 32px is a thick marker).
internal const val MIN_WIDTH_SCREEN = 1f
internal const val MAX_WIDTH_SCREEN = 32f

// A fully transparent stroke would be pointless and indistinguishable from "nothing drawn" -
// this floor keeps the lightest setting still faintly visible.
internal const val MIN_OPACITY = 0.1f

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Toolbar(
    state: CanvasState,
    modifier: Modifier = Modifier,
    /** Button groups the user turned off in Settings (roadmap item 71); see [ToolbarGroup]. */
    hiddenGroups: Set<ToolbarGroup> = emptySet(),
    onOpenDocuments: () -> Unit = {},
    onOpenPins: () -> Unit = {},
    onOpenVersions: () -> Unit = {},
    onInsertImage: () -> Unit = {},
    onExportPng: () -> Unit = {},
    onExportPdf: () -> Unit = {},
    onExportTransparentPng: () -> Unit = {},
    onExportSelection: () -> Unit = {},
    onExportSvg: () -> Unit = {},
    onExportEps: () -> Unit = {},
    /** Illustrator (.ai, PDF-compatible) export (roadmap item 103). */
    onExportAi: () -> Unit = {},
    onExportDxf: () -> Unit = {},
    onExportCmykPdf: () -> Unit = {},
    onExportExr: () -> Unit = {},
    onShare: () -> Unit = {},
    onExportBundle: () -> Unit = {},
    onExportSpriteSheet: () -> Unit = {},
    onExportSpriteAtlas: () -> Unit = {},
    onExportGif: () -> Unit = {},
    onExportApng: () -> Unit = {},
    onExportLottie: () -> Unit = {},
    onExportReplayGif: () -> Unit = {},
    onExportReplayMp4: () -> Unit = {},
    onExportMp4: () -> Unit = {},
    onExportWebm: () -> Unit = {},
    onImportPdf: () -> Unit = {},
    onImportSpine: () -> Unit = {},
    onImportLayered: () -> Unit = {},
    /** Vector import of a PDF or Illustrator file (roadmap item 103). */
    onImportVectors: () -> Unit = {},
    onExportPsd: () -> Unit = {},
    onExportKra: () -> Unit = {},
    onExportSpine: () -> Unit = {},
    onExportDragonBones: () -> Unit = {},
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
    /** Trace the selected image into vector shapes (roadmap item 101). */
    onTraceImage: () -> Unit = {},
    /** Embeds a video (roadmap item 107). */
    onInsertVideo: () -> Unit = {},
    /** Imports an OBJ model to paint in 3D (roadmap item 108). */
    onImportObj: () -> Unit = {},
    /** Commands the palette offers beside the toolbar's own, e.g. recent documents (design roadmap D14). */
    extraCommands: List<PaletteCommand> = emptyList(),
    /** Called when a workspace (design roadmap D11) changes which groups show. */
    onHiddenGroupsChange: (Set<ToolbarGroup>) -> Unit = {},
    /** Where the contextual properties bar goes (design roadmap D9). */
    propertiesModifier: Modifier = Modifier,
) {
    val dragOutLabel = stringResource(R.string.tb_drag_out)
    var showExportMenu by remember { mutableStateOf(false) }
    var showMore by remember { mutableStateOf(false) }
    var showWidthSlider by remember { mutableStateOf(false) }
    var showColorPicker by remember { mutableStateOf(false) }
    var showBrushLibrary by remember { mutableStateOf(false) }
    var showShapeKindMenu by remember { mutableStateOf(false) }
    var showOpacitySlider by remember { mutableStateOf(false) }
    var showStabilizationSlider by remember { mutableStateOf(false) }
    var showLayersDialog by remember { mutableStateOf(false) }
    var showSearchDialog by remember { mutableStateOf(false) }
    var showSymmetryMenu by remember { mutableStateOf(false) }
    var showAlignMenu by remember { mutableStateOf(false) }
    var showStatsDialog by remember { mutableStateOf(false) }
    if (showStatsDialog) StatsDialog(state, onDismiss = { showStatsDialog = false })
    var showSignatureDialog by remember { mutableStateOf(false) }
    var showQrDialog by remember { mutableStateOf(false) }
    // Game objects (roadmap item 155).
    var editingObject by remember { mutableStateOf<Pair<GameObject, List<WorldPoint>?>?>(null) }
    editingObject?.let { (target, path) -> GameObjectDialog(state, target, path, onDismiss = { editingObject = null }) }
    var showPlugins by remember { mutableStateOf(false) }
    if (showPlugins) PluginsDialog(state, onDismiss = { showPlugins = false })
    // 3D painting (roadmap item 108): adding a model, and the model open in the editor.
    var showInsert3d by remember { mutableStateOf(false) }
    var editingModel by remember { mutableStateOf<Pair<ImageItem, Model3d>?>(null) }
    run {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        if (showInsert3d) {
            Insert3dDialog(
                onPrimitive = { primitive ->
                    showInsert3d = false
                    scope.launch {
                        val stored = withContext(Dispatchers.Default) { storeModel(context, newModel(primitiveMesh(primitive))) }
                        state.placeModel(stored)
                    }
                },
                onHand = {
                    showInsert3d = false
                    scope.launch { state.placeModel(withContext(Dispatchers.Default) { storeModel(context, newHandModel()) }) }
                },
                onHead = {
                    showInsert3d = false
                    scope.launch { state.placeModel(withContext(Dispatchers.Default) { storeModel(context, newHeadModel()) }) }
                },
                onImportObj = {
                    showInsert3d = false
                    onImportObj()
                },
                onDismiss = { showInsert3d = false },
            )
        }
        editingModel?.let { (item, model) ->
            Model3dEditor(
                model,
                initialColor = state.activeColor,
                onSave = { painted ->
                    editingModel = null
                    scope.launch {
                        val stored = withContext(Dispatchers.Default) { storeModel(context, painted) }
                        state.replaceImageModel(item.id, stored.posterFileName, stored.modelFileName, stored.aspect)
                    }
                },
                onDismiss = { editingModel = null },
            )
        }
    }
    fun openModel(item: ImageItem, context: Context, scope: CoroutineScope) {
        val name = item.modelFileName ?: return
        scope.launch {
            val model = withContext(Dispatchers.IO) { runCatching { Model3dFile.read(CanvasStorage.modelFile(context, name).readBytes()) }.getOrNull() }
            if (model != null) editingModel = item to model
        }
    }
    var playingVideo by remember { mutableStateOf<ImageItem?>(null) }
    playingVideo?.let { VideoPlayerDialog(it, onDismiss = { playingVideo = null }) }
    var showComicDialog by remember { mutableStateOf(false) }
    if (showComicDialog) ComicDialog(state, onDismiss = { showComicDialog = false })
    if (showQrDialog) QrCodeDialog(state, onDismiss = { showQrDialog = false })
    if (showSignatureDialog) {
        val context = LocalContext.current
        SignatureDialog(
            saved = remember { SignatureStore.load(context) },
            onPlace = { signature ->
                SignatureStore.save(context, signature)
                state.placeSignature(signature)
                showSignatureDialog = false
            },
            onDismiss = { showSignatureDialog = false },
        )
    }
    val view = LocalView.current
    // Destructive actions say what they did and offer the way back (design roadmap D6).
    val deletedMessage = stringResource(R.string.msg_deleted)
    val clearedMessage = stringResource(R.string.msg_cleared)
    val undoLabel = stringResource(R.string.action_undo)
    var showScriptDialog by remember { mutableStateOf(false) }
    if (showScriptDialog) {
        ScriptDialog(
            onRun = { lines ->
                state.insertScriptLines(lines)
                showScriptDialog = false
            },
            onDismiss = { showScriptDialog = false },
        )
    }
    // Symbol library (roadmap item 100), read and written off the main thread.
    val context = LocalContext.current
    val symbolScope = rememberCoroutineScope()
    var symbols by remember { mutableStateOf<List<SymbolMeta>?>(null) }
    fun openSymbols() {
        symbolScope.launch { symbols = withContext(Dispatchers.IO) { SymbolLibrary.list(context) } }
    }
    symbols?.let { list ->
        SymbolsDialog(
            symbols = list,
            canSaveSelection = state.selection.isNotEmpty(),
            selectedInstanceOf = state.selectedSymbolInstance?.let { instance -> list.firstOrNull { it.id == instance.symbolId } },
            onRedefine = {
                val (instance, master) = state.symbolRedefinitionFromSelection() ?: return@SymbolsDialog
                symbolScope.launch {
                    val meta = withContext(Dispatchers.IO) { SymbolLibrary.update(context, instance.symbolId, master) }
                    if (meta != null) state.syncSymbol(meta, master)
                    symbols = null
                }
            },
            onDetach = {
                state.detachSymbolInstance()
                symbols = null
            },
            onInsert = { symbol ->
                symbolScope.launch {
                    val strokes = withContext(Dispatchers.IO) { SymbolLibrary.load(context, symbol.id) }
                    state.activeTool = Tool.SELECT
                    state.placeSymbol(symbol, strokes)
                    symbols = null
                }
            },
            onDelete = { symbol ->
                symbolScope.launch { symbols = withContext(Dispatchers.IO) { SymbolLibrary.delete(context, symbol.id); SymbolLibrary.list(context) } }
            },
            onSaveSelection = { name ->
                val strokes = state.selectionForClipboard()
                val fallback = context.getString(R.string.symbols_default_name, list.size + 1)
                symbolScope.launch {
                    symbols = withContext(Dispatchers.IO) { SymbolLibrary.save(context, name.ifBlank { fallback }, strokes); SymbolLibrary.list(context) }
                }
            },
            onDismiss = { symbols = null },
        )
    }
    var editingSavedColor by remember { mutableStateOf<Color?>(null) }
    editingSavedColor?.let { old ->
        ColorPickerDialog(
            initialColor = old,
            onDismiss = { editingSavedColor = null },
            onColorSelected = { new ->
                state.replaceColorEverywhere(old, new)
                editingSavedColor = null
            },
        )
    }
    var showTransformDialog by remember { mutableStateOf(false) }
    // The selection strip asks for these (design roadmap D39); their dialogs live here.
    if (SelectionRequests.transform) {
        SelectionRequests.transform = false
        showTransformDialog = true
    }
    val stripObjectName = stringResource(R.string.object_default_name, state.gameObjects.size + 1)
    if (SelectionRequests.makeObject) {
        SelectionRequests.makeObject = false
        editingObject = state.objectForEditing(stripObjectName)
    }
    if (showTransformDialog) {
        val bounds = remember { state.selectionBounds() }
        if (bounds == null) showTransformDialog = false
        else TransformDialog(
            bounds,
            unitsPerWorld = state.drawingScale.factor,
            unitSymbol = state.drawingScale.symbol,
            onApply = { left, top, width, height, rotation ->
                state.transformPreview = null
                state.transformSelection(left, top, width, height, rotation)
                showTransformDialog = false
            },
            onDismiss = {
                state.transformPreview = null
                showTransformDialog = false
            },
            onPreview = { left, top, width, height, rotation -> state.transformPreview = TransformPreview(left, top, width, height, rotation) },
        )
    }
    var showLineStyleMenu by remember { mutableStateOf(false) }
    var showCombineMenu by remember { mutableStateOf(false) }
    var showContourDialog by remember { mutableStateOf(false) }
    var showRotatePixels by remember { mutableStateOf(false) }
    if (showRotatePixels) RotatePixelsDialog(state, onDismiss = { showRotatePixels = false })
    var showDrawingScaleDialog by remember { mutableStateOf(false) }
    if (showDrawingScaleDialog) DrawingScaleDialog(state, onDismiss = { showDrawingScaleDialog = false })
    var showPlaceOnPlaneMenu by remember { mutableStateOf(false) }
    var showLinkMenu by remember { mutableStateOf(false) }
    var showControls by remember { mutableStateOf(false) }
    if (showControls) InputMappingDialog(state, onDismiss = { showControls = false })
    var showConstraintsDialog by remember { mutableStateOf(false) }
    if (showConstraintsDialog) ConstraintsDialog(state, onDismiss = { showConstraintsDialog = false })
    if (showContourDialog) ContourDialog(state, onDismiss = { showContourDialog = false })
    var showBlendModeMenu by remember { mutableStateOf(false) }
    var showBackgroundStyleMenu by remember { mutableStateOf(false) }
    var showBackgroundColorPicker by remember { mutableStateOf(false) }
    var showBrushEditor by remember { mutableStateOf(false) }
    var showGradientDialog by remember { mutableStateOf(false) }
    var showPerspectiveMenu by remember { mutableStateOf(false) }

    val insertItems = listOf<Triple<Int, androidx.compose.ui.graphics.vector.ImageVector, () -> Unit>>(
        Triple(R.string.menu_insert_image, Icons.Filled.Image, onInsertImage),
        Triple(R.string.menu_insert_video, Icons.Filled.VideoFile, onInsertVideo),
        Triple(R.string.model_insert_title, Icons.Filled._3dRotation) { showInsert3d = true },
        Triple(R.string.symbols_title, Icons.Filled.Category) { openSymbols() },
        Triple(R.string.signature_title, Icons.Filled.Draw) { showSignatureDialog = true },
        Triple(R.string.qr_title, Icons.Filled.QrCode2) { showQrDialog = true },
        Triple(R.string.comic_title, Icons.Filled.AutoStories) { showComicDialog = true },
    )
    val importItems = listOf<Triple<Int, androidx.compose.ui.graphics.vector.ImageVector, () -> Unit>>(
        Triple(R.string.menu_import_pdf, Icons.Filled.PictureAsPdf, onImportPdf),
        Triple(R.string.menu_import_vectors, Icons.Filled.Polyline, onImportVectors),
        Triple(R.string.menu_import_layered, Icons.Filled.Layers, onImportLayered),
        Triple(R.string.menu_import_spine, Icons.Filled.AccessibilityNew, onImportSpine),
    )
    val documentItems = listOf<Triple<Int, androidx.compose.ui.graphics.vector.ImageVector, () -> Unit>>(
        Triple(R.string.search_title, Icons.Filled.Search) { showSearchDialog = true },
        Triple(R.string.frames_title, Icons.Filled.CropFree, onOpenFrames),
        Triple(R.string.pins_title, Icons.Filled.PushPin, onOpenPins),
        Triple(R.string.versions_title, Icons.Filled.History, onOpenVersions),
        Triple(R.string.audio_title, Icons.Filled.Mic, onOpenAudioNotes),
        Triple(R.string.stats_title, Icons.Filled.QueryStats) { showStatsDialog = true },
        Triple(R.string.script_title, Icons.Filled.Code) { showScriptDialog = true },
        Triple(R.string.second_view_title, Icons.Filled.VerticalSplit) { state.secondViewOpen = !state.secondViewOpen },
        Triple(if (state.showRulers) R.string.rulers_hide else R.string.rulers_show, Icons.Filled.SquareFoot) { state.showRulers = !state.showRulers },
        Triple(if (state.wrapFrameId == null) R.string.wrap_on else R.string.wrap_off, Icons.Filled.Texture) { state.toggleWrapAround() },
    )
    val context0 = LocalContext.current
    // Recording macros (design roadmap D34).
    var macros by remember { mutableStateOf(Macros.saved(context0)) }
    var namingMacro by remember { mutableStateOf<List<QuickMenuAction>?>(null) }
    val macroLocation = stringResource(R.string.macro_location)
    namingMacro?.let { steps ->
        var name by remember { mutableStateOf("") }
        FernPanel(
            scrollContent = true,
            onDismissRequest = { namingMacro = null },
            title = { Text(stringResource(R.string.macro_name_title, steps.size)) },
            text = { FernTextField(name, { name = it }, singleLine = true) },
            confirmButton = {
                FernTextButton(enabled = name.isNotBlank(), onClick = {
                    macros = macros + Macro(name.trim(), steps)
                    Macros.setSaved(context0, macros)
                    namingMacro = null
                }) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = { FernTextButton(onClick = { namingMacro = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    val appItems = listOf<Triple<Int, androidx.compose.ui.graphics.vector.ImageVector, () -> Unit>>(
        if (Macros.recording == null) Triple(R.string.macro_record, Icons.Filled.FiberManualRecord) { Macros.startRecording() }
        else Triple(R.string.macro_stop, Icons.Filled.Stop) { namingMacro = Macros.stopRecording() },
        Triple(R.string.settings_title, Icons.Filled.Settings, onOpenSettings),
        Triple(R.string.plugins_title, Icons.Filled.Extension) { showPlugins = true },
    )
    val runExport: (ExportFormat) -> Unit = { format ->
        when (format) {
            ExportFormat.SHARE -> onShare()
            ExportFormat.PNG -> onExportPng()
            ExportFormat.TRANSPARENT_PNG -> onExportTransparentPng()
            ExportFormat.SELECTION_PNG -> onExportSelection()
            ExportFormat.EXR -> onExportExr()
            ExportFormat.PDF -> onExportPdf()
            ExportFormat.CMYK_PDF -> onExportCmykPdf()
            ExportFormat.SVG -> onExportSvg()
            ExportFormat.EPS -> onExportEps()
            ExportFormat.AI -> onExportAi()
            ExportFormat.DXF -> onExportDxf()
            ExportFormat.PSD -> onExportPsd()
            ExportFormat.KRA -> onExportKra()
            ExportFormat.FERN -> onExportBundle()
            ExportFormat.GIF -> onExportGif()
            ExportFormat.MP4 -> onExportMp4()
            ExportFormat.WEBM -> onExportWebm()
            ExportFormat.APNG -> onExportApng()
            ExportFormat.LOTTIE -> onExportLottie()
            ExportFormat.REPLAY_GIF -> onExportReplayGif()
            ExportFormat.REPLAY_MP4 -> onExportReplayMp4()
            ExportFormat.SPRITE_SHEET -> onExportSpriteSheet()
            ExportFormat.SPRITE_ATLAS -> onExportSpriteAtlas()
            ExportFormat.SPINE -> onExportSpine()
            ExportFormat.DRAGON_BONES -> onExportDragonBones()
        }
    }
    // Every action by name, in either language (design roadmap D14).
    val labels = rememberCommandLabels()
    val paletteCommands = buildList {
        val toolShortcuts = mapOf(Tool.PEN to "P", Tool.ERASER to "E", Tool.SELECT to "V")
        for (tool in Tool.entries) add(labels.command(toolName(tool), R.string.palette_location_rail, toolShortcuts[tool]) {
            state.activeTool = tool
            UsageHints.toolReachedIndirectly(context0, tool)
        })
        add(labels.command(R.string.action_undo, R.string.palette_location_toolbar, "Ctrl+Z") { state.undo() })
        add(labels.command(R.string.action_redo, R.string.palette_location_toolbar, "Ctrl+Y") { state.redo() })
        add(labels.command(R.string.layers_title, R.string.palette_location_toolbar) { showLayersDialog = true })
        add(labels.command(R.string.documents_title, R.string.palette_location_toolbar, run = onOpenDocuments))
        add(labels.command(R.string.tb_custom_color, R.string.palette_location_toolbar) { showColorPicker = true })
        for ((group, items) in listOf(R.string.menu_group_insert to insertItems, R.string.menu_group_import to importItems, R.string.menu_group_document to documentItems, R.string.menu_group_app to appItems)) {
            for ((label, _, action) in items) add(labels.command(label, group, run = action))
        }
        for (format in ExportFormat.entries) if (format.available(state)) add(labels.command(format.label, R.string.tb_export) { runExport(format) })
        // Actions and macros (design roadmap D34): run through Macros so they can be repeated and recorded.
        for (action in QuickMenuAction.entries) add(labels.command(action.label, R.string.quick_menu) { Macros.perform(state, action) })
        add(labels.command(R.string.macro_repeat_last, R.string.menu_group_document, "Ctrl+.") { Macros.repeatLast(state) })
        for (macro in macros) add(PaletteCommand(macro.name, macro.name, macroLocation) { Macros.run(state, macro) })
        addAll(extraCommands)
    }
    if (CommandPaletteState.open) CommandPalette(paletteCommands, onDismiss = { CommandPaletteState.open = false })
    // Contextual properties (design roadmap D9): only the active tool's settings and the
    // selection's actions, in their own bar beside the rail instead of mixed into the toolbar.
    val properties: @Composable () -> Unit = {
                // One colour strip for every tool that draws in colour (design roadmap D20).
                if (state.activeTool == Tool.PEN || state.activeTool == Tool.SHAPE || state.activeTool == Tool.BEZIER || state.activeTool == Tool.FILL) {
                    ColorStrip(state, palette, ToolbarGroup.PALETTE !in hiddenGroups, onOpenPicker = { showColorPicker = true }, onChangeEverywhere = { editingSavedColor = it })
                }
                if (state.activeTool == Tool.PEN || state.activeTool == Tool.SHAPE || state.activeTool == Tool.BEZIER) {
                // Pen kinds and saved brushes in one library (design roadmap D44).
                IconButton(onClick = { showBrushLibrary = true }) {
                    Icon(penTypeIcon(state.activePenType), contentDescription = stringResource(R.string.tb_pen_type, stringResource(penTypeLabel(state.activePenType))))
                }
                if (state.activePenType == PenType.PIXEL) {
                    IconButton(onClick = { state.indexedPalette = !state.indexedPalette }, enabled = state.savedColors.isNotEmpty()) {
                        Icon(
                            Icons.Filled.GridView,
                            contentDescription = if (state.indexedPalette) stringResource(R.string.tb_indexed_on) else stringResource(R.string.tb_indexed_off),
                            tint = if (state.indexedPalette) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                        )
                    }
                }
                if (state.activePenType == PenType.STAMP) {
                    IconButton(onClick = { showBrushEditor = true }) {
                        Icon(Icons.Filled.Tune, contentDescription = stringResource(R.string.brush_settings))
                    }
                }
                if (state.activeLayerIsRaster) RasterBrushControls(state)
                }
                // Eyedropper (roadmap item 173): what is shown or only this layer's ink, and how wide it averages.
                if (state.activeTool == Tool.EYEDROPPER) {
                    androidx.compose.material3.FilterChip(
                        selected = state.eyedropperThisLayerOnly,
                        onClick = { state.eyedropperThisLayerOnly = !state.eyedropperThisLayerOnly },
                        label = { Text(stringResource(R.string.eyedropper_this_layer)) },
                        modifier = Modifier.padding(end = FernScale.s8),
                    )
                    for (size in EYEDROPPER_SAMPLE_SIZES) {
                        androidx.compose.material3.FilterChip(
                            selected = state.eyedropperSampleSize == size,
                            onClick = { state.eyedropperSampleSize = size },
                            label = { Text(stringResource(R.string.eyedropper_sample_px, size)) },
                            modifier = Modifier.padding(end = FernScale.s4),
                        )
                    }
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
                // The shape tool is on the rail (design roadmap D8); its kind is chosen here while it's active.
                if (state.activeTool == Tool.SHAPE) Box {
                    IconButton(onClick = { showShapeKindMenu = true }) {
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
                        // Isometric plane (roadmap item 142) the shapes are drawn on.
                        HorizontalDivider()
                        for (plane in listOf(null) + IsometricPlane.entries) {
                            DropdownMenuItem(
                                text = { Text(stringResource(isometricPlaneLabel(plane))) },
                                leadingIcon = { if (state.isometricPlane == plane) Icon(Icons.Filled.Check, contentDescription = null) },
                                onClick = {
                                    state.isometricPlane = plane
                                    state.activeTool = Tool.SHAPE
                                    showShapeKindMenu = false
                                },
                            )
                        }
                    }
                }
                // Measure (roadmap item 143): drag for a distance and angle, tap a shape for its
                // perimeter and area, tap a line for its length; the scale button sets real units.
                if (state.activeTool == Tool.MEASURE) {
                    FernTextButton(onClick = { showDrawingScaleDialog = true }) {
                        Text(stringResource(R.string.scale_button, state.drawingScale.symbol))
                    }
                }
                // Bézier pen (roadmap item 96): tap for corners, drag for curves, tap the first node to close.
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
                if (state.activeTool in STROKE_TOOLS) {
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
                // Line options and stabiliser behind More (design roadmap D32).
                if (ToolbarGroup.STROKE_OPTIONS !in hiddenGroups) MoreInline("stroke_options") {
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
                }
                // Skeleton (roadmap item 83): draw bones, then pose them frame by frame.
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
                    val selectedBone = state.currentBones.firstOrNull { it.id == state.selectedBoneId }
                    val selectedParent = selectedBone?.parentId?.let { id -> state.currentBones.firstOrNull { it.id == id } }
                    if (selectedBone != null && selectedParent != null) {
                        var editingLimits by remember { mutableStateOf(false) }
                        IconButton(onClick = { editingLimits = true }) {
                            Icon(
                                Icons.Filled.Straighten,
                                contentDescription = stringResource(R.string.joint_limits_title),
                                tint = if (selectedBone.minTurn != null) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                            )
                        }
                        if (editingLimits) {
                            JointLimitsDialog(
                                bone = selectedBone,
                                current = Math.toDegrees(relativeAngle(selectedBone, selectedParent)),
                                onApply = { min, max -> state.setJointLimits(selectedBone.id, min, max); editingLimits = false },
                                onDismiss = { editingLimits = false },
                            )
                        }
                        IconButton(onClick = { state.setBoneDynamic(selectedBone.id, !selectedBone.dynamic) }) {
                            Icon(
                                Icons.Filled.Air,
                                contentDescription = if (selectedBone.dynamic) stringResource(R.string.tb_bone_dynamic_off) else stringResource(R.string.tb_bone_dynamic_on),
                                tint = if (selectedBone.dynamic) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                            )
                        }
                    }
                    if (state.animFrameCount > 1 && state.bones.any { it.dynamic }) {
                        IconButton(onClick = { state.simulateDynamics() }) {
                            Icon(Icons.Filled.Waves, contentDescription = stringResource(R.string.tb_simulate_dynamics))
                        }
                    }
                    IconButton(onClick = { state.paintingWeights = !state.paintingWeights }) {
                        Icon(
                            Icons.Filled.FormatPaint,
                            contentDescription = if (state.paintingWeights) stringResource(R.string.tb_weights_stop) else stringResource(R.string.tb_weights),
                            tint = if (state.paintingWeights) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                        )
                    }
                    if (state.paintingWeights) {
                        // Cycles the painted weight in quarters: how much ink follows the selected bone.
                        FernTextButton(onClick = { state.weightPaintValue = ((state.weightPaintValue * 4 + 1) % 5) / 4 }) {
                            Text(stringResource(R.string.tb_weight_value, (state.weightPaintValue * 100).toInt()))
                        }
                    }
                    IconButton(onClick = { state.bindStrokesToNearestBones() }, enabled = state.currentBones.isNotEmpty()) {
                        Icon(Icons.Filled.Link, contentDescription = stringResource(R.string.tb_bones_bind))
                    }
                    IconButton(onClick = { state.deleteSelectedBone() }, enabled = state.selectedBoneId != null) {
                        Icon(Icons.Filled.DeleteOutline, contentDescription = stringResource(R.string.tb_bones_delete))
                    }
                }
                if (ToolbarGroup.FILL !in hiddenGroups) {
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
                    if (state.activeTool == Tool.CONNECT) {
                        IconButton(onClick = { state.tidyDiagram() }, enabled = state.connectors.isNotEmpty()) {
                            Icon(Icons.Filled.Schema, contentDescription = stringResource(R.string.tb_tidy))
                        }
                    }
                }
                if (state.activeLayerIsRaster && (state.activeTool == Tool.SELECT || state.paintingSelection)) {
                    // Global selection mask (roadmap item 138): paint the selection with the pen and eraser.
                    IconButton(onClick = { state.togglePaintingSelection() }) {
                        Icon(
                            Icons.Filled.BorderColor,
                            contentDescription = stringResource(if (state.paintingSelection) R.string.raster_sel_paint_stop else R.string.raster_sel_paint),
                            tint = if (state.paintingSelection) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                        )
                    }
                }
                if (state.activeTool == Tool.SELECT && state.hasRasterSelection) {
                    // Raster selection actions (roadmap item 91).
                    IconButton(onClick = { state.deleteRasterSelectionPixels() }) {
                        Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.raster_sel_clear))
                    }
                    IconButton(onClick = { state.fillRasterSelection() }) {
                        Icon(Icons.Filled.FormatColorFill, contentDescription = stringResource(R.string.raster_sel_fill))
                    }
                    IconButton(onClick = { state.copyRasterSelectionToNewLayer() }) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = stringResource(R.string.raster_sel_copy_layer))
                    }
                    IconButton(onClick = { state.clearRasterSelection() }) {
                        Icon(Icons.Filled.Deselect, contentDescription = stringResource(R.string.raster_sel_deselect))
                    }
                }
                if (state.activeTool == Tool.SELECT && state.activeLayerIsRaster) {
                    // RotSprite (roadmap item 145): turn the selection's pixels, or the layer's.
                    IconButton(onClick = { showRotatePixels = true }) {
                        Icon(Icons.Filled.RotateRight, contentDescription = stringResource(R.string.rotate_pixels_title))
                    }
                }
                if (state.activeTool == Tool.SELECT) {
                    if (state.selectedImageId != null) {
                        // An embedded video (roadmap item 107) plays from here.
                        val video = state.imageItems.firstOrNull { it.id == state.selectedImageId && it.videoFileName != null }
                        if (video != null) {
                            IconButton(onClick = { playingVideo = video }) {
                                Icon(Icons.Filled.PlayCircle, contentDescription = stringResource(R.string.video_play))
                            }
                        }
                        // A 3D model (roadmap item 108) opens for painting from here.
                        val modelItem = state.imageItems.firstOrNull { it.id == state.selectedImageId && it.modelFileName != null }
                        if (modelItem != null) {
                            val context = LocalContext.current
                            val scope = rememberCoroutineScope()
                            IconButton(onClick = { openModel(modelItem, context, scope) }) {
                                Icon(Icons.Filled._3dRotation, contentDescription = stringResource(R.string.model_paint))
                            }
                        }
                        IconButton(onClick = onTraceImage) {
                            Icon(Icons.Filled.AutoFixHigh, contentDescription = stringResource(R.string.tb_trace_image))
                        }
                    }
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
                    // Game object (roadmap item 155): the selection as an object with properties and behaviours.
                    val newObjectName = stringResource(R.string.object_default_name, state.gameObjects.size + 1)
                    // Drawn hitboxes and links (roadmap item 157).
                    if (state.canUseHitbox) {
                        IconButton(onClick = { state.useSelectionAsHitbox() }) {
                            Icon(Icons.Filled.Fence, contentDescription = stringResource(R.string.object_use_hitbox))
                        }
                    }
                    if (state.canLink) {
                        Box {
                            IconButton(onClick = { showLinkMenu = true }) {
                                Icon(Icons.Filled.Cable, contentDescription = stringResource(R.string.object_link))
                            }
                            DropdownMenu(expanded = showLinkMenu, onDismissRequest = { showLinkMenu = false }) {
                                for ((kind, label) in listOf(LinkKind.ROD to R.string.link_rod, LinkKind.ROPE to R.string.link_rope, LinkKind.ATTACH to R.string.link_attach)) {
                                    DropdownMenuItem(text = { Text(stringResource(label)) }, onClick = {
                                        showLinkMenu = false
                                        state.linkSelection(kind)
                                    })
                                }
                            }
                        }
                    }
                    // Constraints (roadmap item 144): how the selection follows a resized frame.
                    IconButton(onClick = { showConstraintsDialog = true }, enabled = state.selection.isNotEmpty()) {
                        Icon(Icons.Filled.PushPin, contentDescription = stringResource(R.string.constraints_title))
                    }
                    // Isometric panel (roadmap item 142): lay the selection onto a cube face.
                    Box {
                        IconButton(onClick = { showPlaceOnPlaneMenu = true }, enabled = state.selection.isNotEmpty()) {
                            Icon(Icons.Filled.ViewInAr, contentDescription = stringResource(R.string.tb_place_on_plane))
                        }
                        DropdownMenu(expanded = showPlaceOnPlaneMenu, onDismissRequest = { showPlaceOnPlaneMenu = false }) {
                            for (plane in IsometricPlane.entries) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(isometricPlaneLabel(plane))) },
                                    onClick = {
                                        state.placeSelectionOnPlane(plane)
                                        showPlaceOnPlaneMenu = false
                                    },
                                )
                            }
                        }
                    }
                    // Shape builder, knife and contour (roadmap item 141).
                    val building = state.shapeEditMode == ShapeEditMode.BUILD_ADD || state.shapeEditMode == ShapeEditMode.BUILD_REMOVE
                    IconButton(onClick = { state.toggleShapeEdit(if (building) state.shapeEditMode!! else ShapeEditMode.BUILD_ADD) }, enabled = building || state.selection.size >= 2) {
                        Icon(
                            Icons.Filled.Interests,
                            contentDescription = stringResource(if (building) R.string.tb_shape_builder_stop else R.string.tb_shape_builder),
                            tint = if (building) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                        )
                    }
                    if (building) {
                        val removing = state.shapeEditMode == ShapeEditMode.BUILD_REMOVE
                        IconButton(onClick = { state.toggleShapeEdit(if (removing) ShapeEditMode.BUILD_ADD else ShapeEditMode.BUILD_REMOVE) }) {
                            Icon(
                                if (removing) Icons.Filled.RemoveCircleOutline else Icons.Filled.AddCircleOutline,
                                contentDescription = stringResource(if (removing) R.string.tb_shape_builder_remove else R.string.tb_shape_builder_add),
                            )
                        }
                    }
                    // Comic panel cutter (roadmap item 105): a drag splits the panels it crosses.
                    val dividing = state.shapeEditMode == ShapeEditMode.PANEL_CUT
                    IconButton(onClick = { state.toggleShapeEdit(ShapeEditMode.PANEL_CUT) }) {
                        Icon(
                            Icons.Filled.ViewQuilt,
                            contentDescription = stringResource(if (dividing) R.string.tb_panel_cut_stop else R.string.tb_panel_cut),
                            tint = if (dividing) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                        )
                    }
                    val cutting = state.shapeEditMode == ShapeEditMode.KNIFE
                    IconButton(onClick = { state.toggleShapeEdit(ShapeEditMode.KNIFE) }, enabled = cutting || state.selection.isNotEmpty()) {
                        Icon(
                            Icons.Filled.ContentCut,
                            contentDescription = stringResource(if (cutting) R.string.tb_knife_stop else R.string.tb_knife),
                            tint = if (cutting) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                        )
                    }
                    IconButton(onClick = { showContourDialog = true }, enabled = state.selection.isNotEmpty()) {
                        Icon(Icons.Filled.Adjust, contentDescription = stringResource(R.string.contour_title))
                    }
                    IconButton(onClick = { state.reduceSelectionToPalette() }, enabled = state.selection.isNotEmpty() && state.savedColors.isNotEmpty()) {
                        Icon(Icons.Filled.InvertColors, contentDescription = stringResource(R.string.tb_reduce_palette))
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
                    IconButton(onClick = { state.copySelectionStyle() }, enabled = state.selection.isNotEmpty()) {
                        Icon(Icons.Filled.Style, contentDescription = stringResource(R.string.tb_copy_style))
                    }
                    IconButton(onClick = { state.pasteStyleToSelection() }, enabled = state.selection.isNotEmpty() && state.copiedStyle != null) {
                        Icon(Icons.Filled.ContentPasteGo, contentDescription = stringResource(R.string.tb_paste_style))
                    }
                    IconButton(onClick = onCopySelection) {
                        Icon(Icons.Filled.CopyAll, contentDescription = stringResource(R.string.tb_copy))
                    }
                    // Drag-only: dragging from this handle carries the selection to another window
                    // (Copy and Paste do the same without a gesture).
                    Box(
                        modifier = Modifier
                            .size(FernScale.s48)
                            .semantics { contentDescription = dragOutLabel }
                            .pointerInput(Unit) { detectDragGestures(onDragStart = { onDragSelectionOut() }) { change, _ -> change.consume() } },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.OpenWith, contentDescription = null)
                    }
                    IconButton(onClick = onPaste) {
                        Icon(Icons.Filled.ContentPaste, contentDescription = stringResource(R.string.action_paste))
                    }
                    IconButton(onClick = { state.toggleNodeEditing() }, enabled = state.selection.size == 1) {
                        Icon(
                            Icons.Filled.Polyline,
                            contentDescription = if (state.nodeEditing) stringResource(R.string.tb_nodes_stop) else stringResource(R.string.tb_nodes),
                            tint = if (state.nodeEditing) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                        )
                    }
                    for ((kind, icon, labels) in listOf(
                        Triple(WarpKind.MESH, Icons.Filled.GridOn, R.string.tb_mesh to R.string.tb_mesh_stop),
                        Triple(WarpKind.DISTORT, Icons.Filled.FormatShapes, R.string.tb_distort to R.string.tb_distort_stop),
                        Triple(WarpKind.PUPPET, Icons.Filled.SportsGymnastics, R.string.tb_puppet to R.string.tb_puppet_stop),
                    )) {
                        val on = state.meshWarping && state.warpKind == kind
                        IconButton(onClick = { state.toggleWarp(kind) }) {
                            Icon(
                                icon,
                                contentDescription = stringResource(if (on) labels.second else labels.first),
                                tint = if (on) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                            )
                        }
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
                }
    }

    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        tonalElevation = FernScale.s4,
        shadowElevation = FernScale.s4,
    ) {
        Column(modifier = Modifier.padding(horizontal = FernScale.s12, vertical = FernScale.s8)) {
            // Every tool, in toolbar order. On a phone they scroll sideways: they add up to far
            // more than its width, and without scrolling everything past the edge was unreachable.
            // On a wide window (roadmap item 74) they wrap onto further rows instead, all in view.
            // The top bar (roadmap items 338, 339): a fixed set that never scrolls - undo, redo,
            // layers, play (once there is a game), search, export and "More". Everything else is
            // in the More panel, in labelled sections, so the bar can't grow with each feature.
            val tools: @Composable () -> Unit = {
                IconButton(onClick = { state.undo() }) {
                    Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = stringResource(R.string.action_undo))
                }
                IconButton(onClick = { state.redo() }) {
                    Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = stringResource(R.string.action_redo))
                }
                if (ToolbarGroup.LAYERS !in hiddenGroups) {
                    IconButton(onClick = { showLayersDialog = true }) {
                        Icon(Icons.Filled.Layers, contentDescription = stringResource(R.string.layers_title))
                    }
                }
                // Play the game (roadmap item 155) once something is an object.
                if (state.gameObjects.isNotEmpty()) {
                    IconButton(onClick = { state.startPlay() }) {
                        Icon(Icons.Filled.SportsEsports, contentDescription = stringResource(R.string.play_start))
                    }
                }
                IconButton(onClick = { CommandPaletteState.open = true }) {
                    Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.palette_title))
                }
                // Export by purpose, then format (design roadmap D13).
                IconButton(onClick = { showExportMenu = true }) {
                    Icon(Icons.Filled.IosShare, contentDescription = stringResource(R.string.tb_export))
                }
                IconButton(onClick = { showMore = true }) {
                    Icon(Icons.Filled.MoreHoriz, contentDescription = stringResource(R.string.more_title))
                }
                if (showExportMenu) {
                    ExportPanel(state, onDismiss = { showExportMenu = false }, run = runExport)
                }
            }
            if (showMore) {
                FernPanel(
                    scrollContent = true,
                    onDismissRequest = { showMore = false },
                    title = { Text(stringResource(R.string.more_title)) },
                    text = {
                        Column {
                            MoreSection(stringResource(R.string.more_section_workspace)) {
                                WorkspaceSwitcher(hiddenGroups, onHiddenGroupsChange)
                            }
                            MoreSection(stringResource(R.string.more_section_mode)) {
                                if (ToolbarGroup.ANIMATION !in hiddenGroups) {
                                    LabelledIconButton(onClick = {
                                        state.animationMode = !state.animationMode
                                        state.playing = false
                                    }, icon = Icons.Filled.Movie, label = if (state.animationMode) stringResource(R.string.tb_anim_on) else stringResource(R.string.tb_anim_off), tint = if (state.animationMode) MaterialTheme.colorScheme.primary else LocalContentColor.current)
                                }
                                if (state.gameObjects.isNotEmpty()) {
                                    // The game's controls (roadmap item 160).
                                    LabelledIconButton(onClick = { showControls = true }, icon = Icons.Filled.Gamepad, label = stringResource(R.string.controls_title))
                                }
                                if (ToolbarGroup.REPLAY !in hiddenGroups) {
                                    LabelledIconButton(onClick = { state.replayCount = if (state.replayCount == null) 0 else null }, icon = Icons.Filled.Replay, label = if (state.replayCount == null) stringResource(R.string.tb_replay) else stringResource(R.string.tb_stop_replay), tint = if (state.replayCount != null) MaterialTheme.colorScheme.primary else LocalContentColor.current)
                                }
                            }
                            MoreSection(stringResource(R.string.more_section_guides)) {
                                if (ToolbarGroup.GUIDES !in hiddenGroups) {
                                    Box {
                                        LabelledIconButton(onClick = { showSymmetryMenu = true }, icon = Icons.Filled.Flare, label = stringResource(R.string.tb_symmetry, stringResource(state.symmetryMode.label)), tint = if (state.symmetryMode != SymmetryMode.OFF) MaterialTheme.colorScheme.primary
                                                else LocalContentColor.current)
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
                                        LabelledIconButton(onClick = { showPerspectiveMenu = true }, icon = Icons.Filled.ViewInAr, label = stringResource(R.string.tb_perspective, stringResource(state.perspectiveMode.label)), tint = if (state.perspectiveMode != PerspectiveMode.OFF) MaterialTheme.colorScheme.primary
                                                else LocalContentColor.current)
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
                                if (ToolbarGroup.GUIDES !in hiddenGroups) {
                                    // Guides (roadmap items 340-343): add one of each kind, edit them, pause snapping.
                                    for (kind in GuideKind.entries) {
                                        LabelledIconButton(onClick = { state.addGuide(kind) }, icon = guideKindIcon(kind), label = stringResource(kind.label))
                                    }
                                    if (state.drawingGuides.isNotEmpty() || state.perspectiveMode != PerspectiveMode.OFF) {
                                        LabelledIconButton(
                                            onClick = { state.editingGuides = !state.editingGuides },
                                            icon = Icons.Filled.Edit,
                                            label = stringResource(if (state.editingGuides) R.string.guide_editing_done else R.string.guide_edit),
                                            tint = if (state.editingGuides) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                                        )
                                        LabelledIconButton(
                                            onClick = { state.guideSnapping = !state.guideSnapping },
                                            icon = Icons.Filled.Straighten,
                                            label = stringResource(R.string.guide_snapping),
                                            tint = if (state.guideSnapping) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                                        )
                                    }
                                }
                            }
                            if (ToolbarGroup.GUIDES !in hiddenGroups && state.drawingGuides.isNotEmpty()) MoreSection(stringResource(R.string.guide_list)) {
                                // Every guide on the page: tap to show or hide it, the cross to remove it (roadmap item 343).
                                for (guide in state.drawingGuides) {
                                    androidx.compose.material3.InputChip(
                                        selected = guide.visible,
                                        onClick = { state.setGuideVisible(guide.id, !guide.visible) },
                                        label = { Text(stringResource(guide.kind.label)) },
                                        trailingIcon = {
                                            Icon(
                                                Icons.Filled.Close,
                                                contentDescription = stringResource(R.string.guide_remove, stringResource(guide.kind.label)),
                                                modifier = Modifier.clickable { state.removeDrawingGuide(guide.id) },
                                            )
                                        },
                                        modifier = Modifier.padding(end = FernScale.s4),
                                    )
                                }
                            }
                            MoreSection(stringResource(R.string.more_section_canvas)) {
                                if (ToolbarGroup.LAYERS !in hiddenGroups) {
                                    Box {
                                        LabelledIconButton(onClick = { showBlendModeMenu = true }, icon = Icons.Filled.Tonality, label = stringResource(R.string.tb_blend, stringResource(blendModeLabel(state.activeBlendMode))), tint = if (state.activeBlendMode != StrokeBlendMode.NORMAL) MaterialTheme.colorScheme.primary
                                                else LocalContentColor.current)
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

                                if (ToolbarGroup.BACKGROUND !in hiddenGroups) {
                                    Box {
                                        LabelledIconButton(onClick = { showBackgroundStyleMenu = true }, icon = Icons.Filled.GridOn, label = stringResource(R.string.tb_background, stringResource(backgroundStyleLabel(state.backgroundStyle))))
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
                                if (ToolbarGroup.VIEW !in hiddenGroups) {
                                    LabelledIconButton(onClick = {
                                        state.clear()
                                        FernMessages.show(clearedMessage, undoLabel) { state.undo() }
                                    }, icon = Icons.Filled.DeleteSweep, label = stringResource(R.string.tb_clear_all))
                                    LabelledIconButton(onClick = { state.resetView() }, icon = Icons.Filled.ZoomOutMap, label = stringResource(R.string.tb_reset_view))
                                    LabelledIconButton(onClick = { state.zoomToFit() }, icon = Icons.Filled.CenterFocusStrong, label = stringResource(R.string.tb_fit))
                                }
                            }
                            MoreSection(stringResource(R.string.more_section_tool_slots)) {
                                if (ToolbarGroup.FAVOURITES !in hiddenGroups) {
                                    for (index in 0 until TOOL_SLOT_COUNT) ToolSlotButton(state, index)
                                }
                            }
                            MoreSection(stringResource(R.string.more_section_menus)) {
                                LabelledIconButton(onClick = onOpenDocuments, icon = Icons.Filled.Description, label = stringResource(R.string.documents_title))
                                // Occasional actions, grouped by purpose behind labelled menus, each one level deep (design roadmap D12).
                                MenuButton(R.string.menu_group_insert, Icons.Filled.AddBox, insertItems)
                                MenuButton(R.string.menu_group_import, Icons.Filled.FileOpen, importItems)
                                MenuButton(R.string.menu_group_document, Icons.Filled.Description, documentItems)
                                MenuButton(R.string.menu_group_app, Icons.Filled.Settings, appItems)
                            }
                        }
                    },
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) { tools() }
        }
    }

    if (state.activeTool != Tool.TEXT) {
        Surface(modifier = propertiesModifier, shape = MaterialTheme.shapes.large, tonalElevation = FernScale.s4, shadowElevation = FernScale.s4) {
            Column(modifier = Modifier.padding(horizontal = FernScale.s12, vertical = FernScale.s4), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                ) { properties() }
                if (showWidthSlider) {
                    FernSlider(
                        value = state.activeWidthWorld.toFloat(),
                        onValueChange = { state.activeWidthWorld = it.toDouble() },
                        valueRange = MIN_WIDTH_SCREEN..MAX_WIDTH_SCREEN,
                        modifier = Modifier
                            .padding(top = FernScale.s8)
                            .width(FernScale.s192),
                    )
                }
                if (showOpacitySlider) {
                    FernSlider(
                        value = state.activeOpacity.toFloat(),
                        onValueChange = { state.activeOpacity = it.toDouble() },
                        valueRange = MIN_OPACITY..1f,
                        modifier = Modifier
                            .padding(top = FernScale.s8)
                            .width(FernScale.s192),
                    )
                }
                if (showStabilizationSlider) {
                    FernSlider(
                        value = state.activeStabilization.toFloat(),
                        onValueChange = { state.activeStabilization = it.toDouble() },
                        valueRange = 0f..CanvasState.MAX_STABILIZATION.toFloat(),
                        modifier = Modifier
                            .padding(top = FernScale.s8)
                            .width(FernScale.s192),
                    )
                }
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
            onColorSelected = { color -> state.activeColor = color },
            live = true,
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

    if (showBrushLibrary) BrushLibrary(state, onEditStamp = { showBrushEditor = true }, onDismiss = { showBrushLibrary = false })
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
            onSetPresetTags = { name, tags -> state.setBrushPresetTags(name, tags) },
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
    val view = LocalView.current
    val preset = state.toolSlots[index]
    var showMenu by remember { mutableStateOf(false) }
    val description = if (preset == null) stringResource(R.string.tb_slot_empty, index + 1)
    else stringResource(R.string.tb_slot, index + 1, stringResource(penTypeLabel(preset.penType)))
    Box {
        Box(
            modifier = Modifier
                // 48dp tall like the buttons beside it, so wrapped rows line up.
                .minimumInteractiveComponentSize().size(FernScale.s32)
                .border(FernScale.s1, MaterialTheme.colorScheme.outline, RoundedCornerShape(FernScale.s8)) // §7.3: 0.24 × 32 = 7.7 → 8
                .semantics { contentDescription = description }
                .pointerInput(preset) {
                    detectTapGestures(
                        onTap = { if (preset == null) state.saveToolSlot(index) else state.applyToolSlot(index) },
                        onLongPress = {
                            FernHaptics.press(view)
                            showMenu = true
                        },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            if (preset == null) {
                Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(FernScale.s16))
            } else {
                Icon(penTypeIcon(preset.penType), contentDescription = null, tint = preset.color, modifier = Modifier.size(FernScale.s16))
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
internal fun penTypeLabel(penType: PenType) = when (penType) {
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
    ShapeKind.THOUGHT_BUBBLE -> Icons.Filled.Cloud
    ShapeKind.SHOUT_BUBBLE -> Icons.Filled.Flare
}

@StringRes
private fun shapeKindLabel(kind: ShapeKind) = when (kind) {
    ShapeKind.LINE -> R.string.shape_line
    ShapeKind.RECTANGLE -> R.string.shape_rectangle
    ShapeKind.ELLIPSE -> R.string.shape_ellipse
    ShapeKind.ARROW -> R.string.shape_arrow
    ShapeKind.SPEECH_BUBBLE -> R.string.shape_bubble
    ShapeKind.THOUGHT_BUBBLE -> R.string.shape_thought
    ShapeKind.SHOUT_BUBBLE -> R.string.shape_shout
}

@StringRes
private fun blendModeLabel(mode: StrokeBlendMode) = when (mode) {
    StrokeBlendMode.NORMAL -> R.string.blend_normal
    StrokeBlendMode.MULTIPLY -> R.string.blend_multiply
    StrokeBlendMode.SCREEN -> R.string.blend_screen
}

@StringRes
internal fun isometricPlaneLabel(plane: IsometricPlane?) = when (plane) {
    null -> R.string.iso_plane_flat
    IsometricPlane.TOP -> R.string.iso_plane_top
    IsometricPlane.LEFT -> R.string.iso_plane_left
    IsometricPlane.RIGHT -> R.string.iso_plane_right
}

@StringRes
private fun backgroundStyleLabel(style: BackgroundStyle) = when (style) {
    BackgroundStyle.PLAIN -> R.string.paper_plain
    BackgroundStyle.GRID -> R.string.paper_grid
    BackgroundStyle.DOT -> R.string.template_dot
    BackgroundStyle.RULED -> R.string.paper_ruled
    BackgroundStyle.ISOMETRIC -> R.string.paper_isometric
}

/** Tells screen readers whether a tool's button is the active tool (design roadmap D4). */
internal fun Modifier.toolSelected(active: Boolean): Modifier = semantics { selected = active }

/** Tools that draw strokes, whose width, opacity and line options the properties bar shows. */
private val STROKE_TOOLS = setOf(Tool.PEN, Tool.SHAPE, Tool.BEZIER, Tool.ERASER)

/**
 * A labelled menu of one purpose (design roadmap D12): an icon with its name beneath, opening
 * the actions it groups - one level, nothing nested.
 */
@Composable
private fun MenuButton(@androidx.annotation.StringRes label: Int, icon: androidx.compose.ui.graphics.vector.ImageVector, items: List<Triple<Int, androidx.compose.ui.graphics.vector.ImageVector, () -> Unit>>) {
    var open by remember { mutableStateOf(false) }
    Box {
        FernTextButton(onClick = { open = true }) {
            Icon(icon, contentDescription = null, modifier = Modifier.padding(end = FernScale.s4))
            Text(stringResource(label))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            for ((itemLabel, itemIcon, action) in items) {
                DropdownMenuItem(
                    text = { Text(stringResource(itemLabel)) },
                    leadingIcon = { Icon(itemIcon, contentDescription = null) },
                    onClick = {
                        open = false
                        action()
                    },
                )
            }
        }
    }
}

/** One labelled section of the More panel: its title, then its controls wrapping onto rows. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun MoreSection(title: String, content: @Composable () -> Unit) {
    Text(title, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = FernScale.s8))
    FlowRow(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(FernScale.s4)) { content() }
}

/** Squares the eyedropper can average over, in screen px (roadmap item 173). */
private val EYEDROPPER_SAMPLE_SIZES = listOf(1, 3, 5)

/** The icon of a guide kind in the More panel. */
private fun guideKindIcon(kind: GuideKind): androidx.compose.ui.graphics.vector.ImageVector = when (kind) {
    GuideKind.STRAIGHT -> Icons.Filled.Straighten
    GuideKind.PARALLEL -> Icons.Filled.Reorder
    GuideKind.RADIAL -> Icons.Filled.WbSunny
    GuideKind.CONCENTRIC -> Icons.Filled.TripOrigin
    GuideKind.ELLIPSE -> Icons.Filled.PanoramaFishEye
}
