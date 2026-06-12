# Big Picture: Vision, Ziele, Gos & Nogos

Diese Datei hält das **große Ganze** sichtbar, damit eine bequeme MVP-Abkürzung nicht
ein erklärtes Langzeitziel zumauert. Sie ist die Antwort auf einen real beobachteten
Fehler: Eine Entscheidung, die „minimal und konsistent mit dem Bestehenden" aussieht
(z. B. ein binäres `EINK/SMARTPHONE`), ist *nur dann* richtig, wenn man die Ziele kennt,
gegen die sie laufen könnte. Wer ein Feature plant, prüft es gegen die Ziele unten —
nicht nur gegen den heutigen Code.

## Die Vision (ein Satz)

Ein **quellen- und geräte-agnostischer** Reader, der mehrere Lesemodi, mehrere Server,
mehrere Gerätearten (E-Ink **und** Nicht-E-Ink) und nutzer-installierbare Plugins in
**einer** App vereint — alle Variabilität hinter den zwei Nähten (`architecture-seams.md`).
Die **Oberfläche selbst ist modular**: alles um die Reader-Engines herum (Chrome) ist bis
zur ganzen UI auswechselbar, damit die Community eigene Looks bauen kann (`ui-modularity` unten).

## Die Langzeit-Ziele (immer mitdenken, auch wenn die Aufgabe sie nicht nennt)

| Ziel | Was es bedeutet | Was es heute schon verbaut, wenn ignoriert |
|---|---|---|
| **Multi-Reader** | paged Comic, Webtoon, geführter Comic, Roman-Reflow — und weitere | Reader-Logik N-fach kopiert statt auf gemeinsamer Naht (`shared-structure-before-variants.md`) |
| **Multi-Server** | Komga, OPDS, Kavita, mehrere gleichzeitig | App an *eine* Quelle gekoppelt (`source-agnostic-integration.md`) |
| **Multi-Device** | mono E-Ink, **Farb-E-Ink (Kaleido)**, LCD-Phone/-Tablet | binäre Geräte-Annahme; „E-Ink-Look" global erzwungen statt geräteklassen-abhängig |
| **Plugins** | nutzer-installierbar: (a) Quellen, (b) UI-Views, (c) Color-Presets | quellenspezifische Annahmen in Interfaces gebacken; kein stabiler Plugin-Vertrag |
| **Modulare UI** | jedes UI-Element (Overlay, Header, Buttons, Navigation, Tiles, Settings) einzeln austausch-/neubaubar, bis hin zur **komplett ersetzten Oberfläche** — die Reader-Engines bleiben Core | UI als monolithischer Compose-Baum mit hartverdrahteten Chrome-Bausteinen; keine adressierbaren Slots → Community kann nichts ersetzen ohne Fork (`ui-modularity` unten) |
| **Color-Filter** | Kaleido-Sättigung/Kontrast vor Anzeige, Presets | Filter nur für eine Quelle / nur Cover statt über die Naht für alle |

**Regel:** Wenn eine „minimale" Lösung eines dieser Ziele zumauert (statt es bloß noch
nicht zu bauen), ist sie **nicht** minimal — sie ist Schulden. Die agnostische/erweiterbare
Variante ist fast immer **gleich groß** (siehe `source-agnostic-integration.md`). YAGNI
heißt „bau es noch nicht", nicht „mach es unmöglich".

## Gos

- Variabilität **hinter** eine Naht legen (neue Quelle/Engine/Gerät = neue Impl, kein Kern-Umbau).
- Gemeinsames **vor** der N-ten Variante extrahieren (`shared-structure-before-variants.md`).
- Domain rein halten (kein Android/Netz/Quelle); konkrete Typen nur in der Wiring-Schicht.
- Generische, quellen-/geräte-neutrale Namen im Domain-Modell.
- Jede Animation über die Geräteklasse gaten (`animation-gating.md`).
- Offline-first: lokaler Fortschritt (`dirty`) → Sync-Queue.

## Nogos

- Konkrete Quelle/`*SourceProvider`/Auth-Schema in ViewModel/UI (`source-agnostic-integration.md`).
- Binäre Geräte-Annahme als *Endzustand* (siehe Geräteklassen unten).
- Quellenspezifische Annahmen ins `MediaSource`-Interface backen (verbaut Plugins).
- Beliebigen Plugin-Code mit Host-Rechten / arbiträre Plugin-Compose-UI (siehe Plugins unten).
- Chrome (Overlay/Header/Buttons/Navigation/Tiles/Settings) hart in den Compose-Baum verdrahten, statt hinter adressierbare, austauschbare Grenzen (`ui-modularity` unten).
- Verbindliche Doku, die nicht-existierende Typen als real darstellt (`docs-match-code`).
- „Später agnostisch/erweiterbar machen" als Rechtfertigung für eine zumauernde Abkürzung.

## Geräteklassen sind nicht binär

Das Zielspektrum ist **mono E-Ink · Farb-E-Ink (Boox Kaleido) · LCD-Phone/-Tablet**. Diese
trennen auf zwei **orthogonalen** Achsen, die ein einzelnes `isEink`-Flag nicht trägt:

| Klasse | Bewegung erlaubt | Akzentfarbe erlaubt |
|---|---|---|
| mono E-Ink | nein | nein |
| Farb-E-Ink (Kaleido) | nein | **nein** (UI-Akzent = Schwarz; Cover-Farbe via Color-Filter) |
| LCD-Phone/-Tablet | ja | ja |

> **User-Entscheidung (2026-06-10, auf echter Go Color 7 verifiziert):** der **E-Ink-Modus ist beim
> UI-Akzent monochrom** — auch auf Kaleido **Schwarz** (gedämpftes Indigo wirkte falsch). `DisplayMode.EINK`
> setzt `allowsAccentColor = false`, unabhängig von `capabilities.canColor`. Die **Farbe der Cover/Seiten**
> auf Kaleido regelt weiterhin der Color-Filter (separat vom UI-Akzent). Das **Modell** behält beide
> orthogonalen Achsen (kein binäres `isEink`): ein künftiges optionales *Farb-E-Ink-Profil* könnte
> `allowsAccentColor = true` setzen — nur der heutige E-Ink-Modus tut es nicht.

**Richtung (Soll):** Geräte-Verhalten aus einem `DisplayBehavior`-Wertobjekt mit getrennten
Flags (`allowsMotion`, `allowsAccentColor`) ableiten, gespeist aus `EinkController.capabilities`
(heute ein vorhandenes, aber **ungenutztes** Feld) plus optionalem User-Override. `LocalEinkMode`
bleibt als dünne abgeleitete Brücke (`!allowsMotion`), damit bestehende Consumer unberührt
bleiben. **Nogo:** neue Features so bauen, dass sie ein binäres `EINK/SMARTPHONE` als Endzustand
zementieren — das blockiert Farb-E-Ink + die „auf dem Tablet darf's hübsch sein"-Anforderung.
Die „E-Ink-Designsprache" (`eink-design-language.md`) ist auf mono/Farb-E-Ink Pflicht, auf LCD
aber gerade **nicht** das Ziel — das Theme muss perspektivisch auf die Geräteklasse verzweigen,
nicht nur die Animation.

> Wenn eine Aufgabe „Display-Modus" oder Geräte-Verhalten berührt: nicht den binären Ist-Stand
> als gegeben nehmen — die Lösung muss mindestens die drei Klassen *aufnehmen können*.

## Plugins — festgelegte Architektur-Entscheidungen (Referenz, Phase 4)

**Update (2026-06-11): Loader + erstes APK-Plugin sind gebaut** — die unten beschriebenen
Entscheidungen 1 und 3 sind jetzt real, nicht mehr nur festgelegter Plan.

1. **Vertrag in eigenem Modul `plugin-api`** (pure JVM/Kotlin) — **Ist: gebaut.** `plugin-api`
   (0.1.0, mavenLocal) enthält `SourcePlugin`, `PluginMetadata`, `ConfigSchema`/`ConfigField`/
   `FieldType`, `PluginAbi` (VERSION=1) und `ColorPresetSpec`. Macht `api(project(":source-api"))`
   → re-exportiert die Naht-A-Typen. **Distribution (Ist, 2026-06-11): ein einzelnes geshadetes
   `:plugin-sdk`** (`com.komgareader:plugin-sdk:0.1.0`, Shadow ohne Relocation) bündelt
   plugin-api+source-api+domain (nur `com.komgareader.**`, keine Fremd-Libs, saubere POM); Plugins
   linken nur dieses eine Artefakt `compileOnly`. Die separaten Modul-Publishes sind entfallen.
2. **ABI-Gate als zwei Integer** (`VERSION`, `MIN_SUPPORTED`), nicht semver-Strings. Plugin-Manifest
   nennt `abiVersion`; außerhalb der Spanne → „inkompatibel", nie instanziiert. Neue Capability =
   neues **optionales** Interface (wie `ContainerSource`), additiv, ohne ABI-Bump.
3. **Paket = separate APK**, via `PackageManager` (Metadata-Flag) entdeckt, via
   `PathClassLoader(sourceDir, nativeLibDir, hostClassLoader)` geladen (Parent = Host-Classloader, sonst
   `ClassCastException` durch doppelte Interface-Klassen). Die OS macht Signatur/Integrität/Update/`.so`-
   Extraktion — **kein** `DexClassLoader` heruntergeladener `.dex` (Security/Play-Policy). Modul `plugin-host`
   — **Ist: gebaut + E2E-verifiziert (2026-06-11).** `PluginHost`, `AbiGate`, `DiscoveredPlugin`,
   `PluginManifestKeys`, `PluginSignature`, `PluginConfigHash` existieren. **E2E-Härtungen:** Host braucht
   `QUERY_ALL_PACKAGES` (Paket-Visibility ab API 30); `PathClassLoader` statt `createPackageContext` (der lädt
   bei Fremdpaketen nur die primäre `classes.dex` → Multidex-Entry-Klasse nicht gefunden). **Trust-Entscheidung
   (2026-06-11): TOFU-Signatur-Pinning** — das Trust-Gate ist der Cert-SHA-256-Pin (beim Erst-Hinzufügen vom
   Nutzer bestätigt, in `ServerConfig.extras["__sig"]` gespeichert). Kein Cert-Pin → kein Laden.
4. **Identität:** `packageName` = der Installierbare; `SourceId.of(name, PLUGIN, "$packageName/$configHash")`
   = der Inhalts-Namespace pro Quelle. Jeder DB-Satz trägt `sourceId` → uninstall fällt sauber auf
   `StubSource` zurück, kein Schema-Change.
5. **Die drei Typen sind unterschiedlich schwer:**
   - **(c) Color-Presets — Ist: data-only APK-Plugin gebaut + E2E-grün (2026-06-11, P1):**
     `ColorPresetSpec` + `PluginAbi` in `plugin-api`; `ColorPresetImporter` in `data` clampt auf
     `ColorProfile`-Konstanten. Ein Preset-Plugin ist ein **data-only APK** (Manifest-Metadata
     `COLOR_PRESETS`=Asset-Name + `ABI_VERSION`, `assets/*.json` = Liste `ColorPresetSpec`, **kein
     Code**). `PluginHost.discoverColorPresetPlugins()` liest das Asset via `createPackageContext(pkg, 0)`
     (**Flags 0 = nur Ressourcen, kein Classloader/TOFU/Multidex**), `parsePresetSpecs` (org.json) →
     `ColorPresetImporter.toProfileOrNull` → `color_profiles` (`builtIn=false`, `pluginPackage=pkg`,
     gesperrt). Verwaltung im **Plugins-Tab** (`PluginsScreen`/`PluginsViewModel`): ⚙ importiert/entfernt
     Presets, 🗑 deinstalliert das APK (OS-Intent `ACTION_DELETE`), Cleanup per Re-Scan beim Tab-`onResume`
     (`planPluginPrune` → `deleteByPluginPackage`; aktiver Zeiger fällt auf `OFF`). `color_profiles.pluginPackage`
     (nullable, Migration 15→16). Die **bespoke JSON-Datei-Import-UI ist entfernt**.
     **Repo-Browser gebaut + E2E-grün (Slice P2, 2026-06-11):** das Screen-`+` öffnet `RepoBrowserScreen`
     (gepushte Route). Ein offizielles Default-Repo (Konstante `PluginRepoDefaults.OFFICIAL_URL` + Settings-
     Toggle, abschaltbar) + vom Nutzer hinzugefügte Repo-URLs (`plugin_repos`, Migration 16→17). Jedes Repo
     liefert ein `repo.json` (`name` + Liste {packageName,name,type,abiVersion,versionCode,apkUrl,**fingerprint**}),
     geladen via `PluginRepoClient` (OkHttp), gemergt (dedup nach höchster versionCode), je Eintrag
     `installState` (NOT_INSTALLED/INSTALLED/UPDATE_AVAILABLE) + ABI-Gate. Install = APK herunterladen →
     **Cert-SHA-256 gegen den Index-`fingerprint` verifizieren** (`PluginInstaller`, reuse `PluginSignature`) →
     nur bei Match `PackageInstaller`-Session (OS-Dialog). Mismatch = harter Abbruch, Datei gelöscht, nie
     installiert. Die reinen Parse-/Merge-/Version-/Fingerprint-Funktionen liegen in `:data` (unit-getestet),
     `domain` bleibt netzfrei. **Soll (späterer Slice):** Auto-Refresh/Update-Badge, Plugin-Icons, Signierung
     des Index selbst.
   - **(a) Quellen — Ist: erstes APK-Plugin gebaut (Kavita, 2026-06-11):** `SourcePlugin` liefert
     `BrowsableSource`-Impls → `PluginHost.sourceFor(...)` → `SourceRegistration` → `SourceManager`.
     Kavita-Quelle (`plugin/komga-kavita-source/`, separates Git-Repo, gitignored) ist das erste
     APK-Plugin (implementiert `BrowsableSource`+`SyncingSource`, linkt `plugin-api` als `compileOnly`).
     E2E gegen Live-Kavita noch offen (separater Task).
   - **(b) UI-Views — riskant, ZULETZT, eingeschränkt:** **kein** beliebiges Compose (Compiler-Kopplung,
     Crash reißt Host mit, Host-Rechte, E-Ink-Invarianten nicht erzwingbar). Stattdessen **deklarativ**:
     Plugin liefert eine *Beschreibung* (Tap-Zonen→Aktion, Panel-Strategie wie der pure `guided-view`),
     der **Host** rendert + steuert Refresh. Voraussetzung: erst die `Viewer`-Naht extrahieren
     (`shared-structure-before-variants.md`), die UI-Plugin ist dann deren 5. Impl, keine Parallel-Linie.

## ui-modularity — die ganze Oberfläche ist auswechselbar (Soll, Richtung — noch nicht gebaut)

**Das Versprechen:** Über den Reader-Engines soll **jedes** UI-Element einzeln austauschbar,
neu baubar oder ganz ersetzbar sein — bis hin zu einer **komplett von der Community gebauten
Oberfläche**. Nicht nur Reader als Plugin (`Viewer`-Naht, Plugins (b) oben), sondern das ganze
*Chrome* drumherum: Overlays, Header/Top-Bar, Action-Buttons, Navigation/Menubar, Bibliotheks-Tiles,
Settings-Screens, Dialog-Look. Ziel ist eine Architektur, in der ein „UI-Pack" alle diese Teile
liefern kann, ohne den Kern anzufassen — gleiches Naht-Prinzip wie bei Quellen und Geräten, nur
auf die Präsentation angewandt.

### Core bleibt, Chrome wird austauschbar

Die Trennlinie ist die zentrale Design-Entscheidung dieses Ziels:

| Schicht | Beispiele | Status |
|---|---|---|
| **Core (bleibt)** | Reader-Engines (paged/webtoon/comic/novel), die `Viewer`-Naht, der `RefreshScheduler`, der Lese-/Sync-Pfad, die Naht-A/B-Verträge | nie durch ein UI-Plugin ersetzbar — sie sind die Lese- und E-Ink-Garantie |
| **Chrome (austauschbar)** | Overlays, Header, Buttons, Navigation, Tiles, Settings, Dialog-Rahmen, das Theme | soll hinter adressierbaren Slots/Regionen liegen, je einzeln ersetzbar; in der Summe = ganze UI |

Die Reader sind **Core, weil** sie die Render-/Refresh-Korrektheit tragen (Naht B, E-Ink-Invarianten).
Ein Community-UI darf den Rahmen neu erfinden, aber nicht die Engine, die Pixel auf das E-Ink-Panel bringt.

### Richtung (wie das gebaut werden soll, sobald es dran ist)

- **Adressierbare UI-Slots statt Monolith.** Chrome wird so zerlegt, dass jede Region (Header,
  Overlay, Button-Leiste, Tile, Settings-Block, …) einen **stabilen, benannten Einhängepunkt** hat.
  Ein UI-Pack füllt einen, mehrere oder *alle* Slots. Default-Pack = das mitgelieferte Onyx-Look-UI;
  fehlt ein Slot im Community-Pack, fällt er sauber auf den Default zurück (analog `StubSource`).
- **Deklarativ, nicht arbiträrer Compose-Code** — dieselbe Entscheidung wie Plugins (b): kein
  beliebiges Plugin-Compose mit Host-Rechten (Compiler-Kopplung, Crash reißt Host mit,
  E-Ink-Invarianten nicht erzwingbar). Das Pack liefert eine **Beschreibung** (Slot→Inhalt,
  Layout, Tap-Zonen→Aktion, Style-Token), der **Host** rendert und steuert Refresh. So bleiben die
  E-Ink-Invarianten (`eink-design-language.md`, `animation-gating.md`) **vom Host erzwungen**, egal
  was das Pack will — Bewegung/Akzentfarbe weiter über die Geräteklasse gegatet, nicht vom Pack.
- **Eigener Vertrag, eingefroren wie die anderen Nähte.** Der Slot-/Theme-Vertrag gehört in ein
  dünnes API-Modul (Kandidat: neben `plugin-api`), nicht in `domain`/`app`. Stabil halten, additiv
  erweitern (neuer Slot = optional), damit ein Pack nicht bei jeder App-Version bricht.
- **Theme zuerst, Layout danach.** Das billigste, risikoärmste Stück ist das **Theme/Token-Pack**
  (Farben, Radius, Border-Stärke, Icon-Set — wie Color-Presets rein deklarativ). Das tatsächliche
  *Umbauen* von Layout/Slots ist das schwere Ende und kommt zuletzt — analog zur Plugin-Reihenfolge
  (c)→(a)→(b).

### Die drei Schichten + der Shell-Pack (die Form-Faktor-Naht) — Shell-Pack gebaut (2026-06-12)

Die modulare UI staffelt sich in **drei Schichten**, jede eine eigene Naht mit Default + Built-in-Varianten:

| Schicht | Was sie tauscht | Trigger der Auswahl | Status |
|---|---|---|---|
| **Theme-Pack** (`UiPack`) | Look: Farbe/Token/Typo/Shapes | Geräteklasse (`DisplayBehavior`) | **gebaut** (Mono/Kaleido/Lcd · **Aurora** = Modern-Mobile-LCD-Look, Phase 1) |
| **Shell-Pack** (`AppShellState`/`DeclarativeShell`+`ShellDescriptor`) | **das ganze Layout-Skelett**: Nav-Ort (Bottom-Bar/Side-Rail/Drawer), Anordnung, Baum | **Form-Faktor** (Bildschirmgröße), orthogonal zur Geräteklasse | **gebaut** (L1: eine deskriptor-getriebene `DeclarativeShell` statt zwei bespoke Built-ins, 2026-06-12) |
| **Region-Slots** (`UiSlotPack`) | einzelne Chrome-Regionen, die ein Shell-Pack platziert: header/homeHeader/overlay/tiles/settings/dialog — plus das Vollbild-Detail-Gerüst `detail` und das ganze Reader-Gerüst `readerChrome` | vom aktiven Shell-Pack gewählt | **sechs Chrome-Regionen + `detail` + `readerChrome` gebaut** (header (mit optionaler Such-Capability `HeaderSearch`)+homeHeader+dialog+settings+tiles+overlay+detail+readerChrome; D1 vollständig: SeriesDetail+GroupBrowse+CollectionDetail; C1: ganzes `ReaderScaffold` hinter `ReaderChromeSlot`, Naht B/`Viewer` bleibt draußen) |

**Warum der Shell-Pack die neue oberste Naht ist (User-Entscheidung 2026-06-12):** Region-Slots sitzen
*in* einem festen Skelett — sie können die Bottom-Bar restylen, aber nicht zu einem Drawer/Side-Rail
machen. Ein echter **Phone-Formfaktor** unterscheidet sich aber im **Skelett selbst** (Nav woanders,
andere Anordnung, Buttons an anderen Orten). Darum: eine Schicht **über** den Regionen, die den ganzen
Layout-Baum besitzt. Der **Core** liefert genau **eine Capability-Surface** `AppShellState`; der
**Shell-Pack** ordnet frei an. Default-Shell = heutiges E-Ink/Tablet-Bottom-Bar-Layout (aus `HomeScreen`
extrahiert). Phone-Shell = zweites Built-in (Drawer/anders), beweist den Skelett-Tausch — exakt wie
Mono/Kaleido/Lcd den Theme-Tausch beweisen. Es ist das **`homeHeader`-Muster eine Ebene höher**:
`DefaultHomeHeader(state)` → `DeclarativeShell(descriptor).Render(appShellState)`. Form-Faktor (Shell) und Geräteklasse (Theme)
bleiben **orthogonale Achsen** (konsistent mit „Geräteklassen sind nicht binär"): eine Boox = großer
E-Ink → Tablet-Shell + Mono-Theme; ein Phone = klein-LCD → Phone-Shell + Lcd-Theme.

**Bauweg 1 jetzt, 3 als Endform — und 1 wächst sauber zu 3:** Wie bei Quellen-Plugins und Theme gibt es
zwei Vertragsformen, gestaffelt vom Einfachen zum Schweren:

1. **In-Tree-Compose-Shell-Packs (Vorstufe):** der Shell-Pack ist eine `@Composable (AppShellState) -> Unit`
   und ordnet mit voller Compose-Freiheit an — wie `DefaultHomeHeader`. Die ursprünglichen zwei bespoke
   Built-ins (Default-/Phone-Shell) waren genau das.
3. **Deklarativer Shell-Pack (Ist in-tree seit L1; externe APK-Packs sind L2):** der Pack liefert **kein**
   arbiträres Compose mit Host-Rechten (Crash/E-Ink-Invarianten, dieselbe Regel wie Plugins (b)), sondern
   einen **Daten-Deskriptor** (Nav-Stil-Enum, welche Destination an welchem Anker, welche Region wohin).
   Der Host shippt **eine** `DeclarativeShell` — selbst ein Ansatz-1-Compose-Built-in, parametrisiert per
   Deskriptor — und rendert **dieselben** `AppShellState`-Stücke an die genannten Plätze. **L1 (Ist, 2026-06-12)
   hat genau das in-tree gebaut:** `DeclarativeShell(ShellDescriptor)` ersetzt die zwei bespoke Built-ins;
   `descriptorFor(formFactor)` liefert den Deskriptor, die Registry treibt damit die echte App. **L2 (Ist,
   2026-06-12) schließt den Kreis:** ein **externer** data-only UI-Pack-APK liefert den `ShellDescriptor`
   (`shell.navStyle`) extern (`ShellPackRegistry.forFormFactor(ff, override)`, Override schlägt Form-Faktor) —
   derselbe Renderer; offen bleibt nur der ABI-Freeze für künftige **Code**-Packs.

**Der 1→3-Pfad ist Evolution, kein Rewrite:** beide Formen konsumieren **dieselbe** `AppShellState`. Form 3
ist nur Form 1, bei der die Anordnungs-Logik von Daten statt Code getrieben ist; In-Tree-Packs (1) sind
das reichere Superset, externe Deskriptor-Packs (3) die beschränkte Teilmenge derselben Surface. **Die eine
Bedingung, die das trägt** (sonst bricht die Evolution): `AppShellState` muss von Anfang an ein **Satz
benannter, host-gebauter, einzeln renderbarer Stücke** sein (`nav`, `content`, `header`, `status`, `search`,
`filter`, `actions`, `overlay` …) mit **endlichem Anordnungs-Vokabular** (Nav-Stil-Enum, Anker-Positionen) —
**nie** ein opaker „hier ist ein Content-Lambda, mach was"-Blob. Opak = ein Deskriptor kann es nie nachbauen.
Diese Bedingung ist **keine Mehrarbeit**: es ist dieselbe „UI neu, Kernlogik gleich"-Disziplin wie bei
`homeHeader` (`menu`/`actions` sind host-gebaute Felder, das Pack platziert nur), nur schon zur **Design-Zeit**
angewandt — wodurch auch die In-Tree-Compose-Packs automatisch deskriptor-ausdrückbar bleiben.

### Weg zur kompletten Modularität — was noch fehlt (Stand 2026-06-12)

Das **Endziel ist die *ganze* Oberfläche modular** (eine komplett von der Community gebaute UI). Was
bisher gebaut ist, sind **erste Nähte**, kein abgeschlossener Zustand — der Shell-Pack deckt nur das
**Home-Skelett**. Alle Punkte unten bleiben **offen, bis die komplette UI modular ist**:

**Gebaut (Stand 2026-06-12):**
- Theme-Pack (`UiPack`, Look nach Geräteklasse) · **sechs Chrome-Region-Slots** **header** + **homeHeader**
  + **dialog** (der eine Onyx-Dialog `EinkModal` hinter `DialogSlot`/`DialogState`) + **settings**
  (das Settings-Skelett hinter `SettingsSlot`/`SettingsState`) + **tiles**
  (die Serien-Kachel `SeriesTile` hinter `TilesSlot`/`TileState`, in Bibliothek + Gruppen) + **overlay**
  (die togglebare Reader-Chrome-Menüleiste hinter `OverlaySlot`/`ReaderOverlayState`) · die siebte
  Region **detail** (das Vollbild-Detail-Gerüst hinter `DetailSlot`/`DetailScaffoldState`, in **allen drei**
  Detail-Routen `SeriesDetail` + `GroupBrowse` + `CollectionDetail` — Sub-Projekt **D1 vollständig**, D1.1
  brachte CollectionDetail rein und baute dafür die `header`-Region um eine optionale Such-Capability
  `HeaderSearch` aus) · die achte Region **readerChrome** (das **ganze Reader-Gerüst** `ReaderScaffold`
  hinter `ReaderChromeSlot`/`ReaderScaffoldState` — Sub-Projekt **C1**; die Reader-Engines + die
  `Viewer`-Naht (Naht B) bleiben Core/draußen, die Surface trägt nur die abgeleiteten
  `chromeVisible`/`onToggleChrome`, nicht den `Viewer`) · **Shell-Pack** für das Home-Skelett
  (`AppShellState`/`DeclarativeShell`+`ShellDescriptor`, Form-Faktor; L1: eine deskriptor-getriebene
  `DeclarativeShell` statt zwei bespoke Built-ins) · **Icon-Pack-Infra** (`I1`): `AppIcons.*`
  delegiert über `ActiveIconPack`/`IconKey` ans aktive Icon-Pack (Default = `DefaultIconPack`, die heutige
  Lucide-Map) — ein Pack ersetzt Glyphen app-weit ohne Call-Site-Änderung (`app/ui/icons/IconPack.kt`).
  Bewusst **prozess-global** statt `CompositionLocal`, weil `AppIcons.*` auch außerhalb von Composition
  gelesen wird (Datenklassen-Felder/Default-Args). Eigene Naht, getrennt vom Theme-`UiPack`.

**Noch offen für „komplette UI modular":**
- Die Chrome-Region-Slot-Reihe (sechs) + die `detail`-Region (D1 vollständig: alle drei Detail-Routen
  modular) + die `readerChrome`-Region (C1: ganzes `ReaderScaffold`-Gerüst) sind gebaut. `nav` ist
  **kein** Region-Slot — das Nav-Skelett gehört dem Shell-Pack. **A1** (`ui-api`-Modul) **und A1b**
  (Reader-Chrome **deklarativ**: `ReaderTapZones`-Deskriptor statt bespoke `tapModifier`) sind **gebaut**
  (Ist, 2026-06-12). Eine eigene `member`-tiles-Region (Collection-Member-Kachel) bleibt späteres YAGNI.
- **Andere Vollbild-Routen:** das Detail-**Gerüst** ist über `detail` swappable für **alle drei**
  Detail-Routen (`SeriesDetail` + `GroupBrowse` + `CollectionDetail`, D1 vollständig). Es ist aber erst das
  *Gerüst* — die *Hero/Grid-Anordnung im Body* (und CollectionDetails `MemberTile`) bleibt Screen-Eigentum
  bis zur späteren `DetailShell`-Stufe (Hero/Grid als arrangierbare Stücke, Master-Detail auf Tablet).
- **Reader-Chrome modular:** die Reader-**Engines** bleiben Core (Render/Refresh/E-Ink-Garantie, Naht B);
  das *Chrome*-**Gerüst** drumherum (Overlay, Chrome-Buttons, `ReaderScaffold`) ist über die `readerChrome`-
  Region (C1) **austauschbar** **und seit A1b deklarativ** (Tap-Zonen als `ReaderTapZones`-Daten-Deskriptor
  statt opakem `tapModifier`; Geometrie host-eigen, Aktion pro Zone als Daten; Comic opt-out via `null`). An
  diese deklarative Form hängt sich die externe UI-Plugin-Form (Plugins (b)) mit L1/L2 an (Enum-Aktionen + Lader).
- **Icon-Pack extern (Ist, L2, 2026-06-12):** die Icon-Stack-Infra (I1) liegt im Modul `:ui-api` (A1,
  `com.komgareader.ui.icons` — `IconKey`/`IconPack`/`DefaultIconPack`/`ActiveIconPack`/`AppIcons`/
  `LucideIcons`, `tools/icons`-Generator schreibt dorthin); der **externe Icon-Pack als installierbarer
  Pack** ist mit **L2 gebaut** — die `icons`-Sektion eines data-only UI-Pack-APKs (Kategorie `UI_PACK`)
  remappt IconKey→IconKey **unter den bestehenden Glyphen** (`UiPackSpec.toIconPack` → `ActiveIconPack.current`
  per `LaunchedEffect` in `MainActivity`). **I1-Limit dokumentiert:** prozess-global, nicht recompose-reaktiv
  — beim App-Start mit persistiertem Pack greift es vor der ersten Composition; ein **live** gewechselter
  Pack greift erst nach Recompose (Tab-Wechsel) bzw. Neustart. Runtime-SVG-Import bleibt YAGNI.
  `IconKey`/`IconPack` werden mit dem ui-api-Code-ABI-Freeze (künftige Code-Packs) mit-eingefroren — nicht Teil
  von L2 (data-Packs linken kein ui-api).
- **`ui-api`-Modul (Ist, A1, 2026-06-12):** der Slot-/Shell-/Theme-/Icon-**Vertrag** liegt **gebaut** im
  eigenen Modul `:ui-api` (`com.komgareader.ui.*`, android-library Compose, DAG `domain → ui-api → app`) —
  das UI-Gegenstück zu `source-api`. Es trägt die Capability-Surfaces, Slot-typealias, Pack-Interfaces,
  reine Resolve-Typen, CompositionLocals **und** die entkoppelten Built-ins (Theme-Packs, Icon-Stack); die
  **gekoppelten Default-Renderer** (Onyx-Look an app-i18n/-Komponenten: `DefaultSlots`/`DefaultHeader`/
  `DeclarativeShell`/`ShellPackRegistry`/`buildSettingsSections`, `Theme.kt`-Host) bleiben in `:app`.
  **Noch nicht eingefroren** (kein ABI-Gate): das Einfrieren + die `api()`-Re-Exportierung (wie
  `plugin-api`→`source-api`) kommt mit dem Pack-Lader (L1/L2).
- **`DeclarativeShell` (Ist, L1, 2026-06-12):** die deskriptor-getriebene Shell ist **in-tree gebaut** —
  `DeclarativeShell(ShellDescriptor)` (`:app`) interpretiert den compose-freien `ShellDescriptor`
  (`:ui-api`, `navStyle: ShellNavStyle{BOTTOM_BAR,DRAWER}`) und treibt die echte App über
  `descriptorFor(formFactor)`.
- **Externer Pack-Lader (Ist, L2, 2026-06-12 — Schlussstein):** das „Community **installiert** eine UI" ist
  als **data-only UI-Pack** gebaut — ein separates APK (Kategorie `UI_PACK`, ABI-Gate `VERSION=2`, Repo-
  Browser + Fingerprint-Install wie Quellen-/Preset-Plugins) liefert einen **deklarativen JSON-Deskriptor**
  `ui_pack.json` mit drei optionalen Sektionen (Subset-Packs, fehlend → Host-Default): `shell.navStyle`
  (überschreibt den Form-Faktor-Default — `ShellPackRegistry.forFormFactor(ff, override)`, Override schlägt
  Form-Faktor), `icons` (IconKey-Remap, s. o.) und `theme` (`accent`-Hex + `cornerRadius` →
  `KomgaReaderTheme(tokenOverride)`). **Streng deklarativ** (kein Plugin-Compose, kein Host-Rechte-Crash);
  der pure Pfad bleibt rein (`UiPackSpec` domain-Primitive, `parseUiPackSpec` data), die Compose-Übersetzung
  liegt nur in `:app` (`UiPackApply.kt`). **E-Ink host-erzwungen:** der **Akzent-Override** gilt NUR bei
  `LocalDisplayBehavior.allowsAccentColor` (mono E-Ink ignoriert ihn → bleibt Schwarz); Eckradius/Shell/Icons
  immer. Aktive Auswahl persistiert wie LANGUAGE (`active_ui_pack`, keine Migration), Picker „UI-Pack" in der
  Darstellung, Plugins-Tab-Filter `UI_PACKS`, Sample `plugin/komga-ui-pack-sample/`. **Offen bleibt nur das
  additive Soll:** der **ui-api-Code-ABI-Freeze** (nur für künftige **Code**-UI-Packs nötig — data-Packs
  linken kein ui-api) und **externe per-Slot-Packs** (header/overlay/… einzeln). Bis dahin sind alle
  Code-Packs Built-ins im App-Modul; der externe **Daten**-Pack-Lader ist gebaut.
- **Shell-Pack-Restposten:** Form-Faktor-User-Override (Ist, S0.1: `ShellLayoutMode{AUTO,COMPACT,EXPANDED}`,
  Settings-Picker, `resolveFormFactor`) · `DrawerShell`-Auswahlfarbe auf Host-Mono-Tokens (Ist, S0.3) ·
  compact-Header-Politur (S0.2 — empirisch verifizieren, nur fixen wenn real kaputt) · Form-Faktor-User-Override
  bleibt orthogonal zu `displayMode`.

**Reihenfolge bleibt „Theme zuerst, Layout danach, Lader zuletzt"** (analog Plugin-Reihenfolge
(c)→(a)→(b)) und **risikoärmstes Stück zuerst**. Jede weitere UI-Arbeit baut auf dieses Ziel hin
(Chrome als eigenständige, gegen eine Alternative austauschbare Composables), statt es zuzumauern.

### Was das schon heute heißt (auch bevor irgendein Plugin existiert)

Auch ohne den Plugin-Mechanismus baut **jede UI-Arbeit ab jetzt auf diese Modularität hin**, statt
sie zuzumauern (sonst wird es genau die Schuld aus der Ziel-Tabelle):

- Chrome-Bausteine als **eigenständige, parametrisierte Composables** mit klarer Grenze bauen — nicht
  als inline-Blöcke tief im Screen verdrahtet. Wer einen Header/ein Overlay/eine Button-Leiste baut,
  baut ihn so, dass er sich gegen eine Alternative *austauschen* ließe.
- Geteiltes Gerüst zentralisieren, bevor die N-te Variante entsteht (`shared-structure-before-variants.md`)
  — `ReaderScaffold`/`BaseDialog` sind die Keimzellen genau dieser Slot-Idee.
- Style **über Theme-Token** ziehen (`Theme.kt`), nie hartkodierte Farben/Maße — ein Theme-Pack
  ersetzt dann Token, nicht Call-Sites.

> **docs-match-code (Stand 2026-06-09, Branch `feat/ui-platform-skins` / `feat/source-agnostic-integration`):**
> Die **Theme-Pack-Naht ist gebaut** — `UiPack` (`app/ui/theme/UiPack.kt`) ist der In-Tree-Vertrag
> „voller Look einer Geräteklasse" (ColorScheme hell+dunkel · `DesignTokens` · Shapes · Typo); drei
> Built-ins `MonoEinkPack`/`KaleidoPack`/`LcdPack`, ausgewählt über `packFor(behavior)` bzw. die
> `UiPackRegistry`, angewandt im Host `KomgaReaderTheme`. Die Farben sind damit **geräteklassen-aware**
> (LCD volles Indigo-Schema, Kaleido gedämpft, mono S/W), nicht mehr global mono. Das ist das
> „Theme zuerst"-Stück. **Aurora (Ist, 2026-06-12, Phase 1):** vierter Built-in `AuroraPack` (`:ui-api`) — ein
> distinktiver **Modern-Mobile-Look** (Slate/Deeper-Grey + Cobalt `#3D5AFE`, dark+light, SoftShapes, getunte
> System-Typo); `packFor(LCD)→AuroraPack` (`LcdPack` bleibt Fallback). Dazu im Smartphone-Modus die schwebende
> Pill-Nav `ShellNavStyle.FLOATING_NAV` (`FloatingNavShell`, host-erzwungen via `auroraShellOverride`) + die
> Card-Kachel `AuroraSeriesTile` (`tiles`-Slot). Emulator-verifiziert; E-Ink unberührt. Das ist der **Referenz-
> Look**, aus dem **Phase 2** das deklarative `ui_pack.json`-`theme` auf **volle Tokens** ableitet (Daten,
> host-gerendert, E-Ink-gegated) — damit Aurora als externes Daten-APK lieferbar wird (eigener Plan, **Soll**).
> Design/Plan: `docs/superpowers/specs|plans/2026-06-12-modern-mobile-ui-pack-aurora*`.
> **Die Layout-Slot-Naht — alle sechs Regionen gebaut (Reihe abgeschlossen):**
> - **Region `header` (Ist, 2026-06-09; Such-Capability 2026-06-12, D1.1):** `app/ui/slots/UiSlots.kt` trägt
>   `HeaderSlot` (jetzt `@Composable (state: HeaderState) -> Unit`), die Surface
>   `HeaderState(title, onBack?, actions, search: HeaderSearch?)` + die optionale `HeaderSearch`, den
>   such-fähigen `DefaultHeader`, `UiSlotPack(header)`, den puren Resolver `UiSlots.resolve` (fehlender Slot →
>   `DefaultSlots`, nie `null`, analog `StubSource`) und `LocalResolvedSlots` (im Host `KomgaReaderTheme`).
>   Abwärtskompatibel: resolved-Property `headerSlot` + Kompat-Extension `ResolvedSlots.header(title, onBack,
>   actions)` → die suchlosen Call-Sites (`SubPageScaffold`, `SettingsRoute`, `HeaderSlotPreview`) rendern
>   `LocalResolvedSlots.current.header(...)` **textlich unverändert** (nur Extension-Import). Swap-Beweis:
>   `HeaderSlotPreview.kt` (`@Preview` zentrierter Alternativ-Header + Such-Zustand, nur Debug/Preview,
>   **keine** Nutzer-Einstellung).
> - **Region `homeHeader` (Ist, 2026-06-12):** zweite gebaute Slot-Region. Vertrag: `HomeHeaderSlot`
>   (typealias `@Composable (state: HomeHeaderState) -> Unit`). Capability-Surface-Prinzip: **„UI neu,
>   Kernlogik gleich"** — der Host (Core) baut `HomeHeaderState` (Status · `HomeHeaderSearch` · optional
>   `HomeHeaderFilter` · Menü-Overlay · `actions: @Composable RowScope.() -> Unit`) und besitzt alle
>   Logik; ein Pack **arrangiert** die Capabilities, implementiert sie nie neu. `DefaultHomeHeader`
>   (`app/ui/home/HomeHeader.kt`) ist das mitgelieferte Onyx-Layout. `HomeScreen` baut die Surface pro Tab
>   und ruft `LocalResolvedSlots.current.homeHeader(state)`. Die frühere **„Ausnahme `HomeScreen`"** (nicht
>   über `LocalResolvedSlots` swappable, direkter `TopAppBar`-Aufruf) ist damit **aufgehoben** — `HomeScreen`
>   ist vollständig in die Slot-Naht integriert. Swap-Beweis:
>   `app/src/debug/kotlin/com/komgareader/app/ui/home/HomeHeaderSlotPreview.kt`
>   (`AlternativeHomeHeader`: Status oben, Aktionen darunter — nur Debug/Preview, **keine** Nutzer-Einstellung).
>   Bewegung/Akzent bleiben **host-erzwungen** (`LocalDisplayBehavior`/`LocalDesignTokens`/`LocalEinkMode`) —
>   ein Slot liefert nur Inhalt/Struktur.
> - **Region `dialog` (Ist, 2026-06-12):** dritte gebaute Slot-Region. Vertrag: `DialogSlot`
>   (typealias `@Composable (state: DialogState) -> Unit`). Capability-Surface `DialogState`
>   (`app/ui/components/EinkModal.kt`) spiegelt die `EinkModal`-Parameter 1:1; `EinkModal(...)` ist ein
>   dünner Host-Wrapper, der `LocalResolvedSlots.current.dialog(state)` ruft — **keine** der ~9 Aufrufstellen
>   ändert sich. `DefaultDialog` ist der verbatim extrahierte Onyx-Renderer; das reine Layout-Detail
>   `modifier` (keine Call-Site setzt es) ist ersatzlos entfallen. Swap-Beweis:
>   `app/src/debug/kotlin/com/komgareader/app/ui/components/DialogSlotPreview.kt`. `EinkInfoDialog`/
>   Scroll-Helfer bleiben unangetastet.
> - **Region `settings` (Ist, 2026-06-12):** vierte gebaute Slot-Region. Vertrag: `SettingsSlot`
>   (typealias `@Composable (state: SettingsState) -> Unit`). Minimale Capability-Surface `SettingsState`
>   (`app/ui/settings/SettingsScreen.kt`): die host-gebauten `SettingsSection`s + der Such-`query`.
>   Der Pack ordnet die Sektionen an (Sidebar-Master-Detail/Accordion/flach) und besitzt den
>   Navigations-State selbst (`selectedId`/`openId` leben bewusst *in* der Layout-Impl, nicht in der
>   Surface — ein flacher Pack hat keine „aktive Sektion") — er rendert die Sektions-Inhalte nie neu.
>   `SettingsScreen(query, modifier, viewModel)` ist ein dünner Host-Wrapper, der
>   `LocalResolvedSlots.current.settings(state)` in `Box(modifier)` ruft (der `modifier` bleibt —
>   `SettingsRoute` reicht Route-Padding durch); **beide** Call-Sites unverändert. `DefaultSettings` ist
>   der verbatim extrahierte Onyx-Renderer (private Helfer `SettingsMasterDetail`/`SettingsSidebar`/
>   `SettingsAccordion` unverändert). Swap-Beweis:
>   `app/src/debug/kotlin/com/komgareader/app/ui/settings/SettingsSlotPreview.kt` (`AlternativeSettings`:
>   flache Einzel-Scroll-Liste). `SettingsSections.kt`/`SettingsViewModel`/die Sektions-Inhalte unangetastet.
> - **Region `tiles` (Ist, 2026-06-12):** fünfte gebaute Slot-Region. Vertrag: `TilesSlot`
>   (typealias `@Composable (state: TileState, modifier: Modifier) -> Unit`). Capability-Surface `TileState`
>   (`app/ui/components/SeriesTile.kt`): Werk + Lokal-Status + Navigations-Callbacks. Der Slot tauscht die
>   **einzelne Serien-Kachel, nicht das Grid** (Grid/Spaltenzahl bleibt Screen-Eigentum); der `modifier`
>   ist hier zweiter Slot-Parameter (Grid-Item-Layout). `SeriesTile(...)` ist ein dünner Host-Wrapper, der
>   `LocalResolvedSlots.current.tiles(TileState(...), modifier)` ruft — **beide** Call-Sites unverändert
>   (`LibraryScreen`, `GroupBrowseRoute`). **Vorgelagerter DRY-Schritt** (`shared-structure-before-variants`):
>   der ~95%-Klon `GroupSeriesCover` in `GroupBrowseRoute` wurde zuvor durch `SeriesTile` (`onLongClick = {}`)
>   ersetzt, sonst träfe ein tiles-Pack nur die Bibliothek. `DefaultSeriesTile` ist der verbatim extrahierte
>   Onyx-Renderer; Cover-Laden + E-Ink-Filter (`FilteredAsyncImage`, `crossfade(false)`) bleiben
>   host-erzwungen. Swap-Beweis: `app/src/debug/kotlin/com/komgareader/app/ui/components/TileSlotPreview.kt`
>   (`AlternativeTile`: Titel über dem Cover). Andere Kachel-Typen (`ChapterTile`/`CollageTile`/`MemberTile`)
>   unangetastet. `UiSlotPack(header, homeHeader, dialog, settings, tiles)` ·
>   `ResolvedSlots(header, homeHeader, dialog, settings, tiles)`.
> - **Region `overlay` (Ist, 2026-06-12):** sechste Chrome-Slot-Region.
>   Vertrag: `OverlaySlot` (typealias `@Composable BoxScope.(state: ReaderOverlayState) -> Unit`,
>   `BoxScope`-Extension wegen `align(TopCenter)`). Slot-ifiziert die togglebare Reader-Chrome-Menüleiste
>   (`ReaderChromeOverlay` → ersetzt durch `DefaultReaderOverlay`). Capability-Surface `ReaderOverlayState`
>   (`app/ui/reader/ReaderChrome.kt`: `title` + `onBack`/`onHome`/`onSettings` + reader-spezifische `actions`).
>   **Kein `visible`-Flag in der Surface:** Sichtbarkeit (chromeVisible) + E-Ink-Scrim (`readerOverlayScrim`)
>   host-erzwungen — `ReaderScaffold` rendert nur in `if (chromeVisible)` (Compose-Knackpunkt: BoxScope-Receiver
>   explizit via `with(this) { overlay(state) }`). Reader-Engines/`Viewer`/`RefreshScheduler` unberührt; das
>   ganze umgebende Gerüst (Tap-Zonen/Footer/Scaffold) ist mit C1 (`readerChrome`-Region) gebaut. Swap-Beweis:
>   `app/src/debug/kotlin/com/komgareader/app/ui/reader/OverlaySlotPreview.kt`
>   (`AlternativeReaderOverlay`: Shortcuts links, Titel zentriert).
> - **Region `detail` (Ist, 2026-06-12, Sub-Projekt D1 + D1.1):** siebte Slot-Region — das **Vollbild-Detail-Gerüst**
>   (kein Chrome-Stück, sondern das geteilte Scaffold, das die Detail-Routen über den `header`-Slot komponiert).
>   Vertrag: `DetailSlot` (typealias `@Composable (state: DetailScaffoldState) -> Unit`). Capability-Surface
>   `DetailScaffoldState` (`app/ui/detail/DetailScaffold.kt`: `title` + `onBack` + Header-`actions` + optionale
>   Header-`search: HeaderSearch?` (D1.1) + optionaler `snackbarHost` + host-gebauter `content: @Composable
>   (PaddingValues) -> Unit`). `DefaultDetailScaffold` = verbatim extrahiertes `Scaffold` + header-Slot
>   (via `headerSlot(HeaderState(…, search))`) + Snackbar. Umgestellt: **alle drei** Detail-Routen
>   `SeriesDetailScreen` + `GroupBrowseRoute` + (D1.1) `CollectionDetailScreen`; Body/Hero/Grid/`MemberTile`/VMs
>   unverändert. Swap-Beweis: `app/src/debug/kotlin/com/komgareader/app/ui/detail/DetailSlotPreview.kt`
>   (`AlternativeDetailScaffold`: schlanker eigener Titelbalken statt header-Slot, ohne Scaffold/Snackbar/actions).
>   `UiSlotPack(header, homeHeader, dialog, settings, tiles, overlay, detail)` · `ResolvedSlots(headerSlot, …, detail)`.
> - **Region `readerChrome` (Ist, 2026-06-12, Sub-Projekt C1):** achte Slot-Region — das **ganze Reader-Gerüst**
>   (`ReaderScaffold`: Vollbild-Hintergrund, Tap-Zonen, Hints, Status-Fuß, `persistentBars`, Start-Hinweis und der
>   schon slot-ifizierte `overlay`). Vertrag: `ReaderChromeSlot` (typealias `@Composable (state: ReaderScaffoldState)
>   -> Unit`). Capability-Surface `ReaderScaffoldState` (`app/ui/reader/ReaderScaffold.kt`): `chromeVisible` +
>   `onToggleChrome` + `title` + `onBack`/`onHome`/`onSettings` + `onPrev`/`onNext` + `background` + reader-spezifische
>   `actions` + `tapZones` (deklarativ, A1b — `ReaderTapZones`, ersetzt das alte opake `tapModifier`) + `footer` +
>   `persistentBars` + `showTapZoneHints` + host-gebauter `content`. **Der
>   entscheidende Schnitt:** die Surface trägt **NICHT** den `Viewer` (Naht B) — `ReaderScaffold` nutzte ihn nur für
>   `chromeVisible`/`toggleChrome` (per grep verifiziert: kein `refreshScheduler`/`navigateTo`/`onPageSettled` im
>   Scaffold), darum die abgeleiteten `chromeVisible: Boolean` + `onToggleChrome: () -> Unit` statt des `Viewer`.
>   `ReaderScaffold(chrome, …)` bleibt dünner Host-Wrapper (collectAsState + Surface bauen +
>   `LocalResolvedSlots.current.readerChrome(state)`); **die fünf Reader-Call-Sites unverändert**.
>   `DefaultReaderScaffold` = verbatim extrahierter Onyx-Renderer (innerer Overlay-Aufruf bleibt über die
>   `overlay`-Region). E-Ink-Scrim + Animation-Gating host-erzwungen; Reader-Engines/`Viewer.kt`/`RefreshScheduler`/
>   `ReaderChrome.kt`-Helfer unberührt. Swap-Beweis: `app/src/debug/kotlin/com/komgareader/app/ui/reader/ReaderChromeSlotPreview.kt`
>   (`AlternativeReaderChrome`: Status-Fuß oben statt unten, Tap-Hints/Start-Hinweis weggelassen).
>   `UiSlotPack(header, homeHeader, dialog, settings, tiles, overlay, detail, readerChrome)` ·
>   `ResolvedSlots(headerSlot, …, detail, readerChrome)`.
> - **Header-Such-Capability (Ist, 2026-06-12, D1.1):** `HeaderSlot` ist jetzt `@Composable (HeaderState) -> Unit`;
>   `HeaderState(title, onBack?, actions, search: HeaderSearch?)` (`UiSlots.kt`) trägt die optionale Such-Capability
>   `HeaderSearch` (Titel↔Suchfeld). `DefaultHeader` ist der such-fähige Default-Renderer. Abwärtskompatibel über
>   `ResolvedSlots.headerSlot` (resolved-Property) + die Kompat-Extension `ResolvedSlots.header(title, onBack, actions)`
>   — suchlose Call-Sites textlich unverändert.
> **Shell-Pack-Schicht gebaut (Ist, 2026-06-12):** `app/ui/shell/` trägt die Capability-Surface
> `AppShellState` (benannte Stücke: `destinations` als Nav-Daten + `ShellDestination{icon,label,
> header:HomeHeaderState?,content}`), den Vertrag `ShellPack`, die pure `formFactorFor(widthDp)` +
> `ShellPackRegistry.forFormFactor`. **L1 (Ist, 2026-06-12): eine deskriptor-getriebene
> `DeclarativeShell(ShellDescriptor)`** statt zwei bespoke Built-ins — `:ui-api` trägt den compose-freien
> `ShellDescriptor(navStyle: ShellNavStyle{BOTTOM_BAR,DRAWER})` + `descriptorFor(formFactor)`; `:app`
> die `DeclarativeShell`, die per Deskriptor die zwei verbatim Skelette (`BottomBarShell`/`DrawerShell`,
> compact: Drawer-Nav, E-Ink-gegatet) schaltet; die Registry liefert `DeclarativeShell(descriptorFor(ff))`.
> `HomeScreen` ist der Host (`HomeShellHost`):
> baut die Surface, löst nach `screenWidthDp` auf, ruft `pack.Render`. NavHost/Reader unberührt
> (`MainActivity` route-graph, Reader = Geschwister-Route). Emulator-verifiziert (expanded→Bottom-Bar,
> compact→Drawer). **Form-Faktor jetzt user-überschreibbar (Ist, S0.1):** `resolveFormFactor(ShellLayoutMode,
> widthDp)` pur+getestet, Settings-Picker (Auto/Kompakt/Breit), orthogonal zu `displayMode`. Drawer-Akzent
> seit S0.3 token-getrieben. Details: `architecture-seams.md` (Shell-Pack-Naht). **Externer Pack-Lader gebaut
> (Ist, L2, 2026-06-12):** ein data-only UI-Pack-APK (Kategorie `UI_PACK`, JSON-Deskriptor) liefert
> `shell.navStyle`/`icons`/`theme` extern und schlägt damit den Form-Faktor-Default (`ShellPackRegistry.
> forFormFactor(ff, override)`); E-Ink-Akzent-Gate host-erzwungen. **Noch Soll:** der ui-api-Code-ABI-Freeze
> + externe Code-/per-Slot-Packs (data-Packs linken kein ui-api), compact-Header-Politur (S0.2). **Die Region-Slot-Reihe
> ist abgeschlossen** (alle sechs Chrome-Regionen + `detail` + `readerChrome` gebaut; `UiSlotPack` trägt
> `header` + `homeHeader` + `dialog` + `settings` + `tiles` + `overlay` + `detail` + `readerChrome`; `nav` ist
> Shell-Pack-Sache, kein Region-Slot). **`ui-api`-Modul gebaut (Ist, A1, 2026-06-12):** der Slot-/Shell-/
> Theme-/Icon-**Vertrag** + die entkoppelten Built-ins (Theme-Packs, Icon-Stack) liegen jetzt im Modul
> `:ui-api` (`com.komgareader.ui.*`, DAG `domain → ui-api → app`), die gekoppelten Default-Renderer bleiben
> in `:app`; `UiSlots.resolve` ist 2-arg + `LocalResolvedSlots` Error-Default, der Host speist über
> app-`resolveSlots`/`DefaultSlots.resolved` ein. **Reader-Chrome deklarativ gebaut (Ist, A1b, 2026-06-12):**
> `ReaderTapZones` (sealed, `HorizontalThirds` + pure `dispatch`, `:ui-api`) ersetzt das opake `tapModifier`;
> Geometrie host-eigen, Aktion pro Zone als Daten, Comic opt-out via `null`. Ebenfalls Soll: das **ABI-Einfrieren** des
> `ui-api`-Vertrags (inkl. Enum-Aktionsform der Tap-Zonen) — nötig erst für künftige externe **Code**-Packs;
> der externe **Daten**-Pack-Lader (data-only UI-Pack) ist mit L2 **gebaut**. Wer hier
> weiterbaut, zieht diesen Ist-Stand und `architecture-seams.md` im selben Commit nach und behauptet keinen
> Typ als real, den `grep` nicht findet.

## docs-match-code

Verbindliche Regeln/Specs beschreiben **Soll und Ist getrennt** und stellen nie einen
nicht-existierenden Typ als real dar. Wer eine Naht/Komponente baut oder umbaut, zieht die
betroffene Regel **im selben Commit** auf den neuen Ist-Stand nach. Beim Lesen einer Regel,
die einen Typ behauptet: vor dem Bauen per `grep` verifizieren (`architecture-seams.md` trug
lange `Viewer`/`RefreshScheduler` als real, obwohl sie nie existierten — das kostet jeden
Leser eine Verifikationsrunde und führt Unvorsichtige in die Irre).

## Bezug

Dach über `architecture-seams.md` (die zwei Nähte), `source-agnostic-integration.md`
(Integrationsseite Naht A), `shared-structure-before-variants.md` (Gegenrichtung),
`eink-design-language.md` + `animation-gating.md` (Geräteseite). Gehört zu
[[project-komga-eink-reader]].
