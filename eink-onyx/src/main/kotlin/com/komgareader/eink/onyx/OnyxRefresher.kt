package com.komgareader.eink.onyx

import android.view.View
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schlanker Wrapper um den [OnyxEinkController] für den Reader-Composable.
 *
 * Kapselt alle Onyx-spezifischen Aufrufe (enterFastMode, exitFastMode,
 * fullRefresh) so, dass der Reader-Code keine Onyx-Typen direkt importieren muss.
 * Auf Nicht-Boox-Geräten enthält [controller] eine No-Op-Instanz
 * ([controller] ist dann NOT vom Typ [OnyxEinkController]) und die
 * Helper-Methoden sind sicher aufzurufen.
 *
 * Bereitgestellt vom Hilt-Modul als [Singleton].
 */
@Singleton
class OnyxRefresher @Inject constructor() {

    /** Wird vom DI-Modul nach der Erstellung gesetzt. */
    var controller: OnyxEinkController? = null

    /** Schaltet den App-weiten A2/DW-Schnell-Modus ein. */
    fun enterFastMode() {
        controller?.enterFastMode()
    }

    /** Schaltet den A2/DW-Schnell-Modus aus. */
    fun exitFastMode() {
        controller?.exitFastMode()
    }

    /**
     * Erzwingt einen GC-Full-Refresh auf der gegebenen [view].
     * Sollte alle N Seitenumbrüche aufgerufen werden.
     */
    fun fullRefreshIfNeeded(view: View, pagesSinceLastRefresh: Int, interval: Int = GHOST_CLEAR_INTERVAL) {
        if (controller != null && pagesSinceLastRefresh >= interval) {
            controller?.fullRefresh(view)
        }
    }

    companion object {
        /** Alle N Seitenumbrüche wird ein GC-Full-Refresh ausgelöst. */
        const val GHOST_CLEAR_INTERVAL = 6
    }
}
