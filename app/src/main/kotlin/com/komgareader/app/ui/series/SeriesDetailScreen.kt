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
import androidx.compose.material3.CircularProgressIndicator
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
import com.komgareader.domain.model.Book
import com.komgareader.domain.repository.ServerConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeriesDetailScreen(
    onBack: () -> Unit,
    onOpenBook: (bookId: String, pageCount: Int, format: String, forceStream: Boolean) -> Unit,
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
                    CircularProgressIndicator()
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
    localIds: Set<String>,
    downloadingIds: Set<String>,
    onOpenBook: (bookId: String, pageCount: Int, format: String, forceStream: Boolean) -> Unit,
    onDownload: (Book) -> Unit,
    onRemoveDownload: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedBook by rememberSaveable(books) { mutableStateOf(books.firstOrNull()?.remoteId) }
    var chaptersExpanded by rememberSaveable { mutableStateOf(true) }

    val currentBook = books.firstOrNull { it.remoteId == selectedBook } ?: books.firstOrNull()

    LazyColumn(modifier = modifier) {
        // Header-Karte: Cover links, Titel + Kapitelanzahl rechts
        item {
            SeriesHeaderCard(
                seriesTitle = seriesTitle,
                bookCount = books.size,
                seriesRemoteId = seriesRemoteId,
                serverConfig = serverConfig,
                modifier = Modifier.padding(12.dp),
            )
        }

        // Ausgewähltes Buch: Metadaten + Aktionen (Lesen + Download/Entfernen-Toggle)
        if (currentBook != null) {
            item {
                SelectedBookBlock(
                    book = currentBook,
                    isLocal = currentBook.remoteId in localIds,
                    isDownloading = currentBook.remoteId in downloadingIds,
                    onRead = { onOpenBook(currentBook.remoteId, currentBook.pageCount, currentBook.format.name, false) },
                    onDownload = { onDownload(currentBook) },
                    onRemoveDownload = { onRemoveDownload(currentBook.remoteId) },
                    modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp),
                )
            }
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

@Composable
private fun SeriesHeaderCard(
    seriesTitle: String,
    bookCount: Int,
    seriesRemoteId: String,
    serverConfig: ServerConfig?,
    modifier: Modifier = Modifier,
) {
    val s = LocalStrings.current
    val ctx = LocalContext.current
    val coverUrl = serverConfig?.let { "${it.baseUrl}series/$seriesRemoteId/thumbnail" }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .border(1.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Cover-Bild links
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
                    .width(72.dp)
                    .aspectRatio(2f / 3f),
            )
        }

        // Titel + Kapitelanzahl rechts
        Column(modifier = Modifier.weight(1f)) {
            Text(
                seriesTitle,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "$bookCount ${s.chapters}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SelectedBookBlock(
    book: Book,
    isLocal: Boolean,
    isDownloading: Boolean,
    onRead: () -> Unit,
    onDownload: () -> Unit,
    onRemoveDownload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = LocalStrings.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(1.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
            .padding(12.dp),
    ) {
        Text(book.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text("${book.pageCount} ${s.pagesShort}", style = MaterialTheme.typography.bodySmall)

        // Metadaten-Mini-Tabelle
        Spacer(Modifier.height(8.dp))
        MetaRow(label = s.formatLabel, value = book.format.name)
        if (book.sizeBytes > 0L) {
            MetaRow(label = s.sizeLabel, value = SeriesDetailViewModel.humanReadableSize(book.sizeBytes))
        }
        book.fileUrl?.let { url ->
            val fileName = url.substringAfterLast('/', url.substringAfterLast('\\', url))
            MetaRow(label = s.fileLabel, value = fileName.take(40))
        }
        book.createdDate?.let { MetaRow(label = s.createdLabel, value = it.take(10)) }
        book.modifiedDate?.let { MetaRow(label = s.modifiedLabel, value = it.take(10)) }

        // Aktionsschaltflächen: Lesen (primär) + Download/Entfernen-Toggle
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Primäre Aktion: Lesen (nutzt lokale Kopie wenn vorhanden, sonst Stream)
            Button(onClick = onRead, modifier = Modifier.weight(1f)) {
                Text(s.read, maxLines = 1)
            }

            // Download/Entfernen-Toggle
            when {
                isDownloading -> Box(
                    modifier = Modifier.size(48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                }
                isLocal -> OutlinedButton(
                    onClick = onRemoveDownload,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(s.removeDownload, maxLines = 1)
                }
                else -> OutlinedButton(
                    onClick = onDownload,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        Icons.Filled.CloudDownload,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(s.downloadShort, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun MetaRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(80.dp),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
        )
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
            Text(
                book.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            )
            Text(
                "${book.pageCount} ${s.pagesShort}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // Statusicon + Download/Entfernen-Aktion pro Zeile
        when {
            isDownloading -> CircularProgressIndicator(
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
