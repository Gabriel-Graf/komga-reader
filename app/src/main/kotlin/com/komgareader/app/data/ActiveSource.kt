package com.komgareader.app.data

import com.komgareader.domain.repository.ServerRepository
import com.komgareader.domain.source.BrowsableSource
import com.komgareader.domain.source.SourceManager
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/** Die einzige Art, wie ein ViewModel eine Quelle bekommt: agnostisch, als [BrowsableSource].
 *  Kein ViewModel kennt KomgaSourceProvider — siehe Regel source-agnostic-integration.
 *
 *  `open`, damit Konsumenten (z. B. [com.komgareader.app.ui.reader.EpubBytesLoader]) im
 *  Unit-Test eine feste [BrowsableSource] liefern können, ohne eine echte (netz-gebundene)
 *  Komga-Quelle aufzubauen. */
@Singleton
open class ActiveSource @Inject constructor(
    private val sources: SourceManager,
    private val servers: ServerRepository,
    private val registration: SourceRegistration,
) {
    /** Stellt sicher, dass die aktuelle Config registriert ist, und liefert die aktive Quelle (oder null). */
    open suspend fun current(): BrowsableSource? {
        val config = servers.config.first()
        val id = registration.activate(config) ?: return null
        return sources.get(id) as? BrowsableSource
    }
}
