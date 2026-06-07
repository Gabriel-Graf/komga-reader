---
name: komga-viewer-type-resolution
description: Use when touching Viewer-/Reader-Mode selection in the Komga-Reader (ResolveViewerType, readingDirection mapping, Shelf defaultContentType fallback, per-book viewer in SeriesDetailViewModel). Hält die verbindliche 6-stufige Prioritätsregel fest, damit sie nicht versehentlich gebrochen wird.
---

# Viewer-Typ-Auflösung (Domain-Regel)

`ResolveViewerType(series, book, fallback)` — Reihenfolge NICHT ändern:

1. `series.contentTypeOverride` → map
2. `book.format == EPUB` → EPUB
3. `readingDirection ∈ {VERTICAL, WEBTOON}` → WEBTOON
4. `fallback (shelf.defaultContentType)` → map
5. `book.format ∈ {CBZ, CBR, PDF}` → PAGED
6. sonst → PAGED

**Stufe 4 (Bibliotheks-Default) steht VOR Stufe 5 (Format-Default):** Webtoons
liegen fast immer als CBZ vor; ein explizites WEBTOON-Bibliothek-Tag muss den
Format-Default (PAGED) schlagen, sonst bliebe der Bibliotheks-Default für Comics
wirkungslos (CBZ erzwänge sonst immer PAGED).

`map`: MANGA/COMIC → PAGED, WEBTOON → WEBTOON, NOVEL → EPUB.

Komga `readingDirection`: `LEFT_TO_RIGHT → LTR`, `RIGHT_TO_LEFT → RTL`,
`VERTICAL → VERTICAL`, `WEBTOON → WEBTOON`, sonst `null`. EPUB/Novel =
`BookFormat.EPUB` (mediaProfile), **nicht** Leserichtung.

App: `ViewerMode` nur `PAGED`/`WEBTOON`; `ViewerType.EPUB`/`PAGED` → `ViewerMode.PAGED`,
`ViewerType.WEBTOON` → `ViewerMode.WEBTOON`. EPUB-Buch wählt Reader per Format.

Auflösung passiert pro Buch in `SeriesDetailViewModel` (Fallback aus
`Shelf.defaultContentType`, per `shelfId` nachgeschlagen).

Volltext + Begründung: `docs/domain/viewer-type-resolution.md`.
Tests: `domain/.../usecase/ResolveViewerTypeTest.kt` (ein Test pro Stufe).
