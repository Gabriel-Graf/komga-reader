#!/bin/bash
# Seedet eine frische Kavita-Instanz: Admin registrieren (erster User = Admin), Library anlegen,
# scannen. Gibt "KAVITA_URL=…" und "KAVITA_KEY=…" auf STDOUT aus. Idempotent: ein schon
# registrierter Admin wird per Login wiederverwendet, eine vorhandene Library nicht doppelt angelegt.
# API verifiziert gegen jvmilazz0/kavita:latest (siehe local-test-kavita). Usage: seed-kavita.sh <base-url>
set -euo pipefail

readonly ADMIN_USER="ci-admin"
readonly ADMIN_PASS="Komga!Test123"
err() { echo "[seed-kavita] $*" >&2; }

wait_up() {
  local base="$1"
  for _i in $(seq 1 120); do
    if [[ "$(curl -fsS -m 3 -o /dev/null -w '%{http_code}' "${base}/api/health" 2>/dev/null)" == "200" ]]; then
      return 0
    fi
    sleep 2
  done
  err "Kavita unter ${base} nicht erreichbar"; return 1
}

# Registriert den Admin (erster User) oder loggt ein, falls schon vorhanden. Echo: JSON mit token+apiKey.
auth() {
  local base="$1" resp
  resp="$(curl -fsS -m 15 -X POST "${base}/api/account/register" -H 'Content-Type: application/json' \
    -d "$(jq -n --arg u "${ADMIN_USER}" --arg p "${ADMIN_PASS}" \
      '{username:$u,password:$p,email:($u+"@ci.local")}')" 2>/dev/null || true)"
  if [[ -z "${resp}" || "$(echo "${resp}" | jq -r '.token // empty')" == "" ]]; then
    resp="$(curl -fsS -m 15 -X POST "${base}/api/account/login" -H 'Content-Type: application/json' \
      -d "$(jq -n --arg u "${ADMIN_USER}" --arg p "${ADMIN_PASS}" '{username:$u,password:$p}')")"
  fi
  echo "${resp}"
}

main() {
  local base="$1" auth_json token key libs
  wait_up "${base}"
  auth_json="$(auth "${base}")"
  token="$(echo "${auth_json}" | jq -r '.token')"
  key="$(echo "${auth_json}" | jq -r '.apiKey')"
  if [[ -z "${token}" || "${token}" == "null" ]]; then err "kein Token erhalten"; return 1; fi

  libs="$(curl -fsS -m 15 "${base}/api/library/libraries" -H "Authorization: Bearer ${token}" 2>/dev/null || echo '[]')"
  if [[ "$(echo "${libs}" | jq 'length')" -eq 0 ]]; then
    curl -fsS -m 15 -X POST "${base}/api/library/create" \
      -H "Authorization: Bearer ${token}" -H 'Content-Type: application/json' \
      -d '{"name":"CI Manga","type":0,"folders":["/manga"],"fileGroupTypes":[1,2,3,4],"excludePatterns":[]}' >/dev/null
    err "Library 'CI Manga' angelegt"
  else
    err "Library existiert bereits"
  fi
  curl -fsS -m 15 -X POST "${base}/api/library/scan?libraryId=1&force=true" \
    -H "Authorization: Bearer ${token}" >/dev/null 2>&1 || true
  err "Scan angestoßen"

  echo "KAVITA_URL=${base}"
  echo "KAVITA_KEY=${key}"
}

main "$@"
