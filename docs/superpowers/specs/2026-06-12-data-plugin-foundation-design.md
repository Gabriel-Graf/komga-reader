# Data-Plugin-Fundament — Design-Spec

**Datum:** 2026-06-12
**Branch:** `feat/json-data-plugins` (Worktree, abgezweigt von `feat/modular-home-header`)
**Status:** Spec 1 von 2. Dies ist das **Fundament**. Spec 2 baut die drei Features
(Source-Plugin-in-Settings-Fix, Reader-Preset-Plugins, Sprach-Plugins) darauf.

## Problem & Motivation

Die App hat heute genau **zwei** Plugin-Mechaniken, unterschieden allein durch die Präsenz
eines Manifest-Keys:

- **Code-Quelle** (`ENTRY_CLASS`) — `SourcePlugin` über `PathClassLoader`, TOFU-Signatur.
- **Color-Preset** (`COLOR_PRESETS`) — **data-only APK** (kein Code), JSON-Asset via
  `createPackageContext(pkg, 0)` gelesen, in `color_profiles` importiert.

Geplant sind zwei weitere **data-only** Kategorien (Spec 2): **Reader-Settings-Presets** und
**Sprachen** (es/fr/it). Beide passen exakt aufs Color-Preset-Muster (JSON-Asset, kein Code),
aber der heutige Loader ist auf die eine Kategorie `COLOR_PRESETS` festverdrahtet
(`discoverColorPresetPlugins()`). Ohne Verallgemeinerung würde jede neue Kategorie den
Discovery-/Host-Code duplizieren.

**Dieses Fundament** verallgemeinert die data-only-Mechanik zu einem **kategorisierten**
Mechanismus: eine generische Discovery + ein Kategorie-Enum im ABI. Danach braucht eine neue
data-only Kategorie nur noch einen reinen Parser/Importer + ihre Heimat-UI — keinen
Discovery-Umbau.

## Scope

### Im Fundament (dieser Spec)
1. `PluginCategory`-Enum + ABI-Bump in `plugin-api`.
2. Generische Manifest-Keys (`DATA_CATEGORY`/`DATA_ASSET`) + Legacy-Alias für `COLOR_PRESETS`.
3. Generische `discoverDataPlugins(category)` im `plugin-host`; `discoverColorPresetPlugins()`
   wird dünner Wrapper darüber.
4. Color-Preset auf die neue Discovery umhängen (Regressions-Beweis, E2E grün).
5. `/plugins/`-Layout-Konvention + data-only-Plugin-Template.

### NICHT im Fundament (Spec 2)
- Source-Plugin-Surfacing im „Server hinzufügen"-Selektor (Bugfix).
- Reader-Preset-JSON-Form, Storage, Apply-UI.
- Sprach-Override-Schicht (`MapBackedStrings`), Sprach-JSON-Form, Picker-Integration.
- Verschieben bestehender Plugin-Repos nach `/plugins/` (nur Konvention+Template jetzt).

## Entwurf

### A · `plugin-api` — Kategorie + ABI

```kotlin
// Neu: data-only Plugin-Kategorien. Code-Quellen bleiben über ENTRY_CLASS (kein Enum-Eintrag).
enum class PluginCategory { COLOR_PRESET, READER_PRESET, LANGUAGE }
```

```kotlin
object PluginAbi {
    const val VERSION = 2        // war 1 — additive Erweiterung (Kategorien)
    const val MIN_SUPPORTED = 1  // color-preset-v1-APKs laden weiter
}
```

Doc-Kommentar an `PluginCategory`/`PluginAbi`: neue Kategorie = additiv, **kein** ABI-Bruch;
`MIN_SUPPORTED` bleibt 1, solange v1-Plugins kompatibel sind.

### B · `PluginManifestKeys` — generische Keys

```kotlin
const val DATA_CATEGORY = "com.komgareader.plugin.DATA_CATEGORY" // "COLOR_PRESET"|"READER_PRESET"|"LANGUAGE"
const val DATA_ASSET    = "com.komgareader.plugin.DATA_ASSET"    // Asset-Dateiname der JSON
// Legacy (bleibt): COLOR_PRESETS = "...COLOR_PRESETS" → category=COLOR_PRESET, asset=value
```

Legacy-Alias: ein APK mit altem `COLOR_PRESETS`-Key wird so behandelt, als trüge es
`DATA_CATEGORY=COLOR_PRESET` + `DATA_ASSET=<wert>`. Das vorhandene Kindle-Preset-APK bricht nicht.

### C · `plugin-host` — generische Discovery

```kotlin
data class DiscoveredDataPlugin(
    val packageName: String,
    val category: PluginCategory,
    val abiVersion: Int,
    val assetName: String,
    val displayName: String,   // pm.getApplicationLabel, vom Color-Preset-Wrapper genutzt
    val assetJson: String,
)
// Kein signatureSha256: data-only Plugins laden nie Code (Flags 0) → kein TOFU/Pinning nötig.

fun discoverDataPlugins(category: PluginCategory): List<DiscoveredDataPlugin>
```

- Scannt installierte Pakete (`QUERY_ALL_PACKAGES`, schon vorhanden), liest `DATA_CATEGORY`
  (oder Legacy `COLOR_PRESETS`) + `DATA_ASSET`, filtert auf die angefragte `category`.
- Lädt das Asset via `createPackageContext(pkg, 0)` — **Flags 0 = nur Ressourcen, kein
  Classloader / kein TOFU / kein Multidex** (identisch zum heutigen Color-Preset-Pfad).
- ABI-Gate über `AbiGate` (abiVersion in `[MIN_SUPPORTED, VERSION]`).
- `discoverColorPresetPlugins()` wird ein **dünner Wrapper**: `discoverDataPlugins(COLOR_PRESET)`
  → Mapping auf den bestehenden `DiscoveredPresetPlugin`-Typ (oder dessen Ersetzung durch
  `DiscoveredDataPlugin`, falls call-site-arm). Kein duplizierter Scan-Code.

### D · `:data` — Parse-Naht + Color-Preset-Beweis

- Die reinen, kategorie-spezifischen Parser bleiben in `:data`/`plugin-host` (heute
  `PresetSpecParser` → `ColorPresetImporter`). Das Fundament generalisiert nur die **Discovery**,
  nicht die Interpretation.
- **Color-Preset wird auf die neue `discoverDataPlugins` umgehängt.** Der bestehende
  Color-Preset-Import-/E2E-Pfad muss unverändert funktionieren = Regressions-Beweis, dass die
  Generalisierung verlustfrei ist.
- Reader-Preset-/Language-Parser+Importer kommen in Spec 2.

### E · `/plugins`-Layout + Template

- **Konvention:** jedes Plugin = eigenes Git-Repo unter `/plugins/<name>/`, im Haupt-Repo
  gitignored (wie Kavita heute unter `plugin/komga-kavita-source/`). Ziel-Konvention `/plugins/`.
- **Data-only-Template** `/plugins/_template-data/`: minimales APK-Gradle-Setup,
  Manifest-Metadata-Stub (`DATA_CATEGORY`/`DATA_ASSET`), Beispiel-`assets/data.json`, README mit
  Schritt-für-Schritt (Kategorie wählen, JSON füllen, bauen, lokal installieren, in der App
  entdecken). Linkt `:plugin-sdk` `compileOnly` (data-only braucht zwar keinen Code, aber
  konsistente Vorlage).
- **Move bestehender Repos:** NICHT jetzt (nur Konvention dokumentieren). Kavita + ggf.
  Color-Preset-Repo bleiben, wo sie sind; Verschieben ist ein separater, risikoarmer Schritt.

### F · Tests

- **Unit (rein):** `discoverDataPlugins` Kategorie-Filter; Legacy-Alias-Mapping
  (`COLOR_PRESETS` → `COLOR_PRESET`); ABI-Gate (in/außerhalb Spanne). Bestehende
  Color-Preset-Parser-Tests bleiben grün.
- **E2E:** der vorhandene Color-Preset-E2E (data-only APK → Discovery → Import → `color_profiles`)
  **muss grün bleiben**. Das ist der Beweis der verlustfreien Generalisierung. Kein neuer E2E
  nötig im Fundament (neue Kategorien bringen ihre E2Es in Spec 2 mit).

## Architektur-Bezug (verbindliche Rules)

- `architecture-seams.md` — Loader-Ist-Stand; Discovery bleibt im `plugin-host`, `domain` netzfrei.
- `plugin-domain` Skill — data-only-Plugin-Muster, Kategorie-Philosophie, „neue Capability = additiv".
- `big-picture-and-goals.md` — Plugins-Sektion (Reihenfolge Preset→Quelle→UI; data-only = risikoärmst).
- `source-extensibility.md` — Kochrezept-Denke (neue Kategorie = Parser+Importer+UI, kein Kern-Umbau).
- `docs-match-code` — diese Spec + `architecture-seams.md` im selben Commit auf den Ist-Stand
  nachziehen, sobald gebaut.

## Entscheidungen (festgehalten)

| Fork | Entscheidung |
|---|---|
| Schnitt | Fundament = 1 Spec/Plan; die 3 Features = 1 Spec/Plan (danach). |
| i18n-Ansatz (Spec 2) | Override-Schicht minimal: `MapBackedStrings`, `map["prop"] ?: fallback(EN). |
| Kategorie-Mechanik | Generischer `DATA_CATEGORY`-Key + `PluginCategory`-Enum + ein `discoverDataPlugins`. |
| Surfacing (Spec 2) | Heimat-Ort je Kategorie + Plugins-Tab als Install-Hub. |
| Legacy `COLOR_PRESETS` | Als Alias behalten (keine Regression, Kindle-APK bleibt). |
| `/plugins/`-Move | Nur Konvention+Template jetzt; Repos verschieben später. |
