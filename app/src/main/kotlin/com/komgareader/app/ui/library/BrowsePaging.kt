package com.komgareader.app.ui.library

import com.komgareader.domain.model.Series
import com.komgareader.domain.source.BrowsableSource
import com.komgareader.domain.source.SourceFilter

/**
 * Eagerly fetch *every* page of a source's series list, following [PagedResult.hasNextPage]
 * until exhausted. Bounded by [maxPages] as a runaway guard (a misbehaving server that always
 * reports a next page must not loop forever).
 *
 * Sequential by contract: cursor-paginated sources (OPDS follows `<link rel="next">`) expose
 * page `n+1` only after page `n` has been fetched, so pages are walked in order, not concurrently.
 *
 * Replaces the old "page 0 only" call site, which silently dropped every series beyond the first
 * page for any paginated source (Komga, OPDS, …).
 */
suspend fun browseAllSeries(source: BrowsableSource, maxPages: Int = 50): List<Series> {
    val all = mutableListOf<Series>()
    var page = 0
    while (page < maxPages) {
        val result = source.browse(page, SourceFilter())
        all += result.items
        if (!result.hasNextPage) break
        page++
    }
    return all
}
