# Integrationstest — Plan 6: Plugin-E2E (Tab-UI + Color-Preset + Kavita-Fixture)

> **For agentic workers:** REQUIRED SUB-SKILL: subagent-driven-development / executing-plans. Checkbox-Steps.

**Goal:** Die Plugin-Schritte (Phase-4-Loader) reproduzierbar im Integrations-Suite-Stil abdecken,
ergänzend zum schon vorhandenen `KavitaPluginLiveTest` (Source-Plugin-Lade-Kette). Drei Stücke:
1. **Plugin-Tab-UI-E2E** — Entdeckung→**UI-Integration**: das installierte Kavita-Plugin erscheint im
   Plugins-Tab (die „nicht nur lädt, sondern integriert"-Garantie), über die Hilt-UI-Harness.
2. **Color-Preset-Plugin-E2E** — `PluginHost.discoverColorPresetPlugins()` findet das (data-only)
   Kindle-Preset-APK und parst dessen `ColorPresetSpec`s.
3. **Kavita reproduzierbar** — Kavita-Instanz in die `tools/ci-fixtures`-Orchestrierung, damit der
   Source-Plugin-E2E gegen eine frisch geseedete Kavita statt den manuellen Container läuft.

**Architecture:** (1)+(2) sind **server-los** — sie hängen nur an installierten Plugin-APKs (Discovery
über den realen `PackageManager`/`PluginHost`, nicht am Quell-Server). (1) nutzt die Plan-4-Hilt-UI-
Harness (`UiTestBase`), kein Server geseedet. (2) ist ein On-Device-Test direkt auf `PluginHost`.
(3) spiegelt die Komga-Fixture-Orchestrierung; die dynamisch erzeugte Kavita-`apiKey` wird vom
Seed emittiert und dem Source-Plugin-E2E per **Instrumentation-Argument** übergeben.

**Voraussetzungen:** Emulator `emulator-5554`; Kavita-Plugin-APK (`com.komgareader.plugin.kavita`) und
Kindle-Preset-APK (`com.komgareader.plugin.preset.kindle` o.ä.) auf dem Emulator installiert (Task 1
baut+installiert das Preset-APK; Kavita-APK ist bereits installiert). Plugin-Quell-Repos liegen unter
`plugin/` im Haupt-Checkout (gitignored).

**Bezug:** CLAUDE.md (Plugin-Loader Ist-Stand), `architecture-seams.md` (Naht A Plugin-Branch),
Memories [[plugin-host-kavita]] / [[local-test-kavita]]. Ergänzt `KavitaPluginLiveTest`.

---

## Grounding (verifiziert)

- `PluginHost(context)`: `discoverPlugins(): List<DiscoveredPlugin>` (je `packageName`, `metadata.displayName`,
  `abiVersion`, `entryClass`, `signatureSha256`, `configSchema`), `sourceFor(pkg, entry, sig, config): BrowsableSource?`,
  `discoverColorPresetPlugins(): List<DiscoveredPresetPlugin>` (`packageName`, `displayName?`, `abiVersion`,
  `presets: List<ColorPresetSpec>`).
- `PluginsScreen` (Bottom-Nav-Tab **„Plugins"**, `navPlugins`): listet Quellen-Plugins (Zeile mit
  `Text(metadata.displayName)` z.B. **„Kavita"** + ABI-Label) und Preset-Plugins; je Zeile ⚙/🗑.
- Kavita-Plugin: `packageName = "com.komgareader.plugin.kavita"`, `displayName = "Kavita"`, `abiVersion = 1`,
  ConfigSchema-Felder `url`/`apiKey`.
- Hilt-UI-Harness (Plan 4a): `UiTestBase` (HiltAndroidRule + createEmptyComposeRule + `inject()`/`launch()`),
  `TestDataModule` ersetzt nur `DataModule` (DB) — **PluginHost** kommt aus `AppModule` (real, entdeckt
  installierte APKs). Selektion über DE-Texte.
- Komga-Fixture-Muster (Plan 1): `tools/ci-fixtures/{docker-compose.yml, seed.sh, up.sh, verify.sh, manifest.json}`.
- Kavita-Seed-Flow ([[local-test-kavita]]): `POST /api/account/register` → `token`+`apiKey`;
  `POST /api/library/create` (Pflichtfelder `name,type,folders,fileGroupTypes:[1,2,3,4],excludePatterns:[]`);
  `POST /api/library/scan?libraryId=&force=true`; `POST /api/series/v2 {}` → Liste.

---

## Task 1: Kindle-Preset-Plugin-APK bauen + installieren (Prereq für Task 3)

**Files:** keine (Setup-Schritt; APK aus separatem Repo).

- [ ] **Step 1: APK bauen**

Run: `cd /home/gabriel/Documents/Projekte/komga-reader/plugin/komga-eink-preset-kindle && ./gradlew assembleDebug 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL; APK unter `app/build/outputs/apk/debug/*.apk` (oder `build/outputs/...`).

- [ ] **Step 2: Auf den Emulator installieren**

Run: `APK=$(find /home/gabriel/Documents/Projekte/komga-reader/plugin/komga-eink-preset-kindle -name '*.apk' -path '*debug*' | head -1); ANDROID_SERIAL=emulator-5554 adb install -r "$APK"`
Expected: `Success`.

- [ ] **Step 3: Verifizieren**

Run: `ANDROID_SERIAL=emulator-5554 adb shell pm list packages | grep -i 'komgareader.plugin'`
Expected: sowohl `com.komgareader.plugin.kavita` als auch das Preset-Paket gelistet. Den **exakten
Preset-Paketnamen** notieren (für Task 3 + die Doku).

*(Kein Commit — reiner Geräte-Setup-Schritt.)*

---

## Task 2: Plugin-Tab-UI-E2E (Entdeckung → UI-Integration)

**Files:**
- Create: `app/src/androidTest/kotlin/com/komgareader/app/ci/ui/PluginTabUiTest.kt`

- [ ] **Step 1: Testklasse schreiben**

```kotlin
package com.komgareader.app.ci.ui

import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Spec-Garantie „Plugin lädt UND ist in der UI integriert": das installierte Kavita-Quellen-Plugin
 * erscheint im Plugins-Tab. Server-los — listet nur installierte APKs (realer PluginHost aus AppModule).
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class PluginTabUiTest : UiTestBase() {

    @Test fun installiertes_kavita_plugin_erscheint_im_plugins_tab() {
        inject()
        launch()   // kein Server nötig — Plugin-Discovery ist serverunabhängig

        // Zum Plugins-Tab.
        composeRule.onNodeWithText("Plugins").performClick()

        // Das installierte Kavita-Plugin muss als Zeile erscheinen (displayName "Kavita").
        composeRule.waitUntil(15_000) {
            composeRule.onAllNodesWithText("Kavita", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Kavita", substring = true).assertExists()
    }
}
```

> Implementer-Hinweis: Falls der Plugins-Tab erst nach Scroll/Discovery die Zeile zeigt, deckt `waitUntil` die Discovery-
> Latenz ab. Falls „Kavita" nicht erscheint, beim ersten Lauf `composeRule.onRoot().printToLog("UI")`
> — und prüfen, ob das APK installiert ist (`adb shell pm list packages`). **Echtes Finding nicht
> verstecken:** ist das Plugin installiert + entdeckt (KavitaPluginLiveTest grün), erscheint aber NICHT
> im Tab → Discovery→UI-Integration ist gebrochen → BLOCKED.

- [ ] **Step 2: Kompiliert + ausführen**

Run: `./gradlew :app:compileDebugAndroidTestKotlin`, dann
`ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.komgareader.app.ci.ui.PluginTabUiTest`
Expected: 1 Test grün.

- [ ] **Step 3: Commit**

```bash
git add app/src/androidTest/kotlin/com/komgareader/app/ci/ui/PluginTabUiTest.kt
git commit -m "test(ci): Plugin-Tab-UI-E2E — installiertes Kavita-Plugin erscheint im Plugins-Tab"
```

---

## Task 3: Color-Preset-Plugin-Discovery-E2E (server-los)

**Files:**
- Create: `app/src/androidTest/kotlin/com/komgareader/app/ci/PluginColorPresetTest.kt`

- [ ] **Step 1: Testklasse schreiben**

```kotlin
package com.komgareader.app.ci

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.komgareader.plugin.host.PluginHost
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * E2E des data-only Color-Preset-Plugins: PluginHost entdeckt das installierte Preset-APK und parst
 * dessen ColorPresetSpec(s) aus dem Asset. Server-los — nur PackageManager + Asset-Lesen.
 * Voraussetzung: das Kindle-Preset-APK ist installiert (Plan 6 Task 1).
 */
@RunWith(AndroidJUnit4::class)
class PluginColorPresetTest {

    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private val host = PluginHost(ctx)

    @Test fun preset_plugin_entdeckt_und_specs_geparst() {
        val presetPlugins = host.discoverColorPresetPlugins()
        assertTrue("Kein Color-Preset-Plugin entdeckt (Preset-APK installiert?)", presetPlugins.isNotEmpty())

        val pkg = presetPlugins.first()
        assertTrue("Entdecktes Preset-Plugin muss ABI 1 haben", pkg.abiVersion == 1)
        assertTrue("Preset-Plugin muss mind. ein ColorPresetSpec liefern, war: ${pkg.presets.size}", pkg.presets.isNotEmpty())
        val spec = pkg.presets.first()
        assertNotNull("ColorPresetSpec braucht einen Namen", spec.name)
        assertTrue("ColorPresetSpec-Name darf nicht leer sein", spec.name.isNotBlank())
    }
}
```

> Implementer-Hinweis: `ColorPresetSpec`-Felder verifizieren (`plugin-api/.../ColorPresetSpec.kt`) —
> `name` sollte existieren; die weiteren Asserts (Sättigung/Kontrast o.ä.) an die echten Felder
> anpassen. Falls `discoverColorPresetPlugins` leer: prüfen, ob das Preset-APK installiert ist
> (Task 1) — sonst BLOCKED (echte Discovery-Lücke).

- [ ] **Step 2: Kompiliert + ausführen**

Run: compile, dann
`ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.komgareader.app.ci.PluginColorPresetTest`
Expected: 1 Test grün.

- [ ] **Step 3: Commit**

```bash
git add app/src/androidTest/kotlin/com/komgareader/app/ci/PluginColorPresetTest.kt
git commit -m "test(ci): Color-Preset-Plugin-Discovery-E2E (data-only APK → discoverColorPresetPlugins)"
```

---

## Task 4: Kavita-Instanz in die CI-Orchestrierung (reproduzierbar)

**Files:**
- Modify: `tools/ci-fixtures/docker-compose.yml` (Kavita-Service)
- Create: `tools/ci-fixtures/seed-kavita.sh`
- Modify: `tools/ci-fixtures/up.sh` (Kavita seeden, Key emittieren), `manifest.json` (Kavita-Instanz)
- Modify: `tools/ci-fixtures/README.md`

- [ ] **Step 1: Kavita-Service in docker-compose**

In `tools/ci-fixtures/docker-compose.yml` ergänzen (mountet die committeten CC0-Manga-Fixtures als
Kavita-Content — anderer Server, bekannter Inhalt):
```yaml
  kavita:
    image: jvmilazz0/kavita:latest
    ports: ["25710:5000"]
    volumes:
      - kavita-config:/kavita/config
      - ./content/manga:/manga:ro
```
und unter `volumes:` `kavita-config:` ergänzen. (Port 25710, um nicht mit dem manuellen kavita-test:5001
zu kollidieren.)

- [ ] **Step 2: seed-kavita.sh schreiben** (ausführbar, ShellCheck-clean)

```bash
#!/bin/bash
# Seedet eine frische Kavita-Instanz: Admin registrieren, Library anlegen, scannen, apiKey ausgeben.
# Gibt "URL=…" und "KEY=…" auf STDOUT aus. Idempotent: ein schon registrierter Admin wird per Login
# wiederverwendet. Flow: register → library/create → library/scan. Siehe local-test-kavita.
set -euo pipefail
readonly ADMIN_USER="ci-admin"
readonly ADMIN_PASS="Komga!Test123"
err() { echo "[seed-kavita] $*" >&2; }

wait_up() {
  local base="$1" i
  for i in $(seq 1 90); do
    if curl -fsS -m 3 "${base}/api/health" >/dev/null 2>&1; then return 0; fi
    sleep 2
  done
  err "Kavita unter ${base} nicht erreichbar"; return 1
}

main() {
  local base="$1"
  wait_up "${base}"
  # register (erster User = Admin) ODER login, falls schon vorhanden.
  local resp token key
  resp="$(curl -fsS -X POST "${base}/api/account/register" -H 'Content-Type: application/json' \
    -d "$(jq -n --arg u "${ADMIN_USER}" --arg p "${ADMIN_PASS}" '{username:$u,password:$p,email:($u+"@ci.local")}')" 2>/dev/null || true)"
  if [[ -z "${resp}" ]]; then
    resp="$(curl -fsS -X POST "${base}/api/account/login" -H 'Content-Type: application/json' \
      -d "$(jq -n --arg u "${ADMIN_USER}" --arg p "${ADMIN_PASS}" '{username:$u,password:$p}')")"
  fi
  token="$(echo "${resp}" | jq -r '.token')"
  key="$(echo "${resp}" | jq -r '.apiKey')"
  # Library anlegen (idempotent: nur wenn keine existiert) + scannen.
  local libs
  libs="$(curl -fsS "${base}/api/library/libraries" -H "Authorization: Bearer ${token}" 2>/dev/null || echo '[]')"
  if [[ "$(echo "${libs}" | jq 'length')" -eq 0 ]]; then
    curl -fsS -X POST "${base}/api/library/create" -H "Authorization: Bearer ${token}" -H 'Content-Type: application/json' \
      -d '{"name":"CI Manga","type":0,"folders":["/manga"],"fileGroupTypes":[1,2,3,4],"excludePatterns":[]}' >/dev/null
  fi
  curl -fsS -X POST "${base}/api/library/scan?libraryId=1&force=true" -H "Authorization: Bearer ${token}" >/dev/null 2>&1 || true
  echo "KAVITA_URL=${base}"
  echo "KAVITA_KEY=${key}"
}
main "$@"
```

> Implementer-Hinweis: die genauen Kavita-Endpunkte/Pflichtfelder gegen die laufende Instanz prüfen
> (Kavita-API variiert je Version). Der manuelle `kavita-test` (Port 5001) ist als Referenz da —
> Felder/Health-Pfad daran verifizieren und bei Abweichung anpassen, im Report vermerken.

- [ ] **Step 3: up.sh + manifest.json** — nach den Komga-Seeds einen Kavita-Block ergänzen, der
  `seed-kavita.sh "http://localhost:25710"` aufruft und `KAVITA_URL`/`KAVITA_KEY` in `.keys.env`
  schreibt. In `verify.sh` Kavita weglassen (anderes API) oder einen einfachen Health-Check ergänzen.
  `manifest.json` um die Kavita-Instanz erweitern (port 25710, Library „CI Manga").

- [ ] **Step 4: ShellCheck + lokaler Lauf**

Run: `cd tools/ci-fixtures && shellcheck seed-kavita.sh && ./up.sh 2>&1 | grep -E 'KAVITA_|Topologie'`
Expected: `shellcheck` clean; `.keys.env` enthält `KAVITA_URL`/`KAVITA_KEY`.

- [ ] **Step 5: Commit**

```bash
git add tools/ci-fixtures/docker-compose.yml tools/ci-fixtures/seed-kavita.sh tools/ci-fixtures/up.sh tools/ci-fixtures/manifest.json tools/ci-fixtures/README.md
git commit -m "feat(ci-fixtures): Kavita-Instanz reproduzierbar (compose + seed-kavita.sh, Key emittiert)"
```

---

## Task 5: Source-Plugin-E2E gegen die CI-Kavita (Instrumentation-Args)

**Files:**
- Create: `app/src/androidTest/kotlin/com/komgareader/app/ci/PluginKavitaCiTest.kt`

Wie `KavitaPluginLiveTest`, aber gegen die **reproduzierbare** CI-Kavita: URL/apiKey aus
Instrumentation-Argumenten (vom `up.sh`-Seed über `.keys.env` → Gradle-Invocation gereicht), Fallback
auf die bekannten lokalen Werte. So ist der Source-Plugin-E2E deterministisch in der CI-Orchestrierung.

- [ ] **Step 1: Testklasse schreiben**

```kotlin
package com.komgareader.app.ci

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.komgareader.domain.source.BrowsableSource
import com.komgareader.domain.source.SourceFilter
import com.komgareader.plugin.host.PluginHost
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Source-Plugin-Lade-Kette gegen die reproduzierbare CI-Kavita (Plan 6 Task 4). URL/apiKey via
 * Instrumentation-Argument (`kavitaUrl`/`kavitaKey`), vom Fixture-Seed gereicht. Ohne Argument
 * (lokaler Ad-hoc-Lauf) wird der Test übersprungen statt rot — der manuelle Pfad ist KavitaPluginLiveTest.
 */
@RunWith(AndroidJUnit4::class)
class PluginKavitaCiTest {

    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private val host = PluginHost(ctx)
    private val pkg = "com.komgareader.plugin.kavita"

    @Test fun kavita_plugin_gegen_ci_kavita() = runTest {
        val args = InstrumentationRegistry.getArguments()
        val url = args.getString("kavitaUrl")
        val key = args.getString("kavitaKey")
        assumeTrue("kavitaUrl/kavitaKey nicht gesetzt — übersprungen (manueller Pfad: KavitaPluginLiveTest)",
            url != null && key != null)

        val discovered = host.discoverPlugins().firstOrNull { it.packageName == pkg }
        assertNotNull("Kavita-Plugin nicht entdeckt (installiert?)", discovered)
        discovered!!
        val source = host.sourceFor(discovered.packageName, discovered.entryClass, discovered.signatureSha256,
            mapOf("url" to url!!, "apiKey" to key!!))
        assertNotNull("sourceFor lieferte null", source)
        source as BrowsableSource
        val items = source.browse(1, SourceFilter()).items
        assertTrue("CI-Kavita muss mind. eine Serie liefern", items.isNotEmpty())
    }
}
```

- [ ] **Step 2: Ausführen mit Args (aus .keys.env)**

Run: `source tools/ci-fixtures/.keys.env; ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.komgareader.app.ci.PluginKavitaCiTest -Pandroid.testInstrumentationRunnerArguments.kavitaUrl=$(echo $KAVITA_URL | sed 's/localhost/10.0.2.2/') -Pandroid.testInstrumentationRunnerArguments.kavitaKey=$KAVITA_KEY`
Expected: 1 Test grün (oder skipped, falls Args fehlen).

> Hinweis: localhost→10.0.2.2 für die Emulator-Sicht. Falls Kavita-Plugin gegen die CI-Kavita-Version
> zickt (API-Drift), den manuellen `KavitaPluginLiveTest` (Port 5001) als Referenz nutzen + Abweichung melden.

- [ ] **Step 3: Commit**

```bash
git add app/src/androidTest/kotlin/com/komgareader/app/ci/PluginKavitaCiTest.kt
git commit -m "test(ci): Source-Plugin-E2E gegen reproduzierbare CI-Kavita (Instrumentation-Args)"
```

---

## Task 6: Gesamtlauf + Spec/Memory-Update

- [ ] **Step 1:** `ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.package=com.komgareader.app.ci` (+ kavita-Args) — alle bisherigen + neuen grün.
- [ ] **Step 2:** Spec §9b + Memory [[integration-test-suite]] um Plugin-E2E (Tab-UI, Color-Preset, Kavita-Fixture) ergänzen. Commit.

---

## Self-Review (Plan-Autor)

- **Ergänzt, dupliziert nicht:** `KavitaPluginLiveTest` (manuell, Loader-Kette) bleibt; neu sind die
  **UI-Integration** (Tab), **Color-Preset-Discovery** und die **reproduzierbare** Kavita-Fixture +
  arg-gespeiste CI-Variante.
- **Server-los wo möglich:** Tab-UI + Color-Preset hängen nur an installierten APKs → robust, kein
  Kavita-Server nötig.
- **APK-Provisioning bewusst NICHT in der CI-Pipeline** (Nutzer-Wahl): Tests setzen installierte APKs
  voraus (dokumentiert); Task 1 baut+installiert das Preset-APK lokal.
- **Offene Annahmen:** Preset-Paketname (Task 1 ermittelt), `ColorPresetSpec`-Felder, Kavita-API-
  Pflichtfelder/Health-Pfad (gegen laufende Instanz verifizieren) — Implementer prüft, Findings melden.

## Danach offen

- Plugin-APK-Build/-Install in die CI-Pipeline (bewusst zurückgestellt; hängt an den gitignored Plugin-Repos).
- Block H restliche `[pending]` (UI-Slot-Packs), erster echter CI-Push.
