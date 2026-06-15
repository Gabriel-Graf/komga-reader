package com.komgareader.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.komgareader.data.db.AppDatabase
import com.komgareader.data.db.NovelBookmarkDao
import com.komgareader.data.db.NovelBookmarkEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * DAO contract for local-only novel bookmarks: insert→observe streams the bookmarks of a
 * book ordered by [NovelBookmarkEntity.number], observe is scoped to (sourceId, bookId),
 * and delete/rename mutate a single row by id. In-memory Room (instrumented).
 */
@RunWith(AndroidJUnit4::class)
class NovelBookmarkDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: NovelBookmarkDao

    private fun entity(number: Int, xp: String, label: String? = null) = NovelBookmarkEntity(
        id = 0, sourceId = 1, bookId = "b1", xpointer = xp, number = number,
        label = label, snippet = "ctx $number", createdAt = number.toLong(),
    )

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), AppDatabase::class.java,
        ).build()
        dao = db.novelBookmarkDao()
    }

    @After
    fun teardown() = db.close()

    @Test
    fun insert_and_observe_orders_by_number() = runBlocking {
        dao.insert(entity(2, "/b"))
        dao.insert(entity(1, "/a"))
        assertEquals(listOf(1, 2), dao.observe(1, "b1").first().map { it.number })
    }

    @Test
    fun observe_scopes_to_source_and_book() = runBlocking {
        dao.insert(entity(1, "/a"))
        assertEquals(0, dao.observe(2, "b1").first().size)
        assertEquals(0, dao.observe(1, "other").first().size)
    }

    @Test
    fun delete_and_rename() = runBlocking {
        val id = dao.insert(entity(1, "/a"))
        dao.rename(id, "Intro")
        assertEquals("Intro", dao.observe(1, "b1").first().single().label)
        dao.delete(id)
        assertEquals(0, dao.observe(1, "b1").first().size)
    }
}
