package com.komgareader.domain.eink

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EinkProfilesTest {

    @Test
    fun `user override wins per axis over device default`() {
        val user = EinkContextProfile(refreshModeId = "speed", colorModeId = null)
        val device = EinkContextProfile(refreshModeId = "hd", colorModeId = "system")
        val resolved = resolveEinkProfile(user, device)
        assertEquals("speed", resolved.refreshModeId) // user wins
        assertEquals("system", resolved.colorModeId)  // falls back to device (user null)
    }

    @Test
    fun `null user falls back to device default`() {
        val device = EinkContextProfile(refreshModeId = "hd", colorModeId = "system")
        assertEquals(device, resolveEinkProfile(null, device))
    }

    @Test
    fun `both null yields untouched profile`() {
        val resolved = resolveEinkProfile(null, EinkContextProfile())
        assertNull(resolved.refreshModeId)
        assertNull(resolved.colorModeId)
    }

    @Test
    fun `all five contexts exist`() {
        assertEquals(5, EinkContext.entries.size)
    }
}
