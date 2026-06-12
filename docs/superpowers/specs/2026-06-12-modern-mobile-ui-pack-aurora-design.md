# Modern-Mobile-UI-Pack „Aurora" + deklaratives Theme-Schema (2a) — Design-Spec

**Stand:** 2026-06-12 · **Status:** Design · Zwei-Phasen-Programm. Knüpft an die UI-Modularität an
(`big-picture-and-goals.md` → ui-modularity; `architecture-seams.md` → Theme-Pack-/Shell-Pack-/Slot-Nähte)
und an den L2-UI-Pack-Lader (`2026-06-12-ui-pack-loader-L2.md`).

> **Self-contained.** Vorher lesen: `big-picture-and-goals.md` (Geräteklassen nicht binär; „Theme zuerst,
> Layout danach"; **deklarativ, kein arbiträres Compose**; E-Ink-Invarianten **host-erzwungen**),
> `architecture-seams.md` (UiPack/`packFor`, ShellPack/`DeclarativeShell`/`ShellDescriptor`, `tiles`-Slot),
> `eink-design-language.md` + `animation-gating.md` (die Achsen, die der Host erzwingt),
> `2026-06-12-ui-pack-loader-L2.md` (data-only-Pack-Mechanik, die Phase 2 erweitert).

## 1. Ziel & Prinzip

Ein **distinktiver Modern-Mobile-Look** (dark + light) für die **LCD-Geräteklasse** (Phone/Tablet) — bewusst
**nicht** der Stock-Material-Default und **nicht** der E-Ink-Look. Er ist der erste „volle" Community-tauglich
gedachte UI-Look und dient zugleich als **Referenz-Implementierung**, aus der das **deklarative Theme-Schema**
(Phase 2, „2a") abgeleitet wird — damit derselbe Look später als **externes data-only UI-Pack-APK** lieferbar
ist, ohne Fremdcode.

**Leitprinzip (unverhandelbar):** Der **Look** gehört dem Pack; die **Motion-/Akzent-Policy** bleibt
host-erzwungen an der Geräteklasse (`DisplayBehavior`: `allowsMotion` ⟂ `allowsAccentColor`). Aurora ist ein
LCD-Look → auf LCD greifen Farbe + Bewegung + Elevation voll; auf mono E-Ink würde der Host Farbe/Bewegung
weiterhin klemmen (Aurora ist dort schlicht nicht die gewählte Geräteklasse). Das ist kein „E-Ink umgehen am
Pack vorbei", sondern: die richtige Geräteklasse ist offen — siehe `big-picture-and-goals.md`,
Geräteklassen-Tabelle.

**Zwei Phasen, je eigener Implementierungsplan, Phase 1 zuerst:**
- **Phase 1** — Aurora als **In-Tree-`UiPack`** + mobile Shell + Card-Tiles. Sichtbar/testbar am Emulator.
- **Phase 2** — `ui_pack.json`-`theme`-Sektion auf **volle Tokens** erweitern (das eigentliche Ziel „2a"),
  Doku, Aurora als externes Daten-APK (1→3-Beweis).

## 2. Der Referenz-Look (Tokens) — verbindlich

Struktur („Soft"): große Radien, weiche Schatten, schwebende Pill-Bottom-Nav, Card-Kacheln. Prinzip beider Modi:
**Nav = kontrastierendes Dock** (Dark heller als bg, Light dunkler als bg).

| Token | Dark (Slate Grey) | Light (Deeper Grey) |
|---|---|---|
| `background` | `#15171C` | `#CDD1D9` |
| `surface` (Cards/Sheets) | `#1C1F26` | `#C3C8D1` |
| `navDock` (Bottom-Nav) | `#1C1F26` | `#959CAA` |
| `accent` (primary) | `#3D5AFE` (Cobalt/Royal) | `#3D5AFE` |
| `onAccent` | `#FFFFFF` | `#FFFFFF` |
| `onBackground` / Text | `#E9EAEE` | `#1A1D24` |
| `onSurfaceVariant` (sekundär) | `#9296A0` | `#5F6570` |
| `chipActive` bg/on | `#262C45` / `#AEB7F5` | `#3D5AFE` / `#FFFFFF` |
| `outline` (Haarlinie) | `#2E313A` | `#B1B7C2` |

**Shapes (SoftShapes-Skala):** small 12dp · medium 16dp (Cards/Cover) · large 20dp (Nav/Sheets) · Buttons & Chips
voll-Pill (99dp).

**Typografie:** System-Font (kein Font-Shipping), modern getunt — Headlines `FontWeight.Bold`/`ExtraBold`,
negatives Tracking (`-0.02em`), klare Größen-Skala; Body Regular, lesbare Skala. Als Tokens (Gewicht/Größe/
Tracking) ausdrückbar → Phase-2-tauglich.

**Tiefe/Bewegung:** weiche Schatten (Elevation) + Motion erlaubt (LCD-Klasse: `allowsMotion = true`,
`allowsAccentColor = true`). Animation bleibt über `LocalEinkMode` gegatet (auf LCD = an).

## 3. Phase 1 — In-Tree-Aurora (Theme + Shell + Tiles)

### 3.1 Theme — `AuroraPack : UiPack`
- Neuer `UiPack` in `:ui-api` (`com/komgareader/ui/theme/UiPack.kt` oder eigene `AuroraPack.kt`): liefert
  `colorScheme(dark)` (volle Material-Rollen aus §2), `designTokens(dark)` (accent, Elevation an, weiche Kanten),
  `shapes = SoftShapes`, `typography = AuroraTypography` (getunte System-Typo).
- `id = "aurora"`. Neben `MonoEinkPack`/`KaleidoPack`/`LcdPack`.

### 3.2 Auswahl — Aurora als LCD-Klassen-Look, Lcd als Fallback
- `packFor(behavior)` wählt heute `LcdPack`, wenn `allowsMotion && allowsAccentColor`. **Entscheidung:** für die
  LCD-Klasse **`AuroraPack`** zurückgeben; `LcdPack` bleibt im Code als generischer Fallback erhalten (kein
  Verhaltensbruch der anderen Klassen). Reine Funktion → unit-getestet (`packFor(LCD-Behavior) == AuroraPack`).
- Keine neue Persistenz nötig: die Auswahl folgt dem bestehenden `displayMode`/`DisplayBehavior`-Pfad
  (Smartphone-Modus → LCD-Behavior → Aurora). `themeMode` (LIGHT/DARK/SYSTEM) steuert dark/light unverändert.

### 3.3 Shell — mobile schwebende Pill-Nav `FLOATING_NAV`
- `ShellNavStyle` (`:ui-api`) additiv um **`FLOATING_NAV`** erweitern (`{ BOTTOM_BAR, DRAWER, FLOATING_NAV }`).
- `descriptorFor(formFactor)` bleibt der Form-Faktor-Default (EXPANDED→BOTTOM_BAR, COMPACT→DRAWER). Aurora soll
  auf Phone **FLOATING_NAV** geben → der Selektor berücksichtigt zusätzlich den aktiven Look:
  `descriptorFor(formFactor, pack)` bzw. ein Aurora-Override (Detail im Plan; **Default-Verhalten ohne Aurora
  unverändert**).
- `DeclarativeShell` (`:app`) interpretiert `FLOATING_NAV` → neues privates `FloatingNavShell`-Composable
  (schwebende Pill-Bottom-Nav, Card-Look). `BottomBarShell`/`DrawerShell` unverändert. E-Ink-Gating
  (`snapTo`/keine Slide-Bewegung) host-erzwungen wie bei `DrawerShell`.

### 3.4 Tiles — Card-Variante über den `tiles`-Slot
- Aurora liefert ein `tiles`-Slot-Pack mit einer **Card-`SeriesTile`** (rund 16dp, weiche Elevation, Cover +
  Titel-Band). Der `tiles`-Slot existiert bereits (`TileState`/`DefaultSeriesTile`); Grid/Spaltenzahl bleibt
  Screen-Eigentum. Cover-Laden + E-Ink-Filter (`FilteredAsyncImage`) bleiben **host-erzwungen**.
- Verdrahtung: das aktive Pack speist ein `UiSlotPack(tiles = AuroraTiles)` in `KomgaReaderTheme`
  (`resolveSlots`); fehlende Regionen → Default (unverändert).

### 3.5 Reader
- Reader-**Engines** + Chrome bleiben Core/unberührt; sie erben nur die Aurora-Theme-Tokens (Farben/Shapes).
  Kein `readerChrome`-Umbau in dieser Runde (bewusst, YAGNI; spätere Runde möglich).

## 4. Phase 2 — Deklaratives Theme-Schema (2a) + externes Aurora-APK

### 4.1 Schema-Erweiterung (`ui_pack.json` `theme`)
Heute `theme: { accent, cornerRadius }`. Erweitern auf **volle, host-gerenderte Tokens** (alles Daten, keine
Compose-Typen in `domain`/`data`):
```json
"theme": {
  "colorScheme": "DARK",
  "background": "#15171C", "surface": "#1C1F26", "navDock": "#1C1F26",
  "accent": "#3D5AFE", "onAccent": "#FFFFFF",
  "onBackground": "#E9EAEE", "onSurfaceVariant": "#9296A0", "outline": "#2E313A",
  "cornerRadius": 16, "navRadius": 20, "elevation": true,
  "typography": { "headlineWeight": 800, "headlineTracking": -0.02, "scale": "regular" }
}
```
- **Reinheit:** `UiPackSpec` (domain) trägt die neuen Felder als **Primitive** (Hex-Strings, Ints, Bool, Enum-als-
  String). `parseUiPackSpec` (data, `org.json`) parst tolerant (fehlend → null/Default). Übersetzung in einen
  Runtime-`UiPack` (ColorScheme/Shapes/Typo) **nur in `:app`** (`UiPackApply.kt` → z.B. `toUiPackOrNull()`),
  analog zu `tokenOverride`/`toIconPack`/`shellOverride`.
- **Symmetrie dark+light:** `colorScheme` = `LIGHT|DARK` (host-`dark`-Flag) **und/oder** explizite Farb-Rollen.
  Beide Modi ausdrückbar (User-Bedingung). Ein Pack kann nur-dark, nur-light oder beides liefern.
- **`navStyle`-Enum** um `FLOATING_NAV` erweitern (Phase-1-Wert wird Daten-ausdrückbar).

### 4.2 E-Ink-Invariante (host-erzwungen, zentral)
- Farb-Rollen + Elevation + Motion gelten **nur**, wenn `DisplayBehavior.allowsAccentColor`/`allowsMotion`
  (LCD). Auf **mono E-Ink** ignoriert der Host die Farb-/Motion-Tokens (bleibt S/W, flach) — exakt der L2-Akzent-
  Gate-Mechanismus, auf das volle Token-Set erweitert. Shapes/Typo/`navStyle`/Icons sind invariant-neutral und
  gelten immer. **Test:** Token-Apply auf mono-Behavior lässt Farb-Rollen fallen.

### 4.3 Doku (Pflicht-Deliverable)
- `docs/ui-packs/README.md`: Schema-Referenz (jede Sektion/jedes Feld), **vollständiges Aurora-`ui_pack.json`**
  als durchgearbeitetes Beispiel, „So baust du einen UI-Pack"-Walkthrough (Manifest, Asset, Build, Install via
  Repo). Zielgruppe **Agenten + Menschen**, mit Code-Beispielen.
- KDoc an `UiPackSpec`, `parseUiPackSpec`, `UiPackApply` auf die neuen Felder.

### 4.4 Externes Aurora-APK (1→3-Beweis)
- `plugin/komga-ui-pack-aurora/` (data-only, `hasCode=false`, `DATA_CATEGORY=UI_PACK`, `ABI_VERSION=2`), Asset =
  das volle Aurora-`ui_pack.json`. Debug-signiert. Eintrag in `KomgaReaderPlugins/repo.json` (`type: ui_pack`).
- Beweis: das installierte Daten-APK reproduziert den In-Tree-Aurora-Look (bei LCD-Geräteklasse) — der Look ist
  1:1 als Daten ausdrückbar.

## 5. Betroffene Module / Dateien (Überblick)

**Phase 1:** `:ui-api` (`UiPack.kt`/`AuroraPack.kt`, `packFor`, `ShellNavStyle`, `ShellDescriptor`/`descriptorFor`),
`:app` (`DeclarativeShell` → `FloatingNavShell`, Aurora-`tiles`-Pack, `KomgaReaderTheme`-Verdrahtung).
**Phase 2:** `domain` (`UiPackSpec`-Felder), `data` (`UiPackParser`), `:app` (`UiPackApply` → `toUiPackOrNull`,
String→`ShellNavStyle`-Mapping inkl. `FLOATING_NAV`), `docs/ui-packs/`, `plugin/komga-ui-pack-aurora/`,
`KomgaReaderPlugins/repo.json`. **Nicht** betroffen: `plugin-api`/`plugin-host` (Kategorie `UI_PACK` + ABI
existieren aus L2; `navStyle` ist reiner JSON-String, das Enum lebt in `:ui-api`).

## 6. Tests (TDD)

- **Pure/Unit:** `packFor(LCD) == AuroraPack`; `descriptorFor`/`FLOATING_NAV`-Auswahl; `parseUiPackSpec` voll +
  leer/teilweise + kaputt; `toUiPackOrNull` (gesetzte Tokens → UiPack, fehlend → null/Default); E-Ink-Gate
  (mono-Behavior verwirft Farb-Tokens).
- **Swap-Preview (debug):** `AuroraPackPreview` (dark+light) + `FloatingNavShellPreview`.
- **E2E (Emulator):** Smartphone-Display-Modus → Aurora-Look sichtbar (dark+light), schwebende Nav, Card-Tiles;
  Phase 2: Aurora-Daten-APK entdeckt/installiert → identischer Look; mono-E-Ink-Modus ignoriert die Farb-Tokens.

## 7. Bewusste Nicht-Ziele (YAGNI)

- **Kein** ui-api-Code-ABI-Freeze und **kein** externes Compose-Code-Pack (Plugin-Typ b bleibt Nogo/Soll).
- **Kein** Reader-Chrome-Umbau, **keine** Font-Bündelung, **keine** per-Slot-Fremd-Packs.
- **Kein** Bruch bestehender Geräteklassen-Looks (Mono/Kaleido/Lcd-Fallback bleiben).

## 8. Offene Detailpunkte (für den Implementierungsplan)
- Genaue Signatur der look-bewussten Shell-Auswahl (`descriptorFor(formFactor, pack)` vs. Aurora-Override-Hook) —
  Default ohne Aurora muss bitidentisch bleiben.
- Ob `colorScheme: LIGHT|DARK` (Flag) **und** explizite Farb-Rollen beide unterstützt werden oder nur die
  expliziten Rollen (Flag ist die kleinere, aber weniger ausdrucksstarke Variante). Empfehlung: explizite Rollen
  (volle Ausdruckskraft), `colorScheme`-Flag optional als Kurzform.
```
