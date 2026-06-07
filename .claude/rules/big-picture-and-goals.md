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

## Die Langzeit-Ziele (immer mitdenken, auch wenn die Aufgabe sie nicht nennt)

| Ziel | Was es bedeutet | Was es heute schon verbaut, wenn ignoriert |
|---|---|---|
| **Multi-Reader** | paged Comic, Webtoon, geführter Comic, Roman-Reflow — und weitere | Reader-Logik N-fach kopiert statt auf gemeinsamer Naht (`shared-structure-before-variants.md`) |
| **Multi-Server** | Komga, OPDS, Kavita, mehrere gleichzeitig | App an *eine* Quelle gekoppelt (`source-agnostic-integration.md`) |
| **Multi-Device** | mono E-Ink, **Farb-E-Ink (Kaleido)**, LCD-Phone/-Tablet | binäre Geräte-Annahme; „E-Ink-Look" global erzwungen statt geräteklassen-abhängig |
| **Plugins** | nutzer-installierbar: (a) Quellen, (b) UI-Views, (c) Color-Presets | quellenspezifische Annahmen in Interfaces gebacken; kein stabiler Plugin-Vertrag |
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
