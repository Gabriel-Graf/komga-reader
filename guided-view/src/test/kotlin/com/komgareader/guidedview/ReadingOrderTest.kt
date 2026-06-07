package com.komgareader.guidedview

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ReadingOrderTest {
    private val tl = PanelRect(0, 0, 40, 40)
    private val tr = PanelRect(60, 0, 40, 40)
    private val bl = PanelRect(0, 100, 40, 40)
    private val br = PanelRect(60, 100, 40, 40)

    @Test
    fun `LTR liest zeilenweise links nach rechts, oben nach unten`() {
        val out = ReadingOrder.sort(listOf(br, tr, bl, tl), ReadingDirection.LEFT_TO_RIGHT)
        assertEquals(listOf(tl, tr, bl, br), out)
    }

    @Test
    fun `RTL liest je Zeile rechts nach links`() {
        val out = ReadingOrder.sort(listOf(tl, tr, bl, br), ReadingDirection.RIGHT_TO_LEFT)
        assertEquals(listOf(tr, tl, br, bl), out)
    }

    @Test
    fun `leere Liste bleibt leer`() {
        assertEquals(emptyList(), ReadingOrder.sort(emptyList(), ReadingDirection.LEFT_TO_RIGHT))
    }
}
