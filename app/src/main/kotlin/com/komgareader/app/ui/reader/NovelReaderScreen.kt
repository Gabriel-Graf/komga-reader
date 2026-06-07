package com.komgareader.app.ui.reader

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.komgareader.app.ui.components.FilteredReaderImage
import com.komgareader.app.ui.components.LoadingIndicator
import com.komgareader.eink.onyx.OnyxRefresher

/**
 * Roman-Reader (ViewerType.NOVEL): zeigt eine **reflowte** EPUB-Seite, die der
 * [NovelReaderViewModel] über die crengine-Engine zur Pixel-Größe des Viewports
 * umschichtet. Baut auf dem geteilten [ReaderScaffold] auf (Drittel-Tap-Zonen,
 * Overlay, Status-Fuß) — keine Parallel-Linie (siehe `shared-structure-before-variants`).
 *
 * **Keine Animation:** Der Seitenwechsel ist ein sofortiger State-Wechsel
 * (currentPage) + ein gezielter Full-Refresh über den [OnyxRefresher] gegen
 * Ghosting — wie der bewusste Bildwechsel im Comic-Reader (E-Ink, `animation-gating`).
 */
@Composable
fun NovelReaderScreen(
    onBack: () -> Unit,
    novelVm: NovelReaderViewModel = hiltViewModel(),
    refresher: OnyxRefresher? = null,
) {
    val state by novelVm.uiState.collectAsState()
    val rootView = LocalView.current

    // Reflow-Seitenwechsel ist ein bewusster Bildwechsel -> sofortiger GC-Full-Refresh
    // gegen Ghosting (No-Op auf Nicht-Boox).
    LaunchedEffect(state.currentPage) {
        novelVm.onPageSettled(state.currentPage)
        refresher?.fullRefreshNow(rootView)
    }

    ReaderScaffold(
        chrome = novelVm,
        title = "${state.currentPage + 1} / ${state.pageCount.coerceAtLeast(1)}",
        onBack = onBack,
        onPrev = novelVm::prevPage,
        onNext = novelVm::nextPage,
        background = Color.White,
        footer = {
            Box(Modifier.fillMaxSize()) {
                Text(
                    text = "${state.currentPage + 1} / ${state.pageCount.coerceAtLeast(1)}",
                    color = Color.Black,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp)
                        .background(Color.LightGray.copy(alpha = 0.6f))
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }
        },
    ) {
        // Der Viewport gibt erst hier seine Pixel-Größe her: damit öffnet das VM das
        // EPUB und schichtet es passend um (idempotent).
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val viewportW = constraints.maxWidth
            val viewportH = constraints.maxHeight
            LaunchedEffect(viewportW, viewportH) {
                novelVm.open(viewportW, viewportH)
            }

            when {
                state.error != null -> Box(
                    Modifier.fillMaxSize().background(Color.White),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(state.error!!, color = Color.Black)
                }
                state.loading -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    LoadingIndicator()
                }
                else -> {
                    val bmp by produceState<Bitmap?>(initialValue = null, key1 = state.currentPage) {
                        value = novelVm.renderPage(state.currentPage)
                    }
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        if (bmp != null) {
                            FilteredReaderImage(
                                bitmap = bmp!!,
                                contentDescription = "Seite ${state.currentPage + 1}",
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
    }
}
