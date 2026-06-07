# Robuster Panel-Detektor — Design-Spec

**Datum:** 2026-06-07
**Status:** genehmigt (Brainstorming)
**Bezug:** ersetzt den projection-profile-XY-Cut in `guided-view`, der echte (Farb-)Comicseiten
unter-segmentiert (Root-Cause-Analyse 2026-06-07: Default `gutterMaxInk=0.02` zu streng →
seitenbreite Merge-Panels → `zoomScale≈1` → „Zoom zeigt ganze Seite"). Gehört zum
[[komga-guided-comic-reader]] auf Branch `feat/guided-comic-reader`.

## Problem

Der XY-Cut wurde nur gegen synthetische Seiten mit perfekten reinweißen 40px-Guttern getestet.
Auf echten Seiten (belegt an Amazing Spider-Man 001, headless gemessen): sobald Art/Text eine
Gasse kreuzt — v. a. die schmalen **vertikalen** Spalten-Gassen — reißt der Gutter-Lauf, Spalten
verschmelzen zu fast seitenbreiten Panels. Resultat im Reader: Tap auf ein Panel zoomt auf ~die
ganze Seite.

## Ziel

Ein robuster, reiner-Kotlin-Detektor, der echte Comicseiten mit gemischten Panel-Layouts
zuverlässig in Panels zerlegt. **Interface unverändert:**
`PanelDetector.detect(page: RenderedPage, dir: ReadingDirection): List<PanelRect>` —
`ComicPageLoader` und `ComicReaderViewModel` bleiben unberührt.

## Nicht-Ziele (YAGNI)

- Keine OpenCV/ML (Naht B: reines Kotlin, host-testbar).
- Keine perfekte Erkennung beliebig schräger/überlappender Künstler-Layouts — solche Seiten
  fallen sauber auf Vollseite zurück (Degenerate-Guard), statt falsch zu zoomen.
- Kein Re-Tuning des alten XY-Cut (wurde verworfen).

## Algorithmus — Hybrid Flood-Fill + Connected-Components

Eingabe ist die von `ComicPageLoader` auf ~1000px Breite herunterskalierte `RenderedPage`
(ARGB-IntArray). Pipeline in kleinen, je pure & einzeln testbaren Einheiten:

1. **Binarisierung** (`ImageBinarization.kt`): Luminanz `0.299r+0.587g+0.114b`. Schwelle
   **adaptiv via Otsu** (Histogramm, maximiert Inter-Klassen-Varianz) statt fixem 128 →
   robust gegen Tonwert-/Farbschwankungen. Ergebnis: `BooleanArray` „Hintergrund (hell)".

2. **Gutter-Flood-Fill / Edge-Seed** (`GutterFill.kt`): BFS über zusammenhängende
   Hintergrundpixel, **geseedet nur von hellen Pixeln am Seitenrand**. Markiert Margin +
   inneres Gutter-Netz. Ist eine Seitenkante dunkel (Full-Bleed-Panel reicht bis dort),
   wird dort nicht geseedet → das Panel bleibt erhalten. 4-Konnektivität.

3. **Connected-Components** (`RegionLabeling.kt`): Label aller **nicht gefluteten** Pixel
   (iteratives Two-Pass- oder BFS-Labeling, 8-Konn). Jede Komponente = eine von Guttern
   umschlossene Panel-Region; innere Weißflächen/Sprechblasen bleiben Teil des Panels
   (vom Rand-Flood nie erreicht). → Bounding-Box je Komponente.

4. **CC-Verfeinerung** (`PanelDetector.kt`): 
   - Mini-Boxen unter `minPanelAreaFraction` (z. B. < 1 % Seitenfläche) verwerfen
     (Text-/Logo-Fragmente).
   - Enthaltene/stark überlappende Boxen mergen.
   - Eine ungewöhnlich große Komponente (> ~60 % Fläche) **einmal** mit einem leichten
     internen Projektions-Check nach einem klaren weißen Durchgangs-Gutter absuchen und
     ggf. splitten (der Hybrid-Teil — fängt von Flood verpasste dünne Innen-Gassen).

5. **Lesereihenfolge** (`ReadingOrder.kt`): Boxen in **Zeilen-Bänder** gruppieren (Boxen mit
   hinreichender vertikaler Überlappung = eine Reihe), Bänder oben→unten, innerhalb eines
   Bandes links→rechts (LTR) bzw. rechts→links (RTL). Gibt geordnete `List<PanelRect>` zurück.

## Degenerate / Fallback

Der Detektor liefert **rohe** Panels (reine Geometrie). Die UX-Entscheidung trifft der
**Degenerate-Guard im `ComicReaderViewModel`**: erkennt der Detektor 0/1 Panel **oder** deckt
ein einzelnes Panel > ~85 % der Seitenfläche ab → die Seite gilt als Fallback (Vollseite,
kein Zoom, Drittel-Tap-Zonen). So entsteht nie ein „Zoom auf ganze Seite" — auch wenn die
Detektion auf einer harten Seite scheitert.

## Mitgelieferte Integrations-Fixes (für korrektes End-to-End)

1. **Degenerate-Guard** (`ComicReaderViewModel`): `unitsAt`/Panel-Übernahme behandelt
   `panels.size < 2 || maxPanelAreaFraction > 0.85` als 1 Einheit (Vollseite). 
2. **Letterbox-Fix** (`ComicReaderScreen`): Bei `ContentScale.Fit` ist die Hochformat-Seite
   seitlich geletterboxt (gemessen ~6.8 % Balken je Seite bei 1264px-Viewport). Tap-Offset
   **und** `transformOrigin` gegen das tatsächlich dargestellte Bild-Rechteck rechnen
   (aus Bild-Seitenverhältnis vs. Viewport), nicht gegen den Viewport. Bild-Aspekt kommt aus
   `PageDetection.pageWidth/pageHeight` bzw. der Coil-Intrinsic-Größe.

## Komponenten (Schnitt)

| Einheit | Datei (`guided-view`) | Aufgabe | pure? |
|---|---|---|---|
| `ImageBinarization` | `ImageBinarization.kt` | RenderedPage → Hintergrund-Maske (Otsu) | ja |
| `GutterFill` | `GutterFill.kt` | Edge-Seed-Flood → geflutete Maske | ja |
| `RegionLabeling` | `RegionLabeling.kt` | Komponenten-Labeling → Bounding-Boxes | ja |
| `ReadingOrder` | `ReadingOrder.kt` | Boxen → Lesereihenfolge (LTR/RTL) | ja |
| `PanelDetector` | `PanelDetector.kt` | Orchestrierung + CC-Verfeinerung, gleiche API | ja |

Alle pure Kotlin, einzeln host-testbar. Der alte XY-Cut-Code wird entfernt.

## Datenfluss

```
RenderedPage → ImageBinarization(Otsu) → bgMask
  bgMask + Seitenrand → GutterFill(edge-seed) → floodedMask
  !floodedMask → RegionLabeling → List<Box>
  → CC-Verfeinerung (filter/merge/split) → ReadingOrder(dir) → List<PanelRect>
```

## Tests

**Synthetischer Seiten-Generator** (Test-Helper in `guided-view/src/test`): baut `RenderedPage`
aus einer Spec (Panel-Rects, Gutterbreite, Hintergrundfarbe, optional: Sprechblasen-Weißinseln,
Full-Bleed-Kanten, „verschmutzte" Gutter mit X % Tinte, JPEG-artiges Rauschen).

**Committed Regressionstests (TDD):**
- Sauberes 3×2-Raster → 6 Panels in korrekter LTR-Reihenfolge.
- Vertikale Gasse mit ~4 % Tinte (Art kreuzt) → trennt trotzdem (Flood-Fill-Stärke).
- Sprechblase (weiße Insel im Panel) → bleibt **ein** Panel.
- Full-Bleed-Panel bis Seitenkante → erhalten (nicht ins Margin geflutet).
- Blank-Seite → leere Liste.
- Einzelbild-Seite (1 Panel ~Vollseite) → 1 Panel (Guard ⇒ Fallback).
- RTL → umgekehrte Reihenfolge.
- Otsu-Schwelle: heller vs. dunkler Seitenhintergrund → korrekte Trennung.

**Lokaler Real-Harness (gitignored, nicht in CI):** liest echte Seiten aus einem lokalen Ordner
(`guided-view/realpages/`, in `.gitignore`) — befüllt aus den NAS-cbz (Korpus:
`/mnt/nas/Manga/Comics/Marvel/The Amazing Spider-Man/Amazing Spider-Man 001…cbz`, 37 Seiten
~1988×3056 inkl. einer Doppelseite 3975×3056, gemischte Panel-Layouts). Skaliert die Seite wie
`ComicPageLoader` auf ~1000px herunter, läuft `detect()` und schreibt Overlay-PNGs (Panel-Boxen +
Reihenfolge-Nummern) zur manuellen Sichtprüfung. Keine urheberrechtlich geschützten Seiten im Repo.

## Aufräumen / Doku

- Alter XY-Cut entfernt; `PanelDetectorTest` an das neue Verhalten/Generator angepasst
  (Confidence/Communication-Kriterium: synthetische Klar-Fälle bleiben).
- `komga-guided-comic-reader`-Skill um neue Detektion (Flood-Fill/CC, Otsu) + Degenerate-Guard +
  Letterbox-Fix ergänzen.
- `.gitignore`: `guided-view/realpages/` ergänzen.
