# Integrationstest — Plan 4b: UI-Test-Katalog (echter Nutzerpfad)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Die nutzersichtbaren End-to-End-Flows über das echte Compose-UI: Server hinzufügen/entfernen, Bibliothek→Serie→Reader navigieren, **richtiger Reader pro Inhaltstyp** (Kern-Invariante #4), Sammlung anlegen. Auf der Hilt-UI-Infra aus Plan 4a.

**Architecture:** Geteilte `UiTestBase` (Hilt-in-memory-DB + `createEmptyComposeRule` + Seed-vor-Launch) trägt alle UI-Tests (`shared-structure-before-variants`). Reader-Dispatch wird robust assertbar durch **vier additive `Modifier.testTag`** an den Reader-Screen-Roots (`reader_paged/webtoon/comic/novel`) — semantics-only, kein Verhaltens-/E-Ink-Einfluss. Selektion sonst über sichtbare DE-Texte/contentDescriptions.

**Tech Stack:** wie Plan 4a (Hilt-Test, Compose-UI-Test, ActivityScenario). Tests gegen `emulator-5554`, CI-Fixtures laufen.

**Bezug:** Spec §2/§9 Block A (A1/A4), B (B7), C (C9-C11), D (D14). CLAUDE.md Invariante #4 (deterministischer Viewer-Dispatch). Regel `shared-structure-before-variants.md`.

---

## Grounding (verifiziert — DE-Strings + Flows)

- **Bottom-Nav** (`HomeScreen.kt:107-115`, Tabs als Text **und** contentDescription): „Stöbern" (Bibliothek), „Sammlungen", „Bibliotheken", „Plugins", **„Einstellungen"** (Settings ist ein **Tab**, keine Route).
- **Add-Server:** Settings-Tab „Einstellungen" → Sektion **„Verbindung"** → „+" (contentDescription **„Server hinzufügen"**) → Modal-Felder `OutlinedTextField`-Labels: **„Anzeigename"**, **„Server-URL"**, **„Benutzername"**, **„Passwort"**, **„API-Schlüssel (optional)"**; Speichern-Button **„Speichern"** (enabled, wenn Name+URL nicht leer). Quellenart-Segment „Komga"/„OPDS".
- **Remove-Server:** je `ServerRow` ein IconButton contentDescription **„Entfernen"** (kein Confirm-Dialog) → `removeServer(cfg.id)`. Leer → Library zeigt **„Noch keine Inhalte. Verbinde einen Komga-Server in den Einstellungen."**.
- **Serien-Tile** (`SeriesTile.kt`): ganzer Tile klickbar; Bild `contentDescription = series.title`, Titel auch als Text → Selektion `hasContentDescription("Sample-Manga")` / `hasText(...)`.
- **SeriesDetail → Reader:** Button **„Lesen"** (`SeriesDetailScreen.kt:524`, `s.read`).
- **Reader-Unterscheidung heute:** Novel = „Inhaltsverzeichnis"/„Typografie" (unique), Comic = „Panel-Modus an/aus" (unique), Webtoon = „Zu Paged-Modus wechseln" (unique), **Paged = kein eindeutiges Positiv-Semantic** → testTag nötig. Chrome muss sichtbar sein (Tap Mitte) für die Overlay-Buttons.
- **Dispatch-Logik:** ohne Shelf-Tag → Manga(CBZ)=PAGED, Novel(EPUB)=NOVEL; Webtoon braucht Shelf `defaultContentType=WEBTOON` (CBZ allein ergibt sonst PAGED). `Shelf(id, name, sources: List<ShelfSource>, defaultContentType: ContentType?)`, `ShelfSource(sourceId, containerIds=emptyList())`.
- **Create-Collection (D14):** „Sammlungen"-Tab → „+" (contentDescription **„Neue Sammlung"**) → Modal Feld **„Name der Sammlung"**, Auswahl **„Serien"**/**„Bücher"**, Bestätigen **„Erstellen"**.
- **G22:** nicht robust UI-assertbar (rein visuell) → bleibt bei `DisplayBehaviorTest` (Plan 3 dokumentiert).
- Infra (Plan 4a): `@HiltAndroidTest`, `HiltAndroidRule`, `createEmptyComposeRule`, `TestDataModule` (in-memory), `@Inject ServerRepository`/`ShelfRepository`, `ActivityScenario.launch(MainActivity::class.java)`. Harness: `CiKomga.A/B`, `CiFixtures`.

---

## Task 1: Reader-Dispatch robust testbar — testTags an die Reader-Roots

**Files (prod, additiv):**
- Modify: `app/src/main/kotlin/com/komgareader/app/ui/reader/PagedReaderScreen.kt`
- Modify: `app/src/main/kotlin/com/komgareader/app/ui/reader/WebtoonReaderScreen.kt`
- Modify: `app/src/main/kotlin/com/komgareader/app/ui/reader/ComicReaderScreen.kt`
- Modify: `app/src/main/kotlin/com/komgareader/app/ui/reader/NovelReaderScreen.kt`

Jede der vier Reader-`@Composable`-Funktionen bekommt am **äußersten** Layout-Container
(das Root-`Box`/`Scaffold`, das den ganzen Screen umschließt) ein:
```kotlin
Modifier.testTag("reader_paged")   // bzw. _webtoon / _comic / _novel
```
`testTag` ist semantics-only (unsichtbar, kein Layout-/E-Ink-/Verhaltens-Einfluss) — es macht
nur den Kern-Invariante-#4-Dispatch eindeutig assertbar.

- [ ] **Step 1: Import + testTag ergänzen (je Datei)**

Pro Reader-Datei: `import androidx.compose.ui.platform.testTag` ergänzen und am Root-Modifier
`.testTag("reader_<typ>")` anhängen. Beispiel `PagedReaderScreen` (Root-Container):
```kotlin
// vorher z.B.:  Box(modifier = Modifier.fillMaxSize()) {
// nachher:
Box(modifier = Modifier.fillMaxSize().testTag("reader_paged")) {
```
Analog: `WebtoonReaderScreen` → `"reader_webtoon"`, `ComicReaderScreen` → `"reader_comic"`,
`NovelReaderScreen` → `"reader_novel"`. Den Root-Container jeweils im Code identifizieren (das
äußerste `Box`/`Scaffold`/`Column`, das den Screen aufspannt — nicht ein inneres Element).

- [ ] **Step 2: Kompiliert**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/komgareader/app/ui/reader/PagedReaderScreen.kt \
        app/src/main/kotlin/com/komgareader/app/ui/reader/WebtoonReaderScreen.kt \
        app/src/main/kotlin/com/komgareader/app/ui/reader/ComicReaderScreen.kt \
        app/src/main/kotlin/com/komgareader/app/ui/reader/NovelReaderScreen.kt
git commit -m "feat(reader): testTags an Reader-Roots — deterministischer Dispatch UI-testbar (Invariante #4)"
```

---

## Task 2: UiTestBase — geteiltes Hilt+Compose+Seed-Gerüst

**Files:**
- Create: `app/src/androidTest/kotlin/com/komgareader/app/ci/ui/UiTestBase.kt`

Extrahiert das in Plan 4a (Smoke) etablierte Gerüst, bevor die 2. UI-Testklasse es kopiert
(`shared-structure-before-variants`).

- [ ] **Step 1: UiTestBase schreiben**

```kotlin
package com.komgareader.app.ci.ui

import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import com.komgareader.app.MainActivity
import com.komgareader.domain.repository.ServerRepository
import dagger.hilt.android.testing.HiltAndroidRule
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import javax.inject.Inject

/**
 * Basis für alle Compose-UI-Integrationstests: isolierte Hilt-in-memory-DB (über [TestDataModule]),
 * Seed VOR dem Activity-Start, dann manueller Launch. Subklassen seeden in @Before via [seedServers]
 * und rufen [launch]. Selektion über sichtbare DE-Texte (keine testTags außer Reader-Roots).
 */
abstract class UiTestBase {

    @get:Rule(order = 0) val hiltRule = HiltAndroidRule(this)
    @get:Rule(order = 1) val composeRule = createEmptyComposeRule()

    @Inject lateinit var servers: ServerRepository

    /** Injiziert den Hilt-Graphen. In @Before VOR dem Seeden aufrufen. */
    fun inject() = hiltRule.inject()

    fun seedServers(vararg configs: com.komgareader.domain.repository.ServerConfig) =
        runBlocking { configs.forEach { servers.save(it) } }

    /** Startet MainActivity — erst NACH dem Seeden aufrufen. */
    fun launch() { ActivityScenario.launch(MainActivity::class.java) }
}
```

> Implementer-Hinweis: Falls `@Inject`-Felder im abstrakten Base mit Hilt zicken, die Felder in die
> konkreten Testklassen ziehen (Hilt injiziert nur annotierte Test-Instanzen).

- [ ] **Step 2: Smoke-Test (Plan 4a) auf die Base umstellen (DRY)**

`HiltUiSmokeTest` so umbauen, dass es `UiTestBase` erweitert (Rules/Inject/Seed daraus), nur die
`@Test`-Methode bleibt. Verhaltensgleich.

- [ ] **Step 3: Kompiliert**

Run: `./gradlew :app:compileDebugAndroidTestKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/androidTest/kotlin/com/komgareader/app/ci/ui/UiTestBase.kt \
        app/src/androidTest/kotlin/com/komgareader/app/ci/ui/HiltUiSmokeTest.kt
git commit -m "test(ui): UiTestBase — geteiltes Hilt+Compose+Seed-Gerüst (DRY); Smoke darauf umgestellt"
```

---

## Task 3: A1 + A4 — Server hinzufügen/entfernen über das echte UI

**Files:**
- Create: `app/src/androidTest/kotlin/com/komgareader/app/ci/ui/BlockAServerUiTest.kt`

- [ ] **Step 1: Testklasse schreiben**

```kotlin
package com.komgareader.app.ci.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.komgareader.app.ci.CiFixtures
import com.komgareader.app.ci.CiKomga
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Test
import org.junit.runner.RunWith

/** Spec §9 Block A — Server über das echte UI hinzufügen (A1) und entfernen (A4). */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class BlockAServerUiTest : UiTestBase() {

    /**
     * A1: Nutzer fügt über das UI einen Server hinzu (Felder ausfüllen → Speichern) und sieht
     * danach die Live-Bibliothek. Kein DB-Seeding — der echte Add-Server-Flow IST der Test.
     */
    @Test fun a1_server_ueber_ui_hinzufuegen_zeigt_bibliothek() {
        inject(); launch()   // ohne Seed starten — leere Bibliothek

        // Zu den Einstellungen → Verbindung → "+".
        composeRule.onNodeWithText("Einstellungen").performClick()
        composeRule.onNodeWithText("Verbindung").performClick()
        composeRule.onNodeWithContentDescription("Server hinzufügen").performClick()

        // Felder ausfüllen (Basic Auth gegen CI-Komga-A).
        composeRule.onNodeWithText("Anzeigename").performTextInput("CI-A")
        composeRule.onNodeWithText("Server-URL").performTextInput(CiKomga.A.baseUrl)
        composeRule.onNodeWithText("Benutzername").performTextInput(CiKomga.ADMIN_USER)
        composeRule.onNodeWithText("Passwort").performTextInput(CiKomga.ADMIN_PASS)
        composeRule.onNodeWithText("Speichern").performClick()

        // Zurück zur Bibliothek („Stöbern") → Live-Inhalt muss erscheinen.
        composeRule.onNodeWithText("Stöbern").performClick()
        composeRule.waitUntil(20_000) {
            composeRule.onAllNodesWithText(CiFixtures.MANGA_SERIES, substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    /**
     * A4: Vorkonfigurierten Server über das UI entfernen → Bibliothek fällt auf den Leerzustand,
     * App bricht nicht.
     */
    @Test fun a4_server_entfernen_zeigt_leerzustand() {
        inject()
        seedServers(CiKomga.A)
        launch()

        // Warten bis Bibliothek geladen, dann entfernen.
        composeRule.waitUntil(20_000) {
            composeRule.onAllNodesWithText(CiFixtures.MANGA_SERIES, substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Einstellungen").performClick()
        composeRule.onNodeWithText("Verbindung").performClick()
        composeRule.onNodeWithContentDescription("Entfernen").performClick()

        // Zurück zur Bibliothek → Leerzustand-Text.
        composeRule.onNodeWithText("Stöbern").performClick()
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithText("Noch keine Inhalte", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
    }
}
```

> Implementer-Hinweis: nötige Imports (`onAllNodesWithText`, `waitUntil` ist Member der Rule).
> Falls die Settings-Master-Detail-Ansicht „Verbindung" anders rendert (Sidebar vs. Accordion je
> Breite), beide Pfade tolerieren: existiert `onNodeWithText("Verbindung")` nicht sofort, ist die
> Sektion evtl. schon offen — dann den „+"-Klick direkt versuchen. Beim ersten Lauf via
> `composeRule.onRoot().printToLog("UI")` das tatsächliche Layout prüfen und Selektoren anpassen,
> Abweichung im Report vermerken.

- [ ] **Step 2: Kompiliert + ausführen**

Run: `./gradlew :app:compileDebugAndroidTestKotlin`, dann
`ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.komgareader.app.ci.ui.BlockAServerUiTest`
Expected: 2 Tests grün.

- [ ] **Step 3: Commit**

```bash
git add app/src/androidTest/kotlin/com/komgareader/app/ci/ui/BlockAServerUiTest.kt
git commit -m "test(ui): Block A — Server hinzufügen (A1) + entfernen→Leerzustand (A4) über echtes UI"
```

---

## Task 4: B7 + C9/C11 — Navigation & deterministischer Reader-Dispatch

**Files:**
- Create: `app/src/androidTest/kotlin/com/komgareader/app/ci/ui/BlockCReaderDispatchUiTest.kt`

- [ ] **Step 1: Testklasse schreiben**

```kotlin
package com.komgareader.app.ci.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.komgareader.app.ci.CiFixtures
import com.komgareader.app.ci.CiKomga
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Spec §9 Block B/C — Bibliothek→Serie→Reader (B7) und deterministischer Reader-Dispatch (C9/C11).
 * Manga(CBZ) → Paged-Reader, Novel(EPUB) → Novel-Reader (Kern-Invariante #4).
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class BlockCReaderDispatchUiTest : UiTestBase() {

    private fun openFirstBookOf(seriesTitle: String) {
        composeRule.waitUntil(20_000) {
            composeRule.onAllNodesWithText(seriesTitle, substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        // B7: Tile antippen → SeriesDetail.
        composeRule.onAllNodesWithText(seriesTitle, substring = true)[0].performClick()
        // „Lesen" öffnet den Reader.
        composeRule.waitUntil(15_000) {
            composeRule.onAllNodesWithText("Lesen").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Lesen").performClick()
    }

    /** C9: Manga (CBZ, kein Shelf-Tag) → Paged-Reader. */
    @Test fun c9_manga_oeffnet_paged_reader() {
        inject(); seedServers(CiKomga.A); launch()
        openFirstBookOf(CiFixtures.MANGA_SERIES)
        composeRule.waitUntil(15_000) {
            composeRule.onAllNodesWithTag("reader_paged").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("reader_paged").assertIsDisplayed()
    }

    /** C11: Novel (EPUB) → Novel-Reader (Format schlägt alles). */
    @Test fun c11_novel_oeffnet_novel_reader() {
        inject(); seedServers(CiKomga.A); launch()
        openFirstBookOf(CiFixtures.NOVELS_A.first())  // "Alpha-Novel"
        composeRule.waitUntil(15_000) {
            composeRule.onAllNodesWithTag("reader_novel").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("reader_novel").assertIsDisplayed()
    }
}
```

> Implementer-Hinweis: `onAllNodesWithTag` importieren. Falls „Lesen" durch ein anderes Element
> verdeckt ist oder die SeriesDetail-Hero-Card anders öffnet, beim ersten Lauf `printToLog`
> nutzen. Falls der Reader nach „Lesen" kurz lädt, deckt das `waitUntil` das ab.

- [ ] **Step 2: Kompiliert + ausführen**

Run: compile, dann
`ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.komgareader.app.ci.ui.BlockCReaderDispatchUiTest`
Expected: 2 Tests grün (Manga→`reader_paged`, Novel→`reader_novel`).

- [ ] **Step 3: Falls Dispatch falsch (echtes Finding)**

Öffnet Manga den Novel-Reader o.ä., ist das ein **echter Verstoß gegen Invariante #4** — als
BLOCKED melden, nicht den Test anpassen.

- [ ] **Step 4: Commit**

```bash
git add app/src/androidTest/kotlin/com/komgareader/app/ci/ui/BlockCReaderDispatchUiTest.kt
git commit -m "test(ui): Block B/C — Navigation + Reader-Dispatch Manga→paged, Novel→novel (B7/C9/C11)"
```

---

> **C10 (Webtoon→Webtoon-Reader) bewusst NICHT im UI-Set:** der Webtoon-Reader greift nur, wenn
> der Inhalt als WEBTOON getaggt ist (Shelf `defaultContentType=WEBTOON`). Das bräuchte ein
> Shelf-Seeding, dessen `ShelfSource.sourceId` exakt der intern aus `normalizedBase` abgeleiteten
> `KomgaSource`-`id` entspricht (`SourceId.of(name, KOMGA, normalizedBase)`) — fragiles
> Nachbilden interner Normalisierung. Die **Webtoon-Dispatch-Logik ist bereits auf Seam-Ebene
> grün** (Plan 3 `BlockCViewerDispatchTest.c10`, `fallback=WEBTOON` → `WEBTOON`). Ein UI-level-C10
> ist inkrementell und wird zurückgestellt, bis es einen sauberen Weg gibt, den Shelf-Tag ohne
> sourceId-Normalisierungs-Nachbau zu setzen.

---

## Task 5: D14 — Sammlung über das UI anlegen

**Files:**
- Create: `app/src/androidTest/kotlin/com/komgareader/app/ci/ui/BlockDCollectionUiTest.kt`

> Nur **Anlegen** (lokale Aktion, stabil). Der Server-Push/Pull-Sync (Block D17) bleibt
> ausgeklammert, bis das parallel entwickelte Feature steht.

- [ ] **Step 1: Testklasse schreiben**

```kotlin
package com.komgareader.app.ci.ui

import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.komgareader.app.ci.CiKomga
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Test
import org.junit.runner.RunWith

/** Spec §9 Block D — Sammlung über das UI anlegen (D14, nur Erstellen; Sync separat). */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class BlockDCollectionUiTest : UiTestBase() {

    @Test fun d14_sammlung_anlegen() {
        inject(); seedServers(CiKomga.A); launch()

        composeRule.onNodeWithText("Sammlungen").performClick()
        composeRule.onNodeWithContentDescription("Neue Sammlung").performClick()
        composeRule.onNodeWithText("Name der Sammlung").performTextInput("Meine Sammlung")
        composeRule.onNodeWithText("Erstellen").performClick()

        // Die neue Sammlung muss in der Liste erscheinen.
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithText("Meine Sammlung", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
```

> Implementer-Hinweis: Falls die „+"/„Neue Sammlung"-Aktion erst nach einem Klick auf den Tab oben
> rechts erscheint oder der Default-Kind „Serien" gewählt sein muss, beim ersten Lauf prüfen und
> Selektoren ergänzen.

- [ ] **Step 2: Ausführen + Commit**

Run: `ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.komgareader.app.ci.ui.BlockDCollectionUiTest`
Expected: 1 Test grün.

```bash
git add app/src/androidTest/kotlin/com/komgareader/app/ci/ui/BlockDCollectionUiTest.kt
git commit -m "test(ui): Block D — Sammlung anlegen über UI (D14)"
```

---

## Task 6: Gesamtlauf UI-Set

- [ ] **Step 1: Alle UI-Tests zusammen**

Run: `ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.package=com.komgareader.app.ci.ui`
Expected: Smoke (1) + A (2) + C (2) + D (1) = **6 UI-Tests grün**. Zusammen mit den 17 Seam-Tests
laufen damit **23 Integrationstests** über `com.komgareader.app.ci.*`.

- [ ] **Step 2: G22-Status in der Spec vermerken**

In der Spec bei A1/A4/B7/C9-11/D14 `[UI]` als umgesetzt markieren; G22 bleibt „nur Domain-Unit-Test
(nicht robust UI-assertbar ohne testTag)". Commit:
```bash
git add docs/superpowers/specs/2026-06-10-integration-test-suite-design.md
git commit -m "docs(spec): UI-Set A1/A4/B7/C9-11/D14 umgesetzt; G22-UI als nicht-robust vermerkt"
```

---

## Self-Review (Plan-Autor)

- **Spec-Coverage:** A1 (Add-Server-UI), A4 (Remove→Leerzustand), B7 (Tile→Detail), C9 (Manga→paged), C11 (Novel→novel), D14 (Sammlung anlegen). C10 (Webtoon-UI) zurückgestellt — Logik bereits seam-grün (Plan 3). G22 bewusst nicht (nicht robust UI-assertbar).
- **Prod-Änderung minimal + begründet:** 4 `testTag`s an Reader-Roots — semantics-only, macht Invariante #4 robust testbar. Kein Verhaltens-/E-Ink-Einfluss.
- **DRY:** `UiTestBase` trägt alle UI-Tests; Smoke darauf umgestellt (`shared-structure-before-variants`).
- **Offene Annahmen (im Plan markiert):** Settings-Master-Detail-Layout-Pfad (Task 3), exakte „Lesen"/Tab-Selektoren — Implementer verifiziert beim ersten Lauf via `printToLog`.
- **Echte Findings nicht verstecken:** falscher Reader-Dispatch (C9-C11) oder nicht greifender Shelf-Fallback (C10) = BLOCKED, nicht Test aufweichen.

## Nächste Pläne

- **CI-Pipeline** `.gitlab-ci.yml` + Runner-`devices=["/dev/kvm"]` + Stages (build/unit/fixtures/instrumented/teardown) — macht alle 24 Tests in GitLab lauffähig.
- **Block D17** (Sammlungen Push/Pull-Sync), sobald das Feature steht.
- **Plan 5** Plugin/modulare-UI-Tests (Block H, `[pending]`).
```
