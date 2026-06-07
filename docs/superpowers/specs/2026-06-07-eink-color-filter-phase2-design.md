# Design: E-Ink-Farbfilter Phase 2 — Pixel-Pipeline

**Datum:** 2026-06-07
**Branch:** `feat/eink-color-filter`
**Status:** Design genehmigt, Implementierung ausstehend
**Baut auf:** `2026-06-06-eink-color-filter-design.md` (Phase 1, fertig)

## Zweck

Phase 1 gleicht den Kaleido-3-Farbverlust mit einer linearen 4×5-ColorMatrix aus
(Sättigung/Kontrast/Helligkeit) — als Compose-`colorFilter` zur Zeichenzeit, GPU-billig,
auf jedem Bild. Das deckt ~80 % ab, kann aber prinzipbedingt keine nicht-linearen oder
nachbarschaftsbasierten Operationen.

Phase 2 ergänzt vier bewusst rechen-/akku-intensivere Operationen für sichtbar bessere
Wiedergabe auf dem Reader-Bild:

- **Gamma-Korrektur** — nicht-lineare Mittelton-Anhebung (Kaleido wirkt flau/dunkel).
- **Tonwert-Korrektur (Levels)** — Schwarzpunkt/Weißpunkt strecken den Dynamikumfang.
- **Unsharp-Mask** — Kantenschärfung gegen den Weichzeichn-Effekt des CFA.
- **Dithering** — Fehlerdiffusion/Ordered gegen Banding und CFA-Raster.

Ziel des Features ist **Evaluierung**: der Nutzer will sehen, ob das Ergebnis den
zusätzlichen Akku-Verbrauch rechtfertigt. Daher: voll abschaltbar, neutral als Default,
nur dort teuer, wo es zählt (beim Lesen), nie beim Stöbern.

## Leitprinzip — Stufen kontextabhängig überspringbar

Die teure Pixel-Verarbeitung darf **niemals** das Scrollen durch die Bibliothek bremsen.
Drei Kontexte, drei Tiefen:

| Kontext | Angewandte Stufen | Mechanik |
|---|---|---|
| **Stöbern (Cover in Lib/Detail)** | nur linear (Sat/Kontrast/Helligkeit) | GPU-`colorFilter` (Phase 1, unverändert) |
| **Reader (Paged/Webtoon/EPUB)** | volle Pipeline (linear → Levels → Gamma → Unsharp → Dither) | Pixel-Kernel am Bitmap |
| **Settings-Vorschau-Cover** | volle Pipeline (Demo) | Pixel-Kernel am kleinen Bitmap, live |

Eine **neutrale** Stufe (z. B. `gamma==1`) wird im Kernel übersprungen (Identität) — die
Pipeline kostet nur, was wirklich gesetzt ist. Cover laufen nie durch den Kernel.

## Architektur — eine neue Naht, dieselben Wrapper

Phase 1 dockt als GPU-`colorFilter` an `FilteredAsyncImage`/`FilteredImage`. Phase 2 kann
das nicht (Gamma/Unsharp/Dither sind nicht als 4×5-Matrix darstellbar) und braucht echte
Pixel. Lösung: ein **reiner Pixel-Kernel** in `domain` (kein Android-/Compose-Import),
der ein ARGB-`IntArray` in-place verarbeitet. Darüber zwei Andock-Punkte im `app`-Modul.

```
ColorProfile ── needsPixelPipeline? ──────┐
   nein → toColorFilterOrNull() (Matrix)   │  ja → applyPixelPipeline(IntArray, w, h, profile)
        ↓ GPU                               ↓ CPU
   Cover (Lib/Detail) — immer billig   Reader-Seiten (alle 3 Viewer):
                                        · Coil-Transformation  (Komga-Streaming-Seiten, AsyncImage)
                                        · Bitmap-Post-Process  (MuPDF EPUB/PDF, FilteredImage)
                                        · Settings-Vorschau    (Demo am Beispiel-Cover)
```

- **Cover-Pfad bleibt exakt wie Phase 1:** `FilteredAsyncImage` an Cover-Stellen liest
  `LocalImageFilter` (GPU-Matrix). Kein Eingriff, kein Kernel — Scrollen unverändert schnell.
- **Reader-Pfad entscheidet:** wenn `profile.needsPixelPipeline`, wird das Seiten-Bitmap durch
  den Kernel geschickt (inkl. linearer Stufe, ein Pass) und mit `colorFilter = null` gerendert.
  Sonst GPU-Matrix wie bisher. **Kein Doppelanwenden.**
- **Kernel ist die einzige Quelle der Wahrheit** für die volle Reihenfolge. Der GPU-Matrix-Pfad
  und die lineare Stufe des Kernels nutzen dieselbe `buildColorMatrix`-Mathematik (DRY).

### Reader-Seiten-Wrapper (neuer Eintrittspunkt)

Die bestehenden Wrapper bekommen Reader-Varianten, die in die Pixel-Pipeline opten:

- `FilteredReaderImage(bitmap, …)` — MuPDF-Bitmap (EPUB/PDF). Wenn `needsPixelPipeline`:
  Bitmap kopieren → `getPixels` → `applyPixelPipeline` → `setPixels` → ungefiltert rendern.
  Sonst wie `FilteredImage` (GPU-Matrix).
- Coil-Seiten (Paged/Webtoon streamen Komga-fertige Seitenbilder via Coil): eine Coil
  `Transformation` (`ColorPipelineTransformation`), die denselben Kernel auf das dekodierte
  Bitmap anwendet, **keyed** auf die Profil-Werte (Coil-Cache-Key → einmal rechnen pro
  Bild+Profil, danach Cache). Wird nur an Reader-Seiten-Requests gehängt, nicht an Cover.

Cover-Stellen bleiben auf `FilteredAsyncImage`/`FilteredImage` (nur GPU-Matrix).

## Pixel-Kernel — `domain/color/PixelPipeline.kt` (pure Kotlin)

```kotlin
/**
 * Verarbeitet ARGB-Pixel in-place mit der vollen Filter-Pipeline. Reihenfolge:
 * linear (Sat→Kontrast→Helligkeit) → Levels → Gamma → Unsharp-Mask → Dithering.
 * Neutrale Stufen werden übersprungen. Pure Kotlin, testbar ohne Android.
 */
fun applyPixelPipeline(pixels: IntArray, width: Int, height: Int, profile: ColorProfile)
```

**Stufen (in dieser Reihenfolge):**

1. **Linear** — pro Pixel die `buildColorMatrix(sat, contrast, brightness)` anwenden
   (RGB getrennt, geklemmt 0..255). Identisch zur GPU-Matrix, nur auf der CPU. Übersprungen,
   wenn linearer Teil neutral.
2. **Levels** — Schwarzpunkt `bp` (0..0.4), Weißpunkt `wp` (0.6..1): `out = (in − bp·255) / ((wp−bp)·255) · 255`, geklemmt. Pro Kanal. Übersprungen bei `bp==0 && wp==1`.
3. **Gamma** — `out = 255·(in/255)^(1/gamma)` als vorab gebaute 256-Eintrag-LUT (einmal pro
   Profil-Anwendung, dann nur Lookup). Übersprungen bei `gamma==1`.
4. **Unsharp-Mask** — Box-Blur mit Radius `r` (1..3) auf eine Bitmap-Kopie, dann
   `out = clamp(in + amount·(in − blur))`. Übersprungen bei `amount==0`. Akku-intensivste Stufe.
5. **Dithering** — zuletzt (näher am Display). `ditherMode`:
   - `FLOYD_STEINBERG` — Fehlerdiffusion (sequentiell), quantisiert auf `ditherLevels` Stufen/Kanal.
   - `ORDERED` — Bayer-4×4-Schwellenmatrix (parallelisierbar, billiger), quantisiert auf `ditherLevels`.
   - `NONE` — übersprungen.

Hilfsfunktion `buildGammaLut(gamma): IntArray` (256) separat + testbar.

## Datenmodell — `ColorProfile` wächst (neutrale Defaults)

`domain/model/ColorProfile.kt` bekommt neue Felder mit neutralen Defaults — bestehende
Profile (auch Custom in der DB) bleiben dadurch unverändert:

```kotlin
enum class DitherMode { NONE, FLOYD_STEINBERG, ORDERED }

data class ColorProfile(
    val id: Long,
    val name: String,
    val saturation: Float,
    val contrast: Float,
    val brightness: Float,
    // Phase 2 — alle neutral:
    val blackPoint: Float = 0f,       // 0.0..0.4
    val whitePoint: Float = 1f,       // 0.6..1.0
    val gamma: Float = 1f,            // 0.4..2.5
    val sharpenAmount: Float = 0f,    // 0.0..2.0
    val sharpenRadius: Int = 1,       // 1..3 (px)
    val ditherMode: DitherMode = DitherMode.NONE,
    val ditherLevels: Int = 16,       // 2..64, nur wirksam bei ditherMode != NONE
    val builtIn: Boolean,
) {
    val isLinearNeutral: Boolean get() = saturation == 1f && contrast == 1f && brightness == 0f
    val needsPixelPipeline: Boolean get() =
        blackPoint > 0f || whitePoint < 1f || gamma != 1f ||
        sharpenAmount > 0f || ditherMode != DitherMode.NONE
    /** True, wenn das Profil gar nichts verändert (kein Filter nötig). */
    val isNeutral: Boolean get() = isLinearNeutral && !needsPixelPipeline
}
```

`toColorFilterOrNull()` (GPU-Pfad, Cover) basiert weiterhin **nur** auf dem linearen Teil
(`isLinearNeutral`) — die Phase-2-Felder berührt der Cover-Pfad nie.

## Persistenz (Room v7 → v8)

- **`ALTER TABLE color_profiles`** um die neuen Spalten mit neutralen Defaults:
  `black_point REAL NOT NULL DEFAULT 0`, `white_point REAL NOT NULL DEFAULT 1`,
  `gamma REAL NOT NULL DEFAULT 1`, `sharpen_amount REAL NOT NULL DEFAULT 0`,
  `sharpen_radius INTEGER NOT NULL DEFAULT 1`, `dither_mode TEXT NOT NULL DEFAULT 'NONE'`,
  `dither_levels INTEGER NOT NULL DEFAULT 16`. Bestehende Zeilen werden automatisch neutral.
- **Neues Built-in seeden:** „Boox Go Color 7 — Voll" mit moderat getunten Werten
  (Vorschlag: `saturation=1.4, contrast=1.15, brightness=0.05, blackPoint=0.05, whitePoint=0.95,
  gamma=1.2, sharpenAmount=0.6, sharpenRadius=1, ditherMode=NONE`). Dither bewusst aus im
  Default — der Nutzer schaltet es zum Experimentieren selbst zu. Aktiv-Pointer **nicht**
  umgestellt (bestehende Wahl des Nutzers bleibt).
- `ColorProfileEntity` + `SEED_CALLBACK.onCreate` (Frisch-Installation) **und** Migration
  (Upgrade) müssen beide das neue Built-in + die neuen Spalten kennen — dieselbe Dual-Seeding-
  Falle wie Phase 1 beachten.

## UI — Editor um „Erweitert"-Sektion ergänzen

`ColorFilterSettingsContent.kt`: die bestehenden drei `CompactStepperRow` (Sättigung/Kontrast/
Helligkeit) bleiben. Darunter neuer Block, E-Ink-konform (flach, 1.5dp-Border, keine Animation,
beschriftete Stepper):

1. **SectionHeader „Erweitert"** (i18n).
2. **Stepper:** Schwarzpunkt, Weißpunkt, Gamma, Schärfe, Schärfe-Radius (`CompactStepperRow`,
   Schrittweiten je sinnvoll: Levels/Gamma 0.05, Schärfe 0.1, Radius 1).
3. **Dither:** eine `ChoiceRow`-artige Auswahl (Aus / Floyd-Steinberg / Ordered) + Stufen-Stepper,
   der nur sichtbar/aktiv ist, wenn Mode ≠ Aus.
4. **Hinweiszeile** (bodySmall, `onSurfaceVariant`): „Wirkt nur beim Lesen, nicht auf Bibliotheks-
   Cover. Erhöht den Akku-Verbrauch." (i18n, DE+EN).

**Live-Vorschau:** das Vorschau-Cover zeigt den **vollen** Pipeline-Effekt, sofort bei jeder
Stepper-Änderung neu berechnet (Kernel auf das ~240dp-Bitmap — klein genug für flüssiges
Feedback, auch mit Floyd-Steinberg). Die Vorschau übersteuert lokal mit den Editor-Werten
(wie Phase 1), ruft aber den Kernel statt nur den GPU-Filter.

Editor-Logik im `ColorFilterViewModel`: `EditState` wächst um die neuen Felder + Update-
Funktionen (geklemmt auf die Bereiche). `beginNewProfile`/`saveAsNew`/`updateExisting`
übernehmen die neuen Felder.

## i18n (DE + EN, echte Umlaute, Compile-Zeit-Parität)

Neue Keys: `colorFilterAdvanced`, `colorFilterBlackPoint`, `colorFilterWhitePoint`,
`colorFilterGamma`, `colorFilterSharpen`, `colorFilterSharpenRadius`, `colorFilterDither`,
`colorFilterDitherNone`, `colorFilterDitherFloyd`, `colorFilterDitherOrdered`,
`colorFilterDitherLevels`, `colorFilterReaderOnlyHint`. Built-in-Name „Boox Go Color 7 — Voll"
(Eigenname, nicht übersetzt; „Voll"/„Full" lokalisiert wo sinnvoll).

## Tests

**Unit (TDD, zuerst — pure Kotlin):**
- `buildGammaLut`: Stützpunkte (Evident-Data) — `lut[0]==0`, `lut[255]==255`, `gamma=1` →
  Identität (`lut[i]==i`), `gamma>1` hebt Mitteltöne (`lut[128] > 128`).
- `applyPixelPipeline`: Identität bei neutralem Profil (Pixel unverändert); Levels-Clipping
  (Werte unter Schwarzpunkt → 0, über Weißpunkt → 255); linearer Teil stimmt mit
  `buildColorMatrix` überein; Unsharp erhöht lokalen Kontrast an einer Kante; Floyd-Steinberg
  erhält die mittlere Helligkeit eines Graufelds (Summe ≈ vorher, ± Quantum); Ordered ist
  deterministisch (gleicher Input → gleicher Output).
- `needsPixelPipeline`/`isNeutral`/`isLinearNeutral`: korrekt für neutrale und gesetzte Felder.
- `RoomColorProfileRepository`: CRUD inkl. neuer Felder (round-trip durch DB).
- Migration v7 → v8: Spalten existieren mit neutralen Defaults; bestehende Zeile bleibt neutral;
  neues Built-in „Boox Go Color 7 — Voll" vorhanden; Aktiv-Pointer unverändert.

**E2E / sichtbar (Invariante 5):**
- Profil mit Phase-2-Werten aktiv → Reader-Seite (Paged + EPUB) sichtbar verarbeitet,
  Bibliotheks-Cover **unverändert** linear (Beweis, dass Cover die Pipeline nicht durchlaufen).
- Settings-Vorschau zeigt vollen Effekt live beim Justieren.
- Screenshot Emulator `eink_test` (1264×1680@300); ideal echte Boox per USB für Kaleido-Eindruck
  und Akku-/Latenz-Realeindruck.

## Provenance / Lizenz

Keine neuen externen Datenquellen. Alle Algorithmen (Levels, Gamma-LUT, Box-Blur/Unsharp,
Floyd-Steinberg, Bayer-Ordered) sind selbst implementiert, Public-Domain-Standardverfahren.
Lizenz unverändert AGPL-3.0-or-later.

## Berührte/neue Dateien (Überblick)

**Neu:**
- `domain/color/PixelPipeline.kt` (`applyPixelPipeline`, `buildGammaLut`) + Test
- `domain/model/DitherMode.kt` (enum)
- `app/.../ui/components/FilteredReaderImage.kt` (Reader-Bitmap-Wrapper mit Kernel)
- `app/.../color/ColorPipelineTransformation.kt` (Coil-Transformation für Reader-Seiten)

**Geändert:**
- `domain/model/ColorProfile.kt` — neue Felder + `needsPixelPipeline`/`isLinearNeutral`
- `data/.../db/ColorProfileEntity.kt`, `ColorProfileDao.kt` — neue Spalten
- `data/.../db/AppDatabase.kt` — v7→v8 + `MIGRATION_7_8` + Seed neues Built-in + SEED_CALLBACK
- `data/.../repository/RoomColorProfileRepository.kt` — Mapping neue Felder
- `app/.../ui/settings/ColorFilterSettingsContent.kt` — „Erweitert"-Sektion + Dither-Auswahl
- `app/.../ui/settings/ColorFilterViewModel.kt` — `EditState` + Update-/Save-Funktionen
- Reader-Seiten-Aufrufstellen (Paged/Webtoon Coil-Requests, EPUB `FilteredImage`→`FilteredReaderImage`)
- `app/.../i18n/Strings.kt` — neue Keys (DE+EN)
```
