package com.komgareader.plugin.host

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PluginSignatureTest {
    @Test fun `sha256 ist deterministisch und 64 hex-zeichen`() {
        val d = PluginSignature.sha256(byteArrayOf(1, 2, 3))
        assertEquals(d, PluginSignature.sha256(byteArrayOf(1, 2, 3)))
        assertEquals(64, d.length)
    }

    @Test fun `unterschiedliche bytes unterschiedlicher digest`() {
        assertFalse(
            PluginSignature.sha256(byteArrayOf(1)) == PluginSignature.sha256(byteArrayOf(2)),
        )
    }

    @Test fun `matches ist case- und whitespace-tolerant`() {
        assertTrue(PluginSignature.matches("ABCDEF", " abcdef "))
        assertFalse(PluginSignature.matches("abc", "abd"))
    }
}
