package com.komgareader.domain.repository

/**
 * Lokaler Roman-Lesefortschritt (ViewerType.NOVEL): der crengine-Xpointer ([anchor]) ist die
 * **exakte**, Schrift-/Viewport-unabhängige Position für das Wiederaufnehmen auf demselben
 * Gerät; [fraction] (0.0..1.0) ist der grobe Anteil für den geräteübergreifenden %-Sync.
 */
data class NovelProgress(
    val sourceId: Long,
    val bookId: String,
    val anchor: String,
    val fraction: Float,
    val dirty: Boolean,
    val updatedAt: Long,
)

/**
 * Persistiert den Roman-Fortschritt **lokal zuerst** (`dirty`), bis der grobe Anteil zum
 * Server gepusht wurde. Der lokale Xpointer ist die einzige Quelle der Wahrheit für das
 * exakte Wiederaufnehmen — der Server kennt nur den groben Prozentwert.
 */
interface NovelProgressRepository {

    /** Schreibt Anker + Anteil lokal mit `dirty = true` (Sync-Rückstand für den %-Push). */
    suspend fun save(sourceId: Long, bookId: String, anchor: String, fraction: Float)

    /** Lokaler Stand für ein Buch, oder `null` wenn es lokal noch keinen gibt. */
    suspend fun get(sourceId: Long, bookId: String): NovelProgress?

    /** Noch nicht zum Server gepushte Einträge. */
    suspend fun dirty(): List<NovelProgress>

    /** Markiert einen Eintrag als synchronisiert (nach erfolgreichem %-Push). */
    suspend fun markSynced(sourceId: Long, bookId: String)
}
