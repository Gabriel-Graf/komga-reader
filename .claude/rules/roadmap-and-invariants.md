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
