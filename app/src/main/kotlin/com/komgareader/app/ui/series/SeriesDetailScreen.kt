package com.komgareader.app.ui.series

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.komgareader.domain.model.Book

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeriesDetailScreen(
    onBack: () -> Unit,
    onOpenBook: (bookId: String, pageCount: Int, format: String) -> Unit,
    viewModel: SeriesDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val localIds by viewModel.localBookIds.collectAsState()
    val downloadingIds by viewModel.downloadingIds.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val title = if (state is SeriesDetailUiState.Content) {
                        (state as SeriesDetailUiState.Content).seriesTitle
                    } else {
                        "Serie"
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
                LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                    items(current.books) { book ->
                        BookRow(
                            book = book,
                            isLocal = book.remoteId in localIds,
                            isDownloading = book.remoteId in downloadingIds,
                            onClick = { onOpenBook(book.remoteId, book.pageCount, book.format.name) },
                            onDownload = { viewModel.download(book) },
                            onRemoveDownload = { viewModel.removeDownload(book.remoteId) },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun BookRow(
    book: Book,
    isLocal: Boolean,
    isDownloading: Boolean,
    onClick: () -> Unit,
    onDownload: () -> Unit,
    onRemoveDownload: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(book.title, style = MaterialTheme.typography.bodyLarge)
            Text("${book.pageCount} Seiten · ${book.format.name}", style = MaterialTheme.typography.bodySmall)
        }
        when {
            isDownloading -> CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            isLocal -> IconButton(onClick = onRemoveDownload) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = "Lokal gespeichert – tippen zum Löschen",
                    tint = Color(0xFF4CAF50),
                )
            }
            else -> IconButton(onClick = onDownload) {
                Icon(Icons.Filled.CloudDownload, contentDescription = "Herunterladen")
            }
        }
    }
}
