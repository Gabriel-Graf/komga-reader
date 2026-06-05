package com.komgareader.domain.usecase

import com.komgareader.domain.model.ReadProgress
import kotlin.test.Test
import kotlin.test.assertSame

class ResolveProgressConflictTest {

    private val resolve = ResolveProgressConflict()
    private fun progress(page: Int, updatedAt: Long, dirty: Boolean = false) =
        ReadProgress(bookId = 1, page = page, totalPages = 100, dirty = dirty, updatedAt = updatedAt)

    @Test
    fun `juengster Stand gewinnt — lokal neuer`() {
        val local = progress(page = 50, updatedAt = 2000)
        val remote = progress(page = 30, updatedAt = 1000)
        assertSame(local, resolve(local, remote))
    }

    @Test
    fun `juengster Stand gewinnt — remote neuer`() {
        val local = progress(page = 50, updatedAt = 1000)
        val remote = progress(page = 70, updatedAt = 3000)
        assertSame(remote, resolve(local, remote))
    }

    @Test
    fun `fehlender Remote-Stand — lokal gewinnt`() {
        val local = progress(page = 50, updatedAt = 1000)
        assertSame(local, resolve(local, null))
    }

    @Test
    fun `gleicher Zeitstempel — lokaler Stand gewinnt`() {
        val local = progress(page = 50, updatedAt = 1000, dirty = true)
        val remote = progress(page = 40, updatedAt = 1000)
        assertSame(local, resolve(local, remote))
    }
}
