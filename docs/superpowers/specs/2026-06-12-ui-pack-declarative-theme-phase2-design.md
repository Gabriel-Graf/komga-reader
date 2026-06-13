# Phase 2: Deklaratives `ui_pack.json`-Theme — voller Look als Daten (2a) — Design-Spec

**Stand:** 2026-06-12 · **Status:** Design · Phase 2 des Aurora-Programms
(`2026-06-12-modern-mobile-ui-pack-aurora-design.md` §4). Baut auf Phase 1 (In-Tree `AuroraPack`,
`FLOATING_NAV`, Card-Tiles — **gemergt**) und auf L2 (`ui_pack.json`-Lader, `UiPackSpec`/`parseUiPackSpec`/
`UiPackApply`).

> **Self-contained.** Vorher lesen: `big-picture-and-goals.md` (ui-modularity → „Community installiert eine
> UI"; **deklarativ, kein arbiträres Compose**; E-Ink-Invarianten **host-erzwungen**), `architecture-seams.md`
> (Theme-Pack-Naht `UiPack`/`packFor`/`KomgaReaderTheme`; Aurora-Ist), `2026-06-12-ui-pack-loader-L2.md`
> (data-only-Pack-Mechanik, die wir erweitern), `komga-plugins`-Skill (deklarativ, „UI neu, Kernlogik gleich").

## 1. Ziel & Prinzip

Heute trägt die `theme`-Sektion eines externen `ui_pack.json` nur **zwei Knöpfe** (`accent`, `cornerRadius`) —
zu dünn für einen *ganzen* Look. Phase 2 erweitert sie auf einen **vollen, host-gerenderten Theme-Deskriptor**
(Farb-Rollen hell+dunkel, Radien, Elevation, Typo-Tuning), sodass ein **externes data-only APK** denselben Look
wie der In-Tree-`AuroraPack` liefern kann — **rein deklarativ** (kein Code, kein Host-Rechte-Risiko). Das ist
der eigentliche „2a"-Schritt: der ganze Look als **Daten**.

**Der Referenz-Look ist der Maßstab:** Das Schema exponiert **genau** die Knöpfe, die der gebaute `AuroraPack`
setzt — nicht mehr (YAGNI), nicht weniger. Lackmustest: das Aurora-Daten-APK reproduziert den In-Tree-Aurora-Look
1:1 (der 1→3-Beweis).

**E-Ink host-erzwungen (zentral):** der **ganze externe Farb-Look** (ColorScheme + Elevation) gilt **nur**, wenn
`DisplayBehavior.allowsAccentColor` (= Smartphone-Modus). Auf **mono/Kaleido-E-Ink** ignoriert der Host die
externen Farben → der geräteklassen-gewählte Pack (mono S/W) bleibt. **Invariant-neutrale** Teile (Eckradien,
Typo-Gewichte, `navStyle`, Icons) gelten immer. Bewegung bleibt über `LocalEinkMode` gegatet.

## 2. JSON-Schema (der erweiterte Vertrag)

`theme` wird additiv erweitert; die alten flachen Felder (`accent`/`cornerRadius`) bleiben gültig (alte Packs
unberührt). Voll-Pack (alle Felder optional):
```json
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
```
- **`light`/`dark`** je optional (ein Pack darf nur-dark, nur-light oder beides liefern — erfüllt die
  Symmetrie-Bedingung). Fehlt ein Modus → der Host fällt für diesen Modus auf den geräteklassen-Pack zurück.
- **Farb-Rollen** = genau die 8, die Aurora setzt. `navDock` ist die Bottom-Nav-Fläche (→ ColorScheme-Rolle
  `surfaceVariant`, die `FloatingNavBar` liest). Andere Material-Rollen leitet der Host konservativ ab
  (`primary`=accent, `onPrimary`=onAccent, `onSurface`=onBackground …).
- **Ungültige Hex/fehlende Felder** → tolerant verworfen, der Host füllt aus dem geräteklassen-Pack (nie Crash).
- **`navStyle`** bleibt in der `shell`-Sektion (L2, schon vorhanden; `FLOATING_NAV` ist seit Phase 1 ein gültiger
  Wert — `UiPackApply.shellOverride()` mappt den String bereits über `ShellNavStyle.entries`).

## 3. Reinheit (Modul-Schnitt — wie L2)

- **domain** (`UiPackSpec`): neue Felder als **reine Primitive** — eine `data class ThemeSpec` (alles
  String-Hex/Int/Boolean/Float), mit `light: ColorRolesSpec?`/`dark: ColorRolesSpec?` (je 8 nullbare Hex-Strings),
  `cornerRadiusDp: Int?`, `elevation: Boolean?`, `typography: TypoSpec?`. **Keine** Compose-/ui-api-
  Typen in domain. (Die Floating-Nav-Eckung bleibt host-fix — kein eigener Token, YAGNI.)
- **data** (`parseUiPackSpec`): die neuen Felder tolerant aus `org.json` lesen (Sub-Objekte `light`/`dark`/
  `typography`). ABI-Range-Check unverändert.
- **app** (`UiPackApply`): `UiPackSpec.toUiPackOrNull(): UiPack?` — baut einen **Runtime-`UiPack`** (ColorScheme
  hell/dunkel aus den Rollen via `lightColorScheme`/`darkColorScheme`, `Shapes` aus den Radien, `Typography` aus
  dem Typo-Tuning, `DesignTokens` aus accent/elevation/cornerRadius). `null`, wenn keine `theme`-Farb-Sektion
  vorhanden (dann greift weiter der bestehende `tokenOverride`-Pfad für reine accent/cornerRadius-Packs).
  Die **Übersetzung Daten→Compose/ui-api lebt ausschließlich hier** (Spec §4-Prinzip).

## 4. Host-Anwendung (`KomgaReaderTheme`)

Heute: `val pack = UiPackRegistry.forBehavior(behavior)`; `tokenOverride` patcht accent/cornerRadius.
Neu — ein **voller externer Pack** ersetzt den geräteklassen-Pack, host-gegated:
```kotlin
val devicePack = UiPackRegistry.forBehavior(behavior)
// Externer Voll-Theme-Pack gilt NUR, wenn die Geräteklasse Farbe erlaubt (mono/Kaleido E-Ink ignoriert ihn).
val pack = if (behavior.allowsAccentColor) externalPack ?: devicePack else devicePack
```
- `externalPack: UiPack?` reicht der Host aus dem aktiven `UiPackSpec` herein (`activeSpec?.toUiPackOrNull()`,
  in `MainActivity` neben dem schon vorhandenen `tokenOverride`/`slotPack`).
- `MaterialTheme(colorScheme = pack.colorScheme(dark), shapes = pack.shapes, typography = pack.typography)` und
  `LocalDesignTokens` ziehen damit automatisch den externen Look. Der bestehende `tokenOverride`-Pfad bleibt für
  **partielle** Packs (nur accent/cornerRadius, kein Voll-Theme) — er patcht weiter `pack.designTokens(dark)`.
- **Kein** Eingriff in Slot-/Shell-Auflösung (die laufen schon über die L2-Pfade `toIconPack`/`shellOverride`).

## 5. Doku (Pflicht-Deliverable — Punkt 2 der Phase-2-Zusage)

- `docs/ui-packs/README.md`: **Schema-Referenz** (jede Sektion/jedes Feld, Typ, Default-Verhalten),
  **vollständiges Aurora-`ui_pack.json`** als durchgearbeitetes Beispiel, **Walkthrough „So baust du einen
  UI-Pack"** (Manifest `DATA_CATEGORY=UI_PACK`/`hasCode=false`, Asset, Build, Repo-Eintrag, Install). Zielgruppe
  **Agenten + Menschen**, mit Code-Beispielen. Ein expliziter Satz: **was NICHT geht** (kein Compose, keine
  Fonts-Dateien, E-Ink ignoriert Farben).
- KDoc an `UiPackSpec.ThemeSpec`, `parseUiPackSpec` (neue Felder), `toUiPackOrNull`.

## 6. Externes Aurora-APK (1→3-Beweis)

- `plugin/komga-ui-pack-aurora/` (data-only, `hasCode=false`, `DATA_CATEGORY=UI_PACK`, `ABI_VERSION=2`), Asset =
  das volle Aurora-`ui_pack.json` (shell.navStyle=FLOATING_NAV + theme light/dark wie §2). Debug-signiert.
- Eintrag in `Gabriel-Graf/KomgaReaderPlugins/repo.json` (`type: ui_pack`, Fingerprint).
- **Beweis:** das installierte+aktivierte Daten-APK reproduziert (im Smartphone-Modus) den In-Tree-Aurora-Look.

## 7. Tests (TDD)

- **data** `parseUiPackSpec`: voll (light+dark+typo+radien), partiell (nur dark), nur-legacy (accent/cornerRadius),
  kaputt (ungültiges Hex → Feld null), ABI außerhalb → null.
- **app** `toUiPackOrNull`: voll → UiPack mit erwarteten ColorScheme-Rollen (background/surface/surfaceVariant/
  primary), Shapes (medium=cornerRadius), DesignTokens (accent/usesShadows); fehlende `theme`-Farben → null;
  nur-dark → light fällt auf Default (dokumentiertes Verhalten testen).
- **app** Host-Gate (pure Hilfsfunktion, falls extrahiert): `behavior.allowsAccentColor=false` → externalPack
  ignoriert (devicePack); `=true` → externalPack.
- **E2E (Emulator):** Aurora-Daten-APK entdeckt/installiert/aktiviert → Smartphone-Modus zeigt den Aurora-Look
  (dark+light), identisch zum In-Tree-Pack; mono-E-Ink-Modus ignoriert die Farben.

## 8. Bewusste Nicht-Ziele (YAGNI)

- **Kein** ui-api-Code-ABI-Freeze, **kein** Compose-Code-Pack (Plugin-Typ b bleibt Nogo/Soll).
- **Keine** Font-Bündelung (Typo nur Gewicht/Tracking als Zahlen), **keine** beliebigen Material-Rollen (nur die
  8 Aurora-Rollen + konservative Ableitung), **keine** per-Slot-Theme-Granularität.
- **Kein** Bruch alter Packs (flaches accent/cornerRadius bleibt gültig) und **kein** Bruch der Geräteklassen-Looks.

## 9. Offene Detailpunkte (für den Plan)
- Genaue Ableitung der nicht-exponierten Material-Rollen (`secondary`/`tertiary`/Container) — Empfehlung:
  aus `accent`/`surface` konservativ ableiten oder Material-Defaults des Modus belassen; im Plan fixieren.
- Ob `externalPack` als neuer `KomgaReaderTheme`-Parameter (analog `tokenOverride`) reinkommt oder der Host die
  Auswahl davor macht — Empfehlung: neuer optionaler Parameter (kleinster Diff, symmetrisch zu `tokenOverride`).
