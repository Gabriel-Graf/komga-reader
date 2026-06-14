package com.komgareader.app.data

import com.komgareader.domain.model.ReaderKind
import com.komgareader.domain.model.ReadingSession
import com.komgareader.domain.model.ReadingStats
import com.komgareader.domain.repository.ReadingStatsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

private class RecordingRepo : ReadingStatsRepository {
    val recorded = mutableListOf<ReadingSession>()
    override suspend fun record(session: ReadingSession) { recorded.add(session) }
    override fun observeStats(): Flow<ReadingStats> = flowOf(ReadingStats())
}

class ReadingSessionTrackerTest {
    @Test fun `sums capped per-page deltas and records on leave`() = runTest {
        val repo = RecordingRepo()
        val t = ReadingSessionTracker(repo, this)
        var now = 1_000L
        t.clock = { now }

        t.enter(ReaderKind.PAGED, "b1", 7L)   // lastTs = 1000
        now = 1_000L + 90_000                 // +90s (below 5min cap)
        t.page()                              // acc += 90s
        now += 12L * 60_000                   // +12min gap (above cap)
        t.page()                              // acc += 5min cap
        t.leave()
        advanceUntilIdle()

        assertEquals(1, repo.recorded.size)
        val s = repo.recorded.single()
        assertEquals(ReaderKind.PAGED, s.readerKind)
        assertEquals("b1", s.bookRemoteId)
        assertEquals(7L, s.sourceId)
        assertEquals(90_000L + 5L * 60_000, s.durationMs)
    }

    @Test fun `zero-duration session is not recorded`() = runTest {
        val repo = RecordingRepo()
        val t = ReadingSessionTracker(repo, this)
        t.clock = { 5_000L }
        t.enter(ReaderKind.NOVEL, "b2", 1L)
        t.leave()
        advanceUntilIdle()
        assertEquals(0, repo.recorded.size)
    }

    @Test fun `entering a new book flushes the previous session`() = runTest {
        val repo = RecordingRepo()
        val t = ReadingSessionTracker(repo, this)
        var now = 0L
        t.clock = { now }
        t.enter(ReaderKind.COMIC, "b1", 1L)
        now = 60_000
        t.page()                              // acc 60s
        t.enter(ReaderKind.COMIC, "b2", 1L)   // flush b1
        advanceUntilIdle()
        assertEquals(1, repo.recorded.size)
        assertEquals("b1", repo.recorded.single().bookRemoteId)
    }
}
