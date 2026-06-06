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
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.komgareader.app.ui.groups.GroupsScreen
import com.komgareader.domain.model.Series
import com.komgareader.domain.repository.ServerConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onOpenSettings: () -> Unit,
    onOpenSeries: (seriesId: String) -> Unit = {},
    onOpenGroup: (shelfId: Long, serverSourceId: Long) -> Unit = { _, _ -> },
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val s = LocalStrings.current
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

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
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(s.libraryTitle) },
                    actions = {
                        if (selectedTab == 0) {
                            IconButton(onClick = { viewModel.refresh() }) {
                                Icon(Icons.Filled.Refresh, contentDescription = null)
                            }
                        }
                        IconButton(onClick = onOpenSettings) {
                            Icon(Icons.Filled.Settings, contentDescription = s.settingsTitle)
                        }
                    },
                )
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text(s.tabBrowse) },
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text(s.tabGroups) },
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when (selectedTab) {
            0 -> BrowseTab(
                state = state,
                onOpenSeries = onOpenSeries,
                onRefresh = viewModel::refresh,
                onDownload = viewModel::downloadSeries,
                modifier = Modifier.fillMaxSize().padding(padding),
            )
            else -> GroupsScreen(
                onOpenGroup = onOpenGroup,
                modifier = Modifier.fillMaxSize().padding(padding),
            )
        }
    }
}

@Composable
private fun BrowseTab(
    state: LibraryUiState,
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
                CircularProgressIndicator()
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
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = modifier.padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(current.series) { series ->
                    SeriesCover(
                        series = series,
                        serverConfig = current.serverConfig,
                        onClick = { onOpenSeries(series.remoteId) },
                        onLongClick = { onDownload(series) },
                    )
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
                Icons.Filled.CloudQueue,
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
