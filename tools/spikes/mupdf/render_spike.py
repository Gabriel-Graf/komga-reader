#!/usr/bin/env python3
"""
MuPDF Engine-Spike (Plan 1.2) — End-to-End-Beweis.

Erzeugt je eine Test-Datei in den drei Formaten, die der Reader können muss
(CBZ = Comic/Manga, PDF, EPUB = Novel), rendert jede mit MuPDF (`mutool draw`,
exakt dieselbe Engine wie die spätere Android-JNI-Anbindung) zu einem PNG und
prüft, dass das Ergebnis die erwarteten Maße hat und NICHT leer ist (echte
dunkle Pixel aus dem gezeichneten Inhalt).

Retiriert das #1-Projektrisiko: "Rendert MuPDF unsere drei Formate zu Pixeln?"

Exit 0 = alle Formate bestanden, sonst != 0.
"""
from __future__ import annotations

import io
import shutil
import subprocess
import sys
import tempfile
import zipfile
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

PAGE_W, PAGE_H = 800, 1200          # Native Fixture-Größe
RENDER_DPI = 150
MIN_DIM = 200                        # Render muss mindestens so groß sein
DARK_THRESHOLD = 80                  # Pixel < dem gilt als "dunkler Inhalt"
MIN_DARK_PIXELS = 500               # So viele dunkle Pixel = nicht leer


def make_page_image(label: str, n: int) -> Image.Image:
    """Ein erkennbares Seitenbild: Rahmen + Balken + Text → garantiert nicht leer."""
    img = Image.new("RGB", (PAGE_W, PAGE_H), "white")
    d = ImageDraw.Draw(img)
    d.rectangle([10, 10, PAGE_W - 10, PAGE_H - 10], outline="black", width=6)
    d.rectangle([40, 40, PAGE_W - 40, 160], fill="black")
    for i in range(6):
        y = 220 + i * 150
        d.rectangle([60, y, PAGE_W - 60, y + 90], fill=(20, 20, 20))
    font = ImageFont.load_default()
    d.text((60, 90), f"{label} — Seite {n}", fill="white", font=font)
    return img


def make_cbz(path: Path) -> None:
    with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED) as z:
        for n in (1, 2):
            buf = io.BytesIO()
            make_page_image("CBZ", n).save(buf, "PNG")
            z.writestr(f"page{n:02d}.png", buf.getvalue())


def make_pdf(path: Path) -> None:
    p1 = make_page_image("PDF", 1)
    p2 = make_page_image("PDF", 2)
    p1.save(path, "PDF", save_all=True, append_images=[p2], resolution=72.0)


def make_epub(path: Path) -> None:
    """Minimal gültiges EPUB3 mit einem Textkapitel (Reflow-Pfad)."""
    container = (
        '<?xml version="1.0"?>\n'
        '<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">\n'
        '  <rootfiles><rootfile full-path="OEBPS/content.opf" '
        'media-type="application/oebps-package+xml"/></rootfiles>\n'
        "</container>\n"
    )
    opf = (
        '<?xml version="1.0" encoding="utf-8"?>\n'
        '<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="bookid">\n'
        '  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">\n'
        '    <dc:identifier id="bookid">urn:uuid:spike-0001</dc:identifier>\n'
        "    <dc:title>MuPDF Spike Novel</dc:title>\n"
        '    <dc:language>de</dc:language>\n'
        "  </metadata>\n"
        "  <manifest>\n"
        '    <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>\n'
        '    <item id="ch1" href="chapter1.xhtml" media-type="application/xhtml+xml"/>\n'
        "  </manifest>\n"
        '  <spine><itemref idref="ch1"/></spine>\n'
        "</package>\n"
    )
    nav = (
        '<?xml version="1.0" encoding="utf-8"?>\n'
        '<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">\n'
        '<head><title>Nav</title></head><body><nav epub:type="toc">\n'
        '<ol><li><a href="chapter1.xhtml">Kapitel 1</a></li></ol></nav></body></html>\n'
    )
    para = ("Die Sonne ging über dem zerfallenen Turm auf und färbte den Himmel tiefschwarz auf weiß. " * 12)
    chapter = (
        '<?xml version="1.0" encoding="utf-8"?>\n'
        '<html xmlns="http://www.w3.org/1999/xhtml"><head><title>Kapitel 1</title></head>\n'
        f"<body><h1>Kapitel 1</h1><p>{para}</p><p>{para}</p></body></html>\n"
    )
    with zipfile.ZipFile(path, "w") as z:
        # mimetype MUSS erste Datei und unkomprimiert sein (EPUB-Spec)
        zi = zipfile.ZipInfo("mimetype")
        zi.compress_type = zipfile.ZIP_STORED
        z.writestr(zi, "application/epub+zip")
        z.writestr("META-INF/container.xml", container)
        z.writestr("OEBPS/content.opf", opf)
        z.writestr("OEBPS/nav.xhtml", nav)
        z.writestr("OEBPS/chapter1.xhtml", chapter)


def render_page1(doc: Path, out_png: Path) -> None:
    subprocess.run(
        ["mutool", "draw", "-r", str(RENDER_DPI), "-o", str(out_png), str(doc), "1"],
        check=True, capture_output=True,
    )


def check_rendered(out_png: Path) -> tuple[bool, str]:
    if not out_png.exists() or out_png.stat().st_size == 0:
        return False, "kein/leeres PNG"
    img = Image.open(out_png).convert("L")
    w, h = img.size
    if w < MIN_DIM or h < MIN_DIM:
        return False, f"zu klein: {w}x{h}"
    dark = sum(1 for px in img.getdata() if px < DARK_THRESHOLD)
    if dark < MIN_DARK_PIXELS:
        return False, f"leer: nur {dark} dunkle Pixel"
    return True, f"{w}x{h}, {dark} dunkle Pixel"


def main() -> int:
    if not shutil.which("mutool"):
        print("FEHLER: mutool (MuPDF) nicht gefunden", file=sys.stderr)
        return 2
    ver = subprocess.run(["mutool", "-v"], capture_output=True, text=True)
    print(f"MuPDF: {(ver.stdout + ver.stderr).strip()}")

    fmts = {"CBZ": make_cbz, "PDF": make_pdf, "EPUB": make_epub}
    exts = {"CBZ": "cbz", "PDF": "pdf", "EPUB": "epub"}
    work = Path(tempfile.mkdtemp(prefix="mupdf-spike-"))
    all_ok = True
    print(f"\nArbeitsverzeichnis: {work}\n")
    print(f"{'Format':6} {'Render':8} Detail")
    print("-" * 50)
    for fmt, builder in fmts.items():
        doc = work / f"sample.{exts[fmt]}"
        out = work / f"{fmt.lower()}-p1.png"
        try:
            builder(doc)
            render_page1(doc, out)
            ok, detail = check_rendered(out)
        except subprocess.CalledProcessError as e:
            ok, detail = False, f"mutool-Fehler: {e.stderr.decode(errors='replace')[:80]}"
        except Exception as e:  # noqa: BLE001 — Spike soll jeden Fehler sichtbar machen
            ok, detail = False, f"{type(e).__name__}: {e}"
        all_ok &= ok
        print(f"{fmt:6} {'OK' if ok else 'FAIL':8} {detail}")

    print("-" * 50)
    print("ERGEBNIS:", "ALLE FORMATE BESTANDEN ✓" if all_ok else "FEHLGESCHLAGEN ✗")
    shutil.rmtree(work, ignore_errors=True)
    return 0 if all_ok else 1


if __name__ == "__main__":
    raise SystemExit(main())
