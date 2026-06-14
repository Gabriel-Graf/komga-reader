# Architecture

This document is the English, code‑anchored overview of how Komga Reader is built. It
summarises the authoritative (and more detailed, German) design specs under
`docs/superpowers/specs/` and the rule files in `.claude/rules/`. The north‑star goal is
**maximum flexibility**: every axis of variation — source, device, reading mode, UI — sits
behind a seam so it can grow by *addition*, never by core rewrite.

> **Visual version:** [`docs/architektur/`](architektur/) has Excalidraw diagrams — an overview
> that drills into each critical subsystem (the two seams, the viewers + comic cutter, the plugin
> interface, the modular UI).

---

## 1. The big idea: two seams

```
┌─ UI (Jetpack Compose, :app) — source- & device-agnostic ───────────────┐
│   ViewModels · Reader screens · modular chrome (theme/shell/slot packs) │
└───────────────── ↓ ───────────────────────────────── ↓ ────────────────┘
┌─ :domain (pure Kotlin — no Android, no network, no source) ─────────────┐
│   Models · UseCases · Repository / Render / Eink INTERFACES · ViewerType │
└──────── ↓ SEAM A: Sources ──────────────────── ↓ SEAM B: Render/E-Ink ──┘
┌─ MediaSource ───────────────┐      ┌─ Document / EinkController ─────────┐
│  KomgaSource (REST)         │      │  MuPDF (JNI) → Bitmap   [render-core]│
│  OpdsSource                 │      │  crengine-ng reflow [render-crengine]│
│  Plugin sources (APK, TOFU) │      │  OnyxEinkController / NoOp [eink-onyx]│
│  SourceManager + StubSource │      │  RefreshScheduler (device-managed)   │
└─────────────────────────────┘      └──────────────────────────────────────┘
```

`domain` knows neither UI, nor data, nor any concrete source. It only defines **interfaces**.
The seams are where concrete implementations plug in.

---

## 2. Module dependency rules (enforced by Gradle)

- `domain` is **pure Kotlin** — its only main dependency is `kotlinx-coroutines-core`. No
  Android, no network, no source module. This makes domain logic trivially unit‑testable.
- `source-api` defines the Seam‑A contract and depends only on `domain`.
- `source-komga`, `source-opds` depend on `domain` + `source-api`, never on each other, never on
  `app`.
- `render-core`, `render-crengine`, `eink-onyx` depend only on `domain`. (The former `guided-view`
  module was removed; panel detection is now the external **comic-cutter** library, wired in `app`
  via `PanelSourceProvider`.)
- `ui-api` depends on `domain` + Compose — the DAG is `domain → ui-api → app`. It is the UI
  counterpart of `source-api`.
- `data` depends on `domain` (+ `plugin-api` for preset import).
- `app` is the imperative shell: it is the only module that wires everything together (DI,
  ViewModels, reader host, default packs). It depends on every module.

If a feature seems to need a new cross‑module import, first check whether it belongs **behind an
existing seam** instead.

---

## 3. Seam A — Sources

Contract: `source-api/.../source/MediaSource.kt`.

- Every backend connection implements `MediaSource` (+ `BrowsableSource` for reading,
  `SyncingSource` for progress sync). Each source has a stable, deterministic `id` (a hash of
  name / type / config).
- `StubSource` holds a title/ID when the real source is unavailable — the library never breaks.
- **The integration side is fully wired and agnostic:** `SourceManager` is populated in `app`
  from the active `ServerConfig` via `SourceRegistration`; `ActiveSource` (in `app/data`) is the
  agnostic resolver every ViewModel injects. Pages and covers flow through the seam via Coil
  fetchers calling `BrowsableSource.openPage` / `coverBytes` — there are **no** raw source URLs
  or auth headers in the UI.
- **Multiple sources at once, mixed.** `ActiveSource.all()` aggregates; `get(sourceId)` resolves
  exactly the source of one work. The `sourceId` is threaded through navigation
  (`series/{seriesId}/{sourceId}`, `reader/{bookId}/{sourceId}/…`) so every consumer resolves
  *per work*, not "the first/active" source. Komga REST and OPDS have been verified live and
  mixed.

Concrete source types (`KomgaSource`, `KomgaSourceProvider`, …) appear **only** in the
`app/data` wiring layer — never in a ViewModel, a UI file, or `domain`.

---

## 4. Seam B — Render & E‑Ink

### Render (`domain/render/Document.kt`)

- `Document` / `DocumentFactory` is the render seam. **MuPDF** (`MupdfDocument` in `render-core`,
  via JNI) renders cbz/cbr/pdf and EPUB to an `android.graphics.Bitmap`.
- For reflowable novels, `ReflowableDocument` / `ReflowableDocumentFactory` is implemented by
  **crengine‑ng** (`render-crengine`, JNI, arm64‑v8a). It reflows EPUB text with hyphenation
  (bundled DE/EN TeX patterns) and bundled reading fonts (DejaVu Sans, Literata, Bitter).
- **Runtime font registration:** `ReflowableDocumentFactory.registerFont(absolutePath): Boolean`
  (default no‑op) keeps `domain` engine‑free; the crengine impl buffers pre‑boot paths in
  `pendingFontPaths` (flushed into the single `nativeInit`) and registers live post‑boot via the
  JNI `CrengineNative.nativeAddFont` → `fontMan->RegisterFont`. Font plugins (see below) install
  TTFs at runtime, no app restart.
- The render target is strictly separated from the view. A different engine plugs in behind
  these interfaces without touching the rest.

### Device & E‑Ink (`domain/eink/EinkController.kt`)

- `EinkController` encapsulates the device: `OnyxEinkController` (Boox SDK, **hardware‑gated**
  via `Build.MANUFACTURER`) and `NoOpEinkController` as the off‑device fallback — development
  never crashes on non‑Boox hardware. It carries `EinkCapabilities` (hasEink / canColor /
  canInvert).
- The refresh **decision** (partial while paging, full promotion against ghosting) lives in the
  device‑independent, unit‑tested `RefreshScheduler`. By default the setting
  `deviceManagedRefresh` is on, so the Onyx device handles full refresh and the scheduler is a
  no‑op fallback. (`RefreshScheduler` is `@Deprecated` and slated for removal once the
  device‑managed path is the only one.)

### The `Viewer` contract & shared reader scaffold

`app/ui/reader/Viewer.kt` is a **Compose‑state** seam (a `chromeVisible` flow,
`toggleChrome` / `navigateTo` / `onPageSettled`, a shared `RefreshScheduler`) — not an OO
bind/teardown lifecycle (Compose manages that declaratively). All reader ViewModels implement it,
and the shared `ReaderScaffold` works against it. There is **one** `RefreshScheduler` per reader
session, shared by all readers.

Four reading modes (`ViewerType`: `PAGED`, `WEBTOON`, `NOVEL`, `COMIC`) are dispatched in
`ReaderRoute.kt`. The **guided comic** reader (`ComicReaderScreen` + `ComicReaderViewModel`) does
panel‑by‑panel zoom. Panel detection comes from the published **comic‑cutter** library
(`com.panela.comiccutter.*`) behind a `PanelSource` seam, chosen by `PanelSourceProvider`:
`GeometricPanelSource` by default, or an ONNX‑backed `MlPanelSource` when ML detection is enabled
(`useMlDetection`) and a `PANEL_MODEL` data‑plugin is installed. `ComicPageLoader` sorts the detected
panels into reading order (`ReadingOrder.sort`); the reader is agnostic to geometric‑vs‑ML.

---

## 5. Determining the reading mode

Viewer resolution is deterministic, not auto‑guessed:

```
Series.contentTypeOverride ?: Shelf.contentType  →  ViewerType
```

`ResolveViewerType` (a pure use‑case in `domain`) plus per‑book overrides apply the priority
rule. This keeps reading‑mode selection predictable across sources.

---

## 6. The three‑layer modular UI

The presentation is split so it can be re‑skinned or re‑arranged independently — eventually by
community packs — while the **host keeps enforcing the E‑Ink invariants** (motion / accent
gating) no pack can override. Three layers, each its own seam with a default + built‑in variants:

| Layer | What it swaps | Selected by | Status |
|---|---|---|---|
| **Theme pack** (`UiPack`) | colours, tokens, typography, shapes | device class (`DisplayBehavior`) | built: `MonoEinkPack`, `KaleidoPack`, `LcdPack`, `AuroraPack` |
| **Shell pack** (`AppShellState` / `DeclarativeShell` + `ShellDescriptor`) | the whole home layout skeleton: nav location, arrangement | **form factor** (screen size), orthogonal to device class | built: one descriptor‑driven shell, nav styles `BOTTOM_BAR` / `DRAWER` / `FLOATING_NAV` |
| **Region slots** (`UiSlotPack`) | individual chrome regions a shell places | the active shell pack | built: 8 regions |

The 8 region slots (`ui-api/.../slots/UiSlots.kt`): `header`, `homeHeader`, `dialog`, `settings`,
`tiles`, `overlay` (reader chrome bar), `detail` (full‑screen detail scaffold), `readerChrome`
(the whole reader scaffold). Each has a default Onyx‑look renderer in `app` (`DefaultSlots`) and a
debug `*Preview.kt` swap‑proof. The resolver falls back to the default when a pack omits a slot
(analogous to `StubSource`).

**Capability‑surface principle ("new UI, same core logic"):** the host builds a state object of
**named, individually renderable pieces** (data for presentation‑only parts like nav; host‑built
composables for logic‑bound parts like content/header); a pack only **arranges** them, it never
re‑implements the logic. This is what lets a future *declarative* (data‑descriptor) pack express
the same arrangement as an in‑tree Compose pack.

**Device classes are not binary.** `DisplayBehavior(allowsMotion, allowsAccentColor)` models two
**orthogonal** axes, so mono E‑Ink, colour E‑Ink (Kaleido) and LCD differ correctly:

| Class | allowsMotion | allowsAccentColor |
|---|---|---|
| mono E‑Ink | no | no |
| colour E‑Ink (Kaleido) | no | no* (cover colour via the colour filter, not UI accent) |
| LCD phone/tablet | yes | yes |

\* A user decision (verified on a Go Color 7): the E‑Ink UI accent stays monochrome even on
Kaleido. The model keeps both axes for a possible future colour‑E‑Ink profile.

> **Scope of external packs (by design):** the region slots are swappable *internally* (proven by
> debug previews). **External** packs are deliberately **declarative/data‑only** — a pack ships a
> JSON description and the host renders it, so it can't crash the host or bypass the E‑Ink
> invariants. Today external packs reach theme / shell nav‑style / icon remap; the additive
> frontier is widening that declarative vocabulary to per‑slot chrome arrangements. Loading
> arbitrary external **code** is intentionally excluded — see [Project Status](PROJECT-STATUS.md).

---

## 7. Colour filter

For Kaleido (and any device), an optional colour filter adjusts saturation / contrast /
brightness before display:

- `ColorProfile` (domain) + `buildColorMatrix` (`domain/color/ColorFilterMatrix.kt`, pure).
- Applied through the image layer (`FilteredImage` / `FilteredReaderImage`) to **both covers and
  reader pages** — so it works for every source, not just one.
- Profiles persist in the `color_profiles` Room table with seeded built‑ins (an "Off" profile and
  a "Boox Go Color 7 Gen2" profile). Colour‑preset plugins can import more.

---

## 8. Data, sync & offline

- Room persistence in `data`. Every record carries a `sourceId` (local source = id 0), so a
  source going away degrades to `StubSource` with no schema change.
- Offline‑first read progress: local `dirty` flag → background sync queue.
- Bidirectional collection sync: the pure `planCollectionSync` use‑case decides per link by
  **last‑write‑wins (UTC)**; the `CollectionSyncManager` shell lists sources agnostically via
  `ActiveSource`. A central `SyncCoordinator` bundles app‑start / server‑changed / manual‑reload /
  tab triggers, gated by device class (E‑Ink syncs less aggressively).

---

## 9. Plugins

A runtime plugin mechanism (the Mihon model — OS‑installed APKs, no downloaded `.dex`):

- **`plugin-api`** (pure JVM): the ABI contract — `SourcePlugin`, `PluginMetadata`,
  `ConfigSchema`, `PluginAbi` (two integers: `VERSION` = 3 / `MIN_SUPPORTED` = 1),
  `PluginCategory { COLOR_PRESET, READER_PRESET, LANGUAGE, UI_PACK, PANEL_MODEL, FONT }`. It
  `api(project(":source-api"))`, re‑exporting the Seam‑A types.
- **`plugin-host`** (Android lib): `PluginHost` discovers installed plugin APKs
  (`QUERY_ALL_PACKAGES`), `AbiGate` checks the two‑int range, and loading uses
  `PathClassLoader(sourceDir, nativeLibDir, hostClassLoader)`. **Trust model: TOFU** — a plugin
  is only instantiated if the package's cert SHA‑256 matches the pin the user confirmed on first
  add.
- **`plugin-sdk`**: a single **shaded** jar (`plugin-api` + `source-api` + `domain`, no
  relocation, clean POM) — the one `compileOnly` artifact external plugin authors link.
  reader presets, languages, UI packs, **panel models**, fonts). Most data‑only packs ship a JSON
  asset and `hasCode="false"`.
- **Panel‑model plugins** (`PluginCategory.PANEL_MODEL`, data‑only): a pack ships a binary **ONNX**
  model as its asset (multi‑MB); because of the asset size, `PluginHost` exposes a binary/metadata‑split
  discovery (`discoverDataPluginInfos` for metadata‑only listing, `binaryDataPluginBytes` to read the
  model bytes lazily) so scans never load the model. The comic reader's `PanelSourceProvider` consumes
  it for ML panel detection (see Render seam).
- **Font plugins** (`PluginCategory.FONT`, data‑only): an APK ships `assets/fonts/*.ttf` + an index
  asset (manifest `DATA_CATEGORY=FONT`, `DATA_ASSET`, `LICENSE=<SPDX>`). `PluginHost.extractFontAsset`
  copies the TTF to permanent version‑keyed storage (`filesDir/plugin-fonts/<pkg>/<versionCode>/…`),
  `PluginCatalog` merges license‑allowed fonts into `allNovelFonts` and registers them with crengine
  (see Render seam). A **hard SPDX allowlist** (`FontLicensePolicy.isLicenseAllowed`: OFL‑1.1, Apache‑2.0,
  CC0‑1.0, MIT, Ubuntu‑1.0) gates both repo install and sideload; the APK manifest license is authoritative.
- **Distribution:** officially supported plugins live in the `Gabriel-Graf/KomgaReaderPlugins`
  **monorepo** (one source tree + one CI that builds, signs and releases all of them) and are
  published through its `repo.json` index. The in‑app repo browser installs from there and verifies
  the cert fingerprint against the index before installing. Third‑party plugins can be hosted in any
  repo that serves a `repo.json`.
- **Discover info modal:** each discovered entry has an ℹ button that opens a per‑plugin info modal
  (header + optional license, an optional preview image, and the rendered `README.md`). Three optional
  generic `repo.json` fields back it — `previewUrl`, `readmeUrl`, `license` — usable by any plugin type.
  README markdown (with remote images) renders via `multiplatform-markdown-renderer` (Apache‑2.0); motion
  is host‑gated for E‑Ink. `license` is shown here; for font plugins it is additionally enforced against
  the SPDX allowlist (see Font plugins above).

**Deliberately not built — arbitrary UI‑view plugins (Compose code with host privileges).** A
crash would take the host down, and the E‑Ink invariants couldn't be enforced. The chosen path is
**declarative**: a pack describes (tap‑zone → action, panel strategy, slot arrangement, style
tokens) and the host renders + controls refresh. The reader chrome is already declarative
(`ReaderTapZones` data descriptor instead of an opaque modifier).

---

## 10. Where to read more

- `docs/superpowers/specs/2026-06-06-komga-eink-reader-design.md` — the master design spec.
- `docs/superpowers/specs/` — one spec per feature/phase (sources, readers, colour filter,
  plugins, the UI‑modularity program, the shell pack, every region slot).
- `.claude/rules/` — the binding architecture rules (seams, source‑agnostic integration,
  device classes, animation gating, the E‑Ink design language, shared‑structure‑before‑variants).
- [PROJECT-STATUS.md](PROJECT-STATUS.md) — the honest gap analysis.
