# Lizenz-Scan: crengine-ng (Phase-0-Gate, 2. Kandidat)

**Datum:** 2026-06-07
**Kandidat:** crengine-ng (modernisierter crengine-Fork von Aleksey Chernov)
**Geprüft gegen:** Projektlizenz **AGPL-3.0-or-later** (via MuPDF).
AGPL-3.0-or-later ist nur mit **GPL-2.0-OR-LATER** (bzw. GPL-3-kompatibel / LGPL)
kombinierbar, **nicht** mit GPL-2.0-ONLY.

## Verdikt: 🟢 GREEN

crengine-ng ist **GPL-2.0-or-later** — sowohl per **Repo-Level-Lizenzaussage** (README,
maßgeblich) als auch per **konsistenten Datei-Headern** im gesamten Kern-Render-Pfad.
Damit ist es mit AGPL-3.0-or-later **kompatibel** und als EPUB-Reflow-Engine hinter
der Render-Naht (Document/Viewer, Phase 4) grundsätzlich einsetzbar.

> Kontrast zum 1. Kandidaten: Der KOReader-**crengine**-Fork war RED
> (Repo-Level GPL-2.0-**only** via README.TXT + COPYING). crengine-ng hat die
> Lizenz auf GPL-2.0-**or-later** geklärt — exakt die Vermutung, die der
> GPL-2.0-or-later-Header in `mdfmt.cpp` (Ursprung crengine-ng) nahegelegt hat.

## Quelle

- **Repo-URL:** https://gitlab.com/coolreader-ng/crengine-ng.git
- **Commit-SHA:** `ec57cc1d16c47237c10ac6f3cfa491791e23a952`
- **Clone:** `--depth 1` nach `/tmp/crengine-ng-src` (read-only, **nicht** vendored)

## 1. Maßgebliche Evidenz — Repo-Level-Lizenzaussage

`README.md`, Abschnitt „License" (Zeilen 127–129), **wörtlich**:

> ## License
> This program is free software; you can redistribute it and/or modify it under the
> terms of the GNU General Public License as published by the Free Software Foundation;
> **either version 2 of the License, or (at your option) any later version.**

Das ist die ausschlaggebende Aussage:

- **GPL-Version:** Version 2 …
- **„or later"-Klausel:** … **„or (at your option) any later version"** → explizit
  **GPL-2.0-or-later**.
- **Kern vs. Thirdparty differenziert:** README Abschnitt „Embedded third party
  components" (Zeile 132) listet `thirdparty/` separat mit eigenen, GPLv2-kompatiblen
  Lizenzen (chmlib LGPL-2.1+, antiword GPL-2.0+, nanosvg ZLib, qimagescale, rfc6234-shas
  BSD-3, cmark-gfm BSD-2, MD4C MIT/Expat; ferner xxhash BSD-2, fc-lang FontConfig/MIT-like,
  libiconv-Fragmente LGPLv2+).

> Hinweis zur `LICENSE`-Datei: Sie enthält den **Volltext der GPL Version 2** (Standard-
> GPLv2-Textkörper, „Version 2, June 1991"). Das ist erwartungsgemäß und **kein**
> Widerspruch zur „or later"-Aussage: Die GPL-Boilerplate empfiehlt, den GPLv2-Volltext
> beizulegen und in der Projektaussage „version 2 … or any later version" zu wählen.
> Die effektive Lizenz wird durch die Projektaussage (README + Datei-Header) bestimmt,
> nicht durch die Versionsnummer des beigelegten Lizenz-Volltextes. (Beim KOReader-Fork
> fehlte genau die „or later"-Klausel → only.)

`AUTHORS` weist crengine-ng (Aleksey Chernov) als Fortführung von CoolReader/crengine
(Vadim Lopatin u. a.) aus.

## 2. Per-Datei-Header-Scan des Kern-Render-Pfads

Layout: `crengine/include/` + `crengine/src/` (mit Unterordnern `lvfont/`, `lvtinydom/`,
`lvstream/`, `lvxml/`, `lvdrawbuf/`, `lvimg/`, `encodings/`, `locale_data/`, `private/`).
Das deckt sich mit den Find-Pfaden von `tools/crengine/scan-license.sh` (rekursiv über
beide Verzeichnisse) — Skript unverändert wiederverwendet.

**Scan-Ergebnis (`crengine/src` + `crengine/include`, alle `.c/.cpp/.h/.hpp`):**

```
SUMMARY  or-later=335   only=0   unklar=261
```

- **`only=0`** — keine einzige GPL-2.0-only-Datei im Kern. Das ist der entscheidende
  Unterschied zum RED-Kandidaten.
- **`or-later=335`** — gesamter eigentlicher crengine-ng-Code.
- **`unklar=261`** — **ausschließlich** gebündelte/generierte Fremd-Daten, **kein**
  Kern-Render-Code:
  - **258×** `src/locale_data/**` — aus der **fontconfig fc-lang**-Datenbank
    autogenerierte Orthographie-Tabellen (FontConfig/MIT-like, README Z. 143).
  - **`src/xxhash.c` / `src/xxhash.h`** — xxHash, **BSD 2-Clause** (Header im File,
    README Z. 142).
  - **`src/mathml_operators.h`** — generiert aus Mozilla mathfont.properties, **MPL-2.0**
    (Header im File).

  Diese fallen nicht ins Kern-Verdikt (Fremd-Code mit eigener, kompatibler Lizenz —
  analog `thirdparty/`, das per Skript-Pfad ohnehin nicht gescannt wird).

### Stichprobe — 6+ Kern-Render-Dateien (Header-Uniformität bestätigt)

Alle tragen wörtlich „… either version 2 of the License, or (at your option) any later
version.":

| Datei | Klausel |
|---|---|
| `crengine/src/lvdocview.cpp` | „version 2 … or … any later version" ✅ |
| `crengine/src/lvrend.cpp` | ✅ |
| `crengine/src/lvtextfm.cpp` | ✅ |
| `crengine/src/lvstsheet.cpp` | ✅ |
| `crengine/src/lvfont/lvfntman.cpp` | ✅ |
| `crengine/src/lvtinydom/lvtinydomutils.cpp` | ✅ |
| `crengine/include/lvtinydom_common.h` | ✅ |

(Weitere Kern-Module — `lvstring`, `lvxml`, `lvstream`, `lvdrawbuf` — liegen unter
denselben Pfaden und sind im `or-later=335`-Block enthalten.)

## 3. Begründung des Verdikts

1. **Repo-Level (maßgeblich):** README erklärt das Projekt explizit als GPL-2.0-**or-later**
   („or (at your option) any later version"). Das ist — wie beim 1. Kandidaten gelernt —
   die ausschlaggebende Evidenz, stärker als einzelne Datei-Header.
2. **Datei-Header konsistent:** 335 Kern-Dateien or-later, **0** only. Stichprobe von
   7 Kern-Render-Dateien bestätigt einheitlich die „or later"-Klausel.
3. **Thirdparty sauber abgegrenzt:** `thirdparty/` und gebündelte Fremd-Daten
   (locale_data, xxhash, mathml_operators) haben eigene, GPLv2-kompatible Lizenzen und
   werden im Kern-Verdikt nicht mitgezählt.

**GPL-2.0-or-later ⇒ kombinierbar mit AGPL-3.0-or-later** (gemeinsamer Nenner GPLv3/AGPLv3).
→ **GREEN.**

## Konsequenz / Hinweise für die Umsetzung

- Bei tatsächlicher Einbindung (Phase 4): crengine-ng **nur** hinter der Render-Naht
  (`Document`/`Viewer`) für EPUB; alle gebündelten Fremd-Lizenzen (`thirdparty/` +
  locale_data/xxhash/mathml/fc-lang) in `NOTICE` aufführen (Provenance-Pflicht).
- Da das Gesamtwerk dann AGPL-3.0-or-later ist, gilt die AGPL-Quelloffenlegung für die
  Distribution unverändert.
