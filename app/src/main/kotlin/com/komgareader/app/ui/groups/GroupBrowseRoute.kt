package com.komgareader.app.ui.groups

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import com.komgareader.app.ui.components.LoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.komgareader.domain.model.Series
import com.komgareader.domain.repository.ServerConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupBrowseRoute(
    shelfId: Long,
    onBack: () -> Unit,
    onOpenSeries: (seriesId: String) -> Unit,
    viewModel: GroupBrowseViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val localSeriesIds by viewModel.localSeriesIds.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val title = when (val s = state) {
                        is GroupBrowseUiState.Content -> s.shelf.name
                        else -> "Gruppe"
                    }
                    Text(title)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
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
                        Text(current.message, textAlign = TextAlign.Center, modifier = Modifier.padding(16.dp))
                        Button(onClick = { viewModel.refresh() }) {
                            Text("Wiederholen")
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
                        GroupSeriesCover(
                            series = series,
                            serverConfig = current.serverConfig,
                            isLocal = series.remoteId in localSeriesIds,
                            onClick = { onOpenSeries(series.remoteId) },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GroupSeriesCover(
    series: Series,
    serverConfig: ServerConfig?,
    isLocal: Boolean,
    onClick: () -> Unit,
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
            .combinedClickable(onClick = onClick),
    ) {
        AsyncImage(
            model = request,
            contentDescription = series.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
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
                // Lokal komplett vorhanden → Download-Logo; sonst Cloud (nur online verfügbar).
                if (isLocal) Icons.Filled.DownloadDone else Icons.Filled.CloudQueue,
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
