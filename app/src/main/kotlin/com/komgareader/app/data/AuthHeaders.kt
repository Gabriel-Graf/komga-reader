package com.komgareader.app.data

import com.komgareader.domain.repository.ServerConfig

/**
 * Berechnet die HTTP-Auth-Header für Coil-Requests (Cover, Seitenbilder).
 * Bevorzugt API-Key; fällt auf HTTP-Basic zurück.
 */
object AuthHeaders {
    fun forCovers(config: ServerConfig?): Map<String, String> = when {
        config == null -> emptyMap()
        !config.apiKey.isNullOrBlank() -> mapOf("X-API-Key" to config.apiKey!!)
        !config.username.isNullOrBlank() && !config.password.isNullOrBlank() ->
            mapOf(
                "Authorization" to "Basic " + java.util.Base64.getEncoder()
                    .encodeToString("${config.username}:${config.password}".toByteArray()),
            )
        else -> emptyMap()
    }
}
