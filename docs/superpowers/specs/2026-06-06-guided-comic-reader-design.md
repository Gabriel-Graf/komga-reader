# Guided Comic Reader — Design-Spec

**Datum:** 2026-06-06
**Status:** genehmigt (Brainstorming abgeschlossen)
**Bezug:** Spec §11 (Guided-View-UI offen), `guided-view`-Modul (Detektor fertig),
Invariante 4 (deterministische Viewer-Auflösung), Naht B (Render/E-Ink).

## Ziel

Ein **geführter Comic-Lesemodus** für E-Ink: im Kern ein Paged-Reader (volle Seite,
Seitenblättern), erweitert um Panel-für-Panel-Zoom. Comics haben oft kleinen Text;
auf 7"-E-Ink ist eine ganze Seite schwer lesbar. Der Nutzer tippt ein Panel an und
liest es vergrößert; vorwärts springt zum nächsten Panel — ohne Zoom-Out — und über
Seitengrenzen hinweg.

Der `PanelDetector` (`guided-view`, pure Kotlin XY-Cut) liefert Panels **bereits in
Leserichtung sortiert**. Damit ist „in welche Richtung liegt das nächste Bild" gelöst:
**nächstes Panel = nächster Index der geordneten Liste.** Keine zusätzliche
Richtungserkennung nötig.

## Nicht-Ziele (YAGNI)

- Keine ML-/OpenCV-Panel-Erkennung — der XY-Cut-Detektor genügt.
- Kein MuPDF-Render-Pfad für Comics — Streaming via Coil bleibt.
- Kein Auto-Erkennen des Inhaltstyps — Viewer-Auflösung bleibt deterministisch.
- Kein Panel-Übergang mit Animation/Pan — E-Ink, sofortige Zustandswechsel.

## Architektur

### 1. Naht B — neuer `ViewerType.COMIC`

`ViewerType` erweitern: `PAGED, WEBTOON, EPUB, COMIC`.

`ResolveViewerType.map(ContentType)` ändern — **nur eine Zeile**:

```
ContentType.MANGA   -> ViewerType.PAGED      // unverändert
ContentType.COMIC   -> ViewerType.COMIC      // war PAGED
ContentType.WEBTOON -> ViewerType.WEBTOON    // unverändert
ContentType.NOVEL   -> ViewerType.EPUB       // unverändert
```

`ContentType` hat COMIC bereits — **kein neuer Enum-Wert**. Die 6-stufige
Prioritätsregel (Override → EPUB → VERTICAL/WEBTOON → Shelf-Fallback → Format → sonst)
bleibt unangetastet. Ein Comic-Regal (`defaultContentType = COMIC`) oder ein
Serien-Override `COMIC` löst künftig auf `ViewerType.COMIC` auf.

App-Seite: `ViewerMode` (aktuell `PAGED, WEBTOON`) um `COMIC` erweitern;
`ViewerType.COMIC → ViewerMode.COMIC`. `ReaderRoute` routet `ViewerMode.COMIC` auf den
neuen `ComicReaderScreen`. `komga-viewer-type-resolution`-Skill entsprechend nachziehen.

### 2. Pixelquelle — Coil-Bitmap, kein MuPDF

Der Detektor braucht `RenderedPage` (Pixel-Array). Die Komga-Seite wird von Coil
ohnehin zu einem Bitmap dekodiert.

- Coil-`AsyncImage`/`ImageRequest` mit `target`/`listener`, das dekodierte Bitmap abgreifen.
- Bitmap auf Detektions-Auflösung **downscalen** (~1000px Breite) — XY-Cut braucht keine
  volle Auflösung, das spart Speicher und Zeit.
- `RenderedPage(pixels, width, height)` aus dem Downscale-Bitmap bauen
  (`getPixels` → IntArray ARGB_8888).
- `PanelDetector.detect(page, direction)` → `List<PanelRect>` (im Downscale-Raum).
- **Koordinaten zurückskalieren** auf die tatsächliche Anzeigegröße der Seite für das
  Zoom-Rechteck (`scaleX = displayW / detectW` etc.).

**Leserichtung:** aus `Series.readingDirection`: `LTR → LEFT_TO_RIGHT`,
`RTL → RIGHT_TO_LEFT`. (VERTICAL/WEBTOON landen nie hier — die sind WEBTOON.)
Default ohne Angabe: `LEFT_TO_RIGHT`.

**Caching:** erkannte Panels pro Seitenindex cachen (`Map<Int, List<PanelRect>>`),
Detektion läuft je Seite genau einmal, auf `Dispatchers.Default`.

### 3. Zustände & Interaktion

Zwei Zustände: **Vollseite** und **Gezoomt**.

| Zustand | Tap auf Panel | Tap Gutter/Rand | Tap links / rechts | HW-Tasten (Boox/Volume) |
|---|---|---|---|---|
| **Vollseite** (≥2 Panels) | Zoom in **dieses** Panel | Chrome-Overlay | — (ganze Tap-Fläche = Panel-Trefferzonen) | ganze Seite blättern |
| **Gezoomt** | — | — | links = voriges Panel, rechts = nächstes Panel | voriges / nächstes Panel |
| **Vollseite** (0/1 Panel, Fallback) | — | Mitte = Chrome | links = vorige, rechts = nächste Seite | ganze Seite blättern |

Regeln:
- **Einstieg:** Tap auf ein beliebiges Panel startet Guided **genau dort** (Tap auf
  Panel 3 von 4 → Start bei Index 2). Tap **nicht** auf ein Panel (Gutter/Rand) →
  Chrome-Overlay. Wird nie ein Panel getippt, bleibt es ein reiner Paged-Reader und die
  Tasten blättern Seiten.
- **Gezoomt, Mitte-Tap:** Zoom-Out zurück auf die Vollseite.
- **Panel-Zoom:** Panel-Rechteck mit kleinem Rand bildschirmfüllend einpassen,
  **sofort** (keine Animation).
- **Seitengrenze:** „nächstes" über das letzte Panel hinaus → Seite still im Hintergrund
  umblättern, erstes Panel der Folgeseite zeigen. „voriges" vor dem ersten Panel →
  letztes Panel der Vorseite. Hat die Nachbarseite 0/1 Panel → ihre Vollseite zeigen.

### 4. Fallback + Toggle

- **Auto-Fallback:** Detektor liefert 0 oder 1 Panel → diese Seite verhält sich wie
  reiner Paged (ganze Seite, Seiten-Tap-Zonen Drittel-Layout). Splash-/Doppelseiten
  kommen so am Stück. Detektor-Schwellen bleiben Default.
- **Manueller Toggle:** Chrome-Overlay enthält einen Guided-Toggle (an/aus). Aus →
  der ganze `ComicReaderScreen` verhält sich wie ein Paged-Reader (keine Panel-Detektion,
  keine Panel-Taps). Zustand lebt im `ComicReaderViewModel` (Session). Default: an.

### 5. E-Ink-Refresh

Panel-Wechsel und Zoom-Out sind Bildwechsel → **Full-Refresh** über den bestehenden
`OnyxRefresher`/`RefreshScheduler` (analog Seitenwechsel im Paged-Reader). Kein blindes
`invalidate`. Auf Nicht-Boox No-Op (HW-gated).

## Komponenten (Schnitt)

| Einheit | Modul | Aufgabe | Hängt ab von |
|---|---|---|---|
| `PanelDetector` (vorhanden) | `guided-view` | XY-Cut → geordnete `List<PanelRect>` | `domain` (RenderedPage) |
| `ViewerType.COMIC` + `ResolveViewerType` | `domain` | Auflösung COMIC | — |
| `PanelGeometry` (neu, pure) | `app` o. `guided-view` | Koordinaten-Rückskalierung Downscale→Display, Zoom-Rechteck | — |
| `GuidedNavigator` (neu, pure) | `app` | Panel-Sequenz-Logik inkl. Seitengrenzen (Index→(Seite,Panel)) | — |
| `ComicReaderViewModel` (neu) | `app` | Zustand (Vollseite/Gezoomt, aktuelle Seite/Panel), Detektion anstoßen, Cache, Toggle | `guided-view`, Source, Settings |
| `ComicReaderScreen` (neu) | `app` | Compose: Vollseite (Coil) + gezoomtes Panel + Tap-Zonen + Chrome | ViewModel |

`GuidedNavigator` und `PanelGeometry` sind **pure** → TDD-freundlich, isoliert testbar.
Der Screen ist dünne imperative Shell.

## Datenfluss

```
Series.readingDirection ─┐
Komga-Seite (URL) → Coil → Bitmap ─ downscale → RenderedPage
                                                    │
                              PanelDetector.detect ─┴→ List<PanelRect> (cache[page])
                                                    │
   Tap (x,y) → PanelGeometry.hitTest → PanelIndex ──┤
                                                    │
   forward/back → GuidedNavigator.step(page,panel) → (page', panel') │
                                                    │
   PanelGeometry.zoomRect(panel, displaySize) → Compose graphicsLayer/Crop
```

## Fehlerbehandlung

- Bitmap-Decode schlägt fehl / Coil liefert nichts → Seite als Vollseite ohne Panels
  (Fallback). Kein Crash.
- Detektion wirft/leer → leere Liste → Fallback.
- Nachbarseite noch nicht dekodiert beim Seitengrenzen-Sprung → Vollseite zeigen, bis
  ihre Panels da sind (lazy), dann erstes Panel.

## Tests (TDD)

Pure Unit-Tests zuerst (Red-Green-Refactor):

- `PanelGeometry`: Rückskalierung Downscale→Display (gesetzt), Zoom-Rechteck mit Rand,
  `hitTest` trifft richtiges Panel / trifft kein Panel (Gutter) → null.
- `GuidedNavigator`: vorwärts innerhalb Seite; vorwärts über letztes Panel → nächste
  Seite Panel 0; rückwärts vor erstes Panel → Vorseite letztes Panel; Nachbarseite mit
  0/1 Panel → Vollseite; Grenzen (erste/letzte Seite des Buchs).
- Fallback: 0/1 Panel → Paged-Verhalten.
- `ResolveViewerType`: COMIC-Override und COMIC-Shelf-Fallback → `ViewerType.COMIC`
  (Ergänzung zu bestehenden Stufen-Tests).
- `PanelDetector`: bestehende Tests bleiben grün.

E2E: lokale Test-Komga (siehe `local-test-komga`), Comic-Serie, `COMIC`-Tag via
`PATCH /api/v1/series/{id}/metadata` setzen → Reader öffnet `ComicReaderScreen`,
Panel-Tap zoomt, Vorwärts läuft über Seitengrenze. Verifikation auf Emulator
`eink_test` (1264×1680@300) bzw. echter Boox.

## Abschluss-Artefakt

Domain-Skill `komga-guided-comic-reader` (`.claude/skills/`), Onyx-/Naht-konform, hält die
verbindlichen Interaktions- und Auflösungsregeln fest (analog `komga-viewer-type-resolution`).
