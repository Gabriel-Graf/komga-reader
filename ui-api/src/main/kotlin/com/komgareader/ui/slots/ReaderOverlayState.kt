package com.komgareader.ui.slots

import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable

/**
 * Capability-Surface der toggle­baren Reader-Chrome-Menüleiste: Titel + Navigations-/Shortcut-
 * Callbacks ([onBack] · [onHome] · [onSettings]) + die reader-spezifischen [actions]
 * (Inhaltsverzeichnis, Suche, Typografie, …). Ein [OverlaySlot]-Pack arrangiert daraus die Leiste.
 *
 * **Bewusst kein `visible`-Flag:** die Sichtbarkeit (chromeVisible) **und** der E-Ink-Scrim
 * (`readerOverlayScrim`) sind **host-erzwungen** (das `ReaderScaffold` rendert die Leiste nur bei
 * `chromeVisible`) — nicht Teil dieser Surface. So bleibt sie sauber und konsistent mit den anderen
 * Slot-Surfaces (kein Layout-/Zustands-Flag in der Surface).
 */
data class ReaderOverlayState(
    val title: String,
    val onBack: () -> Unit,
    val onHome: () -> Unit,
    val onSettings: () -> Unit,
    val actions: @Composable RowScope.() -> Unit,
)
