# Font-Plugins P2 — Font-Subsystem (data-only FONT-Plugins, crengine-Live-Registrierung, Lizenz-Gate)

> Datum: 2026-06-14 · Status: Design freigegeben · Phase: P2 von 3 (Font-Plugin-Vorhaben)

## Einordnung: das 3-Spec-Vorhaben

Ziel des Gesamtvorhabens: **Schriftarten als nutzer-installierbare Plugins** für den
NOVEL-/crengine-Reader, mit guter Discover-UX und sauberer Lizenz-Hygiene. Drei Specs, je
eigener Spec→Plan→Bau:

- **P1 (fertig, auf main) — Generische Discover-UX:** Info-Button → Modal mit gerendertem
  README + Vorschau-Bild; `RepoPluginEntry` trägt `previewUrl`/`readmeUrl`/`license`.
- **P2 (dieses Dokument) — Font-Plugin-Subsystem:** `PluginCategory.FONT` (additiv), Discovery,
  TTF aus Fremd-APK-Assets, `nativeAddFont`-JNI (crengine live, ohne Neustart), **SPDX-Lizenz-Gate**
  (Allowlist, harter Block), Settings-Integration (Plugin-Fonts in `NovelFonts` mergen, Picker,
  Live-Sample im echten Font).
- **P3 — 5 Fonts + Specimen-Generator:** Build-Script TTF→Specimen-PNG, fünf data-only Font-APKs
  (OFL-1.1), `repo.json`-Einträge, Provenance/NOTICE je Schrift.

Dieses Dokument spezifiziert **nur P2**. Es baut auf P1 (`license`-Feld, Info-Modal) auf und ist
Voraussetzung für P3 (das die Kategorie + das Gate nutzt).

## Motivation

Heute sind Lese-Schriften fest gebündelt (`NovelFonts.ALL`: DejaVu Sans, Literata, Bitter) und
über `assets/fonts/*.ttf` vor dem ersten crengine-Init registriert. Der Nutzer kann keine
weiteren Schriften hinzufügen. P2 macht Schriften **nutzer-installierbar** über denselben
data-only-Plugin-Mechanismus, der schon Color-Presets, Sprachen, Reader-Presets und UI-Packs
trägt — als neue Kategorie `FONT`, hinter Naht B (Render-Engine) live registriert, ohne
App-Neustart und ohne den crengine-Kern umzubauen.

Schriften sind lizenzrechtlich heikel (viele verbreitete Fonts sind proprietär). Darum ein
**harter SPDX-Lizenz-Gate**: nur Schriften unter einer Allowlist-Lizenz werden je installiert
oder registriert — bei Repo-Install **und** bei Sideload.

## Ziele

1. Neue data-only Kategorie `PluginCategory.FONT` (additiv, `PluginAbi.VERSION` bleibt **2**).
2. crengine registriert Plugin-TTFs **zur Laufzeit** (`nativeAddFont` + `RegisterFont`), ohne
   Neustart und ohne zweites `nativeInit`. Naht B bleibt gewahrt (Domain crengine-frei).
3. TTF-Assets aus dem Fremd-APK in **permanenten** App-Speicher extrahieren, versioniert (kein
   stale TTF bei Plugin-Update).
4. **SPDX-Lizenz-Gate** mit Allowlist `{OFL-1.1, Apache-2.0, CC0-1.0, MIT, Ubuntu-1.0}`, harter
   Block (fehlend/nicht in Liste → nie installiert, nie registriert) — Repo-Install **und** Sideload.
5. Settings-Integration: Plugin-Fonts in die Font-Auswahl gemergt; Picker zeigt **Live-Sample im
   echten Font**; aktive Font-Familie wird bereinigt, wenn ihr Plugin verschwindet.
6. Plugins-Tab listet Font-Plugins (P1-Info-Modal greift automatisch); Repo-Kind `font`.

## Nicht-Ziele (bewusst ausgeklammert)

- **UI-Typografie-Fonts** (App-Schrift) — andere Mechanik (`Typography`/`UiPack`), später.
- Variable Fonts — nur STATIC-Instanzen (Android/Compose-VF-Achsen fragil). P3-Konvention.
- Die 5 realen Schriften, der Specimen-PNG-Generator, `repo.json`-Inhalte → **P3**.
- Per-Font-Gewichte/-Styles (Bold/Italic-Varianten als eigene Faces) — eine Familie = eine TTF
  in P2. Mehr-Face-Familien sind additiv später möglich (FontSpec ist erweiterbar).
- Hyphenation-/Sprach-Pakete für Schriften — orthogonal, nicht P2.

## Architektur

Vier Module sind berührt, streng entlang der bestehenden Schichten. Reihenfolge wie gebaut wird:
Naht B (render) → Vertrag/Discovery (plugin-api/host) → reine Parser/Policy (data) → Verdrahtung/UI
(app).

### A) Naht B — crengine Live-Font-Registrierung

**`render-crengine/.../cpp/cr3_bridge.cpp`** — neue JNI-Funktion nach `nativeInit` (JNI verwendet
**Auto-Naming**, keine `RegisterNatives`-Tabelle → eine zusätzliche Funktion genügt):

```cpp
JNIEXPORT jboolean JNICALL
Java_com_komgareader_render_crengine_CrengineNative_nativeAddFont(
        JNIEnv* env, jobject /*thiz*/, jstring jPath) {
    // Font manager must be booted (InitFontManager ran in nativeInit).
    if (fontMan == nullptr)
        return JNI_FALSE;
    const char* path = env->GetStringUTFChars(jPath, nullptr);
    bool registered = fontMan->RegisterFont(lString8(path));
    LOGI("nativeAddFont(%s) -> %d", path, registered);
    env->ReleaseStringUTFChars(jPath, path);
    // Re-registering the same path returns false (already known) — benign.
    // Success of the manager as a whole is "has at least one usable font".
    return (fontMan->GetFontCount() > 0) ? JNI_TRUE : JNI_FALSE;
}
```

`RegisterFont` ist nach `InitFontManager` jederzeit aufrufbar; ein zweiter Aufruf mit gleichem
Pfad liefert `false` (schon bekannt) — kein Fehler.

**`render-crengine/.../CrengineNative.kt`** — `external fun nativeAddFont(absolutePath: String): Boolean`
(nach `nativeFontFaces`).

**`domain/render/Document.kt`** → `interface ReflowableDocumentFactory` — Default-Methode:

```kotlin
/**
 * Registers an additional reflowable-reader font at runtime (absolute TTF path).
 * Returns true if the engine font manager has at least one usable font afterwards.
 * Default no-op so non-crengine factories need no change (domain stays engine-free).
 */
fun registerFont(absolutePath: String): Boolean = false
```

**`render-crengine/.../CrengineDocumentFactory.kt`** — die Live-/Pending-Mechanik. `nativeInit`
darf **nur einmal** laufen (`fontManagerReady: AtomicBoolean`), darum:

```kotlin
private val pendingFontPaths = mutableListOf<String>()

@Synchronized
override fun registerFont(absolutePath: String): Boolean {
    // Boot not done yet → defer; ensureFontManager() will pass these to the single nativeInit.
    if (!fontManagerReady.get()) {
        if (absolutePath !in pendingFontPaths) pendingFontPaths.add(absolutePath)
        return false
    }
    // Already booted → register live; a second nativeInit is forbidden.
    return CrengineNative.nativeAddFont(absolutePath)
}
```

`ensureFontManager()` gibt **`NovelFonts.ALL`-Pfade + `pendingFontPaths`** an `nativeInit`:

```kotlin
private fun ensureFontManager() {
    if (!fontManagerReady.compareAndSet(false, true)) return
    val builtinPaths = NovelFonts.ALL.map { extractAsset(it.asset, context.cacheDir).absolutePath }
    val pending = synchronized(this) { pendingFontPaths.toList() }
    val fontPaths = (builtinPaths + pending).toTypedArray()
    val hyphDir = extractHyphenationPatterns()
    CrengineNative.nativeInit(fontPaths, hyphDir)
}
```

> **Race-Notiz:** `registerFont` (Install-Zeit, selten) vs. `ensureFontManager`/`nativeApplyLayout`
> (Lese-Zeit). `@Synchronized` auf `registerFont` + `compareAndSet`-Boot decken den realen Fall ab.
> Verbleibende Lücke (Pending-Add genau während `ensureFontManager` die Liste schnappt) ist benigne:
> der Font wird beim nächsten `registerFont`-Aufruf nach Boot live nachgezogen, oder beim nächsten
> `scanLocal` re-registriert (idempotent). Dokumentiert, nicht über ein zweites `nativeInit` „gelöst".

`@Singleton`-Bindung von `ReflowableDocumentFactory` in `AppModule` existiert bereits.

### B) Vertrag + Discovery (plugin-api / plugin-host)

**`plugin-api/.../PluginCategory.kt`** — additiv:

```kotlin
enum class PluginCategory { COLOR_PRESET, READER_PRESET, LANGUAGE, UI_PACK, FONT }
```

**`domain/render/FontSpec.kt`** (NEU, neben `NovelFont.kt`, nur Primitive — keine plugin-api-/
Android-Abhängigkeit):

```kotlin
package com.komgareader.domain.render

/**
 * One installable reflowable-reader font, declared by a data-only FONT plugin's JSON asset.
 * [family] MUST equal the TTF's internal FreeType family name — crengine selects by it
 * (novelFontFamily setting → font.face.default). [license] is per-font SPDX for display/
 * provenance; the install/registration gate is the APK-level SPDX (see spec §E).
 */
data class FontSpec(
    val family: String,
    val label: String,
    val asset: String,
    val license: String = "",
)
```

**`data/.../plugin/FontSpecParser.kt`** (NEU, pur, unit-getestet — spiegelt `parseReaderPresetSpecs`):

```kotlin
fun parseFontSpecs(json: String, manifestAbi: Int): List<FontSpec>? {
    val array = runCatching { JSONArray(json) }.getOrNull() ?: return null
    val out = mutableListOf<FontSpec>()
    for (i in 0 until array.length()) {
        val obj = array.optJSONObject(i) ?: continue
        val family = obj.optString("family").takeIf { it.isNotBlank() } ?: continue
        val asset = obj.optString("asset").takeIf { it.isNotBlank() } ?: continue
        val label = obj.optString("label").takeIf { it.isNotBlank() } ?: family
        val license = obj.optString("license")
        out.add(FontSpec(family = family, label = label, asset = asset, license = license))
    }
    return out
}
```

Schlechte/teil-leere Einträge werden übersprungen (`family`/`asset` Pflicht). `manifestAbi`-Parameter
mitgeführt für Signatur-Symmetrie zu den Geschwister-Parsern (in P2 nicht weiter ausgewertet).

**`plugin-host/.../PluginManifestKeys.kt`** — neuer Key:

```kotlin
const val LICENSE = "com.komgareader.plugin.LICENSE"
```

**`plugin-host/.../DiscoveredDataPlugin.kt`** — zwei Felder (mit Defaults, damit bestehende
Konstruktion + Tests tolerant bleiben):

```kotlin
data class DiscoveredDataPlugin(
    val packageName: String,
    val category: PluginCategory,
    val abiVersion: Int,
    val assetName: String,
    val displayName: String,
    val assetJson: String,
    val license: String = "",
    val versionCode: Long = 0,
)
```

**`plugin-host/.../PluginHost.kt`** — `discoverDataPlugins` liest beide neuen Felder:
`license = meta.getString(PluginManifestKeys.LICENSE)?.trim().orEmpty()` und
`versionCode = PackageInfoCompat.getLongVersionCode(pkg)` (bzw. `pkg.longVersionCode`), und reicht
sie in `DiscoveredDataPlugin` durch. Neue Methode:

```kotlin
/**
 * Extracts a data-only plugin asset (e.g. a TTF) to permanent storage, version-keyed.
 * Path: <destRoot>/<packageName>/<versionCode>/<asset-basename>. Stale version dirs of the
 * same package are removed first (no stale TTF after a plugin update). Returns null on I/O error.
 * Uses createPackageContext(pkg, 0) — resources only, no code load / no TOFU.
 */
fun extractFontAsset(packageName: String, assetPath: String, destRoot: File): File?
```

Implementierungs-Skizze: `versionCode` via `PackageManager.getPackageInfo`; Ziel
`destRoot/<pkg>/<versionCode>/`; vorab andere `<versionCode>`-Unterordner unter `destRoot/<pkg>/`
löschen; Asset über `createPackageContext(pkg, 0).assets.open(assetPath)` nach Ziel kopieren (wenn
nicht vorhanden); `File` zurück, `null` bei Fehler.

### C) Font-Plugin-APK-Form (data-only) — für P3, hier nur Vertrag

Manifest-Metadata: `DATA_CATEGORY=FONT`, `DATA_ASSET=<index.json>`, `ABI_VERSION=2`,
`LICENSE=<SPDX>`, `android:hasCode="false"`. Assets: `assets/fonts/*.ttf` (STATIC) +
`assets/<index.json>` (JSON-Array von `FontSpec`: `family`/`label`/`asset`/`license`). Standalone-Repo,
gitignored. P2 verifiziert den Vertrag mit **einem Test-APK** (eine freie OFL-Schrift, z. B. eine
schon gebündelte zum Sideload-Test) + einem **Negativ-APK** (nicht-allowlisted Lizenz).

### D) Lizenz-Gate (data, pur)

**`data/.../plugin/repo/FontLicensePolicy.kt`** (NEU, pur, unit-getestet):

```kotlin
/** SPDX identifiers permitted for installable FONT plugins. Hard allowlist. */
val FONT_LICENSE_ALLOWLIST: Set<String> = setOf(
    "OFL-1.1", "Apache-2.0", "CC0-1.0", "MIT", "Ubuntu-1.0",
)

/**
 * True iff [spdx] is an allowed font license. Comparison is trimmed + case-insensitive
 * (SPDX matching is officially case-insensitive). Blank or unknown → false → blocked.
 */
fun isLicenseAllowed(spdx: String): Boolean {
    val v = spdx.trim()
    if (v.isEmpty()) return false
    return FONT_LICENSE_ALLOWLIST.any { it.equals(v, ignoreCase = true) }
}
```

**Entscheidung (Case):** case-insensitiv nach trim. **Entscheidung (Autorität):** das Gate
prüft die **APK-Ebene** — Repo-Install: `RepoPluginEntry.license`; Sideload:
`DiscoveredDataPlugin.license` (Manifest-`LICENSE`). `FontSpec.license` ist Anzeige/Provenance pro
Schrift, **nicht** das Gate.

### E) Verdrahtung + UI (app)

**`app/.../data/PluginCatalog.kt`** — neue Dep `ReflowableDocumentFactory`; FONT-Block in
`scanLocal` (analog `rawUiPacks`):

1. `rawFonts = pluginHost.discoverDataPlugins(PluginCategory.FONT)`
2. **Gate B (Sideload):** `allowedFonts = rawFonts.filter { isLicenseAllowed(it.license) }`
   (nicht-erlaubte still skippen — nie registriert).
3. Pro erlaubtem Plugin: `parseFontSpecs(assetJson, abiVersion)` → pro `FontSpec`:
   `extractFontAsset(pkg, spec.asset, fontsRoot)` → `reflowableDocumentFactory.registerFont(file.path)`
   → `NovelFont(family = spec.family, label = spec.label, asset = file.path)` + Eintrag `family → file`.
4. StateFlows setzen:
   - `fontDataPlugins: StateFlow<List<DiscoveredDataPlugin>>` = `rawFonts` (roh, Plugins-Tab; **alle**
     entdeckten, auch geblockte — bleiben deinstallierbar, werden aber nie registriert/gemergt).
   - `allNovelFonts: StateFlow<List<NovelFont>>` = `NovelFonts.ALL + Plugin-Fonts`.
   - `fontSampleFiles: StateFlow<Map<String, File>>` = Plugin-`family→file` **+** extrahierte
     Built-in-TTFs (`NovelFonts.ALL` einmalig nach `filesDir` extrahiert) — fürs Live-Sample.
5. **Active-Font-Prune** (Muster active-ui-pack): `val active = settings.novelFontFamily.first()`;
   wenn `active` nicht in `allNovelFonts`-Familien → `settings.setNovelFontFamily(NovelFonts.DEFAULT)`.

`fontsRoot = File(context.filesDir, "plugin-fonts")`. Registrierung läuft auf `Dispatchers.IO`
(wie die anderen Discovery-Schritte); `registerFont` ist `@Synchronized`.

`installedEntriesOf(...)` bekommt Parameter `fonts: List<DiscoveredDataPlugin> = emptyList()` + Zweig
`fonts.map { InstalledEntry(it.packageName, it.displayName, PluginKind.FONT) }`.

**Gate A (Repo-Install)** in `PluginCatalog.install(row)` — nach Download-OK, **vor**
`verifyAndInstall`:

```kotlin
if (row.item.kind == PluginKind.FONT && !isLicenseAllowed(row.item.entry.license)) {
    dest.delete()
    _error.value = "license_blocked"
    return
}
```

**Repo-Kinds/Filter (data + app):**
- `RepoModels.kt`: `enum class PluginKind { SOURCE, PRESET, LANGUAGE, READER_PRESET, UI_PACK, FONT }`
- `RepoIndexParser.kt`: `pluginKindOf`-Zweig `type.equals("font", ignoreCase = true) -> PluginKind.FONT`
- `PluginCatalogFilter.kt`: `PluginTypeFilter { …, FONTS }` + `matches`-Zweig `FONTS -> this == FONT`
- `PluginFilterMenu.kt`: Filterzeile für `FONTS`
- Der P1-Info-Modal-Pfad (`RepoRow` ℹ) greift für Font-Zeilen automatisch (generisch).

**Settings/Picker/Live-Sample:**
- `SettingsViewModel` + `NovelReaderViewModel` exposen `allNovelFonts = catalog.allNovelFonts.stateIn(...)`
  und `fontSampleFiles = catalog.fontSampleFiles.stateIn(...)`.
- `NovelTypographyControls` (`app/ui/reader/`): neue Parameter
  `availableFonts: List<NovelFont> = NovelFonts.ALL` und `fontFiles: Map<String, File> = emptyMap()`;
  die Font-Schleife iteriert `availableFonts`. **Live-Sample:** jede Zeile rendert ihr `label` (oder
  ein kurzes Muster) in der echten Schrift via `remember(file) { FontFamily(Font(file)) }`
  (`androidx.compose.ui.text.font.Font(File)`, API 26+); fehlt die Datei → Default-Schrift. E-Ink-
  Invarianten bleiben host-erzwungen (kein Crossfade, kein Motion).
- Call-Sites (`NovelTypoPanel` o. ä. + Settings-Reader-Sektion) reichen `availableFonts`/`fontFiles`
  durch.

**i18n** (`app/i18n/Strings.kt`, de+en, Compile-Parität): neuer Key für die `license_blocked`-Meldung
(z. B. `pluginErrorLicenseBlocked`) im `PluginsScreen`-Fehler-`when`; echte Umlaute.

## Datenfluss

```
Font-APK (assets/index.json: [FontSpec], assets/fonts/*.ttf; Manifest DATA_CATEGORY=FONT, LICENSE=SPDX)
  ── Sideload ──────────────────────────────────────────────────────────────────────────────────┐
  └ Repo: repo.json {type:"font", license, fingerprint, apkUrl}                                   │
        → PluginRepoClient.fetchIndex → parseRepoIndex (kind=FONT) → BrowserRow                    │
        → PluginCatalog.install(row): Download → [Gate A: isLicenseAllowed(entry.license)?]        │
              nein → delete + error "license_blocked";  ja → verifyAndInstall (Fingerprint) → OS   │
                                                                                                   │
PluginCatalog.scanLocal() (App-Start / Plugins-Tab onResume) ◄────────── installiert ─────────────┘
  → discoverDataPlugins(FONT) = rawFonts
  → [Gate B: rawFonts.filter { isLicenseAllowed(it.license) }] = allowedFonts
  → je allowed: parseFontSpecs → je Spec: extractFontAsset(filesDir/plugin-fonts/<pkg>/<vc>/) 
        → ReflowableDocumentFactory.registerFont(path)
              ├ Boot noch nicht? → pendingFontPaths (greift beim ersten Novel-Open via nativeInit)
              └ Boot fertig?      → CrengineNative.nativeAddFont(path) → fontMan->RegisterFont
  → allNovelFonts = NovelFonts.ALL + Plugin-NovelFonts ;  fontSampleFiles = family→File
  → Active-Font-Prune: novelFontFamily ∉ allNovelFonts → DEFAULT
        ↓
SettingsViewModel / NovelReaderViewModel exposen allNovelFonts + fontSampleFiles
        ↓
NovelTypographyControls(availableFonts, fontFiles): Picker + Live-Sample im echten Font
        ↓  (Auswahl → setNovelFontFamily)
settings.novelFontFamily → reflowConfig → observeReflowConfig → relayout (Bestand) → crengine wählt
   font.face.default = family → rendert in der gewählten (Plugin-)Schrift
```

## Fehlerbehandlung

- `parseFontSpecs` null/leer (kaputtes JSON, kein gültiger Eintrag) → Plugin liefert keine Fonts,
  kein Crash; das Paket bleibt im Tab sichtbar (deinstallierbar).
- `extractFontAsset` null (I/O) → diese Schrift wird übersprungen, andere Specs laufen weiter.
- `registerFont` false (Boot offen → pending; oder crengine lehnt ab) → Schrift erscheint im Picker
  (gemergt), wird beim nächsten Boot/`scanLocal` registriert; crengine fällt sonst auf die Default-
  Familie zurück (`font.face.default`-Mismatch ist in crengine benigne).
- Lizenz fehlend/nicht-allowlisted → Gate A blockt Install (Datei gelöscht, Fehlermeldung), Gate B
  skippt still. Nie installiert, nie registriert.
- Plugin deinstalliert → `scanLocal` re-scannt, `allNovelFonts` schrumpft, Active-Font-Prune setzt
  ggf. `DEFAULT`. Stale Versions-Ordner werden bei künftigem `extractFontAsset` (Update) aufgeräumt;
  ein Voll-Cleanup deinstallierter Pakete ist YAGNI (filesDir ist klein).

## E-Ink-Invarianten (host-erzwungen)

- Picker-Live-Sample: kein Crossfade/Motion; `FontFamily(Font(file))` ist statisch. Konform
  `animation-gating.md` / `eink-design-language.md`.
- Plugins-Tab + Fehlermeldungen über die bestehenden flachen Onyx-Komponenten; Icons via `AppIcons`.
- Schrift-Rendering im Reader unverändert (crengine → Bitmap → bestehender Refresh-Pfad).

## Lizenz- & Doku-Pflichten

- **Kein** neuer Dritt-Code in P2 (nur eigener Code + ein Test-Font, der schon AGPL-kompatibel ist).
  `data-provenance.md` greift in **P3** (die 5 realen Schriften), nicht hier.
- `komga-doc-sync`: betroffene Rules/Docs im selben Commit nachziehen — `architecture-seams.md`
  (Naht-B-Live-Registrierung + neue Daten-Kategorie FONT), `source-extensibility.md` (FONT als
  data-only-Kategorie), die englischen Community-Docs (README/ARCHITECTURE/PROJECT-STATUS), Memory
  `data-plugin-distribution` (neue Kategorie) + `plugin-host-kavita` (Discovery-Erweiterung).

## Tests

- **Unit, pur (`:data`):**
  - `FontSpecParserTest`: gesetzt (mehrere Specs), `label`-Fallback auf `family`, leeres Array,
    kaputtes JSON → null, Einträge ohne `family`/`asset` übersprungen, `license` durchgereicht.
  - `FontLicensePolicyTest`: jeder Allowlist-Eintrag → true; blank → false; unbekannt → false;
    Case-Varianten (`"ofl-1.1"`, `"APACHE-2.0"`) → true; mit Leerzeichen (`" MIT "`) → true.
- **Unit (`:data`/`:app`), Repo-Kind:** `pluginKindOf("font") == FONT`; `FONTS`-Filter matcht nur FONT.
- **crengine (Instrumented, Emulator `eink_test`):** `nativeAddFont` gegen eine echte TTF auf Platte
  → true; `nativeFontFaces` enthält danach die neue Familie.
- **E2E manuell (nur `emulator-5554`):** Font-Test-APK bauen + installieren → `scanLocal` registriert
  → Picker zeigt die Schrift mit Live-Sample → Novel-Reader rendert darin (Screenshot-Beweis).
  Negativtest: nicht-allowlisted APK → Gate A blockt Install / Gate B skippt → Schrift erscheint nie
  im Picker. Versions-Update: höhere `versionCode` → neuer Ordner, alter weg.
- TDD für die puren Funktionen (Parser, Policy) zuerst (Red→Green→Refactor).

## Risiken / offene Punkte

- **FreeType-Familienname-Mismatch:** `FontSpec.family` muss exakt dem internen TTF-Namen
  entsprechen, sonst wählt crengine die Schrift nicht. Verantwortung des Plugin-Autors (P3 prüft
  via `nativeFontFaces`/`fc-scan`). P2 trusts den Spec-Wert.
- **`Font(File)`-Kompatibilität:** API 26+ (App-`minSdk` prüfen — wenn <26, Fallback-Sample in
  Default-Schrift; zur Implementierung gegen `minSdk` fixieren).
- **`compileOnly`-Vertrag:** Font-APKs sind data-only (`hasCode=false`) → linken **nichts**, kein
  `plugin-sdk` nötig; das JSON-Schema *ist* der Vertrag (wie UI-Packs).
- **`versionCode` in `DiscoveredDataPlugin`:** rein additiv (Default 0); andere Kategorien ignorieren
  es. Keine Migration, kein ABI-Bump.

## Abgrenzung zur Folge

P3 **füllt** die Kategorie: fünf OFL-1.1-Font-APKs (`FontSpec`-Index + STATIC-TTFs +
`LICENSE=OFL-1.1`), ein Specimen-PNG-Generator (TTF → Vorschaubild, das via P1 im README erscheint),
`repo.json`-Einträge (`type:"font"`, `license`, `previewUrl`, `readmeUrl`, `fingerprint`) +
Provenance/NOTICE je Schrift. P3 baut rein auf P2s Kategorie + Gate auf, ohne P2-Code zu ändern.
