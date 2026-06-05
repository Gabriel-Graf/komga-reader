package com.komgareader.domain.source

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Reaktive Registry aller aktiven Quellen (id → Source). Bibliothek, Reader und
 * Sync greifen ausschließlich hierüber zu und bleiben damit quellen-agnostisch.
 * [getOrStub] garantiert, dass ein fehlendes Backend die Bibliothek nicht bricht.
 *
 * [sources] wird ohne eigenen CoroutineScope direkt aus dem Backing-StateFlow
 * exponiert — kein Leak, keine Coroutine-Maschinerie im Domain-Modul. Konsumenten
 * (UI-Schicht) mappen die Map bei Bedarf mit ihrem eigenen Scope auf eine Liste.
 */
class SourceManager {

    private val registry = MutableStateFlow<Map<Long, MediaSource>>(emptyMap())

    /** Beobachtbare Map aller registrierten Quellen (id → Quelle). */
    val sources: StateFlow<Map<Long, MediaSource>> = registry.asStateFlow()

    fun register(source: MediaSource) {
        registry.update { it + (source.id to source) }
    }

    fun unregister(id: Long) {
        registry.update { it - id }
    }

    fun get(id: Long): MediaSource? = registry.value[id]

    fun getOrStub(id: Long, name: String): MediaSource =
        registry.value[id] ?: StubSource(id, name)
}
