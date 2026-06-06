# Phase 1 · Plan 4c/… — Persistenz-Fundament (Hilt + Room)

> REQUIRED SUB-SKILL: subagent-driven-development / executing-plans. Checkbox steps.

**Goal:** Komga-Server-Verbindung (`ServerConfig`) und App-Einstellungen (Theme, Sprache) dauerhaft speichern, über Hilt injizierbar. Instrumented-getestet auf dem Emulator (echtes Room).

**Architecture:** Neues Android-Lib-Modul `:data` mit Room (Entities + DAO + DB) und Repository-Impls; Domain bekommt schlanke Repository-Interfaces. Hilt verdrahtet alles; `:app` wird Hilt-fähig. Volle Bibliotheks-Spiegelung (Series/Books-Cache) bewusst später (1.4d+).

**Tech Stack:** Room 2.6.1 · Hilt 2.52 · KSP 2.0.21-1.0.25 · androidx.test (Instrumented).

## Versionen (Catalog erweitern)
`[versions]`: `room = "2.6.1"`, `hilt = "2.52"`, `ksp = "2.0.21-1.0.25"`, `hiltNavigationCompose = "1.2.0"`.
`[libraries]`: `room-runtime`(androidx.room:room-runtime), `room-ktx`(androidx.room:room-ktx), `room-compiler`(androidx.room:room-compiler), `hilt-android`(com.google.dagger:hilt-android), `hilt-compiler`(com.google.dagger:hilt-android-compiler), `hilt-navigation-compose`(androidx.hilt:hilt-navigation-compose:1.2.0).
`[plugins]`: `ksp`(com.google.devtools.ksp ref ksp), `hilt`(com.google.dagger.hilt.android ref hilt). Root `build.gradle.kts`: beide `apply false` ergänzen.

---

### Task 0: Domain — Repository-Interfaces

**Files:** Create `domain/src/main/kotlin/com/komgareader/domain/repository/SettingsRepository.kt`, `ServerRepository.kt`.

- [ ] **Step 1**

`SettingsRepository.kt`:
```kotlin
package com.komgareader.domain.repository

import kotlinx.coroutines.flow.Flow

/** App-weite Einstellungen (Theme, Sprache) als Strings (UI-neutral). */
interface SettingsRepository {
    val themeMode: Flow<String>   // "LIGHT" | "DARK" | "SYSTEM"
    val language: Flow<String>    // "de" | "en"
    suspend fun setThemeMode(value: String)
    suspend fun setLanguage(value: String)
}
```

`ServerRepository.kt`:
```kotlin
package com.komgareader.domain.repository

import kotlinx.coroutines.flow.Flow

/** Persistierte Komga-Verbindung. Genau eine im MVP (null = nicht konfiguriert). */
data class ServerConfig(val name: String, val baseUrl: String, val apiKey: String)

interface ServerRepository {
    val config: Flow<ServerConfig?>
    suspend fun save(config: ServerConfig)
    suspend fun clear()
}
```

- [ ] **Step 2** `./gradlew :domain:test` → 17 grün. Commit: `feat(domain): Settings-/Server-Repository-Interfaces`.

---

### Task 1: `:data`-Modul (Room) anlegen

**Files:** settings.gradle.kts (`include(":data")`), `data/build.gradle.kts`, `data/src/main/AndroidManifest.xml`.

- [ ] **Step 1** `data/build.gradle.kts`:
```kotlin
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}
android {
    namespace = "com.komgareader.data"
    compileSdk = 34
    defaultConfig {
        minSdk = 28
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation(project(":domain"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
```
`AndroidManifest.xml`: `<manifest />`.

- [ ] **Step 2** `./gradlew :data:assembleDebug` → SUCCESSFUL. Commit: `build: :data-Modul (Room + Hilt)`.

---

### Task 2: Room — Entities, DAO, Database

**Files:** Create under `data/src/main/kotlin/com/komgareader/data/db/`: `Entities.kt`, `SettingsDao.kt`, `ServerDao.kt`, `AppDatabase.kt`.

- [ ] **Step 1** `Entities.kt`:
```kotlin
package com.komgareader.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Key-Value-Settings (id = Key). */
@Entity(tableName = "settings")
data class SettingEntity(@PrimaryKey val key: String, val value: String)

/** Einzelne Server-Verbindung (id fix = 1 im MVP). */
@Entity(tableName = "server")
data class ServerEntity(
    @PrimaryKey val id: Int = 1,
    val name: String,
    val baseUrl: String,
    val apiKey: String,
)
```

`SettingsDao.kt`:
```kotlin
package com.komgareader.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SettingsDao {
    @Query("SELECT value FROM settings WHERE key = :key")
    fun observe(key: String): Flow<String?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entity: SettingEntity)
}
```

`ServerDao.kt`:
```kotlin
package com.komgareader.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ServerDao {
    @Query("SELECT * FROM server WHERE id = 1")
    fun observe(): Flow<ServerEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(entity: ServerEntity)

    @Query("DELETE FROM server")
    suspend fun clear()
}
```

`AppDatabase.kt`:
```kotlin
package com.komgareader.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [SettingEntity::class, ServerEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun settingsDao(): SettingsDao
    abstract fun serverDao(): ServerDao
}
```

- [ ] **Step 2** `./gradlew :data:assembleDebug` → SUCCESSFUL (Room-Codegen läuft). Commit: `feat(data): Room Entities/DAO/Database`.

---

### Task 3: Repository-Impls

**Files:** Create `data/src/main/kotlin/com/komgareader/data/repository/RoomSettingsRepository.kt`, `RoomServerRepository.kt`.

- [ ] **Step 1** `RoomSettingsRepository.kt`:
```kotlin
package com.komgareader.data.repository

import com.komgareader.data.db.SettingEntity
import com.komgareader.data.db.SettingsDao
import com.komgareader.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomSettingsRepository(private val dao: SettingsDao) : SettingsRepository {
    override val themeMode: Flow<String> = dao.observe(KEY_THEME).map { it ?: "SYSTEM" }
    override val language: Flow<String> = dao.observe(KEY_LANG).map { it ?: "de" }
    override suspend fun setThemeMode(value: String) = dao.put(SettingEntity(KEY_THEME, value))
    override suspend fun setLanguage(value: String) = dao.put(SettingEntity(KEY_LANG, value))

    private companion object { const val KEY_THEME = "theme_mode"; const val KEY_LANG = "language" }
}
```

`RoomServerRepository.kt`:
```kotlin
package com.komgareader.data.repository

import com.komgareader.data.db.ServerDao
import com.komgareader.data.db.ServerEntity
import com.komgareader.domain.repository.ServerConfig
import com.komgareader.domain.repository.ServerRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomServerRepository(private val dao: ServerDao) : ServerRepository {
    override val config: Flow<ServerConfig?> = dao.observe().map { e ->
        e?.let { ServerConfig(it.name, it.baseUrl, it.apiKey) }
    }
    override suspend fun save(config: ServerConfig) =
        dao.save(ServerEntity(name = config.name, baseUrl = config.baseUrl, apiKey = config.apiKey))
    override suspend fun clear() = dao.clear()
}
```

- [ ] **Step 2** `./gradlew :data:assembleDebug` → SUCCESSFUL. Commit: `feat(data): Room-Repository-Impls`.

---

### Task 4: Hilt-Modul (DI-Verdrahtung)

**Files:** Create `data/src/main/kotlin/com/komgareader/data/di/DataModule.kt`.

- [ ] **Step 1**
```kotlin
package com.komgareader.data.di

import android.content.Context
import androidx.room.Room
import com.komgareader.data.db.AppDatabase
import com.komgareader.data.repository.RoomServerRepository
import com.komgareader.data.repository.RoomSettingsRepository
import com.komgareader.domain.repository.ServerRepository
import com.komgareader.domain.repository.SettingsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides @Singleton
    fun database(@ApplicationContext ctx: Context): AppDatabase =
        Room.databaseBuilder(ctx, AppDatabase::class.java, "komga-reader.db").build()

    @Provides @Singleton
    fun settingsRepository(db: AppDatabase): SettingsRepository = RoomSettingsRepository(db.settingsDao())

    @Provides @Singleton
    fun serverRepository(db: AppDatabase): ServerRepository = RoomServerRepository(db.serverDao())
}
```

- [ ] **Step 2** `./gradlew :data:assembleDebug` → SUCCESSFUL. Commit: `feat(data): Hilt-DataModule`.

---

### Task 5: `:app` Hilt-fähig machen

**Files:** Modify `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`; Create `app/src/main/kotlin/com/komgareader/app/KomgaReaderApp.kt`; Modify `MainActivity.kt`.

- [ ] **Step 1** `app/build.gradle.kts`: Plugins ergänzen `alias(libs.plugins.ksp)`, `alias(libs.plugins.hilt)`. Dependencies ergänzen: `implementation(project(":data"))`, `implementation(project(":domain"))`, `implementation(libs.hilt.android)`, `ksp(libs.hilt.compiler)`, `implementation(libs.hilt.navigation.compose)`.

- [ ] **Step 2** Create `KomgaReaderApp.kt`:
```kotlin
package com.komgareader.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class KomgaReaderApp : Application()
```

`AndroidManifest.xml`: am `<application>`-Tag `android:name=".KomgaReaderApp"` ergänzen.

`MainActivity.kt`: Klasse mit `@AndroidEntryPoint` annotieren (Import `dagger.hilt.android.AndroidEntryPoint`). Sonst unverändert (ViewModel-Anbindung kommt in 1.4d).

- [ ] **Step 3** `./gradlew :app:assembleDebug` → SUCCESSFUL. Commit: `feat(app): Hilt aktiviert (Application + AndroidEntryPoint)`.

---

### Task 6: Instrumented-E2E — Persistenz überlebt (Room auf Emulator)

**Files:** Create `data/src/androidTest/kotlin/com/komgareader/data/PersistenceInstrumentedTest.kt`.

- [ ] **Step 1**
```kotlin
package com.komgareader.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.komgareader.data.db.AppDatabase
import com.komgareader.data.repository.RoomServerRepository
import com.komgareader.data.repository.RoomSettingsRepository
import com.komgareader.domain.repository.ServerConfig
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PersistenceInstrumentedTest {

    private lateinit var db: AppDatabase

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
    }
    @After fun teardown() = db.close()

    @Test fun server_wird_gespeichert_und_gelesen() = runTest {
        val repo = RoomServerRepository(db.serverDao())
        assertNull(repo.config.first())
        repo.save(ServerConfig(name = "NAS", baseUrl = "https://nas.local/api/v1/", apiKey = "k"))
        val loaded = repo.config.first()!!
        assertEquals("NAS", loaded.name)
        assertEquals("k", loaded.apiKey)
        repo.clear()
        assertNull(repo.config.first())
    }

    @Test fun settings_default_und_ueberschreiben() = runTest {
        val repo = RoomSettingsRepository(db.settingsDao())
        assertEquals("SYSTEM", repo.themeMode.first())
        assertEquals("de", repo.language.first())
        repo.setThemeMode("DARK")
        repo.setLanguage("en")
        assertEquals("DARK", repo.themeMode.first())
        assertEquals("en", repo.language.first())
    }
}
```

- [ ] **Step 2** `./gradlew :data:connectedDebugAndroidTest` → 2 Tests grün auf dem Emulator. Commit: `test(data): Instrumented-Persistenz (Room) auf Emulator`.

---

## Self-Review-Notiz
- **Spec-Abdeckung:** §3 `:data`-Modul, §8 persistierte Settings, §5 Server-Verbindung persistiert. Hilt-DI verdrahtet.
- **Bewusst verschoben:** Series/Book-Cache + Sync-Queue (1.4d/e), DataStore (Room reicht), ViewModel-Anbindung der Settings-UI an die Repos (1.4d).
- **Abnahme:** alle Module bauen; `:data:connectedDebugAndroidTest` 2 grün; `:app:assembleDebug` mit Hilt grün.
