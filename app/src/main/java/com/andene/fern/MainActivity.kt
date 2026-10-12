@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.andene.fern

import android.os.SystemClock

import com.andene.fern.canvas.GameInputState
import android.view.MotionEvent
import android.view.KeyEvent
import android.view.InputDevice
import com.andene.fern.canvas.traceImage
import com.andene.fern.canvas.TRACE_MAX_EDGE_PX
import java.time.format.TextStyle as JavaTextStyle
import java.time.temporal.WeekFields
import java.util.Locale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.draganddrop.mimeTypes
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import android.view.DragEvent
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.DisposableEffect
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.andene.fern.canvas.AnimationBar
import com.andene.fern.canvas.PlayOverlay
import com.andene.fern.canvas.AppSettings
import com.andene.fern.canvas.AudioNotes
import com.andene.fern.canvas.AudioNotesDialog
import com.andene.fern.canvas.AudioSeekOverlay
import com.andene.fern.canvas.AudioBar
import com.andene.fern.canvas.CanvasHaptics
import com.andene.fern.canvas.ToolRail
import com.andene.fern.canvas.CommandPaletteState
import com.andene.fern.canvas.GestureAction
import com.andene.fern.canvas.KeyCombo
import com.andene.fern.canvas.ShortcutAction
import com.andene.fern.canvas.ShortcutCapture
import com.andene.fern.canvas.ShortcutCheatSheet
import com.andene.fern.canvas.ShortcutSettings
import com.andene.fern.canvas.MIN_WIDTH_SCREEN
import com.andene.fern.canvas.MAX_WIDTH_SCREEN
import com.andene.fern.canvas.CanvasGesture
import com.andene.fern.canvas.GestureSettings
import com.andene.fern.canvas.runGestureAction
import com.andene.fern.canvas.PaletteCommand
import com.andene.fern.canvas.BackgroundStyle
import com.andene.fern.canvas.CanvasState
import com.andene.fern.canvas.CanvasStorage
import com.andene.fern.canvas.DocumentMeta
import com.andene.fern.canvas.DocumentTemplate
import com.andene.fern.canvas.DocumentsDialog
import com.andene.fern.canvas.DrawingCanvas
import com.andene.fern.canvas.Frame
import com.andene.fern.canvas.FramesDialog
import com.andene.fern.canvas.ClipboardContent
import com.andene.fern.canvas.DocumentLoadingOverlay
import com.andene.fern.canvas.LoadedDocument
import com.andene.fern.canvas.LinkChoices
import com.andene.fern.canvas.LinkTarget
import com.andene.fern.canvas.Minimap
import com.andene.fern.canvas.SystemClipboard
import com.andene.fern.canvas.startSelectionDrag
import com.andene.fern.canvas.QuickMenuButton
import com.andene.fern.canvas.QuickMenuOverlay
import com.andene.fern.canvas.PinMeta
import com.andene.fern.canvas.ColorManagement
import com.andene.fern.canvas.SymbolLibrary
import com.andene.fern.canvas.Rulers
import com.andene.fern.canvas.readFileLayerImage
import com.andene.fern.canvas.QuickMenuCustomizeDialog
import com.andene.fern.canvas.SecondView
import com.andene.fern.canvas.PinsDialog
import com.andene.fern.canvas.PresentationControls
import com.andene.fern.canvas.SettingsDialog
import com.andene.fern.canvas.Tool
import com.andene.fern.canvas.Toolbar
import com.andene.fern.canvas.ToolbarCustomizeDialog
import com.andene.fern.canvas.VersionsDialog
import com.andene.fern.canvas.WelcomeDialog
import com.andene.fern.canvas.WorldPoint
import com.andene.fern.canvas.exportAnimatedGif
import com.andene.fern.canvas.exportAnimationVideo
import com.andene.fern.canvas.Mp4Encoder
import com.andene.fern.canvas.exportReplayMp4
import com.andene.fern.canvas.exportAnimatedPng
import com.andene.fern.canvas.exportLottie
import com.andene.fern.canvas.exportDocumentCmykPdf
import com.andene.fern.canvas.exportDocumentDxf
import com.andene.fern.canvas.exportDocumentExr
import com.andene.fern.canvas.exportSlicesZip
import com.andene.fern.canvas.exportDocumentEps
import com.andene.fern.canvas.exportDocumentAi
import com.andene.fern.canvas.embedVideo
import com.andene.fern.canvas.decodeDownsampled
import com.andene.fern.canvas.newModel
import com.andene.fern.canvas.readObj
import com.andene.fern.canvas.storeModel
import com.andene.fern.canvas.readPdfVectors
import com.andene.fern.canvas.exportReplayGif
import com.andene.fern.canvas.StrokeChunkFile
import com.andene.fern.canvas.SpineImport
import com.andene.fern.canvas.PsdFormat
import com.andene.fern.canvas.KraFormat
import com.andene.fern.canvas.renderLayers
import com.andene.fern.canvas.readLayeredFile
import com.andene.fern.canvas.maskIdOf
import com.andene.fern.canvas.exportSpine
import com.andene.fern.canvas.exportDragonBones
import com.andene.fern.canvas.exportDocumentBundle
import com.andene.fern.canvas.exportDocumentPdf
import com.andene.fern.canvas.exportDocumentPng
import com.andene.fern.canvas.exportDocumentSvg
import com.andene.fern.canvas.exportSpriteAtlas
import com.andene.fern.canvas.exportSpriteSheetPng
import com.andene.fern.canvas.importDocumentBundle
import com.andene.fern.canvas.importPdfPages
import com.andene.fern.canvas.markWelcomeSeen
import com.andene.fern.canvas.isAdobeSwatchExchange
import com.andene.fern.canvas.parseAdobeSwatchExchange
import com.andene.fern.canvas.parseGimpPalette
import com.andene.fern.canvas.shouldShowWelcome
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

// Inserted images are stored at most this large on their longest edge: plenty for a canvas
// image, while a full-resolution phone photo (48 MP+) decoded every session could exhaust
// memory. 2048 is on the project size scale.
private const val MAX_IMAGE_EDGE_PX = 2048

// Window width from which the toolbar wraps instead of scrolling (tablets, unfolded foldables,
// wide split windows). Material's "expanded" class starts at 840dp; this is that, put on the §7
// scale (512 + 256), which also takes in a portrait tablet.
private const val WIDE_WINDOW_DP = 768

class MainActivity : ComponentActivity() {
    private val powerMonitor by lazy { PowerMonitor(applicationContext) }

    // The playing game's input, for gamepad sticks, which reach only the activity (roadmap item 160).
    private var playInput: GameInputState? = null

    /**
     * Gamepad sticks and hats while a game plays: the sticks' axes feed the input mapping; a hat
     * (d-pad reported as axes) presses the d-pad keys, as most games expect.
     */
    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        val input = playInput
        if (input == null || !event.isFromSource(InputDevice.SOURCE_JOYSTICK) || event.action != MotionEvent.ACTION_MOVE) return super.dispatchGenericMotionEvent(event)
        input.axis(MotionEvent.AXIS_X, event.getAxisValue(MotionEvent.AXIS_X))
        input.axis(MotionEvent.AXIS_Y, event.getAxisValue(MotionEvent.AXIS_Y))
        val hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X)
        val hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y)
        input.key(KeyEvent.KEYCODE_DPAD_LEFT, hatX < -0.5f)
        input.key(KeyEvent.KEYCODE_DPAD_RIGHT, hatX > 0.5f)
        input.key(KeyEvent.KEYCODE_DPAD_UP, hatY < -0.5f)
        input.key(KeyEvent.KEYCODE_DPAD_DOWN, hatY > 0.5f)
        return true
    }

    override fun onStart() {
        super.onStart()
        powerMonitor.start()
    }

    override fun onStop() {
        powerMonitor.stop()
        super.onStop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashReporter.install(this)
        CrashReporter.recordUnseenExits(this)
        enableEdgeToEdge()
        setContent {
            // Follows the system light/dark setting for the app's own UI (toolbar, dialogs). The
            // canvas paper keeps each document's chosen background colour either way.
            var highContrast by remember { mutableStateOf(AppSettings.highContrast(this)) }
            var colorBlindSafe by remember { mutableStateOf(AppSettings.colorBlindSafe(this)) }
            remember { FernHaptics.enabled = AppSettings.haptics(this) }
            FernTheme(highContrast = highContrast, colorBlindSafe = colorBlindSafe) {
                Surface(color = Color(0xFFF7F5F0)) {
                    val context = LocalContext.current
                    val lifecycleOwner = LocalLifecycleOwner.current
                    val coroutineScope = rememberCoroutineScope()
                    var autosaveJob by remember { mutableStateOf<Job?>(null) }
                    // Raster changes ask for a save here; the writer below batches them.
                    val rasterSaveRequests = remember { kotlinx.coroutines.flow.MutableStateFlow(0) }

                    var currentDocumentId by remember { mutableStateOf("") }
                    var documents by remember { mutableStateOf(emptyList<DocumentMeta>()) }
                    var showDocumentsDialog by remember { mutableStateOf(false) }
                    var pins by remember { mutableStateOf(emptyList<PinMeta>()) }
                    var showPinsDialog by remember { mutableStateOf(false) }
                    var showFramesDialog by remember { mutableStateOf(false) }
                    var showWelcome by remember { mutableStateOf(shouldShowWelcome(context)) }
                    var showSettings by remember { mutableStateOf(false) }
                    var showQuickMenu by remember { mutableStateOf(false) }
                    // Every bar and panel hidden for a clear canvas (design roadmap D15 and D78).
                    var chromeHidden by remember { mutableStateOf(false) }
                    // A tool key being held: the key, the tool before it, when it went down (design roadmap D17).
                    var toolKeyHold by remember { mutableStateOf<Triple<Key, Tool, Long>?>(null) }
                    // True while a document is being read: the canvas is covered so nothing can be
                    // drawn onto (and then autosaved over) a document that isn't loaded yet.
                    var loadingDocument by remember { mutableStateOf(true) }
                    val switchMutex = remember { Mutex() }
                    var showAudioNotes by remember { mutableStateOf(false) }
                    val audioNotes = remember { AudioNotes(context) }
                    // The CMYK profile and spot colours (roadmap item 102), app-wide.
                    LaunchedEffect(Unit) { withContext(Dispatchers.IO) { ColorManagement.load(context) } }
                    // The voice note playing on the canvas in step with its ink (roadmap item 130).
                    var playingNote by remember { mutableStateOf<java.io.File?>(null) }
                    // Recording carries on while drawing, but not past leaving the app's screen.
                    DisposableEffect(audioNotes) {
                        onDispose {
                            audioNotes.stop()
                            audioNotes.stopPlayback()
                        }
                    }
                    val micPermissionLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.RequestPermission(),
                    ) { granted ->
                        if (granted) showAudioNotes = true
                        else FernMessages.show(context.getString(R.string.toast_mic))
                    }
                    var leftHanded by remember { mutableStateOf(AppSettings.leftHanded(context)) }
                    var toolbarAtBottom by remember { mutableStateOf(AppSettings.toolbarAtBottom(context)) }
                    var hiddenToolbarGroups by remember { mutableStateOf(AppSettings.hiddenToolbarGroups(context)) }
                    var quickMenuActions by remember { mutableStateOf(AppSettings.quickMenuActions(context)) }
                    var showQuickMenuCustomize by remember { mutableStateOf(false) }
                    var showToolbarCustomize by remember { mutableStateOf(false) }
                    var crashReport by remember { mutableStateOf(CrashReporter.pendingReport(context)) }
                    var presentingIndex by remember { mutableStateOf<Int?>(null) } // non-null = presenting that frame
                    var frameToExport by remember { mutableStateOf<Frame?>(null) }
                    var versions by remember { mutableStateOf<List<Long>?>(null) } // non-null = dialog open

                    // Debounced: a completed stroke/undo/clear schedules a save ~500ms out,
                    // cancelling any still-pending one, so a burst of quick actions coalesces
                    // into one write instead of one per action. This is a safety net for a
                    // hard kill/crash mid-session; the ON_STOP save below is the primary path
                    // for a normal backgrounding. Reads currentDocumentId at call time (not
                    // captured at creation), so it always saves to whichever document is
                    // actually open, even after switching documents.
                    val canvasState = remember {
                        CanvasState(
                            onChanged = { snapshot ->
                                autosaveJob?.cancel()
                                val documentId = currentDocumentId
                                autosaveJob = coroutineScope.launch {
                                    delay(PowerMonitor.autosaveDelayMillis(powerMonitor.constrained))
                                    withContext(Dispatchers.IO) { CanvasStorage.save(context, documentId, snapshot, withThumbnail = false) }
                                }
                            },
                            // Text edits are infrequent (one dialog confirm at a time), so
                            // saved directly rather than debounced like stroke autosave.
                            onTextChanged = { snapshot -> CanvasStorage.saveTextItems(context, currentDocumentId, snapshot) },
                            onImageChanged = { snapshot -> CanvasStorage.saveImageItems(context, currentDocumentId, snapshot) },
                            onBackgroundChanged = { color, style -> CanvasStorage.saveBackground(context, currentDocumentId, color, style) },
                            onSavedColorsChanged = { colors -> CanvasStorage.saveSavedColors(context, colors) },
                            onBrushChanged = { brush -> AppSettings.setStampBrush(context, brush) },
                            onToolSlotsChanged = { slots -> AppSettings.setToolSlots(context, slots) },
                            onBrushPresetsChanged = { presets -> AppSettings.setBrushPresets(context, presets) },
                            onConnectorsChanged = { connectors -> CanvasStorage.saveConnectors(context, currentDocumentId, connectors) },
                            onLayerCompsChanged = { comps -> CanvasStorage.saveLayerComps(context, currentDocumentId, comps) },
                            onStatsChanged = { stats -> CanvasStorage.saveStats(context, currentDocumentId, stats) },
                            onGuidesChanged = { guides -> CanvasStorage.saveGuides(context, currentDocumentId, guides) },
                            onDrawingScaleChanged = { scale -> CanvasStorage.saveDrawingScale(context, currentDocumentId, scale) },
                            onGameObjectsChanged = { objects, settings -> CanvasStorage.saveGameObjects(context, currentDocumentId, objects, settings) },
                            // Branch snapshots can be large: written off the main thread (roadmap item 153).
                            onBranchesChanged = { branches, current ->
                                val documentId = currentDocumentId
                                coroutineScope.launch(Dispatchers.IO) { CanvasStorage.saveBranches(context, documentId, branches, current) }
                            },
                            onSymbolInstancesChanged = { instances -> CanvasStorage.saveSymbolInstances(context, currentDocumentId, instances) },
                            onBonesChanged = { bones -> CanvasStorage.saveBones(context, currentDocumentId, bones) },
                            onAnimationSettingsChanged = { settings -> CanvasStorage.saveAnimationSettings(context, currentDocumentId, settings) },
                            onFramesChanged = { frames -> CanvasStorage.saveFrames(context, currentDocumentId, frames) },
                            onLayersChanged = { layers -> CanvasStorage.saveLayers(context, currentDocumentId, layers) },
                            // Raster pixels are saved by the debounced writer below (it needs this state).
                            onRasterChanged = { rasterSaveRequests.value++ },
                            onRebased = { offset ->
                                if (pins.isNotEmpty()) {
                                    pins = pins.map { it.copy(x = it.x - offset.x, y = it.y - offset.y) }
                                    CanvasStorage.savePins(context, currentDocumentId, pins)
                                }
                            },
                        )
                    }

                    // Raster pixels: changed tiles are copied here on the main thread, then written off it.
                    fun saveRasters(documentId: String) {
                        val dirty = canvasState.takeDirtyRasterTiles()
                        val deleted = canvasState.takeDeletedRasterLayers()
                        if (dirty.isEmpty() && deleted.isEmpty()) return
                        coroutineScope.launch(Dispatchers.IO) {
                            for (id in deleted) CanvasStorage.deleteRasterLayer(context, documentId, id)
                            for ((id, change) in dirty) CanvasStorage.saveRasterTiles(context, documentId, id, change.first, change.second)
                        }
                    }
                    LaunchedEffect(Unit) {
                        rasterSaveRequests.collectLatest {
                            delay(PowerMonitor.autosaveDelayMillis(powerMonitor.constrained))
                            saveRasters(currentDocumentId)
                        }
                    }


                    val textMeasurer = rememberTextMeasurer()
                    val density = LocalDensity.current
                    val imageForExport: (String) -> ImageBitmap? = { fileName ->
                        BitmapFactory.decodeFile(CanvasStorage.imageFile(context, fileName).absolutePath)?.asImageBitmap()
                    }

                    fun currentDocumentName() = documents.firstOrNull { it.id == currentDocumentId }?.name ?: "Fern"

                    // Renders off the main thread, then writes to the picked location; null bytes = empty document.
                    fun writeExport(uri: Uri, render: () -> ByteArray?) {
                        coroutineScope.launch {
                            val bytes = withContext(Dispatchers.Default) { render() }
                            val message = if (bytes == null) {
                                context.getString(R.string.toast_nothing_export)
                            } else {
                                withContext(Dispatchers.IO) {
                                    context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                                }
                                context.getString(R.string.toast_exported)
                            }
                            FernMessages.show(message)
                        }
                    }

                    // Export goes through the system "save as" picker (Storage Access Framework):
                    // works on every API level from minSdk 26 without a storage permission.
                    val pngExportLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.CreateDocument("image/png"),
                    ) { uri ->
                        if (uri != null) writeExport(uri) { exportDocumentPng(canvasState, textMeasurer, density, imageForExport) }
                    }
                    val svgExportLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.CreateDocument("image/svg+xml"),
                    ) { uri ->
                        if (uri != null) writeExport(uri) {
                            exportDocumentSvg(canvasState) { fileName ->
                                CanvasStorage.imageFile(context, fileName).takeIf { it.exists() }?.readBytes()
                            }?.toByteArray()
                        }
                    }
                    var sliceScalesToExport by remember { mutableStateOf(emptyList<Int>()) }
                    val slicesExportLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.CreateDocument("application/zip"),
                    ) { uri ->
                        if (uri != null) writeExport(uri) {
                            exportSlicesZip(canvasState, canvasState.frames.toList(), sliceScalesToExport, textMeasurer, density, imageForExport)
                        }
                    }
                    val frameExportLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.CreateDocument("image/png"),
                    ) { uri ->
                        val frame = frameToExport
                        frameToExport = null
                        if (uri != null && frame != null) {
                            writeExport(uri) { exportDocumentPng(canvasState, textMeasurer, density, imageForExport, frame, wrap = frame.id == canvasState.wrapFrameId) }
                        }
                    }
                    val bundleExportLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.CreateDocument("application/octet-stream"),
                    ) { uri ->
                        if (uri == null) return@rememberLauncherForActivityResult
                        // Save first so the bundle holds exactly what's on screen.
                        CanvasStorage.save(context, currentDocumentId, canvasState.strokes.toList())
                        CanvasStorage.saveTextItems(context, currentDocumentId, canvasState.textItems.toList())
                        CanvasStorage.saveImageItems(context, currentDocumentId, canvasState.imageItems.toList())
                        val id = currentDocumentId
                        val name = currentDocumentName()
                        writeExport(uri) { exportDocumentBundle(context, id, name) }
                    }
                    val spriteAtlasExportLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.CreateDocument("application/zip"),
                    ) { uri ->
                        if (uri != null) writeExport(uri) {
                            // File-safe: the name goes inside the ZIP and the JSON.
                            val name = currentDocumentName().replace(Regex("[\\\\/:*?\"<>|]"), "_")
                            exportSpriteAtlas(canvasState, name, textMeasurer, density, imageForExport)
                        }
                    }
                    val spriteSheetExportLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.CreateDocument("image/png"),
                    ) { uri ->
                        if (uri != null) writeExport(uri) { exportSpriteSheetPng(canvasState, textMeasurer, density, imageForExport) }
                    }
                    val gifExportLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.CreateDocument("image/gif"),
                    ) { uri ->
                        if (uri != null) writeExport(uri) { exportAnimatedGif(canvasState, textMeasurer, density, imageForExport) }
                    }
                    val apngExportLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.CreateDocument("image/apng"),
                    ) { uri ->
                        if (uri != null) writeExport(uri) { exportAnimatedPng(canvasState, textMeasurer, density, imageForExport) }
                    }
                    val epsExportLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.CreateDocument("application/postscript"),
                    ) { uri ->
                        if (uri != null) writeExport(uri) { exportDocumentEps(canvasState)?.toByteArray() }
                    }
                    // A PDF-compatible Illustrator file (roadmap item 103).
                    val aiExportLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.CreateDocument("application/illustrator"),
                    ) { uri ->
                        if (uri != null) writeExport(uri) { exportDocumentAi(canvasState) }
                    }
                    val exrExportLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.CreateDocument("image/x-exr"),
                    ) { uri ->
                        if (uri != null) writeExport(uri) { exportDocumentExr(canvasState, textMeasurer, density, imageForExport) }
                    }
                    val cmykPdfExportLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.CreateDocument("application/pdf"),
                    ) { uri ->
                        if (uri != null) writeExport(uri) { exportDocumentCmykPdf(canvasState) }
                    }
                    val dxfExportLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.CreateDocument("image/vnd.dxf"),
                    ) { uri ->
                        if (uri != null) writeExport(uri) { exportDocumentDxf(canvasState)?.toByteArray() }
                    }
                    val lottieExportLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.CreateDocument("application/json"),
                    ) { uri ->
                        if (uri != null) writeExport(uri) { exportLottie(canvasState)?.toByteArray() }
                    }
                    val replayGifExportLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.CreateDocument("image/gif"),
                    ) { uri ->
                        if (uri != null) writeExport(uri) { exportReplayGif(canvasState, textMeasurer, density, imageForExport) }
                    }
                    val replayMp4ExportLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.CreateDocument("video/mp4"),
                    ) { uri ->
                        if (uri != null) writeExport(uri) { exportReplayMp4(canvasState, textMeasurer, density, imageForExport, context.cacheDir) }
                    }
                    val mp4ExportLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.CreateDocument("video/mp4"),
                    ) { uri ->
                        if (uri != null) writeExport(uri) { exportAnimationVideo(canvasState, textMeasurer, density, imageForExport, context.cacheDir) }
                    }
                    val webmExportLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.CreateDocument("video/webm"),
                    ) { uri ->
                        if (uri != null) writeExport(uri) { exportAnimationVideo(canvasState, textMeasurer, density, imageForExport, context.cacheDir, Mp4Encoder.Format.WEBM) }
                    }
                    val transparentPngExportLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.CreateDocument("image/png"),
                    ) { uri ->
                        if (uri != null) writeExport(uri) { exportDocumentPng(canvasState, textMeasurer, density, imageForExport, transparent = true) }
                    }
                    val selectionExportLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.CreateDocument("image/png"),
                    ) { uri ->
                        val region = canvasState.selectionExportRegion()
                        if (uri != null && region != null) {
                            writeExport(uri) { exportDocumentPng(canvasState, textMeasurer, density, imageForExport, region) }
                        }
                    }
                    val pdfExportLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.CreateDocument("application/pdf"),
                    ) { uri ->
                        if (uri != null) writeExport(uri) { exportDocumentPdf(canvasState, textMeasurer, imageForExport) }
                    }

                    val spineExportLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.CreateDocument("application/zip"),
                    ) { uri ->
                        if (uri != null) writeExport(uri) { exportSpine(canvasState, textMeasurer, density, imageForExport) }
                    }
                    val dragonBonesExportLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.CreateDocument("application/zip"),
                    ) { uri ->
                        if (uri != null) writeExport(uri) { exportDragonBones(canvasState, textMeasurer, density, imageForExport) }
                    }
                    val psdExportLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.CreateDocument("image/vnd.adobe.photoshop"),
                    ) { uri ->
                        if (uri != null) writeExport(uri) { renderLayers(canvasState, textMeasurer, density, imageForExport)?.let(PsdFormat::write) }
                    }
                    val kraExportLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.CreateDocument("application/x-krita"),
                    ) { uri ->
                        if (uri != null) writeExport(uri) { renderLayers(canvasState, textMeasurer, density, imageForExport)?.let(KraFormat::write) }
                    }
                    // A PDF or Illustrator file's paths as strokes (roadmap item 103).
                    val vectorImportLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.OpenDocument(),
                    ) { uri ->
                        if (uri == null) return@rememberLauncherForActivityResult
                        coroutineScope.launch {
                            val pages = withContext(Dispatchers.IO) {
                                runCatching {
                                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)
                                    readPdfVectors(bytes)
                                }.getOrNull()
                            }
                            val added = if (pages == null) 0 else canvasState.importVectorPages(pages)
                            if (added == 0) FernMessages.show(context.getString(R.string.toast_bad_vectors))
                        }
                    }
                    // PSD, Krita or Procreate, told apart by their contents (roadmap item 95).
                    val layeredImportLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.OpenDocument(),
                    ) { uri ->
                        if (uri == null) return@rememberLauncherForActivityResult
                        coroutineScope.launch {
                            val document = withContext(Dispatchers.IO) {
                                runCatching {
                                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)
                                    readLayeredFile(bytes)
                                }.getOrNull()
                            }
                            if (document == null || document.layers.isEmpty()) {
                                FernMessages.show(context.getString(R.string.toast_bad_layered))
                            } else {
                                canvasState.importLayers(document)
                            }
                        }
                    }
                    val spineImportLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.OpenDocument(),
                    ) { uri ->
                        if (uri == null) return@rememberLauncherForActivityResult
                        coroutineScope.launch {
                            val poses = withContext(Dispatchers.IO) {
                                runCatching {
                                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)
                                    SpineImport.read(bytes, canvasState.animFps)
                                }.getOrNull()
                            }
                            if (poses.isNullOrEmpty()) {
                                FernMessages.show(context.getString(R.string.toast_bad_spine))
                            } else {
                                canvasState.importSkeleton(poses)
                            }
                        }
                    }
                    val pdfImportLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.OpenDocument(),
                    ) { uri ->
                        if (uri == null) return@rememberLauncherForActivityResult
                        coroutineScope.launch {
                            val pages = withContext(Dispatchers.IO) { importPdfPages(context, uri) }
                            if (pages.isEmpty()) {
                                FernMessages.show(context.getString(R.string.toast_bad_pdf))
                                return@launch
                            }
                            // Pages stacked top to bottom like a scrolling document, each 512
                            // screen px wide at the current zoom (on the §7 scale), centred
                            // horizontally on the view with a 32 px gap between pages.
                            val scale = canvasState.scale.coerceAtLeast(1e-300)
                            val widthWorld = 512.0 / scale
                            val gapWorld = 32.0 / scale
                            var top = canvasState.panWorld.y
                            for ((index, page) in pages.withIndex()) {
                                val heightWorld = widthWorld * page.heightPx / page.widthPx
                                canvasState.addImage(
                                    page.fileName,
                                    WorldPoint(canvasState.panWorld.x - widthWorld / 2.0, top),
                                    widthWorld,
                                    heightWorld,
                                    recordUndo = index == 0, // the whole import undoes in one step
                                )
                                top += heightWorld + gapWorld
                            }
                        }
                    }

                    val paletteImportLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.OpenDocument(),
                    ) { uri ->
                        if (uri == null) return@rememberLauncherForActivityResult
                        coroutineScope.launch {
                            val colors = withContext(Dispatchers.IO) {
                                runCatching {
                                    context.contentResolver.openInputStream(uri)?.use { stream ->
                                        val bytes = stream.readBytes()
                                        // Told apart by content, not file name: providers don't always keep the extension.
                                        if (isAdobeSwatchExchange(bytes)) parseAdobeSwatchExchange(bytes)
                                        else parseGimpPalette(bytes.decodeToString())
                                    }
                                }.getOrNull().orEmpty()
                            }
                            colors.forEach { canvasState.addSavedColor(it) }
                            FernMessages.show(if (colors.isEmpty()) context.getString(R.string.toast_bad_palette) else context.resources.getQuantityString(R.plurals.toast_imported_colours, colors.size, colors.size))
                        }
                    }

                    /** Places the image at [uri] centred on [at] - the view centre for a pick or a paste, the drop point for a drop. */
                    suspend fun insertImage(uri: android.net.Uri, at: WorldPoint = canvasState.panWorld) {
                        val decoded = withContext(Dispatchers.IO) {
                            val bitmap = decodeDownsampled(context, uri, MAX_IMAGE_EDGE_PX) ?: return@withContext null
                            val fileName = "${UUID.randomUUID()}.png"
                            CanvasStorage.imageFile(context, fileName).outputStream().use { out ->
                                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                            }
                            Triple(fileName, bitmap.width, bitmap.height)
                        } ?: return
                        val (fileName, widthPx, heightPx) = decoded
                        // Default placed size: 300 screen px wide (on the §7 scale -
                        // between Primary 256 and 384... actually not on-scale, but this
                        // is placed-content sizing, not a UI chrome dimension, so §7
                        // doesn't govern it), aspect-ratio preserved, centered on the
                        // current viewport.
                        val targetWidthScreen = 300.0
                        val targetHeightScreen = targetWidthScreen * heightPx / widthPx
                        val scale = canvasState.scale.coerceAtLeast(1e-300)
                        val widthWorld = targetWidthScreen / scale
                        val heightWorld = targetHeightScreen / scale
                        val position = WorldPoint(at.x - widthWorld / 2.0, at.y - heightWorld / 2.0)
                        canvasState.addImage(fileName, position, widthWorld, heightWorld)
                    }

                    // An OBJ model to paint in 3D (roadmap item 108).
                    val objImportLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.OpenDocument(),
                    ) { uri ->
                        if (uri == null) return@rememberLauncherForActivityResult
                        coroutineScope.launch {
                            val stored = withContext(Dispatchers.Default) {
                                runCatching {
                                    val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
                                    text?.let(::readObj)?.let { mesh -> storeModel(context, newModel(mesh)) }
                                }.getOrNull()
                            }
                            if (stored == null) FernMessages.show(context.getString(R.string.toast_bad_obj))
                            else canvasState.placeModel(stored)
                        }
                    }

                    // Embedded video (roadmap item 107): copied in, shown by its first frame.
                    val videoPickerLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.OpenDocument(),
                    ) { uri ->
                        if (uri == null) return@rememberLauncherForActivityResult
                        coroutineScope.launch {
                            val video = withContext(Dispatchers.IO) { embedVideo(context, uri, MAX_IMAGE_EDGE_PX) }
                            if (video == null) {
                                FernMessages.show(context.getString(R.string.toast_bad_video))
                                return@launch
                            }
                            // Placed like a picked image: 300 screen px wide, centred on the view.
                            val scale = canvasState.scale.coerceAtLeast(1e-300)
                            val widthWorld = 300.0 / scale
                            val heightWorld = widthWorld * video.heightPx / video.widthPx
                            val at = canvasState.panWorld
                            canvasState.addImage(video.posterFileName, WorldPoint(at.x - widthWorld / 2, at.y - heightWorld / 2), widthWorld, heightWorld, videoFileName = video.videoFileName)
                        }
                    }

                    val imagePickerLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.PickVisualMedia(),
                    ) { uri ->
                        if (uri == null) return@rememberLauncherForActivityResult
                        coroutineScope.launch { insertImage(uri) }
                    }

                    // System clipboard (SystemClipboard): a copy carries a PNG of the selected
                    // area for other apps plus the strokes themselves for Fern.
                    fun copySelectionToClipboard() {
                        val strokes = canvasState.selectionForClipboard()
                        val region = canvasState.selectionExportRegion()
                        if (strokes.isEmpty() || region == null) return
                        coroutineScope.launch {
                            val png = exportDocumentPng(canvasState, textMeasurer, density, imageForExport, region) ?: return@launch
                            withContext(Dispatchers.IO) { SystemClipboard.copy(context, strokes, png) }
                            // Android 13+ confirms clipboard writes itself; earlier versions get a toast.
                            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                                FernMessages.show(context.getString(R.string.toast_copied))
                            }
                        }
                    }

                    // Drag and drop to other windows (roadmap item 75): the same clip as a copy,
                    // started once the picture is rendered if the finger is still down by then.
                    // Image trace (roadmap item 101): decoded at most TRACE_MAX_EDGE_PX on its long edge, traced off the main thread.
                    fun traceSelectedImage() {
                        val image = canvasState.imageItems.firstOrNull { it.id == canvasState.selectedImageId } ?: return
                        coroutineScope.launch {
                            val traced = withContext(Dispatchers.Default) {
                                val file = CanvasStorage.imageFile(context, image.fileName).absolutePath
                                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }.also { BitmapFactory.decodeFile(file, it) }
                                var sample = 1
                                while (maxOf(bounds.outWidth, bounds.outHeight) / sample > TRACE_MAX_EDGE_PX * 2) sample *= 2
                                val decoded = BitmapFactory.decodeFile(file, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return@withContext null
                                val scale = minOf(1f, TRACE_MAX_EDGE_PX.toFloat() / maxOf(decoded.width, decoded.height))
                                val bitmap = Bitmap.createScaledBitmap(decoded, maxOf(1, (decoded.width * scale).toInt()), maxOf(1, (decoded.height * scale).toInt()), true)
                                val pixels = IntArray(bitmap.width * bitmap.height).also { bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height) }
                                Triple(traceImage(pixels, bitmap.width, bitmap.height), bitmap.width, bitmap.height)
                            } ?: return@launch
                            if (traced.first.isEmpty()) FernMessages.show(context.getString(R.string.toast_trace_nothing))
                            else canvasState.addTracedImage(image, traced.first, traced.second, traced.third)
                        }
                    }

                    val view = LocalView.current
                    fun dragSelectionOut() {
                        val strokes = canvasState.selectionForClipboard()
                        val region = canvasState.selectionExportRegion()
                        if (strokes.isEmpty() || region == null) return
                        coroutineScope.launch {
                            val png = exportDocumentPng(canvasState, textMeasurer, density, imageForExport, region) ?: return@launch
                            val (clip, image) = withContext(Dispatchers.IO) {
                                SystemClipboard.dragClip(context, strokes, png) to BitmapFactory.decodeByteArray(png, 0, png.size)
                            }
                            if (image != null) startSelectionDrag(view, clip, image)
                        }
                    }

                    /**
                     * Drag and drop from other apps: an image or text dropped on the canvas lands
                     * where it was dropped (Fern strokes too, from another Fern window). Reading a
                     * dropped content URI needs the drop's permission grant, requested first.
                     */
                    fun dropOntoCanvas(event: DragEvent) {
                        val clip = event.clipData ?: return
                        val permissions = this@MainActivity.requestDragAndDropPermissions(event)
                        val size = canvasState.viewportSize
                        val center = Offset(size.width / 2f, size.height / 2f)
                        // The canvas fills the window, so the drop point is already in canvas coordinates.
                        val at = canvasState.screenToWorld(canvasState.unrotateScreen(Offset(event.x, event.y), center), center)
                        coroutineScope.launch {
                            try {
                                when (val content = withContext(Dispatchers.IO) { SystemClipboard.contentOf(context, clip) }) {
                                    is ClipboardContent.FernStrokes -> canvasState.pasteStrokes(content.strokes, at)
                                    is ClipboardContent.Text -> canvasState.pasteText(content.text, at)
                                    is ClipboardContent.Image -> insertImage(content.uri, at)
                                    ClipboardContent.Nothing -> {}
                                }
                            } finally {
                                permissions?.release()
                            }
                        }
                    }

                    fun pasteFromClipboard() {
                        coroutineScope.launch {
                            when (val content = withContext(Dispatchers.IO) { SystemClipboard.read(context) }) {
                                is ClipboardContent.FernStrokes -> canvasState.pasteStrokes(content.strokes)
                                is ClipboardContent.Text -> canvasState.pasteText(content.text)
                                is ClipboardContent.Image -> insertImage(content.uri)
                                ClipboardContent.Nothing -> FernMessages.show(context.getString(R.string.toast_nothing_paste))
                            }
                        }
                    }

                    suspend fun refreshDocuments() {
                        documents = withContext(Dispatchers.IO) { CanvasStorage.listDocuments(context) }
                    }

                    /**
                     * Reads a whole document off the main thread, then applies it in one go, and
                     * only then makes it current: a big document takes seconds to read (3.5 s for
                     * 1M strokes in ScaleBenchmark), which on the main thread froze the app, and
                     * an ON_STOP save while it was half loaded wrote the half-loaded canvas over it.
                     */
                    suspend fun loadDocument(documentId: String) {
                        // The saved view comes back first, so the part of a big document on screen
                        // can be read and shown before the rest (roadmap item 53).
                        val view = withContext(Dispatchers.IO) { CanvasStorage.loadView(context, documentId) }
                        if (view != null) canvasState.jumpTo(view.first, view.second)
                        val viewport = canvasState.viewportSize
                        val first = view?.takeIf { viewport.width > 0f }?.let { (center, scale) ->
                            val halfWidth = viewport.width / 2.0 / scale
                            val halfHeight = viewport.height / 2.0 / scale
                            StrokeChunkFile.Area(center.x - halfWidth, center.y - halfHeight, center.x + halfWidth, center.y + halfHeight)
                        }
                        // Main-thread flag: a preview posted late must not replace the full document.
                        var previewWanted = true
                        val loaded = withContext(Dispatchers.IO) {
                            LoadedDocument(
                                strokes = CanvasStorage.load(context, documentId, first) { visible ->
                                    // Shown under the loading cover, which still blocks input until everything is in.
                                    coroutineScope.launch(Dispatchers.Main) { if (previewWanted && loadingDocument) canvasState.loadStrokes(visible) }
                                },
                                layers = CanvasStorage.loadLayers(context, documentId),
                                layerComps = CanvasStorage.loadLayerComps(context, documentId),
                                stats = CanvasStorage.loadStats(context, documentId),
                                guides = CanvasStorage.loadGuides(context, documentId),
                                symbolInstances = CanvasStorage.loadSymbolInstances(context, documentId),
                                rasters = CanvasStorage.loadLayers(context, documentId).let { layers ->
                                    // Raster layers' pixels and every layer mask (roadmap items 89, 91).
                                    CanvasStorage.loadRasters(context, documentId, layers.filter { it.raster }.map { it.id } + layers.filter { it.masked }.map { maskIdOf(it.id) })
                                },
                                frames = CanvasStorage.loadFrames(context, documentId),
                                drawingScale = CanvasStorage.loadDrawingScale(context, documentId),
                                branches = CanvasStorage.loadBranches(context, documentId),
                                gameObjects = CanvasStorage.loadGameObjects(context, documentId),
                                history = CanvasStorage.loadHistory(context, documentId),
                                texts = CanvasStorage.loadTextItems(context, documentId),
                                connectors = CanvasStorage.loadConnectors(context, documentId),
                                bones = CanvasStorage.loadBones(context, documentId),
                                animation = CanvasStorage.loadAnimationSettings(context, documentId),
                                images = CanvasStorage.loadImageItems(context, documentId),
                                background = CanvasStorage.loadBackground(context, documentId),
                                pins = CanvasStorage.listPins(context, documentId),
                            )
                        }
                        previewWanted = false
                        canvasState.loadStrokes(loaded.strokes)
                        canvasState.loadLayers(loaded.layers)
                        canvasState.loadLayerComps(loaded.layerComps)
                        // File layers (roadmap item 138) show their files as they are now; one
                        // that can't be read keeps the pixels saved with the document.
                        for (layer in loaded.layers.filter { it.fileSource != null }) {
                            val image = withContext(Dispatchers.IO) { readFileLayerImage(context, Uri.parse(layer.fileSource)) } ?: continue
                            canvasState.reloadFileLayer(layer.id, image.argb, image.width, image.height, recordUndo = false)
                        }
                        canvasState.loadStats(loaded.stats)
                        canvasState.loadGuides(loaded.guides)
                        canvasState.loadDrawingScale(loaded.drawingScale)
                        canvasState.loadBranches(loaded.branches.first, loaded.branches.second)
                        canvasState.loadGameObjects(loaded.gameObjects.first, loaded.gameObjects.second)
                        canvasState.loadSymbolInstances(loaded.symbolInstances)
                        // Symbols redefined since this document was last open: catch its instances up.
                        if (loaded.symbolInstances.isNotEmpty()) {
                            val library = withContext(Dispatchers.IO) { SymbolLibrary.list(context) }
                            for (symbol in library) {
                                if (loaded.symbolInstances.none { it.symbolId == symbol.id && it.revision < symbol.revision }) continue
                                val master = withContext(Dispatchers.IO) { SymbolLibrary.load(context, symbol.id) }
                                if (master.isNotEmpty()) canvasState.syncSymbol(symbol, master, recordUndo = false)
                            }
                        }
                        canvasState.loadRasters(loaded.rasters)
                        canvasState.loadFrames(loaded.frames)
                        canvasState.loadHistory(loaded.history.first, loaded.history.second)
                        canvasState.loadTextItems(loaded.texts)
                        canvasState.loadConnectors(loaded.connectors)
                        canvasState.loadBones(loaded.bones)
                        canvasState.loadAnimationSettings(loaded.animation)
                        canvasState.loadImageItems(loaded.images)
                        canvasState.loadBackground(loaded.background.first, loaded.background.second)
                        pins = loaded.pins
                        currentDocumentId = documentId
                    }

                    /**
                     * Saves the open document and opens [documentId], both off the main thread,
                     * with the canvas covered (and so not drawable) until it's done. Switches run
                     * one at a time.
                     */
                    suspend fun switchToDocument(documentId: String) = switchMutex.withLock {
                        autosaveJob?.cancel()
                        playingNote = null // its ink is the other document's
                        loadingDocument = true
                        try {
                            val leaving = currentDocumentId
                            saveRasters(leaving)
                            val leavingView = canvasState.panWorld to canvasState.scale
                            val strokes = canvasState.strokes.toList()
                            val texts = canvasState.textItems.toList()
                            val images = canvasState.imageItems.toList()
                            val (undo, redo) = canvasState.historySnapshot()
                            withContext(Dispatchers.IO) {
                                CanvasStorage.saveView(context, leaving, leavingView.first, leavingView.second)
                                CanvasStorage.save(context, leaving, strokes)
                                CanvasStorage.saveTextItems(context, leaving, texts)
                                CanvasStorage.saveImageItems(context, leaving, images)
                                // The history lists materialize as read; nothing changes them meanwhile (canvas covered).
                                CanvasStorage.saveHistory(context, leaving, undo, redo)
                            }
                            loadDocument(documentId)
                        } finally {
                            loadingDocument = false
                        }
                    }

                    LaunchedEffect(Unit) {
                        canvasState.loadSavedColors(withContext(Dispatchers.IO) { CanvasStorage.loadSavedColors(context) })
                        canvasState.loadBrush(AppSettings.stampBrush(context))
                        canvasState.loadToolSlots(AppSettings.toolSlots(context))
                        canvasState.loadBrushPresets(AppSettings.brushPresets(context))
                        canvasState.snapToObjects = AppSettings.snapToObjects(context)
                        refreshDocuments()
                        var current = documents.firstOrNull { it.trashedAt == null }
                        if (current == null) {
                            current = withContext(Dispatchers.IO) { CanvasStorage.createDocument(context, context.getString(R.string.default_document_name)) }
                            refreshDocuments()
                        }
                        loadDocument(current.id)
                        loadingDocument = false
                    }

                    // Saved synchronously on ON_STOP: the write is small (JSON of the current
                    // strokes) and this is the one point we're guaranteed to still be alive to
                    // do it, unlike a background coroutine that could be cancelled alongside
                    // the composition tearing down.
                    DisposableEffect(lifecycleOwner) {
                        val observer = LifecycleEventObserver { _, event ->
                            if (event == Lifecycle.Event.ON_STOP && currentDocumentId.isNotEmpty()) {
                                // No thumbnail: rendering every stroke on the main thread here took seconds on
                                // huge documents; the library refreshes it when it's next opened.
                                CanvasStorage.saveView(context, currentDocumentId, canvasState.panWorld, canvasState.scale)
                                saveRasters(currentDocumentId)
                                CanvasStorage.save(context, currentDocumentId, canvasState.strokes.toList(), withThumbnail = false)
                                CanvasStorage.saveTextItems(context, currentDocumentId, canvasState.textItems.toList())
                                CanvasStorage.saveImageItems(context, currentDocumentId, canvasState.imageItems.toList())
                                canvasState.historySnapshot().let { (undo, redo) -> CanvasStorage.saveHistory(context, currentDocumentId, undo, redo) }
                            }
                        }
                        lifecycleOwner.lifecycle.addObserver(observer)
                        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                    }

                    // Time-lapse replay: reveals strokes over about 8 seconds whatever the document's
                    // size, then holds the finished drawing for a moment and ends.
                    LaunchedEffect(canvasState.replayCount == null) {
                        if (canvasState.replayCount == null) return@LaunchedEffect
                        val total = canvasState.strokes.size
                        val perTick = maxOf(1, total / 240)
                        while (true) {
                            val shown = canvasState.replayCount ?: break
                            if (shown >= total) {
                                delay(1500)
                                canvasState.replayCount = null
                                break
                            }
                            delay(33)
                            canvasState.replayCount = minOf(total, shown + perTick)
                        }
                    }

                    // Flipbook playback: advances one frame per tick while playing.
                    LaunchedEffect(canvasState.playing, canvasState.animFps, powerMonitor.constrained) {
                        while (canvasState.playing) {
                            delay(1000L / PowerMonitor.playbackFps(canvasState.animFps, powerMonitor.constrained))
                            canvasState.advanceAnimFrame()
                        }
                    }
                    // The soundtrack plays along from the frame playback starts on, and starts over
                    // whenever the animation loops back to its first frame (roadmap item 88).
                    val soundtrackName = canvasState.soundtrack
                    var soundtrackRestart by remember { mutableIntStateOf(0) }
                    DisposableEffect(canvasState.playing, soundtrackName, soundtrackRestart) {
                        val player = if (canvasState.playing && soundtrackName != null) {
                            runCatching {
                                android.media.MediaPlayer().apply {
                                    setDataSource(CanvasStorage.soundtrackFile(context, soundtrackName).path)
                                    prepare()
                                    seekTo(canvasState.frameStartMillis(canvasState.currentAnimFrame).toInt())
                                    start()
                                }
                            }.getOrNull()
                        } else {
                            null
                        }
                        onDispose { player?.release() }
                    }
                    LaunchedEffect(canvasState.playing, soundtrackName) {
                        if (!canvasState.playing || soundtrackName == null) return@LaunchedEffect
                        // drop(1): the frame playback started on is already where the player began.
                        snapshotFlow { canvasState.currentAnimFrame }.drop(1).collect { frame ->
                            if (frame == 0) soundtrackRestart++
                        }
                    }

                    val shortcutFocus = remember { FocusRequester() }
                    LaunchedEffect(Unit) { shortcutFocus.requestFocus() }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            // Hardware-keyboard shortcuts (tablets with keyboards, Chromebooks),
                            // the standard desktop set. Pen tool on P, eraser on E, select on V
                            // follow common drawing-app letters.
                            .focusRequester(shortcutFocus)
                            .focusable()
                            .onPreviewKeyEvent { event ->
                                val native = event.nativeKeyEvent
                                val isCtrlKey = native.keyCode == android.view.KeyEvent.KEYCODE_CTRL_LEFT || native.keyCode == android.view.KeyEvent.KEYCODE_CTRL_RIGHT ||
                                    native.keyCode == android.view.KeyEvent.KEYCODE_META_LEFT || native.keyCode == android.view.KeyEvent.KEYCODE_META_RIGHT
                                // The shortcuts at a glance while Ctrl is held (design roadmap D18).
                                if (isCtrlKey) {
                                    ShortcutCapture.cheatSheet = event.type == KeyEventType.KeyDown
                                    return@onPreviewKeyEvent false
                                }
                                ShortcutCapture.cheatSheet = false
                                // A tool key held down borrows its tool until released (design roadmap D17).
                                if (event.type == KeyEventType.KeyUp) {
                                    val hold = toolKeyHold
                                    if (hold != null && hold.first == event.key) {
                                        if (SystemClock.uptimeMillis() - hold.third >= TOOL_KEY_HOLD_MILLIS) canvasState.activeTool = hold.second
                                        toolKeyHold = null
                                        return@onPreviewKeyEvent true
                                    }
                                    return@onPreviewKeyEvent false
                                }
                                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                // Stylus barrel and tail buttons arrive as key events (API 34+ USI pens).
                                if (native.keyCode in STYLUS_BUTTON_KEYCODES) {
                                    canvasState.runGestureAction(GestureSettings.read(context).getValue(CanvasGesture.STYLUS_BUTTON), null) { action ->
                                        when (action) {
                                            GestureAction.QUICK_MENU -> showQuickMenu = true
                                            GestureAction.COMMAND_PALETTE -> CommandPaletteState.open = true
                                            GestureAction.TOGGLE_CHROME -> chromeHidden = !chromeHidden
                                            else -> {}
                                        }
                                    }
                                    return@onPreviewKeyEvent true
                                }
                                val combo = KeyCombo(native.keyCode, event.isCtrlPressed || event.isMetaPressed, event.isShiftPressed)
                                val action = ShortcutSettings.actionFor(ShortcutSettings.state(context).value, combo) ?: return@onPreviewKeyEvent false
                                action.tool?.let { tool ->
                                    if (native.repeatCount == 0) {
                                        toolKeyHold = Triple(event.key, canvasState.activeTool, SystemClock.uptimeMillis())
                                        canvasState.activeTool = tool
                                    }
                                    return@onPreviewKeyEvent true
                                }
                                val center = Offset(canvasState.viewportSize.width / 2f, canvasState.viewportSize.height / 2f)
                                when (action) {
                                    ShortcutAction.UNDO -> canvasState.undo()
                                    ShortcutAction.REDO, ShortcutAction.REDO_ALT -> canvasState.redo()
                                    ShortcutAction.COPY -> copySelectionToClipboard()
                                    ShortcutAction.PASTE -> pasteFromClipboard()
                                    ShortcutAction.DUPLICATE -> canvasState.duplicateSelection()
                                    ShortcutAction.DELETE, ShortcutAction.DELETE_BACK -> {
                                        canvasState.deleteSelection()
                                        FernMessages.show(context.getString(R.string.msg_deleted), context.getString(R.string.action_undo)) { canvasState.undo() }
                                    }
                                    ShortcutAction.SIZE_DOWN -> canvasState.activeWidthWorld = (canvasState.activeWidthWorld / SIZE_STEP).coerceAtLeast(MIN_WIDTH_SCREEN.toDouble())
                                    ShortcutAction.SIZE_UP -> canvasState.activeWidthWorld = (canvasState.activeWidthWorld * SIZE_STEP).coerceAtMost(MAX_WIDTH_SCREEN.toDouble())
                                    ShortcutAction.ZOOM_IN -> canvasState.zoomAround(center, center, ZOOM_STEP)
                                    ShortcutAction.ZOOM_OUT -> canvasState.zoomAround(center, center, 1 / ZOOM_STEP)
                                    ShortcutAction.FIT -> canvasState.zoomToFit()
                                    ShortcutAction.PALETTE -> CommandPaletteState.open = true
                                    ShortcutAction.TOGGLE_CHROME -> chromeHidden = !chromeHidden
                                    else -> return@onPreviewKeyEvent false
                                }
                                true
                            },
                    ) {
                        DrawingCanvas(
                            state = canvasState,
                            modifier = Modifier
                                .fillMaxSize()
                                .dragAndDropTarget(
                                    shouldStartDragAndDrop = { event ->
                                        event.mimeTypes().any { it.startsWith("image/") || it.startsWith("text/") }
                                    },
                                    target = remember {
                                        object : DragAndDropTarget {
                                            override fun onDrop(event: DragAndDropEvent): Boolean {
                                                dropOntoCanvas(event.toAndroidDragEvent())
                                                return true
                                            }
                                        }
                                    },
                                ),
                            linkChoices = LinkChoices(pins, documents.filter { it.trashedAt == null && it.id != currentDocumentId }),
                            onGestureAction = { action ->
                                when (action) {
                                    GestureAction.QUICK_MENU -> showQuickMenu = true
                                    GestureAction.COMMAND_PALETTE -> CommandPaletteState.open = true
                                    GestureAction.TOGGLE_CHROME -> chromeHidden = !chromeHidden
                                    else -> {}
                                }
                            },
                            onFollowLink = { target ->
                                when (target) {
                                    is LinkTarget.Pin -> {
                                        val pin = pins.firstOrNull { it.id == target.pinId }
                                        if (pin != null) canvasState.jumpTo(WorldPoint(pin.x, pin.y), pin.scale)
                                        else FernMessages.show(context.getString(R.string.toast_pin_gone))
                                    }
                                    is LinkTarget.Document -> {
                                        val document = documents.firstOrNull { it.id == target.documentId && it.trashedAt == null }
                                        if (document != null) coroutineScope.launch { switchToDocument(document.id) }
                                        else FernMessages.show(context.getString(R.string.toast_document_gone))
                                    }
                                }
                            },
                        )
                        val presenting = presentingIndex
                        // Below the status bar, and below the toolbar when it's at the top.
                        if (canvasState.showRulers) {
                            val insetTop = WindowInsets.systemBars.asPaddingValues().calculateTopPadding()
                            Rulers(canvasState, topInset = insetTop + if (toolbarAtBottom) FernScale.none else TOOLBAR_CLEARANCE_DP.dp)
                        }
                        // Above the canvas, beneath every control: while a voice note plays, a tap on its ink seeks.
                        if (playingNote != null && audioNotes.recordingSince == null) {
                            AudioSeekOverlay(audio = audioNotes, state = canvasState, modifier = Modifier.fillMaxSize())
                        }
                        if (presenting != null && presenting in canvasState.frames.indices) {
                            // Presentation mode: editing chrome hidden, frames shown one at a time.
                            PresentationControls(
                                position = presenting,
                                count = canvasState.frames.size,
                                onStep = { step ->
                                    val next = (presenting + step).coerceIn(canvasState.frames.indices)
                                    presentingIndex = next
                                    canvasState.jumpToFrame(canvasState.frames[next])
                                },
                                onExit = { presentingIndex = null },
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(WindowInsets.systemBars.asPaddingValues())
                                    .padding(FernScale.s16),
                            )
                        } else if (canvasState.play != null) {
                            val playing = canvasState.play
                            DisposableEffect(playing) {
                                playInput = playing?.input
                                onDispose { playInput = null }
                            }
                            // The game playing (roadmap item 155): editing chrome hidden.
                            PlayOverlay(canvasState)
                        } else if (chromeHidden) {
                            // The one way back that needs no gesture.
                            IconButton(
                                onClick = { chromeHidden = false },
                                modifier = Modifier
                                    .align(if (leftHanded) AbsoluteAlignment.TopRight else AbsoluteAlignment.TopLeft)
                                    .padding(WindowInsets.systemBars.asPaddingValues())
                                    .padding(FernScale.s8),
                            ) { Icon(Icons.Filled.FullscreenExit, contentDescription = stringResource(R.string.chrome_show)) }
                            if (showQuickMenu) QuickMenuOverlay(state = canvasState, onDismiss = { showQuickMenu = false }, actions = quickMenuActions)
                        } else {
                            // The tool rail (design roadmap D8): along the bottom of an upright phone, in
                            // thumb reach; along the free hand's side otherwise. Other bars keep clear of it.
                            val portrait = LocalConfiguration.current.let { it.screenHeightDp >= it.screenWidthDp }
                            val railInset = if (portrait) FernScale.s64 else FernScale.none
                            val railSide = if (portrait) FernScale.none else FernScale.s64
                            Toolbar(
                                state = canvasState,
                                hiddenGroups = hiddenToolbarGroups,
                                onHiddenGroupsChange = { hiddenToolbarGroups = it },
                                // Recent documents in the command palette (design roadmap D14).
                                extraCommands = documents.filter { it.trashedAt == null && it.id != currentDocumentId }.sortedByDescending { it.lastModified }.take(RECENT_IN_PALETTE).map { doc ->
                                    PaletteCommand(doc.name, doc.name, context.getString(R.string.palette_location_documents)) { coroutineScope.launch { switchToDocument(doc.id) } }
                                },
                                propertiesModifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(WindowInsets.systemBars.asPaddingValues())
                                    .padding(bottom = railInset + FernScale.s8, start = railSide, end = railSide)
                                    .padding(horizontal = FernScale.s8),
                                wrap = LocalConfiguration.current.screenWidthDp >= WIDE_WINDOW_DP,
                                modifier = Modifier
                                    .align(if (toolbarAtBottom) Alignment.BottomCenter else Alignment.TopCenter)
                                    .padding(WindowInsets.systemBars.asPaddingValues())
                                    .padding(bottom = if (toolbarAtBottom) railInset else FernScale.none)
                                    .padding(vertical = FernScale.s12),
                                onOpenDocuments = {
                                    // Autosave skips thumbnails, so bring this document's up to date for the library.
                                    val id = currentDocumentId
                                    val snapshot = canvasState.strokes.toList()
                                    coroutineScope.launch {
                                        withContext(Dispatchers.IO) { CanvasStorage.saveThumbnail(context, id, snapshot) }
                                        refreshDocuments()
                                        showDocumentsDialog = true
                                    }
                                },
                                onOpenPins = { showPinsDialog = true },
                                onOpenFrames = { showFramesDialog = true },
                                onOpenSettings = { showSettings = true },
                                onCopySelection = { copySelectionToClipboard() },
                                onDragSelectionOut = { dragSelectionOut() },
                                onInsertVideo = { videoPickerLauncher.launch(arrayOf("video/*")) },
                                onImportObj = { objImportLauncher.launch(arrayOf("*/*")) },
                                onTraceImage = { traceSelectedImage() },
                                onPaste = { pasteFromClipboard() },
                                onOpenAudioNotes = {
                                    val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                                    if (granted) showAudioNotes = true else micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                },
                                onImportPalette = { paletteImportLauncher.launch(arrayOf("*/*")) },
                                onOpenVersions = {
                                    coroutineScope.launch {
                                        versions = withContext(Dispatchers.IO) { CanvasStorage.listVersions(context, currentDocumentId) }
                                    }
                                },
                                onExportPng = { pngExportLauncher.launch("${currentDocumentName()}.png") },
                                onExportSelection = { selectionExportLauncher.launch(context.getString(R.string.name_selection, currentDocumentName()) + ".png") },
                                onExportTransparentPng = { transparentPngExportLauncher.launch("${currentDocumentName()}.png") },
                                onExportPdf = { pdfExportLauncher.launch("${currentDocumentName()}.pdf") },
                                onExportSvg = { svgExportLauncher.launch("${currentDocumentName()}.svg") },
                                onExportEps = { epsExportLauncher.launch("${currentDocumentName()}.eps") },
                                onExportAi = { aiExportLauncher.launch("${currentDocumentName()}.ai") },
                                onExportDxf = { dxfExportLauncher.launch("${currentDocumentName()}.dxf") },
                                onExportCmykPdf = { cmykPdfExportLauncher.launch("${currentDocumentName()} CMYK.pdf") },
                                onExportExr = { exrExportLauncher.launch("${currentDocumentName()}.exr") },
                                onExportGif = { gifExportLauncher.launch("${currentDocumentName()}.gif") },
                                onExportApng = { apngExportLauncher.launch("${currentDocumentName()}.png") },
                                onExportLottie = { lottieExportLauncher.launch("${currentDocumentName()}.json") },
                                onExportReplayGif = { replayGifExportLauncher.launch(context.getString(R.string.name_timelapse, currentDocumentName()) + ".gif") },
                                onExportReplayMp4 = { replayMp4ExportLauncher.launch(context.getString(R.string.name_timelapse, currentDocumentName()) + ".mp4") },
                                onExportMp4 = { mp4ExportLauncher.launch("${currentDocumentName()}.mp4") },
                                onExportWebm = { webmExportLauncher.launch("${currentDocumentName()}.webm") },
                                onExportSpriteSheet = { spriteSheetExportLauncher.launch(context.getString(R.string.name_sprites, currentDocumentName()) + ".png") },
                                onExportSpriteAtlas = { spriteAtlasExportLauncher.launch(context.getString(R.string.name_sprites, currentDocumentName()) + ".zip") },
                                onExportBundle = { bundleExportLauncher.launch("${currentDocumentName()}.fern") },
                                onShare = {
                                    coroutineScope.launch {
                                        val png = withContext(Dispatchers.Default) {
                                            exportDocumentPng(canvasState, textMeasurer, density, imageForExport)
                                        }
                                        if (png == null) {
                                            FernMessages.show(context.getString(R.string.toast_nothing_share))
                                            return@launch
                                        }
                                        // Overwrites the previous share each time, so the cache never accumulates exports.
                                        val file = withContext(Dispatchers.IO) {
                                            val dir = File(context.cacheDir, "shared").apply { mkdirs() }
                                            dir.listFiles()?.forEach { it.delete() }
                                            // A "/" in a document name would otherwise be read as a folder.
                                            File(dir, "${currentDocumentName().replace('/', '_')}.png").apply { writeBytes(png) }
                                        }
                                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.share", file)
                                        val send = Intent(Intent.ACTION_SEND).apply {
                                            type = "image/png"
                                            putExtra(Intent.EXTRA_STREAM, uri)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(Intent.createChooser(send, null))
                                    }
                                },
                                onImportPdf = { pdfImportLauncher.launch(arrayOf("application/pdf")) },
                                onImportSpine = { spineImportLauncher.launch(arrayOf("application/json", "application/zip", "application/octet-stream")) },
                                onImportLayered = { layeredImportLauncher.launch(arrayOf("*/*")) },
                                onImportVectors = { vectorImportLauncher.launch(arrayOf("application/pdf", "application/illustrator", "application/postscript", "*/*")) },
                                onExportPsd = { psdExportLauncher.launch("${currentDocumentName()}.psd") },
                                onExportKra = { kraExportLauncher.launch("${currentDocumentName()}.kra") },
                                onExportSpine = { spineExportLauncher.launch("${currentDocumentName()} (Spine).zip") },
                                onExportDragonBones = { dragonBonesExportLauncher.launch("${currentDocumentName()} (DragonBones).zip") },
                                onInsertImage = {
                                    imagePickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                            )
                            if (canvasState.animationMode) {
                                AnimationBar(
                                    state = canvasState,
                                    modifier = Modifier
                                        // Opposite edge from the toolbar so the two never overlap.
                                        .align(if (toolbarAtBottom) Alignment.TopCenter else Alignment.BottomCenter)
                                        .padding(WindowInsets.systemBars.asPaddingValues())
                                        .padding(bottom = FernScale.s16)
                                        .padding(bottom = if (toolbarAtBottom) FernScale.none else railInset),
                                )
                            }
                            if (canvasState.secondViewOpen) {
                                SecondView(
                                    state = canvasState,
                                    modifier = Modifier
                                        // Opposite the drawing hand, above the minimap.
                                        .align(if (leftHanded) AbsoluteAlignment.TopRight else AbsoluteAlignment.TopLeft)
                                        .padding(WindowInsets.systemBars.asPaddingValues())
                                        .padding(top = FernScale.s64, start = FernScale.s12, end = FernScale.s12)
                                        .padding(horizontal = railSide)
                                        .fillMaxWidth(0.5f)
                                        .fillMaxHeight(0.4f),
                                )
                            }
                            Minimap(
                                state = canvasState,
                                modifier = Modifier
                                    // Opposite the drawing hand, whose palm rests below and beside the pen.
                                    // Physical sides (not start/end): the hands are the same in a right-to-left language.
                                    .align(if (leftHanded) AbsoluteAlignment.BottomRight else AbsoluteAlignment.BottomLeft)
                                    .padding(WindowInsets.systemBars.asPaddingValues())
                                    .padding(FernScale.s12)
                                    .padding(bottom = railInset)
                                    .padding(horizontal = railSide),
                            )
                            CanvasHaptics(canvasState)
                            // Messages (design roadmap D6), opposite the toolbar and out from under the drawing hand.
                            FernMessageHost(
                                Modifier
                                    .align(
                                        when {
                                            toolbarAtBottom -> if (leftHanded) AbsoluteAlignment.TopRight else AbsoluteAlignment.TopLeft
                                            else -> if (leftHanded) AbsoluteAlignment.BottomRight else AbsoluteAlignment.BottomLeft
                                        },
                                    )
                                    .padding(WindowInsets.systemBars.asPaddingValues())
                                    .padding(vertical = FernScale.s64)
                                    .padding(bottom = railInset)
                                    .padding(horizontal = railSide),
                            )
                            AudioBar(
                                audio = audioNotes,
                                state = canvasState,
                                playing = playingNote,
                                onClosePlayback = { playingNote = null },
                                modifier = Modifier
                                    // Opposite edge from the toolbar, like the animation bar.
                                    .align(if (toolbarAtBottom) Alignment.TopCenter else Alignment.BottomCenter)
                                    .padding(WindowInsets.systemBars.asPaddingValues())
                                    .padding(vertical = FernScale.s64)
                                    .padding(bottom = railInset),
                            )
                            QuickMenuButton(
                                onOpen = { showQuickMenu = true },
                                modifier = Modifier
                                    // Same side as the minimap, mid-height: in reach of the free hand's thumb.
                                    .align(if (leftHanded) AbsoluteAlignment.CenterRight else AbsoluteAlignment.CenterLeft)
                                    .padding(WindowInsets.systemBars.asPaddingValues())
                                    .padding(FernScale.s12)
                                    .padding(horizontal = railSide),
                            )
                            ToolRail(
                                state = canvasState,
                                vertical = !portrait,
                                modifier = Modifier
                                    .align(
                                        when {
                                            portrait -> Alignment.BottomCenter
                                            leftHanded -> AbsoluteAlignment.CenterRight
                                            else -> AbsoluteAlignment.CenterLeft
                                        },
                                    )
                                    .padding(WindowInsets.systemBars.asPaddingValues())
                                    .padding(FernScale.s8),
                            )
                            if (ShortcutCapture.cheatSheet) ShortcutCheatSheet(Modifier.align(Alignment.Center))
                            // Panels (design roadmap D10): beside the rail, never covering the canvas.
                            FernPanelHost(railOnLeft = !leftHanded, railInset = railInset, railSide = railSide)
                            if (showQuickMenu) QuickMenuOverlay(state = canvasState, onDismiss = { showQuickMenu = false }, actions = quickMenuActions)
                            if (loadingDocument) DocumentLoadingOverlay()
                        }
                    }

                    // .fern files have no registered MIME type, so the picker accepts any file and
                    // import rejects anything that isn't a valid bundle.
                    val bundleImportLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.OpenDocument(),
                    ) { uri ->
                        if (uri == null) return@rememberLauncherForActivityResult
                        coroutineScope.launch {
                            val imported = withContext(Dispatchers.IO) {
                                runCatching {
                                    context.contentResolver.openInputStream(uri)?.use { importDocumentBundle(context, it) }
                                }.getOrNull()
                            }
                            if (imported == null) {
                                FernMessages.show(context.getString(R.string.toast_bad_backup))
                                return@launch
                            }
                            refreshDocuments()
                            switchToDocument(imported.id)
                            showDocumentsDialog = false
                        }
                    }

                    if (showDocumentsDialog) {
                        DocumentsDialog(
                            documents = documents,
                            currentDocumentId = currentDocumentId,
                            onSelect = { doc ->
                                coroutineScope.launch { switchToDocument(doc.id) }
                                showDocumentsDialog = false
                            },
                            onRename = { doc, newName ->
                                CanvasStorage.renameDocument(context, doc.id, newName)
                                coroutineScope.launch { refreshDocuments() }
                            },
                            onDuplicate = { doc ->
                                CanvasStorage.duplicateDocument(context, doc.id, context.getString(R.string.name_copy, doc.name))
                                coroutineScope.launch { refreshDocuments() }
                            },
                            onToggleFavorite = { doc ->
                                CanvasStorage.setFavorite(context, doc.id, !doc.favorite)
                                coroutineScope.launch { refreshDocuments() }
                            },
                            onMoveToFolder = { doc, folder ->
                                CanvasStorage.setFolder(context, doc.id, folder)
                                coroutineScope.launch { refreshDocuments() }
                            },
                            onTrash = { doc ->
                                val wasCurrent = doc.id == currentDocumentId
                                CanvasStorage.moveToTrash(context, doc.id)
                                coroutineScope.launch {
                                    refreshDocuments()
                                    if (wasCurrent) {
                                        documents.firstOrNull { it.trashedAt == null }?.let { switchToDocument(it.id) }
                                    }
                                }
                            },
                            onRestore = { doc ->
                                CanvasStorage.restoreFromTrash(context, doc.id)
                                coroutineScope.launch { refreshDocuments() }
                            },
                            onDeleteForever = { doc ->
                                CanvasStorage.deleteDocument(context, doc.id)
                                coroutineScope.launch { refreshDocuments() }
                            },
                            onCreateNew = { template ->
                                coroutineScope.launch {
                                    val created = withContext(Dispatchers.IO) {
                                        CanvasStorage.createDocument(context, context.getString(if (template == DocumentTemplate.BLANK) R.string.untitled else template.label))
                                    }
                                    refreshDocuments()
                                    switchToDocument(created.id)
                                    canvasState.resetView()
                                    if (template.paper != BackgroundStyle.PLAIN) {
                                        canvasState.setBackground(canvasState.backgroundColor, template.paper)
                                    }
                                    when (template) {
                                        DocumentTemplate.COMIC -> canvasState.insertComicPage()
                                        DocumentTemplate.STORYBOARD -> canvasState.insertStoryboard()
                                        DocumentTemplate.PLANNER -> {
                                            // The locale's own week: its first day, its day names.
                                            val locale = Locale.getDefault()
                                            val first = WeekFields.of(locale).firstDayOfWeek
                                            val days = (0L until 7L).map { first.plus(it).getDisplayName(JavaTextStyle.FULL, locale).replaceFirstChar { c -> c.titlecase(locale) } }
                                            canvasState.insertWeeklyPlanner(context.getString(R.string.template_planner), days, context.getString(R.string.planner_notes))
                                        }
                                        DocumentTemplate.MIND_MAP -> canvasState.insertMindMap(context.getString(R.string.mind_map_central), context.getString(R.string.mind_map_branch))
                                        else -> {}
                                    }
                                    showDocumentsDialog = false
                                }
                            },
                            onImport = { bundleImportLauncher.launch(arrayOf("*/*")) },
                            onDismiss = { showDocumentsDialog = false },
                            loadThumbnail = { doc -> CanvasStorage.loadThumbnail(context, doc.id)?.asImageBitmap() },
                        )
                    }

                    versions?.let { list ->
                        VersionsDialog(
                            versions = list,
                            onRestore = { timestamp ->
                                canvasState.restoreVersion(CanvasStorage.loadVersion(context, currentDocumentId, timestamp))
                                versions = null
                            },
                            onDismiss = { versions = null },
                        )
                    }

                    crashReport?.let { report ->
                        AlertDialog(
                            onDismissRequest = {
                                CrashReporter.clearPendingReport(context)
                                crashReport = null
                            },
                            title = { Text(stringResource(R.string.crash_title)) },
                            text = { Text(stringResource(R.string.crash_text)) },
                            confirmButton = {
                                TextButton(onClick = {
                                    val send = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.crash_subject))
                                        putExtra(Intent.EXTRA_TEXT, report)
                                    }
                                    context.startActivity(Intent.createChooser(send, null))
                                    CrashReporter.clearPendingReport(context)
                                    crashReport = null
                                }) { Text(stringResource(R.string.crash_share)) }
                            },
                            dismissButton = {
                                TextButton(onClick = {
                                    CrashReporter.clearPendingReport(context)
                                    crashReport = null
                                }) { Text(stringResource(R.string.action_dismiss)) }
                            },
                        )
                    }

                    if (showAudioNotes) {
                        AudioNotesDialog(
                            audio = audioNotes,
                            documentId = currentDocumentId,
                            onPlay = { note ->
                                playingNote = note
                                showAudioNotes = false
                            },
                            onDismiss = { showAudioNotes = false },
                        )
                    }

                    if (showSettings) {
                        SettingsDialog(
                            leftHanded = leftHanded,
                            onLeftHandedChange = { value ->
                                leftHanded = value
                                AppSettings.setLeftHanded(context, value)
                            },
                            toolbarAtBottom = toolbarAtBottom,
                            onToolbarAtBottomChange = { value ->
                                toolbarAtBottom = value
                                AppSettings.setToolbarAtBottom(context, value)
                            },
                            highContrast = highContrast,
                            onHighContrastChange = { value ->
                                highContrast = value
                                AppSettings.setHighContrast(context, value)
                            },
                            colorBlindSafe = colorBlindSafe,
                            onColorBlindSafeChange = { value ->
                                colorBlindSafe = value
                                AppSettings.setColorBlindSafe(context, value)
                            },
                            haptics = FernHaptics.enabled,
                            onHapticsChange = { value ->
                                FernHaptics.enabled = value
                                AppSettings.setHaptics(context, value)
                            },
                            snapToObjects = canvasState.snapToObjects,
                            onSnapToObjectsChange = { value ->
                                canvasState.snapToObjects = value
                                AppSettings.setSnapToObjects(context, value)
                            },
                            onShowGuide = {
                                showSettings = false
                                showWelcome = true
                            },
                            onCustomizeToolbar = {
                                showSettings = false
                                showToolbarCustomize = true
                            },
                            onCustomizeQuickMenu = {
                                showSettings = false
                                showQuickMenuCustomize = true
                            },
                            onDismiss = { showSettings = false },
                        )
                    }

                    if (showQuickMenuCustomize) {
                        QuickMenuCustomizeDialog(
                            chosen = quickMenuActions,
                            onChange = { value ->
                                quickMenuActions = value
                                AppSettings.setQuickMenuActions(context, value)
                            },
                            state = canvasState,
                            onDismiss = { showQuickMenuCustomize = false },
                        )
                    }

                    if (showToolbarCustomize) {
                        ToolbarCustomizeDialog(
                            hidden = hiddenToolbarGroups,
                            onHiddenChange = { value ->
                                hiddenToolbarGroups = value
                                AppSettings.setHiddenToolbarGroups(context, value)
                            },
                            onDismiss = { showToolbarCustomize = false },
                        )
                    }

                    if (showWelcome) {
                        WelcomeDialog(onDismiss = {
                            markWelcomeSeen(context)
                            showWelcome = false
                        })
                    }

                    if (showFramesDialog) {
                        FramesDialog(
                            frames = canvasState.frames,
                            onJump = { frame ->
                                canvasState.jumpToFrame(frame)
                                showFramesDialog = false
                            },
                            onExport = { frame ->
                                frameToExport = frame
                                frameExportLauncher.launch("${frame.name.replace('/', '_')}.png")
                            },
                            onExportSlices = { scales ->
                                sliceScalesToExport = scales
                                slicesExportLauncher.launch("${currentDocumentName()} slices.zip")
                            },
                            onDelete = { frame -> canvasState.deleteFrame(frame.id) },
                            onSetLayoutGrid = { frame, grid -> canvasState.setFrameLayoutGrid(frame.id, grid) },
                            onResize = { frame, width, height -> canvasState.resizeFrame(frame.id, width, height) },
                            drawingScale = canvasState.drawingScale,
                            onCreate = { name -> canvasState.addFrameFromView(name) },
                            onAddComicPage = {
                                canvasState.insertComicPage()
                                showFramesDialog = false
                            },
                            onPresent = {
                                showFramesDialog = false
                                presentingIndex = 0
                                canvasState.jumpToFrame(canvasState.frames[0])
                            },
                            onDismiss = { showFramesDialog = false },
                        )
                    }

                    if (showPinsDialog) {
                        PinsDialog(
                            pins = pins,
                            onJump = { pin ->
                                canvasState.jumpTo(WorldPoint(pin.x, pin.y), pin.scale)
                                showPinsDialog = false
                            },
                            onDelete = { pin ->
                                val updated = pins.filter { it.id != pin.id }
                                CanvasStorage.savePins(context, currentDocumentId, updated)
                                pins = updated
                            },
                            onCreate = { name ->
                                val newPin = PinMeta(
                                    id = UUID.randomUUID().toString(),
                                    name = name,
                                    x = canvasState.panWorld.x,
                                    y = canvasState.panWorld.y,
                                    scale = canvasState.scale,
                                )
                                val updated = pins + newPin
                                CanvasStorage.savePins(context, currentDocumentId, updated)
                                pins = updated
                            },
                            onDismiss = { showPinsDialog = false },
                        )
                    }
                }
            }
        }
    }
}

/**
 * KEYCODE_STYLUS_BUTTON_PRIMARY, _SECONDARY, _TERTIARY and _TAIL (API 34). Literal values so the
 * mapping compiles against older platforms; older devices simply never send them.
 */
private val STYLUS_BUTTON_KEYCODES = setOf(308, 309, 310, 311)

// Room the rulers leave for the toolbar when it's along the top (its 48 dp buttons and padding).
private const val TOOLBAR_CLEARANCE_DP = 64

// Each [ or ] press scales the brush by this, and each zoom shortcut the view (design roadmap D18).
private const val SIZE_STEP = 1.25
private const val ZOOM_STEP = 1.25

// A tool key held this long borrows the tool instead of switching to it (design roadmap D17).
private const val TOOL_KEY_HOLD_MILLIS = 500L

/** How many recent documents the command palette offers (design roadmap D14). */
private const val RECENT_IN_PALETTE = 8
