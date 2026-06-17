# Domain-Regel: Viewer-Typ-Auflösung

`ResolveViewerType(series, book, fallback, auto = null)` bestimmt deterministisch den
Lese-Modus. Die Reihenfolge ist verbindlich — nicht umsortieren.

| Stufe | Bedingung | Ergebnis |
|-------|-----------|----------|
| 1 | `series.contentTypeOverride != null` | map(override) |
| 2 | `book.format == EPUB` | NOVEL |
| 3 | `series.readingDirection ∈ {VERTICAL, WEBTOON}` | WEBTOON |
| 4 | `fallback != null` | map(fallback) |
| 5 | `auto != null` (Pixel-Vorschlag) | map(auto) |
| 6 | `book.format ∈ {CBZ, CBR, PDF}` | PAGED |
| 7 | sonst | PAGED |

`map`: MANGA/COMIC → PAGED, WEBTOON → WEBTOON, NOVEL → NOVEL.

> Der alte EPUB-Viewer wird ausgemustert: reflowbare Bücher lösen seit Phase 3
> nach `ViewerType.NOVEL` auf (NovelReader). `ViewerType.EPUB` bleibt vorerst als
> Legacy-Enum-Wert bestehen und wird in Phase 4 mit der App-Migration entfernt.

**Warum Stufe 4 (Bibliotheks-Default) vor dem Format-Default:** Webtoons
liegen fast immer als CBZ vor. Stünde der Format-Default (CBZ → PAGED) vorher,
würde er den Bibliotheks-Default für jede CBZ kurzschließen — das WEBTOON-Tag
einer Bibliothek bliebe für Comics wirkungslos. Daher schlägt das explizite
Bibliotheks-Tag den Format-Default. Serien-Override (1) und Komga-Leserichtung
(3) bleiben höher priorisiert.

## Stufe 5 — Auto-Vorschlag (Pixel-Heuristik)

Wenn weder ein manueller Override (1) noch eine Server-Leserichtung (3) noch ein
Bibliotheks-Default (4) greifen, füllt ein **persistierter Auto-Vorschlag** die
manga-vs-comic-Lücke (CBZ trägt den Typ nicht im Format). Er ist deterministisch
über eine **vorab persistierte** Heuristik, nicht über ein Raten im Lesepfad —
Invariante 4 bleibt gewahrt.

- **Signale (rein, unit-getestet):** mehrere Innenseiten werden gesampelt; Median
  des Seiten-Aspekts `h/w ≥ 2.0` → WEBTOON (Strips); sonst Median der
  Graufraktion `≥ 0.96` → MANGA (S/W-Interieur), `≤ 0.60` → COMIC (farbig); mittleres
  Band → kein Vorschlag (`null`, nie raten). `SuggestContentType` + `measureGrayFraction`.
- **Schwellen kalibriert** am gelabelten NAS-Korpus (per-Datei-Median über 3 Innenseiten,
  box-downsampled auf 200px). **Aspect:** normale Seiten capen niedrig — Manga ≤ 1.50,
  Comic ≤ 1.54, sogar ein **paginiert** gespeicherter Webtoon (Tower of God) ≤ 1.83; **echte
  Strips** (Solo Levelling) liegen bei **3.15–5.56** (100% ≥ 3.0). Zwischen 1.83 und 3.15 ist
  eine **leere Lücke**, daher `≥ 2.0` — fängt Strips (auch mäßig hohe anderer Quellen) mit
  breiter Margin über jeder echten Comic/Manga-Seite. **Grau:** Manga ist **echt-graustufig**
  (Graufraktion = 1.000), Comic farbig (Median 0.44), Webtoon farbig (Median 0.67); `0.96`
  trennt Manga sauber (schließt selbst den gräulichsten Webtoon bei 0.977 aus).
  **Webtoon-Limit:** ein **paginiert** gespeicherter Webtoon *ist* physisch nur normale Seiten —
  **kein** Pixel-Signal trennt ihn von einem Comic; er fällt auf `null` (kein Falsch-Rat) und
  braucht das Bibliotheks-/Server-Tag.
- **Vorbehalt:** Grau entscheidet **S/W-vs-Farbe**, **nie** die Leserichtung — ein
  S/W-Westcomic liest nicht rückwärts; RTL bleibt allein metadaten-getrieben.
- **Sampler (Shell):** `ContentTypeDetector` (`app/data`) lädt Seiten über die Naht
  (`BrowsableSource.openPage`), dekodiert downgesampelt, misst, ruft `SuggestContentType`
  und persistiert. Läuft **off the read path** (Hintergrund, lazy bei fehlendem Vorschlag),
  idempotent über `detectorVersion`. Cover (Index 0) wird übersprungen (oft farbig).
- **Persistenz:** Tabelle `series_auto_types` über `SeriesAutoTypeRepository`
  (parallel zum manuellen `SeriesOverrideRepository`, AppDatabase v21). Auch ein
  **Null-Verdikt** (mehrdeutig) schreibt eine Zeile (leerer Typname + `detectorVersion`),
  damit eine mehrdeutige Serie **einmal** gesampelt und nicht bei jedem Öffnen neu
  gescannt wird; `get` mappt den leeren Marker zurück auf `null`.
- **Out of scope (v1):** ComicInfo-`<Manga>`-Parsing + Sprach-Tiebreaker — additive
  Folge-Erweiterung; Komgas `readingDirection` deckt den manga-RTL-Server-Fall bereits.

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
- `ViewerType.PAGED` / `ViewerType.NOVEL` / `ViewerType.EPUB` → `ViewerMode.PAGED`

Reflowbare Bücher (`BookFormat.EPUB` → `ViewerType.NOVEL`) wählen den Reader
anhand des `BookFormat`. Die Verdrahtung des NovelReaders folgt in Phase 4;
bis dahin fällt `NOVEL` über den `else`-Zweig auf `ViewerMode.PAGED` zurück.

## Verweise

- Implementierung: `domain/src/main/kotlin/com/komgareader/domain/usecase/ResolveViewerType.kt`
- Tests: `domain/src/test/kotlin/com/komgareader/domain/usecase/ResolveViewerTypeTest.kt` (ein Test pro Stufe)
- Auflösungspunkt: `SeriesDetailViewModel` löst pro Buch auf; der Bibliotheks-Default
  kommt aus `Shelf.defaultContentType` (per `shelfId` nachgeschlagen).
