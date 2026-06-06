# Phase 1 · Plan 2/4 — MuPDF Engine-Spike

**Goal:** Das höchste Projektrisiko zuerst retten — beweisen, dass MuPDF die drei Pflicht-Formate (CBZ/Comic, PDF, EPUB/Novel) tatsächlich zu Pixeln rendert, mit einem **lauffähigen** End-to-End-Test.

**Approach:** `mutool` (MuPDF 1.23.10) ist host-seitig installiert — exakt dieselbe Engine, die später via JNI auf Android angebunden wird. Statt auf ein fehlendes Android-Gerät zu warten, rendert der Spike die Formate host-seitig und verifiziert das Raster-Ergebnis. Damit ist die Engine-Eignung bewiesen, bevor eine Zeile JNI/NDK geschrieben wird.

**Status:** ✅ ABGESCHLOSSEN (2026-06-06)

---

## Durchführung
Script: `tools/spikes/mupdf/render_spike.py` (Python + PIL + `mutool`).

1. Erzeugt je eine Fixture pro Format:
   - **CBZ**: ZIP aus zwei generierten PNG-Seiten (Comic/Manga-Pfad).
   - **PDF**: zweiseitiges Bild-PDF (gescannter Comic-Pfad).
   - **EPUB**: minimal gültiges EPUB3 mit Textkapitel (Reflow-Pfad).
2. Rendert Seite 1 jeder Datei mit `mutool draw -r 150 -o out.png <doc> 1`.
3. Prüft pro Ergebnis: PNG existiert, Maße ≥ 200px, und enthält echten dunklen Inhalt (≥ 500 Pixel < Helligkeit 80) → nicht leer.

Ausführen: `python3 tools/spikes/mupdf/render_spike.py` (Exit 0 = alle bestanden).

## Ergebnis (verifiziert)
```
MuPDF: mutool version 1.23.10
Format Render   Detail
CBZ    OK       1250x1875, 1169398 dunkle Pixel
PDF    OK       1667x2500, 2090406 dunkle Pixel
EPUB   OK       875x1240,  84896 dunkle Pixel
ERGEBNIS: ALLE FORMATE BESTANDEN ✓
```

## Schlussfolgerungen
- **MuPDF deckt alle drei Formate aus einer Engine ab** — die Architektur-Annahme (eine Engine hinter `Document`/Naht B) hält. EPUB wird reflowt (875×1240 bei 150 DPI passend skaliert), Bild-Formate in voller Auflösung.
- Die `Document.renderPage(...) → RenderedPage(ARGB)`-Abstraktion ist tragfähig: `mutool draw` liefert pro Seite ein Raster, exakt das, was der JNI-Wrapper später in eine `android.graphics.Bitmap` kopiert.

## Bewusst auf Plan 1.4 verschoben (gerätegebunden)
- **Android-JNI-Anbindung** (`:render-core`-Modul, C++-Wrapper auf `fz_open_document_with_stream`/`fz_run_page` → ARGB-Pixmap, `external`-Kotlin-Methoden) und der **NDK-Build von libmupdf** als `.so` pro ABI. Diese sind nur auf einem echten Gerät/Emulator laufzeit-verifizierbar; gebaut/gelinkt wird mit Plan 1.4, wenn Reader-UI + Emulator-Target stehen. Die Engine-Eignung selbst ist mit diesem Spike bereits bewiesen, also ist das Restrisiko dort rein Integrations-/Build-Natur, nicht „funktioniert MuPDF überhaupt".
