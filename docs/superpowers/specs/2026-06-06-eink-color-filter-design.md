# Design: Zentraler E-Ink-Farbfilter

**Datum:** 2026-06-06
**Branch:** `feat/eink-color-filter`
**Status:** Design genehmigt, Implementierung ausstehend

## Zweck

Alle in der App angezeigten Bilder (Cover in Bibliothek/Serien-Detail **und** Reader-Seiten
in allen drei Viewern) zentral mit einem Farbfilter versehen, damit Bilder auf dem
niedrig aufgelösten Kaleido-3-E-Ink-Display (Onyx Boox Go Color 7 Gen2) möglichst
originalgetreu und mit Rücksicht auf dessen Farbcharakter ankommen.

Hintergrund Kaleido 3: Color-Filter-Array (CFA) über monochromem Panel → gedämpfte
Sättigung, kleiner Gamut, dunkleres Weiß, reduzierter Dynamikumfang, effektiv 150 PPI im
Farbmodus. Gegenmaßnahme: Sättigung anheben (gleicht CFA-Verlust aus), Kontrast leicht
hoch, Helligkeit/Mitteltöne anheben.

## Umfang (Phasen)

**Phase 1 (dieser Spec): ColorMatrix.** Sättigung, Kontrast, Helligkeit (linear) als
4×5-ColorMatrix. Deckt ~80 % des Kaleido-Ausgleichs. GPU-billig, live, keine Bitmap-Kopie.

**Phase 2 (später, nicht hier): Pixel-Pipeline.** Gamma/Tonwertkurve (echte Mittelton-Anhebung),
Unsharp-Mask, Floyd-Steinberg/Ordered-Dithering gegen CFA-Raster. Klinkt sich hinter dieselbe
Naht ein (Bitmap-Post-Process), `ColorProfile`-Modell wächst per Default-Feldern. Wird hier
nur durch erweiterbare Schnittstellen vorbereitet, **nicht implementiert** (YAGNI).

## Architektur — eine Naht

ColorMatrix wird als Compose-`colorFilter`-Parameter angewandt — existiert identisch auf
`AsyncImage` (Coil, Cover + Paged + Webtoon) **und** `Image` (MuPDF-Bitmap, EPUB/PDF).
Daher in Phase 1: **kein Coil-Interceptor, keine Bitmap-Kopie, kein MuPDF-Eingriff.**

```
KomgaReaderTheme / App-Root
  └─ CompositionLocal  LocalImageFilter: ColorFilter?      (aus aktivem ColorProfile)
       ↓ liest
  FilteredAsyncImage  /  FilteredImage    (dünne Wrapper = einzige Bild-Eintrittspunkte)
       ↓ colorFilter = LocalImageFilter.current
  LibraryScreen · SeriesDetailScreen · PagedReaderScreen · WebtoonReaderScreen · EpubReaderScreen
```

- **Wrapper-Prinzip:** Alle 5 bestehenden Bild-Aufrufstellen werden auf `FilteredAsyncImage`
  bzw. `FilteredImage` umgestellt. Die Wrapper lesen `LocalImageFilter` und reichen `colorFilter`
  durch — sonst identisch zur Coil-`AsyncImage`/Compose-`Image`-API (alle bestehenden Parameter
  durchgereicht). Einzige Stelle, an der der Filter andockt → DRY, eine Naht.
- **`null` = kein Filter:** Profil „Aus" liefert `null` → Wrapper rendert ungefiltert
  (kein Overhead, identisch zu vorher).
- **Geräte-agnostisch:** Die Matrix ist nur Zahlen, läuft auf jeder HW. Nicht HW-gated. Das
  Default-Profil ist Kaleido-getunt, aber die Mechanik ist geräteneutral (Invariante 2).
- **Phase-2-Bereitschaft:** Pixel-Pipeline würde das Bitmap vor Anzeige bearbeiten (Coil
  `Transformation` + MuPDF-Post-Process) hinter denselben Wrappern; `ColorProfile` bekommt
  dann zusätzliche Felder mit neutralen Defaults — kein Umbau der Aufrufstellen.

## Daten-Modell

### Domain (pure Kotlin, `domain/`)

`domain/model/ColorProfile.kt`:
```kotlin
data class ColorProfile(
    val id: Long,
    val name: String,
    val saturation: Float,   // 1.0 = neutral; Kaleido-Default ~1.4
    val contrast: Float,     // 1.0 = neutral; Default ~1.15
    val brightness: Float,   // 0.0 = neutral; linearer Offset
    val builtIn: Boolean,    // mitgeliefert → nicht editier-/löschbar
)
```

`domain/color/ColorFilterMatrix.kt` — **pure Funktion, kein Compose/Android-Import:**
```kotlin
/** Baut eine row-major 4x5-ColorMatrix (FloatArray länge 20) im 0..255-Wertebereich. */
fun buildColorMatrix(saturation: Float, contrast: Float, brightness: Float): FloatArray
```
Reihenfolge der Verkettung: Sättigung → Kontrast → Helligkeit, kombiniert zu einer Matrix.
- Sättigung: Luminanz-Gewichte 0.213/0.715/0.072 (Rec.709), Standard-Saturation-Matrix.
- Kontrast `c` um Pivot 0.5 (= 127.5 im 0..255-Raum): Skalierung `c`, Offset `(1-c)*127.5`.
- Helligkeit: linearer Offset `brightness * 255` auf R/G/B.
Identität bei `buildColorMatrix(1f, 1f, 0f)` (Toleranz für Float). → TDD-Kern.

Die App wrappt das Ergebnis: `ColorFilter.colorMatrix(ColorMatrix(buildColorMatrix(...)))`.
(`ColorMatrix` ist `androidx.compose.ui.graphics` → bleibt im `app`-Modul, `domain` bleibt rein.)

### Repository

`domain/repository/ColorProfileRepository.kt` (neues Interface, SRP — getrennt von
`SettingsRepository`):
```kotlin
interface ColorProfileRepository {
    fun observeAll(): Flow<List<ColorProfile>>
    fun observeActive(): Flow<ColorProfile>          // fällt auf „Aus" zurück, nie leer
    suspend fun upsert(profile: ColorProfile): Long  // builtIn=true → IllegalArgument bei Update
    suspend fun delete(id: Long)                     // builtIn → no-op/Fehler
    suspend fun setActive(id: Long)
}
```

`observeActive` kombiniert `active_color_profile_id` (aus Settings-KV) mit der Profil-Tabelle;
fehlt/ungültig → „Aus"-Profil. Quellen-/geräteagnostisch.

### Persistenz (Room)

- **Neue Tabelle** `color_profiles`: `id` (autoGenerate), `name`, `saturation`, `contrast`,
  `brightness`, `built_in`. Entity + DAO (`ColorProfileDao`: `observeAll`, `observeById`,
  `upsert`, `deleteById`).
- **Aktiv-Pointer:** neuer Settings-KV-Key `active_color_profile_id` in bestehender
  `settings`-Tabelle (kein Settings-Schema-Bruch). `SettingsRepository` +
  `RoomSettingsRepository` um `activeColorProfileId: Flow<Long?>` / `setActiveColorProfileId`
  erweitern.
- **Migration Room v6 → v7:** Tabelle `color_profiles` anlegen **und** Built-ins seeden:
  - „Aus" — `saturation=1.0, contrast=1.0, brightness=0.0, built_in=1`
  - „Boox Go Color 7 Gen2" — `saturation=1.4, contrast=1.15, brightness=0.05, built_in=1`
  - Migration setzt `active_color_profile_id` initial auf die **Go-7**-Profil-ID (Default = das
    Gerät des Nutzers, ist der Zweck des Features).

## UI

Neuer Screen **Einstellungen → Display → Farbfilter** (`ColorFilterSettingsScreen`), Navigation
analog zu den bestehenden Settings-Screens. `eink-ui`-Skill beim Bau konsultieren.

Aufbau (E-Ink-Designsprache: flach, 1.5px-Border, keine Animationen, monochrom):
1. **Profil-Auswahl:** `ChoiceRow`-Liste aller Profile (Built-ins + Custom), aktives mit Häkchen.
2. **Live-Vorschau-Box:** ein zufälliges echtes Cover aus der Bibliothek (lokal vorhanden oder per
   Server-URL), live mit den aktuellen Editor-Werten gefiltert (`FilteredAsyncImage` mit lokal
   übersteuertem `colorFilter`). Fallback-Testbild, wenn kein Cover verfügbar.
3. **Regler:** `StepperRow` (± Schritte) für **Sättigung**, **Kontrast**, **Helligkeit** —
   **bewusst kein kontinuierlicher Slider** (ruckelt auf E-Ink; diskrete Schritte sind
   designsprachenkonform). Schrittweite z. B. 0.05.
4. **Aktionen** (Buttons, beschriftet): „Als neues Profil speichern" (Dialog für Namen),
   „Profil aktualisieren" (nur Custom), „Profil löschen" (nur Custom). Built-ins: nur „Duplizieren".

**i18n:** alle Texte über `i18n` (DE + EN, Compile-Zeit-Parität), echte Umlaute/ß. Neue Keys u. a.
`settings.colorFilter.title`, `.saturation`, `.contrast`, `.brightness`, `.saveAsNew`, `.update`,
`.delete`, `.duplicate`, Built-in-Profilnamen lokalisiert wo sinnvoll (Geräte-Eigenname „Boox Go
Color 7 Gen2" bleibt; „Aus" lokalisiert).

## Tests

**Unit (TDD, zuerst):**
- `buildColorMatrix`: Identität bei (1,1,0); Sättigung 0 → Graustufen (R=G=B = Luminanz);
  Sättigung 1.4 erhöht Farbabstand; Kontrast-Pivot 0.5 bleibt fix; Helligkeit-Offset additiv.
  Evident-Data-Stil (Berechnung im Assert sichtbar).
- `ColorProfileRepository` (In-Memory-Room): CRUD; `observeActive` fällt auf „Aus" bei
  fehlendem/ungültigem Pointer; `upsert`/`delete` auf Built-in wird abgelehnt.
- Migration v6 → v7: Tabelle existiert, beide Built-ins geseedet, Aktiv-Pointer = Go-7.

**E2E / sichtbar (Invariante 5):**
- Profil wechseln → Cover in Bibliothek sichtbar anders gefiltert; Vorschau aktualisiert sofort.
- Reader-Seite (Paged + EPUB) zeigt gefiltertes Bild.
- Screenshot auf Emulator `eink_test` (1264×1680@300) als Beweis; ideal echte Boox per USB für
  Kaleido-Realeindruck.

## Bekannte Interaktion (nicht blockierend)

Dark-Mode (App-Theme) bzw. Onyx-NightMode-Invert wirken global/HW-seitig **über** dem
In-App-Farbfilter. Sie komponieren additiv (Filter auf Bild-Pixel, Invert global). Kein Konflikt
für Phase 1; im Implementierungsplan als Verifikationspunkt notieren (Filter + Dark-Mode
zusammen prüfen).

## Berührte/neue Dateien (Überblick)

**Neu:**
- `domain/model/ColorProfile.kt`
- `domain/color/ColorFilterMatrix.kt` (+ Test)
- `domain/repository/ColorProfileRepository.kt`
- `data/.../db/ColorProfileEntity.kt`, `ColorProfileDao.kt`
- `data/.../repository/RoomColorProfileRepository.kt` (+ Test)
- `app/.../ui/components/FilteredImage.kt` (`FilteredAsyncImage` + `FilteredImage` + `LocalImageFilter`)
- `app/.../ui/settings/ColorFilterSettingsScreen.kt`

**Geändert:**
- `data/.../db/*` — DB-Version 6 → 7 + Migration + Seeds, DAO-Registrierung
- `domain/repository/SettingsRepository.kt` + `data/.../RoomSettingsRepository.kt` — Aktiv-Pointer
- `app/.../di/*` — `ColorProfileRepository` bereitstellen
- `app/.../ui/theme/Theme.kt` oder App-Root — `LocalImageFilter` providen
- `LibraryScreen`, `SeriesDetailScreen`, `PagedReaderScreen`, `WebtoonReaderScreen`,
  `EpubReaderScreen` — auf Wrapper umstellen
- `SettingsViewModel` (+ ggf. eigenes `ColorFilterViewModel`), Settings-Navigation
- `i18n/Strings.kt` (DE + EN)
