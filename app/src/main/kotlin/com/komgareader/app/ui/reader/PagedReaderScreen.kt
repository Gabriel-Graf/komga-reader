package com.komgareader.app.ui.reader

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.hilt.navigation.compose.hiltViewModel
import com.komgareader.app.data.coil.SourceImage
import com.komgareader.app.ui.components.FilteredReaderAsyncImage
import coil.request.ImageRequest
import com.komgareader.domain.eink.RefreshMode
import com.komgareader.eink.onyx.OnyxRefresher

@Composable
fun PagedReaderScreen(
    pages: List<SourceImage>,
    initialPage: Int,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onSettings: () -> Unit,
    onToggleMode: () -> Unit = {},
    viewModel: ReaderViewModel = hiltViewModel(),
    refresher: OnyxRefresher? = null,
) {
    val requestedPage by viewModel.requestedPage.collectAsState()
    val ctx = LocalContext.current
    val rootView = LocalView.current

    val pageCount = pages.size
    if (pageCount == 0) return

    val pagerState = rememberPagerState(initialPage = initialPage) { pageCount }

    // Button-Navigation: angeforderte Seite vom ViewModel scrollen
    LaunchedEffect(requestedPage) {
        if (requestedPage >= 0 && requestedPage != pagerState.currentPage) {
            pagerState.animateScrollToPage(requestedPage)
        }
    }

    // Fortschritt pushen wenn Seite gesettled + Refresh-Entscheidung über den geteilten Scheduler
    LaunchedEffect(pagerState.currentPage) {
        viewModel.onPageSettled(pagerState.currentPage)
        // Event-gezählte FULL-Promotion gegen Ghosting (nicht Index-Modulo); nur Boox refresht.
        if (refresher != null && viewModel.refreshScheduler.onContentChange() == RefreshMode.FULL) {
            refresher.fullRefreshNow(rootView)
        }
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
            FilteredReaderAsyncImage(
                model = request,
                contentDescription = "Seite ${pageIndex + 1}",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
