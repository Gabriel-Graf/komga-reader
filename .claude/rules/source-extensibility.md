# Quellen-Erweiterbarkeit: Features quellen-agnostisch hinzufügen

Das wichtigste Versprechen der App: **mehr als nur Komga**. Weitere Server (Kavita, OPDS),
mehrere Quellen gleichzeitig, lokale Ordner, später Plugins. Damit das hält, gilt für **jedes**
neue Metadatum und **jedes** neue Quellen-Feature dieses Muster — nie eine Abkürzung über
quellenspezifischen Code in `domain` oder `app`.

## Kochrezept A — neues Metadatum/Feld (z. B. Beschreibung, Status, Genres)

Referenz-Implementierung: der Serien-Detail-Umbau (summary/status/genres/readingDirection).
Schritte, **in dieser Reihenfolge**:

1. **Domain-Modell erweitern** (`domain/model/*.kt`): Feld mit sinnvollem Default hinzufügen
   (`val summary: String? = null`). Generischer, quellen-neutraler Name — kein Komga-Jargon.
2. **Interface erweitern, falls nötig** (`source-api/…/source/MediaSource.kt`): braucht es einen neuen
   Abruf (z. B. `seriesDetail(remoteId): Series?`)? Dann ins `BrowsableSource`-Interface, mit
   nullbarem/leerem Rückgabe-Vertrag für Quellen, die es nicht liefern können.
3. **Jede** Quelle implementiert: Komga (echtes Mapping), OPDS (so viel der Feed hergibt, sonst `null`).
   `StubSource` bleibt unberührt (kennt keine Inhalte).
4. **Quellenspezifisches Mapping** lebt **nur** im Quellen-Modul (`KomgaMapper`, DTOs). DTO-Felder
   mit Defaults (`val summary: String = ""`), Mapper macht `.ifBlank { null }`.
5. **ViewModel** zieht das Feld über das Interface, fällt bei Fehler/Fehlen sauber zurück
   (`runCatching { source.seriesDetail(id) }.getOrNull()`).
6. **UI** rendert konditional: nur zeigen, wenn vorhanden. Lokalisierte Roh-Werte (Status etc.)
   über `i18n` übersetzen, nie hartkodiert.
7. **Tests:** Mapper-Unit-Tests für gesetzt **und** leer/null. E2E gegen lokale Test-Komga
   (Metadaten via `PATCH /api/v1/series/{id}/metadata` setzen — siehe [[local-test-komga]]).

**Anti-Pattern (sofort ablehnen):** Komga-DTO oder Komga-spezifisches Feld bis in `app`/`domain`
durchreichen; `if (source is KomgaSource)` in der UI; Feld nur in einer Quelle füllen und in den
anderen vergessen (bricht still bei Quellenwechsel).

## Kochrezept B — ganz neuer Server (z. B. Kavita, Phase 3)

1. Neues Modul `source-kavita`, hängt **nur** von `domain` ab.
2. Klasse implementiert `BrowsableSource` (+ `SyncingSource`, falls Server Fortschritt syncen kann).
   `SourceKind`-Eintrag ergänzen. Deterministische `id` via `SourceId.of(...)`.
3. Eigener Mapper + DTOs im Modul. HTTP wie bei Komga (Retrofit/OkHttp + kotlinx.serialization).
4. In `SourceManager`/DI registrieren. **Keine** Änderung an `domain` oder `app` außer Registrierung.
5. Vertragstests gegen gemockte Server-Responses (MockWebServer-Muster wie `source-komga`).

Beweis, dass das Muster trägt: `source-opds` existiert bereits genau so und teilt sich kein
Komga-Wissen.

Zweites Beispiel (Ist, 2026-06-14): **`source-local`** (`LocalSource`, `SourceKind.LOCAL`, id 0) liest
einen SAF-Geräteordner als Quelle — genau nach diesem Rezept, plus zwei Besonderheiten, die jede
gerätenahe oder rein-lokale Quelle betreffen: (1) das Modul ist eine **Android-Library** (nicht pur-JVM
wie `source-opds`), weil es `Context`/SAF (`DocumentFile`) braucht — pure Logik (Parser/Mapper/Sortierung)
bleibt trotzdem in plain-Kotlin-Klassen, JVM-unit-getestet; es hängt weiter nur an `domain`/`source-api`,
**nie** an `render-core`/UI. (2) remoteIds müssen **opak** sein (keine `/`), weil die App sie als einzelne
Nav-Pfadsegmente fädelt — `LocalSource` exponiert Base64-URL-kodierte remoteIds und dekodiert intern;
quellenspezifische IDs mit Sonderzeichen (Pfade, Leerzeichen) immer kodieren. Lesen ohne seitenweises
Streaming: `pages()` = `emptyList()` zurückgeben, dann rendert der Reader whole-file (s.
`architecture-seams.md` „Reader-Lesepfad").

## Kochrezept C — externe Datei „öffnen mit" (kein Quellen-Modul, transiente Download-Zeile)

Manchmal kommt ein Werk **nicht** aus einer registrierten Quelle, sondern als einmaliger
„Öffnen mit"-VIEW-Intent (z. B. eine `.epub`/`.cbz`/`.cbr`/`.pdf` aus dem Boox-Dateimanager).
Dafür gibt es **keine** neue `MediaSource` — die reservierte transiente Quellen-ID
`SourceId.EXTERNAL = 1L` (`source-api`) und der **bestehende Offline-/Download-Lesepfad** tragen es:

- **External Book File Handler (Ist, 2026-06-15):** `detectBookFormat(mime, fileName): BookFormat?`
  (`domain/usecase`) erkennt das Format (`enum BookFormat{CBZ,CBR,PDF,EPUB}`). `ExternalBookOpener`
  (`app/data`) `prepareEphemeral` fügt eine **transiente** `DownloadedBook`-Zeile mit
  `sourceId = SourceId.EXTERNAL` und `localPath` = der content-URI ein → der bestehende Reader liest
  sie über den Offline-Pfad (kein Reader-Umbau, keine `MediaSource`); `importToFolder` kopiert die
  Bytes via `DocumentFile` in den lokalen(=Download-)SAF-Ordner; `purgeTransient`
  (`DownloadRepository.removeBySourceId` → DAO `deleteBySourceId`) räumt die EXTERNAL-Zeilen bei
  `SyncCoordinator.onAppStart` auf. **`LocalDownloadSync` reconciliert nur `sourceId == SourceId.LOCAL`**
  (id 0) — die EXTERNAL-Zeilen (id 1) bleiben unberührt; das ist der Grund für die getrennte
  reservierte ID. Verhalten persistiert über `SettingsRepository.externalOpenBehavior`
  (`ExternalOpenBehavior{ASK,IMPORT,READ_ONLY}`, Room-Key `external_open_behavior`, keine Migration),
  editierbar in Settings → Downloads. Der Download-Ordner-Picker dort setzt jetzt zugleich den lokalen
  Ordner (`setBothFolders` als Default; der separate „Gemeinsamer Ordner"-Button ist entfernt). **Device-
  Vorbehalt:** EPUB-Ephemeral-Open (crengine-`.so` arm64-only) und die tatsächliche „Öffnen mit"-Listung
  im Boox-Dateimanager sind **noch nicht auf echter arm64-Boox verifiziert** (Soll); Build + Unit + DAO-
  androidTest sind grün.

## Bezug

Setzt `architecture-seams.md` (Naht A) voraus. Gehört zu [[project-komga-eink-reader]].
