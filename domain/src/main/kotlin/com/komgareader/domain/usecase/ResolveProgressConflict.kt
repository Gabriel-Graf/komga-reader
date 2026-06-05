package com.komgareader.domain.usecase

import com.komgareader.domain.model.ReadProgress

/**
 * Offline-first-Konfliktregel beim Sync: der jüngste Stand (höchstes updatedAt)
 * gewinnt. Bei Gleichstand gewinnt der lokale Stand. Fehlt der Remote-Stand,
 * bleibt der lokale erhalten.
 */
class ResolveProgressConflict {

    operator fun invoke(local: ReadProgress, remote: ReadProgress?): ReadProgress {
        if (remote == null) return local
        return if (remote.updatedAt > local.updatedAt) remote else local
    }
}
