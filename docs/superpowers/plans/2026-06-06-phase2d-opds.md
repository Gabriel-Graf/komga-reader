# Phase 2d — OPDS-Source

> REQUIRED SUB-SKILL: subagent-driven-development.

**Goal:** Eine zweite Backend-Quelle hinter derselben Naht: `OpdsSource` spricht einen OPDS-Katalog (Atom-XML). Beweist, dass die `MediaSource`-Naht über Komga hinaus trägt. Host-testbar mit MockWebServer (kein Gerät nötig).

**Architecture:** Neues Kotlin/JVM-Modul `:source-opds`. Parst OPDS-Atom-Feeds mit JVM-DOM (`javax.xml.parsers`). `OpdsSource : BrowsableSource` (+ `downloadFile`): `browse`/`search` holen einen Feed und mappen `<entry>` → `Series`; `books` liefert pro Entry ein `Book` (Format aus dem Acquisition-Link-`type`); `downloadFile` lädt die Publikation über den Acquisition-Link. OPDS unterstützt kein per-Seite-Streaming → Lesen läuft über Download (wie Phase 2c). `pages()` liefert leere Liste, `openPage` wirft `UnsupportedOperationException` (Lesen via downloadFile + MuPDF).

**Tech:** Kotlin/JVM · OkHttp · JVM-DOM-XML · JUnit5 · MockWebServer.

## OPDS-Grundlagen (verifiziert genug für MVP)
Atom-Feed: `<feed>` mit `<entry>`-Elementen. Pro Entry: `<title>`, `<id>`, `<link rel="..." href="..." type="...">`. Relevante rels: `http://opds-spec.org/acquisition` (Download), `http://opds-spec.org/image` oder `.../image/thumbnail` (Cover). Acquisition-`type` z.B. `application/epub+zip`, `application/x-cbz`, `application/pdf`.

---

### Task 0: Modul `:source-opds`

**Files:** `settings.gradle.kts` (`include(":source-opds")`), `gradle/libs.versions.toml` (falls nötig, reuse vorhandener okhttp/junit/mockwebserver), `source-opds/build.gradle.kts`.

- [ ] `source-opds/build.gradle.kts`: `kotlin("jvm")` + serialization NICHT nötig (DOM). Deps: `implementation(project(":domain"))`, `implementation(libs.kotlinx.coroutines.core)`, `implementation(libs.okhttp)`; test: `kotlin("test")`, junit-jupiter, okhttp-mockwebserver, coroutines-test. `tasks.test { useJUnitPlatform() }`, `jvmToolchain(21)`.
- [ ] `./gradlew :source-opds:build` → SUCCESSFUL. Commit: `build: Modul :source-opds`.

---

### Task 1: OpdsFeedParser (DOM → Modell, TDD)

**Files:** `source-opds/.../OpdsModels.kt`, `OpdsFeedParser.kt`; test `OpdsFeedParserTest.kt`.

- [ ] Modelle: `data class OpdsEntry(val id: String, val title: String, val coverHref: String?, val acquisitionHref: String?, val acquisitionType: String?)`.
- [ ] **TDD:** Test parst einen Beispiel-Atom-Feed (String) → 2 Entries mit korrektem Titel/id/coverHref/acquisitionHref/-type. Beispiel-Feed im Test inline:
```xml
<?xml version="1.0"?>
<feed xmlns="http://www.w3.org/2005/Atom">
  <entry><title>Vinland Saga 01</title><id>urn:vs:1</id>
    <link rel="http://opds-spec.org/image/thumbnail" href="/cover/1.jpg" type="image/jpeg"/>
    <link rel="http://opds-spec.org/acquisition" href="/dl/1.cbz" type="application/x-cbz"/></entry>
  <entry><title>Mistborn</title><id>urn:mb:1</id>
    <link rel="http://opds-spec.org/acquisition" href="/dl/mb.epub" type="application/epub+zip"/></entry>
</feed>
```
Erwartung: 2 Entries; Entry0 cover `/cover/1.jpg`, acquisition `/dl/1.cbz` type `application/x-cbz`; Entry1 cover null, acquisition `/dl/mb.epub`.
- [ ] `OpdsFeedParser.parse(xml: String): List<OpdsEntry>` via `DocumentBuilderFactory` (namespace-aware), iteriere `entry`-Elemente, lies `title`/`id`-Textinhalt und `link`-Elemente (rel/href/type). → GREEN. Commit: `feat(opds): Atom-Feed-Parser (TDD)`.

---

### Task 2: OPDS-Format-Mapping + OpdsSource + Factory (MockWebServer)

**Files:** `source-opds/.../OpdsFormat.kt`, `OpdsSource.kt`; test `OpdsSourceTest.kt`.

- [ ] `opdsTypeToFormat(type: String?): BookFormat` — `application/x-cbz`/`application/zip`→CBZ, `...x-cbr`/`x-rar`→CBR, `application/pdf`→PDF, `application/epub+zip`→EPUB, sonst CBZ. (TDD, paar Fälle.)
- [ ] `OpdsSource(BrowsableSource)` mit OkHttp + Basis-URL (Catalog-Root) + `OpdsFeedParser`:
  - `browse(page, filter)`: GET Catalog-URL → parse → map Entry→`Series(id=0, sourceId, remoteId=entry.id, title, coverUrl=absolut(coverHref))`. `hasNextPage=false` (MVP, keine Feed-Paginierung).
  - `search(query, page)`: GET `catalogUrl?query={q}` (MVP) → gleich.
  - `books(seriesRemoteId)`: MVP — ein Entry = ein Buch; baue `Book(remoteId=entry.id, format=opdsTypeToFormat(...), pageCount=0)`. (Da OPDS Series/Book nicht trennt, hält die Source eine Map id→Entry aus dem letzten browse; für den Test reicht: erneutes Parsen oder gemockte zweite Antwort.)
  - `pages()` → `emptyList()`; `openPage()` → `throw UnsupportedOperationException("OPDS liest via Download")`.
  - `downloadFile(bookRemoteId)`: GET den Acquisition-Href → bytes.
  - `id = SourceId.of(name, SourceKind.OPDS, catalogUrl)`, `kind = OPDS`.
- [ ] `OpdsSourceFactory.create(name, catalogUrl): OpdsSource`.
- [ ] **MockWebServer-Test:** Enqueue den Beispiel-Feed → `browse` liefert 2 Series (Titel „Vinland Saga 01", „Mistborn"); zweiter Test: `downloadFile` gegen einen gemockten Acquisition-Response liefert die Bytes; `openPage` wirft. → GREEN. Commit: `feat(opds): OpdsSource + Factory (MockWebServer)`.

---

### Task 3: E2E-Test (voller OPDS-Fluss)

- [ ] `OpdsE2ETest`: Feed → browse → erste Series → (gemockter) downloadFile → Bytes. `./gradlew :source-opds:test` → alle grün. Commit: `test(opds): E2E browse→download`.

---

## Self-Review
- **Spec §11 P2 OPDS + §5 Naht-Erweiterbarkeit:** zweite `MediaSource` über dieselbe Naht, host-getestet → Tasks 1,2,3.
- **Verschoben:** App-UI-Anbindung (Multi-Source-Auswahl/Server-Typ-Wahl in Settings — die App nutzt aktuell fix KomgaSourceProvider; Multi-Source-Verdrahtung + Shelf-UI ist eigener Schritt), Feed-Navigation/Paginierung, OPDS-PSE-Page-Streaming, Auth-Varianten.
- **Abnahme:** `:source-opds:test` grün — Parser + Source + E2E gegen MockWebServer.
