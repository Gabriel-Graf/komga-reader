# Calibre Source Plugin — Design

**Date:** 2026-06-17
**Status:** Approved (design), pre-implementation
**Repo (build target):** `KomgaReaderPlugins` (monorepo) — new module `komga-calibre-source`
**Host edits:** none — the reader already has plugin discovery, the `SourceKind.PLUGIN` wiring branch, and the repo browser.

## Goal

A native source plugin that connects the Komga-Reader to a **Calibre Content Server** via its
`/ajax/` JSON API, presenting the library as Series → Volumes grouped by Calibre's `series`
metadata. Read-only in V1 (no progress sync).

## Why a native plugin (and not OPDS)

Calibre **is** reachable over OPDS today (Content Server serves `/opds`, and the in-app OPDS
source supports it incl. PSE) — no plugin needed. The native `/ajax/` plugin exists only because
Calibre's OPDS is *weaker* for our purpose:

| | Calibre OPDS feed | Calibre `/ajax/` JSON |
|---|---|---|
| Series grouping | flat / navigation by author/tag; no clean `series` grouping | `series` + `series_index` → real Series→Volume hierarchy |
| Metadata | only what the feed entry carries | full metadata, custom columns, virtual libraries |
| Search | limited | Calibre search syntax (`series:`, `tag:`, …) |
| Cover/download | OPDS acquisition links | direct `/get/` routes |

Per `big-picture-and-goals.md`, Calibre is a Class-1 source covered by OPDS; this native source is
the "only where OPDS can't express it well" case (series grouping + rich metadata).

## Backend: Calibre Content Server `/ajax/` API

Authoritative shapes from `calibre/src/calibre/srv/ajax.py` (verified 2026-06-17):

- `GET /ajax/library-info` → `{ library_map, default_library }`
- `GET /ajax/categories/{library_id?}` → `[{ name, url, icon, is_category }]` (one entry is the
  built-in **Series** category)
- `GET /ajax/category/{encoded}/{library_id?}?num&offset&sort&sort_order` →
  `{ category_name, base_url, total_num, offset, num, subcategories, items:[{ name, average_rating, count, url, has_children }] }`
- `GET /ajax/search/{library_id?}?query&num&offset&sort&sort_order` →
  `{ total_num, offset, num, sort, sort_order, query, library_id, base_url, book_ids:[int], num_books_without_search, bad_restriction? }`
- `GET /ajax/books/{library_id?}?ids=1,2,3` → `{ "<id>": <book-metadata> | null }`
- `GET /ajax/book/{book_id}/{library_id?}` → book metadata:
  `{ title, authors, series, series_index, rating, pubdate, languages, cover, thumbnail, formats, format_metadata, main_format, other_formats }`
- Cover: `GET /get/cover/{book_id}/{library_id}`
- Format download: `GET /get/{FMT}/{book_id}/{library_id}` (e.g. `/get/EPUB/123/Calibre_Library`)

**Auth:** HTTP Basic when the server runs with `--enable-auth` (optional). The plugin sends a
`Authorization: Basic …` header only when a username is configured.

> ⚠️ Content Server, **not** calibre-web — calibre-web is a separate project and does **not** serve
> `/ajax/`. E2E must target the calibre **content server** (`linuxserver/calibre`, port 8081, or
> `calibre-server` directly).

## Identity & manifest

- Module: `komga-calibre-source` (mirrors `komga-kavita-source` naming)
- `packageName` / `applicationId`: `com.komgareader.plugin.calibre`
- Entry class: `com.komgareader.plugin.calibre.CalibreSourcePlugin` (public no-arg constructor —
  the host instantiates via reflection)
- `AndroidManifest.xml`: `<uses-permission INTERNET>`, meta-data
  `com.komgareader.plugin.SOURCE = com.komgareader.plugin.calibre.CalibreSourcePlugin`,
  `com.komgareader.plugin.ABI_VERSION = 1`

## ConfigSchema (rendered by host as PluginConfigForm)

| key | label | type | required | note |
|---|---|---|---|---|
| `url` | Server-URL | URL | yes | e.g. `http://192.168.1.10:8080` |
| `username` | Benutzername | TEXT | no | only if Calibre auth enabled |
| `password` | Passwort | SECRET | no | Basic-Auth |
| `library` | Bibliothek | TEXT | no | blank → `default_library` from `/ajax/library-info` |

## Data model — series-name join key

Calibre is flat books with optional `series` (name) + `series_index`. Map to the app's
Series→Books domain using the **series name as the join key** (cleaner than opaque category URLs,
and it makes `seriesIdOf` consistent).

remoteIds are **Base64URL-encoded** (LocalSource lesson: the app threads remoteIds as single
nav-path segments, so no `/` allowed):

- `series:<name>` → a Domain `Series` (volumes = books sharing that series name)
- `book:<id>` → a standalone book (no Calibre series) modelled as a one-volume Series

A small pure encoder/decoder (`CalibreRemoteId`) handles tag + Base64URL, unit-tested for
round-trip incl. names with `/`, spaces, quotes.

## BrowsableSource implementation

| Method | Calibre call(s) |
|---|---|
| `browse(page, filter)` | **Series first, then standalone.** Resolve the Series category url once (`/ajax/categories`). Page over `/ajax/category/<series>?num&offset` → `items` (each → `Series(remoteId=series:<name>, title=name)`). When series pages are exhausted, page over `/ajax/search?query=series:false&num&offset` → `book_ids` → `/ajax/books` → each → `Series(remoteId=book:<id>)`. Offset math in a pure, tested `BrowsePaging` helper. `hasNextPage` chains the two phases. |
| `search(query, page)` | `/ajax/search?query=<q>&num&offset` → `book_ids` → `/ajax/books` → group by `series` field (books with a series collapse into one Series tile; standalone → one-book Series). |
| `books(seriesRemoteId)` | `series:` → `/ajax/search?query=series:"<name>"` → `book_ids` → `/ajax/books`, sort by `series_index`, map to `Book(number=series_index)`. `book:` → `/ajax/book/<id>` → single-element list. |
| `seriesDetail(seriesRemoteId)` | `series:` → `Series(title=name)` + best-effort (Calibre series carry no description — summary/genres stay null/empty). `book:` → from `/ajax/book/<id>` (title, authors, comments→summary if present). Returns `null`-tolerant. |
| `pages(bookRemoteId)` | **`emptyList()`** — Calibre serves whole files, no page streaming. The reader then reads whole-file via `downloadFile` (same path as LocalSource PDF/CBR and OPDS-without-PSE). |
| `openPage(ref)` | not reached (pages empty); throws `UnsupportedOperationException` defensively. |
| `downloadFile(bookRemoteId, onProgress)` | `book:` id → `/ajax/book/<id>` → pick a supported format from `main_format`/`formats` (priority EPUB > PDF > CBZ > CBR) → `GET /get/<FMT>/<id>/<lib>`, streamed to bytes with `onProgress(read, total)` (total from Content-Length, else best-effort). |
| `seriesIdOf(bookRemoteId)` | `/ajax/book/<id>` → `series` name → `encode(series:<name>)`; no series → `encode(book:<id>)`. |
| `coverBytes(remoteId, isSeriesCover)` | book cover → `GET /get/cover/<bookId>/<lib>`. Series cover → resolve first volume (lowest `series_index`) via `/ajax/search?query=series:"<name>"` + `/ajax/books`, then its `/get/cover/...`; cache `seriesName → firstBookId`. |

`Book.format` derived from `main_format`/`formats` → `BookFormat{CBZ,CBR,PDF,EPUB}` (unknown
formats filtered out; a book with no readable format is skipped). `Book.pageCount = 0` (whole-file).
`Book.number = series_index`. `id = SourceId.of(name, SourceKind.PLUGIN, baseUrl)`.

**No `SyncingSource` in V1.** Calibre's last-read-position API is EPUB-CFI based, not page-based —
mismatched with the page-based `ReadProgress`. Progress stays local. **The plugin README states this
gap explicitly** (and that it is the planned next step).

## Module structure (mirrors Kavita plugin)

```
komga-calibre-source/
  build.gradle.kts          (com.android.application + kotlin-android + serialization)
  src/main/AndroidManifest.xml
  src/main/.../calibre/
    CalibreSourcePlugin.kt   (SourcePlugin entry)
    CalibreSource.kt         (BrowsableSource)
    CalibreMapper.kt         (DTO → domain; pure)
    CalibreRemoteId.kt       (pure encode/decode)
    BrowsePaging.kt          (pure series→standalone offset math)
    api/CalibreApi.kt        (Retrofit interface)
    api/CalibreDtos.kt       (@Serializable DTOs, ignoreUnknownKeys)
    client/CalibreAuthInterceptor.kt  (optional Basic-Auth header)
    client/CalibreClient.kt           (Retrofit/OkHttp builder)
  src/test/.../calibre/
    CalibreMapperTest.kt
    CalibreRemoteIdTest.kt
    BrowsePagingTest.kt
    CalibreSourceContractTest.kt  (MockWebServer: endpoints, auth, grouping)
  README.md
```

Deps: `com.komgareader:plugin-sdk:0.1.0` **compileOnly** (+ testImplementation — JVM tests need the
contract types the host supplies at runtime); Retrofit + OkHttp + logging-interceptor +
retrofit-kotlinx-serialization-converter + kotlinx-serialization-json + coroutines (bundled);
JUnit4 + MockWebServer + coroutines-test (test). Use the monorepo version catalog where entries
exist, string coordinates otherwise.

## Monorepo wiring

- `settings.gradle.kts`: add `:komga-calibre-source` to `include(...)`, and add **`mavenLocal()`**
  to `dependencyResolutionManagement.repositories` (currently only google()+mavenCentral() — the
  `plugin-sdk:0.1.0` artifact lives in mavenLocal, published by the host build).
- `repo.json`: new entry
  `{ packageName: "com.komgareader.plugin.calibre", name: "Calibre", description: "Calibre Content Server als Quelle (E-Books/Comics)", type: "source", abiVersion: 1, versionCode: 1, versionName: "0.1.0", apkUrl: "plugins/komga-calibre-source-0.1.0.apk", fingerprint: "<debug keystore SHA-256, same as siblings>" }`
- Built debug APK copied to `plugins/komga-calibre-source-0.1.0.apk`, signed with the shared debug
  keystore (same fingerprint `F4:16:…` as the other entries).

## README (plugin repo) — must include

1. **What it is** + the OPDS alternative (Calibre also works over the built-in OPDS source; this
   plugin gives proper series grouping + richer metadata).
2. **Server setup:** run the Calibre **Content Server** (`calibre-server` or `linuxserver/calibre`,
   default port 8080/8081), optionally `--enable-auth` for username/password.
3. **Connect from the reader:** Settings → "Server hinzufügen" → Plugins → install Calibre →
   TOFU-trust → config form: enter `url` (`http://<host>:<port>`), optional `username`/`password`,
   optional `library` (blank = default).
4. **Quirks:** Content Server ≠ calibre-web; series cover = first volume's cover; whole-file reads
   (no page streaming); read-only — **progress is not synced to Calibre yet** (planned).
5. **Verified-against** section: the Docker image + version + the demo works used (kept current per
   the verification loop below).

## Testing & verification

- **Unit (JVM):** `CalibreMapperTest` (set + empty/null fields), `CalibreRemoteIdTest` (round-trip,
  `/`/space/quote names), `BrowsePagingTest` (series→standalone offset chaining), and
  `CalibreSourceContractTest` (MockWebServer: exact `/ajax/` paths, Basic-Auth header present only
  when configured, series grouping, format selection, whole-file download).
- **E2E (mandatory, per user directive):** stand up a **Docker Calibre Content Server demo** with a
  small library of **real demo works** (a multi-volume series + a couple of standalone books, mixed
  EPUB/PDF/CBZ). Drive `CalibreSource` directly (env-gated live test, like `OpdsLiveTest`) to verify
  browse grouping → books → cover → whole-file download end-to-end against real responses. After
  verifying, **update the README's "verified-against" section** with the image/version + the works
  used. Repeat this verify→document loop whenever the plugin changes.

## Open wrinkles (documented, accepted for V1)

- browse pagination chains two endpoints (series category, then `series:false` search) — deterministic
  and avoids in-series page splits, at the cost of slightly more pagination bookkeeping (isolated in
  the pure `BrowsePaging` helper).
- Series cover is the first volume's cover (Calibre has no per-series cover).
- `seriesDetail` metadata is thin (Calibre series carry no description/genres).

## Out of scope (V1)

Progress sync (`SyncingSource`), virtual-library selection UI, custom columns, multi-library
switching beyond the single configured `library`.
