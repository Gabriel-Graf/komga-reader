package com.komgareader.app.data

import com.komgareader.domain.model.CollectionKind
import com.komgareader.domain.model.CollectionMember
import com.komgareader.domain.model.SyncStatus
import com.komgareader.domain.model.UserCollection
import com.komgareader.domain.repository.CollectionRepository
import com.komgareader.domain.repository.CollectionSyncLink
import com.komgareader.domain.model.SourceKind
import com.komgareader.domain.source.CollectionSyncSource
import com.komgareader.domain.model.RemoteCollection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CollectionSyncManagerTest {

    private class FakeSource(
        override val id: Long,
        private val canWrite: Boolean,
        /** SERIES-Sammlungen am Server (Komga: collections). */
        val existing: MutableList<RemoteCollection> = mutableListOf(),
        /** BOOK-Sammlungen am Server (Komga: readlists) — getrennt, sonst tauchen SERIES doppelt auf. */
        val existingBooks: MutableList<RemoteCollection> = mutableListOf(),
        private val failOnCreate: Boolean = false,
    ) : CollectionSyncSource {
        override val name = "fake$id"
        override val kind = SourceKind.KOMGA
        var lastCreate: Pair<String, List<String>>? = null
        var lastUpdate: Triple<String, String, List<String>>? = null
        /** Namen aller create()-Aufrufe — für die „kein Push"-Assertion im pullOnlySync-Test. */
        val createdNames = mutableListOf<String>()
        /** Namen aller update()-Aufrufe — eine adoptierte (umbenannte) Sammlung zählt auch als Push. */
        val updatedNames = mutableListOf<String>()

        private fun storeFor(kind: CollectionKind) =
            if (kind == CollectionKind.SERIES) existing else existingBooks

        override suspend fun canWriteCollections() = canWrite
        override suspend fun listCollections(kind: CollectionKind) = storeFor(kind).toList()
        override suspend fun createCollection(kind: CollectionKind, name: String, memberRemoteIds: List<String>): RemoteCollection {
            if (failOnCreate) throw RuntimeException("boom")
            lastCreate = name to memberRemoteIds
            createdNames += name
            val rc = RemoteCollection("remote-$name-$id", name, memberRemoteIds, updatedAt = 0L)
            storeFor(kind) += rc
            return rc
        }
        override suspend fun updateCollection(kind: CollectionKind, remoteId: String, name: String, memberRemoteIds: List<String>) {
            lastUpdate = Triple(remoteId, name, memberRemoteIds)
            updatedNames += name
        }
        override suspend fun deleteCollection(kind: CollectionKind, remoteId: String) {
            storeFor(kind).removeAll { it.remoteId == remoteId }
        }
    }

    private class FakeCollectionRepo : CollectionRepository {
        /** Letzter geschriebener Link je sourceId — die push-Tests prüfen darüber. */
        val links = mutableMapOf<Long, CollectionSyncLink>()
        /** Vollständige Link-Tabelle je collectionId — für fullSync (Plan-Eingabe). */
        val linksByCollection = mutableMapOf<Long, MutableList<CollectionSyncLink>>()
        val storedCollections = mutableMapOf<Long, UserCollection>()
        private var nextId = 1L

        fun seedCollection(name: String, kind: CollectionKind, members: List<CollectionMember>): Long {
            val id = nextId++
            storedCollections[id] = UserCollection(id, name, kind, members)
            return id
        }

        fun seedLink(link: CollectionSyncLink) {
            linksByCollection.getOrPut(link.collectionId) { mutableListOf() } += link
            links[link.sourceId] = link
        }

        override val collections: Flow<List<UserCollection>>
            get() = flowOf(storedCollections.values.toList())

        override fun syncLinks(collectionId: Long): Flow<List<CollectionSyncLink>> =
            flowOf(linksByCollection[collectionId].orEmpty().toList())

        override suspend fun create(name: String, kind: CollectionKind): Long {
            val id = nextId++
            storedCollections[id] = UserCollection(id, name, kind, emptyList())
            return id
        }

        override suspend fun rename(collectionId: Long, name: String) = error("not used")
        override suspend fun delete(collectionId: Long) { storedCollections.remove(collectionId) }

        override suspend fun setMembers(collectionId: Long, members: List<CollectionMember>) {
            storedCollections[collectionId]?.let { storedCollections[collectionId] = it.copy(members = members) }
        }

        override suspend fun addMember(collectionId: Long, member: CollectionMember) = error("not used")
        override suspend fun removeMember(collectionId: Long, sourceId: Long, remoteId: String) = error("not used")

        override suspend fun updateSyncLink(link: CollectionSyncLink) {
            links[link.sourceId] = link
            val list = linksByCollection.getOrPut(link.collectionId) { mutableListOf() }
            list.removeAll { it.sourceId == link.sourceId }
            list += link
        }

        override suspend fun get(collectionId: Long): UserCollection? = storedCollections[collectionId]
        override suspend fun removeSource(sourceId: Long) {}
    }

    @Test
    fun `push creates per-source collection with that source subset`() = runBlocking {
        val s1 = FakeSource(1, canWrite = true)
        val s2 = FakeSource(2, canWrite = true)
        val repo = FakeCollectionRepo()
        val mgr = CollectionSyncManager(repo, resolver = { id -> mapOf(1L to s1, 2L to s2)[id] }, allSources = { emptyList() }, titleResolver = { _, _, _ -> null })
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
        val s1 = FakeSource(1, canWrite = true, existing = mutableListOf(RemoteCollection("pre1", "Mix", listOf("x"), updatedAt = 0L)))
        val repo = FakeCollectionRepo()
        val mgr = CollectionSyncManager(repo, resolver = { s1 }, allSources = { emptyList() }, titleResolver = { _, _, _ -> null })
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
        val mgr = CollectionSyncManager(repo, resolver = { s1 }, allSources = { emptyList() }, titleResolver = { _, _, _ -> null })
        mgr.push(UserCollection(10, "X", CollectionKind.SERIES, listOf(CollectionMember(1, "a", "A"))))
        assertEquals(SyncStatus.FORBIDDEN, repo.links.getValue(1).status)
        assertNull(s1.lastCreate)
    }

    @Test
    fun `push marks unsupported source (no CollectionSyncSource) UNSUPPORTED`() = runBlocking {
        val repo = FakeCollectionRepo()
        val mgr = CollectionSyncManager(repo, resolver = { null }, allSources = { emptyList() }, titleResolver = { _, _, _ -> null })  // z.B. OPDS
        mgr.push(UserCollection(10, "X", CollectionKind.SERIES, listOf(CollectionMember(5, "o", "O"))))
        assertEquals(SyncStatus.UNSUPPORTED, repo.links.getValue(5).status)
    }

    @Test
    fun `push isolates exception — failing source gets FORBIDDEN, other source still syncs`() = runBlocking {
        val s1 = FakeSource(1, canWrite = true, failOnCreate = true)
        val s2 = FakeSource(2, canWrite = true)
        val repo = FakeCollectionRepo()
        val mgr = CollectionSyncManager(repo, resolver = { id -> mapOf(1L to s1, 2L to s2)[id] }, allSources = { emptyList() }, titleResolver = { _, _, _ -> null })
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
        val s1 = FakeSource(1, canWrite = true, existing = mutableListOf(RemoteCollection("pre1", "Mix", listOf("x"), updatedAt = 0L)))
        val repo = FakeCollectionRepo()
        val mgr = CollectionSyncManager(repo, resolver = { s1 }, allSources = { emptyList() }, titleResolver = { _, _, _ -> null })
        val col = UserCollection(10, "Mix", CollectionKind.SERIES, listOf(CollectionMember(1, "a", "A")))
        mgr.push(col)
        assertEquals("pre1", repo.links.getValue(1).remoteCollectionId)
        assertEquals(SyncStatus.SYNCED, repo.links.getValue(1).status)
        assertEquals(false, repo.links.getValue(1).dirty)
    }

    @Test
    fun `fullSync entdeckt Server-Sammlung lokal und gibt vanished zurueck`() = runBlocking {
        // Server (Quelle 7) hat eine neue Sammlung „Neu", die lokal fehlt → Discovery.
        val source = FakeSource(7, canWrite = true, existing = mutableListOf(
            RemoteCollection("rc-neu", "Neu", listOf("s1"), updatedAt = 100L),
        ))
        val repo = FakeCollectionRepo()
        // Lokal: „Weg" war mit rc-weg synced, Server hat sie nicht mehr → vanished.
        val wegId = repo.seedCollection("Weg", CollectionKind.SERIES, listOf(CollectionMember(7, "x", "X")))
        repo.seedLink(CollectionSyncLink(wegId, 7, "rc-weg", SyncStatus.SYNCED, dirty = false, updatedAt = 50L))

        val mgr = CollectionSyncManager(repo, resolver = { source }, allSources = { listOf(7L to source) }, titleResolver = { _, _, _ -> null })
        val vanished = mgr.fullSync()

        val neu = repo.storedCollections.values.filter { it.name == "Neu" }
        assertEquals(1, neu.size, "genau eine Neu angelegt (kein BOOK-Duplikat)")
        assertEquals(CollectionKind.SERIES, neu.first().kind)
        assertEquals(1, vanished.size)
        assertEquals("Weg", vanished.first().name)
    }

    @Test
    fun `discovery loest echte Titel auf statt remoteId`() = runBlocking {
        val source = FakeSource(7, canWrite = true, existing = mutableListOf(
            RemoteCollection("rc-marvel", "Marvel", listOf("s1", "s2"), updatedAt = 100L),
        ))
        val repo = FakeCollectionRepo()
        val titles = mapOf("s1" to "Berserk", "s2" to "Saga")
        val manager = CollectionSyncManager(
            repo,
            resolver = { source },
            allSources = { listOf(7L to source) },
            titleResolver = { _, _, rid -> titles[rid] },
        )
        manager.fullSync()

        val marvel = repo.storedCollections.values.first { it.name == "Marvel" }
        assertEquals(listOf("Berserk", "Saga"), marvel.members.map { it.title })
        assertEquals(listOf("s1", "s2"), marvel.members.map { it.remoteId }) // remoteId unverändert (= Link)
    }

    @Test
    fun `pullOnlySync entdeckt Server-Sammlung aber pusht lokale NICHT`() = runBlocking {
        // Server (Quelle 7) hat „ServerOnly", lokal unbekannt → muss per Pull entdeckt werden.
        val source = FakeSource(7, canWrite = true, existing = mutableListOf(
            RemoteCollection("rc-server", "ServerOnly", listOf("s1"), updatedAt = 100L),
        ))
        val repo = FakeCollectionRepo()
        // Lokal-only „NurLokal" mit DIRTY-Link (remoteId=null) — in einem fullSync wäre das ein Push.
        val lokalId = repo.seedCollection("NurLokal", CollectionKind.SERIES, listOf(CollectionMember(7, "x", "X")))
        repo.seedLink(CollectionSyncLink(lokalId, 7, null, SyncStatus.DIRTY, dirty = true, updatedAt = 0L))

        val mgr = CollectionSyncManager(repo, resolver = { source }, allSources = { listOf(7L to source) }, titleResolver = { _, _, _ -> null })
        mgr.pullOnlySync()

        // Discovery lief: die Server-Sammlung ServerOnly ist jetzt lokal vorhanden.
        assertTrue(
            repo.storedCollections.values.any { it.name == "ServerOnly" },
            "pullOnlySync muss die Server-Sammlung ServerOnly lokal entdecken",
        )
        // KEIN Push: NurLokal wurde weder erstellt noch (per Adopt) aktualisiert.
        assertTrue(
            "NurLokal" !in source.createdNames,
            "pullOnlySync darf die lokale Sammlung NurLokal NICHT zum Server pushen (create), war: ${source.createdNames}",
        )
        assertTrue(
            "NurLokal" !in source.updatedNames,
            "pullOnlySync darf NurLokal NICHT zum Server pushen (update), war: ${source.updatedNames}",
        )
    }
}
