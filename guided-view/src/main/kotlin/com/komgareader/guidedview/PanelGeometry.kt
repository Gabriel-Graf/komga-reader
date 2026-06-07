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

    /** Größter normalisierter Flächenanteil (w*h) unter den Panels; 0 wenn leer. */
    fun maxAreaFraction(panels: List<NormRect>): Float =
        panels.maxOfOrNull { it.width * it.height } ?: 0f

    /**
     * Skalierungsfaktor, mit dem [panel] (bild-normalisiert) im Viewport bildschirmfüllend wird,
     * unter Berücksichtigung des bei ContentScale.Fit dargestellten Content-Rechtecks
     * ([contentW]x[contentH]) innerhalb des Viewports ([viewportW]x[viewportH]). Pivot = Panel-Mitte.
     *
     * **Contain** (`min(sx,sy)`): das ganze Panel bleibt sichtbar, es wird NIE beschnitten —
     * der Zoom überragt das Panel nicht. Ein seiten-breites/-hohes Panel füllt seine limitierende
     * Achse bereits, daher ist dort Faktor ≈ 1 das korrekte (Crop-freie) Maximum.
     */
    fun fitScale(
        panel: NormRect,
        contentW: Float, contentH: Float,
        viewportW: Float, viewportH: Float,
        marginFraction: Float,
    ): Float {
        val panelW = (panel.width * contentW).coerceAtLeast(1f)
        val panelH = (panel.height * contentH).coerceAtLeast(1f)
        val sx = viewportW / panelW
        val sy = viewportH / panelH
        return (1f - 2f * marginFraction) * minOf(sx, sy)
    }
}
