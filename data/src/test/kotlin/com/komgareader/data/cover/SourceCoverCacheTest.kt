package com.komgareader.data.cover

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/** Pure cache-key logic of the source-cover cache (file IO is exercised on device). */
class SourceCoverCacheTest {

    @Test
    fun `key is deterministic for same inputs`() {
        assertEquals(
            sourceCoverKey(7L, "s1", isSeries = true),
            sourceCoverKey(7L, "s1", isSeries = true),
        )
    }

    @Test
    fun `key differs per source`() {
        assertNotEquals(sourceCoverKey(1L, "s1", true), sourceCoverKey(2L, "s1", true))
    }

    @Test
    fun `key differs per remote id`() {
        assertNotEquals(sourceCoverKey(7L, "s1", true), sourceCoverKey(7L, "s2", true))
    }

    @Test
    fun `series and book cover of the same id are distinct keys`() {
        assertNotEquals(sourceCoverKey(7L, "x", isSeries = true), sourceCoverKey(7L, "x", isSeries = false))
    }
}
