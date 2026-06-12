package com.komgareader.app.ui.groups

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.komgareader.app.i18n.LocalStrings
import com.komgareader.app.ui.common.UiErrorText
import com.komgareader.ui.slots.DetailScaffoldState
import com.komgareader.app.ui.components.LoadingIndicator
import com.komgareader.app.ui.components.SeriesTile
import com.komgareader.ui.icons.AppIcons
import com.komgareader.ui.slots.LocalResolvedSlots

@Composable
fun GroupBrowseRoute(
    shelfId: Long,
    onBack: () -> Unit,
    onOpenSeries: (seriesId: String, sourceId: Long) -> Unit,
    viewModel: GroupBrowseViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val localSeriesIds by viewModel.localSeriesIds.collectAsState()

    val title = when (val s = state) {
        is GroupBrowseUiState.Content -> s.shelf.name
        else -> "Gruppe"
    }
    // Detail-Gerüst über die Slot-Naht (austauschbar durch UI-Packs): Scaffold + Header + Body.
    // Kein Snackbar. Header-Aktion: Aktualisieren.
    LocalResolvedSlots.current.detail(
        DetailScaffoldState(
            title = title,
            onBack = onBack,
            actions = {
                IconButton(onClick = { viewModel.refresh() }) {
                    Icon(AppIcons.Refresh, contentDescription = null)
                }
            },
            content = { padding ->
                when (val current = state) {
                    is GroupBrowseUiState.Loading -> {
                        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                            LoadingIndicator()
                        }
                    }
                    is GroupBrowseUiState.NoServer -> {
                        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                            Text("Kein Server verbunden.", textAlign = TextAlign.Center)
                        }
                    }
                    is GroupBrowseUiState.Error -> {
                        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                UiErrorText(current.error, modifier = Modifier.padding(16.dp))
                                Button(onClick = { viewModel.refresh() }) {
                                    Text(LocalStrings.current.retry)
                                }
                            }
                        }
                    }
                    is GroupBrowseUiState.Content -> {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            modifier = Modifier.fillMaxSize().padding(padding).padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            items(current.series) { series ->
                                SeriesTile(
                                    series = series,
                                    isLocal = series.remoteId in localSeriesIds,
                                    onClick = { onOpenSeries(series.remoteId, series.sourceId) },
                                    onLongClick = {},
                                )
                            }
                        }
                    }
                }
            },
        ),
    )
}

