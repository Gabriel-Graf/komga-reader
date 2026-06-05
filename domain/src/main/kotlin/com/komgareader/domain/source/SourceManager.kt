package com.komgareader.domain.source

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/**
 * Reaktive Registry aller aktiven Quellen (id → Source). Bibliothek, Reader und
 * Sync greifen ausschließlich hierüber zu und bleiben damit quellen-agnostisch.
 * [getOrStub] garantiert, dass ein fehlendes Backend die Bibliothek nicht bricht.
 */
class SourceManager {

    private val registry = MutableStateFlow<Map<Long, MediaSource>>(emptyMap())

    /** Beobachtbare Liste aller registrierten Quellen. */
    val sources: StateFlow<List<MediaSource>> =
        registry
            .map { it.values.toList() }
            .stateIn(
                scope = CoroutineScope(Dispatchers.Unconfined),
                started = SharingStarted.Eagerly,
                initialValue = emptyList(),
            )

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
