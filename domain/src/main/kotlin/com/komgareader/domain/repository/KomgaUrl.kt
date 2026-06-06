package com.komgareader.domain.repository

/**
 * Normalisiert Komga-Server-URLs, die Nutzer eingeben.
 * Stellt sicher, dass immer ein vollständiger API-Basispfad (`/api/v1/`) vorhanden ist.
 */
object KomgaUrl {

    /** Normalisiert eine vom Nutzer eingegebene Server-URL auf `<base>/api/v1/`. */
    fun normalize(input: String): String {
        var url = input.trim()
        if (!url.contains("://")) url = "https://$url"
        url = url.trimEnd('/')
        val apiVersionPattern = Regex("/api/v\\d+$")
        if (!apiVersionPattern.containsMatchIn(url)) url = "$url/api/v1"
        return "$url/"
    }
}
