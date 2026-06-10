# Integrationstest — Plan 4a: Hilt-UI-Test-Infrastruktur + Smoke-Test

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Eine saubere, isolierte Compose-UI-Test-Infrastruktur über Hilt: jeder UI-Test bekommt eine **in-memory** `AppDatabase` (verschmutzt **nie** die echte `komga-reader.db` auf dem Gerät), kann vor dem Activity-Start einen CI-Server seeden und treibt das **echte** `MainActivity`-Compose-UI. Bewiesen durch einen End-to-End-Smoke-Test (Server seeden → App starten → Bibliothek zeigt `Sample-Manga` live aus der CI-Komga).

**Architecture:** Custom `HiltTestRunner` (→ `HiltTestApplication`), ein `@TestInstallIn`-Modul ersetzt das produktive `DataModule` durch dieselben Bindings auf einer in-memory-`AppDatabase`. UI-Tests nutzen `HiltAndroidRule` + `createEmptyComposeRule()` und starten `MainActivity` **manuell nach** dem Seeding (sonst läse die App die DB vor dem Seed). Selektion über sichtbare DE-Texte (es gibt keine `testTag`s).

**Tech Stack:** Hilt 2.52 (`hilt-android-testing`), Compose BOM 2024.10.01 (`ui-test-junit4`, `ui-test-manifest`), `androidx.test:core` (`ActivityScenario`), KSP. `testInstrumentationRunner` → custom.

**Voraussetzung:** CI-Komga läuft (`tools/ci-fixtures/up.sh`), Emulator `eink_test` an, Tests gegen `emulator-5554`.

**Bezug:** Spec §2 (UI-Set), §9 (A1). Diese Infra trägt **alle** späteren UI-Tests (Plan 4b) und Plugin-UI-Tests (Plan 5).

---

## Grounding (verifiziert)

- App nutzt Hilt: `@HiltAndroidApp KomgaReaderApp` (`app/.../KomgaReaderApp.kt:8`), `@AndroidEntryPoint MainActivity` (`MainActivity.kt:47`), Single-Activity Compose-NavHost, Start-Route `"home"`.
- `testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"` (`app/build.gradle.kts:19`) — wird ersetzt.
- `DataModule` (`data/.../di/DataModule.kt`, `@InstallIn(SingletonComponent)`) provided: `database` (`"komga-reader.db"`), `settingsRepository`, `keystoreCredentialStore`, `credentialStore`, `serverRepository`, `downloadRepository`, `localBookBytes`, `shelfRepository`, `seriesOverrideRepository`, `readProgressRepository`, `novelProgressRepository`, `colorProfileRepository`, `collectionRepository`. **Nur `database` muss isoliert werden** — alle anderen leiten aus `db` ab.
- Plugin-Versionen: hilt `2.52`, ksp `2.0.21-1.0.25`, composeBom `2024.10.01`. Plugins `ksp`+`hilt` schon aktiv in `app/build.gradle.kts`.
- Bibliothek-Leerzustand-Text (DE): `"Noch keine Inhalte. Verbinde einen Komga-Server in den Einstellungen."` Serien-Tile zeigt `series.title` als Text (z.B. `Sample-Manga`).
- Harness aus Plan 2: `CiKomga.A` (statische Basic-Auth-`ServerConfig`), `CiFixtures.MANGA_SERIES`.

---

## Task 1: Gradle — Compose-UI-Test- + Hilt-Test-Deps + Runner

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`

- [ ] **Step 1: Version-Catalog-Einträge ergänzen**

In `gradle/libs.versions.toml` unter `[versions]` ergänzen:
```toml
androidxTestCore = "1.6.1"
```
Unter `[libraries]` ergänzen:
```toml
compose-ui-test-junit4 = { module = "androidx.compose.ui:ui-test-junit4" }
compose-ui-test-manifest = { module = "androidx.compose.ui:ui-test-manifest" }
hilt-android-testing = { module = "com.google.dagger:hilt-android-testing", version.ref = "hilt" }
androidx-test-core = { module = "androidx.test:core", version.ref = "androidxTestCore" }
```
(Die Compose-Test-Artefakte ziehen ihre Version aus der bestehenden `compose-bom`.)

- [ ] **Step 2: app/build.gradle.kts — Runner + androidTest-Deps**

Setze in `app/build.gradle.kts` den Runner (Zeile 19) auf:
```kotlin
testInstrumentationRunner = "com.komgareader.app.HiltTestRunner"
```
Ergänze im `dependencies`-Block (zu den bestehenden `androidTestImplementation`-Zeilen):
```kotlin
androidTestImplementation(platform(libs.compose.bom))
androidTestImplementation(libs.compose.ui.test.junit4)
androidTestImplementation(libs.hilt.android.testing)
androidTestImplementation(libs.androidx.test.core)
kspAndroidTest(libs.hilt.compiler)
debugImplementation(libs.compose.ui.test.manifest)
```

- [ ] **Step 3: Sync/Build prüfen**

Run: `./gradlew :app:help -q` (zwingt Catalog-/Build-Script-Auswertung)
Expected: kein Fehler (kein „Unresolved reference" im Build-Script, Katalog-Aliasse auflösbar).

- [ ] **Step 4: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts
git commit -m "test(ui): Compose-UI-Test- + Hilt-Test-Deps + custom Test-Runner"
```

---

## Task 2: HiltTestRunner

**Files:**
- Create: `app/src/androidTest/kotlin/com/komgareader/app/HiltTestRunner.kt`

- [ ] **Step 1: Runner schreiben**

```kotlin
package com.komgareader.app

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import dagger.hilt.android.testing.HiltTestApplication

/**
 * Ersetzt die App-Application im Instrumented-Test durch [HiltTestApplication], damit die
 * `@TestInstallIn`-Module (z.B. in-memory-DB) greifen. Referenziert in `app/build.gradle.kts`
 * als `testInstrumentationRunner`.
 */
class HiltTestRunner : AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader?, name: String?, context: Context?): Application =
        super.newApplication(cl, HiltTestApplication::class.java.name, context)
}
```

- [ ] **Step 2: Commit (kompiliert mit Task 3 zusammen)**

```bash
git add app/src/androidTest/kotlin/com/komgareader/app/HiltTestRunner.kt
git commit -m "test(ui): HiltTestRunner → HiltTestApplication"
```

---

## Task 3: TestDataModule — in-memory-DB statt komga-reader.db

**Files:**
- Create: `app/src/androidTest/kotlin/com/komgareader/app/ci/ui/TestDataModule.kt`

Ersetzt das produktive `DataModule` vollständig (alle Bindings 1:1, nur `database()` → in-memory).
So bleibt der gesamte übrige Graph (ViewModels, ActiveSource, Reader …) unverändert produktiv,
aber jeder Test bekommt eine frische, isolierte DB.

- [ ] **Step 1: TestDataModule schreiben**

```kotlin
package com.komgareader.app.ci.ui

import android.content.Context
import androidx.room.Room
import com.komgareader.data.db.AppDatabase
import com.komgareader.data.db.SEED_CALLBACK
import com.komgareader.data.di.DataModule
import com.komgareader.data.download.DownloadManager
import com.komgareader.data.download.LocalBookBytes
import com.komgareader.data.repository.RoomCollectionRepository
import com.komgareader.data.repository.RoomColorProfileRepository
import com.komgareader.data.repository.RoomDownloadRepository
import com.komgareader.data.repository.RoomNovelProgressRepository
import com.komgareader.data.repository.RoomReadProgressRepository
import com.komgareader.data.repository.RoomSeriesOverrideRepository
import com.komgareader.data.repository.RoomServerRepository
import com.komgareader.data.repository.RoomSettingsRepository
import com.komgareader.data.repository.RoomShelfRepository
import com.komgareader.data.security.CredentialStore
import com.komgareader.data.security.KeystoreCredentialStore
import com.komgareader.domain.repository.CollectionRepository
import com.komgareader.domain.repository.ColorProfileRepository
import com.komgareader.domain.repository.DownloadRepository
import com.komgareader.domain.repository.NovelProgressRepository
import com.komgareader.domain.repository.ReadProgressRepository
import com.komgareader.domain.repository.SeriesOverrideRepository
import com.komgareader.domain.repository.ServerRepository
import com.komgareader.domain.repository.SettingsRepository
import com.komgareader.domain.repository.ShelfRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.testing.TestInstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Test-Doppel für [DataModule]: identische Bindings, aber die [AppDatabase] ist **in-memory**
 * (frisch pro Hilt-Test-Komponente → pro Test isoliert, kein Zugriff auf die echte
 * `komga-reader.db`). Der KeystoreCredentialStore nutzt einen eindeutigen Alias je Testlauf,
 * damit keine echten App-Credentials berührt werden.
 */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [DataModule::class])
object TestDataModule {

    @Provides @Singleton
    fun database(@ApplicationContext ctx: Context): AppDatabase =
        Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java)
            .addCallback(SEED_CALLBACK)
            .build()

    @Provides @Singleton
    fun settingsRepository(db: AppDatabase): SettingsRepository = RoomSettingsRepository(db.settingsDao())

    @Provides @Singleton
    fun keystoreCredentialStore(): KeystoreCredentialStore = KeystoreCredentialStore("ci-ui-test")

    @Provides @Singleton
    fun credentialStore(store: KeystoreCredentialStore): CredentialStore = store

    @Provides @Singleton
    fun serverRepository(db: AppDatabase, credentials: KeystoreCredentialStore): ServerRepository =
        RoomServerRepository(db.serverDao(), credentials)

    @Provides @Singleton
    fun downloadRepository(db: AppDatabase): DownloadRepository = RoomDownloadRepository(db.downloadDao())

    @Provides @Singleton
    fun localBookBytes(manager: DownloadManager): LocalBookBytes = manager

    @Provides @Singleton
    fun shelfRepository(db: AppDatabase): ShelfRepository = RoomShelfRepository(db.shelfDao())

    @Provides @Singleton
    fun seriesOverrideRepository(db: AppDatabase): SeriesOverrideRepository =
        RoomSeriesOverrideRepository(db.seriesOverrideDao())

    @Provides @Singleton
    fun readProgressRepository(db: AppDatabase): ReadProgressRepository =
        RoomReadProgressRepository(db.readProgressDao())

    @Provides @Singleton
    fun novelProgressRepository(db: AppDatabase): NovelProgressRepository =
        RoomNovelProgressRepository(db.novelProgressDao())

    @Provides @Singleton
    fun colorProfileRepository(db: AppDatabase, settings: SettingsRepository): ColorProfileRepository =
        RoomColorProfileRepository(
            dao = db.colorProfileDao(),
            activePointer = settings.activeColorProfileId,
            setActivePointer = { settings.setActiveColorProfileId(it) },
        )

    @Provides @Singleton
    fun collectionRepository(db: AppDatabase): CollectionRepository = RoomCollectionRepository(db.collectionDao())
}
```

> Implementer-Hinweis: Die Provider-Signaturen **müssen** exakt denen in `DataModule` entsprechen
> (gleiche Rückgabetypen/Parameter), sonst meckert Hilt über fehlende Bindings. Bei Abweichung
> die echte `DataModule`-Signatur übernehmen und vermerken.

- [ ] **Step 2: Kompiliert**

Run: `./gradlew :app:compileDebugAndroidTestKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/androidTest/kotlin/com/komgareader/app/ci/ui/TestDataModule.kt
git commit -m "test(ui): TestDataModule — in-memory-AppDatabase ersetzt DataModule (Isolation)"
```

---

## Task 4: Smoke-Test — Scaffold end-to-end beweisen

**Files:**
- Create: `app/src/androidTest/kotlin/com/komgareader/app/ci/ui/HiltUiSmokeTest.kt`

- [ ] **Step 1: Smoke-Test schreiben**

```kotlin
package com.komgareader.app.ci.ui

import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.komgareader.app.MainActivity
import com.komgareader.app.ci.CiFixtures
import com.komgareader.app.ci.CiKomga
import com.komgareader.domain.repository.ServerRepository
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

/**
 * Beweist die UI-Test-Infrastruktur end-to-end: in-memory-DB (Hilt) + vor-Start-Seeding eines
 * CI-Servers + echtes MainActivity-Compose-UI + Live-Bibliothek aus der CI-Komga.
 *
 * `createEmptyComposeRule` startet KEINE Activity automatisch — wir seeden zuerst die DB und
 * starten MainActivity erst danach manuell, sonst läse die App die (leere) DB vor dem Seed.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class HiltUiSmokeTest {

    @get:Rule(order = 0) val hiltRule = HiltAndroidRule(this)
    @get:Rule(order = 1) val composeRule = createEmptyComposeRule()

    @Inject lateinit var servers: ServerRepository

    @Before fun seedAndLaunch() {
        hiltRule.inject()
        runBlocking { servers.save(CiKomga.A) }   // in-memory-DB → isoliert
        ActivityScenario.launch(MainActivity::class.java)
    }

    /** A1 (UI-Smoke): nach dem Start erscheint die Live-Bibliothek mit der Manga-Serie. */
    @Test fun bibliothek_zeigt_seeded_server_inhalt() {
        composeRule.waitUntil(timeoutMillis = 20_000) {
            composeRule.onAllNodesWithText(CiFixtures.MANGA_SERIES, substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onAllNodesWithText(CiFixtures.MANGA_SERIES, substring = true)
            .fetchSemanticsNodes().isNotEmpty().let { found ->
                assert(found) { "Manga-Serie '${CiFixtures.MANGA_SERIES}' muss in der Bibliothek erscheinen" }
            }
    }
}
```

- [ ] **Step 2: Kompiliert**

Run: `./gradlew :app:compileDebugAndroidTestKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Ausführen (Fixtures + Emulator) — der eigentliche Scaffold-Beweis**

Run (Fixtures laufen lassen): `tools/ci-fixtures/up.sh`, dann
`ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.komgareader.app.ci.ui.HiltUiSmokeTest`
Expected: 1 Test grün. (Beweist: Hilt-Test-Graph + in-memory-DB + Seeding + echtes UI + Live-Komga + Compose-Selektion.)

- [ ] **Step 4: Häufige Stolpersteine (falls rot)**
  - **„No instrumentation registered" / Runner nicht gefunden:** `testInstrumentationRunner` (Task 1) nicht gesetzt oder Tippfehler im FQN `com.komgareader.app.HiltTestRunner`.
  - **Hilt „missing binding":** eine `TestDataModule`-Provider-Signatur weicht von `DataModule` ab → exakt angleichen.
  - **Timeout, Bibliothek leer:** Fixtures nicht oben (`up.sh`), oder die App braucht einen anderen Tab als Start — dann erst prüfen, welcher Text nach Launch sichtbar ist (`composeRule.onRoot().printToLog(...)`), Selektor anpassen, im Report vermerken.
  - **`enterFullscreen()`/WindowFocus-Zicken:** ggf. `composeRule.waitForIdle()` nach Launch; Timeout großzügig (schon 20s).

- [ ] **Step 5: Commit**

```bash
git add app/src/androidTest/kotlin/com/komgareader/app/ci/ui/HiltUiSmokeTest.kt
git commit -m "test(ui): Hilt-UI-Smoke — Server seeden → MainActivity → Live-Bibliothek (A1-Smoke)"
```

---

## Self-Review (Plan-Autor)

- **Ziel:** isolierte UI-Test-Infra (in-memory-DB, kein echter App-Zustand berührt) + ein End-to-End-Smoke, der die ganze Kette beweist. Kein UI-Test-Katalog hier — das ist Plan 4b (baut auf dieser Infra auf).
- **Isolation:** `@TestInstallIn replaces DataModule` swappt nur die DB-Quelle; `HiltAndroidRule` baut die Komponente pro Test → frische in-memory-DB je Test. Keystore mit Test-Alias.
- **Seeding-vor-Launch:** `createEmptyComposeRule` + manuelles `ActivityScenario.launch` nach `servers.save` — sonst Race (App liest DB vor Seed).
- **Verifizierte Fakten:** alle `DataModule`-Provider gelistet (nur `database` isoliert); hilt 2.52/ksp/composeBom vorhanden; Selektion über DE-Text (keine testTags).
- **Offen:** exakte Provider-Signatur-Treue (Task 3) + tatsächlich sichtbarer Start-Text (Task 4 Step 4) — Implementer verifiziert beim ersten Lauf.

## Nächster Plan

- **Plan 4b** UI-Test-Katalog auf dieser Infra: A1 (Add-Server über echtes UI), A4 (Server entfernen → Leerzustand), B7 (Werk der zweiten Quelle öffnen), C9–11 (Reader-Dispatch: Tile-Tap → richtiger Reader), D14 (Sammlung anlegen), G22 (E-Ink-Akzent/keine Bewegung sichtbar). Gemeinsame `UiTestBase` (Hilt+Compose-Rule+Seed) extrahieren, sobald die 2. UI-Testklasse entsteht (`shared-structure-before-variants`).
```
