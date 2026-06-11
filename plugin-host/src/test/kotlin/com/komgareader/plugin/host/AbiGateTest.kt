package com.komgareader.plugin.host

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AbiGateTest {
    @Test fun acceptsV1AndV2() {
        assertTrue(AbiGate.isCompatible(1))
        assertTrue(AbiGate.isCompatible(2))
    }

    @Test fun rejectsBelowMinAndAboveVersion() {
        assertFalse(AbiGate.isCompatible(0))
        assertFalse(AbiGate.isCompatible(3))
    }
}
