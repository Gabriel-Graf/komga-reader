package com.komgareader.plugin.host

import java.security.MessageDigest

/** Deterministischer, reihenfolge-unabhängiger Hash der Config-Werte (für SourceId-Namespace). */
object PluginConfigHash {
    fun of(config: Map<String, String>): String {
        val canonical = config.toSortedMap().entries.joinToString("&") { "${it.key}=${it.value}" }
        val digest = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }.take(16)
    }
}
