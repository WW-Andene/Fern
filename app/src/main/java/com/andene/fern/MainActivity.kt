package com.andene.fern

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.andene.fern.canvas.CanvasState
import com.andene.fern.canvas.CanvasStorage
import com.andene.fern.canvas.DocumentMeta
import com.andene.fern.canvas.DocumentsDialog
import com.andene.fern.canvas.DrawingCanvas
import com.andene.fern.canvas.Minimap
import com.andene.fern.canvas.PinMeta
import com.andene.fern.canvas.PinsDialog
import com.andene.fern.canvas.Toolbar
import com.andene.fern.canvas.WorldPoint
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
            MaterialTheme {
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
                        )
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
                        currentDocumentId = documentId
                        canvasState.loadStrokes(CanvasStorage.load(context, documentId))
                        canvasState.loadTextItems(CanvasStorage.loadTextItems(context, documentId))
                        pins = CanvasStorage.listPins(context, documentId)
                    }

                    LaunchedEffect(Unit) {
                        refreshDocuments()
                        var current = documents.firstOrNull()
                        if (current == null) {
                            current = withContext(Dispatchers.IO) { CanvasStorage.createDocument(context, "My Canvas") }
                            refreshDocuments()
                        }
                        currentDocumentId = current.id
                        canvasState.loadStrokes(withContext(Dispatchers.IO) { CanvasStorage.load(context, current.id) })
                        canvasState.loadTextItems(withContext(Dispatchers.IO) { CanvasStorage.loadTextItems(context, current.id) })
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
                            onDelete = { doc ->
                                val wasCurrent = doc.id == currentDocumentId
                                CanvasStorage.deleteDocument(context, doc.id)
                                coroutineScope.launch {
                                    refreshDocuments()
                                    if (wasCurrent) {
                                        documents.firstOrNull()?.let { switchToDocument(it.id) }
                                    }
                                }
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
