package com.komgareader.app.data

import com.komgareader.domain.model.CollectionKind
import com.komgareader.domain.model.CollectionMember
import com.komgareader.domain.model.SyncStatus
import com.komgareader.domain.model.UserCollection
import com.komgareader.domain.repository.CollectionRepository
import com.komgareader.domain.repository.CollectionSyncLink
import com.komgareader.domain.model.SourceKind
import com.komgareader.domain.source.CollectionSyncSource
import com.komgareader.domain.source.RemoteCollection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class CollectionSyncManagerTest {

    private class FakeSource(
        override val id: Long,
        private val canWrite: Boolean,
        val existing: MutableList<RemoteCollection> = mutableListOf(),
        private val failOnCreate: Boolean = false,
    ) : CollectionSyncSource {
        override val name = "fake$id"
        override val kind = SourceKind.KOMGA
        var lastCreate: Pair<String, List<String>>? = null
        var lastUpdate: Triple<String, String, List<String>>? = null

        override suspend fun canWriteCollections() = canWrite
        override suspend fun listCollections(kind: CollectionKind) = existing.toList()
        override suspend fun createCollection(kind: CollectionKind, name: String, memberRemoteIds: List<String>): RemoteCollection {
            if (failOnCreate) throw RuntimeException("boom")
            lastCreate = name to memberRemoteIds
            val rc = RemoteCollection("remote-$name-$id", name, memberRemoteIds)
            existing += rc
            return rc
        }
        override suspend fun updateCollection(kind: CollectionKind, remoteId: String, name: String, memberRemoteIds: List<String>) {
            lastUpdate = Triple(remoteId, name, memberRemoteIds)
        }
        override suspend fun deleteCollection(kind: CollectionKind, remoteId: String) {
            existing.removeAll { it.remoteId == remoteId }
        }
    }

    private class FakeCollectionRepo : CollectionRepository {
        val links = mutableMapOf<Long, CollectionSyncLink>()

        override val collections: Flow<List<UserCollection>> = flowOf(emptyList())
        override fun syncLinks(collectionId: Long): Flow<List<CollectionSyncLink>> = flowOf(emptyList())
        override suspend fun create(name: String, kind: CollectionKind): Long = error("not used")
        override suspend fun rename(collectionId: Long, name: String) = error("not used")
        override suspend fun delete(collectionId: Long) = error("not used")
        override suspend fun setMembers(collectionId: Long, members: List<CollectionMember>) = Unit
        override suspend fun addMember(collectionId: Long, member: CollectionMember) = error("not used")
        override suspend fun removeMember(collectionId: Long, sourceId: Long, remoteId: String) = error("not used")
        override suspend fun updateSyncLink(link: CollectionSyncLink) { links[link.sourceId] = link }
        override suspend fun get(collectionId: Long): UserCollection? = null
    }

    @Test
    fun `push creates per-source collection with that source subset`() = runBlocking {
        val s1 = FakeSource(1, canWrite = true)
        val s2 = FakeSource(2, canWrite = true)
        val repo = FakeCollectionRepo()
        val mgr = CollectionSyncManager(repo, resolver = { id -> mapOf(1L to s1, 2L to s2)[id] })
        val col = UserCollection(
            id = 10, name = "Mix", kind = CollectionKind.SERIES,
            members = listOf(CollectionMember(1, "a", "A"), CollectionMember(2, "b", "B"), CollectionMember(1, "c", "C")),
        )
        mgr.push(col)
        assertEquals("a,c" to true, (s1.lastCreate!!.second.joinToString(",")) to (s1.lastCreate!!.first == "Mix"))
        assertEquals(listOf("b"), s2.lastCreate!!.second)
        assertEquals(SyncStatus.SYNCED, repo.links.getValue(1).status)
        assertEquals(SyncStatus.SYNCED, repo.links.getValue(2).status)
    }

    @Test
    fun `push adopts existing remote collection by name instead of creating`() = runBlocking {
        val s1 = FakeSource(1, canWrite = true, existing = mutableListOf(RemoteCollection("pre1", "Mix", listOf("x"))))
        val repo = FakeCollectionRepo()
        val mgr = CollectionSyncManager(repo, resolver = { s1 })
        val col = UserCollection(10, "Mix", CollectionKind.SERIES, listOf(CollectionMember(1, "a", "A")))
        mgr.push(col)
        // adoptiert pre1 → update statt create
        assertNull(s1.lastCreate)
        assertEquals("pre1", s1.lastUpdate!!.first)
        assertEquals(listOf("a"), s1.lastUpdate!!.third)
    }

    @Test
    fun `push marks non-writable source FORBIDDEN, keeps local`() = runBlocking {
        val s1 = FakeSource(1, canWrite = false)
        val repo = FakeCollectionRepo()
        val mgr = CollectionSyncManager(repo, resolver = { s1 })
        mgr.push(UserCollection(10, "X", CollectionKind.SERIES, listOf(CollectionMember(1, "a", "A"))))
        assertEquals(SyncStatus.FORBIDDEN, repo.links.getValue(1).status)
        assertNull(s1.lastCreate)
    }

    @Test
    fun `push marks unsupported source (no CollectionSyncSource) UNSUPPORTED`() = runBlocking {
        val repo = FakeCollectionRepo()
        val mgr = CollectionSyncManager(repo, resolver = { null })  // z.B. OPDS
        mgr.push(UserCollection(10, "X", CollectionKind.SERIES, listOf(CollectionMember(5, "o", "O"))))
        assertEquals(SyncStatus.UNSUPPORTED, repo.links.getValue(5).status)
    }

    @Test
    fun `push isolates exception — failing source gets FORBIDDEN, other source still syncs`() = runBlocking {
        val s1 = FakeSource(1, canWrite = true, failOnCreate = true)
        val s2 = FakeSource(2, canWrite = true)
        val repo = FakeCollectionRepo()
        val mgr = CollectionSyncManager(repo, resolver = { id -> mapOf(1L to s1, 2L to s2)[id] })
        val col = UserCollection(
            id = 10, name = "Mix", kind = CollectionKind.SERIES,
            members = listOf(CollectionMember(1, "a", "A"), CollectionMember(2, "b", "B")),
        )
        mgr.push(col)
        assertEquals(SyncStatus.FORBIDDEN, repo.links.getValue(1).status)
        assertEquals(true, repo.links.getValue(1).dirty)
        assertEquals(SyncStatus.SYNCED, repo.links.getValue(2).status)
        assertEquals(false, repo.links.getValue(2).dirty)
    }

    @Test
    fun `push adopt stores remoteId of the adopted remote collection`() = runBlocking {
        val s1 = FakeSource(1, canWrite = true, existing = mutableListOf(RemoteCollection("pre1", "Mix", listOf("x"))))
        val repo = FakeCollectionRepo()
        val mgr = CollectionSyncManager(repo, resolver = { s1 })
        val col = UserCollection(10, "Mix", CollectionKind.SERIES, listOf(CollectionMember(1, "a", "A")))
        mgr.push(col)
        assertEquals("pre1", repo.links.getValue(1).remoteCollectionId)
        assertEquals(SyncStatus.SYNCED, repo.links.getValue(1).status)
        assertEquals(false, repo.links.getValue(1).dirty)
    }

    @Test fun `refresh merges server subsets back into canonical list`() = runBlocking {
        val s1 = FakeSource(1, canWrite = true, existing = mutableListOf(RemoteCollection("r1", "Mix", listOf("a", "d"))))
        val repo = FakeCollectionRepo()
        val mgr = CollectionSyncManager(repo, resolver = { s1 })
        val col = UserCollection(10, "Mix", CollectionKind.SERIES,
            listOf(CollectionMember(1, "a", "A"), CollectionMember(1, "b", "B")))  // b lokal, weg auf Server; d neu auf Server
        val merged = mgr.refresh(col)
        assertEquals(listOf("a", "d"), merged.members.map { it.remoteId })  // b entfernt, d angehängt
    }
}
