package com.komgareader.app.ui.groups

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.komgareader.app.data.AuthHeaders
import com.komgareader.app.i18n.LocalStrings
import com.komgareader.app.ui.components.EinkModal
import com.komgareader.domain.model.ContentType
import com.komgareader.domain.model.Shelf
import com.komgareader.domain.repository.ServerConfig
import com.komgareader.domain.source.SourceContainer

/**
 * Bibliotheken-Tab. Das Erstellen wird extern über [showCreateDialog] (FAB in der
 * Home-TopBar) ausgelöst; das Bearbeiten intern über das Settings-Icon je Karte.
 * Der Dialog wählt Komga-Libraries aus und setzt einen optionalen Fallback-Typ.
 */
@Composable
fun GroupsScreen(
    onOpenGroup: (shelfId: Long, serverSourceId: Long) -> Unit,
    showCreateDialog: Boolean,
    onDismissCreate: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GroupsViewModel = hiltViewModel(),
) {
    val s = LocalStrings.current
    val state by viewModel.state.collectAsState()
    val containers by viewModel.containers.collectAsState()
    val covers by viewModel.covers.collectAsState()
    var editing by remember { mutableStateOf<Shelf?>(null) }

    val dialogOpen = showCreateDialog || editing != null

    // Library-Liste laden, sobald der Dialog (Erstellen oder Bearbeiten) öffnet.
    LaunchedEffect(dialogOpen) {
        if (dialogOpen) viewModel.loadContainers()
    }

    // Collage-Cover (erste 4 Titel je Bibliothek) laden, sobald sich die Liste ändert.
    LaunchedEffect(state.shelves) {
        if (state.shelves.isNotEmpty()) viewModel.loadCovers()
    }

    if (state.shelves.isEmpty()) {
        Box(
            modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                s.noGroupsHint,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(32.dp),
            )
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.shelves, key = { it.id }) { shelf ->
                GroupTile(
                    shelf = shelf,
                    coverUrls = covers[shelf.id] ?: emptyList(),
                    serverConfig = state.serverConfig,
                    onClick = {
                        val sourceId = state.serverSourceId ?: return@GroupTile
                        onOpenGroup(shelf.id, sourceId)
                    },
                    onEdit = { editing = shelf },
                    onDelete = { viewModel.deleteGroup(shelf.id) },
                )
            }
        }
    }

    if (dialogOpen) {
        val dismiss = {
            editing = null
            onDismissCreate()
        }
        LibraryEditDialog(
            existing = editing,
            containers = containers,
            serverName = state.serverConfig?.name,
            onSave = { id, name, containerIds, type ->
                viewModel.saveGroup(id, name, containerIds, type)
                dismiss()
            },
            onDismiss = dismiss,
        )
    }
}

/**
 * Bibliotheks-Kachel im Grid: 2×2-Collage aus den ersten vier Titel-Covern, darüber als
 * Overlay die Aktionen (Einstellungen, Löschen) oben rechts und der Name unten.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GroupTile(
    shelf: Shelf,
    coverUrls: List<String?>,
    serverConfig: ServerConfig?,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val s = LocalStrings.current
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(2f / 3f)
            .border(1.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp))
            .combinedClickable(onClick = onClick),
    ) {
        CompositeCover(coverUrls = coverUrls, serverConfig = serverConfig)

        shelf.defaultContentType?.let { type ->
            TypeChip(
                label = labelForContentType(type, s),
                modifier = Modifier.align(Alignment.TopStart).padding(4.dp),
            )
        }

        CornerActions(
            modifier = Modifier.align(Alignment.TopEnd),
            onEdit = onEdit,
            onDelete = onDelete,
        )

        Text(
            shelf.name,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelLarge,
            color = Color.White,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.7f))
                .padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

/** Inhaltstyp-Chip oben links auf der Collage; opak für Kontrast über Covern. */
@Composable
private fun TypeChip(label: String, modifier: Modifier = Modifier) {
    Text(
        label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(6.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/**
 * Aktions-Ecke oben rechts: eine flache, opake Leiste mit Einstellungen + Löschen, die
 * sich an die Kachelecke schmiegt (nur innere Ecke gerundet) — E-Ink-flach, Hairline-Rand.
 */
@Composable
private fun CornerActions(
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = LocalStrings.current
    val shape = RoundedCornerShape(bottomStart = 10.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.5.dp, MaterialTheme.colorScheme.outline, shape),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CornerAction(Icons.Filled.Settings, s.editLibrary, onEdit)
        CornerAction(Icons.Filled.Delete, s.deleteGroup, onDelete)
    }
}

/** Einzel-Icon der Aktions-Ecke ohne eigenen Hintergrund. */
@Composable
private fun CornerAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, modifier = Modifier.size(40.dp)) {
        Icon(
            icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * 2×2-Collage der ersten vier Cover. Fehlende Slots bleiben leer (neutrale Fläche) —
 * bei 1–3 Titeln wird stets das Vierer-Raster gezeigt.
 */
@Composable
private fun CompositeCover(
    coverUrls: List<String?>,
    serverConfig: ServerConfig?,
) {
    Column(Modifier.fillMaxSize()) {
        repeat(2) { row ->
            Row(Modifier.fillMaxWidth().weight(1f)) {
                repeat(2) { col ->
                    val url = coverUrls.getOrNull(row * 2 + col)
                    CoverSlot(
                        url = url,
                        serverConfig = serverConfig,
                        modifier = Modifier.fillMaxSize().weight(1f),
                    )
                }
            }
        }
    }
}

/** Einzelne Cover-Zelle der Collage; ohne URL eine ruhige Platzhalter-Fläche. */
@Composable
private fun CoverSlot(
    url: String?,
    serverConfig: ServerConfig?,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
        if (url != null) {
            val request = remember(url, serverConfig) {
                val headers = AuthHeaders.forCovers(serverConfig)
                ImageRequest.Builder(ctx).data(url)
                    .apply { headers.forEach { addHeader(it.key, it.value) } }
                    .crossfade(false).build()
            }
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
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

    EinkModal(
        title = if (existing == null) s.createLibrary else s.editLibrary,
        onDismiss = onDismiss,
        confirmLabel = if (existing == null) s.create else s.save,
        onConfirm = {
            if (name.isNotBlank() && serverName != null) {
                onSave(existing?.id ?: 0L, name, selected.toList(), selectedType)
            }
        },
        confirmEnabled = name.isNotBlank() && serverName != null,
        dismissLabel = s.cancel,
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
}

private fun labelForContentType(type: ContentType, s: com.komgareader.app.i18n.Strings) = when (type) {
    ContentType.MANGA -> s.tagManga
    ContentType.COMIC -> s.tagComic
    ContentType.NOVEL -> s.tagNovel
    ContentType.WEBTOON -> s.tagWebtoon
}
