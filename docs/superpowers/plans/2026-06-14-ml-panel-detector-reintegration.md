# ML Panel Detector Re-Integration Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Re-apply the "ML panel detector" feature (originally the single stale commit `bde5a504`, 106 commits behind `main`) freshly onto current `main`, reworked to fit how `main` has evolved.

**Architecture:** Two phases. **Phase 1** lands every additive, CI-safe part (new `PANEL_MODEL` plugin category, binary/metadata-only data-plugin discovery, repo-layer wiring, a `useMlDetection` setting, Plugins-tab + Settings UI, i18n, test-stub updates) — `guided-view` stays in-tree, no external dependency, `main` stays green. **Phase 2** performs the actual engine swap (delete the in-tree `guided-view` module, depend on the published `comic-cutter` library + ONNX runtime, introduce a `PanelGuide`/`PanelGuideProvider` indirection that picks geometric vs. ML detection per the `useMlDetection` setting and an installed `PANEL_MODEL` plugin). Phase 2 is gated on a CI-reachable `comic-cutter` 0.3.x coordinate carrying the `PanelGuide` + ONNX API.

**Tech Stack:** Kotlin, Jetpack Compose, Hilt, Room, `comic-cutter` (`io.github.gabriel-graf:comic-cutter` / JitPack `com.github.Gabriel-Graf:ComicCutter`), `com.microsoft.onnxruntime:onnxruntime-android`.

---

## Key divergence (why this is a rework, not a cherry-pick)

The stale commit forked **before** `UI_PACK` landed. On `main` today:

| Contract | Stale commit assumed | `main` reality → rework |
|---|---|---|
| `PluginCategory` 4th value | `PANEL_MODEL` | `UI_PACK` exists → `PANEL_MODEL` becomes the **5th** value |
| `PluginAbi.VERSION` | bumped 2→3 | currently **2** → bump to **3** (still additive, `MIN_SUPPORTED` stays 1) |
| `PluginKind` | adds `PANEL_MODEL` | has `UI_PACK` → add `PANEL_MODEL` alongside |
| `PluginTypeFilter` | adds `PANEL_MODELS` | has `UI_PACKS` → add `PANEL_MODELS` alongside |
| `DiscoveredDataPlugin` | text `assetJson` only | unchanged → **add** binary/meta variants (ONNX is multi-MB; never read bytes during a scan) |
| `MlFilter` ctor | 2-arg `(minScore, nmsIoU)` | published 0.3.1 is **4-arg** `(minScore, nmsIoU, minAreaFraction, keepClass: Int?)` → use 4-arg |
| Detector | external `comic-cutter` lib | in-tree `guided-view` → swap in Phase 2 |

**Verified facts (do not re-litigate):**
- `comic-cutter-jvm:0.3.1` carries the **entire** guided-view surface under `com.panela.comiccutter.*` with identical class names (`PanelDetector`, `PanelRect`, `ReadingDirection`, `ReadingOrder`, `PanelGeometry`, `NormRect`, `GuidedNavigator`, `GuidedPosition`) **plus** the ML stack (`PanelGuide`, `PanelSource`, `GeometricPanelSource`, `MlPanelSource`, `MlFilter`, `ModelRunner`, `PageGuide`, `RawDetection`). `comic-cutter-onnx-jvm:0.3.1` adds `OnnxModelRunner` + `Letterbox`.
- comic-cutter 0.3.1 publishes only `-jvm` and `-js` variants (no `-android`). It is pure Kotlin/JVM (no Android APIs) → Android consumes it. `onnx-jvm` excludes `com.microsoft.onnxruntime:onnxruntime`; the app supplies `onnxruntime-android`.
- Confirmed signatures: `PanelGuide()` (geometric) and `PanelGuide(PanelSource)`; `PanelGuide.guide(com.panela.comiccutter.model.RenderedPage): PageGuide`; `com.panela.comiccutter.model.RenderedPage(width: Int, height: Int, pixels: IntArray)`; `MlPanelSource(ModelRunner, MlFilter)`; `MlFilter(Float, Float, Float, Int?)`; `OnnxModelRunner(modelBytes: ByteArray)` (verify the byte-array ctor against the resolved artifact in Task 10).

---

# PHASE 1 — Additive, CI-safe (guided-view stays, no external dependency)

Branch off `main`:

```bash
git fetch origin && git switch main && git pull
git switch -c feat/ml-panel-detector-p1-additive
```

Build/test commands used throughout:
- Module unit tests: `./gradlew :plugin-api:test :plugin-host:test :data:test :app:testDebugUnitTest`
- Full compile: `./gradlew assembleDebug`

---

### Task 1: Add `PANEL_MODEL` category + bump ABI

**Files:**
- Modify: `plugin-api/src/main/kotlin/com/komgareader/plugin/PluginCategory.kt:8`
- Modify: `plugin-api/src/main/kotlin/com/komgareader/plugin/ColorPresetSpec.kt:7`

- [ ] **Step 1: Add the enum value**

Change line 8 of `PluginCategory.kt` to:

```kotlin
enum class PluginCategory { COLOR_PRESET, READER_PRESET, LANGUAGE, UI_PACK, PANEL_MODEL }
```

- [ ] **Step 2: Bump the ABI version**

In `ColorPresetSpec.kt`, change `const val VERSION = 2` to `const val VERSION = 3` and update the KDoc above `object PluginAbi`:

```kotlin
/** ABI-Gate als zwei Integer (kein semver-String) — Plugin-Plan-Entscheidung 2.
 *  VERSION 3 = additive Erweiterung (data-only Kategorie PANEL_MODEL, [PluginCategory]).
 *  MIN_SUPPORTED bleibt 1: color-preset-v1-APKs laden unverändert weiter. */
object PluginAbi {
    const val VERSION = 3
    const val MIN_SUPPORTED = 1
}
```

- [ ] **Step 3: Compile + commit**

Run: `./gradlew :plugin-api:test` → Expected: PASS
```bash
git add plugin-api/src/main/kotlin/com/komgareader/plugin/PluginCategory.kt plugin-api/src/main/kotlin/com/komgareader/plugin/ColorPresetSpec.kt
git commit -m "feat(plugin-api): add PANEL_MODEL data-plugin category, bump ABI to 3"
```

---

### Task 2: Binary / metadata-only data-plugin discovery

A PANEL_MODEL plugin ships a large binary ONNX asset. Reading it during every catalog scan would load multiple MB per scan, so we add (a) a **metadata-only** discovery for listing, and (b) a **bytes** discovery used only when actually instantiating the detector.

**Files:**
- Modify: `plugin-host/src/main/kotlin/com/komgareader/plugin/host/DiscoveredDataPlugin.kt`
- Modify: `plugin-host/src/main/kotlin/com/komgareader/plugin/host/PluginHost.kt`
- Test: `plugin-host/src/test/kotlin/com/komgareader/plugin/host/DataPluginManifestTest.kt` (extend if present; see Step 1)

- [ ] **Step 1: Write a failing test for the manifest resolver accepting PANEL_MODEL**

`resolveDataPluginManifest` already does `PluginCategory.valueOf(...)`, so PANEL_MODEL is resolved for free. Add a regression test so this is locked. Append to the existing `DataPluginManifestTest.kt` (create it if absent, mirroring the existing test style in `plugin-host/src/test/...`):

```kotlin
@Test
fun `resolves panel_model category from explicit keys`() {
    val r = resolveDataPluginManifest(
        dataCategory = "PANEL_MODEL",
        dataAsset = "panel_model.onnx",
        legacyColorPresets = null,
    )
    assertEquals(PluginCategory.PANEL_MODEL to "panel_model.onnx", r)
}
```

- [ ] **Step 2: Run it to verify it fails to compile/pass**

Run: `./gradlew :plugin-host:test --tests '*DataPluginManifestTest'`
Expected: PASS already (resolver is generic). If the file did not exist, it now compiles and passes. (This test documents the contract; keep it.)

- [ ] **Step 3: Add the two new discovery data types**

Append to `DiscoveredDataPlugin.kt`:

```kotlin
/**
 * Metadaten eines installierten, ABI-kompatiblen data-only Plugins OHNE dass das Asset gelesen wird.
 * Für Kategorien mit großen Binär-Assets (z.B. PANEL_MODEL/ONNX, mehrere MB): Listen/UI brauchen nur
 * Identität + ABI + Asset-Name, nicht die Bytes. Bytes erst über [PluginHost.binaryDataPluginBytes].
 */
data class DataPluginInfo(
    val packageName: String,
    val category: PluginCategory,
    val abiVersion: Int,
    val assetName: String,
    val displayName: String,
)
```

- [ ] **Step 4: Add the two PluginHost methods**

Insert into `PluginHost` (after `discoverDataPlugins`). The first is a near-clone of `discoverDataPlugins` that **skips the asset read**; the second reads the asset as raw bytes on demand.

```kotlin
    /**
     * Wie [discoverDataPlugins], aber OHNE das Asset zu lesen — nur Identität/ABI/Asset-Name. Für
     * Kategorien mit großen Binär-Assets (PANEL_MODEL): das Listing soll nie mehrere MB pro Scan laden.
     */
    fun discoverDataPluginInfos(category: PluginCategory): List<DataPluginInfo> {
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
            val label = pkg.applicationInfo?.let { appInfo ->
                runCatching { pm.getApplicationLabel(appInfo).toString() }.getOrNull()?.ifBlank { null }
            } ?: pkg.packageName
            DataPluginInfo(pkg.packageName, resolvedCategory, abi, assetName, label)
        }
    }

    /**
     * Liest die rohen Asset-Bytes des ersten installierten, ABI-kompatiblen data-only Plugins der
     * [category] (via `createPackageContext(pkg, 0)`, Flags 0 = nur Ressourcen, KEIN Code). null,
     * wenn keines installiert ist oder das Asset nicht lesbar ist. Für binäre Assets (ONNX-Modell).
     */
    fun binaryDataPluginBytes(category: PluginCategory): ByteArray? {
        val info = discoverDataPluginInfos(category).firstOrNull() ?: return null
        return runCatching {
            context.createPackageContext(info.packageName, 0)
                .assets.open(info.assetName).use { it.readBytes() }
        }.getOrNull()
    }
```

- [ ] **Step 5: Compile + commit**

Run: `./gradlew :plugin-host:test` → Expected: PASS
```bash
git add plugin-host/src/main/kotlin/com/komgareader/plugin/host/DiscoveredDataPlugin.kt plugin-host/src/main/kotlin/com/komgareader/plugin/host/PluginHost.kt plugin-host/src/test
git commit -m "feat(plugin-host): metadata-only + raw-bytes discovery for binary data plugins"
```

---

### Task 3: Repo-layer wiring for PANEL_MODEL

**Files:**
- Modify: `data/src/main/kotlin/com/komgareader/data/plugin/repo/RepoModels.kt:6`
- Modify: `data/src/main/kotlin/com/komgareader/data/plugin/repo/RepoIndexParser.kt:25-31`
- Modify: `data/src/main/kotlin/com/komgareader/data/plugin/repo/PluginCatalogFilter.kt:4,27-34`
- Test: `data/src/test/kotlin/com/komgareader/data/plugin/repo/RepoIndexParserTest.kt`

- [ ] **Step 1: Write the failing test**

Append to `RepoIndexParserTest.kt` (mirror the existing `pluginKindOf` tests):

```kotlin
@Test
fun `maps panel_model type to PANEL_MODEL kind`() {
    assertEquals(PluginKind.PANEL_MODEL, pluginKindOf("panel_model"))
    assertEquals(PluginKind.PANEL_MODEL, pluginKindOf("PANEL_MODEL"))
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :data:test --tests '*RepoIndexParserTest'`
Expected: FAIL — `PluginKind.PANEL_MODEL` unresolved (won't compile).

- [ ] **Step 3: Add the kind, the mapping, the filter**

`RepoModels.kt` line 6:
```kotlin
enum class PluginKind { SOURCE, PRESET, LANGUAGE, READER_PRESET, UI_PACK, PANEL_MODEL }
```

`RepoIndexParser.kt` `pluginKindOf` — add a branch before `else`:
```kotlin
    type.equals("panel_model", ignoreCase = true) -> PluginKind.PANEL_MODEL
```

`PluginCatalogFilter.kt` line 4:
```kotlin
enum class PluginTypeFilter { ALL, SOURCES, PRESETS, LANGUAGES, READER_PRESETS, UI_PACKS, PANEL_MODELS }
```
and add to `PluginKind.matches` `when (filter)`:
```kotlin
    PluginTypeFilter.PANEL_MODELS -> this == PluginKind.PANEL_MODEL
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :data:test --tests '*RepoIndexParserTest'` → Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add data/src/main/kotlin/com/komgareader/data/plugin/repo data/src/test/kotlin/com/komgareader/data/plugin/repo
git commit -m "feat(data): repo PluginKind.PANEL_MODEL + panel_model type mapping + filter"
```

---

### Task 4: `useMlDetection` setting (domain + data)

**Files:**
- Modify: `domain/src/main/kotlin/com/komgareader/domain/repository/SettingsRepository.kt`
- Modify: `data/src/main/kotlin/com/komgareader/data/repository/RoomSettingsRepository.kt`
- Test: `data/src/test/kotlin/com/komgareader/data/repository/RoomSettingsRepositoryTest.kt`

No Room migration: settings live in the generic key-value `settings` table.

- [ ] **Step 1: Write the failing test**

Append to `RoomSettingsRepositoryTest.kt` (mirror an existing boolean-setting test such as the one for `guidedPanelOverlay`/`deviceManagedRefresh`):

```kotlin
@Test
fun `useMlDetection defaults true and round-trips`() = runTest {
    assertTrue(repo.useMlDetection.first())       // default true (key absent)
    repo.setUseMlDetection(false)
    assertFalse(repo.useMlDetection.first())
    repo.setUseMlDetection(true)
    assertTrue(repo.useMlDetection.first())
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :data:test --tests '*RoomSettingsRepositoryTest'`
Expected: FAIL — `useMlDetection`/`setUseMlDetection` unresolved.

- [ ] **Step 3: Add to the interface**

In `SettingsRepository.kt`, add near `guidedPanelOverlay`:
```kotlin
    /** Comic-Guided: Panel-Erkennung per ML-Modell-Plugin (PANEL_MODEL); aus = Geometrie-Fallback. Default true. */
    val useMlDetection: Flow<Boolean>
```
and with the other setters:
```kotlin
    suspend fun setUseMlDetection(value: Boolean)
```

- [ ] **Step 4: Implement in Room (default true)**

In `RoomSettingsRepository.kt` add a key constant alongside the others (e.g. near `KEY_PANEL_OVERLAY`):
```kotlin
        private const val KEY_USE_ML = "use_ml_detection"
```
the Flow (default true ⇒ only `"false"` is off, matching the original design):
```kotlin
    override val useMlDetection: Flow<Boolean> = dao.observe(KEY_USE_ML).map { it != "false" }
```
the setter (mirror the existing string-valued setters in this file):
```kotlin
    override suspend fun setUseMlDetection(value: Boolean) = dao.put(SettingEntity(KEY_USE_ML, value.toString()))
```
> Note: confirm the exact `dao.put(...)`/`SettingEntity` shape by copying it verbatim from an existing setter in the same file (e.g. `setGuidedPanelOverlay`).

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew :data:test --tests '*RoomSettingsRepositoryTest'` → Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add domain/src/main/kotlin/com/komgareader/domain/repository/SettingsRepository.kt data/src/main/kotlin/com/komgareader/data/repository/RoomSettingsRepository.kt data/src/test/kotlin/com/komgareader/data/repository/RoomSettingsRepositoryTest.kt
git commit -m "feat(settings): useMlDetection flag (default true, no migration)"
```

---

### Task 5: Fix all `SettingsRepository` stubs/fakes (compile gate)

Adding interface members breaks every test double + any non-Room impl. Find and fix them all before proceeding.

**Files:** discovered via grep (do not assume the list).

- [ ] **Step 1: Find every implementor**

Run:
```bash
grep -rln 'SettingsRepository' --include=*.kt app domain data | xargs grep -l ': SettingsRepository' 
grep -rln 'override val guidedPanelOverlay' --include=*.kt .
```
Known from the stale commit, at minimum: `app/src/test/.../ui/reader/ReaderViewModelTest.kt`, `app/src/test/.../ui/settings/SettingsViewModelTest.kt` (multiple stub classes).

- [ ] **Step 2: Add the two members to each stub**

For each stub/fake implementing `SettingsRepository`, add:
```kotlin
    override val useMlDetection: Flow<Boolean> = flowOf(true)
    override suspend fun setUseMlDetection(value: Boolean) {}
```
(For a "capturing" fake that records writes, store the value like its siblings do.)

- [ ] **Step 3: Compile all tests**

Run: `./gradlew :app:testDebugUnitTest :data:test` → Expected: PASS (compiles + green).

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "test: implement useMlDetection in all SettingsRepository test doubles"
```

---

### Task 6: i18n strings (de + en parity)

**Files:**
- Modify: `app/src/main/kotlin/com/komgareader/app/i18n/Strings.kt` (interface + the de/en impls in this file)
- Modify: `app/src/main/kotlin/com/komgareader/app/i18n/MapBackedStrings.kt`
- Test: `app/src/test/kotlin/com/komgareader/app/i18n/MapBackedStringsTest.kt`

- [ ] **Step 1: Add the keys to the interface + both impls**

Add these properties to the `Strings` interface and provide German + English values in both impls (mirror an existing key such as `pluginUninstall`):

| key | DE | EN |
|---|---|---|
| `pluginTabPanelModelLabel` | `"Panel-Modell"` | `"Panel model"` |
| `pluginFilterPanelModels` | `"Panel-Modelle"` | `"Panel models"` |
| `readerUseMlDetection` | `"Panel-Erkennung per ML-Modell (aus = Geometrie-Fallback)"` | `"ML model panel detection (off = geometric fallback)"` |

(If the Comic settings section needs its own header and none exists, also add `settingsScopeComic` = `"Comic (Guided)"` / `"Comic (guided)"`. Check `SettingsContent.kt` first — reuse an existing scope header if present.)

- [ ] **Step 2: Add fallthrough getters in MapBackedStrings**

For each new key, add an override mirroring the existing pattern:
```kotlin
    override val pluginTabPanelModelLabel: String get() = overrides["pluginTabPanelModelLabel"] ?: fallback.pluginTabPanelModelLabel
    override val pluginFilterPanelModels: String get() = overrides["pluginFilterPanelModels"] ?: fallback.pluginFilterPanelModels
    override val readerUseMlDetection: String get() = overrides["readerUseMlDetection"] ?: fallback.readerUseMlDetection
```

- [ ] **Step 3: Run the i18n parity test**

Run: `./gradlew :app:testDebugUnitTest --tests '*MapBackedStringsTest'` → Expected: PASS (compile-time parity holds; if the test enumerates keys, it now covers the new ones).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/komgareader/app/i18n app/src/test/kotlin/com/komgareader/app/i18n
git commit -m "i18n: panel-model labels + ML-detection setting string (de+en)"
```

---

### Task 7: Surface PANEL_MODEL in `PluginCatalog`

**Files:**
- Modify: `app/src/main/kotlin/com/komgareader/app/data/PluginCatalog.kt`

- [ ] **Step 1: Add the metadata-only StateFlow + scan**

Mirror an existing data-plugin field (e.g. `_uiPackDataPlugins`). Add:
```kotlin
    private val _panelModelDataPlugins = MutableStateFlow<List<DataPluginInfo>>(emptyList())
    val panelModelDataPlugins: StateFlow<List<DataPluginInfo>> = _panelModelDataPlugins.asStateFlow()
```
In `scanLocal()` (wherever the other `discoverDataPlugins(...)`/`discoverDataPluginInfos` calls happen), add:
```kotlin
        _panelModelDataPlugins.value = pluginHost.discoverDataPluginInfos(PluginCategory.PANEL_MODEL)
```
Import `com.komgareader.plugin.host.DataPluginInfo` and `com.komgareader.plugin.PluginCategory`.

- [ ] **Step 2: Map into `installedEntriesOf` (the function that builds `List<InstalledEntry>`)**

Add the panel models to the installed list, mirroring how UI-pack data plugins are mapped:
```kotlin
        _panelModelDataPlugins.value.map { InstalledEntry(it.packageName, it.displayName, PluginKind.PANEL_MODEL) }
```
(Combine into the existing concatenation that produces installed entries.)

- [ ] **Step 3: Compile + commit**

Run: `./gradlew :app:assembleDebug` → Expected: BUILD SUCCESSFUL
```bash
git add app/src/main/kotlin/com/komgareader/app/data/PluginCatalog.kt
git commit -m "feat(app): PluginCatalog discovers PANEL_MODEL data plugins (metadata-only)"
```

---

### Task 8: Plugins-tab UI (ViewModel + Screen + filter chip)

**Files:**
- Modify: `app/src/main/kotlin/com/komgareader/app/ui/plugins/PluginsViewModel.kt`
- Modify: `app/src/main/kotlin/com/komgareader/app/ui/plugins/PluginsScreen.kt`
- Modify: `app/src/main/kotlin/com/komgareader/app/ui/components/PluginFilterMenu.kt`

- [ ] **Step 1: Expose the flow in the ViewModel**

Add (mirror `uiPackDataPlugins`):
```kotlin
    val panelModelDataPlugins: StateFlow<List<DataPluginInfo>> = catalog.panelModelDataPlugins
```
and include it in whatever `combine(...)` produces the `visible` rows so PANEL_MODEL entries appear in the installed section.

- [ ] **Step 2: Render the installed row**

In `PluginsScreen.kt`, collect the flow and add the lookup + the `PluginKind.PANEL_MODEL` case in the installed-row `when`, mirroring the UI-pack/language data-plugin row (uses `DataPluginRow` with `typeLabel = s.pluginTabPanelModelLabel`, `uninstallLabel = s.pluginUninstall`, `onUninstall = { uninstall(model.packageName) }`). Add the repo-row label case too: `PluginKind.PANEL_MODEL -> s.pluginTabPanelModelLabel`.

- [ ] **Step 3: Add the filter chip**

In `PluginFilterMenu.kt`, add a `FilterRow` for `PluginTypeFilter.PANEL_MODELS` with label `s.pluginFilterPanelModels`, mirroring the `UI_PACKS` row.

- [ ] **Step 4: Compile + commit**

Run: `./gradlew :app:assembleDebug` → Expected: BUILD SUCCESSFUL
```bash
git add app/src/main/kotlin/com/komgareader/app/ui/plugins app/src/main/kotlin/com/komgareader/app/ui/components/PluginFilterMenu.kt
git commit -m "feat(app): Plugins tab lists + filters PANEL_MODEL plugins"
```

---

### Task 9: Settings UI — ML-detection toggle

**Files:**
- Modify: `app/src/main/kotlin/com/komgareader/app/ui/settings/SettingsContent.kt`
- Modify: `app/src/main/kotlin/com/komgareader/app/ui/settings/SettingsViewModel.kt`

- [ ] **Step 1: Expose flow + setter in the ViewModel**

Mirror `guidedPanelOverlay` wiring:
```kotlin
    val useMlDetection = settings.useMlDetection.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    fun setUseMlDetection(value: Boolean) { viewModelScope.launch { settings.setUseMlDetection(value) } }
```

- [ ] **Step 2: Add the SwitchRow in the Comic scope**

In `SettingsContent.kt`, in the Comic/guided section (where `readerPanelOverlay`/`guidedPanelOverlay` toggle already lives — reuse that scope), add a `SwitchRow`:
```kotlin
        SwitchRow(
            label = s.readerUseMlDetection,
            checked = useMlDetection,
            onCheckedChange = onSetUseMlDetection,
        )
```
Thread `useMlDetection`/`onSetUseMlDetection` down from the ViewModel exactly like the existing overlay toggle.

- [ ] **Step 3: Compile + commit**

Run: `./gradlew :app:assembleDebug` → Expected: BUILD SUCCESSFUL
```bash
git add app/src/main/kotlin/com/komgareader/app/ui/settings
git commit -m "feat(app): settings toggle for ML panel detection"
```

---

### Phase 1 gate

- [ ] **Full build + all unit tests green**

Run: `./gradlew assembleDebug :plugin-api:test :plugin-host:test :data:test :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, all tests PASS.

- [ ] **Behavior unchanged on device** — comic reader still uses in-tree `guided-view` geometric detection (no PANEL_MODEL plugin installed yet, `useMlDetection` has no effect until Phase 2 wires it). Sanity-launch on emulator `eink_test`; open a comic, confirm guided panel zoom works as before.

- [ ] **Open PR for Phase 1** (mergeable independently; nothing here depends on the external lib).

```bash
git push -u origin feat/ml-panel-detector-p1-additive
gh pr create --fill --base main
```

---

# PHASE 2 — Engine swap (gated on a CI-reachable comic-cutter 0.3.x)

**Precondition:** a publicly resolvable `comic-cutter` coordinate at **0.3.x** (carrying `PanelGuide` + the ONNX stack) exists — either JitPack `com.github.Gabriel-Graf:ComicCutter:<0.3.x tag>` or Maven Central `io.github.gabriel-graf:comic-cutter:0.3.x`. The user confirmed comic-cutter is published; Task 10 verifies the exact reachable coordinate/version. **Do not** wire `mavenLocal()` into `settings.gradle` for CI — resolve from the public repo.

Branch off `main` (after Phase 1 merges) or off the Phase 1 branch:

```bash
git switch -c feat/ml-panel-detector-p2-engine-swap
```

---

### Task 10: Declare the dependency (resolve the real coordinate first)

**Files:**
- Modify: `settings.gradle.kts` (add the repo if JitPack)
- Modify: `app/build.gradle.kts`

- [ ] **Step 1: Resolve the exact reachable coordinate + version**

Confirm which of these resolves with the `PanelGuide` + `onnx` API (0.3.x):
```bash
# JitPack (tag is enough, no server):
#   repo: maven("https://jitpack.io"), dep: com.github.Gabriel-Graf:ComicCutter:<tag>
# Maven Central:
#   dep: io.github.gabriel-graf:comic-cutter:0.3.x  (+ :comic-cutter-onnx-jvm:0.3.x)
```
Pick the coordinate that exposes `com.panela.comiccutter.PanelGuide` and `com.panela.comiccutter.onnx.OnnxModelRunner`. Record it in the task as `<CC_DEP>` / `<CC_ONNX_DEP>`. Also confirm the `OnnxModelRunner(ByteArray)` and `MlFilter(Float,Float,Float,Int?)` signatures against the resolved jar:
```bash
# example, against whichever jar resolves:
javap -p -cp <resolved-jar> com.panela.comiccutter.PageGuide com.panela.comiccutter.onnx.OnnxModelRunner com.panela.comiccutter.MlFilter
```

- [ ] **Step 2: Add the repository (JitPack case only)**

If using JitPack, add to `settings.gradle.kts` `dependencyResolutionManagement { repositories { ... } }` (after `mavenCentral()`):
```kotlin
        maven { url = uri("https://jitpack.io") }
```

- [ ] **Step 3: Add dependencies + ABI filter**

In `app/build.gradle.kts` `dependencies { }`:
```kotlin
    implementation("<CC_DEP>")                 // comic-cutter (geometric + ML API)
    implementation("<CC_ONNX_DEP>") {          // comic-cutter ONNX adapter
        exclude(group = "com.microsoft.onnxruntime", module = "onnxruntime")
    }
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.20.0")
```
In `android { defaultConfig { ndk { ... } } }` (create the block if absent — onnxruntime-android ships native libs only for these ABIs):
```kotlin
            ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
```
> `arm64-v8a` covers the Boox Go Color 7; `x86_64` covers the emulator. Document the APK-size impact (onnxruntime native is several MB) in the PR.

- [ ] **Step 4: Resolve + compile (lib still unused → still green)**

Run: `./gradlew :app:dependencies --configuration debugRuntimeClasspath | grep -i comic-cutter` → Expected: the coordinate resolves from the public repo (not mavenLocal).
Run: `./gradlew :app:assembleDebug` → Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add settings.gradle.kts app/build.gradle.kts
git commit -m "build(app): depend on published comic-cutter + onnxruntime-android"
```

---

### Task 11: Re-package the comic reader onto comic-cutter (geometric, behavior-identical)

Swap the `com.komgareader.guidedview.*` imports for `com.panela.comiccutter.*` (identical class names) and bridge the two `RenderedPage` types. No ML yet — pure geometric, so the reader behaves exactly as before but now backed by the library.

**Files:**
- Modify: `app/src/main/kotlin/com/komgareader/app/ui/reader/ComicPageLoader.kt`
- Modify: `app/src/main/kotlin/com/komgareader/app/ui/reader/ComicReaderViewModel.kt`
- Modify: `app/src/main/kotlin/com/komgareader/app/ui/reader/ComicReaderScreen.kt` (imports only — it uses `NormRect`/`PanelGeometry`)

- [ ] **Step 1: Bridge RenderedPage in `ComicPageLoader`**

Replace the guided-view imports:
```kotlin
import com.panela.comiccutter.PanelDetector
import com.panela.comiccutter.PanelRect
import com.panela.comiccutter.ReadingDirection
```
Keep the domain `RenderedPage` import for the bitmap decode, then convert to the lib type before `detector.detect(...)`. Change `toRenderedPage`'s return + the `detect` body so the page handed to the library is `com.panela.comiccutter.model.RenderedPage`:
```kotlin
    private fun toRenderedPage(bmp: Bitmap): com.panela.comiccutter.model.RenderedPage {
        val pixels = IntArray(bmp.width * bmp.height)
        bmp.getPixels(pixels, 0, bmp.width, 0, 0, bmp.width, bmp.height)
        return com.panela.comiccutter.model.RenderedPage(bmp.width, bmp.height, pixels)
    }
```
`PageDetection` now holds `List<com.panela.comiccutter.PanelRect>`. The detector call is unchanged in shape:
```kotlin
            val panels = detector.detect(page, ReadingDirection.LEFT_TO_RIGHT)
            PageDetection(panels, page.width, page.height)
```

- [ ] **Step 2: Re-package imports in `ComicReaderViewModel` + `ComicReaderScreen`**

Replace every `com.komgareader.guidedview.X` import with `com.panela.comiccutter.X` (`GuidedNavigator`, `GuidedPosition`, `NormRect`, `PanelGeometry`, plus `PanelRect` where referenced). All call sites (`PanelGeometry.normalize/hitTest/maxAreaFraction`, `GuidedNavigator.next/previous`, `GuidedPosition`) keep the same names/signatures (verified identical).

- [ ] **Step 3: Compile + run comic-reader unit tests**

Run: `./gradlew :app:testDebugUnitTest --tests '*Comic*'` → Expected: PASS.
Run: `./gradlew :app:assembleDebug` → Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Emulator smoke — geometric parity**

Install on `eink_test`, open a comic, confirm guided panel zoom / tap-to-zoom / page step behave exactly as Phase 1.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/komgareader/app/ui/reader/ComicPageLoader.kt app/src/main/kotlin/com/komgareader/app/ui/reader/ComicReaderViewModel.kt app/src/main/kotlin/com/komgareader/app/ui/reader/ComicReaderScreen.kt
git commit -m "refactor(comic): back panel detection with comic-cutter lib (geometric, parity)"
```

---

### Task 12: `PanelGuideProvider` — geometric vs. ML selection

Introduce the indirection that the `useMlDetection` setting + an installed `PANEL_MODEL` plugin drive. The provider returns a `com.panela.comiccutter.PanelGuide` (geometric `PanelGuide()` or ML `PanelGuide(MlPanelSource(OnnxModelRunner(bytes), MlFilter(...)))`), cached, with graceful fallback to geometric on any failure.

**Files:**
- Create: `app/src/main/kotlin/com/komgareader/app/ui/reader/PanelGuideProvider.kt`
- Modify: `app/src/main/kotlin/com/komgareader/app/ui/reader/ComicPageLoader.kt`
- Modify: `app/src/main/kotlin/com/komgareader/app/ui/reader/ComicReaderViewModel.kt`
- Modify: DI module providing `PluginHost` (it is already Hilt-provided in `AppModule` per CLAUDE.md) — inject `PanelGuideProvider` into `ComicReaderViewModel`.

- [ ] **Step 1: Write the provider**

```kotlin
package com.komgareader.app.ui.reader

import com.komgareader.domain.repository.SettingsRepository
import com.komgareader.plugin.PluginCategory
import com.komgareader.plugin.host.PluginHost
import com.panela.comiccutter.MlFilter
import com.panela.comiccutter.MlPanelSource
import com.panela.comiccutter.PanelGuide
import com.panela.comiccutter.onnx.OnnxModelRunner
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Liefert die [PanelGuide], mit der der Comic-Reader Panels erkennt — ML (PANEL_MODEL-Plugin) oder
 * Geometrie-Fallback. Single source of truth für die Detektor-Wahl; der Reader kennt den Unterschied
 * nicht. Auswahl: Setting [SettingsRepository.useMlDetection] AN + ein installiertes PANEL_MODEL-Plugin
 * + erfolgreiche ONNX-Init → ML; sonst (aus, kein Plugin, Init-Fehler) → geometrischer Guide. Das
 * Ergebnis wird gecacht (Modell-Laden ist teuer).
 */
@Singleton
class PanelGuideProvider @Inject constructor(
    private val pluginHost: PluginHost,
    private val settings: SettingsRepository,
) {
    @Volatile private var cached: PanelGuide? = null

    suspend fun current(): PanelGuide {
        cached?.let { return it }
        val guide = build()
        cached = guide
        return guide
    }

    private suspend fun build(): PanelGuide {
        if (!settings.useMlDetection.first()) return PanelGuide()
        val bytes = pluginHost.binaryDataPluginBytes(PluginCategory.PANEL_MODEL) ?: return PanelGuide()
        return runCatching {
            val runner = OnnxModelRunner(bytes)
            PanelGuide(MlPanelSource(runner, MlFilter(0.25f, 0.7f, 0.0f, null)))
        }.getOrDefault(PanelGuide())
    }
}
```
> Verify the `MlFilter` 4-arg values + the `MlPanelSource(ModelRunner, MlFilter)` and `OnnxModelRunner(ByteArray)` ctors against the resolved jar (Task 10 Step 1). `PanelGuide.guide(RenderedPage): PageGuide` returns the ordered panels — confirm the property name on `PageGuide` (e.g. `panels`) via `javap` and use it in Step 2.

- [ ] **Step 2: Route `ComicPageLoader` through the guide**

`ComicPageLoader` should detect via the provided `PanelGuide` rather than a hardcoded `PanelDetector`. Change its `detect` to accept (or hold) the guide and call `guide.guide(renderedPage).panels` (confirm `PageGuide.panels` name). Keep the downscale + bitmap decode untouched. The geometric path is `PanelGuide()` (no detector args), so this also subsumes Task 11's direct `PanelDetector` usage.

- [ ] **Step 3: Inject the provider into the ViewModel**

Add `private val guideProvider: PanelGuideProvider` to `ComicReaderViewModel`'s constructor. In `loadPanels(page)`, obtain the guide once (`val guide = guideProvider.current()`) and pass it to the loader. The degenerate-guard logic (`< 2 panels` / `> 0.85` area → full page) stays in the ViewModel unchanged.

- [ ] **Step 4: Fix the comic-reader test double**

`ReaderViewModelTest` / any `ComicReaderViewModel` test must provide a `PanelGuideProvider`. Since it depends on `PluginHost` (Android) + `SettingsRepository`, prefer constructing the VM in tests that already fake these, or extract the panel-source decision behind a tiny seam if a test needs it. Keep changes minimal — match the existing test setup.

- [ ] **Step 5: Compile + unit tests**

Run: `./gradlew :app:testDebugUnitTest --tests '*Comic*' --tests '*Reader*'` → Expected: PASS.
Run: `./gradlew :app:assembleDebug` → Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/com/komgareader/app/ui/reader
git commit -m "feat(comic): PanelGuideProvider selects ML vs geometric detection"
```

---

### Task 13: Delete the in-tree `guided-view` module

Nothing references `com.komgareader.guidedview.*` anymore (Task 11 + 12 moved every consumer to `com.panela.comiccutter.*`). Remove the module.

**Files:**
- Delete: `guided-view/` (whole directory)
- Modify: `settings.gradle.kts` (remove `include(":guided-view")`)
- Modify: `app/build.gradle.kts` (remove `implementation(project(":guided-view"))`)

- [ ] **Step 1: Verify no remaining references**

Run: `grep -rn 'com.komgareader.guidedview\|":guided-view"\|project(":guided-view")' --include=*.kt --include=*.kts .`
Expected: **no hits** (if any remain, they were missed in Task 11/12 — fix them first).

- [ ] **Step 2: Remove the module wiring + directory**

Remove the `:guided-view` line from `settings.gradle.kts`, the `project(":guided-view")` dependency from `app/build.gradle.kts`, then:
```bash
git rm -r guided-view
```

- [ ] **Step 3: Full build + tests**

Run: `./gradlew assembleDebug :app:testDebugUnitTest` → Expected: BUILD SUCCESSFUL, tests PASS.

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "refactor: remove in-tree guided-view module (replaced by comic-cutter)"
```

---

### Task 14: E2E verification on emulator

- [ ] **Step 1: Geometric path (no plugin installed)**

With no PANEL_MODEL plugin installed and `useMlDetection` ON, open a comic on `eink_test`. Confirm panel zoom works (falls back to geometric — provider returns `PanelGuide()` when `binaryDataPluginBytes` is null). Toggle `useMlDetection` OFF in Settings, reopen a comic, confirm still geometric. Capture a screenshot.

- [ ] **Step 2: ML path (PANEL_MODEL plugin installed)**

Install a PANEL_MODEL data-only APK (debug-signed, from the distribution repo / sample) declaring `DATA_CATEGORY=PANEL_MODEL`, `DATA_ASSET=<model>.onnx`, `ABI_VERSION=3`, `android:hasCode="false"`. With `useMlDetection` ON, open a comic; confirm the ML detector loads (no crash, panels detected) and the Plugins tab lists it under the "Panel model" filter. Capture a screenshot.

- [ ] **Step 3: Record evidence** in the PR (build green + both screenshots), per the project's "claim done only with shown proof" invariant.

---

### Task 15: Documentation sync

**REQUIRED SUB-SKILL:** Use the `komga-doc-sync` skill — this change adds a plugin category, swaps a seam (Naht B detector), removes a module, and adds a setting.

- [ ] **Step 1: Update the seam/architecture docs**

In the same commit as the code (`docs-match-code` rule): `CLAUDE.md` (module table — remove `guided-view`, note comic-cutter dependency), `.claude/rules/architecture-seams.md` (Naht B: detector is now the external `comic-cutter` lib; PANEL_MODEL category; ABI VERSION=3), `.claude/rules/roadmap-and-invariants.md` (guided-view detector → external lib + ML), `.claude/skills/komga-guided-comic-reader/SKILL.md` (pixel source + detector resolution now via `PanelGuideProvider`/comic-cutter).

- [ ] **Step 2: Update memory pointer** — `memory/guided-comic-reader.md` (and MEMORY.md hook) noting the comic-cutter swap + PANEL_MODEL plugin category.

- [ ] **Step 3: Commit**

```bash
git add -A
git commit -m "docs: sync architecture/seam docs to comic-cutter + PANEL_MODEL"
```

- [ ] **Step 4: Open PR for Phase 2**

```bash
git push -u origin feat/ml-panel-detector-p2-engine-swap
gh pr create --fill --base main
```

---

## Self-review notes

- **Spec coverage:** every slice of `bde5a504` is mapped — module swap (T10/11/13), PANEL_MODEL category + ABI (T1), binary discovery (T2), repo layer (T3), reader rewire (T11/12), settings (T4/9), Plugins UI (T7/8), i18n (T6), test stubs (T5/12). Docs (T15).
- **Naming locked:** `PANEL_MODEL` (category + PluginKind), `PANEL_MODELS` (filter), repo type string `"panel_model"`, setting key `use_ml_detection`, ABI `VERSION=3`. These names are used consistently across all tasks.
- **CI safety:** Phase 1 touches nothing external and keeps `guided-view`, so `main` stays buildable by anyone. The external dependency + module deletion are isolated in Phase 2, gated on a public coordinate (Task 10), never `mavenLocal()` for CI.
- **Open verifications deferred to build time (flagged, not guessed):** exact reachable comic-cutter coordinate/version (T10.1), `MlFilter`/`OnnxModelRunner`/`PageGuide.panels` signatures (T10.1, T12.1), KMP `-jvm` variant resolution from an Android module (T10.4), onnxruntime APK-size impact (T10.3).
