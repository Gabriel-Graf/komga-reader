package com.komgareader.domain.usecase

import com.komgareader.domain.model.CollectionMember
import com.komgareader.domain.model.SyncStatus
import kotlin.test.Test
import kotlin.test.assertEquals

class CollectionMergeTest {

    private fun m(source: Long, remote: String) = CollectionMember(source, remote, "T-$remote")

    @Test
    fun `groupBySource splits members by sourceId preserving order`() {
        val members = listOf(m(1, "a"), m(2, "b"), m(1, "c"))
        val grouped = groupBySource(members)
        assertEquals(listOf("a", "c"), grouped.getValue(1).map { it.remoteId })
        assertEquals(listOf("b"), grouped.getValue(2).map { it.remoteId })
    }

    @Test
    fun `mergeSubsets keeps canonical order, appends new remote members, drops removed`() {
        val canonical = listOf(m(1, "a"), m(2, "b"), m(1, "c"))
        val perSource = mapOf(
            1L to listOf("a", "c", "d"),
            2L to emptyList(),
        )
        val merged = mergeSubsets(canonical, perSource, titleFor = { _, r -> "srv-$r" })
        assertEquals(listOf("a" to 1L, "c" to 1L, "d" to 1L), merged.map { it.remoteId to it.sourceId })
        assertEquals("srv-d", merged.first { it.remoteId == "d" }.title)
    }

    @Test
    fun `mergeSubsets leaves members of non-syncable sources untouched`() {
        val canonical = listOf(m(9, "opds1"), m(1, "a"))
        val merged = mergeSubsets(canonical, mapOf(1L to listOf("a")), titleFor = { _, r -> r })
        assertEquals(listOf("opds1" to 9L, "a" to 1L), merged.map { it.remoteId to it.sourceId })
    }

    @Test
    fun `deriveStatus maps capability and dirty flag`() {
        assertEquals(SyncStatus.UNSUPPORTED, deriveStatus(syncable = false, canWrite = false, dirty = true))
        assertEquals(SyncStatus.FORBIDDEN, deriveStatus(syncable = true, canWrite = false, dirty = true))
        assertEquals(SyncStatus.DIRTY, deriveStatus(syncable = true, canWrite = true, dirty = true))
        assertEquals(SyncStatus.SYNCED, deriveStatus(syncable = true, canWrite = true, dirty = false))
    }
}
