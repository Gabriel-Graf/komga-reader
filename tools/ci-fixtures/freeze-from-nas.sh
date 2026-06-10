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
err "TODO Operator: Tier-2-Einträge in manifest.json + PROVENANCE.md manuell nachtragen"
err "  (Auto-Eintrag kommt mit dem ersten Tier-2-Render-Smoke-Test, Plan 2+)"
