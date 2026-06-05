package com.komgareader.domain.source

import com.komgareader.domain.model.SourceKind
import java.security.MessageDigest

/**
 * Erzeugt global eindeutige, deterministische 64-bit-Quellen-IDs ohne zentrale
 * Registry — analog zu Mihons Source-ID. So koexistieren mehrere Server/Quellen
 * stabil. ID 0 ist für die lokale Quelle reserviert.
 */
object SourceId {

    const val LOCAL: Long = 0L

    fun of(name: String, kind: SourceKind, config: String): Long {
        val key = "${name.lowercase()}/${kind.name}/$config"
        val digest = MessageDigest.getInstance("SHA-256").digest(key.toByteArray())
        var id = 0L
        for (i in 0 until 8) {
            id = (id shl 8) or (digest[i].toLong() and 0xff)
        }
        return id and Long.MAX_VALUE // Sign-Bit löschen → immer >= 0
    }
}
