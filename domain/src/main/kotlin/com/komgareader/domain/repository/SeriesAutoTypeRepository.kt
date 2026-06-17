package com.komgareader.domain.repository

import com.komgareader.domain.model.ContentType

/**
 * Persists the *auto-detected* content-type suggestion per series (source-agnostic via
 * [sourceId] + [seriesRemoteId]). Separate from the manual SeriesOverrideRepository: this
 * is a heuristic guess that the user / server metadata / library tag all outrank. The
 * stored [detectorVersion] lets re-detection be idempotent and re-runnable after an
 * algorithm bump without touching manual overrides.
 */
interface SeriesAutoTypeRepository {
    /** Detected type, or `null` if none stored or detection produced no verdict. */
    suspend fun get(sourceId: Long, seriesRemoteId: String): ContentType?

    /**
     * Detector version recorded for this series, or `null` if detection never ran. Present even
     * when [get] is `null` (ambiguous verdict), so the detector skips an already-sampled series.
     */
    suspend fun detectorVersion(sourceId: Long, seriesRemoteId: String): Int?

    /**
     * Records a detection result at [detectorVersion]. A non-null [type] is the verdict; a `null`
     * [type] records "ran, no verdict" so the series is not re-sampled on every open.
     */
    suspend fun set(sourceId: Long, seriesRemoteId: String, type: ContentType?, detectorVersion: Int)
}
