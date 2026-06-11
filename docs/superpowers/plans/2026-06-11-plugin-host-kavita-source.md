# Plugin-Fundament + Kavita-Quellen-Plugin Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Einen Runtime-Plugin-Loader (`plugin-host`) + erweiterten ABI-Vertrag (`plugin-api`) bauen und als ersten echten Konsumenten ein Kavita-Quellen-Plugin als separates APK, das über die bestehende Naht A (`SourceManager`/`BrowsableSource`) gleichzeitig mit Komga läuft.

**Architecture:** `plugin-api` (pure JVM) re-exportiert die Naht-Typen via `api(project(":source-api"))` und deklariert `SourcePlugin`/`ConfigSchema`. `plugin-host` (Android-Lib) entdeckt Plugin-APKs per `PackageManager`, prüft das 2-Int-ABI-Gate, lädt sie via `createPackageContext`-Classloader (Parent = Host) und instanziiert `SourcePlugin`. Der Host speichert Plugin-Config in `ServerConfig.extras` (Keystore-verschlüsselt) und verdrahtet den `SourceKind.PLUGIN`-Zweig in `SourceRegistration`. Das Kavita-APK linkt `plugin-api` compileOnly und implementiert `BrowsableSource` + `SyncingSource`.

**Tech Stack:** Kotlin, Gradle (multi-module), Android (`plugin-host`/Kavita-APK), Room (`extras`-Spalten), Retrofit/OkHttp + kotlinx.serialization (Kavita), JUnit5 + MockWebServer (Unit/Vertrag), Compose (Config-Form), Docker (Kavita-E2E).

---

## Dateien-Struktur (Decomposition)

**`plugin-api` (pure JVM, erweitern):**
- Modify: `plugin-api/build.gradle.kts` — `api(project(":source-api"))`, Maven-Publish.
- Create: `plugin-api/src/main/kotlin/com/komgareader/plugin/SourcePlugin.kt` — `SourcePlugin`, `PluginMetadata`.
- Create: `plugin-api/src/main/kotlin/com/komgareader/plugin/ConfigSchema.kt` — `ConfigSchema`, `ConfigField`, `FieldType`.

**`plugin-host` (neues Android-Lib-Modul):**
- Create: `plugin-host/build.gradle.kts`
- Create: `plugin-host/src/main/AndroidManifest.xml`
- Create: `plugin-host/src/main/kotlin/com/komgareader/plugin/host/PluginManifestKeys.kt` — Metadata-Schlüssel-Konstanten.
- Create: `plugin-host/src/main/kotlin/com/komgareader/plugin/host/AbiGate.kt` — reines ABI-Gate (pur testbar).
- Create: `plugin-host/src/main/kotlin/com/komgareader/plugin/host/PluginConfigHash.kt` — `configHash` (pur).
- Create: `plugin-host/src/main/kotlin/com/komgareader/plugin/host/DiscoveredPlugin.kt` — Daten-Holder.
- Create: `plugin-host/src/main/kotlin/com/komgareader/plugin/host/PluginHost.kt` — Discovery/Load/Instantiate (Android).
- Create: `plugin-host/src/test/kotlin/com/komgareader/plugin/host/AbiGateTest.kt`
- Create: `plugin-host/src/test/kotlin/com/komgareader/plugin/host/PluginConfigHashTest.kt`

**`domain` (Modell):**
- Modify: `domain/src/main/kotlin/com/komgareader/domain/repository/ServerRepository.kt` — `extras: Map<String,String>` in `ServerConfig`.

**`data` (Persistenz):**
- Modify: `data/src/main/kotlin/com/komgareader/data/db/Entities.kt` — `ServerEntity` + `extrasCiphertext`/`extrasIv`.
- Modify: `data/src/main/kotlin/com/komgareader/data/db/AppDatabase.kt` — `version = 15` + `MIGRATION_14_15`.
- Modify: `data/src/main/kotlin/com/komgareader/data/repository/RoomServerRepository.kt` — extras ent-/verschlüsseln.
- Modify: `data/src/main/kotlin/com/komgareader/data/di/DataModule.kt` — Migration registrieren.
- Create: `data/src/androidTest/kotlin/com/komgareader/data/db/Migration14To15Test.kt`

**`app` (Shell):**
- Create: `app/src/main/kotlin/com/komgareader/app/ui/settings/PluginConfigForm.kt` — generische Form aus `ConfigSchema`.
- Modify: `app/src/main/kotlin/com/komgareader/app/data/SourceRegistration.kt` — `SourceKind.PLUGIN`-Zweig.
- Modify: Settings-„Server hinzufügen"-Flow (`SettingsViewModel` + Screen) — Plugin-Auswahl + Form.
- Modify: `app/build.gradle.kts` + `settings.gradle.kts` — `plugin-host`-Abhängigkeit/Include.
- Create/Modify: DI-Modul für `PluginHost` (Context-Injection).

**Kavita-Plugin (`plugin/komga-kavita-source/`, eigenes Git-Repo, gitignored):**
- Eigenes Gradle-Projekt, Android-App-APK. Details in Phase 6.

---

## Phase 1 — `plugin-api` erweitern + publizieren

### Task 1: `ConfigSchema`-Typen

**Files:**
- Create: `plugin-api/src/main/kotlin/com/komgareader/plugin/ConfigSchema.kt`

- [ ] **Step 1: Implementieren** (reine Daten, kein Test nötig — Data-Classes ohne Logik)

```kotlin
package com.komgareader.plugin

/** Generisches Config-Schema, das ein Plugin dem Host deklariert (Plugin-Plan: Settings-per-Plugin-Keim). */
data class ConfigSchema(val fields: List<ConfigField>)

data class ConfigField(
    /** Speicher-Schlüssel in ServerConfig.extras. */
    val key: String,
    /** Vom Plugin geliefertes (bereits lokalisiertes) Label. */
    val label: String,
    val type: FieldType,
    val required: Boolean = true,
    val default: String = "",
)

enum class FieldType { TEXT, SECRET, URL, BOOL }
```

- [ ] **Step 2: Build prüfen**

Run: `./gradlew :plugin-api:compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add plugin-api/src/main/kotlin/com/komgareader/plugin/ConfigSchema.kt
git commit -m "feat(plugin-api): generisches ConfigSchema/ConfigField"
```

### Task 2: `SourcePlugin`-Vertrag + `api(source-api)`

**Files:**
- Create: `plugin-api/src/main/kotlin/com/komgareader/plugin/SourcePlugin.kt`
- Modify: `plugin-api/build.gradle.kts`

- [ ] **Step 1: build.gradle.kts — source-api re-exportieren**

In `plugin-api/build.gradle.kts` die `dependencies`-Block-Zeile `implementation(project(":domain"))` ersetzen durch:

```kotlin
dependencies {
    // api(): re-exportiert die Naht-Typen (BrowsableSource & die berührten domain-Modelle)
    // an Plugin-Konsumenten. domain kommt transitiv über source-api.
    api(project(":source-api"))
    testImplementation(kotlin("test"))
}
```

- [ ] **Step 2: SourcePlugin implementieren**

```kotlin
package com.komgareader.plugin

import com.komgareader.domain.model.SourceKind
import com.komgareader.domain.source.BrowsableSource

/**
 * Entry-Point eines Quellen-Plugin-APKs (Plugin-Typ a). Der Host instanziiert die im
 * Manifest deklarierte Klasse und ruft [create] mit den vom Nutzer eingegebenen Config-Werten.
 *
 * Classloader-Regel: Das Plugin linkt plugin-api COMPILE-ONLY — diese Klassen kommen zur
 * Laufzeit vom Host-Classloader (Parent), nie aus dem APK (sonst ClassCastException).
 */
interface SourcePlugin {
    val metadata: PluginMetadata
    fun configSchema(): ConfigSchema
    /** Erzeugt die laufende Quelle. Implementierungen dürfen zusätzlich SyncingSource implementieren. */
    fun create(config: Map<String, String>): BrowsableSource
}

data class PluginMetadata(
    val displayName: String,
    val kind: SourceKind = SourceKind.PLUGIN,
)
```

- [ ] **Step 3: Build prüfen**

Run: `./gradlew :plugin-api:compileKotlin`
Expected: BUILD SUCCESSFUL (BrowsableSource aus source-api auflösbar)

- [ ] **Step 4: Commit**

```bash
git add plugin-api/src/main/kotlin/com/komgareader/plugin/SourcePlugin.kt plugin-api/build.gradle.kts
git commit -m "feat(plugin-api): SourcePlugin-Vertrag, re-exportiert Naht-Typen via api(source-api)"
```

### Task 3: `plugin-api` nach mavenLocal publizieren

**Files:**
- Modify: `plugin-api/build.gradle.kts`

- [ ] **Step 1: maven-publish-Plugin + Koordinaten**

In `plugin-api/build.gradle.kts` oben den `plugins`-Block erweitern und unten einen `publishing`-Block ergänzen:

```kotlin
plugins {
    alias(libs.plugins.kotlin.jvm)
    `maven-publish`
    `java-library`
}

group = "com.komgareader"
version = "0.1.0"

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = "plugin-api"
            from(components["java"])
        }
    }
}
```

- [ ] **Step 2: Publizieren + verifizieren**

Run: `./gradlew :plugin-api:publishToMavenLocal && ls ~/.m2/repository/com/komgareader/plugin-api/0.1.0/`
Expected: `plugin-api-0.1.0.jar` vorhanden

- [ ] **Step 3: Commit**

```bash
git add plugin-api/build.gradle.kts
git commit -m "build(plugin-api): publishToMavenLocal (com.komgareader:plugin-api:0.1.0)"
```

---

## Phase 2 — `plugin-host` Loader + ABI-Gate

### Task 4: ABI-Gate (pur, TDD)

**Files:**
- Create: `plugin-host/build.gradle.kts`
- Create: `plugin-host/src/main/AndroidManifest.xml`
- Create: `plugin-host/src/main/kotlin/com/komgareader/plugin/host/AbiGate.kt`
- Test: `plugin-host/src/test/kotlin/com/komgareader/plugin/host/AbiGateTest.kt`

- [ ] **Step 1: Modul anlegen (build + manifest + settings include)**

`plugin-host/build.gradle.kts` — Android-Lib, Muster wie `eink-onyx`/`render-core` (Android-Lib-Module im Projekt; deren `build.gradle.kts` als Vorlage lesen). Mindestens:

```kotlin
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}
android {
    namespace = "com.komgareader.plugin.host"
    compileSdk = 35
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlinOptions { jvmTarget = "21" }
}
dependencies {
    api(project(":plugin-api"))
    implementation(project(":source-api"))
    implementation(project(":domain"))
    testImplementation(kotlin("test"))
    testImplementation(libs.junit.jupiter)
}
tasks.withType<Test> { useJUnitPlatform() }
```

`plugin-host/src/main/AndroidManifest.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest />
```

In `settings.gradle.kts` ergänzen: `include(":plugin-host")`

- [ ] **Step 2: Failing Test schreiben**

```kotlin
package com.komgareader.plugin.host

import com.komgareader.plugin.PluginAbi
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AbiGateTest {
    @Test fun `aktuelle ABI ist kompatibel`() =
        assertTrue(AbiGate.isCompatible(PluginAbi.VERSION))

    @Test fun `min unterstuetzte ABI ist kompatibel`() =
        assertTrue(AbiGate.isCompatible(PluginAbi.MIN_SUPPORTED))

    @Test fun `zu alt ist inkompatibel`() =
        assertFalse(AbiGate.isCompatible(PluginAbi.MIN_SUPPORTED - 1))

    @Test fun `zu neu ist inkompatibel`() =
        assertFalse(AbiGate.isCompatible(PluginAbi.VERSION + 1))
}
```

- [ ] **Step 3: Test ausführen (muss fehlschlagen)**

Run: `./gradlew :plugin-host:testDebugUnitTest --tests "*AbiGateTest"`
Expected: FAIL — `AbiGate` nicht definiert

- [ ] **Step 4: AbiGate implementieren**

```kotlin
package com.komgareader.plugin.host

import com.komgareader.plugin.PluginAbi

/** Reines 2-Int-ABI-Gate (Plugin-Plan-Entscheidung 2). */
object AbiGate {
    fun isCompatible(abiVersion: Int): Boolean =
        abiVersion in PluginAbi.MIN_SUPPORTED..PluginAbi.VERSION
}
```

- [ ] **Step 5: Test ausführen (muss grün sein)**

Run: `./gradlew :plugin-host:testDebugUnitTest --tests "*AbiGateTest"`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add plugin-host settings.gradle.kts
git commit -m "feat(plugin-host): Modul + ABI-Gate (TDD)"
```

### Task 5: `PluginConfigHash` (pur, TDD)

**Files:**
- Create: `plugin-host/src/main/kotlin/com/komgareader/plugin/host/PluginConfigHash.kt`
- Test: `plugin-host/src/test/kotlin/com/komgareader/plugin/host/PluginConfigHashTest.kt`

- [ ] **Step 1: Failing Test**

```kotlin
package com.komgareader.plugin.host

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class PluginConfigHashTest {
    @Test fun `gleiche werte unabhaengig der reihenfolge gleicher hash`() {
        val a = PluginConfigHash.of(mapOf("url" to "x", "apiKey" to "y"))
        val b = PluginConfigHash.of(mapOf("apiKey" to "y", "url" to "x"))
        assertEquals(a, b)
    }

    @Test fun `unterschiedliche werte unterschiedlicher hash`() {
        assertNotEquals(
            PluginConfigHash.of(mapOf("url" to "a")),
            PluginConfigHash.of(mapOf("url" to "b")),
        )
    }

    @Test fun `leere config liefert stabilen hash`() =
        assertEquals(PluginConfigHash.of(emptyMap()), PluginConfigHash.of(emptyMap()))
}
```

- [ ] **Step 2: Test ausführen (muss fehlschlagen)**

Run: `./gradlew :plugin-host:testDebugUnitTest --tests "*PluginConfigHashTest"`
Expected: FAIL — `PluginConfigHash` nicht definiert

- [ ] **Step 3: Implementieren**

```kotlin
package com.komgareader.plugin.host

import java.security.MessageDigest

/** Deterministischer, reihenfolge-unabhängiger Hash der Config-Werte (für SourceId-Namespace). */
object PluginConfigHash {
    fun of(config: Map<String, String>): String {
        val canonical = config.toSortedMap().entries.joinToString("&") { "${it.key}=${it.value}" }
        val digest = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }.take(16)
    }
}
```

- [ ] **Step 4: Test ausführen (grün)**

Run: `./gradlew :plugin-host:testDebugUnitTest --tests "*PluginConfigHashTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add plugin-host/src/main/kotlin/com/komgareader/plugin/host/PluginConfigHash.kt plugin-host/src/test/kotlin/com/komgareader/plugin/host/PluginConfigHashTest.kt
git commit -m "feat(plugin-host): deterministischer PluginConfigHash (TDD)"
```

### Task 6: Manifest-Schlüssel + `DiscoveredPlugin`

**Files:**
- Create: `plugin-host/src/main/kotlin/com/komgareader/plugin/host/PluginManifestKeys.kt`
- Create: `plugin-host/src/main/kotlin/com/komgareader/plugin/host/DiscoveredPlugin.kt`

- [ ] **Step 1: Implementieren**

```kotlin
// PluginManifestKeys.kt
package com.komgareader.plugin.host

/** Manifest-Metadata-Schlüssel, die ein Quellen-Plugin-APK deklariert. */
object PluginManifestKeys {
    const val ENTRY_CLASS = "com.komgareader.plugin.SOURCE"   // Wert: vollqualifizierter SourcePlugin-Class-Name
    const val ABI_VERSION = "com.komgareader.plugin.ABI_VERSION" // Wert: Int
}
```

```kotlin
// DiscoveredPlugin.kt
package com.komgareader.plugin.host

import com.komgareader.plugin.ConfigSchema
import com.komgareader.plugin.PluginMetadata

/** Ein installiertes, ABI-kompatibles Quellen-Plugin (für die Settings-Auswahlliste). */
data class DiscoveredPlugin(
    val packageName: String,
    val abiVersion: Int,
    val metadata: PluginMetadata,
    val configSchema: ConfigSchema,
)
```

- [ ] **Step 2: Build**

Run: `./gradlew :plugin-host:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add plugin-host/src/main/kotlin/com/komgareader/plugin/host/PluginManifestKeys.kt plugin-host/src/main/kotlin/com/komgareader/plugin/host/DiscoveredPlugin.kt
git commit -m "feat(plugin-host): Manifest-Schlüssel + DiscoveredPlugin-Holder"
```

### Task 7: `PluginHost` — Discovery/Load/Instantiate (Android)

**Files:**
- Create: `plugin-host/src/main/kotlin/com/komgareader/plugin/host/PluginHost.kt`

> Android-Framework-Klassen (`PackageManager`, `createPackageContext`) → kein reiner JUnit-Test. Verifikation per Build + E2E (Phase 7). Logik klein halten; pures Gate/Hash sind separat getestet (Task 4/5).

- [ ] **Step 1: Implementieren**

```kotlin
package com.komgareader.plugin.host

import android.content.Context
import android.content.pm.PackageManager
import com.komgareader.domain.model.SourceKind
import com.komgareader.domain.source.BrowsableSource
import com.komgareader.domain.source.SourceId
import com.komgareader.plugin.ConfigSchema
import com.komgareader.plugin.PluginMetadata
import com.komgareader.plugin.SourcePlugin

/**
 * Entdeckt, prüft und lädt Quellen-Plugin-APKs (Plugin-Plan-Entscheidungen 3–5).
 * Lädt via createPackageContext-Classloader mit Host als Parent — keine DexClassLoader,
 * keine heruntergeladenen .dex (OS macht Signatur/Integrität beim Install).
 */
class PluginHost(private val context: Context) {

    /** Alle installierten, ABI-kompatiblen Quellen-Plugins. */
    fun discoverPlugins(): List<DiscoveredPlugin> {
        val pm = context.packageManager
        val packages = pm.getInstalledPackages(PackageManager.GET_META_DATA)
        return packages.mapNotNull { pkg ->
            val meta = pkg.applicationInfo?.metaData ?: return@mapNotNull null
            val entry = meta.getString(PluginManifestKeys.ENTRY_CLASS) ?: return@mapNotNull null
            val abi = meta.getInt(PluginManifestKeys.ABI_VERSION, -1)
            if (!AbiGate.isCompatible(abi)) return@mapNotNull null
            val plugin = instantiate(pkg.packageName, entry) ?: return@mapNotNull null
            DiscoveredPlugin(pkg.packageName, abi, plugin.metadata, plugin.configSchema())
        }
    }

    /** Erzeugt die laufende BrowsableSource für eine konfigurierte Plugin-Quelle. */
    fun sourceFor(packageName: String, entryClass: String, config: Map<String, String>): BrowsableSource? {
        val plugin = instantiate(packageName, entryClass) ?: return null
        return plugin.create(config)
    }

    /** Stabile sourceId für eine Plugin-Quelle (packageName + configHash als Namespace). */
    fun sourceId(packageName: String, displayName: String, config: Map<String, String>): Long =
        SourceId.of(displayName, SourceKind.PLUGIN, "$packageName/${PluginConfigHash.of(config)}")

    private fun instantiate(packageName: String, entryClass: String): SourcePlugin? = runCatching {
        val flags = Context.CONTEXT_INCLUDE_CODE or Context.CONTEXT_IGNORE_SECURITY
        val pluginContext = context.createPackageContext(packageName, flags)
        // Parent = Host-Classloader → Vertrags-Interfaces sind dieselben Klassen (kein ClassCastException).
        val clazz = pluginContext.classLoader.loadClass(entryClass)
        clazz.getDeclaredConstructor().newInstance() as SourcePlugin
    }.getOrNull()
}
```

- [ ] **Step 2: Build**

Run: `./gradlew :plugin-host:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add plugin-host/src/main/kotlin/com/komgareader/plugin/host/PluginHost.kt
git commit -m "feat(plugin-host): PluginHost Discovery/Load/Instantiate (createPackageContext)"
```

---

## Phase 3 — `ServerConfig.extras` + Migration

### Task 8: `ServerConfig.extras` im Domain-Modell

**Files:**
- Modify: `domain/src/main/kotlin/com/komgareader/domain/repository/ServerRepository.kt:12-25`

- [ ] **Step 1: Feld ergänzen**

In `data class ServerConfig(...)` nach `val kind: SourceKind = SourceKind.KOMGA,` ergänzen:

```kotlin
    /**
     * Plugin-spezifische Config-Werte (key→value aus dem ConfigSchema des Plugins).
     * Leer für eingebaute Quellen (Komga/OPDS). SECRET-Felder werden in :data
     * Keystore-verschlüsselt persistiert (wie apiKey).
     */
    val extras: Map<String, String> = emptyMap(),
```

(Das bestehende `val id: Long = 0` bleibt das letzte Feld.)

- [ ] **Step 2: Build**

Run: `./gradlew :domain:compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add domain/src/main/kotlin/com/komgareader/domain/repository/ServerRepository.kt
git commit -m "feat(domain): ServerConfig.extras für Plugin-Config"
```

### Task 9: Room-Migration 14→15 (nullable `extras`-Spalten, ALTER ADD COLUMN)

**Files:**
- Modify: `data/src/main/kotlin/com/komgareader/data/db/Entities.kt` (ServerEntity)
- Modify: `data/src/main/kotlin/com/komgareader/data/db/AppDatabase.kt`
- Modify: `data/src/main/kotlin/com/komgareader/data/di/DataModule.kt`
- Test: `data/src/androidTest/kotlin/com/komgareader/data/db/Migration14To15Test.kt`

> **Migrations-Begründung:** Die `extras`-Spalten sind **nullable TEXT ohne DEFAULT** (verschlüsselter JSON-Blob). Für diesen Fall ist `ALTER TABLE ADD COLUMN` korrekt und nicht-destruktiv — exakt das Muster von `MIGRATION_4_5` (nullable Credential-Spalten). Die Memory-Falle `room-migration-destructive-pitfall` betrifft nur **NON-NULL + DEFAULT** ohne passendes `@ColumnInfo`; das trifft hier nicht zu. Kein Recreate-Table nötig.

- [ ] **Step 1: ServerEntity erweitern**

In `Entities.kt`, `ServerEntity` zwei nullable Spalten ergänzen (Muster wie `apiKeyCiphertext`):

```kotlin
    val extrasCiphertext: String? = null,
    val extrasIv: String? = null,
```

- [ ] **Step 2: Migrations-Test schreiben (failing)**

```kotlin
package com.komgareader.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class Migration14To15Test {
    private val dbName = "migration-14-15-test"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrate14To15_behaeltServerZeile_undFuegtExtrasSpaltenHinzu() {
        helper.createDatabase(dbName, 14).apply {
            execSQL("INSERT INTO server (id, name, baseUrl, kind) VALUES (1, 'Heim', 'http://h', 'KOMGA')")
            close()
        }
        val db = helper.runMigrationsAndValidate(dbName, 15, true, MIGRATION_14_15)
        db.query("SELECT name, extrasCiphertext FROM server WHERE id = 1").use { c ->
            c.moveToFirst()
            assertEquals("Heim", c.getString(0))
            assertEquals(true, c.isNull(1)) // neue Spalte, NULL für Altbestand
        }
    }
}
```

> Hinweis: Die exakten Spalten der v14-`server`-Tabelle aus dem aktuellen `ServerEntity` ableiten (der INSERT muss zu v14 passen — ggf. weitere NOT-NULL-Spalten mit Werten füllen). `ServerEntity` vor dem Schreiben lesen.

- [ ] **Step 3: Test ausführen (fehlschlägt — Migration fehlt)**

Run: `./gradlew :data:connectedDebugAndroidTest --tests "*Migration14To15Test"`
Expected: FAIL (MIGRATION_14_15 nicht definiert / Schema-Mismatch)

- [ ] **Step 4: Migration + Version**

In `AppDatabase.kt`: `version = 14` → `version = 15`. Nach `MIGRATION_4_5` (bzw. am Ende der Migrations) ergänzen:

```kotlin
/** v14 → v15: extras-Spalten (Keystore-verschlüsselter JSON-Blob) für Plugin-Quellen-Config. */
val MIGRATION_14_15 = object : Migration(14, 15) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `server` ADD COLUMN `extrasCiphertext` TEXT")
        db.execSQL("ALTER TABLE `server` ADD COLUMN `extrasIv` TEXT")
    }
}
```

In `DataModule.kt` die `MIGRATION_14_15` in der `.addMigrations(...)`-Liste des Room-Builders registrieren (bestehende Liste lesen + ergänzen).

- [ ] **Step 5: Test ausführen (grün)**

Run: `./gradlew :data:connectedDebugAndroidTest --tests "*Migration14To15Test"`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add data/src/main/kotlin/com/komgareader/data/db/ data/src/main/kotlin/com/komgareader/data/di/DataModule.kt data/src/androidTest/kotlin/com/komgareader/data/db/Migration14To15Test.kt
git commit -m "feat(data): Room 14→15 extras-Spalten für Plugin-Config (ALTER ADD, nullable)"
```

### Task 10: extras in `RoomServerRepository` ent-/verschlüsseln

**Files:**
- Modify: `data/src/main/kotlin/com/komgareader/data/repository/RoomServerRepository.kt`

> `RoomServerRepository` vor dem Schreiben lesen: es kapselt bereits die Keystore-Verschlüsselung für `apiKey`/`password` (cipher/iv). Denselben Helper für den `extras`-JSON-Blob nutzen.

- [ ] **Step 1: Mapping ergänzen**

- ServerEntity→ServerConfig (Lesen): `extrasCiphertext`/`extrasIv` mit dem bestehenden Keystore-Decrypt-Helper entschlüsseln → JSON-String → `Map<String,String>` (kotlinx.serialization `Json.decodeFromString<Map<String,String>>`). NULL/leer → `emptyMap()`.
- ServerConfig→ServerEntity (Schreiben): `extras.isEmpty()` → beide Spalten `null`; sonst `Json.encodeToString(extras)` → mit bestehendem Keystore-Encrypt-Helper verschlüsseln → cipher/iv setzen.

Konkrete Helper-Namen/Signaturen aus der Datei übernehmen (dieselben wie für `apiKey`). `kotlinx.serialization.json.Json` ist in `:data` bereits verfügbar (sonst Dependency prüfen).

- [ ] **Step 2: Build + bestehende data-Tests**

Run: `./gradlew :data:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, bestehende Tests grün

- [ ] **Step 3: Commit**

```bash
git add data/src/main/kotlin/com/komgareader/data/repository/RoomServerRepository.kt
git commit -m "feat(data): extras Keystore-verschlüsselt persistieren (wie apiKey)"
```

---

## Phase 4 — Generische Config-Form (`app`)

### Task 11: `PluginConfigForm` Composable

**Files:**
- Create: `app/src/main/kotlin/com/komgareader/app/ui/settings/PluginConfigForm.kt`

> E-Ink-Designsprache + `animation-gating` beachten: flach, 1.5px-Border, keine Animation, `i18n` für Rahmen/Buttons (Feld-Labels liefert das Plugin). Bestehende Settings-Form-Composables als Stil-Vorlage lesen (z.B. der „Server hinzufügen"-Dialog).

- [ ] **Step 1: Implementieren**

```kotlin
package com.komgareader.app.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import com.komgareader.plugin.ConfigField
import com.komgareader.plugin.ConfigSchema
import com.komgareader.plugin.FieldType

/**
 * Rendert eine Eingabe-Form generisch aus dem [schema] eines Plugins — KEIN quellen-
 * spezifischer Code. [onSubmit] erhält die Werte als key→value-Map (= ServerConfig.extras).
 */
@Composable
fun PluginConfigForm(
    schema: ConfigSchema,
    onSubmit: (Map<String, String>) -> Unit,
) {
    val values = remember { mutableStateMapOf<String, String>() }
    // Pro ConfigField ein Control je FieldType:
    //   TEXT/URL → Textfeld, SECRET → maskiertes Textfeld, BOOL → Toggle (Wert "true"/"false").
    //   Defaults aus field.default vorbelegen. required-Felder leer → Submit deaktiviert.
    // Bestehende E-Ink-Textfeld-/Toggle-Komponenten + BaseDialog-Footer-Buttons verwenden.
    // … (Compose-UI nach Projekt-Komponenten; values füllen; Submit → onSubmit(values.toMap()))
}
```

> Dieser Step ist UI-Layout (visuell). Konkrete Compose-Controls aus dem Projekt-Komponentenkatalog (`komga-eink-ui-polish`-Skill / bestehende Settings-Dialoge) übernehmen — nicht neu erfinden. Validierung: alle `required && type!=BOOL` nicht-leer → Submit aktiv.

- [ ] **Step 2: Build**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/komgareader/app/ui/settings/PluginConfigForm.kt
git commit -m "feat(app): generische PluginConfigForm aus ConfigSchema (E-Ink)"
```

### Task 12: Plugin-Auswahl im „Server hinzufügen"-Flow

**Files:**
- Modify: Settings-ViewModel + „Server hinzufügen"-Screen/Dialog (vorher `grep -rn "Server hinzu\|addServer\|server_add" app/src` lesen).

- [ ] **Step 1: Discovery in Settings einbinden**

- `PluginHost.discoverPlugins()` im SettingsViewModel aufrufen (über DI), Ergebnis als Liste anbieten: „Komga", „OPDS", + je entdecktes Plugin (`metadata.displayName`).
- Wählt der Nutzer ein Plugin → **TOFU-Bestätigung:** den `DiscoveredPlugin.signatureSha256` (Cert-Digest) in einem `BaseDialog` zeigen + bestätigen lassen (einmalig, „Diesem Plugin vertrauen?"). Erst danach `PluginConfigForm(plugin.configSchema)` zeigen.
- Submit → `ServerConfig(name=displayName, kind=SourceKind.PLUGIN, extras=values + wiringKeys, baseUrl=values["url"] ?: "")` speichern (über bestehenden addServer-Pfad). `baseUrl` best-effort aus einem URL-Feld, sonst leer (SourceId nutzt configHash, nicht baseUrl).
- **Reservierte Wiring-Keys in `extras`** ablegen, damit `SourceRegistration` sie wiederfindet: `__pkg` = packageName, `__entry` = entryClass, **`__sig` = der bestätigte `signatureSha256` (Pin)**. Alle `__`-Keys werden vor `plugin.create(...)` herausgefiltert (nicht ans Plugin durchgereicht).

- [ ] **Step 2: Build**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/komgareader/app/ui/settings/
git commit -m "feat(app): Plugin in 'Server hinzufügen' wählbar + generische Config-Form"
```

---

## Phase 5 — `SourceRegistration` PLUGIN-Zweig + DI

### Task 13: `PluginHost` per DI bereitstellen

**Files:**
- Modify: app-DI-Modul (vorher `grep -rn "@Provides\|@Module" app/src/main/kotlin/com/komgareader/app/data` + `di`-Ordner lesen).
- Modify: `app/build.gradle.kts` (+ `implementation(project(":plugin-host"))`).

- [ ] **Step 1: Abhängigkeit + Provider**

- `app/build.gradle.kts`: `implementation(project(":plugin-host"))`.
- Hilt-Provider: `@Provides @Singleton fun providePluginHost(@ApplicationContext ctx: Context) = PluginHost(ctx)`.

- [ ] **Step 2: Build**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/build.gradle.kts app/src/main/kotlin/com/komgareader/app/
git commit -m "build(app): plugin-host-Abhängigkeit + PluginHost-DI-Provider"
```

### Task 14: PLUGIN-Zweig in `SourceRegistration.build`

**Files:**
- Modify: `app/src/main/kotlin/com/komgareader/app/data/SourceRegistration.kt`

- [ ] **Step 1: PluginHost injizieren + Zweig**

`SourceRegistration`-Konstruktor um `private val pluginHost: PluginHost` erweitern. In `build(config)` den `when (config.kind)`:

```kotlin
        SourceKind.PLUGIN -> {
            val pkg = config.extras["__pkg"] ?: return null
            val entry = config.extras["__entry"] ?: return null
            val sig = config.extras["__sig"] ?: return null   // ohne Pin nicht laden (TOFU)
            // reservierte Wiring-Keys nicht ans Plugin durchreichen
            val pluginConfig = config.extras.filterKeys { !it.startsWith("__") }
            // sourceFor verifiziert die aktuelle Signatur gegen den Pin und lädt nur bei Match.
            pluginHost.sourceFor(pkg, entry, sig, pluginConfig)
        }
```

(Vor `else -> komgaProvider.from(config)` einfügen.)

- [ ] **Step 2: Bestehenden SourceRegistrationTest anpassen + Build**

`SourceRegistration`-Konstruktor-Aufrufe in Tests/DI um den `pluginHost`-Parameter ergänzen (Test darf einen `PluginHost`-Fake/Mock übergeben).

Run: `./gradlew :app:testDebugUnitTest --tests "*SourceRegistrationTest" && ./gradlew :app:compileDebugKotlin`
Expected: PASS + BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/komgareader/app/data/SourceRegistration.kt app/src/test/
git commit -m "feat(app): SourceRegistration PLUGIN-Zweig über PluginHost (Naht gewahrt)"
```

---

## Phase 6 — Kavita-Plugin-APK (eigenes Repo)

### Task 15: Kavita-Plugin-Projekt anlegen

**Files (außerhalb Host-Git, in `plugin/komga-kavita-source/`):**
- Create: Gradle-Android-App-Projekt + eigenes `git init`.

- [ ] **Step 1: Projekt + Git**

```bash
mkdir -p plugin/komga-kavita-source && cd plugin/komga-kavita-source && git init
```

Minimales Android-App-Gradle-Projekt (eigenes `settings.gradle.kts`, `build.gradle.kts`, `app`-Modul oder single-module). Schlüssel:
- `compileOnly("com.komgareader:plugin-api:0.1.0")` aus `mavenLocal()` (in `repositories { mavenLocal(); google(); mavenCentral() }`).
- Eigene Deps (gepackt): Retrofit, OkHttp, kotlinx.serialization, coroutines.
- `minSdk = 26`, JVM 21.

- [ ] **Step 2: Manifest-Metadata**

`AndroidManifest.xml` im `<application>`:

```xml
<meta-data android:name="com.komgareader.plugin.SOURCE"
    android:value="com.komgareader.plugin.kavita.KavitaSourcePlugin" />
<meta-data android:name="com.komgareader.plugin.ABI_VERSION"
    android:value="1" />
```

- [ ] **Step 3: Commit (im Plugin-Repo)**

```bash
git add -A && git commit -m "chore: Kavita-Quellen-Plugin Projekt-Gerüst (plugin-api compileOnly)"
```

### Task 16: Kavita-Auth + Retrofit-Client (TDD MockWebServer)

**Files (Plugin-Repo):**
- Create: `.../kavita/KavitaApi.kt`, `.../kavita/KavitaAuth.kt` + Test mit MockWebServer.

- [ ] **Step 1: Failing Auth-Test (MockWebServer)**

Test: API-Key `x-api-key` → `POST /api/Plugin/authenticate` → Antwort `{"token":"jwt..."}` → Client setzt `Authorization: Bearer jwt...` auf Folge-Requests. (Exakte Pfade/Felder gegen `https://raw.githubusercontent.com/Kareadita/Kavita/develop/openapi.json` pinnen.)

- [ ] **Step 2: Test fehlschlägt** → `./gradlew test` (Plugin-Repo) Expected: FAIL
- [ ] **Step 3: Auth + Client implementieren** (OkHttp-Interceptor hängt Bearer-Token an; Token bei 401 erneuern).
- [ ] **Step 4: Test grün** → `./gradlew test` Expected: PASS
- [ ] **Step 5: Commit** im Plugin-Repo.

### Task 17: `BrowsableSource` + `SyncingSource` + Mapper (TDD)

**Files (Plugin-Repo):**
- Create: `.../kavita/KavitaSource.kt`, `.../kavita/KavitaMapper.kt`, `.../kavita/KavitaSourcePlugin.kt` + Tests.

- [ ] **Step 1: Mapper-Test (gesetzt + leer/null)**

Kavita-DTO→domain: Series/Volume/Chapter→`Series`/`Book`, Seiten→`PageRef`, Progress→`ReadProgress`. Tests für vorhandene **und** fehlende Felder (`ifBlank { null }` etc.).

- [ ] **Step 2: Vertragstests (MockWebServer)** für `browse`/`search`/`books`/`seriesDetail`/`pages`/`openPage`/`coverBytes`/`downloadFile` + `pushProgress`/`pullProgress`/`setRead`.
- [ ] **Step 3: Implementieren** `KavitaSource : BrowsableSource, SyncingSource` mit deterministischer `id` (aus `name`/PLUGIN/config). `KavitaSourcePlugin : SourcePlugin` mit `configSchema = { url(URL), apiKey(SECRET) }` und `create(config)` → `KavitaSource`.
- [ ] **Step 4: Tests grün** → `./gradlew test` Expected: PASS
- [ ] **Step 5: APK bauen** → `./gradlew assembleDebug` Expected: APK in `build/outputs/apk/`.
- [ ] **Step 6: Commit** im Plugin-Repo.

- [ ] **Step 7: APK-Hygiene prüfen (Classloader-Falle)**

Verifizieren, dass die `plugin-api`/`source-api`/`domain`-Klassen NICHT im APK sind (compileOnly):

Run: `unzip -l build/outputs/apk/debug/*.apk | grep -i "com/komgareader/domain\|com/komgareader/plugin/SourcePlugin" || echo "OK: Vertragsklassen nicht im APK"`
Expected: `OK: Vertragsklassen nicht im APK`

---

## Phase 7 — Docker-Kavita + E2E

### Task 18: Docker-Kavita hochfahren + seeden

- [ ] **Step 1: Container starten**

Kavita-Docker (`jvmilazz0/kavita` o.ä. offizielles Image) mit gemapptem Daten-/Manga-Verzeichnis starten. Test-Inhalt ablegen, **der nicht in der Test-Komga existiert** (eigene Serie(n) → beweist unterscheidbaren Content). Erst-Setup (Admin-Account) + Library anlegen + API-Key generieren.

- [ ] **Step 2: API-Key + URL notieren** für den E2E-Test.

- [ ] **Step 3: Memory dokumentieren** (`local-test-kavita.md` analog `local-test-komga`): URL, API-Key, Seed-Inhalt, Docker-Befehl. MEMORY.md-Zeile ergänzen.

### Task 19: E2E mixed-source (Emulator)

**Files:**
- Modify/Create: `app/src/androidTest/.../MixedSourcesLiveTest.kt` (bestehenden erweitern).

- [ ] **Step 1: Kavita-Plugin-APK auf Emulator installieren**

Run: `adb install plugin/komga-kavita-source/.../debug/*.apk`

- [ ] **Step 2: E2E-Test**

Komga + Kavita-Plugin gleichzeitig registriert (Kavita über `PluginHost`/`ServerConfig.extras`). Assert: `ActiveSource.all()` liefert beide Quellen; eine Kavita-Serie (Seed-only) ist sichtbar; eine Seite/Datei der Kavita-Serie wird über die Naht (`openPage`/`downloadFile`) geladen. Beweist gemischte Quellen mit unterscheidbarem Content.

- [ ] **Step 3: Ausführen**

Run: `./gradlew :app:connectedDebugAndroidTest --tests "*MixedSourcesLiveTest"`
Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add app/src/androidTest/
git commit -m "test(e2e): Komga + Kavita-Plugin gemischt live verifiziert"
```

---

## Phase 8 — Doku nachziehen (docs-match-code)

### Task 20: Regeln + Memory aktualisieren

- [ ] **Step 1: `architecture-seams.md`** — neuen Ist-Stand „Plugin-Loader (plugin-host) + Source-Plugin-ABI gebaut, Kavita als erstes APK" ergänzen (Naht A, Plugin-Bereitschaft).
- [ ] **Step 2: `big-picture-and-goals.md`** — Plugin-Plan (a) Quellen von „Soll/designter Pfad" auf „erstes APK live" aktualisieren; `plugin-host` als real markieren.
- [ ] **Step 3: Memory** `project-komga-eink-reader` + neue `local-test-kavita` verlinken.
- [ ] **Step 4: Commit**

```bash
git add .claude/rules/ docs/
git commit -m "docs: Plugin-Loader + Kavita-Quelle Ist-Stand (docs-match-code)"
```

---

## Self-Review-Notiz

- **Spec-Abdeckung:** ABI-Vertrag (T1–2), publish (T3), Loader/Gate (T4–7), extras+Migration (T8–10), Config-Form (T11–12), Wiring (T13–14), Kavita-APK (T15–17), Docker+E2E (T18–19), docs-match-code (T20). Alle Spec-Abschnitte abgedeckt.
- **Migration-Reconciliation:** Spec sagte „Recreate-Table"; Plan nutzt `ALTER ADD COLUMN` für nullable Spalten (proven `MIGRATION_4_5`-Muster). Spec-Note wird angeglichen (separat).
- **Typ-Konsistenz:** `SourcePlugin.create(Map)`, `ConfigField{key,label,type,required,default}`, `PluginHost.sourceFor(pkg,entry,config)`/`sourceId(...)`, `extras`-reservierte Keys `__pkg`/`__entry` durchgängig.
- **Offene Reads (kein Phantom):** ServerEntity v14-Spalten (T9-Test-INSERT), Keystore-Helper-Signaturen (T10), Settings-addServer-Pfad (T12), app-DI-Modul (T13), bestehende Compose-Controls (T11) — jeweils „vor dem Schreiben lesen" markiert.
