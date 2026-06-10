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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.request.ImageRequest
import com.komgareader.app.data.coil.SourceCover
import com.komgareader.app.i18n.LocalStrings
import com.komgareader.app.ui.components.AnchoredMenuPopup
import com.komgareader.app.ui.components.ChoiceRow
import com.komgareader.app.ui.components.EinkModal
import com.komgareader.app.ui.components.EinkOutlinedButton
import com.komgareader.app.ui.components.FilteredAsyncImage
import com.komgareader.app.ui.components.LocalContentBottomInset
import com.komgareader.app.ui.components.TileTitleBand
import com.komgareader.app.ui.icons.AppIcons
import com.komgareader.app.ui.library.LibraryUiState
import com.komgareader.app.ui.library.LibraryViewModel
import com.komgareader.app.ui.theme.EinkTokens
import com.komgareader.app.ui.theme.LocalDesignTokens
import com.komgareader.domain.model.CollectionKind
import com.komgareader.domain.model.CollectionMember
import com.komgareader.domain.model.Series

/**
 * Detail einer Sammlung. Eigene TopBar (die Home-TopBar tritt zurück): links Zurück, mittig der
 * Sammlungs-Titel (groß) — per Lupe gegen ein Suchfeld tauschbar —, rechts Lupe · „+" (Werke aus
 * der Bibliothek) · Sync · Löschen. Die Werke liegen als Cover-Gitter (3 Spalten).
 */
@Composable
fun CollectionDetailScreen(
    collectionId: Long,
    onBack: () -> Unit,
    viewModel: CollectionsViewModel = hiltViewModel(),
    libraryVm: LibraryViewModel = hiltViewModel(),
) {
    val s = LocalStrings.current
    val collections by viewModel.collections.collectAsState()
    val collection = collections.find { it.id == collectionId }
    val syncLinks by viewModel.syncLinks(collectionId).collectAsState(emptyList())

    var searchActive by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var syncAnchor by remember { mutableStateOf(IntOffset.Zero) }
    var showSyncPanel by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var showAdd by remember { mutableStateOf(false) }

    if (collection == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(s.collections) }
        return
    }
    val isSeries = collection.kind == CollectionKind.SERIES
    val members = if (query.isBlank()) collection.members
    else collection.members.filter { it.title.contains(query, ignoreCase = true) }

    Column(Modifier.fillMaxSize()) {
        CollectionDetailHeader(
            title = collection.name,
            searchActive = searchActive,
            query = query,
            onQueryChange = { query = it },
            onOpenSearch = { searchActive = true },
            onCloseSearch = { searchActive = false; query = "" },
            onBack = onBack,
            showAdd = isSeries,
            onAdd = { showAdd = true },
            onSyncAnchor = { syncAnchor = it },
            onSync = { showSyncPanel = true },
            onDelete = { showDelete = true },
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize().padding(horizontal = EinkTokens.tileGap),
            contentPadding = PaddingValues(top = EinkTokens.tileGap, bottom = LocalContentBottomInset.current + 4.dp),
            horizontalArrangement = Arrangement.spacedBy(EinkTokens.tileGap),
            verticalArrangement = Arrangement.spacedBy(EinkTokens.tileGap),
        ) {
            items(members, key = { it.sourceId.toString() + it.remoteId }) { member ->
                MemberTile(
                    member = member,
                    isSeries = isSeries,
                    onRemove = { viewModel.removeMember(collectionId, member.sourceId, member.remoteId) },
                )
            }
        }
    }

    // Sync-Status als Kontextfeld direkt unter dem Sync-Icon (outside-tap schließt = „blur").
    if (showSyncPanel) {
        AnchoredMenuPopup(anchor = syncAnchor, alignEnd = true, onDismiss = { showSyncPanel = false }) {
            EinkOutlinedButton(
                onClick = { viewModel.syncNow(collectionId); showSyncPanel = false },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(AppIcons.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(s.collectionSyncNow)
            }
            if (syncLinks.isEmpty()) {
                Text(
                    s.collectionLocalOnly,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            } else {
                syncLinks.forEach { link ->
                    Row(
                        Modifier.fillMaxWidth().padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(s.sourceLabel(link.sourceId), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Text(
                            "${link.status.name} · ${if (link.dirty) s.collectionPending else s.collectionSynced}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    if (showAdd && isSeries) {
        AddWorksModal(
            existingKeys = remember(collection.members) { collection.members.map { it.sourceId to it.remoteId }.toSet() },
            libraryVm = libraryVm,
            onConfirm = { picked ->
                picked.forEach { viewModel.addMember(collectionId, it) }
                showAdd = false
            },
            onDismiss = { showAdd = false },
        )
    }

    if (showDelete) {
        var serverToo by remember { mutableStateOf(false) }
        EinkModal(
            title = s.deleteCollection,
            onDismiss = { showDelete = false },
            confirmLabel = s.deleteCollection,
            onConfirm = {
                viewModel.delete(collectionId, serverToo)
                showDelete = false
                onBack()
            },
            dismissLabel = s.cancel,
        ) {
            Text(collection.name, style = MaterialTheme.typography.bodyLarge)
            ChoiceRow(label = s.deleteCollectionServerToo, selected = serverToo, onSelect = { serverToo = !serverToo })
        }
    }
}

/** Höhe der Detail-TopBar — auf Höhe der übrigen Aktions-Icons. */
private val HEADER_HEIGHT = 56.dp

/**
 * Eigene TopBar des Sammlungs-Details: Zurück (links), Titel groß+zentriert bzw. Suchfeld (Lupe),
 * rechts die Aktionen. Verlässt das Suchfeld den Fokus ohne Eingabe, klappt es wieder zum Titel.
 */
@Composable
private fun CollectionDetailHeader(
    title: String,
    searchActive: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    onOpenSearch: () -> Unit,
    onCloseSearch: () -> Unit,
    onBack: () -> Unit,
    showAdd: Boolean,
    onAdd: () -> Unit,
    onSyncAnchor: (IntOffset) -> Unit,
    onSync: () -> Unit,
    onDelete: () -> Unit,
) {
    val s = LocalStrings.current
    Column {
        Box(
            Modifier
                .fillMaxWidth()
                .height(HEADER_HEIGHT)
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) {
                Icon(AppIcons.Back, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
            }

            if (searchActive) {
                val focus = remember { FocusRequester() }
                LaunchedEffect(Unit) { focus.requestFocus() }
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    placeholder = { Text(s.searchInCollection(title), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .focusRequester(focus)
                        .onFocusChanged { if (!it.isFocused && query.isBlank()) onCloseSearch() },
                )
            } else {
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(0.6f),
                )
            }

            if (!searchActive) {
                Row(Modifier.align(Alignment.CenterEnd), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onOpenSearch) {
                        Icon(AppIcons.Search, contentDescription = s.searchInCollection(title), tint = MaterialTheme.colorScheme.onSurface)
                    }
                    if (showAdd) {
                        IconButton(onClick = onAdd) {
                            Icon(AppIcons.Plus, contentDescription = s.addWorks, tint = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                    IconButton(
                        onClick = onSync,
                        modifier = Modifier.onGloballyPositioned {
                            val p = it.positionInWindow()
                            onSyncAnchor(IntOffset((p.x + it.size.width).toInt(), (p.y + it.size.height).toInt()))
                        },
                    ) {
                        Icon(AppIcons.Refresh, contentDescription = s.collectionSyncNow, tint = MaterialTheme.colorScheme.onSurface)
                    }
                    IconButton(onClick = onDelete) {
                        Icon(AppIcons.Delete, contentDescription = s.deleteCollection, tint = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(EinkTokens.hairline).background(MaterialTheme.colorScheme.outlineVariant))
    }
}

/**
 * Modal „Werke hinzufügen": die gesamte Bibliotheks-Serienliste, per Suche filterbar. Jedes Cover
 * trägt oben rechts ein „+"/✓-Overlay zum (Ab-)Wählen; **Speichern** übernimmt alle, **Abbrechen**
 * verwirft. Bereits enthaltene Werke sind vorab als ✓ markiert.
 */
@Composable
private fun AddWorksModal(
    existingKeys: Set<Pair<Long, String>>,
    libraryVm: LibraryViewModel,
    onConfirm: (List<CollectionMember>) -> Unit,
    onDismiss: () -> Unit,
) {
    val s = LocalStrings.current
    val libState by libraryVm.state.collectAsState()
    val allSeries = (libState as? LibraryUiState.Content)?.series ?: emptyList()

    var query by remember { mutableStateOf("") }
    val shown = if (query.isBlank()) allSeries
    else allSeries.filter { it.title.contains(query, ignoreCase = true) }

    // Lokale (staged) Auswahl der neu hinzuzufügenden Werke — erst „Speichern" wendet sie an.
    val staged = remember { mutableStateListOf<Series>() }

    EinkModal(
        title = s.addWorks,
        onDismiss = onDismiss,
        confirmLabel = s.save,
        onConfirm = { onConfirm(staged.map { CollectionMember(it.sourceId, it.remoteId, it.title) }) },
        dismissLabel = s.cancel,
        confirmEnabled = staged.isNotEmpty(),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text(s.searchMediaHint) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Column(
            Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(EinkTokens.tileGap),
        ) {
            shown.forEach { series ->
                val key = series.sourceId to series.remoteId
                val alreadyIn = key in existingKeys
                val isStaged = staged.any { it.sourceId == series.sourceId && it.remoteId == series.remoteId }
                AddWorkRow(
                    series = series,
                    checked = alreadyIn || isStaged,
                    enabled = !alreadyIn,
                    onToggle = {
                        if (isStaged) staged.removeAll { it.sourceId == series.sourceId && it.remoteId == series.remoteId }
                        else staged.add(series)
                    },
                )
            }
        }
    }
}

/** Zeile in „Werke hinzufügen": kleines Cover mit „+"/✓-Overlay + Titel. */
@Composable
private fun AddWorkRow(series: Series, checked: Boolean, enabled: Boolean, onToggle: () -> Unit) {
    val ctx = LocalContext.current
    val accent = LocalDesignTokens.current.accent
    val onAccent = LocalDesignTokens.current.onAccent
    val request = remember(series.sourceId, series.remoteId) {
        ImageRequest.Builder(ctx).data(SourceCover(series.sourceId, series.remoteId, isSeries = true)).crossfade(false).build()
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onToggle),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(40.dp)
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(4.dp))
                .border(EinkTokens.hairline, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp)),
        ) {
            FilteredAsyncImage(
                model = request,
                contentDescription = series.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            // „+"/✓-Overlay oben rechts: gewählt = gefüllter Akzent, sonst Outline.
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(2.dp)
                    .size(16.dp)
                    .clip(CircleShape)
                    .then(
                        if (checked) Modifier.background(accent)
                        else Modifier.background(MaterialTheme.colorScheme.surface).border(EinkTokens.hairline, MaterialTheme.colorScheme.outline, CircleShape),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (checked) AppIcons.Check else AppIcons.Plus,
                    contentDescription = null,
                    tint = if (checked) onAccent else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(11.dp),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(
            series.title,
            style = MaterialTheme.typography.bodyLarge,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * Mitglied als **Cover-Kachel** (2:3): Cover (quellen-agnostisch über [SourceCover]), Titelband
 * unten, Entfernen-Aktion oben rechts.
 */
@Composable
private fun MemberTile(
    member: CollectionMember,
    isSeries: Boolean,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = LocalStrings.current
    val ctx = LocalContext.current
    val request = remember(member, isSeries) {
        ImageRequest.Builder(ctx)
            .data(SourceCover(member.sourceId, member.remoteId, isSeries))
            .crossfade(false).build()
    }
    Box(
        modifier
            .aspectRatio(2f / 3f)
            .clip(RoundedCornerShape(8.dp))
            .border(EinkTokens.hairline, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)),
    ) {
        FilteredAsyncImage(
            model = request,
            contentDescription = member.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        val cornerShape = RoundedCornerShape(bottomStart = 10.dp)
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .clip(cornerShape)
                .background(MaterialTheme.colorScheme.surface)
                .border(EinkTokens.hairline, MaterialTheme.colorScheme.outline, cornerShape),
        ) {
            IconButton(onClick = onRemove, modifier = Modifier.size(36.dp)) {
                Icon(
                    AppIcons.Delete,
                    contentDescription = s.removeFromCollection,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        TileTitleBand(member.title, Modifier.align(Alignment.BottomStart))
    }
}
