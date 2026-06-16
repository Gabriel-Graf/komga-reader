package com.komgareader.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.komgareader.data.db.AppDatabase
import com.komgareader.data.db.SeriesAutoTypeDao
import com.komgareader.data.db.SeriesAutoTypeEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SeriesAutoTypeDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: SeriesAutoTypeDao

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), AppDatabase::class.java,
        ).build()
        dao = db.seriesAutoTypeDao()
    }

    @After fun tearDown() = db.close()

    @Test fun put_then_get_roundtrips() = runBlocking {
        dao.put(SeriesAutoTypeEntity(1L, "S1", "MANGA", 1))
        val e = dao.get(1L, "S1")
        assertEquals("MANGA", e?.contentType)
        assertEquals(1, e?.detectorVersion)
    }

    @Test fun delete_removes_row() = runBlocking {
        dao.put(SeriesAutoTypeEntity(1L, "S1", "COMIC", 1))
        dao.delete(1L, "S1")
        assertNull(dao.get(1L, "S1"))
    }
}
