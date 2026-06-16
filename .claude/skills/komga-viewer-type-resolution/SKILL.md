---
name: komga-viewer-type-resolution
description: Use when touching Viewer-/Reader-Mode selection in the Komga-Reader (ResolveViewerType, readingDirection mapping, Shelf defaultContentType fallback, auto content-type suggestion, per-book viewer in SeriesDetailViewModel). Hält die verbindliche 7-stufige Prioritätsregel fest, damit sie nicht versehentlich gebrochen wird.
---

# Viewer-Typ-Auflösung (Domain-Regel)

`ResolveViewerType(series, book, fallback, auto = null)` — Reihenfolge NICHT ändern:

1. `series.contentTypeOverride` → map
2. `book.format == EPUB` → NOVEL
3. `readingDirection ∈ {VERTICAL, WEBTOON}` → WEBTOON
4. `fallback (shelf.defaultContentType)` → map
5. `auto (persistierter Pixel-Vorschlag)` → map
6. `book.format ∈ {CBZ, CBR, PDF}` → PAGED
7. sonst → PAGED

**Stufe 4 (Bibliotheks-Default) steht VOR dem Format-Default:** Webtoons
liegen fast immer als CBZ vor; ein explizites WEBTOON-Bibliothek-Tag muss den
Format-Default (PAGED) schlagen, sonst bliebe der Bibliotheks-Default für Comics
wirkungslos (CBZ erzwänge sonst immer PAGED).

**Stufe 5 (Auto-Vorschlag)** ist eine persistierte Pixel-Heuristik (`series_auto_types`,
`SeriesAutoTypeRepository`): Innenseiten grau → MANGA, farbig → COMIC, lange Strips →
WEBTOON (`SuggestContentType`/`measureGrayFraction`, rein/getestet; Sampler =
`ContentTypeDetector` in `app`). Sie füllt **nur** die Lücke, wenn weder manueller
Override (1) noch Server-Leserichtung (3) noch Bibliotheks-Default (4) greifen — alle
drei schlagen sie bewusst. **Grau ⇒ S/W-vs-Farbe, nie Leserichtung** (RTL bleibt
metadaten-getrieben).

`map`: MANGA/COMIC → PAGED, WEBTOON → WEBTOON, NOVEL → NOVEL.

Komga `readingDirection`: `LEFT_TO_RIGHT → LTR`, `RIGHT_TO_LEFT → RTL`,
`VERTICAL → VERTICAL`, `WEBTOON → WEBTOON`, sonst `null`. EPUB/Novel =
`BookFormat.EPUB` (mediaProfile), **nicht** Leserichtung.

App: `ViewerMode` nur `PAGED`/`WEBTOON`; `ViewerType.NOVEL`/`EPUB`/`PAGED` → `ViewerMode.PAGED`,
`ViewerType.WEBTOON` → `ViewerMode.WEBTOON`. Reflowbares Buch wählt Reader per Format.

`ViewerType.EPUB` ist Legacy (alter EPUB-Viewer wird ausgemustert) und wird in
Phase 4 mit der NovelReader-Migration entfernt; reflowbare Bücher lösen jetzt
nach `ViewerType.NOVEL` auf. Die NovelReader-Verdrahtung folgt in Phase 4 — bis
dahin fällt `NOVEL` über den `else`-Zweig auf `ViewerMode.PAGED` zurück.

Auflösung passiert pro Buch in `SeriesDetailViewModel` (Fallback aus
`Shelf.defaultContentType`, per `shelfId` nachgeschlagen).

Volltext + Begründung: `docs/domain/viewer-type-resolution.md`.
Tests: `domain/.../usecase/ResolveViewerTypeTest.kt` (ein Test pro Stufe),
`SuggestContentTypeTest`, `MeasureGrayFractionTest`.
