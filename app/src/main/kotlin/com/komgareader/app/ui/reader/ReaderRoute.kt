package com.komgareader.app.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.hilt.navigation.compose.hiltViewModel
import com.komgareader.eink.onyx.OnyxRefresher

/**
 * Reader-Host: holt den ReaderViewModel und wählt je nach ReaderContent
 * zwischen EpubReaderScreen, PagedReaderScreen und WebtoonReaderScreen.
 *
 * Aktiviert außerdem den E-Ink-Schnell-Modus (A2/DW) für Onyx-Geräte
 * und stellt ihn beim Verlassen wieder her (No-Op auf Nicht-Boox).
 */
@Composable
fun ReaderRoute(
    onBack: () -> Unit,
    viewModel: ReaderViewModel = hiltViewModel(),
    einkHolder: ReaderEinkHolder = hiltViewModel(),
) {
    val refresher: OnyxRefresher = einkHolder.refresher

    // E-Ink-Fast-Modus aktivieren, beim Verlassen deaktivieren
    EinkReaderEffect(refresher)

    val content by viewModel.content.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val mode by viewModel.viewerMode.collectAsState()

    when (val c = content) {
        is ReaderContent.Loading -> {
            Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White)
            }
        }
        is ReaderContent.Error -> {
            Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                Text(c.message, color = Color.White)
            }
        }
        is ReaderContent.Rendered -> {
            EpubReaderScreen(
                pageCount = c.pageCount,
                initialPage = c.initialPage,
                onBack = onBack,
                viewModel = viewModel,
            )
        }
        is ReaderContent.Streamed -> {
            when (mode) {
                ViewerMode.PAGED -> PagedReaderScreen(
                    pages = c.pages,
                    authHeaders = c.authHeaders,
                    initialPage = c.initialPage,
                    onBack = onBack,
                    onToggleMode = viewModel::toggleViewerMode,
                    viewModel = viewModel,
                    refresher = refresher,
                )
                ViewerMode.WEBTOON -> WebtoonReaderScreen(
                    pages = c.pages,
                    authHeaders = c.authHeaders,
                    initialPage = c.initialPage,
                    chromeVisible = uiState.chromeVisible,
                    onToggleChrome = viewModel::toggleChrome,
                    onBack = onBack,
                    onPageVisible = viewModel::onPageSettled,
                    onToggleMode = viewModel::toggleViewerMode,
                    refresher = refresher,
                )
            }
        }
    }
}
