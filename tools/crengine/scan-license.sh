#!/bin/bash
#
# scan-license.sh — Phase-0-Gate: klassifiziert die GPL-Lizenz jeder crengine-
# Quelldatei im Render-Pfad (crengine/src + crengine/include).
#
# AGPL-3.0-or-later (unser Projekt, via MuPDF) ist nur mit GPL-2.0-OR-LATER
# kompatibel, nicht mit GPL-2.0-ONLY. Dieses Skript liest den Header jeder
# Datei und stuft sie ein: or-later | only | unklar.
#
# Aufruf:  scan-license.sh <pfad-zur-crengine-quelle>
# Ausgabe: TSV "datei<TAB>verdict" auf STDOUT, "SUMMARY ..." auf STDERR.
#
set -euo pipefail

src="${1:?Pfad zur crengine-Quelle}"

green=0
red=0
unknown=0

while IFS= read -r f; do
  # Header auf eine Zeile kollabieren, damit mehrzeilige Lizenz-Blurbs
  # ("version 2 ... or ... any later version") als ein Match erkannt werden.
  hdr="$(head -n 40 "${f}" | tr '\n' ' ')"
  if grep -qiE 'SPDX-License-Identifier:[[:space:]]*GPL-2\.0-or-later|GPL-3\.0' <<<"${hdr}" ||
     grep -qiE 'version 2 .*or .*(any )?later version' <<<"${hdr}"; then
    printf '%s\tor-later\n' "${f}"
    green=$((green + 1))
  elif grep -qiE 'SPDX-License-Identifier:[[:space:]]*GPL-2\.0(-only)?\b' <<<"${hdr}" ||
       grep -qiE 'GNU General Public License.*version 2' <<<"${hdr}"; then
    printf '%s\tonly\n' "${f}"
    red=$((red + 1))
  else
    printf '%s\tunklar\n' "${f}"
    unknown=$((unknown + 1))
  fi
done < <(find "${src}/crengine/src" "${src}/crengine/include" \
           -type f \( -name '*.cpp' -o -name '*.c' -o -name '*.h' -o -name '*.hpp' \) 2>/dev/null | sort)

echo "SUMMARY or-later=${green} only=${red} unklar=${unknown}" >&2
