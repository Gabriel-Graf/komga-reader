# LocalSource — Device Folder as a Reading Source (Design)

Date: 2026-06-14
Status: Approved (design), ready for planning
Branch: `feat/source-local`

## Problem

Today the app only knows "local" content as *downloaded-from-a-server* (`DownloadEntity`,
keyed by the originating `sourceId`). Files a user manually places on the device — CBZ/PDF/EPUB
that never came through Komga/OPDS — are invisible. There is no folder scanner and no file
import. We want the user to point the app at a device folder and have its contents appear and
read **exactly like any other source's works**, fully mixed with Komga/OPDS.

## Goal

A real **`LocalSource`** behind Seam A (`MediaSource`), as foreseen in the architecture
(`SourceKind.LOCAL`, `SourceId.LOCAL = 0`). A device folder becomes a browsable, readable
source. Because the whole app above the seam is source-agnostic (ViewModels resolve via
`ActiveSource.get(sourceId)`, images flow through `openPage`/`coverBytes` Coil fetchers), local
works read "like downloads" with **almost no app changes** — the new wiring is one
`SourceKind.LOCAL` branch in `SourceRegistration` plus one small, *general* reader fallback (see
Reading Model) that also unlocks streaming-less sources (OPDS) without a prior download.

**`LocalSource` stays a pure source — it never depends on `render-core`/MuPDF.** A "page" in a
CBZ literally *is* a stored image file, so extracting it is byte-reading (a source job), not
rendering. Formats whose pages require rendering (PDF, CBR) are not streamed by the source; the
render layer renders them from the whole file (Seam B), keeping the two seams cleanly separated.

## Non-Goals (V1)

- Multiple local folders (V1 = exactly one local source, id 0). Additive later.
- A live `ContentObserver` / auto-rescan on file change. Scan on app-start + manual reload only.
- Writing back to the folder, editing, or any progress sync to a server (`LocalSource` is **not**
  a `SyncingSource`).
- CBR guaranteed support (see Risks — verify MuPDF RAR, else drop CBR in V1).

## Architecture

### Module

New Gradle module **`source-local`**, modeled verbatim on `source-opds`:

```
source-local/
  build.gradle.kts          # deps: :domain, :source-api, kotlinx-coroutines-core,
                            #       androidx.documentfile  (Android library — needs SAF/Context)
  src/main/kotlin/com/komgareader/source/local/
    LocalSource.kt          # implements BrowsableSource
    LocalSourceFactory.kt   # create(context, name, rootTreeUri): LocalSource
    LocalLibraryIndex.kt    # folder scan -> in-memory Series/Book tree (cached)
    LocalMetadataParser.kt  # ComicInfo.xml + filename/folder fallback (pure)
    LocalFileCache.kt       # SAF uri -> cached File (LRU), random access
    LocalModels.kt          # internal index DTOs
  src/test/kotlin/...        # pure unit tests (parser, mapping, sort)
```

> `source-opds` is a pure-JVM module; `source-local` must be an **Android library** because it
> needs `Context`/`ContentResolver`/SAF (`DocumentFile`). That is the only structural deviation
> from the OPDS template. It still depends only on `:domain` + `:source-api` (+ `androidx.documentfile`)
> — never on `:app`, never on other sources, **never on `:render-core`/MuPDF**. CBZ page extraction
> uses only `java.util.zip` (JDK). Keep all pure logic (parser, mapping, sort) in plain Kotlin
> classes so they stay JVM-unit-testable without instrumentation.

### Seam contract implemented

`LocalSource : BrowsableSource` (verify exact signatures against
`source-api/.../source/MediaSource.kt` during planning):

| Method | Behavior for LOCAL |
|---|---|
| `id` / `name` / `kind` | `SourceId.LOCAL (=0)` / folder display name / `SourceKind.LOCAL` |
| `browse(page, filter)` | all indexed series (single implicit container); paging trivial |
| `search(query, page)` | filter indexed series by title (case-insensitive) |
| `books(seriesRemoteId)` | the files in that series folder, natural-sorted |
| `seriesDetail(seriesRemoteId)` | series from the index (summary/genres if ComicInfo present, else null fields) |
| `pages(bookRemoteId)` | **CBZ**: one `PageRef(index, bookRemoteId, pageNumber=index+1, url="")` per image entry. **PDF/CBR/EPUB**: `emptyList()` (no per-file pages → reader renders whole-file, see Reading Model) |
| `openPage(ref)` | **CBZ only**: raw image entry bytes from the cached zip (no decode). **else**: `throw UnsupportedOperationException` (never called — `pages()` is empty for those, like OPDS) |
| `downloadFile(bookId, onProgress)` | **whole-file bytes** from the cached file (all formats) |
| `seriesIdOf(bookRemoteId)` | parent folder path (book remoteId is `<seriesPath>/<file>`; loose file → its own series) |
| `coverBytes(remoteId, isSeriesCover)` | **CBZ**: first image entry (raw). **PDF/CBR/EPUB**: `ByteArray(0)` (placeholder cover in V1 — no renderer in the source; see Covers) |

**Not implemented:** `SyncingSource` (no server to sync to), `ContainerSource` (single implicit
container in V1). `StubSource` fallback still applies if the folder/permission is lost.

### Identity & stability

- Source registry id = `SourceId.LOCAL = 0` (the reserved constant), one local source in V1.
- Series `remoteId` = relative folder path under the root (e.g. `Berserk`).
- Book `remoteId` = relative file path under the root (e.g. `Berserk/v01.cbz`).
- Stable across rescans as long as the user does not rename/move files. No DB schema change —
  the per-record `sourceId` mechanism already namespaces local works; losing the folder falls
  back to `StubSource` like any other source.

## Folder Mapping (Komga convention)

`DocumentFile.fromTreeUri(context, rootTreeUri)`, one level deep:

- **Subfolder** → a **Series** (`remoteId` = relative folder path). Files inside → its **volumes**
  (`remoteId` = relative file path), natural-sorted (`v2` before `v10`).
- **Loose file in the root** → a **single-volume Series** (series title = file base name).
- Recognized extensions: `.cbz`, `.pdf`, `.epub` (and `.cbr` iff MuPDF RAR verified — Risks).
- Non-recognized files ignored. Empty folders ignored.

The scan produces an in-memory `LocalLibraryIndex` (series → books, with parsed metadata),
cached until the next scan. No persistence of the index itself.

## Metadata

`LocalMetadataParser` (pure, unit-tested), precedence embedded → filename:

- **CBZ**: read `ComicInfo.xml` entry if present → `Series.title`/`summary`/`genres`/`status`,
  `Book.number`/`summary`. Map ComicInfo fields conservatively; missing → null/empty.
- **EPUB**: title/author via crengine `ReflowableDocument.title()`/`authors()` (lazy, only when
  opened — do **not** open every EPUB during scan; use filename for the library listing and
  enrich on detail/open). Filename fallback for the listing.
- **PDF**: filename/folder only in V1 (MuPDF metadata extraction is optional, deferred).
- **Fallback always**: series title = folder name; book title = file base name (extension
  stripped); `pageCount` from the index (cheap to obtain — see below).

Page counts at scan time: for CBZ, count image entries in the zip central directory (cheap, no
decode, no renderer). For PDF/CBR/EPUB the source reports `pageCount = 0` — it has no renderer;
the real count comes from the opened `Document` on the reader's whole-file `Rendered` path (PDF/CBR)
or is locator-driven (EPUB/NOVEL), exactly as for OPDS/downloaded books today.

## Reading Model (core decision — Hybrid, approved; renderer split corrected during planning)

`LocalSource` is a pure source (no renderer). `LocalFileCache` copies a SAF file into the app
cache once (LRU-evicted, size-capped) so we get random access from a real `File`.

**Two paths, split by whether a "page" is a stored file or must be rendered:**

- **CBZ — streamed via the source (no renderer):** `pages()` returns one `PageRef` per image
  entry (natural-sorted, image extensions only). The existing Coil `SourcePageFetcher` calls
  `openPage(ref)`, which extracts the raw image entry from the cached zip (`ZipFile`, by index)
  and returns its bytes **verbatim** — no decode/re-encode (best quality, lowest CPU/battery on
  E-Ink). This is exactly how the paged/webtoon/comic readers already stream pages — **zero
  reader change for CBZ.**
- **PDF / CBR — rendered whole-file by the render layer (Seam B):** `pages()` returns
  `emptyList()` (like OPDS). The source provides only `downloadFile` (whole-file bytes). The
  reader renders pages from those bytes via the existing `DocumentFactory`/MuPDF
  (`ReaderContent.Rendered`). MuPDF stays in `render-core`, not in the source.
- **EPUB — whole-file (NOVEL reader):** resolves to `ViewerType.NOVEL`; `EpubBytesLoader` already
  calls `downloadFile` directly. No change.
- **`downloadFile(bookId, onProgress)`:** full file bytes from the cache (read once, progress over
  the read). Mirrors the OPDS path.

### The one general reader change (Task in plan)

Today `ReaderViewModel.loadBook` uses the whole-file `Rendered` path **only when a `DownloadEntity`
exists**, otherwise it builds a `Streamed` list from `pages()`. When `pages()` is empty and the
book is not downloaded (OPDS today; LocalSource PDF/CBR), reading currently fails. Fix it
generally: **when `source.pages(bookId)` is empty and no local download exists, fetch
`source.downloadFile(bookId)` and render whole-file (`documentFactory.open(bytes, ext)` →
`Rendered`).** This is a small, source-agnostic improvement — it also lets OPDS books read without
a prior explicit download. It is the only reader behavior change; it special-cases no source type.

## Covers

Covers load through the existing agnostic `SourceCoverFetcher → coverBytes` path (no local cover
shortcut — that would break agnosticism). Since the source has no renderer:

- **CBZ**: real cover = first image entry (raw zip extract). Series cover = its first book's first
  entry.
- **PDF / CBR / EPUB**: `coverBytes` returns `ByteArray(0)` → the UI shows its placeholder. V1
  limitation, documented. (Rendering a PDF/EPUB cover would require MuPDF/crengine inside the
  source, which we forbid. A future enricher in the render layer could fill these without touching
  the seam.)

## Folder Selection, Wiring & Settings

- **Settings "Add server"** gains a **"Local folder"** segment (reuse the existing add-source
  modal flow). It launches `ACTION_OPEN_DOCUMENT_TREE`, then
  `contentResolver.takePersistableUriPermission(uri, READ)`.
- Persist as `ServerConfig(name = <folder display name>, baseUrl = <tree uri string>,
  kind = SourceKind.LOCAL)` via the existing `ServerRepository.save`. No apiKey/credentials.
- **`SourceRegistration.build`** gets a `SourceKind.LOCAL` branch:
  ```kotlin
  SourceKind.LOCAL -> LocalSourceFactory.create(context, config.name, config.baseUrl)
  ```
  `context` is provided via Hilt in `AppModule` (the registration layer is the only place that
  knows the concrete `LocalSource` type — seam respected).
- Removing the local server: existing `removeServer` path (`registration.sourceIdOf` →
  `collections.removeSource`) applies unchanged; also release the persisted URI permission.

## ViewerType & Progress

- A local series carries no server `contentType`, so `ResolveViewerType` resolves by:
  `series.contentTypeOverride ?: (EPUB → NOVEL) : PAGED`. The user can override per series via the
  existing TypeMenu (`contentTypeOverride`, already persisted) to get WEBTOON/COMIC.
- Reading progress stays **local** in the app DB (the existing offline progress store). `dirty`
  never syncs (no server). No new persistence.

## Testing

**Unit (pure, `source-local/src/test`):**
- `LocalMetadataParser`: ComicInfo present (title/number/summary/genres/status) **and** absent
  (filename/folder fallback) — both asserted.
- Folder → series/book mapping: subfolder→series, loose file→single-volume series, mixed.
- Natural sort (`v2` before `v10`); unsupported extensions ignored.
- CBZ page-count via zip central directory (no decode).

**E2E (emulator, `eink_test`):**
- Push a test folder (subfolder with 2 CBZ, a loose PDF, an EPUB) to the device, pick it via SAF.
- Verify: series + books appear in the library mixed with a live source; covers load; PAGED
  reader opens a CBZ page (raw extract), PDF page (MuPDF), NOVEL reader opens the EPUB.
- Verify progress persists across an app restart.
- Verify the local source survives restart via the persisted URI permission, and falls back to
  `StubSource` cleanly if the permission/folder is gone.

## Risks

- **CBR/RAR**: handled like PDF — `pages()` empty → whole-file MuPDF render (no `junrar`, no zip
  extract, since `java.util.zip` can't read RAR). Whether it reads at all depends on MuPDF's RAR
  support in the `render-core` build. Plan Task 0 verifies it; if unsupported, CBR is dropped from
  the recognized extensions in V1. CBZ/PDF/EPUB are unaffected.
- **Large files on E-Ink**: `LocalFileCache` copies whole files into the app cache for random
  access. Bound the cache (LRU + size cap) and evict; document the trade-off. Per-page CBZ
  extraction after the one-time copy is cheap.
- **SAF performance**: directory listing over `DocumentFile` is slower than `java.io.File`.
  Acceptable for an on-start/manual scan; do not scan on every navigation.
- **`source-local` as Android library**: unlike the pure-JVM `source-opds`, this module pulls in
  Android (`Context`, SAF). Keep all pure logic (parser, mapping, sort) in plain Kotlin classes so
  they remain JVM-unit-testable without instrumentation.

## Definition of Done

- `source-local` module builds; `LocalSource` implements `BrowsableSource`; depends only on
  `:domain` + `:source-api` + `androidx.documentfile` (no `:render-core`).
- One `SourceKind.LOCAL` branch in `SourceRegistration` (+ `Context` injected there); the
  add-source UI gains a "Local folder" segment; one general `ReaderViewModel` fallback
  (`pages()` empty + not downloaded → whole-file render). No other `:app`/`:domain` change.
- Settings can add/remove a local folder (SAF, persisted permission).
- Pure unit tests green (parser, mapping, sort, page-count).
- E2E green on the emulator (library listing, three readers, covers, progress across restart),
  mixed with a live Komga/OPDS source.
- Docs nachgezogen: `architecture-seams.md` (Seam A — LocalSource now real),
  `source-extensibility.md` (recipe B reference), CLAUDE.md module table — in the same commit
  as the feature (`docs-match-code`).

## References (verified against code, 2026-06-14)

- Seam A contract: `source-api/.../source/MediaSource.kt`, `SourceId.kt`, `SourceManager.kt`
- Template: `source-opds/` (module layout, factory, mapper)
- Wiring: `app/.../data/SourceRegistration.kt`, `ActiveSource.kt`; `ServerRepository` (domain)
- ViewerType: `domain/.../usecase/ResolveViewerType.kt`, `ContentType`/`ViewerType`/`BookFormat`
- Render: `domain/.../render/Document.kt`, `render-core/.../MupdfDocument.kt`,
  `render-crengine/.../CrengineDocumentFactory.kt`
- SAF precedent: `data/.../download/DownloadManager.kt` (`DocumentFile.fromTreeUri`, content://)
- Coil fetchers (no change needed): `app/.../data/coil/SourcePageFetcher.kt`, `SourceCoverFetcher.kt`
