# Roadmap & Arbeits-Invarianten

Damit künftige Features nicht aus den Augen verloren werden: die Phasen-Roadmap (Spec §11) ist die
Landkarte. Jede Phase = eigene Spec→Plan→Bau-Runde, jede für sich lauffähig. **Beim Bauen immer
prüfen, ob eine Entscheidung eine spätere Phase verbaut** — wenn ja, hinter die Naht ausweichen.

## Phasen

- **Phase 1 / MVP — ✅ fertig:** Komga verbinden → Bibliothek → PagedViewer streamen → Boox-Tasten +
  Basis-Refresh → Progress-Sync. Beweist die ganze Pipeline.
- **Phase 2 — ✅ fertig:** WebtoonViewer + EpubViewer · Download/Offline · Regal-Verwaltung
  (mehrere Quellen, Typ-Tag) · OPDS-Quelle · geführter Comic-Reader inkl. Panel-Erkennung (heute
  über die externe Lib **comic-cutter**; das frühere In-Tree-Modul `guided-view` ist entfernt).
- **Phase 3 — offen:** Cover-Farbfilter (Kaleido-Sättigung/Kontrast vor Anzeige) · per-Region-Refresh-
  Feintuning · erweiterte E-Ink-Settings · **weiterer Server: Kavita** (`source-kavita` nach Naht A).
- **Phase 4 — offen:** **Runtime-Plugin-Loader** (nutzer-installierbare Online-Quellen, Mihon-Modell) ·
  optional crengine als EPUB-Engine hinter `Document`.

## Noch offen / nicht vergessen (auch außerhalb der Phasen)

- **Geführter Comic-Reader — gebaut (Ist):** `ComicReaderScreen`/`ComicReaderViewModel` (Tap →
  Panel-Zoom → Weiter, hinter `Viewer`-Naht). Die Panel-Erkennung liefert die externe Lib
  **comic-cutter** über `PanelSourceProvider` — geometrisch per Default, **ML via ONNX** bei
  installiertem `PANEL_MODEL`-Plugin + `useMlDetection`. (Das frühere „UI fehlt noch"-Item ist
  damit erledigt — die UI ist längst da.)
- **Lesestatistik — gebaut (Ist, 2026-06-15):** Lokales Zeittracking pro Reader-Typ
  (`ReaderKind{PAGED,WEBTOON,COMIC,NOVEL}`), gecappte Per-Seite-Deltas (kein Hintergrund-Timer —
  E-Ink-Akku), started/finished-Zähler aus vorhandenen Progress-Tabellen abgeleitet (kein neues
  Tracking). Domain-Modelle `ReadingSession`/`ReadingStats`/`ReadingTimeCaps`/`ReadingStatsAggregator`
  (pure, unit-getestet); Room-Tabelle `reading_session` (`MIGRATION_17_18`, Schema v18);
  `ReadingSessionTracker` (@Singleton, app/data); `ReadingSessionEffect` in jedem Reader-Screen;
  Settings → Statistik (`SettingsSectionId.STATISTICS`). Kein Server-Sync — rein lokal.
- **Roman-Wort-Lesezeichen — gebaut (Ist, 2026-06-15; Runtime gerätegebunden offen):** Tap-auf-Wort
  setzt/entfernt im Roman-Reader ein nummeriertes Lesezeichen (liste/umbenennen/löschen/anspringen,
  Marker auf der Seite). Render-Naht-Erweiterung `ReflowableDocument.wordAt`/`rectsFor` (+ zwei neue JNI),
  lokal-only Room-Tabelle `novel_bookmark` (`MIGRATION_18_19`, Schema v19, **nicht** synchronisiert). Compile- +
  unit-verifiziert, `:app:assembleDebug` grün; das **Runtime-Wort-Tap-/Marker-Verhalten** ist mangels
  arm64-crengine-`.so` auf dem Emulator **nur auf echter Boox verifizierbar** (noch ausstehend). Details:
  `architecture-seams.md` (Naht B).
- **Externer Buch-Datei-Handler („Öffnen mit") — gebaut (Ist, 2026-06-15; Runtime gerätegebunden offen):**
  Die App ist System-Handler für `.epub`/`.cbz`/`.cbr`/`.pdf` (VIEW-`<intent-filter>` in `MainActivity`,
  content-Schema + Buch-MIME-Typen + `application/octet-stream`). Eine externe Datei öffnet **ephemer**
  über eine **transiente** Download-Zeile unter `SourceId.EXTERNAL = 1L` (`ExternalBookOpener.prepareEphemeral`)
  — kein Reader-Umbau, keine neue `MediaSource`; `importToFolder` kopiert in den lokalen(=Download-)SAF-Ordner;
  `purgeTransient` räumt bei `SyncCoordinator.onAppStart` auf. Verhalten merkbar/editierbar
  (`SettingsRepository.externalOpenBehavior`, `ExternalOpenBehavior{ASK,IMPORT,READ_ONLY}`) in Settings →
  Downloads; der Download-Ordner-Picker setzt jetzt zugleich den lokalen Ordner (`setBothFolders`-Default).
  Compile- + unit-verifiziert (`detectBookFormat`-Tests), `:app:assembleDebug` grün, `DownloadDaoSourceIdTest`
  androidTest grün. **Noch nicht auf echter arm64-Boox verifiziert** (Soll): EPUB-Ephemeral-Open (crengine-`.so`
  arm64-only) und die tatsächliche „Öffnen mit"-Listung im Boox-Dateimanager. Details:
  `source-extensibility.md` (Kochrezept C), `architecture-seams.md` (Naht A).
- **Bekannte Minor-Issues** (siehe [[project-komga-eink-reader]]): Reader fängt Volume-Tasten global ab
  (sollte nur im Reader); Streaming-PagedViewer nutzt Komga-fertige Seitenbilder via Coil, nicht MuPDF.
- **Plugin-Bereitschaft wahren:** keine quellenspezifischen Annahmen ins `MediaSource`-Interface backen
  (Phase 4 hängt sich dort ein). Siehe `source-extensibility.md`.

## Arbeits-Invarianten

- **TDD:** Domain/Mapper sind pure Funktionen → Unit-Test zuerst (gesetzt **und** leer/null). Red-Green-Refactor.
- **E2E pro Feature:** kleines Script gegen die lokale Test-Komga (Docker, [[local-test-komga]]) ODER
  Emulator/Boox-Screenshot. Behauptung „fertig" nur mit gezeigtem Beweis (Build grün + sichtbares Verhalten).
- **Autonom durcharbeiten:** Phasen/Pläne ohne Rückfrage durchziehen, nur bei echten Design-Forks fragen
  (siehe [[autonomous-execution-directive]]). Umgebungs-Setup (Gradle/SDK/NDK, Docker, AVD) selbst erledigen.
- **Verifikations-Setup:** lokale Test-Komga + Emulator `eink_test` (auf Boox-Maße 1264×1680@300) sind
  in [[local-test-komga]] dokumentiert; echte Boox per USB für Onyx-/Refresh-Verhalten.
- **Quellen-Provenance & Lizenz:** App ist **AGPL-3.0-or-later** (MuPDF). Drittsoftware in `NOTICE`.

Gehört zu [[project-komga-eink-reader]].
