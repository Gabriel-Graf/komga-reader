package com.komgareader.data.repository

import com.komgareader.data.db.ShelfDao
import com.komgareader.data.db.ShelfEntity
import com.komgareader.domain.model.ContentType
import com.komgareader.domain.model.Shelf
import com.komgareader.domain.model.ShelfSource
import com.komgareader.domain.repository.ShelfRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Kodiert [ShelfSource]-Listen in einen flachen String für Room.
 * Form: `sourceId=cid1,cid2|sourceId=...`. Container-IDs enthalten nie `|=,`
 * (Komga-Library-IDs sind alphanumerisch).
 */
object ShelfSourceCodec {
    fun encode(sources: List<ShelfSource>): String =
        sources.joinToString("|") { "${it.sourceId}=${it.containerIds.joinToString(",")}" }

    fun decode(raw: String): List<ShelfSource> =
        raw.split("|")
            .filter { it.isNotBlank() }
            .mapNotNull { part ->
                val eq = part.indexOf('=')
                if (eq < 0) return@mapNotNull null
                val sourceId = part.substring(0, eq).trim().toLongOrNull() ?: return@mapNotNull null
                val containers = part.substring(eq + 1)
                    .split(",")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                ShelfSource(sourceId = sourceId, containerIds = containers)
            }
}

class RoomShelfRepository(private val dao: ShelfDao) : ShelfRepository {

    override val shelves: Flow<List<Shelf>> = dao.observeAll().map { entities ->
        entities.map(::toShelf)
    }

    override suspend fun add(shelf: Shelf): Long = dao.insert(toEntity(shelf))

    override suspend fun update(shelf: Shelf) {
        dao.insert(toEntity(shelf)) // REPLACE-Insert aktualisiert per PrimaryKey
    }

    override suspend fun delete(id: Long) {
        dao.deleteById(id)
    }

    private fun toShelf(entity: ShelfEntity): Shelf = Shelf(
        id = entity.id,
        name = entity.name,
        sources = ShelfSourceCodec.decode(entity.sources),
        defaultContentType = entity.defaultContentType
            ?.let { runCatching { ContentType.valueOf(it) }.getOrNull() },
    )

    private fun toEntity(shelf: Shelf): ShelfEntity = ShelfEntity(
        id = shelf.id,
        name = shelf.name,
        sources = ShelfSourceCodec.encode(shelf.sources),
        defaultContentType = shelf.defaultContentType?.name,
    )
}
