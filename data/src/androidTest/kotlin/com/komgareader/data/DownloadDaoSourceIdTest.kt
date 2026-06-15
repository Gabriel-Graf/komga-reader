package com.komgareader.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.komgareader.data.db.AppDatabase
import com.komgareader.data.db.DownloadDao
import com.komgareader.data.db.DownloadEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * DAO contract for source-scoped download deletion: [DownloadDao.deleteBySourceId] removes only
 * the rows of the given source and leaves all other sources intact. In-memory Room (instrumented).
 */
@RunWith(AndroidJUnit4::class)
class DownloadDaoSourceIdTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: DownloadDao

    private fun entity(id: String, source: Long) = DownloadEntity(
        bookRemoteId = id, sourceId = source, seriesRemoteId = "s", title = id,
        format = "cbz", localPath = "content://x/$id", totalPages = 0,
        seriesTitle = "", seriesCoverUrl = null,
    )

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), AppDatabase::class.java,
        ).build()
        dao = db.downloadDao()
    }

    @After
    fun teardown() = db.close()

    @Test
    fun deleteBySourceId_removes_only_that_source() = runBlocking {
        dao.put(entity("a", 1L))
        dao.put(entity("b", 1L))
        dao.put(entity("c", 0L))
        dao.deleteBySourceId(1L)
        assertEquals(listOf("c"), dao.observeAll().first().map { it.bookRemoteId })
    }
}
