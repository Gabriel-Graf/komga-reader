# Farbfilter Phase 2 — Pixel-Pipeline Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reader-Seiten (alle 3 Viewer) durch eine pixelbasierte Filter-Pipeline (linear → Levels → Gamma → Unsharp-Mask → Dithering) schicken; Bibliotheks-Cover bleiben auf der billigen GPU-Matrix.

**Architecture:** Reiner Pixel-Kernel `applyPixelPipeline` in `domain/color` (kein Android-Import, in-place auf ARGB-`IntArray`). Andockung im `app`-Modul über (a) eine Coil-`Transformation` für Coil-Seiten (Paged/Webtoon) und (b) einen Bitmap-Wrapper für MuPDF-Seiten (EPUB). Ein neuer CompositionLocal `LocalColorProfile` reicht das aktive Profil an die Reader-Wrapper. `ColorProfile` wächst um neutrale Phase-2-Felder; Room v7→v8 ergänzt Spalten + ein Demo-Built-in. Cover-Pfad und Stöbern bleiben unverändert (nur lineare GPU-Matrix).

**Tech Stack:** Kotlin, Jetpack Compose, Coil 2.7.0 (`coil.transform.Transformation`), Room, Hilt, kotlin.test/JUnit4.

**Spec:** `docs/superpowers/specs/2026-06-07-eink-color-filter-phase2-design.md`

**Wichtige Konvention im Projekt:** Bash-Befehle als Oneliner (keine Newlines). Echte Umlaute/ß in allen deutschen Texten. Tests laufen mit `./gradlew :modul:test` (JVM-Unit) bzw. `:data:connectedDebugAndroidTest` (Room-Migration, Emulator nötig). E-Ink-Designsprache (`komga-eink-ui`-Skill) bei UI-Arbeit befolgen.

---

## Datei-Struktur (Verantwortlichkeiten)

**Neu:**
- `domain/src/main/kotlin/com/komgareader/domain/model/DitherMode.kt` — Enum.
- `domain/src/main/kotlin/com/komgareader/domain/color/PixelPipeline.kt` — `applyPixelPipeline` + `buildGammaLut` (pure Kotlin).
- `domain/src/test/kotlin/com/komgareader/domain/color/PixelPipelineTest.kt` — Kernel-Tests.
- `app/src/main/kotlin/com/komgareader/app/color/ColorPipelineTransformation.kt` — Coil-Transformation, wendet Kernel auf dekodiertes Bitmap an.
- `app/src/main/kotlin/com/komgareader/app/ui/components/FilteredReaderImage.kt` — `FilteredReaderAsyncImage` + `FilteredReaderImage` + `LocalColorProfile`.

**Geändert:**
- `domain/.../model/ColorProfile.kt` — neue Felder + `isLinearNeutral`/`needsPixelPipeline`.
- `app/.../ui/components/FilteredImage.kt` — `toColorFilterOrNull` auf `isLinearNeutral` umstellen.
- `data/.../db/Entities.kt` — `ColorProfileEntity` neue Spalten.
- `data/.../db/AppDatabase.kt` — v7→v8, `MIGRATION_7_8`, `seedColorProfiles` erweitert.
- `data/.../di/DataModule.kt` — Migration registrieren.
- `data/.../repository/RoomColorProfileRepository.kt` — Mapper neue Felder.
- `app/.../MainActivity.kt` — `LocalColorProfile` providen.
- `app/.../ui/reader/PagedReaderScreen.kt`, `WebtoonReaderScreen.kt`, `EpubReaderScreen.kt` — Reader-Wrapper nutzen.
- `app/.../ui/settings/ColorFilterViewModel.kt` — `EditState` + Update-/Save-Funktionen.
- `app/.../ui/settings/ColorFilterSettingsContent.kt` — „Erweitert"-Sektion + Dither + Vorschau auf volle Pipeline.
- `app/.../i18n/Strings.kt` — neue Keys (DE+EN).
- `data/src/androidTest/.../ColorProfileSeedTest.kt` — Seed-Erwartungen.

---

## Task 1: Domain — DitherMode-Enum

**Files:**
- Create: `domain/src/main/kotlin/com/komgareader/domain/model/DitherMode.kt`

- [ ] **Step 1: Enum anlegen**

```kotlin
package com.komgareader.domain.model

/** Dithering-Verfahren für die Reader-Pixel-Pipeline. NONE = aus (neutral). */
enum class DitherMode { NONE, FLOYD_STEINBERG, ORDERED }
```

- [ ] **Step 2: Build prüfen**

Run: `./gradlew :domain:compileKotlin -q`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add domain/src/main/kotlin/com/komgareader/domain/model/DitherMode.kt && git commit -m "feat(domain): DitherMode-Enum für Phase-2-Pipeline"
```

---

## Task 2: Domain — ColorProfile um Phase-2-Felder erweitern

**Files:**
- Modify: `domain/src/main/kotlin/com/komgareader/domain/model/ColorProfile.kt`
- Test: `domain/src/test/kotlin/com/komgareader/domain/model/ColorProfileTest.kt` (Create)

- [ ] **Step 1: Failing test schreiben**

Create `domain/src/test/kotlin/com/komgareader/domain/model/ColorProfileTest.kt`:

```kotlin
package com.komgareader.domain.model

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ColorProfileTest {

    private fun base() = ColorProfile(
        id = 9, name = "T", saturation = 1f, contrast = 1f, brightness = 0f, builtIn = false,
    )

    @Test
    fun `neutrales Profil ist isNeutral und braucht keine Pixel-Pipeline`() {
        val p = base()
        assertTrue(p.isLinearNeutral)
        assertFalse(p.needsPixelPipeline)
        assertTrue(p.isNeutral)
    }

    @Test
    fun `nur lineare Werte gesetzt braucht keine Pixel-Pipeline`() {
        val p = base().copy(saturation = 1.4f)
        assertFalse(p.isLinearNeutral)
        assertFalse(p.needsPixelPipeline)
        assertFalse(p.isNeutral)
    }

    @Test
    fun `Gamma ungleich 1 braucht die Pixel-Pipeline`() {
        assertTrue(base().copy(gamma = 1.2f).needsPixelPipeline)
    }

    @Test
    fun `Levels Unsharp und Dither lösen die Pixel-Pipeline aus`() {
        assertTrue(base().copy(blackPoint = 0.05f).needsPixelPipeline)
        assertTrue(base().copy(whitePoint = 0.9f).needsPixelPipeline)
        assertTrue(base().copy(sharpenAmount = 0.5f).needsPixelPipeline)
        assertTrue(base().copy(ditherMode = DitherMode.FLOYD_STEINBERG).needsPixelPipeline)
    }
}
```

- [ ] **Step 2: Test auf Rot laufen**

Run: `./gradlew :domain:test --tests "com.komgareader.domain.model.ColorProfileTest" -q`
Expected: FAIL — Kompilierfehler (`gamma`, `blackPoint`, `needsPixelPipeline` etc. existieren nicht).

- [ ] **Step 3: ColorProfile erweitern**

Ersetze den kompletten Inhalt von `domain/src/main/kotlin/com/komgareader/domain/model/ColorProfile.kt`:

```kotlin
package com.komgareader.domain.model

/**
 * Ein benanntes E-Ink-Farbfilter-Profil. Quellen-/geräteneutral: nur Zahlen.
 * Lineare Felder (saturation/contrast/brightness) werden zur GPU-ColorMatrix
 * (siehe [com.komgareader.domain.color.buildColorMatrix]) — auf Cover UND Reader-Seiten.
 * Phase-2-Felder (Levels/Gamma/Unsharp/Dither) laufen nur beim Lesen durch den
 * Pixel-Kernel (siehe [com.komgareader.domain.color.applyPixelPipeline]).
 *
 * @param saturation 1.0 = neutral, 0.0 = Graustufen, >1 = kräftiger.
 * @param contrast   1.0 = neutral; skaliert um den Mittelwert.
 * @param brightness 0.0 = neutral; linearer Offset.
 * @param blackPoint 0.0 = neutral; Tonwert-Eingang Schwarzpunkt (0.0..0.4).
 * @param whitePoint 1.0 = neutral; Tonwert-Eingang Weißpunkt (0.6..1.0).
 * @param gamma      1.0 = neutral; >1 hebt Mitteltöne.
 * @param sharpenAmount 0.0 = neutral; Stärke der Unsharp-Mask.
 * @param sharpenRadius  Box-Blur-Radius in px (1..3).
 * @param ditherMode  NONE = aus.
 * @param ditherLevels Stufen pro Kanal (2..64), nur wirksam bei ditherMode != NONE.
 * @param builtIn    mitgeliefert → nicht editier-/löschbar.
 */
data class ColorProfile(
    val id: Long,
    val name: String,
    val saturation: Float,
    val contrast: Float,
    val brightness: Float,
    val blackPoint: Float = 0f,
    val whitePoint: Float = 1f,
    val gamma: Float = 1f,
    val sharpenAmount: Float = 0f,
    val sharpenRadius: Int = 1,
    val ditherMode: DitherMode = DitherMode.NONE,
    val ditherLevels: Int = 16,
    val builtIn: Boolean,
) {
    /** True, wenn die linearen Werte nichts verändern (GPU-Matrix-Pfad, Cover). */
    val isLinearNeutral: Boolean get() = saturation == 1f && contrast == 1f && brightness == 0f

    /** True, wenn mindestens eine Phase-2-Operation gesetzt ist (Reader-Pixel-Kernel nötig). */
    val needsPixelPipeline: Boolean get() =
        blackPoint > 0f || whitePoint < 1f || gamma != 1f ||
            sharpenAmount > 0f || ditherMode != DitherMode.NONE

    /** True, wenn das Profil gar nichts verändert (kein Filter nötig). */
    val isNeutral: Boolean get() = isLinearNeutral && !needsPixelPipeline

    companion object {
        /** Fallback, wenn kein aktives Profil existiert: kein Filter. */
        val OFF = ColorProfile(id = 1L, name = "Aus", saturation = 1f, contrast = 1f, brightness = 0f, builtIn = true)
    }
}
```

- [ ] **Step 4: Test auf Grün laufen**

Run: `./gradlew :domain:test --tests "com.komgareader.domain.model.ColorProfileTest" -q`
Expected: PASS (4 Tests).

- [ ] **Step 5: Commit**

```bash
git add domain/src/main/kotlin/com/komgareader/domain/model/ColorProfile.kt domain/src/test/kotlin/com/komgareader/domain/model/ColorProfileTest.kt && git commit -m "feat(domain): ColorProfile um Phase-2-Felder + needsPixelPipeline"
```

---

## Task 3: Domain — buildGammaLut

**Files:**
- Create: `domain/src/main/kotlin/com/komgareader/domain/color/PixelPipeline.kt`
- Test: `domain/src/test/kotlin/com/komgareader/domain/color/PixelPipelineTest.kt` (Create)

- [ ] **Step 1: Failing test schreiben**

Create `domain/src/test/kotlin/com/komgareader/domain/color/PixelPipelineTest.kt`:

```kotlin
package com.komgareader.domain.color

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PixelPipelineTest {

    @Test
    fun `Gamma 1 ergibt die Identitäts-LUT`() {
        val lut = buildGammaLut(1f)
        assertEquals(256, lut.size)
        for (i in 0..255) assertEquals(i, lut[i], "Index $i")
    }

    @Test
    fun `Gamma-LUT fixiert Endpunkte`() {
        val lut = buildGammaLut(2.2f)
        assertEquals(0, lut[0])
        assertEquals(255, lut[255])
    }

    @Test
    fun `Gamma größer 1 hebt die Mitteltöne`() {
        // out = 255 * (in/255)^(1/gamma); gamma>1 => 1/gamma<1 => Mittelton steigt.
        val lut = buildGammaLut(2.2f)
        assertTrue(lut[128] > 128, "lut[128]=${lut[128]} sollte > 128 sein")
    }
}
```

- [ ] **Step 2: Test auf Rot laufen**

Run: `./gradlew :domain:test --tests "com.komgareader.domain.color.PixelPipelineTest" -q`
Expected: FAIL — `buildGammaLut` nicht gefunden.

- [ ] **Step 3: buildGammaLut implementieren**

Create `domain/src/main/kotlin/com/komgareader/domain/color/PixelPipeline.kt`:

```kotlin
package com.komgareader.domain.color

import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * 256-Eintrag-Lookup für Gamma-Korrektur: out = 255 * (in/255)^(1/gamma).
 * gamma == 1 → Identität. gamma > 1 hebt Mitteltöne (Kaleido wirkt flau/dunkel).
 */
fun buildGammaLut(gamma: Float): IntArray {
    val lut = IntArray(256)
    if (gamma == 1f) {
        for (i in 0..255) lut[i] = i
        return lut
    }
    val invGamma = 1.0 / gamma
    for (i in 0..255) {
        lut[i] = (255.0 * (i / 255.0).pow(invGamma)).roundToInt().coerceIn(0, 255)
    }
    return lut
}
```

- [ ] **Step 4: Test auf Grün laufen**

Run: `./gradlew :domain:test --tests "com.komgareader.domain.color.PixelPipelineTest" -q`
Expected: PASS (3 Tests).

- [ ] **Step 5: Commit**

```bash
git add domain/src/main/kotlin/com/komgareader/domain/color/PixelPipeline.kt domain/src/test/kotlin/com/komgareader/domain/color/PixelPipelineTest.kt && git commit -m "feat(domain): buildGammaLut (256-Eintrag-Gamma-LUT)"
```

---

## Task 4: Domain — applyPixelPipeline: Tonal-Stufen (linear + Levels + Gamma)

**Files:**
- Modify: `domain/src/main/kotlin/com/komgareader/domain/color/PixelPipeline.kt`
- Modify: `domain/src/test/kotlin/com/komgareader/domain/color/PixelPipelineTest.kt`

Hintergrund: ARGB-Pixel sind Ints im Format `(A<<24)|(R<<16)|(G<<8)|B`. Der Kernel arbeitet in-place. Neutrale Stufen werden übersprungen.

- [ ] **Step 1: Failing tests ergänzen**

Hänge an `PixelPipelineTest.kt` (innerhalb der Klasse) an:

```kotlin
    private fun argb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    private fun r(p: Int) = (p shr 16) and 0xFF
    private fun g(p: Int) = (p shr 8) and 0xFF
    private fun b(p: Int) = p and 0xFF

    private fun profile(
        saturation: Float = 1f, contrast: Float = 1f, brightness: Float = 0f,
        blackPoint: Float = 0f, whitePoint: Float = 1f, gamma: Float = 1f,
        sharpenAmount: Float = 0f, sharpenRadius: Int = 1,
        ditherMode: com.komgareader.domain.model.DitherMode = com.komgareader.domain.model.DitherMode.NONE,
        ditherLevels: Int = 16,
    ) = com.komgareader.domain.model.ColorProfile(
        id = 1, name = "p", saturation = saturation, contrast = contrast, brightness = brightness,
        blackPoint = blackPoint, whitePoint = whitePoint, gamma = gamma,
        sharpenAmount = sharpenAmount, sharpenRadius = sharpenRadius,
        ditherMode = ditherMode, ditherLevels = ditherLevels, builtIn = false,
    )

    @Test
    fun `neutrales Profil lässt Pixel unverändert`() {
        val px = intArrayOf(argb(10, 128, 240), argb(0, 0, 0), argb(255, 255, 255))
        val copy = px.copyOf()
        applyPixelPipeline(px, width = 3, height = 1, profile = profile())
        assertTrue(px.contentEquals(copy))
    }

    @Test
    fun `Levels clippt unter Schwarzpunkt auf 0 und über Weißpunkt auf 255`() {
        // blackPoint 0.2 => 51, whitePoint 0.8 => 204. Werte darunter/darüber clippen.
        val px = intArrayOf(argb(40, 128, 230))
        applyPixelPipeline(px, 1, 1, profile(blackPoint = 0.2f, whitePoint = 0.8f))
        assertEquals(0, r(px[0]), "40 < 51 => 0")
        assertEquals(255, b(px[0]), "230 > 204 => 255")
        // Mittelwert 128 → (128-51)/(204-51)*255 ≈ 128
        assertTrue(g(px[0]) in 120..136, "g=${g(px[0])}")
    }

    @Test
    fun `linearer Teil entspricht buildColorMatrix`() {
        val p = profile(saturation = 1.4f, contrast = 1.15f, brightness = 0.05f)
        val m = buildColorMatrix(1.4f, 1.15f, 0.05f)
        val rIn = 90; val gIn = 130; val bIn = 200
        val px = intArrayOf(argb(rIn, gIn, bIn))
        applyPixelPipeline(px, 1, 1, p)
        val expR = (m[0] * rIn + m[1] * gIn + m[2] * bIn + m[4]).toInt().coerceIn(0, 255)
        assertEquals(expR, r(px[0]), "R via Matrix")
    }

    @Test
    fun `Gamma hebt einen mittelgrauen Pixel`() {
        val px = intArrayOf(argb(128, 128, 128))
        applyPixelPipeline(px, 1, 1, profile(gamma = 2.2f))
        assertTrue(r(px[0]) > 128 && g(px[0]) > 128 && b(px[0]) > 128)
    }
```

- [ ] **Step 2: Test auf Rot laufen**

Run: `./gradlew :domain:test --tests "com.komgareader.domain.color.PixelPipelineTest" -q`
Expected: FAIL — `applyPixelPipeline` nicht gefunden.

- [ ] **Step 3: applyPixelPipeline (Tonal-Stufen) implementieren**

Ergänze in `PixelPipeline.kt` (Import oben + Funktionen):

```kotlin
import com.komgareader.domain.model.ColorProfile
```

```kotlin
/**
 * Verarbeitet ARGB-Pixel ([px], Format (A<<24)|(R<<16)|(G<<8)|B) in-place mit der vollen
 * Filter-Pipeline: linear (Sat→Kontrast→Helligkeit) → Levels → Gamma → Unsharp-Mask →
 * Dithering. Neutrale Stufen werden übersprungen. Pure Kotlin, ohne Android.
 */
fun applyPixelPipeline(px: IntArray, width: Int, height: Int, profile: ColorProfile) {
    if (!profile.isLinearNeutral) applyLinear(px, profile.saturation, profile.contrast, profile.brightness)
    if (profile.blackPoint > 0f || profile.whitePoint < 1f || profile.gamma != 1f) {
        applyToneLut(px, buildToneLut(profile.blackPoint, profile.whitePoint, profile.gamma))
    }
}

/** Levels (Schwarz-/Weißpunkt-Streckung) gefolgt von Gamma, als kombinierte 256-LUT. */
internal fun buildToneLut(blackPoint: Float, whitePoint: Float, gamma: Float): IntArray {
    val bp = (blackPoint * 255f)
    val wp = (whitePoint * 255f)
    val span = (wp - bp).coerceAtLeast(1f)
    val gammaLut = buildGammaLut(gamma)
    val lut = IntArray(256)
    for (i in 0..255) {
        val leveled = (((i - bp) / span) * 255f).roundToInt().coerceIn(0, 255)
        lut[i] = gammaLut[leveled]
    }
    return lut
}

private fun applyToneLut(px: IntArray, lut: IntArray) {
    for (idx in px.indices) {
        val p = px[idx]
        val a = p and -0x1000000 // 0xFF000000
        val r = lut[(p shr 16) and 0xFF]
        val g = lut[(p shr 8) and 0xFF]
        val b = lut[p and 0xFF]
        px[idx] = a or (r shl 16) or (g shl 8) or b
    }
}

/** Wendet die lineare ColorMatrix (siehe [buildColorMatrix]) pro Pixel an, geklemmt 0..255. */
private fun applyLinear(px: IntArray, saturation: Float, contrast: Float, brightness: Float) {
    val m = buildColorMatrix(saturation, contrast, brightness)
    for (idx in px.indices) {
        val p = px[idx]
        val a = p and -0x1000000
        val rIn = (p shr 16) and 0xFF
        val gIn = (p shr 8) and 0xFF
        val bIn = p and 0xFF
        val r = (m[0] * rIn + m[1] * gIn + m[2] * bIn + m[4]).roundToInt().coerceIn(0, 255)
        val g = (m[5] * rIn + m[6] * gIn + m[7] * bIn + m[9]).roundToInt().coerceIn(0, 255)
        val b = (m[10] * rIn + m[11] * gIn + m[12] * bIn + m[14]).roundToInt().coerceIn(0, 255)
        px[idx] = a or (r shl 16) or (g shl 8) or b
    }
}
```

Hinweis: `buildColorMatrix` liegt im selben Package (`com.komgareader.domain.color`) — kein Import nötig.

- [ ] **Step 4: Test auf Grün laufen**

Run: `./gradlew :domain:test --tests "com.komgareader.domain.color.PixelPipelineTest" -q`
Expected: PASS (alle bisherigen + 4 neue).

- [ ] **Step 5: Commit**

```bash
git add domain/src/main/kotlin/com/komgareader/domain/color/PixelPipeline.kt domain/src/test/kotlin/com/komgareader/domain/color/PixelPipelineTest.kt && git commit -m "feat(domain): Pixel-Pipeline Tonal-Stufen (linear + Levels + Gamma)"
```

---

## Task 5: Domain — Unsharp-Mask-Stufe

**Files:**
- Modify: `domain/src/main/kotlin/com/komgareader/domain/color/PixelPipeline.kt`
- Modify: `domain/src/test/kotlin/com/komgareader/domain/color/PixelPipelineTest.kt`

- [ ] **Step 1: Failing test ergänzen**

Hänge in `PixelPipelineTest.kt` an:

```kotlin
    @Test
    fun `Unsharp-Mask erhöht den Kontrast an einer Kante`() {
        // 4x1 Kante: dunkel | dunkel | hell | hell. Nach Unsharp wird der dunkle Pixel an
        // der Kante dunkler und der helle heller (lokaler Kontrast steigt).
        val px = intArrayOf(argb(100, 100, 100), argb(100, 100, 100), argb(160, 160, 160), argb(160, 160, 160))
        applyPixelPipeline(px, width = 4, height = 1, profile(sharpenAmount = 1.0f, sharpenRadius = 1))
        assertTrue(g(px[1]) < 100, "Kanten-Pixel dunkle Seite: ${g(px[1])} < 100")
        assertTrue(g(px[2]) > 160, "Kanten-Pixel helle Seite: ${g(px[2])} > 160")
    }
```

- [ ] **Step 2: Test auf Rot laufen**

Run: `./gradlew :domain:test --tests "com.komgareader.domain.color.PixelPipelineTest" -q`
Expected: FAIL — Kanten-Pixel unverändert (Unsharp noch nicht implementiert).

- [ ] **Step 3: Unsharp-Stufe ergänzen**

In `applyPixelPipeline` nach dem Tonal-Block, vor dem Funktionsende, einfügen:

```kotlin
    if (profile.sharpenAmount > 0f) applyUnsharp(px, width, height, profile.sharpenAmount, profile.sharpenRadius)
```

Und die Hilfsfunktionen ans Dateiende:

```kotlin
/**
 * Unsharp-Mask: out = clamp(in + amount * (in - blur)). [radius] = Box-Blur-Radius in px.
 * Arbeitet pro Kanal auf einer Blur-Kopie der Eingangswerte (kein In-place-Feedback).
 */
private fun applyUnsharp(px: IntArray, width: Int, height: Int, amount: Float, radius: Int) {
    val n = px.size
    val rIn = IntArray(n); val gIn = IntArray(n); val bIn = IntArray(n)
    for (i in 0 until n) {
        val p = px[i]
        rIn[i] = (p shr 16) and 0xFF; gIn[i] = (p shr 8) and 0xFF; bIn[i] = p and 0xFF
    }
    val rBlur = boxBlur(rIn, width, height, radius)
    val gBlur = boxBlur(gIn, width, height, radius)
    val bBlur = boxBlur(bIn, width, height, radius)
    for (i in 0 until n) {
        val a = px[i] and -0x1000000
        val r = (rIn[i] + amount * (rIn[i] - rBlur[i])).roundToInt().coerceIn(0, 255)
        val g = (gIn[i] + amount * (gIn[i] - gBlur[i])).roundToInt().coerceIn(0, 255)
        val b = (bIn[i] + amount * (bIn[i] - bBlur[i])).roundToInt().coerceIn(0, 255)
        px[i] = a or (r shl 16) or (g shl 8) or b
    }
}

/** Einfacher (separabler) Box-Blur über ein Kanal-Array. */
private fun boxBlur(src: IntArray, width: Int, height: Int, radius: Int): IntArray {
    val tmp = IntArray(src.size)
    val out = IntArray(src.size)
    // horizontal
    for (y in 0 until height) {
        val row = y * width
        for (x in 0 until width) {
            var sum = 0; var cnt = 0
            for (dx in -radius..radius) {
                val xx = x + dx
                if (xx in 0 until width) { sum += src[row + xx]; cnt++ }
            }
            tmp[row + x] = sum / cnt
        }
    }
    // vertikal
    for (y in 0 until height) {
        for (x in 0 until width) {
            var sum = 0; var cnt = 0
            for (dy in -radius..radius) {
                val yy = y + dy
                if (yy in 0 until height) { sum += tmp[yy * width + x]; cnt++ }
            }
            out[y * width + x] = sum / cnt
        }
    }
    return out
}
```

- [ ] **Step 4: Test auf Grün laufen**

Run: `./gradlew :domain:test --tests "com.komgareader.domain.color.PixelPipelineTest" -q`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add domain/src/main/kotlin/com/komgareader/domain/color/PixelPipeline.kt domain/src/test/kotlin/com/komgareader/domain/color/PixelPipelineTest.kt && git commit -m "feat(domain): Unsharp-Mask-Stufe (Box-Blur + Schärfung)"
```

---

## Task 6: Domain — Dithering (Floyd-Steinberg + Ordered)

**Files:**
- Modify: `domain/src/main/kotlin/com/komgareader/domain/color/PixelPipeline.kt`
- Modify: `domain/src/test/kotlin/com/komgareader/domain/color/PixelPipelineTest.kt`

- [ ] **Step 1: Failing tests ergänzen**

Hänge in `PixelPipelineTest.kt` an:

```kotlin
    @Test
    fun `Floyd-Steinberg erhält die mittlere Helligkeit eines Graufelds`() {
        val n = 16 * 16
        val px = IntArray(n) { argb(120, 120, 120) }
        applyPixelPipeline(px, 16, 16, profile(ditherMode = com.komgareader.domain.model.DitherMode.FLOYD_STEINBERG, ditherLevels = 4))
        val mean = px.map { g(it) }.average()
        // Fehlerdiffusion bewahrt den Durchschnitt (± einige Stufen Toleranz am Rand).
        assertTrue(mean in 100.0..140.0, "mean=$mean")
    }

    @Test
    fun `Ordered ist deterministisch`() {
        val a = IntArray(8 * 8) { argb(90, 130, 200) }
        val b = a.copyOf()
        val p = profile(ditherMode = com.komgareader.domain.model.DitherMode.ORDERED, ditherLevels = 8)
        applyPixelPipeline(a, 8, 8, p)
        applyPixelPipeline(b, 8, 8, p)
        assertTrue(a.contentEquals(b))
    }

    @Test
    fun `Dither auf 256 Stufen ist nahezu verlustfrei`() {
        val px = intArrayOf(argb(10, 128, 240))
        val copy = px.copyOf()
        applyPixelPipeline(px, 1, 1, profile(ditherMode = com.komgareader.domain.model.DitherMode.ORDERED, ditherLevels = 256))
        assertTrue(px.contentEquals(copy), "256 Stufen ≈ keine Quantisierung")
    }
```

- [ ] **Step 2: Test auf Rot laufen**

Run: `./gradlew :domain:test --tests "com.komgareader.domain.color.PixelPipelineTest" -q`
Expected: FAIL — Dither nicht implementiert (Pixel unverändert).

- [ ] **Step 3: Dither-Stufen ergänzen**

In `applyPixelPipeline` ans Ende (nach Unsharp) einfügen:

```kotlin
    when (profile.ditherMode) {
        com.komgareader.domain.model.DitherMode.FLOYD_STEINBERG -> applyFloydSteinberg(px, width, height, profile.ditherLevels)
        com.komgareader.domain.model.DitherMode.ORDERED -> applyOrdered(px, width, height, profile.ditherLevels)
        com.komgareader.domain.model.DitherMode.NONE -> {}
    }
```

Hilfsfunktionen ans Dateiende:

```kotlin
/** Quantisiert einen 0..255-Wert auf [levels] gleichverteilte Stufen. */
private fun quantize(value: Int, levels: Int): Int {
    if (levels >= 256) return value.coerceIn(0, 255)
    val step = 255f / (levels - 1)
    return (Math.round(value / step) * step).roundToInt().coerceIn(0, 255)
}

/** Floyd-Steinberg-Fehlerdiffusion pro Kanal (sequentiell). */
private fun applyFloydSteinberg(px: IntArray, width: Int, height: Int, levels: Int) {
    if (levels >= 256) return
    val ch = intArrayOf(16, 8, 0) // R, G, B Shift
    for (shift in ch) {
        val buf = FloatArray(px.size) { ((px[it] shr shift) and 0xFF).toFloat() }
        for (y in 0 until height) {
            for (x in 0 until width) {
                val i = y * width + x
                val old = buf[i].roundToInt().coerceIn(0, 255)
                val new = quantize(old, levels)
                val err = buf[i] - new
                buf[i] = new.toFloat()
                if (x + 1 < width) buf[i + 1] += err * 7f / 16f
                if (y + 1 < height) {
                    if (x > 0) buf[i + width - 1] += err * 3f / 16f
                    buf[i + width] += err * 5f / 16f
                    if (x + 1 < width) buf[i + width + 1] += err * 1f / 16f
                }
            }
        }
        val mask = (0xFF shl shift).inv()
        for (i in px.indices) {
            val v = buf[i].roundToInt().coerceIn(0, 255)
            px[i] = (px[i] and mask) or (v shl shift)
        }
    }
}

/** Ordered (Bayer-4x4) Dithering pro Kanal — parallelisierbar, deterministisch. */
private fun applyOrdered(px: IntArray, width: Int, height: Int, levels: Int) {
    if (levels >= 256) return
    // Normalisierte 4x4-Bayer-Matrix, zentriert auf 0 (Bereich -0.5..0.5).
    val bayer = arrayOf(
        intArrayOf(0, 8, 2, 10), intArrayOf(12, 4, 14, 6),
        intArrayOf(3, 11, 1, 9), intArrayOf(15, 7, 13, 5),
    )
    val step = 255f / (levels - 1)
    for (y in 0 until height) {
        for (x in 0 until width) {
            val i = y * width + x
            val t = (bayer[y and 3][x and 3] / 16f - 0.5f) * step
            val a = px[i] and -0x1000000
            val r = quantize((((px[i] shr 16) and 0xFF) + t).roundToInt().coerceIn(0, 255), levels)
            val g = quantize((((px[i] shr 8) and 0xFF) + t).roundToInt().coerceIn(0, 255), levels)
            val b = quantize(((px[i] and 0xFF) + t).roundToInt().coerceIn(0, 255), levels)
            px[i] = a or (r shl 16) or (g shl 8) or b
        }
    }
}
```

- [ ] **Step 4: Test auf Grün laufen + ganzes Domain-Modul**

Run: `./gradlew :domain:test -q`
Expected: PASS (alle Domain-Tests inkl. ColorFilterMatrixTest, PixelPipelineTest, ColorProfileTest).

- [ ] **Step 5: Commit**

```bash
git add domain/src/main/kotlin/com/komgareader/domain/color/PixelPipeline.kt domain/src/test/kotlin/com/komgareader/domain/color/PixelPipelineTest.kt && git commit -m "feat(domain): Dithering (Floyd-Steinberg + Ordered/Bayer)"
```

---

## Task 7: Data — ColorProfileEntity + Mapper um Phase-2-Spalten

**Files:**
- Modify: `data/src/main/kotlin/com/komgareader/data/db/Entities.kt:52-61`
- Modify: `data/src/main/kotlin/com/komgareader/data/repository/RoomColorProfileRepository.kt:40-44`
- Test: `data/src/test/kotlin/com/komgareader/data/repository/RoomColorProfileRepositoryTest.kt`

- [ ] **Step 1: Failing test ergänzen**

Öffne `data/src/test/kotlin/com/komgareader/data/repository/RoomColorProfileRepositoryTest.kt`, finde einen bestehenden CRUD-Test und ergänze einen Round-Trip-Test für die neuen Felder (innerhalb der Testklasse):

```kotlin
    @Test
    fun `upsert und observeAll erhalten die Phase-2-Felder`() = runTest {
        val id = repo.upsert(
            ColorProfile(
                id = 0, name = "P2", saturation = 1f, contrast = 1f, brightness = 0f,
                blackPoint = 0.1f, whitePoint = 0.9f, gamma = 1.3f,
                sharpenAmount = 0.5f, sharpenRadius = 2,
                ditherMode = DitherMode.FLOYD_STEINBERG, ditherLevels = 8, builtIn = false,
            ),
        )
        val loaded = repo.observeAll().first().first { it.id == id }
        assertEquals(0.1f, loaded.blackPoint)
        assertEquals(0.9f, loaded.whitePoint)
        assertEquals(1.3f, loaded.gamma)
        assertEquals(0.5f, loaded.sharpenAmount)
        assertEquals(2, loaded.sharpenRadius)
        assertEquals(DitherMode.FLOYD_STEINBERG, loaded.ditherMode)
        assertEquals(8, loaded.ditherLevels)
    }
```

Stelle sicher, dass die Imports `com.komgareader.domain.model.DitherMode` und `com.komgareader.domain.model.ColorProfile` vorhanden sind (ergänzen falls nötig).

- [ ] **Step 2: Test auf Rot laufen**

Run: `./gradlew :data:test --tests "com.komgareader.data.repository.RoomColorProfileRepositoryTest" -q`
Expected: FAIL — Kompilierfehler (neue Felder existieren in Entity/Mapper nicht).

- [ ] **Step 3: Entity erweitern**

Ersetze `ColorProfileEntity` in `data/src/main/kotlin/com/komgareader/data/db/Entities.kt`:

```kotlin
/** Persistiertes E-Ink-Farbfilter-Profil. */
@Entity(tableName = "color_profiles")
data class ColorProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val saturation: Float,
    val contrast: Float,
    val brightness: Float,
    val blackPoint: Float = 0f,
    val whitePoint: Float = 1f,
    val gamma: Float = 1f,
    val sharpenAmount: Float = 0f,
    val sharpenRadius: Int = 1,
    val ditherMode: String = "NONE",
    val ditherLevels: Int = 16,
    val builtIn: Boolean,
)
```

- [ ] **Step 4: Mapper erweitern**

Ersetze die beiden Mapper-Funktionen am Ende von `RoomColorProfileRepository.kt`:

```kotlin
private fun ColorProfileEntity.toDomain() = ColorProfile(
    id = id, name = name, saturation = saturation, contrast = contrast, brightness = brightness,
    blackPoint = blackPoint, whitePoint = whitePoint, gamma = gamma,
    sharpenAmount = sharpenAmount, sharpenRadius = sharpenRadius,
    ditherMode = runCatching { DitherMode.valueOf(ditherMode) }.getOrDefault(DitherMode.NONE),
    ditherLevels = ditherLevels, builtIn = builtIn,
)

private fun ColorProfile.toEntity() = ColorProfileEntity(
    id = id, name = name, saturation = saturation, contrast = contrast, brightness = brightness,
    blackPoint = blackPoint, whitePoint = whitePoint, gamma = gamma,
    sharpenAmount = sharpenAmount, sharpenRadius = sharpenRadius,
    ditherMode = ditherMode.name, ditherLevels = ditherLevels, builtIn = builtIn,
)
```

Ergänze oben den Import: `import com.komgareader.domain.model.DitherMode`.

- [ ] **Step 5: Test auf Grün laufen**

Run: `./gradlew :data:test --tests "com.komgareader.data.repository.RoomColorProfileRepositoryTest" -q`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add data/src/main/kotlin/com/komgareader/data/db/Entities.kt data/src/main/kotlin/com/komgareader/data/repository/RoomColorProfileRepository.kt data/src/test/kotlin/com/komgareader/data/repository/RoomColorProfileRepositoryTest.kt && git commit -m "feat(data): ColorProfileEntity + Mapper um Phase-2-Spalten"
```

---

## Task 8: Data — Room v7→v8-Migration + Demo-Built-in seeden

**Files:**
- Modify: `data/src/main/kotlin/com/komgareader/data/db/AppDatabase.kt`
- Modify: `data/src/main/kotlin/com/komgareader/data/di/DataModule.kt:6-11,39`
- Modify: `data/src/androidTest/kotlin/com/komgareader/data/ColorProfileSeedTest.kt`

- [ ] **Step 1: Failing test (Seed-Erwartung) ergänzen**

In `ColorProfileSeedTest.kt` einen Test ergänzen, der das neue Built-in erwartet (innerhalb der Klasse). Nutze den vorhandenen DAO-Zugriff (`db.colorProfileDao()`):

```kotlin
    @Test
    fun freshInstall_enthältDemoBuiltinVoll() = runBlocking {
        val all = db.colorProfileDao().observeAll().first()
        val voll = all.firstOrNull { it.name == "Boox Go Color 7 — Voll" }
        assertTrue(voll != null && voll.builtIn)
        // Phase-2-Default-Werte des Demo-Profils:
        assertEquals(1.2f, voll!!.gamma)
        assertTrue(voll.sharpenAmount > 0f)
    }
```

Stelle sicher, dass `import org.junit.Assert.assertEquals` und `kotlinx.coroutines.flow.first` vorhanden sind.

- [ ] **Step 2: Test auf Rot laufen**

Run: `./gradlew :data:connectedDebugAndroidTest --tests "com.komgareader.data.ColorProfileSeedTest" -q`
Expected: FAIL — Demo-Built-in fehlt. (Benötigt laufenden Emulator `eink_test`.)

- [ ] **Step 3: seedColorProfiles + Migration + Version**

In `AppDatabase.kt`: `version = 7` → `version = 8`.

Ersetze `seedColorProfiles` (volle Spaltenliste + neues Built-in):

```kotlin
private fun seedColorProfiles(db: SupportSQLiteDatabase) {
    val cols = "(`id`,`name`,`saturation`,`contrast`,`brightness`,`blackPoint`,`whitePoint`,`gamma`,`sharpenAmount`,`sharpenRadius`,`ditherMode`,`ditherLevels`,`builtIn`)"
    db.execSQL("INSERT OR REPLACE INTO `color_profiles` $cols VALUES (1,'Aus',1.0,1.0,0.0,0.0,1.0,1.0,0.0,1,'NONE',16,1)")
    db.execSQL("INSERT OR REPLACE INTO `color_profiles` $cols VALUES (2,'Boox Go Color 7 Gen2',1.4,1.15,0.05,0.0,1.0,1.0,0.0,1,'NONE',16,1)")
    db.execSQL("INSERT OR REPLACE INTO `color_profiles` $cols VALUES (3,'Boox Go Color 7 — Voll',1.4,1.15,0.05,0.05,0.95,1.2,0.6,1,'NONE',16,1)")
    db.execSQL("INSERT OR REPLACE INTO `settings` (`key`,`value`) VALUES ('active_color_profile_id','2')")
}
```

Ergänze nach `MIGRATION_6_7` die neue Migration:

```kotlin
/**
 * v7 → v8: Phase-2-Pixel-Pipeline-Spalten (Levels/Gamma/Unsharp/Dither) mit neutralen Defaults
 * — bestehende Profile bleiben dadurch unverändert. Seedet zusätzlich das Demo-Built-in
 * „Boox Go Color 7 — Voll". Der aktive Pointer wird NICHT verändert.
 */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `color_profiles` ADD COLUMN `blackPoint` REAL NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `color_profiles` ADD COLUMN `whitePoint` REAL NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE `color_profiles` ADD COLUMN `gamma` REAL NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE `color_profiles` ADD COLUMN `sharpenAmount` REAL NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `color_profiles` ADD COLUMN `sharpenRadius` INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE `color_profiles` ADD COLUMN `ditherMode` TEXT NOT NULL DEFAULT 'NONE'")
        db.execSQL("ALTER TABLE `color_profiles` ADD COLUMN `ditherLevels` INTEGER NOT NULL DEFAULT 16")
        db.execSQL(
            "INSERT OR REPLACE INTO `color_profiles` " +
                "(`id`,`name`,`saturation`,`contrast`,`brightness`,`blackPoint`,`whitePoint`,`gamma`,`sharpenAmount`,`sharpenRadius`,`ditherMode`,`ditherLevels`,`builtIn`) " +
                "VALUES (3,'Boox Go Color 7 — Voll',1.4,1.15,0.05,0.05,0.95,1.2,0.6,1,'NONE',16,1)",
        )
    }
}
```

- [ ] **Step 4: Migration in DI registrieren**

In `DataModule.kt`: Import ergänzen `import com.komgareader.data.db.MIGRATION_7_8` und die `addMigrations`-Zeile erweitern:

```kotlin
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
```

- [ ] **Step 5: Test auf Grün laufen**

Run: `./gradlew :data:connectedDebugAndroidTest --tests "com.komgareader.data.ColorProfileSeedTest" -q`
Expected: PASS (Fresh-Install enthält das Demo-Built-in mit gamma=1.2, sharpenAmount>0).

- [ ] **Step 6: Commit**

```bash
git add data/src/main/kotlin/com/komgareader/data/db/AppDatabase.kt data/src/main/kotlin/com/komgareader/data/di/DataModule.kt data/src/androidTest/kotlin/com/komgareader/data/ColorProfileSeedTest.kt && git commit -m "feat(data): Room v7→v8 — Phase-2-Spalten + Demo-Built-in Boox Go Color 7 — Voll"
```

---

## Task 9: App — Coil-Transformation + Reader-Wrapper + LocalColorProfile

**Files:**
- Create: `app/src/main/kotlin/com/komgareader/app/color/ColorPipelineTransformation.kt`
- Create: `app/src/main/kotlin/com/komgareader/app/ui/components/FilteredReaderImage.kt`
- Modify: `app/src/main/kotlin/com/komgareader/app/ui/components/FilteredImage.kt:23-25`
- Modify: `app/src/main/kotlin/com/komgareader/app/MainActivity.kt:85-89`

- [ ] **Step 1: Coil-Transformation anlegen**

Create `app/src/main/kotlin/com/komgareader/app/color/ColorPipelineTransformation.kt`:

```kotlin
package com.komgareader.app.color

import android.graphics.Bitmap
import coil.size.Size
import coil.transform.Transformation
import com.komgareader.domain.color.applyPixelPipeline
import com.komgareader.domain.model.ColorProfile

/**
 * Coil-Transformation: wendet die volle E-Ink-Pixel-Pipeline ([applyPixelPipeline]) auf das
 * dekodierte Seiten-Bitmap an. Nur an Reader-Seiten-Requests hängen (nie an Cover). Der
 * [cacheKey] enthält alle Profil-Werte → Coil cacht das Ergebnis pro Bild+Profil (einmal rechnen).
 */
class ColorPipelineTransformation(private val profile: ColorProfile) : Transformation {

    override val cacheKey: String = "colorpipe:" + listOf(
        profile.saturation, profile.contrast, profile.brightness,
        profile.blackPoint, profile.whitePoint, profile.gamma,
        profile.sharpenAmount, profile.sharpenRadius, profile.ditherMode, profile.ditherLevels,
    ).joinToString(":")

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        val bmp = if (input.config == Bitmap.Config.ARGB_8888 && input.isMutable) input
        else input.copy(Bitmap.Config.ARGB_8888, true)
        val w = bmp.width; val h = bmp.height
        val px = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h)
        applyPixelPipeline(px, w, h, profile)
        bmp.setPixels(px, 0, w, 0, 0, w, h)
        return bmp
    }

    override fun equals(other: Any?) = other is ColorPipelineTransformation && other.cacheKey == cacheKey
    override fun hashCode() = cacheKey.hashCode()
}
```

- [ ] **Step 2: Reader-Wrapper + LocalColorProfile anlegen**

Create `app/src/main/kotlin/com/komgareader/app/ui/components/FilteredReaderImage.kt`:

```kotlin
package com.komgareader.app.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.komgareader.app.color.ColorPipelineTransformation
import com.komgareader.domain.color.applyPixelPipeline
import com.komgareader.domain.model.ColorProfile

/**
 * Aktives Profil für den Reader-Pfad. Wird in MainActivity bereitgestellt. Cover lesen weiter
 * [LocalImageFilter] (nur GPU-Matrix); Reader-Wrapper lesen DIESES, um die volle Pixel-Pipeline
 * zu entscheiden.
 */
val LocalColorProfile = staticCompositionLocalOf { ColorProfile.OFF }

/**
 * Coil-`AsyncImage` für **Reader-Seiten**. Wenn das aktive Profil [ColorProfile.needsPixelPipeline]
 * verlangt, läuft die volle Pixel-Pipeline als Coil-Transformation (colorFilter = null, da der
 * Kernel die lineare Stufe mitmacht). Sonst nur die billige GPU-Matrix (wie Cover).
 * [profileOverride] übersteuert das aktive Profil (Live-Vorschau im Editor).
 */
@Composable
fun FilteredReaderAsyncImage(
    model: ImageRequest,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    profileOverride: ColorProfile? = null,
) {
    val profile = profileOverride ?: LocalColorProfile.current
    if (profile.needsPixelPipeline) {
        val req = remember(model, profile) {
            model.newBuilder().transformations(ColorPipelineTransformation(profile)).build()
        }
        AsyncImage(model = req, contentDescription = contentDescription, contentScale = contentScale, colorFilter = null, modifier = modifier)
    } else {
        AsyncImage(model = model, contentDescription = contentDescription, contentScale = contentScale, colorFilter = profile.toColorFilterOrNull(), modifier = modifier)
    }
}

/**
 * Compose-`Image` für **MuPDF-Reader-Seiten** (EPUB/PDF). Verarbeitet das Bitmap pixelweise,
 * wenn das aktive Profil es verlangt; sonst GPU-Matrix. Verarbeitung ist auf (Bitmap, Profil)
 * gemerkt — kein Neurechnen pro Recomposition.
 */
@Composable
fun FilteredReaderImage(
    bitmap: Bitmap,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    profileOverride: ColorProfile? = null,
) {
    val profile = profileOverride ?: LocalColorProfile.current
    if (profile.needsPixelPipeline) {
        val processed = remember(bitmap, profile) {
            val out = bitmap.copy(Bitmap.Config.ARGB_8888, true)
            val w = out.width; val h = out.height
            val px = IntArray(w * h)
            out.getPixels(px, 0, w, 0, 0, w, h)
            applyPixelPipeline(px, w, h, profile)
            out.setPixels(px, 0, w, 0, 0, w, h)
            out.asImageBitmap()
        }
        Image(bitmap = processed, contentDescription = contentDescription, contentScale = contentScale, colorFilter = null, modifier = modifier)
    } else {
        Image(bitmap = bitmap.asImageBitmap(), contentDescription = contentDescription, contentScale = contentScale, colorFilter = profile.toColorFilterOrNull(), modifier = modifier)
    }
}
```

- [ ] **Step 3: toColorFilterOrNull auf isLinearNeutral umstellen**

In `app/src/main/kotlin/com/komgareader/app/ui/components/FilteredImage.kt` die Funktion ändern, damit Cover auch bei gesetzten Phase-2-Feldern nur die lineare Matrix bekommen:

```kotlin
/** Wandelt ein Profil in einen ColorFilter (nur linearer Teil) — oder `null`, wenn linear neutral. */
fun ColorProfile.toColorFilterOrNull(): ColorFilter? =
    if (isLinearNeutral) null
    else ColorFilter.colorMatrix(ColorMatrix(buildColorMatrix(saturation, contrast, brightness)))
```

- [ ] **Step 4: LocalColorProfile in MainActivity providen**

In `MainActivity.kt` den Import ergänzen `import com.komgareader.app.ui.components.LocalColorProfile` und im `CompositionLocalProvider` eine Zeile hinzufügen:

```kotlin
            CompositionLocalProvider(
                LocalStrings provides stringsFor(language),
                LocalEinkMode provides isEink,
                LocalImageFilter provides activeColorProfile.toColorFilterOrNull(),
                LocalColorProfile provides activeColorProfile,
            ) {
```

- [ ] **Step 5: Build prüfen**

Run: `./gradlew :app:compileDebugKotlin -q`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/com/komgareader/app/color/ColorPipelineTransformation.kt app/src/main/kotlin/com/komgareader/app/ui/components/FilteredReaderImage.kt app/src/main/kotlin/com/komgareader/app/ui/components/FilteredImage.kt app/src/main/kotlin/com/komgareader/app/MainActivity.kt && git commit -m "feat(app): Coil-Pipeline-Transformation + Reader-Wrapper + LocalColorProfile"
```

---

## Task 10: App — Reader-Screens auf Reader-Wrapper umstellen

**Files:**
- Modify: `app/src/main/kotlin/com/komgareader/app/ui/reader/PagedReaderScreen.kt:30,86-91`
- Modify: `app/src/main/kotlin/com/komgareader/app/ui/reader/WebtoonReaderScreen.kt:35,131`
- Modify: `app/src/main/kotlin/com/komgareader/app/ui/reader/EpubReaderScreen.kt:4,70-75`

- [ ] **Step 1: PagedReaderScreen umstellen**

Import `com.komgareader.app.ui.components.FilteredAsyncImage` ersetzen durch `com.komgareader.app.ui.components.FilteredReaderAsyncImage`. Den Aufruf `FilteredAsyncImage(...)` (Zeile ~86) in `FilteredReaderAsyncImage(...)` umbenennen (gleiche Parameter):

```kotlin
            FilteredReaderAsyncImage(
                model = request,
                contentDescription = "Seite ${pageIndex + 1}",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
```

- [ ] **Step 2: WebtoonReaderScreen umstellen**

Import `FilteredAsyncImage` → `FilteredReaderAsyncImage`; den `FilteredAsyncImage(...)`-Aufruf (Zeile ~131) entsprechend umbenennen. Parameter unverändert lassen.

- [ ] **Step 3: EpubReaderScreen umstellen**

Import `com.komgareader.app.ui.components.FilteredImage` → `com.komgareader.app.ui.components.FilteredReaderImage`. Den Aufruf (Zeile ~70) ändern — `FilteredReaderImage` nimmt das **android.graphics.Bitmap** direkt (nicht `asImageBitmap()`):

```kotlin
                if (bmp != null) {
                    FilteredReaderImage(
                        bitmap = bmp!!,
                        contentDescription = "Seite ${pageIndex + 1}",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
```

Den jetzt ungenutzten Import `androidx.compose.ui.graphics.asImageBitmap` (Zeile 23) entfernen, falls er sonst nirgends in der Datei genutzt wird.

- [ ] **Step 4: Build prüfen**

Run: `./gradlew :app:compileDebugKotlin -q`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/komgareader/app/ui/reader/PagedReaderScreen.kt app/src/main/kotlin/com/komgareader/app/ui/reader/WebtoonReaderScreen.kt app/src/main/kotlin/com/komgareader/app/ui/reader/EpubReaderScreen.kt && git commit -m "feat(app): Reader-Seiten (Paged/Webtoon/EPUB) durch die Pixel-Pipeline"
```

---

## Task 11: App — i18n-Keys für Phase 2

**Files:**
- Modify: `app/src/main/kotlin/com/komgareader/app/i18n/Strings.kt` (Interface ~98-114, DE ~215-231, EN ~332-348)

- [ ] **Step 1: Interface-Keys ergänzen**

Im Strings-Interface nach `val colorFilterNextImage: String` hinzufügen:

```kotlin
    val colorFilterAdvanced: String
    val colorFilterBlackPoint: String
    val colorFilterWhitePoint: String
    val colorFilterGamma: String
    val colorFilterSharpen: String
    val colorFilterSharpenRadius: String
    val colorFilterDither: String
    val colorFilterDitherNone: String
    val colorFilterDitherFloyd: String
    val colorFilterDitherOrdered: String
    val colorFilterDitherLevels: String
    val colorFilterReaderOnlyHint: String
```

- [ ] **Step 2: DE-Werte ergänzen**

Im deutschen Objekt nach `override val colorFilterNextImage = "Nächstes"`:

```kotlin
    override val colorFilterAdvanced = "Erweitert"
    override val colorFilterBlackPoint = "Schwarzpunkt"
    override val colorFilterWhitePoint = "Weißpunkt"
    override val colorFilterGamma = "Gamma"
    override val colorFilterSharpen = "Schärfe"
    override val colorFilterSharpenRadius = "Schärfe-Radius"
    override val colorFilterDither = "Dithering"
    override val colorFilterDitherNone = "Aus"
    override val colorFilterDitherFloyd = "Floyd-Steinberg"
    override val colorFilterDitherOrdered = "Ordered"
    override val colorFilterDitherLevels = "Stufen"
    override val colorFilterReaderOnlyHint = "Wirkt nur beim Lesen, nicht auf Bibliotheks-Cover. Erhöht den Akku-Verbrauch."
```

- [ ] **Step 3: EN-Werte ergänzen**

Im englischen Objekt nach `override val colorFilterNextImage = "Next"`:

```kotlin
    override val colorFilterAdvanced = "Advanced"
    override val colorFilterBlackPoint = "Black point"
    override val colorFilterWhitePoint = "White point"
    override val colorFilterGamma = "Gamma"
    override val colorFilterSharpen = "Sharpen"
    override val colorFilterSharpenRadius = "Sharpen radius"
    override val colorFilterDither = "Dithering"
    override val colorFilterDitherNone = "Off"
    override val colorFilterDitherFloyd = "Floyd-Steinberg"
    override val colorFilterDitherOrdered = "Ordered"
    override val colorFilterDitherLevels = "Levels"
    override val colorFilterReaderOnlyHint = "Applies only while reading, not to library covers. Increases battery use."
```

- [ ] **Step 4: Build prüfen (Compile-Zeit-Parität DE/EN)**

Run: `./gradlew :app:compileDebugKotlin -q`
Expected: BUILD SUCCESSFUL (fehlt ein Key in einer Sprache → Compile-Fehler).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/komgareader/app/i18n/Strings.kt && git commit -m "feat(app): i18n-Keys für Farbfilter-Phase-2 (DE+EN)"
```

---

## Task 12: App — ColorFilterViewModel um Phase-2-Editor-Werte

**Files:**
- Modify: `app/src/main/kotlin/com/komgareader/app/ui/settings/ColorFilterViewModel.kt`

- [ ] **Step 1: EditState erweitern**

Ersetze die `EditState`-data-class:

```kotlin
/** Editor-Werte (live, noch nicht persistiert). */
data class EditState(
    val baseProfileId: Long,
    val name: String,
    val saturation: Float,
    val contrast: Float,
    val brightness: Float,
    val blackPoint: Float,
    val whitePoint: Float,
    val gamma: Float,
    val sharpenAmount: Float,
    val sharpenRadius: Int,
    val ditherMode: DitherMode,
    val ditherLevels: Int,
    val builtIn: Boolean,
)
```

Ergänze Import `import com.komgareader.domain.model.DitherMode`.

- [ ] **Step 2: beginEdit / beginNewProfile erweitern**

Ersetze beide Funktionen:

```kotlin
    /** Editor mit den Werten von [profile] öffnen. */
    fun beginEdit(profile: ColorProfile) {
        _edit.value = EditState(
            baseProfileId = profile.id, name = profile.name,
            saturation = profile.saturation, contrast = profile.contrast, brightness = profile.brightness,
            blackPoint = profile.blackPoint, whitePoint = profile.whitePoint, gamma = profile.gamma,
            sharpenAmount = profile.sharpenAmount, sharpenRadius = profile.sharpenRadius,
            ditherMode = profile.ditherMode, ditherLevels = profile.ditherLevels, builtIn = profile.builtIn,
        )
    }

    /** Neuen, editierbaren Entwurf öffnen (baseProfileId = 0), vorbefüllt vom aktiven Profil. */
    fun beginNewProfile() {
        val base = active.value
        _edit.value = EditState(
            baseProfileId = 0L, name = "",
            saturation = base.saturation, contrast = base.contrast, brightness = base.brightness,
            blackPoint = base.blackPoint, whitePoint = base.whitePoint, gamma = base.gamma,
            sharpenAmount = base.sharpenAmount, sharpenRadius = base.sharpenRadius,
            ditherMode = base.ditherMode, ditherLevels = base.ditherLevels, builtIn = false,
        )
    }
```

- [ ] **Step 3: Update-Funktionen ergänzen**

Nach den bestehenden `updateBrightness`-Zeile einfügen:

```kotlin
    fun updateBlackPoint(delta: Float) = mutate { it.copy(blackPoint = clamp(it.blackPoint + delta, 0f, 0.4f)) }
    fun updateWhitePoint(delta: Float) = mutate { it.copy(whitePoint = clamp(it.whitePoint + delta, 0.6f, 1f)) }
    fun updateGamma(delta: Float) = mutate { it.copy(gamma = clamp(it.gamma + delta, 0.4f, 2.5f)) }
    fun updateSharpen(delta: Float) = mutate { it.copy(sharpenAmount = clamp(it.sharpenAmount + delta, 0f, 2f)) }
    fun updateSharpenRadius(delta: Int) = mutate { it.copy(sharpenRadius = (it.sharpenRadius + delta).coerceIn(1, 3)) }
    fun setDitherMode(mode: DitherMode) = mutate { it.copy(ditherMode = mode) }
    fun updateDitherLevels(delta: Int) = mutate { it.copy(ditherLevels = (it.ditherLevels + delta).coerceIn(2, 64)) }
```

- [ ] **Step 4: saveAsNew / updateExisting erweitern**

Ersetze beide Funktionen, damit die neuen Felder persistiert werden:

```kotlin
    /** Aktuelle Editor-Werte als neues Custom-Profil speichern und aktiv setzen. */
    fun saveAsNew(name: String) = viewModelScope.launch {
        val e = _edit.value ?: return@launch
        val id = colorProfiles.upsert(e.toProfile(id = 0L, name = name.ifBlank { "Profil" }))
        colorProfiles.setActive(id)
        _edit.value = null
    }

    /** Bestehendes Custom-Profil aktualisieren. */
    fun updateExisting() = viewModelScope.launch {
        val e = _edit.value ?: return@launch
        if (e.builtIn) return@launch
        colorProfiles.upsert(e.toProfile(id = e.baseProfileId, name = e.name))
        _edit.value = null
    }

    private fun EditState.toProfile(id: Long, name: String) = ColorProfile(
        id = id, name = name, saturation = saturation, contrast = contrast, brightness = brightness,
        blackPoint = blackPoint, whitePoint = whitePoint, gamma = gamma,
        sharpenAmount = sharpenAmount, sharpenRadius = sharpenRadius,
        ditherMode = ditherMode, ditherLevels = ditherLevels, builtIn = false,
    )
```

- [ ] **Step 5: Build prüfen**

Run: `./gradlew :app:compileDebugKotlin -q`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/com/komgareader/app/ui/settings/ColorFilterViewModel.kt && git commit -m "feat(app): ColorFilterViewModel — Phase-2-Editor-Werte + Persistenz"
```

---

## Task 13: App — UI „Erweitert"-Sektion + Dither + Vorschau auf volle Pipeline

**Files:**
- Modify: `app/src/main/kotlin/com/komgareader/app/ui/settings/ColorFilterSettingsContent.kt`

- [ ] **Step 1: Vorschau-Cover auf die volle Pipeline umstellen**

Im Composable die Vorschau-`previewProfile`-Konstruktion (Zeile ~98) auf alle Felder erweitern und den `FilteredAsyncImage`-Aufruf durch `FilteredReaderAsyncImage` mit `profileOverride` ersetzen. Ersetze den Block:

```kotlin
        val previewProfile = edit?.let {
            ColorProfile(
                id = it.baseProfileId, name = it.name,
                saturation = it.saturation, contrast = it.contrast, brightness = it.brightness,
                blackPoint = it.blackPoint, whitePoint = it.whitePoint, gamma = it.gamma,
                sharpenAmount = it.sharpenAmount, sharpenRadius = it.sharpenRadius,
                ditherMode = it.ditherMode, ditherLevels = it.ditherLevels, builtIn = it.builtIn,
            )
        } ?: active
```

und den Bild-Aufruf:

```kotlin
                FilteredReaderAsyncImage(
                    model = request,
                    contentDescription = s.colorFilterPreview,
                    contentScale = ContentScale.Crop,
                    profileOverride = previewProfile,
                    modifier = Modifier
                        .height(240.dp)
                        .aspectRatio(2f / 3f)
                        .clip(RoundedCornerShape(4.dp))
                        .border(1.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp)),
                )
```

Imports anpassen: `com.komgareader.app.ui.components.FilteredAsyncImage` → `com.komgareader.app.ui.components.FilteredReaderAsyncImage`; den jetzt ungenutzten Import `com.komgareader.app.ui.components.toColorFilterOrNull` entfernen. Import `com.komgareader.domain.model.DitherMode` ergänzen.

- [ ] **Step 2: „Erweitert"-Sektion im Editor ergänzen**

Im Editor-Block (`edit?.takeIf { !it.builtIn }?.let { e ->`), nach der `CompactStepperRow` für Helligkeit (Zeile ~231) und **vor** der Aktions-`Row`, einfügen:

```kotlin
                SectionHeader(s.colorFilterAdvanced)
                CompactStepperRow(s.colorFilterBlackPoint, format(e.blackPoint),
                    { viewModel.updateBlackPoint(-STEP) }, { viewModel.updateBlackPoint(STEP) })
                CompactStepperRow(s.colorFilterWhitePoint, format(e.whitePoint),
                    { viewModel.updateWhitePoint(-STEP) }, { viewModel.updateWhitePoint(STEP) })
                CompactStepperRow(s.colorFilterGamma, format(e.gamma),
                    { viewModel.updateGamma(-STEP) }, { viewModel.updateGamma(STEP) })
                CompactStepperRow(s.colorFilterSharpen, format(e.sharpenAmount),
                    { viewModel.updateSharpen(-0.1f) }, { viewModel.updateSharpen(0.1f) })
                CompactStepperRow(s.colorFilterSharpenRadius, e.sharpenRadius.toString(),
                    { viewModel.updateSharpenRadius(-1) }, { viewModel.updateSharpenRadius(1) })
                DitherSelectorRow(
                    selected = e.ditherMode,
                    labels = Triple(s.colorFilterDitherNone, s.colorFilterDitherFloyd, s.colorFilterDitherOrdered),
                    label = s.colorFilterDither,
                    onSelect = { viewModel.setDitherMode(it) },
                )
                if (e.ditherMode != DitherMode.NONE) {
                    CompactStepperRow(s.colorFilterDitherLevels, e.ditherLevels.toString(),
                        { viewModel.updateDitherLevels(-2) }, { viewModel.updateDitherLevels(2) })
                }
                Text(
                    s.colorFilterReaderOnlyHint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
```

- [ ] **Step 3: DitherSelectorRow-Composable ergänzen**

Am Dateiende (neben den anderen privaten Composables) hinzufügen:

```kotlin
/** Dither-Auswahl als drei umrandete Segmente (Aus / Floyd-Steinberg / Ordered) — E-Ink-flach. */
@Composable
private fun DitherSelectorRow(
    selected: com.komgareader.domain.model.DitherMode,
    labels: Triple<String, String, String>,
    label: String,
    onSelect: (com.komgareader.domain.model.DitherMode) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        val modes = listOf(
            com.komgareader.domain.model.DitherMode.NONE to labels.first,
            com.komgareader.domain.model.DitherMode.FLOYD_STEINBERG to labels.second,
            com.komgareader.domain.model.DitherMode.ORDERED to labels.third,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            modes.forEach { (mode, text) ->
                val active = mode == selected
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .then(
                            if (active) Modifier.background(MaterialTheme.colorScheme.primary)
                            else Modifier.border(EinkTokens.hairline, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp)),
                        )
                        .clickable { onSelect(mode) }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
    }
}
```

Ergänze die Imports `androidx.compose.foundation.background` (falls nicht vorhanden).

- [ ] **Step 4: Info-Dialog um Phase-2-Werte ergänzen**

Im `infoProfile?.let { p -> EinkInfoDialog(...)`-Block nach den drei bestehenden `InfoValueRow` ergänzen:

```kotlin
            InfoValueRow(s.colorFilterBlackPoint, format(p.blackPoint))
            InfoValueRow(s.colorFilterWhitePoint, format(p.whitePoint))
            InfoValueRow(s.colorFilterGamma, format(p.gamma))
            InfoValueRow(s.colorFilterSharpen, format(p.sharpenAmount))
            InfoValueRow(s.colorFilterDither, when (p.ditherMode) {
                DitherMode.NONE -> s.colorFilterDitherNone
                DitherMode.FLOYD_STEINBERG -> s.colorFilterDitherFloyd
                DitherMode.ORDERED -> s.colorFilterDitherOrdered
            })
```

- [ ] **Step 5: Build prüfen**

Run: `./gradlew :app:compileDebugKotlin -q`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/com/komgareader/app/ui/settings/ColorFilterSettingsContent.kt && git commit -m "feat(app): Farbfilter-Editor — Erweitert-Sektion (Levels/Gamma/Schärfe/Dither) + volle Vorschau"
```

---

## Task 14: Verifikation (Build, Tests, E2E auf Emulator)

**Files:** keine (Verifikation).

- [ ] **Step 1: Volles Build + alle JVM-Unit-Tests**

Run: `./gradlew :domain:test :data:test :app:assembleDebug -q`
Expected: BUILD SUCCESSFUL, alle Tests grün.

- [ ] **Step 2: Room-Migration-/Seed-Test (Emulator)**

Run: `./gradlew :data:connectedDebugAndroidTest --tests "com.komgareader.data.ColorProfileSeedTest" -q`
Expected: PASS.

- [ ] **Step 3: App installieren + DB frisch seeden**

Run: `./gradlew :app:installDebug -q && adb shell pm clear com.komgareader.app`
Expected: Success; frische DB enthält Built-ins inkl. „Boox Go Color 7 — Voll".

- [ ] **Step 4: Sichtbare Verifikation (Screenshots)**

Manuell/per adb: App öffnen → Einstellungen → Farbfilter. „Boox Go Color 7 — Voll" aktivieren, Zahnrad → Editor: Gamma/Schärfe/Dither ändern, **Vorschau-Cover aktualisiert sich live mit voller Pipeline**. Dann eine Serie öffnen und lesen (Paged + EPUB) → Seite sichtbar verarbeitet. Zurück zur Bibliothek → **Cover NICHT pixelverarbeitet** (nur lineare Matrix, Scrollen flüssig).

Run (Screenshots): `adb exec-out screencap -p > /tmp/p2_settings.png` und `adb exec-out screencap -p > /tmp/p2_reader.png`
Expected: Vorschau zeigt Effekt; Reader-Seite gefiltert; Bibliotheks-Cover unverändert.

- [ ] **Step 5: Auf der echten Boox deployen (E-Ink-Realeindruck + Akku)**

Run: `./gradlew :app:installDebug -q` (Boox per USB; `install -r` erhält die Daten)
Expected: Nutzer prüft Kaleido-Realeindruck und Akku-Verhalten — der eigentliche Zweck des Features.

- [ ] **Step 6: Skill-Notiz (optional, nur wenn neue Erkenntnis)**

Falls beim Bau eine nicht-offensichtliche E-Ink-/Pipeline-Erkenntnis auftaucht: `.claude/skills/komga-eink-color-filter/SKILL.md` ergänzen (Phase-2-Abschnitt: Kernel-Stufen-Reihenfolge, Cover-vs-Reader-Trennung, Coil-cacheKey-Falle).

---

## Self-Review-Notizen (vom Plan-Autor)

- **Spec-Abdeckung:** Gamma (T3/T4), Levels (T4), Unsharp (T5), Dither beide Modi (T6), `ColorProfile`-Felder (T2), Room v7→v8 + Demo-Built-in (T7/T8), Cover-nur-linear-Trennung (T9 `toColorFilterOrNull`→`isLinearNeutral`, Reader-Wrapper), Reader-alle-3-Viewer (T10), Live-Vorschau volle Pipeline (T13), i18n (T11), Tests (T2–T8), E2E (T14). Vollständig.
- **Typ-Konsistenz:** `applyPixelPipeline(IntArray, Int, Int, ColorProfile)`, `buildGammaLut(Float): IntArray`, `ColorPipelineTransformation(ColorProfile)`, `FilteredReaderAsyncImage(model, …, profileOverride)`, `FilteredReaderImage(bitmap: Bitmap, …, profileOverride)`, `LocalColorProfile: ColorProfile`, `DitherMode` — über alle Tasks identisch verwendet.
- **Keine Platzhalter:** jeder Code-Schritt zeigt vollständigen Code.
```
