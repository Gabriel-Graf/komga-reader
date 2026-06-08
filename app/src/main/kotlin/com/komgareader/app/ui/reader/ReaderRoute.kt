package com.komgareader.app.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import com.komgareader.app.ui.components.LoadingIndicator
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
 * zwischen NovelReaderScreen (EPUB-Reflow), dem MuPDF-Reader für lokale Downloads
 * (EpubReaderScreen), PagedReaderScreen, WebtoonReaderScreen und ComicReaderScreen.
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

    // Vollbild wird app-weit in MainActivity erzwungen — kein eigener Reader-Effekt nötig.

    // E-Ink-Fast-Modus aktivieren, beim Verlassen deaktivieren
    EinkReaderEffect(refresher)

    val content by viewModel.content.collectAsState()
    val mode by viewModel.viewerMode.collectAsState()
    val displayMode by viewModel.displayMode.collectAsState()

    when (val c = content) {
        is ReaderContent.Loading -> {
            Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                LoadingIndicator(onDark = true)
            }
        }
        is ReaderContent.Error -> {
            Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                Text(c.message, color = Color.White)
            }
        }
        is ReaderContent.Novel -> {
            NovelReaderScreen(
                onBack = onBack,
                refresher = refresher,
            )
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
                    initialPage = c.initialPage,
                    onBack = onBack,
                    onToggleMode = viewModel::toggleViewerMode,
                    viewModel = viewModel,
                    refresher = refresher,
                )
                ViewerMode.WEBTOON -> WebtoonReaderScreen(
                    pages = c.pages,
                    initialPage = c.initialPage,
                    displayMode = displayMode,
                    frameSteps = viewModel.frameStep,
                    chrome = viewModel,
                    refreshScheduler = viewModel.refreshScheduler,
                    onBack = onBack,
                    onPageVisible = viewModel::onPageSettled,
                    onToggleMode = viewModel::toggleViewerMode,
                    refresher = refresher,
                )
                ViewerMode.COMIC -> ComicReaderScreen(
                    pages = c.pages,
                    initialPage = c.initialPage,
                    onBack = onBack,
                    onToggleMode = viewModel::toggleViewerMode,
                    refresher = refresher,
                )
            }
        }
        is ReaderContent.Webtoon -> {
            when (mode) {
                ViewerMode.PAGED -> PagedReaderScreen(
                    pages = c.pages,
                    initialPage = c.initialPage,
                    onBack = onBack,
                    onToggleMode = viewModel::toggleViewerMode,
                    viewModel = viewModel,
                    refresher = refresher,
                )
                ViewerMode.WEBTOON -> WebtoonReaderScreen(
                    pages = c.pages,
                    initialPage = c.initialPage,
                    displayMode = displayMode,
                    frameSteps = viewModel.frameStep,
                    chrome = viewModel,
                    refreshScheduler = viewModel.refreshScheduler,
                    onBack = onBack,
                    onPageVisible = viewModel::onPageSettled,
                    onToggleMode = viewModel::toggleViewerMode,
                    refresher = refresher,
                )
                ViewerMode.COMIC -> ComicReaderScreen(
                    pages = c.pages,
                    initialPage = c.initialPage,
                    onBack = onBack,
                    onToggleMode = viewModel::toggleViewerMode,
                    refresher = refresher,
                )
            }
        }
    }
}
