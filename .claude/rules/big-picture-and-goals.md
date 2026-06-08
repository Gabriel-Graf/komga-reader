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
| Farb-E-Ink (Kaleido) | nein | ja (gedämpft, via Color-Filter) |
| LCD-Phone/-Tablet | ja | ja |

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

Noch nicht gebaut. Diese Entscheidungen stehen aber fest, damit nicht jede Session sie neu
herleitet (und divergiert). Modell: Mihon/Tachiyomi.

1. **Vertrag in eigenem Modul `plugin-api`** (pure Kotlin), **nicht** in `domain`. Plugins linken
   es `compileOnly`; `domain` zu shippen würde App-Interna in die ABI ziehen. `plugin-api` ist eine
   dünne, eingefrorene Fassade über die stabilen Naht-Typen (`MediaSource` & Co.).
2. **ABI-Gate als zwei Integer** (`VERSION`, `MIN_SUPPORTED`), nicht semver-Strings. Plugin-Manifest
   nennt `abiVersion`; außerhalb der Spanne → „inkompatibel", nie instanziiert. Neue Capability =
   neues **optionales** Interface (wie `ContainerSource`), additiv, ohne ABI-Bump.
3. **Paket = separate APK**, via `PackageManager` (Metadata-Flag) entdeckt, via
   `createPackageContext(...).classLoader` geladen (Parent = Host-Classloader, sonst `ClassCastException`
   durch doppelte Interface-Klassen). Die OS macht Signatur/Integrität/Update/`.so`-Extraktion — **kein**
   `DexClassLoader` heruntergeladener `.dex` (Security/Play-Policy). Lebt in neuem Modul `plugin-host`.
4. **Identität:** `packageName` = der Installierbare; `SourceId.of(name, PLUGIN, "$packageName/$configHash")`
   = der Inhalts-Namespace pro Quelle. Jeder DB-Satz trägt `sourceId` → uninstall fällt sauber auf
   `StubSource` zurück, kein Schema-Change.
5. **Die drei Typen sind unterschiedlich schwer:**
   - **(c) Color-Presets — am einfachsten, ZUERST:** kein Code, **deklarative Daten** (JSON mit
     `ColorProfile`-Zahlen, auf Wertebereiche geclampt) → `color_profiles`-Tabelle, `builtIn=false`,
     origin-getaggt (löschbar). Null Classloader-/ABI-Risiko → idealer Proof des Lade-Wegs.
   - **(a) Quellen — machbar, der designte Pfad:** `SourcePlugin` liefert `BrowsableSource`-Impls →
     `SourceManager.register()`. Setzt aber `source-agnostic-integration.md` **vollständig** voraus:
     solange der Reader Komga-URLs lädt, kann keine Plugin-Quelle Seiten liefern.
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

> **docs-match-code:** Dies ist **Soll/Richtung**, kein Ist. Es gibt **heute keine** UI-Slot-Naht,
> kein `ui-api`-Modul, keinen Pack-Lader. Wer hier baut, führt den Ist-Stand in dieser Sektion und in
> `architecture-seams.md` im selben Commit nach und behauptet keinen Typ als real, den `grep` nicht findet.

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
