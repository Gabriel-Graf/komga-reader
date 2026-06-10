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
