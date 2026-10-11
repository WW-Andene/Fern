package com.andene.fern.canvas

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** How the document list is ordered. Favourites are always pinned above the rest. */
private enum class DocumentSort(val label: String) {
    RECENT("Recent"),
    NAME("Name"),
}

/**
 * The document library: search, sort, favourites, and a trash view. Deleting moves a document
 * to the trash (restorable for 30 days, see [CanvasStorage.moveToTrash]); only the trash view
 * deletes permanently. The last non-trashed document can't be trashed, since a canvas with
 * zero open-able documents isn't a valid state.
 */
@Composable
fun DocumentsDialog(
    documents: List<DocumentMeta>,
    currentDocumentId: String,
    onSelect: (DocumentMeta) -> Unit,
    onRename: (DocumentMeta, String) -> Unit,
    onDuplicate: (DocumentMeta) -> Unit,
    onToggleFavorite: (DocumentMeta) -> Unit,
    onTrash: (DocumentMeta) -> Unit,
    onRestore: (DocumentMeta) -> Unit,
    onDeleteForever: (DocumentMeta) -> Unit,
    onCreateNew: () -> Unit,
    onDismiss: () -> Unit,
    loadThumbnail: (DocumentMeta) -> ImageBitmap?,
) {
    var renamingId by remember { mutableStateOf<String?>(null) }
    var renameText by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf(DocumentSort.RECENT) }
    var showingTrash by remember { mutableStateOf(false) }

    val activeCount = documents.count { it.trashedAt == null }
    val visible = documents
        .filter { (it.trashedAt != null) == showingTrash }
        .filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }
        .let { list ->
            when (sort) {
                DocumentSort.RECENT -> list.sortedByDescending { it.lastModified }
                DocumentSort.NAME -> list.sortedBy { it.name.lowercase() }
            }
        }
        .sortedByDescending { it.favorite } // stable sort: keeps the order above within each group

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (showingTrash) "Trash" else "Documents") },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    placeholder = { Text("Search") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    for (option in DocumentSort.entries) {
                        FilterChip(
                            selected = sort == option,
                            onClick = { sort = option },
                            label = { Text(option.label) },
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    }
                    FilterChip(
                        selected = showingTrash,
                        onClick = { showingTrash = !showingTrash },
                        label = { Text("Trash") },
                    )
                }
                Column(
                    modifier = Modifier
                        .heightIn(max = 384.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    if (visible.isEmpty()) {
                        Text(
                            text = if (showingTrash) "Trash is empty" else "No documents found",
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }
                    for (doc in visible) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (showingTrash) {
                                Text(text = doc.name, modifier = Modifier.weight(1f))
                                IconButton(onClick = { onRestore(doc) }) {
                                    Icon(Icons.Filled.Restore, contentDescription = "Restore")
                                }
                                IconButton(onClick = { onDeleteForever(doc) }) {
                                    Icon(Icons.Filled.DeleteForever, contentDescription = "Delete forever")
                                }
                            } else if (renamingId == doc.id) {
                                OutlinedTextField(
                                    value = renameText,
                                    onValueChange = { renameText = it },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                )
                                IconButton(onClick = {
                                    onRename(doc, renameText)
                                    renamingId = null
                                }) {
                                    Icon(Icons.Filled.Check, contentDescription = "Confirm rename")
                                }
                            } else {
                                IconButton(onClick = { onToggleFavorite(doc) }) {
                                    Icon(
                                        if (doc.favorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                                        contentDescription = if (doc.favorite) "Remove from favourites" else "Add to favourites",
                                    )
                                }
                                DocumentThumbnailImage(doc, loadThumbnail)
                                Text(
                                    text = doc.name,
                                    modifier = Modifier
                                        .padding(start = 8.dp)
                                        .weight(1f)
                                        .clickable { onSelect(doc) },
                                    fontWeight = if (doc.id == currentDocumentId) FontWeight.Bold else FontWeight.Normal,
                                )
                                IconButton(onClick = {
                                    renamingId = doc.id
                                    renameText = doc.name
                                }) {
                                    Icon(Icons.Filled.Edit, contentDescription = "Rename")
                                }
                                IconButton(onClick = { onDuplicate(doc) }) {
                                    Icon(Icons.Filled.ContentCopy, contentDescription = "Duplicate")
                                }
                                IconButton(onClick = { onTrash(doc) }, enabled = activeCount > 1) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Move to trash")
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onCreateNew) { Text("New document") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}

/** A small preview of [doc]; a blank tile when it has nothing drawn yet. */
@Composable
private fun DocumentThumbnailImage(doc: DocumentMeta, loadThumbnail: (DocumentMeta) -> ImageBitmap?) {
    // Keyed on lastModified so the preview refreshes after the document is saved again.
    val thumbnail = remember(doc.id, doc.lastModified) { loadThumbnail(doc) }
    val tileModifier = Modifier
        .size(48.dp)
        .clip(RoundedCornerShape(12.dp)) // §7.3: 0.24 × 48 = 11.5, nearest scale value 12
        .background(Color(0xFFF7F5F0))
    if (thumbnail != null) {
        Image(bitmap = thumbnail, contentDescription = null, modifier = tileModifier)
    } else {
        Box(modifier = tileModifier)
    }
}
