# Plugin-Repo-Browser + APK-Download/Install (Slice P2) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the Plugins-tab `+` real — browse an official + user-added plugin repositories (JSON index), download an APK over HTTPS, verify its signing-cert fingerprint against the index, install via `PackageInstaller`, and show install/update state per entry.

**Architecture:** Pure parse/version/fingerprint logic lives in `:data` (functional core, JVM-unit-tested); `PluginRepoClient` (OkHttp) fetches indices + downloads APKs; `RepoStore` persists user repos (Room `plugin_repos`, migration 16→17) + an official-repo toggle (settings KV); the official repo URL is a swappable code constant (not seeded). `PluginInstaller` (`:app`) reads the downloaded APK's cert, verifies the fingerprint, and drives a `PackageInstaller` session (OS shows the install dialog). A pushed `RepoBrowserScreen` route hangs off the existing NavHost; `domain` is untouched.

**Tech Stack:** Kotlin, Jetpack Compose, Hilt, Room, OkHttp (`libs.okhttp`), kotlinx.serialization, `PackageInstaller`/`PackageManager`, JUnit5 + MockK + MockWebServer (unit), AndroidJUnit4 (migration/E2E).

**Invariants (check before every commit):**
- `domain` stays free of Android/network/plugin-concrete types. The official-repo toggle is a generic `Boolean` settings flag (allowed); no repo/plugin types leak into `domain`.
- Pure functions (`parseRepoIndex`, `resolveApkUrl`, `mergeRepoEntries`, `installState`, `fingerprintMatches`, `pluginKindOf`) have NO Android/OkHttp imports and are unit-tested.
- **Security:** an APK is handed to `PackageInstaller` ONLY after its cert SHA-256 matches the index `fingerprint` (normalized). Mismatch = hard abort + delete the cached file.
- E-Ink design: flat, 1.5px border (`EinkTokens.hairline`), Lucide via `AppIcons`, NO animation, `EinkModal`/`EinkInfoDialog`, `LoadingIndicator`; all visible text via `LocalStrings` (DE+EN parity, real umlauts/ß).
- Room: migration 16→17 is a plain `CREATE TABLE` matching the entity schema exactly; bump `@Database(version=17)` AND register the migration.

---

## File Structure

**`data` (pure logic, networking, persistence):**
- Create `data/src/main/kotlin/com/komgareader/data/plugin/repo/RepoModels.kt` — `RepoIndex`, `RepoPluginEntry` (`@Serializable`), `PluginKind`, `InstallState`, `BrowsableEntry`.
- Create `data/src/main/kotlin/com/komgareader/data/plugin/repo/RepoIndexParser.kt` — pure `parseRepoIndex`, `resolveApkUrl`, `mergeRepoEntries`, `installState`, `normalizeFingerprint`, `fingerprintMatches`, `pluginKindOf`.
- Create `data/src/main/kotlin/com/komgareader/data/plugin/repo/PluginRepoDefaults.kt` — `OFFICIAL_URL` constant.
- Create `data/src/main/kotlin/com/komgareader/data/plugin/repo/PluginRepoClient.kt` — OkHttp fetch + download.
- Create `data/src/main/kotlin/com/komgareader/data/plugin/repo/RepoStore.kt` — user-repo CRUD + official toggle.
- Modify `data/src/main/kotlin/com/komgareader/data/db/Entities.kt` — `PluginRepoEntity`.
- Create `data/src/main/kotlin/com/komgareader/data/db/PluginRepoDao.kt`.
- Modify `data/src/main/kotlin/com/komgareader/data/db/AppDatabase.kt` — version 17, `MIGRATION_16_17`, dao accessor, entity registration.
- Modify `data/src/main/kotlin/com/komgareader/data/di/DataModule.kt` — register migration + provide `RepoStore`/`PluginRepoClient`.
- Modify `data/build.gradle.kts` — `implementation(libs.okhttp)`, `testImplementation(libs.okhttp.mockwebserver)`.
- Test: `data/src/test/.../plugin/repo/RepoIndexParserTest.kt`, `PluginRepoClientTest.kt`; `data/src/androidTest/.../db/Migration16To17Test.kt`.

**`domain` (minimal — generic flag only):**
- Modify `domain/src/main/kotlin/com/komgareader/domain/repository/SettingsRepository.kt` — `officialRepoEnabled: Flow<Boolean>` + `setOfficialRepoEnabled(Boolean)`.

**`app` (install + UI + wiring):**
- Create `app/src/main/kotlin/com/komgareader/app/ui/plugins/repo/PluginInstaller.kt` — cert-verify + `PackageInstaller` session.
- Create `app/src/main/kotlin/com/komgareader/app/ui/plugins/repo/RepoBrowserViewModel.kt`.
- Create `app/src/main/kotlin/com/komgareader/app/ui/plugins/repo/RepoBrowserScreen.kt` — screen + `RepoBrowserRoute`.
- Modify `app/src/main/kotlin/com/komgareader/app/MainActivity.kt` — `plugin-repos` route + thread `onOpenRepoBrowser`.
- Modify `app/src/main/kotlin/com/komgareader/app/ui/home/HomeScreen.kt` — thread `onOpenRepoBrowser` to `PluginsScreen`.
- Modify `app/src/main/kotlin/com/komgareader/app/ui/plugins/PluginsScreen.kt` — `+`/discover button → `onOpenRepoBrowser`.
- Modify `app/src/main/kotlin/com/komgareader/app/i18n/Strings.kt` — new keys.
- Modify `app/src/main/AndroidManifest.xml` — `REQUEST_INSTALL_PACKAGES`.
- Modify `data/src/main/kotlin/com/komgareader/data/repository/RoomSettingsRepository.kt` — impl of the new toggle.
- Fix test stubs implementing `SettingsRepository` (compile fallout).

---

## Task 1: data — pure repo models + parser/logic (TDD)

**Files:**
- Create: `data/src/main/kotlin/com/komgareader/data/plugin/repo/RepoModels.kt`
- Create: `data/src/main/kotlin/com/komgareader/data/plugin/repo/RepoIndexParser.kt`
- Test: `data/src/test/kotlin/com/komgareader/data/plugin/repo/RepoIndexParserTest.kt`

- [ ] **Step 1: Models**

`RepoModels.kt`:
```kotlin
package com.komgareader.data.plugin.repo

import kotlinx.serialization.Serializable

/** Plugin-Typ im Repo-Index (nur für Label/Filter — Install ist für beide gleich). */
enum class PluginKind { SOURCE, PRESET }

/** Installations-Zustand eines Index-Eintrags relativ zum installierten Paket. */
enum class InstallState { NOT_INSTALLED, INSTALLED, UPDATE_AVAILABLE }

/** Ein `repo.json`-Index. Unbekannte Felder werden beim Parsen ignoriert (vorwärtskompatibel). */
@Serializable
data class RepoIndex(
    val name: String = "",
    val plugins: List<RepoPluginEntry> = emptyList(),
)

/** Ein einzelner Plugin-Eintrag im Index. Defaults erlauben tolerantes Parsen; Pflichtfelder
 *  werden in [parseRepoIndex] geprüft (leere → Eintrag verworfen). */
@Serializable
data class RepoPluginEntry(
    val packageName: String = "",
    val name: String = "",
    val description: String = "",
    val type: String = "source",
    val abiVersion: Int = 0,
    val versionCode: Long = 0,
    val versionName: String = "",
    val apkUrl: String = "",
    val fingerprint: String = "",
)

/** Ein gemergter, anzeigefertiger Eintrag mit Repo-Herkunft. */
data class BrowsableEntry(
    val entry: RepoPluginEntry,
    val repoName: String,
    val repoUrl: String,
    val kind: PluginKind,
)
```

- [ ] **Step 2: Failing test**

`RepoIndexParserTest.kt`:
```kotlin
package com.komgareader.data.plugin.repo

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RepoIndexParserTest {

    private val full = """
        {"name":"Off","plugins":[
          {"packageName":"a.b","name":"A","type":"preset","abiVersion":1,"versionCode":2,"versionName":"1.1","apkUrl":"p/a.apk","fingerprint":"AA:bb"},
          {"packageName":"c.d","name":"C","type":"source","abiVersion":1,"versionCode":1,"versionName":"1.0","apkUrl":"https://x/c.apk","fingerprint":"cc"}
        ]}
    """.trimIndent()

    @Test fun parsesValidIndex() {
        val idx = parseRepoIndex(full)!!
        assertEquals("Off", idx.name)
        assertEquals(2, idx.plugins.size)
        assertEquals("a.b", idx.plugins[0].packageName)
    }

    @Test fun ignoresUnknownFields() {
        val idx = parseRepoIndex("""{"name":"X","extra":true,"plugins":[{"packageName":"a","name":"A","versionCode":1,"apkUrl":"u","fingerprint":"f","weird":9}]}""")
        assertEquals("X", idx!!.name)
        assertEquals(1, idx.plugins.size)
    }

    @Test fun dropsEntriesMissingRequiredFields() {
        // fehlend: packageName / apkUrl / fingerprint / versionCode<=0 → verworfen
        val idx = parseRepoIndex("""{"plugins":[
            {"name":"no-pkg","versionCode":1,"apkUrl":"u","fingerprint":"f"},
            {"packageName":"ok","name":"Ok","versionCode":1,"apkUrl":"u","fingerprint":"f"}
        ]}""")
        assertEquals(1, idx!!.plugins.size)
        assertEquals("ok", idx.plugins[0].packageName)
    }

    @Test fun malformedReturnsNull() {
        assertNull(parseRepoIndex("not json"))
        assertNull(parseRepoIndex("""[1,2,3]"""))
    }

    @Test fun resolveApkUrl_absolutePassesThrough() {
        assertEquals("https://x/c.apk", resolveApkUrl("https://r/repo.json", "https://x/c.apk"))
    }

    @Test fun resolveApkUrl_relativeAgainstRepoBase() {
        assertEquals("https://r/sub/p/a.apk", resolveApkUrl("https://r/sub/repo.json", "p/a.apk"))
        assertEquals("https://r/p/a.apk", resolveApkUrl("https://r/repo.json", "/p/a.apk"))
    }

    @Test fun installStateAllCases() {
        assertEquals(InstallState.NOT_INSTALLED, installState(2, null))
        assertEquals(InstallState.INSTALLED, installState(2, 2))
        assertEquals(InstallState.INSTALLED, installState(2, 3))
        assertEquals(InstallState.UPDATE_AVAILABLE, installState(3, 2))
    }

    @Test fun fingerprintMatchesNormalizesColonsAndCase() {
        assertTrue(fingerprintMatches("AA:bb:CC", "aabbcc"))
        assertTrue(fingerprintMatches("aa bb", "AABB"))
        assertTrue(!fingerprintMatches("aa", "bb"))
    }

    @Test fun pluginKindOf_unknownIsSource() {
        assertEquals(PluginKind.PRESET, pluginKindOf("preset"))
        assertEquals(PluginKind.SOURCE, pluginKindOf("source"))
        assertEquals(PluginKind.SOURCE, pluginKindOf("garbage"))
    }

    @Test fun mergeDedupsByPackageKeepingHighestVersion() {
        val e1 = BrowsableEntry(RepoPluginEntry("a.b", "A", versionCode = 1, apkUrl = "u", fingerprint = "f"), "R1", "https://r1/repo.json", PluginKind.SOURCE)
        val e2 = BrowsableEntry(RepoPluginEntry("a.b", "A", versionCode = 3, apkUrl = "u", fingerprint = "f"), "R2", "https://r2/repo.json", PluginKind.SOURCE)
        val e3 = BrowsableEntry(RepoPluginEntry("c.d", "C", versionCode = 1, apkUrl = "u", fingerprint = "f"), "R1", "https://r1/repo.json", PluginKind.PRESET)
        val merged = mergeRepoEntries(listOf(e1, e2, e3))
        assertEquals(2, merged.size)
        assertEquals(3L, merged.first { it.entry.packageName == "a.b" }.entry.versionCode)
        assertEquals("R2", merged.first { it.entry.packageName == "a.b" }.repoName)
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `./gradlew :data:testDebugUnitTest --tests "com.komgareader.data.plugin.repo.RepoIndexParserTest"`
Expected: FAIL (unresolved references).

- [ ] **Step 4: Implement**

`RepoIndexParser.kt`:
```kotlin
package com.komgareader.data.plugin.repo

import kotlinx.serialization.json.Json
import java.net.URI

private val json = Json { ignoreUnknownKeys = true; isLenient = true }

/** Parst einen `repo.json`-Index. Gibt null zurück, wenn das JSON kein Index-Objekt ist/kaputt ist.
 *  Einzelne Einträge ohne Pflichtfeld (packageName/apkUrl/fingerprint/versionCode>0) werden verworfen. */
fun parseRepoIndex(text: String): RepoIndex? {
    val idx = runCatching { json.decodeFromString(RepoIndex.serializer(), text) }.getOrNull() ?: return null
    val valid = idx.plugins.filter {
        it.packageName.isNotBlank() && it.apkUrl.isNotBlank() && it.fingerprint.isNotBlank() && it.versionCode > 0
    }
    return idx.copy(plugins = valid)
}

/** Löst [apkUrl] auf: absolute http(s)-URL unverändert; sonst relativ gegen die Basis der [repoUrl]. */
fun resolveApkUrl(repoUrl: String, apkUrl: String): String {
    if (apkUrl.startsWith("http://") || apkUrl.startsWith("https://")) return apkUrl
    return URI(repoUrl).resolve(apkUrl).toString()
}

/** Mappt den Index-`type` auf [PluginKind]; Unbekanntes → SOURCE (konservativ). */
fun pluginKindOf(type: String): PluginKind =
    if (type.equals("preset", ignoreCase = true)) PluginKind.PRESET else PluginKind.SOURCE

/** Dedupt nach packageName, behält den Eintrag mit der höchsten versionCode (inkl. Repo-Herkunft). */
fun mergeRepoEntries(all: List<BrowsableEntry>): List<BrowsableEntry> =
    all.groupBy { it.entry.packageName }
        .map { (_, group) -> group.maxBy { it.entry.versionCode } }

/** Zustand eines Eintrags relativ zum installierten Paket (null = nicht installiert). */
fun installState(entryVersionCode: Long, installedVersionCode: Long?): InstallState = when {
    installedVersionCode == null -> InstallState.NOT_INSTALLED
    installedVersionCode >= entryVersionCode -> InstallState.INSTALLED
    else -> InstallState.UPDATE_AVAILABLE
}

/** SHA-256-Hex ohne Doppelpunkte/Whitespace, lowercase. */
fun normalizeFingerprint(s: String): String =
    s.filterNot { it == ':' || it.isWhitespace() }.lowercase()

/** True, wenn Index-Fingerprint und APK-Cert-SHA nach Normalisierung gleich sind. */
fun fingerprintMatches(indexFingerprint: String, apkCertSha256: String): Boolean =
    normalizeFingerprint(indexFingerprint) == normalizeFingerprint(apkCertSha256)
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew :data:testDebugUnitTest --tests "com.komgareader.data.plugin.repo.RepoIndexParserTest"`
Expected: PASS (10 tests).

- [ ] **Step 6: Commit**

```bash
git add data/src/main/kotlin/com/komgareader/data/plugin/repo/RepoModels.kt data/src/main/kotlin/com/komgareader/data/plugin/repo/RepoIndexParser.kt data/src/test/kotlin/com/komgareader/data/plugin/repo/RepoIndexParserTest.kt
git commit -m "feat(data): pure repo-index parser, url-resolve, merge, installState, fingerprint match"
```
End commit body with: `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`

---

## Task 2: data — `plugin_repos` table + migration 16→17

**Files:**
- Modify: `data/src/main/kotlin/com/komgareader/data/db/Entities.kt`
- Create: `data/src/main/kotlin/com/komgareader/data/db/PluginRepoDao.kt`
- Modify: `data/src/main/kotlin/com/komgareader/data/db/AppDatabase.kt`
- Modify: `data/src/main/kotlin/com/komgareader/data/di/DataModule.kt`
- Test: `data/src/androidTest/kotlin/com/komgareader/data/db/Migration16To17Test.kt`

- [ ] **Step 1: Entity**

Append to `Entities.kt`:
```kotlin
@Entity(tableName = "plugin_repos", indices = [Index(value = ["url"], unique = true)])
data class PluginRepoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val name: String? = null,
)
```
(READ the file's imports first; ensure `androidx.room.Entity`, `PrimaryKey`, `Index` are imported — add `import androidx.room.Index` if missing.)

- [ ] **Step 2: DAO**

`PluginRepoDao.kt`:
```kotlin
package com.komgareader.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PluginRepoDao {
    @Query("SELECT * FROM plugin_repos ORDER BY id ASC")
    fun observeAll(): Flow<List<PluginRepoEntity>>

    @Query("SELECT * FROM plugin_repos ORDER BY id ASC")
    suspend fun getAll(): List<PluginRepoEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: PluginRepoEntity): Long

    @Query("UPDATE plugin_repos SET name = :name WHERE url = :url")
    suspend fun setName(url: String, name: String?)

    @Query("DELETE FROM plugin_repos WHERE id = :id")
    suspend fun delete(id: Long)
}
```

- [ ] **Step 3: Migration + version + registration**

In `AppDatabase.kt`: change `version = 16` → `version = 17`; add `PluginRepoEntity::class` to the `entities = [...]` array; add `abstract fun pluginRepoDao(): PluginRepoDao`; after `MIGRATION_15_16` add:
```kotlin
/**
 * v16 → v17: `plugin_repos`-Tabelle (vom Nutzer hinzugefügte Plugin-Repo-URLs). **Nicht-destruktiv** —
 * reines `CREATE TABLE` (+ Unique-Index auf `url`), keine Bestandsdaten, kein `ALTER`. Das Schema bildet
 * exakt das vom Entity erzeugte ab, damit Rooms Validierung sauber läuft und `fallbackToDestructiveMigration`
 * NICHT greift. Das offizielle Repo ist KEINE Zeile hier (Code-Konstante + Settings-Toggle).
 */
val MIGRATION_16_17 = object : Migration(16, 17) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `plugin_repos` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `url` TEXT NOT NULL, `name` TEXT)",
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_plugin_repos_url` ON `plugin_repos` (`url`)")
    }
}
```

In `DataModule.kt`: import `MIGRATION_16_17`, append it to `addMigrations(...)`.

- [ ] **Step 4: Migration androidTest**

`Migration16To17Test.kt` (mirror `Migration15To16Test`: raw v16 DB via `SupportSQLiteOpenHelper`, open with `MIGRATION_16_17`, assert `plugin_repos` exists + is empty). Full content:
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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration16To17Test {
    private val dbName = "migration-16-17-test-${System.nanoTime()}.db"
    private val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Before fun deleteOldDb() { ctx.deleteDatabase(dbName) }
    @After fun cleanup() { ctx.deleteDatabase(dbName) }

    @Test
    fun migrate16To17_legtPluginReposTabelleAn_leer() {
        createV16Database()
        val db = Room.databaseBuilder(ctx, AppDatabase::class.java, dbName)
            .addMigrations(MIGRATION_16_17)
            .allowMainThreadQueries()
            .build()
        val c = db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM `plugin_repos`")
        c.moveToFirst()
        assertEquals(0, c.getInt(0))
        c.close()
        db.close()
    }

    /** Vollständige v16-DB (alle Tabellen im v16-Schema inkl. color_profiles.pluginPackage). */
    private fun createV16Database() {
        val config = SupportSQLiteOpenHelper.Configuration.builder(ctx)
            .name(dbName)
            .callback(object : SupportSQLiteOpenHelper.Callback(16) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL("CREATE TABLE IF NOT EXISTS `settings` (`key` TEXT NOT NULL, `value` TEXT NOT NULL, PRIMARY KEY(`key`))")
                    db.execSQL("CREATE TABLE IF NOT EXISTS `server` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `baseUrl` TEXT NOT NULL, `username` TEXT, `apiKeyCiphertext` TEXT, `apiKeyIv` TEXT, `passwordCiphertext` TEXT, `passwordIv` TEXT, `kind` TEXT NOT NULL DEFAULT 'KOMGA', `extrasCiphertext` TEXT, `extrasIv` TEXT)")
                    db.execSQL("CREATE TABLE IF NOT EXISTS `downloads` (`bookRemoteId` TEXT NOT NULL, `sourceId` INTEGER NOT NULL, `seriesRemoteId` TEXT NOT NULL, `title` TEXT NOT NULL, `format` TEXT NOT NULL, `localPath` TEXT NOT NULL, `totalPages` INTEGER NOT NULL, `seriesTitle` TEXT NOT NULL DEFAULT '', `seriesCoverUrl` TEXT, PRIMARY KEY(`bookRemoteId`))")
                    db.execSQL("CREATE TABLE IF NOT EXISTS `shelves` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `sources` TEXT NOT NULL, `defaultContentType` TEXT)")
                    db.execSQL("CREATE TABLE IF NOT EXISTS `series_overrides` (`sourceId` INTEGER NOT NULL, `seriesRemoteId` TEXT NOT NULL, `contentType` TEXT NOT NULL, PRIMARY KEY(`sourceId`, `seriesRemoteId`))")
                    db.execSQL("CREATE TABLE IF NOT EXISTS `read_progress` (`bookRemoteId` TEXT NOT NULL, `sourceId` INTEGER NOT NULL, `page` INTEGER NOT NULL, `completed` INTEGER NOT NULL, `totalPages` INTEGER NOT NULL, `dirty` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`bookRemoteId`))")
                    db.execSQL("CREATE TABLE IF NOT EXISTS `color_profiles` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `saturation` REAL NOT NULL, `contrast` REAL NOT NULL, `brightness` REAL NOT NULL, `blackPoint` REAL NOT NULL, `whitePoint` REAL NOT NULL, `gamma` REAL NOT NULL, `sharpenAmount` REAL NOT NULL, `sharpenRadius` INTEGER NOT NULL, `ditherMode` TEXT NOT NULL, `ditherLevels` INTEGER NOT NULL, `builtIn` INTEGER NOT NULL, `pluginPackage` TEXT)")
                    db.execSQL("CREATE TABLE IF NOT EXISTS `novel_progress` (`sourceId` INTEGER NOT NULL, `bookId` TEXT NOT NULL, `anchor` TEXT NOT NULL, `fraction` REAL NOT NULL, `dirty` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`sourceId`, `bookId`))")
                    db.execSQL("CREATE TABLE IF NOT EXISTS `collections` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `kind` TEXT NOT NULL)")
                    db.execSQL("CREATE TABLE IF NOT EXISTS `collection_members` (`rowId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `collectionId` INTEGER NOT NULL, `sourceId` INTEGER NOT NULL, `remoteId` TEXT NOT NULL, `title` TEXT NOT NULL, `position` INTEGER NOT NULL)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_collection_members_collectionId` ON `collection_members` (`collectionId`)")
                    db.execSQL("CREATE TABLE IF NOT EXISTS `collection_sync_links` (`collectionId` INTEGER NOT NULL, `sourceId` INTEGER NOT NULL, `remoteCollectionId` TEXT, `status` TEXT NOT NULL, `dirty` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`collectionId`, `sourceId`))")
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()
        FrameworkSQLiteOpenHelperFactory().create(config).writableDatabase.close()
    }
}
```
Before finalizing, READ `Migration15To16Test.kt` and reconcile any table-schema difference (it is the source of truth for the real v16 schema — the only delta vs v15 is `color_profiles.pluginPackage TEXT`, included above).

- [ ] **Step 5: Run migration test (emulator running)**

Run: `./gradlew :data:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.komgareader.data.db.Migration16To17Test`
Expected: PASS.

- [ ] **Step 6: data unit tests still green**

Run: `./gradlew :data:testDebugUnitTest`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add data/src/main/kotlin/com/komgareader/data/db/Entities.kt data/src/main/kotlin/com/komgareader/data/db/PluginRepoDao.kt data/src/main/kotlin/com/komgareader/data/db/AppDatabase.kt data/src/main/kotlin/com/komgareader/data/di/DataModule.kt data/src/androidTest/kotlin/com/komgareader/data/db/Migration16To17Test.kt
git commit -m "feat(data): plugin_repos table + migration 16->17"
```
End commit body with the Co-Authored-By trailer.

---

## Task 3: domain+data — official-repo toggle (settings) + `RepoStore` + defaults

**Files:**
- Modify: `domain/src/main/kotlin/com/komgareader/domain/repository/SettingsRepository.kt`
- Modify: `data/src/main/kotlin/com/komgareader/data/repository/RoomSettingsRepository.kt`
- Create: `data/src/main/kotlin/com/komgareader/data/plugin/repo/PluginRepoDefaults.kt`
- Create: `data/src/main/kotlin/com/komgareader/data/plugin/repo/RepoStore.kt`
- Modify: `data/src/main/kotlin/com/komgareader/data/di/DataModule.kt`
- Modify (test fallout): any `SettingsRepository` stub in `app`/`data` tests.

- [ ] **Step 1: Settings interface**

In `SettingsRepository.kt`, mirror the existing `deviceManagedRefresh` member exactly. Add to the interface:
```kotlin
    /** Ob das offizielle Plugin-Repo im Browser geladen wird (Default true). */
    val officialRepoEnabled: kotlinx.coroutines.flow.Flow<Boolean>
    suspend fun setOfficialRepoEnabled(enabled: Boolean)
```
(READ the file to match its import style for `Flow`; place near `deviceManagedRefresh`.)

- [ ] **Step 2: Settings impl**

In `RoomSettingsRepository.kt`, READ the `deviceManagedRefresh`/`setDeviceManagedRefresh` implementation and replicate it verbatim for the new flag with key `"official_repo_enabled"` and default `true`. (Same KV-table read/write helper the existing boolean uses — do not invent a new mechanism.)

- [ ] **Step 3: Defaults constant**

`PluginRepoDefaults.kt`:
```kotlin
package com.komgareader.data.plugin.repo

/**
 * Das offizielle Plugin-Repo. **Platzhalter** — wird vor dem echten Release auf die offizielle
 * Komga-Reader-Repo-URL gesetzt. Bewusst eine Konstante (kein DB-Seed): der spätere URL-Tausch
 * ist ein Ein-Zeilen-Edit, ohne Migrations-/Seed-Falle. Im Browser über das Settings-Flag
 * `official_repo_enabled` abschaltbar.
 */
object PluginRepoDefaults {
    const val OFFICIAL_NAME = "Komga Reader Official"
    const val OFFICIAL_URL = "https://example.invalid/komga-reader-plugins/repo.json"
}
```

- [ ] **Step 4: RepoStore**

`RepoStore.kt`:
```kotlin
package com.komgareader.data.plugin.repo

import com.komgareader.data.db.PluginRepoDao
import com.komgareader.data.db.PluginRepoEntity
import com.komgareader.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** Eine konfigurierte Repo-Quelle (offiziell oder vom Nutzer). [id] = null beim offiziellen Repo. */
data class RepoSource(val id: Long?, val url: String, val name: String, val official: Boolean)

/**
 * Verwaltet die aktiven Repo-Quellen: das offizielle Repo (Konstante + Toggle) plus die vom Nutzer
 * hinzugefügten ([PluginRepoDao]). Reine Verwaltung — kein Netz.
 */
class RepoStore(
    private val dao: PluginRepoDao,
    private val settings: SettingsRepository,
) {
    /** Alle aktiven Repos (offiziell zuerst, falls aktiviert), reaktiv. */
    fun observeActive(): Flow<List<RepoSource>> =
        combine(dao.observeAll(), settings.officialRepoEnabled) { userRepos, officialOn ->
            buildList {
                if (officialOn) add(RepoSource(null, PluginRepoDefaults.OFFICIAL_URL, PluginRepoDefaults.OFFICIAL_NAME, official = true))
                userRepos.forEach { add(RepoSource(it.id, it.url, it.name ?: it.url, official = false)) }
            }
        }

    suspend fun addUserRepo(url: String) {
        val trimmed = url.trim()
        if (trimmed.isNotBlank()) dao.insert(PluginRepoEntity(url = trimmed))
    }

    suspend fun removeUserRepo(id: Long) = dao.delete(id)

    suspend fun setOfficialEnabled(enabled: Boolean) = settings.setOfficialRepoEnabled(enabled)

    /** Nach erfolgreichem Index-Laden den lesbaren Repo-Namen für eine User-Repo-URL nachtragen. */
    suspend fun rememberName(url: String, name: String) = dao.setName(url, name.ifBlank { null })
}
```

- [ ] **Step 5: DI**

In `DataModule.kt`, add providers:
```kotlin
    @Provides @Singleton
    fun pluginRepoDao(db: AppDatabase): com.komgareader.data.db.PluginRepoDao = db.pluginRepoDao()

    @Provides @Singleton
    fun repoStore(
        dao: com.komgareader.data.db.PluginRepoDao,
        settings: SettingsRepository,
    ): com.komgareader.data.plugin.repo.RepoStore =
        com.komgareader.data.plugin.repo.RepoStore(dao, settings)
```

- [ ] **Step 6: Fix SettingsRepository stubs (compile fallout)**

Run `grep -rln "SettingsRepository" app/src/test data/src/test` and in each stub class implementing it add:
```kotlin
    override val officialRepoEnabled: kotlinx.coroutines.flow.Flow<Boolean> = kotlinx.coroutines.flow.flowOf(true)
    override suspend fun setOfficialRepoEnabled(enabled: Boolean) {}
```
(Match the stub's existing import/formatting style.)

- [ ] **Step 7: Build + tests**

Run: `./gradlew :domain:compileKotlin :data:testDebugUnitTest :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, tests PASS.

- [ ] **Step 8: Commit**

```bash
git add domain/src/main/kotlin/com/komgareader/domain/repository/SettingsRepository.kt data/src/main/kotlin/com/komgareader/data/repository/RoomSettingsRepository.kt data/src/main/kotlin/com/komgareader/data/plugin/repo/PluginRepoDefaults.kt data/src/main/kotlin/com/komgareader/data/plugin/repo/RepoStore.kt data/src/main/kotlin/com/komgareader/data/di/DataModule.kt
git add -A app/src/test data/src/test
git commit -m "feat(data): RepoStore (user repos + official toggle) + official-repo settings flag"
```
End commit body with the Co-Authored-By trailer.

---

## Task 4: data — `PluginRepoClient` (OkHttp fetch + download)

**Files:**
- Modify: `data/build.gradle.kts`
- Create: `data/src/main/kotlin/com/komgareader/data/plugin/repo/PluginRepoClient.kt`
- Test: `data/src/test/kotlin/com/komgareader/data/plugin/repo/PluginRepoClientTest.kt`

- [ ] **Step 1: OkHttp deps**

In `data/build.gradle.kts` `dependencies`: add `implementation(libs.okhttp)` and `testImplementation(libs.okhttp.mockwebserver)`.

- [ ] **Step 2: Failing test**

`PluginRepoClientTest.kt`:
```kotlin
package com.komgareader.data.plugin.repo

import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PluginRepoClientTest {
    private val server = MockWebServer()
    private val client = PluginRepoClient(OkHttpClient())

    @AfterTest fun tearDown() { server.shutdown() }

    @Test fun fetchIndexReturnsBody() = runTest {
        server.enqueue(MockResponse().setBody("""{"name":"R","plugins":[]}"""))
        server.start()
        val body = client.fetchIndex(server.url("/repo.json").toString())
        assertEquals("""{"name":"R","plugins":[]}""", body)
    }

    @Test fun fetchIndexReturnsNullOn404() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))
        server.start()
        assertNull(client.fetchIndex(server.url("/repo.json").toString()))
    }

    @Test fun downloadWritesBytesToFile() = runTest {
        server.enqueue(MockResponse().setBody("APKBYTES"))
        server.start()
        val dest = File.createTempFile("dl", ".apk").apply { deleteOnExit() }
        val ok = client.download(server.url("/a.apk").toString(), dest)
        assertTrue(ok)
        assertEquals("APKBYTES", dest.readText())
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `./gradlew :data:testDebugUnitTest --tests "com.komgareader.data.plugin.repo.PluginRepoClientTest"`
Expected: FAIL (unresolved `PluginRepoClient`).

- [ ] **Step 4: Implement**

`PluginRepoClient.kt`:
```kotlin
package com.komgareader.data.plugin.repo

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

/**
 * Lädt Repo-Indizes + APKs über HTTPS. Reines I/O (kein Parsen, keine Install-Logik) — die
 * Verifikation/Installation macht der [com.komgareader.app...PluginInstaller]. Alle Calls auf IO.
 */
class PluginRepoClient(private val http: OkHttpClient) {

    /** Lädt den Index-Text; null bei Netz-/HTTP-Fehler (Aufrufer überspringt das Repo). */
    suspend fun fetchIndex(url: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            http.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                resp.body?.string()
            }
        }.getOrNull()
    }

    /** Lädt [url] nach [dest]; true bei Erfolg. Bei Fehler wird [dest] gelöscht. */
    suspend fun download(url: String, dest: File): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            http.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                if (!resp.isSuccessful) return@use false
                val body = resp.body ?: return@use false
                dest.outputStream().use { out -> body.byteStream().copyTo(out) }
                true
            }
        }.getOrElse { dest.delete(); false }
    }
}
```

Also add the DI provider in `DataModule.kt`:
```kotlin
    @Provides @Singleton
    fun pluginRepoClient(): com.komgareader.data.plugin.repo.PluginRepoClient =
        com.komgareader.data.plugin.repo.PluginRepoClient(okhttp3.OkHttpClient())
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew :data:testDebugUnitTest --tests "com.komgareader.data.plugin.repo.PluginRepoClientTest"`
Expected: PASS (3 tests).

- [ ] **Step 6: Commit**

```bash
git add data/build.gradle.kts data/src/main/kotlin/com/komgareader/data/plugin/repo/PluginRepoClient.kt data/src/main/kotlin/com/komgareader/data/di/DataModule.kt data/src/test/kotlin/com/komgareader/data/plugin/repo/PluginRepoClientTest.kt
git commit -m "feat(data): PluginRepoClient (OkHttp index fetch + APK download)"
```
End commit body with the Co-Authored-By trailer.

---

## Task 5: app — `PluginInstaller` (cert verify + PackageInstaller) + manifest permission

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/kotlin/com/komgareader/app/ui/plugins/repo/PluginInstaller.kt`

- [ ] **Step 1: Manifest permission**

In `AndroidManifest.xml`, next to the existing `<uses-permission>` lines add:
```xml
    <uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />
```

- [ ] **Step 2: Implement PluginInstaller**

`PluginInstaller.kt`:
```kotlin
package com.komgareader.app.ui.plugins.repo

import android.content.Context
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import com.komgareader.data.plugin.repo.fingerprintMatches
import com.komgareader.plugin.host.PluginSignature
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/** Ergebnis eines Install-Versuchs (für die UI). */
sealed interface InstallResult {
    data object SessionStarted : InstallResult   // OS-Dialog läuft; Endzustand via Tab-Re-Scan
    data object FingerprintMismatch : InstallResult
    data object Failed : InstallResult
}

/**
 * Verifiziert die Signatur eines heruntergeladenen APK gegen den erwarteten Fingerprint und startet
 * dann eine [PackageInstaller]-Session (OS zeigt den Installationsdialog). Liest NIE Plugin-Code.
 * Sicherheits-Gate: ohne Fingerprint-Match wird nichts installiert und die Datei gelöscht.
 */
class PluginInstaller @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** SHA-256 des Signaturzertifikats einer noch-nicht-installierten APK-Datei (API 28+). */
    private fun apkCertSha256(apk: File): String? = runCatching {
        val info = context.packageManager.getPackageArchiveInfo(
            apk.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES,
        ) ?: return null
        val cert = info.signingInfo?.apkContentsSigners?.firstOrNull() ?: return null
        PluginSignature.sha256(cert.toByteArray())
    }.getOrNull()

    /**
     * Verifiziert [apk] gegen [expectedFingerprint]; bei Match → PackageInstaller-Session committen
     * (OS-Dialog). Bei Mismatch/Fehler wird [apk] gelöscht.
     */
    suspend fun verifyAndInstall(apk: File, expectedFingerprint: String): InstallResult =
        withContext(Dispatchers.IO) {
            val actual = apkCertSha256(apk)
            if (actual == null || !fingerprintMatches(expectedFingerprint, actual)) {
                apk.delete()
                return@withContext if (actual == null) InstallResult.Failed else InstallResult.FingerprintMismatch
            }
            runCatching {
                val pi = context.packageManager.packageInstaller
                val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
                val sessionId = pi.createSession(params)
                pi.openSession(sessionId).use { session ->
                    session.openWrite("plugin.apk", 0, apk.length()).use { out ->
                        apk.inputStream().use { it.copyTo(out) }
                        session.fsync(out)
                    }
                    val intent = android.content.Intent(context, PluginInstallReceiver::class.java)
                    val flags = android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_MUTABLE
                    val pending = android.app.PendingIntent.getBroadcast(context, sessionId, intent, flags)
                    session.commit(pending.intentSender)
                }
                InstallResult.SessionStarted
            }.getOrElse { apk.delete(); InstallResult.Failed }
        }
}
```

Also create the broadcast receiver `app/src/main/kotlin/com/komgareader/app/ui/plugins/repo/PluginInstallReceiver.kt`:
```kotlin
package com.komgareader.app.ui.plugins.repo

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller

/**
 * Empfängt das PackageInstaller-Session-Resultat. Bei STATUS_PENDING_USER_ACTION muss der vom OS
 * gelieferte Bestätigungs-Intent gestartet werden (zeigt den Installationsdialog). Der Endzustand
 * (installiert/abgebrochen) wird NICHT hier ausgewertet — der Plugin-Tab/Browser scannt beim
 * onResume neu (kein verlässliches Cross-Process-Callback).
 */
class PluginInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            @Suppress("DEPRECATION")
            val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
            confirm?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            confirm?.let { context.startActivity(it) }
        }
    }
}
```

Register the receiver in `AndroidManifest.xml` inside `<application>`:
```xml
        <receiver
            android:name=".ui.plugins.repo.PluginInstallReceiver"
            android:exported="false" />
```

- [ ] **Step 3: Build**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL. (No unit test — the security decision is the pure `fingerprintMatches` from Task 1, already tested; the PackageInstaller orchestration is verified in the E2E.)

- [ ] **Step 4: Commit**

```bash
git add app/src/main/AndroidManifest.xml app/src/main/kotlin/com/komgareader/app/ui/plugins/repo/PluginInstaller.kt app/src/main/kotlin/com/komgareader/app/ui/plugins/repo/PluginInstallReceiver.kt
git commit -m "feat(app): PluginInstaller — verify APK fingerprint then PackageInstaller session"
```
End commit body with the Co-Authored-By trailer.

---

## Task 6: app — `RepoBrowserViewModel`

**Files:**
- Create: `app/src/main/kotlin/com/komgareader/app/ui/plugins/repo/RepoBrowserViewModel.kt`

- [ ] **Step 1: Implement**

```kotlin
package com.komgareader.app.ui.plugins.repo

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.pm.PackageInfoCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.komgareader.data.plugin.repo.BrowsableEntry
import com.komgareader.data.plugin.repo.InstallState
import com.komgareader.data.plugin.repo.PluginRepoClient
import com.komgareader.data.plugin.repo.RepoSource
import com.komgareader.data.plugin.repo.RepoStore
import com.komgareader.data.plugin.repo.installState
import com.komgareader.data.plugin.repo.mergeRepoEntries
import com.komgareader.data.plugin.repo.parseRepoIndex
import com.komgareader.data.plugin.repo.pluginKindOf
import com.komgareader.data.plugin.repo.resolveApkUrl
import com.komgareader.plugin.PluginAbi
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/** Eine Browser-Zeile: gemergter Eintrag + abgeleiteter Install-Zustand. */
data class BrowserRow(
    val item: BrowsableEntry,
    val state: InstallState,
    val compatible: Boolean,
)

@HiltViewModel
class RepoBrowserViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repoStore: RepoStore,
    private val client: PluginRepoClient,
    private val installer: PluginInstaller,
) : ViewModel() {

    val repos: StateFlow<List<RepoSource>> =
        repoStore.observeActive().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _rows = MutableStateFlow<List<BrowserRow>>(emptyList())
    val rows: StateFlow<List<BrowserRow>> = _rows.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()
    fun setQuery(q: String) { _query.value = q }

    /** Lädt alle aktiven Repo-Indizes, merged + berechnet den Install-Zustand. */
    fun refresh() = viewModelScope.launch {
        _loading.value = true
        val sources = repoStore.observeActive().first()
        val collected = mutableListOf<BrowsableEntry>()
        for (src in sources) {
            val body = client.fetchIndex(src.url) ?: continue
            val index = parseRepoIndex(body) ?: continue
            if (!src.official) repoStore.rememberName(src.url, index.name)
            val repoLabel = index.name.ifBlank { src.name }
            index.plugins.forEach {
                collected += BrowsableEntry(it, repoLabel, src.url, pluginKindOf(it.type))
            }
        }
        val merged = mergeRepoEntries(collected)
        _rows.value = withContext(Dispatchers.IO) { merged.map { it.toRow() } }
        _loading.value = false
    }.let {}

    private fun BrowsableEntry.toRow(): BrowserRow {
        val installed = runCatching {
            PackageInfoCompat.getLongVersionCode(
                context.packageManager.getPackageInfo(entry.packageName, 0),
            )
        }.getOrNull()
        val compatible = entry.abiVersion in PluginAbi.MIN_SUPPORTED..PluginAbi.VERSION
        return BrowserRow(this, installState(entry.versionCode, installed), compatible)
    }

    /** Lädt das APK, verifiziert den Fingerprint, startet die Installation (OS-Dialog). */
    fun install(row: BrowserRow) = viewModelScope.launch {
        _error.value = null
        val url = resolveApkUrl(row.item.repoUrl, row.item.entry.apkUrl)
        val dir = File(context.cacheDir, "plugin-repo").apply { mkdirs() }
        val dest = File(dir, "${row.item.entry.packageName}-${row.item.entry.versionCode}.apk")
        val ok = client.download(url, dest)
        if (!ok) { _error.value = "download"; return@launch }
        when (installer.verifyAndInstall(dest, row.item.entry.fingerprint)) {
            is InstallResult.FingerprintMismatch -> _error.value = "fingerprint"
            is InstallResult.Failed -> _error.value = "install"
            is InstallResult.SessionStarted -> Unit // OS-Dialog; Re-Scan beim onResume
        }
    }.let {}

    fun addRepo(url: String) = viewModelScope.launch { repoStore.addUserRepo(url); refresh() }.let {}
    fun removeRepo(id: Long) = viewModelScope.launch { repoStore.removeUserRepo(id); refresh() }.let {}
    fun setOfficialEnabled(enabled: Boolean) = viewModelScope.launch { repoStore.setOfficialEnabled(enabled); refresh() }.let {}
    fun dismissError() { _error.value = null }
}
```

- [ ] **Step 2: Build**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL. If `PackageInfoCompat` is unresolved, confirm `androidx.core:core-ktx` is a dependency (it is, app-wide) — the import is `androidx.core.content.pm.PackageInfoCompat`.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/komgareader/app/ui/plugins/repo/RepoBrowserViewModel.kt
git commit -m "feat(app): RepoBrowserViewModel — load/merge indices, install state, install flow"
```
End commit body with the Co-Authored-By trailer.

---

## Task 7: app — `RepoBrowserScreen`

**Files:**
- Create: `app/src/main/kotlin/com/komgareader/app/ui/plugins/repo/RepoBrowserScreen.kt`

- [ ] **Step 1: Implement**

Use the E-Ink components/patterns from P1 (`SubPageScaffold` or the project's standard scaffold for pushed pages, `EinkModal`, `EinkInfoDialog`, `EinkTokens.hairline`, `AppIcons`, `LoadingIndicator`, `OutlinedTextField`). READ `app/src/main/kotlin/com/komgareader/app/ui/plugins/PluginsScreen.kt` (P1) and the settings screens to match the pushed-page scaffold + search-field conventions used elsewhere; mirror them. The screen:

```kotlin
package com.komgareader.app.ui.plugins.repo

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.komgareader.app.i18n.LocalStrings
import com.komgareader.app.ui.components.EinkInfoDialog
import com.komgareader.app.ui.components.EinkModal
import com.komgareader.app.ui.components.EinkToggle
import com.komgareader.app.ui.components.LoadingIndicator
import com.komgareader.app.ui.icons.AppIcons
import com.komgareader.app.ui.theme.EinkTokens
import com.komgareader.data.plugin.repo.InstallState
import com.komgareader.data.plugin.repo.PluginKind

@Composable
fun RepoBrowserScreen(onBack: () -> Unit, viewModel: RepoBrowserViewModel = hiltViewModel()) {
    val s = LocalStrings.current
    val rows by viewModel.rows.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val query by viewModel.query.collectAsState()
    val repos by viewModel.repos.collectAsState()
    val error by viewModel.error.collectAsState()
    var showRepoMgmt by remember { mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(Unit) { viewModel.refresh() }

    // SubPageScaffold liefert TopBar mit Titel + Zurück; READ einen bestehenden Aufrufer für die exakte Signatur.
    com.komgareader.app.ui.components.SubPageScaffold(title = s.repoBrowserTitle, onBack = onBack) {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = query, onValueChange = viewModel::setQuery,
                    label = { Text(s.repoBrowserSearch) }, singleLine = true, modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { showRepoMgmt = true }) {
                    Icon(AppIcons.Settings, contentDescription = s.repoBrowserManage, modifier = Modifier.size(22.dp))
                }
                IconButton(onClick = { viewModel.refresh() }) {
                    Icon(AppIcons.Refresh, contentDescription = s.refresh, modifier = Modifier.size(22.dp))
                }
            }
            if (loading) LoadingIndicator()
            val filtered = rows.filter {
                query.isBlank() ||
                    it.item.entry.name.contains(query, true) ||
                    it.item.entry.description.contains(query, true)
            }
            if (!loading && filtered.isEmpty()) {
                Text(s.repoBrowserEmpty, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filtered, key = { it.item.entry.packageName }) { row ->
                    RepoRow(
                        row = row,
                        typeLabel = if (row.item.kind == PluginKind.PRESET) s.pluginTabPresetLabel else s.pluginTabSourceLabel,
                        abiLabel = s.pluginAbiLabel,
                        installLabel = s.repoBrowserInstall,
                        updateLabel = s.repoBrowserUpdate,
                        installedLabel = s.pluginPresetImported,
                        incompatibleLabel = s.repoBrowserIncompatible,
                        onInstall = { viewModel.install(row) },
                    )
                }
            }
        }
    }

    if (showRepoMgmt) {
        RepoManagementModal(
            repos = repos,
            onDismiss = { showRepoMgmt = false },
            onAdd = viewModel::addRepo,
            onRemove = viewModel::removeRepo,
            onToggleOfficial = viewModel::setOfficialEnabled,
        )
    }

    error?.let { code ->
        val msg = when (code) {
            "fingerprint" -> s.repoBrowserErrorFingerprint
            "download" -> s.repoBrowserErrorDownload
            else -> s.repoBrowserErrorInstall
        }
        EinkInfoDialog(title = s.repoBrowserErrorTitle, onDismiss = { viewModel.dismissError() }, closeLabel = s.close) {
            Text(msg, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
```

Plus the private `RepoRow` (mirror P1 `PluginRow`: name + "Typ · v{versionName} · ABI {n} · {repoName}" + a state-dependent trailing control: `NOT_INSTALLED`→Install icon-button (`AppIcons.Download`), `UPDATE_AVAILABLE`→Update button (`AppIcons.Download` + update label), `INSTALLED`→`installedLabel` text, incompatible→`incompatibleLabel` text) and `RepoManagementModal` (an `EinkModal`: a row with `EinkToggle` for the official repo, a list of user repos each with a 🗑, and an `OutlinedTextField` + add button to add a URL). Keep both flat/1.5px-border/no-animation.

> If `SubPageScaffold`, `LoadingIndicator`, `EinkToggle`, or `AppIcons.Refresh` have different names, READ the components package (`app/src/main/kotlin/com/komgareader/app/ui/components/`) and `AppIcons.kt` and use the real names. Do not invent component names.

- [ ] **Step 2: Build**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL. Fix any component/icon name to the real one found by grep.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/komgareader/app/ui/plugins/repo/RepoBrowserScreen.kt
git commit -m "feat(app): RepoBrowserScreen — search, repo management, install/update rows"
```
End commit body with the Co-Authored-By trailer.

---

## Task 8: app — wire the route + the Plugins-tab entry point

**Files:**
- Modify: `app/src/main/kotlin/com/komgareader/app/MainActivity.kt`
- Modify: `app/src/main/kotlin/com/komgareader/app/ui/home/HomeScreen.kt`
- Modify: `app/src/main/kotlin/com/komgareader/app/ui/plugins/PluginsScreen.kt`

- [ ] **Step 1: NavHost route**

In `MainActivity.kt`, inside the `NavHost { ... }` (next to `composable("settings")`), add:
```kotlin
                        composable("plugin-repos") {
                            RepoBrowserScreen(onBack = { nav.popBackStack() })
                        }
```
Add the import `import com.komgareader.app.ui.plugins.repo.RepoBrowserScreen`. In the `composable("home") { HomeScreen(...) }` call, add the parameter:
```kotlin
                                onOpenRepoBrowser = { nav.navigate("plugin-repos") },
```

- [ ] **Step 2: HomeScreen threads the callback**

In `HomeScreen.kt`: add `onOpenRepoBrowser: () -> Unit` to the `HomeScreen(...)` signature (next to `onOpenSeries`/`onOpenGroup`). At the `TAB_PLUGINS -> PluginsScreen()` call, change to `TAB_PLUGINS -> PluginsScreen(onOpenRepoBrowser = onOpenRepoBrowser)`.

- [ ] **Step 3: PluginsScreen entry button**

In `PluginsScreen.kt`: add `onOpenRepoBrowser: () -> Unit = {}` to the signature (default keeps any preview working). Replace the existing `pluginTabReposHint` `Text` with a tappable discover entry — an `EinkOutlinedButton` (or the project's standard button; READ which button P1/Settings use) labelled `s.repoBrowserOpen` that calls `onOpenRepoBrowser`. Keep it flat, no animation.

- [ ] **Step 4: Build**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/komgareader/app/MainActivity.kt app/src/main/kotlin/com/komgareader/app/ui/home/HomeScreen.kt app/src/main/kotlin/com/komgareader/app/ui/plugins/PluginsScreen.kt
git commit -m "feat(app): wire plugin-repos route + discover entry from Plugins tab"
```
End commit body with the Co-Authored-By trailer.

---

## Task 9: app — i18n keys

**Files:**
- Modify: `app/src/main/kotlin/com/komgareader/app/i18n/Strings.kt`

- [ ] **Step 1: Add keys to interface + both locale objects**

READ `Strings.kt` first. Add these to `interface Strings`, `object De`, `object En` (keep parity — same keys in all three). Reuse existing `refresh`/`close`/`cancel`/`save` if present (grep before adding; only add what's missing):
```kotlin
    // interface
    val repoBrowserTitle: String
    val repoBrowserOpen: String
    val repoBrowserSearch: String
    val repoBrowserManage: String
    val repoBrowserEmpty: String
    val repoBrowserInstall: String
    val repoBrowserUpdate: String
    val repoBrowserIncompatible: String
    val repoBrowserAddRepo: String
    val repoBrowserOfficial: String
    val repoBrowserRepoUrl: String
    val repoBrowserErrorTitle: String
    val repoBrowserErrorFingerprint: String
    val repoBrowserErrorDownload: String
    val repoBrowserErrorInstall: String
```
DE:
```kotlin
    override val repoBrowserTitle = "Plugins entdecken"
    override val repoBrowserOpen = "Plugins entdecken"
    override val repoBrowserSearch = "Plugins durchsuchen"
    override val repoBrowserManage = "Repos verwalten"
    override val repoBrowserEmpty = "Keine Plugins gefunden."
    override val repoBrowserInstall = "Installieren"
    override val repoBrowserUpdate = "Update"
    override val repoBrowserIncompatible = "Inkompatibel"
    override val repoBrowserAddRepo = "Repo hinzufügen"
    override val repoBrowserOfficial = "Offizielles Repo"
    override val repoBrowserRepoUrl = "Repo-URL"
    override val repoBrowserErrorTitle = "Installation fehlgeschlagen"
    override val repoBrowserErrorFingerprint = "Signatur passt nicht zum Repo-Eintrag — Installation abgebrochen."
    override val repoBrowserErrorDownload = "Download fehlgeschlagen."
    override val repoBrowserErrorInstall = "Installation konnte nicht gestartet werden."
```
EN:
```kotlin
    override val repoBrowserTitle = "Discover plugins"
    override val repoBrowserOpen = "Discover plugins"
    override val repoBrowserSearch = "Search plugins"
    override val repoBrowserManage = "Manage repos"
    override val repoBrowserEmpty = "No plugins found."
    override val repoBrowserInstall = "Install"
    override val repoBrowserUpdate = "Update"
    override val repoBrowserIncompatible = "Incompatible"
    override val repoBrowserAddRepo = "Add repo"
    override val repoBrowserOfficial = "Official repo"
    override val repoBrowserRepoUrl = "Repo URL"
    override val repoBrowserErrorTitle = "Installation failed"
    override val repoBrowserErrorFingerprint = "Signature does not match the repo entry — installation aborted."
    override val repoBrowserErrorDownload = "Download failed."
    override val repoBrowserErrorInstall = "Could not start installation."
```
If `refresh` is not already a key (grep), add `repoBrowserRefresh`/use existing; do NOT duplicate an existing key.

- [ ] **Step 2: Build (parity enforced by compiler)**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/komgareader/app/i18n/Strings.kt
git commit -m "i18n: repo-browser strings (DE+EN)"
```
End commit body with the Co-Authored-By trailer.

---

## Task 10: E2E on the emulator (local test repo)

Verifies the whole chain against a local repo server + the P1 Kindle preset APK.

- [ ] **Step 1: Build the test APK + a local repo**

Reuse the P1 example plugin APK at `plugin/komga-eink-preset-kindle/app/build/outputs/apk/debug/app-debug.apk` (rebuild if missing: `cd plugin/komga-eink-preset-kindle && ./gradlew :app:assembleDebug`).

Get its real signing fingerprint (the SHA-256 the index must declare):
```bash
apksigner verify --print-certs <apk> | grep -i "SHA-256" || keytool -printcert -jarfile <apk> | grep -i "SHA256"
```
(If `apksigner`/`keytool` unavailable, install via the SDK build-tools; the value is the cert SHA-256, colons OK.)

Create `/tmp/komga-repo/repo.json`:
```json
{ "name": "Local Test", "plugins": [
  { "packageName": "com.komgareader.preset.kindle", "name": "Kindle E-Ink Presets", "description": "Test", "type": "preset", "abiVersion": 1, "versionCode": 1, "versionName": "1.0", "apkUrl": "kindle.apk", "fingerprint": "<REAL_SHA256>" }
]}
```
Copy the APK to `/tmp/komga-repo/kindle.apk`. Serve it on the emulator-reachable host (`10.0.2.2`):
```bash
cd /tmp/komga-repo && python3 -m http.server 8077 &
```
Point the official URL at it: temporarily set `PluginRepoDefaults.OFFICIAL_URL = "http://10.0.2.2:8077/repo.json"` (revert before final commit — note in report) **OR** add it as a user repo via the UI (cleaner — no code edit). Prefer adding via UI in step 3.

Note: HTTP (not HTTPS) to `10.0.2.2` needs cleartext allowed. The emulator debug build: add `android:usesCleartextTraffic="true"` to the debug manifest OR a network-security-config exception for `10.0.2.2`. Add a `app/src/debug/AndroidManifest.xml` with `usesCleartextTraffic="true"` (debug-only — do NOT enable in release). Document this.

- [ ] **Step 2: Uninstall the preset plugin first (clean slate)**

```bash
adb uninstall com.komgareader.preset.kindle
./gradlew :app:installDebug
```

- [ ] **Step 3: Drive the flow**

Launch app → Plugins tab → "Plugins entdecken" → (Repos verwalten → add `http://10.0.2.2:8077/repo.json` if not using the official slot) → the list shows "Kindle E-Ink Presets · Preset · v1.0 · ABI 1" with **Installieren** → tap → OS install dialog → confirm → return → Plugins tab `onResume` shows it installed; repo browser row now shows **Installiert**. Screenshot each step.

- [ ] **Step 4: Negative + update cases**

- Fingerprint mismatch: edit `repo.json` fingerprint to a wrong value, refresh, Install → expect the **„Signatur passt nicht"**-Fehlerdialog, no install.
- Update: bump `versionCode` to 2 in `repo.json` (+ keep APK), refresh → row shows **Update** (since installed v1 < index v2).

- [ ] **Step 5: Revert any test-only code edit**

If `OFFICIAL_URL` was edited, revert it to the placeholder. Keep the debug `usesCleartextTraffic` manifest (it's debug-only and harmless) OR revert it — note the choice. Run full unit suite:
```bash
./gradlew :plugin-host:testDebugUnitTest :domain:test :data:testDebugUnitTest :app:testDebugUnitTest
```
Expected: PASS.

- [ ] **Step 6: Commit (if any debug-manifest/file remains)**

```bash
git add -A
git commit -m "test(app): debug cleartext manifest for local plugin-repo E2E"
```
End commit body with the Co-Authored-By trailer. (Skip if nothing to commit.)

---

## Task 11: docs-match-code + memory

- [ ] **Step 1: Update the rule**

In `.claude/rules/big-picture-and-goals.md`, update the Plugin-Typ (c)/(a) section: the repo-browser + APK download/install (P2) is now **Ist** (was "Soll (Slice P2)"). Note remaining Soll: auto-refresh/badge, plugin icons.

- [ ] **Step 2: Memory**

Update `plugin-host-kavita.md`: add that Slice P2 (repo browser, official+user repos, fingerprint-verified PackageInstaller, update detection, migration 16→17) is built + E2E-verified. Adjust the `MEMORY.md` pointer line. (Memory files are outside the repo — no repo commit.)

- [ ] **Step 3: Commit the rule**

```bash
git add .claude/rules/big-picture-and-goals.md
git commit -m "docs(rules): plugin repo-browser + install (P2) as Ist (docs-match-code)"
```
End commit body with the Co-Authored-By trailer.

---

## Self-Review

**Spec coverage:**
- Official + user repos → Task 3 (`RepoStore`, official toggle, `PluginRepoDefaults`). ✓
- Browse + search → Task 6 (load/merge) + Task 7 (search field/list). ✓
- Install (download→verify→PackageInstaller) → Task 4 (download) + Task 5 (verify+install) + Task 6 (orchestration). ✓
- Update detection → `installState` (Task 1) + row state (Task 6/7). ✓
- `repo.json` schema → Task 1 models + parser. ✓
- Fingerprint verify before install → Task 5 (`verifyAndInstall`) + Task 1 (`fingerprintMatches`). ✓
- Relative apkUrl → `resolveApkUrl` (Task 1). ✓
- Persistence/migration 16→17 → Task 2. ✓
- `REQUEST_INSTALL_PACKAGES` → Task 5. ✓
- UI (E-Ink, repo mgmt modal, states, errors, loading) → Task 7. ✓
- `+` entry from Plugins tab → Task 8. ✓
- i18n → Task 9. ✓
- Tests: pure (Task 1), client (Task 4), migration (Task 2), E2E incl. mismatch+update (Task 10). ✓
- Out of scope (auto-refresh/badge, icons, index signing) → not built. ✓

**Type consistency:** `RepoIndex`/`RepoPluginEntry`/`PluginKind`/`InstallState`/`BrowsableEntry` (Task 1) used identically in Tasks 6/7. `RepoStore.observeActive(): Flow<List<RepoSource>>`, `addUserRepo`/`removeUserRepo`/`setOfficialEnabled`/`rememberName` consistent across Tasks 3/6. `PluginRepoClient.fetchIndex`/`download` consistent (Tasks 4/6). `PluginInstaller.verifyAndInstall` + `InstallResult` consistent (Tasks 5/6). `installState`/`fingerprintMatches`/`resolveApkUrl`/`mergeRepoEntries`/`pluginKindOf` signatures stable. DB version 17 + `MIGRATION_16_17` consistent (Task 2). i18n keys referenced in Task 7 are all defined in Task 9.

**Placeholder scan:** No "TBD"/"handle errors" — every code step has concrete code; UI-component name uncertainties are explicitly resolved via "READ the components package and use the real names" (the only safe instruction when the exact scaffold/button names must match an existing file). E2E fingerprint value is obtained via a concrete command.
