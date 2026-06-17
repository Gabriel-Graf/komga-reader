# Per-Plugin Release — Design

**Date:** 2026-06-17
**Status:** Approved (design), pre-implementation
**Repo (build target):** `KomgaReaderPlugins` (monorepo) — branch `feat/per-plugin-release`
**App repo (unchanged):** `komga-reader` — only consumes `repo.json`; no app edits.

## Goal

Replace the single shared release tag (`v*` → rebuild + re-version *all* plugins) with
**per-plugin tags and builds**: each plugin is versioned, built, signed, released, and indexed
independently. Fix the latent versionCode bug along the way so the app actually detects updates.

## Why (problems with the shared model)

1. **Version inflation / dishonest versions.** Tagging `v0.3.0` to add Calibre bumped all 12
   existing plugins 0.2.0→0.3.0 though their code was unchanged — meaningless version numbers.
2. **Coupled cadence.** No single-plugin hotfix without re-releasing everything.
3. **Latent versionCode bug.** The app's `installState(entryVersionCode, installedVersionCode)`
   (`data/.../repo/RepoIndexParser.kt:42`) drives `UPDATE_AVAILABLE` by comparing the **repo.json
   entry's `versionCode`** against the **installed APK's `versionCode`**. Today every plugin is
   `versionCode = 1` in both build.gradle and repo.json (the CI script `update-repo-for-release.mjs`
   only stamps `apkUrl`/`fingerprint`/`versionName`, never `versionCode`). Result: updates are never
   detected. Per-plugin releases force an honest, monotonic `versionCode` per plugin.

This is the standard model for extension repos (Mihon/Tachiyomi, F-Droid): per-package versioning.

## Decisions (settled)

- **Tag format:** `<moduleDir>-v<semver>` — e.g. `komga-calibre-source-v0.3.1`, `komga-font-lora-v0.3.1`.
  The module dir name is the unambiguous build target (`:<moduleDir>`).
- **versionCode formula:** `major*10000 + minor*100 + patch` (the project's existing convention, same
  as the app's own versionCode). E.g. `0.3.1 → 30001`, `1.2.3 → 10203`.
- **Version source of truth at build time:** the **tag**. The CI parses module + semver from
  `GITHUB_REF_NAME`, derives versionCode, and injects both into the Gradle build **and** stamps both
  into the single repo.json entry. The APK manifest and the index entry therefore always agree.
- **Baseline (one-time):** all 13 existing repo.json entries are normalized to `versionName "0.3.0"`
  / `versionCode 30000` (their current released state). Next per-plugin tag for any of them is
  `…-v0.3.1`.
- **One release per tag:** named for the tag, carrying exactly one APK asset.
- **No middle ground / no hash-diff hack.** Clean per-plugin tags.

## Build-time version injection

A baseline default lives once in root `gradle.properties`:

```
pluginVersionName=0.3.0
pluginVersionCode=30000
```

Every plugin module's `defaultConfig` reads these properties (so a local `assembleDebug` works with
the baseline, and CI overrides per tag):

```kotlin
versionCode = (project.findProperty("pluginVersionCode") as String).toInt()
versionName = project.findProperty("pluginVersionName") as String
```

CI for tag `komga-calibre-source-v0.3.1` runs:
`./gradlew :komga-calibre-source:assembleRelease -PpluginVersionName=0.3.1 -PpluginVersionCode=30001`

The local-only YOLO module (`komga-panel-model-yolo`, gitignored) is **not** part of CI; it keeps its
own static version (it can adopt the same property read for consistency, defaulting to baseline).

## CI rework (`.github/workflows/release.yml`)

Current: `on: push: tags: ['v*']`, builds `for d in komga-*/` (all), one release, rewrites all entries.

New:
- `on: push: tags: ['*-v*']`.
- **Parse step:** from `GITHUB_REF_NAME` (e.g. `komga-calibre-source-v0.3.1`) extract
  `MODULE=komga-calibre-source` and `VERSION=0.3.1` (split on the last `-v`). Compute
  `VCODE` via the formula. Fail fast if the module dir doesn't exist.
- **Build:** `./gradlew :$MODULE:assembleRelease -PpluginVersionName=$VERSION -PpluginVersionCode=$VCODE`.
- **Sign:** the single `$MODULE` release APK (same keystore secrets, same fingerprint), → `dist/$MODULE-$VERSION.apk`.
- **Release:** `softprops/action-gh-release` with `files: dist/$MODULE-$VERSION.apk` (tag = `GITHUB_REF_NAME`).
- **repo.json:** `node tools/update-repo-for-release.mjs <module> <releaseBaseUrl> <fingerprint> <version> <vcode>`
  updates **only** that plugin's entry; commit back to main (`chore(release): <module> -> <tag>`).

## `tools/update-repo-for-release.mjs` rework

Current: loops over **all** `repo.plugins`, rewrites apkUrl/fingerprint/versionName from a base URL.

New signature: `update-repo-for-release.mjs <moduleDir> <releaseBaseUrl> <fingerprint> <versionName> <versionCode> [repoJsonPath]`.
- Find the single entry whose committed `apkUrl` basename starts with `<moduleDir>-` (or map module→packageName).
- Set its `apkUrl = <base>/<moduleDir>-<versionName>.apk`, `fingerprint`, `versionName`, **`versionCode`**.
- Leave every other entry untouched.
- Error (non-zero exit) if no matching entry — never silently no-op.

Matching module→entry: the committed `apkUrl` basename already encodes the module dir
(`komga-<x>-<ver>.apk`), so match on that prefix; assert exactly one match.

## One-time migration commit (on the branch, before any tag)

1. Add `gradle.properties` baseline (`pluginVersionName=0.3.0`, `pluginVersionCode=30000`).
2. Edit every plugin module `build.gradle.kts` `defaultConfig` to read the two properties (baseline
   default via the gradle.properties values; calibre + all 12 + sample).
3. Normalize `repo.json`: set `versionCode: 30000` on all 13 entries (versionName already 0.3.0).
4. Rework `release.yml` + `update-repo-for-release.mjs` as above.
5. Verify: `./gradlew assembleDebug` (all modules build with baseline version), a dry-run of the
   node script against a copy updates exactly one entry.

After merge to main, future releases are per-plugin tags only.

## Testing & verification

- **Node script unit check:** run `update-repo-for-release.mjs` against a fixture repo.json copy for a
  sample module; assert exactly one entry changed (apkUrl/versionName/versionCode/fingerprint) and all
  others byte-identical; assert non-zero exit on an unknown module.
- **Build check:** `./gradlew assembleDebug` builds all modules with the baseline version; a
  `-PpluginVersionCode=30001 -PpluginVersionName=0.3.1 :komga-calibre-source:assembleDebug` produces an
  APK whose manifest carries versionCode 30001 (verify with `aapt dump badging` / `apkanalyzer`).
- **End-to-end (live):** after merge, push a real per-plugin tag (e.g. `komga-calibre-source-v0.3.1`)
  and confirm: CI builds only that module, the release has one asset, and `repo.json` on main has only
  the Calibre entry changed (to v0.3.1 / vc 30001), all others untouched. (This live tag is the user's
  call — the branch work stops at "ready"; tagging is a separate go.)

## Out of scope

- Changing the signing key / fingerprint (unchanged shared keystore).
- App-side changes (the app already compares repo.json versionCode correctly).
- Re-releasing existing plugins (baseline normalization is index-only; no rebuild until each is tagged).
- The local YOLO plugin's distribution (stays local/gitignored).
