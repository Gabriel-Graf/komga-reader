package com.komgareader.app.ui.groups

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.komgareader.app.data.coil.SourceCover
import com.komgareader.app.i18n.LocalStrings
import com.komgareader.app.i18n.Strings
import com.komgareader.app.ui.components.ChoiceRow
import com.komgareader.app.ui.components.CollageTile
import com.komgareader.app.ui.components.EinkModal
import com.komgareader.app.ui.components.EntityListRow
import com.komgareader.app.ui.components.FieldCaption
import com.komgareader.app.ui.components.LocalContentBottomInset
import com.komgareader.app.ui.components.TileViewMode
import com.komgareader.app.ui.components.tileViewModeOf
import com.komgareader.ui.icons.AppIcons
import com.komgareader.ui.theme.EinkTokens
import com.komgareader.domain.model.ContentType
import com.komgareader.domain.model.Shelf
import com.komgareader.domain.repository.ServerConfig
import com.komgareader.domain.source.SourceContainer

/**
 * Bibliotheken-Tab. Drei Ansichten über den geteilten [ViewModeToggle] (Liste · Kachel · große
 * Kachel, persistiert; Default Liste). Die Kachel-Mechanik kommt aus dem geteilten [CollageTile],
 * die Listen-Zeile aus [EntityListRow] (DRY mit den Sammlungen). Erstellen wird extern über
 * [showCreateDialog] (FAB in der Home-TopBar) ausgelöst; Bearbeiten je Eintrag.
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
    val viewModeStr by viewModel.viewMode.collectAsState()
    val viewMode = tileViewModeOf(viewModeStr, TileViewMode.LIST)
    var editing by remember { mutableStateOf<Shelf?>(null) }

    val dialogOpen = showCreateDialog || editing != null

    LaunchedEffect(dialogOpen) {
        if (dialogOpen) viewModel.loadContainers()
    }
    // Collage-Cover (erste 4 Titel je Bibliothek) nur für die Kachel-Ansichten laden.
    LaunchedEffect(state.shelves, viewMode) {
        if (state.shelves.isNotEmpty() && viewMode != TileViewMode.LIST) viewModel.loadCovers()
    }

    if (state.shelves.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(s.noGroupsHint, textAlign = TextAlign.Center, modifier = Modifier.padding(32.dp))
        }
    } else {
        Column(modifier.fillMaxSize()) {
            // Ansichts-Umschalter liegt in der Home-TopBar (rotierender Button rechts).
            val openGroup: (Shelf) -> Unit = { shelf ->
                state.serverSourceId?.let { onOpenGroup(shelf.id, it) }
            }
            val cols = viewMode.columns
            if (cols == null) {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = EinkTokens.screenPadding,
                        end = EinkTokens.screenPadding,
                        top = 4.dp,
                        bottom = LocalContentBottomInset.current + 4.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(EinkTokens.tileGap),
                ) {
                    items(state.shelves, key = { it.id }) { shelf ->
                        GroupListRow(shelf, s, { openGroup(shelf) }, { editing = shelf }, { viewModel.deleteGroup(shelf.id) })
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(cols),
                    modifier = Modifier.fillMaxSize().padding(horizontal = EinkTokens.tileGap),
                    contentPadding = PaddingValues(top = 4.dp, bottom = LocalContentBottomInset.current + 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(EinkTokens.tileGap),
                    verticalArrangement = Arrangement.spacedBy(EinkTokens.tileGap),
                ) {
                    items(state.shelves, key = { it.id }) { shelf ->
                        GroupTile(shelf, covers[shelf.id] ?: emptyList(), { openGroup(shelf) }, { editing = shelf }, { viewModel.deleteGroup(shelf.id) })
                    }
                }
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

/** Bibliotheks-Kachel: geteiltes [CollageTile] mit Typ-Chip (oben links) + Aktionen (oben rechts). */
@Composable
private fun GroupTile(
    shelf: Shelf,
    covers: List<SourceCover>,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val s = LocalStrings.current
    CollageTile(
        covers = covers,
        name = shelf.name,
        onClick = onClick,
        modifier = Modifier.aspectRatio(2f / 3f),
        topStart = { shelf.defaultContentType?.let { TypeChip(labelForContentType(it, s)) } },
        topEnd = { CornerActions(onEdit = onEdit, onDelete = onDelete) },
    )
}

/** Bibliotheks-Zeile (Listen-Ansicht): geteiltes [EntityListRow], Aktionen rechts. */
@Composable
private fun GroupListRow(
    shelf: Shelf,
    s: Strings,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    EntityListRow(
        title = shelf.name,
        subtitle = shelf.defaultContentType?.let { labelForContentType(it, s) },
        onClick = onClick,
        trailing = {
            CornerAction(AppIcons.Edit, s.editLibrary, onEdit)
            CornerAction(AppIcons.Delete, s.deleteGroup, onDelete)
        },
    )
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
            .border(EinkTokens.hairline, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/**
 * Aktions-Ecke oben rechts: flache, opake Leiste mit Einstellungen + Löschen, die sich an die
 * Kachelecke schmiegt (nur innere Ecke gerundet) — E-Ink-flach, Hairline-Rand.
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
            .border(EinkTokens.hairline, MaterialTheme.colorScheme.outline, shape),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CornerAction(AppIcons.Edit, s.editLibrary, onEdit)
        CornerAction(AppIcons.Delete, s.deleteGroup, onDelete)
    }
}

/** Einzel-Icon-Button (40 dp) — in der Kachelecke und als Listen-Zeilen-Aktion wiederverwendet. */
@Composable
private fun CornerAction(
    icon: ImageVector,
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
        FieldCaption(s.selectLibraries)
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
            // Mehrfachauswahl als ChoiceRow-Liste (Häkchen = gewählt) — kein nacktes Material-Checkbox.
            Column(Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState())) {
                containers.forEach { container ->
                    ChoiceRow(
                        label = container.name,
                        selected = container.id in selected,
                        dense = true,
                        onSelect = {
                            if (container.id in selected) selected.remove(container.id)
                            else selected.add(container.id)
                        },
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        // Fallback-Typ: Einfachauswahl als ChoiceRow-Liste — fünf Optionen wären als Segmente
        // auf E-Ink zu eng; vertikal ist konsistent mit der Bibliotheks-Liste darüber.
        FieldCaption(s.fallbackType)
        typeOptions.forEach { (type, label) ->
            ChoiceRow(
                label = label,
                selected = selectedType == type,
                dense = true,
                onSelect = { selectedType = type },
            )
        }
    }
}

private fun labelForContentType(type: ContentType, s: Strings) = when (type) {
    ContentType.MANGA -> s.tagManga
    ContentType.COMIC -> s.tagComic
    ContentType.NOVEL -> s.tagNovel
    ContentType.WEBTOON -> s.tagWebtoon
}
