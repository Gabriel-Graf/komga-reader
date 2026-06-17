#!/usr/bin/env python3
"""Calibrate content-type detection thresholds against the labelled NAS corpus.

Replicates the app detector's signal extraction (ContentTypeDetector + measureGrayFraction +
SuggestContentType) as closely as practical:
  - open CBZ (zip), natural-sort image entries, skip cover (index 0)
  - pick interior pages at 25/50/75 %
  - decode, keep ORIGINAL (w,h) for aspect, downsample long edge -> ~200px (box avg) for gray stats
  - grayFraction = fraction of pixels with channel span max-min < satEps (=16)
  - per book: median gray, median aspect over its samples
Then prints per-type distributions to pick thresholds.
"""
import io, os, re, sys, zipfile, random
import numpy as np
from PIL import Image

ROOT = "/mnt/nas/Manga"
TYPES = {"MANGA": "Manga", "COMIC": "Comics", "WEBTOON": "Webtoon"}
IMG_EXT = (".jpg", ".jpeg", ".png", ".webp", ".gif", ".bmp")
SAT_EPS = 16
STATS_LONG_EDGE = 200
PER_TYPE = 45          # books sampled per type
random.seed(42)

def natkey(s):
    return [int(t) if t.isdigit() else t.lower() for t in re.split(r"(\d+)", s)]

def gray_fraction(img_small):
    a = np.asarray(img_small.convert("RGB"), dtype=np.int16)  # HxWx3
    mx = a.max(axis=2); mn = a.min(axis=2)
    span = mx - mn
    return float((span < SAT_EPS).mean())

def sample_book(path):
    try:
        zf = zipfile.ZipFile(path)
    except Exception:
        return None
    names = [n for n in zf.namelist() if n.lower().endswith(IMG_EXT) and not n.endswith("/")]
    names.sort(key=natkey)
    if len(names) < 2:
        return None
    interior = names[1:]                       # skip cover
    idxs = sorted(set(int(f * (len(interior) - 1)) for f in (0.25, 0.5, 0.75)))
    grays, aspects = [], []
    for i in idxs:
        try:
            data = zf.read(interior[i])
            im = Image.open(io.BytesIO(data)); im.load()
        except Exception:
            continue
        w, h = im.size
        if w <= 0 or h <= 0:
            continue
        aspects.append(h / w)
        s = max(1, max(w, h) // STATS_LONG_EDGE)
        small = im.resize((max(1, w // s), max(1, h // s)), Image.BOX)
        grays.append(gray_fraction(small))
    if not grays:
        return None
    return float(np.median(grays)), float(np.median(aspects))

def pct(arr, p):
    return float(np.percentile(arr, p)) if len(arr) else float("nan")

def main():
    results = {}
    for label, sub in TYPES.items():
        d = os.path.join(ROOT, sub)
        files = []
        for dp, _, fns in os.walk(d):
            for fn in fns:
                if fn.lower().endswith(".cbz"):
                    files.append(os.path.join(dp, fn))
        random.shuffle(files)
        grays, aspects, n = [], [], 0
        for f in files:
            if n >= PER_TYPE:
                break
            r = sample_book(f)
            if r is None:
                continue
            g, a = r
            grays.append(g); aspects.append(a); n += 1
        results[label] = (np.array(grays), np.array(aspects))
        print(f"\n=== {label}  (n={n} books) ===")
        print("  grayFraction  min/10/25/50/75/90/max: " +
              " / ".join(f"{pct(grays,p):.3f}" for p in (0,10,25,50,75,90,100)))
        print("  aspect h/w    min/10/25/50/75/90/max: " +
              " / ".join(f"{pct(aspects,p):.2f}" for p in (0,10,25,50,75,90,100)))
    return results

if __name__ == "__main__":
    main()
