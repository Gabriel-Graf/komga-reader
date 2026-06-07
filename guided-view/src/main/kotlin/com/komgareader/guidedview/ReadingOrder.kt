package com.komgareader.guidedview

/**
 * Sortiert Panel-Boxen in Lesereihenfolge: in Zeilen-Bänder gruppieren (vertikale Überlappung
 * mit dem aktuellen Band), Bänder oben→unten, je Band links→rechts (LTR) bzw. rechts→links (RTL).
 */
object ReadingOrder {

    fun sort(boxes: List<PanelRect>, direction: ReadingDirection): List<PanelRect> {
        if (boxes.isEmpty()) return boxes
        val byTop = boxes.sortedBy { it.y }
        val rows = mutableListOf<MutableList<PanelRect>>()
        for (b in byTop) {
            val row = rows.lastOrNull()
            if (row != null && overlapsRow(row, b)) row.add(b) else rows.add(mutableListOf(b))
        }
        return rows.flatMap { row ->
            val ltr = row.sortedBy { it.x }
            if (direction == ReadingDirection.RIGHT_TO_LEFT) ltr.reversed() else ltr
        }
    }

    /** true, wenn [b] vertikal hinreichend mit dem bisherigen Band überlappt (gleiche Zeile). */
    private fun overlapsRow(row: List<PanelRect>, b: PanelRect): Boolean {
        val top = row.minOf { it.y }
        val bottom = row.maxOf { it.y + it.height }
        val overlap = minOf(bottom, b.y + b.height) - maxOf(top, b.y)
        return overlap > b.height / 2
    }
}
