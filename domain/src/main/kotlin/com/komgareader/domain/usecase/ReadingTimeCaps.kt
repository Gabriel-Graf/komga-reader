package com.komgareader.domain.usecase

import com.komgareader.domain.model.ReaderKind

/**
 * Per-reader-type caps for a single page's reading-time delta. The cap is an **idle guard**:
 * real active per-page reading is far below it (a comic page averages ~3.75 s), so it never
 * clips genuine reading — only a device left lying open between page turns.
 */
object ReadingTimeCaps {
    private val capMs: Map<ReaderKind, Long> = mapOf(
        ReaderKind.WEBTOON to 2L * 60_000,
        ReaderKind.PAGED to 5L * 60_000,
        ReaderKind.COMIC to 5L * 60_000,
        ReaderKind.NOVEL to 7L * 60_000,
    )

    fun capMsFor(kind: ReaderKind): Long = capMs.getValue(kind)

    /** Below cap → verbatim; above cap → clipped; negative/zero → 0. */
    fun capDeltaMs(kind: ReaderKind, rawDeltaMs: Long): Long =
        rawDeltaMs.coerceIn(0L, capMsFor(kind))
}
