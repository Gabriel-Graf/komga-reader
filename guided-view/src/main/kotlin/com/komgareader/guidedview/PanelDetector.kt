package com.komgareader.guidedview

import com.komgareader.domain.render.RenderedPage

/**
 * Erkennt Panel-Grenzen in einer gerenderten Comic-Seite mittels rekursivem XY-Cut
 * (Projection-Profile). Reiner Kotlin-Algorithmus, kein OpenCV, voll host-testbar.
 *
 * @param darkThreshold  Helligkeitsschwelle [0..255]: Pixel mit (r+g+b)/3 < threshold gelten als dunkel.
 * @param gutterMaxInk   Max. Anteil dunkler Pixel in einer Gutter-Zeile/-Spalte (0..1).
 * @param minGutter      Mindestbreite eines Gutters in Pixeln.
 * @param minPanel       Mindestgröße eines Panels in Pixeln (Breite UND Höhe).
 */
class PanelDetector(
    private val darkThreshold: Int = 128,
    private val gutterMaxInk: Double = 0.02,
    private val minGutter: Int = 12,
    private val minPanel: Int = 20,
) {

    /**
     * Zerlegt [page] rekursiv in Panels und gibt sie in [direction]-Reihenfolge zurück.
     * Leere Seiten (zu wenig dunkle Pixel) → leere Liste.
     */
    fun detect(page: RenderedPage, direction: ReadingDirection): List<PanelRect> {
        val darkMap = buildDarkMap(page)

        // Leere Seite: Gesamt-Tinte unter einer kleinen Schwelle
        val totalDark = darkMap.sumOf { row -> row.count { it } }
        val minInkPixels = page.width * page.height * 0.005   // 0,5 % der Seite
        if (totalDark < minInkPixels) return emptyList()

        val region = PanelRect(0, 0, page.width, page.height)
        val panels = mutableListOf<PanelRect>()
        splitHorizontal(darkMap, region, direction, panels)
        return panels
    }

    // -----------------------------------------------------------------------
    // Rekursiver XY-Cut
    // -----------------------------------------------------------------------

    /** Spaltet eine Region entlang horizontaler Gutter; jedes Band wird vertikal weitergespalten. */
    private fun splitHorizontal(
        dark: Array<BooleanArray>,
        region: PanelRect,
        direction: ReadingDirection,
        result: MutableList<PanelRect>,
    ) {
        val gutters = findHorizontalGutters(dark, region)
        if (gutters.isEmpty()) {
            // Keine horizontale Unterteilung möglich → vertikal versuchen
            splitVertical(dark, region, direction, result)
            return
        }

        val bands = regionsFromHorizontalGutters(region, gutters)
        for (band in bands) {
            splitVertical(dark, band, direction, result)
        }
    }

    /** Spaltet eine Region entlang vertikaler Gutter; Blätter werden als Panels geliefert. */
    private fun splitVertical(
        dark: Array<BooleanArray>,
        region: PanelRect,
        direction: ReadingDirection,
        result: MutableList<PanelRect>,
    ) {
        val gutters = findVerticalGutters(dark, region)
        if (gutters.isEmpty()) {
            // Blatt: dieses Region ist ein Panel — Whitespace-Trim
            val trimmed = trimToContent(dark, region)
            if (trimmed != null) result.add(trimmed)
            return
        }

        val subRegions = regionsFromVerticalGutters(region, gutters)
        val ordered = when (direction) {
            ReadingDirection.LEFT_TO_RIGHT -> subRegions
            ReadingDirection.RIGHT_TO_LEFT -> subRegions.reversed()
        }
        for (sub in ordered) {
            // Sub-Regionen könnten ihrerseits horizontal teilbar sein
            splitHorizontal(dark, sub, direction, result)
        }
    }

    // -----------------------------------------------------------------------
    // Gutter-Erkennung
    // -----------------------------------------------------------------------

    /**
     * Findet alle horizontalen Gutter-Läufe (Zeilen mit < gutterMaxInk Tinte)
     * innerhalb der gegebenen [region]. Gibt Gutter als (startRow, endRow)-Intervalle zurück.
     */
    private fun findHorizontalGutters(dark: Array<BooleanArray>, region: PanelRect): List<IntRange> {
        val colCount = region.width
        val gutterRows = mutableListOf<IntRange>()
        var runStart = -1

        for (row in region.y until region.y + region.height) {
            val inkRatio = inkRatioInRow(dark, row, region.x, region.x + region.width)
            val isGutter = inkRatio < gutterMaxInk
            if (isGutter && runStart < 0) {
                runStart = row
            } else if (!isGutter && runStart >= 0) {
                val len = row - runStart
                if (len >= minGutter) gutterRows.add(runStart until row)
                runStart = -1
            }
        }
        // Lauf bis ans Ende
        if (runStart >= 0) {
            val len = (region.y + region.height) - runStart
            if (len >= minGutter) gutterRows.add(runStart until region.y + region.height)
        }
        return gutterRows
    }

    /**
     * Findet alle vertikalen Gutter-Läufe (Spalten mit < gutterMaxInk Tinte)
     * innerhalb der gegebenen [region].
     */
    private fun findVerticalGutters(dark: Array<BooleanArray>, region: PanelRect): List<IntRange> {
        val gutterCols = mutableListOf<IntRange>()
        var runStart = -1

        for (col in region.x until region.x + region.width) {
            val inkRatio = inkRatioInCol(dark, col, region.y, region.y + region.height)
            val isGutter = inkRatio < gutterMaxInk
            if (isGutter && runStart < 0) {
                runStart = col
            } else if (!isGutter && runStart >= 0) {
                val len = col - runStart
                if (len >= minGutter) gutterCols.add(runStart until col)
                runStart = -1
            }
        }
        if (runStart >= 0) {
            val len = (region.x + region.width) - runStart
            if (len >= minGutter) gutterCols.add(runStart until region.x + region.width)
        }
        return gutterCols
    }

    // -----------------------------------------------------------------------
    // Regionen aus Guttern ableiten
    // -----------------------------------------------------------------------

    private fun regionsFromHorizontalGutters(region: PanelRect, gutters: List<IntRange>): List<PanelRect> {
        val bands = mutableListOf<PanelRect>()
        var currentY = region.y

        for (gutter in gutters) {
            val bandH = gutter.first - currentY
            if (bandH >= minPanel) {
                bands.add(PanelRect(region.x, currentY, region.width, bandH))
            }
            currentY = gutter.last + 1
        }
        // Letztes Band nach dem letzten Gutter
        val remaining = (region.y + region.height) - currentY
        if (remaining >= minPanel) {
            bands.add(PanelRect(region.x, currentY, region.width, remaining))
        }
        return bands
    }

    private fun regionsFromVerticalGutters(region: PanelRect, gutters: List<IntRange>): List<PanelRect> {
        val cols = mutableListOf<PanelRect>()
        var currentX = region.x

        for (gutter in gutters) {
            val colW = gutter.first - currentX
            if (colW >= minPanel) {
                cols.add(PanelRect(currentX, region.y, colW, region.height))
            }
            currentX = gutter.last + 1
        }
        val remaining = (region.x + region.width) - currentX
        if (remaining >= minPanel) {
            cols.add(PanelRect(currentX, region.y, remaining, region.height))
        }
        return cols
    }

    // -----------------------------------------------------------------------
    // Hilfsfunktionen
    // -----------------------------------------------------------------------

    /** Berechnet den Anteil dunkler Pixel in einer Zeile [row] zwischen [xFrom..xTo). */
    private fun inkRatioInRow(dark: Array<BooleanArray>, row: Int, xFrom: Int, xTo: Int): Double {
        if (row < 0 || row >= dark.size) return 0.0
        val width = xTo - xFrom
        if (width <= 0) return 0.0
        var count = 0
        val rowArr = dark[row]
        for (x in xFrom until xTo) {
            if (x < rowArr.size && rowArr[x]) count++
        }
        return count.toDouble() / width
    }

    /** Berechnet den Anteil dunkler Pixel in einer Spalte [col] zwischen [yFrom..yTo). */
    private fun inkRatioInCol(dark: Array<BooleanArray>, col: Int, yFrom: Int, yTo: Int): Double {
        val height = yTo - yFrom
        if (height <= 0) return 0.0
        var count = 0
        for (y in yFrom until yTo) {
            if (y < dark.size && col < dark[y].size && dark[y][col]) count++
        }
        return count.toDouble() / height
    }

    /**
     * Trimmt eine Region auf die engste Bounding-Box ihrer dunklen Pixel.
     * Gibt null zurück, wenn die Region keine dunklen Pixel enthält oder zu klein ist.
     */
    private fun trimToContent(dark: Array<BooleanArray>, region: PanelRect): PanelRect? {
        var minX = Int.MAX_VALUE; var maxX = Int.MIN_VALUE
        var minY = Int.MAX_VALUE; var maxY = Int.MIN_VALUE

        for (y in region.y until region.y + region.height) {
            if (y >= dark.size) continue
            val row = dark[y]
            for (x in region.x until region.x + region.width) {
                if (x < row.size && row[x]) {
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
        }
        if (minX == Int.MAX_VALUE) return null
        val w = maxX - minX + 1
        val h = maxY - minY + 1
        if (w < minPanel || h < minPanel) return null
        return PanelRect(minX, minY, w, h)
    }

    /**
     * Baut eine 2D-Boolean-Map: [y][x] = true wenn Pixel dunkel.
     * Extrahiert r, g, b aus dem ARGB_8888-Int-Wert.
     */
    private fun buildDarkMap(page: RenderedPage): Array<BooleanArray> {
        val pixels = page.pixels
        return Array(page.height) { y ->
            BooleanArray(page.width) { x ->
                val argb = pixels[y * page.width + x]
                val r = (argb shr 16) and 0xFF
                val g = (argb shr 8)  and 0xFF
                val b =  argb         and 0xFF
                (r + g + b) / 3 < darkThreshold
            }
        }
    }
}
