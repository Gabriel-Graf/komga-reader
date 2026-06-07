# Icon-Generator

Generiert `app/src/main/kotlin/com/komgareader/app/ui/icons/LucideIcons.kt` aus Lucide-SVGs
mit E-Ink-tauglichem Stroke. Die App nutzt **nie** Material-Icons mehr — nur `AppIcons` (SSOT).

## Quelle (Provenance)

| Feld | Wert |
|------|------|
| **Name** | Lucide (`lucide-static`) |
| **URL** | https://github.com/lucide-icons/lucide |
| **Lizenz** | ISC (SPDX: `ISC`) — permissiv, Attribution in `NOTICE` |
| **Version** | v0.460.0 (siehe `package.json`) |
| **Erfassungsdatum** | 2026-06-07 |
| **Cap / Filter** | nur die 36 in `icon-set.mjs` gelisteten Glyphen, kein voller Satz (~1600) |
| **Risk** | keine — ISC ohne ShareAlike/NC |

## Regenerieren

```bash
cd tools/icons && npm install && npm test && npm run generate
```

## Stroke ändern

Die Stroke-Breite wird **nicht** hier gesetzt, sondern zentral in der generierten
`LucideIcons.kt` (Konstante `STROKE`, aktuell `2.5f`). Nur den Wert ändern — **kein**
Neu-Generieren nötig, da der `d`-Pfad strokeunabhängig ist.

## Architektur

- `icon-set.mjs` — welche Glyphen (kebab → Kotlin-Property).
- `lib/svg-to-pathdata.mjs` — pure Funktionen: SVG-Primitive (`line`/`rect`/`circle`/
  `polyline`/`polygon`/`ellipse`) → Path-`d`; ganze SVG → konkateniertes `d`.
- `generate.mjs` — liest Set, ruft Converter, emittiert Kotlin.

Lucide-Icons sind reine Stroke-Pfade (kein Fill), 24×24-viewBox, Round-Caps/Joins.
