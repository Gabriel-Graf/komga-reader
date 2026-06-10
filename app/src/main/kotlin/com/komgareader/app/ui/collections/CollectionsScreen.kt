package com.komgareader.app.ui.collections

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.komgareader.app.ui.components.ViewModeToggle
import com.komgareader.app.ui.components.tileViewModeOf
import com.komgareader.app.ui.icons.AppIcons
import com.komgareader.app.ui.theme.EinkTokens
import com.komgareader.domain.model.CollectionKind
import com.komgareader.domain.model.SyncStatus
import com.komgareader.domain.model.UserCollection

/**
 * Sammlungen-Tab. Drei Ansichten über den geteilten [ViewModeToggle] (Liste · Kachel · große
 * Kachel, persistiert; Default große Kachel). Kachel-Mechanik aus [CollageTile], Listen-Zeile aus
 * [EntityListRow] — beide geteilt mit den Bibliotheken (DRY). Jeder Eintrag öffnet den Detail-Screen.
 */
@Composable
fun CollectionsScreen(
    onOpenCollection: (Long) -> Unit,
    modifier: Modifier = Modifier,
    onNewCollection: () -> Unit = {},
    viewModel: CollectionsViewModel = hiltViewModel(),
) {
    val s = LocalStrings.current
    val collections by viewModel.collections.collectAsState()
    val localOnly by viewModel.localOnly.collectAsState()
    val viewModeStr by viewModel.viewMode.collectAsState()
    val viewMode = tileViewModeOf(viewModeStr, TileViewMode.LARGE_TILE)

    var showCreate by remember { mutableStateOf(false) }
    // Sync-Info-Dialog auf Screen-Ebene (von Liste UND Kachel geteilt, statt je Tile dupliziert).
    var syncInfoFor by remember { mutableStateOf<UserCollection?>(null) }

    val openCreate = {
        showCreate = true
        onNewCollection()
    }

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
                EinkOutlinedButton(onClick = openCreate, modifier = Modifier.fillMaxWidth(0.7f)) {
                    Text(s.newCollection)
                }
            }
        }
    } else {
        Column(modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = EinkTokens.screenPadding, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                EinkOutlinedButton(onClick = openCreate) {
                    Icon(AppIcons.Plus, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(s.newCollection)
                }
                Spacer(Modifier.weight(1f))
                ViewModeToggle(
                    current = viewMode,
                    onSelect = { viewModel.setViewMode(it.name) },
                    listLabel = s.viewList,
                    tileLabel = s.viewTile,
                    largeTileLabel = s.viewLargeTile,
                )
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
                    items(collections, key = { it.id }) { collection ->
                        CollectionRow(
                            collection = collection,
                            isLocalOnly = localOnly[collection.id] == true,
                            onClick = { onOpenCollection(collection.id) },
                            onBadge = { syncInfoFor = collection },
                        )
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
                    items(collections, key = { it.id }) { collection ->
                        CollectionGridTile(
                            collection = collection,
                            isLocalOnly = localOnly[collection.id] == true,
                            onClick = { onOpenCollection(collection.id) },
                            onBadge = { syncInfoFor = collection },
                        )
                    }
                }
            }
        }
    }

    if (showCreate) {
        key(showCreate) {
            var name by remember { mutableStateOf("") }
            var kind by remember { mutableStateOf(CollectionKind.SERIES) }
            EinkModal(
                title = s.newCollection,
                onDismiss = { showCreate = false },
                confirmLabel = s.create,
                onConfirm = {
                    viewModel.create(name.trim(), kind)
                    showCreate = false
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

    syncInfoFor?.let { collection ->
        val links by viewModel.syncLinks(collection.id).collectAsState(emptyList())
        val hasUnsupported = links.any { it.status == SyncStatus.UNSUPPORTED }
        val hasForbidden = links.any { it.status == SyncStatus.FORBIDDEN }
        EinkInfoDialog(
            title = s.collectionSyncInfoTitle,
            onDismiss = { syncInfoFor = null },
            closeLabel = s.close,
        ) {
            if (hasForbidden) {
                Text(s.collectionSyncForbidden, style = MaterialTheme.typography.bodyMedium)
            }
            if (hasUnsupported || (!hasForbidden && !hasUnsupported)) {
                Text(s.collectionSyncUnsupported, style = MaterialTheme.typography.bodyMedium)
            }
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

/** Sammlungs-Zeile (Listen-Ansicht): geteiltes [EntityListRow], „Nur lokal"-Badge rechts. */
@Composable
private fun CollectionRow(
    collection: UserCollection,
    isLocalOnly: Boolean,
    onClick: () -> Unit,
    onBadge: () -> Unit,
) {
    EntityListRow(
        title = collection.name,
        subtitle = collectionSubtitle(collection),
        onClick = onClick,
        trailing = {
            if (isLocalOnly) {
                Spacer(Modifier.width(8.dp))
                LocalOnlyBadge(onClick = onBadge)
            }
        },
    )
}

/** Sammlungs-Kachel (Kachel-Ansicht): geteiltes [CollageTile] mit Mitglieder-Collage. */
@Composable
private fun CollectionGridTile(
    collection: UserCollection,
    isLocalOnly: Boolean,
    onClick: () -> Unit,
    onBadge: () -> Unit,
) {
    CollageTile(
        covers = collectionCovers(collection),
        name = collection.name,
        onClick = onClick,
        modifier = Modifier.aspectRatio(2f / 3f),
        topStart = { if (isLocalOnly) LocalOnlyBadge(onClick = onBadge) },
    )
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
