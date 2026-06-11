package com.komgareader.data.repository

import com.komgareader.data.db.CollectionDao
import com.komgareader.data.db.CollectionEntity
import com.komgareader.data.db.CollectionMemberEntity
import com.komgareader.data.db.CollectionSyncLinkEntity
import com.komgareader.domain.model.CollectionKind
import com.komgareader.domain.model.CollectionMember
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** In-Memory-Fake des CollectionDao — testet die Mapping-/Sync-Logik ohne echtes Room. */
private class FakeCollectionDao : CollectionDao {
    val collections = MutableStateFlow<List<CollectionEntity>>(emptyList())
    val members = MutableStateFlow<List<CollectionMemberEntity>>(emptyList())
    val links = MutableStateFlow<List<CollectionSyncLinkEntity>>(emptyList())
    private var nextId = 1L

    override fun observeCollections(): Flow<List<CollectionEntity>> = collections
    override fun observeMembers(): Flow<List<CollectionMemberEntity>> = members
    override fun observeLinks(collectionId: Long): Flow<List<CollectionSyncLinkEntity>> =
        links.map { it.filter { l -> l.collectionId == collectionId } }

    override suspend fun getCollection(id: Long): CollectionEntity? =
        collections.value.firstOrNull { it.id == id }

    override suspend fun getMembers(id: Long): List<CollectionMemberEntity> =
        members.value.filter { it.collectionId == id }.sortedBy { it.position }

    override suspend fun insertCollection(entity: CollectionEntity): Long {
        val id = if (entity.id == 0L) nextId++ else entity.id
        collections.value = collections.value.filterNot { it.id == id } + entity.copy(id = id)
        return id
    }

    override suspend fun renameCollection(id: Long, name: String) {
        collections.value = collections.value.map { if (it.id == id) it.copy(name = name) else it }
    }

    override suspend fun deleteCollection(id: Long) {
        collections.value = collections.value.filterNot { it.id == id }
    }

    override suspend fun clearMembers(id: Long) {
        members.value = members.value.filterNot { it.collectionId == id }
    }

    override suspend fun insertMembers(newMembers: List<CollectionMemberEntity>) {
        members.value = members.value + newMembers
    }

    override suspend fun upsertLink(link: CollectionSyncLinkEntity) {
        links.value = links.value.filterNot {
            it.collectionId == link.collectionId && it.sourceId == link.sourceId
        } + link
    }

    override suspend fun clearLinks(id: Long) {
        links.value = links.value.filterNot { it.collectionId == id }
    }

    override suspend fun collectionIdsWithMemberSource(sourceId: Long): List<Long> =
        members.value.filter { it.sourceId == sourceId }.map { it.collectionId }.distinct()

    override suspend fun collectionIdsWithLinkSource(sourceId: Long): List<Long> =
        links.value.filter { it.sourceId == sourceId }.map { it.collectionId }.distinct()

    override suspend fun clearMembersForSource(sourceId: Long) {
        members.value = members.value.filterNot { it.sourceId == sourceId }
    }

    override suspend fun clearLinksForSource(sourceId: Long) {
        links.value = links.value.filterNot { it.sourceId == sourceId }
    }

    override suspend fun memberCount(id: Long): Int =
        members.value.count { it.collectionId == id }

    override suspend fun linkCount(id: Long): Int =
        links.value.count { it.collectionId == id }
}

class RoomCollectionRepositoryTest {

    @Test
    fun `create dann setMembers liefert geordnete kanonische Collection`() = runTest {
        val dao = FakeCollectionDao()
        val repo = RoomCollectionRepository(dao)

        val id = repo.create("Lese gerade", CollectionKind.SERIES)
        repo.setMembers(id, listOf(CollectionMember(1, "a", "A"), CollectionMember(2, "b", "B")))

        val c = repo.collections.first().single()
        assertEquals("Lese gerade", c.name)
        assertEquals(listOf("a", "b"), c.members.map { it.remoteId })

        val linksList = repo.syncLinks(id).first()
        val linksMap = linksList.associateBy { link -> link.sourceId }
        assertEquals(true, linksMap.getValue(1L).dirty)
        assertEquals(true, linksMap.getValue(2L).dirty)
    }

    @Test
    fun `delete räumt Members und SyncLinks mit — keine Orphans`() = runTest {
        val dao = FakeCollectionDao()
        val repo = RoomCollectionRepository(dao)

        val id = repo.create("Orphan-Test", CollectionKind.SERIES)
        repo.setMembers(id, listOf(CollectionMember(1, "a", "A"), CollectionMember(2, "b", "B")))

        // Vor dem Löschen: Collection + Members + Links vorhanden
        assertEquals(1, repo.collections.first().size)
        assertEquals(2, repo.syncLinks(id).first().size)

        repo.delete(id)

        // Collection weg
        assertTrue(repo.collections.first().isEmpty(), "collections soll leer sein nach delete")
        // get() liefert null
        assertEquals(null, repo.get(id), "get(id) soll null liefern nach delete")
        // Keine verwaisten SyncLinks
        assertTrue(repo.syncLinks(id).first().isEmpty(), "syncLinks soll leer sein — keine Orphans")
        // Keine verwaisten Members im DAO
        assertTrue(dao.members.value.none { it.collectionId == id }, "dao.members soll keine Orphan-Rows enthalten")
    }

    @Test
    fun `removeSource loescht Member und Links nur dieser Quelle`() = runTest {
        val dao = FakeCollectionDao()
        val repo = RoomCollectionRepository(dao)

        // Eine Sammlung über zwei Quellen (src=7 und src=8).
        val id = repo.create("Gemischt", CollectionKind.SERIES)
        repo.setMembers(
            id,
            listOf(CollectionMember(7, "s7a", "S7A"), CollectionMember(8, "s8a", "S8A")),
        )

        repo.removeSource(7)

        // Sammlung bleibt erhalten, nur die src7-Member sind weg, src8 bleibt.
        val c = repo.collections.first().single()
        assertEquals(id, c.id)
        assertEquals(listOf(8L), c.members.map { it.sourceId })
        assertTrue(dao.members.value.none { it.sourceId == 7L }, "src7-Member sollen weg sein")
        assertTrue(dao.members.value.any { it.sourceId == 8L }, "src8-Member sollen bleiben")
        // Sync-Links analog: src7-Link weg, src8-Link bleibt.
        assertTrue(dao.links.value.none { it.sourceId == 7L }, "src7-Link soll weg sein")
        assertTrue(dao.links.value.any { it.sourceId == 8L }, "src8-Link soll bleiben")
    }

    @Test
    fun `removeSource entfernt eine reine Single-Source-Sammlung ganz`() = runTest {
        val dao = FakeCollectionDao()
        val repo = RoomCollectionRepository(dao)

        // Sammlung nur mit src7-Member (+ src7-Link via setMembers).
        val id = repo.create("Nur Quelle 7", CollectionKind.SERIES)
        repo.setMembers(id, listOf(CollectionMember(7, "s7a", "S7A")))

        repo.removeSource(7)

        // Komplett leer geworden UND von src7 berührt -> Sammlung ganz gelöscht.
        assertTrue(repo.collections.first().isEmpty(), "collections soll leer sein")
        assertEquals(null, repo.get(id), "get(id) soll null liefern")
        assertTrue(dao.members.value.isEmpty(), "keine Orphan-Member")
        assertTrue(dao.links.value.isEmpty(), "keine Orphan-Links")
    }

    @Test
    fun `removeSource laesst fremde leere Sammlung unberuehrt`() = runTest {
        val dao = FakeCollectionDao()
        val repo = RoomCollectionRepository(dao)

        // A: frisch angelegt, ohne Member/Links — von src7 nie berührt.
        val idA = repo.create("Leer-A", CollectionKind.SERIES)
        // B: nur src7-Member.
        val idB = repo.create("Quelle-7-B", CollectionKind.SERIES)
        repo.setMembers(idB, listOf(CollectionMember(7, "s7a", "S7A")))

        repo.removeSource(7)

        // B weg (leer + berührt), A bleibt erhalten (nie berührt -> nicht in 'affected').
        val remaining = repo.collections.first()
        assertEquals(listOf(idA), remaining.map { it.id })
        assertEquals(null, repo.get(idB), "B soll gelöscht sein")
    }
}
