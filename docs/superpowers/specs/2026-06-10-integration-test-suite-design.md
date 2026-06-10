# Integrationstest-Suite + CI — Design

> Status: Design (genehmigt 2026-06-10). Nächster Schritt: Implementierungsplan (writing-plans).
> Bezug: `.claude/rules/big-picture-and-goals.md`, `architecture-seams.md`,
> `source-agnostic-integration.md`, `roadmap-and-invariants.md`,
> `2026-06-09-user-collections-server-sync-design.md` (Collections-Push/Pull, parallel in Arbeit).

## 1. Ziel & Abgrenzung

Eine **feature-orientierte Integrationstest-Suite**, die echte komplette Features gegen echte
Backends prüft — nicht einzelne Funktionen/Module (das tun die 272 bestehenden Unit-Tests).
Jeder Test bewacht zusätzlich eine **Big-Picture-Invariante** (Quellen-Agnostik, Viewer-Dispatch,
Geräteklasse, Plugin-Integration), damit eine bequeme Abkürzung ein Langzeitziel nicht still
zumauert.

Die Suite läuft **zuerst lokal** vollständig, danach als **GitLab-CI** auf dem self-hosted
KVM-Runner. Features, die noch nicht existieren (Plugins, modulare UI, Collections-Sync), werden
**vorformuliert** und als `[pending]` markiert — sie laufen, sobald das Feature gebaut ist.

**Nicht-Ziele:** Pixel-genaue Render-Vergleiche (MuPDF/crengine-versionsabhängig); Last-/Performance-Tests;
Ersatz der Unit-Tests.

## 2. Test-Ebenen (Hybrid)

Beide Sets sind `androidTest` (laufen on-device im Emulator; KVM ist vorhanden, daher kein
JVM/Robolectric-Umbau nötig).

| Set | Treibt über | Deckt ab | Stärke |
|---|---|---|---|
| **Seam-Set** | VM · `ActiveSource` · `SourceManager` · Room gegen echte Komga | A2–6, B, C12–13, D15–17, E, F, G23, H2/H5–6/H10 | schnell, stabil, beweist Architektur-Invarianten |
| **UI-Set** | Compose-Test-Rule + Semantics, echter Nutzerpfad (Dialog→Tap→Reader) | A1/A4, B7, C9–11, D14, G22, H1/H3–4/H7–9/H11 | am nächsten am echten „kompletten Feature" |

Manche Szenarien laufen auf beiden Ebenen (z.B. „Server entfernen → StubSource" Seam **und** UI).

## 3. Fixture-Strategie (zweistufig, lizenz-getrieben)

### Tier 1 — committbar (deterministischer Kern)
Public-Domain / CC-BY / CC0-Comics + eigene minimale EPUBs. **Im Repo** unter `tools/ci-fixtures/content/`.
Echter, renderbarer Inhalt, lizenz-sauber, teilbar, bit-deterministisch — trägt die Masse der
Struktur-/Agnostik-Tests und ist auch ohne NAS CI-portabel (Default-Pfad).

- **Deferred Task:** Web-Suche nach geeigneten Werken — Kandidaten: Pepper&Carrot (CC-BY),
  Sintel-Comic, Comics auf Wikimedia Commons, PD vor ~1929. Lizenz pro Fund in `PROVENANCE.md`.
- ⚠️ Original-1939-Batman u.ä. sind **noch geschützt** → nicht verwenden. Nur explizit PD/CC.

### Tier 2 — runner-lokal eingefroren (echte Vielfalt)
Kuratierter Subset aus `/mnt/nas/Manga`, einmal kopiert nach `~/komga-ci-fixtures/` auf dem Runner.
**Nie committet** (urheberrechtlich geschützt). Nur für Render-Smoke / Real-World-Edge-Cases
(echtes cbz/cbr/pdf/epub mit echten Eigenheiten). Eingefroren = deterministisch.

### Determinismus-Garanten
- **`tools/ci-fixtures/manifest.json`** (committbar, nur Metadaten): erwartete Serien-Namen,
  Buch-Counts, Content-Typen, SHA der Quelldateien (Tier 1 vollständig, Tier 2 als Referenz).
  Tests asserten gegen das Manifest — kein „was gerade auf dem NAS liegt".
- **`tools/ci-fixtures/PROVENANCE.md`** (`data-provenance`-Regel, Pflicht): pro Quelle Name, URL,
  Lizenz (SPDX wo möglich), Cap/Volumen, Erfassungsdatum, Risk-Notiz. Tier 2 explizit als
  „runner-lokal, nie verteilt" markiert.

## 4. Fixture-Topologie (Komga-Instanzen)

| Instanz | Libraries | Zweck |
|---|---|---|
| **Komga-A** | „Manga" (cbz) + „Novels-A" (epub, Set 1) | Hauptquelle, Manga + Novel-Dispatch |
| **Komga-B** | „Webtoon" (cbz) + „Novels-B" (epub, Set 2, **disjunkt** zu A) | zweite Quelle, Webtoon-Dispatch, n-unterschiedliche-Server |
| **Komga-C** (optional) | Spiegel von A (identischer Content) | n-**gleiche**-Server, SourceId-Stabilität/Dedup |

Jede Instanz liefert zusätzlich gratis einen **OPDS-Endpoint** → gemischte Quellenarten ohne
Extra-Infra. Disjunkte Novel-Sets ermöglichen „Werk gehört zur zweiten Quelle"-Asserts und
cross-source-Collections.

## 5. Orchestrierung (Hybrid: Compose + gecachtes Volume)

`tools/ci-fixtures/`:
- **`docker-compose.yml`** — N Komga-Container (`gotson/komga`), je eigener Port, Fixture-Dirs
  read-only gemountet (Tier 1 aus Repo, Tier 2 vom Runner-Pfad).
- **`seed.sh`** — via Komga-REST: Admin-User + API-Key anlegen, Libraries auf die Mount-Pfade
  zeigen lassen, auf **Scan-Ende pollen** (`/api/v1/libraries` Status), Topologie aus §4 herstellen.
- **`freeze-from-nas.sh`** — Einmal-Kurator: kopiert den Tier-2-Subset deterministisch aus
  `/mnt/nas/Manga`, schreibt Manifest-Einträge + PROVENANCE.
- **Volume-Cache:** das gescannte Komga-config-Volume wird zwischen Läufen gecacht; Cache-Key =
  Hash von `manifest.json`. Rebuild nur bei Fixture-Änderung. Lokal startet man über den
  Compose+Seed-Pfad; die Cache-Schicht ist die CI-Beschleunigung (Start in Sekunden statt Scan-Minute).

## 6. Harness-Ergonomie (DRY-Topologie an einem Ort)

`app/src/androidTest/.../ci/`:
- **`CiKomga`** — liefert pro Instanz (`A`/`B`/`C`) ein `ServerConfig` (Port, API-Key, OPDS-Creds).
  Tests sagen deklarativ „registriere A+B", nicht hardcoded URLs über die Suite verstreut.
  (Heute hardcoden alle Instrumented-Tests `10.0.2.2:25600` + Key — diese Schuld wird hier
  zentralisiert; `shared-structure-before-variants`.)
- **`CiFixtures`** — liest `manifest.json`, stellt erwartete Counts/Namen für Asserts bereit.
- **Base-Rules** — gemeinsames Setup (DB, isolierter Credential-Store, Source-Registrierung).

## 7. Prerequisites (vor den Tests, eigene Plan-Phase)

1. **Prefs-Isolations-Bug fixen** — `KeystoreCredentialStore`/Prefs-Dateiname injizierbar machen;
   Tests nutzen eine **eigene** Prefs-Datei. Heute ruft `clear()` auf die ECHTEN App-Prefs →
   jeder Testlauf löscht den gespeicherten API-Key der App (`local-test-komga`, bekannter Bug).
   Blocker für wiederholbare/parallele Läufe.
2. **Runner-Config** — `devices = ["/dev/kvm"]` in `[runners.docker]` der `config.toml`
   (least-privilege, kein `privileged = true`); CI-Image als root, damit der Job-User `/dev/kvm`
   (root:kvm 0660) nutzen kann. Verifiziert: Host ist bare-metal mit `/dev/kvm` + VT-x; einzige
   fehlende Zeile ist das Device-Passthrough.

## 8. CI-Pipeline (`.gitlab-ci.yml`)

| Stage | Inhalt | Umgebung |
|---|---|---|
| `build` | `assembleDebug` | JVM |
| `unit` | `test` (272 bestehende) | JVM, KVM-frei, schnell |
| `fixtures` | Compose hoch + `seed.sh` (oder Cache-Hit) | Docker |
| `instrumented` | headless Emulator (`-no-window -gpu swiftshader_indirect`, KVM) → `connectedDebugAndroidTest` | Emulator + KVM |
| `teardown` | Compose runter, Artefakte (Test-Reports, Logcat) sichern | — |

Emulator-AVD wie der lokale `eink_test`: Boox-Geometrie 1264×1680@300dpi, damit CI-UI-Tests die
reale Hardware-Proportion treffen.

## 9. Szenario-Katalog (A–H, 34 Szenarien)

Marker: `[UI]` / `[Seam]` Ebene · `[pending]` Feature existiert noch nicht.
„bewacht" = die Big-Picture-Invariante, die der Test absichert.

### A — Quellen-Verbindung & Multi-Source (Naht A)
- **A1** Verbinde 1 Server → Bibliothek erscheint `[UI]`
- **A2** n unterschiedliche Server (A+B) → `all()` aggregiert beide, jedes Werk korrekte `sourceId` `[Seam]` — bewacht Aggregation
- **A3** n „gleiche" Server (selbe baseUrl/key 2× ODER Spiegel-C) → `SourceId`-Stabilität/Dedup `[Seam]` — bewacht deterministische SourceId
- **A4** Server entfernen → Werke fallen auf `StubSource`, Bibliothek bricht nie `[Seam+UI]` — bewacht StubSource-Invariante
- **A5** Gemischte Arten live: Komga-REST + OPDS → beide browsebar, Lesen je über `openPage`/`downloadFile` `[Seam]` (= `MixedSourcesLiveTest`, ausgebaut)
- **A6** Lackmustest: ein Feature läuft mit Stub/OPDS **ohne Code-Änderung** `[Seam]`

### B — Werk-Auflösung pro Quelle
- **B7** Werk der **zweiten** Quelle wird über `get(sourceId)` aufgelöst (nicht `current()`); sourceId durch Navigation gefädelt `[UI/Seam]` — bewacht multi-source pro-Werk
- **B8** Cover/Seiten durch die Naht (`SourcePageFetcher`/`CoverFetcher`), keine direkte URL/Auth `[Seam]`

### C — Reader-Dispatch (Viewer-Auflösung deterministisch)
- **C9** Manga-getaggt → Paged/Comic-Reader `[UI]`
- **C10** Webtoon-getaggt → Webtoon-Reader `[UI]`
- **C11** Novel/EPUB → Novel-Reader (Reflow) `[UI]`
- **C12** `contentTypeOverride` schlägt `Shelf.contentType` `[Seam]` — bewacht „kein Auto-Erkennen"
- **C13** In-Reader-Toggle paged⟷webtoon teilt **einen** `RefreshScheduler`/`currentPage` `[Seam]`

### D — Sammlungen
- **D14** Sammlung anlegen + Werk hinzufügen `[UI]`
- **D15** Sammlung aus n Quellen (Werke aus A + B in einer Sammlung) `[UI/Seam]` — bewacht cross-source
- **D16** Sammlung mit Werk gelöschter Quelle → Stub-Eintrag, kein Crash `[Seam]`
- **D17** Collections-Push/Pull-Sync über n Server (server-seitige Komga-Collections, pro Quelle) `[Seam]` — bewacht multi-source-Sync; siehe `2026-06-09-user-collections-server-sync-design.md`

### E — Lesefortschritt / Sync (offline-first)
- **E18** Fortschritt lokal (`dirty`) → Sync-Queue → an die richtige Quelle gepusht `[Seam]`
- **E19** Offline lesen → später syncen `[Seam]`
- **E20** Werk aus B synct zu B, nicht A `[Seam]` — bewacht multi-source-Sync

### F — Download / Offline
- **F21** `downloadFile` mit Progress → offline lesbar `[Seam]` (= `DownloadInstrumentedTest`, ausgebaut)

### G — Geräteklasse / E-Ink (orthogonale Achsen)
- **G22** E-Ink-Modus: keine Bewegung, Akzent monochrom-schwarz (auch Kaleido) `[Seam/UI]` — bewacht device-class
  - *Status (Plan 3):* die pure Logik `displayBehaviorFor(mode, capabilities)` ist durch den Domain-Unit-Test `DisplayBehaviorTest` (mono/kaleido/lcd) **vollständig abgedeckt** — kein Integrations-Duplikat. Sichtbare UI-Wirkung (Bewegung/Akzent im Compose-Baum) → UI-Set (Plan 4).
- **G23** Device-managed Refresh default → `fullRefresh` No-Op, Fast-Mode an `[Seam]`
  - *Status (Plan 3):* `RefreshScheduler` ist pur + eigen-unit-getestet; der `OnyxRefresher.deviceManaged`-No-Op ist gerätenah (Boox-SDK), auf Nicht-Boox-HW nicht sinnvoll instrumentierbar — kein eigener Integrationstest.

### H — Plugins & modulare UI
System grösstenteils noch nicht gebaut → vorformuliert, `[pending]` bis Feature existiert.
**Querschnitt-Garantie für ALLE H-Tests:** nicht „lädt/registriert", sondern **„ist integriert"** —
die geladene Sache ist real in der UI wirksam/sichtbar (Quelle in Bibliothek, Preset filtert echt,
Slot rendert Pack-Inhalt, Theme-Token an den Call-Sites wirksam).

*Color-Presets (c) — teils gebaut (`ColorPresetImporter`):*
- **H1** Nur Farb-Preset laden (JSON→`ColorPresetSpec`→DB) → in Auswahl **und** Filter real angewandt `[UI]`
- **H2** Preset löschbar, Built-ins nicht `[Seam]`

*Settings-Pack:*
- **H3** Komplette Settings laden → alle Settings-Blöcke vom Pack bestückt `[UI/pending]`
- **H4** Nur Teil-Settings → restliche Blöcke fallen auf Default `[UI/pending]` — bewacht Slot-Fallback

*Source-Plugin (a):*
- **H5** Server-Plugin laden → `SourcePlugin`→`BrowsableSource`→`register` → Werke browsebar **und in Bibliothek sichtbar** `[Seam+UI/pending]`
- **H6** Plugin-Quelle deinstallieren → Werke fallen auf `StubSource` `[Seam/pending]`

*UI-Erweiterung / Slots (b, deklarativ):*
- **H7** UI-Pack füllt **Subset** der Slots (nur header) → Rest Default, kein visueller Bruch, E-Ink-Invarianten host-erzwungen `[UI/pending]`
- **H8** UI-Pack ersetzt **alle** Chrome-Slots = komplett neue Oberfläche (nur Smartphone) → Reader-Engines (Core) unverändert `[UI/pending]` — bewacht Core-bleibt/Chrome-austauschbar
- **H9** Deklaratives Pack kann **keine** E-Ink-Invariante brechen (will Animation/Akzent → Host gated weg) `[UI/pending]` — bewacht host-enforced motion/accent

*Theme-Packs (gebaut: `UiPack`):*
- **H10** Voller `UiPack` pro Geräteklasse (LCD Indigo / Kaleido gedämpft / mono S/W) via `packFor(behavior)`, im Host angewandt `[Seam/UI]`
- **H11** Nur Farbpalette (Token-only, Smartphone und/oder Tablet) → ColorScheme ersetzt, Layout/Slots unverändert `[UI/pending]` — bewacht „Theme zuerst, Layout danach"

## 9b. Umsetzungsstand (2026-06-10)

**Lokal grün, 23 Integrationstests** (`com.komgareader.app.ci.*`, Emulator gegen Live-CI-Komga):
- **Seam (17):** A1seam/A2/A3a/A3b/A4 · A5/A6 (Komga+OPDS) · B7/B8 · C9–C12 (Resolver auf echten Metadaten) · E-local/E18/E20 · F21.
- **UI (6):** Smoke · A1 (Add-Server übers UI) · A4 (Remove→Leerzustand) · C9 (Manga→`reader_paged`) · C11 (Novel→`reader_novel`) · D14 (Sammlung anlegen).
- **G22/G23:** durch Domain-Unit-Tests (`DisplayBehaviorTest`/`RefreshScheduler`) abgedeckt — kein Integrations-Duplikat; UI-Akzent/Bewegung nicht robust UI-assertbar.
- **Zurückgestellt:** C10 (Webtoon-UI, Logik seam-grün) · Block D17 (Sammlungen-Push/Pull, Feature in Arbeit) · Block H (Plugins, `[pending]`).
- **Noch offen:** CI-Pipeline (`.gitlab-ci.yml` + Runner-`devices=["/dev/kvm"]`).

Pläne: `plans/2026-06-10-integration-{fixtures-orchestration, harness-seam-AB, seam-CEFG, ui-hilt-infra, ui-catalog}.md`.

## 10. Neue/berührte Dateien (Übersicht)

- `tools/ci-fixtures/` — `docker-compose.yml`, `seed.sh`, `freeze-from-nas.sh`, `manifest.json`,
  `PROVENANCE.md`, `content/` (Tier-1-Werke)
- `app/src/androidTest/.../ci/` — `CiKomga`, `CiFixtures`, Base-Rules
- Test-Klassen pro Block A–H; bestehende (`MixedSourcesLiveTest`, `DownloadInstrumentedTest`,
  `MultiServerPersistenceTest`, `LibraryFlowInstrumentedTest`, `ReaderFlowInstrumentedTest`)
  eingegliedert/ausgebaut statt dupliziert
- `.gitlab-ci.yml`
- Prereq: `KeystoreCredentialStore` (injizierbarer Prefs-Name) + Aufrufer

## 11. Offene Punkte / Reihenfolge

1. Deferred: Web-Suche CC/PD-Tier-1-Werke + Lizenzen in PROVENANCE.
2. H-Block bleibt `[pending]`, bis Plugin-/modulare-UI-System existiert (Phase 4 / ui-modularity).
3. D17 hängt am Collections-Push/Pull-Feature (parallel in Arbeit) — Test darf vorlaufen.
4. Implementierungs-Reihenfolge (für den Plan): Prereqs (§7) → Harness (§6) → Orchestrierung (§5,
   Tier-1-Fixtures) → Seam-Block A/B/E/F → UI-Block A/C/D → G → Tier-2-Smoke → CI (§8) → H (`[pending]`).
