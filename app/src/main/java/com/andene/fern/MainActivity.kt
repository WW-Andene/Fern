@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.andene.fern

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
import android.widget.Toast
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import com.andene.fern.canvas.AppSettings
import com.andene.fern.canvas.AudioNotes
import com.andene.fern.canvas.AudioNotesDialog
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
import com.andene.fern.canvas.QuickMenuButton
import com.andene.fern.canvas.QuickMenuOverlay
import com.andene.fern.canvas.PinMeta
import com.andene.fern.canvas.PinsDialog
import com.andene.fern.canvas.PresentationControls
import com.andene.fern.canvas.SettingsDialog
import com.andene.fern.canvas.Tool
import com.andene.fern.canvas.Toolbar
import com.andene.fern.canvas.VersionsDialog
import com.andene.fern.canvas.WelcomeDialog
import com.andene.fern.canvas.WorldPoint
import com.andene.fern.canvas.exportAnimatedGif
import com.andene.fern.canvas.exportReplayGif
import com.andene.fern.canvas.exportDocumentBundle
import com.andene.fern.canvas.exportDocumentPdf
import com.andene.fern.canvas.exportDocumentPng
import com.andene.fern.canvas.exportDocumentSvg
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

/**
 * Decodes the image at [uri] no larger than [maxEdgePx] on its longest edge: first reads only
 * the size, decodes with the largest power-of-two subsampling that stays at or above the
 * target (cheap, done by the decoder), then scales the rest of the way exactly.
 */
private fun decodeDownsampled(context: android.content.Context, uri: Uri, maxEdgePx: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    val longest = maxOf(bounds.outWidth, bounds.outHeight)
    if (longest <= 0) return null
    var sample = 1
    while (longest / (sample * 2) >= maxEdgePx) sample *= 2
    val decoded = context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
    } ?: return null
    val decodedLongest = maxOf(decoded.width, decoded.height)
    if (decodedLongest <= maxEdgePx) return decoded
    val factor = maxEdgePx.toFloat() / decodedLongest
    return Bitmap.createScaledBitmap(decoded, (decoded.width * factor).toInt(), (decoded.height * factor).toInt(), true)
        .also { if (it !== decoded) decoded.recycle() }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashReporter.install(this)
        enableEdgeToEdge()
        setContent {
            // Follows the system light/dark setting for the app's own UI (toolbar, dialogs). The
            // canvas paper keeps each document's chosen background colour either way.
            var highContrast by remember { mutableStateOf(AppSettings.highContrast(this)) }
            FernTheme(highContrast = highContrast) {
                Surface(color = Color(0xFFF7F5F0)) {
                    val context = LocalContext.current
                    val lifecycleOwner = LocalLifecycleOwner.current
                    val coroutineScope = rememberCoroutineScope()
                    var autosaveJob by remember { mutableStateOf<Job?>(null) }

                    var currentDocumentId by remember { mutableStateOf("") }
                    var documents by remember { mutableStateOf(emptyList<DocumentMeta>()) }
                    var showDocumentsDialog by remember { mutableStateOf(false) }
                    var pins by remember { mutableStateOf(emptyList<PinMeta>()) }
                    var showPinsDialog by remember { mutableStateOf(false) }
                    var showFramesDialog by remember { mutableStateOf(false) }
                    var showWelcome by remember { mutableStateOf(shouldShowWelcome(context)) }
                    var showSettings by remember { mutableStateOf(false) }
                    var showQuickMenu by remember { mutableStateOf(false) }
                    // True while a document is being read: the canvas is covered so nothing can be
                    // drawn onto (and then autosaved over) a document that isn't loaded yet.
                    var loadingDocument by remember { mutableStateOf(true) }
                    val switchMutex = remember { Mutex() }
                    var showAudioNotes by remember { mutableStateOf(false) }
                    val audioNotes = remember { AudioNotes(context) }
                    val micPermissionLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.RequestPermission(),
                    ) { granted ->
                        if (granted) showAudioNotes = true
                        else Toast.makeText(context, "Voice notes need microphone access", Toast.LENGTH_SHORT).show()
                    }
                    var leftHanded by remember { mutableStateOf(AppSettings.leftHanded(context)) }
                    var toolbarAtBottom by remember { mutableStateOf(AppSettings.toolbarAtBottom(context)) }
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
                                    delay(500)
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
                            onFramesChanged = { frames -> CanvasStorage.saveFrames(context, currentDocumentId, frames) },
                            onLayersChanged = { layers -> CanvasStorage.saveLayers(context, currentDocumentId, layers) },
                            onRebased = { offset ->
                                if (pins.isNotEmpty()) {
                                    pins = pins.map { it.copy(x = it.x - offset.x, y = it.y - offset.y) }
                                    CanvasStorage.savePins(context, currentDocumentId, pins)
                                }
                            },
                        )
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
                                "Nothing to export yet"
                            } else {
                                withContext(Dispatchers.IO) {
                                    context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                                }
                                "Exported"
                            }
                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
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
                    val frameExportLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.CreateDocument("image/png"),
                    ) { uri ->
                        val frame = frameToExport
                        frameToExport = null
                        if (uri != null && frame != null) {
                            writeExport(uri) { exportDocumentPng(canvasState, textMeasurer, density, imageForExport, frame) }
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
                    val replayGifExportLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.CreateDocument("image/gif"),
                    ) { uri ->
                        if (uri != null) writeExport(uri) { exportReplayGif(canvasState, textMeasurer, density, imageForExport) }
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

                    val pdfImportLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.OpenDocument(),
                    ) { uri ->
                        if (uri == null) return@rememberLauncherForActivityResult
                        coroutineScope.launch {
                            val pages = withContext(Dispatchers.IO) { importPdfPages(context, uri) }
                            if (pages.isEmpty()) {
                                Toast.makeText(context, "Couldn't open that PDF", Toast.LENGTH_SHORT).show()
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
                            Toast.makeText(
                                context,
                                if (colors.isEmpty()) "That isn't a GIMP (.gpl) or Adobe swatch (.ase) palette" else "Imported ${colors.size} colours",
                                Toast.LENGTH_SHORT,
                            ).show()
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
                                Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                            }
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
                                ClipboardContent.Nothing -> Toast.makeText(context, "Nothing to paste", Toast.LENGTH_SHORT).show()
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
                        val loaded = withContext(Dispatchers.IO) {
                            LoadedDocument(
                                strokes = CanvasStorage.load(context, documentId),
                                layers = CanvasStorage.loadLayers(context, documentId),
                                frames = CanvasStorage.loadFrames(context, documentId),
                                history = CanvasStorage.loadHistory(context, documentId),
                                texts = CanvasStorage.loadTextItems(context, documentId),
                                connectors = CanvasStorage.loadConnectors(context, documentId),
                                images = CanvasStorage.loadImageItems(context, documentId),
                                background = CanvasStorage.loadBackground(context, documentId),
                                pins = CanvasStorage.listPins(context, documentId),
                            )
                        }
                        canvasState.loadStrokes(loaded.strokes)
                        canvasState.loadLayers(loaded.layers)
                        canvasState.loadFrames(loaded.frames)
                        canvasState.loadHistory(loaded.history.first, loaded.history.second)
                        canvasState.loadTextItems(loaded.texts)
                        canvasState.loadConnectors(loaded.connectors)
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
                        loadingDocument = true
                        try {
                            val leaving = currentDocumentId
                            val strokes = canvasState.strokes.toList()
                            val texts = canvasState.textItems.toList()
                            val images = canvasState.imageItems.toList()
                            val (undo, redo) = canvasState.historySnapshot()
                            withContext(Dispatchers.IO) {
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
                            current = withContext(Dispatchers.IO) { CanvasStorage.createDocument(context, "My Canvas") }
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
                                CanvasStorage.save(context, currentDocumentId, canvasState.strokes.toList())
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
                    LaunchedEffect(canvasState.playing, canvasState.animFps) {
                        while (canvasState.playing) {
                            delay(1000L / canvasState.animFps)
                            canvasState.advanceAnimFrame()
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
                                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                val ctrl = event.isCtrlPressed || event.isMetaPressed
                                when {
                                    ctrl && event.key == Key.Z && event.isShiftPressed -> canvasState.redo()
                                    ctrl && event.key == Key.Z -> canvasState.undo()
                                    ctrl && event.key == Key.Y -> canvasState.redo()
                                    ctrl && event.key == Key.C -> copySelectionToClipboard()
                                    ctrl && event.key == Key.V -> pasteFromClipboard()
                                    ctrl && event.key == Key.D -> canvasState.duplicateSelection()
                                    event.key == Key.Delete || event.key == Key.Backspace -> canvasState.deleteSelection()
                                    event.key == Key.P -> canvasState.activeTool = Tool.PEN
                                    event.key == Key.E -> canvasState.activeTool = Tool.ERASER
                                    event.key == Key.V -> canvasState.activeTool = Tool.SELECT
                                    event.key == Key.Zero -> canvasState.zoomToFit()
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
                            onFollowLink = { target ->
                                when (target) {
                                    is LinkTarget.Pin -> {
                                        val pin = pins.firstOrNull { it.id == target.pinId }
                                        if (pin != null) canvasState.jumpTo(WorldPoint(pin.x, pin.y), pin.scale)
                                        else Toast.makeText(context, "That pin no longer exists", Toast.LENGTH_SHORT).show()
                                    }
                                    is LinkTarget.Document -> {
                                        val document = documents.firstOrNull { it.id == target.documentId && it.trashedAt == null }
                                        if (document != null) coroutineScope.launch { switchToDocument(document.id) }
                                        else Toast.makeText(context, "That document no longer exists", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                        )
                        val presenting = presentingIndex
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
                                    .padding(16.dp),
                            )
                        } else {
                            Toolbar(
                                state = canvasState,
                                modifier = Modifier
                                    .align(if (toolbarAtBottom) Alignment.BottomCenter else Alignment.TopCenter)
                                    .padding(WindowInsets.systemBars.asPaddingValues())
                                    .padding(vertical = 12.dp),
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
                                onExportSelection = { selectionExportLauncher.launch("${currentDocumentName()} selection.png") },
                                onExportTransparentPng = { transparentPngExportLauncher.launch("${currentDocumentName()}.png") },
                                onExportPdf = { pdfExportLauncher.launch("${currentDocumentName()}.pdf") },
                                onExportSvg = { svgExportLauncher.launch("${currentDocumentName()}.svg") },
                                onExportGif = { gifExportLauncher.launch("${currentDocumentName()}.gif") },
                                onExportReplayGif = { replayGifExportLauncher.launch("${currentDocumentName()} time-lapse.gif") },
                                onExportSpriteSheet = { spriteSheetExportLauncher.launch("${currentDocumentName()} sprites.png") },
                                onExportBundle = { bundleExportLauncher.launch("${currentDocumentName()}.fern") },
                                onShare = {
                                    coroutineScope.launch {
                                        val png = withContext(Dispatchers.Default) {
                                            exportDocumentPng(canvasState, textMeasurer, density, imageForExport)
                                        }
                                        if (png == null) {
                                            Toast.makeText(context, "Nothing to share yet", Toast.LENGTH_SHORT).show()
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
                                        .padding(bottom = 16.dp),
                                )
                            }
                            Minimap(
                                state = canvasState,
                                modifier = Modifier
                                    // Opposite the drawing hand, whose palm rests below and beside the pen.
                                    .align(if (leftHanded) Alignment.BottomEnd else Alignment.BottomStart)
                                    .padding(WindowInsets.systemBars.asPaddingValues())
                                    .padding(12.dp),
                            )
                            QuickMenuButton(
                                onOpen = { showQuickMenu = true },
                                modifier = Modifier
                                    // Same side as the minimap, mid-height: in reach of the free hand's thumb.
                                    .align(if (leftHanded) Alignment.CenterEnd else Alignment.CenterStart)
                                    .padding(WindowInsets.systemBars.asPaddingValues())
                                    .padding(12.dp),
                            )
                            if (showQuickMenu) QuickMenuOverlay(state = canvasState, onDismiss = { showQuickMenu = false })
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
                                Toast.makeText(context, "That isn't a Fern backup", Toast.LENGTH_SHORT).show()
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
                                CanvasStorage.duplicateDocument(context, doc.id, "${doc.name} copy")
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
                            title = { Text("Fern closed unexpectedly") },
                            text = { Text("A crash report was saved on this device. Share it to help fix the problem? It contains technical details only, no drawings.") },
                            confirmButton = {
                                TextButton(onClick = {
                                    val send = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_SUBJECT, "Fern crash report")
                                        putExtra(Intent.EXTRA_TEXT, report)
                                    }
                                    context.startActivity(Intent.createChooser(send, null))
                                    CrashReporter.clearPendingReport(context)
                                    crashReport = null
                                }) { Text("Share report") }
                            },
                            dismissButton = {
                                TextButton(onClick = {
                                    CrashReporter.clearPendingReport(context)
                                    crashReport = null
                                }) { Text("Dismiss") }
                            },
                        )
                    }

                    if (showAudioNotes) {
                        AudioNotesDialog(audio = audioNotes, documentId = currentDocumentId, onDismiss = { showAudioNotes = false })
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
                            snapToObjects = canvasState.snapToObjects,
                            onSnapToObjectsChange = { value ->
                                canvasState.snapToObjects = value
                                AppSettings.setSnapToObjects(context, value)
                            },
                            onShowGuide = {
                                showSettings = false
                                showWelcome = true
                            },
                            onDismiss = { showSettings = false },
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
                            onDelete = { frame -> canvasState.deleteFrame(frame.id) },
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
