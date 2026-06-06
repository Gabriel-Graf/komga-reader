package com.komgareader.domain.model

/**
 * Lese-Richtung einer Serie, quellen-agnostisch (Naht A). `VERTICAL`/`WEBTOON`
 * bedeuten vertikalen Strip; `LTR`/`RTL` paginierte Comics/Manga. `null` =
 * unbekannt → der Viewer fällt auf Format bzw. Bibliotheks-Default zurück.
 */
enum class ReadingDirection { LTR, RTL, VERTICAL, WEBTOON }
