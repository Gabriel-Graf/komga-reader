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
    /** Detected type, or `null` if none stored. */
    suspend fun get(sourceId: Long, seriesRemoteId: String): ContentType?

    /** Detector version that produced the stored value, or `null` if none stored. */
    suspend fun detectorVersion(sourceId: Long, seriesRemoteId: String): Int?

    /** Stores ([type] != null) or clears ([type] == null) the detected type. */
    suspend fun set(sourceId: Long, seriesRemoteId: String, type: ContentType?, detectorVersion: Int)
}
