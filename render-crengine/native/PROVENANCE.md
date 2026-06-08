# render-crengine — Native-Dependency-Provenance

Die EPUB-Reflow-Engine **crengine-ng** (Phase 1b) plus die ~10 nativen
Bibliotheken, die sie als Build-Inputs benötigt (Phase 1a). **Alle hier
gelisteten Quellen wurden am 2026-06-07 für Android `arm64-v8a`
(`aarch64-linux-android`, `ANDROID_PLATFORM=android-21`,
`ANDROID_STL=c++_static`) cross-kompiliert** und landen im selben Prefix
`native/prefix/aarch64-linux-android/`.

## Build-Setup

- **Rezept:** reproduziert das LxReader `thirdparty-bldtool`
  (`gitlab.com/coolreader-ng/lxreader`, GPL-3.0) — siehe
  `native/thirdparty/thirdparty-bldtool/`. Versionen, Build-Flags und die
  Build-Reihenfolge stammen 1:1 aus diesem Tool.
- **NDK:** Android NDK `28.2.13676358` (LxReader nutzt r27; ein neueres NDK
  baut alle Deps fehlerfrei).
- **CMake:** Android-SDK-CMake `3.22.1`, Toolchain
  `$NDK/build/cmake/android.toolchain.cmake`.
- **CFLAGS:** `-fPIC -DPIC -g0 -O2` (PIC für späteres Linken in eine `.so`).
- **Output-Prefix:** `native/prefix/aarch64-linux-android/`
  (`lib/*.a` + `include/`).
- **fontconfig:** bewusst NICHT gebaut (crengine-ng wird mit
  `-DUSE_FONTCONFIG=OFF` gebaut). Kein ICU.

### Build-Reihenfolge (zirkuläre freetype↔harfbuzz-Abhängigkeit)

```
zlib → libpng → libjpeg-turbo → libwebp → freetype-stage0 (ohne harfbuzz)
     → harfbuzz (mit FreeType / hb-ft) → freetype (final, mit harfbuzz)
     → fribidi → libunibreak → utf8proc → zstd
```

`freetype-stage0` baut FreeType ohne HarfBuzz, damit HarfBuzz dagegen linken
kann; danach wird FreeType final mit HarfBuzz-Unterstützung neu gebaut.

### Reproduzieren

```bash
cd native/thirdparty/thirdparty-bldtool
# make.conf zeigt auf das lokale NDK/SDK (siehe Datei)
./build.sh aarch64-linux-android zlib
./build.sh aarch64-linux-android libpng libjpeg-turbo libwebp \
    freetype-stage0 harfbuzz freetype fribidi libunibreak utf8proc zstd
```

## Quellen

| Name | Version | Upstream-URL | Lizenz (SPDX) | Static-Lib(s) |
|------|---------|--------------|---------------|---------------|
| zlib | 1.3.1 | https://github.com/madler/zlib/releases/tag/v1.3.1 | `Zlib` | `libz.a` |
| libpng | 1.6.50 | https://sourceforge.net/projects/libpng/files/libpng16/1.6.50/ | `Libpng` | `libpng.a`, `libpng16.a` |
| libjpeg-turbo | 3.1.2 | https://github.com/libjpeg-turbo/libjpeg-turbo/releases/tag/3.1.2 | `IJG AND BSD-3-Clause AND Zlib` | `libjpeg.a`, `libturbojpeg.a` |
| libwebp | 1.6.0 | https://storage.googleapis.com/downloads.webmproject.org/releases/webp/index.html | `BSD-3-Clause` | `libwebp.a`, `libwebpdecoder.a`, `libwebpdemux.a`, `libwebpmux.a`, `libsharpyuv.a`, `libcpufeatures-webp.a` |
| freetype | 2.14.1 | https://download.savannah.gnu.org/releases/freetype/ | `FTL OR GPL-2.0-or-later` | `libfreetype.a` |
| harfbuzz | 12.1.0 | https://github.com/harfbuzz/harfbuzz/releases/tag/12.1.0 | `MIT` | `libharfbuzz.a` |
| fribidi | 1.0.16 | https://github.com/fribidi/fribidi/releases/tag/v1.0.16 | `LGPL-2.1-or-later` | `libfribidi.a` |
| libunibreak | 6.1 | https://github.com/adah1972/libunibreak/releases/tag/libunibreak_6_1 | `Zlib` | `libunibreak.a`, `liblinebreak.a` |
| utf8proc | 2.11.0 | https://github.com/JuliaStrings/utf8proc/releases/tag/v2.11.0 | `MIT` | `libutf8proc.a` |
| zstd | 1.5.7 | https://github.com/facebook/zstd/releases/tag/v1.5.7 | `BSD-3-Clause OR GPL-2.0-only` | `libzstd.a` |

### Build-Anpassungen gegenüber dem LxReader-Rezept

- **zlib-URL:** `www.zlib.net` rotiert ältere Releases aus dem Root (1.3.1 → 404).
  Stattdessen wird das byte-identische GitHub-Release-Asset gezogen
  (über die SHA512-Prüfsumme des Rezepts verifiziert). Siehe
  `thirdparty/thirdparty-bldtool/repo/zlib.meta.sh`.

## Engine: crengine-ng (Phase 1b)

Die EPUB-Reflow-Engine selbst, gebaut **gegen** den obigen Dependency-Prefix und
**in** denselben Prefix installiert (damit `find_package(crengine-ng CONFIG)` in
Phase 1c auflöst).

| Name | Pinned Commit | Upstream-URL | Lizenz (SPDX) | Build-Datum | Static-Lib |
|------|---------------|--------------|---------------|-------------|------------|
| crengine-ng | `ec57cc1` (`ec57cc1d16c47237c10ac6f3cfa491791e23a952`) | https://gitlab.com/coolreader-ng/crengine-ng | `GPL-2.0-or-later` | 2026-06-07 | `libcrengine-ng.a` |

- **Rezept:** reproduziert LxReaders `tools/crengine-ng-build/build-all.sh`
  (`gitlab.com/coolreader-ng/lxreader`, GPL-3.0), auf eine ABI (arm64-v8a)
  reduziert. Build-Skript: `native/build-crengine.sh`.
- **Decisive Flag `-DUSE_FONTCONFIG=OFF`** — auf Android gibt es kein fontconfig;
  Fonts werden später app-seitig registriert. Ebenso `-DCRE_BUILD_SHARED=OFF`,
  `-DCRE_BUILD_STATIC=ON`, `-DUSE_FONTCONFIG=OFF`,
  `-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON` (16-KB-Page-Geräte),
  `-DWITH_LIBJXL=OFF` (libjxl nicht gebaut). Alle übrigen `WITH_*` aktiv
  (freetype, harfbuzz, fribidi, libunibreak, png, jpeg, webp, utf8proc, zlib,
  zstd) — gegen den Prefix via `-DCMAKE_FIND_ROOT_PATH=<prefix>`.
- **Patch nicht nötig:** Der LxReader-Patch `0.9.13-account-find-root-path.patch`
  (CMAKE_FIND_ROOT_PATH → Interface-Link-Dirs) ist im gepinnten Commit `ec57cc1`
  bereits upstream gemergt.
- **Host-CMake-Workaround:** Die gebündelte SDK-CMake `3.22.1` honoriert
  `CMAKE_REQUIRED_LINK_DIRECTORIES` in `CHECK_INCLUDE_FILE` noch nicht (kam erst
  in CMake 3.31, das LxReader nutzt). WebPConfig exportiert seine Libs als bloße
  Namen (`webp;webpdecoder;…`), wodurch der `hb-ft.h`-Feature-Test sie als
  `-lwebp` ohne Suchpfad linkt und scheitert. Fix: Prefix-`lib/` global auf den
  Linker-Suchpfad gelegt (`-DCMAKE_LIBRARY_PATH` + `-DCMAKE_EXE_LINKER_FLAGS=-L…`)
  — keine crengine-ng-Quellenänderung.

### Reproduzieren (crengine-ng)

```bash
cd native
./build-crengine.sh    # klont den Pin, konfiguriert, baut crengine-ng_static, installiert in den Prefix
```

## Integritäts-Verifikation (2026-06-07)

- Alle Archive sind `elf64-littleaarch64` (arm64-v8a).
- `libharfbuzz.a` exportiert die FreeType-Integration (`hb_ft_font_create`,
  `hb_ft_face_create`) — `hb-ft` ist vorhanden.
- `libfreetype.a` referenziert HarfBuzz-Symbole (`hb_buffer_*`) — die finale
  FreeType-Stufe ist korrekt mit HarfBuzz gebaut (Auto-Hinting via HarfBuzz).
- `libcrengine-ng.a` ist `elf64-littleaarch64`, enthält die crengine-Symbole
  (`LVDocView`, `LVFileParserBase` u. a.) und installiert das CMake-Paket
  `lib/cmake/crengine-ng/` mit dem Imported-Target
  `crengine-ng::crengine-ng_static` → `find_package(crengine-ng CONFIG)` löst auf.

## Risk-Register

- **freetype** ist dual lizenziert `FTL OR GPL-2.0-or-later`. Die Gesamt-App ist
  bereits **AGPL-3.0-or-later** (MuPDF), womit GPL-Verträglichkeit gegeben ist.
- **zstd** und **libjpeg-turbo** sind ebenfalls dual/mehrteilig lizenziert; alle
  Teile sind permissiv bzw. GPL-2.0-kompatibel und damit AGPL-verträglich.
- **crengine-ng** ist `GPL-2.0-or-later`. Die Gesamt-App ist bereits
  **AGPL-3.0-or-later** (MuPDF) — GPL-2.0-or-later ist nach oben mit GPL-3.0/AGPL-3.0
  verträglich. Jede Verteilung legt den Quellcode offen (`build-crengine.sh` hält
  den exakten Pin fest).
- Keine NonCommercial-, Gated- oder ToS-only-Quellen.

## Phase 1c — JNI-Bridge + Test-Assets

Der JNI-Bridge-Code (`src/main/cpp/`) und die Test-Assets:

| Quelle | URL | Lizenz | Verwendung |
|--------|-----|--------|------------|
| **LxReader** (`jnigraphicslib.cpp/.h`, `lvcolordrawbufex.cpp/.h`) | https://gitlab.com/coolreader-ng/lxreader | GPL-3.0-or-later | Bitmap-Lock + BGRX→RGBA-Konvertierung, 1:1 übernommen (basierend auf CoolReader, Vadim Lopatin). `cr3_bridge.cpp` ist eigener Code, adaptiert deren Muster. |
| **sample.epub** (`src/androidTest/assets/`) | — | eigene Test-Fixture (Kopie aus `render-core`) | Reflow-Render-Gate. |

- **LxReader** ist `GPL-3.0-or-later` — die Gesamt-App ist bereits
  **AGPL-3.0-or-later**, also verträglich. `cr3_bridge.cpp` trägt einen
  AGPL-Header.

## Phase 8 — Gebündelte Lese-Fonts + DE/EN-Silbentrennung

Diese Assets werden **ins App-Release gebündelt** (`app/src/main/assets/`) und
zusätzlich in die render-crengine-Instrumented-Test-Assets gespiegelt
(`render-crengine/src/androidTest/assets/`). Zur Laufzeit registriert
`CrengineNative.nativeInit` jede Schrift (per FreeType-Familienname) und lädt das
entpackte Trennmuster-Verzeichnis über `HyphMan::initDictionaries`.

### Lese-Fonts (`assets/fonts/`)

| Name | Familienname (registriert) | Version | Upstream-URL | Lizenz (SPDX) | Erfassungsdatum |
|------|----------------------------|---------|--------------|---------------|-----------------|
| **DejaVu Sans** | `DejaVu Sans` | 2.37 | https://dejavu-fonts.github.io | `Bitstream-Vera` (+ Public-Domain-Erweiterungen, permissiv, kein Copyleft) | 2026-06-07 |
| **Literata** | `Literata` | 3.103 (variabel: `opsz` 7–72, `wght` 200–900, Default 12/400) | https://github.com/google/fonts/tree/main/ofl/literata (`Literata[opsz,wght].ttf`) | `OFL-1.1` | 2026-06-07 |
| **Bitter** | `Bitter` | 3.021 (statisch Regular, Latin-Subset via Fontsource-Build von Google Fonts) | https://github.com/google/fonts/tree/main/ofl/bitter · https://cdn.jsdelivr.net/fontsource/fonts/bitter@latest/latin-400-normal.ttf | `OFL-1.1` | 2026-06-07 |

- **Cap/Volumen:** DejaVuSans.ttf 741 KB · Literata.ttf 932 KB · Bitter.ttf 43 KB.
- **Filter:** keine Nachbearbeitung — die TTFs werden unverändert gebündelt.
  Glyph-Abdeckung für Deutsch (ä ö ü Ä Ö Ü ß), Akzente und typografische
  Anführungszeichen/Gedankenstriche bei allen drei verifiziert.
- **OFL-Volltext** der beiden OFL-Schriften liegt bei:
  `app/src/main/assets/fonts/licenses/Literata-OFL.txt`,
  `app/src/main/assets/fonts/licenses/Bitter-OFL.txt` (OFL-1.1 verlangt Mitlieferung).
- **Familienname-Hinweis:** crengine-ng wählt die Schrift per **exaktem**
  Abgleich des registrierten FreeType-`family_name`. Maßgeblich ist daher
  `"DejaVu Sans"` (mit Leerzeichen), nicht `"DejaVuSans"`. SSOT der Familien-/
  Asset-Zuordnung: `domain/render/NovelFont.kt` (`NovelFonts`).

### Silbentrennungs-Muster (`assets/hyph/`)

Echte TeX-Muster-Wörterbücher aus dem **hyph-utf8 / TeX-Hyphenation**-Paket, von
crengine-ngs Build ins `.pattern`-Format konvertiert (übernommen aus dem
crengine-ng-Prefix `share/crengine-ng/hyph/`, gebaut aus dem gepinnten Commit
`ec57cc1`, siehe oben).

| Name | Sprach-Tag | Quelle | Lizenz (SPDX) | Erfassungsdatum |
|------|-----------|--------|---------------|-----------------|
| **hyph-de-1996.pattern** | `de` (reformierte Rechtschreibung 2006) | hyph-utf8 `patterns/tex/hyph-de-1996.tex` (https://github.com/hyphenation/tex-hyphen) via crengine-ng | `MIT` (im Datei-Header dokumentiert) | 2026-06-07 |
| **hyph-en-us.pattern** | `en-US` | hyph-utf8 `patterns/tex/hyph-en-us.tex` (https://github.com/hyphenation/tex-hyphen) via crengine-ng | permissiv/royalty-free (Knuth/Liang, Copy/Verbreitung mit Copyright-Vermerk erlaubt; im Datei-Header dokumentiert) | 2026-06-07 |

- **Cap/Volumen:** hyph-de-1996.pattern 974 KB · hyph-en-us.pattern 135 KB.
- **Filter:** unverändert übernommen. Mapping `de`→`hyph-de-1996.pattern`,
  `en`→`hyph-en-us.pattern` in `render-crengine/.../ReflowCss.kt`; alle anderen
  Sprachen fallen auf crengine-ngs generischen `@algorithm`-Pfad zurück.

### Phase-8-Risk-Notiz

- **DejaVu Sans** (Bitstream-Vera) und **Literata/Bitter** (OFL-1.1) sind
  permissiv und AGPL-verträglich. OFL-1.1 verlangt Mitlieferung des Lizenztexts
  (erfüllt, siehe `assets/fonts/licenses/`) und untersagt den Verkauf der Font-
  Dateien für sich — beides für eine gebündelte App unproblematisch.
- **hyph-de-1996** (MIT) und **hyph-en-us** (permissiv, royalty-free) sind
  GPL-/AGPL-verträglich.
- Keine NonCommercial-, Gated- oder ToS-only-Quellen. Kein AGPL-inkompatibles Asset.

---

Letzte Komplettrevision: 2026-06-07 (Phase 8 Lese-Fonts + DE/EN-Silbentrennung; +Phase 1c JNI-Render arm64-v8a; +Phase 1b Cross-Build).
