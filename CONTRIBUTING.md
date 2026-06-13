# Contributing to Komga Reader

Thanks for considering a contribution. This project has an unusually strong architectural
spine — the whole point is **flexibility behind stable seams** — so the most important thing a
contributor can do is understand and respect that spine. This guide gets you building, testing,
and changing code without breaking the seams.

---

## 1. Development setup

### Prerequisites

| Tool | Version |
|---|---|
| JDK | **17** |
| Android SDK | API **34** (compile/target); `minSdk 28` |
| Kotlin / AGP | 2.0.21 / 8.7.2 (managed by Gradle) |
| Device / emulator | **arm64‑v8a** (the crengine reflow engine ships only for arm64) |

You do **not** need the Android NDK for a normal build: the crengine native static libraries
are committed prebuilt under `render-crengine/native/prefix/` for `arm64-v8a`. You only need the
NDK (`28.2.13676358`) + CMake if you change the native engine — see
[`render-crengine/native/PROVENANCE.md`](render-crengine/native/PROVENANCE.md) for the exact,
reproducible build recipe.

### Build

```bash
./gradlew :app:assembleDebug     # build the debug APK
./gradlew :app:installDebug      # install onto a connected arm64 device
./gradlew build                  # build everything (slower)
```

External Maven repositories used (declared in `settings.gradle.kts`):

- `maven.ghostscript.com` — MuPDF (`com.artifex.mupdf:fitz`)
- `repo.boox.com` — Onyx Boox E‑Ink SDK (note: this repo is served over **HTTP**; it is the
  only insecure repo in the build and is needed for `eink-onyx`)

---

## 2. Testing

The project follows **TDD** for all domain/mapper logic and verifies every feature with a small
E2E test. Two layers:

### Unit tests (JVM, fast)

```bash
./gradlew test
```

Pure logic — domain models, use‑cases, mappers, the panel detector, sync planners, theme/token
resolution. These need no device. There are ~100 unit‑test files; keep them pure (no Android, no
I/O, no sleeps).

### Instrumented / End‑to‑end testing

```bash
./gradlew :app:connectedAndroidTest
```

Instrumented tests run on an arm64 device/emulator and exercise the real reading pipeline against
a real Komga server. To run them you need a reachable Komga instance.

A convenient local setup is a Docker Komga:

```bash
docker run -d --name komga-test -p 25600:25600 \
  -e PUID=1000 -e PGID=1000 -e KOMGA_LIBRARIES_SCAN_STARTUP=true \
  -v ~/komga-test/config:/config -v ~/komga-test/comics:/data \
  gotson/komga:latest
```

From an Android emulator the host is reachable at `http://10.0.2.2:25600/api/v1/`. Komga
authenticates with an API key via the `X-API-Key` header (create one at
`POST /api/v2/users/me/api-keys`).

> **Note:** some instrumented tests currently hard‑code a local test API key and connection. If
> you are setting up CI or a public fork, parameterise these (BuildConfig field / instrumentation
> argument) rather than committing your own server credentials. See
> [Project Status → release readiness](docs/PROJECT-STATUS.md#4-open-source-release-readiness).

The reference emulator (`eink_test`) is configured to **real Boox Go Color 7 Gen2 geometry**
(`1264×1680 @ 300dpi`) so screenshots match the hardware.

### CI

CI is currently GitLab‑based ([`.gitlab-ci.yml`](.gitlab-ci.yml), stages `build` / `unit` /
`integration`). The integration stage needs a shell‑executor runner with Docker + `/dev/kvm` +
the Android SDK. A GitHub Actions port is not yet provided.

---

## 3. The five non‑negotiable invariants

These carry the whole project. A change that violates one is building **around** the
architecture, not within it. (The authoritative, longer form lives in `CLAUDE.md` and
`.claude/rules/`.)

1. **Two seams encapsulate all variability.** Seam A = sources (`MediaSource`), Seam B =
   render/E‑Ink (`Document` / `EinkController`, shared `Viewer` contract). A new source, engine
   or device is a **new implementation behind the interface — never a core change.**

2. **Everything above the seams is source‑ and device‑agnostic — including the wiring.** No
   `KomgaSource` / `*SourceProvider` knowledge in `domain` or `app`. ViewModels resolve sources
   through `SourceManager` / `ActiveSource` and read via `BrowsableSource.openPage`; concrete
   source types live **only** in the `app/data` wiring layer.
   *Litmus test for any feature:* does it still work if the active source is OPDS, a stub, or a
   future plugin — without changing a line? If not, the seam is violated.

3. **The E‑Ink design language is mandatory, not taste.** Flat, 1.5px borders instead of
   shadows, no animations (every animation is gated through the device class and has an instant
   E‑Ink alternative), monochrome‑strong, Lucide icons via the `AppIcons` registry, one dialog
   at a time. The host enforces these even for UI packs.

4. **Viewer resolution is deterministic:** `Series.contentTypeOverride ?: Shelf.contentType →
   ViewerType`. No fragile content‑type auto‑guessing.

5. **Offline‑first + TDD + E2E.** Read progress is local first (`dirty`) → sync queue. Every
   feature ships with unit tests (pure domain/mapper) **and** a small E2E against a real Komga.

When a new variant of something that already exists (a 4th reader, a 3rd source, an Nth dialog)
is added, **extract the shared structure first**, then build the variant on top of it — don't
copy‑paste the previous variant.

---

## 4. Where plugins live

The officially supported plugins (a Kavita source, UI packs, colour/reader/language presets) live
in the **[`Gabriel-Graf/KomgaReaderPlugins`](https://github.com/Gabriel-Graf/KomgaReaderPlugins)
monorepo** — one source tree built by one CI that signs and releases every plugin and regenerates
the `repo.json` index the app reads. They are **not** part of this host repo. (The `plugin/` dir
here is local scratch and is gitignored.) Third-party plugins can be hosted in any repo serving a
`repo.json`.

To build a plugin, link only the shaded **`com.komgareader:plugin-sdk`** artifact as
`compileOnly` (it bundles `plugin-api` + `source-api` + `domain` with a clean POM). A source
plugin implements `BrowsableSource` (+ `SyncingSource`); a data‑only pack ships a JSON asset and
declares its category via manifest metadata. See
[`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md#plugins) and the plugin design specs under
`docs/superpowers/specs/`.

---

## 5. Code style & conventions

- **Kotlin**, Compose for UI, Hilt for DI, Room for persistence, Retrofit/OkHttp +
  kotlinx.serialization for HTTP, Coil for images.
- Follow the surrounding code: match its naming, comment density and idioms.
- Keep `domain` pure. Put concrete/Android/network types only in the modules that own them.
- All user‑visible text goes through the type‑safe `i18n` layer (English + German, compile‑time
  parity). Use real umlauts/ß in German strings.
- Prefer immutability and pure functions for transformations; keep side effects at the edges.

---

## 6. Documentation discipline (`docs-match-code`)

This project keeps a large amount of design prose (`CLAUDE.md`, `.claude/rules/`,
`docs/superpowers/specs/`). That prose has drifted from code before. The rule is: **when you
build or change a seam/component, update the relevant doc in the same commit**, and describe
*intended* (Soll) vs *actual* (Ist) state separately. Never document a type as real that `grep`
can't find. When in doubt, the code is the source of truth — verify before asserting.

> Most internal design specs are currently written in **German**. New community‑facing docs
> (README, this file, `docs/ARCHITECTURE.md`, `docs/PROJECT-STATUS.md`) are in **English**.
> Translating the internal specs is a welcome contribution.

---

## 7. Pull requests

- Branch off `main`, keep changes focused.
- Run `./gradlew test` before opening a PR; run the relevant instrumented test if you touched
  the reading/sync pipeline.
- Explain *why*, not just *what*. If you deviate from an invariant for a good reason, say so
  explicitly in the PR — silently following a known anti‑pattern "for consistency" is not
  acceptable.
- By contributing you agree your contribution is licensed under **AGPL‑3.0‑or‑later**.
