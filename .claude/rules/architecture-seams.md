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
- **LocalSource gebaut (Ist, 2026-06-14):** `SourceKind.LOCAL` ist real — Modul `:source-local`
  (`LocalSource : BrowsableSource`, `id = SourceId.LOCAL = 0`) liest einen vom Nutzer gewählten
  **SAF-Geräteordner** als Quelle, voll gemischt mit Komga/OPDS. Konvention: Unterordner = Serie,
  Dateien darin = Bände (natürlich sortiert), lose Wurzeldateien = Ein-Band-Serie. **Renderer-frei**
  (hängt **nicht** an `render-core`): eine CBZ-Seite *ist* eine gespeicherte Bilddatei, daher liefert
  `openPage` den rohen Zip-Eintrag (`java.util.zip`, kein Decode); für PDF/CBR/EPUB gibt `pages()`
  `emptyList()` zurück → der Reader rendert whole-file (s. u. „Reader-Lesepfad"). `SyncingSource`
  wird **nicht** implementiert (kein Server zum Syncen — Fortschritt bleibt lokal). **Cover (Ist,
  2026-06-16 — entlang Format aufgeteilt):** `coverBytes` liefert die **renderer-freien** Formate
  direkt durch die Naht: **CBZ** = erstes Zip-Bild (`CbzArchive`), **EPUB** = das **eingebettete
  Cover-Bild** aus dem EPUB-Zip (`EpubCover.kt`: OPF folgen — EPUB3 `properties="cover-image"`, sonst
  EPUB2 `<meta name="cover">`→Manifest-Item; pures Zip+Text-Parsing, JVM-unit-getestet `EpubCoverTest`).
  Das EPUB-Cover ist damit **Vollbild wie das Server-Cover** (Komga liefert dasselbe eingebettete Bild),
  **nicht** die reflowte erste Seite mit Lese-Rändern. **PDF/CBR** brauchen echt eine Render-Engine →
  `coverBytes` gibt `ByteArray(0)`, und das Cover entsteht **außerhalb der Quelle**: der `@Singleton`
  **`LocalCoverStore`** (`:data`, `com.komgareader.data.cover` — hängt nur am `DocumentFactory`-**Interface**
  von Naht B, nicht an einer Render-Impl; 2026-06-17 aus `app/data` hierher verschoben, hält `app` dünn)
  **rendert vor** (Seite 0 via `DocumentFactory`/MuPDF, `renderFirstPageCover`) und
  **persistiert** nach `filesDir/local-covers/<key>.png` — signatur-gekeyt (`coverCacheKey(remoteId,
  size:mtime)`, pure, unit-getestet), gebündelte Nebenläufigkeit (`Semaphore`), gepruned
  (`coverPrunePlan`, pure). Der **Hintergrund-Prewarm** läuft als `appScope.launch` am Ende von
  `LocalDownloadSync.sync()` (nicht-blockierend, idempotent) → das Bibliotheks-Grid lädt sofort statt
  pro Cover synchron in Coils `fetch` zu rendern (E-Ink-Jank). `LocalCoverRenderer` (`app/data`) ist
  nur noch der dünne **on-demand-Fallback** im `SourceCoverFetcher` (Coil), der bei leeren
  Primär-Bytes + `sourceId == LOCAL` an `LocalCoverStore.get` delegiert (PDF/CBR; CBZ/EPUB liefert die
  Source). Metadaten
  best-effort: Dateiname als Basis, `seriesDetail` reichert aus der **ersten** CBZ-`ComicInfo.xml`
  an (ein Materialize, nicht pro Buch — sonst kopierte ein Listing jede Datei). **Wichtig — opake
  remoteIds:** lokale Pfade enthalten `/`, die App fädelt remoteIds aber als einzelne Nav-Pfad-Segmente
  → `LocalSource` exponiert **Base64-URL-kodierte** remoteIds und dekodiert intern auf den echten Pfad
  (sonst crasht das Routing). `LocalFileCache` materialisiert SAF-Dateien einmalig in den App-Cache
  (LRU) für Random-Access. Verdrahtung: `SourceKind.LOCAL`-Branch in `SourceRegistration`
  (`LocalSourceFactory.create(context, name, treeUri)`, `@ApplicationContext`). **Ordnerwahl in
  Settings → Downloads** (nicht im Server-Dialog — UX-Entscheidung 2026-06-14: ein lokaler Ordner ist
  kein Server, daher aus der „Verbundene Server"-Liste **ausgeblendet** via `kind != LOCAL`-Filter):
  Gruppe „Lokaler Ordner" (`ACTION_OPEN_DOCUMENT_TREE` + `takePersistableUriPermission`, **Upsert** über
  die eine LOCAL-Rowid → kein zweiter Eintrag, Release beim Entfernen) neben „Download-Ordner" + einem
  „Gemeinsamer Ordner"-Button (`SettingsViewModel.setBothFolders` setzt beide Pfade gleich). Beide Pfade
  werden **voll qualifiziert** angezeigt (`treeUriToDisplayPath`: SAF-Tree-URI → `/storage/emulated/0/…`).
  **E2E (Emulator, 2026-06-14):** Ordnerwahl →
  Bibliothek (Serie+Bände+lose Werke) → CBZ-PAGED (Zip-Extrakt) + PDF (whole-file MuPDF) gerendert,
  Persistenz über Neustart. (EPUB liefert Bytes korrekt; crengine-`.so` fehlt für x86_64-Emulator —
  arm64-Boox unberührt.) V1 = ein Ordner; mehrere Ordner = additives Soll.
- **Lokale Werke = „heruntergeladen" (Ist, 2026-06-14):** Werke der LOCAL-Quelle werden in die
  **Download-Tabelle** gespiegelt (User-Entscheidung: lokal == heruntergeladen). `LocalSource.asDownloadedBooks()`
  baut `DownloadedBook`s (sourceId=0, `localPath` = echte SAF-Document-URI via O(1)
  `LocalFolderScanner.documentUri`, ids = die opaken encodeten remoteIds, `totalPages=0`); der `@Singleton`
  **`LocalDownloadSync`** (app/data) **reconciliert** die Tabelle (upsert alle, stale sourceId=0-Zeilen löschen;
  Ordner weg → alle sourceId=0-Zeilen weg) und läuft über `SyncCoordinator.onAppStart`/`onServerChanged`/
  `onManualReload`. Folge: lokale Werke tragen das **Lokal/Download-Badge** (`isLocal` aus der Download-Tabelle)
  und lesen über den **Offline-Pfad** (`documentFactory.open` aus der content-URI) statt `openPage` — der
  CBZ-Zip-Extrakt-Pfad wird für lokale Werke dadurch unbenutzt (bleibt für andere streamlose Quellen). **Löschen
  eines lokalen Werks löscht die ECHTE Datei** (kein Papierkorb) — `SeriesDetailScreen` fängt das Entfernen für
  sourceId=0 ab und zeigt erst ein **Warn-`EinkModal`** („Lokale Datei löschen?"), bestätigt → `removeDownload`/
  `removeAll` → `DownloadManager.delete` → `DocumentFile.delete`. „Alle herunterladen" überspringt lokale Werke
  automatisch (`!in localBookIds`). E2E-verifiziert (Badge + Warn-Modal + Abbrechen lässt Datei intakt).
- **Externe Datei „öffnen mit" über transiente Download-Zeile (Ist, 2026-06-15):** Eine per VIEW-Intent
  übergebene `.epub`/`.cbz`/`.cbr`/`.pdf` (Boox-Dateimanager / „Öffnen mit") öffnet **ohne** neue
  `MediaSource` — über die reservierte transiente Quellen-ID **`SourceId.EXTERNAL = 1L`** (`source-api`) und
  den **bestehenden Offline-/Download-Lesepfad**. `ExternalBookOpener` (`app/data`): `prepareEphemeral`
  fügt eine transiente `DownloadedBook(sourceId = SourceId.EXTERNAL, localPath = content-URI)` ein → der
  vorhandene Reader liest sie (`documentFactory.open`, kein Reader-Umbau); `importToFolder` kopiert via
  `DocumentFile` in den lokalen(=Download-)SAF-Ordner; `purgeTransient`
  (`DownloadRepository.removeBySourceId` → DAO `deleteBySourceId`) räumt die EXTERNAL-Zeilen bei
  `SyncCoordinator.onAppStart`. **`LocalDownloadSync` reconciliert nur `sourceId == SourceId.LOCAL`** (id 0),
  fasst also die EXTERNAL-Zeilen (id 1) nie an — deshalb die getrennte reservierte ID. `detectBookFormat(mime,
  fileName): BookFormat?` (`domain/usecase`, `enum BookFormat{CBZ,CBR,PDF,EPUB}`) bestimmt die Viewer-Auflösung
  (Route `viewerMode=PAGED`, EPUB→Novel über das Format). `MainActivity` trägt den VIEW-`<intent-filter>`
  (content-Schema, Buch-MIME-Typen + `application/octet-stream`), fängt den Intent in `onCreate`/`onNewIntent`
  und zeigt — je nach persistiertem `SettingsRepository.externalOpenBehavior`
  (`ExternalOpenBehavior{ASK,IMPORT,READ_ONLY}`, Room-Key `external_open_behavior`, keine Migration) — ein
  Prompt-`EinkModal` (Merken-Checkbox + Import-Ordner-Picker), importiert direkt, oder öffnet read-only.
  Editierbar in Settings → Downloads; dort setzt der Download-Ordner-Picker jetzt zugleich den lokalen Ordner
  (`SettingsViewModel.setBothFolders` als Default; der separate „Gemeinsamer Ordner"-Button ist entfernt).
  **Verifikation:** `detectBookFormat`-Unit-Tests + `:app:assembleDebug` grün, `DownloadDaoSourceIdTest`
  androidTest grün (Emulator). **Noch nicht auf echter arm64-Boox verifiziert** (Soll): EPUB-Ephemeral-Open
  (crengine-`.so` arm64-only) und die tatsächliche „Öffnen mit"-Listung im Boox-Dateimanager.
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
  `PluginMetadata`, `ConfigSchema`/`ConfigField`/`FieldType{TEXT,SECRET,URL,BOOL,NUMBER}`, `PluginAbi`
  (VERSION=4, MIN_SUPPORTED=1) und `ColorPresetSpec`. Es macht `api(project(":source-api"))`, re-exportiert
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
  **kategorisiert**: `PluginCategory{COLOR_PRESET,READER_PRESET,LANGUAGE,UI_PACK,PANEL_MODEL}`
  (plugin-api, ABI `VERSION=4`/`MIN_SUPPORTED=1`, additiv — `PANEL_MODEL` ist seit 2026-06-14 die
  5. Kategorie, der `VERSION`-Bump 2→3 kam mit ihr; 3→4 brachte `FieldType.NUMBER` + generische
  Data-Plugin-Config, s. u.). Manifest-Keys `DATA_CATEGORY`+`DATA_ASSET` (mit
  Legacy-Alias `COLOR_PRESETS`). `PluginHost.discoverDataPlugins(category)` ist die generische
  Discovery; der **geteilte private `scanDataPluginManifests`**-Helfer (über
  `resolveDataPluginManifest`) trägt sie, und beide Metadata-Discovery-Methoden
  (`discoverDataPlugins`/`discoverDataPluginInfos`) delegieren an ihn; `discoverColorPresetPlugins()`
  ist nur noch ein dünner Wrapper darüber (+ `parsePresetSpecs`). Reader-Preset-/Sprach-Plugins (Spec 2)
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
- **Panel-Modell-Plugin (Ist, 2026-06-14, ML-Panel-Detektor):** fünfte data-only Kategorie
  `PANEL_MODEL` (ABI-Bump `VERSION=2`→`3`). Ein PANEL_MODEL-Plugin ist ein **data-only APK**, das ein
  binäres **ONNX-Modell** als Asset shippt (Manifest `DATA_CATEGORY=PANEL_MODEL`,
  `DATA_ASSET=<modell>.onnx`, `ABI_VERSION=3`; seit ABI 4 optional auch `DATA_CONFIG=config.json` für
  konfigurierbare Parameter — s. „Generische Data-Plugin-Config" unten). **Besonderheit gegenüber den anderen data-Kategorien:**
  das Asset ist mehrere MB groß, darf also **nie beim Scan** gelesen werden. Darum hat `plugin-host`
  jetzt eine **Binär-/Metadaten-getrennte** Discovery: `DataPluginInfo` (nur Metadaten, keine
  Asset-Bytes), `PluginHost.discoverDataPluginInfos(category)` (metadata-only, für Listen/UI) und
  `PluginHost.binaryDataPluginBytes(category): ByteArray?` (liest das ONNX **lazy**, nur wenn wirklich
  gebraucht). Der `PanelSourceProvider` (Naht B oben) verbraucht `binaryDataPluginBytes`. Repo-Schicht:
  `PluginKind.PANEL_MODEL`, `pluginKindOf("panel_model")`, `PluginTypeFilter.PANEL_MODELS`. Discovery
  metadata-only in `PluginCatalog`; der Plugins-Tab listet + filtert sie (Label „Panel-Modell", Filter
  „Panel-Modelle"); das Plugin-Info-Modal kennt den neuen Kind. Persistierte Einstellung **`useMlDetection`**
  (`SettingsRepository`, Default **true**, Room-Key `use_ml_detection`, **keine** Migration; Toggle in
  Settings → Comic). Distribution wie die anderen Kategorien über `KomgaReaderPlugins` (`type:panel_model`,
  `abiVersion:3`).
- **Generische Data-Plugin-Config (Ist, 2026-06-17):** ABI-Bump `VERSION=3`→`4` (additiv, MIN_SUPPORTED
  bleibt 1). Neues Manifest-Key `PluginManifestKeys.DATA_CONFIG` (`com.komgareader.plugin.DATA_CONFIG`)
  trägt den Asset-Namen einer `config.json`-Schemadatei (parallel zu `DATA_ASSET`). `DataPluginInfo`
  trägt das neue Feld `configAssetName: String?`. `PluginHost.dataPluginConfigJson(packageName): String?`
  liest das Asset ressourcen-only (derzeit auf `PANEL_MODEL` beschränkt). Neuer `FieldType.NUMBER` in
  `ConfigSchema`/`ConfigField` (mit nullable `min`/`max`/`step: Double?`). Parsing: pure
  `parseConfigSchema(json): ConfigSchema?` in `data/.../plugin/ConfigSchemaParser.kt` (org.json; unbekannte
  Typen werden übersprungen; null bei ungültigem JSON). Persistenz: `SettingsRepository.pluginConfig(pkg,
  key): Flow<String?>` + `setPluginConfig(...)`, Key-Schema `plugincfg:<pkg>:<key>` — **kein neuer
  Room-Table, keine Migration**. UI: Zahnrad-Icon auf PANEL_MODEL-Zeile in `PluginsScreen` (nur wenn Schema
  nicht leer) öffnet `EinkModal` mit dem geteilten `PluginConfigForm`; NUMBER-Felder rendern als diskrete
  `EinkSliderRow` (E-Ink-sicher, kein Material-Slider). `PluginConfigForm` ist geteilt zwischen
  Code-Plugins und Data-Plugins.
- **Font-Plugins (Ist, 2026-06-14, P2):** sechste data-only Kategorie `PluginCategory.FONT` über
  `discoverDataPlugins(FONT)` (additiv; `PluginAbi.VERSION` ist 4, von PANEL_MODEL auf 3 und
  Config-Feature auf 4 gebumpt) — ein **extern installierbarer** APK
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
- **Dokumentsprache + Auto-Silbentrennung (Ist, 2026-06-15):** `ReflowableDocument.contentLanguage(): String`
  (Default `""`, engine-neutral) liefert die EPUB-`dc:language` über das neue JNI `nativeLanguage`
  (wrappt `LVDocView::getLanguage`, gespiegelt an `nativeTitle` in `cr3_bridge.cpp`). Der Roman-Reader löst
  damit den Silbentrennungs-Wert **`"auto"`** über den **reinen** `resolveHyphenationLang(setting, docLang)`
  (`domain/render`, „auto" → normalisierte unterstützte Doc-Sprache {de,en} oder „" = aus; explizite Werte
  unverändert; `SUPPORTED_HYPHENATION` spiegelt `ReflowCss.PATTERN_DICTS`). `NovelReaderViewModel` liest
  `contentLanguage()` **einmal beim Öffnen** und baut die initiale `ReflowConfig` **deterministisch** mit
  bereits aufgelöster Silbentrennung (kein Stale-Read der `combine`-StateFlow → kein Doppel-Relayout/
  E-Ink-Flash). UI: „Automatisch" in beiden Hyphenation-Pickern (Settings + In-Reader), i18n
  `novelHyphenationAuto` (de+en).
  **Erweitert (Ist, 2026-06-15, Sprachauswahl):** Default ist jetzt **`"auto"`**
  (`RoomSettingsRepository`, `?: "auto"`, keine Migration). Die unterstützten Trennsprachen sind **domain-SSOT**:
  `HyphenationLanguages.SUPPORTED` (24 Basis-Codes ar…uk) — `resolveHyphenationLang` nutzt sie statt des alten
  harten `{de,en}`. `ReflowCss.PATTERN_DICTS` (jetzt `internal`) mappt **genau diese Codes** → `.pattern`-Dateiname;
  `CrengineDocumentFactory.HYPH_PATTERNS` ist daraus **abgeleitet** (`PATTERN_DICTS.values`), und ein
  render-Unit-Test (`HyphenationParityTest`) sichert `PATTERN_DICTS.keys == HyphenationLanguages.SUPPORTED`. Die
  **vollen ~24 crengine-`.pattern`** liegen als App-Assets (`app/src/main/assets/hyph/`, Combo-/Pinyin-Dateien
  ausgelassen); `HyphMan::initDictionaries` registriert sie beim Start (native Seite unverändert). UI: geteiltes
  `HyphenationPicker` (Composable, an **beiden** Orten) = Chips **Automatisch/Aus** + Chip **Sprache** → öffnet das
  `HyphenationLanguageModal` (`EinkInfoDialog`, scrollende Liste, `Locale`-lokalisierte Namen, alphabetisch). Reine
  Helfer `hyphenationModeOf(value)` (AUTO/OFF/LANGUAGE) steuert die Chip-Auswahl. i18n `novelHyphenationLanguage`/
  `hyphenationLanguageTitle` (de+en). Eine Trennsprache hinzufügen = 1 Asset + 1 `PATTERN_DICTS`-Eintrag + 1
  `SUPPORTED`-Eintrag, Paritätstest fängt Vergessenes.
- **Runtime-Font-Registrierung (Ist, 2026-06-14, P2):** Plugin-TTFs (data-only Kategorie `FONT`, s. Naht A)
  werden zur **Laufzeit** in den crengine-Font-Manager eingehängt — kein App-Neustart. `domain` bleibt
  engine-frei über `ReflowableDocumentFactory.registerFont(absolutePath): Boolean` (Default no-op in
  `domain/render/Document.kt`). Die crengine-Impl `CrengineDocumentFactory.registerFont` puffert vor dem
  (einmaligen) `nativeInit`-Boot Pfade in `pendingFontPaths` (werden in den **einen** `nativeInit`-Aufruf
  geflusht) und registriert nach dem Boot live via neuem JNI `CrengineNative.nativeAddFont`
  (`render-crengine/.../cr3_bridge.cpp` → `fontMan->RegisterFont`). Ein zweites `nativeInit` ist verboten.
  Verdrahtet in `PluginCatalog` (extrahiert TTF via `PluginHost.extractFontAsset`, ruft `registerFont`).
- **Roman-Wort-Lesezeichen (Ist, 2026-06-15 — Runtime-Verifikation gerätegebunden offen):** Tippe ein
  Wort im Roman-Reader (crengine-Reflow-Pfad), um an genau diesem Wort ein nummeriertes Lesezeichen zu
  setzen/entfernen (liste/umbenennen/löschen/anspringen; Marker auf der Seite gezeichnet). Die Render-Naht
  bekam dafür zwei neue, engine-neutrale Methoden auf `ReflowableDocument`:
  `wordAt(page, x, y): WordHit?` + `rectsFor(page, xpointers): Map<String, IntRect>` (Default no-op in
  `domain/render/Document.kt`; neue engine-neutrale Typen `WordHit`/`IntRect` ebenda). Die crengine-Impl
  `CrengineDocument.wordAt`/`rectsFor` ruft zwei neue JNI `CrengineNative.nativeXPointerAtPoint` /
  `nativeRectsForXPointers` (`render-crengine/.../cr3_bridge.cpp`); das Anspringen nutzt das bestehende
  `goToAnchor`/`seekToAnchor`. **Page-aware (2026-06-16, Desync-Fix):** beide Methoden tragen einen
  `page`-Index; die native Seite wird vor dem Hit-Test per `goToPage(page)` darauf gesetzt — sonst lag
  die native „current page" nach Rück-Navigation **hinter** der angezeigten Seite (VM-`renderPage` cached
  und ruft bei Cache-Hit kein natives `goToPage`), der Tap traf die falsche/keine Seite, der Marker fehlte.
  Der native Hit-Test ist zusätzlich whitespace-tolerant (Retry an kleinen Offsets) und loggt jeden
  Fehl-Schritt (`adb logcat -s cr3bridge`). **Koordinaten-Fix (2026-06-16, auf echter Boox verifiziert):**
  `getNodeByPoint` erwartet **window-/seiten-relative** Koordinaten (seine `windowToDocPoint` mappt selbst
  in Dokument-Koords) — das frühere `y + GetPos()` zählte den Seiten-Offset **doppelt** und gab immer `null`
  („Tap markiert keine Wörter"). Eingabe also rohe `(x, y)`. Der Ausgabe-Rect (aus `getRectEx`, Dokument-
  Koords) wird per Inverse zurück in Window-Koords gerechnet: `x + pageMargin.left`,
  `y - GetPos() + pageMargin.top` (über `getPageMargins()`) — sonst zeichnete der Marker um den Seitenrand
  versetzt. **Lokal-only** (kein Server hat Wort-Lesezeichen): Room-Tabelle
  `novel_bookmark` (`NovelBookmarkEntity`, `NovelBookmarkDao`, domain `NovelBookmarkRepository`,
  `RoomNovelBookmarkRepository`), `AppDatabase` 18 → 19 (`MIGRATION_18_19`) — bewusst **nicht** in der
  Sync-Queue. Die Tap-Verdrahtung läuft über die deklarative `ReaderTapZones`-Naht (A1b): Lesezeichen-Modus
  → `tapZones = null` (der Reader macht den Wort-Hit-Test selbst, wie Comic), sonst `HorizontalThirds`.
  Marker-Stil (`BookmarkMarkerStyle{UNDERLINE,MARGIN,FLAG}`) + **Marker-Farbe (ARGB)** sind seit
  dem Wide-Panel-Umbau (s. u.) **pro Lesezeichen** gespeichert (`NovelBookmark.markerStyle`/`.color`,
  Spalten in `novel_bookmark`); das Setting `bookmark_marker_style` ist nur noch der **Default für
  neue** Lesezeichen (Default **FLAG**). **Ist (2026-06-16, auf echter Boox Go Color 7 Gen2 verifiziert):** Tap im Lesezeichen-Modus
  setzt das Wort-Lesezeichen (per `adb input tap` + `adb logcat -s cr3bridge` bewiesen: `wordAt: HIT` →
  Eintrag „#1 …" in der Liste); die Wort-Auflösung trifft das angetippte Wort, der Marker ist
  margin-korrigiert. Der Koordinaten-Fix oben war der eigentliche „markiert keine Wörter"-Bug.
  **Marker-Fix (2026-06-16, auf echter Boox: gespeichert aber kein Marker gezeichnet):**
  `nativeRectsForXPointers` maß den gespeicherten Start-XPointer per `ptr.getRect` — das gibt für
  eine Einzel-Textposition einen leeren/degenerierten Caret-Rect → `rect.isEmpty()` → Marker still
  übersprungen. Fix: den **Wort-Bereich rekonstruieren** wie `wordAt` (`ldomXRange::getWordRange` +
  `getRectEx`, `getRect` nur als Fallback) → derselbe Rect, den `wordAt` beim Setzen maß; Marker
  zeichnet jetzt. **UI-Swap (2026-06-16):** Inhaltsverzeichnis ist jetzt ein **Chrome-Button** oben
  (`NovelTocPanel`-Modal über `NovelTocList`), Lesezeichen sind ein **Tab im Bottom-Sheet**
  (`NovelBookmarkList` frameless, neben Typografie; `NovelSheetTab{TYPOGRAPHY,BOOKMARKS}`) — getauscht
  gegenüber vorher (TOC-Tab im Sheet, Lesezeichen-Liste als Top-Modal). Das Expanded-Sheet ist ein
  **schmalerer (0.92), kürzerer (0.45) schwebender Karten**-Look (gerundet, Rand-Abstand), der Scrim
  bleibt transparent (Live-Vorschau). **Wide-Panel-Sheet-Umbau (Ist, 2026-06-16, reine UI/UX —
  Runtime gerätegebunden offen):** der Roman-Settings-Sheet ist für breite Panels neu gestaltet.
  (1) **Pro-Lesezeichen Marker-Stil + Farbe:** `NovelBookmark` trägt jetzt `markerStyle: String`
  (Default FLAG) + `color: Int` (ARGB, Default Schwarz); Entity-Spalten + DAO-Batch-Updates,
  `AppDatabase` **19 → 20** (`MIGRATION_19_20`: `ALTER ADD COLUMN` mit @ColumnInfo-`defaultValue`-Parität,
  bestehende Zeilen per `COALESCE`-Backfill aus `bookmark_marker_style` → vorheriger Look erhalten).
  `BookmarkMarkers` zeichnet **pro Lesezeichen** in dessen Stil+Farbe (kein globaler `markerStyleName`
  mehr). Modi sind mittendrin wechselbar, ohne bestehende zu verändern. (2) **Bookmark-Tab** (`NovelBookmarkPanel`,
  ersetzt `NovelBookmarkList`): fixierter Default-Marker-Selektor + Mehrfachauswahl-Leiste (Alle/Anzahl,
  Farbe/Marker/Löschen auf der Auswahl), engere Zeilen mit Farb-Swatch → `EinkColorPicker` (Palette +
  `#RRGGBB`, Schwarz default; Screen besitzt das eine Modal). (3) **Typografie-Tab:** Seitenrand +
  Schriftgröße/Zeilenabstand/Stärke als diskreter `EinkSliderRow` (−/+ + gerasterte Spur + Landmarks;
  Rand-Stufen `NovelSettings.MARGIN_STEPS` 12·20·25·40·50·100 mit Landmarks Schmal/Normal/Breit/Extra),
  Ausrichtung als `SegmentedChoiceRow`, Silbentrennung-Reihenfolge Aus/Auto/Sprache. Schmalere `SheetTabRow`.
  `EinkSliderRow`/`EinkColorPicker` sind neue geteilte `ui/components`-Bausteine. Build grün
  (`:app:assembleDebug`, `:domain:test`); Wort-Tap-Render + Farbe selbst sind arm64-crengine, nur auf
  echter Boox verifizierbar.
- **Geräte-Naht (Ist):** `EinkController` (`domain/eink/EinkController.kt`) kapselt das Gerät:
  `OnyxEinkController` (Boox-SDK, **HW-gated** über `Build.MANUFACTURER`), `NoOpEinkController` als
  Fallback. **Entwicklung crasht nie auf Nicht-Boox-HW.** Trägt `EinkCapabilities`
  (hasEink/canColor/canInvert, `refreshModes: List<EinkModeOption>`, `colorModes: List<EinkModeOption>`;
  leere Liste = Achse nicht unterstützt, UI blendet Sektion aus; `hasHardwareButtons: Boolean` = physische
  Tasten, Onyx=true/NoOp=false) — siehe Big-Picture-Doku zur Geräteklassen-Frage.
- **Screensaver / Standby-Bild (Ist, 2026-06-16):** `EinkController.setScreenSaverImage(absolutePath): Boolean`
  (domain, Default `false`; NoOp inert) setzt das Geräte-Standby-Bild. **Onyx-Impl** nutzt
  `ScreenSaverUtils.setScreenResource(ctx, path, TYPE_SCREENSAVER, true)` (`com.onyx.android.sdk.api.device.screensaver`) —
  eine **normale, nicht-System-App darf das** (PoC + physisch auf echter Boox verifiziert; nicht vom System
  überspielt). **Harte Anforderung:** das Bild muss in **öffentlichem Shared-Storage** liegen (der Onyx-System-Prozess
  läuft als anderer uid) — `filesDir` → „Image file not exists!", `Android/data`-External → EACCES (FUSE); deshalb
  publiziert der `@Singleton ScreenSaverManager` (`app/data`) über **MediaStore/Pictures** und reicht den aufgelösten
  absoluten Pfad durch (`coverBytes`/Custom-Bytes → Bitmap → PNG, eine wiederverwendete Datei). **Feature
  (Ist, 2026-06-16):** Settings → Darstellung → „Bildschirmschoner" (`ScreenSaverMode{OFF,CUSTOM,BOOK_COVER}`,
  Room-Keys `screensaver_mode`/`screensaver_custom_uri`, **keine Migration**, gegated über `capabilities.hasEink`;
  **Default `BOOK_COVER`** (2026-06-18 — der Standby zeigt auf dem E-Ink-Ziel standardmäßig das Werk-Cover; explizites
  OFF wird respektiert, nur ein ungesetzter Wert fällt auf BOOK_COVER). `ScreenSaverManager.applyCached` bricht auf
  Nicht-E-Ink (`!capabilities.hasEink`) früh ab, damit der Default nicht sinnlos ein Cover in die LCD-Galerie schreibt.
  CUSTOM = SAF-gewähltes Bild (persistable URI); BOOK_COVER = beim Öffnen jedes Werks wird dessen Cover als Screensaver
  gesetzt. **Off-load-path über einen WorkManager-Worker (Ist, 2026-06-18):** `ReaderViewModel.init` enqueued nur
  `ScreenSaverScheduler.schedule(sourceId, bookId, viewerMode, format)` (`enqueueUniqueWork(REPLACE)` — das zuletzt
  geöffnete Werk gewinnt); der `@HiltWorker ScreenSaverCoverWorker` ruft den `@Singleton ScreenSaverCoverResolver`, der
  **(1)** das Server-Cover sofort setzt = **Crash-Fallback** (`needsHighResUpgrade`-Policy davor) und **(2)** bei zu
  kleinem Cover (Kurzkante < halbe Schirmkante) auf Hochauflösung upgradet. So blockiert die Cover-Erzeugung den
  Reader-Start nicht mehr; alle Resolver-Calls sind exception-safe + der Worker gibt immer `Result.success()`.
  — **per Typ**: Webtoon = Serien-Cover (kein Upgrade), Paged/Comic/Novel = Werk-Cover. Hochauflösungs-Quelle: die
  **volle erste Seite** via `openPage(0)` (Bild-Werke, native Auflösung) bzw. — wenn nicht streambar (EPUB/whole-file)
  — die **ganze Datei laden + Cover extrahieren** (EPUB-Embedded-Cover `extractEpubCoverImage`, sonst MuPDF `renderFirstPageCover`);
  das fixt auch die Roman-Schärfe (Komga liefert für EPUB nur ein ~195×300-Thumbnail). Komgas Cover-*Thumbnail* ist je
  Serie 200×300…720×1024 → klein = unscharf hochskaliert, daher nur Fallback. Das Bild wird vor dem Publish geschärft + in Kontrast/Sättigung angehoben (`enhanceForEink`), um das
  Weichzeichnen/Entsättigen des Onyx-Standby-Renderers zu kompensieren (kein SDK-Qualitätsregler vorhanden); der
  Standby-Reload-Broadcast wird sofort **und** nach 1,5 s gesendet (async `setScreenResource`-Race → sonst nur erstes Setzen sichtbar). **Nicht per adb visuell verifizierbar** (Onyx-Standby ist
  ein Daydream-Overlay, SurfaceFlinger sieht es nicht); `ScreenSaverManager` end-to-end auf Boox verifiziert
  (`adb logcat -s ScreenSaverManager OnyxEinkController` + Onyx „scanned successfully").
- **Reader-Input (Ist, 2026-06-15 — Hardware-Tasten, manueller Refresh):**
  Zwei additive Erweiterungen der Device-Naht für gerätenahen Reader-Input, alle agnostisch
  (NoOp inert). (1) **Press-Art:** `ButtonEvent` trägt jetzt `press: PressKind {SHORT, LONG}`
  (Default SHORT, quell-kompatibel). `MainActivity` klassifiziert kurze/lange Volume-Drücke über
  den Android-Long-Press-Lifecycle (`startTracking`/`onKeyLongPress`/`onKeyUp`). **Befund + Korrektur
  (2026-06-16, auf echter Boox per `adb logcat -s HwButtons` bewiesen):** die Go Color 7 Gen2 macht
  **echtes Long-Press unmöglich** — ihre Firmware **kollabiert jeden Halt zu einem einzigen Instant-Tap**
  (ein `onKeyDown`+`onKeyUp` 1ms auseinander, `downTime == eventTime`, `repeatCount = 0`, kein zweites
  Event — egal ob 0,1s oder 3s gehalten). Weder `onKeyLongPress` (feuert nie) noch eine Haltedauer-Messung
  sehen je einen Halt; die Onyx-eigene App kann es nur, weil sie als **System-App `/dev/input` direkt liest**.
  Darum löst `MainActivity` die Shortcuts über **Doppel-Druck** aus (zwei diskrete Taps derselben Taste
  innerhalb `doublePressMs = 300ms`): erster Tap = Blättern + Fenster scharf, zweiter Tap = Shortcut
  (`longPress = true`-Pfad). `onKeyLongPress` bleibt als sekundärer Trigger für echte Android-Geräte, die
  ihn liefern. Die `PressKind.LONG`/`volumeButtonEvent(.., longPress)`-Verdrahtung bleibt unverändert —
  „LONG" ist jetzt nur das **Shortcut-Signal**, auf der Boox per Doppel-Druck ausgelöst statt per Halten.
  der pure `volumeButtonEvent(keyCode, longPress)` mappt: kurz → `PAGE_PREV`/`PAGE_NEXT` (Blättern
  unverändert), lang → `VOLUME_UP`/`VOLUME_DOWN` mit `LONG`. Alle drei Reader-VMs ignorieren LONG
  fürs Blättern; der geteilte `ReaderShortcutsViewModel` (eine Stelle, nicht pro-VM) mappt
  `VOLUME_UP,LONG` → **Home** (`homeRequests`-Flow, von `ReaderRoute` an `onHome` gehängt) und
  `VOLUME_DOWN,LONG` → **manueller Full-Refresh**. (2) **Manueller GC-Refresh:** `EinkController.refresh`
  ist auf Onyx jetzt **real** für `RefreshMode.FULL` (`EpdController.applyTransientUpdate(UpdateMode.GC)`
  — `repaintEveryThing` existiert in SDK 1.3.5 **nicht**); `EinkContextController.manualFullRefresh()`
  ist der agnostische Auslöser (NoOp = no-op). **Boox-verifiziert** (Tasten/Refresh auf echter HW;
  `adb logcat -s OnyxEinkController HwButtons`).
- **Frontlight ENTFERNT (Ist, 2026-06-17):** die frühere Helligkeits-Capability (`EinkCapabilities.brightnessRange`,
  `EinkController.setBrightness`/`brightness()`, `BrightnessBar`, `FrontlightHolder`, `SettingsRepository.frontlightLevel`,
  die Rand-Wisch-Streifen in `DefaultReaderScaffold`) ist **vollständig gelöscht**. Grund: auf dem einzigen Zielgerät
  (Go Color 7 Gen2) ist das Frontlight von einer sideloaded App **hardware-unmöglich** zu steuern — onyxsdk-device 1.3.5
  kennt den qcom/`lito`-Chipsatz nicht (`getBrightnessType()==NONE`, jeder Provider/Setter ein No-Op), und der
  sysfs-Knoten `/sys/class/backlight/onyx_bl_br` braucht System-Kontext (App = EACCES, SELinux untrusted_app). Es war
  also reine tote Fläche (Cap lieferte `null` → UI immer versteckt). Eine künftige Boox, deren SDK das Frontlight
  *wirklich* steuert, bekommt die Capability als **additive** Naht-B-Erweiterung neu — kein Kern-Umbau. → [[onyx-frontlight-index-providers]]
- **Reader-Gesten + Tasten-Übersicht (Ist, 2026-06-16, Novel-Reader-Politur):** (4) **System-Gesten-Ausschluss:**
  `ReaderScaffoldState.gestureExclusion` (host-erzwungen, opt-in pro Reader) legt zwei volle Rand-Streifen mit
  `Modifier.systemGestureExclusion()` an → Rand-Wisch geht an den Reader statt System-Zurück (OS-Cap 200dp/Kante,
  reine Markierung ohne `pointerInput` → klaut keine Taps). **Nur Novel-Reader** (`gestureExclusion = true`). Der
  **System-Home-Wisch (von unten) lässt sich NICHT** deaktivieren (OS-Garantie) — nur die Zurück-Kanten.
  **Gerätebefund (2026-06-16, echte Boox):** die Go Color 7 läuft in **3-Button-Android-Nav**
  (`settings get secure navigation_mode == 0`), also gibt es gar keinen Android-Gesten-Nav-Zurück-Wisch —
  `systemGestureExclusion` ist dort **wirkungslos**. Die „Wisch-zurück/von-unten"-Gesten, über die der Nutzer
  klagt, sind **Onyx-eigene System-Gesten** (Boox-proprietär), die **kein** Android-API erreicht; sie sind nur
  in den **Onyx-Systemeinstellungen** abschaltbar. Der Code bleibt (korrekt für echte Android-Gesten-Nav-Geräte). (5)
  **Tasten-Übersicht:** read-only Settings-Sektion `SettingsSectionId.BUTTONS` (`ButtonsSettingsContent`) zeigt die
  zwei Doppel-Druck-Belegungen (Doppel-Lauter→Home, Doppel-Leiser→Full-Refresh), gegated über
  `EinkCapabilities.hasHardwareButtons` (nicht über `displayMode` — Tasten sind physisch). (6) **Bottom-Sheet-Politur:**
  der Sheet-„Scrim" ist ein **transparenter** Tap-Dismisser (kein Abdunkeln → Live-Vorschau der Reflow-Änderungen),
  die zugeklappte Peek-Leiste ist **schwarz/weiß** wie `DefaultReaderOverlay`.
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
- **Panel-Erkennungs-Naht (Ist, 2026-06-14 — externe Lib + ML-Tausch):** Die Comic-Panel-Erkennung
  (geführter Comic-Reader) sitzt **nicht mehr** im gelöschten In-Tree-Modul `:guided-view`, sondern
  in der **veröffentlichten Lib `comic-cutter`** (`io.github.gabriel-graf:comic-cutter-jvm:0.4.0` +
  `comic-cutter-onnx-jvm:0.4.0`, Paket `com.panela.comiccutter.*`; + `com.microsoft.onnxruntime:
  onnxruntime-android` für die ML-Runtime). **0.4.0-Neuerung:** `PanelRect.score: Float` (ML-Konfidenz,
  Default `1.0f`) + `NormRect.score` werden durch `PanelGeometry.normalize` durchgereicht — die
  ML-Quelle befüllt sie, die geometrische Quelle gibt `1.0f`. Die Lib ist der alte guided-view-Detektor
  als Artefakt (identische Klassen `PanelDetector`/`PanelGeometry`/`NormRect`/`PanelRect`/`GuidedNavigator`/
  `GuidedPosition`/`ReadingOrder`/`ReadingDirection`) **plus** ein ML-Stack (`PanelSource`,
  `GeometricPanelSource`, `MlPanelSource`, `MlFilter`, `ModelRunner`, `OnnxModelRunner` —
  Letztere im Unter-Paket `com.panela.comiccutter.onnx`). **Die Naht ist `PanelSource`:** der
  `@Singleton` **`PanelSourceProvider`** (`app/ui/reader/PanelSourceProvider.kt`) liefert die zu
  nutzende `com.panela.comiccutter.PanelSource` — `GeometricPanelSource()` per Default, oder
  `MlPanelSource(OnnxModelRunner(bytes), MlFilter(minConfidence, ...))`, wenn
  `SettingsRepository.useMlDetection` an ist **und** ein `PANEL_MODEL`-Plugin installiert ist
  (`PluginHost.binaryDataPluginBytes`); jeder Fehler (kein Plugin, ONNX-Init) **degradiert sauber**
  auf geometrisch, die gewählte Quelle wird gecacht. **`min_confidence` konfigurierbar (Ist,
  2026-06-17):** `PanelSourceProvider` liest den Wert über `SettingsRepository.pluginConfig(pkg,
  "min_confidence")` via `resolveMinConfidence(stored): Float` (pure, Default `0.55`, Paket
  `com.komgareader.model.panel.yolo`) statt hardcoded; Cache invalidiert bei Wertänderung. Die
  In-App-`configSchemaFor(PANEL_YOLO_PKG)` erzwingt zudem ein eigenes `min_confidence`-NUMBER-Feld
  (`PANEL_MIN_CONFIDENCE_FIELD`, 0.10–0.95, step 0.05, Default 0.55), damit der Slider unabhängig
  von der externen `config.json` des Plugins korrekte Grenzen + Default hat (eine kaputte/fehlende
  Range pinnte ihn sonst am Minimum). NMS/IoU bleiben hardcoded. `ComicReaderViewModel` injiziert den Provider;
  `ComicPageLoader.detect(page, panelSource)` erkennt und **sortiert das Ergebnis in
  Lesereihenfolge** (`ReadingOrder.sort(panels, LEFT_TO_RIGHT)` — die ML-Quelle liefert
  Confidence-Reihenfolge, geometrisch war schon sortiert → idempotent). Alles dahinter
  (`PanelGeometry.normalize`, der Degenerate-Guard `<2 Panels || maxAreaFraction>0.85`,
  `GuidedNavigator`-Stepping, die E-Ink-Dynamik-Verdrahtung) ist **unverändert** — der Reader ist
  agnostisch gegen geometrisch-vs-ML. **Debug-Overlay (Ist, 2026-06-17):** `ComicReaderScreen`
  zeichnet per Panel `#<reading-order-index> <score>` oben links (nur im Debug-Build).
  **Misdetection-Capture-Loop (Ist, 2026-06-17):** `SettingsRepository.misdetectionDir:
  Flow<String?>` (Room-Key `misdetection_dir`, SAF-Tree-URI, keine Migration); ein Capture-Button
  im Comic-Reader-Overlay (nur wenn `misdetectionDir` gesetzt) schreibt die Seite als PNG +
  einen Pixel-Space-Sidecar (`misdetectionSidecarJson`, mllabeltool-Prediction-Format) via
  `MisdetectionWriter` (SAF/`DocumentFile`) in den konfigurierten Ordner. Das PC-seitige
  Relabeling (mllabeltool → Retraining) liegt außerhalb der App.
  **Verhaltens-Vorbehalt:** der geometrische `PanelDetector` der Lib hat einen anderen Algorithmus
  als der alte In-Tree-Detektor (mit Merge-über-Split-Arbitrierung), darum können geometrische
  Panel-*Ergebnisse* leicht von früher abweichen — erwartet, kein Regress.
  **Dependency-Hinweis:** comic-cutter wird in der Entwicklung aus `mavenLocal()` aufgelöst
  (content-gefiltert auf `io.github.gabriel-graf`); die öffentliche JitPack/Maven-Central-Koordinate
  muss vor CI eingesetzt werden (TODO in `settings.gradle.kts`). Das `-jvm`-Artefakt wird direkt
  gelinkt (die KMP-Wurzel publiziert nur jvm/js, kein android) — pures Kotlin/JVM, Android konsumiert
  es; keine `abiFilters`. Dependency-Verification ist „trust-all-except-Onyx", daher keine neuen Pins.
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
- **Reader-Lesepfad: vereinheitlichtes Coil-Page-Model (Ist, aktualisiert 2026-06-17):**
  `ReaderViewModel.loadBook` produziert **einen** Content-Typ `ReaderContent.Pages(pages:
  List<ReaderPageImage>, initialPage)` für beide Quellen-Arten — streamen **und** whole-file. Das
  Page-Model `ReaderPageImage` (`app/data/coil`, sealed) hat zwei Spielarten: **`SourceImage`**
  (gestreamt über die Quellen-Naht → Coil-`SourcePageFetcher` → `openPage`; Komga/Kavita/OPDS-PSE)
  und **`RenderedPageImage`** (whole-file: lokaler Download, lokale PDF/CBR, OPDS ohne PSE — von
  MuPDF gerendert). Coil löst jede Spielart über ihre eigene registrierte `Fetcher.Factory` (per
  konkretem Typ) auf, sodass ein Reader beide über denselben `ImageRequest.data(page)`-Aufruf zeigt.
  **Wahl quellen-agnostisch:** liegt ein lokaler Download vor → whole-file; sonst `source.pages(bookId)`
  — ist das **leer** (kein seitenweises Streaming), ebenfalls whole-file; nur nicht-leeres `pages()` geht
  den Stream-Pfad. **Whole-file rendert der `@Singleton RenderedPageStore`** (`app/data`, hängt nur am
  `DocumentFactory`-Interface von Naht B): öffnet das Dokument **einmal pro Buch** (Ein-Doc-Cache,
  `Mutex`-serialisiert weil MuPDF nicht thread-safe + Coil mehrere Seiten parallel prefetcht), liefert
  `prepare()` (Seitenzahl) + `render()` (Seite→Bitmap); Bytes seam-treu (lokaler Download via
  `LocalBookBytes`, sonst `source.downloadFile`). Der `RenderedPageFetcher` (Coil) gibt die gerenderte
  Seite als `DrawableResult` zurück (kein Re-Encode) — Panel-Detektor (`ComicPageLoader`) **und**
  `AsyncImage` konsumieren sie wie eine Stream-Seite. **Folge — der eigentliche Fix:** der Reader-Host
  dispatcht `ReaderContent.Pages` über `when(ViewerMode)` auf paged/comic/webtoon **auch für
  whole-file/heruntergeladene Werke** — ein lokaler Comic/Webtoon öffnet jetzt im aufgelösten Viewer
  statt immer paged. Der frühere `ReaderContent.Rendered` + der separate `EpubReaderScreen` (nur paged,
  Mode ignoriert) sind **entfernt**; `renderEpubPage`/das Doc-Handle im VM ebenso (Rendering liegt nun
  im `RenderedPageStore`, `release()` am Reader-Ende über den App-Scope). **Verifikation:** `:app`
  compile + `:app:testDebugUnitTest` (inkl. `ReaderViewModelTest` rendered→`Pages`-Pfad,
  `ReaderFilterCoverageTest`) + androidTest-Compile + `assembleDebug` grün. **Runtime gerätegebunden
  (Soll):** MuPDF-Render + Panel-Detektion auf gerenderten Seiten sind arm64-crengine/MuPDF — der
  Comic-/Webtoon-Modus für whole-file/heruntergeladene Werke ist erst auf echter Boox final verifiziert.
  - **OPDS-PSE (Ist, 2026-06-17):** `OpdsSource` liefert jetzt für PSE-fähige Einträge (OPDS Page Streaming
    Extension: `pse:count` + `{pageNumber}`-Vorlage, NS `http://vaemendis.net/opds-pse/ns`) nicht-leere
    `pages()` und streamt einzelne Seiten über `openPage` — **derselbe `Streamed`-Pfad wie Komga, kein
    Voll-Download**. Ohne PSE (EPUB/PDF, Server ohne PSE) bleibt `pages()` leer → whole-file. **Rein
    quell-intern, kein App-/Naht-Edit** (`ReaderViewModel` unverändert). Da `SourceImage`/`loadPageBytes`
    `openPage` nur mit (bookRemoteId, pageNumber) füttern (kein `PageRef.url` durchgereicht), cached
    `OpdsSource` die PSE-Stream-Vorlage pro `remoteId` (`@Volatile`, unveränderliche URL-Vorlagen → keine
    Stale-Sorge; von `pages()` gewärmt, `openPage` kalt-fallback). Platzhalter **0-basiert per Spec**.
    Unit-getestet (Parser + MockWebServer, `OpdsFeedParserTest`/`OpdsSourceTest`).
  - **OPDS-Navigation (Ist, 2026-06-17):** `OpdsSource` versteht **hierarchische** Feeds
    (catalog→series→books) — der Normalfall echter OPDS-Server. Parser liest den `subsection`-Nav-Link
    (`navigationHref`) + matcht den Acquisition-rel per **Prefix** (Komga `…/acquisition`, Kavita
    `…/acquisition/open-access`). `browse()` mappt Nav-Einträge→Series; `books(seriesId)` **folgt** dem
    Subsection-Link auf den Acquisition-Feed der Serie (PSE inklusive). Flat-Katalog bleibt
    rückwärtskompatibel. `remoteId` = stabile OPDS-`<id>`; Buch-Ops lösen URLs aus `browse`/`books`-
    gewärmten Caches (`entriesById`/`seriesAcqFeed`/`bookSeries`). **Live durch `OpdsSource` verifiziert**
    (env-gated `OpdsLiveTest`, `OPDS_LIVE=1`): echtes Komga (`/opds/v1.2/series`) + Kavita
    (`/api/opds/<key>/libraries/1`) navigieren+streamen PSE end-to-end. **Soll:** voller In-App-E2E auf
    Gerät, `browse`-Pagination (nur Seite 0), tiefere Bäume (>1 Nav-Ebene), Komga-OPDS-Serien-Cover.
  - **Prefetch (Ist, 2026-06-17):** der Paged-Reader (`PagedReaderScreen`) wärmt via `prefetchIndices`
    (pure, `ReaderPrefetchTest`) + Coil `enqueue` die nächsten 3 Seiten in den Cache → Streaming-
    Seitenwechsel (OPDS-PSE/Komga) aus Cache statt frischem Roundtrip. Reine Decode-/Cache-Arbeit, keine
    UI-Bewegung → E-Ink-sicher (kein Gating).
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
    Surface bauen + `LocalResolvedSlots.current.readerChrome(state)`); **die vier Reader-Call-Sites bleiben
    unverändert** (PagedReaderScreen/WebtoonReaderScreen/ComicReaderScreen/NovelReaderScreen — `EpubReaderScreen`
    ist mit dem vereinheitlichten Page-Model entfallen; whole-file läuft jetzt über `PagedReaderScreen`/den
    Mode-Dispatch, s. „Reader-Lesepfad").
    `DefaultReaderScaffold(state)` ist der verbatim extrahierte Onyx-Renderer; der innere Overlay-Aufruf bleibt
    über die `overlay`-Region (Komposition). Der E-Ink-Scrim (`readerOverlayScrim`) + die Animation-Gating-Pfade
    bleiben host-erzwungen. Reader-Engines / `Viewer.kt` / die `ReaderChrome.kt`-Helfer
    unangetastet. Swap-Beweis: `app/src/debug/kotlin/com/komgareader/app/ui/reader/ReaderChromeSlotPreview.kt`
    (`AlternativeReaderChrome`: Status-Fuß oben statt unten, Tap-Hints/Start-Hinweis weggelassen — nur
    Debug/Preview).
    - **Bottom-Sheet-Capability (Ist, 2026-06-15):** `ReaderScaffoldState` trägt zusätzlich ein
      **optionales** `bottomSheet: ReaderBottomSheet?` (`:ui-api`, Felder `expanded`/`onExpandedChange`/
      `peekLabel`/`content`; `null` = keins, Default). Die **Mechanik gehört dem Host**: `DefaultReaderScaffold`
      rendert (an *einer* Stelle, neben den Tap-Zonen) über `BoxScope.ReaderBottomSheetLayer`
      (`app/ui/reader/ReaderBottomSheet.kt`) den Aufwärts-Wisch am unteren Rand (nur vertikaler Drag konsumiert
      → Blätter-Taps bleiben durch), den ein-/ausklappbaren Peek-Balken (nur bei `chromeVisible`), den Scrim
      (`readerOverlayScrim`) und den vollbreiten, höhen-gedeckelten Container; der **Reader liefert nur
      `content`**. **E-Ink host-erzwungen** (`LocalEinkMode`): instant auf/zu auf E-Ink, `slide`+`fade` nur auf
      Phone. **Einziger Consumer: der Novel-Reader** — Typografie + TOC als zwei Tabs (`NovelSettingsSheet` mit
      `NovelSheetTab{TYPOGRAPHY,TOC}`, wiederverwendet `NovelTypographyControls` + das extrahierte `NovelTocList`),
      die die zwei zentrierten Modale (`NovelTypoPanel` gelöscht, `NovelTocPanel`→`NovelTocList`) **ersetzen**;
      die zwei alten Top-Icons sind entfernt, Suche + Lesezeichen bleiben. Paged/Comic/Webtoon/Epub übergeben
      `null`. Swap-Beweis: `ReaderBottomSheetPreview.kt` (Peek + Expanded). **Runtime gerätegebunden** (echte
      Wisch-Geste + crengine-Novel-Render arm64) — auf Boox noch zu verifizieren (Soll).
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
- `render-core`, `eink-onyx` hängen nicht von der UI ab. (Das frühere `guided-view` ist entfernt —
  Panel-Erkennung ist jetzt die externe Lib `comic-cutter`, verdrahtet in `app` über `PanelSourceProvider`.)
- `app` ist die imperative Shell (DI, ViewModels, Viewer-Host) — die einzige Schicht, die alles verdrahtet.
- Wenn ein Feature einen neuen Cross-Modul-Import nötig zu machen scheint: erst prüfen, ob es nicht
  hinter eine bestehende Naht gehört.

## Bezug

Gehört zu [[project-komga-eink-reader]]. Erweiterungs-Kochrezept: `source-extensibility.md`.
