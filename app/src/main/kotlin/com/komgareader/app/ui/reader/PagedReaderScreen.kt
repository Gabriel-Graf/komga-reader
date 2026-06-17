package com.komgareader.app.ui.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImagePainter
import com.komgareader.app.data.coil.SourceImage
import com.komgareader.app.i18n.LocalStrings
import com.komgareader.app.ui.components.FilteredReaderAsyncImage
import com.komgareader.ui.icons.AppIcons
import coil.request.ImageRequest
import com.komgareader.domain.model.ReaderKind

@Composable
fun PagedReaderScreen(
    pages: List<SourceImage>,
    initialPage: Int,
    readerKind: ReaderKind,
    bookRemoteId: String,
    sourceId: Long,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onSettings: () -> Unit,
    onToggleMode: () -> Unit = {},
    viewModel: ReaderViewModel = hiltViewModel(),
) {
    val requestedPage by viewModel.requestedPage.collectAsState()
    val ctx = LocalContext.current

    val pageCount = pages.size
    if (pageCount == 0) return

    val pagerState = rememberPagerState(initialPage = initialPage) { pageCount }

    ReadingSessionEffect(readerKind, bookRemoteId, sourceId, pagerState.currentPage)

    // Button-Navigation: angeforderte Seite vom ViewModel scrollen
    LaunchedEffect(requestedPage) {
        if (requestedPage >= 0 && requestedPage != pagerState.currentPage) {
            pagerState.animateScrollToPage(requestedPage)
        }
    }

    // Report page settle for progress tracking
    LaunchedEffect(pagerState.currentPage) {
        viewModel.onPageSettled(pagerState.currentPage)
    }

    ReaderScaffold(
        modifier = Modifier.testTag("reader_paged"),
        chrome = viewModel,
        title = "${pagerState.currentPage + 1} / $pageCount",
        onBack = onBack,
        onHome = onHome,
        onSettings = onSettings,
        onPrev = { viewModel.navigateTo((pagerState.currentPage - 1).coerceAtLeast(0)) },
        onNext = { viewModel.navigateTo((pagerState.currentPage + 1).coerceAtMost(pageCount - 1)) },
        actions = { ReaderModeAction(onToggleMode, "Zu Webtoon-Modus wechseln") },
        footer = { ReaderStatusBar("${pagerState.currentPage + 1} / $pageCount", dark = true) },
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { pageIndex ->
            val pageImage = pages[pageIndex]
            val request = remember(pageImage) {
                ImageRequest.Builder(ctx)
                    .data(pageImage)
                    .crossfade(false)
                    .build()
            }
            // Track the load result so a failed page (e.g. server 500) shows a message
            // instead of a silent black screen.
            var failed by remember(pageImage) { mutableStateOf(false) }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                FilteredReaderAsyncImage(
                    model = request,
                    contentDescription = "Seite ${pageIndex + 1}",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                    onState = { failed = it is AsyncImagePainter.State.Error },
                )
                if (failed) ReaderPageError()
            }
        }
    }
}

/**
 * Centered failure message shown over the (black) reader background when a page image cannot
 * be loaded — most often a server-side error. Static (no motion) so it is E-Ink-safe; light
 * foreground for contrast on the dark reader surface.
 */
@Composable
private fun ReaderPageError() {
    val s = LocalStrings.current
    Column(
        modifier = Modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = AppIcons.Info,
            contentDescription = null,
            tint = Color.White,
        )
        Text(
            text = s.readerPageError,
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}
