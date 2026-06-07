package com.komgareader.app.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.ViewDay
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.komgareader.app.i18n.LocalStrings
import com.komgareader.eink.onyx.OnyxRefresher
import com.komgareader.guidedview.PanelGeometry

/**
 * Geführter Comic-Reader (ViewerMode.COMIC): zeigt die volle Seite über einen
 * Coil-Pager und zoomt im geführten Modus per [graphicsLayer] auf einzelne
 * Panels. Navigation erfolgt in 7a ausschließlich über Tap-Zonen; die
 * Hardware-Tasten-Steuerung folgt separat in 7b.
 *
 * Der [ComicReaderViewModel] ist die einzige Wahrheitsquelle für die sichtbare
 * Seite (`state.position.page`) und den Zoom-Zustand.
 */
@Composable
fun ComicReaderScreen(
    pages: List<com.komgareader.domain.source.PageRef>,
    authHeaders: Map<String, String>,
    initialPage: Int,
    onBack: () -> Unit,
    onToggleMode: () -> Unit = {},
    comicVm: ComicReaderViewModel = hiltViewModel(),
    refresher: OnyxRefresher? = null,
) {
    val ctx = LocalContext.current
    val rootView = LocalView.current
    val pageCount = pages.size
    if (pageCount == 0) return

    val s = LocalStrings.current

    LaunchedEffect(Unit) {
        comicVm.init(pages.map { it.url }, authHeaders, initialPage)
    }

    val state by comicVm.uiState.collectAsState()
    val pagerState = rememberPagerState(initialPage = initialPage) { pageCount }

    // Pager-Settle (z.B. manuelles Wischen) -> ViewModel informieren
    LaunchedEffect(pagerState.currentPage) { comicVm.onPageSettled(pagerState.currentPage) }

    // INTEGRATION 1: position.page -> Pager synchronisieren.
    // Panel-Navigation (next()/previous()) kann Seitengrenzen überschreiten und
    // damit position.page ändern; der Pager muss nachziehen, sonst zeigt er das
    // falsche Seitenbild unter dem Zoom. Die Rückkopplung ist stabil:
    // scrollToPage -> onPageSettled(page) == position.page -> early-return im VM.
    LaunchedEffect(state.position.page) {
        if (state.position.page != pagerState.currentPage) {
            pagerState.scrollToPage(state.position.page)
        }
    }

    // INTEGRATION 2: E-Ink-Full-Refresh bei Panel-/Zoom-Wechsel (Spec §5).
    // Ein Panel- oder Zoom-Wechsel ist ein bewusster Bildwechsel -> sofortiger
    // GC-Full-Refresh gegen Ghosting, analog zum Webtoon-Frame-Sprung.
    // Null-safe: No-Op auf Nicht-Boox-Geräten.
    LaunchedEffect(state.position, state.zoomed) {
        refresher?.fullRefreshNow(rootView)
    }

    val marginFraction = 0.05f

    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        // Bei ContentScale.Fit wird die Seite ins Viewport eingepasst und seitlich
        // bzw. oben/unten mit Letterbox-Balken zentriert. Tap-Normalisierung und
        // Panel-Pivot müssen gegen dieses tatsächlich dargestellte Content-Rechteck
        // rechnen, nicht gegen das volle Viewport.
        val vw = constraints.maxWidth.toFloat()
        val vh = constraints.maxHeight.toFloat()
        val aspect = if (state.pageAspect > 0f) state.pageAspect else vw / vh
        val contentW: Float
        val contentH: Float
        if (aspect < vw / vh) {
            contentH = vh; contentW = vh * aspect
        } else {
            contentW = vw; contentH = vw / aspect
        }
        val offX = (vw - contentW) / 2f
        val offY = (vh - contentH) / 2f

        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { pageIndex ->
            val pageRef = pages[pageIndex]
            val request = remember(pageRef.url, authHeaders) {
                ImageRequest.Builder(ctx).data(pageRef.url)
                    .apply { authHeaders.forEach { addHeader(it.key, it.value) } }
                    .crossfade(false).build()
            }
            val isCurrent = pageIndex == state.position.page
            val panel = if (isCurrent && state.zoomed) state.currentPanels.getOrNull(state.position.unit) else null
            val mod = if (panel != null) {
                val scale = PanelGeometry.fitScale(panel, contentW, contentH, vw, vh, marginFraction = marginFraction)
                val pivotX = (offX + panel.centerX * contentW) / vw
                val pivotY = (offY + panel.centerY * contentH) / vh
                Modifier.fillMaxSize().graphicsLayer(
                    scaleX = scale, scaleY = scale,
                    transformOrigin = TransformOrigin(pivotX, pivotY),
                )
            } else Modifier.fillMaxSize()

            AsyncImage(
                model = request,
                contentDescription = "Seite ${pageIndex + 1}",
                contentScale = ContentScale.Fit,
                modifier = mod,
            )
        }

        Box(
            Modifier.fillMaxSize().pointerInput(state.zoomed, state.guidedEnabled, state.currentPanels) {
                detectTapGestures { offset ->
                    if (state.zoomed) {
                        when {
                            offset.x < vw / 3f -> comicVm.previous()
                            offset.x > vw * 2f / 3f -> comicVm.next()
                            else -> comicVm.zoomOut()
                        }
                    } else {
                        val xN = ((offset.x - offX) / contentW).coerceIn(0f, 1f)
                        val yN = ((offset.y - offY) / contentH).coerceIn(0f, 1f)
                        comicVm.onPageTap(xN, yN)
                    }
                }
            },
        )

        ReaderChromeOverlay(
            visible = state.chromeVisible,
            title = "${state.position.page + 1} / $pageCount",
            onBack = onBack,
            actions = {
                IconButton(onClick = { comicVm.toggleGuided() }) {
                    Icon(
                        Icons.Filled.GridView,
                        contentDescription = if (state.guidedEnabled) s.readerPanelModeOff else s.readerPanelModeOn,
                        tint = if (state.guidedEnabled) Color.White else Color.Gray,
                    )
                }
                IconButton(onClick = onToggleMode) {
                    Icon(Icons.Filled.ViewDay, contentDescription = "Zu Webtoon-Modus wechseln", tint = Color.White)
                }
            },
        )
    }
}
