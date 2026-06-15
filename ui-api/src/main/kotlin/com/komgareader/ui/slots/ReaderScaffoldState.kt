package com.komgareader.ui.slots

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Capability-Surface des **ganzen Reader-Chrome**: die host-gebauten Stücke, die ein
 * [ReaderChromeSlot]-Pack zur Lese-Oberfläche arrangiert —
 * Vollbild-Hintergrund ([background]), Tap-Zonen (deklarativ über [tapZones]: die Geometrie gehört
 * dem Host, der Screen liefert pro Zone nur die Aktion; `null` = der Screen behandelt Taps selbst),
 * Tap-Zonen-Hints ([showTapZoneHints]), die Chrome-
 * Menüleiste (über die `overlay`-Region aus [title]/[onBack]/[onHome]/[onSettings]/[actions]),
 * der optionale Status-Fuß ([footer]), die dauerhaften Info-Leisten ([persistentBars]), der
 * Start-Hinweis und der eigentliche [content].
 *
 * **Trägt NICHT den `Viewer` (Naht B).** `ReaderScaffold` nutzt den Viewer nur, um die abgeleiteten
 * [chromeVisible] (aus `chrome.chromeVisible`) und [onToggleChrome] (`chrome::toggleChrome`) zu
 * bilden — `refreshScheduler`/`navigateTo`/`onPageSettled` fasst das Scaffold nie an. So bleibt
 * Naht B (Refresh-Scheduler, Engine-Navigation) vollständig aus der austauschbaren Surface; ein
 * Chrome-Pack kann sie nicht berühren. Auch der E-Ink-Scrim und die Animation-Gating-Pfade bleiben
 * host-/Core-erzwungen, nicht Teil hiervon.
 */
/**
 * Optional bottom-sheet capability of the reader chrome. The HOST owns the open mechanics
 * (upward bottom-edge swipe, an expandable peek bar shown while chrome is visible, scrim,
 * expand/collapse) and ENFORCES the E-Ink invariants (no motion on E-Ink); the reader supplies
 * only [content] (arbitrary — e.g. a tabbed panel). `null` on [ReaderScaffoldState] = no bottom
 * sheet for this reader (default; Paged/Comic/Webtoon/Epub).
 */
data class ReaderBottomSheet(
    val expanded: Boolean,
    val onExpandedChange: (Boolean) -> Unit,
    /** Label of the collapsed peek bar (only shown while the chrome is visible). */
    val peekLabel: String,
    /** Reader-provided body (the tabs live here — the host does not know about them). */
    val content: @Composable () -> Unit,
)

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
    /**
     * Deklarative Tap-Zonen (Geometrie host-eigen, Aktion pro Zone als Daten). `null` = der Screen
     * behandelt Taps selbst (Escape-Luke für Comic: Panel-Hit-Test/Zoom in der content-Lambda) →
     * kein Host-Tap-Layer. Ersetzt das frühere opake `tapModifier`.
     */
    val tapZones: ReaderTapZones? = null,
    val footer: (@Composable BoxScope.() -> Unit)? = null,
    val persistentBars: (@Composable BoxScope.() -> Unit)? = null,
    /**
     * Ob die Tap-Zonen-Hints (Letzte/Nächste-Seite-Chips) bei sichtbarem Chrome erscheinen. Die
     * Standard-Drittel-Reader (Paged/Epub/Novel) wollen sie (`true`); Reader mit bespoke Gesten
     * (Comic/Webtoon) setzen `false` — ihre Hints passen nicht zur eigenen Gesten-Logik.
     */
    val showTapZoneHints: Boolean = true,
    /**
     * Optional bottom sheet (content reader-provided, mechanics host-owned). `null` = none.
     * Host-enforced E-Ink invariants: instant on E-Ink, animated only on phone.
     */
    val bottomSheet: ReaderBottomSheet? = null,
    /**
     * When true, the host excludes the left/right screen edges of the reader from the system BACK
     * gesture (`Modifier.systemGestureExclusion`, OS-capped at 200dp/edge) so edge swipes go to the
     * reader instead of triggering predictive-back. Used by the Novel reader, whose reading gestures
     * collide with the system swipe-back. NOTE: the system HOME swipe-up cannot be disabled by any
     * app (OS guarantee) — this only holds off the back-edge swipes.
     */
    val gestureExclusion: Boolean = false,
    val content: @Composable () -> Unit,
)
