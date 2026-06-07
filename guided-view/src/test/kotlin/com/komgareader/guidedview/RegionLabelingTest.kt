package com.komgareader.guidedview

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class RegionLabelingTest {
    private fun mask(vararg rows: String): Triple<BooleanArray, Int, Int> {
        val h = rows.size; val w = rows[0].length
        val m = BooleanArray(w * h)
        for (y in 0 until h) for (x in 0 until w) m[y * w + x] = rows[y][x] == 'b'
        return Triple(m, w, h)
    }

    @Test
    fun `zwei nicht-geflutete Regionen ergeben zwei Boxen`() {
        val (flooded, w, h) = mask(
            "##b##",
            "##b##",
        )
        val boxes = RegionLabeling.labelRegions(flooded, w, h)
        assertEquals(2, boxes.size)
        val sorted = boxes.sortedBy { it.x }
        assertEquals(PanelRect(0, 0, 2, 2), sorted[0])
        assertEquals(PanelRect(3, 0, 2, 2), sorted[1])
    }

    @Test
    fun `vollständig geflutet ergibt keine Box`() {
        val (flooded, w, h) = mask("bbb", "bbb")
        assertEquals(0, RegionLabeling.labelRegions(flooded, w, h).size)
    }
}
