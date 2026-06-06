package com.komgareader.data.repository

import com.komgareader.data.db.ShelfDao
import com.komgareader.data.db.ShelfEntity
import com.komgareader.domain.model.ContentType
import com.komgareader.domain.model.Shelf
import com.komgareader.domain.repository.ShelfRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomShelfRepository(private val dao: ShelfDao) : ShelfRepository {

    override val shelves: Flow<List<Shelf>> = dao.observeAll().map { entities ->
        entities.map(::toShelf)
    }

    override suspend fun add(shelf: Shelf) {
        dao.insert(toEntity(shelf))
    }

    override suspend fun delete(id: Long) {
        dao.deleteById(id)
    }

    private fun toShelf(entity: ShelfEntity): Shelf = Shelf(
        id = entity.id,
        name = entity.name,
        contentType = runCatching { ContentType.valueOf(entity.contentType) }
            .getOrDefault(ContentType.COMIC),
        sourceIds = entity.sourceIds
            .split(",")
            .mapNotNull { it.trim().toLongOrNull() },
    )

    private fun toEntity(shelf: Shelf): ShelfEntity = ShelfEntity(
        id = shelf.id,
        name = shelf.name,
        contentType = shelf.contentType.name,
        sourceIds = shelf.sourceIds.joinToString(","),
    )
}
