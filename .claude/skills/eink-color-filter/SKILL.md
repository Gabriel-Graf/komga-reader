---
name: eink-color-filter
description: Use when touching the central E-Ink color filter in the Komga-Reader (covers + reader pages getting saturation/contrast/brightness adjustment, ColorProfile, buildColorMatrix, LocalImageFilter, FilteredAsyncImage/FilteredImage, color_profiles table, built-in profile seeding). Hält die Naht und die Seeding-Regel fest, damit sie nicht versehentlich gebrochen werden.
---

# E-Ink-Farbfilter (Domain-Regel)

Ziel: alle angezeigten Bilder (Cover + Reader-Seiten) zentral fürs Kaleido-3-Display anpassen
(Sättigung anheben gegen CFA-Dämpfung, Kontrast/Helligkeit). Default-Profil „Boox Go Color 7 Gen2".

## Die eine Naht — NICHT umgehen

Genau **ein** `CompositionLocal` trägt den Filter:
`LocalImageFilter: ColorFilter?` (`app/ui/components/FilteredImage.kt`), **einmal** am NavHost-Root
in `MainActivity` aus `SettingsViewModel.activeColorProfile.toColorFilterOrNull()` bereitgestellt.

Alle Bilder laufen durch **zwei Wrapper**, sonst nichts:
- `FilteredAsyncImage` (Coil) — Cover + gestreamte Seiten (Library, SeriesDetail, GroupBrowse, Paged, Webtoon)
- `FilteredImage` (Compose `Image`) — MuPDF-Bitmap (EPUB/PDF)

`colorFilterOverride` + `useOverride=true` übersteuern den globalen Filter (Live-Vorschau im Editor).

**Falsch (sofort ablehnen):** `colorFilter=` direkt pro Aufrufstelle setzen · Coil-Interceptor /
Bitmap-Kopie (Allokation pro Bild, gecachtes Filterresultat nicht umschaltbar, verfehlt MuPDF) ·
Pixel-Schleife über das Bitmap. Phase 1 = reiner Compose-`colorFilter`, keine Bitmap-Verarbeitung.

## Die Mathematik (pure, domain)

`domain/color/ColorFilterMatrix.kt`: `buildColorMatrix(saturation, contrast, brightness): FloatArray`
— row-major 4×5 im 0..255-Raum, Rec.709-Luminanz (0.213/0.715/0.072), Kontrast-Pivot 127.5,
Helligkeit linear (`*255`). Pure Kotlin, unit-getestet (`ColorFilterMatrixTest`).

`ColorProfile.toColorFilterOrNull()` gibt **`null`** zurück, wenn `isNeutral`
(`saturation==1 && contrast==1 && brightness==0`) → Compose filtert gar nicht (keine Allokation).
`ColorProfile.OFF` (id 1, „Aus") ist das kanonische neutrale Profil.

## Persistenz: Liste vs. aktiver Zeiger

- **Profilliste:** Tabelle `color_profiles` (`ColorProfileDao`, `RoomColorProfileRepository`).
- **Aktiver Zeiger:** **getrennt** als Settings-KV-Key `active_color_profile_id`
  (`SettingsRepository.activeColorProfileId: Flow<Long?>`), per Konstruktor in das Repo injiziert
  (SRP — Repo kennt `SettingsRepository` nicht direkt).
- `observeActive()` kombiniert beide; fehlt/ungültig → Fallback `ColorProfile.OFF`.

## Seeding-Gotcha (am leichtesten zu brechen)

Built-ins werden in **`seedColorProfiles(db)`** (`AppDatabase.kt`) gesetzt — aufgerufen von **ZWEI**
Pfaden, die beide nötig sind:
1. `MIGRATION_6_7` → Bestands-Upgrades (v6 → v7).
2. `SEED_CALLBACK.onCreate` (in `DataModule` via `.addCallback(...)`) → **Frisch-Installationen**
   (Room legt Tabellen direkt aus Entities an, **Migration läuft dann NICHT**).

Ein neues Built-in hinzufügen heißt: Zeile in `seedColorProfiles` **und** eine **neue** Migration
(`MIGRATION_7_8`, registrieren + `@Database(version=…)` bumpen) für Bestands-Installationen.
`seedColorProfiles` allein deckt nur Neu-Installationen ab.

## Phase 2 (offen)

Gamma/Tonwertkurve, Unsharp-Mask, Dithering klinken sich hinter **dieselben Wrapper** als
Bitmap-Post-Process ein; `ColorProfile` wächst per Felder mit neutralem Default. Keine Aufrufstellen-Umstellung.

## Pointer

Spec/Plan: `docs/superpowers/specs/2026-06-06-eink-color-filter-design.md`,
`docs/superpowers/plans/2026-06-06-eink-color-filter.md`.
Kern: `domain/color/ColorFilterMatrix.kt`, `domain/model/ColorProfile.kt`,
`app/ui/components/FilteredImage.kt`, `app/ui/settings/ColorFilterViewModel.kt` + `ColorFilterSettingsScreen.kt`,
`data/db/AppDatabase.kt` (`seedColorProfiles`/`MIGRATION_6_7`/`SEED_CALLBACK`), `data/repository/RoomColorProfileRepository.kt`.
Tests: `ColorFilterMatrixTest`, `RoomColorProfileRepositoryTest`, `ColorProfileSeedTest` (instrumentiert).
Gehört zu [[project-komga-eink-reader]]; UI-Look siehe `eink-ui`.
