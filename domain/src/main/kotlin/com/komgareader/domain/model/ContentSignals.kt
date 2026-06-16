package com.komgareader.domain.model

/** One sampled interior page: pixel dimensions + grayscale fraction (0f..1f). */
data class PageSample(val widthPx: Int, val heightPx: Int, val grayFraction: Float)

/**
 * Pixel-derived signals for content-type detection. Source-agnostic: any source's
 * sampled interior pages feed this; the decision is pure (see
 * [com.komgareader.domain.usecase.SuggestContentType]).
 */
data class ContentSignals(val samples: List<PageSample>)
