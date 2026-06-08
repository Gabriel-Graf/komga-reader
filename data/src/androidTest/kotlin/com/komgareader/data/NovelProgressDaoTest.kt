package com.komgareader.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.komgareader.data.db.AppDatabase
import com.komgareader.data.db.NovelProgressEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * DAO-Vertrag für den Roman-Fortschritt: upsert→get gibt Anker + Anteil zurück,
 * der zusammengesetzte PK (sourceId+bookId) ersetzt beim erneuten Schreiben, und
 * [dirtyEntries] liefert nur den Sync-Rückstand. In-Memory-Room (instrumentiert).
 */
@RunWith(AndroidJUnit4::class)
class NovelProgressDaoTest {

    private lateinit var db: AppDatabase

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun teardown() = db.close()

    @Test
    fun upsert_dann_get_liefertAnkerUndAnteil() = runBlocking {
        val dao = db.novelProgressDao()
        dao.upsert(
            NovelProgressEntity(
                sourceId = 7,
                bookId = "book-1",
                anchor = "/body/DocFragment[3].0",
                fraction = 0.42f,
                dirty = true,
                updatedAt = 1000,
            ),
        )

        val loaded = db.novelProgressDao().get(sourceId = 7, bookId = "book-1")!!
        assertEquals("/body/DocFragment[3].0", loaded.anchor)
        assertEquals(0.42f, loaded.fraction, 0.0001f)
        assertTrue(loaded.dirty)
    }

    @Test
    fun get_mitAndererQuelle_liefertNull() = runBlocking {
        val dao = db.novelProgressDao()
        dao.upsert(NovelProgressEntity(1, "book-1", "/a", 0.1f, false, 1))
        assertNull(dao.get(sourceId = 2, bookId = "book-1"))
        assertNull(dao.get(sourceId = 1, bookId = "book-x"))
    }

    @Test
    fun upsert_aufGleichenSchlüssel_ersetzt() = runBlocking {
        val dao = db.novelProgressDao()
        dao.upsert(NovelProgressEntity(1, "book-1", "/a", 0.10f, true, 1))
        dao.upsert(NovelProgressEntity(1, "book-1", "/b", 0.80f, true, 2))

        val loaded = dao.get(1, "book-1")!!
        assertEquals("/b", loaded.anchor)
        assertEquals(0.80f, loaded.fraction, 0.0001f)
    }

    @Test
    fun dirtyEntries_liefertNurDirty() = runBlocking {
        val dao = db.novelProgressDao()
        dao.upsert(NovelProgressEntity(1, "dirty-book", "/a", 0.2f, true, 1))
        dao.upsert(NovelProgressEntity(1, "clean-book", "/b", 0.3f, false, 1))

        val dirty = dao.dirtyEntries()
        assertEquals(1, dirty.size)
        assertEquals("dirty-book", dirty.first().bookId)
    }

    @Test
    fun markClean_entferntAusDirtyEntries() = runBlocking {
        val dao = db.novelProgressDao()
        dao.upsert(NovelProgressEntity(1, "book-1", "/a", 0.2f, true, 1))
        dao.markClean(sourceId = 1, bookId = "book-1")

        assertTrue(dao.dirtyEntries().isEmpty())
        assertEquals(false, dao.get(1, "book-1")!!.dirty)
    }
}
