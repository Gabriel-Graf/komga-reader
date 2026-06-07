# Robuster Panel-Detektor — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Den XY-Cut-`PanelDetector` durch einen robusten Flood-Fill+Connected-Components-Detektor (Otsu) ersetzen, der echte Comicseiten zuverlässig segmentiert; plus Degenerate-Guard (nie auf ~Vollseite zoomen) und Letterbox-Tap/Zoom-Fix.

**Architecture:** Reine-Kotlin-Pipeline in `guided-view` aus kleinen, einzeln host-testbaren Einheiten: Binarisierung (Otsu) → Edge-Seed-Gutter-Flood-Fill → Connected-Component-Bounding-Boxes → Filter/Merge → Lesereihenfolge. Interface `PanelDetector.detect(page, dir): List<PanelRect>` bleibt stabil. Schlüsselidee: das weiße Gutter-Netz ist vom Seitenrand zusammenhängend; Flutung vom Rand trennt Panels auch dann, wenn Art eine Gasse ausbeult (XY-Cut brauchte volle saubere Gutter). Integrations-Fixes im `app`-Modul (VM-Guard, Screen-Letterbox).

**Tech Stack:** Kotlin (pure JVM in `guided-view`, JUnit5 + kotlin.test), Jetpack Compose (app), `domain.render.RenderedPage`.

**Maßgeblich:** `docs/superpowers/specs/2026-06-07-robust-panel-detector-design.md`, Skill `komga-guided-comic-reader`. Arbeitsverzeichnis: dieser Worktree (`feat/guided-comic-reader`). Alle `git`-Befehle von hier.

**Bestehende, unveränderte Typen** (`guided-view/.../Panel.kt`): `data class PanelRect(val x:Int, val y:Int, val width:Int, val height:Int)` mit `centerX`/`centerY`; `enum class ReadingDirection { LEFT_TO_RIGHT, RIGHT_TO_LEFT }`. `RenderedPage(width:Int, height:Int, pixels:IntArray)` (ARGB) aus `domain`.

---

## Dateienübersicht

**Neu (`guided-view/src/main/kotlin/com/komgareader/guidedview/`):**
- `ImageBinarization.kt` — Luminanz + Otsu-Schwelle + Hintergrund-Maske.
- `GutterFill.kt` — Edge-Seed-Flood-Fill des Hintergrund-/Gutter-Netzes.
- `RegionLabeling.kt` — Connected-Components der Nicht-gefluteten → Bounding-Boxes.
- `ReadingOrder.kt` — Boxen → Lesereihenfolge (Zeilen-Bänder, LTR/RTL).

**Neu (Test):**
- `guided-view/src/test/.../SyntheticPage.kt` — Test-Helper: baut `RenderedPage` aus Panel-Specs.
- `guided-view/src/test/.../ImageBinarizationTest.kt`, `GutterFillTest.kt`, `RegionLabelingTest.kt`, `ReadingOrderTest.kt`, neue `PanelDetectorTest.kt`.
- `guided-view/src/test/.../RealPageHarness.kt` — gitignored Harness gegen echte Seiten (manuell).

**Ersetzen/Ändern:**
- `guided-view/.../PanelDetector.kt` — XY-Cut-Interna komplett ersetzen, Interface gleich.
- `guided-view/.../PanelGeometry.kt` — `maxAreaFraction` + `fitScale` ergänzen.
- `app/.../ui/reader/ComicReaderViewModel.kt` — Degenerate-Guard.
- `app/.../ui/reader/ComicReaderScreen.kt` — Letterbox-Tap + transformOrigin + fitScale.
- `.gitignore` (Repo-Root) — `guided-view/realpages/`.
- `.claude/skills/komga-guided-comic-reader/SKILL.md` — Detektion + Guard + Letterbox aktualisieren.

---

## Task 1: ImageBinarization (Otsu) — pure, TDD

**Files:**
- Create: `guided-view/src/main/kotlin/com/komgareader/guidedview/ImageBinarization.kt`
- Test: `guided-view/src/test/kotlin/com/komgareader/guidedview/ImageBinarizationTest.kt`

- [ ] **Step 1: Failing test**

```kotlin
package com.komgareader.guidedview

import com.komgareader.domain.render.RenderedPage
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ImageBinarizationTest {
    private fun page(vararg lum: Int): RenderedPage {
        val px = IntArray(lum.size) { val v = lum[it]; (0xFF shl 24) or (v shl 16) or (v shl 8) or v }
        return RenderedPage(lum.size, 1, px)
    }

    @Test
    fun `Luminanz aus ARGB`() {
        assertEquals(0, ImageBinarization.luminance(0xFF000000.toInt()))
        assertEquals(255, ImageBinarization.luminance(0xFFFFFFFF.toInt()))
    }

    @Test
    fun `Otsu trennt zwei klar getrennte Populationen`() {
        // Hälfte dunkel (10), Hälfte hell (240) -> Schwelle dazwischen
        val p = page(10, 10, 10, 10, 240, 240, 240, 240)
        val t = ImageBinarization.otsuThreshold(p)
        assertTrue(t in 11..239, "Schwelle war $t")
    }

    @Test
    fun `backgroundMask markiert helle Pixel als Hintergrund`() {
        val p = page(10, 240)
        val mask = ImageBinarization.backgroundMask(p, threshold = 128)
        assertEquals(false, mask[0]) // dunkel = Vordergrund
        assertEquals(true, mask[1])  // hell = Hintergrund
    }
}
```

- [ ] **Step 2: Run, expect FAIL**

Run: `./gradlew :guided-view:test --tests "com.komgareader.guidedview.ImageBinarizationTest"`
Expected: FAIL — `ImageBinarization` ungelöst.

- [ ] **Step 3: Implement**

```kotlin
package com.komgareader.guidedview

import com.komgareader.domain.render.RenderedPage

/** Binarisierung einer Seite: Luminanz + adaptive Otsu-Schwelle → Hintergrund-Maske. Reines Kotlin. */
object ImageBinarization {

    /** Wahrgenommene Helligkeit 0..255 aus einem ARGB-Pixel (Rec. 601). */
    fun luminance(argb: Int): Int {
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return (r * 299 + g * 587 + b * 114) / 1000
    }

    /** Otsu-Schwelle: maximiert die Inter-Klassen-Varianz über das Luminanz-Histogramm. */
    fun otsuThreshold(page: RenderedPage): Int {
        val hist = IntArray(256)
        for (p in page.pixels) hist[luminance(p)]++
        val total = page.pixels.size
        if (total == 0) return 127
        var sumAll = 0.0
        for (t in 0..255) sumAll += t.toDouble() * hist[t]
        var sumB = 0.0
        var wB = 0
        var maxVar = -1.0
        var threshold = 127
        for (t in 0..255) {
            wB += hist[t]
            if (wB == 0) continue
            val wF = total - wB
            if (wF == 0) break
            sumB += t.toDouble() * hist[t]
            val mB = sumB / wB
            val mF = (sumAll - sumB) / wF
            val between = wB.toDouble() * wF * (mB - mF) * (mB - mF)
            if (between > maxVar) { maxVar = between; threshold = t }
        }
        return threshold
    }

    /** true = Hintergrund (Luminanz > [threshold]). */
    fun backgroundMask(page: RenderedPage, threshold: Int): BooleanArray =
        BooleanArray(page.pixels.size) { luminance(page.pixels[it]) > threshold }
}
```

- [ ] **Step 4: Run, expect PASS**

Run: `./gradlew :guided-view:test --tests "com.komgareader.guidedview.ImageBinarizationTest"`
Expected: PASS (3 Tests).

- [ ] **Step 5: Commit**

```bash
git add guided-view/src/main/kotlin/com/komgareader/guidedview/ImageBinarization.kt guided-view/src/test/kotlin/com/komgareader/guidedview/ImageBinarizationTest.kt
git commit -m "feat(guided-view): ImageBinarization (Luminanz + Otsu + Hintergrund-Maske)"
```

---

## Task 2: GutterFill (Edge-Seed-Flood) — pure, TDD

**Files:**
- Create: `guided-view/src/main/kotlin/com/komgareader/guidedview/GutterFill.kt`
- Test: `guided-view/src/test/kotlin/com/komgareader/guidedview/GutterFillTest.kt`

- [ ] **Step 1: Failing test**

```kotlin
package com.komgareader.guidedview

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GutterFillTest {
    // Hilfsbau einer Maske: true=Hintergrund. 'b'=bg, '#'=Vordergrund.
    private fun mask(vararg rows: String): Triple<BooleanArray, Int, Int> {
        val h = rows.size; val w = rows[0].length
        val m = BooleanArray(w * h)
        for (y in 0 until h) for (x in 0 until w) m[y * w + x] = rows[y][x] == 'b'
        return Triple(m, w, h)
    }

    @Test
    fun `Rand-Hintergrund wird geflutet, eingeschlossene Insel nicht`() {
        // 5x5: außen bg, in der Mitte ein Vordergrund-Ring mit bg-Insel innen
        val (m, w, h) = mask(
            "bbbbb",
            "b###b",
            "b#b#b",
            "b###b",
            "bbbbb",
        )
        val flooded = GutterFill.floodFromEdges(m, w, h)
        assertTrue(flooded[0])                 // Ecke (Rand) geflutet
        assertFalse(flooded[2 * w + 2])        // bg-Insel (2,2) NICHT geflutet (eingeschlossen)
        assertFalse(flooded[1 * w + 1])        // Vordergrund nie geflutet
    }

    @Test
    fun `Full-Bleed an der Kante wird nicht geflutet`() {
        // linke Spalte ist Vordergrund (Panel bleedet bis Rand) -> kein Seed dort
        val (m, w, h) = mask(
            "#bbb",
            "#bbb",
        )
        val flooded = GutterFill.floodFromEdges(m, w, h)
        assertFalse(flooded[0])      // (0,0) Vordergrund, kein Seed
        assertTrue(flooded[1])       // (1,0) bg vom rechten/oberen Rand erreichbar
    }
}
```

- [ ] **Step 2: Run, expect FAIL**

Run: `./gradlew :guided-view:test --tests "com.komgareader.guidedview.GutterFillTest"`
Expected: FAIL — `GutterFill` ungelöst.

- [ ] **Step 3: Implement**

```kotlin
package com.komgareader.guidedview

/**
 * Flutet das vom Seitenrand erreichbare Hintergrund-(Gutter-/Margin-)Netz.
 * Geseedet werden NUR helle Randpixel — dunkle Kanten (Full-Bleed-Panels) bleiben ungeflutet.
 * 4-Konnektivität. Reines Kotlin.
 */
object GutterFill {

    fun floodFromEdges(background: BooleanArray, width: Int, height: Int): BooleanArray {
        val flooded = BooleanArray(background.size)
        val stack = ArrayDeque<Int>()

        fun push(i: Int) {
            if (background[i] && !flooded[i]) { flooded[i] = true; stack.addLast(i) }
        }

        for (x in 0 until width) {
            push(x)                          // obere Kante
            push((height - 1) * width + x)   // untere Kante
        }
        for (y in 0 until height) {
            push(y * width)                  // linke Kante
            push(y * width + (width - 1))    // rechte Kante
        }

        while (stack.isNotEmpty()) {
            val i = stack.removeLast()
            val x = i % width
            val y = i / width
            if (x > 0) push(i - 1)
            if (x < width - 1) push(i + 1)
            if (y > 0) push(i - width)
            if (y < height - 1) push(i + width)
        }
        return flooded
    }
}
```

- [ ] **Step 4: Run, expect PASS**

Run: `./gradlew :guided-view:test --tests "com.komgareader.guidedview.GutterFillTest"`
Expected: PASS (2 Tests).

- [ ] **Step 5: Commit**

```bash
git add guided-view/src/main/kotlin/com/komgareader/guidedview/GutterFill.kt guided-view/src/test/kotlin/com/komgareader/guidedview/GutterFillTest.kt
git commit -m "feat(guided-view): GutterFill (Edge-Seed-Flood des Gutter-Netzes)"
```

---

## Task 3: RegionLabeling (Connected-Components) — pure, TDD

**Files:**
- Create: `guided-view/src/main/kotlin/com/komgareader/guidedview/RegionLabeling.kt`
- Test: `guided-view/src/test/kotlin/com/komgareader/guidedview/RegionLabelingTest.kt`

- [ ] **Step 1: Failing test**

```kotlin
package com.komgareader.guidedview

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class RegionLabelingTest {
    private fun mask(vararg rows: String): Triple<BooleanArray, Int, Int> {
        val h = rows.size; val w = rows[0].length
        val m = BooleanArray(w * h)
        for (y in 0 until h) for (x in 0 until w) m[y * w + x] = rows[y][x] == 'b'
        return Triple(m, w, h)
    }

    @Test
    fun `zwei nicht-geflutete Regionen ergeben zwei Boxen`() {
        // geflutet='b'; zwei #-Blöcke links/rechts, durch bg-Spalte getrennt
        val (flooded, w, h) = mask(
            "##b##",
            "##b##",
        )
        val boxes = RegionLabeling.labelRegions(flooded, w, h)
        assertEquals(2, boxes.size)
        val sorted = boxes.sortedBy { it.x }
        assertEquals(PanelRect(0, 0, 2, 2), sorted[0])
        assertEquals(PanelRect(3, 0, 2, 2), sorted[1])
    }

    @Test
    fun `vollständig geflutet ergibt keine Box`() {
        val (flooded, w, h) = mask("bbb", "bbb")
        assertEquals(0, RegionLabeling.labelRegions(flooded, w, h).size)
    }
}
```

- [ ] **Step 2: Run, expect FAIL**

Run: `./gradlew :guided-view:test --tests "com.komgareader.guidedview.RegionLabelingTest"`
Expected: FAIL — `RegionLabeling` ungelöst.

- [ ] **Step 3: Implement**

```kotlin
package com.komgareader.guidedview

/**
 * Labelt zusammenhängende NICHT-geflutete Regionen (= von Guttern umschlossene Panels)
 * per 8-Konnektivitäts-BFS und liefert deren Bounding-Boxes. Reines Kotlin.
 */
object RegionLabeling {

    fun labelRegions(flooded: BooleanArray, width: Int, height: Int): List<PanelRect> {
        val visited = BooleanArray(flooded.size)
        val boxes = mutableListOf<PanelRect>()
        val stack = ArrayDeque<Int>()

        for (start in flooded.indices) {
            if (flooded[start] || visited[start]) continue
            visited[start] = true
            stack.addLast(start)
            var minX = Int.MAX_VALUE; var minY = Int.MAX_VALUE
            var maxX = Int.MIN_VALUE; var maxY = Int.MIN_VALUE
            while (stack.isNotEmpty()) {
                val i = stack.removeLast()
                val x = i % width; val y = i / width
                if (x < minX) minX = x; if (x > maxX) maxX = x
                if (y < minY) minY = y; if (y > maxY) maxY = y
                var dy = -1
                while (dy <= 1) {
                    var dx = -1
                    while (dx <= 1) {
                        if (!(dx == 0 && dy == 0)) {
                            val nx = x + dx; val ny = y + dy
                            if (nx in 0 until width && ny in 0 until height) {
                                val ni = ny * width + nx
                                if (!flooded[ni] && !visited[ni]) { visited[ni] = true; stack.addLast(ni) }
                            }
                        }
                        dx++
                    }
                    dy++
                }
            }
            boxes.add(PanelRect(minX, minY, maxX - minX + 1, maxY - minY + 1))
        }
        return boxes
    }
}
```

- [ ] **Step 4: Run, expect PASS**

Run: `./gradlew :guided-view:test --tests "com.komgareader.guidedview.RegionLabelingTest"`
Expected: PASS (2 Tests).

- [ ] **Step 5: Commit**

```bash
git add guided-view/src/main/kotlin/com/komgareader/guidedview/RegionLabeling.kt guided-view/src/test/kotlin/com/komgareader/guidedview/RegionLabelingTest.kt
git commit -m "feat(guided-view): RegionLabeling (Connected-Component-Bounding-Boxes)"
```

---

## Task 4: ReadingOrder (Zeilen-Bänder, LTR/RTL) — pure, TDD

**Files:**
- Create: `guided-view/src/main/kotlin/com/komgareader/guidedview/ReadingOrder.kt`
- Test: `guided-view/src/test/kotlin/com/komgareader/guidedview/ReadingOrderTest.kt`

- [ ] **Step 1: Failing test**

```kotlin
package com.komgareader.guidedview

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ReadingOrderTest {
    // 2x2-Raster: TL, TR (obere Reihe y=0), BL, BR (untere Reihe y=100)
    private val tl = PanelRect(0, 0, 40, 40)
    private val tr = PanelRect(60, 0, 40, 40)
    private val bl = PanelRect(0, 100, 40, 40)
    private val br = PanelRect(60, 100, 40, 40)

    @Test
    fun `LTR liest zeilenweise links nach rechts, oben nach unten`() {
        val out = ReadingOrder.sort(listOf(br, tr, bl, tl), ReadingDirection.LEFT_TO_RIGHT)
        assertEquals(listOf(tl, tr, bl, br), out)
    }

    @Test
    fun `RTL liest je Zeile rechts nach links`() {
        val out = ReadingOrder.sort(listOf(tl, tr, bl, br), ReadingDirection.RIGHT_TO_LEFT)
        assertEquals(listOf(tr, tl, br, bl), out)
    }

    @Test
    fun `leere Liste bleibt leer`() {
        assertEquals(emptyList(), ReadingOrder.sort(emptyList(), ReadingDirection.LEFT_TO_RIGHT))
    }
}
```

- [ ] **Step 2: Run, expect FAIL**

Run: `./gradlew :guided-view:test --tests "com.komgareader.guidedview.ReadingOrderTest"`
Expected: FAIL — `ReadingOrder` ungelöst.

- [ ] **Step 3: Implement**

```kotlin
package com.komgareader.guidedview

/**
 * Sortiert Panel-Boxen in Lesereihenfolge: in Zeilen-Bänder gruppieren (vertikale Überlappung
 * mit dem aktuellen Band), Bänder oben→unten, je Band links→rechts (LTR) bzw. rechts→links (RTL).
 */
object ReadingOrder {

    fun sort(boxes: List<PanelRect>, direction: ReadingDirection): List<PanelRect> {
        if (boxes.isEmpty()) return boxes
        val byTop = boxes.sortedBy { it.y }
        val rows = mutableListOf<MutableList<PanelRect>>()
        for (b in byTop) {
            val row = rows.lastOrNull()
            if (row != null && overlapsRow(row, b)) row.add(b) else rows.add(mutableListOf(b))
        }
        return rows.flatMap { row ->
            val ltr = row.sortedBy { it.x }
            if (direction == ReadingDirection.RIGHT_TO_LEFT) ltr.reversed() else ltr
        }
    }

    /** true, wenn [b] vertikal hinreichend mit dem bisherigen Band überlappt (gleiche Zeile). */
    private fun overlapsRow(row: List<PanelRect>, b: PanelRect): Boolean {
        val top = row.minOf { it.y }
        val bottom = row.maxOf { it.y + it.height }
        val overlap = minOf(bottom, b.y + b.height) - maxOf(top, b.y)
        return overlap > b.height / 2
    }
}
```

- [ ] **Step 4: Run, expect PASS**

Run: `./gradlew :guided-view:test --tests "com.komgareader.guidedview.ReadingOrderTest"`
Expected: PASS (3 Tests).

- [ ] **Step 5: Commit**

```bash
git add guided-view/src/main/kotlin/com/komgareader/guidedview/ReadingOrder.kt guided-view/src/test/kotlin/com/komgareader/guidedview/ReadingOrderTest.kt
git commit -m "feat(guided-view): ReadingOrder (Zeilen-Bänder, LTR/RTL)"
```

---

## Task 5: PanelDetector neu + synthetischer Seiten-Generator — TDD

Ersetzt die XY-Cut-Interna. Orchestriert die Pipeline + CC-Verfeinerung (Min-Fläche-Filter, enthaltene Boxen mergen).

**Files:**
- Modify (komplett ersetzen): `guided-view/src/main/kotlin/com/komgareader/guidedview/PanelDetector.kt`
- Create: `guided-view/src/test/kotlin/com/komgareader/guidedview/SyntheticPage.kt`
- Replace: `guided-view/src/test/kotlin/com/komgareader/guidedview/PanelDetectorTest.kt`

- [ ] **Step 1: Test-Helper (synthetischer Seiten-Generator)**

`SyntheticPage.kt`:

```kotlin
package com.komgareader.guidedview

import com.komgareader.domain.render.RenderedPage

/** Baut synthetische Comicseiten als RenderedPage für Detektor-Tests. */
object SyntheticPage {
    private const val WHITE = 0xFFFFFFFF.toInt()
    private const val BLACK = 0xFF101010.toInt()

    /**
     * Weiße Seite [w]x[h]; jedes Rechteck in [panels] wird dunkel gefüllt (Panel-Inhalt).
     * [holes] werden danach wieder weiß gefüllt (z. B. Sprechblasen-Inseln).
     */
    fun of(w: Int, h: Int, panels: List<PanelRect>, holes: List<PanelRect> = emptyList()): RenderedPage {
        val px = IntArray(w * h) { WHITE }
        fun fill(r: PanelRect, color: Int) {
            for (y in r.y until r.y + r.height) for (x in r.x until r.x + r.width) {
                if (x in 0 until w && y in 0 until h) px[y * w + x] = color
            }
        }
        panels.forEach { fill(it, BLACK) }
        holes.forEach { fill(it, WHITE) }
        return RenderedPage(w, h, px)
    }
}
```

- [ ] **Step 2: Failing tests** — `PanelDetectorTest.kt` (ersetzt die alte Datei vollständig)

```kotlin
package com.komgareader.guidedview

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PanelDetectorTest {
    private val det = PanelDetector()

    @Test
    fun `sauberes 3x2-Raster ergibt 6 Panels in LTR-Reihenfolge`() {
        // 1000x800, 20px Gutter, 6 Panels (3 Spalten x 2 Reihen)
        val panels = mutableListOf<PanelRect>()
        val pw = 300; val ph = 370; val gx = 20; val gy = 20; val m = 20
        for (row in 0..1) for (col in 0..2) {
            panels.add(PanelRect(m + col * (pw + gx), m + row * (ph + gy), pw, ph))
        }
        val page = SyntheticPage.of(1000, 800, panels)
        val out = det.detect(page, ReadingDirection.LEFT_TO_RIGHT)
        assertEquals(6, out.size, "Erwarte 6 Panels, war ${out.size}")
        // Reihenfolge: oben-links zuerst, dann nach rechts
        assertTrue(out[0].x < out[1].x && out[1].x < out[2].x)
        assertTrue(out[0].y < out[3].y) // erste Reihe vor zweiter
    }

    @Test
    fun `Sprechblase im Panel bleibt ein Panel`() {
        val panel = PanelRect(50, 50, 900, 700)
        val bubble = PanelRect(400, 300, 200, 150) // weiße Insel
        val page = SyntheticPage.of(1000, 800, listOf(panel), holes = listOf(bubble))
        val out = det.detect(page, ReadingDirection.LEFT_TO_RIGHT)
        assertEquals(1, out.size)
    }

    @Test
    fun `Full-Bleed bis zur Kante bleibt erhalten`() {
        // Panel bleedet bis zur linken/oberen/unteren Kante (x=0), rechts daneben ein zweites Panel
        val left = PanelRect(0, 0, 460, 800)
        val right = PanelRect(500, 20, 480, 760)
        val page = SyntheticPage.of(1000, 800, listOf(left, right))
        val out = det.detect(page, ReadingDirection.LEFT_TO_RIGHT)
        assertEquals(2, out.size)
    }

    @Test
    fun `Blank-Seite ergibt keine Panels`() {
        val page = SyntheticPage.of(1000, 800, emptyList())
        assertEquals(0, det.detect(page, ReadingDirection.LEFT_TO_RIGHT).size)
    }

    @Test
    fun `Einzelnes Vollseiten-Panel ergibt genau ein Panel`() {
        val page = SyntheticPage.of(1000, 800, listOf(PanelRect(20, 20, 960, 760)))
        assertEquals(1, det.detect(page, ReadingDirection.LEFT_TO_RIGHT).size)
    }

    @Test
    fun `Mini-Fleck unter Min-Fläche wird verworfen`() {
        val panel = PanelRect(40, 40, 900, 700)
        val speck = PanelRect(10, 10, 8, 8) // ~0.0001 Fläche
        val page = SyntheticPage.of(1000, 800, listOf(panel, speck))
        assertEquals(1, det.detect(page, ReadingDirection.LEFT_TO_RIGHT).size)
    }
}
```

- [ ] **Step 3: Run, expect FAIL** (altes XY-Cut-Verhalten/Signatur)

Run: `./gradlew :guided-view:test --tests "com.komgareader.guidedview.PanelDetectorTest"`
Expected: FAIL/Compile-Fehler (alte `PanelDetector`-Konstruktorparameter, anderes Verhalten).

- [ ] **Step 4: PanelDetector komplett ersetzen**

`PanelDetector.kt` (gesamter Dateiinhalt):

```kotlin
package com.komgareader.guidedview

import com.komgareader.domain.render.RenderedPage

/**
 * Erkennt Panels via Hybrid Flood-Fill + Connected-Components (reines Kotlin, host-testbar):
 * Otsu-Binarisierung → Edge-Seed-Gutter-Flood → Component-Bounding-Boxes → Filter/Merge →
 * Lesereihenfolge. Das weiße Gutter-Netz ist vom Seitenrand zusammenhängend, daher trennt die
 * Flutung Panels auch bei in die Gasse ragender Art (anders als der frühere XY-Cut).
 *
 * @param minPanelAreaFraction  Boxen kleiner als dieser Seitenflächen-Anteil werden verworfen.
 * @param containmentFraction   Box gilt als „enthalten" wenn dieser Anteil ihrer Fläche in einer größeren liegt.
 */
class PanelDetector(
    private val minPanelAreaFraction: Double = 0.01,
    private val containmentFraction: Double = 0.8,
) {

    fun detect(page: RenderedPage, direction: ReadingDirection): List<PanelRect> {
        if (page.width <= 0 || page.height <= 0 || page.pixels.isEmpty()) return emptyList()
        val threshold = ImageBinarization.otsuThreshold(page)
        val background = ImageBinarization.backgroundMask(page, threshold)
        val flooded = GutterFill.floodFromEdges(background, page.width, page.height)
        val regions = RegionLabeling.labelRegions(flooded, page.width, page.height)

        val minArea = page.width.toLong() * page.height * minPanelAreaFraction
        val filtered = regions.filter { it.width.toLong() * it.height >= minArea }
        val merged = dropContained(filtered)
        return ReadingOrder.sort(merged, direction)
    }

    /** Entfernt Boxen, die zu [containmentFraction] in einer anderen (größeren) liegen. */
    private fun dropContained(boxes: List<PanelRect>): List<PanelRect> {
        val bySize = boxes.sortedByDescending { it.width.toLong() * it.height }
        val kept = mutableListOf<PanelRect>()
        for (b in bySize) {
            val area = b.width.toLong() * b.height
            val contained = kept.any { k ->
                val ix = maxOf(b.x, k.x); val iy = maxOf(b.y, k.y)
                val ax = minOf(b.x + b.width, k.x + k.width); val ay = minOf(b.y + b.height, k.y + k.height)
                val iw = ax - ix; val ih = ay - iy
                if (iw <= 0 || ih <= 0) false
                else iw.toLong() * ih >= area * containmentFraction
            }
            if (!contained) kept.add(b)
        }
        return kept
    }
}
```

- [ ] **Step 5: Run, expect PASS**

Run: `./gradlew :guided-view:test --tests "com.komgareader.guidedview.PanelDetectorTest"`
Expected: PASS (6 Tests). Falls das 3×2-Raster nicht exakt 6 liefert: prüfen, dass der Generator-Gutter (20px) > 0 und reinweiß ist — der Flood muss durchlaufen. Min-Fläche 0.01 nicht zu hoch.

- [ ] **Step 6: Alt-Test `PanelDetectorE2ETest` anpassen**

`guided-view/src/test/kotlin/com/komgareader/guidedview/PanelDetectorE2ETest.kt` existiert noch und nutzt die alte API/Erwartungen (XY-Cut, alte Konstruktorparameter wie `darkThreshold`). Datei lesen: nutzt sie nur synthetische Klar-Fälle (saubere Gutter), die Tests auf den neuen `SyntheticPage`-Generator + neue API umschreiben (Verhalten: gleiche Panel-Zahl/Reihenfolge auf sauberen Seiten). Referenziert sie nur entferntes XY-Cut-Verhalten ohne Mehrwert (Confidence/Communication), Datei löschen. Danach:

Run: `./gradlew :guided-view:test`
Expected: BUILD SUCCESSFUL (GuidedNavigator/PanelGeometry/neue Units + angepasster E2E-Test grün).

- [ ] **Step 7: Commit**

```bash
git add guided-view/src/main/kotlin/com/komgareader/guidedview/PanelDetector.kt guided-view/src/test/kotlin/com/komgareader/guidedview/PanelDetector*.kt guided-view/src/test/kotlin/com/komgareader/guidedview/SyntheticPage.kt
git commit -m "feat(guided-view): PanelDetector neu (Flood-Fill + CC), XY-Cut ersetzt"
```

---

## Task 6: PanelGeometry erweitern (maxAreaFraction + fitScale) — pure, TDD

**Files:**
- Modify: `guided-view/src/main/kotlin/com/komgareader/guidedview/PanelGeometry.kt`
- Test: `guided-view/src/test/kotlin/com/komgareader/guidedview/PanelGeometryTest.kt` (ergänzen)

- [ ] **Step 1: Failing tests anhängen** (an die bestehende `PanelGeometryTest`-Klasse)

```kotlin
    @Test
    fun `maxAreaFraction liefert den größten Flächenanteil`() {
        val a = NormRect(0f, 0f, 0.3f, 0.4f)      // 0.12
        val b = NormRect(0.5f, 0.5f, 0.4f, 0.5f)  // 0.20
        assertTrue(kotlin.math.abs(PanelGeometry.maxAreaFraction(listOf(a, b)) - 0.20f) < 1e-4f)
        assertEquals(0f, PanelGeometry.maxAreaFraction(emptyList()))
    }

    @Test
    fun `fitScale füllt das Panel im Viewport mit Rand`() {
        // Bild content 1000x1500 in Viewport 1000x1500 (kein Letterbox), Panel halbe Höhe
        val panel = NormRect(0f, 0f, 0.5f, 0.5f)
        val s = PanelGeometry.fitScale(panel, contentW = 1000f, contentH = 1500f, viewportW = 1000f, viewportH = 1500f, marginFraction = 0.05f)
        // begrenzend ist die Höhe: (1-0.1)/0.5 = 1.8
        assertTrue(kotlin.math.abs(s - 1.8f) < 1e-3f, "war $s")
    }

    @Test
    fun `fitScale berücksichtigt Letterbox (schmaleres Content-Rechteck)`() {
        // Content nur 800 breit in 1000-Viewport (Letterbox), Panel volle Breite
        val panel = NormRect(0f, 0f, 1.0f, 0.25f)
        val s = PanelGeometry.fitScale(panel, contentW = 800f, contentH = 1500f, viewportW = 1000f, viewportH = 1500f, marginFraction = 0f)
        // Breite begrenzt: viewportW/(wN*contentW)=1000/800=1.25; Höhe: 1500/(0.25*1500)=4 -> min=1.25
        assertTrue(kotlin.math.abs(s - 1.25f) < 1e-3f, "war $s")
    }
```

- [ ] **Step 2: Run, expect FAIL**

Run: `./gradlew :guided-view:test --tests "com.komgareader.guidedview.PanelGeometryTest"`
Expected: FAIL — `maxAreaFraction`/`fitScale` ungelöst.

- [ ] **Step 3: In `PanelGeometry` (object) ergänzen**

```kotlin
    /** Größter normalisierter Flächenanteil (w*h) unter den Panels; 0 wenn leer. */
    fun maxAreaFraction(panels: List<NormRect>): Float =
        panels.maxOfOrNull { it.width * it.height } ?: 0f

    /**
     * Skalierungsfaktor, mit dem [panel] (bild-normalisiert) im Viewport bildschirmfüllend wird,
     * unter Berücksichtigung des bei ContentScale.Fit dargestellten Content-Rechtecks
     * ([contentW]x[contentH]) innerhalb des Viewports ([viewportW]x[viewportH]). Pivot = Panel-Mitte.
     */
    fun fitScale(
        panel: NormRect,
        contentW: Float, contentH: Float,
        viewportW: Float, viewportH: Float,
        marginFraction: Float,
    ): Float {
        val panelW = (panel.width * contentW).coerceAtLeast(1f)
        val panelH = (panel.height * contentH).coerceAtLeast(1f)
        val sx = viewportW / panelW
        val sy = viewportH / panelH
        return (1f - 2f * marginFraction) * minOf(sx, sy)
    }
```

- [ ] **Step 4: Run, expect PASS**

Run: `./gradlew :guided-view:test --tests "com.komgareader.guidedview.PanelGeometryTest"`
Expected: PASS (bestehende 4 + 3 neue).

- [ ] **Step 5: Commit**

```bash
git add guided-view/src/main/kotlin/com/komgareader/guidedview/PanelGeometry.kt guided-view/src/test/kotlin/com/komgareader/guidedview/PanelGeometryTest.kt
git commit -m "feat(guided-view): PanelGeometry.maxAreaFraction + fitScale (Letterbox-bewusst)"
```

---

## Task 7: Degenerate-Guard im ComicReaderViewModel

Eine Seite mit <2 Panels ODER einem Panel >85 % Fläche wird als Vollseiten-Fallback behandelt (keine Zoom-Einheiten → Tap blättert/öffnet Chrome statt „auf ganze Seite zu zoomen").

**Files:**
- Modify: `app/src/main/kotlin/com/komgareader/app/ui/reader/ComicReaderViewModel.kt` (Methode `loadPanels`/`ensurePanels`)

- [ ] **Step 1: `loadPanels` anpassen**

Den Block, der `panelCache`/`unitsPerPage` setzt, so ändern, dass degenerierte Panel-Mengen verworfen werden. In `loadPanels` (nach `val norms = det.panels.map { ... }`):

```kotlin
val usable = if (norms.size < 2 || PanelGeometry.maxAreaFraction(norms) > 0.85f) emptyList() else norms
panelCache[page] = usable
unitsPerPage[page] = if (usable.isEmpty()) 1 else usable.size
return usable
```

(Import sicherstellen: `import com.komgareader.guidedview.PanelGeometry` — sollte vorhanden sein.)

Dadurch ist `currentPanels` bei degenerierten Seiten leer → `onPageTap` greift in den Fallback-Zweig (Drittel-Tap-Zonen / Chrome), und `step()` zoomt nie auf ein ~ganzseitiges Panel.

- [ ] **Step 2: Build**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/komgareader/app/ui/reader/ComicReaderViewModel.kt
git commit -m "fix(app): Degenerate-Guard — nie auf ~ganzseitiges Panel zoomen"
```

---

## Task 8: Letterbox-Fix im ComicReaderScreen

Tap-Normalisierung + `transformOrigin` + Zoom-Scale gegen das real dargestellte Bild-Rechteck (Fit-Letterbox) rechnen. Bild-Aspekt aus der aktuellen Seite (`pageWidth/pageHeight` der Detektion); solange unbekannt, Viewport-Aspekt als Näherung.

**Files:**
- Modify: `app/src/main/kotlin/com/komgareader/app/ui/reader/ComicReaderViewModel.kt` (Aspekt in State exponieren)
- Modify: `app/src/main/kotlin/com/komgareader/app/ui/reader/ComicReaderScreen.kt`

- [ ] **Step 1: Aspekt im VM-State exponieren**

In `ComicUiState` ein Feld ergänzen:

```kotlin
data class ComicUiState(
    val guidedEnabled: Boolean = true,
    val zoomed: Boolean = false,
    val position: GuidedPosition = GuidedPosition(0, 0),
    val currentPanels: List<NormRect> = emptyList(),
    val chromeVisible: Boolean = false,
    /** Seitenverhältnis (Breite/Höhe) der aktuellen Seite; 0 = unbekannt. */
    val pageAspect: Float = 0f,
)
```

In `loadPanels` das Aspekt-Verhältnis mitführen: nach Detektion, wenn `det.pageWidth>0 && det.pageHeight>0` und die Seite die aktuelle ist, `_uiState` zusätzlich mit `pageAspect = det.pageWidth.toFloat()/det.pageHeight` aktualisieren. Konkret beim State-Update in `loadPanels` (und im `ensurePanels`-Update) `pageAspect` mitsetzen, wenn `position.page == page`.

Beispiel (im `ensurePanels`-Callback und überall, wo `currentPanels` für die aktuelle Seite gesetzt wird):

```kotlin
if (_uiState.value.position.page == page) {
    _uiState.value = _uiState.value.copy(
        currentPanels = usable,
        pageAspect = if (det.pageWidth > 0 && det.pageHeight > 0) det.pageWidth.toFloat() / det.pageHeight else _uiState.value.pageAspect,
    )
}
```
(Damit `det` hier verfügbar ist, das Aspekt-Verhältnis innerhalb `loadPanels` berechnen und zurückgeben oder dort direkt setzen. Einfachste Variante: `loadPanels` setzt `pageAspect` selbst, da es `det` hat.)

- [ ] **Step 2: Screen — Content-Rechteck + korrekte Tap/Zoom**

In `ComicReaderScreen` die `graphicsLayer`- und Tap-Logik ersetzen. Hilfsrechnung im Composable (mit `BoxWithConstraints` für Viewport-Maße):

Ersetze das äußere `Box(...)` durch `BoxWithConstraints(...)`, hole `val vw = constraints.maxWidth.toFloat(); val vh = constraints.maxHeight.toFloat()`, und berechne das Content-Rechteck:

```kotlin
val aspect = if (state.pageAspect > 0f) state.pageAspect else vw / vh
// ContentScale.Fit: Content füllt die begrenzende Dimension, zentriert
val contentW: Float; val contentH: Float
if (aspect < vw / vh) { contentH = vh; contentW = vh * aspect } else { contentW = vw; contentH = vw / aspect }
val offX = (vw - contentW) / 2f
val offY = (vh - contentH) / 2f
```

Zoom-Modifier des aktuellen Bildes:

```kotlin
val panel = if (isCurrent && state.zoomed) state.currentPanels.getOrNull(state.position.unit) else null
val mod = if (panel != null) {
    val scale = PanelGeometry.fitScale(panel, contentW, contentH, vw, vh, marginFraction = 0.05f)
    // Pivot = Panel-Mitte, ausgedrückt als Viewport-Fraktion (inkl. Letterbox-Offset)
    val pivotX = (offX + panel.centerX * contentW) / vw
    val pivotY = (offY + panel.centerY * contentH) / vh
    Modifier.fillMaxSize().graphicsLayer(
        scaleX = scale, scaleY = scale,
        transformOrigin = TransformOrigin(pivotX, pivotY),
    )
} else Modifier.fillMaxSize()
```

Tap-Normalisierung gegen das Content-Rechteck:

```kotlin
detectTapGestures { offset ->
    if (state.zoomed) {
        when {
            offset.x < vw / 3f -> comicVm.previous()
            offset.x > vw * 2f / 3f -> comicVm.next()
            else -> comicVm.zoomOut()
        }
    } else {
        val xN = ((offset.x - offX) / contentW).coerceIn(0f, 1f)
        val yN = ((offset.y - offY) / contentH).coerceIn(0f, 1f)
        comicVm.onPageTap(xN, yN)
    }
}
```

Imports ergänzen: `androidx.compose.foundation.layout.BoxWithConstraints`. `PanelGeometry` und `TransformOrigin` sind schon importiert (Task 7a).

- [ ] **Step 3: Build**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL. (Falls `BoxWithConstraints`-Scope `constraints` nicht auflöst: sicherstellen, dass der gesamte Inhalt im `BoxWithConstraints { }`-Lambda steht.)

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/komgareader/app/ui/reader/ComicReaderViewModel.kt app/src/main/kotlin/com/komgareader/app/ui/reader/ComicReaderScreen.kt
git commit -m "fix(app): Letterbox-bewusste Tap-Normalisierung + Panel-Zoom (fitScale, Pivot)"
```

---

## Task 9: Real-Harness (gitignored) + .gitignore + Skill-Update + Verifikation

**Files:**
- Create: `guided-view/src/test/kotlin/com/komgareader/guidedview/RealPageHarness.kt`
- Modify: `.gitignore` (Repo-Root)
- Modify: `.claude/skills/komga-guided-comic-reader/SKILL.md`

- [ ] **Step 1: .gitignore ergänzen**

An `.gitignore` (Repo-Root) anhängen:

```
# Lokaler Real-Page-Korpus für PanelDetector-Harness (urheberrechtlich, nie committen)
guided-view/realpages/
```

- [ ] **Step 2: Real-Page-Korpus lokal befüllen**

```bash
mkdir -p guided-view/realpages
cd guided-view/realpages
unzip -o "/mnt/nas/Manga/Comics/Marvel/The Amazing Spider-Man/Amazing Spider-Man 001 (2025) (Digital) (Shan-Empire).cbz" >/dev/null
cd -
```
(Liegt unter dem gitignorten Pfad — wird nicht eingecheckt.)

- [ ] **Step 3: Harness schreiben** (`@Disabled` by default, manuell aktivierbar)

`RealPageHarness.kt`:

```kotlin
package com.komgareader.guidedview

import com.komgareader.domain.render.RenderedPage
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * MANUELLER Harness (nicht committed laufend): läuft PanelDetector gegen echte Seiten in
 * guided-view/realpages/** (gitignored), skaliert wie ComicPageLoader auf ~1000px und schreibt
 * Overlay-PNGs mit Panel-Boxen + Reihenfolge nach guided-view/realpages/_out/.
 * Aktivieren: @Disabled entfernen und `./gradlew :guided-view:test --tests "*RealPageHarness*"`.
 */
@Disabled("Manueller Harness — lokal aktivieren")
class RealPageHarness {

    private val detector = PanelDetector()
    private val detectionWidth = 1000

    @Test
    fun `Panels auf echten Seiten visualisieren`() {
        val root = File("realpages")
        val out = File(root, "_out").apply { mkdirs() }
        val files = root.walkTopDown().filter {
            it.isFile && it.extension.lowercase() in setOf("jpg", "jpeg", "png")
        }.sorted().toList()
        println("Harness: ${files.size} Seiten")
        for (f in files) {
            val src = ImageIO.read(f) ?: continue
            val scaled = downscale(src)
            val page = toRenderedPage(scaled)
            val panels = detector.detect(page, ReadingDirection.LEFT_TO_RIGHT)
            drawOverlay(scaled, panels, File(out, f.nameWithoutExtension + "_panels.png"))
            println("${f.name}: ${panels.size} Panels")
        }
    }

    private fun downscale(src: BufferedImage): BufferedImage {
        if (src.width <= detectionWidth) return src
        val r = detectionWidth.toDouble() / src.width
        val h = (src.height * r).toInt()
        val dst = BufferedImage(detectionWidth, h, BufferedImage.TYPE_INT_ARGB)
        val g = dst.createGraphics()
        g.drawImage(src.getScaledInstance(detectionWidth, h, java.awt.Image.SCALE_AREA_AVERAGING), 0, 0, null)
        g.dispose()
        return dst
    }

    private fun toRenderedPage(b: BufferedImage): RenderedPage {
        val px = IntArray(b.width * b.height)
        b.getRGB(0, 0, b.width, b.height, px, 0, b.width)
        return RenderedPage(b.width, b.height, px)
    }

    private fun drawOverlay(base: BufferedImage, panels: List<PanelRect>, dest: File) {
        val img = BufferedImage(base.width, base.height, BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()
        g.drawImage(base, 0, 0, null)
        g.color = Color.RED
        panels.forEachIndexed { i, p ->
            g.drawRect(p.x, p.y, p.width - 1, p.height - 1)
            g.drawString("#$i", p.x + 4, p.y + 16)
        }
        g.dispose()
        ImageIO.write(img, "png", dest)
    }
}
```

- [ ] **Step 4: Harness manuell laufen lassen (Beweis)**

```bash
# @Disabled temporär entfernen ODER mit Property überschreiben:
sed -i 's/@Disabled("Manueller Harness — lokal aktivieren")//' guided-view/src/test/kotlin/com/komgareader/guidedview/RealPageHarness.kt
./gradlew :guided-view:test --tests "com.komgareader.guidedview.RealPageHarness" -i 2>&1 | grep -E "Seiten|Panels"
git checkout guided-view/src/test/kotlin/com/komgareader/guidedview/RealPageHarness.kt  # @Disabled zurück
```
Erwartung: die meisten Seiten ergeben mehrere (>1, <85%) Panels; die Doppelseite und Splash-Seiten ggf. 1 (→ Fallback). Overlay-PNGs in `guided-view/realpages/_out/` per Read-Tool sichten: Boxen liegen plausibel auf den Panels, Reihenfolge stimmt. Bei systematischer Unter-/Über-Segmentierung: `minPanelAreaFraction`/Otsu nachziehen und Task 5 erneut.

- [ ] **Step 5: Skill aktualisieren**

In `.claude/skills/komga-guided-comic-reader/SKILL.md` den Detektions-Absatz ersetzen: nicht mehr XY-Cut, sondern „Flood-Fill + Connected-Components (Otsu-Binarisierung, Edge-Seed-Gutter-Flood, Component-Boxes, Lesereihenfolge)". Anti-Pattern „eigene Richtungserkennung" bleibt. Ergänzen: Degenerate-Guard (Panel >85 % oder <2 → Vollseite-Fallback) und Letterbox-bewusste Tap/Zoom (`fitScale`, Content-Rechteck). Pipeline-Units (`ImageBinarization`/`GutterFill`/`RegionLabeling`/`ReadingOrder`) nennen.

- [ ] **Step 6: Volle Verifikation**

Run: `./gradlew :guided-view:test :domain:test :app:assembleDebug`
Expected: BUILD SUCCESSFUL, alle Unit-Tests grün.

- [ ] **Step 7: Commit**

```bash
git add .gitignore guided-view/src/test/kotlin/com/komgareader/guidedview/RealPageHarness.kt .claude/skills/komga-guided-comic-reader/SKILL.md
git commit -m "test(guided-view): Real-Page-Harness (gitignored) + Skill-Update Detektor"
```

---

## Offen / nicht in diesem Plan (YAGNI)

- Optionaler interner Projektions-Split sehr großer Komponenten: Flood-Fill trennt interne Gutter bereits (sie sind mit dem Margin verbunden). Erst nachrüsten, wenn der Real-Harness eine konkrete Unter-Segmentierung zeigt.
- On-Device-E2E (Boox/Emulator) des sichtbaren Zoom-Verhaltens: separat, sobald NAS/Server stabil verbunden.
