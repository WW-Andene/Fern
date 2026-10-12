package com.andene.fern

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.andene.fern.canvas.BackgroundStyle
import com.andene.fern.canvas.CanvasState
import com.andene.fern.canvas.CanvasStorage
import com.andene.fern.canvas.DocumentMeta
import com.andene.fern.canvas.DocumentsDialog
import com.andene.fern.canvas.DrawingCanvas
import com.andene.fern.canvas.Frame
import com.andene.fern.canvas.FramesDialog
import com.andene.fern.canvas.Minimap
import com.andene.fern.canvas.PinMeta
import com.andene.fern.canvas.PinsDialog
import com.andene.fern.canvas.PresentationControls
import com.andene.fern.canvas.Toolbar
import com.andene.fern.canvas.VersionsDialog
import com.andene.fern.canvas.WelcomeDialog
import com.andene.fern.canvas.WorldPoint
import com.andene.fern.canvas.exportDocumentBundle
import com.andene.fern.canvas.exportDocumentPdf
import com.andene.fern.canvas.exportDocumentPng
import com.andene.fern.canvas.exportDocumentSvg
import com.andene.fern.canvas.importDocumentBundle
import com.andene.fern.canvas.importPdfPages
import com.andene.fern.canvas.markWelcomeSeen
import com.andene.fern.canvas.shouldShowWelcome
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
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
                                    withContext(Dispatchers.IO) { CanvasStorage.save(context, documentId, snapshot) }
                                }
                            },
                            // Text edits are infrequent (one dialog confirm at a time), so
                            // saved directly rather than debounced like stroke autosave.
                            onTextChanged = { snapshot -> CanvasStorage.saveTextItems(context, currentDocumentId, snapshot) },
                            onImageChanged = { snapshot -> CanvasStorage.saveImageItems(context, currentDocumentId, snapshot) },
                            onBackgroundChanged = { color, style -> CanvasStorage.saveBackground(context, currentDocumentId, color, style) },
                            onSavedColorsChanged = { colors -> CanvasStorage.saveSavedColors(context, colors) },
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
                            for (page in pages) {
                                val heightWorld = widthWorld * page.heightPx / page.widthPx
                                canvasState.addImage(
                                    page.fileName,
                                    WorldPoint(canvasState.panWorld.x - widthWorld / 2.0, top),
                                    widthWorld,
                                    heightWorld,
                                )
                                top += heightWorld + gapWorld
                            }
                        }
                    }

                    val imagePickerLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.PickVisualMedia(),
                    ) { uri ->
                        if (uri == null) return@rememberLauncherForActivityResult
                        coroutineScope.launch {
                            val decoded = withContext(Dispatchers.IO) {
                                val bitmap = decodeDownsampled(context, uri, MAX_IMAGE_EDGE_PX) ?: return@withContext null
                                val fileName = "${UUID.randomUUID()}.png"
                                CanvasStorage.imageFile(context, fileName).outputStream().use { out ->
                                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                                }
                                Triple(fileName, bitmap.width, bitmap.height)
                            } ?: return@launch
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
                            val position = WorldPoint(
                                canvasState.panWorld.x - widthWorld / 2.0,
                                canvasState.panWorld.y - heightWorld / 2.0,
                            )
                            canvasState.addImage(fileName, position, widthWorld, heightWorld)
                        }
                    }

                    suspend fun refreshDocuments() {
                        documents = withContext(Dispatchers.IO) { CanvasStorage.listDocuments(context) }
                    }

                    suspend fun refreshPins() {
                        pins = withContext(Dispatchers.IO) { CanvasStorage.listPins(context, currentDocumentId) }
                    }

                    fun switchToDocument(documentId: String) {
                        autosaveJob?.cancel()
                        CanvasStorage.save(context, currentDocumentId, canvasState.strokes.toList())
                        CanvasStorage.saveTextItems(context, currentDocumentId, canvasState.textItems.toList())
                        CanvasStorage.saveImageItems(context, currentDocumentId, canvasState.imageItems.toList())
                        canvasState.historySnapshot().let { (undo, redo) -> CanvasStorage.saveHistory(context, currentDocumentId, undo, redo) }
                        currentDocumentId = documentId
                        canvasState.loadStrokes(CanvasStorage.load(context, documentId))
                        canvasState.loadLayers(CanvasStorage.loadLayers(context, documentId))
                        canvasState.loadFrames(CanvasStorage.loadFrames(context, documentId))
                        CanvasStorage.loadHistory(context, documentId).let { (undo, redo) -> canvasState.loadHistory(undo, redo) }
                        canvasState.loadTextItems(CanvasStorage.loadTextItems(context, documentId))
                        canvasState.loadImageItems(CanvasStorage.loadImageItems(context, documentId))
                        val (backgroundColor, backgroundStyle) = CanvasStorage.loadBackground(context, documentId)
                        canvasState.loadBackground(backgroundColor, backgroundStyle)
                        pins = CanvasStorage.listPins(context, documentId)
                    }

                    LaunchedEffect(Unit) {
                        canvasState.loadSavedColors(withContext(Dispatchers.IO) { CanvasStorage.loadSavedColors(context) })
                        refreshDocuments()
                        var current = documents.firstOrNull { it.trashedAt == null }
                        if (current == null) {
                            current = withContext(Dispatchers.IO) { CanvasStorage.createDocument(context, "My Canvas") }
                            refreshDocuments()
                        }
                        currentDocumentId = current.id
                        canvasState.loadStrokes(withContext(Dispatchers.IO) { CanvasStorage.load(context, current.id) })
                        canvasState.loadLayers(withContext(Dispatchers.IO) { CanvasStorage.loadLayers(context, current.id) })
                        canvasState.loadFrames(withContext(Dispatchers.IO) { CanvasStorage.loadFrames(context, current.id) })
                        withContext(Dispatchers.IO) { CanvasStorage.loadHistory(context, current.id) }
                            .let { (undo, redo) -> canvasState.loadHistory(undo, redo) }
                        canvasState.loadTextItems(withContext(Dispatchers.IO) { CanvasStorage.loadTextItems(context, current.id) })
                        canvasState.loadImageItems(withContext(Dispatchers.IO) { CanvasStorage.loadImageItems(context, current.id) })
                        val (backgroundColor, backgroundStyle) = withContext(Dispatchers.IO) { CanvasStorage.loadBackground(context, current.id) }
                        canvasState.loadBackground(backgroundColor, backgroundStyle)
                        refreshPins()
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

                    Box(modifier = Modifier.fillMaxSize()) {
                        DrawingCanvas(state = canvasState, modifier = Modifier.fillMaxSize())
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
                                    .align(Alignment.TopCenter)
                                    .padding(WindowInsets.systemBars.asPaddingValues())
                                    .padding(top = 12.dp),
                                onOpenDocuments = { showDocumentsDialog = true },
                                onOpenPins = { showPinsDialog = true },
                                onOpenFrames = { showFramesDialog = true },
                                onOpenVersions = {
                                    coroutineScope.launch {
                                        versions = withContext(Dispatchers.IO) { CanvasStorage.listVersions(context, currentDocumentId) }
                                    }
                                },
                                onExportPng = { pngExportLauncher.launch("${currentDocumentName()}.png") },
                                onExportPdf = { pdfExportLauncher.launch("${currentDocumentName()}.pdf") },
                                onExportSvg = { svgExportLauncher.launch("${currentDocumentName()}.svg") },
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
                            Minimap(
                                state = canvasState,
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(WindowInsets.systemBars.asPaddingValues())
                                    .padding(12.dp),
                            )
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
                                switchToDocument(doc.id)
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
                            onCreateNew = {
                                coroutineScope.launch {
                                    val created = withContext(Dispatchers.IO) {
                                        CanvasStorage.createDocument(context, "Untitled")
                                    }
                                    refreshDocuments()
                                    switchToDocument(created.id)
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
