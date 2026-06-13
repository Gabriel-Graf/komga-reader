package com.komgareader.plugin.host

import com.komgareader.plugin.PluginAbi
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AbiGateTest {
    @Test fun acceptsSupportedRange() {
        assertTrue(AbiGate.isCompatible(PluginAbi.MIN_SUPPORTED))
        assertTrue(AbiGate.isCompatible(PluginAbi.VERSION))
    }

    @Test fun rejectsOutsideRange() {
        assertFalse(AbiGate.isCompatible(PluginAbi.MIN_SUPPORTED - 1))
        assertFalse(AbiGate.isCompatible(PluginAbi.VERSION + 1))
    }
}
