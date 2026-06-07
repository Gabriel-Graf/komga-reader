# Guided Comic Reader Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ein geführter Comic-Lesemodus (`ViewerType.COMIC`): Paged-Reader-Kern + Panel-für-Panel-Zoom, Panels vom vorhandenen `PanelDetector` in Leserichtung sortiert, Pixel aus Coil-Bitmap, E-Ink-konform.

**Architecture:** Pure Logik (`GuidedNavigator` Panel-Sequenz inkl. Seitengrenzen, `PanelGeometry` Hit-Test + Zoom) im Modul `guided-view` (host-testbar, JUnit5). `ComicReaderViewModel`/`ComicReaderScreen` in `app` sind dünne Compose-/Coil-Shell. `ViewerType.COMIC` löst deterministisch über `ResolveViewerType` auf — kein neuer `ContentType`, nur das COMIC-Mapping wechselt.

**Tech Stack:** Kotlin, Jetpack Compose, Coil (Bitmap-Decode + ImageLoader.execute), Hilt, JUnit5 (`guided-view`/`domain` host-tests), bestehendes `guided-view`-Modul (`PanelDetector`, `PanelRect`, `ReadingDirection`).

**Maßgebliche Regeln:** `docs/superpowers/specs/2026-06-06-guided-comic-reader-design.md`, Skill `komga-guided-comic-reader`, `komga-viewer-type-resolution`, `komga-eink-ui`. COMIC = westliche Comics = LTR (Manga/RTL löst auf PAGED auf) → Detektor-Richtung fix `LEFT_TO_RIGHT`.

---

## Dateienübersicht

**Ändern:**
- `domain/.../model/ViewerType.kt` — Enum um `COMIC`.
- `domain/.../usecase/ResolveViewerType.kt` — `map(COMIC) → ViewerType.COMIC`.
- `domain/.../usecase/ResolveViewerTypeTest.kt` — COMIC-Tests.
- `app/.../ui/reader/ViewerMode.kt` — Enum um `COMIC`.
- `app/.../ui/series/SeriesDetailViewModel.kt` — `mapViewerMode(COMIC) → ViewerMode.COMIC`.
- `app/.../ui/reader/ReaderRoute.kt` — `ViewerMode.COMIC → ComicReaderScreen`.
- `app/build.gradle.kts` — Abhängigkeit `:guided-view`.
- `app/.../i18n/Strings.kt` — Toggle-Label-Keys (DE+EN).

**Neu:**
- `guided-view/.../GuidedNavigator.kt` + `GuidedNavigatorTest.kt` — Panel-Sequenz (pure).
- `guided-view/.../PanelGeometry.kt` + `PanelGeometryTest.kt` — Hit-Test + Zoom (pure).
- `app/.../ui/reader/ComicPageLoader.kt` — Coil-Bitmap → Downscale → `RenderedPage` → Panels.
- `app/.../ui/reader/ComicReaderViewModel.kt` — Zustand, Detektion, Cache, Toggle.
- `app/.../ui/reader/ComicReaderScreen.kt` — Compose-Shell (Vollseite/Zoom, Tap-Zonen, Chrome).

---

## Task 1: ViewerType.COMIC + Auflösung (domain, pure)

**Files:**
- Modify: `domain/src/main/kotlin/com/komgareader/domain/model/ViewerType.kt`
- Modify: `domain/src/main/kotlin/com/komgareader/domain/usecase/ResolveViewerType.kt:53-58`
- Test: `domain/src/test/kotlin/com/komgareader/domain/usecase/ResolveViewerTypeTest.kt`

- [ ] **Step 1: Failing tests ergänzen**

In `ResolveViewerTypeTest.kt` zwei Tests anhängen (nutzt vorhandene `series(...)`/`book(...)`-Helfer):

```kotlin
@Test
fun `COMIC-Override ergibt COMIC`() {
    val result = resolve(series(override = ContentType.COMIC), book(BookFormat.CBZ), fallback = null)
    assertEquals(ViewerType.COMIC, result)
}

@Test
fun `COMIC-Bibliotheks-Default ergibt COMIC`() {
    val result = resolve(series(), book(BookFormat.CBZ), fallback = ContentType.COMIC)
    assertEquals(ViewerType.COMIC, result)
}
```

- [ ] **Step 2: Tests laufen lassen — müssen fehlschlagen**

Run: `./gradlew :domain:test --tests "com.komgareader.domain.usecase.ResolveViewerTypeTest"`
Expected: FAIL — `expected ViewerType.COMIC but was PAGED` (Enum-Wert COMIC existiert noch nicht → Kompilierfehler bzw. Assertion).

- [ ] **Step 3: ViewerType erweitern**

`ViewerType.kt`:

```kotlin
package com.komgareader.domain.model

enum class ViewerType { PAGED, WEBTOON, EPUB, COMIC }
```

- [ ] **Step 4: Mapping anpassen**

In `ResolveViewerType.kt`, Methode `map` (aktuell Zeile 53-58), COMIC-Zeile ändern:

```kotlin
private fun map(type: ContentType): ViewerType = when (type) {
    ContentType.MANGA -> ViewerType.PAGED
    ContentType.COMIC -> ViewerType.COMIC
    ContentType.WEBTOON -> ViewerType.WEBTOON
    ContentType.NOVEL -> ViewerType.EPUB
}
```

Die 6-stufige Prioritätsregel (`invoke`) bleibt unverändert.

- [ ] **Step 5: Tests laufen lassen — grün**

Run: `./gradlew :domain:test --tests "com.komgareader.domain.usecase.ResolveViewerTypeTest"`
Expected: PASS (alle, inkl. der vorhandenen Stufen-Tests).

- [ ] **Step 6: Commit**

```bash
git add domain/src/main/kotlin/com/komgareader/domain/model/ViewerType.kt domain/src/main/kotlin/com/komgareader/domain/usecase/ResolveViewerType.kt domain/src/test/kotlin/com/komgareader/domain/usecase/ResolveViewerTypeTest.kt
git commit -m "feat(domain): ViewerType.COMIC, ContentType.COMIC loest COMIC auf"
```

---

## Task 2: GuidedNavigator — Panel-Sequenz inkl. Seitengrenzen (guided-view, pure, TDD)

Reines Index-Modell. Jede Seite hat `units = max(1, panelCount)` Navigations-Einheiten; eine Seite mit <2 Panels = 1 Einheit (Vollseite-Fallback). Vorwärts/rückwärts steppt über Seitengrenzen.

**Files:**
- Create: `guided-view/src/main/kotlin/com/komgareader/guidedview/GuidedNavigator.kt`
- Test: `guided-view/src/test/kotlin/com/komgareader/guidedview/GuidedNavigatorTest.kt`

- [ ] **Step 1: Failing tests schreiben**

`GuidedNavigatorTest.kt`:

```kotlin
package com.komgareader.guidedview

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GuidedNavigatorTest {

    // Seite 0: 3 Einheiten, Seite 1: 1 Einheit (Splash), Seite 2: 2 Einheiten
    private val units = listOf(3, 1, 2)
    private val pageCount = units.size
    private val unitsAt: (Int) -> Int = { units[it] }

    @Test
    fun `vorwaerts innerhalb der Seite`() {
        assertEquals(GuidedPosition(0, 1), GuidedNavigator.next(GuidedPosition(0, 0), pageCount, unitsAt))
    }

    @Test
    fun `vorwaerts ueber letztes Panel springt auf naechste Seite Einheit 0`() {
        assertEquals(GuidedPosition(1, 0), GuidedNavigator.next(GuidedPosition(0, 2), pageCount, unitsAt))
    }

    @Test
    fun `vorwaerts am Buchende ergibt null`() {
        assertNull(GuidedNavigator.next(GuidedPosition(2, 1), pageCount, unitsAt))
    }

    @Test
    fun `rueckwaerts innerhalb der Seite`() {
        assertEquals(GuidedPosition(0, 1), GuidedNavigator.previous(GuidedPosition(0, 2), pageCount, unitsAt))
    }

    @Test
    fun `rueckwaerts vor Einheit 0 springt auf Vorseite letzte Einheit`() {
        assertEquals(GuidedPosition(0, 2), GuidedNavigator.previous(GuidedPosition(1, 0), pageCount, unitsAt))
    }

    @Test
    fun `rueckwaerts am Buchanfang ergibt null`() {
        assertNull(GuidedNavigator.previous(GuidedPosition(0, 0), pageCount, unitsAt))
    }
}
```

- [ ] **Step 2: Tests laufen lassen — müssen fehlschlagen**

Run: `./gradlew :guided-view:test --tests "com.komgareader.guidedview.GuidedNavigatorTest"`
Expected: FAIL — `GuidedNavigator`/`GuidedPosition` nicht definiert (Kompilierfehler).

- [ ] **Step 3: Implementierung**

`GuidedNavigator.kt`:

```kotlin
package com.komgareader.guidedview

/** Position im geführten Lesefluss: [page] = Seitenindex, [unit] = Navigations-Einheit (Panel-Index bzw. 0 = Vollseite). */
data class GuidedPosition(val page: Int, val unit: Int)

/**
 * Reine Index-Logik für die Panel-für-Panel-Navigation über Seitengrenzen hinweg.
 * [unitsAt] liefert die Anzahl Navigations-Einheiten einer Seite (immer >= 1;
 * eine Seite mit <2 erkannten Panels hat genau 1 Einheit = Vollseite).
 */
object GuidedNavigator {

    fun next(pos: GuidedPosition, pageCount: Int, unitsAt: (Int) -> Int): GuidedPosition? {
        if (pos.unit + 1 < unitsAt(pos.page)) return GuidedPosition(pos.page, pos.unit + 1)
        val nextPage = pos.page + 1
        if (nextPage >= pageCount) return null
        return GuidedPosition(nextPage, 0)
    }

    fun previous(pos: GuidedPosition, pageCount: Int, unitsAt: (Int) -> Int): GuidedPosition? {
        if (pos.unit > 0) return GuidedPosition(pos.page, pos.unit - 1)
        val prevPage = pos.page - 1
        if (prevPage < 0) return null
        return GuidedPosition(prevPage, unitsAt(prevPage) - 1)
    }
}
```

- [ ] **Step 4: Tests laufen lassen — grün**

Run: `./gradlew :guided-view:test --tests "com.komgareader.guidedview.GuidedNavigatorTest"`
Expected: PASS (6 Tests).

- [ ] **Step 5: Commit**

```bash
git add guided-view/src/main/kotlin/com/komgareader/guidedview/GuidedNavigator.kt guided-view/src/test/kotlin/com/komgareader/guidedview/GuidedNavigatorTest.kt
git commit -m "feat(guided-view): GuidedNavigator fuer Panel-Sequenz ueber Seitengrenzen"
```

---

## Task 3: PanelGeometry — Hit-Test + Zoom (guided-view, pure, TDD)

Arbeitet in **bild-normalisierten** Koordinaten [0..1] (unabhängig von Detektions-Auflösung und Viewport). `normalize` rechnet `PanelRect` (Detektions-Pixel) → `NormRect`. `hitTest` findet das getroffene Panel. `zoomScale` liefert den Vergrößerungsfaktor, mit dem das Panel bildschirmfüllend wird (Pivot = Panel-Mitte, von der Compose-Schicht als `transformOrigin` genutzt).

**Files:**
- Create: `guided-view/src/main/kotlin/com/komgareader/guidedview/PanelGeometry.kt`
- Test: `guided-view/src/test/kotlin/com/komgareader/guidedview/PanelGeometryTest.kt`

- [ ] **Step 1: Failing tests schreiben**

`PanelGeometryTest.kt`:

```kotlin
package com.komgareader.guidedview

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PanelGeometryTest {

    // Seite 1000x800; Panel links-oben 0..400 x 0..400
    private val panel = PanelRect(0, 0, 400, 400)

    @Test
    fun `normalize rechnet Detektions-Pixel in 0 bis 1`() {
        val n = PanelGeometry.normalize(panel, pageW = 1000, pageH = 800)
        assertEquals(0f, n.left); assertEquals(0f, n.top)
        assertEquals(0.4f, n.width); assertEquals(0.5f, n.height)
    }

    @Test
    fun `hitTest trifft das Panel das den Punkt enthaelt`() {
        val a = PanelGeometry.normalize(PanelRect(0, 0, 400, 800), 1000, 800)   // linke Spalte
        val b = PanelGeometry.normalize(PanelRect(600, 0, 400, 800), 1000, 800) // rechte Spalte
        // Punkt bei x=0.1,y=0.5 liegt in a (links)
        assertEquals(0, PanelGeometry.hitTest(0.1f, 0.5f, listOf(a, b)))
        // Punkt bei x=0.8 liegt in b (rechts)
        assertEquals(1, PanelGeometry.hitTest(0.8f, 0.5f, listOf(a, b)))
    }

    @Test
    fun `hitTest im Gutter trifft kein Panel`() {
        val a = PanelGeometry.normalize(PanelRect(0, 0, 400, 800), 1000, 800)
        val b = PanelGeometry.normalize(PanelRect(600, 0, 400, 800), 1000, 800)
        // x=0.5 liegt im Gutter zwischen 0.4 und 0.6
        assertNull(PanelGeometry.hitTest(0.5f, 0.5f, listOf(a, b)))
    }

    @Test
    fun `zoomScale fuellt das groessere Panel-Mass abzueglich Rand`() {
        val n = PanelGeometry.normalize(panel, 1000, 800) // width 0.4, height 0.5
        // groesseres Mass = 0.5; Rand 0.05 -> Scale = (1 - 2*0.05) / 0.5 = 1.8
        val s = PanelGeometry.zoomScale(n, marginFraction = 0.05f)
        assertTrue(kotlin.math.abs(s - 1.8f) < 0.001f, "Scale war $s")
    }
}
```

- [ ] **Step 2: Tests laufen lassen — müssen fehlschlagen**

Run: `./gradlew :guided-view:test --tests "com.komgareader.guidedview.PanelGeometryTest"`
Expected: FAIL — `PanelGeometry`/`NormRect` nicht definiert.

- [ ] **Step 3: Implementierung**

`PanelGeometry.kt`:

```kotlin
package com.komgareader.guidedview

/** Panel in bild-normalisierten Koordinaten [0..1] relativ zur Seite. */
data class NormRect(val left: Float, val top: Float, val width: Float, val height: Float) {
    val centerX: Float get() = left + width / 2f
    val centerY: Float get() = top + height / 2f
    fun contains(x: Float, y: Float): Boolean =
        x >= left && x < left + width && y >= top && y < top + height
}

/**
 * Reine Geometrie für den Comic-Reader: Panel-Koordinaten normalisieren,
 * Tap-Treffer bestimmen, Zoom-Faktor berechnen. Kein Android, kein Viewport-Wissen —
 * die Compose-Schicht rechnet Viewport-Taps in bild-normalisierte Koordinaten um.
 */
object PanelGeometry {

    fun normalize(panel: PanelRect, pageW: Int, pageH: Int): NormRect =
        NormRect(
            left = panel.x.toFloat() / pageW,
            top = panel.y.toFloat() / pageH,
            width = panel.width.toFloat() / pageW,
            height = panel.height.toFloat() / pageH,
        )

    /** Index des Panels, das den (normalisierten) Punkt enthält, sonst null (Gutter/Rand). */
    fun hitTest(xNorm: Float, yNorm: Float, panels: List<NormRect>): Int? {
        val i = panels.indexOfFirst { it.contains(xNorm, yNorm) }
        return if (i >= 0) i else null
    }

    /**
     * Faktor, mit dem die Seite skaliert werden muss, damit [panel] (abzüglich
     * [marginFraction] Rand) bildschirmfüllend wird. Pivot ist die Panel-Mitte.
     */
    fun zoomScale(panel: NormRect, marginFraction: Float): Float {
        val largest = maxOf(panel.width, panel.height).coerceAtLeast(0.0001f)
        return (1f - 2f * marginFraction) / largest
    }
}
```

- [ ] **Step 4: Tests laufen lassen — grün**

Run: `./gradlew :guided-view:test --tests "com.komgareader.guidedview.PanelGeometryTest"`
Expected: PASS (4 Tests).

- [ ] **Step 5: Commit**

```bash
git add guided-view/src/main/kotlin/com/komgareader/guidedview/PanelGeometry.kt guided-view/src/test/kotlin/com/komgareader/guidedview/PanelGeometryTest.kt
git commit -m "feat(guided-view): PanelGeometry (normalize, hitTest, zoomScale)"
```

---

## Task 4: app hängt von guided-view ab + ViewerMode.COMIC

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/kotlin/com/komgareader/app/ui/reader/ViewerMode.kt`
- Modify: `app/src/main/kotlin/com/komgareader/app/ui/series/SeriesDetailViewModel.kt:178-181`

- [ ] **Step 1: Gradle-Abhängigkeit ergänzen**

In `app/build.gradle.kts` im `dependencies`-Block neben den anderen `implementation(project(":..."))`-Zeilen hinzufügen:

```kotlin
implementation(project(":guided-view"))
```

- [ ] **Step 2: ViewerMode erweitern**

`ViewerMode.kt`:

```kotlin
package com.komgareader.app.ui.reader

enum class ViewerMode { PAGED, WEBTOON, COMIC }
```

- [ ] **Step 3: mapViewerMode erweitern**

In `SeriesDetailViewModel.kt`, Methode `mapViewerMode` (aktuell Zeile 178-181):

```kotlin
private fun mapViewerMode(type: ViewerType): ViewerMode = when (type) {
    ViewerType.WEBTOON -> ViewerMode.WEBTOON
    ViewerType.COMIC -> ViewerMode.COMIC
    else -> ViewerMode.PAGED // PAGED und EPUB lesen paginiert; EPUB-Buch wählt Reader per Format
}
```

- [ ] **Step 4: Kompilieren**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL (noch kein `ComicReaderScreen` referenziert — `ReaderRoute` behandelt COMIC erst in Task 7; bis dahin würde `when(mode)` nicht-erschöpfend sein → diese Task NICHT einzeln bauen, sondern erst nach Task 7. Stattdessen hier nur `:app:compileDebugKotlin` überspringen und im Commit-Schritt ohne Build committen.)

> Hinweis: `ViewerMode.COMIC` macht das `when (mode)` in `ReaderRoute`/`PagedReaderScreen` nicht-erschöpfend. Das wird in Task 7 geschlossen. Diese Task kompiliert allein noch nicht — das ist erwartet.

- [ ] **Step 5: Commit**

```bash
git add app/build.gradle.kts app/src/main/kotlin/com/komgareader/app/ui/reader/ViewerMode.kt app/src/main/kotlin/com/komgareader/app/ui/series/SeriesDetailViewModel.kt
git commit -m "feat(app): ViewerMode.COMIC + guided-view-Abhaengigkeit"
```

---

## Task 5: ComicPageLoader — Coil-Bitmap → Panels (app)

Lädt eine Seite über Coils `ImageLoader.execute`, skaliert das Bitmap runter, baut `RenderedPage` und ruft den `PanelDetector`. `allowHardware(false)`, damit Pixel lesbar sind. Richtung fix `LEFT_TO_RIGHT` (COMIC = westlich; siehe Plan-Kopf).

**Files:**
- Create: `app/src/main/kotlin/com/komgareader/app/ui/reader/ComicPageLoader.kt`

- [ ] **Step 1: Implementierung**

`ComicPageLoader.kt`:

```kotlin
package com.komgareader.app.ui.reader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.core.graphics.scale
import coil.ImageLoader
import coil.request.ImageRequest
import com.komgareader.domain.render.RenderedPage
import com.komgareader.guidedview.PanelDetector
import com.komgareader.guidedview.PanelRect
import com.komgareader.guidedview.ReadingDirection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Beschafft die Panel-Rechtecke einer Comic-Seite: Coil dekodiert das Seitenbild,
 * es wird auf [detectionWidth] runterskaliert (XY-Cut braucht keine volle Auflösung),
 * der [PanelDetector] liefert die in Leserichtung sortierten Panels.
 *
 * Panel-Koordinaten liegen im Downscale-Raum; die Compose-Schicht normalisiert sie
 * über die tatsächlichen Detektions-Maße (siehe [PageDetection]).
 */
class ComicPageLoader(
    private val context: Context,
    private val imageLoader: ImageLoader,
    private val detector: PanelDetector = PanelDetector(),
    private val detectionWidth: Int = 1000,
) {

    data class PageDetection(val panels: List<PanelRect>, val pageWidth: Int, val pageHeight: Int)

    suspend fun detect(pageUrl: String, headers: Map<String, String>): PageDetection =
        withContext(Dispatchers.Default) {
            val bitmap = decode(pageUrl, headers) ?: return@withContext PageDetection(emptyList(), 0, 0)
            val scaled = downscale(bitmap)
            val page = toRenderedPage(scaled)
            val panels = detector.detect(page, ReadingDirection.LEFT_TO_RIGHT)
            PageDetection(panels, page.width, page.height)
        }

    private suspend fun decode(url: String, headers: Map<String, String>): Bitmap? =
        withContext(Dispatchers.IO) {
            val request = ImageRequest.Builder(context)
                .data(url)
                .allowHardware(false)
                .apply { headers.forEach { addHeader(it.key, it.value) } }
                .build()
            val result = imageLoader.execute(request)
            (result.drawable as? BitmapDrawable)?.bitmap
        }

    private fun downscale(src: Bitmap): Bitmap {
        if (src.width <= detectionWidth) return src
        val ratio = detectionWidth.toFloat() / src.width
        return src.scale(detectionWidth, (src.height * ratio).toInt())
    }

    private fun toRenderedPage(bmp: Bitmap): RenderedPage {
        val pixels = IntArray(bmp.width * bmp.height)
        bmp.getPixels(pixels, 0, bmp.width, 0, 0, bmp.width, bmp.height)
        return RenderedPage(bmp.width, bmp.height, pixels)
    }
}
```

> `androidx.core.graphics.scale` kommt aus `androidx.core:core-ktx` (im Projekt vorhanden). Falls der Import fehlt: `Bitmap.createScaledBitmap(src, w, h, true)` verwenden.

- [ ] **Step 2: Kompilieren**

Run: `./gradlew :app:compileDebugKotlin` (erwartet weiterhin den nicht-erschöpfenden `when`-Fehler aus Task 4 — erst nach Task 7 grün; hier nur sicherstellen, dass `ComicPageLoader.kt` selbst keine eigenen Fehler wirft, indem die Fehlermeldung ausschließlich `ReaderRoute.kt`/`PagedReaderScreen.kt` betrifft).

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/komgareader/app/ui/reader/ComicPageLoader.kt
git commit -m "feat(app): ComicPageLoader (Coil-Bitmap -> Downscale -> PanelDetector)"
```

---

## Task 6: ComicReaderViewModel — Zustand, Detektion, Cache, Toggle (app)

Hält den Comic-Zustand getrennt vom bestehenden `ReaderViewModel`: aktuelle `GuidedPosition`, ob gezoomt, Panel-Cache pro Seite, Guided-Toggle. Nutzt `ComicPageLoader` (Detektion), `PanelGeometry`/`GuidedNavigator` (pure). Fortschritt-Push delegiert weiterhin an die Quelle wie im Paged-Reader.

**Files:**
- Create: `app/src/main/kotlin/com/komgareader/app/ui/reader/ComicReaderViewModel.kt`

- [ ] **Step 1: Implementierung**

`ComicReaderViewModel.kt`:

```kotlin
package com.komgareader.app.ui.reader

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.ImageLoader
import com.komgareader.guidedview.GuidedNavigator
import com.komgareader.guidedview.GuidedPosition
import com.komgareader.guidedview.NormRect
import com.komgareader.guidedview.PanelGeometry
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Zustand des Comic-Readers für eine Seite und den Zoom-Status. */
data class ComicUiState(
    val guidedEnabled: Boolean = true,
    val zoomed: Boolean = false,
    val position: GuidedPosition = GuidedPosition(0, 0),
    /** Normalisierte Panels der aktuellen Seite (leer = Vollseite/Fallback). */
    val currentPanels: List<NormRect> = emptyList(),
    val chromeVisible: Boolean = false,
)

@HiltViewModel
class ComicReaderViewModel @Inject constructor(
    @ApplicationContext context: Context,
    imageLoader: ImageLoader,
) : ViewModel() {

    private val loader = ComicPageLoader(context, imageLoader)
    private val panelCache = mutableMapOf<Int, List<NormRect>>()
    private val unitsPerPage = mutableMapOf<Int, Int>()

    private val _uiState = MutableStateFlow(ComicUiState())
    val uiState: StateFlow<ComicUiState> = _uiState.asStateFlow()

    private var pageCount: Int = 0
    private var pages: List<String> = emptyList()
    private var headers: Map<String, String> = emptyMap()
    private val marginFraction = 0.05f

    fun init(pageUrls: List<String>, authHeaders: Map<String, String>, startPage: Int) {
        pages = pageUrls
        headers = authHeaders
        pageCount = pageUrls.size
        _uiState.value = ComicUiState(position = GuidedPosition(startPage.coerceIn(0, pageCount - 1), 0))
        ensurePanels(_uiState.value.position.page)
    }

    /** Anzahl Navigations-Einheiten einer Seite (>=1). Unbekannt → 1 (Vollseite). */
    private fun unitsAt(page: Int): Int = unitsPerPage[page] ?: 1

    private fun ensurePanels(page: Int) {
        if (panelCache.containsKey(page) || page !in 0 until pageCount) return
        viewModelScope.launch {
            val det = loader.detect(pages[page], headers)
            val norms = det.panels.map { PanelGeometry.normalize(it, det.pageWidth, det.pageHeight) }
            panelCache[page] = norms
            unitsPerPage[page] = if (norms.size < 2) 1 else norms.size
            if (_uiState.value.position.page == page) {
                _uiState.value = _uiState.value.copy(currentPanels = norms)
            }
        }
    }

    /** Tap auf normalisierten Punkt der Vollseite: Panel treffen → dort zoomen. */
    fun onPageTap(xNorm: Float, yNorm: Float) {
        val s = _uiState.value
        if (!s.guidedEnabled || s.currentPanels.size < 2) { toggleChrome(); return }
        val hit = PanelGeometry.hitTest(xNorm, yNorm, s.currentPanels)
        if (hit == null) { toggleChrome(); return }
        _uiState.value = s.copy(zoomed = true, position = s.position.copy(unit = hit), chromeVisible = false)
    }

    fun next() = step(forward = true)
    fun previous() = step(forward = false)

    private fun step(forward: Boolean) {
        val s = _uiState.value
        if (!s.zoomed) return // Vollseiten-Blättern macht der Screen über requestedPage
        val target = if (forward) GuidedNavigator.next(s.position, pageCount, ::unitsAt)
                     else GuidedNavigator.previous(s.position, pageCount, ::unitsAt)
        target ?: return
        ensurePanels(target.page)
        val panels = panelCache[target.page] ?: emptyList()
        // Zielseite ohne erkennbare Panels → Vollseite zeigen (Fallback), nicht zoomen.
        val zoomed = panels.size >= 2
        _uiState.value = s.copy(position = target, currentPanels = panels, zoomed = zoomed)
        ensurePanels(target.page + 1) // Nachbar vorausladen
    }

    fun zoomOut() { _uiState.value = _uiState.value.copy(zoomed = false) }

    fun toggleGuided() {
        val s = _uiState.value
        _uiState.value = s.copy(guidedEnabled = !s.guidedEnabled, zoomed = false)
    }

    fun toggleChrome() {
        _uiState.value = _uiState.value.copy(chromeVisible = !_uiState.value.chromeVisible)
    }

    /** Wird vom Screen aufgerufen, wenn die Vollseite gewechselt hat (Pager-Settle). */
    fun onPageSettled(page: Int) {
        if (page == _uiState.value.position.page) return
        _uiState.value = _uiState.value.copy(
            position = GuidedPosition(page, 0),
            currentPanels = panelCache[page] ?: emptyList(),
            zoomed = false,
        )
        ensurePanels(page)
        ensurePanels(page + 1)
    }
}
```

> Coil-`ImageLoader` ist Hilt-injizierbar, sofern das Projekt einen Provider hat. Falls `imageLoader` nicht bereitsteht: in einem Hilt-`@Module` `@Provides fun imageLoader(@ApplicationContext c: Context): ImageLoader = ImageLoader(c)` ergänzen (analog vorhandener DI-Module unter `app/.../di/`). Vor Implementierung prüfen: `grep -rn "ImageLoader" app/src/main/kotlin/com/komgareader/app/di`.

- [ ] **Step 2: DI-Check / ImageLoader-Provider sicherstellen**

Run: `grep -rn "ImageLoader" app/src/main/kotlin/com/komgareader/app/di || echo "KEIN PROVIDER"`
Falls "KEIN PROVIDER": einen `@Provides`-Eintrag wie oben in ein bestehendes `@Module` (z.B. das App-/Netzwerk-Modul) einfügen und mitcommitten.

- [ ] **Step 3: Commit** (Build erst nach Task 7 grün — siehe Hinweis Task 4)

```bash
git add app/src/main/kotlin/com/komgareader/app/ui/reader/ComicReaderViewModel.kt
git commit -m "feat(app): ComicReaderViewModel (Zustand, Detektion, Cache, Toggle)"
```

---

## Task 7: ComicReaderScreen + ReaderRoute-Verdrahtung (app)

Vollseite via `HorizontalPager` + Coil (wie `PagedReaderScreen`). Im gezoomten Zustand wird dieselbe Seite mit `graphicsLayer`-Scale um die Panel-Mitte gezeigt. Tap-Zonen je Zustand. Chrome-Overlay enthält den Guided-Toggle.

**Files:**
- Create: `app/src/main/kotlin/com/komgareader/app/ui/reader/ComicReaderScreen.kt`
- Modify: `app/src/main/kotlin/com/komgareader/app/ui/reader/ReaderRoute.kt:62-115`

- [ ] **Step 1: ComicReaderScreen implementieren**

`ComicReaderScreen.kt`:

```kotlin
package com.komgareader.app.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.ViewDay
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.komgareader.guidedview.PanelGeometry

@Composable
fun ComicReaderScreen(
    pages: List<com.komgareader.domain.source.PageRef>,
    authHeaders: Map<String, String>,
    initialPage: Int,
    onBack: () -> Unit,
    onToggleMode: () -> Unit = {},
    comicVm: ComicReaderViewModel = hiltViewModel(),
) {
    val ctx = LocalContext.current
    val pageCount = pages.size
    if (pageCount == 0) return

    LaunchedEffect(Unit) {
        comicVm.init(pages.map { it.url }, authHeaders, initialPage)
    }

    val state by comicVm.uiState.collectAsState()
    val pagerState = rememberPagerState(initialPage = initialPage) { pageCount }

    // Vollseite gewechselt → ViewModel informieren
    LaunchedEffect(pagerState.currentPage) { comicVm.onPageSettled(pagerState.currentPage) }

    val marginFraction = 0.05f

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { pageIndex ->
            val pageRef = pages[pageIndex]
            val request = remember(pageRef.url, authHeaders) {
                ImageRequest.Builder(ctx).data(pageRef.url)
                    .apply { authHeaders.forEach { addHeader(it.key, it.value) } }
                    .crossfade(false).build()
            }
            // Zoom nur auf der aktuellen Seite anwenden
            val isCurrent = pageIndex == state.position.page
            val panel = if (isCurrent && state.zoomed) state.currentPanels.getOrNull(state.position.unit) else null
            val mod = if (panel != null) {
                val scale = PanelGeometry.zoomScale(panel, marginFraction)
                Modifier.fillMaxSize().graphicsLayer(
                    scaleX = scale, scaleY = scale,
                    transformOrigin = TransformOrigin(panel.centerX, panel.centerY),
                )
            } else Modifier.fillMaxSize()

            AsyncImage(
                model = request,
                contentDescription = "Seite ${pageIndex + 1}",
                contentScale = ContentScale.Fit,
                modifier = mod,
            )
        }

        // Tap-Zonen je Zustand
        Box(
            Modifier.fillMaxSize().pointerInput(state.zoomed, state.guidedEnabled, state.currentPanels) {
                detectTapGestures { offset ->
                    val w = size.width.toFloat()
                    val h = size.height.toFloat()
                    if (state.zoomed) {
                        when {
                            offset.x < w / 3f -> comicVm.previous()
                            offset.x > w * 2f / 3f -> comicVm.next()
                            else -> comicVm.zoomOut()
                        }
                    } else {
                        // Vollseite: normalisierter Tap → Panel treffen oder Chrome
                        comicVm.onPageTap(offset.x / w, offset.y / h)
                    }
                }
            },
        )

        ReaderChromeOverlay(
            visible = state.chromeVisible,
            title = "${state.position.page + 1} / $pageCount",
            onBack = onBack,
            actions = {
                IconButton(onClick = { comicVm.toggleGuided() }) {
                    Icon(
                        Icons.Filled.GridView,
                        contentDescription = if (state.guidedEnabled) "Panel-Modus aus" else "Panel-Modus an",
                        tint = if (state.guidedEnabled) Color.White else Color.Gray,
                    )
                }
                IconButton(onClick = onToggleMode) {
                    Icon(Icons.Filled.ViewDay, contentDescription = "Zu Webtoon-Modus wechseln", tint = Color.White)
                }
            },
        )
    }
}
```

> `ReaderChromeOverlay` ist die im Paged-/Webtoon-Reader genutzte Chrome-Komponente (`ReaderChrome.kt`). Signatur dort prüfen (`grep -n "fun ReaderChromeOverlay" app/.../ui/reader/ReaderChrome.kt`) und Parameter exakt übernehmen. Falls `title`/`actions` anders heißen, anpassen.

- [ ] **Step 2: HW-Tasten im gezoomten Zustand**

Comic nutzt im gezoomten Zustand die Hardware-Tasten für Panel-Schritte. Der bestehende `ReaderViewModel.collectButtonEvents` behandelt nur PAGED/WEBTOON. Da der Comic-Reader einen eigenen `ComicReaderViewModel` hat, im `ComicReaderScreen` zusätzlich den `HardwareButtonBus` beobachten — analog zur Lösung im Paged-Reader. Vor Implementierung prüfen, wie der Paged-Reader an den Bus kommt:

Run: `grep -rn "HardwareButtonBus\|bus.events\|frameStep" app/src/main/kotlin/com/komgareader/app/ui/reader`

Den Bus per `hiltViewModel`/Injection im `ComicReaderViewModel` aufnehmen (wie `ReaderViewModel` es tut) und in `collectButtonEvents` bei `zoomed` `next()/previous()` aufrufen, sonst Vollseiten-Blättern über `pagerState` (Screen beobachtet eine `requestedPage`-Flow analog `ReaderViewModel`). Muster aus `ReaderViewModel.kt:208-238` übernehmen.

- [ ] **Step 2b: E-Ink-Full-Refresh bei Bild-/Panel-Wechsel (Spec §5)**

Panel-Wechsel und Zoom-Out sind Bildwechsel → Full-Refresh wie der Seitenwechsel im Paged-Reader. `PagedReaderScreen` bekommt dafür einen `refresher: OnyxRefresher?` und ruft `refresher.fullRefreshIfNeeded(...)`. Muster prüfen:

Run: `grep -n "OnyxRefresher\|fullRefreshIfNeeded\|triggerGhostClearIfNeeded\|rootView" app/src/main/kotlin/com/komgareader/app/ui/reader/PagedReaderScreen.kt`

`ComicReaderScreen` ebenfalls `refresher: OnyxRefresher? = null` als Parameter aufnehmen (von `ReaderRoute` durchreichen, wie bei Paged) und in einem `LaunchedEffect(state.position, state.zoomed)` einen Full-Refresh über `rootView = LocalView.current` auslösen, sobald sich gezeigtes Panel oder Zoom-Status ändert (No-Op auf Nicht-Boox, HW-gated). Kein blindes `invalidate`.

- [ ] **Step 3: ReaderRoute verdrahten**

In `ReaderRoute.kt` in **beiden** `when (mode)`-Blöcken (`ReaderContent.Streamed` und `ReaderContent.Webtoon`) den COMIC-Zweig ergänzen:

```kotlin
ViewerMode.COMIC -> ComicReaderScreen(
    pages = c.pages,
    authHeaders = c.authHeaders,
    initialPage = c.initialPage,
    onBack = onBack,
    onToggleMode = viewModel::toggleViewerMode,
    refresher = refresher,
)
```

Außerdem `ReaderViewModel.toggleViewerMode()` so erweitern, dass COMIC im Zyklus sinnvoll bleibt (z.B. COMIC ↔ WEBTOON wie PAGED ↔ WEBTOON). Minimal: COMIC verhält sich beim Toggle wie PAGED. In `ReaderViewModel.kt:106-108`:

```kotlin
fun toggleViewerMode() {
    viewerMode.value = when (viewerMode.value) {
        ViewerMode.WEBTOON -> ViewerMode.PAGED
        else -> ViewerMode.WEBTOON
    }
}
```

- [ ] **Step 4: Voll-Build**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL — `when (mode)` ist jetzt erschöpfend (PAGED/WEBTOON/COMIC in beiden Blöcken).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/komgareader/app/ui/reader/ComicReaderScreen.kt app/src/main/kotlin/com/komgareader/app/ui/reader/ReaderRoute.kt app/src/main/kotlin/com/komgareader/app/ui/reader/ReaderViewModel.kt
git commit -m "feat(app): ComicReaderScreen + ReaderRoute-Verdrahtung fuer ViewerMode.COMIC"
```

---

## Task 8: i18n-Keys für den Panel-Toggle (app)

Sichtbarer Text immer über `i18n` (E-Ink-Regel). Die Content-Descriptions im Screen durch i18n-Keys ersetzen.

**Files:**
- Modify: `app/src/main/kotlin/com/komgareader/app/i18n/Strings.kt`
- Modify: `app/src/main/kotlin/com/komgareader/app/ui/reader/ComicReaderScreen.kt`

- [ ] **Step 1: Key-Struktur prüfen**

Run: `grep -n "readerWebtoonMode\|reader" app/src/main/kotlin/com/komgareader/app/i18n/Strings.kt | head`
Vorhandenes Reader-Key-Muster (DE+EN) ansehen, identisch fortführen.

- [ ] **Step 2: Keys ergänzen**

In `Strings.kt` analog vorhandener Reader-Keys (DE und EN, echte Umlaute):

```kotlin
val readerPanelModeOn: String   // DE "Panel-Modus an"  / EN "Panel mode on"
val readerPanelModeOff: String  // DE "Panel-Modus aus" / EN "Panel mode off"
```

Werte in beiden Sprach-Objekten setzen (Compile-Zeit-Parität — beide müssen den Key haben, sonst Build-Fehler).

- [ ] **Step 3: Screen auf i18n umstellen**

In `ComicReaderScreen.kt` die hartkodierten Strings ersetzen (Zugriff auf `Strings` wie in anderen Reader-Screens — Muster prüfen: `grep -n "Strings\|LocalStrings\|stringsOf" app/.../ui/reader/*.kt`):

```kotlin
contentDescription = if (state.guidedEnabled) strings.readerPanelModeOff else strings.readerPanelModeOn,
```

- [ ] **Step 4: Build**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/komgareader/app/i18n/Strings.kt app/src/main/kotlin/com/komgareader/app/ui/reader/ComicReaderScreen.kt
git commit -m "feat(app): i18n-Keys fuer Comic-Panel-Toggle"
```

---

## Task 9: Verifikation — Build, Unit-Tests, E2E

**Files:** keine (Verifikation).

- [ ] **Step 1: Alle relevanten Unit-Tests**

Run: `./gradlew :domain:test :guided-view:test`
Expected: PASS (ResolveViewerType inkl. COMIC, GuidedNavigator 6, PanelGeometry 4, PanelDetector unverändert grün).

- [ ] **Step 2: Voll-Build + Lint**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: E2E gegen lokale Test-Komga**

Lokale Test-Komga starten (siehe Memory `local-test-komga`). Eine Comic-Serie auf `COMIC` taggen:

`PATCH /api/v1/series/{id}/metadata` mit Body `{"readingDirection":"LEFT_TO_RIGHT"}` und das Regal/Serie-`contentType` auf COMIC setzen (bzw. Serien-Override COMIC). Verifizieren, dass `SeriesDetailViewModel` für deren Bücher `viewerMode = "COMIC"` liefert.

- [ ] **Step 4: Emulator-Verifikation (eink_test, 1264×1680@300)**

App auf Emulator `eink_test` installieren, Comic-Serie öffnen:
1. Vollseite erscheint.
2. Tap auf ein Panel → zoomt genau dieses Panel.
3. Rechts-Tap → nächstes Panel (ohne Zoom-Out); am letzten Panel → erstes Panel der Folgeseite.
4. Links-Tap → voriges Panel / Vorseite letztes Panel.
5. Mitte-Tap (gezoomt) → Zoom-Out auf Vollseite.
6. Tap in Gutter (Vollseite) → Chrome erscheint; Panel-Toggle aus → reines Vollseiten-Blättern.
7. Splash-Seite (0/1 Panel) → kein Zoom, ganze Seite.

Screenshot je Schritt zur Beweisführung (Behauptung „fertig" nur mit sichtbarem Verhalten — Arbeits-Invariante).

- [ ] **Step 5: Abschluss-Commit (falls Fixes aus E2E)**

```bash
git add -A && git commit -m "fix(app): E2E-Korrekturen Comic-Reader"
```

---

## Offene Folgepunkte (nicht in diesem Plan — YAGNI)

- RTL-Comics (selten; Manga löst auf PAGED auf) → bei Bedarf `readingDirection` als zusätzlichen Nav-Arg durchreichen und an `ComicPageLoader.detect` übergeben.
- Aspekt-genauer Zoom (derzeit `zoomScale` über das größere Panel-Maß; bei stark abweichendem Seitenverhältnis leicht konservativ) → exakte Viewport-/Image-Aspekt-Rechnung in `PanelGeometry`.
- Per-Region-Refresh-Feintuning des Panel-Wechsels (Phase 3).
