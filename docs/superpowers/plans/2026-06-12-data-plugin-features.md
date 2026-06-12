# Data-Plugin-Features Implementation Plan (Spec 2)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax.

**Goal:** Drei Features auf dem data-plugin-Fundament: (A) Quellen-Plugin im „Server hinzufügen"-Selektor, (B) Reader-Preset-Plugins (`READER_PRESET`), (C) Sprach-Plugins (`LANGUAGE`, es/fr/it) via Runtime-Override.

**Architecture:** Folgt dem **plugin-domain-Rezept**: jede data-only Kategorie = reiner Parser (`:data`) + Importer/Apply (Host besitzt die Logik) + Heimat-UI mit garantiertem Default (nie `null`). Discovery läuft über das Fundament (`PluginHost.discoverDataPlugins(category)`), kein Discovery-Umbau. Feature A reicht keinen konkreten Quellen-Typ ins VM (Naht A). E-Ink-Invarianten bleiben host-erzwungen.

**Tech Stack:** Kotlin, Compose, Hilt, Room (keine neue Tabelle), org.json, JUnit/kotlin.test, AndroidJUnit4.

**Worktree:** `/home/gabriel/Documents/Projekte/komga-reader-json-plugins` (Branch `feat/json-data-plugins`). Alle Pfade relativ; alle `git`/`./gradlew` von dort. Fundament (Spec 1) ist bereits gebaut (`PluginCategory`, `discoverDataPlugins`, `DiscoveredDataPlugin`, `resolveDataPluginManifest`).

---

## File Structure

**Neu (Code):**
- `domain/src/main/kotlin/com/komgareader/domain/model/ReaderPreset.kt` — `ReaderPreset` + `ReaderPresetOverrides`
- `domain/src/main/kotlin/com/komgareader/domain/usecase/ApplyReaderPreset.kt` — reine Apply-Reihenfolge (welche Setter)
- `data/src/main/kotlin/com/komgareader/data/plugin/ReaderPresetParser.kt` — `parseReaderPresetSpecs`
- `data/src/main/kotlin/com/komgareader/data/plugin/LanguageSpecParser.kt` — `LanguageSpec` + `parseLanguageSpec`
- `app/src/main/kotlin/com/komgareader/app/i18n/MapBackedStrings.kt` — Runtime-Override-`Strings`
- `app/src/main/kotlin/com/komgareader/app/i18n/LanguageResolver.kt` — Code → aktive `Strings`
- `app/src/main/kotlin/com/komgareader/app/ui/plugins/AddPluginSourceFlow.kt` — geteilter Plugin-Add-Flow (extrahiert)
- Tests: `LanguageSpecParserTest`, `ReaderPresetParserTest`, `ApplyReaderPresetTest` (`:data`/`domain` test), `MapBackedStringsTest` (`app` unit), E2Es in `app/src/androidTest/.../ci/`

**Geändert:**
- `app/.../i18n/Strings.kt` — neue UI-Strings (Interface + StringsDe + StringsEn)
- `app/.../MainActivity.kt` — aktive `Strings` über `LanguageResolver`
- `app/.../data/PluginCatalog.kt` — Discovery `languagePlugins` + `readerPresetPlugins`
- `app/.../ui/settings/SettingsViewModel.kt` — `availableLanguages`, `applyReaderPreset`, `readerPresets`
- `app/.../ui/settings/SettingsContent.kt` — Sprach-Picker (dynamisch), „Preset anwenden"-Zeile, „Plugin"-Segment
- `app/.../ui/plugins/PluginsViewModel.kt` + `PluginsScreen.kt` — Hub zeigt Language/ReaderPreset, `PluginKind` erweitert
- `.claude/rules/architecture-seams.md` — Ist nachziehen (docs-match-code)

**Deliverables `/plugins/`:** `komga-lang-es`, `komga-lang-fr`, `komga-lang-it`, `komga-reader-preset-eink` (je aus `_template-data/`).

---

### Task 1: Neue UI-Strings (Interface + DE + EN)

Setzt den finalen Key-Satz fest, **bevor** `MapBackedStrings` generiert wird. Alle neuen sichtbaren Strings der drei Features.

**Files:** Modify `app/src/main/kotlin/com/komgareader/app/i18n/Strings.kt` (Interface-Block, `StringsDe`, `StringsEn`).

- [ ] **Step 1: Add to the `Strings` interface** (am Ende des Interface-Blocks, vor der schließenden `}` bei ~Zeile 299):

```kotlin
    // Feature A — Quellen-Plugin im Add-Server-Selektor
    val serverKindPlugin: String
    val addServerSelectPlugin: String
    val addServerNoSourcePlugins: String
    // Feature B — Reader-Preset
    val readerPresetApply: String
    val readerPresetNone: String
    val readerPresetConfirmTitle: String
    fun readerPresetConfirmBody(name: String): String
    // Feature C — Sprache
    val languagePluginInstalledHint: String
```

- [ ] **Step 2: Add the German overrides to `StringsDe`** (am Ende des `StringsDe`-Objekts):

```kotlin
    override val serverKindPlugin = "Plugin"
    override val addServerSelectPlugin = "Quellen-Plugin wählen"
    override val addServerNoSourcePlugins = "Keine Quellen-Plugins installiert — im Plugins-Tab hinzufügen."
    override val readerPresetApply = "Preset anwenden"
    override val readerPresetNone = "Keine Reader-Presets installiert"
    override val readerPresetConfirmTitle = "Preset anwenden?"
    override fun readerPresetConfirmBody(name: String) = "„$name" überschreibt die betroffenen Reader-Einstellungen."
    override val languagePluginInstalledHint = "Installiert über Plugin"
```

- [ ] **Step 3: Add the English overrides to `StringsEn`** (am Ende des `StringsEn`-Objekts):

```kotlin
    override val serverKindPlugin = "Plugin"
    override val addServerSelectPlugin = "Choose source plugin"
    override val addServerNoSourcePlugins = "No source plugins installed — add one in the Plugins tab."
    override val readerPresetApply = "Apply preset"
    override val readerPresetNone = "No reader presets installed"
    override val readerPresetConfirmTitle = "Apply preset?"
    override fun readerPresetConfirmBody(name: String) = "\"$name\" overwrites the affected reader settings."
    override val languagePluginInstalledHint = "Installed via plugin"
```

- [ ] **Step 4: Verify it compiles** (Compile-Zeit-Parität DE/EN erzwingt das Interface):

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/komgareader/app/i18n/Strings.kt
git commit -m "feat(i18n): add UI strings for plugin-source/reader-preset/language features"
```

---

### Task 2: Reader-Preset-Parser + Domain + Apply

**Files:**
- Create: `domain/src/main/kotlin/com/komgareader/domain/model/ReaderPreset.kt`
- Create: `domain/src/main/kotlin/com/komgareader/domain/usecase/ApplyReaderPreset.kt`
- Create: `data/src/main/kotlin/com/komgareader/data/plugin/ReaderPresetParser.kt`
- Test: `data/src/test/kotlin/com/komgareader/data/plugin/ReaderPresetParserTest.kt`
- Test: `domain/src/test/kotlin/com/komgareader/domain/usecase/ApplyReaderPresetTest.kt`

- [ ] **Step 1: Write the failing parser test** (`ReaderPresetParserTest.kt`)

```kotlin
package com.komgareader.data.plugin

import com.komgareader.domain.model.ReaderPreset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReaderPresetParserTest {
    private val abi = 2

    @Test fun parsesPartialPreset() {
        val json = """
            [{"abiVersion":2,"name":"Roman komfortabel",
              "settings":{"novelFontSizeEm":1.2,"novelLineHeight":1.4,"novelMarginPreset":"WIDE"}}]
        """.trimIndent()
        val presets = parseReaderPresetSpecs(json, abi)
        assertEquals(1, presets!!.size)
        val p = presets.single()
        assertEquals("Roman komfortabel", p.name)
        assertEquals(1.2f, p.overrides.novelFontSizeEm)
        assertEquals(1.4f, p.overrides.novelLineHeight)
        assertEquals("WIDE", p.overrides.novelMarginPreset)
        assertNull(p.overrides.displayMode)        // nicht gesetzt → null
        assertNull(p.overrides.webtoonOverlapPercent)
    }

    @Test fun parsesAllFields() {
        val json = """
            [{"abiVersion":2,"name":"Voll","settings":{
              "displayMode":"EINK","deviceManagedRefresh":false,"webtoonOverlapPercent":30,
              "novelFontSizeEm":1.0,"novelLineHeight":1.0,"novelMarginPreset":"NORMAL",
              "novelFontFamily":"DejaVu Sans","novelTextAlign":"JUSTIFY","novelHyphenationLang":"de",
              "novelFontWeight":400,"guidedPanelOverlay":true}}]
        """.trimIndent()
        val o = parseReaderPresetSpecs(json, abi)!!.single().overrides
        assertEquals("EINK", o.displayMode)
        assertEquals(false, o.deviceManagedRefresh)
        assertEquals(30, o.webtoonOverlapPercent)
        assertEquals(400, o.novelFontWeight)
        assertEquals(true, o.guidedPanelOverlay)
    }

    @Test fun skipsEntryWithoutName() {
        val json = """[{"settings":{"novelFontSizeEm":1.0}}]"""
        assertTrue(parseReaderPresetSpecs(json, abi)!!.isEmpty())
    }

    @Test fun nullWhenNotArray() {
        assertNull(parseReaderPresetSpecs("{}", abi))
    }

    @Test fun emptyArrayParsesToEmptyList() {
        assertEquals(emptyList(), parseReaderPresetSpecs("[]", abi))
    }

    @Test fun ignoresUnknownAndWrongTypeKeys() {
        val json = """[{"name":"X","settings":{"bogus":1,"novelFontSizeEm":"notanumber","novelLineHeight":1.3}}]"""
        val o = parseReaderPresetSpecs(json, abi)!!.single().overrides
        assertNull(o.novelFontSizeEm)              // falscher Typ → übersprungen
        assertEquals(1.3f, o.novelLineHeight)
    }
}
```

- [ ] **Step 2: Run, expect FAIL** (types undefined)

Run: `./gradlew :data:test --tests "com.komgareader.data.plugin.ReaderPresetParserTest"`
Expected: FAIL (Compile-Fehler — `ReaderPreset`/`parseReaderPresetSpecs` fehlen).

- [ ] **Step 3: Create `ReaderPreset.kt`** (domain)

```kotlin
package com.komgareader.domain.model

/**
 * Ein benannter **Teil-**Snapshot der Reader-Einstellungen (data-only Plugin-Kategorie READER_PRESET).
 * Jedes Feld ist nullable — `null` = vom Preset NICHT gesetzt, beim Anwenden unberührt gelassen.
 * Generische, quellen-/geräte-neutrale Feldnamen (spiegeln die SettingsRepository-Setter).
 */
data class ReaderPresetOverrides(
    val displayMode: String? = null,
    val deviceManagedRefresh: Boolean? = null,
    val webtoonOverlapPercent: Int? = null,
    val novelFontSizeEm: Float? = null,
    val novelLineHeight: Float? = null,
    val novelMarginPreset: String? = null,
    val novelFontFamily: String? = null,
    val novelTextAlign: String? = null,
    val novelHyphenationLang: String? = null,
    val novelFontWeight: Int? = null,
    val guidedPanelOverlay: Boolean? = null,
)

data class ReaderPreset(
    val name: String,
    val abiVersion: Int,
    val overrides: ReaderPresetOverrides,
)
```

- [ ] **Step 4: Create `ReaderPresetParser.kt`** (`:data`)

```kotlin
package com.komgareader.data.plugin

import com.komgareader.domain.model.ReaderPreset
import com.komgareader.domain.model.ReaderPresetOverrides
import org.json.JSONArray
import org.json.JSONObject

/**
 * Parst ein JSON-Array benannter Reader-Preset-Einträge (data-only Plugin-Kategorie READER_PRESET)
 * in [ReaderPreset]. Rein — kein Android/I/O; das JSON kommt vom Host als String. `null` wenn der
 * Top-Level kein Array ist. Einträge ohne `name` werden übersprungen. Im `settings`-Objekt werden
 * unbekannte oder typ-falsche Keys übersprungen (gültige bleiben) → robuste Teil-Snapshots.
 *
 * Fehlt einem Eintrag `abiVersion`, erbt er die im Manifest deklarierte [manifestAbi]. ABI-/Wert-
 * Validierung beim Anwenden ist Sache der UI/des Hosts (Setter clampen selbst).
 */
fun parseReaderPresetSpecs(json: String, manifestAbi: Int): List<ReaderPreset>? {
    val array = runCatching { JSONArray(json) }.getOrNull() ?: return null
    val presets = mutableListOf<ReaderPreset>()
    for (i in 0 until array.length()) {
        val obj = array.optJSONObject(i) ?: continue
        val name = obj.optString("name").takeIf { it.isNotBlank() } ?: continue
        val abi = if (obj.has("abiVersion")) obj.optInt("abiVersion", manifestAbi) else manifestAbi
        val settings = obj.optJSONObject("settings") ?: JSONObject()
        presets.add(ReaderPreset(name, abi, settings.toOverrides()))
    }
    return presets
}

private fun JSONObject.toOverrides(): ReaderPresetOverrides = ReaderPresetOverrides(
    displayMode = optStringOrNull("displayMode"),
    deviceManagedRefresh = optBoolOrNull("deviceManagedRefresh"),
    webtoonOverlapPercent = optIntOrNull("webtoonOverlapPercent"),
    novelFontSizeEm = optFloatOrNull("novelFontSizeEm"),
    novelLineHeight = optFloatOrNull("novelLineHeight"),
    novelMarginPreset = optStringOrNull("novelMarginPreset"),
    novelFontFamily = optStringOrNull("novelFontFamily"),
    novelTextAlign = optStringOrNull("novelTextAlign"),
    novelHyphenationLang = optStringOrNull("novelHyphenationLang"),
    novelFontWeight = optIntOrNull("novelFontWeight"),
    guidedPanelOverlay = optBoolOrNull("guidedPanelOverlay"),
)

private fun JSONObject.optStringOrNull(key: String): String? =
    if (has(key) && get(key) is String) getString(key).takeIf { it.isNotBlank() } else null
private fun JSONObject.optBoolOrNull(key: String): Boolean? =
    if (has(key) && get(key) is Boolean) getBoolean(key) else null
private fun JSONObject.optIntOrNull(key: String): Int? =
    if (has(key) && get(key) is Number) getInt(key) else null
private fun JSONObject.optFloatOrNull(key: String): Float? =
    if (has(key) && get(key) is Number) getDouble(key).toFloat() else null
```

- [ ] **Step 5: Run, expect PASS**

Run: `./gradlew :data:test --tests "com.komgareader.data.plugin.ReaderPresetParserTest"`
Expected: PASS (6 Tests).

- [ ] **Step 6: Write the failing apply test** (`ApplyReaderPresetTest.kt`, domain)

```kotlin
package com.komgareader.domain.usecase

import com.komgareader.domain.model.ReaderPresetOverrides
import kotlin.test.Test
import kotlin.test.assertEquals

class ApplyReaderPresetTest {
    @Test fun appliesOnlyNonNullFields() {
        val applied = linkedMapOf<String, Any>()
        val sink = ReaderPresetSink(
            setDisplayMode = { applied["displayMode"] = it },
            setDeviceManagedRefresh = { applied["deviceManagedRefresh"] = it },
            setWebtoonOverlapPercent = { applied["webtoonOverlapPercent"] = it },
            setNovelFontSizeEm = { applied["novelFontSizeEm"] = it },
            setNovelLineHeight = { applied["novelLineHeight"] = it },
            setNovelMarginPreset = { applied["novelMarginPreset"] = it },
            setNovelFontFamily = { applied["novelFontFamily"] = it },
            setNovelTextAlign = { applied["novelTextAlign"] = it },
            setNovelHyphenationLang = { applied["novelHyphenationLang"] = it },
            setNovelFontWeight = { applied["novelFontWeight"] = it },
            setGuidedPanelOverlay = { applied["guidedPanelOverlay"] = it },
        )
        applyReaderPreset(ReaderPresetOverrides(novelFontSizeEm = 1.2f, novelMarginPreset = "WIDE"), sink)
        assertEquals(mapOf("novelFontSizeEm" to 1.2f, "novelMarginPreset" to "WIDE"), applied)
    }
}
```

- [ ] **Step 7: Run, expect FAIL** then **create `ApplyReaderPreset.kt`** (domain)

```kotlin
package com.komgareader.domain.usecase

import com.komgareader.domain.model.ReaderPresetOverrides

/**
 * Senke für [applyReaderPreset] — entkoppelt die reine Apply-Reihenfolge von der konkreten
 * SettingsRepository-Verdrahtung (testbar ohne Android). Die Shell (ViewModel) reicht die echten
 * Setter herein.
 */
class ReaderPresetSink(
    val setDisplayMode: (String) -> Unit,
    val setDeviceManagedRefresh: (Boolean) -> Unit,
    val setWebtoonOverlapPercent: (Int) -> Unit,
    val setNovelFontSizeEm: (Float) -> Unit,
    val setNovelLineHeight: (Float) -> Unit,
    val setNovelMarginPreset: (String) -> Unit,
    val setNovelFontFamily: (String) -> Unit,
    val setNovelTextAlign: (String) -> Unit,
    val setNovelHyphenationLang: (String) -> Unit,
    val setNovelFontWeight: (Int) -> Unit,
    val setGuidedPanelOverlay: (Boolean) -> Unit,
)

/** Wendet nur die gesetzten (nicht-null) Felder eines Presets über die [sink] an. */
fun applyReaderPreset(o: ReaderPresetOverrides, sink: ReaderPresetSink) {
    o.displayMode?.let(sink.setDisplayMode)
    o.deviceManagedRefresh?.let(sink.setDeviceManagedRefresh)
    o.webtoonOverlapPercent?.let(sink.setWebtoonOverlapPercent)
    o.novelFontSizeEm?.let(sink.setNovelFontSizeEm)
    o.novelLineHeight?.let(sink.setNovelLineHeight)
    o.novelMarginPreset?.let(sink.setNovelMarginPreset)
    o.novelFontFamily?.let(sink.setNovelFontFamily)
    o.novelTextAlign?.let(sink.setNovelTextAlign)
    o.novelHyphenationLang?.let(sink.setNovelHyphenationLang)
    o.novelFontWeight?.let(sink.setNovelFontWeight)
    o.guidedPanelOverlay?.let(sink.setGuidedPanelOverlay)
}
```

- [ ] **Step 8: Run both test suites, expect PASS**

Run: `./gradlew :data:test --tests "com.komgareader.data.plugin.ReaderPresetParserTest" :domain:test --tests "com.komgareader.domain.usecase.ApplyReaderPresetTest"`
Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add domain/src/main/kotlin/com/komgareader/domain/model/ReaderPreset.kt domain/src/main/kotlin/com/komgareader/domain/usecase/ApplyReaderPreset.kt domain/src/test/kotlin/com/komgareader/domain/usecase/ApplyReaderPresetTest.kt data/src/main/kotlin/com/komgareader/data/plugin/ReaderPresetParser.kt data/src/test/kotlin/com/komgareader/data/plugin/ReaderPresetParserTest.kt
git commit -m "feat(reader-preset): ReaderPreset model + parser + pure apply use-case"
```

---

### Task 3: Language-Spec-Parser

**Files:**
- Create: `data/src/main/kotlin/com/komgareader/data/plugin/LanguageSpecParser.kt`
- Test: `data/src/test/kotlin/com/komgareader/data/plugin/LanguageSpecParserTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.komgareader.data.plugin

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LanguageSpecParserTest {
    private val abi = 2

    @Test fun parsesLanguage() {
        val json = """
            {"abiVersion":2,"code":"es","name":"Español",
             "strings":{"appName":"Komga Reader","libraryTitle":"Biblioteca",
                        "downloadingChapters":"Cargando {count} capítulos…"}}
        """.trimIndent()
        val spec = parseLanguageSpec(json, abi)!!
        assertEquals("es", spec.code)
        assertEquals("Español", spec.name)
        assertEquals("Biblioteca", spec.strings["libraryTitle"])
        assertEquals("Cargando {count} capítulos…", spec.strings["downloadingChapters"])
    }

    @Test fun nullWhenCodeMissing() {
        assertNull(parseLanguageSpec("""{"name":"X","strings":{}}""", abi))
    }

    @Test fun nullWhenNameMissing() {
        assertNull(parseLanguageSpec("""{"code":"es","strings":{}}""", abi))
    }

    @Test fun nullWhenStringsMissing() {
        assertNull(parseLanguageSpec("""{"code":"es","name":"Español"}""", abi))
    }

    @Test fun nullWhenNotObject() {
        assertNull(parseLanguageSpec("[]", abi))
    }
}
```

- [ ] **Step 2: Run, expect FAIL**

Run: `./gradlew :data:test --tests "com.komgareader.data.plugin.LanguageSpecParserTest"`

- [ ] **Step 3: Create `LanguageSpecParser.kt`**

```kotlin
package com.komgareader.data.plugin

import org.json.JSONObject

/** Eine entdeckte Sprach-Nutzlast (data-only Plugin-Kategorie LANGUAGE). */
data class LanguageSpec(
    val code: String,
    val name: String,
    val abiVersion: Int,
    val strings: Map<String, String>,
)

/**
 * Parst ein einzelnes Sprach-JSON-Objekt in [LanguageSpec]. Rein — kein Android/I/O. `null`, wenn
 * der Top-Level kein Objekt ist oder `code`/`name`/`strings` fehlen. Nicht-String-Werte im
 * `strings`-Objekt werden übersprungen. Fehlt `abiVersion`, erbt es [manifestAbi].
 */
fun parseLanguageSpec(json: String, manifestAbi: Int): LanguageSpec? {
    val obj = runCatching { JSONObject(json) }.getOrNull() ?: return null
    val code = obj.optString("code").takeIf { it.isNotBlank() } ?: return null
    val name = obj.optString("name").takeIf { it.isNotBlank() } ?: return null
    val stringsObj = obj.optJSONObject("strings") ?: return null
    val abi = if (obj.has("abiVersion")) obj.optInt("abiVersion", manifestAbi) else manifestAbi
    val strings = buildMap {
        stringsObj.keys().forEach { key ->
            if (stringsObj.get(key) is String) put(key, stringsObj.getString(key))
        }
    }
    return LanguageSpec(code, name, abi, strings)
}
```

- [ ] **Step 4: Run, expect PASS**

Run: `./gradlew :data:test --tests "com.komgareader.data.plugin.LanguageSpecParserTest"`

- [ ] **Step 5: Commit**

```bash
git add data/src/main/kotlin/com/komgareader/data/plugin/LanguageSpecParser.kt data/src/test/kotlin/com/komgareader/data/plugin/LanguageSpecParserTest.kt
git commit -m "feat(language): LanguageSpec + parseLanguageSpec (pure)"
```

---

### Task 4: `MapBackedStrings` (Runtime-Override)

`MapBackedStrings` implementiert das **gesamte** `Strings`-Interface. **Mechanisch aus `Strings.kt` generieren** — der Compiler erzwingt Vollständigkeit (jeder fehlende Member = Compile-Fehler; so kann nichts driften). Properties: `overrides["<name>"] ?: fallback.<name>`. Funktionen: Template-Interpolation der Platzhalter, sonst Fallback.

**Files:**
- Create: `app/src/main/kotlin/com/komgareader/app/i18n/MapBackedStrings.kt`
- Test: `app/src/test/kotlin/com/komgareader/app/i18n/MapBackedStringsTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.komgareader.app.i18n

import kotlin.test.Test
import kotlin.test.assertEquals

class MapBackedStringsTest {
    @Test fun overrideWins() {
        val s = MapBackedStrings(mapOf("libraryTitle" to "Biblioteca"), StringsEn)
        assertEquals("Biblioteca", s.libraryTitle)
    }

    @Test fun missingKeyFallsBackToEnglish() {
        val s = MapBackedStrings(mapOf("libraryTitle" to "Biblioteca"), StringsEn)
        assertEquals(StringsEn.appName, s.appName)   // nicht überschrieben → EN
    }

    @Test fun functionTemplateInterpolates() {
        val s = MapBackedStrings(mapOf("downloadingChapters" to "Cargando {count} capítulos…"), StringsEn)
        assertEquals("Cargando 3 capítulos…", s.downloadingChapters(3))
    }

    @Test fun functionFallsBackWhenAbsent() {
        val s = MapBackedStrings(emptyMap(), StringsEn)
        assertEquals(StringsEn.downloadingChapters(5), s.downloadingChapters(5))
    }
}
```

- [ ] **Step 2: Run, expect FAIL**

Run: `./gradlew :app:testDebugUnitTest --tests "com.komgareader.app.i18n.MapBackedStringsTest"`

- [ ] **Step 3: Generate `MapBackedStrings.kt`**

Read `app/src/main/kotlin/com/komgareader/app/i18n/Strings.kt` and produce ONE override for EVERY interface member.

- For every `val <name>: String` →
  ```kotlin
  override val <name>: String get() = overrides["<name>"] ?: fallback.<name>
  ```
- For the parameterized functions (exactly these four; verify against Strings.kt and adjust if more exist):
  ```kotlin
  override fun downloadingChapters(count: Int): String =
      overrides["downloadingChapters"]?.replace("{count}", count.toString()) ?: fallback.downloadingChapters(count)
  override fun downloadFailed(message: String): String =
      overrides["downloadFailed"]?.replace("{message}", message) ?: fallback.downloadFailed(message)
  override fun searchInCollection(name: String): String =
      overrides["searchInCollection"]?.replace("{name}", name) ?: fallback.searchInCollection(name)
  override fun sourceLabel(id: Long): String =
      overrides["sourceLabel"]?.replace("{id}", id.toString()) ?: fallback.sourceLabel(id)
  ```

Class skeleton:
```kotlin
package com.komgareader.app.i18n

/**
 * [Strings] aus einer Runtime-Override-Map (Sprach-Plugin, Kategorie LANGUAGE). Jede Property/Funktion
 * liest zuerst die [overrides] (Key = Interface-Member-Name), fällt sonst auf [fallback] (Built-in,
 * i.d.R. [StringsEn]) zurück — ein partielles Plugin bleibt nutzbar. Funktionen interpolieren die
 * Platzhalter (`{count}`/`{message}`/`{name}`/`{id}`) im Override-Template.
 *
 * GENERIERT aus Strings.kt: jede Member-Override hier ist Pflicht (Interface erzwingt Vollständigkeit
 * → kein stilles Driften). Neue Strings im Interface erfordern hier eine neue Zeile (Compile-Fehler sonst).
 */
class MapBackedStrings(
    private val overrides: Map<String, String>,
    private val fallback: Strings,
) : Strings {
    // ... 261 val-Overrides + 4 fun-Overrides (generiert)
}
```

- [ ] **Step 4: Run, expect PASS**

Run: `./gradlew :app:testDebugUnitTest --tests "com.komgareader.app.i18n.MapBackedStringsTest"`
Expected: PASS. If `:app:compileDebugKotlin` complains about a missing/extra override, add/fix exactly that member until it compiles (the compiler is the completeness check).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/komgareader/app/i18n/MapBackedStrings.kt app/src/test/kotlin/com/komgareader/app/i18n/MapBackedStringsTest.kt
git commit -m "feat(i18n): MapBackedStrings runtime override with English fallback"
```

---

### Task 5: PluginCatalog discovery für LANGUAGE + READER_PRESET

**Files:** Modify `app/src/main/kotlin/com/komgareader/app/data/PluginCatalog.kt`.

- [ ] **Step 1: Add StateFlows** (neben `_presetPlugins`, ~Zeile 71):

```kotlin
    private val _languagePlugins = MutableStateFlow<List<LanguageSpec>>(emptyList())
    val languagePlugins: StateFlow<List<LanguageSpec>> = _languagePlugins.asStateFlow()

    private val _readerPresetPlugins = MutableStateFlow<List<ReaderPreset>>(emptyList())
    val readerPresetPlugins: StateFlow<List<ReaderPreset>> = _readerPresetPlugins.asStateFlow()
```

Imports oben ergänzen:
```kotlin
import com.komgareader.data.plugin.LanguageSpec
import com.komgareader.data.plugin.parseLanguageSpec
import com.komgareader.data.plugin.parseReaderPresetSpecs
import com.komgareader.domain.model.ReaderPreset
import com.komgareader.plugin.PluginCategory
```

- [ ] **Step 2: Discover them in `scanLocal()`** (im `scanLocal`-Block, nach der `presets`-Ermittlung, vor `_sources.value = …`):

```kotlin
        val languages = withContext(Dispatchers.IO) {
            runCatching {
                pluginHost.discoverDataPlugins(PluginCategory.LANGUAGE)
                    .mapNotNull { parseLanguageSpec(it.assetJson, it.abiVersion) }
            }.onFailure { Log.w("PluginCatalog", "discoverLanguagePlugins failed", it) }
                .getOrDefault(emptyList())
        }
        val readerPresets = withContext(Dispatchers.IO) {
            runCatching {
                pluginHost.discoverDataPlugins(PluginCategory.READER_PRESET)
                    .flatMap { parseReaderPresetSpecs(it.assetJson, it.abiVersion).orEmpty() }
            }.onFailure { Log.w("PluginCatalog", "discoverReaderPresetPlugins failed", it) }
                .getOrDefault(emptyList())
        }
```
und nach `_presetPlugins.value = presets`:
```kotlin
        _languagePlugins.value = languages
        _readerPresetPlugins.value = readerPresets
```

- [ ] **Step 3: Verify compile**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/komgareader/app/data/PluginCatalog.kt
git commit -m "feat(plugin-catalog): discover LANGUAGE + READER_PRESET plugins"
```

---

### Task 6: Feature C — Sprach-Auswahl verdrahten

**Files:**
- Create: `app/src/main/kotlin/com/komgareader/app/i18n/LanguageResolver.kt`
- Modify: `app/src/main/kotlin/com/komgareader/app/ui/settings/SettingsViewModel.kt`
- Modify: `app/src/main/kotlin/com/komgareader/app/MainActivity.kt`
- Modify: `app/src/main/kotlin/com/komgareader/app/ui/settings/SettingsContent.kt` (`LanguageSettingsContent`)
- Test: `app/src/test/kotlin/com/komgareader/app/i18n/LanguageResolverTest.kt`

- [ ] **Step 1: Write the failing resolver test**

```kotlin
package com.komgareader.app.i18n

import com.komgareader.data.plugin.LanguageSpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class LanguageResolverTest {
    @Test fun builtinDe() { assertSame(StringsDe, resolveStrings("de", emptyList())) }
    @Test fun builtinEn() { assertSame(StringsEn, resolveStrings("en", emptyList())) }

    @Test fun pluginLanguageBuildsMapBacked() {
        val es = LanguageSpec("es", "Español", 2, mapOf("libraryTitle" to "Biblioteca"))
        val s = resolveStrings("es", listOf(es))
        assertEquals("Biblioteca", s.libraryTitle)
        assertEquals(StringsEn.appName, s.appName)   // Fallback EN
    }

    @Test fun unknownCodeFallsBackToEnglish() {
        assertSame(StringsEn, resolveStrings("zz", emptyList()))
    }
}
```

- [ ] **Step 2: Run, expect FAIL**, then **create `LanguageResolver.kt`**

```kotlin
package com.komgareader.app.i18n

import com.komgareader.data.plugin.LanguageSpec

/**
 * Löst den gespeicherten Sprach-Code in die aktive [Strings] auf. Built-in `de`/`en` zuerst; sonst ein
 * installiertes Sprach-Plugin mit diesem Code → [MapBackedStrings] mit EN-Fallback; sonst [StringsEn]
 * (sicherer Default, nie null). Spiegelt das StubSource-/Default-Prinzip der data-only Nähte.
 */
fun resolveStrings(code: String, installed: List<LanguageSpec>): Strings = when (code) {
    "de" -> StringsDe
    "en" -> StringsEn
    else -> installed.firstOrNull { it.code == code }
        ?.let { MapBackedStrings(it.strings, StringsEn) }
        ?: StringsEn
}
```

- [ ] **Step 3: Run, expect PASS**

Run: `./gradlew :app:testDebugUnitTest --tests "com.komgareader.app.i18n.LanguageResolverTest"`

- [ ] **Step 4: Expose languages + flow in `SettingsViewModel.kt`**

Konstruktor hat bereits Zugriff auf `PluginCatalog` (injiziert? — falls nicht, `catalog: PluginCatalog` zum Konstruktor hinzufügen; es ist `@Singleton`). Ergänze:
```kotlin
    val availableLanguages = catalog.languagePlugins
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
```
(Import `com.komgareader.data.plugin.LanguageSpec` falls für Typannotation nötig.)

- [ ] **Step 5: Build active Strings in `MainActivity.kt`** (Block bei Zeile 100–121 ändern)

Ersetze `val language = if (languageStr == "en") Language.EN else Language.DE` und das `LocalStrings provides stringsFor(language)` durch eine Auflösung über den Resolver:
```kotlin
    val installedLanguages by settingsViewModel.availableLanguages.collectAsState()
    val activeStrings = resolveStrings(languageStr, installedLanguages)
```
und im `CompositionLocalProvider`:
```kotlin
        LocalStrings provides activeStrings,
```
Imports: `com.komgareader.app.i18n.resolveStrings`. `Language`/`stringsFor` bleiben für `LanguageSettingsContent` referenziert; falls hier ungenutzt, deren Import entfernen, damit es ohne Warning baut.

- [ ] **Step 6: Dynamic language picker in `LanguageSettingsContent`** (`SettingsContent.kt` ~699–713)

Ersetze die `Language.entries.forEach`-Schleife durch Built-ins **plus** installierte Plugins:
```kotlin
@Composable
fun LanguageSettingsContent(viewModel: SettingsViewModel, query: String) {
    val s = LocalStrings.current
    val languageStr by viewModel.language.collectAsState()
    val installed by viewModel.availableLanguages.collectAsState()
    SettingsGroup(s.settingsLanguage, query) {
        Language.entries.forEach { lang ->
            val label = when (lang) {
                Language.DE -> "Deutsch"
                Language.EN -> "English"
            }
            ChoiceRow(label, selected = lang.code == languageStr, query = query, dense = true) {
                viewModel.setLanguage(lang.code)
            }
        }
        installed.forEach { spec ->
            ChoiceRow(spec.name, selected = spec.code == languageStr, query = query, dense = true) {
                viewModel.setLanguage(spec.code)
            }
        }
    }
}
```

- [ ] **Step 7: Verify compile + tests**

Run: `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest --tests "com.komgareader.app.i18n.*"`
Expected: BUILD SUCCESSFUL, language tests green.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/kotlin/com/komgareader/app/i18n/LanguageResolver.kt app/src/test/kotlin/com/komgareader/app/i18n/LanguageResolverTest.kt app/src/main/kotlin/com/komgareader/app/ui/settings/SettingsViewModel.kt app/src/main/kotlin/com/komgareader/app/MainActivity.kt app/src/main/kotlin/com/komgareader/app/ui/settings/SettingsContent.kt
git commit -m "feat(language): resolve active Strings via installed language plugins + dynamic picker"
```

---

### Task 7: Feature B — Reader-Preset anwenden (VM + UI)

**Files:**
- Modify: `app/src/main/kotlin/com/komgareader/app/ui/settings/SettingsViewModel.kt`
- Modify: `app/src/main/kotlin/com/komgareader/app/ui/settings/SettingsContent.kt` (`GeneralScope`)

- [ ] **Step 1: Expose presets + apply in `SettingsViewModel.kt`**

```kotlin
    val readerPresets = catalog.readerPresetPlugins
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun applyReaderPreset(preset: ReaderPreset) {
        applyReaderPreset(
            preset.overrides,
            ReaderPresetSink(
                setDisplayMode = ::setDisplayMode,
                setDeviceManagedRefresh = ::setDeviceManagedRefresh,
                setWebtoonOverlapPercent = ::setWebtoonOverlap,
                setNovelFontSizeEm = ::setNovelFontSizeEm,
                setNovelLineHeight = ::setNovelLineHeight,
                setNovelMarginPreset = ::setNovelMarginPreset,
                setNovelFontFamily = ::setNovelFontFamily,
                setNovelTextAlign = ::setNovelTextAlign,
                setNovelHyphenationLang = ::setNovelHyphenationLang,
                setNovelFontWeight = ::setNovelFontWeight,
                setGuidedPanelOverlay = ::setGuidedPanelOverlay,
            ),
        )
    }
```
Imports: `com.komgareader.domain.model.ReaderPreset`, `com.komgareader.domain.usecase.ReaderPresetSink`, `com.komgareader.domain.usecase.applyReaderPreset`. **Hinweis:** falls `setDisplayMode` im VM noch fehlt (Explore zeigte `setDeviceManagedRefresh` etc., aber prüfe `setDisplayMode`), füge die fehlenden Wrapper analog der bestehenden Setter hinzu (`fun setDisplayMode(value: String) = viewModelScope.launch { settings.setDisplayMode(value) }.let {}`).

- [ ] **Step 2: Add "Preset anwenden" row to `GeneralScope`** (`SettingsContent.kt`, in `GeneralScope`, vor dessen schließender `}` bei ~Zeile 521)

```kotlin
        val presets by viewModel.readerPresets.collectAsState()
        var confirmPreset by remember { mutableStateOf<ReaderPreset?>(null) }
        if (presets.isEmpty()) {
            InfoRow(s.readerPresetApply, s.readerPresetNone, query = query)
        } else {
            PickerRow(
                label = s.readerPresetApply,
                value = "",
                query = query,
                options = presets.map { PickerOption(it.name, it.name) },
                onSelect = { name -> confirmPreset = presets.firstOrNull { it.name == name } },
            )
        }
        confirmPreset?.let { p ->
            EinkModal(
                title = s.readerPresetConfirmTitle,
                onDismiss = { confirmPreset = null },
                confirmLabel = s.readerPresetApply,
                onConfirm = { viewModel.applyReaderPreset(p); confirmPreset = null },
                dismissLabel = s.cancel,
            ) { Text(s.readerPresetConfirmBody(p.name), style = MaterialTheme.typography.bodyMedium) }
        }
```
Imports: `com.komgareader.domain.model.ReaderPreset`. **Wichtig:** prüfe die echten Namen der vorhandenen Settings-Composables (`PickerRow`/`PickerOption`/`InfoRow`/`EinkModal`) im File und nutze exakt die existierenden Helfer-Signaturen; passe die obigen Aufrufe an die tatsächlichen Parameter an (z.B. heißt die Picker-Zeile evtl. anders). Wenn ein passender „nur-Auswahl"-Helfer fehlt, nutze `ChoiceRow` je Preset analog zum Sprach-Picker statt `PickerRow`.

- [ ] **Step 3: Verify compile**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/komgareader/app/ui/settings/SettingsViewModel.kt app/src/main/kotlin/com/komgareader/app/ui/settings/SettingsContent.kt
git commit -m "feat(reader-preset): apply discovered presets from reader settings (confirm dialog)"
```

---

### Task 8: Feature A — geteilter Plugin-Add-Flow + „Plugin"-Segment

**Files:**
- Create: `app/src/main/kotlin/com/komgareader/app/ui/plugins/AddPluginSourceFlow.kt`
- Modify: `app/src/main/kotlin/com/komgareader/app/ui/settings/SettingsContent.kt` (`AddConnectionModal`)
- Modify ggf.: `app/src/main/kotlin/com/komgareader/app/ui/plugins/PluginsScreen.kt` (Flow wiederverwenden statt Duplikat)

- [ ] **Step 1: Extract the shared add-flow** — `AddPluginSourceFlow.kt`

Ein Composable, das den TOFU→Config→Persist-Ablauf für eine Liste entdeckter Quellen-Plugins kapselt, sodass Plugins-Tab UND AddConnectionModal es nutzen. Es rendert die `PluginTofuModal`+`PluginConfigModal` (bestehend in `PluginSourceFlow.kt`) und ruft bei Abschluss `onAdd(plugin, values)`:
```kotlin
package com.komgareader.app.ui.plugins

import androidx.compose.runtime.*
import com.komgareader.plugin.host.DiscoveredPlugin

/**
 * Geteilter TOFU→Config→Persist-Ablauf für das Hinzufügen einer Quellen-Plugin-Verbindung. Hält den
 * 2-Stufen-Modal-Zustand und ruft [onAdd] mit dem gewählten Plugin + Config-Werten. Wiederverwendet von
 * Plugins-Tab und „Server hinzufügen" (shared-structure-before-variants). [trigger] = aktuell gewähltes
 * Plugin (von außen gesetzt); null = nichts offen.
 */
@Composable
fun AddPluginSourceModals(
    trigger: DiscoveredPlugin?,
    onDismiss: () -> Unit,
    onAdd: (DiscoveredPlugin, Map<String, String>) -> Unit,
) {
    var confirmed by remember(trigger) { mutableStateOf(false) }
    val plugin = trigger ?: return
    if (!confirmed) {
        PluginTofuModal(plugin = plugin, onDismiss = onDismiss, onConfirm = { confirmed = true })
    } else {
        PluginConfigModal(
            plugin = plugin,
            onDismiss = onDismiss,
            onSubmit = { values -> onAdd(plugin, values); onDismiss() },
        )
    }
}
```
Falls `PluginsScreen` heute denselben TOFU→Config-Ablauf inline hat (`tofuFor`-State), **diesen** durch `AddPluginSourceModals` ersetzen (Duplikat entfernen). Verhalten muss gleich bleiben.

- [ ] **Step 2: Wire the "Plugin" segment into `AddConnectionModal`** (`SettingsContent.kt`)

Der `AddConnectionModal` braucht Zugriff auf die entdeckten Quellen-Plugins + den Add-Callback. Reiche sie vom Settings-VM/PluginCatalog herein (die Settings-Connection-UI nutzt bereits ein VM — füge `sourcePlugins: List<DiscoveredPlugin>` und `onAddPluginSource: (DiscoveredPlugin, Map<String,String>) -> Unit` als Parameter hinzu, gespeist aus `catalog.sources` bzw. der geteilten `addPluginSource`-Logik). Erweitere den Selektor:
```kotlin
        SegmentedChoiceRow(
            label = s.serverSectionKind,
            options = listOf(
                SegmentOption(SourceKind.KOMGA.name, "Komga"),
                SegmentOption(SourceKind.OPDS.name, "OPDS"),
                SegmentOption(SourceKind.PLUGIN.name, s.serverKindPlugin),
            ),
            selectedKey = kindInput.name,
            onSelect = { kindInput = SourceKind.valueOf(it) },
        )
```
Wenn `kindInput == SourceKind.PLUGIN`: statt der URL-Felder eine Auswahl der `sourcePlugins` rendern (je Plugin eine `ChoiceRow`, die `pluginTrigger = plugin` setzt); leer → `Text(s.addServerNoSourcePlugins)`. Unten im Modal `AddPluginSourceModals(trigger = pluginTrigger, onDismiss = { pluginTrigger = null }, onAdd = onAddPluginSource)` einhängen. Der reguläre `onSave`-Confirm-Button bleibt nur für KOMGA/OPDS aktiv (`confirmEnabled` entsprechend: bei PLUGIN false, da der Add über den Plugin-Flow läuft).

**Naht A:** kein konkreter Quellen-Typ wandert ins VM — `DiscoveredPlugin`/`addPluginSource` sind die generische Plugin-Grenze (wie im Plugins-Tab), kein `KomgaSource` o.ä.

- [ ] **Step 3: Verify compile + existing plugin tests**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/komgareader/app/ui/plugins/AddPluginSourceFlow.kt app/src/main/kotlin/com/komgareader/app/ui/settings/SettingsContent.kt app/src/main/kotlin/com/komgareader/app/ui/plugins/PluginsScreen.kt
git commit -m "feat(settings): add source plugins via Server-hinzufuegen selector (shared add-flow)"
```

---

### Task 9: Plugins-Tab-Hub zeigt Language + Reader-Preset

**Files:** Modify `app/.../ui/plugins/PluginsViewModel.kt`, `PluginsScreen.kt` (+ ggf. `PluginCatalog`/`PluginKind`).

- [ ] **Step 1: Inspect current `PluginKind` + installed-list assembly**

Run: `grep -rn "enum class PluginKind\|PluginKind\." app/src/main/kotlin/com/komgareader/app/ | head -40`
Verstehe, wie `installed`-Items (`PluginKind.SOURCE`/`PRESET`) gebaut werden und wie die Uninstall/Type-Filter funktionieren.

- [ ] **Step 2: Extend `PluginKind` + installed assembly** to include `LANGUAGE` and `READER_PRESET`

Füge die zwei Enum-Werte hinzu und baue für entdeckte `languagePlugins`/`readerPresetPlugins` je eine installed-Row (Titel = `spec.name` / `preset.name`, `typeLabel` = neue i18n-Labels, Uninstall = `uninstall(packageName)`). **Hinweis:** Language-/ReaderPreset-Specs tragen heute keinen `packageName` (der Parser liefert nur Inhalt). Für die Uninstall-Zeile den `packageName` aus der Discovery durchreichen: erweitere die `PluginCatalog`-Flows auf ein Paar `(packageName, spec)` ODER halte zusätzlich die `DiscoveredDataPlugin`-Liste je Kategorie. Wähle den kleineren Diff: in `PluginCatalog` statt `List<LanguageSpec>` eine `List<InstalledData<LanguageSpec>>` mit `packageName` + `displayName` + `spec` führen (analog für ReaderPreset). Passe Task-6/7-Consumer (die nur `spec.name`/`spec.strings`/`overrides` brauchen) entsprechend an (`.map { it.spec }` an der Verbrauchsstelle).

Neue i18n-Labels in Strings (Interface+DE+EN): `pluginTabLanguageLabel` („Sprache"/„Language"), `pluginTabReaderPresetLabel` („Reader-Preset"). **Danach `MapBackedStrings` um diese Member ergänzen** (Compile-Fehler weist darauf hin).

- [ ] **Step 3: Render the new kinds in `PluginsScreen`** analog der bestehenden `PluginKind.SOURCE`/`PRESET`-Zweige (nur Anzeige + Uninstall; kein Configure für data-only — oder Configure zeigt eine Info). Type-Filter-Chip um die zwei Kategorien erweitern.

- [ ] **Step 4: Verify compile**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/komgareader/app/ app/src/main/kotlin/com/komgareader/app/i18n/Strings.kt app/src/main/kotlin/com/komgareader/app/i18n/MapBackedStrings.kt
git commit -m "feat(plugins): hub lists installed language + reader-preset plugins"
```

---

### Task 10: Deliverable-Plugin-Repos unter `/plugins/`

Vier data-only Plugins aus `_template-data/`: es/fr/it (LANGUAGE) + 1 Reader-Preset-Sample. Jedes ein eigenes Verzeichnis unter `plugins/` (im Haupt-Repo gitignored — wird **nicht** committet, nur lokal gebaut/installiert). Diese Task erzeugt die Quellbäume; sie sind die echten APK-Repos.

- [ ] **Step 1: Build the reader-preset sample** `plugins/komga-reader-preset-eink/`

Aus `plugins/_template-data/` kopieren. Manifest `DATA_CATEGORY=READER_PRESET`, `DATA_ASSET=data.json`, `ABI_VERSION=2`, eigenes `applicationId` (z.B. `com.komgareader.preset.reader.eink`), Label „E-Ink Reader-Presets". `assets/data.json`:
```json
[
  {"abiVersion":2,"name":"E-Ink Roman komfortabel",
   "settings":{"novelFontSizeEm":1.2,"novelLineHeight":1.4,"novelMarginPreset":"WIDE","novelTextAlign":"LEFT","deviceManagedRefresh":true}},
  {"abiVersion":2,"name":"Webtoon dicht",
   "settings":{"webtoonOverlapPercent":10}}
]
```

- [ ] **Step 2: Generate the three language payloads**

Für jede Sprache (es/fr/it) ein Verzeichnis `plugins/komga-lang-<code>/` aus `_template-data/`. Manifest `DATA_CATEGORY=LANGUAGE`, `DATA_ASSET=data.json`, `ABI_VERSION=2`, `applicationId` `com.komgareader.lang.<code>`, Label = endonym (Español/Français/Italiano). `assets/data.json` = `{"abiVersion":2,"code":"<code>","name":"<endonym>","strings":{ … }}`.

**Den `strings`-Inhalt so erzeugen:** lies ALLE `override val …`-Strings aus `StringsEn` in `app/.../i18n/Strings.kt` und übersetze jeden **Wert** ins Ziel-Sprachidiom. Die Keys sind die Property-Namen (z.B. `"libraryTitle"`). **Platzhalter erhalten:** wo EN `$count`/`$message`/`$name`/`$id` nutzt (die 4 Funktionen), das Template mit `{count}`/`{message}`/`{name}`/`{id}` schreiben (z.B. `"downloadingChapters":"Cargando {count} capítulos…"`). Vollständig (alle Keys), maschinen-Übersetzung ist ok (roh), echte Akzente/Umlaute.

> Diese drei Übersetzungs-Payloads sind groß — sie werden über drei **parallele** Übersetzungs-Subagenten erzeugt (einer je Sprache), die `StringsEn` lesen und das JSON schreiben. Siehe Ausführungs-Hinweis unten.

- [ ] **Step 3: Update `plugins/README.md`** um die vier konkreten Repos in einer Tabelle zu nennen (Name, Kategorie, applicationId).

- [ ] **Step 4: Confirm they are gitignored** (nur Inhalt lokal, nicht committet außer README-Update)

Run: `git status --short plugins`
Expected: nur `plugins/README.md` (geändert) erscheint; die vier neuen Plugin-Verzeichnisse sind durch `/plugins/*/` ignoriert.

- [ ] **Step 5: Commit (nur README)**

```bash
git add plugins/README.md
git commit -m "docs(plugins): list es/fr/it language + e-ink reader-preset deliverable repos"
```

---

### Task 11: E2E + docs-match-code

**Files:**
- Create: `app/src/androidTest/kotlin/com/komgareader/app/ci/PluginLanguageTest.kt`
- Create: `app/src/androidTest/kotlin/com/komgareader/app/ci/PluginReaderPresetTest.kt`
- Modify: `.claude/rules/architecture-seams.md`

- [ ] **Step 1: Language E2E** (Muster wie `PluginColorPresetTest`, `assumeTrue`-gegated)

```kotlin
package com.komgareader.app.ci

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.komgareader.data.plugin.parseLanguageSpec
import com.komgareader.plugin.PluginCategory
import com.komgareader.plugin.host.PluginHost
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PluginLanguageTest {
    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private val host = PluginHost(ctx)
    private val pkg = "com.komgareader.lang.es"
    private fun installed(p: String) = runCatching { ctx.packageManager.getPackageInfo(p, 0); true }.getOrDefault(false)

    @Test fun language_plugin_entdeckt_und_geparst() {
        assumeTrue("Sprach-APK '$pkg' nicht installiert — übersprungen", installed(pkg))
        val data = host.discoverDataPlugins(PluginCategory.LANGUAGE)
        assertTrue("Kein LANGUAGE-Plugin entdeckt", data.isNotEmpty())
        val spec = parseLanguageSpec(data.first().assetJson, data.first().abiVersion)
        assertTrue("Spec muss Code+Strings tragen", spec != null && spec.code.isNotBlank() && spec.strings.isNotEmpty())
    }
}
```

- [ ] **Step 2: Reader-Preset E2E** (analog, `pkg = "com.komgareader.preset.reader.eink"`, `PluginCategory.READER_PRESET`, `parseReaderPresetSpecs`, assert nicht-leere Preset-Liste mit nicht-leerem `name`).

- [ ] **Step 3: Compile the androidTest** (Device-Lauf deferred wie Color-Preset; bekannter pre-existing Hilt-androidTest-Assemble-Bug ist unabhängig)

Run: `./gradlew :app:compileDebugAndroidTestKotlin` (falls dieser Task existiert) bzw. `:app:assembleDebugAndroidTest` — wenn letzteres am bekannten pre-existing Hilt-`RepoStore`/`PluginRepoClient`-MissingBinding scheitert, ist das **nicht** von dieser Arbeit; im Task-Review vermerken. Compile der neuen Testklassen darf nicht an unseren Typen scheitern.

- [ ] **Step 4: docs-match-code** — ergänze in `.claude/rules/architecture-seams.md` (Plugin-Loader-Ist) einen Satz:

```markdown
- **Reader-Preset- + Sprach-Plugins (Ist, 2026-06-12, Spec 2):** Zwei neue data-only Kategorien über
  `discoverDataPlugins`: `READER_PRESET` (benannte Teil-Snapshots der Reader-Settings → `parseReaderPresetSpecs`
  → `applyReaderPreset` mutiert nur gesetzte Felder, **kein** Room-Table) und `LANGUAGE` (Runtime-i18n-Override
  `MapBackedStrings` mit EN-Fallback, `language`-Setting hält beliebigen Code, `resolveStrings` in MainActivity).
  Quellen-Plugins sind jetzt auch über den „Server hinzufügen"-Selektor („Plugin"-Segment, geteilter
  `AddPluginSourceModals`-Flow) hinzufügbar, nicht nur im Plugins-Tab. Deliverables: `/plugins/komga-lang-{es,fr,it}`,
  `/plugins/komga-reader-preset-eink`.
```

- [ ] **Step 5: Commit**

```bash
git add app/src/androidTest/kotlin/com/komgareader/app/ci/PluginLanguageTest.kt app/src/androidTest/kotlin/com/komgareader/app/ci/PluginReaderPresetTest.kt .claude/rules/architecture-seams.md
git commit -m "test(plugins): language + reader-preset E2E (assumeTrue) + docs-match-code"
```

---

## Ausführungs-Hinweise

- **Task 10 Step 2 (Übersetzungen):** drei parallele Übersetzungs-Subagenten (es/fr/it), jeder liest `StringsEn` aus `Strings.kt` und schreibt `plugins/komga-lang-<code>/src/main/assets/data.json` mit allen Keys übersetzt, Platzhalter `{…}` erhalten, echte Akzente.
- **`MapBackedStrings` (Task 4) + neue Strings (Task 1, 9):** Reihenfolge wichtig — neue Interface-Strings VOR der `MapBackedStrings`-Generierung; spätere String-Adds (Task 9) ziehen eine `MapBackedStrings`-Zeile nach (Compiler erzwingt es). Wer Task 9 macht, baut `MapBackedStrings` mit.
- **UI-Helfer-Namen (Task 7, 8):** der Plan nennt plausible Composable-Namen (`PickerRow`, `EinkModal`, `ChoiceRow`); der Implementer prüft die echten Signaturen im File und passt die Aufrufe an, statt zu erfinden.

## Self-Review-Ergebnis

- **Spec-Coverage:** Feature A → Task 8 (+ Strings Task 1); Feature B → Task 2,7 (+ deliverable Task 10); Feature C → Task 3,4,6 (+ deliverables Task 10); Querschnitt PluginCatalog → Task 5; Hub → Task 9; Tests/docs → Task 11. Alles abgedeckt.
- **Placeholder-Scan:** Code für die puren/tricky Teile (Parser, MapBackedStrings-Pattern, Resolver, Apply) vollständig; bulk-mechanische Teile (261 Overrides, 3 Übersetzungen) mit klarer Quelle (`Strings.kt`) + Compiler-Netz statt erfundenem Inhalt — bewusst, da 261×4 Werte nicht sinnvoll inline-bar.
- **Typ-Konsistenz:** `parseReaderPresetSpecs(json, abi): List<ReaderPreset>?`, `ReaderPresetOverrides`(nullable Felder), `applyReaderPreset(overrides, sink)` + `ReaderPresetSink` konsistent Task 2↔7. `parseLanguageSpec(json, abi): LanguageSpec?`, `LanguageSpec(code,name,abiVersion,strings)`, `resolveStrings(code, installed): Strings`, `MapBackedStrings(overrides, fallback)` konsistent Task 3↔4↔6. `discoverDataPlugins(category)` aus dem Fundament konsistent genutzt (Task 5).
