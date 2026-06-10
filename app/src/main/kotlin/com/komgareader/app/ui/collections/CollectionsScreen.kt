package com.komgareader.app.ui.collections

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.komgareader.app.ui.components.ChoiceRow
import com.komgareader.app.ui.components.CollageTile
import com.komgareader.app.ui.components.EinkInfoDialog
import com.komgareader.app.ui.components.EinkModal
import com.komgareader.app.ui.components.EinkOutlinedButton
import com.komgareader.app.ui.components.EntityListRow
import com.komgareader.app.ui.components.LocalContentBottomInset
import com.komgareader.app.ui.components.TileViewMode
import com.komgareader.app.ui.components.tileViewModeOf
import com.komgareader.app.ui.icons.AppIcons
import com.komgareader.app.ui.theme.EinkTokens
import com.komgareader.domain.model.CollectionKind
import com.komgareader.domain.model.SyncStatus
import com.komgareader.domain.model.UserCollection

/**
 * Sammlungen-Tab. Drei Ansichten über den **rotierenden Button in der Home-TopBar** (Liste · Kachel ·
 * große Kachel, persistiert; Default große Kachel); das „+" (neue Sammlung) liegt ebenfalls oben.
 * Kachel-Mechanik aus [CollageTile], Listen-Zeile aus [EntityListRow] — beide geteilt mit den
 * Bibliotheken (DRY). Jeder Eintrag trägt Bearbeiten (umbenennen) + Löschen und öffnet das Detail.
 */
@Composable
fun CollectionsScreen(
    onOpenCollection: (Long) -> Unit,
    modifier: Modifier = Modifier,
    onNewCollection: () -> Unit = {},
    showCreateDialog: Boolean = false,
    onDismissCreate: () -> Unit = {},
    viewModel: CollectionsViewModel = hiltViewModel(),
) {
    val s = LocalStrings.current
    LaunchedEffect(Unit) { viewModel.syncOnceOnEnter() }
    val collections by viewModel.collections.collectAsState()
    val localOnly by viewModel.localOnly.collectAsState()
    val viewModeStr by viewModel.viewMode.collectAsState()
    val viewMode = tileViewModeOf(viewModeStr, TileViewMode.LARGE_TILE)

    // Sync-Info-Dialog auf Screen-Ebene (von Liste UND Kachel geteilt, statt je Tile dupliziert).
    var syncInfoFor by remember { mutableStateOf<UserCollection?>(null) }
    var renaming by remember { mutableStateOf<UserCollection?>(null) }
    var deleting by remember { mutableStateOf<UserCollection?>(null) }

    if (collections.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                Text(
                    s.collectionsEmpty,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(24.dp))
                EinkOutlinedButton(onClick = onNewCollection, modifier = Modifier.fillMaxWidth(0.7f)) {
                    Text(s.newCollection)
                }
            }
        }
    } else {
        val cols = viewMode.columns
        if (cols == null) {
            LazyColumn(
                modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = EinkTokens.screenPadding,
                    end = EinkTokens.screenPadding,
                    top = EinkTokens.screenPadding,
                    bottom = LocalContentBottomInset.current + EinkTokens.screenPadding,
                ),
                verticalArrangement = Arrangement.spacedBy(EinkTokens.tileGap),
            ) {
                items(collections, key = { it.id }) { collection ->
                    CollectionRow(
                        collection = collection,
                        isLocalOnly = localOnly[collection.id] == true,
                        onClick = { onOpenCollection(collection.id) },
                        onBadge = { syncInfoFor = collection },
                        onEdit = { renaming = collection },
                        onDelete = { deleting = collection },
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(cols),
                modifier = modifier.fillMaxSize().padding(horizontal = EinkTokens.tileGap),
                contentPadding = PaddingValues(top = EinkTokens.tileGap, bottom = LocalContentBottomInset.current + 4.dp),
                horizontalArrangement = Arrangement.spacedBy(EinkTokens.tileGap),
                verticalArrangement = Arrangement.spacedBy(EinkTokens.tileGap),
            ) {
                items(collections, key = { it.id }) { collection ->
                    CollectionGridTile(
                        collection = collection,
                        isLocalOnly = localOnly[collection.id] == true,
                        onClick = { onOpenCollection(collection.id) },
                        onBadge = { syncInfoFor = collection },
                        onEdit = { renaming = collection },
                        onDelete = { deleting = collection },
                    )
                }
            }
        }
    }

    // Neue Sammlung (vom „+" in der TopBar bzw. Empty-State ausgelöst).
    if (showCreateDialog) {
        key(showCreateDialog) {
            var name by remember { mutableStateOf("") }
            var kind by remember { mutableStateOf(CollectionKind.SERIES) }
            EinkModal(
                title = s.newCollection,
                onDismiss = onDismissCreate,
                confirmLabel = s.create,
                onConfirm = {
                    viewModel.create(name.trim(), kind)
                    onDismissCreate()
                },
                dismissLabel = s.cancel,
                confirmEnabled = name.isNotBlank(),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(s.collectionName) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                ChoiceRow(
                    label = s.collectionKindSeries,
                    selected = kind == CollectionKind.SERIES,
                    onSelect = { kind = CollectionKind.SERIES },
                )
                ChoiceRow(
                    label = s.collectionKindBook,
                    selected = kind == CollectionKind.BOOK,
                    onSelect = { kind = CollectionKind.BOOK },
                )
            }
        }
    }

    // Umbenennen.
    renaming?.let { collection ->
        key(collection.id) {
            var name by remember { mutableStateOf(collection.name) }
            EinkModal(
                title = s.editLibrary,
                onDismiss = { renaming = null },
                confirmLabel = s.save,
                onConfirm = {
                    viewModel.rename(collection.id, name.trim())
                    renaming = null
                },
                dismissLabel = s.cancel,
                confirmEnabled = name.isNotBlank(),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(s.collectionName) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    // Löschen (mit optionalem Server-Löschen).
    deleting?.let { collection ->
        key(collection.id) {
            var serverToo by remember { mutableStateOf(false) }
            EinkModal(
                title = s.deleteCollection,
                onDismiss = { deleting = null },
                confirmLabel = s.deleteCollection,
                onConfirm = {
                    viewModel.delete(collection.id, serverToo)
                    deleting = null
                },
                dismissLabel = s.cancel,
            ) {
                Text(collection.name, style = MaterialTheme.typography.bodyLarge)
                ChoiceRow(
                    label = s.deleteCollectionServerToo,
                    selected = serverToo,
                    onSelect = { serverToo = !serverToo },
                )
            }
        }
    }

    // Am Server verschwundene Sammlungen: bestätigen, ob auch lokal gelöscht werden soll.
    val vanished by viewModel.vanished.collectAsState()
    if (vanished.isNotEmpty()) {
        EinkModal(
            title = s.collectionVanishedTitle,
            onDismiss = { viewModel.dismissVanished() },
            confirmLabel = s.collectionVanishedDeleteHere,
            onConfirm = { viewModel.confirmVanishedDelete(vanished.map { it.collectionId }) },
            dismissLabel = s.collectionVanishedKeepHere,
        ) {
            Text(s.collectionVanishedBody, style = MaterialTheme.typography.bodyMedium)
            vanished.forEach { v ->
                Text("• ${v.name}", style = MaterialTheme.typography.bodyLarge)
            }
        }
    }

    syncInfoFor?.let { collection ->
        val links by viewModel.syncLinks(collection.id).collectAsState(emptyList())
        val hasForbidden = links.any { it.status == SyncStatus.FORBIDDEN }
        EinkInfoDialog(
            title = s.collectionSyncInfoTitle,
            onDismiss = { syncInfoFor = null },
            closeLabel = s.close,
        ) {
            Text(
                if (hasForbidden) s.collectionSyncForbidden else s.collectionSyncUnsupported,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/** Untertitel einer Sammlung: „N Serien" bzw. „N Bücher". */
@Composable
private fun collectionSubtitle(collection: UserCollection): String {
    val s = LocalStrings.current
    val kindLabel = when (collection.kind) {
        CollectionKind.SERIES -> s.collectionKindSeries
        CollectionKind.BOOK -> s.collectionKindBook
    }
    return "${collection.members.size} $kindLabel"
}

/** Erste bis zu vier Mitglieder-Cover der Sammlung für die Collage-Kachel. */
private fun collectionCovers(collection: UserCollection): List<SourceCover> =
    collection.members.take(4).map {
        SourceCover(it.sourceId, it.remoteId, isSeries = collection.kind == CollectionKind.SERIES)
    }

/** Sammlungs-Zeile (Listen-Ansicht): geteiltes [EntityListRow], rechts Badge + Bearbeiten + Löschen. */
@Composable
private fun CollectionRow(
    collection: UserCollection,
    isLocalOnly: Boolean,
    onClick: () -> Unit,
    onBadge: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val s = LocalStrings.current
    EntityListRow(
        title = collection.name,
        subtitle = collectionSubtitle(collection),
        onClick = onClick,
        trailing = {
            if (isLocalOnly) {
                LocalOnlyBadge(onClick = onBadge)
                Spacer(Modifier.width(4.dp))
            }
            ActionIcon(AppIcons.Edit, s.editLibrary, onEdit)
            ActionIcon(AppIcons.Delete, s.deleteCollection, onDelete)
        },
    )
}

/** Sammlungs-Kachel (Kachel-Ansicht): geteiltes [CollageTile] + Aktionen (oben rechts), Badge (oben links). */
@Composable
private fun CollectionGridTile(
    collection: UserCollection,
    isLocalOnly: Boolean,
    onClick: () -> Unit,
    onBadge: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val s = LocalStrings.current
    CollageTile(
        covers = collectionCovers(collection),
        name = collection.name,
        onClick = onClick,
        modifier = Modifier.aspectRatio(2f / 3f),
        topStart = { if (isLocalOnly) LocalOnlyBadge(onClick = onBadge) },
        topEnd = { CollectionCornerActions(onEdit = onEdit, onDelete = onDelete, s = s) },
    )
}

/** Aktions-Ecke (Bearbeiten + Löschen), an die Kachelecke geschmiegt — wie bei den Bibliotheken. */
@Composable
private fun CollectionCornerActions(onEdit: () -> Unit, onDelete: () -> Unit, s: com.komgareader.app.i18n.Strings) {
    val shape = RoundedCornerShape(bottomStart = 10.dp)
    Row(
        modifier = Modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(EinkTokens.hairline, MaterialTheme.colorScheme.outline, shape),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ActionIcon(AppIcons.Edit, s.editLibrary, onEdit)
        ActionIcon(AppIcons.Delete, s.deleteCollection, onDelete)
    }
}

/** Einzel-Icon-Button (40 dp) — Kachelecke und Listen-Zeilen-Aktion. */
@Composable
private fun ActionIcon(icon: ImageVector, contentDescription: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(40.dp)) {
        Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurface)
    }
}

/**
 * Badge-Chip „Nur lokal": Icon + Label, Hairline-Border, eigenständiger Click-Bereich (öffnet den
 * Sync-Erklär-Dialog), ohne den umgebenden Click auszulösen.
 */
@Composable
private fun LocalOnlyBadge(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = LocalStrings.current
    val chipShape = RoundedCornerShape(6.dp)
    Row(
        modifier = modifier
            .border(EinkTokens.hairline, MaterialTheme.colorScheme.outline, chipShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = AppIcons.Local,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = s.collectionLocalOnly,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
