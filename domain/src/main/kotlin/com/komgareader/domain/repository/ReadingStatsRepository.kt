package com.komgareader.domain.repository

import com.komgareader.domain.model.ReadingSession
import com.komgareader.domain.model.ReadingStats
import kotlinx.coroutines.flow.Flow

/** Local-only reading statistics. No server sync (single user). */
interface ReadingStatsRepository {
    /** Append one finished reading session to the log. */
    suspend fun record(session: ReadingSession)

    /** Reactive aggregated statistics (time from the session log; work counts from progress). */
    fun observeStats(): Flow<ReadingStats>
}
