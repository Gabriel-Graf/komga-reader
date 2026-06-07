package com.komgareader.app.ui.reader

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.hilt.navigation.compose.hiltViewModel
import com.komgareader.app.ui.components.FilteredReaderAsyncImage
import coil.request.ImageRequest
import com.komgareader.app.i18n.LocalStrings
import com.komgareader.app.ui.icons.AppIcons
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
    val showOverlay by comicVm.showPanelOverlay.collectAsState()
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

    // Kleiner Rand: Panel füllt das Viewport möglichst voll (ohne Crop). Größerer Wert = mehr Luft.
    val marginFraction = 0.02f

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
                // Panel-Mittelpunkt (in Viewport-Pixeln) auf die Viewport-Mitte schieben UND skalieren,
                // damit genau der Panel-Inhalt zentriert bildschirmfüllend gezeigt wird. transformOrigin
                // oben-links (0,0) + Translation ist nötig: nur ein Pivot würde das Panel an seiner
                // Off-Center-Position fixieren statt es zu zentrieren.
                val cx = offX + panel.centerX * contentW
                val cy = offY + panel.centerY * contentH
                Modifier.fillMaxSize().graphicsLayer(
                    scaleX = scale, scaleY = scale,
                    transformOrigin = TransformOrigin(0f, 0f),
                    translationX = vw / 2f - scale * cx,
                    translationY = vh / 2f - scale * cy,
                )
            } else Modifier.fillMaxSize()

            FilteredReaderAsyncImage(
                model = request,
                contentDescription = "Seite ${pageIndex + 1}",
                modifier = mod,
                contentScale = ContentScale.Fit,
            )
        }

        // Debug-Overlay: erkannte Panel-Rahmen als grüne Rechtecke (kein pointerInput → Taps passieren durch).
        if (showOverlay && !state.zoomed && state.currentPanels.isNotEmpty()) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = Stroke(width = 9f)
                state.currentPanels.forEach { p ->
                    drawRect(
                        color = Color(0xFF00C800),
                        topLeft = Offset(offX + p.left * contentW, offY + p.top * contentH),
                        size = Size(p.width * contentW, p.height * contentH),
                        style = stroke,
                    )
                }
            }
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
                        AppIcons.PanelMode,
                        contentDescription = if (state.guidedEnabled) s.readerPanelModeOff else s.readerPanelModeOn,
                        tint = if (state.guidedEnabled) Color.White else Color.Gray,
                    )
                }
                IconButton(onClick = onToggleMode) {
                    Icon(AppIcons.ReaderMode, contentDescription = "Zu Webtoon-Modus wechseln", tint = Color.White)
                }
            },
        )
    }
}
