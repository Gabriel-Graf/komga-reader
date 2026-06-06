#!/usr/bin/env python3
"""Erzeugt Sample-CBZ-Comics in einer Komga-Library-Struktur (root -> series -> books)."""
import io
import sys
import zipfile
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

PAGE_W, PAGE_H = 800, 1200

# (series_folder, book_filename, anzahl_seiten, hintergrundfarbe)
BOOKS = [
    ("Berserk", "vol01.cbz", 4, (40, 20, 20)),
    ("Berserk", "vol02.cbz", 4, (20, 20, 40)),
    ("Saga", "issue01.cbz", 3, (20, 40, 20)),
]


def _page(series: str, book: str, page_number: int, bg: tuple[int, int, int]) -> Image.Image:
    img = Image.new("RGB", (PAGE_W, PAGE_H), bg)
    draw = ImageDraw.Draw(img)
    draw.rectangle([10, 10, PAGE_W - 10, PAGE_H - 10], outline="white", width=6)
    draw.rectangle([40, 40, PAGE_W - 40, 200], fill="black")
    for i in range(5):
        y = 260 + i * 170
        shade = 30 + i * 30
        draw.rectangle([60, y, PAGE_W - 60, y + 120], fill=(shade, shade, shade))
    font = ImageFont.load_default()
    draw.text((60, 100), f"{series} / {book}", fill="white", font=font)
    draw.text((60, 140), f"Seite {page_number}", fill="white", font=font)
    return img


def make_cbz(path: Path, series: str, book: str, pages: int, bg: tuple[int, int, int]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED) as archive:
        for page_number in range(1, pages + 1):
            buffer = io.BytesIO()
            _page(series, book, page_number, bg).save(buffer, "PNG")
            archive.writestr(f"p{page_number:02d}.png", buffer.getvalue())


def main() -> int:
    root = Path(sys.argv[1])
    for series, book, pages, bg in BOOKS:
        target = root / series / book
        make_cbz(target, series, book, pages, bg)
        print(f"  {target} ({pages} Seiten)")
    print(f"Comics in {root}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
