package com.komgareader.app.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.komgareader.app.ui.components.FilteredReaderAsyncImage
import coil.request.ImageRequest
import com.komgareader.eink.onyx.OnyxRefresher

@Composable
fun PagedReaderScreen(
    pages: List<com.komgareader.domain.source.PageRef>,
    authHeaders: Map<String, String>,
    initialPage: Int,
    onBack: () -> Unit,
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

    // Fortschritt pushen wenn Seite gesettled + periodischen GC-Refresh auslösen
    LaunchedEffect(pagerState.currentPage) {
        viewModel.onPageSettled(pagerState.currentPage)
        // Alle N Seitenumbrüche: GC-Full-Refresh gegen Ghosting (nur Boox)
        if (refresher != null && triggerGhostClearIfNeeded(pagerState.currentPage, refresher)) {
            refresher.fullRefreshIfNeeded(
                view = rootView,
                pagesSinceLastRefresh = OnyxRefresher.GHOST_CLEAR_INTERVAL,
            )
        }
    }

    ReaderScaffold(
        chrome = viewModel,
        title = "${pagerState.currentPage + 1} / $pageCount",
        onBack = onBack,
        onPrev = { viewModel.navigateTo((pagerState.currentPage - 1).coerceAtLeast(0)) },
        onNext = { viewModel.navigateTo((pagerState.currentPage + 1).coerceAtMost(pageCount - 1)) },
        actions = { ReaderModeAction(onToggleMode, "Zu Webtoon-Modus wechseln") },
        footer = {
            Box(Modifier.fillMaxSize()) {
                Text(
                    text = "${pagerState.currentPage + 1} / $pageCount",
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp)
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }
        },
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { pageIndex ->
            val pageRef = pages[pageIndex]
            val request = remember(pageRef.url, authHeaders) {
                ImageRequest.Builder(ctx)
                    .data(pageRef.url)
                    .apply { authHeaders.forEach { addHeader(it.key, it.value) } }
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
