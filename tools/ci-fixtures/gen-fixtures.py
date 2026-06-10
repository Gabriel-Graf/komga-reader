#!/usr/bin/env python3
"""Erzeugt deterministische, lizenz-freie (CC0) Test-Fixtures: cbz (Comic) + epub (Novel).

Selbst gezeichnete Farbseiten mit aufgedruckter Serien-/Band-/Seitenzahl — genug,
damit Komga sie als Serien/Bücher erkennt und MuPDF/crengine sie rendern. Output ist
bit-deterministisch (feste Farben, feste ZIP-Timestamps), damit das committete content/
bei Re-Gen stabil bleibt.

Abhängigkeit: Pillow (`pip install pillow`). Nur zum Generieren nötig — CI nutzt das
committete content/.
"""
from __future__ import annotations
import sys, zipfile, io, pathlib
from PIL import Image, ImageDraw

ROOT = pathlib.Path(__file__).parent / "content"
FIXED_DATE = (2026, 1, 1, 0, 0, 0)  # feste ZIP-Timestamps → deterministisch

def _page(w: int, h: int, rgb: tuple[int, int, int], label: str) -> bytes:
    img = Image.new("RGB", (w, h), rgb)
    d = ImageDraw.Draw(img)
    d.text((20, 20), label, fill=(0, 0, 0))
    d.rectangle((5, 5, w - 6, h - 6), outline=(0, 0, 0), width=3)
    buf = io.BytesIO()
    img.save(buf, format="PNG")
    return buf.getvalue()

def _write_zip(path: pathlib.Path, entries: list[tuple[str, bytes]], mimetype: str | None = None) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED) as z:
        if mimetype is not None:  # epub: mimetype zuerst, unkomprimiert
            zi = zipfile.ZipInfo("mimetype", FIXED_DATE)
            zi.compress_type = zipfile.ZIP_STORED
            z.writestr(zi, mimetype)
        for name, data in entries:
            zi = zipfile.ZipInfo(name, FIXED_DATE)
            z.writestr(zi, data)

def make_cbz(series_dir: pathlib.Path, volume: int, pages: int, size: tuple[int, int], hue: int) -> None:
    entries = []
    for p in range(1, pages + 1):
        rgb = ((hue + p * 10) % 256, (hue * 2) % 256, (200 - p * 5) % 256)
        entries.append((f"{p:03d}.png", _page(size[0], size[1], rgb, f"V{volume} P{p}")))
    _write_zip(series_dir / f"vol-{volume:02d}.cbz", entries)

def make_epub(path: pathlib.Path, title: str, chapters: int) -> None:
    container = (
        '<?xml version="1.0"?><container version="1.0" '
        'xmlns="urn:oasis:names:tc:opendocument:xmlns:container">'
        '<rootfiles><rootfile full-path="OEBPS/content.opf" '
        'media-type="application/oebps-package+xml"/></rootfiles></container>'
    )
    manifest_items, spine_items, chapter_files = [], [], []
    for c in range(1, chapters + 1):
        cid = f"ch{c}"
        html = (
            f'<?xml version="1.0" encoding="utf-8"?><!DOCTYPE html>'
            f'<html xmlns="http://www.w3.org/1999/xhtml"><head><title>{title} — Kapitel {c}</title></head>'
            f"<body><h1>Kapitel {c}</h1>" + ("<p>Lorem ipsum dolor sit amet. </p>" * 20) + "</body></html>"
        )
        chapter_files.append((f"OEBPS/{cid}.xhtml", html))
        manifest_items.append(f'<item id="{cid}" href="{cid}.xhtml" media-type="application/xhtml+xml"/>')
        spine_items.append(f'<itemref idref="{cid}"/>')
    opf = (
        '<?xml version="1.0" encoding="utf-8"?>'
        '<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="bookid">'
        f'<metadata xmlns:dc="http://purl.org/dc/elements/1.1/">'
        f'<dc:identifier id="bookid">urn:uuid:fixture-{title}</dc:identifier>'
        f"<dc:title>{title}</dc:title><dc:language>de</dc:language></metadata>"
        f'<manifest>{"".join(manifest_items)}</manifest>'
        f'<spine>{"".join(spine_items)}</spine></package>'
    )
    entries = [("META-INF/container.xml", container.encode()), ("OEBPS/content.opf", opf.encode())]
    entries += [(n, h.encode()) for n, h in chapter_files]
    _write_zip(path, entries, mimetype="application/epub+zip")

def main() -> int:
    # Manga: standard-ratio, 2 Bände
    make_cbz(ROOT / "manga" / "Sample-Manga", 1, pages=6, size=(800, 1200), hue=40)
    make_cbz(ROOT / "manga" / "Sample-Manga", 2, pages=5, size=(800, 1200), hue=90)
    # Webtoon: tall pages, 1 Serie
    make_cbz(ROOT / "webtoon" / "Sample-Webtoon", 1, pages=4, size=(800, 3200), hue=140)
    # Novels A (disjunkt zu B) — jede Novel in eigenem Unterordner, damit Komga
    # pro Verzeichnis genau EINE Serie erkennt (flache epubs im Library-Root würden
    # sonst zu einer einzigen Serie „novels-a" gruppiert).
    make_epub(ROOT / "novels-a" / "Alpha-Novel" / "Alpha-Novel.epub", "Alpha-Novel", chapters=3)
    make_epub(ROOT / "novels-a" / "Beta-Novel" / "Beta-Novel.epub", "Beta-Novel", chapters=2)
    # Novels B (disjunkt zu A)
    make_epub(ROOT / "novels-b" / "Gamma-Novel" / "Gamma-Novel.epub", "Gamma-Novel", chapters=3)
    print("Fixtures generiert nach", ROOT)
    return 0

if __name__ == "__main__":
    sys.exit(main())
