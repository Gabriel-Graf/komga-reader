package com.komgareader.data.repository

import com.komgareader.data.db.ReadProgressDao
import com.komgareader.data.db.ReadProgressEntity
import com.komgareader.domain.repository.LocalReadProgress
import com.komgareader.domain.repository.ReadProgressRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Room-Implementierung von [ReadProgressRepository]. */
class RoomReadProgressRepository(private val dao: ReadProgressDao) : ReadProgressRepository {

    override val all: Flow<Map<String, LocalReadProgress>> =
        dao.observeAll().map { list -> list.associate { it.bookRemoteId to it.toDomain() } }

    override suspend fun markProgress(
        sourceId: Long,
        bookRemoteId: String,
        page: Int,
        completed: Boolean,
        totalPages: Int,
    ) {
        val existing = dao.get(bookRemoteId)
        // Kein Regress: die zuletzt bekannte (lokale) Seite bleibt mindestens erhalten.
        val mergedPage = maxOf(page, existing?.page ?: 0)
        dao.put(
            ReadProgressEntity(
                bookRemoteId = bookRemoteId,
                sourceId = sourceId,
                page = mergedPage,
                completed = completed || (existing?.completed ?: false),
                totalPages = totalPages,
                dirty = true,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    override suspend fun dirty(): List<LocalReadProgress> = dao.dirty().map { it.toDomain() }

    override suspend fun markSynced(bookRemoteId: String) = dao.markClean(bookRemoteId)

    private fun ReadProgressEntity.toDomain() = LocalReadProgress(
        bookRemoteId = bookRemoteId,
        sourceId = sourceId,
        page = page,
        completed = completed,
        totalPages = totalPages,
        dirty = dirty,
        updatedAt = updatedAt,
    )
}
