# Deklaratives ui_pack.json-Theme (Phase 2 / 2a) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Die `theme`-Sektion eines externen `ui_pack.json` auf einen **vollen, host-gerenderten Look** (Farb-Rollen hell+dunkel, Radius, Elevation, Typo) erweitern, sodass ein externes data-only APK denselben Look wie der In-Tree-`AuroraPack` liefert — rein deklarativ, E-Ink-host-gegated.

**Architecture:** Reine Daten in `domain` (`ThemeSpec`), tolerantes Parsen in `data` (`parseUiPackSpec`), Übersetzung in einen Runtime-`UiPack` nur in `:app` (`toUiPackOrNull`), Host (`KomgaReaderTheme`) ersetzt den geräteklassen-Pack durch den externen — nur wenn `DisplayBehavior.allowsAccentColor`. Alte flache `accent`/`cornerRadius`-Packs bleiben über den bestehenden `tokenOverride`-Pfad gültig.

**Tech Stack:** Kotlin, Compose Material 3, org.json (data), JUnit5, Android-Emulator.

**Referenz-Spec:** `docs/superpowers/specs/2026-06-12-ui-pack-declarative-theme-phase2-design.md` (verbindlich). Baut auf dem gemergten Phase-1-`AuroraPack` (der Referenz-Look, §2 dort).

---

## File Structure

**Neu:**
- `domain/src/main/kotlin/com/komgareader/domain/model/ThemeSpec.kt` — reine Theme-Daten (`ThemeSpec`/`ColorRolesSpec`/`TypoSpec`).
- `data/src/test/.../plugin/UiPackParserThemeTest.kt` — neue Parser-Tests (oder Ergänzung der bestehenden Datei).
- `app/src/main/kotlin/com/komgareader/app/ui/pack/UiPackTheme.kt` — `UiPackSpec.toUiPackOrNull()` + Helfer (Runtime-`UiPack`-Bau).
- `app/src/test/.../ui/pack/UiPackThemeTest.kt` — Konverter-Tests.
- `docs/ui-packs/README.md` — Schema-Referenz + Aurora-Beispiel + Walkthrough.
- `plugin/komga-ui-pack-aurora/` — externes data-only Aurora-APK (Template: `plugin/komga-ui-pack-sample/`).

**Geändert:**
- `domain/.../model/UiPackSpec.kt` — `val theme: ThemeSpec? = null` + `hasAnyOverride`.
- `data/.../plugin/UiPackParser.kt` — `theme.light/dark/elevation/typography` parsen.
- `app/.../ui/theme/Theme.kt` — `KomgaReaderTheme(externalPack: UiPack? = null)` + Gate.
- `app/.../MainActivity.kt` — `externalPack = activeUiPack?.toUiPackOrNull()` reinreichen.
- `KomgaReaderPlugins/repo.json` (Distributions-Repo) — `ui_pack`-Eintrag Aurora.
- `.claude/rules/architecture-seams.md` + `big-picture-and-goals.md` — docs-match-code.

---

## Task 1: domain — ThemeSpec + UiPackSpec.theme

**Files:**
- Create: `domain/src/main/kotlin/com/komgareader/domain/model/ThemeSpec.kt`
- Modify: `domain/src/main/kotlin/com/komgareader/domain/model/UiPackSpec.kt`
- Test: `domain/src/test/kotlin/com/komgareader/domain/model/UiPackSpecThemeTest.kt`

- [ ] **Step 1: Write the failing test**
```kotlin
package com.komgareader.domain.model

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class UiPackSpecThemeTest {
    private fun spec(theme: ThemeSpec? = null) =
        UiPackSpec(packageName = "p", displayName = "d", abiVersion = 2, theme = theme)

    @Test fun `theme mit farben zaehlt als override`() =
        assertTrue(spec(ThemeSpec(dark = ColorRolesSpec(background = "#15171C"))).hasAnyOverride)

    @Test fun `leerer spec ist kein override`() = assertFalse(spec().hasAnyOverride)

    @Test fun `ThemeSpec hasColors nur bei light oder dark`() {
        assertTrue(ThemeSpec(light = ColorRolesSpec(accent = "#3D5AFE")).hasColors)
        assertFalse(ThemeSpec(cornerRadiusDp = 16).hasColors)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :domain:test --tests "com.komgareader.domain.model.UiPackSpecThemeTest"`
Expected: FAIL — `ThemeSpec`/`ColorRolesSpec` und das `theme`-Feld unbekannt.

- [ ] **Step 3: Implementation**

`ThemeSpec.kt`:
```kotlin
package com.komgareader.domain.model

/**
 * Voller deklarativer Theme-Deskriptor eines externen UI-Packs (Phase 2). **Nur Primitive** (Hex-Strings,
 * Int, Boolean, Float) — keine Compose-/ui-api-Typen, damit `domain`/`data` rein bleiben. Übersetzung in
 * ColorScheme/Shapes/Typo passiert nur in `:app` (`UiPackSpec.toUiPackOrNull`). Jede Sektion optional.
 */
data class ThemeSpec(
    val light: ColorRolesSpec? = null,
    val dark: ColorRolesSpec? = null,
    val cornerRadiusDp: Int? = null,
    val elevation: Boolean? = null,
    val typography: TypoSpec? = null,
) {
    /** true, wenn mindestens ein Farb-Modus geliefert wird (sonst ist die theme-Farbschicht leer). */
    val hasColors: Boolean get() = light != null || dark != null
}

/** Die acht Farb-Rollen, die ein Pack pro Modus setzen darf (genau die, die der In-Tree-AuroraPack setzt).
 *  Alle als `#RRGGBB`-Hex-Strings, alle optional (fehlend → Host-Default des Modus). `navDock` = Bottom-Nav-Fläche. */
data class ColorRolesSpec(
    val background: String? = null,
    val surface: String? = null,
    val navDock: String? = null,
    val accent: String? = null,
    val onAccent: String? = null,
    val onBackground: String? = null,
    val onSurfaceVariant: String? = null,
    val outline: String? = null,
)

/** Typo-Tuning als Daten: Font-Gewichte (100..900) + Headline-Tracking in em. Keine Font-Dateien (YAGNI). */
data class TypoSpec(
    val headlineWeight: Int? = null,
    val titleWeight: Int? = null,
    val headlineTrackingEm: Float? = null,
)
```

In `UiPackSpec.kt`: Feld + hasAnyOverride erweitern:
```kotlin
data class UiPackSpec(
    val packageName: String,
    val displayName: String,
    val abiVersion: Int,
    val navStyle: String? = null,
    val iconRemap: Map<String, String> = emptyMap(),
    val accentHex: String? = null,
    val cornerRadiusDp: Int? = null,
    val theme: ThemeSpec? = null,
) {
    val hasAnyOverride: Boolean
        get() = navStyle != null || iconRemap.isNotEmpty() || accentHex != null ||
            cornerRadiusDp != null || theme != null
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :domain:test --tests "com.komgareader.domain.model.UiPackSpecThemeTest"`
Expected: PASS. Außerdem `./gradlew :domain:test` (volle Domain-Suite) grün.

- [ ] **Step 5: Commit**
```bash
git add domain/src/main/kotlin/com/komgareader/domain/model/ThemeSpec.kt domain/src/main/kotlin/com/komgareader/domain/model/UiPackSpec.kt domain/src/test/kotlin/com/komgareader/domain/model/UiPackSpecThemeTest.kt
git commit -m "feat(ui-pack): domain ThemeSpec — voller Theme-Deskriptor als Primitive"
```

---

## Task 2: data — parseUiPackSpec liest die theme-Sektion

**Files:**
- Modify: `data/src/main/kotlin/com/komgareader/data/plugin/UiPackParser.kt`
- Test: `data/src/test/kotlin/com/komgareader/data/plugin/UiPackParserThemeTest.kt`

> READ `UiPackParser.kt` + die bestehende `UiPackParserTest.kt` zuerst, um Stil/Helfer zu matchen. Die bestehenden `accentHex`/`cornerRadiusDp`-Parses bleiben (Legacy-Packs). Neu: ein `theme`-Objekt mit `light`/`dark`/`elevation`/`typography` → `ThemeSpec` (nur gesetzt, wenn `light` oder `dark` da ist).

- [ ] **Step 1: Write the failing test**
```kotlin
package com.komgareader.data.plugin

import com.komgareader.domain.model.UiPackSpec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class UiPackParserThemeTest {
    private fun parse(json: String): UiPackSpec? = parseUiPackSpec(json, "pkg", "Name", manifestAbi = 2)

    @Test fun `volle theme-sektion light+dark+typo`() {
        val s = parse(
            """{"abiVersion":2,"theme":{"cornerRadius":16,"elevation":true,
               "typography":{"headlineWeight":700,"headlineTrackingEm":-0.02},
               "light":{"background":"#CDD1D9","navDock":"#959CAA","accent":"#3D5AFE"},
               "dark":{"background":"#15171C","surface":"#1C1F26","accent":"#3D5AFE"}}}""",
        )!!
        assertEquals("#CDD1D9", s.theme!!.light!!.background)
        assertEquals("#959CAA", s.theme!!.light!!.navDock)
        assertEquals("#15171C", s.theme!!.dark!!.background)
        assertEquals(16, s.theme!!.cornerRadiusDp)
        assertEquals(true, s.theme!!.elevation)
        assertEquals(700, s.theme!!.typography!!.headlineWeight)
        assertEquals(-0.02f, s.theme!!.typography!!.headlineTrackingEm)
    }

    @Test fun `nur dark - light bleibt null`() {
        val s = parse("""{"abiVersion":2,"theme":{"dark":{"background":"#15171C"}}}""")!!
        assertNull(s.theme!!.light)
        assertEquals("#15171C", s.theme!!.dark!!.background)
    }

    @Test fun `legacy flach - theme bleibt null, accent gesetzt`() {
        val s = parse("""{"abiVersion":2,"theme":{"accent":"#3D5AFE","cornerRadius":4}}""")!!
        assertNull(s.theme)
        assertEquals("#3D5AFE", s.accentHex)
        assertEquals(4, s.cornerRadiusDp)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :data:testDebugUnitTest --tests "com.komgareader.data.plugin.UiPackParserThemeTest"`
Expected: FAIL — `theme` wird noch nicht geparst (`s.theme` ist null in Test 1).

- [ ] **Step 3: Implementation**

In `parseUiPackSpec`, nach dem bestehenden `theme`-Block (der `accentHex`/`cornerRadiusDp` liest), die volle Sektion ergänzen. Helfer für Rollen + Typo, dann `ThemeSpec` nur bauen, wenn `light`/`dark` da:
```kotlin
// (bestehend: accentHex + cornerRadiusDp aus `theme` — bleibt für Legacy-Packs.)

val themeSpec = theme?.let { t ->
    fun roles(key: String): ColorRolesSpec? = t.optJSONObject(key)?.let { o ->
        ColorRolesSpec(
            background = o.optString("background").takeIf { it.isNotBlank() },
            surface = o.optString("surface").takeIf { it.isNotBlank() },
            navDock = o.optString("navDock").takeIf { it.isNotBlank() },
            accent = o.optString("accent").takeIf { it.isNotBlank() },
            onAccent = o.optString("onAccent").takeIf { it.isNotBlank() },
            onBackground = o.optString("onBackground").takeIf { it.isNotBlank() },
            onSurfaceVariant = o.optString("onSurfaceVariant").takeIf { it.isNotBlank() },
            outline = o.optString("outline").takeIf { it.isNotBlank() },
        )
    }
    val light = roles("light"); val dark = roles("dark")
    if (light == null && dark == null) {
        null // reiner Legacy-/Leer-Pack → keine ThemeSpec
    } else {
        val typo = t.optJSONObject("typography")?.let { p ->
            TypoSpec(
                headlineWeight = p.optInt("headlineWeight", Int.MIN_VALUE).takeIf { it != Int.MIN_VALUE },
                titleWeight = p.optInt("titleWeight", Int.MIN_VALUE).takeIf { it != Int.MIN_VALUE },
                headlineTrackingEm = if (p.has("headlineTrackingEm")) p.optDouble("headlineTrackingEm").toFloat() else null,
            )
        }
        ThemeSpec(
            light = light, dark = dark,
            cornerRadiusDp = cornerRadiusDp, // derselbe Wert wie der Legacy-Knopf
            elevation = if (t.has("elevation")) t.optBoolean("elevation") else null,
            typography = typo,
        )
    }
}
```
Und `theme = themeSpec` in den `UiPackSpec(...)`-Konstruktor aufnehmen. Imports: `com.komgareader.domain.model.{ThemeSpec, ColorRolesSpec, TypoSpec}`.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :data:testDebugUnitTest --tests "com.komgareader.data.plugin.UiPackParserThemeTest"`
Expected: PASS. Außerdem die bestehende `UiPackParserTest` weiter grün: `./gradlew :data:testDebugUnitTest --tests "com.komgareader.data.plugin.UiPackParserTest"`.

- [ ] **Step 5: Commit**
```bash
git add data/src/main/kotlin/com/komgareader/data/plugin/UiPackParser.kt data/src/test/kotlin/com/komgareader/data/plugin/UiPackParserThemeTest.kt
git commit -m "feat(ui-pack): parseUiPackSpec liest volle theme-Sektion (light/dark/typo)"
```

---

## Task 3: app — toUiPackOrNull (Runtime-UiPack aus Daten)

**Files:**
- Create: `app/src/main/kotlin/com/komgareader/app/ui/pack/UiPackTheme.kt`
- Test: `app/src/test/kotlin/com/komgareader/app/ui/pack/UiPackThemeTest.kt`

> `UiPack` (ui-api) = `id`, `colorScheme(dark)`, `designTokens(dark)`, `shapes`, `typography`. Hex via vorhandenes `parseHexColor` (in `UiPackApply.kt`, dieselbe Package). **Fehlender Modus mirror't den anderen** (deterministisch, dokumentiert; Single-Mode-Packs rendern wenigstens). Fehlende Einzel-Rolle → Material-Default des Modus.

- [ ] **Step 1: Write the failing test**
```kotlin
package com.komgareader.app.ui.pack

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.komgareader.domain.model.ColorRolesSpec
import com.komgareader.domain.model.ThemeSpec
import com.komgareader.domain.model.UiPackSpec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class UiPackThemeTest {
    private fun spec(theme: ThemeSpec?) =
        UiPackSpec("p", "Aurora", 2, theme = theme)

    @Test fun `kein theme - null`() {
        assertNull(spec(null).toUiPackOrNull())
        assertNull(spec(ThemeSpec(cornerRadiusDp = 16)).toUiPackOrNull()) // keine Farben
    }

    @Test fun `voller pack baut ColorScheme + Shapes + Tokens`() {
        val pack = spec(
            ThemeSpec(
                dark = ColorRolesSpec(background = "#15171C", surface = "#1C1F26", navDock = "#1C1F26", accent = "#3D5AFE", onAccent = "#FFFFFF"),
                light = ColorRolesSpec(background = "#CDD1D9", navDock = "#959CAA", accent = "#3D5AFE"),
                cornerRadiusDp = 16, elevation = true,
            ),
        ).toUiPackOrNull()!!
        val csD = pack.colorScheme(dark = true)
        assertEquals(Color(0xFF15171C), csD.background)
        assertEquals(Color(0xFF1C1F26), csD.surface)
        assertEquals(Color(0xFF1C1F26), csD.surfaceVariant) // navDock → surfaceVariant
        assertEquals(Color(0xFF3D5AFE), csD.primary)        // accent → primary
        assertEquals(Color(0xFF959CAA), pack.colorScheme(dark = false).surfaceVariant)
        assertEquals(RoundedCornerShape(16.dp), pack.shapes.medium)
        val t = pack.designTokens(dark = true)
        assertEquals(Color(0xFF3D5AFE), t.accent)
        assertEquals(16.dp, t.cornerRadius)
        assertTrue(t.usesShadows)
    }

    @Test fun `nur-dark spec mirror't in light`() {
        val pack = spec(ThemeSpec(dark = ColorRolesSpec(background = "#15171C", accent = "#3D5AFE"))).toUiPackOrNull()!!
        assertEquals(Color(0xFF15171C), pack.colorScheme(dark = false).background) // light mirror't dark
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.komgareader.app.ui.pack.UiPackThemeTest"`
Expected: FAIL — `toUiPackOrNull` unbekannt.

- [ ] **Step 3: Implementation**

`UiPackTheme.kt`:
```kotlin
package com.komgareader.app.ui.pack

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import com.komgareader.domain.model.ColorRolesSpec
import com.komgareader.domain.model.ThemeSpec
import com.komgareader.domain.model.UiPackSpec
import com.komgareader.ui.theme.DesignTokens
import com.komgareader.ui.theme.UiPack

/**
 * Übersetzt die deklarative [ThemeSpec] (domain, Primitive) in einen Runtime-[UiPack] — **nur hier in `:app`**
 * (domain/data bleiben Compose-frei). `null`, wenn der Pack keine Farb-Sektion liefert (dann greift weiter der
 * `tokenOverride`-Pfad für reine accent/cornerRadius-Packs). Fehlender Modus mirror't den anderen; fehlende
 * Einzel-Rolle bleibt Material-Default. E-Ink-Gate (Pack nur auf LCD) liegt host-seitig in `KomgaReaderTheme`.
 */
fun UiPackSpec.toUiPackOrNull(): UiPack? {
    val t = theme?.takeIf { it.hasColors } ?: return null
    val darkRoles = t.dark ?: t.light!!   // mirror
    val lightRoles = t.light ?: t.dark!!  // mirror
    val radius = (t.cornerRadiusDp ?: 16).coerceIn(0, 32).dp
    val shadows = t.elevation ?: true
    val shapes = Shapes(
        small = RoundedCornerShape((radius.value - 4).coerceAtLeast(0f).dp),
        medium = RoundedCornerShape(radius),
        large = RoundedCornerShape((radius.value + 4).dp),
    )
    val typography = buildTypography(t)
    return object : UiPack {
        override val id = "external:$packageName"
        override fun colorScheme(dark: Boolean) = schemeOf(if (dark) darkRoles else lightRoles, dark)
        override fun designTokens(dark: Boolean): DesignTokens {
            val roles = if (dark) darkRoles else lightRoles
            return DesignTokens(
                accent = roles.accent.hexOr(Color(0xFF3D5AFE)),
                onAccent = roles.onAccent.hexOr(Color.White),
                usesShadows = shadows,
                cardElevation = if (shadows) 3.dp else 0.dp,
                cornerRadius = radius,
            )
        }
        override val shapes = shapes
        override val typography = typography
    }
}

private fun schemeOf(r: ColorRolesSpec, dark: Boolean): ColorScheme {
    val base = if (dark) darkColorScheme() else lightColorScheme()
    val accent = r.accent.hexOr(base.primary)
    val onAccent = r.onAccent.hexOr(base.onPrimary)
    val onBg = r.onBackground.hexOr(base.onBackground)
    return base.copy(
        primary = accent, onPrimary = onAccent,
        background = r.background.hexOr(base.background), onBackground = onBg,
        surface = r.surface.hexOr(r.background.hexOr(base.surface)), onSurface = onBg,
        surfaceVariant = r.navDock.hexOr(base.surfaceVariant),
        onSurfaceVariant = r.onSurfaceVariant.hexOr(base.onSurfaceVariant),
        outline = r.outline.hexOr(base.outline),
    )
}

private fun buildTypography(t: ThemeSpec): Typography {
    val base = Typography()
    val hw = t.typography?.headlineWeight?.let { FontWeight(it.coerceIn(100, 900)) }
    val tw = t.typography?.titleWeight?.let { FontWeight(it.coerceIn(100, 900)) }
    val track = t.typography?.headlineTrackingEm
    return base.copy(
        headlineSmall = base.headlineSmall.copy(
            fontWeight = hw ?: base.headlineSmall.fontWeight,
            letterSpacing = track?.let { (it * 16).sp } ?: base.headlineSmall.letterSpacing,
        ),
        titleLarge = base.titleLarge.copy(fontWeight = tw ?: base.titleLarge.fontWeight),
    )
}

/** Parst `#RRGGBB` über das vorhandene [parseHexColor]; bei null/ungültig der [fallback]. */
private fun String?.hexOr(fallback: Color): Color = this?.let(::parseHexColor) ?: fallback
```
> `parseHexColor` ist `internal`/dieselbe Package in `UiPackApply.kt` — falls `private`, dort auf `internal` heben (1 Wort) und im Commit erwähnen.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.komgareader.app.ui.pack.UiPackThemeTest"`
Expected: PASS.

- [ ] **Step 5: Commit**
```bash
git add app/src/main/kotlin/com/komgareader/app/ui/pack/UiPackTheme.kt app/src/test/kotlin/com/komgareader/app/ui/pack/UiPackThemeTest.kt app/src/main/kotlin/com/komgareader/app/ui/pack/UiPackApply.kt
git commit -m "feat(ui-pack): toUiPackOrNull — Runtime-UiPack aus deklarativer ThemeSpec"
```

---

## Task 4: Host-Anwendung (KomgaReaderTheme + MainActivity)

**Files:**
- Modify: `app/src/main/kotlin/com/komgareader/app/ui/theme/Theme.kt`
- Modify: `app/src/main/kotlin/com/komgareader/app/MainActivity.kt`

> Wiring; verifiziert über Emulator (Task 7). Kein neuer Unit-Test (die Bau-Logik ist Task 3, das Gate ist eine triviale `if`-Zeile).

- [ ] **Step 1: KomgaReaderTheme — externalPack-Parameter + Gate**

In `Theme.kt`: Parameter ergänzen und den Pack ersetzen:
```kotlin
fun KomgaReaderTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    slotPack: UiSlotPack = UiSlotPack(),
    tokenOverride: TokenOverride? = null,
    externalPack: UiPack? = null,
    content: @Composable () -> Unit,
) {
```
Und die Pack-Auflösung (~Z.37):
```kotlin
val devicePack = UiPackRegistry.forBehavior(behavior)
// Externer Voll-Theme-Pack (Phase 2) ersetzt den Geräteklassen-Pack — NUR wenn die Geräteklasse Farbe
// erlaubt (mono/Kaleido E-Ink ignorieren ihn → bleiben S/W). Invariant-neutrale Teile (Radius/Typo) kämen
// ohnehin nur über diesen Pfad, also ganz oder gar nicht.
val pack = if (behavior.allowsAccentColor) externalPack ?: devicePack else devicePack
```
Import `com.komgareader.ui.theme.UiPack` ergänzen. Der bestehende `tokenOverride`-Block (patcht `pack.designTokens(dark)`) bleibt unverändert — für reine accent/cornerRadius-Packs (deren `externalPack` ist null).

- [ ] **Step 2: MainActivity — externalPack aus dem aktiven Spec**

Neben dem vorhandenen `tokenOverride` (≈ Z.127):
```kotlin
val externalPack = remember(activeUiPack) { activeUiPack?.toUiPackOrNull() }
```
und an den Aufruf:
```kotlin
KomgaReaderTheme(themeMode = themeMode, slotPack = slotPack, tokenOverride = tokenOverride, externalPack = externalPack) {
```
Imports: `com.komgareader.app.ui.pack.toUiPackOrNull`. (`activeUiPack` ist der schon aufgelöste `UiPackSpec?`.)

- [ ] **Step 3: Build**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**
```bash
git add app/src/main/kotlin/com/komgareader/app/ui/theme/Theme.kt app/src/main/kotlin/com/komgareader/app/MainActivity.kt
git commit -m "feat(ui-pack): Host wendet externen Voll-Theme-Pack an (E-Ink-gegated)"
```

---

## Task 5: Doku — docs/ui-packs/

**Files:**
- Create: `docs/ui-packs/README.md`

> Pflicht-Deliverable. Zielgruppe Agenten + Menschen, mit vollständigem Beispiel + Walkthrough.

- [ ] **Step 1: README schreiben** mit diesen Abschnitten:
  1. **Was ein UI-Pack ist** (data-only APK, kein Code, deklarativ; was NICHT geht: kein Compose, keine Font-Dateien, E-Ink ignoriert Farben).
  2. **Manifest** (verbatim Block: `DATA_CATEGORY=UI_PACK`, `DATA_ASSET=ui_pack.json`, `ABI_VERSION=2`, `android:hasCode="false"`).
  3. **Schema-Referenz** — Tabelle je Sektion/Feld: `shell.navStyle` (BOTTOM_BAR/DRAWER/FLOATING_NAV), `icons` (IconKey→IconKey), `theme` (cornerRadius/elevation/typography + light/dark mit den 8 Rollen background/surface/navDock/accent/onAccent/onBackground/onSurfaceVariant/outline). Default-Verhalten je „fehlt".
  4. **Vollständiges Aurora-Beispiel** `ui_pack.json` (genau das aus Task 6).
  5. **Walkthrough** „So baust du einen UI-Pack": Modul anlegen (Template `plugin/komga-ui-pack-sample`), Asset, `./gradlew assembleDebug`, Fingerprint (`keytool`/`apksigner`), Repo-Eintrag, Install über den In-App-Repo-Browser, Aktivieren im „UI-Pack"-Picker (Darstellung).
  6. **E-Ink-Hinweis** (Akzent/Farbe nur im Smartphone-Modus; Shapes/Typo/navStyle immer).

- [ ] **Step 2: KDoc** an `ThemeSpec`/`ColorRolesSpec` (domain), `parseUiPackSpec` (neue Felder), `toUiPackOrNull` (sind in Task 1–3 schon gesetzt — hier nur prüfen, dass sie das Schema benennen).

- [ ] **Step 3: Commit**
```bash
git add docs/ui-packs/README.md
git commit -m "docs(ui-pack): Schema-Referenz + Aurora-Beispiel + Pack-Bau-Walkthrough"
```

---

## Task 6: Externes Aurora-Daten-APK + Repo-Eintrag

**Files:**
- Create: `plugin/komga-ui-pack-aurora/` (Modul, Template `plugin/komga-ui-pack-sample/`)
- Modify: `~/Documents/Projekte/KomgaReaderPlugins/repo.json` (Distributions-Repo, separates Git-Repo)

> `plugin/` ist im App-Repo **gitignored** (separates Repo) — also wird hier **nichts im App-Repo committet**; nur das Distributions-Repo bekommt einen Commit. Der 1→3-Beweis.

- [ ] **Step 1: Modul anlegen** — `plugin/komga-ui-pack-sample/` als Vorlage kopieren nach `plugin/komga-ui-pack-aurora/`. Ändern: `namespace`/`applicationId` = `com.komgareader.uipack.aurora`, `android:label="Aurora — Modern Mobile"`.

- [ ] **Step 2: Asset** `plugin/komga-ui-pack-aurora/app/src/main/assets/ui_pack.json`:
```json
{
  "abiVersion": 2,
  "shell": { "navStyle": "FLOATING_NAV" },
  "theme": {
    "cornerRadius": 16,
    "elevation": true,
    "typography": { "headlineWeight": 700, "titleWeight": 700, "headlineTrackingEm": -0.02 },
    "light": {
      "background": "#CDD1D9", "surface": "#C3C8D1", "navDock": "#959CAA",
      "accent": "#3D5AFE", "onAccent": "#FFFFFF",
      "onBackground": "#1A1D24", "onSurfaceVariant": "#3F4450", "outline": "#B1B7C2"
    },
    "dark": {
      "background": "#15171C", "surface": "#1C1F26", "navDock": "#1C1F26",
      "accent": "#3D5AFE", "onAccent": "#FFFFFF",
      "onBackground": "#E9EAEE", "onSurfaceVariant": "#9296A0", "outline": "#2E313A"
    }
  }
}
```

- [ ] **Step 3: Bauen + Fingerprint**

Run (im Plugin-Modul): `cd plugin/komga-ui-pack-aurora && ./gradlew assembleDebug`
Dann Cert-SHA-256: `apksigner verify --print-certs app/build/outputs/apk/debug/app-debug.apk | grep -i "SHA-256"` (oder `keytool -printcert -jarfile ...`). Notiere den Fingerprint (Debug-Keystore → identisch zu den anderen Plugins; gegen `repo.json` der bestehenden Einträge gegenprüfen).

- [ ] **Step 4: Ins Distributions-Repo** — APK nach `~/Documents/Projekte/KomgaReaderPlugins/plugins/komga-ui-pack-aurora-0.1.0.apk` kopieren, Eintrag in `repo.json`:
```json
{ "packageName": "com.komgareader.uipack.aurora", "name": "Aurora — Modern Mobile", "description": "Moderner Mobile-Look (dark+light, Cobalt) als deklaratives UI-Pack", "type": "ui_pack", "abiVersion": 2, "versionCode": 1, "versionName": "0.1.0", "apkUrl": "plugins/komga-ui-pack-aurora-0.1.0.apk", "fingerprint": "<aus Step 3>" }
```
Im Distributions-Repo committen: `git -C ~/Documents/Projekte/KomgaReaderPlugins add -A && git -C ~/Documents/Projekte/KomgaReaderPlugins commit -m "feat: Aurora UI-Pack (ui_pack, ABI 2)"`.

- [ ] **Step 5:** Kein App-Repo-Commit (plugin/ gitignored). Notiere im Task-Log, dass nur das Distributions-Repo geändert wurde.

---

## Task 7: Tests + Emulator-E2E

**Files:** keine (Verifikation).

- [ ] **Step 1: Volle Unit-Suite**

Run: `./gradlew :domain:test :data:testDebugUnitTest :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: App + Aurora-APK installieren**

`./gradlew :app:assembleDebug` → `adb install -r app/build/outputs/apk/debug/app-debug.apk`.
Aurora-Daten-APK installieren: `adb install -r plugin/komga-ui-pack-aurora/app/build/outputs/apk/debug/app-debug.apk`.

- [ ] **Step 3: Pack aktivieren + Look prüfen (Smartphone-Modus)**

Smartphone-Modus + den Aurora-**Daten**-Pack aktiv setzen (Picker „UI-Pack" in Darstellung; oder direkt via DB: `adb shell "run-as com.komgareader.app sqlite3 databases/komga-reader.db \"INSERT OR REPLACE INTO settings(key,value) VALUES('display_mode','SMARTPHONE'),('active_ui_pack','com.komgareader.uipack.aurora');\""`, App neu starten). Erwartet: **identischer Aurora-Look** wie der In-Tree-Pack (dark+light) — Slate/Deeper-Grey, Cobalt, Floating-Nav, Card-Tiles. Screenshot dark + light.

- [ ] **Step 4: E-Ink-Regression** — `display_mode=EINK`: der externe Farb-Pack wird ignoriert (mono S/W, Default-Look). Screenshot.

- [ ] **Step 5:** Settings sauber zurücksetzen (`active_ui_pack=''`, `display_mode=EINK`). Commit nur, falls Verifikation Fixes nötig machte.

---

## Task 8: docs-match-code

**Files:**
- Modify: `.claude/rules/architecture-seams.md`, `.claude/rules/big-picture-and-goals.md`

- [ ] **Step 1:** Je einen knappen Ist-Absatz (Datum 2026-06-13): das `ui_pack.json`-`theme` trägt jetzt den **vollen Look als Daten** (light/dark-Rollen + Radius/Elevation/Typo); `ThemeSpec` (domain, Primitive) → `parseUiPackSpec` (data) → `toUiPackOrNull` (app, Runtime-`UiPack`) → `KomgaReaderTheme` ersetzt den Geräteklassen-Pack **host-gegated** (`allowsAccentColor`). Externes Aurora-Daten-APK reproduziert den In-Tree-Look (1→3-Beweis). **L2-„theme = nur accent/cornerRadius"** ist damit überholt — Stelle aktualisieren. „Phase 2 bleibt Soll" → „Phase 2 gebaut".

- [ ] **Step 2: Commit**
```bash
git add .claude/rules/architecture-seams.md .claude/rules/big-picture-and-goals.md
git commit -m "docs(ui-pack): docs-match-code — deklaratives Voll-Theme (Phase 2, Ist)"
```

---

## Self-Review (vom Plan-Autor)

**Spec-Abdeckung:** §2 Schema → Task 1 (domain) + 2 (parse). §3 Reinheit → Task 1/2/3 (domain/data/app-Schnitt). §4 Host → Task 4. §5 Doku → Task 5. §6 externes APK → Task 6. §7 Tests → Task 1–3 (unit) + 7 (E2E). docs-match-code → Task 8. §8 YAGNI gewahrt (kein navRadius, keine Fonts, nur 8 Rollen). §9-Detailpunkte aufgelöst: Material-Rollen aus `base.copy` (konservativ) + `externalPack` als neuer Parameter.

**Platzhalter:** keiner — jeder Code-Schritt trägt vollständigen Code.

**Typ-Konsistenz:** `ThemeSpec`/`ColorRolesSpec`/`TypoSpec`/`UiPackSpec.theme`/`toUiPackOrNull`/`externalPack`/`parseHexColor` durchgängig identisch. `navDock`→`surfaceVariant`-Mapping in Task 3 (Konverter) und Task 6 (Beispiel) konsistent, und `FloatingNavBar` liest `surfaceVariant` (Phase 1) — der Pack-`navDock` trifft also die Nav-Fläche.

**Offene Verifikationspunkte (im Bau):** `parseHexColor`-Sichtbarkeit (Task 3, ggf. `internal`); exaktes `activeUiPack`-Symbol in MainActivity; Fingerprint des Debug-Keystores (Task 6, gegen bestehende repo.json-Einträge).
```
