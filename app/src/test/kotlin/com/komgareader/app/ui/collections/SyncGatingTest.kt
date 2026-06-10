package com.komgareader.app.ui.collections

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SyncGatingTest {
    @Test fun `EINK erlaubt keinen aggressiven Sync`() {
        assertFalse(aggressiveSyncAllowed("EINK"))
    }
    @Test fun `SMARTPHONE erlaubt aggressiven Sync`() {
        assertTrue(aggressiveSyncAllowed("SMARTPHONE"))
    }
}
