# render-crengine — Native-Dependency-Provenance

Cross-gebaute native Bibliotheken, die crengine-ng (Phase 1b) als Build-Inputs
benötigt. **Alle hier gelisteten Quellen wurden am 2026-06-07 für Android
`arm64-v8a` (`aarch64-linux-android`, `ANDROID_PLATFORM=android-21`,
`ANDROID_STL=c++_static`) cross-kompiliert.**

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

## Integritäts-Verifikation (2026-06-07)

- Alle Archive sind `elf64-littleaarch64` (arm64-v8a).
- `libharfbuzz.a` exportiert die FreeType-Integration (`hb_ft_font_create`,
  `hb_ft_face_create`) — `hb-ft` ist vorhanden.
- `libfreetype.a` referenziert HarfBuzz-Symbole (`hb_buffer_*`) — die finale
  FreeType-Stufe ist korrekt mit HarfBuzz gebaut (Auto-Hinting via HarfBuzz).

## Risk-Register

- **freetype** ist dual lizenziert `FTL OR GPL-2.0-or-later`. Die Gesamt-App ist
  bereits **AGPL-3.0-or-later** (MuPDF), womit GPL-Verträglichkeit gegeben ist.
- **zstd** und **libjpeg-turbo** sind ebenfalls dual/mehrteilig lizenziert; alle
  Teile sind permissiv bzw. GPL-2.0-kompatibel und damit AGPL-verträglich.
- Keine NonCommercial-, Gated- oder ToS-only-Quellen.

---

Letzte Komplettrevision: 2026-06-07 (Phase 1a Dependency-Cross-Build, arm64-v8a).
