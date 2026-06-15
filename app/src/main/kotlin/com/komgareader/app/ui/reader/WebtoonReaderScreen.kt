package com.komgareader.app.ui.reader

import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.komgareader.app.data.coil.SourceImage
import com.komgareader.app.ui.components.FilteredReaderAsyncImage
import coil.compose.AsyncImagePainter
import coil.request.ImageRequest
import com.komgareader.domain.model.DisplayMode
import com.komgareader.domain.model.ReaderKind
import com.komgareader.ui.slots.ReaderTapZones
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
 * Overlay/Footer laufen über [ReaderScaffold]; die Tap-Zonen sind deklarativ über `tapZones`
 * mit bespoke Aktionen belegt (E-Ink-gegatete Frame-Sprünge statt Seiten-Navigation) — die
 * Host-Drittel-Geometrie bleibt, nur die Zonen-Aktionen sind hier andere.
 */
@Composable
fun WebtoonReaderScreen(
    pages: List<SourceImage>,
    initialPage: Int,
    readerKind: ReaderKind,
    bookRemoteId: String,
    sourceId: Long,
    displayMode: DisplayMode,
    frameSteps: Flow<Int>,
    chrome: Viewer,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onSettings: () -> Unit,
    onPageVisible: (Int) -> Unit,
    onToggleMode: () -> Unit,
) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialPage)
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val pageCount = pages.size
    val eink = displayMode == DisplayMode.EINK

    ReadingSessionEffect(readerKind, bookRemoteId, sourceId, listState.firstVisibleItemIndex)

    // Platzhalter-Höhe für noch nicht geladene Seiten: ≈ ein Bildschirm. Damit
    // reserviert jedes Item Platz, bevor das Bild da ist — die LazyColumn
    // komponiert nur die sichtbaren + nächsten Items und löst nicht Hunderte
    // gleichzeitiger Bild-Requests aus (sonst Lade-Sturm bei langen Strips).
    val placeholderHeight = LocalConfiguration.current.screenHeightDp.dp

    // Frame-Sprung: ~1 Bildschirmhöhe (mit Überlappung). E-Ink ohne Animation; Smartphone animiert.
    suspend fun jumpFrame(direction: Int) {
        val viewport = listState.layoutInfo.viewportSize.height
        if (viewport <= 0) return
        val delta = direction * viewport * (1f - FRAME_OVERLAP)
        if (eink) listState.scrollBy(delta) else listState.animateScrollBy(delta)
    }

    // Hardware-/Lautstärke-Tasten → Frame-Sprung
    LaunchedEffect(frameSteps, eink) {
        frameSteps.collect { jumpFrame(it) }
    }

    // Track progress (chapter-accurate in ViewModel).
    LaunchedEffect(listState.firstVisibleItemIndex) {
        onPageVisible(listState.firstVisibleItemIndex)
    }

    ReaderScaffold(
        modifier = Modifier.testTag("reader_webtoon"),
        chrome = chrome,
        title = "${listState.firstVisibleItemIndex + 1} / $pageCount",
        onBack = onBack,
        onHome = onHome,
        onSettings = onSettings,
        // Tap-Navigation läuft hier über jumpFrame (siehe tapZones), nicht über Seiten.
        onPrev = {},
        onNext = {},
        actions = { ReaderModeAction(onToggleMode, "Zu Paged-Modus wechseln") },
        // Deklarative Tap-Zonen (Geometrie host-eigen). E-Ink: links/rechts = Frame zurück/vor,
        // Mitte = Overlay. Smartphone: jede Zone togglet das Overlay, Drags gehen an die LazyColumn.
        tapZones = if (eink) {
            ReaderTapZones.HorizontalThirds(
                left = { scope.launch { jumpFrame(-1) } },
                center = { chrome.toggleChrome() },
                right = { scope.launch { jumpFrame(1) } },
            )
        } else {
            ReaderTapZones.HorizontalThirds(
                left = { chrome.toggleChrome() },
                center = { chrome.toggleChrome() },
                right = { chrome.toggleChrome() },
            )
        },
        showTapZoneHints = false,
        footer = { ReaderStatusBar("${listState.firstVisibleItemIndex + 1} / $pageCount", dark = true) },
    ) {
        LazyColumn(
            state = listState,
            userScrollEnabled = !eink,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(0.dp),
        ) {
            itemsIndexed(
                pages,
                key = { _, img -> "${img.bookRemoteId}-${img.pageNumber}" },
            ) { index, pageImage ->
                val request = remember(pageImage) {
                    ImageRequest.Builder(ctx)
                        .data(pageImage)
                        .crossfade(false)
                        .build()
                }
                // Solange das Bild lädt: volle Platzhalterhöhe reservieren (damit die
                // LazyColumn nur sichtbare + nächste Items komponiert, kein Lade-Sturm).
                // Nach dem Laden die exakte Bild-Ratio per aspectRatio erzwingen — sonst
                // bliebe das Item bildschirmhoch und kurze Seiten (Banner/Spacer) erschienen
                // zentriert mit schwarzen Balken darüber/darunter. (FillWidth allein lässt
                // AsyncImage bei unbegrenzter Höhe NICHT auf die Bildhöhe wrappen.)
                var aspect by remember(pageImage) { mutableStateOf(0f) }
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
