# Komga Reader

A native Android comic, manga, webtoon and e‑book reader built **E‑Ink‑first** for
[Onyx Boox](https://onyxboox.com) devices — but **source‑agnostic, device‑agnostic and
modular** from day one.

It speaks [Komga](https://komga.org) and OPDS natively, reads paged comics, vertical
webtoons, panel‑by‑panel "guided" comics and reflowable e‑books, and is built so that
**new sources, new devices and even whole new UIs can be added behind stable seams —
without touching the core.** Maximum flexibility is the explicit north‑star goal.

> **Status:** early but substantial (`versionName 0.1.0`). The architecture is in place
> and proven; the app runs on real hardware. See [Project Status](docs/PROJECT-STATUS.md)
> for an honest, evidence‑backed assessment of what is done, what is missing, and what is
> still blocked.

License: **AGPL‑3.0‑or‑later** (see [Licensing](#license)).

---

## Why another reader?

Most readers hard‑wire one server, one device class and one look. This one is built around
**two architectural seams** that isolate everything that varies, so the whole project can
grow without a core rewrite:

- **Seam A — Sources** (`MediaSource`): Komga, OPDS, or a user‑installed plugin all look
  the same to the app. The library never breaks when a source is offline; it degrades to a
  placeholder.
- **Seam B — Render & E‑Ink** (`Document` / `EinkController`): MuPDF renders cbz/cbr/pdf/epub
  to bitmaps, crengine reflows novels, and the Onyx E‑Ink refresh controller is fully gated so
  development never crashes on non‑Boox hardware.

On top of that sits a **three‑layer modular UI** (theme packs → shell packs → region slots)
so the presentation can be re‑skinned or re‑arranged — eventually by community‑installed
packs — while the host keeps enforcing the E‑Ink correctness rules no pack can override.

---

## Features

| Area | What works today |
|---|---|
| **Sources** | Komga (REST) and OPDS, **multiple servers at once, mixed**. Per‑work source resolution. Kavita via plugin. |
| **Reading modes** | Paged comics, vertical **Webtoon** scroll, **Guided Comic** (panel‑by‑panel zoom, automatic panel detection), **Novel** EPUB reflow (crengine‑ng with hyphenation + bundled reading fonts). |
| **Devices** | Mono E‑Ink, colour E‑Ink (Kaleido), LCD phone/tablet — motion and accent‑colour gated **per device class** on two orthogonal axes. |
| **E‑Ink** | Onyx refresh control (fast mode + device‑managed full refresh), no‑op fallback off‑device, no animations in E‑Ink mode. |
| **Colour filter** | Per‑profile saturation / contrast / brightness applied to **both covers and reader pages** (Kaleido‑tuned built‑in profile). |
| **Offline‑first** | Download manager, local read‑progress (`dirty` flag), background sync queue, bidirectional collection sync (last‑write‑wins). |
| **Plugins** | Runtime‑installed source plugins (APK, TOFU‑pinned), plus data‑only packs: colour presets, reader presets, languages, and **UI packs**. |
| **Modular UI** | Device‑class theme packs, form‑factor shell packs (bottom‑bar / drawer / floating nav), and 8 swappable chrome **region slots**. |
| **i18n** | Type‑safe English + German, compile‑time parity, runtime language packs. |

See the [Feature status matrix](docs/PROJECT-STATUS.md#1-goal-by-goal-scorecard) for the
"built vs. planned vs. blocked" detail behind this table.

---

## Architecture at a glance

```
┌─ UI (Jetpack Compose, :app) — source- & device-agnostic ───────────────┐
│   ViewModels · Reader screens · modular chrome (theme/shell/slot packs) │
└───────────────── ↓ ───────────────────────────────── ↓ ────────────────┘
┌─ :domain (pure Kotlin — no Android, no network, no source) ─────────────┐
│   Models · UseCases · Repository / Render / Eink INTERFACES · ViewerType │
└──────── ↓ SEAM A: Sources ──────────────────── ↓ SEAM B: Render/E-Ink ──┘
┌─ MediaSource ───────────────┐      ┌─ Document / EinkController ─────────┐
│  KomgaSource (REST)         │      │  MuPDF (JNI) → Bitmap   [render-core]│
│  OpdsSource                 │      │  crengine-ng reflow  [render-crengine]│
│  Plugin sources (APK, TOFU) │      │  OnyxEinkController / NoOp [eink-onyx]│
│  SourceManager + StubSource │      │  RefreshScheduler (device-managed)   │
└─────────────────────────────┘      └──────────────────────────────────────┘
```

Everything above the seams is generic. A new source or device is a **new implementation
behind the interface — never a core change.** Full detail in
[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

### Module map

| Module | Responsibility | May **not** depend on |
|---|---|---|
| `domain` | Models, use‑cases, repository/render/eink interfaces, `ViewerType` | Android, network, any source, `source-api` |
| `source-api` | Seam‑A contract (`MediaSource`, `SourceManager`, `SourceId`) | Android, network, UI |
| `source-komga` · `source-opds` | Concrete `MediaSource` implementations | UI, other sources |
| `render-core` | `Document` + MuPDF JNI (Seam B render) | UI |
| `render-crengine` | crengine‑ng EPUB reflow engine (JNI, arm64‑v8a) | UI |
| `eink-onyx` | `OnyxEinkController` (Onyx SDK, hardware‑gated) | UI, sources |
| `guided-view` | Pure‑Kotlin panel detection (XY‑cut) | engine, UI |
| `ui-api` | UI pack / slot / shell / theme / icon **contracts** + decoupled built‑ins | ViewModels, network, sources |
| `data` | Room persistence, sync queue, download manager | UI |
| `plugin-api` · `plugin-host` · `plugin-sdk` | Plugin ABI contract, runtime loader (TOFU, `PathClassLoader`), shaded SDK jar | UI |
| `app` | Compose UI, ViewModels, DI wiring, reader host, default packs | — (top layer) |

The Gradle module graph **enforces** these boundaries: `domain` is pure Kotlin with only a
coroutines dependency; every other module depends on `domain` (and `source-api` / `plugin-api`
where relevant), never on `app`, never on each other across seams.

---

## Quick start

### Prerequisites

- **JDK 17**
- **Android SDK** with API level **34** (compile/target; `minSdk 28`)
- Android Studio Ladybug+ (optional but recommended)
- The crengine native libraries are **committed prebuilt** for `arm64-v8a`, so you do **not**
  need the NDK for a normal build. (Rebuilding crengine needs NDK `28.2.13676358` + CMake — see
  [render-crengine/native/PROVENANCE.md](render-crengine/native/PROVENANCE.md).)

> **arm64‑v8a only.** The native reflow engine ships only for `arm64-v8a` (every Boox and
> virtually all modern Android devices). The app will not run on x86 emulators; use an
> arm64 device or an arm64 system image.

### Build & install

```bash
git clone <this-repo> komga-reader
cd komga-reader
./gradlew :app:assembleDebug          # build the debug APK
./gradlew :app:installDebug           # install onto a connected arm64 device
```

MuPDF is pulled as a prebuilt Maven artifact (`com.artifex.mupdf:fitz`) from the Ghostscript
Maven repo; the Onyx Boox SDK comes from `repo.boox.com`. Both repositories are declared in
`settings.gradle.kts`.

### Run the tests

```bash
./gradlew test                        # all JVM unit tests (domain, mappers, pure logic)
./gradlew :app:connectedAndroidTest   # instrumented / E2E tests (needs an arm64 device + a Komga server)
```

The instrumented tests expect a reachable Komga instance. A local Docker test server and the
emulator geometry used for screenshots are described in
[CONTRIBUTING.md](CONTRIBUTING.md#end-to-end-testing).

---

## Extending it

Flexibility is the point, so extension is first‑class. See
[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) and the in‑repo design specs under
`docs/superpowers/specs/`.

- **Add a source** (Komga‑like server): implement `MediaSource` / `BrowsableSource` in a new
  `source-*` module, or ship it as a **plugin APK** linking only `com.komgareader:plugin-sdk`.
- **Ship a plugin**: source plugins, colour presets, reader presets, language packs and
  **UI packs** install at runtime. Plugins live in their **own repositories** (the official
  index is `Gabriel-Graf/KomgaReaderPlugins`); the example plugins under `plugin/` are
  intentionally **not** part of this repo (they have their own git repos and are gitignored).
- **Re‑skin the UI**: a data‑only **UI pack** (`ui_pack.json`) can replace the theme (full
  colour roles + typography + shapes), the navigation shell style, and remap icons —
  declaratively, with no plugin code and no host privileges. The E‑Ink invariants stay
  host‑enforced regardless of what a pack requests.

> **By design, external packs are declarative/data‑only** — a pack ships a JSON *description* and
> the host renders it, so a pack can never crash the host or bypass the E‑Ink invariants. Today
> that covers theme / shell nav‑style / icon remap; the roadmap *widens the declarative vocabulary*
> to per‑slot chrome arrangements. Loading arbitrary external UI **code** is intentionally excluded
> (same decision as UI‑view plugins). See [Project Status](docs/PROJECT-STATUS.md).

---

## Project layout

```
domain/            pure-Kotlin core (models, use-cases, interfaces)
source-api/        Seam-A contract
source-komga/      Komga REST source
source-opds/       OPDS source
render-core/       MuPDF render engine (JNI)
render-crengine/   crengine-ng EPUB reflow engine (JNI, arm64-v8a, committed prefix)
eink-onyx/         Onyx E-Ink refresh controller (hardware-gated)
guided-view/       panel detection for guided comic reading
ui-api/            UI pack / slot / shell / theme / icon contracts
data/              Room persistence, sync queue, downloads
plugin-api/        plugin ABI contract
plugin-host/       runtime plugin loader (TOFU, PathClassLoader)
plugin-sdk/        shaded single-jar for external plugin authors
app/               Compose UI, ViewModels, DI, reader host, default packs
docs/              design specs, plans, domain & UI-pack docs
tools/             icon generator, CI fixtures, e2e helpers
```

---

## Roadmap (high level)

- **Phase 1–2 (done):** Komga + library + paged/webtoon/novel/guided readers, OPDS, downloads,
  shelves, panel detection, offline sync.
- **Phase 3 (open):** Kaleido colour‑filter refinement, per‑region refresh tuning, extended
  E‑Ink settings, more servers.
- **Phase 4 (largely done early):** runtime plugin loader, plugin SDK, data‑only UI‑pack loader —
  the remaining piece is widening the declarative UI‑pack vocabulary to per‑slot chrome
  arrangements (still declarative; external *code* packs are intentionally out of scope).

The detailed, code‑anchored gap analysis lives in
[docs/PROJECT-STATUS.md](docs/PROJECT-STATUS.md).

---

## Contributing

New contributors: start with [CONTRIBUTING.md](CONTRIBUTING.md). It covers the dev setup, the
build, the test workflow, and — most importantly — the **five non‑negotiable architecture
invariants** that keep the project flexible. The seams only stay valuable if every change
respects them.

---

## License

**AGPL‑3.0‑or‑later.** This app links MuPDF (AGPL) and crengine‑ng (GPL‑2.0‑or‑later) via JNI,
so the combined work is AGPL. **Every distribution must make the complete source available.**

Full license text in [LICENSE](LICENSE); third‑party attributions in [NOTICE](NOTICE);
native dependency provenance (versions, SPDX identifiers, build recipe) in
[render-crengine/native/PROVENANCE.md](render-crengine/native/PROVENANCE.md).

Bundled assets: MuPDF (AGPL), crengine‑ng (GPL‑2.0‑or‑later), Lucide icons (ISC), DejaVu Sans
(Bitstream Vera), Literata & Bitter (OFL‑1.1), TeX hyphenation patterns (MIT / permissive).
