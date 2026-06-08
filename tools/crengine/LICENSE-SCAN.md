# crengine Lizenz-Scan — Phase-0-Gate

**Datum:** 2026-06-07
**Gescannte Quelle:** KOReader crengine-Fork — https://github.com/koreader/crengine.git
**Commit:** `e32ab969d293dabe5b68dddbede7b60c5ca772a2`
**Scan-Skript:** `tools/crengine/scan-license.sh`
**Render-Pfad:** `crengine/src` + `crengine/include` (`*.cpp *.c *.h *.hpp`)

---

## Verdikt: 🔴 RED — Feature wird NICHT gebaut

**Begründung:** Der crengine-Kern (das eigentliche Reflow-Render-Engine) ist **GPL-2.0-only**,
nicht "-or-later". Das ist mit unserem **AGPL-3.0-or-later** (via MuPDF) **inkompatibel** —
GPL-2.0-only-Code darf nicht mit AGPL-3.0-Code zu einem Werk verlinkt werden.

### Wie sich das aus dem Scan ergibt

Der reine Header-Scan liefert:

```
SUMMARY or-later=12 only=0 unklar=114
```

`only=0` ist **irreführend** und darf NICHT als GREEN gelesen werden. Grund: Praktisch der
gesamte Kern trägt **keinen** versionierten Lizenz-Header. Jede Datei (z. B. `lvtinydom.cpp`,
`lvdocview.cpp`, `lvrend.cpp`, `lvtextfm.cpp`, `lvstsheet.cpp`, `lvpagesplitter.cpp`,
`lvstyles.cpp`, `lvfntman.cpp`) sagt nur:

```
   CoolReader Engine
   (c) Vadim Lopatin
   This source code is distributed under the terms of
   GNU General Public License
   See LICENSE file for details
```

— "GNU General Public License", **ohne** Versionsangabe, **ohne** "or later". Die Version wird
ausschließlich durch die referenzierte LICENSE/README-Aussage auf Repo-Ebene festgelegt. Deshalb
stuft das Skript diese Dateien korrekt als `unklar` ein (der Datei-Header allein ist mehrdeutig).

### Die Repo-Ebene löst `unklar` → **only** auf

- **`README.TXT`** (autoritative Aussage):
  > `LICENSE: All source codes (except thirdparty directory) are provided under the terms of GNU GPL license, version 2`
- **Alle `COPYING`-Dateien** im Repo (`cr3gui/COPYING`, `cr3wx/COPYING`, `tinydict/COPYING`)
  enthalten den Volltext **"GNU GENERAL PUBLIC LICENSE Version 2, June 1991"**.
- Im gesamten Repo (`README.TXT`, `changelog`, `CMakeLists.txt`) gibt es **kein** "or later",
  **kein** "version 3", **kein** "either version 2 ... or any later version" für den Kern.
- Es existiert **keine** LICENSE-Datei direkt neben `crengine/` — die Header verweisen auf die
  Repo-weite "version 2"-Aussage.

→ Die 114 `unklar`-Kerndateien sind damit faktisch **GPL-2.0-only**. Das ist der Gate-Killer.

### Einordnung der 12 `or-later`-Treffer (zählen NICHT für GREEN)

| Datei(en) | Was es ist | Relevanz |
|---|---|---|
| `crengine/include/crgui.h`, `crengine/include/crskin.h` | echte GPL-"version 2 ... or (at your option) any later version"-Header; GUI-Skin/-Helfer, **nicht** Kern-Render | echt or-later, aber Randmodule |
| `crengine/include/encodings/big5.h`, `big5_2003.h`, `cp936ext.h`, `gb2312.h`, `gbkext1.h`, `gbkext2.h`, `jisx0213.h`, `ksc5601.h` (8 Dateien) | **gebündeltes GNU LIBICONV** — Header sagt "GNU **Library** General Public License" (LGPL); das Skript matcht die "or later"-Klausel | 3rd-party, LGPL, nicht crengine-Kern |
| `crengine/src/mdfmt.cpp`, `crengine/src/mdfmt.h` | **crengine-ng**-Code (Aleksey Chernov), echtes GPL-2.0-or-later (Markdown-Format) | echt or-later, aber Einzel-Format-Modul |

Diese Ausreißer ändern nichts: Der **Kern** (DOM, Rendering, Text-Layout, CSS, Pagination,
Font-Management) hängt geschlossen an der GPL-2.0-**only**-Aussage.

### `only`-Dateien (Header-Scan)

Keine — der Header-Scan findet **0** Dateien mit explizitem GPL-2.0-only-SPDX/Volltext im
Datei-Header. Das `only`-Problem entsteht erst durch die **Repo-LICENSE-Auflösung** der
`unklar`-Dateien (siehe oben), nicht durch einen einzelnen Datei-Header.

### Core-`unklar`-Dateien (alle GPL-2.0-only via Repo-LICENSE)

Alle 114 `unklar`-Treffer sind crengine-Kern bzw. eng zugehörige Format-/Util-Module mit dem
"GNU General Public License / See LICENSE file"-Header, darunter die render-kritischen:
`lvtinydom.{cpp,h}`, `lvdocview.{cpp,h}`, `lvrend.{cpp,h}`, `lvtextfm.{cpp,h}`,
`lvstsheet.{cpp,h}`, `lvstyles.{cpp,h}`, `lvpagesplitter.{cpp,h}`, `lvfntman.{cpp,h}`,
`lvdrawbuf.{cpp,h}`, `lvbmpbuf.{cpp,h}`, `lvstring.{cpp,h}`, `lvxml.{cpp,h}`,
`lvstream.{cpp,h}`, `lvimg.{cpp,h}`. Keine davon trägt eine "or later"-Klausel; alle
unterliegen der Repo-weiten "version 2"-Aussage. Vollständige Liste siehe Tabelle unten.

---

## Konsequenz für das Feature

- crengine darf **nicht** in dieses AGPL-3.0-or-later-Projekt gevendort/verlinkt werden.
- Phase-0-Gate = **RED**: Der crengine-basierte Reflow-Novel-Reader wird **gestoppt**.
- **Weg nach vorn (separat zu entscheiden, nicht Teil dieses Tasks):** beim Reflow-Reading bei
  **MuPDF** bleiben (bereits AGPL, bereits Naht B), das EPUB-Reflow über MuPDF lösen — die Spec
  sieht crengine ohnehin nur als optionalen Phase-4-Pfad "falls MuPDFs EPUB-Qualität nicht reicht".
  Kein Lizenz-Konflikt, kein Vendoring nötig.

---

## Pro-Datei-Tabelle (Header-Scan-Rohausgabe)

> `unklar` = Datei-Header ohne Versionsangabe → via Repo-`README.TXT`/`COPYING` als **GPL-2.0-only** aufgelöst.
> `or-later` = Header mit expliziter "any later version"-Klausel (bzw. LGPL bei `encodings/`).

| Datei | Header-Verdikt |
|---|---|
| `crengine/include/bookformats.h` | unklar |
| `crengine/include/chmfmt.h` | unklar |
| `crengine/include/cp_stats.h` | unklar |
| `crengine/include/cr3version.h` | unklar |
| `crengine/include/crconcurrent.h` | unklar |
| `crengine/include/crengine.h` | unklar |
| `crengine/include/crgui.h` | or-later |
| `crengine/include/cri18n.h` | unklar |
| `crengine/include/crlocks.h` | unklar |
| `crengine/include/crsetup.h` | unklar |
| `crengine/include/crskin.h` | or-later |
| `crengine/include/crtest.h` | unklar |
| `crengine/include/crtrace.h` | unklar |
| `crengine/include/crtxtenc.h` | unklar |
| `crengine/include/cssdef.h` | unklar |
| `crengine/include/docxfmt.h` | unklar |
| `crengine/include/dtddef.h` | unklar |
| `crengine/include/encodings/big5_2003.h` | or-later |
| `crengine/include/encodings/big5.h` | or-later |
| `crengine/include/encodings/cp936ext.h` | or-later |
| `crengine/include/encodings/gb2312.h` | or-later |
| `crengine/include/encodings/gbkext1.h` | or-later |
| `crengine/include/encodings/gbkext2.h` | or-later |
| `crengine/include/encodings/jisx0213.h` | or-later |
| `crengine/include/encodings/ksc5601.h` | or-later |
| `crengine/include/epubfmt.h` | unklar |
| `crengine/include/fb2def.h` | unklar |
| `crengine/include/fb3fmt.h` | unklar |
| `crengine/include/gammatbl.h` | unklar |
| `crengine/include/hist.h` | unklar |
| `crengine/include/hyphman.h` | unklar |
| `crengine/include/lstridmap.h` | unklar |
| `crengine/include/lvarray.h` | unklar |
| `crengine/include/lvautoptr.h` | unklar |
| `crengine/include/lvbmpbuf.h` | unklar |
| `crengine/include/lvdocviewcmd.h` | unklar |
| `crengine/include/lvdocview.h` | unklar |
| `crengine/include/lvdocviewprops.h` | unklar |
| `crengine/include/lvdrawbuf.h` | unklar |
| `crengine/include/lvfnt.h` | unklar |
| `crengine/include/lvfntman.h` | unklar |
| `crengine/include/lvhashtable.h` | unklar |
| `crengine/include/lvimg.h` | unklar |
| `crengine/include/lvmemman.h` | unklar |
| `crengine/include/lvopc.h` | unklar |
| `crengine/include/lvpagesplitter.h` | unklar |
| `crengine/include/lvplatform.h` | unklar |
| `crengine/include/lvptrvec.h` | unklar |
| `crengine/include/lvqueue.h` | unklar |
| `crengine/include/lvrefcache.h` | unklar |
| `crengine/include/lvref.h` | unklar |
| `crengine/include/lvrend.h` | unklar |
| `crengine/include/lvstream.h` | unklar |
| `crengine/include/lvstring.h` | unklar |
| `crengine/include/lvstsheet.h` | unklar |
| `crengine/include/lvstyles.h` | unklar |
| `crengine/include/lvtextfm.h` | unklar |
| `crengine/include/lvthread.h` | unklar |
| `crengine/include/lvtinydom.h` | unklar |
| `crengine/include/lvtypes.h` | unklar |
| `crengine/include/lvxml.h` | unklar |
| `crengine/include/mathml.h` | unklar |
| `crengine/include/odtfmt.h` | unklar |
| `crengine/include/pdbfmt.h` | unklar |
| `crengine/include/props.h` | unklar |
| `crengine/include/renderutil.h` | unklar |
| `crengine/include/rtfcmd.h` | unklar |
| `crengine/include/rtfimp.h` | unklar |
| `crengine/include/s32utils.h` | unklar |
| `crengine/include/textlang.h` | unklar |
| `crengine/include/txtselector.h` | unklar |
| `crengine/include/w32utils.h` | unklar |
| `crengine/include/wolutil.h` | unklar |
| `crengine/include/wordfmt.h` | unklar |
| `crengine/include/xutils.h` | unklar |
| `crengine/src/bookformats.cpp` | unklar |
| `crengine/src/chmfmt.cpp` | unklar |
| `crengine/src/cp_stats.cpp` | unklar |
| `crengine/src/crconcurrent.cpp` | unklar |
| `crengine/src/crgui.cpp` | unklar |
| `crengine/src/cri18n.cpp` | unklar |
| `crengine/src/crskin.cpp` | unklar |
| `crengine/src/crtest.cpp` | unklar |
| `crengine/src/crtxtenc.cpp` | unklar |
| `crengine/src/docxfmt.cpp` | unklar |
| `crengine/src/epubfmt.cpp` | unklar |
| `crengine/src/fb3fmt.cpp` | unklar |
| `crengine/src/hist.cpp` | unklar |
| `crengine/src/hyphman.cpp` | unklar |
| `crengine/src/lstridmap.cpp` | unklar |
| `crengine/src/lvbmpbuf.cpp` | unklar |
| `crengine/src/lvdocview.cpp` | unklar |
| `crengine/src/lvdrawbuf.cpp` | unklar |
| `crengine/src/lvfnt.cpp` | unklar |
| `crengine/src/lvfntman.cpp` | unklar |
| `crengine/src/lvimg.cpp` | unklar |
| `crengine/src/lvmemman.cpp` | unklar |
| `crengine/src/lvopc.cpp` | unklar |
| `crengine/src/lvpagesplitter.cpp` | unklar |
| `crengine/src/lvrend.cpp` | unklar |
| `crengine/src/lvstream.cpp` | unklar |
| `crengine/src/lvstring.cpp` | unklar |
| `crengine/src/lvstsheet.cpp` | unklar |
| `crengine/src/lvstyles.cpp` | unklar |
| `crengine/src/lvtextfm.cpp` | unklar |
| `crengine/src/lvtinydom.cpp` | unklar |
| `crengine/src/lvxml.cpp` | unklar |
| `crengine/src/mathml.cpp` | unklar |
| `crengine/src/mathml_operators.h` | unklar |
| `crengine/src/mathml_table_ext.h` | unklar |
| `crengine/src/mdfmt.cpp` | or-later |
| `crengine/src/mdfmt.h` | or-later |
| `crengine/src/odtfmt.cpp` | unklar |
| `crengine/src/odxutil.cpp` | unklar |
| `crengine/src/odxutil.h` | unklar |
| `crengine/src/pdbfmt.cpp` | unklar |
| `crengine/src/props.cpp` | unklar |
| `crengine/src/renderutil.cpp` | unklar |
| `crengine/src/rtfimp.cpp` | unklar |
| `crengine/src/s32utils.cpp` | unklar |
| `crengine/src/textlang.cpp` | unklar |
| `crengine/src/txtselector.cpp` | unklar |
| `crengine/src/w32utils.cpp` | unklar |
| `crengine/src/wolutil.cpp` | unklar |
| `crengine/src/wordfmt.cpp` | unklar |
| `crengine/src/xutils.cpp` | unklar |

```
SUMMARY or-later=12 only=0 unklar=114
```
