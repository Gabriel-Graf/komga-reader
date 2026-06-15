package com.komgareader.app.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.komgareader.app.i18n.LocalStrings
import com.komgareader.ui.slots.LocalResolvedSlots
import com.komgareader.ui.slots.ReaderBottomSheet
import com.komgareader.ui.slots.ReaderOverlayState
import com.komgareader.ui.slots.ReaderScaffoldState
import com.komgareader.ui.slots.ReaderTapZones
import com.komgareader.ui.slots.dispatch
import kotlinx.coroutines.delay

/** Anzeigedauer des Start-Hinweises oben mittig, bevor er wieder verschwindet. */
private const val START_HINT_MILLIS = 1500L

/**
 * Geteiltes Reader-Gerüst: kapselt die über alle Reader identische Chrome-Mechanik —
 * den schwarzen Vollbild-Hintergrund, die Drittel-Tap-Zonen (links → [onPrev],
 * rechts → [onNext], Mitte → `chrome.toggleChrome()`), die Chrome-Menüleiste (hinter der
 * `overlay`-Slot-Region, default [DefaultReaderOverlay]), die Tap-Zonen-Hints, einen optionalen
 * Status-Fuß ([footer]) und den Start-Hinweis.
 *
 * Das gehört nach `shared-structure-before-variants` an genau **eine** Stelle: alle
 * Reader bauen darauf statt als Parallel-Linie. Die Tap-Zonen sind **deklarativ** ([tapZones]):
 * die Drittel-Geometrie gehört dem Host, der Reader liefert pro Zone nur die Aktion (Webtoon:
 * E-Ink-gegatete Frame-Sprünge). Reader mit echten Custom-Gesten (Comic: Panel-Hit-Test/Zoom mit
 * Viewport-Geometrie) übergeben `tapZones = null` und behandeln Taps in der content-Lambda selbst.
 *
 * Dünner **Host-Wrapper** der `readerChrome`-Slot-Region (C1): bildet aus dem [chrome]-[Viewer]
 * (Naht B) die abgeleiteten `chromeVisible`/`onToggleChrome`, baut die [ReaderScaffoldState]-Surface
 * und ruft den aufgelösten Slot (Default = [DefaultReaderScaffold]). Die Signatur bleibt unverändert,
 * damit die fünf Reader-Call-Sites unangetastet bleiben.
 */
@Composable
fun ReaderScaffold(
    chrome: Viewer,
    title: String,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onSettings: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    background: Color = Color.Black,
    actions: @Composable RowScope.() -> Unit = {},
    tapZones: ReaderTapZones? = ReaderTapZones.HorizontalThirds(onPrev, chrome::toggleChrome, onNext),
    footer: (@Composable BoxScope.() -> Unit)? = null,
    persistentBars: (@Composable BoxScope.() -> Unit)? = null,
    showTapZoneHints: Boolean = true,
    bottomSheet: ReaderBottomSheet? = null,
    content: @Composable () -> Unit,
) {
    val chromeVisible by chrome.chromeVisible.collectAsState()
    val state = ReaderScaffoldState(
        chromeVisible = chromeVisible,
        onToggleChrome = chrome::toggleChrome,
        title = title,
        onBack = onBack,
        onHome = onHome,
        onSettings = onSettings,
        onPrev = onPrev,
        onNext = onNext,
        background = background,
        actions = actions,
        tapZones = tapZones,
        footer = footer,
        persistentBars = persistentBars,
        showTapZoneHints = showTapZoneHints,
        bottomSheet = bottomSheet,
        content = content,
    )
    // `modifier` ist Host-Layout — als Box-Wrapper um den Slot, nicht in der Surface; der Renderer
    // macht das `fillMaxSize().background(...)` selbst.
    Box(modifier) { LocalResolvedSlots.current.readerChrome(state) }
}

/**
 * Der mitgelieferte Default-Renderer der `readerChrome`-Region (der verbatim aus dem alten
 * [ReaderScaffold]-Body extrahierte Onyx-Look): schwarzer Vollbild-Hintergrund, Drittel-Tap-Zonen,
 * Tap-Zonen-Hints, die Chrome-Menüleiste über die `overlay`-Region, der optionale Status-Fuß, die
 * dauerhaften Info-Leisten und der Start-Hinweis — alles aus [state].
 *
 * **Keine Animation auf E-Ink:** Overlay, Footer und Tap-Zonen-Hints erscheinen sofort;
 * nur der Start-Hinweis blendet auf dem Smartphone per Fade, auf E-Ink sofort
 * (`animation-gating`). Refresh-Verhalten ist nicht Teil der Surface → ein Pack kann
 * ihn nicht umgehen; der E-Ink-Scrim bleibt host-erzwungen.
 */
@Composable
fun DefaultReaderScaffold(state: ReaderScaffoldState) {
    val strings = LocalStrings.current

    // Start-Hinweis oben mittig beim Öffnen jedes Readers, verschwindet nach ~1,5 s.
    var startHintVisible by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(START_HINT_MILLIS)
        startHintVisible = false
    }

    Box(Modifier.fillMaxSize().background(state.background)) {
        state.content()

        // Tap-Zonen: die Geometrie (Drittel) gehört dem Host; der Reader liefert pro Zone nur die
        // Aktion (deklarativ). `null` = der Reader behandelt Taps selbst (Comic: Panel-Hit-Test in
        // der content-Lambda) → kein Tap-Layer.
        val zones = state.tapZones
        if (zones is ReaderTapZones.HorizontalThirds) {
            // pointerInput auf `Unit` keyen (nie neu starten); die jeweils aktuelle Zonen-Instanz
            // über rememberUpdatedState lesen. Sonst würde jede neue HorizontalThirds-Instanz
            // (data class mit Lambda-Feldern → nie `==`, pro Recompose neu) den Gesten-Detektor bei
            // jedem Seitenwechsel neu starten und könnte einen laufenden Tap verschlucken.
            val currentZones = rememberUpdatedState(zones)
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            currentZones.value.dispatch(offset.x / size.width.toFloat())
                        }
                    },
            )
        }

        // Frontlight edge strips — only on devices with a controllable frontlight. Two thin
        // (24dp) strips at the left and right edges detect an inward horizontal drag and open
        // the BrightnessBar. Thin so they never steal central HorizontalPager page swipes.
        // On the emulator (NoOpEinkController) brightnessRange is null → whole block skipped.
        val frontlight: FrontlightHolder = hiltViewModel()
        val brightnessRange = frontlight.brightnessRange
        if (brightnessRange != null) {
            var barAlign by remember { mutableStateOf<Alignment?>(null) }
            val level by frontlight.level.collectAsState()
            val effectiveLevel = if (level < 0) brightnessRange.first else level

            // Left strip: inward drag (dragAmount > 0) opens the bar on the start side.
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxHeight()
                    .width(24.dp)
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures { change, dragAmount ->
                            if (dragAmount > 0f) {
                                barAlign = Alignment.CenterStart
                                change.consume()
                            }
                        }
                    },
            )
            // Right strip: inward drag (dragAmount < 0) opens the bar on the end side.
            Box(
                Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(24.dp)
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures { change, dragAmount ->
                            if (dragAmount < 0f) {
                                barAlign = Alignment.CenterEnd
                                change.consume()
                            }
                        }
                    },
            )
            barAlign?.let { align ->
                BrightnessBar(
                    level = effectiveLevel,
                    range = brightnessRange,
                    alignment = align,
                    onLevel = { frontlight.setLevel(it) },
                    onDismiss = { barAlign = null },
                )
            }
        }

        // Dauerhafte Info-Leisten (Roman-Page-Header/-Footer): immer sichtbar, liegen unter
        // dem toggle­baren Chrome — die Menüleiste überdeckt bei Bedarf den Page-Header oben.
        state.persistentBars?.invoke(this)

        // Tap-Zonen-Hints (Hintergrund + Pfeil + Letzte/Nächste Seite) nur bei sichtbarem
        // Chrome und nur für Reader mit den Standard-Drittel-Zonen (echte Seiten-Navigation).
        if (state.chromeVisible && state.showTapZoneHints) {
            ReaderTapZoneHints()
        }

        // Reader-Chrome-Menüleiste hinter der overlay-Slot-Region (austauschbar). Host-gegatet:
        // sichtbar gdw. chromeVisible — die Sichtbarkeit gehört dem Host, nicht der Slot-Surface.
        // overlay ist eine BoxScope-Extension; der BoxScope-Receiver (dieses Box) wird über
        // `with(this)` explizit gemacht (der implizite Receiver greift bei Funktionswerten nicht).
        if (state.chromeVisible) {
            val overlay = LocalResolvedSlots.current.overlay
            with(this) {
                overlay(
                    ReaderOverlayState(
                        title = state.title,
                        onBack = state.onBack,
                        onHome = state.onHome,
                        onSettings = state.onSettings,
                        actions = state.actions,
                    ),
                )
            }
        }

        // Der Status-Fuß ist ein Overlay wie die Top-Leiste: nur sichtbar, wenn das
        // Chrome sichtbar ist (überdeckt dann ggf. Inhalt) — nicht dauerhaft.
        // Lokale val: [ReaderScaffoldState] liegt im Modul :ui-api, ein cross-module-Property
        // ist nicht smart-castbar.
        val footer = state.footer
        if (footer != null && state.chromeVisible) {
            footer.invoke(this)
        }

        // Optional bottom sheet (host-owned mechanics, reader-provided content). Sits above the
        // footer/overlay; the scrim + motion are host-/E-Ink-enforced.
        val sheet = state.bottomSheet
        if (sheet != null) {
            ReaderBottomSheetLayer(sheet = sheet, chromeVisible = state.chromeVisible)
        }

        // Liegt zuoberst, weicht aber dem Chrome (sonst überlappten Top-Leiste und Hinweis).
        ReaderStartHint(text = strings.readerTapHint, visible = startHintVisible && !state.chromeVisible)
    }
}
