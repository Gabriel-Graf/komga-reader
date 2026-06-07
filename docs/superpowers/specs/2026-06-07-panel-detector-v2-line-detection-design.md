# Panel-Detektor v2 — achsenparallele Rahmen-Linien + Bubble-Filter — Design-Spec

**Datum:** 2026-06-07
**Status:** genehmigt (Brainstorming)
**Bezug:** erweitert den Flood-Fill/CC-Detektor (`2026-06-07-robust-panel-detector-design.md`). Gehört zu
[[guided-comic-reader]], Branch `feat/guided-comic-reader`.

## Problem (auf dem Gerät belegt)

Der Flood-Fill-Detektor trennt nur über **weiße Gutter**. Schwarz-umrandete, eng aneinander liegende
Panels (z. B. Red Hood) ohne Weißraum verschmelzen zu **seitenbreiten Querbändern** → `wN≈1.0` →
`fitScale≈1` → „Zoom zeigt ganze Seite". Zusätzlich werden manche **Sprechblasen fälschlich als Panel**
erkannt.

**Baseline-Messung** (aktueller Detektor, 300+ zufällige Seiten aus `/mnt/nas/Manga/Comics`,
diverse Comics): **41 % der Seiten → 1 Panel (Fallback, kein Zoom)**, 59 % ≥2 Panels, Median 2,
Ausreißer bis 24 Panels (Über-Segmentierung). Viele der 1-Panel-Seiten sind echte Splash/Cover, aber
ein erheblicher Teil ist die Schwarz-Rahmen-Unter-Segmentierung.

## Ziel

Panels auch ohne weiße Gutter zuverlässig trennen (schwarz-umrandete/dichte Layouts), Sprechblasen nie
als Panel zählen, Über-Segmentierungs-Ausreißer reduzieren. **Interface unverändert:**
`PanelDetector.detect(page, dir): List<PanelRect>`. Reines Kotlin, host-testbar.

## Nicht-Ziele (YAGNI)

- Keine Voll-Winkel-Linienerkennung — nur **achsenparallel (0°/90°)**. Schräge Panels → Bounding-Box
  (der Zoom ist ohnehin achsenparallel).
- Keine ML/OpenCV.
- Keine perfekte Trennung pathologischer Künstler-Layouts — solche Seiten dürfen auf Vollseite
  zurückfallen (Degenerate-Guard), statt falsch zu zoomen.

## Algorithmus (Erweiterung der bestehenden Pipeline)

Bestehend bleibt: Otsu-Binarisierung → Edge-Seed-Gutter-Flood → Connected-Components → Filter/Merge →
Lesereihenfolge. **Neu** zwischen CC und Lesereihenfolge:

1. **Rahmen-Linien-Split** (`BorderLineSplit.kt`, pure): Für jede Komponente, die zu groß ist
   (z. B. > 50 % Seitenbreite **und** > 50 % Seitenhöhe, oder seitenbreites Band), rekursiv nach
   **starken durchgehenden achsenparallelen Rahmenlinien** suchen und dort schneiden:
   - Kanten-/Tinten-Profil pro Zeile/Spalte **innerhalb** der Komponenten-Bbox (Sobel-ähnlicher
     Gradient oder Dunkel-Dichte).
   - Eine **horizontale Rahmenlinie** = ein Zeilenband, das über einen großen Anteil (z. B. ≥ 80 %)
     der Komponentenbreite durchgehend dunkel/kantig ist (Hough-Akkumulator auf 0°). Vertikal analog (90°).
   - Am stärksten Linie schneiden → zwei Sub-Rechtecke → rekursiv (Tiefe begrenzt, min-Panel-Größe).
   - Trennt schwarz-umrandete Nachbar-Panels (geteilte Rahmenlinie wird erkannt und geschnitten).

2. **Sprechblasen-/False-Positive-Filter** (`PanelDetector`): Eine Box wird verworfen, wenn sie
   **klein** (z. B. < 6 % Seitenfläche) **und vollständig in einer größeren Box enthalten** ist
   (Blase = helle Insel im Panel) — zusätzlich zu min-Fläche-Filter und Contained-Merge. Optional:
   sehr hohe Hintergrund-(Weiß-)Quote in der Box als zusätzliches Bubble-Indiz.

3. **Über-Segmentierung dämpfen:** zu kleine/zahlreiche Fragmente mergen bzw. verwerfen, damit
   Ausreißer (24 Panels) verschwinden; deterministische Obergrenze pro Seite ist **kein** Muss, aber
   Mini-Fragmente unter min-Fläche fliegen raus.

## Komponenten (Schnitt)

| Einheit | Datei (`guided-view`) | Aufgabe | pure? |
|---|---|---|---|
| `BorderLineSplit` (neu) | `BorderLineSplit.kt` | Komponente an H/V-Rahmenlinien rekursiv splitten | ja |
| `PanelDetector` (erweitert) | `PanelDetector.kt` | Pipeline + Bubble-Filter + Split-Integration | ja |
| bestehend | `ImageBinarization`/`GutterFill`/`RegionLabeling`/`ReadingOrder` | unverändert | ja |

`BorderLineSplit` braucht die binarisierte/Kanten-Info der Region → bekommt die `RenderedPage`
(oder die Dunkel-Maske) + die Box, arbeitet darauf. Pure, host-testbar.

## Validierung — Akzeptanz-Gate (300-Bilder-Stichprobe)

`RealPageHarness` (erweitert/Skript): zieht **300 zufällige Seiten** aus `/mnt/nas/Manga/Comics`
(jedes Comic angefasst, zufällige Innenseiten), auf ~1000px skaliert, in gitignored
`guided-view/realpages/sample300/`. Detektor drauf → Overlays + **Statistik** (Panels/Seite-Verteilung,
Fallback-Anteil, Ausreißer). **Vorher/Nachher:**
- Vorher (Flood-Fill): 41 % Fallback.
- Nachher-Ziel: deutlich weniger Fallback auf **echten** Mehr-Panel-Seiten, keine Blasen-FPs in der
  Sichtprüfung, keine 20+-Ausreißer. (Echte Splash/Cover bleiben 1 Panel — korrekt.)
- Manuelle Sichtprüfung einer Auswahl von Overlays (inkl. der Blasen-/Schwarz-Rahmen-Fälle).

## Tests (TDD, committed synthetisch)

- **Schwarz-umrandetes 2×2-Raster ohne Weißgutter** (Panels durch 4px dunkle Linien getrennt) → 4 Panels.
- **Seitenbreites Band mit innerer vertikaler Rahmenlinie** → 2 Panels (Kernfall Red Hood).
- **Sprechblase** (helle Insel mit „Text"-Tinte im Panel) → **kein** eigenes Panel.
- **Weiß-Gutter-Raster** bleibt korrekt (keine Regression).
- **Full-Bleed** bleibt erhalten.
- `BorderLineSplit`-Unit-Tests: findet die stärkste H/V-Linie, schneidet korrekt, Rekursion terminiert,
  splittet **nicht** bei fehlender klarer Linie (kein Über-Splitten von Artwork).

## Aufräumen / Doku

- `guided-comic-reader`-Skill um die Linien-Split-Stufe + Bubble-Filter ergänzen.
- Der ursprünglich als YAGNI zurückgestellte „interne Split" wird hiermit umgesetzt (Begründung:
  Gerätetest zeigte Schwarz-Rahmen-Unter-Segmentierung).
