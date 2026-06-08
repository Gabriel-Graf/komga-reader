package com.komgareader.app.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.komgareader.app.ui.components.FilteredReaderAsyncImage
import coil.compose.AsyncImagePainter
import coil.request.ImageRequest
import com.komgareader.domain.model.DisplayMode
import com.komgareader.domain.source.PageRef
import com.komgareader.eink.onyx.OnyxRefresher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/** Anteil eines Bildschirms, der beim Frame-Sprung als Überlappung erhalten bleibt (≈30%). */
private const val FRAME_OVERLAP = 0.30f

/**
 * Webtoon-Reader: alle Kapitel nahtlos untereinander (FillWidth, volle Breite),
 * immer im Vollbild. Ein Tipp in die Mitte blendet ein durchscheinendes Overlay
 * mit Zurück-Button ein/aus — ohne die Scrollposition zu verändern.
 *
 * - [DisplayMode.SMARTPHONE]: frei scrollbar mit Smooth-Scroll; Frame-Tasten animiert.
 * - [DisplayMode.EINK]: kein Free-Scroll. „Blättern“ = Frame-Sprung um ~1 Bildschirm
 *   über die Tap-Zonen **links/rechts** sowie Hardware-/Lautstärke-Tasten ([frameSteps]),
 *   ohne Animation und mit einem GC-Full-Refresh pro Frame.
 *
 * Overlay/Footer laufen über [ReaderScaffold]; die Tap-Zonen sind hier bespoke
 * (E-Ink-gegatete Frame-Sprünge statt Seiten-Navigation) und werden daher über
 * `tapModifier` selbst geliefert — die Scaffold-Standard-Drittel greifen nicht.
 */
@Composable
fun WebtoonReaderScreen(
    pages: List<PageRef>,
    authHeaders: Map<String, String>,
    initialPage: Int,
    displayMode: DisplayMode,
    frameSteps: Flow<Int>,
    chrome: ReaderChromeState,
    onBack: () -> Unit,
    onPageVisible: (Int) -> Unit,
    onToggleMode: () -> Unit,
    refresher: OnyxRefresher? = null,
) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialPage)
    val ctx = LocalContext.current
    val rootView = LocalView.current
    val scope = rememberCoroutineScope()
    val pageCount = pages.size
    val eink = displayMode == DisplayMode.EINK

    // Platzhalter-Höhe für noch nicht geladene Seiten: ≈ ein Bildschirm. Damit
    // reserviert jedes Item Platz, bevor das Bild da ist — die LazyColumn
    // komponiert nur die sichtbaren + nächsten Items und löst nicht Hunderte
    // gleichzeitiger Bild-Requests aus (sonst Lade-Sturm bei langen Strips).
    val placeholderHeight = LocalConfiguration.current.screenHeightDp.dp

    // Frame-Sprung: ~1 Bildschirmhöhe (mit Überlappung). E-Ink ohne Animation,
    // danach ein GC-Full-Refresh; Smartphone animiert.
    suspend fun jumpFrame(direction: Int) {
        val viewport = listState.layoutInfo.viewportSize.height
        if (viewport <= 0) return
        val delta = direction * viewport * (1f - FRAME_OVERLAP)
        if (eink) {
            listState.scrollBy(delta)
            refresher?.fullRefreshNow(rootView)
        } else {
            listState.animateScrollBy(delta)
        }
    }

    // Hardware-/Lautstärke-Tasten → Frame-Sprung
    LaunchedEffect(frameSteps, eink) {
        frameSteps.collect { jumpFrame(it) }
    }

    // Fortschritt tracken (kapitel-genau im ViewModel). Im Smartphone-Modus zusätzlich
    // periodischer GC-Refresh gegen Ghosting (E-Ink refresht ohnehin pro Frame).
    LaunchedEffect(listState.firstVisibleItemIndex) {
        onPageVisible(listState.firstVisibleItemIndex)
        if (!eink && refresher != null &&
            triggerGhostClearIfNeeded(listState.firstVisibleItemIndex, refresher)
        ) {
            refresher.fullRefreshIfNeeded(
                view = rootView,
                pagesSinceLastRefresh = OnyxRefresher.GHOST_CLEAR_INTERVAL,
            )
        }
    }

    ReaderScaffold(
        chrome = chrome,
        title = "${listState.firstVisibleItemIndex + 1} / $pageCount",
        onBack = onBack,
        // Tap-Navigation läuft hier über jumpFrame (siehe tapModifier), nicht über Seiten.
        onPrev = {},
        onNext = {},
        actions = { ReaderModeAction(onToggleMode, "Zu Paged-Modus wechseln") },
        tapModifier = Modifier
            .fillMaxSize()
            // Tap-Zonen-Overlay. E-Ink: links/rechts = Frame zurück/vor, Mitte = Overlay.
            // Smartphone: Tap togglet Overlay, Drags gehen an die LazyColumn.
            .pointerInput(eink, pageCount) {
                detectTapGestures { offset ->
                    val width = size.width.toFloat()
                    when {
                        eink && offset.x < width / 3f -> scope.launch { jumpFrame(-1) }
                        eink && offset.x > width * 2f / 3f -> scope.launch { jumpFrame(1) }
                        else -> chrome.toggleChrome()
                    }
                }
            },
        footer = {
            Box(Modifier.fillMaxSize()) {
                Text(
                    text = "${listState.firstVisibleItemIndex + 1} / $pageCount",
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
        LazyColumn(
            state = listState,
            userScrollEnabled = !eink,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(0.dp),
        ) {
            itemsIndexed(pages, key = { _, pageRef -> pageRef.url }) { index, pageRef ->
                val request = remember(pageRef.url, authHeaders) {
                    ImageRequest.Builder(ctx)
                        .data(pageRef.url)
                        .apply { authHeaders.forEach { addHeader(it.key, it.value) } }
                        .crossfade(false)
                        .build()
                }
                // Solange das Bild lädt: volle Platzhalterhöhe reservieren (damit die
                // LazyColumn nur sichtbare + nächste Items komponiert, kein Lade-Sturm).
                // Nach dem Laden die exakte Bild-Ratio per aspectRatio erzwingen — sonst
                // bliebe das Item bildschirmhoch und kurze Seiten (Banner/Spacer) erschienen
                // zentriert mit schwarzen Balken darüber/darunter. (FillWidth allein lässt
                // AsyncImage bei unbegrenzter Höhe NICHT auf die Bildhöhe wrappen.)
                var aspect by remember(pageRef.url) { mutableStateOf(0f) }
                FilteredReaderAsyncImage(
                    model = request,
                    contentDescription = "Seite ${index + 1}",
                    contentScale = ContentScale.FillWidth,
                    onState = { state ->
                        val size = (state as? AsyncImagePainter.State.Success)?.painter?.intrinsicSize
                        if (size != null && size.isSpecified && size.height > 0f) {
                            aspect = size.width / size.height
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (aspect > 0f) Modifier.aspectRatio(aspect)
                            else Modifier.height(placeholderHeight),
                        ),
                )
            }
        }
    }
}
