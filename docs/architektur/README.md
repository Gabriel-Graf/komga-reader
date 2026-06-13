# Architecture Diagrams

Visual, "look-and-understand" architecture diagrams for the Komga E-Ink Reader — an overview that
**drills into the critical subsystems** (the two seams, the viewers + the comic cutter, the plugin
interface, and the modular UI).

They are **generated deterministically** by [`build-diagrams.mjs`](build-diagrams.mjs): the box set
and sizing come from the [`understand`](../../.understand-anything/knowledge-graph.json) knowledge
graph (layer file-counts + complexity), and the arrows from the Gradle module DAG + the documented
seams (the graph's Kotlin import edges are too sparse — see
[`../PROJECT-STATUS.md`](../PROJECT-STATUS.md)). They are **not** UML and not class-level — only
components and how they connect.

## The diagrams

| # | File | What it shows |
|---|------|---------------|
| ① | [`01-overview.excalidraw`](01-overview.excalidraw) | The whole system on its **two seams**: the pure Domain Core (hub) defines the contracts; the App Shell + UI modularity drive it; sources, render/E-Ink, data and plugins are the driven adapters. Start here. |
| ② | [`02-viewer-comic-cutter.excalidraw`](02-viewer-comic-cutter.excalidraw) | `ReaderRoute` dispatching the 4 readers (Paged / Webtoon / Novel / **Comic ★**) over the one `Viewer` contract + `ReaderScaffold`, and where the **Comic Cutter** (guided-view XY-cut: PanelDetector → GuidedNavigator → ComicReaderViewModel) plugs in. |
| ③ | [`03-seam-a-sources.excalidraw`](03-seam-a-sources.excalidraw) | **Seam A** — `MediaSource`/`BrowsableSource` contract, the `ActiveSource` resolver, the adapters (Komga · OPDS · plugin · Stub), and the wiring/image-path layer where concrete source types are confined. |
| ④ | [`04-seam-b-render-eink.excalidraw`](04-seam-b-render-eink.excalidraw) | **Seam B** — the `Document` render seam (MuPDF · crengine) and the `EinkController` device seam (Onyx · No-Op), with the `RefreshScheduler` + `OnyxRefresher`. |
| ⑤ | [`05-plugin-system.excalidraw`](05-plugin-system.excalidraw) | The runtime **plugin interface**: `plugin-api` ABI, `plugin-host` (TOFU pin, `PathClassLoader`), the shaded `plugin-sdk`, the APK plugins + categories, and the `KomgaReaderPlugins` repo/install path. |
| ⑥ | [`06-modular-ui.excalidraw`](06-modular-ui.excalidraw) | The **modular UI**: theme pack · shell pack · the 8 region slots, the external `ui_pack.json` data pack, and the host-enforced E-Ink invariants underneath. |

## Visual language

Colour encodes the **role** of a component (consistent across all diagrams):

| Colour | Role |
|--------|------|
| 🟢 green | **Core** — pure domain / contracts / the comic-reader screen (Core, not Chrome) |
| 🟡 yellow | **Port / seam** — the interface boundary (Seam A, Seam B) |
| 🟠 orange | **Driven adapter** — concrete impl behind a seam (Komga, MuPDF, Onyx …) |
| 🔵 blue | **UI / driving** — Compose UI, ViewModels, reader screens, UI-pack layers |
| 🟣 violet | **State / store** — resolver, registry, the Viewer/scaffold/refresh state |
| 🩷 pink | **Transport / execution** — Coil fetchers, the refresher |
| 🩵 teal | **Plugins / registries** — plugin-api/host/sdk, the comic cutter, external packs |
| ⚪ grey | **Neutral** — utilities, fallbacks, data-only descriptors |

Other conventions: **★** marks a deliberately complex / load-bearing component · **dashed green
arrows** mean "the Domain Core *defines* this contract" · box size ∝ subsystem weight.

## Opening them

`.excalidraw` files open in:
- [excalidraw.com](https://excalidraw.com) (File → Open), or
- the **Excalidraw** extension in VS Code (open the file directly).

## Regenerating

After an architecture change, refresh the graph and rebuild:

```bash
# 1. (optional) refresh the knowledge graph
#    /understand            — or the understand-anything CLI

# 2. rebuild the diagrams
node docs/architektur/build-diagrams.mjs

# 3. validate — must report bind-OK · NO-OVERLAP · texts-clear for every file
node <skill>/assets/validate-diagrams.mjs docs/architektur
```

Edit only the `build(...)` specs in `build-diagrams.mjs` (the layout helpers are fixed and produce
overflow-free output). Keep this set in sync when a seam, viewer, plugin category or UI layer
changes — same `docs-match-code` discipline as the rest of the docs.
