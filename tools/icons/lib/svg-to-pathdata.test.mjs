import { test } from "node:test";
import assert from "node:assert/strict";
import {
  lineToPath,
  rectToPath,
  circleToPath,
  ellipseToPath,
  polyToPath,
  svgToPathData,
} from "./svg-to-pathdata.mjs";

test("line → M..L (Attribut-Reihenfolge egal)", () => {
  assert.equal(lineToPath({ x1: "1", x2: "3", y1: "2", y2: "4" }), "M1 2L3 4");
});

test("polyline → offen (kein Z)", () => {
  assert.equal(polyToPath({ points: "1,2 3,4 5,6" }, false), "M1 2L3 4L5 6");
});

test("polygon → geschlossen (Z)", () => {
  assert.equal(polyToPath({ points: "1,2 3,4 5,6" }, true), "M1 2L3 4L5 6Z");
});

test("polyline mit space-separierten Punkten (Lucide-Format)", () => {
  assert.equal(polyToPath({ points: "7 10 12 15 17 10" }, false), "M7 10L12 15L17 10");
});

test("rect ohne rx → 4 Kanten + Z", () => {
  assert.equal(rectToPath({ x: "2", y: "3", width: "10", height: "6" }), "M2 3H12V9H2Z");
});

test("rect mit rx → abgerundet via Arcs", () => {
  const d = rectToPath({ x: "0", y: "0", width: "10", height: "10", rx: "2" });
  assert.match(d, /^M2 0H8A2 2 0 0 1 10 2/);
  assert.match(d, /Z$/);
});

test("circle → zwei Halbkreis-Arcs", () => {
  assert.equal(
    circleToPath({ cx: "12", cy: "12", r: "10" }),
    "M2 12A10 10 0 1 0 22 12A10 10 0 1 0 2 12",
  );
});

test("ellipse → zwei Halb-Arcs mit rx/ry", () => {
  assert.equal(
    ellipseToPath({ cx: "12", cy: "12", rx: "8", ry: "4" }),
    "M4 12A8 4 0 1 0 20 12A8 4 0 1 0 4 12",
  );
});

test("svgToPathData konkateniert alle Kinder (path-Stücke absolut normalisiert)", () => {
  const svg = '<svg viewBox="0 0 24 24"><path d="M4 4h2"/><line x1="1" y1="1" x2="2" y2="2"/></svg>';
  assert.equal(svgToPathData(svg), "M4 4H6 M1 1L2 2");
});

test("relativer Folge-Pfad (Lucide search) wird absolut — Griff bleibt im Viewport", () => {
  // circle + path mit relativem Start-m: beim Verketten darf m NICHT relativ ans
  // Kreis-Ende laufen, sonst landet der Griff außerhalb 24x24 und ist unsichtbar.
  const svg = '<svg><circle cx="11" cy="11" r="8" /><path d="m21 21-4.3-4.3" /></svg>';
  const d = svgToPathData(svg);
  assert.ok(d.includes("M21 21"), `Griff sollte absolut bei 21,21 starten: ${d}`);
  assert.ok(!/ m21 21/.test(d), `kein relatives m21 im Ergebnis: ${d}`);
});

test("svgToPathData mischt rect+circle+path (echte Lucide-Form)", () => {
  const svg =
    '<svg><circle cx="12" cy="12" r="10" /><rect x="9" y="9" width="6" height="6" rx="1" /></svg>';
  const d = svgToPathData(svg);
  assert.ok(d.startsWith("M2 12A10 10 0 1 0"));
  assert.ok(d.includes("M10 9H14A1 1 0 0 1 15 10"));
});
