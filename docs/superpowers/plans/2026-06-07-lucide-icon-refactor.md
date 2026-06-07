# Lucide-Icon-Refactor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Alle Material-Design-Icons (`material-icons-extended`) durch eine zentrale, E-Ink-getunte Lucide-Icon-Registry ersetzen — generiert aus den Lucide-MIT-SVGs mit dickerem Stroke (2.5px), eine Quelle der Wahrheit, semantisch benannt.

**Architecture:** Ein Node-Generator (`tools/icons/`) liest die benötigten `lucide-static`-SVGs, normalisiert alle SVG-Primitive (`circle`/`rect`/`line`/`polyline`/`polygon`/`ellipse`) zu Path-`d`-Strings, konkateniert sie pro Icon und emittiert eine generierte Kotlin-Datei (`LucideIcons.kt`) mit den `d`-Strings. Zur Laufzeit baut ein gemeinsamer Helper jeden `ImageVector` über `PathParser().parsePathString(d).toNodes()` mit **einer** zentralen Stroke-Konstante (`STROKE = 2.5f`) — Stroke ist damit ohne Neu-Generierung tunbar. Eine handgepflegte `AppIcons.kt` bildet **semantische** Namen (`AppIcons.Delete`) auf die generierten Lucide-Glyphen ab; alle Aufruf-Stellen nutzen nur `AppIcons`. `material-icons-extended` wird als Dependency entfernt.

**Tech Stack:** Kotlin/Jetpack Compose (Compose BOM 2024.10.01, `androidx.compose.ui.graphics.vector.PathParser`), Node v22 (Generator, `lucide-static`), JUnit (Generator-Unit-Tests via `node:test`).

---

## File Structure

| Datei | Verantwortung | Neu/Ändern |
|---|---|---|
| `tools/icons/package.json` | Generator-Deps (`lucide-static`) + npm-Scripts | Neu |
| `tools/icons/lib/svg-to-pathdata.mjs` | Pure Funktionen: SVG-Primitive → Path-`d`, SVG-Datei → konkateniertes `d` | Neu |
| `tools/icons/lib/svg-to-pathdata.test.mjs` | `node:test`-Unit-Tests für die Konverter | Neu |
| `tools/icons/icon-set.mjs` | Liste der benötigten Icons (kebab-Name → Kotlin-Property-Name) | Neu |
| `tools/icons/generate.mjs` | Liest Set, ruft Konverter, emittiert `LucideIcons.kt` | Neu |
| `tools/icons/README.md` | Provenance + Regenerieren-Anleitung | Neu |
| `app/src/main/kotlin/com/komgareader/app/ui/icons/LucideIcons.kt` | **Generiert** — `d`-Strings + lazy `ImageVector`s + Stroke-Helper + `STROKE`-Konstante | Neu (generiert) |
| `app/src/main/kotlin/com/komgareader/app/ui/icons/AppIcons.kt` | **SSOT** — semantische Namen → Lucide-Glyphen | Neu |
| 18 UI-Dateien (siehe Task 6) | `Icons.*` → `AppIcons.*`, Imports tauschen | Ändern |
| `app/build.gradle.kts` | `material-icons-extended` entfernen | Ändern |
| `gradle/libs.versions.toml` | `compose-material-icons-extended` entfernen | Ändern |
| `NOTICE` | Material Symbols raus, Lucide (ISC) rein | Ändern |
| `tools/e2e/` (Screenshot) | E2E-Verifikation auf `eink_test`-Emulator | Nutzen |

## Icon-Mapping (verbindliche SSOT)

Semantischer Name in `AppIcons` → Lucide-Glyph (kebab SVG / Kotlin-Property). **Fett = bewusste Verbesserung** statt 1:1.

| `AppIcons.X` | Zweck (Aufruf-Stellen) | Alt (Material) | Lucide |
|---|---|---|---|
| `Close` | Modal schließen, Suche leeren, Chip entfernen | `Outlined.Close` | `x` → `X` |
| `Back` | Zurück-Navigation | `AutoMirrored.*.ArrowBack` | `arrow-left` → `ArrowLeft` |
| `Forward` | nächste Vorschau | `AutoMirrored.Outlined.ArrowForward` | `arrow-right` → `ArrowRight` |
| `Check` | Auswahl-/Gelesen-Häkchen (vereinheitlicht Outlined) | `Outlined/Filled.Check` | `check` → `Check` |
| `Plus` | Inkrement / neu / hinzufügen | `Outlined.Add` | `plus` → `Plus` |
| `Minus` | Dekrement (Stepper) | `Outlined.Remove` | `minus` → `Minus` |
| `ChevronRight` | Drill-in | `AutoMirrored.*.KeyboardArrowRight` | `chevron-right` → `ChevronRight` |
| `ChevronDown` | Aufklappen (Accordion/Profilliste) | `Outlined.KeyboardArrowDown`, `ExpandMore` | `chevron-down` → `ChevronDown` |
| `ChevronUp` | Zuklappen | `Outlined.ExpandLess` | `chevron-up` → `ChevronUp` |
| `Search` | Suche | `Outlined.Search` | `search` → `Search` |
| `Refresh` | Aktualisieren/Sync/Neu-laden (vereinheitlicht) | `Filled.Refresh`, `Outlined.Sync` | `refresh-cw` → `RefreshCw` |
| **`Edit`** | Gruppe/Profil bearbeiten (Zahnrad→Stift, klarer) | `Filled/Outlined.Settings` | `square-pen` → `SquarePen` |
| `Settings` | Einstellungen-Tab + Sektion | `Outlined.Settings` | `settings` → `Settings` |
| `Delete` | Gruppe/Download löschen (vereinheitlicht Outlined) | `Filled/Outlined.Delete` | `trash-2` → `Trash2` |
| `Download` | Download-Aktion + Sektion | `Filled/Outlined.CloudDownload`, `Outlined.Download` | `cloud-download` → `CloudDownload` |
| **`Local`** | Badge „auf Gerät" (klarer als DownloadDone) | `Outlined/Filled.DownloadDone` | `hard-drive-download` → `HardDriveDownload` |
| `Cloud` | Badge „nur online" | `Outlined/Filled.CloudQueue` | `cloud` → `Cloud` |
| `Info` | Info | `Outlined.Info` | `info` → `Info` |
| **`Filter`** | Typ-Filter | `Outlined.FilterList` | `list-filter` → `ListFilter` |
| **`Overflow`** | Überlauf-Menü | `Filled.MoreVert` | `ellipsis-vertical` → `EllipsisVertical` |
| **`Stop`** | Download abbrechen | `Filled.Stop` | `circle-stop` → `CircleStop` |
| `GridView` | Layout-Toggle Kachel | `Filled/Outlined.GridView` | `layout-grid` → `LayoutGrid` |
| `ListView` | Layout-Toggle Liste | `AutoMirrored.Outlined.ViewList` | `list` → `List` |
| `Bookmark` | „Hier weiterlesen" | `Outlined/Filled.Bookmark` | `bookmark` → `Bookmark` |
| `Library` | Bibliothek-Tab | `Outlined.LibraryBooks` | `library` → `Library` |
| **`Groups`** | Gruppen-Tab | `Outlined.Dashboard` | `layout-dashboard` → `LayoutDashboard` |
| **`Plugins`** | Plugins-Tab (Puzzle ≈ Onyx-Apps) | `Outlined.Extension` | `puzzle` → `Puzzle` |
| `Contrast` | Darstellung/Theme | `Outlined.Contrast` | `contrast` → `Contrast` |
| `Palette` | Farbfilter-Sektion | `Outlined.Palette` | `palette` → `Palette` |
| **`Reader`** | Reader-Sektion | `Outlined.ChromeReaderMode` | `book-open` → `BookOpen` |
| **`Language`** | Sprache | `Outlined.Language` | `languages` → `Languages` |
| **`Connection`** | Verbindung/Server-Sektion | `Outlined.Cloud` | `server` → `Server` |
| **`ReaderMode`** | Lesemodus-Toggle paged↔webtoon | `Filled.ViewDay` | `gallery-vertical` → `GalleryVertical` |
| **`PanelMode`** | Guided-Panel-Modus (Comic) | `Filled.GridView` | `grid-2x2` → `Grid2x2` |

**Entfällt komplett:** `Outlined.BatteryStd` (StatusCluster) — Akku-Icon raus, nur `%`-Text bleibt (User-Entscheid).

36 Lucide-Glyphen. Generator zieht genau diese.

---

### Task 1: Generator-Projekt + Provenance

**Files:**
- Create: `tools/icons/package.json`
- Create: `tools/icons/icon-set.mjs`
- Create: `tools/icons/README.md`

- [ ] **Step 1: package.json anlegen**

```json
{
  "name": "komga-reader-icon-generator",
  "private": true,
  "type": "module",
  "scripts": {
    "test": "node --test",
    "generate": "node generate.mjs"
  },
  "dependencies": {
    "lucide-static": "^0.460.0"
  }
}
```

- [ ] **Step 2: icon-set.mjs anlegen** (kebab-SVG-Name → Kotlin-Property)

```js
// Genau die im App-UI verwendeten Glyphen. Reihenfolge = alphabetisch nach Property.
export const ICONS = {
  "arrow-left": "ArrowLeft",
  "arrow-right": "ArrowRight",
  "book-open": "BookOpen",
  "bookmark": "Bookmark",
  "check": "Check",
  "chevron-down": "ChevronDown",
  "chevron-right": "ChevronRight",
  "chevron-up": "ChevronUp",
  "circle-stop": "CircleStop",
  "cloud": "Cloud",
  "cloud-download": "CloudDownload",
  "contrast": "Contrast",
  "download": "Download",
  "ellipsis-vertical": "EllipsisVertical",
  "gallery-vertical": "GalleryVertical",
  "grid-2x2": "Grid2x2",
  "hard-drive-download": "HardDriveDownload",
  "info": "Info",
  "languages": "Languages",
  "layout-dashboard": "LayoutDashboard",
  "layout-grid": "LayoutGrid",
  "library": "Library",
  "list": "List",
  "list-filter": "ListFilter",
  "minus": "Minus",
  "palette": "Palette",
  "plus": "Plus",
  "puzzle": "Puzzle",
  "refresh-cw": "RefreshCw",
  "search": "Search",
  "server": "Server",
  "settings": "Settings",
  "square-pen": "SquarePen",
  "trash-2": "Trash2",
  "x": "X",
};
```

- [ ] **Step 3: README.md mit Provenance** (Daten-Provenance-Pflicht)

```markdown
# Icon-Generator

Generiert `app/.../ui/icons/LucideIcons.kt` aus Lucide-SVGs mit E-Ink-Stroke (2.5px).

## Quelle (Provenance)
- **Name:** Lucide (`lucide-static`)
- **URL:** https://github.com/lucide-icons/lucide
- **Lizenz:** ISC (SPDX: ISC)
- **Erfassungsdatum:** 2026-06-07
- **Cap/Filter:** nur die in `icon-set.mjs` gelisteten 36 Glyphen (kein voller Satz)
- **Risk:** keine — ISC ist permissiv, Attribution in NOTICE.

## Regenerieren
\`\`\`bash
cd tools/icons && npm install && npm test && npm run generate
\`\`\`
Stroke-Breite wird NICHT hier gesetzt, sondern zentral in `LucideIcons.kt` (`STROKE`-Konstante) — nur den Wert ändern, kein Neu-Generieren nötig.
```

- [ ] **Step 4: Install + Commit**

```bash
cd tools/icons && npm install 2>&1 | tail -2
git add tools/icons/package.json tools/icons/package-lock.json tools/icons/icon-set.mjs tools/icons/README.md
git commit -m "build(icons): Generator-Gerüst + lucide-static + Provenance"
```

---

### Task 2: SVG→Path-Konverter (TDD, pure)

Lucide-SVGs sind 24×24-viewBox, Kinder: `path`, `circle`, `rect`, `line`, `polyline`, `polygon`. Konverter wandelt alle Primitive in Path-`d` und konkateniert sie (gemeinsamer Stroke-Stil → ein Pfad-String).

**Files:**
- Create: `tools/icons/lib/svg-to-pathdata.mjs`
- Test: `tools/icons/lib/svg-to-pathdata.test.mjs`

- [ ] **Step 1: Failing Tests schreiben**

```js
import { test } from "node:test";
import assert from "node:assert/strict";
import { lineToPath, rectToPath, circleToPath, polyToPath, svgToPathData } from "./svg-to-pathdata.mjs";

test("line → M..L", () => {
  assert.equal(lineToPath({ x1: "1", y1: "2", x2: "3", y2: "4" }), "M1 2L3 4");
});

test("polyline → offen (kein Z)", () => {
  assert.equal(polyToPath({ points: "1,2 3,4 5,6" }, false), "M1 2L3 4L5 6");
});

test("polygon → geschlossen (Z)", () => {
  assert.equal(polyToPath({ points: "1,2 3,4 5,6" }, true), "M1 2L3 4L5 6Z");
});

test("rect ohne rx → 4 Kanten + Z", () => {
  assert.equal(rectToPath({ x: "2", y: "3", width: "10", height: "6" }), "M2 3H12V9H2Z");
});

test("rect mit rx → abgerundet via Arcs", () => {
  // Eckpunkt-Arcs: startet am oberen Rand nach rx eingerückt
  const d = rectToPath({ x: "0", y: "0", width: "10", height: "10", rx: "2" });
  assert.match(d, /^M2 0H8A2 2 0 0 1 10 2/);
});

test("circle → zwei Halbkreis-Arcs", () => {
  assert.equal(circleToPath({ cx: "12", cy: "12", r: "10" }),
    "M2 12A10 10 0 1 0 22 12A10 10 0 1 0 2 12");
});

test("svgToPathData konkateniert alle Kinder", () => {
  const svg = '<svg viewBox="0 0 24 24"><path d="M4 4h2"/><line x1="1" y1="1" x2="2" y2="2"/></svg>';
  assert.equal(svgToPathData(svg), "M4 4h2 M1 1L2 2");
});
```

- [ ] **Step 2: Tests laufen, müssen scheitern**

Run: `cd tools/icons && node --test lib/`
Expected: FAIL — „Cannot find module ./svg-to-pathdata.mjs" bzw. Funktionen undefiniert.

- [ ] **Step 3: Konverter implementieren**

```js
// Pure SVG-Primitive → Path-d. Lucide nutzt nur diese Primitive.
const num = (v, d = 0) => (v === undefined ? d : parseFloat(v));

export function lineToPath(a) {
  return `M${num(a.x1)} ${num(a.y1)}L${num(a.x2)} ${num(a.y2)}`;
}

export function polyToPath(a, closed) {
  const pts = a.points.trim().split(/\s+/).map((p) => p.split(",").map(Number));
  const [h, ...rest] = pts;
  return `M${h[0]} ${h[1]}` + rest.map((p) => `L${p[0]} ${p[1]}`).join("") + (closed ? "Z" : "");
}

export function rectToPath(a) {
  const x = num(a.x), y = num(a.y), w = num(a.width), h = num(a.height);
  const rx = num(a.rx, num(a.ry, 0)), ry = num(a.ry, rx);
  if (rx <= 0 && ry <= 0) {
    return `M${x} ${y}H${x + w}V${y + h}H${x}Z`;
  }
  return (
    `M${x + rx} ${y}` +
    `H${x + w - rx}A${rx} ${ry} 0 0 1 ${x + w} ${y + ry}` +
    `V${y + h - ry}A${rx} ${ry} 0 0 1 ${x + w - rx} ${y + h}` +
    `H${x + rx}A${rx} ${ry} 0 0 1 ${x} ${y + h - ry}` +
    `V${y + ry}A${rx} ${ry} 0 0 1 ${x + rx} ${y}Z`
  );
}

export function circleToPath(a) {
  const cx = num(a.cx), cy = num(a.cy), r = num(a.r);
  return `M${cx - r} ${cy}A${r} ${r} 0 1 0 ${cx + r} ${cy}A${r} ${r} 0 1 0 ${cx - r} ${cy}`;
}

export function ellipseToPath(a) {
  const cx = num(a.cx), cy = num(a.cy), rx = num(a.rx), ry = num(a.ry);
  return `M${cx - rx} ${cy}A${rx} ${ry} 0 1 0 ${cx + rx} ${cy}A${rx} ${ry} 0 1 0 ${cx - rx} ${cy}`;
}

const attrs = (tag) => {
  const o = {};
  for (const m of tag.matchAll(/([a-zA-Z0-9_-]+)="([^"]*)"/g)) o[m[1]] = m[2];
  return o;
};

export function svgToPathData(svg) {
  const parts = [];
  for (const m of svg.matchAll(/<(path|line|polyline|polygon|rect|circle|ellipse)\b([^>]*)\/?>/g)) {
    const tag = m[1], a = attrs(m[2]);
    if (tag === "path") parts.push(a.d.trim());
    else if (tag === "line") parts.push(lineToPath(a));
    else if (tag === "polyline") parts.push(polyToPath(a, false));
    else if (tag === "polygon") parts.push(polyToPath(a, true));
    else if (tag === "rect") parts.push(rectToPath(a));
    else if (tag === "circle") parts.push(circleToPath(a));
    else if (tag === "ellipse") parts.push(ellipseToPath(a));
  }
  return parts.join(" ");
}
```

- [ ] **Step 4: Tests laufen, müssen grün sein**

Run: `cd tools/icons && node --test lib/`
Expected: PASS (7 Tests).

- [ ] **Step 5: Commit**

```bash
git add tools/icons/lib/
git commit -m "feat(icons): SVG-Primitive→Path-d-Konverter (TDD)"
```

---

### Task 3: Generator → LucideIcons.kt

**Files:**
- Create: `tools/icons/generate.mjs`
- Create (generiert): `app/src/main/kotlin/com/komgareader/app/ui/icons/LucideIcons.kt`

- [ ] **Step 1: generate.mjs schreiben**

```js
import { readFileSync, writeFileSync, mkdirSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";
import { ICONS } from "./icon-set.mjs";
import { svgToPathData } from "./lib/svg-to-pathdata.mjs";

const here = dirname(fileURLToPath(import.meta.url));
const svgDir = join(here, "node_modules", "lucide-static", "icons");
const outFile = join(here, "..", "..", "app", "src", "main", "kotlin",
  "com", "komgareader", "app", "ui", "icons", "LucideIcons.kt");

const entries = Object.entries(ICONS).map(([kebab, prop]) => {
  const svg = readFileSync(join(svgDir, `${kebab}.svg`), "utf8");
  const d = svgToPathData(svg).replace(/\\/g, "\\\\").replace(/"/g, '\\"');
  return { prop, d };
});

const body = entries.map(({ prop, d }) =>
  `    val ${prop}: ImageVector by lazy { lucide("${prop}", "${d}") }`
).join("\n");

const out = `// GENERIERT von tools/icons/generate.mjs — NICHT von Hand editieren.
// Quelle: Lucide (ISC), https://github.com/lucide-icons/lucide
package com.komgareader.app.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** Lucide-Glyphen mit E-Ink-Stroke. Stroke zentral hier tunbar — keine Neu-Generierung nötig. */
object LucideIcons {
    /** E-Ink-Stroke-Breite (Lucide-Default 2f; hier dicker für E-Ink-Sichtbarkeit). */
    const val STROKE: Float = 2.5f

    private fun lucide(name: String, pathData: String): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).addPath(
            pathData = PathParser().parsePathString(pathData).toNodes(),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = STROKE,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ).build()

${body}
}
`;
mkdirSync(dirname(outFile), { recursive: true });
writeFileSync(outFile, out);
console.log(`${entries.length} Icons → ${outFile}`);
```

- [ ] **Step 2: Generieren**

Run: `cd tools/icons && node generate.mjs`
Expected: `36 Icons → .../LucideIcons.kt`

- [ ] **Step 3: Sichtprüfung der generierten Datei**

Run: `head -40 app/src/main/kotlin/com/komgareader/app/ui/icons/LucideIcons.kt`
Expected: `object LucideIcons`, `STROKE = 2.5f`, je Icon eine `val ... by lazy`-Zeile, keine leeren `pathData=""`.

- [ ] **Step 4: Commit**

```bash
git add tools/icons/generate.mjs app/src/main/kotlin/com/komgareader/app/ui/icons/LucideIcons.kt
git commit -m "feat(icons): Generator + generierte LucideIcons.kt (Stroke 2.5px)"
```

---

### Task 4: AppIcons-SSOT

**Files:**
- Create: `app/src/main/kotlin/com/komgareader/app/ui/icons/AppIcons.kt`

- [ ] **Step 1: AppIcons schreiben** (semantisch → Lucide; einzige Schicht, die das UI berührt)

```kotlin
package com.komgareader.app.ui.icons

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Zentrale Icon-Registry (SSOT). UI nutzt NUR `AppIcons.*` — nie `LucideIcons.*` oder
 * `androidx.compose.material.icons.*` direkt. Neuer Bedarf: hier einen semantischen Namen
 * ergänzen und auf einen Lucide-Glyph zeigen (ggf. Glyph in tools/icons/icon-set.mjs + regenerieren).
 */
object AppIcons {
    val Close: ImageVector get() = LucideIcons.X
    val Back: ImageVector get() = LucideIcons.ArrowLeft
    val Forward: ImageVector get() = LucideIcons.ArrowRight
    val Check: ImageVector get() = LucideIcons.Check
    val Plus: ImageVector get() = LucideIcons.Plus
    val Minus: ImageVector get() = LucideIcons.Minus
    val ChevronRight: ImageVector get() = LucideIcons.ChevronRight
    val ChevronDown: ImageVector get() = LucideIcons.ChevronDown
    val ChevronUp: ImageVector get() = LucideIcons.ChevronUp
    val Search: ImageVector get() = LucideIcons.Search
    val Refresh: ImageVector get() = LucideIcons.RefreshCw
    val Edit: ImageVector get() = LucideIcons.SquarePen
    val Settings: ImageVector get() = LucideIcons.Settings
    val Delete: ImageVector get() = LucideIcons.Trash2
    val Download: ImageVector get() = LucideIcons.CloudDownload
    val Local: ImageVector get() = LucideIcons.HardDriveDownload
    val Cloud: ImageVector get() = LucideIcons.Cloud
    val Info: ImageVector get() = LucideIcons.Info
    val Filter: ImageVector get() = LucideIcons.ListFilter
    val Overflow: ImageVector get() = LucideIcons.EllipsisVertical
    val Stop: ImageVector get() = LucideIcons.CircleStop
    val GridView: ImageVector get() = LucideIcons.LayoutGrid
    val ListView: ImageVector get() = LucideIcons.List
    val Bookmark: ImageVector get() = LucideIcons.Bookmark
    val Library: ImageVector get() = LucideIcons.Library
    val Groups: ImageVector get() = LucideIcons.LayoutDashboard
    val Plugins: ImageVector get() = LucideIcons.Puzzle
    val Contrast: ImageVector get() = LucideIcons.Contrast
    val Palette: ImageVector get() = LucideIcons.Palette
    val Reader: ImageVector get() = LucideIcons.BookOpen
    val Language: ImageVector get() = LucideIcons.Languages
    val Connection: ImageVector get() = LucideIcons.Server
    val ReaderMode: ImageVector get() = LucideIcons.GalleryVertical
    val PanelMode: ImageVector get() = LucideIcons.Grid2x2
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/kotlin/com/komgareader/app/ui/icons/AppIcons.kt
git commit -m "feat(icons): AppIcons-SSOT (semantisch → Lucide)"
```

---

### Task 5: Akku-Icon entfernen (StatusCluster)

**Files:**
- Modify: `app/src/main/kotlin/com/komgareader/app/ui/components/StatusCluster.kt:65,73`

- [ ] **Step 1: Akku-`Icon`-Aufruf + Import entfernen, nur `%`-Text behalten**

Lies die Datei, entferne den `Icon(Icons.Outlined.BatteryStd, …)`-Block und den `import androidx.compose.material.icons.outlined.BatteryStd` (+ `Icons`-Import, falls dort nicht mehr genutzt). Layout so anpassen, dass nur der Prozent-`Text` bleibt (kein leerer Row-Slot/Spacing-Rest).

- [ ] **Step 2: Commit** (Build folgt in Task 7)

```bash
git add app/src/main/kotlin/com/komgareader/app/ui/components/StatusCluster.kt
git commit -m "refactor(status): Akku-Icon entfernt, nur Prozent bleibt"
```

---

### Task 6: Aufruf-Stellen umstellen (Icons.* → AppIcons.*)

Mechanischer Tausch nach Mapping-Tabelle. Pro Datei: `androidx.compose.material.icons.*`-Imports raus, `import com.komgareader.app.ui.icons.AppIcons` rein, jede `Icons.X.Y` → passendes `AppIcons.Z`. **Filled/Outlined-Mix** löst sich auf, da `AppIcons` nur eine (outline) Variante hat. Toggle-Stellen behalten ihre Logik (gleiches Icon, nur Glyph getauscht), nur `contentDescription` bleibt.

**Files (alle `app/src/main/kotlin/com/komgareader/app/ui/`):**
- `components/EinkModal.kt` — Close→`AppIcons.Close`
- `components/EinkComponents.kt` — KeyboardArrowRight→`ChevronRight`, Check→`Check`, Remove→`Minus`, Add→`Plus`, ArrowBack→`Back`
- `components/EinkSearchBar.kt` — Close→`Close`, Search→`Search`
- `components/StatusCluster.kt` — (in Task 5 erledigt)
- `home/HomeScreen.kt` — LibraryBooks→`Library`, Dashboard→`Groups`, Extension→`Plugins`, Settings→`Settings`, FilterList→`Filter`, Sync→`Refresh`, Add→`Plus`, Close→`Close`
- `library/LibraryScreen.kt` — DownloadDone→`Local`, CloudQueue→`Cloud`
- `groups/GroupsScreen.kt` — Settings→`Edit`, Delete→`Delete`
- `groups/GroupBrowseRoute.kt` — ArrowBack→`Back`, Refresh→`Refresh`, DownloadDone→`Local`, CloudQueue→`Cloud`
- `plugins/PluginsScreen.kt` — Extension→`Plugins`
- `settings/SettingsScreen.kt` — KeyboardArrowDown/KeyboardArrowRight→`ChevronDown`/`ChevronRight`
- `settings/SettingsSections.kt` — Cloud→`Connection`, Contrast→`Contrast`, Palette→`Palette`, ChromeReaderMode→`Reader`, Download→`Download`, Language→`Language`, Info→`Info`
- `settings/ColorFilterSettingsContent.kt` — ArrowBack→`Back`, ArrowForward→`Forward`, Info→`Info`, ExpandLess/ExpandMore→`ChevronUp`/`ChevronDown`, Add→`Plus`, Check→`Check`, Settings→`Edit`, Remove→`Minus`
- `series/SeriesDetailScreen.kt` — ArrowBack→`Back`, GridView→`GridView`, ViewList→`ListView`, MoreVert→`Overflow`, Stop→`Stop`, Delete→`Delete`, CloudDownload→`Download`, Check→`Check`, Bookmark→`Bookmark`, Info→`Info`
- `series/` TypeFilterMenu.kt — Check→`Check`
- `reader/ReaderChrome.kt` — ArrowBack→`Back`
- `reader/PagedReaderScreen.kt` — ViewDay→`ReaderMode`
- `reader/WebtoonReaderScreen.kt` — ViewDay→`ReaderMode`
- `reader/ComicReaderScreen.kt` — GridView→`PanelMode`, ViewDay→`ReaderMode`

- [ ] **Step 1:** Pro Datei Imports + Referenzen tauschen (s.o.).
- [ ] **Step 2: Verifizieren, dass keine Material-Icon-Referenz mehr existiert**

Run: `grep -rn "androidx.compose.material.icons\|Icons\." app/src/main/kotlin/`
Expected: **leer** (keine Treffer).

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/komgareader/app/ui/
git commit -m "refactor(ui): alle Icons auf AppIcons-Registry umgestellt"
```

---

### Task 7: Dependency entfernen, NOTICE, Build

**Files:**
- Modify: `app/build.gradle.kts`, `gradle/libs.versions.toml`, `NOTICE`

- [ ] **Step 1:** In `gradle/libs.versions.toml` die Zeile `compose-material-icons-extended = …` entfernen; in `app/build.gradle.kts` die zugehörige `implementation(libs.compose.material.icons.extended)`-Zeile entfernen.
- [ ] **Step 2:** `NOTICE` — Zeile „Material Symbols (Icons) — Apache-2.0, Google." ersetzen durch:

```
- Lucide (Icons) — ISC, Lucide Contributors.
  https://github.com/lucide-icons/lucide — ImageVectors generiert via tools/icons, Stroke E-Ink-getunt.
```

- [ ] **Step 3: Build**

Run: `./gradlew :app:assembleDebug 2>&1 | tail -20`
Expected: `BUILD SUCCESSFUL`. Bei Compile-Fehlern: übersehene `Icons.`-Referenz → fixen.

- [ ] **Step 4: Commit**

```bash
git add app/build.gradle.kts gradle/libs.versions.toml NOTICE
git commit -m "build(icons): material-icons-extended entfernt, NOTICE auf Lucide"
```

---

### Task 8: E2E-Screenshot-Verifikation (E-Ink)

**Files:**
- Nutzen: `eink_test`-Emulator (1264×1680@300), `tools/e2e/`

- [ ] **Step 1:** Emulator `eink_test` starten, Debug-APK installieren.
- [ ] **Step 2:** Screenshots der Schlüssel-Screens (Bibliothek-BottomBar, Settings-Sektionsicons, SeriesDetail-Aktionsleiste, Reader-Chrome) ziehen.
- [ ] **Step 3:** Visuell prüfen: Lucide-Glyphen sichtbar, Stroke kräftig genug auf E-Ink, kein Akku-Icon im StatusCluster (nur `%`), keine fehlenden/leeren Icons.
- [ ] **Step 4:** Screenshots in `docs/superpowers/plans/artifacts/` ablegen, Commit.

```bash
git add docs/superpowers/plans/artifacts/
git commit -m "test(icons): E2E-Screenshots Lucide-Icons auf eink_test"
```

---

## Self-Review

- **Spec-Coverage:** Material→Lucide (Tasks 2–4,6), Stroke-Tuning (Task 3 `STROKE`), SSOT (Task 4), bessere Icons (Mapping-Tabelle, fett), Akku-Icon raus (Task 5), Dependency/NOTICE (Task 7), TDD-Generator (Task 2), E2E (Task 8). ✓
- **Type-Konsistenz:** `LucideIcons.<Prop>` (PascalCase aus `icon-set.mjs`) == `AppIcons`-Referenzen == Generator-Output. `STROKE` einmal definiert. ✓
- **Platzhalter:** keine — Konverter, Generator, Registry, Mapping vollständig als Code. ✓
- **Risiko:** `grid-2x2`/`circle-stop`/`square-pen`/`list-filter` SVG-Dateinamen in `lucide-static` verifizieren (Step Task 3 schlägt sonst mit ENOENT fehl → Name in `icon-set.mjs` korrigieren).
