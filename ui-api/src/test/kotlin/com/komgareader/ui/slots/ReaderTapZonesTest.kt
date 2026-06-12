package com.komgareader.ui.slots

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Reine Geometrie-Tests des deklarativen Tap-Zonen-Dispatch (`HorizontalThirds.dispatch`) — ohne
 * Compose. Die Drittel-Grenzen leben an genau einer Stelle (ui-api), nicht mehr pro Reader inline.
 */
class ReaderTapZonesTest {

    private class Recorder {
        var fired: String? = null
        fun zones() = ReaderTapZones.HorizontalThirds(
            left = { fired = "left" },
            center = { fired = "center" },
            right = { fired = "right" },
        )
    }

    @Test
    fun `linker Anteil ruft die linke Zone`() {
        val r = Recorder()
        r.zones().dispatch(xFraction = 0.1f)
        assertEquals("left", r.fired)
    }

    @Test
    fun `mittlerer Anteil ruft die mittlere Zone`() {
        val r = Recorder()
        r.zones().dispatch(xFraction = 0.5f)
        assertEquals("center", r.fired)
    }

    @Test
    fun `rechter Anteil ruft die rechte Zone`() {
        val r = Recorder()
        r.zones().dispatch(xFraction = 0.9f)
        assertEquals("right", r.fired)
    }

    @Test
    fun `knapp unter einem Drittel ist noch links`() {
        val r = Recorder()
        r.zones().dispatch(xFraction = 1f / 3f - 0.001f)
        assertEquals("left", r.fired)
    }

    @Test
    fun `knapp über zwei Dritteln ist schon rechts`() {
        val r = Recorder()
        r.zones().dispatch(xFraction = 2f / 3f + 0.001f)
        assertEquals("right", r.fired)
    }

    @Test
    fun `die Drittel-Grenzen selbst fallen auf die Mitte`() {
        val lower = Recorder()
        lower.zones().dispatch(xFraction = 1f / 3f)
        assertEquals("center", lower.fired)

        val upper = Recorder()
        upper.zones().dispatch(xFraction = 2f / 3f)
        assertEquals("center", upper.fired)
    }
}
