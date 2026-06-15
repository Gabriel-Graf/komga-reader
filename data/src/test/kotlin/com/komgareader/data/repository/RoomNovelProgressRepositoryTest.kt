package com.komgareader.data.repository

import com.komgareader.data.db.NovelProgressDao
import com.komgareader.data.db.NovelProgressEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** In-Memory-Fake des DAO — testet die Mapping-/dirty-/Clamp-Logik ohne echtes Room. */
private class FakeNovelProgressDao : NovelProgressDao {
    val rows = mutableListOf<NovelProgressEntity>()
    private fun key(e: NovelProgressEntity) = e.sourceId to e.bookId
    override fun observeAll(): Flow<List<NovelProgressEntity>> = flowOf(rows.toList())
    override suspend fun upsert(entry: NovelProgressEntity) {
        rows.removeAll { it.sourceId == entry.sourceId && it.bookId == entry.bookId }
        rows.add(entry)
    }
    override suspend fun get(sourceId: Long, bookId: String): NovelProgressEntity? =
        rows.firstOrNull { it.sourceId == sourceId && it.bookId == bookId }
    override suspend fun dirtyEntries(): List<NovelProgressEntity> = rows.filter { it.dirty }
    override suspend fun markClean(sourceId: Long, bookId: String) {
        rows.replaceAll { if (it.sourceId == sourceId && it.bookId == bookId) it.copy(dirty = false) else it }
    }
}

class RoomNovelProgressRepositoryTest {

    @Test
    fun `save schreibt Anker und Anteil als dirty`() = runTest {
        val dao = FakeNovelProgressDao()
        val repo = RoomNovelProgressRepository(dao)

        repo.save(sourceId = 7, bookId = "b1", anchor = "/body/DocFragment[2].0", fraction = 0.5f)

        val loaded = repo.get(7, "b1")!!
        assertEquals("/body/DocFragment[2].0", loaded.anchor)
        assertEquals(0.5f, loaded.fraction, 0.0001f)
        assertTrue(loaded.dirty)
    }

    @Test
    fun `save klemmt den Anteil in 0 bis 1`() = runTest {
        val repo = RoomNovelProgressRepository(FakeNovelProgressDao())
        repo.save(1, "low", "/a", -1f)
        repo.save(1, "high", "/b", 2f)
        assertEquals(0f, repo.get(1, "low")!!.fraction, 0.0001f)
        assertEquals(1f, repo.get(1, "high")!!.fraction, 0.0001f)
    }

    @Test
    fun `get einer fremden Quelle liefert null`() = runTest {
        val repo = RoomNovelProgressRepository(FakeNovelProgressDao())
        repo.save(1, "b1", "/a", 0.2f)
        assertNull(repo.get(2, "b1"))
    }

    @Test
    fun `markSynced raeumt den Eintrag aus dirty`() = runTest {
        val repo = RoomNovelProgressRepository(FakeNovelProgressDao())
        repo.save(1, "b1", "/a", 0.2f)
        repo.markSynced(1, "b1")
        assertTrue(repo.dirty().isEmpty())
    }
}
