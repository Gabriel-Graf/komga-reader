package com.komgareader.domain.usecase

import com.komgareader.domain.model.ReaderKind
import kotlin.test.Test
import kotlin.test.assertEquals

class ReadingTimeCapsTest {
    @Test fun `delta below cap is kept verbatim`() {
        val raw = 3L * 60_000 + 18_000 // 3.3 min, below PAGED cap (5 min)
        assertEquals(raw, ReadingTimeCaps.capDeltaMs(ReaderKind.PAGED, raw))
    }

    @Test fun `delta above cap is clipped to the cap`() {
        val raw = 12L * 60_000 // 12 min gap
        assertEquals(5L * 60_000, ReadingTimeCaps.capDeltaMs(ReaderKind.PAGED, raw))
    }

    @Test fun `negative or zero delta becomes zero`() {
        assertEquals(0L, ReadingTimeCaps.capDeltaMs(ReaderKind.NOVEL, -500L))
        assertEquals(0L, ReadingTimeCaps.capDeltaMs(ReaderKind.NOVEL, 0L))
    }

    @Test fun `each kind has its own cap`() {
        assertEquals(2L * 60_000, ReadingTimeCaps.capMsFor(ReaderKind.WEBTOON))
        assertEquals(5L * 60_000, ReadingTimeCaps.capMsFor(ReaderKind.PAGED))
        assertEquals(5L * 60_000, ReadingTimeCaps.capMsFor(ReaderKind.COMIC))
        assertEquals(7L * 60_000, ReadingTimeCaps.capMsFor(ReaderKind.NOVEL))
    }
}
