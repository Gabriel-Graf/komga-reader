package com.komgareader.guidedview

/** Panel in bild-normalisierten Koordinaten [0..1] relativ zur Seite. */
data class NormRect(val left: Float, val top: Float, val width: Float, val height: Float) {
    val centerX: Float get() = left + width / 2f
    val centerY: Float get() = top + height / 2f
    fun contains(x: Float, y: Float): Boolean =
        x >= left && x < left + width && y >= top && y < top + height
}

/**
 * Reine Geometrie für den Comic-Reader: Panel-Koordinaten normalisieren,
 * Tap-Treffer bestimmen, Zoom-Faktor berechnen. Kein Android, kein Viewport-Wissen —
 * die Compose-Schicht rechnet Viewport-Taps in bild-normalisierte Koordinaten um.
 */
object PanelGeometry {

    fun normalize(panel: PanelRect, pageW: Int, pageH: Int): NormRect =
        NormRect(
            left = panel.x.toFloat() / pageW,
            top = panel.y.toFloat() / pageH,
            width = panel.width.toFloat() / pageW,
            height = panel.height.toFloat() / pageH,
        )

    /** Index des Panels, das den (normalisierten) Punkt enthält, sonst null (Gutter/Rand). */
    fun hitTest(xNorm: Float, yNorm: Float, panels: List<NormRect>): Int? {
        val i = panels.indexOfFirst { it.contains(xNorm, yNorm) }
        return if (i >= 0) i else null
    }

    /**
     * Faktor, mit dem die Seite skaliert werden muss, damit [panel] (abzüglich
     * [marginFraction] Rand) bildschirmfüllend wird. Pivot ist die Panel-Mitte.
     */
    fun zoomScale(panel: NormRect, marginFraction: Float): Float {
        val largest = maxOf(panel.width, panel.height).coerceAtLeast(0.0001f)
        return (1f - 2f * marginFraction) / largest
    }
}
