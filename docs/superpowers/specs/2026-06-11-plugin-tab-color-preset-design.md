# Plugin-Tab (installiert) + Color-Preset-Plugin (Typ c) + Import-UI raus — Design

> Datum: 2026-06-11 · Phase 4, Subsystem 2 (Slice **P1**) · Branch `feat/plugin-tab-color-preset`
> Status: Design genehmigt, Implementierung folgt (Plan → Bau).
> Voraussetzung: Subsystem 1 (Plugin-Loader + Kavita) ist auf `master` gemergt.

## Zweck & Abgrenzung

Macht **Farbfilter-Presets zu Plugins** (Plugin-Typ c, „deklarativ, kein Classloader" — big-picture)
und gibt den Plugins ein **Verwaltungs-Zuhause**: den bereits in `HomeScreen` verdrahteten
**Plugins-Tab** (`PluginsScreen`, heute „coming soon"-Platzhalter). Die bespoke Farbfilter-
**Import-UI** (JSON-Datei-Import) entfällt — Presets kommen künftig aus installierten Plugin-APKs.

**Slice P1 (diese Spec) deckt ab:**
1. Color-Preset als **data-only** Plugin-Typ (Host liest ein JSON-Asset aus dem Fremd-APK **ohne**
   dessen Code zu laden).
2. **Plugin-Tab** für **installierte** Plugin-APKs (Quellen **und** Preset-Plugins): je Zeile
   **⚙ Einstellungen** + **🗑 entfernen** (= APK deinstallieren via OS-Intent, Cascade-Cleanup).
3. **Entfernen** der bespoke Farbfilter-Import-UI.
4. Ein **Beispiel-Preset-Plugin** in `plugin/` (anderes E-Ink-Gerät) als erster Konsument.

**Bewusst NICHT in P1 (eigene spätere Spec → „Slice P2"):** der **Repo-Browser** — Sammel-Repo-
Index + externer Repo-Link + **Suche** + APK **herunterladen/installieren** via `PackageInstaller`
+ Signatur-Pin beim Install. Das schirm-level **+** ist P2; in P1 nur Platzhalter/Hinweis.

## Festgelegte Entscheidungen (Brainstorming)

| Frage | Entscheidung |
|---|---|
| Preset-Liefermechanik | **Reine Daten (JSON-Asset, kein Code, kein Classloader, kein TOFU).** ABI-Gate greift trotzdem. |
| Preset-Lifecycle | Nutzer **importiert manuell** je Plugin (⚙ → importieren), Plugin-Presets sind **read-only/gesperrt** (wie Built-in „Go 7"). Verwaltung lebt im **Plugin-Tab**. |
| 🗑 Entfernen | **APK deinstallieren** via OS-Intent (`ACTION_DELETE`/`ACTION_UNINSTALL_PACKAGE`, keine Spezial-Permission). Cascade beim nächsten Scan: Quellen-Config → `removeSource`, Preset-Zeilen → nach `pluginPackage` geprunt. |
| Tab-Ort | Bestehender `PluginsScreen` (Bottom-Nav `TAB_PLUGINS`, `HomeScreen:290`) — Platzhalter ersetzen. |
| Repo-Browser | **P2**, nicht in P1. |

## Ist-Stand (verifiziert — keine Phantome)

- **Plugin-Loader (Subsystem 1, auf master):** `plugin-host` mit `PluginHost(context)`
  (`discoverPlugins()` für Quellen via `getInstalledPackages(GET_META_DATA)`, ABI-Gate, TOFU,
  `PathClassLoader`), `PluginManifestKeys` (`ENTRY_CLASS`/`ABI_VERSION`), `DiscoveredPlugin`.
  Host braucht `QUERY_ALL_PACKAGES` (im app-Manifest, gesetzt).
- **plugin-api / plugin-sdk:** `ColorPresetSpec(abiVersion, name, saturation, contrast, brightness)`
  existiert in `plugin-api`; `PluginAbi` (VERSION=1, MIN_SUPPORTED=1). `plugin-sdk` = geshadetes
  Single-Jar (für Code-Plugins). **Preset-Plugins brauchen kein SDK** (reine Daten).
- **Color-Filter-Seam (Domain-Regel `komga-eink-color-filter`):** `LocalImageFilter` (ein
  CompositionLocal), `ColorProfile` (+ `toColorFilterOrNull`, `OFF` id 1), `color_profiles`-Tabelle
  (`ColorProfileDao`/`RoomColorProfileRepository`), aktiver Zeiger getrennt als Settings-KV
  `active_color_profile_id`. **Seeding-Gotcha:** Built-ins über `seedColorProfiles(db)` aus ZWEI
  Pfaden — `MIGRATION_6_7` (Upgrades) **und** `SEED_CALLBACK.onCreate` (Frisch-Install). Neues
  Schema = neue Migration **und** `@Database(version=…)`-Bump.
- **In-App-Import (zu entfernen):** `ColorFilterSettingsContent.kt` — `rememberLauncherForActivityResult(
  OpenDocument())` (Z.100), „Preset importieren"-Button (Z.241–246, `s.colorFilterImportPreset`),
  Import-Fehler-Dialog (Z.377–379, `s.colorFilterImportError`). `ColorFilterViewModel.kt` —
  `importPresetJson(json)` (Z.175), `_importError`/`importError`/`dismissImportError` (Z.164–167),
  `org.json`-Parse → `ColorPresetSpec` → `ColorPresetImporter.toProfileOrNull`.
- **`ColorPresetImporter`** (`data/plugin`): `toProfileOrNull(spec)` (ABI-Gate + Clamp) — **bleibt**,
  wird vom Plugin-Pfad genutzt.
- **`ColorProfile`/`ColorProfileEntity`:** Felder id/name/saturation/contrast/brightness/blackPoint/
  whitePoint/gamma/sharpenAmount/sharpenRadius/ditherMode/ditherLevels/builtIn. **Kein** Quellen-/
  Paket-Tag → muss ergänzt werden.
- **`PluginsScreen.kt`:** content-only Platzhalter (`s.pluginsComingSoon`), TopBar von HomeScreen.

## Architektur

```
Plugins-Tab (HomeScreen TAB_PLUGINS → PluginsScreen)
  PluginsViewModel
    ├─ PluginHost.discoverPlugins()             → installierte Quellen-Plugins (Code, Subsystem 1)
    └─ PluginHost.discoverColorPresetPlugins()  → installierte Preset-Plugins (NEU, data-only)
  je Zeile: ⚙ konfigurieren · 🗑 APK deinstallieren (ACTION_DELETE)

Preset-Plugin-APK (plugin/komga-eink-preset-*, data-only)
  AndroidManifest meta-data: COLOR_PRESETS=assets/color_presets.json, ABI_VERSION=1
  assets/color_presets.json = [ ColorPresetSpec, … ]   (reine Daten, KEIN Code)

Host liest:  createPackageContext(pkg, 0).assets.open(name)   ← Flags 0 = nur Ressourcen, kein Code
  → JSON parse → List<ColorPresetSpec> → ColorPresetImporter.toProfileOrNull → ColorProfile
  → color_profiles (builtIn=false, pluginPackage=pkg, gesperrt)
```

### Modul-Schnitt

| Modul | Änderung |
|---|---|
| `plugin-host` | + `PluginManifestKeys.COLOR_PRESETS`; + `DiscoveredPresetPlugin(packageName, abiVersion, presets: List<ColorPresetSpec>)`; + `PluginHost.discoverColorPresetPlugins()` (liest Asset via `createPackageContext(pkg, 0).assets`, ABI-Gate, JSON-Parse mit `org.json`); + `uninstallIntent(packageName)`-Helfer (oder im app). |
| `domain` | `ColorProfile` + `pluginPackage: String? = null`. `ColorProfileRepository`: `deleteByPluginPackage(pkg)` + (falls nötig) `upsert`-Tagging. |
| `data` | `ColorProfileEntity` + `pluginPackage`; **Room-Migration** (neue Version + `seedColorProfiles` unberührt lassen, nur Spalte add — `ALTER TABLE color_profiles ADD COLUMN pluginPackage TEXT`, nullable); `RoomColorProfileRepository` Mapping + `deleteByPluginPackage`. |
| `app` | `PluginsScreen` echt + `PluginsViewModel`; `ColorFilterSettingsContent`/`ColorFilterViewModel` Import-UI raus; Settings „Server hinzufügen": Plugin-Auswahl raus (nur Komga/OPDS bleibt); DI für `PluginHost` ist vorhanden. |
| `plugin/komga-eink-preset-*` | Beispiel-Preset-Plugin (eigenes Git-Repo, gitignored). |

## Loader: Preset-Discovery (data-only)

```kotlin
// plugin-host
data class DiscoveredPresetPlugin(
    val packageName: String,
    val abiVersion: Int,
    val presets: List<ColorPresetSpec>,
)

fun discoverColorPresetPlugins(): List<DiscoveredPresetPlugin> {
    val pm = context.packageManager
    return pm.getInstalledPackages(PackageManager.GET_META_DATA).mapNotNull { pkg ->
        val meta = pkg.applicationInfo?.metaData ?: return@mapNotNull null
        val assetName = meta.getString(PluginManifestKeys.COLOR_PRESETS) ?: return@mapNotNull null
        val abi = readAbiVersion(meta) ?: return@mapNotNull null            // bestehender robuster Int/String-Read
        if (!AbiGate.isCompatible(abi)) return@mapNotNull null
        val json = runCatching {
            context.createPackageContext(pkg.packageName, 0)                 // Flags 0 = NUR Ressourcen, kein Code
                .assets.open(assetName).bufferedReader().use { it.readText() }
        }.getOrNull() ?: return@mapNotNull null
        val specs = parsePresetSpecs(json, abi) ?: return@mapNotNull null    // org.json → List<ColorPresetSpec>
        DiscoveredPresetPlugin(pkg.packageName, abi, specs)
    }
}
```

- **Kein** `CONTEXT_INCLUDE_CODE`, **kein** `PathClassLoader`, **kein** TOFU: es wird ausschließlich
  eine Datei gelesen + geparst, niemals Plugin-Code ausgeführt. Damit entfallen die Multidex-/
  Signatur-/Classloader-Themen der Code-Plugins vollständig.
- `parsePresetSpecs` ist pures Parsen (org.json), pro Eintrag `ColorPresetSpec`; die ABI-/Wert-
  Validierung macht weiterhin `ColorPresetImporter.toProfileOrNull` beim Import.

## Plugin-Tab (`PluginsScreen` + `PluginsViewModel`)

- **State:** `installedSources: List<DiscoveredPlugin>` + `presetPlugins: List<DiscoveredPresetPlugin>`,
  refresht bei Tab-Sicht (`getInstalledPackages` ist günstig).
- **Zeile** (E-Ink-Designsprache: flach, 1.5px-Border, Lucide via `AppIcons`, keine Animation):
  Name + Typ-Label (i18n: „Quelle"/„Farbprofile") + ABI; rechts **⚙** und **🗑**.
  - **⚙ Quelle:** öffnet den bestehenden Add/Config-Server-Flow (Config-Schema-Form + TOFU-Dialog,
    aus Subsystem 1) für genau dieses Plugin.
  - **⚙ Preset-Plugin:** Detail (`EinkModal`/Sub-Screen) listet die Presets des Plugins mit
    „importieren/aktivieren" → `ColorPresetImporter.toProfileOrNull` → `color_profiles`
    (`builtIn=false`, `pluginPackage=pkg`). Bereits importierte zeigen „entfernen".
  - **🗑:** `Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkg"))` (bzw.
    `ACTION_UNINSTALL_PACKAGE`) → OS-Dialog. Nach Rückkehr Re-Scan; verschwundene Pakete →
    `removeSource` (Quellen) bzw. `ColorProfileRepository.deleteByPluginPackage(pkg)` (Presets).
- **+** (Screen-Ebene): Platzhalter/Hinweis „Repos folgen" (P2). Kein Repo-Code in P1.
- TopBar liefert weiter `HomeScreen` (content-only Screen, wie der Platzhalter).
- **Cleanup-Trigger:** beim Tab-Öffnen/`onResume` Plugin-Pakete neu scannen; für jede in
  `color_profiles` getaggte `pluginPackage`, deren APK nicht mehr installiert ist,
  `deleteByPluginPackage`. Aktiver Zeiger zeigt dann ggf. ins Leere → bestehender Fallback `OFF`.

## Persistenz / Migration

- `ColorProfile.pluginPackage: String? = null`; `ColorProfileEntity.pluginPackage: String? = null`.
- Room: neue **nullable** Spalte → `ALTER TABLE color_profiles ADD COLUMN pluginPackage TEXT`
  (nicht-destruktiv, wie `MIGRATION_4_5`/`14_15`; **kein** Recreate). `@Database(version=…)` bumpen,
  Migration in `DataModule.addMigrations(...)` registrieren. **`seedColorProfiles` bleibt** (Built-ins
  unverändert, `pluginPackage=null`) — die Seeding-Gotcha gilt nur für **neue Built-ins**, hier keine.
- Migrations-androidTest (echte Upgrade-DB): Bestandsprofil überlebt, neue Spalte NULL.

## Import-UI entfernen

- `ColorFilterSettingsContent.kt`: `OpenDocument`-Launcher, „Preset importieren"-Button, Import-
  Fehler-Dialog **raus** (+ ungenutzte Imports). 
- `ColorFilterViewModel.kt`: `importPresetJson`, `_importError`/`importError`/`dismissImportError`,
  `ColorPresetImporter`/`ColorPresetSpec`-Import **raus**.
- i18n-Keys `colorFilterImportPreset`/`colorFilterImportError`: entfernen, falls nirgends sonst
  genutzt (sonst belassen). DE+EN-Parität wahren.
- `ColorPresetImporter` **bleibt** (Plugin-Pfad). `ColorPresetSpec` bleibt (plugin-api).

## Settings-Bereinigung

- „Server hinzufügen" (Settings) bietet künftig nur **Komga/OPDS** an; die Plugin-Quellen-Auswahl
  + TOFU-Dialog + `PluginConfigForm` werden vom **Plugin-Tab** aufgerufen (gleiche Composables,
  verschoben, nicht dupliziert). Bestehende Server-Liste (inkl. schon konfigurierter Plugin-Server)
  bleibt unberührt. Falls die Verschiebung zu groß wird, ist ein Zwischenschritt zulässig: Tab ruft
  den **bestehenden** Settings-Flow auf; die Bereinigung der Settings-Auswahl folgt — im selben PR
  dokumentiert.

## Beispiel-Preset-Plugin (`plugin/komga-eink-preset-<gerät>/`)

- Eigenes Git-Repo (gitignored unter `plugin/`). **Minimal-APK, reine Daten:** `AndroidManifest.xml`
  mit `<meta-data COLOR_PRESETS=color_presets.json>` + `ABI_VERSION=1`, `assets/color_presets.json`
  mit 1–2 `ColorPresetSpec` für ein anderes E-Ink (z.B. gedämpftere Sättigung als Go-7). **Kein**
  Code, **keine** Abhängigkeit (auch nicht `plugin-sdk`) — nur Manifest + Asset. Eigener Gradle-
  Build (Wrapper kopiert), installierbares Debug-APK.

## Tests

- **plugin-host (Unit):** `parsePresetSpecs` (gültig / leer / kaputt / ABI zu alt) pur. Discovery-
  Metadata-Parse (soweit ohne echtes PM testbar; sonst E2E).
- **data:** `RoomColorProfileRepository` Tagging + `deleteByPluginPackage` (Unit/Robolectric je
  Projektmuster). Migrations-androidTest (`pluginPackage`-Spalte).
- **E2E (Emulator):** Beispiel-Preset-APK installieren → Plugin-Tab zeigt es → Presets importieren →
  Profil erscheint im Farbfilter, aktivierbar, Bild gefiltert; **🗑/uninstall** → getaggte Profile
  weg (Re-Scan-Prune), aktiver Zeiger fällt auf `OFF`.

## Baureihenfolge

1. `plugin-host`: `COLOR_PRESETS`-Key + `DiscoveredPresetPlugin` + `parsePresetSpecs` (TDD) +
   `discoverColorPresetPlugins` (Asset-Read Flags 0).
2. `domain`/`data`: `ColorProfile.pluginPackage` + Entity + Migration + `deleteByPluginPackage` (TDD/
   Migrations-Test).
3. `app`: `PluginsViewModel` + `PluginsScreen` (Liste, ⚙, 🗑/uninstall + Re-Scan-Prune).
4. `app`: Import-UI in `ColorFilterSettingsContent`/`ColorFilterViewModel` entfernen; Settings-
   „Server hinzufügen" auf Komga/OPDS reduzieren, Plugin-Flow im Tab.
5. Beispiel-Preset-Plugin in `plugin/` bauen.
6. E2E auf dem Emulator gegen das installierte Beispiel-Plugin.

## Risiken

- **Asset-Read aus Fremd-APK:** `createPackageContext(pkg, 0)` muss ohne `CONTEXT_INCLUDE_CODE`
  Ressourcen liefern — verifizieren (sollte; Code wird bewusst nicht geladen). Paket-Sicht via
  bereits gesetztem `QUERY_ALL_PACKAGES`.
- **Uninstall-Intent-Rückkehr:** kein verlässliches Ergebnis-Callback → Cleanup über Re-Scan beim
  Tab-`onResume`, nicht über das Intent-Resultat.
- **Settings/Tab-Doppelung:** Plugin-Flow nur an einer Stelle (Tab); Settings-Auswahl entsprechend
  reduzieren, sonst zwei Wege.

## Bezug

Setzt Subsystem 1 (`plugin-host`, master) voraus. Domain-Regel `komga-eink-color-filter` (Seam +
Seeding-Gotcha) strikt einhalten. Erweitert big-picture Plugin-Typ (c) auf „APK-data-only + Tab".
Memory: [[plugin-host-kavita]], [[local-test-kavita]], [[room-migration-destructive-pitfall]].
Slice P2 (Repo-Browser + APK-Install) = eigene Spec.
