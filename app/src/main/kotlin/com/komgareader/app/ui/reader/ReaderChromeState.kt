package com.komgareader.app.ui.reader

import kotlinx.coroutines.flow.StateFlow

/**
 * Gemeinsamer Chrome-/Navigations-Vertrag, den jeder Reader-ViewModel erfüllt.
 *
 * Diese dünne Schicht zentralisiert, was die Reader bislang ad-hoc je VM trugen
 * (Overlay-Sichtbarkeit, Tap-Zonen-/Settle-Navigation), damit das geteilte
 * [ReaderScaffold] gegen genau dieses Interface arbeitet — und der vierte Reader
 * (NOVEL) darauf aufbaut statt als Parallel-Linie daneben
 * (siehe `shared-structure-before-variants`).
 */
interface ReaderChromeState {
    /** Sichtbarkeit des durchscheinenden Reader-Overlays (Bars ein/aus). */
    val chromeVisible: StateFlow<Boolean>

    /** Mitte-Tap: Overlay ein-/ausblenden. */
    fun toggleChrome()

    /** Tap-Zone links/rechts: gezielt eine Seite ansteuern. */
    fun navigateTo(page: Int)

    /** Der Viewer hat eine Seite final eingenommen (Pager-/Scroll-Settle). */
    fun onPageSettled(page: Int)
}
