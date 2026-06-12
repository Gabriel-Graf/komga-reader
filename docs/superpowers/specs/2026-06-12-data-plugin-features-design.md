# Data-Plugin-Features — Design-Spec (Spec 2)

**Datum:** 2026-06-12
**Branch:** `feat/json-data-plugins` (Worktree)
**Baut auf:** Spec 1 `2026-06-12-data-plugin-foundation-design.md` (generisches `PluginCategory` +
`discoverDataPlugins` — bereits gebaut). Dies ist die zweite von zwei Runden: die **drei Features**.

## Features

1. **Quellen-Plugin im „Server hinzufügen"-Selektor** (Bugfix: Kavita/Quellen-Plugins fehlen neben Komga/OPDS).
2. **Reader-Preset-Plugins** (`READER_PRESET`) — benannte Teil-Snapshots der Reader-Settings, data-only.
3. **Sprach-Plugins** (`LANGUAGE`) — es/fr/it als data-only JSON, Runtime-Override über `MapBackedStrings`.

---

## Feature A — Quellen-Plugin im „Server hinzufügen"

**Problem:** `AddConnectionModal` (`app/ui/settings/SettingsContent.kt`) hardcodet den Quellen-Typ-Selektor
auf nur `KOMGA`/`OPDS`. Installierte Quellen-Plugins (Kavita) sind nur über den Plugins-Tab hinzufügbar,
nicht hier — entgegen der Nutzererwartung „Quelle neben Komga/OPDS".

**Entwurf:**
- Der Typ-Selektor bekommt ein drittes Segment **„Plugin"**. `KOMGA`/`OPDS` → bestehendes URL-/Credentials-
  Formular. **„Plugin"** → Liste der entdeckten Quellen-Plugins (`pluginHost.discoverPlugins()` via
  `PluginCatalog`); Auswahl → **bestehender** TOFU-Bestätigungs-Modal + generisches `PluginConfigForm` →
  `addPluginSource(...)`.
- **Shared-structure-before-variants:** der Plugin-Add-Flow (TOFU → Config → Persistieren als
  `ServerConfig` mit `SourceKind.PLUGIN` + `__pkg`/`__entry`/`__sig`) wird als **ein** geteiltes Stück
  extrahiert, das Plugins-Tab **und** AddConnectionModal aufrufen. Kein Duplikat der Persistier-Logik.
- **Leerzustand:** keine Quellen-Plugins installiert → Hinweistext „Im Plugins-Tab installieren" statt leerer Liste.

**Lackmustest:** ein neu installiertes Quellen-Plugin erscheint ohne Code-Änderung im „Plugin"-Segment.

---

## Feature B — Reader-Preset-Plugins (`READER_PRESET`)

**Entwurf:**
- **JSON (data-only Asset):** Array benannter **Teil-**Snapshots:
  ```json
  [{ "abiVersion": 2, "name": "E-Ink Roman komfortabel",
     "settings": { "novelFontSizeEm": 1.2, "novelLineHeight": 1.4, "novelMarginPreset": "WIDE" } }]
  ```
  Erlaubte Keys = die ~11 Reader-Felder: `displayMode`, `deviceManagedRefresh`, `webtoonOverlapPercent`,
  `novelFontSizeEm`, `novelLineHeight`, `novelMarginPreset`, `novelFontFamily`, `novelTextAlign`,
  `novelHyphenationLang`, `novelFontWeight`, `guidedPanelOverlay`. **Fehlt ein Key → unberührt** (partiell).
- **Domain:** `ReaderPresetOverrides` (alle Felder nullable, `null` = nicht gesetzt) + `name`.
  Apply: pro Nicht-Null-Feld den passenden `SettingsRepository.setX(...)`-Setter aufrufen.
- **Parser `parseReaderPresetSpecs(json, manifestAbi): List<ReaderPreset>?`** (rein, org.json, `:data`) —
  analog `parsePresetSpecs`. Unbekannte/typ-falsche Keys werden übersprungen, gültige bleiben.
- **Kein Room-Table (YAGNI):** Presets werden **live** aus installierten `READER_PRESET`-Plugins entdeckt
  (`discoverDataPlugins(READER_PRESET)` → `parseReaderPresetSpecs`); Apply mutiert nur die bestehenden
  Settings. Keine Persistenz des Presets, keine Migration.
- **UI:** Reader-Settings bekommt eine „Preset anwenden"-Zeile (Picker der entdeckten Presets). Auswahl →
  `BaseDialog`-Bestätigung („überschreibt: …") → Apply. Animation E-Ink-gegatet (host-erzwungen).
- **Plugins-Tab-Hub:** listet installierte Reader-Preset-Plugins (Deinstall), Typ-Filter-Chip deckt die Kategorie.

---

## Feature C — Sprach-Plugins (`LANGUAGE`)

**Entwurf:**
- **`MapBackedStrings(overrides: Map<String,String>, fallback: Strings) : Strings`** (`app/i18n/`) —
  implementiert alle 273 Properties + die 5 parametrisierten Funktionen. Jede Property:
  `overrides["propertyName"] ?: fallback.propertyName`. Funktionen als **Template** mit Platzhaltern
  (`overrides["downloadingChapters"]?.let{ it.replace("{count}", count.toString()) } ?: fallback.downloadingChapters(count)`).
  **Hand-generiert** (mechanisch aus `Strings.kt`); drift-sicher, weil das Interface alle Member erzwingt.
  Fallback = `StringsEn` (ein partielles Sprach-Plugin fällt pro Key auf Englisch zurück).
- **JSON (data-only Asset):** ein Objekt je Sprach-Plugin:
  ```json
  { "abiVersion": 2, "code": "es", "name": "Español",
    "strings": { "appName": "Komga Reader", "libraryTitle": "Biblioteca",
                 "downloadingChapters": "Cargando {count} capítulos…", "...": "..." } }
  ```
- **Parser `parseLanguageSpec(json, manifestAbi): LanguageSpec?`** (rein, org.json, `:data`) →
  `LanguageSpec(code, name, strings: Map<String,String>, abiVersion)`. `null` bei fehlendem `code`/`name`/`strings`.
- **Auswahl/Resolution:** das `language`-Setting hält jetzt **beliebigen Code** (nicht nur „de"/„en").
  `PluginCatalog` entdeckt installierte Sprachen einmalig als `StateFlow<List<LanguageSpec>>`
  (`discoverDataPlugins(LANGUAGE)` → `parseLanguageSpec`). MainActivity baut die aktive `Strings`:
  - Code ∈ Built-in (`de`/`en`) → `stringsFor(builtin)`.
  - sonst Plugin-Spec mit diesem Code gefunden → `MapBackedStrings(spec.strings, StringsEn)`.
  - sonst → `StringsEn` (sicherer Default).
- **Picker:** Settings → Sprache listet `Deutsch`/`English` **+ jede installierte Sprache** (per `name`);
  Auswahl setzt `language=code`.
- **Maschinen-Übersetzung:** die es/fr/it-JSONs werden aus den EN-Strings generiert (Platzhalter `{…}`
  erhalten), roh aber vollständig — Muttersprachler verfeinern später.

---

## Querschnitt

- **`PluginCatalog`** ergänzt Discovery für `LANGUAGE` + `READER_PRESET` (über `discoverDataPlugins`),
  hält sie als `StateFlow`. Plugins-Tab-Hub zeigt sie (Deinstall), Typ-Filter-Chip deckt die neuen Kategorien.
- **Neue UI-Strings** in `StringsDe`/`StringsEn` (Segment „Plugin", „Preset anwenden", Bestätigungstexte,
  Leerzustände). **Wichtig:** `MapBackedStrings` wird **nach** dem Hinzufügen aller neuen Strings generiert.
- **Deliverables** (Korrektur 2026-06-12: **nicht** unter `komga-reader/plugins/`, sondern als
  gebaute, debug-signierte APKs + `repo.json`-Einträge im Distributions-Repo
  `Gabriel-Graf/KomgaReaderPlugins`): `komga-lang-es`, `komga-lang-fr`, `komga-lang-it` (LANGUAGE)
  + `komga-reader-preset-eink` (READER_PRESET-Sample).
- **Tests:** Parser-Units (`parseReaderPresetSpecs`, `parseLanguageSpec` — gesetzt **und** leer/fehlend);
  `MapBackedStrings`-Unit (override greift, fehlender Key → Fallback, Template-Interpolation);
  `ReaderPresetOverrides`-Apply-Logik (nur Nicht-Null gesetzt). E2E je Kategorie (Discovery→Parse→
  Select/Apply), `assumeTrue`-gegated wie Color-Preset.

## Architektur-Bezug

- `architecture-seams.md` (Naht-A-Integration für Feature A, data-only-Loader für B/C),
  `source-agnostic-integration.md` (Feature A: kein konkreter Quellen-Typ ins VM),
  `eink-design-language.md` + `animation-gating.md` (Picker/Dialog host-erzwungen),
  `shared-structure-before-variants.md` (Plugin-Add-Flow-Extraktion), `plugin-domain` (Kategorie-Rezept),
  `docs-match-code` (Regeln im selben Commit nachziehen).

## Entscheidungen (festgehalten)

| Fork | Entscheidung |
|---|---|
| Feature A Surfacing | „Plugin"-Segment → Picker → bestehender TOFU/`PluginConfigForm`-Flow (extrahiert, geteilt). |
| Reader-Preset Umfang | Import + Apply, **partiell** erlaubt; kein Save-current, kein Room-Table. |
| i18n | `MapBackedStrings` hand-generiert, Fallback `StringsEn`; `language`-Setting = beliebiger Code. |
| Sprach-Inhalt | es/fr/it maschinen-übersetzt, vollständig, jetzt. |
| Deliverables | 3 Sprach-Plugin-Repos + 1 Reader-Preset-Sample. |
