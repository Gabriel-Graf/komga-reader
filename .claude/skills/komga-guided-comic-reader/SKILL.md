---
name: komga-guided-comic-reader
description: Use when touching the Guided Comic Reader in the Komga-Reader (ViewerType.COMIC, ComicReaderScreen/ViewModel, panel-by-panel zoom, PanelDetector wiring, GuidedNavigator/PanelGeometry). Hält die verbindlichen Interaktions-, Pixelquellen- und Auflösungsregeln fest, damit der geführte Panel-Lesefluss und die E-Ink-Invarianten nicht versehentlich gebrochen werden.
---

# Guided Comic Reader (Domain-/Reader-Regel)

Comic-Lesemodus für E-Ink: im Kern Paged-Reader (volle Seite, Seitenblättern) +
**Panel-für-Panel-Zoom**. Naht-konform — die Panel-Erkennung liefert die externe Lib **comic-cutter**
(`io.github.gabriel-graf:comic-cutter-jvm` + `comic-cutter-onnx-jvm`, Paket `com.panela.comiccutter.*`)
über die `PanelSource`-Naht (geometrisch ODER ML); Pixel kommen aus Coil, Streaming bleibt. Das frühere
In-Tree-Modul `:guided-view` ist gelöscht. Volltext:
`docs/superpowers/specs/2026-06-06-guided-comic-reader-design.md`.

## Auflösung — `ViewerType.COMIC`

`ViewerType = PAGED, WEBTOON, EPUB, COMIC`. In `ResolveViewerType.map`:

```
MANGA   -> PAGED      COMIC -> COMIC      WEBTOON -> WEBTOON      NOVEL -> EPUB
```

`ContentType.COMIC` existiert bereits — **kein neuer Enum-Wert**. Die 6-stufige
Prioritätsregel aus [[komga-viewer-type-resolution]] bleibt unverändert; nur das COMIC-Mapping
wechselt von PAGED auf COMIC. App: `ViewerMode.COMIC`, `ReaderRoute` → `ComicReaderScreen`.

## Pixelquelle — Coil, NIE MuPDF

Kein Voll-Download fürs Streaming. Ablauf je Seite:
Coil dekodiert die Komga-Seite → Bitmap abgreifen → auf ~1000px Breite **downscalen** →
`RenderedPage` (ARGB-IntArray) → `ComicPageLoader.detect(page, panelSource)` ruft die vom
`PanelSourceProvider` gelieferte `com.panela.comiccutter.PanelSource` auf und **sortiert** das Ergebnis
mit `ReadingOrder.sort(panels, LEFT_TO_RIGHT)` in Lesereihenfolge. **Diese Sortierung ist Pflicht:** die
ML-`PanelSource` liefert Panels in **Confidence-Reihenfolge**, nicht in Lesereihenfolge; die geometrische
Quelle war schon sortiert → die Sortierung ist dort idempotent.
Panel-Koordinaten liegen im Downscale-Raum → **zurückskalieren** auf Display-Größe
(`PanelGeometry`) fürs Zoom-Rechteck. Detektion pro Seite **einmal**, gecacht
(`Map<page, List<PanelRect>>`), auf `Dispatchers.Default`.

**Welche `PanelSource`? (`PanelSourceProvider`, @Singleton):** `GeometricPanelSource()` per Default;
`MlPanelSource(OnnxModelRunner(bytes), MlFilter(...))`, wenn `SettingsRepository.useMlDetection` an ist
(Toggle in Settings → Comic, Default **true**) **und** ein `PANEL_MODEL`-Plugin (data-only APK mit
ONNX-Modell-Asset) installiert ist (`PluginHost.binaryDataPluginBytes(PANEL_MODEL)`). Jeder Fehler
(kein Plugin, ONNX-Init) **degradiert sauber** auf geometrisch; die gewählte Quelle wird gecacht. Der
Reader ist **agnostisch** gegen geometrisch-vs-ML — nur die rohe Erkennung unterscheidet sich.

Leserichtung: `Series.readingDirection` `LTR → LEFT_TO_RIGHT`, `RTL → RIGHT_TO_LEFT`,
Default `LEFT_TO_RIGHT`. (VERTICAL/WEBTOON sind nie COMIC.)

## Der zentrale Punkt: „nächstes Panel" ist gelöst

`ComicPageLoader` liefert Panels **in Leserichtung sortiert** (`ReadingOrder.sort`, s. o.). Nächstes
Panel = nächster Listenindex. **Keine eigene Richtungserkennung bauen** — das ist der häufigste
Fehler. `GuidedNavigator` steppt nur durch die sortierte Liste und über Seitengrenzen.

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
E-Ink-Designsprache gilt ([[komga-eink-ui-polish]]): flach, 1.5px-Border, monochrom, keine Animation.

## Detektor-Algorithmus — jetzt in der externen Lib `comic-cutter`

Der Erkennungs-Algorithmus selbst lebt **nicht mehr im App-Repo** — er ist mit `:guided-view` ausgezogen
in die Lib **comic-cutter** (`com.panela.comiccutter.*`). Zwei Quellen hinter der `PanelSource`-Naht:
- **`GeometricPanelSource`** — der reine Flood-Fill/Connected-Components-Detektor (Profil-XY-Cut +
  Flood-Fallback), funktional der Nachfolger des alten In-Tree-`PanelDetector`.
- **`MlPanelSource`** — ein ONNX-Modell (`OnnxModelRunner` aus `comic-cutter-onnx-jvm`, Unter-Paket
  `com.panela.comiccutter.onnx`) hinter `MlFilter` (Confidence-/Overlap-Schwellen). Modell-Bytes kommen
  aus einem installierten `PANEL_MODEL`-Plugin.

**Verhaltens-Vorbehalt:** der geometrische Algorithmus der Lib weicht vom alten In-Tree-Detektor ab
(er ergänzt eine Merge-über-Split-Arbitrierung). Geometrische Panel-*Ergebnisse* können also leicht von
früher abweichen — das ist **erwartet, kein Regress**.

**Degenerate-Guard (im ViewModel — host-seitig, unverändert):** Liefert die `PanelSource` <2 Panels ODER
belegt ein Panel >85 % der Seitenfläche → Vollseite-Fallback (Seite verhält sich wie Paged, kein
Panel-Zoom). Gilt für geometrisch UND ML gleich.

**Letterbox-bewusste Tap/Zoom:** Tap-Koordinaten und Zoom-Rechteck rechnen gegen das
Content-Rechteck (bei ContentScale.Fit: `contentW`×`contentH` innerhalb des Viewports), nicht
gegen den rohen Viewport. `PanelGeometry.fitScale(panel, contentW, contentH, viewportW, viewportH,
marginFraction)` liefert den korrekten Faktor inklusive Letterbox-Offset.

## Schnitt (was wohin)

- `PanelSource` (`GeometricPanelSource`/`MlPanelSource`), `PanelGeometry`, `GuidedNavigator`,
  `ReadingOrder` — **extern in `comic-cutter`** (`com.panela.comiccutter.*`), nicht im App-Repo; nicht
  forken, gegen die Lib programmieren.
- `PanelSourceProvider` (`app/ui/reader`, @Singleton) — wählt geometrisch vs. ML, cacht, degradiert.
- `ComicPageLoader` (`app/ui/reader`) — Coil-Pixel → `RenderedPage` → `panelSource.detect` →
  `ReadingOrder.sort`.
- `ComicReaderViewModel` — Zustand, Detektion anstoßen (injiziert `PanelSourceProvider`), Cache,
  Toggle, Degenerate-Guard.
- `ComicReaderScreen` — dünne Compose-Shell (Coil-Vollseite + gezoomtes Panel + Tap-Zonen).

## Anti-Pattern (sofort ablehnen)

- Eigene „Richtungserkennung" fürs nächste Panel — `ReadingOrder.sort` ordnet schon.
- Die `ReadingOrder.sort`-Sortierung weglassen, weil „geometrisch war schon sortiert" — die ML-Quelle
  liefert Confidence-Reihenfolge; ohne Sortierung springt Guided durcheinander.
- MuPDF/Voll-Download für Comic-Pixel — Coil-Bitmap downscalen.
- Animierter Panel-Pan/Zoom auf E-Ink.
- Neuer `ContentType`-Wert — COMIC existiert.
- Panel-Tap-Logik gegen un-zurückskalierte Downscale-Koordinaten (Treffer daneben).
- Tap/Zoom gegen Viewport statt Content-Rechteck (Letterbox ignorieren) — Treffer und Zoom-Pivot stimmen nicht.

Bezug: [[komga-viewer-type-resolution]], [[komga-eink-ui-polish]], `comic-cutter`-Lib (Panel-Erkennung,
früher das gelöschte `:guided-view`-Modul), Naht B + Naht-A-Panel-Modell-Item (`architecture-seams.md`).
`GuidedNavigator`/`PanelGeometry` werden in der `comic-cutter`-Lib getestet (nicht mehr im App-Repo);
hier bleibt `ResolveViewerTypeTest` (COMIC-Stufen).
