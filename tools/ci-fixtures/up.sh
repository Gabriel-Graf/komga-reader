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

# Wartet, bis jede Instanz ihren claim-Endpoint bedient (Komga-Boot dauert nach jedem
# `compose up` ~30–60s). Muss VOR dem Cache/Seed-Zweig laufen: beim Cache-Hit wird seed.sh
# (das selbst wartet) übersprungen, und verify.sh würde sonst gegen noch nicht gebootete
# Container laufen.
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

# Selbstheilender Cache-Gate: nicht auf die Volume-Existenz prüfen (docker compose legt ein
# fehlendes Named-Volume beim Start LEER neu an → wäre fälschlich „vorhanden"), sondern auf
# den tatsächlichen Inhalt. Stimmen die Serien-Counts jeder Instanz mit dem Manifest, ist der
# Cache echt warm; sonst (frische/leere/gewipete DB) fällt up.sh auf den Seed-Pfad durch.
# Admin-Creds gespiegelt aus seed.sh (Fixture-Konstanten).
content_ok() {
  local inst base want got
  for inst in $(jq -r '.instances | keys[]' manifest.json); do
    base="$(url "${inst}")"
    want="$(jq -r "[.instances.\"${inst}\".libraries[] as \$l | .tier1[\$l].seriesCount] | add" manifest.json)"
    got="$(curl -fsS -m 5 "${base}/api/v1/series?size=0" -u "admin@ci.local:ci-testpass-123" 2>/dev/null | jq -r '.totalElements' 2>/dev/null)" || return 1
    [[ "${got}" == "${want}" ]] || return 1
  done
}

if [[ -f "${MARKER}" && -f "${KEYS}" ]] && content_ok; then
  err "Cache-Hit (${MARKER}) — Seed übersprungen"
else
  rm -f .seeded-* "${KEYS}"
  # komga-a: Manga (1 Serie/2 Bücher) + Novels-A (2 Serien) = 3 Serien total
  KEY_A="$(./seed.sh "$(url komga-a)" 3 Manga /content/manga Novels-A /content/novels-a)"
  # komga-b: Webtoon (1) + Novels-B (1) = 2 Serien total
  KEY_B="$(./seed.sh "$(url komga-b)" 2 Webtoon /content/webtoon Novels-B /content/novels-b)"
  # komga-c: Spiegel von A = 3 Serien total
  KEY_C="$(./seed.sh "$(url komga-c)" 3 Manga /content/manga Novels-A /content/novels-a)"
  # kavita: zweite Server-Art (Source-Plugin-E2E). Seed emittiert KAVITA_URL/KAVITA_KEY-Zeilen.
  KAVITA_OUT="$(./seed-kavita.sh "http://localhost:25710")"
  {
    echo "KOMGA_A_URL=$(url komga-a)"; echo "KOMGA_A_KEY=${KEY_A}"
    echo "KOMGA_B_URL=$(url komga-b)"; echo "KOMGA_B_KEY=${KEY_B}"
    echo "KOMGA_C_URL=$(url komga-c)"; echo "KOMGA_C_KEY=${KEY_C}"
    echo "${KAVITA_OUT}"
  } > "${KEYS}"
  touch "${MARKER}"
  err "Seed abgeschlossen → ${KEYS}"
fi

./verify.sh
err "Bereit. Keys in ${KEYS}:"
cat "${KEYS}"
