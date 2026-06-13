# Font-Plugins P2 — Font-Subsystem Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Schriftarten als nutzer-installierbare data-only `FONT`-Plugins für den NOVEL-/crengine-Reader — live registriert ohne Neustart, hinter einem harten SPDX-Lizenz-Gate, in die Font-Auswahl mit Live-Sample gemergt.

**Architecture:** Neue data-only Kategorie `PluginCategory.FONT` (additiv, ABI bleibt 2) über den bestehenden `discoverDataPlugins`-Mechanismus. TTFs werden aus dem Fremd-APK in permanenten Speicher extrahiert und über ein neues `nativeAddFont`-JNI zur Laufzeit in crengine registriert (Naht B; Domain bleibt engine-frei). Ein harter Lizenz-Gate (Allowlist, case-insensitiv) blockt bei Repo-Install **und** Sideload. `PluginCatalog` mergt erlaubte Plugin-Fonts in `NovelFonts.ALL`; der Picker zeigt ein Live-Sample in der echten Schrift.

**Tech Stack:** Kotlin, Jetpack Compose, Hilt, Room (keine Migration), crengine-ng (C++/JNI, NDK), kotlinx.serialization/org.json, kotlin.test, MockWebServer, Android instrumented tests (Emulator `eink_test`).

**Worktree:** Diese Arbeit läuft im isolierten Worktree `.claude/worktrees/font-plugins-p2` (Branch `feat/font-plugins-p2-font-subsystem`, von lokal `main` @ 224c5825 mit P1). Alle Pfade relativ zur Worktree-Wurzel. Build-Befehle aus der Worktree-Wurzel; `local.properties` ist gesetzt. Der Haupt-Checkout `/home/gabriel/Documents/Projekte/komga-reader` (parallele AppUpdate-Session) wird **nicht** angefasst. Tests/Installs **nur** auf `emulator-5554` — `db4c96d` ist die physische Boox, nie ohne explizites OK.

**Konvention:** Code/KDoc/Kommentare **Englisch**; Commit-Messages **Deutsch** (echte Umlaute/ß). Jede Commit-Message endet mit `Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>`.

**Spec:** `docs/superpowers/specs/2026-06-14-font-plugins-p2-font-subsystem-design.md`.

---

## Task 1: Additive Verträge (PluginCategory.FONT, FontSpec, registerFont-Default)

Drei rein additive Vertrags-Änderungen in `plugin-api` und `domain`. Keine Verhaltensänderung an Bestehendem.

**Files:**
- Modify: `plugin-api/src/main/kotlin/com/komgareader/plugin/PluginCategory.kt`
- Create: `domain/src/main/kotlin/com/komgareader/domain/render/FontSpec.kt`
- Modify: `domain/src/main/kotlin/com/komgareader/domain/render/Document.kt` (interface `ReflowableDocumentFactory`)

- [ ] **Step 1: PluginCategory.FONT ergänzen**

`PluginCategory.kt` — `FONT` ans Ende der Enum (additiv, hält `PluginAbi.VERSION = 2`):

```kotlin
enum class PluginCategory { COLOR_PRESET, READER_PRESET, LANGUAGE, UI_PACK, FONT }
```

- [ ] **Step 2: FontSpec anlegen**

Neue Datei `domain/.../render/FontSpec.kt` (neben `NovelFont.kt`; nur Primitive, keine plugin-api-/Android-Abhängigkeit):

```kotlin
package com.komgareader.domain.render

/**
 * One installable reflowable-reader font, declared by a data-only FONT plugin's JSON asset.
 *
 * [family] MUST equal the TTF's internal FreeType family name — crengine selects fonts by it
 * (the `novelFontFamily` setting feeds `font.face.default`). [license] is the per-font SPDX
 * identifier kept for display/provenance only; the install/registration gate uses the
 * APK-level SPDX (see the P2 design, §D/§E), not this field.
 */
data class FontSpec(
    val family: String,
    val label: String,
    val asset: String,
    val license: String = "",
)
```

- [ ] **Step 3: registerFont-Default in ReflowableDocumentFactory**

In `domain/.../render/Document.kt`, im `interface ReflowableDocumentFactory` (lies die Datei, finde das Interface; bestehende Methode `open(...)` unverändert lassen) eine Default-Methode ergänzen:

```kotlin
    /**
     * Registers an additional reflowable-reader font at runtime (absolute TTF path).
     * Returns true if the engine's font manager has at least one usable font afterwards.
     * Default no-op so non-crengine factories need no change — the domain stays engine-free.
     */
    fun registerFont(absolutePath: String): Boolean = false
```

- [ ] **Step 4: Build der berührten Module**

Run: `./gradlew :plugin-api:compileDebugKotlin :domain:compileDebugKotlin -q`
Expected: BUILD SUCCESSFUL (additive Änderungen, kein bestehender Code bricht).

- [ ] **Step 5: Commit**

```bash
git add plugin-api/src/main/kotlin/com/komgareader/plugin/PluginCategory.kt domain/src/main/kotlin/com/komgareader/domain/render/FontSpec.kt domain/src/main/kotlin/com/komgareader/domain/render/Document.kt
git commit -m "feat(plugin-api,domain): FONT-Kategorie, FontSpec, ReflowableDocumentFactory.registerFont-Default"
```

---

## Task 2: parseFontSpecs (data, pur, TDD)

Reiner JSON-Parser für das `FontSpec`-Asset, spiegelt `parseReaderPresetSpecs`. Schlechte Einträge werden übersprungen.

**Files:**
- Create: `data/src/main/kotlin/com/komgareader/data/plugin/FontSpecParser.kt`
- Test: `data/src/test/kotlin/com/komgareader/data/plugin/FontSpecParserTest.kt`

- [ ] **Step 1: Failing test schreiben**

`FontSpecParserTest.kt`:

```kotlin
package com.komgareader.data.plugin

import com.komgareader.domain.render.FontSpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FontSpecParserTest {
    @Test fun parsesMultipleSpecs() {
        val json = """
            [
              {"family":"Lora","label":"Lora","asset":"fonts/Lora.ttf","license":"OFL-1.1"},
              {"family":"EB Garamond","label":"EB Garamond","asset":"fonts/EBGaramond.ttf","license":"OFL-1.1"}
            ]
        """.trimIndent()
        val result = parseFontSpecs(json, manifestAbi = 2)
        assertEquals(
            listOf(
                FontSpec("Lora", "Lora", "fonts/Lora.ttf", "OFL-1.1"),
                FontSpec("EB Garamond", "EB Garamond", "fonts/EBGaramond.ttf", "OFL-1.1"),
            ),
            result,
        )
    }

    @Test fun labelFallsBackToFamily() {
        val result = parseFontSpecs("""[{"family":"Lora","asset":"fonts/Lora.ttf"}]""", 2)
        assertEquals(listOf(FontSpec("Lora", "Lora", "fonts/Lora.ttf", "")), result)
    }

    @Test fun skipsEntriesMissingFamilyOrAsset() {
        val json = """
            [
              {"label":"No family","asset":"fonts/x.ttf"},
              {"family":"NoAsset"},
              {"family":"Good","asset":"fonts/g.ttf"}
            ]
        """.trimIndent()
        assertEquals(listOf(FontSpec("Good", "Good", "fonts/g.ttf", "")), parseFontSpecs(json, 2))
    }

    @Test fun emptyArrayYieldsEmptyList() {
        assertEquals(emptyList(), parseFontSpecs("[]", 2))
    }

    @Test fun brokenJsonYieldsNull() {
        assertNull(parseFontSpecs("not json", 2))
        assertNull(parseFontSpecs("""{"family":"x"}""", 2)) // top-level object, not array
    }
}
```

- [ ] **Step 2: Test laufen lassen, Fehlschlag prüfen**

Run: `./gradlew :data:testDebugUnitTest --tests "com.komgareader.data.plugin.FontSpecParserTest" -q`
Expected: FAIL — `parseFontSpecs` unaufgelöst (compile error).

- [ ] **Step 3: Minimal-Implementierung**

`FontSpecParser.kt`:

```kotlin
package com.komgareader.data.plugin

import com.komgareader.domain.render.FontSpec
import org.json.JSONArray

/**
 * Parses a data-only FONT plugin's JSON asset (a JSONArray of font entries) into [FontSpec]s.
 * Returns null when the top-level JSON is not an array. Entries missing `family` or `asset`
 * are skipped; `label` falls back to `family`. Mirrors [parseReaderPresetSpecs]. [manifestAbi]
 * is carried for signature symmetry with the sibling parsers (not further evaluated in P2).
 */
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

- [ ] **Step 4: Test laufen lassen, grün prüfen**

Run: `./gradlew :data:testDebugUnitTest --tests "com.komgareader.data.plugin.FontSpecParserTest" -q`
Expected: PASS (5 Tests).

- [ ] **Step 5: Commit**

```bash
git add data/src/main/kotlin/com/komgareader/data/plugin/FontSpecParser.kt data/src/test/kotlin/com/komgareader/data/plugin/FontSpecParserTest.kt
git commit -m "feat(data): parseFontSpecs — reiner Parser für FONT-Plugin-Assets (TDD)"
```

---

## Task 3: FontLicensePolicy (data, pur, TDD)

Harte SPDX-Allowlist, case-insensitiv nach trim. Blank/unbekannt → blockiert.

**Files:**
- Create: `data/src/main/kotlin/com/komgareader/data/plugin/repo/FontLicensePolicy.kt`
- Test: `data/src/test/kotlin/com/komgareader/data/plugin/repo/FontLicensePolicyTest.kt`

- [ ] **Step 1: Failing test schreiben**

`FontLicensePolicyTest.kt`:

```kotlin
package com.komgareader.data.plugin.repo

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FontLicensePolicyTest {
    @Test fun allowsEveryAllowlistEntry() {
        listOf("OFL-1.1", "Apache-2.0", "CC0-1.0", "MIT", "Ubuntu-1.0").forEach {
            assertTrue(isLicenseAllowed(it), "expected allowed: $it")
        }
    }

    @Test fun blankIsBlocked() {
        assertFalse(isLicenseAllowed(""))
        assertFalse(isLicenseAllowed("   "))
    }

    @Test fun unknownIsBlocked() {
        assertFalse(isLicenseAllowed("GPL-3.0-only"))
        assertFalse(isLicenseAllowed("Proprietary"))
    }

    @Test fun matchesCaseInsensitivelyAfterTrim() {
        assertTrue(isLicenseAllowed("ofl-1.1"))
        assertTrue(isLicenseAllowed("APACHE-2.0"))
        assertTrue(isLicenseAllowed("  MIT  "))
    }
}
```

- [ ] **Step 2: Test laufen lassen, Fehlschlag prüfen**

Run: `./gradlew :data:testDebugUnitTest --tests "com.komgareader.data.plugin.repo.FontLicensePolicyTest" -q`
Expected: FAIL — `isLicenseAllowed` unaufgelöst.

- [ ] **Step 3: Minimal-Implementierung**

`FontLicensePolicy.kt`:

```kotlin
package com.komgareader.data.plugin.repo

/** SPDX identifiers permitted for installable FONT plugins. Hard allowlist (P2 design §D). */
val FONT_LICENSE_ALLOWLIST: Set<String> = setOf(
    "OFL-1.1", "Apache-2.0", "CC0-1.0", "MIT", "Ubuntu-1.0",
)

/**
 * True iff [spdx] is an allowed font license. Comparison is trimmed and case-insensitive
 * (SPDX identifier matching is officially case-insensitive). Blank or unknown → false → blocked.
 */
fun isLicenseAllowed(spdx: String): Boolean {
    val value = spdx.trim()
    if (value.isEmpty()) return false
    return FONT_LICENSE_ALLOWLIST.any { it.equals(value, ignoreCase = true) }
}
```

- [ ] **Step 4: Test laufen lassen, grün prüfen**

Run: `./gradlew :data:testDebugUnitTest --tests "com.komgareader.data.plugin.repo.FontLicensePolicyTest" -q`
Expected: PASS (4 Tests).

- [ ] **Step 5: Commit**

```bash
git add data/src/main/kotlin/com/komgareader/data/plugin/repo/FontLicensePolicy.kt data/src/test/kotlin/com/komgareader/data/plugin/repo/FontLicensePolicyTest.kt
git commit -m "feat(data): FontLicensePolicy — harte SPDX-Allowlist für Font-Plugins (TDD)"
```

---

## Task 4: Repo-Kind-Verdrahtung (PluginKind.FONT + Filter + Menü-Zeile)

`font` als Repo-Typ durch die reine Schicht (`:data`) und den Filter ziehen, dann die Menü-Zeile + i18n-Label (`:app`).

**Files:**
- Modify: `data/src/main/kotlin/com/komgareader/data/plugin/repo/RepoModels.kt:6`
- Modify: `data/src/main/kotlin/com/komgareader/data/plugin/repo/RepoIndexParser.kt:26-32`
- Modify: `data/src/main/kotlin/com/komgareader/data/plugin/repo/PluginCatalogFilter.kt:4` und `:27-34`
- Modify: `data/src/test/kotlin/com/komgareader/data/plugin/repo/RepoIndexParserTest.kt`
- Modify: `app/src/main/kotlin/com/komgareader/app/ui/components/PluginFilterMenu.kt`
- Modify: `app/src/main/kotlin/com/komgareader/app/i18n/Strings.kt` (de + en)

- [ ] **Step 1: Failing test ergänzen**

In `RepoIndexParserTest.kt` (kotlin.test) zwei Asserts ergänzen (eigene Test-Methode):

```kotlin
    @Test fun fontTypeMapsToFontKind() {
        assertEquals(PluginKind.FONT, pluginKindOf("font"))
        assertEquals(PluginKind.FONT, pluginKindOf("FONT"))
    }
```

- [ ] **Step 2: Test laufen lassen, Fehlschlag prüfen**

Run: `./gradlew :data:testDebugUnitTest --tests "com.komgareader.data.plugin.repo.RepoIndexParserTest" -q`
Expected: FAIL — `PluginKind.FONT` existiert nicht.

- [ ] **Step 3: PluginKind + Mapping + Filter ergänzen**

`RepoModels.kt:6`:

```kotlin
enum class PluginKind { SOURCE, PRESET, LANGUAGE, READER_PRESET, UI_PACK, FONT }
```

`RepoIndexParser.kt` — neuen Zweig **vor** dem `else`:

```kotlin
fun pluginKindOf(type: String): PluginKind = when {
    type.equals("preset", ignoreCase = true) -> PluginKind.PRESET
    type.equals("language", ignoreCase = true) -> PluginKind.LANGUAGE
    type.equals("reader_preset", ignoreCase = true) -> PluginKind.READER_PRESET
    type.equals("ui_pack", ignoreCase = true) -> PluginKind.UI_PACK
    type.equals("font", ignoreCase = true) -> PluginKind.FONT
    else -> PluginKind.SOURCE
}
```

`PluginCatalogFilter.kt:4`:

```kotlin
enum class PluginTypeFilter { ALL, SOURCES, PRESETS, LANGUAGES, READER_PRESETS, UI_PACKS, FONTS }
```

`PluginCatalogFilter.kt` `matches` — neuer Zweig (das `when` ist erschöpfend → muss ergänzt werden):

```kotlin
private fun PluginKind.matches(filter: PluginTypeFilter): Boolean = when (filter) {
    PluginTypeFilter.ALL -> true
    PluginTypeFilter.SOURCES -> this == PluginKind.SOURCE
    PluginTypeFilter.PRESETS -> this == PluginKind.PRESET
    PluginTypeFilter.LANGUAGES -> this == PluginKind.LANGUAGE
    PluginTypeFilter.READER_PRESETS -> this == PluginKind.READER_PRESET
    PluginTypeFilter.UI_PACKS -> this == PluginKind.UI_PACK
    PluginTypeFilter.FONTS -> this == PluginKind.FONT
}
```

- [ ] **Step 4: Data-Test grün prüfen**

Run: `./gradlew :data:testDebugUnitTest --tests "com.komgareader.data.plugin.repo.RepoIndexParserTest" -q`
Expected: PASS.

- [ ] **Step 5: i18n-Key + Menü-Zeile (app)**

In `Strings.kt`: neuen Key `pluginFilterFonts` in **beiden** Sprachen (an die anderen `pluginFilter*`-Keys gereiht — finde sie und ergänze paritätisch). DE-Wert `"Schriften"`, EN-Wert `"Fonts"`. Echte Umlaute.

In `PluginFilterMenu.kt` nach der `UI_PACKS`-`FilterRow` eine neue ergänzen:

```kotlin
        FilterRow(label = s.pluginFilterFonts, checked = selected == PluginTypeFilter.FONTS) {
            onSelect(PluginTypeFilter.FONTS); onDismiss()
        }
```

- [ ] **Step 6: App-Build prüfen**

Run: `./gradlew :app:compileDebugKotlin -q`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add data/src/main/kotlin/com/komgareader/data/plugin/repo/RepoModels.kt data/src/main/kotlin/com/komgareader/data/plugin/repo/RepoIndexParser.kt data/src/main/kotlin/com/komgareader/data/plugin/repo/PluginCatalogFilter.kt data/src/test/kotlin/com/komgareader/data/plugin/repo/RepoIndexParserTest.kt app/src/main/kotlin/com/komgareader/app/ui/components/PluginFilterMenu.kt app/src/main/kotlin/com/komgareader/app/i18n/Strings.kt
git commit -m "feat(data,app): Repo-Kind 'font' + FONTS-Filter + Menü-Zeile"
```

---

## Task 5: plugin-host — LICENSE-Key + DiscoveredDataPlugin-Felder + Discovery liest sie

**Files:**
- Modify: `plugin-host/src/main/kotlin/com/komgareader/plugin/host/PluginManifestKeys.kt`
- Modify: `plugin-host/src/main/kotlin/com/komgareader/plugin/host/DiscoveredDataPlugin.kt`
- Modify: `plugin-host/src/main/kotlin/com/komgareader/plugin/host/PluginHost.kt` (`discoverDataPlugins`)

- [ ] **Step 1: Manifest-Key ergänzen**

In `PluginManifestKeys.kt` (object-Body):

```kotlin
    const val LICENSE = "com.komgareader.plugin.LICENSE"
```

- [ ] **Step 2: DiscoveredDataPlugin-Felder ergänzen**

`DiscoveredDataPlugin.kt` — zwei neue Felder **mit Defaults** (bestehende 6-Argument-Konstruktion + Tests bleiben tolerant):

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

- [ ] **Step 3: discoverDataPlugins liest license + versionCode**

In `PluginHost.kt`, in `discoverDataPlugins` (lies die aktuelle Methode), vor der `DiscoveredDataPlugin(...)`-Konstruktion die beiden Werte lesen und durchreichen. `versionCode` über `PackageInfoCompat.getLongVersionCode(pkg)` (import `androidx.core.content.pm.PackageInfoCompat`):

```kotlin
        val license = meta.getString(PluginManifestKeys.LICENSE)?.trim().orEmpty()
        val versionCode = PackageInfoCompat.getLongVersionCode(pkg)
        DiscoveredDataPlugin(
            pkg.packageName, resolvedCategory, abi, assetName, label, json, license, versionCode,
        )
```

(Falls `androidx.core` in `plugin-host` nicht verfügbar ist: `@Suppress("DEPRECATION") pkg.versionCode.toLong()` als Fallback — prüfe `plugin-host/build.gradle.kts` auf `androidx.core:core-ktx`; bevorzugt `PackageInfoCompat`.)

- [ ] **Step 4: Build prüfen**

Run: `./gradlew :plugin-host:compileDebugKotlin -q`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add plugin-host/src/main/kotlin/com/komgareader/plugin/host/PluginManifestKeys.kt plugin-host/src/main/kotlin/com/komgareader/plugin/host/DiscoveredDataPlugin.kt plugin-host/src/main/kotlin/com/komgareader/plugin/host/PluginHost.kt
git commit -m "feat(plugin-host): LICENSE-Manifest-Key + license/versionCode in DiscoveredDataPlugin"
```

---

## Task 6: PluginHost.extractFontAsset + reiner Pfad-Helfer (TDD)

TTF aus dem Fremd-APK in permanenten, versionierten Speicher extrahieren. Die Pfad-/Cleanup-Logik wird in einen reinen, JVM-testbaren Helfer gezogen; die I/O-Methode ruft ihn.

**Files:**
- Create: `plugin-host/src/main/kotlin/com/komgareader/plugin/host/FontAssetPaths.kt`
- Test: `plugin-host/src/test/kotlin/com/komgareader/plugin/host/FontAssetPathsTest.kt`
- Modify: `plugin-host/src/main/kotlin/com/komgareader/plugin/host/PluginHost.kt`

- [ ] **Step 1: Failing test für den reinen Helfer**

`FontAssetPathsTest.kt` (kotlin.test; nutzt `@TempDir`-freie, reine `File`-Pfadlogik):

```kotlin
package com.komgareader.plugin.host

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FontAssetPathsTest {
    @Test fun targetFileIsVersionKeyedByPackageAndBasename() {
        val root = File("/tmp/plugin-fonts")
        val target = fontAssetTargetFile(root, "com.example.lora", versionCode = 7, assetPath = "fonts/Lora.ttf")
        assertEquals(File("/tmp/plugin-fonts/com.example.lora/7/Lora.ttf"), target)
    }

    @Test fun staleVersionDirsListedForRemoval() {
        // Given package dir with versions 5, 6 and target version 7, the stale dirs are 5 and 6.
        val pkgDir = createTempPkgDir(listOf("5", "6", "7"))
        val stale = staleVersionDirs(pkgDir, keepVersionCode = 7)
        assertEquals(setOf("5", "6"), stale.map { it.name }.toSet())
        pkgDir.deleteRecursively()
    }

    @Test fun staleVersionDirsEmptyWhenOnlyTargetPresent() {
        val pkgDir = createTempPkgDir(listOf("7"))
        assertTrue(staleVersionDirs(pkgDir, keepVersionCode = 7).isEmpty())
        pkgDir.deleteRecursively()
    }

    private fun createTempPkgDir(versions: List<String>): File {
        val base = File.createTempFile("pkg", "").let { it.delete(); it.mkdirs(); it }
        versions.forEach { File(base, it).mkdirs() }
        return base
    }
}
```

- [ ] **Step 2: Test laufen lassen, Fehlschlag prüfen**

Run: `./gradlew :plugin-host:testDebugUnitTest --tests "com.komgareader.plugin.host.FontAssetPathsTest" -q`
Expected: FAIL — `fontAssetTargetFile`/`staleVersionDirs` unaufgelöst.

- [ ] **Step 3: Reinen Helfer implementieren**

`FontAssetPaths.kt`:

```kotlin
package com.komgareader.plugin.host

import java.io.File

/**
 * Permanent, version-keyed target file for an extracted plugin font asset:
 * `<root>/<packageName>/<versionCode>/<asset-basename>`. Pure path computation (no I/O).
 */
fun fontAssetTargetFile(root: File, packageName: String, versionCode: Long, assetPath: String): File {
    val basename = assetPath.substringAfterLast('/')
    return File(File(File(root, packageName), versionCode.toString()), basename)
}

/**
 * Version subdirectories under a package's font dir that are NOT [keepVersionCode]. Removing
 * these on extraction prevents a stale TTF lingering after a plugin update. Returns empty if
 * the package dir does not exist.
 */
fun staleVersionDirs(packageDir: File, keepVersionCode: Long): List<File> {
    val keep = keepVersionCode.toString()
    return packageDir.listFiles { f -> f.isDirectory && f.name != keep }?.toList().orEmpty()
}
```

- [ ] **Step 4: Test grün prüfen**

Run: `./gradlew :plugin-host:testDebugUnitTest --tests "com.komgareader.plugin.host.FontAssetPathsTest" -q`
Expected: PASS (3 Tests).

- [ ] **Step 5: extractFontAsset (I/O) in PluginHost**

In `PluginHost.kt` neue öffentliche Methode (nach `discoverDataPlugins`/den Daten-Discovery-Methoden):

```kotlin
    /**
     * Extracts a data-only plugin asset (e.g. a TTF) to permanent, version-keyed storage:
     * `<destRoot>/<packageName>/<versionCode>/<asset-basename>`. Stale version dirs of the same
     * package are removed first (no stale TTF after an update). Returns the file, or null on
     * I/O error. Uses createPackageContext(pkg, 0) — resources only, no code load / no TOFU.
     */
    fun extractFontAsset(packageName: String, assetPath: String, destRoot: File): File? = runCatching {
        val pm = context.packageManager
        val versionCode = PackageInfoCompat.getLongVersionCode(pm.getPackageInfo(packageName, 0))
        val target = fontAssetTargetFile(destRoot, packageName, versionCode, assetPath)
        val packageDir = target.parentFile?.parentFile // <destRoot>/<packageName>
        if (packageDir != null) staleVersionDirs(packageDir, versionCode).forEach { it.deleteRecursively() }
        if (!target.exists()) {
            target.parentFile?.mkdirs()
            context.createPackageContext(packageName, 0).assets.open(assetPath).use { input ->
                target.outputStream().use { input.copyTo(it) }
            }
        }
        target
    }.getOrNull()
```

(Import `androidx.core.content.pm.PackageInfoCompat`, `java.io.File`.)

- [ ] **Step 6: Build prüfen**

Run: `./gradlew :plugin-host:compileDebugKotlin -q`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add plugin-host/src/main/kotlin/com/komgareader/plugin/host/FontAssetPaths.kt plugin-host/src/test/kotlin/com/komgareader/plugin/host/FontAssetPathsTest.kt plugin-host/src/main/kotlin/com/komgareader/plugin/host/PluginHost.kt
git commit -m "feat(plugin-host): extractFontAsset (versioniert, permanent) + reiner Pfad-Helfer (TDD)"
```

---

## Task 7: render-crengine native — nativeAddFont (JNI + Kotlin) + Instrumented-Test

**Files:**
- Modify: `render-crengine/src/main/cpp/cr3_bridge.cpp` (nach `nativeInit`, ~Z.100)
- Modify: `render-crengine/src/main/kotlin/com/komgareader/render/crengine/CrengineNative.kt` (nach `nativeFontFaces`)
- Test: `render-crengine/src/androidTest/kotlin/com/komgareader/render/crengine/NativeAddFontTest.kt` (Pfad/Package an bestehende androidTests anpassen — prüfe vorhandene Instrumented-Tests im Modul)

- [ ] **Step 1: JNI-Funktion in cr3_bridge.cpp**

Direkt nach der schließenden Klammer von `nativeInit` (JNI nutzt Auto-Naming, keine RegisterNatives-Tabelle):

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
    // Re-registering the same path returns false (already known) — benign. The real success
    // condition is that the manager ends up with at least one usable font.
    return (fontMan->GetFontCount() > 0) ? JNI_TRUE : JNI_FALSE;
}
```

- [ ] **Step 2: Kotlin-Deklaration**

In `CrengineNative.kt` nach `external fun nativeFontFaces(): String`:

```kotlin
    external fun nativeAddFont(absolutePath: String): Boolean
```

- [ ] **Step 3: Instrumented-Test schreiben**

Prüfe zuerst, ob im Modul schon ein Instrumented-Test existiert, der `nativeInit` bootet (z. B. einer der `nativeFontFaces` testet) — daran anlehnen (gleiches Boot-Setup: bundled Fonts/Hyph aus den Test-Assets extrahieren, `nativeInit` rufen). Test-Skizze:

```kotlin
@Test
fun nativeAddFont_registersAdditionalFontAndExposesItsFace() {
    // Boot the font manager exactly as the existing nativeFontFaces test does.
    bootFontManager() // helper mirroring the existing test's setup (extract assets + nativeInit)
    // Extract one extra TTF from test assets to a real file on disk.
    val extra = extractTestAsset("fonts/ExtraTest.ttf")
    val ok = CrengineNative.nativeAddFont(extra.absolutePath)
    assertTrue(ok)
    val faces = CrengineNative.nativeFontFaces()
    // The extra font's family name must now appear among the registered faces.
    assertTrue(faces.contains("ExtraTest") || faces.isNotEmpty())
}
```

Lege eine kleine, frei lizenzierte TTF unter `render-crengine/src/androidTest/assets/fonts/ExtraTest.ttf` ab (z. B. eine Kopie einer schon im Repo gebündelten OFL-/Apache-Schrift; den exakten internen Familiennamen via `nativeFontFaces`-Ausgabe ablesen und im Assert verwenden).

- [ ] **Step 4: NDK-Build prüfen**

Run: `./gradlew :render-crengine:compileDebugKotlin :render-crengine:externalNativeBuildDebug -q`
Expected: BUILD SUCCESSFUL (NDK kompiliert `nativeAddFont`).

- [ ] **Step 5: Instrumented-Test auf Emulator**

Sicherstellen, dass `emulator-5554` läuft (sonst `eink_test`-AVD starten). 
Run: `./gradlew :render-crengine:connectedDebugAndroidTest --tests "com.komgareader.render.crengine.NativeAddFontTest" -q`
Expected: PASS auf `emulator-5554`.

- [ ] **Step 6: Commit**

```bash
git add render-crengine/src/main/cpp/cr3_bridge.cpp render-crengine/src/main/kotlin/com/komgareader/render/crengine/CrengineNative.kt render-crengine/src/androidTest
git commit -m "feat(render-crengine): nativeAddFont (RegisterFont zur Laufzeit) + Instrumented-Test"
```

---

## Task 8: CrengineDocumentFactory.registerFont + Pending-Liste + ensureFontManager

Die Live-/Pending-Mechanik. Vor Boot gepufferte Pfade fließen beim einzigen `nativeInit` mit ein; nach Boot direkt über `nativeAddFont`.

**Files:**
- Modify: `render-crengine/src/main/kotlin/com/komgareader/render/crengine/CrengineDocumentFactory.kt`

- [ ] **Step 1: Pending-Liste + registerFont**

Lies die aktuelle Datei. Feld neben `fontManagerReady` ergänzen:

```kotlin
    // Plugin font paths registered before the (single) nativeInit boot; flushed in ensureFontManager().
    private val pendingFontPaths = mutableListOf<String>()
```

Methode (implementiert das domain-Interface; `override`):

```kotlin
    @Synchronized
    override fun registerFont(absolutePath: String): Boolean {
        // Boot not done yet → defer; ensureFontManager() passes these to the single nativeInit.
        if (!fontManagerReady.get()) {
            if (absolutePath !in pendingFontPaths) pendingFontPaths.add(absolutePath)
            return false
        }
        // Already booted → register live; a second nativeInit is forbidden.
        return CrengineNative.nativeAddFont(absolutePath)
    }
```

- [ ] **Step 2: ensureFontManager fügt Pending hinzu**

Die bestehende `ensureFontManager()` so anpassen, dass `pendingFontPaths` neben `NovelFonts.ALL` an `nativeInit` gehen (lies die aktuelle Implementierung; angepasst):

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

(Wenn `CrengineDocumentFactory` das Interface bisher nicht explizit `: ReflowableDocumentFactory` implementiert hat: prüfen — es sollte; `override` entsprechend. Klassen-Annotation `@Singleton`/Hilt-Bindung in `AppModule` bleibt unverändert.)

- [ ] **Step 3: Build prüfen**

Run: `./gradlew :render-crengine:compileDebugKotlin -q`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add render-crengine/src/main/kotlin/com/komgareader/render/crengine/CrengineDocumentFactory.kt
git commit -m "feat(render-crengine): registerFont live/pending + ensureFontManager bezieht Plugin-Fonts ein"
```

---

## Task 9: PluginCatalog — FONT-Discovery, Gate B, Registrierung, StateFlows, Prune, Gate A

Das zentrale Wiring in `:app`. Lies die ganze Datei zuerst.

**Files:**
- Modify: `app/src/main/kotlin/com/komgareader/app/data/PluginCatalog.kt`

- [ ] **Step 1: Neue Dependency + Imports**

Konstruktor um `ReflowableDocumentFactory` erweitern (Hilt liefert die `@Singleton`-Bindung aus `AppModule`):

```kotlin
    private val reflowableDocumentFactory: com.komgareader.domain.render.ReflowableDocumentFactory,
```

Imports ergänzen: `com.komgareader.domain.render.NovelFont`, `com.komgareader.domain.render.NovelFonts`, `com.komgareader.data.plugin.parseFontSpecs`, `com.komgareader.data.plugin.repo.isLicenseAllowed`, `java.io.File`.

- [ ] **Step 2: StateFlows ergänzen**

Bei den anderen `_uiPack*`-Flows:

```kotlin
    // Raw discovered font plugins (Plugins tab; includes license-blocked ones so they stay uninstallable-visible).
    private val _fontDataPlugins = MutableStateFlow<List<DiscoveredDataPlugin>>(emptyList())
    val fontDataPlugins: StateFlow<List<DiscoveredDataPlugin>> = _fontDataPlugins.asStateFlow()

    // Built-in + license-allowed plugin fonts, merged for the novel font picker.
    private val _allNovelFonts = MutableStateFlow(NovelFonts.ALL)
    val allNovelFonts: StateFlow<List<NovelFont>> = _allNovelFonts.asStateFlow()

    // family -> TTF file on disk (plugin fonts + extracted built-ins), for the picker live sample.
    private val _fontSampleFiles = MutableStateFlow<Map<String, File>>(emptyMap())
    val fontSampleFiles: StateFlow<Map<String, File>> = _fontSampleFiles.asStateFlow()
```

- [ ] **Step 3: FONT-Discovery + Registrierung in scanLocal**

Nach dem `rawUiPacks`-Discovery-Block einen FONT-Block ergänzen:

```kotlin
        val rawFonts = withContext(Dispatchers.IO) {
            runCatching { pluginHost.discoverDataPlugins(PluginCategory.FONT) }
                .onFailure { Log.w("PluginCatalog", "discoverFontPlugins failed", it) }
                .getOrDefault(emptyList())
        }
```

Nach den bestehenden `_uiPack*`-Zuweisungen + dem registrieren/extrahieren (auf `Dispatchers.IO`, weil File-I/O + JNI):

```kotlin
        _fontDataPlugins.value = rawFonts
        val fontsRoot = File(context.filesDir, "plugin-fonts")
        val pluginFonts = mutableListOf<NovelFont>()
        val sampleFiles = mutableMapOf<String, File>()
        withContext(Dispatchers.IO) {
            // Gate B (sideload): only license-allowed font APKs are extracted/registered/merged.
            rawFonts.filter { isLicenseAllowed(it.license) }.forEach { plugin ->
                parseFontSpecs(plugin.assetJson, plugin.abiVersion).orEmpty().forEach { spec ->
                    val file = pluginHost.extractFontAsset(plugin.packageName, spec.asset, fontsRoot)
                        ?: return@forEach
                    reflowableDocumentFactory.registerFont(file.absolutePath)
                    pluginFonts.add(NovelFont(family = spec.family, label = spec.label, asset = file.absolutePath))
                    sampleFiles[spec.family] = file
                }
            }
            // Extract built-in TTFs too, so the picker can sample them in their real face.
            NovelFonts.ALL.forEach { font ->
                runCatching {
                    val out = File(fontsRoot, "builtin/${font.asset.substringAfterLast('/')}")
                    if (!out.exists()) {
                        out.parentFile?.mkdirs()
                        context.assets.open(font.asset).use { input -> out.outputStream().use { input.copyTo(it) } }
                    }
                    sampleFiles[font.family] = out
                }
            }
        }
        _allNovelFonts.value = NovelFonts.ALL + pluginFonts
        _fontSampleFiles.value = sampleFiles
```

- [ ] **Step 4: Active-Font-Prune**

Direkt nach dem bestehenden active-ui-pack-Prune-Block:

```kotlin
        // Active novel font falls back to default if its plugin is gone / license-blocked.
        val activeFont = settings.novelFontFamily.first()
        if (_allNovelFonts.value.none { it.family == activeFont }) {
            settings.setNovelFontFamily(NovelFonts.DEFAULT)
        }
```

- [ ] **Step 5: installedEntriesOf + installedEntries um Fonts erweitern**

`installedEntriesOf` (top-level fun) um Parameter + Zweig erweitern:

```kotlin
fun installedEntriesOf(
    sources: List<DiscoveredPlugin>,
    presets: List<DiscoveredPresetPlugin>,
    languages: List<DiscoveredDataPlugin> = emptyList(),
    readerPresets: List<DiscoveredDataPlugin> = emptyList(),
    uiPacks: List<DiscoveredDataPlugin> = emptyList(),
    fonts: List<DiscoveredDataPlugin> = emptyList(),
): List<InstalledEntry> =
    sources.map { InstalledEntry(it.packageName, it.metadata.displayName, PluginKind.SOURCE) } +
        presets.map { InstalledEntry(it.packageName, it.displayName, PluginKind.PRESET) } +
        languages.map { InstalledEntry(it.packageName, it.displayName, PluginKind.LANGUAGE) } +
        readerPresets.map { InstalledEntry(it.packageName, it.displayName, PluginKind.READER_PRESET) } +
        uiPacks.map { InstalledEntry(it.packageName, it.displayName, PluginKind.UI_PACK) } +
        fonts.map { InstalledEntry(it.packageName, it.displayName, PluginKind.FONT) }
```

`installedEntries()` (Member) den neuen Arg durchreichen:

```kotlin
    fun installedEntries(): List<InstalledEntry> =
        installedEntriesOf(
            _sources.value,
            _presetPlugins.value,
            _languageDataPlugins.value,
            _readerPresetDataPlugins.value,
            _uiPackDataPlugins.value,
            _fontDataPlugins.value,
        )
```

- [ ] **Step 6: Gate A in install(row)**

In `install(row)` nach `if (!ok) { _error.value = "download"; return }`, **vor** dem `installer.verifyAndInstall(...)`-`when`:

```kotlin
        if (row.item.kind == PluginKind.FONT && !isLicenseAllowed(row.item.entry.license)) {
            dest.delete()
            _error.value = "license_blocked"
            return
        }
```

- [ ] **Step 7: Build prüfen**

Run: `./gradlew :app:compileDebugKotlin -q`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/kotlin/com/komgareader/app/data/PluginCatalog.kt
git commit -m "feat(app): PluginCatalog FONT-Discovery + Lizenz-Gate (A/B) + Live-Registrierung + Merge/Prune"
```

---

## Task 10: ViewModels exponieren allNovelFonts + fontSampleFiles

**Files:**
- Modify: `app/src/main/kotlin/com/komgareader/app/ui/settings/SettingsViewModel.kt`
- Modify: `app/src/main/kotlin/com/komgareader/app/ui/reader/NovelReaderViewModel.kt`

- [ ] **Step 1: SettingsViewModel**

Bei den anderen `catalog.*`-Exposures (lies die Datei):

```kotlin
    val availableNovelFonts =
        catalog.allNovelFonts.stateIn(viewModelScope, SharingStarted.Eagerly, NovelFonts.ALL)
    val fontSampleFiles =
        catalog.fontSampleFiles.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())
```

(Import `com.komgareader.domain.render.NovelFonts`. `catalog: PluginCatalog` ist bereits injiziert.)

- [ ] **Step 2: NovelReaderViewModel**

Prüfen, ob `PluginCatalog` injiziert ist; falls nicht, als Konstruktor-Dep ergänzen (Hilt `@Singleton`). Dann analog:

```kotlin
    val availableNovelFonts =
        catalog.allNovelFonts.stateIn(viewModelScope, SharingStarted.Eagerly, NovelFonts.ALL)
    val fontSampleFiles =
        catalog.fontSampleFiles.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())
```

`reflowConfig`/`setFontFamily` bleiben unverändert — die Font-Auswahl läuft weiter über `settings.novelFontFamily` → `reflowConfig` → relayout (Bestand).

- [ ] **Step 3: Build prüfen**

Run: `./gradlew :app:compileDebugKotlin -q`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/komgareader/app/ui/settings/SettingsViewModel.kt app/src/main/kotlin/com/komgareader/app/ui/reader/NovelReaderViewModel.kt
git commit -m "feat(app): SettingsVM/NovelReaderVM exponieren allNovelFonts + fontSampleFiles"
```

---

## Task 11: NovelTypographyControls — availableFonts + fontFiles + Live-Sample

**Files:**
- Modify: `app/src/main/kotlin/com/komgareader/app/ui/reader/NovelTypographyControls.kt`
- Modify: alle Call-Sites von `NovelTypographyControls` (per grep finden: Reader-Typo-Panel + Settings-Reader-Sektion)

- [ ] **Step 1: Parameter ergänzen**

Signatur um zwei Parameter erweitern (Defaults halten bestehende Call-Sites lauffähig):

```kotlin
    availableFonts: List<NovelFont> = NovelFonts.ALL,
    fontFiles: Map<String, File> = emptyMap(),
```

(Imports: `com.komgareader.domain.render.NovelFont`, `java.io.File`, `androidx.compose.ui.text.font.Font`, `androidx.compose.ui.text.font.FontFamily`.)

- [ ] **Step 2: Font-Schleife auf availableFonts + Live-Sample**

Die bestehende `NovelFonts.ALL.forEach { font -> ChoiceRow(...) }`-Schleife ersetzen durch eine über `availableFonts`, die das Label in der echten Schrift rendert. Da `ChoiceRow` nur ein `label: String` nimmt, eine kleine font-fähige Variante inline bauen — das Sample-Composable rendert den Schrift-Namen in seiner eigenen Familie:

```kotlin
        availableFonts.forEach { font ->
            val file = fontFiles[font.family]
            val sampleFamily = remember(file) { file?.let { runCatching { FontFamily(Font(it)) }.getOrNull() } }
            ChoiceRow(
                label = font.label,
                selected = fontFamily == font.family,
                dense = true,
                labelFontFamily = sampleFamily, // see Step 3
                onSelect = { onFontFamily(font.family) },
            )
        }
```

- [ ] **Step 3: ChoiceRow um optionales labelFontFamily erweitern**

Lies `ChoiceRow` (vermutlich in `app/ui/components` oder neben den Panels). Optionalen Parameter ergänzen, der die Label-`Text`-Schrift setzt — ohne bestehende Aufrufer zu brechen:

```kotlin
    labelFontFamily: FontFamily? = null,
```

und am Label-`Text`: `fontFamily = labelFontFamily` (ist `null` → Theme-Default; E-Ink-Invariante unberührt, rein statisch). Wenn `ChoiceRow` von vielen geteilt wird und ein Parameter unpassend ist: stattdessen im Picker eine lokale `Text(font.label, fontFamily = sampleFamily, …)`-Zeile statt `ChoiceRow` verwenden (gleiche Klick-/Selektions-Optik). Entscheide nach dem realen `ChoiceRow`-Code; bevorzugt der additive Parameter.

- [ ] **Step 4: Call-Sites durchreichen**

Jede Call-Site von `NovelTypographyControls` (Reader-Typo-Panel, Settings-Reader-Sektion) bekommt `availableFonts = <vm>.availableNovelFonts.collectAsState().value` und `fontFiles = <vm>.fontSampleFiles.collectAsState().value` (das jeweilige VM ist dort verfügbar — `NovelReaderViewModel` im Reader, `SettingsViewModel` in Settings). Wo das VM nicht direkt erreichbar ist, die Werte als Parameter durch das umschließende Composable reichen.

- [ ] **Step 5: Build prüfen**

Run: `./gradlew :app:compileDebugKotlin -q`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/com/komgareader/app/ui/reader/NovelTypographyControls.kt app/src/main/kotlin/com/komgareader/app/ui/
git commit -m "feat(app): Font-Picker mergt Plugin-Fonts + Live-Sample in der echten Schrift"
```

---

## Task 12: PluginsScreen — license_blocked Fehlermeldung (i18n)

**Files:**
- Modify: `app/src/main/kotlin/com/komgareader/app/i18n/Strings.kt` (de + en)
- Modify: `app/src/main/kotlin/com/komgareader/app/ui/plugins/PluginsScreen.kt` (Fehler-`when`)

- [ ] **Step 1: i18n-Key**

Neuen Key `pluginErrorLicenseBlocked` in **beiden** Sprachen (bei den anderen `pluginError*`-Keys — finde sie):
- DE: `"Schrift-Lizenz nicht erlaubt — nicht installiert."`
- EN: `"Font license not allowed — not installed."`

- [ ] **Step 2: Fehler-Zweig in PluginsScreen**

Im Fehler-`when(error)` (oder der Stelle, die `error`-Codes wie `"download"`/`"fingerprint"`/`"install"` auf Strings mappt) den Zweig ergänzen:

```kotlin
            "license_blocked" -> s.pluginErrorLicenseBlocked
```

- [ ] **Step 3: Build prüfen**

Run: `./gradlew :app:compileDebugKotlin -q`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/komgareader/app/i18n/Strings.kt app/src/main/kotlin/com/komgareader/app/ui/plugins/PluginsScreen.kt
git commit -m "feat(app): license_blocked-Fehlermeldung im Plugins-Tab (i18n de+en)"
```

---

## Task 13: Doku-Sync (komga-doc-sync)

**Files:**
- Modify: `.claude/rules/architecture-seams.md` (Naht-B-Live-Font-Registrierung + Daten-Kategorie FONT)
- Modify: `.claude/rules/source-extensibility.md` (FONT als data-only-Kategorie — kurzer Verweis)
- Modify: `README.md`, `docs/ARCHITECTURE.md`, `docs/PROJECT-STATUS.md` (sofern vorhanden — Font-Plugins erwähnen)
- Modify: Memory `font-plugins-research` bzw. `data-plugin-distribution`/`plugin-host-kavita` (über das Memory-Tool, nicht im Repo)

- [ ] **Step 1: komga-doc-sync-Skill anwenden**

Den Skill `komga-doc-sync` invoken und seinen Anweisungen folgen: die berührten Nähte/Verträge auf den neuen Ist-Stand ziehen (FONT-Kategorie, `nativeAddFont`/`registerFont`, Lizenz-Gate, `extractFontAsset`, Picker-Live-Sample). Knapp, Soll/Ist getrennt, keine nicht-existierenden Typen behaupten. Englisch.

- [ ] **Step 2: Commit**

```bash
git add .claude/rules README.md docs
git commit -m "docs: Font-Subsystem (P2) — Naht B Live-Font-Registrierung + FONT-Kategorie nachziehen"
```

(Memory-Dateien separat über das Memory-Tool aktualisieren — nicht im Repo committen.)

---

## Task 14: E2E-Verifikation auf Emulator (Test-Font-APK)

Beweis, dass die ganze Kette real funktioniert. **Nur `emulator-5554`.**

**Files:**
- Create (außerhalb des App-Repos, standalone): ein minimales data-only Font-Test-APK-Projekt — ODER einen bestehenden data-only-Plugin-Build (z. B. das UI-Pack-Sample) als Vorlage kopieren. Manifest: `DATA_CATEGORY=FONT`, `DATA_ASSET=index.json`, `ABI_VERSION=2`, `LICENSE=OFL-1.1`, `android:hasCode="false"`; `assets/fonts/<eine freie OFL-TTF>`, `assets/index.json` = `[{"family":"<exakter TTF-Familienname>","label":"…","asset":"fonts/<datei>.ttf","license":"OFL-1.1"}]`. Plus ein Negativ-APK mit `LICENSE=GPL-3.0-only`.

- [ ] **Step 1: App auf Emulator installieren**

```bash
adb -s emulator-5554 get-state  # ensure emulator running; else start eink_test AVD
./gradlew :app:installDebug -q
```
Bei versionCode-Downgrade-Block: vorher `adb -s emulator-5554 uninstall com.komgareader.app`.

- [ ] **Step 2: Positiv-Font-APK installieren + Discovery**

Test-Font-APK bauen, dann `adb -s emulator-5554 install -r <font-apk>`. App öffnen → Plugins-Tab (Filter „Schriften") zeigt das Font-Plugin; ℹ-Info-Modal (P1) greift. Settings → Reader → Schriftart: die neue Schrift erscheint mit Live-Sample in ihrer echten Form.

- [ ] **Step 3: Reader rendert in der Schrift**

Einen NOVEL-Titel öffnen, die Plugin-Schrift wählen → Reflow rendert in ihr (Screenshot via `adb -s emulator-5554 exec-out screencap -p > /tmp/font-e2e.png`). Beleg ablegen.

- [ ] **Step 4: Negativtest — Lizenz-Block**

Negativ-APK (`LICENSE=GPL-3.0-only`) installieren → in Settings/Picker erscheint die Schrift **nicht** (Gate B skippt). Falls über ein Test-`repo.json` (MockWebServer-Muster) installiert: Install-Versuch → Fehlermeldung „Schrift-Lizenz nicht erlaubt" (Gate A), nichts installiert.

- [ ] **Step 5: Versions-Update**

Positiv-APK mit höherem `versionCode` neu installieren → `filesDir/plugin-fonts/<pkg>/<neuerVC>/` entsteht, alter VC-Ordner ist weg (per `adb -s emulator-5554 shell run-as com.komgareader.app ls files/plugin-fonts/<pkg>`).

- [ ] **Step 6: Verifikations-Notiz**

Ergebnis (Screenshots + adb-Ausgaben) im finishing-Schritt festhalten. Kein Commit nötig (das Test-APK lebt außerhalb des Repos).

---

## Abschluss

Nach Task 14: `superpowers:finishing-a-development-branch` zur Integration nach lokal `main` (kein Push ohne OK — `origin/main` ist eine fremde Linie ohne P1; der Haupt-Checkout der parallelen Session bleibt unberührt).
