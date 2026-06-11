# Plugins-Seite vereinheitlichen + SyncCoordinator — Implementierungsplan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Plugin-Verwaltung + Repo-Entdeckung auf **einer** Seite (Plugins-Tab) vereinen und alle Sync-/Discovery-Trigger hinter **einem** `SyncCoordinator` bündeln.

**Architecture:** Drei neue, fokussierte Einheiten: eine **pure** Filterfunktion `visibleRows` (`:data`), ein **`PluginCatalog`** (@Singleton, hält Discovery-Zustand: lokaler APK-Scan + Repo-Fetch + Install + Prune — die zusammengelegte I/O beider alten ViewModels), und ein **`SyncCoordinator`** (@Singleton, App-Start-Latch + Sync-Entscheidungen, delegiert an `CollectionSyncManager` + `PluginCatalog`). Der Plugins-Tab-VM wird dünn (beobachtet `PluginCatalog`, hält Query/Typ-Filter). Die separate Repo-Browser-Seite + Route entfallen.

**Tech Stack:** Kotlin, Jetpack Compose, Hilt, kotlinx.coroutines (StateFlow), JUnit (pure Unit-Tests), Emulator-E2E (`eink_test`, lokale Test-Komga + Docker-Kavita).

**Spec:** `docs/superpowers/specs/2026-06-11-plugins-page-unification-sync-coordinator-design.md`

---

## Dateistruktur

**Neu:**
- `data/src/main/kotlin/com/komgareader/data/plugin/repo/PluginCatalogFilter.kt` — pure `visibleRows` + Typen
- `data/src/test/kotlin/com/komgareader/data/plugin/repo/PluginCatalogFilterTest.kt`
- `app/src/main/kotlin/com/komgareader/app/data/PluginCatalog.kt` — @Singleton Discovery-Zustand/-Orchestrierung
- `app/src/main/kotlin/com/komgareader/app/data/SyncCoordinator.kt` — @Singleton Sync-Naht
- `app/src/test/kotlin/com/komgareader/app/data/SyncCoordinatorTest.kt`

**Geändert:**
- `app/src/main/kotlin/com/komgareader/app/ui/plugins/PluginsViewModel.kt` — dünn, beobachtet `PluginCatalog`
- `app/src/main/kotlin/com/komgareader/app/ui/plugins/PluginsScreen.kt` — vereinte Liste (installiert/Divider/entdeckt) + Typ-Filter
- `app/src/main/kotlin/com/komgareader/app/ui/home/HomeScreen.kt` — TopBar TAB_PLUGINS: Suche-Filter-Anzeige + ⚙ + ⟳; Repo-Mgmt-Modal-State
- `app/src/main/kotlin/com/komgareader/app/MainActivity.kt` — Route `plugin-repos` weg, `onOpenRepoBrowser` weg, `SyncCoordinator.onAppStart()` aufrufen
- `app/src/main/kotlin/com/komgareader/app/ui/collections/CollectionsViewModel.kt` — Sync-Trigger → Coordinator
- `app/src/main/kotlin/com/komgareader/app/ui/settings/SettingsViewModel.kt` — `pullOnlySync` → Coordinator
- `app/src/main/kotlin/com/komgareader/app/i18n/*` — neue/umbenannte Strings (Filter-Chips, Reload)

**Gelöscht:**
- `app/src/main/kotlin/com/komgareader/app/ui/plugins/repo/RepoBrowserScreen.kt`
- `app/src/main/kotlin/com/komgareader/app/ui/plugins/repo/RepoBrowserViewModel.kt`

**Verschoben (Code aus RepoBrowserViewModel):** `BrowserRow` zieht nach `PluginCatalogFilter.kt` (data); die `toRow()`/`install()`/`refresh()`-Logik nach `PluginCatalog`.

---

## Phase 0 — Pure Filterfunktion (`:data`, TDD)

### Task 1: `visibleRows` + `BrowserRow`/`PluginTypeFilter` in `:data`

**Files:**
- Create: `data/src/main/kotlin/com/komgareader/data/plugin/repo/PluginCatalogFilter.kt`
- Test: `data/src/test/kotlin/com/komgareader/data/plugin/repo/PluginCatalogFilterTest.kt`

`BrowserRow` lebt heute in `RepoBrowserViewModel` (app). Es zieht nach `:data`, damit die pure Filterfunktion es ohne app-Abhängigkeit nutzen kann. Die installierte Seite wird im Filter als generisches `InstalledEntry` modelliert (Quelle ODER Preset), damit `visibleRows` ohne Plugin-Host-Typen testbar bleibt.

- [ ] **Step 1: Failing test schreiben**

```kotlin
package com.komgareader.data.plugin.repo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginCatalogFilterTest {

    private fun installed(name: String, kind: PluginKind) =
        InstalledEntry(packageName = "pkg.$name", displayName = name, kind = kind)

    private fun discovered(name: String, kind: PluginKind, desc: String = "") =
        BrowserRow(
            item = BrowsableEntry(
                entry = RepoPluginEntry(packageName = "pkg.$name", name = name, description = desc),
                repoName = "Repo",
                repoUrl = "https://x/repo.json",
                kind = kind,
            ),
            state = InstallState.NOT_INSTALLED,
            compatible = true,
        )

    @Test
    fun `empty filters keep everything, installed and discovered split`() {
        val out = visibleRows(
            installed = listOf(installed("Kavita", PluginKind.SOURCE)),
            discovered = listOf(discovered("Comick", PluginKind.SOURCE)),
            query = "",
            typeFilter = PluginTypeFilter.ALL,
        )
        assertEquals(listOf("Kavita"), out.installed.map { it.displayName })
        assertEquals(listOf("Comick"), out.discovered.map { it.item.entry.name })
    }

    @Test
    fun `type filter SOURCES drops presets from both sections`() {
        val out = visibleRows(
            installed = listOf(installed("Kavita", PluginKind.SOURCE), installed("Sepia", PluginKind.PRESET)),
            discovered = listOf(discovered("Comick", PluginKind.SOURCE), discovered("Warm", PluginKind.PRESET)),
            query = "",
            typeFilter = PluginTypeFilter.SOURCES,
        )
        assertEquals(listOf("Kavita"), out.installed.map { it.displayName })
        assertEquals(listOf("Comick"), out.discovered.map { it.item.entry.name })
    }

    @Test
    fun `query matches name and description, case-insensitive, both sections`() {
        val out = visibleRows(
            installed = listOf(installed("Kavita", PluginKind.SOURCE), installed("Comick", PluginKind.SOURCE)),
            discovered = listOf(discovered("Warm", PluginKind.PRESET, desc = "kavita preset")),
            query = "kav",
            typeFilter = PluginTypeFilter.ALL,
        )
        assertEquals(listOf("Kavita"), out.installed.map { it.displayName })
        assertEquals(listOf("Warm"), out.discovered.map { it.item.entry.name })
    }

    @Test
    fun `showDivider only when both sections non-empty after filtering`() {
        val both = visibleRows(
            installed = listOf(installed("Kavita", PluginKind.SOURCE)),
            discovered = listOf(discovered("Comick", PluginKind.SOURCE)),
            query = "", typeFilter = PluginTypeFilter.ALL,
        )
        assertTrue(both.showDivider)

        val onlyInstalled = visibleRows(
            installed = listOf(installed("Kavita", PluginKind.SOURCE)),
            discovered = emptyList(),
            query = "", typeFilter = PluginTypeFilter.ALL,
        )
        assertEquals(false, onlyInstalled.showDivider)

        val onlyDiscovered = visibleRows(
            installed = emptyList(),
            discovered = listOf(discovered("Comick", PluginKind.SOURCE)),
            query = "", typeFilter = PluginTypeFilter.ALL,
        )
        assertEquals(false, onlyDiscovered.showDivider)
    }
}
```

- [ ] **Step 2: Test rotlaufen lassen**

Run: `./gradlew :data:testDebugUnitTest --tests "com.komgareader.data.plugin.repo.PluginCatalogFilterTest"`
Expected: FAIL — `visibleRows`/`InstalledEntry`/`PluginTypeFilter`/`VisibleRows` ungelöst.

- [ ] **Step 3: Minimale Implementierung**

```kotlin
package com.komgareader.data.plugin.repo

/** Typ-Filter des Plugins-Tabs (Chip „Alle/Quellen/Presets"). */
enum class PluginTypeFilter { ALL, SOURCES, PRESETS }

/** Quellen-agnostische Sicht auf ein installiertes Plugin-APK (Quelle ODER Preset). */
data class InstalledEntry(
    val packageName: String,
    val displayName: String,
    val kind: PluginKind,
)

/** Ergebnis der Filterung: beide Abschnitte + ob ein Divider dazwischen gehört. */
data class VisibleRows(
    val installed: List<InstalledEntry>,
    val discovered: List<BrowserRow>,
    val showDivider: Boolean,
)

private fun PluginKind.matches(filter: PluginTypeFilter): Boolean = when (filter) {
    PluginTypeFilter.ALL -> true
    PluginTypeFilter.SOURCES -> this == PluginKind.SOURCE
    PluginTypeFilter.PRESETS -> this == PluginKind.PRESET
}

/**
 * Reine Filterung des Plugins-Tabs: Typ-Chip + Suchtext auf installierte UND entdeckte anwenden.
 * Installierte bleiben oben; der Divider erscheint nur, wenn nach Filterung BEIDE Abschnitte Inhalt
 * haben (keine schwebende Linie über leerem Bereich).
 */
fun visibleRows(
    installed: List<InstalledEntry>,
    discovered: List<BrowserRow>,
    query: String,
    typeFilter: PluginTypeFilter,
): VisibleRows {
    val q = query.trim()
    val inst = installed.filter {
        it.kind.matches(typeFilter) && (q.isBlank() || it.displayName.contains(q, ignoreCase = true))
    }
    val disc = discovered.filter {
        it.item.kind.matches(typeFilter) && (
            q.isBlank() ||
                it.item.entry.name.contains(q, ignoreCase = true) ||
                it.item.entry.description.contains(q, ignoreCase = true)
        )
    }
    return VisibleRows(inst, disc, showDivider = inst.isNotEmpty() && disc.isNotEmpty())
}
```

Außerdem `BrowserRow` aus `RepoBrowserViewModel.kt` hierher verschieben (gleiches Package `com.komgareader.data.plugin.repo`):

```kotlin
/** Eine Browser-Zeile: gemergter Eintrag + abgeleiteter Install-Zustand + ABI-Kompatibilität. */
data class BrowserRow(
    val item: BrowsableEntry,
    val state: InstallState,
    val compatible: Boolean,
)
```

- [ ] **Step 4: Test grün**

Run: `./gradlew :data:testDebugUnitTest --tests "com.komgareader.data.plugin.repo.PluginCatalogFilterTest"`
Expected: PASS (4 Tests).

- [ ] **Step 5: Commit**

```bash
git add data/src/main/kotlin/com/komgareader/data/plugin/repo/PluginCatalogFilter.kt data/src/test/kotlin/com/komgareader/data/plugin/repo/PluginCatalogFilterTest.kt
git commit -m "feat(data): pure visibleRows-Filter + BrowserRow nach :data ziehen"
```

---

## Phase 1 — `PluginCatalog` (@Singleton, Discovery-Zustand)

### Task 2: `PluginCatalog` anlegen (lokaler Scan + Prune + Repo-Fetch + Install)

**Files:**
- Create: `app/src/main/kotlin/com/komgareader/app/data/PluginCatalog.kt`

Konsolidiert die I/O aus `PluginsViewModel.refresh()` (Scan + Prune) und `RepoBrowserViewModel` (Fetch + Install). Hält den Zustand als StateFlows, die der dünne VM beobachtet. **Keine** ViewModel-Lifecycle-Bindung — der `SyncCoordinator` (App-Start) und der VM (Reload) treiben dieselbe Instanz.

- [ ] **Step 1: Datei schreiben**

```kotlin
package com.komgareader.app.data

import android.content.Context
import androidx.core.content.pm.PackageInfoCompat
import com.komgareader.app.ui.plugins.PluginInstaller
import com.komgareader.app.ui.plugins.InstallResult
import com.komgareader.app.ui.plugins.planPluginPrune
import com.komgareader.data.plugin.repo.BrowsableEntry
import com.komgareader.data.plugin.repo.BrowserRow
import com.komgareader.data.plugin.repo.InstalledEntry
import com.komgareader.data.plugin.repo.PluginKind
import com.komgareader.data.plugin.repo.PluginRepoClient
import com.komgareader.data.plugin.repo.RepoStore
import com.komgareader.data.plugin.repo.installState
import com.komgareader.data.plugin.repo.mergeRepoEntries
import com.komgareader.data.plugin.repo.parseRepoIndex
import com.komgareader.data.plugin.repo.pluginKindOf
import com.komgareader.data.plugin.repo.resolveApkUrl
import com.komgareader.domain.model.SourceKind
import com.komgareader.domain.repository.CollectionRepository
import com.komgareader.domain.repository.ColorProfileRepository
import com.komgareader.domain.repository.ServerRepository
import com.komgareader.plugin.PluginAbi
import com.komgareader.plugin.host.DiscoveredPlugin
import com.komgareader.plugin.host.DiscoveredPresetPlugin
import com.komgareader.plugin.host.PluginHost
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Einziger Halter des Plugin-Discovery-Zustands (installierte APK-Quellen/-Presets + entdeckte
 * Repo-Einträge). Von [SyncCoordinator] (App-Start/Reload) und dem dünnen `PluginsViewModel`
 * (Reader) geteilt. Lokaler Scan = kein Netz; Repo-Fetch = Netz (nur App-Start 1× + manueller Reload).
 */
@Singleton
class PluginCatalog @Inject constructor(
    @ApplicationContext private val context: Context,
    private val pluginHost: PluginHost,
    private val servers: ServerRepository,
    private val registration: SourceRegistration,
    private val collections: CollectionRepository,
    private val colorProfiles: ColorProfileRepository,
    private val repoStore: RepoStore,
    private val client: PluginRepoClient,
    private val installer: PluginInstaller,
) {
    private val _sources = MutableStateFlow<List<DiscoveredPlugin>>(emptyList())
    val sources: StateFlow<List<DiscoveredPlugin>> = _sources.asStateFlow()

    private val _presetPlugins = MutableStateFlow<List<DiscoveredPresetPlugin>>(emptyList())
    val presetPlugins: StateFlow<List<DiscoveredPresetPlugin>> = _presetPlugins.asStateFlow()

    private val _discovered = MutableStateFlow<List<BrowserRow>>(emptyList())
    val discovered: StateFlow<List<BrowserRow>> = _discovered.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun dismissError() { _error.value = null }

    /** Installierte als quellen-agnostische [InstalledEntry] (für die pure `visibleRows`-Filterung). */
    fun installedEntries(): List<InstalledEntry> =
        _sources.value.map { InstalledEntry(it.packageName, it.metadata.displayName, PluginKind.SOURCE) } +
            _presetPlugins.value.map { InstalledEntry(it.packageName, it.displayName, PluginKind.PRESET) }

    /**
     * Lokaler APK-Scan (kein Netz): Quellen + Presets neu entdecken, danach Verwaistes prunen
     * (deinstallierte APKs → Profile/Plugin-Quellen löschen). Verschoben aus PluginsViewModel.refresh().
     */
    suspend fun scanLocal() {
        val srcs = withContext(Dispatchers.IO) {
            runCatching { pluginHost.discoverPlugins() }.getOrDefault(emptyList())
        }
        val presets = withContext(Dispatchers.IO) {
            runCatching { pluginHost.discoverColorPresetPlugins() }.getOrDefault(emptyList())
        }
        _sources.value = srcs
        _presetPlugins.value = presets

        val installed = (srcs.map { it.packageName } + presets.map { it.packageName }).toSet()
        val taggedPkgs = colorProfiles.observeAll().first().mapNotNull { it.pluginPackage }
        val pluginConfigs = servers.configs.first().filter { it.kind == SourceKind.PLUGIN }
        val plan = planPluginPrune(installed, taggedPkgs, pluginConfigs)
        plan.presetPackagesToDrop.forEach { colorProfiles.deleteByPluginPackage(it) }
        plan.sourceConfigIdsToRemove.forEach { id ->
            val cfg = pluginConfigs.firstOrNull { it.id == id }
            servers.remove(id)
            cfg?.let { registration.sourceIdOf(it) }?.let { collections.removeSource(it) }
        }
    }

    /** Netz-Repo-Fetch: aktive Repos laden, mergen, Install-Zustand berechnen. */
    suspend fun fetchRepos() {
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
        _discovered.value = withContext(Dispatchers.IO) { merged.map { it.toRow() } }
        _loading.value = false
    }

    private fun BrowsableEntry.toRow(): BrowserRow {
        val installedVersion = runCatching {
            PackageInfoCompat.getLongVersionCode(
                context.packageManager.getPackageInfo(entry.packageName, 0),
            )
        }.getOrNull()
        val compatible = entry.abiVersion in PluginAbi.MIN_SUPPORTED..PluginAbi.VERSION
        return BrowserRow(this, installState(entry.versionCode, installedVersion), compatible)
    }

    /** Lädt das APK, verifiziert den Fingerprint, startet die Installation (OS-Dialog). */
    suspend fun install(row: BrowserRow) {
        _error.value = null
        val url = resolveApkUrl(row.item.repoUrl, row.item.entry.apkUrl)
        val dir = File(context.cacheDir, "plugin-repo").apply { mkdirs() }
        val dest = File(dir, "${row.item.entry.packageName}-${row.item.entry.versionCode}.apk")
        val ok = client.download(url, dest)
        if (!ok) { _error.value = "download"; return }
        when (installer.verifyAndInstall(dest, row.item.entry.fingerprint)) {
            is InstallResult.FingerprintMismatch -> _error.value = "fingerprint"
            is InstallResult.Failed -> _error.value = "install"
            is InstallResult.SessionStarted -> Unit // OS-Dialog; Re-Scan beim onResume
        }
    }

    suspend fun addRepo(url: String) { repoStore.addUserRepo(url); fetchRepos() }
    suspend fun removeRepo(id: Long) { repoStore.removeUserRepo(id); fetchRepos() }
    suspend fun setOfficialEnabled(enabled: Boolean) { repoStore.setOfficialEnabled(enabled); fetchRepos() }
}
```

> Hinweis: `PluginInstaller`, `InstallResult`, `planPluginPrune` liegen heute im Package
> `com.komgareader.app.ui.plugins(.repo)`. Imports oben prüfen und an die echten Package-Pfade anpassen
> (`PluginInstaller`/`InstallResult` aktuell `com.komgareader.app.ui.plugins.repo`). Beim Verschieben des
> Catalogs nicht den Package-Pfad der Installer-Typen ändern — nur korrekt importieren.

- [ ] **Step 2: Build (Catalog kompiliert, noch ungenutzt)**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL (ggf. „unused"-Warnungen; Importpfade von `PluginInstaller`/`InstallResult`/`planPluginPrune` exakt setzen).

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/komgareader/app/data/PluginCatalog.kt
git commit -m "feat(app): PluginCatalog @Singleton — geteilter Discovery-Zustand (Scan/Fetch/Install/Prune)"
```

---

## Phase 2 — `SyncCoordinator` (@Singleton, Sync-Naht)

### Task 3: `SyncCoordinator` mit App-Start-Latch + Gating (TDD auf Latch/Gating)

**Files:**
- Create: `app/src/main/kotlin/com/komgareader/app/data/SyncCoordinator.kt`
- Test: `app/src/test/kotlin/com/komgareader/app/data/SyncCoordinatorTest.kt`

Der Koordinator ist die **eine** Stelle der Sync-Entscheidungen. Ereignis rein (`onAppStart`,
`onServerChanged`, `onManualReload`, `onCollectionsTabEntered`), Aktion drin. Gegen Fakes testbar:
`CollectionSyncManager` ist `open`/interface-frei → wir injizieren stattdessen schmale Funktions-Lambdas,
damit der Koordinator pur testbar bleibt (gleiches Muster wie `CollectionSyncManager`s Lambda-Konstruktor).

- [ ] **Step 1: Failing test schreiben**

```kotlin
package com.komgareader.app.data

import com.komgareader.domain.usecase.VanishedCollection
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SyncCoordinatorTest {

    private class Spy {
        var fullSyncCount = 0
        var pullOnlyCount = 0
        var scanLocalCount = 0
        var fetchReposCount = 0
    }

    private fun coordinator(spy: Spy, displayMode: String) = SyncCoordinator(
        fullSync = { spy.fullSyncCount++; emptyList() },
        pullOnlySync = { spy.pullOnlyCount++ },
        scanLocal = { spy.scanLocalCount++ },
        fetchRepos = { spy.fetchReposCount++ },
        displayMode = { displayMode },
    )

    @Test
    fun `onAppStart runs once even if called twice`() = runTest {
        val spy = Spy()
        val c = coordinator(spy, "LCD")
        c.onAppStart()
        c.onAppStart()
        assertEquals(1, spy.fullSyncCount)
        assertEquals(1, spy.scanLocalCount)
        assertEquals(1, spy.fetchReposCount)
    }

    @Test
    fun `onAppStart on EINK still runs full sync once (app-start is allowed)`() = runTest {
        val spy = Spy()
        coordinator(spy, "EINK").onAppStart()
        assertEquals(1, spy.fullSyncCount)
    }

    @Test
    fun `onServerChanged pulls only`() = runTest {
        val spy = Spy()
        coordinator(spy, "EINK").onServerChanged()
        assertEquals(1, spy.pullOnlyCount)
        assertEquals(0, spy.fullSyncCount)
    }

    @Test
    fun `onManualReload fetches repos and rescans local`() = runTest {
        val spy = Spy()
        coordinator(spy, "EINK").onManualReload()
        assertEquals(1, spy.fetchReposCount)
        assertEquals(1, spy.scanLocalCount)
    }

    @Test
    fun `onCollectionsTabEntered full-syncs on LCD but not on EINK`() = runTest {
        val lcd = Spy(); coordinator(lcd, "LCD").onCollectionsTabEntered(); assertEquals(1, lcd.fullSyncCount)
        val eink = Spy(); coordinator(eink, "EINK").onCollectionsTabEntered(); assertEquals(0, eink.fullSyncCount)
    }
}
```

- [ ] **Step 2: Test rotlaufen lassen**

Run: `./gradlew :app:testDebugUnitTest --tests "com.komgareader.app.data.SyncCoordinatorTest"`
Expected: FAIL — `SyncCoordinator` ungelöst.

- [ ] **Step 3: `SyncCoordinator` implementieren**

```kotlin
package com.komgareader.app.data

import com.komgareader.app.ui.collections.aggressiveSyncAllowed
import com.komgareader.domain.repository.SettingsRepository
import com.komgareader.domain.usecase.VanishedCollection
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Die EINE Stelle der Sync-/Discovery-Entscheidungen. Call-Sites melden nur das Ereignis
 * (App-Start, Server geändert, manueller Reload, Sammlungen-Tab betreten); WAS dann synct/entdeckt,
 * entscheidet ausschließlich diese Klasse. Quellen-agnostisch — delegiert an CollectionSyncManager
 * (Sammlungen) und PluginCatalog (Plugins). Werke/Libs laden reaktiv über die bestehenden
 * ActiveSource-Flows (LibraryViewModel); der App-Start-Sync stößt deren Sammlungs-/Plugin-Teil an.
 *
 * App lädt einmalig pro Launch (akkuschonend auf E-Ink): [onAppStart] ist latch-geschützt.
 */
@Singleton
class SyncCoordinator(
    private val fullSync: suspend () -> List<VanishedCollection>,
    private val pullOnlySync: suspend () -> Unit,
    private val scanLocal: suspend () -> Unit,
    private val fetchRepos: suspend () -> Unit,
    private val displayMode: suspend () -> String,
) {
    @Inject constructor(
        sync: CollectionSyncManager,
        catalog: PluginCatalog,
        settings: SettingsRepository,
    ) : this(
        fullSync = { sync.fullSync() },
        pullOnlySync = { sync.pullOnlySync() },
        scanLocal = { catalog.scanLocal() },
        fetchRepos = { catalog.fetchRepos() },
        displayMode = { settings.displayMode.first() },
    )

    private val mutex = Mutex()
    private var appStartDone = false

    /** Einmalig pro App-Launch: Sammlungs-Voll-Sync + lokaler Plugin-Scan + 1× Repo-Fetch. Best-effort je Domäne. */
    suspend fun onAppStart() {
        mutex.withLock {
            if (appStartDone) return
            appStartDone = true
        }
        runCatching { fullSync() }
        runCatching { scanLocal() }
        runCatching { fetchRepos() }
    }

    /** Server hinzugefügt/aktualisiert/entfernt → dessen Sammlungen NUR pullen. */
    suspend fun onServerChanged() { runCatching { pullOnlySync() } }

    /** Reload-Button im Plugins-Tab: Repo neu holen + lokal neu scannen. */
    suspend fun onManualReload() {
        runCatching { fetchRepos() }
        runCatching { scanLocal() }
    }

    /** Sammlungen-Tab betreten: auf Nicht-E-Ink zusätzlich voll synchronisieren (Akku-Gating). */
    suspend fun onCollectionsTabEntered() {
        if (aggressiveSyncAllowed(displayMode())) runCatching { fullSync() }
    }
}
```

- [ ] **Step 4: Test grün**

Run: `./gradlew :app:testDebugUnitTest --tests "com.komgareader.app.data.SyncCoordinatorTest"`
Expected: PASS (5 Tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/komgareader/app/data/SyncCoordinator.kt app/src/test/kotlin/com/komgareader/app/data/SyncCoordinatorTest.kt
git commit -m "feat(app): SyncCoordinator — zentrale Sync-Naht (App-Start-Latch + Gating), TDD"
```

---

## Phase 3 — Dünner `PluginsViewModel`

### Task 4: `PluginsViewModel` auf `PluginCatalog` + `SyncCoordinator` umstellen

**Files:**
- Modify: `app/src/main/kotlin/com/komgareader/app/ui/plugins/PluginsViewModel.kt`

Der VM hält nur noch UI-Zustand (Query, Typ-Filter) + delegiert Discovery an den Catalog und Reload an
den Koordinator. Add-/Import-/Remove-Aktionen bleiben (Server-Add ruft jetzt `coordinator.onServerChanged`).

- [ ] **Step 1: Datei ersetzen**

```kotlin
package com.komgareader.app.ui.plugins

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.komgareader.app.data.PluginCatalog
import com.komgareader.app.data.SyncCoordinator
import com.komgareader.data.plugin.repo.BrowserRow
import com.komgareader.data.plugin.repo.PluginTypeFilter
import com.komgareader.data.plugin.repo.VisibleRows
import com.komgareader.data.plugin.repo.visibleRows
import com.komgareader.domain.model.ColorProfile
import com.komgareader.domain.model.SourceKind
import com.komgareader.domain.repository.ColorProfileRepository
import com.komgareader.domain.repository.ServerConfig
import com.komgareader.domain.repository.ServerRepository
import com.komgareader.plugin.ColorPresetSpec
import com.komgareader.plugin.host.DiscoveredPlugin
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.komgareader.data.plugin.repo.ColorPresetImporter
import javax.inject.Inject

@HiltViewModel
class PluginsViewModel @Inject constructor(
    private val catalog: PluginCatalog,
    private val coordinator: SyncCoordinator,
    private val servers: ServerRepository,
    private val colorProfiles: ColorProfileRepository,
) : ViewModel() {

    val sources = catalog.sources
    val presetPlugins = catalog.presetPlugins
    val loading = catalog.loading
    val error = catalog.error

    val profiles: StateFlow<List<ColorProfile>> =
        colorProfiles.observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()
    fun setQuery(q: String) { _query.value = q }

    private val _typeFilter = MutableStateFlow(PluginTypeFilter.ALL)
    val typeFilter: StateFlow<PluginTypeFilter> = _typeFilter.asStateFlow()
    fun setTypeFilter(f: PluginTypeFilter) { _typeFilter.value = f }

    /** Gefilterte, anzeigefertige Sicht: installiert oben, entdeckt unten, Divider-Flag. */
    val visible: StateFlow<VisibleRows> =
        combine(catalog.sources, catalog.presetPlugins, catalog.discovered, _query, _typeFilter) { _, _, disc, q, f ->
            visibleRows(catalog.installedEntries(), disc, q, f)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VisibleRows(emptyList(), emptyList(), false))

    /** Lokaler Re-Scan beim Tab-`onResume` (kein Netz) — entdeckt Install/Uninstall + prunt. */
    fun rescanLocal() = viewModelScope.launch { catalog.scanLocal() }.let {}

    /** Reload-Button: Netz-Repo-Fetch + lokaler Scan über den Koordinator. */
    fun reload() = viewModelScope.launch { coordinator.onManualReload() }.let {}

    fun install(row: BrowserRow) = viewModelScope.launch { catalog.install(row) }.let {}
    fun dismissError() { catalog.dismissError() }

    fun addRepo(url: String) = viewModelScope.launch { catalog.addRepo(url) }.let {}
    fun removeRepo(id: Long) = viewModelScope.launch { catalog.removeRepo(id) }.let {}
    fun setOfficialEnabled(enabled: Boolean) = viewModelScope.launch { catalog.setOfficialEnabled(enabled) }.let {}

    /** Bestätigte Plugin-Quelle persistieren (kind = PLUGIN, TOFU-Pin in extras) + Sync anstoßen. */
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
        coordinator.onServerChanged()
    }.let {}

    fun importPreset(pkg: String, spec: ColorPresetSpec) = viewModelScope.launch {
        val profile = ColorPresetImporter.toProfileOrNull(spec) ?: return@launch
        colorProfiles.upsert(profile.copy(pluginPackage = pkg))
    }.let {}

    fun removeImportedProfile(profile: ColorProfile) = viewModelScope.launch {
        colorProfiles.delete(profile.id)
    }.let {}
}
```

> `repos` für das Repo-Mgmt-Modal kommt weiterhin direkt: ergänze
> `val repos = repoStore.observeActive()...` falls das Modal sie braucht — siehe Task 5; dort wird
> `RepoStore` bei Bedarf zusätzlich injiziert ODER der Catalog exponiert `repos`. **Entscheidung:**
> `PluginCatalog` exponiert `val repos: StateFlow<List<RepoSource>>` (in Task 2 ergänzen:
> `repoStore.observeActive()` ist ein Flow — im Catalog NICHT als StateFlow halten, sondern im VM
> `repoStore`-frei über `catalog`-Methoden; fürs Modal reicht ein `repos`-Flow). Konkret: in Task 2
> `val reposFlow = repoStore.observeActive()` als `Flow` aus dem Catalog geben und im VM
> `val repos = catalog.reposFlow.stateIn(...)`.

- [ ] **Step 2: `ColorPresetImporter`-Importpfad prüfen**

Run: `grep -rn "object ColorPresetImporter\|class ColorPresetImporter" data/ app/`
Expected: ein Treffer — den Importpfad oben exakt anpassen (heute `com.komgareader.data.plugin.ColorPresetImporter`).

- [ ] **Step 3: Build (PluginsScreen/HomeScreen noch nicht angepasst → erwartete Fehler nur dort)**

Run: `./gradlew :app:compileDebugKotlin`
Expected: FAIL nur in `PluginsScreen.kt`/`HomeScreen.kt` (alte VM-API). VM selbst kompiliert. (Wird in Task 5/6 grün.)

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/komgareader/app/ui/plugins/PluginsViewModel.kt
git commit -m "refactor(app): PluginsViewModel dünn — Catalog/Coordinator delegieren, Query+Typ-Filter"
```

---

## Phase 4 — Vereinte `PluginsScreen`

### Task 5: `PluginsScreen` als eine Liste (installiert / Divider / entdeckt) + Repo-Mgmt-Modal

**Files:**
- Modify: `app/src/main/kotlin/com/komgareader/app/ui/plugins/PluginsScreen.kt`

Content-only (TopBar liefert HomeScreen). Liste = installierte Zeilen (oben), `HorizontalDivider` nur bei
`visible.showDivider`, dann entdeckte Zeilen. Such-/Filter-State liest der Screen aus dem VM. Das
Repo-Mgmt-Modal + Error-Dialog ziehen aus `RepoBrowserScreen` hierher. Der „Repo-Browser öffnen"-Button entfällt.

- [ ] **Step 1: `PluginsScreen` ersetzen** (Kern; `PluginRow`/`PresetImportRow` unverändert behalten, `RepoRow`/`RepoManagementModal` aus RepoBrowserScreen übernehmen)

```kotlin
@Composable
fun PluginsScreen(
    modifier: Modifier = Modifier,
    showRepoManagement: Boolean = false,
    onRepoManagementDismiss: () -> Unit = {},
    viewModel: PluginsViewModel = hiltViewModel(),
) {
    val s = LocalStrings.current
    val ctx = LocalContext.current
    val visible by viewModel.visible.collectAsState()
    val profiles by viewModel.profiles.collectAsState()
    val sources by viewModel.sources.collectAsState()
    val presets by viewModel.presetPlugins.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val repos by viewModel.repos.collectAsState()
    val error by viewModel.error.collectAsState()

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.rescanLocal()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var tofuFor by remember { mutableStateOf<DiscoveredPlugin?>(null) }
    var configFor by remember { mutableStateOf<DiscoveredPlugin?>(null) }
    var presetDetailFor by remember { mutableStateOf<DiscoveredPresetPlugin?>(null) }

    fun uninstall(pkg: String) {
        ctx.startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkg")))
    }

    // Schneller Lookup vom InstalledEntry zurück auf das Discovered-Plugin (für ⚙-Aktionen).
    fun sourceFor(pkg: String): DiscoveredPlugin? = sources.firstOrNull { it.packageName == pkg }
    fun presetFor(pkg: String): DiscoveredPresetPlugin? = presets.firstOrNull { it.packageName == pkg }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (visible.installed.isEmpty() && visible.discovered.isEmpty() && !loading) {
            Text(
                s.pluginTabEmpty,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // Installierte (oben)
        visible.installed.forEach { item ->
            when (item.kind) {
                PluginKind.SOURCE -> sourceFor(item.packageName)?.let { src ->
                    PluginRow(
                        title = src.metadata.displayName,
                        typeLabel = s.pluginTabSourceLabel,
                        abiLabel = s.pluginAbiLabel,
                        abiVersion = src.abiVersion,
                        configureLabel = s.pluginConfigure,
                        uninstallLabel = s.pluginUninstall,
                        onConfigure = { tofuFor = src },
                        onUninstall = { uninstall(src.packageName) },
                    )
                }
                PluginKind.PRESET -> presetFor(item.packageName)?.let { p ->
                    PluginRow(
                        title = p.displayName,
                        typeLabel = s.pluginTabPresetLabel,
                        abiLabel = s.pluginAbiLabel,
                        abiVersion = p.abiVersion,
                        configureLabel = s.pluginConfigure,
                        uninstallLabel = s.pluginUninstall,
                        onConfigure = { presetDetailFor = p },
                        onUninstall = { uninstall(p.packageName) },
                    )
                }
            }
        }
        if (visible.showDivider) {
            HorizontalDivider(thickness = EinkTokens.hairline, color = MaterialTheme.colorScheme.outlineVariant)
        }
        if (loading) LoadingIndicator()
        // Entdeckte (unten)
        visible.discovered.forEach { row ->
            RepoRow(row = row, onInstall = { viewModel.install(row) })
        }
    }

    if (showRepoManagement) {
        RepoManagementModal(
            repos = repos,
            onDismiss = onRepoManagementDismiss,
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

    tofuFor?.let { plugin ->
        PluginTofuModal(plugin = plugin, onDismiss = { tofuFor = null }, onConfirm = { tofuFor = null; configFor = plugin })
    }
    configFor?.let { plugin ->
        PluginConfigModal(plugin = plugin, onDismiss = { configFor = null }, onSubmit = { values -> viewModel.addPluginSource(plugin, values); configFor = null })
    }
    presetDetailFor?.let { p ->
        EinkInfoDialog(title = s.pluginPresetsTitle, onDismiss = { presetDetailFor = null }, closeLabel = s.close) {
            p.presets.forEach { spec ->
                PresetImportRow(
                    spec = spec,
                    imported = profiles.any { it.pluginPackage == p.packageName && it.name == spec.name },
                    importLabel = s.pluginPresetImport,
                    removeLabel = s.pluginPresetRemove,
                    importedLabel = s.pluginPresetImported,
                    onImport = { viewModel.importPreset(p.packageName, spec) },
                    onRemove = {
                        profiles.firstOrNull { it.pluginPackage == p.packageName && it.name == spec.name }
                            ?.let { viewModel.removeImportedProfile(it) }
                    },
                )
            }
        }
    }
}
```

- [ ] **Step 2: `RepoRow` + `RepoManagementModal` aus `RepoBrowserScreen.kt` nach `PluginsScreen.kt` kopieren** (private Composables, verbatim aus den gelesenen Zeilen 124–227; `import`s `HorizontalDivider`, `LoadingIndicator`, `EinkToggle`, `PluginKind`, `RepoSource`, `InstallState`, `OutlinedTextField` ergänzen). Den „Repo-Browser öffnen"-`EinkOutlinedButton` NICHT übernehmen.

- [ ] **Step 3: Build**

Run: `./gradlew :app:compileDebugKotlin`
Expected: FAIL nur noch in `HomeScreen.kt` (Aufruf `PluginsScreen(onOpenRepoBrowser=…)` + Reload/Settings-Aktionen). PluginsScreen kompiliert.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/komgareader/app/ui/plugins/PluginsScreen.kt
git commit -m "feat(app): eine Plugins-Seite — installiert oben, Divider, entdeckte unten + Repo-Mgmt/Install inline"
```

---

## Phase 5 — HomeScreen TopBar (TAB_PLUGINS)

### Task 6: Typ-Filter-Chip + ⚙ Repo-Settings + ⟳ Reload im Plugins-Tab

**Files:**
- Modify: `app/src/main/kotlin/com/komgareader/app/ui/home/HomeScreen.kt`

Im Plugins-Tab trägt die zentrale Suche ihren Text in den `PluginsViewModel`; der Typ-Chip sitzt im
`leading`-Slot (wie die Library-Chips); rechts stehen ⚙ (öffnet Repo-Mgmt-Modal über einen State) und ⟳ (Reload).

- [ ] **Step 1: VM + States ergänzen** (oben bei den anderen VMs, ~Z. 100–106)

```kotlin
    val pluginsVm: com.komgareader.app.ui.plugins.PluginsViewModel = hiltViewModel()
    val pluginTypeFilter by pluginsVm.typeFilter.collectAsState()
    var showRepoMgmt by remember { mutableStateOf(false) }
```

- [ ] **Step 2: Suche an Plugins koppeln** — im `EinkSearchBar`-Block: für TAB_PLUGINS `query` live an den VM geben. In `onQueryChange` ergänzen:

```kotlin
                                onQueryChange = {
                                    query = it
                                    if (selected == TAB_PLUGINS) pluginsVm.setQuery(it)
                                },
```

und `onClear` ergänzen: `if (selected == TAB_PLUGINS) pluginsVm.setQuery("")`. Placeholder erweitern:
`placeholder = if (onSettingsTab) s.searchSettingsHint else if (selected == TAB_PLUGINS) s.pluginSearchHint else s.searchMediaHint`.

- [ ] **Step 3: Typ-Chip im `leading`-Slot** — die `leading`-Bedingung erweitern, sodass für TAB_PLUGINS ein Segment-Chip erscheint. Einfachste E-Ink-konforme Variante: drei `TypeFilterChip`-artige Buttons über `AnchoredMenuPopup` ODER ein kompaktes `SegmentedChoiceRow`. **Entscheidung (YAGNI, konsistent mit Library-Chip):** ein einzelner Chip, der durch die drei Werte rotiert (Tap = nächster Filter), Label = aktueller Filter:

```kotlin
                                leading = if (selected == TAB_PLUGINS) {
                                    {
                                        PluginFilterChip(
                                            label = when (pluginTypeFilter) {
                                                PluginTypeFilter.ALL -> s.pluginFilterAll
                                                PluginTypeFilter.SOURCES -> s.pluginFilterSources
                                                PluginTypeFilter.PRESETS -> s.pluginFilterPresets
                                            },
                                            onClick = {
                                                pluginsVm.setTypeFilter(
                                                    when (pluginTypeFilter) {
                                                        PluginTypeFilter.ALL -> PluginTypeFilter.SOURCES
                                                        PluginTypeFilter.SOURCES -> PluginTypeFilter.PRESETS
                                                        PluginTypeFilter.PRESETS -> PluginTypeFilter.ALL
                                                    },
                                                )
                                            },
                                        )
                                    }
                                } else if (selected == TAB_LIBRARY && (typeFilter.value.isNotEmpty() || downloadedOnly)) {
                                    { /* bestehende Library-Chips, unverändert */ }
                                } else null,
```

`PluginFilterChip` als kleines privates Composable unten in HomeScreen.kt anlegen (flach, 1.5px-Border,
`LocalDesignTokens.accent` hinter `allowsAccentColor`, keine Animation) — analog `TypeFilterChip` (Z. 318+),
aber `onClick` statt `onRemove` und ohne ✕-Icon.

- [ ] **Step 4: Action-Icons rechts** — im `when (selected)`-Block (Z. 194) ergänzen:

```kotlin
                                TAB_PLUGINS -> {
                                    IconButton(onClick = { showRepoMgmt = true }) {
                                        Icon(AppIcons.Settings, contentDescription = s.repoBrowserManage)
                                    }
                                    IconButton(onClick = { pluginsVm.reload() }) {
                                        Icon(AppIcons.Refresh, contentDescription = s.repoBrowserRefresh)
                                    }
                                }
```

- [ ] **Step 5: Screen-Aufruf anpassen** (Z. 291):

```kotlin
                        TAB_PLUGINS -> PluginsScreen(
                            showRepoManagement = showRepoMgmt,
                            onRepoManagementDismiss = { showRepoMgmt = false },
                            viewModel = pluginsVm,
                        )
```

- [ ] **Step 6: `onSelect`-Reset** (Z. 299) ergänzen: `showRepoMgmt = false` und `pluginsVm.setQuery("")`.

- [ ] **Step 7: Imports** ergänzen: `com.komgareader.data.plugin.repo.PluginTypeFilter`. `PluginsScreen` ist bereits importiert (Z. 59).

- [ ] **Step 8: Build**

Run: `./gradlew :app:compileDebugKotlin`
Expected: FAIL nur in `MainActivity.kt` (`onOpenRepoBrowser` weg). HomeScreen kompiliert.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/kotlin/com/komgareader/app/ui/home/HomeScreen.kt
git commit -m "feat(app): Plugins-Tab TopBar — Typ-Filter-Chip + Repo-Settings + Reload"
```

---

## Phase 6 — Wiring: MainActivity + delegierende VMs

### Task 7: Route `plugin-repos` entfernen, `onAppStart` aufrufen

**Files:**
- Modify: `app/src/main/kotlin/com/komgareader/app/MainActivity.kt`

- [ ] **Step 1: `SyncCoordinator` injizieren** (bei den anderen `@Inject lateinit var`, ~Z. 51–62):

```kotlin
    @Inject lateinit var syncCoordinator: com.komgareader.app.data.SyncCoordinator
```

- [ ] **Step 2: App-Start-Sync auslösen** — im `setContent`-Block, innerhalb `KomgaReaderTheme { … }` direkt nach `val nav = rememberNavController()` (Z. 121):

```kotlin
                    androidx.compose.runtime.LaunchedEffect(Unit) { syncCoordinator.onAppStart() }
```

- [ ] **Step 3: `HomeScreen`-Aufruf** (Z. 133–137): `onOpenRepoBrowser = …` Zeile **entfernen**.

- [ ] **Step 4: Route entfernen** (Z. 243–245): den `composable("plugin-repos") { … }`-Block **löschen** und den Import `import com.komgareader.app.ui.plugins.repo.RepoBrowserScreen` (Z. 33) entfernen.

- [ ] **Step 5: Build**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL (RepoBrowser-Dateien existieren noch, werden nur nicht mehr referenziert).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/com/komgareader/app/MainActivity.kt
git commit -m "feat(app): SyncCoordinator.onAppStart bei App-Start; plugin-repos-Route entfernt"
```

### Task 8: CollectionsViewModel + SettingsViewModel an den Koordinator delegieren

**Files:**
- Modify: `app/src/main/kotlin/com/komgareader/app/ui/collections/CollectionsViewModel.kt`
- Modify: `app/src/main/kotlin/com/komgareader/app/ui/settings/SettingsViewModel.kt`

- [ ] **Step 1: CollectionsViewModel** — `SyncCoordinator` injizieren, die App-Start-/Tab-Trigger delegieren. `syncOnceOnEnter()` ruft nun `coordinator.onCollectionsTabEntered()` NICHT (das wäre gegated); stattdessen bleibt der erste Eintritt ein voller Sync. **Konkret:** Konstruktor um `private val coordinator: com.komgareader.app.data.SyncCoordinator` erweitern; `syncOnTabOpen()` ersetzen durch `viewModelScope.launch { coordinator.onCollectionsTabEntered() }`. `syncOnceOnEnter()`/`runFullSync()`/`syncNow()` bleiben (sie rufen `sync.fullSync()` direkt — manueller/erster Voll-Sync ist Tab-eigenes Verhalten und liefert `vanished` für das Modal, das der Koordinator nicht durchreicht). Keine Verhaltensänderung, nur `syncOnTabOpen` zieht das Gating aus dem VM in den Koordinator.

```kotlin
    // Tab-Öffnen: Gating-Entscheidung liegt jetzt im Koordinator (zentralisiert).
    fun syncOnTabOpen() = viewModelScope.launch { coordinator.onCollectionsTabEntered() }
```

> Hinweis: `onCollectionsTabEntered` verwirft das `vanished`-Ergebnis (gegateter Hintergrund-Sync).
> Das ist akzeptabel — `vanished` wird beim ersten Eintritt (`syncOnceOnEnter` → `runFullSync`) und bei
> `syncNow()` erfasst. `aggressiveSyncAllowed`-Import im VM kann entfallen, falls nicht mehr genutzt.

- [ ] **Step 2: SettingsViewModel** — `SyncCoordinator` injizieren; in `saveServer()` `runCatching { sync.pullOnlySync() }` ersetzen durch `coordinator.onServerChanged()`. Falls dadurch `sync: CollectionSyncManager` ungenutzt wird, Injektion entfernen. `removeServer()`: nach dem lokalen Cleanup zusätzlich `coordinator.onServerChanged()` ist NICHT nötig (Entfernen pullt nicht) — unverändert lassen.

```kotlin
        coordinator.onServerChanged()
```

- [ ] **Step 3: Build + bestehende Tests**

Run: `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL; alle bestehenden Unit-Tests grün.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/komgareader/app/ui/collections/CollectionsViewModel.kt app/src/main/kotlin/com/komgareader/app/ui/settings/SettingsViewModel.kt
git commit -m "refactor(app): Collections/Settings-Sync-Trigger über SyncCoordinator"
```

---

## Phase 7 — Tote Repo-Browser-Dateien entfernen

### Task 9: `RepoBrowserScreen` + `RepoBrowserViewModel` löschen

**Files:**
- Delete: `app/src/main/kotlin/com/komgareader/app/ui/plugins/repo/RepoBrowserScreen.kt`
- Delete: `app/src/main/kotlin/com/komgareader/app/ui/plugins/repo/RepoBrowserViewModel.kt`

- [ ] **Step 1: Sicherstellen, dass `BrowserRow` schon nach `:data` verschoben ist** (Task 1) — sonst bricht der Build.

Run: `grep -rn "class RepoBrowserViewModel\|fun RepoBrowserScreen\|data class BrowserRow" app/`
Expected: `BrowserRow` NICHT mehr in `app/` (nur noch `:data`); die beiden RepoBrowser-Symbole vor dem Löschen nur in den zu löschenden Dateien.

- [ ] **Step 2: Löschen + Build**

```bash
git rm app/src/main/kotlin/com/komgareader/app/ui/plugins/repo/RepoBrowserScreen.kt app/src/main/kotlin/com/komgareader/app/ui/plugins/repo/RepoBrowserViewModel.kt
./gradlew :app:compileDebugKotlin
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git commit -m "chore(app): toten Repo-Browser-Screen/-VM entfernen (in Plugins-Tab aufgegangen)"
```

---

## Phase 8 — i18n-Strings

### Task 10: Neue Strings (Filter-Chips, Plugin-Suche) DE + EN

**Files:**
- Modify: i18n-Quellen (`grep -rln "pluginTabEmpty" app/src/main` → die `Strings`-Interface- + DE/EN-Impl-Dateien)

- [ ] **Step 1: Keys im `Strings`-Interface ergänzen**

```kotlin
    val pluginSearchHint: String
    val pluginFilterAll: String
    val pluginFilterSources: String
    val pluginFilterPresets: String
```

- [ ] **Step 2: DE-Werte** — `pluginSearchHint = "Plugins suchen"`, `pluginFilterAll = "Alle"`, `pluginFilterSources = "Quellen"`, `pluginFilterPresets = "Presets"`.

- [ ] **Step 3: EN-Werte** — `pluginSearchHint = "Search plugins"`, `pluginFilterAll = "All"`, `pluginFilterSources = "Sources"`, `pluginFilterPresets = "Presets"`.

- [ ] **Step 4: Build (Compile-Zeit-Parität DE/EN)**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL — fehlt ein Key in einer Sprache, bricht der Compiler.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/komgareader/app/i18n/
git commit -m "feat(i18n): Plugin-Suche + Typ-Filter-Labels (DE+EN)"
```

---

## Phase 9 — Doku nachziehen + E2E (docs-match-code)

### Task 11: Skill + Regel + Memory auf den SyncCoordinator nachziehen

**Files:**
- Modify: `.claude/skills/komga-collection-server-sync/SKILL.md` (Pfad via `find .claude -name SKILL.md -path '*collection*'` bestätigen)
- Modify: `.claude/rules/architecture-seams.md`
- Modify: `/home/gabriel/.claude/projects/-home-gabriel-Documents-Projekte-komga-reader/memory/` (Sync-relevantes Memo + MEMORY.md-Zeile)

- [ ] **Step 1: `komga-collection-server-sync`-Skill** — die verstreuten Einstiegspunkte (`syncOnceOnEnter`/`syncOnTabOpen`, Server-Connect→`pullOnlySync`, Plugin-Add→`pullOnlySync`) als „rufen jetzt `SyncCoordinator`" beschreiben; den Koordinator als die Sync-Naht dokumentieren (Ereignis→Aktion-Verträge `onAppStart`/`onServerChanged`/`onManualReload`/`onCollectionsTabEntered`, App-Start-Latch, Gating darin). `CollectionSyncManager` bleibt der Sammlungs-Executor unter dem Koordinator.

- [ ] **Step 2: `architecture-seams.md`** — kurzen Ist-Absatz ergänzen: „SyncCoordinator (app/data, 2026-06-11) bündelt die Sync-/Discovery-Trigger; PluginCatalog hält den geteilten Discovery-Zustand; der Repo-Browser ist in den Plugins-Tab aufgegangen."

- [ ] **Step 3: Memory** — bestehendes Sync-/Plugin-Memo aktualisieren oder neues `sync-coordinator.md` anlegen + MEMORY.md-Zeile.

- [ ] **Step 4: Commit**

```bash
git add .claude docs
git commit -m "docs: SyncCoordinator + vereinte Plugins-Seite (docs-match-code)"
```

### Task 12: E2E auf Emulator (`eink_test`)

**Vorbedingung:** Emulator `eink_test` (1264×1680@300) läuft; lokale Test-Komga + Docker-Kavita erreichbar
([[local-test-komga]], [[local-test-kavita]]).

- [ ] **Step 1: Installieren**

```bash
./gradlew :app:installDebug
```

- [ ] **Step 2: Manuell/Screenshot verifizieren** (oder bestehenden E2E-Test erweitern):
  1. App starten → Plugins-Tab öffnen: installierte Plugins (Kavita/Preset) oben; bei vorhandenen Repo-Treffern Divider + entdeckte unten; ohne installierte KEIN Divider.
  2. Suche oben tippen → filtert installierte + entdeckte.
  3. Typ-Chip durchschalten (Alle→Quellen→Presets) → Liste filtert; Divider verschwindet, wenn ein Abschnitt leer wird.
  4. ⚙ → Repo-Mgmt-Modal (offizielles Repo togglen, URL hinzufügen/entfernen).
  5. ⟳ → Repo-Reload, entdeckte aktualisiert.
  6. Install aus „entdeckt" → OS-Dialog → nach Rückkehr wandert der Eintrag nach „installiert" (onResume-Rescan).
  7. „Plugins entdecken"-Button existiert nicht mehr; es gibt keine separate Seite.

- [ ] **Step 3: Build-Gesamtlauf**

Run: `./gradlew :app:compileDebugKotlin :data:testDebugUnitTest :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, alle Unit-Tests grün.

- [ ] **Step 4: Commit (falls E2E-Testcode ergänzt)**

```bash
git add app/src/androidTest
git commit -m "test(app): E2E vereinte Plugins-Seite (Filter/Divider/Reload/Install)"
```

---

## Selbst-Review-Notizen (vor Übergabe geprüft)

- **Spec-Abdeckung:** A SyncCoordinator (Task 3, 7, 8) · B eine Seite + Route/Button weg (Task 5, 6, 7, 9) ·
  C VM-Merge + `visibleRows` (Task 1, 4) · Typ-Chip + Divider-nur-bei-beiden (Task 1, 5, 6) ·
  live-Fetch-1×-Start + Reload (Task 2, 3) · Doku/Skill (Task 11). Vollständig.
- **Typ-Konsistenz:** `BrowserRow`/`PluginKind`/`InstallState` in `:data`; `visibleRows`/`VisibleRows`/
  `InstalledEntry`/`PluginTypeFilter` durchgehend gleich benannt; `PluginCatalog`-Methoden
  `scanLocal`/`fetchRepos`/`install`/`installedEntries` matchen VM + Koordinator-Lambdas.
- **Offene Verifikation beim Bau:** exakte Importpfade von `PluginInstaller`/`InstallResult`/
  `planPluginPrune`/`ColorPresetImporter` (Task 2/4 Steps prüfen das per `grep`); `PluginCatalog.repos`-Flow
  für das Modal (Task 4-Hinweis).
