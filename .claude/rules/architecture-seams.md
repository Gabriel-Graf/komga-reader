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
┌─ MediaSource ───────────┐   ┌─ Document (Render) ───────────────┐
│ KomgaSource (REST)      │   │ MuPDF (C++/JNI) → Bitmap          │
│ OpdsSource              │   │ EinkController (Onyx ⟂ No-Op)     │
│ [LocalSource, Plugins…] │   │ EinkContextController (@Singleton) │
│ SourceManager + Stub     │  │ Compose-Reader-Screens             │
└─────────────────────────┘   └───────────────────────────────────┘
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
  **kategorisiert**: `PluginCategory{COLOR_PRESET,READER_PRESET,LANGUAGE,UI_PACK}` (plugin-api, ABI
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
- **Externer UI-Pack (Ist, 2026-06-12, L2 — Schlussstein der UI-Modularität):** vierte data-only Kategorie
  `UI_PACK` über `discoverDataPlugins` — ein **extern installierbarer** APK liefert ein reines JSON-Asset
  `ui_pack.json` (Manifest `DATA_CATEGORY=UI_PACK`/`DATA_ASSET`/`ABI_VERSION=2`, `android:hasCode="false"`),
  das **deklarativ** Teile der Oberfläche ersetzt (kein Plugin-Compose, kein Host-Rechte-Risiko). **Drei
  Sektionen, alle optional** (Subset-Packs, fehlend → Host-Default): `shell.navStyle` (BOTTOM_BAR/DRAWER) ·
  `icons` (IconKey-Name→IconKey-Name-Remap unter den bestehenden Lucide-Glyphen, I1) · `theme`
  (`accent`-Hex + `cornerRadius`-dp). Der pure Pfad bleibt rein: `UiPackSpec` (domain, **nur Primitive**),
  `parseUiPackSpec` (data, `org.json`, ABI-Range-Check); die Übersetzung in ui-api/Compose-Typen
  (`toIconPack`/`shellOverride`/`tokenOverride`, `app/ui/pack/UiPackApply.kt`) passiert **nur in `:app`**.
  **Drei Apply-Pfade:** Icons → `ActiveIconPack.current` per `LaunchedEffect` in `MainActivity` (prozess-global,
  I1: live erst nach Recompose/Neustart) · Shell → `ShellPackRegistry.forFormFactor(ff, override)` (der
  navStyle-Override **schlägt** den Form-Faktor-Default, durchgereicht in `HomeScreen`) · Theme →
  `KomgaReaderTheme(tokenOverride = …)`. **E-Ink-Invariante host-erzwungen:** der **Akzent-Override** gilt
  NUR, wenn `LocalDisplayBehavior.current.allowsAccentColor` (mono E-Ink ignoriert ihn → bleibt Schwarz);
  Eckradius/Shell/Icons sind invariant-neutral und gelten immer. Aktive Auswahl persistiert wie LANGUAGE
  (Setting-Key `active_ui_pack`, `Flow<String>`, **keine** Migration; Picker „UI-Pack" in
  `AppearanceSettingsContent`, „Standard" = keiner). Discovery+Prune in `PluginCatalog`
  (`uiPackPlugins`/`uiPackDataPlugins`; aktiver Zeiger fällt auf `""`, wenn das Paket verschwindet);
  Plugins-Tab-Filter `UI_PACKS`; Repo-Index-Typ `ui_pack`→`PluginKind.UI_PACK` (Install/Fingerprint-Pfad
  unverändert). Sample-APK `plugin/komga-ui-pack-sample/` (Standalone, gitignored). **Kein** ui-api-Code-ABI-
  Freeze in L2 (data-Packs linken kein ui-api — der Vertrag ist das JSON-Schema); Code-UI-Packs/externe
  per-Slot-Packs bleiben additives Soll.
- **Font-Plugins (Ist, 2026-06-14, P2):** fünfte data-only Kategorie `PluginCategory.FONT` über
  `discoverDataPlugins(FONT)` (additiv, `PluginAbi.VERSION` bleibt 2) — ein **extern installierbarer** APK
  liefert TTFs als Assets (Manifest `DATA_CATEGORY=FONT`/`DATA_ASSET=<index.json>`/`LICENSE=<SPDX>`,
  `android:hasCode="false"`, `assets/fonts/*.ttf`). `DiscoveredDataPlugin` trägt dafür jetzt `license` +
  `versionCode`; Manifest-Key `PluginManifestKeys.LICENSE` neu. `PluginHost.extractFontAsset(pkg, assetPath,
  destRoot)` extrahiert die TTF nach permanenten, versions-gekeyten Speicher
  (`filesDir/plugin-fonts/<pkg>/<versionCode>/<basename>`, veraltete Versions-Verzeichnisse werden geprunt).
  **Harter SPDX-Lizenz-Gate** (`FontLicensePolicy.isLicenseAllowed`, `data`, Allowlist {OFL-1.1, Apache-2.0,
  CC0-1.0, MIT, Ubuntu-1.0}, case-insensitiv): Gate A beim Repo-Install (`PluginCatalog.install`, auf
  `entry.license`), Gate B beim Sideload (Filter in `scanLocal`, auf Manifest-`DiscoveredDataPlugin.license`).
  Nicht-gelistet → nie installiert/registriert; die APK-Lizenz ist autoritativ, `FontSpec.license` nur Anzeige.
  Verdrahtung: `PluginCatalog` merged lizenz-erlaubte Plugin-Fonts in `allNovelFonts`
  (= `NovelFonts.ALL` + Plugin-Fonts), bietet `fontDataPlugins` (roh) + `fontSampleFiles` (Familie→File);
  ein verschwundenes Plugin setzt `novelFontFamily` auf den Default zurück. Repo-Index-Typ `font`→
  `PluginKind.FONT`, Plugins-Tab-Filter `FONTS`. Die Render-Seite (crengine `registerFont`/`nativeAddFont`)
  steht in Naht B unten.

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
- **Runtime-Font-Registrierung (Ist, 2026-06-14, P2):** Plugin-TTFs (data-only Kategorie `FONT`, s. Naht A)
  werden zur **Laufzeit** in den crengine-Font-Manager eingehängt — kein App-Neustart. `domain` bleibt
  engine-frei über `ReflowableDocumentFactory.registerFont(absolutePath): Boolean` (Default no-op in
  `domain/render/Document.kt`). Die crengine-Impl `CrengineDocumentFactory.registerFont` puffert vor dem
  (einmaligen) `nativeInit`-Boot Pfade in `pendingFontPaths` (werden in den **einen** `nativeInit`-Aufruf
  geflusht) und registriert nach dem Boot live via neuem JNI `CrengineNative.nativeAddFont`
  (`render-crengine/.../cr3_bridge.cpp` → `fontMan->RegisterFont`). Ein zweites `nativeInit` ist verboten.
  Verdrahtet in `PluginCatalog` (extrahiert TTF via `PluginHost.extractFontAsset`, ruft `registerFont`).
- **Geräte-Naht (Ist):** `EinkController` (`domain/eink/EinkController.kt`) kapselt das Gerät:
  `OnyxEinkController` (Boox-SDK, **HW-gated** über `Build.MANUFACTURER`), `NoOpEinkController` als
  Fallback. **Entwicklung crasht nie auf Nicht-Boox-HW.** Trägt `EinkCapabilities`
  (hasEink/canColor/canInvert, `refreshModes: List<EinkModeOption>`, `colorModes: List<EinkModeOption>`;
  leere Liste = Achse nicht unterstützt, UI blendet Sektion aus) — siehe Big-Picture-Doku zur
  Geräteklassen-Frage.
- **E-Ink-Refresh + Farbe (Ist, 2026-06-13 — kontext-basierter EinkWise-Pfad):**
  Der Refresh-Modus und der System-Farbmodus werden **je Lese-Kontext** über das Onyx-EinkWise-API
  angewendet. Kerntypen in `domain/eink/`:
  `EinkContext{HOME,PAGED,WEBTOON,COMIC,NOVEL}` — was gerade auf dem Bildschirm ist;
  `EinkModeOption(id, label)` — ein vom Gerät beworbener Modus;
  `EinkContextProfile(refreshModeId?, colorModeId?)` — Profil pro Kontext (null = Gerätestandard
  unberührt lassen);
  `resolveEinkProfile(userOverride, deviceDefault)` — pure Funktion, unit-getestet
  (`EinkProfilesTest`): gesetzter Override gewinnt, ungesetzte Achse fällt auf Gerätestandard zurück.
  `EinkController` hat neu: `applyRefreshMode(id?)`, `applyColorMode(id?)`,
  `defaultProfile(context): EinkContextProfile`.
  `OnyxEinkController` bildet Ids auf `EpdController.setAppScopeRefreshMode(UpdateOption)` ab
  (hd→NORMAL, balanced→FAST_QUALITY, regal→REGAL, speed→FAST, ultra→FAST_X) und schaltet
  Systemfarbe via `enableColorAdjust()`/`disableColorAdjust()`. Gerätestandard: WEBTOON→speed,
  alle anderen→hd, Farbe immer system.
  `EinkContextController` (@Singleton, `app/data`) ist der imperative Shell-Singleton: liest
  User-Override aus `SettingsRepository.einkContextProfiles` (JSON-Blob, Room-Key
  `eink_context_profiles`, keine Migration), löst das Profil per `resolveEinkProfile` auf und
  ruft `controller.applyRefreshMode`/`applyColorMode`. `EinkContextEffect(context)` ist das
  zugehörige Composable: `LifecycleResumeEffect` stellt sicher, dass das richtige Profil auch
  nach einem Push (z. B. Reader→Home) beim Resume wiederhergestellt wird. Jeder Screen deklariert
  seinen Kontext: `HomeScreen→HOME`, `ReaderRoute` mappt `ViewerMode`/`ReaderContent.Novel`.
  Settings-Sektion **„E-Ink Dynamik"** (Matrix Kontext × {Refresh, Farbe}): nur sichtbar auf
  Boox (refreshModes/colorModes nicht leer). Ersetzt den alten `deviceManagedRefresh`-Toggle.
  Entfernt: `RefreshScheduler` (domain + Test), `OnyxRefresher` (eink-onyx), `EinkReaderEffect`,
  `ReaderEinkHolder`, `Viewer.refreshScheduler`-Property, `deviceManagedRefresh`-Setting,
  `ReaderPresetOverrides.deviceManagedRefresh`-Feld (Legacy-JSON-Key wird beim Lesen ignoriert),
  `enterFastMode`/`fullRefreshNow`/`fullRefreshIfNeeded` in `OnyxEinkController`.
- **Reader / Viewer-Naht (Ist, 2026-06-08; aktualisiert 2026-06-13):** Es gibt den **`Viewer`**-Vertrag
  (`app/ui/reader/Viewer.kt`) — eine **Compose-Zustands**-Naht (chromeVisible-`StateFlow`,
  `toggleChrome`/`navigateTo`/`onPageSettled`), **nicht** das alte OO-`bind/onButton/teardown`
  (Compose verwaltet den Lifecycle deklarativ). Alle Reader-VMs
  (`ReaderViewModel`, `ComicReaderViewModel`, `NovelReaderViewModel`) implementieren ihn; das
  geteilte `ReaderScaffold` arbeitet dagegen. Reader bleiben eigene `@Composable`-Screens
  (`PagedReaderScreen`/`WebtoonReaderScreen`/`ComicReaderScreen`/`NovelReaderScreen`), dispatcht
  per `when(ViewerMode)`/`when(ReaderContent)` in `ReaderRoute.kt` — ein 5. Reader/UI-Plugin
  implementiert **`Viewer`** statt einer Parallel-Linie. **Kein `refreshScheduler`** im `Viewer`
  (seit 2026-06-13 entfernt — E-Ink-Refresh läuft jetzt über den kontext-basierten
  `EinkContextController`-Pfad, nicht über die Viewer-Naht).
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
  beide Layouts auf **einem** geladenen `ReaderContent` rendert und dabei `_currentPage` und
  `frameStep` geteilt teilt. Ein voller Zwei-VM-Split würde diesen Toggle brechen (Scroll-/
  Seitenposition ginge verloren oder Inhalt müsste auf Toggle neu geladen werden = Lade-Sturm +
  Verhaltensänderung). Der Toggle bleibt daher **bewusst** in `ReaderViewModel` — dokumentiert
  direkt an `toggleViewerMode`. Die genuin webtoon-spezifische Logik ist trotzdem raus aus dem
  God-VM und einzeln getestet (`WebtoonStripPlannerTest`).

- **UI-Slot-Naht / Chrome (Ist, 2026-06-09 — erste Region `header` gebaut; Ist, 2026-06-12 — zweite
  Region `homeHeader`, dritte Region `dialog`, vierte Region `settings`, fünfte Region `tiles`,
  sechste Region `overlay`, siebte Region `detail` + achte Region `readerChrome` gebaut):**
  Über den Reader-Engines wird das *Chrome*
  (Header/Overlay/Tiles/Nav/Settings/Dialog) regionweise auswechselbar — das „Layout danach"-Stück der
  modularen UI (`big-picture-and-goals.md` → ui-modularity). **Gebaut sind sechs Chrome-Regionen + die
  detail-Region + die readerChrome-Region.** **Ist (2026-06-12, A1): die Verträge + entkoppelten
  Surfaces leben jetzt im eigenen Modul `:ui-api`** (`ui-api/…/com/komgareader/ui/slots/UiSlots.kt`
  u. a.), nicht mehr in-tree in `app/ui/...`. Die **gekoppelten Default-Renderer** (`DefaultSlots`/
  `DefaultHeader`/`DefaultDialog`/… — der Onyx-Look an app-i18n/-Komponenten) bleiben in `:app`. Eine
  Folge: `UiSlots.resolve` ist **2-arg** (`resolve(pack, defaults)`, pur), `LocalResolvedSlots` hat
  einen **Error-Default**, und der Host speist über die app-`resolveSlots`/`DefaultSlots.resolved` ein.
  Vertrag **noch nicht eingefroren** (kein ABI-Gate — kommt mit dem Pack-Lader L1/L2):
  - **Region `header` (Ist, 2026-06-09; Such-Capability 2026-06-12, D1.1):** In-Tree-Vertrag
    `HeaderSlot` ist jetzt **`@Composable (state: HeaderState) -> Unit`** — die Capability-Surface
    `HeaderState(title, onBack?, actions, search: HeaderSearch?)` (in `UiSlots.kt`) trägt zusätzlich
    eine **optionale Such-Capability** `HeaderSearch(active, query, onQueryChange, onOpen, onClose,
    placeholder?)` (Titel↔Suchfeld per Lupe, Vorbild `HomeHeaderSearch`). Der Default-Renderer ist
    `DefaultHeader(state)`: ohne Suche eine `StandardTopAppBar` (mit vorgelagerter Lupe, falls
    `search != null`), bei `search.active` ein zentriertes Suchfeld statt Titel (verbatim aus dem alten
    `CollectionDetailHeader`). **Abwärtskompatibel:** die resolved-Property heißt **`headerSlot`** (nicht
    `header`), und eine dünne **Kompat-Extension** `ResolvedSlots.header(title, onBack, actions)` baut die
    Surface und ruft `headerSlot` — die suchlosen Call-Sites (`SubPageScaffold`, `SettingsRoute`,
    `HeaderSlotPreview`) bleiben **textlich unverändert** (nur ein Import der Extension nötig). Der
    such-fähige Pfad ruft `headerSlot(HeaderState(…, search = …))` direkt (über das `detail`-Gerüst,
    s. u.). Swap-Beweis: `HeaderSlotPreview.kt` (zentrierter Alternativ-Header + ein Such-Zustands-Preview,
    nur Debug/Preview).
  - **Region `homeHeader` (Ist, 2026-06-12):** In-Tree-Vertrag `HomeHeaderSlot`
    (`@Composable (state: HomeHeaderState) -> Unit`). Die **Capability-Surface** `HomeHeaderState`
    kapselt Status, **Tab-Titel** (`title`, im Drawer-Modus neben dem Burger gezeigt; Bottom-Bar zeigt
    stattdessen den Status), Suche (`HomeHeaderSearch`), generischen Filter-Slot (`HomeHeaderFilter`),
    Menü-Overlay und Tab-spezifische Aktionen. Der Host (Core) baut die Surface und besitzt die Logik; das Pack
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
  - **Region `tiles` (Ist, 2026-06-12):** In-Tree-Vertrag `TilesSlot`
    (`@Composable (state: TileState, modifier: Modifier) -> Unit`). Die **Capability-Surface** `TileState`
    (`app/ui/components/SeriesTile.kt`) trägt das Werk (`series`) + den Lokal-Status (`isLocal`) + die
    Navigations-Callbacks (`onClick`/`onLongClick`). Der Slot tauscht **die einzelne Serien-Kachel, nicht
    das Grid** — `LazyVerticalGrid`/Spaltenzahl/welche-Items bleiben Screen-Eigentum (analog: der
    Dialog-Aufrufer entscheidet *wann* ein Dialog erscheint, der Slot nur *wie*). Der `modifier` ist hier
    **zweiter Slot-Parameter** (anders als dialog/settings): eine Grid-Kachel trägt einen Layout-Modifier.
    `SeriesTile(series, isLocal, onClick, onLongClick, modifier)` ist jetzt ein **dünner Host-Wrapper**:
    ruft `LocalResolvedSlots.current.tiles(TileState(...), modifier)`; **beide** Call-Sites unverändert
    (`LibraryScreen`, `GroupBrowseRoute`). **Vorgelagerter DRY-Schritt** (`shared-structure-before-variants`):
    der ~95%-Klon `GroupSeriesCover` in `GroupBrowseRoute` wurde **vor** dem Slot durch `SeriesTile`
    (`onLongClick = {}`) ersetzt — sonst träfe ein tiles-Pack nur die Bibliothek, nicht die Gruppen.
    `DefaultSeriesTile` ist der verbatim extrahierte Onyx-Renderer (Cover via `SourceCover`,
    Lokal/Cloud-Badge, `TileTitleBand`); Cover-Laden + E-Ink-Filter (`FilteredAsyncImage`,
    `crossfade(false)`) bleiben **host-erzwungen**. Swap-Beweis:
    `app/src/debug/kotlin/com/komgareader/app/ui/components/TileSlotPreview.kt`
    (`AlternativeTile`: Titel über dem Cover statt Scrim-Band unten, Badge bewusst weggelassen — nur
    Debug/Preview, keine Nutzer-Einstellung). Die anderen Kachel-Typen (`ChapterTile`/`CollageTile`/
    `MemberTile`) sind eigene spätere Regionen, hier unangetastet.
  - **Region `overlay` (Ist, 2026-06-12 — sechste/letzte):** In-Tree-Vertrag `OverlaySlot`
    (`@Composable BoxScope.(state: ReaderOverlayState) -> Unit`). Slot-ifiziert genau die **togglebare
    Reader-Chrome-Menüleiste** (`ReaderChromeOverlay` → ersetzt durch `DefaultReaderOverlay`). Die
    **Capability-Surface** `ReaderOverlayState` (`app/ui/reader/ReaderChrome.kt`) trägt `title` +
    `onBack`/`onHome`/`onSettings` + die reader-spezifischen `actions`. **Besonderheit:** der Slot ist
    eine **`BoxScope`-Extension** (die Leiste positioniert sich per `Modifier.align(TopCenter)` im
    Reader-`Box`) — kein zweiter Modifier-Parameter wie bei `tiles`. **Kein `visible`-Flag in der
    Surface:** die Sichtbarkeit (chromeVisible) **und** der E-Ink-Scrim (`readerOverlayScrim`) sind
    **host-erzwungen** — `ReaderScaffold` rendert die Leiste nur in `if (chromeVisible) { … }`. **Compose-
    Knackpunkt:** der implizite `BoxScope`-Receiver greift bei einem Funktionswert nicht; die Call-Site
    macht ihn explizit (`val overlay = LocalResolvedSlots.current.overlay; with(this) { overlay(state) }`).
    `ReaderScaffold`/Tap-Zonen/`ReaderStatusBar`/`persistentBars`/Hints + Reader-Engines + `Viewer`-Naht
    unberührt; nur das *Aussehen* der Leiste ist auswechselbar (NICHT in R4: Footer/Tap-Zonen → C1).
    Swap-Beweis: `app/src/debug/kotlin/com/komgareader/app/ui/reader/OverlaySlotPreview.kt`
    (`AlternativeReaderOverlay`: Shortcuts links, Titel zentriert, actions weggelassen — nur Debug/Preview).
  - **Region `detail` (Ist, 2026-06-12 — siebte, Sub-Projekt D1):** In-Tree-Vertrag `DetailSlot`
    (`@Composable (state: DetailScaffoldState) -> Unit`). Slot-ifiziert das **Vollbild-Detail-Gerüst**
    (Scaffold + Header über den `header`-Slot + optionaler Snackbar + padding-durchgereichter Body) —
    keine eigene Chrome-Region, sondern das geteilte Gerüst, das die Detail-Routen über den `header`-Slot
    **komponiert** (`shared-structure-before-variants`: es war zuvor in jeder Detail-Route dupliziert). Die
    **Capability-Surface** `DetailScaffoldState` (`app/ui/detail/DetailScaffold.kt`) trägt `title` +
    `onBack` + Header-`actions` (→ header-Slot) + optionalen `snackbarHost` + den **host-gebauten** `content`
    (`@Composable (PaddingValues) -> Unit`). Der Body (Hero/Grid/Dialoge/State) bleibt Screen-Eigentum; das
    Pack platziert ihn nur, baut ihn nie neu („UI neu, Kernlogik gleich"). `DefaultDetailScaffold` ist der
    verbatim extrahierte Onyx-Renderer (Material-`Scaffold` + header-Slot + Snackbar). **Umgestellt:**
    `SeriesDetailScreen` (mit Snackbar + Bookmark/Home-Aktionen) + `GroupBrowseRoute` (ohne Snackbar,
    Refresh-Aktion) bauen das Gerüst nicht mehr selbst, sondern über `LocalResolvedSlots.current.detail(...)`;
    `TypeMenu`/`AddToCollectionSheet` bleiben nach dem detail-Aufruf, `SeriesDetailContent`/Hero/Grid/VMs
    unverändert. E-Ink/Header-Look host-erzwungen. Swap-Beweis:
    `app/src/debug/kotlin/com/komgareader/app/ui/detail/DetailSlotPreview.kt`
    (`AlternativeDetailScaffold`: eigener schlanker Titelbalken statt header-Slot, ohne Material-Scaffold,
    Snackbar/actions weggelassen — nur Debug/Preview).
    **D1.1 (Ist, 2026-06-12) — D1 vollständig:** Die letzte Detail-Route `CollectionDetailScreen` ist
    umgestellt — `DetailScaffoldState` trägt jetzt zusätzlich ein optionales `search: HeaderSearch?`, das
    `DefaultDetailScaffold` über `headerSlot(HeaderState(title, onBack, actions, search))` durchreicht
    (SeriesDetail/GroupBrowse lassen `search = null`, Verhalten unverändert). CollectionDetail baut das
    Gerüst über `current.detail(...)` mit `actions` (Add nur Serien-Sammlung · Sync mit `onGloballyPositioned`-
    Anchor · Delete) + `search` (Titel↔Suchfeld) + Body (3-Spalten-`MemberTile`-Grid); das private
    `CollectionDetailHeader` ist gelöscht (sein Such-/Titel-Verhalten lebt jetzt im `DefaultHeader`). Popups
    (`showSyncPanel`/`showAdd`/`showDelete`) · `collection==null`-Early-Return · `members`-Filter · `MemberTile`
    unverändert. **Alle drei Detail-Routen jetzt modular.** `MemberTile` bleibt eine eigene Kachel (eigene
    spätere `member`-tiles-Region, YAGNI).
  - **Region `readerChrome` (Ist, 2026-06-12 — achte, Sub-Projekt C1):** In-Tree-Vertrag
    `ReaderChromeSlot` (`@Composable (state: ReaderScaffoldState) -> Unit`). Slot-ifiziert das **ganze
    Reader-Gerüst** (`ReaderScaffold` — Vollbild-Hintergrund, Drittel-Tap-Zonen, Tap-Zonen-Hints, die
    Chrome-Menüleiste über die schon slot-ifizierte `overlay`-Region, optionaler Status-Fuß, `persistentBars`,
    Start-Hinweis und der eigentliche Inhalt). Die **Capability-Surface** `ReaderScaffoldState`
    (`app/ui/reader/ReaderScaffold.kt`) trägt `chromeVisible` + `onToggleChrome` + `title` + `onBack`/
    `onHome`/`onSettings` + `onPrev`/`onNext` + `background` + reader-spezifische `actions` + `tapZones`
    (deklarativ, A1b — s. u.) + `footer` + `persistentBars` + `showTapZoneHints` + den host-gebauten `content`. **Der entscheidende
    Schnitt:** die Surface trägt **NICHT** den `Viewer` (Naht B). `ReaderScaffold` benutzte `chrome: Viewer`
    nur für `chrome.chromeVisible.collectAsState()` + `chrome.toggleChrome()` (Mitte-Tap; per grep verifiziert
    — `refreshScheduler`/`navigateTo`/`onPageSettled` fasst das Scaffold nie an), darum trägt die Surface die
    **abgeleiteten** `chromeVisible: Boolean` + `onToggleChrome: () -> Unit` statt des `Viewer`. So bleiben
    Refresh-Scheduler + Engine-Navigation (Naht B) vollständig aus der austauschbaren Surface — ein Chrome-Pack
    kann sie nicht berühren. `ReaderScaffold(chrome, …)` bleibt ein **dünner Host-Wrapper** (collectAsState +
    Surface bauen + `LocalResolvedSlots.current.readerChrome(state)`); **die fünf Reader-Call-Sites bleiben
    unverändert** (PagedReaderScreen/WebtoonReaderScreen/ComicReaderScreen/NovelReaderScreen/EpubReaderScreen).
    `DefaultReaderScaffold(state)` ist der verbatim extrahierte Onyx-Renderer; der innere Overlay-Aufruf bleibt
    über die `overlay`-Region (Komposition). Der E-Ink-Scrim (`readerOverlayScrim`) + die Animation-Gating-Pfade
    bleiben host-erzwungen. Reader-Engines / `Viewer.kt` / die `ReaderChrome.kt`-Helfer
    unangetastet. Swap-Beweis: `app/src/debug/kotlin/com/komgareader/app/ui/reader/ReaderChromeSlotPreview.kt`
    (`AlternativeReaderChrome`: Status-Fuß oben statt unten, Tap-Hints/Start-Hinweis weggelassen — nur
    Debug/Preview).
  `UiSlotPack(header, homeHeader, dialog, settings, tiles, overlay, detail, readerChrome)` (Vertrag
  jetzt im Modul `:ui-api`, `com.komgareader.ui.slots`) · `ResolvedSlots(…, detail, readerChrome)` (ui-api) ·
  **app-`DefaultSlots`** mit allen acht gekoppelten Default-Impls (Onyx-Look, bleibt in `:app`).
  **E-Ink-Invarianten host-erzwungen:** Slots liefern
  Inhalt/Struktur, nie die Bewegungs-/Akzent-Policy (die bleibt an
  `LocalDisplayBehavior`/`LocalDesignTokens`/`LocalEinkMode`).
  Die ursprünglich genannte Region `nav` ist
  **kein** Region-Slot — das Nav-Skelett gehört dem **Shell-Pack** (unten). **Ist (A1, 2026-06-12):** die
  `ui-api`-Modul-Extraktion ist **gebaut** (Verträge + entkoppelte Built-ins im Modul `:ui-api`).
  **Weiter Soll:** der APK-Pack-Lader (Skins-Plan P2/P3); das **Reader-Chrome komplett
  modular** ist mit der `readerChrome`-Region als **Gerüst** gebaut **und seit A1b (Ist, 2026-06-12)
  deklarativ**: das frühere opake `tapModifier: Modifier?` ist durch den endlichen Daten-Deskriptor
  `ReaderTapZones` (sealed, `HorizontalThirds(left/center/right)` + pure `dispatch`, in `:ui-api`) ersetzt —
  die Drittel-**Geometrie** gehört dem Host (`DefaultReaderScaffold` interpretiert sie an *einer* Stelle,
  `pointerInput(Unit)` + `rememberUpdatedState` gegen Gesten-Neustart), der Reader liefert pro Zone nur die
  **Aktion**. Comic ist die Escape-Luke (`tapZones = null` → kein Host-Tap-Layer, Panel-Hit-Test in seiner
  content-Lambda). Offen bleibt nur die **externe/Enum**-Aktionsform + der Pack-Lader (L1/L2); der
  spätere `DetailShell` (Hero/Grid als arrangierbare Stücke). Vertrag im `:ui-api`-Modul, **noch nicht
  eingefroren** (kein ABI-Gate — das kommt mit L1/L2, dann re-exportiert das Lader-Modul `ui-api` via `api()`).

- **Shell-Pack-Naht (Ist, 2026-06-12 — die oberste UI-Schicht, Form-Faktor):** Über den Region-Slots
  liegt jetzt eine Naht, die das **ganze Home-Layout-Skelett** auswechselt (Region-Slots restylen
  Regionen *in* einem festen Baum; ein Shell-Pack besitzt den **ganzen Baum** — Nav-Ort, Anordnung).
  **Vertrag im Modul `:ui-api`** (`com.komgareader.ui.shell`, A1): die **Capability-Surface** `AppShellState`
  (benannte Stücke: `destinations` als
  Nav-**Daten** + `selectedId`/`onSelect`; je `ShellDestination` trägt `icon`/`label` (Daten) und
  `header: HomeHeaderState?`/`content: @Composable` (host-gebaut)), der Vertrag `ShellPack`
  (`@Composable Render(AppShellState)`), die pure Auswahl `formFactorFor(widthDp)` (<600dp=compact) +
  `resolveFormFactor`. **L1 (Ist, 2026-06-12): die zwei bespoke Built-ins sind durch EINE
  deskriptor-getriebene `DeclarativeShell` ersetzt.** Statt `DefaultShell`/`PhoneShell` als getrennte
  `object … : ShellPack` trägt `:ui-api` jetzt den **Daten-Deskriptor** `ShellDescriptor(navStyle)` +
  das endliche Vokabular `ShellNavStyle{BOTTOM_BAR,DRAWER}` + die pure `descriptorFor(formFactor)`
  (EXPANDED→BOTTOM_BAR, COMPACT→DRAWER; **Compose-frei**, für L2-Serialisierung). Die **eine**
  `DeclarativeShell(descriptor): ShellPack` (`:app`, `com.komgareader.app.ui.shell`) interpretiert den
  Deskriptor und rendert die zwei Skelette als **deskriptor-geschaltete private Composables**
  (`BottomBarShell`/`DrawerShell` — die verbatim Bodies der alten Objekte). `ShellPackRegistry.forFormFactor`
  liefert `DeclarativeShell(descriptorFor(ff))`. Das ist der In-Tree-Beleg des 1→3-Pfads: ein externer
  APK-Pack (L2) liefert später nur den `ShellDescriptor`, dieser Renderer bleibt. **Aurora / Modern-Mobile-Look
  (Ist, 2026-06-12, Phase 1):** dritter `ShellNavStyle` **`FLOATING_NAV`** (schwebende Pill-Bottom-Nav,
  `FloatingNavBar`/`FloatingNavShell` in `:app`) + vierter Theme-`UiPack` **`AuroraPack`** (`:ui-api`, Slate/
  Deeper-Grey + Cobalt `#3D5AFE`, dark+light, SoftShapes, getunte System-Typo); `packFor(LCD)→AuroraPack`
  (`LcdPack` bleibt registrierter Fallback). **Aktiv NUR im Smartphone-Modus** (`DisplayMode.SMARTPHONE`): die
  pure `auroraShellOverride(DisplayMode)` setzt `FLOATING_NAV` (ein L2-UI-Pack-`shellOverride()` **schlägt** ihn:
  `shellOverride ?: auroraShellOverride(...)`), und der Host speist die Card-Kachel `AuroraSeriesTile` über den
  `tiles`-Slot ein. `BottomBarShell`/`FloatingNavShell` teilen das Gerüst (Inset-Mechanik) über das geteilte
  `OverlayBarShell` (Bar als `@Composable`-Slot, `shared-structure-before-variants`); die Kachel-Varianten teilen
  `TileCoverContent`. E-Ink-Modus unverändert (Default-Kachel + `EinkBottomBar`). Emulator-verifiziert (dark+light
  + E-Ink-Regression). **Phase 2 (Ist, 2026-06-13) — das deklarative `ui_pack.json`-`theme` trägt jetzt den
  vollen Look als Daten:** `theme.light`/`dark` mit 8 Farb-Rollen (background/surface/navDock/accent/onAccent/
  onBackground/onSurfaceVariant/outline) + `cornerRadius`/`elevation`/`typography`. Reinheit: `ThemeSpec` (domain,
  Primitive) → `parseUiPackSpec` (data) → `UiPackSpec.toUiPackOrNull()` (app, Runtime-`UiPack`) → `KomgaReaderTheme`
  **ersetzt** den Geräteklassen-Pack durch den externen — **host-gegated** (`allowsAccentColor`; mono E-Ink ignoriert
  Farben, Emulator-verifiziert). `navDock`→`surfaceVariant` (Floating-Nav-Fläche). Alte flache accent/cornerRadius-
  Packs bleiben über `tokenOverride` gültig. **Externes Aurora-Daten-APK** (`plugin/komga-ui-pack-aurora`,
  Distributions-Repo) reproduziert den In-Tree-Look 1:1 (1→3-Beweis, mit rotem Akzent distinkt bewiesen).
  **Form-Faktor-User-Override (Ist, 2026-06-12, S0.1):** die
  pure `resolveFormFactor(mode: ShellLayoutMode, widthDp)` (neben `formFactorFor`, unit-getestet) lässt
  den Nutzer den Form-Faktor überschreiben — Domain-Enum `ShellLayoutMode{AUTO,COMPACT,EXPANDED}`,
  persistiert wie `displayMode` (`SettingsRepository.shellLayoutMode`/`setShellLayoutMode`, Room-Key
  `shell_layout_mode`, **keine Migration**), Picker in `AppearanceSettingsContent`/`GeneralScope`
  (i18n `settingsShellLayout`/`shellLayout{Auto,Compact,Expanded}`, de+en). Default `AUTO` =
  verhaltensgleich zu vorher (aus Breite ableiten). **Achse bleibt orthogonal zum `displayMode`** (Theme).
  **Drei deskriptor-geschaltete Skelette (private Composables in `DeclarativeShell`, Gerüst geteilt über
  `OverlayBarShell`; `FLOATING_NAV`→`FloatingNavShell` kam mit Aurora/Phase 1):** `BottomBarShell`
  (E-Ink/Tablet-Bottom-Bar, pixelgleich zum alten `HomeScreen`) und `DrawerShell` (compact: Drawer-Nav statt
  Bottom-Bar; die aktive Drawer-Zeile folgt seit S0.3 den Host-Mono-Tokens `LocalDesignTokens.accent`/`onAccent`
  über `NavigationDrawerItemDefaults.colors`, konsistent mit `EinkBottomBar` — kein Material3-Default-Akzent mehr).
  `HomeScreen`
  ist der **Host** (`HomeShellHost`): baut die Surface pro Tab + besitzt allen State/VMs/Dialoge, löst das
  Pack über `resolveFormFactor(shellLayoutMode, screenWidthDp)` (Override schlägt Auto) auf und ruft
  `pack.Render(...)`. **Der entscheidende Schnitt — Daten vs.
  host-gebaut:** reine-Präsentation-über-Daten-Stücke (Nav) gehen als **Daten** rein (das Pack baut das
  Widget — die Widget-Wahl IST die Variabilität); logik-gebundene (content/header) als **host-gebaute**
  Composables (Pack platziert nur, „UI neu, Kernlogik gleich"). **NavHost + Reader unberührt:** der Reader
  ist eine Geschwister-Route im NavHost (`MainActivity`), liegt nicht *in* der Shell — der Shell-Pack-Bereich
  ist exakt das alte `HomeScreen`. **Wisch-Navigation (Ist, 2026-06-13):** der geteilte `ShellContent`
  (in `DeclarativeShell`, von allen drei Skeletten genutzt) rendert den aktiven Inhalt im Smartphone-Modus
  (`allowsMotion`) in einem `HorizontalPager` über ALLE Destinations — der ganze Inhalt ist zwischen den Tabs
  wischbar, Tab-Tap ⇄ Wisch-Geste bleiben über `selectedId`/`settledPage` synchron. **E-Ink host-erzwungen:**
  bei `!allowsMotion` KEIN Pager (direktes Rendern), weil jede Wisch-Bewegung Ghosting/Teil-Refresh erzeugt
  (`animation-gating.md`). **E-Ink host-erzwungen:** `DrawerShell` gatet die Drawer-Bewegung über
  `LocalEinkMode` (`snapTo` statt Slide); der Drawer trägt oben einen Kopf (Logo + App-Name), die aktive Zeile
  ist theme-gerundet (`MaterialTheme.shapes.small`, kein Material-Pill). **Form-Faktor (Shell) ⟂ Geräteklasse (Theme)** — orthogonale
  Achsen. Emulator-verifiziert: expanded→Bottom-Bar-Skelett, compact→Drawer-Skelett, gleiche `AppShellState`.
  Swap-Beweis: `app/src/debug/.../ui/shell/DeclarativeShellPreview.kt` (dieselbe `AppShellState`, nur der
  `ShellDescriptor` schaltet das Skelett). **Weiter Soll:** der **externe** deklarative Shell-Pack-Lader
  (L2: separates APK / ABI-Gate / TOFU; liefert den `ShellDescriptor` extern, `DeclarativeShell` bleibt) +
  per-compact-Politur des Headers (S0.2 — auf Emulator verifizieren, nur fixen wenn real kaputt).
  Design/Plan: `docs/superpowers/specs|plans/2026-06-12-modular-ui-shell-pack*` · `2026-06-12-shell-restposten-S0.md`.

## Modulgrenzen (Gradle-Schnitt = erzwungene Architektur)

- `domain` hat **keine** Android-/Netz-/Quellen-Abhängigkeit. Pure Kotlin, pure Unit-Tests.
- Quellen-Module hängen nur von `domain` ab, nie voneinander, nie von `app`.
- `render-core`, `eink-onyx`, `guided-view` hängen nicht von der UI ab.
- `app` ist die imperative Shell (DI, ViewModels, Viewer-Host) — die einzige Schicht, die alles verdrahtet.
- Wenn ein Feature einen neuen Cross-Modul-Import nötig zu machen scheint: erst prüfen, ob es nicht
  hinter eine bestehende Naht gehört.

## Bezug

Gehört zu [[project-komga-eink-reader]]. Erweiterungs-Kochrezept: `source-extensibility.md`.
