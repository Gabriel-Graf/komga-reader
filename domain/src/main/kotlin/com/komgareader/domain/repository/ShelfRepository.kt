package com.komgareader.domain.repository

import com.komgareader.domain.model.Shelf
import kotlinx.coroutines.flow.Flow

interface ShelfRepository {
    val shelves: Flow<List<Shelf>>
    suspend fun add(shelf: Shelf): Long
    suspend fun update(shelf: Shelf)
    suspend fun delete(id: Long)
}
