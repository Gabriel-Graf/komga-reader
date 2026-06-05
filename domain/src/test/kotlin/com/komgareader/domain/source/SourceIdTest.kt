package com.komgareader.domain.source

import com.komgareader.domain.model.SourceKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class SourceIdTest {

    @Test
    fun `gleiche Eingabe ergibt stabil dieselbe ID`() {
        val a = SourceId.of("Mein Komga", SourceKind.KOMGA, "https://nas.local:25600")
        val b = SourceId.of("Mein Komga", SourceKind.KOMGA, "https://nas.local:25600")
        assertEquals(a, b)
    }

    @Test
    fun `unterschiedliche Eingabe ergibt unterschiedliche ID`() {
        val a = SourceId.of("Mein Komga", SourceKind.KOMGA, "https://nas.local:25600")
        val b = SourceId.of("Mein Komga", SourceKind.KOMGA, "https://other.local:25600")
        assertNotEquals(a, b)
    }

    @Test
    fun `ID ist nie negativ (Sign-Bit gelöscht)`() {
        val id = SourceId.of("x", SourceKind.PLUGIN, "y")
        assertTrue(id >= 0L)
    }

    @Test
    fun `lokale Quelle hat reservierte ID 0`() {
        assertEquals(0L, SourceId.LOCAL)
    }
}
