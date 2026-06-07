package com.komgareader.app.ui.series

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import com.komgareader.app.ui.components.LoadingIndicator
import com.komgareader.app.ui.components.LocalEinkMode
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
    modifier: Modifier = Modifier,
) {
    // „Weiterlesen": erstes noch nicht abgeschlossenes Kapitel (laufendes oder nächstes
    // ungelesenes); sind alle gelesen, das erste. So öffnet „Lesen" das richtige Kapitel.
    val continueBookId = books.firstOrNull { !it.readCompleted }?.remoteId ?: books.firstOrNull()?.remoteId
    var selectedBook by rememberSaveable(books) { mutableStateOf(continueBookId) }
    var chaptersExpanded by rememberSaveable { mutableStateOf(true) }

    val currentBook = books.firstOrNull { it.remoteId == selectedBook } ?: books.firstOrNull()
    // Genau EIN Lesezeichen: am weitesten gelesenen Kapitel (letztes mit Leseposition in
    // Lesereihenfolge). Springt man von Kapitel 1 zu 5, wandert es zu 5 — kein Sammeln.
    val bookmarkBookId = books.lastOrNull { it.lastReadPage != null }?.remoteId
    // Beschreibung: Serien-Summary hat Vorrang, sonst Summary des ausgewählten Buchs.
    val description = seriesSummary?.takeIf { it.isNotBlank() }
        ?: currentBook?.summary?.takeIf { it.isNotBlank() }

    LazyColumn(modifier = modifier) {
        // Fusionierte Hero-Karte: großes Cover, Titel, Status/Genres, Beschreibung, Aktionen.
        item {
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

        // Kollabierbare Kapitelliste
        item {
            ChaptersSectionHeader(
                count = books.size,
                expanded = chaptersExpanded,
                onToggle = { chaptersExpanded = !chaptersExpanded },
            )
        }

        item {
            // E-Ink: kein Bewegungs-Effekt (Ghosting) → sofortiges Auf-/Zuklappen.
            // Smartphone: vertikales Falten von oben (kein Diagonal-Schrumpfen in die Ecke).
            val eink = LocalEinkMode.current
            AnimatedVisibility(
                visible = chaptersExpanded,
                enter = if (eink) EnterTransition.None else expandVertically(expandFrom = Alignment.Top),
                exit = if (eink) ExitTransition.None else shrinkVertically(shrinkTowards = Alignment.Top),
            ) {
                Column {
                    books.forEach { book ->
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
                        )
                        HorizontalDivider()
                    }
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

        // Beschreibung (Serie, Fallback ausgewähltes Buch) — nur wenn vorhanden
        if (description != null) {
            Spacer(Modifier.height(12.dp))
            Text(
                description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

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
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val s = LocalStrings.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "${s.chapters} ($count)",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f),
        )
        Icon(
            if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
            contentDescription = if (expanded) "Einklappen" else "Ausklappen",
        )
    }
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
            val subtitle = when {
                book.readCompleted -> s.statusRead
                book.lastReadPage != null -> "${s.pagesShort} ${book.lastReadPage}/${book.pageCount}"
                else -> "${book.pageCount} ${s.pagesShort}"
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
