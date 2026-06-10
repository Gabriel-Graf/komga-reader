package com.komgareader.data.repository

import com.komgareader.data.db.CollectionDao
import com.komgareader.data.db.CollectionEntity
import com.komgareader.data.db.CollectionMemberEntity
import com.komgareader.data.db.CollectionSyncLinkEntity
import com.komgareader.domain.model.CollectionKind
import com.komgareader.domain.model.CollectionMember
import com.komgareader.domain.model.SyncStatus
import com.komgareader.domain.model.UserCollection
import com.komgareader.domain.repository.CollectionRepository
import com.komgareader.domain.repository.CollectionSyncLink
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class RoomCollectionRepository(private val dao: CollectionDao) : CollectionRepository {

    override val collections: Flow<List<UserCollection>> =
        combine(dao.observeCollections(), dao.observeMembers()) { cols, members ->
            val byCol = members.groupBy { it.collectionId }
            cols.map { c ->
                UserCollection(
                    id = c.id,
                    name = c.name,
                    kind = CollectionKind.valueOf(c.kind),
                    members = byCol[c.id].orEmpty().sortedBy { it.position }
                        .map { CollectionMember(it.sourceId, it.remoteId, it.title) },
                )
            }
        }

    override fun syncLinks(collectionId: Long): Flow<List<CollectionSyncLink>> =
        dao.observeLinks(collectionId).map { rows -> rows.map(::toLink) }

    override suspend fun create(name: String, kind: CollectionKind): Long =
        dao.insertCollection(CollectionEntity(name = name, kind = kind.name))

    override suspend fun rename(collectionId: Long, name: String) =
        dao.renameCollection(collectionId, name)

    override suspend fun delete(collectionId: Long) {
        dao.clearLinks(collectionId)
        dao.clearMembers(collectionId)
        dao.deleteCollection(collectionId)
    }

    override suspend fun setMembers(collectionId: Long, members: List<CollectionMember>) {
        dao.replaceMembers(
            collectionId,
            members.mapIndexed { i, m ->
                CollectionMemberEntity(
                    collectionId = collectionId,
                    sourceId = m.sourceId,
                    remoteId = m.remoteId,
                    title = m.title,
                    position = i,
                )
            },
        )
        members.map { it.sourceId }.toSet().forEach { sourceId ->
            dao.upsertLink(
                CollectionSyncLinkEntity(
                    collectionId = collectionId,
                    sourceId = sourceId,
                    remoteCollectionId = null,
                    status = SyncStatus.DIRTY.name,
                    dirty = true,
                    updatedAt = nowMillis(),
                ),
            )
        }
    }

    override suspend fun addMember(collectionId: Long, member: CollectionMember) {
        val current = currentMembers(collectionId)
        if (current.any { it.sourceId == member.sourceId && it.remoteId == member.remoteId }) return
        setMembers(collectionId, current + member)
    }

    override suspend fun removeMember(collectionId: Long, sourceId: Long, remoteId: String) {
        val current = currentMembers(collectionId)
        setMembers(collectionId, current.filterNot { it.sourceId == sourceId && it.remoteId == remoteId })
    }

    override suspend fun updateSyncLink(link: CollectionSyncLink) {
        dao.upsertLink(
            CollectionSyncLinkEntity(
                collectionId = link.collectionId,
                sourceId = link.sourceId,
                remoteCollectionId = link.remoteCollectionId,
                status = link.status.name,
                dirty = link.dirty,
                updatedAt = link.updatedAt,  // Caller-Timestamp übernehmen (z.B. Server-Stand bei Pull)
            ),
        )
    }

    override suspend fun get(collectionId: Long): UserCollection? {
        val c = dao.getCollection(collectionId) ?: return null
        return UserCollection(
            id = c.id,
            name = c.name,
            kind = CollectionKind.valueOf(c.kind),
            members = dao.getMembers(collectionId).map { CollectionMember(it.sourceId, it.remoteId, it.title) },
        )
    }

    override suspend fun removeSource(sourceId: Long) {
        // Vor dem Löschen: welche Sammlungen berührt diese Quelle überhaupt?
        val affected = (dao.collectionIdsWithMemberSource(sourceId) + dao.collectionIdsWithLinkSource(sourceId)).toSet()
        dao.clearMembersForSource(sourceId)
        dao.clearLinksForSource(sourceId)
        // Nur die berührten Sammlungen, die jetzt komplett leer sind, ganz entfernen.
        affected.forEach { id ->
            if (dao.memberCount(id) == 0 && dao.linkCount(id) == 0) {
                dao.deleteCollection(id)
            }
        }
    }

    private suspend fun currentMembers(collectionId: Long): List<CollectionMember> =
        dao.getMembers(collectionId).map { CollectionMember(it.sourceId, it.remoteId, it.title) }

    private fun toLink(e: CollectionSyncLinkEntity) = CollectionSyncLink(
        collectionId = e.collectionId,
        sourceId = e.sourceId,
        remoteCollectionId = e.remoteCollectionId,
        status = SyncStatus.valueOf(e.status),
        dirty = e.dirty,
        updatedAt = e.updatedAt,
    )

    private fun nowMillis(): Long = System.currentTimeMillis()
}
