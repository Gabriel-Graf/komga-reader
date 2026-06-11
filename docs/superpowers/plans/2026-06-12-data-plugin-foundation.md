# Data-Plugin-Fundament Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Die color-preset-spezifische data-only-Plugin-Mechanik zu einem kategorisierten, generischen Mechanismus verallgemeinern (`PluginCategory`-Enum, ABI 1→2 additiv, `DATA_CATEGORY`/`DATA_ASSET` Manifest-Keys, generische `discoverDataPlugins(category)`), ohne Color-Preset-Regression.

**Architecture:** `plugin-api` (pure JVM) bekommt das `PluginCategory`-Enum + ABI-Bump. `plugin-host` (Android-Lib) bekommt eine reine Manifest-Resolver-Funktion (legacy-alias-fähig), den generischen `DiscoveredDataPlugin`-Typ und `discoverDataPlugins(category)`; `discoverColorPresetPlugins()` wird ein dünner Wrapper darüber, sodass alle Color-Preset-Call-Sites (PluginCatalog, PluginsScreen, E2E) und der Typ `DiscoveredPresetPlugin` unverändert bleiben. Reine Logik unit-getestet, Discovery durch den bestehenden Color-Preset-E2E abgesichert.

**Tech Stack:** Kotlin, Gradle (Android + pure JVM Module), JUnit/kotlin.test (Unit), AndroidJUnit4 (E2E), org.json.

**Worktree:** `/home/gabriel/Documents/Projekte/komga-reader-json-plugins` (Branch `feat/json-data-plugins`). Alle Pfade relativ dazu. Alle `git`/`gradle`-Kommandos aus diesem Verzeichnis.

---

## File Structure

**Neu:**
- `plugin-api/src/main/kotlin/com/komgareader/plugin/PluginCategory.kt` — data-only Kategorie-Enum
- `plugin-host/src/main/kotlin/com/komgareader/plugin/host/DataPluginManifest.kt` — reiner Manifest-Resolver (category + asset, legacy-alias)
- `plugin-host/src/main/kotlin/com/komgareader/plugin/host/DiscoveredDataPlugin.kt` — generischer Discovery-Typ
- `plugin-host/src/test/kotlin/com/komgareader/plugin/host/DataPluginManifestTest.kt` — Resolver-Unit-Tests
- `plugin-host/src/test/kotlin/com/komgareader/plugin/host/AbiGateTest.kt` — ABI-Boundary-Tests (Bump-Beweis)
- `plugins/README.md` — `/plugins/`-Konvention
- `plugins/_template-data/` — data-only-Plugin-Template (Manifest-Snippet, build.gradle.kts, asset, README)

**Geändert:**
- `plugin-api/src/main/kotlin/com/komgareader/plugin/ColorPresetSpec.kt` — `PluginAbi.VERSION = 2`
- `plugin-host/src/main/kotlin/com/komgareader/plugin/host/PluginManifestKeys.kt` — `DATA_CATEGORY`/`DATA_ASSET` ergänzen
- `plugin-host/src/main/kotlin/com/komgareader/plugin/host/PluginHost.kt` — `discoverDataPlugins`, `discoverColorPresetPlugins` als Wrapper
- `.claude/rules/architecture-seams.md` — Loader-Ist-Stand nachziehen (docs-match-code)

**Unberührt (Beweis der Verlustfreiheit):** `DiscoveredPresetPlugin.kt`, `PresetSpecParser.kt`, `PluginCatalog.kt`, `PluginsScreen.kt`, `PluginColorPresetTest.kt`.

---

### Task 1: `PluginCategory`-Enum + ABI-Bump

**Files:**
- Create: `plugin-api/src/main/kotlin/com/komgareader/plugin/PluginCategory.kt`
- Modify: `plugin-api/src/main/kotlin/com/komgareader/plugin/ColorPresetSpec.kt:4-7`
- Test: `plugin-host/src/test/kotlin/com/komgareader/plugin/host/AbiGateTest.kt`

- [ ] **Step 1: Write the failing test** (`AbiGateTest.kt`)

```kotlin
package com.komgareader.plugin.host

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AbiGateTest {
    @Test fun acceptsV1AndV2() {
        assertTrue(AbiGate.isCompatible(1))
        assertTrue(AbiGate.isCompatible(2))
    }

    @Test fun rejectsBelowMinAndAboveVersion() {
        assertFalse(AbiGate.isCompatible(0))
        assertFalse(AbiGate.isCompatible(3))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :plugin-host:test --tests "com.komgareader.plugin.host.AbiGateTest"`
Expected: FAIL — `acceptsV1AndV2` schlägt fehl, weil `AbiGate.isCompatible(2)` noch `false` ist (VERSION=1).

- [ ] **Step 3: Bump ABI VERSION** (`ColorPresetSpec.kt`, Zeilen 4-7)

```kotlin
/** ABI-Gate als zwei Integer (kein semver-String) — Plugin-Plan-Entscheidung 2.
 *  VERSION 2 = additive Erweiterung (data-only Plugin-Kategorien, [PluginCategory]).
 *  MIN_SUPPORTED bleibt 1: color-preset-v1-APKs laden unverändert weiter. */
object PluginAbi {
    const val VERSION = 2
    const val MIN_SUPPORTED = 1
}
```

- [ ] **Step 4: Create `PluginCategory.kt`**

```kotlin
package com.komgareader.plugin

/**
 * Kategorien für **data-only** Plugins (kein Code, nur JSON-Asset). Code-Quellen-Plugins
 * laufen über [com.komgareader.plugin.host.PluginManifestKeys.ENTRY_CLASS] und haben hier
 * KEINEN Eintrag. Neue Kategorie hinzufügen = additiv, kein ABI-Bruch.
 */
enum class PluginCategory { COLOR_PRESET, READER_PRESET, LANGUAGE }
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew :plugin-host:test --tests "com.komgareader.plugin.host.AbiGateTest"`
Expected: PASS (beide Tests grün).

- [ ] **Step 6: Commit**

```bash
git add plugin-api/src/main/kotlin/com/komgareader/plugin/PluginCategory.kt \
        plugin-api/src/main/kotlin/com/komgareader/plugin/ColorPresetSpec.kt \
        plugin-host/src/test/kotlin/com/komgareader/plugin/host/AbiGateTest.kt
git commit -m "feat(plugin-api): PluginCategory enum + ABI VERSION 1->2 (additiv)"
```

---

### Task 2: Manifest-Keys + reiner Manifest-Resolver

Reine Funktion, die aus den drei Manifest-Werten (`DATA_CATEGORY`, `DATA_ASSET`, legacy `COLOR_PRESETS`) die Kategorie + den Asset-Namen ableitet. Legacy-Alias: `COLOR_PRESETS` vorhanden → `COLOR_PRESET` + dessen Wert als Asset. Kein Android, voll unit-testbar.

**Files:**
- Modify: `plugin-host/src/main/kotlin/com/komgareader/plugin/host/PluginManifestKeys.kt`
- Create: `plugin-host/src/main/kotlin/com/komgareader/plugin/host/DataPluginManifest.kt`
- Test: `plugin-host/src/test/kotlin/com/komgareader/plugin/host/DataPluginManifestTest.kt`

- [ ] **Step 1: Write the failing test** (`DataPluginManifestTest.kt`)

```kotlin
package com.komgareader.plugin.host

import com.komgareader.plugin.PluginCategory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DataPluginManifestTest {

    @Test fun resolvesExplicitCategoryAndAsset() {
        val r = resolveDataPluginManifest(
            dataCategory = "LANGUAGE", dataAsset = "lang_es.json", legacyColorPresets = null,
        )
        assertEquals(PluginCategory.LANGUAGE to "lang_es.json", r)
    }

    @Test fun legacyColorPresetsKeyMapsToColorPresetCategory() {
        val r = resolveDataPluginManifest(
            dataCategory = null, dataAsset = null, legacyColorPresets = "presets.json",
        )
        assertEquals(PluginCategory.COLOR_PRESET to "presets.json", r)
    }

    @Test fun explicitCategoryWinsOverLegacy() {
        val r = resolveDataPluginManifest(
            dataCategory = "READER_PRESET", dataAsset = "rp.json", legacyColorPresets = "old.json",
        )
        assertEquals(PluginCategory.READER_PRESET to "rp.json", r)
    }

    @Test fun nullWhenNoKeysPresent() {
        assertNull(resolveDataPluginManifest(null, null, null))
    }

    @Test fun nullWhenCategoryWithoutAsset() {
        assertNull(resolveDataPluginManifest("LANGUAGE", null, null))
    }

    @Test fun nullWhenUnknownCategory() {
        assertNull(resolveDataPluginManifest("BOGUS", "x.json", null))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :plugin-host:test --tests "com.komgareader.plugin.host.DataPluginManifestTest"`
Expected: FAIL — `resolveDataPluginManifest` ist nicht definiert (Compile-Fehler).

- [ ] **Step 3: Add manifest keys** (`PluginManifestKeys.kt`, nach dem `COLOR_PRESETS`-Block einfügen)

```kotlin
    /**
     * Generischer data-only Plugin-Kategorie-Key. Wert = einer von
     * [com.komgareader.plugin.PluginCategory] (`COLOR_PRESET`|`READER_PRESET`|`LANGUAGE`).
     * Zusammen mit [DATA_ASSET] der Nachfolger des kategorie-spezifischen [COLOR_PRESETS].
     */
    const val DATA_CATEGORY = "com.komgareader.plugin.DATA_CATEGORY"

    /** Asset-Dateiname (relativ zu `assets/`) der data-only JSON-Nutzlast. */
    const val DATA_ASSET = "com.komgareader.plugin.DATA_ASSET"
```

- [ ] **Step 4: Create `DataPluginManifest.kt`**

```kotlin
package com.komgareader.plugin.host

import com.komgareader.plugin.PluginCategory

/**
 * Reine Ableitung der data-only Plugin-Identität aus den Manifest-Metadaten. Kein Android,
 * kein I/O — der Host reicht die drei Roh-Werte herein.
 *
 * - Explizites [PluginManifestKeys.DATA_CATEGORY] + [PluginManifestKeys.DATA_ASSET] hat Vorrang.
 * - Legacy: nur [PluginManifestKeys.COLOR_PRESETS] vorhanden → ([PluginCategory.COLOR_PRESET], dessen Wert).
 *   So bleiben alte Color-Preset-APKs ohne Neubau ladbar.
 *
 * Gibt `null`, wenn keine data-only Deklaration vorliegt, das Asset fehlt oder die Kategorie
 * unbekannt ist.
 */
fun resolveDataPluginManifest(
    dataCategory: String?,
    dataAsset: String?,
    legacyColorPresets: String?,
): Pair<PluginCategory, String>? {
    if (dataCategory != null) {
        val asset = dataAsset?.takeIf { it.isNotBlank() } ?: return null
        val category = runCatching { PluginCategory.valueOf(dataCategory.trim()) }.getOrNull() ?: return null
        return category to asset
    }
    val legacy = legacyColorPresets?.takeIf { it.isNotBlank() } ?: return null
    return PluginCategory.COLOR_PRESET to legacy
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew :plugin-host:test --tests "com.komgareader.plugin.host.DataPluginManifestTest"`
Expected: PASS (alle 6 Tests grün).

- [ ] **Step 6: Commit**

```bash
git add plugin-host/src/main/kotlin/com/komgareader/plugin/host/PluginManifestKeys.kt \
        plugin-host/src/main/kotlin/com/komgareader/plugin/host/DataPluginManifest.kt \
        plugin-host/src/test/kotlin/com/komgareader/plugin/host/DataPluginManifestTest.kt
git commit -m "feat(plugin-host): DATA_CATEGORY/DATA_ASSET keys + pure resolver (legacy alias)"
```

---

### Task 3: `DiscoveredDataPlugin`-Typ

**Files:**
- Create: `plugin-host/src/main/kotlin/com/komgareader/plugin/host/DiscoveredDataPlugin.kt`

- [ ] **Step 1: Create `DiscoveredDataPlugin.kt`**

```kotlin
package com.komgareader.plugin.host

import com.komgareader.plugin.PluginCategory

/**
 * Ein installiertes, ABI-kompatibles **data-only** Plugin beliebiger Kategorie. Trägt den bereits
 * gelesenen Asset-JSON-String — KEIN Plugin-Code wird geladen (Asset via `createPackageContext(pkg, 0)`,
 * Flags 0 = nur Ressourcen). Die kategorie-spezifische Interpretation (Parsen/Clampen) passiert
 * darüber (z.B. [parsePresetSpecs] für [PluginCategory.COLOR_PRESET]).
 *
 * Kein `signatureSha256`: data-only Plugins führen nie Code aus → kein TOFU/Signatur-Pinning nötig.
 */
data class DiscoveredDataPlugin(
    val packageName: String,
    val category: PluginCategory,
    val abiVersion: Int,
    val assetName: String,
    val displayName: String,
    val assetJson: String,
)
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :plugin-host:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add plugin-host/src/main/kotlin/com/komgareader/plugin/host/DiscoveredDataPlugin.kt
git commit -m "feat(plugin-host): generic DiscoveredDataPlugin type"
```

---

### Task 4: `discoverDataPlugins` + Color-Preset als Wrapper

`discoverDataPlugins(category)` ersetzt die feste Color-Preset-Scan-Schleife. `discoverColorPresetPlugins()` behält Signatur **und** Rückgabetyp (`List<DiscoveredPresetPlugin>`), delegiert aber an die generische Discovery + `parsePresetSpecs`. Alle Color-Preset-Call-Sites bleiben unberührt.

**Files:**
- Modify: `plugin-host/src/main/kotlin/com/komgareader/plugin/host/PluginHost.kt` (Methode `discoverColorPresetPlugins`, Import-Bereich)

- [ ] **Step 1: Replace `discoverColorPresetPlugins` with generic discovery + wrapper**

Ersetze die komplette `discoverColorPresetPlugins()`-Methode (inkl. ihres KDoc) durch:

```kotlin
    /**
     * Generische Discovery aller installierten, ABI-kompatiblen **data-only** Plugins einer
     * [category]. Liest pro Paket die Kategorie+Asset aus den Manifest-Metadaten (über den reinen
     * [resolveDataPluginManifest], legacy-`COLOR_PRESETS`-fähig) und das Asset via
     * `createPackageContext(pkg, 0)` — **Flags 0 = nur Ressourcen, KEIN Code**: kein PathClassLoader,
     * keine Signatur-Prüfung, kein Multidex. Es wird ausschließlich eine Datei gelesen, nie
     * Plugin-Code ausgeführt. Paket-Sicht via app-Manifest-`QUERY_ALL_PACKAGES`. Die JSON-Interpretation
     * macht der Aufrufer (z.B. [discoverColorPresetPlugins] → [parsePresetSpecs]).
     */
    fun discoverDataPlugins(category: com.komgareader.plugin.PluginCategory): List<DiscoveredDataPlugin> {
        val pm = context.packageManager
        val packages = pm.getInstalledPackages(PackageManager.GET_META_DATA)
        return packages.mapNotNull { pkg ->
            val meta = pkg.applicationInfo?.metaData ?: return@mapNotNull null
            val resolved = resolveDataPluginManifest(
                dataCategory = meta.getString(PluginManifestKeys.DATA_CATEGORY),
                dataAsset = meta.getString(PluginManifestKeys.DATA_ASSET),
                legacyColorPresets = meta.getString(PluginManifestKeys.COLOR_PRESETS),
            ) ?: return@mapNotNull null
            val (resolvedCategory, assetName) = resolved
            if (resolvedCategory != category) return@mapNotNull null
            val abi = readAbiVersion(meta) ?: return@mapNotNull null
            if (!AbiGate.isCompatible(abi)) return@mapNotNull null
            val json = runCatching {
                context.createPackageContext(pkg.packageName, 0)
                    .assets.open(assetName).bufferedReader().use { it.readText() }
            }.getOrNull() ?: return@mapNotNull null
            val label = pkg.applicationInfo?.let { appInfo ->
                runCatching { pm.getApplicationLabel(appInfo).toString() }.getOrNull()?.ifBlank { null }
            } ?: pkg.packageName
            DiscoveredDataPlugin(pkg.packageName, resolvedCategory, abi, assetName, label, json)
        }
    }

    /**
     * Alle installierten, ABI-kompatiblen **data-only** Color-Preset-Plugins (Typ c) — dünner Wrapper
     * über [discoverDataPlugins] für [com.komgareader.plugin.PluginCategory.COLOR_PRESET]. Parst den
     * Asset-JSON via [parsePresetSpecs]; leere/kaputte Assets werden verworfen.
     */
    fun discoverColorPresetPlugins(): List<DiscoveredPresetPlugin> =
        discoverDataPlugins(com.komgareader.plugin.PluginCategory.COLOR_PRESET).mapNotNull { d ->
            val specs = parsePresetSpecs(d.assetJson, d.abiVersion)?.takeIf { it.isNotEmpty() }
                ?: return@mapNotNull null
            DiscoveredPresetPlugin(d.packageName, d.displayName, d.abiVersion, specs)
        }
```

- [ ] **Step 2: Verify `plugin-host` compiles and unit tests stay green**

Run: `./gradlew :plugin-host:test`
Expected: BUILD SUCCESSFUL — `PresetSpecParserTest`, `DataPluginManifestTest`, `AbiGateTest` alle grün. (Keine neuen Unit-Tests hier: die Android-Discovery ist durch den E2E in Task 5 abgesichert, die reine Logik durch Task 2.)

- [ ] **Step 3: Commit**

```bash
git add plugin-host/src/main/kotlin/com/komgareader/plugin/host/PluginHost.kt
git commit -m "refactor(plugin-host): discoverColorPresetPlugins via generic discoverDataPlugins"
```

---

### Task 5: Regressions-Beweis — Color-Preset-E2E grün

Kein Code-Change. Beweist, dass die Generalisierung den bestehenden Color-Preset-Pfad nicht bricht. Der E2E (`PluginColorPresetTest`) überspringt sich selbst, wenn das Kindle-Preset-APK nicht installiert ist (`assumeTrue`).

**Files:** keine (Verifikation).

- [ ] **Step 1: Build the app + androidTest assemble**

Run: `./gradlew :app:assembleDebug :app:assembleDebugAndroidTest`
Expected: BUILD SUCCESSFUL (kompiliert gegen die geänderte `discoverColorPresetPlugins`-Signatur — beweist Call-Site-Kompatibilität in `PluginCatalog`/`PluginsScreen`).

- [ ] **Step 2: Run the Color-Preset E2E (Emulator `eink_test` läuft, Kindle-Preset-APK installiert)**

Wenn das Kindle-Preset-APK verfügbar ist, installieren (`adb install -r <kindle-preset.apk>`), dann:
Run: `./gradlew :app:connectedDebugAndroidTest --tests "com.komgareader.app.ci.PluginColorPresetTest"`
Expected: PASS — `preset_plugin_entdeckt_und_specs_geparst` grün (Plugin via generischer Discovery entdeckt, Specs geparst).
Falls das APK nicht provisioniert werden kann: Test wird **übersprungen** (assumeTrue) — das ist kein Fehler, aber dann im Commit vermerken, dass der E2E mangels APK übersprungen wurde.

- [ ] **Step 3: Commit (nur falls Verifikations-Notizen/keine Datei — sonst überspringen)**

Kein Datei-Change → kein Commit nötig. Ergebnis im Task-Review festhalten.

---

### Task 6: `/plugins/`-Konvention + data-only-Template

Dokumentiert die `/plugins/<name>/`-Konvention (eigenes Git-Repo je Plugin, im Haupt-Repo gitignored) und liefert ein data-only-Template, gegen das Spec-2-Plugins (es/fr/it, Reader-Presets) nur noch gefüllt werden.

**Files:**
- Create: `plugins/README.md`
- Create: `plugins/_template-data/README.md`
- Create: `plugins/_template-data/src/main/AndroidManifest.xml`
- Create: `plugins/_template-data/src/main/assets/data.json`
- Modify: `.gitignore` (Konvention: `/plugins/*/` ignorieren außer `_template-data` + README)

- [ ] **Step 1: Check current .gitignore for the plugin convention**

Run: `grep -n "plugin" .gitignore`
Expected: zeigt die bestehende Kavita-Ignore-Zeile (z.B. `plugin/komga-kavita-source/`). Daran orientieren.

- [ ] **Step 2: Create `plugins/README.md`**

```markdown
# `/plugins/` — externe Plugin-Repos

Jedes Plugin ist ein **eigenes Git-Repo** unter `plugins/<name>/`, im Haupt-Repo **gitignored**
(wie das Kavita-Quellen-Plugin). Eingecheckt bleiben nur diese `README.md` und das Template
`_template-data/`.

## Plugin-Typen

| Typ | Manifest-Deklaration | Code? | Referenz |
|---|---|---|---|
| Quelle (a) | `com.komgareader.plugin.SOURCE` (Entry-Klasse) + `ABI_VERSION` | ja | Kavita-Plugin |
| Color-Preset (c) | `DATA_CATEGORY=COLOR_PRESET` + `DATA_ASSET` + `ABI_VERSION` | nein | `_template-data/` |
| Reader-Preset (c) | `DATA_CATEGORY=READER_PRESET` + `DATA_ASSET` + `ABI_VERSION` | nein | `_template-data/` |
| Sprache (c) | `DATA_CATEGORY=LANGUAGE` + `DATA_ASSET` + `ABI_VERSION` | nein | `_template-data/` |

Data-only Plugins (c) tragen **keinen Code** — nur ein JSON-Asset + Manifest-Metadaten. Der Host
liest das Asset via `createPackageContext(pkg, 0)` (nur Ressourcen, kein Classloader/TOFU).

## Ein data-only Plugin bauen

1. `_template-data/` in ein neues Repo `plugins/<name>/` kopieren.
2. In `AndroidManifest.xml` die `DATA_CATEGORY` setzen, `applicationId`/Label anpassen.
3. `assets/data.json` mit der Nutzlast der Kategorie füllen (Schema siehe Spec 2).
4. APK bauen, auf dem Gerät installieren (`adb install -r …`).
5. In der App: Plugins-Tab → entdeckt das Plugin automatisch (Kategorie-spezifische Heimat-UI).

ABI: aktuell `2` (`PluginAbi.VERSION`). Plugin muss `ABI_VERSION` in `[1, 2]` deklarieren.
```

- [ ] **Step 3: Create `plugins/_template-data/src/main/AndroidManifest.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- Data-only Plugin-Template. KEIN Code — nur Metadaten + assets/data.json.
     DATA_CATEGORY auf COLOR_PRESET | READER_PRESET | LANGUAGE setzen. -->
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application android:label="Komga Data Plugin">
        <meta-data
            android:name="com.komgareader.plugin.DATA_CATEGORY"
            android:value="LANGUAGE" />
        <meta-data
            android:name="com.komgareader.plugin.DATA_ASSET"
            android:value="data.json" />
        <meta-data
            android:name="com.komgareader.plugin.ABI_VERSION"
            android:value="2" />
    </application>
</manifest>
```

- [ ] **Step 4: Create `plugins/_template-data/src/main/assets/data.json`**

```json
[]
```

- [ ] **Step 5: Create `plugins/_template-data/README.md`**

```markdown
# Data-only Plugin-Template

Kopiervorlage für Color-Preset-, Reader-Preset- und Sprach-Plugins (data-only, kein Code).

- `src/main/AndroidManifest.xml` — `DATA_CATEGORY` (COLOR_PRESET|READER_PRESET|LANGUAGE),
  `DATA_ASSET` (Asset-Name), `ABI_VERSION` (in `[1,2]`).
- `src/main/assets/data.json` — die Nutzlast. Form je Kategorie (Schema in Spec 2 /
  `2026-06-12-data-plugin-foundation-design.md`). Aktuell leer (`[]`).

Build: minimales Android-Library/App-APK-Setup analog zum Kindle-Color-Preset-Plugin.
Das Asset wird vom Host nur **gelesen**, nie ausgeführt.
```

- [ ] **Step 6: Add `/plugins/` ignore rule** (`.gitignore`, ans Ende anfügen)

```gitignore
# Externe Plugin-Repos: jedes Plugin eigenes Git-Repo, nicht im Haupt-Repo (siehe plugins/README.md).
/plugins/*/
!/plugins/_template-data/
```

- [ ] **Step 7: Verify the template + README are tracked, foreign repos ignored**

Run: `git add plugins .gitignore && git status --short plugins`
Expected: nur `plugins/README.md`, `plugins/_template-data/**` und `.gitignore` als hinzugefügt; keine fremden Plugin-Repos.

- [ ] **Step 8: Commit**

```bash
git commit -m "docs(plugins): /plugins convention + data-only plugin template"
```

---

### Task 7: docs-match-code — `architecture-seams.md` nachziehen

**Files:**
- Modify: `.claude/rules/architecture-seams.md` (Plugin-Loader-Ist-Abschnitt)

- [ ] **Step 1: Locate the data-only/color-preset Ist-passage**

Run: `grep -n "discoverColorPresetPlugins\|COLOR_PRESETS\|data-only\|createPackageContext(pkg, 0)" .claude/rules/architecture-seams.md`
Expected: findet die Stelle(n), die den Color-Preset-Loader als Ist beschreiben.

- [ ] **Step 2: Add a concise Ist-update sentence** an passender Stelle im Plugin-Loader-Abschnitt (verbatim einfügen):

```markdown
- **Data-only Discovery generalisiert (Ist, 2026-06-12):** Die data-only-Mechanik ist jetzt
  **kategorisiert**: `PluginCategory{COLOR_PRESET,READER_PRESET,LANGUAGE}` (plugin-api, ABI
  `VERSION=2`/`MIN_SUPPORTED=1`, additiv). Manifest-Keys `DATA_CATEGORY`+`DATA_ASSET` (mit
  Legacy-Alias `COLOR_PRESETS`). `PluginHost.discoverDataPlugins(category)` ist die generische
  Discovery (reiner `resolveDataPluginManifest`-Helfer); `discoverColorPresetPlugins()` ist nur
  noch ein dünner Wrapper darüber (+ `parsePresetSpecs`). Reader-Preset-/Sprach-Plugins (Spec 2)
  hängen sich als neue Kategorien ein, ohne Discovery-Umbau. Template/Konvention: `/plugins/`.
```

- [ ] **Step 3: Commit**

```bash
git add .claude/rules/architecture-seams.md
git commit -m "docs(rules): architecture-seams — generalized data-only plugin discovery (Ist)"
```

---

## Self-Review-Ergebnis

- **Spec-Coverage:** A (Task 1) · B Manifest-Keys+Legacy (Task 2) · C `DiscoveredDataPlugin`+`discoverDataPlugins`+Wrapper (Task 3,4) · D Color-Preset-Umhängung+Beweis (Task 4,5) · E `/plugins`+Template (Task 6) · F Tests (Task 1,2,4,5) · docs-match-code (Task 7). Alles abgedeckt.
- **Placeholder-Scan:** kein TBD/„appropriate error handling"; Code in jedem Code-Step vollständig.
- **Typ-Konsistenz:** `resolveDataPluginManifest(dataCategory, dataAsset, legacyColorPresets): Pair<PluginCategory,String>?` identisch in Task 2-Test, Task 2-Impl, Task 4-Call. `DiscoveredDataPlugin(packageName, category, abiVersion, assetName, displayName, assetJson)` identisch Task 3-Def ↔ Task 4-Konstruktion. `discoverColorPresetPlugins(): List<DiscoveredPresetPlugin>` unverändert (Call-Sites grün).
```
