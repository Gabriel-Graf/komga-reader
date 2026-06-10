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
