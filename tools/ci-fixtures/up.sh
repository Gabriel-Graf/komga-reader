#!/bin/bash
# Fährt die 3 Komga-Instanzen hoch und seedet sie. Schreibt API-Keys + baseUrls nach .keys.env.
# Volume-Cache: ist .seeded-<manifest-hash> vorhanden UND die config-Volumes existieren, wird
# der Seed übersprungen (Scan-Ergebnis steckt im Volume).
set -euo pipefail
cd "$(dirname "$0")"

err() { echo "[up] $*" >&2; }

HASH="$(sha256sum manifest.json | cut -c1-16)"
readonly HASH
readonly MARKER=".seeded-${HASH}"
readonly KEYS=".keys.env"

# Host-URL (lokal/CI auf demselben Host). Emulator nutzt 10.0.2.2:<port> — siehe README.
url() { echo "http://localhost:$(jq -r ".instances.\"$1\".port" manifest.json)"; }

docker compose up -d
err "Container gestartet"

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
