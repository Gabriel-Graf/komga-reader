# Plugin-Tab + Color-Preset-Plugin (Typ c, data-only) + Import-UI raus — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Farbfilter-Presets werden zu **data-only Plugin-APKs** (JSON-Asset, kein Code/TOFU/Multidex); der bestehende **Plugins-Tab** verwaltet alle installierten Plugin-APKs (Quellen + Presets) mit ⚙ Konfigurieren und 🗑 Deinstallieren; die bespoke Farbfilter-Import-UI entfällt; ein Beispiel-Preset-Plugin beweist die Kette.

**Architecture:** Host liest das JSON-Asset eines Fremd-APK via `createPackageContext(pkg, 0).assets` (Flags 0 = nur Ressourcen, **kein** Classloader) — damit entfallen Multidex/Signatur/TOFU der Code-Plugins vollständig; ABI-Gate greift trotzdem. Der Plugin-Quellen-Add-Flow (TOFU + Config-Form, Subsystem 1) wird aus den Settings in den Plugins-Tab verschoben (geteilte Composables, nicht dupliziert). `color_profiles` bekommt eine nullable Spalte `pluginPackage` (nicht-destruktive Migration). Uninstall liefert kein verlässliches Callback → Cleanup über Re-Scan beim Tab-`onResume` (rein-getesteter Prune-Planer).

**Tech Stack:** Kotlin, Jetpack Compose, Hilt, Room, `org.json` (Preset-Parsing), JUnit5 + MockK (Unit), AndroidJUnit4 (Migrations-/E2E-Instrumented).

**Invarianten (Pflicht, vor jedem Commit prüfen):**
- **Naht A:** Konkrete Plugin-/Quellen-Typen (`DiscoveredPlugin`, `PluginHost`, `SourceRegistration`) leben nur in `app`-Wiring + `plugin-host` — nie in `domain`. `ColorProfile` bleibt quellen-/geräteneutral (nur Zahlen + nullable `pluginPackage`-Tag).
- **E-Ink-Designsprache:** flach, 1.5px-Border (`EinkTokens.hairline`/`strongBorder`), Lucide via `AppIcons`, **keine Animation**, `EinkModal`/`EinkInfoDialog`. Sichtbarer Text **immer** über `LocalStrings` (DE+EN-Parität, echte Umlaute/ß).
- **Seeding-Gotcha (`komga-eink-color-filter`):** `seedColorProfiles` bleibt unberührt — neue Migration fügt nur die Spalte hinzu, keine neuen Built-ins. `@Database(version=…)` bumpen **und** Migration registrieren.

---

## File Structure

**`plugin-host`** (Asset-Discovery, data-only):
- Modify `plugin-host/src/main/kotlin/com/komgareader/plugin/host/PluginManifestKeys.kt` — `COLOR_PRESETS`-Key.
- Create `plugin-host/src/main/kotlin/com/komgareader/plugin/host/DiscoveredPresetPlugin.kt` — Datenklasse.
- Create `plugin-host/src/main/kotlin/com/komgareader/plugin/host/PresetSpecParser.kt` — pures `parsePresetSpecs(json, abi)`.
- Modify `plugin-host/src/main/kotlin/com/komgareader/plugin/host/PluginHost.kt` — `discoverColorPresetPlugins()`.
- Modify `plugin-host/build.gradle.kts` — `testImplementation("org.json:json:20231013")` (echtes org.json statt Android-Stub im Unit-Test).
- Create `plugin-host/src/test/kotlin/com/komgareader/plugin/host/PresetSpecParserTest.kt`.

**`domain`** (neutrales Tag + Repo-Vertrag):
- Modify `domain/src/main/kotlin/com/komgareader/domain/model/ColorProfile.kt` — `pluginPackage: String? = null`.
- Modify `domain/src/main/kotlin/com/komgareader/domain/repository/ColorProfileRepository.kt` — `deleteByPluginPackage(pkg)`.

**`data`** (Persistenz + Migration):
- Modify `data/src/main/kotlin/com/komgareader/data/db/Entities.kt` — `ColorProfileEntity.pluginPackage: String? = null`.
- Modify `data/src/main/kotlin/com/komgareader/data/db/ColorProfileDao.kt` — `deleteByPluginPackage`.
- Modify `data/src/main/kotlin/com/komgareader/data/db/AppDatabase.kt` — `version=16` + `MIGRATION_15_16`.
- Modify `data/src/main/kotlin/com/komgareader/data/di/DataModule.kt` — Migration registrieren.
- Modify `data/src/main/kotlin/com/komgareader/data/repository/RoomColorProfileRepository.kt` — Mapping + `deleteByPluginPackage`.
- Create `data/src/androidTest/kotlin/com/komgareader/data/db/Migration15To16Test.kt`.

**`app`** (Tab, Flow-Verschiebung, Import-UI raus):
- Create `app/src/main/kotlin/com/komgareader/app/ui/plugins/PluginPrune.kt` — purer Prune-Planer.
- Create `app/src/main/kotlin/com/komgareader/app/ui/plugins/PluginsViewModel.kt`.
- Modify `app/src/main/kotlin/com/komgareader/app/ui/plugins/PluginsScreen.kt` — echte UI.
- Create `app/src/main/kotlin/com/komgareader/app/ui/plugins/PluginSourceFlow.kt` — `PluginTofuModal`/`PluginConfigModal`/`PluginInfoRow` (aus `SettingsContent` extrahiert).
- Modify `app/src/main/kotlin/com/komgareader/app/ui/settings/SettingsContent.kt` — Plugin-Auswahl/Modals raus, ruft geteilten Flow nicht mehr.
- Modify `app/src/main/kotlin/com/komgareader/app/ui/settings/SettingsViewModel.kt` — Plugin-Members raus.
- Modify `app/src/main/kotlin/com/komgareader/app/ui/settings/ColorFilterViewModel.kt` — Import-Logik raus.
- Modify `app/src/main/kotlin/com/komgareader/app/ui/settings/ColorFilterSettingsContent.kt` — Import-UI raus.
- Modify `app/src/main/kotlin/com/komgareader/app/i18n/Strings.kt` — neue Keys, Import-Keys raus.
- Create `app/src/test/kotlin/com/komgareader/app/ui/plugins/PluginPruneTest.kt`.

**`plugin/komga-eink-preset-kindle/`** (Beispiel, eigenes Git-Repo, gitignored): Minimal-APK, reine Daten.

---

## Task 1: plugin-host — `COLOR_PRESETS`-Key + reiner `parsePresetSpecs`

**Files:**
- Modify: `plugin-host/build.gradle.kts`
- Modify: `plugin-host/src/main/kotlin/com/komgareader/plugin/host/PluginManifestKeys.kt`
- Create: `plugin-host/src/main/kotlin/com/komgareader/plugin/host/PresetSpecParser.kt`
- Test: `plugin-host/src/test/kotlin/com/komgareader/plugin/host/PresetSpecParserTest.kt`

- [ ] **Step 1: Echtes org.json auf den Unit-Test-Classpath legen**

Android-Unit-Tests stuben `org.json` (jede Methode wirft „not mocked"). Damit `parsePresetSpecs` rein auf der JVM testbar ist, das echte Artefakt als `testImplementation` ergänzen. In `plugin-host/build.gradle.kts` den `dependencies`-Block erweitern:

```kotlin
dependencies {
    api(project(":plugin-api"))
    implementation(project(":source-api"))
    implementation(project(":domain"))
    testImplementation(kotlin("test"))
    testImplementation(libs.junit.jupiter)
    testImplementation("org.json:json:20231013")
}
```

- [ ] **Step 2: `COLOR_PRESETS`-Manifest-Key ergänzen**

`PluginManifestKeys.kt` — der Schlüssel, unter dem ein Preset-Plugin den Asset-Dateinamen deklariert:

```kotlin
package com.komgareader.plugin.host

/** Manifest-Metadata-Schlüssel, die ein Plugin-APK deklariert. */
object PluginManifestKeys {
    const val ENTRY_CLASS = "com.komgareader.plugin.SOURCE"
    const val ABI_VERSION = "com.komgareader.plugin.ABI_VERSION"

    /**
     * Data-only Color-Preset-Plugin (Typ c): Wert = Asset-Dateiname (relativ zu `assets/`)
     * einer JSON-Liste von ColorPresetSpec. Anwesenheit dieses Keys → Preset-Plugin, nicht
     * Quellen-Plugin (das nennt [ENTRY_CLASS]). Der Host liest das Asset OHNE Plugin-Code.
     */
    const val COLOR_PRESETS = "com.komgareader.plugin.COLOR_PRESETS"
}
```

- [ ] **Step 3: Failing test schreiben**

`PresetSpecParserTest.kt`:

```kotlin
package com.komgareader.plugin.host

import com.komgareader.plugin.ColorPresetSpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PresetSpecParserTest {

    private val abi = 1

    @Test
    fun parsesArrayOfSpecs() {
        val json = """
            [
              {"abiVersion":1,"name":"Kindle Sepia","saturation":0.9,"contrast":1.1,"brightness":0.05},
              {"abiVersion":1,"name":"Kindle Mono","saturation":0.5,"contrast":1.2,"brightness":0.0}
            ]
        """.trimIndent()
        val specs = parsePresetSpecs(json, abi)
        assertEquals(
            listOf(
                ColorPresetSpec(1, "Kindle Sepia", 0.9f, 1.1f, 0.05f),
                ColorPresetSpec(1, "Kindle Mono", 0.5f, 1.2f, 0.0f),
            ),
            specs,
        )
    }

    @Test
    fun usesDiscoveredAbiWhenEntryOmitsIt() {
        // Ein Eintrag ohne eigenes abiVersion-Feld erbt die im Manifest deklarierte ABI.
        val specs = parsePresetSpecs("""[{"name":"X","saturation":1.0,"contrast":1.0,"brightness":0.0}]""", abi)
        assertEquals(1, specs!!.single().abiVersion)
    }

    @Test
    fun emptyArrayParsesToEmptyList() {
        assertEquals(emptyList(), parsePresetSpecs("[]", abi))
    }

    @Test
    fun malformedJsonReturnsNull() {
        assertNull(parsePresetSpecs("not json", abi))
        assertNull(parsePresetSpecs("""{"name":"obj-not-array"}""", abi))
    }

    @Test
    fun entryMissingRequiredFieldIsSkipped() {
        // Ein kaputter Eintrag (fehlendes name) wird übersprungen, gültige bleiben.
        val json = """[{"saturation":1.0,"contrast":1.0,"brightness":0.0},{"name":"Ok","saturation":1.0,"contrast":1.0,"brightness":0.0}]"""
        val specs = parsePresetSpecs(json, abi)
        assertTrue(specs!!.single().name == "Ok")
    }
}
```

- [ ] **Step 4: Run test to verify it fails**

Run: `./gradlew :plugin-host:testDebugUnitTest --tests "com.komgareader.plugin.host.PresetSpecParserTest"`
Expected: FAIL — `parsePresetSpecs` ist nicht definiert (unresolved reference).

- [ ] **Step 5: `parsePresetSpecs` implementieren**

`PresetSpecParser.kt`:

```kotlin
package com.komgareader.plugin.host

import com.komgareader.plugin.ColorPresetSpec
import org.json.JSONArray

/**
 * Parst ein JSON-Array von Color-Preset-Einträgen (data-only Plugin-Typ c) in [ColorPresetSpec].
 * Rein — kein Android, kein I/O, kein Classloader; das JSON kommt vom Host als bereits gelesener
 * String. Gibt null zurück, wenn der Top-Level kein Array ist oder das JSON kaputt ist.
 * Einzelne kaputte Einträge (fehlendes Pflichtfeld) werden übersprungen, gültige bleiben.
 *
 * Fehlt einem Eintrag das `abiVersion`-Feld, erbt er die im Manifest deklarierte [manifestAbi].
 * Wert-Validierung (Clamp, ABI-Range, Endlichkeit) macht NICHT dieser Parser, sondern
 * `ColorPresetImporter.toProfileOrNull` beim Import.
 */
fun parsePresetSpecs(json: String, manifestAbi: Int): List<ColorPresetSpec>? {
    val array = runCatching { JSONArray(json) }.getOrNull() ?: return null
    val specs = mutableListOf<ColorPresetSpec>()
    for (i in 0 until array.length()) {
        val obj = array.optJSONObject(i) ?: continue
        val spec = runCatching {
            ColorPresetSpec(
                abiVersion = if (obj.has("abiVersion")) obj.getInt("abiVersion") else manifestAbi,
                name = obj.getString("name"),
                saturation = obj.getDouble("saturation").toFloat(),
                contrast = obj.getDouble("contrast").toFloat(),
                brightness = obj.getDouble("brightness").toFloat(),
            )
        }.getOrNull() ?: continue
        specs.add(spec)
    }
    return specs
}
```

- [ ] **Step 6: Run test to verify it passes**

Run: `./gradlew :plugin-host:testDebugUnitTest --tests "com.komgareader.plugin.host.PresetSpecParserTest"`
Expected: PASS (5 Tests grün).

- [ ] **Step 7: Commit**

```bash
git add plugin-host/build.gradle.kts plugin-host/src/main/kotlin/com/komgareader/plugin/host/PluginManifestKeys.kt plugin-host/src/main/kotlin/com/komgareader/plugin/host/PresetSpecParser.kt plugin-host/src/test/kotlin/com/komgareader/plugin/host/PresetSpecParserTest.kt
git commit -m "feat(plugin-host): COLOR_PRESETS key + pure parsePresetSpecs (data-only preset plugins)"
```

---

## Task 2: plugin-host — `DiscoveredPresetPlugin` + `discoverColorPresetPlugins()`

Asset-Read aus dem Fremd-APK ohne Code (Flags 0). Nicht unit-testbar (braucht echten PackageManager) → Implementierung + Build-grün; verifiziert wird über das E2E in Task 11.

**Files:**
- Create: `plugin-host/src/main/kotlin/com/komgareader/plugin/host/DiscoveredPresetPlugin.kt`
- Modify: `plugin-host/src/main/kotlin/com/komgareader/plugin/host/PluginHost.kt`

- [ ] **Step 1: `DiscoveredPresetPlugin` anlegen**

`DiscoveredPresetPlugin.kt`:

```kotlin
package com.komgareader.plugin.host

import com.komgareader.plugin.ColorPresetSpec

/**
 * Ein installiertes, ABI-kompatibles **data-only** Color-Preset-Plugin (Typ c). Trägt die bereits
 * aus dem Asset geparsten Specs — es wird KEIN Plugin-Code geladen (kein Classloader, kein TOFU).
 */
data class DiscoveredPresetPlugin(
    val packageName: String,
    val displayName: String,
    val abiVersion: Int,
    val presets: List<ColorPresetSpec>,
)
```

- [ ] **Step 2: `discoverColorPresetPlugins()` in `PluginHost` ergänzen**

In `PluginHost.kt` nach `discoverPlugins()` (vor `sourceFor`) einfügen. Nutzt `readAbiVersion` (privat, bereits vorhanden) und `AbiGate`. `displayName` aus dem App-Label des Pakets (Preset-Plugins haben keine `PluginMetadata`):

```kotlin
    /**
     * Alle installierten, ABI-kompatiblen **data-only** Color-Preset-Plugins (Typ c). Liest pro Paket
     * das im Manifest unter [PluginManifestKeys.COLOR_PRESETS] deklarierte Asset über
     * `createPackageContext(pkg, 0)` — **Flags 0 = nur Ressourcen, KEIN Code**: kein PathClassLoader,
     * keine Signatur-Prüfung, kein Multidex-Thema. Es wird ausschließlich eine Datei gelesen und
     * geparst, niemals Plugin-Code ausgeführt. Paket-Sicht via app-Manifest-`QUERY_ALL_PACKAGES`.
     */
    fun discoverColorPresetPlugins(): List<DiscoveredPresetPlugin> {
        val pm = context.packageManager
        return pm.getInstalledPackages(PackageManager.GET_META_DATA).mapNotNull { pkg ->
            val meta = pkg.applicationInfo?.metaData ?: return@mapNotNull null
            val assetName = meta.getString(PluginManifestKeys.COLOR_PRESETS) ?: return@mapNotNull null
            val abi = readAbiVersion(meta) ?: return@mapNotNull null
            if (!AbiGate.isCompatible(abi)) return@mapNotNull null
            val json = runCatching {
                context.createPackageContext(pkg.packageName, 0)
                    .assets.open(assetName).bufferedReader().use { it.readText() }
            }.getOrNull() ?: return@mapNotNull null
            val specs = parsePresetSpecs(json, abi) ?: return@mapNotNull null
            val label = runCatching {
                pm.getApplicationLabel(pkg.applicationInfo!!).toString()
            }.getOrNull()?.ifBlank { null } ?: pkg.packageName
            DiscoveredPresetPlugin(pkg.packageName, label, abi, specs)
        }
    }
```

- [ ] **Step 3: Build verifizieren**

Run: `./gradlew :plugin-host:compileDebugKotlin`
Expected: BUILD SUCCESSFUL (keine unresolved references; `PackageManager`/`createPackageContext` sind bereits importiert bzw. via `context`).

- [ ] **Step 4: Commit**

```bash
git add plugin-host/src/main/kotlin/com/komgareader/plugin/host/DiscoveredPresetPlugin.kt plugin-host/src/main/kotlin/com/komgareader/plugin/host/PluginHost.kt
git commit -m "feat(plugin-host): discoverColorPresetPlugins reads JSON asset without loading code"
```

---

## Task 3: domain — `ColorProfile.pluginPackage` + Repo-Vertrag

**Files:**
- Modify: `domain/src/main/kotlin/com/komgareader/domain/model/ColorProfile.kt`
- Modify: `domain/src/main/kotlin/com/komgareader/domain/repository/ColorProfileRepository.kt`

- [ ] **Step 1: Feld am Modell ergänzen**

In `ColorProfile.kt` — neues nullable Feld **nach** `builtIn` (Default `null` hält alle bestehenden Konstruktoraufrufe gültig). KDoc-Zeile ergänzen und das Datenklassen-Feld:

KDoc (im `@param`-Block nach `@param builtIn`):
```kotlin
 * @param pluginPackage Paketname des Preset-Plugins, das dieses Profil geliefert hat (null = Built-in
 *   oder vom Nutzer angelegt). Getaggte Profile werden gesperrt behandelt und beim Uninstall des
 *   Plugins automatisch entfernt. Quellen-/geräteneutral: nur ein Paket-Tag, kein Quellen-Wissen.
```

Feld:
```kotlin
    val builtIn: Boolean,
    val pluginPackage: String? = null,
) {
```

`ColorProfile.OFF` bleibt unverändert (kein `pluginPackage` → null).

- [ ] **Step 2: Repo-Methode ergänzen**

In `ColorProfileRepository.kt` vor `setActive`:

```kotlin
    /**
     * Löscht ALLE Profile, die von [pkg] (einem deinstallierten Preset-Plugin) stammen.
     * Built-ins/Custom-Profile (pluginPackage = null) bleiben unberührt. Zeigt der aktive Pointer
     * danach ins Leere, fällt [observeActive] automatisch auf [ColorProfile.OFF] zurück.
     */
    suspend fun deleteByPluginPackage(pkg: String)
```

- [ ] **Step 3: Build verifizieren**

Run: `./gradlew :domain:compileKotlin`
Expected: BUILD SUCCESSFUL.

> Hinweis: `:data` (`RoomColorProfileRepository`) implementiert das Interface und compiliert jetzt NICHT mehr (fehlende `deleteByPluginPackage` + fehlendes Mapping). Das wird in Task 4 behoben — `:domain` allein baut grün.

- [ ] **Step 4: Commit**

```bash
git add domain/src/main/kotlin/com/komgareader/domain/model/ColorProfile.kt domain/src/main/kotlin/com/komgareader/domain/repository/ColorProfileRepository.kt
git commit -m "feat(domain): ColorProfile.pluginPackage tag + deleteByPluginPackage contract"
```

---

## Task 4: data — Entity + Dao + Migration 15→16 + Repo-Mapping

**Files:**
- Modify: `data/src/main/kotlin/com/komgareader/data/db/Entities.kt`
- Modify: `data/src/main/kotlin/com/komgareader/data/db/ColorProfileDao.kt`
- Modify: `data/src/main/kotlin/com/komgareader/data/db/AppDatabase.kt`
- Modify: `data/src/main/kotlin/com/komgareader/data/di/DataModule.kt`
- Modify: `data/src/main/kotlin/com/komgareader/data/repository/RoomColorProfileRepository.kt`
- Test: `data/src/androidTest/kotlin/com/komgareader/data/db/Migration15To16Test.kt`

- [ ] **Step 1: Entity-Spalte ergänzen**

In `Entities.kt`, `ColorProfileEntity` — neue **nullable** Spalte **ohne** `@ColumnInfo(defaultValue=…)` (passt exakt zum `ALTER ADD COLUMN … TEXT` ohne Default in Step 3):

```kotlin
    val ditherLevels: Int = 16,
    val builtIn: Boolean,
    val pluginPackage: String? = null,
)
```

- [ ] **Step 2: Dao-Query ergänzen**

In `ColorProfileDao.kt` nach `deleteCustom`:

```kotlin
    @Query("DELETE FROM color_profiles WHERE pluginPackage = :pkg")
    suspend fun deleteByPluginPackage(pkg: String)
```

- [ ] **Step 3: Migration + Version-Bump**

In `AppDatabase.kt` `version = 15` → `version = 16` ändern und nach `MIGRATION_14_15` die neue Migration ergänzen:

```kotlin
/**
 * v15 → v16: Spalte `pluginPackage` an `color_profiles` (Preset-Plugin-Herkunft, Typ c).
 * **Additiv & nicht-destruktiv** — nullable `ALTER ADD COLUMN` ohne Default, exakt passend zu
 * `ColorProfileEntity.pluginPackage: String? = null` (kein `@ColumnInfo(defaultValue=…)`), damit
 * Rooms Schema-Validierung sauber durchläuft und `fallbackToDestructiveMigration` NICHT greift.
 * Bestandsprofile (Built-ins/Custom) behalten NULL. `seedColorProfiles` bleibt unberührt.
 */
val MIGRATION_15_16 = object : Migration(15, 16) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `color_profiles` ADD COLUMN `pluginPackage` TEXT")
    }
}
```

- [ ] **Step 4: Migration in DI registrieren**

In `DataModule.kt`: Import `import com.komgareader.data.db.MIGRATION_15_16` (nach `MIGRATION_14_15`) und im `addMigrations(...)`-Aufruf `MIGRATION_15_16` ans Ende der Liste hängen:

```kotlin
                MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15,
                MIGRATION_15_16,
            )
```

- [ ] **Step 5: Repo-Mapping + Methode**

In `RoomColorProfileRepository.kt`: `deleteByPluginPackage` implementieren und beide Mapper um `pluginPackage` erweitern.

Methode (nach `delete`):
```kotlin
    override suspend fun deleteByPluginPackage(pkg: String) = dao.deleteByPluginPackage(pkg)
```

`toDomain()` — letzte Zeile ergänzen:
```kotlin
    ditherLevels = ditherLevels, builtIn = builtIn, pluginPackage = pluginPackage,
)
```

`toEntity()` — letzte Zeile ergänzen:
```kotlin
    ditherMode = ditherMode.name, ditherLevels = ditherLevels, builtIn = builtIn, pluginPackage = pluginPackage,
)
```

- [ ] **Step 6: Migrations-Instrumented-Test schreiben**

`Migration15To16Test.kt` (Muster aus `Migration14To15Test`: rohe v15-DB erzeugen, mit Room+Migration auf 16 öffnen, Bestand prüfen + neue Spalte NULL). Das volle v15-Schema ist nötig, weil Room beim Öffnen alle Tabellen validiert.

```kotlin
package com.komgareader.data.db

import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Beweist, dass MIGRATION_15_16 nicht-destruktiv ist: Eine v15-DB mit einem Bestands-Profil in
 * `color_profiles` wird über die echte Migration auf v16 geöffnet. Danach muss (a) das alte Profil
 * unversehrt vorhanden sein (kein Wipe) und (b) die neue Spalte `pluginPackage` mit NULL existieren.
 */
@RunWith(AndroidJUnit4::class)
class Migration15To16Test {

    private val dbName = "migration-15-16-test-${System.nanoTime()}.db"
    private val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Before fun deleteOldDb() { ctx.deleteDatabase(dbName) }
    @After fun cleanup() { ctx.deleteDatabase(dbName) }

    @Test
    fun migrate15To16_behaeltProfil_undFuegtPluginPackageSpalteHinzu() {
        createV15DatabaseWithColorProfile()

        val db = Room.databaseBuilder(ctx, AppDatabase::class.java, dbName)
            .addMigrations(MIGRATION_15_16)
            .allowMainThreadQueries()
            .build()

        val cursor = db.openHelper.readableDatabase.query(
            "SELECT `name`, `pluginPackage` FROM `color_profiles` WHERE `id` = 99",
        )
        assertTrue("Profil muss nach der Migration noch vorhanden sein", cursor.moveToFirst())
        assertEquals("Bestand", cursor.getString(0))
        assertTrue("pluginPackage muss NULL für Altbestand sein", cursor.isNull(1))
        cursor.close()
        db.close()
    }

    /**
     * Erzeugt eine vollständige v15-Datenbank (alle Tabellen exakt im v15-Schema) plus ein
     * Bestands-Profil. Room validiert beim Öffnen ALLE Tabellen — eine Teil-DB würde scheitern.
     */
    private fun createV15DatabaseWithColorProfile() {
        val config = SupportSQLiteOpenHelper.Configuration.builder(ctx)
            .name(dbName)
            .callback(object : SupportSQLiteOpenHelper.Callback(15) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL("CREATE TABLE IF NOT EXISTS `settings` (`key` TEXT NOT NULL, `value` TEXT NOT NULL, PRIMARY KEY(`key`))")
                    db.execSQL("CREATE TABLE IF NOT EXISTS `server` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `baseUrl` TEXT NOT NULL, `username` TEXT, `apiKeyCiphertext` TEXT, `apiKeyIv` TEXT, `passwordCiphertext` TEXT, `passwordIv` TEXT, `kind` TEXT NOT NULL DEFAULT 'KOMGA', `extrasCiphertext` TEXT, `extrasIv` TEXT)")
                    db.execSQL("CREATE TABLE IF NOT EXISTS `downloads` (`bookRemoteId` TEXT NOT NULL, `sourceId` INTEGER NOT NULL, `seriesRemoteId` TEXT NOT NULL, `title` TEXT NOT NULL, `format` TEXT NOT NULL, `localPath` TEXT NOT NULL, `totalPages` INTEGER NOT NULL, `seriesTitle` TEXT NOT NULL DEFAULT '', `seriesCoverUrl` TEXT, PRIMARY KEY(`bookRemoteId`))")
                    db.execSQL("CREATE TABLE IF NOT EXISTS `shelves` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `sources` TEXT NOT NULL, `defaultContentType` TEXT)")
                    db.execSQL("CREATE TABLE IF NOT EXISTS `series_overrides` (`sourceId` INTEGER NOT NULL, `seriesRemoteId` TEXT NOT NULL, `contentType` TEXT NOT NULL, PRIMARY KEY(`sourceId`, `seriesRemoteId`))")
                    db.execSQL("CREATE TABLE IF NOT EXISTS `read_progress` (`bookRemoteId` TEXT NOT NULL, `sourceId` INTEGER NOT NULL, `page` INTEGER NOT NULL, `completed` INTEGER NOT NULL, `totalPages` INTEGER NOT NULL, `dirty` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`bookRemoteId`))")
                    db.execSQL("CREATE TABLE IF NOT EXISTS `color_profiles` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `saturation` REAL NOT NULL, `contrast` REAL NOT NULL, `brightness` REAL NOT NULL, `blackPoint` REAL NOT NULL, `whitePoint` REAL NOT NULL, `gamma` REAL NOT NULL, `sharpenAmount` REAL NOT NULL, `sharpenRadius` INTEGER NOT NULL, `ditherMode` TEXT NOT NULL, `ditherLevels` INTEGER NOT NULL, `builtIn` INTEGER NOT NULL)")
                    db.execSQL("CREATE TABLE IF NOT EXISTS `novel_progress` (`sourceId` INTEGER NOT NULL, `bookId` TEXT NOT NULL, `anchor` TEXT NOT NULL, `fraction` REAL NOT NULL, `dirty` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`sourceId`, `bookId`))")
                    db.execSQL("CREATE TABLE IF NOT EXISTS `collections` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `kind` TEXT NOT NULL)")
                    db.execSQL("CREATE TABLE IF NOT EXISTS `collection_members` (`rowId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `collectionId` INTEGER NOT NULL, `sourceId` INTEGER NOT NULL, `remoteId` TEXT NOT NULL, `title` TEXT NOT NULL, `position` INTEGER NOT NULL)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_collection_members_collectionId` ON `collection_members` (`collectionId`)")
                    db.execSQL("CREATE TABLE IF NOT EXISTS `collection_sync_links` (`collectionId` INTEGER NOT NULL, `sourceId` INTEGER NOT NULL, `remoteCollectionId` TEXT, `status` TEXT NOT NULL, `dirty` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`collectionId`, `sourceId`))")
                    db.execSQL("INSERT INTO `color_profiles` (`id`,`name`,`saturation`,`contrast`,`brightness`,`blackPoint`,`whitePoint`,`gamma`,`sharpenAmount`,`sharpenRadius`,`ditherMode`,`ditherLevels`,`builtIn`) VALUES (99,'Bestand',1.0,1.0,0.0,0.0,1.0,1.0,0.0,1,'NONE',16,0)")
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()
        FrameworkSQLiteOpenHelperFactory().create(config).writableDatabase.close()
    }
}
```

- [ ] **Step 6b: Run migration test (Emulator/Boox läuft)**

Run: `./gradlew :data:connectedDebugAndroidTest --tests "com.komgareader.data.db.Migration15To16Test"`
Expected: PASS — Bestandsprofil überlebt, `pluginPackage` NULL.

- [ ] **Step 7: Volle data-Unit-Tests grün**

Run: `./gradlew :data:testDebugUnitTest`
Expected: PASS (Mapping-Erweiterung bricht nichts; `ColorPresetImporterTest` weiter grün).

- [ ] **Step 8: Commit**

```bash
git add data/src/main/kotlin/com/komgareader/data/db/Entities.kt data/src/main/kotlin/com/komgareader/data/db/ColorProfileDao.kt data/src/main/kotlin/com/komgareader/data/db/AppDatabase.kt data/src/main/kotlin/com/komgareader/data/di/DataModule.kt data/src/main/kotlin/com/komgareader/data/repository/RoomColorProfileRepository.kt data/src/androidTest/kotlin/com/komgareader/data/db/Migration15To16Test.kt
git commit -m "feat(data): color_profiles.pluginPackage column (migration 15->16) + deleteByPluginPackage"
```

---

## Task 5: app — purer Prune-Planer (TDD)

Beim Tab-Re-Scan: welche getaggten Preset-Pakete und welche Plugin-Quellen-Verbindungen sind nicht mehr installiert? Reine Funktion → unit-getestet (functional core).

**Files:**
- Create: `app/src/main/kotlin/com/komgareader/app/ui/plugins/PluginPrune.kt`
- Test: `app/src/test/kotlin/com/komgareader/app/ui/plugins/PluginPruneTest.kt`

- [ ] **Step 1: Failing test schreiben**

`PluginPruneTest.kt`:

```kotlin
package com.komgareader.app.ui.plugins

import com.komgareader.domain.model.SourceKind
import com.komgareader.domain.repository.ServerConfig
import kotlin.test.Test
import kotlin.test.assertEquals

class PluginPruneTest {

    private fun pluginConfig(id: Long, pkg: String) = ServerConfig(
        name = "P", baseUrl = "", kind = SourceKind.PLUGIN,
        extras = mapOf("__pkg" to pkg, "__entry" to "E", "__sig" to "S"), id = id,
    )

    @Test
    fun dropsTaggedPresetPackagesThatAreNoLongerInstalled() {
        val plan = planPluginPrune(
            installedPackages = setOf("com.keep"),
            taggedPresetPackages = listOf("com.keep", "com.gone"),
            pluginSourceConfigs = emptyList(),
        )
        assertEquals(listOf("com.gone"), plan.presetPackagesToDrop)
        assertEquals(emptyList(), plan.sourceConfigIdsToRemove)
    }

    @Test
    fun removesPluginSourceConfigsWhoseApkVanished() {
        val plan = planPluginPrune(
            installedPackages = setOf("com.live"),
            taggedPresetPackages = emptyList(),
            pluginSourceConfigs = listOf(pluginConfig(1, "com.live"), pluginConfig(2, "com.dead")),
        )
        assertEquals(emptyList(), plan.presetPackagesToDrop)
        assertEquals(listOf(2L), plan.sourceConfigIdsToRemove)
    }

    @Test
    fun keepsEverythingWhenAllInstalled() {
        val plan = planPluginPrune(
            installedPackages = setOf("a", "b"),
            taggedPresetPackages = listOf("a"),
            pluginSourceConfigs = listOf(pluginConfig(1, "b")),
        )
        assertEquals(emptyList(), plan.presetPackagesToDrop)
        assertEquals(emptyList(), plan.sourceConfigIdsToRemove)
    }

    @Test
    fun deduplicatesPresetPackages() {
        val plan = planPluginPrune(
            installedPackages = emptySet(),
            taggedPresetPackages = listOf("com.gone", "com.gone"),
            pluginSourceConfigs = emptyList(),
        )
        assertEquals(listOf("com.gone"), plan.presetPackagesToDrop)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.komgareader.app.ui.plugins.PluginPruneTest"`
Expected: FAIL — `planPluginPrune` / `PrunePlan` nicht definiert.

- [ ] **Step 3: Implementieren**

`PluginPrune.kt`:

```kotlin
package com.komgareader.app.ui.plugins

import com.komgareader.domain.model.SourceKind
import com.komgareader.domain.repository.ServerConfig

/** Was beim Re-Scan aufgeräumt werden muss, weil das zugehörige Plugin-APK deinstalliert wurde. */
data class PrunePlan(
    val presetPackagesToDrop: List<String>,
    val sourceConfigIdsToRemove: List<Long>,
)

/**
 * Reiner Cleanup-Planer für deinstallierte Plugin-APKs (Uninstall liefert kein verlässliches
 * Callback → Re-Scan beim Tab-`onResume`). [installedPackages] = aktuell installierte, ABI-
 * kompatible Plugin-Pakete (Quellen ∪ Presets). Alles, was in der DB getaggt/konfiguriert ist,
 * aber nicht mehr installiert, wird zum Aufräumen markiert.
 */
fun planPluginPrune(
    installedPackages: Set<String>,
    taggedPresetPackages: List<String>,
    pluginSourceConfigs: List<ServerConfig>,
): PrunePlan {
    val presetDrop = taggedPresetPackages.distinct().filter { it !in installedPackages }
    val sourceRemove = pluginSourceConfigs
        .filter { it.kind == SourceKind.PLUGIN }
        .filter { (it.extras["__pkg"] ?: return@filter false) !in installedPackages }
        .map { it.id }
    return PrunePlan(presetDrop, sourceRemove)
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.komgareader.app.ui.plugins.PluginPruneTest"`
Expected: PASS (4 Tests grün).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/komgareader/app/ui/plugins/PluginPrune.kt app/src/test/kotlin/com/komgareader/app/ui/plugins/PluginPruneTest.kt
git commit -m "feat(app): pure planPluginPrune for uninstalled-plugin cleanup"
```

---

## Task 6: app — Plugin-Quellen-Flow-Composables aus Settings extrahieren

Vor der zweiten Variante (Plugins-Tab) das Geteilte zentralisieren (`shared-structure-before-variants`): `PluginTofuModal`, `PluginConfigModal`, `PluginInfoRow` aus `SettingsContent.kt` in eine eigene Datei verschieben — verhaltenserhaltend, beide Aufrufer nutzen dieselben Composables.

**Files:**
- Create: `app/src/main/kotlin/com/komgareader/app/ui/plugins/PluginSourceFlow.kt`
- Modify: `app/src/main/kotlin/com/komgareader/app/ui/settings/SettingsContent.kt`

- [ ] **Step 1: Flow-Composables in neue Datei verschieben**

Schneide aus `SettingsContent.kt` die drei privaten Composables **`PluginTofuModal`**, **`PluginConfigModal`** und **`PluginInfoRow`** vollständig heraus (inkl. ihrer KDoc) und füge sie in `PluginSourceFlow.kt` ein — dort als **`internal`** (paketübergreifend nutzbar: `com.komgareader.app.ui.plugins`). Datei-Kopf:

```kotlin
package com.komgareader.app.ui.plugins

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.komgareader.app.i18n.LocalStrings
import com.komgareader.app.ui.components.EinkModal
import com.komgareader.app.ui.settings.PluginConfigForm
import com.komgareader.app.ui.settings.rememberPluginFormState
import com.komgareader.plugin.host.DiscoveredPlugin
```

Die drei Composables erhalten Sichtbarkeit `internal` statt `private`. Inhalt/Logik bleiben **unverändert** (verhaltenserhaltend). Sollten weitere Imports nötig sein (z.B. `androidx.compose.foundation.layout.padding`, `dp`), aus den Original-Importen von `SettingsContent.kt` übernehmen.

> `PluginConfigForm`/`rememberPluginFormState` bleiben in `app/ui/settings/PluginConfigForm.kt` (public) — der neue Flow importiert sie von dort. Nicht verschieben.

- [ ] **Step 2: In `SettingsContent.kt` die verschobenen Composables entfernen**

Lösche die nun in Schritt 1 verschobenen `PluginTofuModal`/`PluginConfigModal`/`PluginInfoRow`-Definitionen aus `SettingsContent.kt`. (Die `ConnectionModal.PluginTofu/PluginConfig`-Zweige werden in Task 8 entfernt — in diesem Task baut `SettingsContent` noch dagegen, deshalb hier die Composables als `internal` aus `ui.plugins` importieren, damit es kompiliert:)

Import in `SettingsContent.kt` ergänzen:
```kotlin
import com.komgareader.app.ui.plugins.PluginTofuModal
import com.komgareader.app.ui.plugins.PluginConfigModal
```

- [ ] **Step 3: Build verifizieren**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL — reiner Move, keine Verhaltensänderung.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/komgareader/app/ui/plugins/PluginSourceFlow.kt app/src/main/kotlin/com/komgareader/app/ui/settings/SettingsContent.kt
git commit -m "refactor(app): extract plugin-source TOFU/config modals to shared ui.plugins.PluginSourceFlow"
```

---

## Task 7: app — `PluginsViewModel`

Beherbergt Discovery (Quellen + Presets), den verschobenen Source-Add (`addPluginSource`), Preset-Import/-Entfernen und den Re-Scan-Prune. Verschiebt diese Verantwortung aus `SettingsViewModel`.

**Files:**
- Create: `app/src/main/kotlin/com/komgareader/app/ui/plugins/PluginsViewModel.kt`

- [ ] **Step 1: ViewModel schreiben**

```kotlin
package com.komgareader.app.ui.plugins

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.komgareader.app.data.CollectionSyncManager
import com.komgareader.app.data.SourceRegistration
import com.komgareader.data.plugin.ColorPresetImporter
import com.komgareader.domain.model.ColorProfile
import com.komgareader.domain.model.SourceKind
import com.komgareader.domain.repository.CollectionRepository
import com.komgareader.domain.repository.ColorProfileRepository
import com.komgareader.domain.repository.ServerConfig
import com.komgareader.domain.repository.ServerRepository
import com.komgareader.plugin.ColorPresetSpec
import com.komgareader.plugin.host.DiscoveredPlugin
import com.komgareader.plugin.host.DiscoveredPresetPlugin
import com.komgareader.plugin.host.PluginHost
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PluginsViewModel @Inject constructor(
    private val pluginHost: PluginHost,
    private val servers: ServerRepository,
    private val registration: SourceRegistration,
    private val collections: CollectionRepository,
    private val colorProfiles: ColorProfileRepository,
    private val sync: CollectionSyncManager,
) : ViewModel() {

    private val _sources = MutableStateFlow<List<DiscoveredPlugin>>(emptyList())
    val sources: StateFlow<List<DiscoveredPlugin>> = _sources.asStateFlow()

    private val _presetPlugins = MutableStateFlow<List<DiscoveredPresetPlugin>>(emptyList())
    val presetPlugins: StateFlow<List<DiscoveredPresetPlugin>> = _presetPlugins.asStateFlow()

    /** Alle Profile (für „bereits importiert?"-Abgleich pro Preset-Plugin nach (pkg, name)). */
    val profiles = colorProfiles.observeAll()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /**
     * Re-Scan beim Tab-Öffnen/`onResume`: Quellen + Presets neu entdecken, danach aufräumen,
     * was zu deinstallierten Plugin-APKs gehört (Uninstall hat kein verlässliches Callback).
     */
    fun refresh() = viewModelScope.launch {
        val srcs = runCatching { pluginHost.discoverPlugins() }.getOrDefault(emptyList())
        val presets = runCatching { pluginHost.discoverColorPresetPlugins() }.getOrDefault(emptyList())
        _sources.value = srcs
        _presetPlugins.value = presets

        val installed = (srcs.map { it.packageName } + presets.map { it.packageName }).toSet()
        val taggedPkgs = profiles.value.mapNotNull { it.pluginPackage }
        val pluginConfigs = servers.configs.first().filter { it.kind == SourceKind.PLUGIN }
        val plan = planPluginPrune(installed, taggedPkgs, pluginConfigs)
        plan.presetPackagesToDrop.forEach { colorProfiles.deleteByPluginPackage(it) }
        plan.sourceConfigIdsToRemove.forEach { id ->
            val cfg = pluginConfigs.firstOrNull { it.id == id }
            servers.remove(id)
            cfg?.let { registration.sourceIdOf(it) }?.let { collections.removeSource(it) }
        }
    }.let {}

    /**
     * Persistiert eine bestätigte Plugin-Quelle als [ServerConfig] (kind = PLUGIN). Extras tragen
     * neben den Nutzerwerten die reservierten Wiring-Keys `__pkg`/`__entry`/`__sig` (TOFU-Pin).
     * Verschoben aus SettingsViewModel — der Add-Flow lebt jetzt im Plugin-Tab.
     */
    fun addPluginSource(plugin: DiscoveredPlugin, values: Map<String, String>) = viewModelScope.launch {
        servers.save(
            ServerConfig(
                name = plugin.metadata.displayName,
                baseUrl = values["url"]?.trim() ?: "",
                kind = SourceKind.PLUGIN,
                extras = values + mapOf(
                    "__pkg" to plugin.packageName,
                    "__entry" to plugin.entryClass,
                    "__sig" to plugin.signatureSha256,
                ),
            )
        )
        runCatching { sync.pullOnlySync() }
    }.let {}

    /** True, wenn aus [pkg] bereits ein Profil mit [name] importiert wurde (Abgleich nach Tag+Name). */
    fun isImported(pkg: String, name: String): Boolean =
        profiles.value.any { it.pluginPackage == pkg && it.name == name }

    /**
     * Importiert ein Preset eines Plugins als getaggtes, gesperrtes Profil (builtIn bleibt false,
     * aber `pluginPackage` markiert es als Plugin-eigen → in der UI nicht editierbar). Inkompatible/
     * fehlerhafte Specs werden still verworfen (ABI-/Wert-Validierung in ColorPresetImporter).
     */
    fun importPreset(pkg: String, spec: ColorPresetSpec) = viewModelScope.launch {
        val profile = ColorPresetImporter.toProfileOrNull(spec) ?: return@launch
        colorProfiles.upsert(profile.copy(pluginPackage = pkg))
    }.let {}

    /** Entfernt ein einzeln importiertes Plugin-Profil wieder (per id). */
    fun removeImportedProfile(profile: ColorProfile) = viewModelScope.launch {
        colorProfiles.delete(profile.id)
    }.let {}
}
```

> Hinweis Naht/Importer: `RoomColorProfileRepository.upsert` verlangt `!builtIn` — Plugin-Profile sind `builtIn = false` (✓). Die „Sperre" (kein Editieren) ist eine reine UI-Eigenschaft über `pluginPackage != null`.

- [ ] **Step 2: Build verifizieren**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/komgareader/app/ui/plugins/PluginsViewModel.kt
git commit -m "feat(app): PluginsViewModel (discover sources+presets, add source, import preset, prune)"
```

---

## Task 8: app — `SettingsViewModel` + `SettingsContent` von Plugins befreien

„Server hinzufügen" bietet nur noch Komga/OPDS; der Plugin-Flow lebt im Tab.

**Files:**
- Modify: `app/src/main/kotlin/com/komgareader/app/ui/settings/SettingsViewModel.kt`
- Modify: `app/src/main/kotlin/com/komgareader/app/ui/settings/SettingsContent.kt`

- [ ] **Step 1: Plugin-Members aus `SettingsViewModel` entfernen**

Lösche: den `pluginHost: PluginHost`-Konstruktorparameter, `_discoveredPlugins`/`discoveredPlugins`, `refreshDiscoveredPlugins()`, `addPluginSource(...)` und die Imports `com.komgareader.plugin.host.DiscoveredPlugin` + `com.komgareader.plugin.host.PluginHost`. `removeServer` (nutzt `registration`/`collections`) **bleibt** — es räumt auch Plugin-Quellen auf, die vom Settings-Server-Listen-Pfad entfernt werden.

- [ ] **Step 2: `SettingsContent` — Plugin-Auswahl + Plugin-Modal-Zweige entfernen**

In `ConnectionSettingsContent`:
- Entferne `val discoveredPlugins by viewModel.discoveredPlugins.collectAsState()`.
- Im `+`-`onClick` (Zeile ~110) den `viewModel.refreshDiscoveredPlugins()`-Aufruf entfernen; nur `modal = ConnectionModal.Add` bleibt.
- Im `when (val mode = modal)`: die Zweige `is ConnectionModal.PluginTofu` und `is ConnectionModal.PluginConfig` **löschen**.
- `AddConnectionModal`-Aufruf: Parameter `discoveredPlugins = …` und `onPickPlugin = …` entfernen.

In `ConnectionModal` (sealed interface): die Fälle `PluginTofu` und `PluginConfig` **löschen**.

In `AddConnectionModal`: die Parameter `discoveredPlugins: List<DiscoveredPlugin>` und `onPickPlugin: (DiscoveredPlugin) -> Unit` aus der Signatur entfernen **und** den ganzen `if (discoveredPlugins.isNotEmpty()) { … }`-Block (die Plugin-Auswahlliste, Zeile ~276–288) löschen.

Entferne nun ungenutzte Imports in `SettingsContent.kt`: `com.komgareader.plugin.host.DiscoveredPlugin`, `com.komgareader.app.ui.plugins.PluginTofuModal`, `com.komgareader.app.ui.plugins.PluginConfigModal` (aus Task 6 Step 2 — werden hier wieder entfernt, da Settings sie nicht mehr aufruft).

- [ ] **Step 3: Build + bestehende Settings-Unit-Tests verifizieren**

Run: `./gradlew :app:compileDebugKotlin && ./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL; bestehende Tests grün (kein Test prüft die entfernten Plugin-Members; falls doch ein Test `addPluginSource`/`discoveredPlugins` auf `SettingsViewModel` referenziert, ihn auf `PluginsViewModel` umziehen).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/komgareader/app/ui/settings/SettingsViewModel.kt app/src/main/kotlin/com/komgareader/app/ui/settings/SettingsContent.kt
git commit -m "refactor(app): move plugin-source add-flow out of Settings (Komga/OPDS only)"
```

---

## Task 9: app — i18n-Keys (neu + Import-Keys raus)

**Files:**
- Modify: `app/src/main/kotlin/com/komgareader/app/i18n/Strings.kt`

- [ ] **Step 1: Neue Keys im Interface deklarieren + Import-Keys entfernen**

Im `interface Strings`:
- **Entfernen:** `val pluginsComingSoon`, `val colorFilterImportPreset`, `val colorFilterImportError`.
- **Hinzufügen** (bei den übrigen Plugin-Keys, nach `pluginTrustConfirm`):

```kotlin
    val pluginTabSourceLabel: String      // Typ-Label „Quelle"
    val pluginTabPresetLabel: String      // Typ-Label „Farbprofile"
    val pluginTabEmpty: String            // Liste leer
    val pluginTabReposHint: String        // „+" / Repo-Browser folgt (P2)
    val pluginConfigure: String           // ⚙ contentDescription
    val pluginUninstall: String           // 🗑 contentDescription
    val pluginPresetsTitle: String        // Titel des Preset-Detail-Modals
    val pluginPresetImport: String        // „Importieren"
    val pluginPresetRemove: String        // „Entfernen"
    val pluginPresetImported: String      // Status „Importiert"
```

- [ ] **Step 2: DE-Implementierung (`object De`) anpassen**

- **Entfernen:** `override val pluginsComingSoon`, `override val colorFilterImportPreset`, `override val colorFilterImportError`.
- **Hinzufügen:**

```kotlin
    override val pluginTabSourceLabel = "Quelle"
    override val pluginTabPresetLabel = "Farbprofile"
    override val pluginTabEmpty = "Keine Plugins installiert."
    override val pluginTabReposHint = "Plugin-Repositories zum Suchen und Installieren folgen in einem späteren Update."
    override val pluginConfigure = "Konfigurieren"
    override val pluginUninstall = "Entfernen"
    override val pluginPresetsTitle = "Farbprofile"
    override val pluginPresetImport = "Importieren"
    override val pluginPresetRemove = "Entfernen"
    override val pluginPresetImported = "Importiert"
```

- [ ] **Step 3: EN-Implementierung (`object En`) anpassen**

- **Entfernen:** die drei `override`s wie in DE.
- **Hinzufügen:**

```kotlin
    override val pluginTabSourceLabel = "Source"
    override val pluginTabPresetLabel = "Color profiles"
    override val pluginTabEmpty = "No plugins installed."
    override val pluginTabReposHint = "Plugin repositories for searching and installing are coming in a later update."
    override val pluginConfigure = "Configure"
    override val pluginUninstall = "Remove"
    override val pluginPresetsTitle = "Color profiles"
    override val pluginPresetImport = "Import"
    override val pluginPresetRemove = "Remove"
    override val pluginPresetImported = "Imported"
```

- [ ] **Step 4: Build verifizieren (Compile-Zeit-Parität)**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL — beide `object`s implementieren das Interface vollständig (fehlende/zuviele Keys = Compile-Fehler).

> `pluginsComingSoon`/`colorFilterImportPreset`/`colorFilterImportError` werden jetzt von keiner Datei mehr referenziert (PluginsScreen wird in Task 10 neu, ColorFilter-Import in Task 11 entfernt). Falls dieser Schritt vor Task 10/11 ausgeführt wird, kommt es zu „unresolved reference" in `PluginsScreen.kt`/`ColorFilterSettingsContent.kt` — die Reihenfolge ist Task 9 → 10 → 11 in EINEM zusammenhängenden Lauf; committe Task 9 erst nach grünem `:app:compileDebugKotlin` am Ende von Task 11, ODER führe Task 10+11 unmittelbar danach aus. Für saubere Commits: Task 9 zuletzt mergen ist nicht nötig — der Build muss nur am Ende der Sequenz grün sein.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/komgareader/app/i18n/Strings.kt
git commit -m "i18n: plugin-tab strings; drop color-filter import-UI keys"
```

---

## Task 10: app — `PluginsScreen` (echte UI)

Liste installierter Plugins (Quellen + Presets), je Zeile ⚙ + 🗑; Quellen-⚙ startet den geteilten TOFU→Config-Flow; Preset-⚙ öffnet ein Preset-Detail-Modal (Import/Entfernen); 🗑 deinstalliert via OS-Intent; Re-Scan beim `onResume`.

**Files:**
- Modify: `app/src/main/kotlin/com/komgareader/app/ui/plugins/PluginsScreen.kt`

- [ ] **Step 1: PluginsScreen neu schreiben**

```kotlin
package com.komgareader.app.ui.plugins

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.runtime.DisposableEffect
import com.komgareader.app.i18n.LocalStrings
import com.komgareader.app.ui.components.EinkInfoDialog
import com.komgareader.app.ui.icons.AppIcons
import com.komgareader.app.ui.theme.EinkTokens
import com.komgareader.plugin.ColorPresetSpec
import com.komgareader.plugin.host.DiscoveredPlugin
import com.komgareader.plugin.host.DiscoveredPresetPlugin

/**
 * Plugins-Tab (`HomeScreen` TAB_PLUGINS, content-only — TopBar liefert HomeScreen). Listet INSTALLIERTE
 * Plugin-APKs (Quellen + data-only Color-Presets). E-Ink: flach, 1.5px-Border, Lucide via AppIcons,
 * keine Animation. ⚙ konfiguriert (Quelle: TOFU→Config-Flow; Preset: Import-Detail), 🗑 deinstalliert
 * via OS-Intent. Cleanup deinstallierter Plugins läuft über Re-Scan beim `onResume` (kein Intent-Callback).
 */
@Composable
fun PluginsScreen(modifier: Modifier = Modifier, viewModel: PluginsViewModel = hiltViewModel()) {
    val s = LocalStrings.current
    val ctx = LocalContext.current
    val sources by viewModel.sources.collectAsState()
    val presets by viewModel.presetPlugins.collectAsState()

    // Re-Scan beim Tab-Sichtbarwerden (ON_RESUME) — entdeckt Neuinstallationen + prunt Deinstalliertes.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Flow-Zustand: ausgewählte Quelle (TOFU→Config) bzw. Preset-Plugin (Detail). Genau eines offen.
    var tofuFor by remember { mutableStateOf<DiscoveredPlugin?>(null) }
    var configFor by remember { mutableStateOf<DiscoveredPlugin?>(null) }
    var presetDetailFor by remember { mutableStateOf<DiscoveredPresetPlugin?>(null) }

    fun uninstall(pkg: String) {
        ctx.startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkg")))
    }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (sources.isEmpty() && presets.isEmpty()) {
            Text(
                s.pluginTabEmpty,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        sources.forEach { src ->
            PluginRow(
                title = src.metadata.displayName,
                typeLabel = s.pluginTabSourceLabel,
                abiVersion = src.abiVersion,
                configureLabel = s.pluginConfigure,
                uninstallLabel = s.pluginUninstall,
                onConfigure = { tofuFor = src },
                onUninstall = { uninstall(src.packageName) },
            )
        }
        presets.forEach { p ->
            PluginRow(
                title = p.displayName,
                typeLabel = s.pluginTabPresetLabel,
                abiVersion = p.abiVersion,
                configureLabel = s.pluginConfigure,
                uninstallLabel = s.pluginUninstall,
                onConfigure = { presetDetailFor = p },
                onUninstall = { uninstall(p.packageName) },
            )
        }
        // P2-Hinweis (Repo-Browser + Suche + Install folgt) — kein Repo-Code in P1.
        Text(
            s.pluginTabReposHint,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp),
        )
    }

    // Quellen-Flow: TOFU-Bestätigung → Config-Form → addPluginSource. Genau ein Modal gleichzeitig.
    tofuFor?.let { plugin ->
        PluginTofuModal(
            plugin = plugin,
            onDismiss = { tofuFor = null },
            onConfirm = { tofuFor = null; configFor = plugin },
        )
    }
    configFor?.let { plugin ->
        PluginConfigModal(
            plugin = plugin,
            onDismiss = { configFor = null },
            onSubmit = { values -> viewModel.addPluginSource(plugin, values); configFor = null },
        )
    }

    // Preset-Detail: Presets dieses Plugins importieren/entfernen (gesperrt = read-only, wie Built-in).
    presetDetailFor?.let { p ->
        EinkInfoDialog(title = s.pluginPresetsTitle, onDismiss = { presetDetailFor = null }, closeLabel = s.close) {
            p.presets.forEach { spec ->
                PresetImportRow(
                    spec = spec,
                    imported = viewModel.isImported(p.packageName, spec.name),
                    importLabel = s.pluginPresetImport,
                    removeLabel = s.pluginPresetRemove,
                    importedLabel = s.pluginPresetImported,
                    onImport = { viewModel.importPreset(p.packageName, spec) },
                    onRemove = {
                        viewModel.profiles.value
                            .firstOrNull { it.pluginPackage == p.packageName && it.name == spec.name }
                            ?.let { viewModel.removeImportedProfile(it) }
                    },
                )
            }
        }
    }
}

/** Eine Plugin-Zeile: Name + Typ-Label + ABI links, ⚙ + 🗑 rechts. Flach, 1.5px-Border, keine Animation. */
@Composable
private fun PluginRow(
    title: String,
    typeLabel: String,
    abiVersion: Int,
    configureLabel: String,
    uninstallLabel: String,
    onConfigure: () -> Unit,
    onUninstall: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(EinkTokens.hairline, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                "$typeLabel · ABI $abiVersion",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onConfigure) {
            Icon(AppIcons.Settings, contentDescription = configureLabel, modifier = Modifier.size(22.dp))
        }
        IconButton(onClick = onUninstall) {
            Icon(AppIcons.Delete, contentDescription = uninstallLabel, modifier = Modifier.size(22.dp))
        }
    }
}

/** Ein Preset im Detail-Modal: Name links, Import-/Entfernen-Aktion rechts. */
@Composable
private fun PresetImportRow(
    spec: ColorPresetSpec,
    imported: Boolean,
    importLabel: String,
    removeLabel: String,
    importedLabel: String,
    onImport: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(spec.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        if (imported) {
            Text(
                importedLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 8.dp),
            )
            IconButton(onClick = onRemove) {
                Icon(AppIcons.Delete, contentDescription = removeLabel, modifier = Modifier.size(20.dp))
            }
        } else {
            IconButton(onClick = onImport) {
                Icon(AppIcons.Download, contentDescription = importLabel, modifier = Modifier.size(20.dp))
            }
        }
    }
}
```

> `PluginTofuModal`/`PluginConfigModal` sind die in Task 6 nach `ui.plugins` extrahierten `internal`-Composables (gleiches Paket → kein Import nötig). `s.close` existiert bereits.

- [ ] **Step 2: Build verifizieren**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL. (`PluginsScreen()` wird in `HomeScreen` ohne Argumente aufgerufen — der `viewModel`-Default via `hiltViewModel()` deckt das ab; `HomeScreen`-Aufrufstelle bleibt unverändert.)

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/komgareader/app/ui/plugins/PluginsScreen.kt
git commit -m "feat(app): real Plugins tab — list installed source/preset plugins, configure, uninstall"
```

---

## Task 11: app — bespoke Farbfilter-Import-UI entfernen

**Files:**
- Modify: `app/src/main/kotlin/com/komgareader/app/ui/settings/ColorFilterViewModel.kt`
- Modify: `app/src/main/kotlin/com/komgareader/app/ui/settings/ColorFilterSettingsContent.kt`

- [ ] **Step 1: `ColorFilterViewModel` — Import-Logik raus**

Entferne: `_importError`/`importError`/`dismissImportError`, die ganze `importPresetJson(json)`-Funktion und die nun ungenutzten Imports `com.komgareader.data.plugin.ColorPresetImporter` und `com.komgareader.plugin.ColorPresetSpec`. Der Rest des ViewModels (Profile/Editor/Preview) bleibt unverändert.

- [ ] **Step 2: `ColorFilterSettingsContent` — Import-UI raus**

Entferne:
- `val importError by viewModel.importError.collectAsState()` (Zeile ~90).
- den `importLauncher`-Block (`rememberLauncherForActivityResult(OpenDocument())`, Zeile ~100–108).
- den „Preset importieren"-`EinkOutlinedButton`-Block am Ende von `SettingsGroup(s.colorFilterProfiles, …)` (Zeile ~241–247).
- den Import-Fehler-Dialog `if (importError) { EinkInfoDialog(… s.colorFilterImportError …) }` (Zeile ~377–381).
- nun ungenutzte Imports: `androidx.activity.compose.rememberLauncherForActivityResult`, `androidx.activity.result.contract.ActivityResultContracts`, `com.komgareader.app.ui.components.EinkOutlinedButton` (nur falls nirgends sonst in der Datei verwendet — prüfen; wird in der Editor-Aktionszeile noch genutzt → **behalten**). `LocalContext`/`ctx` wird weiterhin für das Preview-`ImageRequest.Builder(ctx)` gebraucht → **behalten**.

> Achtung: `EinkOutlinedButton` und `ctx`/`LocalContext` bleiben (anderweitig genutzt). Nur `rememberLauncherForActivityResult` + `ActivityResultContracts` werden ungenutzt → entfernen.

- [ ] **Step 3: Build verifizieren**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL — keine Referenz mehr auf `colorFilterImportPreset`/`colorFilterImportError`/`importPresetJson`.

- [ ] **Step 4: Volle app-Unit-Tests + Lint-Gate**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/komgareader/app/ui/settings/ColorFilterViewModel.kt app/src/main/kotlin/com/komgareader/app/ui/settings/ColorFilterSettingsContent.kt
git commit -m "refactor(app): remove bespoke color-filter JSON import UI (presets come from plugins now)"
```

---

## Task 12: Beispiel-Preset-Plugin (`plugin/komga-eink-preset-kindle/`)

Eigenes Git-Repo unter `plugin/` (gitignored). Minimal-APK, **reine Daten** — Manifest + Asset, **kein** Code, **keine** Abhängigkeit (auch nicht `plugin-sdk`).

**Files (im separaten Repo `plugin/komga-eink-preset-kindle/`):**
- Create: `settings.gradle.kts`, `build.gradle.kts`, `gradle/` (Wrapper kopiert), `app/build.gradle.kts`
- Create: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/assets/color_presets.json`

- [ ] **Step 1: Repo + Gradle-Gerüst**

```bash
mkdir -p plugin/komga-eink-preset-kindle/app/src/main/assets
cd plugin/komga-eink-preset-kindle && git init
cp -r ../komga-kavita-source/gradle ./gradle 2>/dev/null || cp -r ../../gradle ./gradle
cp ../../gradlew ../../gradlew.bat ./ 2>/dev/null || true
```

> Falls kein vorhandenes Plugin-Repo zum Kopieren da ist: `gradle wrapper` im neuen Repo ausführen.

`settings.gradle.kts`:
```kotlin
pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositories { google(); mavenCentral() }
}
rootProject.name = "komga-eink-preset-kindle"
include(":app")
```

Root `build.gradle.kts`:
```kotlin
plugins {
    id("com.android.application") version "8.5.2" apply false
}
```

`app/build.gradle.kts` (data-only — keine Kotlin-/Compose-/SDK-Abhängigkeit nötig):
```kotlin
plugins { id("com.android.application") }

android {
    namespace = "com.komgareader.preset.kindle"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.komgareader.preset.kindle"
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
```

- [ ] **Step 2: Manifest mit Metadata-Keys**

`app/src/main/AndroidManifest.xml` — deklariert das Asset + ABI; **keine** Activity, **kein** Code:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application android:label="Kindle E-Ink Presets" android:hasCode="false">
        <meta-data
            android:name="com.komgareader.plugin.COLOR_PRESETS"
            android:value="color_presets.json" />
        <meta-data
            android:name="com.komgareader.plugin.ABI_VERSION"
            android:value="1" />
    </application>
</manifest>
```

> `android:hasCode="false"` — data-only-APK, kein DEX. Der Host liest nur das Asset (Flags 0).

- [ ] **Step 3: Preset-Asset**

`app/src/main/assets/color_presets.json` (zwei Presets für ein anderes E-Ink — gedämpftere Sättigung als Go-7):

```json
[
  { "abiVersion": 1, "name": "Kindle Warm", "saturation": 0.9, "contrast": 1.1, "brightness": 0.05 },
  { "abiVersion": 1, "name": "Kindle Mono", "saturation": 0.5, "contrast": 1.2, "brightness": 0.0 }
]
```

- [ ] **Step 4: Debug-APK bauen + installieren**

Run (im Plugin-Repo): `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL → `app/build/outputs/apk/debug/app-debug.apk`.

Run: `adb install -r plugin/komga-eink-preset-kindle/app/build/outputs/apk/debug/app-debug.apk`
Expected: `Success`.

- [ ] **Step 5: Commit (im Plugin-Repo)**

```bash
cd plugin/komga-eink-preset-kindle && git add -A && git commit -m "feat: data-only Kindle E-Ink color-preset plugin (ABI 1)"
```

> Das Haupt-Repo committet hier **nichts** (`plugin/` ist gitignored).

---

## Task 13: E2E auf dem Emulator/Boox

Beweist die ganze Kette manuell (Compose-UI; kein automatisierter Instrumented-Test für den Tab in P1).

- [ ] **Step 1: App bauen + installieren, Beispiel-APK installiert**

Run: `./gradlew :app:installDebug`
Sicherstellen: Beispiel-Preset-APK aus Task 12 ist installiert (`adb shell pm list packages | grep preset.kindle`).

- [ ] **Step 2: Plugin-Tab zeigt das Preset-Plugin**

App öffnen → Tab „Plugins". Erwartet: Zeile „Kindle E-Ink Presets · Farbprofile · ABI 1" mit ⚙ und 🗑. Screenshot.

- [ ] **Step 3: Presets importieren**

⚙ auf der Preset-Zeile → Modal „Farbprofile" listet „Kindle Warm" + „Kindle Mono" mit Import-Icon. „Kindle Warm" importieren → Status wechselt auf „Importiert". Modal schließen.

- [ ] **Step 4: Profil im Farbfilter sichtbar + aktivierbar + Bild gefiltert**

Settings → Farbfilter → Profil-Dropdown enthält „Kindle Warm". Auswählen → Vorschau-Cover wird sichtbar wärmer/entsättigt gefiltert. Screenshot.

- [ ] **Step 5: Uninstall → Cascade-Prune**

Zurück zum Plugin-Tab → 🗑 auf der Preset-Zeile → OS-Deinstallations-Dialog bestätigen → zurück in die App (Tab wird `ON_RESUME` → `refresh()`). Erwartet: Zeile verschwindet. Settings → Farbfilter: „Kindle Warm" ist weg (Re-Scan-Prune via `deleteByPluginPackage`); war es aktiv, steht der Filter jetzt auf „Aus" (Fallback `OFF`). Screenshot.

- [ ] **Step 6: Settings „Server hinzufügen" zeigt keine Plugin-Auswahl mehr**

Settings → Verbindungen → „+": nur Komga/OPDS-Formular, keine Plugin-Liste. Screenshot.

- [ ] **Step 7: Voller Build + alle Unit-Tests grün**

Run: `./gradlew :plugin-host:testDebugUnitTest :domain:test :data:testDebugUnitTest :app:testDebugUnitTest`
Expected: PASS überall.

---

## Task 14: Memory + docs-match-code nachziehen

- [ ] **Step 1: Memory aktualisieren**

In `plugin-host-kavita.md` den „Subsystem 2 (Slice P1)"-Block von „in Arbeit" auf „gebaut/E2E-grün" umschreiben (was real ist: data-only Preset-Discovery, Plugin-Tab, Import-UI raus, Beispiel-Plugin, Migration 15→16). `MEMORY.md`-Pointer-Zeile entsprechend anpassen. Slice P2 (Repo-Browser) als nächster Schritt notieren.

- [ ] **Step 2: Commit**

```bash
git add docs/superpowers/plans/2026-06-11-plugin-tab-color-preset.md
git commit -m "docs(plan): plugin-tab + color-preset-plugin (P1) implementation plan"
```

(Memory-Dateien liegen außerhalb des Repos → kein Repo-Commit.)

---

## Self-Review

**Spec-Abdeckung:**
- Color-Preset als data-only Plugin (JSON-Asset, kein Code) → Task 1+2. ✓
- Plugin-Tab für installierte Plugins (Quellen + Presets), ⚙ + 🗑 → Task 7+10. ✓
- ⚙ Quelle = bestehender TOFU→Config-Flow → Task 6 (Extraktion) + 10. ✓
- ⚙ Preset = Import-Detail, getaggt+gesperrt, „bereits importiert → entfernen" → Task 7 (`importPreset`/`isImported`/`removeImportedProfile`) + 10. ✓
- 🗑 = APK-Uninstall via OS-Intent + Re-Scan-Prune → Task 5 (purer Planer) + 10 (Intent + `onResume`). ✓
- Bespoke Import-UI raus → Task 11. ✓
- Settings „Server hinzufügen" nur Komga/OPDS → Task 8. ✓
- `color_profiles.pluginPackage` + nicht-destruktive Migration + Version-Bump → Task 4. ✓
- Beispiel-Preset-Plugin → Task 12. ✓
- `+` = P2-Platzhalter/Hinweis → Task 10 (`pluginTabReposHint`). ✓
- Tests (parsePresetSpecs pur, Migration androidTest, E2E) → Task 1, 4, 13. ✓
- Repo-Browser bewusst NICHT in P1 → nur Hinweis. ✓

**Typkonsistenz:** `DiscoveredPresetPlugin(packageName, displayName, abiVersion, presets)` einheitlich in Task 2/7/10. `planPluginPrune`/`PrunePlan` mit Feldern `presetPackagesToDrop`/`sourceConfigIdsToRemove` in Task 5/7. `deleteByPluginPackage(pkg)` in domain/data/VM einheitlich. `PluginManifestKeys.COLOR_PRESETS` in Task 1/2/12. `ColorProfile.pluginPackage` in domain/data/VM/UI einheitlich.

**Platzhalter-Scan:** Keine „TODO/TBD/handle edge cases" — alle Code-Schritte zeigen vollständigen Code; alle Commands haben erwartete Ausgabe.
