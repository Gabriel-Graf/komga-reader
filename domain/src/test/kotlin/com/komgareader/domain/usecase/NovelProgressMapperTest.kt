package com.komgareader.domain.usecase

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Reiner Mapper für den Roman-Fortschritt: rechnet den geräteunabhängigen Anteil
 * (0.0..1.0) auf das grobe Prozent-Raster um, das zu Komga gesynct wird, und löst die
 * Wiederaufnahme-Quelle auf (Xpointer exakt vor %-Fallback).
 */
class NovelProgressMapperTest {

    private val mapper = NovelProgressMapper()

    @Test
    fun `toReadProgress setzt totalProgression als Prozent-Raster`() {
        // 0.42 → Seite 42 von 100 (page/totalPages == 0.42).
        val rp = mapper.toReadProgress(fraction = 0.42f)
        assertEquals(100, rp.totalPages)
        assertEquals(42, rp.page)
    }

    @Test
    fun `toReadProgress am Anfang ist Seite 1 von 100`() {
        // Komga-Seiten sind 1-basiert: 0%-Anteil meldet die erste Seite, nie Seite 0.
        val rp = mapper.toReadProgress(fraction = 0f)
        assertEquals(1, rp.page)
        assertEquals(100, rp.totalPages)
        assertEquals(false, rp.completed)
    }

    @Test
    fun `toReadProgress am Ende ist vollständig`() {
        val rp = mapper.toReadProgress(fraction = 1f)
        assertEquals(100, rp.page)
        assertEquals(100, rp.totalPages)
        assertEquals(true, rp.completed)
    }

    @Test
    fun `toReadProgress klemmt Anteile ausserhalb 0 bis 1`() {
        assertEquals(1, mapper.toReadProgress(fraction = -0.5f).page)
        assertEquals(100, mapper.toReadProgress(fraction = 1.5f).page)
    }

    @Test
    fun `resumeFraction nutzt den Anteil wenn kein Anker vorhanden`() {
        assertEquals(0.30f, mapper.resumeFraction(anchor = null, fraction = 0.30f), 0.0001f)
        assertEquals(0.30f, mapper.resumeFraction(anchor = "", fraction = 0.30f), 0.0001f)
    }

    @Test
    fun `resumeFraction klemmt den Anteil in 0 bis 1`() {
        assertEquals(0f, mapper.resumeFraction(anchor = null, fraction = -1f), 0.0001f)
        assertEquals(1f, mapper.resumeFraction(anchor = null, fraction = 2f), 0.0001f)
    }
}
