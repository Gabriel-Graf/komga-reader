# Font-Plugins P1 — Generische Discover-UX (Info-Modal + Vorschau-Bild) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Jede entdeckte Plugin-Zeile im Plugins-Tab bekommt einen ℹ-Info-Button, der ein Modal mit gerendertem README (inkl. Remote-Bildern) + optionalem Vorschau-Bild öffnet — generisch für alle Plugin-Typen.

**Architecture:** Drei optionale, vorwärtskompatible Metadaten-Felder (`previewUrl`/`readmeUrl`/`license`) in `RepoPluginEntry` (Modul `:data`) fließen über die bestehende `BrowsableEntry`/`BrowserRow`-Kette unverändert zur UI. `PluginRepoClient` lädt das README-Markdown (neues `fetchText`), `PluginCatalog` reicht es durch, `PluginsViewModel` hält den Modal-Zustand, `PluginsScreen` rendert ℹ-Button + `PluginInfoModal` (auf `EinkInfoDialog`-Basis). Markdown rendert die Lib `com.mikepenz:multiplatform-markdown-renderer` (Apache-2.0, coil2-Variante), E-Ink-Bewegung host-gegated.

**Tech Stack:** Kotlin, Jetpack Compose (Material3), Coil 2.7.0, kotlinx.serialization, OkHttp/MockWebServer, JUnit5; `com.mikepenz:multiplatform-markdown-renderer:0.41.0` (android + m3 + coil2).

**Spec:** `docs/superpowers/specs/2026-06-13-font-plugins-p1-discover-ux-design.md`. **Recherche-Belege:** Memory `font-plugins-research` (Lib-Lizenz/AGPL verifiziert).

---

## Dateistruktur (was wird angefasst)

| Datei | Rolle | Aktion |
|---|---|---|
| `gradle/libs.versions.toml` | Version-Catalog | Modify — Lib-Version + 3 Library-Einträge |
| `app/build.gradle.kts` | App-Deps | Modify — 3 `implementation`-Zeilen |
| `data/.../plugin/repo/RepoModels.kt` | Repo-Datenmodell | Modify — 3 Felder an `RepoPluginEntry` |
| `data/.../plugin/repo/RepoIndexParser.kt` | URL-Auflösung | Modify — `resolveApkUrl` → `resolveRepoUrl` (generisch) |
| `data/.../plugin/repo/PluginRepoClient.kt` | HTTPS-I/O | Modify — `fetchText(url)` |
| `data/src/test/.../RepoIndexParserTest.kt` | Parser-Tests | Modify — neue Felder + Rename |
| `data/src/test/.../PluginRepoClientTest.kt` | Client-Tests | Modify — `fetchText`-Tests |
| `app/.../data/PluginCatalog.kt` | Wiring/Discovery | Modify — `fetchReadme`-Delegation + Rename-Call-Site |
| `app/.../ui/plugins/PluginsViewModel.kt` | Tab-State | Modify — Info-Modal-Zustand |
| `app/.../ui/plugins/PluginInfoModal.kt` | Info-Modal | **Create** |
| `app/.../ui/plugins/PluginsScreen.kt` | Tab-UI | Modify — ℹ-Button + Modal-Verdrahtung |
| `app/.../i18n/Strings.kt` | i18n | Modify — neue Keys (de+en) |
| `NOTICE`, `README.md` | Lizenz/Doku | Modify — Lib-Attribution + Link |
| `app/src/androidTest/.../PluginInfoModalTest.kt` | UI-Test | **Create** |

---

## Task 1: Markdown-Renderer-Abhängigkeit + API-Spike

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts:69` (bei den Compose/Coil-Deps)

- [ ] **Step 1: Version + Library-Einträge in den Catalog**

In `gradle/libs.versions.toml` unter `[versions]` (z. B. nach `coil = "2.7.0"`):

```toml
mikepenzMarkdown = "0.41.0"
```

Unter `[libraries]` (z. B. nach `coil-compose = ...`):

```toml
markdown-renderer-android = { module = "com.mikepenz:multiplatform-markdown-renderer-android", version.ref = "mikepenzMarkdown" }
markdown-renderer-m3 = { module = "com.mikepenz:multiplatform-markdown-renderer-m3", version.ref = "mikepenzMarkdown" }
markdown-renderer-coil2 = { module = "com.mikepenz:multiplatform-markdown-renderer-coil2", version.ref = "mikepenzMarkdown" }
```

- [ ] **Step 2: App-Modul-Deps**

In `app/build.gradle.kts` direkt nach `implementation(libs.coil.compose)` (Zeile ~69):

```kotlin
implementation(libs.markdown.renderer.android)
implementation(libs.markdown.renderer.m3)
implementation(libs.markdown.renderer.coil2)
```

- [ ] **Step 3: Auflösung verifizieren**

Run: `./gradlew :app:dependencies --configuration debugRuntimeClasspath | grep -i markdown`
Expected: drei `com.mikepenz:multiplatform-markdown-renderer-*:0.41.0`-Zeilen (aufgelöst, kein FAILED).

- [ ] **Step 4: API-Spike — exakte 0.41.0-Symbole festnageln**

Die exakten Paket-/Klassennamen gegen das aufgelöste Artefakt bestätigen (NICHT raten). Quelle:
`https://github.com/mikepenz/multiplatform-markdown-renderer/tree/v0.41.0` bzw. die entpackten
`.aar`/`-sources`. Drei Symbole notieren (als Kommentar in `PluginInfoModal.kt` in Task 5 eintragen):
1. Der M3-`Markdown`-Composable-Entrypoint (erwartet: `com.mikepenz.markdown.m3.Markdown`).
2. Der coil2-Image-Transformer (erwartet: `com.mikepenz.markdown.coil2.Coil2ImageTransformerImpl`).
3. Die Animations-Factory + ihr Opt-out-Parameter (erwartet: `com.mikepenz.markdown.model.markdownAnimations`, Param `animateTextSize: Modifier.() -> Modifier`).

Run (Hilfe beim Finden): `find ~/.gradle/caches -path '*multiplatform-markdown-renderer-m3*' -name '*.aar' 2>/dev/null | head` und ggf. `unzip -l <aar>`.
Expected: die drei Symbole existieren; Task 5 verwendet die bestätigten Namen.

- [ ] **Step 5: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts
git commit -m "build: multiplatform-markdown-renderer 0.41.0 (android+m3+coil2) für Plugin-Info-Modal"
```

---

## Task 2: `RepoPluginEntry`-Felder + generische URL-Auflösung (`:data`, pure, TDD)

**Files:**
- Modify: `data/src/main/kotlin/com/komgareader/data/plugin/repo/RepoModels.kt:20-31`
- Modify: `data/src/main/kotlin/com/komgareader/data/plugin/repo/RepoIndexParser.kt:18-22`
- Modify: `data/src/test/kotlin/com/komgareader/data/plugin/repo/RepoIndexParserTest.kt`
- Modify: `app/src/main/kotlin/com/komgareader/app/data/PluginCatalog.kt:20,242` (Rename-Call-Site)

- [ ] **Step 1: Failing-Tests schreiben**

In `RepoIndexParserTest.kt` ergänzen (die bestehenden `resolveApkUrl_*`-Tests auf den neuen Namen `resolveRepoUrl` umbenennen — verbatim ersetzen, gleiche Assertions):

```kotlin
@Test fun parsesOptionalMetadataFields() {
    val json = """
        {"name":"R","plugins":[{
          "packageName":"com.x","apkUrl":"x.apk","fingerprint":"ab","versionCode":1,
          "previewUrl":"img/x.png","readmeUrl":"x/README.md","license":"OFL-1.1"
        }]}
    """.trimIndent()
    val e = parseRepoIndex(json)!!.plugins.single()
    assertEquals("img/x.png", e.previewUrl)
    assertEquals("x/README.md", e.readmeUrl)
    assertEquals("OFL-1.1", e.license)
}

@Test fun optionalMetadataDefaultsToEmpty() {
    val json = """{"name":"R","plugins":[{"packageName":"com.x","apkUrl":"x.apk","fingerprint":"ab","versionCode":1}]}"""
    val e = parseRepoIndex(json)!!.plugins.single()
    assertEquals("", e.previewUrl)
    assertEquals("", e.readmeUrl)
    assertEquals("", e.license)
}

@Test fun resolveRepoUrl_absolutePassesThrough() {
    assertEquals("https://x/c.png", resolveRepoUrl("https://r/repo.json", "https://x/c.png"))
}

@Test fun resolveRepoUrl_relativeAgainstRepoBase() {
    assertEquals("https://r/sub/p/a.png", resolveRepoUrl("https://r/sub/repo.json", "p/a.png"))
    assertEquals("https://r/p/a.png", resolveRepoUrl("https://r/repo.json", "/p/a.png"))
}
```

Die alten `resolveApkUrl_absolutePassesThrough` / `resolveApkUrl_relativeAgainstRepoBase` (Zeilen 44-51) löschen (durch obige ersetzt).

- [ ] **Step 2: Test ausführen, Fehlschlag bestätigen**

Run: `./gradlew :data:testDebugUnitTest --tests "com.komgareader.data.plugin.repo.RepoIndexParserTest"`
Expected: FAIL — `previewUrl`/`readmeUrl`/`license` unbekannt, `resolveRepoUrl` nicht aufgelöst.

- [ ] **Step 3: Felder + Rename implementieren**

`RepoModels.kt` — `RepoPluginEntry` um drei Felder erweitern (ans Ende, vor `)`):

```kotlin
    val apkUrl: String = "",
    val fingerprint: String = "",
    val previewUrl: String = "",
    val readmeUrl: String = "",
    val license: String = "",
)
```

`RepoIndexParser.kt` — `resolveApkUrl` in `resolveRepoUrl` umbenennen + generischen Doc:

```kotlin
/** Löst eine repo-relative URL gegen die Basis der [repoUrl] auf; absolute http(s)-URL unverändert.
 *  Generisch für apkUrl/previewUrl/readmeUrl. */
fun resolveRepoUrl(repoUrl: String, path: String): String {
    if (path.startsWith("http://") || path.startsWith("https://")) return path
    return URI(repoUrl).resolve(path).toString()
}
```

`PluginCatalog.kt` — Import (Zeile 20) + Call-Site (Zeile 242) auf den neuen Namen ziehen:

```kotlin
import com.komgareader.data.plugin.repo.resolveRepoUrl
```
```kotlin
        val url = resolveRepoUrl(row.item.repoUrl, row.item.entry.apkUrl)
```

- [ ] **Step 4: Tests grün**

Run: `./gradlew :data:testDebugUnitTest --tests "com.komgareader.data.plugin.repo.RepoIndexParserTest"`
Expected: PASS (alle, inkl. der umbenannten resolve-Tests).

- [ ] **Step 5: App kompiliert (Rename-Call-Site)**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL (kein verbleibendes `resolveApkUrl`).

- [ ] **Step 6: Commit**

```bash
git add data/src/main/kotlin/com/komgareader/data/plugin/repo/RepoModels.kt data/src/main/kotlin/com/komgareader/data/plugin/repo/RepoIndexParser.kt data/src/test/kotlin/com/komgareader/data/plugin/repo/RepoIndexParserTest.kt app/src/main/kotlin/com/komgareader/app/data/PluginCatalog.kt
git commit -m "feat(data): repo.json previewUrl/readmeUrl/license (generisch) + resolveApkUrl→resolveRepoUrl"
```

---

## Task 3: `PluginRepoClient.fetchText` (`:data`, TDD mit MockWebServer)

**Files:**
- Modify: `data/src/main/kotlin/com/komgareader/data/plugin/repo/PluginRepoClient.kt:14-23`
- Modify: `data/src/test/kotlin/com/komgareader/data/plugin/repo/PluginRepoClientTest.kt`

- [ ] **Step 1: Failing-Tests schreiben**

In `PluginRepoClientTest.kt` (Muster der vorhandenen `fetchIndex`-Tests, Zeilen 20-31):

```kotlin
@Test fun fetchTextReturnsBody() = runTest {
    server.enqueue(MockResponse().setBody("# Hallo\n![x](https://h/x.png)"))
    val client = PluginRepoClient(OkHttpClient())
    val body = client.fetchText(server.url("/README.md").toString())
    assertEquals("# Hallo\n![x](https://h/x.png)", body)
}

@Test fun fetchTextReturnsNullOn404() = runTest {
    server.enqueue(MockResponse().setResponseCode(404))
    val client = PluginRepoClient(OkHttpClient())
    assertNull(client.fetchText(server.url("/missing").toString()))
}
```

(Imports `assertEquals`/`assertNull` sind in der Datei vorhanden — sonst ergänzen.)

- [ ] **Step 2: Test ausführen, Fehlschlag bestätigen**

Run: `./gradlew :data:testDebugUnitTest --tests "com.komgareader.data.plugin.repo.PluginRepoClientTest"`
Expected: FAIL — `fetchText` existiert nicht.

- [ ] **Step 3: `fetchText` implementieren**

In `PluginRepoClient.kt` nach `fetchIndex` (vor `download`):

```kotlin
    /** Lädt beliebigen Text (z. B. README-Markdown); null bei Netz-/HTTP-Fehler. */
    suspend fun fetchText(url: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            http.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                resp.body?.string()
            }
        }.getOrNull()
    }
```

- [ ] **Step 4: Tests grün**

Run: `./gradlew :data:testDebugUnitTest --tests "com.komgareader.data.plugin.repo.PluginRepoClientTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add data/src/main/kotlin/com/komgareader/data/plugin/repo/PluginRepoClient.kt data/src/test/kotlin/com/komgareader/data/plugin/repo/PluginRepoClientTest.kt
git commit -m "feat(data): PluginRepoClient.fetchText für README-Markdown"
```

---

## Task 4: `fetchReadme`-Delegation + Info-Modal-State (`:app`)

**Files:**
- Modify: `app/src/main/kotlin/com/komgareader/app/data/PluginCatalog.kt` (neue Methode)
- Modify: `app/src/main/kotlin/com/komgareader/app/ui/plugins/PluginsViewModel.kt`

- [ ] **Step 1: `fetchReadme` in `PluginCatalog`**

In `PluginCatalog.kt` (z. B. nach `dismissError()`, Zeile ~126) — der Catalog kapselt den
`client`, das ViewModel kennt `PluginRepoClient` nicht:

```kotlin
    /** README-Markdown eines entdeckten Plugins laden (null bei Fehler/leer). */
    suspend fun fetchReadme(url: String): String? =
        if (url.isBlank()) null else client.fetchText(url)
```

- [ ] **Step 2: Info-State im `PluginsViewModel`**

In `PluginsViewModel.kt`:

ReadmeState-Typ (oben in der Datei, vor der Klasse):

```kotlin
/** Zustand des README-Bereichs im Plugin-Info-Modal. */
sealed interface ReadmeState {
    data object Loading : ReadmeState
    data class Loaded(val markdown: String) : ReadmeState
    data object Empty : ReadmeState   // kein README / Fehler → description-Fallback in der UI
}
```

In der Klasse (z. B. nach den `typeFilter`-Feldern):

```kotlin
    private val _infoFor = MutableStateFlow<BrowserRow?>(null)
    val infoFor: StateFlow<BrowserRow?> = _infoFor.asStateFlow()

    private val _readmeState = MutableStateFlow<ReadmeState>(ReadmeState.Empty)
    val readmeState: StateFlow<ReadmeState> = _readmeState.asStateFlow()

    /** Öffnet das Info-Modal für [row] und lädt — falls vorhanden — dessen README. */
    fun openInfo(row: BrowserRow) {
        _infoFor.value = row
        val raw = row.item.entry.readmeUrl
        if (raw.isBlank()) { _readmeState.value = ReadmeState.Empty; return }
        _readmeState.value = ReadmeState.Loading
        viewModelScope.launch {
            val url = resolveRepoUrl(row.item.repoUrl, raw)
            val md = catalog.fetchReadme(url)
            _readmeState.value = if (md.isNullOrBlank()) ReadmeState.Empty else ReadmeState.Loaded(md)
        }
    }

    /** Schließt das Info-Modal. */
    fun closeInfo() { _infoFor.value = null; _readmeState.value = ReadmeState.Empty }
```

Import ergänzen: `import com.komgareader.data.plugin.repo.resolveRepoUrl`.

- [ ] **Step 3: Kompilieren**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/komgareader/app/data/PluginCatalog.kt app/src/main/kotlin/com/komgareader/app/ui/plugins/PluginsViewModel.kt
git commit -m "feat(app): Plugin-Info-Modal-State + fetchReadme-Delegation"
```

---

## Task 5: `PluginInfoModal` + ℹ-Button + i18n (`:app`)

**Files:**
- Create: `app/src/main/kotlin/com/komgareader/app/ui/plugins/PluginInfoModal.kt`
- Modify: `app/src/main/kotlin/com/komgareader/app/ui/plugins/PluginsScreen.kt` (RepoRow + Verdrahtung)
- Modify: `app/src/main/kotlin/com/komgareader/app/i18n/Strings.kt`

- [ ] **Step 1: i18n-Keys (Interface + de + en)**

In `Strings.kt` im Strings-Interface (bei den `plugin*`-Keys, ~Zeile 294):

```kotlin
    val pluginInfo: String
    val pluginInfoLicense: String
    val pluginInfoLoadingReadme: String
    val pluginInfoNoDescription: String
```

In der DE-Impl (`GermanStrings`, bei den `plugin*`-Overrides):

```kotlin
    override val pluginInfo = "Info"
    override val pluginInfoLicense = "Lizenz"
    override val pluginInfoLoadingReadme = "README wird geladen…"
    override val pluginInfoNoDescription = "Keine Beschreibung verfügbar."
```

In der EN-Impl (`EnglishStrings`):

```kotlin
    override val pluginInfo = "Info"
    override val pluginInfoLicense = "License"
    override val pluginInfoLoadingReadme = "Loading README…"
    override val pluginInfoNoDescription = "No description available."
```

- [ ] **Step 2: `PluginInfoModal` erstellen**

> Vor dem Schreiben: die in **Task 1 Step 4** notierten exakten 0.41.0-Symbole verwenden. Unten
> stehen die erwarteten Namen — bei Abweichung die bestätigten einsetzen.

Create `app/src/main/kotlin/com/komgareader/app/ui/plugins/PluginInfoModal.kt`:

```kotlin
package com.komgareader.app.ui.plugins

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.request.ImageRequest
import com.komgareader.app.i18n.LocalStrings
import com.komgareader.app.ui.components.EinkInfoDialog
import com.komgareader.app.ui.components.FilteredAsyncImage
import com.komgareader.app.ui.components.LoadingIndicator
import com.komgareader.app.ui.components.LocalEinkMode
import com.komgareader.data.plugin.repo.BrowserRow
import com.komgareader.data.plugin.repo.PluginKind
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.coil2.Coil2ImageTransformerImpl
import com.mikepenz.markdown.model.markdownAnimations

/**
 * Plugin-Info-Modal: Kopf (Name/Typ/Version/Lizenz), optionales Vorschau-Bild, gerendertes
 * README (Remote-Bilder an) mit description-Fallback. Onyx-Look über [EinkInfoDialog];
 * Bewegung host-gegated (kein animateContentSize auf E-Ink).
 */
@Composable
fun PluginInfoModal(
    row: BrowserRow,
    readme: ReadmeState,
    onDismiss: () -> Unit,
) {
    val s = LocalStrings.current
    val ctx = LocalContext.current
    val eink = LocalEinkMode.current
    val entry = row.item.entry
    val typeLabel = when (row.item.kind) {
        PluginKind.SOURCE -> s.pluginTabSourceLabel
        PluginKind.PRESET -> s.pluginTabPresetLabel
        PluginKind.LANGUAGE -> s.pluginTabLanguageLabel
        PluginKind.READER_PRESET -> s.pluginTabReaderPresetLabel
        PluginKind.UI_PACK -> s.pluginTabUiPackLabel
    }

    EinkInfoDialog(title = entry.name, onDismiss = onDismiss, closeLabel = s.close) {
        Text(
            "$typeLabel · v${entry.versionName}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (entry.license.isNotBlank()) {
            Text(
                "${s.pluginInfoLicense}: ${entry.license}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (entry.previewUrl.isNotBlank()) {
            val url = com.komgareader.data.plugin.repo.resolveRepoUrl(row.item.repoUrl, entry.previewUrl)
            FilteredAsyncImage(
                model = ImageRequest.Builder(ctx).data(url).crossfade(false).build(),
                contentDescription = entry.name,
                modifier = Modifier.fillMaxWidth().height(120.dp),
            )
        }
        when (readme) {
            is ReadmeState.Loading -> LoadingIndicator()
            is ReadmeState.Loaded -> Markdown(
                content = readme.markdown,
                imageTransformer = Coil2ImageTransformerImpl,
                animations = markdownAnimations(
                    animateTextSize = { if (eink) this else animateContentSize() },
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            is ReadmeState.Empty -> Text(
                entry.description.ifBlank { s.pluginInfoNoDescription },
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
```

> Falls `Markdown` in 0.41.0 nicht-optionale `colors`/`typography`-Parameter erwartet: die
> M3-Defaults aus dem `-m3`-Modul übergeben (`com.mikepenz.markdown.m3.markdownColor()` /
> `markdownTypography()`). Falls die coil2-Transformer-Instanz einen `ImageLoader` braucht, den
> App-Loader (`LocalContext`-Coil-Singleton bzw. via Konstruktor) reichen — in Task 1 Step 4
> geklärt.

- [ ] **Step 3: ℹ-Button in `RepoRow` + Verdrahtung in `PluginsScreen`**

In `PluginsScreen.kt` — `RepoRow`-Signatur um `onInfo` erweitern und den Button **vor** dem
Install-Block rendern. Ersetze den `when { … }`-Block-Anfang so, dass davor der ℹ-Button steht:

```kotlin
private fun RepoRow(row: BrowserRow, onInfo: () -> Unit, onInstall: () -> Unit) {
```

Im `Row { … }` direkt vor dem `when { … }` (nach der `Column(Modifier.weight(1f)) { … }`):

```kotlin
        IconButton(onClick = onInfo) {
            Icon(AppIcons.Info, contentDescription = s.pluginInfo, modifier = Modifier.size(22.dp))
        }
```

Call-Site (Zeile ~191) anpassen:

```kotlin
        visible.discovered.forEach { row ->
            RepoRow(row = row, onInfo = { viewModel.openInfo(row) }, onInstall = { viewModel.install(row) })
        }
```

Modal rendern — am Ende von `PluginsScreen` (z. B. nach dem `presetDetailFor`-Block):

```kotlin
    val infoFor by viewModel.infoFor.collectAsState()
    val readmeState by viewModel.readmeState.collectAsState()
    infoFor?.let { row ->
        PluginInfoModal(row = row, readme = readmeState, onDismiss = { viewModel.closeInfo() })
    }
```

(`collectAsState`/`getValue` sind bereits importiert.)

- [ ] **Step 4: Kompilieren**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL. Bei Markdown-API-Fehlern die in Task 1 Step 4 bestätigten Symbole einsetzen.

- [ ] **Step 5: i18n-Parität prüfen**

Run: `./gradlew :app:compileDebugKotlin` (die `Strings`-Schnittstelle erzwingt Compile-Parität — fehlt ein Override in DE/EN, bricht der Build).
Expected: BUILD SUCCESSFUL (beide Sprachen vollständig).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/com/komgareader/app/ui/plugins/PluginInfoModal.kt app/src/main/kotlin/com/komgareader/app/ui/plugins/PluginsScreen.kt app/src/main/kotlin/com/komgareader/app/i18n/Strings.kt
git commit -m "feat(app): ℹ-Info-Button + Plugin-Info-Modal (README-Render + Vorschau-Bild)"
```

---

## Task 6: Lizenz-/Doku-Pflichten (NOTICE + README)

**Files:**
- Modify: `NOTICE`
- Modify: `README.md`

- [ ] **Step 1: NOTICE-Eintrag**

In `NOTICE` unter „Drittsoftware:" eine Zeile ergänzen:

```
- multiplatform-markdown-renderer (Markdown-Render im Plugin-Info-Modal) — Apache-2.0, Mike Penz.
  https://github.com/mikepenz/multiplatform-markdown-renderer — enthält Teile unter MIT (Copyright 2021 Erik Hellman); Remote-Bilder via Coil (Apache-2.0).
```

- [ ] **Step 2: README-Lizenzzeile + Link**

In `README.md` in der `## License`-Sektion die „Bundled assets"-Zeile bzw. eine
Dependency-Zeile um die Lib ergänzen (mit Link, User-Wunsch). Beispiel-Ergänzung nach der
Bundled-assets-Zeile:

```markdown
Key UI dependency: [multiplatform-markdown-renderer](https://github.com/mikepenz/multiplatform-markdown-renderer)
(Apache‑2.0) renders plugin READMEs in the in‑app info modal.
```

- [ ] **Step 3: Commit**

```bash
git add NOTICE README.md
git commit -m "docs: NOTICE + README — multiplatform-markdown-renderer (Apache-2.0) Attribution"
```

---

## Task 7: UI-Test — Info-Modal (`:app` androidTest)

**Files:**
- Create: `app/src/androidTest/kotlin/com/komgareader/app/ui/plugins/PluginInfoModalTest.kt`

- [ ] **Step 1: Compose-UI-Test schreiben**

Testet die zwei verifizierbaren UI-Pfade ohne Netz: (a) `Empty` → description-Fallback sichtbar;
(b) `Loaded` ohne Remote-Bild → Markdown-Text sichtbar. (Remote-Bild-Render = manueller E2E,
Step 3.)

```kotlin
package com.komgareader.app.ui.plugins

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.komgareader.data.plugin.repo.BrowsableEntry
import com.komgareader.data.plugin.repo.BrowserRow
import com.komgareader.data.plugin.repo.InstallState
import com.komgareader.data.plugin.repo.PluginKind
import com.komgareader.data.plugin.repo.RepoPluginEntry
import org.junit.Rule
import org.junit.Test

class PluginInfoModalTest {
    @get:Rule val rule = createComposeRule()

    private fun row(description: String = "", license: String = "") = BrowserRow(
        item = BrowsableEntry(
            entry = RepoPluginEntry(
                packageName = "com.x", name = "Demo Plugin", description = description,
                versionName = "1.2", apkUrl = "x.apk", fingerprint = "ab", versionCode = 1,
                license = license,
            ),
            repoName = "R", repoUrl = "https://r/repo.json", kind = PluginKind.SOURCE,
        ),
        state = InstallState.NOT_INSTALLED, compatible = true,
    )

    @Test fun emptyReadmeShowsDescription() {
        rule.setContent {
            PluginInfoModal(row = row(description = "Eine Demo-Beschreibung."), readme = ReadmeState.Empty, onDismiss = {})
        }
        rule.onNodeWithText("Eine Demo-Beschreibung.").assertIsDisplayed()
    }

    @Test fun loadedReadmeShowsMarkdownText() {
        rule.setContent {
            PluginInfoModal(row = row(), readme = ReadmeState.Loaded("Hallo Welt Markdown"), onDismiss = {})
        }
        rule.onNodeWithText("Hallo Welt Markdown", substring = true).assertIsDisplayed()
    }
}
```

> Falls `PluginInfoModal` `LocalStrings`/`LocalEinkMode` ohne Provider liest und der Test crasht:
> den Inhalt in den App-Theme-/Strings-Provider wrappen (Muster aus einem bestehenden
> `*SlotPreview`/androidTest übernehmen — z. B. `KomgaReaderTheme { CompositionLocalProvider(LocalStrings provides GermanStrings) { … } }`).

- [ ] **Step 2: Test ausführen**

Run: `./gradlew :app:connectedDebugAndroidTest --tests "com.komgareader.app.ui.plugins.PluginInfoModalTest"` (Emulator `eink_test` läuft).
Expected: PASS (2 Tests).

- [ ] **Step 3: Manuelle E2E-Verifikation (Remote-Bild + README-Render)**

Mit einem lokalen HTTP-Server eine `repo.json` + `README.md` + Specimen-`PNG` servieren (oder
das spätere P3-Test-Repo). Im Emulator: Plugins-Tab → entdeckte Zeile → ℹ → Modal zeigt
Vorschau-Bild + gerendertes README inkl. eingebettetem Bild, **ohne** Bewegung (E-Ink-Modus).
Beweis per Screenshot ablegen.

- [ ] **Step 4: Commit**

```bash
git add app/src/androidTest/kotlin/com/komgareader/app/ui/plugins/PluginInfoModalTest.kt
git commit -m "test(app): Plugin-Info-Modal UI-Test (description-Fallback + Markdown-Render)"
```

---

## Task 8: Doku-Sync + Abschluss

**Files:**
- Modify: ggf. `CLAUDE.md` / `.claude/rules/*` / `docs/*` (nur falls P1 sie veraltet)

- [ ] **Step 1: `komga-doc-sync` durchgehen**

Prüfen, ob P1 (generische Plugin-Discover-UX, neue repo.json-Felder) eine Rule/Doku veraltet
(z. B. Erwähnung der repo.json-Felder in `architecture-seams.md`/README-Extending). Betroffenes
im selben Schritt nachziehen. `data-provenance.md` betrifft P1 **nicht** (keine Datenquelle).

- [ ] **Step 2: Volle Test-Runde**

Run: `./gradlew :data:testDebugUnitTest :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL, alle Unit-Tests grün.

- [ ] **Step 3: Commit (falls Doku-Änderungen)**

```bash
git add -A && git commit -m "docs: P1 Discover-UX Ist-Stand nachgezogen (doc-sync)"
```

---

## Selbst-Review (Spec-Abdeckung)

- Ziel 1 (3 Felder) → Task 2. ✓
- Ziel 2 (ℹ-Button Discover-Zeile) → Task 5 Step 3. ✓
- Ziel 3 (Modal: Kopf/Lizenz/Bild/README+Fallback) → Task 5 Step 2. ✓
- Ziel 4 (AGPL-kompatible compose-native Lib, E-Ink-Anim gegated) → Task 1 + Task 5 (`markdownAnimations`-Opt-out). ✓
- Ziel 5 (Lizenz/Doku-Pflicht) → Task 6. ✓
- Fehlerbehandlung (readme leer/Fehler → description; previewUrl leer → weg) → Task 4 (`openInfo`) + Task 5 (conditional Render). ✓
- E-Ink (keine Bewegung, crossfade(false), EinkInfoDialog) → Task 5. ✓
- Tests (parser/client pure + UI + manuell E2E) → Tasks 2,3,7. ✓
- Nicht-Ziele (Font-Kategorie/Lizenz-Block/Specimen) bewusst draußen → P2/P3. ✓

Typkonsistenz: `ReadmeState`/`openInfo`/`closeInfo`/`fetchReadme`/`resolveRepoUrl`/`PluginInfoModal(row, readme, onDismiss)` über Tasks 4/5/7 identisch verwendet. ✓
