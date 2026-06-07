# Spec: Komga-Libraries-Import + echte Viewer-Erkennung

**Datum:** 2026-06-06
**Status:** Genehmigt (Brainstorming abgeschlossen)

## Problem

Die Viewer-Typ-Erkennung (PAGED / WEBTOON / EPUB) funktioniert nicht bei
gemischtem Inhalt. Aktuell entscheidet `ResolveViewerType` rein über
`series.contentTypeOverride ?: shelf.contentType` — d.h. eine ganze Gruppe wird
auf **einen** Inhaltstyp gezwungen. Ein Komga-Server mit Webtoons, Manga und
Novels in derselben Gruppe zeigt für alles den falschen Reader.

Komga liefert die nötige Klassifizierung bereits über die API
(`series.metadata.readingDirection`, `book.media.mediaProfile`), aber diese
Felder werden weder in den DTOs noch im Domain-Modell abgebildet.

Zusätzlich fehlt jeder Bezug zu Komgas **Libraries** (Top-Level-Container, die
Series enthalten). `browse()` zieht stumpf alle Series ohne Library-Filter.

## Ziele

1. **Echte Viewer-Erkennung** aus Komga-Metadaten statt erzwungenem Gruppen-Typ.
2. **Komga-Libraries importierbar** und einer App-Bibliothek frei zuordenbar
   (Multi-Select, perspektivisch über mehrere Server-Quellen hinweg).
3. **Saubere, server-agnostische Naht** — andere Quellen (OPDS, künftige
   Webtoon-Sites) können dieselben Fähigkeiten optional implementieren.
4. **Umbenennung** der UI: „Gruppen" → „Bibliotheken", aktuelle Browse-Tab →
   „Browsen/Entdecken" (DE + EN).
5. **Bibliotheken bearbeitbar** nach dem Erstellen (Settings-Icon → Edit-Modal).
6. Viewer-Resolution-Regel **dokumentiert** (docs + Projekt-Skill).

## Nicht-Ziele (YAGNI)

- Keine Library-Verwaltung serverseitig (anlegen/löschen von Komga-Libraries).
- Keine Komga-Collections/Read-Lists in diesem Schritt (nur Libraries).
- Kein automatisches Re-Tagging schlecht gepflegter Komga-Metadaten.

---

## Architektur

### Naht A — Domain: `ReadingDirection`

Neues Domain-Enum, quellen-neutral:

```kotlin
enum class ReadingDirection { LTR, RTL, VERTICAL, WEBTOON }
```

`Series` bekommt ein nullable Feld:

```kotlin
data class Series(
    // ...bestehend...
    val contentTypeOverride: ContentType? = null,
    val readingDirection: ReadingDirection? = null,   // NEU; null = unbekannt
)
```

Komga füllt das Feld. OPDS und andere Quellen, die es nicht kennen, lassen es
`null` — dann greift der Fallback.

### Naht B — Viewer-Resolution (die Domain-Regel)

`ResolveViewerType` wird auf Buch-Ebene aufgelöst (EPUB ist pro Buch, Webtoon
pro Serie). Neue Signatur:

```kotlin
class ResolveViewerType {
    operator fun invoke(
        series: Series,
        book: Book,
        fallback: ContentType?,   // shelf.defaultContentType
    ): ViewerType
}
```

**Prioritätsregel (verbindlich, in Skill + docs dokumentiert):**

| Stufe | Bedingung | Ergebnis |
|-------|-----------|----------|
| 1 | `series.contentTypeOverride != null` | map(override) |
| 2 | `book.format == EPUB` | `EPUB` |
| 3 | `series.readingDirection ∈ {VERTICAL, WEBTOON}` | `WEBTOON` |
| 4 | `book.format ∈ {CBZ, CBR, PDF}` | `PAGED` |
| 5 | `fallback != null` | map(fallback) |
| 6 | sonst | `PAGED` |

`map(ContentType)`: MANGA/COMIC → PAGED, WEBTOON → WEBTOON, NOVEL → EPUB
(bestehende Logik).

Begründung Reihenfolge: Manueller Override schlägt alles. EPUB ist hart am
Format erkennbar und eindeutig. Webtoon-Richtung schlägt das Default-Paged von
Archiv-Formaten. Fallback nur, wenn Metadaten nichts hergeben (z.B. Webtoon
ohne gesetzte `readingDirection`, Komga-Default ist `LEFT_TO_RIGHT`).

### Naht C — Source-Container-Capability

Neue **optionale** Capability im `domain/source`-Paket:

```kotlin
data class SourceContainer(val id: String, val name: String)

interface ContainerSource : MediaSource {
    suspend fun listContainers(): List<SourceContainer>
}
```

`SourceFilter` wird erweitert:

```kotlin
data class SourceFilter(
    val seriesId: String? = null,
    val containerIds: List<String> = emptyList(),  // NEU; leer = kein Filter
)
```

- `KomgaSource` implementiert `ContainerSource`: `listContainers()` →
  `GET /api/v1/libraries` → `[{id, name}]`. `browse()` hängt bei nicht-leerer
  `containerIds` den Query-Param `library_id` (mehrfach) an `GET /series`.
- OPDS/Stub implementieren `ContainerSource` **nicht** → UI behandelt solche
  Quellen als „ganze Quelle, keine Container".

### Naht D — Bibliothek (ehem. Shelf), Multi-Source/Multi-Lib

```kotlin
data class Shelf(
    val id: Long,
    val name: String,
    val sources: List<ShelfSource>,
    val defaultContentType: ContentType?,   // optionaler Viewer-Notnagel
)

data class ShelfSource(
    val sourceId: Long,
    val containerIds: List<String>,   // leer = ganze Quelle
)
```

`ShelfRepository`:

```kotlin
interface ShelfRepository {
    val shelves: Flow<List<Shelf>>
    suspend fun add(shelf: Shelf): Long
    suspend fun update(shelf: Shelf)   // NEU
    suspend fun delete(id: Long)
}
```

**Room-Migration v5 → v6** (`AppDatabase` version 6):
- `ShelfEntity`: Spalte `sourceIds: String` (CSV) **und** `contentType: String`
  ersetzt durch `sources: String` (JSON von `List<ShelfSource>`) und
  `defaultContentType: String?` (nullable).
- Echte Migration: alte CSV-`sourceIds` → JSON `[{sourceId, containerIds:[]}]`,
  alter `contentType` → `defaultContentType`. Kein Datenverlust.

### Naht E — Browse-Flow

`GroupBrowseViewModel` (umzubenennen passend zur „Bibliothek"-Terminologie,
aber Datei-Rename optional): pro `ShelfSource` mit dessen `containerIds` browsen,
Ergebnisse mergen. Beim Öffnen eines Buchs Viewer via
`ResolveViewerType(series, book, shelf.defaultContentType)`.

### Naht F — Modal (erstellen + bearbeiten)

Ein gemeinsames `LibraryEditDialog` (ersetzt `CreateGroupDialog`):
- Name-Feld (vorbefüllt im Edit).
- Pro verbundener `ContainerSource`: ausklappbarer Abschnitt mit
  Checkbox-Liste der `listContainers()`-Ergebnisse; Vorauswahl im Edit.
- Optional Default-Typ-Chips (`defaultContentType`, auch „keiner").
- Speichern → `add()` (neu) bzw. `update()` (Edit).

Bibliotheks-Karte in `GroupsScreen` bekommt ein Settings-Icon → öffnet das
Modal im Edit-Modus.

### Naht G — i18n-Rename (DE + EN)

| Key (Bedeutung) | DE alt | DE neu | EN neu |
|-----------------|--------|--------|--------|
| Gruppen-Tab | Gruppen | Bibliotheken | Libraries |
| Browse-Tab | Stöbern | Browsen | Browse |
| (ggf. „Bibliothek"-Label der Browse-Tab) | Bibliothek | Entdecken | Discover |

Exakte Keys beim Implementieren aus `de.kt`/`en.kt` verifizieren; beide Sprachen
parität-konform halten.

### Naht H — Doku + Projekt-Skill

- `docs/` (z.B. `docs/domain/viewer-type-resolution.md`): Prioritätstabelle +
  Begründung + Komga-Feld-Mapping.
- Projekt-Skill `.claude/skills/komga-viewer-type-resolution/SKILL.md`: kompakte
  Domain-Regel, damit künftige Arbeit die Reihenfolge nicht bricht.

---

## Datenfluss (neu)

```
Komga API
  GET /libraries           → SourceContainer[]        (Modal: Lib-Auswahl)
  GET /series?library_id=  → SeriesDto(+readingDirection)
  GET /books               → BookDto(+mediaProfile)
        ↓ KomgaMapper
  Series(readingDirection), Book(format)
        ↓ ResolveViewerType(series, book, shelf.defaultContentType)
  ViewerType → Reader
```

## Tests (TDD)

- `ResolveViewerTypeTest`: je Stufe der Prioritätsregel ein Test (Override,
  EPUB-Format, WEBTOON-Richtung, Paged-Format, Fallback, Default-Paged).
- `KomgaMapperTest`: `readingDirection`-Mapping inkl. unbekannter/leerer Wert →
  null; `mediaProfile`/Format-Mapping.
- `KomgaSource` (MockWebServer): `listContainers()` parst `/libraries`;
  `browse()` mit `containerIds` setzt `library_id`-Param.
- Migration v5→v6 (Room migration test): CSV+contentType → JSON+nullable.
- ShelfRepository: `update()` persistiert und emittiert.

## Risiken

- **Komga-Default `LEFT_TO_RIGHT`**: schlecht getaggte Webtoons zeigen Paged bis
  Default-Fallback oder manueller Override greift — bewusst akzeptiert,
  dokumentiert.
- **Migration**: bestehende Gruppen müssen verlustfrei übernommen werden; echte
  Room-Migration mit Test absichern (kein destructive fallback).

## Offene Punkte

Keine — Design genehmigt.
