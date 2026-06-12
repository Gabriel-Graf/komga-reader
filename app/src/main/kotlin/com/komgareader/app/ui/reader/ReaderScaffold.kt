package com.komgareader.app.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.RowScope
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
 * Capability-Surface des **ganzen Reader-Chrome**: die host-gebauten Stücke, die ein
 * [com.komgareader.app.ui.slots.ReaderChromeSlot]-Pack zur Lese-Oberfläche arrangiert —
 * Vollbild-Hintergrund ([background]), Tap-Zonen (default Drittel-Navigation [onPrev]/[onNext]/
 * [onToggleChrome] **oder** [tapModifier]), Tap-Zonen-Hints ([showTapZoneHints]), die Chrome-
 * Menüleiste (über die `overlay`-Region aus [title]/[onBack]/[onHome]/[onSettings]/[actions]),
 * der optionale Status-Fuß ([footer]), die dauerhaften Info-Leisten ([persistentBars]), der
 * Start-Hinweis und der eigentliche [content].
 *
 * **Trägt NICHT den [Viewer] (Naht B).** [ReaderScaffold] nutzt den Viewer nur, um die abgeleiteten
 * [chromeVisible] (aus `chrome.chromeVisible`) und [onToggleChrome] (`chrome::toggleChrome`) zu
 * bilden — `refreshScheduler`/`navigateTo`/`onPageSettled` fasst das Scaffold nie an. So bleibt
 * Naht B (Refresh-Scheduler, Engine-Navigation) vollständig aus der austauschbaren Surface; ein
 * Chrome-Pack kann sie nicht berühren. Auch der E-Ink-Scrim und die Animation-Gating-Pfade bleiben
 * host-/Core-erzwungen, nicht Teil hiervon.
 */
data class ReaderScaffoldState(
    val chromeVisible: Boolean,
    val onToggleChrome: () -> Unit,
    val title: String,
    val onBack: () -> Unit,
    val onHome: () -> Unit,
    val onSettings: () -> Unit,
    val onPrev: () -> Unit,
    val onNext: () -> Unit,
    val background: Color = Color.Black,
    val actions: @Composable RowScope.() -> Unit = {},
    val tapModifier: Modifier? = null,
    val footer: (@Composable BoxScope.() -> Unit)? = null,
    val persistentBars: (@Composable BoxScope.() -> Unit)? = null,
    val showTapZoneHints: Boolean = tapModifier == null,
    val content: @Composable () -> Unit,
)

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
    tapModifier: Modifier? = null,
    footer: (@Composable BoxScope.() -> Unit)? = null,
    persistentBars: (@Composable BoxScope.() -> Unit)? = null,
    showTapZoneHints: Boolean = tapModifier == null,
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
        tapModifier = tapModifier,
        footer = footer,
        persistentBars = persistentBars,
        showTapZoneHints = showTapZoneHints,
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
 * (`animation-gating`). Refresh (`RefreshScheduler`) ist nicht Teil der Surface → ein Pack kann
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

        // Tap-Zonen: standardmäßig Drittel-Navigation; bespoke Reader liefern eigene.
        val taps = state.tapModifier ?: Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val width = size.width.toFloat()
                    when {
                        offset.x < width / 3f -> state.onPrev()
                        offset.x > width * 2f / 3f -> state.onNext()
                        else -> state.onToggleChrome()
                    }
                }
            }
        Box(taps)

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
        if (state.footer != null && state.chromeVisible) {
            state.footer.invoke(this)
        }

        // Liegt zuoberst, weicht aber dem Chrome (sonst überlappten Top-Leiste und Hinweis).
        ReaderStartHint(text = strings.readerTapHint, visible = startHintVisible && !state.chromeVisible)
    }
}
