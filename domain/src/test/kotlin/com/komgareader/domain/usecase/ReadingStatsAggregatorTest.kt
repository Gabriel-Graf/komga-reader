package com.komgareader.domain.usecase

import com.komgareader.domain.model.ReaderKind
import com.komgareader.domain.model.ReadingSession
import kotlin.test.Test
import kotlin.test.assertEquals

class ReadingStatsAggregatorTest {
    private fun session(kind: ReaderKind, ms: Long) =
        ReadingSession(kind, "b", 1L, startTs = 0L, durationMs = ms)

    @Test fun `total and per-kind sums over sessions`() {
        val sessions = listOf(
            session(ReaderKind.PAGED, 1000),
            session(ReaderKind.PAGED, 500),
            session(ReaderKind.NOVEL, 2000),
        )
        val stats = ReadingStatsAggregator.aggregate(
            sessions, pagedCompleted = emptyList(), novelFractions = emptyList(),
        )
        assertEquals(3500, stats.totalMs)
        assertEquals(1500, stats.perKindMs[ReaderKind.PAGED])
        assertEquals(2000, stats.perKindMs[ReaderKind.NOVEL])
        assertEquals(0, stats.perKindMs[ReaderKind.WEBTOON])
        assertEquals(0, stats.perKindMs[ReaderKind.COMIC])
    }

    @Test fun `empty input is all-zero with every kind present`() {
        val stats = ReadingStatsAggregator.aggregate(emptyList(), emptyList(), emptyList())
        assertEquals(0, stats.totalMs)
        assertEquals(0, stats.startedWorks)
        assertEquals(0, stats.finishedWorks)
        ReaderKind.entries.forEach { assertEquals(0, stats.perKindMs[it]) }
    }

    @Test fun `started counts every progressed work, finished counts completed and novels at threshold`() {
        val stats = ReadingStatsAggregator.aggregate(
            sessions = emptyList(),
            pagedCompleted = listOf(true, false, false),
            novelFractions = listOf(1.0f, 0.99f, 0.5f),
        )
        assertEquals(6, stats.startedWorks)
        assertEquals(3, stats.finishedWorks)
    }
}
