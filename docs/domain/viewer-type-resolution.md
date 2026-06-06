# Domain-Regel: Viewer-Typ-Auflösung

`ResolveViewerType(series, book, fallback)` bestimmt deterministisch den
Lese-Modus. Die Reihenfolge ist verbindlich — nicht umsortieren.

| Stufe | Bedingung | Ergebnis |
|-------|-----------|----------|
| 1 | `series.contentTypeOverride != null` | map(override) |
| 2 | `book.format == EPUB` | EPUB |
| 3 | `series.readingDirection ∈ {VERTICAL, WEBTOON}` | WEBTOON |
| 4 | `fallback != null` | map(fallback) |
| 5 | `book.format ∈ {CBZ, CBR, PDF}` | PAGED |
| 6 | sonst | PAGED |

`map`: MANGA/COMIC → PAGED, WEBTOON → WEBTOON, NOVEL → EPUB.

**Warum Stufe 4 (Bibliotheks-Default) vor Stufe 5 (Format-Default):** Webtoons
liegen fast immer als CBZ vor. Stünde der Format-Default (CBZ → PAGED) vorher,
würde er den Bibliotheks-Default für jede CBZ kurzschließen — das WEBTOON-Tag
einer Bibliothek bliebe für Comics wirkungslos. Daher schlägt das explizite
Bibliotheks-Tag den Format-Default. Serien-Override (1) und Komga-Leserichtung
(3) bleiben höher priorisiert.

## Komga-Feld-Mapping (Naht A)

- `series.metadata.readingDirection`: `LEFT_TO_RIGHT → LTR`, `RIGHT_TO_LEFT → RTL`,
  `VERTICAL → VERTICAL`, `WEBTOON → WEBTOON`, sonst `null`.
- EPUB/Novel wird über `BookFormat.EPUB` (Komga `mediaProfile`) erkannt, **nicht**
  über die Leserichtung.

## Bekanntes Risiko

Komgas Default-Leserichtung ist `LEFT_TO_RIGHT`. Schlecht getaggte Webtoons
zeigen daher Paged, bis entweder (a) die Serie in Komga korrekt getaggt ist,
(b) der Bibliotheks-Default (Stufe 4) greift, oder (c) ein Serien-Override
(Stufe 1) gesetzt wird.

## App-Mapping (ViewerMode)

`ViewerMode` kennt nur `PAGED` und `WEBTOON`. Die Domain-`ViewerType` wird so
gemappt:

- `ViewerType.WEBTOON` → `ViewerMode.WEBTOON`
- `ViewerType.PAGED` / `ViewerType.EPUB` → `ViewerMode.PAGED`

EPUB-Bücher wählen den passenden Reader anhand des `BookFormat` (EpubReader),
unabhängig vom `ViewerMode`.

## Verweise

- Implementierung: `domain/src/main/kotlin/com/komgareader/domain/usecase/ResolveViewerType.kt`
- Tests: `domain/src/test/kotlin/com/komgareader/domain/usecase/ResolveViewerTypeTest.kt` (ein Test pro Stufe)
- Auflösungspunkt: `SeriesDetailViewModel` löst pro Buch auf; der Bibliotheks-Default
  kommt aus `Shelf.defaultContentType` (per `shelfId` nachgeschlagen).
