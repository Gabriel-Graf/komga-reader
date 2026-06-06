# Phase 1 · Plan 4d/… — Library/Settings-UI an KomgaSource

> REQUIRED SUB-SKILL: subagent-driven-development / executing-plans.

**Goal:** In den Einstellungen einen Komga-Server verbinden (persistiert); die Bibliothek zeigt dann das echte Serien-Grid mit Covern + Cloud-Badge. Theme/Sprache werden persistiert. Verifiziert gegen eine **echte lokale Komga** auf dem Emulator.

**Architecture:** Hilt-ViewModels (`SettingsViewModel`, `LibraryViewModel`) über den persistierten `ServerRepository`/`SettingsRepository` (aus 1.4c). Ein `KomgaSourceProvider` baut aus dem `ServerConfig` eine `KomgaSource` (Factory aus `:source-komga`). Cover laden über Coil mit `X-API-Key`-Header.

**Tech:** Coil 2.7 · Hilt-ViewModel · `:source-komga` · Material3 Compose.

## Test-Komga (lokal, läuft)
- Emulator-URL: `http://10.0.2.2:25600/api/v1/` · API-Key: `2243c9f4ecc5404992ddf8eba4bf6488`
- Inhalt: Serien `Berserk`, `Saga`. (Container `komga-test`; falls aus: `docker start komga-test`.)
- Cleartext-HTTP → Network-Security-Config nötig (Task 0).

---

### Task 0: app-Dependencies + Cleartext-Config + BuildConfig

**Files:** `gradle/libs.versions.toml`, `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`, `app/src/main/res/xml/network_security_config.xml`.

- [ ] **Step 1** Catalog: `[versions]` `coil = "2.7.0"`; `[libraries]` `coil-compose = { module = "io.coil-kt:coil-compose", version.ref = "coil" }`.

- [ ] **Step 2** `app/build.gradle.kts`: in `dependencies` ergänzen `implementation(project(":source-komga"))`, `implementation(libs.coil.compose)` (`:domain`, `:data`, Hilt, hilt-navigation-compose existieren bereits). In `android { defaultConfig { ... } }` nichts; in `android { buildFeatures { compose = true } }` → ergänze `buildConfig = true`.

- [ ] **Step 3** `network_security_config.xml` (Cleartext nur für lokale Test-Hosts, sonst Default-sicher):
```xml
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <domain-config cleartextTrafficPermitted="true">
        <domain includeSubdomains="true">10.0.2.2</domain>
        <domain includeSubdomains="true">localhost</domain>
        <domain includeSubdomains="true">127.0.0.1</domain>
    </domain-config>
</network-security-config>
```
Im Manifest `<application ... android:networkSecurityConfig="@xml/network_security_config">`.

- [ ] **Step 4** `./gradlew :app:assembleDebug` → SUCCESSFUL. Commit: `build(app): source-komga + Coil + Cleartext-Config (lokale Tests)`.

---

### Task 1: KomgaSourceProvider (ServerConfig → KomgaSource)

**Files:** Create `app/src/main/kotlin/com/komgareader/app/data/KomgaSourceProvider.kt`.

- [ ] **Step 1**
```kotlin
package com.komgareader.app.data

import com.komgareader.domain.repository.ServerConfig
import com.komgareader.source.komga.KomgaSource
import com.komgareader.source.komga.KomgaSourceFactory
import javax.inject.Inject
import javax.inject.Singleton

/** Baut aus einer persistierten [ServerConfig] eine [KomgaSource]. Null = nicht verbunden. */
@Singleton
class KomgaSourceProvider @Inject constructor() {
    fun from(config: ServerConfig?): KomgaSource? = config?.let {
        KomgaSourceFactory.create(name = it.name, baseUrl = it.baseUrl, apiKey = it.apiKey)
    }
}
```
- [ ] **Step 2** `./gradlew :app:compileDebugKotlin` → SUCCESSFUL. Commit: `feat(app): KomgaSourceProvider`.

---

### Task 2: SettingsViewModel + Settings-UI (Server verbinden + Theme/Sprache persistiert)

**Files:** Create `app/src/main/kotlin/com/komgareader/app/ui/settings/SettingsViewModel.kt`; modify `SettingsScreen.kt`.

- [ ] **Step 1** `SettingsViewModel.kt`:
```kotlin
package com.komgareader.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.komgareader.domain.repository.ServerConfig
import com.komgareader.domain.repository.ServerRepository
import com.komgareader.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val servers: ServerRepository,
) : ViewModel() {
    val themeMode = settings.themeMode.stateIn(viewModelScope, SharingStarted.Eagerly, "SYSTEM")
    val language = settings.language.stateIn(viewModelScope, SharingStarted.Eagerly, "de")
    val server = servers.config.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun setTheme(value: String) = viewModelScope.launch { settings.setThemeMode(value) }.let {}
    fun setLanguage(value: String) = viewModelScope.launch { settings.setLanguage(value) }.let {}
    fun saveServer(name: String, baseUrl: String, apiKey: String) = viewModelScope.launch {
        servers.save(ServerConfig(name = name, baseUrl = baseUrl.trimEnd('/') + "/", apiKey = apiKey))
    }.let {}
    fun disconnect() = viewModelScope.launch { servers.clear() }.let {}
}
```
- [ ] **Step 2** `SettingsScreen.kt`: Parameter umstellen auf `viewModel: SettingsViewModel = hiltViewModel()` (Import `androidx.hilt.navigation.compose.hiltViewModel`), Theme/Sprache aus `viewModel.themeMode.collectAsState()` etc., Callbacks an `viewModel.setTheme/setLanguage`. NEUE Sektion „Komga-Server": drei `OutlinedTextField` (Name, URL, API-Key — Key mit `PasswordVisualTransformation`), ein „Verbinden"-Button → `viewModel.saveServer(...)`, plus Statuszeile (verbunden: `server.value?.name` anzeigen, sonst „nicht verbunden") und „Trennen"-Button. i18n-Keys ergänzen (`settingsServer`, `serverName`, `serverUrl`, `serverApiKey`, `connect`, `disconnect`, `connected`, `notConnected`) in `Strings`/`StringsDe`/`StringsEn`. `onBack`-Param bleibt.
- [ ] **Step 3** `./gradlew :app:assembleDebug` → SUCCESSFUL. Commit: `feat(app): SettingsViewModel + Server-verbinden-UI (persistiert)`.

---

### Task 3: LibraryViewModel + Grid mit Covern

**Files:** Create `app/src/main/kotlin/com/komgareader/app/ui/library/LibraryViewModel.kt`; modify `LibraryScreen.kt`.

- [ ] **Step 1** `LibraryViewModel.kt`:
```kotlin
package com.komgareader.app.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.komgareader.app.data.KomgaSourceProvider
import com.komgareader.domain.model.Series
import com.komgareader.domain.repository.ServerRepository
import com.komgareader.domain.source.SourceFilter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface LibraryUiState {
    data object NoServer : LibraryUiState
    data object Loading : LibraryUiState
    data class Content(val series: List<Series>, val apiKey: String) : LibraryUiState
    data class Error(val message: String) : LibraryUiState
}

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val servers: ServerRepository,
    private val sourceProvider: KomgaSourceProvider,
) : ViewModel() {

    private val _state = MutableStateFlow<LibraryUiState>(LibraryUiState.Loading)
    val state: StateFlow<LibraryUiState> = _state

    init { refresh() }

    fun refresh() = viewModelScope.launch {
        _state.value = LibraryUiState.Loading
        val config = servers.config.first()
        val source = sourceProvider.from(config)
        if (config == null || source == null) { _state.value = LibraryUiState.NoServer; return@launch }
        _state.value = try {
            val page = source.browse(page = 0, filter = SourceFilter())
            LibraryUiState.Content(series = page.items, apiKey = config.apiKey)
        } catch (e: Exception) {
            LibraryUiState.Error(e.message ?: "Verbindung fehlgeschlagen")
        }
    }.let {}
}
```
- [ ] **Step 2** `LibraryScreen.kt`: `viewModel: LibraryViewModel = hiltViewModel()`, `val state by viewModel.state.collectAsState()`. TopBar mit Settings-Icon + Refresh-Icon (`viewModel.refresh()`). Body nach State:
  - `NoServer` → der bisherige Leer-Text (`libraryEmpty`).
  - `Loading` → `CircularProgressIndicator` zentriert.
  - `Error` → Fehlertext + Wiederholen-Button.
  - `Content` → `LazyVerticalGrid(columns = GridCells.Fixed(3))` über `series`; jede Zelle: Cover via Coil + Badge.
  Cover-Composable (Coil mit API-Key-Header):
```kotlin
@Composable
private fun SeriesCover(series: Series, apiKey: String) {
    val ctx = LocalContext.current
    val request = remember(series.coverUrl, apiKey) {
        ImageRequest.Builder(ctx).data(series.coverUrl)
            .addHeader("X-API-Key", apiKey).crossfade(false).build()
    }
    Box(Modifier.aspectRatio(2f / 3f).border(1.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))) {
        AsyncImage(model = request, contentDescription = series.title,
            contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        Icon(Icons.Filled.CloudQueue, contentDescription = null,
            modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(20.dp))
        Text(series.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.7f)).padding(2.dp), color = Color.White, fontSize = 10.sp)
    }
}
```
  (Alle nötigen Imports ergänzen: coil `AsyncImage`/`ImageRequest`, compose foundation grid/border/background, etc.)
- [ ] **Step 3** `./gradlew :app:assembleDebug` → SUCCESSFUL. Commit: `feat(app): LibraryViewModel + Cover-Grid`.

---

### Task 4: MainActivity an persistierte Settings + ViewModels

**Files:** modify `MainActivity.kt`.

- [ ] **Step 1** MainActivity: `@AndroidEntryPoint` (schon da). Theme/Sprache aus einem `SettingsViewModel = hiltViewModel()` (oder `viewModel()`), `collectAsState`, in `KomgaReaderTheme(themeMode = ThemeMode.valueOf(themeStr))` und `stringsFor(if (langStr=="en") EN else DE)` übersetzen. NavHost wie gehabt; `LibraryScreen(onOpenSettings=...)` und `SettingsScreen(onBack=...)` ziehen ihre ViewModels selbst via `hiltViewModel()`. Entferne den bisherigen In-Memory-`remember`-State.
- [ ] **Step 2** `./gradlew :app:assembleDebug` → SUCCESSFUL + `bash tools/e2e/app_smoke.sh` → PASS (App bootet weiter crashfrei). Commit: `feat(app): MainActivity an persistierte Settings/ViewModels`.

---

### Task 5: Instrumented-E2E gegen echte Komga + Screenshot

**Files:** Create `app/src/androidTest/kotlin/com/komgareader/app/LibraryFlowInstrumentedTest.kt`.

- [ ] **Step 1** Test: persistiert eine `ServerConfig` auf 10.0.2.2 und prüft, dass der App-Stack die echten Serien lädt.
```kotlin
package com.komgareader.app

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.komgareader.app.data.KomgaSourceProvider
import com.komgareader.data.db.AppDatabase
import com.komgareader.data.repository.RoomServerRepository
import com.komgareader.data.security.EncryptedCredentialStore
import com.komgareader.domain.repository.ServerConfig
import com.komgareader.domain.source.SourceFilter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryFlowInstrumentedTest {

    @Test fun laedt_echte_serien_von_lokaler_komga() = runTest {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).build()
        val store = EncryptedCredentialStore(ctx).apply { clear() }
        val repo = RoomServerRepository(db.serverDao(), store)
        repo.save(ServerConfig(
            name = "Test", baseUrl = "http://10.0.2.2:25600/api/v1/",
            apiKey = "2243c9f4ecc5404992ddf8eba4bf6488",
        ))
        val source = KomgaSourceProvider().from(repo.config.first())!!
        val page = source.browse(0, SourceFilter())
        val titles = page.items.map { it.title }
        assertTrue("Serien geladen: $titles", titles.any { it.contains("Berserk") })
        assertTrue("Serien geladen: $titles", titles.any { it.contains("Saga") })
        db.close()
    }
}
```
- [ ] **Step 2** Stelle sicher, dass die Test-Komga läuft: `docker start komga-test` (idempotent), `curl -s -H "X-API-Key: 2243c9f4ecc5404992ddf8eba4bf6488" http://localhost:25600/api/v1/series | grep -q Berserk`. Dann `./gradlew :app:connectedDebugAndroidTest` → grün.
- [ ] **Step 3 (Screenshot, optionaler Visual-Beweis)** Über die UI verbinden und Grid abfotografieren:
  - App starten, in Settings navigieren, Felder füllen (`adb shell input`), verbinden, zurück zur Bibliothek, `adb exec-out screencap -p > /tmp/library_grid.png`. Falls `adb input`-Navigation zu fragil ist: diesen Schritt überspringen — der Instrumented-Test in Step 2 ist der maßgebliche E2E-Beweis. Notiere im Report, ob ein Screenshot erzeugt wurde.
- [ ] **Step 4** Commit: `test(app): Instrumented-E2E laedt echte Serien von lokaler Komga`.

---

## Self-Review-Notiz
- **Spec-Abdeckung:** §8 Bibliothek-Grid + Cover + Badge, Settings-Server-verbinden, Theme/Sprache persistiert → Tasks 2–4; Quellen-agnostische Anbindung über KomgaSource → Task 1/3.
- **Bewusst verschoben:** Serien-Detail + Buch-Liste + Reader-Navigation (1.4e), Offline-Cache/Room-Spiegel der Serien (später), Regal-Verwaltung/Multi-Source (Phase 2), Custom-Controls statt Material3-TextField/RadioButton (Politur).
- **Abnahme:** `:app:assembleDebug` grün, App bootet (Smoke PASS), `:app:connectedDebugAndroidTest` lädt echte Berserk/Saga von der lokalen Komga.
