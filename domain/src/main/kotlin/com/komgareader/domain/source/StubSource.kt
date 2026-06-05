package com.komgareader.domain.source

import com.komgareader.domain.model.SourceKind

/**
 * Platzhalter-Quelle für Bibliotheks-Einträge, deren echte Quelle (noch) nicht
 * verfügbar ist (entfernter Server, deinstalliertes Plugin). Hält Titel/ID, damit
 * die Bibliothek nie bricht. Browsing/Sync schlagen bewusst fehl.
 */
data class StubSource(
    override val id: Long,
    override val name: String,
) : MediaSource {
    override val kind: SourceKind = SourceKind.UNKNOWN
}
