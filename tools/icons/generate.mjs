import { readFileSync, writeFileSync, mkdirSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";
import { ICONS, FILLED } from "./icon-set.mjs";
import { svgToPathData } from "./lib/svg-to-pathdata.mjs";

const here = dirname(fileURLToPath(import.meta.url));
const svgDir = join(here, "node_modules", "lucide-static", "icons");
const outFile = join(
  here, "..", "..", "ui-api", "src", "main", "kotlin",
  "com", "komgareader", "ui", "icons", "LucideIcons.kt",
);

const entries = Object.entries(ICONS).map(([kebab, prop]) => {
  const svg = readFileSync(join(svgDir, `${kebab}.svg`), "utf8");
  const d = svgToPathData(svg).replace(/\\/g, "\\\\").replace(/"/g, '\\"');
  if (!d) throw new Error(`Leerer Pfad für ${kebab}`);
  return { prop, d };
});

const filledEntries = Object.entries(FILLED).map(([kebab, prop]) => {
  const svg = readFileSync(join(svgDir, `${kebab}.svg`), "utf8");
  const d = svgToPathData(svg).replace(/\\/g, "\\\\").replace(/"/g, '\\"');
  if (!d) throw new Error(`Leerer Pfad für ${kebab}`);
  return { prop, d };
});

const body = [...entries, ...filledEntries.map((e) => ({ ...e, filled: true }))]
  .map(({ prop, d, filled }) =>
    `    val ${prop}: ImageVector by lazy { ${filled ? "lucideFilled" : "lucide"}("${prop}", "${d}") }`,
  )
  .join("\n");

const out = `// GENERIERT von tools/icons/generate.mjs — NICHT von Hand editieren.
// Quelle: Lucide (ISC), https://github.com/lucide-icons/lucide  ·  Stroke: siehe STROKE.
package com.komgareader.ui.icons

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

    /** Gefüllte Variante (fill statt stroke) — als Aktiv-Zustand desselben Outline-Glyphs. */
    private fun lucideFilled(name: String, pathData: String): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).addPath(
            pathData = PathParser().parsePathString(pathData).toNodes(),
            fill = SolidColor(Color.Black),
        ).build()

${body}
}
`;

mkdirSync(dirname(outFile), { recursive: true });
writeFileSync(outFile, out);
console.log(`${entries.length} Icons → ${outFile}`);
