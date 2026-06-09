# Plan: Einheitliche UI + 3-Plattform-Skins (eink/smartphone/tablet) als Token-Packs

Stand: 2026-06-09 · Branch `feat/ui-platform-skins` · Worktree `.claude/worktrees/ui-platform-skins`

## Ziel

Eine **einheitliche, moderne (2026), professionelle** UI auf Kindle-/KOReader-Niveau und besser —
kohärent über **drei Geräteklassen**: mono E-Ink · Farb-E-Ink (Kaleido) · LCD-Phone/Tablet. Sie legt
den Grundstein für die **modulare UI** (`big-picture-and-goals.md` → ui-modularity): Plattform-Looks
als austauschbare Skins, später als komplettes UI-Plugin.

## Strategische Leitplanken (entschieden mit User 2026-06-09)

1. **Keine 3 getrennten UI-Bäume.** EINE Host-UI; Plattformen = **Token-Packs** dahinter. Sonst wird
   der Reader-Core 3× dupliziert (Naht B verletzt), E-Ink-Invarianten pro Tree neu erzwungen → Drift.
   Core (Reader-Engines, `Viewer`-Naht, `RefreshScheduler`, Lese-/Sync-Pfad) bleibt **nie** ein Pack.
2. **„Skin jetzt, UX später".** Jetzt: 1 Layout + Token-Packs (kohärent). Slot-Naht so vorbereiten,
   dass **divergente Layout-UX** (Phone = bottom-sheets/gesten-nav/material-reich; E-Ink = button-driven,
   flach) **später ohne Core-Umbau** als Layout-Pack nachrüstbar ist.
3. **ABI NICHT jetzt freezen.** Skins in-tree als Packs bauen, so strukturiert dass sie später als
   komplettes UI-Plugin rausziehbar sind. **Kein** `ui-api`-Modul-Freeze, bevor 3 echte Kunden
   (= die 3 Plattform-Packs) gegen den Vertrag gebaut haben. Der Vertrag wird *aus* ihnen extrahiert,
   nicht vorab erfunden.
4. **„Andere E-Ink-Displays" ist Naht B, nicht die UI-Naht.** Das Theme ist schon `capabilities`-getrieben
   (nicht Onyx-hart). Bessere Nicht-Onyx-Unterstützung = neue `EinkController`-Impls + nicht-Onyx
   Capability-Erkennung — **separater** Track, nicht in diesen Refactor mischen.

## Ist-Stand (verifiziert per grep, nicht Doku)

- **2-Achsen-Modell** (`allowsMotion`/`allowsAccentColor`, `domain/model/DisplayBehavior.kt`) voll bis
  `MainActivity` verdrahtet — **aber `allowsAccentColor` hat 0 UI-Consumer**, `Theme.kt` global mono,
  **kein Akzent-Token**. `LocalDisplayBehavior` nie im UI gelesen, nur `LocalEinkMode` (=`!allowsMotion`).
- Beide Achsen trennen alle 3 Klassen sauber: mono=`(!motion,!accent)` · kaleido=`(!motion,accent)` ·
  lcd=`(motion,accent)`. (Die Kombi `(motion,!accent)` ist sinnlos → Fallback mono.)
- **Reader-Chrome stark** (`ReaderScaffold`+`Viewer`, 5 Slots ~6.5/10). **Rest Monolith** (~3/10):
  keine `SeriesTile`, TopAppBar pro Screen dupliziert, Tabs als lokaler State, **kein Slot außerhalb Reader**.

## Phasen (Risiko billig→teuer)

### P0 — Achse lebendig machen & Monolithen zerlegen (Fundament, kein Plugin)
- **P0.1 Token-Seam:** `DesignTokens` (Akzent, Schatten-Flag, …) + `LocalDesignTokens`, in
  `KomgaReaderTheme` aus `LocalDisplayBehavior` abgeleitet (`designTokensFor`). Erster Consumer:
  `EinkBottomBar`-Akzentbalken liest `LocalDesignTokens.current.accent` statt hartem `onSurface`.
  *Zunächst monochrom* (Plumbing, 0 visuelle Änderung, grüner Build) — Akzent-**Wert** ist danach
  eine 1-Zeilen-Token-Änderung.
- **P0.2 Akzent-Identität wählen** (Design-Fork mit User): LCD-Akzent (emulator-verifizierbar),
  Kaleido-Akzent gedämpft (echte HW-Verify Pflicht), mono bleibt schwarz/weiß.
- **P0.3 Monolithen → Bausteine:** `SeriesTile`, `StandardTopAppBar`, `SettingsPageScaffold` extrahieren
  (`shared-structure-before-variants.md`). Macht nicht-Reader-Chrome erst slot-fähig.

### P1 — Token-Pack-Naht (deklarativ, Plugin-Typ-(c)-analog, risikoärmst)
- `DesignTokens` zu vollem Skin-Vertrag ausbauen (Farben inkl. Akzent/Sekundär, Radius, Border-Stärke,
  Typo-Gewichte, Motion/Schatten-Policy, Icon-Stroke). 3 Built-in-Packs: `MonoEinkPack`/`KaleidoPack`/
  `LcdPack` (×light/dark). Host wählt Pack aus Geräteklasse. **Smartphone-Pack** bringt Animation+Schatten+Farbe,
  E-Ink-Packs flach+statisch — alle über *dieselbe* Host-UI.

### P2 — Slot/Region-Naht fürs Chrome (schweres Ende)
- Benannte, adressierbare Slots (header/overlay/tiles/nav/settings/dialog). Host rendert + erzwingt
  E-Ink-Invarianten (Motion/Akzent weiter host-gegatet, nie pack-gegatet). Fehlender Slot → Default-Pack
  (wie `StubSource`). Hier klinkt ein Community-UI-Pack ein; hier wird divergente Phone-UX zum Layout-Pack.

### P3 — `ui-api`-Modul + Pack-Lader extrahieren (Phase-4, mit Plugin-Architektur)
- Vertrag aus den 3 erprobten Packs extrahieren, einfrieren (ABI als 2 Integer wie Plugin-Plan),
  separates APK-Pack via `PackageManager` laden. **Erst wenn P1/P2 den Vertrag bewiesen haben.**

## Invarianten (gelten durchgehend)
- E-Ink-Invarianten **host-erzwungen**, nie pack-delegiert (`eink-design-language.md`, `animation-gating.md`).
- Motion über `allowsMotion`/`LocalEinkMode`, Farbe über `allowsAccentColor` — getrennt, nie gekoppelt.
- Reader-Engines = Core, nie ersetzbar. Token über Theme, nie hartkodiert. Sichtbarer Text über i18n DE+EN.
- TDD wo pur (`designTokensFor`, Mapper); E2E/Emulator-Screenshot pro sichtbarem Feature.
