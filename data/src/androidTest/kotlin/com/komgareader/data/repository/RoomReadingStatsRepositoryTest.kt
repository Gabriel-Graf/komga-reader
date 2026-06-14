package com.komgareader.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.komgareader.data.db.AppDatabase
import com.komgareader.data.db.NovelProgressEntity
import com.komgareader.data.db.ReadProgressEntity
import com.komgareader.domain.model.ReaderKind
import com.komgareader.domain.model.ReadingSession
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * On-device pipeline test for [RoomReadingStatsRepository].
 *
 * Verifies the full record → observe → aggregate path against a real in-memory Room DB:
 * two sessions (PAGED + NOVEL) + one completed read_progress + one finished novel_progress
 * must yield the expected [com.komgareader.domain.model.ReadingStats].
 */
@RunWith(AndroidJUnit4::class)
class RoomReadingStatsRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: RoomReadingStatsRepository

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).build()
        repository = RoomReadingStatsRepository(
            sessions = db.readingSessionDao(),
            readProgress = db.readProgressDao(),
            novelProgress = db.novelProgressDao(),
        )
    }

    @After
    fun teardown() = db.close()

    @Test
    fun record_thenObserveStats_returnsExpectedAggregates() = runTest {
        // Record two reading sessions: 5 s paged + 7 s novel → total 12 s.
        repository.record(
            ReadingSession(
                readerKind = ReaderKind.PAGED,
                bookRemoteId = "b1",
                sourceId = 1L,
                startTs = 0L,
                durationMs = 5_000L,
            ),
        )
        repository.record(
            ReadingSession(
                readerKind = ReaderKind.NOVEL,
                bookRemoteId = "n1",
                sourceId = 1L,
                startTs = 0L,
                durationMs = 7_000L,
            ),
        )

        // Insert one completed paged-progress row and one finished novel-progress row.
        db.readProgressDao().put(
            ReadProgressEntity(
                bookRemoteId = "b1",
                sourceId = 1L,
                page = 10,
                completed = true,
                totalPages = 10,
                dirty = false,
                updatedAt = 1_000L,
            ),
        )
        db.novelProgressDao().upsert(
            NovelProgressEntity(
                sourceId = 1L,
                bookId = "n1",
                anchor = "/body/DocFragment[1].0",
                fraction = 1.0f, // >= 0.99 → finished
                dirty = false,
                updatedAt = 2_000L,
            ),
        )

        val stats = repository.observeStats().first()

        assertEquals("totalMs", 12_000L, stats.totalMs)
        assertEquals("perKindMs[PAGED]", 5_000L, stats.perKindMs[ReaderKind.PAGED])
        assertEquals("perKindMs[NOVEL]", 7_000L, stats.perKindMs[ReaderKind.NOVEL])
        // startedWorks = 1 read_progress row + 1 novel_progress row
        assertEquals("startedWorks", 2, stats.startedWorks)
        // finishedWorks = 1 completed paged + 1 novel at threshold
        assertEquals("finishedWorks", 2, stats.finishedWorks)
    }
}
