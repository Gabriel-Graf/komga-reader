#!/bin/bash
# Seedet eine Komga-Instanz: claim Admin, erzeuge API-Key, lege Libraries an, warte auf Scan.
# Gibt den API-Key auf STDOUT aus. Idempotent: bereits geclaimte Instanz wird übersprungen,
# vorhandene Libraries werden nicht doppelt angelegt.
set -euo pipefail

readonly ADMIN_EMAIL="admin@ci.local"
readonly ADMIN_PASS="ci-testpass-123"

err() { echo "[seed] $*" >&2; }

wait_up() {
  local base="$1" _i
  for _i in $(seq 1 60); do
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
  local base="$1" want="$2" _i got
  got=0
  for _i in $(seq 1 60); do
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
