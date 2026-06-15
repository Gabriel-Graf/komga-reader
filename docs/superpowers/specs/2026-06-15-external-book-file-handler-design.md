# External Book File Handler — Design

Date: 2026-06-15
Status: Approved (brainstorming)
Scope: one implementation plan.

## Goal

Make the app a system handler for book files so a tap on an `.epub`/`.cbz`/`.cbr`/`.pdf`
in the Boox file manager (or any "open with") opens it in our reader. The file
opens **ephemerally** by default; the reader offers to **import** it into the local
library (a copy into the configured folder). The open behaviour is rememberable and
editable under Settings → Downloads.

This does NOT remap the Boox launcher's built-in "Reading" tab (not possible via
stock settings) — it adds our app as a file-type handler / "open with" target.

## Decisions (locked in brainstorming)

| Topic | Decision |
|---|---|
| Open behaviour | **Ephemeral by default** + offer to import. Persisted preference `externalOpenBehavior` = `ASK` (default) / `IMPORT` / `READ_ONLY`. |
| Reader integration | Reuse the existing reader pipeline by inserting a **transient download row** (the reader already reads `content://` URIs via the download table). No reader rewrite. |
| Import target | Copy into the **configured local folder**, which by default **is the same as the download folder**. LocalSource then turns it into a normal local work on next scan. |
| Folder default | Choosing the **download folder** also sets the **local folder** to the same SAF tree (`setBothFolders` becomes the default path, not an extra button). One folder serves downloads + local library. |
| Remember choice | The import prompt has a **"Auswahl merken"** checkbox; checked → writes `externalOpenBehavior`. Editable in Settings → Downloads (a Fragen / Immer importieren / Nur lesen row). |
| Viewer type | `ResolveViewerType` with no series/shelf → format drives it (EPUB→NOVEL, CBZ/CBR/PDF→PAGED). In-reader toggle stays. |

## Why this approach

The existing open path is source/`bookId`-based, but `DownloadManager.readBytes`
already reads `content://` URIs and `ReaderViewModel.loadBook` consults the **download
table first**. So an external file is wired in as a **transient `DownloadedBook`**
(reserved `SourceId.EXTERNAL`, opaque Base64(URI) `bookId`, `localPath` = the
content URI) and the whole reader pipeline — all formats, viewer-type resolution,
progress — works unchanged. Alternatives (a dedicated external `ReaderContent`/route,
or a synthetic `MediaSource`) were rejected as more invasive for no gain.

## Architecture

### 1. Intent entry (`app`)

- **Manifest:** `MainActivity` gains an `ACTION_VIEW` `<intent-filter>` (`category
  DEFAULT` + `BROWSABLE`, `scheme content`) for the book MIME types
  (`application/epub+zip`, `application/pdf`, `application/zip`,
  `application/vnd.comicbook+zip`, `application/x-cbz`, `application/x-cbr`,
  `application/vnd.comicbook-rar`, `application/x-rar*`) **plus**
  `application/octet-stream` (CBZ/CBR are often generically typed) — the latter
  gated by extension at runtime.
- **MainActivity.onCreate / onNewIntent:** detect `ACTION_VIEW` + `intent.data`.
  Resolve format via `detectBookFormat(mime, fileName)`; unknown → a friendly
  "format not supported" message, no reader launch.

### 2. Format detection (`domain`, pure)

```kotlin
enum class ExternalOpenBehavior { ASK, IMPORT, READ_ONLY }

fun detectBookFormat(mime: String?, fileName: String?): BookFormat?
```
MIME first (epub+zip→EPUB, pdf→PDF, zip / vnd.comicbook+zip / x-cbz→CBZ,
x-cbr / x-rar* / vnd.comicbook-rar→CBR); `application/octet-stream`/null → extension
only (`.epub/.cbz/.cbr/.pdf`); nothing matches → `null`. Pure, unit-tested for every
MIME and extension variant + unknown.

### 3. Transient open (`app`/`data`)

- Reserved `SourceId.EXTERNAL` (a constant ≠ `LOCAL`(0), so `LocalDownloadSync` — which
  only reconciles `sourceId == 0` — never touches it).
- On open: upsert a transient `DownloadedBook(sourceId = EXTERNAL, bookId =
  Base64(uri), localPath = uri.toString(), format, title = fileName, totalPages = 0)`,
  then navigate to the existing reader route
  `reader/{bookId}/{EXTERNAL}/0/{format}/false/{viewerMode}` (viewerMode from
  `ResolveViewerType`). The reader loads via the download path: CBZ/CBR/PDF →
  `documentFactory.open`, EPUB → Novel reader (both already read `content://`).
- **Ephemeral guarantee:** the `ACTION_VIEW` URI grant lives for the task lifetime
  (covers the reading session). Stale `EXTERNAL` rows are **purged on app start** and
  on reader exit. Progress is best-effort keyed by `(EXTERNAL, bookId)` and does not
  survive a restart.

### 4. Import path (`app`/`data`)

- **Default folder coupling:** picking the **download folder** also sets the **local
  folder** to the same SAF tree (the existing `setBothFolders` becomes the default
  applied by the download-folder picker; the separate "shared folder" button is no
  longer needed). While no folder is chosen at all, downloads live app-internally and
  LocalSource has nothing to scan.
- **Import action:** copy the URI bytes into the local(=download) folder via
  `DocumentFile.createFile` under the persisted tree URI (name = original file name,
  collision → numeric suffix). Next LocalSource scan (`SyncCoordinator.onManualReload`/
  `onAppStart`) makes it a normal LOCAL work (cover, progress) — no new source kind.
- **No-folder fallback:** if no folder is set, the import action first opens the
  existing SAF folder picker (`ACTION_OPEN_DOCUMENT_TREE` + `takePersistableUriPermission`),
  sets it as the shared folder, then copies.

### 5. Prompt + preference (`app`/`domain`/`data`)

- New persisted setting `SettingsRepository.externalOpenBehavior: Flow<String>`
  (default `"ASK"`, Room key `external_open_behavior`, no migration) +
  `setExternalOpenBehavior`, wired like `bookmarkMarkerStyle`/`shellLayoutMode`.
- **Prompt logic on external open:** `ASK` → an `EinkModal` "Diese Datei in deine
  Bibliothek übernehmen?" with **[Importieren] / [Nur lesen]** and a **"Auswahl merken"**
  checkbox; confirming with the checkbox set writes `externalOpenBehavior`. `IMPORT` /
  `READ_ONLY` → act directly, no modal.
- **Settings → Downloads:** a `SegmentedChoiceRow` (Fragen / Immer importieren / Nur
  lesen) in the existing Downloads group, plus the folder default change above.

## E-Ink invariants

Prompt via `EinkModal` (no animation, monochrome, one dialog at a time). No new motion.

## Testing

- **Pure (`domain`, JVM):** `detectBookFormat` for every MIME + extension + unknown.
- **Emulator:** intent routing → ephemeral CBZ/PDF open (crengine/EPUB only on Boox);
  transient-row purge on app start; import copy into a SAF folder → LocalSource scan
  surfaces it (mirror the existing LocalSource E2E).
- **Real arm64 Boox:** EPUB ephemeral open; "open with our app" actually appears as a
  handler in the Boox file manager (screenshot); import → file lands in the folder →
  shows as a local work.

## Out of scope

- Remapping the Boox launcher's built-in "Reading" tab (firmware, not possible).
- Replacing the whole launcher.
- `SEND`/share-sheet ingestion (only `VIEW` "open with").
- Durable progress for ephemeral (never-imported) external files across restarts.
- Streaming page extraction for external CBZ (whole-file render via MuPDF is fine).
