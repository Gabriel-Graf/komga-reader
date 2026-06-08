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

    /**
     * Wenn true, überlässt die App den Voll-Refresh (Ghosting-Clear) dem Gerät: die
     * App-seitigen GC-Full-Aufrufe ([fullRefreshNow]/[fullRefreshIfNeeded]) werden zu No-Ops,
     * der Fast-Modus ([enterFastMode]) bleibt aktiv. Gespeist aus der Einstellung
     * `deviceManagedRefresh` (Default an). Volatile: aus dem Compose-Main-Thread gesetzt/gelesen.
     */
    @Volatile
    var deviceManaged: Boolean = true

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
        if (deviceManaged) return
        if (controller != null && pagesSinceLastRefresh >= interval) {
            controller?.fullRefresh(view)
        }
    }

    /** Sofortiger GC-Full-Refresh — für den E-Ink-Webtoon-Frame-Sprung (ein Refresh pro Frame). */
    fun fullRefreshNow(view: View) {
        if (deviceManaged) return
        controller?.fullRefresh(view)
    }

    companion object {
        /** Alle N Seitenumbrüche wird ein GC-Full-Refresh ausgelöst. */
        const val GHOST_CLEAR_INTERVAL = 6
    }
}
