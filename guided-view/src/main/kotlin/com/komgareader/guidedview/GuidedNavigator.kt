package com.komgareader.guidedview

/** Position im geführten Lesefluss: [page] = Seitenindex, [unit] = Navigations-Einheit (Panel-Index bzw. 0 = Vollseite). */
data class GuidedPosition(val page: Int, val unit: Int)

/**
 * Reine Index-Logik für die Panel-für-Panel-Navigation über Seitengrenzen hinweg.
 * [unitsAt] liefert die Anzahl Navigations-Einheiten einer Seite (immer >= 1;
 * eine Seite mit <2 erkannten Panels hat genau 1 Einheit = Vollseite).
 */
object GuidedNavigator {

    fun next(pos: GuidedPosition, pageCount: Int, unitsAt: (Int) -> Int): GuidedPosition? {
        if (pos.unit + 1 < unitsAt(pos.page)) return GuidedPosition(pos.page, pos.unit + 1)
        val nextPage = pos.page + 1
        if (nextPage >= pageCount) return null
        return GuidedPosition(nextPage, 0)
    }

    fun previous(pos: GuidedPosition, pageCount: Int, unitsAt: (Int) -> Int): GuidedPosition? {
        if (pos.unit > 0) return GuidedPosition(pos.page, pos.unit - 1)
        val prevPage = pos.page - 1
        if (prevPage < 0) return null
        return GuidedPosition(prevPage, unitsAt(prevPage) - 1)
    }
}
