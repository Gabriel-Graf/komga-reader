# Per-Plugin Release Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move the KomgaReaderPlugins monorepo from one shared `v*` release tag (rebuilds + re-versions all plugins) to **per-plugin tags** (`<module>-v<semver>`), each building/signing/releasing/indexing one plugin, with honest per-plugin `versionCode` so the app detects updates.

**Architecture:** Version is derived from the tag and injected into the Gradle build (`-PpluginVersionName`/`-PpluginVersionCode`, default baseline in root `gradle.properties`) AND stamped into the single matching `repo.json` entry by a single-plugin rewrite script. The CI parses module+version from the tag and builds only that module.

**Tech Stack:** GitHub Actions (`release.yml`), Node (`tools/update-repo-for-release.mjs`), Gradle (Kotlin DSL), Android plugin modules.

**Repo root for all paths:** `/home/gabriel/Documents/Projekte/KomgaReaderPlugins-perplugin` (worktree, branch `feat/per-plugin-release`, based on `origin/main`).
**Spec:** `/home/gabriel/Documents/Projekte/komga-reader/docs/superpowers/specs/2026-06-17-per-plugin-release-design.md`

## Global Constraints

- Tag format: `<moduleDir>-v<semver>` (e.g. `komga-calibre-source-v0.3.1`). Split on the **last** `-v`.
- versionCode formula: `major*10000 + minor*100 + patch` (e.g. `0.3.1 → 30001`).
- Baseline for all existing plugins: versionName `0.3.0`, versionCode `30000` (no re-dating).
- One release per tag, exactly one APK asset. Signing keystore + fingerprint unchanged (`F4:16:…:68:DA`).
- The rewrite script updates **only** the one matching entry; non-zero exit if no/multiple matches — never silently no-op.
- English comments/commit messages. No app-repo edits.
- The local-only `komga-panel-model-yolo` module is gitignored and NOT part of CI — leave it (it may read the same props with the baseline default, but is never tagged/released).

## Module list (14 tracked plugin modules)

`komga-lang-es`, `komga-lang-fr`, `komga-lang-it`, `komga-reader-preset-eink`, `komga-eink-preset-kindle`, `komga-ui-pack-aurora`, `komga-ui-pack-sample`, `komga-kavita-source`, `komga-font-ebgaramond`, `komga-font-lora`, `komga-font-merriweather`, `komga-font-sourceserif`, `komga-font-atkinson`, `komga-calibre-source`.

---

### Task 1: Baseline version injection + honest repo.json versionCodes

**Files:**
- Create: `gradle.properties` (or append if exists)
- Modify: each of the 14 modules' `build.gradle.kts` (`defaultConfig` versionCode/versionName)
- Modify: `repo.json` (all 13 entries' versionCode)

**Interfaces:**
- Produces: every plugin module's versionCode/versionName come from `pluginVersionCode`/`pluginVersionName` Gradle properties (baseline default 30000 / "0.3.0"); CI overrides per tag.

- [ ] **Step 1: Add the baseline properties**

Check if `gradle.properties` exists at repo root; create or append:

```properties
# Plugin release version baseline. CI overrides per tag (-PpluginVersionName/-PpluginVersionCode).
# Local builds use this baseline. versionCode = major*10000 + minor*100 + patch.
pluginVersionName=0.3.0
pluginVersionCode=30000
```

- [ ] **Step 2: Make every plugin module read the properties**

In each of the 14 modules' `build.gradle.kts`, replace the two literal lines in `defaultConfig`:

```kotlin
        versionCode = 1
        versionName = "0.1.0"
```
(the literal version differs per module — match whatever the current `versionCode = …` / `versionName = "…"` lines are)

with:

```kotlin
        versionCode = (project.findProperty("pluginVersionCode") as String).toInt()
        versionName = project.findProperty("pluginVersionName") as String
```

Do this for ALL 14 modules listed in Global Constraints. (Leave `komga-panel-model-yolo` if present — local-only; optional to convert, default baseline is fine either way.)

- [ ] **Step 3: Normalize repo.json versionCodes**

In `repo.json`, set `"versionCode": 30000` on **all 13** entries (versionName is already `"0.3.0"`). Leave every other field untouched. Validate JSON.

- [ ] **Step 4: Verify baseline build + property override**

```bash
cd /home/gabriel/Documents/Projekte/KomgaReaderPlugins-perplugin
./gradlew assembleDebug
```
Expected: BUILD SUCCESSFUL (all modules build with baseline 30000/"0.3.0").

Then verify an override reaches the APK manifest:
```bash
./gradlew :komga-calibre-source:assembleDebug -PpluginVersionName=0.3.1 -PpluginVersionCode=30001
"$ANDROID_HOME"/build-tools/*/aapt dump badging komga-calibre-source/build/outputs/apk/debug/*.apk 2>/dev/null | grep versionCode
```
Expected: `versionCode='30001'`. (If `aapt` path differs, use any installed build-tools aapt or `apkanalyzer apk summary`.)

- [ ] **Step 5: Validate JSON + commit**

```bash
python3 -c "import json;json.load(open('repo.json'));print('repo.json valid')"
git add gradle.properties repo.json $(printf 'komga-%s/build.gradle.kts ' lang-es lang-fr lang-it reader-preset-eink eink-preset-kindle ui-pack-aurora ui-pack-sample kavita-source font-ebgaramond font-lora font-merriweather font-sourceserif font-atkinson calibre-source)
git commit -m "build(release): version plugins from gradle properties + honest baseline versionCodes"
```

---

### Task 2: Single-plugin repo.json rewrite script

**Files:**
- Modify: `tools/update-repo-for-release.mjs`
- Create: `tools/update-repo-for-release.test.mjs` (a small Node assertion script)

**Interfaces:**
- Consumes: nothing from Task 1 at runtime.
- Produces: `node tools/update-repo-for-release.mjs <moduleDir> <releaseBaseUrl> <fingerprint> <versionName> <versionCode> [repoJsonPath]` — updates exactly the one entry whose `apkUrl` basename starts with `<moduleDir>-`; sets `apkUrl=<base>/<moduleDir>-<versionName>.apk`, `fingerprint`, `versionName`, `versionCode` (as a Number); leaves all others byte-identical; exits non-zero if not exactly one match.

- [ ] **Step 1: Rewrite the script**

Replace `tools/update-repo-for-release.mjs` with:

```javascript
#!/usr/bin/env node
// Rewrites ONE plugin's entry in repo.json for a per-plugin release: points its apkUrl at the
// release asset for the given tag and stamps fingerprint/versionName/versionCode. Every other
// entry is left untouched. repo.json stays the hand-maintained source of truth for
// name/description/type/abiVersion.
//
// Usage:
//   node tools/update-repo-for-release.mjs <moduleDir> <releaseBaseUrl> <fingerprint> <versionName> <versionCode> [repoJsonPath]
import { readFileSync, writeFileSync } from 'node:fs'

const [, , moduleDir, releaseBaseUrl, fingerprint, versionName, versionCode, repoPath = 'repo.json'] = process.argv
if (!moduleDir || !releaseBaseUrl || !fingerprint || !versionName || !versionCode) {
  console.error('usage: update-repo-for-release.mjs <moduleDir> <releaseBaseUrl> <fingerprint> <versionName> <versionCode> [repoJsonPath]')
  process.exit(1)
}

const repo = JSON.parse(readFileSync(repoPath, 'utf8'))
const base = releaseBaseUrl.replace(/\/+$/, '')
const prefix = `${moduleDir}-`
const matches = repo.plugins.filter((p) => {
  const name = p.apkUrl.split('/').pop()
  return name.startsWith(prefix)
})
if (matches.length !== 1) {
  console.error(`expected exactly one entry for module "${moduleDir}", found ${matches.length}`)
  process.exit(2)
}
const p = matches[0]
p.apkUrl = `${base}/${moduleDir}-${versionName}.apk`
p.fingerprint = fingerprint
p.versionName = versionName
p.versionCode = Number(versionCode)
writeFileSync(repoPath, JSON.stringify(repo, null, 2) + '\n')
console.log(`Updated ${p.packageName} → ${p.apkUrl} (vc ${p.versionCode})`)
```

- [ ] **Step 2: Write a Node test**

Create `tools/update-repo-for-release.test.mjs`:

```javascript
// Run: node tools/update-repo-for-release.test.mjs
import { readFileSync, writeFileSync, mkdtempSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { execFileSync } from 'node:child_process'
import assert from 'node:assert'

const dir = mkdtempSync(join(tmpdir(), 'repotest-'))
const repoPath = join(dir, 'repo.json')
const fixture = {
  name: 'T',
  plugins: [
    { packageName: 'a.b.calibre', name: 'Calibre', type: 'source', abiVersion: 1, versionCode: 30000, versionName: '0.3.0', apkUrl: 'https://x/releases/download/v0.3.0/komga-calibre-source-0.3.0.apk', fingerprint: 'OLD' },
    { packageName: 'a.b.lora', name: 'Lora', type: 'font', abiVersion: 2, versionCode: 30000, versionName: '0.3.0', apkUrl: 'https://x/releases/download/v0.3.0/komga-font-lora-0.3.0.apk', fingerprint: 'OLD' },
  ],
}
writeFileSync(repoPath, JSON.stringify(fixture, null, 2) + '\n')

execFileSync('node', ['tools/update-repo-for-release.mjs', 'komga-calibre-source',
  'https://x/releases/download/komga-calibre-source-v0.3.1', 'NEWFP', '0.3.1', '30001', repoPath])

const out = JSON.parse(readFileSync(repoPath, 'utf8'))
const cal = out.plugins.find((p) => p.packageName === 'a.b.calibre')
const lora = out.plugins.find((p) => p.packageName === 'a.b.lora')
assert.equal(cal.versionName, '0.3.1')
assert.equal(cal.versionCode, 30001)
assert.equal(cal.fingerprint, 'NEWFP')
assert.equal(cal.apkUrl, 'https://x/releases/download/komga-calibre-source-v0.3.1/komga-calibre-source-0.3.1.apk')
// other entry untouched
assert.equal(lora.versionName, '0.3.0')
assert.equal(lora.versionCode, 30000)
assert.equal(lora.fingerprint, 'OLD')

// unknown module → non-zero exit
let failed = false
try {
  execFileSync('node', ['tools/update-repo-for-release.mjs', 'komga-nope', 'https://x', 'F', '1.0.0', '10000', repoPath])
} catch { failed = true }
assert.ok(failed, 'unknown module must exit non-zero')

console.log('update-repo-for-release.test.mjs: OK')
```

- [ ] **Step 3: Run the test**

```bash
cd /home/gabriel/Documents/Projekte/KomgaReaderPlugins-perplugin
node tools/update-repo-for-release.test.mjs
```
Expected: `update-repo-for-release.test.mjs: OK`.

- [ ] **Step 4: Commit**

```bash
git add tools/update-repo-for-release.mjs tools/update-repo-for-release.test.mjs
git commit -m "build(release): single-plugin repo.json rewrite (module-scoped, stamps versionCode)"
```

---

### Task 3: Per-plugin CI workflow

**Files:**
- Modify: `.github/workflows/release.yml`

**Interfaces:**
- Consumes: the gradle properties (Task 1) and the single-plugin script (Task 2).

- [ ] **Step 1: Rewrite release.yml**

Replace `.github/workflows/release.yml` with (keep the secrets/comment header conventions from the current file):

```yaml
name: Build & Release Plugin

# Builds ONE plugin APK on a per-plugin version tag "<module>-v<semver>", signs it, publishes a
# GitHub Release with that single APK asset, then updates only that plugin's repo.json entry.
#
# Required repository secrets: PLUGIN_KEYSTORE_BASE64, PLUGIN_KEYSTORE_PASSWORD, PLUGIN_KEY_ALIAS,
# PLUGIN_KEY_PASSWORD (same shared keystore → unchanged fingerprint).

on:
  push:
    tags: ['*-v*']

permissions:
  contents: write

jobs:
  release:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
        with:
          ref: main

      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'

      - uses: android-actions/setup-android@v3

      - name: Parse tag → module + version + versionCode
        run: |
          set -euo pipefail
          TAG="$GITHUB_REF_NAME"
          MODULE="${TAG%-v*}"
          VERSION="${TAG##*-v}"
          if [ ! -d "$MODULE" ]; then echo "::error::module dir '$MODULE' not found for tag '$TAG'"; exit 1; fi
          if ! echo "$VERSION" | grep -Eq '^[0-9]+\.[0-9]+\.[0-9]+$'; then echo "::error::bad version '$VERSION'"; exit 1; fi
          IFS=. read -r MA MI PA <<< "$VERSION"
          VCODE=$(( MA*10000 + MI*100 + PA ))
          echo "MODULE=$MODULE" >> "$GITHUB_ENV"
          echo "VERSION=$VERSION" >> "$GITHUB_ENV"
          echo "VCODE=$VCODE" >> "$GITHUB_ENV"

      - name: Decode signing keystore
        env:
          KS_BASE64: ${{ secrets.PLUGIN_KEYSTORE_BASE64 }}
        run: echo "$KS_BASE64" | base64 -d > "$RUNNER_TEMP/plugins.jks"

      - name: Build release APK
        run: ./gradlew ":$MODULE:assembleRelease" --no-daemon --stacktrace -PpluginVersionName="$VERSION" -PpluginVersionCode="$VCODE"

      - name: Sign APK and compute fingerprint
        env:
          KS_PASS: ${{ secrets.PLUGIN_KEYSTORE_PASSWORD }}
          KEY_ALIAS: ${{ secrets.PLUGIN_KEY_ALIAS }}
          KEY_PASS: ${{ secrets.PLUGIN_KEY_PASSWORD }}
        run: |
          set -euo pipefail
          BT="$(ls -d "$ANDROID_HOME"/build-tools/* | sort -V | tail -1)"
          apk="$(find "$MODULE/build/outputs/apk/release" -name '*.apk' | head -1)"
          [ -z "$apk" ] && { echo "::error::no release APK for $MODULE"; exit 1; }
          mkdir -p dist
          out="dist/$MODULE-$VERSION.apk"
          "$BT/zipalign" -f -p 4 "$apk" "$out"
          "$BT/apksigner" sign --ks "$RUNNER_TEMP/plugins.jks" \
            --ks-pass "pass:$KS_PASS" --ks-key-alias "$KEY_ALIAS" --key-pass "pass:$KEY_PASS" "$out"
          FP="$(keytool -list -v -keystore "$RUNNER_TEMP/plugins.jks" -storepass "$KS_PASS" -alias "$KEY_ALIAS" \
                | grep -i 'SHA256:' | head -1 | sed 's/.*SHA256: *//')"
          echo "FINGERPRINT=$FP" >> "$GITHUB_ENV"

      - name: Create GitHub Release with the APK asset
        uses: softprops/action-gh-release@v2
        with:
          files: dist/${{ env.MODULE }}-${{ env.VERSION }}.apk

      - name: Point this plugin's repo.json entry at the release asset
        env:
          REPO: ${{ github.repository }}
        run: |
          BASE="https://github.com/$REPO/releases/download/$GITHUB_REF_NAME"
          node tools/update-repo-for-release.mjs "$MODULE" "$BASE" "$FINGERPRINT" "$VERSION" "$VCODE"

      - name: Commit updated repo.json to main
        run: |
          set -euo pipefail
          git config user.name "github-actions[bot]"
          git config user.email "github-actions[bot]@users.noreply.github.com"
          git add repo.json
          git commit -m "chore(release): ${GITHUB_REF_NAME}" || { echo "repo.json unchanged"; exit 0; }
          git push origin HEAD:main
```

- [ ] **Step 2: Lint the YAML**

```bash
cd /home/gabriel/Documents/Projekte/KomgaReaderPlugins-perplugin
python3 -c "import yaml,sys;yaml.safe_load(open('.github/workflows/release.yml'));print('release.yml valid YAML')"
```
Expected: `release.yml valid YAML`. (If PyYAML missing: `python3 -m pip install --user pyyaml` or skip with a note.)

- [ ] **Step 3: Commit**

```bash
git add .github/workflows/release.yml
git commit -m "ci(release): per-plugin tag workflow (<module>-v<semver>)"
```

---

### Task 4: Whole-branch verification

**Files:** none (verification only)

- [ ] **Step 1: Full build + node test green**

```bash
cd /home/gabriel/Documents/Projekte/KomgaReaderPlugins-perplugin
./gradlew assembleDebug
node tools/update-repo-for-release.test.mjs
python3 -c "import json;json.load(open('repo.json'));print('repo.json valid')"
```
Expected: all succeed.

- [ ] **Step 2: Dry-run the full release path locally (no tag, no push)**

Simulate what CI does for a Calibre tag against a COPY of repo.json (do not modify the real one):

```bash
cp repo.json /tmp/repo.dry.json
node tools/update-repo-for-release.mjs komga-calibre-source \
  https://github.com/Gabriel-Graf/KomgaReaderPlugins/releases/download/komga-calibre-source-v0.3.1 \
  F4:16:A7:F7:44:DE:08:44:8F:E9:99:1C:AC:DB:2A:19:7E:14:82:DA:55:AE:2C:18:5F:EC:C6:24:C6:C0:68:DA \
  0.3.1 30001 /tmp/repo.dry.json
python3 - <<'PY'
import json
a=json.load(open('repo.json')); b=json.load(open('/tmp/repo.dry.json'))
changed=[p['packageName'] for p,q in zip(a['plugins'],b['plugins']) if p!=q]
print('changed entries:', changed)
assert changed==['com.komgareader.plugin.calibre'], changed
print('dry-run OK: only calibre entry changed')
PY
```
Expected: `dry-run OK: only calibre entry changed`.

- [ ] **Step 3: Report ready**

Confirm the branch is ready to merge to main. The first real per-plugin tag (e.g. `komga-calibre-source-v0.3.1`) is the user's call — not pushed by this plan.

---

## Self-Review

**Spec coverage:** tag format + parsing (T3), versionCode formula (T1 build + T3 CI + T2 script), build-time injection via gradle.properties (T1), single-plugin repo.json rewrite + non-zero on no-match (T2), baseline normalization to 30000 (T1), per-plugin CI workflow (T3), verification incl. only-one-entry-changed dry-run (T4). All spec sections covered. App repo untouched. YOLO untouched.

**Placeholder scan:** none — all code/commands are concrete.

**Type/name consistency:** `pluginVersionName`/`pluginVersionCode` property names identical across gradle.properties (T1), module build.gradle (T1), and CI `-P` flags (T3). Script arg order `<moduleDir> <releaseBaseUrl> <fingerprint> <versionName> <versionCode>` identical in the script (T2), its test (T2), the CI call (T3), and the dry-run (T4). Tag→module split `${TAG%-v*}` / version `${TAG##*-v}` consistent.
