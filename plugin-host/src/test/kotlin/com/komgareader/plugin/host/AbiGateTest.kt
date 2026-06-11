package com.komgareader.plugin.host

import com.komgareader.plugin.PluginAbi
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AbiGateTest {
    @Test fun `aktuelle ABI ist kompatibel`() =
        assertTrue(AbiGate.isCompatible(PluginAbi.VERSION))

    @Test fun `min unterstuetzte ABI ist kompatibel`() =
        assertTrue(AbiGate.isCompatible(PluginAbi.MIN_SUPPORTED))

    @Test fun `zu alt ist inkompatibel`() =
        assertFalse(AbiGate.isCompatible(PluginAbi.MIN_SUPPORTED - 1))

    @Test fun `zu neu ist inkompatibel`() =
        assertFalse(AbiGate.isCompatible(PluginAbi.VERSION + 1))
}
