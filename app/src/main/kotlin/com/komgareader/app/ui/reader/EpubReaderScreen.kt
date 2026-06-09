package com.komgareader.app.ui.reader

import android.graphics.Bitmap
import com.komgareader.app.ui.components.FilteredReaderImage
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import com.komgareader.app.ui.components.LoadingIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun EpubReaderScreen(
    pageCount: Int,
    initialPage: Int,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onSettings: () -> Unit,
    viewModel: ReaderViewModel = hiltViewModel(),
) {
    val requestedPage by viewModel.requestedPage.collectAsState()

    val pagerState = rememberPagerState(initialPage = initialPage) { pageCount }

    // Button-Navigation: angeforderte Seite vom ViewModel scrollen
    LaunchedEffect(requestedPage) {
        if (requestedPage >= 0 && requestedPage != pagerState.currentPage) {
            pagerState.animateScrollToPage(requestedPage)
        }
    }

    // Fortschritt pushen wenn Seite gesettled
    LaunchedEffect(pagerState.currentPage) {
        viewModel.onPageSettled(pagerState.currentPage)
    }

    ReaderScaffold(
        chrome = viewModel,
        title = "${pagerState.currentPage + 1} / $pageCount",
        onBack = onBack,
        onHome = onHome,
        onSettings = onSettings,
        onPrev = { viewModel.navigateTo((pagerState.currentPage - 1).coerceAtLeast(0)) },
        onNext = { viewModel.navigateTo((pagerState.currentPage + 1).coerceAtMost(pageCount - 1)) },
        background = Color.White,
        footer = { ReaderStatusBar("${pagerState.currentPage + 1} / $pageCount", dark = false) },
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { pageIndex ->
            val bmp by produceState<Bitmap?>(initialValue = null, key1 = pageIndex) {
                value = viewModel.renderEpubPage(pageIndex)
            }
            Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                if (bmp != null) {
                    FilteredReaderImage(
                        bitmap = bmp!!,
                        contentDescription = "Seite ${pageIndex + 1}",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    LoadingIndicator()
                }
            }
        }
    }
}
