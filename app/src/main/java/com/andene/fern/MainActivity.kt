package com.andene.fern

import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.andene.fern.canvas.BackgroundStyle
import com.andene.fern.canvas.CanvasState
import com.andene.fern.canvas.CanvasStorage
import com.andene.fern.canvas.DocumentMeta
import com.andene.fern.canvas.DocumentsDialog
import com.andene.fern.canvas.DrawingCanvas
import com.andene.fern.canvas.Minimap
import com.andene.fern.canvas.PinMeta
import com.andene.fern.canvas.PinsDialog
import com.andene.fern.canvas.Toolbar
import com.andene.fern.canvas.VersionsDialog
import com.andene.fern.canvas.WorldPoint
import com.andene.fern.canvas.exportDocumentPng
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
                    // Export goes through the system "save as" picker (Storage Access Framework):
                    // works on every API level from minSdk 26 without a storage permission.
                    val exportLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.CreateDocument("image/png"),
                    ) { uri ->
                        if (uri == null) return@rememberLauncherForActivityResult
                        coroutineScope.launch {
                            val png = withContext(Dispatchers.Default) {
                                exportDocumentPng(canvasState, textMeasurer, density) { fileName ->
                                    BitmapFactory.decodeFile(CanvasStorage.imageFile(context, fileName).absolutePath)?.asImageBitmap()
                                }
                            }
                            val message = if (png == null) {
                                "Nothing to export yet"
                            } else {
                                withContext(Dispatchers.IO) {
                                    context.contentResolver.openOutputStream(uri)?.use { it.write(png) }
                                }
                                "Exported"
                            }
                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                        }
                    }

                    val imagePickerLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.PickVisualMedia(),
                    ) { uri ->
                        if (uri == null) return@rememberLauncherForActivityResult
                        coroutineScope.launch {
                            val decoded = withContext(Dispatchers.IO) {
                                val bitmap = context.contentResolver.openInputStream(uri)
                                    ?.use { BitmapFactory.decodeStream(it) }
                                    ?: return@withContext null
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
                        CanvasStorage.loadHistory(context, documentId).let { (undo, redo) -> canvasState.loadHistory(undo, redo) }
                        canvasState.loadTextItems(CanvasStorage.loadTextItems(context, documentId))
                        canvasState.loadImageItems(CanvasStorage.loadImageItems(context, documentId))
                        val (backgroundColor, backgroundStyle) = CanvasStorage.loadBackground(context, documentId)
                        canvasState.loadBackground(backgroundColor, backgroundStyle)
                        pins = CanvasStorage.listPins(context, documentId)
                    }

                    LaunchedEffect(Unit) {
                        refreshDocuments()
                        var current = documents.firstOrNull { it.trashedAt == null }
                        if (current == null) {
                            current = withContext(Dispatchers.IO) { CanvasStorage.createDocument(context, "My Canvas") }
                            refreshDocuments()
                        }
                        currentDocumentId = current.id
                        canvasState.loadStrokes(withContext(Dispatchers.IO) { CanvasStorage.load(context, current.id) })
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
                        Toolbar(
                            state = canvasState,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(WindowInsets.systemBars.asPaddingValues())
                                .padding(top = 12.dp),
                            onOpenDocuments = { showDocumentsDialog = true },
                            onOpenPins = { showPinsDialog = true },
                            onOpenVersions = {
                                coroutineScope.launch {
                                    versions = withContext(Dispatchers.IO) { CanvasStorage.listVersions(context, currentDocumentId) }
                                }
                            },
                            onExportImage = {
                                val name = documents.firstOrNull { it.id == currentDocumentId }?.name ?: "Fern"
                                exportLauncher.launch("$name.png")
                            },
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
