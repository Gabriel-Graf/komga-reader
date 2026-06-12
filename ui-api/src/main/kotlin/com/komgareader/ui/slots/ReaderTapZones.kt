package com.komgareader.ui.slots

/**
 * Deklarative Tap-Zonen-Beschreibung der Reader-Region. Die **Geometrie gehört dem Host**
 * (endliches Vokabular), der Screen/Pack liefert pro Zone nur die **Aktion** — kein opaker
 * Modifier. Ersetzt das frühere bespoke `tapModifier` der [ReaderScaffoldState] (Sub-Projekt A1b).
 *
 * Additiv erweiterbar: weitere Geometrien (Hälften, Quadranten) kommen als neue Fälle hinzu, ohne
 * bestehende zu brechen. Die externe/Enum-Aktionsform (Tap-Zone→`TapAction`-Enum statt Callback)
 * folgt mit dem externen Pack-Lader (L1/L2).
 */
sealed interface ReaderTapZones {
    /** Horizontale Drittel: links/mitte/rechts → je eine Aktion. Heute die einzige Geometrie. */
    data class HorizontalThirds(
        val left: () -> Unit,
        val center: () -> Unit,
        val right: () -> Unit,
    ) : ReaderTapZones
}

/**
 * Pure Zonen-Dispatch: ruft anhand des **normalisierten** Tap-Anteils (x/Breite ∈ [0,1]) die
 * passende Zonen-Aktion. Die Drittel-Geometrie liegt damit an genau einer Stelle → unit-testbar
 * ohne Compose (siehe `ReaderTapZonesTest`). Grenzen wie zuvor im Host: `< 1/3` links, `> 2/3`
 * rechts, sonst Mitte (die Grenzanteile 1/3 und 2/3 selbst fallen auf Mitte).
 */
fun ReaderTapZones.HorizontalThirds.dispatch(xFraction: Float) = when {
    xFraction < 1f / 3f -> left()
    xFraction > 2f / 3f -> right()
    else -> center()
}
