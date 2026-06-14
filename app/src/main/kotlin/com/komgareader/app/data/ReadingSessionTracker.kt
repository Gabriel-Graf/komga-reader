package com.komgareader.app.data

import com.komgareader.app.di.ApplicationScope
import com.komgareader.domain.model.ReaderKind
import com.komgareader.domain.model.ReadingSession
import com.komgareader.domain.repository.ReadingStatsRepository
import com.komgareader.domain.usecase.ReadingTimeCaps
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Event-driven, capped-delta reading-time measurement — the single place this logic lives.
 * **No ticking timer** (E-Ink battery): time is only sampled at events that already happen
 * (enter / page-settle / leave). A page delta above the per-kind cap is clipped (idle guard).
 * Flushing uses the application scope, so a session survives reader/VM teardown (offline-first).
 */
@Singleton
class ReadingSessionTracker @Inject constructor(
    private val repo: ReadingStatsRepository,
    @ApplicationScope private val appScope: CoroutineScope,
) {
    /** Overridable for tests. */
    internal var clock: () -> Long = { System.currentTimeMillis() }

    private var kind: ReaderKind? = null
    private var bookRemoteId: String = ""
    private var sourceId: Long = 0L
    private var startTs: Long = 0L
    private var lastTs: Long = 0L
    private var accMs: Long = 0L

    @Synchronized
    fun enter(kind: ReaderKind, bookRemoteId: String, sourceId: Long) {
        flushInternal()
        this.kind = kind
        this.bookRemoteId = bookRemoteId
        this.sourceId = sourceId
        val now = clock()
        startTs = now
        lastTs = now
        accMs = 0L
    }

    @Synchronized
    fun page() {
        val k = kind ?: return
        val now = clock()
        accMs += ReadingTimeCaps.capDeltaMs(k, now - lastTs)
        lastTs = now
    }

    @Synchronized
    fun leave() = flushInternal()

    private fun flushInternal() {
        val k = kind ?: return
        val duration = accMs
        val session = ReadingSession(k, bookRemoteId, sourceId, startTs, duration)
        kind = null
        accMs = 0L
        if (duration > 0L) {
            appScope.launch { runCatching { repo.record(session) } }
        }
    }
}
