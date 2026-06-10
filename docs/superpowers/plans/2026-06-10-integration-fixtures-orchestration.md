# Integrationstest — Plan 1: Fixture- & Orchestrierungs-Infrastruktur

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reproduzierbar N Komga-Instanzen mit deterministischem, lizenz-sauberem Test-Content hochfahren und seeden, sodass die Test-Topologie (Komga-A=Manga+Novels-A, B=Webtoon+Novels-B, C=Spiegel-A) per Skript verifizierbar entsteht.

**Architecture:** docker-compose fährt 3 Komga-Container (eigene Ports + config-Volumes). Tier-1-Content (CC0, selbst generiert, committbar) liegt im Repo unter `tools/ci-fixtures/content/`; ein Generator erzeugt valide cbz/epub deterministisch. `seed.sh` claimt pro Instanz den Admin, erzeugt einen API-Key, legt Libraries an und pollt bis der Scan die erwarteten Serien-Counts liefert. `verify.sh` asserted die Topologie gegen `manifest.json`. Volume-Cache (Marker = Manifest-Hash) überspringt Re-Seed bei unverändertem Fixture.

**Tech Stack:** Docker + docker-compose, `gotson/komga:latest`, Bash (`set -euo pipefail`, ShellCheck-konform), Python 3 + Pillow (nur zum einmaligen Generieren der committeten Fixtures), `curl` + `jq` für REST.

**Bezug:** Spec `docs/superpowers/specs/2026-06-10-integration-test-suite-design.md` §3–§5. Globale Regeln: `shell-scripting.md`, `data-provenance.md`.

---

## File Structure

```
tools/ci-fixtures/
├── README.md                  # Was das ist, wie man es startet, Determinismus-Garantie
├── gen-fixtures.py            # Generator: erzeugt content/ deterministisch (CC0)
├── content/                   # COMMITTED Tier-1-Fixtures (Output von gen-fixtures.py)
│   ├── manga/Sample-Manga/    # standard-ratio cbz (mehrere Bände)
│   ├── webtoon/Sample-Webtoon/# tall-page cbz
│   ├── novels-a/              # epub Set 1
│   └── novels-b/              # epub Set 2 (disjunkt zu A)
├── manifest.json             # erwartete Serien/Counts/Typen + SHAs (Asserts dagegen)
├── PROVENANCE.md             # Pflicht: Herkunft/Lizenz pro Quelle (data-provenance-Regel)
├── docker-compose.yml        # komga-a/b/c, Ports 25701/02/03, config-Volumes, content RO-Mounts
├── seed.sh                   # claim → api-key → libraries → scan-poll, je Instanz
├── verify.sh                 # asserted Topologie gegen manifest.json
├── up.sh                     # compose up → wait-healthy → (cache?) seed → verify; gibt Keys aus
├── down.sh                   # compose down (-v optional)
└── freeze-from-nas.sh        # Tier-2: kuratierten NAS-Subset runner-lokal einfrieren + Manifest
```

**Determinismus-Entscheidung:** Der **Output** (`content/`) wird committet, nicht nur der Generator — so braucht CI kein Python und der Inhalt ist bit-stabil. `gen-fixtures.py` ist reproduzierbar (feste Farben, feste ZIP-Timestamps), damit ein Re-Gen denselben Bytes-Stand liefert.

---

## Task 1: Verzeichnis-Skeleton + README

**Files:**
- Create: `tools/ci-fixtures/README.md`
- Create: `tools/ci-fixtures/.gitignore`

- [ ] **Step 1: README anlegen**

Create `tools/ci-fixtures/README.md`:

```markdown
# CI-Fixtures — Komga-Integrationstest-Backends

Fährt reproduzierbar 3 Komga-Instanzen mit deterministischem Test-Content hoch.

## Schnellstart (lokal)

    ./up.sh        # compose up + seed + verify; druckt pro Instanz baseUrl + API-Key
    ./down.sh      # stoppt (config-Volumes bleiben; -v zum Wipen)

## Topologie

| Instanz | Port  | Libraries                     |
|---------|-------|-------------------------------|
| komga-a | 25701 | Manga (cbz) + Novels-A (epub) |
| komga-b | 25702 | Webtoon (cbz) + Novels-B (epub, disjunkt) |
| komga-c | 25703 | Spiegel von A (n-gleiche-Server-Test) |

Vom Android-Emulator erreichbar über `http://10.0.2.2:<port>/api/v1/`.

## Content-Tiers

- **Tier 1** (`content/`, committet, CC0): selbst generiert via `gen-fixtures.py`.
  Trägt die Masse der Tests, CI-portabel ohne NAS.
- **Tier 2** (runner-lokal, NIE committet): echter NAS-Subset via `freeze-from-nas.sh`,
  nur für Render-Smoke. Siehe `PROVENANCE.md`.

## Determinismus

`manifest.json` hält erwartete Serien/Counts/Typen + SHAs. `verify.sh` asserted dagegen.
Tier-1-Content ist committet → jeder Lauf identisch.
```

- [ ] **Step 2: .gitignore für Tier-2/Laufzeit anlegen**

Create `tools/ci-fixtures/.gitignore`:

```gitignore
# Tier-2 NAS-Subset ist runner-lokal, NIE committen (Urheberrecht)
/tier2/
# Laufzeit-Marker
/.seeded-*
/.keys.env
```

- [ ] **Step 3: Commit**

```bash
git add tools/ci-fixtures/README.md tools/ci-fixtures/.gitignore
git commit -m "chore(ci-fixtures): Verzeichnis-Skeleton + README"
```

---

## Task 2: Fixture-Generator (Tier-1, CC0)

**Files:**
- Create: `tools/ci-fixtures/gen-fixtures.py`

- [ ] **Step 1: Generator schreiben**

Create `tools/ci-fixtures/gen-fixtures.py`:

```python
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
    # Novels A (disjunkt zu B) — jede Novel in eigenem Unterordner (Komga: 1 Serie/Verzeichnis;
    # flache epubs im Library-Root würden zu einer einzigen Serie gruppiert)
    make_epub(ROOT / "novels-a" / "Alpha-Novel" / "Alpha-Novel.epub", "Alpha-Novel", chapters=3)
    make_epub(ROOT / "novels-a" / "Beta-Novel" / "Beta-Novel.epub", "Beta-Novel", chapters=2)
    # Novels B (disjunkt zu A)
    make_epub(ROOT / "novels-b" / "Gamma-Novel" / "Gamma-Novel.epub", "Gamma-Novel", chapters=3)
    print("Fixtures generiert nach", ROOT)
    return 0

if __name__ == "__main__":
    sys.exit(main())
```

- [ ] **Step 2: Generator ausführen**

Run: `cd tools/ci-fixtures && python3 -m pip install --quiet pillow && python3 gen-fixtures.py`
Expected: `Fixtures generiert nach .../content`

- [ ] **Step 3: Output verifizieren (valide Archive + Determinismus)**

Run: `cd tools/ci-fixtures && find content -type f | sort && unzip -l content/manga/Sample-Manga/vol-01.cbz | tail -3 && unzip -l content/novels-a/Alpha-Novel.epub | grep mimetype`
Expected: cbz listet `001.png`..`006.png`; epub enthält `mimetype`. Zweiter `python3 gen-fixtures.py`-Lauf ändert keine Bytes (`git status` sauber).

- [ ] **Step 4: Commit (Generator + Output)**

```bash
git add tools/ci-fixtures/gen-fixtures.py tools/ci-fixtures/content
git commit -m "feat(ci-fixtures): deterministischer CC0-Fixture-Generator + committeter Content (Tier 1)"
```

---

## Task 3: Manifest (erwartete Topologie)

**Files:**
- Create: `tools/ci-fixtures/manifest.json`

- [ ] **Step 1: Manifest schreiben**

Create `tools/ci-fixtures/manifest.json`:

```json
{
  "schemaVersion": 1,
  "tier1": {
    "manga":    { "library": "Manga",    "contentType": "MANGA",   "series": ["Sample-Manga"],   "seriesCount": 1, "bookCount": 2 },
    "webtoon":  { "library": "Webtoon",  "contentType": "WEBTOON", "series": ["Sample-Webtoon"], "seriesCount": 1, "bookCount": 1 },
    "novels-a": { "library": "Novels-A", "contentType": "NOVEL",   "series": ["Alpha-Novel", "Beta-Novel"], "seriesCount": 2, "bookCount": 2 },
    "novels-b": { "library": "Novels-B", "contentType": "NOVEL",   "series": ["Gamma-Novel"], "seriesCount": 1, "bookCount": 1 }
  },
  "instances": {
    "komga-a": { "port": 25701, "libraries": ["manga", "novels-a"] },
    "komga-b": { "port": 25702, "libraries": ["webtoon", "novels-b"] },
    "komga-c": { "port": 25703, "libraries": ["manga", "novels-a"], "mirrorOf": "komga-a" }
  }
}
```

- [ ] **Step 2: Manifest gegen echten Content gegenprüfen**

Run: `cd tools/ci-fixtures && jq -r '.tier1.manga.bookCount' manifest.json && ls content/manga/Sample-Manga/*.cbz | wc -l`
Expected: beide `2` (Manifest-bookCount stimmt mit erzeugten Dateien überein). Analog für die anderen Libraries prüfen.

- [ ] **Step 3: Commit**

```bash
git add tools/ci-fixtures/manifest.json
git commit -m "feat(ci-fixtures): manifest.json — erwartete Serien/Counts/Topologie"
```

---

## Task 4: PROVENANCE.md (Pflicht)

**Files:**
- Create: `tools/ci-fixtures/PROVENANCE.md`

- [ ] **Step 1: Provenance schreiben**

Create `tools/ci-fixtures/PROVENANCE.md`:

```markdown
# CI-Fixtures — Daten-Provenance

## Tier 1 — committeter Test-Content

| Feld | Wert |
|------|------|
| **Name** | Sample-Manga / Sample-Webtoon / Alpha/Beta/Gamma-Novel |
| **URL** | Selbst erzeugt via `gen-fixtures.py` (keine externe Quelle) |
| **Lizenz** | CC0-1.0 (gemeinfrei, eigenes Werk) |
| **Cap / Volumen** | 4 Libraries, 5 Serien, 6 Bücher; wenige KB pro Datei |
| **Filter** | keiner — programmatisch generiert |
| **Erfassungsdatum** | 2026-06-10 |
| **Risk-Notiz** | keine — CC0, committbar, frei verteilbar |

## Tier 2 — runner-lokaler NAS-Subset (NIE committet)

| Feld | Wert |
|------|------|
| **Name** | kuratierter Subset aus `/mnt/nas/Manga` (siehe `freeze-from-nas.sh`-Auswahl) |
| **URL** | lokales NAS `192.168.178.22:/data` → `/mnt/nas/Manga` |
| **Lizenz** | **urheberrechtlich geschützt** — nur runner-lokal, **nie verteilt/committet** |
| **Cap / Volumen** | 1 Manga + 1 Webtoon + 2 Novels (im Freeze-Manifest fixiert) |
| **Filter** | manuelle Kuratierung, eingefroren (kein Re-Sync) |
| **Erfassungsdatum** | (von `freeze-from-nas.sh` beim Einfrieren gesetzt) |
| **Risk-Notiz** | ToS/Copyright: Rohdaten verlassen den Runner nie; `.gitignore` schützt `/tier2/`. |

## Deferred: echte CC/PD-Werke für Tier 1

Geplant (Spec §3): Tier 1 um echte freie Comics erweitern — Pepper&Carrot (CC-BY 4.0),
Sintel-Comic, Comics auf Wikimedia Commons, PD-Werke vor ~1929. Bei Aufnahme jeweils Name,
direkter Permalink, SPDX-Lizenz und Erfassungsdatum hier ergänzen. ⚠️ Original-1939-Batman u.ä.
sind noch geschützt — nicht aufnehmen.

Letzte Komplettrevision: 2026-06-10 (gabriel)
```

- [ ] **Step 2: Commit**

```bash
git add tools/ci-fixtures/PROVENANCE.md
git commit -m "docs(ci-fixtures): PROVENANCE — Tier-1 CC0, Tier-2 runner-lokal, CC/PD deferred"
```

---

## Task 5: docker-compose (3 Instanzen)

**Files:**
- Create: `tools/ci-fixtures/docker-compose.yml`

- [ ] **Step 1: Compose schreiben**

Create `tools/ci-fixtures/docker-compose.yml`:

```yaml
# 3 Komga-Instanzen für Integrationstests. Content read-only gemountet (schützt Repo-Fixtures),
# config-Volumes pro Instanz (halten Scan-Ergebnis → Volume-Cache).
services:
  komga-a:
    image: gotson/komga:latest
    ports: ["25701:25600"]
    environment:
      - KOMGA_LIBRARIES_SCAN_STARTUP=false
    volumes:
      - komga-a-config:/config
      - ./content/manga:/content/manga:ro
      - ./content/novels-a:/content/novels-a:ro
  komga-b:
    image: gotson/komga:latest
    ports: ["25702:25600"]
    environment:
      - KOMGA_LIBRARIES_SCAN_STARTUP=false
    volumes:
      - komga-b-config:/config
      - ./content/webtoon:/content/webtoon:ro
      - ./content/novels-b:/content/novels-b:ro
  komga-c:
    image: gotson/komga:latest
    ports: ["25703:25600"]
    environment:
      - KOMGA_LIBRARIES_SCAN_STARTUP=false
    volumes:
      - komga-c-config:/config
      - ./content/manga:/content/manga:ro
      - ./content/novels-a:/content/novels-a:ro
volumes:
  komga-a-config:
  komga-b-config:
  komga-c-config:
```

- [ ] **Step 2: Compose-Syntax prüfen**

Run: `cd tools/ci-fixtures && docker compose config -q && echo VALID`
Expected: `VALID` (keine Fehlerausgabe)

- [ ] **Step 3: Commit**

```bash
git add tools/ci-fixtures/docker-compose.yml
git commit -m "feat(ci-fixtures): docker-compose — komga-a/b/c mit RO-Content-Mounts"
```

---

## Task 6: seed.sh (claim → key → libraries → scan-poll)

**Files:**
- Create: `tools/ci-fixtures/seed.sh`

- [ ] **Step 1: seed.sh schreiben**

Create `tools/ci-fixtures/seed.sh` (ausführbar). Erwartete Komga-REST-Shapes (verifiziert gegen
`gotson/komga:latest`): `POST /api/v1/claim` mit Headern `X-Komga-Email`/`X-Komga-Password`;
`POST /api/v2/users/me/api-keys` body `{"comment":...}` → Antwort enthält `.key` (einmalig);
`POST /api/v1/libraries` mit vollem `LibraryCreationDto`; `GET /api/v1/series?library_id=&size=0`
liefert `.totalElements`.

```bash
#!/bin/bash
# Seedet eine Komga-Instanz: claim Admin, erzeuge API-Key, lege Libraries an, warte auf Scan.
# Gibt den API-Key auf STDOUT aus. Idempotent: bereits geclaimte Instanz wird übersprungen,
# vorhandene Libraries werden nicht doppelt angelegt.
set -euo pipefail

readonly ADMIN_EMAIL="admin@ci.local"
readonly ADMIN_PASS="ci-testpass-123"

err() { echo "[seed] $*" >&2; }

wait_up() {
  local base="$1" i
  for i in $(seq 1 60); do
    if curl -fsS -m 3 "${base}/api/v1/claim" >/dev/null 2>&1; then return 0; fi
    sleep 2
  done
  err "Komga unter ${base} nicht erreichbar"; return 1
}

claim_admin() {
  local base="$1"
  local claimed
  claimed="$(curl -fsS "${base}/api/v1/claim" | jq -r '.isClaimed')"
  if [[ "${claimed}" == "true" ]]; then err "schon geclaimt: ${base}"; return 0; fi
  curl -fsS -X POST "${base}/api/v1/claim" \
    -H "X-Komga-Email: ${ADMIN_EMAIL}" \
    -H "X-Komga-Password: ${ADMIN_PASS}" >/dev/null
  err "Admin geclaimt: ${base}"
}

make_api_key() {
  local base="$1"
  curl -fsS -X POST "${base}/api/v2/users/me/api-keys" \
    -u "${ADMIN_EMAIL}:${ADMIN_PASS}" \
    -H "Content-Type: application/json" \
    -d '{"comment":"ci"}' | jq -r '.key'
}

library_exists() {
  local base="$1" name="$2"
  curl -fsS "${base}/api/v1/libraries" -u "${ADMIN_EMAIL}:${ADMIN_PASS}" \
    | jq -e --arg n "${name}" 'any(.[]; .name == $n)' >/dev/null
}

create_library() {
  local base="$1" name="$2" root="$3"
  if library_exists "${base}" "${name}"; then err "Library '${name}' existiert"; return 0; fi
  # LibraryCreationDto: alle Pflicht-Booleans müssen gesetzt sein.
  curl -fsS -X POST "${base}/api/v1/libraries" \
    -u "${ADMIN_EMAIL}:${ADMIN_PASS}" \
    -H "Content-Type: application/json" \
    -d "$(jq -n --arg name "${name}" --arg root "${root}" '{
      name:$name, root:$root, scanInterval:"DISABLED", scanOnStartup:true, seriesCover:"FIRST",
      scanCbx:true, scanPdf:true, scanEpub:true, scanForceModifiedTime:false,
      importComicInfoBook:true, importComicInfoSeries:true, importComicInfoCollection:true,
      importComicInfoReadList:true, importComicInfoSeriesAppendVolume:true,
      importEpubBook:true, importEpubSeries:true, importMylarSeries:false,
      importLocalArtwork:true, importBarcodeIsbn:false, repairExtensions:false,
      convertToCbz:false, emptyTrashAfterScan:false, hashFiles:true, hashKoreader:false,
      hashPages:false, analyzeDimensions:true, scanDirectoryExclusions:[]
    }')" >/dev/null
  err "Library '${name}' angelegt (${root})"
}

wait_series_count() {
  local base="$1" want="$2" i got
  for i in $(seq 1 60); do
    got="$(curl -fsS "${base}/api/v1/series?size=0" -u "${ADMIN_EMAIL}:${ADMIN_PASS}" | jq -r '.totalElements')"
    if [[ "${got}" -ge "${want}" ]]; then return 0; fi
    sleep 2
  done
  err "Scan unvollständig unter ${base}: ${got}/${want} Serien"; return 1
}

# Usage: seed.sh <base-url> <want-series-total> <libName1> <libRoot1> [<libName2> <libRoot2> ...]
main() {
  local base="$1" want="$2"; shift 2
  wait_up "${base}"
  claim_admin "${base}"
  while [[ $# -ge 2 ]]; do
    create_library "${base}" "$1" "$2"; shift 2
  done
  wait_series_count "${base}" "${want}"
  make_api_key "${base}"   # API-Key auf STDOUT
}

main "$@"
```

- [ ] **Step 2: Ausführbar machen + ShellCheck**

Run: `cd tools/ci-fixtures && chmod +x seed.sh && shellcheck seed.sh && echo CLEAN`
Expected: `CLEAN` (keine Warnungen)

- [ ] **Step 3: Commit**

```bash
git add tools/ci-fixtures/seed.sh
git commit -m "feat(ci-fixtures): seed.sh — claim/api-key/libraries/scan-poll, idempotent"
```

---

## Task 7: up.sh / down.sh (Orchestrator + Volume-Cache)

**Files:**
- Create: `tools/ci-fixtures/up.sh`
- Create: `tools/ci-fixtures/down.sh`

- [ ] **Step 1: up.sh schreiben**

Create `tools/ci-fixtures/up.sh` (ausführbar):

```bash
#!/bin/bash
# Fährt die 3 Komga-Instanzen hoch und seedet sie. Schreibt API-Keys + baseUrls nach .keys.env.
# Volume-Cache: ist .seeded-<manifest-hash> vorhanden UND die config-Volumes existieren, wird
# der Seed übersprungen (Scan-Ergebnis steckt im Volume).
set -euo pipefail
cd "$(dirname "$0")"

err() { echo "[up] $*" >&2; }

readonly HASH="$(sha256sum manifest.json | cut -c1-16)"
readonly MARKER=".seeded-${HASH}"
readonly KEYS=".keys.env"

# Host-URL (lokal/CI auf demselben Host). Emulator nutzt 10.0.2.2:<port> — siehe README.
url() { echo "http://localhost:$(jq -r ".instances.\"$1\".port" manifest.json)"; }

# Wartet, bis jede Instanz ihren claim-Endpoint bedient (Komga-Boot ~30–60s pro compose up).
# Muss VOR dem Cache/Seed-Zweig laufen: beim Cache-Hit wird seed.sh (das selbst wartet)
# übersprungen, und verify.sh würde sonst gegen noch nicht gebootete Container laufen.
wait_ready() {
  local inst base i
  for inst in $(jq -r '.instances | keys[]' manifest.json); do
    base="$(url "${inst}")"
    for i in $(seq 1 60); do
      if curl -fsS -m 3 "${base}/api/v1/claim" >/dev/null 2>&1; then break; fi
      if [[ "${i}" -eq 60 ]]; then err "${inst} (${base}) nicht erreichbar"; return 1; fi
      sleep 2
    done
  done
  err "alle Instanzen bereit"
}

docker compose up -d
err "Container gestartet"
wait_ready

if [[ -f "${MARKER}" && -f "${KEYS}" ]]; then
  err "Cache-Hit (${MARKER}) — Seed übersprungen"
else
  rm -f .seeded-* "${KEYS}"
  # komga-a: Manga (2) + Novels-A (2) = 3 Serien total
  KEY_A="$(./seed.sh "$(url komga-a)" 3 Manga /content/manga Novels-A /content/novels-a)"
  # komga-b: Webtoon (1) + Novels-B (1) = 2 Serien total
  KEY_B="$(./seed.sh "$(url komga-b)" 2 Webtoon /content/webtoon Novels-B /content/novels-b)"
  # komga-c: Spiegel von A = 3 Serien total
  KEY_C="$(./seed.sh "$(url komga-c)" 3 Manga /content/manga Novels-A /content/novels-a)"
  {
    echo "KOMGA_A_URL=$(url komga-a)"; echo "KOMGA_A_KEY=${KEY_A}"
    echo "KOMGA_B_URL=$(url komga-b)"; echo "KOMGA_B_KEY=${KEY_B}"
    echo "KOMGA_C_URL=$(url komga-c)"; echo "KOMGA_C_KEY=${KEY_C}"
  } > "${KEYS}"
  touch "${MARKER}"
  err "Seed abgeschlossen → ${KEYS}"
fi

./verify.sh
err "Bereit. Keys in ${KEYS}:"
cat "${KEYS}"
```

- [ ] **Step 2: down.sh schreiben**

Create `tools/ci-fixtures/down.sh` (ausführbar):

```bash
#!/bin/bash
# Stoppt die Instanzen. Mit -v werden auch die config-Volumes (Scan-Cache) entfernt.
set -euo pipefail
cd "$(dirname "$0")"
if [[ "${1:-}" == "-v" ]]; then
  docker compose down -v
  rm -f .seeded-* .keys.env
  echo "[down] Container + Volumes + Marker entfernt" >&2
else
  docker compose down
  echo "[down] Container gestoppt (Volumes bleiben)" >&2
fi
```

- [ ] **Step 3: Ausführbar + ShellCheck**

Run: `cd tools/ci-fixtures && chmod +x up.sh down.sh && shellcheck up.sh down.sh && echo CLEAN`
Expected: `CLEAN`

- [ ] **Step 4: Commit**

```bash
git add tools/ci-fixtures/up.sh tools/ci-fixtures/down.sh
git commit -m "feat(ci-fixtures): up.sh/down.sh — Orchestrator mit Volume-Cache (Manifest-Hash)"
```

---

## Task 8: verify.sh (Topologie gegen Manifest asserten = der Test)

**Files:**
- Create: `tools/ci-fixtures/verify.sh`

- [ ] **Step 1: verify.sh schreiben (die Assertion zuerst — Assert First)**

Create `tools/ci-fixtures/verify.sh` (ausführbar):

```bash
#!/bin/bash
# Asserted die geseedete Topologie gegen manifest.json. Exit 0 = alle Asserts grün.
# Prüft pro Instanz: erreichbar, erwartete Libraries vorhanden, Serien-Total stimmt.
set -euo pipefail
cd "$(dirname "$0")"

readonly ADMIN_EMAIL="admin@ci.local"
readonly ADMIN_PASS="ci-testpass-123"
fail=0
err() { echo "[verify] $*" >&2; }
ok()  { echo "[verify] OK: $*"; }

url() { echo "http://localhost:$(jq -r ".instances.\"$1\".port" manifest.json)"; }

# Erwartete Serien-Summe einer Instanz aus dem Manifest berechnen.
want_series() {
  local inst="$1"
  jq -r "[.instances.\"${inst}\".libraries[] as \$l | .tier1[\$l].seriesCount] | add" manifest.json
}

assert_instance() {
  local inst="$1" base want got libname
  base="$(url "${inst}")"
  want="$(want_series "${inst}")"
  got="$(curl -fsS "${base}/api/v1/series?size=0" -u "${ADMIN_EMAIL}:${ADMIN_PASS}" | jq -r '.totalElements')"
  if [[ "${got}" == "${want}" ]]; then ok "${inst}: ${got} Serien"; else err "${inst}: ${got} != ${want} Serien"; fail=1; fi
  # Jede erwartete Library muss existieren.
  for libkey in $(jq -r ".instances.\"${inst}\".libraries[]" manifest.json); do
    libname="$(jq -r ".tier1.\"${libkey}\".library" manifest.json)"
    if curl -fsS "${base}/api/v1/libraries" -u "${ADMIN_EMAIL}:${ADMIN_PASS}" \
         | jq -e --arg n "${libname}" 'any(.[]; .name == $n)' >/dev/null; then
      ok "${inst}: Library '${libname}'"
    else
      err "${inst}: Library '${libname}' fehlt"; fail=1
    fi
  done
}

for inst in $(jq -r '.instances | keys[]' manifest.json); do
  assert_instance "${inst}"
done

if [[ "${fail}" -ne 0 ]]; then err "FEHLGESCHLAGEN"; exit 1; fi
ok "Topologie vollständig — alle Asserts grün"
```

- [ ] **Step 2: Ausführbar + ShellCheck**

Run: `cd tools/ci-fixtures && chmod +x verify.sh && shellcheck verify.sh && echo CLEAN`
Expected: `CLEAN`

- [ ] **Step 3: Verify gegen NICHT-laufende Instanzen → muss fehlschlagen (Red)**

Run: `cd tools/ci-fixtures && ./down.sh -v >/dev/null 2>&1; ./verify.sh; echo "exit=$?"`
Expected: `[verify] ... nicht erreichbar`-Fehler, `exit=1` (Assertion schlägt korrekt fehl, wenn nichts läuft).

- [ ] **Step 4: Voller End-to-End-Lauf (Green)**

Run: `cd tools/ci-fixtures && ./up.sh`
Expected: Container hoch, Seed läuft, am Ende `[verify] OK: Topologie vollständig — alle Asserts grün` und die 3 `KOMGA_*_URL`/`KOMGA_*_KEY`-Zeilen. (Erster Lauf zieht das Komga-Image — kann dauern.)

- [ ] **Step 5: Cache-Hit verifizieren**

Run: `cd tools/ci-fixtures && ./down.sh && ./up.sh 2>&1 | grep -E 'Cache-Hit|Topologie vollständig'`
Expected: `Cache-Hit` (Re-Seed übersprungen) **und** `Topologie vollständig` (Volume hielt das Scan-Ergebnis).

- [ ] **Step 6: Commit**

```bash
git add tools/ci-fixtures/verify.sh
git commit -m "feat(ci-fixtures): verify.sh — Topologie-Asserts gegen manifest.json"
```

---

## Task 9: freeze-from-nas.sh (Tier-2, runner-lokal)

**Files:**
- Create: `tools/ci-fixtures/freeze-from-nas.sh`

- [ ] **Step 1: Script schreiben**

Create `tools/ci-fixtures/freeze-from-nas.sh` (ausführbar). Kopiert einen kuratierten Subset
deterministisch nach `tier2/` (von `.gitignore` ausgeschlossen) und schreibt das Erfassungsdatum
in `tier2/FROZEN.txt`. Die konkrete Serien-Auswahl wird oben als Array gepflegt.

```bash
#!/bin/bash
# Friert einen kuratierten echten NAS-Subset runner-lokal ein (Tier 2). NIE committen.
# Auswahl unten anpassen; Pfade relativ zu /mnt/nas/Manga. Idempotent (rsync).
set -euo pipefail
cd "$(dirname "$0")"

readonly NAS_ROOT="/mnt/nas/Manga"
readonly OUT="tier2"
err() { echo "[freeze] $*" >&2; }

# Kuratierte Auswahl: "<ziel-kategorie>:<nas-relativer-pfad>"
# Disjunkt halten (Manga != Webtoon != Novels). Beispiele — an echten NAS-Inhalt anpassen.
readonly SELECTION=(
  "manga:Attack on Titan"
  "webtoon:Solo Leveling"
  "novels-a:Some Light Novel A"
  "novels-b:Some Light Novel B"
)

if [[ ! -d "${NAS_ROOT}" ]]; then err "NAS nicht gemountet: ${NAS_ROOT} (mount /mnt/nas)"; exit 1; fi

for entry in "${SELECTION[@]}"; do
  cat="${entry%%:*}"; rel="${entry#*:}"
  src="${NAS_ROOT}/${rel}"
  dst="${OUT}/${cat}/$(basename "${rel}")"
  if [[ ! -e "${src}" ]]; then err "FEHLT auf NAS: ${src} — Auswahl anpassen"; exit 1; fi
  mkdir -p "$(dirname "${dst}")"
  rsync -a --delete "${src}/" "${dst}/"
  err "eingefroren: ${cat} ← ${rel}"
done

date -u +"frozen-at=%Y-%m-%dT%H:%M:%SZ" > "${OUT}/FROZEN.txt"
err "Tier-2-Subset eingefroren nach ${OUT}/ (NIE committen — siehe PROVENANCE.md)"
```

- [ ] **Step 2: Ausführbar + ShellCheck**

Run: `cd tools/ci-fixtures && chmod +x freeze-from-nas.sh && shellcheck freeze-from-nas.sh && echo CLEAN`
Expected: `CLEAN`

- [ ] **Step 3: .gitignore-Schutz verifizieren**

Run: `cd tools/ci-fixtures && mkdir -p tier2 && touch tier2/probe && git check-ignore tier2/probe && rm -rf tier2`
Expected: `tier2/probe` wird ausgegeben (= ignoriert; Tier-2 kann nie versehentlich committet werden).

- [ ] **Step 4: Commit**

```bash
git add tools/ci-fixtures/freeze-from-nas.sh
git commit -m "feat(ci-fixtures): freeze-from-nas.sh — kuratierter Tier-2-Subset (runner-lokal)"
```

---

## Self-Review-Ergebnis (vom Plan-Autor)

- **Spec-Coverage:** §3 Tier-1 (Task 2/4), Tier-2 (Task 9), Manifest+Provenance (Task 3/4); §4 Topologie A/B/C (Task 5, manifest); §5 Compose+Seed+Volume-Cache (Task 5/6/7), Scan-Poll (Task 6). CC/PD-Web-Suche bleibt bewusst deferred (in PROVENANCE dokumentiert).
- **Keine Phantome:** Komga-REST-Shapes (`claim`-Header, `LibraryCreationDto`-Pflichtfelder, `ApiKeyDto.key`, `series?size=0.totalElements`) gegen die laufende `gotson/komga:latest` verifiziert.
- **Determinismus:** committeter Content + feste ZIP-Timestamps; verify.sh asserted gegen Manifest; Cache-Key = Manifest-Hash.

## Review-Findings (bewusst deferred, mit Begründung)

Branch-Review (Spec- + Code-Quality-Reviewer) + Lifecycle-Test deckten ab. Behoben: novels
in Unterordnern, `up.sh` wartet auf Boot, selbstheilender Cache-Gate (`content_ok` statt
Volume-Existenz), idempotenter `make_api_key` (löscht alten `ci`-Key vor Neuerzeugung).
Zwei Spec-Findings bleiben **bewusst offen**:

- **Manifest-SHAs (Spec §3 „SHA der Quelldateien"):** für Tier-1 **redundant** — der Content
  ist git-committet, also bit-gepinnt; verify asserted Serien-Namen+Counts. SHA-Manifest wird
  erst für **Tier-2** (runner-lokal, nicht git-getrackt) sinnvoll → dort beim Freeze nachziehen.
- **`freeze-from-nas.sh` schreibt Manifest/PROVENANCE-Einträge (Spec §5):** Tier-2 wird heute von
  **keinem** Test genutzt (erst ab Plan 2+) und der Operator kuratiert die `SELECTION` ohnehin
  manuell. Auto-Eintrag braucht einen Scan-Roundtrip → wird zusammen mit dem ersten Tier-2-Test
  (Render-Smoke) implementiert, nicht auf Vorrat. Bis dahin trägt der Operator Manifest/PROVENANCE
  manuell nach (im Script-Footer vermerkt).

False Positives des Code-Reviewers (verifiziert): `.keys.env` ist **nicht** getrackt (gitignore
greift); shellcheck 0.10.0 meldet **ALL CLEAN** (die behaupteten SC2044/SC2043 feuern nicht).

## Nächste Pläne (folgen nach Plan 1)

- **Plan 2** Harness (`CiKomga` liest `.keys.env`/Env, `CiFixtures` liest `manifest.json`) + Seam-Tests A/B/C/D/E/F/G.
- **Plan 3** Compose-UI-Test-Deps + UI-Tests A/C/D.
- **Plan 4** `.gitlab-ci.yml` + Runner-`devices=["/dev/kvm"]` + Emulator-Job.
- **Plan 5** Plugin/modular-UI-Tests (Block H, `[pending]`).
