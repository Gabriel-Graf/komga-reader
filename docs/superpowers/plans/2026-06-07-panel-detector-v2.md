# Panel-Detektor v2 (Rahmen-Linien-Split + Bubble-Filter) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development. Steps use checkbox (`- [ ]`).

**Goal:** Schwarz-umrandete/eng liegende Panels trennen (achsenparalleler Rahmen-Linien-Split) und Sprechblasen nie als Panel zählen — Interface `PanelDetector.detect(page, dir): List<PanelRect>` unverändert.

**Architecture:** Neue pure Einheit `BorderLineSplit` (rekursiver Schnitt an dünnen durchgehenden dunklen H/V-Linien zwischen helleren Bereichen) wird in `PanelDetector` zwischen Connected-Components und Lesereihenfolge eingehängt; plus ein Bubble/Containment-Filter. Alles in `guided-view`, reines Kotlin, host-testbar.

**Tech Stack:** Kotlin, JUnit5 + kotlin.test. Bestehende Units: `ImageBinarization` (luminance/otsuThreshold/backgroundMask), `GutterFill`, `RegionLabeling`, `ReadingOrder`, `PanelDetector`, `PanelRect`, `ReadingDirection`, `RenderedPage`.

**Maßgeblich:** `docs/superpowers/specs/2026-06-07-panel-detector-v2-line-detection-design.md`. Worktree `feat/guided-comic-reader`. Baseline (alter Detektor, 300 Seiten): 41 % Fallback.

---

## Task 1: BorderLineSplit — rekursiver Schnitt an Rahmenlinien (pure, TDD)

**Files:**
- Create: `guided-view/src/main/kotlin/com/komgareader/guidedview/BorderLineSplit.kt`
- Test: `guided-view/src/test/kotlin/com/komgareader/guidedview/BorderLineSplitTest.kt`

Eine „Rahmenlinie" ist ein **dünnes, durchgehend dunkles** Zeilen-/Spaltenband, das einen großen Anteil der Region überspannt **und** von **helleren** Bereichen umgeben ist (so wird uniform-dunkle Art NICHT zerschnitten). `dark[y*width+x] == true` heißt Tinte/dunkel.

- [ ] **Step 1: Failing test** `BorderLineSplitTest.kt`:

```kotlin
package com.komgareader.guidedview

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BorderLineSplitTest {
    // dark-Maske bauen: '#'=dunkel, '.'=hell
    private fun mask(vararg rows: String): Triple<BooleanArray, Int, Int> {
        val h = rows.size; val w = rows[0].length
        val m = BooleanArray(w * h)
        for (y in 0 until h) for (x in 0 until w) m[y * w + x] = rows[y][x] == '#'
        return Triple(m, w, h)
    }
    private fun full(w: Int, h: Int) = PanelRect(0, 0, w, h)

    @Test
    fun `zwei durch dunkle Querlinie getrennte helle Felder ergeben zwei`() {
        // 10 breit, 9 hoch: helle Felder oben/unten, in der Mitte (y=4) eine volle dunkle Linie
        val rows = Array(9) { y -> if (y == 4) "##########" else ".........." }
        val (m, w, h) = mask(*rows)
        val out = BorderLineSplit.split(m, w, h, full(w, h), minPanel = 2)
        assertEquals(2, out.size)
        assertTrue(out.all { it.height in 3..5 })
    }

    @Test
    fun `kein klarer Strich ergibt unverändert ein Feld`() {
        // gleichmäßig helle Region, keine Linie
        val rows = Array(9) { "." .repeat(10) }
        val (m, w, h) = mask(*rows)
        assertEquals(1, BorderLineSplit.split(m, w, h, full(w, h), minPanel = 2).size)
    }

    @Test
    fun `uniform dunkle Region wird NICHT zersplittet`() {
        // alles dunkel (z.B. dunkle Splash-Art) -> keine Linie zwischen HELLEN Bereichen -> 1 Feld
        val rows = Array(9) { "#".repeat(10) }
        val (m, w, h) = mask(*rows)
        assertEquals(1, BorderLineSplit.split(m, w, h, full(w, h), minPanel = 2).size)
    }

    @Test
    fun `2x2 schwarz-umrandet ohne Weissgutter ergibt vier`() {
        // Kreuz aus dunkler Linie (x=5 Spalte, y=4 Zeile), Felder hell
        val rows = Array(9) { y ->
            buildString { for (x in 0 until 11) append(if (y == 4 || x == 5) '#' else '.') }
        }
        val (m, w, h) = mask(*rows)
        val out = BorderLineSplit.split(m, w, h, full(w, h), minPanel = 2)
        assertEquals(4, out.size)
    }
}
```

- [ ] **Step 2: Run, expect FAIL**

Run: `./gradlew :guided-view:test --tests "com.komgareader.guidedview.BorderLineSplitTest"`
Expected: FAIL — `BorderLineSplit` ungelöst.

- [ ] **Step 3: Implement** `BorderLineSplit.kt`:

```kotlin
package com.komgareader.guidedview

/**
 * Zerlegt eine Region rekursiv an dünnen, durchgehend dunklen, von helleren Bereichen
 * umgebenen achsenparallelen Rahmenlinien. Trennt schwarz-umrandete/eng liegende Panels,
 * die der Weiß-Gutter-Flood verschmilzt. Uniform-dunkle Art wird NICHT zersplittet
 * (eine Linie zählt nur, wenn die Nachbarbänder deutlich heller sind). Reines Kotlin.
 *
 * @param lineDarkFraction  Mindest-Dunkelanteil entlang der Linie (über die Regions-Spanne).
 * @param neighborMaxFraction  Max. Dunkelanteil der Nachbarbänder, damit die Linie als Grenze gilt.
 * @param maxLineThickness  Max. Dicke (px) eines Linienbands.
 * @param minPanel  Mindest-Sub-Panel-Größe (px) in der Schnitt-Achse.
 * @param maxDepth  Rekursionsgrenze.
 */
object BorderLineSplit {

    fun split(
        dark: BooleanArray, width: Int, height: Int, box: PanelRect,
        lineDarkFraction: Double = 0.7,
        neighborMaxFraction: Double = 0.4,
        maxLineThickness: Int = 24,
        minPanel: Int = 30,
        maxDepth: Int = 8,
    ): List<PanelRect> {
        if (maxDepth <= 0) return listOf(box)
        val cut = bestCut(dark, width, height, box, lineDarkFraction, neighborMaxFraction, maxLineThickness, minPanel)
            ?: return listOf(box)
        val (a, b) = cut
        return split(dark, width, height, a, lineDarkFraction, neighborMaxFraction, maxLineThickness, minPanel, maxDepth - 1) +
            split(dark, width, height, b, lineDarkFraction, neighborMaxFraction, maxLineThickness, minPanel, maxDepth - 1)
    }

    /** Bester Schnitt (H oder V) oder null. Liefert die zwei Teil-Boxen (ohne das Linienband). */
    private fun bestCut(
        dark: BooleanArray, width: Int, height: Int, box: PanelRect,
        lineDarkFraction: Double, neighborMaxFraction: Double, maxLineThickness: Int, minPanel: Int,
    ): Pair<PanelRect, PanelRect>? {
        val h = bestLine(box, minPanel, maxLineThickness, lineDarkFraction, neighborMaxFraction) { pos ->
            darkFractionRow(dark, width, height, box, pos)
        }
        val v = bestLine(box, minPanel, maxLineThickness, lineDarkFraction, neighborMaxFraction) { pos ->
            darkFractionCol(dark, width, height, box, pos)
        }
        // stärkeren Schnitt wählen (höherer Dunkelanteil)
        val pickH = when {
            h == null -> false
            v == null -> true
            else -> h.second >= v.second
        }
        return when {
            pickH && h != null -> {
                val (start, _, end) = Triple(h.first, 0, h.third)
                val top = PanelRect(box.x, box.y, box.width, start - box.y)
                val bot = PanelRect(box.x, end, box.width, box.y + box.height - end)
                if (top.height >= minPanel && bot.height >= minPanel) top to bot else null
            }
            v != null -> {
                val start = v.first; val end = v.third
                val left = PanelRect(box.x, box.y, start - box.x, box.height)
                val right = PanelRect(end, box.y, box.x + box.width - end, box.height)
                if (left.width >= minPanel && right.width >= minPanel) left to right else null
            }
            else -> null
        }
    }

    /**
     * Findet das beste interne Linienband entlang einer Achse. [fractionAt] gibt den Dunkelanteil
     * an Position pos (Zeile bzw. Spalte). Liefert (bandStart, score, bandEndExclusive) oder null.
     */
    private inline fun bestLine(
        box: PanelRect, minPanel: Int, maxLineThickness: Int,
        lineDarkFraction: Double, neighborMaxFraction: Double,
        axisLength: () -> Int = { 0 },
        fractionAt: (Int) -> Double,
    ): Triple<Int, Double, Int>? {
        // Achsenlänge implizit über box: für H = box.y..box.y+box.height, für V = box.x..box.x+box.width.
        // Der Aufrufer übergibt die passende fractionAt; die Grenzen bestimmen wir hier generisch
        // über minPanel-Rand. Da die Achse aus fractionAt nicht ableitbar ist, wird sie über die
        // box im Aufrufer gekapselt -> hier nutzen wir das Intervall [lo, hi).
        return null // ersetzt durch achsen-spezifische Implementierung unten
    }

    // --- achsen-spezifische Hilfen ---

    private fun darkFractionRow(dark: BooleanArray, width: Int, height: Int, box: PanelRect, y: Int): Double {
        if (y < 0 || y >= height) return 0.0
        var c = 0
        val x0 = box.x; val x1 = (box.x + box.width).coerceAtMost(width)
        for (x in x0 until x1) if (dark[y * width + x]) c++
        val span = (x1 - x0).coerceAtLeast(1)
        return c.toDouble() / span
    }

    private fun darkFractionCol(dark: BooleanArray, width: Int, height: Int, box: PanelRect, x: Int): Double {
        if (x < 0 || x >= width) return 0.0
        var c = 0
        val y0 = box.y; val y1 = (box.y + box.height).coerceAtMost(height)
        for (y in y0 until y1) if (dark[y * width + x]) c++
        val span = (y1 - y0).coerceAtLeast(1)
        return c.toDouble() / span
    }
}
```

> Hinweis Implementierung: Die generische `bestLine` oben ist **Platzhalter** — implementiere stattdessen zwei konkrete Funktionen `bestHLine(dark,width,height,box,...)` und `bestVLine(...)`, die jeweils:
> 1. das Intervall der inneren Positionen durchlaufen (`box.y+minPanel .. box.y+box.height-minPanel` bzw. x-analog),
> 2. zusammenhängende Bänder mit `fraction >= lineDarkFraction` und Dicke `<= maxLineThickness` finden,
> 3. prüfen, dass die Positionen unmittelbar **vor** dem Band und **nach** dem Band `fraction <= neighborMaxFraction` haben (heller — also echte Grenze, keine dunkle Art),
> 4. das Band mit dem höchsten mittleren Dunkelanteil als (start, score, endExclusive) zurückgeben, sonst null.
>
> Die Tests in Step 1 definieren das Sollverhalten; implementiere `bestHLine`/`bestVLine` so, dass alle vier Tests grün werden. Entferne die Platzhalter-`bestLine`.

- [ ] **Step 4: Run, expect PASS** (4 Tests)

Run: `./gradlew :guided-view:test --tests "com.komgareader.guidedview.BorderLineSplitTest"`
Falls ein Test rot bleibt: NICHT die Tests aufweichen — den Algorithmus (Schwellen/Band-Logik) korrigieren bis korrekt.

- [ ] **Step 5: Commit**

```bash
git add guided-view/src/main/kotlin/com/komgareader/guidedview/BorderLineSplit.kt guided-view/src/test/kotlin/com/komgareader/guidedview/BorderLineSplitTest.kt
git commit -m "feat(guided-view): BorderLineSplit (Schnitt an achsenparallelen Rahmenlinien)"
```

---

## Task 2: PanelDetector v2 — Split integrieren + Bubble-Filter (TDD)

**Files:**
- Modify: `guided-view/src/main/kotlin/com/komgareader/guidedview/PanelDetector.kt`
- Modify: `guided-view/src/test/kotlin/com/komgareader/guidedview/PanelDetectorTest.kt` (Tests anhängen)

`PanelDetector` baut bereits `background` (Otsu) und `flooded`; die Dunkel-Maske ist `!background[i]`. Nach `RegionLabeling` + Min-Fläche-Filter: jede Box, die seitenbreit/-hoch ist (z. B. Breite > 0.6*pageW UND Höhe > 0.5*pageH, oder sehr breit), durch `BorderLineSplit.split(darkMask, pageW, pageH, box)` ersetzen. Danach Bubble-Filter + Contained-Merge, dann Lesereihenfolge.

- [ ] **Step 1: Failing tests** an `PanelDetectorTest` anhängen (nutzt `SyntheticPage`):

```kotlin
    @Test
    fun `schwarz-umrandetes 2x2 ohne Weissgutter ergibt 4 Panels`() {
        // 1000x800: 2x2 Panels, getrennt NUR durch dunkle Rahmenlinien (kein Weißgutter)
        val px = IntArray(1000 * 800) { 0xFFFFFFFF.toInt() }
        fun vline(x: Int) { for (y in 0 until 800) for (dx in 0..5) px[y * 1000 + (x + dx)] = 0xFF101010.toInt() }
        fun hline(y: Int) { for (x in 0 until 1000) for (dy in 0..5) px[(y + dy) * 1000 + x] = 0xFF101010.toInt() }
        // Rahmen außen + Kreuz innen
        hline(10); hline(395); hline(784); vline(10); vline(495); vline(984)
        // etwas Inhalt in jedes Feld (mittelgrau), damit Felder nicht „leer“ sind
        for (q in 0..1) for (r in 0..1) {
            val ox = 60 + r * 485; val oy = 60 + q * 385
            for (y in oy until oy + 250) for (x in ox until ox + 350) px[y * 1000 + x] = 0xFF808080.toInt()
        }
        val page = com.komgareader.domain.render.RenderedPage(1000, 800, px)
        val out = PanelDetector().detect(page, ReadingDirection.LEFT_TO_RIGHT)
        assertEquals(4, out.size, "Erwarte 4 Panels (Rahmen-Split), war ${out.size}")
    }

    @Test
    fun `Sprechblase wird nicht als eigenes Panel gezählt`() {
        // Ein großes Panel mit dunklem Inhalt + eine kleine weiße Insel mit „Text" (dunkle Punkte)
        val panel = PanelRect(50, 50, 900, 700)
        val px = IntArray(1000 * 800) { 0xFFFFFFFF.toInt() }
        for (y in panel.y until panel.y + panel.height) for (x in panel.x until panel.x + panel.width)
            px[y * 1000 + x] = 0xFF303030.toInt() // dunkler Panel-Inhalt
        // weiße Blase
        for (y in 300 until 450) for (x in 400 until 600) px[y * 1000 + x] = 0xFFFFFFFF.toInt()
        // „Text" in der Blase
        for (y in 360 until 380) for (x in 440 until 560) px[y * 1000 + x] = 0xFF101010.toInt()
        val page = com.komgareader.domain.render.RenderedPage(1000, 800, px)
        val out = PanelDetector().detect(page, ReadingDirection.LEFT_TO_RIGHT)
        assertEquals(1, out.size, "Blase darf kein eigenes Panel sein, war ${out.size}")
    }
```

(Die bestehenden Tests — sauberes 3×2-Raster, Full-Bleed, Blank, Einzelpanel, verschmutzte Gasse, Otsu grau — müssen grün bleiben.)

- [ ] **Step 2: Run, expect FAIL** (neue Tests rot)

Run: `./gradlew :guided-view:test --tests "com.komgareader.guidedview.PanelDetectorTest"`

- [ ] **Step 3: PanelDetector erweitern**

In `detect()` nach dem Min-Fläche-Filter (`filtered`):

```kotlin
val darkMask = BooleanArray(background.size) { !background[it] }
val splitPageW = page.width; val splitPageH = page.height
val expanded = filtered.flatMap { box ->
    val wide = box.width > splitPageW * 0.6 && box.height > splitPageH * 0.5
    val bandlike = box.width > splitPageW * 0.85 || box.height > splitPageH * 0.85
    if (wide || bandlike)
        BorderLineSplit.split(darkMask, splitPageW, splitPageH, box)
    else listOf(box)
}
val minArea2 = page.width.toLong() * page.height * minPanelAreaFraction
val sized = expanded.filter { it.width.toLong() * it.height >= minArea2 }
val deBubbled = dropContainedSmall(sized, page.width, page.height)
val merged = dropContained(deBubbled)
return ReadingOrder.sort(merged, direction)
```

Bubble-Filter `dropContainedSmall` ergänzen (verwirft kleine Boxen, die vollständig in einer größeren liegen — Sprechblasen):

```kotlin
/** Verwirft kleine Boxen (< [smallAreaFraction] Seitenfläche), die vollständig in einer größeren liegen (Sprechblasen). */
private fun dropContainedSmall(
    boxes: List<PanelRect>, pageW: Int, pageH: Int, smallAreaFraction: Double = 0.06,
): List<PanelRect> {
    val pageArea = pageW.toLong() * pageH
    return boxes.filterNot { b ->
        val small = b.width.toLong() * b.height < pageArea * smallAreaFraction
        small && boxes.any { o ->
            o !== b && o.width.toLong() * o.height > b.width.toLong() * b.height &&
                b.x >= o.x && b.y >= o.y && b.x + b.width <= o.x + o.width && b.y + b.height <= o.y + o.height
        }
    }
}
```

> Konstanten (0.6/0.5/0.85, 0.06) sind Startwerte; in Task 3 (300-Validierung) nachjustieren, falls über-/unter-segmentiert.

- [ ] **Step 4: Run, expect PASS** (alle PanelDetectorTest grün, inkl. 2 neue + bestehende)

Run: `./gradlew :guided-view:test`
Erwartung: BUILD SUCCESSFUL (alle guided-view-Tests). Falls bestehende Tests brechen → Split/Filter-Schwellen so justieren, dass saubere Raster/Full-Bleed/Einzelpanel weiterhin korrekt sind (kein Test aufweichen).

- [ ] **Step 5: Commit**

```bash
git add guided-view/src/main/kotlin/com/komgareader/guidedview/PanelDetector.kt guided-view/src/test/kotlin/com/komgareader/guidedview/PanelDetectorTest.kt
git commit -m "feat(guided-view): PanelDetector v2 — Rahmen-Linien-Split + Bubble-Filter"
```

---

## Task 3: 300-Seiten-Validierung + Tuning + Skill (Vorher/Nachher)

**Files:** keine Produktionsänderung außer ggf. Schwellen-Tuning in `BorderLineSplit`/`PanelDetector`; `.claude/skills/komga-guided-comic-reader/SKILL.md`.

- [ ] **Step 1: Harness gegen die 300er-Stichprobe laufen lassen** (Korpus liegt schon in `guided-view/realpages/sample300/`):

```bash
sed -i 's/@Disabled("Manueller Harness — lokal aktivieren")//' guided-view/src/test/kotlin/com/komgareader/guidedview/RealPageHarness.kt
./gradlew :guided-view:test --tests "com.komgareader.guidedview.RealPageHarness" --rerun-tasks -i > /tmp/v2.out 2>&1
git checkout guided-view/src/test/kotlin/com/komgareader/guidedview/RealPageHarness.kt
grep -oE ': [0-9]+ Panels' /tmp/v2.out | grep -oE '[0-9]+' | sort -n | uniq -c
```
Verteilung mit der Baseline (41 % Fallback, Ausreißer 24) vergleichen. **Ziel:** Fallback-Anteil deutlich gesunken auf echten Mehr-Panel-Seiten, keine 20+-Ausreißer mehr.

- [ ] **Step 2: Overlays sichten** (Stichprobe, inkl. schwarz-umrandete + Blasen-Seiten) — Overlays in `guided-view/realpages/_out/`. Per Read-Tool ~10 Seiten prüfen: Boxen liegen plausibel auf Panels, keine Blasen-Box, keine Querbänder mehr. Bei systematischem Problem → Schwellen in `BorderLineSplit`/`dropContainedSmall` nachziehen, Task 2 erneut.

- [ ] **Step 3: Skill aktualisieren** — in `.claude/skills/komga-guided-comic-reader/SKILL.md` den Detektor-Absatz um die **Rahmen-Linien-Split-Stufe** (schwarz-umrandete Panels) und den **Bubble-Filter** (kleine enthaltene Box = keine Panel) ergänzen.

- [ ] **Step 4: Voll-Verifikation**

```bash
./gradlew :guided-view:test :domain:test :app:assembleDebug
```
Erwartung: BUILD SUCCESSFUL, alle Unit-Tests grün.

- [ ] **Step 5: Commit**

```bash
git add .claude/skills/komga-guided-comic-reader/SKILL.md guided-view/src/main/kotlin/com/komgareader/guidedview/
git commit -m "test+docs(guided-view): 300-Seiten-Validierung v2, Skill-Update, Schwellen-Tuning"
```

---

## Danach (außerhalb dieses Plans, vom Controller gesteuert)

- On-Device-Test (Emulator: Test-Komga + COMIC-Regal): Comic über Stöbern → Panel-Tap → Zoom-Screenshot.
- Merge nach master (eink-color-filter + Settings-Master-Detail integrieren, Farbfilter auf Comic-Seiten).
