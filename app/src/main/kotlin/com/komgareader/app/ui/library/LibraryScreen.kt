package com.komgareader.app.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.komgareader.app.ui.components.LoadingIndicator
import com.komgareader.app.ui.components.LocalContentBottomInset
import com.komgareader.app.ui.components.SeriesTile
import com.komgareader.app.i18n.LocalStrings
import com.komgareader.domain.model.ContentType
import com.komgareader.domain.model.Series

@Composable
fun LibraryScreen(
    query: String = "",
    typeFilter: Set<ContentType> = emptySet(),
    downloadedOnly: Boolean = false,
    onOpenSeries: (seriesId: String, sourceId: Long) -> Unit = { _, _ -> },
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
            typeFilter = typeFilter,
            downloadedOnly = downloadedOnly,
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
    typeFilter: Set<ContentType>,
    downloadedOnly: Boolean,
    localSeriesIds: Set<String>,
    onOpenSeries: (seriesId: String, sourceId: Long) -> Unit,
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
            val shown = remember(current.series, query, typeFilter, downloadedOnly, current.effectiveTypes, localSeriesIds) {
                filterSeries(
                    current.series, query, typeFilter,
                    downloadedOnly = downloadedOnly,
                    typeOf = { current.effectiveTypes[it.remoteId] },
                    isDownloaded = { it.remoteId in localSeriesIds },
                )
            }
            if (shown.isEmpty() && typeFilter.isNotEmpty()) {
                Box(modifier, contentAlignment = Alignment.Center) {
                    Text(
                        s.filterTypePlaceholder,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(32.dp),
                    )
                }
            } else if (shown.isEmpty() && downloadedOnly) {
                Box(modifier, contentAlignment = Alignment.Center) {
                    Text(s.filterDownloadedEmpty, textAlign = TextAlign.Center, modifier = Modifier.padding(32.dp))
                }
            } else if (shown.isEmpty() && query.isNotBlank()) {
                Box(modifier, contentAlignment = Alignment.Center) {
                    Text(s.searchNoResults, textAlign = TextAlign.Center, modifier = Modifier.padding(32.dp))
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    // Oben kein Padding — Cover schließen direkt an die TopBar an. Unten der von der
                    // überlagernden Menubar reservierte Freiraum, damit die letzte Reihe frei steht;
                    // das Grid selbst füllt bis zur Unterkante und scheint hinter der Bar durch.
                    modifier = modifier.padding(horizontal = 4.dp),
                    contentPadding = PaddingValues(bottom = LocalContentBottomInset.current + 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(shown) { series ->
                        SeriesTile(
                            series = series,
                            isLocal = series.remoteId in localSeriesIds,
                            onClick = { onOpenSeries(series.remoteId, series.sourceId) },
                            onLongClick = { onDownload(series) },
                        )
                    }
                }
            }
        }
    }
}

