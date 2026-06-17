# Panel-Confidence-Config + Misdetection-Capture Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Eine einstellbare Mindest-Confidence für die YOLO-Panel-Erkennung (Zahnrad am data-only Plugin), Debug-Overlay mit Panel-Nummer + Score, und ein 1-Tap-Capture falsch erkannter Seiten in einen SAF-Ordner fürs PC-Labeling.

**Architecture:** Score wird in der ComicCutter-Lib durch `PanelRect`/`NormRect` durchgereicht (Release 0.4.0). Der Host bekommt eine generische Config-Mechanik für data-only Plugins (Schema via `config.json`-Asset + Manifest-Key `DATA_CONFIG`, Werte als Room-KV `plugincfg:<pkg>:<key>`, Zahnrad an `DataPluginRow`, Slider im wiederverwendeten `PluginConfigForm`). `PanelSourceProvider` liest die Confidence statt der hartkodierten 0.25. Capture nutzt SAF-Tree-URI + `DocumentFile`.

**Tech Stack:** Kotlin, Jetpack Compose, Hilt, Room, ComicCutter (JitPack KMP-Lib), onnxruntime-android, JUnit 5 + kotlin.test + MockK.

## Global Constraints

- ComicCutter-Änderungen sind **additiv** (`score: Float = 1.0f` Default) — bestehende Aufrufer dürfen nicht brechen.
- ComicCutter-Release **0.4.0**; komga-reader bumpt beide Artefakte (`comic-cutter-jvm`, `comic-cutter-onnx-jvm`) auf `0.4.0`.
- Confidence-Slider: **Bereich 0.1–1.0, Default 0.25, step 0.05**. NMS/IoU bleiben hartkodiert (`0.7f, 0.0f, null`).
- Persistenz: Room-KV über bestehende `SettingsDao`, Key-Schema `plugincfg:<packageName>:<fieldKey>`. **Kein neuer Table.**
- SAF: **Tree**-URI über `ActivityResultContracts.OpenDocumentTree()` + `takePersistableUriPermission(uri, READ|WRITE)`. **Kein** roher Dateipfad. Schreiben via `DocumentFile`/`ContentResolver`.
- Sidecar-JSON = mllabeltool-Prediction-Format, **pixel-space**: `{ "items": [ { "box": [x,y,w,h], "label": "panel", "score": <float> } ] }`.
- ABI: `PluginAbi.VERSION` additiv von 3 → **4** erhöhen; `MIN_SUPPORTED` bleibt 1.
- Pure Units in `src/test/kotlin` (JUnit 5 + `kotlin.test`), Stil wie `ColorPresetImporterTest`. Functional Core / Imperative Shell: Parser + JSON-Bau rein + getestet, SAF-I/O in dünner App-Shell.
- Reader-Package `com.komgareader.model.panel.yolo` ist der PANEL_MODEL-Plugin-Package-Name (Konstante in `PanelSourceProvider`).
- Commits pro Repo (ComicCutter / komga-reader / KomgaReaderPlugins haben getrennte Git-Roots). Commit-Footer: `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`.

---

## Phase 1 — ComicCutter (Repo: `~/Documents/Projekte/ComicCutter`, Release 0.4.0)

### Task 1: Score an `PanelRect` + `MlFilter` reicht ihn durch

**Files:**
- Modify: `src/commonMain/kotlin/com/panela/comiccutter/Panel.kt`
- Modify: `src/commonMain/kotlin/com/panela/comiccutter/MlFilter.kt` (Methode `apply`, letzte `.map`-Zeile)
- Test: `src/commonTest/kotlin/com/panela/comiccutter/MlFilterTest.kt`

**Interfaces:**
- Produces: `PanelRect(x, y, width, height, score: Float = 1.0f)`; `MlFilter.apply(...)` liefert `PanelRect`e mit dem Score der überlebenden `RawDetection`.

- [ ] **Step 1: Failing test** — in `MlFilterTest.kt` ergänzen:

```kotlin
@Test
fun apply_carries_detection_score_into_panel() {
    val filter = MlFilter(minScore = 0.1f, nmsIoU = 0.5f, minAreaFraction = 0.0f, keepClass = null)
    val dets = listOf(RawDetection(x = 0, y = 0, width = 50, height = 50, score = 0.83f, cls = 0))
    val panels = filter.apply(dets, pageW = 100, pageH = 100)
    assertEquals(1, panels.size)
    assertEquals(0.83f, panels[0].score, 0.0001f)
}
```

- [ ] **Step 2: Run, verify fail**

Run: `./gradlew :jvmTest --tests "com.panela.comiccutter.MlFilterTest"`
Expected: FAIL — `PanelRect` hat (noch) kein `score` / Compile-Fehler `no value passed for parameter score`.

- [ ] **Step 3: Implement** — `Panel.kt`:

```kotlin
/** Rectangular bounds of a detected panel within a page. [score] is the model confidence (1.0 for the geometric source). */
data class PanelRect(val x: Int, val y: Int, val width: Int, val height: Int, val score: Float = 1.0f) {
    val centerX: Int get() = x + width / 2
    val centerY: Int get() = y + height / 2
}
```

`MlFilter.kt` — letzte Zeile von `apply` ändern:

```kotlin
        return nms(kept).map { PanelRect(it.x, it.y, it.width, it.height, it.score) }
```

- [ ] **Step 4: Run, verify pass**

Run: `./gradlew :jvmTest --tests "com.panela.comiccutter.MlFilterTest"`
Expected: PASS (alle bestehenden MlFilter-Tests bleiben grün).

- [ ] **Step 5: Commit**

```bash
git add src/commonMain/kotlin/com/panela/comiccutter/Panel.kt src/commonMain/kotlin/com/panela/comiccutter/MlFilter.kt src/commonTest/kotlin/com/panela/comiccutter/MlFilterTest.kt
git commit -m "feat(panel): carry model confidence into PanelRect.score" -m "Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

### Task 2: Score an `NormRect` + `PanelGeometry.normalize` reicht ihn durch

**Files:**
- Modify: `src/commonMain/kotlin/com/panela/comiccutter/PanelGeometry.kt` (`NormRect` + `normalize`)
- Test: `src/commonTest/kotlin/com/panela/comiccutter/PanelGeometryTest.kt`

**Interfaces:**
- Produces: `NormRect(left, top, width, height, score: Float = 1.0f)`; `PanelGeometry.normalize(panel, w, h)` setzt `score = panel.score`.

- [ ] **Step 1: Failing test** — in `PanelGeometryTest.kt`:

```kotlin
@Test
fun normalize_carries_score() {
    val panel = PanelRect(x = 10, y = 20, width = 30, height = 40, score = 0.66f)
    val norm = PanelGeometry.normalize(panel, pageW = 100, pageH = 200)
    assertEquals(0.66f, norm.score, 0.0001f)
}
```

- [ ] **Step 2: Run, verify fail**

Run: `./gradlew :jvmTest --tests "com.panela.comiccutter.PanelGeometryTest"`
Expected: FAIL — `NormRect.score` existiert nicht.

- [ ] **Step 3: Implement** — `PanelGeometry.kt`:

```kotlin
data class NormRect(val left: Float, val top: Float, val width: Float, val height: Float, val score: Float = 1.0f) {
    val centerX: Float get() = left + width / 2f
    val centerY: Float get() = top + height / 2f
    fun contains(x: Float, y: Float): Boolean =
        x >= left && x < left + width && y >= top && y < top + height
}
```

`normalize` ergänzen:

```kotlin
    fun normalize(panel: PanelRect, pageW: Int, pageH: Int): NormRect =
        NormRect(
            left = panel.x.toFloat() / pageW,
            top = panel.y.toFloat() / pageH,
            width = panel.width.toFloat() / pageW,
            height = panel.height.toFloat() / pageH,
            score = panel.score,
        )
```

- [ ] **Step 4: Run, verify pass**

Run: `./gradlew :jvmTest --tests "com.panela.comiccutter.PanelGeometryTest"`
Expected: PASS (`FULL_PAGE`-Konstante etc. nutzen den Default 1.0f, kein Bruch).

- [ ] **Step 5: Commit**

```bash
git add src/commonMain/kotlin/com/panela/comiccutter/PanelGeometry.kt src/commonTest/kotlin/com/panela/comiccutter/PanelGeometryTest.kt
git commit -m "feat(panel): carry score into NormRect via normalize" -m "Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

### Task 3: Release 0.4.0 (Tag → JitPack)

**Files:**
- Modify: `build.gradle.kts` (root) — `version` Property auf `0.4.0` (suche `version = "0.3.1"` o.ä.; falls Version nur per Tag kommt, diesen Step überspringen und nur taggen).

- [ ] **Step 1: Volltest grün**

Run: `./gradlew jvmTest`
Expected: PASS (alle Tests).

- [ ] **Step 2: Version bumpen (falls im Build deklariert)**

Suche: `grep -rn '0\.3\.1' build.gradle.kts` — wenn gefunden, auf `0.4.0` setzen.

- [ ] **Step 3: Commit + Tag + Push**

```bash
git add -A && git commit -m "chore(release): 0.4.0 — PanelRect/NormRect score" -m "Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
git tag 0.4.0
git push origin HEAD --tags
```

- [ ] **Step 4: JitPack-Build antriggern**

Run: `curl -s "https://jitpack.io/com/github/Gabriel-Graf/ComicCutter/comic-cutter-jvm/0.4.0/comic-cutter-jvm-0.4.0.pom" -o /dev/null -w "%{http_code}\n"`
Expected: am Anfang evtl. `404`/`200` während JitPack baut; wiederholen bis `200` (Erstbuild dauert 1–3 min). Erst dann Phase 2 Task 4.

---

## Phase 2 — komga-reader (Branch `feat/panel-confidence-config`)

### Task 4: ComicCutter-Dependency auf 0.4.0 bumpen

**Files:**
- Modify: `app/build.gradle.kts:105-106`

**Interfaces:**
- Consumes: ComicCutter 0.4.0 von JitPack (Task 3).

- [ ] **Step 1: Version ändern**

```kotlin
implementation("com.github.Gabriel-Graf.ComicCutter:comic-cutter-jvm:0.4.0")
implementation("com.github.Gabriel-Graf.ComicCutter:comic-cutter-onnx-jvm:0.4.0") {
    exclude(group = "com.microsoft.onnxruntime", module = "onnxruntime")
}
```

- [ ] **Step 2: Resolve verifizieren**

Run: `./gradlew :app:dependencies --configuration releaseRuntimeClasspath 2>/dev/null | grep ComicCutter`
Expected: zeigt `...comic-cutter-jvm:0.4.0` und `...comic-cutter-onnx-jvm:0.4.0`.

- [ ] **Step 3: Commit**

```bash
git add app/build.gradle.kts
git commit -m "chore(deps): bump ComicCutter to 0.4.0 (panel score)" -m "Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

### Task 5: plugin-api — `FieldType.NUMBER` + `ConfigField` min/max/step + ABI-Bump

**Files:**
- Modify: `plugin-api/src/main/kotlin/com/komgareader/plugin/ConfigSchema.kt`
- Modify: `plugin-api/src/main/kotlin/com/komgareader/plugin/ColorPresetSpec.kt` (`PluginAbi.VERSION`)
- Test: `plugin-api/src/test/kotlin/com/komgareader/plugin/ConfigFieldTest.kt` (neu; falls `plugin-api` kein `src/test` hat, im `:data`-Testset ablegen: `data/src/test/kotlin/com/komgareader/plugin/ConfigFieldTest.kt`)

**Interfaces:**
- Produces: `enum FieldType { TEXT, SECRET, URL, BOOL, NUMBER }`; `ConfigField(key, label, type, required=true, default="", min: Double? = null, max: Double? = null, step: Double? = null)`.

- [ ] **Step 1: Failing test**

```kotlin
package com.komgareader.plugin

import kotlin.test.Test
import kotlin.test.assertEquals

class ConfigFieldTest {
    @Test
    fun number_field_carries_range() {
        val f = ConfigField("min_confidence", "Mindest-Confidence", FieldType.NUMBER, required = false, default = "0.25", min = 0.1, max = 1.0, step = 0.05)
        assertEquals(FieldType.NUMBER, f.type)
        assertEquals(0.1, f.min)
        assertEquals(1.0, f.max)
        assertEquals(0.05, f.step)
    }
}
```

- [ ] **Step 2: Run, verify fail**

Run: `./gradlew :plugin-api:test --tests "com.komgareader.plugin.ConfigFieldTest"` (oder `:data:test`)
Expected: FAIL — `FieldType.NUMBER` und `min/max/step` existieren nicht.

- [ ] **Step 3: Implement** — `ConfigSchema.kt`:

```kotlin
data class ConfigField(
    /** Speicher-Schlüssel in ServerConfig.extras bzw. plugincfg-KV. */
    val key: String,
    /** Vom Plugin geliefert (bereits lokalisiertes) Label. */
    val label: String,
    val type: FieldType,
    val required: Boolean = true,
    val default: String = "",
    /** Nur NUMBER: Slider-Grenzen/Schrittweite. null für andere Typen. */
    val min: Double? = null,
    val max: Double? = null,
    val step: Double? = null,
)

enum class FieldType { TEXT, SECRET, URL, BOOL, NUMBER }
```

`ColorPresetSpec.kt` — ABI bumpen:

```kotlin
object PluginAbi {
    const val VERSION = 4        // war 3 — additiv: NUMBER-FieldType + data-only Plugin-Config (DATA_CONFIG)
    const val MIN_SUPPORTED = 1
}
```

- [ ] **Step 4: Run, verify pass**

Run: `./gradlew :plugin-api:test --tests "com.komgareader.plugin.ConfigFieldTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add plugin-api/src/main/kotlin/com/komgareader/plugin/ConfigSchema.kt plugin-api/src/main/kotlin/com/komgareader/plugin/ColorPresetSpec.kt plugin-api/src/test
git commit -m "feat(plugin-api): NUMBER field type + ConfigField range, ABI 4" -m "Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

### Task 6: plugin-host — `DATA_CONFIG`-Key + config-Asset-Lesen

**Files:**
- Modify: `plugin-host/src/main/kotlin/com/komgareader/plugin/host/PluginManifestKeys.kt`
- Modify: `plugin-host/src/main/kotlin/com/komgareader/plugin/host/DiscoveredDataPlugin.kt` (`DataPluginInfo` um `configAssetName: String?`)
- Modify: `plugin-host/src/main/kotlin/com/komgareader/plugin/host/PluginHost.kt` (Manifest-Scan liest `DATA_CONFIG`; neue Methode `dataPluginConfigJson`)

**Interfaces:**
- Consumes: `DataPluginInfo` (bestehend).
- Produces: `PluginManifestKeys.DATA_CONFIG`; `DataPluginInfo(..., configAssetName: String?)`; `PluginHost.dataPluginConfigJson(packageName: String): String?` (liest das `DATA_CONFIG`-Asset resource-only, null wenn keins deklariert/lesbar).

- [ ] **Step 1: Implement Manifest-Key** — `PluginManifestKeys.kt` ergänzen:

```kotlin
    /** Asset-Dateiname (relativ zu `assets/`) eines optionalen Config-Schemas (ConfigSchema-JSON) für data-only Plugins. */
    const val DATA_CONFIG = "com.komgareader.plugin.DATA_CONFIG"
```

- [ ] **Step 2: `DataPluginInfo` erweitern** — `DiscoveredDataPlugin.kt`:

```kotlin
data class DataPluginInfo(
    val packageName: String,
    val category: PluginCategory,
    val abiVersion: Int,
    val assetName: String,
    val displayName: String,
    /** Asset-Name des optionalen Config-Schemas (DATA_CONFIG), null wenn das Plugin keins deklariert. */
    val configAssetName: String? = null,
)
```

- [ ] **Step 3: Scan + Reader in `PluginHost.kt`**

Im `scanDataPluginManifests` (bzw. `resolveDataPluginManifest`) das `DATA_CONFIG`-Meta mitlesen und in `DataPluginInfo.configAssetName` füllen (in `discoverDataPluginInfos`-Mapping ergänzen). Neue Methode hinzufügen:

```kotlin
/**
 * Liest das optionale Config-Schema-Asset (DATA_CONFIG) des installierten data-only Plugins
 * [packageName] als JSON-String — resource-only via `createPackageContext(pkg, 0)`, KEIN Code.
 * null, wenn das Plugin kein DATA_CONFIG deklariert oder das Asset nicht lesbar ist.
 */
fun dataPluginConfigJson(packageName: String): String? {
    val info = discoverDataPluginInfos(PluginCategory.PANEL_MODEL)
        .firstOrNull { it.packageName == packageName } ?: return null
    val asset = info.configAssetName ?: return null
    return runCatching {
        context.createPackageContext(packageName, 0).assets.open(asset).bufferedReader().use { it.readText() }
    }.getOrNull()
}
```

> Hinweis: `discoverDataPluginInfos(category)` filtert auf eine Kategorie. Für eine generische Variante über alle Kategorien iterieren — hier reicht PANEL_MODEL (einziges Plugin mit Config). Falls später mehr: Signatur `dataPluginConfigJson(pkg, category)` erweitern.

- [ ] **Step 4: Build verifizieren**

Run: `./gradlew :plugin-host:compileReleaseKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add plugin-host/src/main/kotlin/com/komgareader/plugin/host/
git commit -m "feat(plugin-host): DATA_CONFIG asset key + dataPluginConfigJson reader" -m "Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

### Task 7: :data — `parseConfigSchema` (reiner Parser)

**Files:**
- Create: `data/src/main/kotlin/com/komgareader/data/plugin/ConfigSchemaParser.kt`
- Test: `data/src/test/kotlin/com/komgareader/data/plugin/ConfigSchemaParserTest.kt`

**Interfaces:**
- Consumes: `ConfigSchema`, `ConfigField`, `FieldType` (plugin-api).
- Produces: `fun parseConfigSchema(json: String): ConfigSchema?` (null bei leer/kaputt; unbekannte `type` werden übersprungen; NUMBER liest `min/max/step` optional).

- [ ] **Step 1: Failing test**

```kotlin
package com.komgareader.data.plugin

import com.komgareader.plugin.FieldType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConfigSchemaParserTest {
    @Test
    fun parses_number_field_with_range() {
        val json = """{"fields":[{"key":"min_confidence","label":"Mindest-Confidence","type":"NUMBER","min":0.1,"max":1.0,"step":0.05,"default":"0.25"}]}"""
        val schema = parseConfigSchema(json)!!
        assertEquals(1, schema.fields.size)
        val f = schema.fields[0]
        assertEquals(FieldType.NUMBER, f.type)
        assertEquals("min_confidence", f.key)
        assertEquals(0.1, f.min); assertEquals(1.0, f.max); assertEquals(0.05, f.step)
        assertEquals("0.25", f.default)
    }

    @Test
    fun unknown_type_is_skipped() {
        val json = """{"fields":[{"key":"x","label":"X","type":"BOGUS"},{"key":"y","label":"Y","type":"BOOL"}]}"""
        val schema = parseConfigSchema(json)!!
        assertEquals(1, schema.fields.size)
        assertEquals("y", schema.fields[0].key)
    }

    @Test
    fun malformed_json_returns_null() {
        assertNull(parseConfigSchema("not json"))
    }

    @Test
    fun empty_fields_yields_empty_schema() {
        val schema = parseConfigSchema("""{"fields":[]}""")!!
        assertTrue(schema.fields.isEmpty())
    }
}
```

- [ ] **Step 2: Run, verify fail**

Run: `./gradlew :data:test --tests "com.komgareader.data.plugin.ConfigSchemaParserTest"`
Expected: FAIL — `parseConfigSchema` nicht definiert.

- [ ] **Step 3: Implement** — `ConfigSchemaParser.kt`:

```kotlin
package com.komgareader.data.plugin

import com.komgareader.plugin.ConfigField
import com.komgareader.plugin.ConfigSchema
import com.komgareader.plugin.FieldType
import org.json.JSONObject

/**
 * Reiner Parser eines data-only Plugin-Config-Schemas (org.json, analog [parsePresetSpecs]).
 * Unbekannte/fehlerhafte Feld-Typen werden übersprungen; `min/max/step` nur für NUMBER gelesen.
 * Liefert null bei nicht-parsebarem JSON.
 */
fun parseConfigSchema(json: String): ConfigSchema? {
    val root = runCatching { JSONObject(json) }.getOrNull() ?: return null
    val arr = root.optJSONArray("fields") ?: return ConfigSchema(emptyList())
    val fields = buildList {
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val type = runCatching { FieldType.valueOf(o.optString("type")) }.getOrNull() ?: continue
            val key = o.optString("key").ifEmpty { continue }
            add(
                ConfigField(
                    key = key,
                    label = o.optString("label", key),
                    type = type,
                    required = o.optBoolean("required", false),
                    default = o.optString("default", ""),
                    min = if (o.has("min")) o.optDouble("min") else null,
                    max = if (o.has("max")) o.optDouble("max") else null,
                    step = if (o.has("step")) o.optDouble("step") else null,
                ),
            )
        }
    }
    return ConfigSchema(fields)
}
```

- [ ] **Step 4: Run, verify pass**

Run: `./gradlew :data:test --tests "com.komgareader.data.plugin.ConfigSchemaParserTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add data/src/main/kotlin/com/komgareader/data/plugin/ConfigSchemaParser.kt data/src/test/kotlin/com/komgareader/data/plugin/ConfigSchemaParserTest.kt
git commit -m "feat(data): parseConfigSchema for data-only plugin config" -m "Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

### Task 8: SettingsRepository — `pluginConfig` get/set

**Files:**
- Modify: `domain/src/main/kotlin/com/komgareader/domain/repository/SettingsRepository.kt`
- Modify: `data/src/main/kotlin/com/komgareader/data/repository/RoomSettingsRepository.kt`
- Test: `data/src/test/kotlin/com/komgareader/data/repository/RoomSettingsRepositoryPluginConfigTest.kt`

**Interfaces:**
- Produces: `SettingsRepository.pluginConfig(pkg: String, key: String): Flow<String?>` und `suspend fun setPluginConfig(pkg: String, key: String, value: String)`. Room-Key = `plugincfg:<pkg>:<key>`.

- [ ] **Step 1: Failing test** (nutzt das im Repo bestehende in-memory Room-Test-Setup für `SettingsDao`; falls eine Fake-Dao-Variante existiert, diese spiegeln):

```kotlin
package com.komgareader.data.repository

import com.komgareader.data.db.SettingEntity
import com.komgareader.data.db.SettingsDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private class FakeSettingsDao : SettingsDao {
    val store = MutableStateFlow<Map<String, String>>(emptyMap())
    override fun observe(key: String): Flow<String?> = store.map { it[key] }
    override suspend fun put(entity: SettingEntity) { store.value = store.value + (entity.key to entity.value) }
    override suspend fun delete(key: String) { store.value = store.value - key }
}

class RoomSettingsRepositoryPluginConfigTest {
    @Test
    fun plugin_config_round_trips_under_namespaced_key() = runTest {
        val dao = FakeSettingsDao()
        val repo = RoomSettingsRepository(dao)
        val pkg = "com.komgareader.model.panel.yolo"
        assertNull(repo.pluginConfig(pkg, "min_confidence").let { flow -> flow.first() })
        repo.setPluginConfig(pkg, "min_confidence", "0.4")
        assertEquals("0.4", repo.pluginConfig(pkg, "min_confidence").first())
        assertEquals("0.4", dao.store.value["plugincfg:$pkg:min_confidence"])
    }
}
```

> Falls `RoomSettingsRepository` weitere Konstruktor-Parameter hat, im Test mit den vorhandenen Fakes/Defaults füllen (siehe bestehende Repo-Tests als Vorlage). `kotlinx.coroutines.flow.first` importieren.

- [ ] **Step 2: Run, verify fail**

Run: `./gradlew :data:test --tests "com.komgareader.data.repository.RoomSettingsRepositoryPluginConfigTest"`
Expected: FAIL — `pluginConfig`/`setPluginConfig` fehlen.

- [ ] **Step 3: Implement** — `SettingsRepository.kt` (Interface) ergänzen:

```kotlin
    /** Wert eines Plugin-Config-Felds (data-only Plugin-Config), null = nicht gesetzt. */
    fun pluginConfig(pkg: String, key: String): Flow<String?>
    suspend fun setPluginConfig(pkg: String, key: String, value: String)
```

`RoomSettingsRepository.kt` — Implementierung (Key-Helper + Methoden):

```kotlin
override fun pluginConfig(pkg: String, key: String): Flow<String?> = dao.observe(pluginCfgKey(pkg, key))
override suspend fun setPluginConfig(pkg: String, key: String, value: String) =
    dao.put(SettingEntity(pluginCfgKey(pkg, key), value))
```

Und in der `private companion object` (oder als top-level private fun):

```kotlin
private fun pluginCfgKey(pkg: String, key: String) = "plugincfg:$pkg:$key"
```

- [ ] **Step 4: Run, verify pass**

Run: `./gradlew :data:test --tests "com.komgareader.data.repository.RoomSettingsRepositoryPluginConfigTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add domain/src/main/kotlin/com/komgareader/domain/repository/SettingsRepository.kt data/src/main/kotlin/com/komgareader/data/repository/RoomSettingsRepository.kt data/src/test/kotlin/com/komgareader/data/repository/RoomSettingsRepositoryPluginConfigTest.kt
git commit -m "feat(settings): per-plugin config KV (plugincfg namespace)" -m "Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

### Task 9: PluginConfigForm — NUMBER → Slider

**Files:**
- Modify: `app/src/main/kotlin/com/komgareader/app/ui/settings/PluginConfigForm.kt`

**Interfaces:**
- Consumes: `FieldType.NUMBER`, `ConfigField.min/max/step`.
- Produces: NUMBER-Felder rendern als beschrifteter Slider; Wert wird als String (z.B. `"0.40"`) in `state.values` gehalten. `rememberPluginFormState`-Default für NUMBER = `default` oder `min` (oder `"0"`).

- [ ] **Step 1: Default-Vorbelegung für NUMBER** — in `rememberPluginFormState` die `initial`-Logik erweitern:

```kotlin
            val initial = field.default.ifEmpty {
                when (field.type) {
                    FieldType.BOOL -> "false"
                    FieldType.NUMBER -> (field.min ?: 0.0).toString()
                    else -> ""
                }
            }
```

- [ ] **Step 2: NUMBER-Branch in `PluginConfigField`** ergänzen (vor dem `FieldType.BOOL`-Branch):

```kotlin
        FieldType.NUMBER -> {
            val min = (field.min ?: 0.0).toFloat()
            val max = (field.max ?: 1.0).toFloat()
            val step = (field.step ?: 0.0).toFloat()
            val current = value.toFloatOrNull()?.coerceIn(min, max) ?: min
            val steps = if (step > 0f) (((max - min) / step).toInt() - 1).coerceAtLeast(0) else 0
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(field.label, style = MaterialTheme.typography.bodyLarge)
                    Text("%.2f".format(current), style = MaterialTheme.typography.bodyMedium)
                }
                androidx.compose.material3.Slider(
                    value = current,
                    onValueChange = { onValueChange("%.2f".format(it)) },
                    valueRange = min..max,
                    steps = steps,
                )
            }
        }
```

> E-Ink-Hinweis: Falls das Projekt eine eigene `EinkSlider`-Komponente hat (grep `fun EinkSlider` / Slider-Nutzung in `SettingsContent.kt` für `webtoonOverlapPercent`), diese statt `material3.Slider` verwenden, um den flachen E-Ink-Stil zu treffen. Signatur (value, onValueChange, valueRange, steps) bleibt gleich.

- [ ] **Step 3: Build verifizieren**

Run: `./gradlew :app:compileReleaseKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/komgareader/app/ui/settings/PluginConfigForm.kt
git commit -m "feat(ui): render NUMBER config field as slider" -m "Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

### Task 10: PluginsScreen — Zahnrad an PANEL_MODEL + Config-Dialog

**Files:**
- Modify: `app/src/main/kotlin/com/komgareader/app/ui/plugins/PluginsScreen.kt` (`DataPluginRow` um optionales `onConfigure`; PANEL_MODEL-Branch)
- Modify: `app/src/main/kotlin/com/komgareader/app/ui/plugins/PluginsViewModel.kt` (Schema laden + Werte + speichern)

**Interfaces:**
- Consumes: `PluginHost.dataPluginConfigJson`, `parseConfigSchema`, `SettingsRepository.pluginConfig/setPluginConfig`, `rememberPluginFormState`, `PluginConfigForm`.
- Produces: ViewModel-API `fun configSchemaFor(pkg: String): ConfigSchema?`, `fun savedConfig(pkg: String): Map<String,String>` (oder Flow), `suspend fun saveConfig(pkg: String, values: Map<String,String>)`; UI öffnet `EinkModal` mit Form.

- [ ] **Step 1: ViewModel — Schema + Persistenz**

In `PluginsViewModel` ergänzen (Hilt-injizierte `pluginHost` + `settings` vorhanden/ergänzen):

```kotlin
/** Geparstes Config-Schema des data-only Plugins, oder null wenn keins deklariert. */
fun configSchemaFor(pkg: String): ConfigSchema? =
    pluginHost.dataPluginConfigJson(pkg)?.let { parseConfigSchema(it) }

/** Aktuell gespeicherte Werte (oder Default aus dem Schema) je Feld. */
suspend fun savedConfig(pkg: String, schema: ConfigSchema): Map<String, String> =
    schema.fields.associate { f -> f.key to (settings.pluginConfig(pkg, f.key).first() ?: f.default) }

suspend fun saveConfig(pkg: String, values: Map<String, String>) {
    values.forEach { (k, v) -> settings.setPluginConfig(pkg, k, v) }
}
```

- [ ] **Step 2: `DataPluginRow` — optionales Zahnrad**

Signatur erweitern und Gear nur bei vorhandenem Callback rendern:

```kotlin
private fun DataPluginRow(
    title: String,
    typeLabel: String,
    abiLabel: String,
    abiVersion: Int,
    uninstallLabel: String,
    onInfo: () -> Unit,
    onUninstall: () -> Unit,
    configureLabel: String? = null,
    onConfigure: (() -> Unit)? = null,
) {
    // ... unveränderter Row-Aufbau bis vor dem Delete-IconButton ...
        IconButton(onClick = onInfo) {
            Icon(AppIcons.Info, contentDescription = s.pluginInfo, modifier = Modifier.size(22.dp))
        }
        if (onConfigure != null) {
            IconButton(onClick = onConfigure) {
                Icon(AppIcons.Settings, contentDescription = configureLabel ?: s.pluginConfigure, modifier = Modifier.size(22.dp))
            }
        }
        IconButton(onClick = onUninstall) {
            Icon(AppIcons.Delete, contentDescription = uninstallLabel, modifier = Modifier.size(22.dp))
        }
}
```

- [ ] **Step 3: PANEL_MODEL-Branch — Zahnrad verdrahten + Dialog-State**

Im Composable einen Dialog-State halten und im PANEL_MODEL-Branch das Schema prüfen:

```kotlin
var configTarget by remember { mutableStateOf<Pair<String, ConfigSchema>?>(null) }
// ... innerhalb PluginKind.PANEL_MODEL -> panelModelFor(...) { model ->
val schema = remember(model.packageName) { viewModel.configSchemaFor(model.packageName) }
DataPluginRow(
    title = model.displayName,
    typeLabel = s.pluginTabPanelModelLabel,
    abiLabel = s.pluginAbiLabel,
    abiVersion = model.abiVersion,
    uninstallLabel = s.pluginUninstall,
    onInfo = { viewModel.openInfoForInstalled(item) },
    onUninstall = { uninstall(model.packageName) },
    configureLabel = s.pluginConfigure,
    onConfigure = if (schema != null && schema.fields.isNotEmpty()) {
        { configTarget = model.packageName to schema }
    } else null,
)
```

- [ ] **Step 4: Config-Dialog (EinkModal + PluginConfigForm)**

Am Ende des Composable (nach der Liste):

```kotlin
configTarget?.let { (pkg, schema) ->
    val scope = rememberCoroutineScope()
    val formState = rememberPluginFormState(schema)
    LaunchedEffect(pkg) {
        viewModel.savedConfig(pkg, schema).forEach { (k, v) -> formState.values[k] = v }
    }
    EinkModal(
        title = s.pluginConfigure,
        confirmEnabled = formState.isValid,
        onConfirm = { scope.launch { viewModel.saveConfig(pkg, formState.snapshot()); configTarget = null } },
        onDismiss = { configTarget = null },
    ) {
        PluginConfigForm(formState)
    }
}
```

> `EinkModal`-Signatur am bestehenden Aufruf in `PluginsScreen`/`SettingsContent` spiegeln (Parameter-Namen `title`/`confirmEnabled`/`onConfirm`/`onDismiss` ggf. anpassen). `s.pluginConfigure` existiert bereits (von `PluginRow`).

- [ ] **Step 5: Build verifizieren**

Run: `./gradlew :app:compileReleaseKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/com/komgareader/app/ui/plugins/PluginsScreen.kt app/src/main/kotlin/com/komgareader/app/ui/plugins/PluginsViewModel.kt
git commit -m "feat(plugins): gear + config dialog for data-only plugins with schema" -m "Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

### Task 11: PanelSourceProvider — Confidence lesen + Invalidierung

**Files:**
- Modify: `app/src/main/kotlin/com/komgareader/app/ui/reader/PanelSourceProvider.kt`
- Test: `app/src/test/kotlin/com/komgareader/app/ui/reader/PanelSourceProviderConfidenceTest.kt`

**Interfaces:**
- Consumes: `SettingsRepository.pluginConfig`, `MlFilter`.
- Produces: gelesener `min_confidence` (Default 0.25) fließt in `MlFilter.minScore`; Cache invalidiert, wenn der gelesene Wert vom zuletzt gebauten abweicht.

- [ ] **Step 1: Failing test** (mit MockK `PluginHost` + Fake/Mock `SettingsRepository`; testet die reine Confidence-Auflösung über eine extrahierte Funktion):

```kotlin
package com.komgareader.app.ui.reader

import kotlin.test.Test
import kotlin.test.assertEquals

class PanelSourceProviderConfidenceTest {
    @Test
    fun parses_stored_confidence_with_default_fallback() {
        assertEquals(0.25f, resolveMinConfidence(null))
        assertEquals(0.4f, resolveMinConfidence("0.40"))
        assertEquals(0.25f, resolveMinConfidence("garbage"))
    }
}
```

- [ ] **Step 2: Run, verify fail**

Run: `./gradlew :app:testReleaseUnitTest --tests "com.komgareader.app.ui.reader.PanelSourceProviderConfidenceTest"`
Expected: FAIL — `resolveMinConfidence` fehlt.

- [ ] **Step 3: Implement** — `PanelSourceProvider.kt`:

```kotlin
private const val PANEL_YOLO_PKG = "com.komgareader.model.panel.yolo"
internal const val DEFAULT_MIN_CONFIDENCE = 0.25f

/** Parst den gespeicherten Confidence-String; Default [DEFAULT_MIN_CONFIDENCE] bei null/ungültig. */
internal fun resolveMinConfidence(stored: String?): Float =
    stored?.toFloatOrNull()?.takeIf { it in 0f..1f } ?: DEFAULT_MIN_CONFIDENCE
```

`build()` + Cache-Invalidierung anpassen:

```kotlin
    @Volatile private var cached: PanelSource? = null
    @Volatile private var cachedConfidence: Float = -1f

    suspend fun current(): PanelSource {
        val conf = resolveMinConfidence(settings.pluginConfig(PANEL_YOLO_PKG, "min_confidence").first())
        cached?.let { if (conf == cachedConfidence) return it }
        val source = build(conf)
        cached = source
        cachedConfidence = conf
        return source
    }

    private suspend fun build(minConfidence: Float): PanelSource {
        if (!settings.useMlDetection.first()) return GeometricPanelSource()
        val bytes = pluginHost.binaryDataPluginBytes(PluginCategory.PANEL_MODEL) ?: return GeometricPanelSource()
        return runCatching {
            MlPanelSource(OnnxModelRunner(bytes), MlFilter(minConfidence, 0.7f, 0.0f, null))
        }.getOrDefault(GeometricPanelSource())
    }
```

> Wichtig: Der Reader cached Panels pro Seite (`panelCache` in `ComicReaderViewModel.loadPanels`). Damit eine geänderte Confidence wirkt, muss beim Reader-(Wieder-)Eintritt der Panel-Cache geleert werden ODER der Provider die geänderte Source liefern und der VM den Cache verwerfen. Minimal-Lösung: in `ComicReaderViewModel` beim Start (`init`/erstem `loadPanels`) ist die Source ohnehin frisch; für Laufzeit-Änderung Doku-Hinweis „App-Reader neu öffnen". (Kein zusätzlicher Cache-Invalidierungs-Pfad im Scope.)

- [ ] **Step 4: Run, verify pass**

Run: `./gradlew :app:testReleaseUnitTest --tests "com.komgareader.app.ui.reader.PanelSourceProviderConfidenceTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/komgareader/app/ui/reader/PanelSourceProvider.kt app/src/test/kotlin/com/komgareader/app/ui/reader/PanelSourceProviderConfidenceTest.kt
git commit -m "feat(reader): read min_confidence from plugin config (default 0.25)" -m "Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

### Task 12: Debug-Overlay — Panel-Nummer + Score

**Files:**
- Modify: `app/src/main/kotlin/com/komgareader/app/ui/reader/ComicReaderScreen.kt` (Overlay-Block ~Z.173-186)

**Interfaces:**
- Consumes: `state.currentPanels: List<NormRect>` mit jetzt vorhandenem `score` (ComicCutter 0.4.0, Task 2/4).

- [ ] **Step 1: Overlay-Block erweitern** — Text je Panel oben-links zeichnen. Innerhalb des `Canvas`-Blocks (Compose `drawText` braucht ein `TextMeasurer`):

```kotlin
if (showOverlay && !state.zoomed && state.currentPanels.isNotEmpty()) {
    val textMeasurer = rememberTextMeasurer()
    Canvas(Modifier.fillMaxSize()) {
        val stroke = Stroke(width = 9f)
        state.currentPanels.forEachIndexed { i, p ->
            val tl = Offset(offX + p.left * contentW, offY + p.top * contentH)
            drawRect(
                color = Color(0xFF00C800),
                topLeft = tl,
                size = Size(p.width * contentW, p.height * contentH),
                style = stroke,
            )
            val label = "#${i + 1}  ${"%.2f".format(p.score)}"
            val measured = textMeasurer.measure(label)
            drawRect(color = Color(0xCC000000), topLeft = tl, size = Size(measured.size.width + 12f, measured.size.height + 8f))
            drawText(measured, color = Color.White, topLeft = Offset(tl.x + 6f, tl.y + 4f))
        }
    }
}
```

Imports ergänzen: `androidx.compose.ui.text.rememberTextMeasurer`, `androidx.compose.ui.graphics.drawscope.drawText` (bzw. `androidx.compose.ui.text.drawText`).

- [ ] **Step 2: Build verifizieren**

Run: `./gradlew :app:compileReleaseKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/komgareader/app/ui/reader/ComicReaderScreen.kt
git commit -m "feat(reader): show panel index + confidence in debug overlay" -m "Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

### Task 13: Misdetection-Sidecar — reiner JSON-Bau

**Files:**
- Create: `app/src/main/kotlin/com/komgareader/app/ui/reader/MisdetectionSidecar.kt`
- Test: `app/src/test/kotlin/com/komgareader/app/ui/reader/MisdetectionSidecarTest.kt`

**Interfaces:**
- Consumes: `NormRect` (ComicCutter).
- Produces: `fun misdetectionSidecarJson(panels: List<NormRect>, imageW: Int, imageH: Int): String` (pixel-space, mllabeltool-Prediction-Format).

- [ ] **Step 1: Failing test**

```kotlin
package com.komgareader.app.ui.reader

import com.panela.comiccutter.NormRect
import org.json.JSONObject
import kotlin.test.Test
import kotlin.test.assertEquals

class MisdetectionSidecarTest {
    @Test
    fun serializes_panels_to_pixel_space_items() {
        val panels = listOf(NormRect(left = 0.1f, top = 0.2f, width = 0.3f, height = 0.4f, score = 0.83f))
        val json = misdetectionSidecarJson(panels, imageW = 1000, imageH = 2000)
        val items = JSONObject(json).getJSONArray("items")
        assertEquals(1, items.length())
        val box = items.getJSONObject(0).getJSONArray("box")
        assertEquals(100, box.getInt(0)); assertEquals(400, box.getInt(1))
        assertEquals(300, box.getInt(2)); assertEquals(800, box.getInt(3))
        assertEquals("panel", items.getJSONObject(0).getString("label"))
        assertEquals(0.83, items.getJSONObject(0).getDouble("score"), 0.0001)
    }
}
```

- [ ] **Step 2: Run, verify fail**

Run: `./gradlew :app:testReleaseUnitTest --tests "com.komgareader.app.ui.reader.MisdetectionSidecarTest"`
Expected: FAIL — `misdetectionSidecarJson` fehlt.

- [ ] **Step 3: Implement**

```kotlin
package com.komgareader.app.ui.reader

import com.panela.comiccutter.NormRect
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

/**
 * Baut das mllabeltool-Prediction-Sidecar (pixel-space) zu den aktuell erkannten (falschen) Panels.
 * Format: { "items": [ { "box": [x,y,w,h], "label": "panel", "score": <float> } ] }.
 */
fun misdetectionSidecarJson(panels: List<NormRect>, imageW: Int, imageH: Int): String {
    val items = JSONArray()
    panels.forEach { p ->
        val box = JSONArray().apply {
            put((p.left * imageW).roundToInt())
            put((p.top * imageH).roundToInt())
            put((p.width * imageW).roundToInt())
            put((p.height * imageH).roundToInt())
        }
        items.put(JSONObject().put("box", box).put("label", "panel").put("score", p.score.toDouble()))
    }
    return JSONObject().put("items", items).toString()
}
```

- [ ] **Step 4: Run, verify pass**

Run: `./gradlew :app:testReleaseUnitTest --tests "com.komgareader.app.ui.reader.MisdetectionSidecarTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/komgareader/app/ui/reader/MisdetectionSidecar.kt app/src/test/kotlin/com/komgareader/app/ui/reader/MisdetectionSidecarTest.kt
git commit -m "feat(reader): pure misdetection sidecar JSON builder" -m "Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

### Task 14: Settings — SAF-Tree-Ordner für Misdetections

**Files:**
- Modify: `domain/src/main/kotlin/com/komgareader/domain/repository/SettingsRepository.kt` (`misdetectionDir` Flow + Setter)
- Modify: `data/src/main/kotlin/com/komgareader/data/repository/RoomSettingsRepository.kt` (Key + Impl)
- Modify: `app/src/main/kotlin/com/komgareader/app/ui/settings/SettingsContent.kt` (Picker-Zeile)
- Modify: zugehöriges Settings-ViewModel (Setter aufrufen)

**Interfaces:**
- Produces: `SettingsRepository.misdetectionDir: Flow<String?>`, `suspend fun setMisdetectionDir(uri: String?)`. Room-Key `misdetection_dir`.

- [ ] **Step 1: Repository erweitern** — Interface:

```kotlin
    /** SAF-Tree-URI des Misdetection-Capture-Ordners, null = nicht gesetzt (Button verborgen). */
    val misdetectionDir: Flow<String?>
    suspend fun setMisdetectionDir(uri: String?)
```

`RoomSettingsRepository.kt`:

```kotlin
// in companion: const val KEY_MISDETECTION_DIR = "misdetection_dir"
override val misdetectionDir: Flow<String?> = dao.observe(KEY_MISDETECTION_DIR)
override suspend fun setMisdetectionDir(uri: String?) {
    if (uri == null) dao.delete(KEY_MISDETECTION_DIR) else dao.put(SettingEntity(KEY_MISDETECTION_DIR, uri))
}
```

- [ ] **Step 2: Settings-UI Picker-Zeile** — in `SettingsContent.kt` analog zum Screensaver-Picker (Z.585), aber **OpenDocumentTree**:

```kotlin
val ctx = LocalContext.current
val misdetectionUri by viewModel.misdetectionDir.collectAsState(initial = null)
val treePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
    if (uri != null) {
        runCatching {
            ctx.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        viewModel.setMisdetectionDir(uri.toString())
    }
}
// Zeile: Label + aktueller Ordnername + "Wählen"/"Entfernen"-Buttons:
//   treePicker.launch(null)           // wählen
//   viewModel.setMisdetectionDir(null) // entfernen
```

Strings (`StringsDe`/`StringsEn`) ergänzen: `misdetectionDirLabel`, `misdetectionDirPick`, `misdetectionDirClear` (E-Ink-knapp). `MapBackedStrings` ist hier nicht betroffen (nur falls Sprach-Plugin-Parität getestet wird — dann nach String-Erweiterung neu generieren).

- [ ] **Step 3: ViewModel** — `misdetectionDir`-Flow durchreichen + `setMisdetectionDir(uri)` delegieren an `settings`.

- [ ] **Step 4: Build verifizieren**

Run: `./gradlew :app:compileReleaseKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add domain/ data/ app/src/main/kotlin/com/komgareader/app/ui/settings/SettingsContent.kt app/src/main/kotlin/com/komgareader/app/i18n/
git commit -m "feat(settings): SAF tree picker for misdetection capture dir" -m "Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

### Task 15: Reader-Overlay — Capture-Button + Datei-Schreiben

**Files:**
- Create: `app/src/main/kotlin/com/komgareader/app/ui/reader/MisdetectionWriter.kt` (SAF-I/O, Shell)
- Modify: `app/src/main/kotlin/com/komgareader/app/ui/reader/ComicPageLoader.kt` (public `loadFullBitmap`)
- Modify: `app/src/main/kotlin/com/komgareader/app/ui/reader/ComicReaderViewModel.kt` (Capture-Action)
- Modify: `app/src/main/kotlin/com/komgareader/app/ui/reader/ComicReaderScreen.kt` (Icon-Button in `actions`)

**Interfaces:**
- Consumes: `misdetectionSidecarJson` (Task 13), `SettingsRepository.misdetectionDir`, `ComicPageLoader`, `state.currentPanels`.
- Produces: `ComicPageLoader.loadFullBitmap(pageImage: SourceImage): Bitmap?`; `MisdetectionWriter.write(treeUri, baseName, bitmap, sidecarJson): Boolean`; `ComicReaderViewModel.captureMisdetection()`-Auslöser + ein `Flow`/Event für Snackbar.

- [ ] **Step 1: `loadFullBitmap`** — in `ComicPageLoader` die private `decode` zusätzlich öffentlich anbieten:

```kotlin
/** Volle (nicht herunterskalierte) Seiten-Bitmap — für Misdetection-Capture. */
suspend fun loadFullBitmap(pageImage: SourceImage): Bitmap? = decode(pageImage)
```

- [ ] **Step 2: `MisdetectionWriter`** (SAF-Shell):

```kotlin
package com.komgareader.app.ui.reader

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Schreibt Seitenbild (PNG) + Sidecar-JSON in den SAF-Tree-Ordner. Kollisionssicher (_2, _3…). */
class MisdetectionWriter(private val context: Context) {
    suspend fun write(treeUri: String, baseName: String, bitmap: Bitmap, sidecarJson: String): Boolean =
        withContext(Dispatchers.IO) {
            val dir = DocumentFile.fromTreeUri(context, Uri.parse(treeUri)) ?: return@withContext false
            val name = uniqueBase(dir, baseName)
            val png = dir.createFile("image/png", "$name.png") ?: return@withContext false
            context.contentResolver.openOutputStream(png.uri)?.use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            } ?: return@withContext false
            val side = dir.createFile("application/json", "$name.png.json") ?: return@withContext true
            context.contentResolver.openOutputStream(side.uri)?.use { it.write(sidecarJson.toByteArray()) }
            true
        }

    private fun uniqueBase(dir: DocumentFile, base: String): String {
        if (dir.findFile("$base.png") == null) return base
        var i = 2
        while (dir.findFile("${base}_$i.png") != null) i++
        return "${base}_$i"
    }
}
```

> Sidecar heißt `<name>.png.json` — mllabeltool-Label-Konvention (`image.jpg.json`). Falls mllabeltool stattdessen `<name>.json` erwartet, hier anpassen (siehe Plugin-README, Task 16).

- [ ] **Step 3: ViewModel-Capture** — in `ComicReaderViewModel`:

```kotlin
private val _captureEvent = MutableSharedFlow<Boolean>()  // true = ok, false = Fehler
val captureEvent: SharedFlow<Boolean> = _captureEvent

fun captureMisdetection() {
    viewModelScope.launch {
        val dir = settings.misdetectionDir.first()
        if (dir == null) { _captureEvent.emit(false); return@launch }
        val page = position.value.page  // aktuelle Seite (an vorhandenes State-Feld anpassen)
        val bmp = loader.loadFullBitmap(pages[page]) ?: run { _captureEvent.emit(false); return@launch }
        val panels = panelCache[page].orEmpty()
        val sidecar = misdetectionSidecarJson(panels, bmp.width, bmp.height)
        val base = "${bookRemoteId}_p${page + 1}".replace(Regex("[^A-Za-z0-9_-]"), "_")
        val ok = writer.write(dir, base, bmp, sidecar)
        _captureEvent.emit(ok)
    }
}
```

> `writer: MisdetectionWriter`, `settings`, `loader`, `pages`, `panelCache`, `bookRemoteId`, `position` an die real existierenden VM-Felder anpassen (siehe `loadPanels`-Kontext). `MisdetectionWriter` via Hilt oder mit `@ApplicationContext` konstruieren.

- [ ] **Step 4: Capture-Icon im Reader-Overlay** — in `ComicReaderScreen.kt` `actions`-Block (nur sichtbar bei gesetztem Ordner):

```kotlin
val misdetectionDir by comicVm.misdetectionDir.collectAsState(initial = null)
LaunchedEffect(Unit) {
    comicVm.captureEvent.collect { ok ->
        // Snackbar/Toast: ok ? s.misdetectionCaptured : s.misdetectionCaptureFailed
    }
}
// innerhalb actions = { ... }:
if (misdetectionDir != null) {
    IconButton(onClick = { comicVm.captureMisdetection() }) {
        Icon(AppIcons.Download, contentDescription = s.misdetectionCapture, tint = Color.White)
    }
}
```

Strings ergänzen: `misdetectionCapture`, `misdetectionCaptured`, `misdetectionCaptureFailed`. `comicVm.misdetectionDir` als Flow im VM aus `settings.misdetectionDir` durchreichen. Icon: vorhandenes `AppIcons.Download` (oder ein passenderes vorhandenes Icon).

- [ ] **Step 5: Build verifizieren**

Run: `./gradlew :app:compileReleaseKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/com/komgareader/app/ui/reader/
git commit -m "feat(reader): capture misdetected page (image + sidecar) to SAF dir" -m "Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

### Task 16 (komga-reader): Volltest + Docs nachziehen

**Files:**
- Modify: `docs/superpowers/specs/2026-06-17-panel-confidence-config-and-capture-design.md` (Status → gebaut) und ggf. `architecture-seams.md`-Notiz.

- [ ] **Step 1: Gesamter Testlauf**

Run: `./gradlew :plugin-api:test :data:test :app:testReleaseUnitTest`
Expected: PASS (alle neuen + bestehenden Tests).

- [ ] **Step 2: App baut**

Run: `./gradlew :app:assembleRelease`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Docs-Status + Commit**

```bash
git add docs/
git commit -m "docs: mark panel-confidence-config built; seam note" -m "Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Phase 3 — KomgaReaderPlugins (Repo: `~/Documents/Projekte/KomgaReaderPlugins`, Plugin)

### Task 17: `komga-panel-model-yolo` — config.json + Manifest + repo.json + README

**Files:**
- Create: `komga-panel-model-yolo/src/main/assets/config.json`
- Modify: `komga-panel-model-yolo/src/main/AndroidManifest.xml` (`DATA_CONFIG`-Meta + `ABI_VERSION` → 4, `versionCode`/`versionName` bump)
- Modify: `komga-panel-model-yolo/build.gradle.kts` (`versionCode`/`versionName` bump)
- Modify: `repo.json` (Panel-Plugin-Eintrag: ABI 4, neue Version) — **Hinweis:** Panel-Plugin ist aktuell evtl. nicht in `repo.json` gelistet; falls nicht, Eintrag analog zu Fonts ergänzen.
- Create: `komga-panel-model-yolo/README.md` (Capture→mllabeltool→Retrain-Loop, Daten-Provenance)

- [ ] **Step 1: `config.json`**

```json
{
  "fields": [
    {
      "key": "min_confidence",
      "label": "Mindest-Confidence",
      "type": "NUMBER",
      "min": 0.1,
      "max": 1.0,
      "step": 0.05,
      "default": "0.25"
    }
  ]
}
```

- [ ] **Step 2: Manifest** — Meta ergänzen, ABI heben:

```xml
        <meta-data android:name="com.komgareader.plugin.DATA_CATEGORY" android:value="PANEL_MODEL" />
        <meta-data android:name="com.komgareader.plugin.DATA_ASSET" android:value="best.int8.onnx" />
        <meta-data android:name="com.komgareader.plugin.DATA_CONFIG" android:value="config.json" />
        <meta-data android:name="com.komgareader.plugin.ABI_VERSION" android:value="4" />
```

- [ ] **Step 3: Versionen bumpen** — `build.gradle.kts` `versionCode = 2`, `versionName = "0.3.0"` (oder Repo-Konvention). `repo.json` Panel-Eintrag: `abiVersion: 4`, neue `versionCode`/`versionName`.

- [ ] **Step 4: README** — Loop dokumentieren (Daten-Provenance-Pflicht, global rule):

```markdown
# YOLO Panel-Modell

Data-only Plugin: shippt das INT8-ONNX-Panel-Erkennungsmodell + ein Config-Schema
(`config.json`) für die Mindest-Confidence (Slider 0.1–1.0, Default 0.25).

## Misdetection → Retraining-Loop
1. Reader: Capture-Button im Comic-Overlay (Ordner in Settings setzen) → kopiert Seite
   (`<serie>_pN.png`) + Sidecar (`<…>.png.json`, mllabeltool-Prediction-Format) in den SAF-Ordner.
2. PC: `mllabeltool` öffnet den Ordner → Sidecar = Prediction-Baseline → korrigieren → Export YOLO-txt.
3. `komga-yolo-spike`: Train (YOLO11) + INT8-Quantize → neues `best.int8.onnx` → hier ersetzen, Version bumpen.

## Daten-Provenance
Modell trainiert auf Golden-Age PD-Comics (archive.org) + NAS-Samples; INT8-QDQ-quantisiert.
Siehe `komga-yolo-spike` / `mllabeltool/models/yolo_v3/README.md`.
```

- [ ] **Step 5: Plugin baut**

Run: `cd ~/Documents/Projekte/KomgaReaderPlugins && ./gradlew :komga-panel-model-yolo:assembleDebug`
Expected: BUILD SUCCESSFUL; APK enthält `assets/config.json` + `assets/best.int8.onnx`.

- [ ] **Step 6: Commit**

```bash
git add komga-panel-model-yolo/ repo.json
git commit -m "feat(panel-yolo): config.json (min_confidence) + DATA_CONFIG meta, ABI 4" -m "Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Self-Review-Notiz

- **Spec-Abdeckung:** Confidence-Slider (Task 5,7,9,10,11,17) ✓; Zahnrad an Data-Plugin (Task 6,10) ✓; Overlay Index+Score (Task 1,2,12) ✓; Capture Bild+Sidecar (Task 13,15) ✓; SAF-Pfad konfigurierbar (Task 14) ✓; ComicCutter-Score-Release (Task 1–4) ✓; Plugin-Deklaration (Task 17) ✓.
- **Abhängigkeits-Reihenfolge:** Phase 1 (ComicCutter Release) MUSS vor Task 4 fertig + auf JitPack sein. Task 12 braucht Task 4 (NormRect.score). Task 15 braucht Task 13+14. Task 17 ist unabhängig (kann parallel), wirkt aber erst mit Task 6/7/10 live.
- **Typ-Konsistenz:** `resolveMinConfidence`, `parseConfigSchema`, `pluginConfig`/`setPluginConfig`, `misdetectionSidecarJson`, `dataPluginConfigJson`, `loadFullBitmap`, `MisdetectionWriter.write` durchgängig gleich benannt.
- **Bekannte Anpass-Stellen (kein Placeholder, aber Repo-Realität prüfen):** exakte `EinkModal`-/Settings-ViewModel-Signaturen, vorhandenes Slider-Component, `ComicReaderViewModel`-Feldnamen (`position`/`bookRemoteId`/`panelCache`) — beim Implementieren an den real existierenden Code spiegeln (Dateien sind in den Tasks genannt).
