/**
 * Architecture diagrams (Excalidraw) for the Komga E-Ink Reader.
 *
 * Box set + sizes derived from the understand graph (.understand-anything/knowledge-graph.json,
 * layer file counts + complexity); ARROWS derived from the Gradle module DAG + the documented
 * seams (the graph's Kotlin edges are too sparse — see docs/PROJECT-STATUS.md).
 *
 * Run:    node docs/architektur/build-diagrams.mjs
 * Then:   node <skill>/assets/validate-diagrams.mjs docs/architektur
 *
 * Do NOT change the HELPERS (proven, produce overflow-free Excalidraw). Only the build() specs.
 */
import { writeFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

const OUT = dirname(fileURLToPath(import.meta.url));

const C = {
  core: ['#2f9e44', '#b2f2bb'], port: ['#f08c00', '#ffec99'], driven: ['#e8590c', '#ffd8a8'],
  ui: ['#1971c2', '#a5d8ff'], store: ['#7048e8', '#d0bfff'], transport: ['#c2255c', '#fcc2d7'],
  ext: ['#0c8599', '#99e9f2'], neutral: ['#868e96', '#e9ecef'], group: ['#adb5bd', '#f8f9fa'],
};
let nonce = 1; const seed = () => Math.floor(Math.random() * 2 ** 31); const nn = () => nonce++;

function rect(id, x, y, w, h, palette, opts = {}) {
  const [stroke, bg] = palette;
  return { id, type: 'rectangle', x, y, width: w, height: h, angle: 0, strokeColor: stroke,
    backgroundColor: bg, fillStyle: 'solid', strokeWidth: opts.strokeWidth ?? 2,
    strokeStyle: opts.strokeStyle ?? 'solid', roughness: opts.roughness ?? 1, opacity: opts.opacity ?? 100,
    groupIds: [], frameId: null, roundness: { type: 3 }, seed: seed(), version: 1, versionNonce: nn(),
    isDeleted: false, boundElements: [], updated: 1, link: null, locked: false };
}
function boundText(containerId, x, y, w, h, text, palette, fontSize) {
  const lines = text.split('\n').length;
  return { id: 't_' + containerId, type: 'text', x: x + 6, y: y + h / 2 - (fontSize * 1.25 * lines) / 2,
    width: w - 12, height: fontSize * 1.25 * lines, angle: 0, strokeColor: palette[0],
    backgroundColor: 'transparent', fillStyle: 'solid', strokeWidth: 2, strokeStyle: 'solid', roughness: 1,
    opacity: 100, groupIds: [], frameId: null, roundness: null, seed: seed(), version: 1, versionNonce: nn(),
    isDeleted: false, boundElements: [], updated: 1, link: null, locked: false, fontSize, fontFamily: 2,
    textAlign: 'center', verticalAlign: 'middle', containerId, originalText: text, text, lineHeight: 1.25, baseline: fontSize };
}
function freeText(x, y, text, size = 16, color = '#495057', align = 'left') {
  const lines = text.split('\n'); const w = Math.max(...lines.map((l) => l.length)) * size * 0.55;
  return { id: 'ft' + nn(), type: 'text', x, y, width: w, height: size * 1.25 * lines.length, angle: 0,
    strokeColor: color, backgroundColor: 'transparent', fillStyle: 'solid', strokeWidth: 2, strokeStyle: 'solid',
    roughness: 1, opacity: 100, groupIds: [], frameId: null, roundness: null, seed: seed(), version: 1,
    versionNonce: nn(), isDeleted: false, boundElements: [], updated: 1, link: null, locked: false, fontSize: size,
    fontFamily: 2, textAlign: align, verticalAlign: 'top', containerId: null, originalText: text, text, lineHeight: 1.25, baseline: size };
}
function centerText(cx, y, text, size = 16, color = '#495057') {
  const t = freeText(0, y, text, size, color, 'center'); t.x = Math.round(cx - t.width / 2); return t;
}
function hexagon(x, y, w, h, palette) {
  const pts = [[0, h / 2], [w * 0.25, 0], [w * 0.75, 0], [w, h / 2], [w * 0.75, h], [w * 0.25, h], [0, h / 2]];
  return { id: 'hex' + nn(), type: 'line', x, y, width: w, height: h, angle: 0, strokeColor: palette[0],
    backgroundColor: palette[1], fillStyle: 'solid', strokeWidth: 3, strokeStyle: 'solid', roughness: 1, opacity: 100,
    groupIds: [], frameId: null, roundness: null, seed: seed(), version: 1, versionNonce: nn(), isDeleted: false,
    boundElements: [], updated: 1, link: null, locked: false, points: pts, lastCommittedPoint: null,
    startBinding: null, endBinding: null, startArrowhead: null, endArrowhead: null };
}
function connect(from, to, opts = {}) {
  const color = opts.color ?? '#343a40'; const dashed = opts.dashed;
  const fcx = from.x + from.width / 2, fcy = from.y + from.height / 2;
  const tcx = to.x + to.width / 2, tcy = to.y + to.height / 2;
  const dx = tcx - fcx, dy = tcy - fcy; let sx, sy, ex, ey, pts;
  const horizontalFirst = opts.dir ? opts.dir === 'h' : Math.abs(dx) >= Math.abs(dy);
  if (horizontalFirst) {
    const r = dx >= 0; sx = r ? from.x + from.width : from.x; sy = fcy; ex = r ? to.x : to.x + to.width; ey = tcy;
    const mid = opts.midX ?? (sx + ex) / 2;
    pts = Math.abs(sy - ey) < 2 ? [[0, 0], [ex - sx, 0]] : [[0, 0], [mid - sx, 0], [mid - sx, ey - sy], [ex - sx, ey - sy]];
  } else {
    const d = dy >= 0; sy = d ? from.y + from.height : from.y; sx = fcx; ey = d ? to.y : to.y + to.height; ex = tcx;
    const mid = opts.midY ?? (sy + ey) / 2;
    pts = Math.abs(sx - ex) < 2 ? [[0, 0], [0, ey - sy]] : [[0, 0], [0, mid - sy], [ex - sx, mid - sy], [ex - sx, ey - sy]];
  }
  return { id: 'a' + nn(), type: 'arrow', x: sx, y: sy, width: ex - sx, height: ey - sy, angle: 0, strokeColor: color,
    backgroundColor: 'transparent', fillStyle: 'solid', strokeWidth: 2, strokeStyle: dashed ? 'dashed' : 'solid',
    roughness: 1, opacity: 100, groupIds: [], frameId: null, roundness: null, seed: seed(), version: 1, versionNonce: nn(),
    isDeleted: false, boundElements: [], updated: 1, link: null, locked: false, points: pts, lastCommittedPoint: null,
    startBinding: null, endBinding: null, startArrowhead: null, endArrowhead: 'arrow' };
}
function build(name, title, subtitle, fn) {
  nonce = 1; const elements = []; const boxes = {};
  const add = (...e) => e.forEach((x) => elements.push(x));
  const N = (id, x, y, w, h, palette, text, fs = 15) => {
    const r = rect(id, x, y, w, h, palette); const t = boundText(id, x, y, w, h, text, palette, fs);
    r.boundElements.push({ type: 'text', id: t.id }); boxes[id] = r; add(r, t); return r;
  };
  const hex = (x, y, w, h, palette) => { const e = hexagon(x, y, w, h, palette); add(e); return e; };
  const A = (from, to, opts) => { const e = connect(from, to, opts); add(e); return e; };
  add(freeText(40, 22, title, 28, '#1e1e1e'));
  if (subtitle) add(freeText(40, 62, subtitle, 15, '#868e96'));
  fn({ N, hex, add, A, freeText, centerText, boxes, C });
  const graph = { type: 'excalidraw', version: 2, source: 'understand-anything + handcrafted', elements,
    appState: { viewBackgroundColor: '#ffffff', gridSize: null }, files: {} };
  writeFileSync(join(OUT, name), JSON.stringify(graph, null, 2));
  console.log(`✓ ${name}  (${elements.filter((e) => e.type === 'rectangle').length} boxes, ${elements.filter((e) => e.type === 'arrow').length} arrows)`);
}

// ════════════════════ 01 — OVERVIEW: the two seams ════════════════════
build('01-overview.excalidraw',
  'Komga E-Ink Reader — Architecture (Overview)',
  'Everything above the two seams is source- & device-agnostic · the pure Domain Core defines the contracts · adapters implement them', (g) => {
  const { N, hex, A, add, centerText, freeText } = g;
  add(freeText(120, 96, 'DRIVING', 13, '#1971c2'));
  const uiapi = N('uiapi', 120, 120, 360, 72, C.ui, 'UI modularity (ui-api)\nTheme · Shell · 8 region slots', 13);
  const app = N('app', 560, 120, 560, 72, C.ui, 'App Shell\nCompose UI · ViewModels · DI · Reader host', 15);
  A(uiapi, app, { color: '#1971c2' });
  const core = hex(560, 290, 400, 210, C.core);
  add(centerText(760, 350, 'Domain Core', 19, '#2f9e44'));
  add(centerText(760, 388, 'pure Kotlin · models · use-cases', 12, '#1e1e1e'));
  add(centerText(760, 420, 'Repo / Render / Eink — INTERFACES', 12, '#1e1e1e'));
  add(centerText(760, 448, 'ViewerType', 12, '#1e1e1e'));
  const seamA = N('seamA', 120, 300, 330, 150, C.port, 'Seam A — Sources\nMediaSource /\nBrowsableSource /\nSyncingSource', 14);
  const seamB = N('seamB', 1080, 300, 330, 150, C.port, 'Seam B — Render & E-Ink\nDocument /\nDocumentFactory /\nEinkController', 14);
  A(app, seamA, { dir: 'v', color: '#1971c2' });
  A(app, core, { dir: 'v', color: '#1971c2' });
  A(app, seamB, { dir: 'v', color: '#1971c2' });
  A(core, seamA, { dir: 'h', dashed: true, color: '#2f9e44' });
  A(core, seamB, { dir: 'h', dashed: true, color: '#2f9e44' });
  add(freeText(470, 268, 'defines ◁', 11, '#2f9e44'));
  add(freeText(968, 268, '▷ defines', 11, '#2f9e44'));
  add(freeText(40, 536, 'DRIVEN ADAPTERS', 13, '#e8590c'));
  const src = N('src', 40, 560, 440, 80, C.driven, 'KomgaSource · OpdsSource\nPlugin sources · StubSource', 13);
  const data = N('data', 540, 560, 360, 80, C.store, 'Data & Persistence\nRoom · sync queue · downloads', 12);
  const render = N('render', 960, 560, 215, 80, C.driven, 'MuPDF (render-core)\ncrengine (render-crengine)', 11);
  const eink = N('eink', 1195, 560, 215, 80, C.driven, 'Onyx E-Ink ctrl\n(eink-onyx) + Refresh', 11);
  A(src, seamA, { dir: 'v', color: '#e8590c' });
  A(render, seamB, { dir: 'v', color: '#e8590c' });
  A(eink, seamB, { dir: 'v', color: '#e8590c' });
  A(data, core, { dir: 'v', color: '#7048e8' });
  const plug = N('plug', 40, 720, 440, 64, C.ext, 'Plugin system — plugin-api (ABI) · host (TOFU) · sdk', 12);
  N('guided', 540, 720, 360, 64, C.neutral, 'Guided-View — panel cutter (XY-cut, pure)', 12);
  A(plug, src, { dir: 'v', color: '#0c8599' });
  add(freeText(940, 726, '→ feeds the Comic viewer (detail ②)', 12, '#868e96'));
  add(freeText(960, 752, 'each adapter = a new impl BEHIND the seam,', 12, '#868e96'));
  add(freeText(960, 772, 'never a core change.', 12, '#868e96'));
});

// ════════════════════ 02 — VIEWER SEAM + COMIC CUTTER ════════════════════
build('02-viewer-comic-cutter.excalidraw',
  'Reader / Viewer seam  +  Comic Cutter (guided-view)',
  'ReaderRoute dispatches 4 readers over ONE Viewer contract & ReaderScaffold · the Comic reader is fed by the XY-cut panel cutter', (g) => {
  const { N, A, add, freeText } = g;
  const rr = N('rr', 330, 110, 440, 64, C.ui, 'ReaderRoute — dispatch\nwhen(ViewerMode / ReaderContent)', 14);
  const paged = N('paged', 40, 250, 240, 66, C.ui, 'PagedReaderScreen', 13);
  const web = N('web', 310, 250, 240, 66, C.ui, 'WebtoonReaderScreen', 12);
  const novel = N('novel', 580, 250, 240, 66, C.ui, 'NovelReaderScreen', 13);
  const comic = N('comic', 1080, 250, 320, 66, C.core, 'ComicReaderScreen ★\n(guided)', 13);
  A(rr, paged, { dir: 'v' }); A(rr, web, { dir: 'v' }); A(rr, novel, { dir: 'v' }); A(rr, comic, { dir: 'v' });
  add(freeText(580, 332, '+ EpubReaderScreen (MuPDF download path)', 12, '#868e96'));
  const vk = N('vk', 40, 420, 240, 80, C.store, 'Viewer contract\nchromeVisible · toggle\nnavigateTo · onPageSettled', 11);
  const scaf = N('scaf', 310, 420, 240, 80, C.store, 'ReaderScaffold\nshared chrome\ntap zones (ReaderTapZones)', 11);
  const refr = N('refr', 580, 420, 240, 80, C.store, 'RefreshScheduler\n1×/session\n(deviceManaged)', 11);
  A(paged, vk, { dir: 'v', color: '#7048e8' });
  A(web, scaf, { dir: 'v', color: '#7048e8' });
  A(novel, refr, { dir: 'v', color: '#7048e8' });
  A(comic, refr, { dir: 'v', color: '#7048e8' });
  add(freeText(40, 516, 'all readers implement the Viewer contract and render in the shared ReaderScaffold (shared-structure-before-variants)', 12, '#868e96'));
  add(freeText(1080, 360, 'COMIC CUTTER — guided-view (pure, XY-cut)  ★', 13, '#0c8599'));
  const cutD = N('cutD', 1080, 388, 320, 60, C.ext, 'PanelDetector ★ (XY-cut)', 13);
  const cutB = N('cutB', 1080, 478, 320, 56, C.neutral, 'ImageBinarization · BorderLineSplit', 11);
  const cutN = N('cutN', 1080, 568, 320, 56, C.neutral, 'GuidedNavigator (panel order)', 11);
  const cutG = N('cutG', 1080, 658, 320, 56, C.neutral, 'PanelGeometry → ComicReaderViewModel', 11);
  A(comic, cutD, { dir: 'v', color: '#0c8599' });
  A(cutD, cutB, { dir: 'v', color: '#868e96' });
  A(cutB, cutN, { dir: 'v', color: '#868e96' });
  A(cutN, cutG, { dir: 'v', color: '#868e96' });
  const nb = N('nb', 40, 600, 520, 64, C.driven, 'Seam B — Document→Bitmap (MuPDF/crengine) · EinkController + OnyxRefresher', 10);
  A(nb, refr, { dir: 'v', color: '#e8590c' });
  add(freeText(1080, 726, 'Input: page bitmap from Seam B', 12, '#868e96'));
});

// ════════════════════ 03 — SEAM A: SOURCES ════════════════════
build('03-seam-a-sources.excalidraw',
  'Seam A — Sources (source-agnostic)',
  'ViewModels resolve per work via ActiveSource(sourceId) · concrete source types live ONLY in the wiring layer', (g) => {
  const { N, A, add, freeText } = g;
  const vm = N('vm', 300, 110, 720, 64, C.ui, 'App ViewModels — source-agnostic\nresolve per work via ActiveSource.get(sourceId) / all()', 13);
  const active = N('active', 40, 270, 300, 96, C.store, 'ActiveSource / SourceManager\nagnostic resolver + registry\nStubSource when a source is missing', 11);
  const contract = N('contract', 440, 270, 580, 96, C.port, 'Seam A — contract\nMediaSource · BrowsableSource\n(openPage · coverBytes · downloadFile · seriesIdOf) · SyncingSource', 11);
  A(vm, active, { dir: 'v', color: '#1971c2' });
  A(vm, contract, { dir: 'v', color: '#1971c2' });
  A(active, contract, { dir: 'h', color: '#7048e8' });
  add(freeText(40, 432, 'ADAPTERS (implement MediaSource)', 13, '#e8590c'));
  const komga = N('komga', 40, 460, 300, 76, C.driven, 'KomgaSource (REST)', 12);
  const opds = N('opds', 370, 460, 240, 76, C.driven, 'OpdsSource (Basic-Auth)', 12);
  const plug = N('plug', 640, 460, 300, 76, C.ext, 'Plugin sources (APK)\ne.g. Kavita', 11);
  const stub = N('stub', 970, 460, 240, 76, C.neutral, 'StubSource\n(fallback)', 12);
  [komga, opds, plug, stub].forEach((b) => A(b, contract, { dir: 'v', color: '#e8590c' }));
  add(freeText(40, 612, 'WIRING LAYER & IMAGE PATH (below the adapters)', 12, '#868e96'));
  N('reg', 40, 640, 560, 70, C.neutral, 'SourceRegistration (wiring)\nKomga/OPDS/Plugin types ONLY here · sourceId threaded through navigation', 10);
  N('fetch', 660, 640, 460, 70, C.transport, 'Coil: SourcePageFetcher / SourceCoverFetcher\n→ openPage / coverBytes (no URL/auth in the UI)', 10);
});

// ════════════════════ 04 — SEAM B: RENDER & E-INK ════════════════════
build('04-seam-b-render-eink.excalidraw',
  'Seam B — Render & E-Ink (device-agnostic)',
  'Document renders to a bitmap · EinkController encapsulates the device (HW-gated, never crashes off-device)', (g) => {
  const { N, A, add, freeText } = g;
  const rd = N('rd', 360, 110, 560, 64, C.ui, 'Reader screens · ReaderViewModel\n(consume Seam B only via contracts)', 13);
  const docp = N('docp', 60, 270, 500, 96, C.port, 'Render seam\nDocument / DocumentFactory\nReflowableDocument / ReflowableDocumentFactory', 11);
  const einkp = N('einkp', 760, 270, 540, 96, C.port, 'Device seam\nEinkController\nEinkCapabilities (hasEink · canColor · canInvert)', 11);
  A(rd, docp, { dir: 'v', color: '#1971c2' });
  A(rd, einkp, { dir: 'v', color: '#1971c2' });
  add(freeText(60, 432, 'RENDER ENGINES', 13, '#e8590c'));
  const mupdf = N('mupdf', 60, 460, 300, 80, C.driven, 'MupdfDocument (render-core)\nMuPDF JNI → Bitmap', 11);
  const cren = N('cren', 380, 460, 300, 80, C.driven, 'CrengineDocumentFactory\n(render-crengine, JNI arm64)', 10);
  add(freeText(760, 432, 'DEVICE IMPLS', 13, '#e8590c'));
  const onyx = N('onyx', 760, 460, 300, 80, C.driven, 'OnyxEinkController\n(Boox SDK, HW-gated)', 11);
  const noop = N('noop', 1080, 460, 220, 80, C.neutral, 'NoOpEinkController\n(fallback off-device)', 10);
  A(mupdf, docp, { dir: 'v', color: '#e8590c' });
  A(cren, docp, { dir: 'v', color: '#e8590c' });
  A(onyx, einkp, { dir: 'v', color: '#e8590c' });
  A(noop, einkp, { dir: 'v', color: '#e8590c' });
  const refr = N('refr', 760, 640, 540, 70, C.transport, 'RefreshScheduler (pure decision) + OnyxRefresher (execution)\npartial while paging · FULL against ghosting · deviceManaged (default)', 10);
  A(refr, onyx, { dir: 'v', color: '#c2255c' });
});

// ════════════════════ 05 — PLUGIN SYSTEM ════════════════════
build('05-plugin-system.excalidraw',
  'Plugin System (runtime loader, Mihon model)',
  'OS-installed APKs · ABI gate (2 ints) · TOFU signature pin · PathClassLoader · re-exports the Seam-A types', (g) => {
  const { N, A, add, freeText } = g;
  const api = N('api', 320, 110, 640, 96, C.ext, 'plugin-api — ABI contract\nSourcePlugin · ConfigSchema · PluginAbi (VERSION=2 / MIN=1)\nPluginCategory { COLOR_PRESET · READER_PRESET · LANGUAGE · UI_PACK }', 11);
  N('sdk', 1000, 110, 400, 96, C.ext, 'plugin-sdk (shaded)\nplugin-api + source-api + domain\ncompileOnly for external plugins', 10);
  const host = N('host', 320, 300, 640, 96, C.ext, 'plugin-host — runtime loader\nPluginHost · AbiGate · PluginSignature (TOFU pin)\nPathClassLoader · discoverDataPlugins(category)', 10);
  A(api, host, { dir: 'v', color: '#0c8599' });
  add(freeText(1000, 214, 'Plugins link ONLY the SDK', 12, '#868e96'));
  add(freeText(1000, 236, 'compileOnly (no duplicate classes)', 12, '#868e96'));
  const apk = N('apk', 40, 480, 420, 80, C.driven, 'APK plugins\nKavita source (code) · data-only packs\n(color/reader preset · language · UI pack)', 10);
  const reg = N('reg', 520, 480, 440, 80, C.neutral, 'SourceRegistration (PLUGIN branch)\nsourceFor(pkg, entry, sigPin, config)\n→ SourceManager (Seam A)', 10);
  A(apk, host, { dir: 'v', color: '#e8590c' });
  A(host, reg, { dir: 'v', color: '#868e96' });
  N('repo', 1000, 300, 400, 130, C.transport, 'KomgaReaderPlugins (repo)\nrepo.json index · repo browser\nfingerprint verify → PackageInstaller', 10);
  add(freeText(520, 576, 'Trust gate: cert SHA-256 pin (confirmed on first add). No pin → no load.', 12, '#868e96'));
  add(freeText(1000, 444, 'Install: download APK → verify fingerprint', 12, '#868e96'));
  add(freeText(1000, 466, 'against the index → only then the OS installer', 12, '#868e96'));
});

// ════════════════════ 06 — MODULAR UI (3 layers) ════════════════════
build('06-modular-ui.excalidraw',
  'Modular UI — Theme · Shell · Region Slots',
  'The host builds the capability surfaces; a pack only arranges them ("new UI, same core logic") · E-Ink invariants host-enforced', (g) => {
  const { N, A, add, freeText } = g;
  const host = N('host', 300, 110, 700, 64, C.ui, 'Host — KomgaReaderTheme · HomeShellHost · LocalResolvedSlots', 12);
  const theme = N('theme', 40, 250, 400, 100, C.ui, 'Theme pack (UiPack)\nMonoEink · Kaleido · Lcd · Aurora\npackFor(DisplayBehavior)', 11);
  const shell = N('shell', 470, 250, 460, 100, C.ui, 'Shell pack (DeclarativeShell)\nShellDescriptor · ShellNavStyle\n{ BOTTOM_BAR · DRAWER · FLOATING_NAV }\nresolveFormFactor(form factor)', 10);
  const slots = N('slots', 960, 250, 440, 100, C.ui, 'Region slots (8) — UiSlotPack / ResolvedSlots\nheader · homeHeader · dialog · settings\ntiles · overlay · detail · readerChrome', 10);
  A(host, theme, { dir: 'v', color: '#1971c2' });
  A(host, shell, { dir: 'v', color: '#1971c2' });
  A(host, slots, { dir: 'v', color: '#1971c2' });
  const ext = N('ext', 300, 420, 700, 80, C.ext, 'External data pack: ui_pack.json (category UI_PACK)\nUiPackSpec → parseUiPackSpec (data) → toUiPackOrNull (:app only)\ndeclarative: theme · shell.navStyle · icons (IconKey remap)', 10);
  A(ext, theme, { dir: 'v', color: '#0c8599' });
  A(ext, shell, { dir: 'v', color: '#0c8599' });
  A(ext, slots, { dir: 'v', color: '#0c8599' });
  N('inv', 300, 580, 700, 70, C.core, 'Host-enforced invariants: DisplayBehavior(allowsMotion · allowsAccentColor)\nLocalEinkMode · DesignTokens — motion/accent gated, whatever a pack requests', 10);
  add(freeText(40, 604, 'FOUNDATION ▷\n(gates everything)', 12, '#2f9e44'));
});

console.log('\nDone. Validate: node <skill>/assets/validate-diagrams.mjs docs/architektur');
