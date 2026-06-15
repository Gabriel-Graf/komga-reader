package com.komgareader.domain.usecase

import com.komgareader.domain.model.ReaderKind
import com.komgareader.domain.model.ReadingSession
import com.komgareader.domain.model.ReadingStats

/**
 * Pure aggregation of the session log + progress facts into [ReadingStats].
 * Time aggregates come from the sessions; work counts are derived from progress rows:
 * started = any progressed work; finished = completed paged works + novels at/above [NOVEL_DONE].
 */
object ReadingStatsAggregator {
    /** Novel completion threshold (no `completed` flag on `novel_progress`; use fraction). */
    const val NOVEL_DONE: Float = 0.99f

    fun aggregate(
        sessions: List<ReadingSession>,
        pagedCompleted: List<Boolean>,
        novelFractions: List<Float>,
    ): ReadingStats {
        val perKind = ReaderKind.entries.associateWith { k ->
            sessions.filter { it.readerKind == k }.sumOf { it.durationMs }
        }
        return ReadingStats(
            totalMs = sessions.sumOf { it.durationMs },
            perKindMs = perKind,
            startedWorks = pagedCompleted.size + novelFractions.size,
            finishedWorks = pagedCompleted.count { it } + novelFractions.count { it >= NOVEL_DONE },
        )
    }
}
