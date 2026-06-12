package com.komgareader.ui.slots

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * Capability-Surface des **ganzen Reader-Chrome**: die host-gebauten Stücke, die ein
 * [ReaderChromeSlot]-Pack zur Lese-Oberfläche arrangiert —
 * Vollbild-Hintergrund ([background]), Tap-Zonen (default Drittel-Navigation [onPrev]/[onNext]/
 * [onToggleChrome] **oder** [tapModifier]), Tap-Zonen-Hints ([showTapZoneHints]), die Chrome-
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
    /**
     * Ob die Tap-Zonen-Hints (Letzte/Nächste-Seite-Chips) bei sichtbarem Chrome erscheinen. Default:
     * `true` **gdw.** [tapModifier] `== null` (Standard-Drittel-Navigation aktiv); Reader mit bespoke
     * Tap-Zonen (Comic/Webtoon übergeben ein nicht-`null` [tapModifier], auch ein leeres `Modifier`)
     * setzen damit implizit `false` — ihre Hints passen nicht zur eigenen Gesten-Logik.
     */
    val showTapZoneHints: Boolean = tapModifier == null,
    val content: @Composable () -> Unit,
)
