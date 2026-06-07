---
name: guided-comic-reader
description: Use when touching the Guided Comic Reader in the Komga-Reader (ViewerType.COMIC, ComicReaderScreen/ViewModel, panel-by-panel zoom, PanelDetector wiring, GuidedNavigator/PanelGeometry). Hält die verbindlichen Interaktions-, Pixelquellen- und Auflösungsregeln fest, damit der geführte Panel-Lesefluss und die E-Ink-Invarianten nicht versehentlich gebrochen werden.
---

# Guided Comic Reader (Domain-/Reader-Regel)

Comic-Lesemodus für E-Ink: im Kern Paged-Reader (volle Seite, Seitenblättern) +
**Panel-für-Panel-Zoom**. Naht-konform — Detektor sitzt in `guided-view`, Pixel kommen
aus Coil, Streaming bleibt. Volltext: `docs/superpowers/specs/2026-06-06-guided-comic-reader-design.md`.

## Auflösung — `ViewerType.COMIC`

`ViewerType = PAGED, WEBTOON, EPUB, COMIC`. In `ResolveViewerType.map`:

```
MANGA   -> PAGED      COMIC -> COMIC      WEBTOON -> WEBTOON      NOVEL -> EPUB
```

`ContentType.COMIC` existiert bereits — **kein neuer Enum-Wert**. Die 6-stufige
Prioritätsregel aus [[viewer-type-resolution]] bleibt unverändert; nur das COMIC-Mapping
wechselt von PAGED auf COMIC. App: `ViewerMode.COMIC`, `ReaderRoute` → `ComicReaderScreen`.

## Pixelquelle — Coil, NIE MuPDF

Kein Voll-Download fürs Streaming. Ablauf je Seite:
Coil dekodiert die Komga-Seite → Bitmap abgreifen → auf ~1000px Breite **downscalen** →
`RenderedPage` (ARGB-IntArray) → `PanelDetector.detect(page, direction)`.
Panel-Koordinaten liegen im Downscale-Raum → **zurückskalieren** auf Display-Größe
(`PanelGeometry`) fürs Zoom-Rechteck. Detektion pro Seite **einmal**, gecacht
(`Map<page, List<PanelRect>>`), auf `Dispatchers.Default`.

Leserichtung: `Series.readingDirection` `LTR → LEFT_TO_RIGHT`, `RTL → RIGHT_TO_LEFT`,
Default `LEFT_TO_RIGHT`. (VERTICAL/WEBTOON sind nie COMIC.)

## Der zentrale Punkt: „nächstes Panel" ist gelöst

`PanelDetector` liefert Panels **bereits in Leserichtung sortiert**. Nächstes Panel =
nächster Listenindex. **Keine eigene Richtungserkennung bauen** — das ist der häufigste
Fehler. `GuidedNavigator` steppt nur durch die Liste und über Seitengrenzen.

## Interaktion — zustandsabhängig

Tap-Mitte bedeutet je Zustand etwas anderes (kein Widerspruch):

| Zustand | Tap Panel | Tap Gutter/Rand | Tap links / rechts | HW-Tasten |
|---|---|---|---|---|
| Vollseite (≥2 Panels) | Zoom in **dieses** Panel | Chrome-Overlay | (ganze Fläche = Panel-Treffer) | ganze Seite blättern |
| Gezoomt | — | — | links=voriges, rechts=nächstes Panel | voriges/nächstes Panel |
| Vollseite (0/1 Panel) | — | Mitte=Chrome | links/rechts = Seite | ganze Seite blättern |

- Tap auf **beliebiges** Panel startet Guided **genau dort** (Tap auf Panel 3 → Start 3).
- Nie ein Panel getippt → reiner Paged-Reader, Tasten blättern Seiten.
- Gezoomt, Mitte-Tap = Zoom-Out auf Vollseite.
- **Seitengrenze:** vorwärts über letztes Panel → Folgeseite Panel 0 (still umblättern);
  rückwärts vor erstes Panel → Vorseite letztes Panel. Nachbarseite 0/1 Panel → Vollseite.
- Panel-Zoom = Rechteck + kleiner Rand bildschirmfüllend, **sofort, keine Animation** (E-Ink).

## Fallback + Toggle

- **Auto:** 0/1 Panel erkannt → Seite verhält sich wie Paged (Drittel-Zonen).
  Splash-/Doppelseiten kommen am Stück. Detektor-Schwellen bleiben Default.
- **Manuell:** Guided-Toggle im Chrome-Overlay. Aus → ganzer `ComicReaderScreen` = reiner
  Paged. Session-Zustand im ViewModel. Default an.

## E-Ink

Panel-Wechsel/Zoom-Out = Bildwechsel → Full-Refresh über `OnyxRefresher`/`RefreshScheduler`
(wie Seitenwechsel). Kein blindes Invalidieren. No-Op auf Nicht-Boox (HW-gated).
E-Ink-Designsprache gilt ([[eink-ui]]): flach, 1.5px-Border, monochrom, keine Animation.

## Detektor-Algorithmus (Flood-Fill + Connected-Components)

`PanelDetector` arbeitet in Stufen (reines Kotlin, host-testbar, keine Android-Deps):
Otsu-Binarisierung → Edge-Seed-Gutter-Flood (vom Seitenrand, Full-Bleed-Panels bleiben erhalten) →
Component-Bounding-Boxes (RegionLabeling) → Min-Fläche-Filter → **Rahmen-Linien-Split** (`BorderLineSplit`)
→ Bubble-Filter (`dropContainedSmall`) + Contained-Merge → Lesereihenfolge (Zeilen-Bänder, LTR/RTL).
Units: `ImageBinarization` / `GutterFill` / `RegionLabeling` / `BorderLineSplit` / `ReadingOrder` / `PanelDetector`.

**Rahmen-Linien-Split (`BorderLineSplit`):** Der Weiß-Gutter-Flood trennt nur über **weiße** Gutter.
Schwarz-umrandete, eng liegende Panels (z. B. Red Hood) verschmelzen sonst zu seitenbreiten Bändern.
Darum wird jede seitenüberspannende Komponente rekursiv an **dünnen, durchgehend dunklen,
von helleren Bändern umgebenen** achsenparallelen Rahmenlinien geschnitten. Der „heller-Nachbar"-Guard
verhindert, dass uniform-dunkle Splash-Art zersplittert wird.

**Bubble-Filter:** Kleine Boxen (< ~6 % Seitenfläche), die vollständig in einer größeren liegen
(Sprechblasen = helle Inseln im Panel), werden verworfen — eine Blase ist nie ein Panel.

**Degenerate-Guard (im ViewModel):** Liefert der Detektor <2 Panels ODER ein Panel belegt >85 % der
Seitenfläche → Vollseite-Fallback (Seite verhält sich wie Paged, kein Panel-Zoom).

**Letterbox-bewusste Tap/Zoom:** Tap-Koordinaten und Zoom-Rechteck rechnen gegen das
Content-Rechteck (bei ContentScale.Fit: `contentW`×`contentH` innerhalb des Viewports), nicht
gegen den rohen Viewport. `PanelGeometry.fitScale(panel, contentW, contentH, viewportW, viewportH,
marginFraction)` liefert den korrekten Faktor inklusive Letterbox-Offset.

## Schnitt (was wohin)

- `PanelDetector` — `guided-view`, vorhanden, nicht anfassen.
- `GuidedNavigator` (Panel-Sequenz inkl. Seitengrenzen) + `PanelGeometry` (Skalierung,
  hitTest, Zoom-Rechteck) — **pure**, TDD zuerst, isoliert testbar.
- `ComicReaderViewModel` — Zustand, Detektion anstoßen, Cache, Toggle, Degenerate-Guard.
- `ComicReaderScreen` — dünne Compose-Shell (Coil-Vollseite + gezoomtes Panel + Tap-Zonen).

## Anti-Pattern (sofort ablehnen)

- Eigene „Richtungserkennung" fürs nächste Panel — der Detektor ordnet schon.
- MuPDF/Voll-Download für Comic-Pixel — Coil-Bitmap downscalen.
- Animierter Panel-Pan/Zoom auf E-Ink.
- Neuer `ContentType`-Wert — COMIC existiert.
- Panel-Tap-Logik gegen un-zurückskalierte Downscale-Koordinaten (Treffer daneben).
- Tap/Zoom gegen Viewport statt Content-Rechteck (Letterbox ignorieren) — Treffer und Zoom-Pivot stimmen nicht.

Bezug: [[viewer-type-resolution]], [[eink-ui]], `guided-view`-Modul, Naht B
(`architecture-seams.md`). Tests: `GuidedNavigatorTest`, `PanelGeometryTest`,
`ResolveViewerTypeTest` (COMIC-Stufen).
