package com.komgareader.plugin.host

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class PluginConfigHashTest {
    @Test fun `gleiche werte unabhängig der reihenfolge gleicher hash`() {
        val a = PluginConfigHash.of(mapOf("url" to "x", "apiKey" to "y"))
        val b = PluginConfigHash.of(mapOf("apiKey" to "y", "url" to "x"))
        assertEquals(a, b)
    }
    @Test fun `unterschiedliche werte unterschiedlicher hash`() {
        assertNotEquals(
            PluginConfigHash.of(mapOf("url" to "a")),
            PluginConfigHash.of(mapOf("url" to "b")),
        )
    }
    @Test fun `leere config liefert stabilen hash`() =
        assertEquals(PluginConfigHash.of(emptyMap()), PluginConfigHash.of(emptyMap()))
}
