package com.komgareader.app.ui.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.DownloadDone
import androidx.compose.material3.Button
import com.komgareader.app.ui.components.LoadingIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.komgareader.app.data.AuthHeaders
import com.komgareader.app.i18n.LocalStrings
import com.komgareader.domain.model.Series
import com.komgareader.domain.repository.ServerConfig

@Composable
fun LibraryScreen(
    query: String = "",
    onOpenSeries: (seriesId: String) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val localSeriesIds by viewModel.localSeriesIds.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            val message = when (event) {
                is LibraryEvent.DownloadStarted -> "Lade ${event.count} Kapitel…"
                is LibraryEvent.DownloadComplete -> "Serie heruntergeladen."
                is LibraryEvent.DownloadError -> "Fehler: ${event.message}"
            }
            snackbarHostState.showSnackbar(message)
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        BrowseTab(
            state = state,
            query = query,
            localSeriesIds = localSeriesIds,
            onOpenSeries = onOpenSeries,
            onRefresh = viewModel::refresh,
            onDownload = viewModel::downloadSeries,
            modifier = Modifier.fillMaxSize().padding(padding),
        )
    }
}

@Composable
private fun BrowseTab(
    state: LibraryUiState,
    query: String,
    localSeriesIds: Set<String>,
    onOpenSeries: (seriesId: String) -> Unit,
    onRefresh: () -> Unit,
    onDownload: (Series) -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = LocalStrings.current
    when (val current = state) {
        is LibraryUiState.NoServer -> {
            Box(modifier, contentAlignment = Alignment.Center) {
                Text(s.libraryEmpty, textAlign = TextAlign.Center, modifier = Modifier.padding(32.dp))
            }
        }
        is LibraryUiState.Loading -> {
            Box(modifier, contentAlignment = Alignment.Center) {
                LoadingIndicator()
            }
        }
        is LibraryUiState.Error -> {
            Box(modifier, contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(current.message, textAlign = TextAlign.Center, modifier = Modifier.padding(16.dp))
                    Button(onClick = onRefresh) {
                        Text("Wiederholen")
                    }
                }
            }
        }
        is LibraryUiState.Content -> {
            val shown = remember(current.series, query) {
                if (query.isBlank()) current.series
                else current.series.filter { it.title.contains(query, ignoreCase = true) }
            }
            if (shown.isEmpty() && query.isNotBlank()) {
                Box(modifier, contentAlignment = Alignment.Center) {
                    Text(s.searchNoResults, textAlign = TextAlign.Center, modifier = Modifier.padding(32.dp))
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    // Oben kein Padding — Cover schließen direkt an die TopBar an.
                    modifier = modifier.padding(start = 4.dp, end = 4.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(shown) { series ->
                        SeriesCover(
                            series = series,
                            serverConfig = current.serverConfig,
                            isLocal = series.remoteId in localSeriesIds,
                            onClick = { onOpenSeries(series.remoteId) },
                            onLongClick = { onDownload(series) },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SeriesCover(
    series: Series,
    serverConfig: ServerConfig?,
    isLocal: Boolean = false,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
) {
    val ctx = LocalContext.current
    val headers = remember(serverConfig) { AuthHeaders.forCovers(serverConfig) }
    val request = remember(series.coverUrl, serverConfig) {
        ImageRequest.Builder(ctx).data(series.coverUrl)
            .apply { headers.forEach { addHeader(it.key, it.value) } }
            .crossfade(false).build()
    }
    Box(
        Modifier
            .aspectRatio(2f / 3f)
            .clip(RoundedCornerShape(4.dp))
            .border(1.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        AsyncImage(
            model = request,
            contentDescription = series.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )

        // Cloud-Badge mit opaquem Hintergrund (damit auf jedem Cover sichtbar)
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .size(24.dp)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f), CircleShape)
                .padding(3.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                // Lokal vorhanden → Download-Logo; sonst Cloud (nur online verfügbar).
                if (isLocal) Icons.Outlined.DownloadDone else Icons.Outlined.CloudQueue,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }

        Text(
            series.title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.7f))
                .padding(2.dp),
            color = Color.White,
            fontSize = 10.sp,
        )
    }
}
