package com.komgareader.app.ui.groups

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ImportContacts
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ViewDay
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.komgareader.app.i18n.LocalStrings
import com.komgareader.domain.model.ContentType
import com.komgareader.domain.model.Shelf
import com.komgareader.domain.source.SourceContainer

@Composable
fun GroupsScreen(
    onOpenGroup: (shelfId: Long, serverSourceId: Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GroupsViewModel = hiltViewModel(),
) {
    val s = LocalStrings.current
    val state by viewModel.state.collectAsState()
    val containers by viewModel.containers.collectAsState()
    var editing by remember { mutableStateOf<Shelf?>(null) }
    var showDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        floatingActionButton = {
            FloatingActionButton(onClick = {
                editing = null
                viewModel.loadContainers()
                showDialog = true
            }) {
                Icon(Icons.Filled.Add, contentDescription = s.createLibrary)
            }
        },
    ) { padding ->
        if (state.shelves.isEmpty()) {
            Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    s.noGroupsHint,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(32.dp),
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 8.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.shelves, key = { it.id }) { shelf ->
                    GroupCard(
                        shelf = shelf,
                        onClick = {
                            val sourceId = state.serverSourceId ?: return@GroupCard
                            onOpenGroup(shelf.id, sourceId)
                        },
                        onEdit = {
                            editing = shelf
                            viewModel.loadContainers()
                            showDialog = true
                        },
                        onDelete = { viewModel.deleteGroup(shelf.id) },
                    )
                }
            }
        }
    }

    if (showDialog) {
        LibraryEditDialog(
            existing = editing,
            containers = containers,
            serverName = state.serverConfig?.name,
            onSave = { id, name, containerIds, type ->
                viewModel.saveGroup(id, name, containerIds, type)
                showDialog = false
            },
            onDismiss = { showDialog = false },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GroupCard(
    shelf: Shelf,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val s = LocalStrings.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
            .combinedClickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = iconForContentType(shelf.defaultContentType),
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(shelf.name, style = MaterialTheme.typography.titleSmall)
            Text(
                labelForContentType(shelf.defaultContentType, s),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onEdit) {
            Icon(Icons.Filled.Settings, contentDescription = s.editLibrary)
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = s.deleteGroup)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LibraryEditDialog(
    existing: Shelf?,
    containers: List<SourceContainer>,
    serverName: String?,
    onSave: (id: Long, name: String, containerIds: List<String>, type: ContentType?) -> Unit,
    onDismiss: () -> Unit,
) {
    val s = LocalStrings.current
    var name by rememberSaveable(existing?.id) { mutableStateOf(existing?.name ?: "") }
    val preselected = existing?.sources?.firstOrNull()?.containerIds ?: emptyList()
    val selected = remember(existing?.id) { mutableStateListOf<String>().apply { addAll(preselected) } }
    var selectedType by remember(existing?.id) { mutableStateOf(existing?.defaultContentType) }

    val typeOptions: List<Pair<ContentType?, String>> = listOf(
        null to s.tagAuto,
        ContentType.MANGA to s.tagManga,
        ContentType.COMIC to s.tagComic,
        ContentType.NOVEL to s.tagNovel,
        ContentType.WEBTOON to s.tagWebtoon,
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) s.createLibrary else s.editLibrary) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(s.groupName) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(s.selectLibraries, style = MaterialTheme.typography.labelMedium)
                if (containers.isEmpty()) {
                    Text(
                        text = serverName ?: s.noServerHint,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (serverName != null) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                    )
                } else {
                    Column(Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState())) {
                        containers.forEach { container ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Checkbox(
                                    checked = container.id in selected,
                                    onCheckedChange = { on ->
                                        if (on) selected.add(container.id) else selected.remove(container.id)
                                    },
                                )
                                Text(container.name)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(s.fallbackType, style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    typeOptions.forEach { (type, label) ->
                        FilterChip(
                            selected = selectedType == type,
                            onClick = { selectedType = type },
                            label = { Text(label) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank() && serverName != null) {
                        onSave(existing?.id ?: 0L, name, selected.toList(), selectedType)
                    }
                },
                enabled = name.isNotBlank() && serverName != null,
            ) {
                Text(if (existing == null) s.create else s.save)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(s.cancel)
            }
        },
    )
}

private fun iconForContentType(type: ContentType?) = when (type) {
    ContentType.MANGA -> Icons.Filled.AutoStories
    ContentType.COMIC -> Icons.Filled.ImportContacts
    ContentType.NOVEL -> Icons.Filled.Book
    ContentType.WEBTOON -> Icons.Filled.ViewDay
    null -> Icons.Filled.AutoStories
}

private fun labelForContentType(type: ContentType?, s: com.komgareader.app.i18n.Strings) = when (type) {
    ContentType.MANGA -> s.tagManga
    ContentType.COMIC -> s.tagComic
    ContentType.NOVEL -> s.tagNovel
    ContentType.WEBTOON -> s.tagWebtoon
    null -> s.tagAuto
}
