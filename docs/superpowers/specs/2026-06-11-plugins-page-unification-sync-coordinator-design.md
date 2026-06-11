# Plugins-Seite vereinheitlichen + zentraler SyncCoordinator

**Datum:** 2026-06-11
**Status:** Design (genehmigt, vor Implementierungsplan)

## Problem

Zwei Beschwerden, ein Schnitt:

1. **Geteilte Plugins-UI.** Plugins leben heute auf zwei Flächen: dem Plugins-Tab
   (`PluginsScreen`/`PluginsViewModel` — installierte Quellen + Color-Presets verwalten)
   und einer separaten, gepushten Seite „Plugins entdecken" (`RepoBrowserScreen`/
   `RepoBrowserViewModel` — Repo-Index browsen + installieren). Der Wechsel über einen
   „Plugins entdecken"-Button ist überflüssig; alles gehört auf **eine** Seite.

2. **Verstreute Sync-Trigger.** Jedes ViewModel triggert seinen Sync selbst und einzeln:
   - `CollectionsViewModel.syncOnceOnEnter()` / `syncOnTabOpen()` → `CollectionSyncManager.fullSync()`
   - `SettingsViewModel.saveServer()` → `CollectionSyncManager.pullOnlySync()`
   - `PluginsViewModel.addPluginSource()` → `CollectionSyncManager.pullOnlySync()`
   - `PluginsViewModel.refresh()` (onResume) → `PluginHost.discoverPlugins()` / `discoverColorPresetPlugins()` (isoliert, keine Koordination)
   - `LibraryViewModel` lädt reaktiv über `ActiveSource.all()` (refreshTrigger)

   Die Sync-**Entscheidung** („was synct wann") ist über den Code verteilt statt an einer
   Stelle. Sync betrifft **Sammlungen, Werke/Libs von den Servern UND Plugin-Discovery** —
   alles soll der Koordinator besitzen.

## Ziele

- **Ein** Plugins-Tab vereint Verwaltung + Entdeckung. Keine separate Route, kein
  „Plugins entdecken"-Button.
- **Ein** zentraler `SyncCoordinator` besitzt die Sync-Entscheidung. Call-Sites melden nur
  noch das *Ereignis* („Server hinzugefügt", „App gestartet", „manueller Reload"), nicht die
  konkrete Sync-Aktion.
- App lädt am Start **einmalig** (akkuschonend auf E-Ink). Netz-Fetch nicht bei jedem onResume.
- Bestehende Naht-Invarianten unberührt: quellen-agnostisch (kein Komga-Wissen im Koordinator),
  E-Ink-Designsprache, geteilte `Eink*`-Komponenten.

## Nicht-Ziele (YAGNI)

- **Kein** Repo-Index-Cache (Room). Ohne Netz ist kein Download möglich; offline werden ohnehin
  nur lokale/installierte Plugins angezeigt. Repo-Einträge zu cachen bringt nichts.
- Keine Änderung an Install-/Config-/Uninstall-Flows, Fingerprint-Verify, TOFU-Pinning,
  `planPluginPrune`, ABI-Gate — alles unverändert wiederverwendet.
- Keine Worker/Background-Sync-Infrastruktur (WorkManager o. Ä.).

---

## Teil A — `SyncCoordinator` (Sync-Vereinheitlichung)

Neue Klasse `SyncCoordinator` (`app/data`, `@Singleton`). Die **eine** Stelle, an der die
Sync-Entscheidungen leben. Besitzt drei Sync-Domänen: Sammlungen, Werke/Libs von Servern,
Plugin-Discovery.

### Verträge (Ereignis rein, Sync-Entscheidung drin)

| Methode | Wann gerufen | Tut |
|---|---|---|
| `onAppStart()` | **einmalig** pro App-Launch (aus `MainActivity`, nicht onResume) | App-Start-Latch prüfen (max. 1×); lokaler Plugin-Scan; **einmaliger** Repo-Live-Fetch; Collection-`fullSync()` (E-Ink-gegatet); Library-Refresh-Trigger |
| `onServerAdded()` | nach Server-Add/Update (Settings, Plugin-Quelle hinzufügen) | `pullOnlySync()` |
| `onServerRemoved()` | nach Server-Entfernen | bestehende Cleanup-Pfade (`CollectionRepository.removeSource` läuft schon in den VMs; Koordinator bündelt nur den Sync-Anteil, falls vorhanden) |
| `onManualReload()` | Reload-Button im Plugins-Tab | Repo-Live-Fetch + lokaler Plugin-Scan |
| `onCollectionsTabEntered()` | Collections-Tab sichtbar (heute `syncOnceOnEnter`/`syncOnTabOpen`) | `fullSync()` bzw. gegateter Re-Sync je `aggressiveSyncAllowed` |

### Gating

E-Ink-Disziplin bleibt: `aggressiveSyncAllowed(displayMode)` (`SyncGating.kt`) entscheidet wie
heute. Auf E-Ink: aggressiver Sync nur an App-Start / manuell, nicht bei jedem Tab-Open. Die
**Logik** zieht in den Koordinator, das Gate-Helfer bleibt rein/unit-getestet.

### App-Start-Latch

Einmal-pro-Launch über ein Flag im Koordinator (Singleton lebt App-weit). `onAppStart()` aus
`MainActivity` (Lifecycle `ON_CREATE` / einmaliger `LaunchedEffect(Unit)`), **nicht** onResume.
Begründung: akkuschonend auf E-Ink, deckt sich mit der Nutzer-Entscheidung „einmal am Anfang laden".

### Agnostik

Der Koordinator nutzt ausschließlich `CollectionSyncManager`, `ActiveSource`, `PluginHost` und
das Repo-Fetch der Plugins — **kein** `KomgaSource`/`*Provider`-Wissen. (`source-agnostic-integration.md`.)

### Plugin-Discovery: lokaler Scan vs. Netz-Fetch (Nuance)

Zwei getrennte Dinge:

- **Lokaler Scan** (`PluginHost.discoverPlugins()` / `discoverColorPresetPlugins()`, **kein Netz**,
  billig): läuft bei App-Start **und** weiterhin onResume des Plugins-Tabs — damit OS-Uninstall-/
  Install-Cleanup (`planPluginPrune`) sofort greift, wenn der Nutzer aus dem OS-Dialog zurückkommt.
- **Netz-Repo-Fetch** (Repo-Index laden, mergen, Install-State berechnen): nur bei **App-Start 1×**
  + manuellem **Reload**. Nicht onResume.

---

## Teil B — Eine Plugins-Seite

### Entfällt

- `RepoBrowserScreen.kt`, `RepoBrowserViewModel.kt`
- Route `"plugin-repos"` (`MainActivity` NavHost) + Callback `onOpenRepoBrowser`
- „Plugins entdecken"/„Repo-Browser öffnen"-Button in `PluginsScreen`

### TopBar (HomeScreen, `TAB_PLUGINS`)

Analog zum Library-Tab-Muster (geteilte Komponenten, nichts roh):

- `EinkSearchBar` zentral. `leading` = **Typ-Filter-Chip** (Alle / Quellen / Presets) — gleicher
  Slot/Stil wie die Library-Filter-Chips.
- Rechts zwei Action-Icons (`AppIcons`, 24 dp, nur funktionierende Aktionen):
  - **⚙ Repo-Settings** → öffnet das bestehende `RepoManagementModal` (Repos hinzufügen/entfernen,
    offizielles Repo togglen).
  - **⟳ Reload** → `SyncCoordinator.onManualReload()`.

### Liste (`PluginsScreen`)

Gefiltert nach aktivem Typ-Chip **und** Suchtext. Innerhalb des Filters:

```
[ Installierte ]   Quellen + Presets (gefiltert), immer oben
———— Divider ————  (nur wenn beide Abschnitte Inhalt haben)
[ Entdeckte ]      aus Repos (gefiltert); offline/leer → nur dieser Abschnitt leer
```

- Suchtext filtert **beide** Abschnitte; installierte bleiben oben.
- Der **Divider** erscheint **nur, wenn der installierte Abschnitt nach Filterung nicht leer ist**
  (und entdeckte vorhanden sind) — keine schwebende Linie über leerem „Installierte"-Bereich.
  Unabhängig vom Typ-Chip, aber abhängig vom tatsächlichen Inhalt.
- Offline / kein Repo erreichbar: installierte bleiben sichtbar, nur „Entdeckte" ist leer
  (kein harter Fehler).
- E-Ink-Layout-Stabilität: feste Rahmen-Platzhalter, kein Loading→Content-Höhensprung.

### Wiederverwendet (unverändert)

Install-Flow + Fingerprint-Verify (`PluginInstaller`), Config-/TOFU-Flow für Quellen,
Preset-Import/-Entfernen, Uninstall via OS-Intent, `planPluginPrune`, `RepoManagementModal`,
Install-State-Anzeige (`NOT_INSTALLED`/`INSTALLED`/`UPDATE_AVAILABLE`) + ABI-Gate.

---

## Teil C — Vereinter `PluginsViewModel`

Merge `PluginsViewModel` + `RepoBrowserViewModel` → ein `PluginsViewModel`.

### State

| Feld | Quelle |
|---|---|
| `installedSources: List<DiscoveredPlugin>` | `PluginHost.discoverPlugins()` |
| `installedPresets: List<DiscoveredPresetPlugin>` | `PluginHost.discoverColorPresetPlugins()` |
| `profiles: List<ColorProfile>` | `ColorProfileRepository` (Import-Status-Abgleich) |
| `discovered: List<BrowserRow>` | Repo-Fetch (`PluginRepoClient` + `parseRepoIndex`/`mergeRepoEntries`) |
| `query: String` | Suchleiste |
| `typeFilter: PluginTypeFilter` (ALLE/QUELLEN/PRESETS) | Filter-Chip |
| `loading: Boolean`, `error: String?` | Repo-Fetch |
| `repos: List<RepoSource>` | `RepoStore.observeActive()` |

### Aktionen

`addPluginSource` · `importPreset` · `removeImportedProfile` · `installFromRepo` (=
`PluginInstaller.verifyAndInstall`) · `addRepo`/`removeRepo`/`setOfficialEnabled` · lokaler
`rescan()` (onResume, kein Netz). Repo-Fetch + lokaler Scan werden zusätzlich vom
`SyncCoordinator` getrieben (App-Start / Reload).

### Reine Funktionen (in `:data`, unit-getestet)

Bestehend: `parseRepoIndex`, `mergeRepoEntries`, `installState`, `planPluginPrune`,
`fingerprintMatches`. **Neu:** reine Filterfunktion `visibleRows(installed, discovered, query,
typeFilter) → (installedVisible, discoveredVisible)` — Chip + Query → sichtbare Abschnitte,
ohne I/O, voll testbar.

---

## Datenfluss

```
MainActivity ──onAppStart()──▶ SyncCoordinator
                                  ├─ lokaler Plugin-Scan ─▶ PluginsViewModel.installed*
                                  ├─ Repo-Live-Fetch 1× ──▶ PluginsViewModel.discovered
                                  ├─ CollectionSyncManager.fullSync() (E-Ink-gegatet)
                                  └─ Library-Refresh-Trigger ─▶ LibraryViewModel

Plugins-Tab:
  Filter-Chip / Suche ─▶ visibleRows(...) ─▶ [Installierte] ─Divider─ [Entdeckte]
  ⚙ ─▶ RepoManagementModal      ⟳ ─▶ SyncCoordinator.onManualReload()
  onResume ─▶ rescan() (lokal, kein Netz) ─▶ planPluginPrune
```

## Fehlerbehandlung

- Repo-Fetch-Fehler / offline: `error` gesetzt **oder** still; „Entdeckte" leer; installierte bleiben.
  Kein Crash, kein leerer Gesamt-Screen.
- Fingerprint-Mismatch beim Install: harter Abbruch, APK gelöscht (bestehend, unverändert).
- App-Start-Sync-Fehler einzelner Domänen isoliert (`runCatching` je Domäne) — eine fehlschlagende
  Domäne reißt die anderen nicht mit.

## Testing

- **Unit (pure, `:data`/`domain`):** neue `visibleRows`-Filterfunktion (gesetzt/leer/Chip-Varianten);
  bestehende Parse/Merge/Prune/Fingerprint-Tests bleiben.
- **Unit (Koordinator-Logik):** Gating-Entscheidung je `displayMode` (E-Ink vs. LCD), App-Start-Latch
  (nur 1× pro Launch), Ereignis→Aktion-Zuordnung mit gemockten Managern.
- **E2E (Emulator `eink_test` 1264×1680@300, lokale Test-Komga + Docker-Kavita):** eine Plugins-Seite
  zeigt installierte oben / entdeckte unten mit Divider; Filter-Chip + Suche filtern; Reload holt Repo;
  Install aus Repo (Fingerprint-Verify) grün; App-Start lädt einmalig; „Plugins entdecken"-Button weg.

## Betroffene Dateien (Richtwert)

**Neu:** `app/data/SyncCoordinator.kt` (+ Test); reine `visibleRows` in `:data` (+ Test).
**Geändert:** `PluginsScreen.kt`, `PluginsViewModel.kt` (Merge), `HomeScreen.kt` (TopBar TAB_PLUGINS),
`MainActivity.kt` (Route + onAppStart), `CollectionsViewModel.kt` / `SettingsViewModel.kt`
(Sync-Trigger → Koordinator). **Gelöscht:** `RepoBrowserScreen.kt`, `RepoBrowserViewModel.kt`.

## Doku nachziehen (docs-match-code — Pflicht, im selben Commit)

- **`komga-collection-server-sync`-Skill** beschreibt heute die **verstreuten** Sync-Einstiegspunkte
  (`syncOnceOnEnter`/`syncOnTabOpen`, Server-Connect → `pullOnlySync`, Plugin-Add → `pullOnlySync`).
  Mit dem `SyncCoordinator` ist das überholt: der Skill muss den Koordinator als **die** Sync-Naht
  beschreiben (Ereignis→Aktion-Verträge, App-Start-Latch, Gating darin) und die alten Einzel-Trigger
  als „rufen jetzt den Koordinator" markieren. Sonst führt der Skill den nächsten Leser in die Irre.
- **`architecture-seams.md`** (oder die Sync-relevante Stelle) auf den Koordinator-Ist-Stand nachziehen.
- Memory `collection-sync` / projekt-relevante Memos prüfen und angleichen.

## Bezug

`architecture-seams.md` (Naht A, agnostisch), `source-agnostic-integration.md`,
`shared-structure-before-variants.md` (Gemeinsames vor Variante — hier: ein VM/eine Seite statt zwei,
ein Sync-Koordinator statt N Trigger), `eink-design-language.md` + `komga-eink-ui-polish`
(TopBar/Liste/Divider/Chips), `roadmap-and-invariants.md` (Phase 4 Plugin-Loader),
`komga-collection-server-sync`-Skill (muss nachgezogen werden, s. o.).
