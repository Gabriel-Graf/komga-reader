# Roman-Reader (Reflow, crengine) — Implementierungs-Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Einen reflowbaren Roman-Reader mit KOReader-artiger Typografie (Schriftgröße, Zeilenabstand, Ränder, Font, Blocksatz, Hyphenation) bauen, E-Ink-optimiert, hinter Naht B mit der crengine-Engine — und den alten fixen EPUB-Viewer ersetzen.

**Architecture:** Neue Engine `render-crengine` (C++/JNI/NDK) implementiert ein engine-neutrales `ReflowableDocument`-Interface in `domain/render`. `ViewerType.NOVEL` ersetzt `ViewerType.EPUB`. Fortschritt lokal als crengine-xpointer (Room), nur `totalProgression`-% zu Komga (`SyncingSource`). Globale Typo-Settings persistiert. Paged-only, alle Übergänge E-Ink-gegatet.

**Tech Stack:** Kotlin, Jetpack Compose, Hilt, Room, Android NDK (CMake), crengine (CoolReader-Engine, GPL-2.0), Maßgeblich: `docs/superpowers/specs/2026-06-07-novel-reflow-reader-design.md`.

**Verbindliche Regeln/Skills:** `komga-viewer-type-resolution` (Reihenfolge NICHT umsortieren), `komga-eink-ui`, `eink-design-language`, `animation-gating`, `room-migration-destructive-pitfall`, `architecture-seams`, `data-provenance`, `tdd`.

---

## Phase 0 — Lizenz-Gate (HART, vor jeder NDK-Zeile) — ✅ ERLEDIGT

> Läuft ZUERST. Bei Rot endet der Plan hier. Kein Vendoren, kein Build davor.

**Ergebnis (2026-06-07):**
- **KOReader-`crengine`-Fork → 🔴 RED** (GPL-2.0-**only**, Repo `koreader/crengine`
  @ `e32ab96`). Report: `tools/crengine/LICENSE-SCAN.md`, Commit `ab1de7d`.
- **`crengine-ng` → 🟢 GREEN** (GPL-2.0-**or-later**, README-Klausel „or (at your
  option) any later version"; Kern `or-later=335, only=0`, alle `unklar` =
  thirdparty MIT/BSD/MPL). Repo `gitlab.com/coolreader-ng/crengine-ng` @
  `ec57cc1d16c47237c10ac6f3cfa491791e23a952`. Report:
  `tools/crengine/LICENSE-SCAN-NG.md`, Commit `ea335cd`.

→ **Gewählte Engine: `crengine-ng`.** Alle weiteren „crengine"-Nennungen in diesem
Plan/Spec meinen **crengine-ng**. Modulname bleibt `render-crengine`.

### Task 0: crengine-Lizenz verifizieren

**Files:**
- Create: `tools/crengine/scan-license.sh`
- Create: `tools/crengine/LICENSE-SCAN.md` (Report-Ausgabe, eingecheckt)

- [ ] **Step 1: crengine-ng-Quelle als Referenz holen (read-only, NICHT vendoren)**

```bash
git clone --depth 1 --branch master https://gitlab.com/coolreader-ng/crengine-ng.git /tmp/crengine-ng-src
```
Erwartung: Quelle in `/tmp/crengine-ng-src` (GREEN-Kandidat aus Phase 0, GPL-2.0-or-later). Layout: `crengine/src` + `crengine/include`.

- [ ] **Step 2: Scan-Skript schreiben** (`#!/bin/bash`, `set -euo pipefail`, ShellCheck-konform)

```bash
#!/bin/bash
set -euo pipefail
# Scannt crengine-Quelldateien im Render-Pfad auf GPL-Version + or-later.
# Ausgabe: TSV  datei \t verdikt(or-later|only|unklar)  + Summary.
src="${1:?Pfad zur crengine-Quelle}"
green=0 red=0 unknown=0
while IFS= read -r f; do
  hdr="$(head -n 40 "${f}")"
  if grep -qiE 'SPDX-License-Identifier:\s*GPL-2\.0-or-later|GPL-3\.0' <<<"${hdr}" ||
     grep -qiE 'version 2 .*or .*(any )?later version' <<<"${hdr}"; then
    echo -e "${f}\tor-later"; green=$((green+1))
  elif grep -qiE 'SPDX-License-Identifier:\s*GPL-2\.0(-only)?\b' <<<"${hdr}" ||
       grep -qiE 'GNU General Public License.*version 2' <<<"${hdr}"; then
    echo -e "${f}\tonly"; red=$((red+1))
  else
    echo -e "${f}\tunklar"; unknown=$((unknown+1))
  fi
done < <(find "${src}/crengine/src" "${src}/crengine/include" \
          -type f \( -name '*.cpp' -o -name '*.c' -o -name '*.h' -o -name '*.hpp' \) 2>/dev/null)
echo "SUMMARY or-later=${green} only=${red} unklar=${unknown}" >&2
```

- [ ] **Step 3: Scan laufen lassen, Report festhalten**

Run: `bash tools/crengine/scan-license.sh /tmp/crengine-src | tee tools/crengine/LICENSE-SCAN.md`
Erwartung: Report-Tabelle + `SUMMARY`-Zeile auf STDERR.

- [ ] **Step 4: ENTSCHEIDUNGSKNOTEN (manuell, nicht automatisierbar wegbügeln)**

- **Grün** = `only == 0` UND `unklar == 0` für Dateien im tatsächlichen Render-Pfad → weiter zu Phase 1.
- **Rot** = ≥1 `only` oder ungeklärtes `unklar` im Render-Pfad → **STOPP**. Befund dem Nutzer berichten, Plan endet. Kein automatischer Fallback.

`unklar`-Dateien einzeln prüfen (manche crengine-Dateien sind 3rd-party mit eigener, ggf. lockererer Lizenz — die sind ok; nur Kern-crengine zählt).

- [ ] **Step 5: Befund dokumentieren + committen**

```bash
git add tools/crengine/scan-license.sh tools/crengine/LICENSE-SCAN.md
git commit -m "chore(crengine): Lizenz-Scan (Phase-0-Gate) + Report"
```
Bei Grün zusätzlich in `NOTICE` + Provenance-Datei: crengine-Quelle (Permalink), Version/Commit, Lizenz-Verdikt, Erfassungsdatum.

---

## Phase 1 — crengine-ng NDK-Spike (de-risk, TDD-exempt: Spike)

> **Referenz-Rezept: LxReader** (`gitlab.com/coolreader-ng/lxreader`, GPL-3.0,
> lizenzkompatibel) — die Android-App der crengine-ng-Autoren. Sie baut crengine-ng
> via NDK und rendert in eine Android-Bitmap. Wir kochen ihr Rezept nach, statt zu
> forschen. Lokal gecheckt: `/tmp/lxreader-src` + `/tmp/crengine-ng-src`.
> **Nur arm64-v8a** (Boox-Ziel) zuerst. Spike-Erfolg = EINE reflowte EPUB-Seite als Bitmap.
> Maßgebliche Befunde: `docs/superpowers/plans/` + Feasibility-Report (Session).

### Task 1a: Dependency-Cross-Build (arm64-v8a)

**Files:**
- Create: `render-crengine/native/thirdparty/` (Cross-Build-Skripte, von LxReader adaptiert)
- Create: `render-crengine/native/prefix/aarch64-linux-android/` (Build-Output: `.a` + Header)

- [ ] **Step 1: LxReader-Skripte als Referenz holen** — `git clone --depth 1 https://gitlab.com/coolreader-ng/lxreader.git /tmp/lxreader-src` (falls weg). Relevant: `tools/thirdparty-bldtool/repo/*.meta.sh`, `tools/crengine-ng-build/build-all.sh`.

- [ ] **Step 2: ~10 Deps cross-bauen** (freetype, harfbuzz, fribidi, libunibreak, libpng, libjpeg-turbo, libwebp, utf8proc, zlib, zstd) per NDK-Toolchain (`$NDK/build/cmake/android.toolchain.cmake`, `ANDROID_ABI=arm64-v8a`, `ANDROID_PLATFORM=android-21`, `ANDROID_STL=c++_static`) in den Prefix. **fontconfig NICHT** (wird abgeschaltet).

- [ ] **Step 3: Artefakte verifizieren** — `find render-crengine/native/prefix -name '*.a'` listet alle Dep-Libs + Header. Erwartung: vollständiger Prefix.

- [ ] **Step 4: Commit** — `git commit -am "spike(render-crengine): Dep-Cross-Build arm64-v8a (LxReader-Rezept)"`

### Task 1b: crengine-ng cross-bauen

**Files:**
- Vendoren: crengine-ng-Quelle unter `render-crengine/native/crengine-ng/` (Commit `ec57cc1`, GPL-2.0-or-later)
- Create: `render-crengine/native/build-crengine.sh` (von LxReaders `build-all.sh`)

- [ ] **Step 1: crengine-ng vendoren** (Pin auf `ec57cc1`; `NOTICE`/Provenance-Eintrag in Task 13 final).

- [ ] **Step 2: `crengine-ng_static` cross-bauen** — Flags aus LxReaders `build-all.sh`, **kritisch `-DUSE_FONTCONFIG=OFF`**, `-DCRE_BUILD_STATIC=ON`, `-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON`, Deps via `CMAKE_FIND_ROOT_PATH=<prefix>`.

- [ ] **Step 3: Verifizieren** — `libcrengine-ng_static.a` + CMake-Config (`find_package(crengine-ng CONFIG)`-fähig) liegen im Prefix.

- [ ] **Step 4: Commit** — `git commit -am "spike(render-crengine): crengine-ng cross-build arm64-v8a"`

### Task 1c: Modul `render-crengine` + JNI-Render einer Seite

**Files:**
- Create: `render-crengine/build.gradle.kts`, `render-crengine/src/main/cpp/CMakeLists.txt`
- Create: `render-crengine/src/main/cpp/cr3_bridge.cpp` (JNI, von LxReaders `crengine-ng-jni.cpp` + `jnigraphicslib.cpp` adaptiert)
- Modify: `settings.gradle` (`include(":render-crengine")`)
- Test: `render-crengine/src/androidTest/.../CrengineRenderInstrumentedTest.kt`

- [ ] **Step 1: Modul + settings.gradle** — `include(":render-crengine")`; `build.gradle.kts`: `com.android.library`, `externalNativeBuild { cmake { … } }`, `ndk { abiFilters += "arm64-v8a" }`, `dependencies { implementation(project(":domain")) }`.

- [ ] **Step 2: CMake** — `find_package(crengine-ng CONFIG)` aus dem Prefix, JNI-Lib linkt `crengine-ng::crengine-ng_static` + Dep-Libs.

- [ ] **Step 3: JNI-Bridge** — `nativeOpen(byte[] bytes, String fmt) -> long`, `nativeRenderPage(long, int idx, int w, int h, Bitmap dst)`, `nativeClose(long)`. `LVDocView::Render`+`Draw` in `LVColorDrawBuf(w,h, lockedPixels, 32)` (LxReaders `lvcolordrawbufex.cpp`-Muster), via `AndroidBitmap_lockPixels`.

- [ ] **Step 4: Bau verifizieren** — `./gradlew :render-crengine:assembleDebug` → BUILD SUCCESSFUL, `libcr3bridge.so` (arm64-v8a) im AAR.

- [ ] **Step 5: Render-Smoke (Instrumented, `eink_test`)** — öffnet `render-core/src/androidTest/assets/sample.epub`, rendert Seite 0 in eine Bitmap, prüft nicht-uniforme Pixel (Text vorhanden). `./gradlew :render-crengine:connectedDebugAndroidTest` → PASS. **Spike-Erfolg = Gate zu Phase 2.**

- [ ] **Step 6: Commit** — `git commit -am "spike(render-crengine): JNI-Render einer reflowten EPUB-Seite"`

> **Off-Ramp (notiert, nicht gewählt):** Reicht der Aufwand nicht, ist MuPDF-Reflow
> der konfliktfreie Rückfall (Spec-Phase-4-Logik, schon hinter Naht B). Nutzer hat
> crengine-ng bewusst gewählt — nur ziehen, wenn der Spike hart blockiert.

---

## Phase 2 — Seam-Erweiterung in `domain` (TDD, pure)

### Task 2: `ReflowConfig` + Wert-Typen

**Files:**
- Create: `domain/src/main/kotlin/com/komgareader/domain/render/ReflowConfig.kt`
- Test: `domain/src/test/kotlin/com/komgareader/domain/render/ReflowConfigTest.kt`

- [ ] **Step 1: Failing test** — Default-Config + Wertebereiche

```kotlin
package com.komgareader.domain.render
import kotlin.test.Test
import kotlin.test.assertEquals

class ReflowConfigTest {
    @Test fun `default config hat lesbare startwerte`() {
        val c = ReflowConfig.DEFAULT
        assertEquals(1.0f, c.lineHeight)
        assertEquals(TextAlign.JUSTIFY, c.textAlign)
        assertEquals(Hyphenation.Off, c.hyphenation)
    }
}
```

- [ ] **Step 2: Run, verify fail** — `./gradlew :domain:test --tests '*ReflowConfigTest*'` → FAIL (unresolved `ReflowConfig`).

- [ ] **Step 3: Implementieren**

```kotlin
package com.komgareader.domain.render

enum class TextAlign { LEFT, JUSTIFY }
sealed interface Hyphenation {
    data object Off : Hyphenation
    data class Language(val lang: String) : Hyphenation
}
data class Margins(val top: Int, val bottom: Int, val left: Int, val right: Int) {
    companion object { val NORMAL = Margins(24, 24, 24, 24) }
}
data class ReflowConfig(
    val fontSizeEm: Float = 1.0f,
    val lineHeight: Float = 1.0f,
    val margin: Margins = Margins.NORMAL,
    val fontFamily: String = "Literata",
    val textAlign: TextAlign = TextAlign.JUSTIFY,
    val hyphenation: Hyphenation = Hyphenation.Off,
) { companion object { val DEFAULT = ReflowConfig() } }
```

- [ ] **Step 4: Run, verify pass** — `./gradlew :domain:test --tests '*ReflowConfigTest*'` → PASS.

- [ ] **Step 5: Commit** — `git commit -am "feat(domain): ReflowConfig + Typo-Wert-Typen"`

### Task 3: `ReflowableDocument`-Interface + `Chapter`/`SearchHit`

**Files:**
- Modify: `domain/src/main/kotlin/com/komgareader/domain/render/Document.kt`
- Test: `domain/src/test/kotlin/com/komgareader/domain/render/ReflowableDocumentContractTest.kt`

- [ ] **Step 1: Failing test** — ein Fake implementiert das Interface, Vertrag prüfbar

```kotlin
package com.komgareader.domain.render
import kotlin.test.Test
import kotlin.test.assertEquals

private class FakeReflowDoc : ReflowableDocument {
    var lastCfg: ReflowConfig? = null
    var anchor = "a0"
    override fun applyLayout(cfg: ReflowConfig) { lastCfg = cfg }
    override fun chapters() = listOf(Chapter("K1", "a0", 0))
    override fun currentAnchor() = anchor
    override fun seekToAnchor(a: String) { anchor = a }
    override fun seekToProgress(fraction: Float) { anchor = "p$fraction" }
    override fun search(query: String) = listOf(SearchHit("a0", query))
    override fun pageCount() = 3
    override fun pageSize(index: Int) = PageSize(100, 100)
    override fun renderPage(index: Int, zoom: Float, rotation: Int) = RenderedPage(1, 1, intArrayOf(0))
    override fun close() {}
}

class ReflowableDocumentContractTest {
    @Test fun `applyLayout merkt sich config, seek setzt anchor`() {
        val d = FakeReflowDoc()
        d.applyLayout(ReflowConfig.DEFAULT.copy(fontSizeEm = 1.4f))
        assertEquals(1.4f, d.lastCfg!!.fontSizeEm)
        d.seekToAnchor("a2"); assertEquals("a2", d.currentAnchor())
    }
}
```

- [ ] **Step 2: Run, verify fail** → FAIL (unresolved `ReflowableDocument`).

- [ ] **Step 3: Interface ergänzen** (in `Document.kt` unter dem bestehenden `Document`)

```kotlin
data class Chapter(val title: String, val anchor: String, val depth: Int)
data class SearchHit(val anchor: String, val snippet: String)

/** Reflowbares Dokument (Roman): Re-Layout, TOC, stabile Anker, Suche. Engine-neutral. */
interface ReflowableDocument : Document {
    fun applyLayout(cfg: ReflowConfig)
    fun chapters(): List<Chapter>
    fun currentAnchor(): String
    fun seekToAnchor(anchor: String)
    fun seekToProgress(fraction: Float)
    fun search(query: String): List<SearchHit>
}
```

- [ ] **Step 4: Run, verify pass** → PASS.

- [ ] **Step 5: Commit** — `git commit -am "feat(domain): ReflowableDocument-Seam (TOC/Anker/Suche)"`

---

## Phase 3 — ViewerType-Auflösung umstellen (TDD, pure)

### Task 4: `ViewerType.EPUB` → `NOVEL`, Resolution-Regel anpassen

**Files:**
- Modify: `domain/src/main/kotlin/com/komgareader/domain/model/ViewerType.kt`
- Modify: `domain/src/main/kotlin/com/komgareader/domain/usecase/ResolveViewerType.kt`
- Modify: `domain/src/test/kotlin/com/komgareader/domain/usecase/ResolveViewerTypeTest.kt`
- Modify: `docs/domain/viewer-type-resolution.md`, `.claude/skills/komga-viewer-type-resolution/SKILL.md`

- [ ] **Step 1: Failing tests** — Stufe 2 + map liefern NOVEL

In `ResolveViewerTypeTest.kt` die EPUB-Erwartungen auf NOVEL ändern und ergänzen:
```kotlin
@Test fun `stufe 2 - EPUB-format ergibt NOVEL`() {
    val r = ResolveViewerType()
    val series = seriesFixture(override = null, dir = ReadingDirection.LTR)
    val book = bookFixture(format = BookFormat.EPUB)
    assertEquals(ViewerType.NOVEL, r(series, book, fallback = null))
}
@Test fun `map - NOVEL contentType ergibt NOVEL`() {
    assertEquals(ViewerType.NOVEL, ResolveViewerType().forContentType(ContentType.NOVEL))
}
```
(Vorhandene Tests, die `ViewerType.EPUB` erwarten, auf `NOVEL` umstellen — Fixtures `seriesFixture`/`bookFixture` wie im bestehenden Test.)

- [ ] **Step 2: Run, verify fail** — `./gradlew :domain:test --tests '*ResolveViewerTypeTest*'` → FAIL (EPUB ≠ NOVEL / `NOVEL` enum fehlt).

- [ ] **Step 3: Implementieren** — Enum + Use-Case

`ViewerType.kt`:
```kotlin
enum class ViewerType { PAGED, WEBTOON, NOVEL, COMIC }
```
`ResolveViewerType.kt`: Stufe 2 `if (book.format == BookFormat.EPUB) return ViewerType.NOVEL` und `ContentType.NOVEL -> ViewerType.NOVEL`. **Reihenfolge der 6 Stufen unverändert.**

- [ ] **Step 4: Run, verify pass** — `./gradlew :domain:test` → PASS (ganzes Modul, keine Resttreffer auf `ViewerType.EPUB`).

- [ ] **Step 5: Doku + Skill nachziehen** — in beiden Dateien `Stufe 2 → NOVEL`, `map: NOVEL→NOVEL`, „EPUB-Viewer entfällt".

- [ ] **Step 6: Commit** — `git commit -am "feat(domain): ViewerType.NOVEL ersetzt EPUB in Resolution + Doku"`

---

## Phase 3.5 — Reader-Chrome-Basis vereinheitlichen (Refactor VOR dem 4. Reader)

> Vier Reader teilen heute nur `ReaderChromeOverlay` + (teils) `ReaderViewModel`.
> `ComicReaderViewModel` schert aus, jeder Screen verdrahtet Tap-Zonen/HW-Tasten/
> immersiv/Refresh selbst → Drift-Gefahr (auf E-Ink: inkonsistentes Refresh-Verhalten).
> Bevor NOVEL als vierter Reader dazukommt, eine **dünne gemeinsame Schicht**
> extrahieren. Reiner Verhaltens-erhaltender Refactor — bestehende Reader-Tests +
> Screenshots als Sicherheitsnetz.

### Task 5a: Gemeinsames `ReaderChromeState`-Interface

**Files:**
- Create: `app/src/main/kotlin/com/komgareader/app/ui/reader/ReaderChromeState.kt`
- Modify: `ReaderViewModel.kt`, `ComicReaderViewModel.kt`

- [ ] **Step 1: Interface definieren** — den geteilten Chrome-/Navigations-Vertrag, den heute beide VMs ad-hoc tragen:

```kotlin
interface ReaderChromeState {
    val chromeVisible: StateFlow<Boolean>
    fun toggleChrome()
    fun navigateTo(page: Int)
    fun onPageSettled(page: Int)
}
```

- [ ] **Step 2: Beide VMs implementieren lassen** — `ReaderViewModel : …, ReaderChromeState` und `ComicReaderViewModel : …, ReaderChromeState`. Vorhandene Member erfüllen den Vertrag (ggf. Sichtbarkeit angleichen). Kein Verhaltenswechsel.

- [ ] **Step 3: Build grün** — `./gradlew :app:assembleDebug`. Bestehende Reader-Instrumented-Tests laufen unverändert.

- [ ] **Step 4: Commit** — `git commit -am "refactor(reader): gemeinsames ReaderChromeState-Interface"`

### Task 5b: `ReaderScaffold`-Composable + bestehende Screens migrieren

**Files:**
- Create: `app/src/main/kotlin/com/komgareader/app/ui/reader/ReaderScaffold.kt`
- Modify: `PagedReaderScreen.kt`, `WebtoonReaderScreen.kt`, `ComicReaderScreen.kt`

- [ ] **Step 1: Scaffold bauen** — kapselt die heute 3–4-fach duplizierte Chrome-Mechanik **an einer Stelle**:

```kotlin
@Composable
fun ReaderScaffold(
    chrome: ReaderChromeState,
    title: String,
    onBack: () -> Unit,
    footer: (@Composable () -> Unit)? = null,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    content: @Composable () -> Unit,
)
```
Enthält: Tap-Zonen (links/rechts = `onPrev`/`onNext`, Mitte = `chrome.toggleChrome()`), immersiver Modus, `ReaderChromeOverlay(visible, title, onBack)`, optionaler Status-Footer-Slot, HW-Tasten-Routing → `onPrev`/`onNext`. **Keine Animation** (`LocalEinkMode`-gegatet, Regel `animation-gating`).

- [ ] **Step 2: Paged/Webtoon/Comic auf `ReaderScaffold` umstellen** — die handgemachten `detectTapGestures`/Overlay-Blöcke je Screen durch `ReaderScaffold { … }` ersetzen. Verhalten identisch.

- [ ] **Step 3: Build + Screenshot-Vergleich** — `./gradlew :app:assembleDebug`; je Reader Bars-Toggle + Blättern + Refresh wie vorher (Emulator/Boox-Screenshot).

- [ ] **Step 4: Commit** — `git commit -am "refactor(reader): ReaderScaffold kapselt Tap/HW/Overlay/Footer fuer alle Reader"`

---

## Phase 4 — Reflow-Engine-Adapter + Reader-Screen (paged)

### Task 5: `CrengineDocument : ReflowableDocument` (Adapter über JNI)

**Files:**
- Create: `render-crengine/src/main/kotlin/com/komgareader/render/crengine/CrengineDocument.kt`
- Create: `render-crengine/src/main/kotlin/com/komgareader/render/crengine/ReflowCss.kt` (pure Mapping)
- Test: `render-crengine/src/test/kotlin/com/komgareader/render/crengine/ReflowCssTest.kt`

- [ ] **Step 1: Failing test** — `ReflowConfig → crengine-Properties` (pure, ohne JNI)

```kotlin
class ReflowCssTest {
    @Test fun `justify + lineHeight landen in properties`() {
        val p = ReflowCss.toProperties(ReflowConfig.DEFAULT.copy(lineHeight = 1.5f, textAlign = TextAlign.JUSTIFY))
        assertEquals("justify", p["crengine.style.text-align"])
        assertEquals("150", p["crengine.style.line-height"])  // % als Ganzzahl
    }
    @Test fun `hyphenation off setzt none`() {
        val p = ReflowCss.toProperties(ReflowConfig.DEFAULT.copy(hyphenation = Hyphenation.Off))
        assertEquals("@none", p["crengine.hyphenation.dictionary"])
    }
}
```

- [ ] **Step 2: Run, verify fail** → FAIL.

- [ ] **Step 3: Implementieren** — `ReflowCss.toProperties(cfg): Map<String,String>` (reine Funktion: em→font-size, lineHeight→%, margins, font-family, align, hyph-Dict). `CrengineDocument` ruft `nativeApplyProperties` + `nativeRender…` und implementiert `ReflowableDocument` (xpointer aus `nativeGetXPointer`, TOC aus `nativeGetToc`, Suche aus `nativeFind`).

- [ ] **Step 4: Run, verify pass** → PASS (`./gradlew :render-crengine:test --tests '*ReflowCssTest*'`).

- [ ] **Step 5: Instrumented-Test** — echtes Reflow: nach `applyLayout(fontSizeEm=2.0)` ändert sich `pageCount()` ggü. `1.0`. Run: `./gradlew :render-crengine:connectedDebugAndroidTest`. Erwartung: PASS.

- [ ] **Step 6: Commit** — `git commit -am "feat(render-crengine): CrengineDocument + ReflowCss-Mapping"`

### Task 6: `NovelReaderScreen` (paged, eink-ui) + Dispatch

**Files:**
- Create: `app/src/main/kotlin/com/komgareader/app/ui/reader/NovelReaderScreen.kt`
- Modify: `app/src/main/kotlin/com/komgareader/app/ui/reader/ViewerMode.kt`
- Modify: `app/src/main/kotlin/com/komgareader/app/ui/reader/ReaderRoute.kt`
- Modify: `app/src/main/kotlin/com/komgareader/app/ui/reader/ReaderViewModel.kt`
- Delete: `app/src/main/kotlin/com/komgareader/app/ui/reader/EpubReaderScreen.kt`

- [ ] **Step 1: `ViewerMode` erweitern** — `enum class ViewerMode { PAGED, WEBTOON, COMIC, NOVEL }`.

- [ ] **Step 2: `NovelReaderScreen` über `ReaderScaffold`** — kein eigenes Tap/HW/Overlay mehr, sondern `ReaderScaffold(chrome = novelVm, …, onPrev/onNext, footer = { NovelFooter(...) }) { FilteredReaderImage(renderPage(currentIndex)) }`. `NovelReaderViewModel : ReaderChromeState` (gleiche Basis wie die anderen drei). Seitenwechsel/Re-Layout = Full-Refresh über `refresher`. UI nach `komga-eink-ui` (flach, 1.5px-Border, Lucide via `AppIcons`). Animation gegatet (`animation-gating`).

- [ ] **Step 3: `ReaderRoute` umstellen** — den `ReaderContent.Rendered`→`EpubReaderScreen`-Zweig durch `NovelReaderScreen` ersetzen; `ReaderViewModel.renderEpubPage` → `renderNovelPage` (über `ReflowableDocument`). `EpubReaderScreen.kt` löschen.

- [ ] **Step 4: Build + Instrumented-Smoke** — `./gradlew :app:assembleDebug` grün; Reader öffnet einen Roman aus der lokalen Test-Komga und rendert Seite 1.

- [ ] **Step 5: Commit** — `git commit -am "feat(app): NovelReaderScreen (paged) ersetzt EpubReaderScreen"`

---

## Phase 5 — Typo-Panel + globale Settings (TDD für Persistenz)

### Task 7: Globale `ReflowConfig`-Persistenz (Settings-Muster `webtoonOverlapPercent`)

**Files:**
- Modify: `domain/src/main/kotlin/com/komgareader/domain/repository/SettingsRepository.kt`
- Modify: `data/src/main/kotlin/com/komgareader/data/repository/RoomSettingsRepository.kt`
- Test: `data/src/test/kotlin/com/komgareader/data/repository/RoomSettingsRepositoryTest.kt`

- [ ] **Step 1: Failing test** — Reflow-Settings round-trip (genau das `webtoonOverlapPercent`-Muster)

```kotlin
@Test fun `novel fontSizeEm persistiert und liest zurueck`() = runTest {
    repo.setNovelFontSizeEm(1.4f)
    assertEquals(1.4f, repo.novelFontSizeEm.first())
}
```
(Analog je Knopf: lineHeight, marginPreset, fontFamily, textAlign, hyphenationLang.)

- [ ] **Step 2: Run, verify fail** → FAIL.

- [ ] **Step 3: Implementieren** — pro Knopf `val novelX: Flow<…>` + `suspend fun setNovelX(...)` im Interface; `RoomSettingsRepository` mit Key-Konstanten + Defaults (exakt wie `KEY_WEBTOON_OVERLAP_PERCENT`).

- [ ] **Step 4: Run, verify pass** → PASS (`./gradlew :data:test --tests '*RoomSettingsRepositoryTest*'`).

- [ ] **Step 5: Commit** — `git commit -am "feat(settings): globale Roman-Typo-Settings persistiert"`

### Task 8: Typo-Panel-UI (BaseDialog, eink-ui, i18n)

**Files:**
- Create: `app/src/main/kotlin/com/komgareader/app/ui/reader/NovelTypoPanel.kt`
- Modify: `app/.../i18n/` (DE+EN-Keys), `AppIcons`-Registry (falls Icon fehlt)

- [ ] **Step 1: Panel bauen** — `BaseDialog`/Bottom-Sheet, Controls je Knopf (Schriftgröße ±, Zeilenabstand, Ränder, Font-Select, Links/Blocksatz, Hyphenation on/off). Änderung → ViewModel → `applyLayout` → Full-Refresh. Texte via `i18n` (DE+EN, echte Umlaute, Compile-Zeit-Parität). Keine Animation (gegatet).

- [ ] **Step 2: Build + Boox/Emulator-Screenshot** — Panel öffnet, Schriftgröße-Änderung sichtbar (Seitenzahl ändert sich), Position bleibt.

- [ ] **Step 3: Commit** — `git commit -am "feat(app): Roman-Typo-Panel (eink-ui, i18n)"`

---

## Phase 6 — Fortschritt (xpointer) + Komga-%-Sync (TDD)

### Task 9: `novel_progress`-Tabelle (Room, Recreate-Migration)

**Files:**
- Create: `data/src/main/kotlin/com/komgareader/data/db/NovelProgressEntity.kt` + DAO
- Modify: `data/src/main/kotlin/com/komgareader/data/db/AppDatabase.kt` (version 11 → 12, Migration)
- Test: `data/src/test/kotlin/com/komgareader/data/db/NovelProgressDaoTest.kt`

- [ ] **Step 1: Failing test** — upsert + read-back (anchor + fraction + dirty)

```kotlin
@Test fun `upsert speichert anchor und fraction`() = runTest {
    dao.upsert(NovelProgressEntity(sourceId = 1, bookId = "b1", anchor = "/body/1/3", fraction = 0.42f, dirty = true, updatedAt = 0))
    val p = dao.get(1, "b1")!!
    assertEquals("/body/1/3", p.anchor); assertEquals(0.42f, p.fraction)
}
```

- [ ] **Step 2: Run, verify fail** → FAIL.

- [ ] **Step 3: Implementieren** — Entity (PK `sourceId`+`bookId`), DAO (`upsert`/`get`/`dirtyEntries`). `AppDatabase` version 12; Migration **Recreate-Table-Muster** (Regel `room-migration-destructive-pitfall`, kein `ALTER ADD COLUMN+DEFAULT`); `onOpen`-Selbstheilung beibehalten.

- [ ] **Step 4: Run, verify pass** → PASS. Zusätzlich Migrations-Instrumented-Test 11→12 (kein Wipe).

- [ ] **Step 5: Commit** — `git commit -am "feat(data): novel_progress (xpointer) + Migration 11->12"`

### Task 10: Progress-Mapper xpointer ↔ Komga-% (TDD, pure) + Sync-Anschluss

**Files:**
- Create: `domain/src/main/kotlin/com/komgareader/domain/usecase/NovelProgressMapper.kt`
- Test: `domain/src/test/kotlin/com/komgareader/domain/usecase/NovelProgressMapperTest.kt`
- Modify: Sync-Queue-Anschluss (wie bestehender Progress-Push via `SyncingSource.pushProgress`)

- [ ] **Step 1: Failing test** — fraction ↔ ReadProgress, leer/null sauber

```kotlin
@Test fun `fraction wird totalProgression prozent`() {
    val rp = NovelProgressMapper.toReadProgress(fraction = 0.5f)
    assertEquals(0.5f, rp.totalProgression)
}
@Test fun `fehlender anchor faellt auf fraction-seek zurueck`() {
    assertEquals(0.5f, NovelProgressMapper.resumeFraction(anchor = null, fraction = 0.5f))
}
```

- [ ] **Step 2: Run, verify fail** → FAIL.

- [ ] **Step 3: Implementieren** — reine Funktionen: `toReadProgress(fraction)`, `resumeFraction(anchor, fraction)`. Anchor = lokaler Resume (exakt), fraction = Komga-Sync (grob). Push über vorhandene Sync-Queue (`dirty`→`pushProgress`), Pull → `seekToProgress`.

- [ ] **Step 4: Run, verify pass** → PASS.

- [ ] **Step 5: E2E** — Roman lesen → `dirty` → %-Push zu lokaler Test-Komga; App neu → Resume via xpointer exakt; anderes Gerät simuliert → grobes %-Seek.

- [ ] **Step 6: Commit** — `git commit -am "feat(sync): Roman-Fortschritt xpointer lokal + %-Sync zu Komga"`

---

## Phase 7 — TOC · Footer · Volltextsuche · Gehe-zu-%

### Task 11: Status-Footer + TOC

**Files:**
- Create: `app/.../reader/NovelFooter.kt`, `app/.../reader/NovelTocPanel.kt`
- Modify: `NovelReaderScreen.kt`, i18n

- [ ] **Step 1: Footer** — %, Seite (aktuelle Pagination), Kapiteltitel; via `i18n`; flach, kein Schatten.
- [ ] **Step 2: TOC-Panel** — `chapters()` → Liste → `seekToAnchor` → Full-Refresh. `BaseDialog`.
- [ ] **Step 3: Build + Screenshot** — Footer zeigt Kapitel, TOC-Sprung landet korrekt.
- [ ] **Step 4: Commit** — `git commit -am "feat(app): Roman-Footer + TOC"`

### Task 12: Volltextsuche + Gehe-zu-%

**Files:**
- Create: `app/.../reader/NovelSearchPanel.kt`
- Modify: `NovelReaderScreen.kt`, i18n

- [ ] **Step 1: Such-Panel** — Eingabe → `search(query)` → Treffer-Liste (Snippet) → `seekToAnchor`. „Gehe zu %" → `seekToProgress`.
- [ ] **Step 2: Build + Smoke** — Suchtreffer springt an richtige Stelle; %-Sprung grob korrekt.
- [ ] **Step 3: Commit** — `git commit -am "feat(app): Roman-Volltextsuche + Gehe-zu-%"`

---

## Phase 8 — Fonts/Hyphenation + Lizenz/Provenance final

### Task 13: Fonts + Hyph-Patterns bündeln + dokumentieren

**Files:**
- Create: `render-crengine/src/main/assets/fonts/` (2–3 Fonts), `…/hyph/` (DE+EN)
- Create/Modify: `NOTICE`, Provenance-Datei (`tools/crengine/PROVENANCE.md` o.ä.)

- [ ] **Step 1: Fonts wählen + bündeln** — Serif (Literata/Bitter) + Sans, OFL-lizenziert. Bei crengine registrieren (`nativeRegisterFont`).
- [ ] **Step 2: Hyph-Patterns DE+EN** bündeln, an `Hyphenation.Language` koppeln.
- [ ] **Step 3: Provenance** — je Asset Name/URL/Lizenz(SPDX)/Version/Erfassungsdatum (Regel `data-provenance`); crengine-Eintrag aus Phase 0 finalisieren; `NOTICE` ergänzen.
- [ ] **Step 4: E2E auf Boox** — Roman mit gewähltem Font + DE-Hyphenation rendern, Blocksatz ohne „rivers" prüfen (Screenshot).
- [ ] **Step 5: Commit** — `git commit -am "feat(render-crengine): Fonts + Hyphenation + Provenance/NOTICE"`

---

## Abschluss-Checks

- [ ] `./gradlew test` (alle Module) grün.
- [ ] `./gradlew :app:assembleDebug` grün; APK-Größenzuwachs notiert (Risiko R3).
- [ ] Keine Resttreffer `ViewerType.EPUB` / `EpubReaderScreen` (`grep -r`).
- [ ] `komga-viewer-type-resolution`-Skill + `docs/domain/viewer-type-resolution.md` aktuell.
- [ ] `NOTICE`/Provenance vollständig (crengine + Fonts + Hyph).
- [ ] E2E gegen lokale Test-Komga + Boox-Screenshot der Typo-Qualität als „fertig"-Beweis.
