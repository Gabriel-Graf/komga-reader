package com.komgareader.app.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import com.komgareader.app.i18n.LocalStrings
import com.komgareader.app.ui.slots.LocalResolvedSlots
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
 * Reader bauen darauf statt als Parallel-Linie. Reader mit bespoke Gesten (Comic:
 * Panel-Hit-Test/Zoom; Webtoon: E-Ink-gegatete Frame-Sprünge) liefern ihren eigenen
 * Tap-Handler über [tapModifier] und nutzen das Scaffold nur für Overlay/Footer.
 *
 * **Keine Animation auf E-Ink:** Overlay, Footer und Tap-Zonen-Hints erscheinen sofort;
 * nur der Start-Hinweis blendet auf dem Smartphone per Fade, auf E-Ink sofort
 * (`animation-gating`).
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
    actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {},
    tapModifier: Modifier? = null,
    footer: (@Composable BoxScope.() -> Unit)? = null,
    persistentBars: (@Composable BoxScope.() -> Unit)? = null,
    showTapZoneHints: Boolean = tapModifier == null,
    content: @Composable () -> Unit,
) {
    val chromeVisible by chrome.chromeVisible.collectAsState()
    val strings = LocalStrings.current

    // Start-Hinweis oben mittig beim Öffnen jedes Readers, verschwindet nach ~1,5 s.
    var startHintVisible by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(START_HINT_MILLIS)
        startHintVisible = false
    }

    Box(modifier.fillMaxSize().background(background)) {
        content()

        // Tap-Zonen: standardmäßig Drittel-Navigation; bespoke Reader liefern eigene.
        val taps = tapModifier ?: Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val width = size.width.toFloat()
                    when {
                        offset.x < width / 3f -> onPrev()
                        offset.x > width * 2f / 3f -> onNext()
                        else -> chrome.toggleChrome()
                    }
                }
            }
        Box(taps)

        // Dauerhafte Info-Leisten (Roman-Page-Header/-Footer): immer sichtbar, liegen unter
        // dem toggle­baren Chrome — die Menüleiste überdeckt bei Bedarf den Page-Header oben.
        persistentBars?.invoke(this)

        // Tap-Zonen-Hints (Hintergrund + Pfeil + Letzte/Nächste Seite) nur bei sichtbarem
        // Chrome und nur für Reader mit den Standard-Drittel-Zonen (echte Seiten-Navigation).
        if (chromeVisible && showTapZoneHints) {
            ReaderTapZoneHints()
        }

        // Reader-Chrome-Menüleiste hinter der overlay-Slot-Region (austauschbar). Host-gegatet:
        // sichtbar gdw. chromeVisible — die Sichtbarkeit gehört dem Host, nicht der Slot-Surface.
        // overlay ist eine BoxScope-Extension; der BoxScope-Receiver (dieses Box) wird über
        // `with(this)` explizit gemacht (der implizite Receiver greift bei Funktionswerten nicht).
        if (chromeVisible) {
            val overlay = LocalResolvedSlots.current.overlay
            with(this) {
                overlay(
                    ReaderOverlayState(
                        title = title,
                        onBack = onBack,
                        onHome = onHome,
                        onSettings = onSettings,
                        actions = actions,
                    ),
                )
            }
        }

        // Der Status-Fuß ist ein Overlay wie die Top-Leiste: nur sichtbar, wenn das
        // Chrome sichtbar ist (überdeckt dann ggf. Inhalt) — nicht dauerhaft.
        if (footer != null && chromeVisible) {
            footer()
        }

        // Liegt zuoberst, weicht aber dem Chrome (sonst überlappten Top-Leiste und Hinweis).
        ReaderStartHint(text = strings.readerTapHint, visible = startHintVisible && !chromeVisible)
    }
}
