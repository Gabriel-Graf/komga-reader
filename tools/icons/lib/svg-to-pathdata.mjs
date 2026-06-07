// Pure SVG-Primitive → Path-`d`. Lucide nutzt nur path/line/polyline/polygon/rect/circle.
// Stroke-Stil ist über alle Kinder gleich → ein konkatenierter d-String pro Icon genügt.
import svgpath from "svgpath";

const num = (v, d = 0) => (v === undefined ? d : parseFloat(v));

export function lineToPath(a) {
  return `M${num(a.x1)} ${num(a.y1)}L${num(a.x2)} ${num(a.y2)}`;
}

export function polyToPath(a, closed) {
  // Lucide-Punkte sind flach (space- oder komma-getrennt): "x y x y" bzw. "x,y x,y".
  const flat = a.points.trim().split(/[\s,]+/).map(Number);
  const pts = [];
  for (let i = 0; i + 1 < flat.length; i += 2) pts.push([flat[i], flat[i + 1]]);
  const [head, ...rest] = pts;
  return (
    `M${head[0]} ${head[1]}` +
    rest.map((p) => `L${p[0]} ${p[1]}`).join("") +
    (closed ? "Z" : "")
  );
}

export function rectToPath(a) {
  const x = num(a.x), y = num(a.y), w = num(a.width), h = num(a.height);
  const rx = num(a.rx, num(a.ry, 0));
  const ry = num(a.ry, rx);
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

function parseAttrs(tag) {
  const o = {};
  for (const m of tag.matchAll(/([a-zA-Z0-9_-]+)="([^"]*)"/g)) o[m[1]] = m[2];
  return o;
}

export function svgToPathData(svg) {
  const parts = [];
  for (const m of svg.matchAll(
    /<(path|line|polyline|polygon|rect|circle|ellipse)\b([^>]*?)\/?>/g,
  )) {
    const tag = m[1];
    const a = parseAttrs(m[2]);
    // path-d zu ABSOLUT normalisieren: beim Verketten mehrerer Subpfade darf ein
    // relatives Start-m nicht relativ ans Ende des Vorgänger-Subpfads laufen.
    if (tag === "path") parts.push(svgpath(a.d.trim()).abs().toString());
    else if (tag === "line") parts.push(lineToPath(a));
    else if (tag === "polyline") parts.push(polyToPath(a, false));
    else if (tag === "polygon") parts.push(polyToPath(a, true));
    else if (tag === "rect") parts.push(rectToPath(a));
    else if (tag === "circle") parts.push(circleToPath(a));
    else if (tag === "ellipse") parts.push(ellipseToPath(a));
  }
  return parts.join(" ");
}
