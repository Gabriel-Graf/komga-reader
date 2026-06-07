package com.komgareader.guidedview

/**
 * Teilt eine Region rekursiv an schmalen, durchgehenden DUNKLEN achsenparallelen Rahmenlinien auf,
 * die von helleren Bändern umgeben sind — trennt also schwarzumrandete / berührende Panels,
 * ohne gleichmäßig dunkle Zeichenflächen zu zersplittern.
 *
 * Schlüsselinvariante: Eine Bandkandidatin ist nur gültig, wenn die Nachbarzeilen/-spalten
 * AUSSERHALB des Bandes signifikant heller sind (darkFraction ≤ neighborMaxFraction). Das
 * verhindert, dass uniforme schwarze Flächen als Rahmenlinien erkannt werden.
 */
object BorderLineSplit {

    /**
     * Spaltet [box] rekursiv an achsenparallelen Rahmenlinien auf.
     *
     * @param dark                 Binärmaske: true = dunkles/Tinten-Pixel
     * @param width                Gesamtbreite des Bildes
     * @param height               Gesamthöhe des Bildes
     * @param box                  Zu untersuchende Region
     * @param lineDarkFraction     Mindest-Dunkelanteil einer Zeile/Spalte um als Linie zu gelten
     * @param neighborMaxFraction  Maximal erlaubter Dunkelanteil der Nachbarbänder (Kontextbedingung)
     * @param maxLineThickness     Maximale Dicke eines Linienbandes in Pixeln
     * @param minPanel             Minimale Panel-Größe (in der Schnittachse) nach dem Split
     * @param maxDepth             Maximale Rekursionstiefe
     */
    fun split(
        dark: BooleanArray,
        width: Int,
        height: Int,
        box: PanelRect,
        lineDarkFraction: Double = 0.7,
        neighborMaxFraction: Double = 0.4,
        maxLineThickness: Int = 24,
        minPanel: Int = 30,
        maxDepth: Int = 8,
    ): List<PanelRect> {
        if (maxDepth <= 0) return listOf(box)

        val hBand = findBestHorizontalBand(dark, width, height, box, lineDarkFraction, neighborMaxFraction, maxLineThickness, minPanel)
        val vBand = findBestVerticalBand(dark, width, height, box, lineDarkFraction, neighborMaxFraction, maxLineThickness, minPanel)

        // Wähle den stärkeren Schnitt (höhere mittlere Dunkelheit)
        val cut: CutBand? = when {
            hBand == null && vBand == null -> null
            hBand == null -> vBand
            vBand == null -> hBand
            hBand.score >= vBand.score -> hBand
            else -> vBand
        }

        if (cut == null) return listOf(box)

        return if (cut.horizontal) {
            // Horizontaler Schnitt: obere Box + untere Box, Band ausgeschlossen
            val topHeight = cut.start - box.y
            val bottomY = cut.endExcl
            val bottomHeight = box.y + box.height - bottomY
            if (topHeight < minPanel || bottomHeight < minPanel) return listOf(box)
            val top = PanelRect(box.x, box.y, box.width, topHeight)
            val bottom = PanelRect(box.x, bottomY, box.width, bottomHeight)
            split(dark, width, height, top, lineDarkFraction, neighborMaxFraction, maxLineThickness, minPanel, maxDepth - 1) +
                split(dark, width, height, bottom, lineDarkFraction, neighborMaxFraction, maxLineThickness, minPanel, maxDepth - 1)
        } else {
            // Vertikaler Schnitt: linke Box + rechte Box, Band ausgeschlossen
            val leftWidth = cut.start - box.x
            val rightX = cut.endExcl
            val rightWidth = box.x + box.width - rightX
            if (leftWidth < minPanel || rightWidth < minPanel) return listOf(box)
            val left = PanelRect(box.x, box.y, leftWidth, box.height)
            val right = PanelRect(rightX, box.y, rightWidth, box.height)
            split(dark, width, height, left, lineDarkFraction, neighborMaxFraction, maxLineThickness, minPanel, maxDepth - 1) +
                split(dark, width, height, right, lineDarkFraction, neighborMaxFraction, maxLineThickness, minPanel, maxDepth - 1)
        }
    }

    // ── Interne Datenklasse für ein gefundenes Linienband ─────────────────────────────────────

    private data class CutBand(
        val start: Int,       // erster Zeilenindex (inkl.) des Bandes
        val endExcl: Int,     // erster Index nach dem Band (exkl.)
        val score: Double,    // mittlere Dunkelheit des Bandes (höher = besser)
        val horizontal: Boolean,
    )

    // ── Horizontale Bandsuche ─────────────────────────────────────────────────────────────────

    /**
     * Sucht das beste horizontale Rahmenlinienband innerhalb von [box].
     * „Innen" bedeutet: Band liegt mindestens [minPanel] Pixel vom oberen und unteren Rand entfernt.
     */
    private fun findBestHorizontalBand(
        dark: BooleanArray,
        width: Int,
        height: Int,
        box: PanelRect,
        lineDarkFraction: Double,
        neighborMaxFraction: Double,
        maxLineThickness: Int,
        minPanel: Int,
    ): CutBand? {
        val yInnerStart = box.y + minPanel
        val yInnerEnd = box.y + box.height - minPanel  // exklusiv: Band darf nicht hier beginnen

        if (yInnerStart >= yInnerEnd) return null

        // Berechne Dunkelanteil für alle Zeilen im Box-Bereich
        val fractions = DoubleArray(box.height) { i ->
            darkFractionRow(dark, width, height, box, box.y + i)
        }

        var bestBand: CutBand? = null

        var y = yInnerStart
        while (y < yInnerEnd) {
            val localY = y - box.y
            if (fractions[localY] >= lineDarkFraction) {
                // Bandstart gefunden — wie weit reicht es?
                var bandEnd = y + 1
                while (bandEnd < yInnerEnd + maxLineThickness && bandEnd < box.y + box.height &&
                    fractions[bandEnd - box.y] >= lineDarkFraction
                ) {
                    bandEnd++
                }
                val thickness = bandEnd - y
                if (thickness <= maxLineThickness) {
                    // Prüfe Nachbarbänder (außerhalb des Bandes, innerhalb der Box)
                    val neighborAbove = neighborFractionAbove(fractions, box, y, neighborRows = 3)
                    val neighborBelow = neighborFractionBelow(fractions, box, bandEnd, neighborRows = 3)
                    if (neighborAbove <= neighborMaxFraction && neighborBelow <= neighborMaxFraction) {
                        val score = fractions.copyOfRange(localY, localY + thickness).average()
                        if (bestBand == null || score > bestBand.score) {
                            bestBand = CutBand(y, bandEnd, score, horizontal = true)
                        }
                    }
                }
                // Springe ans Bandende weiter
                y = bandEnd
            } else {
                y++
            }
        }
        return bestBand
    }

    // ── Vertikale Bandsuche ───────────────────────────────────────────────────────────────────

    /**
     * Sucht das beste vertikale Rahmenlinienband innerhalb von [box].
     * „Innen" bedeutet: Band liegt mindestens [minPanel] Pixel vom linken und rechten Rand entfernt.
     */
    private fun findBestVerticalBand(
        dark: BooleanArray,
        width: Int,
        height: Int,
        box: PanelRect,
        lineDarkFraction: Double,
        neighborMaxFraction: Double,
        maxLineThickness: Int,
        minPanel: Int,
    ): CutBand? {
        val xInnerStart = box.x + minPanel
        val xInnerEnd = box.x + box.width - minPanel

        if (xInnerStart >= xInnerEnd) return null

        val fractions = DoubleArray(box.width) { i ->
            darkFractionCol(dark, width, height, box, box.x + i)
        }

        var bestBand: CutBand? = null

        var x = xInnerStart
        while (x < xInnerEnd) {
            val localX = x - box.x
            if (fractions[localX] >= lineDarkFraction) {
                var bandEnd = x + 1
                while (bandEnd < xInnerEnd + maxLineThickness && bandEnd < box.x + box.width &&
                    fractions[bandEnd - box.x] >= lineDarkFraction
                ) {
                    bandEnd++
                }
                val thickness = bandEnd - x
                if (thickness <= maxLineThickness) {
                    val neighborLeft = neighborFractionLeft(fractions, box, x, neighborCols = 3)
                    val neighborRight = neighborFractionRight(fractions, box, bandEnd, neighborCols = 3)
                    if (neighborLeft <= neighborMaxFraction && neighborRight <= neighborMaxFraction) {
                        val score = fractions.copyOfRange(localX, localX + thickness).average()
                        if (bestBand == null || score > bestBand.score) {
                            bestBand = CutBand(x, bandEnd, score, horizontal = false)
                        }
                    }
                }
                x = bandEnd
            } else {
                x++
            }
        }
        return bestBand
    }

    // ── Nachbar-Dunkelheit-Helfer ─────────────────────────────────────────────────────────────

    /** Mittlere Dunkelheit der [neighborRows] Zeilen direkt ÜBER dem Band (relativ zur Box). */
    private fun neighborFractionAbove(fractions: DoubleArray, box: PanelRect, bandStartAbs: Int, neighborRows: Int): Double {
        val localStart = bandStartAbs - box.y
        if (localStart <= 0) return 0.0
        val from = maxOf(0, localStart - neighborRows)
        return fractions.copyOfRange(from, localStart).average()
    }

    /** Mittlere Dunkelheit der [neighborRows] Zeilen direkt UNTER dem Band. */
    private fun neighborFractionBelow(fractions: DoubleArray, box: PanelRect, bandEndAbs: Int, neighborRows: Int): Double {
        val localEnd = bandEndAbs - box.y
        if (localEnd >= fractions.size) return 0.0
        val to = minOf(fractions.size, localEnd + neighborRows)
        return fractions.copyOfRange(localEnd, to).average()
    }

    /** Mittlere Dunkelheit der [neighborCols] Spalten direkt LINKS vom Band. */
    private fun neighborFractionLeft(fractions: DoubleArray, box: PanelRect, bandStartAbs: Int, neighborCols: Int): Double {
        val localStart = bandStartAbs - box.x
        if (localStart <= 0) return 0.0
        val from = maxOf(0, localStart - neighborCols)
        return fractions.copyOfRange(from, localStart).average()
    }

    /** Mittlere Dunkelheit der [neighborCols] Spalten direkt RECHTS vom Band. */
    private fun neighborFractionRight(fractions: DoubleArray, box: PanelRect, bandEndAbs: Int, neighborCols: Int): Double {
        val localEnd = bandEndAbs - box.x
        if (localEnd >= fractions.size) return 0.0
        val to = minOf(fractions.size, localEnd + neighborCols)
        return fractions.copyOfRange(localEnd, to).average()
    }

    // ── Pixel-Dichte-Helfer (vom Spec vorgegeben) ────────────────────────────────────────────

    private fun darkFractionRow(dark: BooleanArray, width: Int, height: Int, box: PanelRect, y: Int): Double {
        if (y < 0 || y >= height) return 0.0
        var c = 0
        val x0 = box.x; val x1 = (box.x + box.width).coerceAtMost(width)
        for (x in x0 until x1) if (dark[y * width + x]) c++
        return c.toDouble() / (x1 - x0).coerceAtLeast(1)
    }

    private fun darkFractionCol(dark: BooleanArray, width: Int, height: Int, box: PanelRect, x: Int): Double {
        if (x < 0 || x >= width) return 0.0
        var c = 0
        val y0 = box.y; val y1 = (box.y + box.height).coerceAtMost(height)
        for (y in y0 until y1) if (dark[y * width + x]) c++
        return c.toDouble() / (y1 - y0).coerceAtLeast(1)
    }
}
