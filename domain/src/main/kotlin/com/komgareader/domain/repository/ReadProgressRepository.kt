package com.komgareader.domain.repository

import kotlinx.coroutines.flow.Flow

/** Lokaler Lesefortschritt eines Buchs (offline-first). */
data class LocalReadProgress(
    val bookRemoteId: String,
    val sourceId: Long,
    val page: Int,
    val completed: Boolean,
    val totalPages: Int,
    val dirty: Boolean,
    val updatedAt: Long,
)

/**
 * Persistiert Lesefortschritt **lokal zuerst** und markiert ihn als `dirty`, bis er zum
 * Server gepusht wurde. Die UI merged den lokalen Stand über den Server-Stand (höhere
 * Seite gewinnt). So zeigt ein Kapitel sein Lesezeichen auch offline sofort.
 */
interface ReadProgressRepository {
    /** Lokale Fortschritte je bookRemoteId — reaktiv für den Merge in der Detail-Ansicht. */
    val all: Flow<Map<String, LocalReadProgress>>

    /**
     * Hält fest, dass ein Kapitel (an)gelesen wird — setzt mindestens [page] (kein Regress),
     * `dirty=true`. Erzeugt damit das Lesezeichen lokal, bevor der Server es kennt.
     */
    suspend fun markProgress(
        sourceId: Long,
        bookRemoteId: String,
        page: Int,
        completed: Boolean,
        totalPages: Int,
    )

    /** Noch nicht synchronisierte Einträge (für den späteren Server-Push). */
    suspend fun dirty(): List<LocalReadProgress>

    /** Markiert einen Eintrag als synchronisiert. */
    suspend fun markSynced(bookRemoteId: String)
}
