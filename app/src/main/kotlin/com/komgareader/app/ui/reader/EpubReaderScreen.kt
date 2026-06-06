package com.komgareader.app.ui.reader

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun EpubReaderScreen(
    pageCount: Int,
    initialPage: Int,
    onBack: () -> Unit,
    viewModel: ReaderViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
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

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.White),
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
                    Image(
                        bitmap = bmp!!.asImageBitmap(),
                        contentDescription = "Seite ${pageIndex + 1}",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    CircularProgressIndicator()
                }
            }
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
        )

        if (uiState.chromeVisible) {
            Text(
                text = "${pagerState.currentPage + 1} / $pageCount",
                color = Color.Black,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp)
                    .background(Color.LightGray.copy(alpha = 0.6f))
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
    }
}
