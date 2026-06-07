package com.komgareader.domain.repository

import com.komgareader.domain.model.ContentType

/**
 * Persistiert manuelle Inhaltstyp-Zuweisungen pro Werk (Serie), quellen-übergreifend
 * via [sourceId] + [seriesRemoteId]. Der manuelle Typ wirkt nur, wenn keine Bibliothek
 * einen Typ vorgibt — der Bibliotheks-Default hat Vorrang (siehe ResolveViewerType-Fallback).
 */
interface SeriesOverrideRepository {
    /** Manuell gesetzter Typ dieses Werks, oder `null` wenn keiner gesetzt ist. */
    suspend fun get(sourceId: Long, seriesRemoteId: String): ContentType?

    /** Alle manuellen Typen einer Quelle als `seriesRemoteId → Typ` (für Listen-/Filter-Auflösung). */
    suspend fun all(sourceId: Long): Map<String, ContentType>

    /** Setzt ([type] != null) oder löscht ([type] == null) den manuellen Typ. */
    suspend fun set(sourceId: Long, seriesRemoteId: String, type: ContentType?)
}
