# Plan: Settings- & Tab-Feinschliff (Design-Sprache geradeziehen)

**Branch:** `feat/reader-ui-polish` (nach Merge `feat/source-agnostic-integration`)
**Companions:** `docs/mockups/settings-redesign.{html,png}`, `docs/mockups/tabs-redesign.{html,png}`

Ziel: die nach dem Merge verbliebenen Design-Sprache-Verstöße geradeziehen — keine
Cover-Grid-Umbauten, keine Architektur-Änderung. Token-/i18n-treu, Akzent über
`LocalDesignTokens` (mono = Schwarz, Kaleido = #445A86, LCD = Vivid Indigo).

## Invarianten
- Sichtbarer Text **immer** i18n (DE+EN, Compile-Parität über `Strings`-Interface).
- Icons **nur** `AppIcons.*` (Lucide), nie Material-Icons.
- Keine nackten Material-Controls (`Checkbox`/`FilterChip`/`Button`) — App-Bausteine.
- Akzent nie hartkodiert, nie an Bewegung gekoppelt — `LocalDesignTokens.current.accent`.
- Eine Linienstärke (`EinkTokens.hairline`), ein Tile-Gap (`EinkTokens.tileGap`).

## Settings

- **S1 — Quelltyp-Toggle (`SettingsContent.kt` ConnectionModal):** zwei rohe `Button`/
  `EinkOutlinedButton` → `SegmentedChoiceRow` (label `serverSectionKind`, Keys
  `KOMGA`/`OPDS`). `FieldCaption` entfällt (Segment trägt das Label).
- **S2 — ChoiceRow-Häkchen (`EinkComponents.kt` ChoiceRow):** Check-`tint` von
  `colorScheme.onSurface` → `LocalDesignTokens.current.accent`. App-weit (Theme/Sprache/
  Picker). Mono unverändert (accent = schwarz).
- **S3 — „Über" (`SettingsContent.kt` AboutContent):** Info-Zeilen Version · Gerät ·
  Lizenz (AGPL-3.0) · Quellcode-Link. Neue Keys `aboutLicense`, `aboutSourceCode`.

## Tabs

- **T1 — Bibliotheken-Dialog (`GroupsScreen.kt` LibraryEditDialog):** Material-`Checkbox`-
  Liste → `ChoiceRow`-Zeilen (Häkchen = ausgewählt, Mehrfachauswahl). `FilterChip`-Reihe
  (Fallback-Typ) → `SegmentedChoiceRow` (Keys `""`=Auto / `MANGA` / `COMIC` / `NOVEL` /
  `WEBTOON`).
- **T2 — Stöbern Empty/Error + i18n (`LibraryScreen.kt`):** hartkodierte Snackbar-Strings
  (`:51-53`) + „Wiederholen" (`:106`) → i18n. Error-State → Line-Art-Icon (`AppIcons.Library`)
  + Text + `EinkOutlinedButton`. Neue Keys: `fun downloadingChapters(count)`,
  `downloadComplete`, `fun downloadFailed(msg)`, `retry`.
- **T3 — Linien & Raster:** `TypeChip`-Rand `1.dp` → `EinkTokens.hairline`. Library-Grid
  `4.dp` → `EinkTokens.tileGap` (= Groups, 8). Titelband Series+Group → geteilte
  `TileTitleBand`-Composable (eine Scrim-/Text-Konstante statt 2× hart `Color.Black`/`10.sp`).

## Reihenfolge (TDD wo Logik)
1. i18n-Keys ins `Strings`-Interface + StringsDe/StringsEn (Compile-Parität = der Test).
2. `TileTitleBand` extrahieren (verhaltens-erhaltend) → SeriesTile + GroupTile darauf.
3. S2 ChoiceRow-Akzent (1 Zeile, app-weit).
4. S1, S3, T1, T2, T3 — reine UI-Verdrahtung über bestehende Bausteine.
5. `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest` grün.
```
