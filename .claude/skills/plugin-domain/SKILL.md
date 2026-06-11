---
name: plugin-domain
description: Use when building or extending plugins (source/preset/UI), designing the plugin/UI-pack ABI, or making ANY subsystem pluggable in the Komga-Reader. Bündelt die Plugin-Philosophie, wie ein Plugin gebaut wird, und das Rezept für ein neues plugbares Subsystem (Capability-Surface, "UI neu, Kernlogik gleich").
---

# Plugin-Domäne: Philosophie, Plugin-Bau, plugbares Subsystem

Dieser Skill ist der Einstieg, sobald etwas im Komga-Reader **plugbar** werden soll —
eine neue Quelle, ein Color-Preset, ein UI-Pack, oder ein bisher monolithisches Subsystem,
das man austauschbar schneiden will. Er hält die **Philosophie** fest, zeigt **wie ein Plugin
gebaut wird**, und gibt das **Rezept**, ein Subsystem plugbar zu machen.

Er **dupliziert keine Regeln** — er verweist auf die maßgeblichen Rules und auf den Ist-Stand
des Loaders. Vor dem Bauen die referenzierten Rules lesen; sie sind verbindlich, dieser Skill
ist ihre anwendungsnahe Bündelung.

---

## Säule 1 — Plugin-Philosophie

Das Versprechen der App: **quellen- und geräte-agnostisch, mit nutzer-installierbaren Plugins,
und einer modularen Oberfläche.** Das hält nur, wenn alle Variabilität **hinter den zwei Nähten**
liegt (Naht A = Quellen, Naht B = Render/E-Ink — siehe [[architecture-seams]]) bzw. hinter einem
sauber geschnittenen Capability-Vertrag. Die Leitprinzipien:

### Core bleibt — Chrome + Capabilities werden austauschbar

| Schicht | Beispiele | Plugbar? |
|---|---|---|
| **Core (bleibt)** | Reader-Engines (paged/webtoon/comic/novel), die `Viewer`-Naht, Lese-/Sync-/Refresh-Pfad, Naht-A/B-Verträge | nie ersetzbar — sie tragen Render- und E-Ink-Korrektheit |
| **Capabilities/Chrome (austauschbar)** | Quellen, Color-Presets, UI-Slots (Header/Overlay/Tiles/…), Theme/Token-Packs | hinter adressierbaren, benannten Verträgen; Default = mitgeliefertes Pack |

Ein Plugin/Pack darf den **Rahmen** neu erfinden, nie die **Engine**, die Pixel aufs E-Ink-Panel
bringt. Quelle der Philosophie: [[big-picture-and-goals]] (Plugins-Sektion + ui-modularity).

### „UI neu, Kernlogik gleich"

Der Kerngedanke jedes Capability-Plugins: das Pack **ordnet/restyled** eine vom Host gebaute
Fähigkeitsmenge — es **implementiert die Logik nie neu**. Ein UI-Pack baut Suche/Sync/Filter
nicht selbst; es bekommt sie als benannte Capabilities und entscheidet nur über Anordnung und
Aussehen. So bleibt die Kernlogik an *einer* Stelle (Host) und wird nicht pro Pack dupliziert.

### Deklarativ statt arbiträrer Compose-Code

UI-Plugins liefern **kein** beliebiges Compose mit Host-Rechten (Compiler-Kopplung, ein Crash
reißt den Host mit, E-Ink-Invarianten nicht erzwingbar). Stattdessen liefert das Pack eine
**Beschreibung** (Slot→Inhalt, Tap-Zone→Aktion, Style-Token), der **Host rendert und steuert
den Refresh**. Damit bleiben die E-Ink-Invarianten (Bewegung/Akzentfarbe über die Geräteklasse
gegatet) **host-erzwungen**, egal was das Pack will — siehe [[eink-design-language]] und
[[animation-gating]].

### ABI-Gate = zwei Ints, additiv erweitern

Der Kompatibilitäts-Vertrag ist `PluginAbi` (`VERSION`, `MIN_SUPPORTED`) — **zwei Integer**,
keine semver-Strings. Das Plugin-Manifest nennt seine `ABI_VERSION`; außerhalb der Spanne →
„inkompatibel", nie instanziiert (`AbiGate`). **Neue Fähigkeit = neues optionales Interface**
(additiv), kein ABI-Bump, der alte Plugins bricht.

### Die drei Plugin-Typen + Reihenfolge

| Typ | Was | Risiko | Reihenfolge |
|---|---|---|---|
| **(c) Color-Preset** | data-only APK (kein Code), liefert `ColorPresetSpec`-Liste als Asset | minimal | **zuerst** |
| **(a) Quelle** | `SourcePlugin` → `BrowsableSource` (+ ggf. `SyncingSource`) | mittel (Code lädt) | **dann** |
| **(b) UI/Capability** | deklaratives Pack füllt Slots/Capability-Surfaces | hoch | **zuletzt** |

Reihenfolge **vom risikoärmsten zum riskantesten** (Preset → Quelle → UI). „Theme zuerst,
Layout danach" gilt analog innerhalb der UI-Modularität.

### Sicherheit: TOFU-Signatur-Pinning

Trust-on-First-Use: das Trust-Gate ist der **Cert-SHA-256-Pin**, beim Erst-Hinzufügen vom
Nutzer bestätigt (`PluginSignature`). Ein Code-Plugin wird nur instanziiert, wenn die aktuelle
Paket-Signatur dem Pin entspricht (`PluginHost.sourceFor` mit `expectedSignature`). Repo-Installs
verifizieren zusätzlich gegen den Index-`fingerprint` vor der `PackageInstaller`-Session
(`PluginInstaller`) — Mismatch = harter Abbruch, nie installiert. Geladen wird nur das
OS-installierte APK über `PathClassLoader`, **kein** `DexClassLoader` heruntergeladener `.dex`.

### Distribution: `plugin-sdk` als einziges `compileOnly`-Artefakt

Externe Plugins linken **genau ein** Artefakt `compileOnly`: das geshadete `:plugin-sdk`
(`com.komgareader:plugin-sdk:0.1.0`) — es bündelt plugin-api + source-api + domain (nur
`com.komgareader.**`, keine Fremd-Libs). Die Vertragsklassen dürfen **nicht** ins Plugin-APK
gebündelt werden (sonst `ClassCastException` durch doppelte Interface-Klassen).

---

## Säule 2 — Wie ein Plugin gebaut wird

**Ist-Stand (gebaut + E2E-grün):** Loader, Quellen-Plugin (Kavita), Color-Preset-Plugin,
Repo-Browser mit APK-Install. Details: [[plugin-host-kavita]].

### Gemeinsam (jeder Plugin-Typ)

1. **Separates APK** (eigenes Git-Repo, im Haupt-Repo gitignored). Linkt `:plugin-sdk`
   `compileOnly`.
2. **Manifest-Metadata** deklariert die Identität (`PluginManifestKeys`): Code-Plugins nennen
   `ENTRY_CLASS` + `ABI_VERSION`; Preset-Plugins nennen `COLOR_PRESETS` (Asset-Name) + `ABI_VERSION`.
3. **Discovery** macht der Host: `PluginHost.discoverPlugins()` (Code-Plugins) bzw.
   `PluginHost.discoverColorPresetPlugins()` (Presets). Voraussetzung: `QUERY_ALL_PACKAGES`
   im App-Manifest (Paket-Visibility ab API 30).
4. **Signatur/Fingerprint**: lokal hinzugefügte Plugins über TOFU-Pin (`PluginSignature`);
   Repo-Installs über Index-`fingerprint` (`PluginInstaller`, `fingerprintMatches`).

### Typ (a) — Quelle (Referenz: Kavita)

- Klasse implementiert `SourcePlugin` (`metadata`, `configSchema()`, `create(config)`).
  `create` liefert eine `BrowsableSource` (+ `SyncingSource`, wenn der Server Fortschritt syncen
  kann).
- Config-Felder über `ConfigSchema`/`ConfigField`/`FieldType` (TEXT/SECRET/URL/BOOL) — der Host
  rendert daraus generisch das `PluginConfigForm`.
- Geladen via `PluginHost.sourceFor(pkg, entry, expectedSignature, config)` → `SourceRegistration`
  (`SourceKind.PLUGIN`-Branch) → `SourceManager`. Naht-A-Integration einhalten:
  [[source-agnostic-integration]], [[source-extensibility]] (Kochrezept B).

### Typ (c) — Color-Preset (Referenz: Kindle-Preset)

- **Kein Code**: APK trägt nur `assets/*.json` (Liste `ColorPresetSpec`) + Manifest-Metadata.
- Host liest via `createPackageContext(pkg, 0)` (**Flags 0 = nur Ressourcen, kein Classloader/
  TOFU/Multidex**), `parsePresetSpecs` → `ColorPresetImporter` clampt auf `ColorProfile`-Grenzen.
  Domain-Regel: [[komga-eink-color-filter]].

### Distribution über Repo

- Repo liefert `repo.json` (`name` + Einträge mit `packageName`/`type`/`abiVersion`/`versionCode`/
  `apkUrl`/`fingerprint`). `:data` parst rein und unit-getestet (`parseRepoIndex`, `mergeRepoEntries`
  nach höchster `versionCode`, `fingerprintMatches`). `PluginRepoClient` (OkHttp) lädt; offizielles
  Default-Repo = `PluginRepoDefaults.OFFICIAL_URL` (+ Settings-Toggle, abschaltbar). `domain` bleibt
  netzfrei.

---

## Säule 3 — Rezept: ein neues Subsystem plugbar machen

Wenn ein bisher fest verdrahtetes Subsystem (z. B. ein UI-Bereich) austauschbar werden soll,
in **dieser Reihenfolge** — analog dem Naht-A-Kochrezept in [[source-extensibility]]:

1. **Capability-Vertrag definieren.** Ein **benannter Satz Daten + Callbacks**
   (eine `data class` mit Fähigkeiten + Aktionen), **kein** arbiträrer Code. Das ist die
   *Capability-Surface*: was das Subsystem kann, ohne *wie* es aussieht.
2. **Host besitzt die Logik.** Der Host (Core) **baut** die Surface — er hält Zustand, Sync,
   Filter, I/O. Das Plugin/Pack bekommt die fertige Surface, nie die Mechanik.
3. **In-Tree-Slot mit Default.** Ein benannter, adressierbarer Einhängepunkt mit
   **garantiertem Default**: fehlt das Pack-Stück, fällt es sauber auf den Default zurück —
   **nie `null`**, analog `StubSource` bei Quellen. (Vorbild: die `header`-Region in `UiSlots`.)
4. **ABI-fähig schneiden, E-Ink host-erzwungen.** Den Vertrag additiv halten (neue Fähigkeit =
   optionales Feld), damit ein Pack nicht bei jeder App-Version bricht. **E-Ink-Invarianten
   (Bewegung/Akzent) bleiben am Host** — nie Teil der Surface, nie vom Pack steuerbar.
5. **Pack/Plugin ordnet/liefert.** Das Pack arrangiert/restyled die Surface — es **implementiert
   die Logik nie** („UI neu, Kernlogik gleich"). Vor der N-ten Variante das Gemeinsame zuerst
   extrahieren: [[shared-structure-before-variants]].

> **Lackmustest:** Funktioniert das Subsystem unverändert, wenn der Slot leer bleibt (Default),
> und kann ein Pack es komplett neu anordnen, ohne eine Zeile Kernlogik zu duplizieren? Wenn nein,
> ist der Schnitt falsch — die Logik leckt in den Slot.

Referenz-Beispiel (in Arbeit, wird hier real verlinkt): [[modular-home-header]] — der ganze
Home-Header als `HomeHeaderState`-Capability-Surface hinter einem `HomeHeaderSlot`.

---

## Bezug

Gehört zu [[project-komga-eink-reader]]. Verbindliche Rules: [[architecture-seams]] (die zwei
Nähte + Loader-Ist), [[source-extensibility]] (Naht-A-Kochrezept, Vorbild fürs Capability-Rezept),
[[source-agnostic-integration]] (Naht-A-Integration), [[big-picture-and-goals]] (Plugin-Philosophie
+ ui-modularity), [[shared-structure-before-variants]] (Gemeinsames vor der N-ten Variante).
Ist-Stand des Loaders: [[plugin-host-kavita]].
