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

### Die drei Schichten + der Shell-Pack (die Form-Faktor-Naht) — Soll, noch nicht gebaut

Die modulare UI staffelt sich in **drei Schichten**, jede eine eigene Naht mit Default + Built-in-Varianten:

| Schicht | Was sie tauscht | Trigger der Auswahl | Status |
|---|---|---|---|
| **Theme-Pack** (`UiPack`) | Look: Farbe/Token/Typo/Shapes | Geräteklasse (`DisplayBehavior`) | **gebaut** (Mono/Kaleido/Lcd) |
| **Shell-Pack** (`AppShellState`/`DefaultShell`) | **das ganze Layout-Skelett**: Nav-Ort (Bottom-Bar/Side-Rail/Drawer), Anordnung, Baum | **Form-Faktor** (Bildschirmgröße), orthogonal zur Geräteklasse | **Soll** (existiert NICHT) |
| **Region-Slots** (`UiSlotPack`) | einzelne Chrome-Regionen, die ein Shell-Pack platziert: header/homeHeader/overlay/tiles/settings/dialog | vom aktiven Shell-Pack gewählt | header+homeHeader **gebaut**, Rest Soll |

**Warum der Shell-Pack die neue oberste Naht ist (User-Entscheidung 2026-06-12):** Region-Slots sitzen
*in* einem festen Skelett — sie können die Bottom-Bar restylen, aber nicht zu einem Drawer/Side-Rail
machen. Ein echter **Phone-Formfaktor** unterscheidet sich aber im **Skelett selbst** (Nav woanders,
andere Anordnung, Buttons an anderen Orten). Darum: eine Schicht **über** den Regionen, die den ganzen
Layout-Baum besitzt. Der **Core** liefert genau **eine Capability-Surface** `AppShellState`; der
**Shell-Pack** ordnet frei an. Default-Shell = heutiges E-Ink/Tablet-Bottom-Bar-Layout (aus `HomeScreen`
extrahiert). Phone-Shell = zweites Built-in (Drawer/anders), beweist den Skelett-Tausch — exakt wie
Mono/Kaleido/Lcd den Theme-Tausch beweisen. Es ist das **`homeHeader`-Muster eine Ebene höher**:
`DefaultHomeHeader(state)` → `DefaultShell(appShellState)`. Form-Faktor (Shell) und Geräteklasse (Theme)
bleiben **orthogonale Achsen** (konsistent mit „Geräteklassen sind nicht binär"): eine Boox = großer
E-Ink → Tablet-Shell + Mono-Theme; ein Phone = klein-LCD → Phone-Shell + Lcd-Theme.

**Bauweg 1 jetzt, 3 als Endform — und 1 wächst sauber zu 3:** Wie bei Quellen-Plugins und Theme gibt es
zwei Vertragsformen, gestaffelt vom Einfachen zum Schweren:

1. **In-Tree-Compose-Shell-Packs (jetzt):** der Shell-Pack ist eine `@Composable (AppShellState) -> Unit`
   und ordnet mit voller Compose-Freiheit an. Built-ins (Default-/Phone-Shell) sind Compose — wie
   `DefaultHomeHeader`.
3. **Deklarativer Shell-Pack (Endform, externe APK-Packs, Phase 4):** der externe Pack liefert **kein**
   arbiträres Compose mit Host-Rechten (Crash/E-Ink-Invarianten, dieselbe Regel wie Plugins (b)), sondern
   einen **Daten-Deskriptor** (Nav-Stil-Enum, welche Destination an welchem Anker, welche Region wohin).
   Der Host shippt **eine** `DeclarativeShell` — selbst ein Ansatz-1-Compose-Built-in, parametrisiert per
   Deskriptor — und rendert **dieselben** `AppShellState`-Stücke an die genannten Plätze.

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
> „Theme zuerst"-Stück.
> **Die Layout-Slot-Naht wächst — zwei Regionen gebaut:**
> - **Region `header` (Ist, 2026-06-09):** `app/ui/slots/UiSlots.kt` trägt `HeaderSlot`
>   (typealias `@Composable (title, onBack?, actions) -> Unit`), `UiSlotPack(header)`, den puren Resolver
>   `UiSlots.resolve` (fehlender Slot → `DefaultSlots`, nie `null`, analog `StubSource`) und
>   `LocalResolvedSlots` (im Host `KomgaReaderTheme`). Die Call-Sites (`SeriesDetailScreen`, `SubPageScaffold`)
>   rendern `LocalResolvedSlots.current.header(...)`. Swap-Beweis: `HeaderSlotPreview.kt` (`@Preview` mit
>   zentriertem Alternativ-Header, nur Debug/Preview, **keine** Nutzer-Einstellung).
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
>   ein Slot liefert nur Inhalt/Struktur. `UiSlotPack(header, homeHeader)` · `ResolvedSlots(header, homeHeader)`.
> **Noch Soll/Richtung (existiert NICHT):** die **Shell-Pack-Schicht** als Ganzes — `AppShellState`
> (die Capability-Surface aus benannten Stücken), `DefaultShell`/Phone-Shell, `DeclarativeShell` und die
> Form-Faktor-Auswahl sind **geplant, nicht gebaut** (User-Entscheidung 2026-06-12, siehe Subsektion „Die
> drei Schichten + der Shell-Pack" oben); das heutige Skelett ist noch hart in `MainActivity` (NavHost) +
> `HomeScreen` (Scaffold + `EinkBottomBar`) verdrahtet. Ebenfalls Soll: die **übrigen vier Slots**
> (overlay/tiles/settings/dialog — `UiSlotPack` trägt heute `header` + `homeHeader`), ein eigenes
> **`ui-api`-Modul** (Vertrag bewusst in-tree, noch nicht eingefroren) und der **externe Pack-Lader**
> (separates APK / ABI-Gate, Phase 4 — `UiPackRegistry` ist nur der In-Tree-Einhängepunkt). Wer hier
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
