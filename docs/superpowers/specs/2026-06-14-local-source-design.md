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
works read "like downloads" with **zero reader/UI/Coil changes** — the only new wiring is one
`SourceKind.LOCAL` branch in `SourceRegistration`.

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
> from the OPDS template. It still depends only on `:domain` + `:source-api` — never on `:app`,
> never on other sources.

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
| `pages(bookRemoteId)` | page count via index; `PageRef(index, bookRemoteId, pageNumber, url="")` |
| `openPage(ref)` | **per-page bytes** — see Reading Model |
| `downloadFile(bookId, onProgress)` | **whole-file bytes** — see Reading Model |
| `seriesIdOf(bookRemoteId)` | parent folder path (book remoteId is `<seriesPath>/<file>`) |
| `coverBytes(remoteId, isSeriesCover)` | first page of the book (series cover = first book's first page) |

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
decode); for PDF, MuPDF `pageCount()` (cheap, indexes only); for EPUB, reflowable has no fixed
page count — report 0/unknown and let the NOVEL reader drive progress by locator (existing path).

## Reading Model (core decision — Hybrid, approved)

`LocalSource` is self-contained behind the seam. `LocalFileCache` copies a SAF file into the app
cache once (LRU-evicted) so we get random access from a real `File`.

- **`openPage(ref)`** (used by paged/webtoon/comic via the existing Coil `SourcePageFetcher`):
  - **CBZ**: extract the raw image entry from the cached archive (`ZipFile`, O(1) by index) and
    return its bytes **verbatim** — no decode/re-encode (best quality, lowest CPU/battery on
    E-Ink).
  - **PDF**: open with MuPDF from the cached file's bytes, render page `ref.index`, encode to
    PNG/JPEG bytes.
- **`downloadFile(bookId, onProgress)`** (used by the NOVEL/EPUB reader, which needs whole-file
  bytes for crengine, and by the guided-comic whole-file path): return the full file bytes from
  the cache (read once, progress callback over the read). This mirrors the OPDS path, which has
  no `openPage` and reads via `downloadFile`.
- **`coverBytes`**: page-1 bytes via the same per-format path as `openPage`.

Result: paged/webtoon/comic readers work through the standard agnostic `SourceImage → openPage`
fetcher with no changes; novel works through the existing whole-file path. The reader stays
source-agnostic.

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

- **CBR/RAR**: MuPDF RAR support depends on the build. Plan task 0 verifies it on the actual
  `render-core` build; if unsupported, CBR is dropped from the recognized extensions in V1 (no
  `junrar` dependency added). CBZ/PDF/EPUB are unaffected.
- **Large files on E-Ink**: `LocalFileCache` copies whole files into the app cache for random
  access. Bound the cache (LRU + size cap) and evict; document the trade-off. Per-page CBZ
  extraction after the one-time copy is cheap.
- **SAF performance**: directory listing over `DocumentFile` is slower than `java.io.File`.
  Acceptable for an on-start/manual scan; do not scan on every navigation.
- **`source-local` as Android library**: unlike the pure-JVM `source-opds`, this module pulls in
  Android (`Context`, SAF). Keep all pure logic (parser, mapping, sort) in plain Kotlin classes so
  they remain JVM-unit-testable without instrumentation.

## Definition of Done

- `source-local` module builds; `LocalSource` implements `BrowsableSource`.
- One `SourceKind.LOCAL` branch in `SourceRegistration`; no other `:app`/`:domain` change beyond
  the add-source UI segment.
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
