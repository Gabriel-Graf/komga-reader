package com.komgareader.app.ui.collections

import com.komgareader.domain.model.CollectionKind
import com.komgareader.domain.model.CollectionMember
import com.komgareader.domain.repository.DownloadedBook
import kotlin.test.Test
import kotlin.test.assertEquals

class CollectionAvailabilityTest {

    private fun member(sourceId: Long, remoteId: String) =
        CollectionMember(sourceId, remoteId, title = remoteId)

    private fun download(sourceId: Long, series: String, book: String) =
        DownloadedBook(
            bookRemoteId = book,
            sourceId = sourceId,
            seriesRemoteId = series,
            title = book,
            format = "CBZ",
            localPath = "/x",
            totalPages = 1,
        )

    @Test
    fun `series download keys use the series id`() {
        val keys = downloadedMemberKeys(listOf(download(1L, series = "s1", book = "b1")), CollectionKind.SERIES)
        assertEquals(setOf(1L to "s1"), keys)
    }

    @Test
    fun `book download keys use the book id`() {
        val keys = downloadedMemberKeys(listOf(download(1L, series = "s1", book = "b1")), CollectionKind.BOOK)
        assertEquals(setOf(1L to "b1"), keys)
    }

    @Test
    fun `online sources show all their members`() {
        val members = listOf(member(1L, "s1"), member(1L, "s2"))
        val visible = visibleMembers(members, downloadedKeys = emptySet(), onlineSources = setOf(1L))
        assertEquals(members, visible)
    }

    @Test
    fun `offline source shows only downloaded members`() {
        val members = listOf(member(1L, "s1"), member(1L, "s2"))
        val visible = visibleMembers(members, downloadedKeys = setOf(1L to "s1"), onlineSources = emptySet())
        assertEquals(listOf(member(1L, "s1")), visible)
    }

    @Test
    fun `mixed sources - online server shows all, offline server only downloaded`() {
        val members = listOf(member(1L, "a"), member(2L, "b"), member(2L, "c"))
        val visible = visibleMembers(
            members,
            downloadedKeys = setOf(2L to "b"),
            onlineSources = setOf(1L), // server 1 online, server 2 offline
        )
        assertEquals(listOf(member(1L, "a"), member(2L, "b")), visible)
    }

    @Test
    fun `offline with nothing downloaded hides everything`() {
        val members = listOf(member(1L, "s1"), member(1L, "s2"))
        val visible = visibleMembers(members, downloadedKeys = emptySet(), onlineSources = emptySet())
        assertEquals(emptyList(), visible)
    }
}
