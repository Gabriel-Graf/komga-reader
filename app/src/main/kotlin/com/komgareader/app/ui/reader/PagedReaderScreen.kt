package com.komgareader.app.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ViewDay
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.komgareader.app.ui.components.FilteredAsyncImage
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
    val uiState by viewModel.uiState.collectAsState()
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

    Box(Modifier.fillMaxSize().background(Color.Black)) {
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
            FilteredAsyncImage(
                model = request,
                contentDescription = "Seite ${pageIndex + 1}",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Transparente Tap-Zonen über dem Pager
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(pageCount) {
                    detectTapGestures { offset ->
                        val width = size.width.toFloat()
                        when {
                            offset.x < width / 3f -> {
                                val prev = (pagerState.currentPage - 1).coerceAtLeast(0)
                                viewModel.navigateTo(prev)
                            }
                            offset.x > width * 2f / 3f -> {
                                val next = (pagerState.currentPage + 1).coerceAtMost(pageCount - 1)
                                viewModel.navigateTo(next)
                            }
                            else -> viewModel.toggleChrome()
                        }
                    }
                },
        )

        ReaderChromeOverlay(
            visible = uiState.chromeVisible,
            title = "${pagerState.currentPage + 1} / $pageCount",
            onBack = onBack,
            actions = {
                IconButton(onClick = onToggleMode) {
                    Icon(Icons.Filled.ViewDay, contentDescription = "Zu Webtoon-Modus wechseln", tint = Color.White)
                }
            },
        )

        if (uiState.chromeVisible) {
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
    }
}
