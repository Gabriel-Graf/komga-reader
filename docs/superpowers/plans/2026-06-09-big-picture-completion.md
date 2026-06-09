# Big-Picture-Vervollständigung — Implementierungsplan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Die sechs offenen Big-Picture-Lücken schließen, die nach dem `feat/ui-platform-skins`-Merge übrig sind — von „cheap proof" (OPDS live) bis „schweres Ende" (UI-Slot-Naht).

**Architecture:** Sechs **unabhängige Work-Streams** (A–F), jeder für sich lauffähig und testbar, in Reihenfolge Risiko/Aufwand billig→teuer. Jeder Stream hält die zwei Nähte (`architecture-seams.md`) und die Agnostik-Regeln ein; keiner baut Kern um. Streams können einzeln, in Teams parallel oder sequenziell ausgeführt werden — es gibt **keine** harte Abhängigkeit zwischen A–F außer den unten notierten.

**Tech Stack:** Kotlin · Jetpack Compose · Hilt · Room · Retrofit/OkHttp · Coil · JUnit/MockWebStubs · Android instrumented tests (`androidTest`) · lokale Test-Komga (Docker) + Emulator `eink_test` (1264×1680@300).

**Ist-Stand nach Merge (verifiziert per grep, nicht Doku — Commit `5fd5247`):**
- **#2 Multi-Device:** Theme-Schicht **fertig** — `UiPack`/`DesignTokens`/`packFor` verzweigen auf `DisplayBehavior`, `allowsAccentColor` eingelöst. Offen: Akzent-Identität (Design-Fork), Kaleido-HW-Verify, breitere `LocalDesignTokens`-Adoption, echte Geräteklassen-Erkennung.
- **#4 Color-Filter:** **vollständig** über alle 5 Reader (`FilteredReaderAsyncImage` + `FilteredReaderImage`). Offen: nur ein Abdeckungs-Beweis-Test.
- **#1 Multi-Server:** Naht A sauber, OPDS registrierbar, Canary-/Contract-Tests existieren (`OpdsCanaryInstrumentedTest`, `OpdsE2ETest`). Offen: **live gemischter** Komga+OPDS-Betrieb am Gerät bewiesen.
- **#3 Multi-Reader:** `Viewer`-Naht da, aber `ReaderViewModel` (320 LOC) = God-VM (PAGED+WEBTOON+Rendered zusammen).
- **#5 Plugins:** `ColorProfile`-Tabelle + origin-Tag da. Offen: `plugin-api`-Modul, Color-Preset-Import (Typ c).
- **#6 Modulare UI:** `UiPack`-Theme-Naht gebaut. Offen: Layout-**Slot**-Naht, `ui-api`-Modul, Pack-Lader (Skins-Plan P2/P3).

**Stream-Reihenfolge & Abhängigkeiten:**
- **A** (OPDS-Proof) · **B** (Filter-Proof) — unabhängig, am billigsten, höchster Beweiswert. Zuerst.
- **C** (Multi-Device-Finish) — unabhängig; enthält **einen Design-Fork** (Akzent-Identität), vor Ausführung mit User klären.
- **D** (God-VM-Split) — unabhängig; reiner Refactor auf bestehender `Viewer`-Naht.
- **E** (plugin-api + Presets) — **setzt A grün voraus** (agnostischer Lesepfad muss bewiesen sein, sonst kann Plugin-Quelle keine Seiten liefern).
- **F** (UI-Slot-Naht) — **profitiert von D** (sauberer Reader-Vertrag), aber nicht hart abhängig.

---

## Stream A — OPDS als live verifizierte zweite Quelle (#1 Multi-Server)

**Warum:** Naht A ist sauber, aber nur gegen *eine* Live-Realität (Komga) bewiesen. Bis Komga **und** OPDS gleichzeitig live laufen, kann eine stille Komga-Annahme lauern. Lackmustest verschärft: funktioniert das Werk, wenn es zur **zweiten** Quelle gehört?

**Files:**
- Test: `app/src/androidTest/kotlin/com/komgareader/app/MixedSourcesLiveTest.kt` (Create)
- Inspect: `app/src/androidTest/kotlin/com/komgareader/app/OpdsCanaryInstrumentedTest.kt`, `app/src/androidTest/kotlin/com/komgareader/app/MultiServerPersistenceTest.kt`
- Inspect: `source-opds/src/main/kotlin/com/komgareader/source/opds/OpdsSource.kt`
- Doc: `.claude/rules/architecture-seams.md`, `.claude/rules/source-agnostic-integration.md` (Modify — „Offen: OPDS live" streichen)

- [ ] **Step A1: Bestandsaufnahme — was beweist der existierende Canary schon?**

Read: `OpdsCanaryInstrumentedTest.kt` und `MultiServerPersistenceTest.kt` vollständig. Notiere: läuft der Canary gegen einen **echten** OPDS-Endpoint oder einen In-Test-Stub? Wird **gemischt** mit Komga registriert (zwei `ServerConfig` gleichzeitig)? Wird eine Seite des OPDS-Werks tatsächlich über `openPage` geladen?
Expected outcome: schriftliche Lücken-Liste (z. B. „Canary lädt nur Feed, nie Seite" oder „nur OPDS allein, nie gemischt").

- [ ] **Step A2: Lokalen OPDS-Endpoint der Test-Komga bestätigen**

Komga liefert selbst OPDS unter `/opds/v1.2`. Prüfe gegen die lokale Test-Komga (siehe `local-test-komga` Memory):

Run: `curl -su <user>:<key> http://<komga-host>:<port>/opds/v1.2 | head -40`
Expected: gültiges OPDS-Acquisition/Navigation-Feed-XML (kein 404/401).
→ Damit ist die **dieselbe** Test-Komga gleichzeitig Komga-REST-Quelle **und** OPDS-Quelle — ideal für „gemischt", ohne zweiten Server.

- [ ] **Step A3: Failing instrumented test — zwei Quellen gleichzeitig, Werk gehört der zweiten**

Create `MixedSourcesLiveTest.kt`. Der Test registriert **beide** Configs (Komga-REST + OPDS, beide auf die lokale Test-Komga), aggregiert die Bibliothek über `ActiveSource.all()`, wählt ein Werk der **OPDS**-Quelle (`item.sourceId == opdsId`) und lädt dessen erste Seite über die agnostische Naht.

```kotlin
@RunWith(AndroidJUnit4::class)
class MixedSourcesLiveTest {

    @Test
    fun work_from_second_source_resolves_and_streams_a_page() = runBlocking {
        // Arrange: beide Quellen registrieren (gleiche Test-Komga, einmal REST, einmal OPDS-Feed).
        val komgaId = sourceRegistration.activate(komgaConfig)!!
        val opdsId = sourceRegistration.activate(opdsConfig)!!
        check(komgaId != opdsId) { "IDs müssen sich unterscheiden — sonst keine echte Mischung" }

        // Act: aggregierte Bibliothek; Werk explizit der ZWEITEN (OPDS) Quelle nehmen.
        val all = activeSource.all().flatMap { it.libraries() /* o.ä. — an echte API anpassen */ }
        val opdsWork = all.first { it.sourceId == opdsId }
        val opdsSource = activeSource.get(opdsWork.sourceId) as BrowsableSource
        val firstBook = opdsSource.books(opdsWork.remoteId).first()
        val pageBytes = opdsSource.openPage(firstBook.pageRef(0))

        // Assert: echte Bildbytes über die Naht, kein leerer/Fehler-Body.
        assertThat(pageBytes.size).isGreaterThan(1024)
    }
}
```

(Methodennamen wie `libraries()`/`books()`/`pageRef()` in Step A1 gegen die echten `BrowsableSource`-Signaturen abgleichen und hier einsetzen — **keine** erfundenen Methoden committen.)

- [ ] **Step A4: Test laufen lassen, Rot bestätigen (aus richtigem Grund)**

Run: `./gradlew :app:connectedDebugAndroidTest --tests "com.komgareader.app.MixedSourcesLiveTest"`
Expected: FAIL — entweder weil OPDS-Registrierung/Seiten-Laden noch eine Komga-Annahme trifft, oder (bestmöglich) PASS, falls die Naht schon trägt. Bei PASS: Stream A ist reiner Beweis → direkt zu A6.

- [ ] **Step A5: Echten Fehler beheben (falls Rot)**

Root-Cause am gefundenen Punkt fixen — **nie** den Test an Komga anpassen. Typische Fundstellen: OPDS `openPage` nicht implementiert, `seriesIdOf`/`coverBytes` nur Komga, URL-Normalisierung zwingt Komga-Pfad auf OPDS. Fix lebt **nur** in `source-opds` bzw. der Wiring-Klasse, nie im VM/UI.
Run erneut bis PASS.

- [ ] **Step A6: Emulator-Beweis (sichtbar) + Doku nachziehen**

Beide Quellen in den Settings hinzufügen, Bibliothek am Emulator `eink_test` zeigen (Screenshot: Werke beider Quellen gemischt, ein OPDS-Werk geöffnet). Dann in `architecture-seams.md` und `source-agnostic-integration.md` das „**Offen:** OPDS als end-to-end verifizierte zweite Live-Quelle" durch „**Ist (2026-06-09):** live gemischt verifiziert (`MixedSourcesLiveTest` + Emulator-Screenshot)" ersetzen.

- [ ] **Step A7: Commit**

```bash
git add app/src/androidTest/.../MixedSourcesLiveTest.kt source-opds/ .claude/rules/architecture-seams.md .claude/rules/source-agnostic-integration.md
git commit -m "test(multi-source): OPDS live als zweite Quelle gemischt mit Komga bewiesen [#1]"
```

---

## Stream B — Color-Filter-Abdeckung beweisen (#4)

**Warum:** Implementierung ist über alle 5 Reader fertig (`FilteredReaderAsyncImage`/`FilteredReaderImage`). Es fehlt ein **Regressions-Beweis**, dass kein Reader-Pfad den Filter umgeht (ein neuer 6. Reader oder ein Refactor könnte das still brechen).

**Files:**
- Test: `app/src/test/kotlin/com/komgareader/app/ui/reader/ReaderFilterCoverageTest.kt` (Create)
- Inspect: `app/src/main/kotlin/com/komgareader/app/ui/reader/{Paged,Webtoon,Comic,Epub,Novel}ReaderScreen.kt`

- [ ] **Step B1: Failing test — jeder Reader-Screen referenziert eine gefilterte Bildkomponente**

Statischer Quell-Scan als Test (kein Compose-Runtime nötig): jede Reader-Screen-Datei muss `FilteredReaderAsyncImage` **oder** `FilteredReaderImage` nennen und **kein** rohes `AsyncImage(`/`Image(` für Seiteninhalt.

```kotlin
class ReaderFilterCoverageTest {
    private val readerDir = File("src/main/kotlin/com/komgareader/app/ui/reader")
    private val screens = listOf(
        "PagedReaderScreen", "WebtoonReaderScreen", "ComicReaderScreen",
        "EpubReaderScreen", "NovelReaderScreen",
    )

    @Test
    fun every_reader_screen_renders_pages_through_the_color_filter() {
        val offenders = screens.filterNot { name ->
            val src = File(readerDir, "$name.kt").readText()
            "FilteredReaderAsyncImage" in src || "FilteredReaderImage" in src
        }
        assertThat(offenders).isEmpty()
    }
}
```

- [ ] **Step B2: Test laufen lassen**

Run: `./gradlew :app:testDebugUnitTest --tests "com.komgareader.app.ui.reader.ReaderFilterCoverageTest"`
Expected: PASS (Implementierung ist da). Falls FAIL → ein Reader umgeht den Filter; den Screen auf `FilteredReader*` umstellen, bis grün.

- [ ] **Step B3: Sichtbarer E2E-Beweis am Emulator**

Ein Profil mit kräftigem Kontrast/Sättigung aktivieren (Settings → Farbfilter), je einen paged- und webtoon-Reader öffnen, Screenshot — Seite sichtbar gefiltert. Belegt, dass der statische Test die Realität trifft.

- [ ] **Step B4: Commit**

```bash
git add app/src/test/.../ReaderFilterCoverageTest.kt
git commit -m "test(color-filter): Abdeckungs-Beweis — alle Reader filtern Seiten [#4]"
```

---

## Stream C — Multi-Device fertigstellen (#2)

**Warum:** Theme-Schicht ist nach Merge fertig. Drei Rest-Lücken: (C1) bisher liest nur `EinkBottomBar` `LocalDesignTokens` → Akzent ist real, aber kaum sichtbar; (C2) die Akzent-**Identität** ist provisorisch (Indigo) — bewusste Wahl mit User; (C3) Geräteklasse kommt aus User-`DisplayMode` + `EinkController.capabilities.canColor`, aber `canColor` ist auf echter Onyx-HW unverifiziert.

> **DESIGN-FORK (vor Ausführung mit User klären):** Akzent-Identität für LCD und Kaleido. Optionen: (a) das gemergte Indigo behalten; (b) eine markenspezifische Farbe; (c) systemdynamische Farbe auf LCD (Material You). Default ohne Entscheidung: (a) behalten. Diese Wahl ist eine 1-Zeilen-Token-Änderung (`AccentVividLight/Dark`, `AccentMuted`), bricht keine Tests.

**Files:**
- Modify: ausgewählte Chrome-Composables, die heute hartes `onSurface`/`primary` für aktive Zustände nutzen (Kandidaten via grep, Step C1)
- Test: `app/src/test/kotlin/com/komgareader/app/ui/theme/DesignTokensTest.kt` (Modify — neue Consumer-Annahmen absichern, falls nötig)
- Inspect: `domain/src/main/kotlin/com/komgareader/domain/eink/EinkController.kt`, `eink-onyx/.../OnyxEinkController.kt`

- [ ] **Step C1: Akzent-Consumer aufnehmen — wer rendert „aktiv"?**

Run: `grep -rn "colorScheme.primary\|onSurface\b" app/src/main/kotlin/com/komgareader/app/ui --include=*.kt | grep -i "select\|active\|tab\|nav\|toggle\|chip"`
Für jede echte „aktiver Zustand"-Stelle (Tab-Indikator, Nav-Auswahl, Toggle-Knob, ausgewählte Tile-Border): auf `LocalDesignTokens.current.accent`/`onAccent` umstellen. **Nur** wo „aktiv/ausgewählt" gemeint ist — neutraler Text bleibt `onSurface`.

- [ ] **Step C2: Failing test — Tokens trennen die drei Klassen am Akzent**

(`DesignTokensTest` existiert; ergänze die Klassentrennung falls noch nicht abgedeckt.)

```kotlin
@Test fun mono_has_monochrome_accent() {
    val t = designTokensFor(DisplayBehavior(allowsMotion = false, allowsAccentColor = false), dark = false)
    assertThat(t.accent).isEqualTo(Color.Black)
    assertThat(t.usesShadows).isFalse()
}
@Test fun kaleido_has_muted_color_accent_but_stays_flat() {
    val t = designTokensFor(DisplayBehavior(allowsMotion = false, allowsAccentColor = true), dark = false)
    assertThat(t.accent).isEqualTo(AccentMuted)
    assertThat(t.usesShadows).isFalse()
}
@Test fun lcd_has_vivid_accent_and_depth() {
    val t = designTokensFor(DisplayBehavior(allowsMotion = true, allowsAccentColor = true), dark = false)
    assertThat(t.usesShadows).isTrue()
    assertThat(t.cardElevation).isGreaterThan(0.dp)
}
```

- [ ] **Step C3: Tests laufen lassen**

Run: `./gradlew :app:testDebugUnitTest --tests "com.komgareader.app.ui.theme.DesignTokensTest"`
Expected: PASS.

- [ ] **Step C4: `EinkCapabilities.canColor` auf echter Onyx-HW verifizieren**

Read: `OnyxEinkController.kt` — woher kommt `canColor`? (Onyx-SDK-Query vs. hartkodiert.) Auf echtem Go Color 7 per USB: App starten, `capabilities` loggen, prüfen `canColor == true` → Kaleido-Pack greift automatisch. Falls `canColor` hart `false`/`true` ist: an die SDK-Geräteklassen-Abfrage hängen (separater kleiner Fix in `eink-onyx`, Naht B — **nicht** in die UI ziehen).
Expected: Go Color 7 löst `KaleidoPack` auf, ein Mono-Boox löst `MonoEinkPack` auf.

- [ ] **Step C5: Emulator-Beweis der drei Looks**

Display-Mode-Setting + (für den Test) capabilities so stellen, dass je einmal mono/Kaleido/LCD aufgelöst wird; Screenshot pro Look (mono S/W, Kaleido gedämpft, LCD Indigo+Schatten). Belegt „eine Host-UI, drei Looks".

- [ ] **Step C6: Commit**

```bash
git add app/src/main/kotlin/com/komgareader/app/ui eink-onyx/ app/src/test/.../DesignTokensTest.kt
git commit -m "feat(device): Akzent-Tokens in Chrome eingelöst + Kaleido-Auflösung HW-verifiziert [#2]"
```

---

## Stream D — God-VM-Split auf der Viewer-Naht (#3 Multi-Reader)

**Warum:** `ReaderViewModel` (320 LOC) hält PAGED + WEBTOON + Rendered (offline) zusammen; Comic/Novel haben eigene VMs. Das ist die Schuld aus `shared-structure-before-variants.md`: die N-te Reader-Variante driftet, weil das Gemeinsame (Chrome/Refresh über `Viewer`) zwar geteilt ist, der **Lade-/Modus-State** aber im God-VM klumpt. Ziel: paged-family und webtoon in fokussierte VMs trennen, beide weiter `Viewer`, ohne Verhalten zu ändern.

> **Verhaltens-erhaltender Refactor.** Netz = die bestehenden Tests (`ReaderViewModelTest`, `ReaderFlowInstrumentedTest`). Keine neue Funktion. Grün bleiben ist die Akzeptanz.

**Files:**
- Inspect zuerst vollständig: `app/.../reader/ReaderViewModel.kt`, `ReaderContent.kt`, `Viewer.kt`, `ReaderRoute.kt`, `ReaderViewModelTest.kt`
- Create: `app/.../reader/WebtoonReaderViewModel.kt`
- Modify: `ReaderViewModel.kt` (auf paged-family eindampfen), `ReaderRoute.kt` (Dispatch)
- Test: `app/src/test/.../reader/WebtoonReaderViewModelTest.kt` (Create), `ReaderViewModelTest.kt` (Modify)

- [ ] **Step D1: Ist-Verantwortlichkeiten kartieren**

Read alle vier Inspect-Dateien. Schreibe eine Tabelle: welche Felder/Methoden gehören zu PAGED-family (Streamed/Rendered), welche zu WEBTOON (`loadWebtoonStrip`, `WebtoonStrip`, Frame-Sprünge), was ist **geteilt** (Refresh, Chrome, Progress-Sync, `Viewer`-Vertrag). Das geteilte Stück bleibt — es ist schon die `Viewer`-Naht.
Expected: klare Schnittlinie webtoon ⟂ paged-family + Liste der geteilten Bausteine.

- [ ] **Step D2: Charakterisierungs-Tests grün bestätigen (Netz spannen)**

Run: `./gradlew :app:testDebugUnitTest --tests "com.komgareader.app.ui.reader.ReaderViewModelTest"`
Expected: PASS (Baseline). Falls Lücken im Test (z. B. Webtoon-Strip ungetestet), **jetzt** charakterisierende Tests ergänzen, die das Ist-Verhalten festnageln — vor dem Verschieben.

- [ ] **Step D3: Failing test für das extrahierte `WebtoonReaderViewModel`**

```kotlin
class WebtoonReaderViewModelTest {
    @Test fun builds_seamless_strip_across_chapters() = runTest {
        val vm = WebtoonReaderViewModel(/* fakes: ActiveSource liefert 2 Kapitel */)
        vm.open(bookId, sourceId)
        val content = vm.content.value as ReaderContent.Webtoon
        assertThat(content.pages).hasSize(/* sum der pageCounts */)
        assertThat(content.strip.chapters).hasSize(2)
    }
    @Test fun implements_viewer_contract() {
        assertThat(WebtoonReaderViewModel(/*…*/) as Viewer).isNotNull()
    }
}
```

- [ ] **Step D4: `WebtoonReaderViewModel` extrahieren (Webtoon-Pfad rüber)**

Verschiebe `loadWebtoonStrip`, Frame-Sprung-Logik, Webtoon-Progress in das neue VM; es implementiert `Viewer` und teilt **dieselbe** `RefreshScheduler`-Instanz-Quelle wie die anderen. `ReaderContent.Webtoon` bleibt unverändert. Den `toggleViewerMode`-Pfad (paged⟷webtoon) so umbauen, dass `ReaderRoute` zwischen den zwei VMs dispatcht statt eines VM mit Modus-Flag (oder, falls Toggle In-VM bleiben muss, die Begründung im Code dokumentieren — kein stiller God-VM-Rest).

- [ ] **Step D5: `ReaderViewModel` auf paged-family eindampfen**

Webtoon-Felder/-Methoden entfernen; übrig bleibt Streamed (paged) + Rendered (offline). `initialViewerMode`-Webtoon-Zweig entfällt aus diesem VM.

- [ ] **Step D6: Dispatch in `ReaderRoute.kt`**

`when(ViewerMode)` so erweitern, dass WEBTOON → `WebtoonReaderViewModel`, PAGED/COMIC/Rendered → bestehende VMs. Hilt-Bereitstellung des neuen VM ergänzen.

- [ ] **Step D7: Alle Reader-Tests grün**

Run: `./gradlew :app:testDebugUnitTest --tests "com.komgareader.app.ui.reader.*"` und `./gradlew :app:connectedDebugAndroidTest --tests "com.komgareader.app.ReaderFlowInstrumentedTest"`
Expected: PASS (gleiches Verhalten, gesplitteter State).

- [ ] **Step D8: Doku + Commit**

`architecture-seams.md` (Abschnitt „Noch Soll: God-VM-Split") auf „aufgelöst" ziehen.

```bash
git add app/src/main/kotlin/com/komgareader/app/ui/reader app/src/test/.../reader .claude/rules/architecture-seams.md
git commit -m "refactor(reader): God-VM-Split — WebtoonReaderViewModel auf der Viewer-Naht [#3]"
```

---

## Stream E — `plugin-api`-Modul + Color-Preset-Import (#5 Plugins, Typ c zuerst)

**Warum:** Plugins sind Phase 4, aber der **risikoärmste** Typ (c) Color-Presets ist rein deklarative Daten (JSON → geclampte `ColorProfile`-Zahlen) — null Classloader/ABI-Risiko, idealer Proof des Lade-Wegs. Setzt voraus: agnostischer Lesepfad bewiesen (Stream A grün), und `ColorProfile`/`color_profiles` mit origin-Tag (existiert).

> **Scope-Cut (YAGNI):** Dieser Stream baut **nur** Typ (c) (deklarative Presets, In-App-Import einer JSON-Datei) + das dünne `plugin-api`-Modul als Vertrag. **Kein** APK-Lader, **kein** `DexClassLoader`, **kein** Quellen-Plugin (Typ a) und **kein** UI-Plugin (Typ b) — die sind spätere, eigene Pläne. Begründung: erst den Lade-Weg an der harmlosesten Variante beweisen (Plugin-Architektur-Entscheidung 5 in `big-picture-and-goals.md`).

**Files:**
- Create Modul: `plugin-api/` (pure Kotlin, hängt nur an `domain`), `settings.gradle.kts` (Modify), `plugin-api/build.gradle.kts`
- Create: `plugin-api/src/main/kotlin/com/komgareader/plugin/ColorPresetSpec.kt` (deklaratives DTO + ABI-Integer)
- Create: `data/.../ColorPresetImporter.kt` (JSON → geclamptes `ColorProfile`, `builtIn=false`, origin-getaggt)
- Test: `data/src/test/.../ColorPresetImporterTest.kt`
- Modify: Settings-UI um „Preset importieren" (Datei-Picker → Importer)
- Inspect: `domain/.../model/ColorProfile.kt`, `data/.../db/ColorProfileDao.kt`, `data/.../repository/RoomColorProfileRepository.kt`

- [ ] **Step E1: Bestandsaufnahme `ColorProfile` + Origin-Tag**

Read `ColorProfile.kt`, `ColorProfileDao.kt`, `RoomColorProfileRepository.kt`, `ColorProfileSeedTest.kt`. Notiere: Wertebereiche jedes Feldes (Sättigung/Kontrast/Helligkeit), wie `builtIn`/origin heute getaggt wird, wie Seeding läuft (für Wiederverwendung beim Import).

- [ ] **Step E2: `plugin-api`-Modul anlegen (Vertrag, nicht eingefroren)**

`settings.gradle.kts`: `include(":plugin-api")`. `plugin-api/build.gradle.kts`: pure-Kotlin-lib, `implementation(project(":domain"))`, **keine** Android-/Netz-Deps. Datei `ColorPresetSpec.kt`:

```kotlin
package com.komgareader.plugin

/** ABI-Gate als zwei Integer (kein semver-String) — Plugin-Plan-Entscheidung 2. */
object PluginAbi { const val VERSION = 1; const val MIN_SUPPORTED = 1 }

/**
 * Deklarative Beschreibung eines Color-Presets (Plugin-Typ c). Reine Daten — kein Code,
 * kein Classloader. Wird beim Import auf die ColorProfile-Wertebereiche geclampt.
 */
data class ColorPresetSpec(
    val abiVersion: Int,
    val name: String,
    val saturation: Float,
    val contrast: Float,
    val brightness: Float,
)
```

- [ ] **Step E3: Failing test — Import clamped & taggt origin**

```kotlin
class ColorPresetImporterTest {
    @Test fun out_of_range_values_are_clamped() {
        val spec = ColorPresetSpec(abiVersion = 1, name = "Wild", saturation = 9f, contrast = -3f, brightness = 2f)
        val profile = ColorPresetImporter.toProfile(spec)
        assertThat(profile.saturation).isAtMost(ColorProfile.SATURATION_MAX)
        assertThat(profile.contrast).isAtLeast(ColorProfile.CONTRAST_MIN)
        assertThat(profile.builtIn).isFalse()        // importiert = löschbar
    }
    @Test fun incompatible_abi_is_rejected() {
        val spec = ColorPresetSpec(abiVersion = 999, name = "Future", saturation = 1f, contrast = 1f, brightness = 1f)
        assertThat(ColorPresetImporter.toProfileOrNull(spec)).isNull()
    }
}
```

(Konstantennamen wie `SATURATION_MAX` in Step E1 gegen die echten `ColorProfile`-Grenzen abgleichen; falls keine existieren, in `ColorProfile` als benannte Konstanten ergänzen — kein Magic Number im Importer.)

- [ ] **Step E4: Test laufen lassen, Rot bestätigen**

Run: `./gradlew :data:testDebugUnitTest --tests "*ColorPresetImporterTest"`
Expected: FAIL — `ColorPresetImporter` existiert nicht.

- [ ] **Step E5: `ColorPresetImporter` implementieren (geclampt, origin-getaggt)**

```kotlin
object ColorPresetImporter {
    fun toProfileOrNull(spec: ColorPresetSpec): ColorProfile? {
        if (spec.abiVersion < PluginAbi.MIN_SUPPORTED || spec.abiVersion > PluginAbi.VERSION) return null
        return toProfile(spec)
    }
    fun toProfile(spec: ColorPresetSpec): ColorProfile = ColorProfile(
        name = spec.name,
        saturation = spec.saturation.coerceIn(ColorProfile.SATURATION_MIN, ColorProfile.SATURATION_MAX),
        contrast = spec.contrast.coerceIn(ColorProfile.CONTRAST_MIN, ColorProfile.CONTRAST_MAX),
        brightness = spec.brightness.coerceIn(ColorProfile.BRIGHTNESS_MIN, ColorProfile.BRIGHTNESS_MAX),
        builtIn = false,
    )
}
```

Run erneut bis PASS.

- [ ] **Step E6: Persistenz-Test — importiertes Preset landet in `color_profiles`, löschbar**

```kotlin
@Test fun imported_preset_is_persisted_and_deletable() = runTest {
    val id = repo.insert(ColorPresetImporter.toProfile(spec))
    assertThat(repo.all().first().any { it.id == id && !it.builtIn }).isTrue()
    repo.delete(id)
    assertThat(repo.all().first().none { it.id == id }).isTrue()
}
```
Run: `./gradlew :data:testDebugUnitTest --tests "*ColorProfileRepositoryTest"` → PASS.

- [ ] **Step E7: Settings-UI — „Preset importieren" (Datei-Picker → Importer)**

In der Farbfilter-Settings-Sektion einen `EinkOutlinedButton` „Preset importieren" ergänzen: `ActivityResultContracts.OpenDocument` (`application/json`) → JSON nach `ColorPresetSpec` (kotlinx.serialization) → `ColorPresetImporter.toProfileOrNull` → bei `null` lokalisierter Fehler-Toast, sonst speichern + Liste aktualisieren. Sichtbarer Text über i18n DE+EN.

- [ ] **Step E8: E2E-Beweis am Emulator**

Eine `preset.json` auf das Gerät legen, importieren, Preset erscheint in der Liste, auf einen Reader anwenden (Screenshot), wieder löschen. Belegt den ganzen Lade-Weg ohne Classloader-Risiko.

- [ ] **Step E9: Provenance + Doku + Commit**

Da Presets eine externe Datenquelle sein können: kurze Notiz in der Projekt-Provenance (Format-Spec der Preset-JSON, ABI-Version). `big-picture-and-goals.md` Plugin-Sektion: Typ (c) „gebaut, In-App-Import; APK-Lader weiter Soll".

```bash
git add plugin-api/ settings.gradle.kts data/ app/src/main/kotlin/com/komgareader/app/ui/settings .claude/rules/big-picture-and-goals.md
git commit -m "feat(plugins): plugin-api-Vertrag + Color-Preset-Import (Typ c) [#5]"
```

---

## Stream F — UI-Slot-Naht fürs Chrome (#6 Modulare UI, Skins-Plan P2)

**Warum:** Die Theme-Pack-Naht (`UiPack`) ist gebaut. Das schwere Ende ist die **Layout-Slot-Naht**: adressierbare, einzeln ersetzbare Chrome-Regionen (header/overlay/tiles/nav/settings/dialog), Host rendert + erzwingt E-Ink-Invarianten, fehlender Slot → Default (wie `StubSource`). Das ist Skins-Plan P2.

> **Profitiert von Stream D** (sauberer Reader-Vertrag) und von den schon extrahierten Bausteinen (`SeriesTile`, `StandardTopAppBar`, `ReaderScaffold`, `BaseDialog`). **YAGNI/Maß halten:** erst **eine** Region wirklich slot-fähig machen (Header) und beweisen, dass ein alternatives Pack sie ersetzen kann — nicht spekulativ alle sechs Slots auf einmal. Der Vertrag wird aus dem ersten echten Slot extrahiert, nicht vorab erfunden (Skins-Plan-Leitplanke 3, „ABI nicht jetzt freezen").

**Files:**
- Create: `app/.../ui/slots/UiSlots.kt` (Slot-Vertrag: benannte Composable-Lambdas mit Default), `app/.../ui/slots/DefaultSlots.kt`
- Modify: `StandardTopAppBar`-Aufrufer (Header über den Slot beziehen)
- Test: `app/src/test/.../ui/slots/SlotFallbackTest.kt`
- Doc: `big-picture-and-goals.md` (ui-modularity Ist-Stand), `architecture-seams.md`

- [ ] **Step F1: Slot-Inventar + erste Region wählen**

Read `StandardTopAppBar.kt`, `ReaderScaffold.kt`, `BaseDialog.kt`. Lege die Slot-Liste fest (header/overlay/tiles/nav/settings/dialog) **als Namen**, implementiere aber in diesem Stream **nur den Header-Slot** end-to-end. Begründung im Code: Header ist überall dupliziert gewesen (jetzt `StandardTopAppBar`) → idealer erster austauschbarer Slot.

- [ ] **Step F2: Failing test — fehlender Slot fällt auf Default zurück**

```kotlin
class SlotFallbackTest {
    @Test fun missing_header_slot_falls_back_to_default_pack() {
        val pack = UiSlotPack(header = null)        // Community-Pack ohne Header
        val resolved = UiSlots.resolve(pack)
        assertThat(resolved.header).isEqualTo(DefaultSlots.header)   // Default, nie null
    }
    @Test fun provided_slot_overrides_default() {
        val custom: HeaderSlot = { /* alt */ }
        val resolved = UiSlots.resolve(UiSlotPack(header = custom))
        assertThat(resolved.header).isEqualTo(custom)
    }
}
```

- [ ] **Step F3: Test laufen lassen, Rot bestätigen**

Run: `./gradlew :app:testDebugUnitTest --tests "*SlotFallbackTest"`
Expected: FAIL — Typen `UiSlotPack`/`UiSlots`/`HeaderSlot` existieren nicht.

- [ ] **Step F4: Slot-Vertrag + Resolver implementieren (Default = mitgeliefertes Onyx-Look-UI)**

```kotlin
typealias HeaderSlot = @Composable (title: String, onBack: (() -> Unit)?) -> Unit

/** Ein UI-Pack füllt einen, mehrere oder keinen Slot. Null = Host nimmt Default (wie StubSource). */
data class UiSlotPack(val header: HeaderSlot? = null /* weitere Slots später, alle optional */)

data class ResolvedSlots(val header: HeaderSlot)

object UiSlots {
    fun resolve(pack: UiSlotPack): ResolvedSlots =
        ResolvedSlots(header = pack.header ?: DefaultSlots.header)
}

object DefaultSlots {
    val header: HeaderSlot = { title, onBack -> StandardTopAppBar(title = title, onBack = onBack) }
}
```

Run F2 bis PASS.

- [ ] **Step F5: Host rendert den Header über den Slot + erzwingt Invarianten**

Den/die Header-Aufrufer so umstellen, dass sie `LocalResolvedSlots.current.header(title, onBack)` rendern statt `StandardTopAppBar` direkt. `LocalResolvedSlots` in der Theme-/Host-Schicht bereitstellen (Default-Pack). **Wichtig:** Motion/Akzent bleiben host-gegatet (`LocalDisplayBehavior`/`LocalDesignTokens`) — ein Pack liefert nur Inhalt/Struktur, nie die Bewegungs-/Farb-Policy.

- [ ] **Step F6: Beweis — alternatives Header-Pack ohne Core-Änderung**

Ein triviales zweites `UiSlotPack` mit abweichendem Header (z. B. zentrierter Titel) in einem Debug-/Preview-Pfad einhängen, Emulator-Screenshot: Header anders, Rest unverändert, E-Ink-Invarianten gehalten. Das ist der konkrete Beleg „Chrome austauschbar, Core bleibt".

- [ ] **Step F7: Doku nachziehen + Commit**

`big-picture-and-goals.md` ui-modularity-Sektion + `architecture-seams.md`: „Header-Slot-Naht gebaut (erste Region); weitere Slots + `ui-api`-Extraktion + APK-Lader weiter Soll (Skins-Plan P2/P3)". Keinen Typ als real behaupten, der nicht existiert.

```bash
git add app/src/main/kotlin/com/komgareader/app/ui/slots app/src/main/kotlin/com/komgareader/app/ui .claude/rules
git commit -m "feat(ui): Header-Slot-Naht — erste austauschbare Chrome-Region [#6 ui-modularity P2]"
```

---

## Selbst-Review (gegen die 6 Punkte)

- **#1 OPDS live:** Stream A (live gemischt, Werk der zweiten Quelle, Seite über Naht). ✓
- **#2 Multi-Device:** Stream C (Akzent-Consumer, Klassentrennung-Tests, Kaleido-HW-Verify). Theme-Bau war schon im Merge. ✓
- **#3 God-VM:** Stream D (Webtoon-VM extrahiert, beide auf `Viewer`, Verhalten erhalten). ✓
- **#4 Color-Filter:** Stream B (Abdeckungs-Beweis-Test + E2E). Impl. war schon da. ✓
- **#5 Plugins:** Stream E (`plugin-api` + Preset-Import Typ c, geclampt/origin-getaggt). Typ a/b bewusst ausgeschnitten. ✓
- **#6 Modulare UI:** Stream F (Header-Slot-Naht als erste Region + Fallback). Weitere Slots/`ui-api`/Lader bewusst später. ✓

**Offene Design-Forks (vor Ausführung mit User):** Akzent-Identität (Stream C, Default = Indigo behalten).

**Querschnitt-Invarianten (alle Streams):** TDD wo pur · E2E/Emulator pro sichtbarem Feature · Naht A/B nie umgangen · E-Ink-Invarianten host-erzwungen · i18n DE+EN · `docs-match-code` (betroffene Regel im selben Commit nachziehen) · AGPL/Provenance bei externen Daten (Stream E).
