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
  local inst="$1" base want got libname libkey
  base="$(url "${inst}")"
  want="$(want_series "${inst}")"
  if ! got="$(curl -fsS "${base}/api/v1/series?size=0" -u "${ADMIN_EMAIL}:${ADMIN_PASS}" 2>/dev/null | jq -r '.totalElements')"; then
    err "${inst}: nicht erreichbar (${base})"; fail=1; return 0
  fi
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
