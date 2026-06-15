package com.komgareader.data.repository

import com.komgareader.data.db.NovelProgressDao
import com.komgareader.data.db.ReadProgressDao
import com.komgareader.data.db.ReadingSessionDao
import com.komgareader.data.db.ReadingSessionEntity
import com.komgareader.domain.model.ReaderKind
import com.komgareader.domain.model.ReadingSession
import com.komgareader.domain.model.ReadingStats
import com.komgareader.domain.repository.ReadingStatsRepository
import com.komgareader.domain.usecase.ReadingStatsAggregator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Room-backed [ReadingStatsRepository]. Time aggregates come from the append-only
 * `reading_session` log; work counts are derived live from the paged/novel progress tables.
 * Pure aggregation is delegated to [ReadingStatsAggregator] (functional core, imperative shell).
 */
class RoomReadingStatsRepository(
    private val sessions: ReadingSessionDao,
    private val readProgress: ReadProgressDao,
    private val novelProgress: NovelProgressDao,
) : ReadingStatsRepository {

    override suspend fun record(session: ReadingSession) {
        sessions.insert(
            ReadingSessionEntity(
                readerKind = session.readerKind.name,
                bookRemoteId = session.bookRemoteId,
                sourceId = session.sourceId,
                startTs = session.startTs,
                durationMs = session.durationMs,
            ),
        )
    }

    override fun observeStats(): Flow<ReadingStats> =
        combine(
            sessions.observeAll(),
            readProgress.observeAll(),
            novelProgress.observeAll(),
        ) { sessionRows, pagedRows, novelRows ->
            ReadingStatsAggregator.aggregate(
                sessions = sessionRows.map { row ->
                    ReadingSession(
                        readerKind = runCatching { ReaderKind.valueOf(row.readerKind) }
                            .getOrDefault(ReaderKind.PAGED),
                        bookRemoteId = row.bookRemoteId,
                        sourceId = row.sourceId,
                        startTs = row.startTs,
                        durationMs = row.durationMs,
                    )
                },
                pagedCompleted = pagedRows.map { it.completed },
                novelFractions = novelRows.map { it.fraction },
            )
        }
}
