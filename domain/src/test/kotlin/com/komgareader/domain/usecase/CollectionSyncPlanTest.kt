package com.komgareader.domain.usecase

import com.komgareader.domain.model.CollectionKind
import com.komgareader.domain.model.CollectionMember
import com.komgareader.domain.model.RemoteCollection
import com.komgareader.domain.model.SyncStatus
import com.komgareader.domain.model.UserCollection
import com.komgareader.domain.repository.CollectionSyncLink
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CollectionSyncPlanTest {

    private fun link(colId: Long, srcId: Long, remoteId: String?, dirty: Boolean, updatedAt: Long) =
        CollectionSyncLink(colId, srcId, remoteId, if (dirty) SyncStatus.DIRTY else SyncStatus.SYNCED, dirty, updatedAt)

    private fun col(id: Long, name: String, members: List<CollectionMember>) =
        UserCollection(id, name, CollectionKind.SERIES, members)

    private fun member(srcId: Long, remoteId: String) = CollectionMember(srcId, remoteId, remoteId)

    @Test fun `Server hat Sammlung lokal fehlt - createLocal (Erstverbindung)`() {
        val remote = RemoteCollection("rc1", "Marvel", listOf("s1", "s2"), updatedAt = 100)
        val plan = planCollectionSync(emptyList(), emptyMap(), mapOf(7L to listOf(remote)), kind = CollectionKind.SERIES)
        assertEquals(1, plan.createLocal.size)
        assertEquals("rc1", plan.createLocal.first().remote.remoteId)
        assertEquals(7L, plan.createLocal.first().sourceId)
        assertTrue(plan.pushLocal.isEmpty())
        assertTrue(plan.pullOverwrite.isEmpty())
        assertTrue(plan.vanished.isEmpty())
    }

    @Test fun `beide vorhanden Server neuer - pullOverwrite`() {
        val local = col(1, "Marvel", listOf(member(7, "s1")))
        val links = mapOf(1L to listOf(link(1, 7, "rc1", dirty = false, updatedAt = 50)))
        val remote = RemoteCollection("rc1", "Marvel", listOf("s1", "s2"), updatedAt = 100)
        val plan = planCollectionSync(listOf(local), links, mapOf(7L to listOf(remote)), kind = CollectionKind.SERIES)
        assertEquals(1, plan.pullOverwrite.size)
        assertEquals(listOf("s1", "s2"), plan.pullOverwrite.first().serverMemberRemoteIds)
        assertEquals("rc1", plan.pullOverwrite.first().remoteId)
        assertEquals(100L, plan.pullOverwrite.first().serverUpdatedAt)
        assertTrue(plan.pushLocal.isEmpty())
    }

    @Test fun `beide vorhanden lokal neuer - pushLocal`() {
        val local = col(1, "Marvel", listOf(member(7, "s1"), member(7, "s9")))
        val links = mapOf(1L to listOf(link(1, 7, "rc1", dirty = true, updatedAt = 200)))
        val remote = RemoteCollection("rc1", "Marvel", listOf("s1"), updatedAt = 100)
        val plan = planCollectionSync(listOf(local), links, mapOf(7L to listOf(remote)), kind = CollectionKind.SERIES)
        assertEquals(listOf(1L), plan.pushLocal)
        assertTrue(plan.pullOverwrite.isEmpty())
    }

    @Test fun `gleicher Zeitstempel - pushLocal (Tie-Break lokal)`() {
        val local = col(1, "Marvel", listOf(member(7, "s1")))
        val links = mapOf(1L to listOf(link(1, 7, "rc1", dirty = false, updatedAt = 100)))
        val remote = RemoteCollection("rc1", "Marvel", listOf("s1"), updatedAt = 100)
        val plan = planCollectionSync(listOf(local), links, mapOf(7L to listOf(remote)), kind = CollectionKind.SERIES)
        assertEquals(listOf(1L), plan.pushLocal)
    }

    @Test fun `nur lokal nie synced - pushLocal (anlegen)`() {
        val local = col(1, "Privat", listOf(member(7, "s1")))
        val links = mapOf(1L to listOf(link(1, 7, remoteId = null, dirty = true, updatedAt = 200)))
        val plan = planCollectionSync(listOf(local), links, mapOf(7L to emptyList()), kind = CollectionKind.SERIES)
        assertEquals(listOf(1L), plan.pushLocal)
        assertTrue(plan.vanished.isEmpty())
    }

    @Test fun `war synced am Server weg - vanished`() {
        val local = col(1, "Weg", listOf(member(7, "s1")))
        val links = mapOf(1L to listOf(link(1, 7, "rc1", dirty = false, updatedAt = 50)))
        val plan = planCollectionSync(listOf(local), links, mapOf(7L to emptyList()), kind = CollectionKind.SERIES)
        assertEquals(1, plan.vanished.size)
        assertEquals(1L, plan.vanished.first().collectionId)
        assertEquals("Weg", plan.vanished.first().name)
    }

    @Test fun `Match per Name wenn remoteId noch unbekannt`() {
        val local = col(1, "Marvel", listOf(member(7, "s1")))
        val links = mapOf(1L to listOf(link(1, 7, remoteId = null, dirty = true, updatedAt = 200)))
        val remote = RemoteCollection("rc1", "Marvel", listOf("s1"), updatedAt = 100)
        val plan = planCollectionSync(listOf(local), links, mapOf(7L to listOf(remote)), kind = CollectionKind.SERIES)
        assertEquals(listOf(1L), plan.pushLocal)
        assertTrue(plan.createLocal.isEmpty(), "kein Duplikat anlegen")
    }

    @Test fun `Multi-Source - eine Quelle pullt andere pusht`() {
        val local = col(1, "Misch", listOf(member(7, "a1"), member(8, "b1")))
        val links = mapOf(
            1L to listOf(
                link(1, 7, "rc7", dirty = false, updatedAt = 50),
                link(1, 8, "rc8", dirty = true, updatedAt = 300),
            ),
        )
        val remotePerSource = mapOf(
            7L to listOf(RemoteCollection("rc7", "Misch", listOf("a1", "a2"), updatedAt = 100)),
            8L to listOf(RemoteCollection("rc8", "Misch", listOf("b1"), updatedAt = 100)),
        )
        val plan = planCollectionSync(listOf(local), links, remotePerSource, kind = CollectionKind.SERIES)
        assertEquals(1, plan.pullOverwrite.size)
        assertEquals(7L, plan.pullOverwrite.first().sourceId)
        assertEquals(listOf(1L), plan.pushLocal)
    }
}
