package com.komgareader.app.ui.reader

import com.komgareader.domain.eink.RefreshScheduler
import kotlinx.coroutines.flow.StateFlow

/**
 * **Viewer-Naht (Reader-Vertrag).** Der eine gemeinsame Vertrag, den **jeder** Reader-ViewModel
 * (paged/webtoon, comic, novel) erfüllt — und gegen den das geteilte [ReaderScaffold] sowie der
 * Reader-Host arbeiten. Ein fünfter Reader (oder später eine deklarative UI-View) implementiert
 * **diesen** Vertrag, statt einen weiteren `when`-Zweig + eine Parallel-Linie danebenzustellen
 * (siehe `shared-structure-before-variants`).
 *
 * Bewusst eine **Compose-Zustands**-Naht (Sichtbarkeit als `StateFlow`, Settle-/Tap-Callbacks),
 * nicht das OO-`bind/onButton/teardown` aus der alten Spec — Compose verwaltet den Lifecycle
 * deklarativ. Hier liegt nur das, was die Reader **wirklich** teilen: Chrome, Navigation und die
 * geräteunabhängige Refresh-Entscheidung.
 */
interface Viewer {
    /** Sichtbarkeit des durchscheinenden Reader-Overlays (Bars ein/aus). */
    val chromeVisible: StateFlow<Boolean>

    /**
     * Geräteunabhängige Refresh-Entscheidung (PARTIAL beim Blättern, FULL-Promotion gegen
     * Ghosting / bei bewusstem Bildwechsel). EINE Instanz pro Reader-Sitzung — alle Reader
     * teilen damit dieselbe getestete Logik; die Ausführung macht der `OnyxRefresher`.
     */
    val refreshScheduler: RefreshScheduler

    /** Mitte-Tap: Overlay ein-/ausblenden. */
    fun toggleChrome()

    /** Tap-Zone links/rechts: gezielt eine Seite ansteuern. */
    fun navigateTo(page: Int)

    /** Der Viewer hat eine Seite final eingenommen (Pager-/Scroll-Settle). */
    fun onPageSettled(page: Int)
}
