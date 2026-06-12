# Architektur: Die zwei Nähte

Das ganze Projekt ruht auf **zwei tragenden Nähten**, die die gesamte Variabilität kapseln.
Über den Nähten ist alles quellen- und geräte-agnostisch. Eine neue Quelle oder ein neues
E-Ink-Gerät = **neue Implementierung hinter dem Interface, kein Kern-Umbau**. Das ist die
zentrale Design-Entscheidung (Spec §3) — sie darf nie aufgeweicht werden.

```
┌─ UI (Compose, app) — quellen-/geräte-agnostisch ───────────┐
└──────────────── ↓ ──────────────────────── ↓ ─────────────┘
┌─ Domain (kennt weder UI noch Daten noch Quelle) ───────────┐
│  Modelle · UseCases · Repository-Interfaces · ViewerType   │
└──────── ↓ NAHT A: Quellen ──────────── ↓ NAHT B: Engine ───┘
┌─ MediaSource ───────────┐   ┌─ Document (Render) ───────────┐
│ KomgaSource (REST)      │   │ MuPDF (C++/JNI) → Bitmap      │
│ OpdsSource              │   │ OnyxRefresher (E-Ink-Refresh) │
│ [LocalSource, Plugins…] │   │ EinkController (Onyx ⟂ No-Op) │
│ SourceManager + Stub     │  │ Compose-Reader-Screens        │
└─────────────────────────┘   └───────────────────────────────┘
```

> **Soll vs. Ist — diese Regel trennt beides.** Verbindliche Regeln dürfen keine
> nicht-existierenden Typen als real darstellen (sonst baut der nächste — Mensch
> oder Agent — gegen ein Phantom, siehe `docs-match-code` in der Big-Picture-Doku).
> Das Naht-**Design** (unten) ist verbindlich; wo der **Ist-Stand** abweicht, steht
> es explizit dabei. Vor dem Bauen den Ist-Stand per `grep` verifizieren, nicht der
> Vokabel der Doku vertrauen.

## Naht A — Quellen (`source-api/…/source/MediaSource.kt`)

- Jede Backend-Verbindung implementiert `MediaSource` (+ `BrowsableSource` zum Lesen,
  `SyncingSource` für Fortschritts-Sync). Stabile, deterministische `id` (Hash aus name/typ/config).
- `StubSource` hält Titel/ID, wenn die echte Quelle fehlt — die Bibliothek bricht nie.
- **Regel:** UI und `domain` kennen **nur** `MediaSource`-Interfaces, **nie** `KomgaSource`/`OpdsSource`
  konkret. Wer in `app` oder `domain` `import …source.komga…` schreibt, hat die Naht verletzt.
- Quellen-übergreifende DB (`data`): jeder Datensatz trägt `sourceId`. `LocalSource` = id 0.
- Runtime-Plugin-Loader (Phase 4) hängt sich genau hier ein — das Interface ist dafür schon stabil,
  also keine quellenspezifischen Annahmen ins Interface backen.
- **Ist-Stand (2026-06-08): die Integrationsseite ist verdrahtet.** `SourceManager` wird in `app`
  über `SourceRegistration` aus der `ServerConfig` befüllt; `ActiveSource` (app/data) ist der
  agnostische Resolver für alle ViewModels. Bilder/Seiten **und** Cover fließen über die Naht
  (Coil `SourcePageFetcher`/`SourceCoverFetcher` → `BrowsableSource.openPage`/`coverBytes`); es gibt
  **kein** `AuthHeaders` mehr und keine quellen-spezifische URL/Auth in der UI. `BrowsableSource`
  trägt jetzt `downloadFile(…, onProgress)`, `seriesIdOf`, `coverBytes`. `KomgaSource`-Typen leben
  nur noch in `ActiveSource`/`SourceRegistration`/`KomgaSourceProvider`.
- **Multi-Source verdrahtet (2026-06-09, #7 P2/P3):** N Quellen gleichzeitig, gemischt.
  `ActiveSource` bietet `all()` (Aggregation) + `get(sourceId)` (genau die Quelle eines Werks);
  `current()` bleibt nur Übergangs-API. Die `sourceId` jedes Werks wird **durch die Navigation
  gefädelt** (`series/{seriesId}/{sourceId}`, `reader/{bookId}/{sourceId}/…`), und alle Consumer
  lösen **pro Werk** über `get(item.sourceId)` auf statt „die erste/aktive" (`LibraryViewModel`
  aggregiert über `all()`; `SeriesDetail`/`Reader`/`Novel`/`GroupBrowse`/`Groups` via `get`).
  Settings verwaltet eine **Server-Liste** (Hinzufügen + Einzel-Entfernen). Emulator-verifiziert.
  **Ist (2026-06-09): OPDS als zweite Live-Quelle live gemischt verifiziert.** Komga-REST + OPDS
  gleichzeitig registriert, `ActiveSource.all()` liefert beide, OPDS-Werk via `downloadFile`
  geladen — bewiesen durch `MixedSourcesLiveTest` auf dem Emulator. `OpdsSource` trägt jetzt
  Basic-Auth-Credentials (Komga OPDS akzeptiert keinen `X-API-Key`-Header). Details/Integrationsregel:
  `source-agnostic-integration.md`.
- **Plugin-Loader gebaut (Ist, 2026-06-11):** Der Runtime-Plugin-Mechanismus für Quellen-Plugins (Phase 4,
  Typ a) ist real. `plugin-api` (pure JVM, `com.komgareader:plugin-api:0.1.0`) enthält den ABI-Vertrag:
  `SourcePlugin` (Interface: `metadata`, `configSchema()`, `create(config): BrowsableSource`),
  `PluginMetadata`, `ConfigSchema`/`ConfigField`/`FieldType{TEXT,SECRET,URL,BOOL}`, `PluginAbi`
  (VERSION=1, MIN_SUPPORTED=1) und `ColorPresetSpec`. Es macht `api(project(":source-api"))`, re-exportiert
  damit alle Naht-A-Typen; `domain` und `source-api` werden ebenfalls als 0.1.0 nach mavenLocal publiziert,
  damit Plugins die transitive Abhängigkeit auflösen können.
  `plugin-host` (Android-Lib) enthält: `AbiGate` (reines 2-Int-Gate), `PluginSignature`/`PluginConfigHash`
  (reine, unit-testbare Helfer), `PluginManifestKeys` (`ENTRY_CLASS`=`com.komgareader.plugin.SOURCE`,
  `ABI_VERSION`=`com.komgareader.plugin.ABI_VERSION`), `DiscoveredPlugin` (trägt `entryClass`) und
  `PluginHost(context)` mit `discoverPlugins()`/`sourceFor(pkg, entry, expectedSignature, config)`.
  Laden erfolgt über `PathClassLoader(appInfo.sourceDir, nativeLibDir, hostClassLoader)` (Mihon-Modell) —
  kein `DexClassLoader` heruntergeladener `.dex`, geladen wird nur das OS-installierte APK. **Sicherheitsmodell:
  TOFU** (Trust-on-First-Use): das Trust-Gate ist der Cert-SHA-256-Pin in `PluginHost.sourceFor` — ein Plugin
  wird nur instanziiert, wenn die aktuelle Paket-Signatur dem beim Erst-Hinzufügen bestätigten Pin entspricht.
  **Drei im E2E (2026-06-11) entdeckte+behobene Naht-Härtungen** (Lackmus „funktioniert es auf echtem Gerät?"):
  (1) **Paket-Visibility** ab API 30 — der Host braucht `QUERY_ALL_PACKAGES` (app-Manifest), sonst sieht
  `getInstalledPackages` das Plugin-APK gar nicht; bewusst gewählt statt intent-`<queries>`, um den Plugin-Vertrag
  minimal zu halten (Plugin deklariert nur Metadata, keine Discovery-Komponente). (2) **Multidex-Foreign-Load** —
  `createPackageContext` lädt für ein Fremdpaket nur die primäre `classes.dex`; die Entry-Klasse großer Plugins
  (Retrofit/serialization → Multidex) liegt in `classesN.dex` → `ClassNotFoundException`. `PathClassLoader` über
  `sourceDir` lädt alle dex (deshalb der Wechsel weg von `createPackageContext`). (3) **ABI-Metadata** robust als
  Int **oder** String lesen (`aapt` typisiert `android:value` uneinheitlich).
  **Wiring:** `ServerConfig.extras: Map<String,String>` (Domain, Ist) trägt Plugin-Config-Werte +
  reservierte Wiring-Keys `__pkg`/`__entry`/`__sig`; Keystore-verschlüsselt in Room (Spalten
  `extrasCiphertext`/`extrasIv`, Migration 14→15). `SourceRegistration.build()` hat einen
  `SourceKind.PLUGIN`-Branch: liest `__pkg`/`__entry`/`__sig`, filtert `__`-Keys heraus, delegiert an
  `pluginHost.sourceFor(...)`. Konkrete Quellen-Typen bleiben ausschließlich in der Wiring-Schicht
  (`SourceRegistration`, `PluginHost` via Hilt in `AppModule`).
  **Erstes APK-Plugin:** Kavita-Quelle (`plugin/komga-kavita-source/`, separates Git-Repo, gitignored)
  implementiert `BrowsableSource`+`SyncingSource` und linkt `plugin-api` als `compileOnly`. **E2E-verifiziert
  (2026-06-11):** `KavitaPluginLiveTest` auf dem Emulator gegen Docker-Kavita ([[local-test-kavita]]) — ganze
  Kette Entdeckung→ABI→TOFU-Signatur→PathClassLoader-Load→`browse`/`books`/`pages`/`openPage` grün, plus
  TOFU-Negativtest (falscher Pin lädt nicht). **Plugin-SDK (Ist, 2026-06-11):** ein **einzelnes geshadetes**
  Modul `:plugin-sdk` (`com.komgareader:plugin-sdk:0.1.0`, Shadow ohne Relocation) bündelt
  plugin-api+source-api+domain (nur `com.komgareader.**`, keine Fremd-Libs, saubere POM) — Plugins linken **nur
  das** `compileOnly`. Die separaten Publishes der drei Module sind entfallen. Kavita-Plugin nutzt es; E2E grün.
  Settings-Seite: Settings „Server hinzufügen" listet entdeckte Plugins, zeigt TOFU-Trust-Dialog
  (Fingerprint-Anzeige), dann generisches `PluginConfigForm` aus dem `ConfigSchema`.
- **Data-only Discovery generalisiert (Ist, 2026-06-12):** Die data-only-Mechanik ist jetzt
  **kategorisiert**: `PluginCategory{COLOR_PRESET,READER_PRESET,LANGUAGE}` (plugin-api, ABI
  `VERSION=2`/`MIN_SUPPORTED=1`, additiv). Manifest-Keys `DATA_CATEGORY`+`DATA_ASSET` (mit
  Legacy-Alias `COLOR_PRESETS`). `PluginHost.discoverDataPlugins(category)` ist die generische
  Discovery (reiner `resolveDataPluginManifest`-Helfer); `discoverColorPresetPlugins()` ist nur
  noch ein dünner Wrapper darüber (+ `parsePresetSpecs`). Reader-Preset-/Sprach-Plugins (Spec 2)
  hängen sich als neue Kategorien ein, ohne Discovery-Umbau. **Distribution:** kein `/plugins/`
  im App-Repo — alle Plugins (gebaute APKs + `repo.json`-Index) leben im separaten
  Distributions-Repo `Gabriel-Graf/KomgaReaderPlugins` (`PluginRepoDefaults.OFFICIAL_URL`); der
  Repo-Browser lädt/verifiziert/installiert von dort. Data-only-APKs sind debug-signiert (Fingerprint
  im Index).
- **Reader-Preset- + Sprach-Plugins (Ist, 2026-06-12, Spec 2):** Zwei neue data-only Kategorien über
  `discoverDataPlugins`: `READER_PRESET` (benannte Teil-Snapshots der Reader-Settings → `parseReaderPresetSpecs`
  → `applyReaderPreset` mutiert nur gesetzte Felder, **kein** Room-Table) und `LANGUAGE` (Runtime-i18n-Override
  `MapBackedStrings` mit EN-Fallback, `language`-Setting hält beliebigen Code, `resolveStrings` in MainActivity).
  Quellen-Plugins sind jetzt auch über den „Server hinzufügen"-Selektor („Plugin"-Segment, geteilter
  `AddPluginSourceModals`-Flow) hinzufügbar, nicht nur im Plugins-Tab. Deliverables liegen im
  Distributions-Repo `KomgaReaderPlugins` (debug-signierte APKs + `repo.json`-Einträge `type:
  language|reader_preset`, `abiVersion:2`): `komga-lang-{es,fr,it}` (LANGUAGE) +
  `komga-reader-preset-eink` (READER_PRESET).

- **Collections-Sync bidirektional (Ist, 2026-06-10):** Sammlungen synchronisieren jetzt **push UND
  pull**. Der reine, pur-getestete `planCollectionSync` (`domain/usecase/CollectionSyncPlan.kt`)
  entscheidet pro (Sammlung, Quelle)-Link per **Last-Write-Wins (UTC)**: Server-Sammlungen, die lokal
  fehlen, werden **entdeckt** und lokal angelegt (Erstverbindung); geänderte mergen nach neuerem
  `updatedAt`; am Server gelöschte (früher synchrone) werden als `vanished` zurückgegeben und über ein
  `EinkModal` mit Nutzer-Bestätigung lokal nachgezogen. Die Shell `CollectionSyncManager.fullSync()`
  (`app/data`) listet alle Quellen agnostisch über `ActiveSource.allCollectionSources()`, je `kind`
  (SERIES/BOOK). Trigger geräteklassen-gegated (`aggressiveSyncAllowed`): E-Ink nur an
  App-Start/Tab-Erst-Sicht + manuell, LCD zusätzlich bei jedem Tab-Öffnen. Das alte `refresh()` ist
  entfernt. **Naht-Änderung:** der Server-Sicht-Typ `RemoteCollection` lebt jetzt in `domain/model/`
  (nicht mehr in `source-api`) — `domain` darf nicht auf `source-api` hängen, der pure Planner braucht
  ihn aber; `source-api` (das auf `domain` hängt) referenziert ihn von dort. Zeitstempel: `RemoteCollection.updatedAt`
  + Domain-`CollectionSyncLink.updatedAt`, alles **UTC-Epoch-Millis** (Komga `lastModifiedDate` über
  `parseIsoUtcMillis`, no-offset = UTC). **Server-Entfernen räumt auf** (≠ Vanish-Modal): wird eine
  Verbindung entfernt, löscht `CollectionRepository.removeSource(sourceId)` deren Member/Sync-Links
  und entfernt dadurch leer gewordene, von der Quelle berührte Sammlungen (multi-source-Sammlungen
  behalten die anderen Quellen; fremde leere Sammlungen bleiben). Verdrahtet in
  `SettingsViewModel.removeServer` über `SourceRegistration.sourceIdOf(config)`. Details:
  `source-agnostic-integration.md`, `source-extensibility.md` (Kochrezept Metadatum).
- **SyncCoordinator (Ist, 2026-06-11):** `app/data/SyncCoordinator.kt` (@Singleton) ist die zentrale
  Sync-/Discovery-Naht: bündelt App-Start- (`onAppStart`, latch-geschützt: `fullSync` + lokaler
  Plugin-Scan + 1× Repo-Fetch), Server-Changed- (`onServerChanged` → `pullOnlySync`), Reload-
  (`onManualReload`: Repo-Fetch + Scan) und Tab-Trigger (`onCollectionsTabEntered`, gegated über
  `aggressiveSyncAllowed`). Call-Sites (`MainActivity`, `SettingsViewModel`, `PluginsViewModel`,
  `CollectionsViewModel`) melden nur das Ereignis. `CollectionSyncManager` bleibt der Executor darunter.
  `PluginCatalog` (`app/data/PluginCatalog.kt`, @Singleton) hält den geteilten Plugin-Discovery-Zustand
  (lokaler APK-Scan+Prune, Repo-Fetch, Install). Der Plugin-Repo-Browser-Screen ist entfernt — eine
  vereinte Plugins-Seite zeigt installierte Plugins oben, Divider, darunter die entdeckten aus den Repos
  (Typ-Filter-Chip, ⚙ Repo-Settings, ⟳ Reload).

## Naht B — Render & E-Ink (`render-core`, `eink-onyx`)

- **Render-Naht (Ist):** `Document`/`DocumentFactory` (`domain/render/Document.kt`) ist die
  Render-Naht: **MuPDF** rendert cbz/cbr/pdf **und** EPUB in eine `android.graphics.Bitmap`
  (`MupdfDocument` in `render-core`). Render-Target strikt von der View getrennt.
  Reicht MuPDFs Qualität nicht, klinkt sich eine andere Engine (crengine, Roman-Reflow) hinter
  `Document`/`ReflowableDocument` ein (Phase 4 / `novel-reflow-reader`) — ohne den Rest zu berühren.
- **Geräte-Naht (Ist):** `EinkController` (`domain/eink/EinkController.kt`) kapselt das Gerät:
  `OnyxEinkController` (Boox-SDK, **HW-gated** über `Build.MANUFACTURER`), `NoOpEinkController` als
  Fallback. **Entwicklung crasht nie auf Nicht-Boox-HW.** Trägt `EinkCapabilities` (hasEink/canColor/
  canInvert) — siehe Big-Picture-Doku zur Geräteklassen-Frage.
- **E-Ink-Refresh (Ist, 2026-06-08):** Die Refresh-**Entscheidung** (PARTIAL beim Blättern,
  FULL-Promotion gegen Ghosting / bei bewusstem Bildwechsel) liegt jetzt im geräteunabhängigen,
  pur-getesteten **`RefreshScheduler`** (`domain/eink`, Event-Zählung statt Index-Modulo +
  `mergeRegions`). Die **Ausführung** macht weiter `OnyxRefresher` (`eink-onyx`, gerätenah) +
  `EinkReaderEffect`. Eine Scheduler-Instanz pro Reader-Sitzung, von allen Readern über den
  `Viewer`-Vertrag geteilt. (`triggerGhostClearIfNeeded` ist entfernt.)
  **Update (2026-06-09): `RefreshScheduler` ist `@Deprecated` + standardmäßig wirkungslos.** Die
  Einstellung `deviceManagedRefresh` (Default **an**) überlässt den Voll-Refresh dem Onyx-Gerät —
  `OnyxRefresher.deviceManaged` macht dann `fullRefreshNow`/`fullRefreshIfNeeded` zu No-Ops (der
  Fast-Modus `enterFastMode` bleibt aktiv). Der Scheduler läuft nur noch als Fallback, wenn der
  Toggle (Settings → Reader → „E-Ink-Refresh") ausgeschaltet wird; dann blättern alle Reader
  partial mit periodischer GC-Promotion (auch der Roman-Reader — der erzwang vorher pro Seite FULL).
  Mittelfristig entfernen.
- **Reader / Viewer-Naht (Ist, 2026-06-08):** Es gibt den **`Viewer`**-Vertrag
  (`app/ui/reader/Viewer.kt`) — eine **Compose-Zustands**-Naht (chromeVisible-`StateFlow`,
  `toggleChrome`/`navigateTo`/`onPageSettled`, `refreshScheduler`), **nicht** das alte OO-`bind/
  onButton/teardown` (Compose verwaltet den Lifecycle deklarativ). Alle Reader-VMs
  (`ReaderViewModel`, `ComicReaderViewModel`, `NovelReaderViewModel`) implementieren ihn; das
  geteilte `ReaderScaffold` arbeitet dagegen. Reader bleiben eigene `@Composable`-Screens
  (`PagedReaderScreen`/`WebtoonReaderScreen`/`ComicReaderScreen`/`NovelReaderScreen`), dispatcht
  per `when(ViewerMode)`/`when(ReaderContent)` in `ReaderRoute.kt` — ein 5. Reader/UI-Plugin
  implementiert **`Viewer`** statt einer Parallel-Linie.
- **Geteilte Chrome-Shortcuts (Ist, 2026-06-10):** `ReaderScaffold`/`ReaderChromeOverlay` tragen
  jetzt `onHome`/`onSettings` und rendern oben rechts an **einer** Stelle die geteilten Buttons
  **[Home][Einstellungen]** (in dieser Reihenfolge), **danach** die reader-spezifischen `actions`
  (Modus-Toggle, Novel-TOC/Suche/Typo). Kein Reader baut sie selbst — `ReaderRoute` reicht die
  beiden Callbacks nur durch (`shared-structure-before-variants`). Die Callbacks sind agnostisch:
  `onHome` → `home`-Route (Reader-Stack bis `home` abgeräumt), `onSettings` → neue `settings`-Route,
  die **über** dem Reader gepusht wird und denselben `SettingsScreen` wie der Bibliotheks-Tab hostet
  (DRY, `SettingsRoute.kt`). Session-Skopierung des „Zurück-zum-Reader" kommt automatisch aus dem
  Compose-Back-Stack (Settings liegt über der konkreten Reader-Route → `popBackStack()` landet dort);
  der Settings-**Tab** in `HomeScreen` bleibt unberührt (kein Zurück-zum-Reader).
- **God-VM-Split (Ist, 2026-06-09 — teilweise aufgelöst):** Die webtoon-spezifische *Lade-Logik*
  ist aus `ReaderViewModel` heraus extrahiert: der nahtlose, kapitelübergreifende Strip (Index↔
  Kapitel/Seite, flache Seiten-Liste, globaler Start) entsteht jetzt im **pur-getesteten**
  `buildWebtoonStrip` (`app/ui/reader/WebtoonStripPlanner.kt`, `WebtoonStripPlan`).
  `ReaderViewModel.loadWebtoonStrip` hält nur noch das I/O (`seriesIdOf`/`books`/`pullProgress`)
  + das `SourceImage`-Mapping und delegiert die Planung. paged/webtoon/rendered **bleiben** in
  `ReaderViewModel`, weil der **In-Screen-Toggle `toggleViewerMode` (paged⟷webtoon, comic⟷webtoon)**
  beide Layouts auf **einem** geladenen `ReaderContent` rendert und dabei `_currentPage`, den
  **einen** `RefreshScheduler` (Viewer-Naht: genau eine Instanz pro Sitzung) und `frameStep`
  geteilt teilt. Ein voller Zwei-VM-Split würde diesen Toggle brechen (zweiter Scheduler verboten,
  Scroll-/Seitenposition ginge verloren oder Inhalt müsste auf Toggle neu geladen werden = Lade-
  Sturm + Verhaltensänderung). Der Toggle bleibt daher **bewusst** in `ReaderViewModel` —
  dokumentiert direkt an `toggleViewerMode`. Die genuin webtoon-spezifische Logik ist trotzdem
  raus aus dem God-VM und einzeln getestet (`WebtoonStripPlannerTest`).

- **UI-Slot-Naht / Chrome (Ist, 2026-06-09 — erste Region `header` gebaut; Ist, 2026-06-12 — zweite
  Region `homeHeader`, dritte Region `dialog` + vierte Region `settings` gebaut):** Über den
  Reader-Engines wird das *Chrome*
  (Header/Overlay/Tiles/Nav/Settings/Dialog) regionweise auswechselbar — das „Layout danach"-Stück der
  modularen UI (`big-picture-and-goals.md` → ui-modularity). **Gebaut sind vier Regionen**
  (`app/ui/slots/UiSlots.kt`):
  - **Region `header` (Ist, 2026-06-09):** In-Tree-Vertrag `HeaderSlot`
    (`@Composable (title, onBack?, actions) -> Unit`, spiegelt `StandardTopAppBar`). Call-Sites
    (`SeriesDetailScreen`, `SubPageScaffold`) rendern `LocalResolvedSlots.current.header(...)`.
    Swap-Beweis: `HeaderSlotPreview.kt` (zentrierter Alternativ-Header, nur Debug/Preview).
  - **Region `homeHeader` (Ist, 2026-06-12):** In-Tree-Vertrag `HomeHeaderSlot`
    (`@Composable (state: HomeHeaderState) -> Unit`). Die **Capability-Surface** `HomeHeaderState`
    kapselt Status, Suche (`HomeHeaderSearch`), generischen Filter-Slot (`HomeHeaderFilter`), Menü-Overlay
    und Tab-spezifische Aktionen. Der Host (Core) baut die Surface und besitzt die Logik; das Pack
    **arrangiert** sie — implementiert sie nie neu („UI neu, Kernlogik gleich"). `DefaultHomeHeader`
    (`app/ui/home/HomeHeader.kt`) ist das Default-Layout (Onyx-Look). `HomeScreen` baut die Surface pro
    Tab und ruft `LocalResolvedSlots.current.homeHeader(state)`. Die frühere **„Ausnahme HomeScreen"**
    (direkter `TopAppBar`-Aufruf, nicht über `LocalResolvedSlots` swappable) ist damit **aufgehoben** —
    `HomeScreen` ist vollständig in die Slot-Naht integriert. Swap-Beweis:
    `app/src/debug/kotlin/com/komgareader/app/ui/home/HomeHeaderSlotPreview.kt`
    (`AlternativeHomeHeader`: Status oben, Aktionen darunter — nur Debug/Preview, keine Nutzer-Einstellung).
  - **Region `dialog` (Ist, 2026-06-12):** In-Tree-Vertrag `DialogSlot`
    (`@Composable (state: DialogState) -> Unit`). Die **Capability-Surface** `DialogState`
    (`app/ui/components/EinkModal.kt`) spiegelt die `EinkModal`-Parameter 1:1 (Titel · `onDismiss`/
    `onConfirm` · confirm/dismiss-Label · `confirmEnabled` · optionale `headerAction` · `content`) —
    das reine Layout-Detail `modifier` gehört dem Default-Renderer, nicht der Surface (keine Call-Site
    setzt es → Parameter ersatzlos entfernt). `EinkModal(...)` ist jetzt ein **dünner Host-Wrapper**:
    baut die Surface und ruft `LocalResolvedSlots.current.dialog(state)`; **keine** der ~9 Aufrufstellen
    ändert sich. `DefaultDialog` ist der verbatim aus dem alten `EinkModal`-Body extrahierte Onyx-Renderer
    (schwarzer Rand, Titel/Body/Aktionen). Swap-Beweis:
    `app/src/debug/kotlin/com/komgareader/app/ui/components/DialogSlotPreview.kt`
    (`AlternativeDialog`: Titel zentriert, Aktionen vertikal gestapelt — nur Debug/Preview, keine
    Nutzer-Einstellung). `EinkInfoDialog`/Scroll-Helfer bleiben unangetastet.
  - **Region `settings` (Ist, 2026-06-12):** In-Tree-Vertrag `SettingsSlot`
    (`@Composable (state: SettingsState) -> Unit`). Die **Capability-Surface** `SettingsState`
    (`app/ui/settings/SettingsScreen.kt`) ist minimal: die host-gebauten `SettingsSection`s
    (aus `buildSettingsSections`, je `id`/`icon`/`title`/`searchTerms` als Daten + `content` als
    host-gebautes Composable) + der Such-`query`. Der Pack **ordnet die Sektionen an** (Sidebar-
    Master-Detail vs. Accordion vs. flach) und besitzt den **Navigations-State selbst** (`selectedId`/
    `openId` leben bewusst *in* der Layout-Impl, nicht in der Surface — ein flacher Pack hat keine
    „aktive Sektion") — er rendert die Sektions-Inhalte nie neu. `SettingsScreen(query, modifier,
    viewModel)` ist jetzt ein **dünner Host-Wrapper**: baut die Surface und ruft
    `LocalResolvedSlots.current.settings(state)` in einem `Box(modifier)` (der `modifier` bleibt —
    `SettingsRoute` reicht das Route-Padding durch); **beide** Call-Sites (HomeScreen-Tab + SettingsRoute)
    unverändert. `DefaultSettings` ist der verbatim extrahierte Onyx-Renderer (adaptive Verzweigung,
    private Helfer `SettingsMasterDetail`/`SettingsSidebar`/`SettingsAccordion` unverändert). Swap-Beweis:
    `app/src/debug/kotlin/com/komgareader/app/ui/settings/SettingsSlotPreview.kt`
    (`AlternativeSettings`: flache Einzel-Scroll-Liste statt Sidebar/Accordion — nur Debug/Preview, keine
    Nutzer-Einstellung). `SettingsSections.kt`/`SettingsViewModel`/die Sektions-Inhalte bleiben unangetastet.
  `UiSlotPack(header, homeHeader, dialog, settings)` · `ResolvedSlots(header, homeHeader, dialog, settings)`
  · `DefaultSlots` mit allen vier Default-Impls. **E-Ink-Invarianten host-erzwungen:** Slots liefern
  Inhalt/Struktur, nie die Bewegungs-/Akzent-Policy (die bleibt an
  `LocalDisplayBehavior`/`LocalDesignTokens`/`LocalEinkMode`).
  **Weiter Soll:** die übrigen Slots (overlay/tiles/nav), die `ui-api`-Modul-Extraktion
  und der APK-Pack-Lader bleiben Soll (Skins-Plan P2/P3). Vertrag bewusst in-tree, **nicht** eingefroren.

- **Shell-Pack-Naht (Ist, 2026-06-12 — die oberste UI-Schicht, Form-Faktor):** Über den Region-Slots
  liegt jetzt eine Naht, die das **ganze Home-Layout-Skelett** auswechselt (Region-Slots restylen
  Regionen *in* einem festen Baum; ein Shell-Pack besitzt den **ganzen Baum** — Nav-Ort, Anordnung).
  `app/ui/shell/`: die **Capability-Surface** `AppShellState` (benannte Stücke: `destinations` als
  Nav-**Daten** + `selectedId`/`onSelect`; je `ShellDestination` trägt `icon`/`label` (Daten) und
  `header: HomeHeaderState?`/`content: @Composable` (host-gebaut)), der Vertrag `ShellPack`
  (`@Composable Render(AppShellState)`), die pure Auswahl `formFactorFor(widthDp)` (<600dp=compact)
  über `ShellPackRegistry.forFormFactor`. **Zwei Built-ins:** `DefaultShell` (E-Ink/Tablet-Bottom-Bar,
  pixelgleich zum alten `HomeScreen`) und `PhoneShell` (compact: Drawer-Nav statt Bottom-Bar). `HomeScreen`
  ist der **Host** (`HomeShellHost`): baut die Surface pro Tab + besitzt allen State/VMs/Dialoge, löst das
  Pack nach `screenWidthDp` auf und ruft `pack.Render(...)`. **Der entscheidende Schnitt — Daten vs.
  host-gebaut:** reine-Präsentation-über-Daten-Stücke (Nav) gehen als **Daten** rein (das Pack baut das
  Widget — die Widget-Wahl IST die Variabilität); logik-gebundene (content/header) als **host-gebaute**
  Composables (Pack platziert nur, „UI neu, Kernlogik gleich"). **NavHost + Reader unberührt:** der Reader
  ist eine Geschwister-Route im NavHost (`MainActivity`), liegt nicht *in* der Shell — der Shell-Pack-Bereich
  ist exakt das alte `HomeScreen`. **E-Ink host-erzwungen:** `PhoneShell` gatet die Drawer-Bewegung über
  `LocalEinkMode` (`snapTo` statt Slide). **Form-Faktor (Shell) ⟂ Geräteklasse (Theme)** — orthogonale
  Achsen. Emulator-verifiziert: expanded→DefaultShell, compact→PhoneShell-Drawer, gleiche `AppShellState`.
  **Weiter Soll:** deklarativer Shell-Pack (Ansatz 3, externe APK-Packs) + Form-Faktor-User-Override +
  per-compact-Politur des Headers. Design/Plan: `docs/superpowers/specs|plans/2026-06-12-modular-ui-shell-pack*`.

## Modulgrenzen (Gradle-Schnitt = erzwungene Architektur)

- `domain` hat **keine** Android-/Netz-/Quellen-Abhängigkeit. Pure Kotlin, pure Unit-Tests.
- Quellen-Module hängen nur von `domain` ab, nie voneinander, nie von `app`.
- `render-core`, `eink-onyx`, `guided-view` hängen nicht von der UI ab.
- `app` ist die imperative Shell (DI, ViewModels, Viewer-Host) — die einzige Schicht, die alles verdrahtet.
- Wenn ein Feature einen neuen Cross-Modul-Import nötig zu machen scheint: erst prüfen, ob es nicht
  hinter eine bestehende Naht gehört.

## Bezug

Gehört zu [[project-komga-eink-reader]]. Erweiterungs-Kochrezept: `source-extensibility.md`.
