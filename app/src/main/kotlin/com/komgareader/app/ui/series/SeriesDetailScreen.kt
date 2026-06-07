package com.komgareader.app.ui.series

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import com.komgareader.app.ui.components.LoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.window.PopupPositionProvider
import androidx.hilt.navigation.compose.hiltViewModel
import com.komgareader.app.ui.components.FilteredAsyncImage
import coil.request.ImageRequest
import com.komgareader.app.data.AuthHeaders
import com.komgareader.app.i18n.LocalStrings
import com.komgareader.app.ui.theme.EinkTokens
import com.komgareader.app.i18n.localizedContentType
import com.komgareader.app.i18n.localizedSeriesStatus
import com.komgareader.domain.model.Book
import com.komgareader.domain.model.ContentType
import com.komgareader.domain.repository.ServerConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeriesDetailScreen(
    onBack: () -> Unit,
    onOpenBook: (bookId: String, pageCount: Int, format: String, forceStream: Boolean, viewerMode: String) -> Unit,
    viewModel: SeriesDetailViewModel = hiltViewModel(),
) {
    val s = LocalStrings.current
    val state by viewModel.state.collectAsState()
    val localIds by viewModel.localBookIds.collectAsState()
    val downloadingIds by viewModel.downloadingIds.collectAsState()
    val downloadProgress by viewModel.downloadProgress.collectAsState()
    val cancelling by viewModel.cancelling.collectAsState()
    val chapterViewMode by viewModel.chapterViewMode.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var typeMenuOpen by remember { mutableStateOf(false) }
    var burgerAnchor by remember { mutableStateOf(IntOffset.Zero) }

    // Fehler-Events als Snackbar anzeigen
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is SeriesDetailEvent.DownloadError -> snackbarHostState.showSnackbar(event.message)
                SeriesDetailEvent.DownloadCancelled -> snackbarHostState.showSnackbar(s.downloadCancelled)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val title = when (val c = state) {
                        is SeriesDetailUiState.Content -> c.seriesTitle
                        else -> "Serie"
                    }
                    Text(title)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when (val current = state) {
            is SeriesDetailUiState.Loading -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    LoadingIndicator()
                }
            }
            is SeriesDetailUiState.NoServer -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text("Kein Server verbunden.")
                }
            }
            is SeriesDetailUiState.Error -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text(current.message)
                }
            }
            is SeriesDetailUiState.Content -> {
                SeriesDetailContent(
                    books = current.books,
                    seriesTitle = current.seriesTitle,
                    seriesRemoteId = current.seriesRemoteId,
                    serverConfig = current.serverConfig,
                    seriesSummary = current.seriesSummary,
                    seriesStatus = current.seriesStatus,
                    seriesGenres = current.seriesGenres,
                    contentType = current.effectiveContentType,
                    viewerModes = current.viewerModes,
                    localIds = localIds,
                    downloadingIds = downloadingIds,
                    downloadProgress = downloadProgress,
                    cancelling = cancelling,
                    onOpenBook = onOpenBook,
                    onOpenChapter = viewModel::onOpenChapter,
                    onDownload = viewModel::download,
                    onRemoveDownload = viewModel::removeDownload,
                    onDownloadAll = { viewModel.downloadAll(current.books) },
                    onCancelDownload = viewModel::cancelDownloadAll,
                    onRemoveAll = { viewModel.removeAll(current.books) },
                    onSetRead = viewModel::setRead,
                    onOpenTypeMenu = { typeMenuOpen = true },
                    onTypeMenuAnchor = { burgerAnchor = it },
                    gridMode = chapterViewMode == "GRID",
                    onToggleViewMode = {
                        viewModel.setChapterViewMode(if (chapterViewMode == "GRID") "LIST" else "GRID")
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                )
            }
        }
    }

    val content = state as? SeriesDetailUiState.Content
    if (typeMenuOpen && content != null) {
        TypeMenu(
            anchor = burgerAnchor,
            current = content.manualContentType,
            onPick = { type ->
                viewModel.setType(type)
                typeMenuOpen = false
            },
            onDismiss = { typeMenuOpen = false },
        )
    }
}

@Composable
private fun SeriesDetailContent(
    books: List<Book>,
    seriesTitle: String,
    seriesRemoteId: String,
    serverConfig: ServerConfig?,
    seriesSummary: String?,
    seriesStatus: String?,
    seriesGenres: List<String>,
    contentType: ContentType?,
    viewerModes: Map<String, String>,
    localIds: Set<String>,
    downloadingIds: Set<String>,
    downloadProgress: DownloadProgress?,
    cancelling: Boolean,
    onOpenBook: (bookId: String, pageCount: Int, format: String, forceStream: Boolean, viewerMode: String) -> Unit,
    onOpenChapter: (Book) -> Unit,
    onDownload: (Book) -> Unit,
    onRemoveDownload: (String) -> Unit,
    onDownloadAll: () -> Unit,
    onCancelDownload: () -> Unit,
    onRemoveAll: () -> Unit,
    onSetRead: (Book, Boolean) -> Unit,
    onOpenTypeMenu: () -> Unit,
    onTypeMenuAnchor: (IntOffset) -> Unit,
    gridMode: Boolean,
    onToggleViewMode: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // „Weiterlesen": erstes noch nicht abgeschlossenes Kapitel (laufendes oder nächstes
    // ungelesenes); sind alle gelesen, das erste. So öffnet „Lesen" das richtige Kapitel.
    val continueBookId = books.firstOrNull { !it.readCompleted }?.remoteId ?: books.firstOrNull()?.remoteId
    var selectedBook by rememberSaveable(books) { mutableStateOf(continueBookId) }

    val currentBook = books.firstOrNull { it.remoteId == selectedBook } ?: books.firstOrNull()
    // Genau EIN Lesezeichen: am weitesten gelesenen Kapitel (letztes mit Leseposition in
    // Lesereihenfolge). Springt man von Kapitel 1 zu 5, wandert es zu 5 — kein Sammeln.
    val bookmarkBookId = books.lastOrNull { it.lastReadPage != null }?.remoteId
    // Beschreibung: Serien-Summary hat Vorrang, sonst Summary des ausgewählten Buchs.
    val description = seriesSummary?.takeIf { it.isNotBlank() }
        ?: currentBook?.summary?.takeIf { it.isNotBlank() }
    // Wenn ein Kapitel per Info-Icon gewählt ist, ersetzt seine Beschreibung die Hero-Karte.
    var infoBook by remember(books) { mutableStateOf<Book?>(null) }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 150.dp),
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(EinkTokens.tileGap),
        verticalArrangement = Arrangement.spacedBy(EinkTokens.tileGap),
        contentPadding = PaddingValues(horizontal = EinkTokens.screenPadding, vertical = EinkTokens.tileGap),
    ) {
        // Hero-Karte: normal die Serie; per Info-Icon das gewählte Kapitel (in-place ersetzt).
        item(span = { GridItemSpan(maxLineSpan) }) {
            val info = infoBook
            if (info != null) {
                ChapterInfoHero(
                    book = info,
                    serverConfig = serverConfig,
                    genres = seriesGenres,
                    contentType = contentType,
                    isLocal = info.remoteId in localIds,
                    isDownloading = info.remoteId in downloadingIds,
                    onRead = {
                        onOpenChapter(info)
                        onOpenBook(
                            info.remoteId, info.pageCount, info.format.name, false,
                            viewerModes[info.remoteId] ?: "PAGED",
                        )
                    },
                    onDownload = { onDownload(info) },
                    onRemoveDownload = { onRemoveDownload(info.remoteId) },
                    onBack = { infoBook = null },
                    modifier = Modifier.padding(12.dp),
                )
            } else {
            SeriesHeroCard(
                seriesTitle = seriesTitle,
                bookCount = books.size,
                seriesRemoteId = seriesRemoteId,
                serverConfig = serverConfig,
                status = seriesStatus,
                genres = seriesGenres,
                contentType = contentType,
                description = description,
                currentBook = currentBook,
                allLocal = books.isNotEmpty() && books.all { it.remoteId in localIds },
                downloadProgress = downloadProgress,
                cancelling = cancelling,
                onOpenTypeMenu = onOpenTypeMenu,
                onTypeMenuAnchor = onTypeMenuAnchor,
                onRead = {
                    currentBook?.let {
                        onOpenChapter(it)
                        onOpenBook(
                            it.remoteId, it.pageCount, it.format.name, false,
                            viewerModes[it.remoteId] ?: "PAGED",
                        )
                    }
                },
                onDownloadAll = onDownloadAll,
                onCancelDownload = onCancelDownload,
                onRemoveAll = onRemoveAll,
                modifier = Modifier.padding(12.dp),
            )
            }
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            ChaptersSectionHeader(
                count = books.size,
                gridMode = gridMode,
                onToggleViewMode = onToggleViewMode,
            )
        }

        if (gridMode) {
            items(books, key = { it.remoteId }) { book ->
                ChapterTile(
                    book = book,
                    serverConfig = serverConfig,
                    isSelected = book.remoteId == currentBook?.remoteId,
                    showBookmark = book.remoteId == bookmarkBookId,
                    isLocal = book.remoteId in localIds,
                    isDownloading = book.remoteId in downloadingIds,
                    onOpen = {
                        onOpenChapter(book)
                        onOpenBook(
                            book.remoteId, book.pageCount, book.format.name, false,
                            viewerModes[book.remoteId] ?: "PAGED",
                        )
                    },
                    onDownload = { onDownload(book) },
                    onRemoveDownload = { onRemoveDownload(book.remoteId) },
                    onSetRead = { read -> onSetRead(book, read) },
                    onShowInfo = { infoBook = book },
                )
            }
        } else {
            items(books, key = { it.remoteId }, span = { GridItemSpan(maxLineSpan) }) { book ->
                Column {
                    ChapterRow(
                        book = book,
                        isSelected = book.remoteId == currentBook?.remoteId,
                        showBookmark = book.remoteId == bookmarkBookId,
                        isLocal = book.remoteId in localIds,
                        isDownloading = book.remoteId in downloadingIds,
                        onOpen = {
                            onOpenChapter(book)
                            onOpenBook(
                                book.remoteId, book.pageCount, book.format.name, false,
                                viewerModes[book.remoteId] ?: "PAGED",
                            )
                        },
                        onDownload = { onDownload(book) },
                        onRemoveDownload = { onRemoveDownload(book.remoteId) },
                        onSetRead = { read -> onSetRead(book, read) },
                        onShowInfo = { infoBook = book },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

/**
 * Fusionierte Hero-Karte: großes Cover trägt Titel + Status/Genres, darunter die
 * Serien-Beschreibung (falls vorhanden) und die Lese-/Download-Aktionen. Ersetzt die
 * frühere kleine Cover-Karte plus den separaten Kapitel-Detailblock.
 */
@Composable
private fun SeriesHeroCard(
    seriesTitle: String,
    bookCount: Int,
    seriesRemoteId: String,
    serverConfig: ServerConfig?,
    status: String?,
    genres: List<String>,
    contentType: ContentType?,
    description: String?,
    currentBook: Book?,
    allLocal: Boolean,
    downloadProgress: DownloadProgress?,
    cancelling: Boolean,
    onOpenTypeMenu: () -> Unit,
    onTypeMenuAnchor: (IntOffset) -> Unit,
    onRead: () -> Unit,
    onDownloadAll: () -> Unit,
    onCancelDownload: () -> Unit,
    onRemoveAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = LocalStrings.current
    val ctx = LocalContext.current
    val coverUrl = serverConfig?.let { "${it.baseUrl}series/$seriesRemoteId/thumbnail" }
    val statusText = status?.takeIf { it.isNotBlank() }?.let { s.localizedSeriesStatus(it) }
    val subtitle = listOfNotNull("$bookCount ${s.chapters}", statusText).joinToString(" · ")

    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(1.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
            .padding(12.dp),
    ) {
        Box(Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            // Großes Cover links
            if (coverUrl != null) {
                val authHeaders = AuthHeaders.forCovers(serverConfig)
                FilteredAsyncImage(
                    model = ImageRequest.Builder(ctx)
                        .data(coverUrl)
                        .apply { authHeaders.forEach { (k, v) -> addHeader(k, v) } }
                        .crossfade(true)
                        .build(),
                    contentDescription = seriesTitle,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .width(140.dp)
                        .aspectRatio(2f / 3f),
                )
            }

            // Titel + Kapitel/Status + Genre-Chips (Endpadding lässt Platz fürs Burger-Icon)
            Column(modifier = Modifier.weight(1f).padding(end = 36.dp)) {
                Text(
                    seriesTitle,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (genres.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    GenreChips(genres = genres)
                }
                // Eigener Typ-Chip unter den Genres: zeigt Bibliotheks- oder manuellen Typ,
                // sonst „Unbekannt" (fällt beim Lesen auf paginiert zurück).
                Spacer(Modifier.height(8.dp))
                TypeChip(label = s.localizedContentType(contentType))
            }
        }
            // Burger oben rechts in der Hero-Ecke: öffnet das Typ-Zuweisungs-Menü darunter.
            IconButton(
                onClick = onOpenTypeMenu,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .onGloballyPositioned { coords ->
                        val pos = coords.positionInWindow()
                        onTypeMenuAnchor(
                            IntOffset(
                                (pos.x + coords.size.width).toInt(),
                                (pos.y + coords.size.height).toInt(),
                            ),
                        )
                    },
            ) {
                Icon(Icons.Filled.Menu, contentDescription = s.assignType)
            }
        }

        // Beschreibung (Serie, Fallback ausgewähltes Buch); sonst Hinweis „keine Beschreibung".
        Spacer(Modifier.height(12.dp))
        Text(
            description ?: s.noDescription,
            style = MaterialTheme.typography.bodyMedium,
            color = if (description != null) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // Aktionsschaltflächen: Lesen (primär) + Download/Entfernen-Toggle
        if (currentBook != null) {
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(onClick = onRead, modifier = Modifier.weight(1f)) {
                    Text(s.read, maxLines = 1)
                }
                when {
                    // Abbruch läuft: aktuelles Kapitel wird noch fertig geladen → Lade-Anzeige
                    // (E-Ink: „Lädt…", Smartphone: Spinner) statt Stop-Button.
                    cancelling -> OutlinedButton(
                        onClick = {},
                        enabled = false,
                        modifier = Modifier.weight(1f),
                    ) {
                        LoadingIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    }
                    // Gleiche Breite (weight 1f) — der Lesen-Button dehnt sich beim Statuswechsel nicht aus.
                    // E-Ink: statischer Fortschrittstext (x/y + Speed, ≤1 Update/s), kein ghostender Spinner.
                    downloadProgress != null -> Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = {},
                            enabled = false,
                            modifier = Modifier.weight(1f),
                        ) {
                            val p = downloadProgress
                            val speed = if (p.bytesPerSec > 0) {
                                " · ${SeriesDetailViewModel.humanReadableSize(p.bytesPerSec)}/s"
                            } else {
                                ""
                            }
                            Text("${p.current}/${p.total}$speed", maxLines = 1)
                        }
                        // Abbrechen-Rechteck rechts: stoppt den Serien-Download (kein weiteres Kapitel).
                        OutlinedButton(
                            onClick = onCancelDownload,
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier.width(48.dp),
                        ) {
                            Icon(
                                Icons.Filled.Stop,
                                contentDescription = s.cancel,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                    allLocal -> OutlinedButton(onClick = onRemoveAll, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(s.removeDownload, maxLines = 1)
                    }
                    else -> OutlinedButton(onClick = onDownloadAll, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(s.downloadAll, maxLines = 1)
                    }
                }
            }
        }
    }
}

/**
 * Typ-Chip: gefüllt (solides Schwarz / invertiert) zur Abgrenzung von den outline-Genre-Chips.
 * Zeigt den wirksamen Inhaltstyp oder „Unbekannt".
 */
@Composable
private fun TypeChip(label: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.onSurface)
            .padding(horizontal = 10.dp, vertical = 3.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.surface,
            maxLines = 1,
        )
    }
}

/**
 * Burger-Menü zur manuellen Typ-Zuweisung (auch ohne Bibliothek). Bordered E-Ink-Popup,
 * Häkchen am aktuell gewählten Typ; „Automatisch" löscht die manuelle Zuweisung.
 */
@Composable
private fun TypeMenu(
    anchor: IntOffset,
    current: ContentType?,
    onPick: (ContentType?) -> Unit,
    onDismiss: () -> Unit,
) {
    val s = LocalStrings.current
    val options: List<Pair<ContentType?, String>> = listOf(
        null to s.tagAuto,
        ContentType.MANGA to s.tagManga,
        ContentType.COMIC to s.tagComic,
        ContentType.WEBTOON to s.tagWebtoon,
        ContentType.NOVEL to s.tagNovel,
    )
    // alignEnd: klappt unter dem Burger oben rechts nach links auf.
    AnchoredMenuPopup(anchor = anchor, alignEnd = true, onDismiss = onDismiss) {
        options.forEachIndexed { index, (type, label) ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onPick(type) }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                if (type == current) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            if (index < options.lastIndex) HorizontalDivider()
        }
    }
}

/** Genre-Chips als E-Ink-Border-Tags (max. 3, Rest abgeschnitten). */
@Composable
private fun GenreChips(genres: List<String>) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        genres.take(3).forEach { genre ->
            Box(
                modifier = Modifier
                    .border(1.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                    .padding(horizontal = 9.dp, vertical = 2.dp),
            ) {
                Text(
                    genre,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun ChaptersSectionHeader(
    count: Int,
    gridMode: Boolean,
    onToggleViewMode: () -> Unit,
) {
    val s = LocalStrings.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "${s.chapters} ($count)",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f),
        )
        // Toggle Liste ⇄ Kachel: zeigt das Ziel-Layout-Icon. Sofortiger Wechsel (E-Ink).
        IconButton(onClick = onToggleViewMode) {
            Icon(
                if (gridMode) Icons.AutoMirrored.Outlined.ViewList else Icons.Outlined.GridView,
                contentDescription =
                    if (gridMode) s.chapterViewSwitchToList else s.chapterViewSwitchToGrid,
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/**
 * Hero-Ersatz: zeigt Titel + Beschreibung eines einzelnen Kapitels statt der Serien-Info.
 * Wird über das Info-Icon eines Kapitels geöffnet; der Zurück-Pfeil stellt die Serie wieder her.
 */
@Composable
private fun ChapterInfoHero(
    book: Book,
    serverConfig: ServerConfig?,
    genres: List<String>,
    contentType: ContentType?,
    isLocal: Boolean,
    isDownloading: Boolean,
    onRead: () -> Unit,
    onDownload: () -> Unit,
    onRemoveDownload: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = LocalStrings.current
    val ctx = LocalContext.current
    val coverUrl = serverConfig?.let { "${it.baseUrl}books/${book.remoteId}/thumbnail" }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(1.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
            .padding(12.dp),
    ) {
        Box(Modifier.fillMaxWidth()) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                if (coverUrl != null) {
                    val authHeaders = AuthHeaders.forCovers(serverConfig)
                    FilteredAsyncImage(
                        model = ImageRequest.Builder(ctx)
                            .data(coverUrl)
                            .apply { authHeaders.forEach { (k, v) -> addHeader(k, v) } }
                            .crossfade(false)
                            .build(),
                        contentDescription = book.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.width(140.dp).aspectRatio(2f / 3f),
                    )
                }
                // Genres + Typ bleiben wie beim Serien-Hero (ändern sich nicht pro Kapitel).
                Column(modifier = Modifier.weight(1f).padding(end = 36.dp)) {
                    Text(
                        book.number?.let { "$it · ${book.title}" } ?: book.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    if (genres.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        GenreChips(genres = genres)
                    }
                    Spacer(Modifier.height(8.dp))
                    TypeChip(label = s.localizedContentType(contentType))
                }
            }
            // Zurück zur Serien-Hero-Karte.
            IconButton(onClick = onBack, modifier = Modifier.align(Alignment.TopEnd)) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = s.backToSeries)
            }
        }
        val summaryText = book.summary?.takeIf { it.isNotBlank() }
        Spacer(Modifier.height(12.dp))
        Text(
            summaryText ?: s.noDescription,
            style = MaterialTheme.typography.bodyMedium,
            color = if (summaryText != null) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // Lese-/Download-Aktionen — beziehen sich auf dieses Kapitel.
        Spacer(Modifier.height(14.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(onClick = onRead, modifier = Modifier.weight(1f)) {
                Text(s.read, maxLines = 1)
            }
            when {
                isDownloading -> OutlinedButton(
                    onClick = {},
                    enabled = false,
                    modifier = Modifier.weight(1f),
                ) {
                    LoadingIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                }
                isLocal -> OutlinedButton(onClick = onRemoveDownload, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(s.removeDownload, maxLines = 1)
                }
                else -> OutlinedButton(onClick = onDownload, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(s.download, maxLines = 1)
                }
            }
        }
    }
}

/** Lesefortschritt in Prozent: 100 wenn abgeschlossen, sonst Leseposition/Seitenzahl, 0 wenn ungelesen. */
private fun readPercent(book: Book): Int {
    if (book.readCompleted) return 100
    val page = book.lastReadPage
    return if (page != null && book.pageCount > 0) (page * 100 / book.pageCount).coerceIn(0, 100) else 0
}

@Composable
private fun ChapterRow(
    book: Book,
    isSelected: Boolean,
    showBookmark: Boolean,
    isLocal: Boolean,
    isDownloading: Boolean,
    onOpen: () -> Unit,
    onDownload: () -> Unit,
    onRemoveDownload: () -> Unit,
    onSetRead: (Boolean) -> Unit,
    onShowInfo: () -> Unit,
) {
    val s = LocalStrings.current
    var menuOpen by remember { mutableStateOf(false) }
    var rowPos by remember { mutableStateOf(Offset.Zero) }
    var pressAnchor by remember { mutableStateOf(IntOffset.Zero) }

    Row(
        Modifier
            .fillMaxWidth()
            .onGloballyPositioned { rowPos = it.positionInWindow() }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onOpen() },
                    onLongPress = { local ->
                        // Druckpunkt in Fensterkoordinaten → Kontextmenü öffnet genau dort.
                        pressAnchor = IntOffset((rowPos.x + local.x).toInt(), (rowPos.y + local.y).toInt())
                        menuOpen = true
                    },
                )
            }
            .background(
                if (isSelected) MaterialTheme.colorScheme.surfaceVariant
                else MaterialTheme.colorScheme.surface,
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                book.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface,
            )
            val percent = readPercent(book)
            val subtitle = when {
                book.readCompleted -> "${s.statusRead} · $percent%"
                book.lastReadPage != null -> "${s.pagesShort} ${book.lastReadPage}/${book.pageCount} · $percent%"
                else -> "${book.pageCount} ${s.pagesShort} · $percent%"
            }
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // Status-/Aktions-Icons: Lesezeichen (Leseposition) · Häkchen (gelesen) · Cloud/Entfernen.
        // E-Ink: Gelesen wird als Häkchen-Logo gezeigt, nicht über Textfarbe.
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onShowInfo, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Outlined.Info,
                    contentDescription = s.chapterInfo,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.dp),
                )
            }
            if (showBookmark) {
                Icon(
                    Icons.Filled.Bookmark,
                    contentDescription = s.resumeHere,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
            }
            if (book.readCompleted) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = s.statusRead,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
            }
            when {
                isDownloading -> LoadingIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                isLocal -> IconButton(onClick = onRemoveDownload, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = s.removeDownload,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp),
                    )
                }
                else -> IconButton(onClick = onDownload, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Filled.CloudDownload,
                        contentDescription = s.download,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }

    if (menuOpen) {
        EinkActionMenu(
            anchor = pressAnchor,
            onDismiss = { menuOpen = false },
            items = listOf(
                s.markRead to { onSetRead(true) },
                s.markUnread to { onSetRead(false) },
            ),
        )
    }
}

/**
 * Kapitel als Kachel: eigenes Cover mit Status-/Aktions-Overlays in den Ecken.
 * oben-rechts = Lesezeichen + „gelesen"-Häkchen, unten-rechts = Download/Löschen.
 * Tap öffnet das Kapitel, Long-Press öffnet das Gelesen/Ungelesen-Menü (wie ChapterRow).
 */
@Composable
private fun ChapterTile(
    book: Book,
    serverConfig: ServerConfig?,
    isSelected: Boolean,
    showBookmark: Boolean,
    isLocal: Boolean,
    isDownloading: Boolean,
    onOpen: () -> Unit,
    onDownload: () -> Unit,
    onRemoveDownload: () -> Unit,
    onSetRead: (Boolean) -> Unit,
    onShowInfo: () -> Unit,
) {
    val s = LocalStrings.current
    val ctx = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }
    var tilePos by remember { mutableStateOf(Offset.Zero) }
    var pressAnchor by remember { mutableStateOf(IntOffset.Zero) }
    val coverUrl = serverConfig?.let { "${it.baseUrl}books/${book.remoteId}/thumbnail" }

    Column(
        Modifier
            .fillMaxWidth()
            .background(
                if (isSelected) MaterialTheme.colorScheme.surfaceVariant
                else MaterialTheme.colorScheme.surface,
            )
            .onGloballyPositioned { tilePos = it.positionInWindow() }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onOpen() },
                    onLongPress = { local ->
                        pressAnchor = IntOffset(
                            (tilePos.x + local.x).toInt(),
                            (tilePos.y + local.y).toInt(),
                        )
                        menuOpen = true
                    },
                )
            }
            .padding(4.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .border(
                    EinkTokens.hairline,
                    MaterialTheme.colorScheme.outlineVariant,
                    RoundedCornerShape(EinkTokens.tileRadius),
                )
                .clip(RoundedCornerShape(EinkTokens.tileRadius)),
        ) {
            if (coverUrl != null) {
                val authHeaders = AuthHeaders.forCovers(serverConfig)
                FilteredAsyncImage(
                    model = ImageRequest.Builder(ctx)
                        .data(coverUrl)
                        .apply { authHeaders.forEach { (k, v) -> addHeader(k, v) } }
                        .crossfade(false)
                        .build(),
                    contentDescription = book.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            // oben-rechts: Info — öffnet immer die Kapitel-Beschreibung (auch wenn keine da ist).
            Box(Modifier.align(Alignment.TopEnd).padding(4.dp)) {
                CoverBadge(onClick = onShowInfo) {
                    Icon(
                        Icons.Outlined.Info,
                        contentDescription = s.chapterInfo,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            // oben-links: Lesezeichen + Häkchen (nur was zutrifft), vertikal gestapelt.
            Column(
                Modifier.align(Alignment.TopStart).padding(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (showBookmark) CoverBadge {
                    Icon(
                        Icons.Outlined.Bookmark,
                        contentDescription = s.resumeHere,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(18.dp),
                    )
                }
                if (book.readCompleted) CoverBadge {
                    Icon(
                        Icons.Outlined.Check,
                        contentDescription = s.statusRead,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            // unten-rechts: Download / Löschen / Lade-Spinner.
            Box(Modifier.align(Alignment.BottomEnd).padding(4.dp)) {
                when {
                    isDownloading -> CoverBadge {
                        LoadingIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    }
                    isLocal -> CoverBadge(onClick = onRemoveDownload) {
                        Icon(
                            Icons.Outlined.Delete,
                            contentDescription = s.removeDownload,
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    else -> CoverBadge(onClick = onDownload) {
                        Icon(
                            Icons.Outlined.CloudDownload,
                            contentDescription = s.download,
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
            // unten-links: Lesefortschritt in Prozent.
            Box(Modifier.align(Alignment.BottomStart).padding(4.dp)) {
                CoverBadge {
                    Text(
                        "${readPercent(book)}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            book.number?.let { "$it · ${book.title}" } ?: book.title,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }

    if (menuOpen) {
        EinkActionMenu(
            anchor = pressAnchor,
            onDismiss = { menuOpen = false },
            items = listOf(
                s.markRead to { onSetRead(true) },
                s.markUnread to { onSetRead(false) },
            ),
        )
    }
}

/**
 * Opaker Eck-Chip auf dem Cover: solide Fläche + Hairline-Rahmen, damit Icons auf
 * jedem (Kaleido-)Cover kontraststark bleiben. Optional klickbar (Download/Löschen).
 */
@Composable
private fun CoverBadge(
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(6.dp)
    var chip = Modifier
        .background(MaterialTheme.colorScheme.surface, shape)
        .border(EinkTokens.hairline, MaterialTheme.colorScheme.outlineVariant, shape)
    if (onClick != null) chip = chip.clickable(onClick = onClick)
    // Chip umschließt das Icon eng (wie der Lesezeichen-Chip) — auch klickbar, kein fixes 36dp.
    Box(chip.padding(4.dp), contentAlignment = Alignment.Center) { content() }
}

/**
 * Positioniert ein Popup an einem absoluten Fenster-Anker. [alignEnd] = true richtet die
 * rechte Kante am Anker aus (Dropdown nach links, z.B. unter dem Burger oben rechts);
 * sonst die linke Kante (Kontextmenü genau am Druckpunkt). Stets im Fenster geklemmt.
 */
private class AnchorPositionProvider(
    private val anchor: IntOffset,
    private val alignEnd: Boolean,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val rawX = if (alignEnd) anchor.x - popupContentSize.width else anchor.x
        val maxX = (windowSize.width - popupContentSize.width).coerceAtLeast(0)
        val maxY = (windowSize.height - popupContentSize.height).coerceAtLeast(0)
        return IntOffset(rawX.coerceIn(0, maxX), anchor.y.coerceIn(0, maxY))
    }
}

/** Bordered E-Ink-Popup-Container am [anchor]; flach, kein Material-Dropdown. */
@Composable
private fun AnchoredMenuPopup(
    anchor: IntOffset,
    alignEnd: Boolean,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val provider = remember(anchor, alignEnd) { AnchorPositionProvider(anchor, alignEnd) }
    Popup(
        popupPositionProvider = provider,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Column(
            Modifier
                .width(260.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)),
            content = content,
        )
    }
}

/**
 * Schlankes E-Ink-Kontextmenü (Long-Press): öffnet genau am Druckpunkt [anchor].
 */
@Composable
private fun EinkActionMenu(
    anchor: IntOffset,
    onDismiss: () -> Unit,
    items: List<Pair<String, () -> Unit>>,
) {
    AnchoredMenuPopup(anchor = anchor, alignEnd = false, onDismiss = onDismiss) {
        items.forEachIndexed { index, (label, action) ->
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { action(); onDismiss() }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            )
            if (index < items.lastIndex) HorizontalDivider()
        }
    }
}
