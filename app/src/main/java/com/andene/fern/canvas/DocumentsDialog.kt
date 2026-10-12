package com.andene.fern.canvas

import com.andene.fern.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** How the document list is ordered. Favourites are always pinned above the rest. */
private enum class DocumentSort(val label: String) {
    RECENT("Recent"),
    NAME("Name"),
}

/**
 * The document library: search, sort, favourites, folders, and a trash view. Deleting moves a document
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
    onMoveToFolder: (DocumentMeta, String?) -> Unit,
    onTrash: (DocumentMeta) -> Unit,
    onRestore: (DocumentMeta) -> Unit,
    onDeleteForever: (DocumentMeta) -> Unit,
    onCreateNew: (DocumentTemplate) -> Unit,
    onImport: () -> Unit,
    onDismiss: () -> Unit,
    loadThumbnail: (DocumentMeta) -> ImageBitmap?,
) {
    var renamingId by remember { mutableStateOf<String?>(null) }
    var renameText by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf(DocumentSort.RECENT) }
    var showingTrash by remember { mutableStateOf(false) }
    var showTemplates by remember { mutableStateOf(false) }
    var openFolder by remember { mutableStateOf<String?>(null) } // null = all documents
    var movingId by remember { mutableStateOf<String?>(null) }
    var moveText by remember { mutableStateOf("") }
    val folders = documents.filter { it.trashedAt == null }.mapNotNull { it.folder }.distinct().sorted()

    val activeCount = documents.count { it.trashedAt == null }
    val visible = documents
        .filter { (it.trashedAt != null) == showingTrash }
        .filter { showingTrash || openFolder == null || it.folder == openFolder }
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
        title = { Text(if (showingTrash) stringResource(R.string.documents_trash) else stringResource(R.string.documents_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.documents_search)) },
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
                        label = { Text(stringResource(R.string.documents_trash)) },
                    )
                }
                if (!showingTrash && folders.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(bottom = 4.dp),
                    ) {
                        FilterChip(
                            selected = openFolder == null,
                            onClick = { openFolder = null },
                            label = { Text(stringResource(R.string.documents_all)) },
                            modifier = Modifier.padding(end = 8.dp),
                        )
                        for (folder in folders) {
                            FilterChip(
                                selected = openFolder == folder,
                                onClick = { openFolder = folder },
                                leadingIcon = { Icon(Icons.Filled.Folder, contentDescription = null) },
                                label = { Text(folder) },
                                modifier = Modifier.padding(end = 8.dp),
                            )
                        }
                    }
                }
                Column(
                    modifier = Modifier
                        .heightIn(max = 384.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    if (visible.isEmpty()) {
                        Text(
                            text = if (showingTrash) stringResource(R.string.documents_trash_empty) else stringResource(R.string.documents_none),
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
                                    Icon(Icons.Filled.Restore, contentDescription = stringResource(R.string.documents_restore))
                                }
                                IconButton(onClick = { onDeleteForever(doc) }) {
                                    Icon(Icons.Filled.DeleteForever, contentDescription = stringResource(R.string.documents_delete_forever))
                                }
                            } else if (movingId == doc.id) {
                                // Empty name moves the document back to the top level.
                                OutlinedTextField(
                                    value = moveText,
                                    onValueChange = { moveText = it },
                                    singleLine = true,
                                    placeholder = { Text(stringResource(R.string.documents_folder_name)) },
                                    modifier = Modifier.weight(1f),
                                )
                                IconButton(onClick = {
                                    onMoveToFolder(doc, moveText.ifBlank { null })
                                    movingId = null
                                }) {
                                    Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.documents_confirm_move))
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
                                    Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.action_confirm_rename))
                                }
                            } else {
                                IconButton(onClick = { onToggleFavorite(doc) }) {
                                    Icon(
                                        if (doc.favorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                                        contentDescription = if (doc.favorite) stringResource(R.string.documents_unfavourite) else stringResource(R.string.documents_favourite),
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
                                    Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.action_rename))
                                }
                                IconButton(onClick = {
                                    movingId = doc.id
                                    moveText = doc.folder ?: openFolder.orEmpty()
                                }) {
                                    Icon(Icons.Filled.DriveFileMove, contentDescription = stringResource(R.string.documents_move_folder))
                                }
                                IconButton(onClick = { onDuplicate(doc) }) {
                                    Icon(Icons.Filled.ContentCopy, contentDescription = stringResource(R.string.action_duplicate))
                                }
                                IconButton(onClick = { onTrash(doc) }, enabled = activeCount > 1) {
                                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.documents_move_trash))
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = onImport) { Text(stringResource(R.string.documents_import)) }
                Box {
                    TextButton(onClick = { showTemplates = true }) { Text(stringResource(R.string.documents_new)) }
                    DropdownMenu(expanded = showTemplates, onDismissRequest = { showTemplates = false }) {
                        for (template in DocumentTemplate.entries) {
                            DropdownMenuItem(
                                text = { Text(template.label) },
                                onClick = {
                                    showTemplates = false
                                    onCreateNew(template)
                                },
                            )
                        }
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
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
