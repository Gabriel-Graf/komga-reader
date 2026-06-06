# Phase 2e — Guided View (Panel-Erkennung)

> REQUIRED SUB-SKILL: subagent-driven-development.

**Goal:** Den Kern von „Guided View" liefern: eine Comic-Seite in **Panels** segmentieren und in Lese-Reihenfolge zurückgeben. Damit kann der Reader später Panel-für-Panel zoomen (Tap → nächstes Panel im Vollbild).

**Architecture-Entscheidung:** Statt OpenCV (NDK-schwer, gerätegebunden) ein **reiner Kotlin-Algorithmus** (Projection-Profile / rekursiver XY-Cut) über die `RenderedPage`-Pixel (ARGB-`IntArray`, bereits Android-frei). Voll host-testbar mit synthetischen Seiten bekannter Panel-Anordnung — kein Gerät, kein OpenCV. Neues JVM-Modul `:guided-view` (Dep `:domain`).

**Tech:** Kotlin/JVM · JUnit5.

## Algorithmus (rekursiver XY-Cut)
1. „Dunkel" = Pixel-Helligkeit `(r+g+b)/3 < darkThreshold` (z.B. 128).
2. Zeilen-/Spalten-Profil: Anteil dunkler Pixel pro Zeile/Spalte.
3. „Gutter" = zusammenhängendes Band aus Zeilen/Spalten mit Dunkel-Anteil `< gutterMaxInk` (fast leer), Mindestbreite `minGutter`.
4. Rekursiv: zuerst horizontale Gutter (→ Zeilenbänder), in jedem Band vertikale Gutter (→ Panels), wechselweise, bis keine Teilung mehr. Ränder (Whitespace) trimmen.
5. Reihenfolge: Bänder oben→unten; innerhalb eines Bands je nach `ReadingDirection` links→rechts (Comic) oder rechts→links (Manga).

---

### Task 0: Modul `:guided-view`

**Files:** `settings.gradle.kts` (`include(":guided-view")`), `guided-view/build.gradle.kts`.

- [ ] `kotlin("jvm")`, Dep `implementation(project(":domain"))`, test `kotlin("test")` + junit-jupiter; `useJUnitPlatform()`, `jvmToolchain(21)`.
- [ ] `./gradlew :guided-view:build` → SUCCESSFUL. Commit: `build: Modul :guided-view`.

---

### Task 1: PanelDetector (TDD)

**Files:** `guided-view/.../Panel.kt`, `PanelDetector.kt`; test `PanelDetectorTest.kt`.

- [ ] Typen:
```kotlin
package com.komgareader.guidedview
data class PanelRect(val x: Int, val y: Int, val width: Int, val height: Int)
enum class ReadingDirection { LEFT_TO_RIGHT, RIGHT_TO_LEFT }
```
- [ ] **TDD — Test-Helper** baut eine `RenderedPage` (weiß = 0xFFFFFFFF) und füllt gegebene Rects dunkel (0xFF000000):
```kotlin
private fun page(w: Int, h: Int, panels: List<PanelRect>): RenderedPage {
    val px = IntArray(w * h) { 0xFFFFFFFF.toInt() }
    for (p in panels) for (y in p.y until p.y + p.height) for (x in p.x until p.x + p.width)
        px[y * w + x] = 0xFF000000.toInt()
    return RenderedPage(w, h, px)
}
```
- [ ] **Tests (Verhalten):**
  1. **Eine ganzseitige Fläche** → genau 1 Panel, ~Vollseite.
  2. **2×2-Raster** (4 dunkle Blöcke mit weißen Guttern dazwischen) → 4 Panels; Reihenfolge LEFT_TO_RIGHT: oben-links, oben-rechts, unten-links, unten-rechts. Prüfe die x/y-Reihenfolge der Mittelpunkte.
  3. **2×2-Raster, RIGHT_TO_LEFT** → oben-rechts vor oben-links.
  4. **3 horizontale Streifen** (übereinander) → 3 Panels von oben nach unten.
  5. **Leere/weiße Seite** → leere Liste (oder 1 Panel = ganze Seite; entscheide und teste konsistent — empfohlen: leere Liste bei <`minInk` Gesamt-Tinte).
  Baue die Rects mit klaren Guttern (z.B. 600×800-Seite, 40px Gutter), sodass die Erkennung eindeutig ist.
- [ ] `PanelDetector(darkThreshold=128, gutterMaxInk=0.02, minGutter=12, minPanel=20)` mit `fun detect(page: RenderedPage, direction: ReadingDirection): List<PanelRect>`. Implementiere den rekursiven XY-Cut. → GREEN.
- [ ] Commit: `feat(guided-view): PanelDetector (XY-Cut, TDD)`.

---

### Task 2: E2E mit „realistischer" Multi-Panel-Seite

**Files:** `PanelDetectorE2ETest.kt`.

- [ ] Baue eine Seite mit gemischtem Layout (z.B. oben ein breites Panel über volle Breite, darunter zwei nebeneinander) → erwarte 3 Panels in korrekter Lesereihenfolge (oben, dann unten-links, unten-rechts bei LTR). `./gradlew :guided-view:test` → alle grün. Commit: `test(guided-view): E2E Multi-Panel-Layout`.

---

## Self-Review
- **Spec §11 P2 Guided-View:** Panel-Segmentierung als testbarer Kern → Tasks 1,2. Bewusst pure-Kotlin statt OpenCV (einfacher, testbar, kein NDK) — dokumentiert.
- **Verschoben:** UI-Integration (Tap→Panel-Vollbild-Zoom im PagedReader), Bleed-/überlappende-Panels (XY-Cut deckt rechteckige Gutter-Layouts ab; komplexe Layouts → Fallback ganze Seite), Webtoon (braucht keine Panels).
- **Abnahme:** `:guided-view:test` grün — Panel-Erkennung für 1-Panel, Raster (LTR/RTL), Streifen, leer, gemischt.
