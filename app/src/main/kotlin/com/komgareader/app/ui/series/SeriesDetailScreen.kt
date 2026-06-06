package com.komgareader.app.ui.series

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.komgareader.app.data.AuthHeaders
import com.komgareader.app.i18n.LocalStrings
import com.komgareader.app.i18n.localizedSeriesStatus
import com.komgareader.domain.model.Book
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
    val snackbarHostState = remember { SnackbarHostState() }

    // Fehler-Events als Snackbar anzeigen
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is SeriesDetailEvent.DownloadError -> snackbarHostState.showSnackbar(event.message)
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
                    viewerModes = current.viewerModes,
                    localIds = localIds,
                    downloadingIds = downloadingIds,
                    onOpenBook = onOpenBook,
                    onDownload = viewModel::download,
                    onRemoveDownload = viewModel::removeDownload,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                )
            }
        }
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
    viewerModes: Map<String, String>,
    localIds: Set<String>,
    downloadingIds: Set<String>,
    onOpenBook: (bookId: String, pageCount: Int, format: String, forceStream: Boolean, viewerMode: String) -> Unit,
    onDownload: (Book) -> Unit,
    onRemoveDownload: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // „Weiterlesen": erstes noch nicht abgeschlossenes Kapitel (laufendes oder nächstes
    // ungelesenes); sind alle gelesen, das erste. So öffnet „Lesen" das richtige Kapitel.
    val continueBookId = books.firstOrNull { !it.readCompleted }?.remoteId ?: books.firstOrNull()?.remoteId
    var selectedBook by rememberSaveable(books) { mutableStateOf(continueBookId) }
    var chaptersExpanded by rememberSaveable { mutableStateOf(true) }

    val currentBook = books.firstOrNull { it.remoteId == selectedBook } ?: books.firstOrNull()
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
                description = description,
                currentBook = currentBook,
                isLocal = currentBook?.remoteId in localIds,
                isDownloading = currentBook?.remoteId in downloadingIds,
                onRead = {
                    currentBook?.let {
                        onOpenBook(
                            it.remoteId, it.pageCount, it.format.name, false,
                            viewerModes[it.remoteId] ?: "PAGED",
                        )
                    }
                },
                onDownload = { currentBook?.let(onDownload) },
                onRemoveDownload = { currentBook?.let { onRemoveDownload(it.remoteId) } },
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
            AnimatedVisibility(visible = chaptersExpanded) {
                Column {
                    books.forEach { book ->
                        ChapterRow(
                            book = book,
                            isSelected = book.remoteId == currentBook?.remoteId,
                            isLocal = book.remoteId in localIds,
                            isDownloading = book.remoteId in downloadingIds,
                            onSelect = { selectedBook = book.remoteId },
                            onDownload = { onDownload(book) },
                            onRemoveDownload = { onRemoveDownload(book.remoteId) },
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
    description: String?,
    currentBook: Book?,
    isLocal: Boolean,
    isDownloading: Boolean,
    onRead: () -> Unit,
    onDownload: () -> Unit,
    onRemoveDownload: () -> Unit,
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
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            // Großes Cover links
            if (coverUrl != null) {
                val authHeaders = AuthHeaders.forCovers(serverConfig)
                AsyncImage(
                    model = ImageRequest.Builder(ctx)
                        .data(coverUrl)
                        .apply { authHeaders.forEach { (k, v) -> addHeader(k, v) } }
                        .crossfade(true)
                        .build(),
                    contentDescription = seriesTitle,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .width(118.dp)
                        .aspectRatio(2f / 3f),
                )
            }

            // Titel + Kapitel/Status + Genre-Chips
            Column(modifier = Modifier.weight(1f)) {
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
                    // Gleiche Breite (weight 1f) wie die Toggle-Buttons, nur in-place getauscht —
                    // so dehnt sich der Lesen-Button beim Statuswechsel nicht aus.
                    // E-Ink: statischer „Lädt…"-Text (kein ghostender Spinner); Smartphone: Spinner.
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
                        Text(s.downloadShort, maxLines = 1)
                    }
                }
            }
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
    isLocal: Boolean,
    isDownloading: Boolean,
    onSelect: () -> Unit,
    onDownload: () -> Unit,
    onRemoveDownload: () -> Unit,
) {
    val s = LocalStrings.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .background(
                if (isSelected) MaterialTheme.colorScheme.surfaceVariant
                else MaterialTheme.colorScheme.surface,
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (book.readCompleted) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = s.statusRead,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    book.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    // Gelesene Kapitel etwas zurückgenommen darstellen.
                    color = if (book.readCompleted) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
            }
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
        // Statusicon + Download/Entfernen-Aktion pro Zeile
        when {
            isDownloading -> LoadingIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
            )
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
