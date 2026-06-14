package com.komgareader.domain.model

/** The four reader types whose reading time is tracked separately. */
enum class ReaderKind { PAGED, WEBTOON, COMIC, NOVEL }

/** One reading session: time spent in a single reader on a single book. */
data class ReadingSession(
    val readerKind: ReaderKind,
    val bookRemoteId: String,
    val sourceId: Long,
    val startTs: Long,
    val durationMs: Long,
)

/** Aggregated, ready-to-display statistics. */
data class ReadingStats(
    val totalMs: Long = 0L,
    val perKindMs: Map<ReaderKind, Long> = emptyMap(),
    val startedWorks: Int = 0,
    val finishedWorks: Int = 0,
)
