#!/usr/bin/env python3
"""Erzeugt CBZ/PDF/EPUB-Test-Fixtures in <zielordner> (für Instrumented-Tests)."""
import io, sys, zipfile
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

PAGE_W, PAGE_H = 800, 1200


def _page(label: str, n: int) -> Image.Image:
    img = Image.new("RGB", (PAGE_W, PAGE_H), "white")
    d = ImageDraw.Draw(img)
    d.rectangle([10, 10, PAGE_W - 10, PAGE_H - 10], outline="black", width=6)
    d.rectangle([40, 40, PAGE_W - 40, 160], fill="black")
    for i in range(6):
        y = 220 + i * 150
        d.rectangle([60, y, PAGE_W - 60, y + 90], fill=(20, 20, 20))
    d.text((60, 90), f"{label} Seite {n}", fill="white", font=ImageFont.load_default())
    return img


def make_cbz(p: Path):
    with zipfile.ZipFile(p, "w", zipfile.ZIP_DEFLATED) as z:
        for n in (1, 2):
            buf = io.BytesIO(); _page("CBZ", n).save(buf, "PNG"); z.writestr(f"p{n:02d}.png", buf.getvalue())


def make_pdf(p: Path):
    _page("PDF", 1).save(p, "PDF", save_all=True, append_images=[_page("PDF", 2)], resolution=72.0)


def make_epub(p: Path):
    container = '<?xml version="1.0"?>\n<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles></container>'
    opf = '<?xml version="1.0" encoding="utf-8"?>\n<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="b"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:identifier id="b">urn:uuid:fx</dc:identifier><dc:title>FX</dc:title><dc:language>de</dc:language></metadata><manifest><item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/><item id="c1" href="c1.xhtml" media-type="application/xhtml+xml"/></manifest><spine><itemref idref="c1"/></spine></package>'
    nav = '<?xml version="1.0" encoding="utf-8"?>\n<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops"><head><title>n</title></head><body><nav epub:type="toc"><ol><li><a href="c1.xhtml">K1</a></li></ol></nav></body></html>'
    para = ("Die Sonne ging über dem zerfallenen Turm auf. " * 14)
    c1 = f'<?xml version="1.0" encoding="utf-8"?>\n<html xmlns="http://www.w3.org/1999/xhtml"><head><title>K1</title></head><body><h1>Kapitel 1</h1><p>{para}</p><p>{para}</p></body></html>'
    with zipfile.ZipFile(p, "w") as z:
        zi = zipfile.ZipInfo("mimetype"); zi.compress_type = zipfile.ZIP_STORED
        z.writestr(zi, "application/epub+zip")
        z.writestr("META-INF/container.xml", container)
        z.writestr("OEBPS/content.opf", opf)
        z.writestr("OEBPS/nav.xhtml", nav)
        z.writestr("OEBPS/c1.xhtml", c1)


def main() -> int:
    out = Path(sys.argv[1]); out.mkdir(parents=True, exist_ok=True)
    make_cbz(out / "sample.cbz"); make_pdf(out / "sample.pdf"); make_epub(out / "sample.epub")
    print(f"Fixtures in {out}: {[p.name for p in out.iterdir()]}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
