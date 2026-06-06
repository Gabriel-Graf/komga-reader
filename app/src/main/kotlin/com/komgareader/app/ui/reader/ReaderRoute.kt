package com.komgareader.app.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * Reader-Host: holt den ReaderViewModel und wählt je nach ViewerMode
 * zwischen PagedReaderScreen und WebtoonReaderScreen.
 */
@Composable
fun ReaderRoute(
    onBack: () -> Unit,
    viewModel: ReaderViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val mode by viewModel.viewerMode.collectAsState()

    if (state.isLoading) {
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            Text("Lade Seiten…", color = Color.White)
        }
        return
    }

    if (state.error != null) {
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            Text(state.error!!, color = Color.White)
        }
        return
    }

    when (mode) {
        ViewerMode.PAGED -> PagedReaderScreen(
            onBack = onBack,
            onToggleMode = viewModel::toggleViewerMode,
            viewModel = viewModel,
        )
        ViewerMode.WEBTOON -> WebtoonReaderScreen(
            pages = state.pages,
            apiKey = state.apiKey,
            initialPage = state.initialPage,
            chromeVisible = state.chromeVisible,
            onToggleChrome = viewModel::toggleChrome,
            onBack = onBack,
            onPageVisible = viewModel::onPageSettled,
            onToggleMode = viewModel::toggleViewerMode,
        )
    }
}
