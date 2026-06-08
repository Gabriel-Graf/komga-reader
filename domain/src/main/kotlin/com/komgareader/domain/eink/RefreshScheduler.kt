package com.komgareader.domain.eink

/**
 * Geräteunabhängige, **pure** Entscheidungslogik für E-Ink-Refresh — trennt die ENTSCHEIDUNG
 * (wann PARTIAL, wann FULL; hier, host-testbar ohne Android) von der AUSFÜHRUNG (`OnyxRefresher`/
 * [EinkController], gerätenah). Eine Instanz pro Reader-Sitzung.
 *
 * Regeln (Modus-Präzedenz):
 * 1. **Bewusster Bildwechsel** ([onContentChange] mit `forceFull = true`: Frame-Sprung,
 *    Panel-/Zoom-Wechsel, Bildwechsel) → sofort [RefreshMode.FULL], Promotion-Zähler zurück.
 * 2. Sonst [RefreshMode.PARTIAL] beim Blättern; nach [ghostClearInterval] PARTIALs seit dem
 *    letzten FULL **einmal** FULL (Ghosting-Promotion), dann Zähler zurück.
 *
 * Das ersetzt die bisher pro Reader unterschiedliche, fragile Index-Modulo-Logik
 * (`index % interval == 0`, die bei Seitensprüngen falsch zählt) durch **Event-Zählung**.
 *
 * @deprecated Die App-seitige Refresh-Steuerung wird zugunsten der geräteeigenen (Onyx)
 * abgelöst: per Einstellung `deviceManagedRefresh` (Default an) überlässt die App das
 * Ghosting-Clear dem Gerät — der `OnyxRefresher` führt dann keine GC-Full-Refreshes mehr aus.
 * Diese Entscheidungslogik läuft zwar noch, bleibt aber ohne Wirkung. Nur als Fallback behalten,
 * wenn der Toggle ausgeschaltet wird; mittelfristig entfernen.
 */
@Deprecated(
    "App-seitige Refresh-Entscheidung wird abgelöst (deviceManagedRefresh, Default an); " +
        "Onyx steuert den Voll-Refresh selbst. Nur noch Fallback für den ausgeschalteten Toggle.",
)
class RefreshScheduler(private val ghostClearInterval: Int = DEFAULT_GHOST_CLEAR_INTERVAL) {

    private var partialsSinceFull = 0

    /** Meldet einen sichtbaren Inhaltswechsel und liefert den fälligen Refresh-Modus. */
    fun onContentChange(forceFull: Boolean = false): RefreshMode {
        if (forceFull) {
            partialsSinceFull = 0
            return RefreshMode.FULL
        }
        partialsSinceFull++
        return if (partialsSinceFull >= ghostClearInterval) {
            partialsSinceFull = 0
            RefreshMode.FULL
        } else {
            RefreshMode.PARTIAL
        }
    }

    /** Setzt den Promotion-Zähler zurück (z. B. nach einem extern ausgelösten FULL). */
    fun reset() {
        partialsSinceFull = 0
    }

    companion object {
        /** Standard: alle 6 Teil-Refreshes ein GC-Full gegen Ghosting (wie `OnyxRefresher`). */
        const val DEFAULT_GHOST_CLEAR_INTERVAL = 6

        /**
         * Region-Merge: umschließende Bounding-Box aller schmutzigen Rechtecke
         * (null = nichts zu refreshen). So wird statt vieler kleiner Teil-Refreshes
         * genau ein Rechteck aktualisiert.
         */
        fun mergeRegions(regions: List<Region>): Region? {
            if (regions.isEmpty()) return null
            val left = regions.minOf { it.x }
            val top = regions.minOf { it.y }
            val right = regions.maxOf { it.x + it.width }
            val bottom = regions.maxOf { it.y + it.height }
            return Region(x = left, y = top, width = right - left, height = bottom - top)
        }
    }
}
