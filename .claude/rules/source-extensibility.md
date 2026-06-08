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

## Bezug

Setzt `architecture-seams.md` (Naht A) voraus. Gehört zu [[project-komga-eink-reader]].
