package com.komgareader.app.ui.reader

/**
 * Pure helper: the forward page indices to warm into the image cache from [current]
 * (exclusive), bounded to `0 until [count]`. Forward-biased — readers move forward, so
 * preloading the next few pages hides the per-page network round-trip of streamed sources
 * (e.g. OPDS-PSE), making page turns feel instant instead of stuttering on each fetch.
 *
 * Kept pure (no Coil/Compose) so the "which pages" decision is unit-testable; the screen
 * only does the dumb Coil enqueue for the returned indices.
 */
fun prefetchIndices(current: Int, count: Int, ahead: Int = 3): List<Int> =
    (1..ahead).map { current + it }.filter { it in 0 until count }
