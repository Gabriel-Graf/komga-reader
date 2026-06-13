# Project Status & Critical Assessment

*An honest, evidence‑backed read of where Komga Reader stands against its own stated goals.*
Every claim here was verified against the actual code (not just the design docs — the project's
own `docs-match-code` rule exists precisely because the prose has drifted from the code before).
The yardstick is the project's north star: **maximum flexibility** — source‑agnostic,
device‑agnostic, fully modular/pluggable.

**TL;DR.** The architecture is the strong part and it is genuinely real: the two seams hold, the
source‑agnostic debt that once existed has been paid off, and the modular‑UI program is built out
to an impressive degree. The main remaining work is **additive, on the external extension
surface**: external packs are deliberately declarative/data‑only (no host‑crashing plugin code,
by design), and the declarative vocabulary reaches theme/shell/icons but not yet per‑slot chrome
arrangements — widening it is the north‑star frontier. The open‑source hygiene is now largely
done (README/CONTRIBUTING/architecture docs added; the plugin monorepo + release CI sorted; the
committed test key removed from source; GitHub Actions CI added). Device handling (mono/Kaleido/LCD)
is **done and
deliberate** — the two‑axis `DisplayBehavior` model even future‑proofs a colour‑accent E‑Ink
profile that today's design intentionally leaves mono.

---

## 1. Goal‑by‑goal scorecard

Legend: ✅ built & wired · 🟡 partial / model ahead of UX · 🟢 built, by‑design‑limited ·
⏳ planned/blocked.

| Goal | State | One‑line verdict |
|---|---|---|
| **Multi‑reader** | ✅ | 4 reading modes (paged, webtoon, novel reflow, guided comic) behind a shared `Viewer` seam + `ReaderScaffold`. |
| **Multi‑server / source‑agnostic** | ✅ | Komga + OPDS live and mixed; zero concrete‑source leakage into VMs/domain (verified). |
| **Multi‑device** | ✅ / 🟡 | `DisplayBehavior(allowsMotion, allowsAccentColor)` models the two axes correctly; mono/Kaleido/LCD behave right. `DisplayMode` is still a binary enum, but Kaleido's mono UI accent is a **deliberate** decision, not a gap — see below. |
| **Plugins** | ✅ / 🟢 | Source + 4 data‑only categories real, TOFU‑pinned, SDK shipped. Arbitrary‑Compose UI plugins are deliberately **not** built (declarative‑only by design). |
| **Modular UI** | 🟡 | Theme + shell + 8 region slots all built and wired. External packs are **deliberately declarative/data‑only**; today they cover theme / shell nav‑style / icons. The additive frontier is widening that declarative vocabulary to address individual chrome slots — **not** loading external code. |
| **Colour filter** | ✅ | Per‑profile saturation/contrast/brightness on **both** covers and reader pages, for all sources. |
| **Offline‑first** | ✅ | Download manager, local `dirty` progress, sync queue, bidirectional collection sync (LWW). |

### Multi‑reader — ✅
`ViewerType` = `PAGED, WEBTOON, NOVEL, COMIC`. Five reader screens
(`PagedReaderScreen`, `WebtoonReaderScreen`, `ComicReaderScreen`, `NovelReaderScreen`,
`EpubReaderScreen`) dispatched in `ReaderRoute.kt`; three reader ViewModels all implement the
`Viewer` Compose‑state seam; the shared `ReaderScaffold` is the single home for tap‑zones /
overlay / refresh. The **guided comic reader is built** (`ComicReaderScreen`, 217 lines +
`ComicReaderViewModel` with panel‑zoom/geometry logic, fed by the pure `guided-view` detector) —
note the roadmap rule still lists "guided‑view UI missing", which is **stale**.

### Multi‑server / source‑agnostic — ✅
This is the strongest result. A grep of `app/` and `domain/` finds **no** `KomgaSource`,
`*SourceProvider`, `*SourceFactory` or `AuthHeaders` in any ViewModel or domain type (the single
textual hit is a comment in `DefaultSlots.kt`). Pages/covers flow through
`BrowsableSource.openPage` / `coverBytes` via Coil fetchers; `sourceId` is threaded through
navigation so every consumer resolves *per work*. Komga REST + OPDS verified live and mixed. The
historical "app coupled to Komga" debt is genuinely remediated.

### Multi‑device — ✅ (with intentional headroom)
`DisplayBehavior` is a proper value object with **two orthogonal flags** — `allowsMotion`
(animations) and `allowsAccentColor` (coloured UI accent). They are independent, so a device is a
*point in 2D*, not one `isEink` switch — this is what lets "no motion **but** colour allowed"
(a Kaleido panel, physically) be expressible without conflating the two. `DesignTokens` derive
from it; four theme packs exist (`MonoEinkPack`, `KaleidoPack`, `LcdPack`, `AuroraPack`), selected
by device class.

`DisplayMode` is still `enum { EINK, SMARTPHONE }`, and `behaviorFor` maps
`EINK → (motion=false, accent=false)` — **including Kaleido.** That is a **deliberate decision**
(verified on a real Go Color 7): the E‑Ink UI accent stays monochrome even on colour E‑Ink because
a coloured chrome accent looked wrong; colour belongs to the **covers/pages via the colour
filter**, not the UI chrome. So this is not a "lag" or a gap. The 2‑axis model is kept as
**future‑proofing**: if an optional colour‑E‑Ink UI profile `(motion=false, accent=true)` is ever
wanted, the model already supports it with no rework — only the enum/mapping would extend.

### Plugins — ✅ (source + data) / 🟢 (UI‑view deliberately deferred)
`plugin-api` (ABI `VERSION=2` / `MIN_SUPPORTED=1`, `PluginCategory{COLOR_PRESET, READER_PRESET,
LANGUAGE, UI_PACK}`), `plugin-host` (`PluginHost`, `AbiGate`, `PluginSignature` TOFU pinning,
`PathClassLoader`, `DataPluginManifest`), and a shaded `plugin-sdk` are all real. Source plugins
(Kavita) and all four data‑only categories work and have E2E coverage. Arbitrary‑Compose UI
plugins are **intentionally not built** (host‑crash + un‑enforceable E‑Ink invariants) — the
declarative path is the chosen replacement. This is a correct decision, not a gap.

### Modular UI — 🟡 (the north‑star goal; most built, one additive frontier left)
See [§5](#5-the-flexibility-frontier). Built: device‑class theme packs (incl. a full data‑driven
theme via `ui_pack.json`), a descriptor‑driven shell pack with three nav styles, and **all 8
region slots wired at real call‑sites** (verified: `detail`×3, `header`×2, plus `dialog`,
`overlay`, `readerChrome`, `settings`, `tiles`) with 12 debug swap‑proof previews. The internal
swap is proven; external packs are **deliberately declarative/data‑only** (no plugin code — so a
pack crash can't take the host down and the E‑Ink invariants stay host‑enforced). The remaining
work is *additive*: widen the declarative vocabulary so external data packs can address individual
chrome slots, not just theme/shell/icons.

### Colour filter — ✅
`ColorProfile` + pure `buildColorMatrix`, applied via `FilteredImage`/`FilteredReaderImage` to
covers (tiles, collection detail, series detail) **and** reader pages, persisted in the seeded
`color_profiles` Room table. Per‑region refresh tuning (Phase 3) is still open.

---

## 2. Architecture strengths (what to protect)

These are real and unusually good for a project this young — they are the reason the flexibility
goal is reachable at all:

- **Clean module DAG, enforced by Gradle.** `domain` is pure Kotlin (only
  `kotlinx-coroutines-core`); no module depends on `app`; sources don't depend on each other.
  Boundaries are structural, not aspirational.
- **The source‑agnostic seam is honoured in practice**, not just in interface design — the thing
  most projects get wrong.
- **Very low entropy.** Zero `TODO`/`FIXME`/`HACK` in production code; `RefreshScheduler` and
  `OnyxRefresher` have been removed (2026-06-13) — the deprecated symbols are gone; only a handful
  of `!!` force‑unwraps outside tests.
- **Strong test footprint:** ~98 JVM unit‑test files + ~43 instrumented tests, with an
  integration‑test suite and CI.
- **Excellent licensing provenance** for native deps (`render-crengine/native/PROVENANCE.md` with
  versions, SPDX IDs, build recipe, risk register) — rare and valuable for an AGPL project.
- **The capability‑surface discipline** ("new UI, same core logic") is applied consistently, which
  is exactly what keeps the in‑tree packs convertible to future declarative packs.

---

## 3. Gaps (stated goals not yet fully delivered)

| # | Gap | Impact on the north star |
|---|---|---|
| G1 | **External declarative vocabulary is narrow.** External data packs cover theme / shell nav‑style / icon remap, but cannot yet pick per‑slot chrome arrangements (alternative header/overlay/tile layouts). *Note:* loading arbitrary external **code** is deliberately excluded by design (declarative‑only, to prevent host crashes and keep E‑Ink invariants enforced) — the frontier is a richer **data** vocabulary, not code. | **High.** This is the gap between today's data packs and "community can re‑arrange the whole UI". |
| G2 | *(Resolved — not a gap.)* Kaleido is not a separate user mode by **deliberate** decision (mono UI accent on colour E‑Ink; colour via the filter). The 2‑axis `DisplayBehavior` already models a future colour‑accent profile if ever wanted. | None today; future‑proofed. |
| G3 | **Detail body not yet a `DetailShell`.** Only the detail *scaffold* is slotted; the hero/grid arrangement is still screen‑owned (lives in the 1228‑line `SeriesDetailScreen`). | Medium. Last big chunk of UI that isn't decomposed. |
| G4 | **Per‑region E‑Ink refresh tuning (Phase 3)** open. The device-managed-per-context path (`EinkContextController` / `EinkContextEffect` / `EinkWise` modes) is built and wired (2026-06-13); `RefreshScheduler` and `OnyxRefresher` are removed. Per-region granularity (partial vs. full, region-aware) remains a future Phase 3 item if the EinkWise approach proves insufficient. | Low. |
| G5 | **Guided‑view interaction polish / detector v2** — detector is built and wired, but tuning and edge cases remain an ongoing area. | Low. |
| G6 | *(Resolved.)* GitHub Actions CI added (`.github/workflows/ci.yml`): unit + build + arm64 integration. First run needs validation. | — |

---

## 4. Open‑source release readiness

The user's explicit goal is to open‑source this. Status of the must‑fix items:

| Item | State | Action |
|---|---|---|
| LICENSE (AGPL‑3.0) at root | ✅ present | — |
| NOTICE (third‑party attribution) | ✅ present & thorough | keep in sync when deps change |
| Native dep provenance | ✅ excellent (`PROVENANCE.md`) | — |
| **README** | ✅ **added in this pass** | flesh out with screenshots |
| **CONTRIBUTING / build guide** | ✅ **added in this pass** | — |
| **Architecture doc (English)** | ✅ **added in this pass** | — |
| **Committed test API key** | ✅ **fixed** — removed from source | dev‑local tests now read it from `BuildConfig` (sourced from gitignored `local.properties` / env), skip when unset (`LocalTestServer`); CI suite uses static fixture Basic‑Auth. Scrubbed from the plan docs too (git history still retains it). |
| Secrets / credentials in prod code | ✅ none found | `local.properties` is gitignored |
| Example plugins discoverable | ✅ in the `KomgaReaderPlugins` monorepo | all official plugin sources now live there (built/signed/released by CI); two UI packs in the index — **Tablet-UI** (floating-nav modern look) and **Smartphone-UI** (drawer/sidebar nav). README points to it |
| Internal docs language | ⚠️ specs/rules are German | community‑facing docs are now English; translating specs is a follow‑up |
| Insecure Boox Maven repo (HTTP) | ✅ **hardened** | external (Onyx's HTTP server, unfixable TLS) — neutralised: the Onyx `.aar` SHA‑256 is pinned in `gradle/verification-metadata.xml` (tamper‑tested), and the Boox + Ghostscript repos are content‑filtered to their one group each (no dependency‑confusion/MITM vector). |
| Native build reproducibility | 🟡 crengine is arm64‑only with a **committed 386‑file prefix** | documented & reproducible, but it makes the repo heavy and x86 emulators unsupported |
| GitHub Actions CI | ✅ **added** (`.github/workflows/ci.yml`) | unit + build on `ubuntu-latest`; integration on an `ubuntu-24.04-arm` runner (arm64 emulator + Docker fixtures). First run needs validation (Actions can't be run from here). |

**None of these is a hard blocker** to publishing the source (AGPL obliges disclosure anyway).
The test‑key cleanup and a plugin‑authoring walkthrough are the highest‑value pre‑release polish.

---

## 5. The flexibility frontier

Maximum flexibility is the stated #1 goal, so this deserves its own section. The internal
modularity is excellent; the remaining work is on the **external** surface — and it is *additive*,
not a redesign.

**A deliberate boundary first.** External packs are **declarative/data‑only by design** — a pack
ships a *description* (JSON), and the **host** renders it and controls refresh. This is a
considered safety decision (the "host renders the description" model), not a missing feature:
arbitrary external Compose code would let a pack crash take the host process down, and would make
the E‑Ink invariants (motion/accent gating, `animation-gating.md`, `eink-design-language.md`)
impossible to enforce against a third party. So "load external UI code" is **explicitly off the
table** — the same decision as plugin type (b).

**Where the surface is today vs. the frontier:**

- A community member can already install **data‑only** packs: a full theme (colour roles +
  typography + shapes as JSON), a shell nav‑style, an icon remap, plus colour/reader/language
  presets and source‑plugin APKs. That is already more than most readers offer.
- They **cannot yet** ship a data pack that selects a different **per‑slot** chrome arrangement
  (an alternative header/overlay/tile layout). All 8 slots are internally swappable (proven by the
  debug previews), but the *external declarative vocabulary* only reaches theme/shell/icons so far.

**Recommendation:** the single highest‑leverage north‑star item is **widening the declarative
vocabulary to per‑slot arrangements** — let a `ui_pack.json` name, from a host‑provided set, which
arrangement each region uses. Everything underneath (the 8 slots, the shell descriptor, the
capability surfaces) is already shaped for it; the work is the descriptor + host wiring, **not**
loading code. (Separately, an internal `ui-api` ABI freeze would let future *in‑tree* code packs
become a stable extension point — a minor, internal concern, not the external‑community path.)

---

## 6. Architecture weaknesses / risks

1. **Documentation as a load‑bearing, hand‑synced artifact.** The project leans on a very large
   prose knowledge base (`CLAUDE.md`, `.claude/rules/`, ~38 specs). It has **already drifted** —
   the roadmap still says the guided‑view UI is missing though it is built; the seams rule once
   listed `Viewer`/`RefreshScheduler` as real before they existed. For a public project with many
   contributors this hand‑sync is a real maintenance liability. *Mitigation:* the slim English
   `ARCHITECTURE.md` added here, plus the generated `understand` knowledge graph
   (`.understand-anything/`), give code‑anchored references that don't rot as easily.
2. **God‑files resisting decomposition.** `SeriesDetailScreen.kt` (1228 lines) and
   `SettingsContent.kt` (868) are the outliers. They're the natural home of the not‑yet‑built
   `DetailShell` and would benefit from the same slot treatment the rest of the chrome got.
3. **Heavy native footprint.** crengine ships as a committed 386‑file arm64‑v8a static prefix.
   It's reproducible and well‑documented, but it bloats the repo, forbids x86 emulators, and is a
   contributor on‑ramp barrier.
4. **Single‑maintainer, German‑first knowledge.** The deep design rationale is in German and in
   one head; English docs (now started) and the knowledge graph reduce the bus‑factor risk.
5. **Keying off `DisplayMode` instead of `DisplayBehavior`.** The binary enum is fine today, but
   the careful two‑axis design only survives if new features read the behaviour **flags**
   (`allowsMotion` / `allowsAccentColor`), never the `EINK`/`SMARTPHONE` enum directly. A feature
   that branches on the enum quietly re‑introduces the binary assumption the model was built to
   avoid.

---

## 7. Prioritised recommendations

**For the open‑source launch (do first):**
1. ~~Parameterise the committed test API key out of the `androidTest` files.~~ **Done** (via
   `BuildConfig`/`local.properties`, `LocalTestServer`); GitHub Actions CI added.
2. Add a short plugin/UI‑pack authoring walkthrough (`docs/plugins/` already exists — point to it
   from the README) so newcomers can find the externally‑hosted examples.
3. ~~Decide the CI story for the public home.~~ **Done** — GitHub Actions CI added
   (`.github/workflows/ci.yml`); the GitLab pipeline is kept too.
4. Add screenshots/GIFs to the README.

**For the north‑star (flexibility) roadmap:**
5. **Widen the external declarative vocabulary to per‑slot arrangements** (§5) — let a
   `ui_pack.json` choose, from a host‑provided set, which layout each chrome region uses. Stay
   declarative (host renders); do **not** add an external code‑loading path. Highest‑leverage
   flexibility work.
6. Decompose `SeriesDetailScreen` into a `DetailShell` (hero/grid as arrangeable pieces) (G3) — a
   prerequisite for #5 reaching the detail screens.
7. *(Optional, only if ever wanted)* surface a colour‑accent E‑Ink profile by extending
   `DisplayMode` and mapping it to `DisplayBehavior(motion=false, accent=true)`. The model is
   already ready; today's mono‑accent‑on‑Kaleido is the deliberate default.

**For long‑term health:**
8. Keep distilling the German specs into code‑anchored English references; lean on the knowledge
   graph for structure so the prose carries only rationale.

---

*This assessment is a point‑in‑time snapshot. Re‑run the verification greps (or regenerate the
`understand` knowledge graph) before treating any specific line as current.*
