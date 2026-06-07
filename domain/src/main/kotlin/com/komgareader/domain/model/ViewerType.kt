package com.komgareader.domain.model

/** Konkreter Lese-Modus, den der Reader lädt. */
enum class ViewerType {
    PAGED,
    WEBTOON,
    NOVEL,

    /** Legacy: wird in Phase 4 mit der NovelReader-Migration entfernt (ersetzt durch [NOVEL]). */
    EPUB,
    COMIC,
}
