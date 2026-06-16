package com.komgareader.domain.usecase

import com.komgareader.domain.model.ContentSignals
import com.komgareader.domain.model.ContentType
import com.komgareader.domain.model.PageSample
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SuggestContentTypeTest {
    private val suggest = SuggestContentType()
    private fun signals(vararg s: PageSample) = ContentSignals(s.toList())
    private fun page(w: Int, h: Int, gray: Float) = PageSample(w, h, gray)

    @Test fun `empty samples yield null`() {
        assertNull(suggest(ContentSignals(emptyList())))
    }

    @Test fun `tall strips yield WEBTOON`() {
        val r = suggest(signals(page(800, 4000, 0.9f), page(800, 5000, 0.2f)))
        assertEquals(ContentType.WEBTOON, r)
    }

    @Test fun `grayscale normal pages yield MANGA`() {
        val r = suggest(signals(page(1000, 1500, 0.98f), page(1000, 1500, 0.95f), page(1000, 1500, 0.99f)))
        assertEquals(ContentType.MANGA, r)
    }

    @Test fun `colorful normal pages yield COMIC`() {
        val r = suggest(signals(page(1000, 1500, 0.2f), page(1000, 1500, 0.1f), page(1000, 1500, 0.3f)))
        assertEquals(ContentType.COMIC, r)
    }

    @Test fun `ambiguous mid-saturation yields null`() {
        val r = suggest(signals(page(1000, 1500, 0.75f), page(1000, 1500, 0.8f)))
        assertNull(r)
    }

    @Test fun `aspect ratio uses median so one tall outlier does not flip`() {
        val r = suggest(signals(page(1000, 1500, 0.97f), page(1000, 1500, 0.96f), page(800, 6000, 0.9f)))
        assertEquals(ContentType.MANGA, r)
    }
}
