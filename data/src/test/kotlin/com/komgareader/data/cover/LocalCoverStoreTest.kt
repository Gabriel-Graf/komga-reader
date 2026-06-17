package com.komgareader.data.cover

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** Pure cache-key + prune-plan logic of the precomputed local cover store. */
class LocalCoverStoreTest {

    @Test
    fun `cache key is deterministic for same inputs`() {
        val a = coverCacheKey("book-1", signature = "1024:1700000000000")
        val b = coverCacheKey("book-1", signature = "1024:1700000000000")
        assertEquals(a, b)
    }

    @Test
    fun `cache key changes when the file signature changes`() {
        val old = coverCacheKey("book-1", signature = "1024:1700000000000")
        val changed = coverCacheKey("book-1", signature = "2048:1700000000000")
        assertNotEquals(old, changed)
    }

    @Test
    fun `cache key changes per book`() {
        val a = coverCacheKey("book-1", signature = "1024:1")
        val b = coverCacheKey("book-2", signature = "1024:1")
        assertNotEquals(a, b)
    }

    @Test
    fun `prune plan deletes files whose key is not kept`() {
        val existing = setOf("aaa.png", "bbb.png", "ccc.png")
        val keep = setOf("aaa", "ccc")
        assertEquals(setOf("bbb.png"), coverPrunePlan(existing, keep))
    }

    @Test
    fun `prune plan keeps everything when all keys are current`() {
        val existing = setOf("aaa.png", "bbb.png")
        val keep = setOf("aaa", "bbb")
        assertTrue(coverPrunePlan(existing, keep).isEmpty())
    }

    @Test
    fun `prune plan ignores non-png entries`() {
        val existing = setOf("aaa.png", "notes.txt")
        val keep = emptySet<String>()
        assertEquals(setOf("aaa.png"), coverPrunePlan(existing, keep))
    }
}
